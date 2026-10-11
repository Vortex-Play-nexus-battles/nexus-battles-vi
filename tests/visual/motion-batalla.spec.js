/**
 * Game feel del combate en un navegador de verdad — HU-UX-003, MOTION-2.
 *
 * Con el canal simulado del laboratorio (`combate-narrado`: un crítico
 * propio, una evasión del rival, una curación y un cambio de turno) se
 * afirma COMPORTAMIENTO, nunca milisegundos:
 *
 *   - quien actúa pasa a su estado (`data-accion`) y quien recibe al suyo
 *     (`data-golpe`), y los dos se van solos;
 *   - la cifra nace sobre el golpeado con su texto y su icono;
 *   - el HUD acaba en el valor del servidor (`aria-valuenow`), con o sin
 *     movimiento;
 *   - con `prefers-reduced-motion` no corre ninguna animación y el dato
 *     sigue ahí.
 */

import { test, expect } from '@playwright/test';

import { PREFIJO_WEB } from './vistas.js';
import { simularCanal } from './canal-simulado.js';
import { ESCENARIOS } from './escenarios-poblados.js';
import { inyectarSesion } from './identidad.js';

const ESCENARIO = ESCENARIOS.find((e) => e.id === 'combate-narrado');
const RIVAL = 'cccccc02-2222-4222-8222-222222222222';

async function abrirCombate(browser, baseURL, { reduce = false } = {}) {
  const contexto = await browser.newContext({
    viewport: { width: 1366, height: 768 },
    baseURL,
    reducedMotion: reduce ? 'reduce' : 'no-preference',
  });
  await inyectarSesion(contexto, ESCENARIO.sesion());
  const pagina = await contexto.newPage();
  for (const [patron, respuesta] of ESCENARIO.rutas) {
    await pagina.route(patron, (ruta) =>
      ruta.fulfill(typeof respuesta === 'function' ? respuesta(ruta) : respuesta),
    );
  }
  await simularCanal(pagina, ESCENARIO.canal);
  await pagina.goto(`/${PREFIJO_WEB}/${ESCENARIO.ruta}`, { waitUntil: 'domcontentloaded' });
  await expect(pagina.locator('body[data-modo="combate"] .combate__campo')).toBeVisible({
    timeout: 15_000,
  });
  return { contexto, pagina };
}

test('el golpe se ve ocurrir: actor en acción, golpeado con su cifra e icono, y todo vuelve a su sitio', async ({
  browser,
  baseURL,
}) => {
  const { contexto, pagina } = await abrirCombate(browser, baseURL);
  try {
    const rival = pagina.locator(`[data-puesto="${RIVAL}"]`);
    // El primer aviso es el crítico propio: la cifra nace sobre el rival.
    const cifra = rival.locator('.impacto');
    await expect(cifra).toBeVisible({ timeout: 10_000 });
    await expect(cifra).toContainText('−9');
    await expect(cifra).toContainText('VIDA');
    await expect(cifra).toContainText('Crítico');
    await expect(cifra.locator('.impacto__icono')).toHaveCount(1);
    // El rival acusó el golpe (estado) mientras duró; luego se va.
    await expect(rival).toHaveAttribute('data-golpe', 'critico', { timeout: 5_000 });
    await expect(rival).not.toHaveAttribute('data-golpe', /.+/, { timeout: 10_000 });
    // El HUD dice la verdad del servidor: 14 de 60.
    const barra = pagina.locator(`.combate__vidas [data-jugador="${RIVAL}"]`);
    await expect(barra).toHaveAttribute('aria-valuenow', '14');
    await expect(barra.locator('.barra-vida__valor')).toHaveText('14/60');
    // La IA «piensa» hasta que llega su golpe (segundo aviso, tras la pausa
    // perceptual), y entonces ataca y deja de pensar.
    await expect(rival).toHaveAttribute('data-accion', 'ataca', { timeout: 10_000 });
    await expect(rival).not.toHaveAttribute('data-pensando', /.+/);
    await expect(rival).not.toHaveAttribute('data-accion', /.+/, { timeout: 10_000 });
    // Al final nadie se queda con animaciones colgando sobre la figura.
    const colgando = await rival
      .locator('.marco-heroe')
      .evaluate((figura) => figura.getAnimations().length);
    expect(colgando).toBe(0);
  } finally {
    await contexto.close();
  }
});

test('con prefers-reduced-motion el dato está y el movimiento no', async ({ browser, baseURL }) => {
  const { contexto, pagina } = await abrirCombate(browser, baseURL, { reduce: true });
  try {
    const rival = pagina.locator(`[data-puesto="${RIVAL}"]`);
    await expect(rival.locator('.impacto')).toContainText('−9', { timeout: 10_000 });
    await expect(pagina.locator(`.combate__vidas [data-jugador="${RIVAL}"]`)).toHaveAttribute(
      'aria-valuenow',
      '14',
    );
    const animaciones = await pagina.evaluate(() => document.getAnimations().length);
    expect(animaciones).toBe(0);
    // El registro lo cuenta con palabras: la animación nunca fue la única información.
    await expect(pagina.locator('.registro-combate__linea--critico').first()).toContainText(
      'Crítico',
    );
  } finally {
    await contexto.close();
  }
});
