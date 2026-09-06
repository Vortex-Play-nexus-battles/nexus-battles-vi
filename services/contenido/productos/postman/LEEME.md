# Validacion con Postman del servicio de productos

Coleccion con **aserciones** para lo que HU-PRD-002 (tiraje limitado) expone
por API hoy: el registro del tiraje al crear un producto.

| Criterio de HU-PRD-002 | ¿Probable por HTTP? |
|---|---|
| -1 identifica disponibilidad ilimitada; N > 0 son unidades exactas | **Si**: peticiones 3 a 7 |
| Un producto agotado no se puede adquirir e informa que esta agotado | **No**: no existe endpoint de adquisicion |
| Dos adquisiciones simultaneas de la ultima unidad: solo una prospera | **No**: mismo motivo |

HU-PRD-004 (suspension y reactivacion) tampoco expone endpoint: `suspender` y
`reactivar` existen solo en el dominio (`CatalogoProductos`). Cuando se
publiquen, agregar aqui sus peticiones.

## Archivos

| Archivo | Que es |
|---|---|
| `productos.postman_collection.json` | 11 peticiones |
| `local.postman_environment.json` | `baseUrl` y `token` |

## Requisitos

- El servicio en ejecucion con MongoDB y con `KEYCLOAK_JWK_SET_URI` apuntando
  al JWKS del Keycloak de la plataforma:

```bash
MONGODB_URI=mongodb://localhost:27017/productos KEYCLOAK_JWK_SET_URI=https://<keycloak>/realms/<realm>/protocol/openid-connect/certs ./gradlew :services:contenido:productos:bootRun
```

- Un **token Bearer con rol ADMINISTRADOR o SUPER_ADMINISTRADOR** (claim
  `realm_access.roles`), pegado en la variable `token` del entorno. Sin
  Keycloak disponible se puede usar un JWKS local y un token firmado con esa
  clave: el servicio solo valida firma y vigencia.

## Con la app de Postman

1. **Import** y arrastrar los dos archivos.
2. Entorno `productos - local`: poner `baseUrl` y `token`.
3. Clic derecho sobre la coleccion → **Run collection** → **Run**.

## Desde consola (Newman)

```bash
npx --yes newman run productos.postman_collection.json -e local.postman_environment.json --env-var token=<JWT>
```

## Que cubre

| Peticion | Verifica |
|---|---|
| Salud | `/actuator/health` es publica y responde UP |
| Crear sin token | 401 con formato de error estandar, titulo "No autenticado" |
| Tiraje -1 | 201, `tiraje` -1, estado ACTIVO, version 1, id UUID, Location |
| Consulta publica | `GET /{id}` sin token devuelve el producto |
| Tiraje 5 | 201 con 5 unidades exactas |
| Tiraje 0 y -5 | 400, el detalle menciona el tiraje |
| Premium sin precio real | 400 |
| Inexistente | 404 con formato de error estandar |
| Estadisticas | 200 con token (cuenta los creados); 401 sin token |
