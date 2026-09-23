/**
 * Configuracion de la suite de rendimiento — RNF-REN-001, HU-REN-001, HU-REN-003.
 *
 * Todo lo que distingue una corrida de otra —contra que entorno, con que
 * cuenta, con cuanta carga y contra que umbral— entra por variable de entorno
 * (`__ENV`). En este archivo no hay ni una URL de un entorno real ni una
 * credencial: la regla 10 de plataforma dice «configuracion solo por variable
 * de entorno, ningun valor real versionado», y una suite de carga es codigo del
 * repositorio como cualquier otro.
 *
 * Las tres obligatorias NO tienen valor por omision, y es deliberado. Un
 * `BASE_URL` con `localhost` de reserva hace que una corrida mal invocada mida
 * un entorno que no es el que se queria medir y entregue un informe que parece
 * bueno; un usuario de reserva convierte el fallo en un 401 a mitad de corrida,
 * que es justo el sintoma confuso que se queria evitar. Aqui falta algo y el
 * proceso se detiene antes de la primera peticion, diciendo cual.
 *
 * @module lib/entorno
 */

import { PERFILES, TECHO_DE_VUS_POR_OMISION } from './perfiles.js';

/** Escenarios de la suite, en el orden en que se ejecutan. */
export const CLAVES_DE_ESCENARIO = ['login', 'listar_salas', 'crear_sala', 'busqueda_inventario'];

/**
 * Lee una variable obligatoria o detiene la corrida con un mensaje util.
 *
 * @param {string} nombre nombre de la variable de entorno
 * @param {string} ejemplo valor de ejemplo, para que el mensaje sirva de receta
 * @returns {string} el valor, ya sin espacios alrededor
 */
function obligatoria(nombre, ejemplo) {
  const valor = String(__ENV[nombre] === undefined ? '' : __ENV[nombre]).trim();
  if (valor === '') {
    throw new Error(
      'Falta la variable de entorno obligatoria ' +
        nombre +
        '.\n' +
        '  Ejemplo: ' +
        nombre +
        '=' +
        ejemplo +
        '\n' +
        '  Las tres obligatorias son BASE_URL, USUARIO y CLAVE. Ninguna tiene valor\n' +
        '  por omision a proposito: ver tests/rendimiento/README.md.',
    );
  }
  return valor;
}

/**
 * Lee una variable opcional de texto.
 *
 * @param {string} nombre
 * @param {string} porOmision
 * @returns {string}
 */
function texto(nombre, porOmision) {
  const valor = String(__ENV[nombre] === undefined ? '' : __ENV[nombre]).trim();
  return valor === '' ? porOmision : valor;
}

/**
 * Lee una variable opcional numerica y comprueba que sea un numero de verdad.
 *
 * @param {string} nombre
 * @param {number} porOmision
 * @returns {number}
 */
function numero(nombre, porOmision) {
  const crudo = String(__ENV[nombre] === undefined ? '' : __ENV[nombre]).trim();
  if (crudo === '') {
    return porOmision;
  }
  const valor = Number(crudo);
  if (!isFinite(valor)) {
    throw new Error('La variable ' + nombre + ' tiene que ser un numero; llego "' + crudo + '".');
  }
  return valor;
}

/**
 * Lee una variable opcional de si/no.
 *
 * @param {string} nombre
 * @param {boolean} porOmision
 * @returns {boolean}
 */
function bandera(nombre, porOmision) {
  const crudo = texto(nombre, porOmision ? 'true' : 'false').toLowerCase();
  return crudo === 'true' || crudo === '1' || crudo === 'si';
}

const nombrePerfil = texto('PERFIL', 'smoke');
const perfil = PERFILES[nombrePerfil];
if (!perfil) {
  throw new Error(
    'PERFIL="' +
      nombrePerfil +
      '" no existe. Los perfiles son: ' +
      Object.keys(PERFILES).join(', ') +
      '.',
  );
}

const vus = Math.round(numero('VUS', perfil.vus));
const techoDeVus = Math.round(numero('TECHO_VUS', TECHO_DE_VUS_POR_OMISION));
if (vus > techoDeVus) {
  // El techo es una decision de ingenieria, no una limitacion de k6: el host de
  // DEV es un t3.small (2 vCPU / 2 GiB) que corre nueve servicios. Se puede
  // subir, pero a proposito y sabiendo contra que se esta disparando.
  throw new Error(
    'VUS=' +
      vus +
      ' supera el techo de ' +
      techoDeVus +
      ' usuarios virtuales.\n' +
      '  El host de DEV es un t3.small (2 vCPU / 2 GiB) con nueve servicios encima:\n' +
      '  pasar de ahi no mide latencia, tumba el entorno de la demo. Si de verdad\n' +
      '  hace falta, subir el techo explicitamente con TECHO_VUS y avisar al equipo.\n' +
      '  Ver tests/rendimiento/README.md, seccion «El techo de carga».',
  );
}
if (vus < 1) {
  throw new Error('VUS tiene que ser al menos 1; llego ' + vus + '.');
}

const duracionSegundos = Math.round(numero('DURACION', perfil.duracionSegundos));
if (duracionSegundos < 1) {
  throw new Error(
    'DURACION (en segundos) tiene que ser al menos 1; llego ' + duracionSegundos + '.',
  );
}

/** Margen entre escenarios, para que el anterior cierre antes de que arranque el siguiente. */
export const MARGEN_ENTRE_ESCENARIOS_SEGUNDOS = 5;

const percentil = Math.round(numero('PERCENTIL', 95));
if (percentil < 1 || percentil > 99) {
  throw new Error('PERCENTIL tiene que estar entre 1 y 99; llego ' + percentil + '.');
}

/**
 * Configuracion efectiva de la corrida.
 *
 * @type {{
 *   baseUrl: string,
 *   usuario: string,
 *   clave: string,
 *   perfil: string,
 *   vus: number,
 *   techoDeVus: number,
 *   duracionSegundos: number,
 *   duracionTotalSegundos: number,
 *   pausaMs: number,
 *   objetivoMs: number,
 *   percentil: number,
 *   tasaErrorMaxima: number,
 *   tamanoPagina: number,
 *   paginas: number,
 *   criterioBusqueda: string,
 *   limpiarSalas: boolean,
 *   resumenTxt: string,
 *   resumenJson: string
 * }}
 */
export const CONFIG = {
  // Base del BORDE, no la de un servicio suelto: se mide lo que ve el jugador,
  // que pasa por nginx (RNF-REN-001 habla de latencia extremo a extremo).
  baseUrl: obligatoria('BASE_URL', 'http://localhost:8099').replace(/\/+$/, ''),
  usuario: obligatoria('USUARIO', 'anfitriona_e2e@nexus.test'),
  clave: obligatoria('CLAVE', 'la-clave-de-esa-cuenta'),

  perfil: nombrePerfil,
  vus: vus,
  techoDeVus: techoDeVus,
  duracionSegundos: duracionSegundos,
  duracionTotalSegundos: duracionSegundos * CLAVES_DE_ESCENARIO.length,
  pausaMs: Math.round(numero('PAUSA_MS', perfil.pausaMs)),

  // RNF-REN-001 fija los 500 ms; ADR-006 fija que se comprueban en p95.
  // Configurables para una campana concreta, nunca por omision.
  objetivoMs: numero('OBJETIVO_MS', 500),
  percentil: percentil,
  tasaErrorMaxima: numero('TASA_ERROR_MAXIMA', 0.01),

  // 16 por pagina es el valor del sistema de diseno (RNF-USA-001).
  tamanoPagina: Math.round(numero('TAMANO_PAGINA', 16)),
  // Se rota entre varias paginas para no medir siempre la misma consulta
  // calentita: una busqueda indexada (HU-REN-003) se mide recorriendo, no
  // repitiendo la pagina 0.
  paginas: Math.max(1, Math.round(numero('PAGINAS', 3))),
  // `prueba` y no `a`: `BuscarElementosInventario` exige MINIMO_CARACTERES = 4
  // y responde 400 con menos. La primera corrida de esta suite contra el banco
  // E2E real salio con `a` y dio 100 % de error en los 20 433 intentos del
  // escenario, con los otros tres en verde — que es exactamente para lo que
  // sirve correrla contra servicios de verdad. Ademas `prueba` casa con lo que
  // siembra `tests/e2e/sembrar.sh` («Guerrero de prueba»), asi que la busqueda
  // devuelve filas y mide el camino indexado y no el del resultado vacio.
  criterioBusqueda: texto('CRITERIO_BUSQUEDA', 'prueba'),

  // La sala creada se cancela inmediatamente despues de medirla. Sin esto, una
  // corrida `load` deja miles de salas abiertas en el listado del entorno.
  limpiarSalas: bandera('LIMPIAR_SALAS', true),

  resumenTxt: texto('RESUMEN_TXT', 'resumen.txt'),
  resumenJson: texto('RESUMEN_JSON', 'resumen.json'),
};

/** Cabeceras JSON con el token de sesion. */
export function cabecerasCon(token) {
  return {
    Authorization: 'Bearer ' + token,
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
}
