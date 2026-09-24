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

/**
 * Modulos de produccion: los `.js` que git sigue bajo `src/`, sin las pruebas
 * ni los bancos de datos de prueba, que fabrican colores a proposito.
 */
function modulosDeProduccion() {
  return hojasSeguidas('frontend/app-web/src/**/*.js')
    .filter((ruta) => !/\.(test|spec|aceptacion)\.[cm]?js$/.test(ruta))
    .map((ruta) => new URL(ruta, raizRepo));
}

/** La ruta como se lee en el informe: desde `src/`, sin el prefijo del repo. */
function relativa(url) {
  return url.pathname.split('/app-web/src/').pop();
}

/**
 * El CSS sin sus comentarios.
 *
 * Los trinquetes de este fichero cuentan lo que la hoja DECLARA o USA, y un
 * comentario no hace ninguna de las dos cosas. Importa porque la costumbre de
 * la casa es dejar escrito que se quito —«aqui habia `#0B6B31`», «pedia
 * `var(--texto-1)`, que no existe»—: sin descontar comentarios, documentar una
 * correccion la hace fallar, y la salida es dejar de documentarla.
 */
function sinComentarios(css) {
  return css.replace(/\/\*[\s\S]*?\*\//g, ' ');
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

describe('ninguna hoja de vista escribe un color a mano', () => {
  /**
   * UX-R4.1 — el trinquete que faltaba.
   *
   * Los dos de arriba impiden que una hoja de vista **tape** algo del kit: un
   * token redeclarado, una clase redefinida. Lo que no miraban es lo contrario:
   * una hoja que se inventa su propio color sin tapar nada.
   *
   * Y eso era lo que quedaba. Medido al cerrar UX-R3: **94 literales de color
   * en nueve hojas**, con los comentarios descontados. `pujas.css` tenia 59, en
   * 21 colores distintos, entre ellos las insignias de rareza —que el kit
   * define desde el principio— y cuatro sombras escritas a mano cuando hay
   * cuatro fichas de elevacion (`--sombra-1..4`). `historial-transacciones.css`
   * traia una cuarta paleta paralela: `#086b31` donde el kit dice `--exito`.
   *
   * Un hexadecimal escrito a mano no responde a `prefers-contrast: more`, y
   * ademas hace que el mismo estado se vea de dos maneras segun la pantalla.
   *
   * ## Que se admite
   *
   * - `transparent` y `currentColor`, que no son colores sino relaciones.
   * - `color-mix(in srgb, var(--x) N%, …)`: es la forma que tiene el
   *   repositorio de sacar un tinte o un halo de un color que YA existe sin
   *   anadir otro a la paleta.
   * - Los comentarios, para poder dejar escrito que hexadecimal se quito.
   *
   * `shared/ui-kit/css/tokens.css` no cae aqui: es el fichero cuyo trabajo es
   * escribir los colores. Los demas los consumen.
   */
  test('ninguna hoja de vista escribe un hexadecimal ni un rgb()', () => {
    const literal = /#[0-9a-fA-F]{3,8}\b|\brgba?\(\s*[0-9]|\bhsla?\(\s*[0-9]/g;
    const encontrados = [];

    for (const ruta of hojasDeVista()) {
      const css = readFileSync(new URL(ruta, raizRepo), 'utf8').replace(/\/\*[\s\S]*?\*\//g, ' ');
      for (const hallazgo of css.match(literal) ?? []) {
        encontrados.push(`${ruta.split('/').pop()} → ${hallazgo}`);
      }
    }

    expect(encontrados).toEqual([]);
  });

  /**
   * UX-R4.2 — el agujero que dejo el trinquete de arriba.
   *
   * Aquel contaba literales en hojas de estilo, y por eso dio cero al cerrar
   * R4.1 teniendo el producto una quinta paleta paralela delante: estaba en
   * JavaScript. `pujas.js` declaraba
   *
   *     comun: { fondo: '#E7EAF0', texto: '#57627A', borde: '#9FABC9' }
   *
   * para las cuatro rarezas, con los mismos valores que `--rareza-*` del kit,
   * y los inyectaba en la ficha como `style` en linea. Doce hexadecimales que
   * ninguna hoja contenia.
   *
   * Un color escrito en un modulo es peor que uno escrito en una hoja: ademas
   * de duplicar, llega al DOM como atributo `style`, donde ya no lo alcanza
   * ningun `@media` ni ninguna clase. Deja de poder corregirse desde el kit.
   *
   * Se admite el color en los comentarios —hace falta para dejar escrito cual
   * se quito— y en las pruebas, que fabrican datos a proposito.
   */
  test('ningun modulo de produccion escribe un color a mano', () => {
    const literal = /#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?\b|\brgba?\(\s*[0-9]|\bhsla?\(\s*[0-9]/g;
    const encontrados = [];

    for (const ruta of modulosDeProduccion()) {
      const js = readFileSync(ruta, 'utf8')
        .replace(/\/\*[\s\S]*?\*\//g, ' ')
        .replace(/^\s*\/\/.*$/gm, ' ');
      for (const hallazgo of js.match(literal) ?? []) {
        encontrados.push(`${relativa(ruta)} → ${hallazgo}`);
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
      const css = sinComentarios(readFileSync(new URL(ruta, raizRepo), 'utf8'));
      for (const [, nombre] of css.matchAll(/var\(\s*(--[a-z0-9-]+)/g)) {
        if (!declaradas.has(nombre)) {
          huerfanas.push(`${ruta.split('/').pop()} → ${nombre}`);
        }
      }
    }

    expect([...new Set(huerfanas)]).toEqual([]);
  });

  /**
   * UX-R2.10 — «declarada en alguna hoja» tenia un agujero.
   *
   * `auditoria.css` usaba `var(--texto-secundario)` y `var(--exito-fondo)`.
   * La prueba de arriba pasaba porque esos nombres SI estaban declarados…
   * en `tema-cuentas.css`, sobre un selector propio que `auditoria.html` no
   * lleva. Una variable declarada en otro sitio no llega: `background:
   * var(--exito-fondo)` sin respaldo es una declaracion invalida, el
   * navegador la descarta y la insignia sale **sin fondo**.
   *
   * Eran 40 usos en seis hojas, y todos apuntaban a un alias con el hex
   * escrito a mano — la misma familia de defecto que PR-UX-5: un hex no
   * responde a `prefers-contrast: more`, la ficha del kit si.
   *
   * La regla, ahora: una variable sin respaldo tiene que estar en el KIT o
   * en la MISMA hoja. Lo de «en alguna parte del repositorio» no basta.
   */
  test('una var(--x) sin respaldo resuelve en el kit o en su propia hoja', () => {
    const delKit = new Set([...tokensDelKit(), ...variablesDelKitCompleto()]);
    const fuera = [];

    for (const ruta of hojasDeVista()) {
      const css = sinComentarios(readFileSync(new URL(ruta, raizRepo), 'utf8'));
      const propias = new Set([...css.matchAll(/^\s*(--[a-z0-9-]+)\s*:/gm)].map((m) => m[1]));
      for (const [, nombre, coma] of css.matchAll(/var\(\s*(--[a-z0-9-]+)\s*(,?)/g)) {
        // Con respaldo es una decision: `var(--x, 24px)` funciona sin `--x`.
        if (coma === ',' || delKit.has(nombre) || propias.has(nombre)) {
          continue;
        }
        fuera.push(`${ruta.split('/').pop()} → ${nombre}`);
      }
    }

    expect([...new Set(fuera)]).toEqual([]);
  });
});

/** Todo lo que declaran las tres hojas del kit, tokens y medidas. */
function variablesDelKitCompleto() {
  const kit = ['tokens.css', 'base.css', 'componentes.css']
    .map((f) => readFileSync(new URL(`shared/ui-kit/css/${f}`, raizRepo), 'utf8'))
    .join('\n');
  return new Set([...kit.matchAll(/^\s*(--[a-z0-9-]+)\s*:/gm)].map((m) => m[1]));
}
