/**
 * Guardián: ninguna vista escribe una clase que nadie define — UX-R2.4.
 *
 * ## Por qué hacía falta
 *
 * Una clase que no existe no falla, no avisa y no se ve en una revisión de
 * código: simplemente no hace nada, y la vista se queda con el estilo por
 * defecto del navegador. El laboratorio visual (#600) lo detecta solo cuando
 * la consecuencia es medible —algo que desborda, algo que se corta—, y muchas
 * veces no lo es: es un bloque sin ancho máximo, o un botón sin aspecto de
 * botón, que se ve raro pero no roto.
 *
 * Al escribir esta prueba había **cinco** en el repositorio:
 *
 * | Clase | Vista | Consecuencia |
 * |---|---|---|
 * | `.encabezado-contenido` | historial-transacciones, mis-cofres | la descripción cruzaba la pantalla entera |
 * | `.contenido` | panel-metricas | el `<main>` de la vista sin ninguna clase de maquetación |
 * | `.dialogo-avatar-cerrar` | registro | la ✕ del diálogo sin aspecto de botón |
 * | `.empty-cart-msg` | tienda | el mensaje de carrito vacío con el estilo por defecto |
 * | `.campo--linea` | sanciones-admin | modificador inventado sobre `.campo` |
 *
 * Ninguna se veía en una captura como un fallo evidente. Todas eran deuda.
 *
 * ## Qué cuenta como «definida»
 *
 * Que aparezca como selector en el kit (`tokens`, `base`, `componentes`) o en
 * un CSS propio de la vista. No se exige que la clase esté en el kit: una
 * vista puede tener estilos propios. Lo que no puede es referirse a algo que
 * no está en ninguna parte.
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const SRC = join(AQUI, '..', '..');
const KIT = join(SRC, '..', '..', '..', 'shared', 'ui-kit', 'css');

/** Todos los archivos con una extensión, recorriendo subcarpetas. */
function archivos(raiz, extension) {
  const salida = [];
  for (const entrada of readdirSync(raiz)) {
    const completa = join(raiz, entrada);
    if (statSync(completa).isDirectory()) {
      salida.push(...archivos(completa, extension));
    } else if (entrada.endsWith(extension)) {
      salida.push(completa);
    }
  }
  return salida;
}

/** Los nombres de clase que aparecen como selector en un CSS. */
function clasesDefinidasEn(rutas) {
  const definidas = new Set();
  for (const ruta of rutas) {
    for (const [, clase] of readFileSync(ruta, 'utf8').matchAll(/\.([a-z][a-z0-9_-]*)/g)) {
      definidas.add(clase);
    }
  }
  return definidas;
}

/** Las clases que escribe cada vista, con la vista donde aparecen. */
function clasesUsadas() {
  const usadas = new Map();
  for (const ruta of archivos(SRC, '.html')) {
    const html = readFileSync(ruta, 'utf8');
    for (const [, lista] of html.matchAll(/class="([^"]+)"/g)) {
      // Se salta lo que no es una clase literal: una plantilla `${...}` no se
      // puede comprobar aquí sin ejecutar la vista.
      if (lista.includes('${') || lista.includes('{{')) {
        continue;
      }
      for (const clase of lista.split(/\s+/).filter(Boolean)) {
        if (!usadas.has(clase)) {
          usadas.set(clase, new Set());
        }
        usadas.get(clase).add(ruta.slice(SRC.length + 1).replace(/\\/g, '/'));
      }
    }
  }
  return usadas;
}

test('ninguna vista usa una clase que no define ni el kit ni su propio CSS', () => {
  const definidas = clasesDefinidasEn([
    ...['tokens.css', 'base.css', 'componentes.css'].map((f) => join(KIT, f)),
    ...archivos(SRC, '.css'),
  ]);

  const fantasma = [...clasesUsadas()]
    .filter(([clase]) => !definidas.has(clase))
    .map(([clase, vistas]) => `${clase} (en ${[...vistas].join(', ')})`)
    .sort();

  // El mensaje va en el propio valor: si falla, el informe de Jest enseña la
  // lista con la clase y la vista donde está, que es lo que hace falta para
  // arreglarlo sin buscar.
  expect(fantasma).toEqual([]);
});

test('el kit define las clases de maquetación que las vistas dan por hechas', () => {
  // Las que sostienen la estructura de una página. Si alguien las borra del
  // kit, media aplicación se queda sin maquetación y este guardián lo dice
  // antes que una captura.
  const definidas = clasesDefinidasEn(
    ['tokens.css', 'base.css', 'componentes.css'].map((f) => join(KIT, f)),
  );

  for (const clase of [
    'pagina',
    'pila',
    'fila',
    'acciones',
    'encabezado-pagina',
    'encabezado-pagina__acciones',
    'encabezado-pagina__contenido',
    'encabezado-seccion',
    'tarjeta',
    'campo',
    'boton',
    'aviso',
    'estado-vista',
  ]) {
    // Se compara contra el nombre para que el fallo diga cuál falta.
    expect(definidas.has(clase) ? clase : `FALTA .${clase} en el kit`).toBe(clase);
  }
});

test('una tarjeta-enlace no se subraya como un enlace de texto', () => {
  // `base.css` subraya todo `<a>`. Una tarjeta que además es enlace tiene que
  // apagarlo, o el título y la descripción salen subrayados dentro de la caja
  // —que es como se veía la home hasta UX-R2.6—. `color: inherit` ya estaba;
  // el subrayado, no.
  const componentes = readFileSync(join(KIT, 'componentes.css'), 'utf8');
  const bloque = componentes.slice(
    componentes.indexOf('.tarjeta--pulsable {'),
    componentes.indexOf('.tarjeta--pulsable:hover'),
  );

  expect(bloque).toContain('text-decoration: none');
  expect(bloque).toContain('color: inherit');
});

test('ninguna ficha de sombra se usa como color de contorno (UX-R2.8d)', () => {
  // Defecto real: siete reglas del kit decian
  //   `outline: var(--foco-grosor) solid var(--anillo-foco)`
  // y `--anillo-foco` vale `0 0 0 3px rgba(34,68,191,.35)` — una sombra, no un
  // color. El `outline` resultante es invalido y el navegador lo descarta, asi
  // que el foco NO se dibujaba en casillas, radios, desplegables, estrellas,
  // ranuras, acciones de combate, zona de carga ni selector de objeto.
  // RNF-ACC-002 exige que el foco sea siempre visible.
  const hojas = ['base.css', 'componentes.css'].map((f) => readFileSync(join(KIT, f), 'utf8'));
  const tokens = readFileSync(join(KIT, 'tokens.css'), 'utf8');

  // Que sigue siendo una sombra: si algun dia pasa a ser un color, esta prueba
  // avisa de que la regla de abajo ya no hace falta.
  expect(tokens).toMatch(/--anillo-foco:\s*0 0 0/);

  const culpables = [];
  for (const hoja of hojas) {
    // Sin los comentarios: la nota que explica este defecto cita la regla
    // mala textualmente, y si no se quitan el guardian se denuncia a si mismo.
    const sinComentarios = hoja.replace(/\/\*[\s\S]*?\*\//g, (bloque) =>
      bloque.replace(/[^\n]/g, ' '),
    );
    sinComentarios.split('\n').forEach((linea, i) => {
      if (/\boutline\s*:[^;]*var\(--anillo-foco\)/.test(linea)) {
        culpables.push(`${i + 1}: ${linea.trim()}`);
      }
    });
  }
  expect(culpables).toEqual([]);
});

/**
 * El kit se carga en su orden — UX-R3.6.
 *
 * `base.css` lo dice en su primera línea: «Cargar siempre en este orden:
 * tokens.css -> base.css -> componentes.css -> css de la vista». No es una
 * recomendación de estilo: `base.css` declara color sobre `h1`, el ancho de
 * `.pagina` y `[hidden] { display: none !important }`, y en una hoja cargada
 * después le gana por orden a cualquier componente con la misma
 * especificidad.
 *
 * `subastas.html` cargaba `componentes.css` primero. El síntoma era el título
 * de la página montado encima de la barra de navegación — un fallo que se ve
 * en la captura y que leyendo el CSS del título no aparece por ninguna parte.
 * Una de treinta y dos; por eso esto es una prueba y no una revisión.
 */
test('toda vista carga el kit en el orden que el propio kit exige', () => {
  const ORDEN = ['tokens.css', 'base.css', 'componentes.css'];
  const fallos = [];

  for (const ruta of archivos(SRC, '.html')) {
    const vista = ruta.slice(SRC.length + 1).replaceAll('\\', '/');
    const marcado = readFileSync(ruta, 'utf8');
    const cargadas = [...marcado.matchAll(/ui-kit\/css\/(\w+\.css)/g)].map((m) => m[1]);
    if (cargadas.length === 0) {
      fallos.push(`${vista}: no carga el kit`);
      continue;
    }
    const esperado = ORDEN.filter((hoja) => cargadas.includes(hoja));
    if (cargadas.join() !== esperado.join()) {
      fallos.push(`${vista}: ${cargadas.join(' → ')}`);
    }
  }

  expect(fallos).toEqual([]);
});
