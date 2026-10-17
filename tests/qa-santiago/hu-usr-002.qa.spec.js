// HU-USR-002 · Creación de cuentas administrativas (Santiago Sanabria)
import { test } from '@playwright/test';
import { enlaceEnElEntorno, esperarCodigo } from '../e2e/ayudantes/correo.js';
import { CORRIDA, BORDE, VISTA, entrar, expect, foto, fotoHttp, fotoTexto, llamar, sesion, sql } from './ayudante-qa.js';

const sello = CORRIDA;
const NUEVO = { apodo: `modqa${sello}`, email: `modqa${sello}@nexus.test` };

async function llenarFormulario(page, { apodo, email, rol = 'MODERADOR' }) {
  await page.locator('#nombres').fill('Mateo');
  await page.locator('#apellidos').fill('Vargas Moderador');
  await page.locator('#apodo').fill(apodo);
  await page.locator('#email').fill(email);
  await page.locator('#password').fill('Temporal#2026qa');
  await page.locator('#rol').selectOption(rol);
}

test.describe('HU-USR-002 · Creación de cuentas administrativas', () => {
  test.describe.configure({ mode: 'serial' });

  test('CP-USR-002-01 · El Super Administrador crea una cuenta de Moderador con su rol y permisos', async ({ page }) => {
    await entrar(page, 'superadmin_qa');
    await page.goto(VISTA('cuentas/crear-cuenta-admin.html'));
    await expect(page.locator('#form-crear-cuenta')).toBeVisible();
    await llenarFormulario(page, NUEVO);
    const permisos = page.locator('#permisos');
    await expect(permisos).not.toContainText('Selecciona un rol');
    await foto(page, 'CP-USR-002-01', `Datos: ${NUEVO.apodo} / ${NUEVO.email} / rol MODERADOR. Al elegir el rol se listan sus permisos`, { resaltar: [page.locator('#rol'), permisos], completa: true });
    await page.locator('#btn-crear').click();
    const dialogo = page.getByRole('dialog');
    await expect(dialogo).toBeVisible();
    await foto(page, 'CP-USR-002-01', 'Confirmación previa a la creación', { resaltar: dialogo });
    await dialogo.getByRole('button').filter({ hasText: /crear|confirmar/i }).first().click();
    const resultado = page.locator('#resultado-creacion');
    await expect(resultado).toBeVisible();
    await foto(page, 'CP-USR-002-01', 'Resultado: cuenta administrativa creada', { resaltar: resultado });
    const fila = sql('identidad', `SELECT u.apodo||' | '||u.email||' | rol='||r.nombre||' | estado='||u.estado FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.apodo='${NUEVO.apodo}';`);
    await fotoTexto(page, 'CP-USR-002-01', 'La cuenta quedó con rol MODERADOR', 'usuarios (ms-identidad)', fila);
    expect(fila).toContain('rol=MODERADOR');
  });

  test('CP-USR-002-02 · Solo el Super Administrador puede crear cuentas administrativas', async ({ page, request }) => {
    await entrar(page, 'admin_e2e');
    await page.goto(VISTA('cuentas/crear-cuenta-admin.html'));
    const bloqueo = page.getByText(/No tienes acceso a esta sección/).locator('..');
    await expect(bloqueo).toBeVisible();
    await foto(page, 'CP-USR-002-02', 'Un ADMINISTRADOR que abre la vista ve «acceso denegado» y no el formulario', { resaltar: bloqueo });
    const admin = await sesion(request, 'admin_e2e');
    const jugador = await sesion(request, 'jugador_qa');
    const cuerpo = { nombres: 'Intento', apellidos: 'Indebido', apodo: `intruso${sello}`, email: `intruso${sello}@nexus.test`, rol: 'ADMINISTRADOR' };
    const porAdmin = await llamar(request, 'POST', '/api/v1/admin/cuentas', { token: admin.token, multipart: cuerpo });
    const porJugador = await llamar(request, 'POST', '/api/v1/admin/cuentas', { token: jugador.token, multipart: { ...cuerpo, apodo: `intruso2${sello}`, email: `intruso2${sello}@nexus.test` } });
    await fotoHttp(page, 'CP-USR-002-02', 'El servidor rechaza la creación pedida por un ADMINISTRADOR y por un JUGADOR', [porAdmin, porJugador]);
    expect(porAdmin.estado).toBe(403);
    expect(porJugador.estado).toBe(403);
  });

  test('CP-USR-002-03 · Un correo ya registrado se rechaza', async ({ page }) => {
    await entrar(page, 'superadmin_qa');
    await page.goto(VISTA('cuentas/crear-cuenta-admin.html'));
    await llenarFormulario(page, { apodo: `otro${sello}`, email: 'admin_e2e@nexus.test', rol: 'ADMINISTRADOR' });
    await page.locator('#btn-crear').click();
    const dialogo = page.getByRole('dialog');
    if (await dialogo.isVisible().catch(() => false)) await dialogo.getByRole('button').filter({ hasText: /crear|confirmar/i }).first().click();
    const error = page.locator('#mensaje-error');
    await expect(error).toBeVisible();
    await foto(page, 'CP-USR-002-03', 'Correo «admin_e2e@nexus.test» ya existe: la creación se rechaza con el motivo', { resaltar: error });
  });

  test('CP-USR-002-04 · La creación queda registrada en la auditoría', async ({ page }) => {
    const uid = sql('identidad', `SELECT public_id FROM usuarios WHERE apodo='${NUEVO.apodo}';`);
    const filas = sql('cumplimiento', `SELECT to_char(fecha_hora,'YYYY-MM-DD HH24:MI:SS')||' | admin='||administrador_id||' | '||tipo_accion||' | afectado='||afectado||' | nuevo='||coalesce(valor_nuevo,'—')||' | motivo='||motivo||' | ip='||ip_origen FROM audit_log WHERE afectado IN ('${uid}','${NUEVO.email}','${NUEVO.apodo}') ORDER BY fecha_hora;`);
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-USR-002-04', 'Asiento de auditoría de la creación de la cuenta administrativa', `audit_log · afectado = ${uid}`, filas || '(sin filas)');
    expect(filas).toMatch(/CREACION/);
    expect(filas).toContain('MODERADOR');
  });

  test('CP-USR-002-05 · La cuenta queda inactiva hasta su primera autenticación (activación por correo)', async ({ page }) => {
    const antes = sql('identidad', `SELECT apodo||' | estado='||estado FROM usuarios WHERE apodo='${NUEVO.apodo}';`);
    expect(antes).toContain('estado=INACTIVO');
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-USR-002-05', 'Recién creada, la cuenta administrativa está INACTIVA', 'usuarios (ms-identidad)', antes);
    await page.goto('/login');
    await page.locator('#email').fill(NUEVO.email);
    await page.locator('#password').fill('Temporal#2026qa');
    await page.locator('#botonEnviar').click();
    const rechazo = page.locator('[data-zona="rechazo"]');
    await expect(rechazo).toBeVisible();
    await foto(page, 'CP-USR-002-05', 'Antes de activarla no puede iniciar sesión (la contraseña la define el titular al activar)', { resaltar: rechazo });
    const correo = await esperarCodigo(NUEVO.email, { espera: 60_000 });
    await page.goto(enlaceEnElEntorno(correo.enlace ?? `${BORDE}/restablecer#codigo=${correo.codigo}&correo=${NUEVO.email}`, BORDE));
    await expect(page.locator('#codigo')).toHaveValue(/.+/);
    await foto(page, 'CP-USR-002-05', 'El enlace del correo de activación abre la vista con el código precargado', { resaltar: page.locator('#formCodigo') });
    await page.locator('[data-accion="continuar"]').click();
    await page.locator('#nuevaPassword').fill('Moderador#Nexus26');
    await page.locator('#confirmarPassword').fill('Moderador#Nexus26');
    await page.locator('[data-accion="guardar"]').click();
    await page.waitForURL(/login/, { timeout: 30_000 }).catch(() => {});
    const estado = sql('identidad', `SELECT apodo||' | estado='||estado FROM usuarios WHERE apodo='${NUEVO.apodo}';`);
    expect(estado).toContain('estado=ACTIVO');
    await entrar(page, NUEVO.apodo, { clave: 'Moderador#Nexus26' });
    await page.goto('/cuenta#resumen');
    const rol = page.getByText('MODERADOR', { exact: true }).first();
    await expect(rol).toBeVisible();
    await foto(page, 'CP-USR-002-05', `Tras activarla y definir su contraseña, la cuenta pasa a ACTIVO y entra con rol MODERADOR (${estado})`, { resaltar: rol });
  });
});
