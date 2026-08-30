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

async function conInventarioEditable(page, iniciales = [], { rechazarModificacion = false } = {}) {
  const elementos = [...iniciales];
  await page.route('**/api/v1/inventario/elementos**', async (ruta) => {
    const solicitud = ruta.request();
    const metodo = solicitud.method();
    expect(solicitud.headers()['x-user-name']).toBe(JUGADOR);

    if (metodo === 'POST') {
      const creado = { id: `elemento-${elementos.length + 1}`, ...solicitud.postDataJSON() };
      elementos.push(creado);
      await ruta.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(creado),
      });
      return;
    }

    if (metodo === 'PATCH') {
      if (rechazarModificacion) {
        await ruta.fulfill({
          status: 403,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            title: 'Inventario ajeno',
            detail: 'No tienes permiso sobre ese inventario.',
          }),
        });
        return;
      }
      const id = solicitud.url().split('/').at(-1);
      const indice = elementos.findIndex((actual) => actual.id === id);
      elementos[indice] = { ...elementos[indice], ...solicitud.postDataJSON() };
      await ruta.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(elementos[indice]),
      });
      return;
    }

    const tamanio = 16;
    const numero = Number(new URL(solicitud.url()).searchParams.get('pagina') ?? 0);
    const desde = numero * tamanio;
    const totalPaginas = Math.ceil(elementos.length / tamanio);
    await ruta.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        elementos: elementos.slice(desde, desde + tamanio),
        numero,
        tamanio,
        totalElementos: elementos.length,
        totalPaginas,
        ultima: totalPaginas === 0 || numero >= totalPaginas - 1,
      }),
    });
  });
}

test.describe('Creacion y edicion del inventario propio', () => {
  test('Un elemento creado se refleja en la vitrina', async ({ page }) => {
    await conInventarioEditable(page);
    await abrirVitrina(page);

    await page.getByRole('button', { name: 'Agregar elemento' }).click();
    await page.getByLabel('Producto').fill('producto-amuleto');
    await page.getByLabel('Tipo').selectOption('ITEM');
    await page.getByLabel('Nombre').fill('Amuleto de Niebla');
    await page.getByRole('button', { name: 'Guardar' }).click();

    await expect(page.locator('.vitrina__producto')).toHaveCount(1);
    await expect(page.locator('.vitrina__nombre')).toHaveText('Amuleto de Niebla');
    await expect(page.locator('.inventario__mensaje')).toHaveText('Elemento creado.');
  });

  test('Un nombre modificado se refleja en la misma tarjeta', async ({ page }) => {
    await conInventarioEditable(page, [elemento(0)]);
    await abrirVitrina(page);

    await page.getByRole('button', { name: /Editar Espada larga de Vorn 0/ }).click();
    await page.getByLabel('Nombre').fill('Espada de Bruma');
    const patchEnviado = page.waitForRequest(
      (solicitud) => solicitud.method() === 'PATCH' && solicitud.url().endsWith('/elemento-0'),
    );
    await page.getByRole('button', { name: 'Guardar' }).click();
    await expect((await patchEnviado).postDataJSON()).toEqual({ nombrePropio: 'Espada de Bruma' });

    await expect(page.locator('.vitrina__producto')).toHaveCount(1);
    await expect(page.locator('.vitrina__nombre')).toHaveText('Espada de Bruma');
    await expect(page.locator('.inventario__mensaje')).toHaveText('Elemento actualizado.');
  });

  test('Una modificacion rechazada conserva la tarjeta e informa el permiso', async ({ page }) => {
    await conInventarioEditable(page, [elemento(0)], { rechazarModificacion: true });
    await abrirVitrina(page);

    await page.getByRole('button', { name: /Editar Espada larga de Vorn 0/ }).click();
    await page.getByLabel('Nombre').fill('Nombre ajeno');
    await page.getByRole('button', { name: 'Guardar' }).click();

    await expect(page.locator('.vitrina__nombre')).toHaveText('Espada larga de Vorn 0');
    await expect(page.locator('.inventario__mensaje')).toContainText(/no tienes permiso/i);
    await expect(page.locator('.inventario__mensaje')).not.toContainText('403');
  });
});
