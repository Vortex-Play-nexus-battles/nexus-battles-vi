# ms-subastas

**Sprint:** 2 · **Modulo:** M12 — Subasta · **Historias:** HU-SUB-001 (Edwin), HU-SUB-011 (Cristian), HU-SUB-004 (Andres)

Mercado de subastas entre jugadores: publicar un producto, listarlos con filtros en vivo, y pujar o comprar de forma inmediata.

> **B8 (septiembre de 2026)** completa 7.7 del documento del curso: reglas que
> faltaban, panel personal, avisos por la bandeja y por correo, y el canal de
> cada subasta. Resumen en
> [B8 — lo que anade](#b8--lo-que-anade-77-del-documento-del-curso); lo que
> queda pendiente de una decision del PO, en
> [Decisiones del PO pendientes](#decisiones-del-po-pendientes-b8); lo que 7.7
> pide y no esta, en [Brechas declaradas](#lo-que-falta-de-77-brechas-declaradas);
> y como desplegarlo sin tumbar el host, en
> [Despliegue en DEV: plan](#despliegue-en-dev-plan-b8).

## HU-SUB-004 — que hace hoy

La historia funciona de extremo a extremo: desde la pantalla del navegador hasta PostgreSQL, con el JWT real de ms-identidad.

### API

Implementa `contracts/openapi/ms-subastas-pujas.yaml` (0.4.0 desde B8). Todo cuelga de `/api/v1` (`server.servlet.context-path`).

| Metodo | Ruta | Exito |
|---|---|---|
| POST | `/subastas/{id}/pujas` | 201 |
| POST | `/subastas/{id}/compra-inmediata` | 201 |
| PUT | `/subastas/{id}/puja-automatica` | 200 |
| DELETE | `/subastas/{id}/puja-automatica` | 204 |
| GET | `/subastas/{id}/pujas` | 200 |
| GET | `/subastas/{id}/mi-participacion` | 200 |
| GET | `/mis-pujas/resumen` | 200 |

**Pujar y comprar son idempotentes de verdad.** La cabecera `Idempotency-Key`
se guarda junto a la puja, con un unico parcial en la base de datos: si la
respuesta se pierde por red y el cliente reintenta, recibe **la puja original**
en vez de una segunda puja o un rechazo. Reutilizar la clave en otra subasta o
para otro jugador se rechaza con `motivo: CLAVE_REUTILIZADA`, porque entonces
la clave ya no identifica una sola operacion.

**El jugador sale siempre del token**, nunca del cuerpo ni de un parametro: estas operaciones mueven creditos. Se resuelve por el puerto `IdentidadClient` de HU-SUB-001, que implementa `seguridad/IdentidadDesdeToken` leyendo el claim `uid`.

**Los errores salen en problem+json con un campo `motivo` estable**, para que la interfaz elija el mensaje sin depender del texto libre. 409 cuando la peticion era valida y otro se adelanto (releer y reintentar tiene sentido); 422 cuando reintentar volveria a fallar igual.

### Reglas y motores

- `pujas/service/MotorPujasService` — las reglas de pujar (la primera puja llega al precio minimo y desde la segunda supera oferta + incremento minimo, intervalo de 5 s **dentro de la misma subasta**, prohibido pujar en la propia, tope de 50 pujas activas) y la compra inmediata, que deja de estar disponible cuando una puja alcanza su precio. Desde B8 el tope de 10 subastas activas es de **publicacion** (7.7.10), no de participacion.
- `pujas/service/MotorPujaAutomaticaService` — configurar (validando saldo y que el limite sea alcanzable), elegir cual de varias responde, cuanto ofrecer y desde cuando.
- `pujas/service/PujaApplicationService` — abre la transaccion, toma el lock pesimista de la subasta y delega las reglas en el motor.
- `pujas/service/EmisionDePujasAutomaticasJob` — emite las automaticas cada 2 s (`app.pujas.emision-automatica-intervalo-ms`). Sondeo y no evento: el intervalo minimo de 5 s ya impide responder al instante, asi que sondear llega igual de rapido y no depende del catalogo de eventos, que sigue vacio.
- `pujas/service/CierreDeSubastasVencidasJob` — cierra las vencidas cada 30 s y restituye los creditos del postor sin adjudicacion.
- `notificaciones/` — outbox transaccional **y su drenador**: los avisos se escriben en la misma transaccion que cierra la subasta, y `DrenadorDeNotificacionesJob` los entrega a `POST /internal/notifications` del modulo de notificaciones. Cada fila se marca por separado; un 409 ("ya existe ese aviso") cuenta como entregado.
- `config/ConfiguracionCors` — sin esto el navegador bloquea hasta el listado publico y la pantalla no carga nada. Lista explicita de origenes, no comodin: por aqui pasan operaciones que mueven creditos.
- Al registrar una puja se publica `SubastaActualizadaEvent`, que alimenta el contador en vivo del listado (HU-SUB-011). Se pudo conectar cuando el `@TransactionalEventListener(AFTER_COMMIT)` de Cristian llego a develop: antes, publicarlo habria metido el envio STOMP dentro del lock pesimista de la subasta.

### Frontend

`frontend/app-web/src/cuentas/pujas.html` + `pujas.js` (vista) + `pujas-api.js` (cliente HTTP).

La pantalla escucha el canal en vivo `/topic/subastas/listado` que publica
`SubastaRealtimePublisher` (HU-SUB-011), asi que se entera al instante de una
puja ajena o del cierre por vencimiento. **Es un anadido al sondeo de 5 s, no un
sustituto**: si el WebSocket no levanta, la pantalla degrada a consulta
periodica, que es lo que exige el riesgo #7 del acta. Y de un mensaje solo se
usa el aviso de que algo cambio: se relee del servidor en vez de pintar lo que
llega, porque ese mensaje no sabe si la puja es tuya ni cuanto llevas retenido.
Se llaman `pujas.*` y no `subastas.*` porque el listado de HU-SUB-011 (Cristian)
ya ocupa ese nombre; se entra desde ahi con `pujas.html?id=<subasta>`.

**No hay modo demo.** La pantalla habla con el servicio de verdad o no muestra
nada: un numero inventado en una pantalla que mueve creditos es peor que una
pantalla vacia. Lo que el backend todavia no da —el saldo del jugador— aparece
como desconocido, no como cero.

Si la peticion de una puja no llega a tener respuesta, el cliente la reintenta
**una vez y con la misma clave de idempotencia**, apoyandose en la reproduccion
del servidor. Un error HTTP no se reintenta: el servidor ya contesto.

## Contratos

- `contracts/openapi/ms-subastas-pujas.yaml` — el de esta HU, publicado antes de los controladores (regla 1 de plataforma).
- Desde B8, tambien `ms-subastas-listado.yaml` 1.1.0, `ms-subastas-publicar.yaml`
  1.0.0, `ms-subastas-panel.yaml` 1.0.0 y `contracts/websocket/subastas.yaml` 1.1.0.
- `contracts/openapi/notificaciones.yaml` — consumido por el drenador del outbox
  (pacto `contracts/pactos/ms-subastas-notificaciones.json`).
- `contracts/openapi/correo.yaml` (`POST /correos/subasta`) y
  `ms-identidad-admin.yaml` (`GET /internal/usuarios/{uid}/contacto`, pendiente
  en B2) — los avisos por correo de B8.
- `contracts/openapi/creditos.yaml` — el de `ms-finanzas`, ya publicado;
  `CreditoClientHttp` y `FinanzasPublicacionClientHttp` lo consumen y el pacto
  `ms-subastas-ms-finanzas.json` lo fija.

## Levantar en local

```bash
docker compose up -d          # PostgreSQL 17 en el puerto 5435
cd ../../..                   # la raiz del monorepo
DB_PASSWORD=subastas_password PARAMETROS_URL=http://localhost:8088/api/v1 \
  ./gradlew :services:cuentas:ms-subastas:bootRun
```

Desde B8 `SUBASTAS_INCREMENTO_MINIMO` no se lee: el incremento minimo es el
parametro `subastas.incremento-minimo` de admin-parametros, que nace sin valor
(decision del PO). Sin admin-parametros, o sin valor en el, **publicar responde
503 `INCREMENTO_MINIMO_NO_CONFIGURADO`** y la pantalla de publicar lo dice; pujar
en subastas ya publicadas sigue funcionando (cada una guarda su incremento).

Y el frontend, en otra terminal:

```bash
cd frontend/app-web && npm install && npm run dev   # http://localhost:8080
```

Arranca con el doble en memoria de creditos (`app.finanzas.modo=fake`) y lo advierte en el log: **ningun credito se mueve de verdad**. Cada jugador aparece con 5000 creditos ficticios (`app.finanzas.saldo-inicial-doble`), porque sin ms-finanzas no hay ningun sitio desde donde acreditar y con saldo cero ninguna puja pasaria.

## Transferencia y entrega de productos (HU-SUB-001 / HU-SUB-004)

Resuelto el dolor prioritario #1: la operacion `transferirProducto` quedo definida en el puerto `InventarioClient` e implementada con:
- `InventarioClientHttp`: cliente REST hacia `ms-inventario` (`POST /api/v1/inventario/elementos/{id}/transferencias`).
- `InventarioClientFake`: doble configurable en memoria (`app.inventario.modo=fake`) que simula la custodia del producto, actualiza el nuevo propietario y soporta simulacion de fallos.
- Integracion en el flujo de compra inmediata (`MotorPujasService.comprarAhora`): transfiere el producto al comprador; si falla, se libera la reserva de creditos y no se consumen fondos.
- Integracion en el flujo de adjudicacion por vencimiento (`CierreDeSubastasVencidasJob` / `MotorPujasService.cerrarPorVencimiento`): transfiere el producto al mejor postor cuando hay ofertas ganadoras; y libera la reserva si la subasta cierra sin adjudicacion.

## Pendiente

Por orden de lo que mas duele. Revisado contra el codigo el 20/09/2026 y
actualizado en B8 (septiembre de 2026).

- ~~El producto no cambia de dueno al comprarlo.~~ Resuelto (FI-TRANSFER-1):
  inventario expone `POST /elementos/{elementoId}/transferencias`, el
  despliegue y el banco E2E van con `INVENTARIO_MODO=http`, y
  `subastas.e2e.spec.js` lo demuestra con un objeto real que cambia de dueno.
  En local el valor por omision sigue siendo `fake`.

- ~~`app.finanzas.modo` sigue en `fake`.~~ En el despliegue y en el banco E2E
  va en `http` desde R16.19 (creditos reales); `fake` queda solo como omision
  para levantarlo en local sin ms-finanzas.

- **No esta desplegado en DEV**, y es por capacidad medida, no por codigo: el
  host de plataforma no admite una decimotercera JVM (CAPACIDAD.md). El plan
  para llevarlo al host de contenido esta abajo, en
  [Despliegue en DEV: plan](#despliegue-en-dev-plan-b8).

- ~~Los pactos estan escritos pero nadie los verifica del lado proveedor.~~
  Resuelto: los tres (`ms-finanzas`, `ms-inventario` y, desde B8,
  `notificaciones`) tienen su verificacion de proveedor y
  `tests/contratos/pactos-verificados.py` lo vigila.

- **`esMaestroDeJuego` devuelve siempre `false`, y es una decision acordada, no
  un olvido.** Ese rol no existe formalmente en ms-identidad y no se inventa
  desde subastas. `false` es el valor seguro porque el Maestro de Juego esta
  exento de la comision de publicacion, asi que **hasta nuevo aviso todos pagan
  comision**. Lo define **HU-SUB-010 del Sprint 3**, y ms-identidad sera la
  fuente de verdad. Acordado con Edwin el 14/09/2026.

- ~~Los 4 limites de participacion son "configurables desde administracion"
  solo por variable de entorno.~~ Resuelto en B8: el incremento, el tope de
  publicaciones, el de pujas y el intervalo se leen de admin-parametros
  (`LectorDeParametros`, cache de 30 s); las variables de entorno quedan de
  respaldo para los tres topes, **no** para el incremento.

- **Falta la parte de la Definition of Done que no es codigo**: desplegado por
  el flujo automatizado, demostrado en la Sprint Review y aceptado por el
  Product Owner.

### Resuelto desde la ultima revision

Lo que este apartado listaba como pendiente el 17/09 y ya no lo esta: el saldo
del jugador en pantalla, las pruebas de contrato del lado consumidor, el
identificador de la frontera con inventario (`propietarioUid`), la consulta de
un elemento por id, y los tres defectos de ms-finanzas.

## Asunciones tomadas (a validar con el cliente)

El backlog solo deja una pregunta abierta (el valor por defecto del incremento minimo, RF-SUB-004), pero al implementar aparecieron tres mas. Se resolvieron con el criterio mas literal y quedan marcadas en el codigo:

1. **Guerra entre pujas automaticas:** se resuelve de forma *iterativa* (cada una ofrece oferta vigente + incremento, por turnos, respetando los 5 s). La alternativa estilo eBay converge en un paso, pero el criterio habla de "emitir ofertas respetando el intervalo minimo". Con 5 s de intervalo, el modo iterativo puede tardar minutos.
2. **Saldo de la puja automatica:** se valida al *configurar*, no al emitir, para avisar al jugador en el momento en vez de que falle en silencio despues.
3. **Anti-sniping:** no implementado, porque la HU no lo menciona. Sin extension de tiempo, una puja manual en el ultimo segundo es inalcanzable para el motor automatico, que debe esperar su intervalo.
4. **Revocacion de rol:** la cadena de seguridad (servidor de recursos contra el JWKS de ms-identidad, ADR-002) comprueba firma y expiracion, no si el rol sigue vigente. Comparar la version del claim `ver` exigiria leer la tabla de usuarios de otro dominio (ArchUnit lo prohibe) o llamar a ms-identidad dentro del lock pesimista. Consecuencia aceptada: un token revocado sirve aqui hasta que expire solo.

## Ver la historia funcionando con creditos reales

`./gradlew ... check` demuestra que el codigo hace lo que dice. Esto demuestra
otra cosa: que la integracion con ms-finanzas funciona de verdad.

```bash
docker compose -f services/cuentas/ms-finanzas/docker-compose.yml up -d
docker compose -f services/cuentas/ms-subastas/docker-compose.yml up -d

DB_PASSWORD=finanzas_password ./gradlew :services:cuentas:ms-finanzas:bootRun
# en otra terminal, OJO con FINANZAS_MODO=http
DB_PASSWORD=subastas_password FINANZAS_MODO=http \
  ./gradlew :services:cuentas:ms-subastas:bootRun

# y con los dos arriba
services/cuentas/ms-subastas/scripts/demo-local.sh
```

Siembra una subasta, acredita creditos y comprueba seis cosas por HTTP: que
pujar sin saldo se rechaza con `SALDO_INSUFICIENTE` y no con un 500, que con
saldo entra, que los creditos quedan retenidos de verdad, que un reintento con
la misma clave devuelve **la misma** puja sin retener dos veces, que al superado
se le devuelven sus creditos, y que el saldo que ve la pantalla es el real.

Es repetible: cada ejecucion usa jugadores y claves nuevos, asi que no depende
de que la base de datos este limpia.

**La subasta se siembra con SQL a proposito.** Publicarla por la API es
HU-SUB-001 y arrastra catalogo, inventario y finanzas en modo http a la vez;
para ver pujar no hacen falta.

## Correr las pruebas

Desde la raiz del monorepo, porque este servicio es un modulo del build raiz
(no trae su propio wrapper: lo prohibe `guardia-monorepo.yml`):

```bash
./gradlew :services:cuentas:ms-subastas:test    # solo pruebas (necesita Docker)
./gradlew :services:cuentas:ms-subastas:check   # pruebas + compuerta del 80 %
./gradlew :services:cuentas:ms-subastas:test --tests '*MotorPujasServiceTest'
```

Y las del frontend:

```bash
cd frontend/app-web && npm test
```

`PujasApiIT` es la que ejerce la cadena entera (JWT real contra PostgreSQL real): es la unica que habria detectado que el puerto `IdentidadClient` no tenia implementacion, cosa que ninguna prueba con dobles puede ver.

La integracion continua la cubre `ci.yml`, que detecta los servicios modificados y los compila con su herramienta. Este servicio no lleva workflow propio.

> **Aviso si trabajas en macOS con la carpeta en iCloud:** iCloud crea copias de conflicto (`Clase 2.class`) dentro de `build/` mientras Gradle compila, y el build falla con "wrong name". Se limpia con
> `find . -path "*/build/*" -name "* [0-9].*" -delete`.


## B8 — lo que anade (7.7 del documento del curso)

Contratos: `ms-subastas-listado.yaml` 1.1.0, `ms-subastas-publicar.yaml` 1.0.0,
`ms-subastas-pujas.yaml` 0.4.0, `ms-subastas-panel.yaml` 1.0.0 (nuevo) y
`contracts/websocket/subastas.yaml` 1.1.0. Migraciones V7 a V10, solo aditivas.

| Metodo | Ruta | Que es (seccion del documento) |
|---|---|---|
| GET | `/subastas/reglas` | Reglas vigentes: duraciones y comisiones (Tabla 25), incremento de admin-parametros, topes, cancelacion, recordatorio, plazo de recogida. Publica |
| GET | `/subastas/{id}` | Ficha en cualquier estado (7.7.9), con puja minima, compra inmediata disponible, reputacion del vendedor y visualizaciones unicas. Publica |
| POST | `/subastas/{id}/cancelacion` | 7.7.10: solo sin pujas, 50 % de la comision, no en las ultimas 6 h |
| PUT/DELETE | `/subastas/{id}/seguimiento` | Lista de seguimiento (7.7.9) |
| GET | `/mis-subastas/publicadas` | Mis subastas, con si se pueden cancelar y cuanto costaria |
| GET | `/mis-subastas/seguimiento` | Lista de seguimiento |
| GET | `/mis-subastas/pendientes` | Productos pendientes de recoger, 7 dias (7.7.9) |
| POST | `/mis-subastas/pendientes/{id}/recogida` y `/pendientes/recogida` | Recoger uno / «recoger todo» |
| GET | `/mis-subastas/historial[?formato=csv]` | Historial de transacciones con balance y exportacion (7.7.9) |
| GET | `/mis-pujas` | Mis pujas, tambien las cerradas (7.7.9) |

Reglas nuevas en el dominio:

- **Publicar:** compra inmediata **superior** al precio minimo; como mucho 10
  subastas activas por vendedor, contadas con un candado por vendedor
  (`pg_advisory_xact_lock`) para que dos publicaciones simultaneas no pasen con
  9; idempotencia de `Idempotency-Key` en la base de datos
  (`publicaciones_idempotentes`), que sobrevive a reinicios y vale entre replicas.
- **Pujar:** la primera puja es el precio minimo; la compra inmediata se rechaza
  (`COMPRA_INMEDIATA_SUPERADA`, 409) cuando una puja alcanzo su precio.
- **Cerrar por vencimiento con ganador:** cobra y transfiere, y el producto queda
  **pendiente de recoger 7 dias** bajo el bloqueo de la subasta
  (`pendientes_de_recoger`); al vencer se aplica la politica del parametro.
- **Cancelar:** con el candado de la subasta (una cancelacion y una puja
  simultaneas: gana una sola); cobra la penalizacion en ms-finanzas
  (`refId sub-cancelacion-{id}`, idempotente) y libera el producto; si algo
  falla despues, se compensan los dos.
- **Recordatorio de 1 hora** a quien pujo o sigue la subasta, una sola vez.

Avisos (7.7.8): `notificaciones/AvisosDeSubasta` decide quien se entera de que;
el outbox es idempotente por evento (id UUID v3 de la clave del hecho,
`INSERT ... ON CONFLICT DO NOTHING`) y el drenador aparta los rechazos
definitivos en vez de bloquear la cola. Nueva puja al vendedor, puja superada,
victoria, venta, cierre sin ofertas, cancelacion, recordatorio y vencimiento del
pendiente salen **tambien por correo** (`POST /correos/subasta`) cuando
`CORREO_URL` esta configurada: el correo y el apodo se piden a ms-identidad en el
momento (`GET /internal/usuarios/{uid}/contacto`) y no se guardan; la
`Idempotency-Key` es el id del aviso. Reintentos con espera exponencial hasta
FALLIDO. **Esa ruta de ms-identidad figura como pendiente (B2)**: sin ella el
correo no sale y termina FALLIDO (no se descarta en silencio).

Tiempo real: ademas de `/topic/subastas/listado` (publico), cada cambio se
publica en `/topic/subastas/{subastaId}`, que exige sesion en el CONNECT. Un
destino que el AsyncAPI no declara se rechaza, tambien con sesion. El borde
publica el endpoint en `/api/v1/ws-subastas` (hasta B8 no tenia `location`).
`CanalDeSubastaIT` lo prueba con un WebSocket de verdad: CONNECT con y sin
token (y con token roto, caducado o de otro emisor), SUBSCRIBE, una puja real
que llega por el canal, una rechazada que no, SEND rechazado y reconexion.

Pantalla (`pujas.js`, `publicar-subasta.js`): las reglas salen de
`GET /subastas/reglas` en vez de `CONFIG_REGLAS` (el incremento de 50 escrito a
mano ya no existe; sin valor en admin-parametros se dice «DECISION PO
pendiente» y publicar no deja enviar), la puja minima de la ficha, la compra
inmediata desactivada y explicada cuando una puja la alcanzo, seguir y dejar
de seguir, cancelar solo al vendedor y con confirmacion de la penalizacion
(desactivado con el motivo si hay pujas o faltan menos de 6 h), y los
pendientes de recoger en «Mis subastas».

Pactos: `NotificacionesPactoTest` (consumidor) y
`notificaciones/.../VerificacionDelPactoDeSubastasTest` (proveedor) fijan la
credencial de servicio, el cuerpo del aviso, el 409 del duplicado y el 401 sin
credencial.

## Decisiones del PO pendientes (B8)

Cada una tiene su mecanismo configurable; los valores son **provisionales**.

| Decision | Mecanismo | Valor provisional |
|---|---|---|
| Incremento minimo entre pujas (RF-SUB-002) | `subastas.incremento-minimo` en admin-parametros | **ninguno**: sin valor no se publica (503 con motivo) |
| Que pasa con un producto no recogido en 7 dias (7.7.9 fija el plazo, no la consecuencia) | `subastas.pendientes.al-vencer` (respaldo `SUBASTAS_PENDIENTES_AL_VENCER`) | `ENTREGAR`: queda disponible para el ganador, que ya pago |
| «Calificacion del vendedor» (7.7.9) en estrellas o en otra escala | la ficha publica la tasa de exito de 7.7.12 y los recuentos | sin estrellas: no se inventa la escala |
| Maestro de Juego (7.7.4) | `IdentidadClient.Identidad.esMaestroDeJuego` | `false`: ms-identidad no tiene ese rol (HU-SUB-010) |
| Preferencia «avisos solo en la aplicacion» | `debeEnviarCorreo` del contrato de correo | siempre `true`: no hay preferencia guardada |

`subastas.pendientes.al-vencer` **no esta todavia en el catalogo de
admin-parametros**: añadirla es una migracion de ese servicio y se dejo fuera
de B8 para no chocar con las migraciones de otras ramas. Hasta entonces manda
la variable de entorno.

## Lo que falta de 7.7 (brechas declaradas)

No se implementaron en B8 y no se fingen:

- **Dinero real y Maestro de Juego (7.7.3, 7.7.4)**: no hay forma de
  reconocer al Maestro de Juego sin inventar un rol (ver arriba), ni pasarela de
  pagos en subastas. Por lo mismo no sale el aviso «nuevos productos del
  Maestro de Juego» (7.7.8).
- **Ficha (7.7.9)**: la seccion de comentarios y valoraciones del producto,
  «Compartir» y «Reportar subasta sospechosa». La ficha se abre desde el
  listado, pero los comentarios viven en `comentarios` y no se enlazaron.
- **Integridad y fraude (7.7.10, 7.7.11)**: deteccion de pujas coordinadas o de
  cuentas multiples, bloqueo automatico, cancelacion automatica por violacion de
  terminos, doble factor para compras de alto valor (no hay 2FA en
  ms-identidad), tickets, mediacion y reversion de transacciones.
- **Metricas para administradores (7.7.12)**: los datos estan (estados,
  `cerrada_en`, comisiones, penalizaciones, vistas) pero no hay informe.
- **Rendimiento (7.7.13)**: sin cache de listados ni cola de mensajes (no hay
  broker en la plataforma, decision del equipo).

## Despliegue en DEV: plan (B8)

**28-sep — ejecutado en la topologia, fase 1.** El catalogo lo declara con
`claseHost: "contenido"` y `desplegableDev: true`; `docker-compose.ms-subastas.yml`
trae los valores por omision de ese host (inventario y catalogo por nombre en
su red; identidad, finanzas por el 8093, notificaciones, sanciones y parametros
por la IP elastica de plataforma); el job `desplegar-contenido-dev` de `cd.yml`
copia su compose, le pasa la base, los origenes y la credencial, y corre la
compuerta de capacidad. La credencial ya no se copia a mano (paso 4 de abajo):
es el secret `SECRETO_SERVICIO_MS_SUBASTAS` del entorno `dev`, que los dos jobs
reciben y `desplegar.sh` prefiere al valor generado en cada host.

Queda **una** cosa, y no es nuestra: que el grupo de seguridad de contenido
(cuenta del Grupo 2) admita el 8092 desde `35.168.124.119/32`
(`infrastructure/entornos/contenido/reglas-entrada.json`). Con eso, un PR
pequeno cambia en `borde-dev.conf` `srv-ms-subastas:8092` por
`34.193.90.11:8092` en las dos `location` (REST y `ws-subastas`), con su
sustitucion en los dos bancos y su comprobacion de fichero en
`comprobar-rutas.sh`. Antes de eso el borde sigue apuntando al nombre, que da
502 al instante; apuntar ya a la IP con el puerto cerrado daria 504 a los 5 s,
y el smoke de dev solo admite 200, 502 o 503.

El plan original, con sus comprobaciones, sigue abajo como se escribio en B8
(27-sep): los pasos 1-3 y 5-8 aplican tal cual; el 4 lo sustituye el secret
compartido.

*(B8)* `desplegableDev` seguia en `false` y B8 no lo cambiaba. Lo que sigue es
el plan para desplegarlo sin tumbar nada, con lo que hay que comprobar en cada
paso. No lo activa nadie por accidente: hace falta cambiar el catalogo, el CD,
el borde y dos grupos de seguridad, y cada cambio esta escrito abajo.

**No** usar el perfil `subastas` del despliegue a demanda sobre el host de
plataforma: una decimotercera JVM es exactamente lo que colgo el host el 24-sep
(CAPACIDAD.md).

### Por que el host de contenido

| | Plataforma (`nexus-plataforma-dev`, 35.168.124.119) | Contenido (`nexus-contenido-dev`, 34.193.90.11) |
|---|---|---|
| RAM disponible | 20 MiB y swap 2047/2047 con 12 JVM (24-sep 03:50) | 633 MiB y swap 150/2047 (23-sep); ≈ 750 MiB segun la medicion mas reciente citada en el encargo de B8, **no escrita en CAPACIDAD.md**: se vuelve a medir (paso 1) |
| Lo que pide ms-subastas | — | JVM 240-300 MiB en reposo con los flags actuales (`-Xmx128m`, sin C2) + Postgres ≈ 47 MiB ⇒ **≈ 300-350 MiB** (limites: 256m + 128m) |

Cabe solo. **Con misiones (B9) tambien en ese host no cabe con holgura**
(misiones pide 320 MiB de limite): se despliega uno, se mide, y el segundo
solo si la RAM disponible despues del primero sigue por encima de ~450 MiB.

### Red: que llama ms-subastas y por donde

Desde el host de contenido (cuenta y VPC del grupo 2, sin camino privado con
plataforma; todo cruza por IP publica y en HTTP, igual que hoy
salas-partidas → inventario):

| Destino | URL desde contenido | Estado hoy | Variable |
|---|---|---|---|
| inventario, catalogo | `http://srv-inventario:8080`, `http://srv-productos:8080` | misma red de Compose: dos llamadas cruzadas menos (publicar, cerrar, cancelar, recoger) | `INVENTARIO_BASE_URL`, `CATALOGO_BASE_URL` |
| ms-identidad (token, JWKS, contacto) | `http://35.168.124.119:8089` | 8089 abierto (los cuatro de contenido ya validan tokens asi) | `SUBASTAS_DIRECTORIO_ACTIVO_URL` (preparada en B8), `IDENTIDAD_JWKS_URL` (via `vars.IDENTIDAD_JWKS_URL_CONTENIDO`), `IDENTIDAD_URL_INTERNA` |
| ms-finanzas (reservar, liberar, consumir, debitar, reversar) | `http://35.168.124.119/api/v1` (**por el borde**, que ya enruta `^/api/v1/(creditos\|transacciones)`) | el 8093 NO esta en el grupo de seguridad; por el borde no hace falta abrirlo y no expone nada que el borde no exponga ya | `FINANZAS_BASE_URL` |
| notificaciones | `http://35.168.124.119:8085/api/v1` | publicado por `docker-compose.yml`; 8081-8088 abiertos a `cidr_servicios` | `NOTIFICACIONES_BASE_URL` |
| moderacion-sanciones | `http://35.168.124.119:8086` | idem | `SANCIONES_BASE_URL` |
| admin-parametros | `http://35.168.124.119:8088/api/v1` | idem (`GET /parametros/{clave}/valor` es publica) | `PARAMETROS_URL` |
| correo | `http://35.168.124.119:8082` | idem; **se deja vacio** hasta que ms-identidad sirva el contacto (B2) | `SUBASTAS_CORREO_URL` |
| el borde → ms-subastas | `34.193.90.11:8092` (REST y `/api/v1/ws-subastas`) | el grupo de seguridad de contenido solo abre 8101-8104 al 35.168.124.119/32 | regla nueva |

`cidr_servicios` de plataforma vale `0.0.0.0/0` por omision en
`infrastructure/entornos/plataforma/variables.tf`; el valor aplicado de verdad
no esta en el repo y B9 dejo escrito que correo no se alcanza. Por eso el paso
2 lo comprueba antes de nada.

### Pasos, en orden, con su comprobacion

1. **Medir antes.** `gh workflow run diagnostico-dev.yml -f ambiente=contenido`.
   Seguir solo si la RAM disponible es ≥ 450 MiB y no hay reinicios ni
   `OOMKilled` en los cuatro de contenido.
2. **Comprobar el camino de red** desde el host de contenido (SSH del CD o el
   mismo diagnostico): `curl -s -o /dev/null -w '%{http_code}\n'` contra
   `http://35.168.124.119:8089/api/v1/auth/jwks` (200),
   `http://35.168.124.119:8085/actuator/health`, `:8086/actuator/health`,
   `:8088/actuator/health` (200) y `http://35.168.124.119/api/v1/subastas`
   (hoy 502: prueba que el borde contesta). Cualquier `000` es un puerto
   cerrado: abrirlo en el grupo de seguridad de plataforma **solo para
   34.193.90.11/32** (`infrastructure/entornos/plataforma/main.tf`, aplicado por
   `infra-dev.yml`).
3. **Grupo de seguridad de contenido**: regla nueva en
   `infrastructure/entornos/contenido/main.tf`, 8092/tcp solo desde
   35.168.124.119/32. La aplica el grupo 2 con sus credenciales (no hay rol OIDC,
   `AWS_ROLE_ARN_CONTENIDO` sin configurar). Comprobacion: desde plataforma,
   `curl -s -o /dev/null -w '%{http_code}' http://34.193.90.11:8092/api/v1/actuator/health`
   deja de ser `000` en cuanto el servicio corra.
4. **Credencial de servicio compartida.** El emisor esta en plataforma y ya
   registra `ms-subastas` (`CLIENTES_DE_SERVICIO` de `desplegar.sh`). Copiar UNA
   vez la linea `SECRETO_SERVICIO_MS_SUBASTAS=...` de
   `/opt/nexus/secretos-servicios.env` de plataforma al mismo archivo de
   contenido (`desplegar.sh` reutiliza lo que ya esta y no la regenera). Nunca
   por git, un issue ni la bitacora. Comprobacion, desde contenido, sin imprimir
   el secreto: `POST http://35.168.124.119:8089/api/v1/auth/token` con
   `grant_type=client_credentials` y esa credencial → 200. Rotarla es hacerlo en
   los dos hosts.
5. **Un solo PR de activacion**, revisado:
   - `servicios.json`: `ms-subastas` con `"claseHost": "contenido"`,
     `"desplegableDev": true` y sin `motivoFueraDeDev` (puertos 8092/8092 y
     `docker-compose.ms-subastas.yml` no cambian).
   - `cd.yml`, job `desplegar-contenido-dev`: anadir
     `docker-compose.ms-subastas.yml` al `source` del `scp`, y al `env`/`envs` del
     despliegue `MS_SUBASTAS_DB_HOST|PORT|NAME` (vars), `MS_SUBASTAS_DB_USER|PASSWORD`
     (secrets), `SUBASTAS_WS_ORIGENES`, `SUBASTAS_CORS_ORIGENES` y las URL de la
     tabla de arriba como variables del entorno `dev` (por ejemplo
     `vars.SUBASTAS_FINANZAS_URL_CONTENIDO` → `FINANZAS_BASE_URL`). Compose las
     interpola desde el shell, asi que no hace falta tocar `desplegar.sh`.
   - `borde-dev.conf`: `srv-ms-subastas:8092` → `34.193.90.11:8092` en las dos
     `location` (REST y `ws-subastas`), como `/api/v1/productos`; en
     `pruebas/docker-compose.yml`, que el `borde-conf` lo devuelva a
     `srv-ms-subastas:8092` para seguir probandolo con trafico; en
     `comprobar-rutas.sh`, la comprobacion de fichero «subastas va al host de
     contenido».
   - Nada en el frontend ni en el banco E2E: van por el borde.
6. **Desplegar** con el CD a demanda del servicio y **medir despues**
   (`diagnostico-dev.yml -f ambiente=contenido`): RAM disponible por encima de
   ~150 MiB, swap estable, `RestartCount` 0.
7. **Comprobar que sirve**, sin crear cuentas en DEV:
   - `curl -fsS http://35.168.124.119/api/v1/subastas/reglas` → 200 con la
     Tabla 25; `.../api/v1/subastas?size=1` → 200.
   - Handshake por el borde: `curl -si -H 'Connection: Upgrade' -H 'Upgrade: websocket'
     -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ=='
     http://35.168.124.119/api/v1/ws-subastas` → `101 Switching Protocols`.
   - Avisos: en `subastas-db`, `select count(*) from notificaciones_pendientes
     where enviada_en is null and fallida_en is null` baja a 0 tras la primera
     puja real (credencial y 8085 bien).
   - Latencia de una puja < 500 ms (7.7.13) en el panel de HU-REN-001.
8. **Revertir** si la RAM disponible baja de ~150 MiB, crece el swap o degrada
   algo de contenido: revertir el PR de activacion (el borde vuelve a dar 502
   como hoy) y parar `srv-ms-subastas` y `subastas-db` en contenido. El volumen
   `subastas-db-datos` se conserva en los dos hosts; no se borra ninguno.

### Datos

El Postgres de contenido nace vacio (Flyway V1-V10). En plataforma quedo el
volumen de los pocos minutos que ms-subastas estuvo desplegado el 24-sep
(CAPACIDAD.md: se retiro «conservando su volumen»): no hay nada que migrar, y
tampoco se borra.

### Riesgos

- **Memoria compartida con misiones (B9)** en el mismo host: ver arriba.
- **Trafico entre hosts en claro.** Tokens de servicio y de jugador cruzan por
  IP publica en HTTP, como hoy salas-partidas → inventario. El Charter pide
  cifrado en transito para el tiempo real: el borde de DEV tampoco tiene TLS
  todavia. Es la misma brecha, no una nueva, pero crece.
- **Latencia de las pujas.** Cada puja reserva creditos en ms-finanzas con el
  candado de la subasta tomado; con finanzas en otro host, eso suma un viaje de
  ida y vuelta por internet dentro de la region. Medir (paso 7).
- **Host sin gobierno del CD.** Contenido es de la cuenta del grupo 2: el CD
  no puede encenderlo ni medirlo por AWS, y su compose no tiene arranque
  escalonado. Si el host reinicia, cinco o seis JVM arrancan a la vez.
- **Credencial copiada a mano** en dos hosts: rotarla exige hacerlo en ambos.
- **Correo**: con `SUBASTAS_CORREO_URL` puesta y la ruta de contacto de
  ms-identidad sin desplegar (B2), los correos se reintentan hasta FALLIDO y lo
  dicen en la bitacora; no se pierden avisos de la bandeja por eso.
- **Coste**: sin EC2 nuevo ni servicios gestionados; las reglas de seguridad
  son gratis y el trafico entre zonas de disponibilidad, de pocos KB por puja,
  es la unica partida nueva.
