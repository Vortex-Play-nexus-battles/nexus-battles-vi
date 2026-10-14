/**
 * Ventana del asistente — HU-CHA-001, HU-CHA-008, HU-CHA-011.
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../cliente-chatbot.js';
import {
  ESPERA_POR_OMISION_S,
  PAGINA_DEL_HISTORIAL,
  TAMANO,
  TEXTOS,
  acotarTamano,
  crearVentanaChatbot,
  rutaDeEstaPagina,
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
    preferencias: jest.fn(async () => ({ idioma: 'AUTOMATICO', nivelDetalle: 'NORMAL' })),
    guardarPreferencias: jest.fn(async (datos) => datos),
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

async function montar({
  cliente = clienteFalso(),
  sesion,
  confirmarBorrado,
  vista,
  reloj,
  login,
  registro,
} = {}) {
  const lanzador = document.createElement('button');
  document.body.append(lanzador);
  const ventana = crearVentanaChatbot({
    cliente,
    sesion: sesion ?? (() => ({ autenticado: false, apodo: '' })),
    chatGeneral: 'http://localhost/chat.html',
    confirmarBorrado: confirmarBorrado ?? (async () => true),
    esperaAutocompletar: 0,
    vista: vista ?? (() => null),
    ...(reloj ? { reloj } : {}),
    ...(login !== undefined ? { login } : {}),
    ...(registro !== undefined ? { registro } : {}),
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
    botonEnviar: el.querySelector('button[type="submit"]'),
    aviso: () => el.querySelector('.chatbot-ventana__aviso'),
    mensajes: () => [...el.querySelectorAll('.chatbot-ventana__mensaje')],
  };
}

/** El texto de un mensaje, sin el prefijo para el lector de pantalla. */
const textoDe = (burbuja) => burbuja.querySelector('.chatbot-ventana__texto')?.textContent ?? '';

/** Reloj manual para la espera tras un 429: solo avanza cuando la prueba lo dice. */
function crearReloj() {
  let siguiente = 1;
  const pendientes = new Map();
  return {
    pendientes,
    setTimeout: jest.fn((funcion, ms) => {
      const id = siguiente++;
      pendientes.set(id, { funcion, ms });
      return id;
    }),
    clearTimeout: jest.fn((id) => pendientes.delete(id)),
    cumplir() {
      for (const [id, { funcion }] of [...pendientes]) {
        pendientes.delete(id);
        funcion();
      }
    },
  };
}

/** Una promesa que la prueba resuelve o rechaza cuando quiere. */
function diferida() {
  let resolver;
  let rechazar;
  const promesa = new Promise((si, no) => {
    resolver = si;
    rechazar = no;
  });
  return { promesa, resolver, rechazar };
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

  // #506 — antes el foco caía en «×»: la caja estaba desactivada mientras
  // cargaba la conversación.
  test('al abrir, el foco va a la caja de texto aunque la conversación siga cargando', () => {
    const lanzador = document.createElement('button');
    document.body.append(lanzador);
    lanzador.focus();
    const ventana = crearVentanaChatbot({
      cliente: clienteFalso({ obtenerHistorial: jest.fn(() => new Promise(() => {})) }),
      sesion: () => ({}),
    });

    ventana.abrir(lanzador);

    const entrada = ventana.elemento.querySelector('textarea[name="contenido"]');
    expect(document.activeElement).toBe(entrada);
    // Se puede escribir, pero no enviar ni borrar hasta que cargue.
    expect(ventana.elemento.querySelector('button[type="submit"]').disabled).toBe(true);
    expect(ventana.elemento.querySelector('[data-accion="borrar-conversacion"]').disabled).toBe(
      true,
    );
  });

  test('mientras carga, Enter no envía y lo escrito se queda en la caja', async () => {
    const historial = diferida();
    const cliente = clienteFalso({ obtenerHistorial: jest.fn(() => historial.promesa) });
    const ventana = crearVentanaChatbot({ cliente, sesion: () => ({}) });
    ventana.abrir();
    const entrada = ventana.elemento.querySelector('textarea[name="contenido"]');

    entrada.value = 'hola';
    entrada.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    await esperar();
    expect(cliente.enviarMensaje).not.toHaveBeenCalled();
    expect(entrada.value).toBe('hola');

    historial.resolver([]);
    await esperar();
    expect(ventana.elemento.querySelector('button[type="submit"]').disabled).toBe(false);
    expect(document.activeElement).toBe(entrada);
  });

  test('tras «Reintentar» con éxito, el foco vuelve a la caja de texto', async () => {
    const obtenerHistorial = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDelChatbot(null, 503))
      .mockResolvedValueOnce([BOT]);
    const vista = await montar({ cliente: clienteFalso({ obtenerHistorial }) });
    const reintentar = vista.el.querySelector('[data-accion="reintentar"]');
    reintentar.focus();

    reintentar.click();
    await esperar();

    expect(document.activeElement).toBe(vista.entrada);
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
    expect(textoDe(bienvenida)).toBe(TEXTOS.bienvenida);
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

  // ms-chatbot.yaml 1.14.0 (§7.4.6): las capturas van en la solicitud de
  // soporte, no en el mensaje (`adjuntoUrl` sigue obsoleto). El clip de la
  // caja de texto es un botón que lleva a «Soporte», no un archivo del mensaje.
  test('el mensaje no lleva capturas, pero el historial sigue enlazando las que ya había', async () => {
    const conCaptura = { ...USUARIO, adjuntoUrl: 'https://img.example/captura.png' };
    const vista = await montar({
      cliente: clienteFalso({ obtenerHistorial: jest.fn(async () => [conCaptura, BOT]) }),
    });

    expect(vista.el.querySelector('input[name="adjuntoUrl"]')).toBeNull();
    expect(vista.formulario.querySelector('input[type="file"]')).toBeNull();
    expect(vista.el.querySelector('[data-accion="adjuntar-captura"]').type).toBe('button');
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
    expect(vista.entrada.value).toBe('hola');
  });
});

// #506 — la pregunta se ve en cuanto se envía, no cuando llega la respuesta.
describe('la pregunta se pinta al enviar', () => {
  test('aparece antes de la respuesta, con «escribiendo…» debajo y la caja vacía', async () => {
    const respuesta = diferida();
    const vista = await montar({
      cliente: clienteFalso({ enviarMensaje: jest.fn(() => respuesta.promesa) }),
    });

    await enviar(vista, 'como pujo');

    // Todavía sin respuesta: la pregunta ya está, y debajo, «escribiendo…».
    const [pregunta] = vista.mensajes();
    expect(pregunta.classList).toContain('chatbot-ventana__mensaje--usuario');
    expect(textoDe(pregunta)).toBe('como pujo');
    expect(pregunta.nextElementSibling.classList).toContain('chatbot-ventana__escribiendo');
    expect(vista.entrada.value).toBe('');
    expect(vista.botonEnviar.disabled).toBe(true);
    expect(vista.el.querySelector('[data-bienvenida]')).toBeNull();

    respuesta.resolver({ ...BOT, id: 'm-bot-nuevo' });
    await esperar();

    const [usuario, bot] = vista.mensajes();
    expect(usuario).toBe(pregunta);
    expect(bot.dataset.mensajeId).toBe('m-bot-nuevo');
    expect(vista.el.querySelector('.chatbot-ventana__escribiendo')).toBeNull();
    expect(vista.botonEnviar.disabled).toBe(false);
  });

  test('si el envío falla, la pregunta sale y su texto vuelve a la caja', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        enviarMensaje: jest.fn(async () => {
          throw new ErrorDelChatbot(null, 0);
        }),
      }),
    });

    await enviar(vista, 'como pujo');

    expect(vista.entrada.value).toBe('como pujo');
    expect(vista.el.querySelector('.chatbot-ventana__mensaje--usuario')).toBeNull();
    expect(vista.el.querySelector('.chatbot-ventana__escribiendo')).toBeNull();
    // La conversación estaba vacía: vuelve la bienvenida.
    expect(textoDe(vista.mensajes()[0])).toBe(TEXTOS.bienvenida);
    expect(document.activeElement).toBe(vista.entrada);
  });

  test('si mientras tanto se empezó otra pregunta, no se pierde ninguna', async () => {
    const respuesta = diferida();
    const vista = await montar({
      cliente: clienteFalso({
        obtenerHistorial: jest.fn(async () => [USUARIO, BOT]),
        enviarMensaje: jest.fn(() => respuesta.promesa),
      }),
    });

    await enviar(vista, 'primera');
    vista.entrada.value = 'segunda';
    respuesta.rechazar(new ErrorDelChatbot(null, 500));
    await esperar();

    expect(vista.entrada.value).toBe('primera\nsegunda');
    // El historial que ya había sigue intacto.
    expect(vista.mensajes().map(textoDe)).toEqual([USUARIO.contenido, BOT.contenido]);
  });
});

// #506 — cada rechazo con su motivo, y el texto se queda para corregirlo.
describe('mensajes rechazados', () => {
  const rechazo = (motivo, estado, opciones) =>
    new ErrorDelChatbot({ title: 'x', motivo }, estado, opciones);

  test.each([
    ['CONTENIDO_BLOQUEADO', 422, TEXTOS.contenidoBloqueado, 'advertencia'],
    ['INYECCION_DETECTADA', 422, TEXTOS.inyeccionDetectada, 'advertencia'],
    ['MODERACION_NO_DISPONIBLE', 503, TEXTOS.moderacionNoDisponible, 'error'],
  ])('%s (%i) se explica y conserva lo escrito', async (motivo, estado, texto, tono) => {
    const vista = await montar({
      cliente: clienteFalso({
        enviarMensaje: jest.fn(async () => {
          throw rechazo(motivo, estado);
        }),
      }),
    });

    await enviar(vista, '<script>alert(1)</script>');

    const aviso = vista.aviso();
    expect(aviso.hidden).toBe(false);
    expect(aviso.textContent).toBe(texto);
    expect(aviso.querySelector(`.aviso--${tono}[role="alert"]`)).not.toBeNull();
    // No es «el asistente no está»: ni chat general ni bloqueo de la caja.
    expect(aviso.querySelector('[data-accion="ir-al-chat"]')).toBeNull();
    expect(vista.entrada.value).toBe('<script>alert(1)</script>');
    expect(vista.entrada.disabled).toBe(false);
    expect(vista.botonEnviar.disabled).toBe(false);
  });

  test('los textos de la ventana son los acordados', () => {
    expect(TEXTOS.contenidoBloqueado).toBe(
      'Tu mensaje no se envió porque tiene palabras que no están permitidas. Escríbelo de otra forma.',
    );
    expect(TEXTOS.inyeccionDetectada).toBe(
      'Tu mensaje no se envió porque parece contener código o instrucciones del sistema. Escribe tu pregunta con palabras.',
    );
    expect(TEXTOS.demasiadosMensajes(30)).toBe(
      'Escribiste muchos mensajes seguidos. Espera 30 s y vuelve a intentarlo.',
    );
    expect(TEXTOS.demasiadosMensajes(null)).toBe(
      'Escribiste muchos mensajes seguidos. Espera un momento y vuelve a intentarlo.',
    );
    expect(TEXTOS.moderacionNoDisponible).toBe(
      'No pude revisar tu mensaje en este momento. Inténtalo de nuevo en unos minutos.',
    );
  });

  test('un 429 dice cuánto esperar, desactiva «Enviar» y lo reactiva solo', async () => {
    const reloj = crearReloj();
    const enviarMensaje = jest.fn(async () => {
      throw rechazo('LIMITE_DE_FRECUENCIA', 429, { reintentarEn: 30 });
    });
    const vista = await montar({ cliente: clienteFalso({ enviarMensaje }), reloj });

    await enviar(vista, 'otra vez');

    expect(vista.aviso().textContent).toBe(TEXTOS.demasiadosMensajes(30));
    expect(vista.aviso().querySelector('.aviso--advertencia')).not.toBeNull();
    expect(vista.entrada.value).toBe('otra vez');
    expect(vista.entrada.disabled).toBe(false);
    expect(vista.botonEnviar.disabled).toBe(true);
    expect(reloj.setTimeout).toHaveBeenCalledWith(expect.any(Function), 30_000);

    // Ni Enter ni una pregunta rápida envían durante la espera, y la pregunta
    // rápida no pisa lo que hay escrito.
    vista.entrada.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    vista.el.querySelector('[data-accion="pregunta-rapida"]')?.click();
    await esperar();
    expect(enviarMensaje).toHaveBeenCalledTimes(1);
    expect(vista.entrada.value).toBe('otra vez');

    reloj.cumplir();

    expect(vista.botonEnviar.disabled).toBe(false);
    expect(vista.aviso().hidden).toBe(true);
    await enviar(vista, 'otra vez');
    expect(enviarMensaje).toHaveBeenCalledTimes(2);
  });

  test('un 429 sin Retry-After espera un momento (la espera por omisión)', async () => {
    const reloj = crearReloj();
    const vista = await montar({
      cliente: clienteFalso({
        enviarMensaje: jest.fn(async () => {
          throw new ErrorDelChatbot(null, 429);
        }),
      }),
      reloj,
    });

    await enviar(vista, 'hola');

    expect(vista.aviso().textContent).toBe(TEXTOS.demasiadosMensajes(null));
    expect(vista.botonEnviar.disabled).toBe(true);
    expect(reloj.setTimeout).toHaveBeenCalledWith(
      expect.any(Function),
      ESPERA_POR_OMISION_S * 1000,
    );
  });
});

// #506 — quién habla, para el lector de pantalla, y la conversación como registro.
describe('accesibilidad de la conversación', () => {
  test('cada mensaje dice quién habla, sin tocar el texto del mensaje', async () => {
    const vista = await montar({
      cliente: clienteFalso({ obtenerHistorial: jest.fn(async () => [USUARIO, BOT]) }),
    });

    const [usuario, bot] = vista.mensajes();
    expect(usuario.querySelector('.solo-lectores').textContent.trim()).toBe(TEXTOS.prefijoUsuario);
    expect(bot.querySelector('.solo-lectores').textContent.trim()).toBe(TEXTOS.prefijoAsistente);
    expect(TEXTOS.prefijoUsuario).toBe('Tú:');
    expect(TEXTOS.prefijoAsistente).toBe('Asistente:');
    expect(textoDe(usuario)).toBe(USUARIO.contenido);
    // Va antes del texto, así que se lee primero.
    expect(usuario.firstElementChild.classList).toContain('solo-lectores');
  });

  test('la bienvenida y la pregunta recién enviada también lo dicen', async () => {
    const vista = await montar();
    expect(vista.mensajes()[0].querySelector('.solo-lectores').textContent.trim()).toBe(
      TEXTOS.prefijoAsistente,
    );

    await enviar(vista, 'hola');
    const pregunta = vista.el.querySelector('.chatbot-ventana__mensaje--usuario');
    expect(pregunta.querySelector('.solo-lectores').textContent.trim()).toBe(TEXTOS.prefijoUsuario);
  });

  test('la conversación es una región role="log" y la lista sigue siendo una lista', async () => {
    const vista = await montar();

    const registro = vista.el.querySelector('.chatbot-ventana__registro');
    const region = registro.parentElement;
    expect(region.getAttribute('role')).toBe('log');
    expect(region.getAttribute('aria-live')).toBe('polite');
    expect(region.getAttribute('aria-label')).toBe(TEXTOS.registro);
    expect(registro.tagName).toBe('OL');
    expect(registro.hasAttribute('role')).toBe(false);
    // Una sola región viva: anidadas, cada mensaje se leería dos veces.
    expect(registro.hasAttribute('aria-live')).toBe(false);
  });

  test('Soporte y Preferencias ocultan la región entera, no solo la lista', async () => {
    const vista = await montar();
    const region = vista.el.querySelector('[role="log"]');

    vista.el.querySelector('[data-accion="preferencias-asistente"]').click();
    await esperar();
    expect(region.hidden).toBe(true);

    vista.el.querySelector('[data-accion="preferencias-asistente"]').click();
    expect(region.hidden).toBe(false);
  });
});

// #506 — las acciones en su propia fila, sin cambiar el orden del tabulador.
describe('cabecera', () => {
  test('título con minimizar y cerrar; las acciones de texto en su propio grupo', async () => {
    const vista = await montar();
    const cabecera = vista.el.querySelector('.chatbot-ventana__cabecera');

    const acciones = cabecera.querySelector('.chatbot-ventana__acciones');
    const controles = cabecera.querySelector('.chatbot-ventana__controles');
    expect([...acciones.children].map((b) => b.dataset.accion)).toEqual([
      'preferencias-asistente',
      'hablar-con-soporte',
      'borrar-conversacion',
    ]);
    expect([...controles.children].map((b) => b.dataset.accion)).toEqual([
      'minimizar-asistente',
      'cerrar-asistente',
    ]);
    // El orden del tabulador es el de antes: las tres acciones, luego − y ×.
    expect([...cabecera.querySelectorAll('button')].map((b) => b.dataset.accion)).toEqual([
      'preferencias-asistente',
      'hablar-con-soporte',
      'borrar-conversacion',
      'minimizar-asistente',
      'cerrar-asistente',
    ]);
    // Las acciones de texto conservan su texto visible.
    expect(acciones.textContent).toBe(`${TEXTOS.preferencias}${TEXTOS.soporte}${TEXTOS.borrar}`);
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

  // #506 — contrato 1.4.0: el comentario pasa por la lista negra y por la
  // revisión de código; si se rechaza, se dice por qué y el texto se queda.
  test.each([
    ['CONTENIDO_BLOQUEADO', 422, TEXTOS.comentarioBloqueado],
    ['INYECCION_DETECTADA', 422, TEXTOS.comentarioConCodigo],
    ['MODERACION_NO_DISPONIBLE', 503, TEXTOS.comentarioSinRevisar],
  ])('un comentario rechazado por %s se explica y se conserva', async (motivo, estado, texto) => {
    const vista = await conUnaRespuesta({
      calificar: jest.fn(async () => {
        throw new ErrorDelChatbot({ title: 'x', motivo }, estado);
      }),
    });
    vista.el.querySelector('[data-accion="calificar-no-util"]').click();
    const comentario = vista.el.querySelector('.chatbot-calificacion textarea');
    comentario.value = 'no sirve';

    vista.el.querySelector('[data-accion="enviar-calificacion"]').click();
    await esperar();

    expect(vista.el.querySelector('.chatbot-calificacion__nota').textContent).toBe(texto);
    expect(comentario.value).toBe('no sirve');
    expect(vista.el.querySelector('[data-accion="enviar-calificacion"]').disabled).toBe(false);
  });

  test('un 429 al calificar dice cuánto esperar', async () => {
    const vista = await conUnaRespuesta({
      calificar: jest.fn(async () => {
        throw new ErrorDelChatbot(null, 429, { reintentarEn: 12 });
      }),
    });
    vista.el.querySelector('[data-accion="calificar-util"]').click();
    await esperar();

    expect(vista.el.querySelector('.chatbot-calificacion__nota').textContent).toBe(
      TEXTOS.demasiadosMensajes(12),
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

  // #506 — defensa en profundidad: un enlace a una vista que esta sesión no
  // puede ver no se pinta, ni suelto ni en una tarjeta.
  test('a un jugador no le pinta enlaces a la consola de operación', async () => {
    const conEnlaces = {
      ...BOT,
      id: 'm-bot-enlaces',
      enriquecido: {
        pasos: [],
        enlaces: [
          { texto: 'Ir a Catálogo', destino: 'productos' },
          { texto: 'Ir a Subastas', destino: 'subastas' },
        ],
        tarjetas: [
          { titulo: 'Usuarios', texto: 'x', enlace: { texto: 'Ver', destino: 'gestion-usuarios' } },
        ],
        respuestasRapidas: [],
        ofrecerSoporteHumano: false,
      },
    };
    const vista = await montar({
      sesion: () => ({ autenticado: true, apodo: 'Kai', rol: 'JUGADOR' }),
      cliente: clienteFalso({ obtenerHistorial: jest.fn(async () => [conEnlaces]) }),
    });

    const enlaces = [...vista.el.querySelectorAll('.chatbot-enriquecido__enlace')];
    expect(enlaces.map((a) => a.dataset.destino)).toEqual(['subastas']);
    expect(vista.el.querySelector('.chatbot-enriquecido__tarjeta').textContent).toContain(
      'Usuarios',
    );
    expect(vista.el.querySelector('.chatbot-enriquecido__tarjeta a')).toBeNull();
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

  // ms-chatbot.yaml 1.14.0 (§7.4.6, RF-CHA-002): el clip de la caja de texto
  // lleva a donde se adjunta la captura, que es la solicitud de soporte.
  test('el clip abre «Soporte» con el foco donde se adjunta: la solicitud abierta o el formulario', async () => {
    const abierta = {
      ticketId: 't-9',
      categoria: 'SOPORTE_TECNICO',
      asunto: 'No carga',
      mensaje: 'x',
      estado: 'ABIERTO',
      creadoEn: '2026-10-14T15:00:00Z',
      actualizadoEn: '2026-10-14T15:00:00Z',
    };
    const vista = await montar({
      cliente: clienteFalso({ misTickets: jest.fn(async () => [abierta]) }),
    });
    const clip = vista.el.querySelector('[data-accion="adjuntar-captura"]');
    expect(clip.getAttribute('aria-label')).toBe(TEXTOS.adjuntarCaptura);
    expect(clip.closest('form')).toBe(vista.formulario);

    clip.click();
    await esperar();
    await esperar();

    const panel = vista.el.querySelector('[data-chatbot-soporte]');
    expect(panel.hidden).toBe(false);
    expect(document.activeElement).toBe(
      panel.querySelector('[data-ticket-id="t-9"] [data-accion="elegir-captura"]'),
    );

    // Sin una abierta, al de la captura opcional del formulario.
    vista.el.querySelector('[data-accion="volver-al-chat"]').click();
    vista.cliente.misTickets.mockResolvedValue([]);
    clip.click();
    await esperar();
    await esperar();
    expect(document.activeElement).toBe(panel.querySelector('form [data-accion="elegir-captura"]'));
  });

  test('el mismo botón cierra el panel si ya estaba abierto', async () => {
    const vista = await montar();
    const boton = vista.el.querySelector('[data-accion="hablar-con-soporte"]');

    boton.click();
    boton.click();

    expect(vista.el.querySelector('[data-chatbot-soporte]').hidden).toBe(true);
  });

  // #506 — un visitante que pide una persona no se queda en un callejón sin
  // salida. Desde ms-chatbot.yaml 1.12.0 (RF-CHA-007) abre la solicitud como
  // cualquiera; la nota le dice hasta cuándo verá la respuesta y le ofrece,
  // como salida secundaria, entrar o crear una cuenta.
  test('a un visitante el panel le deja pedir ayuda y le ofrece una cuenta', async () => {
    const vista = await montar({
      login: () => 'http://localhost/cuentas/login.html?volver=%2Fcuentas%2Fsubastas.html',
      registro: 'http://localhost/cuentas/registro.html',
    });

    vista.el.querySelector('[data-accion="hablar-con-soporte"]').click();
    await esperar();

    const panel = vista.el.querySelector('[data-chatbot-soporte]');
    expect(panel.textContent).toContain('Estás como visitante');
    expect(panel.querySelector('form')).not.toBeNull();
    expect(vista.cliente.misTickets).toHaveBeenCalled();
    expect(panel.querySelector('[data-accion="iniciar-sesion"]').getAttribute('href')).toBe(
      'http://localhost/cuentas/login.html?volver=%2Fcuentas%2Fsubastas.html',
    );
    expect(panel.querySelector('[data-accion="crear-cuenta"]').getAttribute('href')).toBe(
      'http://localhost/cuentas/registro.html',
    );
    // El chat general también pide sesión: era otra puerta al mismo login.
    expect(panel.querySelector('[data-accion="chat-general"]')).toBeNull();
    // Lo principal es pedir ayuda: el foco va al formulario, no a la cuenta.
    expect(document.activeElement).toBe(panel.querySelector('select'));
  });

  test('por omisión, «Iniciar sesión» lleva al login con la vuelta a esta página', async () => {
    window.history.replaceState(null, '', '/frontend/app-web/src/cuentas/subastas.html?q=1#lista');
    try {
      const vista = await montar();
      vista.el.querySelector('[data-accion="hablar-con-soporte"]').click();

      const login = new URL(
        vista.el.querySelector('[data-accion="iniciar-sesion"]').getAttribute('href'),
      );
      expect(login.pathname).toMatch(/\/cuentas\/login\.html$/);
      expect(login.searchParams.get('volver')).toBe(
        '/frontend/app-web/src/cuentas/subastas.html?q=1#lista',
      );
      expect(vista.el.querySelector('[data-accion="crear-cuenta"]').getAttribute('href')).toMatch(
        /\/cuentas\/registro\.html$/,
      );
    } finally {
      window.history.replaceState(null, '', '/');
    }
  });

  test('un jugador con sesión sigue viendo el formulario de solicitud', async () => {
    const vista = await montar({ sesion: () => ({ autenticado: true, apodo: 'Kai' }) });

    vista.el.querySelector('[data-accion="hablar-con-soporte"]').click();
    await esperar();

    const panel = vista.el.querySelector('[data-chatbot-soporte]');
    expect(panel.querySelector('form')).not.toBeNull();
    expect(panel.querySelector('[data-accion="iniciar-sesion"]')).toBeNull();
    expect(panel.querySelector('[data-accion="crear-cuenta"]')).toBeNull();
  });
});

describe('rutaDeEstaPagina (#506)', () => {
  test('es la ruta segura de la página, con su búsqueda y su fragmento', () => {
    expect(
      rutaDeEstaPagina({
        origin: 'http://localhost:8099',
        pathname: '/cuentas/subastas.html',
        search: '?q=1',
        hash: '#x',
      }),
    ).toBe('/cuentas/subastas.html?q=1#x');
  });

  test('desde una pantalla de entrada no hay vuelta (sería un bucle)', () => {
    expect(
      rutaDeEstaPagina({
        origin: 'http://localhost:8099',
        pathname: '/cuentas/login.html',
        search: '',
        hash: '',
      }),
    ).toBeNull();
    expect(rutaDeEstaPagina(null)).toBeNull();
  });
});

// 7.4.5: «Preferencias» ocupa el sitio de la conversación, como «Soporte».
describe('preferencias', () => {
  const botonPreferencias = (vista) =>
    vista.el.querySelector('[data-accion="preferencias-asistente"]');
  const panelPreferencias = (vista) => vista.el.querySelector('[data-chatbot-preferencias]');
  const panelSoporte = (vista) => vista.el.querySelector('[data-chatbot-soporte]');

  test('el botón abre el panel en lugar de la conversación y lo carga', async () => {
    const vista = await montar();
    const boton = botonPreferencias(vista);
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(panelPreferencias(vista).hidden).toBe(true);

    boton.click();
    await esperar();

    expect(panelPreferencias(vista).hidden).toBe(false);
    expect(vista.el.querySelector('.chatbot-ventana__registro').hidden).toBe(true);
    expect(vista.formulario.hidden).toBe(true);
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(vista.cliente.preferencias).toHaveBeenCalledTimes(1);
  });

  test('«Volver al chat» y el mismo botón devuelven la conversación con el foco dentro', async () => {
    const vista = await montar();
    botonPreferencias(vista).click();
    await esperar();

    panelPreferencias(vista).querySelector('[data-accion="volver-al-chat"]').click();
    expect(panelPreferencias(vista).hidden).toBe(true);
    expect(vista.el.querySelector('.chatbot-ventana__registro').hidden).toBe(false);
    expect(document.activeElement).toBe(vista.entrada);

    botonPreferencias(vista).click();
    await esperar();
    botonPreferencias(vista).click();
    expect(panelPreferencias(vista).hidden).toBe(true);
    expect(botonPreferencias(vista).getAttribute('aria-expanded')).toBe('false');
  });

  test('abrir «Soporte» cierra «Preferencias» y al revés: nunca los dos', async () => {
    const vista = await montar();
    botonPreferencias(vista).click();
    await esperar();

    vista.el.querySelector('[data-accion="hablar-con-soporte"]').click();
    await esperar();
    expect(panelSoporte(vista).hidden).toBe(false);
    expect(panelPreferencias(vista).hidden).toBe(true);
    expect(botonPreferencias(vista).getAttribute('aria-expanded')).toBe('false');

    botonPreferencias(vista).click();
    await esperar();
    expect(panelPreferencias(vista).hidden).toBe(false);
    expect(panelSoporte(vista).hidden).toBe(true);
    expect(
      vista.el.querySelector('[data-accion="hablar-con-soporte"]').getAttribute('aria-expanded'),
    ).toBe('false');
  });

  test('guardar desde la ventana manda lo elegido', async () => {
    const vista = await montar();
    botonPreferencias(vista).click();
    await esperar();

    const panel = panelPreferencias(vista);
    panel.querySelector('select[name="nivelDetalle"]').value = 'BREVE';
    panel.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();

    expect(vista.cliente.guardarPreferencias).toHaveBeenCalledWith({
      idioma: 'AUTOMATICO',
      nivelDetalle: 'BREVE',
    });
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
    expect(textoDe(vista.mensajes()[0])).toBe(TEXTOS.bienvenida);
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
