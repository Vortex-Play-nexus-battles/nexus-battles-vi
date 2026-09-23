/**
 * Suite de rendimiento de Nexus Battles VI — punto de entrada unico.
 *
 * RNF-REN-001 (500 ms extremo a extremo), HU-REN-001 y HU-REN-003 CA-01.
 * El percentil de evaluacion es p95 por ADR-006.
 *
 * ## Por que un solo guion con `scenarios` y no un archivo por escenario
 *
 * Porque el informe tiene que dar los percentiles, la tasa de error y el
 * throughput POR ESCENARIO Y EN TOTAL, y `handleSummary` corre una vez por
 * proceso de k6. Con un archivo por escenario habria cuatro resumenes sueltos
 * y el total habria que calcularlo a mano fuera de k6 —o sea, un sitio mas
 * donde equivocarse—. Con un solo proceso, una corrida produce un `resumen.json`
 * que ya trae las cinco filas y se adjunta tal cual como evidencia de Sprint.
 *
 * De paso, la sesion se consigue una sola vez en `setup()` y los cuatro
 * escenarios la comparten, en vez de repetir el login cuatro veces.
 *
 * Los escenarios corren EN SERIE, escalonados con `startTime`. Si corrieran a
 * la vez, el p95 del listado incluiria la contencion que provoca la creacion de
 * salas y no se podria atribuir la latencia a nada. Cada numero describe su
 * operacion; el perfil de trafico mezclado es otro ejercicio.
 *
 * Uso:
 *   BASE_URL=... USUARIO=... CLAVE=... PERFIL=smoke \
 *     k6 run tests/rendimiento/k6/rendimiento.js
 *
 * Todas las variables, en `tests/rendimiento/README.md`.
 *
 * @module rendimiento
 */

import { CLAVES_DE_ESCENARIO, CONFIG, MARGEN_ENTRE_ESCENARIOS_SEGUNDOS } from './lib/entorno.js';
import { ESTADISTICOS, construirInforme } from './lib/informe.js';
import { CLAVE_TOTAL } from './lib/medicion.js';
import { prepararSesion } from './lib/sesion.js';

import { medirLogin } from './escenarios/autenticacion.js';
import { medirBusquedaDeInventario } from './escenarios/inventario.js';
import { medirCreacionDeSala, medirListadoDeSalas } from './escenarios/salas.js';

// ---------------------------------------------------------------------------
// Funciones que ejecuta k6, una por escenario.
//
// Son envoltorios de una linea a proposito: `scenarios[].exec` referencia una
// funcion EXPORTADA DE ESTE ARCHIVO por su nombre, asi que la implementacion
// vive en `escenarios/` y aqui solo queda el punto de entrada.
// ---------------------------------------------------------------------------

/** @param {{token: string}} _sesion */
export function escenarioLogin(_sesion) {
  medirLogin();
}

/** @param {{token: string}} sesion */
export function escenarioListarSalas(sesion) {
  medirListadoDeSalas(sesion);
}

/** @param {{token: string}} sesion */
export function escenarioCrearSala(sesion) {
  medirCreacionDeSala(sesion);
}

/** @param {{token: string}} sesion */
export function escenarioBusquedaInventario(sesion) {
  medirBusquedaDeInventario(sesion);
}

/** Que funcion ejecuta cada escenario, en el orden en que corren. */
const EJECUTORES = {
  login: 'escenarioLogin',
  listar_salas: 'escenarioListarSalas',
  crear_sala: 'escenarioCrearSala',
  busqueda_inventario: 'escenarioBusquedaInventario',
};

/**
 * Un escenario de k6, colocado detras del anterior.
 *
 * @param {number} indice posicion en la serie, desde 0
 * @param {string} clave clave del escenario
 * @returns {Object} definicion para `options.scenarios`
 */
function enSerie(indice, clave) {
  const paso = CONFIG.duracionSegundos + MARGEN_ENTRE_ESCENARIOS_SEGUNDOS;
  return {
    executor: 'constant-vus',
    exec: EJECUTORES[clave],
    vus: CONFIG.vus,
    duration: CONFIG.duracionSegundos + 's',
    startTime: indice * paso + 's',
    // Margen para que las peticiones en vuelo terminen y se cuenten, en vez de
    // aparecer como errores de la suite al cortar el escenario.
    gracefulStop: MARGEN_ENTRE_ESCENARIOS_SEGUNDOS + 's',
    tags: { escenario: clave },
  };
}

/** Umbrales: los mismos para cada escenario y para el total. */
function umbrales() {
  const definidos = {};
  const claves = CLAVES_DE_ESCENARIO.concat([CLAVE_TOTAL]);
  for (const clave of claves) {
    definidos['latencia_' + clave] = ['p(' + CONFIG.percentil + ')<=' + CONFIG.objetivoMs];
    definidos['errores_' + clave] = ['rate<=' + CONFIG.tasaErrorMaxima];
  }
  return definidos;
}

const escenarios = {};
CLAVES_DE_ESCENARIO.forEach(function (clave, indice) {
  escenarios[clave] = enSerie(indice, clave);
});

export const options = {
  scenarios: escenarios,
  thresholds: umbrales(),
  // Sin estos, el resumen que recibe `handleSummary` no trae p50 ni p99 y el
  // informe no podria darlos.
  summaryTrendStats: ESTADISTICOS,
  summaryTimeUnit: 'ms',
  // La suite comprueba el cuerpo de las respuestas (que el listado trae
  // `contenido`, que el login trae `token`), asi que no se pueden descartar.
  discardResponseBodies: false,
};

/**
 * Comprueba el entorno y deja la sesion lista. Lo que devuelve llega a cada
 * escenario como primer argumento.
 *
 * @returns {{token: string}}
 */
export function setup() {
  return prepararSesion();
}

/**
 * Escribe el informe. `stdout` para quien mire la consola, y los dos archivos
 * para el artefacto del workflow.
 *
 * @param {*} datos resumen de k6
 * @returns {Record<string, string>}
 */
export function handleSummary(datos) {
  const informe = construirInforme(datos);

  const salida = {};
  salida.stdout = '\n' + informe.texto + '\n';
  salida[CONFIG.resumenTxt] = informe.texto + '\n';
  salida[CONFIG.resumenJson] = informe.json + '\n';
  return salida;
}
