/**
 * Nadie compone marcado con `innerHTML` y una plantilla.
 *
 * ## El defecto que esta prueba impide que vuelva
 *
 * Dos paneles de administración construían sus formularios así:
 *
 * ```js
 * form.innerHTML = `
 *   <select name="valor">${parametro.opciones
 *     .map((o) => `<option value="${o}">${o}</option>`).join('')}</select>`;
 * ```
 *
 * `parametro.opciones` y `parametro.unidad` llegan del servicio de parámetros
 * y se interpolaban **sin escapar** dentro del atributo. Una comilla o un `<`
 * en uno de esos valores rompe el marcado del panel; y el patrón en sí es el
 * que convierte un dato en código. La capa `comun/ui` existe justamente para
 * eso: `h()` y `campo()` ponen cada texto con `textContent`, que no interpreta
 * marcado, y de paso asocian la etiqueta al control.
 *
 * Vaciar un contenedor con `innerHTML = ''` no era peligroso, pero `vaciar()`
 * dice lo que hace y no pasa por el parser. Se unifica.
 *
 * ## Cómo se usa esta prueba
 *
 * Es un trinquete, no una barrida. Los módulos que TODAVÍA usan `innerHTML`
 * están listados abajo uno a uno, con quién es su dueño y por qué no se
 * migraron aquí. La prueba falla si aparece uno nuevo o si uno de la lista se
 * arregla y nadie borra su línea: en los dos sentidos obliga a mirar.
 *
 * Los ocho que quedan son de los otros dos equipos —incluido
 * `publicar-subasta.js`, que está en la lista de módulos protegidos de la
 * auditoría HU-SUB-001— y migrarlos desde fuera de su historia sería
 * exactamente lo que el Charter prohíbe.
 */

import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const raizRepo = new URL('../../../../', import.meta.url);

/**
 * Deuda conocida: módulos que todavía componen marcado con plantillas, con su
 * dueño. Quitar una línea de aquí es lo que cierra cada caso.
 *
 * @type {Record<string, string>}
 */
const PENDIENTES = Object.freeze({
  // Grupo 2 — Contenido. No se tocan sin su dueño.
  'contenido/productos/productos.js': 'grupo-2',

  // Grupo 4 — Cuentas, comercio y subastas. No se tocan sin su dueño.
  'cuentas/auditoria.js': 'grupo-4',
  // UX-R3.11 — `historial-transacciones.js` y `mis-cofres.js` salen de la lista:
  // sus dos `innerHTML = ''` pasaron a `replaceChildren()` al reescribir sus
  // estados de vacio y de fallo. El trinquete lo exigio en cuanto se arreglaron,
  // que es exactamente para lo que esta: una lista de pendientes que no
  // adelgaza no es una lista de pendientes.
  'cuentas/publicar-subasta.js': 'grupo-4, ademas protegido por HU-SUB-001',
  'cuentas/pujas.js': 'grupo-4',
  'cuentas/registro.js': 'grupo-4',
  'cuentas/tienda.js': 'grupo-4',
});

function modulosDeVista() {
  return execFileSync('git', ['ls-files', '--', 'frontend/app-web/src/**/*.js'], {
    cwd: raizRepo,
    encoding: 'utf8',
  })
    .split('\n')
    .filter(Boolean)
    .filter((ruta) => !ruta.endsWith('.test.js') && !ruta.endsWith('.spec.js'));
}

describe('el marcado se construye con nodos, no con plantillas', () => {
  test('ningún modulo asigna innerHTML', () => {
    const encontrados = [];

    for (const ruta of modulosDeVista()) {
      const js = readFileSync(new URL(ruta, raizRepo), 'utf8');
      // `.innerHTML =` en codigo; una mencion en un comentario no cuenta.
      for (const linea of js.split('\n')) {
        const sinComentario = linea.replace(/^\s*(?:\/\/|\*|\/\*).*/, '');
        if (/\.innerHTML\s*=/.test(sinComentario)) {
          encontrados.push(`${ruta.replace('frontend/app-web/src/', '')}`);
        }
      }
    }

    const nuevos = [...new Set(encontrados)].filter((ruta) => !(ruta in PENDIENTES));
    expect(nuevos).toEqual([]);
  });

  test('la lista de pendientes no se queda desactualizada', () => {
    // Si alguien arregla uno y no borra su linea, esta prueba lo dice: una
    // lista de deuda que miente es peor que no tenerla.
    const conInnerHtml = new Set();
    for (const ruta of modulosDeVista()) {
      const js = readFileSync(new URL(ruta, raizRepo), 'utf8');
      for (const linea of js.split('\n')) {
        if (/\.innerHTML\s*=/.test(linea.replace(/^\s*(?:\/\/|\*|\/\*).*/, ''))) {
          conInnerHtml.add(ruta.replace('frontend/app-web/src/', ''));
        }
      }
    }

    const yaArreglados = Object.keys(PENDIENTES).filter((ruta) => !conInnerHtml.has(ruta));
    expect(yaArreglados).toEqual([]);
  });

  test('tampoco insertAdjacentHTML ni document.write', () => {
    const encontrados = [];

    for (const ruta of modulosDeVista()) {
      const js = readFileSync(new URL(ruta, raizRepo), 'utf8');
      if (/insertAdjacentHTML|document\.write\s*\(/.test(js)) {
        encontrados.push(ruta.replace('frontend/app-web/src/', ''));
      }
    }

    expect(encontrados).toEqual([]);
  });
});
