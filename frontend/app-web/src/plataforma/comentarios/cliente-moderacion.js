/**
 * RF-COM-005, RF-COM-006 y RF-COM-008 — acceso HTTP a la moderacion de
 * comentarios, contrato 1.3.0 (`contracts/openapi/comentarios.yaml`).
 *
 * <h2>Dos prefijos, y no es un descuido</h2>
 *
 * Reportar cuelga del comentario (`/api/v1/products/{id}/comments/{id}/reportes`)
 * porque es donde esta el jugador cuando reporta. La cola y las decisiones
 * cuelgan de `/api/v1/comentarios/moderacion`, que es territorio del
 * moderador. Mezclarlos daria un prefijo donde la mitad de los metodos son
 * para cualquiera y la otra mitad no.
 *
 * Y NO es `/api/v1/moderacion/...`: ese prefijo ya es de metricas-plataforma
 * y el borde lo enruta a otro servicio. Cambiarlo aqui a la ruta «bonita»
 * rompe la vista en el navegador aunque las pruebas sigan verdes.
 *
 * Los errores salen como {@link ErrorDeApi} —el mismo de
 * `cliente-comentarios.js`— para que la vista decida por `motivo` y nunca
 * comparando textos (`shared/ui-kit/MAPEO-ERRORES.md`).
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { ErrorDeApi, baseDeApi, rutaDeComentarios } from './cliente-comentarios.js';

export { ErrorDeApi };

/** Categorias de reporte del contrato, en el orden en que se ofrecen. */
export const CATEGORIAS = Object.freeze([
  { valor: 'CONTENIDO_OFENSIVO', etiqueta: 'Contenido ofensivo' },
  { valor: 'ACOSO', etiqueta: 'Acoso a otra persona' },
  { valor: 'SPAM', etiqueta: 'Spam o publicidad' },
  { valor: 'INFORMACION_FALSA', etiqueta: 'Información falsa' },
  { valor: 'CONTENIDO_INAPROPIADO', etiqueta: 'Contenido inapropiado' },
  { valor: 'VIOLACION_DE_DERECHOS', etiqueta: 'Violación de derechos' },
]);

/**
 * Acciones de RF-COM-008 con los estados desde los que valen.
 *
 * Esta tabla es un ESPEJO de `AccionDeModeracion` del servicio, y esta aqui
 * solo para no ofrecerle al moderador un boton que va a responder 409. La
 * decision de verdad la toma el servicio: si las dos tablas se separan, la
 * que manda es la suya y la vista se entera por el problem detail.
 */
export const ACCIONES = Object.freeze([
  { valor: 'APROBAR', etiqueta: 'Aprobar', desde: ['EN_REVISION'] },
  { valor: 'OCULTAR', etiqueta: 'Ocultar', desde: ['EN_REVISION', 'PUBLICADO'] },
  { valor: 'ELIMINAR', etiqueta: 'Eliminar', desde: ['EN_REVISION', 'PUBLICADO', 'OCULTO'] },
  { valor: 'RESTAURAR', etiqueta: 'Restaurar', desde: ['OCULTO'] },
]);

/** Motivos de rechazo que enumera el contrato 1.3.0 en `ProblemDetail.motivo`. */
export const MOTIVO_MODERACION = Object.freeze({
  REPORTE_DUPLICADO: 'REPORTE_DUPLICADO',
  LIMITE_DE_REPORTES: 'LIMITE_DE_REPORTES',
  TRANSICION_INVALIDA: 'TRANSICION_INVALIDA',
});

/**
 * @param {string} estado estado actual del comentario
 * @returns {Array<{valor: string, etiqueta: string}>} acciones que tienen sentido
 */
export function accionesDesde(estado) {
  return ACCIONES.filter((a) => a.desde.includes(estado)).map(({ valor, etiqueta }) => ({
    valor,
    etiqueta,
  }));
}

/** @returns {string} base de la cola de moderacion, sin barra final */
export function rutaDeModeracion() {
  return `${baseDeApi()}/api/v1/comentarios/moderacion`;
}

async function cuerpoDelProblema(respuesta) {
  try {
    return await respuesta.json();
  } catch {
    return null;
  }
}

async function pedir(url, opciones, fetchImpl) {
  const respuesta = await fetchImpl(url, opciones);
  if (respuesta.ok) {
    return respuesta.status === 204 ? null : respuesta.json();
  }
  throw new ErrorDeApi(await cuerpoDelProblema(respuesta), respuesta.status);
}

/**
 * Reportar un comentario ajeno — RF-COM-006.
 *
 * El reportante NO viaja en el cuerpo: sale del `uid` del token, que pone el
 * interceptor. Si viajara, cualquiera podria dejar reportes a nombre de otra
 * persona y el limite por usuario no significaria nada.
 *
 * @param {string} productoId
 * @param {string} comentarioId
 * @param {{categoria: string, descripcion?: string}} cuerpo
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{id: string, estadoDelComentario: string, reportesTotales: number}>}
 * @throws {ErrorDeApi} 409 `REPORTE_DUPLICADO`, 429 `LIMITE_DE_REPORTES`, 404
 */
export async function reportarComentario(
  productoId,
  comentarioId,
  cuerpo,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  return pedir(
    `${rutaDeComentarios(productoId)}/${encodeURIComponent(comentarioId)}/reportes`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(cuerpo),
    },
    fetchImpl,
  );
}

/**
 * La cola priorizada — RF-COM-005. Solo roles de moderacion.
 *
 * Vacia es `200` con lista vacia, no un 404: no tener trabajo pendiente es
 * una respuesta correcta, y la vista lo pinta como tal.
 *
 * @param {{productoId?: string|null, pagina?: number, tamano?: number}} [filtro]
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{entradas: object[], total: number, pagina: number, tamano: number}>}
 */
export async function consultarCola(
  { productoId = null, pagina = 0, tamano = 20 } = {},
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const parametros = new URLSearchParams({ pagina: String(pagina), tamano: String(tamano) });
  if (productoId) {
    parametros.set('productoId', productoId);
  }
  return pedir(
    `${rutaDeModeracion()}?${parametros}`,
    { method: 'GET', headers: { Accept: 'application/json' } },
    fetchImpl,
  );
}

/**
 * El comentario con sus reportes y su historial: todo lo que hace falta para
 * decidir sin abrir otra pantalla.
 *
 * @param {string} comentarioId
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{comentario: object, reportes: object[], historial: object[]}>}
 */
export async function consultarDetalle(
  comentarioId,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  return pedir(
    `${rutaDeModeracion()}/${encodeURIComponent(comentarioId)}`,
    { method: 'GET', headers: { Accept: 'application/json' } },
    fetchImpl,
  );
}

/**
 * La decision — RF-COM-008. El motivo es obligatorio, incluida APROBAR: no se
 * archiva nada sin decir por que.
 *
 * Quien firma sale del token, no de aqui.
 *
 * @param {string} comentarioId
 * @param {{accion: string, motivo: string}} decision
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{comentario: object, asiento: object, autorNotificado: boolean}>}
 * @throws {ErrorDeApi} 400 sin motivo, 409 `TRANSICION_INVALIDA` si otro se adelanto
 */
export async function resolverComentario(
  comentarioId,
  decision,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  return pedir(
    `${rutaDeModeracion()}/${encodeURIComponent(comentarioId)}/decision`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(decision),
    },
    fetchImpl,
  );
}
