/**
 * El inventario entero, partido en héroes y objetos — UXC-1 (feedback del
 * profesor: «no presentar una sola lista genérica»).
 *
 * ## Por qué se reúne entero
 *
 * `GET /api/v1/inventario/elementos` pagina de dieciséis en dieciséis y no
 * admite filtro por tipo (inventario.yaml, `consultarInventarioPaginado`: su
 * único parámetro es `pagina`). Con esa sola consulta no hay forma de enseñar
 * «tus héroes» por un lado y «tus objetos» por otro: una página del servidor
 * los trae mezclados, y filtrarla dejaría páginas de nueve o de cuatro.
 *
 * Así que se piden las páginas hasta la última, se parte la colección por tipo
 * y cada pestaña pagina la suya de dieciséis en dieciséis, con el mismo
 * control 1…10 de siempre (§7.1). Un jugador empieza con un héroe y un kit
 * (`PoliticaInicial` de ms-identidad): son una o dos consultas. Hay un tope
 * para que un inventario enorme no dispare cien peticiones; si se alcanza, la
 * vista lo dice en vez de enseñar una colección cortada como si fuera toda.
 *
 * La búsqueda sigue siendo la del servidor (`/busqueda`, índice de
 * HU-INV-002 y HU-REN-003): no se reimplementa aquí.
 */

import { PRODUCTOS_POR_PAGINA } from './vitrina.js';

/** Tope de páginas que se piden de una vez: 25 × 16 = 400 elementos. */
export const MAX_PAGINAS = 25;

/**
 * Pide todas las páginas del inventario propio, en orden.
 *
 * @param {(identidad: string, pagina: number) => Promise<object>} consultar
 * @param {string} identidad
 * @param {{maxPaginas?: number}} [opciones]
 * @returns {Promise<{elementos: object[], completo: boolean, totalElementos: number}>}
 */
export async function reunirInventario(consultar, identidad, { maxPaginas = MAX_PAGINAS } = {}) {
  const elementos = [];
  let pagina = 0;
  let totalElementos = 0;
  for (;;) {
    const respuesta = await consultar(identidad, pagina);
    const lote = Array.isArray(respuesta?.elementos) ? respuesta.elementos : [];
    elementos.push(...lote);
    totalElementos = Number.isFinite(respuesta?.totalElementos)
      ? respuesta.totalElementos
      : elementos.length;
    const totalPaginas = Number.isFinite(respuesta?.totalPaginas) ? respuesta.totalPaginas : 0;
    const ultima = respuesta?.ultima === true || lote.length === 0 || pagina + 1 >= totalPaginas;
    if (ultima) {
      return { elementos, completo: true, totalElementos };
    }
    pagina += 1;
    if (pagina >= maxPaginas) {
      return { elementos, completo: false, totalElementos };
    }
  }
}

/** ¿Es un héroe? El tipo sale de `ElementoInventario.tipo`. */
export function esHeroe(elemento) {
  return elemento?.tipo === 'HEROE';
}

/**
 * Una página de dieciséis sobre una lista ya reunida, con la misma forma que
 * `PaginaInventario` del contrato: así la vitrina y el control de paginación
 * no distinguen si la página vino del servidor o se cortó aquí.
 *
 * @param {object[]} lista
 * @param {number} numero desde cero
 * @param {number} [tamanio]
 * @returns {{elementos: object[], numero: number, tamanio: number, totalElementos: number,
 *   totalPaginas: number, ultima: boolean}}
 */
export function paginaLocal(lista, numero = 0, tamanio = PRODUCTOS_POR_PAGINA) {
  const total = Array.isArray(lista) ? lista.length : 0;
  const totalPaginas = Math.ceil(total / tamanio);
  const acotado = totalPaginas === 0 ? 0 : Math.min(Math.max(0, numero), totalPaginas - 1);
  const desde = acotado * tamanio;
  return {
    elementos: (lista ?? []).slice(desde, desde + tamanio),
    numero: acotado,
    tamanio,
    totalElementos: total,
    totalPaginas,
    ultima: totalPaginas === 0 || acotado >= totalPaginas - 1,
  };
}

/**
 * Qué héroe lleva cada objeto: `elementoId → nombre del héroe`.
 *
 * Sale de los `EquipamientoHeroe` que ya se consultaron; un héroe cuyo equipo
 * no se pudo leer simplemente no aporta entradas (no se afirma nada de sus
 * objetos).
 *
 * @param {object[]} heroes elementos de tipo HEROE
 * @param {Map<string, object|undefined>} equipos heroeId → equipamiento
 * @returns {Map<string, string>}
 */
export function mapaDeEquipados(heroes, equipos) {
  const mapa = new Map();
  for (const heroe of heroes ?? []) {
    const equipo = equipos?.get(heroe.id);
    if (!equipo) {
      continue;
    }
    const ids = [
      ...(Array.isArray(equipo.armas) ? equipo.armas : []),
      ...(Array.isArray(equipo.items) ? equipo.items : []),
      ...Object.values(equipo.armaduras ?? {}),
    ].filter(Boolean);
    for (const id of ids) {
      mapa.set(id, heroe.nombrePropio);
    }
  }
  return mapa;
}
