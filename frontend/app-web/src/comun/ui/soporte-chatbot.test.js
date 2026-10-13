/**
 * «Hablar con soporte» dentro de la ventana del asistente.
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../cliente-chatbot.js';
import {
  CATEGORIAS,
  ESTADOS,
  TEXTOS_SOPORTE,
  avisoDeError,
  crearPanelSoporte,
} from './soporte-chatbot.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const TICKET = {
  ticketId: 't-1',
  categoria: 'SOPORTE_TECNICO',
  asunto: 'No carga mi inventario',
  mensaje: 'Se queda cargando',
  estado: 'RESUELTO',
  respuesta: 'Ya quedó, vuelve a entrar.',
  creadoEn: '2026-09-28T18:00:00Z',
  actualizadoEn: '2026-09-28T19:00:00Z',
};

function clienteFalso(sobrescribir = {}) {
  return {
    abrirTicket: jest.fn(async () => ({ ...TICKET, estado: 'ABIERTO', respuesta: null })),
    misTickets: jest.fn(async () => []),
    ...sobrescribir,
  };
}

async function montar({ cliente = clienteFalso(), autenticado = true, login = '/login' } = {}) {
  const alVolver = jest.fn();
  const panel = crearPanelSoporte({
    cliente,
    sesion: () => ({ autenticado }),
    login,
    chatGeneral: '/chat.html',
    alVolver,
  });
  document.body.append(panel.elemento);
  panel.abrir();
  await esperar();
  const el = panel.elemento;
  return {
    panel,
    el,
    cliente,
    alVolver,
    formulario: el.querySelector('form'),
    asunto: el.querySelector('input[name="asunto"]'),
    mensaje: el.querySelector('textarea[name="mensaje"]'),
    categoria: el.querySelector('select[name="categoria"]'),
    aviso: () => el.querySelector('.chatbot-soporte__aviso').textContent,
  };
}

async function enviar(vista, asunto, mensaje) {
  vista.asunto.value = asunto;
  vista.mensaje.value = mensaje;
  vista.formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  await esperar();
  await esperar();
}

beforeEach(() => {
  document.body.replaceChildren();
});

describe('visitante', () => {
  test('no ve el formulario: se le ofrece iniciar sesión y el chat general', async () => {
    const vista = await montar({ autenticado: false });

    expect(vista.formulario).toBeNull();
    expect(vista.el.textContent).toContain(TEXTOS_SOPORTE.visitante);
    expect(vista.el.querySelector('[data-accion="iniciar-sesion"]').getAttribute('href')).toBe(
      '/login',
    );
    expect(vista.el.querySelector('[data-accion="chat-general"]').getAttribute('href')).toBe(
      '/chat.html',
    );
    expect(vista.cliente.misTickets).not.toHaveBeenCalled();
  });

  test('sin ruta de login solo ofrece el chat general', async () => {
    const vista = await montar({ autenticado: false, login: null });

    expect(vista.el.querySelector('[data-accion="iniciar-sesion"]')).toBeNull();
    expect(vista.el.querySelector('[data-accion="chat-general"]')).not.toBeNull();
  });
});

describe('jugador', () => {
  test('ve el formulario con las 8 categorías y sus solicitudes', async () => {
    const vista = await montar({
      cliente: clienteFalso({ misTickets: jest.fn(async () => [TICKET]) }),
    });

    expect(vista.categoria.options).toHaveLength(CATEGORIAS.length);
    expect(vista.asunto.getAttribute('maxlength')).toBe('150');
    expect(vista.mensaje.getAttribute('maxlength')).toBe('2000');
    const item = vista.el.querySelector('.chatbot-soporte__ticket');
    expect(item.textContent).toContain('No carga mi inventario');
    expect(item.textContent).toContain(ESTADOS.RESUELTO.texto);
    expect(item.textContent).toContain('Ya quedó, vuelve a entrar.');
  });

  test('sin solicitudes lo dice', async () => {
    const vista = await montar();

    expect(vista.el.querySelector('.chatbot-soporte__vacio').textContent).toBe(
      TEXTOS_SOPORTE.ninguna,
    );
  });

  test('si la lista no carga lo dice sin romper el formulario', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        misTickets: jest.fn(async () => {
          throw new ErrorDelChatbot(null, 0);
        }),
      }),
    });

    expect(vista.el.querySelector('.chatbot-soporte__vacio').textContent).toBe(
      TEXTOS_SOPORTE.errorAlCargar,
    );
    expect(vista.formulario).not.toBeNull();
  });

  test('el texto del servidor nunca se interpreta como HTML', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        misTickets: jest.fn(async () => [{ ...TICKET, asunto: '<img src=x onerror=alert(1)>' }]),
      }),
    });

    expect(vista.el.querySelector('img')).toBeNull();
    expect(vista.el.querySelector('.chatbot-soporte__asunto').textContent).toBe(
      '<img src=x onerror=alert(1)>',
    );
  });

  test('sin asunto o sin mensaje no envía y marca el campo', async () => {
    const vista = await montar();

    await enviar(vista, '   ', 'algo');
    expect(vista.cliente.abrirTicket).not.toHaveBeenCalled();
    expect(vista.el.textContent).toContain(TEXTOS_SOPORTE.faltaAsunto);

    await enviar(vista, 'Asunto', '  ');
    expect(vista.cliente.abrirTicket).not.toHaveBeenCalled();
    expect(vista.el.textContent).toContain(TEXTOS_SOPORTE.faltaMensaje);
  });

  test('envía la solicitud recortada, avisa y recarga la lista', async () => {
    const vista = await montar();
    vista.categoria.value = 'CUENTA_Y_REGISTRO';

    await enviar(vista, '  No entro  ', '  Olvidé mi contraseña  ');

    expect(vista.cliente.abrirTicket).toHaveBeenCalledWith({
      categoria: 'CUENTA_Y_REGISTRO',
      asunto: 'No entro',
      mensaje: 'Olvidé mi contraseña',
    });
    expect(vista.aviso()).toContain(TEXTOS_SOPORTE.enviada);
    expect(vista.asunto.value).toBe('');
    expect(vista.mensaje.value).toBe('');
    expect(vista.cliente.misTickets).toHaveBeenCalledTimes(2);
  });

  test('un error del servidor se explica por su motivo', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        abrirTicket: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'x', motivo: 'TICKET_ABIERTO' }, 409);
        }),
      }),
    });

    await enviar(vista, 'Asunto', 'Mensaje');

    expect(vista.aviso()).toContain(TEXTOS_SOPORTE.yaAbierta);
    expect(vista.asunto.value).toBe('Asunto');
  });

  test('Volver al chat avisa a la ventana', async () => {
    const vista = await montar();

    vista.el.querySelector('[data-accion="volver-al-chat"]').click();

    expect(vista.alVolver).toHaveBeenCalled();
  });

  test('enfocar lleva el foco al primer control', async () => {
    const vista = await montar();

    vista.panel.enfocar();

    expect(document.activeElement).toBe(vista.categoria);
  });
});

describe('avisoDeError', () => {
  const con = (motivo, estado = 400) => new ErrorDelChatbot({ title: 'x', motivo }, estado);

  test.each([
    [con('TICKET_ABIERTO', 409), TEXTOS_SOPORTE.yaAbierta, 'advertencia'],
    [con('SESION_REQUERIDA', 401), TEXTOS_SOPORTE.sinSesion, 'advertencia'],
    [new ErrorDelChatbot(null, 403), TEXTOS_SOPORTE.sinSesion, 'advertencia'],
    [con('CONTENIDO_BLOQUEADO', 422), TEXTOS_SOPORTE.bloqueado, 'advertencia'],
    [con('LIMITE_DE_FRECUENCIA', 429), TEXTOS_SOPORTE.muchas, 'advertencia'],
    [con('MODERACION_NO_DISPONIBLE', 503), TEXTOS_SOPORTE.noDisponible, 'error'],
    [new ErrorDelChatbot(null, 0), TEXTOS_SOPORTE.noDisponible, 'error'],
    [new ErrorDelChatbot({ title: 'Bad Request' }, 400), TEXTOS_SOPORTE.invalido, 'advertencia'],
    [new ErrorDelChatbot({ title: 'x' }, 500), TEXTOS_SOPORTE.error, 'error'],
  ])('%#: se elige el aviso por motivo y estado', (error, titulo, tono) => {
    expect(avisoDeError(error)).toMatchObject({ titulo, tono });
  });
});
