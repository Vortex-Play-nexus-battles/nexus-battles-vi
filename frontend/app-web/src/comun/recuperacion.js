/**
 * Recuperar el acceso a una cuenta — B1 (identidad 2.0.0, 7.1.1).
 *
 * 7.1.1: «El usuario puede recuperar su cuenta contestando preguntas con
 * respuestas previamente configuradas y el código enviado a su correo
 * electrónico». Son tres llamadas públicas, en este orden:
 *
 *   1. `solicitar` — pide el código. Responde lo mismo exista o no la cuenta.
 *   2. `preguntas` — a cambio del código, las preguntas que la cuenta
 *      configuró (lista vacía si no configuró ninguna: entonces basta el código).
 *   3. `confirmar` — código, respuestas y contraseña nueva.
 *
 * El mismo canje sirve para ACTIVAR una cuenta que creó un Super
 * Administrador: el correo de activación lleva al mismo formulario, con un
 * código del mismo formato (correo.yaml 1.4.0, `proposito: ACTIVACION`).
 *
 * `fetch` directo por lo mismo que `entrada.js` y `verificacion.js`: son
 * rutas de quien todavía no puede iniciar sesión.
 *
 * @module comun/recuperacion
 */

import { baseDeApi } from './base-api.js';
import { detalleDelProblema, tipoDelProblema } from './codigo-de-correo.js';
import { cuerpoDe } from './entrada.js';

/** Las tres rutas del contrato. Literales enteros: el guardián de rutas las lee. */
export const RUTAS_DE_RECUPERACION = Object.freeze({
  solicitar: '/api/v1/auth/restablecer/solicitar',
  preguntas: '/api/v1/auth/restablecer/preguntas',
  confirmar: '/api/v1/auth/restablecer/confirmar',
});

/**
 * Lo único que se dice al pedir el código, exista o no la cuenta. Es fijo en
 * la interfaz: si dependiera del texto de la respuesta, un cambio de redacción
 * en el servidor podría acabar diciendo qué correos están registrados.
 */
export const MENSAJE_DE_SOLICITUD = 'Si existe una cuenta asociada, recibirás instrucciones.';

/** Lo que se dice al canjear si el servidor no manda texto propio. */
export const MENSAJE_DE_CAMBIO = 'Contraseña actualizada correctamente. Ya puedes iniciar sesión.';

/** Motivos de rechazo del canje, además de los del código. */
export const PROBLEMAS_DE_RECUPERACION = Object.freeze({
  RESPUESTAS_INCORRECTAS: 'respuestas-incorrectas',
  CONTRASENA_NO_CUMPLE_POLITICA: 'contrasena-no-cumple-politica',
});

/**
 * `solicitar` y `confirmar` responden texto plano cuando van bien y problem
 * details cuando no: se aceptan los dos.
 */
const ACEPTA = 'application/problem+json, application/json, text/plain';

/** Un rechazo del servidor, ya leído. La vista decide por `tipo` y `estado`. */
export class FalloDeRecuperacion extends Error {
  /**
   * @param {{estado: number, tipo?: string|null, detalle?: string|null}} datos
   */
  constructor({ estado, tipo = null, detalle = null }) {
    super(detalle ?? `HTTP ${estado}`);
    this.name = 'FalloDeRecuperacion';
    this.estado = estado;
    this.tipo = tipo;
    this.detalle = detalle;
  }
}

/**
 * @param {string} ruta
 * @param {object} datos
 * @param {typeof fetch} fetchImpl
 */
async function publicar(ruta, datos, fetchImpl) {
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: ACEPTA },
    body: JSON.stringify(datos),
  });
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw new FalloDeRecuperacion({
      estado: respuesta.status,
      tipo: tipoDelProblema(cuerpo),
      detalle: detalleDelProblema(cuerpo),
    });
  }
  return cuerpo;
}

/**
 * Paso 1: pide el código de recuperación.
 *
 * @param {string} email
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 * @returns {Promise<string>} siempre `MENSAJE_DE_SOLICITUD`
 * @throws {FalloDeRecuperacion} si el servidor no la aceptó (límite de peticiones, correo mal escrito)
 */
export async function solicitarRestablecimiento(email, { fetchImpl = globalThis.fetch } = {}) {
  await publicar(RUTAS_DE_RECUPERACION.solicitar, { email }, fetchImpl);
  return MENSAJE_DE_SOLICITUD;
}

/**
 * Paso 2: las preguntas de seguridad de la cuenta, a cambio del código. Un
 * código incorrecto cuenta como intento fallido (cinco y se anula).
 *
 * @param {{email: string, codigo: string}} datos
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 * @returns {Promise<{configuradas: boolean, preguntas: Array<{id: string, texto: string}>}>}
 * @throws {FalloDeRecuperacion}
 */
export async function consultarPreguntas({ email, codigo }, { fetchImpl = globalThis.fetch } = {}) {
  const cuerpo = await publicar(RUTAS_DE_RECUPERACION.preguntas, { email, codigo }, fetchImpl);
  const preguntas = Array.isArray(cuerpo?.preguntas)
    ? cuerpo.preguntas.filter(
        (pregunta) => typeof pregunta?.id === 'string' && typeof pregunta?.texto === 'string',
      )
    : [];
  return { configuradas: cuerpo?.configuradas === true && preguntas.length > 0, preguntas };
}

/**
 * Paso 3: canjea el código (y las respuestas, si la cuenta tiene preguntas) y
 * fija la contraseña nueva. Canjearlo cierra las sesiones que hubiera abiertas.
 *
 * `respuestas` solo viaja si hay alguna: el contrato la pide únicamente a las
 * cuentas que configuraron preguntas.
 *
 * @param {{email: string, codigo: string, nuevaPassword: string,
 *          respuestas?: Array<{preguntaId: string, respuesta: string}>}} datos
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 * @returns {Promise<string>} el mensaje a enseñar
 * @throws {FalloDeRecuperacion}
 */
export async function confirmarRestablecimiento(
  { email, codigo, nuevaPassword, respuestas = [] },
  { fetchImpl = globalThis.fetch } = {},
) {
  const datos = { email, codigo };
  if (respuestas.length > 0) {
    datos.respuestas = respuestas;
  }
  datos.nuevaPassword = nuevaPassword;
  const cuerpo = await publicar(RUTAS_DE_RECUPERACION.confirmar, datos, fetchImpl);
  return typeof cuerpo === 'string' && cuerpo.trim() ? cuerpo.trim() : MENSAJE_DE_CAMBIO;
}
