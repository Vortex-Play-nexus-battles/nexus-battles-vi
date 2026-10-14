/**
 * Enlaces con ancla — ms-chatbot.yaml 1.8.0: «Ver el centro de ayuda» llega
 * con `destino: 'ayuda'` y la categoría de la pregunta en `ancla`.
 */

import { jest } from '@jest/globals';

import { conAncla, pintarEnriquecido } from './enriquecido-chatbot.js';

const url = (destino) => `/vistas/${destino}.html`;

function enlaces(lista) {
  const el = pintarEnriquecido(
    { enlaces: lista },
    { alPreguntar: jest.fn(), alPedirSoporte: jest.fn(), url, visible: () => true },
  );
  return [...el.querySelectorAll('.chatbot-enriquecido__enlace')];
}

test('el enlace al centro de ayuda abre la categoría de la pregunta', () => {
  const [ayuda] = enlaces([
    { texto: 'Ver el centro de ayuda', destino: 'ayuda', ancla: 'categoria-cuenta-y-registro' },
  ]);

  expect(ayuda.getAttribute('href')).toBe('/vistas/ayuda.html#categoria-cuenta-y-registro');
  expect(ayuda.textContent).toBe('Ver el centro de ayuda');
  expect(ayuda.dataset.destino).toBe('ayuda');
});

test('sin ancla, o con una que no tiene la forma del contrato, va al principio de la vista', () => {
  const [sinAncla, conNulo, rara] = enlaces([
    { texto: 'Ir a Subasta', destino: 'subastas' },
    { texto: 'Ver el centro de ayuda', destino: 'ayuda', ancla: null },
    { texto: 'Ver el centro de ayuda', destino: 'ayuda', ancla: '"><img src=x>' },
  ]);

  expect(sinAncla.getAttribute('href')).toBe('/vistas/subastas.html');
  expect(conNulo.getAttribute('href')).toBe('/vistas/ayuda.html');
  expect(rara.getAttribute('href')).toBe('/vistas/ayuda.html');
});

test('conAncla solo acepta letras minúsculas, cifras y guiones', () => {
  expect(conAncla('/a.html', 'categoria-faq-general')).toBe('/a.html#categoria-faq-general');
  expect(conAncla('/a.html', 'Categoria')).toBe('/a.html');
  expect(conAncla('/a.html', 'x'.repeat(61))).toBe('/a.html');
  expect(conAncla('/a.html', undefined)).toBe('/a.html');
});
