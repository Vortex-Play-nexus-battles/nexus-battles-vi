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
  iniciarPartida,
  obtenerPartida,
  obtenerSala,
  abandonarSala,
  cancelarSala,
  baseDeApi,
  esSalaPrivada,
  esHeroeNoDisponible,
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

  test('un filtro vacío no se manda: no es lo mismo que filtrar por cadena vacia', async () => {
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
  test('a una sala publica se entra sin cuerpo: el jugador sale del token', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { id: 'abc' }));

    await ingresarASala('abc', { fetchImpl });

    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/salas/abc/participantes');
    expect(opciones.method).toBe('POST');
    // El contrato declara el cuerpo opcional justamente para esto: mandar
    // `{"codigoInvitacion": null}` seria pedirle ruido al cliente.
    expect(opciones.body).toBeUndefined();
  });

  /**
   * FI-R4 — el codigo de invitacion viaja.
   *
   * Esta funcion hacia `POST` **sin cuerpo siempre**. El servidor recibia
   * `null` como codigo y el agregado rechaza con 403 toda sala privada, porque
   * `codigoCoincide(null)` nunca es cierto. Una sala privada era, por
   * construccion, una sala a la que no podia entrar nadie salvo su anfitrion —
   * y nada lo decia. El backend lleva el mecanismo completo desde la migracion
   * V5.
   */
  describe('FI-R4 - el codigo de invitacion', () => {
    test('con codigo, va en el cuerpo y con su Content-Type', async () => {
      const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { id: 'abc' }));

      await ingresarASala('abc', { codigoInvitacion: 'WXYZ-2345', fetchImpl });

      const [, opciones] = fetchImpl.mock.calls[0];
      expect(JSON.parse(opciones.body)).toEqual({ codigoInvitacion: 'WXYZ-2345' });
      expect(opciones.headers['Content-Type']).toBe('application/json');
    });

    test('el codigo se manda tal cual lo escribio la persona', async () => {
      // El servidor normaliza mayusculas, espacios y guiones (`Sala.normalizar`).
      // Limpiarlo aqui crearia una segunda verdad sobre que forma tiene un
      // codigo valido, y las dos se desincronizarian.
      const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { id: 'abc' }));

      await ingresarASala('abc', { codigoInvitacion: '  wxyz2345  ', fetchImpl });

      expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual({
        codigoInvitacion: 'wxyz2345',
      });
    });

    test('un codigo en blanco no manda cuerpo: es lo mismo que no tenerlo', async () => {
      const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { id: 'abc' }));

      await ingresarASala('abc', { codigoInvitacion: '   ', fetchImpl });

      expect(fetchImpl.mock.calls[0][1].body).toBeUndefined();
    });

    test('esSalaPrivada reconoce el 403 del dominio por su tipo, no por su texto', async () => {
      const fetchImpl = jest.fn().mockResolvedValue(
        respuesta(403, {
          type: 'https://nexusbattles.local/errores/sala-privada',
          title: 'Esta sala es privada',
          status: 403,
        }),
      );

      const error = await ingresarASala('abc', { fetchImpl }).catch((e) => e);

      expect(esSalaPrivada(error)).toBe(true);
    });

    test('otros rechazos no se confunden con una sala privada', async () => {
      // Un 409 dice «prueba con otra sala»; un 403 de otro tipo no se arregla
      // escribiendo un codigo. Pedirlo en esos casos seria mandar a la persona
      // a buscar algo que no existe.
      const llena = await ingresarASala('abc', {
        fetchImpl: jest.fn().mockResolvedValue(
          respuesta(409, {
            type: 'urn:llena',
            title: 'La sala ya alcanzo su maximo',
            status: 409,
          }),
        ),
      }).catch((e) => e);
      expect(esSalaPrivada(llena)).toBe(false);

      const otro403 = await ingresarASala('abc', {
        fetchImpl: jest
          .fn()
          .mockResolvedValue(
            respuesta(403, { type: 'urn:otra-cosa', title: 'Prohibido', status: 403 }),
          ),
      }).catch((e) => e);
      expect(esSalaPrivada(otro403)).toBe(false);
    });
  });

  test('un 403 de sala privada llega con su tipo, para que la vista lo distinga', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(403, {
        type: 'https://nexusbattles.local/errores/sala-privada',
        title: 'Esta sala es privada',
        detail: 'Necesitas un código de invitación.',
        status: 403,
      }),
    );

    const error = await ingresarASala('abc', { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.tipo).toBe('https://nexusbattles.local/errores/sala-privada');
    expect(error.estado).toBe(403);
  });

  test('un 409 de sala llena también llega interpretado', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(409, {
        type: 'https://nexusbattles.local/errores/ingreso-no-permitido',
        title: 'No puedes entrar',
        detail: 'La sala ya alcanzo su máximo de participantes.',
        status: 409,
      }),
    );

    const error = await ingresarASala('abc', { fetchImpl }).catch((e) => e);

    expect(error.estado).toBe(409);
    expect(error.detalle).toContain('máximo de participantes');
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

  test('traduce los créditos insuficientes conservando el motivo', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(422, {
        type: 'https://nexusbattles.local/errores/creditos-insuficientes',
        title: 'Creditos insuficientes',
        status: 422,
        detail: 'Tienes 240 créditos y necesitas 400 para crear esta sala.',
      }),
    );

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(error.tipo).toBe('https://nexusbattles.local/errores/creditos-insuficientes');
    expect(error.detalle).toContain('240');
    expect(error.detalle).toContain('400');
    expect(error.esDeFormulario).toBe(false);
  });

  test('el 503 de créditos sin integrar llega con su tipo, para poder distinguirlo', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(503, {
        type: 'https://nexusbattles.local/errores/creditos-sin-integrar',
        title: 'Las apuestas todavía no están disponibles',
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
    expect(error.detalle).toMatch(/iniciar sesión/i);
  });

  test('un error sin cuerpo ni forma conocida sigue diciendo algo', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(500, undefined));

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(error.estado).toBe(500);
    // UX-R2.4 — antes se exigia que el detalle CONTUVIERA «500». El codigo
    // sigue disponible en `estado`, que es por donde lo lee quien programa; lo
    // que ve el jugador es una frase que le dice que hacer. «500» no le dice
    // a nadie si esperar, reintentar o irse.
    expect(error.detalle).toMatch(/no responde ahora mismo/i);
    expect(error.detalle).not.toMatch(/\b500\b/);
  });
});

/**
 * Ejecucion estatica contra ejecucion integrada.
 *
 * Al abrir las vistas desde un servidor de ficheros no hay API detras. Antes
 * eso se veia como «No se pudo crear la sala · El servicio respondio 405», que
 * culpa al servicio de salas de algo que ni siquiera esta levantado.
 */
/**
 * UX-R3.4 — el diagnostico y el mensaje son dos cosas distintas.
 *
 * Cuando no hay API detras, el cliente lo detecta y lo dice. Hasta ahora lo
 * decia EN LA PANTALLA: «Estas viendo la vista servida como HTML estatico:
 * nadie atiende /api/v1/salas. Levanta el servicio de salas, o declara en la
 * pagina <meta name="nexus-api-base">…». Es el mensaje correcto y va a la
 * persona equivocada: a quien programa le dice exactamente que hacer; a un
 * jugador le enseña una etiqueta HTML y le pide que levante un servicio.
 *
 * La deteccion no cambia —sigue siendo util, y es la que evita confundir un
 * servidor estatico con un fallo del servicio—; lo que cambia es a donde va
 * cada mensaje.
 */
describe('sin backend detras', () => {
  let avisos;

  beforeEach(() => {
    avisos = jest.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    avisos.mockRestore();
  });

  test('un 405 de servidor estatico se diagnostica en consola, no en pantalla', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(405, undefined, 'text/html'));

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    // Lo que lee el jugador: un servicio que no esta, como cualquier otro.
    expect(error.titulo).toBe('Las batallas no están disponibles');
    expect(error.detalle).not.toMatch(/<meta|html estatico|levanta el servicio/i);
    expect(error.detalle).not.toMatch(/respondio 405/i);

    // Lo que lee quien puede arreglarlo.
    const diagnostico = avisos.mock.calls.map((c) => c.join(' ')).join(' ');
    expect(diagnostico).toContain('/api/v1/salas');
    expect(diagnostico).toMatch(/nexus-api-base/);
  });

  test('el 405 real de http-server llega como text/plain y tambien se reconoce', async () => {
    // `http-server` responde 405 con `content-type: text/plain` a un POST.
    // Reducir la deteccion a HTML, como sugirio Copilot en #271, perderia
    // justo este caso, que es el que motivo la distincion.
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(405, undefined, 'text/plain'));

    await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(avisos).toHaveBeenCalled();
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

  test('el diagnostico nombra la URL real de la petición cuando fetch la trae', async () => {
    const conUrl = {
      ...respuesta(404, undefined, 'text/html'),
      url: 'http://127.0.0.1:4399/api/v1/salas/s1/verificacion-heroe',
    };
    const fetchImpl = jest.fn().mockResolvedValue(conUrl);

    await verificarHeroe('s1', { fetchImpl }).catch((e) => e);

    const diagnostico = avisos.mock.calls.map((c) => c.join(' ')).join(' ');
    expect(diagnostico).toContain('/api/v1/salas/s1/verificacion-heroe');
  });

  test('un GET que devuelve la página HTML del servidor estatico también se detecta', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValue(respuesta(404, undefined, 'text/html; charset=utf-8'));

    const error = await listarSalas({}, { fetchImpl }).catch((e) => e);

    expect(avisos).toHaveBeenCalled();
    expect(error.titulo).toBe('Las batallas no están disponibles');
  });

  test('un fallo real del servicio sigue siendo un fallo del servicio', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValue(respuesta(503, undefined, 'application/problem+json'));

    const error = await listarSalas({}, { fetchImpl }).catch((e) => e);

    expect(error.titulo).not.toMatch(/no hay ninguna api/i);
    // El codigo sigue ahi para quien programa; el detalle es para quien juega.
    expect(error.estado).toBe(503);
    expect(error.detalle).toMatch(/no responde ahora mismo/i);
  });
});

describe('baseDeApi', () => {
  afterEach(() => {
    document.head.innerHTML = '';
  });

  test('sin declararla, es el mismo origen: nada de localhost escrito en el código', () => {
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

// ===========================================================================
// HU-SAL-004 · RF-JUE-017 — arranque del combate y estado de la partida
// ===========================================================================

describe('iniciarPartida', () => {
  test('llama a la subruta de partida sin cuerpo: el anfitrion sale del token', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(201, { id: 'p1' }));

    await iniciarPartida('abc', { fetchImpl });

    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/salas/abc/partida');
    expect(opciones.method).toBe('POST');
    expect(opciones.body).toBeUndefined();
  });

  test('devuelve la partida iniciada tal como la manda el servicio', async () => {
    const partida = { id: 'p1', estado: 'EN_CURSO', participantes: [] };
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(201, partida));

    await expect(iniciarPartida('abc', { fetchImpl })).resolves.toEqual(partida);
  });

  test('un 403 de quien no es el anfitrion llega con su tipo', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(403, {
        type: 'https://nexusbattles.local/errores/no-es-el-anfitrion',
        title: 'No eres el anfitrion de esta sala',
        detail: 'Solo quien creo la sala puede iniciarla.',
        status: 403,
      }),
    );

    const error = await iniciarPartida('abc', { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.tipo).toBe('https://nexusbattles.local/errores/no-es-el-anfitrion');
    expect(error.estado).toBe(403);
  });

  test('un 409 de sala que todavía no puede empezar llega interpretado', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(409, {
        type: 'https://nexusbattles.local/errores/ingreso-no-permitido',
        title: 'No puedes entrar a esta sala',
        detail: 'La sala necesita al menos un rival o el héroe de la IA.',
        status: 409,
      }),
    );

    const error = await iniciarPartida('abc', { fetchImpl }).catch((e) => e);

    expect(error.estado).toBe(409);
    expect(error.detalle).toMatch(/rival/i);
  });

  test('el identificador de la sala se codifica: no se pega crudo en la URL', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(201, {}));

    await iniciarPartida('a b/c', { fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/salas/a%20b%2Fc/partida');
  });
});

describe('obtenerPartida', () => {
  test('cuelga de /api/v1/partidas, no de /api/v1/salas', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { id: 'p1' }));

    await obtenerPartida('p1', { fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/partidas/p1');
  });

  test('es una lectura: va sin opciones de petición', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { id: 'p1' }));

    await obtenerPartida('p1', { fetchImpl });

    expect(fetchImpl.mock.calls[0][1]).toBeUndefined();
  });

  test('una partida que no existe llega como 404 con su tipo', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(404, {
        type: 'https://nexusbattles.local/errores/partida-no-encontrada',
        title: 'La partida no existe',
        detail: 'No hay ninguna partida con ese identificador.',
        status: 404,
      }),
    );

    const error = await obtenerPartida('p1', { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.tipo).toBe('https://nexusbattles.local/errores/partida-no-encontrada');
    expect(error.estado).toBe(404);
  });
});

// ===========================================================================
// HU-SAL-006 — salir y cancelar antes de empezar
// ===========================================================================

describe('obtenerSala', () => {
  test('pide la sala por su identificador y la devuelve tal cual', async () => {
    const sala = { id: 's1', idAnfitrion: 'a', ocupacion: 2, maximoParticipantes: 4 };
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, sala));

    expect(await obtenerSala('s1', { fetchImpl })).toEqual(sala);
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/salas/s1');
  });

  test('un 404 llega interpretado', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValue(respuesta(404, { type: 'x', title: 'No existe', status: 404 }));

    const error = await obtenerSala('s1', { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.estado).toBe(404);
  });
});

describe('abandonarSala', () => {
  test('hace DELETE a /participantes sin cuerpo: quien sale es quien firma el token', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(204, undefined));

    await abandonarSala('s1', { fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/salas/s1/participantes');
    expect(fetchImpl.mock.calls[0][1]).toEqual({ method: 'DELETE' });
  });

  test('el 409 del anfitrion (su camino es cancelar) llega interpretado', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(409, {
        type: 'https://nexusbattles.local/errores/salida-no-permitida',
        title: 'No puedes salir de esta sala',
        detail: 'El anfitrion no abandona su sala: la cancela.',
        status: 409,
      }),
    );

    const error = await abandonarSala('s1', { fetchImpl }).catch((e) => e);

    expect(error.estado).toBe(409);
    expect(error.detalle).toContain('la cancela');
  });
});

describe('cancelarSala', () => {
  test('hace DELETE a la sala', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(204, undefined));

    await cancelarSala('s1', { fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/salas/s1');
    expect(fetchImpl.mock.calls[0][1]).toEqual({ method: 'DELETE' });
  });

  test('quien no es el anfitrion recibe el 403 interpretado', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(403, {
        type: 'https://nexusbattles.local/errores/no-es-el-anfitrion',
        title: 'Solo el anfitrion puede hacer esto',
        status: 403,
      }),
    );

    const error = await cancelarSala('s1', { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.estado).toBe(403);
  });
});

/**
 * UX-R3.4 — un 404 no siempre significa «esa sala ya no existe».
 *
 * El detalle del 404 estaba escrito para UNA sala y se usaba para todo. Sin
 * backend, quien abria el listado de batallas leia, en la pantalla del
 * listado, que volviera al listado para ver una sala que nadie habia abierto.
 * Un mensaje que se contradice con la pantalla en la que está enseña a no
 * leer los mensajes.
 */
describe('§17 — el 404 dice lo que falta, no lo que se supone', () => {
  test('pidiendo el listado, no habla de una sala que no se pidio', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(404, undefined));

    const error = await listarSalas({}, { fetchImpl }).catch((e) => e);

    expect(error.detalle).not.toMatch(/esa sala|vuelve al listado/i);
    expect(error.detalle).toMatch(/no está disponible/i);
  });

  test('pidiendo una sala concreta, sigue diciendo que ya no existe', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(404, undefined));

    const error = await obtenerSala('s-1', { fetchImpl }).catch((e) => e);

    expect(error.detalle).toMatch(/esa sala ya no existe/i);
  });

  test('ningun mensaje lleva el codigo a la pantalla', async () => {
    for (const estado of [401, 403, 404, 409, 500]) {
      const fetchImpl = jest.fn().mockResolvedValue(respuesta(estado, undefined));
      const error = await listarSalas({}, { fetchImpl }).catch((e) => e);
      expect(`${error.titulo} ${error.detalle}`).not.toMatch(/\b(401|403|404|409|500|502)\b/);
    }
  });
});

/**
 * FI-R6 — reconocer el rechazo de RF-JUE-003.
 *
 * `PuertaDeHeroe.comprobar` corre dentro de `IngresarASala` antes que nada y
 * lanza `HeroeNoDisponible` con 422. Es el unico rechazo del ingreso que se
 * arregla yendo al inventario, y hasta FI-R6 el listado lo pintaba como un
 * aviso rojo cualquiera.
 */
describe('FI-R6 - esHeroeNoDisponible', () => {
  const con = (status, type) => new ErrorDeApi({ type, title: 't', status }, status);

  test('reconoce los dos tipos de RF-JUE-003', () => {
    expect(
      esHeroeNoDisponible(con(422, 'https://nexusbattles.local/errores/heroe-no-equipado')),
    ).toBe(true);
    expect(esHeroeNoDisponible(con(422, 'https://nexusbattles.local/errores/heroe-ocupado'))).toBe(
      true,
    );
  });

  test('no confunde otros rechazos con un problema de heroe', () => {
    expect(esHeroeNoDisponible(con(422, 'urn:otra-cosa'))).toBe(false);
    expect(esHeroeNoDisponible(con(409, 'urn:llena'))).toBe(false);
    expect(esHeroeNoDisponible(con(403, 'https://nexusbattles.local/errores/sala-privada'))).toBe(
      false,
    );
    expect(esHeroeNoDisponible(null)).toBe(false);
  });

  test('los dos reconocedores no se pisan', () => {
    const privada = con(403, 'https://nexusbattles.local/errores/sala-privada');
    const heroe = con(422, 'https://nexusbattles.local/errores/heroe-no-equipado');
    expect(esSalaPrivada(privada)).toBe(true);
    expect(esHeroeNoDisponible(privada)).toBe(false);
    expect(esSalaPrivada(heroe)).toBe(false);
    expect(esHeroeNoDisponible(heroe)).toBe(true);
  });
});
