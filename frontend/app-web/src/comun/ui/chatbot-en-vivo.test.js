/**
 * 7.4.9 — la ventana del asistente con el canal en tiempo real
 * (`canal-chatbot.js`): «escribiendo…» por el aviso del servidor, el estado
 * del canal en su píldora y las solicitudes de soporte al día. Va aparte de
 * `ventana-chatbot.test.js` y `soporte-chatbot.test.js`: sin canal, esas dos
 * siguen probando la ventana de siempre.
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../cliente-chatbot.js';
import { ESTADOS, crearPanelSoporte } from './soporte-chatbot.js';
import { TEXTOS, crearVentanaChatbot } from './ventana-chatbot.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const BOT = {
  id: 'm-bot-canal',
  remitente: 'BOT',
  contenido: 'Puedes pujar desde la sección Subasta.',
  adjuntoUrl: null,
  fechaEnvio: '2026-10-14T18:00:00Z',
};

const TICKET = {
  ticketId: 't-1',
  categoria: 'SOPORTE_TECNICO',
  asunto: 'No carga mi inventario',
  mensaje: 'Se queda cargando',
  estado: 'ABIERTO',
  respuesta: null,
  creadoEn: '2026-10-14T10:00:00Z',
  actualizadoEn: '2026-10-14T10:00:00Z',
};
const RESUELTO = {
  ...TICKET,
  estado: 'RESUELTO',
  respuesta: 'Ya quedó, vuelve a entrar.',
  actualizadoEn: '2026-10-14T11:00:00Z',
};

function clienteFalso(sobrescribir = {}) {
  return {
    obtenerHistorial: jest.fn(async () => []),
    enviarMensaje: jest.fn(async () => ({ ...BOT, id: 'm-bot-http' })),
    limpiarHistorial: jest.fn(async () => null),
    calificar: jest.fn(async () => ({ id: 'c-1' })),
    abrirTicket: jest.fn(async () => TICKET),
    misTickets: jest.fn(async () => [TICKET]),
    preferencias: jest.fn(async () => ({ idioma: 'AUTOMATICO', nivelDetalle: 'NORMAL' })),
    guardarPreferencias: jest.fn(async (datos) => datos),
    sugerencias: jest.fn(async () => []),
    ...sobrescribir,
  };
}

/** El canal, con la misma forma que `crearCanalChatbot`, movido a mano. */
function canalFalso() {
  const oyentes = { estado: [], ticket: [], reconectar: [] };
  let enCurso = null;
  return {
    abrir: jest.fn(async () => {}),
    reintentar: jest.fn(),
    enviarMensaje: jest.fn(
      (_contenido, { alEscribiendo } = {}) =>
        new Promise((resolver, rechazar) => {
          enCurso = { alEscribiendo, resolver, rechazar };
        }),
    ),
    alEstado: jest.fn((oyente) => oyentes.estado.push(oyente)),
    alTicket: jest.fn((oyente) => oyentes.ticket.push(oyente)),
    alReconectar: jest.fn((oyente) => oyentes.reconectar.push(oyente)),
    escribiendo: () => enCurso.alEscribiendo(),
    responder: (mensaje) => enCurso.resolver(mensaje),
    fallar: (error) => enCurso.rechazar(error),
    estado: (datos) => oyentes.estado.forEach((oyente) => oyente(datos)),
    ticket: (ticket) => oyentes.ticket.forEach((oyente) => oyente(ticket)),
    reconectar: () => oyentes.reconectar.forEach((oyente) => oyente()),
  };
}

async function montar({
  cliente = clienteFalso(),
  canal = canalFalso(),
  autenticado = false,
} = {}) {
  const ventana = crearVentanaChatbot({
    cliente,
    canal,
    sesion: () => ({ autenticado, apodo: autenticado ? 'Ana' : '' }),
    chatGeneral: 'http://localhost/chat.html',
    confirmarBorrado: async () => true,
    esperaAutocompletar: 0,
    vista: () => 'SUBASTAS',
  });
  ventana.abrir();
  await esperar();
  const el = ventana.elemento;
  return {
    ventana,
    cliente,
    canal,
    el,
    entrada: el.querySelector('textarea[name="contenido"]'),
    formulario: el.querySelector('form'),
    escribiendo: () => el.querySelector('.chatbot-ventana__escribiendo'),
    pildora: () => el.querySelector('[data-zona="conexion-asistente"]'),
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

describe('preguntar con el canal', () => {
  test('la pregunta va al canal con la vista; «escribiendo…» espera al ESCRIBIENDO', async () => {
    const vista = await montar();
    await enviar(vista, '  como pujo  ');

    expect(vista.canal.enviarMensaje).toHaveBeenCalledWith('como pujo', {
      vista: 'SUBASTAS',
      alEscribiendo: expect.any(Function),
    });
    expect(vista.cliente.enviarMensaje).not.toHaveBeenCalled();
    expect(vista.escribiendo().hidden).toBe(true);

    vista.canal.escribiendo();
    expect(vista.escribiendo().hidden).toBe(false);
    expect(vista.escribiendo().textContent).toBe(TEXTOS.escribiendo);

    vista.canal.responder(BOT);
    await esperar();
    expect(vista.escribiendo()).toBeNull();
    const mensajes = [...vista.el.querySelectorAll('.chatbot-ventana__mensaje')];
    expect(mensajes.at(-2).textContent).toContain('como pujo');
    expect(mensajes.at(-1).dataset.mensajeId).toBe('m-bot-canal');
    expect(vista.entrada.value).toBe('');
  });

  test('un error que llega por el canal se explica igual que por HTTP y conserva el texto', async () => {
    const vista = await montar();
    await enviar(vista, 'hola');
    vista.canal.fallar(new ErrorDelChatbot({ title: 'Bad Request', status: 400 }, 400));
    await esperar();

    expect(vista.el.querySelector('.chatbot-ventana__aviso').textContent).toContain(
      TEXTOS.mensajeInvalido,
    );
    expect(vista.escribiendo()).toBeNull();
    expect(vista.entrada.value).toBe('hola');
  });

  test('el canal se abre cuando la conversación cargó, y no si no cargó', async () => {
    const vista = await montar();
    expect(vista.canal.abrir).toHaveBeenCalledTimes(1);

    document.body.replaceChildren();
    const caida = await montar({
      cliente: clienteFalso({
        obtenerHistorial: jest.fn(async () => {
          throw new ErrorDelChatbot(null, 0);
        }),
      }),
    });
    expect(caida.canal.abrir).not.toHaveBeenCalled();
  });
});

describe('estado del canal (MAPEO-ERRORES §5.4)', () => {
  test('conectado no se ve; sin conexión se ve, con «Reintentar» que reintenta el canal', async () => {
    const vista = await montar();
    vista.canal.estado({ estado: 'conectado' });
    expect(vista.pildora().hidden).toBe(true);

    vista.canal.estado({ estado: 'sin-conexion', de: 5 });
    expect(vista.pildora().hidden).toBe(false);
    expect(vista.pildora().getAttribute('role')).toBe('status');
    expect(vista.pildora().textContent).toContain('Sin conexión');
    vista.el.querySelector('[data-accion="reintentar-canal"]').click();
    expect(vista.canal.reintentar).toHaveBeenCalledTimes(1);
  });

  test('sin canal no hay píldora', async () => {
    const ventana = crearVentanaChatbot({ cliente: clienteFalso(), sesion: () => ({}) });
    expect(ventana.elemento.querySelector('[data-zona="conexion-asistente"]')).toBeNull();
  });
});

describe('soporte en vivo', () => {
  async function conSoporteAbierto(opciones) {
    const vista = await montar({ autenticado: true, ...opciones });
    vista.el.querySelector('[data-accion="hablar-con-soporte"]').click();
    await esperar();
    return vista;
  }

  test('un cambio que llega por el canal actualiza «Tus solicitudes» sin releerla', async () => {
    const vista = await conSoporteAbierto();
    expect(vista.cliente.misTickets).toHaveBeenCalledTimes(1);

    vista.canal.ticket(RESUELTO);

    const items = vista.el.querySelectorAll('.chatbot-soporte__ticket');
    expect(items).toHaveLength(1);
    expect(items[0].textContent).toContain(ESTADOS.RESUELTO.texto);
    expect(items[0].textContent).toContain('Ya quedó, vuelve a entrar.');
    expect(vista.cliente.misTickets).toHaveBeenCalledTimes(1);
  });

  test('al volver el canal tras una caída, la lista se relee', async () => {
    const vista = await conSoporteAbierto();
    vista.canal.reconectar();
    await esperar();
    expect(vista.cliente.misTickets).toHaveBeenCalledTimes(2);
  });
});

describe('panel de soporte: actualizarTicket y recargarLista', () => {
  async function panel({ autenticado = true, tickets = [TICKET] } = {}) {
    const cliente = clienteFalso({ misTickets: jest.fn(async () => tickets) });
    const soporte = crearPanelSoporte({
      cliente,
      sesion: () => ({ autenticado }),
      login: '/login',
      chatGeneral: '/chat.html',
      alVolver: () => {},
    });
    document.body.append(soporte.elemento);
    return { soporte, cliente };
  }

  test('con el panel cerrado no hace nada: al abrirlo se lee de nuevo', async () => {
    const { soporte, cliente } = await panel();
    soporte.actualizarTicket(RESUELTO);
    soporte.recargarLista();
    expect(cliente.misTickets).not.toHaveBeenCalled();
  });

  test('una solicitud que la lista no tiene hace releerla entera', async () => {
    const { soporte, cliente } = await panel({ tickets: [] });
    soporte.abrir();
    await esperar();
    expect(cliente.misTickets).toHaveBeenCalledTimes(1);

    soporte.actualizarTicket({ ...RESUELTO, ticketId: 't-nuevo' });
    await esperar();
    expect(cliente.misTickets).toHaveBeenCalledTimes(2);

    soporte.actualizarTicket({ estado: 'RESUELTO' });
    await esperar();
    expect(cliente.misTickets).toHaveBeenCalledTimes(2);
  });

  test('un visitante no tiene lista: ni se actualiza ni se relee', async () => {
    const { soporte, cliente } = await panel({ autenticado: false });
    soporte.abrir();
    await esperar();
    soporte.actualizarTicket(RESUELTO);
    soporte.recargarLista();
    await esperar();
    expect(cliente.misTickets).not.toHaveBeenCalled();
  });

  test('el texto que llega por el canal nunca se interpreta como HTML', async () => {
    const { soporte } = await panel();
    soporte.abrir();
    await esperar();
    soporte.actualizarTicket({ ...RESUELTO, respuesta: '<img src=x onerror=alert(1)>' });
    const item = soporte.elemento.querySelector('.chatbot-soporte__ticket');
    expect(item.querySelector('img')).toBeNull();
    expect(item.textContent).toContain('<img src=x onerror=alert(1)>');
  });
});
