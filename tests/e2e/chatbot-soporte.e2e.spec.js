// @ts-check
/**
 * Soporte humano desde el asistente (§7.4.3 y §7.4.6), de punta a punta: lo
 * que el asistente no resuelve llega a una persona, sin ningún secreto.
 *
 * El recorrido de la revisión final del 7.4: una jugadora pregunta algo que
 * el asistente no entiende (y en la pregunta va su contraseña) → la respuesta
 * le ofrece alternativas y «Hablar con soporte» → abre la solicitud con esa
 * conversación en un clic → la contraseña no llega a ninguna parte (ni a la
 * conversación, ni a la solicitud, ni a su contexto) → la solicitud persiste
 * → el administrador la ve en el panel del asistente, la lee y la responde →
 * la jugadora ve la respuesta en su ventana.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { abrirAsistente, preguntar } from './ayudantes/asistente.js';
import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const SUFIJO = Date.now().toString(36);
/** Un secreto que no puede aparecer en ningún sitio. */
const SECRETO = `Zafiro${SUFIJO}!`;
// Algo que el asistente no sabe responder (ningún tema habla de constelaciones).
const PREGUNTA = `mi contraseña es ${SECRETO} y la constelación Quetzal ${SUFIJO} desapareció`;
/** Como queda guardada: la contraseña entera tapada, también su «!» final (1.11.0). */
const PREGUNTA_REDACTADA = `mi contraseña es *** y la constelación Quetzal ${SUFIJO} desapareció`;
const RESPUESTA_DE_SOPORTE = `Revisamos tu cuenta (${SUFIJO}): tu héroe está en «Mi inventario».`;
const VISTA_PANEL = '/frontend/app-web/src/cuentas/panel-chatbot.html';

function conToken(token) {
  return { Authorization: `Bearer ${token}` };
}

/** La sesión que deja login.js (comun/sesion.js) en una página nueva. */
async function conSesion(page, sesion, rol) {
  await page.addInitScript(
    ([token, apodo, uid, elRol]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', apodo);
      sessionStorage.setItem('nexus.rolActual', elRol);
      sessionStorage.setItem('nexus.usuarioId', uid);
    },
    [sesion.token, sesion.apodo, sesion.claims.uid, rol],
  );
}

test.describe('Asistente · soporte humano (§7.4.3, §7.4.6)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let jugadora;
  let ticket;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    jugadora = await sesionDelBanco(api, `chat_sop_${SUFIJO}`, { clave: CLAVE, base: BORDE });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('sin resolver: alternativas, la solicitud en un clic y ningún secreto guardado', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await conSesion(page, jugadora, 'JUGADOR');
    await page.goto(`${BORDE}/inicio`);
    await abrirAsistente(page);

    const respuesta = await preguntar(page, PREGUNTA);
    // El dato sensible se tapó y se lo dice.
    await expect(respuesta).toContainText('Por tu seguridad tapé un dato sensible');
    // No lo entendió: alternativas y soporte humano.
    await expect(respuesta).toContainText('No estoy seguro de haber entendido');
    await expect(respuesta.locator('[data-accion="respuesta-rapida"]').first()).toBeVisible();
    // En la conversación, la pregunta se ve ya tapada al recargar.
    await page.reload();
    const recargado = await abrirAsistente(page);
    await expect(recargado.preguntas.last()).toContainText(PREGUNTA_REDACTADA);
    await expect(recargado.ventana).not.toContainText(SECRETO);

    // «Hablar con soporte» y la solicitud con esta conversación.
    await recargado.respuestas.last().locator('[data-accion="ofrecer-soporte"]').click();
    const panel = page.locator('.chatbot-soporte');
    await expect(panel).toBeVisible();
    const crear = panel.locator('[data-accion="crear-solicitud-desde-conversacion"]');
    await expect(crear).toBeVisible();
    const creada = page.waitForResponse(
      (r) =>
        r.url().includes('/api/v1/chat/tickets/desde-conversacion') &&
        r.request().method() === 'POST',
    );
    await crear.click();
    const respuestaDeCreacion = await creada;
    expect(respuestaDeCreacion.status(), await respuestaDeCreacion.text()).toBe(201);
    ticket = await respuestaDeCreacion.json();
    await expect(panel).toContainText('Listo: abrimos tu solicitud con esta conversación.');
    const fila = panel
      .locator('.chatbot-soporte__ticket')
      .filter({ hasText: 'Sin respuesta del asistente' });
    await expect(fila.first()).toContainText('Abierta');

    // Persistida, y redactada en todas sus partes.
    const mias = await api.get('/api/v1/chat/tickets', { headers: conToken(jugadora.token) });
    expect(mias.status()).toBe(200);
    const texto = JSON.stringify(await mias.json());
    expect(texto).toContain('Sin respuesta del asistente');
    expect(texto).toContain(PREGUNTA_REDACTADA);
    expect(texto).not.toContain(SECRETO);
  });

  test('el administrador la ve en el panel, sin el secreto, y la responde', async ({ page }) => {
    test.setTimeout(120_000);
    expect(ticket?.ticketId ?? ticket?.id, 'la solicitud de la prueba anterior').toBeTruthy();
    const id = ticket.ticketId ?? ticket.id;
    const admin = await sesionDelBanco(api, ADMIN, { clave: CLAVE, base: BORDE });
    expect(admin.claims.rol).toBe('ADMINISTRADOR');

    // Por la API: la solicitud y su conversación, sin el secreto.
    const detalle = await api.get(`/api/v1/chatbot/admin/tickets/${id}`, {
      headers: conToken(admin.token),
    });
    expect(detalle.status(), await detalle.text()).toBe(200);
    const cuerpo = JSON.stringify(await detalle.json());
    expect(cuerpo).toContain('***');
    expect(cuerpo).not.toContain(SECRETO);

    // Por el panel.
    await conSesion(page, admin, 'ADMINISTRADOR');
    await page.goto(`${BORDE}${VISTA_PANEL}#soporte`);
    const abrir = page.locator(`[data-accion="abrir-ticket"][data-ticket="${id}"]`);
    await expect(abrir).toBeVisible({ timeout: 30_000 });
    await abrir.click();
    const dialogo = page.locator('[role="dialog"]');
    await expect(dialogo).toContainText(PREGUNTA_REDACTADA);
    await expect(dialogo).not.toContainText(SECRETO);
    await dialogo.locator('select[name="estado"]').selectOption('RESUELTO');
    await dialogo.locator('textarea[name="respuesta"]').fill(RESPUESTA_DE_SOPORTE);
    const guardado = page.waitForResponse(
      (r) =>
        r.url().includes(`/api/v1/chatbot/admin/tickets/${id}`) && r.request().method() === 'PATCH',
    );
    await dialogo.locator('[data-accion="guardar-ticket"]').click();
    expect((await guardado).status()).toBe(200);
    await expect(dialogo).toBeHidden();
  });

  test('la jugadora ve la respuesta de soporte en su ventana', async ({ page }) => {
    await conSesion(page, jugadora, 'JUGADOR');
    await page.goto(`${BORDE}/inicio`);
    const asistente = await abrirAsistente(page);
    await asistente.soporte.click();
    const solicitud = page
      .locator('.chatbot-soporte .chatbot-soporte__ticket')
      .filter({ hasText: 'Sin respuesta del asistente' })
      .first();
    await expect(solicitud).toContainText('Respondida', { timeout: 20_000 });
    await expect(solicitud.locator('.chatbot-soporte__respuesta')).toContainText(
      RESPUESTA_DE_SOPORTE,
    );
  });
});
