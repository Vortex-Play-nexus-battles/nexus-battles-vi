/**
 * Chat grupal en la sala de batalla — revisión del modo jugador del 6-oct,
 * punto 15: «en plena batalla no será dinámico estar entre pestañas
 * escribiendo y jugando».
 *
 * Lo que se prueba: que es el MISMO chat de sala (se monta con `montarChat`
 * y el canal de la sala, una sola vez y solo al abrirlo), que vive en la
 * misma página (nada de `target=_blank` ni de navegar), que se abre y se
 * cierra con el teclado devolviendo el foco, que en móvil retiene el foco
 * como una hoja modal, y que con el panel cerrado cuenta los mensajes nuevos
 * de los demás sin anunciarlos uno a uno.
 */

import { jest } from '@jest/globals';

import { montarChatGrupal, textoDeNuevos, ANCHO_DE_HOJA } from './chat-grupal.js';
import { destinosDe } from './chat.js';

const SALA = '55555555-5555-5555-5555-555555555555';
const YO = '22222222-2222-2222-2222-222222222222';
const OTRO = '33333333-3333-3333-3333-333333333333';

const HUD = `
  <button type="button" data-accion="abrir-chat-grupal" hidden>
    Chat grupal<span data-zona="chat-nuevos" aria-hidden="true" hidden></span>
  </button>
`;

/** Un `montarChat` falso: dice con qué se montó y deja entregar mensajes vivos. */
function chatFalso() {
  const oyentes = new Map();
  const montar = jest.fn(async () => ({
    suscribir: (destino, alRecibir) => oyentes.set(destino, alRecibir),
  }));
  const entregar = (mensaje) => oyentes.get(destinosDe({ idSala: SALA }).vivo)?.(mensaje);
  return { montar, entregar };
}

const escritorio = () => ({ matches: false });
const movil = () => ({ matches: true });

function montarEn({ medios = escritorio, miId = YO } = {}) {
  document.body.innerHTML = HUD;
  const abridor = document.querySelector('[data-accion="abrir-chat-grupal"]');
  const falso = chatFalso();
  const chat = montarChatGrupal(document, {
    idSala: SALA,
    abridor,
    token: 'token-de-prueba',
    miId,
    montar: falso.montar,
    medios,
  });
  return { chat, abridor, ...falso };
}

/** Deja correr las promesas pendientes (el montaje del chat es asíncrono). */
const pendientes = () => new Promise((resolver) => setTimeout(resolver, 0));

afterEach(() => {
  delete document.body.dataset.chatGrupal;
});

describe('textoDeNuevos', () => {
  test('sin nuevos no hay cifra y el nombre es el de siempre', () => {
    expect(textoDeNuevos(0)).toEqual({ visible: '', accesible: 'Chat grupal' });
  });

  test('singular, plural y tope de 99+', () => {
    expect(textoDeNuevos(1)).toEqual({
      visible: '1',
      accesible: 'Chat grupal, 1 mensaje nuevo',
    });
    expect(textoDeNuevos(3).accesible).toBe('Chat grupal, 3 mensajes nuevos');
    expect(textoDeNuevos(120).visible).toBe('99+');
  });
});

describe('montarChatGrupal', () => {
  test('sin sala o sin botón no monta nada', () => {
    document.body.innerHTML = HUD;
    const abridor = document.querySelector('[data-accion="abrir-chat-grupal"]');
    expect(montarChatGrupal(document, { abridor, token: 't' })).toBeNull();
    expect(montarChatGrupal(document, { idSala: SALA, token: 't' })).toBeNull();
    expect(abridor.hidden).toBe(true);
  });

  test('el panel vive en la misma página, cerrado, y el botón dice lo que controla', () => {
    const { chat, abridor } = montarEn();
    expect(abridor.hidden).toBe(false);
    expect(abridor.tagName).toBe('BUTTON');
    expect(abridor.getAttribute('aria-controls')).toBe('chat-grupal');
    expect(abridor.getAttribute('aria-expanded')).toBe('false');
    expect(chat.panel.parentElement).toBe(document.body);
    expect(chat.panel.hidden).toBe(true);
    expect(chat.panel.getAttribute('role')).toBe('dialog');
    expect(chat.panel.getAttribute('aria-labelledby')).toBe('titulo-chat-grupal');
    expect(document.getElementById('titulo-chat-grupal').textContent).toBe('Chat grupal');
    // Nunca una pestaña nueva ni un enlace a otra vista.
    expect(document.querySelector('a[target="_blank"]')).toBeNull();
    expect(document.querySelector('a[href*="chat.html"]')).toBeNull();
  });

  test('el canal se abre al abrir el panel, una sola vez, con la sala y la sesión', async () => {
    const { chat, abridor, montar } = montarEn();
    expect(montar).not.toHaveBeenCalled();

    abridor.click();
    await pendientes();
    expect(montar).toHaveBeenCalledTimes(1);
    const [raiz, opciones] = montar.mock.calls[0];
    expect(raiz).toBe(chat.panel);
    expect(opciones).toEqual({ canal: { idSala: SALA }, token: 'token-de-prueba', miId: YO });
    // Lo que `montarChat` necesita está en el panel.
    expect(raiz.querySelector('[data-zona="mensajes"]')).not.toBeNull();
    expect(raiz.querySelector('[data-zona="sin-mensajes"]')).not.toBeNull();
    expect(raiz.querySelector('[data-zona="conexion"]')).not.toBeNull();
    expect(raiz.querySelector('form [data-zona="aviso"]')).not.toBeNull();
    expect(raiz.querySelector('textarea[name="texto"]').getAttribute('maxlength')).toBe('500');

    abridor.click();
    abridor.click();
    await pendientes();
    expect(montar).toHaveBeenCalledTimes(1);
  });

  test('abrir lleva el foco al redactor; Cerrar y Escape lo devuelven al botón', () => {
    const { chat, abridor } = montarEn();

    abridor.click();
    expect(chat.panel.hidden).toBe(false);
    expect(abridor.getAttribute('aria-expanded')).toBe('true');
    expect(document.activeElement).toBe(chat.panel.querySelector('textarea'));
    expect(document.body.dataset.chatGrupal).toBe('abierto');

    chat.panel.querySelector('[data-accion="cerrar-chat-grupal"]').click();
    expect(chat.panel.hidden).toBe(true);
    expect(abridor.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(abridor);
    expect(document.body.dataset.chatGrupal).toBeUndefined();

    abridor.click();
    chat.panel
      .querySelector('textarea')
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(chat.panel.hidden).toBe(true);
    expect(document.activeElement).toBe(abridor);
  });

  test('en escritorio no es modal: se sigue jugando con él abierto', () => {
    const { chat, abridor } = montarEn({ medios: escritorio });
    abridor.click();
    expect(chat.panel.dataset.hoja).toBe('no');
    expect(chat.panel.getAttribute('aria-modal')).toBe('false');
  });

  test('en móvil es una hoja modal: el foco no se escapa por detrás', () => {
    const consultas = [];
    const { chat, abridor } = montarEn({
      medios: (consulta) => {
        consultas.push(consulta);
        return movil();
      },
    });
    abridor.click();
    expect(consultas).toContain(`(max-width: ${ANCHO_DE_HOJA}px)`);
    expect(chat.panel.dataset.hoja).toBe('si');
    expect(chat.panel.getAttribute('aria-modal')).toBe('true');

    const enfocables = [...chat.panel.querySelectorAll('button, textarea')];
    const primero = enfocables[0];
    const ultimo = enfocables[enfocables.length - 1];

    ultimo.focus();
    ultimo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    expect(document.activeElement).toBe(primero);

    primero.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }),
    );
    expect(document.activeElement).toBe(ultimo);
  });

  test('cerrado cuenta los mensajes nuevos de otros; los propios no; abrir los da por leídos', async () => {
    const { chat, abridor, entregar } = montarEn();
    const marca = abridor.querySelector('[data-zona="chat-nuevos"]');

    // El canal se abre la primera vez que se abre el panel.
    abridor.click();
    await pendientes();
    chat.cerrar();

    entregar({ id: 'm1', autor: { id: OTRO }, texto: 'hola' });
    entregar({ id: 'm2', autor: { id: YO }, texto: 'mío' });
    entregar({ id: 'm3', autor: { id: OTRO }, texto: '¿listos?' });

    expect(chat.nuevos()).toBe(2);
    expect(marca.hidden).toBe(false);
    expect(marca.textContent).toBe('2');
    expect(abridor.getAttribute('aria-label')).toBe('Chat grupal, 2 mensajes nuevos');

    abridor.click();
    expect(chat.nuevos()).toBe(0);
    expect(marca.hidden).toBe(true);
    expect(abridor.getAttribute('aria-label')).toBe('Chat grupal');

    // Abierto, lo que llega se lee en el panel: no se cuenta.
    entregar({ id: 'm4', autor: { id: OTRO }, texto: 'ya' });
    expect(chat.nuevos()).toBe(0);
  });
});
