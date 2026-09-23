/**
 * Lo que tiene que seguir siendo verdad aunque alguien borre el código y su
 * prueba a la vez — FRONTEND-INTEGRATION R0.5.
 *
 * ## Por qué existe este fichero
 *
 * El PR #648, que traía trabajo legítimo de contratos, se llevó por delante
 * 282 líneas de frontend ya fusionado: la acción real del botón de validación
 * de héroe, el arreglo de contraste del botón urgente y el plegado de los
 * filtros en móvil. **Y se llevó sus siete pruebas en el mismo diff.** Jest
 * pasó de 1239 a 1238 —menos siete míos, más seis de otro PR— y las tres
 * compuertas siguieron en verde. Nadie podía haberlo visto.
 *
 * Una prueba que vive al lado del código que protege no protege de eso. Estas
 * viven en `tests/visual/`, fuera de `frontend/app-web/src/`, y comprueban
 * **comportamiento en un navegador real**, no la forma del código. Para
 * saltárselas hay que borrar este fichero a propósito, que es un acto
 * distinto de reordenar unos ficheros de una vista.
 *
 * ## Qué NO es
 *
 * No es una comparación byte a byte con ningún commit. No sabe qué líneas
 * escribió quién. Comprueba tres hechos observables:
 *
 *   1. el botón principal de validación de héroe hace una operación real;
 *   2. la llamada a la acción urgente del mercado se puede leer;
 *   3. los filtros del mercado no tapan el mercado en un teléfono.
 *
 * Si mañana esos tres hechos se consiguen de otra manera, estas pruebas
 * siguen valiendo. Si dejan de ser verdad, fallan, venga de donde venga.
 */

import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { PREFIJO_WEB } from './vistas.js';
import { inyectarSesion, sesionSintetica } from './identidad.js';

/**
 * La raiz del repositorio.
 *
 * Playwright transpila las especificaciones, asi que `import.meta.url` no
 * existe aqui dentro. El arnes se lanza siempre desde `frontend/app-web`
 * —es donde vive `playwright.visual.config.js`—, asi que dos saltos arriba
 * es la raiz. Se comprueba leyendo algo que solo esta ahi, para que un
 * cambio de directorio de trabajo falle diciendo por que.
 */
const RAIZ = resolve(process.cwd(), '..', '..');

/** Lee un fichero del repositorio, o dice exactamente cual no encontro. */
function delRepositorio(ruta) {
  try {
    return readFileSync(resolve(RAIZ, ruta), 'utf8');
  } catch {
    throw new Error(
      `No encuentro ${ruta} desde ${RAIZ}. Estas pruebas asumen que Playwright ` +
        'se lanza desde frontend/app-web; si eso cambio, arregla RAIZ.',
    );
  }
}

/** Relación de contraste WCAG entre dos colores `rgb(...)` calculados. */
function contraste(colorA, colorB) {
  const luminancia = (color) => {
    const [r, g, b] = color
      .match(/\d+(\.\d+)?/g)
      .slice(0, 3)
      .map(Number)
      .map((canal) => {
        const s = canal / 255;
        return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
      });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };
  const [claro, oscuro] = [luminancia(colorA), luminancia(colorB)].sort((x, y) => y - x);
  return (claro + 0.05) / (oscuro + 0.05);
}

/** Sesión de jugador: las tres vistas de aquí son privadas o la piden. */
async function comoJugador(contexto) {
  await inyectarSesion(contexto, sesionSintetica({ apodo: 'qa_integracion', rol: 'JUGADOR' }));
}

test.describe('la acción real del botón de validación de héroe', () => {
  /**
   * El defecto original: los tres botones primarios de esta pantalla hacían
   * `console.info` y nada más. Se arregló, y un PR de contratos lo devolvió a
   * `console.info` sin que saltara nada.
   *
   * Esta prueba no mira el código: pulsa el botón y comprueba que el navegador
   * intenta algo de verdad —una petición al servidor o una navegación—. Da
   * igual cómo esté escrito.
   */
  test('pulsar el botón principal hace una operación real, no un mensaje de consola', async ({
    browser,
    baseURL,
  }) => {
    const contexto = await browser.newContext({ viewport: { width: 1280, height: 800 }, baseURL });
    await comoJugador(contexto);
    const pagina = await contexto.newPage();

    // El veredicto lo sirve el contrato; aquí se fija para llegar a la
    // variante que ofrece «Entrar a la sala».
    await pagina.route('**/api/v1/salas/*/verificacion-heroe', (ruta) =>
      ruta.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          resultado: 'DISPONIBLE',
          puedeIngresar: true,
          heroe: { id: 'h-1', nombre: 'Héroe de prueba', vidaMaxima: 120 },
          creditosRequeridos: 150,
        }),
      }),
    );

    // Lo que se vigila: que salga UNA operación real.
    let huboOperacion = false;
    await pagina.route('**/api/v1/salas/*/participantes', (ruta) => {
      huboOperacion = true;
      return ruta.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: 's-1' }),
      });
    });
    const mensajes = [];
    pagina.on('console', (m) => mensajes.push(m.text()));

    await pagina.goto(
      `/${PREFIJO_WEB}/plataforma/salas-partidas/validacion-heroe.html?sala=s-1`,
      { waitUntil: 'domcontentloaded' },
    );
    const confirmar = pagina.locator('[data-accion="confirmar"]');
    await expect(confirmar).toBeVisible({ timeout: 10_000 });

    const navego = pagina
      .waitForURL(/sala-batalla|inventario/, { timeout: 4000 })
      .then(() => true)
      .catch(() => false);
    await confirmar.click();
    const huboNavegacion = await navego;

    expect(
      huboOperacion || huboNavegacion,
      'el botón principal no pidió nada al servidor ni llevó a ninguna parte',
    ).toBe(true);

    expect(
      mensajes.filter((t) => t.includes('falta HU-SAL-003')),
      'el botón volvió a escribir en la consola en vez de actuar',
    ).toEqual([]);

    await contexto.close();
  });
});

test.describe('la llamada a la acción urgente del mercado se puede leer', () => {
  /**
   * El defecto original: `.animacion-latido` forzaba `color: var(--error)
   * !important`, y esa clase se pone también en el botón «Ir ahora», que lleva
   * relleno primario azul. Rojo sobre azul: 1,30:1 medido. Y el latido bajaba
   * la opacidad a 0,55, recortando el contraste a la mitad dos veces por
   * segundo.
   *
   * No se puede fotografiar en el laboratorio porque el botón solo existe con
   * una subasta a menos de diez segundos de cerrar. Así que aquí se monta el
   * componente con las hojas reales y se mide el color calculado.
   */
  test('«Ir ahora» cumple el contraste de texto de WCAG AA', async ({ browser }) => {
    const hojas = ['tokens', 'base', 'componentes']
      .map((n) => delRepositorio(`shared/ui-kit/css/${n}.css`))
      .concat(delRepositorio('frontend/app-web/src/cuentas/pujas.css'))
      .join('\n');

    const contexto = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    const pagina = await contexto.newPage();
    await pagina.setContent(`<!doctype html><meta charset="utf-8"><style>${hojas}</style>
      <body style="background: var(--fondo); padding: 24px">
        <button id="urgente" class="btn btn-primario btn-ir-ahora animacion-latido">Ir ahora</button>
        <button id="normal" class="btn btn-primario">Ver subasta</button>
      </body>`);

    const medido = await pagina.evaluate(() =>
      ['urgente', 'normal'].map((id) => {
        const estilo = getComputedStyle(document.getElementById(id));
        return { id, color: estilo.color, fondo: estilo.backgroundColor };
      }),
    );
    const urgente = medido.find((m) => m.id === 'urgente');
    const normal = medido.find((m) => m.id === 'normal');

    expect(
      Number(contraste(urgente.color, urgente.fondo).toFixed(2)),
      `«Ir ahora» quedó en ${contraste(urgente.color, urgente.fondo).toFixed(2)}:1`,
    ).toBeGreaterThanOrEqual(4.5);

    // Y no por haber apagado el botón normal: los dos son el mismo botón.
    expect(urgente.color).toBe(normal.color);

    await contexto.close();
  });

  test('el latido no baja la opacidad de lo que hay que leer', async ({ browser }) => {
    const pujas = delRepositorio('frontend/app-web/src/cuentas/pujas.css');
    const contexto = await browser.newContext({ viewport: { width: 800, height: 400 } });
    const pagina = await contexto.newPage();
    await pagina.setContent(`<!doctype html><meta charset="utf-8"><style>${pujas}</style>
      <body><span class="animacion-latido">0:08</span></body>`);

    // Se lee la regla del propio navegador, no el texto del fichero: si el
    // latido se reescribe con otro nombre, esto sigue valiendo mientras la
    // clase siga siendo la que se aplica.
    const fotogramas = await pagina.evaluate(() => {
      const nombre = getComputedStyle(document.querySelector('.animacion-latido')).animationName;
      const reglas = [...document.styleSheets[0].cssRules];
      const clave = reglas.find((r) => r.type === CSSRule.KEYFRAMES_RULE && r.name === nombre);
      return clave ? [...clave.cssRules].map((f) => f.style.opacity).filter(Boolean) : [];
    });

    expect(
      fotogramas.filter((o) => Number(o) < 1),
      'el latido vuelve a apagar el texto a media animación',
    ).toEqual([]);

    await contexto.close();
  });
});

test.describe('los filtros no tapan el mercado en un teléfono', () => {
  /**
   * El defecto original: a 375 px la rejilla pasa a una columna y el panel de
   * filtros —veinticuatro controles— se apila encima de los resultados. Unos
   * 1.400 px de casillas antes del primer objeto, en el mercado público.
   *
   * Se mide dónde empieza el contenido, no cómo está escrito el plegado.
   */
  test('a 375 px el contenido del mercado empieza en la primera pantalla', async ({
    browser,
    baseURL,
  }) => {
    const contexto = await browser.newContext({ viewport: { width: 375, height: 812 }, baseURL });
    await comoJugador(contexto);
    const pagina = await contexto.newPage();
    await pagina.goto(`/${PREFIJO_WEB}/cuentas/subastas.html`, { waitUntil: 'domcontentloaded' });
    await pagina.waitForTimeout(800);

    const arriba = await pagina
      .locator('#subastas-resultados')
      .evaluate((el) => el.getBoundingClientRect().top + window.scrollY);

    expect(
      Math.round(arriba),
      `el contenido del mercado empieza a ${Math.round(arriba)} px del principio`,
    ).toBeLessThan(812);

    await contexto.close();
  });

  test('a 1440 px los filtros siguen a la vista, sin plegar', async ({ browser, baseURL }) => {
    const contexto = await browser.newContext({ viewport: { width: 1440, height: 900 }, baseURL });
    await comoJugador(contexto);
    const pagina = await contexto.newPage();
    await pagina.goto(`/${PREFIJO_WEB}/cuentas/subastas.html`, { waitUntil: 'domcontentloaded' });
    await pagina.waitForTimeout(800);

    // En escritorio el panel no se esconde: lo que cambió en móvil no puede
    // haberse llevado por delante la columna de filtros de siempre.
    await expect(pagina.locator('.subastas-filtros')).toBeVisible();
    const alto = await pagina
      .locator('.subastas-filtros')
      .evaluate((el) => el.getBoundingClientRect().height);
    expect(alto, 'el panel de filtros de escritorio quedó plegado').toBeGreaterThan(200);

    await contexto.close();
  });
});
