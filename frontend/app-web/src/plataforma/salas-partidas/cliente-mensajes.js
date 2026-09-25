/**
 * B6 — Mensajes privados: acceso HTTP, destinos STOMP y motivos de rechazo.
 *
 * FEEDBACK DEL PROFESOR, no requisito del documento: la sección 7.6 pide chat
 * en las salas y en la vista general; los mensajes privados se piden en la
 * demostración y van sobre el mismo chat (mismo servicio, mismo canal STOMP).
 *
 * Contratos:
 *   - `contracts/openapi/salas-partidas.yaml` 1.6.x, `/mensajes-directos/**`:
 *     conversaciones, historial, envío de respaldo y marcar leído.
 *   - `contracts/websocket/mensajes-directos.yaml` 1.0.x: envío por
 *     `/app/mensajes-directos/{uidDestino}` y recepción por
 *     `/usuario/cola/mensajes-directos` (mensajes y rechazos).
 *
 * El remitente NUNCA viaja: lo pone el servidor a partir del token. Aquí solo
 * se dice a quién.
 *
 * @module plataforma/salas-partidas/cliente-mensajes
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { rutaDeApi } from '../../comun/base-api.js';
import { RUTAS, resolver } from '../../comun/sesion.js';

/** Cola de usuario por la que llegan los mensajes y los rechazos. */
export const COLA_MENSAJES = '/usuario/cola/mensajes-directos';

/** Largo máximo del texto: el mismo que el chat (500). */
export const LARGO_MAXIMO = 500;

/** Mensajes por página del historial (valor por omisión del contrato). */
export const TAMANO_DE_PAGINA = 50;

/** @param {string} uidDestino */
export function destinoDeEnvio(uidDestino) {
  return `/app/mensajes-directos/${uidDestino}`;
}

const FORMA_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * ¿Es un uid? Lo que llega por `?con=` es de la URL y no se da por bueno.
 *
 * @param {unknown} valor
 * @returns {boolean}
 */
export function esUid(valor) {
  return typeof valor === 'string' && FORMA_UUID.test(valor);
}

/**
 * Identificador que pone el cliente para reconocer el eco de su envío y para
 * que un reintento no duplique (máximo 64 caracteres en el contrato).
 *
 * @returns {string}
 */
export function nuevoIdCliente() {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `c-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Enlace a la conversación con ese jugador. Es el que ponen el chat y la sala
 * de espera en el botón «Mensaje privado». El uid del otro no es un dato
 * sensible: es el identificador público que ya viaja en el chat y en la sala.
 *
 * @param {string} uid
 * @param {{base?: string}} [opciones] base de resolución, inyectable en pruebas
 * @returns {string}
 */
export function urlDeMensajesCon(uid, { base } = {}) {
  const url = new URL(base ? resolver(RUTAS.mensajes, base) : resolver(RUTAS.mensajes));
  url.searchParams.set('con', uid);
  return url.href;
}

/**
 * Qué se le dice a quien escribió, por motivo del contrato (`EnvioRechazado`).
 * El texto lo pone la interfaz; el servidor solo manda el motivo.
 */
export const RECHAZOS = Object.freeze({
  TEXTO_NO_PERMITIDO: {
    tono: 'advertencia',
    titulo: 'Tu mensaje no se envió',
    detalle: 'Contiene términos que no están permitidos. Cámbialo y vuelve a intentarlo.',
  },
  SANCIONADO: {
    tono: 'advertencia',
    titulo: 'Tienes una sanción activa',
    detalle:
      'Mientras siga vigente no puedes enviar mensajes privados. Puedes consultarla en «Mis sanciones».',
  },
  DESTINATARIO_INEXISTENTE: {
    tono: 'advertencia',
    titulo: 'Ese jugador no puede recibir mensajes',
    detalle: 'La cuenta no existe o no está activa.',
  },
  DESTINATARIO_PROPIO: {
    tono: 'advertencia',
    titulo: 'No puedes escribirte a ti mismo',
    detalle: 'Elige a otro jugador desde el chat o desde una sala.',
  },
  DEMASIADO_RAPIDO: {
    tono: 'advertencia',
    titulo: 'Vas demasiado rápido',
    detalle: 'Espera unos segundos antes de enviar otro mensaje.',
  },
  TEXTO_INVALIDO: {
    tono: 'advertencia',
    titulo: 'Revisa el mensaje',
    detalle: `No puede estar vacío ni pasar de ${LARGO_MAXIMO} caracteres.`,
  },
  MODERACION_NO_DISPONIBLE: {
    tono: 'error',
    titulo: 'No pudimos revisar tu mensaje',
    detalle: 'No se envió sin revisar. Inténtalo de nuevo en un momento.',
  },
});

const RECHAZO_DESCONOCIDO = Object.freeze({
  tono: 'error',
  titulo: 'Tu mensaje no se envió',
  detalle: 'Algo falló al enviarlo. Inténtalo de nuevo en un momento.',
});

/**
 * @param {string|null|undefined} motivo
 * @returns {{tono: string, titulo: string, detalle: string}}
 */
export function textoDeRechazo(motivo) {
  return RECHAZOS[motivo] ?? RECHAZO_DESCONOCIDO;
}

/**
 * El `type` de cada motivo en la vía REST (salas-partidas.yaml 1.6.1). Se
 * decide por el `type`, nunca por el texto (MAPEO-ERRORES, regla de oro).
 */
const MOTIVO_POR_TIPO = Object.freeze({
  'mensaje-invalido': 'TEXTO_INVALIDO',
  'destinatario-propio': 'DESTINATARIO_PROPIO',
  'jugador-sancionado': 'SANCIONADO',
  'destinatario-inexistente': 'DESTINATARIO_INEXISTENTE',
  'contenido-bloqueado': 'TEXTO_NO_PERMITIDO',
  'demasiados-mensajes': 'DEMASIADO_RAPIDO',
  'moderacion-no-disponible': 'MODERACION_NO_DISPONIBLE',
});

/**
 * El motivo del contrato a partir de un problem details de la vía REST.
 *
 * @param {{type?: string}|null|undefined} problema
 * @returns {string|null}
 */
export function motivoDeProblema(problema) {
  const tipo = String(problema?.type ?? '');
  const sufijo = tipo.slice(tipo.lastIndexOf('/') + 1);
  return MOTIVO_POR_TIPO[sufijo] ?? null;
}

/** Error de la API de mensajes, ya interpretado. */
export class ErrorDeMensajes extends Error {
  /**
   * @param {object} problema problem details tal como llegó (o uno mínimo)
   * @param {number} estado código HTTP real
   * @param {string|null} [reintentarEn] valor de `Retry-After`, si vino
   */
  constructor(problema, estado, reintentarEn = null) {
    super(problema?.detail || problema?.title || 'No se pudo completar la operación.');
    this.name = 'ErrorDeMensajes';
    this.estado = problema?.status ?? estado;
    this.tipo = problema?.type ?? null;
    this.motivo = motivoDeProblema(problema);
    const segundos = Number(reintentarEn);
    this.reintentarEnSegundos = Number.isFinite(segundos) && segundos > 0 ? segundos : null;
  }
}

async function problemaDe(respuesta) {
  try {
    const problema = await respuesta.json();
    if (problema && typeof problema === 'object') {
      return problema;
    }
  } catch {
    // Sin cuerpo (un 401 de Spring Security) o un servidor que no es la API.
  }
  return { status: respuesta.status };
}

async function comprobar(respuesta) {
  if (respuesta.ok) {
    return respuesta;
  }
  throw new ErrorDeMensajes(
    await problemaDe(respuesta),
    respuesta.status,
    respuesta.headers?.get?.('Retry-After') ?? null,
  );
}

function ruta(sufijo = '') {
  return rutaDeApi(`/mensajes-directos/conversaciones${sufijo}`);
}

/**
 * Las conversaciones del jugador de la sesión, la más reciente primero.
 *
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<Array<object>>} esquema `ResumenDeConversacion`
 */
export async function listarConversaciones({ fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await comprobar(await fetchImpl(ruta()));
  return respuesta.json();
}

/**
 * Una página de la conversación con ese jugador, de la más antigua a la más
 * reciente. Hacia atrás con `antesDe` (la fecha del primero que ya se tiene).
 *
 * @param {string} uidOtro
 * @param {{antesDe?: string|null, limite?: number, fetchImpl?: Function}} [opciones]
 * @returns {Promise<Array<object>>} esquema `MensajeDirecto`
 */
export async function historial(
  uidOtro,
  { antesDe = null, limite = TAMANO_DE_PAGINA, fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const consulta = new URLSearchParams({ limite: String(limite) });
  if (antesDe) {
    consulta.set('antesDe', antesDe);
  }
  const respuesta = await comprobar(
    await fetchImpl(`${ruta(`/${encodeURIComponent(uidOtro)}/mensajes`)}?${consulta}`),
  );
  return respuesta.json();
}

/**
 * Envío de respaldo, cuando el canal en tiempo real no está: mismas reglas
 * que por STOMP (el servidor usa la misma clase). Un reintento con el mismo
 * `idCliente` devuelve el mismo mensaje, así que se puede repetir sin miedo.
 *
 * @param {string} uidOtro
 * @param {{texto: string, idCliente?: string|null}} mensaje
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<object>} el `MensajeDirecto` guardado
 * @throws {ErrorDeMensajes} con el `motivo` del contrato
 */
export async function enviarPorRest(
  uidOtro,
  { texto, idCliente = null },
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const cuerpo = idCliente ? { texto, idCliente } : { texto };
  const respuesta = await comprobar(
    await fetchImpl(ruta(`/${encodeURIComponent(uidOtro)}/mensajes`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cuerpo),
    }),
  );
  return respuesta.json();
}

/**
 * Marca como leído lo que ese jugador te escribió. Idempotente.
 *
 * @param {string} uidOtro
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<void>}
 */
export async function marcarLeida(uidOtro, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  await comprobar(
    await fetchImpl(ruta(`/${encodeURIComponent(uidOtro)}/leido`), { method: 'POST' }),
  );
}
