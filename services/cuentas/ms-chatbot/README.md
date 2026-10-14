# ms-chatbot (7.4 — Chatbot de soporte)

**Contrato:** `contracts/openapi/ms-chatbot.yaml` 1.15.0 (compatible con 1.1.0) · `contracts/websocket/chatbot.yaml` 1.2.0 (canal en tiempo real, 7.4.9)
**Stack:** Java 21 · Spring Boot 4.1 · Gradle · PostgreSQL propio (`chatbot-db`, esquema `chatbot`, Flyway V1–V20) · resource server de ms-identidad · puerto 8094, context-path `/api/v1`
**Historias:** HU-CHA-001 (chat 24/7), HU-CHA-004 (motor de reglas), HU-CHA-008 (consultas asistidas), HU-CHA-011 (calificación y brechas), HU-CHA-012 (panel de administración); endurecimiento B11.

## Qué hace

Chat de soporte para visitantes y jugadores: respuestas de una base de conocimiento
versionada (motor de reglas con tolerancia a errores ortográficos, español/inglés),
consultas en vivo sobre la propia cuenta, historial, calificación útil/no útil, brechas de
conocimiento y panel de analíticas. **No** es un modelo de lenguaje: es un motor de reglas
(interpretación de RF-CHA-014 documentada en `V4__panel_de_administracion.sql`; D-CHA-01 en
`docs/gobierno/DECISIONES-PENDIENTES-DEL-PO.md`).

## Base de conocimiento (V11, 1.5.0)

La versión en producción la publica la migración **V11** (Java, `db.migration`) a partir de
`src/main/resources/db/contenido/V11__base_de_conocimiento_fiel_al_juego.json`: corrige los
temas que describían cosas que el juego no tiene o tiene distinto (#948: logros, mazos,
pérdida de objetos, equipos de seis en el torneo…) y añade los que faltaban del 7.4.1. De
dónde sale cada afirmación (contrato, código, regla o decisión), tema por tema, está en
`contenido/evidencia-v11.md`.

- Retira la versión en producción (queda RETIRADA y se puede revertir desde el panel) y
  publica una nueva con 59 temas, todos con variantes en español e inglés.
- La candidata en edición, si la hay, recibe las mismas correcciones.
- Los temas sembrados en V2 cambian su clave (su UUID) por una legible (`crear-cuenta`,
  `publicar-subasta`…); los casos de evaluación **y las respuestas ya guardadas** pasan a la
  clave nueva, así que las analíticas de temas frecuentes siguen contando cada tema junto.
- Añade 118 casos de evaluación (dos por tema, español e inglés).

**V15 (1.11.0).** «¿Cuáles son las reglas del juego?», «¿cómo se juega?» o «what are the game
rules?» no tenían respuesta (o caían en el tema de la IA o en el de las normas de conducta).
V15 publica, como V11, una versión nueva con el tema «Reglas básicas del juego»
(`reglas-del-juego`, enlace al centro de ayuda), que resume lo ya verificado en los temas de
V11, y sus casos de evaluación; la copia conserva las intenciones adicionales de V12.

**V16 (1.11.0).** La base dice lo que el juego hace hoy. «Cómo apelar una sanción»
(`apelar-sancion`, V11) se escribió antes de la apelación sin sesión de SA-4 y decía que una
suspensión o un baneo no se podían apelar; ahora explica «Apelar esta sanción» en la
pantalla de entrada. Y 18 temas creados en DEV desde la consola, que el repositorio no
tiene, contradecían el código o el documento (la evidencia, en `contenido/evidencia-v11.md`).
Cada corrección se aplica solo al tema que sigue diciendo **exactamente** lo auditado: lo que
un administrador ya editó no se toca. Si hay algo que corregir en producción, se publica como
V11 (versión nueva, la anterior retirada y revertible); la candidata recibe lo mismo.

**V18 (1.12.0, segunda auditoría del 7.4).** La base entiende más formas de preguntar.
Publica, como V15, una versión nueva con dos temas que faltaban, «Términos del servicio»
(`terminos-del-servicio`: no hay un documento de términos aparte, así que dice lo que sí está
escrito —las causales de sanción, «Mi Cuenta» › «Privacidad», que los créditos no se cambian por
dinero— sin inventar uno) y «Cómo funciona la subasta» (`como-funciona-la-subasta`, cinco
pasos con lo ya verificado en V11); suma variantes que faltaban (otras formas de preguntar por
los modos de juego, «won't load» en inglés) y 13 casos de evaluación con erratas. Un tema solo
se crea si su clave no existe y una variante solo se suma si falta, así que lo que un
administrador ya editó se conserva. El motor, por su parte, tolera una errata de una letra en
las palabras de 6 o más letras de una frase («cómo funsionan las subatsas», «términos de
servisio»), salvo que lo escrito sea
otra palabra que la base ya conoce, con la excepción de su plural.

`contenido/base-de-conocimiento.json` es la misma base en el formato de importación del
panel (62 temas y 139 casos): sirve para restaurarla en otro entorno sin tocar la base de
datos. `BaseDeConocimientoEnProduccionIT` comprueba que cada caso activo lo responde su
tema, que ningún tema enlaza a una vista de administración y que una pregunta en inglés se
responde en inglés.

## Gestión de la base (1.6.0)

§7.4.10 «Gestión de la base de conocimiento» (RF-CHA-013) en el panel
(`/chatbot/admin/base-conocimiento/**`). Migración **V12**.

- **Intenciones adicionales** («asociar respuestas con múltiples intenciones»): un tema
  conserva su `categoria` (la intención principal, la que cuentan las analíticas) y puede
  atender hasta 5 más con la misma respuesta (`intencionesAdicionales`; sin repetir y nunca
  la principal, si no 400 con el motivo). Tabla `intenciones_adicionales_de_tema` (clave
  primaria `tema_id`+`categoria`, `ON DELETE CASCADE`); viajan con el tema: crear la
  candidata las copia (`TemaConocimiento.copiarA`), desplegar o revertir no las mueve,
  exportar las incluye e importar las respeta (un archivo sin el campo, como
  `contenido/base-de-conocimiento.json`, entra igual). Cuentan en el desempate por la vista
  del jugador (`MotorRespuestas.esMejor`), en las preguntas relacionadas
  (`Enriquecedor`: un tema sale entre las de cada una de sus intenciones; las de un tema,
  primero de su categoría) y en `GET /chat/sugerencias?categoria=`. En el panel: casillas
  «También responde a» en el formulario del tema y la columna «Intención».
- **Título duplicado → 409 `TEMA_DUPLICADO`** (RF-CHA-013 «entrada duplicada»): el mismo
  título que otro tema de la candidata, sin tildes, mayúsculas ni signos
  (`NormalizadorTexto`), al agregar o editar. La importación **reemplaza** la candidata
  entera, así que el duplicado solo puede estar dentro del archivo: también 409, y no se
  escribe nada. Las variantes repetidas de un tema se guardan una vez, sin error
  (`VariantesDePregunta.unirSinRepetir`; V11 sigue con `unir`, tal como se aplicó).
- **Importación acotada → 413 `IMPORTACION_DEMASIADO_GRANDE`**: más de 500 temas (lo
  comprueba el servicio antes de bloquear nada) o más de 8 MiB. Spring no limita el tamaño
  de un cuerpo JSON (el `max-http-form-post-size` de Tomcat es solo para formularios) y el
  borde corta en 10 MB solo lo que pasa por él: `LimiteDelArchivoDeImportacion`, dentro de
  la cadena de seguridad y después de la autorización, rechaza por `Content-Length` sin
  leer el cuerpo (o lee como mucho 8 MiB + 1 byte si llega por trozos).
- **Transiciones en fila → 409 `CAMBIO_CONCURRENTE`**: desplegar (botón y publicación
  programada), revertir, crear/descartar la candidata, editarla, importarla, evaluarla y
  programarla empiezan bloqueando la fila de la versión que cambian
  (`BloqueoDeVersiones`, `SELECT … FOR NO KEY UPDATE`), siempre en el orden producción →
  candidata. La que llega después espera; si la fila cambió de estado mientras tanto
  (PostgreSQL ya no la devuelve), falla limpia con 409 en vez del 500 del índice parcial de
  V4 o de publicar dos veces. La publicación programada que pierde la carrera no hace nada
  y vuelve a mirar al minuto. Además, `versiones_base_conocimiento.revision` (V12) es el
  `@Version` de JPA: todo UPDATE de una versión lleva la revisión leída, y un fallo de
  concurrencia de la base también responde 409 `CAMBIO_CONCURRENTE`, nunca 500.
  Reemplaza la reclamación con un UPDATE suelto de 1.3.8 (`reclamarDespliegueProgramado`),
  que además se saltaba la revisión.

`TransicionesDeVersionIT` fuerza las carreras contra PostgreSQL real (desplegar contra
revertir, dos despliegues, el botón contra la publicación programada): queda exactamente
una versión en producción y la que pierde recibe 409 (o no hace nada).
`GestionDeLaBaseDeConocimientoIT` recorre el ciclo de las intenciones (candidata,
exportación e importación, despliegue y copia siguiente), los 409 de título duplicado, los
413 de la importación y el esquema de V12.

## Preguntas sin respuesta y analíticas (1.7.0)

§7.4.5 «aprendizaje continuo» y §7.4.7 «analíticas». Migración **V13**.

**Preguntas sin respuesta** (`/chatbot/admin/brechas`; pestaña «Preguntas sin respuesta» del
panel). Las brechas de HU-CHA-011 —lo que el motor no entendió y escaló, y las respuestas
calificadas «no útil»— se guardaban y nadie las veía.

- `GET` las ordena por total (escalamientos + «no útil») y, a igual total, la más reciente
  primero; filtra por estado y por período, y pagina (16 en el panel; el servicio admite
  hasta 100). Una brecha entra en un período si tuvo actividad en él: última vez ≥ inicio y
  primera vez < fin. Los contadores son los de toda su vida: no hay registro por ocurrencia.
- `PATCH /{brechaId}` la marca `ATENDIDA` (un tema ya la cubre) o `DESCARTADA` (ruido), o la
  devuelve a `PENDIENTE`, y guarda quién (el claim `uid` del token del administrador; si no lo
  trae, el `sub`) y cuándo. Pedir el estado que ya tiene no cambia nada.
- Una `ATENDIDA` que se vuelve a preguntar regresa sola a `PENDIENTE` (sin quién, con la
  fecha del regreso) y suma `reaperturas`: el tema no bastaba. Una `DESCARTADA` sigue
  descartada y solo suma sus contadores. `BrechaConocimiento` es `@DynamicUpdate`: el
  contador que suma un jugador y el estado que cambia un administrador escriben columnas
  distintas.
- «Crear tema con esta pregunta» abre el formulario de tema de la versión candidata con la
  pregunta como primera variante en español (sin comas, hasta 200 caracteres). Sin
  candidata, lo explica y ofrece ir a «Base de conocimiento».

**Resultado de cada respuesta** (`mensajes.resultado`, V13). Lo decide el motor que respondió
(`ResultadoMotor.resultado`), no se adivina después (D-CHA-07):

| Resultado | Cuándo | Cuenta como |
|---|---|---|
| `TEMA` | respondió un tema de la base | resuelta |
| `CONSULTA_EN_VIVO` | una consulta en vivo trajo datos, aunque sea «no tienes subastas»; el informe de actividad, si llegó al menos una parte | resuelta |
| `NAVEGACION` | llevó a la sección que pidió | resuelta |
| `NO_DISPONIBLE` | el servicio consultado no respondió (en el informe: ninguna parte) | no resuelta |
| `ESCALADO` | no la entendió y ofreció soporte | no resuelta |
| `NO_SIRVIO` | «no me sirvió»: relacionadas y soporte | no resuelta |
| `PIDE_REFORMULAR` | búsqueda en el inventario demasiado corta, o «más detalle», «en inglés»… sobre una respuesta que no salió de un tema | no resuelta |

Las respuestas guardadas antes de V13 quedan sin resultado y no cuentan en la tasa.

**Fórmulas del tablero** (`GET /chatbot/admin/analiticas`; días en `America/Bogota`, período
`[desde 00:00, hasta+1 00:00)`, como mucho 366 días):

- **Conversaciones iniciadas**: `conversaciones.fecha_inicio` dentro del período. Borrar un
  historial ya no cambia el pasado. Las que tuvieron preguntas (lo que contaba antes) siguen
  en `conversacionesConActividad`; la tendencia diaria también cuenta las iniciadas.
- **Tasa de resolución**: resueltas / respuestas con resultado (tabla de arriba). `null` si
  ninguna guardó resultado. `resultados` trae el recuento de cada uno.
- **Escalamientos** son las veces que el asistente ofreció soporte (respuestas con
  `escalado`); las **solicitudes** que de verdad se abrieron son `tickets.total`. El panel las
  muestra separadas: «Ofreció soporte» y «Solicitudes de soporte».
- **Preguntas frecuentes** (`preguntasFrecuentes`): las 10 más repetidas de las últimas 5000
  preguntas del período, normalizadas con `NormalizadorTexto` (sin tildes, mayúsculas ni
  signos), ya redactadas y hasta 200 caracteres; **solo las que hicieron al menos 3
  conversaciones distintas** (D-CHA-06, provisional), para no mostrar lo que escribió una
  persona.
- **Palabras clave**: como en 1.3.7, más cuántas preguntas las usaron en el período anterior
  de los mismos días y la tendencia (`SUBE`, `BAJA`, `IGUAL`, `NUEVA`).
- **Tiempo de respuesta promedio**: lo que tarda el servidor desde que la pregunta, ya
  revisada y aprobada por la lista negra, empieza a procesarse hasta que la respuesta está
  lista para guardarse (motor, consultas en vivo y, si no la entendió, anotar la brecha). No
  incluye la lista negra, el guardado de la conversación ni la red.

**CSV sin fórmulas** (`/analiticas/exportacion`): una celda que empieza por `=`, `+`, `-`,
`@`, tabulador o retorno de carro lleva delante una comilla simple (inyección de fórmulas,
OWASP), y se sigue entrecomillando lo que trae `;`, comillas o saltos de línea. Bloques
nuevos: resultado de las respuestas y preguntas frecuentes.

**Errores del panel**: cada pestaña dice qué falló («No se pudo crear la candidata», «No se
pudieron cargar las solicitudes de soporte»…) y el detalle dice por qué; antes todas decían
«No se pudieron cargar las analíticas». En la ventana del chat, un 503
`MODERACION_NO_DISPONIBLE` al abrir una solicitud dice que no se pudo revisar el mensaje, no
que soporte no está.

`AprendizajeYAnaliticasIT` siembra un conjunto calculado a mano (3 conversaciones, 9
preguntas, 7 respuestas con resultado, 3 resueltas) y comprueba cada cifra, el CSV, el esquema
de V13, la lista y el cambio de estado de las brechas (con la reapertura) y que un jugador
recibe 403.

## Soporte desde la conversación y centro de ayuda (1.8.0)

§7.4.3 «escalamiento a agente humano con contexto de la conversación» y «enlaces a
documentación relevante». Sin migración: no hace falta nada nuevo en la base (sigue V13).

**`POST /chat/tickets/desde-conversacion`** — la solicitud en un clic. Desde 1.12.0, para el
jugador y para el visitante (ver «Solicitud automática y visitantes»). La conversación es la de
la identidad verificada (`claveDeConversacion`, que sale del `uid` del token o de la sesión
registrada del visitante); el cuerpo (`vista`, `comentario` ≤ 2000, los dos opcionales) no
elige de quién es.

- **Pregunta** (`PreguntaParaSoporte`): la última que el asistente no resolvió, buscando desde
  el final. Si no la entendió (`ESCALADO`), la pregunta de justo antes; si «no le sirvió»
  (`NO_SIRVIO`), la que respondió la respuesta anterior a ese «no me sirvió», no el «no me
  sirvió». Las respuestas anteriores a V13 cuentan por la marca `escalado`. Si nada quedó sin
  resolver, la última pregunta (no un «más detalle» ni un «en inglés»). Sin ninguna, 409
  `SIN_CONVERSACION`. Ya está redactada: se guardó así.
- **Categoría**: la del tema de esa respuesta; si no hubo tema, la de `vista`; si tampoco,
  `FAQ_GENERAL`.
- **Asunto** «Sin respuesta del asistente: <pregunta>» (150 como mucho, con «…» si se corta) y
  **mensaje** «Le pregunté al asistente: «…» y no la entendió.» (o «y su respuesta no me
  sirvió.»), más el comentario. Si no cabe en 2000, se recorta la pregunta, nunca el
  comentario.
- Después, **las mismas reglas que `POST /chat/tickets`** (`TicketSoporteService`): límite de
  frecuencia, revisión (422 `INYECCION_DETECTADA`, redacción), lista negra (422/503), una
  abierta a la vez (409 `TICKET_ABIERTO`) y el contexto redactado. `abrir` sigue leyendo la
  conversación solo si el ticket se va a guardar.

En la ventana: si alguna respuesta de la conversación ofreció soporte, «Hablar con soporte»
(el del mensaje o el de la cabecera) muestra «Crear solicitud con esta conversación» encima
del formulario, que sigue igual. Un clic la crea, lo confirma y la solicitud aparece en «Tus
solicitudes».

**`GET /chat/ayuda`** — el centro de ayuda (`frontend/app-web/src/cuentas/ayuda.html`, vista
pública `ayuda`, en el menú de la cuenta). Los temas activos de la versión **en producción**,
sin los de cortesía, por categoría en el orden de `Categoria` y dentro de cada una por
prioridad y título: título, respuesta en español e inglés y pasos (los de los temas paso a
paso, como en el chat). Nada interno (clave, variantes, prioridad, versión).
`Cache-Control: public, max-age=300` (Spring Security no pisa un `Cache-Control` que ya trae la
respuesta).

**Enlace «Ver el centro de ayuda»** (`Enriquecedor`): al no entender una pregunta, tras un «no
me sirvió» y en la oferta de soporte sola, con `destino` `ayuda` y, si se sabe la categoría
(la de la vista al escalar, la del tema tras un «no me sirvió»), `ancla`
`categoria-<categoría>`: es el mismo `ancla` que trae cada grupo de `GET /chat/ayuda`
(`Enriquecedor.anclaDe`). `EnlaceInterno.ancla` no se escribe si no hay, así que los enlaces
de siempre se guardan y se mandan igual.

**Borde**: nada que cambiar. `/api/v1/(chat|chatbot)` ya va al servicio; el `POST` nuevo cuenta
en la zona de escritura (la expresión `POST /api/v1/chat/(mensajes|sesiones|tickets)` no se
ancla al final), y `GET /chat/ayuda`, como toda lectura, no tiene límite en el borde: es
barata, igual para todos y se puede guardar en caché.

`SoporteDesdeLaConversacionYAyudaIT`: con PostgreSQL real, dos jugadores con su propia
pregunta (redactada: la tarjeta no llega a la base), el 409 de la segunda, la que se ve en «Tus
solicitudes», el «no me sirvió» con su categoría, la del visitante, el 401 sin identidad y el
409 `SIN_CONVERSACION`; y el centro de ayuda solo con lo activo en producción, sin campos
internos y con su `Cache-Control`.

## Solicitud automática y visitantes (1.12.0)

RF-CHA-007 pide «generar automáticamente un ticket de soporte» si el chatbot no puede
resolver la consulta, con el Visitante y el Jugador como actores. Hasta 1.11.0 la solicitud
pedía un clic y el visitante recibía 401: la segunda auditoría del 7.4 lo encontró y no había
decisión que lo cubriera. Sin migración: la tabla de V6 ya guarda cualquier clave de 64.

- **Se abre sola** (`TicketSoporteService.alNoResolver`, desde `ChatService`) cuando el
  asistente no entiende una pregunta (`ESCALADO`) o le dicen «no me sirvió» (`NO_SIRVIO`),
  después de guardar el intercambio: lleva esa pregunta y la conversación, redactadas, con el
  asunto, el mensaje y la categoría de la solicitud en un clic. Las mismas reglas (revisión,
  lista negra, una abierta por persona) salvo el límite de frecuencia: el mensaje que la
  provocó ya lo pasó.
- **Una abierta por persona.** Si ya hay una (`ABIERTO` o `EN_PROCESO`), la pregunta y su
  respuesta se suman a su contexto (`TicketSoporte.sumarAlContexto`, los 20 más recientes) en
  una transacción. Dos a la vez: el índice único de V6 deja abrir una y la otra se suma.
- **La respuesta lo dice** al final («Abrí una solicitud de soporte…» o «Sumé esta pregunta…»)
  y trae `enriquecido.solicitud` (`id`, `asunto`, `estado`, `nueva`); se guarda así, de modo
  que el historial la enseña igual (`RegistroDeConversaciones.completarRespuesta`). La ventana
  pinta «Ver solicitud» en lugar de «Hablar con soporte».
- **Si no se puede abrir** (la lista negra no responde, un error de la base), la respuesta lo
  dice y ofrece el formulario y el centro de ayuda; nunca falla por eso. Es el «canal
  alternativo» de las excepciones de RF-CHA-007.
- **El visitante** abre, ve y recibe en vivo las suyas con su sesión (`X-Id-Sesion-Anonima`,
  las reglas de `POST /chat/mensajes`; `ResolutorDeIdentidad.resolverODeclarar` registra la
  declarada al abrir, nunca emite una). Son de la clave de su conversación (`anonimo:<sesión>`,
  ningún uid puede ocuparla, V5) y las ve mientras dure la sesión (24 h sin actividad). Al
  listar, una sesión con forma que no está registrada o venció recibe `[]`; sin token ni
  sesión, 401 como antes. En el panel, `deVisitante` y «Un visitante sin cuenta» en lugar de
  un uid. No se le pide correo ni teléfono (D-CHA-11).
- El texto del escalamiento ya no promete que alguien escribirá: «Esta pregunta quedó
  registrada para mejorar mis respuestas».

`SolicitudesAutomaticasIT` (PostgreSQL real): la primera pregunta sin resolver abre la de un
jugador (redactada, con la categoría de la vista), la segunda se suma, una resuelta no la toca,
el historial la guarda, el botón de un clic da 409 y, resuelta por un administrador, la
siguiente abre otra; el «no me sirvió» con la pregunta del tema; la del visitante, que solo
ve su sesión y que el administrador reconoce; y la que abre con el formulario antes de
escribir. `CanalDelChatbotIT` añade el aviso en vivo al visitante (y a ningún otro).

## Capturas de pantalla para soporte técnico (7.4.6, 1.14.0)

§7.4.6 dice que la interfaz «debe incluir» la «opción de adjuntar capturas de pantalla para
soporte técnico». No había ninguna forma de mandar un archivo: la decisión que descartaba las
imágenes no tiene registro escrito (D-CHA-02). Las capturas van en la **solicitud de soporte**,
no en la conversación: no las lee el asistente sino la persona que atiende la solicitud. Cifras y
reglas en D-CHA-12.

- `POST /chat/tickets/{ticketId}/capturas` (multipart, parte `archivo`): solo quien abrió la
  solicitud, con la identidad con la que la abrió (el `uid` del token o la sesión vigente del
  visitante; aquí nunca se registra una), y mientras siga `ABIERTO` o `EN_PROCESO`. La de otra
  persona responde 404 igual que una que no existe (`CapturasDeSoporteService`). Pasa por el
  límite de frecuencia de los mensajes.
- **`ExaminadorDeCapturas`**, en este orden: de 1 byte a 2 MB; PNG o JPEG por su firma de bytes
  (nunca por el nombre ni el `Content-Type`); el archivo completo y acabando donde su formato dice
  (un PNG en `IEND` con cada CRC en orden, un JPEG en `FF D9`), así que un archivo cortado o un
  políglota (una imagen con un HTML o un ZIP detrás) es 415; y las medidas, hasta 4096 por lado,
  leídas de la cabecera **sin decodificar la imagen**. Las mismas comprobaciones que las imágenes
  de comentarios, sin WebP.
- **Sin metadatos**: se reescribe con solo lo que hace falta para verla (del PNG, cabecera,
  paleta, datos, transparencia, color, fondo y densidad; del JPEG, JFIF, el perfil de color y
  Adobe). Se van el EXIF (con la ubicación de una foto del móvil), XMP, IPTC, los textos, los
  comentarios y la fecha. No se vuelve a codificar. El nombre del archivo no se guarda: la
  ventana ni siquiera lo manda.
- **Dónde**: en la base del servicio (V19, `tickets_soporte_capturas`, `BYTEA`), como el resto
  de la solicitud: el host de DEV no tiene almacenamiento de objetos. Tres por solicitud como
  mucho, contadas con la solicitud bloqueada (`TicketSoporteRepository.bloquear`), así que dos
  subidas a la vez no pasan de tres; se borran con ella (`ON DELETE CASCADE`).
- **Quién las ve**: su dueño (`GET /chat/tickets/{ticketId}/capturas/{capturaId}`) y un
  administrador (`GET /chatbot/admin/tickets/{ticketId}/capturas/{capturaId}`), con el tipo
  comprobado, `nosniff`, `Content-Security-Policy: default-src 'none'; sandbox`,
  `Cache-Control: no-store, private`, `Cross-Origin-Resource-Policy: same-origin` y un nombre
  propio (`RespuestaDeCaptura`). `GET /chat/tickets`, el panel y el aviso del canal (`chatbot.yaml`
  1.2.0) traen sus datos (`CapturaResponse`), nunca el contenido.
- **Errores** con `motivo`: 400 `CAPTURA_AUSENTE`, 404 `SOLICITUD_NO_ENCONTRADA`, 409
  `SOLICITUD_CERRADA` o `CAPTURAS_COMPLETAS`, 413 `CAPTURA_DEMASIADO_GRANDE` y 415
  `CAPTURA_NO_ADMITIDA`. El techo de Spring (`spring.servlet.multipart.*`: 2 MB por archivo, 3 MB
  por petición) lee el cuerpo al resolver el parámetro (`resolve-lazily`), así que también su 413
  sale con el motivo; para eso `ManejadorErroresDelChat` va antes que el de problem details de
  Spring Boot, que sigue resolviendo los demás errores de Spring MVC.
- **Lo que no se puede redactar**: a una imagen no se le aplica la redacción de los textos
  (7.4.8). La ventana avisa antes de elegirla que no se vean contraseñas, códigos ni tarjetas.

`CapturasDeSoporteIT` (PostgreSQL real): la dueña adjunta un PNG y un JPEG con texto, EXIF y
comentario y lo guardado no los lleva; «Tus solicitudes» las lista en orden; el contenido sale
con sus cabeceras; otro jugador, otra sesión o un token ajeno reciben 404 y nada se guarda; el
administrador las ve y las lee, y por la ruta de otra solicitud no; GIF, SVG, políglota y más de
2 MB se rechazan; tres sí y la cuarta no; respondida ya no admite; el visitante adjunta a la
suya; y se borran con su solicitud.

## Sugerencias para ti y detalle según la experiencia (7.4.3 y 7.4.5, 1.15.0)

- **«Quizá te interese»** (§7.4.3 «sugerencias proactivas: recomendaciones basadas en el
  comportamiento del usuario», D-CHA-14): `GET /chat/sugerencias/para-ti?vista=&limite=`
  (`SugerenciasParaTiService`) propone, con la identidad del chat (token o sesión de
  visitante registrada; nunca un parámetro), primero otras preguntas de la misma categoría que
  los temas que ya le respondieron (`POR_LO_QUE_PREGUNTASTE`, con `relacionadoCon`: el tema que
  las trajo; dos por tema, de las 50 respuestas más recientes), luego las de la sección donde
  está (`POR_DONDE_ESTAS`) y, solo a quien aún no preguntó nada, las más consultadas
  (`PARA_EMPEZAR`). Nunca un tema de cortesía ni uno ya respondido; todo de la versión en
  producción. Se calcula al pedirlo: no guarda nada, no abre conversación ni registra una
  sesión, y no mira otro servicio. Borrar la conversación lo devuelve a lo de quien empieza.
- **Nivel de detalle automático** (§7.4.5 «adaptar el nivel de detalle según la experiencia del
  usuario», RF-CHA-010, D-CHA-13): `nivelDetalle` admite `AUTOMATICO` (V20), el de quien nunca
  eligió. `ExperienciaDelChat` lo traduce antes de responder con las preguntas suyas que el
  asistente ya resolvió (`TEMA`, `CONSULTA_EN_VIVO`, `NAVEGACION`): menos de 5, `DETALLADO`;
  hasta 29, `NORMAL`; desde 30, `BREVE`, y la respuesta recortada lleva delante el botón «Más
  detalle» (un seguimiento: la da entera). El motor y la caché solo ven el nivel ya traducido;
  `GET`/`PUT /chat/preferencias` devuelven también `nivelDetalleEfectivo`. Un nivel elegido a
  mano manda; las filas guardadas antes de V20 no cambian.

## Diálogo (1.5.0)

- **Idioma:** las respuestas en vivo, la navegación, los avisos y el escalamiento van en el
  idioma de la pregunta (`IdiomaDelTexto`), o en el de las preferencias del chat. Las
  secciones se nombran como en la barra; en inglés, la etiqueta española con su traducción.
- **Errores de tecleo:** `DistanciaDeEdicion` (Damerau en su forma de alineación óptima):
  un error por palabra de 5 letras o más, y dos letras vecinas cambiadas cuentan como uno.
- **Seguimiento de la respuesta anterior** (`Seguimiento`): «más detalle», «en inglés», «en
  español», «repítelo» y «no me sirvió», solos o con relleno («por favor», «can you»), se
  resuelven con la última respuesta del bot en esa conversación. «No me sirvió» ofrece
  soporte humano, cuenta como escalamiento y no se registra como brecha. Con una palabra de
  tema («explícame mejor cómo publicar una subasta») ya es una pregunta nueva.

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

**1.11.0: el valor entero.** Hasta la 1.10.0 la puntuación en que terminaba una contraseña
quedaba a la vista: «mi contraseña es Hunter2024!» se guardaba «… ***!», y ese símbolo es parte
de la contraseña (la política de claves exige uno). Desde la 1.11.0 se tapa el valor entero (se
pierde, a lo sumo, el punto de la frase) y la migración **V14** vuelve a redactar lo guardado con
la regla nueva, con la misma implementación de V10.

**1.12.0: las claves anunciadas con «es».** La segunda auditoría encontró que «mi clave es
Hunter2024! gracias» se guardaba entera: clave, token, secret y pin son palabras ambiguas («la
clave es atacar primero») y antes solo anunciaban un secreto con dos puntos o «=». Ahora, con
«es»/«is» o sin separador, también lo anuncian si el valor lo parece (letras y dígitos, o un
símbolo): «mi clave es Hunter2024!», «mi token es abc123XYZ», «my secret is P4ssw0rd!» y «mi
clave Hunter2024 no sirve» se tapan; «la clave es atacar primero» o «el token es válido» quedan
igual. La migración **V17** vuelve a redactar lo guardado, otra vez con la implementación de
V10.

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
| Búsqueda en el inventario (1.5.0) | inventario `GET /api/v1/inventario/elementos/busqueda` | token del jugador |
| Subastas | ms-subastas `GET /mis-pujas/resumen` | token del jugador |
| Notificaciones | notificaciones `GET /users/{uid}/notifications` | token del jugador |
| Movimientos de créditos (B11) | ms-finanzas `GET /creditos/{uid}/movimientos` | token del jugador |
| Torneos en curso y mi equipo (B11) | torneos `GET /torneos`, `GET /torneos/{id}` | ninguna (dato público) |
| Misiones en curso (1.3.0) | misiones `GET /api/v1/misiones/en-curso` | token del jugador |
| Catálogo de productos (1.10.0, también visitantes) | productos `GET /api/v1/productos` | ninguna (dato público) |

**Cómo se reconoce (1.11.0).** El inventario, las subastas, las notificaciones, las
transacciones y las misiones se reconocen igual (`IntencionDeConsulta`): habla de ese tema,
pregunta por lo suyo («mi», «mis», «tengo», «my», «I have») y no es una pregunta de reglas.
Así «¿tengo alguna misión activa?», «¿cuáles son mis últimas transacciones?» o «¿qué subastas
tengo?» llegan a la consulta en vivo, y «¿puedo cancelar mi puja?», «¿qué pasa si abandono mi
misión?» o «¿tengo que pagar para subastar?» los responde la base aunque digan «mi».

**Un visitante (1.11.0)** que pide algo de esto, o ir a una sección suya, recibe que hace falta
una sesión, con enlaces a «Entrar» y «Crear cuenta»: no se consulta nada ni se registra como
pregunta sin respuesta. **Navegación pública** (`NavegacionPublica`, para todos): «llévame a la
subasta», «ir al centro de ayuda», «take me to the store»… con el enlace a la vista; la tienda,
el chat general y «Jugar online» piden sesión, y a un visitante se le dice que entre.

«Busca espada en mi inventario», «¿tengo alguna poción en mi inventario?» o «search my
inventory for a sword» usan la búsqueda real (criterio de 4 letras o más, como pide el
contrato del inventario). El **informe de actividad** pide sus cinco partes a la vez (hilos
virtuales) y espera como mucho 1,7 s en total: la parte que no llega se dice «no disponible»
y el resto se entrega (7.4.11, menos de 2 s).

## Catálogo en vivo (7.4.1, 7.4.2 y 7.4.5, 1.10.0)

Para visitantes y jugadores: los productos que se venden hoy, sus precios y su ficha salen del
catálogo público de productos (`productos.yaml`, `GET /api/v1/productos`, **sin credencial**:
la proyección que ve cualquier visitante, sin campos internos ni suspendidos), no de un texto
copiado en la base. Un producto nuevo, un precio cambiado o uno retirado se ven en el chat sin
editar ningún tema: es la «actualización automática de la base de conocimiento con nueva
información del sistema» de 7.4.5.

- «¿Qué productos hay en la tienda?», «¿qué armas venden?», «what can I buy?»: cuántos hay
  por tipo y algunos con su precio.
- «¿Cuánto cuesta la espada de una mano?», «precio del orbe»: el precio del producto que se
  nombra (nombre completo, o sus palabras distintivas con un error de tecleo como mucho). Si
  hay varios, hasta tres con su precio; si hay más, pide el nombre completo.
- «¿Qué hace la espada de dos manos?»: su descripción y su precio.
- Lo que no nombra ningún producto («¿cuánto cuesta publicar una subasta?») o pregunta cómo
  hacer algo («¿cómo compro en la tienda?») lo responde la base, como antes.

`CatalogoEnVivo` guarda una sola copia para todo el servicio (es dato público, igual para
todos) durante 5 minutos y, si el catálogo no responde, sigue usando la última hasta 30
minutos (D-CHA-10). `PRODUCTOS_BASE_URL` es la raíz del servicio de productos, la misma que
usa ms-identidad (host de contenido, 8103).

**Imágenes (7.4.3, 1.13.0).** §7.4.3 pide respuestas enriquecidas «incluyendo imágenes,
enlaces y tarjetas informativas». La decisión que las descartaba no tiene registro escrito
(D-CHA-02), así que el requisito vuelve a aplicar. Las respuestas del catálogo llevan una
`TarjetaInformativa` por producto nombrado (los mismos que el texto: hasta cinco en el
listado, tres en los precios, uno en la ficha), con su nombre, su tipo y su precio y, en
`imagen`, la imagen del producto, solo si `ImagenDeProducto` la deja pasar:

- un recurso gráfico del catálogo, `/api/v1/productos/recursos/<id>` (HU-PRD-009: comprobado al
  subirlo y servido con `nosniff` y `Content-Security-Policy: default-src 'none'`);
- o una imagen incrustada pequeña (`data:image/png|jpeg|webp|gif|svg+xml`, hasta 8192
  caracteres, sin espacios ni comillas): así son hoy las de la semilla.

Nada más: ni una URL de otro sitio (ni rastreo ni contenido mixto), ni un nombre suelto como
`espada.png` que nadie sirve (en DEV hay productos así); entonces la tarjeta va sin imagen.
`imagen` no se escribe si no hay, así que las tarjetas de siempre (las de misiones) se guardan
igual. La ventana repite la misma regla (`imagenSegura`), pinta la imagen en un `<img>` con
texto alternativo vacío (el nombre va al lado) y la quita si no carga; con dos o más productos
los pasa en un carrusel que se recorre con sus botones, la rueda o el teclado. El asistente
sigue sin **recibir** imágenes en el chat: `adjuntoUrl` sigue obsoleto; las capturas para
soporte técnico van en la solicitud (1.14.0, más arriba).

## Tiempo real (7.4.9, `contracts/websocket/chatbot.yaml` 1.2.0)

«WebSocket para comunicación en tiempo real». STOMP sobre WebSocket nativo (sin SockJS), como
ms-subastas, en `/api/v1/ws-chatbot` (`chat.tiemporeal`). No sustituye a la API: es la misma
conversación por otro transporte, y cada pregunta pasa por `ChatService.enviarMensaje`, el
mismo camino que `POST /chat/mensajes` (límites por identidad y por la dirección del
handshake, revisión de la entrada, lista negra, motores, caché, guardado). Nada se duplica.

| Destino | Quién | Qué |
|---|---|---|
| `SEND /app/chat/mensajes` | cliente | `{contenido, vista?, idCorrelacion?}` (`EnviarMensaje` de la API más la correlación) |
| `/user/queue/chat/estado` | servidor | `{estado: "ESCRIBIENDO"}` en cuanto acepta la pregunta |
| `/user/queue/chat/respuestas` | servidor | el `Mensaje` de la API, solo a la conexión que preguntó |
| `/user/queue/chat/errores` | servidor | problem details plano con `motivo` y, en un 429, `reintentarEn` |
| `/user/queue/soporte/tickets` | servidor | lo que el dueño ve de su solicitud al atenderla un administrador |

- **Handshake**: solo los orígenes de CORS (`CORS_ORIGENES`), sin identidad (el navegador no
  puede mandar cabeceras). `SecurityConfig` deja pasar `/ws-chatbot`; la identidad va en el
  CONNECT.
- **CONNECT, con las reglas de la API** (`PoliticaDelCanal` + `ResolutorDeIdentidad.enElCanal`):
  `Authorization: Bearer` válido con `uid` → jugador; uno inválido, vencido o sin `uid` degrada
  a visitante; un visitante necesita `X-Id-Sesion-Anonima` (emitida vigente o declarada). Sin
  ninguna, frame `ERROR`. El nombre de la conexión lo pone el servidor: el `uid` o `anonimo:` +
  la huella de la sesión; nunca algo del cliente.
- **Interceptor**: `SUBSCRIBE` solo a los cuatro `/user/queue/...` de la tabla y `SEND` solo a
  `/app/chat/mensajes`. Lo demás (`/user/<uid>/...`, `/queue/...` directo, `/topic`, comodines)
  recibe `ERROR` con su motivo (`ErroresDelProtocolo`) y la conexión se cierra.
- **En cada mensaje** la identidad se vuelve a resolver con lo que trajo el CONNECT (un token que
  vence deja de ser un jugador, como en la API). La respuesta lleva `id-correlacion` y
  `traceparent` (regla 5: cada pregunta abre su traza). Se responde en un hilo virtual por
  pregunta, con **2 en vuelo por conexión** como mucho (la siguiente, 429).
- **Tamaño**: un mensaje WebSocket de más de 16 640 caracteres (el mínimo que fija Spring para
  STOMP) cierra la conexión; una pregunta de 4000 cabe de sobra y la ventana manda por HTTP lo
  que, codificado en JSON, pasaría de 16 000.
- **Soporte en vivo**: `TicketSoporteAdminService.atender` publica `TicketActualizado` y, tras el
  commit, `AvisosDeSoporte` lo manda al `uid` del dueño (todas sus conexiones, nadie más). Desde
  1.1.0 también al visitante: la solicitud es de `anonimo:<sesión>` y `AvisosDeSoporte` la
  traduce, con la sesión registrada, al nombre de sus conexiones (`anonimo:<huella>`); si la
  sesión ya no existe, no avisa a nadie.
- **Una sola instancia.** El broker es el simple de Spring, en memoria: conexiones,
  suscripciones y avisos son de la instancia. Escalar horizontalmente exigiría un broker externo
  (relay STOMP), que no está desplegado; el contrato no promete más. Efecto sabido: el
  planificador del broker es el único `TaskScheduler`, así que las tareas `@Scheduled` corren en
  él (un hilo por procesador), como en ms-subastas.

| Variable | Por omisión | Qué fija |
|---|---|---|
| `CHATBOT_WS_LATIDO_MS` | 10000 | latido STOMP en cada sentido (D-CHA-09) |
| `CHATBOT_WS_MENSAJES_EN_VUELO` | 2 | preguntas sin responder por conexión (D-CHA-09) |

Pruebas: `PoliticaDelCanalTest`, `ConversacionEnVivoTest`, `PiezasDelCanalTest` y
`CanalDelChatbotIT` (`WebSocketStompClient` contra el puerto real, PostgreSQL de Testcontainers
y JWT RS256 firmados de verdad): B nunca recibe la respuesta ni el ticket de A, un visitante no
se suscribe a `/user/<uid>/...`, y el 429 por identidad llega con `reintentarEn`.

## Caché de respuestas frecuentes (7.4.9, `ms-chatbot.yaml` 1.9.0)

- **Qué se guarda**: solo el `ResultadoMotor` de la base de conocimiento
  (`MotorRespuestas.generarRespuesta`). Clave: versión en producción + huella SHA-256 de la
  pregunta normalizada + idioma + nivel de detalle + `vista` (`CacheDeRespuestas.Clave`). No
  depende de quién pregunta. **Nunca** pasan por ella las consultas en vivo
  (`MotorConsultasAsistidas`, privadas de cada jugador), los seguimientos (`Seguimiento`) ni el
  aviso de un dato tapado. La brecha de una pregunta escalada se registra igual aunque la
  respuesta salga de la caché.
- **Cuánto**: LRU acotada con vigencia, propia (sin dependencias nuevas): 500 respuestas, 30 min
  cada una (D-CHA-08); unos 3 KB por respuesta, ~1,5 MB del montón de 128 MiB.
- **Versiones**: en cada mensaje se consulta solo el id de la versión en producción (una fila);
  los temas se leen una vez por versión (`TemasEnProduccion`, unos cientos de KB). Un despliegue,
  una reversión o la publicación programada —en esta instancia o en otra— cambian ese id: lo
  guardado con la anterior se descarta y una respuesta calculada con la vieja no se guarda.
- **Métricas** (Actuator): `chatbot.cache.respuestas.aciertos`, `.fallos`,
  `.desalojos{causa=capacidad|caducidad|version}`, `.tamano`; `chatbot.cache.temas.cargas` y
  `.tamano`.

| Variable | Por omisión | Qué fija |
|---|---|---|
| `CHATBOT_CACHE_RESPUESTAS_MAXIMO` | 500 | respuestas guardadas (0 la apaga) |
| `CHATBOT_CACHE_RESPUESTAS_MINUTOS` | 30 | minutos que vale cada una |

Pruebas: `CacheDeRespuestasTest`, `TemasEnProduccionTest`, `RespuestasEnCacheTest` y
`RespuestasFrecuentesIT` (una pregunta repetida no vuelve al repositorio de temas: lo cuentan el
repositorio espiado y las estadísticas de Hibernate; desplegar y revertir leen los temas
nuevos).

## Despliegue

En DEV desde #775 (29-sep, host de plataforma en `c7i-flex.large`): `desplegableDev: true` en
`infrastructure/despliegue/servicios.json`, JVM de 256 MiB y `chatbot-db` de 128 MiB
(`docker-compose.ms-chatbot.yml`), rutas `/api/v1/(chat|chatbot)` y `/api/v1/ws-chatbot` (con
`Upgrade`, 7.4.9) en `borde-dev.conf`. El CD lo reconstruye cuando un commit toca el servicio y
comprueba `/api/v1/chat/historial` por el borde. Una sola instancia: el límite de frecuencia, la
caché, el broker del canal en tiempo real y lo que guarde en memoria son por instancia.

## Frontend

`frontend/app-web/src/comun/cliente-chatbot.js` (PR #708, equipo de Cuentas) declara su propio
identificador de visitante (`visitante-` + `crypto.randomUUID()`) y lo manda siempre: funciona
tal cual con 1.2.0, sin cambios. Recomendación para su dueño, no requisito: fuera de HTTPS
`crypto.randomUUID()` no existe y el respaldo (tiempo y `Math.random()` en base 36) es fácil de
adivinar; pedir la sesión con `POST /chat/sesiones` la haría de 256 bits. CORS ya expone
`X-Id-Sesion-Anonima` y `Retry-After` para desarrollo local.

7.4.9: `frontend/app-web/src/comun/canal-chatbot.js` abre el canal (con
`transporte-stomp.js` y la reconexión de `canal-reconectable.js`) cuando la ventana ya cargó
la conversación. Mientras está abierto las preguntas van por STOMP y «escribiendo…» sale del
`ESCRIBIENDO`; si no abre, se cae o no acusa una pregunta a tiempo (5 s; 30 s para la
respuesta), esa pregunta va por HTTP y el canal se rehace (degradación controlada, riesgo #7;
en el peor caso la pregunta queda dos veces en el historial, nunca se pierde). El estado se ve
en la píldora del kit en modo discreto, y «Tus solicitudes» se actualiza sola.

1.14.0 (§7.4.6): `comun/ui/capturas-chatbot.js` pinta las capturas de una solicitud, plegadas
(«Ver captura» las pide con el token o la sesión y las enseña con un enlace de objeto que se
libera al repintar), y el selector para adjuntarlas, con la pista de lo que no debe verse ANTES
de elegir. En «Hablar con soporte» (`soporte-chatbot.js`) cada solicitud abierta con sitio lo
ofrece y el formulario admite una captura opcional, que se sube en cuanto la solicitud queda
abierta; en el panel (`panel-chatbot-soporte.js`) el detalle de la solicitud las enseña.

1.15.0 (§7.4.3 y §7.4.5): al cargar la conversación, `ventana-chatbot.js` pinta al final la
burbuja «Quizá te interese» (`contenidoParaTi` en `sugerencias-chatbot.js`), agrupada por el
motivo que la explica; es del navegador (no entra en el historial) y se va con la siguiente
pregunta. Si la conversación cambia de alto mientras se leía lo último, lo último sigue a la
vista. En «Preferencias», el nivel «Automático» dice con qué nivel se le responde hoy, y
«Restablecer» vuelve a las de quien nunca eligió (RF-CHA-010).

## Pruebas

`./gradlew :services:cuentas:ms-chatbot:check` — unitarias (`ChatServiceTest`,
`ChatControllerTest`, `SesionesAnonimasTest`, `LimitadorDeFrecuenciaTest`,
`ListaNegraClientHttpTest`, `MotorConsultasAsistidasTest`…) e integración con PostgreSQL real
(`SeguridadDelHistorialIT`: el ataque de la auditoría, límite de frecuencia, mensaje
bloqueado, restricción de espacios de claves; `BaseDeConocimientoFielAlJuegoIT`: V10 → V11
con producción, candidata y respuestas guardadas; `BaseDeConocimientoEnProduccionIT`: los
155 casos de evaluación contra la base publicada; `GestionDeLaBaseDeConocimientoIT` y
`TransicionesDeVersionIT`: V12 y la gestión de la base de 1.6.0; `AprendizajeYAnaliticasIT`:
V13, preguntas sin respuesta y fórmulas del tablero de 1.7.0;
`SoporteDesdeLaConversacionYAyudaIT`: la solicitud desde la conversación y el centro de ayuda
de 1.8.0; `CanalDelChatbotIT` y `RespuestasFrecuentesIT`: el canal en tiempo real y la caché
de 7.4.9; `CatalogoEnVivoIT`: el catálogo de 1.10.0; `RedactarLaPuntuacionDeLosSecretosIT` y
`ReglasDelJuegoIT` y `BaseDeConocimientoAlDiaIT`: V14, V15 y V16 de 1.11.0;
`RedactarLasClavesAnunciadasIT` y `SolicitudesAutomaticasIT`: V17 y la solicitud automática de
1.12.0; `CapturasDeSoporteIT`: V19 y las capturas de 1.14.0; `NivelDeDetalleAutomaticoIT` y
`SugerenciasYExperienciaIT`: V20, el nivel por experiencia y las sugerencias para ti de
1.15.0). JaCoCo ≥ 80 %.
