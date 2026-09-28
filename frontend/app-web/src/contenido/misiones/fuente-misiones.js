/**
 * De dónde salen las misiones — UXC-5, con servicio desde B9.
 *
 * La interfaz se construyó contra un **puerto** (esta fuente) cuando el
 * módulo de misiones no tenía servicio ni contrato. Ahora los tiene:
 * `services/contenido/misiones` y `contracts/openapi/misiones.yaml` 1.0.0, que
 * se escribió para servir exactamente a este puerto. Así que lo que se
 * exporta es un **adaptador HTTP** contra ese contrato, y el resto de la vista
 * no cambia.
 *
 * ## Si el servicio no responde
 *
 * La vista sigue mirando `disponible` antes de pedir nada. Por eso
 * `fuenteDeMisiones()` pregunta primero al servicio —la consulta de
 * destacadas, que es barata y la pide igualmente el banner— y solo si
 * contesta bien devuelve el adaptador. Si no contesta (sin red, el borde da
 * 502 porque el servicio no está desplegado, 404 porque el borde no conoce la
 * ruta, o tarda más de `ESPERA_DE_LA_SONDA_MS`), devuelve la fuente sin
 * servicio: `disponible: false`, y la vista pinta su estado honesto sin hacer
 * ni una petición más. La respuesta de esa primera consulta no se tira: la
 * primera llamada a `destacadas()` la reutiliza.
 *
 * ## Los errores
 *
 * Cada operación que falla lanza un `Error` con `status` y, cuando el
 * contrato dice que su `detail` es apto para el jugador (404, 409, 422 y el
 * 503 de sección degradada), `detalle`. Un 422 de matrícula trae además
 * `motivos` (todos, HU-MIS-008) y uno de estrategia `habilidadesValidas`. Es
 * la misma forma que ya leen las vistas (`fallo.status`, `fallo.detalle`).
 *
 * ## Y el laboratorio
 *
 * El laboratorio visual sirve en lugar de este archivo otro con el mismo
 * nombre (`tests/visual/laboratorio/fuente-misiones.js`) que responde con el
 * ejemplo de misión del documento (§7.8.14, «El Templo Olvidado»). Esos datos
 * viven en `tests/`, marcados como de laboratorio, y nunca llegan aquí.
 *
 * @module contenido/misiones/fuente-misiones
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

/**
 * @typedef {'HISTORIA'|'DESAFIO'|'EXPLORACION'} Categoria
 * @typedef {'FACIL'|'NORMAL'|'DIFICIL'|'EXTREMO'} Dificultad
 * @typedef {'DISPONIBLE'|'BLOQUEADA'|'EN_PROGRESO'|'COMPLETADA'|'FALLIDA'|'ABANDONADA'} EstadoMision
 * @typedef {'NORMAL'|'HEROICO'|'LEGENDARIO'|'MITICO'} Escalon
 */

/**
 * La tarjeta del tablón (§7.8.9, «Tarjetas de misiones»; `ResumenMision`).
 *
 * @typedef {object} ResumenMision
 * @property {string} id
 * @property {string} nombre
 * @property {Categoria} categoria
 * @property {string} descripcionBreve
 * @property {string|null} [imagen] URL de la imagen representativa
 * @property {Dificultad} dificultad
 * @property {number} duracionHoras
 * @property {number|null} [nivelRecomendado]
 * @property {string[]} [recompensasDestacadas]
 * @property {EstadoMision} estado
 * @property {string|null} [motivoBloqueo] por qué está bloqueada, dicho para el jugador
 * @property {number|null} [progreso] de 0 a 1, si la misión está en curso y es calculable
 * @property {string|null} [ultimaEjecucionId] para abrir su reporte
 * @property {boolean} [destacada] va en el banner rotativo
 * @property {boolean} [nueva]
 * @property {string|null} [disponibleHasta] ISO 8601, si es de tiempo limitado
 * @property {boolean} [favorita]
 * @property {'DOCUMENTO'|'PROVISIONAL_DEV'} [origen]
 */

/**
 * El detalle (§7.8.3 y §7.8.9, «Vista de detalle de misión»; `Mision`).
 *
 * @typedef {ResumenMision & {
 *   narrativa: string,
 *   escenario?: string|null,
 *   objetivos: {principales: string[], secundarios: string[]},
 *   requisitosPrevios?: string[],
 *   encuentros?: number|null,
 *   escalon?: Escalon|null,
 *   escalones?: Array<{id: Escalon, desbloqueado: boolean, multiplicadorDeEstadisticas?: number|null}>,
 *   enemigos: Array<{nombre: string, cantidad?: number|null, descripcion?: string|null}>,
 *   jefe?: {nombre: string, prototipo?: string|null, vida?: number|null, descripcion?: string|null}|null,
 *   probabilidadMaster?: number|null,
 *   masters?: Array<{nombre: string, prototipo?: string|null, probabilidad?: number|null,
 *     epica: {nombre: string, efectoGeneral?: string|null, efectoPotenciado?: string|null}}>,
 *   recompensas: {
 *     garantizadas: string[],
 *     potenciales: Array<{nombre: string, probabilidad: number, detalle?: string|null}>,
 *     porObjetivos?: Array<{objetivo: string, recompensa: string}>,
 *     primeraVez?: string[],
 *   },
 *   experiencia?: {porEnemigo: string, porCompletar?: number},
 *   intentos?: {maximo: number, periodo: 'DIARIO'|'SEMANAL', restantes: number}|null,
 * }} Mision
 */

/**
 * Una misión en curso (§7.8.9, «Panel de misiones activas»; `MisionActiva`).
 *
 * @typedef {object} MisionActiva
 * @property {string} ejecucionId
 * @property {string} misionId
 * @property {string} nombre
 * @property {Categoria} categoria
 * @property {{id: string, nombre: string, prototipo?: string|null, nivel?: number|null}} heroe
 * @property {string} iniciadaEn ISO 8601
 * @property {string} terminaEn ISO 8601
 * @property {number|null} [progreso] de 0 a 1, si es calculable
 * @property {string|null} [penalizacion] lo que cuesta cancelarla, dicho para el jugador
 * @property {Escalon} [escalon]
 * @property {'EN_PROGRESO'} [estado]
 */

/**
 * Lo que devuelve cancelar (`Cancelacion`).
 *
 * @typedef {object} Cancelacion
 * @property {string} ejecucionId
 * @property {'ABANDONADA'} estado
 * @property {boolean} heroeLiberado falso si el inventario no contestó: se termina en segundo plano
 * @property {string} penalizacion
 */

/**
 * El reporte de una misión terminada (§7.8.8; `ReporteMision`).
 *
 * @typedef {object} ReporteMision
 * @property {string} ejecucionId
 * @property {{id: string, nombre: string, categoria: Categoria}} mision
 * @property {'EXITO'|'FALLO'} resultado
 * @property {Escalon} [escalon]
 * @property {number} duracionMs
 * @property {string} terminadaEn ISO 8601
 * @property {{id?: string, nombre: string, prototipo?: string|null, nivel?: number|null,
 *   nivelAlcanzado?: number|null}} heroe
 * @property {{encuentros: number, danoInfligido: number, danoRecibido: number, turnos: number,
 *   habilidadesMasUsadas: Array<{nombre: string, usos: number}>, criticos: number}} combate
 * @property {Array<{nombre: string, cantidad: number}>} enemigosDerrotados
 * @property {Array<{nombre: string, epica: string}>} mastersDerrotados
 * @property {boolean} jefeDerrotado
 * @property {{creditos: number, productos: Array<{nombre: string, rareza?: string|null}>,
 *   epicas: string[], experiencia: number,
 *   sinEntregar?: Array<{nombre: string, motivo: string}>, entregaPendiente?: boolean}} recompensas
 * @property {Array<{texto: string, cumplido: boolean, bonificacion?: string|null}>} objetivos
 */

/**
 * El historial (§7.8.8, «Historial de misiones»; `HistorialDeMisiones`).
 *
 * @typedef {object} HistorialDeMisiones
 * @property {Array<{ejecucionId: string, misionId: string, nombre: string, categoria: Categoria,
 *   terminadaEn: string, resultado: 'EXITO'|'FALLO', duracionMs: number}>} completadas
 * @property {Array<{categoria: Categoria, completadas: number, fallidas: number}>} porCategoria
 * @property {Array<{misionId: string, nombre: string, duracionMs: number}>} mejoresTiempos
 * @property {Array<{nombre: string, master: string, obtenidaEn: string}>} epicas
 * @property {Array<{nombre: string, completadas: number, total: number}>} cadenas
 */

/**
 * La estrategia guardada de un héroe (§7.8.12, «configuraciones de rotaciones
 * guardadas»; `EstrategiaGuardada`). La valida el servidor con el prototipo y
 * el nivel REALES del héroe.
 *
 * @typedef {object} EstrategiaGuardada
 * @property {string} heroeId
 * @property {string} prototipo
 * @property {number} nivel
 * @property {Array<{prioridad: 'Alta'|'Media'|'Baja', pasos: string[]}>} rotaciones
 * @property {string} actualizadaEn ISO 8601
 */

/**
 * @typedef {object} CriteriosDelTablero
 * @property {Categoria} categoria
 * @property {Dificultad|''} [dificultad]
 * @property {''|'DISPONIBLE'|'EN_PROGRESO'|'COMPLETADA'} [estado]
 * @property {''|'HASTA_12'|'DE_12_A_24'|'MAS_DE_24'} [duracion]
 * @property {number} [pagina] desde cero
 */

/**
 * @typedef {object} FuenteDeMisiones
 * @property {boolean} disponible
 * @property {(criterios: CriteriosDelTablero) =>
 *   Promise<{misiones: ResumenMision[], total: number, pagina: number, totalPaginas: number}>} tablero
 * @property {() => Promise<ResumenMision[]>} destacadas
 * @property {(id: string) => Promise<Mision>} detalle
 * @property {() => Promise<MisionActiva[]>} activas
 * @property {() => Promise<HistorialDeMisiones>} historial
 * @property {(ejecucionId: string) => Promise<ReporteMision>} reporte
 * @property {(solicitud: {misionId: string, heroeId: string,
 *   rotaciones?: Array<{pasos: string[]}>, escalon?: Escalon}) => Promise<MisionActiva>} matricular
 * @property {(ejecucionId: string) => Promise<Cancelacion|void>} cancelar
 * @property {(misionId: string, favorita: boolean) => Promise<void>} marcarFavorita
 * @property {(heroeId: string) => Promise<EstrategiaGuardada|null>} estrategiaGuardada
 *   la guardada, o `null` si el jugador no guardó ninguna para ese héroe
 * @property {(heroeId: string, rotaciones: Array<{pasos: string[]}>) =>
 *   Promise<EstrategiaGuardada>} guardarEstrategia
 */

/** Lo que se lanza si alguien pide misiones a la fuente que no las tiene. */
export class MisionesSinAbrir extends Error {
  constructor() {
    super('Las misiones no están disponibles.');
    this.name = 'MisionesSinAbrir';
  }
}

async function sinAbrir() {
  throw new MisionesSinAbrir();
}

/**
 * La fuente cuando el servicio no responde. Cada operación rechaza sin tocar
 * la red; la vista no llega a llamarlas porque mira `disponible` primero.
 *
 * @type {Readonly<FuenteDeMisiones>}
 */
export const FUENTE_SIN_SERVICIO = Object.freeze({
  disponible: false,
  tablero: sinAbrir,
  destacadas: sinAbrir,
  detalle: sinAbrir,
  activas: sinAbrir,
  historial: sinAbrir,
  reporte: sinAbrir,
  matricular: sinAbrir,
  cancelar: sinAbrir,
  marcarFavorita: sinAbrir,
  estrategiaGuardada: sinAbrir,
  guardarEstrategia: sinAbrir,
});

/* ------------------------------------------------------------ adaptador HTTP */

const RUTA = '/api/v1/misiones';

/**
 * Lo que se espera a la primera respuesta del servicio antes de darlo por
 * caído. Un borde sano contesta un 502 en milisegundos; esto es para cuando
 * la red se queda colgada, que la página no espere para siempre.
 */
export const ESPERA_DE_LA_SONDA_MS = 6000;

/**
 * Estados cuyo `detail` el contrato da por apto para el jugador: 404 (lo
 * ajeno no se confirma), 409 (misión o héroe ocupados), 422 (héroe no apto o
 * estrategia que no vale) y 503 (sección degradada, HU-DIS-003). Un 400 es un
 * fallo de la pantalla, no algo que el jugador pueda arreglar.
 */
const CON_DETALLE_APTO = new Set([404, 409, 422, 503]);

/**
 * El error de una operación, con lo que la vista sabe leer.
 *
 * @param {Response} respuesta
 * @param {string} queFallo en palabras de quien lo lee en la consola
 * @returns {Promise<Error & {status: number, detalle?: string, motivos?: string[],
 *   habilidadesValidas?: string[]}>}
 */
async function falloDe(respuesta, queFallo) {
  const fallo = new Error(`${queFallo} (${respuesta.status})`);
  fallo.status = respuesta.status;
  let problema = null;
  try {
    problema = await respuesta.json();
  } catch {
    problema = null;
  }
  if (problema && typeof problema === 'object') {
    const detalle = typeof problema.detail === 'string' ? problema.detail.trim() : '';
    if (detalle && CON_DETALLE_APTO.has(respuesta.status)) {
      fallo.detalle = detalle;
    }
    if (Array.isArray(problema.motivos)) {
      fallo.motivos = problema.motivos.filter((m) => typeof m === 'string');
    }
    if (Array.isArray(problema.habilidadesValidas)) {
      fallo.habilidadesValidas = problema.habilidadesValidas.filter((h) => typeof h === 'string');
    }
  }
  return fallo;
}

/** Una clave de idempotencia nueva (`Idempotency-Key`, de 1 a 100 caracteres). */
function claveNueva() {
  return (
    globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

/**
 * El adaptador HTTP contra `misiones.yaml` 1.0.0. No pregunta nada al
 * crearse: `disponible` es cierto porque quien lo usa ya sabe que el servicio
 * contesta (ver `fuenteDeMisiones`).
 *
 * @param {{fetchImpl?: Function, generarClave?: () => string}} [opciones]
 * @returns {FuenteDeMisiones}
 */
export function fuenteHttpDeMisiones({
  fetchImpl = fetchWithHttpErrorInterceptor,
  generarClave = claveNueva,
} = {}) {
  /**
   * @param {string} ruta
   * @param {string} queFallo
   * @param {{method?: string, cuerpo?: unknown, cabeceras?: Record<string, string>}} [peticion]
   * @returns {Promise<Response>}
   */
  async function llamar(ruta, queFallo, { method = 'GET', cuerpo, cabeceras = {} } = {}) {
    const conCuerpo = cuerpo !== undefined;
    const respuesta = await fetchImpl(ruta, {
      method,
      headers: {
        Accept: 'application/json',
        ...(conCuerpo ? { 'Content-Type': 'application/json' } : {}),
        ...cabeceras,
      },
      ...(conCuerpo ? { body: JSON.stringify(cuerpo) } : {}),
    });
    if (!respuesta.ok) {
      throw await falloDe(respuesta, queFallo);
    }
    return respuesta;
  }

  const leer = async (ruta, queFallo) => (await llamar(ruta, queFallo)).json();
  const deMision = (id) => `${RUTA}/${encodeURIComponent(id)}`;
  const deEjecucion = (id) => `${RUTA}/ejecuciones/${encodeURIComponent(id)}`;
  const deEstrategia = (heroeId) => `${RUTA}/estrategias/${encodeURIComponent(heroeId)}`;
  /** Solo `pasos`: `RotacionSolicitada` no admite nada más (additionalProperties: false). */
  const soloPasos = (rotaciones) => rotaciones.map((r) => ({ pasos: [...r.pasos] }));

  return {
    disponible: true,

    tablero({ categoria, dificultad = '', estado = '', duracion = '', pagina = 0 }) {
      const parametros = new URLSearchParams({ categoria });
      for (const [nombre, valor] of [
        ['dificultad', dificultad],
        ['estado', estado],
        ['duracion', duracion],
      ]) {
        if (valor) {
          parametros.set(nombre, valor);
        }
      }
      if (Number.isInteger(pagina) && pagina > 0) {
        parametros.set('pagina', String(pagina));
      }
      return leer(`${RUTA}?${parametros}`, 'No se pudo leer el tablón de misiones');
    },

    destacadas() {
      return leer(`${RUTA}/destacadas`, 'No se pudieron leer las misiones destacadas');
    },

    detalle(id) {
      return leer(deMision(id), 'No se pudo leer la misión');
    },

    activas() {
      return leer(`${RUTA}/en-curso`, 'No se pudieron leer las misiones en curso');
    },

    historial() {
      return leer(`${RUTA}/historial`, 'No se pudo leer el historial de misiones');
    },

    reporte(ejecucionId) {
      return leer(deEjecucion(ejecucionId), 'No se pudo leer el reporte de la misión');
    },

    async matricular({ misionId, heroeId, rotaciones, escalon }) {
      // `misionId` va en la ruta: el cuerpo es `SolicitudDeMatricula`, que no
      // admite propiedades de más. Una clave por envío: si la respuesta se
      // pierde y el mismo envío se repite, el servidor devuelve la ejecución
      // ya creada en vez de crear otra.
      const cuerpo = { heroeId };
      if (Array.isArray(rotaciones)) {
        cuerpo.rotaciones = soloPasos(rotaciones);
      }
      if (escalon) {
        cuerpo.escalon = escalon;
      }
      const respuesta = await llamar(
        `${deMision(misionId)}/ejecuciones`,
        'No se pudo iniciar la misión',
        {
          method: 'POST',
          cuerpo,
          cabeceras: { 'Idempotency-Key': generarClave() },
        },
      );
      return respuesta.json();
    },

    async cancelar(ejecucionId) {
      const respuesta = await llamar(
        `${deEjecucion(ejecucionId)}/cancelacion`,
        'No se pudo cancelar la misión',
        { method: 'POST' },
      );
      return respuesta.json();
    },

    async marcarFavorita(misionId, favorita) {
      await llamar(`${deMision(misionId)}/favorita`, 'No se pudo guardar la favorita', {
        method: favorita ? 'PUT' : 'DELETE',
      });
    },

    async estrategiaGuardada(heroeId) {
      try {
        return await leer(deEstrategia(heroeId), 'No se pudo leer la estrategia guardada');
      } catch (fallo) {
        // 404 es «todavía no guardaste ninguna para este héroe»: no es un fallo.
        if (fallo?.status === 404) {
          return null;
        }
        throw fallo;
      }
    },

    async guardarEstrategia(heroeId, rotaciones) {
      const respuesta = await llamar(deEstrategia(heroeId), 'No se pudo guardar la estrategia', {
        method: 'PUT',
        cuerpo: { rotaciones: soloPasos(rotaciones ?? []) },
      });
      return respuesta.json();
    },
  };
}

/**
 * La primera consulta: las destacadas, o `null` si el servicio no contesta
 * bien a tiempo. Nunca lanza.
 *
 * @param {Function} fetchImpl
 * @param {number} esperaMs
 * @returns {Promise<ResumenMision[]|null>}
 */
async function sondear(fetchImpl, esperaMs) {
  const control = typeof AbortController === 'function' ? new AbortController() : null;
  let reloj;
  const plazo = new Promise((resolver) => {
    reloj = setTimeout(() => {
      control?.abort();
      resolver(null);
    }, esperaMs);
  });
  const pregunta = (async () => {
    try {
      const respuesta = await fetchImpl(`${RUTA}/destacadas`, {
        headers: { Accept: 'application/json' },
        ...(control ? { signal: control.signal } : {}),
      });
      if (!respuesta.ok) {
        return null;
      }
      const cuerpo = await respuesta.json();
      return Array.isArray(cuerpo) ? cuerpo : null;
    } catch {
      // Sin red, cortada por el plazo, o un cuerpo que no es JSON (una página
      // del borde en vez del servicio): para la vista es lo mismo.
      return null;
    }
  })();
  try {
    return await Promise.race([pregunta, plazo]);
  } finally {
    clearTimeout(reloj);
  }
}

/**
 * La fuente de misiones de este despliegue: el adaptador HTTP si el servicio
 * contesta, o la fuente sin servicio si no.
 *
 * @param {{fetchImpl?: Function, esperaMs?: number, generarClave?: () => string}} [opciones]
 * @returns {Promise<FuenteDeMisiones>}
 */
export async function fuenteDeMisiones({
  fetchImpl = fetchWithHttpErrorInterceptor,
  esperaMs = ESPERA_DE_LA_SONDA_MS,
  generarClave = claveNueva,
} = {}) {
  // Sin `fetch` no hay red a la que preguntar (el envoltorio común lo usa por
  // debajo): no se intenta.
  if (fetchImpl === fetchWithHttpErrorInterceptor && typeof globalThis.fetch !== 'function') {
    return FUENTE_SIN_SERVICIO;
  }
  const destacadas = await sondear(fetchImpl, esperaMs);
  if (destacadas === null) {
    return FUENTE_SIN_SERVICIO;
  }
  const http = fuenteHttpDeMisiones({ fetchImpl, generarClave });
  let primeras = destacadas;
  return Object.freeze({
    ...http,
    async destacadas() {
      // La primera vez, las de la sonda: son de hace un instante y el banner
      // las pide justo al montar. Después, siempre al servidor.
      if (primeras) {
        const lista = primeras;
        primeras = null;
        return lista;
      }
      return http.destacadas();
    },
  });
}
