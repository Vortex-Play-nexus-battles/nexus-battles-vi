/**
 * UXC-5 — el cliente de la estrategia contra `heroes.yaml` 1.1.0.
 */

import { jest } from '@jest/globals';

import { NIVELES, validarEstrategia, vistaEnNivel } from './cliente-estrategias.js';

function respuesta(cuerpo, { ok = true, status = 200 } = {}) {
  return { ok, status, json: async () => cuerpo };
}

describe('validarEstrategia', () => {
  test('manda el prototipo, el nivel y las rotaciones tal como las pide el contrato', async () => {
    const fetchImpl = jest.fn(async () => respuesta({ valida: true }));

    await validarEstrategia(
      {
        heroe: ' Guerrero Armas ',
        nivel: 8,
        rotaciones: [{ pasos: ['Golpe de tormenta', 'Ataque básico'] }],
      },
      { fetchImpl },
    );

    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/estrategias/validacion');
    expect(opciones.method).toBe('POST');
    expect(opciones.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(opciones.body)).toEqual({
      heroe: 'Guerrero Armas',
      nivel: 8,
      rotaciones: [{ pasos: ['Golpe de tormenta', 'Ataque básico'] }],
    });
  });

  test('sin nivel ni rotaciones no inventa ninguno: el servicio supone el 1 y la de por defecto', async () => {
    const fetchImpl = jest.fn(async () => respuesta({ valida: true, porDefecto: true }));

    await validarEstrategia({ heroe: 'Chamán' }, { fetchImpl });

    expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual({ heroe: 'Chamán' });
  });

  test('un rechazo de la regla llega como veredicto, no como error', async () => {
    const veredicto = {
      valida: false,
      motivo:
        'La rotación 1 usa una habilidad que Guerrero Armas no posee en nivel 1: Golpe de tormenta.',
      habilidadesValidas: ['Embate sangriento', 'Ataque básico'],
    };
    const fetchImpl = jest.fn(async () => respuesta(veredicto));

    await expect(
      validarEstrategia(
        { heroe: 'Guerrero Armas', rotaciones: [{ pasos: ['Golpe de tormenta'] }] },
        { fetchImpl },
      ),
    ).resolves.toEqual(veredicto);
  });

  test('un 404 trae el mensaje apto para el jugador; un 500, no', async () => {
    const con404 = jest.fn(async () =>
      respuesta(
        { detail: 'El héroe solicitado no está disponible en el catálogo.' },
        { ok: false, status: 404 },
      ),
    );
    await expect(
      validarEstrategia({ heroe: 'Nigromante' }, { fetchImpl: con404 }),
    ).rejects.toMatchObject({
      status: 404,
      detalle: 'El héroe solicitado no está disponible en el catálogo.',
    });

    const con500 = jest.fn(async () =>
      respuesta({ detail: 'NullPointerException' }, { ok: false, status: 500 }),
    );
    const fallo = await validarEstrategia({ heroe: 'Chamán' }, { fetchImpl: con500 }).catch(
      (e) => e,
    );
    expect(fallo.status).toBe(500);
    expect(fallo.detalle).toBeUndefined();
  });

  test('sin prototipo no se pregunta nada', async () => {
    const fetchImpl = jest.fn();
    await expect(validarEstrategia({ heroe: '  ' }, { fetchImpl })).rejects.toBeInstanceOf(
      TypeError,
    );
    expect(fetchImpl).not.toHaveBeenCalled();
  });
});

describe('vistaEnNivel', () => {
  test('pide la vista del prototipo en su nivel, con el nombre escapado', async () => {
    const fetchImpl = jest.fn(async () => respuesta({ nombre: 'Pícaro Veneno', nivel: 4 }));

    await vistaEnNivel('Pícaro Veneno', 4, { fetchImpl });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/heroes/P%C3%ADcaro%20Veneno/niveles/4');
  });

  test('solo los niveles del 1 al 8', async () => {
    expect(NIVELES).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
    const fetchImpl = jest.fn();
    await expect(vistaEnNivel('Chamán', 9, { fetchImpl })).rejects.toBeInstanceOf(RangeError);
    expect(fetchImpl).not.toHaveBeenCalled();
  });
});
