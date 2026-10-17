// HU-CHA-011 · Calificar las respuestas del chatbot (Edwin Pedraza)
import { test } from '@playwright/test';
import { CORRIDA, VISTA, abrirChat, entrar, expect, foto, fotoHttp, fotoTexto, llamar, preguntar, sesion, sql } from './ayudante-qa.js';

test.describe('HU-CHA-011 · Calificar las respuestas del chatbot', () => {
  test('CP-CHA-011-01 · Calificar una respuesta como útil', async ({ page }) => {
    await page.goto('/');
    await abrirChat(page);
    const r = await preguntar(page, 'como funciona el combate por turnos');
    const util = r.locator('[data-accion="calificar-util"]');
    await expect(util).toBeVisible();
    await foto(page, 'CP-CHA-011-01', 'Bajo cada respuesta del bot aparecen los botones 👍 Sí / 👎 No', { resaltar: r.locator('.chatbot-calificacion') });
    await util.click();
    await expect(r.locator('.chatbot-calificacion__gracias')).toBeVisible();
    await foto(page, 'CP-CHA-011-01', 'Visitante pulsa 👍: la calificación queda registrada y se agradece', { resaltar: r.locator('.chatbot-calificacion') });
  });

  test('CP-CHA-011-02 · Calificar como no útil con comentario libre opcional', async ({ page }) => {
    await entrar(page, 'comprador_qa');
    await abrirChat(page);
    const r = await preguntar(page, 'que armas y armaduras hay');
    await r.locator('[data-accion="calificar-no-util"]').click();
    const comentario = r.locator('.chatbot-calificacion__comentario textarea, textarea.chatbot-calificacion__comentario').first();
    await expect(comentario).toBeVisible();
    await comentario.fill(`Quería saber cuáles dan más defensa (${CORRIDA})`);
    await foto(page, 'CP-CHA-011-02', 'Al pulsar 👎 se abre el campo opcional «¿Qué faltó?»', { resaltar: r.locator('.chatbot-calificacion') });
    await r.locator('[data-accion="enviar-calificacion"]').click();
    await expect(r.locator('.chatbot-calificacion__gracias')).toBeVisible();
    await foto(page, 'CP-CHA-011-02', 'Calificación «no útil» enviada con su comentario', { resaltar: r.locator('.chatbot-calificacion') });
    const fila = sql('chatbot', `SELECT util::text||' | '||coalesce(comentario,'—') FROM chatbot.calificaciones WHERE comentario LIKE '%${CORRIDA}%';`);
    await fotoTexto(page, 'CP-CHA-011-02', 'Calificación persistida', 'chatbot.calificaciones', fila);
    expect(fila).toContain('false');
  });

  test('CP-CHA-011-03 · No se admite calificar dos veces la misma respuesta', async ({ page, request }) => {
    const s = await sesion(request, 'comprador_qa');
    const msj = await llamar(request, 'POST', '/api/v1/chat/mensajes', { token: s.token, data: { contenido: 'como subo de nivel' } });
    const id = msj.cuerpo.id;
    const primera = await llamar(request, 'POST', `/api/v1/chat/mensajes/${id}/calificacion`, { token: s.token, data: { util: true } });
    const segunda = await llamar(request, 'POST', `/api/v1/chat/mensajes/${id}/calificacion`, { token: s.token, data: { util: false, comentario: 'cambio de opinión' } });
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-CHA-011-03', 'La misma respuesta calificada dos veces: la primera 201, la segunda 409 «ya calificada»', [primera, segunda]);
    expect(primera.estado).toBe(201);
    expect(segunda.estado).toBe(409);
    await entrar(page, 'comprador_qa');
    await abrirChat(page);
    const ultimo = page.locator('.chatbot-ventana__mensaje--bot').filter({ hasText: 'nivel' }).last();
    await ultimo.scrollIntoViewIfNeeded();
    const resp = page.waitForResponse((x) => x.url().includes('/calificacion'), { timeout: 10_000 }).catch(() => null);
    await ultimo.locator('[data-accion="calificar-util"]').click();
    const r2 = await resp;
    await page.waitForTimeout(800);
    await foto(page, 'CP-CHA-011-03', `Si se intenta calificar otra vez desde la ventana, el servidor responde ${r2 ? r2.status() : '—'} y la vista lo informa`, { resaltar: ultimo.locator('.chatbot-calificacion') });
    expect(r2?.status()).toBe(409);
    await expect(ultimo.locator('.chatbot-calificacion')).toContainText(/Ya hab[ií]as calificado/i);
  });

  test('CP-CHA-011-04 · Las preguntas no cubiertas se agrupan como brecha de conocimiento', async ({ page, request }) => {
    const s = await sesion(request, 'comprador2_qa');
    const variantes = [`Cual es el horario del soporte ${CORRIDA}`, `cuál es el HORARIO del soporte ${CORRIDA}?`];
    const respuestas = [];
    for (const v of variantes) respuestas.push(await llamar(request, 'POST', '/api/v1/chat/mensajes', { token: s.token, data: { contenido: v } }));
    for (const r of respuestas) {
      if (r.cuerpo?.id) await llamar(request, 'POST', `/api/v1/chat/mensajes/${r.cuerpo.id}/calificacion`, { token: s.token, data: { util: false, comentario: 'no respondió' } });
    }
    const filas = sql('chatbot', `SELECT texto_normalizado||' | no_util='||contador_no_util||' | escalamientos='||contador_escalamiento||' | ejemplo='||left(ejemplo_pregunta,60) FROM chatbot.brechas_conocimiento WHERE texto_normalizado LIKE '%${CORRIDA}%';`);
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-CHA-011-04', 'Dos formulaciones de la misma pregunta no cubierta quedan agrupadas en UNA brecha con su contador', 'chatbot.brechas_conocimiento', filas || '(sin filas)');
    expect(filas.split('\n').length).toBe(1);
    expect(filas).toMatch(/escalamientos=2|no_util=2/);
  });

  test('CP-CHA-011-05 · Las brechas agrupadas quedan disponibles para alimentar la base de conocimiento', async ({ page }) => {
    await entrar(page, 'superadmin_qa');
    await page.goto(VISTA('cuentas/panel-chatbot.html#base-conocimiento'));
    await page.waitForTimeout(2500);
    await foto(page, 'CP-CHA-011-05', 'Panel del asistente › Base de conocimiento: se buscan las preguntas no cubiertas para convertirlas en temas', { completa: true });
    await page.goto(VISTA('cuentas/panel-chatbot.html#analiticas'));
    await page.waitForTimeout(2500);
    await expect(page.getByText(new RegExp(`horario del soporte ${CORRIDA}`, 'i')), 'el administrador debe poder ver las brechas para alimentar la base').toBeVisible({ timeout: 3000 });
  });
});
