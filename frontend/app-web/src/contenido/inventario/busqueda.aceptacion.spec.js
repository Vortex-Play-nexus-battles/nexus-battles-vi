/**
 * HU-INV-002 - Pruebas de aceptacion de la busqueda en navegador real.
 *
 * Traduce los escenarios de `HU-INV-002-busqueda-inventario.feature` y
 * verifica la peticion que enlaza el campo de la vitrina con el endpoint.
 */
import { test, expect } from '@playwright/test';

const JUGADOR = 'jugador-de-prueba';
const PRODUCTOS = [
  {
    id: 'elemento-arma',
    productoId: 'producto-arma-001',
    tipo: 'ARMA',
    nombrePropio: 'Espada de Bruma',
  },
  {
    id: 'elemento-item',
    productoId: 'reliquia-solar-002',
    tipo: 'ITEM',
    nombrePropio: 'Amuleto Carmesí',
  },
  {
    id: 'elemento-armadura',
    productoId: 'proteccion-norte-003',
    tipo: 'ARMADURA',
    nombrePropio: 'Manoplas del Guardián',
    parteArmadura: 'GUANTES',
  },
];

function normalizar(texto) {
  return String(texto ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

function coincide(producto, criterio) {
  const texto = normalizar(criterio);
  return ['productoId', 'tipo', 'nombrePropio', 'parteArmadura'].some((campo) =>
    normalizar(producto[campo]).includes(texto),
  );
}

function pagina(elementos) {
  return {
    elementos,
    numero: 0,
    tamanio: 16,
    totalElementos: elementos.length,
    totalPaginas: elementos.length === 0 ? 0 : 1,
    ultima: true,
  };
}

async function prepararInventario(page) {
  const busquedas = [];
  await page.route('**/api/v1/inventario/elementos**', async (ruta) => {
    const solicitud = ruta.request();
    const url = new URL(solicitud.url());

    if (url.pathname.endsWith('/busqueda')) {
      const criterio = url.searchParams.get('criterio');
      busquedas.push({
        criterio,
        pagina: url.searchParams.get('pagina'),
        identidad: solicitud.headers()['x-user-name'],
      });
      await ruta.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(pagina(PRODUCTOS.filter((producto) => coincide(producto, criterio)))),
      });
      return;
    }

    await ruta.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(pagina(PRODUCTOS)),
    });
  });
  return busquedas;
}

async function abrirVitrina(page) {
  await page.goto(`/contenido/inventario/inventario.html?jugador=${JUGADOR}`);
  await expect(page.locator('.vitrina__producto')).toHaveCount(PRODUCTOS.length);
}

function formularioBusqueda(page) {
  return page.locator('.inventario-busqueda');
}

const casos = [
  { campo: 'nombre propio', criterio: 'bruma', esperado: 'Espada de Bruma' },
  { campo: 'identificador', criterio: 'solar', esperado: 'Amuleto Carmesí' },
  { campo: 'tipo', criterio: 'armadura', esperado: 'Manoplas del Guardián' },
  { campo: 'parte de armadura', criterio: 'guantes', esperado: 'Manoplas del Guardián' },
];

for (const { campo, criterio, esperado } of casos) {
  test(`Un producto se localiza por ${campo}`, async ({ page }) => {
    const busquedas = await prepararInventario(page);
    await abrirVitrina(page);

    const formulario = formularioBusqueda(page);
    await formulario.getByRole('searchbox').fill(criterio);
    await formulario.getByRole('button', { name: 'Buscar', exact: true }).click();

    await expect(page.locator('.vitrina__producto')).toHaveCount(1);
    await expect(page.locator('.vitrina__nombre')).toHaveText(esperado);
    expect(busquedas).toEqual([{ criterio, pagina: '0', identidad: JUGADOR }]);
  });
}

test('Un criterio menor de cuatro caracteres no inicia la busqueda', async ({ page }) => {
  const busquedas = await prepararInventario(page);
  await abrirVitrina(page);

  const formulario = formularioBusqueda(page);
  const campo = formulario.getByRole('searchbox');
  await campo.fill('arm');
  await formulario.getByRole('button', { name: 'Buscar', exact: true }).click();

  expect(await campo.evaluate((elemento) => elemento.validity.tooShort)).toBe(true);
  expect(busquedas).toEqual([]);
  await expect(page.locator('.vitrina__producto')).toHaveCount(PRODUCTOS.length);
});

test('Una busqueda valida puede no tener coincidencias', async ({ page }) => {
  await prepararInventario(page);
  await abrirVitrina(page);

  const formulario = formularioBusqueda(page);
  await formulario.getByRole('searchbox').fill('inexistente');
  await formulario.getByRole('button', { name: 'Buscar', exact: true }).click();

  await expect(page.locator('.estado-vacio')).toContainText(/no encontramos productos/i);
  await expect(page.locator('.inventario__mensaje')).toContainText('0 resultados');
  await expect(page.locator('.estado-error')).toHaveCount(0);
});

test('El jugador limpia la busqueda y vuelve a ver su inventario', async ({ page }) => {
  await prepararInventario(page);
  await abrirVitrina(page);

  const formulario = formularioBusqueda(page);
  const campo = formulario.getByRole('searchbox');
  await campo.fill('bruma');
  await formulario.getByRole('button', { name: 'Buscar', exact: true }).click();
  await expect(page.locator('.vitrina__producto')).toHaveCount(1);

  await formulario.getByRole('button', { name: 'Limpiar' }).click();

  await expect(page.locator('.vitrina__producto')).toHaveCount(PRODUCTOS.length);
  await expect(campo).toHaveValue('');
  await expect(formulario.getByRole('button', { name: 'Limpiar' })).toBeHidden();
});
