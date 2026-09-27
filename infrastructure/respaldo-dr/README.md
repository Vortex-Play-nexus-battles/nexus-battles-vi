# Respaldo y recuperación de DEV (M16A)

Respaldo diario de las bases de los dos hosts de desarrollo, restauración de
prueba automática y el procedimiento para recuperar a mano. **Sin coste
nuevo**: todo vive en el disco de cada host, que ya se paga.

| Pieza | Qué es |
|---|---|
| [`respaldar.sh`](respaldar.sh) | Vuelca las bases de un host a `/opt/nexus/respaldos/<host>/<AAAAMMDD-HHMM>/` (UTC) y aplica la retención |
| [`simulacro-restauracion.sh`](simulacro-restauracion.sh) | Restaura el último respaldo en bases temporales `simulacro_*` del mismo servidor, cuenta lo restaurado y las borra |
| [`.github/workflows/respaldo-dev.yml`](../../.github/workflows/respaldo-dev.yml) | Cada día a las 02:30 UTC (21:30 en Colombia) y a demanda: copia los dos scripts a cada host por `scp` y los ejecuta por `ssh`, como `cd.yml` |

## Qué se respalda

| Host | Contenedor | Qué hay dentro | Formato |
|---|---|---|---|
| plataforma | `plataforma-db` (obligatoria) | `plataformadb`: un esquema por servicio de plataforma (correo, comentarios, salas-partidas…) | `pg_dump -Fc`, un `.dump` por base |
| plataforma | `identidad-db` (obligatoria) | `identidaddb`: cuentas, credenciales (hash), roles, códigos | `pg_dump -Fc` |
| plataforma | `cumplimiento-db`, `ecommerce-db`, `finanzas-db`, `subastas-db`, `chatbot-db` | la base de cada servicio de Cuentas, **si está desplegado** en ese host (capacidad: `infrastructure/despliegue/CAPACIDAD.md`) | `pg_dump -Fc` |
| contenido | `contenido-mongo` (obligatoria) | `heroes`, `inventario`, `productos`… (todas las bases del servidor) | `mongodump --archive --gzip` |

Los nombres salen de `docker-compose.yml`, `docker-compose.deploy.yml`,
`docker-compose.cuentas.yml`, `docker-compose.ms-*.yml` y
`docker-compose.contenido.yml`, pero lo que se vuelca **no** sale de una lista:
es todo contenedor `postgres:*` o `mongo:*` en marcha. Una base nueva queda
respaldada el primer día, no cuando alguien se acuerde de añadirla. Si una
obligatoria no está en marcha, el respaldo sale en rojo; una opcional solo se
anuncia.

Cada respaldo lleva un `MANIFIESTO` (host, sello, fallos, `sha256` y tamaño de
cada fichero). El directorio se prepara como `.<sello>.parcial` y solo se
publica con su nombre al final: nunca hay un respaldo a medias con cara de
completo. Directorios `700`, ficheros `600` (`umask 077`).

**Credenciales.** Ninguna pasa por la línea de órdenes del host ni se imprime.
Cada volcado corre dentro de su contenedor (`docker exec … sh -c`) con las
variables que el contenedor ya tiene (`POSTGRES_USER`/`POSTGRES_PASSWORD`,
`MONGO_INITDB_ROOT_*`): la clave de Postgres va en `PGPASSWORD`; la de Mongo,
el día que la tenga, en un fichero `600` dentro del contenedor (`--config` de
las herramientas de Mongo), y `mongosh` la lee de `process.env`.

**Retención: 7 días**, y nunca se borra el respaldo más reciente ni el último
sin fallos, por viejos que sean. Antes de volcar se comprueba que queda al
menos 1 GiB libre: un respaldo que llena el disco tumba la base que quería
proteger. Hoy las bases de DEV pesan kilobytes o pocos megabytes; siete días
caben de sobra en los 20 GB del host.

## RPO y RTO

| Qué pasa | RPO (datos que se pierden) | RTO (hasta volver a servir) |
|---|---|---|
| Error lógico en una base: migración mala, borrado, un servicio que escribe basura | **≤ 24 h** (respaldo diario). Con el host apagado no cambia nada, así que el fin de semana no cuenta | **~15 min** con el procedimiento de abajo: parar el servicio, restaurar (segundos con estos tamaños), arrancarlo (una JVM tarda 1-2 min en el `t3.small`) y comprobar |
| Se pierde un contenedor o su volumen, el host sigue | ≤ 24 h | ~15-30 min: `cd.yml` vuelve a crear el contenedor y se restaura encima |
| **Se pierde la instancia o su disco** | **Todo**: los respaldos viven en ese mismo disco | ~1 h para volver a tener el entorno (`infra-dev.yml` + `cd.yml`), pero con las bases vacías y las semillas |

La última fila es el límite honesto de «sin coste nuevo». Este respaldo protege
de errores, no de perder la máquina. Opciones, **ninguna activada**:

- **S3**: `aws s3 cp` del directorio del día a un bucket con ciclo de vida de 7
  días. Unos céntimos al mes con estos tamaños, pero es **coste nuevo** en una
  cuenta con Free Plan y necesita un rol para el host (hoy el host no tiene
  claves ni permisos de S3, a propósito). Requiere **autorización de coste del
  Product Owner** antes de escribir una línea.
- **Instantánea EBS** del volumen raíz: mismo caso (USD 0,05/GB-mes).
- **Copia a mano** antes de algo arriesgado (una migración grande, el
  `destroy` del cierre del proyecto): `scp -r ubuntu@<ip>:/opt/nexus/respaldos/plataforma/<sello> .`
  al portátil de un administrador. Son datos personales (correos, hashes de
  contraseñas): nunca al repositorio, a un artefacto de Actions ni a un chat.

## El simulacro

`respaldo-dev.yml` lo corre después de cada respaldo (a demanda se puede
apagar con la casilla `simulacro`). Un respaldo que nunca se ha restaurado no
es un respaldo.

- **PostgreSQL:** cada volcado se restaura con `pg_restore --exit-on-error` en
  una base **nueva** del mismo servidor, `simulacro_<AAAAMMDD_HHMMSS>_<n>`; se
  cuentan sus tablas y sus filas exactas, las tablas se comparan con las del
  índice del volcado (`pg_restore --list`) y la base se borra (también si algo
  falla, con `trap`).
- **MongoDB:** el archivo se restaura en el mismo servidor con cada base
  renombrada a `simulacro_<AAAAMMDD_HHMMSS>_<base>` (`--nsFrom/--nsTo`; `admin`,
  `config` y `local` fuera). Antes, un `--dryRun` con los mismos argumentos dice
  adónde iría cada colección: si una sola no fuera a una base `simulacro_`, se
  para sin escribir nada. Después se cuentan colecciones y documentos, se
  comparan con lo que dice `mongorestore` y las bases se borran.
- **Nunca toca una base real:** ninguna orden nombra una base que no sea
  `simulacro_<fecha>_<hora>[_<n>]` (se comprueba con una expresión regular antes
  de crear, restaurar y borrar), y si una base con ese nombre ya existiera no
  se toca y se para.

Salida típica (probado en local contra PostgreSQL 15 y 17, y Mongo 8.0 con y
sin autenticación):

```
Simulacro de restauracion del respaldo /opt/nexus/respaldos/plataforma/20260927-2045
  ok    identidad-db/identidaddb: 1 tablas y 30 filas restauradas (el indice del volcado dice 1 tablas)
  ok    plataforma-db/plataformadb: 3 tablas y 292 filas restauradas (el indice del volcado dice 3 tablas)
Simulacro de plataforma: 2 restauracion(es) correcta(s); ninguna base real tocada.
```

Para un solo volcado: `simulacro-restauracion.sh --host plataforma --base plataforma-db/plataformadb`.

## Cómo restaurar de verdad, paso a paso

En el host (SSH con la llave de despliegue o Session Manager). Los ejemplos
son de `identidad-db`; para otra base cambia el contenedor, la base y el
servicio que la usa.

1. **Elegir el respaldo** y comprobar que está entero:

   ```bash
   ls /opt/nexus/respaldos/plataforma/
   cd /opt/nexus/respaldos/plataforma/<sello>
   cat MANIFIESTO                                   # "fallos: 0"
   grep -E '^[0-9a-f]{64}  ' MANIFIESTO | awk '{print $1"  "$3}' | sha256sum -c
   ```

2. **Ensayar primero** esa misma base, sin tocar la real:
   `/opt/nexus/infrastructure/respaldo-dr/simulacro-restauracion.sh --host plataforma --base identidad-db/identidaddb`.

3. **Guardar el estado actual**, por si hubiera que volver atrás:
   `/opt/nexus/infrastructure/respaldo-dr/respaldar.sh --host plataforma`.

4. **Parar lo que escribe en esa base** (en `plataformadb`, todos los servicios
   de plataforma que tengan esquema allí):

   ```bash
   docker stop srv-ms-identidad
   ```

5. **Restaurar.** La base entera, desde cero:

   ```bash
   docker exec -i identidad-db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" dropdb -U "$POSTGRES_USER" --if-exists identidaddb \
     && PGPASSWORD="$POSTGRES_PASSWORD" createdb -U "$POSTGRES_USER" identidaddb'
   docker exec -i identidad-db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -U "$POSTGRES_USER" -d identidaddb --no-owner --exit-on-error' \
     < /opt/nexus/respaldos/plataforma/<sello>/identidad-db/identidaddb.dump
   ```

   O un solo esquema de `plataformadb` (solo el servicio afectado se para):

   ```bash
   docker exec -i plataforma-db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -U "$POSTGRES_USER" -d plataformadb --clean --if-exists --no-owner -n correo' \
     < /opt/nexus/respaldos/plataforma/<sello>/plataforma-db/plataformadb.dump
   ```

   MongoDB (una base; `--drop` borra cada colección antes de restaurarla):

   ```bash
   docker stop srv-inventario
   docker exec -i contenido-mongo mongorestore --archive --gzip --drop --nsInclude 'inventario.*' \
     < /opt/nexus/respaldos/contenido/<sello>/contenido-mongo/mongodump.archive.gz
   ```

6. **Arrancar** el servicio (`docker start srv-ms-identidad`). Flyway encuentra
   su tabla de historial restaurada y aplica solo las migraciones posteriores al
   respaldo, si las hay.

7. **Comprobar**: lanzar el smoke (`smoke-dev.yml`, *Run workflow*) o, como
   mínimo, `curl http://localhost:8089/actuator/health` en el host.

## Operación

- **A demanda:** Actions → «Respaldo de DEV» → *Run workflow*.
- **Host apagado:** el job lo detecta (el 22 no contesta) y termina en verde
  con un aviso. No enciende nada: encender cuesta horas de instancia y, apagado,
  no hay cambios que respaldar.
- **A mano en el host:** `respaldar.sh --host plataforma` (o `contenido`).
  `RESPALDO_CONTENEDORES="identidad-db"` limita el volcado a esos contenedores.
- **Probar los scripts en un portátil** (Docker y bash): `RESPALDO_RAIZ` a una
  carpeta temporal y `RESPALDO_CONTENEDORES` con los contenedores de prueba, para
  no tocar otras bases que haya en marcha en esa máquina.
