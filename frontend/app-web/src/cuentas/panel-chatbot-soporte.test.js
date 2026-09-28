/**
 * Pestaña «Soporte» del panel del asistente.
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../comun/cliente-chatbot.js';
import {
  POR_PAGINA,
  SIGUIENTES,
  atenderEnDialogo,
  montarSoporte,
  nombreDeCategoria,
  textoDeFalloAlAtender,
} from './panel-chatbot-soporte.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const RESUMEN = {
  ticketId: 't-1',
  categoria: 'SOPORTE_TECNICO',
  asunto: 'No carga mi inventario',
  mensaje: 'Se queda cargando',
  estado: 'ABIERTO',
  respuesta: null,
  creadoEn: '2026-09-28T18:00:00Z',
  actualizadoEn: '2026-09-28T18:00:00Z',
};

const DETALLE = {
  ...RESUMEN,
  uid: 'uid-jugador',
  asignadoA: null,
  contexto: [
    {
      remitente: 'USUARIO',
      contenido: 'mi inventario no carga',
      fechaEnvio: '2026-09-28T17:59:00Z',
    },
    { remitente: 'BOT', contenido: 'No entendí tu pregunta.', fechaEnvio: '2026-09-28T17:59:01Z' },
  ],
};

function pagina(contenido, totalElementos = contenido.length) {
  return { contenido, pagina: 0, tamano: POR_PAGINA, totalElementos };
}

function clienteFalso(sobrescribir = {}) {
  return {
    listarTickets: jest.fn(async () => pagina([RESUMEN])),
    obtenerTicket: jest.fn(async () => DETALLE),
    atenderTicket: jest.fn(async () => ({ ...DETALLE, estado: 'RESUELTO' })),
    ...sobrescribir,
  };
}

async function montar({ cliente = clienteFalso(), atender } = {}) {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  const pestana = montarSoporte(raiz, { cliente, ...(atender ? { atender } : {}) });
  await pestana.cargar();
  return { raiz, cliente, pestana };
}

describe('bandeja', () => {
  test('lista las solicitudes con tema, estado y fecha', async () => {
    const { raiz, cliente } = await montar();

    expect(cliente.listarTickets).toHaveBeenCalledWith({
      estado: null,
      pagina: 0,
      tamano: POR_PAGINA,
    });
    const fila = raiz.querySelector('[data-ticket="t-1"]');
    expect(fila.textContent).toContain('No carga mi inventario');
    expect(fila.textContent).toContain('Problema técnico');
    expect(fila.textContent).toContain('Abierta');
  });

  test('sin solicitudes lo dice', async () => {
    const { raiz } = await montar({
      cliente: clienteFalso({ listarTickets: jest.fn(async () => pagina([])) }),
    });

    expect(raiz.textContent).toContain('No hay solicitudes de soporte');
  });

  test('filtrar por estado vuelve a la primera página', async () => {
    const { raiz, cliente } = await montar();
    const filtro = raiz.querySelector('select[name="estado"]');

    filtro.value = 'EN_PROCESO';
    filtro.dispatchEvent(new Event('change'));
    await esperar();

    expect(cliente.listarTickets).toHaveBeenLastCalledWith({
      estado: 'EN_PROCESO',
      pagina: 0,
      tamano: POR_PAGINA,
    });
    expect(raiz.textContent).toContain('No carga mi inventario');
  });

  test('con más de una página se puede avanzar y volver', async () => {
    const cliente = clienteFalso({ listarTickets: jest.fn(async () => pagina([RESUMEN], 45)) });
    const { raiz } = await montar({ cliente });

    expect(raiz.textContent).toContain('Página 1 de 3');
    expect(raiz.querySelector('[data-accion="pagina-anterior"]').disabled).toBe(true);

    raiz.querySelector('[data-accion="pagina-siguiente"]').click();
    await esperar();
    expect(cliente.listarTickets).toHaveBeenLastCalledWith({
      estado: null,
      pagina: 1,
      tamano: POR_PAGINA,
    });

    raiz.querySelector('[data-accion="pagina-anterior"]').click();
    await esperar();
    expect(cliente.listarTickets).toHaveBeenLastCalledWith({
      estado: null,
      pagina: 0,
      tamano: POR_PAGINA,
    });
  });

  test('si el servicio no responde ofrece reintentar', async () => {
    const { raiz } = await montar({
      cliente: clienteFalso({
        listarTickets: jest.fn(async () => {
          throw new ErrorDelChatbot(null, 0, { rutaFija: true });
        }),
      }),
    });

    expect(raiz.textContent).toContain('no responde');
  });

  test('al guardar desde el detalle recarga y lo avisa', async () => {
    const atender = jest.fn(async () => true);
    const { raiz, cliente } = await montar({ atender });

    raiz.querySelector('[data-accion="abrir-ticket"]').click();
    await esperar();
    await esperar();

    expect(atender).toHaveBeenCalledWith({ cliente, ticketId: 't-1' });
    expect(cliente.listarTickets).toHaveBeenCalledTimes(2);
    expect(raiz.querySelector('.panel-chatbot__nota').textContent).toBe('Solicitud actualizada.');
  });
});

describe('detalle de una solicitud', () => {
  beforeEach(() => document.body.replaceChildren());

  async function abrir(cliente = clienteFalso()) {
    const resultado = atenderEnDialogo({ cliente, ticketId: 't-1' });
    await esperar();
    return { resultado, cliente, cuerpo: document.querySelector('.panel-chatbot__ticket') };
  }

  test('muestra el mensaje, el jugador y la conversación', async () => {
    const { cuerpo } = await abrir();

    expect(cuerpo.textContent).toContain('uid-jugador');
    expect(cuerpo.textContent).toContain('Se queda cargando');
    expect(cuerpo.textContent).toContain('mi inventario no carga');
    expect(cuerpo.textContent).toContain('Asistente');
  });

  test('responder y marcar como respondida manda solo lo que cambió', async () => {
    const { resultado, cliente } = await abrir();
    document.querySelector('select[name="estado"]').value = 'RESUELTO';
    document.querySelector('textarea[name="respuesta"]').value = '  Ya quedó.  ';

    document.querySelector('[data-accion="guardar-ticket"]').click();

    await expect(resultado).resolves.toBe(true);
    expect(cliente.atenderTicket).toHaveBeenCalledWith('t-1', {
      estado: 'RESUELTO',
      respuesta: 'Ya quedó.',
    });
  });

  test('marcar como respondida sin respuesta no se manda', async () => {
    const { cliente } = await abrir();
    document.querySelector('select[name="estado"]').value = 'RESUELTO';

    document.querySelector('[data-accion="guardar-ticket"]').click();
    await esperar();

    expect(cliente.atenderTicket).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain('Escribe la respuesta para el jugador.');
  });

  test('sin cambios lo dice y no llama al servicio', async () => {
    const { cliente } = await abrir();

    document.querySelector('[data-accion="guardar-ticket"]').click();
    await esperar();

    expect(cliente.atenderTicket).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain('No hay cambios que guardar.');
  });

  test('un 409 del servicio se explica y deja reintentar', async () => {
    const { cliente } = await abrir(
      clienteFalso({
        atenderTicket: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'x', motivo: 'TRANSICION_NO_PERMITIDA' }, 409);
        }),
      }),
    );
    document.querySelector('select[name="estado"]').value = 'CERRADO';

    document.querySelector('[data-accion="guardar-ticket"]').click();
    await esperar();

    expect(cliente.atenderTicket).toHaveBeenCalled();
    expect(document.body.textContent).toContain('no está permitido');
    expect(document.querySelector('[data-accion="guardar-ticket"]').disabled).toBe(false);
  });

  test('una solicitud cerrada no se puede cambiar', async () => {
    await abrir(
      clienteFalso({
        obtenerTicket: jest.fn(async () => ({ ...DETALLE, estado: 'CERRADO', respuesta: 'Listo' })),
      }),
    );

    expect(document.querySelector('select[name="estado"]')).toBeNull();
    expect(document.querySelector('[data-accion="guardar-ticket"]').disabled).toBe(true);
    expect(document.body.textContent).toContain('Respuesta enviada: Listo');
  });

  test('Cerrar resuelve sin cambios', async () => {
    const { resultado } = await abrir();

    document.querySelector('[data-accion="cerrar-detalle"]').click();

    await expect(resultado).resolves.toBe(false);
  });

  test('si el detalle no carga lo dice', async () => {
    await abrir(
      clienteFalso({
        obtenerTicket: jest.fn(async () => {
          throw new ErrorDelChatbot(null, 0, { rutaFija: true });
        }),
      }),
    );

    expect(document.body.textContent).toContain('no responde');
  });
});

test('las transiciones son las del contrato', () => {
  expect(SIGUIENTES.ABIERTO).toEqual(['EN_PROCESO', 'RESUELTO', 'CERRADO']);
  expect(SIGUIENTES.CERRADO).toEqual([]);
});

test('nombreDeCategoria usa el nombre legible y, si no lo conoce, la clave', () => {
  expect(nombreDeCategoria('CUENTA_Y_REGISTRO')).toBe('Mi cuenta');
  expect(nombreDeCategoria('OTRA')).toBe('OTRA');
});

test('textoDeFalloAlAtender por motivo y estado', () => {
  expect(
    textoDeFalloAlAtender(new ErrorDelChatbot({ motivo: 'TRANSICION_NO_PERMITIDA' }, 409)),
  ).toContain('no está permitido');
  expect(textoDeFalloAlAtender(new ErrorDelChatbot({ title: 'x' }, 404))).toContain('ya no existe');
  expect(textoDeFalloAlAtender(new ErrorDelChatbot({ title: 'x' }, 400))).toContain('2000');
  expect(textoDeFalloAlAtender(new ErrorDelChatbot(null, 0, { rutaFija: true }))).toContain(
    'no responde',
  );
});
