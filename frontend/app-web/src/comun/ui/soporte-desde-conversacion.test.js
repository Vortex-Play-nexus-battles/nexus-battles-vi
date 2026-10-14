/**
 * «Crear solicitud con esta conversación» — ms-chatbot.yaml 1.8.0 (7.4.3).
 *
 * Después de que el asistente no entendió o de un «no me sirvió», «Hablar con
 * soporte» ofrece abrir la solicitud en un clic; el formulario sigue debajo.
 * Va en su propio archivo para no tocar las pruebas de la ventana que cambia
 * #506.
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../cliente-chatbot.js';
import { TEXTOS_SOPORTE, avisoDesdeConversacion, crearPanelSoporte } from './soporte-chatbot.js';
import { crearVentanaChatbot } from './ventana-chatbot.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const CREADA = {
  ticketId: 't-9',
  categoria: 'SUBASTA_Y_COMERCIO',
  asunto: 'Sin respuesta del asistente: zxqwv plorf',
  mensaje: 'Le pregunté al asistente: «zxqwv plorf» y no la entendió.',
  estado: 'ABIERTO',
  respuesta: null,
  creadoEn: '2026-10-14T15:00:00Z',
  actualizadoEn: '2026-10-14T15:00:00Z',
};

function clienteFalso(sobrescribir = {}) {
  return {
    abrirTicket: jest.fn(async () => CREADA),
    abrirTicketDesdeConversacion: jest.fn(async () => CREADA),
    misTickets: jest.fn(async () => []),
    ...sobrescribir,
  };
}

async function abrirPanel({ cliente = clienteFalso(), autenticado = true, opciones } = {}) {
  const panel = crearPanelSoporte({
    cliente,
    sesion: () => ({ autenticado }),
    login: '/login',
    chatGeneral: '/chat.html',
    alVolver: jest.fn(),
  });
  document.body.append(panel.elemento);
  panel.abrir(opciones);
  await esperar();
  return { panel, el: panel.elemento, cliente };
}

const botonCrear = (el) => el.querySelector('[data-accion="crear-solicitud-desde-conversacion"]');

beforeEach(() => {
  document.body.replaceChildren();
  sessionStorage.clear();
});

describe('el panel de soporte', () => {
  test('con una pregunta sin resolver ofrece crear la solicitud, encima del formulario', async () => {
    const { el, panel } = await abrirPanel({
      opciones: { conConversacion: true, vista: 'SUBASTAS' },
    });

    const crear = botonCrear(el);
    expect(crear.textContent).toBe(TEXTOS_SOPORTE.crearDesdeConversacion);
    expect(el.textContent).toContain(TEXTOS_SOPORTE.desdeConversacion);
    // El formulario sigue ahí, después.
    const formulario = el.querySelector('form');
    expect(formulario).not.toBeNull();
    expect(
      crear.compareDocumentPosition(formulario) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    // Es lo primero que recibe el foco.
    panel.enfocar();
    expect(document.activeElement).toBe(crear);
  });

  test('un clic la crea con la vista, lo confirma y la muestra en «Tus solicitudes»', async () => {
    const cliente = clienteFalso({
      misTickets: jest.fn().mockResolvedValueOnce([]).mockResolvedValue([CREADA]),
    });
    const { el } = await abrirPanel({
      cliente,
      opciones: { conConversacion: true, vista: 'SUBASTAS' },
    });

    botonCrear(el).click();
    await esperar();
    await esperar();

    expect(cliente.abrirTicketDesdeConversacion).toHaveBeenCalledTimes(1);
    expect(cliente.abrirTicketDesdeConversacion).toHaveBeenCalledWith({ vista: 'SUBASTAS' });
    const aviso = el.querySelector('.chatbot-soporte__aviso');
    expect(aviso.hidden).toBe(false);
    expect(aviso.querySelector('[role="status"]').textContent).toContain(
      TEXTOS_SOPORTE.creadaDesdeConversacion,
    );
    expect(aviso.textContent).toContain('«Tus solicitudes»');
    // Ya no se ofrece otra vez, y la nueva está en la lista.
    expect(botonCrear(el)).toBeNull();
    expect(cliente.misTickets).toHaveBeenCalledTimes(2);
    expect(el.querySelector('[data-ticket-id="t-9"]').textContent).toContain(CREADA.asunto);
  });

  test('un doble clic no abre dos', async () => {
    let terminar;
    const cliente = clienteFalso({
      abrirTicketDesdeConversacion: jest.fn(
        () =>
          new Promise((resolver) => {
            terminar = resolver;
          }),
      ),
    });
    const { el } = await abrirPanel({ cliente, opciones: { conConversacion: true } });
    const crear = botonCrear(el);

    crear.click();
    crear.click();
    expect(crear.disabled).toBe(true);
    terminar(CREADA);
    await esperar();

    expect(cliente.abrirTicketDesdeConversacion).toHaveBeenCalledTimes(1);
  });

  test('si ya hay una abierta lo dice y deja el formulario', async () => {
    const cliente = clienteFalso({
      abrirTicketDesdeConversacion: jest.fn(async () => {
        throw new ErrorDelChatbot({ title: 'x', motivo: 'TICKET_ABIERTO' }, 409);
      }),
    });
    const { el } = await abrirPanel({ cliente, opciones: { conConversacion: true } });

    botonCrear(el).click();
    await esperar();

    expect(el.querySelector('.chatbot-soporte__aviso').textContent).toContain(
      TEXTOS_SOPORTE.yaAbierta,
    );
    expect(botonCrear(el).disabled).toBe(false);
    expect(el.querySelector('form')).not.toBeNull();
  });

  test('sin una conversación de la cual partir lo dice', async () => {
    const cliente = clienteFalso({
      abrirTicketDesdeConversacion: jest.fn(async () => {
        throw new ErrorDelChatbot({ title: 'x', motivo: 'SIN_CONVERSACION' }, 409);
      }),
    });
    const { el } = await abrirPanel({ cliente, opciones: { conConversacion: true } });

    botonCrear(el).click();
    await esperar();

    expect(el.querySelector('.chatbot-soporte__aviso').textContent).toContain(
      TEXTOS_SOPORTE.sinConversacion,
    );
  });

  test('sin pregunta sin resolver, o para un visitante, no se ofrece', async () => {
    const sinPregunta = await abrirPanel();
    expect(botonCrear(sinPregunta.el)).toBeNull();
    expect(sinPregunta.el.querySelector('form')).not.toBeNull();

    document.body.replaceChildren();
    const visitante = await abrirPanel({ autenticado: false, opciones: { conConversacion: true } });
    expect(botonCrear(visitante.el)).toBeNull();
  });

  test('al volver a abrir sin la pregunta, ya no se ofrece', async () => {
    const { el, panel } = await abrirPanel({ opciones: { conConversacion: true } });
    expect(botonCrear(el)).not.toBeNull();

    panel.abrir();
    await esperar();

    expect(botonCrear(el)).toBeNull();
  });
});

test('avisoDesdeConversacion: SIN_CONVERSACION es suyo; lo demás, como el formulario', () => {
  expect(
    avisoDesdeConversacion(new ErrorDelChatbot({ title: 'x', motivo: 'SIN_CONVERSACION' }, 409)),
  ).toMatchObject({ tono: 'advertencia', titulo: TEXTOS_SOPORTE.sinConversacion });
  expect(avisoDesdeConversacion({ estado: 409, motivo: 'SIN_CONVERSACION' })).toMatchObject({
    titulo: TEXTOS_SOPORTE.sinConversacion,
  });
  expect(avisoDesdeConversacion(new ErrorDelChatbot(null, 0))).toMatchObject({
    titulo: TEXTOS_SOPORTE.noDisponible,
  });
});

describe('la ventana', () => {
  const BOT_ESCALADO = {
    id: 'm-bot-1',
    remitente: 'BOT',
    contenido: 'No entendí tu pregunta.',
    adjuntoUrl: null,
    fechaEnvio: '2026-10-14T15:00:01Z',
    enriquecido: {
      pasos: [],
      enlaces: [],
      tarjetas: [],
      respuestasRapidas: [],
      ofrecerSoporteHumano: true,
    },
  };
  const BOT_RESUELTO = { ...BOT_ESCALADO, id: 'm-bot-2', enriquecido: null };

  async function ventanaCon(historial) {
    const cliente = {
      ...clienteFalso(),
      obtenerHistorial: jest.fn(async () => historial),
      enviarMensaje: jest.fn(),
      limpiarHistorial: jest.fn(),
      calificar: jest.fn(),
      preferencias: jest.fn(async () => ({ idioma: 'AUTOMATICO', nivelDetalle: 'NORMAL' })),
      guardarPreferencias: jest.fn(),
      sugerencias: jest.fn(async () => []),
    };
    const ventana = crearVentanaChatbot({
      cliente,
      sesion: () => ({ autenticado: true, apodo: 'Kai' }),
      chatGeneral: 'http://localhost/chat.html',
      esperaAutocompletar: 0,
      vista: () => 'INVENTARIO',
    });
    ventana.abrir();
    await esperar();
    return { el: ventana.elemento, cliente };
  }

  test('«Hablar con soporte» tras no entender ofrece el clic, con la vista donde está', async () => {
    const { el, cliente } = await ventanaCon([BOT_ESCALADO]);

    el.querySelector('[data-accion="ofrecer-soporte"]').click();
    await esperar();
    botonCrear(el).click();
    await esperar();

    expect(cliente.abrirTicketDesdeConversacion).toHaveBeenCalledWith({ vista: 'INVENTARIO' });
  });

  test('sin respuestas que ofrecieran soporte, «Soporte» solo trae el formulario', async () => {
    const { el } = await ventanaCon([BOT_RESUELTO]);

    el.querySelector('[data-accion="hablar-con-soporte"]').click();
    await esperar();

    expect(el.querySelector('[data-chatbot-soporte]').hidden).toBe(false);
    expect(botonCrear(el)).toBeNull();
  });
});
