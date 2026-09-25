/**
 * Línea de tiempo — AdminTimeline, UXC-7.
 *
 * §7.3.8 pide, en la gestión de usuarios, una «línea de tiempo de actividad
 * del usuario». Lo que hoy publica un servicio con fechas por usuario es el
 * historial de sanciones (`GET /sanciones/usuarios/{usuarioId}`): emitida,
 * revertida, vence o venció. Este componente pinta cualquier lista de hechos
 * fechados; quien lo usa decide qué hechos son, sin inventar ninguno.
 *
 * Cada hecho se lee entero sin mirar el color: fecha y hora en un `<time>`,
 * qué pasó en palabras, quién lo hizo si se sabe, y el tono va además en la
 * forma del marcador (círculo, rombo, cuadrado).
 *
 * @module comun/ui/linea-de-tiempo
 */

import { h } from './dom.js';

const LOCALIZACION = 'es-CO';

/** Tonos admitidos; el marcador cambia de forma con cada uno. */
export const TONOS = Object.freeze(['info', 'advertencia', 'error', 'exito', 'neutro']);

/**
 * @typedef {object} HechoDeLaLinea
 * @property {string} cuando ISO-8601
 * @property {string} titulo qué pasó
 * @property {string|null} [detalle]
 * @property {string|null} [actor] quién lo hizo
 * @property {'info'|'advertencia'|'error'|'exito'|'neutro'} [tono]
 * @property {Node|null} [extra] algo más (una cuenta atrás, un enlace)
 * @property {boolean} [futuro] un hecho que todavía no ha pasado (vence…)
 */

/**
 * @param {string} valor
 * @returns {string}
 */
export function fechaDeHecho(valor) {
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) {
    return '—';
  }
  return fecha.toLocaleString(LOCALIZACION, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * @param {HechoDeLaLinea[]} hechos en cualquier orden: se ordenan del más
 *   reciente al más antiguo (un hecho futuro va primero)
 * @param {{etiqueta?: string}} [opciones] nombre de la lista para el lector de pantalla
 * @returns {HTMLOListElement}
 */
export function lineaDeTiempo(hechos, { etiqueta = 'Línea de tiempo' } = {}) {
  const ordenados = [...(hechos ?? [])].sort(
    (a, b) => new Date(b.cuando).getTime() - new Date(a.cuando).getTime(),
  );
  return h('ol', {
    clase: 'linea-tiempo',
    atributos: { 'aria-label': etiqueta },
    hijos: ordenados.map((hecho) => {
      const tono = TONOS.includes(hecho.tono) ? hecho.tono : 'neutro';
      return h('li', {
        clase: `linea-tiempo__hecho linea-tiempo__hecho--${tono}${hecho.futuro ? ' linea-tiempo__hecho--futuro' : ''}`,
        datos: { tono },
        hijos: [
          h('span', { clase: 'linea-tiempo__marca', atributos: { 'aria-hidden': 'true' } }),
          h('div', {
            clase: 'linea-tiempo__cuerpo',
            hijos: [
              h('p', {
                clase: 'linea-tiempo__cuando',
                hijos: [
                  hecho.futuro ? h('span', { texto: 'Previsto · ' }) : null,
                  h('time', {
                    texto: fechaDeHecho(hecho.cuando),
                    atributos: { datetime: hecho.cuando },
                  }),
                ],
              }),
              h('p', { clase: 'linea-tiempo__titulo', texto: hecho.titulo }),
              hecho.detalle
                ? h('p', { clase: 'linea-tiempo__detalle', texto: hecho.detalle })
                : null,
              hecho.actor ? h('p', { clase: 'linea-tiempo__actor', texto: hecho.actor }) : null,
              hecho.extra ?? null,
            ],
          }),
        ],
      });
    }),
  });
}
