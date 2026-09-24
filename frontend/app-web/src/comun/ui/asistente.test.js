/**
 * Pruebas del botón flotante del asistente (HU-CHA-001, RF-CHA-001).
 *
 * El botón abre y cierra la ventana del asistente. La ventana se inyecta: sus
 * propias pruebas están en `ventana-chatbot.test.js`.
 */

import { jest } from '@jest/globals';

import { montarAsistente } from './asistente.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

function ventanaFalsa() {
  const elemento = document.createElement('section');
  elemento.id = 'chatbot-ventana-prueba';
  let abierta = false;
  return {
    elemento,
    alternar: jest.fn(() => {
      abierta = !abierta;
    }),
    abierta: () => abierta,
  };
}

beforeEach(() => {
  document.body.replaceChildren();
});

describe('montarAsistente()', () => {
  test('monta el botón flotante con nombre accesible, cerrado', () => {
    const boton = montarAsistente(document, { crearVentana: ventanaFalsa });

    expect(boton.classList.contains('chatbot-flotante')).toBe(true);
    expect(boton.getAttribute('aria-label')).toBe('Abrir el asistente');
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(boton.type).toBe('button');
    expect(document.body.contains(boton)).toBe(true);
  });

  test('reutiliza el botón que la vista ya traiga en su HTML, sin duplicarlo', () => {
    const previo = document.createElement('button');
    previo.className = 'chatbot-flotante';
    previo.type = 'button';
    document.body.append(previo);

    const boton = montarAsistente(document, { crearVentana: ventanaFalsa });

    expect(boton).toBe(previo);
    expect(document.querySelectorAll('.chatbot-flotante')).toHaveLength(1);
  });

  // Lo monta el armazón y todavía lo montaban algunas vistas: montarlo dos
  // veces no puede enganchar dos clics (la ventana se abriría y cerraría).
  test('montarlo dos veces no duplica el botón ni el comportamiento', async () => {
    const crearVentana = jest.fn(ventanaFalsa);
    const primero = montarAsistente(document, { crearVentana });
    const segundo = montarAsistente(document, { crearVentana });

    expect(segundo).toBe(primero);
    primero.click();
    await esperar();
    expect(crearVentana).toHaveBeenCalledTimes(1);
    expect(primero.getAttribute('aria-expanded')).toBe('true');
  });

  test('el primer clic crea la ventana y la abre; el segundo la cierra', async () => {
    const ventana = ventanaFalsa();
    const crearVentana = jest.fn(() => ventana);
    const boton = montarAsistente(document, { crearVentana });

    boton.click();
    await esperar();
    expect(crearVentana).toHaveBeenCalledTimes(1);
    expect(ventana.alternar).toHaveBeenCalledWith(boton);
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(boton.getAttribute('aria-label')).toBe('Cerrar el asistente');
    expect(boton.getAttribute('aria-controls')).toBe('chatbot-ventana-prueba');

    boton.click();
    await esperar();
    expect(crearVentana).toHaveBeenCalledTimes(1);
    expect(boton.getAttribute('aria-expanded')).toBe('false');
  });

  test('si la ventana se cierra sola (Escape, ×), el botón vuelve a «Abrir»', async () => {
    const boton = montarAsistente(document, { crearVentana: ventanaFalsa });
    boton.click();
    await esperar();

    document.body.dispatchEvent(new CustomEvent('chatbot:cerrada', { bubbles: true }));

    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(boton.getAttribute('aria-label')).toBe('Abrir el asistente');
  });

  test('sin dónde colgarlo no falla', () => {
    expect(montarAsistente(null, { crearVentana: ventanaFalsa })).toBeNull();
  });
});
