/**
 * HU-NOT-006 — Acceso HTTP y direcciones del canal de notificaciones.
 *
 * Habla con `contracts/openapi/notificaciones.yaml` (bandeja, marcar leido,
 * entregar pendientes a una sesion) y conoce las direcciones de
 * `contracts/websocket/notificaciones.yaml` (handshake, cola privada, alta de
 * sesion). No decide nada que los contratos no digan.
 *
 * Identidad de la conexion. Desde la 1.1.0 del contrato AsyncAPI el canal se
 * identifica igual que la API HTTP: el JWT de la sesion viaja en la cabecera
 * `Authorization` del frame CONNECT (como ya exige salas-partidas, #222) y el
 * servidor deja como Principal el `uid` del token. Por la URL del handshake
 * NO viaja nada que identifique a nadie: ni el token ni el usuario. La
 * identidad que antes iba en `?usuario=` la ponia el cliente sin verificar.
 *
 * Todo error HTTP sale como `ErrorDeApi` (problem details, RFC 7807), para que
 * la vista decida por `tipo` y `estado` y nunca por el texto
 * (`shared/ui-kit/MAPEO-ERRORES.md`).
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { usuarioIdDeSesion } from '../../comun/identidad.js';

const CLAVE_APODO = 'nexus.apodoActual';
const CLAVE_TOKEN = 'nexus.token';

/**
 * Identificador estable de ESTA sesion del navegador. Sobrevive a la
 * reconexion (lo guarda el navegador) y es distinto por pestana
 * (sessionStorage), que es lo que el criterio CA-01 llama «sesion activa».
 */
export const CLAVE_SESION_CANAL = 'nexus.notificaciones.sesionId';

/**
 * Destinos y rutas del contrato AsyncAPI. El handshake es `/ws/notificaciones`
 * y no `/ws`: el borde enruta por la ruta, ya no por `?usuario=`.
 */
export const CANAL = Object.freeze({
  RUTA_HANDSHAKE: '/ws/notificaciones',
  COLA_PRIVADA: '/usuario/cola/notificaciones',
  ALTA_DE_SESION: '/app/notificaciones/sesion',
});

/**
 * Token de acceso de la sesion, el mismo que lleva la API HTTP. Va en la
 * cabecera `Authorization` del CONNECT; sin el, el servidor no abre sesion.
 *
 * @param {Storage} [almacen=sessionStorage]
 * @returns {string|null}
 */
export function tokenDeSesion(almacen = globalThis.sessionStorage) {
  return almacen?.getItem?.(CLAVE_TOKEN) ?? null;
}

/**
 * Base de la API. Vacia por omision (mismo origen). Para revisar la vista
 * como HTML estatico contra un backend en otro sitio, la pagina lo declara:
 *
 *   <meta name="nexus-api-base" content="http://127.0.0.1:8085" />
 *
 * @returns {string} base sin barra final, o cadena vacia
 */
export function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

/**
 * Lee la identidad del jugador que dejan las vistas de cuentas.
 *
 * @param {Storage} [almacen=sessionStorage]
 * @returns {{usuarioId: string|null, apodo: string|null}}
 */
export function leerSesion(almacen = globalThis.sessionStorage) {
  return {
    // Por `comun/identidad.js` y no por `nexus.usuarioId` a pelo: esa clave la
    // escribe el login con la clave primaria de la tabla, y
    // `gestion-usuarios.js` la pisa con el usuario que el administrador esta
    // consultando. Suscribirse al canal de otra persona le entregaria sus
    // notificaciones a quien no son.
    usuarioId: usuarioIdDeSesion(almacen),
    apodo: almacen?.getItem?.(CLAVE_APODO) ?? null,
  };
}

/**
 * Devuelve el identificador estable de la sesion del canal, creandolo la
 * primera vez. Mismo valor mientras viva la pestana, aunque el canal se caiga
 * y vuelva: asi el servidor sabe que se perdio exactamente esta sesion.
 *
 * @param {Storage} [almacen=sessionStorage]
 * @param {{randomUUID?: Function}} [generador=crypto]
 * @returns {string}
 */
export function identificadorDeSesion(
  almacen = globalThis.sessionStorage,
  generador = globalThis.crypto,
) {
  const existente = almacen?.getItem?.(CLAVE_SESION_CANAL);
  if (existente) {
    return existente;
  }
  const nuevo =
    typeof generador?.randomUUID === 'function'
      ? generador.randomUUID()
      : `sesion-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  almacen?.setItem?.(CLAVE_SESION_CANAL, nuevo);
  return nuevo;
}

/**
 * URL del handshake WebSocket. Sin identidad: esa va en el CONNECT.
 *
 * @param {{base?: string, location?: Location}} [opciones]
 *   `base` es la de la API HTTP (`http(s)://...` o vacia = mismo origen);
 *   se traduce a `ws(s)://`. `location` es inyeccion para las pruebas.
 * @returns {string}
 */
export function urlDelCanal({ base = baseDeApi(), location = globalThis.location } = {}) {
  const origen = base || `${location.protocol}//${location.host}`;
  const url = new URL(CANAL.RUTA_HANDSHAKE, origen);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return url.toString();
}

function rutaDeUsuario(usuarioId, sufijo = '') {
  return `${baseDeApi()}/api/v1/users/${encodeURIComponent(usuarioId)}${sufijo}`;
}

/** Error de negocio devuelto por el servicio, ya interpretado. */
export class ErrorDeApi extends Error {
  constructor(problema, estado) {
    super(problema?.detail || problema?.title || 'El servicio no pudo completar la operación.');
    this.name = 'ErrorDeApi';
    this.tipo = problema?.type ?? null;
    this.titulo = problema?.title ?? 'El servicio no pudo completar la operación';
    this.detalle = this.message;
    this.estado = problema?.status ?? estado;
  }
}

async function cuerpoDelProblema(respuesta) {
  try {
    return await respuesta.json();
  } catch {
    return null;
  }
}

async function leerJson(respuesta) {
  if (respuesta.ok) {
    return respuesta.json();
  }
  throw new ErrorDeApi(await cuerpoDelProblema(respuesta), respuesta.status);
}

/**
 * Bandeja completa del jugador: historial con estado de lectura y no leidos.
 *
 * @param {string} usuarioId
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{usuarioId: string, noLeidas: number, avisos: object[]}>} `BandejaResponse`
 */
export async function consultarBandeja(
  usuarioId,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  return leerJson(await fetchImpl(rutaDeUsuario(usuarioId, '/notifications')));
}

/**
 * Marca un aviso como leido para todo el jugador (idempotente).
 *
 * @param {string} usuarioId
 * @param {string} notificacionId
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{usuarioId: string, noLeidas: number}>} `ContadorResponse`
 */
export async function marcarLeida(
  usuarioId,
  notificacionId,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  return leerJson(
    await fetchImpl(
      rutaDeUsuario(usuarioId, `/notifications/${encodeURIComponent(notificacionId)}/read`),
      { method: 'POST' },
    ),
  );
}

/**
 * Recupera por HTTP lo que esta sesion se perdio (CA-03: cuando el canal no
 * esta disponible). El contrato lo declara como complemento del canal, no
 * como reemplazo.
 *
 * @param {string} usuarioId
 * @param {string} sesionId
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<object[]>} avisos entregados en esta llamada
 */
export async function entregarPendientes(
  usuarioId,
  sesionId,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  return leerJson(
    await fetchImpl(rutaDeUsuario(usuarioId, `/sessions/${encodeURIComponent(sesionId)}/pending`), {
      method: 'POST',
    }),
  );
}
