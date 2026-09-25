/**
 * Bloque de estadísticas de un héroe — UXC-1 (StatBlock).
 *
 * La Tabla 6 del documento da seis cifras por prototipo: poder, vida,
 * defensa, ataque, daño y sanar (las tres últimas en notación de dados). Hasta
 * aquí se pintaban en dos sitios con dos marcados distintos —la ficha del
 * producto (`ficha__atributos`) y la cabecera del panel de equipamiento
 * (`inventario-equipo__estadisticas`)— y cada uno decidía por su cuenta qué
 * hacer con una fórmula ausente. Ahora hay uno.
 *
 * ## Acepta las dos formas que publican los servicios
 *
 *   - `EstadisticasEquipadas` de inventario.yaml: ataque/daño/sanar como
 *     objetos `{base, cantidadDados, caras, formula}`.
 *   - `Estadisticas` de heroes.yaml: ataque/daño/sanar como texto de la Tabla 6
 *     («10 + 1d6») y el detalle aparte.
 *
 * ## Lo que falta no se pinta
 *
 * Un sanador no tiene ataque; un guerrero no sana. La cifra ausente se omite:
 * un «0» o un «—» se leerían como «este héroe no tiene defensa». Tampoco se
 * calcula nada: los valores son inalterables (Project Charter) y se muestran
 * tal cual llegan.
 */

import { h } from '../dom.js';
import { icono } from '../icono.js';

/** Las seis cifras, en el orden de la Tabla 6, con su icono. */
export const CIFRAS = Object.freeze([
  { campo: 'poder', etiqueta: 'Poder', icono: 'rayo' },
  { campo: 'vida', etiqueta: 'Vida', icono: 'corazon' },
  { campo: 'defensa', etiqueta: 'Defensa', icono: 'escudo' },
  { campo: 'ataque', etiqueta: 'Ataque', icono: 'espada', formula: true },
  { campo: 'dano', etiqueta: 'Daño', icono: 'objetivo', formula: true },
  { campo: 'sanar', etiqueta: 'Sanar', icono: 'cruz', formula: true },
]);

/**
 * Una fórmula de dados dicha como la escribiría una persona.
 *
 * @param {string|{base?: number, cantidadDados?: number, caras?: number, formula?: string}|null} formula
 * @returns {string|null}
 */
export function formulaLegible(formula) {
  if (typeof formula === 'string') {
    const limpia = formula.trim();
    return limpia === '' || limpia === '-' ? null : limpia;
  }
  if (!formula || typeof formula !== 'object') {
    return null;
  }
  const { base, cantidadDados, caras } = formula;
  const dados =
    Number.isFinite(cantidadDados) && Number.isFinite(caras) && cantidadDados > 0 && caras > 0
      ? `${cantidadDados}d${caras}`
      : null;
  const conBase = Number.isFinite(base) && base !== 0 ? String(base) : null;
  if (conBase && dados) {
    return `${conBase} + ${dados}`;
  }
  if (conBase ?? dados) {
    return conBase ?? dados;
  }
  // El servidor también manda la fórmula ya compuesta: último recurso.
  return typeof formula.formula === 'string' && formula.formula.trim() ? formula.formula : null;
}

/**
 * Las cifras utilizables de un objeto de estadísticas, en orden.
 *
 * @param {object|null|undefined} estadisticas
 * @returns {Array<{campo: string, etiqueta: string, icono: string, valor: string}>}
 */
export function cifrasDe(estadisticas) {
  if (!estadisticas || typeof estadisticas !== 'object') {
    return [];
  }
  const cifras = [];
  for (const cifra of CIFRAS) {
    const bruto = estadisticas[cifra.campo];
    let valor = null;
    if (cifra.formula) {
      valor = formulaLegible(bruto);
    } else if (Number.isFinite(bruto)) {
      valor = String(bruto);
    }
    if (valor !== null) {
      cifras.push({ ...cifra, valor });
    }
  }
  return cifras;
}

/**
 * El bloque, o `null` si no trae ni una cifra.
 *
 * @param {object|null|undefined} estadisticas
 * @param {{compacto?: boolean, titulo?: string|null, nota?: string|null}} [opciones]
 *   `compacto` las pone en una fila (tarjeta, HUD); sin él, en rejilla.
 * @returns {HTMLElement|null}
 */
export function bloqueDeEstadisticas(
  estadisticas,
  { compacto = false, titulo = null, nota = null } = {},
) {
  const cifras = cifrasDe(estadisticas);
  if (cifras.length === 0) {
    return null;
  }
  const lista = h('dl', {
    clase: compacto ? 'stat-block stat-block--compacto' : 'stat-block',
    hijos: cifras.map((cifra) =>
      h('div', {
        clase: 'stat-block__cifra',
        datos: { campo: cifra.campo },
        hijos: [
          h('dt', {
            clase: 'stat-block__etiqueta',
            hijos: [
              icono(cifra.icono, { clase: 'icono stat-block__icono', etiqueta: null }),
              h('span', { texto: cifra.etiqueta }),
            ],
          }),
          h('dd', { clase: 'stat-block__valor', texto: cifra.valor }),
        ],
      }),
    ),
  });
  if (!titulo && !nota) {
    return lista;
  }
  return h('section', {
    clase: 'stat-block__seccion',
    hijos: [
      titulo ? h('h3', { clase: 'stat-block__titulo', texto: titulo }) : null,
      nota ? h('p', { clase: 'stat-block__nota t-meta', texto: nota }) : null,
      lista,
    ],
  });
}
