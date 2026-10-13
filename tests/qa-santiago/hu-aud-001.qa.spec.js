// HU-AUD-001 · Registro de auditoría de acciones administrativas (Juan Diego)
import { test } from '@playwright/test';
import { CORRIDA, VISTA, aceptarDialogo, contenedor, entrar, esperar, esperarSano, expect, foto, fotoTexto, sesion, sql } from './ayudante-qa.js';

async function abrirAuditoria(page) {
  await page.goto(VISTA('cuentas/auditoria.html'));
  await expect(page.locator('#auditoria-tbody tr').first()).toBeVisible();
}

test.describe('HU-AUD-001 · Registro de auditoría de acciones administrativas', () => {
  test('CP-AUD-001-01 · Cada acción administrativa registra los 7 campos exigidos', async ({ page, request }) => {
    const apodo = `audit${CORRIDA}`;
    await sesion(request, apodo);
    const id = sql('identidad', `SELECT id FROM usuarios WHERE apodo='${apodo}';`);
    await entrar(page, 'admin_e2e');
    await page.goto(VISTA(`cuentas/gestion-usuarios.html?usuario=${id}`));
    await expect(page.locator('#panel-usuario')).toBeVisible();
    await page.locator('#nombres').fill('Nombre Auditado');
    await page.locator('#btn-guardar-perfil').click();
    if (await page.getByRole('dialog').isVisible({ timeout: 1500 }).catch(() => false)) await aceptarDialogo(page);
    await expect(page.locator('#mensaje-perfil')).toBeVisible();
    await foto(page, 'CP-AUD-001-01', `Acción administrativa: admin_e2e edita el perfil del usuario ${apodo} (ID ${id})`, { resaltar: page.locator('#formulario-perfil-admin') });
    await entrar(page, 'superadmin_qa');
    await abrirAuditoria(page);
    const fila = page.locator('#auditoria-tbody tr').filter({ hasText: 'Edición administrativa de perfil' }).first();
    await expect(fila).toBeVisible();
    await foto(page, 'CP-AUD-001-01', 'Registro de auditoría: fecha y hora, administrador, tipo de acción, afectado, valor anterior, valor nuevo, motivo e IP', { resaltar: [page.locator('thead'), fila] });
    const bd = sql('cumplimiento', `SELECT 'fecha_hora='||fecha_hora||E'\\nadministrador_id='||administrador_id||E'\\ntipo_accion='||tipo_accion||E'\\nafectado='||afectado||E'\\nvalor_anterior='||coalesce(valor_anterior,'—')||E'\\nvalor_nuevo='||coalesce(valor_nuevo,'—')||E'\\nmotivo='||motivo||E'\\nip_origen='||ip_origen FROM audit_log WHERE afectado='${id}' AND motivo='Edición administrativa de perfil' ORDER BY fecha_hora DESC LIMIT 1;`);
    await fotoTexto(page, 'CP-AUD-001-01', 'Asiento en la base del servicio de auditoría con sus 7 campos', 'audit_log (ms-cumplimiento)', bd);
    for (const c of ['fecha_hora=', 'administrador_id=', 'tipo_accion=ACTUALIZACION', 'afectado=', 'valor_anterior=', 'valor_nuevo=', 'motivo=', 'ip_origen=']) expect(bd).toContain(c);
    expect(bd).toContain('Nombre Auditado');
  });

  test('CP-AUD-001-02 · El registro es síncrono: si la auditoría no responde, la acción administrativa no se consuma', async ({ page, request }) => {
    test.setTimeout(300_000);
    const apodo = `sincro${CORRIDA}`;
    await sesion(request, apodo);
    const id = sql('identidad', `SELECT id FROM usuarios WHERE apodo='${apodo}';`);
    await entrar(page, 'admin_e2e');
    await page.goto(VISTA(`cuentas/gestion-usuarios.html?usuario=${id}`));
    await expect(page.locator('#panel-usuario')).toBeVisible();
    const antes = sql('identidad', `SELECT nombres FROM perfiles_usuario WHERE usuario_id=${id};`);
    contenedor('stop', 'srv-ms-cumplimiento');
    try {
      await page.locator('#nombres').fill('Cambio Sin Auditoria');
      await page.locator('#btn-guardar-perfil').click();
      if (await page.getByRole('dialog').isVisible({ timeout: 1500 }).catch(() => false)) await aceptarDialogo(page);
      const mensaje = page.locator('#mensaje-perfil');
      await expect(mensaje).toBeVisible({ timeout: 30_000 });
      await foto(page, 'CP-AUD-001-02', 'Con ms-cumplimiento DETENIDO, el administrador intenta guardar: la operación se rechaza', { resaltar: mensaje });
      const despues = sql('identidad', `SELECT nombres FROM perfiles_usuario WHERE usuario_id=${id};`);
      await fotoTexto(page, 'CP-AUD-001-02', 'El perfil NO cambió: sin asiento de auditoría no hay acción administrativa', 'perfiles_usuario.nombres (ms-identidad)', `antes:   ${antes}\ndespués: ${despues}`);
      expect(despues).toBe(antes);
    } finally {
      contenedor('start', 'srv-ms-cumplimiento');
      await esperarSano(request, 'http://localhost:8091/actuator/health');
      await esperar(35_000); // el corta-circuitos de auditoría en ms-identidad se reabre a los 30 s
    }
  });

  test('CP-AUD-001-03 · También se registran las acciones automáticas del propio sistema', async ({ page }) => {
    await entrar(page, 'superadmin_qa');
    await abrirAuditoria(page);
    await page.locator('#filtro-administrador').fill('ms-identidad');
    await page.locator('#btn-filtrar').click();
    await page.waitForTimeout(1500);
    const filas = page.locator('#auditoria-tbody tr');
    await expect(filas.first()).toContainText('ms-identidad');
    await foto(page, 'CP-AUD-001-03', 'Filtro «Administrador = ms-identidad»: acciones automáticas del sistema (alta de jugador completada, cierre de cuenta ejecutado)', { completa: true });
    const sistema = sql('cumplimiento', `SELECT tipo_accion||' | '||motivo||' | '||count(*) FROM audit_log WHERE administrador_id='ms-identidad' GROUP BY tipo_accion, motivo;`);
    await fotoTexto(page, 'CP-AUD-001-03', 'Acciones automáticas auditadas, agrupadas por tipo', 'audit_log WHERE administrador_id = ms-identidad', sistema);
    expect(sistema).toMatch(/ONBOARDING_COMPLETADO/);
  });

  test('CP-AUD-001-04 · La edición de perfil conserva el valor anterior en el asiento', async ({ page }) => {
    const filas = sql('cumplimiento', `SELECT to_char(fecha_hora,'HH24:MI:SS')||' | '||motivo||' | anterior='||coalesce(valor_anterior,'—')||' | nuevo='||left(coalesce(valor_nuevo,'—'),60) FROM audit_log WHERE motivo IN ('Edición administrativa de perfil','Suspensión de cuenta (moderacion-sanciones)','Asignación administrativa de rol') ORDER BY fecha_hora DESC LIMIT 6;`);
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-AUD-001-04', 'Comparación: la suspensión y el cambio de rol guardan el valor anterior; la edición de perfil lo deja vacío', 'audit_log (ms-cumplimiento)', filas);
    const perfil = filas.split(/\r?\n/).filter((l) => l.includes('Edición administrativa de perfil'));
    expect(perfil.length).toBeGreaterThan(0);
    for (const l of perfil) expect(l, 'la edición de perfil debe registrar el valor anterior').not.toContain('anterior=—');
  });
});
