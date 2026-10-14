// @ts-check
/**
 * El asistente en AWS DEV, después de cada despliegue — §7.4.4 (consultas en
 * vivo) y §7.4.11 (respuestas en menos de 2 s).
 *
 * Una jugadora de QA nueva (`canario_chat…@nexus.test`, verificada con el
 * código de su buzón, sin SQL) le pregunta al asistente por lo suyo con su
 * token, por el borde público: inventario, misiones, subastas, avisos,
 * transacciones, una búsqueda en su inventario y el informe de actividad. Y,
 * sin sesión, lo que pregunta un visitante: un tema de la base, el catálogo,
 * una navegación y un dato privado.
 *
 * Cada respuesta se mide desde el runner (conexión ya abierta) y se anota
 * `CANARIO|chatbot|<tipo>|<ms>` en la bitácora: se acumulan corrida tras
 * corrida. Lo que se afirma:
 *   - cada pregunta responde 200 y ninguna consulta en vivo dice «no pude
 *     consultar» (los servicios que consulta están desplegados);
 *   - el p95 de todas las respuestas medidas queda bajo 2000 ms (7.4.11),
 *     con al menos veinte muestras: no se declara un tiempo por una sola
 *     petición.
 *
 * Respeta los límites del asistente (D-CHA-04): una sesión de jugador y dos
 * de visitante, menos de veinte mensajes por minuto cada una.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe } from './ayudantes/cuentas.js';

const AWS = process.env.E2E_AWS ?? 'http://35.168.124.119';
const CLAVE = 'Contrasena-Canario-2026';
const MARCA = Date.now().toString(36);
const LIMITE_MS = 2_000;

const DEL_JUGADOR = [
  {
    tipo: 'inventario',
    pregunta: '¿Qué tengo en mi inventario?',
    dice: /elemento\(s\) en tu inventario|inventario está vacío/,
  },
  {
    tipo: 'misiones',
    pregunta: '¿Tengo alguna misión activa?',
    dice: /misiones en curso|misión\(es\) en curso/,
  },
  { tipo: 'subastas', pregunta: '¿Cómo van mis subastas?', dice: /Vas ganando/ },
  {
    tipo: 'notificaciones',
    pregunta: '¿Tengo notificaciones?',
    dice: /notificaciones sin leer|notificación\(es\) sin leer/,
  },
  {
    tipo: 'transacciones',
    pregunta: '¿Cuáles son mis últimas transacciones?',
    dice: /movimientos de créditos/,
  },
  { tipo: 'busqueda', pregunta: 'busca espada en mi inventario', dice: /«espada»/ },
  { tipo: 'informe', pregunta: 'dame un informe de mi actividad', dice: /./ },
];

const DEL_VISITANTE = [
  { tipo: 'base', pregunta: '¿Cómo me registro?', dice: /Para crear tu cuenta/ },
  { tipo: 'base', pregunta: '¿Cuáles son las reglas del juego?', dice: /reglas básicas/ },
  { tipo: 'catalogo', pregunta: '¿Qué productos hay en la tienda?', dice: /catálogo|Tienda/ },
  { tipo: 'navegacion', pregunta: 'llévame a la subasta', dice: /Subasta/ },
  { tipo: 'sin-sesion', pregunta: 'mis últimas transacciones', dice: /inicies sesión/ },
];

function percentil(muestras, p) {
  const orden = [...muestras].sort((a, b) => a - b);
  const k = ((orden.length - 1) * p) / 100;
  const f = Math.floor(k);
  const c = Math.min(f + 1, orden.length - 1);
  return orden[f] + (orden[c] - orden[f]) * (k - f);
}

test.describe('Asistente en AWS DEV (§7.4.4, §7.4.11)', () => {
  test.describe.configure({ mode: 'serial' });

  test('consultas en vivo del jugador y respuestas del visitante, cada una medida', async () => {
    test.setTimeout(300_000);
    const api = await apiRequest.newContext({ baseURL: AWS });
    const muestras = [];
    try {
      const jugadora = await sesionDe(api, `canario_chat${MARCA}`, { clave: CLAVE, base: AWS });

      /** Pregunta, mide y comprueba. */
      async function preguntar(cabeceras, { tipo, pregunta, dice }) {
        const inicio = performance.now();
        const r = await api.post('/api/v1/chat/mensajes', {
          headers: cabeceras,
          data: { contenido: pregunta },
        });
        const ms = Math.round(performance.now() - inicio);
        const cuerpo = await r.text();
        expect(r.status(), `${tipo}: ${cuerpo.slice(0, 200)}`).toBe(200);
        const contenido = JSON.parse(cuerpo).contenido ?? '';
        expect(contenido, tipo).toMatch(dice);
        expect(contenido, `${tipo}: un servicio no respondió`).not.toMatch(/No pude consultar/);
        console.log(`CANARIO|chatbot|${tipo}|${ms}`);
        muestras.push(ms);
      }

      // Una primera petición abre la conexión: no se mide el saludo TLS.
      await api.get('/api/v1/chat/sugerencias?q=reg');

      const comoJugadora = { Authorization: `Bearer ${jugadora.token}` };
      for (let vuelta = 0; vuelta < 2; vuelta += 1) {
        for (const consulta of DEL_JUGADOR) {
          await preguntar(comoJugadora, consulta);
        }
      }
      for (let vuelta = 0; vuelta < 2; vuelta += 1) {
        const visitante = { 'X-Id-Sesion-Anonima': `visitante-canario-${MARCA}-${vuelta}` };
        for (const consulta of DEL_VISITANTE) {
          await preguntar(visitante, consulta);
        }
      }
    } finally {
      await api.dispose();
    }

    expect(muestras.length).toBeGreaterThanOrEqual(20);
    const p50 = Math.round(percentil(muestras, 50));
    const p95 = Math.round(percentil(muestras, 95));
    const p99 = Math.round(percentil(muestras, 99));
    console.log(`CANARIO|chatbot|resumen|n=${muestras.length} p50=${p50} p95=${p95} p99=${p99}`);
    expect(p95, `p95 de ${muestras.length} respuestas (p50=${p50}, p99=${p99})`).toBeLessThan(
      LIMITE_MS,
    );
  });
});
