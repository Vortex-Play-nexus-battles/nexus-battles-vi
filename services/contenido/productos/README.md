

## Despliegue

Este servicio se despliega en el host propio del dominio de contenido (`infrastructure/entornos/contenido/`), por el flujo `cd.yml` (job `desplegar-contenido-dev`) en cada push a `develop` que toque `services/contenido/productos`. Queda publicado en el puerto **8103** del host (8080 dentro del contenedor), con su MongoDB en la misma red de Compose (`MONGODB_URI`); `KEYCLOAK_JWK_SET_URI` apunta por defecto al JWKS de desarrollo de la misma red (`jwks-dev`, ver `postman/LEEME.md`) hasta que cuentas publique su Keycloak; entonces se cambia en el entorno del servidor. Salud: `http://<ip-del-host>:8103/actuator/health`.

La imagen lleva la etiqueta propia de este servicio (`TAG_PRODUCTOS`, el sha corto del push que lo cambió); las dependencias que no cambiaron conservan la etiqueta que ya tienen desplegada. Lo resuelve `resolver_etiquetas_contenido` en `scripts/cd/desplegar.sh`.
