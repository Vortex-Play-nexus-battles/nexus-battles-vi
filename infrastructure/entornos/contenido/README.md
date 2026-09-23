# Host de desarrollo del dominio de contenido

Instancia propia para `services/contenido/*` (héroes, inventario, productos, motor de combate y su MongoDB), desplegada por **el mismo pipeline** (`cd.yml`, job `desplegar-contenido-dev`) con **el mismo script** (`scripts/cd/desplegar.sh`) y **el mismo compose** (`docker-compose.contenido.yml`) que el host de plataforma. Lo único distinto es el host y la llave.

## Por qué

- Las reglas de trabajo de plataforma piden "desplegar por perfil de dominio, no los 20 servicios a la vez".
- El host de plataforma es una `t3.small` (2 vCPU / 2 GiB): ya corre nueve servicios, el borde, PostgreSQL, Redis y Mailpit. No caben ahí 20 JVM más las bases de datos.
- El cliente autorizó el uso de la prueba gratuita / créditos de AWS (clase del 2026-09-03).

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
