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

`frontend/app-web/src/cuentas/subastas.html` + `subastas.js` (vista) + `subastas-api.js` (cliente HTTP).

La pantalla habla con el servicio de verdad. Con `?demo` en la URL se monta con datos de ejemplo y sin servidor, para ensenarla en la sustentacion si el backend no esta levantado.

## Contratos

- `contracts/openapi/ms-subastas-pujas.yaml` — el de esta HU, publicado antes de los controladores (regla 1 de plataforma).
- `contracts/openapi/notificaciones.yaml` — consumido por el drenador del outbox.
- El de reserva de creditos es de `ms-finanzas` (Juan Diego) y **todavia no existe**: se le paso un borrador escrito desde el consumidor.

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

Por orden de lo que mas duele:

- **Ninguna puja mueve creditos de verdad.** `services/cuentas/ms-finanzas/` tiene un unico archivo Java (la clase de arranque), cero endpoints y ningun contrato publicado, asi que no existe un `CreditoClientHttp` al que cambiar. Hay un borrador de contrato en conversacion con Juan Diego, pensado para los tres consumidores a la vez (este servicio, HU-SUB-001 y salas-partidas) para no repetir lo del Sprint 1 con auditoria. Nomenclatura acordada: base `/api/v1/creditos`, operacion `consolidar`, identificador `uid`. Cuando lo suba a `contracts/openapi/`, el cliente se genera desde ahi y `app.finanzas.modo` pasa a `http`.
- **El producto no cambia de dueno al comprarlo.** La logica esta, pero inventario no expone transferencia de propiedad. De los tres endpoints pedidos a Nicolay ya publico dos —el bloqueo y su liberacion (HU-INV-010), ambos ya implementados en `InventarioClientHttp`—; falta la transferencia y un `GET /elementos/{elementoId}` para resolver un elemento por id.
- **El identificador de la frontera con inventario: acordado, pendiente de implementar.** Inventario autentica con `X-User-Name`, que es el apodo; este servicio solo conoce el `uid` del token. Se descarto la transicion de pasar ambos porque **en tres de las cinco llamadas a inventario no existe ningun apodo que propagar**: el cierre por vencimiento y la liberacion los dispara un `@Scheduled` sin peticion ni token, y la compensacion transfiere al vendedor, que no es quien hizo la peticion. El `uid`, en cambio, ya queda persistido al crear la subasta y se reutiliza despues. Acordado con Edwin el 14/09/2026: el contrato nuevo nace con `uid`. `InventarioClient` no se toca hasta cerrarlo con Nicolay.
- **`esMaestroDeJuego` devuelve siempre `false`, y es una decision acordada, no un olvido.** Ese rol no existe formalmente en ms-identidad y no se inventa desde subastas. `false` es el valor seguro porque el Maestro de Juego esta exento de la comision de publicacion, asi que **hasta nuevo aviso todos pagan comision**. Lo define **HU-SUB-010 del Sprint 3**, y ms-identidad sera la fuente de verdad. Acordado con Edwin el 14/09/2026.
- **El saldo del jugador no se muestra de verdad** en la pantalla: sale del valor de ejemplo, porque no hay endpoint que lo dé. Se resuelve con el `GET /api/v1/creditos/{uid}/saldo` del contrato de arriba.
- Pruebas de contrato (Pact) — ninguna todavia.
- Los 4 limites de participacion son "configurables desde administracion" solo por variable de entorno.

## Asunciones tomadas (a validar con el cliente)

El backlog solo deja una pregunta abierta (el valor por defecto del incremento minimo, RF-SUB-004), pero al implementar aparecieron tres mas. Se resolvieron con el criterio mas literal y quedan marcadas en el codigo:

1. **Guerra entre pujas automaticas:** se resuelve de forma *iterativa* (cada una ofrece oferta vigente + incremento, por turnos, respetando los 5 s). La alternativa estilo eBay converge en un paso, pero el criterio habla de "emitir ofertas respetando el intervalo minimo". Con 5 s de intervalo, el modo iterativo puede tardar minutos.
2. **Saldo de la puja automatica:** se valida al *configurar*, no al emitir, para avisar al jugador en el momento en vez de que falle en silencio despues.
3. **Anti-sniping:** no implementado, porque la HU no lo menciona. Sin extension de tiempo, una puja manual en el ultimo segundo es inalcanzable para el motor automatico, que debe esperar su intervalo.
4. **Revocacion de rol:** `ValidadorDeToken` comprueba firma y expiracion, no si el rol sigue vigente. Comparar la version del claim `ver` exigiria leer la tabla de usuarios de otro dominio (ArchUnit lo prohibe) o llamar a ms-identidad dentro del lock pesimista. Consecuencia aceptada: un token revocado sirve aqui hasta que expire solo.

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
