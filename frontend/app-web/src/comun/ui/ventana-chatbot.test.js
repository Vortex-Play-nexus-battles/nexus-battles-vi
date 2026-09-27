/**
 * Ventana del asistente — HU-CHA-001, HU-CHA-008, HU-CHA-011.
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../cliente-chatbot.js';
import { TEXTOS, crearVentanaChatbot, urlPermitida } from './ventana-chatbot.js';

const BOT = {
  id: 'm-bot-1',
  remitente: 'BOT',
  contenido: 'Puedes pujar desde la sección Subasta.',
  adjuntoUrl: null,
  fechaEnvio: '2026-09-23T18:00:00Z',
};
const USUARIO = {
  id: 'm-usr-1',
  remitente: 'USUARIO',
  contenido: 'como pujo',
  adjuntoUrl: null,
  fechaEnvio: '2026-09-23T17:59:59Z',
};

// Armado por partes para que ESLint (no-script-url) no lo tome por código.
const URL_JAVASCRIPT = ['javascript', 'alert(1)'].join(':');

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

function clienteFalso(sobrescribir = {}) {
  return {
    obtenerHistorial: jest.fn(async () => []),
    enviarMensaje: jest.fn(async () => ({ ...BOT, id: 'm-bot-nuevo' })),
    limpiarHistorial: jest.fn(async () => null),
    calificar: jest.fn(async () => ({ id: 'c-1' })),
    ...sobrescribir,
  };
}

async function montar({ cliente = clienteFalso(), sesion, confirmarBorrado } = {}) {
  const lanzador = document.createElement('button');
  document.body.append(lanzador);
  const ventana = crearVentanaChatbot({
    cliente,
    sesion: sesion ?? (() => ({ autenticado: false, apodo: '' })),
    chatGeneral: 'http://localhost/chat.html',
    confirmarBorrado: confirmarBorrado ?? (async () => true),
  });
  ventana.abrir(lanzador);
  await esperar();
  const el = ventana.elemento;
  return {
    ventana,
    cliente,
    lanzador,
    el,
    entrada: el.querySelector('textarea[name="contenido"]'),
    formulario: el.querySelector('form'),
    mensajes: () => [...el.querySelectorAll('.chatbot-ventana__mensaje')],
  };
}

async function enviar(vista, texto) {
  vista.entrada.value = texto;
  vista.formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  await esperar();
}

beforeEach(() => {
  document.body.replaceChildren();
});

describe('abrir y cerrar', () => {
  test('nace oculta, es un diálogo no modal con título, y se abre al pedirlo', async () => {
    const ventana = crearVentanaChatbot({ cliente: clienteFalso(), sesion: () => ({}) });
    expect(ventana.elemento.hidden).toBe(true);
    expect(ventana.elemento.getAttribute('role')).toBe('dialog');
    expect(ventana.elemento.getAttribute('aria-modal')).toBe('false');
    const idTitulo = ventana.elemento.getAttribute('aria-labelledby');
    expect(document.getElementById(idTitulo).textContent).toBe(TEXTOS.titulo);

    ventana.abrir();
    await esperar();
    expect(ventana.abierta()).toBe(true);
  });

  test('Escape la cierra y devuelve el foco a quien la abrió', async () => {
    const vista = await montar();
    vista.entrada.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(vista.ventana.abierta()).toBe(false);
    expect(document.activeElement).toBe(vista.lanzador);
  });

  test('el historial se carga una sola vez aunque se abra varias veces', async () => {
    const vista = await montar();
    vista.ventana.cerrar();
    vista.ventana.abrir();
    await esperar();
    expect(vista.cliente.obtenerHistorial).toHaveBeenCalledTimes(1);
  });

  test('distingue visitante de jugador con sesión', async () => {
    const visitante = await montar();
    expect(visitante.el.querySelector('.chatbot-ventana__estado').textContent).toBe(
      TEXTOS.estadoVisitante,
    );

    document.body.replaceChildren();
    const jugador = await montar({ sesion: () => ({ autenticado: true, apodo: 'Ana' }) });
    expect(jugador.el.querySelector('.chatbot-ventana__estado').textContent).toContain('Ana');
  });
});

describe('historial', () => {
  test('pinta los mensajes y solo los del bot se pueden calificar', async () => {
    const vista = await montar({
      cliente: clienteFalso({ obtenerHistorial: jest.fn(async () => [USUARIO, BOT]) }),
    });

    const [usuario, bot] = vista.mensajes();
    expect(usuario.classList).toContain('chatbot-ventana__mensaje--usuario');
    expect(usuario.querySelector('.chatbot-calificacion')).toBeNull();
    expect(bot.classList).toContain('chatbot-ventana__mensaje--bot');
    expect(bot.querySelector('.chatbot-ventana__texto').textContent).toBe(BOT.contenido);
    expect(bot.querySelector('.chatbot-calificacion')).not.toBeNull();
  });

  test('sin conversación previa muestra la bienvenida, sin calificación', async () => {
    const vista = await montar();
    const [bienvenida] = vista.mensajes();
    expect(bienvenida.textContent).toBe(TEXTOS.bienvenida);
    expect(bienvenida.querySelector('.chatbot-calificacion')).toBeNull();
  });

  test('el texto del servidor nunca se interpreta como HTML', async () => {
    const malicioso = { ...BOT, contenido: '<img src=x onerror="alert(1)">' };
    const vista = await montar({
      cliente: clienteFalso({ obtenerHistorial: jest.fn(async () => [malicioso]) }),
    });
    expect(vista.el.querySelector('img')).toBeNull();
    expect(vista.mensajes()[0].querySelector('.chatbot-ventana__texto').textContent).toBe(
      malicioso.contenido,
    );
  });

  // CA-03 de HU-CHA-001: servicio caído -> se informa y se ofrece la vía alternativa.
  test('si el servicio no está, lo dice, ofrece el chat general y bloquea el envío', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        obtenerHistorial: jest.fn(async () => {
          throw new ErrorDelChatbot(null, 0);
        }),
      }),
    });

    const aviso = vista.el.querySelector('.chatbot-ventana__aviso');
    expect(aviso.hidden).toBe(false);
    expect(aviso.textContent).toContain(TEXTOS.noDisponibleTitulo);
    expect(aviso.querySelector('[data-accion="ir-al-chat"]').getAttribute('href')).toBe(
      'http://localhost/chat.html',
    );
    expect(vista.entrada.disabled).toBe(true);
  });

  test('Reintentar vuelve a cargar y, si responde, habilita el envío', async () => {
    const obtenerHistorial = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDelChatbot(null, 503))
      .mockResolvedValueOnce([BOT]);
    const vista = await montar({ cliente: clienteFalso({ obtenerHistorial }) });

    vista.el.querySelector('[data-accion="reintentar"]').click();
    await esperar();

    expect(obtenerHistorial).toHaveBeenCalledTimes(2);
    expect(vista.el.querySelector('.chatbot-ventana__aviso').hidden).toBe(true);
    expect(vista.entrada.disabled).toBe(false);
    expect(vista.mensajes()).toHaveLength(1);
  });
});

describe('enviar', () => {
  test('pinta la pregunta y la respuesta, y limpia la caja de texto', async () => {
    const vista = await montar();
    await enviar(vista, '  como pujo  ');

    expect(vista.cliente.enviarMensaje).toHaveBeenCalledWith('como pujo', null);
    const mensajes = vista.mensajes();
    expect(mensajes).toHaveLength(2);
    expect(mensajes[0].textContent).toContain('como pujo');
    expect(mensajes[1].dataset.mensajeId).toBe('m-bot-nuevo');
    expect(vista.entrada.value).toBe('');
  });

  test('un mensaje vacío no se envía', async () => {
    const vista = await montar();
    await enviar(vista, '   ');
    expect(vista.cliente.enviarMensaje).not.toHaveBeenCalled();
  });

  test('Enter envía y Shift+Enter no', async () => {
    const vista = await montar();
    vista.entrada.value = 'hola';

    vista.entrada.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, bubbles: true }),
    );
    await esperar();
    expect(vista.cliente.enviarMensaje).not.toHaveBeenCalled();

    vista.entrada.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    await esperar();
    expect(vista.cliente.enviarMensaje).toHaveBeenCalledWith('hola', null);
  });

  test('la URL de captura se valida y se envía con el mensaje', async () => {
    const vista = await montar();
    vista.el.querySelector('[data-accion="adjuntar-captura"]').click();
    const url = vista.el.querySelector('input[name="adjuntoUrl"]');

    url.value = URL_JAVASCRIPT;
    await enviar(vista, 'mira');
    expect(vista.cliente.enviarMensaje).not.toHaveBeenCalled();
    expect(vista.el.querySelector('.chatbot-ventana__captura').textContent).toContain(
      TEXTOS.adjuntoInvalido,
    );

    url.value = 'https://img.example/captura.png';
    await enviar(vista, 'mira');
    expect(vista.cliente.enviarMensaje).toHaveBeenCalledWith(
      'mira',
      'https://img.example/captura.png',
    );
    expect(vista.el.querySelector('.chatbot-ventana__adjunto').getAttribute('href')).toBe(
      'https://img.example/captura.png',
    );
  });

  test('si falla, avisa y conserva lo que escribió', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        enviarMensaje: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'Ruta sin servicio en el borde' }, 404);
        }),
      }),
    });
    await enviar(vista, 'hola');

    expect(vista.entrada.value).toBe('hola');
    expect(vista.el.querySelector('.chatbot-ventana__aviso').textContent).toContain(
      TEXTOS.noDisponibleTitulo,
    );
    expect(vista.el.querySelector('.chatbot-ventana__escribiendo')).toBeNull();
  });

  test('un 400 se explica como mensaje inválido', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        enviarMensaje: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'Bad Request' }, 400);
        }),
      }),
    });
    await enviar(vista, 'hola');
    expect(vista.el.querySelector('.chatbot-ventana__aviso').textContent).toContain(
      TEXTOS.mensajeInvalido,
    );
  });
});

describe('calificar (HU-CHA-011)', () => {
  async function conUnaRespuesta(cliente) {
    return montar({
      cliente: clienteFalso({ obtenerHistorial: jest.fn(async () => [BOT]), ...cliente }),
    });
  }

  test('👍 califica como útil y agradece', async () => {
    const vista = await conUnaRespuesta();
    vista.el.querySelector('[data-accion="calificar-util"]').click();
    await esperar();

    expect(vista.cliente.calificar).toHaveBeenCalledWith('m-bot-1', true, null);
    expect(vista.el.querySelector('.chatbot-calificacion').textContent).toBe(TEXTOS.gracias);
  });

  test('👎 pide un comentario opcional y lo envía', async () => {
    const vista = await conUnaRespuesta();
    const noUtil = vista.el.querySelector('[data-accion="calificar-no-util"]');
    noUtil.click();
    expect(noUtil.getAttribute('aria-expanded')).toBe('true');

    vista.el.querySelector('.chatbot-calificacion textarea').value = 'no respondió';
    vista.el.querySelector('[data-accion="enviar-calificacion"]').click();
    await esperar();

    expect(vista.cliente.calificar).toHaveBeenCalledWith('m-bot-1', false, 'no respondió');
    expect(vista.el.querySelector('.chatbot-calificacion').textContent).toBe(TEXTOS.gracias);
  });

  test('Cancelar esconde el comentario sin calificar', async () => {
    const vista = await conUnaRespuesta();
    vista.el.querySelector('[data-accion="calificar-no-util"]').click();
    vista.el.querySelector('[data-accion="cancelar-calificacion"]').click();

    expect(vista.el.querySelector('.chatbot-calificacion__comentario').hidden).toBe(true);
    expect(vista.cliente.calificar).not.toHaveBeenCalled();
  });

  // El historial no dice qué respuestas ya se calificaron: el 409 lo aclara.
  test('una respuesta ya calificada lo dice en vez de mostrar un error', async () => {
    const vista = await conUnaRespuesta({
      calificar: jest.fn(async () => {
        throw new ErrorDelChatbot({ title: 'Conflict' }, 409);
      }),
    });
    vista.el.querySelector('[data-accion="calificar-util"]').click();
    await esperar();
    expect(vista.el.querySelector('.chatbot-calificacion').textContent).toBe(TEXTOS.yaCalificada);
  });

  test('otro error deja los botones para reintentar', async () => {
    const vista = await conUnaRespuesta({
      calificar: jest.fn(async () => {
        throw new ErrorDelChatbot({ title: 'Error' }, 500);
      }),
    });
    const util = vista.el.querySelector('[data-accion="calificar-util"]');
    util.click();
    await esperar();

    expect(util.disabled).toBe(false);
    expect(vista.el.querySelector('.chatbot-calificacion__nota').textContent).toBe(
      TEXTOS.errorAlCalificar,
    );
  });
});

describe('borrar la conversación', () => {
  test('con confirmación borra y vuelve a la bienvenida', async () => {
    const vista = await montar({
      cliente: clienteFalso({ obtenerHistorial: jest.fn(async () => [USUARIO, BOT]) }),
    });
    vista.el.querySelector('[data-accion="borrar-conversacion"]').click();
    await esperar();

    expect(vista.cliente.limpiarHistorial).toHaveBeenCalled();
    expect(vista.mensajes()).toHaveLength(1);
    expect(vista.mensajes()[0].textContent).toBe(TEXTOS.bienvenida);
  });

  test('sin confirmación no borra nada', async () => {
    const vista = await montar({
      cliente: clienteFalso({ obtenerHistorial: jest.fn(async () => [USUARIO, BOT]) }),
      confirmarBorrado: async () => false,
    });
    vista.el.querySelector('[data-accion="borrar-conversacion"]').click();
    await esperar();

    expect(vista.cliente.limpiarHistorial).not.toHaveBeenCalled();
    expect(vista.mensajes()).toHaveLength(2);
  });
});

test('urlPermitida solo deja pasar http y https', () => {
  expect(urlPermitida('https://a.example/x.png')).toBe('https://a.example/x.png');
  expect(urlPermitida('http://a.example/')).toBe('http://a.example/');
  expect(urlPermitida(URL_JAVASCRIPT)).toBeNull();
  expect(urlPermitida('data:image/png;base64,xx')).toBeNull();
  expect(urlPermitida('no es una url')).toBeNull();
  expect(urlPermitida(null)).toBeNull();
});
