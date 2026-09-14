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

- **`CreditoClientHttp` real.** `services/cuentas/ms-finanzas/` solo tiene un README: el servicio no existe. Cuando exista y publique contrato, el doble se genera desde ahi (la regla pide generarlo, no escribirlo a mano) y `app.finanzas.modo` pasa a `http`.
- **`esMaestroDeJuego` devuelve siempre `false`.** El token no trae ese dato y ms-identidad no tiene ese rol. `false` es el valor seguro porque el Maestro de Juego esta exento de la comision de publicacion. Falta acordar de donde sale.
- **El saldo del jugador no se muestra de verdad** en la pantalla: sale del valor de ejemplo, porque no hay endpoint que lo dé.
- **`SubastaActualizadaEvent` no se publica desde el camino de pujas.** El contador en vivo del listado no se mueve al pujar. Hacerlo hoy meteria el envio STOMP dentro del lock pesimista; el arreglo de Cristian (`AFTER_COMMIT`) esta en `feat/listado-manejo-errores` y todavia no en develop. En cuanto se fusione, es una linea.
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
