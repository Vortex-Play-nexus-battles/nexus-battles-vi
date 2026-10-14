/**
 * HU-INV-007 - Lectura del catalogo de productos.
 *
 * Contrato: `GET /api/v1/productos/{id}`, publicado en
 * `contracts/openapi/productos.yaml`.
 *
 * Es una lectura **publica**: a diferencia de la creacion, no lleva identidad.
 * El inventario del jugador guarda solo la referencia `productoId`, nunca una
 * copia de los atributos, para que un cambio del administrador se propague a
 * todas las instancias (RF-ADM-10). De ahi que la ficha tenga que venir aqui.
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

const RUTA = '/api/v1/productos';

/**
 * Consulta un producto del catalogo por su identificador.
 *
 * @param {string} productoId referencia guardada en el inventario.
 * @param {{fetchImpl?: Function}} opciones inyeccion para las pruebas.
 * @returns {Promise<object>} el producto tal como lo entrega el catalogo.
 */
export async function consultarProducto(
  productoId,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  if (typeof productoId !== 'string' || productoId.trim() === '') {
    throw new TypeError('El identificador del producto no puede estar vacío');
  }

  const respuesta = await fetchImpl(`${RUTA}/${encodeURIComponent(productoId.trim())}`);

  // Un producto inexistente no es un fallo del catalogo: se distingue para
  // que la vista pueda decir algo distinto de "vuelve a intentarlo".
  if (respuesta.status === 404) {
    throw new Error(`El producto ${productoId} no existe en el catalogo`);
  }
  if (!respuesta.ok) {
    throw new Error(
      `El catalogo de productos respondio ${respuesta.status} al pedir ${productoId}`,
    );
  }
  return respuesta.json();
}

/**
 * Una pagina del catalogo publico, filtrada por tipo (`GET /api/v1/productos`,
 * productos.yaml: publico, solo ACTIVO y UNICO por omision).
 *
 * PLAYER-07a — la usa «Héroes del Nexo» para enseñar los héroes que se pueden
 * conseguir: el catalogo es la fuente, no una lista escrita en la vista.
 *
 * @param {{tipo?: string, pagina?: number, tamano?: number, fetchImpl?: Function}} [opciones]
 *   `tamano` entre 1 y 50 (el maximo del contrato)
 * @returns {Promise<{productos: object[], total: number|null}>}
 */
export async function listarProductos({
  tipo,
  pagina = 0,
  tamano = 50,
  fetchImpl = fetchWithHttpErrorInterceptor,
} = {}) {
  const parametros = new URLSearchParams({ page: String(pagina), size: String(tamano) });
  if (tipo) {
    parametros.set('tipo', tipo);
  }
  const respuesta = await fetchImpl(`${RUTA}?${parametros}`);
  if (!respuesta.ok) {
    throw new Error(`El catalogo de productos respondio ${respuesta.status} al listar`);
  }
  // `PaginaDeProductos`: la lista en `content` y el total en `totalElements`.
  const cuerpo = await respuesta.json();
  return {
    productos: Array.isArray(cuerpo?.content) ? cuerpo.content : [],
    total: Number.isInteger(cuerpo?.totalElements) ? cuerpo.totalElements : null,
  };
}
