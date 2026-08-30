/**
 * HU-INV-001 - Acceso HTTP a la consulta paginada del inventario.
 *
 * El servicio identifica al jugador por la cabecera `X-User-Name`, la misma
 * que usan las operaciones de creacion y modificacion. Cuando llegue el
 * contrato de identidad (HU-INF-009) esa cabecera la pondra la sesion.
 *
 * Las peticiones salen por el envoltorio comun de `src/comun/`, no por `fetch`
 * pelado: asi el manejo de Problem Details es el mismo en los veinte modulos.
 * Sigue siendo inyectable para que las pruebas no dependan de la red.
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

const RUTA = '/api/v1/inventario/elementos';

function identidadNormalizada(identidad) {
  if (typeof identidad !== 'string' || identidad.trim() === '') {
    throw new TypeError('La identidad no puede estar vacia');
  }
  return identidad.trim();
}

function textoObligatorio(valor) {
  return typeof valor === 'string' && valor.trim() !== '';
}

async function escribir(ruta, metodo, identidad, cuerpo, fetchImpl) {
  const respuesta = await fetchImpl(ruta, {
    method: metodo,
    headers: {
      'Content-Type': 'application/json',
      'X-User-Name': identidadNormalizada(identidad),
    },
    body: JSON.stringify(cuerpo),
  });
  if (!respuesta.ok) {
    const fallo = new Error(`El servicio de inventario respondio ${respuesta.status} al guardar`);
    fallo.status = respuesta.status;
    throw fallo;
  }
  return respuesta.json();
}

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
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const propietario = identidadNormalizada(identidad);
  const pagina = numeroPagina ?? 0;
  if (!Number.isInteger(pagina) || pagina < 0) {
    throw new RangeError('El numero de pagina no puede ser negativo');
  }

  // La identidad viaja en la cabecera y nunca en la ruta: asi un jugador no
  // puede pedir el inventario de otro cambiando la URL.
  const url = `${RUTA}?pagina=${pagina}`;
  const respuesta = await fetchImpl(url, {
    headers: { 'X-User-Name': propietario },
  });
  if (!respuesta.ok) {
    throw new Error(
      `El servicio de inventario respondio ${respuesta.status} al pedir la pagina ${pagina}`,
    );
  }
  return respuesta.json();
}

/** Crea un elemento en el inventario del jugador autenticado. */
export async function crearElemento(
  identidad,
  { productoId, tipo, nombrePropio },
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  if (![productoId, tipo, nombrePropio].every(textoObligatorio)) {
    throw new TypeError('Producto, tipo y nombre son obligatorios');
  }
  return escribir(
    RUTA,
    'POST',
    identidad,
    {
      productoId: productoId.trim(),
      tipo: tipo.trim(),
      nombrePropio: nombrePropio.trim(),
    },
    fetchImpl,
  );
}

/** Modifica el nombre de un elemento propio. */
export async function modificarElemento(
  identidad,
  elementoId,
  { nombrePropio },
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  if (!textoObligatorio(elementoId) || !textoObligatorio(nombrePropio)) {
    throw new TypeError('El elemento y el nombre son obligatorios');
  }
  return escribir(
    `${RUTA}/${encodeURIComponent(elementoId.trim())}`,
    'PATCH',
    identidad,
    { nombrePropio: nombrePropio.trim() },
    fetchImpl,
  );
}
