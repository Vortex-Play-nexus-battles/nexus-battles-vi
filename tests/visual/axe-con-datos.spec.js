/**
 * Accesibilidad de los estados POBLADOS, no de los vacios — FI-R15.
 *
 * ## Por que hacia falta otro fichero
 *
 * `accesibilidad.spec.js` abre las 32 vistas sin servicios y pasa axe. Eso
 * cubre mucho, y deja fuera justo lo que mas riesgo tiene: **ninguna de esas
 * capturas tiene datos**. Una tarjeta de subasta activa, un contador por debajo
 * de diez segundos, un distintivo de rareza, un credito comprometido, una tabla
 * de moderacion con filas, una bandeja con avisos — nada de eso existe en la
 * pantalla vacia, y es donde viven los contrastes raros, los `role` mal puestos
 * y los objetivos tactiles pequenos.
 *
 * Dicho de otra forma: el barrido en verde demostraba que los estados vacios
 * son accesibles. De los poblados no decia nada.
 *
 * ## Como se poblan
 *
 * Interceptando la red con `page.route` y devolviendo cuerpos con la forma del
 * contrato. No se tocan los modulos ni se les inyecta nada: la vista hace sus
 * peticiones de siempre y recibe una respuesta valida. Si un dia el contrato
 * cambia y el cuerpo de aqui deja de encajar, la vista se pintara vacia y estas
 * pruebas dejaran de ejercitar lo que dicen ejercitar — por eso cada escenario
 * afirma primero que lo que queria pintar **esta en la pantalla**, y solo
 * despues pasa axe. Un banco que no llega a pintar nada es un verde que no
 * significa nada, y ese es el fallo que este fichero no puede permitirse.
 *
 * ## Que se exige
 *
 * Lo mismo que el barrido vacio: falla con `serious` y `critical` de WCAG 2.1
 * AA. No se sube el nivel aqui; se amplia la superficie.
 *
 * Son estados representativos de riesgo, no las 33 vistas por 5 anchuras: un
 * barrido exhaustivo con datos costaria minutos de CI y repetiria lo que el
 * barrido vacio ya cubre.
 */

import { AxeBuilder } from '@axe-core/playwright';
import { test, expect } from '@playwright/test';

import { PREFIJO_WEB } from './vistas.js';
import { textoSinContrasteSobreAtmosfera } from './contraste-atmosfera.js';
import { ESCENARIOS } from './escenarios-poblados.js';
import { inyectarSesion } from './identidad.js';

const NORMAS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const GRAVES = new Set(['serious', 'critical']);

/** Escritorio y movil: el objetivo tactil y algunos contrastes solo salen en uno. */
const ANCHURAS = Object.freeze([
  { nombre: 'desktop', ancho: 1440, alto: 900 },
  { nombre: 'movil', ancho: 375, alto: 812 },
]);

for (const escenario of ESCENARIOS) {
  for (const pantalla of ANCHURAS) {
    test(`${escenario.id} · ${pantalla.nombre}: ${escenario.titulo}`, async ({
      browser,
      baseURL,
    }) => {
      const contexto = await browser.newContext({
        viewport: { width: pantalla.ancho, height: pantalla.alto },
        baseURL,
      });
      try {
        await inyectarSesion(contexto, escenario.sesion());
        const pagina = await contexto.newPage();

        for (const [patron, respuesta] of escenario.rutas) {
          // Una respuesta puede depender de la peticion (p. ej. el id del
          // producto): entonces es una funcion.
          await pagina.route(patron, (ruta) =>
            ruta.fulfill(typeof respuesta === 'function' ? respuesta(ruta) : respuesta),
          );
        }
        // El canal en vivo no se simula: la vista tiene que funcionar sin el, y
        // su estado degradado tambien entra en la auditoria.
        await pagina.route('**/ws-subastas/**', (ruta) => ruta.abort());

        await pagina.goto(`/${PREFIJO_WEB}/${escenario.ruta}`, {
          waitUntil: 'domcontentloaded',
        });

        // Algunos estados solo existen tras un gesto: un dialogo que se abre,
        // una pestana que se cambia. Se hace ANTES de exigir el marcado, para
        // que la exigencia siga siendo la que decide si la prueba significa
        // algo.
        if (escenario.interaccion) {
          await escenario.interaccion(pagina);
        }

        // Primero: que el banco haya pintado de verdad. Un escenario que no
        // llega a pintar sus datos pasaria axe por no tener nada que revisar, y
        // seria el peor verde posible: el que dice que se reviso algo que no
        // estaba.
        for (const selector of escenario.exige) {
          await expect(
            pagina.locator(selector).first(),
            `${escenario.id}: el banco no pinto «${selector}». La prueba no esta ` +
              'auditando el estado poblado que dice auditar; probablemente el cuerpo ' +
              'de prueba dejo de encajar con el contrato.',
          ).toBeAttached({ timeout: 15_000 });
        }

        const resultado = await new AxeBuilder({ page: pagina }).withTags(NORMAS).analyze();
        const graves = resultado.violations.filter((v) => GRAVES.has(v.impact));

        expect(
          graves.map(
            (v) =>
              `${v.id} (${v.impact}) × ${v.nodes.length}: ${v.help}\n` +
              v.nodes
                .slice(0, 3)
                .map((n) => `        ${n.target.join(' ')}  ${n.html.slice(0, 160)}`)
                .join('\n'),
          ),
          `Accesibilidad grave en ${escenario.id} a ${pantalla.nombre}, con datos`,
        ).toEqual([]);

        // UX-GAME-1 — texto directamente sobre la atmósfera, que axe no mide.
        const sobreAtmosfera = await textoSinContrasteSobreAtmosfera(pagina);
        expect(
          sobreAtmosfera.map((h) => `${h.selector} ${h.color} ${h.contraste}:1 — «${h.texto}»`),
          `${escenario.id} a ${pantalla.nombre}: texto sin contraste sobre la atmósfera`,
        ).toEqual([]);
      } finally {
        await contexto.close();
      }
    });
  }
}
