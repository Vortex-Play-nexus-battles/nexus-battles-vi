/**
 * HU-SAL-001 — Cliente HTTP de creacion de salas.
 *
 * Lo que se prueba aqui es la traduccion del problem details: que la vista
 * reciba siempre un `ErrorDeApi` utilizable, incluso cuando el servicio
 * responde sin cuerpo.
 */

// Con modulos ES, Jest NO inyecta `jest` como global: hay que importarlo.
// Misma linea que ya tiene inventario.test.js.
import { jest } from '@jest/globals';

import {
  crearSala,
  listarSalas,
  ingresarASala,
  verificarHeroe,
  baseDeApi,
  ErrorDeApi,
} from './cliente-salas.js';

const PARAMETROS = {
  maximoParticipantes: 4,
  modalidad: 'HASTA_SEIS',
  recompensaCreditos: 0,
  incluirHeroeIA: false,
  privada: false,
  tamanoEquipo: null,
};

function respuesta(estado, cuerpo, tipoContenido) {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    headers: { get: (clave) => (clave === 'content-type' ? (tipoContenido ?? null) : null) },
    json: async () => {
      if (cuerpo === undefined) {
        throw new SyntaxError('Unexpected end of JSON input');
      }
      return cuerpo;
    },
  };
}

describe('listarSalas', () => {
  test('sin filtros pide la ruta desnuda: los valores por defecto los pone el servidor', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { contenido: [] }));

    await listarSalas({}, { fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/salas');
  });

  test('traslada los filtros del contrato a la consulta', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { contenido: [] }));

    await listarSalas(
      { pagina: 2, tamano: 8, modalidad: 'HASTA_SEIS', estado: 'ABIERTA' },
      { fetchImpl },
    );

    const url = new URL(fetchImpl.mock.calls[0][0], 'http://local');
    expect(url.pathname).toBe('/api/v1/salas');
    expect(url.searchParams.get('pagina')).toBe('2');
    expect(url.searchParams.get('tamano')).toBe('8');
    expect(url.searchParams.get('modalidad')).toBe('HASTA_SEIS');
    expect(url.searchParams.get('estado')).toBe('ABIERTA');
  });

  test('un filtro vacio no se manda: no es lo mismo que filtrar por cadena vacia', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { contenido: [] }));

    await listarSalas({ modalidad: '', estado: null, pagina: 0 }, { fetchImpl });

    const url = new URL(fetchImpl.mock.calls[0][0], 'http://local');
    expect(url.searchParams.has('modalidad')).toBe(false);
    expect(url.searchParams.has('estado')).toBe(false);
    expect(url.searchParams.get('pagina')).toBe('0');
  });

  test('devuelve la pagina tal cual la da el contrato', async () => {
    const paginaDelContrato = {
      contenido: [{ id: 'a', estado: 'ABIERTA' }],
      pagina: 0,
      tamano: 16,
      totalElementos: 1,
      totalPaginas: 1,
    };
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, paginaDelContrato));

    expect(await listarSalas({}, { fetchImpl })).toEqual(paginaDelContrato);
  });
});

describe('ingresarASala', () => {
  test('llama a la subruta de participantes sin cuerpo: el jugador sale del token', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { id: 'abc' }));

    await ingresarASala('abc', { fetchImpl });

    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/salas/abc/participantes');
    expect(opciones.method).toBe('POST');
    expect(opciones.body).toBeUndefined();
  });

  test('un 403 de sala privada llega con su tipo, para que la vista lo distinga', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(403, {
        type: 'https://nexusbattles.local/errores/sala-privada',
        title: 'Esta sala es privada',
        detail: 'Necesitas un codigo de invitacion.',
        status: 403,
      }),
    );

    const error = await ingresarASala('abc', { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.tipo).toBe('https://nexusbattles.local/errores/sala-privada');
    expect(error.estado).toBe(403);
  });

  test('un 409 de sala llena tambien llega interpretado', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(409, {
        type: 'https://nexusbattles.local/errores/ingreso-no-permitido',
        title: 'No puedes entrar',
        detail: 'La sala ya alcanzo su maximo de participantes.',
        status: 409,
      }),
    );

    const error = await ingresarASala('abc', { fetchImpl }).catch((e) => e);

    expect(error.estado).toBe(409);
    expect(error.detalle).toContain('maximo de participantes');
  });
});

describe('verificarHeroe', () => {
  test('consulta la ruta de verificacion del contrato', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { resultado: 'DISPONIBLE' }));

    await verificarHeroe('abc', { fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/salas/abc/verificacion-heroe');
  });

  test('devuelve el veredicto tal cual: la vista no decide nada', async () => {
    const veredicto = {
      resultado: 'HEROE_OCUPADO',
      puedeIngresar: false,
      heroe: { id: 'h1', nombre: 'Arquero del Norte', vidaActual: 120, vidaMaxima: 120 },
      salaQueLoOcupa: 'Torre del Alba',
    };
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, veredicto));

    expect(await verificarHeroe('abc', { fetchImpl })).toEqual(veredicto);
  });

  test('mientras el servicio no implemente la ruta, el 404 llega interpretado', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(404, undefined));

    const error = await verificarHeroe('abc', { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.estado).toBe(404);
  });
});

describe('crearSala', () => {
  test('envia el cuerpo al contrato y devuelve la sala creada', async () => {
    const sala = { id: 'abc', estado: 'ABIERTA', maximoParticipantes: 4 };
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(201, sala));

    const resultado = await crearSala(PARAMETROS, { fetchImpl });

    expect(resultado).toEqual(sala);
    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/salas');
    expect(opciones.method).toBe('POST');
    expect(JSON.parse(opciones.body)).toEqual(PARAMETROS);
  });

  test('el anfitrion no viaja en el cuerpo: lo pone el servidor con el token', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(201, {}));

    await crearSala(PARAMETROS, { fetchImpl });

    const enviado = JSON.parse(fetchImpl.mock.calls[0][1].body);
    expect(enviado).not.toHaveProperty('idAnfitrion');
    expect(enviado).not.toHaveProperty('anfitrion');
  });

  test('traduce un rechazo por campos a un error de formulario', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(400, {
        type: 'https://nexusbattles.local/errores/parametros-invalidos',
        title: 'Revisa los datos de la sala',
        status: 400,
        detail: 'Hay 2 campos que corregir.',
        errores: [
          { campo: 'maximoParticipantes', mensaje: 'Fuera de rango.' },
          { campo: 'recompensaCreditos', mensaje: 'No puede ser negativa.' },
        ],
      }),
    );

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.esDeFormulario).toBe(true);
    expect(error.errores).toHaveLength(2);
    expect(error.errores[0].campo).toBe('maximoParticipantes');
    expect(error.estado).toBe(400);
  });

  test('traduce los creditos insuficientes conservando el motivo', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(422, {
        type: 'https://nexusbattles.local/errores/creditos-insuficientes',
        title: 'Creditos insuficientes',
        status: 422,
        detail: 'Tienes 240 creditos y necesitas 400 para crear esta sala.',
      }),
    );

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(error.tipo).toBe('https://nexusbattles.local/errores/creditos-insuficientes');
    expect(error.detalle).toContain('240');
    expect(error.detalle).toContain('400');
    expect(error.esDeFormulario).toBe(false);
  });

  test('el 503 de creditos sin integrar llega con su tipo, para poder distinguirlo', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(503, {
        type: 'https://nexusbattles.local/errores/creditos-sin-integrar',
        title: 'Las apuestas todavia no estan disponibles',
        status: 503,
        detail: 'Por ahora solo se pueden crear salas sin recompensa.',
      }),
    );

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(error.tipo).toBe('https://nexusbattles.local/errores/creditos-sin-integrar');
    expect(error.estado).toBe(503);
  });

  test('un 401 sin cuerpo no revienta: produce un mensaje utilizable', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(401, undefined));

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.estado).toBe(401);
    expect(error.detalle).toMatch(/iniciar sesion/i);
  });

  test('un error sin cuerpo ni forma conocida sigue diciendo algo', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(500, undefined));

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(error.estado).toBe(500);
    expect(error.detalle).toContain('500');
  });
});

/**
 * Ejecucion estatica contra ejecucion integrada.
 *
 * Al abrir las vistas desde un servidor de ficheros no hay API detras. Antes
 * eso se veia como «No se pudo crear la sala · El servicio respondio 405», que
 * culpa al servicio de salas de algo que ni siquiera esta levantado.
 */
describe('sin backend detras', () => {
  test('un 405 de servidor estatico se nombra por lo que es, no como fallo del servicio', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(405, undefined, 'text/html'));

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(error.titulo).toMatch(/no hay ninguna api/i);
    expect(error.detalle).toContain('/api/v1/salas');
    expect(error.detalle).not.toMatch(/respondio 405/i);
  });

  test('el 405 real de http-server llega como text/plain y tambien se reconoce', async () => {
    // `http-server` responde 405 con `content-type: text/plain` a un POST.
    // Reducir la deteccion a HTML, como sugirio Copilot en #271, perderia
    // justo este caso, que es el que motivo la distincion.
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(405, undefined, 'text/plain'));

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(error.titulo).toMatch(/no hay ninguna api/i);
  });

  test('un 405 con problem details es un fallo del servicio, no un servidor estatico', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValue(
        respuesta(
          405,
          { status: 405, title: 'Metodo no permitido', detail: 'Solo GET.' },
          'application/problem+json',
        ),
      );

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(error.titulo).toBe('Metodo no permitido');
  });

  test('el mensaje nombra la URL real de la peticion cuando fetch la trae', async () => {
    const conUrl = {
      ...respuesta(404, undefined, 'text/html'),
      url: 'http://127.0.0.1:4399/api/v1/salas/s1/verificacion-heroe',
    };
    const fetchImpl = jest.fn().mockResolvedValue(conUrl);

    const error = await verificarHeroe('s1', { fetchImpl }).catch((e) => e);

    expect(error.detalle).toContain('/api/v1/salas/s1/verificacion-heroe');
  });

  test('un GET que devuelve la pagina HTML del servidor estatico tambien se detecta', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValue(respuesta(404, undefined, 'text/html; charset=utf-8'));

    const error = await listarSalas({}, { fetchImpl }).catch((e) => e);

    expect(error.titulo).toMatch(/no hay ninguna api/i);
  });

  test('un fallo real del servicio sigue siendo un fallo del servicio', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValue(respuesta(503, undefined, 'application/problem+json'));

    const error = await listarSalas({}, { fetchImpl }).catch((e) => e);

    expect(error.titulo).not.toMatch(/no hay ninguna api/i);
    expect(error.detalle).toContain('503');
  });
});

describe('baseDeApi', () => {
  afterEach(() => {
    document.head.innerHTML = '';
  });

  test('sin declararla, es el mismo origen: nada de localhost escrito en el codigo', () => {
    expect(baseDeApi()).toBe('');
  });

  test('la pagina puede apuntar a un backend en otro sitio', () => {
    document.head.innerHTML = '<meta name="nexus-api-base" content="http://127.0.0.1:8083/" />';

    expect(baseDeApi()).toBe('http://127.0.0.1:8083');
  });

  test('la ruta se construye sobre esa base', async () => {
    document.head.innerHTML = '<meta name="nexus-api-base" content="http://127.0.0.1:8083" />';
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { contenido: [] }));

    await listarSalas({}, { fetchImpl });

    expect(fetchImpl).toHaveBeenCalledWith('http://127.0.0.1:8083/api/v1/salas');
  });
});
