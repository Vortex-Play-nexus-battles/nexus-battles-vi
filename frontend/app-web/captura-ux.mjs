/**
 * Capturas puntuales para revisar un cambio de UX a ojo — UX-R3.
 *
 *   node captura-ux.mjs <salida> <vista:rol> [vista:rol...]
 *
 * `rol` puede ser `anon`, `JUGADOR`, `MODERADOR`, `ADMINISTRADOR` o
 * `SUPER_ADMINISTRADOR`. Sin backend: se inyecta la sesión que deja el login
 * para que el guard del navegador deje pintar. Ese token no abre nada en el
 * servidor — sirve para fotografiar, no para entrar.
 */
import { chromium } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { spawn } from 'node:child_process';

const PUERTO = 4408;
const RAIZ = `http://127.0.0.1:${PUERTO}/frontend/app-web/src`;
const [salida, ...objetivos] = process.argv.slice(2);
mkdirSync(salida, { recursive: true });

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
function token(rol) {
  const cuerpo = {
    sub: 'qa_ux',
    preferred_username: 'qa_ux',
    uid: randomUUID(),
    rol,
    exp: Math.floor(Date.now() / 1000) + 8 * 3600,
  };
  return `${b64({ alg: 'none', typ: 'JWT' })}.${b64(cuerpo)}.sin-firma`;
}

const servidor = spawn(
  `npx http-server ../.. -p ${PUERTO} -c-1 --silent`,
  { stdio: 'ignore', shell: true },
);
await new Promise((r) => setTimeout(r, 2500));

const navegador = await chromium.launch();
try {
  for (const objetivo of objetivos) {
    const [ruta, rol = 'anon', ancho = '1440', alto = '900'] = objetivo.split('|');
    const ctx = await navegador.newContext({
      viewport: { width: Number(ancho), height: Number(alto) },
    });
    if (rol !== 'anon') {
      await ctx.addInitScript(
        ([t, r]) => {
          sessionStorage.setItem('nexus.token', t);
          sessionStorage.setItem('nexus.apodo', 'qa_ux');
          sessionStorage.setItem('nexus.apodoActual', 'qa_ux');
          sessionStorage.setItem('nexus.rolActual', r);
        },
        [token(rol), rol],
      );
    }
    const pagina = await ctx.newPage();
    const errores = [];
    pagina.on('pageerror', (e) => errores.push(String(e).slice(0, 160)));
    await pagina.goto(`${RAIZ}/${ruta}`, { waitUntil: 'networkidle' }).catch(() => {});
    await pagina.waitForTimeout(700);
    const nombre = `${ruta.replace(/[/.]/g, '-')}-${rol}-${ancho}`;
    await pagina.screenshot({ path: `${salida}/${nombre}.png`, fullPage: false });
    const desborde = await pagina.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    );
    console.log(
      `${nombre}  url=${new URL(pagina.url()).pathname.split('/').pop()}` +
        `${desborde ? '  DESBORDA' : ''}${errores.length ? `  ERR:${errores[0]}` : ''}`,
    );
    await ctx.close();
  }
} finally {
  await navegador.close();
  servidor.kill();
}
