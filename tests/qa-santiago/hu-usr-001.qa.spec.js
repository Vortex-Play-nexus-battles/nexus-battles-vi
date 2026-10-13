// HU-USR-001 · Edición del perfil propio (Santiago Sanabria)
import { test } from '@playwright/test';
import path from 'node:path';
import { CORRIDA, entrar, expect, foto, fotoTexto, sesion, sql } from './ayudante-qa.js';

const sello = CORRIDA;
const AVATAR = path.resolve(process.cwd(), 'src/cuentas/avatares/mago-de-fuego.jpg');

async function abrirPerfil(page) {
  await page.goto('/cuenta#perfil');
  await expect(page.locator('#formulario-perfil')).toBeVisible();
  await page.waitForLoadState('networkidle').catch(() => {});
}

test.describe('HU-USR-001 · Edición del perfil propio', () => {
  test('CP-USR-001-01 · El jugador ve y edita su perfil, avatar y preferencias', async ({ page, request }) => {
    const apodo = `perfil${sello}`;
    await sesion(request, apodo);
    await entrar(page, apodo);
    await abrirPerfil(page);
    await foto(page, 'CP-USR-001-01', 'Mi cuenta › Perfil: datos actuales del jugador (nombres, apellidos, apodo, preferencias, avatar)', { resaltar: page.locator('#formulario-perfil') });
    await page.locator('#nombres').fill('Valentina');
    await page.locator('#apellidos').fill('Rojas Pérez');
    await page.locator('#preferencias').fill('Idioma: español · Notificaciones de subastas activadas');
    await page.locator('#avatar').setInputFiles(AVATAR);
    const recorte = page.locator('dialog[open]');
    if (await recorte.isVisible().catch(() => false)) await recorte.getByRole('button', { name: /usar|confirmar|aceptar|recortar/i }).first().click();
    await foto(page, 'CP-USR-001-01', 'Datos de entrada: nombres «Valentina», apellidos «Rojas Pérez», preferencias e imagen de avatar', { resaltar: page.locator('#formulario-perfil') });
    await page.locator('[data-accion="guardar-perfil"]').click();
    const aviso = page.locator('[data-zona="aviso"]');
    await expect(aviso).toBeVisible();
    await foto(page, 'CP-USR-001-01', 'Al pulsar «Guardar cambios» el sistema confirma que el perfil se actualizó', { resaltar: aviso });
    await page.reload();
    await abrirPerfil(page);
    await expect(page.locator('#nombres')).toHaveValue('Valentina');
    await expect(page.locator('#apellidos')).toHaveValue('Rojas Pérez');
    await expect(page.locator('#preferencias')).toHaveValue(/Notificaciones de subastas/);
    await expect(page.locator('#formulario-perfil img').first()).toBeVisible();
    await foto(page, 'CP-USR-001-01', 'Tras recargar, los cambios persisten (incluida la foto de perfil)', { resaltar: page.locator('#formulario-perfil') });
  });

  test('CP-USR-001-02 · Un usuario de otro rol (Administrador) también edita su propio perfil', async ({ page }) => {
    await entrar(page, 'admin_e2e');
    await abrirPerfil(page);
    await page.locator('#preferencias').fill(`Panel en modo compacto · ${sello}`);
    await page.locator('[data-accion="guardar-perfil"]').click();
    await expect(page.locator('[data-zona="aviso"]')).toBeVisible();
    await page.reload();
    await abrirPerfil(page);
    await expect(page.locator('#preferencias')).toHaveValue(new RegExp(sello));
    await foto(page, 'CP-USR-001-02', 'Rol ADMINISTRADOR: edita y guarda sus preferencias en su propio perfil', { resaltar: page.locator('#preferencias') });
  });

  test('CP-USR-001-03 · Cambio de apodo válido con confirmación', async ({ page, request }) => {
    const apodo = `apodo${sello}`;
    await sesion(request, apodo);
    await entrar(page, apodo);
    await abrirPerfil(page);
    const nuevo = `Lyra${sello}`;
    await page.locator('#apodo').fill(nuevo);
    await page.locator('[data-accion="guardar-perfil"]').click();
    const dialogo = page.getByRole('dialog');
    await expect(dialogo).toContainText('Cambiar tu apodo');
    await foto(page, 'CP-USR-001-03', `El sistema pide confirmar el cambio de apodo «${apodo}» → «${nuevo}»`, { resaltar: dialogo });
    await dialogo.getByRole('button', { name: 'Cambiar apodo' }).click();
    await expect(page.locator('[data-zona="aviso"]')).toBeVisible();
    await page.reload();
    await abrirPerfil(page);
    await expect(page.locator('#apodo')).toHaveValue(nuevo);
    await foto(page, 'CP-USR-001-03', 'Apodo actualizado y persistido', { resaltar: page.locator('#apodo') });
  });

  test('CP-USR-001-04 · El nuevo apodo pasa por la lista negra del registro', async ({ page, request }) => {
    const apodo = `negra${sello}`;
    await sesion(request, apodo);
    await entrar(page, apodo);
    await abrirPerfil(page);
    await page.locator('#apodo').fill('Sp1d3rm4n');
    await page.locator('[data-accion="guardar-perfil"]').click();
    const dialogo = page.getByRole('dialog');
    if (await dialogo.isVisible().catch(() => false)) await dialogo.getByRole('button', { name: 'Cambiar apodo' }).click();
    const aviso = page.locator('[data-zona="aviso"]');
    await expect(aviso).toBeVisible();
    await expect(aviso).toContainText(/no está permitido|no permitido/i);
    await foto(page, 'CP-USR-001-04', 'Dato «Sp1d3rm4n» (evasión de «spiderman»): el cambio se rechaza con el motivo', { resaltar: [aviso, page.locator('#apodo')] });
    await page.reload();
    await abrirPerfil(page);
    await expect(page.locator('#apodo')).toHaveValue(apodo);
  });

  test('CP-USR-001-05 · El cambio de perfil queda registrado en la auditoría', async ({ page, request }) => {
    const apodo = `audperf${sello}`;
    const s = await sesion(request, apodo);
    await entrar(page, apodo);
    await abrirPerfil(page);
    await page.locator('#nombres').fill('Auditado');
    await page.locator('[data-accion="guardar-perfil"]').click();
    await expect(page.locator('[data-zona="aviso"]')).toBeVisible();
    const filas = sql('cumplimiento', `SELECT to_char(fecha_hora,'HH24:MI:SS')||' | '||tipo_accion||' | '||motivo||' | anterior='||coalesce(valor_anterior,'—')||' | nuevo='||coalesce(valor_nuevo,'—') FROM audit_log WHERE afectado='${s.claims.uid}' ORDER BY fecha_hora;`);
    await fotoTexto(page, 'CP-USR-001-05', 'Asientos de auditoría de la cuenta tras editar el perfil', `audit_log WHERE afectado = ${s.claims.uid}`, filas || '(sin filas)');
    expect(filas, 'debe existir un asiento del cambio de perfil').toMatch(/PERFIL|ACTUALIZACION.*Auditado/i);
  });
});
