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

import { existsSync, readFileSync } from 'node:fs';
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
  return hojasSeguidas('frontend/app-web/src/**/*.css');
}

/**
 * Hojas que git sigue Y que siguen en disco. El filtro por disco importa:
 * `git ls-files` sigue listando un archivo borrado hasta que se prepara el
 * borrado, y sin el filtro la prueba revienta con ENOENT en vez de decir lo
 * que de verdad pasa.
 */
function hojasSeguidas(patron) {
  return execFileSync('git', ['ls-files', '--', patron], { cwd: raizRepo, encoding: 'utf8' })
    .split('\n')
    .filter(Boolean)
    .filter((ruta) => existsSync(new URL(ruta, raizRepo)));
}

/** Clases que el kit declara como selector suelto: `.foo {`, `.foo:hover`… */
function clasesDelKit() {
  const kit =
    readFileSync(new URL('shared/ui-kit/css/componentes.css', raizRepo), 'utf8') +
    readFileSync(new URL('shared/ui-kit/css/base.css', raizRepo), 'utf8');
  return new Set([...kit.matchAll(/^\.([a-z0-9_-]+)\s*(?:\{|,|::?[a-z-]+)/gm)].map((m) => m[1]));
}

/** Toda variable que el repositorio declara en algun sitio. */
function variablesDeclaradas() {
  let todas = new Set();
  for (const ruta of hojasSeguidas('*.css')) {
    const css = readFileSync(new URL(ruta, raizRepo), 'utf8');
    todas = new Set([...todas, ...[...css.matchAll(/^\s*(--[a-z0-9-]+)\s*:/gm)].map((m) => m[1])]);
  }
  return todas;
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

describe('los componentes compartidos no se redefinen en las vistas', () => {
  /**
   * Redefinir `.tarjeta` o `.aviso` en una hoja de vista hace que el MISMO
   * componente se vea distinto segun la pantalla. Pasaba en cuatro hojas: la
   * tarjeta tenia radio 8 px en unas vistas y 10 px en otras, y el aviso salia
   * siempre azul aunque el modificador dijera error o exito.
   *
   * No caen aqui, porque extienden el componente en vez de reescribirlo: el
   * selector compuesto (`.tarjeta.fila`), el descendiente (`.fila > label`) y
   * el acotado por atributo (`.distintivo[data-tipo-operacion='lectura']`,
   * que da color a una variante concreta del panel de metricas).
   */
  test('ninguna hoja de vista redefine una clase del kit', () => {
    const delKit = clasesDelKit();
    const encontrados = [];

    for (const ruta of hojasDeVista()) {
      const css = readFileSync(new URL(ruta, raizRepo), 'utf8');
      for (const [, nombre] of css.matchAll(/^\.([a-z0-9_-]+)\s*(?:\{|,|::?[a-z-]+)/gm)) {
        if (delKit.has(nombre)) {
          encontrados.push(`${ruta.split('/').pop()} → .${nombre}`);
        }
      }
    }

    expect(encontrados).toEqual([]);
  });
});

describe('ninguna hoja usa variables que no existen', () => {
  /**
   * `panel-metricas.css` usaba diez variables inexistentes
   * (`--color-info-suave`, `--espacio-3`, `--texto-pequeno`…) y por tanto
   * siempre ganaba el valor de respaldo. Los respaldos eran de una paleta
   * OSCURA dentro de una aplicacion clara: los distintivos salian azul marino
   * sobre blanco, y siempre iban a salir asi.
   *
   * Un respaldo es legitimo —`var(--x, 0)` para una unidad que puede faltar—;
   * lo que no lo es es que el nombre no exista en ninguna parte, porque
   * entonces no es un respaldo sino el unico valor, escondido.
   */
  test('toda var(--x) referenciada esta declarada en alguna hoja', () => {
    const declaradas = variablesDeclaradas();
    const huerfanas = [];

    for (const ruta of hojasDeVista()) {
      const css = readFileSync(new URL(ruta, raizRepo), 'utf8');
      for (const [, nombre] of css.matchAll(/var\(\s*(--[a-z0-9-]+)/g)) {
        if (!declaradas.has(nombre)) {
          huerfanas.push(`${ruta.split('/').pop()} → ${nombre}`);
        }
      }
    }

    expect([...new Set(huerfanas)]).toEqual([]);
  });
});
