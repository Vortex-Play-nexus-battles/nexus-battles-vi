/**
 * Fechas de los filtros de la consola — SA-4.
 *
 * Un filtro de fecha da un día (`aaaa-mm-dd`, el valor de un `<input
 * type="date">`); los servicios esperan un instante ISO-8601 con zona. El día
 * es el de Colombia, que no cambia de hora: empieza a las 00:00 −05:00.
 *
 * Lo usan la consola de sanciones y el historial de moderación.
 *
 * @module comun/ui/admin/fechas
 */

/** Colombia no cambia de hora. */
export const ZONA_DE_COLOMBIA = '-05:00';

/**
 * Un día de un filtro como instante: el principio de ese día en Colombia.
 * `finDelDia` da el principio del día siguiente, que es lo que espera un
 * «hasta» excluido para que el día elegido cuente entero.
 *
 * @param {string} dia
 * @param {{finDelDia?: boolean}} [opciones]
 * @returns {string|null}
 */
export function instanteDelDia(dia, { finDelDia = false } = {}) {
  const coincide = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(dia ?? '').trim());
  if (!coincide) {
    return null;
  }
  const [, anio, mes, diaDelMes] = coincide.map(Number);
  const base = new Date(Date.UTC(anio, mes - 1, diaDelMes + (finDelDia ? 1 : 0)));
  if (Number.isNaN(base.getTime())) {
    return null;
  }
  return `${base.toISOString().slice(0, 10)}T00:00:00${ZONA_DE_COLOMBIA}`;
}

/**
 * El primer instante del mes en curso en Colombia.
 *
 * @param {number} ahora milisegundos
 * @returns {string}
 */
export function inicioDelMes(ahora) {
  // La fecha de Colombia es la de UTC cinco horas antes.
  const enColombia = new Date(ahora - 5 * 60 * 60 * 1000);
  const mes = String(enColombia.getUTCMonth() + 1).padStart(2, '0');
  return `${enColombia.getUTCFullYear()}-${mes}-01T00:00:00${ZONA_DE_COLOMBIA}`;
}
