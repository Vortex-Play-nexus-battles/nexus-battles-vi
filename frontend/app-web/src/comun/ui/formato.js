/**
 * Formato de cifras y fechas para la interfaz.
 *
 * Seis vistas tenian su propia funcion de formateo y cada una elegia su
 * localizacion: unas `toLocaleDateString('es-CO')`, otras `toLocaleString()` a
 * secas (que depende del navegador de quien mire). Aqui se decide una vez.
 *
 * `es-CO` porque el producto es para la UPB Seccional Bucaramanga; si algun
 * dia hay que cambiarlo, se cambia en esta linea y no en veinte.
 */

const LOCALIZACION = 'es-CO';

/**
 * Creditos, siempre con separador de miles y sin decimales: el libro de
 * creditos trabaja en enteros (creditos.yaml).
 *
 * @param {number|string|null|undefined} cantidad
 * @returns {string} por ejemplo `1.250`; `—` si no hay dato
 */
export function creditos(cantidad) {
  const cifra = Number(cantidad);
  if (cantidad === null || cantidad === undefined || Number.isNaN(cifra)) {
    return '—';
  }
  return cifra.toLocaleString(LOCALIZACION, { maximumFractionDigits: 0 });
}

/**
 * Fecha y hora cortas, en la zona horaria de quien mira.
 *
 * @param {string|number|Date|null|undefined} valor ISO-8601, marca de tiempo o Date
 * @returns {string} `—` si no hay dato o no es una fecha
 */
export function fechaHora(valor) {
  const momento = aFecha(valor);
  if (!momento) {
    return '—';
  }
  return momento.toLocaleString(LOCALIZACION, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * Solo la fecha.
 *
 * @param {string|number|Date|null|undefined} valor
 * @returns {string}
 */
export function fecha(valor) {
  const cuando = aFecha(valor);
  if (!cuando) {
    return '—';
  }
  return cuando.toLocaleDateString(LOCALIZACION, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

/**
 * Cuanto falta (o cuanto paso) en lenguaje corto: `en 3 d`, `en 2 h 15 min`,
 * `termino`. Para contadores de subasta y de cierre de inscripciones.
 *
 * @param {string|number|Date|null|undefined} objetivo
 * @param {Date} [ahora] inyectable para poder probarlo sin relojes falsos
 * @returns {string}
 */
export function cuantoFalta(objetivo, ahora = new Date()) {
  const cuando = aFecha(objetivo);
  if (!cuando) {
    return '—';
  }
  const restanMs = cuando.getTime() - ahora.getTime();
  if (restanMs <= 0) {
    return 'termino';
  }
  const minutos = Math.floor(restanMs / 60000);
  const horas = Math.floor(minutos / 60);
  const dias = Math.floor(horas / 24);
  if (dias >= 1) {
    return `en ${dias} d`;
  }
  if (horas >= 1) {
    return `en ${horas} h ${minutos % 60} min`;
  }
  return `en ${minutos} min`;
}

/**
 * Numero con separador de miles.
 *
 * @param {number|string|null|undefined} valor
 * @param {number} [decimales]
 * @returns {string}
 */
export function numero(valor, decimales = 0) {
  const n = Number(valor);
  if (valor === null || valor === undefined || Number.isNaN(n)) {
    return '—';
  }
  return n.toLocaleString(LOCALIZACION, {
    minimumFractionDigits: decimales,
    maximumFractionDigits: decimales,
  });
}

/**
 * Proporcion 0..1 como porcentaje entero.
 *
 * @param {number|null|undefined} proporcion
 * @returns {string}
 */
export function porcentaje(proporcion) {
  if (proporcion === null || proporcion === undefined || Number.isNaN(Number(proporcion))) {
    return '—';
  }
  return `${Math.round(Number(proporcion) * 100)} %`;
}

/**
 * @param {string|number|Date|null|undefined} valor
 * @returns {Date|null}
 */
function aFecha(valor) {
  if (valor === null || valor === undefined || valor === '') {
    return null;
  }
  const fechaValor = valor instanceof Date ? valor : new Date(valor);
  return Number.isNaN(fechaValor.getTime()) ? null : fechaValor;
}

/**
 * Nombre de cada tipo de producto del catálogo (`TipoProducto`, el mismo enum
 * en productos, inventario y subastas). UXC-8 — había cuatro copias de esta
 * tabla (ficha de producto, catálogo de la consola, subastas, publicar); una
 * sola aquí, y las demás la importan.
 */
export const NOMBRE_DEL_TIPO = Object.freeze({
  HEROE: 'Héroe',
  HABILIDAD: 'Habilidad',
  ARMA: 'Arma',
  ARMADURA: 'Armadura',
  ITEM: 'Ítem',
  EPICA: 'Épica',
});

/**
 * @param {string|null|undefined} tipo la constante del contrato
 * @returns {string} el nombre para leer; «Objeto» si no vino ninguno
 */
export function nombreDelTipo(tipo) {
  return NOMBRE_DEL_TIPO[tipo] ?? (tipo ? String(tipo) : 'Objeto');
}
