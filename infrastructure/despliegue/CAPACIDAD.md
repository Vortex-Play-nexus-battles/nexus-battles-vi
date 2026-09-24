# Capacidad de los hosts de DEV — mediciones del 23 y 24 de septiembre de 2026

Este archivo existe porque durante semanas la decisión de «qué se despliega en
dev» se apoyó en una frase heredada de comentario en comentario —
*«~330 MB libres y 1,1 GB en swap»*— que nadie volvió a comprobar. Aquí está la
medición real, con fecha, y cómo reproducirla.

```
gh workflow run diagnostico-dev.yml -f ambiente=dev
gh workflow run diagnostico-dev.yml -f ambiente=contenido
gh workflow run medir-memoria-servicios.yml
gh workflow run medir-jvm-dev.yml            # RAM + swap de cada contenedor, solo lectura
gh workflow run experimento-flags-jvm.yml    # A/B de flags en el banco E2E, con la suite como carga
```

## 24-sep 03:50 UTC — la prevision de abajo NO se cumplio: el host esta lleno

`medir-jvm-dev.yml`, corrida 35952844520, con 12 JVM en el host (las 9 de
plataforma + ms-finanzas, ms-cumplimiento y ms-ecommerce; 4 de ellas ya con los
flags nuevos): **RAM disponible 20 MiB y swap 2047 de 2047 MiB**. La propia
medicion se corto por tiempo a mitad de la lista.

| Contenedor | RAM | swap | total | flags |
|---|--:|--:|--:|---|
| `srv-ms-finanzas` | 122 | 180 | 302 | viejos |
| `srv-moderacion-sanciones` | 93 | 184 | 277 | nuevos |
| `srv-comentarios` | 86 | 181 | 267 | viejos |
| `srv-admin-parametros` | 78 | 187 | 265 | viejos |
| `srv-ms-cumplimiento` | 72 | 170 | 242 | viejos |
| `srv-ms-ecommerce` | 145 | 95 | 240 | nuevos |
| `srv-correo` | 109 | 114 | 223 | viejos |
| `srv-metricas-plataforma` | 75 | 109 | 184 | viejos |
| (identidad, notificaciones, salas, torneos: la medicion no llego) | | | | |

**Donde fallo la cuenta.** La prevision (≈2780 MiB con las 14 JVM) salio de
la foto de las 01:58, tomada 7 minutos despues de un reinicio, con los montones
recien nacidos. Con horas de uso, una JVM de este host ocupa **240-300 MiB**
tambien con los flags nuevos: el monton comprometido es 70-85 MiB, y el resto
es no-monton y la cache de archivos de una imagen JDK. Doce JVM ya llenan el
host. Los flags nuevos siguen siendo mejores que los viejos (el experimento lo
repite dos veces), pero **no crean sitio para dos servicios mas**.

**Decision, con esta medicion:**

- `ms-subastas` y `ms-chatbot` vuelven a `desplegableDev: false`. No
  caben, y desplegarlos tumbaria a los que si estan. Su bloqueo es de
  capacidad, medido, no de codigo.
- Se termina de aplicar los flags nuevos a las JVM que aun tienen los viejos,
  de dos en dos, para bajar la presion.
- **Salidas que quedan, sin tercer EC2 ni cambiar de tamano:** (a) llevar
  2-3 servicios al host de contenido, que tiene RAM libre, con acuerdo del
  grupo 2 (su cuenta, su grupo de seguridad) y resolviendo que esos servicios
  hablan con los de plataforma por IP publica; (b) consolidar los Postgres
  (≈200 MiB, ver abajo) y bajar el monton a 96 MiB con medicion; (c) aceptar
  perfiles para los servicios de administracion. Las tres son decision del
  equipo o del PO, no de capacidad. Quedan abiertas.
## 24-sep — la memoria se va en el no-montón, y el montón crecía sin freno (R16.5)

### Lo que ocupa hoy el host de plataforma

`medir-jvm-dev.yml`, corrida 35945219416, 7 minutos después de un reinicio. RAM
y swap salen del cgroup de cada contenedor: son del núcleo, no estimaciones.
`docker stats` no ve el swap, y por eso las cifras del 23-sep eran la mitad de
la verdad.

| Contenedor | RAM | swap | total | montón comprometido | techo del montón |
|---|--:|--:|--:|--:|--:|
| `srv-ms-cumplimiento` | 182 | 53 | 235 | 50 | 180 |
| `srv-salas-partidas` | 152 | 83 | 235 | — | 270 |
| `srv-moderacion-sanciones` | 175 | 56 | 231 | 54 | 270 |
| `srv-ms-ecommerce` | 170 | 61 | 231 | 53 | 180 |
| `srv-notificaciones` | 166 | 52 | 218 | 51 | 270 |
| `srv-admin-parametros` | 138 | 56 | 194 | 52 | 270 |
| `srv-comentarios` | 115 | 59 | 174 | 52 | 270 |
| `srv-metricas-plataforma` | 25 | 143 | 168 | 55 | 270 |
| `srv-torneos` | 81 | 86 | 167 | 53 | 270 |
| `srv-ms-identidad` | 60 | 106 | 166 | 55 | 314 |
| `srv-correo` | 45 | 119 | 164 | 36 | 270 |
| `plataforma-db` | 33 | 85 | 118 | | |
| `cumplimiento-db` | 19 | 28 | 47 | | |
| `ecommerce-db` | 8 | 27 | 35 | | |
| `mailpit` · `identidad-db` · `plataforma-cache` · `srv-borde` | 40 | 26 | 59 | | |
| **suma** | **1402** | **1040** | **2442** | | |

Host: 1910 MiB de RAM (127 disponibles) y 1021 de 2047 MiB de swap en uso. No
corrían `ms-finanzas`, `ms-subastas` ni `ms-chatbot`.

Lo que dice la tabla: cada JVM tiene **~50 MiB de montón comprometido** y ocupa
**165-235 MiB**. El resto es no-montón: metaspace, la code cache del compilador
C2 y los hilos. Y el techo del montón (70 % del `mem_limit`, 270 MiB) no frena
nada: es el «reposo mayor que el pico» que se anotó el 23-sep.

### El experimento: mismos servicios, mismo `mem_limit`, flags distintos

`experimento-flags-jvm.yml`, corridas 35946114614 y 35949136685. Levanta el banco E2E dos
veces, con el `mem_limit` de dev en las dos, y pasa la suite E2E entera como
carga. Mide la memoria anónima del cgroup, que es la que no se puede soltar y
acaba en swap.

| Corrida | | línea base `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC` | desplegados (`docker-compose.deploy.yml`) |
|---|---|--:|--:|
| 1 | memoria anónima en reposo, 21 contenedores | 3510 MiB | **3067 MiB (−443, −12,6 %)** |
| 1 | memoria anónima tras la suite E2E | 4014 MiB | **3289 MiB (−725, −18 %)** |
| 1 | arranque del banco entero · suite E2E | 135 s · 100/101 (`recompensa-por-partida` por tiempo) | 112 s · 101/101 |
| 2 | memoria anónima en reposo | 3568 MiB | **3134 MiB (−434, −12,2 %)** |
| 2 | memoria anónima tras la suite E2E | 3881 MiB | **3347 MiB (−534, −13,8 %)** |
| 2 | arranque del banco entero · suite E2E | 118 s · 101/101 | 120 s · 101/101 |

Lo que se repite en las dos corridas es la memoria: −12 % en reposo y entre −14 % y −18 % tras la carga. El tiempo de arranque **no** cambia de forma apreciable (135→112 s en una, 118→120 s en la otra: ruido del runner), y el fallo de la línea base en la primera corrida fue de tiempo en una partida contra la IA, que en la segunda pasó.

Flags desplegados: `-XX:+UseSerialGC -Xmx128m -XX:TieredStopAtLevel=1
-XX:ReservedCodeCacheSize=48m -XX:+ExitOnOutOfMemoryError`.

- El montón **usado** tras la carga no pasa de 66 MiB en ningún servicio, con
  ninguno de los dos juegos de flags. `-Xmx128m` deja el doble de margen.
- El no-montón baja de 12 a 30 MiB por JVM al quitar el C2.
- `ms-finanzas` y `ms-subastas` casi no cambian: sus techos (192m y 256m) ya
  los frenaban. Por eso `ms-finanzas` sube a `mem_limit: 256m`: con 192m el
  techo quedaba por debajo de su no-montón más el montón.

Las cifras absolutas del banco (imágenes JRE sobre glibc) son más altas que las
de dev (JDK sobre musl, Alpine). Lo que se traslada es la proporción.

### Presupuesto del host de plataforma con todo encendido

| Partida | MiB |
|---|--:|
| Hoy, medido: 11 JVM + 4 Postgres + auxiliares, flags viejos | 2442 |
| Flags nuevos sobre las 11 JVM (−12,6 % de 2183, lo medido en reposo) | −275 |
| `ms-finanzas` + `ms-subastas` + `ms-chatbot` con flags nuevos (banco × 0,78, la proporción dev/banco medida en reposo) | +480 |
| Sus tres Postgres (lo que miden `cumplimiento-db` y `ecommerce-db`) | +135 |
| **Previsto** | **≈ 2780** |
| RAM + swap del host | 3957 |

Cabe entero, con un swap parecido al de hoy (≈ 1,1 GiB) y **tres servicios
más**. El swap no es el problema mientras lo que está en uso quepa en RAM, y con
el montón acotado y sin C2 lo que una JVM ociosa toca es poco. Lo que sí tumbó
el host el 24-sep fue el **arranque simultáneo** tras un reinicio: Docker
levanta todos los contenedores a la vez, sin respetar `depends_on`. Con el
montón acotado cada arranque pide menos memoria, pero el experimento no muestra
un arranque más rápido, y la tormenta sigue existiendo: queda anotada como
riesgo abierto.

*Corregido a las 03:50 UTC, ver la seccion de arriba: la prevision no se cumplio.*
Con ella, `ms-finanzas`, `ms-subastas`, `ms-ecommerce` y `ms-cumplimiento` pasaron
a `desplegableDev: true`; `ms-subastas` ha vuelto a `false`.
`ms-chatbot` tambien (R16.22): no tenia credenciales de base de datos en el
entorno `dev` (`MS_CHATBOT_DB_*`), y ahora `desplegar.sh` las genera en el propio
host, como las credenciales de servicio.

**Regla al desplegar, la misma que antes:** medir con `medir-jvm-dev.yml` antes
y después. Si la RAM disponible baja de ~80 MiB y el swap crece deprisa, o un
servicio del MVP degrada, se revierte el último servicio añadido, no el MVP. Y
este archivo se actualiza con la medición real después del despliegue: lo de
arriba es una previsión.

### Consolidar Postgres: evaluado, no ejecutado

Con todo encendido habría **7 procesos Postgres** en el host: `plataforma-db`
(118 MiB, 7 esquemas), `identidad-db` (23) y uno por cada servicio de economía
(35-47 cada uno). En total, ≈ 360 MiB. Un solo proceso Postgres 17 con una base
y un usuario por servicio (sin esquemas ni tablas compartidas, regla 7)
rondaría los 150 MiB: **≈ 200 MiB menos**.

No se hace ahora, y no por falta de beneficio:

- Hay que migrar los datos de cada volumen con volcado y restauración, el libro
  de créditos incluido, sin pérdida. `plataforma-db` además es Postgres 15.
- La V1 de `ms-cumplimiento` usa `dblink`, que pide superusuario o permiso para
  crear la extensión. Un usuario por servicio sin privilegios no lo tiene.
- La suma de los pools de Hikari (10 por servicio) supera `max_connections=100`.
  Hay que acotarlos antes.

Si la medición tras el despliegue muestra presión de swap con todo encendido,
este es el siguiente paso, con su propio plan de migración y su prueba de
restauración.

Si los números cambian, **actualiza este archivo con la fecha nueva** en vez de
dejar que la cifra vieja siga circulando por los comentarios del código. Esa es
exactamente la falla que este archivo viene a cerrar. No lo conviertas en
configuración: nada en el pipeline lee estos números.

---

*Lo que sigue es la medición del 23-sep y la decisión que se tomó con ella. Se
conserva como historia: la decisión de los perfiles la sustituye la sección de
arriba.*

## Los dos hosts NO están en la misma cuenta de AWS

Esto se discutía de memoria y ya costó un día entero (#666). Ahora está
comprobado: cada máquina lo dice de sí misma, desde dentro, por el servicio de
metadatos de EC2 — que no necesita credenciales y funciona aunque el rol OIDC
de un host no vea al otro, que era justo lo que impedía compararlos.

| | `nexus-plataforma-dev` | `nexus-contenido-dev` |
|---|---|---|
| **cuenta AWS** | **362403299569** | **551262695144** |
| instancia | `i-072c81aa0b68370cf` | `i-0388a00d533e39039` |
| tipo | t3.small | t3.small |
| región / AZ | us-east-1f | us-east-1c |
| **VPC** | `vpc-05feed1d917793dd3` | `vpc-0c20d0cf549519754` |
| subred | `subnet-02caa4037a3dbf4b4` | `subnet-0169022dc456017a6` |
| grupo de seguridad | `sg-005c60a57c33baac1` | `sg-01bfe668448b037c4` |
| IP privada | 172.31.76.191 | 172.31.27.94 |
| perfil IAM | `nexus-plataforma-dev-host` | ninguno |
| encendida desde | 2026-09-23 | 2026-09-09 |
| lo administra | `infra-dev.yml` (rol `AWS_ROLE_ARN`) | el grupo 2; `AWS_ROLE_ARN_CONTENIDO` sin configurar |

Cuentas distintas, **VPC distintas y sin emparejar**. El CD despliega en los dos
por SSH con claves separadas (`SSH_KEY_DEV` y `SSH_KEY_CONTENIDO_DEV`), pero
solo puede hablar con AWS en la cuenta de plataforma.

### Por qué el host de contenido NO es un host auxiliar para economía

Tiene más RAM libre, y aun así la respuesta es no:

- **No hay camino privado entre las dos VPC.** El borde llega a heroes,
  inventario y productos por `34.193.90.11:810x`, o sea **por internet**, con
  esos puertos abiertos en su grupo de seguridad. Poner ahí el libro de créditos
  significaría publicar un servicio financiero en un puerto público de una
  cuenta que no administramos. Hoy `ms-finanzas` vive dentro de la red de
  Compose, inalcanzable salvo por el borde.
- **No hay rol OIDC** hacia esa cuenta: el CD no puede encenderla, medirla ni
  gobernarla. Ni siquiera tiene perfil IAM de instancia.
- Sus contenedores corren con `restart: no`: si se cae uno, se queda caído.

Formalizar el acceso (rol OIDC, red, propiedad, presupuesto) es un acuerdo entre
grupos, no una decisión de capacidad. Queda propuesto; **no ejecutado**.

## Estado de cada host

| Host | RAM | Disponible | Swap usado | Contenedores | Suma de `mem_limit` | Disco libre |
|---|--:|--:|--:|--:|--:|--:|
| plataforma | 1910 MiB | **218 MiB** | **1429 / 2047 MiB** | 14 | 4144 MiB | 7,5 GB de 20 GB |
| contenido | 1910 MiB | **633 MiB** | 150 / 2047 MiB | 6 | 1760 MiB | 6,1 GB de 16 GB |

Sin OOM-killer en `dmesg` ni `journalctl` de los últimos 7 días, y cero
reinicios en todos los contenedores de los dos hosts. El host de plataforma **no
se está cayendo**: funciona al límite apoyado en el swap. No es lo mismo, y la
diferencia importa para decidir.

### Memoria real por contenedor — host de plataforma

| Contenedor | Uso / límite | Contenedor | Uso / límite |
|---|--:|---|--:|
| `srv-moderacion-sanciones` | 296 / 384 MiB | `srv-correo` | 101 / 384 MiB |
| `srv-salas-partidas` | 274 / 384 MiB | `srv-notificaciones` | 92 / 384 MiB |
| `srv-admin-parametros` | 192 / 384 MiB | `srv-ms-identidad` | 85 / 448 MiB |
| `srv-metricas-plataforma` | 71 / 384 MiB | `srv-comentarios` | 64 / 384 MiB |
| `srv-torneos` | 60 / 384 MiB | `plataforma-db` | 37 / 256 MiB |
| `identidad-db` | 16 / 192 MiB | `mailpit` | 13 / 64 MiB |
| `plataforma-cache` | 2 / 64 MiB | `srv-borde` | 2 / 48 MiB |

## Lo que pide de verdad cada servicio de economía

Medido levantando cada uno **solo**, con su propia base (corrida `35922011495`).
Antes se decidía con el `mem_limit`, que es un techo y no un consumo.

Con el techo original de **384 MiB**:

| Servicio | Pico de arranque | Reposo | Base de datos | Total | Arranque |
|---|--:|--:|--:|--:|--:|
| `ms-finanzas` | 297 MiB | 300 MiB | 46 MiB | **346 MiB** | 13 s |
| `ms-subastas` | 332 MiB | 336 MiB | 47 MiB | **383 MiB** | 12 s |
| `ms-cumplimiento` | 245 MiB | 248 MiB | 47 MiB | **295 MiB** | 9 s |
| `ms-ecommerce` | 263 MiB | 266 MiB | 47 MiB | **313 MiB** | 9 s |

El dato que cambia la conversación: **el reposo es mayor que el pico**. Eso no
es un servicio que crezca con el uso; es un JVM que se expande hasta llenar lo
que le dejan. Con `MaxRAMPercentage=70` sobre 384 MiB, el montón puede llegar a
269 MiB y la JVM no tiene razón para recolectar antes. En el host real,
`comentarios` y `torneos` —del mismo tamaño y con el mismo techo— viven con 60
y 64 MiB, porque ahí sí hay presión de memoria.

Conclusión: **ese número era el techo hablando, no la necesidad.** Por eso los
cuatro bajan a `mem_limit: 256m` (montón ≈ 179 MiB) y `128m` para su Postgres,
que medido ocupa 46 MiB.

## La decisión

Con 218 MiB disponibles en plataforma, **cabe como mucho un servicio de
economía**, y por eso existen los perfiles de despliegue (`servicios.json`):
se pide el que haga falta para la demo que toca, no los cuatro a la vez.

Prioridad, por valor demostrable:

1. **`ms-finanzas`** — desbloquea HU-PAG-002 (#536), HU-JUE-012 (#470) y tres
   de los cinco grupos de rutas en 502 (`/creditos`, `/transacciones`,
   `/cofres`).
2. **`ms-cumplimiento`** — quita el WARN continuo de `ms-identidad`, que hoy no
   puede auditar ningún intento de login, y abre `/admin/auditoria`.
3. **`ms-subastas`** — REST y STOMP funcionan, pero el cierre de la subasta no:
   falta `POST /inventario/elementos/{id}/transferencias` (#669). Desplegarlo no
   lo vuelve demostrable de punta a punta.
4. **`ms-ecommerce`** — depende del reparto de `GET /api/v1/productos` (#421).

El resto se demuestra en el banco E2E, donde los cuatro caben.

### Regla al desplegar uno

Antes y después: `free -m`, `swapon --show`, `docker stats`, reinicios y
`OOMKilled`. Si después del despliegue la RAM disponible baja de ~80 MiB, el
swap crece deprisa o algún servicio del MVP degrada, **se revierte ese servicio,
no el MVP**.

## Deuda conocida

- `ms-identidad` sigue con secrets `TODO_DB_*`: está desplegado y funcionando
  con esos valores, y migrarlo exige conocerlos. Los otros cuatro servicios de
  economía ya usan nombres definitivos, con las variables no sensibles como
  variables y solo la credencial como secret.
- El host de plataforma tiene 54 imágenes de Docker (14 en uso) y ~5 GB
  reclamables. No libera RAM —el cuello es memoria, no disco— pero conviene
  podarlo. `desplegar.sh` ya poda al final de cada despliegue correcto.
