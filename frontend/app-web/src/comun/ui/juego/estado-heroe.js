/**
 * En qué estado está un héroe o un objeto del jugador — UXC-1 (HeroStatusBadge).
 *
 * §7.8.10 del documento pide que un héroe comprometido se vea como tal («En
 * misión») y no se pueda equipar ni llevar a otra partida; §7.7 bloquea el
 * producto publicado en subasta; y HU-SAL-003 no deja entrar a una partida con
 * un héroe sin equipo. Son estados distintos con consecuencias distintas, y
 * hasta aquí la vitrina solo sabía decir «No disponible».
 *
 * ## Solo estados que un servicio sostiene
 *
 * `estadoDeHeroe` y `estadoDeObjeto` DEDUCEN el estado de datos publicados:
 *
 *   - `disponible` y `subastaId` de `ElementoInventario` (inventario.yaml):
 *     bloqueado por subasta, o no disponible sin más.
 *   - `EquipamientoHeroe`: sin nada puesto el héroe no puede combatir (lo
 *     comprueba HU-SAL-003 en el servidor; aquí solo se avisa antes).
 *   - Qué héroe lleva cada objeto, del mismo `EquipamientoHeroe`.
 *
 * «En misión» y «En torneo» existen como estados del componente porque el
 * documento los pide, pero **ninguna función de aquí los deduce**: ningún
 * contrato publica hoy qué héroe está en una misión (no hay servicio de
 * misiones) ni qué héroe juega un torneo (los equipos de `torneos.yaml`
 * inscriben jugadores, no héroes). Pintarlos sin dato sería inventarlos.
 *
 * ## No solo color
 *
 * Cada estado lleva icono y texto; el color del distintivo refuerza.
 */

import { h } from '../dom.js';
import { icono } from '../icono.js';

/** Los estados que el componente sabe pintar. */
export const ESTADOS = Object.freeze({
  DISPONIBLE: 'DISPONIBLE',
  EQUIPADO: 'EQUIPADO',
  EN_MISION: 'EN_MISION',
  EN_TORNEO: 'EN_TORNEO',
  BLOQUEADO: 'BLOQUEADO',
  NO_ELEGIBLE: 'NO_ELEGIBLE',
});

/** Texto, icono y variante del kit de cada estado. */
const PRESENTACION = Object.freeze({
  DISPONIBLE: { texto: 'Disponible', icono: 'check', variante: 'activo' },
  EQUIPADO: { texto: 'Equipado', icono: 'escudo-check', variante: 'equipado' },
  EN_MISION: { texto: 'En misión', icono: 'mapa', variante: 'en-mision' },
  EN_TORNEO: { texto: 'En torneo', icono: 'trofeo', variante: 'en-juego' },
  // Bloqueado es neutro (no es culpa del jugador ni un error); no poder
  // combatir es un aviso con solucion (equiparlo): tono de advertencia.
  // «No disponible» es la palabra de HU-INV-009/010 para lo bloqueado.
  BLOQUEADO: { texto: 'No disponible', icono: 'candado', variante: 'bloqueado' },
  NO_ELEGIBLE: { texto: 'No puede combatir', icono: 'alerta', variante: 'aviso' },
});

/**
 * Cuántas ranuras ocupa un equipamiento (2 armas, 6 armaduras, 2 ítems).
 *
 * @param {{armas?: string[], armaduras?: Record<string,string>, items?: string[]}|null} equipo
 * @returns {number}
 */
export function ranurasOcupadas(equipo) {
  if (!equipo || typeof equipo !== 'object') {
    return 0;
  }
  const armas = Array.isArray(equipo.armas) ? equipo.armas.length : 0;
  const items = Array.isArray(equipo.items) ? equipo.items.length : 0;
  const armaduras =
    equipo.armaduras && typeof equipo.armaduras === 'object'
      ? Object.values(equipo.armaduras).filter(Boolean).length
      : 0;
  return armas + armaduras + items;
}

/** Total de ranuras de un héroe según §6.1.2: 2 + 6 + 2. */
export const RANURAS_TOTALES = 10;

/**
 * Estado de un héroe del jugador.
 *
 * @param {{elemento: object, equipamiento?: object|null}} datos
 *   `equipamiento` ausente (`undefined`) significa «no se pudo consultar»: en
 *   ese caso no se afirma que le falte equipo.
 * @returns {{estado: string, detalle: string|null}}
 */
export function estadoDeHeroe({ elemento, equipamiento }) {
  if (elemento?.disponible === false) {
    return {
      estado: ESTADOS.BLOQUEADO,
      detalle: elemento.subastaId ? 'en subasta' : 'no disponible',
    };
  }
  if (equipamiento === undefined) {
    return { estado: ESTADOS.DISPONIBLE, detalle: null };
  }
  if (ranurasOcupadas(equipamiento) === 0) {
    return { estado: ESTADOS.NO_ELEGIBLE, detalle: 'sin equipo' };
  }
  return { estado: ESTADOS.DISPONIBLE, detalle: null };
}

/**
 * Estado de un objeto (arma, armadura, ítem…).
 *
 * @param {{elemento: object, equipadoEn?: string|null}} datos
 *   `equipadoEn`: nombre del héroe que lo lleva, si alguno.
 * @returns {{estado: string, detalle: string|null}}
 */
export function estadoDeObjeto({ elemento, equipadoEn = null }) {
  if (elemento?.disponible === false) {
    return {
      estado: ESTADOS.BLOQUEADO,
      detalle: elemento.subastaId ? 'en subasta' : 'no disponible',
    };
  }
  if (equipadoEn) {
    return { estado: ESTADOS.EQUIPADO, detalle: equipadoEn };
  }
  return { estado: ESTADOS.DISPONIBLE, detalle: null };
}

/**
 * Distintivo del estado: icono + texto, con la variante del kit.
 *
 * @param {string} estado uno de `ESTADOS`
 * @param {{detalle?: string|null}} [opciones] se escribe tras un punto medio
 * @returns {HTMLElement}
 */
export function selloDeEstado(estado, { detalle = null } = {}) {
  const presentacion = PRESENTACION[estado] ?? PRESENTACION.DISPONIBLE;
  const texto = detalle ? `${presentacion.texto} · ${detalle}` : presentacion.texto;
  return h('span', {
    clase: `distintivo distintivo--${presentacion.variante} sello-estado-heroe`,
    datos: { estado },
    hijos: [
      icono(presentacion.icono, { clase: 'icono sello-estado-heroe__icono', etiqueta: null }),
      h('span', { texto }),
    ],
  });
}
