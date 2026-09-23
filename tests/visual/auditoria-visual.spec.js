/**
 * Laboratorio visual — UX-R2.1.
 *
 * Recorre las 31 vistas del producto en cinco anchuras, guarda una captura de
 * cada combinación y pasa las auditorías estructurales de `auditoria.js`.
 *
 * ## Dos modos
 *
 * **Sin backend** (por omisión): sirve **la raíz del monorepo** con un servidor
 * estático — la raíz, no `src/`, porque las vistas enlazan el kit saliendo de
 * `src/` (ver `PREFIJO_WEB` en `vistas.js`). No hay API, así que cada vista
 * enseña su estado degradado — que es precisamente el que más se rompe y el que
 * nadie mira. Rápido, hermético, sin infraestructura: puede correr en cada PR.
 *
 * **Con backend**: `VISUAL_BASE=http://localhost:8099 npx playwright test
 * --config=playwright.visual.config.js`. Apuntando al banco de `tests/e2e/` o
 * al host de dev, el arnés registra una cuenta efímera de verdad y captura los
 * estados cargados.
 *
 * ## Qué falla y qué no
 *
 * Las auditorías **no** fallan la prueba todavía: se publican como aviso.
 * Convertirlas en bloqueantes es el último paso de UX-R2.9, cuando ya no
 * queden hallazgos heredados; una compuerta que nace roja la desactiva
 * alguien en dos días. Lo que sí falla desde el primer día es que el catálogo
 * de vistas se quede desactualizado.
 */

import { existsSync, mkdirSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

import { test, expect, request as apiRequest } from '@playwright/test';

import { PANTALLAS, PREFIJO_WEB, VISTAS } from './vistas.js';
import { auditar } from './auditoria.js';
import { inyectarSesion } from './identidad.js';
import { NOMBRE, conseguirPersonas, personasDe } from './personas.js';

// Playwright transpila estos specs a CommonJS (no hay package.json con
// "type": "module" en `tests/`), asi que `import.meta` no existe aqui y
// `__dirname` si. Mismo patron que `tests/e2e/degradacion.e2e.spec.js`.
const AQUI =
  typeof __dirname === 'undefined' ? resolve(process.cwd(), '../../tests/visual') : __dirname;
const RAIZ = join(AQUI, '..', '..');
// La carpeta se llamaba `ux-r2` porque nació con aquel bloque. Ya no es de
// un bloque: es el laboratorio del producto, y se corre en cada PR.
const EVIDENCIA = join(RAIZ, 'docs', 'evidencia', 'laboratorio-visual');
const CON_BACKEND = Boolean(process.env.VISUAL_BASE);

/** Todo lo que encuentra la corrida, para el informe final. */
const informe = [];

test.describe.configure({ mode: 'serial' });

/**
 * UX-R3.9 — una persona por rol, no dos aproximaciones.
 *
 * Hasta aquí había dos sesiones, y con backend real la segunda era la
 * primera: `sesionAdmin = sesionJugador`. Es decir, las nueve pantallas de la
 * consola se fotografiaban **con un jugador**, salía el estado de permiso
 * denegado, y eso se archivaba como «la captura de la vista de auditoría».
 *
 * Ahora cada vista se abre con la persona que le corresponde según la matriz
 * de acceso, y las personas se consiguen por los mecanismos reales del
 * sistema. Ver `personas.js`.
 */
let sesiones = {};
let avisosDePersonas = [];

test.beforeAll(async ({ baseURL }) => {
  if (!CON_BACKEND) {
    ({ sesiones, avisos: avisosDePersonas } = await conseguirPersonas(null));
    return;
  }
  const api = await apiRequest.newContext({ baseURL, ignoreHTTPSErrors: true });
  try {
    ({ sesiones, avisos: avisosDePersonas } = await conseguirPersonas(api));
    for (const aviso of avisosDePersonas) {
      console.warn(`  ⚠ identidades: ${aviso}`);
    }
  } finally {
    await api.dispose();
  }
});

test('el catálogo de vistas cubre todas las que hay en el repositorio', async () => {
  const base = join(RAIZ, 'frontend', 'app-web', 'src');
  const encontradas = [];
  const recorrer = (dir, prefijo = '') => {
    for (const entrada of readdirSync(dir)) {
      const completa = join(dir, entrada);
      if (statSync(completa).isDirectory()) {
        recorrer(completa, `${prefijo}${entrada}/`);
      } else if (entrada.endsWith('.html')) {
        encontradas.push(`${prefijo}${entrada}`);
      }
    }
  };
  recorrer(base);

  const catalogadas = new Set(VISTAS.map((v) => v.ruta));
  const sinCatalogar = encontradas.filter((r) => !catalogadas.has(r));
  const fantasmas = [...catalogadas].filter((r) => !encontradas.includes(r));

  expect(
    sinCatalogar,
    'hay vistas en el repositorio que el arnés no revisa: añádelas a tests/visual/vistas.js',
  ).toEqual([]);
  expect(fantasmas, 'el catálogo nombra vistas que ya no existen').toEqual([]);
});

for (const vista of VISTAS) {
  for (const pantalla of PANTALLAS) {
    test(`${vista.id} · ${pantalla.nombre} (${pantalla.ancho}×${pantalla.alto})`, async ({
      browser,
      baseURL,
    }) => {
      const contexto = await browser.newContext({
        viewport: { width: pantalla.ancho, height: pantalla.alto },
        baseURL,
      });
      try {
        // La persona sale de la matriz de acceso, no de una lista aparte.
        const { para } = personasDe(vista);
        const sesion = vista.acceso === 'publica' ? null : (sesiones[para] ?? null);
        await inyectarSesion(contexto, sesion);

        const pagina = await contexto.newPage();
        // Los fallos de red son el estado que se quiere fotografiar sin
        // backend: no se espera a que la red calle, solo al DOM.
        await pagina.goto(`/${PREFIJO_WEB}/${vista.ruta}`, { waitUntil: 'domcontentloaded' });
        // Un respiro para que los módulos monten y pinten su estado.
        await pagina.waitForTimeout(900);

        // Si el kit no cargó, lo que se fotografía es HTML desnudo y todas las
        // medidas de abajo mienten. Es un fallo del arnés, no de la vista, y
        // tiene que gritar: la primera corrida de UX-R2.1 servía `src/` y las
        // 155 capturas salieron sin un solo estilo.
        const kitCargado = await pagina.evaluate(() =>
          getComputedStyle(document.documentElement).getPropertyValue('--cromo').trim(),
        );
        expect(kitCargado, `${vista.id}: el ui-kit no cargó (¿raíz servida mal?)`).not.toBe('');

        const carpeta = join(EVIDENCIA, vista.id, pantalla.nombre);
        mkdirSync(carpeta, { recursive: true });
        await pagina.screenshot({
          path: join(carpeta, `${CON_BACKEND ? 'con-servicios' : 'sin-servicios'}.png`),
          fullPage: true,
        });

        const hallazgos = await auditar(pagina, pantalla);
        informe.push({
          vista: vista.id,
          grupo: vista.grupo,
          pantalla: pantalla.nombre,
          ancho: pantalla.ancho,
          hallazgos,
        });

        // El guard tiene que haber hecho su trabajo: una vista privada sin
        // sesión acaba en el login. Esto SÍ falla: es comportamiento, no
        // estética.
        if (vista.acceso !== 'publica' && !sesion) {
          expect(pagina.url()).toContain('login.html');
        }

        // UX-R3.9 · §25 — y con la persona correcta, la vista se abre de
        // verdad: ni redirige al login ni se queda en la pantalla de «sin
        // acceso». Sin esto, una captura de la consola hecha con un jugador
        // se archivaba como si fuera la vista.
        if (sesion) {
          expect(
            pagina.url(),
            `${vista.id}: ${NOMBRE[para]} debería entrar y acabó en el login`,
          ).not.toContain('login.html');
          const denegada = await pagina.evaluate(
            () => document.documentElement.dataset.acceso === 'denegado',
          );
          expect(denegada, `${vista.id}: ${NOMBRE[para]} recibió «sin acceso»`).toBe(false);
        }

        // La pantalla no puede quedarse en blanco. Una vista que no pinta
        // nada es un fallo, aunque no haya servicios.
        const texto = (await pagina.locator('body').innerText()).trim();
        expect(texto.length, `${vista.id} no pintó nada en ${pantalla.nombre}`).toBeGreaterThan(0);

        if (hallazgos.length > 0) {
          // Aviso, no fallo: ver la cabecera del archivo.
          console.warn(
            `  ⚠ ${vista.id} · ${pantalla.nombre}: ${hallazgos.map((h) => h.motivo).join(', ')}`,
          );
        }
        await pagina.close();
      } finally {
        await contexto.close();
      }
    });
  }
}

test.afterAll(() => {
  if (informe.length === 0) {
    return;
  }
  mkdirSync(EVIDENCIA, { recursive: true });
  const conHallazgos = informe.filter((f) => f.hallazgos.length > 0);
  const porMotivo = {};
  for (const fila of conHallazgos) {
    for (const h of fila.hallazgos) {
      porMotivo[h.motivo] = (porMotivo[h.motivo] ?? 0) + 1;
    }
  }
  const resumen = {
    generado: new Date().toISOString(),
    modo: CON_BACKEND ? 'con-servicios' : 'sin-servicios',
    combinaciones: informe.length,
    conHallazgos: conHallazgos.length,
    porMotivo,
    detalle: conHallazgos,
  };
  writeFileSync(join(EVIDENCIA, 'informe.json'), `${JSON.stringify(resumen, null, 2)}\n`, 'utf8');

  const lineas = [
    '# Auditoría visual automática del producto',
    '',
    `Generado: ${resumen.generado} · modo **${resumen.modo}**`,
    '',
    `${resumen.combinaciones} combinaciones vista × resolución · ${resumen.conHallazgos} con hallazgos`,
    '',
    '## Por motivo',
    '',
    '| Motivo | Veces |',
    '|---|---|',
    ...Object.entries(porMotivo)
      .sort((a, b) => b[1] - a[1])
      .map(([m, n]) => `| \`${m}\` | ${n} |`),
    '',
    '## Detalle',
    '',
    '| Vista | Resolución | Motivo | Detalle |',
    '|---|---|---|---|',
    ...conHallazgos.flatMap((f) =>
      f.hallazgos.map((h) => `| ${f.vista} | ${f.pantalla} | \`${h.motivo}\` | ${h.detalle} |`),
    ),
    '',
  ];
  writeFileSync(join(EVIDENCIA, 'INFORME.md'), lineas.join('\n'), 'utf8');
  if (existsSync(join(EVIDENCIA, 'INFORME.md'))) {
    console.log(`\n  Informe visual en docs/evidencia/laboratorio-visual/INFORME.md`);
  }
});
