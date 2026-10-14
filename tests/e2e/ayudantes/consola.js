// @ts-check
/**
 * El recorrido de la consola de administración (SA-9): lo usan el banco
 * (`super-administrador.e2e.spec.js`) y AWS DEV
 * (`consola-super-administrador.smoke.spec.js`) para comprobar lo mismo en
 * los dos entornos.
 *
 * Lo que mira en cada sección es lo que pidió el informe de revisión del
 * Super Administrador: que cargue con su título, que se quede en su dirección
 * limpia, que ningún enlace apunte a un `.html` (punto crítico externo), que
 * no enseñe textos de desarrollo (puntos 2 a 23) y que no rompa con un error
 * de la página.
 */

import { expect } from '@playwright/test';

/** Lo que el informe de revisión pidió quitar de la consola. */
export const TEXTOS_DE_DESARROLLO =
  /Fuente: GET|Sondeo real|NO IMPLEMENTADO|SERVICIO DEGRADADO|Pendiente HU-|Charter|RNF-|shared\/libs|no recalcula/;

/** Las secciones de la consola, por su dirección limpia, con su título. */
export const SECCIONES_DE_LA_CONSOLA = Object.freeze([
  ['/consola', 'Control del Nexo'],
  ['/consola/control', 'Control integral'],
  ['/consola/usuarios', 'Gestión de usuarios'],
  ['/consola/usuarios/nueva-cuenta', 'Crear cuenta administrativa'],
  ['/consola/productos', 'Catálogo'],
  ['/consola/productos/nuevo', 'Nuevo producto'],
  ['/consola/productos/banners', 'Banners y anuncios'],
  ['/consola/comentarios', 'Comentarios reportados'],
  ['/consola/sanciones', 'Sanciones'],
  ['/consola/lista-negra', 'Lista negra'],
  ['/consola/parametros', 'Parámetros de plataforma'],
  ['/consola/metricas', 'Métricas de plataforma'],
  ['/consola/tecnico', 'Tablero técnico'],
  ['/consola/asistente', 'Asistente'],
  ['/consola/auditoria', 'Registro de auditoría'],
]);

/**
 * Deja la sesión en la pestaña como la deja el login (una sola vez por
 * pestaña: si la consola la cierra, no vuelve sola).
 *
 * @param {import('@playwright/test').Page} page
 * @param {{token: string, apodo: string, claims: {rol: string, uid: string}}} sesion
 */
export async function entrarEnLaPagina(page, sesion) {
  await page.addInitScript(
    ([token, apodo, rol, uid]) => {
      if (sessionStorage.getItem('nexus.e2e.inyectada')) {
        return;
      }
      sessionStorage.setItem('nexus.e2e.inyectada', '1');
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', apodo);
      sessionStorage.setItem('nexus.rolActual', rol);
      sessionStorage.setItem('nexus.usuarioId', uid);
    },
    [sesion.token, sesion.apodo, sesion.claims.rol, sesion.claims.uid],
  );
}

/**
 * Recorre las secciones y comprueba cada una. Devuelve los errores de página
 * que hubo, para que la prueba los afirme al final con su contexto.
 *
 * @param {import('@playwright/test').Page} page
 * @param {{base?: string, secciones?: ReadonlyArray<readonly [string, string]>}} [opciones]
 * @returns {Promise<string[]>}
 */
export async function recorrerLaConsola(
  page,
  { base = '', secciones = SECCIONES_DE_LA_CONSOLA } = {},
) {
  const errores = [];
  const anotar = (error) => errores.push(`${page.url()} → ${error.message}`);
  page.on('pageerror', anotar);

  for (const [ruta, titulo] of secciones) {
    await page.goto(`${base}${ruta}`);
    await expect(page.locator('h1').first(), ruta).toHaveText(titulo, { timeout: 30_000 });
    // Que la sección termine de pedir lo suyo antes de leerla. Las que se
    // actualizan solas nunca quedan del todo quietas: no se espera más.
    await page.waitForLoadState('networkidle', { timeout: 4_000 }).catch(() => {});

    expect(new URL(page.url()).pathname, `${ruta} se queda en su dirección limpia`).toBe(ruta);
    const conHtml = await page.$$eval('a[href]', (enlaces) =>
      enlaces
        .map((enlace) => enlace.getAttribute('href') ?? '')
        .filter((href) => /\.html(?:[?#]|$)/.test(href)),
    );
    expect(conHtml, `${ruta}: ningún enlace a un .html`).toEqual([]);
    await expect(page.locator('main'), ruta).not.toContainText(TEXTOS_DE_DESARROLLO);
  }

  page.off('pageerror', anotar);
  return errores;
}
