/**
 * «Hablar con soporte» dentro de la ventana del asistente.
 *
 * Cuando el asistente no resuelve algo, se abre una solicitud de soporte (un
 * ticket en el servidor) y aquí se ven las propias, con su estado y la
 * respuesta de soporte cuando llega. Desde ms-chatbot.yaml 1.12.0
 * (RF-CHA-007) el asistente la abre solo al no resolver una pregunta; este
 * panel sirve para verla, para abrir una con la conversación si eso falló, o
 * para escribirla con otras palabras.
 *
 * 1.12.0 — el visitante también (RF-CHA-007 nombra al Visitante): sus
 * solicitudes son de su sesión de visitante, así que una nota le dice que
 * verá la respuesta aquí mientras siga en ella y le ofrece iniciar sesión
 * (volviendo después a la página donde estaba) o crear una cuenta. Antes
 * (#506) solo se le ofrecía la cuenta, porque el servidor le respondía 401.
 *
 * 1.14.0 (§7.4.6 «Opción de adjuntar capturas de pantalla para soporte
 * técnico») — a una solicitud abierta se le adjuntan hasta tres capturas,
 * al escribirla (opcional) o después, desde «Tus solicitudes», donde también
 * se ven. Antes de elegirla, la pista dice que no se vean contraseñas,
 * códigos ni tarjetas: a una imagen no se le puede aplicar la redacción de
 * los textos.
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
import {
  MAXIMO_DE_CAPTURAS,
  crearRegistroDeImagenes,
  listaDeCapturas,
  pesoLegible,
  rechazoDeCaptura,
  selectorDeCaptura,
} from './capturas-chatbot.js';
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

/** 1.14.0 — los estados en los que una solicitud admite capturas. */
const ADMITEN_CAPTURAS = new Set(['ABIERTO', 'EN_PROCESO']);

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
  // 1.12.0 — el visitante también abre solicitudes, ligadas a su sesión.
  visitante:
    'Estás como visitante: verás la respuesta aquí, en «Tus solicitudes», mientras sigas en esta sesión del navegador. Con una cuenta, tus solicitudes quedan guardadas.',
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
  // ms-chatbot.yaml 1.14.0 (§7.4.6) — capturas de pantalla.
  capturaOpcional: 'Adjuntar una captura (opcional)',
  adjuntarCaptura: 'Adjuntar captura',
  pistaCaptura:
    'PNG o JPEG de hasta 2 MB. Antes de elegirla, revisa que no se vean contraseñas, códigos ni datos de tarjetas: soporte la verá tal cual.',
  subiendoCaptura: 'Subiendo captura…',
  capturaElegida: (peso) => `Captura lista para enviar (${peso}).`,
  quitarCaptura: 'Quitar',
  capturaAdjunta: 'Adjuntamos tu captura.',
  capturaAdjuntaDetalle: 'Soporte la verá con tu solicitud.',
  conCaptura: 'Le adjuntamos tu captura.',
  enviadaSinCaptura: 'Abrimos tu solicitud, pero no pudimos adjuntar la captura.',
  enviadaSinCapturaDetalle: (motivo) => `${motivo} Puedes adjuntarla desde «Tus solicitudes».`,
  capturaVacia: 'Ese archivo está vacío.',
  capturaVaciaDetalle: 'Elige una captura PNG o JPEG.',
  capturaNoAdmitida: 'Esa imagen no se puede adjuntar.',
  capturaNoAdmitidaDetalle: 'Elige una captura PNG o JPEG.',
  capturaGrande: 'La captura es demasiado grande.',
  capturaGrandeDetalle: 'Puede pesar hasta 2 MB y medir hasta 4096 píxeles por lado.',
  capturasCompletas: `Esta solicitud ya tiene ${MAXIMO_DE_CAPTURAS} capturas.`,
  capturasCompletasDetalle: 'No caben más; soporte ya puede verlas.',
  solicitudCerrada: 'Esta solicitud ya no admite capturas.',
  solicitudCerradaDetalle: 'Soporte ya la respondió o la cerró.',
  solicitudNoEncontrada: 'No encontramos esa solicitud.',
  solicitudNoEncontradaDetalle: 'Cierra «Hablar con soporte» y vuelve a abrirlo.',
  muchasCapturas: 'Subiste muchas capturas seguidas.',
  capturaError: 'No se pudo adjuntar la captura.',
  capturaErrorDetalle: 'Inténtalo de nuevo.',
});

/**
 * 1.14.0 — aviso para una captura que no vale la pena subir: vacía, de otro
 * tipo o de más de 2 MB (`rechazoDeCaptura`).
 *
 * @param {'vacia'|'tipo'|'tamano'} rechazo
 * @returns {{tono: string, titulo: string, detalle: string}}
 */
export function avisoDeRechazo(rechazo) {
  const t = TEXTOS_SOPORTE;
  if (rechazo === 'tamano') {
    return { tono: 'advertencia', titulo: t.capturaGrande, detalle: t.capturaGrandeDetalle };
  }
  if (rechazo === 'tipo') {
    return {
      tono: 'advertencia',
      titulo: t.capturaNoAdmitida,
      detalle: t.capturaNoAdmitidaDetalle,
    };
  }
  return { tono: 'advertencia', titulo: t.capturaVacia, detalle: t.capturaVaciaDetalle };
}

/**
 * 1.14.0 — aviso para un fallo al adjuntar una captura. Por el `motivo`; un
 * 413 o un 415 sin él (el del borde no lo trae) se entienden igual.
 *
 * @param {{estado?: number, noDisponible?: boolean, motivo?: string|null,
 *          reintentarEn?: number|null, problema?: {motivo?: string}|null}} error
 * @returns {{tono: string, titulo: string, detalle: string}}
 */
export function avisoDeCaptura(error) {
  const motivo = error?.motivo ?? error?.problema?.motivo ?? null;
  const t = TEXTOS_SOPORTE;
  if (motivo === 'CAPTURA_DEMASIADO_GRANDE' || error?.estado === 413) {
    return avisoDeRechazo('tamano');
  }
  if (motivo === 'CAPTURA_NO_ADMITIDA' || error?.estado === 415) {
    return avisoDeRechazo('tipo');
  }
  if (motivo === 'CAPTURA_AUSENTE') {
    return avisoDeRechazo('vacia');
  }
  if (motivo === 'CAPTURAS_COMPLETAS') {
    return {
      tono: 'advertencia',
      titulo: t.capturasCompletas,
      detalle: t.capturasCompletasDetalle,
    };
  }
  if (motivo === 'SOLICITUD_CERRADA') {
    return { tono: 'advertencia', titulo: t.solicitudCerrada, detalle: t.solicitudCerradaDetalle };
  }
  if (motivo === 'SOLICITUD_NO_ENCONTRADA') {
    return {
      tono: 'advertencia',
      titulo: t.solicitudNoEncontrada,
      detalle: t.solicitudNoEncontradaDetalle,
    };
  }
  if (motivo === 'LIMITE_DE_FRECUENCIA' || error?.estado === 429) {
    return { ...avisoDeError(error), titulo: t.muchasCapturas };
  }
  if (motivo === 'SESION_REQUERIDA' || error?.sinPermiso || error?.noDisponible) {
    return avisoDeError(error);
  }
  return { tono: 'error', titulo: t.capturaError, detalle: t.capturaErrorDetalle };
}

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
 * @param {{cliente: {abrirTicket: Function, misTickets: Function,
 *                    subirCaptura?: Function, leerCaptura?: Function},
 *          sesion: () => {autenticado?: boolean},
 *          login: string|(() => string|null)|null,
 *          registro?: string|(() => string|null)|null,
 *          alVolver: () => void,
 *          imagenes?: {crear: (contenido: Blob) => string, liberar: () => void}}} opciones
 *          `login` y `registro`: la URL, o una función que la da al abrir el
 *          panel (así la vuelta tras entrar es la página de ese momento).
 *          `imagenes`: los enlaces de objeto de las capturas (1.14.0)
 * @returns {{elemento: HTMLElement, abrir: () => void, enfocar: () => void,
 *            enfocarCaptura: () => Promise<void>,
 *            actualizarTicket: (ticket: object) => void, recargarLista: () => void}}
 */
export function crearPanelSoporte({
  cliente,
  sesion,
  login,
  registro = null,
  alVolver,
  imagenes = crearRegistroDeImagenes(),
}) {
  let enviando = false;
  let subiendo = false;

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

  // 1.12.0 — hasta cuándo verá la respuesta y, si quiere, una cuenta. Las
  // salidas son secundarias: lo principal del panel es pedir ayuda.
  function notaDeVisitante() {
    const acciones = h('div', { clase: 'chatbot-soporte__acciones' });
    const destinoLogin = typeof login === 'function' ? login() : login;
    const destinoRegistro = typeof registro === 'function' ? registro() : registro;
    if (destinoLogin) {
      acciones.append(
        boton({
          texto: TEXTOS_SOPORTE.iniciarSesion,
          variante: 'secundario',
          tamano: 'pequeno',
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
          tamano: 'pequeno',
          href: destinoRegistro,
          nombre: 'crear-cuenta',
        }),
      );
    }
    return h('div', {
      clase: 'chatbot-soporte__visitante',
      datos: { zona: 'visitante' },
      hijos: [
        h('p', { clase: 'chatbot-soporte__texto', texto: TEXTOS_SOPORTE.visitante }),
        acciones.childElementCount > 0 ? acciones : null,
      ],
    });
  }

  // ---------------------------------------------------- jugador y visitante

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
  // 1.14.0 — una captura opcional, que se adjunta en cuanto la solicitud
  // queda abierta. Elegirla no sube nada todavía.
  let capturaElegida = null;
  const elegida = h('p', {
    clase: 'chatbot-soporte__elegida',
    datos: { zona: 'captura-elegida' },
    atributos: { hidden: true },
  });
  const selectorDelFormulario = selectorDeCaptura({
    texto: TEXTOS_SOPORTE.capturaOpcional,
    pista: TEXTOS_SOPORTE.pistaCaptura,
    alElegir: elegirCaptura,
  });

  function elegirCaptura(archivo) {
    const rechazo = rechazoDeCaptura(archivo);
    if (rechazo) {
      pintarAviso(zonaAviso, avisoDeRechazo(rechazo));
      return;
    }
    limpiarAviso(zonaAviso);
    capturaElegida = archivo;
    const quitar = boton({
      texto: TEXTOS_SOPORTE.quitarCaptura,
      variante: 'secundario',
      tamano: 'pequeno',
      nombre: 'quitar-captura',
    });
    quitar.addEventListener('click', () => {
      quitarCapturaElegida();
      selectorDelFormulario.entrada.focus();
    });
    elegida.replaceChildren(
      h('span', { texto: TEXTOS_SOPORTE.capturaElegida(pesoLegible(archivo.size)) }),
      quitar,
    );
    elegida.hidden = false;
  }

  function quitarCapturaElegida() {
    capturaElegida = null;
    elegida.replaceChildren();
    elegida.hidden = true;
  }

  const botonEnviar = boton({
    texto: TEXTOS_SOPORTE.enviar,
    tipo: 'submit',
    nombre: 'enviar-solicitud',
  });
  const formulario = h('form', {
    clase: 'chatbot-soporte__formulario',
    atributos: { novalidate: true },
    hijos: [
      categoria.elemento,
      asunto.elemento,
      mensaje.elemento,
      selectorDelFormulario.elemento,
      elegida,
      botonEnviar,
    ],
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
    // 1.14.0 — si ya eligió una captura en el formulario, va con esta.
    const captura = capturaElegida;
    limpiarAviso(zonaAviso);
    conCarga(crear, true, TEXTOS_SOPORTE.creandoDesdeConversacion);
    try {
      const ticket = await cliente.abrirTicketDesdeConversacion({ vista: vistaDeApertura });
      await alCrearDesdeConversacion(ticket, captura);
    } catch (error) {
      pintarAviso(zonaAviso, avisoDesdeConversacion(error));
    } finally {
      terminarDesdeConversacion(crear);
    }
  }

  async function alCrearDesdeConversacion(ticket, captura) {
    quitarDesdeConversacion();
    pintarAviso(
      zonaAviso,
      await conLaCaptura(ticket, captura, {
        tono: 'exito',
        titulo: TEXTOS_SOPORTE.creadaDesdeConversacion,
        detalle: TEXTOS_SOPORTE.creadaDesdeConversacionDetalle,
      }),
    );
    cargarLista();
  }

  function terminarDesdeConversacion(crear) {
    enviando = false;
    conCarga(crear, false);
  }

  function pintarSolicitudes({ visitante }) {
    contenido.append(
      h('p', { clase: 'chatbot-soporte__texto', texto: TEXTOS_SOPORTE.intro }),
      ...(visitante ? [notaDeVisitante()] : []),
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
    cargaDeLaLista = cargarLista();
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
    imagenes.liberar();
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
    const capturas = zonaDeCapturas(ticket);
    if (capturas) {
      item.append(capturas);
    }
    return item;
  }

  // 1.14.0 — las capturas de la solicitud y, si aún admite, el selector.
  function zonaDeCapturas(ticket) {
    const capturas = Array.isArray(ticket.capturas) ? ticket.capturas : [];
    const admite =
      Boolean(ticket.ticketId) &&
      ADMITEN_CAPTURAS.has(ticket.estado) &&
      capturas.length < MAXIMO_DE_CAPTURAS;
    if (capturas.length === 0 && !admite) {
      return null;
    }
    const zona = h('div', { clase: 'chatbot-soporte__capturas', datos: { zona: 'capturas' } });
    if (capturas.length > 0) {
      zona.append(
        listaDeCapturas({
          capturas,
          asunto: ticket.asunto,
          leer: (capturaId) => cliente.leerCaptura(ticket.ticketId, capturaId),
          registro: imagenes,
        }),
      );
    }
    const zonaDeAviso = h('div', {
      clase: 'chatbot-soporte__aviso-captura',
      datos: { zona: 'aviso-captura' },
      atributos: { hidden: true },
    });
    if (admite) {
      const selector = selectorDeCaptura({
        texto: TEXTOS_SOPORTE.adjuntarCaptura,
        pista: TEXTOS_SOPORTE.pistaCaptura,
        alElegir: (archivo) => adjuntar(ticket, archivo, selector, zonaDeAviso),
      });
      zona.append(selector.elemento);
    }
    zona.append(zonaDeAviso);
    return zona;
  }

  async function adjuntar(ticket, archivo, selector, zonaDeAviso) {
    const rechazo = rechazoDeCaptura(archivo);
    if (rechazo) {
      pintarAviso(zonaDeAviso, avisoDeRechazo(rechazo));
      return;
    }
    if (subiendo) {
      return;
    }
    subiendo = true;
    limpiarAviso(zonaDeAviso);
    selector.ocupado(true, TEXTOS_SOPORTE.subiendoCaptura);
    try {
      const captura = await cliente.subirCaptura(ticket.ticketId, archivo);
      alAdjuntar(ticket, captura);
    } catch (error) {
      selector.ocupado(false);
      pintarAviso(zonaDeAviso, avisoDeCaptura(error));
    } finally {
      terminarSubida();
    }
  }

  function terminarSubida() {
    subiendo = false;
  }

  // La solicitud se repinta con su captura nueva, sin releer la lista; el
  // foco va a «Ver captura» de la que se acaba de subir.
  function alAdjuntar(ticket, captura) {
    const nuevo = reemplazarTicket({ ...ticket, capturas: [...(ticket.capturas ?? []), captura] });
    if (!nuevo) {
      cargarLista();
      return;
    }
    const zonaDeAviso = nuevo.querySelector('[data-zona="aviso-captura"]');
    pintarAviso(zonaDeAviso, {
      tono: 'exito',
      titulo: TEXTOS_SOPORTE.capturaAdjunta,
      detalle: TEXTOS_SOPORTE.capturaAdjuntaDetalle,
    });
    const ver = nuevo.querySelectorAll('[data-accion="ver-captura"]');
    ver[ver.length - 1]?.focus();
  }

  /** Cambia en la lista la solicitud con ese id; null si no la tiene. */
  function reemplazarTicket(ticket) {
    const actual = [...lista.querySelectorAll('[data-ticket-id]')].find(
      (item) => item.dataset.ticketId === ticket.ticketId,
    );
    if (!actual) {
      return null;
    }
    const nuevo = elementoDeTicket(ticket);
    actual.replaceWith(nuevo);
    return nuevo;
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
    const captura = capturaElegida;
    limpiarAviso(zonaAviso);
    conCarga(botonEnviar, true, TEXTOS_SOPORTE.enviando);
    try {
      const ticket = await cliente.abrirTicket({
        categoria: categoria.control.value,
        asunto: textoAsunto,
        mensaje: textoMensaje,
      });
      await alEnviar(ticket, captura);
    } catch (error) {
      pintarAviso(zonaAviso, avisoDeError(error));
    } finally {
      terminarEnvio();
    }
  }

  // Fuera de `enviar` a propósito: lo que se toca después de un `await` vive
  // en funciones aparte (regla require-atomic-updates de ESLint).
  async function alEnviar(ticket, captura) {
    asunto.control.value = '';
    mensaje.control.value = '';
    pintarAviso(
      zonaAviso,
      await conLaCaptura(ticket, captura, {
        tono: 'exito',
        titulo: TEXTOS_SOPORTE.enviada,
        detalle: TEXTOS_SOPORTE.enviadaDetalle,
      }),
    );
    cargarLista();
  }

  // 1.14.0 — la solicitud ya está abierta. Con una captura elegida, se le
  // adjunta; si no entra, se dice por qué y dónde adjuntarla después, sin dar
  // la solicitud por fallida.
  async function conLaCaptura(ticket, captura, exito) {
    if (!captura || !ticket?.ticketId) {
      return exito;
    }
    quitarCapturaElegida();
    try {
      await cliente.subirCaptura(ticket.ticketId, captura);
      return { ...exito, detalle: `${TEXTOS_SOPORTE.conCaptura} ${exito.detalle}` };
    } catch (error) {
      return {
        tono: 'advertencia',
        titulo: TEXTOS_SOPORTE.enviadaSinCaptura,
        detalle: TEXTOS_SOPORTE.enviadaSinCapturaDetalle(avisoDeCaptura(error).titulo),
      };
    }
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
  // 1.14.0 — la carga de «Tus solicitudes» al abrir, para enfocar después
  // el sitio donde se adjunta una captura.
  let cargaDeLaLista = Promise.resolve();

  /**
   * @param {{conConversacion?: boolean, vista?: string|null}} [opciones]
   *   `conConversacion`: el asistente no entendió algo o no le sirvió, así
   *   que se ofrece crear la solicitud con la conversación (1.8.0)
   */
  function abrir({ conConversacion: ofrecer = false, vista = null } = {}) {
    conConversacion = Boolean(ofrecer);
    vistaDeApertura = vista;
    limpiarAviso(zonaAviso);
    imagenes.liberar();
    vaciar(contenido);
    pintarSolicitudes({ visitante: !sesion()?.autenticado });
    panel.hidden = false;
  }

  // Lo principal del panel: «Crear solicitud con esta conversación» si está
  // y, si no, el formulario. La nota del visitante va antes, pero sus
  // enlaces son una salida secundaria y no se llevan el foco.
  function enfocar() {
    const destino =
      panel.querySelector('[data-accion="crear-solicitud-desde-conversacion"]') ??
      panel.querySelector('select') ??
      botonVolver;
    destino.focus();
  }

  // 1.14.0 — desde el clip de la ventana: el foco va a donde se adjunta, la
  // solicitud abierta que aún tiene sitio o, si no la hay, el formulario.
  async function enfocarCaptura() {
    await cargaDeLaLista;
    const destino =
      lista.querySelector('[data-accion="elegir-captura"]') ?? selectorDelFormulario.entrada;
    destino.focus();
  }

  // ---------------------------------------------------- 7.4.9, en vivo

  /** Si «Tus solicitudes» está a la vista (panel abierto). */
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
    if (!reemplazarTicket(ticket)) {
      cargarLista();
    }
  }

  /** Relee la lista si está a la vista (al volver el canal tras una caída). */
  function recargarLista() {
    if (listaALaVista()) {
      cargarLista();
    }
  }

  return { elemento: panel, abrir, enfocar, enfocarCaptura, actualizarTicket, recargarLista };
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
