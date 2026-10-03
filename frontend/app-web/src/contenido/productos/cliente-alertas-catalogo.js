import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

const RUTA_ALERTAS_CATALOGO = '/api/v1/productos/alertas/inicio-sesion';

export async function consultarAlertasCatalogo({ fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(RUTA_ALERTAS_CATALOGO, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
  });

  if (!respuesta.ok) {
    const error = new Error('No fue posible consultar los cambios del catálogo.');
    error.status = respuesta.status;
    throw error;
  }

  const alertas = await respuesta.json();
  return Array.isArray(alertas) ? alertas : [];
}

export { RUTA_ALERTAS_CATALOGO };
