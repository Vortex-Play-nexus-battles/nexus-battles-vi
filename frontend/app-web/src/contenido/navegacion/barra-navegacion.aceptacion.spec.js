/**
 * HU-INV-004 - Aceptacion de la barra superior, en navegador real.
 * Fuente: Proyecto Integrador II, secciones 7.1-7.1.1, pp. 34-35.
 */
import { test, expect } from '@playwright/test';

const PAGINA = '/contenido/inventario/index.html?propietario=jugador-de-prueba';

async function abrir(page) {
  await page.route('**/api/v1/inventarios/**', (ruta) => ruta.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      elementos: [], numero: 0, tamanio: 16,
      totalElementos: 0, totalPaginas: 0, ultima: true,
    }),
  }));
  await page.goto(PAGINA);
  await expect(page.locator('.barra')).toBeVisible();
}

test.describe('Barra superior de navegacion', () => {

  test('Cualquier pantalla del grupo presenta la barra con busqueda y las seis secciones',
    async ({ page }) => {
      await abrir(page);

      await expect(page.locator('.barra__busqueda input')).toBeVisible();
      await expect(page.locator('.barra__acceso')).toHaveText([
        'Jugar online', 'Misiones', 'Torneo', 'Mi inventario', 'Subasta', 'Mi Cuenta',
      ]);
    });

  test('La seccion en curso queda marcada como activa',
    async ({ page }) => {
      await abrir(page);

      const activo = page.locator('.barra__acceso--activo');
      await expect(activo).toHaveCount(1);
      await expect(activo).toHaveText('Mi inventario');
    });

  test('Un visitante que abre Mi Cuenta recibe unicamente la opcion de registro',
    async ({ page }) => {
      await abrir(page);

      await page.locator('.barra__acceso', { hasText: 'Mi Cuenta' }).click();

      await expect(page.locator('.barra__opcion-cuenta')).toHaveText(['Registrarse']);
    });

  test('La barra no introduce desplazamiento horizontal en la resolucion mas estrecha',
    async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 812 });
      await abrir(page);

      const desborda = await page.evaluate(() => {
        const raiz = document.documentElement;
        return raiz.scrollWidth > raiz.clientWidth;
      });
      expect(desborda).toBe(false);
    });
});
