# ms-chatbot (7.4 — Chatbot de soporte)

**Contrato:** `contracts/openapi/ms-chatbot.yaml` 2.0.0
**Stack:** Java 21 · Spring Boot 4.1 · Gradle · PostgreSQL propio (`chatbot-db`, esquema `chatbot`, Flyway V1–V5) · resource server de ms-identidad · puerto 8094, context-path `/api/v1`
**Historias:** HU-CHA-001 (chat 24/7), HU-CHA-004 (motor de reglas), HU-CHA-008 (consultas asistidas), HU-CHA-011 (calificación y brechas), HU-CHA-012 (panel de administración); endurecimiento B11.

## Qué hace

Chat de soporte para visitantes y jugadores: respuestas de una base de conocimiento
versionada (motor de reglas con tolerancia a errores ortográficos, español/inglés),
consultas en vivo sobre la propia cuenta, historial, calificación útil/no útil, brechas de
conocimiento y panel de analíticas. **No** es un modelo de lenguaje: es un motor de reglas
(interpretación de RF-CHA-014 documentada en `V4__panel_de_administracion.sql`).

## Seguridad (B11)

**Identidad.** El fallo de la auditoría: `X-Id-Sesion-Anonima` se usaba como la misma clave
que el `uid`, así que un visitante que mandara el `uid` de un jugador (son públicos, p. ej.
en `GET /torneos`) leía, calificaba o borraba su historial. Desde 2.0.0:

- Un jugador es el `uid` de su token y nada más (`ResolutorDeIdentidad`); con token válido la
  cabecera se ignora. Un token que no identifica a una persona degrada a visitante.
- Un visitante usa una sesión **que emite el servidor** (`POST /chat/sesiones`, o la cabecera
  de respuesta del primer `POST /chat/mensajes`): `anon_` + 256 bits aleatorios en base64url.
  En la base solo queda su huella SHA-256 (`sesiones_anonimas`, V5). Vence tras
  `CHATBOT_SESION_ANONIMA_HORAS` (24) sin actividad.
- Espacios de claves separados: la conversación de un jugador es su `uid`; la de un visitante,
  `anonimo:<id de sesión>`. Una restricción `CHECK` en la base impide mezclarlos, y V5 apartó
  las conversaciones anónimas anteriores (claves elegidas por el cliente) a `legado-anonimo:`.
- Un identificador que el servidor no emitió no abre nada: historial vacío, nada que borrar,
  404 al calificar. Las pruebas negativas del ataque exacto están en `SeguridadDelHistorialIT`.

**Límite de frecuencia** (7.4.8; ventana de un minuto en memoria de la instancia, 429 con
`Retry-After`). Cifras **provisionales**, convención técnica (el documento no las da):

| Variable | Por omisión | Qué limita |
|---|---|---|
| `CHATBOT_LIMITE_MENSAJES_POR_MINUTO` | 20 | mensajes por jugador o por sesión |
| `CHATBOT_LIMITE_SESIONES_POR_ORIGEN` | 10 | sesiones nuevas por dirección (`X-Real-IP` del borde) |
| `CHATBOT_LIMITE_SESIONES_GLOBAL` | 300 | sesiones nuevas por minuto en la instancia |

**Filtrado** (7.4.8): todo el texto del usuario pasa por la lista negra
(`LISTA_NEGRA_VERIFICAR_URL`, contexto `CHAT_GENERAL`, por tramos de 2000 caracteres
solapados). Bloqueado → 422 y no se guarda nada. Si la lista negra no responde → 503
(fail-closed, D-14); `CHATBOT_MODERACION_SI_NO_RESPONDE=PERMITIR` lo cambia.

**Sin HTTP dentro de una transacción:** la respuesta (consultas en vivo) se genera sin
transacción abierta y pregunta + respuesta se guardan juntas en una corta
(`RegistroDeConversaciones`). **Tiempos de espera** en todo `RestClient`
(`CHATBOT_HTTP_TIMEOUT_CONEXION_MS` 1000, `…_LECTURA_MS` 1500): el documento pide respuestas
en menos de 2 s (7.4.11); lo que no llega a tiempo responde «no disponible» solo para esa parte.

## Consultas en vivo (HU-CHA-008, 7.4.4)

Solo para jugadores con sesión, y **nunca con más privilegios que el jugador**: se reenvía su
propio token; el chatbot no tiene credencial de servicio.

| Consulta | Servicio | Credencial |
|---|---|---|
| Inventario | inventario `GET /api/v1/inventario/elementos` | token del jugador |
| Subastas | ms-subastas `GET /mis-pujas/resumen` | token del jugador |
| Notificaciones | notificaciones `GET /users/{uid}/notifications` | token del jugador |
| Movimientos de créditos (B11) | ms-finanzas `GET /creditos/{uid}/movimientos` | token del jugador |
| Torneos en curso y mi equipo (B11) | torneos `GET /torneos`, `GET /torneos/{id}` | ninguna (dato público) |
| Misiones | — | «en construcción»: el servicio de misiones aún no publica contrato |

## Despliegue

`docker-compose.ms-chatbot.yml` (con su `chatbot-db`, arranque escalonado turno 6) viaja al
host con `cd.yml` como los demás overrides de Cuentas. **`desplegableDev` sigue en `false`**
en `infrastructure/despliegue/servicios.json`: el host de plataforma no tiene memoria para una
13.ª JVM (`infrastructure/despliegue/CAPACIDAD.md`, medición del 24-sep).

**Plan para desplegarlo (decisión del equipo, no de capacidad):** llevarlo al host de
contenido, que tiene RAM libre (≈384 MiB: JVM 256 + base 128):

1. Acordarlo con el Grupo 2 (su cuenta y su grupo de seguridad) y abrir el 8094 solo al borde.
2. En `servicios.json`: `claseHost: "contenido"`, y añadir el override a la lista de copia del
   job `desplegar-contenido-dev` de `cd.yml`.
3. En su compose: inventario pasa a red local; subastas, notificaciones, finanzas, torneos,
   la lista negra y el JWKS de ms-identidad se alcanzan por la IP del host de plataforma
   (mismo patrón que salas-partidas usa hacia contenido).
4. En `borde-dev.conf`: `/api/v1/(chat|chatbot)` apunta a `http://<IP de contenido>:8094`
   (como `/api/v1/productos`).
5. Poner `desplegableDev: true` y comprobar con `scripts/cd/comprobar-catalogo-servicios.sh`.

Alternativas que CAPACIDAD.md deja abiertas: consolidar los Postgres en el host de
plataforma (≈128 MiB, pero la JVM sigue sin caber) o desplegarlo a demanda en perfiles de demo.

## Lo que falta del 7.4 (brechas declaradas)

- **WebSocket** (7.4.9): el chat es REST; no hay canal STOMP del chatbot ni contrato en
  `contracts/websocket/`.
- **Tickets de soporte** (7.4.3): una pregunta no entendida se escala y se registra como
  brecha de conocimiento, pero no se genera un ticket ni hay soporte humano detrás.
- **Caché de respuestas frecuentes** (7.4.9) y cifrado en reposo (7.4.8): no implementados.

## Frontend

`frontend/app-web/src/comun/cliente-chatbot.js` (PR #708) genera hoy su propio identificador
de visitante: con 2.0.0 el chat le sigue funcionando, pero su historial no persiste hasta que
guarde el identificador que devuelve el servidor en la cabecera `X-Id-Sesion-Anonima` (y, en
desarrollo con CORS, lo exponga en `exposedHeaders`).

## Pruebas

`./gradlew :services:cuentas:ms-chatbot:check` — unitarias (`ChatServiceTest`,
`ChatControllerTest`, `SesionesAnonimasTest`, `LimitadorDeFrecuenciaTest`,
`ListaNegraClientHttpTest`, `MotorConsultasAsistidasTest`…) e integración con PostgreSQL real
(`SeguridadDelHistorialIT`: el ataque de la auditoría, límite de frecuencia, mensaje
bloqueado, restricción de espacios de claves). JaCoCo ≥ 80 %.
