/** HU-PRD-001 y HU-PRD-008 - Cliente HTTP del catálogo de productos. */
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { textoDelServidor } from '../../comun/ui/texto-de-fallo.js';

const RUTA_PRODUCTOS = '/api/v1/productos';
const RUTA_ESTADISTICAS = `${RUTA_PRODUCTOS}/estadisticas`;
/** HU-PRD-009 (productos.yaml 1.10.0) — la biblioteca de recursos gráficos. */
const RUTA_RECURSOS = `${RUTA_PRODUCTOS}/recursos`;

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

/** HU-PRD-007 — historial de cambios de solo lectura. */
export async function consultarHistorialProducto(
  id,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(`${RUTA_PRODUCTOS}/${encodeURIComponent(id)}/historial`, {
    method: 'GET',
  });
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudo consultar el historial del producto.');
  }
  return cuerpo;
}

/**
 * HU-PRD-007 — crea una reversión auditada a partir de un respaldo. Desde
 * 1.10.0 (§7.2.5) lleva el motivo, que queda en la entrada de la reversión.
 *
 * @param {string} id
 * @param {string} respaldoId
 * @param {{fetchImpl?: Function, motivo?: string|null}} [opciones]
 */
export async function revertirProducto(
  id,
  respaldoId,
  { fetchImpl = fetchWithHttpErrorInterceptor, motivo = null } = {},
) {
  const cuerpoDeSolicitud = motivo ? { respaldoId, motivo } : { respaldoId };
  const respuesta = await fetchImpl(`${RUTA_PRODUCTOS}/${encodeURIComponent(id)}/reversiones`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(cuerpoDeSolicitud),
  });
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudo revertir el producto.');
  }
  return cuerpo;
}

/** HU-PRD-011 — versiones del diseño con fecha y autor. */
export async function consultarVersionesDiseno(
  id,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(
    `${RUTA_PRODUCTOS}/${encodeURIComponent(id)}/diseno/versiones`,
    { method: 'GET' },
  );
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudieron consultar las versiones del diseño.');
  }
  return cuerpo;
}

/** HU-PRD-011 — exporta el diseño actual en el formato portable del servicio. */
export async function exportarPlantillaDiseno(
  id,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(
    `${RUTA_PRODUCTOS}/${encodeURIComponent(id)}/diseno/plantilla`,
    { method: 'GET' },
  );
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudo exportar la plantilla de diseño.');
  }
  return cuerpo;
}

/** HU-PRD-011 — aplica una plantilla validada sin cambiar los demás datos. */
export async function importarPlantillaDiseno(
  id,
  plantilla,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(
    `${RUTA_PRODUCTOS}/${encodeURIComponent(id)}/diseno/importaciones`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(plantilla),
    },
  );
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudo importar la plantilla de diseño.');
  }
  return cuerpo;
}

/**
 * Suspende o reactiva un producto (`suspenderProducto` / `reactivarProducto`).
 * Desde 1.10.0 (§7.2.5) con el motivo, que queda en el historial.
 *
 * @param {string} id
 * @param {'suspender'|'reactivar'} accion
 * @param {{fetchImpl?: Function, motivo?: string|null}} [opciones]
 * @returns {Promise<{productoId: string, estado: string, tiraje: number}>}
 */
export async function cambiarDisponibilidad(
  id,
  accion,
  { fetchImpl = fetchWithHttpErrorInterceptor, motivo = null } = {},
) {
  const consulta = motivo ? `?${new URLSearchParams({ motivo })}` : '';
  const respuesta = await fetchImpl(
    `${RUTA_PRODUCTOS}/${encodeURIComponent(id)}/${accion}${consulta}`,
    { method: 'PUT' },
  );
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

/**
 * HU-PRD-009 — sube un recurso gráfico (`subirRecursoGrafico`). El formato lo
 * decide el servicio por los bytes; si no cumple, su mensaje dice el requisito
 * incumplido y llega tal cual en `error.message`.
 *
 * @param {Blob} archivo
 * @param {{clase: 'IMAGEN'|'ICONO'|'ANIMACION', productoId?: string|null}} datos
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<object>} el `RecursoGrafico`, nuevo o el mismo que ya estaba
 */
export async function subirRecurso(
  archivo,
  { clase, productoId = null },
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const formulario = new FormData();
  formulario.append('archivo', archivo, archivo?.name ?? 'recurso');
  formulario.append('clase', clase);
  if (productoId) {
    formulario.append('productoId', productoId);
  }
  const respuesta = await fetchImpl(RUTA_RECURSOS, { method: 'POST', body: formulario });
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudo subir el archivo.');
  }
  return cuerpo;
}

/**
 * HU-PRD-009 — una página de la biblioteca (`listarRecursosGraficos`).
 *
 * @param {{clase?: string|null, productoId?: string|null, incluirRetirados?: boolean,
 *          pagina?: number, tamano?: number}} [filtro]
 * @param {{fetchImpl?: Function}} [opciones]
 */
export async function listarRecursos(
  { clase = null, productoId = null, incluirRetirados = false, pagina = 0, tamano = 16 } = {},
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const parametros = new URLSearchParams({ page: String(pagina), size: String(tamano) });
  if (clase) {
    parametros.set('clase', clase);
  }
  if (productoId) {
    parametros.set('productoId', productoId);
  }
  if (incluirRetirados) {
    parametros.set('incluirRetirados', 'true');
  }
  const respuesta = await fetchImpl(`${RUTA_RECURSOS}?${parametros}`, { method: 'GET' });
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudo consultar la biblioteca de recursos.');
  }
  return cuerpo;
}

/**
 * HU-PRD-009 — retira un recurso de la biblioteca (`retirarRecursoGrafico`).
 *
 * @param {string} id
 * @param {string} motivo
 * @param {{fetchImpl?: Function}} [opciones]
 */
export async function retirarRecurso(
  id,
  motivo,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const consulta = motivo ? `?${new URLSearchParams({ motivo })}` : '';
  const respuesta = await fetchImpl(`${RUTA_RECURSOS}/${encodeURIComponent(id)}${consulta}`, {
    method: 'DELETE',
  });
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw errorDe(cuerpo, respuesta.status, 'No se pudo retirar el recurso.');
  }
  return cuerpo;
}

export { RUTA_ESTADISTICAS, RUTA_PRODUCTOS, RUTA_RECURSOS };
