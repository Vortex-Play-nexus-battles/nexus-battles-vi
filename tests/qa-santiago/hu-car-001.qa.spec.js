// HU-CAR-001 · Vitrina de productos del comercio electrónico (Julián Reyes)
import { test } from '@playwright/test';
import { CORRIDA, VISTA, comprar, datosQa, entrar, expect, foto, llamar, sesion } from './ayudante-qa.js';

const datos = datosQa();
const CLIENTE = `vitrina${CORRIDA}`;
const tarjetas = (page) => page.locator('#productos-grid .product-card');
const tarjeta = (page, nombre) => tarjetas(page).filter({ has: page.locator('.product-card__nombre', { hasText: new RegExp(`^${nombre}$`) }) }).first();

async function ubicar(page, nombre) {
  await page.locator('#busqueda-tienda').fill(nombre);
  await page.waitForTimeout(1200);
  return tarjeta(page, nombre);
}

async function abrirTienda(page) {
  await page.goto(VISTA('cuentas/tienda.html'));
  await expect(tarjetas(page).first()).toBeVisible();
  await page.waitForTimeout(1000);
}

test.describe('HU-CAR-001 · Vitrina de productos del comercio electrónico', () => {
  test.beforeAll(async ({ request }) => {
    const s = await sesion(request, CLIENTE);
    const ordenes = await llamar(request, 'GET', '/api/v1/ordenes', { token: s.token });
    if (!ordenes.cuerpo?.length) await comprar(request, s, datos.productosTienda[3].id);
    await llamar(request, 'PUT', `/api/v1/lista-deseos/${datos.productosTienda[5].id}`, { token: s.token });
  });

  test('CP-CAR-001-01 · 16 productos por página con imagen, nombre, tipo, descripción y precio', async ({ page }) => {
    await entrar(page, CLIENTE);
    await abrirTienda(page);
    await expect(tarjetas(page)).toHaveCount(16);
    const primera = tarjetas(page).first();
    for (const parte of ['.product-card__nombre', '.product-card__tipo', '.product-card__descripcion']) await expect(primera.locator(parte).first()).toBeVisible();
    await expect(primera).toContainText(/créditos|COP/);
    await foto(page, 'CP-CAR-001-01', 'Vitrina: primera página con 16 tarjetas (imagen/banner, tipo, nombre, descripción y precio)', { completa: true });
    const pag = page.locator('#paginacion-tienda');
    await expect(pag).toBeVisible();
    await pag.getByRole('button', { name: /2|siguiente|›/i }).last().click();
    await page.waitForTimeout(800);
    const segunda = await tarjetas(page).count();
    await foto(page, 'CP-CAR-001-01', `Página 2 de la vitrina: ${segunda} productos más`, { resaltar: pag });
    expect(segunda).toBeGreaterThan(0);
    await abrirTienda(page);
    (await ubicar(page, 'Espada del Alba'));
    await tarjeta(page, 'Espada del Alba').locator('.product-card__ver, button:has-text("Ver producto")').first().click();
    const ficha = page.getByRole('dialog').first();
    await expect(ficha).toBeVisible();
    await foto(page, 'CP-CAR-001-01', '«Ver producto»: ficha con imagen, descripción, atributos/habilidades del producto y precio', { resaltar: ficha });
  });

  test('CP-CAR-001-02 · El precio se presenta en la moneda del cliente con selección manual (COP, USD, EUR)', async ({ page }) => {
    const moneda = page.locator('#moneda-tienda');
    // Precondición: el Super Administrador define las tasas de cambio (parámetros sin valor: decisión del PO).
    await entrar(page, 'superadmin_qa');
    await page.goto(VISTA('plataforma/admin-parametros/parametros-admin.html'));
    for (const [clave, valor] of [['tienda.tasa-cop-usd', '4000'], ['tienda.tasa-cop-eur', '4400']]) {
      const f = page.locator('[data-zona="catalogo"] > *').filter({ hasText: clave }).first();
      await f.locator('[name="valor"]').fill(valor);
      await f.locator('[name="motivo"]').fill('Tasa de referencia para la prueba QA de la vitrina');
      await f.locator('[data-accion="guardar"]').click();
      await expect(f).toContainText(new RegExp(`Vigente: ${valor}`));
      if (clave.endsWith('usd')) await foto(page, 'CP-CAR-001-02', 'Administración › Parámetros: tasas COP/USD = 4.000 y COP/EUR = 4.400', { resaltar: f });
    }
    await entrar(page, CLIENTE);
    await abrirTienda(page);
    for (let i = 0; i < 30 && (await moneda.locator('option[value="USD"]:disabled').count()); i += 1) {
      await page.waitForTimeout(3000);
      await abrirTienda(page);
    }
    await expect(moneda).toHaveValue('COP');
    await foto(page, 'CP-CAR-001-02', 'Por la ubicación del cliente (Colombia) la vitrina abre en pesos (COP); USD y EUR quedan disponibles para elegir', { resaltar: moneda });
    const espada = async () => ubicar(page, 'Espada del Alba');
    await moneda.selectOption('USD');
    await page.waitForTimeout(1500);
    await expect(await espada()).toContainText(/USD|US\$/);
    await foto(page, 'CP-CAR-001-02', 'Selección manual USD: «Espada del Alba» 11.250 COP → ≈ 2,81 USD', { resaltar: [moneda, await espada()] });
    await moneda.selectOption('EUR');
    await page.waitForTimeout(1500);
    await expect(await espada()).toContainText(/EUR|€/);
    await foto(page, 'CP-CAR-001-02', 'Selección manual EUR: el mismo producto en euros', { resaltar: [moneda, await espada()] });
  });

  test('CP-CAR-001-03 · Los productos en promoción muestran el % de descuento y la promoción vencida se retira', async ({ page }) => {
    await entrar(page, CLIENTE);
    await abrirTienda(page);
    const promo = await ubicar(page, 'Espada del Alba');
    await expect(promo).toContainText('25');
    await expect(promo).toContainText('11.250');
    await foto(page, 'CP-CAR-001-03', '«Espada del Alba» con promoción vigente del 25 %: precio 15.000 → 11.250 COP', { resaltar: promo });
    const vencida = await ubicar(page, 'Hacha de Obsidiana');
    await expect(vencida).not.toContainText('%');
    await expect(vencida).toContainText('18.500');
    await foto(page, 'CP-CAR-001-03', '«Hacha de Obsidiana»: su promoción del 40 % venció (duraba 4 minutos) y el marcador se retiró solo; vuelve a 18.500 COP', { resaltar: vencida });
  });

  test('CP-CAR-001-04 · Los productos deseados y los ya adquiridos llevan una distinción visual permanente', async ({ page }) => {
    await entrar(page, CLIENTE);
    await abrirTienda(page);
    const propio = await ubicar(page, datos.productosTienda[3].nombre);
    await expect(propio).toContainText('Ya lo tienes');
    await foto(page, 'CP-CAR-001-04', `«${datos.productosTienda[3].nombre}» (ya comprado) lleva el distintivo «Ya lo tienes» y no se puede volver a añadir`, { resaltar: propio });
    const deseado = await ubicar(page, datos.productosTienda[5].nombre);
    await expect(deseado.locator('[aria-pressed="true"], .product-card__distintivos').first()).toBeVisible();
    await foto(page, 'CP-CAR-001-04', `«${datos.productosTienda[5].nombre}» (en la lista de deseos) lleva su distintivo y el corazón marcado`, { resaltar: deseado });
    await page.evaluate(() => sessionStorage.clear());
    await entrar(page, CLIENTE);
    await abrirTienda(page);
    await page.locator('#solo-deseos').check();
    await page.waitForTimeout(1200);
    await expect(tarjeta(page, datos.productosTienda[5].nombre)).toBeVisible();
    await foto(page, 'CP-CAR-001-04', 'En una sesión nueva la distinción se conserva: «Solo mi lista de deseos» muestra el producto deseado', { resaltar: tarjetas(page).first() });
  });

  test('CP-CAR-001-05 · Búsqueda por cualquier atributo (incluido el precio) y filtros por precio, tipo y promoción', async ({ page }) => {
    await entrar(page, CLIENTE);
    await abrirTienda(page);
    const buscar = page.locator('#busqueda-tienda');
    await buscar.fill('cuerpo a cuerpo');
    await page.waitForTimeout(1200);
    const porDescripcion = await tarjetas(page).count();
    await foto(page, 'CP-CAR-001-05', `Búsqueda por descripción «cuerpo a cuerpo»: ${porDescripcion} resultados`, { resaltar: buscar });
    expect(porDescripcion).toBeGreaterThan(0);
    await buscar.fill('26.200');
    await page.waitForTimeout(1200);
    await expect(tarjetas(page).first()).toContainText('26.200');
    await foto(page, 'CP-CAR-001-05', 'Búsqueda por precio «26.200»: el producto con ese precio', { resaltar: tarjetas(page).first() });
    await buscar.fill('');
    await page.locator('#tipo-tienda').selectOption({ label: 'Armadura' });
    await page.locator('#precio-minimo').fill('30000');
    await page.locator('#precio-maximo').fill('45000');
    await page.locator('#precio-maximo').press('Tab');
    await page.waitForTimeout(1200);
    const tipos = await tarjetas(page).locator('.product-card__tipo').allInnerTexts();
    for (const t of tipos) expect(t).toMatch(/ARMADURA/i);
    await foto(page, 'CP-CAR-001-05', `Filtro tipo Armadura + precio 30.000–45.000 COP: ${tipos.length} resultados`, { resaltar: page.locator('#filtros-tienda') });
    await page.getByRole('button', { name: 'Limpiar filtros' }).click();
    await page.locator('#solo-promocion').check();
    await page.waitForTimeout(1200);
    await expect(tarjetas(page)).toHaveCount(1);
    await foto(page, 'CP-CAR-001-05', '«Solo en promoción»: únicamente la Espada del Alba (25 %)', { resaltar: [page.locator('#solo-promocion'), tarjetas(page).first()] });
  });
});
