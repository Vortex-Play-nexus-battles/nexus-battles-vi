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

/**
 * Retira un banner (retiro lógico). SA-18 (productos.yaml 1.12.0): con el
 * `motivo` que escribió quien lo retira, que queda en su historial y en la
 * bitácora central.
 *
 * @param {string} id
 * @param {{motivo?: string|null, fetchImpl?: Function}} [opciones]
 */
export function retirarBanner(id, opciones = {}) {
  const motivo = String(opciones.motivo ?? '').trim();
  const consulta = motivo ? `?motivo=${encodeURIComponent(motivo)}` : '';
  return ejecutar(
    `${RUTA_BANNERS}/${encodeURIComponent(id)}${consulta}`,
    { method: 'DELETE' },
    opciones.fetchImpl ?? fetchWithHttpErrorInterceptor,
  );
}

/**
 * SA-18 (productos.yaml 1.12.0, `consultarHistorialBanner`): lo que se hizo
 * con un banner, de lo más reciente a lo más antiguo, sin la IP.
 *
 * @param {string} id
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<Array<object>>}
 */
export function historialDeBanner(id, opciones = {}) {
  return ejecutar(
    `${RUTA_BANNERS}/${encodeURIComponent(id)}/historial`,
    { method: 'GET' },
    opciones.fetchImpl ?? fetchWithHttpErrorInterceptor,
  );
}
