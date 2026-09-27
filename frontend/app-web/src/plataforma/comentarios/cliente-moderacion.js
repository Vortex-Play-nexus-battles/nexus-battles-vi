/**
 * RF-COM-005, RF-COM-006 y RF-COM-008 — acceso HTTP a la moderacion de
 * comentarios, contrato 1.3.0 (`contracts/openapi/comentarios.yaml`), con lo
 * que anade la 1.5.0 (B3, 7.3.3): EDITAR con `textoNuevo`, MARCAR y DESMARCAR
 * para el seguimiento especial, el filtro `marcado` de la cola y las imagenes
 * privadas de un comentario en revision, que solo ve su autor o moderacion.
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
import { ErrorDeApi, baseDeApi, rutaDeComentarios, urlDeImagen } from './cliente-comentarios.js';

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

/** Los estados desde los que se edita y se marca: todos menos ELIMINADO (1.5.0). */
const NO_TERMINALES = Object.freeze(['PUBLICADO', 'EN_REVISION', 'OCULTO']);

/**
 * Acciones de RF-COM-008 (y de 7.3.3 desde la 1.5.0) con los estados desde los
 * que valen y, para MARCAR y DESMARCAR, la marca que tiene que tener el
 * comentario (`marca`): marcar uno ya marcado es 409, como desmarcar uno sin
 * marca.
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
  { valor: 'EDITAR', etiqueta: 'Editar el texto', desde: NO_TERMINALES },
  { valor: 'MARCAR', etiqueta: 'Marcar para seguimiento', desde: NO_TERMINALES, marca: false },
  {
    valor: 'DESMARCAR',
    etiqueta: 'Quitar la marca de seguimiento',
    desde: NO_TERMINALES,
    marca: true,
  },
]);

/**
 * Las que dejan el comentario en el estado en que estaba (1.5.0): tras ellas
 * el moderador sigue con el mismo comentario delante.
 */
export const ACCIONES_SIN_CAMBIO_DE_ESTADO = Object.freeze(['EDITAR', 'MARCAR', 'DESMARCAR']);

/**
 * Las que son una nota interna de moderacion: el autor no recibe aviso
 * (`autorNotificado: false` siempre, comentarios.yaml 1.5.0).
 */
export const ACCIONES_INTERNAS = Object.freeze(['MARCAR', 'DESMARCAR']);

/** Motivos de rechazo que enumera el contrato 1.3.0 en `ProblemDetail.motivo`. */
export const MOTIVO_MODERACION = Object.freeze({
  REPORTE_DUPLICADO: 'REPORTE_DUPLICADO',
  LIMITE_DE_REPORTES: 'LIMITE_DE_REPORTES',
  TRANSICION_INVALIDA: 'TRANSICION_INVALIDA',
});

/** Largos del contrato (`DecisionRequest`). */
export const MOTIVO_MINIMO = 3;
export const MOTIVO_MAXIMO = 500;
export const TEXTO_NUEVO_MAXIMO = 2000;

/**
 * Que mirar en la cola (`marcado`, 1.5.0): sin filtro, la de siempre (los
 * EN_REVISION); `true`, la lista de seguimiento (los marcados en cualquier
 * estado salvo ELIMINADO); `false`, los EN_REVISION sin marcar.
 */
export const FILTROS_DE_COLA = Object.freeze([
  { valor: 'en-revision', etiqueta: 'En revisión', marcado: null },
  { valor: 'marcados', etiqueta: 'Marcados para seguimiento', marcado: true },
  { valor: 'sin-marcar', etiqueta: 'En revisión sin marcar', marcado: false },
]);

/**
 * @param {string} estado estado actual del comentario
 * @param {boolean} [marcado] si tiene la marca de seguimiento (1.5.0)
 * @returns {Array<{valor: string, etiqueta: string}>} acciones que tienen sentido
 */
export function accionesDesde(estado, marcado = false) {
  return ACCIONES.filter(
    (a) => a.desde.includes(estado) && (a.marca === undefined || a.marca === Boolean(marcado)),
  ).map(({ valor, etiqueta }) => ({ valor, etiqueta }));
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
 * @param {{productoId?: string|null, marcado?: boolean|null, pagina?: number, tamano?: number}} [filtro]
 *   `marcado` (1.5.0): `true` la lista de seguimiento, `false` los en revision
 *   sin marcar; `null` no viaja y el servicio da la cola de siempre.
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{entradas: object[], total: number, pagina: number, tamano: number}>}
 */
export async function consultarCola(
  { productoId = null, marcado = null, pagina = 0, tamano = 20 } = {},
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const parametros = new URLSearchParams({ pagina: String(pagina), tamano: String(tamano) });
  if (productoId) {
    parametros.set('productoId', productoId);
  }
  if (marcado === true || marcado === false) {
    parametros.set('marcado', String(marcado));
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
 * archiva nada sin decir por que. Con EDITAR viaja ademas `textoNuevo`, el
 * texto que queda visible (1.5.0); en las demas acciones no se manda.
 *
 * Quien firma sale del token, no de aqui.
 *
 * @param {string} comentarioId
 * @param {{accion: string, motivo: string, textoNuevo?: string}} decision
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{comentario: object, asiento: object, autorNotificado: boolean}>}
 * @throws {ErrorDeApi} 400 sin motivo (o EDITAR sin texto), 409
 *   `TRANSICION_INVALIDA` si otro se adelanto o la marca ya estaba como se pide
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

/**
 * Los bytes de una imagen de un comentario en revision (1.5.0).
 *
 * Las imagenes de un comentario que no esta PUBLICADO no son publicas: el
 * servicio solo se las da a su autor y a moderacion, y un `<img src>` no manda
 * el token. Por eso se piden con `fetch` (el interceptor pone la sesion) y la
 * vista las pinta desde un `blob:`. Para cualquier otro, el servicio responde
 * 404 igual que si no existiera.
 *
 * @param {string} imagenId
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<Blob|null>} `null` si ya no existe (o no se puede ver)
 * @throws {ErrorDeApi} cualquier otro fallo del servicio
 */
export async function imagenParaModeracion(
  imagenId,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(urlDeImagen(imagenId), { method: 'GET' });
  if (respuesta.status === 404) {
    return null;
  }
  if (!respuesta.ok) {
    throw new ErrorDeApi(await cuerpoDelProblema(respuesta), respuesta.status);
  }
  return respuesta.blob();
}
