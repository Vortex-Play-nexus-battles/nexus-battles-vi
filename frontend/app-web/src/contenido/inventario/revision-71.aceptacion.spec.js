/**
 * Revisión final del módulo Jugador / Inventario (13-oct) — §7.1 del Documento
 * Oficial, en navegador real, contra un servidor con la forma de
 * `inventario.yaml` 1.7.0: cada pestaña pide SU página (`categoria`), cada
 * objeto dice quién lo lleva (`equipadoEn`).
 *
 * Lo que aquí se mide y Jest no puede: que la tarjeta entera abra el detalle
 * con el ratón, que el foco no se escape del diálogo, que el resaltado de
 * hover/foco se vea en la carta del héroe, que los dieciséis quepan a
 * 1360×768 sin desplazamiento horizontal, y axe sin hallazgos serios ni
 * críticos en los tres estados (objetos, héroes, detalle abierto).
 */
import { AxeBuilder } from '@axe-core/playwright';
import { test, expect } from '@playwright/test';
import { prepararPagina } from './entorno-de-prueba.js';

const PAGINA = '/contenido/inventario/inventario.html';
const TAMANIO = 16;

const HEROES = [
  { id: 'h-ayla', productoId: 'p-tanque', tipo: 'HEROE', nombrePropio: 'Ayla' },
  { id: 'h-bruno', productoId: 'p-fuego', tipo: 'HEROE', nombrePropio: 'Bruno' },
];

/** Veinte objetos: dos páginas en «Objetos». Uno lo lleva Bruno; otro, en subasta. */
const OBJETOS = Array.from({ length: 20 }, (_, i) => {
  const base = {
    id: `o-${i}`,
    productoId: `p-arma-${i}`,
    tipo: 'ARMA',
    nombrePropio: `Espada ${i + 1}`,
    disponible: true,
    subastaId: null,
  };
  if (i === 0) {
    return { ...base, equipadoEn: { heroeId: 'h-bruno', nombreHeroe: 'Bruno' } };
  }
  if (i === 1) {
    return { ...base, disponible: false, subastaId: 'sub-1' };
  }
  return base;
});

function json(cuerpo, status = 200) {
  return { status, contentType: 'application/json', body: JSON.stringify(cuerpo) };
}

function paginaDe(lista, numero) {
  const totalPaginas = Math.ceil(lista.length / TAMANIO);
  return {
    elementos: lista.slice(numero * TAMANIO, numero * TAMANIO + TAMANIO),
    numero,
    tamanio: TAMANIO,
    totalElementos: lista.length,
    totalPaginas,
    ultima: totalPaginas === 0 || numero >= totalPaginas - 1,
  };
}

/**
 * El servidor de 1.7.0 simulado. Devuelve la lista de consultas al inventario
 * para que las pruebas puedan afirmar qué se pidió.
 */
async function conServidor171(page) {
  const consultas = [];
  await page.route('**/api/v1/inventario/elementos?*', (ruta) => {
    const url = new URL(ruta.request().url());
    const categoria = url.searchParams.get('categoria');
    const numero = Number(url.searchParams.get('pagina') ?? 0);
    consultas.push({ categoria, numero });
    const lista = categoria === 'HEROES' ? HEROES : OBJETOS;
    return ruta.fulfill(json(paginaDe(lista, numero)));
  });
  const PROTOTIPOS = { 'p-tanque': 'Guerrero Tanque', 'p-fuego': 'Mago Fuego' };
  await page.route('**/api/v1/productos/*', (ruta) => {
    const id = new URL(ruta.request().url()).pathname.split('/').at(-1);
    const prototipo = PROTOTIPOS[id] ?? null;
    const heroe = prototipo !== null;
    return ruta.fulfill(
      json({
        id,
        nombre: prototipo ?? 'Espada del Nexo',
        tipo: heroe ? 'HEROE' : 'ARMA',
        prototipo,
        descripcion: heroe ? 'Prototipo del catálogo.' : 'Forjada en el Nexo.',
        imagen: null,
        estado: 'ACTIVO',
        tiraje: -1,
        premium: false,
        poderDeAtaque: heroe ? undefined : 12,
      }),
    );
  });
  await page.route('**/api/v1/productos?*', (ruta) =>
    ruta.fulfill(json({ productos: [], numero: 0, totalPaginas: 0, totalElementos: 0 })),
  );
  await page.route('**/api/v1/inventario/heroes/*/estadisticas', (ruta) =>
    ruta.fulfill(json({ poder: 10, vida: 44, defensa: 11 })),
  );
  await page.route('**/api/v1/inventario/heroes/*/equipamiento', (ruta) => {
    const heroeId = new URL(ruta.request().url()).pathname.split('/').at(-2);
    return ruta.fulfill(
      json({
        heroeId,
        armas: heroeId === 'h-bruno' ? ['o-0'] : [],
        armaduras: {},
        items: [],
      }),
    );
  });
  return consultas;
}

async function abrir(page, ancla = 'objetos') {
  await page.setViewportSize({ width: 1360, height: 768 });
  await prepararPagina(page);
  await page.goto(`${PAGINA}#${ancla}`);
  await page.waitForFunction(() => !document.querySelector('.estado-carga'));
}

async function sinHallazgosGraves(page, contexto) {
  const { violations } = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const graves = violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`);
  expect(graves, `axe en ${contexto}`).toEqual([]);
}

const objetos = (page) => page.locator('.inventario__panel--objetos');

test.describe('§7.1 Mi inventario — revisión final', () => {
  test('cada pestaña pide al servidor solo su página de dieciséis', async ({ page }) => {
    const consultas = await conServidor171(page);
    await abrir(page);

    await expect(objetos(page).locator('.vitrina__producto')).toHaveCount(16);
    await expect(objetos(page).locator('.paginacion__pagina:not([data-direccion])')).toHaveText([
      '1',
      '2',
    ]);
    expect(consultas).toEqual([
      { categoria: 'HEROES', numero: 0 },
      { categoria: 'OBJETOS', numero: 0 },
    ]);
    // Los dieciséis caben a la resolución de referencia, sin desplazamiento lateral.
    const desborda = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(desborda).toBe(false);
  });

  test('un clic en la tarjeta abre el detalle con «En tu inventario»', async ({ page }) => {
    await conServidor171(page);
    await abrir(page);

    const tarjeta = objetos(page).locator('.vitrina__producto').first();
    await tarjeta.locator('.vitrina__nombre').click();

    const dialogo = page.getByRole('dialog');
    await expect(dialogo).toBeVisible();
    await expect(dialogo.locator('.ficha__nombre')).toHaveText('Espada del Nexo');
    const enTuInventario = dialogo.locator('[data-zona="en-tu-inventario"]');
    await expect(enTuInventario).toContainText('Lo llamas «Espada 1»');
    await expect(enTuInventario).toContainText('Bruno');
    // El botón de cerrar está dentro del diálogo modal.
    await expect(dialogo.getByRole('button', { name: /cerrar/i })).toBeVisible();
  });

  test('el foco no se escapa del detalle y vuelve a la tarjeta al cerrar', async ({ page }) => {
    await conServidor171(page);
    await abrir(page);

    const verDetalle = objetos(page).getByRole('button', { name: 'Ver detalle de Espada 3' });
    await verDetalle.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('dialog')).toBeVisible();

    for (let i = 0; i < 12; i += 1) {
      await page.keyboard.press('Tab');
      const dentro = await page.evaluate(() =>
        Boolean(document.querySelector('.ficha-capa')?.contains(document.activeElement)),
      );
      expect(dentro, `tras ${i + 1} Tab`).toBe(true);
    }
    for (let i = 0; i < 4; i += 1) {
      await page.keyboard.press('Shift+Tab');
      const dentro = await page.evaluate(() =>
        Boolean(document.querySelector('.ficha-capa')?.contains(document.activeElement)),
      );
      expect(dentro, `tras ${i + 1} Mayús+Tab`).toBe(true);
    }

    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(verDetalle).toBeFocused();
  });

  test('un clic en la carta del héroe abre su ficha', async ({ page }) => {
    await conServidor171(page);
    await abrir(page, 'heroes');

    const carta = page.locator('.inventario__panel--heroes .hero-card').first();
    await expect(carta).toBeVisible();
    await expect(carta).toHaveCSS('cursor', 'pointer');
    await carta.locator('h3, .hero-card__nombre').first().click();

    const dialogo = page.getByRole('dialog');
    await expect(dialogo).toBeVisible();
    await expect(dialogo.locator('[data-zona="en-tu-inventario"]')).toBeVisible();
  });

  test('la carta del héroe se resalta con el ratón y con el foco', async ({ page }) => {
    await conServidor171(page);
    await abrir(page, 'heroes');

    const carta = page.locator('.inventario__panel--heroes .hero-card').first();
    await expect(carta).toBeVisible();
    await page.mouse.move(0, 0);
    const reposo = await carta.evaluate((n) => getComputedStyle(n).boxShadow);

    await carta.hover();
    await expect.poll(() => carta.evaluate((n) => getComputedStyle(n).boxShadow)).not.toBe(reposo);

    await page.mouse.move(0, 0);
    await carta.getByRole('button', { name: 'Ver ficha de Ayla' }).focus();
    await expect.poll(() => carta.evaluate((n) => getComputedStyle(n).boxShadow)).not.toBe(reposo);
  });

  test('lo que lleva otro héroe se ofrece apagado, diciendo quién lo lleva', async ({ page }) => {
    await conServidor171(page);
    await abrir(page, 'heroes');

    await page.getByRole('button', { name: /^(Equipamiento de|Equiparlo:) Ayla$/ }).click();
    await expect(page.locator('.inventario-equipo')).toBeVisible();
    await page
      .locator('.ranura')
      .filter({ has: page.locator('.ranura__etiqueta', { hasText: /^Arma 1$/ }) })
      .locator('.ranura__caja')
      .click();

    const deBruno = page.locator('[data-elegir="o-0"]');
    await expect(deBruno).toBeDisabled();
    await expect(deBruno).toHaveText('Espada 1 · lo lleva Bruno');
    const enSubasta = page.locator('[data-elegir="o-1"]');
    await expect(enSubasta).toBeDisabled();
    await expect(enSubasta).toHaveText('Espada 2 · en subasta');
    await expect(page.locator('[data-elegir="o-2"]')).toBeEnabled();
  });

  test('axe: sin hallazgos serios ni críticos en objetos, héroes y el detalle', async ({
    page,
  }) => {
    await conServidor171(page);
    await abrir(page);
    await expect(objetos(page).locator('.vitrina__producto')).toHaveCount(16);
    await sinHallazgosGraves(page, 'la pestaña Objetos');

    await page.locator('#pestana-heroes').click();
    await expect(page.locator('.inventario__panel--heroes .hero-card').first()).toBeVisible();
    await sinHallazgosGraves(page, 'la pestaña Héroes');

    await page.locator('#pestana-objetos').click();
    await objetos(page).locator('.vitrina__nombre').first().click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await sinHallazgosGraves(page, 'el detalle abierto');
  });
});
