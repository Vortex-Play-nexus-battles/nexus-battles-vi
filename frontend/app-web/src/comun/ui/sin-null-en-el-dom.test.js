/**
 * Guardián: ningún módulo le pasa `null` (o `undefined`) a `replaceChildren`,
 * `append`, `prepend`, `before` o `after` — RFINAL-07 (revisión de DEV del 4-oct).
 *
 * Esos métodos del DOM convierten cualquier argumento que no sea un nodo en
 * texto: `zona.replaceChildren(titulo, condicion ? linea : null)` pinta la
 * palabra «null» en pantalla. Así salió en la ficha de gestión de productos,
 * debajo del título, cuando el producto no tenía promoción; y lo mismo pasaba
 * en el aviso del pago sin acciones. `h()` de `comun/ui/dom.js` sí descarta
 * los `null` de `hijos`, por eso allí la forma `cond ? x : null` es correcta;
 * en las llamadas directas al DOM, no.
 *
 * Qué se busca: un argumento de primer nivel de esas llamadas que sea un
 * condicional cuya rama termina en `null`/`undefined` (`c ? x : null`), un
 * `c && x` (que vale `false` o `null`), o un `null`/`undefined` a secas. La
 * forma buena es filtrar antes (`...lista.filter(Boolean)`) o expandir una
 * lista condicional (`...(c ? [x] : [])`).
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

function modulos(raiz) {
  const salida = [];
  for (const entrada of readdirSync(raiz)) {
    const completa = join(raiz, entrada);
    if (statSync(completa).isDirectory()) {
      if (entrada !== 'node_modules') {
        salida.push(...modulos(completa));
      }
    } else if (
      entrada.endsWith('.js') &&
      !entrada.endsWith('.test.js') &&
      !entrada.endsWith('.spec.js')
    ) {
      salida.push(completa);
    }
  }
  return salida;
}

/** Cierre de la llamada que empieza en `inicio` (justo tras el paréntesis). */
function finDeLlamada(texto, inicio) {
  let profundidad = 1;
  let comilla = null;
  for (let i = inicio; i < texto.length; i += 1) {
    const c = texto[i];
    if (comilla) {
      if (c === '\\') {
        i += 1;
      } else if (c === comilla) {
        comilla = null;
      }
    } else if (c === "'" || c === '"' || c === '`') {
      comilla = c;
    } else if (c === '(') {
      profundidad += 1;
    } else if (c === ')') {
      profundidad -= 1;
      if (profundidad === 0) {
        return i;
      }
    }
  }
  return texto.length;
}

/** Argumentos de primer nivel: comas fuera de paréntesis, corchetes, llaves y cadenas. */
function argumentos(texto) {
  const salida = [];
  let actual = '';
  let profundidad = 0;
  let comilla = null;
  for (let i = 0; i < texto.length; i += 1) {
    const c = texto[i];
    if (comilla) {
      actual += c;
      if (c === '\\') {
        actual += texto[i + 1] ?? '';
        i += 1;
      } else if (c === comilla) {
        comilla = null;
      }
    } else if (c === "'" || c === '"' || c === '`') {
      comilla = c;
      actual += c;
    } else if ('([{'.includes(c)) {
      profundidad += 1;
      actual += c;
    } else if (')]}'.includes(c)) {
      profundidad -= 1;
      actual += c;
    } else if (c === ',' && profundidad === 0) {
      salida.push(actual);
      actual = '';
    } else {
      actual += c;
    }
  }
  if (actual.trim()) {
    salida.push(actual);
  }
  return salida.map((a) => a.trim());
}

const PELIGROSO = [
  /\?[\s\S]*:\s*(null|undefined)$/u,
  /^[\w.?!]+(\s*\([^)]*\))?\s*&&/u,
  /^(null|undefined)$/u,
];

export function hallazgos(texto) {
  const encontrados = [];
  const llamada = /\.(replaceChildren|append|prepend|before|after)\(/gu;
  for (let m = llamada.exec(texto); m; m = llamada.exec(texto)) {
    const inicio = m.index + m[0].length;
    const fin = finDeLlamada(texto, inicio);
    for (const arg of argumentos(texto.slice(inicio, fin))) {
      if (PELIGROSO.some((patron) => patron.test(arg))) {
        encontrados.push({
          linea: texto.slice(0, m.index).split('\n').length,
          metodo: m[1],
          argumento: arg.replace(/\s+/gu, ' ').slice(0, 80),
        });
      }
    }
  }
  return encontrados;
}

describe('ningún null llega al DOM como texto', () => {
  test('el detector reconoce las formas peligrosas y deja pasar las buenas', () => {
    expect(hallazgos("zona.replaceChildren(a, cond ? h('p') : null);")).toHaveLength(1);
    expect(hallazgos('zona.append(lista.length && h(lista));')).toHaveLength(1);
    expect(hallazgos('zona.append(null);')).toHaveLength(1);
    expect(
      hallazgos("zona.replaceChildren(...[a, cond ? h('p') : null].filter(Boolean));"),
    ).toEqual([]);
    expect(hallazgos("zona.replaceChildren(a, ...(cond ? [h('p')] : []));")).toEqual([]);
    expect(hallazgos("zona.append(h('p', { hijos: [cond ? x : null] }));")).toEqual([]);
    expect(hallazgos("zona.append(cond ? h('p') : h('span'));")).toEqual([]);
  });

  test('ningún módulo de src/ pasa null o undefined directamente al DOM', () => {
    const malos = [];
    for (const archivo of modulos(SRC)) {
      for (const hallazgo of hallazgos(readFileSync(archivo, 'utf8'))) {
        malos.push(
          `${relative(SRC, archivo).replace(/\\/gu, '/')}:${hallazgo.linea} ${hallazgo.metodo}(${hallazgo.argumento})`,
        );
      }
    }
    expect(malos).toEqual([]);
  });
});
