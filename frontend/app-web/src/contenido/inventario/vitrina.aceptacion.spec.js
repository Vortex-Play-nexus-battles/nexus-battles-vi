/**
 * HU-INV-001 - Pruebas de aceptacion, en navegador real.
 *
 * Traduccion uno a uno de los escenarios de
 * `pruebas-aceptacion/HU-INV-001-vitrina-del-inventario.feature`.
 * Cada `test` lleva el nombre del escenario que verifica.
 *
 * Aqui se mide lo que Jest no puede: desplazamiento horizontal y
 * legibilidad dependen del motor de maquetacion.
 */
import { test, expect } from '@playwright/test';

const JUGADOR = 'jugador-de-prueba';

/** Tamano minimo que aceptamos como legible para el texto de una tarjeta. */
const MINIMO_LEGIBLE_PX = 12;

function elemento(indice) {
  const tipos = ['HEROE', 'ARMA', 'ARMADURA', 'ITEM', 'EPICA', 'HABILIDAD'];
  return {
    id: `elemento-${indice}`,
    productoId: `producto-${indice}`,
    tipo: tipos[indice % tipos.length],
    nombrePropio: `Espada larga de Vorn ${indice}`,
  };
}

/** Responde la consulta paginada como lo hace el servicio de SCRUM-318. */
async function conInventarioDe(page, totalElementos) {
  await page.route('**/api/v1/inventario/elementos*', async (ruta) => {
    const url = new URL(ruta.request().url());
    const numero = Number(url.searchParams.get('pagina') ?? 0);
    const tamanio = 16;
    const desde = numero * tamanio;
    const elementos = Array.from(
      { length: Math.max(0, Math.min(tamanio, totalElementos - desde)) },
      (_, i) => elemento(desde + i),
    );
    const totalPaginas = Math.ceil(totalElementos / tamanio);
    await ruta.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        elementos,
        numero,
        tamanio,
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

/** true si el documento desborda a lo ancho. */
function hayDesplazamientoHorizontal(page) {
  return page.evaluate(() => {
    const raiz = document.documentElement;
    return raiz.scrollWidth > raiz.clientWidth;
  });
}

test.describe('Vitrina del inventario', () => {
  test('La vitrina muestra dieciseis productos en la resolucion de referencia', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1360, height: 768 });
    await conInventarioDe(page, 40);
    await abrirVitrina(page);

    await expect(page.locator('.vitrina__producto')).toHaveCount(16);
    expect(await hayDesplazamientoHorizontal(page)).toBe(false);
  });

  test('La vitrina se ve completa: los dieciseis caben en la pantalla de referencia', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1360, height: 768 });
    await conInventarioDe(page, 40);
    await abrirVitrina(page);

    // "la vitrina se ve completa": nada de lo que la historia pide mostrar
    // queda por debajo del pliegue, ni siquiera con la barra de HU-INV-004.
    const desborda = await page.evaluate(() => {
      const raiz = document.documentElement;
      return raiz.scrollHeight > raiz.clientHeight;
    });
    expect(desborda).toBe(false);
  });

  test('La vitrina no rellena huecos cuando hay menos de dieciseis productos', async ({ page }) => {
    await page.setViewportSize({ width: 1360, height: 768 });
    await conInventarioDe(page, 7);
    await abrirVitrina(page);

    await expect(page.locator('.vitrina__producto')).toHaveCount(7);
    expect(await hayDesplazamientoHorizontal(page)).toBe(false);
  });

  const resoluciones = [
    { ancho: 1024, alto: 768 },
    { ancho: 768, alto: 1024 },
    { ancho: 375, alto: 812 },
  ];

  for (const { ancho, alto } of resoluciones) {
    test(`El contenido se reorganiza en ${ancho} x ${alto} sin perder legibilidad`, async ({
      page,
    }) => {
      await page.setViewportSize({ width: ancho, height: alto });
      await conInventarioDe(page, 40);
      await abrirVitrina(page);

      // "se reorganiza para caber en el ancho disponible"
      const tarjetas = page.locator('.vitrina__producto');
      await expect(tarjetas).toHaveCount(16);
      const anchoTarjeta = (await tarjetas.first().boundingBox()).width;
      expect(anchoTarjeta).toBeLessThanOrEqual(ancho);

      // "no se produce desplazamiento horizontal"
      expect(await hayDesplazamientoHorizontal(page)).toBe(false);

      // "todo texto e icono de cada tarjeta permanece legible":
      // ni encogido por debajo del minimo ni recortado por su caja.
      const medidas = await page.evaluate((minimo) => {
        const problemas = [];
        for (const t of document.querySelectorAll('.vitrina__producto')) {
          for (const texto of t.querySelectorAll('p')) {
            const px = parseFloat(getComputedStyle(texto).fontSize);
            if (px < minimo) {
              problemas.push(`fuente ${px}px`);
            }
            if (texto.scrollWidth > texto.clientWidth + 1) {
              problemas.push(`texto recortado: ${texto.textContent}`);
            }
          }
        }
        return problemas;
      }, MINIMO_LEGIBLE_PX);
      expect(medidas).toEqual([]);
    });
  }

  test('Un inventario vacio muestra un estado explicativo', async ({ page }) => {
    await page.setViewportSize({ width: 1360, height: 768 });
    await conInventarioDe(page, 0);
    await abrirVitrina(page);

    // "se muestra un estado vacio que explica que aun no tiene productos"
    await expect(page.locator('.estado-vacio')).toBeVisible();
    await expect(page.locator('.estado-vacio')).toContainText(/todav[ií]a no tienes productos/i);

    // "no se muestra ningun mensaje de error"
    await expect(page.locator('.estado-error')).toHaveCount(0);
    const texto = await page.locator('body').innerText();
    expect(texto).not.toMatch(/error/i);

    // "no se muestra ningun codigo de estado del protocolo"
    expect(texto).not.toMatch(/\b[1-5]\d{2}\b/);
  });
});
