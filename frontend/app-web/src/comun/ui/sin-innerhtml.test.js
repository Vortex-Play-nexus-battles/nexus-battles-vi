/**
 * Guardián: ningún módulo interpola datos dentro de `innerHTML` — UX-R2.8.
 *
 * ## Por qué
 *
 * `innerHTML` con una plantilla que lleva `${...}` es la forma más directa de
 * meter un XSS en esta aplicación: basta con que uno de esos valores venga del
 * servidor o lo haya escrito una persona. Ya pasó dos veces en este
 * repositorio:
 *
 *  - PR-UX-8: el interceptor de errores pintaba el `detail` del servidor en un
 *    aviso flotante con `innerHTML`.
 *  - UX-R2.8: `historial-transacciones.js` metía `comprobanteUrl` dentro de un
 *    `href`, así que un comprobante con `javascript:` se ejecutaba al pulsar
 *    «Ver»; y `auditoria.js` interpolaba el motivo de una sanción —texto que
 *    escribe una persona— nueve veces, dos de ellas dentro de `title="..."`,
 *    donde una comilla cierra el atributo.
 *
 * Las dos se arreglaron. Este guardián existe para que no haya una tercera.
 *
 * ## Qué se permite y qué no
 *
 * Lo que se persigue es la **interpolación**, que es de donde viene el
 * peligro. Se permite:
 *
 *  - `elemento.innerHTML = ''` para vaciar.
 *  - Una plantilla **literal sin `${...}`**: marcado fijo escrito a mano, que
 *    no puede inyectar nada porque no entra ningún dato.
 *
 * Falla cualquier `innerHTML` que reciba una interpolación, una variable o el
 * resultado de una función: ahí no se puede saber, leyendo la línea, si lo que
 * entra viene del servidor. Se arregla construyendo nodos — `comun/ui/dom.js`
 * está exactamente para eso.
 *
 * El marcado fijo sigue siendo deuda (una vista no debería declarar su HTML en
 * una cadena de JavaScript), pero es deuda de estructura, no un agujero de
 * seguridad. Se limpia en UX-R2.10.
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

/**
 * Las asignaciones que YA se miraron una por una, con el motivo.
 *
 * No es una lista de excepciones cómoda: es el registro de lo que alguien
 * comprobó. Cada entrada dice por qué esa línea no puede inyectar nada. Una
 * asignación nueva **no** entra aquí sin revisarla, y si la línea se mueve, el
 * guardián vuelve a fallar — que es justo lo que se quiere cuando se toca un
 * archivo con `innerHTML` dentro.
 *
 * Las de `pujas.js` son la deuda grande y conocida (2.297 líneas, UX-R2.8c):
 * el contenido se arma en cinco generadores que sí interpolan datos de la
 * subasta. Está anotado, no tapado.
 *
 * R9.6a — las cuatro líneas de `pujas.js` se corrieron 24 posiciones porque el
 * canal STOMP pasó a acreditarse y eso añadió código ARRIBA de ellas. Ninguna
 * asignación cambió: cambió su número de línea. El guardián hizo exactamente
 * lo que promete («si la línea se mueve, vuelve a fallar»), y revisarlas de
 * nuevo confirmó que siguen siendo las mismas cuatro, con el mismo
 * saneamiento. Queda dicho para quien mantenga esto: la clave por número de
 * línea es deliberadamente incómoda, y el precio es este — una revisión
 * obligatoria cada vez que alguien toca el archivo por encima.
 */
const REVISADOS = new Map([
  ['contenido/productos/productos.js:159', 'plantilla() devuelve marcado fijo, sin datos'],
  ['cuentas/publicar-subasta.js:68', 'plantilla fija del formulario, sin interpolación'],
  ['cuentas/registro.js:235', 'cadena literal fija, sin interpolación'],
  ['cuentas/tienda.js:141', 'plantilla fija; el color pasó a data-tipo en UX-R2.8'],

  ['cuentas/tienda.js:226', 'cadena literal fija del carrito vacío'],
  ['cuentas/tienda.js:236', 'plantilla fija; los datos entran luego por textContent'],
  ['cuentas/pujas.js:1258', 'plantilla fija del estado de carga'],
  ['cuentas/pujas.js:1276', 'estado de error: el único dato va por esc() (UX-R2.8c)'],
  ['cuentas/pujas.js:1292', 'plantilla fija del estado vacío'],
  [
    'cuentas/pujas.js:1332',
    'DELIBERADO y SANEADO (UX-R2.8c): las 20 interpolaciones con datos del ' +
      'servidor pasan por esc(); pujas.test.js lo comprueba con cargas reales. ' +
      'La estructura (2.297 líneas de plantilla) se mueve en UX-R2.10.',
  ],
  [
    'cuentas/registro.js:222',
    'DEUDA CONOCIDA: interpola una URL de objeto local del selector de archivos',
  ],
]);

/** Todos los `.js` de producción (las pruebas montan HTML a propósito). */
function modulos(raiz) {
  const salida = [];
  for (const entrada of readdirSync(raiz)) {
    const completa = join(raiz, entrada);
    if (statSync(completa).isDirectory()) {
      salida.push(...modulos(completa));
    } else if (entrada.endsWith('.js') && !entrada.endsWith('.test.js')) {
      salida.push(completa);
    }
  }
  return salida;
}

test('nadie asigna a innerHTML algo que no sea la cadena vacía', () => {
  const culpables = [];

  for (const ruta of modulos(SRC)) {
    const codigo = readFileSync(ruta, 'utf8');
    const nombre = ruta.slice(SRC.length + 1).replace(/\\/g, '/');

    codigo.split('\n').forEach((linea, indice) => {
      // Solo asignaciones reales: un comentario que habla de `innerHTML`
      // —y hay varios, explicando por qué NO se usa— no es una asignación.
      const sinComentario = linea.replace(/^\s*(\/\/|\*).*$/, '');
      const asigna = /\.innerHTML\s*=(?!=)/.test(sinComentario);
      if (!asigna) {
        return;
      }
      // Vaciar es legítimo.
      if (/\.innerHTML\s*=\s*(''|""|``)\s*;?\s*$/.test(sinComentario)) {
        return;
      }

      const donde = `${nombre}:${indice + 1}`;
      if (REVISADOS.has(donde)) {
        return;
      }
      culpables.push(`${donde} → ${linea.trim().slice(0, 70)}`);
    });
  }

  // Si esto falla, el informe de Jest enseña el archivo, la línea y el código.
  expect(culpables).toEqual([]);
});
