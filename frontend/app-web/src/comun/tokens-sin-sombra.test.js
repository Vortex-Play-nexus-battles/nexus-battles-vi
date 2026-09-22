/**
 * Ningún CSS de vista puede redeclarar un token del sistema de diseño.
 *
 * ## El defecto que esta prueba impide que vuelva
 *
 * `shared/ui-kit/css/tokens.css` define la paleta en `:root` y la **vuelve a
 * definir**, con valores de 7:1, dentro de `@media (prefers-contrast: more)`.
 * Ese bloque es la forma en que el producto atiende a quien activa «aumentar
 * contraste» en su sistema operativo.
 *
 * Cuatro hojas de vista copiaban la paleta sobre un selector propio
 * (`.vitrina-pagina`, `.pujas-pagina`…), con un comentario que decía que era
 * porque «estas vistas todavia no enlazan el ui-kit». Eso dejó de ser cierto:
 * las ocho vistas afectadas cargan `tokens.css`. Y como las variables
 * personalizadas se heredan y gana la declaración más cercana, una copia en un
 * descendiente de `:root` **gana siempre** — también sobre el bloque de alto
 * contraste.
 *
 * Resultado medido antes de arreglarlo: 56 declaraciones duplicadas, 43 de
 * ellas sobre variables que el kit redefine en alto contraste. Quien tenía la
 * preferencia activada la recibía en casi toda la aplicación y **no** en
 * inventario, productos, pujas, subastas, tienda, auditoría, historial de
 * transacciones ni mis cofres.
 *
 * Un alias con nombre propio (`--texto-secundario`, `--error-fondo`) no cae
 * aquí: no tapa nada porque el kit no lo define. Son deuda de vocabulario, no
 * un fallo de accesibilidad.
 */

import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const raizRepo = new URL('../../../../', import.meta.url);

/** Nombres de token que declara el sistema de diseño. */
function tokensDelKit() {
  const kit = readFileSync(new URL('shared/ui-kit/css/tokens.css', raizRepo), 'utf8');
  return new Set([...kit.matchAll(/^\s*(--[a-z0-9-]+)\s*:/gm)].map((m) => m[1]));
}

/** Los que además cambian en alto contraste: tapar uno de estos es lo grave. */
function tokensDeAltoContraste() {
  const kit = readFileSync(new URL('shared/ui-kit/css/tokens.css', raizRepo), 'utf8');
  const desde = kit.indexOf('@media (prefers-contrast: more)');
  if (desde < 0) {
    return new Set();
  }
  return new Set([...kit.slice(desde).matchAll(/^\s*(--[a-z0-9-]+)\s*:/gm)].map((m) => m[1]));
}

function hojasDeVista() {
  return execFileSync('git', ['ls-files', '--', 'frontend/app-web/src/**/*.css'], {
    cwd: raizRepo,
    encoding: 'utf8',
  })
    .split('\n')
    .filter(Boolean);
}

describe('los tokens del sistema de diseno no se copian en las vistas', () => {
  test('el kit sigue teniendo su modo de alto contraste', () => {
    // Si alguien lo quita, esta prueba deja de proteger nada y hay que saberlo.
    expect(tokensDeAltoContraste().size).toBeGreaterThan(0);
  });

  test('ninguna hoja de vista redeclara un token del kit', () => {
    const delKit = tokensDelKit();
    const encontrados = [];

    for (const ruta of hojasDeVista()) {
      const css = readFileSync(new URL(ruta, raizRepo), 'utf8');
      for (const [, nombre] of css.matchAll(/^\s*(--[a-z0-9-]+)\s*:/gm)) {
        if (delKit.has(nombre)) {
          encontrados.push(`${ruta.split('/').pop()} → ${nombre}`);
        }
      }
    }

    expect(encontrados).toEqual([]);
  });
});
