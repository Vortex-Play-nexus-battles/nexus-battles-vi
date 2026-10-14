/**
 * Fechas de los filtros de la consola — SA-4.
 *
 * Un filtro de fecha da un día (`aaaa-mm-dd`, el valor de un `<input
 * type="date">`); los servicios esperan un instante ISO-8601 con zona. El día
 * es el de Colombia, que no cambia de hora: empieza a las 00:00 −05:00.
 *
 * Lo usan la consola de sanciones y el historial de moderación.
 *
 * Y los días de una serie (`aaaa-mm-dd` que publica un servicio: registros,
 * sanciones o reportes por día): un día no tiene hora ni zona, así que se
 * escribe tal cual, sin pasar por la zona horaria de quien mira. Leído como
 * instante, «2026-10-05» sería la medianoche UTC, y en Colombia eso todavía
 * es el 4 de octubre.
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

/** Un día `aaaa-mm-dd` como fecha UTC, o null si no lo es. */
function diaComoFecha(dia) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(dia ?? '').trim())) {
    return null;
  }
  const fecha = new Date(`${String(dia).trim()}T00:00:00Z`);
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

/**
 * `2026-10-05` → «5 oct»: la etiqueta de una barra. Sin el «de» que pone el
 * formato del español («5 de oct»): en un eje con treinta días, cada letra de
 * más acaba pisando la etiqueta de al lado en un teléfono.
 *
 * @param {string|null|undefined} dia
 * @returns {string}
 */
export function diaCorto(dia) {
  if (!dia) {
    return 'sin fecha';
  }
  const fecha = diaComoFecha(dia);
  return fecha
    ? fecha
        .toLocaleDateString('es-CO', { day: 'numeric', month: 'short', timeZone: 'UTC' })
        .replace(/\sde\s/g, ' ')
    : String(dia);
}

/**
 * `2026-10-05` → «5 oct 2026».
 *
 * @param {string|null|undefined} dia
 * @returns {string}
 */
export function diaCompleto(dia) {
  if (!dia) {
    return 'sin fecha';
  }
  const fecha = diaComoFecha(dia);
  return fecha
    ? fecha.toLocaleDateString('es-CO', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        timeZone: 'UTC',
      })
    : String(dia);
}

/** Más días que esto no se rellenan: no es una serie que quepa en un gráfico. */
const MAXIMO_DE_DIAS = 400;

/**
 * Una serie por día con los días sin actividad puestos a cero.
 *
 * Los servicios solo publican los días con algo (un día sin sanciones no sale
 * en `porDia`); pintados así, dos barras de días separados por una semana
 * quedarían juntas y el gráfico mentiría. Los días son UTC, el corte de esas
 * series: del de `desde` al de `hasta`, este incluido salvo que `hasta` caiga
 * justo a medianoche (un «hasta» excluido no se cuenta).
 *
 * @param {string} desde instante ISO-8601
 * @param {string} hasta instante ISO-8601
 * @param {Array<{fecha: string}>} porDia los días que publicó el servicio
 * @param {(dia: object) => number} valor la cifra de un día publicado
 * @returns {Array<{fecha: string, valor: number}>} o la serie tal cual si el periodo no se puede leer
 */
export function serieDiaria(desde, hasta, porDia, valor) {
  const publicados = (Array.isArray(porDia) ? porDia : []).filter((d) => d?.fecha);
  const porFecha = new Map(publicados.map((d) => [String(d.fecha), valor(d)]));
  const inicio = new Date(desde);
  const fin = new Date(hasta);
  if (Number.isNaN(inicio.getTime()) || Number.isNaN(fin.getTime()) || fin <= inicio) {
    return publicados.map((d) => ({ fecha: String(d.fecha), valor: valor(d) }));
  }
  const primero = Date.UTC(inicio.getUTCFullYear(), inicio.getUTCMonth(), inicio.getUTCDate());
  // Un «hasta» a medianoche UTC excluye ese día.
  const ultimoInstante = new Date(fin.getTime() - 1);
  const ultimo = Date.UTC(
    ultimoInstante.getUTCFullYear(),
    ultimoInstante.getUTCMonth(),
    ultimoInstante.getUTCDate(),
  );
  const DIA = 24 * 60 * 60 * 1000;
  if ((ultimo - primero) / DIA + 1 > MAXIMO_DE_DIAS) {
    return publicados.map((d) => ({ fecha: String(d.fecha), valor: valor(d) }));
  }
  const serie = [];
  for (let instante = primero; instante <= ultimo; instante += DIA) {
    const fecha = new Date(instante).toISOString().slice(0, 10);
    serie.push({ fecha, valor: porFecha.get(fecha) ?? 0 });
  }
  return serie;
}
