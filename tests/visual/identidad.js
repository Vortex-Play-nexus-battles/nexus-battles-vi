/**
 * Identidad efímera para el laboratorio visual — UX-R2.1.
 *
 * ## El problema que resuelve
 *
 * 20 de las 31 vistas del producto son privadas: sin sesión rebotan al login.
 * Hasta ahora eso significaba que **nadie podía revisar visualmente dos
 * tercios del producto** sin sentarse a escribir una contraseña a mano, y que
 * ninguna comprobación automática podía entrar. El bloque de rediseño
 * necesitaba mirar esas pantallas antes de tocarlas.
 *
 * Este módulo consigue la sesión **por los mecanismos reales del sistema**:
 * registra una cuenta contra `ms-identidad` e inicia sesión con ella, igual
 * que haría el navegador. No hay puerta trasera ni token falsificado cuando
 * hay backend.
 *
 * ## Credenciales
 *
 * Nada está escrito en el repositorio. El apodo lleva marca de tiempo y la
 * contraseña se genera al azar en cada corrida con `crypto.randomBytes`, así
 * que ni siquiera se repite entre ejecuciones. Se puede fijar por variable de
 * entorno para depurar (`VISUAL_CLAVE`), nunca por omisión.
 *
 * ## Sin backend
 *
 * El modo por omisión del arnés sirve `src/` con un servidor estático y sin
 * API: eso es a propósito, porque así se capturan los estados degradados, que
 * son los que más se rompen. En ese modo no hay a quién registrarse, así que
 * se fabrica un JWT **sin firma válida** solo para que el guard del navegador
 * deje pasar y se pueda fotografiar el armazón de la vista.
 *
 * Ese token no abre nada: cualquier servicio real lo rechaza, porque no está
 * firmado por el emisor. Sirve para pintar, no para entrar. Las capturas de
 * ese modo se etiquetan `sin-servicios` para que nadie las confunda con una
 * sesión de verdad.
 */

import { randomBytes, randomUUID } from 'node:crypto';

/** Claves que `login.js` deja en `sessionStorage`. Un solo sitio las nombra. */
export const CLAVES = Object.freeze({
  token: 'nexus.token',
  apodo: 'nexus.apodoActual',
  rol: 'nexus.rolActual',
  usuarioId: 'nexus.usuarioId',
});

/**
 * Contraseña efímera que cumple la política de ms-identidad (9+ caracteres,
 * mayúscula, minúscula, dígito y símbolo). Nunca se escribe en el repositorio
 * ni se reutiliza entre corridas.
 *
 * @returns {string}
 */
export function claveEfimera() {
  if (process.env.VISUAL_CLAVE) {
    return process.env.VISUAL_CLAVE;
  }
  return `Qa-${randomBytes(12).toString('base64url')}-9x`;
}

/** Apodo irrepetible, para no chocar con cuentas anteriores del mismo banco. */
export function apodoEfimero(prefijo = 'qa_visual') {
  return `${prefijo}_${Date.now().toString(36)}${randomBytes(2).toString('hex')}`;
}

function base64url(objeto) {
  return Buffer.from(JSON.stringify(objeto)).toString('base64url');
}

/**
 * JWT sin firma válida, solo para que el guard del navegador deje pintar la
 * vista cuando no hay backend. No autentica contra nada.
 *
 * @param {{apodo: string, rol?: string, horas?: number}} opciones
 * @returns {{token: string, uid: string}}
 */
export function sesionSintetica({ apodo, rol = 'JUGADOR', horas = 8 }) {
  const uid = randomUUID();
  const cuerpo = {
    sub: apodo,
    preferred_username: apodo,
    uid,
    rol,
    exp: Math.floor(Date.now() / 1000) + horas * 3600,
  };
  // La firma es un relleno a propósito: este token no vale para el servidor.
  const token = `${base64url({ alg: 'none', typ: 'JWT' })}.${base64url(cuerpo)}.sin-firma`;
  return { token, uid };
}

/**
 * Registra e inicia sesión de verdad contra la API. Devuelve lo mismo que
 * `login.js` guarda, para poder inyectarlo tal cual.
 *
 * @param {import('@playwright/test').APIRequestContext} api
 * @param {{apodo?: string, rol?: string}} [opciones]
 * @returns {Promise<{apodo: string, token: string, uid: string, rol: string, clave: string}>}
 */
export async function identidadReal(api, { apodo = apodoEfimero() } = {}) {
  const clave = claveEfimera();
  const email = `${apodo}@nexus.test`;

  // Registro por el mismo multipart que manda el formulario del navegador.
  await api.post('/api/v1/auth/registro', {
    multipart: {
      nombres: 'Revisión',
      apellidos: 'Visual',
      email,
      password: clave,
      apodo,
    },
  });

  const entrada = await api.post('/api/v1/auth/login', { data: { email, password: clave } });
  if (!entrada.ok()) {
    throw new Error(
      `El arnés no pudo iniciar sesión como ${apodo}: ${entrada.status()}. ` +
        'Comprueba que ms-identidad responde en la base indicada.',
    );
  }
  const cuerpo = await entrada.json();
  const claims = JSON.parse(Buffer.from(cuerpo.token.split('.')[1], 'base64url').toString('utf8'));

  return {
    apodo: cuerpo.apodo ?? apodo,
    token: cuerpo.token,
    uid: claims.uid ?? cuerpo.usuarioId ?? '',
    rol: cuerpo.rol ?? 'JUGADOR',
    clave,
  };
}

/**
 * Deja la sesión en el navegador **antes** de que cargue la página, que es
 * cuando el guard la lee. Con `addInitScript` el token ya está puesto en el
 * primer `sessionStorage.getItem` de la vista.
 *
 * @param {import('@playwright/test').BrowserContext|import('@playwright/test').Page} donde
 * @param {{token: string, apodo: string, uid: string, rol: string}|null} sesion
 */
export async function inyectarSesion(donde, sesion) {
  if (!sesion) {
    return;
  }
  await donde.addInitScript(
    ([claves, datos]) => {
      window.sessionStorage.setItem(claves.token, datos.token);
      window.sessionStorage.setItem(claves.apodo, datos.apodo);
      window.sessionStorage.setItem(claves.rol, datos.rol);
      window.sessionStorage.setItem(claves.usuarioId, datos.uid);
    },
    [CLAVES, sesion],
  );
}
