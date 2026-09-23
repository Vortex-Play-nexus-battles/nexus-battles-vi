# Arquitectura desplegada — los dos hosts de DEV

Este documento describe **lo que hay corriendo de verdad** en AWS, no lo que se
planeó en su día. Es la referencia a la que apunta el resto de la documentación
cuando dice «el host» o «el entorno de dev»: desde el **8 de septiembre de 2026**
no hay un host, hay **dos**, y mucho texto anterior a esa fecha quedó escrito en
singular. Si un documento del repositorio contradice lo que dice aquí, manda esto.

Nada de lo que sigue es secreto. Las dos direcciones IP son públicas —es su razón
de ser— y ya están escritas en la configuración del borde, en los workflows y en
las colecciones de Postman. Credenciales, llaves y secretos **no** se documentan
aquí ni en ningún otro archivo del repositorio (regla 10 de plataforma): viven en
los secretos del entorno `dev` de GitHub y en Parameter Store.

---

## 1. Por qué dos hosts y no uno

La cuenta de AWS es una cuenta normal con el **Free Plan**, con crédito acotado y
la política de gasto de bolsillo cero que describe
[`infrastructure/entornos/plataforma/README.md`](../../infrastructure/entornos/plataforma/README.md).
Bajo esa política el dimensionado no es una preferencia: es una restricción. Un
`t3.small` son 2 vCPU y 2 GiB de RAM, y una JVM de Spring Boot con su contexto
ocupa entre 250 y 380 MB. La suma de los servicios de plataforma más el borde,
PostgreSQL, Redis y Mailpit ya llena esa instancia.

Durante el Sprint 2 se intentó sostenerlo todo en una sola máquina y no cupo. El
resultado no era «un servicio más lento»: era el OOM killer eligiendo víctima sin
preguntar, y la víctima podía ser `salas-partidas`, que es justo lo que la demo
necesita en pie. Por eso el 8 de septiembre se levantó un **segundo host dedicado
al dominio de contenido**, con su propio Terraform, su propio estado y su propia
llave. Es la aplicación literal de la regla de trabajo de plataforma: *desplegar
por perfil de dominio, no los 20 servicios a la vez*.

Los dos hosts son `t3.small`. Los dos llevan **2 GiB de swap** en `/swapfile`,
creado por el `user_data` de su Terraform respectivo y reasegurado en cada
despliegue por `scripts/cd/asegurar-swap.sh`. El swap no está para correr más
servicios: está para que un pico de arranque **degrade** en vez de matar. Sin él,
el arranque simultáneo de varias JVM se lleva por delante un contenedor que ya
estaba sano.

---

## 2. Los dos hosts

### `nexus-plataforma-dev` — EIP `35.168.124.119`

Es el host **de cara al mundo**: el único que expone el puerto 80, el que sirve el
frontend y el que enruta todas las llamadas de la aplicación. Concentra el bloque
de Grupo 6 más la identidad de Cuentas.

| Servicio | Puerto | Qué hace |
|---|---|---|
| `srv-borde` (nginx 1.27-alpine) | **80** | Único puerto público. Sirve el frontend estático y enruta `/api/v1/*` y `/ws` |
| `srv-comentarios` | 8081 | Comentarios, calificación y su moderación |
| `srv-correo` | 8082 | Correo transaccional sobre plantilla MJML |
| `srv-torneos` | 8083 | Torneos, árbol de encuentros y transmisión |
| `srv-salas-partidas` | 8084 | Sala de batalla, partida, chat y canal STOMP |
| `srv-notificaciones` | 8085 | Bandeja y notificaciones en tiempo real |
| `srv-moderacion-sanciones` | 8086 | Lista negra, sanciones y apelaciones |
| `srv-metricas-plataforma` | 8087 | Observabilidad: disponibilidad, latencia, agregados |
| `srv-admin-parametros` | 8088 | Catálogo de parámetros configurables |
| `srv-ms-identidad` | 8089 | Emisor de tokens de usuario y de servicio (ADR-002 / ADR-005) |
| `plataforma-db` (PostgreSQL 17) | interno | Un esquema por servicio (regla 7) |
| `plataforma-cache` (Redis) | interno | Estado y caché |
| `mailpit` | interno, tras el borde en `/mailpit/` | Captura el correo de dev sin enviarlo |

Cada servicio Java lleva `mem_limit: 384m` y `-XX:MaxRAMPercentage=70` en
`docker-compose.deploy.yml` (líneas 32, 39, 47, 51, 65 para las piezas de infra;
el ancla `x-servicio-plataforma` para las JVM). Ese límite no es decorativo: es lo
que impide que un arranque se coma la memoria de los demás. Todos llevan además
`restart: unless-stopped`, porque sin eso un ciclo de apagado nocturno dejaría los
contenedores parados y la demo aparecería caída a la mañana siguiente.

### `nexus-contenido-dev` — EIP `34.193.90.11`

Es el host **interno**: nada de lo que corre aquí lo toca un navegador
directamente. Aloja el dominio de contenido (Grupo 2) y su base documental.

| Servicio | Puerto | Qué hace |
|---|---|---|
| `srv-heroes` | 8101 | Héroes, equipos, estrategias, progresión |
| `srv-inventario` | 8102 | Inventario y equipamiento del jugador |
| `srv-productos` | 8103 | Catálogo de productos |
| `srv-motor-combate` | 8104 | Resolución de ataques y efectos |
| `contenido-mongo` (MongoDB 8) | interno, sin puerto publicado | Documental de contenido |

Los límites de memoria de este host se declaran en `docker-compose.contenido.yml`
con `deploy.resources.limits.memory` (320 MB por servicio Java, 384 MB para
MongoDB), que Docker Compose v2 aplica sin necesidad de swarm. Suman ~1,7 GB:
caben en 2 GiB con el swap de respaldo.

---

## 3. Cómo se conectan: el borde nginx

El borde vive **solo en el host de plataforma** y es la única puerta. Está
configurado en
[`infrastructure/red-balanceo/borde-dev.conf`](../../infrastructure/red-balanceo/borde-dev.conf),
y el reparto de prefijos lo verifica `comprobar-rutas.sh` en CI.

Su trabajo es doble:

1. **Servir el frontend.** `cd.yml` deja `frontend/app-web`, `shared/ui-kit` y la
   propia configuración del borde en `/opt/nexus/web/`, montado de solo lectura en
   el contenedor de nginx.
2. **Enrutar las API.** Cada prefijo `/api/v1/<algo>` tiene su `location`. Los
   prefijos de plataforma y de cuentas resuelven a un nombre de servicio de la red
   interna de Compose (`srv-salas-partidas:8084`, `srv-ms-identidad:8089`…). Los
   prefijos de contenido —`/api/v1/heroes`, `/equipos`, `/estrategias`,
   `/progresion`, `/inventario`, `/productos`— resuelven **a la IP elástica del
   otro host** y a su puerto: `34.193.90.11:8101`, `:8102`, `:8103`.

Esa es la costura entre las dos máquinas, y es la única que atraviesa internet. La
segunda —invisible para el navegador— es `salas-partidas`, que llama por HTTP a
`inventario` (puerta de héroe, HU-SAL-003) y a `motor-combate` (resolución de
acciones) desde el host de plataforma.

Para el navegador todo esto es un único origen: `http://35.168.124.119`. El
segundo host no aparece nunca en una URL del cliente.

### Por qué 8101-8104 están cerrados salvo desde la EIP de plataforma

El grupo de seguridad de `nexus-contenido-dev` permite 8101-8104 **solo desde
`35.168.124.119/32`** (R9.4). La razón es que quien los consume de verdad es uno
solo: el host de plataforma. De ahí salen las dos únicas llamadas reales —el borde
nginx y `salas-partidas`— y ninguna otra. Dejarlos en `0.0.0.0/0` publicaba cuatro
servicios Java a internet sin ninguna necesidad, y con ellos su superficie de
ataque y su `/actuator`.

Se comprobó **antes** de cerrar, no después: `smoke-dev.yml` y
`smoke-aws.smoke.spec.js` solo usan el puerto 80 del host de plataforma, y
`diagnostico-dev.yml` consulta `localhost` desde dentro de cada host por SSH.
Ninguno se quedó fuera. La consecuencia práctica está en las colecciones de
Postman de contenido: su `baseUrl` va **por el borde**, no al puerto directo —
mismo camino que la aplicación real, con lo que de paso ejercitan el enrutado.

Para depurar contra el puerto directo desde un portátil se añade la IP propia a
`cidr_servicios` en `infrastructure/entornos/contenido/main.tf`, a propósito y
temporalmente. No se deja puesta.

El puerto **22 sigue abierto en los dos hosts, a propósito**: `cd.yml` despliega
por `scp`+`ssh` desde runners de GitHub, que no tienen IP fija. La autenticación es
solo por llave (la AMI de Ubuntu no acepta contraseña). La salida limpia es migrar
el despliegue a SSM Session Manager —el perfil de instancia ya está puesto—, no
adivinar un rango de IPs.

---

## 4. Qué gobierna cada host en Terraform

Dos carpetas, dos estados, ningún recurso compartido.

| Carpeta | Host | Estado remoto (S3) |
|---|---|---|
| [`infrastructure/entornos/plataforma/`](../../infrastructure/entornos/plataforma/) | `nexus-plataforma-dev` | `entornos/plataforma-dev.tfstate` |
| [`infrastructure/entornos/contenido/`](../../infrastructure/entornos/contenido/) | `nexus-contenido-dev` | `entornos/contenido-dev.tfstate` |

Ambos backends viven en el bucket versionado
`nexus-battles-vi-tfstate-362403299569`, con `use_lockfile` (bloqueo: dos personas
aplicando a la vez ya no corrompen el estado) y los proveedores fijados por
versión. Se aplican **solo** por `.github/workflows/infra-dev.yml`, que asume un
rol IAM por OIDC: ninguna clave de AWS existe en el repositorio ni en los secretos
de GitHub.

Cada carpeta declara su instancia, su IP elástica, su grupo de seguridad, su par de
llaves, el perfil de instancia con `AmazonSSMManagedInstanceCore` y el `user_data`
que instala Docker y crea el swap. La de plataforma añade los dos presupuestos de
AWS y los recordatorios de salida por SNS + EventBridge Scheduler.

> **`infrastructure/entornos/main.tf` no gobierna nada.** Declara tres instancias
> `t3.micro` (`nexus-dev` / `test` / `prod`) con estado local y sin backend, y
> ningún workflow lo aplica. Se conserva como referencia histórica del diseño
> inicial de tres entornos; lo sustituyen las dos carpetas de la tabla. Su propia
> cabecera lo dice.

---

## 5. El apagado programado

Ningún host se queda encendido las 24 horas. `infra-dev.yml` cubre **los dos** con
la misma política horaria, en una matriz:

| Acción | Cron (UTC) | Hora Colombia |
|---|---|---|
| apagar | `23 4 * * *` y `23 5 * * *` | 23:23 y 00:23, todos los días |
| encender | `17 12 * * 1-5` y `17 13 * * 1-5` | 07:17 y 08:17, lunes a viernes |

Cada acción se intenta dos veces con una hora de diferencia, y los minutos van
fuera de punto. La razón es concreta: GitHub retrasa las tareas programadas cuando
hay carga y, si el retraso es grande, las descarta sin más. Repetir no molesta
porque encender y apagar son idempotentes; una noche sin apagar sí cuesta dinero.
Los fines de semana quedan apagados a propósito.

Quien necesite desplegar fuera de horario no tiene que hacer nada: el propio CD
enciende el host que le toca con la acción local
`.github/actions/asegurar-host-encendido`, espera los chequeos de EC2 y comprueba
que el 22 responde antes de gastar el minuto del `scp` (R9.2).

Con la instancia apagada solo factura la IP elástica y el disco. La IP se conserva,
así que **los secretos de despliegue no cambian** tras un ciclo de apagado.

> Límite conocido de GitHub: el botón *Run workflow* y los `cron` solo existen
> cuando el archivo del workflow está en la rama por defecto (`main`). Mientras
> `develop` no se promueva, el apagado automático depende de que `main` tenga la
> versión vigente del workflow.

---

## 6. Qué NO está desplegado, y por qué

Esta sección existe porque la ausencia de estos servicios en AWS se ha leído más de
una vez como un defecto o un olvido. No lo es: es una **decisión de capacidad
documentada**, tomada con el gasto de bolsillo en cero como restricción dura.

Los cuatro servicios del bloque de economía y cumplimiento **no corren en ninguno
de los dos hosts**. Caben en una máquina o caben los servicios que sostienen el
MVP, y se eligió el MVP.

| Servicio | AWS dev | Banco E2E (CI) | Estado y motivo |
|---|---|---|---|
| `ms-finanzas` (M07, libro de créditos) | **NO DESPLEGADO** | **SÍ** (`tests/e2e/compose.yml`) | No tiene puerto asignado en `puerto_de()` de `cd.yml`, así que el flujo lo omite explícitamente: no se construye su imagen ni se despliega. En el banco E2E corre de verdad y contra él se ejercitan la apuesta de créditos y la recompensa por partida. |
| `ms-subastas` (M08) | **NO DESPLEGADO** | **SÍ** (`tests/e2e/compose.yml`) | Mismo caso: sin puerto en `cd.yml`, fuera del flujo automático. En el banco E2E corre con el JWKS real de `ms-identidad`. |
| `ms-ecommerce` (M06, tienda y carrito) | **NO DESPLEGADO** (solo a demanda) | NO | Sí tiene bloque de detección y puerto 8090 en `cd.yml`, pero está en `FUERA_DEL_HOST_DEV`: en cualquier disparo que no sea `workflow_dispatch` se le saca de la matriz por capacidad. A demanda sí se intenta — quien lo pide sabe lo que hace. |
| `ms-cumplimiento` (auditoría y privacidad) | **NO DESPLEGADO** (solo a demanda) | NO | Idéntico, puerto 8091. Su ausencia es la razón de que `AUDITORIA_URL` se deje sin poner: el asiento de auditoría queda en la tabla del propio servicio y en la bitácora JSON, que es el rastro que exige la ficha. |

**Las pruebas y la compuerta de calidad de los cuatro siguen corriendo en
`ci.yml`.** No estar desplegado no es estar sin verificar.

### Qué se ve en DEV cuando se toca uno de ellos

Nada falla en silencio, que es lo que pide el riesgo #7 del Project Charter:

- Una sala con recompensa `> 0` responde **503 `creditos-no-disponibles`** y no
  reserva nada. Las salas sin recompensa funcionan completas.
- Una inscripción a torneo con costo `> 0` responde **503 `libro-no-disponible`**.
  Un torneo gratuito funciona de principio a fin.
- Las URL (`CREDITOS_URL`, `AUDITORIA_URL`) ya están escritas en el compose
  apuntando a donde estarán: el día que se desplieguen, no hay que tocar
  configuración.

Dónde sí se demuestra el camino completo: en el **banco E2E**
(`tests/e2e/compose.yml`), que levanta los servicios de verdad con sus bases,
la misma configuración del borde y los mismos jars, y juega una partida entera con
la apuesta liquidada en el libro real.

---

## 7. Documentos relacionados

- [`infrastructure/entornos/plataforma/README.md`](../../infrastructure/entornos/plataforma/README.md) — política de la cuenta, costos y operación del host de plataforma
- [`infrastructure/entornos/contenido/README.md`](../../infrastructure/entornos/contenido/README.md) — gobierno, importación de estado y grupos de seguridad del host de contenido
- [`infrastructure/red-balanceo/borde-dev.conf`](../../infrastructure/red-balanceo/borde-dev.conf) — el reparto de rutas, prefijo por prefijo
- [`docs/gobierno/ADR-005-emisor-transitorio-de-credenciales-de-servicio.md`](../gobierno/ADR-005-emisor-transitorio-de-credenciales-de-servicio.md) — quién emite los tokens entre servicios
- [`docs/gobierno/SIMULACRO-REVERSION.md`](../gobierno/SIMULACRO-REVERSION.md) — acta de la reversión automática ejecutada sobre el host de plataforma
- [`docs/gobierno/DEMO-SPRINT-2.md`](../gobierno/DEMO-SPRINT-2.md) — qué se demuestra en cada entorno
