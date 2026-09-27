/**
 * Las pestañas del mismo navegador hablan de la sesión entre sí — R17.
 *
 * ## Por qué hace falta
 *
 * La sesión vive en `sessionStorage`, que es **de cada pestaña**. Es una
 * decisión buena para la seguridad (el token no sobrevive a cerrar el
 * navegador ni queda en disco como con `localStorage`), pero tiene dos
 * consecuencias que alguien que no conoce el juego vive como fallos:
 *
 * 1. Abre un enlace del juego en una pestaña nueva y le pide entrar otra vez,
 *    aunque la pestaña de al lado tenga la sesión abierta.
 * 2. Cierra sesión en una pestaña y las demás siguen dentro.
 *
 * Aquí se resuelven las dos con un `BroadcastChannel` del propio origen:
 *
 *   - `cerrada`   — una pestaña cerró sesión; las demás la olvidan también.
 *   - `pedir`     — una pestaña sin sesión pregunta si alguna tiene una.
 *   - `compartir` — respuesta a `pedir`, dirigida a quien preguntó.
 *
 * ## Lo que NO hace
 *
 * No guarda nada y no inventa sesiones: solo reparte la que ya existe en otra
 * pestaña del mismo navegador. El canal no sale del origen (lo garantiza el
 * navegador), y quien podría escucharlo —un script del propio origen— ya
 * podría leer `sessionStorage` directamente.
 *
 * Sin `BroadcastChannel` (navegadores muy antiguos, o las pruebas en jsdom)
 * todo se degrada a no hacer nada: cada pestaña vuelve a ser independiente,
 * que es exactamente como funcionaba antes.
 *
 * @module comun/canal-sesion
 */

/** Nombre del canal. Un solo sitio lo escribe. */
export const NOMBRE_CANAL = 'nexus.sesion';

/** Tipos de mensaje del canal. */
export const MENSAJES = Object.freeze({
  CERRADA: 'cerrada',
  PEDIR: 'pedir',
  COMPARTIR: 'compartir',
});

/** Lo que se espera una respuesta antes de dar por hecho que no hay nadie. */
export const ESPERA_POR_OMISION_MS = 400;

/**
 * Abre el canal, o devuelve `null` si el navegador no lo tiene.
 *
 * @param {typeof BroadcastChannel|undefined} [Fabrica]
 * @returns {BroadcastChannel|null}
 */
export function abrirCanal(Fabrica = globalThis.BroadcastChannel) {
  if (typeof Fabrica !== 'function') {
    return null;
  }
  try {
    return new Fabrica(NOMBRE_CANAL);
  } catch {
    return null;
  }
}

/**
 * Avisa a las demás pestañas de que la sesión se cerró.
 *
 * Se cierra el canal nada más publicar: el mensaje ya está en la cola de cada
 * destinatario, y esta página está a punto de navegar al login.
 *
 * @param {typeof BroadcastChannel|undefined} [Fabrica]
 */
export function difundirCierre(Fabrica = globalThis.BroadcastChannel) {
  const canal = abrirCanal(Fabrica);
  if (!canal) {
    return;
  }
  try {
    canal.postMessage({ tipo: MENSAJES.CERRADA });
  } catch {
    // Sin canal utilizable: las demás pestañas lo descubrirán en su próxima
    // llamada, que el servidor ya no aceptará cuando el token caduque.
  } finally {
    canal.close();
  }
}

function identificador() {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

/**
 * Pregunta a las demás pestañas por una sesión abierta.
 *
 * Resuelve con la primera que conteste, o con `null` si nadie lo hace en
 * `espera` milisegundos. Nunca rechaza: preguntar es opcional.
 *
 * @param {{Fabrica?: typeof BroadcastChannel, espera?: number, reloj?: {setTimeout: Function, clearTimeout: Function}}} [opciones]
 * @returns {Promise<{token: string, apodo?: string, rol?: string, uid?: string}|null>}
 */
export function pedirSesionAOtraPestana({
  Fabrica = globalThis.BroadcastChannel,
  espera = ESPERA_POR_OMISION_MS,
  reloj = globalThis,
} = {}) {
  const canal = abrirCanal(Fabrica);
  if (!canal) {
    return Promise.resolve(null);
  }
  const id = identificador();
  return new Promise((resolver) => {
    let temporizador = null;
    const terminar = (sesion) => {
      reloj.clearTimeout(temporizador);
      canal.onmessage = null;
      canal.close();
      resolver(sesion);
    };
    canal.onmessage = (evento) => {
      const mensaje = evento?.data;
      if (
        mensaje?.tipo === MENSAJES.COMPARTIR &&
        mensaje.para === id &&
        typeof mensaje.sesion?.token === 'string' &&
        mensaje.sesion.token !== ''
      ) {
        terminar(mensaje.sesion);
      }
    };
    temporizador = reloj.setTimeout(() => terminar(null), espera);
    try {
      canal.postMessage({ tipo: MENSAJES.PEDIR, id });
    } catch {
      terminar(null);
    }
  });
}

/**
 * Escucha el canal en una pestaña con sesión: contesta a quien pregunta y se
 * entera de los cierres hechos en otra pestaña.
 *
 * @param {{
 *   alCerrarse: () => void,
 *   sesionParaCompartir: () => ({token: string, apodo?: string, rol?: string, uid?: string}|null),
 *   Fabrica?: typeof BroadcastChannel,
 * }} opciones
 * @returns {() => void} deja de escuchar
 */
export function escucharCanal({
  alCerrarse,
  sesionParaCompartir,
  Fabrica = globalThis.BroadcastChannel,
}) {
  const canal = abrirCanal(Fabrica);
  if (!canal) {
    return () => {};
  }
  canal.onmessage = (evento) => {
    const mensaje = evento?.data;
    if (mensaje?.tipo === MENSAJES.CERRADA) {
      alCerrarse();
      return;
    }
    if (mensaje?.tipo === MENSAJES.PEDIR && typeof mensaje.id === 'string') {
      const sesion = sesionParaCompartir();
      if (sesion?.token) {
        canal.postMessage({ tipo: MENSAJES.COMPARTIR, para: mensaje.id, sesion });
      }
    }
  };
  return () => {
    canal.onmessage = null;
    canal.close();
  };
}
