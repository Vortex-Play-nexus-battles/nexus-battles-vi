/**
 * SCRUM-319 - Acceso HTTP a la consulta paginada del inventario (SCRUM-318).
 *
 * El propietarioId viaja hoy como dato de entrada. La validacion contra la
 * identidad autenticada llega con el contrato de identidad (HU-INF-009), tal
 * como advierte el README del servicio: sin esa integracion el endpoint no
 * debe exponerse fuera del entorno interno.
 */

const RUTA = '/api/v1/inventarios';

/**
 * Consulta una pagina del inventario de un jugador.
 *
 * @param {string} propietarioId identificador del jugador.
 * @param {number} numeroPagina pagina pedida, desde cero.
 * @param {{fetchImpl?: Function}} opciones inyeccion para las pruebas.
 * @returns {Promise<object>} la pagina tal como la entrega el servicio.
 */
export async function consultarPagina(
  propietarioId, numeroPagina = 0, { fetchImpl = globalThis.fetch } = {}) {

  if (typeof propietarioId !== 'string' || propietarioId.trim() === '') {
    throw new TypeError('El propietarioId no puede estar vacio');
  }
  const pagina = numeroPagina ?? 0;
  if (!Number.isInteger(pagina) || pagina < 0) {
    throw new RangeError('El numero de pagina no puede ser negativo');
  }

  const url = `${RUTA}/${encodeURIComponent(propietarioId)}/elementos?pagina=${pagina}`;
  const respuesta = await fetchImpl(url);
  if (!respuesta.ok) {
    throw new Error(
      `El servicio de inventario respondio ${respuesta.status} al pedir la pagina ${pagina}`);
  }
  return respuesta.json();
}
