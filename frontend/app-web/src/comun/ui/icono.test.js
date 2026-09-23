/**
 * El sprite y la lista `ICONOS` tienen que decir lo mismo — UX-R4.4.
 *
 * `icono()` y `iconoHtml()` validan contra `ICONOS` y fallan si les piden un
 * nombre que no esta en la lista. Lo que nadie comprobaba es lo contrario:
 * que cada nombre de la lista exista de verdad dentro del sprite. Un nombre
 * en la lista sin simbolo detras pasa las dos validaciones, no lanza nada y
 * pinta un hueco transparente del tamano del icono. Se ve como un despiste de
 * maquetacion, no como un error, y por eso aguanta hasta que alguien mira la
 * pantalla con atencion.
 *
 * Importa ahora porque `cofre` es el primer simbolo que NO viene exportado de
 * Figma: se dibujo a mano en este repositorio, y ninguna prueba pintaba una
 * tarjeta de cofre. Sin esto, escribir mal el identificador en el sprite o en
 * la lista no lo habria notado nadie hasta abrir «Mis cofres» en el navegador.
 */

import { readFileSync } from 'node:fs';
import { ICONOS, rutaDelSprite } from './icono.js';

const sprite = readFileSync(
  new URL('../../../../../shared/ui-kit/iconos/sprite.svg', import.meta.url),
  'utf8',
);

/** Los `id` que el sprite declara, en el orden en que aparecen. */
function simbolosDelSprite() {
  return [...sprite.matchAll(/<symbol[^>]*\bid="([^"]+)"/g)].map((coincidencia) => coincidencia[1]);
}

describe('el sprite y la lista de iconos dicen lo mismo', () => {
  test('cada nombre de ICONOS existe como simbolo en el sprite', () => {
    const enElSprite = new Set(simbolosDelSprite());
    expect(ICONOS.filter((nombre) => !enElSprite.has(nombre))).toEqual([]);
  });

  test('cada simbolo del sprite esta en ICONOS', () => {
    const enLaLista = new Set(ICONOS);
    expect(simbolosDelSprite().filter((nombre) => !enLaLista.has(nombre))).toEqual([]);
  });

  test('ningun simbolo esta declarado dos veces', () => {
    const vistos = simbolosDelSprite();
    const repetidos = vistos.filter((nombre, i) => vistos.indexOf(nombre) !== i);
    expect(repetidos).toEqual([]);
  });

  /**
   * El trazo es lo que hace que treinta y un dibujos distintos parezcan una
   * sola familia. `cofre` se dibujo a mano y tenia que igualarlo: misma
   * reticula de 24, mismo grosor de 2, mismas terminaciones redondas.
   */
  test('todos los simbolos comparten reticula, grosor y terminaciones', () => {
    const fuera = [];
    for (const [, atributos, nombre] of sprite.matchAll(/<symbol([^>]*\bid="([^"]+)"[^>]*)>/g)) {
      const esperado = {
        viewBox: 'viewBox="0 0 24 24"',
        trazo: 'stroke="currentColor"',
        grosor: 'stroke-width="2"',
        remate: 'stroke-linecap="round"',
        union: 'stroke-linejoin="round"',
      };
      for (const [que, texto] of Object.entries(esperado)) {
        if (!atributos.includes(texto)) {
          fuera.push(`${nombre} → ${que}`);
        }
      }
    }
    expect(fuera).toEqual([]);
  });

  test('la ruta del sprite se calcula desde el modulo, no desde quien lo llama', () => {
    expect(rutaDelSprite()).toMatch(/shared\/ui-kit\/iconos\/sprite\.svg$/);
  });
});
