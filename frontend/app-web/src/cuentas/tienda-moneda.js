/**
 * La moneda en la que se ve y se paga la tienda — B5.
 *
 * §7.5 del documento: el precio de cada producto va «en COP o dólar o euro
 * dependiendo de su ubicación geográfica». El precio lo calcula siempre el
 * servidor (ecommerce-carrito.yaml 1.4.0, parámetro `moneda`): aquí solo se
 * decide en cuál se pide.
 *
 * ## De dónde sale la moneda
 *
 * 1. La que la persona eligió en el selector, si la eligió (se recuerda en el
 *    navegador: es una preferencia, no un dato de la cuenta).
 * 2. Si no, la del idioma del navegador (`navigator.languages`): la región
 *    del primer idioma que la declare. **Es una aproximación de la
 *    ubicación**, no la ubicación: el navegador no la da sin pedir permiso, y
 *    pedir la geolocalización para enseñar un precio sería desproporcionado.
 *    Colombia y cualquier otra región dan COP; los países que usan el dólar,
 *    USD; los que usan el euro, EUR.
 *
 * ## Solo las monedas que el servidor ofrece
 *
 * Las tasas COP→USD y COP→EUR son una decisión del PO que todavía no está
 * tomada (parámetros `tienda.tasa-cop-usd` y `tienda.tasa-cop-eur` de
 * admin-parametros, sin valor). Mientras falten, la vitrina solo ofrece COP
 * (`monedasDisponibles`) y pedir otra responde 422. Por eso la tienda **pide
 * primero en COP** y solo cambia a la preferida si la vitrina dice que está
 * disponible: nunca se enseña un error por una preferencia, y el selector
 * dice qué monedas no están todavía.
 *
 * @module cuentas/tienda-moneda
 */

/** Las monedas del contrato, en el orden del selector. */
export const MONEDAS = Object.freeze(['COP', 'USD', 'EUR']);

/** La del catálogo: siempre disponible, no depende de ninguna tasa. */
export const MONEDA_BASE = 'COP';

/** Clave de la preferencia en el almacenamiento local (solo la moneda elegida). */
export const CLAVE_MONEDA = 'nexus.tienda.moneda';

/** Nombre de cada moneda, para el selector y los avisos. */
export const NOMBRE_DE_MONEDA = Object.freeze({
  COP: 'peso colombiano',
  USD: 'dólar estadounidense',
  EUR: 'euro',
});

/**
 * Regiones que usan el dólar estadounidense como moneda propia: Estados
 * Unidos, Puerto Rico, Ecuador, El Salvador y Panamá (donde circula a la par
 * del balboa).
 */
const REGIONES_EN_DOLARES = new Set(['US', 'PR', 'EC', 'SV', 'PA']);

/**
 * Regiones que usan el euro: los veintiún países de la zona euro (Bulgaria
 * desde el 1 de enero de 2026) y los que lo usan sin pertenecer a ella.
 */
const REGIONES_EN_EUROS = new Set([
  'AT',
  'BE',
  'BG',
  'CY',
  'DE',
  'EE',
  'ES',
  'FI',
  'FR',
  'GR',
  'HR',
  'IE',
  'IT',
  'LT',
  'LU',
  'LV',
  'MT',
  'NL',
  'PT',
  'SI',
  'SK',
  'AD',
  'MC',
  'ME',
  'SM',
  'VA',
  'XK',
]);

/**
 * La región de una etiqueta de idioma BCP 47 («es-CO» → «CO»,
 * «zh-Hant-TW» → «TW»), o null si no la declara («es») o no es un país
 * («es-419», América Latina).
 *
 * @param {unknown} idioma
 * @returns {string|null}
 */
export function regionDe(idioma) {
  if (typeof idioma !== 'string') {
    return null;
  }
  const partes = idioma.trim().split(/[-_]/).slice(1);
  const region = partes.find((parte) => /^[A-Za-z]{2}$/.test(parte));
  return region ? region.toUpperCase() : null;
}

/**
 * La moneda que corresponde a los idiomas del navegador: la región del
 * primero que la declare decide. Sin ninguna región, COP.
 *
 * @param {ReadonlyArray<string>|null|undefined} idiomas `navigator.languages`
 * @returns {string} COP, USD o EUR
 */
export function monedaPorIdioma(idiomas) {
  for (const idioma of Array.isArray(idiomas) ? idiomas : []) {
    const region = regionDe(idioma);
    if (!region) {
      continue;
    }
    if (REGIONES_EN_DOLARES.has(region)) {
      return 'USD';
    }
    if (REGIONES_EN_EUROS.has(region)) {
      return 'EUR';
    }
    return MONEDA_BASE;
  }
  return MONEDA_BASE;
}

/**
 * Los idiomas del navegador, en orden de preferencia.
 *
 * @param {Navigator|{languages?: string[], language?: string}|null|undefined} navegador
 * @returns {string[]}
 */
export function idiomasDe(navegador) {
  if (Array.isArray(navegador?.languages) && navegador.languages.length > 0) {
    return [...navegador.languages];
  }
  return typeof navegador?.language === 'string' ? [navegador.language] : [];
}

/**
 * La moneda que la persona eligió antes, si la hay y es una de las tres.
 *
 * @param {Storage|null} almacen
 * @returns {string|null}
 */
export function monedaGuardada(almacen) {
  try {
    const valor = almacen?.getItem(CLAVE_MONEDA) ?? null;
    return MONEDAS.includes(valor) ? valor : null;
  } catch {
    return null;
  }
}

/**
 * Recuerda la moneda elegida. Si el almacenamiento falla (ventana privada),
 * no se recuerda y ya.
 *
 * @param {Storage|null} almacen
 * @param {string} moneda
 */
export function guardarMoneda(almacen, moneda) {
  try {
    if (MONEDAS.includes(moneda)) {
      almacen?.setItem(CLAVE_MONEDA, moneda);
    }
  } catch {
    // Sin almacenamiento: la preferencia dura lo que dura la página.
  }
}

/**
 * La moneda preferida: la elegida, o la del idioma del navegador.
 *
 * @param {{almacen?: Storage|null, navegador?: object|null}} [opciones]
 * @returns {{moneda: string, porIdioma: boolean}} `porIdioma` si nadie la eligió
 */
export function monedaPreferida({
  almacen = globalThis.localStorage ?? null,
  navegador = globalThis.navigator ?? null,
} = {}) {
  const guardada = monedaGuardada(almacen);
  if (guardada) {
    return { moneda: guardada, porIdioma: false };
  }
  return { moneda: monedaPorIdioma(idiomasDe(navegador)), porIdioma: true };
}

/**
 * Las monedas que el servidor ofrece, de una página de la vitrina o de un
 * problem detail `moneda-no-disponible`. COP está siempre: no depende de
 * ninguna tasa. Sin el campo (un servicio anterior a 1.4.0), solo COP.
 *
 * @param {{monedasDisponibles?: unknown}|null|undefined} cuerpo
 * @returns {string[]} en el orden de `MONEDAS`
 */
export function disponiblesDe(cuerpo) {
  const declaradas = Array.isArray(cuerpo?.monedasDisponibles) ? cuerpo.monedasDisponibles : [];
  return MONEDAS.filter((moneda) => moneda === MONEDA_BASE || declaradas.includes(moneda));
}

/**
 * En qué moneda se enseña la tienda: la preferida si el servidor la ofrece;
 * si no, COP.
 *
 * @param {string|null} preferida
 * @param {ReadonlyArray<string>} disponibles
 * @returns {string}
 */
export function monedaAMostrar(preferida, disponibles) {
  return preferida && disponibles.includes(preferida) ? preferida : MONEDA_BASE;
}

/**
 * Añade `moneda` a una ruta de la API, salvo COP, que es lo que el servicio
 * entiende sin parámetro: así las rutas en pesos no cambian.
 *
 * @param {string} ruta `/vitrina?size=50`, `/carrito`…
 * @param {string|null|undefined} moneda
 * @returns {string}
 */
export function conMoneda(ruta, moneda) {
  if (!moneda || moneda === MONEDA_BASE || !MONEDAS.includes(moneda)) {
    return ruta;
  }
  return `${ruta}${ruta.includes('?') ? '&' : '?'}moneda=${moneda}`;
}

/**
 * Pinta el selector: la moneda en uso y, desactivadas y dicho en el texto,
 * las que el servidor todavía no ofrece.
 *
 * @param {HTMLSelectElement|null} selector `#moneda-tienda`
 * @param {{actual: string, disponibles: ReadonlyArray<string>}} estado
 */
export function pintarSelectorDeMoneda(selector, { actual, disponibles }) {
  if (!selector) {
    return;
  }
  for (const opcion of Array.from(selector.options)) {
    const disponible = disponibles.includes(opcion.value);
    const nombre = NOMBRE_DE_MONEDA[opcion.value] ?? opcion.value;
    opcion.disabled = !disponible;
    opcion.textContent = disponible
      ? `${opcion.value} · ${nombre}`
      : `${opcion.value} · ${nombre} (no disponible)`;
  }
  selector.value = actual;
}

/**
 * «USD», «USD y EUR».
 *
 * @param {ReadonlyArray<string>} monedas
 * @returns {string}
 */
function enumerar(monedas) {
  return monedas.length > 1
    ? `${monedas.slice(0, -1).join(', ')} y ${monedas[monedas.length - 1]}`
    : (monedas[0] ?? '');
}

/**
 * La nota bajo el selector: por qué se ve esa moneda y qué falta.
 *
 * Una moneda que el servidor no ofrece es una tasa de cambio que la tienda no
 * tiene: la decisión del PO sin tomar (D-32) o admin-parametros sin
 * responder. La nota lo dice con esas palabras, sin nombrar parámetros ni
 * servicios, para que las opciones desactivadas no sean un misterio.
 *
 * @param {{actual: string, preferida: string|null, disponibles: ReadonlyArray<string>,
 *          porIdioma: boolean}} estado
 * @returns {string}
 */
export function notaDeMoneda({ actual, preferida, disponibles, porIdioma }) {
  const faltan = MONEDAS.filter((moneda) => !disponibles.includes(moneda));
  if (preferida && preferida !== actual && faltan.includes(preferida)) {
    return `Los precios en ${preferida} no están disponibles por ahora (la tienda aún no tiene su tasa de cambio): se muestran en ${actual}.`;
  }
  if (actual !== MONEDA_BASE && porIdioma) {
    return `Precios en ${actual} según el idioma de tu navegador. Puedes cambiarlo.`;
  }
  if (faltan.length > 0) {
    const verbo = faltan.length > 1 ? 'no están disponibles' : 'no está disponible';
    return `${enumerar(faltan)} ${verbo} por ahora: la tienda aún no tiene su tasa de cambio.`;
  }
  return '';
}
