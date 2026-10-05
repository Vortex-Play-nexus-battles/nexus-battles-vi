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
import { consultarFichaDeHeroe, consultarVistaPorNivel } from './cliente-heroes.js';
import { bloqueDeEstadisticas } from '../../comun/ui/juego/estadisticas.js';
import { icono } from '../../comun/ui/icono.js';

/**
 * Una formula de dados, dicha como la escribiria una persona. Vive ahora en
 * el bloque de estadisticas compartido (UXC-1); se reexporta para quien ya la
 * importaba de aqui.
 */
export { formulaLegible } from '../../comun/ui/juego/estadisticas.js';

/**
 * El bloque de estadisticas del heroe del jugador.
 *
 * @param {object} estadisticas respuesta de inventario.
 * @returns {HTMLElement|null} `null` si no trae ni una cifra utilizable.
 */
export function construirEstadisticas(estadisticas) {
  // UXC-1 — el bloque compartido (StatBlock): el mismo que la carta del heroe
  // y la cabecera del panel de equipamiento. Lo ausente no se pinta.
  const bloque = bloqueDeEstadisticas(estadisticas);
  if (!bloque) {
    return null;
  }
  return conTitulo('Tus estadísticas', 'Con lo que llevas equipado.', bloque);
}

/**
 * El bloque del prototipo: lo que hace este tipo de heroe.
 *
 * @param {object} ficha respuesta del catalogo de heroes.
 * @returns {HTMLElement|null}
 */
export function construirAccionesDelPrototipo(ficha, { aprendidas = null } = {}) {
  if (!ficha || typeof ficha !== 'object') {
    return null;
  }
  // UXC-9 — con la vista por nivel, cuáles sabe ya este héroe (las demás se
  // desbloquean al subir de nivel; el catálogo no las da todas desde el 1).
  const sabe = Array.isArray(aprendidas) ? new Set(aprendidas.map((a) => a?.nombre)) : null;
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
    // UXC-1 — cada accion como carta: nombre, lo que cuesta, lo que hace y su
    // carga. El texto del coste es del catalogo y se muestra tal cual; el
    // numero solo se destaca si el texto lo trae.
    const punto = document.createElement('li');
    punto.className = 'ficha__accion';
    const bloqueada = sabe !== null && !sabe.has(accion.nombre);
    if (bloqueada) {
      punto.classList.add('ficha__accion--bloqueada');
    }
    const cabeza = document.createElement('p');
    cabeza.className = 'ficha__accion-cabeza';
    cabeza.append(icono('rayo', { clase: 'icono ficha__accion-icono', etiqueta: null }));
    const nombre = document.createElement('strong');
    nombre.textContent = accion.nombre;
    cabeza.append(nombre);
    punto.append(cabeza);

    const datos = document.createElement('p');
    datos.className = 'ficha__accion-datos';
    if (accion.costo) {
      const coste = document.createElement('span');
      coste.className = 'ficha__accion-coste';
      coste.textContent = accion.costo;
      datos.append(coste);
    }
    // §6.1.2: «estas acciones solo aplican en el turno, tiene un turno de
    // carga». Es regla del documento, igual para las tres.
    const carga = document.createElement('span');
    carga.className = 'ficha__accion-carga';
    carga.append(icono('reloj', { clase: 'icono', etiqueta: null }));
    carga.append(document.createTextNode('1 turno de carga'));
    datos.append(carga);
    punto.append(datos);

    if (accion.efecto) {
      const efecto = document.createElement('p');
      efecto.className = 'ficha__accion-efecto';
      efecto.textContent = accion.efecto;
      punto.append(efecto);
    }
    if (bloqueada) {
      const aviso = document.createElement('p');
      aviso.className = 'ficha__accion-bloqueo';
      aviso.append(icono('candado', { clase: 'icono', etiqueta: null }));
      aviso.append(
        document.createTextNode('Todavía no la aprendió: se desbloquea al subir de nivel.'),
      );
      punto.append(aviso);
    }
    lista.append(punto);
  }
  if (lista.children.length === 0) {
    return null;
  }

  const nota =
    ficha.esSanador === true
      ? 'Prototipo sanador. Cada acción gasta poder y el poder se recupera 2 puntos por turno durante el combate.'
      : 'Iguales para este prototipo. Cada acción gasta poder y el poder se recupera 2 puntos por turno durante el combate.';
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
  nivel = null,
  estadisticasDe = consultarEstadisticasDelHeroe,
  fichaDe = consultarFichaDeHeroe,
  vistaDe = consultarVistaPorNivel,
}) {
  const peticiones = [
    heroeId ? estadisticasDe(identidad, heroeId) : Promise.reject(new Error('sin heroe')),
    prototipo ? fichaDe(prototipo) : Promise.reject(new Error('sin prototipo')),
    // UXC-9 — con el nivel del inventario, el prototipo en ese nivel.
    prototipo && Number.isInteger(nivel)
      ? vistaDe(prototipo, nivel)
      : Promise.reject(new Error('sin nivel')),
  ];
  const [estadisticas, ficha, vista] = await Promise.allSettled(peticiones);
  const enSuNivel = vista.status === 'fulfilled' ? vista.value : null;

  const bloques = [];
  if (estadisticas.status === 'fulfilled') {
    const bloque = construirEstadisticas(estadisticas.value);
    if (bloque) {
      bloques.push(bloque);
    }
  }
  if (ficha.status === 'fulfilled') {
    const bloque = construirAccionesDelPrototipo(ficha.value, {
      aprendidas: enSuNivel?.accionesDisponibles ?? null,
    });
    if (bloque) {
      bloques.push(bloque);
    }
  }
  const epica = construirEpicaAfin(enSuNivel?.epica);
  if (epica) {
    bloques.push(epica);
  }
  return bloques;
}

/**
 * UXC-9 — la épica afín del prototipo (§6.1.2, Tabla 20; `EpicaVista` de
 * heroes.yaml): no gasta poder y tiene su recarga; para este héroe, además,
 * su efecto potenciado.
 *
 * @param {{nombre: string, efectoGeneral?: string, efectoPotenciado?: string,
 *   turnosDeRecarga?: number}|null|undefined} epica
 * @returns {HTMLElement|null}
 */
export function construirEpicaAfin(epica) {
  if (!epica?.nombre) {
    return null;
  }
  const carta = document.createElement('div');
  carta.className = 'ficha__epica';
  const cabeza = document.createElement('p');
  cabeza.className = 'ficha__accion-cabeza';
  cabeza.append(icono('estrella-llena', { clase: 'icono ficha__accion-icono', etiqueta: null }));
  const nombre = document.createElement('strong');
  nombre.textContent = epica.nombre;
  cabeza.append(nombre);
  carta.append(cabeza);

  const datos = document.createElement('p');
  datos.className = 'ficha__accion-datos';
  const coste = document.createElement('span');
  coste.className = 'ficha__accion-coste';
  coste.textContent = 'Sin coste de poder';
  datos.append(coste);
  if (Number.isInteger(epica.turnosDeRecarga)) {
    const recarga = document.createElement('span');
    recarga.className = 'ficha__accion-carga';
    recarga.append(icono('reloj', { clase: 'icono', etiqueta: null }));
    recarga.append(
      document.createTextNode(
        `${epica.turnosDeRecarga} ${epica.turnosDeRecarga === 1 ? 'turno' : 'turnos'} de recarga`,
      ),
    );
    datos.append(recarga);
  }
  carta.append(datos);

  for (const [clase, texto] of [
    ['ficha__accion-efecto', epica.efectoGeneral],
    [
      'ficha__accion-efecto',
      epica.efectoPotenciado ? `Para este héroe: ${epica.efectoPotenciado}` : null,
    ],
  ]) {
    if (texto) {
      const parrafo = document.createElement('p');
      parrafo.className = clase;
      parrafo.textContent = texto;
      carta.append(parrafo);
    }
  }
  return conTitulo('Épica afín', 'La épica que mejor le va a este prototipo.', carta);
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
