# ms-chatbot (7.4 — Chatbot de soporte)

**Contrato:** `contracts/openapi/ms-chatbot.yaml` 1.4.0 (compatible con 1.1.0)
**Stack:** Java 21 · Spring Boot 4.1 · Gradle · PostgreSQL propio (`chatbot-db`, esquema `chatbot`, Flyway V1–V10) · resource server de ms-identidad · puerto 8094, context-path `/api/v1`
**Historias:** HU-CHA-001 (chat 24/7), HU-CHA-004 (motor de reglas), HU-CHA-008 (consultas asistidas), HU-CHA-011 (calificación y brechas), HU-CHA-012 (panel de administración); endurecimiento B11.

## Qué hace

Chat de soporte para visitantes y jugadores: respuestas de una base de conocimiento
versionada (motor de reglas con tolerancia a errores ortográficos, español/inglés),
consultas en vivo sobre la propia cuenta, historial, calificación útil/no útil, brechas de
conocimiento y panel de analíticas. **No** es un modelo de lenguaje: es un motor de reglas
(interpretación de RF-CHA-014 documentada en `V4__panel_de_administracion.sql`).

## Seguridad y privacidad (B11 y revisión final del 7.4.8, 1.4.0)

**Lo que escribe el usuario (1.4.0).** Toda pregunta, comentario de calificación, asunto y
mensaje de ticket pasa primero por `chat.entrada.RevisionDeEntrada`, antes de moderarse,
procesarse, guardarse o salir hacia otro servicio:

1. se quitan los caracteres de control y los invisibles (ancho cero, inversión bidireccional;
   un NUL hacía fallar a PostgreSQL con un 500);
2. `DetectorDeInyeccion` reconoce intentos de inyección de código (etiquetas y manejadores de
   HTML, SQL encadenado o tautologías, `${jndi:…}` y otras plantillas, comandos del sistema,
   operadores de NoSQL, rutas del sistema) y responde **422 `INYECCION_DETECTADA`** sin guardar
   nada; la métrica `chatbot_inyecciones_bloqueadas_total{tipo}` los cuenta, y la bitácora
   anota el tipo, nunca el texto. Busca formas, no palabras: las ~900 variantes, títulos,
   respuestas y casos de la base de conocimiento están en `DetectorDeInyeccionTest` para que
   ninguna pregunta legítima se bloquee;
3. `RedaccionDeDatosSensibles` tapa contraseñas, tarjetas, tokens y claves. **Desde aquí solo
   circula el texto redactado:** lista negra, motores, brechas, analíticas y base. Si algo se
   tapó, la respuesta del bot empieza diciéndolo.

La migración **V10** (Java, `db.migration`) redactó lo que ya estaba guardado: mensajes del
usuario, comentarios, tickets y su contexto, y brechas (las que solo diferían por el secreto se
fundieron sumando sus contadores; las que solo eran un secreto se borraron).

**Cifrado (7.4.8).** En tránsito: TLS 1.2/1.3 en el borde (`https://nexusbattlesvi.com`);
del borde al servicio y del servicio a su base el tráfico no sale del host. En reposo: el
volumen EBS del host de plataforma, donde vive `chatbot-db`, está cifrado con llave de AWS
(`infrastructure/entornos/plataforma/main.tf`, `encrypted = true`). No hay cifrado por columna;
lo sensible no llega a la base (redacción) y las sesiones de visitante solo dejan su huella
SHA-256.

**Qué borra «Borrar conversación»** (`DELETE /chat/historial`): los mensajes y sus
calificaciones. Quedan la fila vacía de la conversación, las preferencias (configuración, no
historial), las brechas (agregado anónimo y redactado), los tickets con su contexto redactado
(constancia de la atención humana) y los respaldos del entorno hasta que caducan. La retención
de las conversaciones de visitantes vencidas no tiene cifra en ningún documento: hoy no se
purgan (pendiente del PO, igual que D-16/D-17).

**Identidad.** El fallo de la auditoría: `X-Id-Sesion-Anonima` se usaba como la misma clave
que el `uid`, así que un visitante que mandara el `uid` de un jugador (son públicos, p. ej.
en `GET /torneos`) leía, calificaba o borraba su historial. Desde 1.2.0:

- Un jugador es el claim `uid` de su token y nada más (`ResolutorDeIdentidad`); con token
  válido la cabecera se ignora. Un token sin `uid` válido degrada a visitante. **Desde 1.4.0 el
  `sub` (el apodo) nunca se toma por uid**, aunque tenga forma de UUID: antes
  `IdentidadDelToken.idDe` lo aceptaba cuando faltaba el `uid`.
- Un visitante usa una sesión de una de dos formas, y en la base solo queda su huella SHA-256
  (`sesiones_anonimas`, V5):
  - **emitida por el servidor** (`POST /chat/sesiones`, o la cabecera de respuesta del primer
    `POST /chat/mensajes`): `anon_` + 256 bits aleatorios en base64url. La recomendada.
  - **declarada por el navegador**: `visitante-` + 10 a 64 caracteres `[A-Za-z0-9-]`, la que ya
    manda el asistente de la interfaz (PR #708, `comun/cliente-chatbot.js`). Se registra la
    primera vez que escribe y cuenta en el límite de sesiones nuevas.

  Las dos vencen tras `CHATBOT_SESION_ANONIMA_HORAS` (24) sin actividad; al volver después,
  el visitante empieza otra conversación.
- Espacios de claves separados: la conversación de un jugador es su `uid`; la de un visitante,
  `anonimo:<id de sesión>`. Una restricción `CHECK` en la base impide mezclarlos, y V5 apartó
  las conversaciones anónimas anteriores (claves elegidas por el cliente) a `legado-anonimo:`.
- Un `uid` a secas, un valor sin ninguna de las dos formas o una sesión vencida no abren nada:
  historial vacío, nada que borrar, 404 al calificar. `visitante-<uid de un jugador>` es solo
  otra conversación de visitante. Las pruebas negativas del ataque exacto están en
  `SeguridadDelHistorialIT`.

**Por qué 1.2.0 y no una versión mayor.** El primer borrador de B11 exigía la sesión emitida
por el servidor (2.0.0) y dejaba sin historial al asistente de #708, que ya estaba en `develop`
y declara su propio identificador; además, la regla 2 pide una ruta nueva para un cambio
incompatible. Aceptar también la forma declarada —siempre en el espacio de visitante— cierra el
mismo fallo sin romper a ningún cliente: `POST /chat/sesiones` queda como opción, no como
requisito.

**Límite de frecuencia** (7.4.8; ventana de un minuto en memoria de la instancia, 429 con
`Retry-After`). Cifras **provisionales**, convención técnica (el documento no las da):

| Variable | Por omisión | Qué limita |
|---|---|---|
| `CHATBOT_LIMITE_MENSAJES_POR_MINUTO` | 20 | mensajes por jugador o por sesión |
| `CHATBOT_LIMITE_MENSAJES_POR_ORIGEN` | 120 | mensajes por dirección, sumando todas sus sesiones (1.4.0) |
| `CHATBOT_LIMITE_SESIONES_POR_ORIGEN` | 10 | sesiones nuevas por dirección (`X-Real-IP` del borde) |
| `CHATBOT_LIMITE_SESIONES_GLOBAL` | 300 | sesiones nuevas por minuto en la instancia |

**Filtrado** (7.4.8): todo el texto del usuario pasa por la lista negra
(`LISTA_NEGRA_VERIFICAR_URL`, contexto `CHAT_GENERAL`, por tramos de 2000 caracteres
solapados). Bloqueado → 422 y no se guarda nada. Si la lista negra no responde → 503
(fail-closed, D-14); `CHATBOT_MODERACION_SI_NO_RESPONDE=PERMITIR` lo cambia.

Además, el borde cuenta `POST /api/v1/chat/(mensajes|sesiones|tickets)` (y las calificaciones)
en su zona de escritura: 120/min por dirección (`borde-dev.conf`, `comprobar-rutas.sh`).

**Redacción de datos sensibles** (7.4.8, «no almacenamiento de información sensible»):
`chat.privacidad.RedaccionDeDatosSensibles`, con `String redactar(String texto)`. Tiene una sola
implementación, `RedactorDeDatosSensibles`: un `@Component` sin configuración ni dependencias.
Tapa contraseñas y claves anunciadas («contraseña: …», «mi password is …», «pin …», «token=…»),
tarjetas de 13 a 19 cifras que cumplen Luhn con el CVV que las acompaña, tokens (Bearer, JWT,
claves de AWS, GitHub, Stripe y Slack) y claves privadas PEM. El resto del texto no cambia, así
que la lista negra sigue viendo un insulto. El resultado nunca es más largo que la entrada.
Orden: redactar → lista negra → guardar solo lo redactado. `RedactorDeDatosSensiblesTest`
impide una segunda implementación u otra interfaz con ese nombre.

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

En DEV desde #775 (29-sep, host de plataforma en `c7i-flex.large`): `desplegableDev: true` en
`infrastructure/despliegue/servicios.json`, JVM de 256 MiB y `chatbot-db` de 128 MiB
(`docker-compose.ms-chatbot.yml`), ruta `/api/v1/(chat|chatbot)` en `borde-dev.conf`. El CD lo
reconstruye cuando un commit toca el servicio y comprueba `/api/v1/chat/historial` por el
borde. Una sola instancia: el límite de frecuencia y lo que guarde en memoria son por instancia.

## Lo que falta del 7.4 (brechas declaradas)

- **WebSocket** (7.4.9): el chat es REST; no hay canal STOMP del chatbot ni contrato en
  `contracts/websocket/`.
- **Caché de respuestas frecuentes** (7.4.9): no implementada.

## Frontend

`frontend/app-web/src/comun/cliente-chatbot.js` (PR #708, equipo de Cuentas) declara su propio
identificador de visitante (`visitante-` + `crypto.randomUUID()`) y lo manda siempre: funciona
tal cual con 1.2.0, sin cambios. Recomendación para su dueño, no requisito: fuera de HTTPS
`crypto.randomUUID()` no existe y el respaldo (tiempo y `Math.random()` en base 36) es fácil de
adivinar; pedir la sesión con `POST /chat/sesiones` la haría de 256 bits. CORS ya expone
`X-Id-Sesion-Anonima` y `Retry-After` para desarrollo local.

## Pruebas

`./gradlew :services:cuentas:ms-chatbot:check` — unitarias (`ChatServiceTest`,
`ChatControllerTest`, `SesionesAnonimasTest`, `LimitadorDeFrecuenciaTest`,
`ListaNegraClientHttpTest`, `MotorConsultasAsistidasTest`…) e integración con PostgreSQL real
(`SeguridadDelHistorialIT`: el ataque de la auditoría, límite de frecuencia, mensaje
bloqueado, restricción de espacios de claves). JaCoCo ≥ 80 %.
