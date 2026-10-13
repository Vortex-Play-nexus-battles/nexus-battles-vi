// HU-AUD-002 · Inmutabilidad y acceso restringido al registro de auditoría (Juan Diego)
import { test } from '@playwright/test';
import { execSync } from 'node:child_process';
import { VISTA, entrar, esperar, expect, foto, fotoHttp, fotoTexto, llamar, sesion, sql } from './ayudante-qa.js';

async function abrirAuditoria(page) {
  await page.goto(VISTA('cuentas/auditoria.html'));
  await expect(page.locator('#auditoria-tbody tr').first()).toBeVisible();
}

function intentarSql(consulta) {
  try {
    return { ok: true, salida: sql('cumplimiento', consulta) };
  } catch (e) {
    return { ok: false, salida: `${e.stderr ?? e.message}`.trim() };
  }
}

test.describe('HU-AUD-002 · Inmutabilidad y acceso restringido al registro de auditoría', () => {
  test('CP-AUD-002-01 · Solo el Super Administrador consulta el registro', async ({ page, request }) => {
    await entrar(page, 'admin_e2e');
    await page.goto(VISTA('cuentas/auditoria.html'));
    const bloqueo = page.getByText(/No tienes acceso a esta sección/);
    await expect(bloqueo).toBeVisible();
    await foto(page, 'CP-AUD-002-01', 'Un ADMINISTRADOR abre el registro de auditoría: acceso denegado', { resaltar: bloqueo.locator('..') });
    const admin = await sesion(request, 'admin_e2e');
    const moderadora = await sesion(request, 'moderadora_e2e');
    const superadmin = await sesion(request, 'superadmin_qa');
    const r = [
      await llamar(request, 'GET', '/api/v1/admin/auditoria?size=1', { token: admin.token }),
      await llamar(request, 'GET', '/api/v1/admin/auditoria?size=1', { token: moderadora.token }),
      await llamar(request, 'GET', '/api/v1/admin/auditoria?size=1', { token: superadmin.token }),
    ];
    await fotoHttp(page, 'CP-AUD-002-01', 'Consulta por la API: ADMINISTRADOR 403, MODERADOR 403, SUPER_ADMINISTRADOR 200', r);
    expect(r[0].estado).toBe(403);
    expect(r[1].estado).toBe(403);
    expect(r[2].estado).toBe(200);
    await entrar(page, 'superadmin_qa');
    await abrirAuditoria(page);
    await expect(page.getByRole('button', { name: /editar|eliminar|borrar/i })).toHaveCount(0);
    await foto(page, 'CP-AUD-002-01', 'El SUPER ADMINISTRADOR ve la tabla con las 7 columnas y sin ningún botón de editar o eliminar', { resaltar: page.locator('table').first() });
  });

  test('CP-AUD-002-02 · Filtros por usuario, tipo de acción y rango de fechas', async ({ page }) => {
    await entrar(page, 'superadmin_qa');
    await abrirAuditoria(page);
    await page.locator('#filtro-administrador').fill('admin_e2e');
    await page.locator('#btn-filtrar').click();
    await page.waitForTimeout(1500);
    const filas = page.locator('#auditoria-tbody tr');
    for (const t of await filas.allInnerTexts()) expect(t).toContain('admin_e2e');
    await foto(page, 'CP-AUD-002-02', 'Filtro «Administrador = admin_e2e»: solo sus acciones', { completa: true });
    await page.locator('#btn-limpiar').click();
    await page.locator('#filtro-tipo-accion').selectOption({ label: 'Cambio de rol' });
    await page.locator('#btn-filtrar').click();
    await page.waitForTimeout(1500);
    for (const t of await filas.allInnerTexts()) expect(t).toMatch(/CAMBIO_ROL/);
    await foto(page, 'CP-AUD-002-02', 'Filtro «Tipo de acción = Cambio de rol»', { resaltar: page.locator('#filtro-tipo-accion') });
    await page.locator('#btn-limpiar').click();
    const hoy = new Date();
    const ayer = new Date(Date.now() - 864e5);
    const f = (d) => d.toISOString().slice(0, 10);
    await page.locator('#filtro-desde').fill(f(ayer));
    await page.locator('#filtro-hasta').fill(f(hoy));
    await page.locator('#btn-filtrar').click();
    await page.waitForTimeout(1500);
    await expect(filas.first()).toBeVisible();
    await foto(page, 'CP-AUD-002-02', `Filtro por rango de fechas ${f(ayer)} → ${f(hoy)}`, { resaltar: [page.locator('#filtro-desde'), page.locator('#filtro-hasta')] });
    await page.locator('#filtro-desde').fill('2020-01-01');
    await page.locator('#filtro-hasta').fill('2020-01-31');
    await page.locator('#btn-filtrar').click();
    await page.waitForTimeout(1500);
    await foto(page, 'CP-AUD-002-02', 'Rango sin actividad (enero 2020): la tabla queda vacía', { resaltar: page.locator('#auditoria-estado, #auditoria-tbody').first() });
  });

  test('CP-AUD-002-03 · Ninguna operación distinta de la inserción se permite sobre el registro', async ({ page, request }) => {
    const superadmin = await sesion(request, 'superadmin_qa');
    const id = sql('cumplimiento', `SELECT id FROM audit_log ORDER BY fecha_hora LIMIT 1;`);
    const api = [
      await llamar(request, 'PUT', `/api/v1/admin/auditoria/${id}`, { token: superadmin.token, data: { motivo: 'alterado' } }),
      await llamar(request, 'DELETE', `/api/v1/admin/auditoria/${id}`, { token: superadmin.token }),
    ];
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-AUD-002-03', 'Ni siquiera el SUPER ADMINISTRADOR puede modificar o borrar un asiento: la API no expone PUT ni DELETE', api);
    for (const r of api) expect(r.estado).toBeGreaterThanOrEqual(400);
    const upd = intentarSql(`UPDATE audit_log SET motivo='alterado' WHERE id='${id}';`);
    const del = intentarSql(`DELETE FROM audit_log WHERE id='${id}';`);
    await fotoTexto(page, 'CP-AUD-002-03', 'Directo en la base de datos (con el usuario del servicio): UPDATE y DELETE se rechazan por el disparador append-only', 'psql · audit_log', `> UPDATE audit_log SET motivo='alterado' WHERE id='${id}';\n${upd.salida}\n\n> DELETE FROM audit_log WHERE id='${id}';\n${del.salida}`);
    expect(upd.ok).toBe(false);
    expect(del.ok).toBe(false);
    expect(sql('cumplimiento', `SELECT count(*) FROM audit_log WHERE id='${id}' AND motivo <> 'alterado';`)).toBe('1');
  });

  test('CP-AUD-002-04 · Un intento de modificación se rechaza y queda registrado como incidente de seguridad', async ({ page }) => {
    const id = sql('cumplimiento', `SELECT id FROM audit_log ORDER BY fecha_hora DESC LIMIT 1;`);
    intentarSql(`UPDATE audit_log SET valor_nuevo='manipulado' WHERE id='${id}';`);
    await esperar(1500);
    const log = execSync('docker compose -p nexus-e2e logs --since 3m e2e-cumplimiento-db', { encoding: 'utf8' })
      .split('\n').filter((l) => /INCIDENTE_SEGURIDAD|append-only/.test(l)).slice(-4).join('\n');
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-AUD-002-04', 'Bitácora de PostgreSQL: el disparador registra el INCIDENTE_SEGURIDAD con la operación, el registro y el usuario', 'docker logs e2e-cumplimiento-db | grep INCIDENTE_SEGURIDAD', log || '(sin líneas)');
    expect(log).toContain('INCIDENTE_SEGURIDAD');
    expect(log).toContain(id);
  });

  test('CP-AUD-002-05 · El propio acceso al registro de auditoría queda auditado', async ({ page }) => {
    const antes = sql('cumplimiento', `SELECT count(*) FROM audit_log WHERE administrador_id IN ('superadmin_qa', (SELECT '' )) OR motivo ILIKE '%consulta%' OR tipo_accion ILIKE '%ACCESO%';`);
    await entrar(page, 'superadmin_qa');
    await abrirAuditoria(page);
    await page.locator('#filtro-administrador').fill('admin_e2e');
    await page.locator('#btn-filtrar').click();
    await page.waitForTimeout(2000);
    const despues = sql('cumplimiento', `SELECT count(*) FROM audit_log WHERE administrador_id IN ('superadmin_qa', (SELECT '' )) OR motivo ILIKE '%consulta%' OR tipo_accion ILIKE '%ACCESO%';`);
    const ultimas = sql('cumplimiento', `SELECT to_char(fecha_hora,'HH24:MI:SS')||' | admin='||administrador_id||' | '||tipo_accion||' | '||motivo FROM audit_log ORDER BY fecha_hora DESC LIMIT 4;`);
    await fotoTexto(page, 'CP-AUD-002-05', 'Tras abrir y filtrar el registro, se buscan asientos del propio acceso del Super Administrador', `asientos de consulta: antes=${antes} · después=${despues}`, ultimas);
    expect(Number(despues), 'consultar el registro debe dejar un asiento de acceso').toBeGreaterThan(Number(antes));
  });
});
