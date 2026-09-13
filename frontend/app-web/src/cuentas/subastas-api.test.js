/**
 * Cliente de la API de subastas (HU-SUB-004).
 *
 * Lo que se fija aqui es el contrato visto desde el consumidor: que salga la
 * cabecera de idempotencia, que el monto viaje como cadena, y que el mensaje
 * que ve el jugador salga del campo 'motivo' y no del texto libre del servidor.
 */

import { jest } from '@jest/globals';
import {
  crearApiSubastas,
  aVistaDeSubasta,
  mensajePara,
  ErrorDeSubastas
} from './subastas-api.js';

function respuesta({ ok = true, status = 200, cuerpo = {} } = {}) {
  return {
    ok,
    status,
    json: async () => cuerpo
  };
}

function apiCon(fetchFalso, { token = 'jwt-de-prueba' } = {}) {
  return crearApiSubastas({
    urlBase: 'http://servidor/api/v1',
    fetch: fetchFalso,
    leerToken: () => token,
    leerApodo: () => 'andres_nv'
  });
}

describe('peticiones que mueven creditos', () => {
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
    // Configurar todavia no mueve creditos: la reserva la hace el motor al
    // emitir, y esa si lleva su propia clave.
    expect(capturado.opciones.headers['Idempotency-Key']).toBeUndefined();
  });

  test('desactivar la puja automatica acepta un 204 sin cuerpo', async () => {
    const falso = jest.fn(async () => ({
      ok: true,
      status: 204,
      json: async () => {
        throw new Error('un 204 no trae cuerpo');
      }
    }));

    await expect(apiCon(falso).desactivarAutomatica('sub-1')).resolves.toBeNull();
  });
});

describe('traduccion de errores', () => {
  test('el mensaje sale del motivo, no del texto tecnico del servidor', async () => {
    const falso = jest.fn(async () => respuesta({
      ok: false,
      status: 409,
      cuerpo: {
        motivo: 'OFERTA_INSUFICIENTE',
        detail: 'La puja de 105 no supera la oferta vigente mas el incremento minimo (110)'
      }
    }));

    await expect(apiCon(falso).pujar('sub-1', 105)).rejects.toMatchObject({
      estado: 409,
      motivo: 'OFERTA_INSUFICIENTE',
      message: 'Alguien se te adelanto: la oferta ya subio. Revisa el nuevo minimo.'
    });
  });

  test('un 401 pide volver a iniciar sesion', async () => {
    const falso = jest.fn(async () => respuesta({ ok: false, status: 401, cuerpo: {} }));

    await expect(apiCon(falso).pujar('sub-1', 110)).rejects.toMatchObject({ estado: 401 });
  });

  test('sin token ni siquiera sale la peticion', async () => {
    const falso = jest.fn();

    await expect(apiCon(falso, { token: null }).pujar('sub-1', 110))
      .rejects.toBeInstanceOf(ErrorDeSubastas);
    expect(falso).not.toHaveBeenCalled();
  });

  test('si el servidor no responde se avisa en vez de romper la pantalla', async () => {
    const falso = jest.fn(async () => {
      throw new TypeError('Failed to fetch');
    });

    await expect(apiCon(falso).pujar('sub-1', 110)).rejects.toMatchObject({ estado: 0 });
  });

  test('un motivo desconocido no deja al jugador sin mensaje', () => {
    expect(mensajePara('ALGO_QUE_NO_EXISTE_TODAVIA')).toBe('No se pudo completar la operacion.');
  });
});

describe('listado', () => {
  test('lee el campo contenido, que es el del contrato', async () => {
    const falso = jest.fn(async () => respuesta({
      cuerpo: {
        contenido: [{
          id: 'sub-1',
          nombreProducto: 'Hacha de Obsidiana',
          ofertaVigente: 1350,
          precioCompraInmediata: 2800,
          cantidadPujas: 3,
          rareza: 'EPICA',
          fechaFin: new Date(Date.now() + 60000).toISOString()
        }],
        pagina: 0
      }
    }));

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
      fechaFin: new Date(Date.now() - 60000).toISOString()
    });

    expect(vencida.segundosRestantes).toBe(0);
  });

  test('el historial llega vacio en vez de inventado', () => {
    // El contrato del listado no expone las pujas una por una. Rellenar el
    // historial con datos falsos seria mentirle al jugador sobre quien puja.
    expect(aVistaDeSubasta({ id: 'x', fechaFin: null }).historial).toEqual([]);
  });
});
