/**
 * Acceso HTTP de la consola de control integral.
 *
 * ## La regla que impone este modulo
 *
 * Un panel administrativo que inventa un numero es peor que un panel vacio:
 * quien opera el sistema toma decisiones con lo que ve. Aqui no hay datos de
 * ejemplo, ni valores por defecto que parezcan reales, ni un cero donde en
 * realidad no se pudo preguntar. Toda consulta termina en uno de seis
 * desenlaces explicitos y el panel pinta exactamente ese desenlace.
 *
 * `consultar()` **nunca lanza**. Un fallo de un servicio no puede tumbar la
 * consola entera: cada seccion se dibuja con lo que consiguio y dice que le
 * falto. Esa es la diferencia entre una consola que sigue sirviendo con medio
 * sistema caido y una pantalla en blanco.
 *
 * @module plataforma/consola/cliente-consola
 */

import { rutaDeApi } from '../../comun/base-api.js';
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

/**
 * Los seis desenlaces posibles de una consulta administrativa.
 *
 * Son los que el mandato exige distinguir: un modulo que no existe todavia no
 * es lo mismo que un servicio caido, y ninguno de los dos es un cero.
 */
export const RESULTADO = Object.freeze({
  /** El servicio respondio y hay datos. */
  DATOS: 'DATOS',
  /** El servicio respondio, pero no hay nada que mostrar. */
  VACIO: 'VACIO',
  /** 401 o 403: la sesion o el rol no alcanzan. No es un fallo del servicio. */
  SIN_PERMISO: 'SIN_PERMISO',
  /** 404: el backend esta vivo pero no publica esa ruta. */
  NO_IMPLEMENTADO: 'NO_IMPLEMENTADO',
  /** 502, 503, 504 o red caida: el servicio no esta atendiendo. */
  SERVICIO_DEGRADADO: 'SERVICIO_DEGRADADO',
  /** Cualquier otra cosa. Se dice el codigo y no se interpreta de mas. */
  NO_DISPONIBLE: 'NO_DISPONIBLE',
});

/** Codigos que significan "el servicio no esta atendiendo", no "dijo que no". */
const CAIDO = Object.freeze([500, 502, 503, 504]);

/**
 * Una consulta administrativa ya interpretada.
 *
 * @typedef {object} Desenlace
 * @property {string} resultado uno de {@link RESULTADO}
 * @property {*} datos cuerpo de la respuesta, o null
 * @property {number|null} estado codigo HTTP, o null si no hubo respuesta
 * @property {string} motivo frase en lenguaje de persona, lista para pintar
 * @property {string} recurso ruta consultada, para el detalle tecnico
 */

/**
 * Consulta un recurso de la API y clasifica el desenlace.
 *
 * @param {string} recurso por ejemplo `/admin/jugadores?page=0`
 * @param {{senal?: AbortSignal, buscar?: typeof fetchWithHttpErrorInterceptor}} [opciones]
 * @returns {Promise<Desenlace>}
 */
export async function consultar(recurso, { senal, buscar = fetchWithHttpErrorInterceptor } = {}) {
  let respuesta;
  try {
    respuesta = await buscar(rutaDeApi(recurso), {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal: senal,
    });
  } catch (error) {
    // Ni siquiera hubo respuesta: DNS, CORS, red o el borde sin ruta.
    return {
      resultado: RESULTADO.SERVICIO_DEGRADADO,
      datos: null,
      estado: null,
      motivo: 'No hubo respuesta del servicio.',
      recurso,
      detalle: error?.message ?? null,
    };
  }

  if (respuesta.ok) {
    const datos = await leerJson(respuesta);
    return {
      resultado: estaVacio(datos) ? RESULTADO.VACIO : RESULTADO.DATOS,
      datos,
      estado: respuesta.status,
      motivo: estaVacio(datos) ? 'El servicio respondió, pero no hay registros.' : '',
      recurso,
      detalle: null,
    };
  }

  return { ...clasificarFallo(respuesta.status), datos: null, estado: respuesta.status, recurso };
}

/**
 * Varias consultas a la vez, cada una con su propio desenlace.
 *
 * Es `Promise.all` sobre `consultar()`, que nunca rechaza: una seccion caida
 * no impide que las otras se pinten.
 *
 * @param {Record<string, string>} recursosPorClave
 * @param {{senal?: AbortSignal, buscar?: Function}} [opciones]
 * @returns {Promise<Record<string, Desenlace>>}
 */
export async function consultarVarios(recursosPorClave, opciones = {}) {
  const claves = Object.keys(recursosPorClave);
  const desenlaces = await Promise.all(
    claves.map((clave) => consultar(recursosPorClave[clave], opciones)),
  );
  return Object.fromEntries(claves.map((clave, i) => [clave, desenlaces[i]]));
}

/** @param {number} estado */
function clasificarFallo(estado) {
  if (estado === 401) {
    return {
      resultado: RESULTADO.SIN_PERMISO,
      motivo: 'Tu sesión ya no es válida. Vuelve a entrar.',
      detalle: null,
    };
  }
  if (estado === 403) {
    return {
      resultado: RESULTADO.SIN_PERMISO,
      motivo: 'Tu rol no alcanza para ver esta sección.',
      detalle: null,
    };
  }
  if (estado === 404) {
    return {
      resultado: RESULTADO.NO_IMPLEMENTADO,
      motivo: 'El servicio está en pie, pero no publica esta consulta.',
      detalle: null,
    };
  }
  if (CAIDO.includes(estado)) {
    return {
      resultado: RESULTADO.SERVICIO_DEGRADADO,
      motivo: 'El servicio que responde esta consulta no está atendiendo.',
      detalle: null,
    };
  }
  return {
    resultado: RESULTADO.NO_DISPONIBLE,
    motivo: 'El servicio respondió algo que la consola no sabe interpretar.',
    detalle: `HTTP ${estado}`,
  };
}

/** @param {Response} respuesta */
async function leerJson(respuesta) {
  try {
    const texto = await respuesta.text();
    return texto ? JSON.parse(texto) : null;
  } catch {
    // Un 200 con cuerpo que no es JSON es un dato que no tenemos, no un dato
    // vacio: el panel lo dira asi.
    return null;
  }
}

/**
 * Vacio en el sentido del panel: nada que ensenar.
 *
 * `0` y `false` NO son vacios -- son respuestas legitimas de un contador.
 *
 * @param {*} datos
 */
export function estaVacio(datos) {
  if (datos === null || datos === undefined) {
    return true;
  }
  if (Array.isArray(datos)) {
    return datos.length === 0;
  }
  if (typeof datos === 'object') {
    if (Array.isArray(datos.contenido)) {
      return datos.contenido.length === 0;
    }
    if (Array.isArray(datos.content)) {
      return datos.content.length === 0;
    }
    return Object.keys(datos).length === 0;
  }
  return false;
}

/**
 * Cuantos elementos hay, sin inventar.
 *
 * Devuelve `null` cuando la respuesta no lleva un total -- que es distinto de
 * cero. El panel pinta un guion, no un 0.
 *
 * @param {*} datos
 * @returns {number|null}
 */
export function totalDe(datos) {
  if (datos === null || datos === undefined) {
    return null;
  }
  if (Array.isArray(datos)) {
    return datos.length;
  }
  if (typeof datos === 'object') {
    if (typeof datos.total === 'number') {
      return datos.total;
    }
    if (typeof datos.totalElements === 'number') {
      return datos.totalElements;
    }
    if (Array.isArray(datos.contenido)) {
      return datos.contenido.length;
    }
    if (Array.isArray(datos.content)) {
      return datos.content.length;
    }
  }
  return null;
}

/** Las filas de una respuesta, venga como lista, como pagina propia o de Spring. */
export function filasDe(datos) {
  if (Array.isArray(datos)) {
    return datos;
  }
  if (Array.isArray(datos?.contenido)) {
    return datos.contenido;
  }
  if (Array.isArray(datos?.content)) {
    return datos.content;
  }
  return [];
}
