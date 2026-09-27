/**
 * Traduccion de `ProductoVitrinaDto` a lo que pinta la vitrina — FI-R2.
 *
 * ## Por que existe este archivo
 *
 * `tienda.js` leia `producto.precio`. Ese campo **no existe**: `ms-ecommerce`
 * devuelve `precioFinal` y `precioOriginal` (ver `ProductoVitrinaDto` y
 * `VitrinaService.convertirADto`). `undefined ?? 0` daba cero, y la tarjeta
 * decia «0 COP» para todos los productos del catalogo. Nadie lo noto porque
 * `tienda.test.js` montaba sus productos con `{ precio: 100 }` — la prueba
 * confirmaba la suposicion del frontend en vez del contrato del servicio.
 *
 * La leccion, que es el motivo de que la traduccion viva aparte y no dentro
 * del render: mientras el DTO y la vista compartan el mismo objeto, cualquiera
 * puede escribir `producto.loQueSea` y no fallara hasta que un jugador lo vea.
 * Aqui la traduccion es explicita y se prueba contra la forma real del DTO.
 *
 * ## Lo que este modulo NO hace
 *
 * No calcula descuentos ni convierte monedas. Desde B5 (contrato 1.4.0) el
 * servidor aplica la promocion vigente del catalogo y la tasa de cambio, y
 * devuelve `precioFinal` ya rebajado junto al `precioOriginal`. Calcularlo
 * aqui seria calcular en el navegador lo que cobra el servidor. Por eso el
 * distintivo de promocion solo sale cuando los dos precios **de verdad**
 * difieren: un «-20%» junto a un precio sin descuento es una promesa que el
 * carrito no va a cumplir.
 *
 * @module tienda-adaptador
 */

/**
 * Un importe del DTO, que llega como `BigDecimal` serializado (numero o
 * cadena), convertido a numero — o null si no vino.
 *
 * `null` y no `0`: cero es un precio, y un producto gratis no es lo mismo que
 * un producto cuyo precio no sabemos.
 *
 * @param {unknown} valor
 * @returns {number|null}
 */
export function aImporte(valor) {
  if (valor === null || valor === undefined || valor === '') {
    return null;
  }
  const n = typeof valor === 'number' ? valor : Number(valor);
  return Number.isFinite(n) ? n : null;
}

/**
 * Importe con separadores es-CO y su moneda, o null si falta el importe.
 *
 * Sin `moneda` se ensena la cifra sola: inventar «COP» en un producto cuyo
 * precio pudo venir en dolares o euros (§7.5 del enunciado: «COP o dolar o
 * euro dependiendo de su ubicacion geografica») seria decirle al jugador que
 * paga en una moneda que no es la suya.
 *
 * @param {number|null} importe
 * @param {string|null} moneda
 * @returns {string|null}
 */
export function textoDePrecio(importe, moneda) {
  if (importe === null) {
    return null;
  }
  const cifra = importe.toLocaleString('es-CO');
  return moneda ? `${cifra} ${moneda}` : cifra;
}

/**
 * `ProductoDeVitrina` -> modelo de la tarjeta.
 *
 * R16 — el `id` es el UUID del catalogo maestro, en texto, y se devuelve tal
 * cual: es lo que `tienda.js` manda a `POST /carrito/items`. Convertirlo en
 * numero lo romperia (`Number('3f2a…')` es `NaN`).
 *
 * @param {object} dto  tal cual lo devuelve GET /api/v1/vitrina
 *   (`ProductoDeVitrina` en ecommerce-carrito.yaml 1.2.0)
 * @returns {{
 *   id: (string|number|null),
 *   nombre: string,
 *   descripcion: string,
 *   habilidades: string|null,
 *   tipo: string,
 *   imagenUrl: string|null,
 *   precio: number|null,
 *   precioAnterior: number|null,
 *   moneda: string|null,
 *   precioTexto: string|null,
 *   precioAnteriorTexto: string|null,
 *   descuento: number|null,
 *   esPropio: boolean,
 *   enListaDeseos: boolean,
 * }}
 */
export function aProductoDeVitrina(dto = {}) {
  const precio = aImporte(dto.precioFinal);
  const original = aImporte(dto.precioOriginal);
  const moneda = typeof dto.moneda === 'string' && dto.moneda.trim() ? dto.moneda.trim() : null;

  // Solo hay precio anterior que tachar si el actual es realmente menor.
  const hayRebaja = precio !== null && original !== null && original > precio;
  const porcentaje = Number.isFinite(dto.porcentajeDescuento) ? dto.porcentajeDescuento : null;

  return {
    id: dto.id ?? null,
    nombre: typeof dto.nombre === 'string' ? dto.nombre : '',
    descripcion: typeof dto.descripcion === 'string' ? dto.descripcion : '',
    habilidades:
      typeof dto.habilidades === 'string' && dto.habilidades.trim() ? dto.habilidades : null,
    tipo: typeof dto.tipo === 'string' ? dto.tipo : '',
    imagenUrl: typeof dto.imagenUrl === 'string' && dto.imagenUrl.trim() ? dto.imagenUrl : null,
    precio,
    precioAnterior: hayRebaja ? original : null,
    moneda,
    precioTexto: textoDePrecio(precio, moneda),
    precioAnteriorTexto: hayRebaja ? textoDePrecio(original, moneda) : null,
    // El porcentaje acompana a una rebaja real; suelto seria un reclamo sin
    // respaldo en lo que se va a cobrar.
    descuento: hayRebaja && porcentaje && porcentaje > 0 ? porcentaje : null,
    esPropio: dto.esPropio === true,
    enListaDeseos: dto.enListaDeseos === true,
  };
}

/** Unidades por linea que admite el carrito (ecommerce-carrito.yaml 1.4.0). */
export const MAXIMO_POR_LINEA = 20;

/**
 * Por que una linea no se puede pagar, dicho para el jugador. La clave es el
 * `motivo` de `LineaDeCarrito` (1.4.0).
 */
const MOTIVOS_DE_LINEA = Object.freeze({
  NO_DISPONIBLE: 'Ya no está a la venta. Quítalo para pagar.',
  AGOTADO: 'Se agotó. Quítalo para pagar.',
  SIN_PRECIO_EN_MONEDA_REAL: 'Ya no se vende con dinero real. Quítalo para pagar.',
  TIRAJE_INSUFICIENTE: 'No quedan tantas unidades. Baja la cantidad para pagar.',
});

/**
 * Un item del carrito -> lo que pinta la fila.
 *
 * El carrito trae los importes del servidor: `precioUnitario`, `subtotal` y
 * el `total` del carrito. Desde 1.4.0 ademas dice si la linea se puede pagar
 * (`disponible`, con su `motivo`) y cuantas unidades admite (`maximo`: 20, o
 * lo que quede del tiraje).
 *
 * @param {object} item
 * @param {string|null} moneda
 */
export function aFilaDeCarrito(item = {}, moneda = null) {
  const subtotal = aImporte(item.subtotal);
  const unitario = aImporte(item.precioUnitario);
  const cantidad = Number.isFinite(item.cantidad) ? item.cantidad : 1;
  // Cero es un maximo (agotado); sin el campo, el tope del contrato.
  const maximo =
    Number.isInteger(item.maximo) && item.maximo >= 0
      ? Math.min(item.maximo, MAXIMO_POR_LINEA)
      : MAXIMO_POR_LINEA;
  const disponible = item.disponible !== false;
  const imagen =
    typeof item.producto?.imagen === 'string' && item.producto.imagen.trim()
      ? item.producto.imagen
      : null;
  return {
    id: item.id ?? null,
    productoId: item.producto?.id ?? null,
    nombre: item.producto?.nombre || 'Producto',
    imagen,
    cantidad,
    maximo,
    disponible,
    motivo: disponible ? null : (item.motivo ?? null),
    motivoTexto: disponible
      ? null
      : (MOTIVOS_DE_LINEA[item.motivo] ?? 'No se puede pagar ahora. Quítalo del carrito.'),
    subtotal,
    subtotalTexto: textoDePrecio(subtotal, moneda),
    unitario,
    unitarioTexto: textoDePrecio(unitario, moneda),
  };
}
