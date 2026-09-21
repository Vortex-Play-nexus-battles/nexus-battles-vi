# admin-parametros

**Grupo responsable:** Grupo 6 · Tiempo real, comunidad y plataforma
**Requisitos:** RF-ADM-001 (HU-ADM-001 #481)
**Contrato:** `contracts/openapi/admin-parametros.yaml` 1.0.0
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
`metricas.umbral-sanciones-por-dia`, `torneos.costo-inscripcion-por-defecto`,
`chat.*`, `salas.apuestas.si-gana-la-maquina`, `subastas.*` (los que ms-subastas
declara configurables desde aquí). Inalterables: 91 días entre torneos, 8 cupos, 2
por equipo, créditos 2/4/1, 6 por batalla, 75 % CPU, 500 ms, 99,95 %.

## Consumidores conectados

- `moderacion-sanciones` (`PARAMETROS_URL`): rango de la suspensión y plazo de apelación,
  con caché corta y las variables de entorno como respaldo.

Pendientes de conectar (ya tienen parámetro): salas-partidas (D-02, D-16), torneos
(D-23), metricas-plataforma (D-25).

## Auditoría

Cada cambio queda en `versiones` y se envía a ms-cumplimiento
(`AUDITORIA_URL` → `POST /admin/auditoria/eventos`, credencial `DIRECTORIO_ACTIVO_*`),
fail-open con bitácora. Sin `AUDITORIA_URL`, solo historial local.

## Pruebas

`./gradlew :services:plataforma:admin-parametros:check` — `ParametroTest`,
`ParametrosServiceIT` (catálogo real), `ParametrosControllerTest`, `ClienteAuditoriaTest`.
E2E: `tests/e2e/parametros.e2e.spec.js`. Vista: `frontend/app-web/src/plataforma/admin-parametros/`.
