// HU-AUD-006 · Auditoría de transacciones económicas (Juan Diego)
import { test } from '@playwright/test';
import { VISTA, entrar, expect, foto, fotoTexto, sql } from './ayudante-qa.js';

test.describe('HU-AUD-006 · Auditoría de transacciones económicas', () => {
  test('CP-AUD-006-01 · Cada transacción económica queda en el registro de auditoría general con partes, montos, comisiones y estado', async ({ page }) => {
    const compras = sql('finanzas', `SELECT ref_id||' | '||uid_usuario||' | '||monto||' '||moneda||' | '||resultado FROM finanzas.transacciones ORDER BY creado DESC LIMIT 3;`);
    const subastas = sql('subastas', `SELECT id||' | vendedor='||apodo_vendedor||' | oferta='||oferta_vigente||' | comision='||coalesce(comision_cobrada::text,'—')||' | '||estado FROM subastas WHERE estado='ADJUDICADA' ORDER BY cerrada_en DESC LIMIT 3;`);
    const enAuditoria = sql('cumplimiento', `SELECT count(*) FROM audit_log WHERE tipo_accion ILIKE ANY (ARRAY['%PAGO%','%COMPRA%','%SUBASTA%','%TRANSAC%']) OR motivo ILIKE ANY (ARRAY['%pago%','%compra%','%subasta%','%transacc%','%puja%']);`);
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-AUD-006-01', 'Transacciones económicas reales del banco frente al registro de auditoría general', 'ms-finanzas · ms-subastas · ms-cumplimiento',
      `Pagos en dinero real (ms-finanzas):\n${compras}\n\nSubastas adjudicadas (ms-subastas):\n${subastas}\n\nAsientos económicos en audit_log (ms-cumplimiento): ${enAuditoria}`);
    await entrar(page, 'superadmin_qa');
    await page.goto(VISTA('cuentas/auditoria.html'));
    await expect(page.locator('#auditoria-tbody tr').first()).toBeVisible();
    const tipos = await page.locator('#filtro-tipo-accion option').allInnerTexts();
    await foto(page, 'CP-AUD-006-01', `Tipos de acción disponibles en la consulta: ${tipos.join(', ')} (ninguno económico)`, { resaltar: page.locator('#filtro-tipo-accion') });
    expect(Number(enAuditoria), 'las transacciones económicas deben quedar en el registro de auditoría').toBeGreaterThan(0);
  });

  test('CP-AUD-006-02 · Una transacción parcialmente completada se registra con su estado de conciliación', async ({ page }) => {
    const indeterminadas = sql('finanzas', `SELECT ref_id||' | '||monto||' '||moneda||' | '||concepto||' | '||resultado FROM finanzas.transacciones WHERE resultado='INDETERMINADO' ORDER BY creado DESC LIMIT 3;`);
    const pendientes = sql('ecommerce', `SELECT id||' | '||estado||' | '||coalesce(ultimo_error,'—') FROM ordenes WHERE estado='PENDIENTE' ORDER BY 1 DESC LIMIT 3;`);
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-AUD-006-02', 'Pagos que no se confirmaron: quedan registrados como INDETERMINADO (finanzas) / PENDIENTE (tienda) para conciliación', 'ms-finanzas.transacciones · ms-ecommerce.ordenes', `${indeterminadas}\n\n${pendientes}`);
    expect(indeterminadas).toMatch(/INDETERMINADO/);
  });

  test('CP-AUD-006-03 · El registro económico es consultable durante la resolución de disputas', async ({ page }) => {
    const id = sql('identidad', `SELECT id FROM usuarios WHERE apodo='comprador2_qa';`);
    await entrar(page, 'admin_e2e');
    await page.goto(VISTA(`cuentas/gestion-usuarios.html?usuario=${id}`));
    await expect(page.locator('#panel-usuario')).toBeVisible();
    const pendiente = page.locator('[data-zona="ficha-pendiente"]');
    await foto(page, 'CP-AUD-006-03', 'Ficha administrativa del comprador de una subasta: «transacciones … la administración de cuentas todavía no los publica»', { resaltar: pendiente });
    await expect(page.locator('#panel-usuario').getByRole('table').filter({ hasText: /transacci|pago|subasta/i }), 'el administrador debe poder consultar las transacciones del usuario para una disputa').toBeVisible({ timeout: 3000 });
  });
});
