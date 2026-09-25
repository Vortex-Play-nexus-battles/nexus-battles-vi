# ms-ecommerce

Tienda de la plataforma (sección 7.5 del documento del curso): vitrina pública
sobre el catálogo maestro, carrito, lista de deseos y **compra con una pasarela
de pagos simulada**. Maven, Java 21, Spring Boot 4.1, PostgreSQL propio.

Contrato: `contracts/openapi/ecommerce-carrito.yaml` **1.4.0**. El servicio
tiene context-path `/ecommerce`: el borde reescribe `/api/v1/...` a
`/ecommerce/api/v1/...` y la salud vive en `/ecommerce/actuator/health`.

| Método | Ruta (`/api/v1`) | Qué hace |
|---|---|---|
| GET | `/vitrina` | Productos a la venta con precio del servidor, `moneda`, filtros (`tipo`, `precioMinimo`, `precioMaximo`, `enPromocion`, `busqueda`). Pública; con sesión marca `esPropio` y `enListaDeseos` |
| GET | `/carrito` | El carrito del jugador (del `uid` del token), recotizado en la `moneda` pedida |
| POST | `/carrito/items` | Añadir un producto (cantidad 1..20, nunca más que el tiraje) |
| PUT | `/carrito/items/{itemId}/cantidad` | Cambiar la cantidad de una línea |
| DELETE | `/carrito/items/{itemId}` | Quitar una línea |
| GET · PUT · DELETE | `/lista-deseos[/{productoId}]` | Lista de deseos, idempotente |
| POST | `/checkout` | Pagar el carrito (`Idempotency-Key` obligatoria) |
| GET | `/ordenes` · `/ordenes/{id}` | Las compras del jugador (solo las suyas) |

## El precio lo pone el servidor

El navegador nunca manda un precio. Base en COP del catálogo, promoción vigente
del catálogo (porcentaje, `desde` inclusive, `hasta` exclusivo, con el reloj de
la tienda) y conversión con la tasa de admin-parametros. Redondeo explícito
`HALF_UP`: COP sin decimales, USD/EUR con dos; nunca por debajo de la unidad
mínima (1 COP, 0,01 USD/EUR). Ver `precios/CalculadoraDePrecios`.

### Tasas de cambio: decisión del PO pendiente (no se inventan)

Cuántos pesos vale un dólar o un euro no lo fija el documento. La tienda lo lee
de dos parámetros de admin-parametros (`GET /parametros/{clave}/valor`,
lectura pública, copia de 60 s con 15 min de gracia si admin-parametros cae):

| Parámetro | Significado | Ejemplo de formato |
|---|---|---|
| `tienda.tasa-cop-usd` | pesos colombianos por 1 USD | `4000` |
| `tienda.tasa-cop-eur` | pesos colombianos por 1 EUR | `4400` |

**Valor provisional: ninguno.** Mientras un parámetro no exista o no tenga
valor, esa moneda no se ofrece: la vitrina la quita de `monedasDisponibles` y
pedirla responde **422 `moneda-no-disponible`**. COP funciona siempre. Los
ejemplos de la tabla son solo de formato, no una propuesta de tasa.

Los parámetros **no existen todavía** en el catálogo de admin-parametros: ese
catálogo es de su dueño y se amplía por migración. Para crearlos (sin valor,
como `jugador.creditos-iniciales` en su `V2`), la siguiente migración libre de
`services/plataforma/admin-parametros/src/main/resources/db/migration/`:

```sql
-- Tasas de la tienda (7.5, precio en la moneda del cliente). Nacen SIN VALOR:
-- son decision del PO. Sin valor, ms-ecommerce vende solo en COP.
INSERT INTO parametros (clave, descripcion, tipo, valor, unidad, minimo, maximo, opciones, inalterable, origen, orden) VALUES
('tienda.tasa-cop-usd',
 'Pesos colombianos por 1 USD para mostrar y cobrar en dolares; vacio = la tienda no ofrece USD',
 'DECIMAL', NULL, 'COP', 1, 1000000, NULL, FALSE, 'PO pendiente / 7.5 moneda del cliente', 70),
('tienda.tasa-cop-eur',
 'Pesos colombianos por 1 EUR para mostrar y cobrar en euros; vacio = la tienda no ofrece EUR',
 'DECIMAL', NULL, 'COP', 1, 1000000, NULL, FALSE, 'PO pendiente / 7.5 moneda del cliente', 71);
```

Cuando el PO fije la tasa, un administrador la escribe desde el panel
**Parámetros** (`PUT /parametros/{clave}`, con motivo): ms-ecommerce la toma
en menos de un minuto, sin desplegar nada. Una orden guarda la tasa con la que
se cobró (`tasaDeCambio`).

## La compra (`POST /checkout`)

Tarjeta de prueba: titular (3..80), número de 12 a 19 dígitos que pase Luhn,
vencimiento `MM/AA` no pasado (zona `TIENDA_ZONA_HORARIA`), código de 3 o 4
dígitos. **Nunca se guarda ni se registra el número completo ni el código**: la
orden conserva marca y últimos cuatro; `SolicitudDePago` y `TarjetaValidada`
enmascaran su `toString`.

La pasarela es **simulada e interna** (`compra/pago/PasarelaSimulada`):

| Tarjeta | Resultado |
|---|---|
| cualquiera válida, p. ej. `4242 4242 4242 4242` | aprobada |
| terminada en `0002` (p. ej. `4000 0000 0000 0002`) | **402** `pago-rechazado`, orden RECHAZADA, carrito intacto |
| terminada en `0069` (p. ej. `4000 0000 0000 0069`) | **503** `pasarela-no-disponible`, orden PENDIENTE; reintentar con la **misma** clave |

Estados de la orden, persistidos en `ordenes` (V4):

```
PENDIENTE ──cobro──▶ COBRADA ──reserva de tiraje + entrega──▶ ENTREGADA ──asiento + correo──▶ COMPLETA
    │                   │
    ├─0002─▶ RECHAZADA  └─agotado / suspendido / entrega rechazada─▶ COMPENSACION_PENDIENTE ──reembolso──▶ REEMBOLSADA (409)
    └─0069─▶ sigue PENDIENTE (503); caduca a RECHAZADA a los 30 min si nadie reintenta
```

Pasos después de cobrar, cada uno con su clave de idempotencia derivada de la
orden, para que repetirlos nunca cobre ni entregue dos veces:

1. reservar tiraje, una unidad por llamada (`POST /productos/{id}/adquisiciones`,
   clave `orden-{id}-l{línea}-u{unidad}`);
2. entregar (`POST /inventario/entregas`, origen `COMPRA`, referencia = la orden,
   clave `orden-{id}`);
3. asentar en ms-finanzas (`POST /transacciones`, `refId` = la orden);
4. correo de confirmación con líneas y total (`POST /correos/confirmacion-compra`,
   contacto de `GET /internal/usuarios/{uid}/contacto`, clave `compra-{id}`).

Lo comprado sale del carrito **al cobrar**, en la misma transacción. Si un paso
falla después del cobro, la orden se queda en su estado intermedio y la tarea
programada (`ReanudadorDeOrdenes`, cada 15 s, espera exponencial hasta 15 min)
la termina con las mismas claves. Una orden se procesa con una concesión
(`bloqueada_hasta`) y bloqueo optimista: la petición y la tarea nunca la tienen
a la vez. Un jugador tiene una compra en curso a la vez (409 `compra-en-curso`).

Sin credencial de servicio o sin la dirección de un servicio de la compra,
`POST /checkout` responde **503 `compra-no-disponible` antes de cobrar**; la
vitrina, el carrito y la lista de deseos siguen funcionando.

## Configuración (solo variables de entorno)

| Variable | Por omisión (local) | Para qué |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | ver `application.properties` | PostgreSQL propio |
| `IDENTIDAD_JWKS_URL` | `http://localhost:8089/api/v1/auth/jwks` | verificar tokens |
| `PRODUCTOS_URL` | `http://localhost:8103` | catálogo maestro y reserva de tiraje (sin `/api/v1`) |
| `DIRECTORIO_ACTIVO_URL` · `_CLIENT_ID` · `_CLIENT_SECRET` | emisor local · vacío · vacío | credencial de servicio (ADR-005). En despliegue el secreto lo genera `desplegar.sh` (`SECRETO_SERVICIO_MS_ECOMMERCE`) |
| `INVENTARIO_BASE_URL` | `http://localhost:8102` | entregas y lo propio del jugador |
| `FINANZAS_BASE_URL` | `http://localhost:8093/api/v1` | asiento del cobro |
| `CORREO_URL` · `IDENTIDAD_URL` | `http://localhost:8082` · `http://localhost:8089` | confirmación de compra (en el compose de despliegue: `ECOMMERCE_CORREO_URL`, `ECOMMERCE_IDENTIDAD_URL`) |
| `PARAMETROS_URL` | `http://localhost:8088/api/v1` | tasas de cambio |
| `TIENDA_CORREO_HABILITADO` | `true` | `false` solo en un entorno sin correo (banco E2E): la orden termina con correo OMITIDO |
| `TIENDA_ZONA_HORARIA` | `America/Bogota` | vencimiento de la tarjeta y fecha del correo |
| `TIENDA_REANUDACION_HABILITADA` · `TIENDA_REANUDACION_INTERVALO_MS` | `true` · `15000` | tarea que termina órdenes a medias |
| `CORS_ORIGENES` | `http://localhost:8080,http://127.0.0.1:8080` | origen del borde |

## Pruebas

```powershell
.\mvnw.cmd -B verify   # unitarias + IT con Testcontainers + pactos + JaCoCo >= 80 %
```

- `compra/CompraDeExtremoAExtremoIT`: PostgreSQL real y servidores HTTP falsos
  (`compra/ServiciosSimulados`) para productos, inventario, ms-finanzas,
  ms-identidad, correo y admin-parametros. Cubre la compra completa, la misma
  clave dos veces y a la vez, 0002, 0069 y reintento, la caducidad de una
  PENDIENTE, la reanudación tras una avería en cada paso y la compensación.
- `contratos/*PactoTest`: pactos de consumidor con productos (reserva) e
  inventario (entrega) en `contracts/pactos/`. La verificación de proveedor la
  añade el integrador cuando esté fusionado B4 (ver `contracts/pactos/README.md`).
