/**
 * Pruebas del botón flotante del asistente (HU-CHA-001 #505, CA-03).
 *
 * Lo que se afirma aquí es justo lo que antes no pasaba: que pulsarlo **hace
 * algo**, que lo que dice es verdad (el asistente no existe todavía) y que
 * ofrece la vía alternativa que exige el criterio.
 */

import { jest } from '@jest/globals';

import { AVISO_SIN_ASISTENTE, montarAsistente } from './asistente.js';

beforeEach(() => {
  document.body.innerHTML = '';
});

describe('montarAsistente()', () => {
  test('monta el boton flotante con nombre accesible', () => {
    const boton = montarAsistente(document);

    expect(boton.classList.contains('chatbot-flotante')).toBe(true);
    expect(boton.getAttribute('aria-label')).toBe('Abrir el asistente');
    expect(boton.type).toBe('button');
    expect(document.body.contains(boton)).toBe(true);
  });

  test('reutiliza el botón que la vista ya traiga en su HTML, sin duplicarlo', () => {
    document.body.innerHTML =
      '<button class="chatbot-flotante" type="button" aria-label="Abrir el asistente">IA</button>';

    montarAsistente(document);

    expect(document.querySelectorAll('.chatbot-flotante')).toHaveLength(1);
  });

  test('pulsarlo ya NO es un control muerto: abre un dialogo', () => {
    const boton = montarAsistente(document);

    boton.click();

    const dialogo = document.querySelector('[role="dialog"]');
    expect(dialogo).not.toBeNull();
    expect(dialogo.getAttribute('aria-modal')).toBe('true');
  });

  test('dice la verdad sobre el estado del asistente (CA-03)', () => {
    montarAsistente(document).click();

    const dialogo = document.querySelector('[role="dialog"]');
    expect(dialogo.textContent).toContain(AVISO_SIN_ASISTENTE.titulo);
    expect(dialogo.textContent).toContain('entrega posterior');
  });

  test('ofrece la via alternativa de contacto, que es el chat general', () => {
    montarAsistente(document).click();

    const enlace = document.querySelector('[data-accion="ir-al-chat"]');
    expect(enlace).not.toBeNull();
    expect(enlace.getAttribute('href')).toContain('chat.html');
  });

  test('con el servicio disponible llama a quien lo abra y no avisa de nada', () => {
    const abrir = jest.fn();

    montarAsistente(document, { disponible: true, alAbrir: abrir }).click();

    expect(abrir).toHaveBeenCalledTimes(1);
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });

  test('con disponible pero sin alAbrir sigue avisando en vez de no hacer nada', () => {
    montarAsistente(document, { disponible: true }).click();

    expect(document.querySelector('[role="dialog"]')).not.toBeNull();
  });

  test('el dialogo se puede cerrar y devuelve la pantalla a su sitio', () => {
    const boton = montarAsistente(document);
    boton.click();

    document.querySelector('.dialogo__cerrar').click();

    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });
});
