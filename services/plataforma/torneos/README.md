# torneos (M13)

**Grupo responsable:** Grupo 6 · Tiempo real, comunidad y plataforma
**Requisitos:** RF-TOR-001..005, RF-TOR-007 (premiación, con montos provisionales D-24), RF-ADM-005 (intervención sobre un encuentro y reintento de operaciones). Brecha declarada: RF-TOR-006 (transmisión, INC-18) — ver abajo.
**Contrato:** `contracts/openapi/torneos.yaml` 1.2.0
**Stack:** Java 21 · Spring Boot 4.1 · PostgreSQL (esquema `torneos`, Flyway V1–V2) · resource server de ms-identidad · puerto 8083

## Qué hace

Un torneo cada 91 días (Charter), ocho cupos, equipos de dos jugadores (nombre y avatar
por la lista negra, contexto `NOMBRE_EQUIPO`), inscripción con reserva de créditos en
ms-finanzas (se cobra al iniciar, se devuelve al cancelar), relleno con equipos de la
máquina, árbol de doble eliminación con la numeración de la ficha (ganadores 1-6, 11 y
final; secundarios 7-10, 12 y 13) y premio al equipo campeón. El resultado de un
encuentro lo aporta un servicio (la partida, `ROLE_SERVICIO`) o un administrador con motivo.

## Cobro, devolución y premio idempotentes (B10)

Hasta la 1.1.0, iniciar y cancelar recorrían los equipos llamando al libro de créditos
**dentro** de la transacción que cambiaba el estado del torneo. Una caída a mitad deshacía
el cambio de estado pero no los cobros ya hechos; cancelar después «liberaba» reservas
consumidas sin devolver nada. Ahora:

1. **Ninguna llamada HTTP dentro de una transacción.** Lo que se consulta antes de decidir
   (lista negra, sanciones, la reserva de la inscripción) va antes de abrirla; lo que hay que
   hacer después (cobrar, devolver, premiar, avisar) se guarda como una fila de
   `operaciones` **en la misma transacción** que el cambio de estado, y se ejecuta cuando esa
   transacción ya se confirmó.
2. **Estado persistido por participante** (tabla `operaciones`, V2): tipo, estado
   (PENDIENTE, EN_CURSO, REINTENTABLE, HECHA, FALLIDA, EXCLUIDA, OMITIDA), intentos, próximo
   intento, último error. Se ve en `Equipo.estadoPago` y en `Torneo.premio`.
3. **Claves estables y únicas:** `torneo-<id>-jugador-<uid>-{inscripcion|cobro|devolucion|premio|epica|aviso-<hito>|correo-<hito>}`.
   La columna `clave` es `UNIQUE`, y la misma clave va al proveedor cuando la admite
   (`Idempotency-Key` de la reserva y de inventario, `refId` de `acreditar`, `id` del aviso).
   No llevan el id del equipo porque el libro guarda la clave en 128 caracteres e inventario
   acepta 100 (con tres UUID eran 138); un jugador está en un solo equipo por torneo, así que
   torneo + jugador ya la hace única, y el equipo va en la fila.
4. **Un torneo a la vez:** toda transacción que cambia un torneo bloquea su fila
   (`SELECT … FOR UPDATE`). Dos inicios simultáneos → uno gana y el otro recibe 409; el último
   cupo no se lo llevan dos equipos (el perdedor recupera su reserva); dos resultados a la vez
   no se sobrescriben el árbol. Crear un torneo toma un cerrojo consultivo de PostgreSQL para
   que dos administradores no metan dos torneos en la ventana de 91 días.
5. **Reintento seguro:** `ProcesadorDeOperaciones` reclama cada operación con una sentencia
   condicional (solo una ejecución la toma), la ejecuta sin transacción y anota el resultado.
   `TareaDeOperaciones` reintenta cada `TORNEOS_OPERACIONES_INTERVALO_MS` con espera
   exponencial; si una ejecución muere a mitad, su plazo (`…_PLAZO_BLOQUEO_S`) vence y otra la
   retoma: como la llamada es idempotente, no se cobra ni se entrega dos veces. Tras
   `…_INTENTOS_MAXIMOS` (o si el proveedor la rechaza) queda FALLIDA; el administrador la
   reabre con `POST /torneos/{id}/operaciones/reintento`.
   La propia petición (iniciar, cancelar, la final) intenta sus operaciones en el acto, pero
   solo durante `TORNEOS_OPERACIONES_PRESUPUESTO_SINCRONO_MS` (3000): con un proveedor lento no
   se queda colgada —importa en la final, que la puede informar salas-partidas— y lo que falte
   lo hace la tarea.
6. **Devolución de una reserva ya cobrada** (lo que dejaba la versión anterior): liberar
   responde CONSUMIDA y la devolución acredita el monto con la clave `…-devolucion` como `refId`.

Pruebas deliberadas de duplicación en `CobroIdempotenteIT` (PostgreSQL real, dobles con
estado del libro, inventario y notificaciones): dos inicios simultáneos, el libro cae a mitad,
el servicio muere después de cobrar y antes de anotarlo, reserva ya liberada, cancelación con
el libro caído, devolución de una reserva ya cobrada, dos equipos por el último cupo, dos
administradores creando a la vez, premio con sanción, épica que falla y llega en el reintento.

## Premio (RF-TOR-007)

Al jugarse la final, cada integrante **humano** del equipo campeón recibe
`creditosPorIntegrante` créditos (`POST /creditos/acreditar`, `refId` `…-premio`) y la épica
`epicaProductoId` (`POST /api/v1/inventario/entregas`, origen `PREMIO_TORNEO`,
`Idempotency-Key` `…-epica`). Torneos **nunca** escribe inventario directamente. Un integrante
con sanción activa al premiar no recibe nada (CA-02); la sanción se consulta una vez y queda
anotada. Si gana la máquina no hay premio (no tiene cuenta, mismo criterio que D-18).

**Montos PROVISIONALES (D-24, pendiente del PO):** el documento dice «créditos y una
recompensa épica única para su héroe» y no fija ninguno de los dos.

| Variable | Valor provisional | Por qué |
|---|---|---|
| `TORNEOS_PREMIO_CREDITOS_POR_INTEGRANTE` | `100` | Cifra de demostración, sin respaldo documental |
| `TORNEOS_PREMIO_EPICA_PRODUCTO_ID` | `81af272d-74fb-3dc1-b6ff-01fdc99a1c1d` («Golpe de defensa», Tabla 20) | Es la épica del Guerrero Tanque, el héroe del kit inicial provisional (D-29) |

Cada torneo copia el premio al crearse (`premio_*` en `torneos`), así que cambiar la variable
no altera el de un torneo ya anunciado. `0` y épica vacía = sin premio.

## Avisos y correo (hitos)

Inscripción confirmada (comprobante, HU-TOR-002 CA-01), inicio, cancelación con devolución
(HU-TOR-001 CA-04) y premio entregado (HU-TOR-007 CA-04) llegan a la bandeja de cada
integrante (`POST /internal/notifications`, idempotente por `id`). El documento del curso no
pide correos de torneo (7.4.12 los enumera: cuenta, contraseña, publicidad, misiones y
subasta); aun así queda listo el envío por correo: contacto por `uid` en ms-identidad
(`GET /api/v1/internal/usuarios/{uid}/contacto`) y `POST /correos/torneo` (correo.yaml 1.5.0,
implementado en correo: plantilla corporativa `torneo` con el asunto y el mensaje de torneos,
`torneoId` como referencia y la clave `…-correo-<hito>` como `Idempotency-Key`, así que un
reintento no manda dos copias). Con `TORNEOS_IDENTIDAD_URL` (base sin `/api/v1`) o
`TORNEOS_CORREO_URL` (base con `/api/v1`) vacías no se crea ninguna operación de correo; en
`docker-compose.deploy.yml` siguen vacías (solo bandeja). Para activarlo en dev:
`TORNEOS_IDENTIDAD_URL=http://srv-ms-identidad:8089` y
`TORNEOS_CORREO_URL=http://srv-correo:8082/api/v1`.

## Transmisión (RF-TOR-006) — brecha declarada, no simulada

El documento pide «live-stream o video-stream de las justas acompañadas de los comentarios de
los presentadores». No hay servidor de vídeo, grabación ni canal de presentadores en la
infraestructura del proyecto (dos t3.small del plan gratuito; INC-18, D-24), y no se finge uno:
ninguna ruta de `torneos.yaml` transmite. Lo que sí existe es el árbol en vivo
(`GET /torneos/{id}`) y el canal STOMP de cada partida en salas-partidas, donde se juega el
encuentro. Queda en el Product Backlog (HU-TOR-006, #491) a la espera de la decisión del PO.

## Dependencias por API (regla 7: nunca la base de otro)

| Variable | Qué | Sin ella |
|---|---|---|
| `CREDITOS_URL` | ms-finanzas (`creditos.yaml`) | inscribir con costo > 0 → 503 `LIBRO_NO_DISPONIBLE`; cobros, devoluciones y premios quedan pendientes |
| `LISTA_NEGRA_VERIFICAR_URL` | moderacion-sanciones | registrar equipo → 503 `LISTA_NEGRA_NO_DISPONIBLE` |
| `SANCIONES_URL` | moderacion-sanciones (consulta 1.1.0) | inscribir → 503 `SANCIONES_NO_DISPONIBLES`; el premio espera |
| `INVENTARIO_BASE_URL` | inventario (`/api/v1/inventario/entregas`) | la épica del premio queda pendiente |
| `NOTIFICACIONES_URL` | notificaciones (`/internal/notifications`) | los avisos quedan pendientes |
| `TORNEOS_IDENTIDAD_URL`, `TORNEOS_CORREO_URL` | contacto y correo | sin correo (vacías por omisión) |
| `DIRECTORIO_ACTIVO_*` | credencial de servicio (ADR-005) | el libro, inventario y notificaciones rechazan (401) |
| `TORNEOS_HTTP_TIMEOUT_*_MS` | tiempos de espera (2000 / 5000) | — |

## Decisiones registradas

D-21 (rol administrador de torneo), D-22 (encuentro 2 contra 2), D-23 (valor de
inscripción y quién paga), D-24 (premiación con valores provisionales; transmisión
pendiente) y D-26 (encuentro ganado por la máquina) en
`docs/gobierno/DECISIONES-PENDIENTES-DEL-PO.md`.

## Pruebas

`./gradlew :services:plataforma:torneos:check` — `ArbolTest` (incluye el estado final de los
ocho equipos, #589), `OperacionesTest`, `TorneosServiceIT` y `CobroIdempotenteIT`
(PostgreSQL real, reloj movible), `TorneosControllerTest`, `ClientesHttpTest`,
`ServidorFalsoTest` (servidor HTTP del JDK: tiempos de espera y claves en cabecera).
E2E: `tests/e2e/torneos.e2e.spec.js`. Vista: `frontend/app-web/src/plataforma/torneos/`.
