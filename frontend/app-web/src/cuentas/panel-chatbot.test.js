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

test('tiene la pestaña «Soporte» y la carga al abrirla', async () => {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  const cliente = {
    analiticas: jest.fn(async () => new Promise(() => {})),
    listarTickets: jest.fn(async () => new Promise(() => {})),
  };

  montarPanelChatbot(raiz, { cliente, hash: false });
  const pestana = raiz.querySelector('[data-pestana="soporte"]');
  expect(pestana.textContent).toBe('Soporte');
  expect(cliente.listarTickets).not.toHaveBeenCalled();

  pestana.click();

  expect(cliente.listarTickets).toHaveBeenCalledTimes(1);
});

test('«Preguntas sin respuesta» va después de «Analíticas» y se carga al abrirla', async () => {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  const cliente = {
    analiticas: jest.fn(async () => new Promise(() => {})),
    listarBrechas: jest.fn(async () => new Promise(() => {})),
  };

  montarPanelChatbot(raiz, { cliente, hash: false });
  const etiquetas = [...raiz.querySelectorAll('[data-pestana]')].map((boton) => boton.textContent);
  expect(etiquetas).toEqual([
    'Analíticas',
    'Preguntas sin respuesta',
    'Base de conocimiento',
    'Reentrenamiento',
    'Soporte',
  ]);
  expect(cliente.listarBrechas).not.toHaveBeenCalled();

  raiz.querySelector('[data-pestana="brechas"]').click();

  expect(cliente.listarBrechas).toHaveBeenCalledTimes(1);
  expect(cliente.listarBrechas.mock.calls[0][0]).toMatchObject({ estado: 'PENDIENTE', pagina: 0 });
});

test('sin candidata, «Crear tema con esta pregunta» lleva a la pestaña de la base', async () => {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  const cliente = {
    analiticas: jest.fn(async () => new Promise(() => {})),
    listarBrechas: jest.fn(async () => ({
      contenido: [
        {
          brechaId: 'b-1',
          pregunta: 'como cambio mi apodo',
          escalamientos: 2,
          noUtiles: 0,
          total: 2,
          primeraVez: '2026-10-01T15:00:00Z',
          ultimaVez: '2026-10-02T15:00:00Z',
          estado: 'PENDIENTE',
          estadoCambiadoPor: null,
          estadoCambiadoEn: null,
          reaperturas: 0,
        },
      ],
      totalElementos: 1,
    })),
    listarVersiones: jest.fn(async () => [{ versionId: 'v-1', numero: 3, estado: 'PRODUCCION' }]),
    listarTemas: jest.fn(async () => new Promise(() => {})),
  };
  montarPanelChatbot(raiz, { cliente, hash: false });
  raiz.querySelector('[data-pestana="brechas"]').click();
  await new Promise((listo) => setTimeout(listo, 0));

  raiz.querySelector('[data-accion="crear-tema-desde-brecha"]').click();
  await new Promise((listo) => setTimeout(listo, 0));
  raiz.querySelector('[data-accion="ir-a-la-base"]').click();

  expect(raiz.querySelector('[data-pestana="base"]').getAttribute('aria-selected')).toBe('true');
});
