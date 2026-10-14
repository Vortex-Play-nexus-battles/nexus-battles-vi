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
 * token: dura lo que la pestaña y no queda en un equipo compartido. #506 —
 * además se olvida al cerrar sesión y al entrar (`sesion.js`, que es donde
 * vive la clave): la persona que use la pestaña después no hereda la
 * conversación de visitante de la anterior.
 *
 * ## Errores y tiempo de espera (#506)
 *
 * Un rechazo llega como problem details con `motivo` (contrato 1.2.0 y
 * 1.4.0); `ErrorDelChatbot` lo expone junto al estado y, si el servidor lo
 * dice, los segundos de `Retry-After`. Una petición que no responde en
 * `TIEMPO_DE_ESPERA_MS` se corta y cuenta como un fallo de red (estado 0).
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

import { CLAVE_SESION_ANONIMA, leerSesion } from './sesion.js';

/**
 * Clave del identificador de visitante en `sessionStorage`. La define
 * `sesion.js`, que la borra al cerrar sesión y al entrar (#506).
 */
export { CLAVE_SESION_ANONIMA };

/** Clave de la base de la API en `localStorage`, solo para desarrollo local. */
export const CLAVE_BASE_LOCAL = 'nexus.chatbot.base';

/**
 * #506 — lo que se espera una respuesta antes de cortar la petición. Pasado
 * este tiempo la ventana dice que el asistente no responde, en vez de dejar
 * «El asistente está escribiendo…» para siempre.
 */
export const TIEMPO_DE_ESPERA_MS = 10_000;

/** Título del 404 que el borde devuelve para un prefijo sin servicio. */
const TITULO_RUTA_SIN_SERVICIO = 'Ruta sin servicio en el borde';

/**
 * El 503 de la lista negra (contrato 1.2.0): el asistente SÍ respondió, lo que
 * no pudo es revisar el texto. No es «el asistente no está».
 */
const MODERACION_NO_DISPONIBLE = 'MODERACION_NO_DISPONIBLE';

/**
 * Los segundos que pide esperar una cabecera `Retry-After`, o `null`.
 *
 * El contrato manda segundos enteros; HTTP también admite una fecha, y se
 * entiende igual (lo que falta hasta ella, redondeado hacia arriba). Una
 * fecha HTTP siempre lleva el nombre del día: sin letras no se intenta, porque
 * `Date.parse` lee «-5» o «1.5» como fechas de 2001.
 *
 * @param {string|null|undefined} valor
 * @param {() => number} [ahora] milisegundos, inyectable en pruebas
 * @returns {number|null}
 */
export function segundosDeEspera(valor, ahora = () => Date.now()) {
  const texto = String(valor ?? '').trim();
  if (!texto) {
    return null;
  }
  if (/^\d+$/.test(texto)) {
    return Number(texto);
  }
  if (!/[a-z]/i.test(texto)) {
    return null;
  }
  const fecha = Date.parse(texto);
  if (Number.isNaN(fecha)) {
    return null;
  }
  return Math.max(0, Math.ceil((fecha - ahora()) / 1000));
}

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
 * Se decide por `estado` y por `motivo`, nunca por el texto (MAPEO-ERRORES
 * §2). El texto que ve el usuario lo escribe la vista, que conoce el contexto.
 */
export class ErrorDelChatbot extends Error {
  /**
   * @param {{title?: string, detail?: string, type?: string, motivo?: string}|null} problema
   * @param {number} estado 0 si no hubo respuesta HTTP (o se agotó la espera)
   * @param {{rutaFija?: boolean, reintentarEn?: number|null, tiempoAgotado?: boolean}} [opciones]
   *   `rutaFija`: la ruta llamada existe siempre en el servicio, así que un 404
   *   solo puede venir de un proxy o de un servidor que no es el asistente
   *   (p. ej. Live Server sin la base local). `reintentarEn`: los segundos de
   *   `Retry-After`. `tiempoAgotado`: la petición se cortó por no responder.
   */
  constructor(
    problema,
    estado,
    { rutaFija = false, reintentarEn = null, tiempoAgotado = false } = {},
  ) {
    super(problema?.detail || problema?.title || 'El asistente no respondió.');
    this.name = 'ErrorDelChatbot';
    this.estado = estado;
    this.titulo = problema?.title ?? null;
    this.detalle = problema?.detail ?? null;
    this.problema = problema ?? null;
    this.rutaFija = rutaFija;
    /** #506 — el `motivo` del problem details (CONTENIDO_BLOQUEADO…), o null. */
    this.motivo = typeof problema?.motivo === 'string' ? problema.motivo : null;
    /** #506 — segundos de `Retry-After` (sobre todo en un 429), o null. */
    this.reintentarEn = Number.isFinite(reintentarEn) ? reintentarEn : null;
    this.tiempoAgotado = Boolean(tiempoAgotado);
  }

  /**
   * El servicio no está (red caída, 502/503/504, o el borde todavía no tiene
   * la ruta). Es el caso de CA-03 de HU-CHA-001: se informa y se ofrece la
   * vía alternativa, no se muestra como un error del usuario.
   *
   * #506 — el 503 con `motivo` MODERACION_NO_DISPONIBLE no cuenta: el
   * asistente respondió, lo que no pudo es revisar ese texto.
   */
  get noDisponible() {
    if (this.motivo === MODERACION_NO_DISPONIBLE) {
      return false;
    }
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
 *          sesion?: () => {autenticado: boolean, token: string|null},
 *          tiempoDeEspera?: number}} [opciones] `tiempoDeEspera`: milisegundos
 *          antes de cortar una petición que no responde (#506)
 */
export function crearClienteChatbot({
  fetch: peticion = (...argumentos) => globalThis.fetch(...argumentos),
  almacen = almacenSeguro('sessionStorage'),
  almacenLocal = almacenSeguro('localStorage'),
  sesion = () => leerSesion(),
  tiempoDeEspera = TIEMPO_DE_ESPERA_MS,
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

    // #506 — sin respuesta en `tiempoDeEspera`, la petición se corta (también
    // si ya llegaron las cabeceras y lo que se atasca es el cuerpo).
    const control = typeof AbortController === 'function' ? new AbortController() : null;
    const temporizador = control ? setTimeout(() => control.abort(), tiempoDeEspera) : null;
    const agotado = () => Boolean(control?.signal.aborted);
    try {
      let respuesta;
      try {
        respuesta = await peticion(rutaDelChatbot(recurso, almacenLocal), {
          method: metodo,
          headers: cabeceras,
          body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
          signal: control?.signal,
        });
      } catch {
        throw new ErrorDelChatbot(null, 0, { rutaFija, tiempoAgotado: agotado() });
      }

      if (!respuesta.ok) {
        throw new ErrorDelChatbot(await leerProblema(respuesta), respuesta.status, {
          rutaFija,
          reintentarEn: segundosDeEspera(respuesta.headers?.get?.('Retry-After')),
        });
      }
      if (respuesta.status === 204) {
        return null;
      }
      return await leerCuerpo(respuesta, agotado, rutaFija);
    } finally {
      clearTimeout(temporizador);
    }
  }

  return {
    /**
     * @param {string} contenido
     * @param {{vista?: string|null}} [opciones] `vista`: la sección donde
     *   está el jugador (`INICIO`, `INVENTARIO`…), para la respuesta contextual
     * @returns {Promise<{id: string, remitente: string, contenido: string,
     *          adjuntoUrl: string|null, fechaEnvio: string, enriquecido?: object|null}>}
     *          la respuesta del bot
     */
    enviarMensaje(contenido, { vista = null } = {}) {
      const cuerpo = { contenido };
      if (vista) {
        cuerpo.vista = vista;
      }
      return llamar('POST', '/chat/mensajes', cuerpo, { rutaFija: true });
    },

    /**
     * Sin opciones, toda la conversación. Con `limite` (y opcionalmente
     * `antesDe`, el id del mensaje más antiguo que ya se tiene), una página:
     * los más recientes, o los anteriores a ese mensaje.
     *
     * @param {{antesDe?: string|null, limite?: number|null}} [pagina]
     * @returns {Promise<Array<object>>} del más antiguo al más reciente
     */
    async obtenerHistorial({ antesDe = null, limite = null } = {}) {
      const parametros = new URLSearchParams();
      if (antesDe) {
        parametros.set('antesDe', antesDe);
      }
      if (limite) {
        parametros.set('limite', String(limite));
      }
      const consulta = parametros.toString();
      const ruta = consulta ? `/chat/historial?${consulta}` : '/chat/historial';
      return (await llamar('GET', ruta, undefined, { rutaFija: true })) ?? [];
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

    /**
     * Abre una solicitud de soporte humano (solo con sesión; el servidor
     * responde 401 SESION_REQUERIDA a un visitante).
     *
     * @param {{categoria: string, asunto: string, mensaje: string}} datos
     * @returns {Promise<object>} la solicitud como la ve el jugador
     */
    abrirTicket({ categoria, asunto, mensaje }) {
      return llamar('POST', '/chat/tickets', { categoria, asunto, mensaje }, { rutaFija: true });
    },

    /**
     * Preguntas rápidas, temas frecuentes o autocompletado (con `q`). Salen
     * de la versión en producción de la base de conocimiento.
     *
     * @param {{q?: string, categoria?: string|null, limite?: number}} [filtro]
     * @returns {Promise<Array<{clave: string, titulo: string, categoria: string, pregunta: string}>>}
     */
    async sugerencias({ q = '', categoria = null, limite = 6 } = {}) {
      const parametros = new URLSearchParams();
      if (q && q.trim()) {
        parametros.set('q', q.trim().slice(0, 100));
      }
      if (categoria) {
        parametros.set('categoria', categoria);
      }
      parametros.set('limite', String(limite));
      return (
        (await llamar('GET', `/chat/sugerencias?${parametros.toString()}`, undefined, {
          rutaFija: true,
        })) ?? []
      );
    },

    /**
     * Preferencias de respuesta de esta sesión (idioma y nivel de detalle).
     * Sin identidad, el servidor responde las de por defecto.
     *
     * @returns {Promise<{idioma: string, nivelDetalle: string}>}
     */
    preferencias() {
      return llamar('GET', '/chat/preferencias', undefined, { rutaFija: true });
    },

    /**
     * Guarda las preferencias (el par completo).
     *
     * @param {{idioma: string, nivelDetalle: string}} preferencias
     * @returns {Promise<{idioma: string, nivelDetalle: string}>} las guardadas
     */
    guardarPreferencias({ idioma, nivelDetalle }) {
      return llamar('PUT', '/chat/preferencias', { idioma, nivelDetalle }, { rutaFija: true });
    },

    /** @returns {Promise<Array<object>>} las solicitudes del jugador, la más reciente primero */
    async misTickets() {
      return (await llamar('GET', '/chat/tickets', undefined, { rutaFija: true })) ?? [];
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

/**
 * El cuerpo de una respuesta correcta. Si se cortó por la espera mientras
 * llegaba, cuenta como un fallo de red (#506); otro fallo sigue tal cual.
 *
 * @param {Response} respuesta
 * @param {() => boolean} agotado
 * @param {boolean} rutaFija
 */
async function leerCuerpo(respuesta, agotado, rutaFija) {
  try {
    return await respuesta.json();
  } catch (error) {
    if (agotado()) {
      throw new ErrorDelChatbot(null, 0, { rutaFija, tiempoAgotado: true });
    }
    throw error;
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
