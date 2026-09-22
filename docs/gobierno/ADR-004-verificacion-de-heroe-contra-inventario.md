# ADR-004 · Verificación de héroe contra inventario (HU-SAL-003)

**Estado:** aceptado para la implementación de HU-SAL-003.
**Fecha:** 17-sep-2026. **Dueño:** Plataforma (Grupo 6).
**Disparador:** issue #27 (HU-SAL-003 · RF-JUE-003, RF-INV-009, RF-MIS-012).

## Problema

`GET /salas/{idSala}/verificación-heroe` ya estaba publicado en
`contracts/openapi/salas-partidas.yaml` y el frontend ya lo consume
(`cliente-salas.js`, `validacion-heroe.js`). Faltaba el backend. Para
construirlo hacía falta decidir tres cosas que el contrato no fija:

1. **Qué significa «héroe equipado»** y de dónde sale ese dato.
2. **Quién decide que un héroe está ocupado**.
3. **Qué hacer cuando el proveedor no responde.**

## Contexto auditado

- El issue #27 fija la frontera: «la disponibilidad del héroe (bloqueo por
  misión o torneo) la resuelven RF-INV-009 y RF-MIS-012 del equipo de Thomas;
  este ítem **consume su respuesta** y rechaza el ingreso cuando el servicio la
  reporta».
- `contracts/openapi/inventario.yaml` publica lo necesario y nada más:
  `GET /api/v1/inventario/elementos` (vitrina del jugador, con `tipo` y
  `disponible`), `GET /api/v1/inventario/heroes/{id}/equipamiento` y
  `GET /api/v1/inventario/heroes/{id}/estadisticas`.
- Ese contrato **no marca ningún héroe como «el activo»**: no existe tal campo.
- Las rutas de jugador de inventario identifican por la cabecera
  `X-User-Name` — «las rutas del jugador conservan temporalmente X-User-Name».
- El puerto `CreditosDelJugador` de este servicio solo sabe *reservar* y
  *liberar*. No tiene consulta de saldo, y esa ausencia es deliberada.

## Decisiones

### 1. Equipado es llevar algo puesto

Un héroe está equipado si `GET /inventario/heroes/{id}/equipamiento` devuelve al
menos un arma, una armadura o un ítem. Es la lectura literal de RF-JUE-003
contra lo único que inventario publica al respecto.

La regla vive en el adaptador, no en el dominio: si contenido decide mañana que
hace falta un mínimo por ranura, cambia en su servicio y aquí no se toca nada.

### 2. La ocupación la decide el proveedor, no nosotros

`HEROE_OCUPADO` sale de `disponible: false` en la vitrina. Este servicio **no**
calcula si un héroe está comprometido: no lleva registro de qué héroe trae cada
participante y añadirlo sería duplicar el estado del que ya es dueño otro
módulo, con las dos copias divergiendo el primer día.

Se descartó la alternativa de mirar las salas propias: RF-INV-009 y RF-MIS-012
cubren misión, torneo y subasta, y una sala no ve nada de eso.

### 3. Cuál héroe se elige cuando hay varios

El primero de la vitrina que esté disponible y equipado, en el orden en que la
devuelve su dueño. No hay concepto de «héroe activo» en el contrato de
inventario, así que cualquier otra elección sería inventada. Si ninguno sirve,
el resultado distingue el motivo —equipar o esperar— porque son dos acciones
distintas para el jugador y el diálogo tiene una variante para cada una.

### 4. Sin inventario, 503 — nunca un veredicto inventado

Si el proveedor no responde, la verificación devuelve `503` con tipo
`https://nexusbattles.local/errores/inventario-no-disponible`, y el frontend
pinta su estado de error con reintento (RNF-USA-003).

Responder `DISPONIBLE` por defecto mandaría al jugador a pulsar Entrar para que
lo rechacen después, que es exactamente lo que RF-JUE-003 quiere evitar.
Responder `SIN_HEROE_EQUIPADO` acusaría al jugador de algo que no sabemos. El
precedente es el chat: cuando la lista negra no contesta, el mensaje **no** se
publica sin verificar.

### 5. `CREDITOS_INSUFICIENTES` no se emite todavía

El contrato declara ese valor y la respuesta lo permite, pero este servicio no
puede producirlo con honestidad: no existe operación de consulta de saldo, y
añadir una solo para esto abriría el hueco de comprobar-sin-reservar que
`CreditosDelJugador` documenta como el motivo de no tenerla.

La respuesta informa `creditosRequeridos` —la recompensa de la sala, dato
propio— y deja `creditosDisponibles` vacío. El día que exista un proveedor de
créditos con consulta, se añade sin cambiar el contrato.

### 6. La identidad va en `X-User-Name` mientras inventario la pida así

ADR-001 fija OAuth2 `client_credentials` como destino, pero mientras el
proveedor no acepte el token, mandar otra cosa sería mandar algo que no mira.
El apodo se toma del token (`preferred_username`, o `apodo`, o el sujeto) y se
transporta en `JugadorAutenticado` junto al identificador estable, para que el
apodo no se filtre al dominio: las salas siguen guardando gente por UUID.

### 7. `HeroeEnPartida.id` deja de ser `uuid`

`ElementoInventario.id` de inventario es `type: string`, una cadena opaca.
Exigir `format: uuid` en nuestro esquema rompía la verificación con inventarios
perfectamente válidos. El cambio es compatible hacia atrás para quien ya lo
consumía: un UUID sigue siendo una cadena.

## Consecuencias

- La verificación previa funciona de extremo a extremo en cuanto inventario esté
  desplegado en el entorno. Hoy no lo está, así que en `dev` responde 503 — y lo
  dice.
- **La puerta del ingreso sigue abierta.** `POST /salas/{id}/participantes` aún
  no consulta este puerto: hacerlo con inventario ausente dejaría el flujo de
  HU-SAL-002 —que hoy funciona— devolviendo 503 en todas las entradas. Queda
  como tarea siguiente, ligada a desplegar inventario en el entorno; hasta
  entonces HU-SAL-003 **no está terminada**, y el issue #27 lo refleja.
