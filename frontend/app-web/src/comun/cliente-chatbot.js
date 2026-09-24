/**
 * Acceso HTTP al asistente (chatbot) — HU-CHA-001, HU-CHA-008, HU-CHA-011.
 *
 * Habla con `/api/v1/chat/**` de `contracts/openapi/ms-chatbot.yaml`. Lo usa
 * la ventana del asistente, que está en todas las vistas, y por eso vive en
 * `comun/`: el panel de administración del chatbot (`cuentas/`) reutiliza de
 * aquí la base de la API y la clase de error.
 *
 * ## Identidad
 *
 * El chat funciona para visitantes y para jugadores con sesión (CA de
 * HU-CHA-001). Cada petición lleva:
 *
 * - `Authorization: Bearer <token>` solo si hay sesión vigente (`leerSesion`
 *   ya descarta un token caducado).
 * - `X-Id-Sesion-Anonima` SIEMPRE. Con token válido el servicio la ignora;
 *   sin él, es la que identifica la conversación. Mandarla siempre evita que
 *   una sesión que vence a mitad de conversación termine en un 400.
 *
 * El identificador del visitante se guarda en `sessionStorage`, igual que el
 * token: dura lo que la pestaña y no queda en un equipo compartido.
 *
 * ## Base de la API
 *
 * No se usa `nexus-api-base`: varias vistas lo apuntan a SU servicio, y la
 * ventana del asistente vive en todas. Por omisión, mismo origen (el borde).
 * Para desarrollo local, `localStorage['nexus.chatbot.base']` la sobrescribe,
 * p. ej. `http://localhost:8094`.
 *
 * @module comun/cliente-chatbot
 */

import { leerSesion } from './sesion.js';

/** Clave del identificador de visitante en `sessionStorage`. */
export const CLAVE_SESION_ANONIMA = 'nexus.chatbot.sesionAnonima';

/** Clave de la base de la API en `localStorage`, solo para desarrollo local. */
export const CLAVE_BASE_LOCAL = 'nexus.chatbot.base';

/** Título del 404 que el borde devuelve para un prefijo sin servicio. */
const TITULO_RUTA_SIN_SERVICIO = 'Ruta sin servicio en el borde';

/**
 * Base de la API del asistente, sin barra final. Cadena vacía = mismo origen.
 *
 * @param {Storage|null} [almacenLocal]
 * @returns {string}
 */
export function baseDelChatbot(almacenLocal = almacenSeguro('localStorage')) {
  let base = '';
  try {
    base = almacenLocal?.getItem(CLAVE_BASE_LOCAL) ?? '';
  } catch {
    base = '';
  }
  return String(base).trim().replace(/\/+$/, '');
}

/**
 * Ruta completa de un recurso del asistente, con el prefijo `/api/v1`.
 *
 * @param {string} recurso p. ej. `/chat/historial`
 * @param {Storage|null} [almacenLocal]
 * @returns {string}
 */
export function rutaDelChatbot(recurso, almacenLocal) {
  return `${baseDelChatbot(almacenLocal)}/api/v1${recurso}`;
}

/**
 * El identificador de visitante de esta pestaña; lo crea si no existe.
 *
 * @param {Storage|null} [almacen]
 * @param {() => string} [generar]
 * @returns {string}
 */
export function idDeSesionAnonima(almacen = almacenSeguro('sessionStorage'), generar = nuevoId) {
  try {
    const existente = almacen?.getItem(CLAVE_SESION_ANONIMA);
    if (existente) {
      return existente;
    }
    const nuevo = `visitante-${generar()}`;
    almacen?.setItem(CLAVE_SESION_ANONIMA, nuevo);
    return nuevo;
  } catch {
    // Sin almacenamiento (modo privado estricto): la conversación dura lo que
    // la vista, que es mejor que no poder chatear.
    return `visitante-${generar()}`;
  }
}

/**
 * Una llamada al asistente que no salió bien, ya interpretada.
 *
 * Se decide por `estado`, nunca por el texto (MAPEO-ERRORES §2). El texto que
 * ve el usuario lo escribe la vista, que conoce el contexto.
 */
export class ErrorDelChatbot extends Error {
  /**
   * @param {{title?: string, detail?: string, type?: string}|null} problema
   * @param {number} estado 0 si no hubo respuesta HTTP
   * @param {{rutaFija?: boolean}} [opciones] `rutaFija`: la ruta llamada existe
   *   siempre en el servicio, así que un 404 solo puede venir de un proxy o de
   *   un servidor que no es el asistente (p. ej. Live Server sin la base local)
   */
  constructor(problema, estado, { rutaFija = false } = {}) {
    super(problema?.detail || problema?.title || 'El asistente no respondió.');
    this.name = 'ErrorDelChatbot';
    this.estado = estado;
    this.titulo = problema?.title ?? null;
    this.detalle = problema?.detail ?? null;
    this.problema = problema ?? null;
    this.rutaFija = rutaFija;
  }

  /**
   * El servicio no está (red caída, 502/503/504, o el borde todavía no tiene
   * la ruta). Es el caso de CA-03 de HU-CHA-001: se informa y se ofrece la
   * vía alternativa, no se muestra como un error del usuario.
   */
  get noDisponible() {
    return (
      this.estado === 0 ||
      this.estado === 502 ||
      this.estado === 503 ||
      this.estado === 504 ||
      (this.estado === 404 && (this.rutaFija || this.titulo === TITULO_RUTA_SIN_SERVICIO))
    );
  }

  /** Sesión caducada o rol insuficiente: no hay nada que reintentar. */
  get sinPermiso() {
    return this.estado === 401 || this.estado === 403;
  }
}

/**
 * Cliente del chat. Todo es inyectable para las pruebas.
 *
 * @param {{fetch?: typeof fetch, almacen?: Storage|null, almacenLocal?: Storage|null,
 *          sesion?: () => {autenticado: boolean, token: string|null}}} [opciones]
 */
export function crearClienteChatbot({
  fetch: peticion = (...argumentos) => globalThis.fetch(...argumentos),
  almacen = almacenSeguro('sessionStorage'),
  almacenLocal = almacenSeguro('localStorage'),
  sesion = () => leerSesion(),
} = {}) {
  async function llamar(metodo, recurso, cuerpo, { rutaFija = false } = {}) {
    const cabeceras = {
      Accept: 'application/json',
      'X-Id-Sesion-Anonima': idDeSesionAnonima(almacen),
    };
    const actual = sesion();
    if (actual?.autenticado && actual.token) {
      cabeceras.Authorization = `Bearer ${actual.token}`;
    }
    if (cuerpo !== undefined) {
      cabeceras['Content-Type'] = 'application/json';
    }

    let respuesta;
    try {
      respuesta = await peticion(rutaDelChatbot(recurso, almacenLocal), {
        method: metodo,
        headers: cabeceras,
        body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      });
    } catch {
      throw new ErrorDelChatbot(null, 0, { rutaFija });
    }

    if (!respuesta.ok) {
      throw new ErrorDelChatbot(await leerProblema(respuesta), respuesta.status, { rutaFija });
    }
    if (respuesta.status === 204) {
      return null;
    }
    return respuesta.json();
  }

  return {
    /**
     * @param {string} contenido
     * @param {string|null} [adjuntoUrl]
     * @returns {Promise<{id: string, remitente: string, contenido: string,
     *          adjuntoUrl: string|null, fechaEnvio: string}>} la respuesta del bot
     */
    enviarMensaje(contenido, adjuntoUrl = null) {
      const cuerpo = { contenido };
      if (adjuntoUrl) {
        cuerpo.adjuntoUrl = adjuntoUrl;
      }
      return llamar('POST', '/chat/mensajes', cuerpo, { rutaFija: true });
    },

    /** @returns {Promise<Array<object>>} del más antiguo al más reciente */
    async obtenerHistorial() {
      return (await llamar('GET', '/chat/historial', undefined, { rutaFija: true })) ?? [];
    },

    /** @returns {Promise<null>} */
    limpiarHistorial() {
      return llamar('DELETE', '/chat/historial', undefined, { rutaFija: true });
    },

    /**
     * @param {string} mensajeId id de un mensaje del bot
     * @param {boolean} util
     * @param {string|null} [comentario]
     */
    calificar(mensajeId, util, comentario = null) {
      const cuerpo = { util };
      if (comentario && comentario.trim()) {
        cuerpo.comentario = comentario.trim();
      }
      return llamar('POST', `/chat/mensajes/${encodeURIComponent(mensajeId)}/calificacion`, cuerpo);
    },
  };
}

/**
 * Lee el cuerpo problem+json de una respuesta fallida. Si no lo hay (p. ej.
 * una página de error del proxy), devuelve null.
 *
 * @param {Response} respuesta
 * @returns {Promise<object|null>}
 */
export async function leerProblema(respuesta) {
  try {
    return await respuesta.json();
  } catch {
    return null;
  }
}

function almacenSeguro(nombre) {
  try {
    return globalThis[nombre] ?? null;
  } catch {
    return null;
  }
}

function nuevoId() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
