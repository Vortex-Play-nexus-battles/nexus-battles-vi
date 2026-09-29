/**
 * Ventana del asistente — HU-CHA-001, HU-CHA-008, HU-CHA-011.
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../cliente-chatbot.js';
import {
  PAGINA_DEL_HISTORIAL,
  TAMANO,
  TEXTOS,
  acotarTamano,
  crearVentanaChatbot,
  urlPermitida,
} from './ventana-chatbot.js';

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
    abrirTicket: jest.fn(async () => ({ ticketId: 't-1', estado: 'ABIERTO' })),
    misTickets: jest.fn(async () => []),
    sugerencias: jest.fn(async () => [
      {
        clave: 'k-1',
        titulo: 'Cómo funciona el Torneo',
        categoria: 'MODALIDAD_JUEGO',
        pregunta: 'Cómo funciona el Torneo',
      },
    ]),
    ...sobrescribir,
  };
}

async function montar({ cliente = clienteFalso(), sesion, confirmarBorrado, vista } = {}) {
  const lanzador = document.createElement('button');
  document.body.append(lanzador);
  const ventana = crearVentanaChatbot({
    cliente,
    sesion: sesion ?? (() => ({ autenticado: false, apodo: '' })),
    chatGeneral: 'http://localhost/chat.html',
    confirmarBorrado: confirmarBorrado ?? (async () => true),
    esperaAutocompletar: 0,
    vista: vista ?? (() => null),
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
  sessionStorage.clear();
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

// 7.4.6: el historial llega por páginas; los anteriores se piden al subir.
describe('historial por páginas', () => {
  /** `cuantos` mensajes del usuario con ids m-<desde>…, del más antiguo al más reciente. */
  function mensajes(desde, cuantos) {
    return Array.from({ length: cuantos }, (_, i) => ({
      ...USUARIO,
      id: `m-${desde + i}`,
      contenido: `mensaje ${desde + i}`,
    }));
  }
  const textos = (vista) =>
    vista.mensajes().map((m) => m.querySelector('.chatbot-ventana__texto').textContent);
  const botonAnteriores = (vista) => vista.el.querySelector('[data-accion="ver-anteriores"]');

  test('al abrir pide solo la última página', async () => {
    const vista = await montar();
    expect(vista.cliente.obtenerHistorial).toHaveBeenCalledWith({ limite: PAGINA_DEL_HISTORIAL });
  });

  test('con una página incompleta no ofrece mensajes anteriores', async () => {
    const vista = await montar({
      cliente: clienteFalso({ obtenerHistorial: jest.fn(async () => [USUARIO, BOT]) }),
    });
    expect(botonAnteriores(vista)).toBeNull();
  });

  test('con una página llena ofrece los anteriores y los pone arriba, en orden', async () => {
    const obtenerHistorial = jest
      .fn()
      .mockResolvedValueOnce(mensajes(100, PAGINA_DEL_HISTORIAL))
      .mockResolvedValueOnce(mensajes(98, 2));
    const vista = await montar({ cliente: clienteFalso({ obtenerHistorial }) });

    const boton = botonAnteriores(vista);
    expect(boton).not.toBeNull();
    expect(vista.el.querySelector('.chatbot-ventana__registro').firstElementChild).toBe(
      boton.closest('li'),
    );

    boton.click();
    await esperar();

    expect(obtenerHistorial).toHaveBeenLastCalledWith({
      antesDe: 'm-100',
      limite: PAGINA_DEL_HISTORIAL,
    });
    expect(textos(vista).slice(0, 3)).toEqual(['mensaje 98', 'mensaje 99', 'mensaje 100']);
    expect(vista.mensajes()).toHaveLength(PAGINA_DEL_HISTORIAL + 2);
    // Página incompleta: ya no hay más hacia atrás, y el foco no se pierde.
    expect(botonAnteriores(vista)).toBeNull();
    expect(vista.el.contains(document.activeElement)).toBe(true);
  });

  test('llegar arriba con el scroll pide los anteriores, una sola vez a la vez', async () => {
    let resolver;
    const obtenerHistorial = jest
      .fn()
      .mockResolvedValueOnce(mensajes(100, PAGINA_DEL_HISTORIAL))
      .mockImplementationOnce(
        () =>
          new Promise((r) => {
            resolver = r;
          }),
      );
    const vista = await montar({ cliente: clienteFalso({ obtenerHistorial }) });
    const registro = vista.el.querySelector('.chatbot-ventana__registro');

    registro.scrollTop = 0;
    registro.dispatchEvent(new Event('scroll'));
    registro.dispatchEvent(new Event('scroll'));
    expect(obtenerHistorial).toHaveBeenCalledTimes(2);
    expect(registro.getAttribute('aria-busy')).toBe('true');

    resolver(mensajes(70, PAGINA_DEL_HISTORIAL));
    await esperar();

    expect(registro.hasAttribute('aria-busy')).toBe(false);
    expect(textos(vista)[0]).toBe('mensaje 70');
    // La página vino llena: puede haber más.
    expect(botonAnteriores(vista)).not.toBeNull();
  });

  test('si el servidor repite mensajes que ya se ven, no los pinta dos veces', async () => {
    const primera = mensajes(1, PAGINA_DEL_HISTORIAL);
    const obtenerHistorial = jest
      .fn()
      .mockResolvedValueOnce(primera)
      .mockResolvedValueOnce(primera);
    const vista = await montar({ cliente: clienteFalso({ obtenerHistorial }) });

    botonAnteriores(vista).click();
    await esperar();

    expect(vista.mensajes()).toHaveLength(PAGINA_DEL_HISTORIAL);
    expect(botonAnteriores(vista)).toBeNull();
  });

  test('si falla, lo dice y Reintentar vuelve a pedir la misma página', async () => {
    const obtenerHistorial = jest
      .fn()
      .mockResolvedValueOnce(mensajes(100, PAGINA_DEL_HISTORIAL))
      .mockRejectedValueOnce(new ErrorDelChatbot(null, 500))
      .mockResolvedValueOnce(mensajes(99, 1));
    const vista = await montar({ cliente: clienteFalso({ obtenerHistorial }) });

    botonAnteriores(vista).click();
    await esperar();
    const aviso = vista.el.querySelector('.chatbot-ventana__aviso');
    expect(aviso.hidden).toBe(false);
    expect(aviso.textContent).toContain(TEXTOS.errorAlCargar);

    aviso.querySelector('[data-accion="reintentar"]').click();
    await esperar();

    expect(obtenerHistorial).toHaveBeenLastCalledWith({
      antesDe: 'm-100',
      limite: PAGINA_DEL_HISTORIAL,
    });
    expect(textos(vista)[0]).toBe('mensaje 99');
    expect(aviso.hidden).toBe(true);
  });

  test('borrar la conversación quita «Ver mensajes anteriores»', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        obtenerHistorial: jest.fn(async () => mensajes(1, PAGINA_DEL_HISTORIAL)),
      }),
    });
    expect(botonAnteriores(vista)).not.toBeNull();

    vista.el.querySelector('[data-accion="borrar-conversacion"]').click();
    await esperar();

    expect(botonAnteriores(vista)).toBeNull();
    expect(textos(vista)).toEqual([TEXTOS.bienvenida]);
  });
});

describe('enviar', () => {
  test('pinta la pregunta y la respuesta, y limpia la caja de texto', async () => {
    const vista = await montar();
    await enviar(vista, '  como pujo  ');

    expect(vista.cliente.enviarMensaje).toHaveBeenCalledWith('como pujo');
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
    expect(vista.cliente.enviarMensaje).toHaveBeenCalledWith('hola');
  });

  test('no pide capturas, pero el historial sigue enlazando las que ya había', async () => {
    const conCaptura = { ...USUARIO, adjuntoUrl: 'https://img.example/captura.png' };
    const vista = await montar({
      cliente: clienteFalso({ obtenerHistorial: jest.fn(async () => [conCaptura, BOT]) }),
    });

    expect(vista.el.querySelector('input[name="adjuntoUrl"]')).toBeNull();
    expect(vista.el.querySelector('[data-accion="adjuntar-captura"]')).toBeNull();
    expect(vista.el.querySelector('.chatbot-ventana__adjunto').getAttribute('href')).toBe(
      'https://img.example/captura.png',
    );

    await enviar(vista, 'mira');
    expect(vista.cliente.enviarMensaje).toHaveBeenCalledWith('mira');
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

describe('respuestas enriquecidas y vista', () => {
  const ENRIQUECIDA = {
    ...BOT,
    id: 'm-bot-rica',
    contenido: 'Para publicar: 1) Elige el ítem. 2) Confirma.',
    enriquecido: {
      pasos: ['Elige el ítem.', 'Confirma.'],
      enlaces: [],
      tarjetas: [],
      respuestasRapidas: ['Cómo pujar'],
      ofrecerSoporteHumano: true,
    },
  };

  test('manda la vista donde está el jugador', async () => {
    const vista = await montar({ vista: () => 'SUBASTAS' });

    await enviar(vista, 'como pujo');

    expect(vista.cliente.enviarMensaje).toHaveBeenCalledWith('como pujo', { vista: 'SUBASTAS' });
  });

  test('pinta los pasos sin repetirlos en el texto, y los botones funcionan', async () => {
    const vista = await montar({
      cliente: clienteFalso({ enviarMensaje: jest.fn(async () => ENRIQUECIDA) }),
    });

    await enviar(vista, 'como publico');
    const burbuja = vista.el.querySelector('[data-mensaje-id="m-bot-rica"]');

    expect(burbuja.querySelector('.chatbot-ventana__texto').textContent).toBe('Para publicar:');
    expect(burbuja.querySelectorAll('.chatbot-enriquecido__pasos li')).toHaveLength(2);

    burbuja.querySelector('[data-accion="ofrecer-soporte"]').click();
    expect(vista.el.querySelector('[data-chatbot-soporte]').hidden).toBe(false);

    vista.el.querySelector('[data-accion="volver-al-chat"]').click();
    burbuja.querySelector('[data-accion="respuesta-rapida"]').click();
    await esperar();
    expect(vista.cliente.enviarMensaje).toHaveBeenLastCalledWith('Cómo pujar');
  });

  test('el historial también pinta lo enriquecido', async () => {
    const vista = await montar({
      cliente: clienteFalso({ obtenerHistorial: jest.fn(async () => [USUARIO, ENRIQUECIDA]) }),
    });

    expect(vista.el.querySelector('.chatbot-enriquecido')).not.toBeNull();
  });
});

describe('preguntas rápidas y autocompletado', () => {
  test('al cargar muestra las preguntas rápidas y al pulsar una la envía', async () => {
    const vista = await montar();
    await esperar();
    const boton = vista.el.querySelector('[data-accion="pregunta-rapida"]');

    expect(boton.textContent).toBe('Cómo funciona el Torneo');
    boton.click();
    await esperar();

    expect(vista.cliente.enviarMensaje).toHaveBeenCalledWith('Cómo funciona el Torneo');
    expect(vista.mensajes().some((m) => m.textContent.includes('Cómo funciona el Torneo'))).toBe(
      true,
    );
  });

  test('escribir sugiere y Enter con una marcada la envía en lugar del texto', async () => {
    const vista = await montar();
    vista.entrada.value = 'tor';
    vista.entrada.dispatchEvent(new Event('input'));
    await new Promise((resolver) => setTimeout(resolver, 0));
    await esperar();

    vista.entrada.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    vista.entrada.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }),
    );
    await esperar();

    expect(vista.cliente.sugerencias).toHaveBeenCalledWith({ q: 'tor', limite: 5 });
    expect(vista.cliente.enviarMensaje).toHaveBeenCalledTimes(1);
    expect(vista.cliente.enviarMensaje).toHaveBeenCalledWith('Cómo funciona el Torneo');
  });
});

describe('soporte', () => {
  test('«Soporte» cambia la conversación por el panel y «Volver al chat» la devuelve', async () => {
    const vista = await montar({ sesion: () => ({ autenticado: true, apodo: 'Kai' }) });
    const boton = vista.el.querySelector('[data-accion="hablar-con-soporte"]');
    const panel = vista.el.querySelector('[data-chatbot-soporte]');
    const registro = vista.el.querySelector('.chatbot-ventana__registro');

    expect(panel.hidden).toBe(true);
    boton.click();
    await esperar();

    expect(panel.hidden).toBe(false);
    expect(registro.hidden).toBe(true);
    expect(vista.formulario.hidden).toBe(true);
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(vista.cliente.misTickets).toHaveBeenCalled();
    expect(panel.contains(document.activeElement)).toBe(true);

    vista.el.querySelector('[data-accion="volver-al-chat"]').click();

    expect(panel.hidden).toBe(true);
    expect(registro.hidden).toBe(false);
    expect(vista.formulario.hidden).toBe(false);
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(vista.entrada);
  });

  test('el mismo botón cierra el panel si ya estaba abierto', async () => {
    const vista = await montar();
    const boton = vista.el.querySelector('[data-accion="hablar-con-soporte"]');

    boton.click();
    boton.click();

    expect(vista.el.querySelector('[data-chatbot-soporte]').hidden).toBe(true);
  });

  test('a un visitante el panel le ofrece iniciar sesión', async () => {
    const vista = await montar();

    vista.el.querySelector('[data-accion="hablar-con-soporte"]').click();

    expect(vista.el.querySelector('[data-chatbot-soporte]').textContent).toContain(
      'necesitas iniciar sesión',
    );
    expect(vista.cliente.misTickets).not.toHaveBeenCalled();
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

describe('minimizar y cambiar el tamaño (UXC-9, §7.4)', () => {
  test('minimizar deja la barra de título y la conversación intacta; el mismo botón restaura', async () => {
    const vista = await montar({ cliente: clienteFalso({ obtenerHistorial: async () => [BOT] }) });
    const boton = vista.el.querySelector('[data-accion="minimizar-asistente"]');
    expect(boton.getAttribute('aria-label')).toBe(TEXTOS.minimizar);
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(document.getElementById(boton.getAttribute('aria-controls'))).toBe(
      vista.el.querySelector('.chatbot-ventana__registro'),
    );

    boton.click();
    expect(vista.ventana.minimizada()).toBe(true);
    expect(vista.ventana.abierta()).toBe(true);
    expect(vista.el.classList).toContain('chatbot-ventana--minimizada');
    expect(boton.getAttribute('aria-label')).toBe(TEXTOS.restaurar);
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(vista.mensajes()).toHaveLength(1);

    boton.click();
    expect(vista.ventana.minimizada()).toBe(false);
    expect(document.activeElement).toBe(vista.entrada);
  });

  test('volver a abrirla la trae restaurada', async () => {
    const vista = await montar();
    vista.el.querySelector('[data-accion="minimizar-asistente"]').click();
    vista.ventana.cerrar();
    vista.ventana.abrir();
    expect(vista.ventana.minimizada()).toBe(false);
  });

  test('el asa cambia el tamaño con las flechas y lo recuerda en la sesión', async () => {
    const vista = await montar();
    const asa = vista.el.querySelector('[data-accion="tamano-asistente"]');
    expect(asa.tagName).toBe('BUTTON');
    expect(asa.getAttribute('aria-label')).toBe(TEXTOS.tamano);

    // jsdom no mide cajas: se parte del tamaño que dijo el asa.
    vista.el.style.width = '400px';
    vista.el.style.height = '500px';
    asa.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    asa.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));

    expect(vista.el.style.width).toBe(`${400 + TAMANO.paso}px`);
    expect(vista.el.style.height).toBe(`${500 + TAMANO.paso}px`);
    expect(JSON.parse(sessionStorage.getItem('nexus.asistente.tamano'))).toEqual({
      ancho: 400 + TAMANO.paso,
      alto: 500 + TAMANO.paso,
    });

    asa.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    expect(vista.el.style.width).toBe('');
    expect(sessionStorage.getItem('nexus.asistente.tamano')).toBeNull();
  });

  test('una ventana nueva en otra vista abre con el tamaño elegido', () => {
    sessionStorage.setItem('nexus.asistente.tamano', JSON.stringify({ ancho: 520, alto: 560 }));
    const ventana = crearVentanaChatbot({ cliente: clienteFalso(), sesion: () => ({}) });
    expect(ventana.elemento.style.width).toBe('520px');
    expect(ventana.elemento.style.height).toBe('560px');
  });

  test('el tamaño nunca baja del mínimo ni se sale de la pantalla', () => {
    expect(acotarTamano({ ancho: 10, alto: 10 }, { ancho: 1440, alto: 900 })).toEqual({
      ancho: TAMANO.anchoMinimo,
      alto: TAMANO.altoMinimo,
    });
    expect(acotarTamano({ ancho: 5000, alto: 5000 }, { ancho: 1440, alto: 900 })).toEqual({
      ancho: 1440 - 48,
      alto: 900 - 56 - 96,
    });
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
