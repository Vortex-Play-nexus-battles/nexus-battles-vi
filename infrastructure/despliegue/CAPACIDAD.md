# Capacidad de los hosts de DEV — medicion del 23 de septiembre de 2026

Este archivo existe porque durante semanas la decision de "que se despliega en
dev" se apoyo en una frase heredada de comentario en comentario —
*"~330 MB libres y 1,1 GB en swap"*— que nadie volvio a comprobar. Aqui esta
la medicion real, con fecha, y como reproducirla.

Como se obtuvo: `gh workflow run diagnostico-dev.yml -f ambiente=dev` y
`-f ambiente=contenido`. El workflow no cambia nada, solo mira.
Corridas: `35914349093` (plataforma) y `35914754734` (contenido).

## Los dos hosts

| Host | Instancia | Tipo | RAM | RAM disponible | Swap usado | Disco libre | Contenedores | Suma de `mem_limit` | Riesgo |
|---|---|---|--:|--:|--:|--:|--:|--:|---|
| `nexus-plataforma-dev` | `i-072c81aa0b68370cf` | t3.small | 1910 MiB | **135 MiB** | **1364 / 2047 MiB** | 8,2 GB de 20 GB | 14 activos + 1 salido | 4144 MiB | **ALTO** |
| `nexus-contenido-dev` | cuenta del grupo 2 | t3.small | 1910 MiB | **1095 MiB** | 149 / 2047 MiB | 6,1 GB de 16 GB | 6 | 1760 MiB | BAJO |

Sin OOM-killer en `dmesg` ni en `journalctl` de los ultimos 7 dias, y cero
reinicios en todos los contenedores de los dos hosts. Es decir: el host de
plataforma **no se esta cayendo**, esta funcionando al limite y apoyandose en
el swap. Eso no es lo mismo, y la diferencia importa para decidir.

## Memoria real por contenedor — host de plataforma

| Contenedor | Uso / limite | Contenedor | Uso / limite |
|---|--:|---|--:|
| `srv-moderacion-sanciones` | 296 / 384 MiB | `srv-correo` | 101 / 384 MiB |
| `srv-salas-partidas` | 274 / 384 MiB | `srv-notificaciones` | 92 / 384 MiB |
| `srv-admin-parametros` | 192 / 384 MiB | `srv-ms-identidad` | 85 / 448 MiB |
| `srv-metricas-plataforma` | 71 / 384 MiB | `srv-comentarios` | 64 / 384 MiB |
| `srv-torneos` | 60 / 384 MiB | `plataforma-db` | 37 / 256 MiB |
| `identidad-db` | 16 / 192 MiB | `mailpit` | 13 / 64 MiB |
| `plataforma-cache` | 2 / 64 MiB | `srv-borde` | 2 / 48 MiB |

Suma real: ~1304 MiB de contenedores sobre 1910 MiB de RAM; el resto
(~300 MiB) es `dockerd` y el sistema. Los limites declarados suman 4144 MiB:
Docker no reserva, asi que eso no es un error en si mismo, pero mide cuanto se
esta confiando en el swap.

## La decision

**ms-finanzas NO se despliega hoy en el host de plataforma.**

El razonamiento, con los numeros de arriba:

- Un Spring Boot de este monorepo ocupa entre 60 y 300 MiB segun su carga; su
  pico es el arranque, con Flyway y Hibernate a la vez. `ms-finanzas` arranca
  Flyway creando el esquema `finanzas`, asi que estaria en la parte alta.
- Su Postgres propio suma otros 20-40 MiB en reposo (medido en `identidad-db`:
  16 MiB).
- Contra 135 MiB disponibles y 683 MiB de swap libre, el arranque entraria
  casi entero en swap, y lo que el nucleo empujaria a disco para hacerle sitio
  serian las paginas frias de `srv-salas-partidas` y `srv-moderacion-sanciones`
  —los dos contenedores mas grandes y los que sostienen el MVP.
- El riesgo no es que `ms-finanzas` no arranque: es que la demo del Sprint
  Review se vuelva lenta o se caiga en el servicio equivocado.

Esto es una decision de **capacidad medida**, no un problema del codigo:
HU-PAG-002 (#536) y HU-JUE-012 (#470) estan completas y con CI en verde.

### Lo que si cambia

El catalogo (`servicios.json`) marca `ms-finanzas`, `ms-subastas`,
`ms-ecommerce` y `ms-cumplimiento` con `desplegableDev: false`. Eso ahora
significa algo distinto a lo de antes, y conviene no confundirlo nunca mas:

- **Antes**: el pipeline no sabia que `ms-finanzas` y `ms-subastas` existian.
  Ni imagen, ni compose, ni forma de pedirlos a demanda. Un olvido.
- **Ahora**: el pipeline los conoce, construye y publica su imagen en cada
  push, tiene su `docker-compose` y sabe desplegarlos. Lo unico que los frena
  es la RAM del host, y se puede forzar el dia que haya sitio con
  `workflow_dispatch` + `servicios: ms-finanzas`.

### Las otras dos opciones, y por que no

- **Host de contenido** (1095 MiB libres): cabe de sobra, tecnicamente es el
  mismo patron que ya usan heroes/inventario/productos (el borde los enruta
  por IP y puerto, no por DNS de Docker). Pero es la instancia del **grupo 2**,
  en **otra cuenta de AWS**, y sus contenedores corren con
  `restart: no` —si se cae uno, se queda caido—. Meter ahi los servicios de
  economia de Cuentas ata la demo de un equipo a la maquina de otro. Es una
  decision de coordinacion entre grupos, no tecnica: **queda propuesta, no
  ejecutada**.
- **Tercer host o instancia mas grande**: prohibido por las reglas del
  proyecto (plan gratuito, sin gasto nuevo).

Mientras tanto, las dos HU se demuestran en el banco E2E completo, donde
`ms-finanzas` y `ms-subastas` si caben.

## Higiene pendiente (no urgente)

El host de plataforma tiene 54 imagenes de Docker (14 en uso) y ~4,97 GB
reclamables. No libera RAM —el cuello es memoria, no disco— pero conviene
podarlo antes de que los 8,2 GB libres se vuelvan un segundo problema.
`desplegar.sh` ya poda al final de cada despliegue correcto.

## Como repetir la medicion

```
gh workflow run diagnostico-dev.yml --ref develop -f ambiente=dev
gh workflow run diagnostico-dev.yml --ref develop -f ambiente=contenido
```

Si los numeros cambian, **actualiza este archivo con la fecha nueva** en vez
de dejar que la cifra vieja siga circulando por los comentarios del codigo.
Esa es exactamente la falla que este archivo viene a cerrar.
