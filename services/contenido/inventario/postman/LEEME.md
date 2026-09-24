# Validacion con Postman del servicio de inventario

Coleccion con **aserciones** para HU-INV-002 (busqueda indexada), HU-INV-003
(creacion y edicion de elementos propios), HU-INV-005 (equipamiento con limites)
y HU-INV-010 (bloqueo por subasta). Cada peticion verifica el criterio de
aceptacion que le corresponde y las peticiones estan encadenadas:
cada una guarda los identificadores que usan las siguientes, asi que se corre
la coleccion **completa y en orden**.

## Archivos

| Archivo | Que es |
|---|---|
| `inventario.postman_collection.json` | 54 peticiones agrupadas por historia |
| `local.postman_environment.json` | Entorno local: `baseUrl` |

## Requisitos

- El servicio en ejecucion con un MongoDB alcanzable. Desde la raiz del monorepo:

```bash
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/inventario ./gradlew :services:contenido:inventario:bootRun
```

- **El catalogo inicial sembrado en productos** (contrato de inventario 1.2.0).
  Crear un elemento consulta el producto en el servicio de productos y solo
  acepta ids que existen, no suspendidos y del mismo tipo. La coleccion usa
  ids del catalogo inicial (`heroe-guerrero-tanque`,
  `arma-guerrero-tanque-espada-de-una-mano`, `armadura-mago-hielo-corona-de-hielo`,
  `item-medico-benditas`, ...); sin la semilla, cada creacion responde 422
  "Producto inexistente" y la coleccion se cae en cascada. Inventario encuentra
  a productos por `PRODUCTOS_BASE_URL`; si productos no responde, crear
  responde 503 "Catalogo no disponible".
- Las rutas del jugador conservan temporalmente `X-User-Name`. La coleccion
  genera dos UUID nuevos para que cada corrida use inventarios independientes.
- Para las operaciones internas, obtener con OAuth2 `client_credentials` un
  access token cuyo cliente sea `ms-subastas` y asignarlo a la variable de
  coleccion `s2sAccessToken`.

  **Contrato 1.1.0 (#451): la identidad del jugador sale del Bearer.** Cada
  peticion de jugador lleva `Authorization: Bearer {{tokenJugadorA}}` (o `B`);
  `X-User-Name` se conserva solo para servicios con credencial. Los apodos
  `jugadorA`/`jugadorB` deben coincidir con el `preferred_username` de cada
  token. Con el JWKS de desarrollo:

  ```bash
  A=jugador-a-$RANDOM; B=jugador-b-$RANDOM
  TA=$(node ../../productos/postman/jwks-dev/emitir-token.mjs ~/.nexus/productos-jwks-dev.pem --rol JUGADOR --usuario $A)
  TB=$(node ../../productos/postman/jwks-dev/emitir-token.mjs ~/.nexus/productos-jwks-dev.pem --rol JUGADOR --usuario $B)
  S2S=$(node ../../productos/postman/jwks-dev/emitir-token.mjs ~/.nexus/productos-jwks-dev.pem --azp ms-subastas)
  npx --yes newman run inventario.postman_collection.json -e local.postman_environment.json --env-var baseUrl=http://35.168.124.119 --env-var jugadorA=$A --env-var tokenJugadorA=$TA --env-var jugadorB=$B --env-var tokenJugadorB=$TB --env-var s2sAccessToken=$S2S
  ```

  **Sin Keycloak (instancia de contenido y local con Compose):** inventario
  apunta al JWKS de desarrollo (`jwks-dev`, ver
  `services/contenido/productos/postman/LEEME.md`) y el token de servicio se
  emite con la clave privada de desarrollo:

  ```bash
  S2S=$(node ../../productos/postman/jwks-dev/emitir-token.mjs ~/.nexus/productos-jwks-dev.pem --azp ms-subastas)
  npx --yes newman run inventario.postman_collection.json -e local.postman_environment.json --env-var baseUrl=http://35.168.124.119 --env-var s2sAccessToken=$S2S
  ``` El token autentica al servicio y
  `propietarioUid` viaja por separado como dato del negocio.

## Con la app de Postman

1. **Import** y arrastrar los dos archivos.
2. Elegir el entorno `inventario - local` (ajustar `baseUrl` si el servicio
   corre en otro puerto).
3. Clic derecho sobre la coleccion → **Run collection** → **Run**. Todo debe
   quedar en verde; cada asercion dice que criterio verifica.

## Desde consola (Newman)

```bash
npx --yes newman run inventario.postman_collection.json -e local.postman_environment.json
```

Contra otro puerto: `--env-var baseUrl=http://localhost:8082`.

## Que cubre

| Historia | Criterio | Peticiones |
|---|---|---|
| HU-INV-002 | Buscar con minimo cuatro caracteres e indice | Coincidencia propia → 200; criterio corto → 400; sin identidad → 401 |
| HU-INV-003 | Crear y modificar quedan persistidos y se ven en la vitrina | Crear heroe (201 + Location), renombrar (200), vitrina en pagina de 16 |
| HU-INV-003 | Operar sobre el inventario de otro no se permite | A renombra el elemento de B → 403 "Inventario ajeno"; B sigue intacto |
| HU-INV-003 | Errores legibles | 401 sin identidad, 400 sin nombre, 404 inexistente |
| HU-INV-003 | Solo productos del catalogo (RG-074) | Producto inventado `espada-corta` → 422 "Producto inexistente"; heroe pedido como ARMA → 400 "Tipo no coincide" |
| HU-INV-005 | Maximo dos armas | 2 equipan, la tercera → 409 "dos armas"; la misma dos veces → 409 |
| HU-INV-005 | Seis partes de armadura, una por ranura | CASCO ocupa, segundo CASCO → 409; PECHO cabe |
| HU-INV-005 | Maximo dos items | 2 equipan, el tercero → 409 |
| HU-INV-005 | Solo se equipan armas, armaduras e items | Equipar el heroe → 400 "Elemento no equipable" |
| HU-INV-005 | Liberar ranura | Desequipar el arma 1 → cabe el arma 3 |
| HU-INV-005 | Propiedad | B consulta el heroe de A → 403; sin identidad → 401 |
| HU-INV-010 | Bloqueo y liberacion por subasta | Reserva → 200; queda no disponible; aviso ajeno → 409; cierre/cancelacion → 200 y vuelve a estar disponible |
| HU-INV-010 | Consulta interna de una unidad | Bearer de `ms-subastas` → 200 con `productoId`, `propietarioUid` y `enUso` |
| HU-INV-010 | No se vende dos veces | Repetir la misma reserva → 200; reservar para otra subasta → 409 |
| HU-INV-010 | Operaciones bloqueadas | Modificar y eliminar el producto reservado → 409 "Producto no disponible" |
| HU-INV-010 | Falla conservadora | Sin aviso del servicio de subastas, una nueva consulta conserva `disponible: false` y el mismo `subastaId` |

Cuando cambie el contrato (`contracts/openapi/inventario.yaml`), actualizar
aqui la peticion afectada en el mismo cambio.

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
