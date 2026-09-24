# Host de desarrollo de plataforma en AWS (Free Plan)

Infraestructura del único servidor de `dev` para `services/plataforma/*`, en la
cuenta AWS de la empresa creada el **2026-09-15** con el **Free Plan**.

## Política de la cuenta (no negociable)

| Regla | Por qué |
|---|---|
| Gasto de bolsillo **USD 0**. Nunca se pasa a Paid Plan. | El Free Plan no cobra a la tarjeta; el Paid Plan sí, y no tiene vuelta atrás. |
| Solo recursos cubiertos por el crédito y sin cobro "en reposo" que no esté contado aquí. | Lo que factura con la instancia apagada: IP elástica (3,60 USD/mes) y disco gp3 (0,08 USD/GB-mes). Nada más. |
| Prohibido: NAT Gateway, ALB, RDS, EKS, ElastiCache, OpenSearch, DocumentDB, Marketplace, AWS Organizations / Control Tower. | Facturan por hora aunque no se usen; Organizations además **anula el crédito** y sube la cuenta a Paid. |
| Root sin access keys y con MFA. Ninguna clave de AWS en el repo ni en GitHub. | CI/CD entra por OIDC asumiendo un rol IAM. Personas: usuario IAM propio con MFA. |
| Solo tipos de instancia elegibles del Free Plan. | `t3.micro`, `t3.small`, `t4g.micro`, `t4g.small`, `c7i-flex.large`, `m7i-flex.large`. Otro tipo lo rechaza el plan al lanzar (docs EC2, cuentas desde 2025-07-15). |

Fechas: crédito USD 100 (hasta 200 con actividades) · el plan **cierra la cuenta el
2027-03-15** o al agotar el crédito · el proyecto termina el 2026-11-06.

## Lo que se creó una sola vez a mano (2026-09-15, CloudShell, cuenta `362403299569`)

- Proveedor OIDC `token.actions.githubusercontent.com`.
- Rol `github-actions-nexus-dev`: confía solo en este repositorio. GitHub emite el `sub`
  con los IDs numéricos de organización y repositorio
  (`repo:Vortex-Play-nexus-battles@317725248/nexus-battles-vi@1336530373:*`), así que la
  política de confianza lleva ese patrón y, por si GitHub alterna, también el clásico
  `repo:Vortex-Play-nexus-battles/nexus-battles-vi:*`. Permisos: `PowerUserAccess` + IAM
  mínimo para perfiles de instancia y presupuestos. El paso "Mostrar la identidad OIDC"
  del workflow imprime el `sub` real (nunca el token) para diagnosticar un rechazo.
- Bucket `nexus-battles-vi-tfstate-362403299569` (versionado, sin acceso público) para el estado.
- Variables del repositorio: `AWS_ROLE_ARN`, `AWS_REGION`, `TFSTATE_BUCKET`, `AWS_CORREO_ALERTAS`.

Nada más se crea a mano. Todo lo demás sale de esta carpeta por `infra-dev.yml`.

## Qué crea esta carpeta

| Recurso | Detalle | Costo oficial (us-east-1, 2026-09-15) |
|---|---|---|
| `aws_instance` `nexus-plataforma-dev` | `t3.small` (2 vCPU, 2 GiB, x86) por defecto; Ubuntu 22.04; Docker + Compose v2; swap 2 GB; IMDSv2; créditos de CPU `standard` | 0,0208 USD/h → 15,0 USD/mes 24×7, ~6,2 con apagado nocturno |
| `aws_eip` | IP fija para `DEPLOY_HOST_DEV` | 0,005 USD/h → 3,60 USD/mes, encendida o apagada |
| disco raíz gp3 20 GB | cifrado, se borra con la instancia | 1,60 USD/mes |
| `aws_security_group` | 22 (cd.yml) y 8081-8088 (servicios) | 0 |
| perfil de instancia + `AmazonSSMManagedInstanceCore` | Session Manager: consola sin puerto 22 | 0 |
| parámetro SecureString `/nexus/dev/plataforma/llave-ssh-despliegue` | llave privada del par de despliegue | 0 |
| 2 `aws_budgets_budget` | crédito total (10/25/50/75/90 %) y tope mensual (50/80/100 % + pronóstico) | 0 (dos primeros presupuestos gratis) |
| SNS + EventBridge Scheduler | recordatorios de salida 2026-10-30, 2027-01-14, 2027-02-12 | 0 |

Total 24×7 ≈ **20,2 USD/mes**; con el apagado programado (23:00 → 07:00 y fines
de semana) ≈ **11,5 USD/mes**. Estas cifras son **solo de este host**: desde el
8-sep-2026 hay un segundo `t3.small` para el dominio de contenido
(`../contenido/`), con su propio coste del mismo orden, así que el gasto real de
dev es aproximadamente el doble. Aun así el crédito de USD 100 cubre el proyecto
hasta el 6/nov con el apagado programado en los dos hosts.

> Memoria: 2 GiB corren el perfil de plataforma con `mem_limit` por contenedor,
> pero no dan para el monorepo entero. La salida fue **un segundo `t3.small`**,
> `nexus-contenido-dev`, levantado el 8-sep-2026 para el dominio de contenido
> (mismo costo otra vez, gobernado por `infrastructure/entornos/contenido/`).
> El mapa completo de qué corre en cada host está en
> [`docs/arquitectura/README.md`](../../../docs/arquitectura/README.md).

### El combate sí se puede probar en DEV — desde el 8-sep-2026

El camino completo de combate —acción → `motor-combate` → vida → STOMP → barra—
necesita `motor-combate` **y** `heroes`, que son de Contenido. **Se decidió NO
meterlos en este `t3.small`**, y la razón es la de arriba: ya corre el perfil
completo de plataforma más el borde y dos bases. Dos servicios Java más
(~320 MB cada uno) dejan la instancia al límite, y cuando el OOM killer entra no
elige: puede llevarse por delante `salas-partidas`, que es justo lo que la demo
necesita en pie.

La salida fue la otra: **un segundo host**. `nexus-contenido-dev`
(`t3.small`, EIP `34.193.90.11`) existe desde el 8 de septiembre de 2026 y corre
`heroes` (8101), `inventario` (8102), `productos` (8103) y `motor-combate`
(8104), con su propio MongoDB. El borde de este host enruta hacia allí los
prefijos de contenido, y `salas-partidas` llama a `inventario` y al motor por la
IP elástica. El detalle está en
[`infrastructure/entornos/contenido/README.md`](../contenido/README.md) y el
mapa de los dos hosts en
[`docs/arquitectura/README.md`](../../../docs/arquitectura/README.md).

El 503 `motor-de-combate-no-disponible` por la cola privada del jugador sigue
siendo el comportamiento correcto cuando el motor no responde —degradación
controlada, no un daño inventado para disimular—, pero ya no es lo que pasa en
DEV con el host de contenido encendido.

Dónde está probado el camino completo, además del entorno:

- `EjecutarAccionTest` — la coordinación entera con el motor como doble.
- `CombateControllerTest` y `ResolverAtaqueTest` (en `motor-combate`) — el otro
  lado del mismo contrato.
- `combate.test.js` — la vista: enviar la acción, umbrales, reconexión sin
  duplicados y el final del combate.
- El banco E2E (`tests/e2e/compose.yml`), que juega una partida entera con los
  servicios de verdad.

## Operación (todo desde GitHub → Actions → "Infra dev (AWS Free Plan)")

| Acción | Qué hace | Cuándo |
|---|---|---|
| `plan` | muestra cambios y los comenta en el PR | sola, en cada PR que toque esta carpeta |
| `apply` | crea o actualiza | **sola, al fusionar en `develop`** (el plan aprobado en el PR es la confirmación); a mano, escribiendo `apply` |
| `start` / `stop` | enciende / apaga la instancia | cron (23:00 Colombia apaga; 07:00 lun-vie enciende) o a mano |
| `destroy` | borra todo, incluida la IP | solo a mano, escribiendo `destroy` |

> Límite de GitHub: el botón *Run workflow* y los `cron` solo funcionan cuando el
> archivo del workflow está en la rama por defecto (`main`). Hasta que `develop`
> se promueva a `main`, el apagado nocturno no corre y `start`/`stop`/`destroy`
> los pide un administrador (o se promueve `develop` → `main`). El `apply` por
> fusión en `develop` no tiene esa limitación.

Para una demo fuera de horario: `start` manual; el `stop` nocturno la apaga después.

### Después del primer `apply` (administrador del repo)

1. Leer las salidas en el resumen del job (`ip_publica`, `parametro_llave_privada`).
2. Con `simon-admin` (no root) en Systems Manager → Parameter Store → el parámetro
   indicado → *Show decrypted value*.
3. Actualizar los secretos del entorno `dev`: `DEPLOY_HOST_DEV` = IP,
   `SSH_USER_DEV` = `ubuntu`, `SSH_KEY_DEV` = la llave completa (`-----BEGIN ... END-----`).
4. Confirmar la suscripción SNS: AWS envía un correo "Subscription Confirmation"
   a `AWS_CORREO_ALERTAS`; sin ese clic no llegan los recordatorios (los
   presupuestos sí avisan sin confirmación).

## Salida (antes del 2027-03-15; en la práctica, al cerrar el proyecto)

1. Respaldar lo que valga la pena de `/opt/nexus` (volúmenes de Postgres) con
   Session Manager o `scp`; en `dev` normalmente nada.
2. `destroy` con confirmación. Verificar en la consola: EC2 (instancias, volúmenes,
   IPs elásticas), Parameter Store, Budgets, SNS, Scheduler → todo vacío.
3. Comprobar en Billing → Credits el saldo, y en Cost Explorer que el mes corriente
   marque 0 tras el destroy.
4. Decidir sobre la cuenta: dejar que cierre sola (opción por defecto, USD 0) o
   subir a Paid **solo** por decisión explícita del cliente.

## Pendientes de coordinación

- `infrastructure/entornos/main.tf` declara 3 hosts `t3.micro` (dev/test/prod)
  con estado local y sin backend: no se aplica en esta cuenta, y su propia
  cabecera lo marca como SUPERSEDED. Propuesta: retirarlo a favor de esta
  carpeta y de `../contenido/` (HU-CICD-002 / HU-POR-003, Néstor y Santiago G.).
- `cd.yml` despliega por `scp`/`ssh`. El perfil SSM ya está en el host para
  migrar a `aws ssm send-command` y cerrar el puerto 22 (`cidr_ssh = []`) sin
  cambiar nada más aquí.

## Resuelto

- **`mem_limit` en `docker-compose.deploy.yml`: ya está puesto.** Cada JVM va
  acotada a 384 MB con `-XX:MaxRAMPercentage=70` (ancla `x-servicio-plataforma`),
  y las piezas de infra llevan el suyo — Postgres 256 MB, Redis y Mailpit 64 MB,
  el borde 48 MB (líneas 32, 39, 47, 51 y 65 del archivo).
- **Desplegar por perfil de dominio: ya se hace.** Contenido tiene su propio
  host (`../contenido/`) y su propio job en `cd.yml` desde el 8-sep-2026.
