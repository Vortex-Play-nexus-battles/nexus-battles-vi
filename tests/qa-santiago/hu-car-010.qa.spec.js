// HU-CAR-010 · Resumen de compra y pago (Julián Reyes)
import { test } from '@playwright/test';
import { correosPara, leerCorreo } from '../e2e/ayudantes/correo.js';
import { CORRIDA, TARJETAS, VISTA, datosQa, entrar, esperar, expect, foto, fotoTexto, llamar, sesion, sql } from './ayudante-qa.js';

const datos = datosQa();
const CLIENTE = `resumen${CORRIDA}`;
const [P1, P2] = [datos.productosTienda[18], datos.productosTienda[19]];

async function abrirPago(page) {
  await page.goto(VISTA('cuentas/tienda.html'));
  const pagar = page.locator('#btn-pagar');
  await expect(pagar).toBeEnabled({ timeout: 20_000 });
  await pagar.click();
  const dialogo = page.getByRole('dialog', { name: 'Pagar tu compra' });
  await expect(dialogo).toBeVisible();
  return dialogo;
}

async function llenar(d, t) {
  await d.getByLabel('Nombre del titular de la tarjeta').fill(t.titular);
  await d.getByLabel('Número de tarjeta').fill(t.numeroTarjeta);
  await d.getByLabel('Fecha de vencimiento (MM/AA)').fill(t.vencimiento);
  await d.getByLabel('Código de seguridad').fill(t.codigoSeguridad);
}

test.describe('HU-CAR-010 · Resumen de compra y pago', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeAll(async ({ request }) => {
    const s = await sesion(request, CLIENTE);
    const carrito = await llamar(request, 'GET', '/api/v1/carrito', { token: s.token });
    if (!carrito.cuerpo.items?.length) {
      for (const p of [P1, P2]) await llamar(request, 'POST', '/api/v1/carrito/items', { token: s.token, data: { productoId: p.id, cantidad: 1 } });
    }
  });

  test('CP-CAR-010-01 · Antes de pagar se muestra el resumen con el detalle de productos y el total', async ({ page }) => {
    await entrar(page, CLIENTE);
    const d = await abrirPago(page);
    await expect(d).toContainText(P1.nombre);
    await expect(d).toContainText(P2.nombre);
    const total = (P1.precio + P2.precio).toLocaleString('es-CO');
    await expect(d).toContainText(total);
    await foto(page, 'CP-CAR-010-01', `Resumen: ${P1.nombre} × 1 + ${P2.nombre} × 1 = ${total} COP antes de pedir los datos de pago`, { resaltar: d });
  });

  test('CP-CAR-010-02 · El formulario pide titular, número, vencimiento y código, y valida su formato', async ({ page }) => {
    await entrar(page, CLIENTE);
    const d = await abrirPago(page);
    for (const l of ['Nombre del titular de la tarjeta', 'Número de tarjeta', 'Fecha de vencimiento (MM/AA)', 'Código de seguridad']) await expect(d.getByLabel(l)).toBeVisible();
    await llenar(d, { titular: 'Laura Gómez QA', numeroTarjeta: '1234', vencimiento: '13/20', codigoSeguridad: '1' });
    const peticiones = [];
    page.on('request', (r) => r.url().includes('/checkout') && peticiones.push(r));
    await d.locator('[data-accion="confirmar-pago"]').click();
    await page.waitForTimeout(1200);
    await foto(page, 'CP-CAR-010-02', 'Datos inválidos (número de 4 dígitos, mes 13, CVV de 1 dígito): la vista marca cada campo y no envía el pago', { resaltar: d });
    expect(peticiones.length).toBe(0);
    await expect(d.locator('[aria-invalid="true"]').first()).toBeVisible();
  });

  test('CP-CAR-010-03 · Se envía a la pasarela simulada y se presenta el resultado (aprobado)', async ({ page }) => {
    await entrar(page, CLIENTE);
    const d = await abrirPago(page);
    await llenar(d, TARJETAS.aprobada);
    await foto(page, 'CP-CAR-010-03', 'Datos válidos de la tarjeta de prueba 4242 4242 4242 4242', { resaltar: d });
    const cobro = page.waitForResponse((r) => r.url().includes('/api/v1/checkout') && r.request().method() === 'POST');
    await d.locator('[data-accion="confirmar-pago"]').click();
    const r = await cobro;
    expect(r.status()).toBe(201);
    await expect(d.locator('.pago__resultado')).toContainText('Compra completada');
    await foto(page, 'CP-CAR-010-03', 'Resultado presentado por la vista: «Compra completada»', { resaltar: d.locator('.pago__resultado') });
    await d.locator('[data-accion="ver-mis-compras"]').click();
    const compras = page.getByRole('dialog', { name: 'Mis compras' });
    await expect(compras.locator('.compra').first()).toContainText('Completada');
    await foto(page, 'CP-CAR-010-03', '«Mis compras»: la orden aparece completada y el carrito quedó vacío', { resaltar: compras });
  });

  test('CP-CAR-010-04 · Al aprobarse se acreditan los productos al inventario, llega el correo y no se almacenan datos de tarjeta', async ({ page, request }) => {
    const s = await sesion(request, CLIENTE);
    const inv = await llamar(request, 'GET', '/api/v1/inventario/elementos?pagina=0', { token: s.token });
    const texto = JSON.stringify(inv.cuerpo);
    expect(texto).toContain(P1.id);
    expect(texto).toContain(P2.id);
    await entrar(page, CLIENTE);
    await page.goto('/inventario');
    await page.waitForTimeout(2500);
    await foto(page, 'CP-CAR-010-04', `Mi inventario: «${P1.nombre}» y «${P2.nombre}» ya están acreditados`, { completa: true });
    let correo = null;
    for (let i = 0; i < 20 && !correo; i += 1) {
      for (const m of await correosPara(`${CLIENTE}@nexus.test`)) {
        const c = await leerCorreo(m.ID);
        if (/compra/i.test(c.Subject ?? '')) correo = c;
      }
      if (!correo) await esperar(1500);
    }
    await fotoTexto(page, 'CP-CAR-010-04', 'Correo de confirmación de la compra en el buzón del jugador', `Asunto: ${correo?.Subject ?? '(no llegó)'}`, (correo?.Text ?? '').slice(0, 1200));
    expect(correo).not.toBeNull();
    const orden = sql('ecommerce', `SELECT row_to_json(o)::text FROM ordenes o WHERE usuario_id='${s.claims.uid}' ORDER BY reservada_en DESC NULLS LAST LIMIT 1;`);
    const columnas = sql('ecommerce', `SELECT string_agg(column_name, ', ') FROM information_schema.columns WHERE table_name='ordenes';`);
    const almacen = await page.evaluate(() => JSON.stringify({ ...sessionStorage, ...localStorage }));
    await fotoTexto(page, 'CP-CAR-010-04', 'Lo que se guardó del pago: solo marca y referencia de la pasarela; ni el número, ni el titular, ni el CVV (base de datos ni navegador)', 'ms-ecommerce.ordenes + almacenamiento del navegador',
      `Columnas de ordenes: ${columnas}\n\nOrden guardada:\n${orden}\n\n¿Aparece "4242 4242" en el navegador? ${/4242\s?4242/.test(almacen) ? 'SÍ' : 'NO'}`);
    expect(orden).not.toMatch(/4242 ?4242|Laura Gómez QA|"123"/);
    expect(almacen).not.toMatch(/4242 ?4242/);
  });
});
