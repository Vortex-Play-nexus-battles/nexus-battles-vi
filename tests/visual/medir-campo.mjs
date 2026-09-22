/**
 * Mide CA-01 de HU-JUE-017 (RF-JUE-017): que el area de juego ocupe mas del
 * 80 % del alto y del ancho utiles.
 *
 * Es un criterio numerico, asi que se mide en vez de mirarlo. Vive aqui, y no
 * dentro del arnes visual, porque es la comprobacion de UNA vista contra UN
 * requisito: meterla en el recorrido de las 31 pantallas la escondería.
 *
 *   # con el servidor estatico de `playwright.visual.config.js` levantado:
 *   npx http-server ../.. -p 4330 -c-1 --silent &
 *   node ../../tests/visual/medir-campo.mjs "<jwt de prueba>"
 *
 * El token solo tiene que dejar pasar el guard de sesion de la vista: sirve
 * uno sin firmar, como el que fabrica `identidad.js`.
 */

import { chromium } from 'playwright';

const RUTA = '/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html';
const SESION = process.argv[2];
const navegador = await chromium.launch();
const filas = [];

for (const [nombre, ancho, alto] of [
  ['1360x768 (minimo de la ficha)', 1360, 768],
  ['1440x900', 1440, 900],
  ['1920x1080', 1920, 1080],
]) {
  const ctx = await navegador.newContext({ viewport: { width: ancho, height: alto } });
  await ctx.addInitScript((t) => {
    sessionStorage.setItem('nexus.token', t);
    sessionStorage.setItem('nexus.apodoActual', 'qa');
    sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
  }, SESION);
  const p = await ctx.newPage();
  await p.goto(`http://127.0.0.1:4330${RUTA}`, { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(600);

  const m = await p.evaluate(() => {
    const campo = document.querySelector('.combate__campo').getBoundingClientRect();
    const main = document.querySelector('.combate').getBoundingClientRect();
    const cab = document.querySelector('.cabecera')?.getBoundingClientRect().height ?? 0;
    const utilAlto = window.innerHeight - cab;
    return {
      alto: +((campo.height / utilAlto) * 100).toFixed(1),
      ancho: +((campo.width / main.width) * 100).toFixed(1),
    };
  });
  filas.push([nombre, m.alto, m.ancho]);
  await ctx.close();
}
await navegador.close();

console.log('| Resolucion | % del alto util | % del ancho |');
console.log('|---|---|---|');
for (const [n, a, w] of filas) console.log(`| ${n} | ${a} % | ${w} % |`);
