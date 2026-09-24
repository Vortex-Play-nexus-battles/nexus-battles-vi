/**
 * Entrar al juego — R17.
 *
 * Lo comparten el login y el registro, que desde R17 entra solo al crear la
 * cuenta: pedir el login, guardar la sesión que devuelve y decidir adónde ir.
 * Antes el login lo hacía a mano, clave por clave, y el registro mandaba a
 * la persona al login a escribir otra vez lo que acababa de escribir.
 *
 * @module comun/entrada
 */

import { destinoDeCuentaNueva, destinoTrasEntrar } from './alta.js';
import { rutaDeApi } from './base-api.js';
import { MOTIVOS, guardarSesion, urlDeLogin } from './sesion.js';

/**
 * Correo que deja el registro cuando no pudo entrar solo, para que el login
 * lo traiga escrito. Va en `sessionStorage` y se borra al usarlo: nunca en la
 * URL, donde quedaría en el historial y en las bitácoras del borde.
 */
export const CLAVE_CORREO_REGISTRADO = 'nexus.registro.correo';

/**
 * Lee el cuerpo de una respuesta que puede venir como JSON o texto plano.
 *
 * @param {Response} respuesta
 * @returns {Promise<unknown>}
 */
export async function cuerpoDe(respuesta) {
  const texto = await respuesta.text();
  if (!texto) {
    return null;
  }
  try {
    return JSON.parse(texto);
  } catch {
    return texto;
  }
}

/**
 * El mensaje que manda el servidor, venga como problem details (R17), como
 * JSON antiguo o como texto plano.
 *
 * @param {unknown} body
 * @returns {string|undefined}
 */
export function mensajeDelServidor(body) {
  if (typeof body === 'string') {
    return body || undefined;
  }
  return body?.detail ?? body?.mensaje ?? undefined;
}

/**
 * Identificador estable del jugador (ADR-002): el claim `uid` del token.
 * `LoginResponse.usuarioId` es la clave primaria de la tabla, que no es lo
 * que comparan los demás servicios (#426); solo se usa si el token no trae
 * `uid`.
 *
 * @param {string|undefined} token
 * @param {unknown} respaldo
 * @returns {string}
 */
export function identificadorDeSesion(token, respaldo) {
  try {
    const cuerpo = String(token ?? '').split('.')[1];
    const base64 = cuerpo.replace(/-/g, '+').replace(/_/g, '/');
    const claims = JSON.parse(atob(base64));
    if (typeof claims.uid === 'string' && claims.uid) {
      return claims.uid;
    }
  } catch {
    // Sin token legible: queda el respaldo.
  }
  return String(respaldo ?? '');
}

/**
 * Pide el login al servidor.
 *
 * Va con `fetch` directo y no con el interceptor: aquí todavía no hay sesión
 * que adjuntar, y el aviso flotante del interceptor repetiría encima el mismo
 * rechazo que ya dice el formulario.
 *
 * @param {{email: string, password: string}} credenciales
 * @param {typeof fetch} [fetchImpl]
 * @returns {Promise<{respuesta: Response, body: unknown}>}
 */
export async function pedirLogin(credenciales, fetchImpl = globalThis.fetch) {
  const respuesta = await fetchImpl(rutaDeApi('/auth/login'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/problem+json, application/json',
    },
    body: JSON.stringify(credenciales),
  });
  return { respuesta, body: await cuerpoDe(respuesta) };
}

/**
 * Entra con la respuesta de un login correcto: guarda la sesión y dice
 * adónde ir (la preparación de la cuenta si aún no está lista).
 *
 * @param {{token: string, apodo?: string, rol?: string, uid?: string, usuarioId?: unknown, onboardingListo?: boolean}} body
 * @param {{volver?: string|null, almacen?: Storage, base?: string}} [opciones]
 * @returns {string} URL de destino
 */
export function entrarCon(body, { volver = null, almacen = globalThis.sessionStorage, base } = {}) {
  guardarSesion(
    {
      token: body.token,
      apodo: body.apodo,
      rol: body.rol,
      uid: body.uid ?? identificadorDeSesion(body.token, body.usuarioId),
    },
    almacen,
  );
  return destinoTrasEntrar(body, volver, base);
}

/**
 * Envía el formulario de registro (multipart, porque puede llevar el avatar).
 *
 * @param {FormData} datos
 * @param {typeof fetch} [fetchImpl]
 * @returns {Promise<{respuesta: Response, body: unknown}>}
 */
export async function pedirRegistro(datos, fetchImpl = globalThis.fetch) {
  // Sin `Content-Type`: el navegador lo pone con el separador del multipart.
  const respuesta = await fetchImpl(rutaDeApi('/auth/registro'), {
    method: 'POST',
    headers: { Accept: 'application/problem+json, application/json' },
    body: datos,
  });
  return { respuesta, body: await cuerpoDe(respuesta) };
}

/**
 * Crea la cuenta y entra con ella, sin volver a pedir lo que se acaba de
 * escribir.
 *
 * Tres finales:
 *   - `dentro`    — cuenta creada y sesión abierta; `destino` es SIEMPRE la
 *                   preparación de la cuenta, aunque el alta ya esté lista:
 *                   es donde se le dice qué le dio el juego
 *                   (`destinoDeCuentaNueva`).
 *   - `creada`    — la cuenta existe pero no se pudo entrar solo (el login
 *                   falló o no contestó): al login con el correo ya escrito.
 *   - `rechazada` — el servidor no creó la cuenta; `campo` dice cuál marcar.
 *
 * Un fallo de red al CREAR la cuenta se propaga: la vista no sabe si se creó,
 * y lo honesto es decirlo así.
 *
 * @param {FormData} datos
 * @param {{email: string, password: string}} credenciales
 * @param {{fetchImpl?: typeof fetch, almacen?: Storage, base?: string}} [opciones]
 */
export async function registrarYEntrar(
  datos,
  { email, password },
  { fetchImpl = globalThis.fetch, almacen = globalThis.sessionStorage, base } = {},
) {
  const { respuesta, body } = await pedirRegistro(datos, fetchImpl);
  if (!respuesta.ok) {
    const campo = body && typeof body === 'object' ? (body.campo ?? null) : null;
    const mensaje =
      respuesta.status >= 500
        ? 'No pudimos crear la cuenta ahora mismo. Inténtalo de nuevo en unos minutos.'
        : (mensajeDelServidor(body) ??
          'No se pudo crear la cuenta. Revisa los datos e inténtalo de nuevo.');
    return { resultado: 'rechazada', mensaje, campo, estado: respuesta.status };
  }

  try {
    const login = await pedirLogin({ email, password }, fetchImpl);
    if (login.respuesta.ok && login.body?.token) {
      entrarCon(login.body, { almacen, base });
      return { resultado: 'dentro', destino: destinoDeCuentaNueva(base) };
    }
  } catch {
    // La cuenta ya existe: queda entrar a mano, con el correo ya escrito.
  }
  almacen.setItem(CLAVE_CORREO_REGISTRADO, email);
  return { resultado: 'creada', destino: urlDeLogin({ motivo: MOTIVOS.REGISTRADA }, base) };
}
