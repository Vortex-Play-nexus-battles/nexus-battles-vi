/**
 * Cliente del asistente — HU-CHA-001, HU-CHA-008, HU-CHA-011.
 */

import { jest } from '@jest/globals';

import {
  CLAVE_BASE_LOCAL,
  CLAVE_SESION_ANONIMA,
  ErrorDelChatbot,
  TIEMPO_DE_ESPERA_MS,
  TIEMPO_DE_SUBIDA_MS,
  baseDelChatbot,
  crearClienteChatbot,
  idDeSesionAnonima,
  imagenDeCaptura,
  rutaDelChatbot,
  segundosDeEspera,
} from './cliente-chatbot.js';
import { CLAVE_SESION_ANONIMA as CLAVE_EN_SESION } from './sesion.js';

function almacenEnMemoria(inicial = {}) {
  const datos = new Map(Object.entries(inicial));
  return {
    getItem: (clave) => (datos.has(clave) ? datos.get(clave) : null),
    setItem: (clave, valor) => datos.set(clave, String(valor)),
    removeItem: (clave) => datos.delete(clave),
  };
}

function respuestaJson(cuerpo, status = 200, cabeceras = {}) {
  const valores = new Map(Object.entries(cabeceras).map(([k, v]) => [k.toLowerCase(), v]));
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (nombre) => valores.get(String(nombre).toLowerCase()) ?? null },
    json: async () => cuerpo,
  };
}

/** Un `fetch` que no responde nunca, salvo para abortarse con su señal. */
function fetchColgado() {
  return jest.fn(
    (_url, { signal }) =>
      new Promise((_resolver, rechazar) => {
        signal.addEventListener('abort', () =>
          rechazar(new DOMException('Abortada', 'AbortError')),
        );
      }),
  );
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

  test('enviarMensaje manda la vista solo si la hay', async () => {
    const fetch = jest.fn(async () => respuestaJson({}));
    const chat = cliente({ fetch });

    await chat.enviarMensaje('como pujo', { vista: 'SUBASTAS' });
    await chat.enviarMensaje('hola', { vista: null });

    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      contenido: 'como pujo',
      vista: 'SUBASTAS',
    });
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ contenido: 'hola' });
  });

  test('obtenerHistorial pide una página solo si se le dan límite o cursor', async () => {
    const fetch = jest.fn(async () => respuestaJson([]));
    const chat = cliente({ fetch });

    await chat.obtenerHistorial();
    await chat.obtenerHistorial({ limite: 30 });
    await chat.obtenerHistorial({ antesDe: 'm-40', limite: 30 });

    expect(fetch.mock.calls[0][0]).toBe('/api/v1/chat/historial');
    expect(fetch.mock.calls[1][0]).toBe('/api/v1/chat/historial?limite=30');
    expect(fetch.mock.calls[2][0]).toBe('/api/v1/chat/historial?antesDe=m-40&limite=30');
  });

  test('preferencias usa GET y guardarPreferencias manda el par completo con PUT', async () => {
    const fetch = jest
      .fn()
      .mockResolvedValueOnce(respuestaJson({ idioma: 'AUTOMATICO', nivelDetalle: 'NORMAL' }))
      .mockResolvedValueOnce(respuestaJson({ idioma: 'EN', nivelDetalle: 'BREVE' }));
    const chat = cliente({ fetch, sesion: JUGADOR });

    expect(await chat.preferencias()).toEqual({ idioma: 'AUTOMATICO', nivelDetalle: 'NORMAL' });
    expect(await chat.guardarPreferencias({ idioma: 'EN', nivelDetalle: 'BREVE' })).toEqual({
      idioma: 'EN',
      nivelDetalle: 'BREVE',
    });

    expect(fetch.mock.calls[0][0]).toBe('/api/v1/chat/preferencias');
    expect(fetch.mock.calls[0][1].method).toBe('GET');
    expect(fetch.mock.calls[1][1].method).toBe('PUT');
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({
      idioma: 'EN',
      nivelDetalle: 'BREVE',
    });
    expect(fetch.mock.calls[1][1].headers.Authorization).toBe('Bearer token-del-jugador');
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

  test('abrirTicket manda categoría, asunto y mensaje con el token del jugador', async () => {
    const fetch = jest.fn(async () => respuestaJson({ ticketId: 't-1', estado: 'ABIERTO' }, 201));

    const ticket = await cliente({ fetch, sesion: JUGADOR }).abrirTicket({
      categoria: 'SOPORTE_TECNICO',
      asunto: 'No carga',
      mensaje: 'Se queda cargando',
    });

    const [url, opciones] = fetch.mock.calls[0];
    expect(url).toBe('/api/v1/chat/tickets');
    expect(opciones.method).toBe('POST');
    expect(opciones.headers.Authorization).toBe('Bearer token-del-jugador');
    expect(JSON.parse(opciones.body)).toEqual({
      categoria: 'SOPORTE_TECNICO',
      asunto: 'No carga',
      mensaje: 'Se queda cargando',
    });
    expect(ticket.estado).toBe('ABIERTO');
  });

  test('misTickets devuelve la lista, o vacía si no llega cuerpo', async () => {
    const fetch = jest
      .fn()
      .mockResolvedValueOnce(respuestaJson([{ ticketId: 't-1' }]))
      .mockResolvedValueOnce({ ok: true, status: 204, json: async () => null });
    const chat = cliente({ fetch, sesion: JUGADOR });

    expect(await chat.misTickets()).toEqual([{ ticketId: 't-1' }]);
    expect(await chat.misTickets()).toEqual([]);
    expect(fetch.mock.calls[0][0]).toBe('/api/v1/chat/tickets');
    expect(fetch.mock.calls[0][1].method).toBe('GET');
  });

  test('sugerencias arma la consulta solo con lo que hay y devuelve la lista', async () => {
    const fetch = jest
      .fn()
      .mockResolvedValueOnce(respuestaJson([{ clave: 'k-1' }]))
      .mockResolvedValueOnce(respuestaJson([]));
    const chat = cliente({ fetch });

    expect(await chat.sugerencias()).toEqual([{ clave: 'k-1' }]);
    await chat.sugerencias({ q: '  cómo  ', categoria: 'PRODUCTO', limite: 3 });

    expect(fetch.mock.calls[0][0]).toBe('/api/v1/chat/sugerencias?limite=6');
    expect(fetch.mock.calls[1][0]).toBe(
      '/api/v1/chat/sugerencias?q=c%C3%B3mo&categoria=PRODUCTO&limite=3',
    );
    expect(fetch.mock.calls[0][1].method).toBe('GET');
  });

  test('un 409 al abrir un ticket conserva el motivo del servidor', async () => {
    const fetch = jest.fn(async () =>
      respuestaJson({ title: 'Ya tienes un ticket abierto', motivo: 'TICKET_ABIERTO' }, 409),
    );

    const error = await cliente({ fetch, sesion: JUGADOR })
      .abrirTicket({ categoria: 'FAQ_GENERAL', asunto: 'a', mensaje: 'b' })
      .catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDelChatbot);
    expect(error.estado).toBe(409);
    expect(error.problema.motivo).toBe('TICKET_ABIERTO');
    expect(error.noDisponible).toBe(false);
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

// #506 — el error dice el estado, el motivo y, en un 429, cuánto esperar.
describe('motivo y Retry-After (#506)', () => {
  test.each([
    ['CONTENIDO_BLOQUEADO', 422],
    ['INYECCION_DETECTADA', 422],
    ['MODERACION_NO_DISPONIBLE', 503],
  ])('un %s llega con su estado y su motivo', async (motivo, estado) => {
    const fetch = jest.fn(async () =>
      respuestaJson({ title: 'x', status: estado, motivo }, estado),
    );

    const error = await cliente({ fetch })
      .enviarMensaje('hola')
      .catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDelChatbot);
    expect(error.estado).toBe(estado);
    expect(error.motivo).toBe(motivo);
    expect(error.reintentarEn).toBeNull();
  });

  test('un 429 lee los segundos de la cabecera Retry-After', async () => {
    const fetch = jest.fn(async () =>
      respuestaJson({ title: 'Too Many Requests', motivo: 'LIMITE_DE_FRECUENCIA' }, 429, {
        'Retry-After': '17',
      }),
    );

    const error = await cliente({ fetch })
      .enviarMensaje('hola')
      .catch((e) => e);

    expect(error.estado).toBe(429);
    expect(error.motivo).toBe('LIMITE_DE_FRECUENCIA');
    expect(error.reintentarEn).toBe(17);
    expect(error.noDisponible).toBe(false);
  });

  test('un 429 del borde, sin cuerpo ni Retry-After, sigue siendo un 429', async () => {
    const fetch = jest.fn(async () => ({
      ok: false,
      status: 429,
      json: async () => {
        throw new SyntaxError('no es JSON');
      },
    }));

    const error = await cliente({ fetch })
      .enviarMensaje('hola')
      .catch((e) => e);

    expect(error.estado).toBe(429);
    expect(error.motivo).toBeNull();
    expect(error.reintentarEn).toBeNull();
  });

  // El asistente respondió; lo que no pudo es revisar el texto.
  test('el 503 de la moderación no es «el asistente no está»; otro 503 sí', () => {
    expect(new ErrorDelChatbot({ motivo: 'MODERACION_NO_DISPONIBLE' }, 503).noDisponible).toBe(
      false,
    );
    expect(new ErrorDelChatbot({ title: 'Service Unavailable' }, 503).noDisponible).toBe(true);
  });

  test('un motivo que no es texto no se toma por motivo', () => {
    expect(new ErrorDelChatbot({ motivo: { raro: true } }, 422).motivo).toBeNull();
    expect(new ErrorDelChatbot(null, 422).motivo).toBeNull();
  });

  test('segundosDeEspera entiende segundos y fechas, y nada más', () => {
    const ahora = () => Date.parse('2026-10-14T12:00:00Z');
    expect(segundosDeEspera('30')).toBe(30);
    expect(segundosDeEspera(' 0 ')).toBe(0);
    expect(segundosDeEspera('Wed, 14 Oct 2026 12:00:42 GMT', ahora)).toBe(42);
    expect(segundosDeEspera('Wed, 14 Oct 2026 11:00:00 GMT', ahora)).toBe(0);
    expect(segundosDeEspera(null)).toBeNull();
    expect(segundosDeEspera('')).toBeNull();
    expect(segundosDeEspera('pronto')).toBeNull();
    // V8 lee estos dos como fechas de 2001: no son segundos válidos.
    expect(segundosDeEspera('-5')).toBeNull();
    expect(segundosDeEspera('1.5')).toBeNull();
  });
});

// #506 — una petición que no responde se corta y cuenta como sin red.
describe('tiempo de espera (#506)', () => {
  test('por omisión son diez segundos', () => {
    expect(TIEMPO_DE_ESPERA_MS).toBe(10_000);
  });

  test('pasado el tiempo, se aborta y es un fallo de red (estado 0, no disponible)', async () => {
    const fetch = fetchColgado();
    const chat = crearClienteChatbot({
      fetch,
      almacen: almacenEnMemoria(),
      almacenLocal: almacenEnMemoria(),
      sesion: () => VISITANTE,
      tiempoDeEspera: 5,
    });

    const error = await chat.enviarMensaje('hola').catch((e) => e);

    expect(fetch.mock.calls[0][1].signal).toBeInstanceOf(AbortSignal);
    expect(fetch.mock.calls[0][1].signal.aborted).toBe(true);
    expect(error).toBeInstanceOf(ErrorDelChatbot);
    expect(error.estado).toBe(0);
    expect(error.tiempoAgotado).toBe(true);
    expect(error.noDisponible).toBe(true);
  });

  test('también si lo que se atasca es el cuerpo de la respuesta', async () => {
    const fetch = jest.fn(async (_url, { signal }) => ({
      ok: true,
      status: 200,
      json: () =>
        new Promise((_resolver, rechazar) => {
          signal.addEventListener('abort', () => rechazar(new DOMException('x', 'AbortError')));
        }),
    }));
    const chat = crearClienteChatbot({
      fetch,
      almacen: almacenEnMemoria(),
      almacenLocal: almacenEnMemoria(),
      sesion: () => VISITANTE,
      tiempoDeEspera: 5,
    });

    const error = await chat.obtenerHistorial().catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDelChatbot);
    expect(error.estado).toBe(0);
    expect(error.tiempoAgotado).toBe(true);
  });

  test('una respuesta a tiempo no se aborta después', async () => {
    const fetch = jest.fn(async () => respuestaJson([]));
    const chat = crearClienteChatbot({
      fetch,
      almacen: almacenEnMemoria(),
      almacenLocal: almacenEnMemoria(),
      sesion: () => VISITANTE,
      tiempoDeEspera: 5,
    });

    expect(await chat.obtenerHistorial()).toEqual([]);
    await new Promise((resolver) => setTimeout(resolver, 15));
    expect(fetch.mock.calls[0][1].signal.aborted).toBe(false);
  });

  test('un fallo de red que no es la espera no se marca como tiempo agotado', async () => {
    const fetch = jest.fn(async () => {
      throw new TypeError('Failed to fetch');
    });
    const error = await cliente({ fetch })
      .obtenerHistorial()
      .catch((e) => e);
    expect(error.estado).toBe(0);
    expect(error.tiempoAgotado).toBe(false);
  });
});

// #506 — la clave vive en sesion.js, que la borra al cerrar sesión y al
// entrar; el cliente la reexporta y la dependencia va en un solo sentido.
test('el identificador de visitante se guarda en la clave que olvida la sesión', () => {
  expect(CLAVE_SESION_ANONIMA).toBe('nexus.chatbot.sesionAnonima');
  expect(CLAVE_SESION_ANONIMA).toBe(CLAVE_EN_SESION);
  const almacen = almacenEnMemoria();
  idDeSesionAnonima(almacen, () => 'abc');
  expect(almacen.getItem(CLAVE_EN_SESION)).toBe('visitante-abc');
});

// --- ms-chatbot.yaml 1.14.0 (§7.4.6): capturas de una solicitud -----------

function respuestaImagen(contenido, tipo = 'image/png', status = 200) {
  return {
    ok: true,
    status,
    headers: { get: (nombre) => (String(nombre).toLowerCase() === 'content-type' ? tipo : null) },
    blob: async () => new Blob([contenido], { type: tipo }),
  };
}

describe('capturas (1.14.0)', () => {
  test('subirCaptura manda un formulario multipart con la parte «archivo», sin su nombre ni Content-Type propio', async () => {
    const guardada = { capturaId: 'c-1', tipo: 'image/png', tamano: 3, ancho: 1, alto: 1 };
    const fetch = jest.fn(async () => respuestaJson(guardada, 201));
    const archivo = new File(['png'], 'Mi pantallazo con mi nombre.png', { type: 'image/png' });

    const respuesta = await cliente({ fetch, sesion: JUGADOR }).subirCaptura('t 1', archivo);

    expect(respuesta).toEqual(guardada);
    const [url, opciones] = fetch.mock.calls[0];
    expect(url).toBe('/api/v1/chat/tickets/t%201/capturas');
    expect(opciones.method).toBe('POST');
    expect(opciones.body).toBeInstanceOf(FormData);
    const parte = opciones.body.get('archivo');
    expect(parte.name).toBe('captura');
    expect(parte.size).toBe(3);
    // El navegador pone el Content-Type con su frontera; uno propio la rompería.
    expect(opciones.headers['Content-Type']).toBeUndefined();
    expect(opciones.headers.Authorization).toBe('Bearer token-del-jugador');
    expect(opciones.headers['X-Id-Sesion-Anonima']).toBe('visitante-fijo');
  });

  test('una subida tiene un minuto, no los diez segundos de una pregunta', () => {
    expect(TIEMPO_DE_SUBIDA_MS).toBe(60_000);
  });

  test('un rechazo trae su motivo, y un 404 de la solicitud no es «el asistente no está»', async () => {
    const fetch = jest.fn(async () =>
      respuestaJson({ title: 'x', status: 404, motivo: 'SOLICITUD_NO_ENCONTRADA' }, 404),
    );

    const error = await cliente({ fetch })
      .subirCaptura('t-1', new Blob(['x']))
      .catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDelChatbot);
    expect(error.motivo).toBe('SOLICITUD_NO_ENCONTRADA');
    expect(error.noDisponible).toBe(false);
  });

  test('leerCaptura pide la imagen con la identidad y la devuelve como Blob de su tipo', async () => {
    const fetch = jest.fn(async () => respuestaImagen('jpeg', 'image/jpeg'));

    const imagen = await cliente({ fetch }).leerCaptura('t-1', 'c/1');

    const [url, opciones] = fetch.mock.calls[0];
    expect(url).toBe('/api/v1/chat/tickets/t-1/capturas/c%2F1');
    expect(opciones.method).toBe('GET');
    expect(opciones.headers.Accept).toBe('image/png, image/jpeg, application/problem+json');
    expect(opciones.headers['X-Id-Sesion-Anonima']).toBe('visitante-fijo');
    expect(imagen).toBeInstanceOf(Blob);
    expect(imagen.type).toBe('image/jpeg');
    expect(imagen.size).toBe(4);
  });

  test('una respuesta que no es una imagen no se devuelve como captura', async () => {
    const fetch = jest.fn(async () => respuestaImagen('<script>', 'text/html'));

    const error = await cliente({ fetch })
      .leerCaptura('t-1', 'c-1')
      .catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDelChatbot);
  });

  test('imagenDeCaptura fija el tipo comprobado, con o sin parámetros', async () => {
    const imagen = await imagenDeCaptura(respuestaImagen('png', 'IMAGE/PNG; charset=binary'));
    expect(imagen.type).toBe('image/png');
    await expect(imagenDeCaptura(respuestaImagen('x', 'image/svg+xml'))).rejects.toBeInstanceOf(
      ErrorDelChatbot,
    );
    await expect(
      imagenDeCaptura({ ok: true, status: 200, headers: {}, blob: async () => new Blob() }),
    ).rejects.toBeInstanceOf(ErrorDelChatbot);
  });

  test('si el contenido se atasca, cuenta como fallo de red', async () => {
    const fetch = jest.fn(async (_url, { signal }) => ({
      ok: true,
      status: 200,
      headers: { get: () => 'image/png' },
      blob: () =>
        new Promise((_resolver, rechazar) => {
          signal.addEventListener('abort', () => rechazar(new DOMException('x', 'AbortError')));
        }),
    }));
    const chat = crearClienteChatbot({
      fetch,
      almacen: almacenEnMemoria(),
      almacenLocal: almacenEnMemoria(),
      sesion: () => VISITANTE,
      tiempoDeEspera: 5,
    });

    const error = await chat.leerCaptura('t-1', 'c-1').catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDelChatbot);
    expect(error.estado).toBe(0);
    expect(error.tiempoAgotado).toBe(true);
  });

  test('un fallo al leer que no es la espera sigue tal cual', async () => {
    const fetch = jest.fn(async () => ({
      ok: true,
      status: 200,
      headers: { get: () => 'image/png' },
      blob: async () => {
        throw new TypeError('roto');
      },
    }));

    const error = await cliente({ fetch })
      .leerCaptura('t-1', 'c-1')
      .catch((e) => e);

    expect(error).toBeInstanceOf(TypeError);
  });
});
