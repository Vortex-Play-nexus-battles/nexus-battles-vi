/**
 * Respuestas enriquecidas del asistente y la vista donde está el jugador.
 */

import { jest } from '@jest/globals';

import {
  VISTA_DEL_CHAT,
  introAntesDePasos,
  pintarEnriquecido,
  vistaDelChat,
} from './enriquecido-chatbot.js';

const url = (destino) => (destino === 'no-existe' ? null : `/vistas/${destino}.html`);

function pintar(enriquecido) {
  const alPreguntar = jest.fn();
  const alPedirSoporte = jest.fn();
  const el = pintarEnriquecido(enriquecido, { alPreguntar, alPedirSoporte, url });
  return { el, alPreguntar, alPedirSoporte };
}

test('pinta los pasos numerados', () => {
  const { el } = pintar({ pasos: ['Elige el ítem.', 'Confirma.'] });

  const pasos = el.querySelectorAll('.chatbot-enriquecido__pasos li');
  expect([...pasos].map((li) => li.textContent)).toEqual(['Elige el ítem.', 'Confirma.']);
});

test('los enlaces son a vistas del sitio; uno desconocido no se pinta', () => {
  const { el } = pintar({
    enlaces: [
      { texto: 'Ir a Subastas', destino: 'subastas' },
      { texto: 'Afuera', destino: 'no-existe' },
    ],
  });

  const enlaces = el.querySelectorAll('.chatbot-enriquecido__enlace');
  expect(enlaces).toHaveLength(1);
  expect(enlaces[0].getAttribute('href')).toBe('/vistas/subastas.html');
  expect(enlaces[0].textContent).toBe('Ir a Subastas');
});

test('las tarjetas llevan título, texto y su enlace si lo hay', () => {
  const { el } = pintar({
    tarjetas: [
      {
        titulo: 'El Templo Olvidado',
        texto: '40 % completada',
        enlace: { texto: 'Ver', destino: 'misiones' },
      },
      { titulo: 'Sin enlace', texto: 'x', enlace: null },
    ],
  });

  const tarjetas = el.querySelectorAll('.chatbot-enriquecido__tarjeta');
  expect(tarjetas).toHaveLength(2);
  expect(tarjetas[0].textContent).toContain('El Templo Olvidado');
  expect(tarjetas[0].querySelector('a').getAttribute('href')).toBe('/vistas/misiones.html');
  expect(tarjetas[1].querySelector('a')).toBeNull();
});

test('los botones preguntan y «Hablar con soporte» abre el soporte', () => {
  const { el, alPreguntar, alPedirSoporte } = pintar({
    respuestasRapidas: ['Cómo funciona el Torneo'],
    ofrecerSoporteHumano: true,
  });

  el.querySelector('[data-accion="respuesta-rapida"]').click();
  el.querySelector('[data-accion="ofrecer-soporte"]').click();

  expect(alPreguntar).toHaveBeenCalledWith('Cómo funciona el Torneo');
  expect(alPedirSoporte).toHaveBeenCalled();
});

test('sin nada que pintar devuelve null', () => {
  expect(pintar(null).el).toBeNull();
  expect(
    pintar({ pasos: [], enlaces: [{ destino: 'no-existe' }], tarjetas: [], respuestasRapidas: [] })
      .el,
  ).toBeNull();
});

test('el texto del servidor nunca se interpreta como HTML', () => {
  const { el } = pintar({ pasos: ['<img src=x onerror=alert(1)>'] });

  expect(el.querySelector('img')).toBeNull();
  expect(el.textContent).toContain('<img src=x onerror=alert(1)>');
});

test('introAntesDePasos deja solo lo que va antes del 1)', () => {
  expect(introAntesDePasos('Para publicar: 1) Elige. 2) Confirma.')).toBe('Para publicar:');
  expect(introAntesDePasos('1) Elige. 2) Confirma.')).toBe('');
  expect(introAntesDePasos('Sin pasos.')).toBe('Sin pasos.');
  expect(introAntesDePasos(null)).toBe('');
});

test('vistaDelChat traduce la vista de la matriz a la del contrato', () => {
  expect(vistaDelChat('subastas')).toBe('SUBASTAS');
  expect(vistaDelChat('pujas')).toBe('SUBASTAS');
  expect(vistaDelChat('perfil')).toBe('CUENTA');
  expect(vistaDelChat('panel-chatbot')).toBeNull();
  expect(vistaDelChat(null)).toBeNull();
  expect(Object.values(VISTA_DEL_CHAT)).toEqual(
    expect.arrayContaining(['INICIO', 'INVENTARIO', 'MISIONES', 'TORNEOS', 'SUBASTAS', 'CUENTA']),
  );
});
