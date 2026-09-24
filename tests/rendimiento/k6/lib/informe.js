/**
 * El informe: un texto legible y un `resumen.json` para adjuntar como evidencia.
 *
 * k6 trae su propio resumen, pero no sirve como evidencia de Sprint: no separa
 * por escenario mas que a base de sub-metricas, no da p50 ni p99 sin
 * configurarlo, y sobre todo no dice contra que requisito se comparo cada
 * numero. Lo que pide el Definition of Done es un informe con trazabilidad
 * («i) Trazabilidad y documentacion tecnica actualizadas»), asi que el informe
 * lleva, escenario a escenario, la ruta medida y el requisito que la justifica.
 *
 * El veredicto se calcula aqui a partir de los mismos numeros que ve el lector,
 * no leyendo el resultado de los `thresholds` de k6. Son dos caminos
 * independientes hacia la misma conclusion: si algun dia divergen, es que uno
 * de los dos esta mal y se nota. El codigo de salida del proceso lo siguen
 * decidiendo los `thresholds` —eso es lo que pone roja la compuerta de CI—.
 *
 * @module lib/informe
 */

import { CLAVES_DE_ESCENARIO, CONFIG } from './entorno.js';
import { CLAVE_TOTAL, FICHAS } from './medicion.js';

/** Estadisticos que se piden a k6 y que salen en el informe. */
export const ESTADISTICOS = ['avg', 'min', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'];

/**
 * Lo que este informe NO mide. Viaja tambien en el JSON: un artefacto de
 * evidencia que no dice donde no llega invita a leerlo de mas.
 */
export const LIMITACIONES = [
  'Mide desde el cliente de k6, por el borde nginx. Incluye la red entre el runner y el entorno: una corrida desde GitHub Actions contra AWS mide tambien Internet.',
  'No cubre el canal en tiempo real (STOMP sobre WebSocket). Ver la seccion «La accion de partida» del README.',
  'Los percentiles salen de las muestras de ESTA corrida y de este unico proceso de k6. No se agregan con las de otra replica ni con el registro en memoria de metricas-plataforma.',
  'Los escenarios corren en serie, no a la vez: cada numero describe su operacion sin contencion de las otras. No es el perfil de trafico de un dia real.',
  'El throughput por escenario es peticiones medidas / duracion configurada del escenario, no un maximo alcanzable: con el modelo cerrado, el ritmo lo marca el propio servidor.',
  'La latencia solo incluye respuestas que llegaron. Un fallo de transporte (status 0) cuenta como error y como peticion, pero no como muestra de latencia.',
];

/**
 * @param {*} datos resumen de k6
 * @param {string} nombre nombre de la metrica
 * @returns {Record<string, number>} sus valores, u objeto vacio si no hay
 */
function valores(datos, nombre) {
  const metrica = datos.metrics ? datos.metrics[nombre] : undefined;
  return metrica && metrica.values ? metrica.values : {};
}

/**
 * Recoge todo lo que hay que decir de un escenario.
 *
 * @param {*} datos resumen de k6
 * @param {string} clave clave del escenario o `total`
 * @param {number} ventanaSegundos segundos sobre los que se calcula el throughput
 * @returns {Object} fila del informe
 */
function filaDe(datos, clave, ventanaSegundos) {
  const latencia = valores(datos, 'latencia_' + clave);
  const errores = valores(datos, 'errores_' + clave);
  const peticiones = valores(datos, 'peticiones_' + clave);

  const muestras = latencia.count || 0;
  const total = peticiones.count || 0;
  const tasaError = errores.rate === undefined ? 0 : errores.rate;
  const percentil = latencia['p(' + CONFIG.percentil + ')'];

  let veredicto;
  if (total === 0) {
    veredicto = 'SIN DATOS';
  } else if (percentil === undefined) {
    // Hubo peticiones pero ninguna respuesta: el entorno no contesto.
    veredicto = 'NO CUMPLE';
  } else {
    const latenciaOk = percentil <= CONFIG.objetivoMs;
    const erroresOk = tasaError <= CONFIG.tasaErrorMaxima;
    veredicto = latenciaOk && erroresOk ? 'CUMPLE' : 'NO CUMPLE';
  }

  return {
    clave: clave,
    peticiones: total,
    muestrasDeLatencia: muestras,
    latenciaMs: {
      media: latencia.avg,
      min: latencia.min,
      p50: latencia['p(50)'],
      p90: latencia['p(90)'],
      p95: latencia['p(95)'],
      p99: latencia['p(99)'],
      max: latencia.max,
    },
    percentilDeEvaluacion: percentil,
    tasaError: tasaError,
    peticionesPorSegundo: ventanaSegundos > 0 ? total / ventanaSegundos : 0,
    veredicto: veredicto,
  };
}

/** Numero con decimales fijos, o un guion si no hay dato. */
function num(valor, decimales) {
  if (valor === undefined || valor === null || !isFinite(valor)) {
    return '-';
  }
  return valor.toFixed(decimales === undefined ? 1 : decimales);
}

/** Porcentaje con dos decimales. */
function porcentaje(valor) {
  if (valor === undefined || valor === null || !isFinite(valor)) {
    return '-';
  }
  return (valor * 100).toFixed(2) + ' %';
}

/** Rellena por la derecha hasta `ancho`. */
function izq(texto, ancho) {
  let salida = String(texto);
  while (salida.length < ancho) {
    salida = salida + ' ';
  }
  return salida;
}

/** Rellena por la izquierda hasta `ancho`. */
function der(texto, ancho) {
  let salida = String(texto);
  while (salida.length < ancho) {
    salida = ' ' + salida;
  }
  return salida;
}

/** Una linea de la tabla. */
function lineaDeTabla(etiqueta, fila) {
  return (
    izq(etiqueta, 22) +
    der(fila.peticiones, 11) +
    der(num(fila.latenciaMs.p50), 9) +
    der(num(fila.latenciaMs.p90), 9) +
    der(num(fila.latenciaMs.p95), 9) +
    der(num(fila.latenciaMs.p99), 9) +
    der(porcentaje(fila.tasaError), 11) +
    der(num(fila.peticionesPorSegundo, 2), 10) +
    '  ' +
    fila.veredicto
  );
}

/**
 * Construye el informe completo.
 *
 * @param {*} datos resumen que entrega k6 a `handleSummary`
 * @returns {{texto: string, json: string, cumple: boolean}}
 */
export function construirInforme(datos) {
  const duracionRealSegundos =
    datos.state && datos.state.testRunDurationMs
      ? datos.state.testRunDurationMs / 1000
      : CONFIG.duracionTotalSegundos;

  const filas = {};
  for (const clave of CLAVES_DE_ESCENARIO) {
    filas[clave] = filaDe(datos, clave, CONFIG.duracionSegundos);
  }
  const total = filaDe(datos, CLAVE_TOTAL, duracionRealSegundos);

  const cumple =
    total.veredicto === 'CUMPLE' &&
    CLAVES_DE_ESCENARIO.every((clave) => filas[clave].veredicto === 'CUMPLE');

  const ancho = 100;
  const raya = new Array(ancho + 1).join('=');
  const guion = new Array(ancho + 1).join('-');

  const lineas = [];
  lineas.push(raya);
  lineas.push('  NEXUS BATTLES VI — Informe de rendimiento (k6)');
  lineas.push(raya);
  lineas.push('  Perfil ................ ' + CONFIG.perfil);
  lineas.push('  Entorno ............... ' + CONFIG.baseUrl);
  lineas.push('  Cuenta ................ ' + CONFIG.usuario);
  lineas.push(
    '  Objetivo .............. p' +
      CONFIG.percentil +
      ' <= ' +
      CONFIG.objetivoMs +
      ' ms   (RNF-REN-001; el percentil, por ADR-006)',
  );
  lineas.push('  Tasa de error maxima .. ' + porcentaje(CONFIG.tasaErrorMaxima));
  lineas.push(
    '  Carga ................. ' +
      CONFIG.vus +
      ' VU · ' +
      CONFIG.duracionSegundos +
      ' s por escenario · ' +
      CLAVES_DE_ESCENARIO.length +
      ' escenarios en serie · pausa ' +
      CONFIG.pausaMs +
      ' ms',
  );
  lineas.push('  Duracion real ......... ' + num(duracionRealSegundos, 1) + ' s');
  lineas.push('');
  lineas.push(
    izq('Escenario', 22) +
      der('Peticiones', 11) +
      der('p50', 9) +
      der('p90', 9) +
      der('p95', 9) +
      der('p99', 9) +
      der('Errores', 11) +
      der('pet/s', 10) +
      '  Veredicto',
  );
  lineas.push(
    izq('', 22) + der('', 11) + der('(ms)', 9) + der('(ms)', 9) + der('(ms)', 9) + der('(ms)', 9),
  );
  lineas.push(guion);
  for (const clave of CLAVES_DE_ESCENARIO) {
    lineas.push(lineaDeTabla(clave, filas[clave]));
  }
  lineas.push(guion);
  lineas.push(lineaDeTabla('TOTAL', total));
  lineas.push('');

  lineas.push('Que mide cada escenario');
  lineas.push(guion);
  for (const clave of CLAVES_DE_ESCENARIO) {
    const ficha = FICHAS[clave];
    lineas.push('  ' + izq(clave, 22) + ficha.ruta);
    lineas.push('  ' + izq('', 22) + ficha.requisito + ' — ' + ficha.porQue);
  }
  lineas.push('');

  lineas.push('Que NO mide este informe');
  lineas.push(guion);
  for (const limitacion of LIMITACIONES) {
    lineas.push('  - ' + limitacion);
  }
  lineas.push('');
  lineas.push(guion);
  lineas.push(
    '  VEREDICTO GLOBAL: ' +
      (cumple ? 'CUMPLE' : 'NO CUMPLE') +
      '  (p' +
      CONFIG.percentil +
      ' <= ' +
      CONFIG.objetivoMs +
      ' ms y errores <= ' +
      porcentaje(CONFIG.tasaErrorMaxima) +
      ' en todos los escenarios)',
  );
  if (CONFIG.perfil === 'smoke') {
    lineas.push('  AVISO: el perfil `smoke` comprueba que la suite corre. Con 1 VU no hay');
    lineas.push(
      '         muestras suficientes para un p95 publicable: para evidencia, `baseline`.',
    );
  }
  lineas.push(raya);
  lineas.push('');

  const json = {
    generadoEn: new Date().toISOString(),
    perfil: CONFIG.perfil,
    entorno: CONFIG.baseUrl,
    cuenta: CONFIG.usuario,
    objetivo: {
      percentil: CONFIG.percentil,
      latenciaMaximaMs: CONFIG.objetivoMs,
      tasaErrorMaxima: CONFIG.tasaErrorMaxima,
      origen: 'RNF-REN-001 (500 ms) + ADR-006 (el percentil es p95)',
    },
    carga: {
      usuariosVirtuales: CONFIG.vus,
      techoDeUsuariosVirtuales: CONFIG.techoDeVus,
      duracionSegundosPorEscenario: CONFIG.duracionSegundos,
      escenariosEnSerie: CLAVES_DE_ESCENARIO.length,
      pausaMs: CONFIG.pausaMs,
      duracionRealSegundos: duracionRealSegundos,
      modelo: 'cerrado (constant-vus)',
    },
    escenarios: {},
    total: total,
    veredictoGlobal: cumple ? 'CUMPLE' : 'NO CUMPLE',
    limitaciones: LIMITACIONES,
  };
  for (const clave of CLAVES_DE_ESCENARIO) {
    json.escenarios[clave] = Object.assign({}, filas[clave], {
      ruta: FICHAS[clave].ruta,
      requisito: FICHAS[clave].requisito,
      porQue: FICHAS[clave].porQue,
    });
  }

  return {
    texto: lineas.join('\n'),
    json: JSON.stringify(json, null, 2),
    cumple: cumple,
  };
}
