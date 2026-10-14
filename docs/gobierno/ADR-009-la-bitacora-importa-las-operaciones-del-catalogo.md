# ADR-009 — La bitácora central importa las operaciones del catálogo

- **Estado:** aceptada
- **Fecha:** 14 de octubre de 2026
- **Ámbito:** `services/contenido/productos`, `services/cuentas/ms-cumplimiento`, `scripts/cd/desplegar.sh`
- **Relacionada con:** §7.2.5 y §7.3.5 del documento del curso, ADR-001, ADR-005, ADR-007, SA-5 (#968), SA-6, SA-18
- **Clasificación:** REQUISITO PDF (§7.2.5, §7.3.5) · DECISIÓN TÉCNICA (cómo llegan a la bitácora)

## Contexto

§7.2.5 pide para el módulo de productos un «registro de auditoría de todas las
operaciones realizadas», y §7.3.5 una bitácora de **todas** las acciones
administrativas con su fecha y hora exacta, quién la hizo, el tipo, lo
afectado, los valores anteriores y nuevos, el motivo y la IP; inmutable, solo
para Super Administradores, exportable y conservada cinco años (ADR-007).

Lo que había en `develop`:

- La bitácora central es `audit_log` de ms-cumplimiento (host de
  plataforma). Sanciones, comentarios, parámetros e identidad le **envían** sus
  eventos por `POST /api/v1/admin/auditoria/eventos` con su credencial de
  servicio (ADR-005).
- Productos (host de contenido) guarda el historial de cada producto
  (`productos_historico`, con autor y motivo desde SA-6) y quién subió o retiró
  cada recurso gráfico, pero **nada de eso llegaba a la bitácora central**: el
  Super Administrador no veía ninguna acción sobre el catálogo. Los banners no
  guardaban ni quién los publicó ni ningún historial. Nada guardaba la IP.

## Opciones

1. **Productos envía sus eventos a la bitácora**, como los demás. Productos
   necesitaría una credencial de servicio que el emisor (ms-identidad, host de
   plataforma) reconozca. `desplegar.sh` genera cada credencial en el host
   donde corre su servicio: para un cliente en el host de contenido el valor
   tiene que venir de un *secret* de GitHub compartido por los dos hosts (así
   funcionan `SECRETO_SERVICIO_MISIONES` y `SECRETO_SERVICIO_MS_SUBASTAS`).
   Crear ese secret es una acción humana sobre la configuración del
   repositorio: hasta que alguien la hiciera, todo quedaría en una cola sin
   entregar en DEV.
2. **La bitácora lee las operaciones del catálogo** por una ruta de productos
   solo para servicios, como notificaciones ya lee los cambios del catálogo
   (`GET /api/v1/productos/alertas/cambios`). ms-cumplimiento corre en el mismo
   host que el emisor: su credencial la genera `desplegar.sh` allí, sin secret
   en GitHub. Desde ese host ya se llega a productos (comentarios y
   notificaciones lo hacen).

## Decisión

Se toma la **opción 2**.

- **Productos registra cada operación administrativa** en
  `operaciones_administrativas` (MongoDB, solo se inserta): alta, modificación,
  cambio de balance, importación de diseño, suspensión, reactivación y
  reversión de un producto; alta y retiro de un recurso gráfico; publicación,
  edición y retiro de un banner. Cada una con su momento, el `uid` de quien la
  hizo (el del token), el tipo, lo afectado y su nombre en ese momento, los
  campos que cambiaron con su valor antes y después, el motivo (si la operación
  lo pide) y la IP que fija el borde (`X-Real-IP`). Se escribe **después** de
  aplicar la operación: nunca queda registrada una que no ocurrió. Lo que no
  hace una persona (la semilla del catálogo) no se registra.
- **`GET /api/v1/productos/auditoria/operaciones`** (productos.yaml 1.12.0) las
  entrega por lotes. Solo con token de servicio y, de los servicios, solo los
  de `productos.auditoria.lectores` (`ms-cumplimiento`): llevan quién actuó y
  desde dónde. Ningún usuario las lee ahí, tampoco el Super Administrador: él
  las lee en la bitácora, donde son inmutables.
- **ms-cumplimiento las importa** cada 30 s (`AUDITORIA_CATALOGO_INTERVALO`)
  con su credencial (`ms-cumplimiento` entra en `CLIENTES_DE_SERVICIO`). Cada
  operación entra una sola vez: su referencia (`productos:<id>`) es única en
  `audit_log` (V3). Cada ronda pide desde la última importada **menos un
  solape** de 2 minutos, para que una operación escrita justo después de una
  lectura, con una marca anterior, no se pierda. Conserva el momento en que
  ocurrió (`ocurridoEn`) y el nombre de lo afectado (`nombre_afectado`, V5).
  Si productos o el emisor no responden, la ronda no escribe nada y la
  siguiente lo vuelve a pedir; la bitácora nunca deja de arrancar por esto.
- **Los banners** guardan `creadoPor` y `modificadoPor`, tienen su historial
  para la administración (`GET /api/v1/banners/{id}/historial`, sin la IP) y su
  retiro pide motivo en la consola.

## Consecuencias

- El Super Administrador ve en el registro de auditoría las acciones sobre el
  catálogo con todo lo que pide §7.3.5, las filtra por tipo
  (`REACTIVACION` y `REVERSION` son nuevos) y las exporta en CSV y PDF.
- Una operación tarda hasta una ronda (30 s en DEV) en aparecer en la
  bitácora central; en el historial del producto o del banner aparece al
  instante.
- Las operaciones anteriores a este cambio siguen en el historial de cada
  producto (con autor y motivo, sin IP), pero **no** se copian a la bitácora
  central: no tienen IP y no se inventa. La bitácora las tiene desde el
  despliegue de SA-18.
- El pacto `contracts/pactos/ms-cumplimiento-productos.json` fija lo que la
  bitácora lee; lo verifica productos (`VerificacionDelPactoDeCumplimientoTest`).
- Ninguna acción humana pendiente: no hace falta ningún secret nuevo.
