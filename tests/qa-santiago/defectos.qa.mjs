// Captura la evidencia de los hallazgos menores del reporte de defectos (sección 5 del informe).
import { chromium, request } from '@playwright/test';
import fs from 'node:fs';
import { sesionDe } from '../e2e/ayudantes/cuentas.js';
import { EVIDENCIAS } from './ayudante-qa.js';

const BORDE = 'http://localhost:8099';
const DIR = `${EVIDENCIAS}/defectos`;
fs.mkdirSync(DIR, { recursive: true });
const b = await chromium.launch();
const ctx = await b.newContext({ viewport: { width: 1366, height: 860 }, locale: 'es-CO' });
const p = await ctx.newPage();

async function entrar(apodo) {
  await p.goto(`${BORDE}/login`);
  await p.evaluate(() => sessionStorage.clear());
  await p.goto(`${BORDE}/login`);
  await p.fill('#email', `${apodo}@nexus.test`);
  await p.fill('#password', 'Contrasena-E2E-2026');
  await p.click('#botonEnviar');
  await p.waitForURL((u) => !/login/.test(u.pathname), { timeout: 30000 });
  await p.waitForTimeout(1500);
}
const marcar = (loc) => loc.evaluate((n) => { n.style.outline = '3px solid #e11d48'; n.style.outlineOffset = '2px'; }).catch(() => {});

// DEF-01 · formulario de soporte del asistente
await entrar('comprador_qa');
await p.locator('button.chatbot-flotante').click();
await p.waitForTimeout(1500);
await p.locator('[data-accion="hablar-con-soporte"]').first().click();
const soporte = p.locator('[data-chatbot-soporte]');
await soporte.locator('[name="asunto"]').fill('No puedo ver mis cofres');
await soporte.locator('[name="mensaje"]').fill('Gané 20 créditos y el cofre no aparece.');
await soporte.getByRole('button', { name: /enviar/i }).click();
await p.waitForTimeout(1500);
await marcar(soporte.locator('select').first());
await marcar(soporte.getByText(/Revisa los datos/).locator('..'));
await p.screenshot({ path: `${DIR}/DEF-01-1.png` });

// DEF-02 · crear cuenta administrativa: la contraseña escrita se descarta
await entrar('superadmin_qa');
await p.goto(`${BORDE}/frontend/app-web/src/cuentas/crear-cuenta-admin.html`);
await p.waitForTimeout(1500);
await marcar(p.locator('#password').locator('..'));
await p.screenshot({ path: `${DIR}/DEF-02-1.png`, fullPage: true });
const api = await request.newContext({ baseURL: BORDE });
const s = await sesionDe(api, 'superadmin_qa');
const sello = Date.now().toString(36).slice(-5);
const alta = await api.post('/api/v1/admin/cuentas', { headers: { Authorization: `Bearer ${s.token}` }, multipart: { nombres: 'Def', apellidos: 'Dos', email: `def02${sello}@nexus.test`, apodo: `def02${sello}`, password: 'Temporal#2026qa', rolNombre: 'ADMINISTRADOR' } });
console.log('alta', alta.status());
await p.evaluate(() => sessionStorage.clear());
await p.goto(`${BORDE}/login`);
await p.fill('#email', `def02${sello}@nexus.test`);
await p.fill('#password', 'Temporal#2026qa');
await p.click('#botonEnviar');
await p.waitForTimeout(2000);
await marcar(p.locator('[data-zona="rechazo"]'));
await p.screenshot({ path: `${DIR}/DEF-02-2.png` });

// DEF-03 · token revocado responde 403 en vez de 401
const j = await sesionDe(api, `def03${sello}`);
const ss = await sesionDe(api, 'superadmin_qa');
const id = (await (await api.get(`/api/v1/rbac/usuarios`, { headers: { Authorization: `Bearer ${ss.token}` } }).catch(() => null))?.status?.()) ?? null;
fs.writeFileSync(`${DIR}/def03.json`, JSON.stringify({ apodo: `def03${sello}`, token: j.token }));
console.log('def03 listo', id);

// DEF-04 · tienda: USD y EUR no disponibles mientras no haya tasa configurada (se captura con las tasas vacías)
const admin = await sesionDe(api, 'superadmin_qa');
const poner = async (valor) => {
  for (const clave of ['tienda.tasa-cop-usd', 'tienda.tasa-cop-eur']) {
    const v = valor === null ? null : clave.endsWith('usd') ? '4000' : '4400';
    const r = await api.put(`/api/v1/parametros/${clave}`, { headers: { Authorization: `Bearer ${admin.token}` }, data: { valor: v, motivo: valor === null ? 'Estado por defecto para evidencia QA' : 'Restablecer tasa QA' } });
    console.log('param', clave, r.status());
  }
};
const monedas = async () => {
  const c = await sesionDe(api, 'comprador_qa');
  const r = await api.get('/api/v1/vitrina?pagina=0&tamano=1', { headers: { Authorization: `Bearer ${c.token}` } });
  return (await r.json()).monedasDisponibles;
};
await poner(null);
for (let i = 0; i < 60 && (await monedas()).length > 1; i += 1) await p.waitForTimeout(3000);
await entrar('comprador_qa');
await p.goto(`${BORDE}/frontend/app-web/src/cuentas/tienda.html`);
await p.waitForTimeout(2500);
await marcar(p.locator('#moneda-tienda'));
await marcar(p.locator('#nota-moneda'));
await p.screenshot({ path: `${DIR}/DEF-04-1.png` });
await poner('ok');
fs.writeFileSync(`${DIR}/listo.txt`, 'ok');
await b.close();
await api.dispose();
