/**
 * Cuentas de prueba: registrar, verificar el correo y entrar — B1.
 *
 * ## Por qué hay un solo sitio para esto
 *
 * Hasta B1 cada spec tenía su propio `sesionDe()`: registrar (tolerando que la
 * cuenta ya existiera) y entrar. Eran dieciocho copias casi idénticas. Con la
 * 2.0.0 de identidad la cuenta nace pendiente de verificar su correo y el
 * login responde 403 `cuenta-no-verificada` hasta que se escribe el código
 * que llegó al buzón. Arreglarlo dieciocho veces era la forma segura de dejar
 * una mal; aquí se arregla una.
 *
 * `sesionDe` hace lo que haría una persona: se registra, lee el código en su
 * correo (Mailpit, `correo.js`), lo confirma y entra. Si la cuenta ya existía
 * y estaba verificada (la sembró `sembrar.sh`, o es de una corrida anterior),
 * entra sin más. Si existía pero sin verificar (una corrida que se cortó a
 * medias), pide otro código y usa el nuevo.
 *
 * Con un servicio anterior a la verificación (la cuenta nace activa y el login
 * entra a la primera), funciona igual: no hay nada que verificar.
 *
 * @module tests/e2e/ayudantes/cuentas
 */

import { expect } from '@playwright/test';

import { baseDelEntorno, correosVistos, enlaceEnElEntorno, esperarCodigo } from './correo.js';

/** La contraseña de los jugadores del banco (la misma que usa `sembrar.sh`). */
export const CLAVE_DEL_BANCO = process.env.E2E_CLAVE ?? 'Contrasena-E2E-2026';

/** Espacio de nombres de los `type` de error del servicio de identidad. */
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';

/** Pide los rechazos como problem details: el motivo sale del `type`. */
const ACEPTA = 'application/problem+json, application/json';

/**
 * Cuerpo de un JWT, sin verificar la firma: aquí solo se lee para afirmar.
 *
 * @param {string} jwt
 */
export function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

/**
 * El motivo de un rechazo: `.../errors/cuenta-no-verificada` → `cuenta-no-verificada`.
 *
 * @param {unknown} cuerpo
 * @returns {string|null}
 */
export function tipoDe(cuerpo) {
  const tipo = cuerpo && typeof cuerpo === 'object' ? cuerpo.type : null;
  return typeof tipo === 'string' && tipo.startsWith(ERRORES) ? tipo.slice(ERRORES.length) : null;
}

/**
 * @param {import('@playwright/test').APIResponse} respuesta
 * @returns {Promise<{estado: number, cuerpo: any, tipo: string|null, texto: string}>}
 */
async function leer(respuesta) {
  const texto = await respuesta.text();
  let cuerpo = texto;
  try {
    cuerpo = JSON.parse(texto);
  } catch {
    // Texto plano (el 200 de `restablecer/*`, o un servicio sin problem details).
  }
  return { estado: respuesta.status(), cuerpo, tipo: tipoDe(cuerpo), texto };
}

/**
 * Registro por el mismo multipart que manda el formulario del navegador.
 *
 * @param {import('@playwright/test').APIRequestContext} api
 * @param {{apodo: string, email: string, clave: string, nombres?: string, apellidos?: string}} datos
 */
export async function registrar(
  api,
  { apodo, email, clave, nombres = 'Jugadora', apellidos = 'De Prueba' },
) {
  return leer(
    await api.post('/api/v1/auth/registro', {
      headers: { Accept: ACEPTA },
      multipart: { nombres, apellidos, email, password: clave, apodo },
    }),
  );
}

/**
 * Login con problem details.
 *
 * @param {import('@playwright/test').APIRequestContext} api
 * @param {string} email
 * @param {string} clave
 */
export async function iniciarSesion(api, email, clave) {
  return leer(
    await api.post('/api/v1/auth/login', {
      headers: { Accept: ACEPTA },
      data: { email, password: clave },
    }),
  );
}

/**
 * Canjea un código de verificación.
 *
 * @param {import('@playwright/test').APIRequestContext} api
 * @param {string} email
 * @param {string} codigo
 */
export async function confirmarCodigo(api, email, codigo) {
  return leer(
    await api.post('/api/v1/auth/verificacion/confirmacion', {
      headers: { Accept: ACEPTA },
      data: { email, codigo },
    }),
  );
}

/**
 * Pide otro código de verificación (respuesta neutra, 202).
 *
 * @param {import('@playwright/test').APIRequestContext} api
 * @param {string} email
 */
export async function pedirOtroCodigo(api, email) {
  return leer(
    await api.post('/api/v1/auth/verificacion/reenvio', {
      headers: { Accept: ACEPTA },
      data: { email },
    }),
  );
}

/**
 * Verifica el correo de una cuenta pendiente con el código del buzón.
 *
 * Primero el código que ya está (el del registro). Si no sirve —caducado,
 * usado o anulado, de una corrida anterior— o no llega, pide otro y espera
 * uno que no estuviera antes. El reenvío tiene un límite de frecuencia en el
 * servidor (60 s por omisión) y, pasado, no manda nada sin decirlo: por eso
 * la segunda espera es larga.
 *
 * @param {import('@playwright/test').APIRequestContext} api
 * @param {string} email
 * @param {{base?: string}} [opciones]
 */
export async function verificarCorreo(api, email, { base = baseDelEntorno() } = {}) {
  let primero = null;
  try {
    primero = await esperarCodigo(email, { tipo: 'verificacion', base, espera: 30_000 });
  } catch {
    primero = null;
  }
  if (primero) {
    const canje = await confirmarCodigo(api, email, primero.codigo);
    if (canje.estado === 200) {
      return canje;
    }
    if (!['codigo-invalido', 'demasiados-intentos'].includes(canje.tipo)) {
      throw new Error(`la verificación de ${email} falló: ${canje.estado} ${canje.texto}`);
    }
  }

  const vistos = await correosVistos(email, { base });
  const reenvio = await pedirOtroCodigo(api, email);
  if (reenvio.estado !== 202 && reenvio.estado !== 200) {
    throw new Error(
      `no se pudo pedir otro código para ${email}: ${reenvio.estado} ${reenvio.texto}`,
    );
  }
  const nuevo = await esperarCodigo(email, {
    tipo: 'verificacion',
    ignorar: vistos,
    base,
    espera: 100_000,
  });
  const canje = await confirmarCodigo(api, email, nuevo.codigo);
  if (canje.estado !== 200) {
    throw new Error(`el código nuevo de ${email} tampoco sirvió: ${canje.estado} ${canje.texto}`);
  }
  return canje;
}

/**
 * La sesión de un jugador de prueba: registra si hace falta, verifica el
 * correo si la cuenta está pendiente y entra.
 *
 * Devuelve lo que devuelve el login (`token`, `apodo`, `rol`, `uid`…) más el
 * apodo, el correo y los claims del token: lo que usaban las copias de cada
 * spec, todo junto.
 *
 * @param {import('@playwright/test').APIRequestContext} api
 * @param {string} apodo
 * @param {{clave?: string, email?: string, nombres?: string, apellidos?: string, base?: string}} [opciones]
 */
export async function sesionDe(
  api,
  apodo,
  {
    clave = CLAVE_DEL_BANCO,
    email = `${apodo}@nexus.test`,
    nombres = 'Jugadora',
    apellidos = 'De Prueba',
    base = baseDelEntorno(),
  } = {},
) {
  const registro = await registrar(api, { apodo, email, clave, nombres, apellidos });
  // 400/409 si ya existe (de la semilla o de una corrida anterior): no es un
  // fallo del flujo. Cualquier otra cosa sí.
  expect([200, 201, 400, 409], `registro de ${apodo}: ${registro.texto}`).toContain(
    registro.estado,
  );

  let login = await iniciarSesion(api, email, clave);
  if (login.estado === 403 && login.tipo === 'cuenta-no-verificada') {
    await verificarCorreo(api, email, { base });
    login = await iniciarSesion(api, email, clave);
  }
  expect(login.estado, `login de ${apodo}: ${login.texto}`).toBe(200);
  return {
    ...login.cuerpo,
    apodo: login.cuerpo.apodo ?? apodo,
    email,
    claims: cuerpoDelToken(login.cuerpo.token),
  };
}

/**
 * Lo que hace una persona tras registrarse desde la vista: está en «Confirma
 * tu correo», abre su buzón, copia el código, lo pega y confirma. Termina en
 * el login, que ya trae el correo escrito.
 *
 * Con `porElEnlace`, en vez de copiar el código abre el enlace del correo, que
 * rellena los dos campos (el otro camino que tiene una persona).
 *
 * @param {import('@playwright/test').Page} page
 * @param {{email: string, base?: string, porElEnlace?: boolean}} opciones
 * @returns {Promise<{codigo: string, enlace: string|null}>}
 */
export async function verificarDesdeLaVista(
  page,
  { email, base = baseDelEntorno(), porElEnlace = false },
) {
  await page.waitForURL(/\/verificar(?:[?#]|$)|verificar-cuenta\.html/, { timeout: 30_000 });
  const correo = await esperarCodigo(email, { tipo: 'verificacion', base });
  if (porElEnlace && correo.enlace) {
    // Se pasa por una página en blanco: si solo cambiara el fragmento, el
    // navegador no recargaría la vista y el enlace no se leería.
    await page.goto('about:blank');
    await page.goto(enlaceEnElEntorno(correo.enlace, base));
    await expect(page.locator('#codigo')).toHaveValue(correo.codigo);
    await expect(page.locator('#email')).toHaveValue(email);
  } else {
    await expect(page.locator('#email')).toHaveValue(email);
    // Como lo pega una persona, y sin `fill`: su valor iría al título del paso
    // en el informe, y el código es un secreto aunque sea de una cuenta de QA.
    const campo = page.locator('#codigo');
    await campo.focus();
    await campo.evaluate((el, valor) => {
      el.value = valor;
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    }, correo.codigo);
  }
  await page.locator('[data-accion="confirmar"]').click();
  await page.waitForURL(/[?&]motivo=verificada/, { timeout: 30_000 });
  return { codigo: correo.codigo, enlace: correo.enlace };
}
