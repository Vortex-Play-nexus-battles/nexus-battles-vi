/**
 * Una partida contada para quien la jugó — UXC-9 (Mi cuenta · Estadísticas).
 *
 * `ResumenDePartida` de `contracts/openapi/salas-partidas.yaml` (1.7.0,
 * `GET /partidas/mias`) trae el resultado **desde el punto de vista de quien
 * pregunta**: lo decide el servidor al cerrar la partida. Aquí solo se dice
 * con palabras; no se recalcula nada.
 *
 * El nombre de las modalidades vivía dentro de `batallas.js`; ahora lo usan
 * dos vistas (el listado de salas y Mi cuenta), así que sube a `comun/`.
 *
 * @module partida
 */

import { distintivo } from '../distintivo.js';

/** Nombre legible de la modalidad (`Modalidad` del contrato, RF-JUE-004). */
export const NOMBRE_DE_MODALIDAD = Object.freeze({
  UNO_CONTRA_UNO: '1 contra 1',
  CONTRA_IA: 'Contra la IA',
  HASTA_SEIS: 'Hasta seis',
});

/**
 * @param {string|null|undefined} modalidad
 * @param {string} [siNoSeSabe] lo que se dice si el servidor no la trae
 * @returns {string}
 */
export function nombreDeModalidad(modalidad, siNoSeSabe = 'Partida') {
  return NOMBRE_DE_MODALIDAD[modalidad] ?? siNoSeSabe;
}

const RESULTADOS = Object.freeze({
  VICTORIA: { texto: 'Victoria', variante: 'victoria' },
  DERROTA: { texto: 'Derrota', variante: 'derrota' },
  EMPATE: { texto: 'Empate', variante: 'empate' },
});

/**
 * El resultado de una partida, dicho para quien la jugó.
 *
 * @param {{estado?: string, resultado?: string|null}|null|undefined} resumen
 * @returns {{texto: string, variante: string|null}}
 */
export function resultadoDePartida(resumen) {
  if (resumen?.estado === 'EN_CURSO') {
    return { texto: 'En curso', variante: 'en-juego' };
  }
  return RESULTADOS[resumen?.resultado] ?? { texto: 'Terminada', variante: null };
}

/**
 * Distintivo del resultado: el texto lo dice, el color solo lo refuerza.
 *
 * @param {{estado?: string, resultado?: string|null}} resumen
 * @returns {HTMLElement}
 */
export function distintivoDeResultado(resumen) {
  const { texto, variante } = resultadoDePartida(resumen);
  return distintivo(texto, variante);
}

/**
 * Cuántas victorias, derrotas y empates hay **entre las partidas dadas**.
 *
 * No es un total de la cuenta: el servidor no publica ese agregado, así que
 * quien lo pinte tiene que decir de cuántas partidas sale («de tus últimas
 * 16»). Contar lo que ya se muestra no es lógica de negocio; inventarse un
 * porcentaje de victorias de toda la carrera sí lo sería.
 *
 * @param {Array<{estado?: string, resultado?: string|null}>} partidas
 * @returns {{victorias: number, derrotas: number, empates: number, enCurso: number}}
 */
export function recuentoDeResultados(partidas) {
  const recuento = { victorias: 0, derrotas: 0, empates: 0, enCurso: 0 };
  for (const partida of Array.isArray(partidas) ? partidas : []) {
    if (partida?.estado === 'EN_CURSO') {
      recuento.enCurso += 1;
    } else if (partida?.resultado === 'VICTORIA') {
      recuento.victorias += 1;
    } else if (partida?.resultado === 'DERROTA') {
      recuento.derrotas += 1;
    } else if (partida?.resultado === 'EMPATE') {
      recuento.empates += 1;
    }
  }
  return recuento;
}
