/**
 * Sanciones en la interfaz — SanctionCountdown y los hechos de la línea de
 * tiempo, UXC-7.
 *
 * Todo sale de la forma `Sancion` de `moderacion-sanciones-admin.yaml`. Qué
 * sanción restringe hoy lo dice el servidor con `vigente` («no revertida y,
 * si es suspensión, no vencida»): aquí no se recalcula ninguna regla, solo se
 * cuenta el tiempo que falta para la fecha que el servidor ya fijó.
 *
 * @module comun/ui/sancion
 */

import { h } from './dom.js';
import { icono } from './icono.js';
import { cuentaAtrasRotulada } from './cuenta-atras.js';

const LOCALIZACION = 'es-CO';

/** Nombre de cada tipo, con su tilde. */
export const NOMBRE_DE_SANCION = Object.freeze({
  ADVERTENCIA: 'Advertencia',
  SUSPENSION: 'Suspensión',
  BANEO: 'Baneo definitivo',
});

/** Quién la emitió, dicho como se dice: el rol, no su constante. */
const QUIEN_EMITE = Object.freeze({
  MODERADOR: 'moderación',
  ADMINISTRADOR: 'administración',
  SUPER_ADMINISTRADOR: 'administración',
  SISTEMA: 'el sistema',
});

const TONO_DE_TIPO = Object.freeze({
  ADVERTENCIA: 'info',
  SUSPENSION: 'advertencia',
  BANEO: 'error',
});

/**
 * @param {string|null|undefined} valor
 * @returns {string}
 */
function fechaLarga(valor) {
  const fecha = new Date(valor ?? Number.NaN);
  if (Number.isNaN(fecha.getTime())) {
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
 * La sanción que hoy impide jugar, según `vigente` del servidor. Si hubiera
 * más de una, manda el baneo y, entre suspensiones, la que dura más.
 *
 * @param {object[]} sanciones
 * @returns {object|null}
 */
export function sancionQueRestringe(sanciones = []) {
  const restringen = (sanciones ?? []).filter(
    (s) => s?.vigente === true && (s.tipo === 'SUSPENSION' || s.tipo === 'BANEO'),
  );
  if (restringen.length === 0) {
    return null;
  }
  const baneo = restringen.find((s) => s.tipo === 'BANEO');
  if (baneo) {
    return baneo;
  }
  return restringen.sort(
    (a, b) => new Date(b.vigenteHasta ?? 0).getTime() - new Date(a.vigenteHasta ?? 0).getTime(),
  )[0];
}

/**
 * Los hechos fechados del historial, para `lineaDeTiempo()`.
 *
 * @param {object[]} sanciones
 * @param {{ahora?: number}} [opciones]
 * @returns {import('./linea-de-tiempo.js').HechoDeLaLinea[]}
 */
export function hechosDeSanciones(sanciones = [], { ahora = Date.now() } = {}) {
  const hechos = [];
  for (const s of sanciones ?? []) {
    const nombre = NOMBRE_DE_SANCION[s.tipo] ?? s.tipo;
    hechos.push({
      cuando: s.emitidaEn,
      // El hecho de la emisión lleva el id de la sanción: la consola (y sus
      // pruebas de extremo a extremo) la encuentran por `data-sancion-id`,
      // como cuando el historial eran tarjetas.
      datos: s.id ? { sancionId: String(s.id) } : null,
      titulo: `${nombre} emitida`,
      detalle: s.politica ? `Motivo: ${s.motivo} · Política: ${s.politica}` : `Motivo: ${s.motivo}`,
      actor: s.rolEmisor ? `Por ${QUIEN_EMITE[s.rolEmisor] ?? s.rolEmisor.toLowerCase()}` : null,
      tono: TONO_DE_TIPO[s.tipo] ?? 'neutro',
    });
    if (s.revertidaEn) {
      hechos.push({
        cuando: s.revertidaEn,
        titulo: `${nombre} revertida`,
        detalle: s.motivoReversion ?? null,
        tono: 'exito',
      });
      continue;
    }
    if (s.tipo === 'SUSPENSION' && s.vigenteHasta) {
      const futura = new Date(s.vigenteHasta).getTime() > ahora;
      hechos.push({
        cuando: s.vigenteHasta,
        titulo: futura ? 'La suspensión termina' : 'La suspensión terminó',
        tono: 'neutro',
        futuro: futura,
        extra: futura ? cuentaAtrasRotulada(s.vigenteHasta, { clase: 't-meta' }) : null,
      });
    }
  }
  return hechos;
}

/** Lo que cambia al hablarle al jugador («tu cuenta») o de él («la cuenta»). */
const PERSONAS = Object.freeze({
  segunda: {
    enRegla: 'Tu cuenta está en regla',
    ninguna: 'Ninguna sanción te impide jugar.',
    advertencias: (n) =>
      `Tienes ${n === 1 ? 'una advertencia' : `${n} advertencias`}: no te impide jugar.`,
  },
  tercera: {
    enRegla: 'La cuenta está en regla',
    ninguna: 'Ninguna sanción le impide jugar.',
    advertencias: (n) =>
      `Tiene ${n === 1 ? 'una advertencia' : `${n} advertencias`}: no le impide jugar.`,
  },
});

/**
 * SanctionCountdown: el estado de la cuenta, con la cuenta atrás de la
 * sanción que restringe (si la hay) y, si se da, dónde verla y apelarla.
 *
 * @param {object[]} sanciones el historial de la cuenta
 * @param {{hrefSanciones?: string|null, textoDelEnlace?: string|null,
 *   persona?: 'segunda'|'tercera', titulo?: string}} [opciones]
 *   `persona`: «tu cuenta» en las vistas del jugador, «la cuenta» en la consola.
 * @returns {HTMLElement}
 */
export function estadoDeCuenta(
  sanciones,
  {
    hrefSanciones = null,
    textoDelEnlace = null,
    persona = 'segunda',
    titulo = 'Estado de la cuenta',
  } = {},
) {
  const textos = PERSONAS[persona] ?? PERSONAS.segunda;
  const activa = sancionQueRestringe(sanciones);
  const advertencias = (sanciones ?? []).filter(
    (s) => s?.vigente === true && s.tipo === 'ADVERTENCIA',
  ).length;
  const enlace = (texto) =>
    hrefSanciones
      ? h('a', {
          clase: 'boton boton--secundario boton--pequeno estado-cuenta__enlace',
          texto: textoDelEnlace ?? texto,
          atributos: { href: hrefSanciones },
        })
      : null;

  if (!activa) {
    return h('section', {
      clase: 'tarjeta estado-cuenta estado-cuenta--en-regla',
      datos: { estadoCuenta: 'en-regla' },
      atributos: { 'aria-label': titulo },
      hijos: [
        icono('escudo-check', { etiqueta: null, clase: 'icono estado-cuenta__icono' }),
        h('div', {
          clase: 'estado-cuenta__cuerpo',
          hijos: [
            h('p', { clase: 'estado-cuenta__titulo', texto: textos.enRegla }),
            h('p', {
              clase: 'estado-cuenta__detalle',
              texto: advertencias > 0 ? textos.advertencias(advertencias) : textos.ninguna,
            }),
          ],
        }),
        enlace('Ver el historial'),
      ],
    });
  }

  const esBaneo = activa.tipo === 'BANEO';
  return h('section', {
    clase: `tarjeta estado-cuenta estado-cuenta--${esBaneo ? 'baneada' : 'suspendida'}`,
    datos: { estadoCuenta: esBaneo ? 'baneada' : 'suspendida' },
    atributos: { 'aria-label': titulo },
    hijos: [
      icono('candado', { etiqueta: null, clase: 'icono estado-cuenta__icono' }),
      h('div', {
        clase: 'estado-cuenta__cuerpo',
        hijos: [
          h('p', {
            clase: 'estado-cuenta__titulo',
            texto: esBaneo ? 'Cuenta baneada' : 'Suspensión activa',
          }),
          esBaneo
            ? h('p', { clase: 'estado-cuenta__detalle', texto: 'Sin fecha de fin.' })
            : h('p', {
                clase: 'estado-cuenta__detalle',
                hijos: [
                  cuentaAtrasRotulada(activa.vigenteHasta, { clase: 'estado-cuenta__cuenta' }),
                  h('span', { texto: ` · hasta el ${fechaLarga(activa.vigenteHasta)}` }),
                ],
              }),
          h('p', { clase: 'estado-cuenta__detalle', texto: `Motivo: ${activa.motivo}` }),
        ],
      }),
      enlace('Ver las sanciones y apelar'),
    ],
  });
}
