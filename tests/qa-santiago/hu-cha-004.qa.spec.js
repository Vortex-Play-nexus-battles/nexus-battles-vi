// HU-CHA-004 · Motor de respuestas del chatbot (Cristian Chaparro)
import { test } from '@playwright/test';
import { abrirChat, entrar, expect, foto, fotoTexto, preguntar, sql } from './ayudante-qa.js';

test.describe('HU-CHA-004 · Motor de respuestas del chatbot', () => {
  test('CP-CHA-004-01 · Comprende consultas en español, con errores ortográficos', async ({ page }) => {
    await page.goto('/');
    await abrirChat(page);
    const r1 = await preguntar(page, 'como me registro');
    await expect(r1).toContainText('Para crear tu cuenta');
    await foto(page, 'CP-CHA-004-01', 'Dato «como me registro»: el bot reconoce la intención «registro» y responde en español', { resaltar: r1 });
    const r2 = await preguntar(page, 'olvde mi contraseña');
    await expect(r2).toContainText('recuperar tu cuenta');
    await foto(page, 'CP-CHA-004-01', 'Dato con error ortográfico «olvde mi contraseña»: igual reconoce «recuperar cuenta»', { resaltar: r2 });
    const r3 = await preguntar(page, 'kiero registrarme');
    await expect(r3).toContainText('Para crear tu cuenta');
    await foto(page, 'CP-CHA-004-01', 'Dato «kiero registrarme» (otra formulación y con error): misma intención reconocida', { resaltar: r3 });
  });

  test('CP-CHA-004-02 · Comprende consultas en inglés y responde en inglés', async ({ page }) => {
    await page.goto('/');
    await abrirChat(page);
    const r1 = await preguntar(page, 'how do I sign up');
    await expect(r1).toContainText('To create your account');
    await foto(page, 'CP-CHA-004-02', 'Dato «how do I sign up»: respuesta en inglés del tema «registro»', { resaltar: r1 });
    const r2 = await preguntar(page, 'i forgot my pasword');
    await expect(r2).toContainText('To recover your account');
    await foto(page, 'CP-CHA-004-02', 'Dato en inglés con error «i forgot my pasword»: respuesta en inglés', { resaltar: r2 });
    const r3 = await preguntar(page, 'what heroes are there');
    await foto(page, 'CP-CHA-004-02', 'Dato «what heroes are there»: se verifica que también responda en inglés', { resaltar: r3 });
    await expect(r3, 'la respuesta a una pregunta en inglés debe estar en inglés').not.toContainText('tiene 8 prototipos de héroes');
  });

  test('CP-CHA-004-03 · Responde desde la base de conocimiento: producto, mecánica y soporte técnico', async ({ page }) => {
    await entrar(page, 'comprador_qa');
    await abrirChat(page);
    const mecanica = await preguntar(page, 'como subo de nivel');
    await expect(mecanica).toContainText('nivel');
    await foto(page, 'CP-CHA-004-03', 'Mecánica de juego: «como subo de nivel» → estadísticas y niveles del héroe', { resaltar: mecanica });
    const producto = await preguntar(page, 'que armas y armaduras hay');
    await expect(producto).toContainText(/arma/i);
    await foto(page, 'CP-CHA-004-03', 'Productos: «que armas y armaduras hay» → tema «Armas, armaduras e ítems»', { resaltar: producto });
    const soporte = await preguntar(page, 'no puedo entrar, me sale un error');
    await expect(soporte).toContainText(/error|soporte/i);
    await foto(page, 'CP-CHA-004-03', 'Soporte técnico: «no puedo entrar, me sale un error» → respuesta contextual de diagnóstico', { resaltar: soporte });
  });

  test('CP-CHA-004-04 · Combina varias entradas de la base en una misma respuesta', async ({ page }) => {
    await page.goto('/');
    await abrirChat(page);
    const r = await preguntar(page, 'como publico un producto en subasta y como pujo');
    await foto(page, 'CP-CHA-004-04', 'Pregunta que toca dos temas a la vez (publicar en subasta + pujar)', { resaltar: r });
    const texto = await r.locator('.chatbot-ventana__texto').first().innerText();
    expect(texto, 'la respuesta debe cubrir publicar Y pujar').toMatch(/publica/i);
    expect(texto).toMatch(/puja/i);
  });

  test('CP-CHA-004-05 · Tipos de respuesta: directa, paso a paso, enriquecida y sugerencias', async ({ page }) => {
    await page.goto('/');
    await abrirChat(page);
    const directa = await preguntar(page, 'que es nexus battles');
    await expect(directa).toContainText('juego de rol por turnos');
    await foto(page, 'CP-CHA-004-05', 'Respuesta DIRECTA: «que es nexus battles»', { resaltar: directa });
    const pasos = await preguntar(page, 'como me registro');
    await expect(pasos.locator('.chatbot-enriquecido__pasos li').first()).toBeVisible();
    await foto(page, 'CP-CHA-004-05', 'Respuesta PASO A PASO: lista numerada de pasos para registrarse', { resaltar: pasos.locator('.chatbot-enriquecido__pasos') });
    const enriquecida = await preguntar(page, 'como pujar en una subasta');
    const enlace = enriquecida.locator('.chatbot-enriquecido__enlace').first();
    await expect(enlace).toBeVisible();
    await expect(enriquecida.locator('[data-accion="respuesta-rapida"]').first()).toBeVisible();
    await foto(page, 'CP-CHA-004-05', 'Respuesta ENRIQUECIDA con enlace «Ir a Subastas» y sugerencias proactivas de temas relacionados', { resaltar: [enlace, enriquecida.locator('.chatbot-rapidas__lista')] });
  });

  test('CP-CHA-004-06 · Consulta no resuelta: soporte humano, preguntas relacionadas, ticket y registro de la brecha', async ({ page }) => {
    await entrar(page, 'comprador_qa');
    await abrirChat(page);
    const marca = Date.now().toString(36);
    const r = await preguntar(page, `cual es la capital de mongolia ${marca}`);
    await expect(r).toContainText('No estoy seguro de haber entendido');
    const soporte = r.locator('[data-accion="ofrecer-soporte"], [data-accion="hablar-con-soporte"]').first();
    await expect(soporte).toBeVisible();
    await foto(page, 'CP-CHA-004-06', 'Consulta fuera de la base: ofrece soporte humano, sugiere preguntas relacionadas y avisa que la registró', { resaltar: r });
    await soporte.click();
    const formulario = page.locator('[data-chatbot-soporte]');
    await expect(formulario).toBeVisible();
    await foto(page, 'CP-CHA-004-06', 'Al pulsar «Hablar con soporte» se abre el formulario de solicitud (ticket)', { resaltar: formulario });
    const tema = formulario.locator('select').first();
    await tema.selectOption({ label: 'Otra consulta' });
    const asunto = formulario.locator('[name="asunto"]');
    if (await asunto.count()) await asunto.fill('Pregunta que el asistente no resolvió');
    const mensaje = formulario.locator('[name="mensaje"]');
    if (await mensaje.count()) await mensaje.fill('El asistente no supo responder mi consulta; necesito ayuda de una persona.');
    await formulario.getByRole('button', { name: /enviar/i }).click();
    await expect(formulario.locator('.chatbot-soporte__ticket').first()).toBeVisible();
    await foto(page, 'CP-CHA-004-06', 'Ticket de soporte creado: aparece en la lista de solicitudes del jugador', { resaltar: formulario.locator('.chatbot-soporte__ticket').first() });
    const filas = sql('chatbot', `SELECT b::text FROM chatbot.brechas_conocimiento b WHERE b::text ILIKE '%${marca}%' LIMIT 3;`);
    await fotoTexto(page, 'CP-CHA-004-06', 'La consulta no resuelta quedó registrada como brecha de conocimiento', 'chatbot.brechas_conocimiento (ms-chatbot)', filas || '(sin filas)');
    expect(filas).toContain(marca);
  });
});
