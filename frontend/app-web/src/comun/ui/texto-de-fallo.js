/**
 * Lo que un jugador puede leer de un fallo del servidor — UXC-9.
 *
 * ## El defecto
 *
 * Cada cliente de servicio hacía lo mismo con un rechazo:
 *
 *     super(problema?.detail || problema?.title || 'No se pudo…');
 *
 * y el mensaje acababa en pantalla. Eso está bien cuando el servicio escribe
 * para personas («Esa sala ya no admite más jugadores»), y está muy mal
 * cuando el que contesta es otra cosa: la página HTML de un proxy caído
 * («502 Bad Gateway · nginx»), el `detail` de una excepción sin manejar
 * («NullPointerException at com.nexus…»), un «Error 503» construido con el
 * código, o el nombre de un servicio interno. Un jugador no sabe qué es
 * `ms-finanzas`; sabe si tiene que esperar, reintentar o irse.
 *
 * ## La regla
 *
 * - Un 500 nunca se lee: es un fallo sin manejar y su texto es técnico por
 *   definición. Se dice el respaldo de quien llama, escrito para su pantalla.
 * - Un 502/503/504 se lee solo si llega como problem details con un `detail`
 *   que pasa el filtro: los servicios de la casa usan el 503 a propósito para
 *   decir «el libro de créditos no responde; nada cambió», que sí sirve. Un
 *   texto plano en un 5xx es la página de un proxy y no se lee nunca.
 * - Cualquier otro rechazo se lee si pasa el filtro (`pareceTextoTecnico`).
 *
 * El código HTTP sigue viajando en el error (`estado`) para quien programa y
 * para la traza; lo que no hace es llegar a la pantalla.
 *
 * @module comun/ui/texto-de-fallo
 */

/** Lo que delata un texto escrito para una máquina y no para una persona. */
const SENALES_TECNICAS = Object.freeze([
  // Marcado: la página de error de un proxy o de un servidor de aplicaciones.
  /<\/?[a-z][^>]*>/i,
  // Frases de estado HTTP, con o sin el código delante.
  /\b(?:Bad Gateway|Internal Server Error|Service Unavailable|Gateway Time-?out|Bad Request|Not Found|Unauthorized|Forbidden|Too Many Requests|Method Not Allowed)\b/i,
  // «HTTP 502», «HTTP/1.1».
  /\bHTTP\s*\/?\s*\d/i,
  // «Error 500», «código 503», «status: 404».
  /\b(?:error|c[oó]digo|status|estado)\s*:?\s*[1-5]\d\d\b/i,
  // Nombres de excepción y de error de un lenguaje.
  /\b[A-Z][A-Za-z]+(?:Exception|Error)\b/,
  // Una traza de pila.
  /\bat\s+[\w$.]+\s*\(/,
  // Nombres de servicios internos.
  /\b(?:ms|srv)-[a-z]+/i,
  // Direcciones y hosts.
  /\b(?:localhost|127\.0\.0\.1)\b|:\/\//i,
  // Base de datos y marcos.
  /\b(?:SQL|JDBC|PSQL|ORA-\d+|Hibernate|Spring|Jackson|constraint)\b/i,
  // Un JSON crudo.
  /^\s*[{[]/,
  // Lo que dicen los navegadores cuando falla la red.
  /Failed to fetch|NetworkError|Load failed|ECONNREFUSED|ETIMEDOUT|ERR_[A-Z_]+/,
  // Identificadores internos.
  /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/i,
  // Inglés de servidor: el producto habla castellano.
  /\b(?:unexpected|occurred|failed|cannot|could not|unable to|null|undefined|timeout|unavailable|invalid)\b/i,
]);

/** Un texto más largo que esto no es un aviso, es un volcado. */
const LARGO_MAXIMO = 280;

/**
 * ¿Parece escrito para una máquina?
 *
 * @param {unknown} texto
 * @returns {boolean}
 */
export function pareceTextoTecnico(texto) {
  if (typeof texto !== 'string') {
    return true;
  }
  const limpio = texto.trim();
  if (!limpio || limpio.length > LARGO_MAXIMO) {
    return true;
  }
  return SENALES_TECNICAS.some((senal) => senal.test(limpio));
}

/** Sin respuesta del servidor (red caída, CORS, sin conexión). */
export const TEXTO_SIN_CONEXION = 'No pudimos conectar. Revisa tu conexión e inténtalo otra vez.';

/** Un servicio caído, dicho sin nombrarlo. */
export const TEXTO_SIN_SERVICIO =
  'Esta parte del juego no responde ahora mismo. Vuelve a intentarlo en un momento.';

/**
 * El texto del servidor si se puede leer; si no, el respaldo de quien llama.
 *
 * @param {unknown} problema problem details (objeto), texto plano o nada
 * @param {number|null|undefined} estado código HTTP, si lo hubo
 * @param {string} respaldo lo que se dice cuando el servidor no da nada legible
 * @returns {string}
 */
export function textoDelServidor(problema, estado, respaldo) {
  const esNumero = typeof estado === 'number';
  if (esNumero && estado >= 500 && (estado === 500 || typeof problema !== 'object')) {
    return respaldo;
  }
  let candidato = problema;
  if (problema && typeof problema === 'object') {
    candidato = problema.detail ?? problema.mensaje ?? problema.title ?? null;
  }
  if (typeof candidato !== 'string' || pareceTextoTecnico(candidato)) {
    return respaldo;
  }
  return candidato.trim();
}

/**
 * El respaldo que toca según el código, para quien no tiene uno propio.
 *
 * @param {number|null|undefined} estado
 * @returns {string}
 */
export function respaldoPorEstado(estado) {
  if (typeof estado !== 'number' || estado === 0) {
    return TEXTO_SIN_CONEXION;
  }
  if (estado >= 500) {
    return TEXTO_SIN_SERVICIO;
  }
  if (estado === 401) {
    return 'Tu sesión terminó. Vuelve a entrar para seguir.';
  }
  if (estado === 403) {
    return 'Tu cuenta no puede hacer esto.';
  }
  if (estado === 404) {
    return 'Eso ya no está disponible.';
  }
  return 'No se pudo completar. Revisa los datos e inténtalo otra vez.';
}
