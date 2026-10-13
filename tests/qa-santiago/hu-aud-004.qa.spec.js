// HU-AUD-004 · Exportación y conservación del registro de auditoría (Juan Diego)
import { test } from '@playwright/test';
import fs from 'node:fs';
import { EVIDENCIAS, RAIZ, VISTA, entrar, esperar, expect, foto, fotoTexto, sql } from './ayudante-qa.js';

async function abrirAuditoria(page) {
  await page.goto(VISTA('cuentas/auditoria.html'));
  await expect(page.locator('#auditoria-tbody tr').first()).toBeVisible();
}

test.describe('HU-AUD-004 · Exportación y conservación del registro de auditoría', () => {
  test('CP-AUD-004-01 · Exportar con filtros; si el volumen supera el máximo por operación se exige segmentar', async ({ page }) => {
    const total = sql('cumplimiento', 'SELECT count(*) FROM audit_log;');
    await entrar(page, 'superadmin_qa');
    await abrirAuditoria(page);
    const resp = page.waitForResponse((r) => r.url().includes('/auditoria/exportar'));
    await page.locator('#btn-exportar').click();
    const r1 = await resp;
    await page.waitForTimeout(1200);
    const aviso = page.locator('#auditoria-estado, [role="alert"], .aviso').filter({ hasText: /máximo|acota|registros/i }).first();
    await expect(aviso).toBeVisible();
    await foto(page, 'CP-AUD-004-01', `Exportar SIN filtros (${total} registros > máximo por operación): HTTP ${r1.status()}, se pide acotar el rango`, { resaltar: aviso });
    expect(r1.status()).toBeGreaterThanOrEqual(400);

    await page.locator('#filtro-tipo-accion').selectOption({ label: 'Cambio de rol' });
    const hoy = new Date().toISOString().slice(0, 10);
    await page.locator('#filtro-desde').fill(hoy);
    await page.locator('#filtro-hasta').fill(hoy);
    await page.locator('#btn-filtrar').click();
    await page.waitForTimeout(1200);
    const descarga = page.waitForEvent('download');
    await page.locator('#btn-exportar').click();
    const archivo = await descarga;
    const ruta = `${EVIDENCIAS}/${archivo.suggestedFilename()}`;
    await archivo.saveAs(ruta);
    expect(fs.readFileSync(ruta).subarray(0, 4).toString()).toBe('%PDF');
    await foto(page, 'CP-AUD-004-01', `Segmentado por tipo «Cambio de rol» y fecha ${hoy}: se descarga «${archivo.suggestedFilename()}»`, { resaltar: [page.locator('#filtro-tipo-accion'), page.locator('#btn-exportar')] });
    const pdf = await page.context().newPage();
    await pdf.goto(`file:///${ruta}`).catch(() => {});
    await pdf.close();
    const accesos = sql('cumplimiento', `SELECT to_char(fecha_hora,'HH24:MI:SS')||' | '||tipo_accion||' | admin='||administrador_id||' | '||coalesce(valor_nuevo,'')||' | ip='||ip_origen FROM audit_log WHERE tipo_accion='EXPORTACION' ORDER BY fecha_hora DESC LIMIT 2;`);
    await fotoTexto(page, 'CP-AUD-004-01', 'Cada exportación (rechazada o realizada) queda registrada como un acceso', 'audit_log (ms-cumplimiento)', accesos);
    expect(accesos).toMatch(/RECHAZADA/);
    expect(accesos).toMatch(/Exportados \d+ registros/);
  });

  test('CP-AUD-004-02 · El registro se conserva: no puede depurarse ni borrarse', async ({ page }) => {
    const primero = sql('cumplimiento', `SELECT to_char(min(fecha_hora),'YYYY-MM-DD HH24:MI')||' | total='||count(*) FROM audit_log;`);
    let borrado;
    try {
      sql('cumplimiento', `DELETE FROM audit_log WHERE fecha_hora < now();`);
      borrado = 'se borró';
    } catch (e) {
      borrado = `${e.stderr ?? e.message}`.split('\n').filter((l) => /ERROR/.test(l)).join('\n');
    }
    const despues = sql('cumplimiento', `SELECT to_char(min(fecha_hora),'YYYY-MM-DD HH24:MI')||' | total='||count(*) FROM audit_log;`);
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-AUD-004-02', 'Una depuración masiva del registro se rechaza: nada se pierde', 'psql · DELETE FROM audit_log WHERE fecha_hora < now()', `antes:   ${primero}\n${borrado}\ndespués: ${despues}`);
    expect(despues).toBe(primero);
  });

  test('CP-AUD-004-03 · El derecho al olvido no afecta los registros de auditoría', async ({ page }) => {
    const uid = sql('identidad', `SELECT usuario_uid FROM solicitudes_cierre_cuenta WHERE estado='EJECUTADA' ORDER BY ejecutada_en DESC LIMIT 1;`);
    expect(uid, 'precondición: una cuenta ya cerrada por derecho al olvido (CP-PAG-002-04)').toBeTruthy();
    const cuenta = sql('identidad', `SELECT apodo||' | '||email||' | '||estado FROM usuarios WHERE public_id='${uid}';`);
    const filas = sql('cumplimiento', `SELECT to_char(fecha_hora,'YYYY-MM-DD HH24:MI')||' | '||tipo_accion||' | '||motivo FROM audit_log WHERE afectado='${uid}' OR administrador_id='${uid}' ORDER BY fecha_hora;`);
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-AUD-004-03', 'Cuenta ya anonimizada por derecho al olvido: sus asientos de auditoría siguen completos', `cuenta ${uid}: ${cuenta}`, filas);
    expect(filas).toMatch(/REGISTRO_JUGADOR/);
    expect(filas).toMatch(/DERECHO_AL_OLVIDO/);
  });

  test('CP-AUD-004-04 · Política de conservación mínima de 5 años con archivado', async ({ page }) => {
    const props = fs.readFileSync(`${RAIZ}/services/cuentas/ms-cumplimiento/src/main/resources/application.properties`, 'utf8');
    const codigo = fs.readdirSync(`${RAIZ}/services/cuentas/ms-cumplimiento/src/main/java/com/nexusbattles/ms_cumplimiento/auditoria`, { recursive: true }).filter((f) => /\.java$/.test(f));
    const conRetencion = codigo.filter((f) => /reten|archiv|conserv/i.test(f));
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-AUD-004-04', 'Se busca la política de retención (5 años) y el mecanismo de archivado en ms-cumplimiento', 'application.properties + clases del módulo de auditoría',
      `Propiedades de retención/archivado: ${/reten|archiv/i.test(props) ? 'sí' : 'ninguna'}\nClases (${codigo.length}): ${codigo.join(', ')}\nClases de retención/archivado: ${conRetencion.join(', ') || 'ninguna'}`);
    expect(conRetencion.length, 'debe existir un mecanismo de archivado que preserve 5 años').toBeGreaterThan(0);
  });
});
