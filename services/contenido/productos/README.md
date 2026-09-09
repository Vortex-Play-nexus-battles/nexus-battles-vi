

## Despliegue

Este servicio se despliega en el host propio del dominio de contenido (`infrastructure/entornos/contenido/`), por el flujo `cd.yml` (job `desplegar-contenido-dev`) en cada push a `develop` que toque `services/contenido/productos`. Queda publicado en el puerto **8103** del host (8080 dentro del contenedor), con su MongoDB en la misma red de Compose (`MONGODB_URI`); `KEYCLOAK_JWK_SET_URI` la aporta el entorno del servidor cuando cuentas publique el JWKS. Salud: `http://<ip-del-host>:8103/actuator/health`.
