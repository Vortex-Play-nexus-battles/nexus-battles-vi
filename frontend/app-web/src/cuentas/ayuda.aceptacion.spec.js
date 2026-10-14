/**
 * Centro de ayuda (ms-chatbot.yaml 1.8.0, 7.4.3) — aceptación en navegador real.
 *
 * Lo que Jest no puede ver: que a 1366 y a 375 px no haya desplazamiento
 * horizontal, que el ancla de una categoría la deje a la vista con el foco en
 * su título, que todo se maneje con el teclado y lo que dice axe en los
 * cuatro estados. No hay servidor: `/api/v1/**` se responde aquí, y el
 * sistema de diseño (`shared/ui-kit/`), fuera de la raíz del servidor de
 * pruebas, se sirve desde el disco.
 */
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { AxeBuilder } from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const AQUI = path.dirname(fileURLToPath(import.meta.url));
const KIT = path.resolve(AQUI, '../../../../shared/ui-kit');
const PAGINA = '/cuentas/ayuda.html';

const LARGO =
  'Una respuesta larga para comprobar que el texto se parte y no empuja la página hacia los lados: ' +
  'superlargapalabrasinespaciosquenodebedesbordarlatarjetaenunteléfononiaunqueseamuyestrecho.';

/** Un centro con la forma de `GET /chat/ayuda`. */
const CENTRO = {
  categorias: [
    {
      categoria: 'PRODUCTO',
      ancla: 'categoria-producto',
      temas: [
        {
          titulo: 'Héroes disponibles',
          respuestaEs: `Hay 8 tipos de héroe. ${LARGO}`,
          respuestaEn: 'There are 8 hero types.',
          pasosEs: [],
          pasosEn: [],
        },
      ],
    },
    {
      categoria: 'CUENTA_Y_REGISTRO',
      ancla: 'categoria-cuenta-y-registro',
      temas: [
        {
          titulo: 'Recuperar la contraseña',
          respuestaEs:
            'Para recuperarla: 1) Ve a «¿Olvidaste tu contraseña?». 2) Escribe tu correo. 3) Usa el código.',
          respuestaEn: 'To recover it: 1) Go to the login. 2) Type your email. 3) Use the code.',
          pasosEs: ['Ve a «¿Olvidaste tu contraseña?».', 'Escribe tu correo.', 'Usa el código.'],
          pasosEn: ['Go to the login.', 'Type your email.', 'Use the code.'],
        },
        {
          titulo: 'Cambiar el apodo',
          respuestaEs: 'Desde Mi Cuenta, en «Perfil».',
          respuestaEn: null,
          pasosEs: [],
          pasosEn: [],
        },
      ],
    },
    ...[
      'MECANICA_JUEGO',
      'MODALIDAD_JUEGO',
      'SUBASTA_Y_COMERCIO',
      'SOPORTE_TECNICO',
      'POLITICAS_Y_TERMINOS',
      'FAQ_GENERAL',
    ].map((categoria) => ({
      categoria,
      ancla: `categoria-${categoria.toLowerCase().replaceAll('_', '-')}`,
      temas: Array.from({ length: 4 }, (_, indice) => ({
        titulo: `Pregunta ${indice + 1} de ${categoria.toLowerCase().replaceAll('_', ' ')}`,
        respuestaEs: `Respuesta ${indice + 1}. ${LARGO}`,
        respuestaEn: null,
        pasosEs: [],
        pasosEn: [],
      })),
    })),
  ],
};

async function servirKit(page) {
  await page.route('**/shared/ui-kit/**', async (ruta) => {
    const relativa = decodeURIComponent(
      new URL(ruta.request().url()).pathname.split('/shared/ui-kit/')[1] ?? '',
    );
    const archivo = path.join(KIT, relativa);
    if (!archivo.startsWith(KIT) || !existsSync(archivo)) {
      await ruta.fulfill({ status: 404, body: '' });
      return;
    }
    await ruta.fulfill({ path: archivo });
  });
}

function json(ruta, cuerpo, status = 200) {
  return ruta.fulfill({
    status,
    contentType: status >= 400 ? 'application/problem+json' : 'application/json',
    body: JSON.stringify(cuerpo),
  });
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {{ayuda?: (ruta: import('@playwright/test').Route) => Promise<void>,
 *          alPreguntar?: object}} [opciones]
 */
async function simularApi(page, { ayuda = null, alPreguntar = null } = {}) {
  await page.route('**/api/v1/**', async (ruta) => {
    const peticion = ruta.request();
    const { pathname } = new URL(peticion.url());
    if (pathname.endsWith('/chat/ayuda')) {
      return ayuda ? ayuda(ruta) : json(ruta, CENTRO);
    }
    if (pathname.endsWith('/chat/historial')) {
      return json(ruta, []);
    }
    if (pathname.endsWith('/chat/sugerencias')) {
      return json(ruta, []);
    }
    if (pathname.endsWith('/chat/preferencias')) {
      return json(ruta, { idioma: 'AUTOMATICO', nivelDetalle: 'NORMAL' });
    }
    if (pathname.endsWith('/chat/mensajes') && peticion.method() === 'POST' && alPreguntar) {
      return json(ruta, alPreguntar);
    }
    return json(ruta, { title: 'Not Found', status: 404 }, 404);
  });
}

async function abrir(page, { ancho = 1366, alto = 768, ancla = '', ...opciones } = {}) {
  await page.setViewportSize({ width: ancho, height: alto });
  await servirKit(page);
  await simularApi(page, opciones);
  await page.goto(`${PAGINA}${ancla}`);
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

async function sinDesplazamientoHorizontal(page) {
  const { ancho, visible } = await page.evaluate(() => ({
    ancho: document.documentElement.scrollWidth,
    visible: document.documentElement.clientWidth,
  }));
  expect(ancho).toBeLessThanOrEqual(visible);
}

const temas = (page) => page.locator('details.ayuda__tema');

test.describe('Centro de ayuda', () => {
  for (const { ancho, alto } of [
    { ancho: 1366, alto: 768 },
    { ancho: 375, alto: 667 },
  ]) {
    test(`a ${ancho} px: categorías, índice y temas, sin desplazamiento horizontal ni hallazgos graves`, async ({
      page,
    }) => {
      await abrir(page, { ancho, alto });

      await expect(page.getByRole('heading', { level: 1, name: 'Centro de ayuda' })).toBeVisible();
      await expect(page.locator('section.ayuda__categoria')).toHaveCount(8);
      await expect(temas(page)).toHaveCount(27);
      await expect(page.locator('[data-zona="indice-ayuda"] a')).toHaveCount(8);
      await sinDesplazamientoHorizontal(page);

      // Con los temas abiertos, el texto largo tampoco empuja hacia los lados.
      await page.getByLabel('Buscar en la ayuda').fill('larga');
      await expect(temas(page).first()).toHaveAttribute('open', '');
      await sinDesplazamientoHorizontal(page);
      await sinHallazgosGraves(page, `${ancho} px`);
    });
  }

  test('el ancla de una categoría la deja a la vista con el foco en su título', async ({
    page,
  }) => {
    await abrir(page, { ancla: '#categoria-cuenta-y-registro' });

    const titulo = page.locator('#categoria-cuenta-y-registro h2');
    await expect(titulo).toBeFocused();
    await expect(titulo).toBeInViewport();
  });

  test('con el teclado: buscar, abrir un tema y leer sus pasos', async ({ page }) => {
    await abrir(page);
    await expect(temas(page)).toHaveCount(27);

    const buscador = page.getByLabel('Buscar en la ayuda');
    await buscador.focus();
    await page.keyboard.type('contraseña');
    await expect(temas(page)).toHaveCount(1);
    await expect(page.locator('[data-zona="resumen-ayuda"]')).toHaveText(
      '1 tema coincide con «contraseña»',
    );

    // Se cierra y se vuelve a abrir con Enter sobre su título.
    const pregunta = page.locator('summary.ayuda__pregunta').first();
    await pregunta.focus();
    await page.keyboard.press('Enter');
    await expect(temas(page).first()).not.toHaveAttribute('open', '');
    await page.keyboard.press('Enter');
    await expect(page.locator('.ayuda__pasos li')).toHaveText([
      'Ve a «¿Olvidaste tu contraseña?».',
      'Escribe tu correo.',
      'Usa el código.',
    ]);
  });

  test('sin coincidencias lo dice, y «Ver todos los temas» vuelve a la lista', async ({ page }) => {
    await abrir(page);
    await page.getByLabel('Buscar en la ayuda').fill('dragones');

    await expect(page.getByText('Ningún tema coincide con «dragones»')).toBeVisible();
    await sinHallazgosGraves(page, 'sin coincidencias');
    await page.getByRole('button', { name: 'Ver todos los temas' }).click();
    await expect(temas(page)).toHaveCount(27);
  });

  test('si el servicio no responde lo dice y deja reintentar', async ({ page }) => {
    let intentos = 0;
    await abrir(page, {
      ayuda: async (ruta) => {
        intentos += 1;
        return intentos === 1 ? ruta.fulfill({ status: 503, body: '' }) : json(ruta, CENTRO);
      },
    });

    await expect(page.getByText('No se pudo cargar el centro de ayuda')).toBeVisible();
    await sinHallazgosGraves(page, 'error');
    await page.getByRole('button', { name: 'Reintentar' }).click();
    await expect(temas(page)).toHaveCount(27);
  });

  test('sin temas publicados lo dice', async ({ page }) => {
    await abrir(page, { ayuda: (ruta) => json(ruta, { categorias: [] }) });

    await expect(page.getByText('Todavía no hay temas publicados')).toBeVisible();
    await expect(page.getByLabel('Buscar en la ayuda')).toBeHidden();
    await sinHallazgosGraves(page, 'vacío');
  });

  test('el asistente enlaza aquí, abierto en la categoría de la pregunta', async ({ page }) => {
    await abrir(page, {
      alPreguntar: {
        id: 'm-bot-1',
        remitente: 'BOT',
        contenido: 'No entendí tu pregunta.',
        adjuntoUrl: null,
        fechaEnvio: '2026-10-14T15:00:01Z',
        enriquecido: {
          pasos: [],
          enlaces: [
            {
              texto: 'Ver el centro de ayuda',
              destino: 'ayuda',
              ancla: 'categoria-cuenta-y-registro',
            },
          ],
          tarjetas: [],
          respuestasRapidas: [],
          ofrecerSoporteHumano: true,
        },
      },
    });
    await page.locator('.chatbot-flotante').click();
    const ventana = page.locator('.chatbot-ventana');
    await expect(ventana).toBeVisible();
    const entrada = ventana.locator('textarea[name="contenido"]');
    await expect(entrada).toBeEnabled();
    await entrada.fill('zxqwv plorf');
    await entrada.press('Enter');

    const enlace = ventana.locator('.chatbot-enriquecido__enlace[data-destino="ayuda"]');
    await expect(enlace).toHaveText('Ver el centro de ayuda');
    await expect(enlace).toHaveAttribute(
      'href',
      /cuentas\/ayuda\.html#categoria-cuenta-y-registro$/,
    );
  });
});
