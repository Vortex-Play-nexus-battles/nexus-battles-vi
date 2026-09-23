/**
 * Entorno de las pruebas de aceptacion del inventario (issue #581).
 *
 * Estas pruebas simulan la red, no la identidad ni el sistema de diseno. Aqui
 * se reponen las dos piezas que la pagina da por sentadas y que el servidor
 * estatico de `playwright.config.js` no puede ofrecer.
 *
 * ## 1. La sesion
 *
 * Desde S1.4 (#451) el servicio resuelve el propietario a partir del JWT, y
 * desde R3 (#566) las vistas privadas llaman a `exigirSesion()`. Sin sesion en
 * `sessionStorage` la pagina se va al login y la vitrina no llega a pintarse:
 * cada `locator` esperaba hasta agotar el tiempo, y por eso fallaban 45 de las
 * 46 pruebas de este modulo.
 *
 * El token no va firmado a proposito: en el navegador `cuerpoDelToken` solo
 * descodifica el cuerpo y `leerSesion` unicamente mira `exp`. Verificar la
 * firma es trabajo del servidor, que aqui esta simulado con `page.route`.
 *
 * ## 2. El sistema de diseno
 *
 * `inventario.html` pide las hojas del kit con `../../../../../shared/`, que
 * resuelve contra la raiz del repositorio. El borde sirve el repositorio
 * completo y ahi funciona, pero el servidor de estas pruebas sirve solo `src`,
 * asi que esas tres hojas quedan fuera de su alcance y responden 404.
 *
 * Eso importa desde el PR #612, que saco los alias de paleta de `vitrina.css`
 * y los dejo unicamente en `tokens.css`. Sin esa hoja, `var(--borde)`,
 * `var(--superficie)` y `var(--sombra-2)` no resuelven; y un `var()` sin
 * resolver **invalida la declaracion completa**, no solo el color. La tarjeta
 * se quedaba sin borde, sin fondo y sin sombra, y los criterios de HU-INV-013
 * —que se verifican sobre el estilo calculado— no podian cumplirse.
 *
 * Se sirven los archivos reales desde disco, no una copia: si el kit cambia
 * sus tokens, estas pruebas lo notan.
 */
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = path.dirname(fileURLToPath(import.meta.url));

/** `shared/ui-kit/css/` en la raiz del repositorio, la misma que pide el HTML. */
const KIT = path.resolve(AQUI, '../../../../../shared/ui-kit/css');

/** Apodo por omision; es el que ya usaban los specs. */
export const JUGADOR_DE_PRUEBA = 'jugador-de-prueba';

/** UUID estable del jugador; es el `uid` que el servicio leeria del JWT. */
export const UID_DE_PRUEBA = '11111111-1111-1111-1111-111111111111';

/**
 * Deja en `sessionStorage` lo que habria dejado el login, antes de que la
 * pagina ejecute su primer script.
 *
 * @param {import('@playwright/test').Page} page
 * @param {{apodo?: string, uid?: string, rol?: string}} [opciones]
 */
export async function sembrarSesion(
  page,
  { apodo = JUGADOR_DE_PRUEBA, uid = UID_DE_PRUEBA, rol = 'JUGADOR' } = {},
) {
  await page.addInitScript(
    ([apodoSesion, uidSesion, rolSesion]) => {
      const base64url = (objeto) =>
        btoa(JSON.stringify(objeto)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

      // `exp` en 2100: la sesion no caduca a mitad de una prueba.
      const cuerpo = base64url({
        sub: apodoSesion,
        uid: uidSesion,
        rol: rolSesion,
        exp: 4102444800,
      });

      sessionStorage.setItem('nexus.token', `encabezado.${cuerpo}.firma`);
      sessionStorage.setItem('nexus.apodoActual', apodoSesion);
      sessionStorage.setItem('nexus.rolActual', rolSesion);
      sessionStorage.setItem('nexus.usuarioId', uidSesion);
    },
    [apodo, uid, rol],
  );
}

/**
 * Responde las hojas del sistema de diseno con los archivos reales del
 * repositorio, que quedan fuera de la raiz del servidor de pruebas.
 *
 * @param {import('@playwright/test').Page} page
 */
export async function servirKitDeDiseno(page) {
  await page.route('**/shared/ui-kit/css/*.css', async (ruta) => {
    const hoja = path.basename(new URL(ruta.request().url()).pathname);
    await ruta.fulfill({ path: path.join(KIT, hoja), contentType: 'text/css' });
  });
}

/**
 * Todo lo que una vista privada del inventario necesita para pintarse igual
 * que en el navegador de un jugador: sesion y sistema de diseno.
 *
 * @param {import('@playwright/test').Page} page
 * @param {{apodo?: string, uid?: string, rol?: string}} [opciones]
 */
export async function prepararPagina(page, opciones) {
  await servirKitDeDiseno(page);
  await sembrarSesion(page, opciones);
}
