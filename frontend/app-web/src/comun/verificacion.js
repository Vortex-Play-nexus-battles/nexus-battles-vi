/**
 * Verificación del correo de una cuenta nueva — B1 (identidad 2.0.0).
 *
 * Desde la 2.0.0 del contrato, una cuenta creada por autorregistro nace
 * `PENDIENTE_VERIFICACION`: el servicio envía un código de un solo uso al
 * correo y el login responde 403 `cuenta-no-verificada` hasta que alguien lo
 * escribe. Que el buzón es de quien se registró se prueba así, con el código,
 * y de ninguna otra forma (7.4.12: «confirmar la creación de una cuenta de
 * usuario»).
 *
 * Aquí viven las dos llamadas del contrato y la espera entre reenvíos. Las
 * usan la vista `verificar-cuenta` y el login, que ofrece reenviar el código
 * cuando la cuenta todavía no está verificada.
 *
 * Van con `fetch` directo y no con el interceptor, por lo mismo que el login
 * (`entrada.js`): son rutas públicas, todavía no hay sesión que adjuntar, y el
 * aviso flotante del interceptor repetiría encima el rechazo que la vista ya
 * explica junto al formulario.
 *
 * @module comun/verificacion
 */

import { baseDeApi } from './base-api.js';
import {
  CLAVES_DEL_CORREO,
  detalleDelProblema,
  recordado,
  recordar,
  tipoDelProblema,
} from './codigo-de-correo.js';
import { cuerpoDe } from './entrada.js';

/** Las dos rutas del contrato. Literales enteros: el guardián de rutas las lee. */
export const RUTAS_DE_VERIFICACION = Object.freeze({
  confirmacion: '/api/v1/auth/verificacion/confirmacion',
  reenvio: '/api/v1/auth/verificacion/reenvio',
});

/**
 * Lo que se dice tras pedir otro código, exista o no la cuenta. Es fijo en la
 * interfaz aunque el servidor ya responda lo mismo: así no puede depender de
 * nada que venga en la respuesta.
 */
export const MENSAJE_DE_REENVIO =
  'Si existe una cuenta pendiente asociada, recibirás un código nuevo.';

/**
 * Espera entre dos peticiones de código desde esta pestaña. Es el valor por
 * omisión de `identidad.verificacion.segundos-entre-reenvios` en el servidor,
 * que es quien de verdad pone el límite (y en silencio: la respuesta no
 * cambia). Aquí solo sirve para no ofrecer un botón que no va a hacer nada.
 */
export const ESPERA_ENTRE_REENVIOS_MS = 60_000;

const CABECERAS = Object.freeze({
  'Content-Type': 'application/json',
  Accept: 'application/problem+json, application/json',
});

/**
 * @param {string} ruta
 * @param {object} datos
 * @param {typeof fetch} fetchImpl
 */
async function publicar(ruta, datos, fetchImpl) {
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
    method: 'POST',
    headers: { ...CABECERAS },
    body: JSON.stringify(datos),
  });
  return { respuesta, cuerpo: await cuerpoDe(respuesta) };
}

/**
 * @typedef {object} ResultadoDeVerificacion
 * @property {boolean} ok
 * @property {number} estado código HTTP
 * @property {string|null} [mensaje] el del servidor, si lo dio (solo con `ok`)
 * @property {string|null} [tipo] motivo del rechazo (`codigo-invalido`, `demasiados-intentos`)
 * @property {string|null} [detalle] texto del rechazo, solo para enseñarlo
 */

/**
 * Canjea el código: la cuenta pasa a `ACTIVO` y ENTONCES empieza el alta del
 * jugador (créditos de bienvenida, héroe inicial y su equipo).
 *
 * Un rechazo del servidor no lanza: vuelve con su `tipo`, que es lo que la
 * vista necesita para decidir. Un fallo de red sí se propaga.
 *
 * @param {{email: string, codigo: string}} datos
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 * @returns {Promise<ResultadoDeVerificacion>}
 */
export async function confirmarCorreo({ email, codigo }, { fetchImpl = globalThis.fetch } = {}) {
  const { respuesta, cuerpo } = await publicar(
    RUTAS_DE_VERIFICACION.confirmacion,
    { email, codigo },
    fetchImpl,
  );
  if (respuesta.ok) {
    const mensaje = cuerpo && typeof cuerpo === 'object' ? (cuerpo.mensaje ?? null) : null;
    return { ok: true, estado: respuesta.status, mensaje };
  }
  return {
    ok: false,
    estado: respuesta.status,
    tipo: tipoDelProblema(cuerpo),
    detalle: detalleDelProblema(cuerpo),
  };
}

/**
 * Pide otro código. La respuesta del servidor es neutra (202 exista o no la
 * cuenta), y la de esta función también: con `ok`, `mensaje` es siempre
 * `MENSAJE_DE_REENVIO`.
 *
 * @param {string} email
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 * @returns {Promise<ResultadoDeVerificacion>}
 */
export async function reenviarCodigo(email, { fetchImpl = globalThis.fetch } = {}) {
  const { respuesta, cuerpo } = await publicar(RUTAS_DE_VERIFICACION.reenvio, { email }, fetchImpl);
  if (respuesta.ok) {
    return { ok: true, estado: respuesta.status, mensaje: MENSAJE_DE_REENVIO };
  }
  return {
    ok: false,
    estado: respuesta.status,
    tipo: tipoDelProblema(cuerpo),
    detalle: detalleDelProblema(cuerpo),
  };
}

/**
 * Anota que acaba de salir un código (al registrarse o al pedir otro), para
 * que la espera siga contando aunque se cambie de pantalla o se recargue.
 *
 * @param {Storage} [almacen]
 * @param {number} [ahora] milisegundos
 */
export function anotarEnvio(almacen = globalThis.sessionStorage, ahora = Date.now()) {
  recordar(CLAVES_DEL_CORREO.ultimoEnvio, String(ahora), almacen);
}

/**
 * Segundos que faltan para poder pedir otro código; 0 si ya se puede.
 *
 * Nunca más de la espera completa: un reloj que se ha movido hacia atrás no
 * deja el botón bloqueado para siempre.
 *
 * @param {Storage} [almacen]
 * @param {number} [ahora] milisegundos
 * @returns {number}
 */
export function segundosParaReenviar(almacen = globalThis.sessionStorage, ahora = Date.now()) {
  const marca = Number(recordado(CLAVES_DEL_CORREO.ultimoEnvio, almacen));
  if (!Number.isFinite(marca) || marca <= 0) {
    return 0;
  }
  const restante = Math.min(marca + ESPERA_ENTRE_REENVIOS_MS - ahora, ESPERA_ENTRE_REENVIOS_MS);
  return restante > 0 ? Math.ceil(restante / 1000) : 0;
}
