# torneos (M13)

**Grupo responsable:** Grupo 6 · Tiempo real, comunidad y plataforma
**Requisitos:** RF-TOR-001..005, RF-ADM-005 (intervención sobre un encuentro). Pendientes del PO: RF-TOR-006 (transmisión, INC-18) y RF-TOR-007 (premiación) — ver D-24.
**Contrato:** `contracts/openapi/torneos.yaml` 1.0.0
**Stack:** Java 21 · Spring Boot 4.1 · PostgreSQL (esquema `torneos`, Flyway) · resource server de ms-identidad · puerto 8083

## Qué hace

Un torneo cada 91 días (Charter), ocho cupos, equipos de dos jugadores (nombre y avatar
por la lista negra), inscripción con reserva de créditos en ms-finanzas (se cobra al
iniciar, se devuelve al cancelar), relleno con equipos de la máquina, árbol de doble
eliminación con la numeración de la ficha (ganadores 1-6, 11 y final; secundarios
7-10, 12 y 13). El resultado de un encuentro lo aporta un servicio (la partida,
`ROLE_SERVICIO`) o un administrador con motivo.

## Dependencias por API (regla 7: nunca la base de otro)

| Variable | Qué | Sin ella |
|---|---|---|
| `CREDITOS_URL` | ms-finanzas (`creditos.yaml`) | inscribir con costo > 0 → 503 `LIBRO_NO_DISPONIBLE` |
| `LISTA_NEGRA_VERIFICAR_URL` | moderacion-sanciones | registrar equipo → 503 `LISTA_NEGRA_NO_DISPONIBLE` |
| `SANCIONES_URL` | moderacion-sanciones (consulta 1.1.0) | inscribir → 503 `SANCIONES_NO_DISPONIBLES` |
| `DIRECTORIO_ACTIVO_*` | credencial de servicio (ADR-005) | el libro rechaza (401) |

## Decisiones registradas

D-21 (rol administrador de torneo), D-22 (encuentro 2 contra 2), D-23 (valor de
inscripción y quién paga), D-24 (premiación y transmisión pendientes) en
`docs/gobierno/DECISIONES-PENDIENTES-DEL-PO.md`.

## Pruebas

`./gradlew :services:plataforma:torneos:check` — `ArbolTest`, `TorneosServiceIT`
(PostgreSQL real, reloj movible), `TorneosControllerTest`, `ClientesHttpTest`.
E2E: `tests/e2e/torneos.e2e.spec.js`. Vista: `frontend/app-web/src/plataforma/torneos/`.
