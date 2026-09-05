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
    throw new TypeError('El identificador del producto no puede estar vacio');
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
