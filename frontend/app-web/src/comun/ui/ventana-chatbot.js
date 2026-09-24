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
 * - el historial de la conversación de esta sesión, y borrarlo;
 * - adjuntar la URL de una captura ya subida (el servicio solo guarda la URL);
 * - calificar cada respuesta del bot (útil / no útil, con comentario opcional).
 *
 * Si el servicio no responde, lo dice y ofrece el chat general: CA-03 de
 * HU-CHA-001.
 *
 * Todo el texto del servidor entra con `textContent` (`h()`), nunca como HTML.
 *
 * @module comun/ui/ventana-chatbot
 */

import { crearClienteChatbot } from '../cliente-chatbot.js';
import { leerSesion } from '../sesion.js';
import { conCarga } from './boton.js';
import { campo } from './campo.js';
import { confirmar } from './dialogo.js';
import { h, vaciar } from './dom.js';
import { estadoDeCarga } from './estado-vista.js';
import { fechaHora } from './formato.js';

/** Destino del chat general de jugadores, relativo a `src/comun/ui/`. */
const CHAT_GENERAL = '../../plataforma/salas-partidas/chat.html';

/** Límites del contrato (`EnviarMensaje`, `CalificarRespuesta`). */
export const LIMITES = Object.freeze({ mensaje: 4000, adjunto: 500, comentario: 1000 });

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
  errorAlBorrar: 'No se pudo borrar la conversación. Inténtalo de nuevo.',
  escribiendo: 'El asistente está escribiendo…',
  placeholder: 'Escribe tu pregunta…',
  etiquetaMensaje: 'Tu pregunta',
  enviar: 'Enviar',
  enviando: 'Enviando…',
  adjuntar: 'Adjuntar captura',
  quitarAdjunto: 'Quitar captura',
  etiquetaAdjunto: 'URL de la captura',
  pistaAdjunto: 'Pega el enlace a una imagen que ya subiste.',
  adjuntoInvalido: 'Escribe un enlace que empiece por http:// o https://.',
  verCaptura: 'Ver captura adjunta',
  borrar: 'Borrar conversación',
  confirmarBorrarTitulo: '¿Borrar la conversación?',
  confirmarBorrarMensaje:
    'Se borrarán todos los mensajes de esta conversación. No se puede deshacer.',
  confirmarBorrarBoton: 'Borrar',
  cerrar: 'Cerrar el asistente',
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
});

let contador = 0;

/**
 * Crea la ventana (oculta) y la cuelga de `raiz`.
 *
 * @param {{cliente?: ReturnType<typeof crearClienteChatbot>,
 *          sesion?: () => {autenticado: boolean, apodo: string},
 *          raiz?: HTMLElement, chatGeneral?: string,
 *          confirmarBorrado?: (opciones: object) => Promise<boolean>}} [opciones]
 * @returns {{elemento: HTMLElement, abrir: (desde?: HTMLElement|null) => void,
 *            cerrar: () => void, alternar: (desde?: HTMLElement|null) => void,
 *            abierta: () => boolean}}
 */
export function crearVentanaChatbot({
  cliente = crearClienteChatbot(),
  sesion = () => leerSesion(),
  raiz = document.body,
  chatGeneral = new URL(CHAT_GENERAL, import.meta.url).href,
  confirmarBorrado = confirmar,
} = {}) {
  contador += 1;
  const idTitulo = `chatbot-ventana-titulo-${contador}`;

  let cargada = false;
  let enviando = false;
  let devolverFocoA = null;

  // ------------------------------------------------------------ estructura

  const titulo = h('h2', { clase: 'chatbot-ventana__titulo', texto: TEXTOS.titulo });
  titulo.id = idTitulo;
  const estado = h('p', { clase: 'chatbot-ventana__estado' });

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

  const cabecera = h('header', {
    clase: 'chatbot-ventana__cabecera',
    hijos: [
      titulo,
      h('div', { clase: 'chatbot-ventana__acciones', hijos: [botonBorrar, botonCerrar] }),
      estado,
    ],
  });

  const registro = h('ol', {
    clase: 'chatbot-ventana__registro',
    atributos: { role: 'log', 'aria-live': 'polite', 'aria-label': TEXTOS.registro },
  });
  const zonaAviso = h('div', { clase: 'chatbot-ventana__aviso', atributos: { hidden: true } });

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
  const adjunto = campo({
    nombre: 'adjuntoUrl',
    etiqueta: TEXTOS.etiquetaAdjunto,
    tipo: 'url',
    pista: TEXTOS.pistaAdjunto,
    atributos: { maxlength: LIMITES.adjunto, inputmode: 'url' },
  });
  adjunto.elemento.classList.add('chatbot-ventana__captura');
  adjunto.elemento.hidden = true;

  const botonAdjuntar = h('button', {
    clase: 'chatbot-ventana__accion',
    texto: TEXTOS.adjuntar,
    atributos: { type: 'button', 'aria-expanded': 'false' },
    datos: { accion: 'adjuntar-captura' },
  });
  const botonEnviar = h('button', {
    clase: 'boton boton--primario',
    texto: TEXTOS.enviar,
    atributos: { type: 'submit' },
  });

  const formulario = h('form', {
    clase: 'chatbot-ventana__formulario',
    atributos: { novalidate: true },
    hijos: [
      adjunto.elemento,
      h('div', { clase: 'chatbot-ventana__fila', hijos: [entrada, botonEnviar] }),
      h('div', { clase: 'chatbot-ventana__herramientas', hijos: [botonAdjuntar] }),
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
    hijos: [cabecera, registro, zonaAviso, formulario],
  });
  ventana.id = `chatbot-ventana-${contador}`;
  raiz.append(ventana);

  // ------------------------------------------------------------- mensajes

  function pintarBienvenida() {
    registro.append(
      h('li', {
        clase: 'chatbot-ventana__mensaje chatbot-ventana__mensaje--bot',
        datos: { bienvenida: '' },
        hijos: [h('p', { clase: 'chatbot-ventana__texto', texto: TEXTOS.bienvenida })],
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
    const esBot = mensaje.remitente === 'BOT';
    const burbuja = h('li', {
      clase: `chatbot-ventana__mensaje chatbot-ventana__mensaje--${esBot ? 'bot' : 'usuario'}`,
      datos: mensaje.id ? { mensajeId: mensaje.id } : {},
      hijos: [h('p', { clase: 'chatbot-ventana__texto', texto: mensaje.contenido })],
    });

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
    registro.append(burbuja);
    registro.scrollTop = registro.scrollHeight;
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
        nota.textContent = error?.noDisponible
          ? TEXTOS.noDisponibleTitulo
          : TEXTOS.errorAlCalificar;
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

  function mostrarAviso(error, { alReintentar = null } = {}) {
    vaciar(zonaAviso);
    zonaAviso.hidden = false;

    if (error?.noDisponible) {
      zonaAviso.append(
        h('div', {
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
        }),
      );
      return;
    }

    zonaAviso.append(
      h('div', {
        clase: 'aviso aviso--error',
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
      }),
    );
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
    botonEnviar.disabled = !activo;
    botonAdjuntar.disabled = !activo;
    botonBorrar.disabled = !activo;
  }

  // ------------------------------------------------------------- historial

  async function cargarHistorial() {
    limpiarAviso();
    vaciar(registro);
    const cargando = estadoDeCarga({ filas: 2, etiqueta: 'Cargando la conversación…' });
    registro.append(h('li', { clase: 'chatbot-ventana__cargando', hijos: [cargando] }));
    habilitarFormulario(false);

    try {
      const historial = await cliente.obtenerHistorial();
      vaciar(registro);
      if (historial.length === 0) {
        pintarBienvenida();
      }
      for (const mensaje of historial) {
        pintarMensaje(mensaje);
      }
      cargada = true;
      habilitarFormulario(true);
    } catch (error) {
      vaciar(registro);
      mostrarAviso(error, { alReintentar: cargarHistorial });
      enfocarDentro();
    }
  }

  // ----------------------------------------------------------------- envío

  async function enviarMensaje() {
    if (enviando) {
      return;
    }
    const contenido = entrada.value.trim();
    if (!contenido) {
      entrada.focus();
      return;
    }

    let adjuntoUrl = null;
    if (!adjunto.elemento.hidden && adjunto.control.value.trim()) {
      adjuntoUrl = urlPermitida(adjunto.control.value.trim());
      if (!adjuntoUrl) {
        adjunto.marcarError(TEXTOS.adjuntoInvalido);
        adjunto.control.focus();
        return;
      }
    }
    adjunto.marcarError(null);

    enviando = true;
    limpiarAviso();
    conCarga(botonEnviar, true, TEXTOS.enviando);
    const escribiendo = h('li', {
      clase: 'chatbot-ventana__escribiendo',
      texto: TEXTOS.escribiendo,
    });
    registro.append(escribiendo);
    registro.scrollTop = registro.scrollHeight;

    try {
      const respuesta = await cliente.enviarMensaje(contenido, adjuntoUrl);
      escribiendo.remove();
      quitarBienvenida();
      pintarMensaje({
        remitente: 'USUARIO',
        contenido,
        adjuntoUrl,
        fechaEnvio: new Date().toISOString(),
      });
      pintarMensaje(respuesta);
      vaciarFormulario();
    } catch (error) {
      escribiendo.remove();
      mostrarAviso(error);
    } finally {
      terminarEnvio();
    }
  }

  // Fuera de enviarMensaje a propósito: lo que se toca después de un `await`
  // vive en funciones aparte (regla require-atomic-updates de ESLint).
  function vaciarFormulario() {
    entrada.value = '';
    mostrarAdjunto(false);
  }

  function terminarEnvio() {
    enviando = false;
    conCarga(botonEnviar, false);
    entrada.focus();
  }

  function mostrarAdjunto(visible) {
    adjunto.elemento.hidden = !visible;
    botonAdjuntar.setAttribute('aria-expanded', String(visible));
    botonAdjuntar.textContent = visible ? TEXTOS.quitarAdjunto : TEXTOS.adjuntar;
    if (!visible) {
      adjunto.control.value = '';
      adjunto.marcarError(null);
    }
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
  botonAdjuntar.addEventListener('click', () => {
    mostrarAdjunto(adjunto.elemento.hidden);
    if (!adjunto.elemento.hidden) {
      adjunto.control.focus();
    }
  });
  botonBorrar.addEventListener('click', borrarConversacion);
  botonCerrar.addEventListener('click', () => cerrar());
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
    ventana.hidden = false;
    if (!cargada) {
      cargarHistorial();
    }
    enfocarDentro();
  }

  // El foco tiene que quedar DENTRO de la ventana (si no, Escape no la
  // cierra): en la caja de texto si se puede escribir; si el asistente no
  // está, en la primera acción del aviso; y si no, en «Cerrar».
  function enfocarDentro() {
    if (ventana.hidden) {
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
  };
}

/**
 * Texto de un error que no es "no disponible". Se decide por el estado, nunca
 * por el texto del servidor (MAPEO-ERRORES §2).
 *
 * @param {string|{estado?: number}|null} error una cadena ya es el texto final
 * @param {Function|null} alReintentar presente cuando falló la carga
 * @returns {string}
 */
function textoDeError(error, alReintentar) {
  if (typeof error === 'string') {
    return error;
  }
  if (error?.estado === 400) {
    return TEXTOS.mensajeInvalido;
  }
  return alReintentar ? TEXTOS.errorAlCargar : TEXTOS.errorAlEnviar;
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
