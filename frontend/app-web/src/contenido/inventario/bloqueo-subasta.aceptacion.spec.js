/** HU-INV-010 - Un producto publicado en subasta figura y permanece no disponible. */
import { test, expect } from '@playwright/test';
import { prepararPagina } from './entorno-de-prueba.js';

const HEROE = {
  id: 'heroe-1',
  productoId: 'producto-heroe',
  tipo: 'HEROE',
  nombrePropio: 'Ayla',
  disponible: true,
  subastaId: null,
};

const ARMA_BLOQUEADA = {
  id: 'arma-1',
  productoId: 'producto-arma',
  tipo: 'ARMA',
  nombrePropio: 'Espada de Bruma',
  disponible: false,
  subastaId: '89d9040d-52e0-44ae-8d8c-8ec033978afb',
};

test('el producto bloqueado se muestra no disponible y no permite operarlo', async ({ page }) => {
  await page.route('**/api/v1/inventario/elementos**', (ruta) =>
    ruta.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        elementos: [HEROE, ARMA_BLOQUEADA],
        numero: 0,
        tamanio: 16,
        totalElementos: 2,
        totalPaginas: 1,
        ultima: true,
      }),
    }),
  );
  await page.route('**/api/v1/inventario/heroes/heroe-1/equipamiento', (ruta) =>
    ruta.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ heroeId: 'heroe-1', armas: [], armaduras: {}, items: [] }),
    }),
  );

  await prepararPagina(page, { apodo: 'jugador-A' });
  // UXC-1 — los objetos viven en la pestaña «Objetos»; el héroe, en «Héroes».
  await page.goto('/contenido/inventario/inventario.html?jugador=jugador-A#objetos');

  const tarjeta = page.locator('[data-elemento-id="arma-1"]');
  await expect(tarjeta.getByText('No disponible')).toBeVisible();
  await expect(tarjeta.getByRole('button', { name: 'Editar Espada de Bruma' })).toBeDisabled();

  // El #605 reescribio el equipamiento sobre las ranuras del kit: ya no hay un
  // boton «No disponible», sino una ranura que abre el selector, y alli el
  // objeto bloqueado aparece deshabilitado y **diciendo por que**.
  await page.locator('#pestana-heroes').click();
  await page.getByRole('button', { name: 'Gestionar el equipamiento de Ayla' }).click();
  await page.getByRole('button', { name: /Arma 1/ }).click();

  const opcion = page.getByRole('button', { name: /Espada de Bruma . en subasta/ });
  await expect(opcion).toBeDisabled();
  // «No se esconde: el jugador tiene que poder entender por que no puede».
  await expect(opcion).toHaveAttribute('title', 'Está publicado en una subasta');
});
