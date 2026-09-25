/**
 * Un mensaje de una conversación — UXC-6.
 *
 * Lo pintan el chat general, el de la sala (RF-JUE-015) y los mensajes
 * privados: el mismo globo en los tres sitios, para que un jugador lea igual
 * una conversación esté donde esté.
 *
 * Quién habla no se dice solo con el color (WCAG 1.4.1). Cada mensaje lleva
 * tres señales a la vez:
 *
 *   - **palabra**: «Tú», el apodo del otro jugador o «Sistema»;
 *   - **posición**: lo tuyo a la derecha, lo de los demás a la izquierda y el
 *     sistema al centro;
 *   - **forma**: el otro jugador lleva su inicial, el sistema su icono y un
 *     borde discontinuo.
 *
 * El color refuerza, no informa.
 *
 * @module comun/ui/conversacion/mensaje
 */

import { h } from '../dom.js';
import { icono } from '../icono.js';

const LOCALIZACION = 'es-CO';

/** Quién escribe un mensaje. */
export const QUIEN = Object.freeze({
  YO: 'yo',
  OTRO: 'otro',
  SISTEMA: 'sistema',
});

/**
 * Estado de entrega de un mensaje tuyo: lo que ya confirmó el servidor y lo
 * que está en el aire. Se dice con palabras; el icono acompaña.
 */
export const ENTREGA = Object.freeze({
  ENVIANDO: { texto: 'Enviando…', clase: 'enviando', icono: 'reloj' },
  ENVIADO: { texto: 'Enviado', clase: 'enviado', icono: 'check' },
  LEIDO: { texto: 'Leído', clase: 'leido', icono: 'ojo' },
  FALLIDO: { texto: 'No se envió', clase: 'fallido', icono: 'alerta' },
});

/**
 * @param {{tipo?: string, autor?: {id?: string|null}|null}} mensaje
 * @param {string|null} miId `uid` de la sesión (`usuarioIdDeSesion()`)
 * @returns {'yo'|'otro'|'sistema'}
 */
export function quienEscribe(mensaje, miId) {
  if (String(mensaje?.tipo ?? '').startsWith('sistema')) {
    return QUIEN.SISTEMA;
  }
  const autor = mensaje?.autor?.id;
  if (miId && autor && String(autor) === String(miId)) {
    return QUIEN.YO;
  }
  return QUIEN.OTRO;
}

/**
 * @param {unknown} valor
 * @returns {Date|null}
 */
function aFecha(valor) {
  if (valor === null || valor === undefined || valor === '') {
    return null;
  }
  const fecha = valor instanceof Date ? valor : new Date(valor);
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

/** @param {Date} fecha */
function inicioDelDia(fecha) {
  return new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate()).getTime();
}

const UN_DIA_MS = 86_400_000;

/**
 * Cuántos días de calendario separan dos momentos (0 = el mismo día).
 *
 * @param {Date} fecha
 * @param {Date} ahora
 */
function diasAtras(fecha, ahora) {
  return Math.round((inicioDelDia(ahora) - inicioDelDia(fecha)) / UN_DIA_MS);
}

/**
 * La hora de un mensaje: «10:02». El día lo dice el separador de la lista.
 *
 * @param {string|Date} valor
 * @returns {string}
 */
export function horaDeMensaje(valor) {
  const fecha = aFecha(valor);
  if (!fecha) {
    return '';
  }
  return fecha.toLocaleTimeString(LOCALIZACION, { hour: '2-digit', minute: '2-digit' });
}

/**
 * El momento entero, para el `title` y los lectores de pantalla.
 *
 * @param {string|Date} valor
 * @returns {string}
 */
export function momentoCompleto(valor) {
  const fecha = aFecha(valor);
  if (!fecha) {
    return '';
  }
  return fecha.toLocaleString(LOCALIZACION, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * El separador de día de una conversación: «Hoy», «Ayer» o la fecha.
 *
 * @param {string|Date} valor
 * @param {Date} [ahora]
 * @returns {string}
 */
export function etiquetaDeDia(valor, ahora = new Date()) {
  const fecha = aFecha(valor);
  if (!fecha) {
    return '';
  }
  const dias = diasAtras(fecha, ahora);
  if (dias === 0) {
    return 'Hoy';
  }
  if (dias === 1) {
    return 'Ayer';
  }
  const texto = fecha.toLocaleDateString(LOCALIZACION, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    ...(fecha.getFullYear() === ahora.getFullYear() ? {} : { year: 'numeric' }),
  });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/**
 * Cuándo fue lo último de una conversación, en la lista: la hora si fue hoy,
 * «Ayer», el día de la semana esta semana y la fecha corta si es más viejo.
 *
 * @param {string|Date} valor
 * @param {Date} [ahora]
 * @returns {string}
 */
export function cuandoFue(valor, ahora = new Date()) {
  const fecha = aFecha(valor);
  if (!fecha) {
    return '';
  }
  const dias = diasAtras(fecha, ahora);
  if (dias <= 0) {
    return horaDeMensaje(fecha);
  }
  if (dias === 1) {
    return 'Ayer';
  }
  if (dias < 7) {
    return fecha.toLocaleDateString(LOCALIZACION, { weekday: 'short' }).replace('.', '');
  }
  return fecha
    .toLocaleDateString(LOCALIZACION, { day: 'numeric', month: 'short' })
    .replace('.', '');
}

/**
 * La inicial de un apodo, para el círculo del otro jugador.
 *
 * @param {string|null|undefined} apodo
 * @returns {string}
 */
export function inicialDe(apodo) {
  const limpio = String(apodo ?? '').trim();
  return limpio ? limpio.charAt(0).toLocaleUpperCase(LOCALIZACION) : '?';
}

/**
 * El círculo con la inicial. Es adorno: el apodo va escrito al lado.
 *
 * @param {string|null|undefined} apodo
 * @param {string} [clase]
 * @returns {HTMLElement}
 */
export function inicialDeJugador(apodo, clase = 'mensaje__inicial') {
  return h('span', { clase, texto: inicialDe(apodo), atributos: { 'aria-hidden': 'true' } });
}

/**
 * @param {'yo'|'otro'|'sistema'} quien
 * @param {{autor?: {apodo?: string}|null}} mensaje
 */
function cabeceraDe(quien, mensaje, cuando) {
  const hora = cuando
    ? h('time', {
        clase: 'mensaje__hora',
        texto: horaDeMensaje(cuando),
        atributos: {
          datetime: aFecha(cuando)?.toISOString() ?? '',
          title: momentoCompleto(cuando),
        },
      })
    : null;
  if (quien === QUIEN.SISTEMA) {
    return h('p', {
      clase: 'mensaje__cabecera',
      hijos: [
        icono('campana', { etiqueta: null, clase: 'icono mensaje__icono' }),
        h('span', { clase: 'mensaje__autor', texto: 'Sistema' }),
        hora,
      ],
    });
  }
  if (quien === QUIEN.YO) {
    return h('p', {
      clase: 'mensaje__cabecera',
      hijos: [h('span', { clase: 'mensaje__autor', texto: 'Tú' }), hora],
    });
  }
  const apodo = mensaje.autor?.apodo || 'Jugador';
  return h('p', {
    clase: 'mensaje__cabecera',
    hijos: [inicialDeJugador(apodo), h('span', { clase: 'mensaje__autor', texto: apodo }), hora],
  });
}

/**
 * La línea de entrega de un mensaje tuyo.
 *
 * @param {keyof typeof ENTREGA} clave
 * @param {{alReintentar?: () => void}} [opciones]
 * @returns {HTMLElement}
 */
export function lineaDeEntrega(clave, { alReintentar } = {}) {
  const entrega = ENTREGA[clave] ?? ENTREGA.ENVIADO;
  const linea = h('p', {
    clase: `mensaje__entrega mensaje__entrega--${entrega.clase}`,
    datos: { entrega: clave },
    hijos: [
      icono(entrega.icono, { etiqueta: null, clase: 'icono mensaje__icono' }),
      h('span', { texto: entrega.texto }),
    ],
  });
  if (clave === 'FALLIDO' && typeof alReintentar === 'function') {
    const boton = h('button', {
      clase: 'boton boton--contorno boton--pequeno mensaje__reintentar',
      texto: 'Reintentar',
      atributos: { type: 'button' },
      datos: { accion: 'reintentar-mensaje' },
    });
    boton.addEventListener('click', () => alReintentar());
    linea.append(boton);
  }
  return linea;
}

/**
 * El globo de un mensaje.
 *
 * Acepta la forma de `MensajeDeChat` del contrato del chat
 * (`contracts/websocket/salas-partidas.yaml`: `id`, `tipo`, `autor`, `texto`,
 * `logro`, `enviadoEn`) y la de un mensaje del sistema
 * (`{tipo: 'sistema', texto, tono?}`), que no viene de ningún servicio: es la
 * propia vista contando algo que pasó (la conexión se cortó, un mensaje no
 * salió).
 *
 * @param {{id?: string, tipo?: string, autor?: {id?: string, apodo?: string}|null,
 *   texto: string, enviadoEn?: string, tono?: 'info'|'advertencia'|'exito',
 *   logro?: {mision: string, titulo: string}|null}} mensaje
 * @param {{miId?: string|null, entrega?: keyof typeof ENTREGA|null,
 *   alReintentar?: () => void}} [opciones]
 * @returns {HTMLLIElement}
 */
export function burbujaDeMensaje(mensaje, { miId = null, entrega = null, alReintentar } = {}) {
  const quien = quienEscribe(mensaje, miId);
  const clases = [`mensaje mensaje--${quien}`];
  if (quien === QUIEN.SISTEMA && mensaje.tono) {
    clases.push(`mensaje--${mensaje.tono}`);
  }
  const item = h('li', {
    clase: clases.join(' '),
    datos: {
      quien,
      tipo: mensaje.tipo ?? 'chat.mensaje',
      ...(mensaje.id ? { id: mensaje.id } : {}),
    },
  });

  item.append(cabeceraDe(quien, mensaje, mensaje.enviadoEn ?? null));

  const globo = h('div', { clase: 'mensaje__globo' });
  globo.append(h('p', { clase: 'mensaje__texto', texto: mensaje.texto }));
  if (mensaje.logro?.titulo) {
    // CA-02 de HU-JUE-015: el logro de misión compartido va dentro del mismo
    // globo, con su trofeo y en palabras.
    globo.append(
      h('p', {
        clase: 'mensaje__logro',
        hijos: [
          icono('trofeo', { etiqueta: null, clase: 'icono mensaje__icono' }),
          h('span', {
            texto: mensaje.logro.mision
              ? `Logro compartido: ${mensaje.logro.titulo} (misión ${mensaje.logro.mision})`
              : `Logro compartido: ${mensaje.logro.titulo}`,
          }),
        ],
      }),
    );
  }
  item.append(globo);

  if (quien === QUIEN.YO && entrega) {
    item.append(lineaDeEntrega(entrega, { alReintentar }));
  }
  return item;
}

/**
 * Un mensaje del sistema: la vista contando algo que pasó en la conversación.
 *
 * @param {string} texto
 * @param {{tono?: 'info'|'advertencia'|'exito', cuando?: Date|string|null}} [opciones]
 * @returns {HTMLLIElement}
 */
export function mensajeDelSistema(texto, { tono = 'info', cuando = new Date() } = {}) {
  return burbujaDeMensaje({
    tipo: 'sistema',
    texto,
    tono,
    enviadoEn: cuando instanceof Date ? cuando.toISOString() : cuando,
  });
}
