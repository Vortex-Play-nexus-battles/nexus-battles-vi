# ms-subastas

**Sprint:** 2 · **Modulo:** M12 — Subasta · **Historias:** HU-SUB-001 (Edwin), HU-SUB-011 (Cristian), HU-SUB-004 (Andres)

Mercado de subastas entre jugadores: publicar un producto, listarlos con filtros en vivo, y pujar o comprar de forma inmediata.

## HU-SUB-004 — que hace hoy

La historia funciona de extremo a extremo: desde la pantalla del navegador hasta PostgreSQL, con el JWT real de ms-identidad.

### API

Implementa `contracts/openapi/ms-subastas-pujas.yaml` (0.2.0). Todo cuelga de `/api/v1` (`server.servlet.context-path`).

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

- `pujas/service/MotorPujasService` — las 5 reglas de pujar (supera oferta + incremento minimo, intervalo de 5 s **dentro de la misma subasta**, prohibido pujar en la propia, topes de 10 subastas / 50 pujas) y la compra inmediata.
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
- `contracts/openapi/notificaciones.yaml` — consumido por el drenador del outbox.
- El de creditos es de `ms-finanzas` (Juan Diego). **Sigue sin publicarse en
  `contracts/openapi/`**, pero el servicio ya expone los endpoints (HU-PAG-001),
  asi que `pujas/creditos/CreditoClientHttp` esta escrito contra lo que publica
  hoy `CreditoController`, no contra un contrato acordado. Cuando lo suba, el
  cliente se regenera desde ahi.

## Levantar en local

```bash
docker compose up -d          # PostgreSQL 17 en el puerto 5435
cd ../../..                   # la raiz del monorepo
DB_PASSWORD=subastas_password SUBASTAS_INCREMENTO_MINIMO=50 \
  ./gradlew :services:cuentas:ms-subastas:bootRun
```

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

Por orden de lo que mas duele. Revisado contra el codigo el 20/09/2026.

- **El producto no cambia de dueno al comprarlo. Es el unico bloqueo real que
  queda.** La logica esta escrita y llamada desde los tres sitios (compra
  inmediata, adjudicacion por vencimiento y compensacion al vendedor), pero
  ms-inventario no expone la transferencia de propiedad:
  `POST /elementos/{elementoId}/transferencias` no existe todavia. Por eso
  `app.inventario.modo` sigue en `fake`: con el modo real, la compra inmediata
  cobraria los creditos y no entregaria nada. Pedido a Nicolay; de los tres
  endpoints que hacian falta ya publico dos —el bloqueo con `propietarioUid`
  (#369) y la consulta por id (#368)—, ambos ya integrados aqui.

- **`app.finanzas.modo` sigue en `fake` por defecto, pero ya NO por un defecto
  ajeno.** Los tres problemas que lo impedian estan resueltos y verificados con
  los servicios levantados: el saldo ya no devuelve 500, existe
  `POST /creditos/acreditar`, y los rechazos llegan con su `type` URI
  (422 saldo-insuficiente, 404 reserva-no-encontrada, 409 reserva-ya-liberada).
  La integracion real se probo de punta a punta —ver el apartado de arriba—, asi
  que cambiarlo a `http` es hoy **una decision de equipo, no un pendiente
  tecnico**.

- **Los pactos estan escritos pero nadie los verifica del lado proveedor.**
  `contracts/pactos/` tiene los dos, con los estados que cada proveedor debe
  saber montar documentados en su README. Mientras no los verifiquen, fijan lo
  que esperamos pero no avisan si el proveedor cambia.

- **`esMaestroDeJuego` devuelve siempre `false`, y es una decision acordada, no
  un olvido.** Ese rol no existe formalmente en ms-identidad y no se inventa
  desde subastas. `false` es el valor seguro porque el Maestro de Juego esta
  exento de la comision de publicacion, asi que **hasta nuevo aviso todos pagan
  comision**. Lo define **HU-SUB-010 del Sprint 3**, y ms-identidad sera la
  fuente de verdad. Acordado con Edwin el 14/09/2026.

- **Los 4 limites de participacion son "configurables desde administracion"
  solo por variable de entorno.** Cambiarlos hoy exige reiniciar el servicio.
  Hacerlo de verdad pasa por `admin-parametros`, que es de otro equipo.

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
