/**
 * Guardián: ninguna vista usa los diálogos del navegador — UXC-7.
 *
 * `window.confirm()`, `window.alert()` y `window.prompt()` no se pueden
 * estilar, bloquean la página entera, no devuelven el foco a donde estaba, se
 * leen mal con lector de pantalla y algunos navegadores los desactivan solos
 * tras el segundo. §7.3.9 pide «confirmación adicional para acciones
 * críticas»: eso es `confirmarCritico()` del kit, no un `confirm()`.
 *
 * Al escribir esta prueba había seis `window.confirm` (gestión de usuarios y
 * lista negra) y un `window.prompt` (editar un término de la lista negra).
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

function archivos(raiz) {
  const salida = [];
  for (const entrada of readdirSync(raiz)) {
    const completa = join(raiz, entrada);
    if (statSync(completa).isDirectory()) {
      salida.push(...archivos(completa));
    } else if (/\.(js|html)$/.test(entrada) && !/\.(test|spec)\.js$/.test(entrada)) {
      salida.push(completa);
    }
  }
  return salida;
}

/** Quita comentarios de línea y de bloque para no denunciar la documentación. */
function sinComentarios(texto) {
  return texto.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');
}

/**
 * UXC-9 — la primera versión solo veía `confirm(` y `window.confirm(`. Se le
 * escapó `globalThis.prompt?.('Motivo de la cancelacion…')` en torneos: por
 * `globalThis.` y por el `?.`. Ahora cubre los objetos globales y la llamada
 * opcional, y sigue sin confundir `confirmar()` ni `algo.prompt()`.
 */
const NATIVO =
  /(^|[^.\w$])(?:(?:window|globalThis|self|top|parent)\s*\.\s*)?(confirm|alert|prompt)\s*(?:\?\.\s*)?\(/;

test('el detector ve las formas que se usaron y no confunde las del kit', () => {
  for (const culpable of [
    "window.confirm('¿Seguro?')",
    "globalThis.prompt?.('Motivo')",
    "alert('hecho')",
    'self.prompt ?.("x")',
  ]) {
    expect(NATIVO.test(culpable)).toBe(true);
  }
  for (const inocente of [
    'await confirmar({ titulo })',
    'instalacion.prompt()',
    'confirmarCritico({})',
    'alertar(zona)',
  ]) {
    expect(NATIVO.test(inocente)).toBe(false);
  }
});

test('ninguna vista abre confirm(), alert() ni prompt() del navegador', () => {
  const culpables = [];
  for (const ruta of archivos(SRC)) {
    const lineas = sinComentarios(readFileSync(ruta, 'utf8')).split('\n');
    lineas.forEach((linea, i) => {
      if (NATIVO.test(linea)) {
        culpables.push(`${relative(SRC, ruta)}:${i + 1}: ${linea.trim()}`);
      }
    });
  }
  expect(culpables).toEqual([]);
});
