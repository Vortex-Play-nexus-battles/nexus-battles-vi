/**
 * De dónde salen las misiones — UXC-5.
 *
 * ## Lo que hay hoy
 *
 * Nada que consultar. El módulo de misiones (M11) es del Grupo de Thomas y a
 * la fecha de este cambio no tiene servicio desplegado ni contrato publicado:
 * `services/contenido/misiones/` es un README vacío y `contracts/openapi/` no
 * trae ningún `misiones.yaml`. Inventar una ruta (`/api/v1/misiones`) para que
 * la pantalla «llame a algo» haría que el tablón pidiera una dirección que
 * nadie sirve, y que el borde contestara un 404 o un 502 en cada visita.
 *
 * Así que la interfaz se construye contra un **puerto**: esta fuente. La que
 * se exporta dice la verdad —`disponible: false`— y la vista, al leerla, pinta
 * el estado honesto con el siguiente paso que sí existe (preparar la
 * estrategia, que valida el servicio de héroes). No se hace ni una petición.
 *
 * ## El día que exista el servicio
 *
 * Se sustituye `fuenteDeMisiones()` por un adaptador HTTP que cumpla esta
 * misma forma contra el contrato que publique su dueño. Los tipos de abajo son
 * lo que la interfaz necesita para pintar lo que pide §7.8.9; no son una
 * propuesta de rutas. El resto de la vista no cambia.
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

/**
 * @typedef {'HISTORIA'|'DESAFIO'|'EXPLORACION'} Categoria
 * @typedef {'FACIL'|'NORMAL'|'DIFICIL'|'EXTREMO'} Dificultad
 * @typedef {'DISPONIBLE'|'BLOQUEADA'|'EN_PROGRESO'|'COMPLETADA'|'FALLIDA'|'ABANDONADA'} EstadoMision
 */

/**
 * La tarjeta del tablón (§7.8.9, «Tarjetas de misiones»).
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
 */

/**
 * El detalle (§7.8.3 y §7.8.9, «Vista de detalle de misión»).
 *
 * @typedef {ResumenMision & {
 *   narrativa: string,
 *   escenario?: string|null,
 *   objetivos: {principales: string[], secundarios: string[]},
 *   requisitosPrevios?: string[],
 *   encuentros?: number|null,
 *   escalon?: 'NORMAL'|'HEROICO'|'LEGENDARIO'|'MITICO'|null,
 *   enemigos: Array<{nombre: string, cantidad?: number|null, descripcion?: string|null}>,
 *   jefe?: {nombre: string, prototipo?: string|null, vida?: number|null, descripcion?: string|null}|null,
 *   probabilidadMaster?: number|null,
 *   masters?: Array<{nombre: string, prototipo?: string|null,
 *     epica: {nombre: string, efectoGeneral?: string|null, efectoPotenciado?: string|null}}>,
 *   recompensas: {
 *     garantizadas: string[],
 *     potenciales: Array<{nombre: string, probabilidad: number, detalle?: string|null}>,
 *     porObjetivos?: Array<{objetivo: string, recompensa: string}>,
 *     primeraVez?: string[],
 *   },
 * }} Mision
 */

/**
 * Una misión en curso (§7.8.9, «Panel de misiones activas»).
 *
 * @typedef {object} MisionActiva
 * @property {string} ejecucionId
 * @property {string} misionId
 * @property {string} nombre
 * @property {Categoria} categoria
 * @property {{id: string, nombre: string, prototipo?: string|null}} heroe
 * @property {string} iniciadaEn ISO 8601
 * @property {string} terminaEn ISO 8601
 * @property {number|null} [progreso] de 0 a 1, si es calculable
 * @property {string|null} [penalizacion] lo que cuesta cancelarla, dicho para el jugador
 */

/**
 * El reporte de una misión terminada (§7.8.8).
 *
 * @typedef {object} ReporteMision
 * @property {string} ejecucionId
 * @property {{id: string, nombre: string, categoria: Categoria}} mision
 * @property {'EXITO'|'FALLO'} resultado
 * @property {number} duracionMs
 * @property {string} terminadaEn ISO 8601
 * @property {{nombre: string, prototipo?: string|null, nivel?: number|null}} heroe
 * @property {{encuentros: number, danoInfligido: number, danoRecibido: number, turnos: number,
 *   habilidadesMasUsadas: Array<{nombre: string, usos: number}>, criticos: number}} combate
 * @property {Array<{nombre: string, cantidad: number}>} enemigosDerrotados
 * @property {Array<{nombre: string, epica: string}>} mastersDerrotados
 * @property {boolean} jefeDerrotado
 * @property {{creditos: number, productos: Array<{nombre: string, rareza?: string|null}>,
 *   epicas: string[], experiencia: number}} recompensas
 * @property {Array<{texto: string, cumplido: boolean, bonificacion?: string|null}>} objetivos
 */

/**
 * El historial (§7.8.8, «Historial de misiones»).
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
 *   rotaciones: Array<{pasos: string[]}>}) => Promise<MisionActiva>} matricular
 * @property {(ejecucionId: string) => Promise<void>} cancelar
 * @property {(misionId: string, favorita: boolean) => Promise<void>} marcarFavorita
 */

/** Lo que se lanza si alguien pide misiones a la fuente que no las tiene. */
export class MisionesSinAbrir extends Error {
  constructor() {
    super('Las misiones todavía no están abiertas.');
    this.name = 'MisionesSinAbrir';
  }
}

async function sinAbrir() {
  throw new MisionesSinAbrir();
}

/**
 * La fuente de hoy: sin servicio. Cada operación rechaza sin tocar la red; la
 * vista no llega a llamarlas porque mira `disponible` primero.
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
});

/**
 * La fuente de misiones de este despliegue.
 *
 * @returns {FuenteDeMisiones}
 */
export function fuenteDeMisiones() {
  return FUENTE_SIN_SERVICIO;
}
