# ms-subastas

**Sprint:** 2 · **Modulo:** M12 — Subasta · **Historias:** HU-SUB-001 (Edwin), HU-SUB-011 (Cristian), HU-SUB-004 (Andres)

Mercado de subastas entre jugadores: publicar un producto, listarlos con filtros en vivo, y pujar o comprar de forma inmediata.

## Estado de esta carpeta (HU-SUB-004)

Lo que ya esta desarrollado, de lo que no depende de nadie mas del equipo:

- `pujas/model` — `Puja`, `PujaAutomatica` y sus enums.
- `pujas/creditos` — contrato `CreditoClient` hacia ms-finanzas (reservar/liberar/consumir/saldo) + `CreditoClientFake` en memoria para desarrollar y testear sin depender del endpoint real.
- `pujas/service/MotorPujasService` — las 5 reglas de negocio de pujar (supera oferta + incremento minimo, intervalo de 5 s, prohibido pujar en la propia subasta, topes de 10 subastas / 50 pujas activas) y la compra inmediata.
- **El intervalo de 5 s se mide por jugador Y SUBASTA, no por jugador a secas.** La consulta era global (`findFirstByJugadorIdOrderByCreadaEnDesc`) y eso contradecia la propia HU: con las 10 subastas simultaneas que permite, pujar en una bloqueaba al jugador en las otras nueve durante 5 s, y su propia puja automatica en una subasta le impedia pujar a mano en otra. El freno existe contra el spam dentro de una subasta, no contra participar en varias. Cubierto por `pujarEnUnaSubastaNoBloqueaPujarEnOtraDistinta` y `pujarDosVecesSeguidasEnLaMismaSubastaSigueRechazandose`.
- `pujas/service/MotorPujaAutomaticaService` — configurar una puja automatica (validando saldo y que el limite sea alcanzable), elegir cual de varias responde (la de mayor limite), cuanto ofrecer, y desde cuando puede volver a emitir sin violar el intervalo de 5 s.
- `pujas/service/ParametrosPuja` — los 4 limites configurables (`app.pujas.*`).
- `pujas/service/PujaApplicationService` — orquesta la transaccion: toma el lock pesimista de la subasta, arma el contexto de participacion desde la base de datos y delega las reglas en el motor.
- `subastas/repository` y `pujas/repository` — repositorios JPA, con `findByIdParaActualizar` (SELECT ... FOR UPDATE) como guardia real de concurrencia.
- `subastas/model/Subasta` — **borrador** pendiente del diseno conjunto del Dia 1 con Edwin y Cristian; puede cambiar.
- `db/migration/V1__create_subastas_pujas.sql` — esquema por Flyway (nunca `ddl-auto`), con los indices que sostienen las reglas: unico parcial de una sola puja vigente por subasta, `(jugador_id, creada_en DESC)` para el intervalo de 5 s, y parcial por jugador para los topes de 10/50.
- `pujas/creditos/CreditoClientResiliente` — cortacircuitos y reintento (Resilience4j) sobre la llamada a ms-finanzas, que ocurre dentro del lock de la subasta. Los rechazos de negocio estan excluidos del reintento: saldo insuficiente no es un fallo del servicio.
- `pujas/service/EmisionDePujasAutomaticasJob` — **emite** las pujas automaticas, cada 2 s (`app.pujas.emision-automatica-intervalo-ms`). Sondeo en vez de escuchar un evento `PujaRealizada`, y no es un atajo: el criterio de la HU exige respetar el intervalo minimo de 5 s entre pujas del mismo jugador, asi que una puja automatica nunca puede responder al instante. Con ese techo, sondear cada 2 s llega igual de rapido que un evento y no depende del catalogo de eventos (`contracts/eventos/`), que sigue vacio. Cada subasta se procesa en su propia transaccion: si una falla, las demas siguen. Una puja automatica rechazada por perder la carrera contra un humano se registra como `debug`, no como error — es el caso normal, no un fallo.
- `pujas/service/CierreDeSubastasVencidasJob` — cierra las subastas pasadas de fecha cada 30 s (`app.subastas.cierre-intervalo-ms`), restituyendo los creditos del postor que queda sin adjudicacion. Tambien una transaccion por subasta. Pendiente de acordar con Edwin quien dispara el cierre (ver abajo).
- `notificaciones/NotificacionOutbox` — los avisos se escriben en la misma transaccion que cierra la subasta (patron outbox), no por una llamada aparte que podria fallar despues de cerrar. `V2` crea la tabla con un indice parcial sobre las no enviadas, para que el servicio de notificaciones las drene sin recorrer el historico.
- `seguridad/ValidadorDeToken` — valida firma y expiracion del JWT que emite ms-identidad (misma version de jjwt, 0.12.6, para no divergir en el formato), y devuelve un `IdentidadDelSolicitante` con apodo, rol y version. Es fail-closed: sin rol, sin sujeto, sin firma o sin version de rol, el token se rechaza. No verifica revocacion (ver "Asunciones tomadas", punto 4). **Vive aqui, no en `shared/libs/`, a proposito:** esa carpeta todavia no es un modulo Gradle y crearla fijaria la estructura para los tres equipos sin haberlo acordado. La clase no depende de Spring ni del dominio — solo de jjwt y `java.time` — asi que mudarla cuando se acuerde es mover el paquete y declarar la dependencia. **Todavia no esta conectado a Spring**: falta el interceptor, que depende de resolver antes el apodo-a-UUID (ver Pendiente).
- Pruebas: 80 en verde. Unitarias de las 5 reglas, `MotorPujasServiceConcurrenciaTest` en memoria, y `PujaConcurrenciaPostgresTest` contra PostgreSQL real (Testcontainers) que si ejercita el lock pesimista y el indice unico parcial. Compuerta JaCoCo al 80% que rompe el build, y `ReglasDeArquitecturaTest` (ArchUnit) que falla si alguien cruza dominios, mete persistencia en el motor o usa `Instant.now()`.

## Contratos

- `contracts/openapi/ms-subastas-pujas.yaml` — el contrato de esta HU, publicado ANTES de los controladores (regla 1 de plataforma).
- El contrato de reserva de creditos que este servicio consume es de `ms-finanzas` (Juan Diego). Se le paso por aparte un borrador escrito desde el consumidor; cuando lo apruebe, se publica en `contracts/openapi/` y se genera el cliente a partir de el.

## Levantar en local

```bash
docker compose up -d          # PostgreSQL 17 en el puerto 5435
cd ../../..                   # la raiz del monorepo
DB_PASSWORD=subastas_password ./gradlew :services:cuentas:ms-subastas:bootRun
```

Arranca con el doble en memoria de creditos (`app.finanzas.modo=fake`) y lo advierte en el log: ningun credito se mueve de verdad.

## Pendiente (bloqueado por otros, o fuera de este paquete)

- **Controladores REST**: la validacion del token ya esta resuelta (`seguridad/ValidadorDeToken`, descrito arriba), pero falta **traducir el apodo del token a un `UUID jugadorId`**. ms-identidad pone el apodo como sujeto del JWT, no un identificador estable, y todo el dominio de subastas trabaja con UUID. Hoy no hay forma de hacer esa traduccion sin preguntarle a ms-identidad por HTTP — y esa llamada caeria dentro del lock pesimista de la subasta, el mismo riesgo que ya se cuida con ms-finanzas. Opciones a acordar con el equipo: que el token lleve tambien el UUID (cambio en ms-identidad, lo mas limpio), o un endpoint de resolucion apodo-a-UUID que se consulte **antes** de abrir la transaccion. Lo que NO se va a hacer es copiar el `X-User-Id` de ms-ecommerce: en endpoints que mueven creditos, cualquiera podria pujar en nombre de otro jugador.
- `CreditoClientHttp` real contra el endpoint de ms-finanzas (Juan Diego, Dia 1-2). Nota: la regla del proyecto pide que el doble se **genere desde el contrato** (Pact), no escrito a mano — `CreditoClientFake` es un andamio temporal hasta que exista ese contrato.
- Evento `SubastaCerrada` hacia notificaciones (plataforma) e inventario (contenido) — ninguno de los dos esta en el Sprint 2.
- Vista de detalle de subasta en el front: prototipo interactivo y navegable completo disponible en `diseno-front/subasta-detalle.html` (cubre listado de múltiples subastas simultáneas en curso, panel de puja con atajos en un toque, compra inmediata, pujas automáticas, aviso de superación y panel de cierre/resultado).
- Acordar con Edwin quien dispara el cierre por vencimiento. `CierreDeSubastasVencidasJob` ya lo hace cada 30 s, pero el contador de la subasta lo inicia HU-SUB-001: si esa historia monta su propio disparador, quedarian los dos corriendo sobre las mismas filas. El lock pesimista evita que se pisen, pero es trabajo duplicado y conviene que solo uno lo haga.

## Asunciones tomadas (a validar con el cliente)

El backlog solo deja una pregunta abierta (el valor por defecto del incremento minimo, RF-SUB-004), pero al implementar aparecieron tres mas. Se resolvieron con el criterio mas literal y quedan marcadas en el codigo:

1. **Guerra entre pujas automaticas:** se resuelve de forma *iterativa* (cada una ofrece oferta vigente + incremento, por turnos, respetando los 5 s). La alternativa estilo eBay —saltar de una al limite del segundo mayor— converge en un paso, pero el criterio habla de "emitir ofertas respetando el intervalo minimo". Con 5 s de intervalo, el modo iterativo puede tardar minutos en converger.
2. **Saldo de la puja automatica:** se valida al *configurar*, no al emitir, para avisar al jugador en el momento en vez de que su puja automatica falle en silencio despues.
3. **Anti-sniping:** no implementado, porque la HU no lo menciona. Sin extension de tiempo, una puja manual en el ultimo segundo es inalcanzable para el motor automatico, que debe esperar su intervalo de 5 s.
4. **Validacion de JWT sin verificar revocacion** (acordado con Cristian, HU-SUB-011): ms-subastas valida solo firma y expiracion del token. Lo que NO valida es el claim `ver` del JWT (version de rol del usuario, campo `versionToken` en la entidad `Usuario` de ms-identidad): `JwtService.esVersionVigente(...)` ya compara esa version contra la actual en base de datos, pero ese metodo no se puede llamar desde aqui porque vive en otro dominio y ArchUnit prohibe importarlo. **Riesgo aceptado:** si a un jugador le revocan el rol, su token sigue siendo valido en ms-subastas hasta que expire — hasta 24 h (`app.jwt.horas-expiracion`, el mismo valor en los perfiles default, dev y prod de ms-identidad). Tolerable este sprint porque no se mueve dinero real entre jugadores todavia; revisar si sigue siendo aceptable cuando se integre el endpoint real de creditos. Diseno de v2 (no implementado): cachear la version vigente en Redis con patron *stale-while-revalidate* (nunca bloquear la puja esperando el refresco) e invalidar el cache por evento de RabbitMQ cuando ms-identidad cambie la version, mas un TTL maximo de respaldo por si el evento se pierde. Depende de que exista infraestructura de RabbitMQ real — hoy `contracts/eventos/` esta vacio, sin un solo publicador o consumidor en el repo — asi que no se construye sin coordinar con quien lleve el catalogo de eventos de Sprint 2.

## Correr las pruebas

Desde la raiz del monorepo, porque este servicio es un modulo del build raiz
(no trae su propio wrapper: lo prohibe `guardia-monorepo.yml`):

```bash
./gradlew :services:cuentas:ms-subastas:test    # solo pruebas
./gradlew :services:cuentas:ms-subastas:check   # pruebas + compuerta del 80 %
./gradlew :services:cuentas:ms-subastas:test --tests '*MotorPujasServiceTest'
```

La integracion continua la cubre `ci.yml`, que detecta los servicios
modificados y los compila con su herramienta. Este servicio no lleva workflow
propio.
