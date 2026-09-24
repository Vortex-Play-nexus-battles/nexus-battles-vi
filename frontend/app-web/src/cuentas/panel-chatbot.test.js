/**
 * Panel de administración del asistente — HU-CHA-012.
 */

import { jest } from '@jest/globals';

import { montarPanelChatbot } from './panel-chatbot.js';

test('monta el encabezado y las pestañas, y carga solo la pestaña visible', async () => {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  const cliente = { analiticas: jest.fn(async () => new Promise(() => {})) };

  montarPanelChatbot(raiz, { cliente, hash: false });

  expect(raiz.querySelector('h1').textContent).toBe('Asistente');
  expect(raiz.querySelector('[role="tablist"]')).not.toBeNull();
  expect(raiz.querySelector('[data-pestana="analiticas"]').getAttribute('aria-selected')).toBe(
    'true',
  );
  expect(cliente.analiticas).toHaveBeenCalledTimes(1);
});
