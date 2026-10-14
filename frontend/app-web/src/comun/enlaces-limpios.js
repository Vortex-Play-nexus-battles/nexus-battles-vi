/**
 * Ningún enlace de la aplicación enseña la carpeta ni el `.html` de una vista
 * (R17; SA-7, informe de revisión del Super Administrador, punto crítico
 * externo: «el link de la navegación aparece aún como ruta de carpetas… y
 * salen los .html»).
 *
 * Las vistas escriben sus enlaces de dos maneras. Unas pasan por `resolver()`
 * o `urlDeVista()`, que ya devuelven la dirección limpia. Otras la escriben a
 * mano con la ruta del fichero (`./crear-cuenta-admin.html`,
 * `../plataforma/moderacion-sanciones/ficha-usuario.html?usuario=…`). Esas
 * funcionan —el borde redirige la ruta antigua a la limpia—, pero la carpeta
 * se ve al pasar el ratón, al copiar el enlace o al abrirlo en otra pestaña.
 *
 * Este módulo las traduce en el DOM: un enlace que apunta al fichero de una
 * vista con dirección limpia pasa a apuntar a esa dirección, con su consulta y
 * su ancla intactas. Lo hace una vez al montar el armazón y después con cada
 * enlace que la vista pinte o cambie. No toca nada más: un enlace externo, un
 * ancla de la misma página o un recurso que no es una vista quedan como están.
 *
 * Solo actúa detrás del borde, que es quien sirve las direcciones limpias
 * (`hayRutasLimpias`); en `npm run dev` o en el laboratorio visual los
 * enlaces siguen siendo los de siempre, que es lo único que ahí existe.
 *
 * @module comun/enlaces-limpios
 */

import { MATRIZ } from './matriz-acceso.js';
import { hayRutasLimpias } from './sesion.js';

/**
 * Camino absoluto del fichero de cada vista → su dirección limpia.
 *
 * Se calcula contra la URL de este módulo, que vive en `src/comun/`, igual
 * que `rutaDeVista`: así vale tanto detrás del borde
 * (`/frontend/app-web/src/cuentas/login.html`) como con otro prefijo.
 *
 * @param {string} [base] URL de un módulo de `src/comun/`
 * @returns {Map<string, string>}
 */
export function limpiasPorCamino(base = import.meta.url) {
  return new Map(
    Object.values(MATRIZ)
      .filter((entrada) => entrada.limpia)
      .map((entrada) => [new URL(`../${entrada.ruta}`, base).pathname, entrada.limpia]),
  );
}

/**
 * La dirección limpia de una URL absoluta, o `null` si no es la de una vista
 * que la tenga (otro origen, otro fichero, una API…).
 *
 * @param {string} href URL absoluta, como la devuelve `enlace.href`
 * @param {Map<string, string>} limpias de `limpiasPorCamino`
 * @param {string} origen `location.origin` de la página
 * @returns {string|null}
 */
export function direccionLimpia(href, limpias, origen) {
  let url;
  try {
    url = new URL(href);
  } catch {
    return null;
  }
  if (url.origin !== origen) {
    return null;
  }
  const limpia = limpias.get(url.pathname);
  if (!limpia) {
    return null;
  }
  url.pathname = limpia;
  return url.href;
}

/**
 * Traduce los enlaces de `raiz` (y la propia raíz si es un enlace).
 *
 * @param {ParentNode|Element} raiz
 * @param {Map<string, string>} limpias
 * @param {string} origen
 * @returns {number} cuántos cambió
 */
export function limpiarEnlaces(raiz, limpias, origen) {
  const enlaces = [];
  if (raiz?.matches?.('a[href], area[href]')) {
    enlaces.push(raiz);
  }
  if (typeof raiz?.querySelectorAll === 'function') {
    enlaces.push(...raiz.querySelectorAll('a[href], area[href]'));
  }
  let cambiados = 0;
  for (const enlace of enlaces) {
    const limpia = direccionLimpia(enlace.href, limpias, origen);
    if (limpia && limpia !== enlace.href) {
      enlace.setAttribute('href', limpia);
      cambiados += 1;
    }
  }
  return cambiados;
}

/** Documentos que ya se están vigilando: montar el armazón dos veces no duplica. */
const vigilados = new WeakSet();

/**
 * Traduce los enlaces que ya hay y los que se pinten o cambien después.
 *
 * Es idempotente y no hace nada si el borde no sirve direcciones limpias. Un
 * enlace ya traducido no vuelve a cambiar: su dirección deja de ser la de un
 * fichero, así que el observador no entra en bucle.
 *
 * @param {object} [opciones]
 * @param {Document} [opciones.documento]
 * @param {string} [opciones.base] URL de un módulo de `src/comun/` (pruebas)
 * @returns {() => void} deja de vigilar
 */
export function vigilarEnlaces({ documento = globalThis.document, base = import.meta.url } = {}) {
  const raiz = documento?.documentElement;
  if (!raiz || vigilados.has(documento) || !hayRutasLimpias(documento)) {
    return () => {};
  }
  const vista = documento.defaultView;
  let origen;
  try {
    origen = vista?.location?.origin ?? new URL(documento.URL).origin;
  } catch {
    return () => {};
  }
  const limpias = limpiasPorCamino(base);
  limpiarEnlaces(raiz, limpias, origen);

  const Observador = vista?.MutationObserver ?? globalThis.MutationObserver;
  if (typeof Observador !== 'function') {
    vigilados.add(documento);
    return () => {};
  }
  const observador = new Observador((cambios) => {
    for (const cambio of cambios) {
      if (cambio.type === 'attributes') {
        limpiarEnlaces(cambio.target, limpias, origen);
        continue;
      }
      for (const nodo of cambio.addedNodes) {
        if (nodo.nodeType === 1) {
          limpiarEnlaces(nodo, limpias, origen);
        }
      }
    }
  });
  observador.observe(raiz, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['href'],
  });
  vigilados.add(documento);
  return () => {
    observador.disconnect();
    vigilados.delete(documento);
  };
}
