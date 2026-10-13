// HU-SUB-011 · Listado de subastas activas (Cristian Chaparro)
import { test } from '@playwright/test';

import { datosQa, expect, foto, fotoTexto, llamar, entrar, sesion, sql, esperar } from './ayudante-qa.js';

const datos = datosQa();
const tarjetas = (page) => page.locator('#subastas-resultados [data-subasta-id]');

async function abrirListado(page) {
  await page.goto('/subastas');
  await expect(tarjetas(page).first()).toBeVisible();
  await page.waitForLoadState('networkidle').catch(() => {});
}

test.describe('HU-SUB-011 · Listado de subastas activas', () => {
  test('CP-SUB-011-01 · 16 subastas por página con miniatura, nombre, tipo, precios, contador, pujas e indicador del Maestro de Juego', async ({ page }) => {
    await abrirListado(page);
    await expect(tarjetas(page)).toHaveCount(16);
    const primera = tarjetas(page).first();
    for (const parte of ['.subastas__miniatura', '.subastas__nombre', '.subastas__precio', '.subastas__contador', '.subastas__pujas']) {
      await expect(primera.locator(parte)).toBeVisible();
    }
    await foto(page, 'CP-SUB-011-01', 'Vista «Subastas activas» abierta por un visitante sin sesión: 16 tarjetas en la primera página', { completa: true });
    const gm = page.locator(`[data-subasta-id="${datos.maestroDeJuego}"]`);
    await expect(gm).toContainText('Maestro de Juego');
    await foto(page, 'CP-SUB-011-01', 'Tarjeta: miniatura, nombre, tipo, precio mínimo, contador, número de pujas e insignia «Maestro de Juego»', { resaltar: gm });
    const conCompra = tarjetas(page).filter({ hasText: 'Comprar ahora' }).first();
    await foto(page, 'CP-SUB-011-01', 'Tarjeta con precio de compra inmediata («Comprar ahora · N»)', { resaltar: conCompra });
    await expect(page.getByText(/Página 1 de 2/)).toBeVisible();
    await page.getByRole('button', { name: /página 2|siguiente|›/i }).last().click();
    await expect(page.getByText(/Página 2 de 2/)).toBeVisible();
    await expect(tarjetas(page).first()).toBeVisible();
    const resto = await tarjetas(page).count();
    expect(resto).toBeGreaterThan(0);
    expect(resto).toBeLessThanOrEqual(16);
    await foto(page, 'CP-SUB-011-01', `Página 2: las ${resto} subastas restantes (total ${16 + resto})`, { resaltar: page.getByText(/Página 2 de 2/) });
  });

  test('CP-SUB-011-02 · El contador de tiempo y el número de pujas se actualizan en vivo', async ({ page, request }) => {
    await entrar(page, 'comprador_qa');
    await abrirListado(page);
    const ids = await tarjetas(page).evaluateAll((ns) => ns.map((n) => n.getAttribute('data-subasta-id')));
    const sub = datos.subastas.find((x) => ids.includes(x.id) && x.id !== datos.maestroDeJuego);
    const id = sub.id;
    // Precondición: esta subasta termina en 9 minutos, para ver correr el contador.
    sql('subastas', `UPDATE subastas SET fecha_fin = now() + interval '9 minutes' WHERE id='${id}';`);
    await abrirListado(page);
    const tarjeta = page.locator(`[data-subasta-id="${id}"]`);
    const contador = tarjeta.locator('.subastas__contador');
    const antes = (await contador.innerText()).trim();
    await foto(page, 'CP-SUB-011-02', `Contador de «${sub.nombre}» (termina en ~9 min): ${antes}`, { resaltar: contador });
    await esperar(3500);
    const despues = (await contador.innerText()).trim();
    expect(despues).not.toBe(antes);
    await foto(page, 'CP-SUB-011-02', `3,5 s después, sin recargar: ${despues}`, { resaltar: contador });

    const pujas = tarjeta.locator('.subastas__pujas');
    const pujasAntes = (await pujas.innerText()).trim();
    const otro = await sesion(request, 'comprador2_qa');
    const ficha = await llamar(request, 'GET', `/api/v1/subastas/${sub.id}`);
    const monto = Number(ficha.cuerpo.pujaMinimaSiguiente);
    const r = await llamar(request, 'POST', `/api/v1/subastas/${sub.id}/pujas`, { token: otro.token, data: { monto }, headers: { 'Idempotency-Key': `qa-vivo-${Date.now()}` } });
    expect(r.estado, r.texto).toBeLessThan(300);
    await expect(pujas).not.toHaveText(pujasAntes, { timeout: 15_000 });
    await foto(page, 'CP-SUB-011-02', `Otro jugador puja ${monto} créditos: la tarjeta pasa de «${pujasAntes}» a «${(await pujas.innerText()).trim()}» sin recargar`, { resaltar: tarjeta });
  });

  test('CP-SUB-011-03 · Filtros por tipo, precio, tipo de venta y vendedor', async ({ page }) => {
    await abrirListado(page);
    await page.getByLabel('Armadura', { exact: true }).check();
    await expect(tarjetas(page).first()).toBeVisible();
    await page.waitForLoadState('networkidle');
    const tipos = await tarjetas(page).allInnerTexts();
    expect(tipos.length).toBeGreaterThan(0);
    for (const t of tipos) expect(t).toMatch(/ARMADURA/i);
    await foto(page, 'CP-SUB-011-03', `Filtro «Tipo: Armadura»: ${tipos.length} resultados, todos de tipo ARMADURA`, { resaltar: page.locator('#mercado-filtros-formulario') });
    await page.getByLabel('Armadura', { exact: true }).uncheck();

    await page.getByLabel('Compra inmediata disponible').check();
    await page.waitForLoadState('networkidle');
    await esperar(800);
    const conCompra = await tarjetas(page).allInnerTexts();
    for (const t of conCompra) expect(t).toContain('Comprar ahora');
    await foto(page, 'CP-SUB-011-03', `Filtro «Tipo de venta: Compra inmediata disponible»: ${conCompra.length} resultados, todos con «Comprar ahora»`, { resaltar: tarjetas(page).first() });
    await page.getByRole('button', { name: 'Limpiar filtros' }).click();

    await page.getByLabel('Mínimo').fill('100');
    await page.getByLabel('Máximo').fill('180');
    await page.getByLabel('Máximo').press('Enter');
    await page.waitForLoadState('networkidle');
    await esperar(1200);
    const precios = (await tarjetas(page).locator('.subastas__precio').allInnerTexts()).map((p) => Number(p.replace(/\D/g, '')));
    expect(precios.length).toBeGreaterThan(0);
    for (const p of precios) expect(p).toBeGreaterThanOrEqual(100), expect(p).toBeLessThanOrEqual(180);
    await foto(page, 'CP-SUB-011-03', `Filtro de precio 100–180 créditos: precios mostrados ${precios.join(', ')}`, { resaltar: page.getByLabel('Mínimo') });
    await page.getByRole('button', { name: 'Limpiar filtros' }).click();

    await page.getByLabel('Maestro de Juego', { exact: true }).check();
    await page.waitForLoadState('networkidle');
    await esperar(800);
    await expect(tarjetas(page)).toHaveCount(1);
    await foto(page, 'CP-SUB-011-03', 'Filtro «Vendedor: Maestro de Juego»: solo la publicación oficial', { resaltar: tarjetas(page).first() });
  });

  test('CP-SUB-011-04 · Ordenamiento por precio y por número de pujas', async ({ page }) => {
    await abrirListado(page);
    await page.locator('#subastas-ordenar').selectOption('PRECIO_ASC');
    await page.waitForLoadState('networkidle');
    await esperar(1000);
    const asc = (await tarjetas(page).locator('.subastas__precio').allInnerTexts()).map((p) => Number(p.replace(/\D/g, '')));
    expect(asc).toEqual([...asc].sort((a, b) => a - b));
    await foto(page, 'CP-SUB-011-04', `Orden «Precio: menor a mayor»: ${asc.slice(0, 8).join(' ≤ ')} …`, { resaltar: page.locator('#subastas-ordenar') });
    await page.locator('#subastas-ordenar').selectOption('PUJAS');
    await page.waitForLoadState('networkidle');
    await esperar(1000);
    const pujas = (await tarjetas(page).locator('.subastas__pujas').allInnerTexts()).map((p) => Number(p.replace(/\D/g, '')));
    expect(pujas).toEqual([...pujas].sort((a, b) => b - a));
    await foto(page, 'CP-SUB-011-04', `Orden «Más pujas»: ${pujas.slice(0, 8).join(' ≥ ')} …`, { resaltar: page.locator('#subastas-ordenar') });
  });

  test('CP-SUB-011-05 · Búsqueda con autocompletado sobre nombre, descripción, tipo y habilidades', async ({ page }) => {
    await abrirListado(page);
    const campo = page.locator('#subastas-buscar');
    await campo.pressSequentially('Espa', { delay: 120 });
    const sugerencias = page.locator('#subastas-sugerencias .subastas-busqueda__sugerencia');
    await expect(sugerencias.first()).toBeVisible();
    await foto(page, 'CP-SUB-011-05', 'Se escribe «Espa»: aparece la lista de autocompletado', { resaltar: page.locator('#subastas-sugerencias') });
    await sugerencias.first().click();
    await page.waitForLoadState('networkidle');
    await esperar(800);
    const resultado = await tarjetas(page).allInnerTexts();
    expect(resultado.length).toBeGreaterThan(0);
    for (const t of resultado) expect(t).toMatch(/Espada/);
    await foto(page, 'CP-SUB-011-05', `Se elige la sugerencia: ${resultado.length} resultado(s) que coinciden`, { resaltar: tarjetas(page).first() });
    await campo.fill('');
    await campo.pressSequentially('gélido', { delay: 80 });
    await campo.press('Enter');
    await page.waitForLoadState('networkidle');
    await esperar(1200);
    const porHabilidad = await tarjetas(page).count();
    await foto(page, 'CP-SUB-011-05', `Búsqueda por habilidad/descripción «gélido»: ${porHabilidad} resultado(s)`, { resaltar: campo });
    expect(porHabilidad).toBeGreaterThan(0);
  });

  test('CP-SUB-011-06 · Sin conexión en tiempo real, el listado se degrada a actualización periódica', async ({ page, request }) => {
    await page.routeWebSocket(/ws-subastas/, (ws) => ws.close());
    await abrirListado(page);
    const id = await tarjetas(page).nth(5).getAttribute('data-subasta-id');
    const sub = datos.subastas.find((x) => x.id === id);
    const tarjeta = page.locator(`[data-subasta-id="${sub.id}"]`);
    const pujas = tarjeta.locator('.subastas__pujas');
    const antes = (await pujas.innerText()).trim();
    await foto(page, 'CP-SUB-011-06', `Canal STOMP bloqueado (se simula la caída del tiempo real). «${sub.nombre}»: ${antes}`, { resaltar: tarjeta });
    const otro = await sesion(request, 'comprador2_qa');
    const ficha = await llamar(request, 'GET', `/api/v1/subastas/${sub.id}`);
    const monto = Number(ficha.cuerpo.pujaMinimaSiguiente);
    const r = await llamar(request, 'POST', `/api/v1/subastas/${sub.id}/pujas`, { token: otro.token, data: { monto }, headers: { 'Idempotency-Key': `qa-degr-${Date.now()}` } });
    expect(r.estado, r.texto).toBeLessThan(300);
    let cambio = false;
    for (let i = 0; i < 12 && !cambio; i += 1) {
      await esperar(5000);
      cambio = (await pujas.innerText()).trim() !== antes;
    }
    await foto(page, 'CP-SUB-011-06', `60 s después de una puja nueva, sin canal en vivo: la tarjeta muestra «${(await pujas.innerText()).trim()}»`, { resaltar: tarjeta });
    expect(cambio, 'la tarjeta debió actualizarse por sondeo periódico al no haber canal en vivo').toBe(true);
  });
});
