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
 * - calificar cada respuesta del bot (útil / no útil, con comentario opcional);
 * - UXC-9 (§7.4, RNF-DIS-002): minimizarla a su barra de título sin perder la
 *   conversación, y cambiarle el tamaño arrastrando la esquina o con las
 *   flechas del teclado sobre ella. El tamaño se recuerda en esta sesión.
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

  const cabecera = h('header', {
    clase: 'chatbot-ventana__cabecera',
    hijos: [
      titulo,
      h('div', {
        clase: 'chatbot-ventana__acciones',
        hijos: [botonBorrar, botonMinimizar, botonCerrar],
      }),
      estado,
    ],
  });

  // UXC-9 — lista con región viva, sin `role="log"`: ese rol le quitaba al
  // <ol> su semántica de lista y cada mensaje (<li>) quedaba huérfano (axe,
  // «listitem», grave). `aria-live` sigue anunciando lo que llega.
  const registro = h('ol', {
    clase: 'chatbot-ventana__registro',
    atributos: { 'aria-live': 'polite', 'aria-label': TEXTOS.registro },
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
    hijos: [asa, cabecera, registro, zonaAviso, formulario],
  });
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
  // cierra): en la caja de texto si se puede escribir; si el asistente no
  // está, en la primera acción del aviso; y si no, en «Cerrar».
  function enfocarDentro() {
    if (ventana.hidden) {
      return;
    }
    if (minimizada()) {
      botonMinimizar.focus();
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
