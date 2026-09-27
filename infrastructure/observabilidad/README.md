# Observabilidad (M16A)

## Tiempos de espera de las llamadas salientes (B12)

Una llamada HTTP a otro servicio sin tiempo de conexión ni de lectura no falla
nunca: si el destino acepta la conexión y no contesta, el hilo se queda
esperando hasta que el sistema operativo se rinde (del orden de minutos) y el
borde contesta un 504 a los 60 s (`proxy_read_timeout`). Ningún cortacircuitos
ni reintento cuenta ese fallo a tiempo, y con tráfico se acaban los hilos del
servicio que llama: la caída de uno se lleva al otro (HU-DIS-003).

`RestClient.builder()` sin fábrica y `new RestTemplate()` salen **sin** tiempos.
El `RestClient.Builder` que inyecta Spring Boot tampoco los trae si nadie define
`spring.http.clients.connect-timeout`/`read-timeout` (Boot 4.1), y en este repo
nadie lo hace.

### Inventario (B12, 27-sep-2026, sobre `develop` a626bb75)

Clientes de `services/plataforma/**`, `services/cuentas/**` y `shared/libs/**`
que salían **sin** tiempo de conexión ni de lectura:

| Servicio | Dónde | Destino | Estado |
|---|---|---|---|
| salas-partidas | `chat/canal/ConfiguracionDelChat.restClientChat` | moderacion-sanciones: lista negra y sanción activa (cada mensaje, alta de sala e ingreso) | **Arreglado en B12**: usa la fábrica de `ConfiguracionDeResiliencia` (2 s / 10 s), como el resto del servicio. Prueba: `ConfiguracionDelChatTest` |
| admin-parametros | `parametros/ConfiguracionDeParametros.auditoria` | ms-cumplimiento `POST /admin/auditoria/eventos` (dentro del cambio de un parámetro) | **Arreglado en B12**: 2 s / 5 s (`AUDITORIA_TIEMPO_CONEXION_MS`, `AUDITORIA_TIEMPO_RESPUESTA_MS`). Prueba: `ConfiguracionDeParametrosTest` |
| ms-finanzas | `pagos/correo/ConfiguracionClientesHttpPagos.restClientCorreo` | correo `POST /correos/confirmacion-compra` (tras aprobar un pago) | **Arreglado en B12**: 2 s / 5 s (`app.correo.tiempo-*` o `FINANZAS_TIEMPO_CONEXION_MS`/`FINANZAS_TIEMPO_RESPUESTA_MS`). Prueba: `ConfiguracionClientesHttpPagosTest` |
| comentarios | `publicacion/ConfiguracionClientesHttp` | moderacion-sanciones, notificaciones, ms-cumplimiento | Resuelto en la rama de B3 (`feat/backend-05-comunidad-producto`, 2 s / 5 s), pendiente de integrar: no se toca aquí para no chocar |
| moderacion-sanciones | `sanciones/ConfiguracionDeSanciones.emisorDeAvisos` | notificaciones | Resuelto en la rama de B2 (`feat/backend-04-lista-negra-moderacion`, 2000 / 5000 ms), pendiente de integrar |
| moderacion-sanciones | `sanciones/ConfiguracionDeSanciones`, `LectorDeParametros.desde(RestClient.builder().build(), …)` | admin-parametros `GET /parametros/{clave}/valor` | **Pendiente.** Arreglo de una línea (pasarle un cliente con la fábrica con tiempos que la rama de B2 ya crea en ese mismo fichero). Se deja a esa rama: tocar el fichero aquí choca con su reescritura |
| torneos | `integracion/ConfiguracionDeIntegraciones` (lista negra, créditos, sanciones) | moderacion-sanciones, ms-finanzas | Resuelto en la rama de B10/B11 (`feat/backend-12-torneos-chatbot`, 2000 / 5000 ms), pendiente de integrar |
| ms-chatbot | `chat/consultas/ClientesExternosConfig` | inventario, ms-subastas, notificaciones | Resuelto en la misma rama (1000 / 1500 ms), pendiente de integrar |
| **librería** `plataforma-seguridad` | `TokenDeServicioOAuth2`: el proveedor `client_credentials` se construye sin cliente de token propio | endpoint de token de ms-identidad (en plataforma y cuentas lo usan admin-parametros, comentarios, moderacion-sanciones, salas-partidas, torneos, ms-finanzas y ms-subastas) | **Pendiente, no es local**: librería compartida (su revisión es de los tres Scrum Masters) y afecta a siete servicios. Arreglo: `cc.accessTokenResponseClient(...)` con un `RestClient` con fábrica con tiempos, conservando los convertidores de OAuth2 que trae el cliente por omisión. Mientras tanto, la primera llamada de cada servicio y cada renovación del token (30 s antes de caducar) no tienen cota, aunque el cliente que la dispara sí la tenga |

Caso aparte, **el decodificador JWT** de los 13 servicios que validan tokens
contra `/api/v1/auth/jwks`: se construye con el cliente por omisión de Spring
Security (30 s de conexión y 30 s de lectura en 7.1.1, según su código). Es
finito, pero no explícito; si se quiere fijar, un
`JwkSetUriJwtDecoderBuilderCustomizer` con `restOperations(...)`, mejor una sola
vez en `plataforma-seguridad`.

**Ya estaban bien** (tiempos explícitos): salas-partidas (el resto de clientes,
2 s / 10 s, `ConfiguracionDeResiliencia.fabricaDePeticionesConTiempos`),
metricas-plataforma (2 s / 2 s y 2 s / 3 s), ms-ecommerce (2 s / 3 s),
ms-identidad (entre 1 y 3 s de conexión y entre 1 y 5 s de lectura, según el
cliente), ms-subastas (1-5 s, `HttpClient` del JDK). correo, notificaciones y
ms-cumplimiento no llaman a nadie por HTTP (correo usa SMTP, con 10 s).

### El patrón a seguir

- **Un bean `ClientHttpRequestFactory` por servicio** con conexión y lectura
  explícitas, leídas de propiedades con valor por omisión y variable de entorno
  (regla 10). Referencia: `ConfiguracionDeResiliencia` de salas-partidas.
- **Todo `RestClient` sale de un único ayudante** que le pone la fábrica y la
  traza (`constructorConTraza` en `ConfiguracionDelServicio` de salas-partidas);
  la credencial se añade después. Así un cliente nuevo no puede nacer sin
  tiempos ni sin traza.
- **Conexión corta, lectura con margen, las dos muy por debajo de los 60 s del
  borde**: 1-3 s y 2-10 s son los valores del repo. Así quien contesta es el
  servicio, con su problem detail, y no el 504 del borde.
- **Cada arreglo, con su prueba**: un `ServerSocket` que acepta la conexión y no
  contesta nunca, y `assertTimeoutPreemptively`. Sin tiempos, la prueba se
  queda colgada y falla; con ellos, termina en décimas de segundo.

## Trazas a través del borde (regla 5, B12)

El borde (`infrastructure/red-balanceo/borde-dev.conf`) **no filtra** las
cabeceras de traza, y ahora se comprueba con tráfico en cada corrida del banco
(`pruebas/comprobar-rutas.sh`, sección «Trazas»):

- `traceparent` y `X-Trace-Id` de la petición llegan al servicio, en una
  `location` normal (`/api/v1/salas`), en una que reescribe la ruta
  (`/api/v1/vitrina`) y en una de WebSocket (`/ws/notificaciones`), que declara
  sus propios `proxy_set_header`.
- El `traceparent` que pone el servicio en la respuesta (`FiltroDeTraza`) vuelve
  al cliente.

Por qué pasan: nginx reenvía las cabeceras de la petición salvo las que llevan
guion bajo (`underscores_in_headers off`) y las que pisa un `proxy_set_header`,
y devuelve las de la respuesta salvo unas pocas suyas (`Server`, `Date`,
`X-Accel-*`). Ninguna de traza cae ahí. Una petición que muere en el propio
borde (el 404 de «prefijo sin servicio», el 403 de Mailpit, el 429 del límite
de frecuencia) no llega a ningún servicio: la identifica `X-Borde-Peticion`
(el `$request_id` de nginx), que el borde pone en todas las respuestas.
