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
 * Lo que abre cada rol, según 7.3.1 y la matriz de permisos del documento
 * (Tabla 24): el Moderador modera, advierte y suspende; el Administrador
 * además gestiona productos, usuarios y configuración; solo el Super
 * Administrador crea cuentas administrativas y lee la auditoría (RF-RBAC-003,
 * RF-AUD-001).
 *
 * Se escribe aquí y no se importa de la matriz de la interfaz
 * (`comun/matriz-acceso.js`): si la interfaz se equivoca, esta lista no se
 * equivoca con ella.
 */
const DE_MODERACION = Object.freeze([
  '/consola',
  '/consola/comentarios',
  '/consola/sanciones',
  '/consola/lista-negra',
]);
const SOLO_DEL_SUPER_ADMINISTRADOR = Object.freeze([
  '/consola/usuarios/nueva-cuenta',
  '/consola/auditoria',
]);

/**
 * Las secciones de la consola que un rol abre.
 *
 * @param {string} rol
 * @returns {ReadonlyArray<readonly [string, string]>}
 */
export function seccionesDeRol(rol) {
  return SECCIONES_DE_LA_CONSOLA.filter(([ruta]) => {
    switch (rol) {
      case 'SUPER_ADMINISTRADOR':
        return true;
      case 'ADMINISTRADOR':
        return !SOLO_DEL_SUPER_ADMINISTRADOR.includes(ruta);
      case 'MODERADOR':
        return DE_MODERACION.includes(ruta);
      default:
        return false;
    }
  });
}

/**
 * Las que no abre.
 *
 * @param {string} rol
 * @returns {ReadonlyArray<readonly [string, string]>}
 */
export function seccionesVetadasA(rol) {
  const abiertas = new Set(seccionesDeRol(rol).map(([ruta]) => ruta));
  return SECCIONES_DE_LA_CONSOLA.filter(([ruta]) => !abiertas.has(ruta));
}

/**
 * Las secciones que ofrece la barra de la consola, por su ruta.
 *
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<string[]>}
 */
export async function seccionesDeLaBarra(page) {
  const barra = page.locator('nav[aria-label="Herramientas de operación"]');
  await expect(barra).toBeAttached({ timeout: 30_000 });
  const enlaces = await barra
    .locator('a[href]')
    .evaluateAll((lista) => lista.map((enlace) => enlace.getAttribute('href') ?? ''));
  return enlaces.map((href) => new URL(href, page.url()).pathname);
}

/**
 * Cada sección vetada se explica con palabras y no enseña nada de la
 * herramienta (RF-INV-008, «no rebota»): la página queda marcada como
 * denegada, dice «No tienes acceso a esta sección.», ofrece volver al inicio
 * y no pinta ni la barra de la consola ni una sola tabla.
 *
 * @param {import('@playwright/test').Page} page
 * @param {ReadonlyArray<readonly [string, string]>} secciones
 * @param {{base?: string}} [opciones]
 */
export async function comprobarSinAcceso(page, secciones, { base = '' } = {}) {
  for (const [ruta] of secciones) {
    await page.goto(`${base}${ruta}`);
    const aviso = page.locator('[data-zona="sin-permiso"]');
    await expect(aviso, ruta).toBeVisible({ timeout: 30_000 });
    await expect(aviso, ruta).toContainText('No tienes acceso a esta sección.');
    await expect(page.locator('html'), ruta).toHaveAttribute('data-acceso', 'denegado');
    await expect(aviso.getByRole('link', { name: 'Volver a mi inicio' }), ruta).toBeVisible();
    await expect(page.locator('nav[aria-label="Herramientas de operación"]'), ruta).toHaveCount(0);
    await expect(page.locator('main table'), ruta).toHaveCount(0);
    expect(new URL(page.url()).pathname, `${ruta} no rebota a otra página`).toBe(ruta);
  }
}

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
