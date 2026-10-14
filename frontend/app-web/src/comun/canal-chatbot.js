/**
 * Canal en tiempo real del asistente — 7.4.9 «WebSocket para comunicación en
 * tiempo real» (`contracts/websocket/chatbot.yaml` 1.0.0).
 *
 * Es la misma conversación que `cliente-chatbot.js`, por STOMP sobre
 * WebSocket (`transporte-stomp.js`, con la reconexión de
 * `canal-reconectable.js`). La ventana del asistente (`ui/ventana-chatbot.js`)
 * le pide aquí las respuestas y este módulo decide por dónde van:
 *
 * - **Con el canal abierto**, la pregunta va a `/app/chat/mensajes` con su
 *   `idCorrelacion`; «escribiendo…» sale cuando el servidor manda
 *   `ESCRIBIENDO` y la respuesta es el mismo `Mensaje` que devuelve la API.
 *   Un error del servidor (400, 422, 429, 500, 503) llega como el
 *   `ErrorDelChatbot` de siempre, con su `motivo`: la ventana lo explica igual.
 * - **Sin canal** (todavía no abrió, se cayó, se está reconectando), la
 *   pregunta va por HTTP, como antes de que existiera el canal: degradación
 *   controlada, riesgo #7 del Project Charter.
 * - **Si el canal se cae o se queda mudo con una pregunta en vuelo** (sin
 *   acuse en `PLAZOS_DEL_CANAL.acuseMs`, o sin respuesta en
 *   `respuestaMs`), esa pregunta se repite por HTTP y el canal se rehace:
 *   nunca se pierde ni se queda esperando. En el peor caso —el corte llega
 *   después de que el servidor la guardara— queda dos veces en el historial.
 *
 * El estado del canal se publica (`alEstado`) para que la ventana lo pinte con
 * la píldora del kit (`ui/reconexion.js`, MAPEO-ERRORES §5.4): nunca falla en
 * silencio. Al volver tras una caída avisa (`alReconectar`) para releer lo que
 * pudo perderse, y los cambios de las solicitudes de soporte llegan por
 * `alTicket`.
 *
 * ## Identidad
 *
 * La del CONNECT, con las reglas de la API: `Authorization: Bearer <token>`
 * solo si hay sesión vigente y `X-Id-Sesion-Anonima` siempre (el servidor la
 * ignora con un token válido). Si la sesión cambia (se inicia, se cierra o se
 * renueva el token), la siguiente pregunta va por HTTP y el canal se rehace
 * con la nueva: el servidor nunca responde con una identidad que ya no es la
 * de la pestaña.
 *
 * ## Lo que no promete
 *
 * El servidor es una sola instancia con el broker en memoria; este módulo no
 * cambia nada si hubiera más (eso exige un broker externo en el servidor).
 *
 * @module comun/canal-chatbot
 */

import { canalReconectable, ESPERAS_POR_OMISION } from './canal-reconectable.js';
import {
  crearClienteChatbot,
  ErrorDelChatbot,
  idDeSesionAnonima,
  rutaDelChatbot,
} from './cliente-chatbot.js';
import { leerSesion } from './sesion.js';
import { conectarStomp } from './transporte-stomp.js';

/** Los destinos del contrato (`chatbot.yaml` 1.0.0). */
export const DESTINOS_DEL_CHATBOT = Object.freeze({
  preguntar: '/app/chat/mensajes',
  estado: '/user/queue/chat/estado',
  respuestas: '/user/queue/chat/respuestas',
  errores: '/user/queue/chat/errores',
  tickets: '/user/queue/soporte/tickets',
});

/**
 * Cuánto se espera al servidor antes de dar el canal por perdido y repetir la
 * pregunta por HTTP. `acuseMs`: hasta el `ESCRIBIENDO` (el servidor lo manda
 * nada más aceptar la pregunta). `respuestaMs`: desde el acuse hasta la
 * respuesta (las consultas en vivo tardan, como mucho, unos pocos segundos).
 * Cifras provisionales, D-CHA-09.
 */
export const PLAZOS_DEL_CANAL = Object.freeze({ acuseMs: 5000, respuestaMs: 30000 });

/**
 * Latidos que pide la ventana: no manda ninguno y recibe los del servidor
 * cada 10 s, que mantienen viva la conexión a través del borde.
 */
export const LATIDOS_DEL_CANAL = '0,10000';

/** Cabecera STOMP con la que el servidor devuelve la correlación. */
const CABECERA_CORRELACION = 'id-correlacion';

/**
 * El canal no contestó a tiempo o se cayó con la pregunta en vuelo: esa
 * pregunta se repite por HTTP. Nunca llega a la ventana.
 */
class SinRespuestaDelCanal extends Error {
  constructor() {
    super('El canal en tiempo real no respondió a tiempo.');
    this.name = 'SinRespuestaDelCanal';
  }
}

/**
 * La URL del canal: la de la API del asistente (mismo origen, o la base local
 * de desarrollo de `cliente-chatbot.js`) con `ws:`, o `wss:` bajo https.
 *
 * @param {Storage|null} [almacenLocal]
 * @param {{href?: string}|null} [ubicacion]
 * @returns {string}
 */
export function urlDelCanalDelChatbot(almacenLocal, ubicacion = globalThis.location) {
  const url = new URL(rutaDelChatbot('/ws-chatbot', almacenLocal), ubicacion?.href);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return url.href;
}

/**
 * Crea el canal del asistente, sin abrirlo (se abre con `abrir()`). Todo es
 * inyectable para las pruebas.
 *
 * @param {object} [opciones]
 * @param {ReturnType<typeof crearClienteChatbot>} [opciones.cliente] la API, para lo que va por HTTP
 * @param {(opciones: {url: string, cabeceras: Record<string, string>}) => Promise<object>} [opciones.conectar]
 * @param {() => {autenticado?: boolean, token?: string|null}} [opciones.sesion]
 * @param {Storage|null} [opciones.almacen] donde vive el identificador de visitante
 * @param {Storage|null} [opciones.almacenLocal] la base local de desarrollo
 * @param {{href?: string}|null} [opciones.ubicacion]
 * @param {{setTimeout: Function, clearTimeout: Function}} [opciones.reloj]
 * @param {readonly number[]} [opciones.esperas] entre intentos de reconexión
 * @param {{addEventListener?: Function, removeEventListener?: Function}} [opciones.ventana]
 * @param {{acuseMs: number, respuestaMs: number}} [opciones.plazos]
 * @param {() => string} [opciones.generarId] la correlación de cada pregunta
 */
export function crearCanalChatbot({
  cliente = crearClienteChatbot(),
  conectar = conectarStomp,
  sesion = () => leerSesion(),
  almacen,
  almacenLocal,
  ubicacion = globalThis.location,
  reloj = globalThis,
  esperas = ESPERAS_POR_OMISION,
  ventana = globalThis,
  plazos = PLAZOS_DEL_CANAL,
  generarId = nuevoId,
} = {}) {
  /** La fachada de `canal-reconectable.js` mientras el canal existe. */
  let fachada = null;
  let abriendo = null;
  let cerrado = false;
  /** La identidad con la que se abrió la conexión viva. */
  let firmaDelCanal = null;
  let ultimoEstado = null;
  /** Preguntas en vuelo por el canal, por su correlación. */
  const pendientes = new Map();
  const oyentes = { estado: new Set(), ticket: new Set(), reconectar: new Set() };

  // ------------------------------------------------------------ identidad

  function identidadActual() {
    const cabeceras = {
      'heart-beat': LATIDOS_DEL_CANAL,
      'X-Id-Sesion-Anonima': idDeSesionAnonima(almacen),
    };
    const actual = sesion();
    if (actual?.autenticado && actual.token) {
      cabeceras.Authorization = `Bearer ${actual.token}`;
    }
    return {
      cabeceras,
      firma: `${cabeceras.Authorization ?? ''}|${cabeceras['X-Id-Sesion-Anonima']}`,
    };
  }

  async function conectarConLaIdentidadActual() {
    const { cabeceras, firma } = identidadActual();
    const nuevo = await conectar({
      url: urlDelCanalDelChatbot(almacenLocal, ubicacion),
      cabeceras,
    });
    firmaDelCanal = firma;
    return nuevo;
  }

  // -------------------------------------------------------------- avisos

  function avisarEstado(datos) {
    ultimoEstado = datos;
    for (const oyente of oyentes.estado) {
      oyente(datos);
    }
  }

  function avisarReconexion() {
    for (const oyente of oyentes.reconectar) {
      oyente();
    }
  }

  // --------------------------------------------------------- abrir y cerrar

  /**
   * Abre el canal una sola vez; llamarlo de nuevo no hace nada. Nunca falla:
   * si no abre, sigue intentándolo y, mientras tanto, todo va por HTTP.
   *
   * @returns {Promise<void>}
   */
  function abrir() {
    if (cerrado || fachada) {
      return Promise.resolve();
    }
    if (!abriendo) {
      avisarEstado({ estado: 'conectando' });
      abriendo = canalReconectable({
        conectar: conectarConLaIdentidadActual,
        alEstado: avisarEstado,
        alReconectar: avisarReconexion,
        esperas,
        reloj,
        ventana,
        insistirAlAbrir: true,
      })
        .then(enlazar)
        .catch(() => {
          // No debería pasar (insiste al abrir); si pasa, todo sigue por HTTP.
          abriendo = null;
        });
    }
    return abriendo;
  }

  function enlazar(nueva) {
    abriendo = null;
    if (cerrado) {
      nueva.cerrar();
      return;
    }
    fachada = nueva;
    // Una caída suelta las preguntas en vuelo: van por HTTP.
    nueva.alCerrar = () => soltarPendientes();
    nueva.suscribir(DESTINOS_DEL_CHATBOT.estado, alRecibirEstado);
    nueva.suscribir(DESTINOS_DEL_CHATBOT.respuestas, alRecibirRespuesta);
    nueva.suscribir(DESTINOS_DEL_CHATBOT.errores, alRecibirError);
    nueva.suscribir(DESTINOS_DEL_CHATBOT.tickets, alRecibirTicket);
  }

  // Otra identidad, o un canal que se quedó mudo: se cierra este y se abre otro.
  function rehacer() {
    const anterior = fachada;
    fachada = null;
    firmaDelCanal = null;
    anterior?.cerrar();
    soltarPendientes();
    abrir();
  }

  function cerrar() {
    cerrado = true;
    const anterior = fachada;
    fachada = null;
    anterior?.cerrar();
    soltarPendientes();
  }

  // ------------------------------------------------------------ preguntas

  /** Con canal y con la identidad de ahora; si la sesión cambió, se rehace. */
  function listoParaPreguntar() {
    if (!fachada?.conectado) {
      return false;
    }
    if (firmaDelCanal !== identidadActual().firma) {
      rehacer();
      return false;
    }
    return true;
  }

  function porHttp(contenido, vista) {
    return vista ? cliente.enviarMensaje(contenido, { vista }) : cliente.enviarMensaje(contenido);
  }

  function porElCanal(contenido, vista, alEscribiendo) {
    return new Promise((resolver, rechazar) => {
      const idCorrelacion = generarId();
      pendientes.set(idCorrelacion, {
        resolver,
        rechazar,
        alEscribiendo,
        acusada: false,
        temporizador: null,
      });
      const cuerpo = { contenido, idCorrelacion };
      if (vista) {
        cuerpo.vista = vista;
      }
      try {
        fachada.enviar(DESTINOS_DEL_CHATBOT.preguntar, cuerpo);
      } catch {
        terminar(idCorrelacion)?.rechazar(new SinRespuestaDelCanal());
        return;
      }
      if (pendientes.get(idCorrelacion)?.acusada === false) {
        vigilar(idCorrelacion, plazos.acuseMs);
      }
    });
  }

  // Si el plazo vence, la pregunta va por HTTP y el canal se rehace: una
  // conexión que no contesta está muerta aunque el navegador no lo sepa.
  function vigilar(idCorrelacion, ms) {
    const pendiente = pendientes.get(idCorrelacion);
    if (!pendiente) {
      return;
    }
    reloj.clearTimeout?.(pendiente.temporizador);
    pendiente.temporizador = reloj.setTimeout(() => {
      if (!pendientes.has(idCorrelacion)) {
        return;
      }
      terminar(idCorrelacion).rechazar(new SinRespuestaDelCanal());
      rehacer();
    }, ms);
  }

  function terminar(idCorrelacion) {
    const pendiente = idCorrelacion ? pendientes.get(idCorrelacion) : undefined;
    if (!pendiente) {
      return null;
    }
    pendientes.delete(idCorrelacion);
    reloj.clearTimeout?.(pendiente.temporizador);
    return pendiente;
  }

  function soltarPendientes() {
    for (const idCorrelacion of [...pendientes.keys()]) {
      terminar(idCorrelacion)?.rechazar(new SinRespuestaDelCanal());
    }
  }

  /**
   * Pregunta al asistente: por el canal si está abierto, por HTTP si no.
   *
   * @param {string} contenido
   * @param {{vista?: string|null, alEscribiendo?: () => void}} [opciones]
   *   `alEscribiendo`: cuando el asistente empieza a responder (por HTTP, en
   *   el acto, porque no hay aviso)
   * @returns {Promise<object>} el `Mensaje` del bot
   */
  async function enviarMensaje(contenido, { vista = null, alEscribiendo = () => {} } = {}) {
    if (listoParaPreguntar()) {
      try {
        return await porElCanal(contenido, vista, alEscribiendo);
      } catch (error) {
        if (!(error instanceof SinRespuestaDelCanal)) {
          throw error;
        }
      }
    }
    alEscribiendo();
    return porHttp(contenido, vista);
  }

  // ----------------------------------------------------- lo que llega

  function correlacionDe(cuerpo, cabeceras) {
    return cabeceras?.[CABECERA_CORRELACION] ?? cuerpo?.idCorrelacion ?? null;
  }

  function alRecibirEstado(cuerpo, cabeceras) {
    const idCorrelacion = correlacionDe(cuerpo, cabeceras);
    const pendiente = idCorrelacion ? pendientes.get(idCorrelacion) : undefined;
    if (!pendiente || pendiente.acusada || cuerpo?.estado !== 'ESCRIBIENDO') {
      return;
    }
    pendiente.acusada = true;
    vigilar(idCorrelacion, plazos.respuestaMs);
    pendiente.alEscribiendo();
  }

  function alRecibirRespuesta(cuerpo, cabeceras) {
    terminar(correlacionDe(null, cabeceras))?.resolver(cuerpo);
  }

  function alRecibirError(cuerpo, cabeceras) {
    const estado = Number(cuerpo?.status) || 0;
    terminar(correlacionDe(cuerpo, cabeceras))?.rechazar(
      new ErrorDelChatbot(cuerpo, estado, { rutaFija: true }),
    );
  }

  function alRecibirTicket(ticket) {
    if (!ticket?.ticketId) {
      return;
    }
    for (const oyente of oyentes.ticket) {
      oyente(ticket);
    }
  }

  return {
    abrir,
    cerrar,
    enviarMensaje,
    /** Otra ronda de intentos, a mano (el «Reintentar» de la píldora). */
    reintentar() {
      fachada?.reintentar();
    },
    /** @param {(estado: {estado: string, intento?: number, de?: number}) => void} oyente */
    alEstado(oyente) {
      oyentes.estado.add(oyente);
      if (ultimoEstado) {
        oyente(ultimoEstado);
      }
    },
    /** @param {(ticket: object) => void} oyente lo que el jugador ve de la solicitud */
    alTicket(oyente) {
      oyentes.ticket.add(oyente);
    },
    /** @param {() => void} oyente tras volver de una caída */
    alReconectar(oyente) {
      oyentes.reconectar.add(oyente);
    },
    /** Si ahora mismo las preguntas van por el canal. */
    get enVivo() {
      return Boolean(fachada?.conectado);
    },
  };
}

function nuevoId() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
