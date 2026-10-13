// HU-RBAC-003 · Asignación y modificación de roles (Edwin Pedraza)
import { test } from '@playwright/test';
import { CORRIDA, VISTA, aceptarDialogo, entrar, expect, foto, fotoHttp, fotoTexto, llamar, sesion, sql } from './ayudante-qa.js';

async function abrirFicha(page, id) {
  await page.goto(VISTA(`cuentas/gestion-usuarios.html?usuario=${id}`));
  await expect(page.locator('#panel-usuario')).toBeVisible();
  await expect(page.locator('#usuario-apodo-mostrado')).not.toBeEmpty();
}

async function cambiarRol(page, rol) {
  await page.locator('#nuevo-rol').selectOption(rol);
  const resp = page.waitForResponse((r) => /\/rbac\/usuarios\/\d+\/rol$/.test(r.url()), { timeout: 15_000 }).catch(() => null);
  await page.locator('#btn-cambiar-rol').click();
  if (await page.getByRole('dialog').last().isVisible({ timeout: 1500 }).catch(() => false)) await aceptarDialogo(page);
  return resp;
}

test.describe('HU-RBAC-003 · Asignación y modificación de roles', () => {
  test('CP-RBAC-003-01 · El Super Administrador asigna un rol; se invalidan las sesiones del afectado y se audita', async ({ page, browser, request }) => {
    const apodo = `ascenso${CORRIDA}`;
    const afectado = await sesion(request, apodo);
    const id = sql('identidad', `SELECT id FROM usuarios WHERE apodo='${apodo}';`);
    const otra = await browser.newContext();
    const paginaAfectado = await otra.newPage();
    await entrar(paginaAfectado, apodo);
    await paginaAfectado.goto('/cuenta#resumen');
    await expect(paginaAfectado.getByText('JUGADOR').first()).toBeVisible();

    await entrar(page, 'superadmin_qa');
    await abrirFicha(page, id);
    await page.locator('#nuevo-rol').selectOption('MODERADOR');
    await foto(page, 'CP-RBAC-003-01', `Super Administrador: ficha de «${apodo}» (JUGADOR), se elige el nuevo rol MODERADOR`, { resaltar: page.locator('#nuevo-rol') });
    const r = await cambiarRol(page, 'MODERADOR');
    expect(r?.status()).toBeLessThan(300);
    await expect(page.locator('#usuario-rol-mostrado')).toContainText('MODERADOR');
    await foto(page, 'CP-RBAC-003-01', 'Rol aplicado: la ficha muestra MODERADOR', { resaltar: [page.locator('#usuario-rol-mostrado'), page.locator('#mensaje-cambio-rol')] });

    const viejo = await llamar(request, 'GET', '/api/v1/auth/onboarding', { token: afectado.token });
    await fotoHttp(page, 'CP-RBAC-003-01', 'El token que el usuario tenía antes del cambio ya no es aceptado (sesión invalidada)', viejo);
    expect([401, 403]).toContain(viejo.estado);
    expect(JSON.stringify(viejo.cuerpo)).toMatch(/inv[aá]lido|expirado/);
    await otra.close();

    const filas = sql('cumplimiento', `SELECT to_char(fecha_hora,'HH24:MI:SS')||' | '||tipo_accion||' | admin='||administrador_id||' | afectado='||afectado||' | anterior='||coalesce(valor_anterior,'—')||' | nuevo='||coalesce(valor_nuevo,'—')||' | ip='||ip_origen FROM audit_log WHERE tipo_accion ILIKE '%ROL%' AND afectado IN ('${id}','${afectado.claims.uid}') ORDER BY fecha_hora;`);
    await fotoTexto(page, 'CP-RBAC-003-01', 'Asiento de auditoría del cambio de rol con valor anterior y nuevo', 'audit_log (ms-cumplimiento)', filas || '(sin filas)');
    expect(filas).toMatch(/JUGADOR/);
    expect(filas).toMatch(/MODERADOR/);
  });

  test('CP-RBAC-003-02 · Solo el Super Administrador puede asignar roles', async ({ page, request }) => {
    const id = sql('identidad', `SELECT id FROM usuarios WHERE apodo='jugador_qa';`);
    const admin = await sesion(request, 'admin_e2e');
    const moderadora = await sesion(request, 'moderadora_e2e');
    const porAdmin = await llamar(request, 'PUT', `/api/v1/rbac/usuarios/${id}/rol`, { token: admin.token, data: { nuevoRol: 'ADMINISTRADOR' } });
    const porMod = await llamar(request, 'PUT', `/api/v1/rbac/usuarios/${id}/rol`, { token: moderadora.token, data: { nuevoRol: 'MODERADOR' } });
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-RBAC-003-02', 'ADMINISTRADOR y MODERADOR intentan asignar un rol: el servidor lo rechaza (403)', [porAdmin, porMod]);
    expect(porAdmin.estado).toBe(403);
    expect(porMod.estado).toBe(403);
    expect(sql('identidad', `SELECT r.nombre FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.id=${id};`)).toBe('JUGADOR');
  });

  test('CP-RBAC-003-03 · Se impide la auto-degradación del último Super Administrador', async ({ page, request }) => {
    const idSuper1 = sql('identidad', `SELECT id FROM usuarios WHERE apodo='superadmin_qa';`);
    const idSuper2 = sql('identidad', `SELECT id FROM usuarios WHERE apodo='superadmin2_qa';`);
    const otros = sql('identidad', `SELECT string_agg(apodo, ', ') FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE r.nombre='SUPER_ADMINISTRADOR' AND u.apodo NOT IN ('superadmin_qa','superadmin2_qa');`);
    // Precondición: dejar a superadmin_qa como ÚNICO Super Administrador (se degrada al otro por la interfaz).
    await entrar(page, 'superadmin_qa');
    await abrirFicha(page, idSuper2);
    if ((await page.locator('#usuario-rol-mostrado').innerText()).includes('SUPER')) await cambiarRol(page, 'ADMINISTRADOR');
    const supers = sql('identidad', `SELECT count(*) FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE r.nombre='SUPER_ADMINISTRADOR' AND u.estado <> 'ELIMINADO';`);
    await fotoTexto(page, 'CP-RBAC-003-03', 'Precondición: superadmin_qa queda como único Super Administrador activo', 'usuarios con rol SUPER_ADMINISTRADOR', `total = ${supers}${otros ? ` (otros: ${otros})` : ''}`);
    try {
      await entrar(page, 'superadmin_qa');
      await abrirFicha(page, idSuper1);
      const r = await cambiarRol(page, 'ADMINISTRADOR');
      const mensaje = page.locator('#mensaje-cambio-rol');
      await expect(mensaje).toBeVisible();
      await foto(page, 'CP-RBAC-003-03', `El último Super Administrador intenta degradarse a sí mismo: se rechaza${r ? ` (HTTP ${r.status()})` : ''} con el motivo`, { resaltar: mensaje });
      expect(sql('identidad', `SELECT r.nombre FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.id=${idSuper1};`)).toBe('SUPER_ADMINISTRADOR');
      expect(r === null || r.status() >= 400).toBe(true);
    } finally {
      sql('identidad', `UPDATE usuarios SET rol_id=(SELECT id FROM roles WHERE nombre='SUPER_ADMINISTRADOR') WHERE id=${idSuper2};`);
    }
  });

  test('CP-RBAC-003-04 · La sesión invalidada por el cambio de rol deja de valer también en los demás microservicios', async ({ page, browser, request }) => {
    const apodo = `ascenso2${CORRIDA}`;
    const afectado = await sesion(request, apodo);
    const id = sql('identidad', `SELECT id FROM usuarios WHERE apodo='${apodo}';`);
    const otra = await browser.newContext();
    const paginaAfectado = await otra.newPage();
    await entrar(paginaAfectado, apodo);
    await entrar(page, 'superadmin_qa');
    await abrirFicha(page, id);
    const r = await cambiarRol(page, 'MODERADOR');
    expect(r?.status()).toBeLessThan(300);
    await paginaAfectado.goto('/cuenta#resumen');
    await paginaAfectado.waitForTimeout(3000);
    await foto(paginaAfectado, 'CP-RBAC-003-04', 'La pestaña que el usuario tenía abierta ANTES del cambio de rol sigue navegando con su sesión anterior (rol JUGADOR)');
    await otra.close();
    const pruebas = [
      await llamar(request, 'GET', '/api/v1/auth/onboarding', { token: afectado.token }),
      await llamar(request, 'GET', `http://localhost:8093/api/v1/creditos/${afectado.claims.uid}/saldo`, { token: afectado.token }),
      await llamar(request, 'GET', '/api/v1/carrito', { token: afectado.token }),
      await llamar(request, 'GET', '/api/v1/mis-pujas', { token: afectado.token }),
    ];
    await fotoHttp(page, 'CP-RBAC-003-04', 'Token anterior al cambio de rol: ms-identidad lo rechaza, pero finanzas, tienda y subastas lo siguen aceptando', pruebas);
    for (const p of pruebas) expect([401, 403], `${p.url} debería rechazar la sesión invalidada`).toContain(p.estado);
  });
});
