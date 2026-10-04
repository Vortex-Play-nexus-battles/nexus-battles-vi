/**
 * Resumen de la calificación de un producto — UXC-3 (RatingSummary).
 *
 * B3 — lo que devuelve `GET /products/{id}/rating` (comentarios.yaml 1.5.0,
 * `ResumenDeCalificaciones`) en una línea que se lee de un vistazo: el
 * promedio, sus estrellas y cuántas personas calificaron. Hasta la 1.5.0 el
 * resumen salía del hilo de comentarios; desde que la calificación vive aparte
 * (7.1: «solo pueden calificar un producto una vez»), su fuente es la suya.
 * Cuántas opiniones hay no es parte de la calificación: es el `total` del hilo,
 * y quien pinta el resumen lo pasa aparte (`opiniones`) si lo conoce.
 *
 * El promedio lo calcula el servicio, con un decimal. Aquí no se estima ni se
 * redondea hacia arriba: un `promedio` nulo es «sin valoraciones», nunca un
 * cero, porque un producto que nadie ha calificado no es un producto malo
 * (CA-03 de HU-COM-003).
 *
 * @module comun/ui/comunidad/resumen
 */

import { clases, h } from '../dom.js';
import { cifraDeCalificacion, estrellasDeCalificacion, MAXIMO_ESTRELLAS } from './estrellas.js';

/**
 * @typedef {{promedio?: number|null, total?: number, distribucion?: Record<string, number>}} Resumen
 *   `ResumenDeCalificaciones`: `total` son las calificaciones, no las opiniones.
 */

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
 * @param {Resumen|null|undefined} resumen
 * @returns {boolean}
 */
export function hayPromedio(resumen) {
  return Number.isFinite(resumen?.promedio) && (resumen?.total ?? 0) > 0;
}

/**
 * Cuántas opiniones decir, o `null` si no se sabe o no hay ninguna.
 *
 * @param {number|null|undefined} opiniones
 * @returns {string|null}
 */
function deOpiniones(opiniones) {
  return Number.isInteger(opiniones) && opiniones > 0
    ? conCuenta(opiniones, 'opinión', 'opiniones')
    : null;
}

/**
 * La frase del resumen, sin marcado: sirve para un `aria-label` o una prueba.
 *
 * @param {Resumen|null|undefined} resumen
 * @param {{opiniones?: number|null}} [opciones] el `total` del hilo, si se conoce
 * @returns {string}
 */
export function textoDelResumen(resumen, { opiniones = null } = {}) {
  const cola = deOpiniones(opiniones);
  const conOpiniones = cola ? ` · ${cola}` : '';
  if (!hayPromedio(resumen)) {
    return `Sin valoraciones todavía${conOpiniones}`;
  }
  const cifra = cifraDeCalificacion(resumen.promedio);
  const valoraciones = conCuenta(resumen.total, 'valoración', 'valoraciones');
  return `${cifra} de ${MAXIMO_ESTRELLAS} · ${valoraciones}${conOpiniones}`;
}

/**
 * El bloque del resumen.
 *
 * @param {Resumen|null|undefined} resumen `ResumenDeCalificaciones`
 * @param {{compacto?: boolean, opiniones?: number|null}} [opciones]
 *   `compacto`: una sola línea, para la cabecera de una ficha o una tarjeta.
 *   `opiniones`: el `total` del hilo, que se dice junto a las valoraciones.
 * @returns {HTMLElement}
 */
export function resumenDeCalificacion(resumen, { compacto = false, opiniones = null } = {}) {
  const conPromedio = hayPromedio(resumen);
  const caja = h('div', {
    clase: clases('resumen-calificacion', compacto && 'resumen-calificacion--compacto'),
    datos: { estado: conPromedio ? 'con-valoraciones' : 'sin-valoraciones' },
  });

  if (!conPromedio) {
    caja.append(
      estrellasDeCalificacion(0, { decorativas: true, clase: 'resumen-calificacion__vacias' }),
      h('p', {
        clase: 'resumen-calificacion__detalle',
        texto: textoDelResumen(resumen, { opiniones }),
      }),
    );
    return caja;
  }

  const cifra = cifraDeCalificacion(resumen.promedio);
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
        estrellasDeCalificacion(resumen.promedio, { conCifra: compacto }),
        h('p', {
          clase: 'resumen-calificacion__detalle',
          texto: [conCuenta(resumen.total, 'valoración', 'valoraciones'), deOpiniones(opiniones)]
            .filter(Boolean)
            .join(' · '),
        }),
      ],
    }),
  );
  return caja;
}
