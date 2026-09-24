/**
 * Cliente del asistente — HU-CHA-001, HU-CHA-008, HU-CHA-011.
 */

import { jest } from '@jest/globals';

import {
  CLAVE_BASE_LOCAL,
  CLAVE_SESION_ANONIMA,
  ErrorDelChatbot,
  baseDelChatbot,
  crearClienteChatbot,
  idDeSesionAnonima,
  rutaDelChatbot,
} from './cliente-chatbot.js';

function almacenEnMemoria(inicial = {}) {
  const datos = new Map(Object.entries(inicial));
  return {
    getItem: (clave) => (datos.has(clave) ? datos.get(clave) : null),
    setItem: (clave, valor) => datos.set(clave, String(valor)),
    removeItem: (clave) => datos.delete(clave),
  };
}

function respuestaJson(cuerpo, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => cuerpo,
  };
}

const VISITANTE = { autenticado: false, token: null };
const JUGADOR = { autenticado: true, token: 'token-del-jugador' };

function cliente({ fetch, sesion = VISITANTE, base = null } = {}) {
  return crearClienteChatbot({
    fetch,
    almacen: almacenEnMemoria({ [CLAVE_SESION_ANONIMA]: 'visitante-fijo' }),
    almacenLocal: almacenEnMemoria(base ? { [CLAVE_BASE_LOCAL]: base } : {}),
    sesion: () => sesion,
  });
}

describe('base de la API', () => {
  test('por omisión es el mismo origen', () => {
    expect(baseDelChatbot(almacenEnMemoria())).toBe('');
    expect(rutaDelChatbot('/chat/historial', almacenEnMemoria())).toBe('/api/v1/chat/historial');
  });

  test('localStorage la sobrescribe para desarrollo local, sin barra final', () => {
    const local = almacenEnMemoria({ [CLAVE_BASE_LOCAL]: ' http://localhost:8094/ ' });
    expect(rutaDelChatbot('/chat/mensajes', local)).toBe(
      'http://localhost:8094/api/v1/chat/mensajes',
    );
  });

  test('un almacenamiento que falla no rompe nada', () => {
    const roto = {
      getItem: () => {
        throw new Error('bloqueado');
      },
    };
    expect(baseDelChatbot(roto)).toBe('');
  });
});

describe('identificador del visitante', () => {
  test('se crea una vez y se reutiliza en la pestaña', () => {
    const almacen = almacenEnMemoria();
    const primero = idDeSesionAnonima(almacen, () => 'abc');
    const segundo = idDeSesionAnonima(almacen, () => 'otro');
    expect(primero).toBe('visitante-abc');
    expect(segundo).toBe('visitante-abc');
  });

  test('sin almacenamiento igual devuelve un identificador', () => {
    const roto = {
      getItem: () => {
        throw new Error('bloqueado');
      },
    };
    expect(idDeSesionAnonima(roto, () => 'efimero')).toBe('visitante-efimero');
  });
});

describe('cabeceras', () => {
  test('un visitante manda solo su identificador, sin Authorization', async () => {
    const fetch = jest.fn(async () => respuestaJson([]));
    await cliente({ fetch }).obtenerHistorial();

    const [url, opciones] = fetch.mock.calls[0];
    expect(url).toBe('/api/v1/chat/historial');
    expect(opciones.method).toBe('GET');
    expect(opciones.headers['X-Id-Sesion-Anonima']).toBe('visitante-fijo');
    expect(opciones.headers.Authorization).toBeUndefined();
  });

  // Se manda también con sesión: si el token vence a mitad de conversación,
  // el servicio lo trata como visitante en vez de responder 400.
  test('un jugador manda su token y también el identificador de visitante', async () => {
    const fetch = jest.fn(async () => respuestaJson([]));
    await cliente({ fetch, sesion: JUGADOR }).obtenerHistorial();

    const { headers } = fetch.mock.calls[0][1];
    expect(headers.Authorization).toBe('Bearer token-del-jugador');
    expect(headers['X-Id-Sesion-Anonima']).toBe('visitante-fijo');
  });
});

describe('operaciones', () => {
  test('enviarMensaje manda el contenido y devuelve la respuesta del bot', async () => {
    const bot = {
      id: 'm-1',
      remitente: 'BOT',
      contenido: 'Hola',
      adjuntoUrl: null,
      fechaEnvio: 'x',
    };
    const fetch = jest.fn(async () => respuestaJson(bot));

    const respuesta = await cliente({ fetch, base: 'http://localhost:8094' }).enviarMensaje('hola');

    const [url, opciones] = fetch.mock.calls[0];
    expect(url).toBe('http://localhost:8094/api/v1/chat/mensajes');
    expect(opciones.method).toBe('POST');
    expect(opciones.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(opciones.body)).toEqual({ contenido: 'hola' });
    expect(respuesta).toEqual(bot);
  });

  test('enviarMensaje incluye la URL de la captura solo si la hay', async () => {
    const fetch = jest.fn(async () => respuestaJson({}));
    await cliente({ fetch }).enviarMensaje('mira esto', 'https://img.example/captura.png');

    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      contenido: 'mira esto',
      adjuntoUrl: 'https://img.example/captura.png',
    });
  });

  test('limpiarHistorial usa DELETE y un 204 devuelve null', async () => {
    const fetch = jest.fn(async () => ({ ok: true, status: 204, json: async () => null }));
    const resultado = await cliente({ fetch }).limpiarHistorial();

    expect(fetch.mock.calls[0][1].method).toBe('DELETE');
    expect(resultado).toBeNull();
  });

  test('calificar manda útil y el comentario recortado; sin comentario no lo manda', async () => {
    const fetch = jest.fn(async () => respuestaJson({ id: 'c-1' }, 201));
    const chat = cliente({ fetch });

    await chat.calificar('m-1', false, '  no era eso  ');
    await chat.calificar('m-2', true, '   ');

    expect(fetch.mock.calls[0][0]).toBe('/api/v1/chat/mensajes/m-1/calificacion');
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      util: false,
      comentario: 'no era eso',
    });
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ util: true });
  });
});

describe('errores', () => {
  test('una respuesta problem+json se convierte en ErrorDelChatbot con su estado', async () => {
    const fetch = jest.fn(async () =>
      respuestaJson({ title: 'Conflict', detail: 'Esa respuesta ya fue calificada.' }, 409),
    );

    await expect(cliente({ fetch }).calificar('m-1', true)).rejects.toMatchObject({
      name: 'ErrorDelChatbot',
      estado: 409,
      detalle: 'Esa respuesta ya fue calificada.',
      noDisponible: false,
    });
  });

  test('sin red, el asistente queda como no disponible', async () => {
    const fetch = jest.fn(async () => {
      throw new TypeError('Failed to fetch');
    });

    const error = await cliente({ fetch })
      .enviarMensaje('hola')
      .catch((e) => e);
    expect(error).toBeInstanceOf(ErrorDelChatbot);
    expect(error.estado).toBe(0);
    expect(error.noDisponible).toBe(true);
  });

  test.each([502, 503, 504])('un %i del proxy es no disponible', async (status) => {
    const fetch = jest.fn(async () => ({
      ok: false,
      status,
      json: async () => {
        throw new SyntaxError('no es JSON');
      },
    }));

    const error = await cliente({ fetch })
      .obtenerHistorial()
      .catch((e) => e);
    expect(error.noDisponible).toBe(true);
    expect(error.problema).toBeNull();
  });

  test('el 404 del borde sin ruta es no disponible; otro 404 no', () => {
    const borde = new ErrorDelChatbot({ title: 'Ruta sin servicio en el borde' }, 404);
    const mensajeAjeno = new ErrorDelChatbot({ title: 'Not Found' }, 404);
    expect(borde.noDisponible).toBe(true);
    expect(mensajeAjeno.noDisponible).toBe(false);
  });

  // Historial y mensajes existen siempre: un 404 ahí es que no respondió el
  // asistente (p. ej. Live Server sin la base local). Al calificar, un 404 es
  // «ese mensaje no existe» y NO es no disponible.
  test('un 404 en historial es no disponible; al calificar no', async () => {
    const fetch = jest.fn(async () => ({
      ok: false,
      status: 404,
      json: async () => {
        throw new SyntaxError('HTML');
      },
    }));
    const chat = cliente({ fetch });

    const alCargar = await chat.obtenerHistorial().catch((e) => e);
    const alCalificar = await chat.calificar('m-1', true).catch((e) => e);

    expect(alCargar.noDisponible).toBe(true);
    expect(alCalificar.noDisponible).toBe(false);
  });

  test('401 y 403 son falta de permiso', () => {
    expect(new ErrorDelChatbot(null, 401).sinPermiso).toBe(true);
    expect(new ErrorDelChatbot(null, 403).sinPermiso).toBe(true);
    expect(new ErrorDelChatbot(null, 400).sinPermiso).toBe(false);
  });
});
