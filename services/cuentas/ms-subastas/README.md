# ms-subastas

**Sprint:** 2 · **Modulo:** M12 — Subasta · **Historias:** HU-SUB-001 (Edwin), HU-SUB-011 (Cristian), HU-SUB-004 (Andres)

Mercado de subastas entre jugadores: publicar un producto, listarlos con filtros en vivo, y pujar o comprar de forma inmediata.

## Estado de esta carpeta (HU-SUB-004)

Lo que ya esta desarrollado, de lo que no depende de nadie mas del equipo:

- `pujas/model` — `Puja`, `PujaAutomatica` y sus enums.
- `pujas/creditos` — contrato `CreditoClient` hacia ms-finanzas (reservar/liberar/consumir/saldo) + `CreditoClientFake` en memoria para desarrollar y testear sin depender del endpoint real.
- `pujas/service/MotorPujasService` — las 5 reglas de negocio de pujar (supera oferta + incremento minimo, intervalo de 5 s, prohibido pujar en la propia subasta, topes de 10 subastas / 50 pujas activas) y la compra inmediata.
- `pujas/service/MotorPujaAutomaticaService` — configurar una puja automatica (validando saldo y que el limite sea alcanzable), elegir cual de varias responde (la de mayor limite), cuanto ofrecer, y desde cuando puede volver a emitir sin violar el intervalo de 5 s.
- `pujas/service/ParametrosPuja` — los 4 limites configurables (`app.pujas.*`).
- `pujas/service/PujaApplicationService` — orquesta la transaccion: toma el lock pesimista de la subasta, arma el contexto de participacion desde la base de datos y delega las reglas en el motor.
- `subastas/repository` y `pujas/repository` — repositorios JPA, con `findByIdParaActualizar` (SELECT ... FOR UPDATE) como guardia real de concurrencia.
- `subastas/model/Subasta` — **borrador** pendiente del diseno conjunto del Dia 1 con Edwin y Cristian; puede cambiar.
- `db/migration/V1__create_subastas_pujas.sql` — esquema por Flyway (nunca `ddl-auto`), con los indices que sostienen las reglas: unico parcial de una sola puja vigente por subasta, `(jugador_id, creada_en DESC)` para el intervalo de 5 s, y parcial por jugador para los topes de 10/50.
- `pujas/creditos/CreditoClientResiliente` — cortacircuitos y reintento (Resilience4j) sobre la llamada a ms-finanzas, que ocurre dentro del lock de la subasta. Los rechazos de negocio estan excluidos del reintento: saldo insuficiente no es un fallo del servicio.
- Pruebas: 49 en verde. Unitarias de las 5 reglas, `MotorPujasServiceConcurrenciaTest` en memoria, y `PujaConcurrenciaPostgresTest` contra PostgreSQL real (Testcontainers) que si ejercita el lock pesimista y el indice unico parcial. Compuerta JaCoCo al 80% que rompe el build, y `ReglasDeArquitecturaTest` (ArchUnit) que falla si alguien cruza dominios, mete persistencia en el motor o usa `Instant.now()`.

## Contratos

- `contracts/openapi/ms-subastas-pujas.yaml` — el contrato de esta HU, publicado ANTES de los controladores (regla 1 de plataforma).
- `contracts/openapi/ms-finanzas-creditos.propuesta.yaml` — borrador escrito desde el consumidor de lo que se necesita de ms-finanzas. **No es el contrato vigente**: el dueno es Juan Diego y requiere su aprobacion.

## Levantar en local

```bash
docker compose up -d          # PostgreSQL 17 en el puerto 5435
DB_PASSWORD=subastas_password ./mvnw spring-boot:run
```

Arranca con el doble en memoria de creditos (`app.finanzas.modo=fake`) y lo advierte en el log: ningun credito se mueve de verdad.

## Pendiente (bloqueado por otros, o fuera de este paquete)

- **Controladores REST**: bloqueados por una decision de autenticacion. Copiar el `X-User-Id` de ms-ecommerce seria una vulnerabilidad aqui (cualquiera podria pujar en nombre de otro jugador y moverle los creditos). El interceptor JWT de ms-identidad no se puede importar: depende de sus entidades internas y ArchUnit prohibe cruzar dominios. Hace falta decidir si se extrae un validador de JWT a `shared/libs/` o si ms-subastas valida el token por su cuenta.
- `CreditoClientHttp` real contra el endpoint de ms-finanzas (Juan Diego, Dia 1-2). Nota: la regla del proyecto pide que el doble se **genere desde el contrato** (Pact), no escrito a mano — `CreditoClientFake` es un andamio temporal hasta que exista ese contrato.
- Evento `SubastaCerrada` hacia notificaciones (plataforma) e inventario (contenido) — ninguno de los dos esta en el Sprint 2.
- Controladores REST y la vista de detalle de subasta en el front.
- Planificador que emite las pujas automaticas: `MotorPujaAutomaticaService` ya decide *que* ofrecer y *desde cuando*, pero falta el listener de `PujaRealizada` que lo dispare. Depende del catalogo de eventos (`contracts/eventos/`).
- Job que cierra las subastas vencidas: la restitucion de creditos ya esta (`cerrarPorVencimiento`), falta acordar con Edwin quien dispara el cierre — el contador de la subasta lo inicia HU-SUB-001.

## Asunciones tomadas (a validar con el cliente)

El backlog solo deja una pregunta abierta (el valor por defecto del incremento minimo, RF-SUB-004), pero al implementar aparecieron tres mas. Se resolvieron con el criterio mas literal y quedan marcadas en el codigo:

1. **Guerra entre pujas automaticas:** se resuelve de forma *iterativa* (cada una ofrece oferta vigente + incremento, por turnos, respetando los 5 s). La alternativa estilo eBay —saltar de una al limite del segundo mayor— converge en un paso, pero el criterio habla de "emitir ofertas respetando el intervalo minimo". Con 5 s de intervalo, el modo iterativo puede tardar minutos en converger.
2. **Saldo de la puja automatica:** se valida al *configurar*, no al emitir, para avisar al jugador en el momento en vez de que su puja automatica falle en silencio despues.
3. **Anti-sniping:** no implementado, porque la HU no lo menciona. Sin extension de tiempo, una puja manual en el ultimo segundo es inalcanzable para el motor automatico, que debe esperar su intervalo de 5 s.

## Correr las pruebas

```bash
./mvnw test
```
