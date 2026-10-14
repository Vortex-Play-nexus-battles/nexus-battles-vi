/**
 * Ventana del asistente — HU-CHA-001, HU-CHA-008, HU-CHA-011.
 *
 * La abre el botón flotante (`asistente.js`) en cualquier vista. No es un
 * diálogo modal: el jugador puede seguir mirando la página mientras pregunta,
 * así que no hay velo ni foco atrapado. Sí cierra con Escape y devuelve el
 * foco a quien la abrió.
 *
 * Qué ofrece:
 *
 * - conversación con el asistente, para visitantes y jugadores con sesión;
 * - el historial de la conversación de esta sesión, y borrarlo. Llega por
 *   páginas (7.4.6): al abrir, los últimos mensajes; al subir hasta arriba, o
 *   con «Ver mensajes anteriores», los de antes, sin mover lo que se está leyendo;
 * - ver los enlaces a capturas que ya tuviera el historial (ya no se piden:
 *   el cliente decidió que el asistente no recibe imágenes);
 * - calificar cada respuesta del bot (útil / no útil, con comentario opcional);
 * - respuestas enriquecidas: pasos, enlaces a secciones del sitio, tarjetas,
 *   botones de respuesta rápida y «Hablar con soporte» (`enriquecido-chatbot.js`);
 *   cada mensaje manda la sección donde está el jugador (`vista`);
 * - preguntas rápidas, temas frecuentes y autocompletado mientras se escribe
 *   (`sugerencias-chatbot.js`), sacados de lo que el asistente sabe responder;
 * - «Soporte»: abrir una solicitud de soporte humano y ver las propias
 *   (`soporte-chatbot.js`); el visitante recibe cómo conseguirlo: entrar
 *   (volviendo a esta página) o crear una cuenta;
 * - «Preferencias» (7.4.5): el idioma de las respuestas y el nivel de detalle,
 *   guardados en el servidor (`preferencias-chatbot.js`);
 * - UXC-9 (§7.4, RNF-DIS-002): minimizarla a su barra de título sin perder la
 *   conversación, y cambiarle el tamaño arrastrando la esquina o con las
 *   flechas del teclado sobre ella. El tamaño se recuerda en esta sesión.
 *
 * Si el servicio no responde, lo dice y ofrece el chat general: CA-03 de
 * HU-CHA-001.
 *
 * #506 — la ventana del chatbot, revisada:
 *
 * - al abrir, el foco va a la caja de texto (antes caía en «×»);
 * - la pregunta se pinta en cuanto se envía, con «escribiendo…» debajo; si el
 *   envío falla, sale de la conversación y su texto vuelve a la caja;
 * - cada rechazo se explica por su motivo (lista negra, inyección de código,
 *   demasiados mensajes, moderación caída) y el texto se queda para
 *   corregirlo; tras un 429, «Enviar» espera lo que dijo `Retry-After`;
 * - el lector de pantalla oye quién habla («Tú:», «Asistente:») y la
 *   conversación es una región `role="log"`;
 * - las acciones de la cabecera se parten en dos filas si no caben;
 * - un visitante que pide soporte humano recibe cómo conseguirlo (entrar con
 *   vuelta a esta página, o crear una cuenta), no un callejón sin salida.
 *
 * 7.4.9 — con un `canal` (`../canal-chatbot.js`, lo pasa `asistente.js`), las
 * preguntas van por el canal en tiempo real mientras está abierto y por HTTP
 * si no; «escribiendo…» sale cuando el servidor lo dice, las solicitudes de
 * soporte se actualizan solas y el estado del canal se ve en su píldora.
 *
 * Todo el texto del servidor entra con `textContent` (`h()`), nunca como HTML.
 *
 * @module comun/ui/ventana-chatbot
 */

import { destinoVisible, urlDeVista } from '../acceso.js';
import { crearClienteChatbot } from '../cliente-chatbot.js';
import { leerSesion, rutaSegura, urlDeLogin } from '../sesion.js';
import { conCarga } from './boton.js';
import { campo } from './campo.js';
import { confirmar } from './dialogo.js';
import { h, vaciar } from './dom.js';
import { introAntesDePasos, pintarEnriquecido, vistaDelChat } from './enriquecido-chatbot.js';
import { estadoDeCarga } from './estado-vista.js';
import { fechaHora } from './formato.js';
import { crearPanelPreferencias } from './preferencias-chatbot.js';
import { pintarEstadoDelCanal } from './reconexion.js';
import { crearPanelSoporte } from './soporte-chatbot.js';
import { conectarAutocompletado, crearPreguntasRapidas } from './sugerencias-chatbot.js';

/** Destino del chat general de jugadores, relativo a `src/comun/ui/`. */
const CHAT_GENERAL = '../../plataforma/salas-partidas/chat.html';

/**
 * #506 — los segundos que «Enviar» queda desactivado tras un 429 que no dice
 * cuánto esperar. El servicio y el borde mandan `Retry-After`; esto es por si
 * algo intermedio lo quita.
 */
export const ESPERA_POR_OMISION_S = 10;

/** Máximo que admite `setTimeout` sin desbordarse (unos 24,8 días). */
const MAXIMO_TEMPORIZADOR_MS = 2_147_483_647;

/** Límites del contrato (`EnviarMensaje`, `CalificarRespuesta`). */
export const LIMITES = Object.freeze({ mensaje: 4000, comentario: 1000 });

/**
 * Mensajes por página del historial (`GET /chat/historial?limite=`, 1 a 100).
 * Una página llena indica que puede haber más hacia atrás.
 */
export const PAGINA_DEL_HISTORIAL = 30;

/** A cuántos px del borde superior se piden los mensajes anteriores. */
const UMBRAL_DE_ANTERIORES = 48;

/**
 * UXC-9 — tamaños de la ventana (px). El mínimo deja leer una respuesta y
 * escribir; el máximo lo pone la pantalla (ver `acotarTamano`). Cada pulsación
 * de flecha mueve un paso.
 */
export const TAMANO = Object.freeze({ anchoMinimo: 300, altoMinimo: 320, paso: 32 });

/** Dónde se recuerda el tamaño elegido: solo esta pestaña (sessionStorage). */
const CLAVE_TAMANO = 'nexus.asistente.tamano';

/**
 * El tamaño pedido, dentro de lo que cabe en la pantalla.
 *
 * @param {{ancho: number, alto: number}} pedido
 * @param {{ancho: number, alto: number}} pantalla tamaño de la ventana del navegador
 * @returns {{ancho: number, alto: number}}
 */
export function acotarTamano(pedido, pantalla) {
  // Lo que ocupa la esquina: el margen derecho, el botón flotante debajo y un
  // respiro arriba para no tapar la cabecera.
  const anchoMaximo = Math.max(TAMANO.anchoMinimo, pantalla.ancho - 48);
  const altoMaximo = Math.max(TAMANO.altoMinimo, pantalla.alto - 56 - 96);
  const acotar = (valor, minimo, maximo) =>
    Math.round(Math.min(maximo, Math.max(minimo, Number(valor) || minimo)));
  return {
    ancho: acotar(pedido.ancho, TAMANO.anchoMinimo, anchoMaximo),
    alto: acotar(pedido.alto, TAMANO.altoMinimo, altoMaximo),
  };
}

/** Todo lo que lee el usuario, en un solo sitio. */
export const TEXTOS = Object.freeze({
  titulo: 'Asistente',
  estadoVisitante: 'Estás como visitante. Inicia sesión para consultar tu cuenta.',
  estadoJugador: (apodo) => `Hola, ${apodo}. También puedo consultar tu cuenta.`,
  bienvenida:
    'Hola, soy el asistente del juego. Pregúntame por las reglas, tu cuenta, las subastas o los torneos.',
  noDisponibleTitulo: 'El asistente no está disponible en este momento',
  noDisponibleDetalle:
    'Puedes preguntar en el chat general, donde hay jugadores conectados, o volver a intentarlo en unos minutos.',
  irAlChatGeneral: 'Ir al chat general',
  reintentar: 'Reintentar',
  errorAlCargar: 'No se pudo cargar la conversación. Inténtalo de nuevo.',
  errorAlEnviar: 'No se pudo enviar tu mensaje. Inténtalo de nuevo.',
  mensajeInvalido: 'Tu mensaje no se pudo enviar: revisa que no esté vacío ni sea demasiado largo.',
  // #506 — un mensaje rechazado se explica por su `motivo` (contrato 1.2.0 y
  // 1.4.0); el 429, por el estado, con los segundos de `Retry-After`.
  contenidoBloqueado:
    'Tu mensaje no se envió porque tiene palabras que no están permitidas. Escríbelo de otra forma.',
  inyeccionDetectada:
    'Tu mensaje no se envió porque parece contener código o instrucciones del sistema. Escribe tu pregunta con palabras.',
  demasiadosMensajes: (segundos) =>
    segundos > 0
      ? `Escribiste muchos mensajes seguidos. Espera ${segundos} s y vuelve a intentarlo.`
      : 'Escribiste muchos mensajes seguidos. Espera un momento y vuelve a intentarlo.',
  moderacionNoDisponible:
    'No pude revisar tu mensaje en este momento. Inténtalo de nuevo en unos minutos.',
  // Lo mismo para el comentario de una calificación (contrato 1.4.0).
  comentarioBloqueado:
    'Tu comentario no se envió porque tiene palabras que no están permitidas. Escríbelo de otra forma.',
  comentarioConCodigo:
    'Tu comentario no se envió porque parece contener código o instrucciones del sistema. Escríbelo con palabras.',
  comentarioSinRevisar:
    'No pude revisar tu comentario en este momento. Inténtalo de nuevo en unos minutos.',
  errorAlBorrar: 'No se pudo borrar la conversación. Inténtalo de nuevo.',
  escribiendo: 'El asistente está escribiendo…',
  placeholder: 'Escribe tu pregunta…',
  etiquetaMensaje: 'Tu pregunta',
  enviar: 'Enviar',
  enviando: 'Enviando…',
  verCaptura: 'Ver captura adjunta',
  anteriores: 'Ver mensajes anteriores',
  cargandoAnteriores: 'Cargando mensajes anteriores…',
  borrar: 'Borrar conversación',
  soporte: 'Soporte',
  preferencias: 'Preferencias',
  confirmarBorrarTitulo: '¿Borrar la conversación?',
  confirmarBorrarMensaje:
    'Se borrarán todos los mensajes de esta conversación. No se puede deshacer.',
  confirmarBorrarBoton: 'Borrar',
  cerrar: 'Cerrar el asistente',
  minimizar: 'Minimizar el asistente',
  restaurar: 'Restaurar el asistente',
  tamano: 'Cambiar el tamaño del asistente. Usa las flechas; Inicio vuelve al tamaño normal.',
  preguntaCalificacion: '¿Te sirvió esta respuesta?',
  util: '👍 Sí',
  noUtil: '👎 No',
  etiquetaUtil: 'Sí, me sirvió',
  etiquetaNoUtil: 'No me sirvió',
  etiquetaComentario: '¿Qué faltó? (opcional)',
  enviarCalificacion: 'Enviar calificación',
  cancelar: 'Cancelar',
  gracias: 'Gracias, tu calificación nos ayuda a mejorar.',
  yaCalificada: 'Ya habías calificado esta respuesta.',
  errorAlCalificar: 'No se pudo guardar tu calificación. Inténtalo de nuevo.',
  registro: 'Conversación con el asistente',
  // #506 — quién habla, solo para el lector de pantalla (no se ve).
  prefijoUsuario: 'Tú:',
  prefijoAsistente: 'Asistente:',
});

/**
 * Lo que dice un rechazo según su `motivo`: al enviar un mensaje y al enviar
 * el comentario de una calificación. Un `Map` y no un objeto: un `motivo`
 * como «constructor» no puede sacar nada del prototipo.
 */
const POR_MOTIVO = Object.freeze({
  mensaje: new Map([
    ['CONTENIDO_BLOQUEADO', TEXTOS.contenidoBloqueado],
    ['INYECCION_DETECTADA', TEXTOS.inyeccionDetectada],
    ['MODERACION_NO_DISPONIBLE', TEXTOS.moderacionNoDisponible],
  ]),
  comentario: new Map([
    ['CONTENIDO_BLOQUEADO', TEXTOS.comentarioBloqueado],
    ['INYECCION_DETECTADA', TEXTOS.comentarioConCodigo],
    ['MODERACION_NO_DISPONIBLE', TEXTOS.comentarioSinRevisar],
  ]),
});

/**
 * #506 — la página donde está la ventana, como vuelta segura tras entrar
 * (`rutaSegura`: solo del mismo origen y nunca otra pantalla de entrada), o
 * `null`.
 *
 * @param {{pathname?: string, search?: string, hash?: string, origin?: string}|null} [ubicacion]
 * @returns {string|null}
 */
export function rutaDeEstaPagina(ubicacion = globalThis.location) {
  if (!ubicacion?.pathname) {
    return null;
  }
  return rutaSegura(
    `${ubicacion.pathname}${ubicacion.search ?? ''}${ubicacion.hash ?? ''}`,
    ubicacion.origin,
  );
}

let contador = 0;

/**
 * Crea la ventana (oculta) y la cuelga de `raiz`.
 *
 * @param {{cliente?: ReturnType<typeof crearClienteChatbot>,
 *          sesion?: () => {autenticado: boolean, apodo: string, rol?: string|null},
 *          raiz?: HTMLElement, chatGeneral?: string,
 *          login?: string|(() => string|null)|null,
 *          registro?: string|(() => string|null)|null,
 *          esperaAutocompletar?: number, vista?: () => string|null,
 *          confirmarBorrado?: (opciones: object) => Promise<boolean>,
 *          reloj?: {setTimeout: Function, clearTimeout: Function},
 *          canal?: ReturnType<typeof import('../canal-chatbot.js').crearCanalChatbot>|null}} [opciones]
 *          `login`: por omisión, la entrada con vuelta a esta página (#506).
 *          `reloj`: el de la espera tras un 429, inyectable en pruebas.
 *          `canal`: 7.4.9, el canal en tiempo real; sin él, todo va por HTTP.
 * @returns {{elemento: HTMLElement, abrir: (desde?: HTMLElement|null) => void,
 *            cerrar: () => void, alternar: (desde?: HTMLElement|null) => void,
 *            abierta: () => boolean}}
 */
export function crearVentanaChatbot({
  cliente = crearClienteChatbot(),
  sesion = () => leerSesion(),
  raiz = document.body,
  // SA-7 — la dirección del chat general es la limpia (`/chat`) detrás del borde.
  chatGeneral = urlDeVista('chat') ?? new URL(CHAT_GENERAL, import.meta.url).href,
  login = () => urlDeLogin({ volver: rutaDeEstaPagina() }),
  registro: destinoRegistro = urlDeVista('registro'),
  confirmarBorrado = confirmar,
  esperaAutocompletar = 250,
  vista = () => vistaDelChat(),
  canal = null,
  reloj = globalThis,
} = {}) {
  contador += 1;
  const idTitulo = `chatbot-ventana-titulo-${contador}`;

  let cargada = false;
  let enviando = false;
  let devolverFocoA = null;

  // #506 — tras un 429, «Enviar» espera lo que dijo el servidor.
  let enEspera = false;
  let temporizadorDeEspera = null;

  // Historial por páginas: el mensaje más antiguo que ya se ve (cursor para
  // pedir los anteriores), si puede haber más, y los ya pintados (así un
  // servidor que devuelva de más no repite ninguno).
  let masAntiguoId = null;
  let hayAnteriores = false;
  let cargandoAnteriores = false;
  const idsPintados = new Set();

  // ------------------------------------------------------------ estructura

  const titulo = h('h2', { clase: 'chatbot-ventana__titulo', texto: TEXTOS.titulo });
  titulo.id = idTitulo;
  const estado = h('p', { clase: 'chatbot-ventana__estado' });
  // 7.4.9 — el estado del canal en tiempo real: la píldora del kit en modo
  // discreto (solo se ve si algo va distinto), no un aviso (MAPEO-ERRORES §5.4).
  const pildoraDelCanal = canal
    ? h('span', {
        clase: 'conexion',
        atributos: { hidden: true },
        datos: { zona: 'conexion-asistente' },
      })
    : null;

  const botonPreferencias = h('button', {
    clase: 'chatbot-ventana__accion',
    texto: TEXTOS.preferencias,
    atributos: { type: 'button', 'aria-expanded': 'false' },
    datos: { accion: 'preferencias-asistente' },
  });
  const botonSoporte = h('button', {
    clase: 'chatbot-ventana__accion',
    texto: TEXTOS.soporte,
    atributos: { type: 'button', 'aria-expanded': 'false' },
    datos: { accion: 'hablar-con-soporte' },
  });
  const botonBorrar = h('button', {
    clase: 'chatbot-ventana__accion',
    texto: TEXTOS.borrar,
    atributos: { type: 'button' },
    datos: { accion: 'borrar-conversacion' },
  });
  const botonCerrar = h('button', {
    clase: 'chatbot-ventana__cerrar',
    texto: '×',
    atributos: { type: 'button', 'aria-label': TEXTOS.cerrar },
    datos: { accion: 'cerrar-asistente' },
  });
  // UXC-9 — minimizar deja la barra de título a la vista y la conversación
  // intacta; el mismo botón la restaura.
  const botonMinimizar = h('button', {
    clase: 'chatbot-ventana__cerrar',
    texto: '−',
    atributos: { type: 'button', 'aria-label': TEXTOS.minimizar, 'aria-expanded': 'true' },
    datos: { accion: 'minimizar-asistente' },
  });
  // UXC-9 — la esquina que cambia el tamaño. La ventana cuelga de la esquina
  // inferior derecha, así que se agarra por la superior izquierda. Es un
  // botón para que también se alcance y se use con el teclado.
  const asa = h('button', {
    clase: 'chatbot-ventana__asa',
    atributos: { type: 'button', 'aria-label': TEXTOS.tamano, title: TEXTOS.tamano },
    datos: { accion: 'tamano-asistente' },
  });

  // #506 — las tres acciones de texto van en su propia fila, que se parte si
  // no cabe; la del título se queda con el título, minimizar y cerrar. Antes
  // las cinco compartían fila con el título: a 1366 px lo dejaban sin sitio y
  // a 320–375 px «Preferencias» salía cortado. El orden del DOM (y con él el
  // del tabulador) es el de siempre: la rejilla del CSS las coloca.
  const cabecera = h('header', {
    clase: 'chatbot-ventana__cabecera',
    hijos: [
      titulo,
      h('div', {
        clase: 'chatbot-ventana__acciones',
        hijos: [botonPreferencias, botonSoporte, botonBorrar],
      }),
      h('div', {
        clase: 'chatbot-ventana__controles',
        hijos: [botonMinimizar, botonCerrar],
      }),
      estado,
      pildoraDelCanal
        ? h('div', { clase: 'chatbot-ventana__canal', hijos: [pildoraDelCanal] })
        : null,
    ],
  });

  // #506 — la conversación es una región `role="log"`, y la lista va dentro
  // tal cual. UXC-9 le había quitado el rol a la <ol> porque en ella le
  // borraba su semántica de lista y cada mensaje (<li>) quedaba huérfano (axe,
  // «listitem», grave); en un envoltorio no toca la lista. La región viva es
  // el envoltorio, así que la lista ya no lleva `aria-live` propio (dos
  // regiones anidadas leerían cada mensaje dos veces).
  const registro = h('ol', { clase: 'chatbot-ventana__registro' });
  const conversacion = h('div', {
    clase: 'chatbot-ventana__conversacion',
    atributos: { role: 'log', 'aria-live': 'polite', 'aria-label': TEXTOS.registro },
    hijos: [registro],
  });
  const zonaAviso = h('div', { clase: 'chatbot-ventana__aviso', atributos: { hidden: true } });

  // Va siempre primero en la lista, solo mientras puede haber mensajes
  // anteriores. Es la vía de teclado y de lector de pantalla del scroll
  // infinito: llegar arriba con la rueda hace lo mismo.
  const botonAnteriores = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: TEXTOS.anteriores,
    atributos: { type: 'button' },
    datos: { accion: 'ver-anteriores' },
  });
  const filaAnteriores = h('li', {
    clase: 'chatbot-ventana__anteriores',
    hijos: [botonAnteriores],
  });

  const entrada = h('textarea', {
    clase: 'campo__control chatbot-ventana__entrada',
    atributos: {
      name: 'contenido',
      rows: 2,
      maxlength: LIMITES.mensaje,
      placeholder: TEXTOS.placeholder,
      'aria-label': TEXTOS.etiquetaMensaje,
    },
  });
  const botonEnviar = h('button', {
    clase: 'boton boton--primario',
    texto: TEXTOS.enviar,
    atributos: { type: 'submit' },
  });

  // Preguntas rápidas y autocompletado. El autocompletado se conecta aquí,
  // antes del manejador de Enter de más abajo, para que Enter elija la
  // sugerencia marcada en vez de enviar lo escrito.
  const rapidas = crearPreguntasRapidas({ cliente, alElegir: enviarSugerencia });
  const autocompletado = conectarAutocompletado({
    entrada,
    cliente,
    alElegir: enviarSugerencia,
    esperaMs: esperaAutocompletar,
  });

  const formulario = h('form', {
    clase: 'chatbot-ventana__formulario',
    atributos: { novalidate: true },
    hijos: [
      rapidas.elemento,
      h('div', {
        clase: 'chatbot-ventana__fila chatbot-ventana__fila--entrada',
        hijos: [autocompletado.elemento, entrada, botonEnviar],
      }),
    ],
  });

  const ventana = h('section', {
    clase: 'chatbot-ventana',
    atributos: {
      role: 'dialog',
      'aria-modal': 'false',
      'aria-labelledby': idTitulo,
      hidden: true,
    },
    datos: { chatbotVentana: '' },
    hijos: [asa, cabecera, conversacion, zonaAviso, formulario],
  });
  // «Soporte» ocupa el sitio de la conversación mientras está abierto; la
  // conversación no se pierde, solo se oculta.
  const panelSoporte = crearPanelSoporte({
    cliente,
    sesion,
    login,
    registro: destinoRegistro,
    alVolver: () => mostrarSoporte(false),
  });
  ventana.append(panelSoporte.elemento);
  // «Preferencias» ocupa el mismo sitio; solo uno de los dos a la vez.
  const panelPreferencias = crearPanelPreferencias({
    cliente,
    sesion,
    alVolver: () => mostrarPreferencias(false),
  });
  ventana.append(panelPreferencias.elemento);
  // 7.4.9 — lo que llega por el canal: su estado, los cambios de las
  // solicitudes de soporte y, al volver de una caída, la relectura de la lista.
  canal?.alEstado((datos) =>
    pintarEstadoDelCanal(pildoraDelCanal, {
      ...datos,
      alReintentar: () => canal.reintentar(),
      discreto: true,
    }),
  );
  canal?.alTicket((ticket) => panelSoporte.actualizarTicket(ticket));
  canal?.alReconectar(() => panelSoporte.recargarLista());
  ventana.id = `chatbot-ventana-${contador}`;
  registro.id = `chatbot-ventana-registro-${contador}`;
  botonMinimizar.setAttribute('aria-controls', registro.id);
  raiz.append(ventana);

  // ------------------------------------------------------------- mensajes

  function pintarBienvenida() {
    registro.append(
      h('li', {
        clase: 'chatbot-ventana__mensaje chatbot-ventana__mensaje--bot',
        datos: { bienvenida: '' },
        hijos: [
          quienHabla(true),
          h('p', { clase: 'chatbot-ventana__texto', texto: TEXTOS.bienvenida }),
        ],
      }),
    );
  }

  function quitarBienvenida() {
    registro.querySelector('[data-bienvenida]')?.remove();
  }

  /**
   * @param {{id?: string|null, remitente: string, contenido: string,
   *          adjuntoUrl?: string|null, fechaEnvio?: string|null}} mensaje
   */
  function pintarMensaje(mensaje) {
    registro.append(crearBurbuja(mensaje));
    registro.scrollTop = registro.scrollHeight;
  }

  /**
   * @param {{id?: string|null, remitente: string, contenido: string,
   *          adjuntoUrl?: string|null, fechaEnvio?: string|null}} mensaje
   * @returns {HTMLElement} la burbuja, sin colgar de la lista
   */
  function crearBurbuja(mensaje) {
    if (mensaje.id) {
      idsPintados.add(mensaje.id);
    }
    const esBot = mensaje.remitente === 'BOT';
    const enriquecido = esBot ? (mensaje.enriquecido ?? null) : null;
    // Con pasos, el texto se queda con la introducción: los pasos se pintan
    // aparte, numerados, y no se repiten.
    const tienePasos = (enriquecido?.pasos?.length ?? 0) > 0;
    const texto = tienePasos ? introAntesDePasos(mensaje.contenido) : mensaje.contenido;
    const burbuja = h('li', {
      clase: `chatbot-ventana__mensaje chatbot-ventana__mensaje--${esBot ? 'bot' : 'usuario'}`,
      datos: mensaje.id ? { mensajeId: mensaje.id } : {},
      hijos: [quienHabla(esBot), texto ? h('p', { clase: 'chatbot-ventana__texto', texto }) : null],
    });
    const parteEnriquecida = pintarEnriquecido(enriquecido, {
      alPreguntar: enviarSugerencia,
      alPedirSoporte: () => mostrarSoporte(true),
      // #506 — un enlace a una vista que esta sesión no puede ver no se pinta.
      visible: (destino) => destinoVisible(destino, sesion() ?? null),
    });
    if (parteEnriquecida) {
      burbuja.append(parteEnriquecida);
    }

    const enlace = urlPermitida(mensaje.adjuntoUrl);
    if (enlace) {
      burbuja.append(
        h('a', {
          clase: 'chatbot-ventana__adjunto',
          texto: TEXTOS.verCaptura,
          atributos: { href: enlace, target: '_blank', rel: 'noopener noreferrer' },
        }),
      );
    }
    if (mensaje.fechaEnvio) {
      burbuja.append(
        h('p', { clase: 'chatbot-ventana__meta', texto: fechaHora(mensaje.fechaEnvio) }),
      );
    }
    if (esBot && mensaje.id) {
      burbuja.append(controlDeCalificacion(mensaje.id));
    }
    return burbuja;
  }

  // --------------------------------------------------------- calificación

  /** @param {string} mensajeId */
  function controlDeCalificacion(mensajeId) {
    const caja = h('div', { clase: 'chatbot-calificacion' });
    const nota = h('p', { clase: 'chatbot-calificacion__nota', atributos: { role: 'status' } });

    const botonUtil = h('button', {
      clase: 'chatbot-calificacion__boton',
      texto: TEXTOS.util,
      atributos: { type: 'button', 'aria-label': TEXTOS.etiquetaUtil },
      datos: { accion: 'calificar-util' },
    });
    const botonNoUtil = h('button', {
      clase: 'chatbot-calificacion__boton',
      texto: TEXTOS.noUtil,
      atributos: { type: 'button', 'aria-label': TEXTOS.etiquetaNoUtil, 'aria-expanded': 'false' },
      datos: { accion: 'calificar-no-util' },
    });

    const comentario = campo({
      nombre: `comentario-${mensajeId}`,
      etiqueta: TEXTOS.etiquetaComentario,
      multilinea: true,
      atributos: { maxlength: LIMITES.comentario, rows: 2 },
    });
    const botonEnviarComentario = h('button', {
      clase: 'boton boton--primario boton--pequeno',
      texto: TEXTOS.enviarCalificacion,
      atributos: { type: 'button' },
      datos: { accion: 'enviar-calificacion' },
    });
    const botonCancelar = h('button', {
      clase: 'boton boton--secundario boton--pequeno',
      texto: TEXTOS.cancelar,
      atributos: { type: 'button' },
      datos: { accion: 'cancelar-calificacion' },
    });
    const formularioComentario = h('div', {
      clase: 'chatbot-calificacion__comentario',
      atributos: { hidden: true },
      hijos: [
        comentario.elemento,
        h('div', { clase: 'acciones', hijos: [botonCancelar, botonEnviarComentario] }),
      ],
    });

    caja.append(
      h('p', { clase: 'chatbot-calificacion__pregunta', texto: TEXTOS.preguntaCalificacion }),
      h('div', { clase: 'chatbot-calificacion__botones', hijos: [botonUtil, botonNoUtil] }),
      formularioComentario,
      nota,
    );

    function terminar(texto) {
      caja.replaceChildren(h('p', { clase: 'chatbot-calificacion__gracias', texto }));
    }

    async function enviar(util, texto, disparador) {
      conCarga(disparador, true, TEXTOS.enviando);
      nota.textContent = '';
      try {
        await cliente.calificar(mensajeId, util, texto);
        terminar(TEXTOS.gracias);
      } catch (error) {
        if (error?.estado === 409) {
          terminar(TEXTOS.yaCalificada);
          return;
        }
        conCarga(disparador, false);
        // #506 — el comentario rechazado se explica y se queda en su caja.
        nota.textContent = textoDeErrorAlCalificar(error);
      }
    }

    botonUtil.addEventListener('click', () => enviar(true, null, botonUtil));
    botonNoUtil.addEventListener('click', () => {
      formularioComentario.hidden = false;
      botonNoUtil.setAttribute('aria-expanded', 'true');
      comentario.control.focus();
    });
    botonCancelar.addEventListener('click', () => {
      formularioComentario.hidden = true;
      botonNoUtil.setAttribute('aria-expanded', 'false');
      botonNoUtil.focus();
    });
    botonEnviarComentario.addEventListener('click', () =>
      enviar(false, comentario.control.value, botonEnviarComentario),
    );

    return caja;
  }

  // --------------------------------------------------------------- avisos

  /** @returns {HTMLElement} el aviso pintado */
  function mostrarAviso(error, { alReintentar = null } = {}) {
    vaciar(zonaAviso);
    zonaAviso.hidden = false;

    if (error?.noDisponible) {
      const aviso = h('div', {
        clase: 'aviso aviso--advertencia',
        atributos: { role: 'alert' },
        hijos: [
          h('div', {
            clase: 'aviso__cuerpo',
            hijos: [
              h('p', { clase: 'aviso__titulo', texto: TEXTOS.noDisponibleTitulo }),
              h('p', { clase: 'aviso__detalle', texto: TEXTOS.noDisponibleDetalle }),
              h('div', {
                clase: 'acciones',
                hijos: [
                  h('a', {
                    clase: 'boton boton--primario boton--pequeno',
                    texto: TEXTOS.irAlChatGeneral,
                    atributos: { href: chatGeneral },
                    datos: { accion: 'ir-al-chat' },
                  }),
                  alReintentar ? botonReintentar(alReintentar) : null,
                ],
              }),
            ],
          }),
        ],
      });
      zonaAviso.append(aviso);
      return aviso;
    }

    // #506 — lo que el jugador puede corregir (lista negra, inyección, un 400,
    // demasiados mensajes) es una advertencia; lo demás, un error.
    const aviso = h('div', {
      clase: `aviso aviso--${tonoDeError(error)}`,
      atributos: { role: 'alert' },
      hijos: [
        h('div', {
          clase: 'aviso__cuerpo',
          hijos: [
            h('p', { clase: 'aviso__detalle', texto: textoDeError(error, alReintentar) }),
            alReintentar
              ? h('div', { clase: 'acciones', hijos: [botonReintentar(alReintentar)] })
              : null,
          ],
        }),
      ],
    });
    zonaAviso.append(aviso);
    return aviso;
  }

  function botonReintentar(alReintentar) {
    const boton = h('button', {
      clase: 'boton boton--secundario boton--pequeno',
      texto: TEXTOS.reintentar,
      atributos: { type: 'button' },
      datos: { accion: 'reintentar' },
    });
    boton.addEventListener('click', alReintentar);
    return boton;
  }

  function limpiarAviso() {
    vaciar(zonaAviso);
    zonaAviso.hidden = true;
  }

  function habilitarFormulario(activo) {
    entrada.disabled = !activo;
    botonBorrar.disabled = !activo;
    refrescarEnvio();
  }

  /**
   * ¿Se puede enviar ya? Con la conversación cargada, la caja activa, sin otro
   * envío en vuelo y fuera de la espera de un 429 (#506).
   */
  function puedeEnviar() {
    return cargada && !entrada.disabled && !enviando && !enEspera;
  }

  // «Enviar» y las preguntas rápidas siguen a `puedeEnviar`. Mientras hay un
  // envío en vuelo, del botón se ocupa `conCarga` («Enviando…»).
  function refrescarEnvio() {
    if (enviando) {
      return;
    }
    const libre = puedeEnviar();
    botonEnviar.disabled = !libre;
    rapidas.habilitar(libre);
  }

  /**
   * #506 — tras un 429, «Enviar» y las preguntas rápidas quedan desactivados
   * los segundos de `Retry-After` y se reactivan solos. La caja de texto sigue
   * activa, para corregir lo escrito mientras tanto.
   *
   * @param {number|null} segundos
   * @param {HTMLElement} aviso el que dice cuánto esperar; se quita al acabar
   */
  function iniciarEspera(segundos, aviso) {
    const espera = Number.isFinite(segundos) && segundos >= 0 ? segundos : ESPERA_POR_OMISION_S;
    enEspera = true;
    reloj.clearTimeout(temporizadorDeEspera);
    temporizadorDeEspera = reloj.setTimeout(
      () => terminarEspera(aviso),
      Math.min(espera * 1000, MAXIMO_TEMPORIZADOR_MS),
    );
    refrescarEnvio();
  }

  function terminarEspera(aviso) {
    enEspera = false;
    temporizadorDeEspera = null;
    refrescarEnvio();
    // «Espera N s» ya no es verdad: se quita, si sigue siendo ese el aviso.
    if (aviso?.isConnected) {
      limpiarAviso();
    }
  }

  // ------------------------------------------------------------- historial

  async function cargarHistorial() {
    limpiarAviso();
    reiniciarPaginas();
    vaciar(registro);
    const cargando = estadoDeCarga({ filas: 2, etiqueta: 'Cargando la conversación…' });
    registro.append(h('li', { clase: 'chatbot-ventana__cargando', hijos: [cargando] }));
    // #506 — mientras carga, la caja de texto sigue activa para que el foco
    // llegue a ella al abrir (antes estaba desactivada y el foco caía en «×»);
    // lo que no se puede es enviar ni borrar hasta que cargue.
    entrada.disabled = false;
    botonBorrar.disabled = true;
    refrescarEnvio();
    recuperarFoco();

    try {
      const historial = await cliente.obtenerHistorial({ limite: PAGINA_DEL_HISTORIAL });
      vaciar(registro);
      if (historial.length === 0) {
        pintarBienvenida();
      }
      for (const mensaje of historial) {
        pintarMensaje(mensaje);
      }
      recordarPagina(historial, historial.length >= PAGINA_DEL_HISTORIAL);
      cargada = true;
      habilitarFormulario(true);
      rapidas.cargar();
      recuperarFoco();
      // 7.4.9 — el canal se abre cuando el asistente ya respondió por HTTP.
      canal?.abrir();
    } catch (error) {
      vaciar(registro);
      habilitarFormulario(false);
      mostrarAviso(error, { alReintentar: cargarHistorial });
      enfocarDentro();
    }
  }

  // Tras «Reintentar», el botón que tenía el foco ya no existe y el foco cae
  // al cuerpo de la página: vuelve a la ventana. Si la persona lo llevó a
  // otra parte mientras cargaba, se respeta.
  function recuperarFoco() {
    const activo = document.activeElement;
    if (!activo || activo === document.body) {
      enfocarDentro();
    }
  }

  function reiniciarPaginas() {
    masAntiguoId = null;
    hayAnteriores = false;
    idsPintados.clear();
    mostrarAnteriores(false);
  }

  /**
   * @param {Array<{id?: string|null}>} mensajes los recién pintados, en orden
   * @param {boolean} llena si la página vino completa (puede haber más)
   */
  function recordarPagina(mensajes, llena) {
    masAntiguoId = mensajes[0]?.id ?? masAntiguoId;
    hayAnteriores = llena && Boolean(masAntiguoId);
    mostrarAnteriores(hayAnteriores);
  }

  function mostrarAnteriores(si) {
    if (si) {
      if (registro.firstElementChild !== filaAnteriores) {
        registro.prepend(filaAnteriores);
      }
      return;
    }
    const teniaElFoco = filaAnteriores.contains(document.activeElement);
    filaAnteriores.remove();
    if (teniaElFoco) {
      enfocarDentro();
    }
  }

  async function cargarAnteriores() {
    if (cargandoAnteriores || !hayAnteriores || !masAntiguoId) {
      return;
    }
    cargandoAnteriores = true;
    limpiarAviso();
    conCarga(botonAnteriores, true, TEXTOS.cargandoAnteriores);
    // Ocupada mientras entran los anteriores: la región viva no lee de golpe
    // una página entera de mensajes viejos.
    registro.setAttribute('aria-busy', 'true');
    conversacion.setAttribute('aria-busy', 'true');
    try {
      const pagina = await cliente.obtenerHistorial({
        antesDe: masAntiguoId,
        limite: PAGINA_DEL_HISTORIAL,
      });
      agregarAnteriores(pagina);
    } catch (error) {
      mostrarAviso(error, { alReintentar: cargarAnteriores });
    } finally {
      terminarAnteriores();
    }
  }

  // Los mensajes de antes entran arriba, en orden, y lo que el jugador estaba
  // leyendo no se mueve: se compensa el scroll con lo que creció la lista.
  function agregarAnteriores(pagina) {
    const nuevos = pagina.filter((mensaje) => !(mensaje.id && idsPintados.has(mensaje.id)));
    if (nuevos.length === 0) {
      hayAnteriores = false;
      mostrarAnteriores(false);
      return;
    }
    const alturaAntes = registro.scrollHeight;
    const desdeArriba = registro.scrollTop;
    const fragmento = document.createDocumentFragment();
    for (const mensaje of nuevos) {
      fragmento.append(crearBurbuja(mensaje));
    }
    const ancla = filaAnteriores.isConnected ? filaAnteriores.nextSibling : registro.firstChild;
    registro.insertBefore(fragmento, ancla);
    recordarPagina(nuevos, pagina.length >= PAGINA_DEL_HISTORIAL);
    registro.scrollTop = registro.scrollHeight - alturaAntes + desdeArriba;
  }

  function terminarAnteriores() {
    cargandoAnteriores = false;
    conCarga(botonAnteriores, false);
    registro.removeAttribute('aria-busy');
    conversacion.removeAttribute('aria-busy');
  }

  // ----------------------------------------------------------------- envío

  // Una pregunta rápida o una sugerencia se envía como si se hubiera escrito.
  // Si todavía no se puede enviar (cargando, en la espera de un 429), no se
  // toca lo que haya en la caja.
  function enviarSugerencia(pregunta) {
    if (!puedeEnviar()) {
      return;
    }
    entrada.value = pregunta;
    enviarMensaje();
  }

  async function enviarMensaje() {
    if (enviando) {
      return;
    }
    const contenido = entrada.value.trim();
    if (!contenido) {
      entrada.focus();
      return;
    }
    // Cargando la conversación o esperando tras un 429: lo escrito se queda
    // en la caja y Enter no envía (el botón ya está desactivado).
    if (!puedeEnviar()) {
      return;
    }

    enviando = true;
    autocompletado.cerrar();
    limpiarAviso();
    conCarga(botonEnviar, true, TEXTOS.enviando);
    rapidas.habilitar(false);
    // #506 — la pregunta se pinta ya, antes de que llegue la respuesta, con
    // «escribiendo…» debajo; la caja queda libre para la siguiente.
    quitarBienvenida();
    const pregunta = crearBurbuja({
      remitente: 'USUARIO',
      contenido,
      fechaEnvio: new Date().toISOString(),
    });
    registro.append(pregunta);
    entrada.value = '';
    const escribiendo = h('li', {
      clase: 'chatbot-ventana__escribiendo',
      texto: TEXTOS.escribiendo,
    });
    // 7.4.9 — con canal, «escribiendo…» espera al ESCRIBIENDO del servidor.
    escribiendo.hidden = Boolean(canal);
    registro.append(escribiendo);
    registro.scrollTop = registro.scrollHeight;

    try {
      const donde = vista();
      const respuesta = await preguntar(contenido, donde, () => {
        escribiendo.hidden = false;
      });
      alResponder(escribiendo, respuesta);
    } catch (error) {
      alFallarElEnvio({ escribiendo, pregunta, contenido, error });
    } finally {
      terminarEnvio();
    }
  }

  // 7.4.9 — por el canal (que decide si va en vivo o por HTTP) o, sin él, por HTTP.
  function preguntar(contenido, donde, alEscribiendo) {
    if (canal) {
      return canal.enviarMensaje(contenido, { vista: donde, alEscribiendo });
    }
    return donde
      ? cliente.enviarMensaje(contenido, { vista: donde })
      : cliente.enviarMensaje(contenido);
  }

  // Fuera de enviarMensaje a propósito: lo que se toca después de un `await`
  // vive en funciones aparte (regla require-atomic-updates de ESLint).
  function alResponder(escribiendo, respuesta) {
    escribiendo.remove();
    pintarMensaje(respuesta);
    autocompletado.cerrar();
  }

  /**
   * #506 — el envío falló (rechazado o sin respuesta): la pregunta sale de la
   * conversación, porque no se guardó, y su texto vuelve a la caja para
   * corregirlo y reenviarlo. Si mientras tanto se empezó a escribir otra
   * cosa, se conserva debajo: no se pierde nada.
   */
  function alFallarElEnvio({ escribiendo, pregunta, contenido, error }) {
    escribiendo.remove();
    pregunta.remove();
    if (!registro.querySelector('.chatbot-ventana__mensaje')) {
      pintarBienvenida();
    }
    entrada.value = entrada.value.trim() ? `${contenido}\n${entrada.value}` : contenido;
    const aviso = mostrarAviso(error);
    if (error?.estado === 429) {
      iniciarEspera(error.reintentarEn ?? null, aviso);
    }
  }

  function terminarEnvio() {
    enviando = false;
    conCarga(botonEnviar, false);
    refrescarEnvio();
    entrada.focus();
    // #506 — si ya tenía el foco, `focus()` no la trae a la vista: en una
    // pantalla baja, con un aviso abierto, la ventana se desplaza (CSS) y la
    // caja con el texto devuelto quedaba por debajo del borde.
    entrada.scrollIntoView?.({ block: 'nearest' });
  }

  async function borrarConversacion() {
    const confirmado = await confirmarBorrado({
      titulo: TEXTOS.confirmarBorrarTitulo,
      mensaje: TEXTOS.confirmarBorrarMensaje,
      textoConfirmar: TEXTOS.confirmarBorrarBoton,
    });
    if (!confirmado) {
      return;
    }
    try {
      await cliente.limpiarHistorial();
      reiniciarPaginas();
      vaciar(registro);
      limpiarAviso();
      pintarBienvenida();
    } catch (error) {
      mostrarAviso(error?.noDisponible ? error : TEXTOS.errorAlBorrar);
    }
  }

  // --------------------------------------------------------------- eventos

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    enviarMensaje();
  });
  // Enter envía; Shift+Enter hace un salto de línea, como en cualquier chat.
  entrada.addEventListener('keydown', (evento) => {
    if (evento.key === 'Enter' && !evento.shiftKey && !evento.isComposing) {
      evento.preventDefault();
      enviarMensaje();
    }
  });
  botonBorrar.addEventListener('click', borrarConversacion);
  botonAnteriores.addEventListener('click', cargarAnteriores);
  // Scroll infinito: al llegar arriba se piden los anteriores.
  registro.addEventListener('scroll', () => {
    if (registro.scrollTop <= UMBRAL_DE_ANTERIORES && hayAnteriores && !cargandoAnteriores) {
      cargarAnteriores();
    }
  });
  botonCerrar.addEventListener('click', () => cerrar());

  // -------------------------------------------------------------- soporte

  function soporteAbierto() {
    return !panelSoporte.elemento.hidden;
  }

  // «Soporte» y «Preferencias» ocupan el sitio de la conversación: se oculta
  // la región entera (el envoltorio con la lista) y el formulario.
  function ocultarConversacion(si) {
    conversacion.hidden = si;
    registro.hidden = si;
    formulario.hidden = si;
  }

  function mostrarSoporte(si) {
    if (si) {
      ocultarPreferencias();
    }
    ocultarConversacion(si);
    if (si) {
      limpiarAviso();
      panelSoporte.abrir({ conConversacion: hayPreguntaSinResolver(), vista: vista() });
    } else {
      panelSoporte.elemento.hidden = true;
    }
    botonSoporte.setAttribute('aria-expanded', String(si));
    if (si) {
      panelSoporte.enfocar();
    } else {
      enfocarDentro();
    }
  }

  botonSoporte.addEventListener('click', () => mostrarSoporte(!soporteAbierto()));

  // ms-chatbot.yaml 1.8.0 — si alguna respuesta de esta conversación ofreció
  // soporte (no entendió, o «no me sirvió»), «Soporte» ofrece crear la
  // solicitud con ella en un clic.
  function hayPreguntaSinResolver() {
    return registro.querySelector('[data-accion="ofrecer-soporte"]') !== null;
  }

  // ---------------------------------------------------------- preferencias

  function preferenciasAbiertas() {
    return !panelPreferencias.elemento.hidden;
  }

  function ocultarPreferencias() {
    panelPreferencias.elemento.hidden = true;
    botonPreferencias.setAttribute('aria-expanded', 'false');
  }

  function mostrarPreferencias(si) {
    if (si && soporteAbierto()) {
      panelSoporte.elemento.hidden = true;
      botonSoporte.setAttribute('aria-expanded', 'false');
    }
    ocultarConversacion(si);
    if (si) {
      limpiarAviso();
      panelPreferencias.abrir();
      botonPreferencias.setAttribute('aria-expanded', 'true');
      panelPreferencias.enfocar();
    } else {
      ocultarPreferencias();
      enfocarDentro();
    }
  }

  botonPreferencias.addEventListener('click', () => mostrarPreferencias(!preferenciasAbiertas()));

  // ------------------------------------------------- minimizar y tamaño

  function minimizada() {
    return ventana.classList.contains('chatbot-ventana--minimizada');
  }

  function fijarMinimizada(si) {
    ventana.classList.toggle('chatbot-ventana--minimizada', si);
    botonMinimizar.textContent = si ? '+' : '−';
    botonMinimizar.setAttribute('aria-label', si ? TEXTOS.restaurar : TEXTOS.minimizar);
    botonMinimizar.setAttribute('aria-expanded', String(!si));
  }

  botonMinimizar.addEventListener('click', () => {
    fijarMinimizada(!minimizada());
    if (!minimizada()) {
      enfocarDentro();
    }
  });

  function pantalla() {
    return {
      ancho: globalThis.innerWidth ?? 1440,
      alto: globalThis.innerHeight ?? 900,
    };
  }

  function aplicarTamano(pedido, { recordar = true } = {}) {
    const { ancho, alto } = acotarTamano(pedido, pantalla());
    ventana.style.width = `${ancho}px`;
    ventana.style.height = `${alto}px`;
    ventana.style.maxHeight = 'none';
    ventana.dataset.tamano = 'propio';
    if (recordar) {
      try {
        globalThis.sessionStorage?.setItem(CLAVE_TAMANO, JSON.stringify({ ancho, alto }));
      } catch {
        // Sin almacenamiento: el tamaño vale mientras la vista siga abierta.
      }
    }
  }

  function tamanoNormal() {
    ventana.style.removeProperty('width');
    ventana.style.removeProperty('height');
    ventana.style.removeProperty('max-height');
    delete ventana.dataset.tamano;
    try {
      globalThis.sessionStorage?.removeItem(CLAVE_TAMANO);
    } catch {
      // Nada que olvidar.
    }
  }

  function tamanoActual() {
    const caja = ventana.getBoundingClientRect();
    return {
      ancho: caja.width || parseFloat(ventana.style.width) || TAMANO.anchoMinimo,
      alto: caja.height || parseFloat(ventana.style.height) || TAMANO.altoMinimo,
    };
  }

  // El que se eligió en otra vista de esta misma sesión.
  try {
    const guardado = JSON.parse(globalThis.sessionStorage?.getItem(CLAVE_TAMANO) ?? 'null');
    if (guardado && Number.isFinite(guardado.ancho) && Number.isFinite(guardado.alto)) {
      aplicarTamano(guardado, { recordar: false });
    }
  } catch {
    // Un valor ilegible se ignora: tamaño normal.
  }

  // Con el teclado: flecha izquierda/arriba agranda (la esquina se aleja de
  // la esquina fija), derecha/abajo encoge, Inicio vuelve al normal.
  asa.addEventListener('keydown', (evento) => {
    const actual = tamanoActual();
    const paso = TAMANO.paso;
    const cambios = {
      ArrowLeft: { ancho: actual.ancho + paso, alto: actual.alto },
      ArrowRight: { ancho: actual.ancho - paso, alto: actual.alto },
      ArrowUp: { ancho: actual.ancho, alto: actual.alto + paso },
      ArrowDown: { ancho: actual.ancho, alto: actual.alto - paso },
    };
    if (evento.key === 'Home') {
      evento.preventDefault();
      tamanoNormal();
    } else if (cambios[evento.key]) {
      evento.preventDefault();
      aplicarTamano(cambios[evento.key]);
    }
  });

  // Con el puntero: se arrastra la esquina superior izquierda.
  asa.addEventListener('pointerdown', (evento) => {
    if (evento.button !== 0) {
      return;
    }
    evento.preventDefault();
    const inicio = { x: evento.clientX, y: evento.clientY, ...tamanoActual() };
    asa.setPointerCapture?.(evento.pointerId);
    const mover = (movimiento) =>
      aplicarTamano(
        {
          ancho: inicio.ancho + (inicio.x - movimiento.clientX),
          alto: inicio.alto + (inicio.y - movimiento.clientY),
        },
        { recordar: false },
      );
    const soltar = () => {
      asa.removeEventListener('pointermove', mover);
      asa.removeEventListener('pointerup', soltar);
      asa.removeEventListener('pointercancel', soltar);
      aplicarTamano(tamanoActual());
    };
    asa.addEventListener('pointermove', mover);
    asa.addEventListener('pointerup', soltar);
    asa.addEventListener('pointercancel', soltar);
  });
  ventana.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape') {
      evento.stopPropagation();
      cerrar();
    }
  });

  // ---------------------------------------------------------------- abrir

  function pintarEstado() {
    const actual = sesion() ?? {};
    estado.textContent =
      actual.autenticado && actual.apodo
        ? TEXTOS.estadoJugador(actual.apodo)
        : TEXTOS.estadoVisitante;
  }

  function abrir(desde = null) {
    devolverFocoA = desde ?? document.activeElement;
    pintarEstado();
    fijarMinimizada(false);
    ventana.hidden = false;
    if (!cargada) {
      cargarHistorial();
    }
    enfocarDentro();
  }

  // El foco tiene que quedar DENTRO de la ventana (si no, Escape no la
  // cierra): en la caja de texto si se puede escribir —también al abrir,
  // mientras carga la conversación (#506)—; si el asistente no está, en la
  // primera acción del aviso; y si no, en «Cerrar».
  function enfocarDentro() {
    if (ventana.hidden) {
      return;
    }
    if (minimizada()) {
      botonMinimizar.focus();
      return;
    }
    if (soporteAbierto()) {
      panelSoporte.enfocar();
      return;
    }
    if (preferenciasAbiertas()) {
      panelPreferencias.enfocar();
      return;
    }
    const destino = !entrada.disabled
      ? entrada
      : (zonaAviso.querySelector('a, button') ?? botonCerrar);
    destino.focus();
  }

  function cerrar() {
    if (ventana.hidden) {
      return;
    }
    ventana.hidden = true;
    // El botón flotante escucha esto para dejar de anunciar «Cerrar».
    ventana.dispatchEvent(new CustomEvent('chatbot:cerrada', { bubbles: true }));
    if (devolverFocoA instanceof HTMLElement) {
      devolverFocoA.focus();
    }
  }

  return {
    elemento: ventana,
    abrir,
    cerrar,
    alternar: (desde = null) => (ventana.hidden ? abrir(desde) : cerrar()),
    abierta: () => !ventana.hidden,
    minimizada,
  };
}

/**
 * «Tú:» o «Asistente:» delante de cada mensaje, solo para el lector de
 * pantalla (#506): sin esto, una conversación leída en voz alta no dice quién
 * habla. Fuera del párrafo del texto, que sigue siendo solo el mensaje.
 *
 * @param {boolean} esBot
 * @returns {HTMLElement}
 */
function quienHabla(esBot) {
  return h('span', {
    clase: 'solo-lectores',
    texto: `${esBot ? TEXTOS.prefijoAsistente : TEXTOS.prefijoUsuario} `,
  });
}

/** El `motivo` del problem details, venga ya leído o dentro del problema. */
function motivoDe(error) {
  return error?.motivo ?? error?.problema?.motivo ?? null;
}

/**
 * Texto de un error que no es "no disponible". Se decide por el `motivo` y el
 * estado, nunca por el texto del servidor (MAPEO-ERRORES §2).
 *
 * @param {string|{estado?: number, motivo?: string|null, reintentarEn?: number|null}|null} error
 *   una cadena ya es el texto final
 * @param {Function|null} alReintentar presente cuando falló la carga
 * @returns {string}
 */
function textoDeError(error, alReintentar) {
  if (typeof error === 'string') {
    return error;
  }
  const porMotivo = POR_MOTIVO.mensaje.get(motivoDe(error));
  if (porMotivo) {
    return porMotivo;
  }
  if (error?.estado === 429) {
    return TEXTOS.demasiadosMensajes(error.reintentarEn ?? null);
  }
  if (error?.estado === 400) {
    return TEXTOS.mensajeInvalido;
  }
  return alReintentar ? TEXTOS.errorAlCargar : TEXTOS.errorAlEnviar;
}

/**
 * #506 — lo que el jugador puede corregir es una advertencia; lo demás, un
 * error.
 *
 * @param {string|{estado?: number}|null} error
 * @returns {'advertencia'|'error'}
 */
function tonoDeError(error) {
  const estado = typeof error === 'string' ? null : error?.estado;
  return estado === 400 || estado === 422 || estado === 429 ? 'advertencia' : 'error';
}

/**
 * #506 — por qué no se guardó una calificación: su comentario pasa por la
 * lista negra y por la revisión de código (contrato 1.4.0).
 *
 * @param {{estado?: number, noDisponible?: boolean, motivo?: string|null,
 *          reintentarEn?: number|null}|null} error
 * @returns {string}
 */
function textoDeErrorAlCalificar(error) {
  if (error?.noDisponible) {
    return TEXTOS.noDisponibleTitulo;
  }
  const porMotivo = POR_MOTIVO.comentario.get(motivoDe(error));
  if (porMotivo) {
    return porMotivo;
  }
  if (error?.estado === 429) {
    return TEXTOS.demasiadosMensajes(error.reintentarEn ?? null);
  }
  return TEXTOS.errorAlCalificar;
}

/**
 * La URL tal cual si es http(s); si no, null. Se asigna con `setAttribute`,
 * así que no hace falta escaparla, solo descartar `javascript:` y compañía.
 *
 * @param {unknown} valor
 * @returns {string|null}
 */
export function urlPermitida(valor) {
  if (!valor) {
    return null;
  }
  try {
    const destino = new URL(String(valor));
    return destino.protocol === 'http:' || destino.protocol === 'https:' ? destino.href : null;
  } catch {
    return null;
  }
}
