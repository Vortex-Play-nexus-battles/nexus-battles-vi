

## API

- `GET /api/v1/productos?page=0&size=20&tipo=…&estado=…` (R16, `contracts/openapi/productos.yaml` 1.2.0): listado público y paginado del catálogo, sin token. Por omisión solo ACTIVO y UNICO (SUSPENDIDO únicamente con `estado=SUSPENDIDO`, y desde 1.4.0 solo para un servicio o un administrador), `size` entre 1 y 50 y orden estable (`creadoEn`, luego `id`); responde `{content, page, size, totalElements, totalPages}` y lo consume ms-ecommerce para proyectar su vitrina. Desde R16 el borde manda todo `/api/v1/productos` a este servicio (la vitrina vive en `/api/v1/vitrina`).

## Despliegue

Este servicio se despliega en el host propio del dominio de contenido (`infrastructure/entornos/contenido/`), por el flujo `cd.yml` (job `desplegar-contenido-dev`) en cada push a `develop` que toque `services/contenido/productos`. Queda publicado en el puerto **8103** del host (8080 dentro del contenedor), con su MongoDB en la misma red de Compose (`MONGODB_URI`); `KEYCLOAK_JWK_SET_URI` apunta por defecto al JWKS de desarrollo de la misma red (`jwks-dev`, ver `postman/LEEME.md`) hasta que cuentas publique su Keycloak; entonces se cambia en el entorno del servidor. Salud: `http://<ip-del-host>:8103/actuator/health`.

La imagen lleva la etiqueta propia de este servicio (`TAG_PRODUCTOS`, el sha corto del push que lo cambió); las dependencias que no cambiaron conservan la etiqueta que ya tienen desplegada. Lo resuelve `resolver_etiquetas_contenido` en `scripts/cd/desplegar.sh`.

## Catalogo inicial

Al arrancar, `SemillaDelCatalogo` crea los productos del catalogo inicial que todavia no existan: 8 heroes, 16 armas, 16 armaduras, 8 items y 8 epicas, extraidos literalmente de las reglas del curso (Tablas 6 y 8 a 20; archivo `src/main/resources/semilla/catalogo-inicial.json`, cada entrada con su `fuente`). Correrla dos veces no duplica ni cambia nada. La lista canonica esta en `contracts/esquemas/catalogo-oficial.yaml` y el guardian `tests/contratos/catalogo-oficial.py` falla si el JSON se aparta de ella.

- **Semilla versionada (B4).** El JSON declara la `version` de su contenido y cada producto sembrado guarda `origen=SEMILLA` y `semillaVersion`. Si el archivo sube de version, al arrancar se ponen al dia los productos sembrados con una version menor que ningun administrador edito (conservan su estado, su fecha de alta, su promocion y sus reservas); los que edito un administrador (`modificadoPor`) se respetan y quedan en la bitacora como aviso. Quien cambie un producto del JSON sube `version`: sin eso el cambio solo llega a las bases nuevas.

- **Precios de demostracion, no del curso.** El curso no fija precios (RF-ADM-03: el producto tiene precio en creditos y en moneda real, y los fija el administrador). Los dos precios por tipo (creditos: heroe 1000, epica 500, arma 300, armadura 250, item 150; pesos COP en `precioMonedaReal`: heroe 20000, epica 10000, arma 6000, armadura 5000, item 3000), el tiraje ilimitado (-1) y `premium: false` son decision del PO para la demo del 24-sep, en el bloque `preciosDemostracion` del JSON. El precio en pesos va aunque el producto no sea premium porque la tienda solo muestra productos con `precioMonedaReal` mayor que cero. Se cambian despues con la modificacion de productos.
- **Identificadores.** Cada producto recibe un UUID estable derivado del slug del JSON; la tabla completa esta en [`docs/catalogo-inicial-identificadores.md`](docs/catalogo-inicial-identificadores.md). Inventario puede usar esos valores como `productoId`.
- **Valores que la regla no trae.** Las armas cuya regla no suma "+N al ataque" (13 de 16) quedan con `poderDeAtaque` 1, el minimo que acepta el alta; su efecto real esta en la descripcion. Las epicas llevan los dos turnos de recarga de la regla general de epicas. La imagen es una figura provisional por tipo, embebida en el propio producto.
- **Como se desactiva.** La controla `catalogo.semilla.habilitada`, que lee la variable `CATALOGO_SEMILLA` y desde B4 vale `true` por omision en todos los entornos (antes solo la encendia `docker-compose.contenido.yml`); para apagarla basta con `CATALOGO_SEMILLA=false` en su entorno. Apagarla no borra lo ya sembrado. Las pruebas la apagan en `src/test/resources/config/application.properties`, salvo `CatalogoContraMongoIT`, que arranca sobre una base vacia con la semilla encendida.

## Administracion (B4, contrato 1.4.0)

- `PUT /api/v1/productos/{id}/suspender` y `/reactivar` (ADMINISTRADOR, SUPER_ADMINISTRADOR): suspension logica, idempotente; el tiraje no cambia.
- `POST /api/v1/productos/{id}/adquisiciones` (solo token de servicio, `Idempotency-Key` obligatoria): reserva atomica de una unidad con `findAndModify`; -1 es ilimitado y en 0 responde 409 AGOTADO. La clave se guarda con su resultado en la coleccion `adquisiciones` y, en el propio producto (`reservasRecientes`, ultimas 100), en la misma escritura que descuenta la unidad: repetir la clave nunca descuenta dos veces.
- `version` es `@Version` de Spring Data: un PATCH que choca con otra escritura responde 409; suspender, reactivar y reservar suben la version. El respaldo de HU-PRD-003 guarda el autor (`uid` del token).
- Promociones: `promocion {porcentaje 1..90, desde, hasta}` opcional al crear y modificar; la vigencia la calcula el servidor.
- Proyeccion publica: sin token, `GET /api/v1/productos` y `/{id}` no muestran productos SUSPENDIDO ni `version`, `tasaDeCaida`, `origen`, `semillaVersion` o `modificadoPor`; un jugador ve un SUSPENDIDO por su id (puede tenerlo en su inventario) sin los internos; servicio y administradores ven todo.
