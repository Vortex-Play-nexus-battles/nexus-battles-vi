# Host de desarrollo del dominio de contenido

Instancia propia para `services/contenido/*` (héroes, inventario, productos, motor de combate y su MongoDB), desplegada por **el mismo pipeline** (`cd.yml`, job `desplegar-contenido-dev`) con **el mismo script** (`scripts/cd/desplegar.sh`) y **el mismo compose** (`docker-compose.contenido.yml`) que el host de plataforma. Lo único distinto es el host y la llave.

## Por qué

- Las reglas de trabajo de plataforma piden "desplegar por perfil de dominio, no los 20 servicios a la vez".
- El host de plataforma es una `t3.micro` (1 GB): no caben 20 JVM más las bases de datos.
- El cliente autorizó el uso de la prueba gratuita / créditos de AWS (clase del 2026-09-03).

## Cómo se levanta (una vez, quien tenga la cuenta)

```bash
cd infrastructure/entornos/contenido
terraform init
terraform apply
terraform output -raw ip_publica
terraform output -raw llave_privada > contenido-dev.pem   # queda fuera del repo (.gitignore)
```

## Secrets que hay que cargar (entorno `dev` del repositorio, lo hace un administrador)

| Secret | Valor |
|---|---|
| `DEPLOY_HOST_CONTENIDO_DEV` | `terraform output -raw ip_publica` |
| `SSH_USER_CONTENIDO_DEV` | `ubuntu` |
| `SSH_KEY_CONTENIDO_DEV` | contenido completo de `contenido-dev.pem` |

Con eso, el siguiente push a `develop` que toque cualquier servicio de contenido lo despliega ahí y verifica su salud. Los servicios quedan en `http://<ip>:8101` (héroes), `8102` (inventario), `8103` (productos) y `8104` (motor).

## Costos y apagado

`t3.medium` ~30 USD/mes bajo demanda, `t3.small` ~17 (variable `instance_type`). Se descuenta de los créditos de la cuenta. Apagar la instancia desde la consola cuando no se use; la IP elástica se conserva, así que el secreto no cambia.

## Lo que NO hace

- No toca el estado ni los recursos de `infrastructure/entornos/main.tf` (plataforma): estado propio, carpeta propia.
- No crea test ni producción de contenido: cuando existan esos entornos, se replica este archivo.
