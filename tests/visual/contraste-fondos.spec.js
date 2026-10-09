/**
 * Contraste del texto sobre los fondos ilustrados — HU-UX-002 (#925).
 *
 * ## El hueco que cubre
 *
 * `contraste-atmosfera.js` mide el texto que cae sobre la atmósfera contra los
 * dos colores de su degradado. Con una ilustración detrás eso ya no basta: el
 * fondo real de cada letra es la escena bajo su velo, distinta en cada vista y
 * en cada ancho (en 1366 la tarjeta tapa lo que en 1920 queda a la vista). Y
 * axe-core no lo mide: un texto sobre una imagen lo deja como «incomplete».
 *
 * ## Cómo mide
 *
 * 1. Abre cada vista con `data-fondo` —con datos si el laboratorio tiene un
 *    escenario poblado para ella; si no, en su estado sin servicios— a
 *    1920 × 1080, 1366 × 768 y 375 × 812.
 * 2. Espera a que la escena esté descargada y decodificada: medir el velo
 *    sobre el cromo daría verde sin haber medido nada.
 * 3. Anota las cajas del texto visible que no está sobre una superficie propia
 *    (ningún antepasado con fondo opaco o imagen de fondo), el mismo criterio
 *    que `contraste-atmosfera.js`.
 * 4. Guarda la captura como evidencia y oculta el contenido para fotografiar
 *    solo la escena con su velo.
 * 5. En el navegador decodifica esa segunda captura en un lienzo y, para cada
 *    caja, calcula el contraste WCAG del color del texto contra cada píxel de
 *    detrás.
 * 6. Exige AA en el percentil 1: 4,5:1 en texto normal y 3:1 en texto grande
 *    (≥ 24 px, o ≥ 18,66 px en negrita). El percentil 1 y no el mínimo porque
 *    una chispa de dos píxeles bajo una letra no es su fondo: si el 1 % más
 *    claro de lo que hay detrás ya da AA, el texto se lee.
 *
 * En el combate mide además los nombres de los héroes sobre la arena
 * (`.campo__nombre`), con una partida de seis.
 *
 * Deja en `docs/evidencia/fondos/<escena>/` una captura por vista y ancho
 * (`<vista>-<ancho>.jpg`) y un informe por vista (`<vista>.md`). Por vista y
 * no por escena a propósito: cada grupo de pantallas llega en su propio PR, y
 * dos PR que reescribieran el mismo informe de una escena chocarían.
 *
 * A menos de 768 px el combate retira el campo (componentes.css, «degradar no
 * es apagar»): los nombres sobre la arena solo se miden en escritorio.
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

import { expect, test } from '@playwright/test';

import { simularCanal } from './canal-simulado.js';
import {
  ESCENARIOS,
  ESCENARIOS_PLAYER07B,
  ESCENARIOS_UXC8,
  ESCENARIOS_UXC9,
  sesionDe,
} from './escenarios-poblados.js';
import { inyectarSesion } from './identidad.js';
import { PREFIJO_WEB, VISTAS } from './vistas.js';

// Playwright transpila estos specs a CommonJS (no hay package.json con
// "type": "module" en `tests/`): `import.meta` no existe aquí y `__dirname`
// sí. Mismo patrón que `auditoria-visual.spec.js`.
const AQUI =
  typeof __dirname === 'undefined' ? resolve(process.cwd(), '../../tests/visual') : __dirname;
const RAIZ = join(AQUI, '..', '..');
const EVIDENCIA = join(RAIZ, 'docs', 'evidencia', 'fondos');

const ANCHOS = Object.freeze([
  { nombre: '1920', ancho: 1920, alto: 1080 },
  { nombre: '1366', ancho: 1366, alto: 768 },
  { nombre: 'movil', ancho: 375, alto: 812 },
]);

const POBLADOS = [ESCENARIOS, ESCENARIOS_UXC8, ESCENARIOS_UXC9, ESCENARIOS_PLAYER07B].flat();

/** Casos que no salen solos de la vista: el campo de combate vive en una partida. */
const CAMPOS = Object.freeze({ 'sala-batalla': 'combate-seis' });

const sinConsulta = (ruta) => ruta.split(/[?#]/)[0];

function fondoDe(ruta) {
  const html = readFileSync(join(RAIZ, PREFIJO_WEB, ruta), 'utf8');
  return html.match(/<body\b[^>]*\sdata-fondo="([^"]+)"/)?.[1] ?? null;
}

const CASOS = VISTAS.flatMap((vista) => {
  const fondo = fondoDe(vista.ruta);
  if (!fondo) {
    return [];
  }
  const escenario = POBLADOS.find((e) => sinConsulta(e.ruta) === vista.ruta) ?? null;
  const casos = [{ vista, fondo, escenario, campo: false }];
  if (CAMPOS[vista.id]) {
    const partida = POBLADOS.find((e) => e.id === CAMPOS[vista.id]);
    casos.push({ vista, fondo: 'arena', escenario: partida, campo: true });
  }
  return casos;
});

/** Filas del informe; al terminar se escribe uno por vista. */
const informe = [];

const ORDEN_ANCHOS = ANCHOS.map((p) => p.nombre);

test.afterAll(() => {
  const porCaso = new Map();
  for (const fila of informe) {
    const clave = `${fila.fondo}/${fila.caso}`;
    if (!porCaso.has(clave)) {
      porCaso.set(clave, []);
    }
    porCaso.get(clave).push(fila);
  }
  for (const [clave, filas] of porCaso) {
    const [fondo, caso] = clave.split('/');
    const carpeta = join(EVIDENCIA, fondo);
    mkdirSync(carpeta, { recursive: true });
    const cuerpo = filas
      .sort((a, b) => ORDEN_ANCHOS.indexOf(a.ancho) - ORDEN_ANCHOS.indexOf(b.ancho))
      .map((f) =>
        [
          f.ancho,
          f.estado,
          f.cajas,
          f.peor ? `${f.peor.texto} (\`${f.peor.sel}\`)` : '—',
          f.peor ? f.peor.p1.toFixed(2) : '—',
          f.peor ? f.peor.umbral : '—',
          f.fallos === 0 ? 'cumple' : `**${f.fallos} por debajo**`,
        ].join(' | '),
      )
      .map((fila) => `| ${fila} |`)
      .join('\n');
    writeFileSync(
      join(carpeta, `${caso}.md`),
      `# Contraste de «${caso}» sobre la escena «${fondo}»\n\n` +
        'Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae ' +
        'directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la ' +
        'escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor ' +
        'texto» es la caja con el percentil 1 más bajo en ese ancho.\n\n' +
        '| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |\n' +
        '|---|---|---|---|---|---|---|\n' +
        `${cuerpo}\n\n` +
        `Capturas: \`${caso}-<ancho>.jpg\` en esta carpeta.\n`,
    );
  }
});

/**
 * Cajas del texto que cae directamente sobre la escena. Corre en el navegador:
 * no puede usar nada de fuera.
 */
function cajasDeTexto(soloCampo) {
  const parsear = (c) => {
    const srgb = c.match(/color\(srgb\s+([^)]+)\)/);
    if (srgb) {
      const [canales, alfa] = srgb[1].split('/');
      const [r, g, b] = canales
        .trim()
        .split(/\s+/)
        .map((v) => Number(v) * 255);
      return { r, g, b, a: alfa === undefined ? 1 : Number(alfa) };
    }
    const m = c.match(/rgba?\(([^)]+)\)/);
    if (!m) {
      return null;
    }
    const p = m[1]
      .split(/[\s,/]+/)
      .filter(Boolean)
      .map(Number);
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
  };
  const conSuperficie = (el) => {
    const s = getComputedStyle(el);
    const fondo = parsear(s.backgroundColor);
    return (fondo && fondo.a > 0.9) || s.backgroundImage !== 'none';
  };
  const sobreEscena = (el) => {
    for (let n = el; n && n !== document.body; n = n.parentElement) {
      if (soloCampo && n.classList.contains('combate__campo')) {
        return true;
      }
      if (conSuperficie(n)) {
        return false;
      }
    }
    return !soloCampo;
  };
  const visible = (el) => {
    for (let n = el; n && n !== document.documentElement; n = n.parentElement) {
      const s = getComputedStyle(n);
      if (s.display === 'none' || s.visibility === 'hidden' || Number(s.opacity) === 0) {
        return false;
      }
    }
    return true;
  };

  const cajas = [];
  const paseo = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  for (let nodo = paseo.nextNode(); nodo; nodo = paseo.nextNode()) {
    const texto = nodo.textContent.trim();
    const el = nodo.parentElement;
    if (!texto || !el || ['SCRIPT', 'STYLE', 'TEMPLATE', 'NOSCRIPT'].includes(el.tagName)) {
      continue;
    }
    if (soloCampo && !el.closest('.campo__nombre')) {
      continue;
    }
    if (!sobreEscena(el) || !visible(el)) {
      continue;
    }
    const estilo = getComputedStyle(el);
    const color = parsear(estilo.color);
    if (!color || color.a === 0) {
      continue;
    }
    const tamano = parseFloat(estilo.fontSize);
    const grande = tamano >= 24 || (tamano >= 18.66 && Number(estilo.fontWeight) >= 700);
    const clases =
      typeof el.className === 'string' && el.className.trim()
        ? `.${el.className.trim().split(/\s+/)[0]}`
        : '';
    const rango = document.createRange();
    rango.selectNodeContents(nodo);
    for (const r of rango.getClientRects()) {
      // Las cajas de 1 px son texto para lectores de pantalla (recortado).
      if (r.width < 3 || r.height < 3 || r.bottom <= 0 || r.top >= innerHeight) {
        continue;
      }
      cajas.push({
        sel: el.tagName.toLowerCase() + clases,
        texto: texto.slice(0, 48),
        x: r.x,
        y: r.y,
        w: r.width,
        h: r.height,
        color,
        umbral: grande ? 3 : 4.5,
      });
    }
  }
  return cajas;
}

/** Contraste de cada caja contra los píxeles de la escena. Corre en el navegador. */
async function medirContraste({ escena, cajas }) {
  const imagen = new Image();
  imagen.src = `data:image/png;base64,${escena}`;
  await imagen.decode();
  const lienzo = document.createElement('canvas');
  lienzo.width = imagen.naturalWidth;
  lienzo.height = imagen.naturalHeight;
  const ctx = lienzo.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(imagen, 0, 0);
  const lineal = (c) => {
    const v = c / 255;
    return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  };
  const luminancia = (r, g, b) => 0.2126 * lineal(r) + 0.7152 * lineal(g) + 0.0722 * lineal(b);
  const medidas = [];
  for (const caja of cajas) {
    const x0 = Math.max(0, Math.floor(caja.x));
    const y0 = Math.max(0, Math.floor(caja.y));
    const x1 = Math.min(lienzo.width, Math.ceil(caja.x + caja.w));
    const y1 = Math.min(lienzo.height, Math.ceil(caja.y + caja.h));
    if (x1 <= x0 || y1 <= y0) {
      continue;
    }
    const { data } = ctx.getImageData(x0, y0, x1 - x0, y1 - y0);
    const { r, g, b, a } = caja.color;
    const ratios = new Float64Array(data.length / 4);
    for (let i = 0; i < ratios.length; i += 1) {
      const fr = data[i * 4];
      const fg = data[i * 4 + 1];
      const fb = data[i * 4 + 2];
      // Un color de texto con transparencia se ve mezclado con lo de detrás.
      const lt = luminancia(a * r + (1 - a) * fr, a * g + (1 - a) * fg, a * b + (1 - a) * fb);
      const lf = luminancia(fr, fg, fb);
      ratios[i] = (Math.max(lt, lf) + 0.05) / (Math.min(lt, lf) + 0.05);
    }
    ratios.sort();
    medidas.push({
      sel: caja.sel,
      texto: caja.texto,
      umbral: caja.umbral,
      p1: ratios[Math.floor((ratios.length - 1) * 0.01)],
      minimo: ratios[0],
    });
  }
  return medidas;
}

/** La escena (o la arena) tiene que estar descargada y decodificada. */
function urlDeEscena(soloCampo) {
  const imagen = soloCampo
    ? getComputedStyle(document.querySelector('.combate__campo')).backgroundImage
    : getComputedStyle(document.body, '::before').backgroundImage;
  return imagen.match(/url\("([^"]+)"\)/)?.[1] ?? null;
}

for (const caso of CASOS) {
  const anchos = caso.campo ? ANCHOS.filter((p) => p.ancho >= 768) : ANCHOS;
  for (const pantalla of anchos) {
    const nombre = `${caso.vista.id}${caso.campo ? '-campo' : ''}`;
    // `@fondos`: visual.yml corre estas pruebas en su propio job, en paralelo.
    test(
      `${caso.fondo} · ${nombre} · ${pantalla.nombre}`,
      { tag: '@fondos' },
      async ({ browser, baseURL }) => {
        const contexto = await browser.newContext({
          viewport: { width: pantalla.ancho, height: pantalla.alto },
          baseURL,
        });
        try {
          const sesion =
            caso.escenario?.sesion?.() ??
            (caso.vista.acceso === 'publica' ? null : sesionDe('qa_fondos', 'JUGADOR'));
          await inyectarSesion(contexto, sesion);
          const pagina = await contexto.newPage();
          for (const [patron, respuesta] of caso.escenario?.rutas ?? []) {
            await pagina.route(patron, (ruta) =>
              ruta.fulfill(typeof respuesta === 'function' ? respuesta(ruta) : respuesta),
            );
          }
          await pagina.route('**/ws-subastas/**', (ruta) => ruta.abort());
          if (caso.escenario?.canal) {
            await simularCanal(pagina, caso.escenario.canal);
          }
          await pagina.goto(`/${PREFIJO_WEB}/${caso.escenario?.ruta ?? caso.vista.ruta}`, {
            waitUntil: 'domcontentloaded',
          });
          if (caso.escenario?.interaccion) {
            await caso.escenario.interaccion(pagina);
          }
          for (const selector of caso.escenario?.exige ?? []) {
            await expect(pagina.locator(selector).first()).toBeAttached({ timeout: 15_000 });
          }
          if (caso.campo) {
            await expect(pagina.locator('.campo__nombre').first()).toBeVisible({ timeout: 15_000 });
          }

          const url = await pagina.evaluate(urlDeEscena, caso.campo);
          expect(
            url,
            `${nombre}: la vista declara data-fondo y no pinta ninguna escena`,
          ).toBeTruthy();
          const cargada = await pagina.evaluate(async (src) => {
            const prueba = new Image();
            prueba.src = src;
            try {
              await prueba.decode();
              return true;
            } catch {
              return false;
            }
          }, url);
          expect(cargada, `${nombre}: la escena ${url} no se pudo descargar`).toBe(true);
          await pagina.waitForTimeout(700);

          const cajas = await pagina.evaluate(cajasDeTexto, caso.campo);

          const carpeta = join(EVIDENCIA, caso.fondo);
          mkdirSync(carpeta, { recursive: true });
          await pagina.screenshot({
            path: join(carpeta, `${nombre}-${pantalla.nombre}.jpg`),
            type: 'jpeg',
            quality: 60,
          });

          await pagina.addStyleTag({
            content: caso.campo
              ? '.campo__puesto { visibility: hidden !important; }'
              : 'body > * { visibility: hidden !important; }',
          });
          await pagina.waitForTimeout(150);
          const escena = (await pagina.screenshot({ type: 'png' })).toString('base64');
          const medidas = await pagina.evaluate(medirContraste, { escena, cajas });

          const fallos = medidas.filter((m) => m.p1 < m.umbral);
          const peor = [...medidas].sort((a, b) => a.p1 / a.umbral - b.p1 / b.umbral)[0] ?? null;
          informe.push({
            fondo: caso.fondo,
            caso: nombre,
            ancho: pantalla.nombre,
            estado: caso.escenario ? `con datos (\`${caso.escenario.id}\`)` : 'sin servicios',
            cajas: medidas.length,
            peor,
            fallos: fallos.length,
          });

          expect(
            fallos.map((f) => `${f.sel} «${f.texto}»: ${f.p1.toFixed(2)}:1 < ${f.umbral}:1`),
            `${nombre} a ${pantalla.nombre}: texto por debajo de AA sobre la escena «${caso.fondo}»`,
          ).toEqual([]);
        } finally {
          await contexto.close();
        }
      },
    );
  }
}
