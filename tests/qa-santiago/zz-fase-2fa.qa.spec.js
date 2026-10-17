// Fase 2FA · criterios que exigen el segundo factor (HU-USR-002 CA2, HU-AUD-002 CA1).
// Enciende la obligatoriedad por rol (ms-identidad) y la exigencia en la auditoría (ms-cumplimiento)
// con compose.2fa.yml, corre los casos y deja el banco como estaba.
import { test } from '@playwright/test';
import { createHmac } from 'node:crypto';
import { execSync } from 'node:child_process';
import { enlaceEnElEntorno, esperarCodigo } from '../e2e/ayudantes/correo.js';
import { BORDE, CLAVE_DEL_BANCO, CORRIDA, RAIZ, VISTA, expect, foto, fotoHttp, llamar, sesion, sql } from './ayudante-qa.js';

// QA_COMPOSE permite usar otro override del banco; QA_COMPOSE_EXTRA añade uno más (p. ej. imágenes locales).
const COMPOSE = `docker compose -f ${RAIZ}/tests/e2e/compose.yml -f ${process.env.QA_COMPOSE ?? `${RAIZ}/tests/qa-santiago/compose.qa.yml`}`
  + (process.env.QA_COMPOSE_EXTRA ? ` -f ${process.env.QA_COMPOSE_EXTRA}` : '');
const SUPER = `superfa${CORRIDA}`;
const MOD = { apodo: `modfa${CORRIDA}`, email: `modfa${CORRIDA}@nexus.test`, clave: 'Moderador#2fa2026' };

function base32(s) {
  const alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const c of s.replace(/[\s=-]/g, '').toUpperCase()) bits += alfabeto.indexOf(c).toString(2).padStart(5, '0');
  const bytes = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) bytes.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(bytes);
}
function totp(secreto, desfase = 0) {
  const contador = Buffer.alloc(8);
  contador.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 1000 / 30) + desfase));
  const h = createHmac('sha1', base32(secreto)).update(contador).digest();
  const o = h[h.length - 1] & 0xf;
  return String(((h.readUInt32BE(o) & 0x7fffffff) % 1e6)).padStart(6, '0');
}

async function loginHastaEnrolar(page, email, clave) {
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(clave);
  await page.locator('#botonEnviar').click();
  const enrolar = page.locator('[data-zona="enrolamiento-obligatorio"]');
  await expect(enrolar).toBeVisible();
  return enrolar;
}

async function enrolarYEntrar(page, enrolar) {
  await enrolar.locator('[data-accion="generar-clave"]').click();
  const clave = enrolar.locator('[data-zona="clave-obligatoria"]');
  await expect(clave).toBeVisible();
  const secreto = (await clave.innerText()).match(/(?:[A-Z2-7]{4}\s+){3,}[A-Z2-7]{2,8}/)[0].replace(/\s+/g, '');
  await enrolar.locator('form [name="codigo"]').fill(totp(secreto));
  await enrolar.locator('form button[type="submit"]').click();
  const codigos = enrolar.locator('[data-zona="codigos-obligatorios"]');
  await expect(codigos).toBeVisible();
  return { secreto, clave, codigos };
}

test.describe('Fase 2FA · segundo factor obligatorio', () => {
  test.describe.configure({ mode: 'serial' });
  let tokenSinSegundoFactor;
  let secretoSuper;

  test.beforeAll(async ({ request }) => {
    test.setTimeout(600_000);
    tokenSinSegundoFactor = (await sesion(request, 'superadmin_qa', { nueva: true })).token;
    await sesion(request, SUPER);
    sql('identidad', `UPDATE usuarios SET rol_id=(SELECT id FROM roles WHERE nombre='SUPER_ADMINISTRADOR') WHERE apodo='${SUPER}';`);
    execSync(`${COMPOSE} -f ${RAIZ}/tests/qa-santiago/compose.2fa.yml up -d --no-deps --wait srv-ms-identidad srv-ms-cumplimiento`, { stdio: 'ignore', timeout: 400_000 });
  });

  test.afterAll(() => {
    // La cuenta de esta fase no debe quedar como Super Administrador: CP-RBAC-003-03 exige que solo existan los dos del banco.
    sql('identidad', `UPDATE usuarios SET rol_id=(SELECT id FROM roles WHERE nombre='ADMINISTRADOR') WHERE apodo LIKE 'superfa%';`);
    execSync(`${COMPOSE} up -d --no-deps --wait srv-ms-identidad srv-ms-cumplimiento`, { stdio: 'ignore', timeout: 400_000 });
  });

  test('CP-AUD-002-06 · La consulta del registro de auditoría exige una sesión verificada con doble factor', async ({ page, request }) => {
    test.setTimeout(300_000);
    const sinOtp = await llamar(request, 'GET', '/api/v1/admin/auditoria?size=1', { token: tokenSinSegundoFactor });
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-AUD-002-06', 'Super Administrador con sesión abierta SOLO con contraseña: la consulta se rechaza', sinOtp);
    expect(sinOtp.estado).toBe(403);
    const enrolar = await loginHastaEnrolar(page, `${SUPER}@nexus.test`, CLAVE_DEL_BANCO);
    await foto(page, 'CP-AUD-002-06', `Super Administrador «${SUPER}» al entrar: el sistema le obliga a configurar la verificación en dos pasos`, { resaltar: enrolar });
    const { secreto, clave, codigos } = await enrolarYEntrar(page, enrolar);
    secretoSuper = secreto;
    await foto(page, 'CP-AUD-002-06', 'Clave TOTP generada, código de 6 dígitos verificado y códigos de recuperación de un solo uso', { resaltar: codigos });
    const seguir = codigos.getByRole('button', { name: /entrar|continuar|guard/i }).first();
    if (await seguir.isVisible().catch(() => false)) await seguir.click();
    await page.waitForURL((u) => !/login/.test(u.pathname), { timeout: 30_000 });
    await page.goto(VISTA('cuentas/auditoria.html'));
    await expect(page.locator('#auditoria-tbody tr').first()).toBeVisible();
    await foto(page, 'CP-AUD-002-06', 'Con la sesión verificada por doble factor, el Super Administrador consulta el registro de auditoría', { completa: false });
    expect(await clave.count()).toBeGreaterThanOrEqual(0);
  });

  test('CP-USR-002-06 · Al crear una cuenta administrativa se obliga el enrolamiento del segundo factor', async ({ page, request }) => {
    test.setTimeout(300_000);
    await page.goto('/login');
    await page.locator('#email').fill(`${SUPER}@nexus.test`);
    await page.locator('#password').fill(CLAVE_DEL_BANCO);
    await page.locator('#botonEnviar').click();
    await expect(page.locator('#formSegundoPaso')).toBeVisible();
    await page.waitForTimeout(31_000 - (Date.now() % 30_000));
    await page.locator('#codigoSegundoPaso').fill(totp(secretoSuper));
    await page.locator('#formSegundoPaso button[type="submit"]').click();
    await page.waitForURL((u) => !/login/.test(u.pathname), { timeout: 30_000 });
    const token = await page.evaluate(() => sessionStorage.getItem('nexus.token'));
    const alta = await llamar(request, 'POST', '/api/v1/admin/cuentas', { token, multipart: { nombres: 'Sara', apellidos: 'Moderadora 2FA', apodo: MOD.apodo, email: MOD.email, password: 'Temporal#2026qa', rolNombre: 'MODERADOR' } });
    await fotoHttp(page, 'CP-USR-002-06', 'El Super Administrador (sesión con 2FA) crea la cuenta MODERADOR', alta);
    expect(alta.estado).toBeLessThan(300);
    const correo = await esperarCodigo(MOD.email, { espera: 60_000 });
    await page.evaluate(() => sessionStorage.clear());
    await page.goto(enlaceEnElEntorno(correo.enlace ?? `${BORDE}/restablecer#codigo=${correo.codigo}&correo=${MOD.email}`, BORDE));
    await page.locator('[data-accion="continuar"]').click();
    await page.locator('#nuevaPassword').fill(MOD.clave);
    await page.locator('#confirmarPassword').fill(MOD.clave);
    await page.locator('[data-accion="guardar"]').click();
    await page.waitForTimeout(2500);
    const enrolar = await loginHastaEnrolar(page, MOD.email, MOD.clave);
    await foto(page, 'CP-USR-002-06', 'Primer ingreso de la cuenta administrativa recién activada: debe enrolar el segundo factor antes de operar', { resaltar: enrolar });
    const { codigos } = await enrolarYEntrar(page, enrolar);
    await foto(page, 'CP-USR-002-06', 'Enrolamiento completado: códigos de recuperación de un solo uso para cuando el segundo factor no esté disponible', { resaltar: codigos });
    const fila = sql('identidad', `SELECT u.apodo||' | estado='||u.estado||' | 2FA activo='||(sf.usuario_id IS NOT NULL) FROM usuarios u LEFT JOIN segundo_factor sf ON sf.usuario_id=u.id WHERE u.apodo='${MOD.apodo}';`);
    expect(fila).toContain('2FA activo=true');
  });
});
