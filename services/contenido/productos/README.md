

## API

- `GET /api/v1/productos?page=0&size=20&tipo=…&estado=…` (R16, `contracts/openapi/productos.yaml` 1.2.0): listado público y paginado del catálogo, sin token. Por omisión solo ACTIVO y UNICO (SUSPENDIDO únicamente con `estado=SUSPENDIDO`), `size` entre 1 y 50 y orden estable (`creadoEn`, luego `id`); responde `{content, page, size, totalElements, totalPages}` y lo consume ms-ecommerce para proyectar su vitrina. Ojo: en el borde el `GET` exacto de esa ruta sigue yendo a la vitrina de ms-ecommerce (#421), así que se llama directo al servicio.

## Despliegue

Este servicio se despliega en el host propio del dominio de contenido (`infrastructure/entornos/contenido/`), por el flujo `cd.yml` (job `desplegar-contenido-dev`) en cada push a `develop` que toque `services/contenido/productos`. Queda publicado en el puerto **8103** del host (8080 dentro del contenedor), con su MongoDB en la misma red de Compose (`MONGODB_URI`); `KEYCLOAK_JWK_SET_URI` apunta por defecto al JWKS de desarrollo de la misma red (`jwks-dev`, ver `postman/LEEME.md`) hasta que cuentas publique su Keycloak; entonces se cambia en el entorno del servidor. Salud: `http://<ip-del-host>:8103/actuator/health`.

La imagen lleva la etiqueta propia de este servicio (`TAG_PRODUCTOS`, el sha corto del push que lo cambió); las dependencias que no cambiaron conservan la etiqueta que ya tienen desplegada. Lo resuelve `resolver_etiquetas_contenido` en `scripts/cd/desplegar.sh`.

## Catalogo inicial

Al arrancar, `SemillaDelCatalogo` crea los productos del catalogo inicial que todavia no existan: 8 heroes, 16 armas, 16 armaduras, 8 items y 8 epicas, extraidos literalmente de las reglas del curso (Tablas 6 y 8 a 20; archivo `src/main/resources/semilla/catalogo-inicial.json`, cada entrada con su `fuente`). No modifica nunca un producto que ya exista, aunque un administrador lo haya editado, y correrla dos veces no duplica nada.

- **Precios de demostracion, no del curso.** El curso no fija precios (RF-ADM-03: los fija el administrador). Los creditos por tipo, el tiraje ilimitado (-1) y `premium: false` son decision del PO para la demo del 24-sep, en el bloque `preciosDemostracion` del JSON. Se cambian despues con la modificacion de productos.
- **Identificadores.** Cada producto recibe un UUID estable derivado del slug del JSON; la tabla completa esta en [`docs/catalogo-inicial-identificadores.md`](docs/catalogo-inicial-identificadores.md). Inventario puede usar esos valores como `productoId`.
- **Valores que la regla no trae.** Las armas cuya regla no suma "+N al ataque" (13 de 16) quedan con `poderDeAtaque` 1, el minimo que acepta el alta; su efecto real esta en la descripcion. Las epicas llevan los dos turnos de recarga de la regla general de epicas. La imagen es una figura provisional por tipo, embebida en el propio producto.
- **Como se desactiva.** La controla `catalogo.semilla.habilitada`, que lee la variable `CATALOGO_SEMILLA` y vale `false` por omision. `docker-compose.contenido.yml` la enciende (`CATALOGO_SEMILLA: ${CATALOGO_SEMILLA:-true}`); para apagarla en el servidor basta con `CATALOGO_SEMILLA=false` en su entorno. Apagarla no borra lo ya sembrado.
