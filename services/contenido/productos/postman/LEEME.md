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

- El servicio en ejecucion con MongoDB y con `IDENTIDAD_JWKS_URL` apuntando al
  JWKS del emisor real, **`ms-identidad`** (ADR-002 / ADR-005). **No hay
  Keycloak ni realm en ningun entorno**: si una instruccion te manda a buscar
  uno, esta caduca.

```bash
MONGODB_URI=mongodb://localhost:27017/productos IDENTIDAD_JWKS_URL=http://localhost:8089/api/v1/auth/jwks ./gradlew :services:contenido:productos:bootRun
```

Contra el entorno desplegado, el JWKS es
`http://35.168.124.119:8089/api/v1/auth/jwks` (ms-identidad corre en el host de
plataforma; ver `docs/arquitectura/README.md`). El servicio prefiere
`IDENTIDAD_JWKS_URL`; la variable `KEYCLOAK_JWK_SET_URI` sigue leyendose solo
como respaldo del `jwks-dev` de abajo — el nombre es historico, no implica que
exista un Keycloak.

- Un **token Bearer con rol ADMINISTRADOR o SUPER_ADMINISTRADOR** (claim
  `realm_access.roles`), pegado en la variable `token` del entorno. Lo normal es
  pedirselo a `ms-identidad`. Para trabajar sin levantarlo esta el **JWKS de
  desarrollo** (abajo), que firma tokens en local: el servicio solo valida firma
  y vigencia.

## JWKS de desarrollo (firmar tokens en local, sin levantar ms-identidad)

En `docker-compose.contenido.yml` hay un servicio `jwks-dev` (nginx estático,
sin puerto en el host) que sirve la clave **pública** RSA de desarrollo en
`http://jwks-dev/certs.json`; productos cae ahí **solo** cuando no recibe
`IDENTIDAD_JWKS_URL`, por la variable de respaldo `KEYCLOAK_JWK_SET_URI`. La
clave **privada** la guarda el PO fuera del
repositorio (nunca se versiona). Herramientas en `jwks-dev/` (Node 20+, sin
dependencias):

| Script | Qué hace |
|---|---|
| `generar-claves.mjs <privada.pem>` | Genera el par RSA, deja la privada en esa ruta (permisos 600) y `jwks.json` al lado; imprime el JWKS público para pegarlo en el compose (`configs.jwks_dev.content`). Rotar la clave = volver a correrlo y abrir PR con el JWKS nuevo |
| `emitir-token.mjs <privada.pem> [--rol ADMINISTRADOR] [--usuario nombre] [--horas 8]` | Firma un JWT RS256 con `realm_access.roles` y vencimiento |
| `emitir-token.test.mjs` | Pruebas: `node --test postman/jwks-dev/emitir-token.test.mjs` |

Quien tenga la clave privada es ADMINISTRADOR de productos en el entorno de
desarrollo: es una herramienta de pruebas con datos de prueba, **no sustituye
la integración con cuentas**. Esa integración **ya existe** (R9.7): en el host
de contenido el CD reparte `IDENTIDAD_JWKS_URL` apuntando a `ms-identidad`, que
gana sobre este JWKS local. Para trabajar contra el emisor real basta con fijar
esa variable y pedirle el token a `ms-identidad`. La prueba
`SeguridadConJwksRealTest` fija el formato del JWKS contra el decodificador real
de Spring, y `SeguridadConEmisorRealTest` lo hace contra el emisor de verdad.

Contra la instancia de contenido:

```bash
TOKEN=$(node postman/jwks-dev/emitir-token.mjs ~/.nexus/productos-jwks-dev.pem --usuario cesar)
npx --yes newman run productos.postman_collection.json -e local.postman_environment.json --env-var baseUrl=http://35.168.124.119 --env-var token=$TOKEN
```

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

> **R9.4 — el `baseUrl` va por el borde, no al puerto del servicio.**
> Los puertos 8101-8104 del host de contenido dejaron de estar abiertos a todo
> internet: solo los alcanza el host de plataforma, que es quien de verdad los
> consume (el borde nginx y salas-partidas). Desde un portatil se entra por el
> borde, que es ademas el mismo camino que usa la aplicacion real, asi que la
> coleccion pasa a ejercitar tambien el enrutado.
>
> La unica peticion que no sobrevive al cambio es `{{baseUrl}}/actuator/health`:
> el borde solo enruta `/api/v1/*`. La salud por host la cubre
> `.github/workflows/diagnostico-dev.yml`.
>
> Para depurar contra el puerto directo hay que anadir la IP propia a
> `cidr_servicios` en `infrastructure/entornos/contenido/main.tf`, a proposito
> y temporalmente.
