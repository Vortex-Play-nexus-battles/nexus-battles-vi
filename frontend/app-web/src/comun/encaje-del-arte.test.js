/**
 * Encaje del arte de la fase 2 — seguimiento de #1006.
 *
 * #1006 colgó el arte aprobado (héroes, misiones y productos) en ranuras que
 * estaban pensadas para un icono, y el CSS de esas ranuras no cambió. En DEV
 * se vio así:
 *
 * - misiones: la escena (16:9) tomaba su alto natural dentro de la rejilla de
 *   centrado, se salía de la franja de 120 px y tapaba el título;
 * - tienda y ficha: el arte es cuadrado y la caja apaisada; con `cover` solo
 *   se veía la mitad del alto (a las espadas se les iba la punta);
 * - Inicio: la regla de los medios de la tarjeta estiraba el retrato del héroe
 *   a 107 x 92, un óvalo;
 * - inventario: el icono del tipo y la imagen salían a la vez, y en el equipo
 *   el retrato seguía en los 92 px del kit dentro de un marco de 80.
 *
 * El criterio es el de `docs/arte/AUDITORIA-ARTE-2026-10-14.md` (ART-5): el
 * objeto entero, `object-fit: contain`, sobre la ranura oscura. Cómo se ve en
 * cada ancho no se puede medir aquí (hace falta pintar la página); esta prueba
 * cuida que no vuelvan las reglas que lo rompían.
 */

import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const raizRepo = new URL('../../../../', import.meta.url);
const leer = (ruta) => readFileSync(new URL(ruta, raizRepo), 'utf8');

/**
 * Las reglas de una hoja: selector (uno por cada coma), cuerpo y la regla-at
 * que la envuelve (`@media …`), sin comentarios.
 *
 * @param {string} css
 * @returns {Array<{selector: string, cuerpo: string, medio: string}>}
 */
function reglas(css) {
  const texto = css.replace(/\/\*[\s\S]*?\*\//g, '');
  const salida = [];
  const pila = [];
  let inicio = 0;
  for (let i = 0; i < texto.length; i += 1) {
    if (texto[i] === '{') {
      const cabeza = texto.slice(inicio, i).trim();
      if (cabeza.startsWith('@')) {
        pila.push(cabeza);
        inicio = i + 1;
      } else {
        const fin = texto.indexOf('}', i);
        const cuerpo = texto.slice(i + 1, fin);
        for (const selector of cabeza.split(',')) {
          salida.push({
            selector: selector.replace(/\s+/g, ' ').trim(),
            cuerpo,
            medio: pila.join(' '),
          });
        }
        i = fin;
        inicio = fin + 1;
      }
    } else if (texto[i] === '}') {
      pila.pop();
      inicio = i + 1;
    }
  }
  return salida;
}

/** El valor de una propiedad en el cuerpo de una regla, o `null`. */
function propiedad(cuerpo, nombre) {
  const hallazgo = new RegExp(`(?:^|;)\\s*${nombre}\\s*:\\s*([^;]+)`).exec(cuerpo ?? '');
  return hallazgo ? hallazgo[1].replace(/\s+/g, ' ').trim() : null;
}

/** El cuerpo de la regla de primer nivel con ese selector exacto. */
function cuerpoDe(hoja, selector) {
  const regla = reglas(leer(hoja)).find((r) => r.selector === selector && r.medio === '');
  return regla?.cuerpo ?? null;
}

const MISIONES = 'frontend/app-web/src/contenido/misiones/misiones.css';
const TIENDA = 'frontend/app-web/src/cuentas/tienda-producto.css';
const FICHA = 'frontend/app-web/src/contenido/inventario/ficha-producto.css';
const VITRINA = 'frontend/app-web/src/contenido/inventario/vitrina.css';
const KIT = 'shared/ui-kit/css/componentes.css';

describe('el arte de la fase 2 cabe en su ranura', () => {
  test('misiones: la escena llena la franja de la tarjeta y no tapa el título', () => {
    expect(propiedad(cuerpoDe(MISIONES, '.mision-card__arte'), 'overflow')).toBe('hidden');
    const imagen = cuerpoDe(MISIONES, '.mision-card__imagen');
    expect(propiedad(imagen, 'position')).toBe('absolute');
    expect(propiedad(imagen, 'inset')).toBe('0');
  });

  test('tienda: el objeto se ve entero en la tarjeta (ART-5)', () => {
    expect(propiedad(cuerpoDe(TIENDA, '.product-image img'), 'object-fit')).toBe('contain');
  });

  test('ficha: el objeto se ve entero sobre la ranura oscura (ART-5)', () => {
    const imagen = cuerpoDe(FICHA, '.ficha__imagen');
    expect(propiedad(imagen, 'object-fit')).toBe('contain');
    expect(propiedad(imagen, 'height')).toMatch(/px$/);
    expect(propiedad(imagen, 'max-height')).toBeNull();
    expect(propiedad(imagen, 'background')).toContain('var(--cromo)');
  });

  test('la regla de los medios de la tarjeta no alcanza al retrato del héroe', () => {
    const selectores = reglas(leer(KIT)).map((r) => r.selector);
    expect(selectores).not.toContain('.tarjeta__medios img');
    expect(selectores).toContain('.tarjeta__medios > img');
  });

  test('vitrina: con imagen no se pinta también el icono, y los objetos van enteros', () => {
    const todas = reglas(leer(VITRINA));
    const oculta = todas.find((r) =>
      /\.marco-heroe__retrato:has\(> ?\.marco-heroe__imagen\) > \.vitrina__icono$/.test(r.selector),
    );
    expect(propiedad(oculta?.cuerpo, 'display')).toBe('none');
    const objetos = todas.find(
      (r) =>
        r.selector ===
        ".vitrina__producto:not([data-tipo='HEROE']) .vitrina__marco .marco-heroe__imagen",
    );
    expect(propiedad(objetos?.cuerpo, 'object-fit')).toBe('contain');
  });

  test('todo contexto que achica el marco del héroe achica también su imagen', () => {
    const hojas = execFileSync(
      'git',
      ['ls-files', '--', 'frontend/app-web/src/**/*.css', 'shared/ui-kit/css/*.css'],
      { cwd: raizRepo, encoding: 'utf8' },
    )
      .split('\n')
      .filter(Boolean);
    const px = (cuerpo) => Number.parseFloat(propiedad(cuerpo, 'width'));
    const base = px(cuerpoDe(KIT, '.marco-heroe__imagen'));
    expect(base).toBeGreaterThan(0);

    const sinAjustar = [];
    for (const hoja of hojas) {
      const todas = reglas(leer(hoja));
      for (const marco of todas) {
        if (!marco.selector.endsWith(' .marco-heroe__retrato') || !(px(marco.cuerpo) > 0)) {
          continue;
        }
        const contexto = marco.selector.slice(0, -'.marco-heroe__retrato'.length);
        const imagen = todas.find(
          (r) =>
            r.selector === `${contexto}.marco-heroe__imagen` &&
            r.medio === marco.medio &&
            px(r.cuerpo) > 0,
        );
        const ancho = imagen ? px(imagen.cuerpo) : base;
        if (ancho >= px(marco.cuerpo)) {
          sinAjustar.push(
            `${hoja.split('/').pop()} → ${marco.selector} (${ancho} px en ${px(marco.cuerpo)} px)`,
          );
        }
      }
    }
    expect(sinAjustar).toEqual([]);
  });
});
