# admin-parametros

**Grupo responsable:** Grupo 6 · Tiempo real, comunidad y plataforma
**Requisitos:** RF-ADM-001 (HU-ADM-001 #481) · §7.3.7 cambios en las políticas de uso (SA-11, RF-NOT-005)
**Contrato:** `contracts/openapi/admin-parametros.yaml` 1.1.0
**Stack:** Java 21 · Spring Boot 4.1 · PostgreSQL (esquema `admin_parametros`, Flyway) · resource server de ms-identidad · puerto 8088

## Qué hace

Catálogo de parámetros operativos con tipo, rango u opciones, valor vigente, cambio
programado (`vigenteDesde`), versión e historial. Cambiar es de ADMINISTRADOR /
SUPER_ADMINISTRADOR con motivo; leer es público dentro de la red
(`GET /parametros/{clave}/valor` para los servicios). Los valores fijados por el
Project Charter están en el catálogo como **inalterables** (409 al editarlos). Un
parámetro con `valor: null` es una decisión que el PO no ha tomado.

## Catálogo (V1)

Editables: `sanciones.suspension.*`, `sanciones.apelacion.plazo-dias`,
`metricas.umbral-sanciones-por-dia` y `metricas.umbral-reportes-por-dia` (V6),
`torneos.costo-inscripcion-por-defecto`,
`chat.*`, `salas.apuestas.si-gana-la-maquina`, `subastas.*` (los que ms-subastas
declara configurables desde aquí). Inalterables: 91 días entre torneos, 8 cupos, 2
por equipo, créditos 2/4/1, 6 por batalla, 75 % CPU, 500 ms, 99,95 %.

## Cambios en las políticas de uso (SA-11, §7.3.7)

`/parametros/politicas/cambios`: la administración (ADMINISTRADOR / SUPER_ADMINISTRADOR)
publica un cambio en los términos y condiciones del servicio o en la política de
privacidad (las dos que nombran las causales de §7.3.2) con título, lo que cambia, desde
cuándo rige y motivo (`V8`, tabla `cambios_de_politica`). Leer es público y no enseña quién
publicó, el motivo ni la IP. Notificaciones lee `/posteriores?desde=` y lo lleva a la bandeja
de cada jugador (`CAMBIO_POLITICA`). Cada publicación llega a la bitácora central con la
misma cola que los cambios de parámetros (`CREACION` de `politica:<POLITICA>`, referencia
`admin-parametros:politica-<id>`).

## Consumidores conectados

- `moderacion-sanciones` (`PARAMETROS_URL`): rango de la suspensión y plazo de apelación,
  con caché corta y las variables de entorno como respaldo.
- `metricas-plataforma` (`PARAMETROS_URL`, SA-4): los umbrales de alta frecuencia del tablero de
  moderación, `metricas.umbral-sanciones-por-dia` (3, decisión del PO que escribe `V6`) y
  `metricas.umbral-reportes-por-dia` (sin cifra del PO), con caché de 30 s.
- `notificaciones` (`PARAMETROS_URL`, SA-11): los cambios en las políticas de uso, al entregar
  lo pendiente a cada sesión.

Pendientes de conectar (ya tienen parámetro): salas-partidas (D-02, D-16), torneos
(D-23).

## Auditoría

Cada cambio queda en `versiones` y se envía a ms-cumplimiento
(`AUDITORIA_URL` → `POST /admin/auditoria/eventos`, credencial `DIRECTORIO_ACTIVO_*`),
fail-open con bitácora. Sin `AUDITORIA_URL`, solo historial local.

## Pruebas

`./gradlew :services:plataforma:admin-parametros:check` — `ParametroTest`,
`ParametrosServiceIT` (catálogo real), `ParametrosControllerTest`, `ClienteAuditoriaTest`;
SA-11: `PoliticasServiceTest`, `PoliticasControllerTest`, `EnvioDeAuditoriaDePoliticasTest`,
`CambiosDePoliticaIT` (V8 sobre PostgreSQL).
E2E: `tests/e2e/parametros.e2e.spec.js`, `tests/e2e/politicas-de-uso.e2e.spec.js`. Vista: `frontend/app-web/src/plataforma/admin-parametros/`.
