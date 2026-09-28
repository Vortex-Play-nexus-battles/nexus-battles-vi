/** HU-PRD-001 y HU-PRD-008 - Cliente HTTP del catálogo de productos. */
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { textoDelServidor } from '../../comun/ui/texto-de-fallo.js';

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
  // UXC-9 — el texto del servicio solo si se puede leer; nunca un 5xx crudo.
  const fallo = new Error(
    textoDelServidor(
      typeof cuerpo === 'object' && cuerpo !== null ? cuerpo : null,
      status,
      mensajePredeterminado,
    ),
  );
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

/**
 * UXC-7 — una página del catálogo para administrarlo (`listarProductosDelCatalogo`).
 *
 * Sin `estado` el servicio lista ACTIVO y UNICO; SUSPENDIDO solo sale
 * pidiéndolo, así que el filtro de la consola lo pide explícitamente.
 *
 * @param {{pagina?: number, tamano?: number, tipo?: string|null, estado?: string|null}} criterios
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{content: object[], page: number, size: number, totalElements: number, totalPages: number}>}
 */
export async function listarProductos(
  { pagina = 0, tamano = 16, tipo = null, estado = null } = {},
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const parametros = new URLSearchParams({ page: String(pagina), size: String(tamano) });
  if (tipo) {
    parametros.set('tipo', tipo);
  }
  if (estado) {
    parametros.set('estado', estado);
  }
  const respuesta = await fetchImpl(`${RUTA_PRODUCTOS}?${parametros}`, { method: 'GET' });
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudo consultar el catálogo.');
  }
  return cuerpo;
}

/**
 * HU-PRD-003 — modifica solo los campos que cambian (`modificarProducto`).
 *
 * @param {string} id
 * @param {object} cambios cuerpo conforme a SolicitudModificarProducto
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<object>} el producto ya fusionado
 */
export async function modificarProducto(
  id,
  cambios,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(`${RUTA_PRODUCTOS}/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(cambios),
  });
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudo modificar el producto.');
  }
  return cuerpo;
}

/**
 * Suspende o reactiva un producto (`suspenderProducto` / `reactivarProducto`).
 *
 * @param {string} id
 * @param {'suspender'|'reactivar'} accion
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{productoId: string, estado: string, tiraje: number}>}
 */
export async function cambiarDisponibilidad(
  id,
  accion,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(`${RUTA_PRODUCTOS}/${encodeURIComponent(id)}/${accion}`, {
    method: 'PUT',
  });
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw errorDe(
      cuerpo,
      respuesta.status,
      accion === 'suspender'
        ? 'No se pudo suspender el producto.'
        : 'No se pudo reactivar el producto.',
    );
  }
  return cuerpo;
}

export { RUTA_ESTADISTICAS, RUTA_PRODUCTOS };
