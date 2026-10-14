/**
 * Ventana del asistente (#506) — pruebas de aceptación en navegador real.
 *
 * Lo que Jest no puede ver: dónde queda el foco de verdad, qué se pinta antes
 * y qué después con la red de por medio, si la cabecera cabe a cada ancho, qué
 * capa queda encima de cuál y lo que dice axe de la ventana abierta.
 *
 * Se usa la portada pública (`cuentas/portada.html`), que monta el armazón y
 * con él el botón del asistente, como lo ve un visitante. No hay servidor:
 * toda llamada a `/api/v1/**` se responde aquí, y el sistema de diseño
 * (`shared/ui-kit/`), que queda fuera de la raíz del servidor de pruebas, se
 * sirve desde el disco (ver `contenido/inventario/entorno-de-prueba.js`).
 */
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { AxeBuilder } from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const AQUI = path.dirname(fileURLToPath(import.meta.url));

/** `shared/ui-kit/` en la raíz del repositorio: hojas, fuentes, iconos, marca y fondos. */
const KIT = path.resolve(AQUI, '../../../../../shared/ui-kit');

const PAGINA = '/cuentas/portada.html';

/** Los textos acordados para los rechazos (#506). */
const TEXTO_INYECCION =
  'Tu mensaje no se envió porque parece contener código o instrucciones del sistema. Escribe tu pregunta con palabras.';
const textoDeEspera = (segundos) =>
  `Escribiste muchos mensajes seguidos. Espera ${segundos} s y vuelve a intentarlo.`;

const USUARIO = {
  id: '6a3f7e1c-0000-4000-8000-000000000001',
  remitente: 'USUARIO',
  contenido: '¿Cómo pujo en una subasta?',
  adjuntoUrl: null,
  fechaEnvio: '2026-10-14T15:00:00Z',
  enriquecido: null,
};

const BOT = {
  id: '6a3f7e1c-0000-4000-8000-000000000002',
  remitente: 'BOT',
  contenido: 'Elige la subasta, escribe tu oferta y confírmala.',
  adjuntoUrl: null,
  fechaEnvio: '2026-10-14T15:00:01Z',
  enriquecido: null,
};

/** Responde las hojas, fuentes e imágenes del kit con los archivos reales. */
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

function json(ruta, cuerpo, status = 200, cabeceras = {}) {
  return ruta.fulfill({
    status,
    headers: cabeceras,
    contentType: status >= 400 ? 'application/problem+json' : 'application/json',
    body: JSON.stringify(cuerpo),
  });
}

/**
 * El servidor entero, de mentira. `alEnviar` decide qué responde
 * `POST /chat/mensajes`; sin él, una respuesta del bot.
 *
 * @param {import('@playwright/test').Page} page
 * @param {{historial?: object[], alEnviar?: (ruta: import('@playwright/test').Route) => Promise<void>}} [opciones]
 */
async function simularApi(page, { historial = [], alEnviar = null } = {}) {
  await page.route('**/api/v1/**', async (ruta) => {
    const peticion = ruta.request();
    const { pathname } = new URL(peticion.url());
    const metodo = peticion.method();

    if (pathname.endsWith('/chat/historial') && metodo === 'GET') {
      return json(ruta, historial);
    }
    if (pathname.endsWith('/chat/sugerencias')) {
      return json(ruta, []);
    }
    if (pathname.endsWith('/chat/preferencias')) {
      return json(ruta, { idioma: 'AUTOMATICO', nivelDetalle: 'NORMAL' });
    }
    if (pathname.endsWith('/chat/mensajes') && metodo === 'POST') {
      return alEnviar ? alEnviar(ruta) : json(ruta, BOT);
    }
    if (pathname.endsWith('/vitrina')) {
      return json(ruta, { content: [], totalElements: 0 });
    }
    return json(ruta, { title: 'Not Found', status: 404 }, 404);
  });
}

/** Abre la portada y la ventana del asistente con el botón flotante. */
async function abrirAsistente(page, opciones) {
  await servirKit(page);
  await simularApi(page, opciones);
  await page.goto(PAGINA);
  const lanzador = page.locator('.chatbot-flotante');
  await expect(lanzador).toBeVisible();
  await lanzador.click();
  const ventana = page.locator('.chatbot-ventana');
  await expect(ventana).toBeVisible();
  const entrada = ventana.locator('textarea[name="contenido"]');
  const enviar = ventana.locator('.chatbot-ventana__formulario button[type="submit"]');
  // La conversación cargada: «Enviar» se activa.
  await expect(enviar).toBeEnabled();
  return { lanzador, ventana, entrada, enviar };
}

test.describe('Ventana del asistente (#506)', () => {
  test('al abrir, el foco va a la caja de texto; Escape la cierra y vuelve al botón', async ({
    page,
  }) => {
    const { lanzador, ventana, entrada } = await abrirAsistente(page);

    await expect(entrada).toBeFocused();

    await page.keyboard.press('Escape');
    await expect(ventana).toBeHidden();
    await expect(lanzador).toBeFocused();
    await expect(lanzador).toHaveAttribute('aria-expanded', 'false');

    // Con el teclado: Enter sobre el botón la vuelve a abrir con el foco en la caja.
    await page.keyboard.press('Enter');
    await expect(ventana).toBeVisible();
    await expect(entrada).toBeFocused();
  });

  test('la pregunta aparece en cuanto se envía, antes que la respuesta', async ({ page }) => {
    let soltar;
    const retenida = new Promise((resolver) => {
      soltar = resolver;
    });
    const { ventana, entrada } = await abrirAsistente(page, {
      alEnviar: async (ruta) => {
        await retenida;
        await json(ruta, BOT);
      },
    });

    await entrada.fill('¿Cómo pujo en una subasta?');
    await entrada.press('Enter');

    const pregunta = ventana.locator('.chatbot-ventana__mensaje--usuario');
    await expect(pregunta).toContainText('¿Cómo pujo en una subasta?');
    await expect(ventana.locator('.chatbot-ventana__escribiendo')).toBeVisible();
    await expect(ventana.locator(`[data-mensaje-id="${BOT.id}"]`)).toHaveCount(0);
    await expect(entrada).toHaveValue('');

    soltar();

    await expect(ventana.locator(`[data-mensaje-id="${BOT.id}"]`)).toBeVisible();
    await expect(ventana.locator('.chatbot-ventana__escribiendo')).toHaveCount(0);
    const orden = await ventana
      .locator('.chatbot-ventana__mensaje')
      .evaluateAll((burbujas) =>
        burbujas.map((b) =>
          b.classList.contains('chatbot-ventana__mensaje--usuario') ? 'usuario' : 'bot',
        ),
      );
    expect(orden).toEqual(['usuario', 'bot']);
    // El lector de pantalla oye quién habla.
    await expect(pregunta.locator('.solo-lectores')).toHaveText(/^Tú:\s*$/);
    await expect(
      ventana.locator(`[data-mensaje-id="${BOT.id}"] .solo-lectores`).first(),
    ).toHaveText(/^Asistente:\s*$/);
  });

  test('un 422 INYECCION_DETECTADA se explica y la caja conserva el texto', async ({ page }) => {
    const { ventana, entrada, enviar } = await abrirAsistente(page, {
      alEnviar: (ruta) =>
        json(
          ruta,
          {
            title: 'Unprocessable Content',
            status: 422,
            detail: 'El mensaje parece contener código.',
            motivo: 'INYECCION_DETECTADA',
          },
          422,
        ),
    });

    await entrada.fill('<script>alert(1)</script>');
    await entrada.press('Enter');

    const aviso = ventana.locator('.chatbot-ventana__aviso [role="alert"]');
    await expect(aviso).toHaveText(TEXTO_INYECCION);
    await expect(entrada).toHaveValue('<script>alert(1)</script>');
    await expect(entrada).toBeFocused();
    await expect(enviar).toBeEnabled();
    // La pregunta rechazada no se queda en la conversación.
    await expect(ventana.locator('.chatbot-ventana__mensaje--usuario')).toHaveCount(0);
  });

  test('un 429 dice cuánto esperar, bloquea «Enviar» y lo reactiva solo', async ({ page }) => {
    let intentos = 0;
    const { ventana, entrada, enviar } = await abrirAsistente(page, {
      alEnviar: (ruta) => {
        intentos += 1;
        return json(
          ruta,
          { title: 'Too Many Requests', status: 429, motivo: 'LIMITE_DE_FRECUENCIA' },
          429,
          { 'Retry-After': '2' },
        );
      },
    });

    await entrada.fill('otra pregunta');
    await entrada.press('Enter');

    await expect(ventana.locator('.chatbot-ventana__aviso [role="alert"]')).toHaveText(
      textoDeEspera(2),
    );
    await expect(entrada).toHaveValue('otra pregunta');
    await expect(enviar).toBeDisabled();
    // Durante la espera, Enter tampoco envía.
    await entrada.press('Enter');

    await expect(enviar).toBeEnabled({ timeout: 5_000 });
    await expect(ventana.locator('.chatbot-ventana__aviso')).toBeHidden();
    await expect(entrada).toHaveValue('otra pregunta');
    // Comprobado ya pasada la espera: si Enter hubiera enviado, habría llegado.
    expect(intentos).toBe(1);
  });

  test('en un teléfono de 375×667, con un aviso abierto, la caja y la cabecera siguen a la vista', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    const { ventana, entrada } = await abrirAsistente(page, {
      historial: [USUARIO, BOT],
      alEnviar: (ruta) =>
        json(
          ruta,
          { title: 'Too Many Requests', status: 429, motivo: 'LIMITE_DE_FRECUENCIA' },
          429,
          { 'Retry-After': '30' },
        ),
    });

    await entrada.fill('otra pregunta');
    await entrada.press('Enter');
    await expect(ventana.locator('.chatbot-ventana__aviso [role="alert"]')).toHaveText(
      textoDeEspera(30),
    );

    // Lo que no cabe se desplaza dentro de la ventana; la caja con el texto
    // devuelto y la barra de título (minimizar, cerrar) quedan enteras.
    await expect(entrada).toHaveValue('otra pregunta');
    await expect(entrada).toBeInViewport({ ratio: 1 });
    await expect(ventana.locator('.chatbot-ventana__titulo')).toBeInViewport({ ratio: 1 });
    await expect(ventana.locator('[data-accion="cerrar-asistente"]')).toBeInViewport({ ratio: 1 });
  });

  test('minimizar deja la barra de título y restaurar devuelve la conversación', async ({
    page,
  }) => {
    const { ventana, entrada } = await abrirAsistente(page, { historial: [USUARIO, BOT] });
    const minimizar = ventana.locator('[data-accion="minimizar-asistente"]');

    await minimizar.click();
    await expect(minimizar).toHaveAttribute('aria-label', 'Restaurar el asistente');
    await expect(ventana.locator('.chatbot-ventana__titulo')).toBeVisible();
    await expect(entrada).toBeHidden();
    await expect(ventana.locator('[role="log"]')).toBeHidden();
    await expect(ventana.locator('[data-accion="preferencias-asistente"]')).toBeHidden();

    await minimizar.click();
    await expect(minimizar).toHaveAttribute('aria-label', 'Minimizar el asistente');
    await expect(entrada).toBeVisible();
    await expect(entrada).toBeFocused();
    await expect(ventana.locator('.chatbot-ventana__mensaje')).toHaveCount(2);
  });

  const TAMANOS = [
    { ancho: 1440, alto: 800 },
    { ancho: 1366, alto: 800 },
    { ancho: 1280, alto: 800 },
    { ancho: 1024, alto: 800 },
    { ancho: 768, alto: 800 },
    { ancho: 375, alto: 667 },
  ];

  for (const { ancho, alto } of TAMANOS) {
    test(`a ${ancho}×${alto} la cabecera se ve entera y nada desborda a lo ancho`, async ({
      page,
    }) => {
      await page.setViewportSize({ width: ancho, height: alto });
      const { ventana } = await abrirAsistente(page, { historial: [USUARIO, BOT] });

      await expect(ventana.locator('.chatbot-ventana__titulo')).toBeVisible();
      await expect(ventana.locator('[data-accion="preferencias-asistente"]')).toBeVisible();

      const medida = await page.evaluate(() => {
        const el = document.querySelector('.chatbot-ventana');
        const caja = el.getBoundingClientRect();
        const cabecera = el.querySelector('.chatbot-ventana__cabecera');
        const piezas = [...cabecera.querySelectorAll('.chatbot-ventana__titulo, button')].map(
          (pieza) => {
            const r = pieza.getBoundingClientRect();
            return {
              nombre: pieza.getAttribute('aria-label') || pieza.textContent.trim(),
              izquierda: r.left,
              derecha: r.right,
              ancho: r.width,
              alto: r.height,
              recortada: pieza.scrollWidth > pieza.clientWidth + 1,
            };
          },
        );
        const desbordadas = [...el.querySelectorAll('*')]
          .filter((nodo) => nodo.getClientRects().length > 0)
          .filter((nodo) => {
            const r = nodo.getBoundingClientRect();
            return r.left < caja.left - 0.5 || r.right > caja.right + 0.5;
          })
          .map((nodo) => nodo.className || nodo.tagName);
        return {
          ventana: {
            izquierda: caja.left,
            derecha: caja.right,
            anchoDeContenido: el.scrollWidth,
            anchoVisible: el.clientWidth,
          },
          cabecera: { anchoDeContenido: cabecera.scrollWidth, anchoVisible: cabecera.clientWidth },
          piezas,
          desbordadas,
          anchoDePantalla: window.innerWidth,
        };
      });

      // La ventana cabe en la pantalla y no se desplaza a lo ancho por dentro.
      expect(medida.ventana.izquierda).toBeGreaterThanOrEqual(0);
      expect(medida.ventana.derecha).toBeLessThanOrEqual(medida.anchoDePantalla);
      expect(medida.ventana.anchoDeContenido).toBeLessThanOrEqual(medida.ventana.anchoVisible);
      expect(medida.cabecera.anchoDeContenido).toBeLessThanOrEqual(medida.cabecera.anchoVisible);
      expect(medida.desbordadas).toEqual([]);

      // El título y las cinco acciones, enteros y dentro de la ventana.
      expect(medida.piezas).toHaveLength(6);
      for (const pieza of medida.piezas) {
        expect(pieza.ancho, pieza.nombre).toBeGreaterThan(0);
        expect(pieza.alto, pieza.nombre).toBeGreaterThan(0);
        expect(pieza.izquierda, pieza.nombre).toBeGreaterThanOrEqual(medida.ventana.izquierda);
        expect(pieza.derecha, pieza.nombre).toBeLessThanOrEqual(medida.ventana.derecha);
        expect(pieza.recortada, pieza.nombre).toBe(false);
      }
      const titulo = medida.piezas.find((pieza) => pieza.nombre === 'Asistente');
      expect(titulo.ancho).toBeGreaterThan(40);
    });
  }

  test('un visitante que pide hablar con soporte sabe cómo conseguirlo', async ({ page }) => {
    const { ventana, entrada } = await abrirAsistente(page, {
      alEnviar: (ruta) =>
        json(ruta, {
          ...BOT,
          contenido: 'No encontré una respuesta para eso.',
          enriquecido: {
            pasos: [],
            enlaces: [],
            tarjetas: [],
            respuestasRapidas: [],
            ofrecerSoporteHumano: true,
          },
        }),
    });

    await entrada.fill('quiero hablar con una persona');
    await entrada.press('Enter');
    await ventana.getByRole('button', { name: 'Hablar con soporte' }).click();

    const panel = ventana.locator('[data-chatbot-soporte]');
    await expect(panel).toBeVisible();
    await expect(panel).toContainText(
      'Para hablar con una persona del equipo necesitas una cuenta: inicia sesión o crea una.',
    );

    const entrar = panel.getByRole('link', { name: 'Iniciar sesión' });
    await expect(entrar).toBeFocused();
    const login = new URL(await entrar.getAttribute('href'));
    expect(login.pathname).toBe('/cuentas/login.html');
    expect(login.searchParams.get('volver')).toBe(PAGINA);

    const crear = panel.getByRole('link', { name: 'Crear cuenta' });
    expect(new URL(await crear.getAttribute('href')).pathname).toBe('/cuentas/registro.html');
    await expect(panel.locator('form')).toHaveCount(0);
  });

  test('el asistente queda sobre un aviso de la vista y bajo un modal', async ({ page }) => {
    const { lanzador, ventana } = await abrirAsistente(page);
    // El aviso cruzado de las pujas, con su hoja real, y otro forzado a la
    // esquina del botón (donde caía antes de #506) para que se solapen.
    await page.addStyleTag({ url: '/cuentas/pujas.css' });
    await page.evaluate(() => {
      for (const abajo of ['', '24px']) {
        const aviso = document.createElement('aside');
        aviso.className = 'toast-cruzado-flotante';
        aviso.textContent = '¡Te superaron en otra subasta!';
        aviso.style.bottom = abajo;
        // Sin la animación de entrada, que lo empieza 30 px más abajo: se
        // mide dónde se queda, no un fotograma de camino.
        aviso.style.animation = 'none';
        document.body.append(aviso);
      }
    });

    const encima = (locator) =>
      locator.evaluate((el) => {
        const r = el.getBoundingClientRect();
        const arriba = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
        return el.contains(arriba);
      });

    expect(await encima(lanzador)).toBe(true);
    expect(await encima(ventana.locator('.chatbot-ventana__formulario'))).toBe(true);

    // Un modal (el velo de los diálogos) sí los tapa.
    await page.evaluate(() => {
      const velo = document.createElement('div');
      velo.className = 'velo';
      document.body.append(velo);
    });
    expect(await encima(lanzador)).toBe(false);
    expect(await encima(ventana.locator('.chatbot-ventana__formulario'))).toBe(false);
  });

  test('axe no encuentra nada grave ni crítico en la ventana abierta', async ({ page }) => {
    const { ventana, entrada } = await abrirAsistente(page, { historial: [USUARIO, BOT] });
    // También con un aviso de rechazo a la vista.
    await page.unroute('**/api/v1/**');
    await simularApi(page, {
      historial: [USUARIO, BOT],
      alEnviar: (ruta) =>
        json(ruta, { title: 'x', status: 422, motivo: 'CONTENIDO_BLOQUEADO' }, 422),
    });
    await entrada.fill('una palabra prohibida');
    await entrada.press('Enter');
    await expect(ventana.locator('.chatbot-ventana__aviso [role="alert"]')).toBeVisible();

    const { violations } = await new AxeBuilder({ page })
      .include('.chatbot-ventana')
      .include('.chatbot-flotante')
      .analyze();
    const graves = violations
      .filter((v) => v.impact === 'serious' || v.impact === 'critical')
      .map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`);
    expect(graves).toEqual([]);
  });
});
