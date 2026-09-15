/**
 * HU-INV-011 - Pruebas de aceptacion del control de paginacion, en navegador
 * real. Subtarea SCRUM-349: recorrido completo de un inventario grande.
 *
 * Aqui se mide lo que Jest no puede: que el control no introduzca
 * desplazamiento horizontal, que el foco sea visible al recorrerlo con el
 * teclado y que la ventana se desplace de verdad al avanzar.
 */
import { test, expect } from '@playwright/test';

const JUGADOR = 'jugador-de-prueba';

/** 40 paginas de 16: suficiente para que la ventana de diez tenga que moverse. */
const TOTAL_ELEMENTOS = 640;
const TAMANIO_PAGINA = 16;

function elemento(indice) {
  const tipos = ['HEROE', 'ARMA', 'ARMADURA', 'ITEM', 'EPICA', 'HABILIDAD'];
  return {
    id: `elemento-${indice}`,
    productoId: `producto-${indice}`,
    tipo: tipos[indice % tipos.length],
    nombrePropio: `Elemento ${indice + 1}`,
  };
}

async function conInventarioDe(page, totalElementos) {
  await page.route('**/api/v1/inventario/elementos*', async (ruta) => {
    const url = new URL(ruta.request().url());
    const numero = Number(url.searchParams.get('pagina') ?? 0);
    const desde = numero * TAMANIO_PAGINA;
    const elementos = Array.from(
      { length: Math.max(0, Math.min(TAMANIO_PAGINA, totalElementos - desde)) },
      (_, i) => elemento(desde + i),
    );
    const totalPaginas = Math.ceil(totalElementos / TAMANIO_PAGINA);
    await ruta.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        elementos,
        numero,
        tamanio: TAMANIO_PAGINA,
        totalElementos,
        totalPaginas,
        ultima: totalPaginas === 0 || numero >= totalPaginas - 1,
      }),
    });
  });
}

async function abrirVitrina(page) {
  await page.goto(`/contenido/inventario/inventario.html?jugador=${JUGADOR}`);
  await page.waitForFunction(() => !document.querySelector('.estado-carga'));
}

const casillas = (page) => page.locator('.paginacion__pagina:not([data-direccion])');
const flecha = (page, direccion) =>
  page.locator(`.paginacion__pagina[data-direccion="${direccion}"]`);

function hayDesplazamientoHorizontal(page) {
  return page.evaluate(() => {
    const raiz = document.documentElement;
    return raiz.scrollWidth > raiz.clientWidth;
  });
}

test.describe('Paginacion del inventario', () => {
  // --- Criterio 1 ---------------------------------------------------------

  test('Un inventario grande presenta diez casillas y marca la pagina en curso', async ({
    page,
  }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);
    await abrirVitrina(page);

    await expect(casillas(page)).toHaveCount(10);
    await expect(casillas(page)).toHaveText(['1', '2', '3', '4', '5', '6', '7', '8', '9', '10']);
    await expect(page.locator('.paginacion__pagina[aria-current="page"]')).toHaveText('1');
    await expect(page.locator('.paginacion__info')).toHaveText('Pagina 1 de 40');
  });

  test('Pulsar una casilla trae los productos de esa pagina', async ({ page }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);
    await abrirVitrina(page);

    await casillas(page).nth(2).click();

    // Pagina 3 (indice 2): elementos 33 a 48.
    await expect(page.locator('.vitrina__nombre').first()).toHaveText('Elemento 33');
    await expect(page.locator('.vitrina__producto')).toHaveCount(TAMANIO_PAGINA);
    await expect(page.locator('.paginacion__pagina[aria-current="page"]')).toHaveText('3');
  });

  // --- Criterio 2 ---------------------------------------------------------

  test('La ventana de casillas se desplaza al avanzar y aparecen las dos flechas', async ({
    page,
  }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);
    await abrirVitrina(page);

    await expect(flecha(page, 'anterior')).toHaveCount(0);
    await expect(flecha(page, 'siguiente')).toHaveCount(1);

    const info = page.locator('.paginacion__info');
    for (let i = 0; i < 9; i += 1) {
      const antes = await info.textContent();
      await flecha(page, 'siguiente').click();
      await expect(info).not.toHaveText(antes);
    }

    await expect(info).toHaveText('Pagina 10 de 40');
    await expect(casillas(page)).toHaveText([
      '5',
      '6',
      '7',
      '8',
      '9',
      '10',
      '11',
      '12',
      '13',
      '14',
    ]);
    await expect(flecha(page, 'anterior')).toHaveCount(1);
    await expect(flecha(page, 'siguiente')).toHaveCount(1);
  });

  test('En la ultima pagina desaparece la flecha de avance', async ({ page }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);
    await abrirVitrina(page);

    // Se salta a la ultima casilla visible hasta llegar al final. Cada cambio
    // de pagina repinta el control entero, asi que se espera a que asiente
    // antes del siguiente clic: si no, se pulsa un boton ya reemplazado.
    //
    // El corte lo marca la pagina, no la flecha: la flecha desaparece cuando
    // la ventana alcanza el final (casillas 31-40), y ahi todavia se esta en
    // la 38. Eso es lo que pide el criterio 2 — la flecha habla del rango
    // visible, no de la pagina en curso.
    const info = page.locator('.paginacion__info');
    for (let salto = 0; salto < 12; salto += 1) {
      const antes = await info.textContent();
      if (antes === 'Pagina 40 de 40') {
        break;
      }
      await casillas(page).last().click();
      await expect(info).not.toHaveText(antes);
    }

    await expect(info).toHaveText('Pagina 40 de 40');
    await expect(flecha(page, 'siguiente')).toHaveCount(0);
    await expect(flecha(page, 'anterior')).toHaveCount(1);
  });

  test('Un inventario de una sola pagina no muestra control', async ({ page }) => {
    await conInventarioDe(page, 10);
    await abrirVitrina(page);

    await expect(page.locator('.paginacion')).toBeHidden();
  });

  // --- Criterio 3 ---------------------------------------------------------
  //
  // La busqueda es HU-INV-002 y aun no existe. Lo verificable hoy es que el
  // cambio de pagina conserva el resto del contexto de la vista: la identidad
  // del jugador viaja igual en todas las consultas y la vista no se remonta.

  test('Al cambiar de pagina se conserva el contexto de la consulta', async ({ page }) => {
    const consultadas = [];
    await page.route('**/api/v1/inventario/elementos*', async (ruta) => {
      const url = new URL(ruta.request().url());
      consultadas.push({
        pagina: Number(url.searchParams.get('pagina') ?? 0),
        jugador: ruta.request().headers()['x-user-name'] ?? url.searchParams.get('jugador'),
      });
      const numero = Number(url.searchParams.get('pagina') ?? 0);
      const desde = numero * TAMANIO_PAGINA;
      await ruta.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          elementos: Array.from({ length: TAMANIO_PAGINA }, (_, i) => elemento(desde + i)),
          numero,
          tamanio: TAMANIO_PAGINA,
          totalElementos: TOTAL_ELEMENTOS,
          totalPaginas: 40,
          ultima: false,
        }),
      });
    });
    await abrirVitrina(page);

    await casillas(page).nth(4).click();
    await expect(page.locator('.paginacion__pagina[aria-current="page"]')).toHaveText('5');

    expect(consultadas).toHaveLength(2);
    expect(consultadas[0].pagina).toBe(0);
    expect(consultadas[1].pagina).toBe(4);
    // El contexto del jugador es el mismo en las dos consultas.
    expect(consultadas[1].jugador).toBe(consultadas[0].jugador);
  });

  // --- RNF: sin desplazamiento y operable por teclado ----------------------

  test('El control no introduce desplazamiento horizontal en ninguna resolucion', async ({
    page,
  }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);

    for (const ancho of [1360, 1024, 768, 375]) {
      await page.setViewportSize({ width: ancho, height: 768 });
      await abrirVitrina(page);
      await expect(page.locator('.paginacion')).toBeVisible();
      expect(await hayDesplazamientoHorizontal(page)).toBe(false);
    }
  });

  test('Se puede cambiar de pagina sin tocar el raton', async ({ page }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);
    await abrirVitrina(page);

    const segunda = casillas(page).nth(1);
    await segunda.focus();
    await expect(segunda).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(page.locator('.paginacion__pagina[aria-current="page"]')).toHaveText('2');
  });

  test('La casilla enfocada muestra un contorno visible', async ({ page }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);
    await abrirVitrina(page);

    const segunda = casillas(page).nth(1);
    await segunda.focus();

    const contorno = await segunda.evaluate((el) => {
      const estilo = getComputedStyle(el);
      return { ancho: estilo.outlineWidth, estilo: estilo.outlineStyle };
    });
    expect(contorno.estilo).not.toBe('none');
    expect(Number.parseFloat(contorno.ancho)).toBeGreaterThan(0);
  });
});
