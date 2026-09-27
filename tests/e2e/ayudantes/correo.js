/**
 * La bandeja de pruebas (Mailpit) — B1.
 *
 * ## Por qué las pruebas leen el correo
 *
 * Desde la 2.0.0 de identidad una cuenta nueva nace pendiente de verificar su
 * correo, y el login responde 403 `cuenta-no-verificada` hasta que alguien
 * escribe el código que llegó al buzón. Una prueba que registra jugadores ya
 * no puede entrar sin leer ese código, igual que una persona no puede entrar
 * sin abrir su correo. La recuperación de la contraseña, lo mismo.
 *
 * Solo las direcciones reservadas para pruebas (`.test`, `.example`,
 * `.invalid`, `.localhost`, `.local`) se desvían a Mailpit: por eso todas las
 * cuentas de prueba son `@nexus.test`. Una dirección real iría a su buzón real
 * y la prueba no la podría leer.
 *
 * ## Dónde está el buzón
 *
 * `MAILPIT_URL` manda. Sin ella, `<base>/mailpit`: el borde lo sirve ahí tanto
 * en el banco E2E (`tests/e2e/compose.yml`) como en AWS DEV
 * (`MP_WEBROOT=/mailpit`, `borde-dev.conf`), con la API en
 * `<base>/mailpit/api/v1/...`. Si el borde le pone autenticación básica (B12),
 * `MAILPIT_USUARIO` y `MAILPIT_CLAVE`, que en CI salen de secretos y nunca del
 * repositorio.
 *
 * ## Cómo se encuentra el código
 *
 * Primero, el enlace que manda el servicio de correo desde su 1.4.0:
 * `/verificar#codigo=...&correo=...` o `/restablecer#codigo=...&correo=...`.
 * Si el correo es anterior y no lo trae, la línea del código tal como la pinta
 * la plantilla (sola, en mayúsculas y cifras). Nunca se imprime entero el
 * correo: es de una cuenta de prueba, pero el código es un secreto igual.
 *
 * @module tests/e2e/ayudantes/correo
 */

/** Cuánto se espera por omisión a que llegue un correo. La entrega es asíncrona (correo 1.4.0). */
export const ESPERA_POR_OMISION_MS = 45_000;

/** Entre dos miradas al buzón. */
const PAUSA_MS = 1_000;

/**
 * La base del entorno contra el que corre la prueba, por si quien llama no la
 * da: la del banco, la del profesor o la de AWS, en ese orden.
 *
 * @returns {string}
 */
export function baseDelEntorno() {
  return (
    process.env.E2E_BORDE ??
    process.env.PROFESOR_URL ??
    process.env.E2E_AWS ??
    'http://localhost:8099'
  );
}

/**
 * Raíz del buzón, sin barra final.
 *
 * @param {string} [base] la del borde que usa la prueba
 * @returns {string}
 */
export function urlDelBuzon(base) {
  const explicita = (process.env.MAILPIT_URL ?? '').trim();
  const raiz = explicita || `${String(base ?? baseDelEntorno()).replace(/\/+$/, '')}/mailpit`;
  return raiz.replace(/\/+$/, '');
}

/** Autenticación básica opcional, solo si el entorno la da. */
function cabeceras() {
  const usuario = process.env.MAILPIT_USUARIO;
  const clave = process.env.MAILPIT_CLAVE;
  const base = { Accept: 'application/json' };
  if (!usuario || !clave) {
    return base;
  }
  return {
    ...base,
    Authorization: `Basic ${Buffer.from(`${usuario}:${clave}`).toString('base64')}`,
  };
}

/**
 * @param {string} url
 * @returns {Promise<any>}
 */
async function pedir(url) {
  const respuesta = await fetch(url, { headers: cabeceras() });
  if (!respuesta.ok) {
    const error = new Error(
      `el buzón respondió ${respuesta.status} en ${new URL(url).pathname}` +
        (respuesta.status === 401 ? ' (¿faltan MAILPIT_USUARIO y MAILPIT_CLAVE?)' : ''),
    );
    error.estado = respuesta.status;
    throw error;
  }
  return respuesta.json();
}

/** @param {number} ms */
function dormir(ms) {
  return new Promise((resolver) => setTimeout(resolver, ms));
}

/**
 * Los correos de una dirección, del más nuevo al más viejo (solo el resumen).
 *
 * Mailpit busca por fragmento en `to:`: se filtra por la dirección exacta para
 * que `ana@nexus.test` no se lleve los de `mariana@nexus.test`.
 *
 * @param {string} correo
 * @param {{base?: string}} [opciones]
 * @returns {Promise<Array<{ID: string, Subject: string, Created: string}>>}
 */
export async function correosPara(correo, { base } = {}) {
  const consulta = encodeURIComponent(`to:"${correo}"`);
  const cuerpo = await pedir(`${urlDelBuzon(base)}/api/v1/search?query=${consulta}&limit=50`);
  const buscado = correo.toLowerCase();
  return (Array.isArray(cuerpo?.messages) ? cuerpo.messages : [])
    .filter((m) => (m.To ?? []).some((d) => String(d?.Address ?? '').toLowerCase() === buscado))
    .sort((a, b) => Date.parse(b.Created ?? 0) - Date.parse(a.Created ?? 0));
}

/**
 * Los identificadores de los correos que ya hay para una dirección. Se toma
 * ANTES de pedir un código nuevo, para esperar después uno que no esté aquí.
 *
 * @param {string} correo
 * @param {{base?: string}} [opciones]
 * @returns {Promise<Set<string>>}
 */
export async function correosVistos(correo, { base } = {}) {
  try {
    return new Set((await correosPara(correo, { base })).map((m) => m.ID));
  } catch {
    return new Set();
  }
}

/**
 * El correo completo (texto y HTML).
 *
 * @param {string} id
 * @param {{base?: string}} [opciones]
 */
export async function leerCorreo(id, { base } = {}) {
  return pedir(`${urlDelBuzon(base)}/api/v1/message/${encodeURIComponent(id)}`);
}

/** Lo mismo que `normalizarCodigo` de la interfaz: sin separadores y en mayúsculas. */
function limpiarCodigo(texto) {
  return String(texto ?? '')
    .replace(/[\s\-‐-―_.·]/gu, '')
    .toUpperCase();
}

/** @param {string} texto */
function decodificar(texto) {
  try {
    return decodeURIComponent(texto);
  } catch {
    return texto;
  }
}

/**
 * El código de un correo, su enlace (si lo trae) y para qué es.
 *
 * `tipo` sale del enlace (`/verificar` → verificación; `/restablecer` →
 * recuperación o activación) o, si el correo es anterior a los enlaces, del
 * asunto. `null` si el correo no trae código (bienvenida, avisos).
 *
 * @param {{Subject?: string, Text?: string, HTML?: string}} correo
 * @returns {{codigo: string, enlace: string|null, correo: string|null,
 *   tipo: 'verificacion'|'recuperacion'|null}|null}
 */
export function codigoDelCorreo(correo) {
  const html = String(correo?.HTML ?? '').replace(/&amp;/g, '&');
  const texto = String(correo?.Text ?? '');

  for (const fuente of [texto, html]) {
    const enlace =
      /https?:\/\/[^\s"'<>]+?\/(verificar|restablecer)(?:\?[^#\s"'<>]*)?#[^\s"'<>]+/i.exec(fuente);
    if (!enlace) {
      continue;
    }
    const fragmento = enlace[0].slice(enlace[0].indexOf('#') + 1);
    const valores = Object.fromEntries(
      fragmento.split('&').map((par) => {
        const [clave, ...resto] = par.split('=');
        return [decodificar(clave), decodificar(resto.join('='))];
      }),
    );
    if (valores.codigo) {
      return {
        codigo: limpiarCodigo(valores.codigo),
        enlace: enlace[0],
        correo: valores.correo || null,
        tipo: enlace[1].toLowerCase() === 'verificar' ? 'verificacion' : 'recuperacion',
      };
    }
  }

  // Sin enlace: la línea del código, sola, como la deja la plantilla en el
  // texto plano; y si tampoco, la celda del código en el HTML.
  const enLinea = texto
    .split('\n')
    .map((linea) => linea.trim())
    .find((linea) => /^[A-Z0-9]{6,16}$/.test(linea));
  const enCelda = /letter-spacing:\s*\d+px;[^>]*>\s*([A-Z0-9]{6,16})\s*</.exec(html)?.[1];
  const codigo = enLinea ?? enCelda;
  if (!codigo) {
    return null;
  }
  const asunto = String(correo?.Subject ?? '');
  let tipo = null;
  if (/recupera|restablec/i.test(asunto)) {
    tipo = 'recuperacion';
  } else if (/confirma|verific|activa/i.test(asunto)) {
    tipo = 'verificacion';
  }
  return { codigo, enlace: null, correo: null, tipo };
}

/**
 * Espera el correo con código más reciente de una dirección y devuelve su
 * código. Con `ignorar`, solo vale uno que no estuviera ya (el que acaba de
 * salir tras pedirlo).
 *
 * @param {string} correo
 * @param {{tipo?: 'verificacion'|'recuperacion', ignorar?: Set<string>, base?: string,
 *   espera?: number}} [opciones]
 * @returns {Promise<{codigo: string, enlace: string|null, tipo: string|null, id: string}>}
 */
export async function esperarCodigo(
  correo,
  { tipo, ignorar = new Set(), base, espera = ESPERA_POR_OMISION_MS } = {},
) {
  const limite = Date.now() + espera;
  let ultimoFallo = null;
  do {
    try {
      for (const resumen of await correosPara(correo, { base })) {
        if (ignorar.has(resumen.ID)) {
          continue;
        }
        const hallado = codigoDelCorreo(await leerCorreo(resumen.ID, { base }));
        if (hallado && (!tipo || !hallado.tipo || hallado.tipo === tipo)) {
          return { ...hallado, id: resumen.ID };
        }
      }
    } catch (error) {
      ultimoFallo = error;
    }
    await dormir(PAUSA_MS);
  } while (Date.now() < limite);
  throw new Error(
    `no llegó a ${correo} ningún correo con código${tipo ? ` de ${tipo}` : ''} en ` +
      `${Math.round(espera / 1000)} s (buzón: ${urlDelBuzon(base)})` +
      (ultimoFallo ? `; último fallo: ${ultimoFallo.message}` : ''),
  );
}

/**
 * La misma dirección de un enlace del correo, pero en el entorno de la prueba.
 *
 * El servicio de correo arma el enlace con su `PUBLIC_BASE_URL`; lo que se
 * prueba aquí es la ruta y el fragmento, no ese valor de configuración.
 *
 * @param {string} enlace
 * @param {string} base
 * @returns {string}
 */
export function enlaceEnElEntorno(enlace, base) {
  const destino = new URL(enlace);
  return new URL(`${destino.pathname}${destino.search}${destino.hash}`, base).href;
}
