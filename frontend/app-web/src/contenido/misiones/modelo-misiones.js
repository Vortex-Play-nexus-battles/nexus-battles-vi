/**
 * Vocabulario del módulo de misiones — UXC-5.
 *
 * Las categorías, las dificultades, los estados y los escalones que fija la
 * sección 7.8 del documento del curso, con el nombre con que se escriben y el
 * icono con que se reconocen. Aquí no se decide nada del juego: qué misión
 * está disponible, cuál está bloqueada y por qué lo dice quien publica las
 * misiones. Esto solo traduce lo que llegue a palabras y a iconos.
 *
 * ## Nada de color sin palabra
 *
 * Cada estado y cada dificultad llevan su texto. La dificultad además se
 * dibuja con marcas (una a cuatro), que se distinguen por cantidad y no por
 * tono: quien no distingue el ámbar del rojo sigue leyendo «Difícil».
 *
 * @module contenido/misiones/modelo-misiones
 */

import { numero, porcentaje } from '../../comun/ui/formato.js';

/**
 * §7.8.2 — las tres categorías, en el orden de las pestañas del tablón
 * (§7.8.9). El resumen es la regla de cada categoría dicha para el jugador:
 * es del documento, no un dato de ninguna misión.
 */
export const CATEGORIAS = Object.freeze([
  {
    id: 'HISTORIA',
    etiqueta: 'Historia',
    icono: 'bandera',
    resumen:
      'Narrativa lineal que se desbloquea en orden. Completarla la primera vez da una recompensa única.',
  },
  {
    id: 'DESAFIO',
    etiqueta: 'Desafío',
    icono: 'rayo',
    resumen:
      'Enfrentamientos de alta dificultad, con requisitos de nivel o equipamiento, intentos limitados y recompensas premium.',
  },
  {
    id: 'EXPLORACION',
    etiqueta: 'Exploración',
    icono: 'mapa',
    resumen:
      'De 24 a 72 horas, con varios encuentros seguidos, más probabilidad de enemigos Máster y recompensas que se acumulan.',
  },
]);

/** §7.8.9 — el filtro de dificultad del tablón. `marcas` es cuántas se dibujan. */
export const DIFICULTADES = Object.freeze([
  { id: 'FACIL', etiqueta: 'Fácil', marcas: 1 },
  { id: 'NORMAL', etiqueta: 'Normal', marcas: 2 },
  { id: 'DIFICIL', etiqueta: 'Difícil', marcas: 3 },
  { id: 'EXTREMO', etiqueta: 'Extremo', marcas: 4 },
]);

/** El máximo de marcas de dificultad: tantas como dificultades. */
export const MARCAS_DE_DIFICULTAD = DIFICULTADES.length;

/**
 * §7.8.7 — los seis estados de una misión, con su distintivo del kit
 * (`.distintivo--<variante>`, que ya traía `bloqueada`, `en-progreso` y
 * `completada` desde las maquetas).
 */
export const ESTADOS = Object.freeze({
  DISPONIBLE: { id: 'DISPONIBLE', texto: 'Disponible', icono: 'objetivo', variante: 'activo' },
  BLOQUEADA: { id: 'BLOQUEADA', texto: 'Bloqueada', icono: 'candado', variante: 'bloqueada' },
  EN_PROGRESO: { id: 'EN_PROGRESO', texto: 'En progreso', icono: 'reloj', variante: 'en-progreso' },
  COMPLETADA: { id: 'COMPLETADA', texto: 'Completada', icono: 'check', variante: 'completada' },
  FALLIDA: { id: 'FALLIDA', texto: 'Fallida', icono: 'alerta', variante: 'suspendido' },
  ABANDONADA: { id: 'ABANDONADA', texto: 'Abandonada', icono: 'cerrar', variante: 'no-disponible' },
});

/**
 * §7.8.9 — el filtro de estado del tablón ofrece tres, no seis: «Disponibles,
 * En progreso, Completadas». Las demás se ven con «Todos».
 */
export const FILTRO_DE_ESTADO = Object.freeze([
  { id: '', etiqueta: 'Todos los estados' },
  { id: 'DISPONIBLE', etiqueta: 'Disponibles' },
  { id: 'EN_PROGRESO', etiqueta: 'En progreso' },
  { id: 'COMPLETADA', etiqueta: 'Completadas' },
]);

/**
 * §7.8.9 — «filtro por duración estimada». El documento no da tramos; estos
 * separan lo que cabe en una tarde, lo que dura un día y la exploración
 * (24 a 72 horas, §7.8.2).
 */
export const FILTRO_DE_DURACION = Object.freeze([
  { id: '', etiqueta: 'Cualquier duración' },
  { id: 'HASTA_12', etiqueta: 'Hasta 12 horas' },
  { id: 'DE_12_A_24', etiqueta: 'De 12 a 24 horas' },
  { id: 'MAS_DE_24', etiqueta: 'Más de 24 horas' },
]);

/** §7.8.11 — los escalones de dificultad de una misión ya completada. */
export const ESCALONES = Object.freeze({
  NORMAL: 'Normal',
  HEROICO: 'Heroico',
  LEGENDARIO: 'Legendario',
  MITICO: 'Mítico',
});

/** Las prioridades de las rotaciones, en su orden (§7.8.5). */
export const PRIORIDADES = Object.freeze(['Alta', 'Media', 'Baja']);

/** Paginación de 16 elementos por página en vitrinas y listados. */
export const MISIONES_POR_PAGINA = 16;

/**
 * @param {string|null|undefined} id
 * @returns {(typeof CATEGORIAS)[number]|null}
 */
export function categoriaDe(id) {
  return CATEGORIAS.find((c) => c.id === id) ?? null;
}

/**
 * @param {string|null|undefined} id
 * @returns {(typeof DIFICULTADES)[number]|null}
 */
export function dificultadDe(id) {
  return DIFICULTADES.find((d) => d.id === id) ?? null;
}

/**
 * El estado tal como llega, o `null` si no es uno de los seis: un estado que
 * no se conoce no se disfraza de «Disponible».
 *
 * @param {string|null|undefined} id
 * @returns {(typeof ESTADOS)[keyof typeof ESTADOS]|null}
 */
export function estadoDe(id) {
  return Object.hasOwn(ESTADOS, String(id)) ? ESTADOS[id] : null;
}

/**
 * «1 hora», «12 horas», «72 horas». Las horas se dicen en horas aunque pasen
 * de un día: así las escribe el documento («24-72 horas») y así se comparan.
 *
 * @param {number|null|undefined} horas
 * @returns {string|null}
 */
export function textoDeDuracion(horas) {
  if (!Number.isFinite(horas) || horas <= 0) {
    return null;
  }
  const cifra = numero(horas, Number.isInteger(horas) ? 0 : 1);
  return `${cifra} ${horas === 1 ? 'hora' : 'horas'}`;
}

/**
 * Un tiempo transcurrido en milisegundos, como se lee en un reporte:
 * «11 h 42 min», «45 min», «2 h».
 *
 * @param {number|null|undefined} ms
 * @returns {string|null}
 */
export function textoDeTiempo(ms) {
  if (!Number.isFinite(ms) || ms < 0) {
    return null;
  }
  const minutos = Math.round(ms / 60_000);
  const horas = Math.floor(minutos / 60);
  const resto = minutos % 60;
  if (horas === 0) {
    return `${resto} min`;
  }
  return resto === 0 ? `${horas} h` : `${horas} h ${resto} min`;
}

/**
 * Una probabilidad de 0 a 1 como porcentaje. Con menos de un 1 % se dan
 * decimales: redondear un 0,5 % a «1 %» duplicaría la probabilidad.
 *
 * @param {number|null|undefined} proporcion
 * @returns {string|null}
 */
export function textoDeProbabilidad(proporcion) {
  if (!Number.isFinite(proporcion) || proporcion < 0 || proporcion > 1) {
    return null;
  }
  if (proporcion > 0 && proporcion < 0.01) {
    return `${numero(proporcion * 100, 2)} %`;
  }
  return porcentaje(proporcion);
}

/**
 * La etiqueta de una rotación por su posición: «Rotación 1 · prioridad alta».
 *
 * @param {number} indice desde cero
 * @returns {string}
 */
export function nombreDeRotacion(indice) {
  const prioridad = PRIORIDADES[indice];
  return prioridad
    ? `Rotación ${indice + 1} · prioridad ${prioridad.toLowerCase()}`
    : `Rotación ${indice + 1}`;
}
