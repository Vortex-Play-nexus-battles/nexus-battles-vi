# Host de desarrollo del dominio de contenido

Instancia propia para `services/contenido/*` (héroes, inventario, productos, motor de combate y su MongoDB), desplegada por **el mismo pipeline** (`cd.yml`, job `desplegar-contenido-dev`) con **el mismo script** (`scripts/cd/desplegar.sh`) y **el mismo compose** (`docker-compose.contenido.yml`) que el host de plataforma. Lo único distinto es el host y la llave.

## Por qué

- Las reglas de trabajo de plataforma piden "desplegar por perfil de dominio, no los 20 servicios a la vez".
- El host de plataforma es una `t3.small` (2 vCPU / 2 GiB): ya corre nueve servicios, el borde, PostgreSQL, Redis y Mailpit. No caben ahí 20 JVM más las bases de datos.
- El cliente autorizó el uso de la prueba gratuita / créditos de AWS (clase del 2026-09-03).

## ANTES DE NADA: este host no vive en la cuenta de plataforma

`nexus-contenido-dev` **existe y está encendido** (IP elástica `34.193.90.11`, arrancado el 9 de septiembre, sin reinicios). Pero vive en la **cuenta gratuita del grupo 2**, no en la de plataforma. El rol OIDC de `vars.AWS_ROLE_ARN` —el que usan `infra-dev.yml` y `cd.yml`— es de la cuenta de plataforma y **no lo ve**.

De ahí salen dos mensajes que parecen decir "el host no existe" y no lo dicen:

| Lo que sale | Lo que significa de verdad |
|---|---|
| `inventario` → *"No hay ninguna instancia con tag Name=nexus-contenido-dev"* | Este rol no la ve. La instancia está corriendo en la otra cuenta. |
| `plan` → *"5 to add"* (instancia, IP, SG, llave) | El estado remoto está vacío **y** el rol no ve el host. No es un host ausente. |

**No se aplica ese plan, y `IMPORTACION_CONTENIDO_COMPLETADA` no se pone en `true` para desbloquearlo.** Aplicarlo crearía un **tercer EC2 en la cuenta equivocada**, con coste nuevo, mientras el host real sigue corriendo y recibiendo despliegues por SSH. Tampoco se puede importar: un `import` de OpenTofu no cruza cuentas, y el rol de esta carpeta es el de plataforma.

Lo comprueba solo el paso *"Compuerta — contenido NUNCA crea un host (vive en otra cuenta)"* de `infra-dev.yml`: si el plan propone crear el host, avisa en `plan` y **falla en `apply`**.

### Entonces, ¿cómo se despliega hoy?

Por SSH, sin tocar esta carpeta: `cd.yml` → job `desplegar-contenido-dev` → secrets `DEPLOY_HOST_CONTENIDO_DEV`, `SSH_USER_CONTENIDO_DEV`, `SSH_KEY_CONTENIDO_DEV`. Funciona y está probado (corrida #353 del 23-sep: los cuatro servicios desplegados y sanos). Lo que **no** está gobernado por OpenTofu es la forma del host; eso sigue pendiente y solo se arregla de una de estas dos maneras:

1. crear un rol OIDC en la cuenta del grupo 2 y apuntar esta carpeta (y su bucket de estado) allí — es lo que ya prevén `vars.AWS_ROLE_ARN_CONTENIDO` y `vars.AWS_REGION_CONTENIDO` en `cd.yml` y `diagnostico-dev.yml`; o
2. aceptar por escrito que este host se gobierna a mano desde la cuenta del grupo 2, y borrar esta carpeta para que no vuelva a proponer crear nada.

Decidirlo es de los dueños de las dos cuentas. Hasta entonces, lo de abajo describe el diseño **como se pensó cuando se creía que el host estaba en esta cuenta**; se conserva porque sigue siendo válido el día que se elija la opción 1.

## Estado del gobierno de esta carpeta — R9.1

Hasta R9.1, esta carpeta se aplicaba **a mano**, con el estado en el disco de quien la aplicó. Cuatro consecuencias, todas reales:

1. **El estado no estaba en ningún sitio compartido.** No en el repositorio (bien), pero tampoco en S3: si esa persona pierde el portátil o se desvincula, el host queda sin forma gobernable de cambiarse. Es el riesgo #5 del acta aplicado a infraestructura.
2. **Sin bloqueo:** dos personas aplicando a la vez corrompen el estado.
3. **Sin versionado:** un `destroy` por error no tiene de dónde recuperarse.
4. **Proveedores sin fijar:** `init` hoy y dentro de un mes pueden traer versiones distintas del proveedor de AWS sobre la misma infraestructura.

Plataforma no tenía ninguno de los cuatro. R9.1 lo alinea: `versiones.tf` con el backend S3 (`entornos/contenido-dev.tfstate`, con `use_lockfile`) y los proveedores fijados, y `default_tags` en el proveedor para que Cost Explorer pueda al fin separar el gasto de este host del de plataforma.

### La compuerta antes del primer `apply` desde CI

Mover el backend a S3 deja el estado remoto **vacío**. Con el estado vacío, `tofu plan` no dice "sin cambios": propone **crear** instancia, IP, grupo de seguridad y llave. Aplicar eso levantaría un tercer EC2 y coste duplicado mientras los actuales siguen corriendo.

Por eso el orden es: backend → **importar** (`importaciones.tf`) → plan limpio → y solo entonces habilitar `apply`. El workflow lo bloquea solo: el paso *"Compuerta — no aplicar sobre contenido hasta que esté importado"* falla mientras la variable de repositorio `IMPORTACION_CONTENIDO_COMPLETADA` no valga `true`.

### Cómo se hace la importación

```
Actions > Infra dev (AWS Free Plan) > Run workflow
  host   = contenido
  accion = inventario          # SOLO LECTURA: imprime los identificadores reales
```

Con esa salida se rellenan los bloques `import` de `importaciones.tf`, se lanza `accion = plan`, y **tiene que decir**:

```
No changes. Your infrastructure matches the configuration.
```

Si en vez de eso aparece **un solo** `destroy` o `replace` sobre la instancia, la IP elástica, el volumen o el grupo de seguridad: **no se aplica**. Se abre issue técnico con recurso, motivo, riesgo y alternativa. El workflow también lo comprueba solo, leyendo el plan, en el paso *"Compuerta — 0 destroy y 0 replace sobre recursos críticos"*.

**Lo primero que va a aparecer**, y conviene saberlo de antemano: `tls_private_key.contenido_dev` no se puede importar, porque la clave privada solo existió en la memoria del proceso que la generó. Al adoptar el key pair sin ella, Terraform propondrá reemplazarlo para regenerarla — y eso es un `replace`. La salida limpia es sacar la generación de la llave de Terraform (la llave real ya está en los secrets del entorno `dev`) y declarar el key pair con `lifecycle { ignore_changes = [public_key] }`.

## Apagado automático — R9.3

Este host **ya no se queda encendido las 24 h**. `infra-dev.yml` cubre ahora los dos hosts con la misma política horaria, sin cambiar ni un horario:

| Acción | Cron (UTC) | Hora Colombia |
|---|---|---|
| apagar | `23 4 * * *` y `23 5 * * *` | 23:23 y 00:23, todos los días |
| encender | `17 12 * * 1-5` y `17 13 * * 1-5` | 07:17 y 08:17, lunes a viernes |

Cada acción se intenta dos veces con una hora de diferencia, y los minutos van fuera de punto: GitHub retrasa las tareas programadas cuando hay carga y, si el retraso es grande, las descarta. Repetir no molesta porque la acción es idempotente.

Los fines de semana queda apagado a propósito. Quien necesite desplegar un sábado no tiene que hacer nada: el propio CD lo enciende (R9.2).

## Despliegue fuera de horario — R9.2

El job `desplegar-contenido-dev` daba por hecho que el host estaba encendido. Plataforma dejó de darlo por hecho en #433, después de que un `start` programado no llegara a ejecutarse y el despliegue de esa tarde muriera contra un host apagado. Contenido heredó el cron pero no la corrección.

Ahora los dos usan la misma acción local, `.github/actions/asegurar-host-encendido`: busca la instancia por etiqueta, la enciende si hace falta, espera los chequeos de EC2 y comprueba que el 22 responda antes de gastar el minuto del `scp`. Es idempotente.

## Security Groups — R9.4

| Puerto | Antes | Ahora | Por qué |
|---|---|---|---|
| 22 (SSH) | `0.0.0.0/0` | **igual, a propósito** | `cd.yml` despliega por `scp`+`ssh` desde runners de GitHub, que no tienen IP fija. Autenticación **solo por llave** (la AMI de Ubuntu no acepta contraseña). Cerrarlo dejaría al CD sin forma de desplegar; la salida de verdad es mover el despliegue a **SSM Session Manager** (sin puerto abierto), no adivinar un rango de IPs. |
| 8101-8104 | `0.0.0.0/0` | **`35.168.124.119/32`** (EIP del host de plataforma) | Quien los consume de verdad es uno solo: el host de plataforma. De ahí salen las dos únicas llamadas reales — el borde nginx, que proxea `/api/v1/{heroes,inventario,productos}`, y `salas-partidas`, que pregunta a inventario y al motor. El navegador nunca los toca: entra por el borde, en el 80. |

Se comprobó **antes** de cerrar, no después: `smoke-dev.yml` y `smoke-aws.smoke.spec.js` solo usan `http://<host-plataforma>` (puerto 80), y `diagnostico-dev.yml` consulta `localhost` desde dentro del host por SSH. Ninguno se queda fuera.

Lo que sí cambia de sitio son las colecciones de Postman de inventario y productos: su `LEEME` apuntaba directo a `34.193.90.11:8102/8103` y ahora va por el borde — mismo origen que la aplicación real, y de paso ejercitan el enrutado. La única petición que no sobrevive es `{{baseUrl}}/actuator/health` de productos, porque el borde solo enruta `/api/v1/*`; la salud por host ya la cubre `diagnostico-dev.yml`.

Para depurar contra el puerto directo desde un portátil se añade la IP propia a `cidr_servicios`, a propósito y temporalmente. No se deja puesta.

## Acción humana pendiente (B12) — no se puede hacer desde este repositorio

Lo de arriba es lo que **declara** `main.tf`. Pero esta carpeta no gobierna el host real (vive en la cuenta del grupo 2, ver «ANTES DE NADA»), y la realidad del 27-sep-2026 es otra: **desde internet se llega a `34.193.90.11:8102` y `:8103`**. Desde este repositorio no se toca nada de esa cuenta, a propósito: ni `tofu apply`, ni consola, ni credenciales. Quedan dos acciones para el dueño de la cuenta del grupo 2, con el equipo de infraestructura (Grupo 6) acompañando.

### 1. Cerrar 8101-8104 al mundo (15 minutos, sin ventana)

**Qué:** en el grupo de seguridad de `nexus-contenido-dev`, las reglas de entrada de 8101-8104 pasan de `0.0.0.0/0` a **`35.168.124.119/32`** (la IP elástica del host de plataforma). El 22 se queda como está (solo llave, lo usa `cd.yml`). Cuando misiones (B9) se despliegue en 8105, la misma regla.

**Por qué:** hoy cualquiera llega a cada servicio de contenido sin pasar por el borde: `/actuator`, rutas de escritura sin el límite de frecuencia del borde y sin sus cabeceras. Los únicos clientes legítimos de esos puertos salen del host de plataforma: el borde (`/api/v1/{heroes,equipos,estrategias,progresion,inventario,productos}`) y `salas-partidas` (inventario, héroes, productos y el motor). El navegador entra por el 80 de plataforma. Es el mismo cierre que B12 hizo en plataforma (`../plataforma/README.md`, «Red»).

**Cómo** (una de las dos):

- **Consola** (lo directo): EC2 → *Security Groups* → el grupo de `nexus-contenido-dev` → *Inbound rules* → *Edit* → en las reglas de 8101-8104 (o 8101-8105) cambiar *Source* a `35.168.124.119/32` → *Save*. Es un cambio en sitio: no reinicia nada ni corta conexiones abiertas.
- **OpenTofu desde la cuenta del grupo 2**: `cidr_servicios` ya vale `["35.168.124.119/32"]` en `main.tf`, pero antes hace falta la importación descrita arriba (o el `plan` propondrá crear un host nuevo). Solo si se elige la opción 1 de «¿cómo se despliega hoy?».

**Comprobar** antes y después:

```bash
# desde un portatil: antes 200, despues se agota el tiempo
curl -m 5 -s -o /dev/null -w '%{http_code}\n' http://34.193.90.11:8102/actuator/health
# desde el host de plataforma (diagnostico-dev.yml o SSH): 200 antes y despues
curl -m 5 -s -o /dev/null -w '%{http_code}\n' http://34.193.90.11:8102/actuator/health
```

Y el smoke de DEV (`smoke-dev.yml`) en verde: pasa por el borde y por `salas-partidas`, que son justo los dos clientes que se mantienen.

### 2. Autenticación en el MongoDB de contenido (ventana de ~30 minutos)

**Situación:** `contenido-mongo` corre **sin autenticación**. No publica puerto en el host (solo se llega desde la red de su compose), así que hoy no está expuesto a internet; pero cualquier contenedor de esa red, o cualquiera con una shell en el host, lee y escribe todas las bases, y un `ports:` añadido por error lo dejaría abierto al mundo. Tampoco hay aislamiento entre servicios: inventario podría escribir en la base de héroes (regla 7 de plataforma).

**Plan** (lo ejecuta el grupo 2 con Grupo 6, fuera de horario de demo):

1. **Respaldo** justo antes: `respaldo-dev.yml` a demanda (o `respaldar.sh --host contenido` en el host) y comprobar su simulacro en verde (`../../respaldo-dr/README.md`).
2. **Usuarios, con la autenticación todavía apagada** (se pueden crear antes de activarla): un administrador (`root` en `admin`) y **un usuario por servicio que usa Mongo** con `readWrite` solo sobre su base: `heroes`, `inventario`, `productos` y, cuando se despliegue, `misiones` (el motor de combate no tiene base). Las claves se generan en ese momento y van **solo** a secretos del entorno `dev` de GitHub (p. ej. `MONGO_ROOT_PASSWORD`, `MONGO_HEROES_PASSWORD`…); nunca al repositorio ni a una bitácora.
3. **Servicios con credenciales:** cada `SPRING_MONGODB_URI`/`MONGODB_URI` de `docker-compose.contenido.yml` pasa a `mongodb://<servicio>:${CLAVE}@contenido-mongo:27017/<base>?authSource=<base>`, con la clave por variable que reparte `desplegar.sh` (como hoy los secretos de plataforma). Se despliegan: con la autenticación apagada, las credenciales se aceptan igual, así que este paso no rompe nada.
4. **Activar la autenticación:** `command: ["mongod", "--auth"]` en `contenido-mongo` y reinicio. `MONGO_INITDB_ROOT_USERNAME/PASSWORD` **no** activan nada sobre un volumen que ya tiene datos (solo actúan al inicializar uno vacío), así que el usuario administrador se crea a mano en el paso 2; esas dos variables se ponen igualmente en el contenedor, porque son las que leen `respaldar.sh` y `simulacro-restauracion.sh` para autenticarse (ya lo soportan: probado con Mongo 8.0 con autenticación).
5. **Comprobar:** salud de los servicios de contenido, `smoke-dev.yml` en verde, un respaldo con su simulacro en verde, y que `mongosh` sin credenciales ya no lista las bases.
6. **Marcha atrás** si algo falla: quitar `--auth` y reiniciar el contenedor. Los usuarios creados no estorban.

Sin coste nuevo: es configuración del mismo contenedor, en el mismo host.

## Acción humana pendiente (28-sep) — puertos para repartir carga y rol OIDC

Medido el 28-sep (`diagnostico-dev.yml`, ver `../../despliegue/CAPACIDAD.md`):
plataforma está con el swap lleno (2047/2047 MiB) y este host tiene 722-740
MiB disponibles. El reparto propuesto trae aquí **misiones** (8105),
**ms-subastas** (8092) y **ms-ecommerce** (8090), y **ms-chatbot** (8094) solo
si la medición lo permite. Su único cliente es el borde del host de plataforma,
así que los cuatro puertos se abren **solo** a su IP elástica. Lo hace el dueño
de la cuenta `551262695144`, una vez, en la consola o en CloudShell.

### Paso A · Grupo de seguridad (10 minutos, sin ventana)

```text
AWS Console (cuenta 551262695144, N. Virginia us-east-1)
→ EC2 → Security Groups → sg-01bfe668448b037c4 → Inbound rules → Edit inbound rules
   1) Añadir primero (para no cortar nada), todas con Source Custom 35.168.124.119/32:
      Custom TCP 8101 "heroes <- plataforma"      Custom TCP 8105 "misiones <- plataforma"
      Custom TCP 8102 "inventario <- plataforma"  Custom TCP 8092 "ms-subastas (HTTP+WS) <- borde"
      Custom TCP 8103 "productos <- plataforma"   Custom TCP 8090 "ms-ecommerce <- borde"
      Custom TCP 8104 "motor <- plataforma"       Custom TCP 8094 "ms-chatbot <- borde"
   2) Borrar las reglas de 8101-8104 cuyo Source sea 0.0.0.0/0
   3) No tocar el 22. Ninguna regla nueva con 0.0.0.0/0.
   → Save rules
```

En CloudShell de esa cuenta, lo mismo:

```bash
SG=sg-01bfe668448b037c4
for p in 8101 8102 8103 8104 8105 8092 8090 8094; do
  aws ec2 authorize-security-group-ingress --region us-east-1 --group-id $SG \
    --ip-permissions "IpProtocol=tcp,FromPort=$p,ToPort=$p,IpRanges=[{CidrIp=35.168.124.119/32,Description=nexus-plataforma}]"
done
aws ec2 describe-security-group-rules --region us-east-1 --filters Name=group-id,Values=$SG \
  --query "SecurityGroupRules[?IsEgress==\`false\` && CidrIpv4=='0.0.0.0/0'].[SecurityGroupRuleId,FromPort,ToPort]" --output table
aws ec2 revoke-security-group-ingress --region us-east-1 --group-id $SG --security-group-rule-ids <sgr-de-8101-8104>
```

Lo comprueba Grupo 6 después: desde internet, 8101-8104 dejan de contestar;
desde plataforma, 8090, 8092, 8094 y 8101-8105 contestan.

### Paso B · Rol OIDC para este repositorio (15-20 minutos, recomendado)

Con él, el pipeline enciende y apaga este host con el mismo horario que
plataforma (hoy lleva semanas encendido 24 h) y los cambios de puertos
futuros dejan de necesitar a una persona. Permisos mínimos: nada de IAM, S3,
otras instancias ni otros grupos de seguridad.

1. IAM → Identity providers → Add provider → OpenID Connect ·
   `https://token.actions.githubusercontent.com` · audiencia `sts.amazonaws.com`
   (si ya existe, se salta).
2. IAM → Policies → Create policy → JSON, nombre `nexus-contenido-dev-ci`:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       { "Sid": "Leer", "Effect": "Allow",
         "Action": ["ec2:DescribeInstances", "ec2:DescribeInstanceStatus",
                    "ec2:DescribeSecurityGroups", "ec2:DescribeSecurityGroupRules"],
         "Resource": "*" },
       { "Sid": "EncenderApagarSoloContenido", "Effect": "Allow",
         "Action": ["ec2:StartInstances", "ec2:StopInstances"],
         "Resource": "arn:aws:ec2:us-east-1:551262695144:instance/i-0388a00d533e39039" },
       { "Sid": "ReglasDeEntradaSoloDeEsteSG", "Effect": "Allow",
         "Action": ["ec2:AuthorizeSecurityGroupIngress", "ec2:RevokeSecurityGroupIngress",
                    "ec2:UpdateSecurityGroupRuleDescriptionsIngress"],
         "Resource": ["arn:aws:ec2:us-east-1:551262695144:security-group/sg-01bfe668448b037c4",
                      "arn:aws:ec2:us-east-1:551262695144:security-group-rule/*"] }
     ]
   }
   ```

3. IAM → Roles → Create role → Web identity → proveedor
   `token.actions.githubusercontent.com`, audiencia `sts.amazonaws.com`,
   organización `Vortex-Play-nexus-battles`, repositorio `nexus-battles-vi`,
   rama `develop` → política `nexus-contenido-dev-ci` → nombre
   `github-actions-nexus-contenido-dev`.
4. Trust relationships → Edit trust policy:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Principal": { "Federated": "arn:aws:iam::551262695144:oidc-provider/token.actions.githubusercontent.com" },
       "Action": "sts:AssumeRoleWithWebIdentity",
       "Condition": {
         "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
         "StringLike": { "token.actions.githubusercontent.com:sub": [
           "repo:Vortex-Play-nexus-battles/nexus-battles-vi:environment:dev",
           "repo:Vortex-Play-nexus-battles/nexus-battles-vi:ref:refs/heads/develop",
           "repo:Vortex-Play-nexus-battles@317725248/nexus-battles-vi@1336530373:environment:dev",
           "repo:Vortex-Play-nexus-battles@317725248/nexus-battles-vi@1336530373:ref:refs/heads/develop"
         ] }
       }
     }]
   }
   ```

5. El ARN (`arn:aws:iam::551262695144:role/github-actions-nexus-contenido-dev`)
   no es un secreto: se guarda como variable de repositorio
   `AWS_ROLE_ARN_CONTENIDO`. `cd.yml`, `diagnostico-dev.yml` e `infra-dev.yml`
   ya lo leen. Nunca access keys estáticas.

Con el rol puesto:

- **El paso A lo puede hacer el pipeline:** `Actions → Infra dev → Run workflow → host
  contenido, accion reglas-sg` aplica `reglas-entrada.json` (probado en CI con
  `scripts/cd/pruebas/reglas-sg-contenido.sh`). Si el dueño solo hace el paso B, Grupo 6
  hace el A desde ahí.
- **El horario de plataforma pasa a aplicarse también a este host:** se apaga a las 23:23 y
  se enciende a las 07:17 (hora Colombia) de lunes a viernes, y queda apagado el fin de
  semana. Si el Grupo 2 necesita su host encendido 24 h, que el rol **no** incluya
  `ec2:StopInstances`.

### Lo que ya está en este host antes del paso A, y lo que queda después

Desde la fase 1 de la topología (28-sep), **misiones** y **ms-subastas** se
despliegan aquí aunque el paso A no esté hecho: arrancan, responden su salud y
hablan con plataforma (8085-8089 y 8093 ya admiten a `34.193.90.11/32`). Lo que
el paso A desbloquea es la **entrada** desde el borde. Después, Grupo 6 sigue
sin nadie más:

1. `diagnostico-dev.yml` con `ambiente=dev` → sección «RED ENTRE HOSTS»: 8105 y
   8092 dejan de dar `000`. `/api/v1/misiones` empieza a responder en ese
   momento (el borde ya apunta a `34.193.90.11:8105`).
2. Un PR pequeño lleva el borde de ms-subastas de `srv-ms-subastas:8092` a
   `34.193.90.11:8092` (REST y `/api/v1/ws-subastas`); hasta entonces el borde
   da 502 al instante, y apuntar antes a la IP con el puerto cerrado daría 504.
3. Smoke, canarios y prueba del profesor en dev.
4. Fase 2 (ms-ecommerce, 8090, con su base por volcado y restauración) y fase
   3 (ms-chatbot, 8094) solo si la compuerta de capacidad de este host lo
   permite (`../../despliegue/CAPACIDAD.md`).

## Cómo se levantó (histórico)

```bash
cd infrastructure/entornos/contenido
terraform init
terraform apply
terraform output -raw ip_publica
terraform output -raw llave_privada > contenido-dev.pem   # queda fuera del repo (.gitignore)
```

Desde R9.1 esto **ya no es el camino**: se hace por `infra-dev.yml`, con el estado en S3 y el plan revisado en el PR.

## Secrets que hay que cargar (entorno `dev` del repositorio, lo hace un administrador)

| Secret | Valor |
|---|---|
| `DEPLOY_HOST_CONTENIDO_DEV` | `terraform output -raw ip_publica` |
| `SSH_USER_CONTENIDO_DEV` | `ubuntu` |
| `SSH_KEY_CONTENIDO_DEV` | contenido completo de `contenido-dev.pem` |

Con eso, el siguiente push a `develop` que toque cualquier servicio de contenido lo despliega ahí y verifica su salud. Los servicios quedan en `http://<ip>:8101` (héroes), `8102` (inventario), `8103` (productos) y `8104` (motor).

## Costos y apagado

`t3.small` ~17 USD/mes más la IP elástica ~3,6 USD/mes, descontados de los créditos del plan gratuito (hasta 200 USD por 6 meses; el semestre cabe con holgura). `t3.small` es tipo elegible del plan gratuito; `t3.medium` no lo es (variable `instance_type`). Desde R9.3 el apagado nocturno es automático; apagada, solo factura la IP elástica y el disco, y la IP se conserva, así que el secreto no cambia.

## Lo que NO hace

- No toca el estado ni los recursos de `infrastructure/entornos/plataforma/`: estado propio, clave propia en el mismo bucket.
- No crea test ni producción de contenido: cuando existan esos entornos, se replica este archivo.
