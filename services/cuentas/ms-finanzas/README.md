# ms-finanzas

Microservicio de pagos, créditos y monetización del dominio Cuentas y Economía
(equipo grupo-4). Módulo M07.

## Estado

Skeleton — Sprint 2, rama `chore/ms-finanzas-skeleton-gradle`. Sin HU
implementadas todavía. Puerto 8093 (host y contenedor).

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
