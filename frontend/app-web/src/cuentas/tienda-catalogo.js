/**
 * El catálogo de la tienda entero, y cómo se busca y se filtra — UXC-4.
 *
 * ## Por qué se reúne entero
 *
 * §7.5 del documento pide para la vitrina búsqueda «incluido el precio» y
 * filtros por precio, tipo y promoción. `GET /api/v1/vitrina`
 * (ecommerce-carrito.yaml 1.2.0) pagina y solo filtra por `tipo`: no tiene
 * búsqueda por texto ni por precio. Filtrar una sola página del servidor
 * enseñaría resultados de dieciséis productos como si fueran los de toda la
 * tienda.
 *
 * Así que se piden las páginas —de cincuenta, el máximo del contrato— hasta la
 * última, y la búsqueda, los filtros y el orden trabajan sobre la colección
 * completa; la vista pagina de dieciséis en dieciséis (RNF-USA-001). Es el
 * mismo criterio que el inventario (`coleccion-inventario.js`). Hay un tope
 * para que un catálogo enorme no dispare cien peticiones: si se alcanza, la
 * vista lo dice.
 *
 * Buscar y filtrar aquí es **presentación**, no negocio: no decide qué se
 * vende ni a qué precio. Eso lo dice el servicio, y el carrito lo vuelve a
 * comprobar al añadir (409 agotado, 422 sin precio en dinero real).
 *
 * ## Qué es «tuyo»
 *
 * La vitrina trae `esPropio` y hoy lo escribe siempre en `false`: no se
 * cruza con el inventario. El inventario del jugador sí lo publica
 * (`GET /api/v1/inventario/elementos`, con su `productoId`), así que la
 * tienda lo lee y marca lo que ya tiene. Es un dato del inventario, no una
 * deducción: si no se puede leer, no se marca nada.
 *
 * @module cuentas/tienda-catalogo
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { rutaDeApi } from '../comun/base-api.js';
import { NOMBRE_DEL_TIPO } from '../contenido/inventario/ficha-producto.js';
import { reunirInventario } from '../contenido/inventario/coleccion-inventario.js';
import { consultarPagina } from '../contenido/inventario/cliente-inventario.js';

/**
 * Un producto de la vitrina ya traducido por `aProductoDeVitrina`.
 *
 * @typedef {ReturnType<typeof import('./tienda-adaptador.js').aProductoDeVitrina>} ProductoDeTienda
 */

/** Productos por petición: el máximo que admite `GET /vitrina`. */
export const PRODUCTOS_POR_LOTE = 50;

/** Tope de peticiones de una vez: 10 × 50 = 500 productos. */
export const MAX_LOTES = 10;

/** Productos por página de la vitrina (RNF-USA-001). */
export const PRODUCTOS_POR_PAGINA = 16;

/** Los órdenes que ofrece la vista. «catalogo» es el orden del servicio. */
export const ORDENES = Object.freeze({
  CATALOGO: 'catalogo',
  PRECIO_ASC: 'precio-asc',
  PRECIO_DESC: 'precio-desc',
  NOMBRE: 'nombre',
});

/**
 * Un rechazo de la vitrina con su estado y su problem detail.
 *
 * @param {Response} respuesta
 * @returns {Promise<Error & {estado: number, problema: object|null}>}
 */
async function errorDeRespuesta(respuesta) {
  const error = new Error(`La vitrina respondió ${respuesta.status}`);
  error.estado = respuesta.status;
  try {
    const cuerpo = await respuesta.json();
    error.problema = cuerpo && typeof cuerpo === 'object' ? cuerpo : null;
  } catch {
    error.problema = null;
  }
  return error;
}

/**
 * Pide la vitrina entera, lote a lote.
 *
 * La primera petición va a `/vitrina?size=50`; las siguientes añaden la
 * página. Se para en la última (`last`, un lote corto o `totalPages`) o en el
 * tope.
 *
 * @param {{fetchImpl?: Function, porLote?: number, maxLotes?: number}} [opciones]
 * @returns {Promise<{productos: object[], completo: boolean}>} los DTO tal cual
 * @throws {Error & {estado: number, problema: object|null}} si el servicio rechaza
 */
export async function reunirVitrina({
  fetchImpl = fetchWithHttpErrorInterceptor,
  porLote = PRODUCTOS_POR_LOTE,
  maxLotes = MAX_LOTES,
} = {}) {
  const productos = [];
  for (let pagina = 0; pagina < maxLotes; pagina += 1) {
    const consulta = pagina === 0 ? `?size=${porLote}` : `?page=${pagina}&size=${porLote}`;
    const respuesta = await fetchImpl(rutaDeApi(`/vitrina${consulta}`), {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
    });
    if (!respuesta.ok) {
      throw await errorDeRespuesta(respuesta);
    }
    const datos = await respuesta.json();
    const lote = Array.isArray(datos?.content) ? datos.content : [];
    productos.push(...lote);
    const ultima =
      datos?.last === true ||
      lote.length < porLote ||
      (Number.isInteger(datos?.totalPages) && pagina + 1 >= datos.totalPages);
    if (ultima) {
      return { productos, completo: true };
    }
  }
  return { productos, completo: false };
}

/**
 * Minúsculas y sin tildes, para comparar como compara una persona.
 *
 * @param {unknown} texto
 * @returns {string}
 */
export function normalizar(texto) {
  return String(texto ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

/** Solo las cifras de un texto: «45.000 COP» → «45000». */
function soloCifras(texto) {
  return String(texto ?? '').replace(/\D/g, '');
}

/**
 * ¿Coincide un producto con lo que se busca?
 *
 * Se busca en el nombre, la descripción, las habilidades, el tipo (en su
 * nombre legible: «ítem», «armadura») y el precio: «45000», «45.000» y
 * «45 000» encuentran el mismo producto (§7.5, «búsqueda incluido precio»).
 *
 * @param {ProductoDeTienda} producto
 * @param {string} busqueda
 * @returns {boolean}
 */
export function coincideBusqueda(producto, busqueda) {
  const termino = normalizar(busqueda);
  if (!termino) {
    return true;
  }
  const texto = normalizar(
    [
      producto.nombre,
      producto.descripcion,
      producto.habilidades,
      NOMBRE_DEL_TIPO[producto.tipo] ?? producto.tipo,
      producto.precioTexto,
    ].join(' '),
  );
  if (texto.includes(termino)) {
    return true;
  }
  // Una búsqueda hecha solo de cifras («45000», «45 000», «$45.000») se
  // compara con el precio sin separadores. Una con letras no: «espada 2» no
  // debe traer todo lo que cuesta algo con un 2.
  if (/^[\d\s.,$]+$/.test(String(busqueda).trim()) && producto.precio !== null) {
    return soloCifras(String(Math.round(producto.precio))).includes(soloCifras(busqueda));
  }
  return false;
}

/**
 * Criterios de la vista, con sus valores neutros.
 *
 * @typedef {{busqueda?: string, tipo?: string, precioMinimo?: number|null,
 *   precioMaximo?: number|null, soloPromocion?: boolean, orden?: string}} Criterios
 */

/**
 * Aplica búsqueda, filtros y orden a la colección ya traducida.
 *
 * @param {Array<ProductoDeTienda>} productos
 * @param {Criterios} criterios
 * @returns {Array<ProductoDeTienda>}
 */
export function filtrarProductos(productos, criterios = {}) {
  const {
    busqueda = '',
    tipo = '',
    precioMinimo = null,
    precioMaximo = null,
    soloPromocion = false,
    orden = ORDENES.CATALOGO,
  } = criterios;

  const filtrados = productos.filter((producto) => {
    if (tipo && producto.tipo !== tipo) {
      return false;
    }
    // Un producto sin precio no pasa un filtro de precio: no se sabe si cae
    // dentro. Sin filtro de precio, sí se enseña (diciendo que falta).
    if (
      Number.isFinite(precioMinimo) &&
      !(producto.precio !== null && producto.precio >= precioMinimo)
    ) {
      return false;
    }
    if (
      Number.isFinite(precioMaximo) &&
      !(producto.precio !== null && producto.precio <= precioMaximo)
    ) {
      return false;
    }
    // «En promoción» es una rebaja que se ve en el precio, la misma regla que
    // pinta el distintivo (`tienda-adaptador.js`): un `enPromocion` sin rebaja
    // no cuenta.
    if (soloPromocion && producto.precioAnterior === null) {
      return false;
    }
    return coincideBusqueda(producto, busqueda);
  });

  return ordenarProductos(filtrados, orden);
}

/**
 * Ordena sin tocar la lista original. Los que no tienen precio van al final
 * en los dos órdenes por precio.
 *
 * @param {Array<ProductoDeTienda>} productos
 * @param {string} orden
 * @returns {Array<ProductoDeTienda>}
 */
export function ordenarProductos(productos, orden) {
  const copia = [...productos];
  const porPrecio = (signo) => (a, b) => {
    if (a.precio === null) {
      return b.precio === null ? 0 : 1;
    }
    if (b.precio === null) {
      return -1;
    }
    return signo * (a.precio - b.precio);
  };
  if (orden === ORDENES.PRECIO_ASC) {
    return copia.sort(porPrecio(1));
  }
  if (orden === ORDENES.PRECIO_DESC) {
    return copia.sort(porPrecio(-1));
  }
  if (orden === ORDENES.NOMBRE) {
    return copia.sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  }
  return copia;
}

/**
 * ¿Hay algún criterio activo? Sirve para decir «ningún producto coincide» en
 * vez de «la tienda está vacía».
 *
 * @param {Criterios} criterios
 * @returns {boolean}
 */
export function hayCriterios(criterios = {}) {
  return Boolean(
    normalizar(criterios.busqueda) ||
    criterios.tipo ||
    Number.isFinite(criterios.precioMinimo) ||
    Number.isFinite(criterios.precioMaximo) ||
    criterios.soloPromocion,
  );
}

/**
 * Cuántas unidades de cada producto del catálogo tiene el jugador.
 *
 * @param {string} identidad el `uid` de la sesión
 * @param {{consultar?: typeof consultarPagina}} [opciones]
 * @returns {Promise<Map<string, number>>} `productoId → unidades`; vacío si
 *   el inventario no se pudo leer (no se afirma nada)
 */
export async function propiedadesDelJugador(identidad, { consultar = consultarPagina } = {}) {
  const propias = new Map();
  if (!identidad) {
    return propias;
  }
  try {
    const { elementos } = await reunirInventario(consultar, identidad);
    for (const elemento of elementos) {
      if (elemento?.productoId) {
        const clave = String(elemento.productoId);
        propias.set(clave, (propias.get(clave) ?? 0) + 1);
      }
    }
  } catch {
    // Sin inventario no se marca nada como tuyo: es preferible a marcar mal.
  }
  return propias;
}
