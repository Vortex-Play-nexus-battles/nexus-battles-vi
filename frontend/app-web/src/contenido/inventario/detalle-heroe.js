/**
 * Lo que un heroe propio tiene de verdad: sus estadisticas y las acciones de su
 * prototipo — R5.
 *
 * ## Por que se anade aqui y no en una seccion nueva
 *
 * «Mi inventario» ya es la superficie de los heroes del jugador: la vitrina los
 * lista con su marco, y la ficha de detalle ya existia. Lo que faltaba no era
 * una pantalla, era el dato. La ficha de un heroe mostraba una sola linea
 * —«Prototipo: Guerrero Tanque»— mientras dos servicios publicaban, probado y
 * sin usar, todo lo demas.
 *
 * ## Las dos mitades no se mezclan
 *
 * Son datos de dueno distinto y se rotulan como tales:
 *
 *   * **Tus estadisticas** salen de `GET /inventario/heroes/{id}/estadisticas`.
 *     Son del jugador: el servicio parte de las del prototipo y les suma los
 *     efectos de lo que tenga equipado. Cambian al equipar.
 *   * **Las acciones y la descripcion** salen de `GET /heroes/{prototipo}` y
 *     son del CATALOGO. Iguales para todos los que tengan ese prototipo.
 *
 * Presentarlas juntas sin decir cual es cual seria ensenarle al jugador cifras
 * de otro, que es el error que el mapa de responsabilidad advierte.
 *
 * ## Lo que no se pinta
 *
 * **Nivel** y **rareza**. No existen: ningun servicio persiste el nivel de un
 * heroe —el catalogo lo acepta como parametro de ruta y dice expresamente que no
 * lo guarda; el documento del inventario no tiene la columna— y `rareza` no
 * aparece en ningun contrato de contenido. El kit tiene marcos de rareza y un
 * distintivo de nivel esperando: el dia que el contrato los publique, se pintan.
 * Hoy serian inventados.
 *
 * ## Si alguna de las dos llamadas falla
 *
 * La ficha no se cae. Cada mitad se pinta si llega y se omite si no, porque el
 * detalle del producto —que ya estaba ahi— sigue siendo util sin ellas. Un hueco
 * es honesto; un cero o un guion se leerian como «este heroe no tiene defensa».
 */

import { consultarEstadisticasDelHeroe } from './cliente-inventario.js';
import { consultarFichaDeHeroe } from './cliente-heroes.js';

/** Las tres cifras escalares, en el orden en que se leen. */
const ESTADISTICAS = Object.freeze([
  ['poder', 'Poder'],
  ['vida', 'Vida'],
  ['defensa', 'Defensa'],
]);

/** Y las tres que son formulas de dados. */
const FORMULAS = Object.freeze([
  ['ataque', 'Ataque'],
  ['dano', 'Daño'],
  ['sanar', 'Sanación'],
]);

/**
 * Una formula de dados, dicha como la escribiria una persona.
 *
 * El contrato la manda descompuesta (`{base, cantidadDados, caras}`) para no
 * obligar a nadie a parsear una cadena. Aqui se vuelve a juntar para leerla:
 * `10 + 1d6`, o solo `10` si no hay dados, o solo `1d6` si no hay base.
 *
 * @param {{base?: number, cantidadDados?: number, caras?: number}|null} formula
 * @returns {string|null} `null` si no hay nada que decir.
 */
export function formulaLegible(formula) {
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
  return conBase ?? dados;
}

/**
 * El bloque de estadisticas del heroe del jugador.
 *
 * @param {object} estadisticas respuesta de inventario.
 * @returns {HTMLElement|null} `null` si no trae ni una cifra utilizable.
 */
export function construirEstadisticas(estadisticas) {
  if (!estadisticas || typeof estadisticas !== 'object') {
    return null;
  }

  const lista = document.createElement('dl');
  lista.className = 'ficha__atributos';

  for (const [campo, etiqueta] of ESTADISTICAS) {
    const valor = estadisticas[campo];
    if (!Number.isFinite(valor)) {
      continue;
    }
    lista.append(...parDeDatos(etiqueta, String(valor)));
  }
  for (const [campo, etiqueta] of FORMULAS) {
    const legible = formulaLegible(estadisticas[campo]);
    if (legible === null) {
      continue;
    }
    lista.append(...parDeDatos(etiqueta, legible));
  }

  if (lista.children.length === 0) {
    return null;
  }
  return conTitulo('Tus estadísticas', 'Con lo que llevas equipado.', lista);
}

/**
 * El bloque del prototipo: lo que hace este tipo de heroe.
 *
 * @param {object} ficha respuesta del catalogo de heroes.
 * @returns {HTMLElement|null}
 */
export function construirAccionesDelPrototipo(ficha) {
  if (!ficha || typeof ficha !== 'object') {
    return null;
  }
  const acciones = Array.isArray(ficha.acciones) ? ficha.acciones : [];
  if (acciones.length === 0) {
    return null;
  }

  const lista = document.createElement('ul');
  lista.className = 'ficha__acciones';
  for (const accion of acciones) {
    if (!accion?.nombre) {
      continue;
    }
    const punto = document.createElement('li');
    const nombre = document.createElement('strong');
    nombre.textContent = accion.nombre;
    punto.append(nombre);
    // El costo lo compone el servidor como texto («2 puntos de poder»): se
    // muestra tal cual, sin volver a interpretarlo.
    if (accion.costo) {
      punto.append(document.createTextNode(` · ${accion.costo}`));
    }
    if (accion.efecto) {
      const efecto = document.createElement('p');
      efecto.className = 'ficha__accion-efecto';
      efecto.textContent = accion.efecto;
      punto.append(efecto);
    }
    lista.append(punto);
  }
  if (lista.children.length === 0) {
    return null;
  }

  const nota = ficha.esSanador === true ? 'Prototipo sanador.' : 'Iguales para este prototipo.';
  return conTitulo('Acciones', nota, lista);
}

/**
 * Pide las dos mitades y devuelve los bloques que se hayan podido construir.
 *
 * Las dos llamadas salen a la vez y ninguna hunde a la otra: se usa
 * `allSettled` a proposito, porque son de servicios distintos y el jugador no
 * tiene por que perder sus estadisticas si el catalogo esta caido.
 *
 * @param {object} opciones
 * @param {string} opciones.identidad
 * @param {string} opciones.heroeId elemento del inventario, tipo HEROE.
 * @param {string|null} [opciones.prototipo] nombre del catalogo, si se conoce.
 * @returns {Promise<Array<HTMLElement>>} en orden de lectura; vacio si nada llego.
 */
export async function construirDetalleDeHeroe({
  identidad,
  heroeId,
  prototipo = null,
  estadisticasDe = consultarEstadisticasDelHeroe,
  fichaDe = consultarFichaDeHeroe,
}) {
  const peticiones = [
    heroeId ? estadisticasDe(identidad, heroeId) : Promise.reject(new Error('sin heroe')),
    prototipo ? fichaDe(prototipo) : Promise.reject(new Error('sin prototipo')),
  ];
  const [estadisticas, ficha] = await Promise.allSettled(peticiones);

  const bloques = [];
  if (estadisticas.status === 'fulfilled') {
    const bloque = construirEstadisticas(estadisticas.value);
    if (bloque) {
      bloques.push(bloque);
    }
  }
  if (ficha.status === 'fulfilled') {
    const bloque = construirAccionesDelPrototipo(ficha.value);
    if (bloque) {
      bloques.push(bloque);
    }
  }
  return bloques;
}

/**
 * Una fila de la lista, con el mismo marcado que las filas de atributos de la
 * ficha (`ficha__atributo`, `ficha__etiqueta`, `ficha__valor`).
 *
 * Se repite ese marcado en vez de importarlo porque `ficha-producto.js` ya
 * importa este modulo, y pedirle el constructor de vuelta cerraria un ciclo.
 * Las clases son las mismas a proposito: asi estas filas se ven exactamente
 * igual que las de arriba y no hace falta CSS nuevo para ellas.
 */
function parDeDatos(etiqueta, valor) {
  const fila = document.createElement('div');
  fila.className = 'ficha__atributo';

  const termino = document.createElement('dt');
  termino.className = 'ficha__etiqueta';
  termino.textContent = etiqueta;

  const dato = document.createElement('dd');
  dato.className = 'ficha__valor';
  dato.textContent = valor;

  fila.append(termino, dato);
  return [fila];
}

function conTitulo(texto, nota, contenido) {
  const seccion = document.createElement('section');
  seccion.className = 'ficha__seccion';

  const titulo = document.createElement('h3');
  titulo.className = 'ficha__seccion-titulo';
  titulo.textContent = texto;
  seccion.append(titulo);

  if (nota) {
    const aclaracion = document.createElement('p');
    aclaracion.className = 'ficha__seccion-nota';
    aclaracion.textContent = nota;
    seccion.append(aclaracion);
  }

  seccion.append(contenido);
  return seccion;
}
