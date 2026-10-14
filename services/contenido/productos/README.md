

## API

### Versiones del diseño

HU-PRD-011 reutiliza el historial auditado del catálogo para mostrar las
versiones del diseño con fecha y autor. Un administrador puede exportar el
diseño actual como JSON mediante
`GET /api/v1/productos/{id}/diseno/plantilla` e importarlo sobre otro producto
con `POST /api/v1/productos/{id}/diseno/importaciones`.

La versión 1 de la plantilla contiene únicamente `imagen` y `descripcion`.
Importarla no cambia el identificador, nombre, tipo, precios, tiraje ni reglas
de combate. El documento declara `formato` y `versionFormato` para que los
recursos gráficos de HU-PRD-009 puedan incorporarse en una versión posterior
sin reinterpretar archivos existentes. Una plantilla inválida se rechaza antes
de invocar la escritura del producto.

- `GET /api/v1/productos?page=0&size=20&tipo=…&estado=…` (R16, `contracts/openapi/productos.yaml` 1.2.0): listado público y paginado del catálogo, sin token. Por omisión solo ACTIVO y UNICO (SUSPENDIDO únicamente con `estado=SUSPENDIDO`, y desde 1.4.0 solo para un servicio o un administrador), `size` entre 1 y 50 y orden estable (`creadoEn`, luego `id`); responde `{content, page, size, totalElements, totalPages}` y lo consume ms-ecommerce para proyectar su vitrina. Desde R16 el borde manda todo `/api/v1/productos` a este servicio (la vitrina vive en `/api/v1/vitrina`).

### Recursos gráficos (HU-PRD-009, contrato 1.10.0)

La imagen de un producto ya no se escribe como ruta: se sube. `POST
/api/v1/productos/recursos` (multipart: `archivo`, `clase` y, si se sube desde
la ficha de un producto, `productoId`) guarda una imagen, un icono o una
animación después de comprobarla por sus bytes —nunca por el nombre ni por el
`Content-Type`— (`ExaminadorDeRecursos`):

| Clase | Formatos | Peso / lado máximos por omisión |
|---|---|---|
| `IMAGEN` | PNG, JPEG o WebP, fijos | 2 MB / 4096 px |
| `ICONO` | PNG o WebP, fijos | 256 KB / 512 px |
| `ANIMACION` | GIF, PNG animado (APNG) o WebP animado | 4 MB / 1024 px |

El archivo tiene que estar completo (ni cortado ni con nada pegado detrás) y el
rechazo dice el requisito incumplido, con su código en `motivo` (413 por peso o
medidas, 415 por formato, archivo dañado o imagen fija subida como animación).
Los límites se cambian por entorno (`CATALOGO_RECURSOS_*`).

`GET /api/v1/productos/recursos/{id}` sirve los bytes en público —son la
imagen que ve la tienda— con el tipo detectado, `nosniff`,
`Content-Security-Policy: default-src 'none'`, `ETag` y caché inmutable. Su
`url` es lo que se guarda en `imagen` con la modificación de siempre, que deja
respaldo, historial y versión de diseño. `GET /api/v1/productos/recursos` es la
biblioteca (16 por página, sin los bytes, con los productos que usan cada
recurso) y `DELETE /api/v1/productos/recursos/{id}?motivo=` lo retira sin
borrarlo: una versión anterior del diseño puede apuntar a él. La imagen actual
de un producto no se retira (409).

### Motivo de las acciones críticas (§7.2.5, contrato 1.10.0)

Suspender (`PUT …/suspender?motivo=`), reactivar (`PUT …/reactivar?motivo=`) y
revertir (`POST …/reversiones` con `motivo`) guardan el motivo en la entrada del
historial (`CambioProducto.motivo`). La consola lo exige; la API lo admite vacío
para no romper a quien ya llamaba.

### Operaciones administrativas para la bitácora central (SA-18, contrato 1.12.0)

Cada operación de un administrador sobre el catálogo —alta, modificación,
cambio de balance, importación de diseño, suspensión, reactivación y reversión
de un producto; alta y retiro de un recurso gráfico; publicación, edición y
retiro de un banner— se registra en `operaciones_administrativas` con su
momento, el `uid` de quien la hizo, lo afectado y su nombre, los campos que
cambiaron (antes y después), el motivo y la IP que fija el borde. Se escribe
después de aplicarla, y la semilla no se registra (no es una persona).

`GET /api/v1/productos/auditoria/operaciones?desde=&limite=` las entrega por
lotes a la bitácora central (ms-cumplimiento), que las guarda inmutables: solo
con token de servicio y solo para los `client_id` de `AUDITORIA_LECTORES`
(`ms-cumplimiento` por omisión). Por qué la bitácora lee y no al revés, en
`docs/gobierno/ADR-009-la-bitacora-importa-las-operaciones-del-catalogo.md`.

Los banners guardan `creadoPor` y `modificadoPor`; `GET
/api/v1/banners/{id}/historial` da su historial a la administración (sin la IP)
y `DELETE /api/v1/banners/{id}?motivo=` lo deja en el retiro.

## Despliegue

Este servicio se despliega en el host propio del dominio de contenido (`infrastructure/entornos/contenido/`), por el flujo `cd.yml` (job `desplegar-contenido-dev`) en cada push a `develop` que toque `services/contenido/productos`. Queda publicado en el puerto **8103** del host (8080 dentro del contenedor), con su MongoDB en la misma red de Compose (`MONGODB_URI`); `KEYCLOAK_JWK_SET_URI` apunta por defecto al JWKS de desarrollo de la misma red (`jwks-dev`, ver `postman/LEEME.md`) hasta que cuentas publique su Keycloak; entonces se cambia en el entorno del servidor. Salud: `http://<ip-del-host>:8103/actuator/health`.

La imagen lleva la etiqueta propia de este servicio (`TAG_PRODUCTOS`, el sha corto del push que lo cambió); las dependencias que no cambiaron conservan la etiqueta que ya tienen desplegada. Lo resuelve `resolver_etiquetas_contenido` en `scripts/cd/desplegar.sh`.

## Catalogo inicial

Al arrancar, `SemillaDelCatalogo` crea los productos del catalogo inicial que todavia no existan: 8 heroes, 16 armas, 16 armaduras, 8 items y 8 epicas, extraidos literalmente de las reglas del curso (Tablas 6 y 8 a 20; archivo `src/main/resources/semilla/catalogo-inicial.json`, cada entrada con su `fuente`). Correrla dos veces no duplica ni cambia nada. La lista canonica esta en `contracts/esquemas/catalogo-oficial.yaml` y el guardian `tests/contratos/catalogo-oficial.py` falla si el JSON se aparta de ella.

- **Semilla versionada (B4).** El JSON declara la `version` de su contenido y cada producto sembrado guarda `origen=SEMILLA` y `semillaVersion`. Si el archivo sube de version, al arrancar se ponen al dia los productos sembrados con una version menor que ningun administrador edito (conservan su estado, su fecha de alta, su promocion y sus reservas); los que edito un administrador (`modificadoPor`) se respetan y quedan en la bitacora como aviso. Quien cambie un producto del JSON sube `version`: sin eso el cambio solo llega a las bases nuevas.

- **Precios de demostracion, no del curso.** El curso no fija precios (RF-ADM-03: el producto tiene precio en creditos y en moneda real, y los fija el administrador). Los dos precios por tipo (creditos: heroe 1000, arma 300, armadura 250, item 150; pesos COP en `precioMonedaReal`: heroe 20000, arma 6000, armadura 5000, item 3000), el tiraje ilimitado (-1) y `premium: false` son decision del PO para la demo del 24-sep, en el bloque `preciosDemostracion` del JSON. El precio en pesos va aunque el producto no sea premium porque la tienda solo muestra productos con `precioMonedaReal` mayor que cero. Se cambian despues con la modificacion de productos.
- **Las epicas no se venden (RG-085, RF-MOT-36).** El cliente dijo que las epicas solo se obtienen derrotando al Master que la tiene, asi que la semilla las deja sin precio: `precioCreditos` 0 y `precioMonedaReal` 0, `premium` false, sin entrada de epica en `preciosDemostracion`. Siguen siendo productos EPICA activos con el mismo id, porque las misiones las entregan por `productoId`; la tienda (la vitrina de ms-ecommerce) no las ofrece porque solo muestra productos con `precioMonedaReal` mayor que cero. La regla vive en `MapeadorDelCatalogo` y no en el JSON: aunque alguien ponga un precio de epica en `preciosDemostracion`, la semilla no se lo aplica. Esto entro con la `version` 2 del contenido: una base que sembro la 1 (con las epicas a 500 creditos y 10.000 COP) las pone sin precio en el siguiente arranque, salvo la epica que un administrador haya editado, que se respeta y queda como aviso en la bitacora (si esa edicion fue ponerle precio, el administrador decide si la quita). Tampoco se le puede poner precio por la API: `POST /api/v1/productos` y `PUT`/`PATCH` rechazan con 400 (problem detail «Solicitud inválida») una epica con `precioCreditos` mayor que 0, `precioMonedaReal` mayor que 0 o `premium` verdadero (`SolicitudCrearProducto.isEpicaSinPrecio`, la misma solicitud que valida el alta, la fusion de la modificacion y la semilla). Dejarle el precio en 0 a una epica que quedo con uno si se puede; editarle otra cosa mientras conserve el precio, no: primero hay que dejarlo en cero.
- **Identificadores.** Cada producto recibe un UUID estable derivado del slug del JSON; la tabla completa esta en [`docs/catalogo-inicial-identificadores.md`](docs/catalogo-inicial-identificadores.md). Inventario puede usar esos valores como `productoId`.
- **Valores que la regla no trae.** Las armas cuya regla no suma "+N al ataque" (13 de 16) quedan con `poderDeAtaque` 1, el minimo que acepta el alta; su efecto real esta en la descripcion. Las epicas llevan los dos turnos de recarga de la regla general de epicas. La imagen es una figura provisional por tipo, embebida en el propio producto.
- **Como se desactiva.** La controla `catalogo.semilla.habilitada`, que lee la variable `CATALOGO_SEMILLA` y desde B4 vale `true` por omision en todos los entornos (antes solo la encendia `docker-compose.contenido.yml`); para apagarla basta con `CATALOGO_SEMILLA=false` en su entorno. Apagarla no borra lo ya sembrado. Las pruebas la apagan en `src/test/resources/config/application.properties`, salvo `CatalogoContraMongoIT`, que arranca sobre una base vacia con la semilla encendida. El banco E2E (`tests/e2e/compose.yml`) tambien la apaga: tiene su catalogo propio (`sembrar.sh`) y la vista de la tienda pinta solo la primera pagina de la vitrina, que con los productos oficiales dejaria fuera el producto de `tienda.e2e.spec.js`.

## Administracion (B4, contrato 1.4.0)

- `PUT /api/v1/productos/{id}/suspender` y `/reactivar` (ADMINISTRADOR, SUPER_ADMINISTRADOR): suspension logica, idempotente; el tiraje no cambia.
- `POST /api/v1/productos/{id}/adquisiciones` (solo token de servicio, `Idempotency-Key` obligatoria): reserva atomica de una unidad con `findAndModify`; -1 es ilimitado y en 0 responde 409 AGOTADO. La clave se guarda con su resultado en la coleccion `adquisiciones` y, en el propio producto (`reservasRecientes`, ultimas 100), en la misma escritura que descuenta la unidad: repetir la clave nunca descuenta dos veces.
- `version` es `@Version` de Spring Data: un PATCH que choca con otra escritura responde 409; suspender, reactivar y reservar suben la version. El respaldo de HU-PRD-003 guarda el autor (`uid` del token).
- **Reversión auditada (HU-PRD-007).** Cada creación, modificación, suspensión, reactivación y reversión administrativa deja una entrada con producto, autor, fecha, estado anterior, estado aplicado y campos cambiados. `GET /api/v1/productos/{id}/historial` es de solo lectura y `POST /api/v1/productos/{id}/reversiones` restaura únicamente el cambio que produjo la versión actual; un respaldo obsoleto responde 409 para no pisar reservas u otras escrituras posteriores. Las unidades de inventario conservan el `productoId`, de modo que proyectan el estado restaurado sin copiar datos del catálogo.
- Promociones: `promocion {porcentaje 1..90, desde, hasta}` opcional al crear y modificar; la vigencia la calcula el servidor.
- Proyeccion publica: sin token, `GET /api/v1/productos` y `/{id}` no muestran productos SUSPENDIDO ni `version`, `tasaDeCaida`, `origen`, `semillaVersion` o `modificadoPor`; un jugador ve un SUSPENDIDO por su id (puede tenerlo en su inventario) sin los internos; servicio y administradores ven todo.
