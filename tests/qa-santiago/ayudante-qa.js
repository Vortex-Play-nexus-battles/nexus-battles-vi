/**
 * Ayudante de la campana QA de las 31 HU del Grupo de Santiago.
 *
 * - `foto()` guarda la evidencia de cada caso en evidencias/<CP-ID>/NN.png con
 *   su descripcion (manifest.json), resaltando en rojo el elemento verificado.
 * - `llamar()` hace una peticion HTTP real por el borde y devuelve lo enviado y
 *   lo recibido; `fotoHttp()` lo pinta como tarjeta de evidencia para las
 *   comprobaciones de servidor (autorizacion, falla cerrada, inmutabilidad...).
 * - `entrar()` inicia sesion por la vista de login, igual que una persona.
 */
import { expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { execSync, spawnSync } from 'node:child_process';

import { CLAVE_DEL_BANCO, cuerpoDelToken, sesionDe } from '../e2e/ayudantes/cuentas.js';

export { CLAVE_DEL_BANCO, cuerpoDelToken };
export const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
/** Raíz del monorepo: la suite se ejecuta desde frontend/app-web (QA_RAIZ la fija explícitamente). */
export const RAIZ = process.env.QA_RAIZ ?? path.resolve(process.cwd(), '../..');
/** Salida de la campaña: evidencias, estado entre casos y resultados (ignorada por Git). */
export const SALIDA = process.env.QA_SALIDA ?? path.join(RAIZ, 'tests/qa-santiago/salida');
export const EVIDENCIAS = process.env.QA_EVIDENCIAS ?? path.join(SALIDA, 'evidencias');
/**
 * Ejecuta el wrapper de Maven o Gradle de `dir`: `mvnw.cmd` / `gradlew.bat` en Windows y
 * `sh mvnw` / `sh gradlew` en Linux (CI).
 */
function wrapper(dir, nombre, args) {
  const opciones = { cwd: dir, encoding: 'utf8', maxBuffer: 50e6 };
  if (process.platform !== 'win32') return spawnSync('sh', [path.join(dir, nombre), ...args], opciones);
  const archivo = path.join(dir, nombre === 'mvnw' ? 'mvnw.cmd' : 'gradlew.bat').split('/').join('\\');
  return spawnSync(process.env.ComSpec ?? 'cmd.exe', ['/c', archivo, ...args], opciones);
}
export const VISTA = (ruta) => `/frontend/app-web/src/${ruta}`;

/**
 * Identificador de la corrida: el mismo para todos los specs y para los
 * workers que Playwright reinicia tras un fallo (asi un caso fallido no deja
 * huerfanos a los siguientes). Dura 15 minutos o lo fija QA_CORRIDA.
 */
export const CORRIDA = (() => {
  if (process.env.QA_CORRIDA) return process.env.QA_CORRIDA;
  const archivo = path.join(EVIDENCIAS, '..', 'corrida.txt');
  try {
    const st = fs.statSync(archivo);
    if (Date.now() - st.mtimeMs < 15 * 60e3) return fs.readFileSync(archivo, 'utf8').trim();
  } catch {
    /* primera vez */
  }
  const nueva = Date.now().toString(36).slice(-5);
  fs.mkdirSync(path.dirname(archivo), { recursive: true });
  fs.writeFileSync(archivo, nueva);
  return nueva;
})();

const contadores = new Map();

function directorioDe(id) {
  const dir = path.join(EVIDENCIAS, id);
  if (!contadores.has(id)) {
    fs.rmSync(dir, { recursive: true, force: true });
    contadores.set(id, 0);
  }
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

function anotar(dir, entrada) {
  const manifiesto = path.join(dir, 'manifest.json');
  const lista = fs.existsSync(manifiesto) ? JSON.parse(fs.readFileSync(manifiesto, 'utf8')) : [];
  lista.push(entrada);
  fs.writeFileSync(manifiesto, JSON.stringify(lista, null, 2));
}

/**
 * Captura de evidencia de un caso de prueba.
 * @param {import('@playwright/test').Page} page
 * @param {string} id        CP-XXX-NNN-NN
 * @param {string} descripcion  que muestra la captura
 * @param {{resaltar?: any, completa?: boolean}} [opciones]
 */
export async function foto(page, id, descripcion, { resaltar, completa = false } = {}) {
  const dir = directorioDe(id);
  const n = contadores.get(id) + 1;
  contadores.set(id, n);
  const marcados = [];
  for (const loc of [resaltar].flat().filter(Boolean)) {
    try {
      const el = loc.first();
      await el.scrollIntoViewIfNeeded({ timeout: 3000 });
      await el.evaluate((nodo) => {
        nodo.setAttribute('data-qa-previo', nodo.style.outline || '');
        nodo.style.outline = '3px solid #e11d48';
        nodo.style.outlineOffset = '2px';
      });
      marcados.push(el);
    } catch {
      /* el resaltado es cosmetico: nunca tumba la prueba */
    }
  }
  const archivo = path.join(dir, `${String(n).padStart(2, '0')}.png`);
  await page.screenshot({ path: archivo, fullPage: completa });
  for (const el of marcados) {
    await el
      .evaluate((nodo) => {
        nodo.style.outline = nodo.getAttribute('data-qa-previo') || '';
        nodo.removeAttribute('data-qa-previo');
      })
      .catch(() => {});
  }
  anotar(dir, { archivo: path.basename(archivo), descripcion });
}

const SENSIBLE = /password|clave|contrasena|numeroTarjeta|numero_tarjeta|cvv|cvc|codigoSeguridad|client_secret/i;
function enmascarar(valor) {
  if (!valor || typeof valor !== 'object' || Buffer.isBuffer(valor)) return valor;
  return Object.fromEntries(
    Object.entries(valor).map(([k, v]) => [
      k,
      SENSIBLE.test(k) && typeof v === 'string'
        ? /tarjeta/i.test(k) ? `•••• •••• •••• ${v.slice(-4)}` : '••••••••'
        : v && typeof v === 'object' ? enmascarar(v) : v,
    ]),
  );
}

function abreviarToken(valor) {
  return typeof valor === 'string' && valor.length > 40
    ? `${valor.slice(0, 18)}…${valor.slice(-10)}`
    : valor;
}

/**
 * Peticion HTTP real por el borde.
 * @returns {Promise<{metodo:string,url:string,estado:number,cuerpo:any,texto:string,enviado:any,token?:string}>}
 */
export async function llamar(api, metodo, url, { token, data, multipart, form, headers = {} } = {}) {
  const cabeceras = { Accept: 'application/problem+json, application/json', ...headers };
  if (token) cabeceras.Authorization = `Bearer ${token}`;
  const opciones = { headers: cabeceras, failOnStatusCode: false };
  if (data !== undefined) opciones.data = data;
  if (multipart !== undefined) opciones.multipart = multipart;
  if (form !== undefined) opciones.form = form;
  let respuesta;
  for (let intento = 0; intento < 6; intento += 1) {
    respuesta = await api.fetch(url, { method: metodo, ...opciones });
    if (respuesta.status() !== 429) break;
    await new Promise((r) => setTimeout(r, 2500 * (intento + 1)));
  }
  const texto = await respuesta.text();
  let cuerpo = texto;
  try {
    cuerpo = JSON.parse(texto);
  } catch {
    /* no es JSON */
  }
  return {
    metodo,
    url,
    estado: respuesta.status(),
    cuerpo,
    texto,
    cabecerasRespuesta: respuesta.headers(),
    enviado: {
      cabeceras: Object.fromEntries(
        Object.entries(cabeceras).map(([k, v]) => [k, k === 'Authorization' ? `Bearer ${abreviarToken(token)}` : v]),
      ),
      cuerpo: enmascarar(data ?? multipart ?? form ?? null),
    },
  };
}

const escapar = (s) =>
  String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]);

function json(valor, max = 1800) {
  if (valor === null || valor === undefined || valor === '') return '—';
  const texto = typeof valor === 'string' ? valor : JSON.stringify(valor, null, 2);
  return escapar(texto.length > max ? `${texto.slice(0, max)}\n… (${texto.length - max} caracteres más)` : texto);
}

/**
 * Pinta uno o varios intercambios HTTP como tarjeta de evidencia y la captura.
 * @param {import('@playwright/test').Page} page  cualquier pagina del contexto
 */
export async function fotoHttp(page, id, descripcion, intercambios, { nota } = {}) {
  const tarjeta = await page.context().newPage();
  await tarjeta.setViewportSize({ width: 1180, height: 260 });
  const bloques = [intercambios]
    .flat()
    .map((x) => {
      const ok = x.estado < 400;
      return `<section>
        <div class="linea"><span class="met">${escapar(x.metodo)}</span> <span class="url">${escapar(BORDE + x.url)}</span></div>
        <div class="cols">
          <div><h3>Solicitud</h3><pre>${json(x.enviado?.cabeceras)}</pre>${
            x.enviado?.cuerpo ? `<pre>${json(x.enviado.cuerpo, 900)}</pre>` : ''
          }</div>
          <div><h3>Respuesta <span class="estado ${ok ? 'ok' : 'ko'}">HTTP ${x.estado}</span></h3><pre>${json(x.cuerpo)}</pre></div>
        </div></section>`;
    })
    .join('');
  await tarjeta.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>
    body{font-family:Segoe UI,Arial,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:22px}
    header{display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #334155;padding-bottom:10px;margin-bottom:14px}
    header h1{font-size:18px;margin:0;color:#f8fafc} header span{font-size:12px;color:#94a3b8}
    .id{background:#4338ca;color:#fff;border-radius:6px;padding:3px 8px;font-size:12px;margin-right:8px}
    section{background:#1e293b;border:1px solid #334155;border-radius:10px;padding:12px 14px;margin-bottom:12px}
    .linea{font-family:Consolas,monospace;font-size:14px;margin-bottom:8px}.met{background:#0ea5e9;color:#04121f;font-weight:700;border-radius:4px;padding:2px 6px}
    .url{color:#a5f3fc}.cols{display:grid;grid-template-columns:1fr 1.25fr;gap:12px}
    h3{font-size:12px;text-transform:uppercase;letter-spacing:.06em;color:#94a3b8;margin:4px 0 6px}
    pre{background:#0b1220;border-radius:6px;padding:8px;font-size:11.5px;white-space:pre-wrap;word-break:break-all;margin:0 0 6px;color:#cbd5e1}
    .estado{border-radius:4px;padding:2px 6px;font-size:12px;color:#04121f;font-weight:700}.ok{background:#4ade80}.ko{background:#fb923c}
    .nota{background:#312e81;border-left:4px solid #818cf8;padding:8px 12px;border-radius:6px;font-size:13px;margin-bottom:12px}
  </style></head><body><header><h1><span class="id">${escapar(id)}</span>${escapar(descripcion)}</h1>
  <span>Evidencia de servidor · ${new Date().toLocaleString('es-CO')}</span></header>
  ${nota ? `<div class="nota">${escapar(nota)}</div>` : ''}${bloques}</body></html>`);
  await foto(tarjeta, id, descripcion, { completa: true });
  await tarjeta.close();
}

/** Tarjeta de evidencia de texto libre (salida de base de datos, bitacora, etc.). */
export async function fotoTexto(page, id, descripcion, titulo, contenido, { nota } = {}) {
  const tarjeta = await page.context().newPage();
  await tarjeta.setViewportSize({ width: 1180, height: 200 });
  await tarjeta.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>
    body{font-family:Segoe UI,Arial,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:22px}
    h1{font-size:18px;margin:0 0 12px}.id{background:#4338ca;color:#fff;border-radius:6px;padding:3px 8px;font-size:12px;margin-right:8px}
    h3{font-size:12px;text-transform:uppercase;color:#94a3b8}
    pre{background:#0b1220;border:1px solid #334155;border-radius:8px;padding:12px;font-size:12px;white-space:pre-wrap;word-break:break-all}
    .nota{background:#312e81;border-left:4px solid #818cf8;padding:8px 12px;border-radius:6px;font-size:13px;margin-bottom:12px}
  </style></head><body><h1><span class="id">${escapar(id)}</span>${escapar(descripcion)}</h1>
  ${nota ? `<div class="nota">${escapar(nota)}</div>` : ''}<h3>${escapar(titulo)}</h3><pre>${escapar(contenido)}</pre></body></html>`);
  await foto(tarjeta, id, descripcion, { completa: true });
  await tarjeta.close();
}

/** Tarjeta de evidencia con HTML propio (tablas de resultados, matrices...). */
export async function fotoHtml(page, id, descripcion, html, { nota } = {}) {
  const tarjeta = await page.context().newPage();
  await tarjeta.setViewportSize({ width: 1180, height: 200 });
  await tarjeta.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>
    body{font-family:Segoe UI,Arial,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:22px}
    h1{font-size:18px;margin:0 0 12px}.id{background:#4338ca;color:#fff;border-radius:6px;padding:3px 8px;font-size:12px;margin-right:8px}
    table{border-collapse:collapse;width:100%;font-size:12.5px;background:#1e293b}
    th,td{border:1px solid #334155;padding:6px 8px;text-align:left} th{background:#0b1220;color:#94a3b8;text-transform:uppercase;font-size:11px}
    .ok{color:#4ade80;font-weight:700}.ko{color:#fb923c;font-weight:700}.na{color:#94a3b8}
    .nota{background:#312e81;border-left:4px solid #818cf8;padding:8px 12px;border-radius:6px;font-size:13px;margin-bottom:12px}
  </style></head><body><h1><span class="id">${escapar(id)}</span>${escapar(descripcion)}</h1>
  ${nota ? `<div class="nota">${escapar(nota)}</div>` : ''}${html}</body></html>`);
  await foto(tarjeta, id, descripcion, { completa: true });
  await tarjeta.close();
}

const sesiones = new Map();
/** Sesion por API de una cuenta del banco (la registra y verifica si hace falta). */
export async function sesion(api, apodo, opciones = {}) {
  const clave = `${apodo}:${opciones.clave ?? ''}`;
  if (!sesiones.has(clave) || opciones.nueva) {
    sesiones.set(clave, await sesionDe(api, apodo, opciones));
  }
  return sesiones.get(clave);
}

/** Inicia sesion por la vista de login, como lo haria la persona. */
export async function entrar(page, apodo, { clave = CLAVE_DEL_BANCO, email = `${apodo}@nexus.test` } = {}) {
  // Si la pestaña ya tiene una sesión, /login redirige: se olvida y se vuelve a cargar.
  await page.goto('/login');
  if (!(await page.locator('#email').isVisible({ timeout: 4000 }).catch(() => false))) {
    await page.evaluate(() => { sessionStorage.clear(); localStorage.clear(); }).catch(() => {});
    await page.goto('/login');
  }
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(clave);
  await page.locator('#botonEnviar').click();
  // Al entrar puede aparecer el aviso «Cambios recientes del catálogo» (HU-PRD-014): se cierra.
  const fuera = () => !/\/login|login\.html/.test(new URL(page.url()).pathname);
  for (let i = 0; i < 60 && !fuera(); i += 1) {
    const aviso = page.getByRole('dialog').filter({ hasText: 'Cambios recientes del catálogo' });
    if (await aviso.isVisible().catch(() => false)) {
      await aviso.getByRole('button').first().click().catch(() => {});
    }
    await page.waitForTimeout(500);
  }
  await page.waitForURL((u) => !/\/login|login\.html/.test(u.pathname), { timeout: 30_000 });
  await page.waitForLoadState('networkidle').catch(() => {});
}

export async function salir(page) {
  await page.evaluate(() => sessionStorage.clear());
}

const BASES = {
  identidad: ['e2e-identidad-db', 'identidad', 'identidaddb'],
  finanzas: ['e2e-finanzas-db', 'finanzas_user', 'finanzas_db'],
  subastas: ['e2e-subastas-db', 'subastas_user', 'subastas_db'],
  ecommerce: ['e2e-ecommerce-db', 'ecom_user', 'ecommerce_db'],
  cumplimiento: ['e2e-cumplimiento-db', 'cumplimiento', 'cumplimientodb'],
  chatbot: ['e2e-chatbot-db', 'chatbot_user', 'chatbot_db'],
  plataforma: ['e2e-plataforma-db', 'plataforma', 'plataformadb'],
};

/** Consulta SQL en la base de un servicio del banco (solo precondiciones y verificacion). */
export function sql(base, consulta, { formato = '-At' } = {}) {
  const [servicio, usuario, bd] = BASES[base];
  return execSync(
    `docker compose -p nexus-e2e exec -T ${servicio} psql -U ${usuario} -d ${bd} ${formato} -v ON_ERROR_STOP=1`,
    { input: consulta, encoding: 'utf8' },
  ).trim();
}

/**
 * Ejecuta una clase JUnit de un servicio Maven del repo y devuelve su reporte surefire.
 * @param {string} modulo  p. ej. 'services/cuentas/ms-identidad'
 * @param {string} clase   nombre simple de la clase de prueba
 */
export function junitMaven(modulo, clase) {
  const dir = path.join(RAIZ, modulo);
  const r = wrapper(dir, 'mvnw', ['-q', '-B', 'test', `-Dtest=${clase}`, '-Djacoco.skip=true']);
  const reportes = path.join(dir, 'target', 'surefire-reports');
  const archivo = fs.readdirSync(reportes).find((f) => f.endsWith(`${clase}.txt`));
  return { salida: `${r.stdout ?? ''}${r.stderr ?? ''}`, reporte: archivo ? fs.readFileSync(path.join(reportes, archivo), 'utf8').trim() : '(sin reporte)', estado: r.status };
}

/** Ejecuta una clase JUnit de un servicio Gradle del repo (proyecto ':services:...'). */
export function junitGradle(proyecto, clase) {
  const r = wrapper(RAIZ, 'gradlew', ['--no-daemon', '-q', `${proyecto}:test`, '--tests', `*${clase}`, '-x', 'jacocoTestCoverageVerification']);
  return { salida: `${r.stdout ?? ''}${r.stderr ?? ''}`, estado: r.status };
}

/** Detiene / arranca un contenedor del banco (pruebas de falla controlada). Al arrancar espera a que esté sano. */
export function contenedor(accion, servicio) {
  execSync(`docker compose -p nexus-e2e ${accion} ${servicio}`, { stdio: 'ignore' });
  if (accion === 'start' || accion === 'restart') {
    const nombre = `nexus-e2e-${servicio}-1`;
    for (let i = 0; i < 90; i += 1) {
      const estado = execSync(`docker inspect --format "{{.State.Health.Status}}" ${nombre}`, { encoding: 'utf8' }).trim();
      if (estado === 'healthy') return;
      execSync('node -e "setTimeout(()=>{},2000)"');
    }
  }
}

export async function esperarSano(api, url, intentos = 60) {
  for (let i = 0; i < intentos; i += 1) {
    const r = await api.get(url, { failOnStatusCode: false }).catch(() => null);
    if (r && r.status() < 500) return;
    await new Promise((res) => setTimeout(res, 2000));
  }
}

export async function esperar(ms) {
  await new Promise((r) => setTimeout(r, ms));
}

export { expect };

const AQUI = typeof __dirname === 'undefined' ? path.resolve(process.cwd(), '../../tests/qa-santiago') : __dirname;
/** Identificadores que dejo semilla-qa.mjs. */
export function datosQa() {
  return JSON.parse(fs.readFileSync(path.join(AQUI, 'datos-qa.json'), 'utf8'));
}

export const TARJETAS = {
  aprobada: { titular: 'Laura Gómez QA', numeroTarjeta: '4242 4242 4242 4242', vencimiento: '12/39', codigoSeguridad: '123' },
  rechazada: { titular: 'Laura Gómez QA', numeroTarjeta: '4000 0000 0000 0002', vencimiento: '12/39', codigoSeguridad: '123' },
  caida: { titular: 'Laura Gómez QA', numeroTarjeta: '4000 0000 0000 0069', vencimiento: '12/39', codigoSeguridad: '123' },
};

/** Credencial de servicio del banco de pruebas (ROLE_SERVICIO), por client_credentials. */
export async function tokenServicio(api, cliente = 'e2e-banco', secreto = 'e2e-secreto-del-banco-de-pruebas') {
  const r = await api.post('/api/v1/auth/token', {
    headers: { Authorization: `Basic ${Buffer.from(`${cliente}:${secreto}`).toString('base64')}`, 'Content-Type': 'application/x-www-form-urlencoded' },
    data: 'grant_type=client_credentials',
  });
  return (await r.json()).access_token;
}

export const FINANZAS_URL = 'http://localhost:8093/api/v1';

/** Entrega objetos del catálogo al inventario de un jugador (canal de servicio, origen ADMINISTRACION). */
export async function entregarObjetos(api, s, productoIds, clave) {
  const servicio = await tokenServicio(api);
  const ids = [];
  for (const [k, productoId] of [productoIds].flat().entries()) {
    const ref = `${clave}-${k}`;
    const r = await llamar(api, 'POST', '/api/v1/inventario/entregas', { token: servicio, headers: { 'Idempotency-Key': ref }, data: { uid: s.claims.uid, origen: 'ADMINISTRACION', referencia: ref, productos: [{ productoId, cantidad: 1 }] } });
    ids.push(r.cuerpo.elementos[0].id);
  }
  return ids;
}

/** Mueve créditos en el libro con la credencial de servicio del banco. */
export async function moverCreditos(api, s, monto, refId, operacion = 'acreditar') {
  const servicio = await tokenServicio(api);
  return llamar(api, 'POST', `${FINANZAS_URL}/creditos/${operacion}`, { token: servicio, data: { uid: s.claims.uid, monto, refId, concepto: 'semilla-qa' } });
}

export async function saldoDe(api, s) {
  return (await llamar(api, 'GET', `${FINANZAS_URL}/creditos/${s.claims.uid}/saldo`, { token: s.token })).cuerpo;
}

/** Pone productos en el carrito y paga por la API de la tienda. */
export async function comprar(api, s, productos, tarjeta = TARJETAS.aprobada) {
  for (const productoId of [productos].flat()) {
    await llamar(api, 'POST', '/api/v1/carrito/items', { token: s.token, data: { productoId, cantidad: 1 } });
  }
  return llamar(api, 'POST', '/api/v1/checkout', {
    token: s.token,
    data: { ...tarjeta, moneda: 'COP' },
    headers: { 'Idempotency-Key': `qa-${Date.now()}-${Math.random().toString(36).slice(2, 7)}` },
  });
}

/** Acepta el dialogo de confirmacion abierto (boton principal). */
export async function aceptarDialogo(page, nombre) {
  const dialogo = page.getByRole('dialog').last();
  await expect(dialogo).toBeVisible();
  const boton = nombre
    ? dialogo.getByRole('button', { name: nombre })
    : dialogo.getByRole('button').filter({ hasNotText: /cancelar|volver|cerrar|^no/i }).last();
  await boton.click();
}

/** Abre la ventana del asistente desde el icono flotante. */
export async function abrirChat(page) {
  const icono = page.locator('button.chatbot-flotante');
  await expect(icono).toBeVisible();
  await icono.click();
  const ventana = page.locator('[data-chatbot-ventana]');
  await expect(ventana).toBeVisible();
  await expect(page.locator('.chatbot-ventana__entrada')).toBeEditable();
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(800);
  return ventana;
}

/** Escribe una pregunta y devuelve el ultimo mensaje del bot (ya con texto). */
export async function preguntar(page, texto) {
  const bots = page.locator('.chatbot-ventana__mensaje--bot');
  const huella = async () => `${await bots.count()}|${(await bots.count()) ? await bots.last().innerText() : ''}`;
  const previo = await huella();
  const entrada = page.locator('.chatbot-ventana__entrada');
  await entrada.fill(texto);
  const respuesta = page.waitForResponse((r) => r.url().includes('/api/v1/chat/mensajes') && r.request().method() === 'POST', { timeout: 20_000 });
  await entrada.press('Enter');
  await respuesta;
  // La bienvenida se retira al primer mensaje: se espera a que cambie el ultimo mensaje del bot.
  await expect
    .poll(huella, { timeout: 20_000 })
    .not.toBe(previo);
  const ultimo = bots.last();
  await expect(ultimo).not.toBeEmpty();
  await ultimo.scrollIntoViewIfNeeded();
  await page.waitForTimeout(500);
  return ultimo;
}
