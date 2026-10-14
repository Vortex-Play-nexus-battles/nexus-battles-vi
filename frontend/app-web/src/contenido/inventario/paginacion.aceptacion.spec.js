/**
 * HU-INV-011 - Pruebas de aceptacion del control de paginacion, en navegador
 * real. Subtarea SCRUM-349: recorrido completo de un inventario grande.
 *
 * Aqui se mide lo que Jest no puede: que el control no introduzca
 * desplazamiento horizontal, que el foco sea visible al recorrerlo con el
 * teclado y que la ventana se desplace de verdad al avanzar.
 */
import { test, expect } from '@playwright/test';
import { prepararPagina } from './entorno-de-prueba.js';

const JUGADOR = 'jugador-de-prueba';

/** 40 paginas de 16: suficiente para que la ventana de diez tenga que moverse. */
const TOTAL_ELEMENTOS = 640;
const TAMANIO_PAGINA = 16;

function elemento(indice) {
  // UXC-1 — la vitrina de «Objetos» no lleva héroes (tienen su pestaña).
  const tipos = ['ARMA', 'ARMADURA', 'ITEM', 'EPICA', 'HABILIDAD'];
  return {
    id: `elemento-${indice}`,
    productoId: `producto-${indice}`,
    tipo: tipos[indice % tipos.length],
    nombrePropio: `Elemento ${indice + 1}`,
  };
}

/**
 * Como el servicio desde inventario.yaml 1.7.0: cada pestaña pide su categoría.
 * Este jugador no tiene héroes; todo lo que tiene son objetos.
 */
async function conInventarioDe(page, totalElementos) {
  await page.route('**/api/v1/inventario/elementos*', async (ruta) => {
    const url = new URL(ruta.request().url());
    const numero = Number(url.searchParams.get('pagina') ?? 0);
    if (url.searchParams.get('categoria') === 'HEROES') {
      await ruta.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          elementos: [],
          numero,
          tamanio: TAMANIO_PAGINA,
          totalElementos: 0,
          totalPaginas: 0,
          ultima: true,
        }),
      });
      return;
    }
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
  await prepararPagina(page);
  await page.goto(`/contenido/inventario/inventario.html?jugador=${JUGADOR}#objetos`);
  await page.waitForFunction(() => !document.querySelector('.estado-carga'));
}

/** El control de la vitrina de «Objetos» («Mis héroes» tiene el suyo). */
const OBJETOS = '.inventario__panel--objetos';
const casillas = (page) => page.locator(`${OBJETOS} .paginacion__pagina:not([data-direccion])`);
const flecha = (page, direccion) =>
  page.locator(`${OBJETOS} .paginacion__pagina[data-direccion="${direccion}"]`);

function hayDesplazamientoHorizontal(page) {
  return page.evaluate(() => {
    const raiz = document.documentElement;
    return raiz.scrollWidth > raiz.clientWidth;
  });
}

test.describe('Paginacion del inventario', () => {
  // --- Criterio 1 ---------------------------------------------------------

  test('Un inventario grande presenta diez casillas y marca la página en curso', async ({
    page,
  }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);
    await abrirVitrina(page);

    await expect(casillas(page)).toHaveCount(10);
    await expect(casillas(page)).toHaveText(['1', '2', '3', '4', '5', '6', '7', '8', '9', '10']);
    await expect(page.locator(`${OBJETOS} .paginacion__pagina[aria-current="page"]`)).toHaveText(
      '1',
    );
    await expect(page.locator(`${OBJETOS} .paginacion__info`)).toHaveText('Página 1 de 40');
  });

  test('Pulsar una casilla trae los productos de esa página', async ({ page }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);
    await abrirVitrina(page);

    await casillas(page).nth(2).click();

    // Pagina 3 (indice 2): elementos 33 a 48.
    await expect(page.locator('.vitrina__nombre').first()).toHaveText('Elemento 33');
    await expect(page.locator('.vitrina__producto')).toHaveCount(TAMANIO_PAGINA);
    await expect(page.locator(`${OBJETOS} .paginacion__pagina[aria-current="page"]`)).toHaveText(
      '3',
    );
  });

  // --- Criterio 2 ---------------------------------------------------------

  // Revisión final del módulo (13-oct) — §7.1: «si hay más páginas a la
  // derecha, aparece una flecha a la derecha para mostrar las opciones
  // ocultas» (y lo mismo a la izquierda). La flecha lleva a la primera página
  // oculta de su lado y la ventana se recentra sobre ella.
  test('Las flechas aparecen solo si hay páginas ocultas y las muestran', async ({ page }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);
    await abrirVitrina(page);

    const info = page.locator(`${OBJETOS} .paginacion__info`);
    await expect(flecha(page, 'anterior')).toHaveCount(0);
    await expect(flecha(page, 'siguiente')).toHaveAttribute(
      'aria-label',
      'Mostrar páginas siguientes (ir a la 11)',
    );

    await flecha(page, 'siguiente').click();

    await expect(info).toHaveText('Página 11 de 40');
    await expect(casillas(page)).toHaveText([
      '6',
      '7',
      '8',
      '9',
      '10',
      '11',
      '12',
      '13',
      '14',
      '15',
    ]);
    await expect(flecha(page, 'anterior')).toHaveCount(1);
    await expect(flecha(page, 'siguiente')).toHaveCount(1);

    // La de la izquierda revela las de la izquierda: la 5 y, con ella, la 1.
    await flecha(page, 'anterior').click();

    await expect(info).toHaveText('Página 5 de 40');
    await expect(casillas(page)).toHaveText(['1', '2', '3', '4', '5', '6', '7', '8', '9', '10']);
    await expect(flecha(page, 'anterior')).toHaveCount(0);
  });

  test('En la ultima página desaparece la flecha de avance', async ({ page }) => {
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
    const info = page.locator(`${OBJETOS} .paginacion__info`);
    for (let salto = 0; salto < 12; salto += 1) {
      const antes = await info.textContent();
      if (antes === 'Página 40 de 40') {
        break;
      }
      await casillas(page).last().click();
      await expect(info).not.toHaveText(antes);
    }

    await expect(info).toHaveText('Página 40 de 40');
    await expect(flecha(page, 'siguiente')).toHaveCount(0);
    await expect(flecha(page, 'anterior')).toHaveCount(1);
  });

  // Revisión final del módulo (13-oct) — §7.1 pide los números de las páginas
  // «a medida que se agregan productos»: con una sola página se ve el «1».
  // (Antes el control se ocultaba con una página.)
  test('Un inventario de una sola página muestra la casilla 1, sin flechas', async ({ page }) => {
    await conInventarioDe(page, 10);
    await abrirVitrina(page);

    const control = page.locator('.inventario__panel--objetos .paginacion');
    await expect(control).toBeVisible();
    await expect(control.locator('.paginacion__pagina:not([data-direccion])')).toHaveText(['1']);
    await expect(control.locator('[aria-current="page"]')).toHaveText('1');
    await expect(control.locator('[data-direccion]')).toHaveCount(0);
  });

  // --- Criterio 3 ---------------------------------------------------------
  //
  // La busqueda es HU-INV-002 y aun no existe. Lo verificable hoy es que el
  // cambio de pagina conserva el resto del contexto de la vista: la identidad
  // del jugador viaja igual en todas las consultas y la vista no se remonta.

  test('Al cambiar de página se conserva el contexto de la consulta', async ({ page }) => {
    const consultadas = [];
    await page.route('**/api/v1/inventario/elementos*', async (ruta) => {
      const url = new URL(ruta.request().url());
      const categoria = url.searchParams.get('categoria');
      consultadas.push({
        pagina: Number(url.searchParams.get('pagina') ?? 0),
        categoria,
        jugador: ruta.request().headers()['x-user-name'] ?? url.searchParams.get('jugador'),
      });
      const numero = Number(url.searchParams.get('pagina') ?? 0);
      if (categoria === 'HEROES') {
        await ruta.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            elementos: [],
            numero,
            tamanio: TAMANIO_PAGINA,
            totalElementos: 0,
            totalPaginas: 0,
            ultima: true,
          }),
        });
        return;
      }
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
    await expect(page.locator(`${OBJETOS} .paginacion__pagina[aria-current="page"]`)).toHaveText(
      '5',
    );

    // Revisión final (13-oct) — cada pestaña pide al servidor solo su página
    // (inventario 1.7.0): ni se reúne el inventario entero ni se pide de más.
    // Lo que CA-3 pide sigue igual: la página pedida es la última consulta y
    // el jugador es el mismo en todas.
    const deObjetos = consultadas.filter((c) => c.categoria === 'OBJETOS');
    expect(deObjetos.map((c) => c.pagina)).toEqual([0, 4]);
    expect(consultadas.at(-1)).toMatchObject({ pagina: 4, categoria: 'OBJETOS' });
    expect(consultadas.every((c) => c.categoria === 'OBJETOS' || c.categoria === 'HEROES')).toBe(
      true,
    );
    expect(new Set(consultadas.map((c) => c.jugador)).size).toBe(1);
  });

  // --- RNF: sin desplazamiento y operable por teclado ----------------------

  test('El control no introduce desplazamiento horizontal en ninguna resolucion', async ({
    page,
  }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);

    for (const ancho of [1360, 1024, 768, 375]) {
      await page.setViewportSize({ width: ancho, height: 768 });
      await abrirVitrina(page);
      await expect(page.locator(`${OBJETOS} .paginacion`)).toBeVisible();
      expect(await hayDesplazamientoHorizontal(page)).toBe(false);
    }
  });

  test('Se puede cambiar de página sin tocar el raton', async ({ page }) => {
    await conInventarioDe(page, TOTAL_ELEMENTOS);
    await abrirVitrina(page);

    const segunda = casillas(page).nth(1);
    await segunda.focus();
    await expect(segunda).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(page.locator(`${OBJETOS} .paginacion__pagina[aria-current="page"]`)).toHaveText(
      '2',
    );
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
