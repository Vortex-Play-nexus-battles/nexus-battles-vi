# ms-finanzas

Microservicio de pagos, créditos y monetización del dominio Cuentas y Economía
(equipo grupo-4). Módulo M07.

## Estado

**Implementado y probado de punta a punta — ya no es un esqueleto.** El libro de
créditos funciona: **HU-JUE-014** (apuesta de créditos: reserva por participante,
liberación y consolidación) y **HU-JUE-012** (recompensa por partida: acreditación
2/4/1 y cofres, idempotente por `refId`) están implementadas, con su contrato
publicado en `contracts/openapi/creditos.yaml` (1.4.0) y pruebas E2E que corren
contra este servicio de verdad, no contra un doble:

- `tests/e2e/apuesta-de-creditos.e2e.spec.js`
- `tests/e2e/recompensa-por-partida.e2e.spec.js`

**No está desplegado en AWS.** No tiene puerto asignado en `puerto_de()` de
`.github/workflows/cd.yml`, así que el flujo de despliegue lo omite: ni construye
su imagen ni lo lleva al host. La razón es capacidad —no cabe en el `t3.small` de
plataforma junto con lo que ya corre (#430)— y es una decisión documentada, no un
olvido; ver `docs/arquitectura/README.md`. Consecuencia visible en DEV: toda sala
o torneo con recompensa/costo `> 0` responde `503` y no reserva nada.

Donde sí corre de verdad es en el **banco E2E** (`tests/e2e/compose.yml`), que es
donde se demuestra la apuesta liquidada. Sus pruebas unitarias y su compuerta de
calidad corren en `ci.yml` como las de cualquier otro servicio.

Puerto 8093 (host y contenedor).

## HU objetivo del Sprint 2

| HU | Nombre | Owner |
|---|---|---|
| HU-PAG-001 | Integración con pasarela de pagos simulada | Juan Diego |
| HU-PAG-002 | Registro de transacciones en moneda real | Santiago |
| HU-JUE-012 | Economía de créditos por partidas | Santiago |

## Endpoints previstos (contrato con ms-subastas y ms-ecommerce)

Base: `http://<host>:8093/api/v1`

```
POST   /creditos/debitar          Cobra N créditos (idempotente por refId).
POST   /creditos/reservar         Reserva N créditos (bloqueo temporal).
POST   /creditos/liberar          Libera una reserva previa.
POST   /creditos/consolidar       Convierte una reserva en débito real.
GET    /creditos/{uid}/saldo      Consulta saldo de un usuario.
POST   /pagos/procesar            Pasarela simulada de pagos en moneda real.
GET    /pagos/{refId}             Estado de una transacción.
```

**Quién puede llamar (desde #455, 21-sep-2026):** todo `/creditos/**` y
`/partidas/**` exige un **token de servicio** (`rol=SERVICIO`, ADR-005:
`client_credentials` contra ms-identidad). Un jugador con su propio token
recibe 403, aunque el `uid` del cuerpo sea el suyo. La única excepción es
`GET /creditos/{uid}/saldo`, que además admite al propio usuario (y solo el
suyo). `/transacciones/**` y `/cofres/**` son del usuario autenticado; un
servicio no tiene historial. Las reglas y sus pruebas negativas de
suplantación están en `seguridad/SecurityConfig` y `SecurityConfigTest`
(tokens reales firmados y verificados contra un JWKS). Contrato:
`contracts/openapi/creditos.yaml` 1.1.0.

ms-subastas ya declara el cliente hacia este servicio en el instance
`creditos` de Resilience4j (ver su `application.properties`). El SLA de
latencia de los endpoints de `/creditos/*` se acuerda con Andrés (HU-SUB-004)
porque los invoca dentro del lock pesimista de la puja.

## Levantar en local

```bash
# 1. Levantar la base de datos aislada del servicio (puerto 5436).
docker compose -f services/cuentas/ms-finanzas/docker-compose.yml up -d

# 2. Compilar y arrancar el microservicio.
./gradlew :services:cuentas:ms-finanzas:bootRun
```

Variables de entorno con valor por defecto: `DB_HOST=localhost`, `DB_PORT=5436`,
`DB_NAME=finanzas_db`, `DB_USER=finanzas_user`. `DB_PASSWORD` es obligatoria
(sin default por seguridad); el `docker-compose.yml` local usa
`finanzas_password` si no se define.

## Comandos

Siempre desde la **raíz del monorepo** (regla del `guardia-monorepo.yml`: los
servicios no traen wrapper propio de Gradle).

```bash
./gradlew :services:cuentas:ms-finanzas:build      # compila + tests
./gradlew :services:cuentas:ms-finanzas:test       # solo tests
./gradlew :services:cuentas:ms-finanzas:bootRun    # arranca el servicio
```
