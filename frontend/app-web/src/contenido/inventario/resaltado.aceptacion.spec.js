/**
 * HU-INV-013 - Resaltado del producto al senalar. Pruebas de aceptacion en
 * navegador real (issue #44, SCRUM-276).
 *
 * La historia es enteramente de presentacion: el realce vive en la hoja de
 * estilos. jsdom no aplica CSS externo ni calcula diseno, asi que aqui es
 * donde se puede verificar de verdad — igual que la reorganizacion
 * responsiva de HU-INV-001.
 */
import { test, expect } from '@playwright/test';

const JUGADOR = 'jugador-de-prueba';

function elemento(indice) {
  const tipos = ['HEROE', 'ARMA', 'ARMADURA', 'ITEM', 'EPICA', 'HABILIDAD'];
  return {
    id: `elemento-${indice}`,
    productoId: `producto-${indice}`,
    tipo: tipos[indice % tipos.length],
    nombrePropio: `Elemento ${indice + 1}`,
  };
}

async function conInventarioDe(page, total) {
  await page.route('**/api/v1/inventario/elementos*', async (ruta) => {
    await ruta.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        elementos: Array.from({ length: total }, (_, i) => elemento(i)),
        numero: 0,
        tamanio: 16,
        totalElementos: total,
        totalPaginas: 1,
        ultima: true,
      }),
    });
  });
}

async function abrirVitrina(page) {
  await page.goto(`/contenido/inventario/inventario.html?jugador=${JUGADOR}`);
  await page.waitForFunction(() => !document.querySelector('.estado-carga'));
}

/** Las propiedades con las que se dibuja el realce. */
function aspectoDe(tarjeta) {
  return tarjeta.evaluate((el) => {
    const e = getComputedStyle(el);
    return {
      borde: e.borderColor,
      fondo: e.backgroundColor,
      sombra: e.boxShadow,
      anchoBorde: e.borderTopWidth,
      relleno: e.paddingTop,
    };
  });
}

/**
 * El realce entra con una transicion, asi que leer justo despues de senalar
 * devuelve un valor a medio camino. Se espera a que dos lecturas seguidas
 * coincidan en vez de fijar una espera a ojo.
 */
async function aspectoEstable(tarjeta) {
  let previo = await aspectoDe(tarjeta);
  for (let intento = 0; intento < 20; intento += 1) {
    await tarjeta.page().waitForTimeout(30);
    const actual = await aspectoDe(tarjeta);
    if (JSON.stringify(actual) === JSON.stringify(previo)) {
      return actual;
    }
    previo = actual;
  }
  return previo;
}

/** Caja de cada tarjeta, redondeada para ignorar el ruido subpixel. */
function cajasDeTodas(page) {
  return page.evaluate(() =>
    [...document.querySelectorAll('.vitrina__producto')].map((el) => {
      const c = el.getBoundingClientRect();
      return {
        x: Math.round(c.x),
        y: Math.round(c.y),
        ancho: Math.round(c.width),
        alto: Math.round(c.height),
      };
    }),
  );
}

/** Aparta el puntero de la cuadricula sin salir de la pagina. */
async function apartarPuntero(page) {
  await page.mouse.move(2, 2);
}

test.describe('Resaltado del producto al senalar', () => {
  // --- Criterio 1 ---------------------------------------------------------

  test('La tarjeta cambia de estado visual cuando el puntero entra', async ({ page }) => {
    await conInventarioDe(page, 16);
    await abrirVitrina(page);

    const tarjeta = page.locator('.vitrina__producto').first();
    const enReposo = await aspectoDe(tarjeta);

    await tarjeta.hover();
    const senalada = await aspectoEstable(tarjeta);

    // "De forma perceptible": no basta con que cambie algo invisible.
    expect(senalada).not.toEqual(enReposo);
    expect(senalada.borde).not.toBe(enReposo.borde);
    expect(senalada.sombra).not.toBe(enReposo.sombra);
    expect(senalada.sombra).not.toBe('none');
  });

  test('La tarjeta vuelve a su estado normal cuando el puntero sale', async ({ page }) => {
    await conInventarioDe(page, 16);
    await abrirVitrina(page);

    const tarjeta = page.locator('.vitrina__producto').first();
    const enReposo = await aspectoDe(tarjeta);

    await tarjeta.hover();
    expect(await aspectoDe(tarjeta)).not.toEqual(enReposo);

    await apartarPuntero(page);
    await expect
      .poll(async () => JSON.stringify(await aspectoDe(tarjeta)))
      .toBe(JSON.stringify(enReposo));
  });

  test('Solo se resalta la tarjeta senalada, no toda la cuadricula', async ({ page }) => {
    await conInventarioDe(page, 16);
    await abrirVitrina(page);

    const tarjetas = page.locator('.vitrina__producto');
    const vecina = tarjetas.nth(1);
    const enReposo = await aspectoDe(vecina);

    await tarjetas.first().hover();

    expect(await aspectoDe(vecina)).toEqual(enReposo);
  });

  // --- Criterio 2 ---------------------------------------------------------

  test('El mismo realce se aplica al recibir el foco por teclado', async ({ page }) => {
    await conInventarioDe(page, 16);
    await abrirVitrina(page);

    const tarjeta = page.locator('.vitrina__producto').first();
    const enReposo = await aspectoDe(tarjeta);

    await tarjeta.hover();
    const conPuntero = await aspectoEstable(tarjeta);
    await apartarPuntero(page);
    await aspectoEstable(tarjeta);

    // La tarjeta es un <li>: quien recibe el foco es un control de dentro.
    await tarjeta.locator('button').first().focus();
    const conFoco = await aspectoEstable(tarjeta);

    expect(conFoco).not.toEqual(enReposo);
    expect(conFoco.borde).toBe(conPuntero.borde);
    expect(conFoco.sombra).toBe(conPuntero.sombra);
  });

  test('El realce por foco desaparece al salir de la tarjeta', async ({ page }) => {
    await conInventarioDe(page, 16);
    await abrirVitrina(page);

    const tarjetas = page.locator('.vitrina__producto');
    const primera = tarjetas.first();
    const enReposo = await aspectoDe(primera);

    await primera.locator('button').first().focus();
    expect(await aspectoEstable(primera)).not.toEqual(enReposo);

    await tarjetas.nth(1).locator('button').first().focus();
    expect(await aspectoEstable(primera)).toEqual(enReposo);
  });

  // --- Criterio 3 ---------------------------------------------------------

  test('El realce no altera la posicion ni el tamano de las demas tarjetas', async ({ page }) => {
    await conInventarioDe(page, 16);
    await abrirVitrina(page);

    const antes = await cajasDeTodas(page);
    await page.locator('.vitrina__producto').first().hover();
    const durante = await cajasDeTodas(page);

    // Se comparan todas: si la senalada creciera, en una rejilla arrastraria
    // a su fila entera, y eso es exactamente lo que el criterio prohibe.
    expect(durante).toEqual(antes);
  });

  test('El realce por foco tampoco mueve la cuadricula', async ({ page }) => {
    await conInventarioDe(page, 16);
    await abrirVitrina(page);

    const antes = await cajasDeTodas(page);
    await page.locator('.vitrina__producto').nth(5).locator('button').first().focus();

    expect(await cajasDeTodas(page)).toEqual(antes);
  });

  test('El realce no engorda el borde ni el relleno de la tarjeta', async ({ page }) => {
    await conInventarioDe(page, 16);
    await abrirVitrina(page);

    const tarjeta = page.locator('.vitrina__producto').first();
    const enReposo = await aspectoDe(tarjeta);

    await tarjeta.hover();
    const senalada = await aspectoDe(tarjeta);

    // Cambiar el ancho del borde o el relleno movería el contenido de la
    // tarjeta aunque la rejilla no se moviera: el realce se dibuja con
    // color y sombra, que no ocupan sitio.
    expect(senalada.anchoBorde).toBe(enReposo.anchoBorde);
    expect(senalada.relleno).toBe(enReposo.relleno);
  });

  test('El realce se mantiene en la resolucion mas estrecha', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await conInventarioDe(page, 16);
    await abrirVitrina(page);

    const tarjeta = page.locator('.vitrina__producto').first();
    const enReposo = await aspectoDe(tarjeta);
    const antes = await cajasDeTodas(page);

    await tarjeta.hover();

    expect(await aspectoDe(tarjeta)).not.toEqual(enReposo);
    expect(await cajasDeTodas(page)).toEqual(antes);
  });
});
