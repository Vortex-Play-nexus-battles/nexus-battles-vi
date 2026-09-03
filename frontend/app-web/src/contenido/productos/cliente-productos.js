/** HU-PRD-001 y HU-PRD-008 - Cliente HTTP del catálogo de productos. */
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

const RUTA_PRODUCTOS = '/api/v1/productos';
const RUTA_ESTADISTICAS = `${RUTA_PRODUCTOS}/estadisticas`;

async function cuerpoDe(respuesta) {
  const texto = await respuesta.text();
  if (!texto) {
    return null;
  }

  try {
    return JSON.parse(texto);
  } catch {
    return texto;
  }
}

function errorDe(cuerpo, status, mensajePredeterminado) {
  const detalle =
    typeof cuerpo === 'object' && cuerpo !== null ? cuerpo.detail || cuerpo.title : null;
  const fallo = new Error(detalle || mensajePredeterminado);
  fallo.status = status;
  fallo.problem = cuerpo;
  return fallo;
}

/**
 * Crea un producto. El interceptor común adjunta automáticamente el token
 * almacenado por el inicio de sesión.
 *
 * @param {object} solicitud cuerpo conforme a SolicitudCrearProducto.
 * @param {{fetchImpl?: Function}} opciones de inyección para pruebas.
 * @returns {Promise<object>} producto persistido por el servicio.
 */
export async function crearProducto(solicitud, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(RUTA_PRODUCTOS, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(solicitud),
  });
  const cuerpo = await cuerpoDe(respuesta);

  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudo crear el producto.');
  }

  return cuerpo;
}

/**
 * Consulta el resumen actual del catálogo para HU-PRD-008.
 *
 * @param {{fetchImpl?: Function}} opciones de inyección para pruebas.
 * @returns {Promise<{total:number, porTipo:object, porEstado:object}>}
 */
export async function consultarEstadisticasCatalogo({
  fetchImpl = fetchWithHttpErrorInterceptor,
} = {}) {
  const respuesta = await fetchImpl(RUTA_ESTADISTICAS, {
    method: 'GET',
  });
  const cuerpo = await cuerpoDe(respuesta);

  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudo consultar el estado del catálogo.');
  }

  return cuerpo;
}

export { RUTA_ESTADISTICAS, RUTA_PRODUCTOS };
