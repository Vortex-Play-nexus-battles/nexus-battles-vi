/**
 * Catálogo de fondos ilustrados — HU-UX-002 (#925).
 *
 * Una vista del jugador elige su escena con `data-fondo` en el `<body>` y
 * enlaza `shared/ui-kit/css/fondos.css`. Esta prueba cuida lo que esa
 * convención puede romper sin que nadie lo vea en un navegador:
 *
 * - un valor de `data-fondo` que el catálogo no declara (la vista se queda
 *   con el velo y sin imagen, y nadie sabe por qué);
 * - una imagen que el catálogo nombra y no está en el repositorio (un 404
 *   en cada visita, y en producción el borde lo sirve tal cual);
 * - el atributo sin la hoja, o la hoja sin el atributo (lo primero no pinta
 *   nada; lo segundo es una descarga inútil);
 * - un fondo en la consola de administración, que por decisión del PO se
 *   queda sin él.
 *
 * El contraste del texto sobre cada escena no se puede medir aquí (hace
 * falta pintar la página): lo mide `tests/visual/contraste-fondos.spec.js`.
 */

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';

import { MATRIZ } from './matriz-acceso.js';

const raizRepo = new URL('../../../../', import.meta.url);
const raizVistas = new URL('frontend/app-web/src/', raizRepo);
const urlHoja = new URL('shared/ui-kit/css/fondos.css', raizRepo);

const leer = (url) => readFileSync(url, 'utf8');

/** `data-fondo` → URLs (escritorio y móvil) que le asigna el catálogo. */
function catalogo() {
  const mapa = new Map();
  const reglas = leer(urlHoja).matchAll(
    /body\[data-fondo='([a-z-]+)'\]\s*\{\s*--fondo-imagen:\s*url\('([^']+)'\);?\s*\}/g,
  );
  for (const [, valor, url] of reglas) {
    if (!mapa.has(valor)) {
      mapa.set(valor, []);
    }
    mapa.get(valor).push(url);
  }
  return mapa;
}

/** Vistas HTML que git sigue bajo `src/`, como rutas relativas a `src/`. */
function vistasSeguidas() {
  return execFileSync('git', ['ls-files', '--', 'frontend/app-web/src/**/*.html'], {
    cwd: raizRepo,
    encoding: 'utf8',
  })
    .split('\n')
    .filter(Boolean)
    .filter((ruta) => existsSync(new URL(ruta, raizRepo)))
    .map((ruta) => ruta.replace('frontend/app-web/src/', ''));
}

/** Lo que una vista declara: su escena y si enlaza la hoja (y dónde). */
function declaracion(ruta) {
  const url = new URL(ruta, raizVistas);
  const html = leer(url);
  const cuerpo = html.match(/<body\b[^>]*>/)?.[0] ?? '';
  const fondo = cuerpo.match(/\sdata-fondo="([^"]*)"/)?.[1] ?? null;
  const hojas = [...html.matchAll(/<link rel="stylesheet" href="([^"]+)"/g)].map(
    ([, href]) => new URL(href, url).href,
  );
  return {
    fondo,
    enlazaHoja: hojas.includes(urlHoja.href),
    posicionHoja: hojas.indexOf(urlHoja.href),
    posicionKit: hojas.indexOf(new URL('shared/ui-kit/css/componentes.css', raizRepo).href),
  };
}

describe('catálogo de fondos ilustrados', () => {
  test('cada escena tiene su versión de escritorio y la móvil, y las dos existen', () => {
    const escenas = catalogo();
    expect(escenas.size).toBeGreaterThan(0);
    const problemas = [];
    for (const [valor, urls] of escenas) {
      const escritorio = urls.filter((u) => !u.endsWith('-movil.webp'));
      const movil = urls.filter((u) => u.endsWith('-movil.webp'));
      if (escritorio.length !== 1 || movil.length !== 1) {
        problemas.push(`${valor}: ${escritorio.length} de escritorio y ${movil.length} móviles`);
      }
    }
    expect(problemas).toEqual([]);
  });

  test('toda imagen que nombra fondos.css está en el repositorio', () => {
    const faltan = [...leer(urlHoja).matchAll(/url\('([^']+)'\)/g)]
      .map(([, relativa]) => new URL(relativa, urlHoja))
      .filter((url) => !existsSync(url))
      .map((url) => url.pathname.split('/shared/').pop());
    expect(faltan).toEqual([]);
  });

  test('el README del catálogo nombra todas las escenas', () => {
    const readme = leer(new URL('shared/ui-kit/fondos/README.md', raizRepo));
    const sinDocumentar = [...catalogo().keys()].filter((v) => !readme.includes(`\`${v}\``));
    expect(sinDocumentar).toEqual([]);
  });
});

describe('vistas con fondo', () => {
  test('toda vista con data-fondo usa una escena del catálogo y enlaza la hoja tras el kit', () => {
    const escenas = catalogo();
    const problemas = [];
    for (const ruta of vistasSeguidas()) {
      const { fondo, enlazaHoja, posicionHoja, posicionKit } = declaracion(ruta);
      if (fondo === null) {
        if (enlazaHoja) {
          problemas.push(`${ruta}: enlaza fondos.css sin data-fondo`);
        }
        continue;
      }
      if (!escenas.has(fondo)) {
        problemas.push(`${ruta}: data-fondo="${fondo}" no está en el catálogo`);
      }
      if (!enlazaHoja) {
        problemas.push(`${ruta}: tiene data-fondo y no enlaza shared/ui-kit/css/fondos.css`);
      } else if (posicionKit < 0 || posicionHoja < posicionKit) {
        problemas.push(`${ruta}: fondos.css tiene que ir después de componentes.css`);
      }
    }
    expect(problemas).toEqual([]);
  });

  test('la consola de administración no lleva fondo', () => {
    const conFondo = Object.entries(MATRIZ)
      .filter(([, entrada]) => entrada.armazon === 'admin')
      .filter(([, entrada]) => {
        const { fondo, enlazaHoja } = declaracion(entrada.ruta);
        return fondo !== null || enlazaHoja;
      })
      .map(([id]) => id);
    expect(conFondo).toEqual([]);
  });
});
