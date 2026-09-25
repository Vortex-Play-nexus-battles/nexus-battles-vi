/**
 * Resumen de la calificación de un producto — UXC-3 (RatingSummary).
 *
 * Lo que devuelve `GET /products/{id}/comments` (comentarios.yaml,
 * `HiloDeComentariosResponse`) en una línea que se lee de un vistazo: el
 * promedio, sus estrellas y cuántas personas calificaron y opinaron.
 *
 * El promedio lo calcula el servicio. Aquí no se estima ni se redondea hacia
 * arriba: `calificacionPromedio` nulo es «sin valoraciones», nunca un cero,
 * porque un producto que nadie ha calificado no es un producto malo (CA-03 de
 * HU-COM-003).
 *
 * @module comun/ui/comunidad/resumen
 */

import { clases, h } from '../dom.js';
import { cifraDeCalificacion, estrellasDeCalificacion, MAXIMO_ESTRELLAS } from './estrellas.js';

/**
 * «1 valoración», «3 valoraciones».
 *
 * @param {number} cuantas
 * @param {string} singular
 * @param {string} plural
 * @returns {string}
 */
export function conCuenta(cuantas, singular, plural) {
  return `${cuantas} ${cuantas === 1 ? singular : plural}`;
}

/**
 * ¿Hay un promedio que enseñar?
 *
 * @param {{calificacionPromedio?: number|null, totalCalificaciones?: number}} hilo
 * @returns {boolean}
 */
export function hayPromedio(hilo) {
  return Number.isFinite(hilo?.calificacionPromedio) && (hilo?.totalCalificaciones ?? 0) > 0;
}

/**
 * La frase del resumen, sin marcado: sirve para un `aria-label` o una prueba.
 *
 * @param {{calificacionPromedio?: number|null, totalCalificaciones?: number, total?: number}} hilo
 * @returns {string}
 */
export function textoDelResumen(hilo) {
  const opiniones = Number.isInteger(hilo?.total) ? hilo.total : 0;
  const deOpiniones = opiniones > 0 ? ` · ${conCuenta(opiniones, 'opinión', 'opiniones')}` : '';
  if (!hayPromedio(hilo)) {
    return `Sin valoraciones todavía${deOpiniones}`;
  }
  const cifra = cifraDeCalificacion(hilo.calificacionPromedio);
  const valoraciones = conCuenta(hilo.totalCalificaciones, 'valoración', 'valoraciones');
  return `${cifra} de ${MAXIMO_ESTRELLAS} · ${valoraciones}${deOpiniones}`;
}

/**
 * El bloque del resumen.
 *
 * @param {{calificacionPromedio?: number|null, totalCalificaciones?: number, total?: number}} hilo
 * @param {{compacto?: boolean}} [opciones] compacto: una sola línea, para la
 *   cabecera de una ficha o una tarjeta
 * @returns {HTMLElement}
 */
export function resumenDeCalificacion(hilo, { compacto = false } = {}) {
  const conPromedio = hayPromedio(hilo);
  const opiniones = Number.isInteger(hilo?.total) ? hilo.total : 0;
  const caja = h('div', {
    clase: clases('resumen-calificacion', compacto && 'resumen-calificacion--compacto'),
    datos: { estado: conPromedio ? 'con-valoraciones' : 'sin-valoraciones' },
  });

  if (!conPromedio) {
    caja.append(
      estrellasDeCalificacion(0, { decorativas: true, clase: 'resumen-calificacion__vacias' }),
      h('p', {
        clase: 'resumen-calificacion__detalle',
        texto: textoDelResumen(hilo),
      }),
    );
    return caja;
  }

  const cifra = cifraDeCalificacion(hilo.calificacionPromedio);
  if (!compacto) {
    caja.append(
      h('p', {
        clase: 'resumen-calificacion__cifra',
        texto: cifra,
        atributos: { 'aria-hidden': 'true' },
      }),
    );
  }
  caja.append(
    h('div', {
      clase: 'resumen-calificacion__cuerpo',
      hijos: [
        estrellasDeCalificacion(hilo.calificacionPromedio, { conCifra: compacto }),
        h('p', {
          clase: 'resumen-calificacion__detalle',
          texto: [
            conCuenta(hilo.totalCalificaciones, 'valoración', 'valoraciones'),
            opiniones > 0 ? conCuenta(opiniones, 'opinión', 'opiniones') : null,
          ]
            .filter(Boolean)
            .join(' · '),
        }),
      ],
    }),
  );
  return caja;
}
