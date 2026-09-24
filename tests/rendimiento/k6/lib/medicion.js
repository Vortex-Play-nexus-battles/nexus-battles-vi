/**
 * Metricas propias, una serie por escenario mas una serie total.
 *
 * ## Por que metricas propias y no las de k6
 *
 * `http_req_duration` mide TODAS las peticiones del proceso, y la suite hace
 * tres cosas que no son la medicion: el login de comprobacion de `setup()`, y
 * la cancelacion de cada sala creada (limpieza). Si los umbrales colgaran de
 * `http_req_*`, el informe mezclaria trabajo de preparacion con la medicion y
 * el p95 diria algo que nadie pidio.
 *
 * Aqui cada peticion medida se anota explicitamente en la serie de su escenario
 * y en la serie total. Lo que no se anota, no cuenta. En la salida nativa de k6
 * se siguen viendo los `http_req_*` completos —son utiles para depurar—, pero
 * el informe y los umbrales se construyen solo con estas series.
 *
 * ## Que entra en la serie de latencia
 *
 * Entran todas las respuestas que llegaron, incluidas las 4xx y 5xx: una
 * respuesta de error tardo lo que tardo y forma parte de la distribucion. NO
 * entran los fallos de transporte (`status === 0`: conexion rechazada, DNS,
 * tiempo agotado sin respuesta), porque k6 les asigna una duracion que no es
 * una latencia de servicio y meterlos abarataria el p95 justo cuando el
 * entorno esta caido. Esos casos si cuentan como error y como peticion, asi
 * que aparecen en la tasa de error y en el throughput.
 *
 * @module lib/medicion
 */

import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

import { CLAVES_DE_ESCENARIO, CONFIG } from './entorno.js';

/**
 * Ficha de cada escenario: que mide, contra que ruta y por que requisito.
 * Viaja al informe para que el artefacto explique por si solo que se midio.
 *
 * @type {Record<string, {titulo: string, ruta: string, requisito: string, porQue: string}>}
 */
export const FICHAS = {
  login: {
    titulo: 'Inicio de sesion',
    ruta: 'POST /api/v1/auth/login',
    requisito: 'RNF-REN-001',
    porQue: 'Camino obligado de todo lo demas: si el login se degrada, se degrada todo.',
  },
  listar_salas: {
    titulo: 'Listado de salas',
    ruta: 'GET /api/v1/salas?pagina&tamano',
    requisito: 'RNF-REN-001 (RF-JUE-002)',
    porQue: 'La lectura mas frecuente del jugador y la primera pantalla del flujo de juego.',
  },
  crear_sala: {
    titulo: 'Creacion de sala',
    ruta: 'POST /api/v1/salas',
    requisito: 'RNF-REN-001 (RF-JUE-001)',
    porQue:
      'La escritura de referencia: valida sancion, consulta inventario por el heroe y persiste.',
  },
  busqueda_inventario: {
    titulo: 'Busqueda en inventario',
    ruta: 'GET /api/v1/inventario/elementos/busqueda?criterio&pagina',
    requisito: 'HU-REN-003 CA-01',
    porQue: 'La busqueda indexada que CA-01 exige dentro del objetivo de latencia.',
  },
};

/** Clave de la serie agregada. */
export const CLAVE_TOTAL = 'total';

/**
 * @typedef {Object} SerieDeEscenario
 * @property {Trend} latencia latencia de respuesta, en milisegundos
 * @property {Rate} errores fraccion de peticiones que no cumplieron sus comprobaciones
 * @property {Counter} peticiones peticiones medidas
 */

/**
 * @param {string} clave
 * @returns {SerieDeEscenario}
 */
function crearSerie(clave) {
  return {
    latencia: new Trend('latencia_' + clave, true),
    errores: new Rate('errores_' + clave),
    peticiones: new Counter('peticiones_' + clave),
  };
}

/** @type {Record<string, SerieDeEscenario>} */
export const SERIES = {};
for (const clave of CLAVES_DE_ESCENARIO) {
  SERIES[clave] = crearSerie(clave);
}
SERIES[CLAVE_TOTAL] = crearSerie(CLAVE_TOTAL);

/**
 * Anota una respuesta en la serie de su escenario y en la total.
 *
 * @param {string} clave clave del escenario, de `CLAVES_DE_ESCENARIO`
 * @param {import('k6/http').Response} respuesta respuesta de k6
 * @param {Record<string, function(import('k6/http').Response): boolean>} comprobaciones
 *        lo que tiene que cumplir la respuesta para no contar como error
 * @returns {boolean} si la respuesta paso todas las comprobaciones
 */
export function medir(clave, respuesta, comprobaciones) {
  const serie = SERIES[clave];
  const total = SERIES[CLAVE_TOTAL];

  const correcta = check(respuesta, comprobaciones, { escenario: clave });

  if (respuesta.status > 0) {
    serie.latencia.add(respuesta.timings.duration);
    total.latencia.add(respuesta.timings.duration);
  }

  serie.errores.add(!correcta);
  total.errores.add(!correcta);
  serie.peticiones.add(1);
  total.peticiones.add(1);

  return correcta;
}

/**
 * Pausa de reflexion entre iteraciones de un mismo usuario virtual.
 *
 * Sin pausa, cada VU vuelve a pedir en cuanto le contestan, que no es lo que
 * hace una persona y convierte cualquier perfil en una prueba de saturacion.
 * `PAUSA_MS=0` la desactiva; es lo que hace `smoke`, donde el objetivo es
 * terminar rapido y no parecerse a nadie.
 */
export function pausar() {
  if (CONFIG.pausaMs > 0) {
    sleep(CONFIG.pausaMs / 1000);
  }
}

/**
 * Lee un campo del cuerpo JSON sin reventar cuando la respuesta no es JSON.
 *
 * Importante para las comprobaciones: un 502 del borde devuelve HTML, y
 * `respuesta.json()` lanzaria dentro del `check`, convirtiendo un error
 * medible en un fallo de la propia suite.
 *
 * @param {import('k6/http').Response} respuesta
 * @param {string} campo
 * @returns {*} el valor, o `undefined` si no se pudo leer
 */
export function campoJson(respuesta, campo) {
  try {
    return respuesta.json(campo);
  } catch (_noEsJson) {
    return undefined;
  }
}
