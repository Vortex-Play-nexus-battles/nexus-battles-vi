import { baseDeApi } from '../../comun/base-api.js';
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { textoDelServidor } from '../../comun/ui/texto-de-fallo.js';

export const RUTA_BANNERS = '/api/v1/banners';

/**
 * Los vigentes: pública (productos.yaml 1.6.0, `consultarBannersVigentes`).
 * Escrita entera a propósito: `rutas-contrato-codigo.py` lee los literales de
 * los clientes y así comprueba que el contrato la sigue declarando.
 */
export const RUTA_BANNERS_VIGENTES = '/api/v1/banners/vigentes';

/**
 * El cuerpo como JSON si lo es. La página HTML de un proxy caído (un 502 del
 * borde) no es JSON: antes reventaba aquí con un `SyntaxError` y su texto
 * técnico llegaba a la pantalla.
 *
 * @param {string} texto
 * @returns {unknown}
 */
function cuerpoDe(texto) {
  if (!texto) {
    return null;
  }
  try {
    return JSON.parse(texto);
  } catch {
    return texto;
  }
}

async function ejecutar(ruta, opciones, fetchImpl) {
  // Misma base que el resto de la home (`base-api.js`): vacía, mismo origen,
  // salvo que la página declare otra.
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, opciones);
  const cuerpo = cuerpoDe(await respuesta.text());
  if (!respuesta.ok) {
    // El texto del servicio solo si lo puede leer una persona (UXC-9).
    const error = new Error(
      textoDelServidor(cuerpo, respuesta.status, 'No se pudo gestionar el banner.'),
    );
    error.status = respuesta.status;
    error.problem = cuerpo;
    throw error;
  }
  return cuerpo;
}

/**
 * Los banners que el servidor declara vigentes ahora mismo (RF-NOT-002): no
 * retirados, ya publicados y sin vencer. Es lo que rota la home del jugador.
 *
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<Array<object>>}
 */
export function listarVigentes(opciones = {}) {
  return ejecutar(
    RUTA_BANNERS_VIGENTES,
    { method: 'GET' },
    opciones.fetchImpl ?? fetchWithHttpErrorInterceptor,
  );
}

export function listarBanners(opciones = {}) {
  return ejecutar(
    RUTA_BANNERS,
    { method: 'GET' },
    opciones.fetchImpl ?? fetchWithHttpErrorInterceptor,
  );
}

export function crearBanner(solicitud, opciones = {}) {
  return ejecutar(
    RUTA_BANNERS,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(solicitud),
    },
    opciones.fetchImpl ?? fetchWithHttpErrorInterceptor,
  );
}

export function editarBanner(id, solicitud, opciones = {}) {
  return ejecutar(
    `${RUTA_BANNERS}/${encodeURIComponent(id)}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(solicitud),
    },
    opciones.fetchImpl ?? fetchWithHttpErrorInterceptor,
  );
}

export function retirarBanner(id, opciones = {}) {
  return ejecutar(
    `${RUTA_BANNERS}/${encodeURIComponent(id)}`,
    { method: 'DELETE' },
    opciones.fetchImpl ?? fetchWithHttpErrorInterceptor,
  );
}
