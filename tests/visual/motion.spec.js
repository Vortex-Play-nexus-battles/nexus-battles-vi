/**
 * Nexus Motion System en un navegador de verdad — MOTION-1.
 *
 * Jest prueba el sistema sobre jsdom, que no tiene Web Animations API. Aquí
 * se comprueba lo que solo un navegador puede decir: que el movimiento
 * existe cuando se permite, que NO existe con `prefers-reduced-motion` ni
 * en el modo determinista, y que la transición entre páginas está declarada
 * (y es inocua donde no se entiende). Se prueba el comportamiento —hay o no
 * hay animación—, nunca un milisegundo exacto.
 */

import { test, expect } from '@playwright/test';

import { PREFIJO_WEB } from './vistas.js';
import { sinMovimiento } from './movimiento.js';

const VISTA = 'cuentas/portada.html';
const MODULO = '/frontend/app-web/src/comun/ui/motion.js';

/** Carga el módulo dentro de la página y aplica un verbo a un elemento nuevo. */
async function golpearEnLaPagina(pagina) {
  return pagina.evaluate(async (ruta) => {
    const { motion, permitido } = await import(ruta);
    const el = document.createElement('div');
    el.textContent = 'objetivo';
    document.body.append(el);
    const animacion = motion.damage(el, { direccion: 1 });
    const vivas = el.getAnimations().length;
    await animacion.finished;
    return {
      permitido: permitido(),
      vivas,
      despues: el.getAnimations().length,
      estado: animacion.playState,
    };
  }, MODULO);
}

test('con movimiento permitido, un golpe anima por WAAPI y termina solo', async ({
  browser,
  baseURL,
}) => {
  const contexto = await browser.newContext({ baseURL, reducedMotion: 'no-preference' });
  try {
    const pagina = await contexto.newPage();
    await pagina.goto(`/${PREFIJO_WEB}/${VISTA}`, { waitUntil: 'domcontentloaded' });
    const r = await golpearEnLaPagina(pagina);
    expect(r.permitido).toBe(true);
    expect(r.vivas).toBe(1);
    expect(r.despues).toBe(0);
    expect(r.estado).toBe('finished');
  } finally {
    await contexto.close();
  }
});

test('con prefers-reduced-motion: reduce no se arranca ninguna animación', async ({
  browser,
  baseURL,
}) => {
  const contexto = await browser.newContext({ baseURL, reducedMotion: 'reduce' });
  try {
    const pagina = await contexto.newPage();
    await pagina.goto(`/${PREFIJO_WEB}/${VISTA}`, { waitUntil: 'domcontentloaded' });
    const r = await golpearEnLaPagina(pagina);
    expect(r.permitido).toBe(false);
    expect(r.vivas).toBe(0);
    expect(r.estado).toBe('finished');
    // Tampoco las de CSS: el kit las apaga con `animation: none`.
    await pagina.waitForTimeout(300);
    expect(await pagina.evaluate(() => document.getAnimations().length)).toBe(0);
  } finally {
    await contexto.close();
  }
});

test('en el modo determinista (data-test-motion="off") tampoco, aunque el sistema no pida nada', async ({
  browser,
  baseURL,
}) => {
  const contexto = await browser.newContext({ baseURL, reducedMotion: 'no-preference' });
  try {
    await sinMovimiento(contexto);
    const pagina = await contexto.newPage();
    await pagina.goto(`/${PREFIJO_WEB}/${VISTA}`, { waitUntil: 'domcontentloaded' });
    expect(
      await pagina.evaluate(() => document.documentElement.getAttribute('data-test-motion')),
    ).toBe('off');
    const r = await golpearEnLaPagina(pagina);
    expect(r.permitido).toBe(false);
    expect(r.vivas).toBe(0);
    await pagina.waitForTimeout(300);
    expect(await pagina.evaluate(() => document.getAnimations().length)).toBe(0);
  } finally {
    await contexto.close();
  }
});

test('la transición entre páginas está declarada en el kit y los tokens de movimiento resuelven', async ({
  browser,
  baseURL,
}) => {
  const contexto = await browser.newContext({ baseURL });
  try {
    const pagina = await contexto.newPage();
    await pagina.goto(`/${PREFIJO_WEB}/${VISTA}`, { waitUntil: 'domcontentloaded' });
    const r = await pagina.evaluate(() => {
      const reglas = [...document.styleSheets].flatMap((hoja) => {
        try {
          return [...hoja.cssRules];
        } catch {
          return [];
        }
      });
      const estilo = getComputedStyle(document.documentElement);
      return {
        viewTransition: reglas.some((regla) => regla.constructor.name === 'CSSViewTransitionRule'),
        fast: estilo.getPropertyValue('--motion-fast').trim(),
        impact: estilo.getPropertyValue('--ease-impact').trim(),
      };
    });
    expect(r.viewTransition).toBe(true);
    expect(r.fast).toBe('120ms');
    expect(r.impact).toBe('cubic-bezier(0.2, 0.9, 0.3, 1)');
  } finally {
    await contexto.close();
  }
});
