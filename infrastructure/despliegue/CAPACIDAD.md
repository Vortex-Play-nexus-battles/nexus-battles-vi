# Capacidad de los hosts de DEV — medición del 23 de septiembre de 2026

Este archivo existe porque durante semanas la decisión de «qué se despliega en
dev» se apoyó en una frase heredada de comentario en comentario —
*«~330 MB libres y 1,1 GB en swap»*— que nadie volvió a comprobar. Aquí está la
medición real, con fecha, y cómo reproducirla.

```
gh workflow run diagnostico-dev.yml -f ambiente=dev
gh workflow run diagnostico-dev.yml -f ambiente=contenido
gh workflow run medir-memoria-servicios.yml
```

Si los números cambian, **actualiza este archivo con la fecha nueva** en vez de
dejar que la cifra vieja siga circulando por los comentarios del código. Esa es
exactamente la falla que este archivo viene a cerrar. No lo conviertas en
configuración: nada en el pipeline lee estos números.

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
