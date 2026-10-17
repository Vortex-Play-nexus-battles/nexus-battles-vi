// HU-AUT-004 · Iniciar sesión (Cristian Chaparro)
import { test } from '@playwright/test';
import { correosPara, leerCorreo } from '../e2e/ayudantes/correo.js';
import { CORRIDA, CLAVE_DEL_BANCO, VISTA, expect, foto, fotoTexto, sesion, sql, entrar, esperar } from './ayudante-qa.js';

const sello = CORRIDA;

async function intentarLogin(page, email, clave) {
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(clave);
  await page.locator('#botonEnviar').click();
}

test.describe('HU-AUT-004 · Iniciar sesión', () => {
  test('CP-AUT-004-01 · Credenciales válidas crean la sesión con los permisos del rol', async ({ page }) => {
    await intentarLogin(page, 'admin_e2e@nexus.test', CLAVE_DEL_BANCO);
    await foto(page, 'CP-AUT-004-01', 'Se ingresan correo y contraseña válidos de la cuenta activa admin_e2e (rol Administrador)');
    await page.waitForURL((u) => !/login/.test(u.pathname), { timeout: 30_000 });
    await page.waitForLoadState('networkidle');
    const insignia = page.getByText('Administrador', { exact: true }).first();
    await expect(insignia).toBeVisible();
    const sesionGuardada = await page.evaluate(() => JSON.stringify(Object.fromEntries(Object.entries(sessionStorage).filter(([k]) => !/token/i.test(k)))));
    await foto(page, 'CP-AUT-004-01', 'Sesión creada: entra a la consola con el menú y la insignia del rol Administrador', { resaltar: insignia });
    expect(sesionGuardada).toMatch(/ADMINISTRADOR/);
  });

  test('CP-AUT-004-02 · Cuenta suspendida: se rechaza mostrando el tiempo restante', async ({ page, request }) => {
    const apodo = `susp${sello}`;
    await sesion(request, apodo);
    sql('identidad', `UPDATE usuarios SET estado='SUSPENDIDO', suspendido_hasta = now() + interval '2 hours 30 minutes' WHERE apodo='${apodo}';`);
    await intentarLogin(page, `${apodo}@nexus.test`, CLAVE_DEL_BANCO);
    const rechazo = page.locator('[data-zona="rechazo"]');
    await expect(rechazo).toBeVisible();
    await expect(rechazo).toContainText(/suspendida/i);
    await expect(rechazo).toContainText(/\d/);
    await foto(page, 'CP-AUT-004-02', `Cuenta ${apodo} suspendida 2 h 30 min: el login la rechaza y muestra hasta cuándo / tiempo restante`, { resaltar: rechazo });
  });

  test('CP-AUT-004-03 · Cuenta baneada: el acceso se rechaza de forma permanente', async ({ page, request }) => {
    const apodo = `ban${sello}`;
    await sesion(request, apodo);
    sql('identidad', `UPDATE usuarios SET estado='BANEADO' WHERE apodo='${apodo}';`);
    await intentarLogin(page, `${apodo}@nexus.test`, CLAVE_DEL_BANCO);
    const rechazo = page.locator('[data-zona="rechazo"]');
    await expect(rechazo).toBeVisible();
    await expect(rechazo).toContainText(/banead|permanente/i);
    await foto(page, 'CP-AUT-004-03', `Cuenta ${apodo} baneada: se informa que el acceso está vetado de forma permanente`, { resaltar: rechazo });
  });

  test('CP-AUT-004-04 · Intentos fallidos se registran y al superar el umbral la cuenta se bloquea', async ({ page, request }) => {
    const apodo = `lock${sello}`;
    const email = `${apodo}@nexus.test`;
    await sesion(request, apodo);
    for (let i = 1; i <= 3; i += 1) {
      await intentarLogin(page, email, `ClaveMala#${i}x`);
      const rechazo = page.locator('[data-zona="rechazo"], #estadoLogin').filter({ hasText: /./ }).first();
      await expect(rechazo).toBeVisible();
      if (i === 1) await foto(page, 'CP-AUT-004-04', 'Intento 1 con contraseña incorrecta: «Correo o contraseña incorrectos»', { resaltar: rechazo });
    }
    await intentarLogin(page, email, CLAVE_DEL_BANCO);
    const bloqueo = page.locator('[data-zona="rechazo"]');
    await expect(bloqueo).toContainText(/bloqueada/i);
    await foto(page, 'CP-AUT-004-04', 'Tras 3 intentos fallidos (umbral configurado), incluso la contraseña correcta se rechaza: cuenta bloqueada temporalmente', { resaltar: bloqueo });
    const fila = sql('identidad', `SELECT apodo||' | intentos_fallidos='||intentos_fallidos||' | bloqueado_hasta='||coalesce(to_char(bloqueado_hasta,'YYYY-MM-DD HH24:MI'),'—') FROM usuarios WHERE apodo='${apodo}';`);
    await fotoTexto(page, 'CP-AUT-004-04', 'Contador de intentos y bloqueo temporal persistidos', 'usuarios (ms-identidad)', fila);
    const auditoria = sql('cumplimiento', `SELECT to_char(fecha_hora,'HH24:MI:SS')||' | '||tipo_accion||' | afectado='||afectado||' | motivo='||coalesce(motivo,'')||' | ip='||coalesce(ip_origen,'') FROM audit_log WHERE afectado='${email}' ORDER BY fecha_hora;`);
    await fotoTexto(page, 'CP-AUT-004-04', 'Cada intento fallido queda registrado en la auditoría con su IP', 'audit_log (ms-cumplimiento)', auditoria);
    expect(auditoria.split('\n').length).toBeGreaterThanOrEqual(3);
  });

  test('CP-AUT-004-05 · Acceso desde un dispositivo nuevo: aviso en la app y correo al titular', async ({ browser, request }) => {
    const apodo = `disp${sello}`;
    const email = `${apodo}@nexus.test`;
    await sesion(request, apodo);
    const contexto = await browser.newContext({ userAgent: 'Mozilla/5.0 (X11; Linux x86_64) NexusQA-DispositivoNuevo/1.0', viewport: { width: 1366, height: 860 } });
    const page = await contexto.newPage();
    // Se retrasan las llamadas posteriores al login para poder fotografiar el aviso antes de navegar.
    await page.route('**/api/v1/**', async (ruta) => {
      if (!ruta.request().url().includes('/auth/login')) await esperar(4000);
      await ruta.continue();
    });
    await intentarLogin(page, email, CLAVE_DEL_BANCO);
    const aviso = page.locator('#avisoDispositivo');
    await expect(aviso).toBeVisible();
    await expect(aviso).toContainText('dispositivo nuevo');
    await foto(page, 'CP-AUT-004-05', 'Login desde un navegador/IP no reconocidos: alerta «Detectamos un inicio de sesión desde un dispositivo nuevo»', { resaltar: aviso });
    let correo = null;
    for (let i = 0; i < 30 && !correo; i += 1) {
      for (const r of await correosPara(email)) {
        const c = await leerCorreo(r.ID);
        if (/acceso|dispositivo|inicio de sesi/i.test(c.Subject ?? '')) correo = c;
      }
      if (!correo) await esperar(1000);
    }
    expect(correo, 'correo de aviso de acceso').not.toBeNull();
    await fotoTexto(page, 'CP-AUT-004-05', 'Correo de aviso de acceso recibido por el titular (buzón Mailpit)', `Asunto: ${correo.Subject}  ·  Para: ${email}`, (correo.Text ?? '').slice(0, 1200));
    await contexto.close();
  });

  test('CP-AUT-004-06 · El aviso de dispositivo nuevo ofrece marcarlo como confiable', async ({ browser, request }) => {
    const apodo = `conf${sello}`;
    await sesion(request, apodo);
    const contexto = await browser.newContext({ userAgent: 'Mozilla/5.0 NexusQA-Confiable/2.0', viewport: { width: 1366, height: 860 } });
    const page = await contexto.newPage();
    await page.route('**/api/v1/**', async (ruta) => {
      if (!ruta.request().url().includes('/auth/login')) await esperar(4000);
      await ruta.continue();
    });
    await intentarLogin(page, `${apodo}@nexus.test`, CLAVE_DEL_BANCO);
    await expect(page.locator('#avisoDispositivo')).toBeVisible();
    const opcion = page.getByRole('button', { name: /confiable|confianza|soy yo/i });
    await foto(page, 'CP-AUT-004-06', 'Se busca en el aviso la opción para marcar el dispositivo como confiable', { resaltar: page.locator('#avisoDispositivo') });
    await expect(opcion, 'botón «Marcar como confiable» en el aviso de dispositivo nuevo').toBeVisible({ timeout: 3000 });
    await contexto.close();
  });
});
