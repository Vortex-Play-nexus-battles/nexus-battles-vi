/**
 * «Hablar con soporte» dentro de la ventana del asistente.
 *
 * Cuando el asistente no resuelve algo, el jugador con sesión abre una
 * solicitud de soporte (un ticket en el servidor) y ve aquí las suyas, con su
 * estado y la respuesta de soporte cuando llega.
 *
 * Un visitante no abre solicitudes (el servidor le responde 401). #506 — en
 * vez de un callejón sin salida se le explica que hablar con una persona del
 * equipo pide una cuenta, y se le ofrece iniciar sesión (volviendo después a
 * la página donde estaba) o crear una. Ya no se le ofrece el chat general:
 * también pide sesión, así que era otra puerta al mismo login.
 *
 * El texto que se pinta sale del servidor, así que entra con `textContent`
 * (`h()`), nunca como HTML. Los errores se deciden por el `motivo` del
 * problem details, no por su texto.
 *
 * @module comun/ui/soporte-chatbot
 */

import { pintarAviso, limpiarAviso } from './aviso.js';
import { boton, conCarga } from './boton.js';
import { campo } from './campo.js';
import { distintivo } from './distintivo.js';
import { h, vaciar } from './dom.js';
import { fechaHora } from './formato.js';

/** Límites del contrato (`AbrirTicket`). */
export const LIMITES_SOPORTE = Object.freeze({ asunto: 150, mensaje: 2000 });

/** Las categorías del contrato, con el nombre que lee el jugador. */
export const CATEGORIAS = Object.freeze([
  { valor: 'SOPORTE_TECNICO', texto: 'Problema técnico' },
  { valor: 'CUENTA_Y_REGISTRO', texto: 'Mi cuenta' },
  { valor: 'SUBASTA_Y_COMERCIO', texto: 'Subastas y comercio' },
  { valor: 'PRODUCTO', texto: 'Héroes, armas e ítems' },
  { valor: 'MECANICA_JUEGO', texto: 'Reglas del juego' },
  { valor: 'MODALIDAD_JUEGO', texto: 'Modos de juego' },
  { valor: 'POLITICAS_Y_TERMINOS', texto: 'Políticas y términos' },
  { valor: 'FAQ_GENERAL', texto: 'Otra consulta' },
]);

/** Estado de una solicitud: texto y variante del distintivo del kit. */
export const ESTADOS = Object.freeze({
  ABIERTO: { texto: 'Abierta', variante: 'activo' },
  EN_PROCESO: { texto: 'En revisión', variante: 'en-progreso' },
  RESUELTO: { texto: 'Respondida', variante: 'completada' },
  CERRADO: { texto: 'Cerrada', variante: null },
});

export const TEXTOS_SOPORTE = Object.freeze({
  titulo: 'Hablar con soporte',
  intro: 'Cuéntanos qué pasa y una persona del equipo te responderá aquí.',
  volver: 'Volver al chat',
  visitante:
    'Para hablar con una persona del equipo necesitas una cuenta: inicia sesión o crea una.',
  iniciarSesion: 'Iniciar sesión',
  crearCuenta: 'Crear cuenta',
  categoria: 'Tema',
  asunto: 'Asunto',
  mensaje: 'Qué pasa',
  pistaMensaje: 'No escribas contraseñas ni datos de tarjetas.',
  enviar: 'Enviar solicitud',
  enviando: 'Enviando…',
  faltaAsunto: 'Escribe el asunto.',
  faltaMensaje: 'Cuéntanos qué pasa.',
  enviada: 'Listo: abrimos tu solicitud.',
  enviadaDetalle: 'Te avisaremos aquí cuando soporte responda.',
  tuyas: 'Tus solicitudes',
  ninguna: 'Todavía no has abierto ninguna solicitud.',
  cargando: 'Cargando tus solicitudes…',
  errorAlCargar: 'No se pudieron cargar tus solicitudes.',
  respuesta: 'Respuesta de soporte:',
  abiertaEl: (fecha) => `Abierta el ${fecha}`,
  // Errores, por el `motivo` del servidor.
  yaAbierta: 'Ya tienes una solicitud abierta.',
  yaAbiertaDetalle: 'Espera la respuesta de soporte antes de abrir otra.',
  sinSesion: 'Tu sesión terminó.',
  sinSesionDetalle: 'Inicia sesión de nuevo para hablar con soporte.',
  bloqueado: 'Tu mensaje tiene palabras que no están permitidas.',
  bloqueadoDetalle: 'Revísalo y vuelve a intentarlo.',
  // #506 — contrato 1.4.0: el servidor también rechaza el código incrustado.
  conCodigo: 'Tu mensaje parece contener código o instrucciones del sistema.',
  conCodigoDetalle: 'Escríbelo con palabras y vuelve a intentarlo.',
  muchas: 'Enviaste muchas solicitudes seguidas.',
  muchasDetalle: 'Espera un momento y vuelve a intentarlo.',
  muchasDetalleCon: (segundos) => `Espera ${segundos} s y vuelve a intentarlo.`,
  noDisponible: 'Soporte no está disponible en este momento.',
  noDisponibleDetalle: 'Inténtalo de nuevo en unos minutos.',
  // 503 MODERACION_NO_DISPONIBLE: lo que falló es la revisión del texto, no soporte.
  sinRevision: 'No pudimos revisar tu mensaje en este momento.',
  sinRevisionDetalle:
    'El filtro de contenido no respondió y por seguridad no enviamos nada. Inténtalo de nuevo en unos minutos.',
  invalido: 'Revisa los datos de la solicitud.',
  invalidoDetalle: 'El asunto admite hasta 150 caracteres y el mensaje hasta 2000.',
  error: 'No se pudo enviar tu solicitud.',
  errorDetalle: 'Inténtalo de nuevo.',
  // ms-chatbot.yaml 1.8.0 — la solicitud en un clic, con la conversación.
  desdeConversacion:
    'Podemos enviarle a soporte esta conversación: la pregunta que el asistente no resolvió y lo que te contestó.',
  crearDesdeConversacion: 'Crear solicitud con esta conversación',
  creandoDesdeConversacion: 'Creando tu solicitud…',
  creadaDesdeConversacion: 'Listo: abrimos tu solicitud con esta conversación.',
  creadaDesdeConversacionDetalle:
    'La ves abajo, en «Tus solicitudes». Una persona del equipo te responderá ahí.',
  oEscribela: 'O cuéntalo con tus palabras:',
  sinConversacion: 'Todavía no hay una pregunta que enviar.',
  sinConversacionDetalle: 'Escribe tu solicitud en el formulario.',
});

/**
 * Aviso para un error al abrir una solicitud. Por el `motivo`, luego por el estado.
 *
 * #506 — también la inyección de código (422 INYECCION_DETECTADA, contrato
 * 1.4.0) y cualquier 429, traiga `motivo` o no (el del borde no lo trae), con
 * los segundos de `Retry-After` si llegaron.
 *
 * @param {{estado?: number, noDisponible?: boolean, motivo?: string|null,
 *          reintentarEn?: number|null, problema?: {motivo?: string}|null}} error
 * @returns {{tono: string, titulo: string, detalle: string}}
 */
export function avisoDeError(error) {
  const motivo = error?.motivo ?? error?.problema?.motivo ?? null;
  const t = TEXTOS_SOPORTE;
  if (motivo === 'TICKET_ABIERTO') {
    return { tono: 'advertencia', titulo: t.yaAbierta, detalle: t.yaAbiertaDetalle };
  }
  if (motivo === 'SESION_REQUERIDA' || error?.estado === 401 || error?.estado === 403) {
    return { tono: 'advertencia', titulo: t.sinSesion, detalle: t.sinSesionDetalle };
  }
  if (motivo === 'CONTENIDO_BLOQUEADO') {
    return { tono: 'advertencia', titulo: t.bloqueado, detalle: t.bloqueadoDetalle };
  }
  if (motivo === 'INYECCION_DETECTADA') {
    return { tono: 'advertencia', titulo: t.conCodigo, detalle: t.conCodigoDetalle };
  }
  if (motivo === 'LIMITE_DE_FRECUENCIA' || error?.estado === 429) {
    const segundos = error?.reintentarEn;
    return {
      tono: 'advertencia',
      titulo: t.muchas,
      detalle: segundos > 0 ? t.muchasDetalleCon(segundos) : t.muchasDetalle,
    };
  }
  if (motivo === 'MODERACION_NO_DISPONIBLE') {
    return { tono: 'error', titulo: t.sinRevision, detalle: t.sinRevisionDetalle };
  }
  if (error?.noDisponible) {
    return { tono: 'error', titulo: t.noDisponible, detalle: t.noDisponibleDetalle };
  }
  if (error?.estado === 400) {
    return { tono: 'advertencia', titulo: t.invalido, detalle: t.invalidoDetalle };
  }
  return { tono: 'error', titulo: t.error, detalle: t.errorDetalle };
}

/**
 * @param {{cliente: {abrirTicket: Function, misTickets: Function},
 *          sesion: () => {autenticado?: boolean},
 *          login: string|(() => string|null)|null,
 *          registro?: string|(() => string|null)|null,
 *          alVolver: () => void}} opciones `login` y `registro`: la URL, o una
 *          función que la da al abrir el panel (así la vuelta tras entrar es
 *          la página de ese momento)
 * @returns {{elemento: HTMLElement, abrir: () => void, enfocar: () => void,
 *            actualizarTicket: (ticket: object) => void, recargarLista: () => void}}
 */
export function crearPanelSoporte({ cliente, sesion, login, registro = null, alVolver }) {
  let enviando = false;

  const botonVolver = h('button', {
    clase: 'chatbot-ventana__accion',
    texto: TEXTOS_SOPORTE.volver,
    atributos: { type: 'button' },
    datos: { accion: 'volver-al-chat' },
  });
  botonVolver.addEventListener('click', () => alVolver());

  const zonaAviso = h('div', { clase: 'chatbot-soporte__aviso', atributos: { hidden: true } });
  const contenido = h('div', { clase: 'chatbot-soporte__contenido' });

  const panel = h('section', {
    clase: 'chatbot-soporte',
    atributos: { hidden: true, 'aria-label': TEXTOS_SOPORTE.titulo },
    datos: { chatbotSoporte: '' },
    hijos: [
      h('div', {
        clase: 'chatbot-soporte__cabecera',
        hijos: [
          h('h3', { clase: 'chatbot-soporte__titulo', texto: TEXTOS_SOPORTE.titulo }),
          botonVolver,
        ],
      }),
      zonaAviso,
      contenido,
    ],
  });

  // --------------------------------------------------------- visitante

  function pintarVisitante() {
    const acciones = h('div', { clase: 'chatbot-soporte__acciones' });
    const destinoLogin = typeof login === 'function' ? login() : login;
    const destinoRegistro = typeof registro === 'function' ? registro() : registro;
    if (destinoLogin) {
      acciones.append(
        boton({
          texto: TEXTOS_SOPORTE.iniciarSesion,
          href: destinoLogin,
          nombre: 'iniciar-sesion',
        }),
      );
    }
    if (destinoRegistro) {
      acciones.append(
        boton({
          texto: TEXTOS_SOPORTE.crearCuenta,
          variante: 'secundario',
          href: destinoRegistro,
          nombre: 'crear-cuenta',
        }),
      );
    }
    contenido.append(
      h('p', { clase: 'chatbot-soporte__texto', texto: TEXTOS_SOPORTE.visitante }),
      acciones,
    );
  }

  // ------------------------------------------------------------ jugador

  const categoria = campo({
    nombre: 'categoria',
    etiqueta: TEXTOS_SOPORTE.categoria,
    opciones: CATEGORIAS,
  });
  const asunto = campo({
    nombre: 'asunto',
    etiqueta: TEXTOS_SOPORTE.asunto,
    atributos: { maxlength: LIMITES_SOPORTE.asunto },
  });
  const mensaje = campo({
    nombre: 'mensaje',
    etiqueta: TEXTOS_SOPORTE.mensaje,
    multilinea: true,
    pista: TEXTOS_SOPORTE.pistaMensaje,
    atributos: { maxlength: LIMITES_SOPORTE.mensaje },
  });
  const botonEnviar = boton({
    texto: TEXTOS_SOPORTE.enviar,
    tipo: 'submit',
    nombre: 'enviar-solicitud',
  });
  const formulario = h('form', {
    clase: 'chatbot-soporte__formulario',
    atributos: { novalidate: true },
    hijos: [categoria.elemento, asunto.elemento, mensaje.elemento, botonEnviar],
  });
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    enviar();
  });

  const lista = h('ul', { clase: 'chatbot-soporte__lista', atributos: { 'aria-live': 'polite' } });

  // ms-chatbot.yaml 1.8.0 — después de que el asistente no entendió o de un
  // «no me sirvió», un clic basta: el servidor arma la solicitud con la
  // pregunta y la conversación. El formulario sigue debajo.
  const zonaDesdeConversacion = h('div', {
    clase: 'chatbot-soporte__desde-conversacion',
    datos: { zona: 'desde-conversacion' },
    atributos: { hidden: true },
  });

  function pintarDesdeConversacion() {
    const crear = boton({
      texto: TEXTOS_SOPORTE.crearDesdeConversacion,
      nombre: 'crear-solicitud-desde-conversacion',
    });
    crear.addEventListener('click', () => crearDesdeConversacion(crear));
    zonaDesdeConversacion.replaceChildren(
      h('p', { clase: 'chatbot-soporte__texto', texto: TEXTOS_SOPORTE.desdeConversacion }),
      crear,
      h('p', { clase: 'chatbot-soporte__texto', texto: TEXTOS_SOPORTE.oEscribela }),
    );
    zonaDesdeConversacion.hidden = false;
  }

  function quitarDesdeConversacion() {
    vaciar(zonaDesdeConversacion);
    zonaDesdeConversacion.hidden = true;
  }

  async function crearDesdeConversacion(crear) {
    if (enviando) {
      return;
    }
    enviando = true;
    limpiarAviso(zonaAviso);
    conCarga(crear, true, TEXTOS_SOPORTE.creandoDesdeConversacion);
    try {
      await cliente.abrirTicketDesdeConversacion({ vista: vistaDeApertura });
      alCrearDesdeConversacion();
    } catch (error) {
      pintarAviso(zonaAviso, avisoDesdeConversacion(error));
    } finally {
      terminarDesdeConversacion(crear);
    }
  }

  function alCrearDesdeConversacion() {
    quitarDesdeConversacion();
    pintarAviso(zonaAviso, {
      tono: 'exito',
      titulo: TEXTOS_SOPORTE.creadaDesdeConversacion,
      detalle: TEXTOS_SOPORTE.creadaDesdeConversacionDetalle,
    });
    cargarLista();
  }

  function terminarDesdeConversacion(crear) {
    enviando = false;
    conCarga(crear, false);
  }

  function pintarJugador() {
    contenido.append(
      h('p', { clase: 'chatbot-soporte__texto', texto: TEXTOS_SOPORTE.intro }),
      zonaDesdeConversacion,
      formulario,
      h('h4', { clase: 'chatbot-soporte__subtitulo', texto: TEXTOS_SOPORTE.tuyas }),
      lista,
    );
    if (conConversacion) {
      pintarDesdeConversacion();
    } else {
      quitarDesdeConversacion();
    }
    cargarLista();
  }

  async function cargarLista() {
    vaciar(lista);
    lista.append(h('li', { clase: 'chatbot-soporte__vacio', texto: TEXTOS_SOPORTE.cargando }));
    try {
      const tickets = await cliente.misTickets();
      pintarLista(tickets ?? []);
    } catch {
      vaciar(lista);
      lista.append(
        h('li', { clase: 'chatbot-soporte__vacio', texto: TEXTOS_SOPORTE.errorAlCargar }),
      );
    }
  }

  function pintarLista(tickets) {
    vaciar(lista);
    if (tickets.length === 0) {
      lista.append(h('li', { clase: 'chatbot-soporte__vacio', texto: TEXTOS_SOPORTE.ninguna }));
      return;
    }
    for (const ticket of tickets) {
      lista.append(elementoDeTicket(ticket));
    }
  }

  function elementoDeTicket(ticket) {
    const estado = ESTADOS[ticket.estado] ?? { texto: ticket.estado, variante: null };
    const item = h('li', {
      clase: 'chatbot-soporte__ticket',
      datos: { ticketId: ticket.ticketId ?? '' },
      hijos: [
        h('div', {
          clase: 'chatbot-soporte__fila',
          hijos: [
            h('p', { clase: 'chatbot-soporte__asunto', texto: ticket.asunto }),
            distintivo(estado.texto, estado.variante),
          ],
        }),
      ],
    });
    if (ticket.creadoEn) {
      item.append(
        h('p', {
          clase: 'chatbot-soporte__meta',
          texto: TEXTOS_SOPORTE.abiertaEl(fechaHora(ticket.creadoEn)),
        }),
      );
    }
    if (ticket.respuesta) {
      item.append(
        h('p', {
          clase: 'chatbot-soporte__respuesta',
          hijos: [h('strong', { texto: TEXTOS_SOPORTE.respuesta }), ` ${ticket.respuesta}`],
        }),
      );
    }
    return item;
  }

  async function enviar() {
    if (enviando) {
      return;
    }
    const textoAsunto = asunto.control.value.trim();
    const textoMensaje = mensaje.control.value.trim();
    asunto.marcarError(textoAsunto ? null : TEXTOS_SOPORTE.faltaAsunto);
    mensaje.marcarError(textoMensaje ? null : TEXTOS_SOPORTE.faltaMensaje);
    if (!textoAsunto || !textoMensaje) {
      (textoAsunto ? mensaje : asunto).control.focus();
      return;
    }

    enviando = true;
    limpiarAviso(zonaAviso);
    conCarga(botonEnviar, true, TEXTOS_SOPORTE.enviando);
    try {
      await cliente.abrirTicket({
        categoria: categoria.control.value,
        asunto: textoAsunto,
        mensaje: textoMensaje,
      });
      alEnviar();
    } catch (error) {
      pintarAviso(zonaAviso, avisoDeError(error));
    } finally {
      terminarEnvio();
    }
  }

  // Fuera de `enviar` a propósito: lo que se toca después de un `await` vive
  // en funciones aparte (regla require-atomic-updates de ESLint).
  function alEnviar() {
    asunto.control.value = '';
    mensaje.control.value = '';
    pintarAviso(zonaAviso, {
      tono: 'exito',
      titulo: TEXTOS_SOPORTE.enviada,
      detalle: TEXTOS_SOPORTE.enviadaDetalle,
    });
    cargarLista();
  }

  function terminarEnvio() {
    enviando = false;
    conCarga(botonEnviar, false);
  }

  // -------------------------------------------------------------- abrir

  // 1.8.0 — lo que dijo la ventana al abrir: si la conversación tiene una
  // pregunta sin resolver, y en qué sección está el jugador.
  let conConversacion = false;
  let vistaDeApertura = null;

  /**
   * @param {{conConversacion?: boolean, vista?: string|null}} [opciones]
   *   `conConversacion`: el asistente no entendió algo o no le sirvió, así
   *   que se ofrece crear la solicitud con la conversación (1.8.0)
   */
  function abrir({ conConversacion: ofrecer = false, vista = null } = {}) {
    conConversacion = Boolean(ofrecer);
    vistaDeApertura = vista;
    limpiarAviso(zonaAviso);
    vaciar(contenido);
    if (sesion()?.autenticado) {
      pintarJugador();
    } else {
      pintarVisitante();
    }
    panel.hidden = false;
  }

  // El primero en el orden de la página: «Crear solicitud con esta
  // conversación» si está, luego el formulario o el botón del visitante.
  function enfocar() {
    const destino =
      panel.querySelector('[data-accion="crear-solicitud-desde-conversacion"], select, a.boton') ??
      botonVolver;
    destino.focus();
  }

  // ---------------------------------------------------- 7.4.9, en vivo

  /** Si «Tus solicitudes» está a la vista (panel abierto, con sesión). */
  function listaALaVista() {
    return !panel.hidden && contenido.contains(lista);
  }

  /**
   * Un cambio que llegó por el canal en tiempo real (`chatbot.yaml` 1.0.0,
   * `/user/queue/soporte/tickets`): reemplaza esa solicitud en la lista. Si la
   * lista no la tiene, se relee entera; con el panel cerrado no hace nada,
   * porque al abrirlo se lee de nuevo.
   *
   * @param {{ticketId?: string}} ticket lo que el jugador ve de la solicitud
   */
  function actualizarTicket(ticket) {
    if (!listaALaVista() || !ticket?.ticketId) {
      return;
    }
    const actual = [...lista.querySelectorAll('[data-ticket-id]')].find(
      (item) => item.dataset.ticketId === ticket.ticketId,
    );
    if (actual) {
      actual.replaceWith(elementoDeTicket(ticket));
    } else {
      cargarLista();
    }
  }

  /** Relee la lista si está a la vista (al volver el canal tras una caída). */
  function recargarLista() {
    if (listaALaVista()) {
      cargarLista();
    }
  }

  return { elemento: panel, abrir, enfocar, actualizarTicket, recargarLista };
}

/**
 * ms-chatbot.yaml 1.8.0 — aviso para un fallo de «Crear solicitud con esta
 * conversación»: el 409 SIN_CONVERSACION es solo suyo; lo demás, como al
 * enviar el formulario.
 *
 * @param {{estado?: number, motivo?: string|null, problema?: {motivo?: string}|null}} error
 * @returns {{tono: string, titulo: string, detalle: string}}
 */
export function avisoDesdeConversacion(error) {
  if ((error?.problema?.motivo ?? error?.motivo) === 'SIN_CONVERSACION') {
    return {
      tono: 'advertencia',
      titulo: TEXTOS_SOPORTE.sinConversacion,
      detalle: TEXTOS_SOPORTE.sinConversacionDetalle,
    };
  }
  return avisoDeError(error);
}
