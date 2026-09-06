/**
 * HU-INV-007 - Aceptacion de la ficha de detalle, en navegador real.
 * Criterio 1: al abrir el detalle se muestran nombre, imagen, descripcion y
 * los atributos del producto.
 *
 * El catalogo se responde por intercepcion de red, contra el contrato
 * `GET /api/v1/productos/{id}`.
 */
import { test, expect } from '@playwright/test';

const PAGINA = '/contenido/inventario/inventario.html?jugador=jugador-de-prueba';

const PRODUCTO = {
  id: 'producto-0',
  nombre: 'Hacha de Vorn',
  imagen: 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7',
  descripcion: 'Forjada en la niebla de los pantanos del norte.',
  tipo: 'ARMA',
  tiraje: -1,
  premium: false,
  estado: 'ACTIVO',
  version: 1,
  poderDeAtaque: 42,
  tasaDeCaida: 0.15,
};

async function conUnProducto(page) {
  await page.route('**/api/v1/inventario/elementos*', (r) =>
    r.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        elementos: [
          {
            id: 'e-0',
            productoId: 'producto-0',
            tipo: 'ARMA',
            nombrePropio: 'Mi hacha',
          },
        ],
        numero: 0,
        tamanio: 16,
        totalElementos: 1,
        totalPaginas: 1,
        ultima: true,
      }),
    }),
  );
  await page.route('**/api/v1/productos/*', (r) =>
    r.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(PRODUCTO),
    }),
  );
  await page.goto(PAGINA);
  await page.waitForFunction(() => !document.querySelector('.estado-carga'));
}

test.describe('Ficha de detalle del producto', () => {
  test('La tarjeta del inventario abre la ficha del producto', async ({ page }) => {
    await conUnProducto(page);

    await page.locator('.vitrina__detalle').click();

    await expect(page.locator('.ficha')).toBeVisible();
    await expect(page.locator('.ficha__nombre')).toHaveText('Hacha de Vorn');
  });

  test('La ficha muestra descripcion, imagen y los atributos del tipo', async ({ page }) => {
    await conUnProducto(page);
    await page.locator('.vitrina__detalle').click();

    await expect(page.locator('.ficha__descripcion')).toHaveText(
      'Forjada en la niebla de los pantanos del norte.',
    );
    await expect(page.locator('.ficha__imagen')).toHaveAttribute('alt', 'Hacha de Vorn');
    await expect(page.locator('.ficha__etiqueta')).toHaveText(['Poder de ataque', 'Tasa de caida']);
  });

  test('La ficha se superpone a la vitrina y no la empuja hacia abajo', async ({ page }) => {
    await page.setViewportSize({ width: 1360, height: 768 });
    await conUnProducto(page);
    const altoAntes = await page.evaluate(() => document.documentElement.scrollHeight);

    await page.locator('.vitrina__detalle').click();
    await expect(page.locator('.ficha')).toBeVisible();

    // La capa flota sobre la vista: ni alarga el documento ni deja la ficha
    // fuera de la pantalla. Sin su hoja de estilos, la ficha se dibujaba
    // debajo de la vitrina y el jugador no la veia.
    const medidas = await page.evaluate(() => {
      const caja = document.querySelector('.ficha').getBoundingClientRect();
      return {
        alto: document.documentElement.scrollHeight,
        dentroDeLaPantalla: caja.top >= 0 && caja.bottom <= window.innerHeight,
        posicionDeLaCapa: getComputedStyle(document.querySelector('.ficha-capa')).position,
      };
    });
    expect(medidas.posicionDeLaCapa).toBe('fixed');
    expect(medidas.dentroDeLaPantalla).toBe(true);
    expect(medidas.alto).toBe(altoAntes);
  });

  test('La ficha se cierra con Escape y el foco vuelve a la tarjeta', async ({ page }) => {
    await conUnProducto(page);
    const boton = page.locator('.vitrina__detalle');
    await boton.click();
    await expect(page.locator('.ficha')).toBeVisible();

    await page.keyboard.press('Escape');

    await expect(page.locator('.ficha')).toHaveCount(0);
    await expect(boton).toBeFocused();
  });

  test('Se puede abrir y cerrar sin tocar el raton', async ({ page }) => {
    await conUnProducto(page);

    // Tabular hasta el boton de detalle y activarlo con el teclado.
    await page.locator('.vitrina__detalle').focus();
    await page.keyboard.press('Enter');

    await expect(page.locator('.ficha')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator('.ficha')).toHaveCount(0);
  });

  test('Si el catalogo falla, el jugador ve un aviso sin codigo de estado', async ({ page }) => {
    await conUnProducto(page);
    await page.unroute('**/api/v1/productos/*');
    await page.route('**/api/v1/productos/*', (r) =>
      r.fulfill({ status: 503, contentType: 'application/json', body: '{}' }),
    );

    await page.locator('.vitrina__detalle').click();

    const aviso = page.locator('.estado-error');
    await expect(aviso).toBeVisible();
    expect(await aviso.innerText()).not.toMatch(/\b[1-5]\d{2}\b/);
  });

  test('Un producto suspendido se ve con el indicador de no disponible', async ({ page }) => {
    await page.route('**/api/v1/inventario/elementos*', (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          elementos: [
            {
              id: 'e-0',
              productoId: 'producto-0',
              tipo: 'ARMA',
              nombrePropio: 'Mi hacha',
            },
          ],
          numero: 0,
          tamanio: 16,
          totalElementos: 1,
          totalPaginas: 1,
          ultima: true,
        }),
      }),
    );
    // RN-27: el catalogo devuelve el producto suspendido con su estado.
    await page.route('**/api/v1/productos/*', (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...PRODUCTO, estado: 'SUSPENDIDO' }),
      }),
    );
    await page.goto(PAGINA);
    await page.waitForFunction(() => !document.querySelector('.estado-carga'));

    await page.locator('.vitrina__detalle').click();

    // La ficha se muestra, no se oculta ni falla.
    await expect(page.locator('.ficha__nombre')).toHaveText('Hacha de Vorn');
    await expect(page.locator('.ficha__no-disponible')).toBeVisible();
    await expect(page.locator('.ficha__no-disponible')).toContainText(/sigue en tu inventario/i);
    // Y no se presenta como un error: sigue siendo suyo.
    await expect(page.locator('.estado-error')).toHaveCount(0);
  });
});
