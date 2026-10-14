/**
 * Presupuesto de rendimiento del movimiento — HU-UX-003, MOTION-4.
 *
 * El combate de seis (`combate-seis`) recibe una ráfaga realista por el
 * canal simulado —un golpe propio y tres de rivales, que el ritmo de combate
 * reparte a 2,2 s— mientras el navegador emula un móvil medio (CPU ×4, CDP
 * `Emulation.setCPUThrottlingRate`). Se mide lo que la directiva fija como
 * presupuesto, con `requestAnimationFrame` y `PerformanceObserver`:
 *
 *   - cuadros: media y p95 del tiempo entre cuadros, y cuántos pasan de
 *     33 ms (un cuadro perdido a 60 Hz);
 *   - long tasks (> 50 ms en el hilo principal);
 *   - CLS (desplazamientos de maquetación sin entrada del usuario).
 *
 * La compuerta es conservadora a propósito —el runner de CI comparte CPU y
 * no tiene GPU— y las cifras completas quedan en
 * `docs/evidencia/motion/rendimiento.md` como evidencia del presupuesto
 * (§10 de ANIMATION-RESEARCH.md). Se afirma comportamiento (nada se mueve
 * de sitio, el hilo principal no se bloquea), no un fps exacto.
 */

import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { test, expect } from '@playwright/test';

import { PREFIJO_WEB } from './vistas.js';
import { simularCanal } from './canal-simulado.js';
import { ESCENARIOS } from './escenarios-poblados.js';
import { inyectarSesion } from './identidad.js';

// Los specs se transpilan a CJS: `__dirname` existe; sin él, desde la raíz.
const RAIZ =
  typeof __dirname === 'undefined' ? resolve(process.cwd(), '../../tests/visual') : __dirname;
const SALIDA = resolve(RAIZ, '../../docs/evidencia/motion');

const ESCENARIO = ESCENARIOS.find((e) => e.id === 'combate-seis');
const ID_PARTIDA = 'cccccc01-1111-4111-8111-111111111111';
const RIVALES = [
  'cccccc02-2222-4222-8222-222222222222',
  'cccccc05-5555-4555-8555-555555555555',
  'cccccc06-6666-4666-8666-666666666666',
];
const ALIADOS = ['cccccc03-3333-4333-8333-333333333333', 'cccccc04-4444-4444-8444-444444444444'];

/** Presupuesto (§10): lo que se afirma en CI. */
const PRESUPUESTO = Object.freeze({
  cls: 0.02,
  longTasks: 3,
  frameP95Ms: 40,
  cuadrosLentosMax: 0.1, // ≤ 10 % de los cuadros por encima de 33 ms
});

function golpe(
  idEjecutor,
  idObjetivo,
  { categoria = 'CAUSAR_DANO', vida = 20, maxima = 40, diferencia = -5 } = {},
) {
  return {
    tipo: 'partida.accion.resuelta',
    idPartida: ID_PARTIDA,
    idEjecutor,
    idObjetivo,
    accion: { codigo: 'ATAQUE_BASICO', nombre: categoria, icono: null, tipo: 'ATAQUE' },
    afectados: [
      { idJugador: idEjecutor, vidaActual: 30, vidaMaxima: 40, diferencia: 0, poderActual: 4 },
      { idJugador: idObjetivo, vidaActual: vida, vidaMaxima: maxima, diferencia },
    ],
  };
}

/** La ráfaga: un golpe propio (inmediato) y tres de rivales (2,2 s entre sí). */
function rafaga(yo) {
  return [
    golpe(yo, RIVALES[0], {
      categoria: 'CAUSAR_DANO_CRITICO',
      diferencia: -9,
      vida: 14,
      maxima: 60,
    }),
    golpe(RIVALES[1], ALIADOS[0], { diferencia: -4, vida: 36 }),
    golpe(RIVALES[2], yo, { categoria: 'EVADIR_EL_GOLPE', diferencia: -2, vida: 39, maxima: 52 }),
    golpe(RIVALES[0], ALIADOS[1], { diferencia: -6, vida: 22, maxima: 28 }),
  ];
}

test(
  'la batalla de seis con una ráfaga de golpes a CPU ×4 respeta el presupuesto',
  { tag: '@motion' },
  async ({ browser, baseURL }) => {
    test.setTimeout(90_000);
    const contexto = await browser.newContext({
      viewport: { width: 1366, height: 768 },
      baseURL,
      reducedMotion: 'no-preference',
    });
    try {
      const sesion = ESCENARIO.sesion();
      await inyectarSesion(contexto, sesion);
      const pagina = await contexto.newPage();
      for (const [patron, respuesta] of ESCENARIO.rutas) {
        await pagina.route(patron, (ruta) =>
          ruta.fulfill(typeof respuesta === 'function' ? respuesta(ruta) : respuesta),
        );
      }
      await simularCanal(pagina, {
        ...ESCENARIO.canal,
        mensajes: { [`/tema/partidas/${ID_PARTIDA}`]: rafaga(sesion.uid) },
      });

      // El medidor arranca antes de que llegue nada: cuenta cuadros y vigila
      // long tasks y desplazamientos desde el primer pintado del combate.
      await pagina.addInitScript(() => {
        const m = { deltas: [], longTasks: 0, longTasksMs: 0, cls: 0, inicio: 0, ultimo: 0 };
        window.__medidas = m;
        const cuadro = (ahora) => {
          if (m.inicio && m.ultimo) {
            m.deltas.push(ahora - m.ultimo);
          }
          m.ultimo = ahora;
          requestAnimationFrame(cuadro);
        };
        requestAnimationFrame(cuadro);
        window.__empezarAMedir = () => {
          m.inicio = performance.now();
          m.ultimo = 0;
          m.deltas = [];
          m.longTasks = 0;
          m.longTasksMs = 0;
          m.cls = 0;
        };
        try {
          new PerformanceObserver((lista) => {
            for (const e of lista.getEntries()) {
              if (m.inicio) {
                m.longTasks += 1;
                m.longTasksMs += e.duration;
              }
            }
          }).observe({ type: 'longtask', buffered: false });
        } catch {
          /* sin soporte */
        }
        try {
          new PerformanceObserver((lista) => {
            for (const e of lista.getEntries()) {
              if (m.inicio && !e.hadRecentInput) {
                m.cls += e.value;
              }
            }
          }).observe({ type: 'layout-shift', buffered: false });
        } catch {
          /* sin soporte */
        }
      });

      const cdp = await contexto.newCDPSession(pagina);
      await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });

      await pagina.goto(`/${PREFIJO_WEB}/${ESCENARIO.ruta}`, { waitUntil: 'domcontentloaded' });
      await expect(pagina.locator('body[data-modo="combate"] .combate__campo')).toBeVisible({
        timeout: 15_000,
      });
      await expect(pagina.locator('.combate__vidas .barra-vida')).toHaveCount(6, {
        timeout: 15_000,
      });
      // La barra de mando termina de montarse cuando llegan las acciones del
      // héroe (tres servicios) y entonces el campo toma su alto definitivo: lo
      // que se mide es el movimiento, no el montaje.
      await expect(pagina.locator('.medidor-poder')).toBeVisible({ timeout: 15_000 });
      // La medición empieza con el combate montado; la ráfaga ya está en camino
      // (el primer golpe es inmediato, los otros tres llegan a 2,2 s).
      await pagina.evaluate(() => window.__empezarAMedir());
      await expect(pagina.locator(`[data-puesto="${RIVALES[0]}"] .impacto`)).toBeVisible({
        timeout: 10_000,
      });
      // Los tres golpes rivales: el último objetivo recibe su cifra.
      await expect(pagina.locator(`[data-puesto="${ALIADOS[1]}"] .impacto`)).toBeVisible({
        timeout: 20_000,
      });
      await pagina.waitForTimeout(1200);

      const medidas = await pagina.evaluate(() => {
        const m = window.__medidas;
        const deltas = m.deltas.slice().sort((a, b) => a - b);
        const total = deltas.reduce((s, x) => s + x, 0);
        const p = (q) => deltas[Math.min(deltas.length - 1, Math.floor(q * deltas.length))] ?? 0;
        return {
          cuadros: deltas.length,
          duracionMs: Math.round(total),
          fpsMedio: deltas.length ? Math.round((1000 * deltas.length) / total) : 0,
          frameP50: Number(p(0.5).toFixed(1)),
          frameP95: Number(p(0.95).toFixed(1)),
          frameMax: Number((deltas[deltas.length - 1] ?? 0).toFixed(1)),
          cuadrosLentos: deltas.filter((x) => x > 33.4).length,
          longTasks: m.longTasks,
          longTasksMs: Math.round(m.longTasksMs),
          cls: Number(m.cls.toFixed(4)),
          heapMB: performance.memory
            ? Number((performance.memory.usedJSHeapSize / 1048576).toFixed(1))
            : null,
          animacionesVivas: document.getAnimations().filter((a) => a.playState === 'running')
            .length,
          figurasConGolpe: [...document.querySelectorAll('.campo__puesto .marco-heroe')].filter(
            (figura) => figura.getAnimations().length > 0,
          ).length,
        };
      });

      mkdirSync(SALIDA, { recursive: true });
      const filas = Object.entries(medidas)
        .map(([k, v]) => `| ${k} | ${v} |`)
        .join('\n');
      writeFileSync(
        resolve(SALIDA, 'rendimiento.md'),
        `# Rendimiento del combate — HU-UX-003 (MOTION-4)\n\nGenerado por \`tests/visual/motion-rendimiento.spec.js\`: \`combate-seis\` con una ráfaga de cuatro golpes (uno propio y tres rivales), Chromium headless 1366×768, CPU ×4 (móvil medio emulado).\n\n| medida | valor |\n|---|---|\n${filas}\n\nPresupuesto: CLS < ${PRESUPUESTO.cls}, long tasks ≤ ${PRESUPUESTO.longTasks}, p95 ≤ ${PRESUPUESTO.frameP95Ms} ms, cuadros > 33 ms ≤ ${PRESUPUESTO.cuadrosLentosMax * 100} %.\n`,
      );
      writeFileSync(resolve(SALIDA, 'rendimiento.json'), `${JSON.stringify(medidas, null, 2)}\n`);

      // Nada se mueve de sitio: el movimiento es transform/opacity/filter.
      expect(medidas.cls, `CLS ${medidas.cls}`).toBeLessThan(PRESUPUESTO.cls);
      // El hilo principal no se bloquea con los golpes.
      expect(
        medidas.longTasks,
        `${medidas.longTasks} long tasks (${medidas.longTasksMs} ms)`,
      ).toBeLessThanOrEqual(PRESUPUESTO.longTasks);
      expect(medidas.frameP95, `p95 ${medidas.frameP95} ms`).toBeLessThanOrEqual(
        PRESUPUESTO.frameP95Ms,
      );
      expect(
        medidas.cuadrosLentos / Math.max(1, medidas.cuadros),
        `${medidas.cuadrosLentos} de ${medidas.cuadros} cuadros por encima de 33 ms`,
      ).toBeLessThanOrEqual(PRESUPUESTO.cuadrosLentosMax);
      // Al terminar la ráfaga no queda ninguna animación de golpe colgando (solo el idle).
      // (lo único vivo es el idle del retrato y, si toca, la IA pensando).
      expect(medidas.figurasConGolpe).toBe(0);
    } finally {
      await contexto.close();
    }
  },
);

test(
  'con la batalla cargada, el login y el inicio no cargan nada de batalla',
  { tag: '@motion' },
  async ({ browser, baseURL }) => {
    // Los módulos de batalla (coreografía, FX) se importan solo desde la sala.
    const contexto = await browser.newContext({ baseURL });
    try {
      const pagina = await contexto.newPage();
      const cargados = new Set();
      pagina.on('request', (req) => cargados.add(new URL(req.url()).pathname));
      await pagina.goto(`/${PREFIJO_WEB}/cuentas/login.html`, { waitUntil: 'networkidle' });
      await pagina.goto(`/${PREFIJO_WEB}/cuentas/portada.html`, { waitUntil: 'networkidle' });
      const deBatalla = [...cargados].filter((ruta) =>
        /coreografia-de-combate|juego\/fx\.js|salas-partidas\/combate\.js/.test(ruta),
      );
      expect(deBatalla).toEqual([]);
    } finally {
      await contexto.close();
    }
  },
);
