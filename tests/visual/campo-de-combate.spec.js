/**
 * §7.6 del documento, medido: «el área de juego debe ocupar más del 80 % de
 * la pantalla» — UXC-2.
 *
 * Es un criterio numérico, así que se mide en vez de mirarlo: área del campo
 * (`.combate__campo`) sobre el área de la ventana, con la partida en curso y
 * los datos de laboratorio de `escenarios-poblados.js`. En 1366×768 (y en el
 * mínimo de la ficha, 1360×768) con un duelo y con seis participantes.
 *
 * Por debajo de 768 px de ancho la vista retira el campo a propósito (CA-05,
 * degradación por tamaño) y dice por qué: ahí no se mide.
 */

import { test, expect } from '@playwright/test';

import { PREFIJO_WEB } from './vistas.js';
import { simularCanal } from './canal-simulado.js';
import { ESCENARIOS } from './escenarios-poblados.js';
import { inyectarSesion } from './identidad.js';

const MEDIDAS = [
  { ancho: 1366, alto: 768 },
  { ancho: 1360, alto: 768 },
];
const PARTIDAS = ['combate-mi-turno', 'combate-narrado', 'combate-seis'];

for (const id of PARTIDAS) {
  const escenario = ESCENARIOS.find((e) => e.id === id);
  for (const { ancho, alto } of MEDIDAS) {
    test(`${id} · ${ancho}×${alto}: el campo ocupa más del 80 % de la pantalla`, async ({
      browser,
      baseURL,
    }) => {
      const contexto = await browser.newContext({ viewport: { width: ancho, height: alto }, baseURL });
      try {
        await inyectarSesion(contexto, escenario.sesion());
        const pagina = await contexto.newPage();
        for (const [patron, respuesta] of escenario.rutas) {
          await pagina.route(patron, (ruta) =>
            ruta.fulfill(typeof respuesta === 'function' ? respuesta(ruta) : respuesta),
          );
        }
        await simularCanal(pagina, escenario.canal);
        await pagina.goto(`/${PREFIJO_WEB}/${escenario.ruta}`, { waitUntil: 'domcontentloaded' });
        await expect(pagina.locator('body[data-modo="combate"] .combate__campo')).toBeVisible({
          timeout: 15_000,
        });
        // La barra de mando termina de pintarse cuando llegan las acciones del
        // heroe (tres servicios): se mide con ella completa.
        await expect(pagina.locator('.medidor-poder')).toBeVisible({ timeout: 15_000 });

        const medida = await pagina.evaluate(() => {
          const campo = document.querySelector('.combate__campo').getBoundingClientRect();
          return {
            proporcion: (campo.width * campo.height) / (window.innerWidth * window.innerHeight),
            desborda: document.documentElement.scrollHeight > window.innerHeight,
          };
        });
        expect(
          medida.proporcion,
          `el campo ocupa el ${(medida.proporcion * 100).toFixed(1)} % de la pantalla`,
        ).toBeGreaterThan(0.8);
        // Sin desplazamiento: HUD, campo y mando caben en la ventana.
        expect(medida.desborda).toBe(false);
      } finally {
        await contexto.close();
      }
    });
  }
}
