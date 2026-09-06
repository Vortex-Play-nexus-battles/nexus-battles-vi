/** HU-PRD-001 - Cliente HTTP para crear productos. */
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

const RUTA_PRODUCTOS = '/api/v1/productos';

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
    const detalle =
      typeof cuerpo === 'object' && cuerpo !== null ? cuerpo.detail || cuerpo.title : null;
    const fallo = new Error(detalle || 'No se pudo crear el producto.');
    fallo.status = respuesta.status;
    fallo.problem = cuerpo;
    throw fallo;
  }

  return cuerpo;
}

export { RUTA_PRODUCTOS };
