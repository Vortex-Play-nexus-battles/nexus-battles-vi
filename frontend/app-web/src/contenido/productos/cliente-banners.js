import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

export const RUTA_BANNERS = '/api/v1/banners';

async function ejecutar(ruta, opciones, fetchImpl) {
  const respuesta = await fetchImpl(ruta, opciones);
  const texto = await respuesta.text();
  const cuerpo = texto ? JSON.parse(texto) : null;
  if (!respuesta.ok) {
    const error = new Error(cuerpo?.detail || cuerpo?.title || 'No se pudo gestionar el banner.');
    error.status = respuesta.status;
    error.problem = cuerpo;
    throw error;
  }
  return cuerpo;
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
