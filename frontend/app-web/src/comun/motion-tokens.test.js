/**
 * Nexus Motion System — MOTION-1: lo que las hojas prometen.
 *
 * `motion.js` lee sus tiempos de `--motion-*` y confía en que el modo
 * determinista (`<html data-test-motion="off">`) apague también lo que se
 * anima desde CSS. Esta prueba cuida esas dos promesas y la regla de
 * rendimiento que ningún navegador avisa cuando se rompe:
 *
 * - los alias `--motion-*` / `--ease-*` existen y apuntan a los tokens del
 *   kit (una sola fuente de valores);
 * - el modo de pruebas deja toda animación y transición de CSS en un cuadro;
 * - ningún `@keyframes` del kit ni de una vista anima width, height, left,
 *   top, margin o padding: se componen en el hilo principal y empujan lo de
 *   alrededor (web.dev/animations-guide); solo transform, opacity y filter;
 * - nada late para siempre salvo lo que se decidió que latiera (un indicador
 *   de carga, el halo del asistente, la respiración de un héroe).
 */

import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const raizRepo = new URL('../../../../', import.meta.url);
const tokens = readFileSync(new URL('shared/ui-kit/css/tokens.css', raizRepo), 'utf8');

/** Hojas CSS que git sigue en el kit y en las vistas. */
function hojas() {
  return execFileSync(
    'git',
    ['ls-files', '--', 'shared/ui-kit/css/*.css', 'frontend/app-web/src/**/*.css'],
    { cwd: raizRepo, encoding: 'utf8' },
  )
    .split('\n')
    .filter(Boolean)
    .map((ruta) => ({ ruta, css: readFileSync(new URL(ruta, raizRepo), 'utf8') }));
}

/** Cada `@keyframes nombre { … }` de una hoja, con su cuerpo. */
function keyframes(css) {
  const bloques = [];
  for (const inicio of css.matchAll(/@keyframes\s+([\w-]+)\s*\{/g)) {
    let i = inicio.index + inicio[0].length;
    let profundidad = 1;
    while (profundidad > 0 && i < css.length) {
      if (css[i] === '{') {
        profundidad += 1;
      } else if (css[i] === '}') {
        profundidad -= 1;
      }
      i += 1;
    }
    bloques.push({ nombre: inicio[1], cuerpo: css.slice(inicio.index + inicio[0].length, i - 1) });
  }
  return bloques;
}

/**
 * Animaciones que SÍ pueden ser infinitas, y por qué. Añadir una aquí es una
 * decisión de diseño, no un atajo.
 */
const INFINITAS_PERMITIDAS = new Map([
  ['giro-de-carga', 'indicador de carga de un botón (aria-busy): dice «esperando»'],
  ['girar', 'indicador de carga de las subastas'],
  ['halo-del-nexo', 'el halo del asistente: ambiental, pequeño y lento'],
  ['motion-idle', 'la respiración de un héroe en el campo (MOTION-2)'],
  [
    'motion-pensando',
    'la IA «piensa»: indicador de espera acotado por su estado (≤ 2,2 s, MOTION-2)',
  ],
]);

describe('Nexus Motion System en tokens.css', () => {
  test('los alias --motion-* apuntan a las duraciones del kit', () => {
    for (const [alias, token] of [
      ['instant', 'instantaneo'],
      ['fast', 'rapido'],
      ['normal', 'normal'],
      ['slow', 'pausado'],
      ['turn', 'turno'],
    ]) {
      expect(tokens).toMatch(new RegExp(`--motion-${alias}:\\s*var\\(--duracion-${token}\\)`));
    }
  });

  test('los alias --ease-* apuntan a las curvas del kit', () => {
    for (const [alias, token] of [
      ['standard', 'estandar'],
      ['enter', 'salida'],
      ['exit', 'entrada'],
      ['impact', 'impacto'],
      ['spring', 'impulso'],
    ]) {
      expect(tokens).toMatch(new RegExp(`--ease-${alias}:\\s*var\\(--curva-${token}\\)`));
    }
    expect(tokens).toMatch(/--curva-impacto:\s*cubic-bezier\(0\.2, 0\.9, 0\.3, 1\)/);
  });

  test('con movimiento reducido las duraciones valen cero y nada se anima ni transiciona', () => {
    const bloque = tokens.slice(tokens.indexOf('@media (prefers-reduced-motion: reduce)'));
    for (const token of ['instantaneo', 'rapido', 'normal', 'pausado', 'turno']) {
      expect(bloque).toMatch(new RegExp(`--duracion-${token}:\\s*0ms`));
    }
    expect(bloque).toMatch(/animation:\s*none\s*!important/);
    expect(bloque).toMatch(/transition:\s*none\s*!important/);
    expect(bloque).not.toMatch(/0\.01ms/);
  });

  test('el modo determinista de pruebas apaga CSS y tokens a la vez', () => {
    const inicio = tokens.indexOf("html[data-test-motion='off']");
    expect(inicio).toBeGreaterThan(-1);
    const bloque = tokens.slice(inicio);
    for (const token of ['instantaneo', 'rapido', 'normal', 'pausado', 'turno']) {
      expect(bloque).toMatch(new RegExp(`--duracion-${token}:\\s*0ms`));
    }
    // `none`, no «0,01 ms»: una transición de 0,01 ms sobre `*` enciende
    // `transition-property: all` y pinta un cuadro con el valor viejo.
    expect(bloque).toMatch(/animation:\s*none\s*!important/);
    expect(bloque).toMatch(/transition:\s*none\s*!important/);
  });
});

describe('las hojas del kit y de las vistas', () => {
  const todas = hojas();

  test('se leen hojas del kit y de las vistas', () => {
    expect(todas.some((h) => h.ruta.endsWith('componentes.css'))).toBe(true);
    expect(todas.some((h) => h.ruta.includes('frontend/app-web/src/'))).toBe(true);
  });

  test('ningún @keyframes anima width, height, left, top, margin ni padding', () => {
    const prohibidas = /^\s*(width|height|left|top|right|bottom|margin[a-z-]*|padding[a-z-]*)\s*:/m;
    const infractores = [];
    for (const { ruta, css } of todas) {
      for (const { nombre, cuerpo } of keyframes(css)) {
        const propiedad = prohibidas.exec(cuerpo)?.[1];
        if (propiedad) {
          infractores.push(`${ruta} @keyframes ${nombre} anima ${propiedad}`);
        }
      }
    }
    expect(infractores).toEqual([]);
  });

  test('solo late para siempre lo que se decidió que latiera', () => {
    const infinitas = [];
    for (const { ruta, css } of todas) {
      for (const linea of css.matchAll(/animation(?:-name)?\s*:\s*([\w-]+)[^;]*\binfinite\b/g)) {
        if (!INFINITAS_PERMITIDAS.has(linea[1])) {
          infinitas.push(`${ruta}: ${linea[1]}`);
        }
      }
    }
    expect(infinitas).toEqual([]);
  });
});
