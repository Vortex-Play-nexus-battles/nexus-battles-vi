/**
 * Entrar al juego — R17, y desde B1 con el correo verificado.
 *
 * Lo comparten el login y el registro: pedir el login, guardar la sesión que
 * devuelve y decidir adónde ir. Antes el login lo hacía a mano, clave por
 * clave.
 *
 * B1 (identidad 2.0.0) — una cuenta de autorregistro nace pendiente de
 * verificar su correo, y hasta entonces el login la rechaza. Por eso el
 * registro ya no entra solo: lleva a la verificación, y es la verificación la
 * que después lleva al login.
 *
 * @module comun/entrada
 */

import { destinoDeCuentaNueva, destinoTrasEntrar } from './alta.js';
import { rutaDeApi } from './base-api.js';
import { CLAVES_DEL_CORREO, recordar, tipoDelProblema } from './codigo-de-correo.js';
import {
  MOTIVOS,
  MOTIVOS_DE_VERIFICACION,
  guardarSesion,
  urlDeLogin,
  urlDeVerificacion,
} from './sesion.js';
import { textoDelServidor } from './ui/texto-de-fallo.js';

/**
 * Correo que el login trae ya escrito: el de la cuenta que se acaba de crear,
 * verificar o recuperar. Va en `sessionStorage` y se borra al usarlo: nunca en
 * la URL, donde quedaría en el historial y en las bitácoras del borde.
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
  // UXC-9 — una página de proxy («502 Bad Gateway · nginx») llegaba aquí como
  // texto plano y el login la pintaba entera. Solo pasa lo que se lee.
  // B1 — del problem details cuentan `detail` (o el `mensaje` antiguo); el
  // `title` nombra el problema, no es un mensaje para quien entra.
  const candidato = body && typeof body === 'object' ? (body.detail ?? body.mensaje ?? null) : body;
  return textoDelServidor(candidato, undefined, '') || undefined;
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
 * B1 — `cuentaNueva`: es la primera entrada de una cuenta que acaba de
 * verificar su correo. Va SIEMPRE a «Preparando tu cuenta», aunque el alta ya
 * haya terminado, por lo mismo que antes iba el registro
 * (`destinoDeCuentaNueva`): es la pantalla que le dice qué le dio el juego.
 *
 * @param {{token: string, apodo?: string, rol?: string, uid?: string, usuarioId?: unknown, onboardingListo?: boolean}} body
 * @param {{volver?: string|null, cuentaNueva?: boolean, almacen?: Storage, base?: string}} [opciones]
 * @returns {string} URL de destino
 */
export function entrarCon(
  body,
  { volver = null, cuentaNueva = false, almacen = globalThis.sessionStorage, base } = {},
) {
  guardarSesion(
    {
      token: body.token,
      apodo: body.apodo,
      rol: body.rol,
      uid: body.uid ?? identificadorDeSesion(body.token, body.usuarioId),
    },
    almacen,
  );
  return cuentaNueva ? destinoDeCuentaNueva(base) : destinoTrasEntrar(body, volver, base);
}

/**
 * UXC-7 (§12 de la auditoría) — lo que se le dice a quien se registra cuando
 * el servidor rechaza el alta, por el `type` estable del rechazo
 * (`ProblemaDeRegistro`, ms-identidad-auth 1.1.0). Con palabras del producto
 * y qué hacer; el servidor sigue siendo quien decide: aquí no se comprueba
 * ninguna lista negra ni ninguna regla, solo se explica su respuesta.
 * Un motivo sin entrada aquí se dice con el texto del servidor.
 */
export const MOTIVOS_DE_REGISTRO = Object.freeze({
  'correo-en-uso': 'Ya hay una cuenta con ese correo. Si es tuya, entra o recupera tu contraseña.',
  'apodo-en-uso':
    'Ese apodo ya lo usa otro jugador. Prueba con otro: es el nombre con el que te verán en las partidas.',
  // RFINAL-03: la misma frase que «Mi cuenta» (MOTIVOS_DEL_PERFIL).
  'apodo-no-permitido': 'Ese apodo no está permitido. Elige otro.',
  'avatar-invalido': 'Esa imagen no sirve como avatar. Usa una foto JPG, PNG o WebP.',
});

/**
 * El motivo estable de un problem details: el último tramo de su `type`.
 *
 * @param {unknown} body
 * @returns {string|null}
 */
export function motivoDelRechazo(body) {
  if (!body || typeof body !== 'object' || typeof body.type !== 'string') {
    return null;
  }
  return body.type.split('/').filter(Boolean).pop() ?? null;
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
 * Por qué el servicio no pudo comprobar el apodo (identidad 2.0.0): la lista
 * negra no respondió y el alta NO se hace. Tiene mensaje propio porque, a
 * diferencia de un fallo cualquiera, no hay nada mal en lo que se escribió.
 */
const MODERACION_NO_DISPONIBLE = 'moderacion-no-disponible';

/**
 * Crea la cuenta y dice adónde ir después.
 *
 * Cuatro finales:
 *   - `pendiente` — B1: cuenta creada y pendiente de verificar su correo. El
 *                   correo se recuerda en esta pestaña (nunca en la URL) y
 *                   `destino` es la verificación, que es donde se escribe el
 *                   código que acaba de salir.
 *   - `dentro`    — la cuenta ya nació activa (un servicio anterior a la
 *                   verificación) y el login entró solo: a «Preparando tu
 *                   cuenta», como en R17.
 *   - `creada`    — nació activa pero no se pudo entrar solo: al login con el
 *                   correo ya escrito.
 *   - `rechazada` — el servidor no creó la cuenta; `campo` dice cuál marcar.
 *
 * Qué camino se toma lo dice el `estado` que devuelve el propio registro, no
 * una suposición: solo una cuenta `ACTIVO` puede iniciar sesión ya.
 *
 * Un fallo de red al CREAR la cuenta se propaga: la vista no sabe si se creó,
 * y lo honesto es decirlo así.
 *
 * @param {FormData} datos
 * @param {{email: string, password: string}} credenciales
 * @param {{fetchImpl?: typeof fetch, almacen?: Storage, base?: string, ahora?: () => number}} [opciones]
 * @returns {Promise<{resultado: 'pendiente'|'dentro'|'creada'|'rechazada', destino?: string,
 *   mensaje?: string, campo?: string|null, estado?: number}>}
 */
export async function registrarCuenta(
  datos,
  { email, password },
  {
    fetchImpl = globalThis.fetch,
    almacen = globalThis.sessionStorage,
    base,
    ahora = () => Date.now(),
  } = {},
) {
  const { respuesta, body } = await pedirRegistro(datos, fetchImpl);
  if (!respuesta.ok) {
    return rechazoDelRegistro(respuesta.status, body);
  }

  if (body && typeof body === 'object' && body.estado === 'ACTIVO') {
    return entrarTrasRegistrar({ email, password }, { fetchImpl, almacen, base });
  }

  recordar(CLAVES_DEL_CORREO.porVerificar, email, almacen);
  recordar(CLAVES_DEL_CORREO.ultimoEnvio, String(ahora()), almacen);
  return {
    resultado: 'pendiente',
    destino: urlDeVerificacion({ motivo: MOTIVOS_DE_VERIFICACION.REGISTRO }, base),
  };
}

/**
 * @param {number} estado
 * @param {unknown} body
 */
function rechazoDelRegistro(estado, body) {
  const campo = body && typeof body === 'object' ? (body.campo ?? null) : null;
  if (tipoDelProblema(body) === MODERACION_NO_DISPONIBLE) {
    return {
      resultado: 'rechazada',
      mensaje:
        'No pudimos comprobar tu apodo en este momento, así que la cuenta no se creó. Inténtalo de nuevo en unos minutos.',
      campo,
      estado,
    };
  }
  // UXC-7: los rechazos del alta se explican por su `type` estable, con
  // palabras del producto; lo que no tenga entrada se dice con el texto del
  // servidor.
  const motivo = estado >= 500 ? null : motivoDelRechazo(body);
  const mensaje =
    estado >= 500
      ? 'No pudimos crear la cuenta ahora mismo. Inténtalo de nuevo en unos minutos.'
      : (MOTIVOS_DE_REGISTRO[motivo] ??
        mensajeDelServidor(body) ??
        'No se pudo crear la cuenta. Revisa los datos e inténtalo de nuevo.');
  return { resultado: 'rechazada', mensaje, campo, estado, ...(motivo ? { motivo } : {}) };
}

/**
 * La cuenta nació activa: entra con ella sin volver a pedir lo que se acaba
 * de escribir (R17).
 *
 * @param {{email: string, password: string}} credenciales
 * @param {{fetchImpl: typeof fetch, almacen: Storage, base?: string}} opciones
 */
async function entrarTrasRegistrar({ email, password }, { fetchImpl, almacen, base }) {
  try {
    const login = await pedirLogin({ email, password }, fetchImpl);
    if (login.respuesta.ok && login.body?.token) {
      entrarCon(login.body, { almacen, base });
      return { resultado: 'dentro', destino: destinoDeCuentaNueva(base) };
    }
  } catch {
    // La cuenta ya existe: queda entrar a mano, con el correo ya escrito.
  }
  recordar(CLAVE_CORREO_REGISTRADO, email, almacen);
  return { resultado: 'creada', destino: urlDeLogin({ motivo: MOTIVOS.REGISTRADA }, base) };
}
