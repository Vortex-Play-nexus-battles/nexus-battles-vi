# ms-subastas

**Sprint:** 2 · **Modulo:** M12 — Subasta · **Historias:** HU-SUB-001 (Edwin), HU-SUB-011 (Cristian), HU-SUB-004 (Andres)

Mercado de subastas entre jugadores: publicar un producto, listarlos con filtros en vivo, y pujar o comprar de forma inmediata.

## Estado de esta carpeta (HU-SUB-004)

Lo que ya esta desarrollado, de lo que no depende de nadie mas del equipo:

- `pujas/model` — `Puja`, `PujaAutomatica` y sus enums.
- `pujas/creditos` — contrato `CreditoClient` hacia ms-finanzas (reservar/liberar/consumir/saldo) + `CreditoClientFake` en memoria para desarrollar y testear sin depender del endpoint real.
- `pujas/service/MotorPujasService` — las 5 reglas de negocio de pujar (supera oferta + incremento minimo, intervalo de 5 s, prohibido pujar en la propia subasta, topes de 10 subastas / 50 pujas activas) y la compra inmediata.
- `pujas/service/MotorPujaAutomaticaService` — decide cuanto ofrecer o si debe detenerse al llegar al limite configurado.
- `pujas/service/ParametrosPuja` — los 4 limites configurables (`app.pujas.*`).
- `pujas/service/PujaApplicationService` — orquesta la transaccion: toma el lock pesimista de la subasta, arma el contexto de participacion desde la base de datos y delega las reglas en el motor.
- `subastas/repository` y `pujas/repository` — repositorios JPA, con `findByIdParaActualizar` (SELECT ... FOR UPDATE) como guardia real de concurrencia.
- `subastas/model/Subasta` — **borrador** pendiente del diseno conjunto del Dia 1 con Edwin y Cristian; puede cambiar.
- `db/migration/V1__create_subastas_pujas.sql` — esquema por Flyway (nunca `ddl-auto`), con los indices que sostienen las reglas: unico parcial de una sola puja vigente por subasta, `(jugador_id, creada_en DESC)` para el intervalo de 5 s, y parcial por jugador para los topes de 10/50.
- Pruebas unitarias de las 5 reglas + prueba de concurrencia (`MotorPujasServiceConcurrenciaTest`) que reproduce el caso pedido por el backlog: varios jugadores pujando en la misma fraccion de segundo, solo uno gana. Compuerta JaCoCo al 80% que rompe el build.

## Pendiente (bloqueado por otros, o fuera de este paquete)

- **Controladores REST**: bloqueados por una decision de autenticacion. Copiar el `X-User-Id` de ms-ecommerce seria una vulnerabilidad aqui (cualquiera podria pujar en nombre de otro jugador y moverle los creditos). El interceptor JWT de ms-identidad no se puede importar: depende de sus entidades internas y ArchUnit prohibe cruzar dominios. Hace falta decidir si se extrae un validador de JWT a `shared/libs/` o si ms-subastas valida el token por su cuenta.
- `CreditoClientHttp` real contra el endpoint de ms-finanzas (Juan Diego, Dia 1-2). Nota: la regla del proyecto pide que el doble se **genere desde el contrato** (Pact), no escrito a mano — `CreditoClientFake` es un andamio temporal hasta que exista ese contrato.
- Evento `SubastaCerrada` hacia notificaciones (plataforma) e inventario (contenido) — ninguno de los dos esta en el Sprint 2.
- Controladores REST y la vista de detalle de subasta en el front.
- Valor por defecto del incremento minimo: pregunta abierta al cliente (RF-SUB-004).

## Correr las pruebas

```bash
./mvnw test
```
