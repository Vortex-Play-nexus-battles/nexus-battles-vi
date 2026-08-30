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

import { crearSala, ErrorDeApi } from './cliente-salas.js';

const PARAMETROS = {
  nombre: 'Duelo en el Nexo',
  maximoParticipantes: 4,
  modalidad: 'HASTA_SEIS',
  recompensaCreditos: 0,
  incluirHeroeIA: false,
  privada: false,
  tamanoEquipo: null,
};

function respuesta(estado, cuerpo) {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    json: async () => {
      if (cuerpo === undefined) {
        throw new SyntaxError('Unexpected end of JSON input');
      }
      return cuerpo;
    },
  };
}

describe('crearSala', () => {
  test('envia el cuerpo al contrato y devuelve la sala creada', async () => {
    const sala = { id: 'abc', nombre: 'Duelo en el Nexo', estado: 'ABIERTA' };
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
          { campo: 'nombre', mensaje: 'Muy corto.' },
          { campo: 'recompensaCreditos', mensaje: 'No puede ser negativa.' },
        ],
      }),
    );

    const error = await crearSala(PARAMETROS, { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.esDeFormulario).toBe(true);
    expect(error.errores).toHaveLength(2);
    expect(error.errores[0].campo).toBe('nombre');
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
