

## API

- `GET /api/v1/productos?page=0&size=20&tipo=…&estado=…` (R16, `contracts/openapi/productos.yaml` 1.2.0): listado público y paginado del catálogo, sin token. Por omisión solo ACTIVO y UNICO (SUSPENDIDO únicamente con `estado=SUSPENDIDO`), `size` entre 1 y 50 y orden estable (`creadoEn`, luego `id`); responde `{content, page, size, totalElements, totalPages}` y lo consume ms-ecommerce para proyectar su vitrina. Ojo: en el borde el `GET` exacto de esa ruta sigue yendo a la vitrina de ms-ecommerce (#421), así que se llama directo al servicio.

## Despliegue

Este servicio se despliega en el host propio del dominio de contenido (`infrastructure/entornos/contenido/`), por el flujo `cd.yml` (job `desplegar-contenido-dev`) en cada push a `develop` que toque `services/contenido/productos`. Queda publicado en el puerto **8103** del host (8080 dentro del contenedor), con su MongoDB en la misma red de Compose (`MONGODB_URI`); `KEYCLOAK_JWK_SET_URI` apunta por defecto al JWKS de desarrollo de la misma red (`jwks-dev`, ver `postman/LEEME.md`) hasta que cuentas publique su Keycloak; entonces se cambia en el entorno del servidor. Salud: `http://<ip-del-host>:8103/actuator/health`.

La imagen lleva la etiqueta propia de este servicio (`TAG_PRODUCTOS`, el sha corto del push que lo cambió); las dependencias que no cambiaron conservan la etiqueta que ya tienen desplegada. Lo resuelve `resolver_etiquetas_contenido` en `scripts/cd/desplegar.sh`.
