// HU-PAG-004 · Facturación electrónica y cumplimiento fiscal (Edwin Pedraza)
import { test } from '@playwright/test';
import { CORRIDA, VISTA, comprar, contenedor, datosQa, entrar, esperarSano, expect, foto, fotoHttp, llamar, sesion } from './ayudante-qa.js';

const datos = datosQa();
const COMPRADOR = `factura${CORRIDA}`;

test.describe('HU-PAG-004 · Facturación electrónica y cumplimiento fiscal', () => {
  let orden;

  test.beforeAll(async ({ request }) => {
    const s = await sesion(request, COMPRADOR);
    const ordenes = await llamar(request, 'GET', '/api/v1/ordenes', { token: s.token });
    orden = ordenes.cuerpo?.[0] ?? (await comprar(request, s, datos.productosTienda[10].id)).cuerpo;
  });

  test('CP-PAG-004-01 · Toda compra aprobada de un producto del Maestro de Juego genera su factura electrónica (simulada)', async ({ page, request }) => {
    const s = await sesion(request, COMPRADOR);
    await entrar(page, COMPRADOR);
    await page.goto(VISTA('cuentas/tienda.html'));
    await page.locator('#btn-mis-compras').click();
    const compras = page.getByRole('dialog', { name: 'Mis compras' });
    await expect(compras.locator('.compra').first()).toBeVisible();
    await foto(page, 'CP-PAG-004-01', `«Mis compras»: orden ${orden.id.slice(0, 8)}… COMPLETA. Se busca el documento de factura`, { resaltar: compras });
    const detalle = await llamar(request, 'GET', `/api/v1/ordenes/${orden.id}`, { token: s.token });
    await fotoHttp(page, 'CP-PAG-004-01', 'Detalle de la orden aprobada: no incluye número ni enlace de factura', detalle);
    await expect(compras.getByRole('link', { name: /factura/i }).or(compras.getByRole('button', { name: /factura/i })), 'debe existir la factura de la compra aprobada').toBeVisible({ timeout: 3000 });
  });

  test('CP-PAG-004-02 · La factura incorpora los tributos de la jurisdicción', async ({ page, request }) => {
    const s = await sesion(request, COMPRADOR);
    const detalle = await llamar(request, 'GET', `/api/v1/ordenes/${orden.id}`, { token: s.token });
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-PAG-004-02', 'Se buscan los tributos (IVA u otros) en la orden y su documento fiscal', detalle);
    expect(JSON.stringify(detalle.cuerpo), 'la orden/factura debe desglosar los tributos aplicables').toMatch(/iva|impuesto|tributo/i);
  });

  test('CP-PAG-004-03 · Si la facturación no está disponible, la emisión se difiere sin bloquear la compra', async ({ page }) => {
    await entrar(page, COMPRADOR);
    await page.goto(VISTA('cuentas/historial-transacciones.html'));
    const tabla = page.locator('#historial-tbody');
    await expect(tabla.locator('tr').first()).toBeVisible();
    await foto(page, 'CP-PAG-004-03', 'Historial de transacciones: la columna «Comprobante» no ofrece factura (ni emitida ni pendiente de emisión)', { resaltar: tabla });
    await expect(tabla.getByText(/factura|pendiente de emisi/i), 'debe mostrarse la factura emitida o en emisión diferida').toBeVisible({ timeout: 3000 });
  });
});
