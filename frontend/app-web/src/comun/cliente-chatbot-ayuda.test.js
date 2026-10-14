/**
 * Cliente del asistente — ms-chatbot.yaml 1.8.0: la solicitud desde la
 * conversación y el centro de ayuda. Archivo propio para no tocar las pruebas
 * del cliente que cambia #506.
 */

import { jest } from '@jest/globals';

import { CLAVE_SESION_ANONIMA, crearClienteChatbot } from './cliente-chatbot.js';

function almacenEnMemoria(inicial = {}) {
  const datos = new Map(Object.entries(inicial));
  return {
    getItem: (clave) => (datos.has(clave) ? datos.get(clave) : null),
    setItem: (clave, valor) => datos.set(clave, String(valor)),
    removeItem: (clave) => datos.delete(clave),
  };
}

function cliente(fetch, sesion = { autenticado: true, token: 'token-del-jugador' }) {
  return crearClienteChatbot({
    fetch,
    almacen: almacenEnMemoria({ [CLAVE_SESION_ANONIMA]: 'visitante-fijo' }),
    almacenLocal: almacenEnMemoria(),
    sesion: () => sesion,
  });
}

const respuesta = (cuerpo, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => cuerpo,
});

describe('abrirTicketDesdeConversacion', () => {
  test('manda la vista y el comentario, y nada que elija la conversación', async () => {
    const fetch = jest.fn(async () => respuesta({ ticketId: 't-1' }, 201));

    const ticket = await cliente(fetch).abrirTicketDesdeConversacion({
      vista: 'SUBASTAS',
      comentario: '  Ya lo intenté.  ',
    });

    expect(ticket).toEqual({ ticketId: 't-1' });
    const [url, opciones] = fetch.mock.calls[0];
    expect(url).toBe('/api/v1/chat/tickets/desde-conversacion');
    expect(opciones.method).toBe('POST');
    expect(opciones.headers.Authorization).toBe('Bearer token-del-jugador');
    expect(JSON.parse(opciones.body)).toEqual({ vista: 'SUBASTAS', comentario: 'Ya lo intenté.' });
  });

  test('sin vista ni comentario manda un cuerpo vacío', async () => {
    const fetch = jest.fn(async () => respuesta({ ticketId: 't-1' }, 201));

    await cliente(fetch).abrirTicketDesdeConversacion();
    await cliente(fetch).abrirTicketDesdeConversacion({ vista: null, comentario: '   ' });

    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({});
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({});
  });

  test('un 409 llega con su motivo', async () => {
    const fetch = jest.fn(async () =>
      respuesta({ title: 'No hay conversacion', motivo: 'SIN_CONVERSACION' }, 409),
    );

    const error = await cliente(fetch)
      .abrirTicketDesdeConversacion()
      .catch((e) => e);

    expect(error.estado).toBe(409);
    expect(error.problema.motivo).toBe('SIN_CONVERSACION');
  });
});

describe('centroDeAyuda', () => {
  test('pide el centro de ayuda, también sin sesión', async () => {
    const centro = {
      categorias: [{ categoria: 'FAQ_GENERAL', ancla: 'categoria-faq-general', temas: [] }],
    };
    const fetch = jest.fn(async () => respuesta(centro));

    await expect(
      cliente(fetch, { autenticado: false, token: null }).centroDeAyuda(),
    ).resolves.toEqual(centro);
    const [url, opciones] = fetch.mock.calls[0];
    expect(url).toBe('/api/v1/chat/ayuda');
    expect(opciones.method).toBe('GET');
    expect(opciones.headers.Authorization).toBeUndefined();
  });

  test('si el servicio no está, el error lo dice', async () => {
    const fetch = jest.fn(async () => {
      throw new TypeError('sin red');
    });

    const error = await cliente(fetch)
      .centroDeAyuda()
      .catch((e) => e);

    expect(error.noDisponible).toBe(true);
  });
});
