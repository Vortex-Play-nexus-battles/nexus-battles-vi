/**
 * HU-INV-001 - Acceso HTTP a la consulta paginada del inventario.
 *
 * El servicio identifica al jugador por la cabecera `X-User-Name`, la misma
 * que usan las operaciones de creacion y modificacion. Cuando llegue el
 * contrato de identidad (HU-INF-009) esa cabecera la pondra la sesion.
 */

const RUTA = '/api/v1/inventario/elementos';

/**
 * Consulta una pagina del inventario propio.
 *
 * @param {string} identidad jugador autenticado, que viaja en la cabecera.
 * @param {number} numeroPagina pagina pedida, desde cero.
 * @param {{fetchImpl?: Function}} opciones inyeccion para las pruebas.
 * @returns {Promise<object>} la pagina tal como la entrega el servicio.
 */
export async function consultarPagina(
  identidad,
  numeroPagina = 0,
  { fetchImpl = globalThis.fetch } = {},
) {
  if (typeof identidad !== 'string' || identidad.trim() === '') {
    throw new TypeError('La identidad no puede estar vacia');
  }
  const pagina = numeroPagina ?? 0;
  if (!Number.isInteger(pagina) || pagina < 0) {
    throw new RangeError('El numero de pagina no puede ser negativo');
  }

  // La identidad viaja en la cabecera y nunca en la ruta: asi un jugador no
  // puede pedir el inventario de otro cambiando la URL.
  const url = `${RUTA}?pagina=${pagina}`;
  const respuesta = await fetchImpl(url, {
    headers: { 'X-User-Name': identidad.trim() },
  });
  if (!respuesta.ok) {
    throw new Error(
      `El servicio de inventario respondio ${respuesta.status} al pedir la pagina ${pagina}`,
    );
  }
  return respuesta.json();
}
