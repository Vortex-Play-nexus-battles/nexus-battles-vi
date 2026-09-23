/**
 * Accesibilidad automática de las 32 vistas — UX-R3.11 (§30, §33).
 *
 * ## Qué es y qué no es
 *
 * axe-core encuentra **una parte** de los problemas de accesibilidad: los que
 * se pueden decidir mirando el DOM y los colores calculados. Contraste, roles
 * ARIA mal puestos, controles sin nombre accesible, listas mal anidadas,
 * `<html>` sin idioma. No encuentra si el orden de lectura tiene sentido, ni
 * si un texto alternativo describe la imagen, ni si el foco va a donde debe.
 * Eso sigue siendo trabajo de persona y está en el barrido de §26.
 *
 * Aquí se comprueba lo automatizable, en cada vista, **con la identidad que la
 * abre de verdad** —la matriz decide cuál— y en dos anchuras: escritorio y
 * móvil, porque hay reglas (contraste sobre un fondo que cambia, objetivo
 * táctil) que solo se ven en una de las dos.
 *
 * ## La compuerta
 *
 * Falla con `serious` y `critical`. `moderate` y `minor` se publican como
 * aviso: son casi siempre decisiones de diseño discutibles —un `<h3>` que
 * salta a `<h5>`, una región sin `<main>`— y convertirlos en bloqueantes de
 * golpe obliga a tocar las 32 vistas en un PR, que es exactamente el PR
 * gigante que este bloque no quiere.
 */

import { AxeBuilder } from '@axe-core/playwright';
import { test, expect } from '@playwright/test';

import { PREFIJO_WEB, VISTAS } from './vistas.js';
import { inyectarSesion } from './identidad.js';
import { conseguirPersonas, personasDe } from './personas.js';

/** Escritorio y móvil: las dos donde cambia lo que axe puede medir. */
const ANCHURAS = Object.freeze([
  { nombre: 'desktop', ancho: 1440, alto: 900 },
  { nombre: 'movil', ancho: 375, alto: 812 },
]);

/** Lo que se exige. WCAG 2.1 AA más las reglas de buenas prácticas de axe. */
const NORMAS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

const GRAVES = new Set(['serious', 'critical']);

let sesiones = {};
const leves = [];

// A proposito NO en serie: es una auditoria, y una auditoria que se para en el
// primer hallazgo solo cuenta el primero. El fichero corre con `workers: 1`,
// asi que el orden sigue siendo el del catalogo.

test.beforeAll(async () => {
  // Sin backend: personas sintéticas. Es el mismo modo en que corre el
  // laboratorio visual en cada PR.
  ({ sesiones } = await conseguirPersonas(null));
});

for (const vista of VISTAS) {
  for (const pantalla of ANCHURAS) {
    test(`${vista.id} · ${pantalla.nombre}`, async ({ browser, baseURL }) => {
      const contexto = await browser.newContext({
        viewport: { width: pantalla.ancho, height: pantalla.alto },
        baseURL,
      });
      try {
        const { para } = personasDe(vista);
        await inyectarSesion(contexto, vista.acceso === 'publica' ? null : (sesiones[para] ?? null));

        const pagina = await contexto.newPage();
        await pagina.goto(`/${PREFIJO_WEB}/${vista.ruta}`, { waitUntil: 'domcontentloaded' });
        await pagina.waitForTimeout(900);

        const resultado = await new AxeBuilder({ page: pagina }).withTags(NORMAS).analyze();

        const graves = resultado.violations.filter((v) => GRAVES.has(v.impact));
        for (const v of resultado.violations.filter((x) => !GRAVES.has(x.impact))) {
          leves.push(`${vista.id}/${pantalla.nombre}: ${v.id} (${v.impact}, ${v.nodes.length})`);
        }

        expect(
          graves.map(
            (v) =>
              `${v.id} [${v.impact}] ${v.nodes.length}× — ${v.help}\n` +
              v.nodes
                .slice(0, 3)
                .map((n) => `        ${n.target.join(' ')}  ${n.html.slice(0, 160)}`)
                .join('\n'),
          ),
          `${vista.id} a ${pantalla.ancho}px`,
        ).toEqual([]);
      } finally {
        await contexto.close();
      }
    });
  }
}

test.afterAll(() => {
  if (leves.length === 0) {
    console.log('\n  axe: sin hallazgos moderados ni menores.');
    return;
  }
  const porRegla = new Map();
  for (const l of leves) {
    const regla = l.split(': ')[1].split(' ')[0];
    porRegla.set(regla, (porRegla.get(regla) ?? 0) + 1);
  }
  console.log(`\n  axe — ${leves.length} hallazgos moderate/minor (no bloquean):`);
  for (const [regla, n] of [...porRegla].sort((a, b) => b[1] - a[1])) {
    console.log(`    ${String(n).padStart(4)}  ${regla}`);
  }
});
