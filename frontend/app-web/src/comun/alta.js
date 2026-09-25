/**
 * El alta del jugador, vista desde la interfaz — R17.
 *
 * Una cuenta nueva no está lista para jugar en el mismo instante en que se
 * crea: el servicio de identidad tiene que pedir los créditos de bienvenida a
 * finanzas y el héroe inicial (con su equipo) al inventario. Lo hace solo, en
 * segundo plano y con reintentos (`GET /api/v1/auth/onboarding` cuenta cómo
 * va). Este módulo es lo que la interfaz necesita saber de eso:
 *
 *   - adónde llevar a alguien que acaba de entrar (`destinoTrasEntrar`);
 *   - cómo preguntar por el alta y cómo pedir otro intento.
 *
 * Nada de aquí da créditos ni héroes: los da el servidor. La pantalla solo
 * cuenta lo que el servidor dice que ya está hecho.
 *
 * @module comun/alta
 */

import { rutaDeApi } from './base-api.js';
import { fetchWithHttpErrorInterceptor } from './interceptors/http-error.interceptor.js';
import { RUTAS, resolver, rutaSegura } from './sesion.js';

/** Estados del alta, tal como los devuelve el servidor. */
export const ESTADOS_ALTA = Object.freeze({
  PENDIENTE: 'PENDIENTE',
  EN_PROCESO: 'EN_PROCESO',
  COMPLETO: 'COMPLETO',
  ERROR_REINTENTABLE: 'ERROR_REINTENTABLE',
  NO_APLICA: 'NO_APLICA',
});

/** Estados de un paso. */
export const ESTADOS_PASO = Object.freeze({
  PENDIENTE: 'PENDIENTE',
  HECHO: 'HECHO',
  ERROR: 'ERROR',
});

/** Un fallo al consultar el alta, con lo que la pantalla necesita para decidir. */
export class FalloDelAlta extends Error {
  /**
   * @param {'sin-sesion'|'no-disponible'} tipo
   * @param {number|null} estado
   */
  constructor(tipo, estado = null) {
    super(tipo);
    this.name = 'FalloDelAlta';
    this.tipo = tipo;
    this.estado = estado;
  }
}

/**
 * Traduce la respuesta del servidor. Un 401/403 es que la sesión ya no vale;
 * cualquier otro fallo es que el servicio no contestó bien, que no es lo mismo.
 *
 * @param {Response} respuesta
 */
async function leerAlta(respuesta) {
  if (respuesta.status === 401 || respuesta.status === 403) {
    throw new FalloDelAlta('sin-sesion', respuesta.status);
  }
  if (!respuesta.ok) {
    throw new FalloDelAlta('no-disponible', respuesta.status);
  }
  return respuesta.json();
}

/**
 * Cómo va el alta del jugador de esta sesión.
 *
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 * @returns {Promise<{estado: string, listo: boolean, intentos: number, siguienteIntento: string|null,
 *   pasos: Array<{paso: string, titulo: string, estado: string, motivo: string|null}>,
 *   creditosIniciales: number|null, heroeInicial: string|null}>}
 */
export async function consultarAlta({ fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  let respuesta;
  try {
    respuesta = await fetchImpl(rutaDeApi('/auth/onboarding'), {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
    });
  } catch {
    throw new FalloDelAlta('no-disponible');
  }
  return leerAlta(respuesta);
}

/**
 * Pide otro intento ahora, sin esperar al reintento automático. El servidor
 * ignora las pulsaciones seguidas, así que no hace falta frenarlas aquí.
 *
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export async function reintentarAlta({ fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  let respuesta;
  try {
    respuesta = await fetchImpl(rutaDeApi('/auth/onboarding/reintentos'), {
      method: 'POST',
      headers: { Accept: 'application/json' },
    });
  } catch {
    throw new FalloDelAlta('no-disponible');
  }
  return leerAlta(respuesta);
}

/**
 * Adónde llevar a alguien que acaba de entrar.
 *
 * - Si el alta no ha terminado (`onboardingListo === false`), a «Preparando tu
 *   cuenta», que le lleva después a donde iba.
 * - Si terminó, o la cuenta no tiene alta (anterior a esta versión, o de
 *   administración), directamente a donde iba: la vuelta si la había, si no
 *   su inicio.
 *
 * Solo `false` explícito manda a la preparación: una respuesta de login de
 * antes de R17 no trae el campo, y esa persona no tiene nada que preparar.
 *
 * @param {{onboardingListo?: boolean}|null} respuestaLogin
 * @param {string|null} volver ruta de vuelta ya validada (`rutaDeVuelta`)
 * @param {string} [base] para resolver las rutas (inyectable en pruebas)
 * @returns {string} URL absoluta
 */
export function destinoTrasEntrar(respuestaLogin, volver = null, base = import.meta.url) {
  return destinoConAlta(respuestaLogin?.onboardingListo !== false, volver, base);
}

/**
 * Adónde llevar a quien ACABA DE CREAR su cuenta: a «Preparando tu cuenta»
 * siempre, aunque el alta ya haya terminado.
 *
 * R17.4 — con el alta rápida (el banco E2E, un host sin carga) el login que
 * sigue al registro ya llega con `onboardingListo: true`, y la persona
 * aterrizaba en el inicio sin que nadie le dijera qué le acababan de dar.
 * La pantalla de preparación no simula nada: si el servidor dice que todo
 * está hecho, la primera consulta pinta los cuatro pasos hechos, los
 * créditos y el héroe, y «Empezar a jugar». Es el mismo recibo, llegue
 * rápido o lento, y la primera vez es cuando más falta hace.
 *
 * Solo aplica al registro: el login de una cuenta con el alta terminada va
 * directo a donde iba (`destinoTrasEntrar`).
 *
 * @param {string} [base] para resolver las rutas (inyectable en pruebas)
 * @returns {string} URL absoluta
 */
export function destinoDeCuentaNueva(base = import.meta.url) {
  return destinoConAlta(false, null, base);
}

/**
 * @param {boolean} lista si el alta terminó (o no aplica)
 * @param {string|null} volver ruta de vuelta ya validada
 * @param {string} base
 * @returns {string} URL absoluta
 */
function destinoConAlta(lista, volver, base) {
  const final = volver ? new URL(volver, base).href : resolver(RUTAS.inicio, base);
  if (lista) {
    return final;
  }
  const preparando = new URL(resolver(RUTAS.preparando, base));
  const destino = new URL(final);
  const rutaFinal = rutaSegura(
    `${destino.pathname}${destino.search}${destino.hash}`,
    destino.origin,
  );
  if (rutaFinal) {
    preparando.searchParams.set('volver', rutaFinal);
  }
  return preparando.href;
}
