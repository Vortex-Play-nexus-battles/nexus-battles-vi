// @ts-check
/**
 * El asistente para un visitante (§7.4), de punta a punta por el borde con
 * ms-chatbot y su base de verdad.
 *
 * El recorrido es el de la revisión final del 7.4: desde /login abre el
 * asistente → pregunta cómo registrarse → pregunta una regla del juego →
 * minimiza y restaura la ventana sin perder nada → sigue el enlace de la
 * respuesta a /registro → allí la conversación sigue (historial de la sesión
 * de visitante) → pide un dato privado y el asistente le dice que hace falta
 * una sesión, sin consultar nada.
 *
 * Y por la API, lo que un visitante NO puede hacer (7.4.8): leer el historial
 * de otra sesión, ni hacerse pasar por un jugador con una cabecera o con la
 * vista desde la que pregunta (el contexto de la vista no autoriza nada).
 *
 * ms-chatbot.yaml 1.12.0 (RF-CHA-007, actor Visitante): lo que no entiende
 * también le abre al visitante una solicitud de soporte, que ve en «Tus
 * solicitudes» con su sesión y que nadie más ve.
 *
 * ms-chatbot.yaml 1.15.0 (§7.4.3 «sugerencias proactivas»): al abrirlo, el
 * asistente le propone algo y dice por qué; antes de preguntar, por la sección
 * donde está, y al volver, por lo que ya preguntó.
 *
 * ms-chatbot.yaml 1.17.0 (§7.4.2 «español/english» y «preguntas relacionadas
 * basadas en el contexto»): a quien pregunta en inglés, las preguntas
 * relacionadas le salen en inglés, pulsar una sigue en inglés y el
 * autocompletado le sugiere en inglés.
 */

import { test, expect, request as apiRequest } from '@playwright/test';
import { AxeBuilder } from '@axe-core/playwright';

import {
  ESPERA_DE_RESPUESTA_MS,
  abrirAsistente,
  destinosDe,
  enlaceA,
  preguntar,
} from './ayudantes/asistente.js';
import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';
import { conLaPaginaQuieta } from './ayudantes/movimiento.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const NORMAS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const GRAVES = new Set(['serious', 'critical']);

/** Lo que el asistente le dice a un visitante que pide algo de su cuenta (1.11.0). */
const SIN_SESION = 'Sin sesión no puedo ver ningún dato personal';

/** Una sesión de visitante declarada, con la forma que acepta el servicio. */
function sesionDeVisitante() {
  return `visitante-e2e-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

test.describe('Asistente · visitante (§7.4)', () => {
  test.describe.configure({ mode: 'serial' });

  test('desde /login: registro, reglas, minimizar y restaurar, navegar, historial y nada privado', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await page.goto(`${BORDE}/login`);
    const asistente = await abrirAsistente(page);

    // 0. Antes de preguntar nada, «Quizá te interese» por la sección donde está.
    const propuesta = asistente.ventana.locator('[data-proactiva]');
    await expect(
      propuesta.getByRole('group', { name: 'Por la sección donde estás' }),
    ).toBeVisible();

    // 1. Cómo registrarse: la guía paso a paso y el enlace a «Crear cuenta».
    const registro = await preguntar(page, '¿Cómo me registro?');
    await expect(registro).toContainText('Para crear tu cuenta');
    // Lo propuesto se va con la pregunta.
    await expect(propuesta).toHaveCount(0);
    expect(await registro.locator('.chatbot-enriquecido__pasos li').count()).toBeGreaterThanOrEqual(
      3,
    );
    await expect(enlaceA(registro, 'registro')).toBeVisible();

    // 2. Las reglas del juego (V15): el resumen paso a paso y el centro de ayuda.
    const reglas = await preguntar(page, '¿Cuáles son las reglas del juego?');
    await expect(reglas).toContainText('Las reglas básicas de THE NEXUS BATTLES VI');
    expect(await reglas.locator('.chatbot-enriquecido__pasos li').count()).toBeGreaterThanOrEqual(
      5,
    );
    await expect(enlaceA(reglas, 'ayuda')).toBeVisible();

    // axe sobre la ventana abierta, con la conversación de verdad.
    await conLaPaginaQuieta(page);
    const analisis = await new AxeBuilder({ page })
      .include('.chatbot-ventana')
      .withTags(NORMAS)
      .analyze();
    const graves = analisis.violations.filter((v) => GRAVES.has(v.impact ?? ''));
    expect(
      graves.map((v) => `${v.id} [${v.impact}] ${v.nodes.length}× — ${v.help}`),
      'axe en la ventana del asistente',
    ).toEqual([]);

    // 3. Minimizar deja la barra de título y la conversación intacta.
    await asistente.minimizar.click();
    await expect(asistente.ventana).toHaveClass(/chatbot-ventana--minimizada/);
    await expect(asistente.registro).toBeHidden();
    await expect(asistente.minimizar).toHaveAttribute('aria-label', 'Restaurar el asistente');
    await asistente.minimizar.click();
    await expect(asistente.ventana).not.toHaveClass(/chatbot-ventana--minimizada/);
    await expect(asistente.registro).toBeVisible();
    await expect(asistente.respuestas).toHaveCount(2);

    // 4. Navegar con el enlace de la respuesta: a /registro.
    await enlaceA(registro, 'registro').click();
    await page.waitForURL(/\/registro(?:[?#]|$)/, { timeout: 20_000 });

    // 5. En la otra vista la conversación sigue: es la misma sesión de visitante.
    const otraVista = await abrirAsistente(page);
    await expect(otraVista.preguntas.first()).toContainText('¿Cómo me registro?');
    await expect(otraVista.respuestas).toHaveCount(2);
    await expect(otraVista.respuestas.nth(1)).toContainText('Las reglas básicas');
    // Y le propone lo que sigue de lo que preguntó, diciendo por qué.
    await expect(
      otraVista.ventana
        .locator('[data-proactiva]')
        .getByRole('group', { name: /^Porque preguntaste por «/ })
        .first(),
    ).toBeVisible();

    // Navegación asistida: una sección pública, con su enlace.
    const subasta = await preguntar(page, 'llévame a la subasta');
    await expect(enlaceA(subasta, 'subastas')).toBeVisible();
    // Y una que pide sesión: entrar o crear cuenta, sin enlace a la tienda.
    const tienda = await preguntar(page, 'llévame a la tienda');
    await expect(tienda).toContainText('necesitas iniciar sesión');
    expect(await destinosDe(tienda)).toEqual(['login', 'registro']);

    // 6. Algo de su cuenta: hace falta una sesión, y no se consultó nada.
    const privado = await preguntar(page, '¿Qué tengo en mi inventario?');
    await expect(privado).toContainText(SIN_SESION);
    expect(await destinosDe(privado)).toContain('login');
    const transacciones = await preguntar(page, 'muéstrame mis últimas transacciones');
    await expect(transacciones).toContainText(SIN_SESION);
    // No es una pregunta sin respuesta: no ofrece soporte humano.
    await expect(transacciones.locator('[data-accion="ofrecer-soporte"]')).toHaveCount(0);
  });

  test('en inglés: las relacionadas, pulsarlas y el autocompletado siguen en inglés', async ({
    page,
  }) => {
    test.setTimeout(90_000);
    await page.goto(`${BORDE}/login`);
    const asistente = await abrirAsistente(page);

    // 1. Una pregunta en inglés: sus preguntas relacionadas, con el título en inglés (V21).
    const torneo = await preguntar(page, 'How does the tournament work?');
    await expect(torneo).toContainText('Related topics:');
    const relacionadas = torneo.locator('[data-accion="respuesta-rapida"]');
    await expect(relacionadas).toHaveText([
      'How to complete a mission (PvE mode)',
      'Battle mode (PvP and co-op)',
      'How to create a battle room',
    ]);

    // 2. Pulsar una sigue la conversación en inglés.
    const antes = await asistente.respuestas.count();
    await relacionadas.nth(1).click();
    await expect(asistente.respuestas).toHaveCount(antes + 1, { timeout: ESPERA_DE_RESPUESTA_MS });
    await expect(asistente.preguntas.last()).toContainText('Battle mode (PvP and co-op)');
    const batalla = asistente.respuestas.nth(antes);
    await expect(batalla).toContainText('Related topics:');
    await expect(batalla).not.toContainText('Temas relacionados');

    // 3. El autocompletado sugiere en el idioma de lo escrito.
    await asistente.entrada.fill('how the tourn');
    await expect(asistente.ventana.locator('.chatbot-autocompletar__opcion').first()).toHaveText(
      'How the Tournament works',
    );
    await asistente.entrada.fill('como funciona el tor');
    await expect(asistente.ventana.locator('.chatbot-autocompletar__opcion').first()).toHaveText(
      'Cómo funciona el Torneo',
    );
  });

  test('sin respuesta: su solicitud se abre sola, la ve en «Tus solicitudes» y nadie más', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    const sufijo = Date.now().toString(36);
    await page.goto(`${BORDE}/login`);
    await abrirAsistente(page);

    const respuesta = await preguntar(page, `la nebulosa Quetzal ${sufijo} no aparece en mi mapa`);
    await expect(respuesta).toContainText('No estoy seguro de haber entendido');
    await expect(respuesta).toContainText('mientras sigas en esta sesión de visitante');
    const aviso = respuesta.locator('.chatbot-enriquecido__solicitud');
    const id = await aviso.getAttribute('data-solicitud-id');
    expect(id).toMatch(/^[0-9a-f-]{36}$/);

    await aviso.locator('[data-accion="ver-solicitud"]').click();
    const panel = page.locator('.chatbot-soporte');
    await expect(panel).toContainText('Estás como visitante');
    await expect(panel.locator(`.chatbot-soporte__ticket[data-ticket-id="${id}"]`)).toContainText(
      'Abierta',
    );
    await expect(panel.locator('form')).toHaveCount(1);
    await expect(panel.getByRole('link', { name: 'Iniciar sesión' })).toBeVisible();

    const api = await apiRequest.newContext({ baseURL: BORDE });
    try {
      const suya = await page.evaluate(() => sessionStorage.getItem('nexus.chatbot.sesionAnonima'));
      expect(suya).toMatch(/^visitante-/);
      const conSesion = (sesion) => ({ headers: { 'X-Id-Sesion-Anonima': sesion } });
      // Su sesión la ve; otra, no; sin sesión, 401.
      const mias = await api.get('/api/v1/chat/tickets', conSesion(suya));
      expect(mias.status()).toBe(200);
      expect((await mias.json()).map((t) => t.ticketId)).toContain(id);
      const ajenas = await api.get('/api/v1/chat/tickets', conSesion(sesionDeVisitante()));
      expect(ajenas.status()).toBe(200);
      expect(await ajenas.json()).toEqual([]);
      expect((await api.get('/api/v1/chat/tickets')).status()).toBe(401);

      // El administrador sabe que es de un visitante, sin cuenta.
      const admin = await sesionDelBanco(api, ADMIN, { clave: CLAVE, base: BORDE });
      const detalle = await api.get(`/api/v1/chatbot/admin/tickets/${id}`, {
        headers: { Authorization: `Bearer ${admin.token}` },
      });
      expect(detalle.status(), await detalle.text()).toBe(200);
      const solicitud = await detalle.json();
      expect(solicitud.deVisitante).toBe(true);
      expect(solicitud.uid).toMatch(/^anonimo:/);
      expect(solicitud.uid).not.toContain(suya);
    } finally {
      await api.dispose();
    }
  });

  test('por la API: ni el historial de otra sesión, ni hacerse pasar por un jugador', async () => {
    const api = await apiRequest.newContext({ baseURL: BORDE });
    try {
      const jugadora = await sesionDelBanco(api, `vis_jug_${Date.now().toString(36)}`, {
        clave: CLAVE,
        base: BORDE,
      });
      const mia = sesionDeVisitante();
      const ajena = sesionDeVisitante();

      const enviar = (sesion, contenido, extra = {}) =>
        api.post('/api/v1/chat/mensajes', {
          headers: { 'X-Id-Sesion-Anonima': sesion, ...extra },
          data: { contenido, ...(extra.cuerpo ?? {}) },
        });

      expect((await enviar(mia, '¿Cómo me registro?')).status()).toBe(200);

      // Otra sesión no ve esa conversación.
      const historialAjeno = await api.get('/api/v1/chat/historial', {
        headers: { 'X-Id-Sesion-Anonima': ajena },
      });
      expect(historialAjeno.status()).toBe(200);
      expect(JSON.stringify(await historialAjeno.json())).not.toContain('¿Cómo me registro?');

      // El uid de una jugadora en cabeceras o en la consulta no la convierte
      // en esa jugadora: el uid sale solo del token (7.4.8).
      const suplantado = await api.post(
        `/api/v1/chat/mensajes?uid=${jugadora.claims.uid}&usuarioId=${jugadora.claims.uid}`,
        {
          headers: {
            'X-Id-Sesion-Anonima': mia,
            'X-Uid': jugadora.claims.uid,
            'X-Usuario-Id': jugadora.claims.uid,
            'X-User-Name': jugadora.apodo,
          },
          data: { contenido: '¿Qué tengo en mi inventario?', vista: 'INVENTARIO' },
        },
      );
      expect(suplantado.status()).toBe(200);
      const respuesta = await suplantado.json();
      expect(respuesta.contenido).toContain(SIN_SESION);
      expect(respuesta.contenido).not.toContain('elemento(s) en tu inventario');

      // Y su historial sigue siendo solo suyo: ni rastro de la del visitante.
      const historialDeLaJugadora = await api.get('/api/v1/chat/historial', {
        headers: { Authorization: `Bearer ${jugadora.token}` },
      });
      expect(historialDeLaJugadora.status()).toBe(200);
      expect(JSON.stringify(await historialDeLaJugadora.json())).not.toContain(
        '¿Cómo me registro?',
      );
    } finally {
      await api.dispose();
    }
  });

  test('una sesión de visitante sin la forma del contrato no abre nada', async () => {
    const api = await apiRequest.newContext({ baseURL: BORDE });
    try {
      // Un uid (UUID) no es una sesión de visitante: se trata como sin sesión.
      const historial = await api.get('/api/v1/chat/historial', {
        headers: { 'X-Id-Sesion-Anonima': '6a3f7e1c-0000-4000-8000-000000000001' },
      });
      expect([200, 400, 401]).toContain(historial.status());
      if (historial.status() === 200) {
        expect(await historial.json()).toEqual([]);
      }
    } finally {
      await api.dispose();
    }
  });
});
