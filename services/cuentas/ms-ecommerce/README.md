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

Los dos parámetros los crea, **sin valor**, la migración `V3__tasas_de_la_tienda.sql`
de admin-parametros (dueño de su catálogo; D-32): aparecen en el panel
**Parámetros** marcados como pendientes del PO y, hasta que tengan valor, la
tienda vende solo en COP.

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

## Pagar con créditos del juego (`/checkout/creditos`, D-44)

Método **adicional** al pago simulado (auditoría del 4-oct, cambio autorizado
n.º 5; ecommerce-carrito.yaml 1.6.0). La misma orden, las mismas claves y los
mismos pasos después de cobrar; cambia el cobro.

- **Precio.** El `precioCreditos` que el catálogo maestro publica para cada
  producto (7.2.1 del documento: «precio en créditos del juego»). No se
  convierte nada desde COP. La promoción vigente lo rebaja con el mismo
  porcentaje, a créditos enteros (mitad hacia arriba), nunca por debajo de 1
  (`CalculadoraDePrecios.enCreditos`). Un producto **premium** no tiene precio
  en créditos (productos.yaml: solo moneda real) y uno con `precioCreditos`
  ausente o cero tampoco: `409 producto-sin-precio-en-creditos`, sin orden.
  En la semilla del catálogo los precios son los de `preciosDemostracion`
  (decisión del PO del 23-sep: HÉROE 1000, ÉPICA 500, ARMA 300, ARMADURA 250,
  ÍTEM 150), editables por el administrador del catálogo.
- **Cotización** (`GET /checkout/creditos`): cada línea con su precio en
  créditos, el total, el saldo disponible del jugador (ms-finanzas
  `GET /creditos/{uid}/saldo`, con la credencial de la tienda) y el saldo
  después. La interfaz la enseña tal cual. No cobra ni crea orden.
- **Cobro** (`POST /checkout/creditos`, sin cuerpo, `Idempotency-Key`): orden
  PENDIENTE → ms-finanzas `POST /creditos/debitar` con `refId`
  `tienda-orden-{id}` → COBRADA → tiraje → entrega → COMPLETA. ms-finanzas no
  descuenta dos veces el mismo `refId` (clave única), así que el doble clic, el
  reintento con la misma clave y la tarea programada nunca cobran dos veces.
- **Saldo insuficiente** (422 de ms-finanzas): orden RECHAZADA, nada cobrado,
  carrito intacto → `402 saldo-insuficiente` con `totalCreditos`.
- **ms-finanzas no responde**: la orden queda PENDIENTE → `503
  creditos-no-disponibles`; la misma clave reintenta el mismo `refId`. Si nadie
  reintenta, antes de caducar (30 min) se concilia con
  `GET /creditos/operaciones/{refId}`: si el débito existe (la respuesta se
  perdió con el cobro hecho), la compra sigue y se entrega; si no, RECHAZADA
  sin cobro. Si ms-finanzas sigue sin responder, se espera a la siguiente vuelta.
- **Fallo parcial**: si el catálogo no reserva o el inventario no entrega, la
  orden se compensa con `POST /creditos/reversar` (mismo `refId`; el segundo
  reverso responde YA_REVERSADO) → REEMBOLSADA.
- **Sin asiento en el libro de moneda real** (`POST /transacciones`): el
  movimiento queda en el libro de créditos del jugador
  (`GET /creditos/{uid}/movimientos`, DEBITO «Compra en la tienda: …»). **Sin
  correo de confirmación**: el de 7.5 es del pago con pasarela y el contrato de
  correo solo admite monedas ISO 4217 (`correoConfirmacion` OMITIDO; ver D-44).

La tabla `ordenes` gana `forma_de_pago` (TARJETA / CREDITOS) y `moneda` pasa a
admitir nulo en una orden con créditos (V5, con restricciones que lo fijan). En
la API la orden con créditos tiene `formaDePago: CREDITOS`, `moneda: CREDITOS`
e importes enteros. ms-finanzas no cambia: la tienda usa operaciones que ya
existían (`/creditos/**`, solo `ROLE_SERVICIO`).

### Coexistencia conocida: `POST /api/v1/pagos/procesar` de ms-finanzas (#715)

Con #715 entró en develop otra pasarela simulada, dentro de ms-finanzas
(HU-PAG-001, solo token de servicio; contrato `pagos.yaml` 1.0.0, publicado en
B0 con sus defectos conocidos). **La tienda no la usa** (decisión técnica de
B5): cobra con su `PasarelaSimulada` interna y asienta el cobro con
`POST /transacciones` (`refId` = la orden), como se describe arriba. Por qué:

- el cobro de la tienda es un paso de una orden con estados, clave de
  idempotencia del jugador, compensación con reembolso y reanudación. Esa ruta
  recibe `uidUsuario`, `monto`, `moneda`, `concepto` y `refId`, **no la
  tarjeta**, y aprueba todo monto positivo: no puede aplicar las tarjetas de
  prueba de 7.5 (`0002` rechaza, `0069` pasarela caída);
- llamarla además de lo anterior duplicaría el asiento (ella también registra
  la transacción) y mandaría un segundo correo que hoy el servicio de correo
  rechaza (usa el `uid` como dirección, defecto anotado en `pagos.yaml`).

Consecuencias: ms-finanzas tiene dos caminos para dejar constancia de un cobro
en dinero real, y el de `/pagos/procesar` no tiene consumidor en la tienda;
tampoco lo tiene `frontend/app-web/src/comun/ui/resultado-pago.js` (de #715),
que pinta su respuesta: el resultado de la compra sale de la orden
(`src/cuentas/tienda-pago.js`). Si se decide unificar, el punto de cambio es
`compra/pago/PasarelaSimulada` (que pase a llamar a ms-finanzas con
`refId = orden-{id}`), sin tocar el contrato de la tienda. B5 no modificó
ms-finanzas.

## Configuración (solo variables de entorno)

| Variable | Por omisión (local) | Para qué |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | ver `application.properties` | PostgreSQL propio |
| `IDENTIDAD_JWKS_URL` | `http://localhost:8089/api/v1/auth/jwks` | verificar tokens |
| `PRODUCTOS_URL` | `http://localhost:8103` | catálogo maestro y reserva de tiraje (sin `/api/v1`) |
| `DIRECTORIO_ACTIVO_URL` · `_CLIENT_ID` · `_CLIENT_SECRET` | emisor local · vacío · vacío | credencial de servicio (ADR-005). En despliegue el secreto lo genera `desplegar.sh` (`SECRETO_SERVICIO_MS_ECOMMERCE`) |
| `INVENTARIO_BASE_URL` | `http://localhost:8102` | entregas y lo propio del jugador |
| `FINANZAS_BASE_URL` | `http://localhost:8093/api/v1` | asiento del cobro; con créditos (D-44), saldo, débito, reverso y conciliación |
| `CORREO_URL` · `IDENTIDAD_URL` | `http://localhost:8082` · `http://localhost:8089` | confirmación de compra (en el compose de despliegue: `ECOMMERCE_CORREO_URL`, `ECOMMERCE_IDENTIDAD_URL`) |
| `PARAMETROS_URL` | `http://localhost:8088/api/v1` | tasas de cambio |
| `TIENDA_CORREO_HABILITADO` | `true` | `false` solo en un entorno sin correo (banco E2E): la orden termina con correo OMITIDO |
| `TIENDA_ZONA_HORARIA` | `America/Bogota` | vencimiento de la tarjeta y fecha del correo |
| `TIENDA_REANUDACION_HABILITADA` · `TIENDA_REANUDACION_INTERVALO_MS` | `true` · `15000` | tarea que termina órdenes a medias |
| `CORS_ORIGENES` | `http://localhost:8080,http://127.0.0.1:8080` | origen del borde |

## La interfaz (`frontend/app-web/src/cuentas/`)

| Módulo | Qué hace |
|---|---|
| `tienda.js` | Vitrina, filtros, carrito con «−»/«+» (`PUT …/cantidad`), «Pagar» y «Mis compras» |
| `tienda-moneda.js` | Moneda inicial por la región de `navigator.languages`: **aproximación** a la «ubicación geográfica» de 7.5, sin geolocalización (D-33). Solo ofrece las de `monedasDisponibles`; las demás salen desactivadas y la nota dice que falta su tasa (D-32) |
| `tienda-deseos.js` | El corazón de la tarjeta y del detalle (`PUT`/`DELETE /lista-deseos/{id}`), con `aria-pressed` |
| `tienda-pago.js` | Resumen y formulario de 7.5 (`autocomplete` `cc-name`, `cc-number`, `cc-exp`, `cc-csc`), una `Idempotency-Key` por intento (la misma al reintentar tras un 503 o un 409 `compra-en-curso`), resultado por estado y «Mis compras». El número y el código solo viven en el formulario y en el cuerpo de la petición: ni almacenamiento del navegador, ni consola, ni dirección. D-44: «¿Cómo quieres pagar? ○ Créditos del Nexo ○ Pago simulado»; con créditos enseña la cotización del servidor (saldo actual, precio, saldo después), confirma con su clave y termina en «Compra realizada → Ver inventario» |

## Pruebas

```powershell
.\mvnw.cmd -B verify   # unitarias + IT con Testcontainers + pactos + JaCoCo >= 80 %
```

- `compra/CompraDeExtremoAExtremoIT`: PostgreSQL real y servidores HTTP falsos
  (`compra/ServiciosSimulados`) para productos, inventario, ms-finanzas,
  ms-identidad, correo y admin-parametros. Cubre la compra completa, la misma
  clave dos veces y a la vez, 0002, 0069 y reintento, la caducidad de una
  PENDIENTE, la reanudación tras una avería en cada paso y la compensación.
- `compra/CompraConCreditosIT` (D-44): la compra con créditos con el libro de
  ms-finanzas simulado e idempotente por `refId`: el saldo baja una vez y
  exactamente el precio, el doble clic, la misma clave, la respuesta perdida,
  la conciliación al caducar (cobrada y no cobrada), el saldo insuficiente, la
  devolución (también con ms-finanzas caído), el premium sin precio en
  créditos, la clave de la otra forma de pago y la seguridad.
- `compra/CotizadorEnCreditosTest`, `integracion/ClienteDeCreditosTest` y
  `precios/CalculadoraDePreciosTest`: la cotización, la frontera
  decisión/avería del cliente de créditos y el precio en créditos.
- `contratos/*PactoTest`: pactos de consumidor con productos (reserva) e
  inventario (entrega) en `contracts/pactos/`. La verificación de proveedor la
  añade el integrador cuando esté fusionado B4 (ver `contracts/pactos/README.md`).
- `tests/e2e/tienda.e2e.spec.js` (banco de servicios reales): compra con la
  `4242` hasta COMPLETA con el producto en el inventario, la misma clave dos
  veces, el rechazo `0002` y la compra desde la vista. Necesita B4 fusionado
  (reserva de tiraje y entregas); el banco no tiene correo, así que la orden
  termina con el correo OMITIDO (`TIENDA_CORREO_HABILITADO=false`).
- `tests/e2e/compra-con-creditos.e2e.spec.js` (D-44): la vitrina con el precio
  en créditos, la cotización, la compra con créditos (saldo antes/después, un
  solo débito en el libro, entrega), dos clics a la vez, el saldo insuficiente
  y la compra desde la vista hasta «Ver inventario».
