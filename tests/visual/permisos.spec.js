/**
 * Lo que cada persona ve y lo que NO ve — UX-R3.9, §25.
 *
 * ## Qué comprueba esto que no comprueba `acceso.test.js`
 *
 * `acceso.test.js` prueba la política: dado un rol y una vista, qué debería
 * pasar. Eso corre en jsdom, sobre la matriz, en milisegundos, y es donde
 * debe estar la mayor parte de la cobertura.
 *
 * Esto prueba otra cosa: que el producto **servido de verdad, abierto en un
 * navegador de verdad, con una identidad de verdad**, se comporta como dice
 * la matriz. Es la diferencia entre «`puedeVer` devuelve DENEGADA» y «un
 * jugador que escribe esa URL no ve la pantalla». La primera es una función;
 * la segunda es la promesa.
 *
 * Con `VISUAL_BASE` apuntando a un backend y las variables
 * `UX_SUPERADMIN_EMAIL` / `UX_SUPERADMIN_PASSWORD`, las identidades son
 * cuentas reales creadas por los endpoints reales. Sin eso, son tokens que
 * solo sirven para que el guard del navegador deje pintar — que es
 * exactamente lo que hay que comprobar aquí, porque lo que se prueba es el
 * guard del navegador, no el del servidor.
 *
 * El del servidor es otra prueba, y es la que de verdad protege
 * (RF-RBAC-004). Esta solo comprueba que no se enseñan puertas que no se
 * pueden abrir.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { PREFIJO_WEB, VISTAS } from './vistas.js';
import { inyectarSesion } from './identidad.js';
import { NOMBRE, conseguirPersonas, personasDe } from './personas.js';

const CON_BACKEND = Boolean(process.env.VISUAL_BASE);

/**
 * Las de la trastienda, según la matriz.
 *
 * UX-R4.6 — el nombre de la prueba decía «las nueve» y ya son once. La lista
 * siempre estuvo bien, porque sale de `MATRIZ` y crece con ella; lo que
 * envejeció fue la frase, y nadie volvió a contar. Ahora el número se escribe
 * solo, así que el informe de Playwright dice cuántas vistas se comprobaron
 * de verdad en vez de cuántas había el día que se escribió la prueba.
 */
const TRASTIENDA = VISTAS.filter((v) => v.armazon === 'admin');

/** Una privada de jugador, para la comprobación del visitante. */
const PRIVADAS_DE_JUGADOR = VISTAS.filter((v) => v.armazon === 'jugador' && v.acceso !== 'publica');

let sesiones = {};

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ baseURL }) => {
  if (!CON_BACKEND) {
    ({ sesiones } = await conseguirPersonas(null));
    return;
  }
  const api = await apiRequest.newContext({ baseURL, ignoreHTTPSErrors: true });
  try {
    ({ sesiones } = await conseguirPersonas(api));
  } finally {
    await api.dispose();
  }
});

/**
 * Abre una vista con una persona y dice qué pasó.
 *
 * @returns {Promise<{url: string, denegada: boolean, texto: string}>}
 */
async function abrir(browser, baseURL, vista, persona) {
  const contexto = await browser.newContext({ viewport: { width: 1280, height: 800 }, baseURL });
  try {
    await inyectarSesion(contexto, sesiones[persona] ?? null);
    const pagina = await contexto.newPage();
    await pagina.goto(`/${PREFIJO_WEB}/${vista.ruta}`, { waitUntil: 'domcontentloaded' });
    await pagina.waitForTimeout(700);
    return {
      url: pagina.url(),
      denegada: await pagina.evaluate(
        () => document.documentElement.dataset.acceso === 'denegado',
      ),
      texto: (await pagina.locator('body').innerText()).trim(),
    };
  } finally {
    await contexto.close();
  }
}

test.describe('un visitante no ve la aplicación', () => {
  test('toda vista privada lo manda al login, con vuelta', async ({ browser, baseURL }) => {
    const fallos = [];
    for (const vista of [...PRIVADAS_DE_JUGADOR, ...TRASTIENDA]) {
      const visto = await abrir(browser, baseURL, vista, 'anonimo');
      if (!visto.url.includes('login.html')) {
        fallos.push(`${vista.id}: acabó en ${visto.url}`);
      } else if (!new URL(visto.url).searchParams.get('volver')) {
        fallos.push(`${vista.id}: fue al login pero sin ruta de vuelta`);
      }
    }
    expect(fallos).toEqual([]);
  });

  test('el portal no lleva la navegación de la aplicación', async ({ browser, baseURL }) => {
    // El hallazgo que abrió UX-R3: los seis destinos de RF-INV-008 encima de
    // la pantalla de inicio de sesión, cuatro de ellos devolviendo al login.
    const login = VISTAS.find((v) => v.id === 'login');
    const visto = await abrir(browser, baseURL, login, 'anonimo');
    for (const destino of ['Jugar online', 'Mi inventario', 'Mi Cuenta', 'Misiones']) {
      expect(visto.texto, `el portal enseña «${destino}»`).not.toContain(destino);
    }
  });
});

test.describe('un jugador no ve la trastienda (§15)', () => {
  test(`las ${TRASTIENDA.length} vistas de consola le dicen que no, y no se pintan`, async ({
    browser,
    baseURL,
  }) => {
    // Si un día el filtro deja de acertar, esto falla en vez de pasar
    // recorriendo una lista vacía, que es como una comprobación de permisos
    // se convierte en un adorno verde.
    expect(TRASTIENDA.length).toBeGreaterThanOrEqual(9);
    const fallos = [];
    for (const vista of TRASTIENDA) {
      const visto = await abrir(browser, baseURL, vista, 'jugador');
      if (!visto.denegada) {
        fallos.push(`${vista.id}: se abrió para un jugador`);
        continue;
      }
      // Y lo dice con palabras, no con un código.
      if (!visto.texto.includes('No tienes acceso a esta sección')) {
        fallos.push(`${vista.id}: denegada, pero sin explicarlo`);
      }
      if (/\b(401|403|500|502)\b/.test(visto.texto)) {
        fallos.push(`${vista.id}: enseña un código HTTP`);
      }
    }
    expect(fallos).toEqual([]);
  });

  test('tampoco por la navegación: su armazón no nombra la consola', async ({
    browser,
    baseURL,
  }) => {
    const home = VISTAS.find((v) => v.id === 'home');
    const visto = await abrir(browser, baseURL, home, 'jugador');
    for (const rastro of ['Consola', 'Gestión de usuarios', 'Auditoría', 'Parámetros']) {
      expect(visto.texto, `la home del jugador nombra «${rastro}»`).not.toContain(rastro);
    }
  });
});

test.describe('cada rol de trastienda llega hasta donde le toca', () => {
  for (const vista of VISTAS.filter((v) => v.armazon === 'admin')) {
    const { para, denegadaPara } = personasDe(vista);

    test(`${vista.id}: entra ${NOMBRE[para]}${
      denegadaPara.length > 0 ? `, no ${denegadaPara.map((p) => NOMBRE[p]).join(' ni ')}` : ''
    }`, async ({ browser, baseURL }) => {
      const suyo = await abrir(browser, baseURL, vista, para);
      expect(suyo.denegada, `${NOMBRE[para]} no pudo abrir ${vista.id}`).toBe(false);
      expect(suyo.url).not.toContain('login.html');

      for (const persona of denegadaPara) {
        const ajeno = await abrir(browser, baseURL, vista, persona);
        expect(ajeno.denegada, `${NOMBRE[persona]} abrió ${vista.id} y no debería`).toBe(true);
      }
    });
  }
});
