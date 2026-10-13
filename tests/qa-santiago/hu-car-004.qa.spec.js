// HU-CAR-004 · Carro de compras (Julián Reyes)
import { test } from '@playwright/test';
import fs from 'node:fs';
import { CORRIDA, SALIDA, VISTA, datosQa, entrar, expect, foto, fotoHttp, llamar, sesion } from './ayudante-qa.js';

const datos = datosQa();
const CLIENTE = `carrito${CORRIDA}`;
const ESTADO = `${SALIDA}/estado-car004-${CORRIDA}.json`;
const [A, B] = [datos.productosTienda[6], datos.productosTienda[8]];

async function abrirTienda(page) {
  await page.goto(VISTA('cuentas/tienda.html'));
  await expect(page.locator('#productos-grid .product-card').first()).toBeVisible();
  await page.waitForTimeout(800);
}
async function ubicar(page, nombre) {
  await page.locator('#busqueda-tienda').fill(nombre);
  await page.waitForTimeout(1000);
  return page.locator('#productos-grid .product-card').filter({ hasText: nombre }).first();
}
const linea = (page, nombre) => page.locator('#cart-items .cart-item').filter({ hasText: nombre }).first();

test.describe('HU-CAR-004 · Carro de compras', () => {
  let limitado;

  test.beforeAll(async ({ request }) => {
    await sesion(request, CLIENTE);
    if (fs.existsSync(ESTADO)) { ({ limitado } = JSON.parse(fs.readFileSync(ESTADO, 'utf8'))); return; }
    const admin = await sesion(request, 'admin_e2e');
    const r = await llamar(request, 'POST', '/api/v1/productos', { token: admin.token, data: { nombre: `Reliquia de edición limitada ${CORRIDA}`, imagen: '/frontend/app-web/src/cuentas/avatares/gran-mago-sabio.jpg', descripcion: 'Solo existen 2 unidades en todo el Nexo.', tipo: 'ARMA', tiraje: 2, premium: false, precioCreditos: 300, precioMonedaReal: 99000, poderDeAtaque: 20, tasaDeCaida: 10 } });
    limitado = { id: r.cuerpo.id, nombre: `Reliquia de edición limitada ${CORRIDA}` };
    fs.writeFileSync(ESTADO, JSON.stringify({ limitado }));
  });

  test('CP-CAR-004-01 · Cada producto tiene «Añadir» al carro y a deseados, con realimentación inmediata', async ({ page }) => {
    await entrar(page, CLIENTE);
    await abrirTienda(page);
    const t = await ubicar(page, A.nombre);
    await foto(page, 'CP-CAR-004-01', `Tarjeta de «${A.nombre}» con los botones «Añadir» (carro) y ♡ (lista de deseos); carrito en 0`, { resaltar: t });
    await t.getByRole('button', { name: `Añadir ${A.nombre} al carrito` }).click();
    await expect(page.locator('#insignia-carrito')).toHaveAttribute('data-unidades', '1');
    await expect(linea(page, A.nombre)).toBeVisible();
    await foto(page, 'CP-CAR-004-01', 'Al pulsar «Añadir» el contador del carrito pasa a 1 y la línea aparece al instante', { resaltar: [page.locator('#insignia-carrito'), linea(page, A.nombre)] });
    const corazon = t.getByRole('button', { name: `Lista de deseos: ${A.nombre}` });
    await corazon.click();
    await expect(corazon).toHaveAttribute('aria-pressed', 'true');
    await foto(page, 'CP-CAR-004-01', 'Al pulsar ♡ el producto queda marcado en la lista de deseos', { resaltar: corazon });
  });

  test('CP-CAR-004-02 · Agregar, modificar cantidades y eliminar recalcula subtotales y total; se validan existencias', async ({ page, request }) => {
    await entrar(page, CLIENTE);
    await abrirTienda(page);
    const t = await ubicar(page, B.nombre);
    await t.getByRole('button', { name: `Añadir ${B.nombre} al carrito` }).click();
    await expect(linea(page, B.nombre)).toBeVisible();
    await linea(page, A.nombre).locator(`[data-cantidad-nueva="2"]`).click();
    await page.waitForTimeout(1200);
    const total = Number((await page.locator('#cart-total').innerText()).replace(/\D/g, ''));
    const esperado = 2 * A.precio + B.precio;
    await foto(page, 'CP-CAR-004-02', `2 × ${A.nombre} (${A.precio}) + 1 × ${B.nombre} (${B.precio}) = ${esperado} COP: el total se recalcula`, { resaltar: page.locator('#panel-carrito') });
    expect(total).toBe(esperado);
    await linea(page, B.nombre).locator('[data-quitar-item]').click();
    await expect(linea(page, B.nombre)).toHaveCount(0);
    await page.waitForTimeout(800);
    expect(Number((await page.locator('#cart-total').innerText()).replace(/\D/g, ''))).toBe(2 * A.precio);
    await foto(page, 'CP-CAR-004-02', `Se quita «${B.nombre}»: total = ${2 * A.precio} COP`, { resaltar: page.locator('#panel-carrito') });
    const s = await sesion(request, CLIENTE);
    const exceso = await llamar(request, 'POST', '/api/v1/carrito/items', { token: s.token, data: { productoId: limitado.id, cantidad: 3 } });
    await fotoHttp(page, 'CP-CAR-004-02', `Producto con tiraje de 2 unidades: pedir 3 se rechaza por existencias`, exceso);
    expect(exceso.estado).toBeGreaterThanOrEqual(400);
  });

  test('CP-CAR-004-03 · El carro está disponible en toda la vista, desplegado (detalle) o minimizado (ícono + contador)', async ({ page }) => {
    await entrar(page, CLIENTE);
    await abrirTienda(page);
    const panel = page.locator('#panel-carrito');
    const estado = page.locator('main[data-carrito]');
    await expect(estado).toHaveAttribute('data-carrito', 'desplegado');
    await foto(page, 'CP-CAR-004-03', 'Vista desplegada: líneas, subtotal, total y botón «Pagar»', { resaltar: panel });
    await page.locator('#minimizar-carrito').click();
    await page.waitForTimeout(600);
    await expect(estado).not.toHaveAttribute('data-carrito', 'desplegado');
    await foto(page, 'CP-CAR-004-03', 'Vista minimizada: solo el ícono del carrito con su contador', { resaltar: page.locator('#insignia-carrito').locator('..') });
    await page.mouse.wheel(0, 2500);
    await page.waitForTimeout(600);
    await foto(page, 'CP-CAR-004-03', 'Al desplazarse por la vitrina el carrito sigue accesible', { resaltar: page.locator('#insignia-carrito').locator('..') });
  });

  test('CP-CAR-004-04 · El carro persiste entre sesiones', async ({ page, browser }) => {
    const otra = await browser.newContext();
    const p2 = await otra.newPage();
    await entrar(p2, CLIENTE);
    await abrirTienda(p2);
    await expect(linea(p2, A.nombre)).toBeVisible();
    await expect(p2.locator('#insignia-carrito')).toHaveAttribute('data-unidades', '2');
    await foto(p2, 'CP-CAR-004-04', 'En otro navegador/sesión, el carrito del jugador conserva sus 2 unidades', { resaltar: p2.locator('#panel-carrito') });
    await otra.close();
  });
});
