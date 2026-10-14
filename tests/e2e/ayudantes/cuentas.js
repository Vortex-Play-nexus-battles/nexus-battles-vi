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

import { closeSync, mkdirSync, openSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { expect } from '@playwright/test';

import { baseDelEntorno, correosVistos, enlaceEnElEntorno, esperarCodigo } from './correo.js';
import { pasoDe, totp } from './totp.js';

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

/** El 429 del límite de frecuencia del BORDE (B12, borde-dev.conf). */
const LIMITE_DEL_BORDE = 'demasiadas-peticiones';

/**
 * Repite una petición que el límite de frecuencia del borde rechazó, después
 * de esperar lo que dice su `Retry-After`: lo que hace un cliente educado.
 *
 * ## Por qué (29-sep)
 *
 * El borde admite 30 altas y entradas por minuto y dirección, con ráfaga de
 * 20 (`zone=acceso`). Mientras el host de DEV iba sin memoria, cada petición
 * tardaba segundos y las suites nunca llegaban al límite. Con el host en 4 GiB
 * responden en milisegundos, y una sola suite —registros, entradas,
 * verificaciones, las variantes de «SpiderMan»— pasa de 50 en su primer minuto
 * desde la IP del runner. El límite está bien: es el que protege el login de
 * un bot. Lo que no estaba bien era un cliente de prueba que no lo respetaba.
 *
 * Solo el 429 del borde (`type` `demasiadas-peticiones`). Un 429 de un
 * servicio —los intentos de un código en ms-identidad, el tope de reportes—
 * se devuelve tal cual: es lo que la prueba quiere ver.
 *
 * @template {import('@playwright/test').APIResponse} R
 * @param {() => Promise<R>} peticion
 * @param {{intentos?: number, esperaMaximaMs?: number}} [opciones]
 * @returns {Promise<R>}
 */
export async function respetandoElLimite(peticion, { intentos = 8, esperaMaximaMs = 30_000 } = {}) {
  for (let intento = 1; ; intento += 1) {
    const respuesta = await peticion();
    if (respuesta.status() !== 429 || intento >= intentos) {
      return respuesta;
    }
    let tipo = null;
    try {
      tipo = tipoDe(JSON.parse(await respuesta.text()));
    } catch {
      tipo = null;
    }
    if (tipo !== LIMITE_DEL_BORDE) {
      return respuesta;
    }
    const segundos = Number(respuesta.headers()['retry-after']);
    const espera = Math.min(
      esperaMaximaMs,
      (Number.isFinite(segundos) && segundos > 0 ? segundos : 2) * 1000 * intento,
    );
    await new Promise((resolver) => setTimeout(resolver, espera));
  }
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
    await respetandoElLimite(() =>
      api.post('/api/v1/auth/registro', {
        headers: { Accept: ACEPTA },
        multipart: { nombres, apellidos, email, password: clave, apodo },
      }),
    ),
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
    await respetandoElLimite(() =>
      api.post('/api/v1/auth/login', {
        headers: { Accept: ACEPTA },
        data: { email, password: clave },
      }),
    ),
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
    await respetandoElLimite(() =>
      api.post('/api/v1/auth/verificacion/confirmacion', {
        headers: { Accept: ACEPTA },
        data: { email, codigo },
      }),
    ),
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
    await respetandoElLimite(() =>
      api.post('/api/v1/auth/verificacion/reenvio', {
        headers: { Accept: ACEPTA },
        data: { email },
      }),
    ),
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

// ------------------------------------------------- cuentas administrativas

/**
 * Las cuentas administrativas de pruebas — 7.3.9.
 *
 * Desde que el segundo factor es obligatorio para Moderador, Administrador y
 * Super Administrador, ninguna de esas cuentas entra solo con la contraseña.
 * ms-identidad crea estas tres al arrancar, ya con su secreto TOTP enrolado
 * (`ADMINS_QA`, `CuentaAdministrativaDePruebas`), y aquí está el mismo valor
 * para calcular el código. Son las del BANCO DE PRUEBAS (`tests/e2e/compose.yml`),
 * no las de ningún entorno; contra AWS DEV llegan por `E2E_ADMINS_QA`, un
 * secreto de Actions que nunca se imprime.
 *
 * Formato: `apodo|correo|clave|secretoTotpBase32|ROL`, separadas por `;`.
 */
export const CUENTAS_ADMINISTRATIVAS_DEL_BANCO = [
  'moderadora_e2e|moderadora_e2e@nexus.test|Contrasena-E2E-2026|MUZGKLLNN5SGK4TBMRXXEYJNORXXI4BNMJQW4Y3P|MODERADOR',
  'admin_e2e|admin_e2e@nexus.test|Contrasena-E2E-2026|MUZGKLLBMRWWS3TJON2HEYLEN5ZC25DPORYC2YTDN4|ADMINISTRADOR',
  'super_e2e|super_e2e@nexus.test|Contrasena-E2E-2026|MUZGKLLTOVYGK4TBMRWWS3RNORXXI4BNMJQW4Y3PEE|SUPER_ADMINISTRADOR',
].join(';');

/**
 * Las cuentas administrativas de pruebas del entorno, por apodo.
 *
 * @param {string} [texto]
 * @returns {Map<string, {apodo: string, email: string, clave: string, secreto: string, rol: string}>}
 */
export function cuentasAdministrativas(
  texto = process.env.E2E_ADMINS_QA || CUENTAS_ADMINISTRATIVAS_DEL_BANCO,
) {
  const cuentas = new Map();
  for (const entrada of String(texto ?? '').split(';')) {
    const partes = entrada.split('|').map((parte) => parte.trim());
    if (partes.length < 4 || !partes[0]) {
      continue;
    }
    const [apodo, email, clave, secreto, rol = 'SUPER_ADMINISTRADOR'] = partes;
    cuentas.set(apodo, { apodo, email: email.toLowerCase(), clave, secreto, rol: rol.toUpperCase() });
  }
  return cuentas;
}

/** La cuenta de pruebas de un rol (la primera que lo tenga), o `null`. */
export function cuentaAdministrativaDeRol(rol) {
  for (const cuenta of cuentasAdministrativas().values()) {
    if (cuenta.rol === rol) {
      return cuenta;
    }
  }
  return null;
}

const dormir = (ms) => new Promise((resolver) => setTimeout(resolver, ms));

/**
 * Lo que comparten los workers de Playwright de una misma corrida: la última
 * sesión de cada cuenta administrativa y el último paso TOTP usado. Es un
 * directorio temporal por entorno: el banco y AWS DEV no se mezclan.
 */
function directorioCompartido(base) {
  const directorio = join(
    process.env.E2E_ESTADO ?? join(tmpdir(), 'nexus-e2e-estado'),
    encodeURIComponent(base ?? 'local'),
  );
  mkdirSync(directorio, { recursive: true });
  return directorio;
}

function leerJson(ruta) {
  try {
    return JSON.parse(readFileSync(ruta, 'utf8'));
  } catch {
    return null;
  }
}

/** Escritura atómica: otro worker nunca lee un archivo a medias. */
function escribirJson(ruta, valor) {
  const temporal = `${ruta}.${process.pid}.tmp`;
  writeFileSync(temporal, JSON.stringify(valor), { mode: 0o600 });
  renameSync(temporal, ruta);
}

/**
 * Un cerrojo entre procesos (archivo creado en exclusiva). El servidor no
 * acepta dos veces el mismo paso TOTP ni uno anterior al último: dos workers
 * entrando a la vez con la misma cuenta se pisarían, y cada código rechazado
 * cuenta como intento fallido (RF-AUT-009).
 */
async function conCerrojo(directorio, nombre, trabajo, { esperaMaximaMs = 180_000 } = {}) {
  const ruta = join(directorio, `${nombre}.cerrojo`);
  const inicio = Date.now();
  for (;;) {
    try {
      closeSync(openSync(ruta, 'wx'));
      break;
    } catch (error) {
      if (error?.code !== 'EEXIST') {
        throw error;
      }
      try {
        // Un worker que murió con el cerrojo puesto no bloquea la corrida.
        if (Date.now() - statSync(ruta).mtimeMs > 120_000) {
          unlinkSync(ruta);
          continue;
        }
      } catch {
        continue;
      }
      if (Date.now() - inicio > esperaMaximaMs) {
        throw new Error(`no se pudo tomar el cerrojo de ${nombre}`);
      }
      await dormir(250);
    }
  }
  try {
    return await trabajo();
  } finally {
    try {
      unlinkSync(ruta);
    } catch {
      // Ya no estaba.
    }
  }
}

/**
 * El código TOTP para un paso que el servidor todavía no vio. Espera si hace
 * falta: el siguiente paso al último usado vale desde un paso antes (el
 * servidor admite ±1).
 */
async function codigoNuevo(directorio, cuenta) {
  const archivo = join(directorio, `${cuenta.apodo}.paso.json`);
  const ultimo = leerJson(archivo)?.paso ?? Number.NEGATIVE_INFINITY;
  let paso = Math.max(pasoDe(), ultimo + 1);
  while (paso > pasoDe() + 1) {
    await dormir(1_000);
  }
  escribirJson(archivo, { paso });
  return totp(cuenta.secreto, paso);
}

/** POST a una ruta del login en dos pasos, con problem details. */
async function enviar(api, ruta, datos) {
  return leer(
    await respetandoElLimite(() => api.post(ruta, { headers: { Accept: ACEPTA }, data: datos })),
  );
}

/**
 * Login completo de una cuenta con segundo factor: contraseña, desafío y
 * código. Si la cuenta todavía no lo tiene enrolado (403
 * `segundo-factor-enrolamiento-requerido`: una cuenta recién ascendida a un
 * rol administrativo), lo enrola como lo haría la persona y guarda el secreto
 * para la próxima vez.
 */
async function entrarConSegundoFactor(api, directorio, cuenta) {
  const login = await iniciarSesion(api, cuenta.email, cuenta.clave);
  if (login.estado === 200) {
    return login;
  }
  if (login.estado === 403 && login.tipo === 'segundo-factor-enrolamiento-requerido') {
    const enrolamiento = await enviar(api, '/api/v1/auth/login/segundo-factor/enrolamiento', {
      desafio: login.cuerpo.desafio,
    });
    expect(enrolamiento.estado, `enrolamiento de ${cuenta.apodo}: ${enrolamiento.texto}`).toBe(200);
    cuenta.secreto = enrolamiento.cuerpo.secreto;
    escribirJson(join(directorio, `${cuenta.apodo}.secreto.json`), { secreto: cuenta.secreto });
    const activacion = await enviar(api, '/api/v1/auth/login/segundo-factor/activacion', {
      desafio: login.cuerpo.desafio,
      codigo: await codigoNuevo(directorio, cuenta),
    });
    expect(activacion.estado, `activación de ${cuenta.apodo}: ${activacion.texto}`).toBe(200);
    return activacion;
  }
  expect(
    [login.estado, login.tipo],
    `login de ${cuenta.apodo}: ${login.texto}`,
  ).toEqual([403, 'segundo-factor-requerido']);
  if (!cuenta.secreto) {
    cuenta.secreto = leerJson(join(directorio, `${cuenta.apodo}.secreto.json`))?.secreto ?? null;
  }
  expect(cuenta.secreto, `no hay secreto TOTP conocido para ${cuenta.apodo}`).toBeTruthy();
  // Un rechazo por paso ya visto (otra corrida que dejó el servidor por
  // delante) se reintenta una vez con el paso siguiente, nunca más: cada
  // rechazo cuenta como intento fallido.
  for (let intento = 1; ; intento += 1) {
    const canje = await enviar(api, '/api/v1/auth/login/segundo-factor', {
      desafio: login.cuerpo.desafio,
      codigo: await codigoNuevo(directorio, cuenta),
    });
    if (canje.estado === 200 || intento >= 2) {
      expect(canje.estado, `segundo paso de ${cuenta.apodo}: ${canje.texto}`).toBe(200);
      return canje;
    }
  }
}

/**
 * La sesión de una cuenta administrativa de pruebas, compartida entre los
 * workers de la corrida: si ya hay una, se renueva (`POST
 * /auth/sesion/renovacion`, sin segundo factor: la sesión ya lo pasó); si no,
 * o si ya no se puede renovar, se entra de nuevo con contraseña y código.
 */
async function sesionAdministrativa(api, cuenta, base) {
  const directorio = directorioCompartido(base);
  const archivo = join(directorio, `${cuenta.apodo}.sesion.json`);
  return conCerrojo(directorio, cuenta.apodo, async () => {
    const guardada = leerJson(archivo);
    if (guardada?.token) {
      const renovada = await respetandoElLimite(() =>
        api.post('/api/v1/auth/sesion/renovacion', {
          headers: { Accept: ACEPTA, Authorization: `Bearer ${guardada.token}` },
        }),
      );
      if (renovada.status() === 200) {
        const { token } = await renovada.json();
        const sesion = { ...guardada, token };
        escribirJson(archivo, sesion);
        return sesion;
      }
    }
    const login = await entrarConSegundoFactor(api, directorio, { ...cuenta });
    escribirJson(archivo, login.cuerpo);
    return login.cuerpo;
  });
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
  // 7.3.9 — las cuentas administrativas de pruebas ya existen (ms-identidad
  // las crea al arrancar) y entran en dos pasos.
  const administrativa = cuentasAdministrativas().get(apodo);
  if (administrativa) {
    const sesion = await sesionAdministrativa(api, administrativa, base);
    return {
      ...sesion,
      apodo: sesion.apodo ?? apodo,
      email: administrativa.email,
      claims: cuerpoDelToken(sesion.token),
    };
  }

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
  // Una cuenta de prueba que un spec ascendió a un rol administrativo: desde
  // 7.3.9 entra en dos pasos (y se enrola la primera vez).
  if (
    login.estado === 403 &&
    ['segundo-factor-requerido', 'segundo-factor-enrolamiento-requerido'].includes(login.tipo)
  ) {
    const directorio = directorioCompartido(base);
    login = await conCerrojo(directorio, apodo, () =>
      entrarConSegundoFactor(api, directorio, { apodo, email, clave, secreto: null }),
    );
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
  // 100 s y no los 45 por omisión: si la primera entrega falla (el servicio de
  // correo recién desplegado, en frío), su cola la reintenta a los 30 s y
  // luego al minuto (CORREO_REINTENTOS_ESPERAS). Esperar menos que eso era
  // pedirle a la prueba más de lo que promete el servicio; 29-sep, primera
  // cuenta de la prueba del profesor justo después de un despliegue.
  const correo = await esperarCodigo(email, { tipo: 'verificacion', base, espera: 100_000 });
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
