/**
 * El asistente (ms-chatbot, §7.4) desde su ventana, como lo usa una persona:
 * el botón flotante, la caja de texto, la respuesta que llega y lo que trae.
 *
 * Una respuesta del bot se reconoce por su `data-mensaje-id` (el id que le dio
 * el servidor): la bienvenida también es una burbuja del bot, pero sin id, y
 * desaparece con la primera pregunta. Por eso `preguntar` cuenta las burbujas
 * con id antes de enviar y espera una más, en vez de mirar «la última».
 *
 * Funciona igual si la pregunta va por el canal en tiempo real (STOMP,
 * contracts/websocket/chatbot.yaml) o por HTTP: lo que se mira es la ventana.
 *
 * @module tests/e2e/ayudantes/asistente
 */

import { expect } from '@playwright/test';

/** Lo que una respuesta puede tardar en el banco (unas veinte JVM en un runner). */
export const ESPERA_DE_RESPUESTA_MS = 30_000;

/**
 * Los elementos del asistente en una página.
 *
 * @param {import('@playwright/test').Page} page
 */
export function asistenteDe(page) {
  const ventana = page.locator('.chatbot-ventana');
  return {
    lanzador: page.locator('.chatbot-flotante'),
    ventana,
    entrada: ventana.locator('textarea[name="contenido"]'),
    enviar: ventana.locator('.chatbot-ventana__formulario button[type="submit"]'),
    registro: ventana.locator('[role="log"]'),
    respuestas: ventana.locator('.chatbot-ventana__mensaje--bot[data-mensaje-id]'),
    preguntas: ventana.locator('.chatbot-ventana__mensaje--usuario'),
    minimizar: ventana.locator('[data-accion="minimizar-asistente"]'),
    preferencias: ventana.locator('[data-accion="preferencias-asistente"]'),
    soporte: ventana.locator('[data-accion="hablar-con-soporte"]'),
    // ms-chatbot.yaml 1.14.0: el clip de la caja de texto (capturas para soporte).
    captura: ventana.locator('[data-accion="adjuntar-captura"]'),
  };
}

/**
 * Abre la ventana con el botón flotante y espera a que la conversación esté
 * cargada («Enviar» activo).
 *
 * @param {import('@playwright/test').Page} page
 */
export async function abrirAsistente(page) {
  const asistente = asistenteDe(page);
  await expect(asistente.lanzador).toBeVisible({ timeout: ESPERA_DE_RESPUESTA_MS });
  if (!(await asistente.ventana.isVisible())) {
    await asistente.lanzador.click();
  }
  await expect(asistente.ventana).toBeVisible();
  await expect(asistente.enviar).toBeEnabled({ timeout: ESPERA_DE_RESPUESTA_MS });
  return asistente;
}

/**
 * Escribe una pregunta, la envía con Enter y devuelve la burbuja de la
 * respuesta nueva.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} texto
 * @returns {Promise<import('@playwright/test').Locator>}
 */
export async function preguntar(page, texto) {
  const asistente = asistenteDe(page);
  await expect(asistente.enviar).toBeEnabled({ timeout: ESPERA_DE_RESPUESTA_MS });
  const antes = await asistente.respuestas.count();
  await asistente.entrada.fill(texto);
  await asistente.entrada.press('Enter');
  await expect(asistente.respuestas).toHaveCount(antes + 1, { timeout: ESPERA_DE_RESPUESTA_MS });
  // La pregunta queda en la conversación y la caja, libre para la siguiente.
  await expect(asistente.preguntas.last()).toContainText(texto);
  return asistente.respuestas.nth(antes);
}

/**
 * Las vistas a las que enlaza una respuesta (el `destino` de cada enlace, un
 * id de la matriz de acceso: «registro», «tienda»…). Solo las que se pintan:
 * la ventana no pinta un enlace a una vista que esa sesión no puede ver.
 *
 * @param {import('@playwright/test').Locator} respuesta
 * @returns {Promise<string[]>}
 */
export async function destinosDe(respuesta) {
  return respuesta
    .locator('a.chatbot-enriquecido__enlace')
    .evaluateAll((enlaces) => enlaces.map((a) => a.dataset.destino ?? ''));
}

/**
 * El enlace de una respuesta a una vista.
 *
 * @param {import('@playwright/test').Locator} respuesta
 * @param {string} destino id de la vista
 */
export function enlaceA(respuesta, destino) {
  return respuesta.locator(`a.chatbot-enriquecido__enlace[data-destino="${destino}"]`);
}
