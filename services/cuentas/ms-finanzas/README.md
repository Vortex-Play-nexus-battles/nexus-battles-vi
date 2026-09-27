# ms-finanzas

Microservicio de pagos, créditos y monetización del dominio Cuentas y Economía
(equipo grupo-4). Módulo M07.

## Estado

**Implementado y probado de punta a punta — ya no es un esqueleto.** El libro de
créditos funciona: **HU-JUE-014** (apuesta de créditos: reserva por participante,
liberación y consolidación) y **HU-JUE-012** (recompensa por partida: acreditación
2/4/1 y cofres, idempotente por `refId`) están implementadas, con su contrato
publicado en `contracts/openapi/creditos.yaml` (1.4.1) y `cofres.yaml` (1.1.0),
y pruebas E2E que corren contra este servicio de verdad, no contra un doble:

- `tests/e2e/apuesta-de-creditos.e2e.spec.js`
- `tests/e2e/recompensa-por-partida.e2e.spec.js`

**Despliegue en DEV.** Hasta el 23-sep quedó fuera de DEV por capacidad (#430).
Desde R16.5 corre en el host de plataforma: `infrastructure/despliegue/servicios.json`
lo marca `desplegableDev: true` y su override es `docker-compose.ms-finanzas.yml`
(base propia `finanzas-db`, credencial de servicio `ms-finanzas`; ver
`infrastructure/despliegue/CAPACIDAD.md`).

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

## Cofres (B7, `cofres.yaml` 1.1.0)

Lo que fija el documento (§7.6) está en `partidas/ReglasDeCofres` y **no se
configura**: un cofre por cada **20 créditos ganados** y **dos cofres por
semana** como máximo.

| Regla | Dónde | Prueba |
|---|---|---|
| Solo cuentan los créditos **ganados** (los 2 del ganador), no el crédito de participar | `CofreService`, `AcreditacionPartidaService` | `CofreServiceTest`, `CofresIT.participarNoCuenta` |
| Cada 20 ganados, un cofre; el contador vuelve a empezar | `ContadorDeCofres` | `CofreServiceTest` |
| Máximo dos por semana ISO, contada en una zona explícita | `ReglasDeCofres`, `ContadorDeCofres` | `CofreServiceTest` (cambio de semana en la zona) |
| El contenido se sortea con una semilla guardada (reproducible) | `TablaDeCofre`, `CofreEntregado` (`semilla`, `tablaVersion`) | `TablaDeCofreTest` |
| Dos victorias simultáneas no pierden créditos ni duplican cofres | `@Version` en `ContadorDeCofres` + `RegistroDeResultados` (reintento) | `CofresIT.victoriasSimultaneas`, `RegistroDeResultadosTest` |
| El contenido llega al inventario por `POST /inventario/entregas`, origen `COFRE`, `Idempotency-Key: cofre-{id}`, **después de confirmar** y fuera del hilo de la petición | `EntregaDeCofres`, `ClienteInventarioDeCofres` | `EntregaDeCofresTest`, `ClienteInventarioDeCofresTest`, `CofresIT` |
| Lo que no entra queda `PENDIENTE` y se reintenta con espera creciente y tope | `EntregaDeCofres.reintentarPendientes` | `EntregaDeCofresTest.reintento`, `CofresIT.entregaPendienteYReintento` |
| "Mis cofres" muestra premios, estado de la entrega, semana y sorteo | `ResumenCofre`, `GET /cofres/mios` | `MisCofresControllerTest`, `CofresIT` |

**Lo que el documento no fija** es parámetro con decisión pendiente del PO
(`docs/gobierno/DECISIONES-PENDIENTES-DEL-PO.md`):

- **D-B7-17** — zona de la semana ISO (`FINANZAS_COFRES_ZONA`, `America/Bogota`)
  y si el sobrante de 20 cuenta para el siguiente cofre
  (`FINANZAS_COFRES_CONSERVAR_SOBRANTE`, `true`).
- **D-B7-18** — el contenido del cofre. Hoy rige una **tabla PROVISIONAL DE
  DESARROLLO** (`src/main/resources/cofres/tabla-provisional-dev-1.txt`, versión
  `PROVISIONAL-DEV-1`): el servicio lo avisa al arrancar y cada cofre guarda la
  versión con la que se sorteó. La definitiva entra por `FINANZAS_COFRES_TABLA`
  (`productoId=peso;...`) con su propia `FINANZAS_COFRES_TABLA_VERSION`.

El contador semanal anterior (que mezclaba créditos de participar) no se migra:
los contadores de B7 empiezan en cero (`V5__cofres_aleatorios.sql`).

**Entrega al inventario.** Necesita `INVENTARIO_BASE_URL` y la credencial de
servicio de ms-finanzas (`DIRECTORIO_ACTIVO_URL`, `DIRECTORIO_ACTIVO_CLIENT_ID`,
`DIRECTORIO_ACTIVO_CLIENT_SECRET`; el cliente `ms-finanzas` tiene que estar dado
de alta en `AUTH_CLIENTES_SERVICIO` de ms-identidad). Sin URL, o mientras
inventario no publique `POST /inventario/entregas` (B4), los cofres se ganan y
quedan `PENDIENTE`; el reintento los entrega en cuanto se pueda, con la misma
clave de idempotencia. Afinado: `FINANZAS_COFRES_REINTENTO_MS` (60000),
`FINANZAS_COFRES_ESPERA_MAXIMA_MINUTOS` (30), `FINANZAS_TIEMPO_CONEXION_MS`
(2000), `FINANZAS_TIEMPO_RESPUESTA_MS` (5000).

**Apuestas (`creditos.yaml` 1.4.1).** Al consumir una reserva a favor de un
beneficiario (el ganador de la apuesta), queda también un movimiento `CREDITO`
a su nombre, con la referencia de la reserva y la clave `consumo-{reservaId}`:
el ganador ve su ingreso en `GET /creditos/{uid}/movimientos`, una sola vez
aunque el consumo se repita.

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
