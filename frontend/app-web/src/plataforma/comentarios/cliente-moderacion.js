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
 * Contrato 1.10.0 (HU-COM-005): la cola acepta ademas `categoria` y
 * `prioridadElevada` como filtros opcionales.
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
  // comentarios.yaml 1.8.0: un reporte encola sin ocultar, asi que se aprueba
  // tambien uno PUBLICADO —pero solo si tiene reportes pendientes: sin ellos,
  // el servicio contesta 409 (otro moderador ya lo atendio)—.
  {
    valor: 'APROBAR',
    etiqueta: 'Aprobar',
    desde: ['EN_REVISION', 'PUBLICADO'],
    publicadoConReportes: true,
  },
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
 * Las decisiones que atienden los reportes recibidos hasta ese momento
 * (comentarios.yaml 1.8.0, `AccionDeModeracion.RESUELVEN_REPORTES`): un
 * reporte anterior a una de ellas ya no esta pendiente. MARCAR y DESMARCAR no.
 */
export const ACCIONES_QUE_RESUELVEN_REPORTES = Object.freeze([
  'APROBAR',
  'OCULTAR',
  'ELIMINAR',
  'RESTAURAR',
  'EDITAR',
]);

/**
 * Si el comentario tiene reportes PENDIENTES: alguno posterior a la ultima
 * decision que atiende reportes. Es la misma regla que aplica el servicio, con
 * lo que ya trae el detalle (sus reportes y su historial).
 *
 * @param {{reportes?: Array<{fecha?: string}>, historial?: Array<{accion?: string, fecha?: string}>}} detalle
 * @returns {boolean}
 */
export function hayReportesPendientes(detalle) {
  const reportes = detalle?.reportes ?? [];
  if (reportes.length === 0) {
    return false;
  }
  const ultimaDecision = (detalle?.historial ?? [])
    .filter((a) => ACCIONES_QUE_RESUELVEN_REPORTES.includes(a.accion))
    .reduce((max, a) => Math.max(max, Date.parse(a.fecha ?? '') || 0), 0);
  return reportes.some((r) => (Date.parse(r.fecha ?? '') || 0) > ultimaDecision);
}

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

/**
 * Motivos de rechazo que enumera el contrato 1.3.0 en `ProblemDetail.motivo`.
 *
 * `REPORTE_INVALIDO` (400 de reportar) lo emite el servicio pero el contrato
 * todavía no lo enumera: la vista lo reconoce y, además, decide por `estado`.
 */
export const MOTIVO_MODERACION = Object.freeze({
  REPORTE_DUPLICADO: 'REPORTE_DUPLICADO',
  REPORTE_INVALIDO: 'REPORTE_INVALIDO',
  LIMITE_DE_REPORTES: 'LIMITE_DE_REPORTES',
  TRANSICION_INVALIDA: 'TRANSICION_INVALIDA',
  // comentarios.yaml 1.10.0: ELIMINAR en lote sin la confirmacion exacta (400).
  CONFIRMACION_REQUERIDA: 'CONFIRMACION_REQUERIDA',
});

/**
 * Las acciones que valen en lote (`AccionDeLote`, comentarios.yaml 1.10.0): las
 * de `ACCIONES` menos EDITAR, que necesita un texto distinto por comentario.
 */
export const ACCIONES_EN_LOTE = Object.freeze(
  ACCIONES.filter((a) => a.valor !== 'EDITAR').map(({ valor, etiqueta }) =>
    Object.freeze({ valor, etiqueta }),
  ),
);

/** Valor exacto de `confirmacion` que exige ELIMINAR en lote (7.3.9, contrato 1.10.0). */
export const CONFIRMACION_DE_ELIMINAR = 'ELIMINAR';

/** Largos del contrato (`DecisionRequest`). */
export const MOTIVO_MINIMO = 3;
export const MOTIVO_MAXIMO = 500;
export const TEXTO_NUEVO_MAXIMO = 2000;

/**
 * Que mirar en la cola (`marcado`, 1.5.0): sin filtro, lo pendiente —los
 * EN_REVISION y, desde la 1.8.0, los PUBLICADOS con reportes pendientes—;
 * `true`, la lista de seguimiento (los marcados en cualquier estado salvo
 * ELIMINADO); `false`, lo pendiente sin marcar.
 */
export const FILTROS_DE_COLA = Object.freeze([
  { valor: 'en-revision', etiqueta: 'Pendientes de revisión', marcado: null },
  { valor: 'marcados', etiqueta: 'Marcados para seguimiento', marcado: true },
  { valor: 'sin-marcar', etiqueta: 'Pendientes sin marcar', marcado: false },
]);

/**
 * Que prioridad mirar en la cola (`prioridadElevada`, 1.10.0): todas, solo las de
 * prioridad elevada o solo las demas. `prioridadElevada: null` no viaja.
 */
export const FILTROS_DE_PRIORIDAD = Object.freeze([
  { valor: 'todas', etiqueta: 'Todas', prioridadElevada: null },
  { valor: 'elevada', etiqueta: 'Solo prioridad elevada', prioridadElevada: true },
  { valor: 'sin-elevada', etiqueta: 'Sin prioridad elevada', prioridadElevada: false },
]);

/**
 * @param {string} estado estado actual del comentario
 * @param {boolean} [marcado] si tiene la marca de seguimiento (1.5.0)
 * @param {{reportesPendientes?: boolean}} [contexto] si tiene reportes pendientes
 *   ({@link hayReportesPendientes}): sin ellos no se ofrece aprobar uno PUBLICADO
 * @returns {Array<{valor: string, etiqueta: string}>} acciones que tienen sentido
 */
export function accionesDesde(estado, marcado = false, { reportesPendientes = false } = {}) {
  return ACCIONES.filter(
    (a) =>
      a.desde.includes(estado) &&
      (a.marca === undefined || a.marca === Boolean(marcado)) &&
      !(a.publicadoConReportes && estado === 'PUBLICADO' && !reportesPendientes),
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

/**
 * @param {string} url
 * @param {object} opciones
 * @param {Function} fetchImpl
 * @param {new (problema: object|null, estado: number) => ErrorDeApi} [FabricaDeError] el
 *   error que se lanza ante un rechazo; por omision {@link ErrorDeApi}, asi que
 *   quien no la pasa se comporta como siempre
 */
async function pedir(url, opciones, fetchImpl, FabricaDeError = ErrorDeApi) {
  const respuesta = await fetchImpl(url, opciones);
  if (respuesta.ok) {
    return respuesta.status === 204 ? null : respuesta.json();
  }
  throw new FabricaDeError(await cuerpoDelProblema(respuesta), respuesta.status);
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
 * @param {{productoId?: string|null, marcado?: boolean|null, categoria?: string|null,
 *   prioridadElevada?: boolean|null, pagina?: number, tamano?: number}} [filtro]
 *   `marcado` (1.5.0): `true` la lista de seguimiento, `false` los en revision
 *   sin marcar; `null` no viaja y el servicio da la cola de siempre.
 *   `categoria` y `prioridadElevada` (1.10.0) filtran ademas: «Todas» llega aqui como
 *   `null` o `''` y no genera parametro. El servicio decide si una categoria existe.
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{entradas: object[], total: number, pagina: number, tamano: number}>}
 */
export async function consultarCola(
  {
    productoId = null,
    marcado = null,
    categoria = null,
    prioridadElevada = null,
    pagina = 0,
    tamano = 20,
  } = {},
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const parametros = new URLSearchParams({ pagina: String(pagina), tamano: String(tamano) });
  if (productoId) {
    parametros.set('productoId', productoId);
  }
  if (marcado === true || marcado === false) {
    parametros.set('marcado', String(marcado));
  }
  if (typeof categoria === 'string' && categoria !== '') {
    parametros.set('categoria', categoria);
  }
  if (prioridadElevada === true || prioridadElevada === false) {
    parametros.set('prioridadElevada', String(prioridadElevada));
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
 * Los comentarios de un autor, del mas reciente al mas antiguo y en cualquier
 * estado (HU-COM-005, comentarios.yaml 1.7.0). Solo roles de moderacion.
 *
 * Un autor sin comentarios es `200` con lista vacia y `total: 0`, no un 404.
 *
 * @param {string} autorId
 * @param {{pagina?: number, tamano?: number}} [paginacion] `tamano` hasta 100
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{autorId: string, apodoAutor?: string, comentarios: object[],
 *   total: number, pagina: number, tamano: number}>}
 * @throws {ErrorDeApi} 401 sin sesion, 403 si el rol no modera
 */
export async function historialDelAutor(
  autorId,
  { pagina = 0, tamano = 20 } = {},
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const parametros = new URLSearchParams({ pagina: String(pagina), tamano: String(tamano) });
  return pedir(
    `${rutaDeModeracion()}/autores/${encodeURIComponent(autorId)}/comentarios?${parametros}`,
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
 * El rechazo de la decision en lote: un {@link ErrorDeApi} que ademas dice que
 * comentarios lo causaron (`comentarioIds` del problem detail: en el 404 los que
 * no existen, en el 409 los que no admiten la accion), en el orden en que los
 * dio el servicio.
 *
 * Es una subclase y no un cambio en `ErrorDeApi` porque ese error lo comparten
 * otras vistas, que no necesitan la lista. Del problema solo se leen textos:
 * lo demas que traiga `comentarioIds` se ignora.
 */
export class ErrorDeLote extends ErrorDeApi {
  /**
   * @param {{comentarioIds?: unknown}|null} problema
   * @param {number} estado
   */
  constructor(problema, estado) {
    super(problema, estado);
    const ids = problema?.comentarioIds;
    /** @type {string[]} */
    this.comentarioIds = Array.isArray(ids) ? ids.filter((id) => typeof id === 'string') : [];
  }
}

/**
 * @typedef {object} DecisionEnLote
 * @property {string[]} comentarioIds
 * @property {string} accion una de {@link ACCIONES_EN_LOTE}
 * @property {string} motivo
 * @property {string} [confirmacion] solo cuenta con ELIMINAR: {@link CONFIRMACION_DE_ELIMINAR}
 *
 * @typedef {object} ItemDeDecisionEnLote
 * @property {string} comentarioId
 * @property {object} asiento
 * @property {boolean} [autorNotificado] `false` no invalida la decision
 *
 * @typedef {object} ResultadoDelLote
 * @property {string} accion
 * @property {number} total
 * @property {ItemDeDecisionEnLote[]} resultados
 */

/**
 * La misma decision para varios comentarios a la vez, atomica — RF-COM-008,
 * comentarios.yaml 1.10.1: o se resuelven todos o no cambia ninguno. Solo roles
 * de moderacion.
 *
 * Quien firma sale del token, no de aqui: aunque `decision` traiga otros campos,
 * el cuerpo lleva solo los ids, la accion, el motivo y, con ELIMINAR, la
 * confirmacion. No se validan topes ni largos: los exige el servicio.
 *
 * @param {DecisionEnLote} decision
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<ResultadoDelLote>}
 * @throws {ErrorDeLote} 400 (entrada o `CONFIRMACION_REQUERIDA`), 401, 403, 404 y 409
 *   con `comentarioIds`
 */
export async function resolverEnLote(
  { comentarioIds, accion, motivo, confirmacion },
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const cuerpo = { comentarioIds, accion, motivo };
  if (accion === 'ELIMINAR') {
    cuerpo.confirmacion = confirmacion;
  }
  return pedir(
    `${rutaDeModeracion()}/decisiones-en-lote`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(cuerpo),
    },
    fetchImpl,
    ErrorDeLote,
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
