/**
 * Cliente de la API de subastas (HU-SUB-004).
 *
 * Lo que se fija aqui es el contrato visto desde el consumidor: que salga la
 * cabecera de idempotencia, que el monto viaje como cadena, y que el mensaje
 * que ve el jugador salga del campo 'motivo' y no del texto libre del servidor.
 */

import { jest } from '@jest/globals';
import { crearApiSubastas, aVistaDeSubasta, mensajePara, ErrorDeSubastas } from './pujas-api.js';

function respuesta({ ok = true, status = 200, cuerpo = {} } = {}) {
  return {
    ok,
    status,
    json: async () => cuerpo,
  };
}

function apiCon(fetchFalso, { token = 'jwt-de-prueba' } = {}) {
  return crearApiSubastas({
    urlBase: 'http://servidor/api/v1',
    fetch: fetchFalso,
    leerToken: () => token,
    leerApodo: () => 'andres_nv',
  });
}

describe('peticiones que mueven créditos', () => {
  test('pujar manda el monto como cadena y una clave de idempotencia', async () => {
    const peticiones = [];
    const falso = jest.fn(async (url, opciones) => {
      peticiones.push({ url, opciones });
      return respuesta({ status: 201, cuerpo: { id: 'p1', estado: 'ACTIVA' } });
    });

    await apiCon(falso).pujar('sub-1', 110);

    const { url, opciones } = peticiones[0];
    expect(url).toBe('http://servidor/api/v1/subastas/sub-1/pujas');
    expect(opciones.method).toBe('POST');
    // Cadena, no numero: un decimal en JSON pasa por el double de JavaScript.
    expect(JSON.parse(opciones.body)).toEqual({ monto: '110' });
    expect(opciones.headers.Authorization).toBe('Bearer jwt-de-prueba');
    expect(opciones.headers['Idempotency-Key']).toBeTruthy();
  });

  test('dos pujas seguidas no reutilizan la misma clave de idempotencia', async () => {
    const claves = [];
    const falso = jest.fn(async (_url, opciones) => {
      claves.push(opciones.headers['Idempotency-Key']);
      return respuesta({ status: 201 });
    });
    const api = apiCon(falso);

    await api.pujar('sub-1', 110);
    await api.pujar('sub-1', 120);

    // Reutilizarla haria que ms-finanzas tomara la segunda puja por un
    // reintento de la primera y no reservara nada.
    expect(claves[0]).not.toBe(claves[1]);
    expect(claves[0].length).toBeGreaterThanOrEqual(8);
  });

  test('la compra inmediata manda la confirmacion explicita', async () => {
    let cuerpoEnviado = null;
    const falso = jest.fn(async (_url, opciones) => {
      cuerpoEnviado = JSON.parse(opciones.body);
      return respuesta({ status: 201 });
    });

    await apiCon(falso).comprarAhora('sub-1');

    expect(cuerpoEnviado).toEqual({ confirmado: true });
  });

  test('configurar la puja automatica es un PUT sin clave de idempotencia', async () => {
    let capturado = null;
    const falso = jest.fn(async (url, opciones) => {
      capturado = { url, opciones };
      return respuesta({ cuerpo: { id: 'a1', activa: true, limite: '400' } });
    });

    await apiCon(falso).configurarAutomatica('sub-1', 400);

    expect(capturado.opciones.method).toBe('PUT');
    expect(JSON.parse(capturado.opciones.body)).toEqual({ limite: '400' });
    // Configurar todavia no mueve créditos: la reserva la hace el motor al
    // emitir, y esa si lleva su propia clave.
    expect(capturado.opciones.headers['Idempotency-Key']).toBeUndefined();
  });

  test('desactivar la puja automatica acepta un 204 sin cuerpo', async () => {
    const falso = jest.fn(async () => ({
      ok: true,
      status: 204,
      json: async () => {
        throw new Error('un 204 no trae cuerpo');
      },
    }));

    await expect(apiCon(falso).desactivarAutomatica('sub-1')).resolves.toBeNull();
  });
});

describe('reintento de una peticion que no llego a tener respuesta', () => {
  test('si la red falla, se reintenta UNA vez con la MISMA clave', async () => {
    const claves = [];
    const falso = jest.fn(async (url, opciones) => {
      claves.push(opciones.headers['Idempotency-Key']);
      if (claves.length === 1) {
        throw new TypeError('Failed to fetch');
      }
      return respuesta({ status: 201, cuerpo: { id: 'p1', estado: 'ACTIVA' } });
    });

    const puja = await apiCon(falso).pujar('s1', '110');

    expect(falso).toHaveBeenCalledTimes(2);
    // Lo esencial: la misma clave. Con una clave nueva el reintento seria una
    // puja distinta y el jugador acabaria pujando dos veces.
    expect(claves[0]).toBe(claves[1]);
    expect(puja.id).toBe('p1');
  });

  test('si la red falla dos veces se avisa, no se reintenta sin fin', async () => {
    const falso = jest.fn(async () => {
      throw new TypeError('Failed to fetch');
    });

    await expect(apiCon(falso).pujar('s1', '110')).rejects.toBeInstanceOf(ErrorDeSubastas);
    expect(falso).toHaveBeenCalledTimes(2);
  });

  test('un error del servidor NO se reintenta: ya respondió', async () => {
    const falso = jest.fn(async () =>
      respuesta({
        ok: false,
        status: 409,
        cuerpo: { motivo: 'OFERTA_INSUFICIENTE' },
      }),
    );

    await expect(apiCon(falso).pujar('s1', '110')).rejects.toBeInstanceOf(ErrorDeSubastas);
    expect(falso).toHaveBeenCalledTimes(1);
  });

  test('una peticion sin clave de idempotencia no se reintenta a ciegas', async () => {
    const falso = jest.fn(async () => {
      throw new TypeError('Failed to fetch');
    });

    await expect(apiCon(falso).miResumen()).rejects.toBeInstanceOf(ErrorDeSubastas);
    expect(falso).toHaveBeenCalledTimes(1);
  });
});

describe('traduccion de errores', () => {
  test('el mensaje sale del motivo, no del texto técnico del servidor', async () => {
    const falso = jest.fn(async () =>
      respuesta({
        ok: false,
        status: 409,
        cuerpo: {
          motivo: 'OFERTA_INSUFICIENTE',
          detail: 'La puja de 105 no supera la oferta vigente mas el incremento mínimo (110)',
        },
      }),
    );

    await expect(apiCon(falso).pujar('sub-1', 105)).rejects.toMatchObject({
      estado: 409,
      motivo: 'OFERTA_INSUFICIENTE',
      message: 'Alguien se te adelantó: la oferta ya subió. Revisa el nuevo mínimo.',
    });
  });

  test('un 401 pide volver a iniciar sesión', async () => {
    const falso = jest.fn(async () => respuesta({ ok: false, status: 401, cuerpo: {} }));

    await expect(apiCon(falso).pujar('sub-1', 110)).rejects.toMatchObject({ estado: 401 });
  });

  test('sin token ni siquiera sale la peticion', async () => {
    const falso = jest.fn();

    await expect(apiCon(falso, { token: null }).pujar('sub-1', 110)).rejects.toBeInstanceOf(
      ErrorDeSubastas,
    );
    expect(falso).not.toHaveBeenCalled();
  });

  test('si el servidor no responde se avisa en vez de romper la pantalla', async () => {
    const falso = jest.fn(async () => {
      throw new TypeError('Failed to fetch');
    });

    await expect(apiCon(falso).pujar('sub-1', 110)).rejects.toMatchObject({ estado: 0 });
  });

  test('un motivo desconocido no deja al jugador sin mensaje', () => {
    expect(mensajePara('ALGO_QUE_NO_EXISTE_TODAVIA')).toBe('No se pudo completar la operación.');
  });
});

describe('listado', () => {
  test('lee el campo contenido, que es el del contrato', async () => {
    const falso = jest.fn(async () =>
      respuesta({
        cuerpo: {
          contenido: [
            {
              id: 'sub-1',
              nombreProducto: 'Hacha de Obsidiana',
              ofertaVigente: 1350,
              precioCompraInmediata: 2800,
              cantidadPujas: 3,
              rareza: 'EPICA',
              fechaFin: new Date(Date.now() + 60000).toISOString(),
            },
          ],
          pagina: 0,
        },
      }),
    );

    const subastas = await apiCon(falso).listar();

    expect(subastas).toHaveLength(1);
    expect(subastas[0].nombre).toBe('Hacha de Obsidiana');
    expect(subastas[0].oferta).toBe(1350);
    expect(subastas[0].rivales).toBe(3);
  });

  test('una subasta sin compra inmediata no inventa un precio', () => {
    const vista = aVistaDeSubasta({ id: 'x', precioCompraInmediata: null, fechaFin: null });

    expect(vista.compraInmediata).toBe(0);
  });

  test('el tiempo restante sale de fechaFin y nunca es negativo', () => {
    const vencida = aVistaDeSubasta({
      id: 'x',
      fechaFin: new Date(Date.now() - 60000).toISOString(),
    });

    expect(vencida.segundosRestantes).toBe(0);
  });

  test('el historial llega vacio en vez de inventado', () => {
    // El contrato del listado no expone las pujas una por una. Rellenar el
    // historial con datos falsos seria mentirle al jugador sobre quien puja.
    expect(aVistaDeSubasta({ id: 'x', fechaFin: null }).historial).toEqual([]);
  });
});

// ---------------------------------------------------------------------- B8

describe('B8 — reglas, ficha, cancelación, seguimiento y pendientes', () => {
  function capturar(cuerpo = {}, status = 200) {
    const peticiones = [];
    const falso = jest.fn(async (url, opciones) => {
      peticiones.push({ url, opciones });
      return respuesta({ status, cuerpo });
    });
    return { falso, peticiones };
  }

  test('las reglas y la ficha son publicas: salen aunque no haya sesion', async () => {
    const { falso, peticiones } = capturar({ incrementoMinimoConfigurado: false });
    const api = apiCon(falso, { token: null });

    await api.reglas();
    await api.ficha('sub-1');

    expect(peticiones[0].url).toBe('http://servidor/api/v1/subastas/reglas');
    expect(peticiones[1].url).toBe('http://servidor/api/v1/subastas/sub-1');
    expect(peticiones[0].opciones.headers.Authorization).toBeUndefined();
  });

  test('cancelar es un POST con sesion y sin cuerpo', async () => {
    const { falso, peticiones } = capturar({ estado: 'CANCELADA' });

    await apiCon(falso).cancelar('sub-1');

    expect(peticiones[0].url).toBe('http://servidor/api/v1/subastas/sub-1/cancelacion');
    expect(peticiones[0].opciones.method).toBe('POST');
    expect(peticiones[0].opciones.body).toBeUndefined();
    expect(peticiones[0].opciones.headers.Authorization).toBe('Bearer jwt-de-prueba');
  });

  test('seguir es PUT y dejar de seguir DELETE sobre el mismo recurso; los 204 no rompen', async () => {
    const { falso, peticiones } = capturar({}, 204);
    const api = apiCon(falso);

    await expect(api.seguir('sub-1')).resolves.toBeNull();
    await expect(api.dejarDeSeguir('sub-1')).resolves.toBeNull();

    expect(peticiones.map((p) => `${p.opciones.method} ${p.url}`)).toEqual([
      'PUT http://servidor/api/v1/subastas/sub-1/seguimiento',
      'DELETE http://servidor/api/v1/subastas/sub-1/seguimiento',
    ]);
  });

  test('los pendientes de recoger: listar, recoger uno y recoger todo', async () => {
    const { falso, peticiones } = capturar([]);
    const api = apiCon(falso);

    await api.pendientes();
    await api.recoger('sub-9');
    await api.recogerTodo();

    expect(peticiones.map((p) => `${p.opciones.method} ${p.url}`)).toEqual([
      'GET http://servidor/api/v1/mis-subastas/pendientes',
      'POST http://servidor/api/v1/mis-subastas/pendientes/sub-9/recogida',
      'POST http://servidor/api/v1/mis-subastas/pendientes/recogida',
    ]);
  });

  test('las acciones del panel exigen sesion', async () => {
    const falso = jest.fn();
    const api = apiCon(falso, { token: null });

    await expect(api.cancelar('sub-1')).rejects.toMatchObject({ estado: 401 });
    await expect(api.pendientes()).rejects.toMatchObject({ estado: 401 });
    expect(falso).not.toHaveBeenCalled();
  });

  test.each([
    ['CANCELACION_CON_PUJAS', /no se puede cancelar/],
    ['CANCELACION_FUERA_DE_PLAZO', /últimas 6 horas/],
    ['NO_ES_EL_VENDEDOR', /Solo quien publicó/],
    ['PENDIENTE_YA_RESUELTO', /7 días/],
    ['COMPRA_INMEDIATA_SUPERADA', /ya no está disponible/],
  ])('el motivo %s tiene su mensaje', (motivo, esperado) => {
    expect(mensajePara(motivo)).toMatch(esperado);
  });

  test('el resumen del listado trae el estado y deja lo que no sabe en null', () => {
    const vista = aVistaDeSubasta({
      id: 'sub-1',
      estado: 'ACTIVA',
      ofertaVigente: '100',
      precioInicial: '100',
      cantidadPujas: 0,
    });

    expect(vista.estado).toBe('ACTIVA');
    expect(vista.precioInicial).toBe(100);
    expect(vista.rivales).toBe(0);
    expect(vista.pujaMinimaSiguiente).toBeNull();
    expect(vista.incrementoMinimo).toBeNull();
    expect(vista.compraInmediataDisponible).toBeNull();
    expect(vista.siguiendo).toBe(false);
  });
});
