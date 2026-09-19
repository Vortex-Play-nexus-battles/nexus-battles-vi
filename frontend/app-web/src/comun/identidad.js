/**
 * Quién es el jugador de esta sesión — ADR-002.
 *
 * ## Por qué existe
 *
 * Tres vistas leen `sessionStorage['nexus.usuarioId']` —`comentarios`,
 * `notificaciones` y `gestion-usuarios`— y **nadie lo escribe nunca**. Solo lo
 * ponen los ficheros de prueba. En producción siempre vale `null`, así que esas
 * vistas llevan funcionando a medias sin que salte ningún error: simplemente no
 * identifican al usuario.
 *
 * La causa de fondo es que el identificador ya viaja en el token, y guardarlo
 * aparte en el login abre la puerta a que las dos copias se separen. Aquí se
 * lee del token, que es la fuente.
 *
 * ## De dónde sale
 *
 * Del claim `uid`, igual que en el backend (`IdentidadDelToken`). Tras ADR-002
 * el `sub` de `ms-identidad` es el **apodo**, no un UUID: leerlo como
 * identificador es el mismo error que provocó el 500 del PR #404 y que se
 * arrastró hasta el chat.
 *
 * Se conserva `nexus.usuarioId` como primera opción para no romper a quien ya
 * lo escriba —o llegue a escribirlo— y para que las pruebas existentes sigan
 * valiendo.
 *
 * @module identidad
 */

const CLAVE_TOKEN = 'nexus.token';
const CLAVE_USUARIO_ID = 'nexus.usuarioId';

/**
 * Descodifica el cuerpo de un JWT sin verificarlo.
 *
 * **No valida la firma, y no debe.** Verificar es trabajo del servidor: aquí
 * solo se lee para pintar. Un token manipulado engañaría a esta función, pero
 * no al backend, que es quien decide.
 *
 * @param {string} token
 * @returns {object|null} el cuerpo, o null si no se puede leer
 */
export function cuerpoDelToken(token) {
  if (typeof token !== 'string') {
    return null;
  }
  const partes = token.split('.');
  if (partes.length < 2) {
    return null;
  }
  try {
    // base64url -> base64, y `atob` para no depender de nada más.
    const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');
    const relleno = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    return JSON.parse(decodeURIComponent(escape(atob(relleno))));
  } catch {
    // Un token ilegible no es un fallo que deba romper la vista: se trata como
    // «no hay sesión».
    return null;
  }
}

/**
 * Identificador estable del jugador de esta sesión.
 *
 * @param {Storage} [almacen] inyectable para las pruebas
 * @returns {string|null} el UUID, o null si no hay sesión utilizable
 */
export function usuarioIdDeSesion(almacen = globalThis.sessionStorage) {
  const guardado = almacen?.getItem?.(CLAVE_USUARIO_ID);
  if (guardado) {
    return guardado;
  }
  const cuerpo = cuerpoDelToken(almacen?.getItem?.(CLAVE_TOKEN));
  if (!cuerpo) {
    return null;
  }
  // `uid` manda. `sub` solo vale de respaldo si es un UUID: en los tokens de
  // ms-identidad posteriores a ADR-002 es el apodo, y devolverlo como
  // identificador sería mentir.
  if (cuerpo.uid) {
    return String(cuerpo.uid);
  }
  return pareceUuid(cuerpo.sub) ? String(cuerpo.sub) : null;
}

const FORMA_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * @param {unknown} valor
 * @returns {boolean}
 */
function pareceUuid(valor) {
  return typeof valor === 'string' && FORMA_UUID.test(valor);
}
