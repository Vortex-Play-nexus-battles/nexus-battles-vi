/**
 * Estrategia de combate del héroe — UXC-5 (RotationBuilder).
 *
 * Dos operaciones del servicio de héroes, las dos en `heroes.yaml` 1.1.0:
 *
 *   - `POST /api/v1/estrategias/validacion` (HU-SIM-001, §7.8.5): valida hasta
 *     tres rotaciones con prioridad Alta, Media y Baja. Sin estado y con
 *     usuario autenticado. Un rechazo responde 200 con el motivo y las
 *     habilidades válidas: no es un error de la petición.
 *   - `GET /api/v1/heroes/{nombre}/niveles/{nivel}`: la vista del prototipo en
 *     un nivel —estadísticas, acciones desbloqueadas con su costo y efecto,
 *     épica afín—. Pública. Es la «vista previa de estadísticas» que pide el
 *     configurador de §7.8.9.
 *
 * Qué NO hace: guardar la estrategia. El propio contrato lo dice: «quien
 * guarda la configuración es el módulo de misiones». Hasta que exista, la
 * estrategia se valida de verdad y no se guarda, y la pantalla lo dice.
 *
 * @module contenido/misiones/cliente-estrategias
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { textoDelServidor } from '../../comun/ui/texto-de-fallo.js';

const RUTA_VALIDACION = '/api/v1/estrategias/validacion';
const RUTA_HEROES = '/api/v1/heroes';

/** El nivel que el servicio supone cuando no se le dice ninguno. */
export const NIVEL_POR_OMISION = 1;

/** Los niveles de un héroe, §6.1.1. */
export const NIVELES = Object.freeze([1, 2, 3, 4, 5, 6, 7, 8]);

/**
 * El `detail` del problema, cuando el contrato dice que es apto para el
 * jugador (400 y 404 de este servicio). Para lo demás no se lee: un 500 no
 * trae nada que enseñar.
 *
 * @param {Response} respuesta
 * @returns {Promise<string|undefined>}
 */
async function detalleApto(respuesta) {
  if (respuesta.status !== 400 && respuesta.status !== 404) {
    return undefined;
  }
  try {
    const problema = await respuesta.json();
    // UXC-9 — el detalle solo si está escrito para quien juega.
    return textoDelServidor(problema, respuesta.status, '') || undefined;
  } catch {
    return undefined;
  }
}

/**
 * @param {Response} respuesta
 * @param {string} queFallo en palabras de quien lo lee en la consola
 * @returns {Promise<Error & {status: number, detalle?: string}>}
 */
async function falloDe(respuesta, queFallo) {
  const fallo = new Error(`${queFallo} (${respuesta.status})`);
  fallo.status = respuesta.status;
  const detalle = await detalleApto(respuesta);
  if (detalle) {
    fallo.detalle = detalle;
  }
  return fallo;
}

/**
 * Valida una estrategia. Sin `rotaciones` es la consulta de «¿qué puede usar
 * este héroe?»: el veredicto trae `habilidadesValidas` y `porDefecto`.
 *
 * @param {{heroe: string, nivel?: number|null, rotaciones?: Array<{pasos: string[]}>}} solicitud
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{valida: boolean, motivo?: string, heroe: string, nivel: number,
 *   rotaciones?: Array<{prioridad: string, pasos: string[]}>, habilidadesValidas: string[],
 *   porDefecto: boolean, comportamientoPorDefecto: string}>}
 */
export async function validarEstrategia(
  { heroe, nivel = null, rotaciones = [] },
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  if (typeof heroe !== 'string' || heroe.trim() === '') {
    throw new TypeError('El prototipo del héroe es obligatorio');
  }
  const cuerpo = { heroe: heroe.trim() };
  if (Number.isInteger(nivel)) {
    cuerpo.nivel = nivel;
  }
  if (Array.isArray(rotaciones) && rotaciones.length > 0) {
    cuerpo.rotaciones = rotaciones.map((rotacion) => ({ pasos: [...rotacion.pasos] }));
  }
  const respuesta = await fetchImpl(RUTA_VALIDACION, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(cuerpo),
  });
  if (!respuesta.ok) {
    throw await falloDe(respuesta, 'No se pudo validar la estrategia');
  }
  return respuesta.json();
}

/**
 * La vista del prototipo en un nivel.
 *
 * @param {string} prototipo nombre del catálogo («Guerrero Armas»)
 * @param {number} nivel de 1 a 8
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{nombre: string, tipo: string, esSanador: boolean, nivel: number,
 *   estadisticas: object, accionesDisponibles: Array<{nombre: string, costo: string, efecto: string}>,
 *   multiplicadorDeEfecto: number, experienciaParaSubir?: number,
 *   epica: {nombre: string, efectoGeneral?: string, efectoPotenciado?: string, turnosDeRecarga?: number}}>}
 */
export async function vistaEnNivel(
  prototipo,
  nivel,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  if (typeof prototipo !== 'string' || prototipo.trim() === '') {
    throw new TypeError('El prototipo es obligatorio');
  }
  if (!NIVELES.includes(nivel)) {
    throw new RangeError('El nivel va de 1 a 8');
  }
  const ruta = `${RUTA_HEROES}/${encodeURIComponent(prototipo.trim())}/niveles/${nivel}`;
  // Sin cabecera de identidad a propósito: el catálogo es público y la
  // respuesta no depende de quién pregunta.
  const respuesta = await fetchImpl(ruta);
  if (!respuesta.ok) {
    throw await falloDe(respuesta, 'No se pudo leer el héroe en ese nivel');
  }
  return respuesta.json();
}
