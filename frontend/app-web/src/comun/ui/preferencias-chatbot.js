/**
 * «Preferencias» dentro de la ventana del asistente (7.4.5, 7.4.6).
 *
 * El jugador elige en qué idioma le responde el asistente y con cuánto
 * detalle. Se guardan en el servidor (`PUT /chat/preferencias`): las de un
 * jugador con sesión lo siguen a otros dispositivos; las de un visitante duran
 * lo que su visita. Se aplican desde la siguiente pregunta.
 *
 * 1.15.0 (7.4.5 «adaptar el nivel de detalle según la experiencia»): el nivel
 * «Automático» es el de quien nunca eligió. El servidor lo traduce con lo que
 * esa persona ya le preguntó al asistente (D-CHA-13) y lo dice en
 * `nivelDetalleEfectivo`; aquí se cuenta con qué nivel se le responde hoy.
 * «Restablecer» (RF-CHA-010, flujo alternativo «restablecer sus preferencias
 * conversacionales») las devuelve a las de quien nunca eligió.
 *
 * Los errores se deciden por el `motivo` y el estado, nunca por el texto del
 * servidor.
 *
 * @module comun/ui/preferencias-chatbot
 */

import { limpiarAviso, pintarAviso } from './aviso.js';
import { boton, conCarga } from './boton.js';
import { campo } from './campo.js';
import { h } from './dom.js';

/** Los idiomas del contrato (`PreferenciasDelChat.idioma`), con el nombre que lee el jugador. */
export const IDIOMAS = Object.freeze([
  { valor: 'AUTOMATICO', texto: 'Automático: el de mi pregunta' },
  { valor: 'ES', texto: 'Español' },
  { valor: 'EN', texto: 'Inglés' },
]);

/** Los niveles del contrato (`PreferenciasDelChat.nivelDetalle`). */
export const NIVELES = Object.freeze([
  { valor: 'AUTOMATICO', texto: 'Automático: según tu experiencia con el asistente' },
  { valor: 'BREVE', texto: 'Breve: solo lo esencial' },
  { valor: 'NORMAL', texto: 'Normal' },
  { valor: 'DETALLADO', texto: 'Detallado: con temas relacionados' },
]);

/** Las que tiene quien nunca las cambió (1.15.0: también el nivel, automático). */
export const POR_DEFECTO = Object.freeze({ idioma: 'AUTOMATICO', nivelDetalle: 'AUTOMATICO' });

/**
 * 1.15.0 — con el nivel automático, cómo se le responde hoy, por el
 * `nivelDetalleEfectivo` que manda el servidor (D-CHA-13).
 */
export const NIVEL_EFECTIVO = Object.freeze({
  DETALLADO:
    'Ahora te respondo con todo el detalle y los temas relacionados, porque estás empezando.',
  NORMAL: 'Ahora te respondo con la respuesta completa.',
  BREVE: 'Ya conoces el asistente: te respondo con lo esencial y «Más detalle» a un clic.',
});

/** Todo lo que lee el usuario, en un solo sitio. */
export const TEXTOS_PREFERENCIAS = Object.freeze({
  titulo: 'Preferencias',
  intro: 'Elige cómo quieres que te responda el asistente.',
  volver: 'Volver al chat',
  idioma: 'Idioma de las respuestas',
  pistaIdioma: 'Si un tema no está en inglés, te respondo en español.',
  nivel: 'Nivel de detalle',
  pistaNivel:
    'Automático cambia según lo que ya me preguntaste. Las guías paso a paso siempre muestran todos sus pasos.',
  guardar: 'Guardar preferencias',
  guardando: 'Guardando…',
  restablecer: 'Restablecer',
  restableciendo: 'Restableciendo…',
  guardadas: 'Listo: guardamos tus preferencias.',
  guardadasJugador: 'Se aplican desde tu próxima pregunta, también en tus otros dispositivos.',
  guardadasVisitante: 'Se aplican desde tu próxima pregunta, mientras dure tu visita.',
  errorAlCargar: 'No pudimos cargar tus preferencias.',
  errorAlCargarDetalle: 'Puedes elegirlas igual y guardarlas.',
  noDisponible: 'El asistente no está disponible en este momento.',
  noDisponibleDetalle: 'Inténtalo de nuevo en unos minutos.',
  muchas: 'Hiciste muchos cambios seguidos.',
  muchasDetalle: 'Espera un momento y vuelve a intentarlo.',
  error: 'No se pudieron guardar tus preferencias.',
  errorDetalle: 'Inténtalo de nuevo.',
});

/**
 * Aviso para un error al guardar. Por el `motivo`, luego por el estado.
 *
 * @param {{estado?: number, noDisponible?: boolean, problema?: {motivo?: string}|null}} error
 * @returns {{tono: string, titulo: string, detalle: string}}
 */
export function avisoDeErrorAlGuardar(error) {
  const t = TEXTOS_PREFERENCIAS;
  if (error?.problema?.motivo === 'LIMITE_DE_FRECUENCIA' || error?.estado === 429) {
    return { tono: 'advertencia', titulo: t.muchas, detalle: t.muchasDetalle };
  }
  if (error?.noDisponible) {
    return { tono: 'error', titulo: t.noDisponible, detalle: t.noDisponibleDetalle };
  }
  return { tono: 'error', titulo: t.error, detalle: t.errorDetalle };
}

/**
 * Un valor del contrato, o el de por defecto si llega otro (un servidor más
 * nuevo con un valor que esta vista aún no conoce).
 *
 * @param {string|null|undefined} valor
 * @param {ReadonlyArray<{valor: string}>} opciones
 * @param {string} porDefecto
 * @returns {string}
 */
function valorConocido(valor, opciones, porDefecto) {
  return opciones.some((opcion) => opcion.valor === valor) ? valor : porDefecto;
}

let contador = 0;

/**
 * @param {{cliente: {preferencias: Function, guardarPreferencias: Function},
 *          sesion: () => {autenticado?: boolean}, alVolver: () => void}} opciones
 * @returns {{elemento: HTMLElement, abrir: () => void, enfocar: () => void}}
 */
export function crearPanelPreferencias({ cliente, sesion, alVolver }) {
  let guardando = false;

  const botonVolver = h('button', {
    clase: 'chatbot-ventana__accion',
    texto: TEXTOS_PREFERENCIAS.volver,
    atributos: { type: 'button' },
    datos: { accion: 'volver-al-chat' },
  });
  botonVolver.addEventListener('click', () => alVolver());

  const zonaAviso = h('div', { clase: 'chatbot-preferencias__aviso', atributos: { hidden: true } });

  const idioma = campo({
    nombre: 'idioma',
    etiqueta: TEXTOS_PREFERENCIAS.idioma,
    opciones: IDIOMAS,
    valor: POR_DEFECTO.idioma,
    pista: TEXTOS_PREFERENCIAS.pistaIdioma,
  });
  const nivel = campo({
    nombre: 'nivelDetalle',
    etiqueta: TEXTOS_PREFERENCIAS.nivel,
    opciones: NIVELES,
    valor: POR_DEFECTO.nivelDetalle,
    pista: TEXTOS_PREFERENCIAS.pistaNivel,
  });
  // 1.15.0 — con «Automático», el nivel de hoy. Solo cuenta lo guardado: si
  // se elige otro nivel sin guardar, se oculta.
  let efectivoGuardado = null;
  const efectivo = h('p', {
    clase: 'chatbot-preferencias__efectivo',
    atributos: { hidden: true },
    datos: { nivelEfectivo: '' },
  });
  contador += 1;
  efectivo.id = `chatbot-preferencias-efectivo-${contador}`;
  nivel.elemento.append(efectivo);
  nivel.control.setAttribute(
    'aria-describedby',
    [nivel.control.getAttribute('aria-describedby'), efectivo.id].filter(Boolean).join(' '),
  );
  nivel.control.addEventListener('change', () => pintarEfectivo());
  const botonGuardar = boton({
    texto: TEXTOS_PREFERENCIAS.guardar,
    tipo: 'submit',
    nombre: 'guardar-preferencias',
  });
  // RF-CHA-010: volver a las de quien nunca eligió, y guardarlas.
  const botonRestablecer = boton({
    texto: TEXTOS_PREFERENCIAS.restablecer,
    variante: 'secundario',
    nombre: 'restablecer-preferencias',
    alPulsar: () => {
      if (guardando) {
        return;
      }
      idioma.control.value = POR_DEFECTO.idioma;
      nivel.control.value = POR_DEFECTO.nivelDetalle;
      pintarEfectivo();
      guardar(botonRestablecer, TEXTOS_PREFERENCIAS.restableciendo);
    },
  });
  const formulario = h('form', {
    clase: 'chatbot-preferencias__formulario',
    atributos: { novalidate: true },
    hijos: [
      idioma.elemento,
      nivel.elemento,
      h('div', {
        clase: 'chatbot-preferencias__acciones',
        hijos: [botonGuardar, botonRestablecer],
      }),
    ],
  });
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    guardar();
  });

  const panel = h('section', {
    clase: 'chatbot-preferencias',
    atributos: { hidden: true, 'aria-label': TEXTOS_PREFERENCIAS.titulo },
    datos: { chatbotPreferencias: '' },
    hijos: [
      h('div', {
        clase: 'chatbot-preferencias__cabecera',
        hijos: [
          h('h3', { clase: 'chatbot-preferencias__titulo', texto: TEXTOS_PREFERENCIAS.titulo }),
          botonVolver,
        ],
      }),
      h('p', { clase: 'chatbot-preferencias__texto', texto: TEXTOS_PREFERENCIAS.intro }),
      zonaAviso,
      formulario,
    ],
  });

  function mostrar({ idioma: elegido, nivelDetalle, nivelDetalleEfectivo } = POR_DEFECTO) {
    idioma.control.value = valorConocido(elegido, IDIOMAS, POR_DEFECTO.idioma);
    nivel.control.value = valorConocido(nivelDetalle, NIVELES, POR_DEFECTO.nivelDetalle);
    efectivoGuardado = nivel.control.value === 'AUTOMATICO' ? (nivelDetalleEfectivo ?? null) : null;
    pintarEfectivo();
  }

  function pintarEfectivo() {
    const texto =
      nivel.control.value === 'AUTOMATICO' && Object.hasOwn(NIVEL_EFECTIVO, efectivoGuardado ?? '')
        ? NIVEL_EFECTIVO[efectivoGuardado]
        : '';
    efectivo.textContent = texto;
    efectivo.hidden = !texto;
  }

  function habilitar(activo) {
    idioma.control.disabled = !activo;
    nivel.control.disabled = !activo;
    botonGuardar.disabled = !activo;
    botonRestablecer.disabled = !activo;
  }

  async function cargar() {
    habilitar(false);
    try {
      mostrar((await cliente.preferencias()) ?? POR_DEFECTO);
    } catch {
      pintarAviso(zonaAviso, {
        tono: 'advertencia',
        titulo: TEXTOS_PREFERENCIAS.errorAlCargar,
        detalle: TEXTOS_PREFERENCIAS.errorAlCargarDetalle,
      });
    } finally {
      habilitar(true);
    }
  }

  /**
   * @param {HTMLButtonElement} [conQue] el botón que lo pidió, que muestra la espera
   * @param {string} [texto] lo que dice mientras guarda
   */
  async function guardar(conQue = botonGuardar, texto = TEXTOS_PREFERENCIAS.guardando) {
    if (guardando) {
      return;
    }
    guardando = true;
    limpiarAviso(zonaAviso);
    conCarga(conQue, true, texto);
    try {
      const guardadas = await cliente.guardarPreferencias({
        idioma: idioma.control.value,
        nivelDetalle: nivel.control.value,
      });
      alGuardar(guardadas);
    } catch (error) {
      pintarAviso(zonaAviso, avisoDeErrorAlGuardar(error));
    } finally {
      terminarGuardado(conQue);
    }
  }

  // Fuera de `guardar` a propósito: lo que se toca después de un `await` vive
  // en funciones aparte (regla require-atomic-updates de ESLint).
  function alGuardar(guardadas) {
    if (guardadas) {
      mostrar(guardadas);
    }
    pintarAviso(zonaAviso, {
      tono: 'exito',
      titulo: TEXTOS_PREFERENCIAS.guardadas,
      detalle: sesion()?.autenticado
        ? TEXTOS_PREFERENCIAS.guardadasJugador
        : TEXTOS_PREFERENCIAS.guardadasVisitante,
    });
  }

  function terminarGuardado(conQue) {
    guardando = false;
    conCarga(conQue, false);
  }

  // Se relee al abrir: pudieron cambiarse en otro dispositivo.
  function abrir() {
    limpiarAviso(zonaAviso);
    panel.hidden = false;
    cargar();
  }

  function enfocar() {
    (idioma.control.disabled ? botonVolver : idioma.control).focus();
  }

  return { elemento: panel, abrir, enfocar };
}
