/**
 * PLAYER-07b (punto 27) — el nombre de un producto tal como lo lee el jugador.
 *
 * El criterio que decide qué es un nombre y qué es un identificador vive en un
 * solo sitio y lo usan la vitrina del mercado y la pantalla de publicar. Si
 * cambia, cambia aquí y estas pruebas lo dicen.
 */
import { jest } from '@jest/globals';

import {
  SIN_NOMBRE,
  crearResolutorDeNombres,
  nombreLegible,
  pareceIdentificador,
} from './nombre-de-producto.js';

const UUID = '7c9e6679-7425-40de-944b-e07fc1f90ae7';

describe('qué es un identificador y qué un nombre', () => {
  test.each([
    ['vacío', ''],
    ['solo espacios', '   '],
    ['null', null],
    ['un número', 42],
    ['un UUID', UUID],
    ['un UUID en mayúsculas', UUID.toUpperCase()],
    ['un texto con un UUID dentro', `Espada de luz · ARMA · ${UUID}`],
    ['una tira hexadecimal larga', '3f2a9c0d7e1b4a5f9c2d'],
  ])('%s no es un nombre', (_caso, texto) => {
    expect(pareceIdentificador(texto)).toBe(true);
  });

  test.each(['Espada de una mano', 'Casco de acero templado', 'Mi hacha', 'Ítem de prueba 7'])(
    '«%s» es un nombre',
    (texto) => {
      expect(pareceIdentificador(texto)).toBe(false);
    },
  );

  test('un texto igual a uno de los ids del objeto tampoco es un nombre', () => {
    expect(pareceIdentificador('p-arma-e2e', ['unidad-1', 'p-arma-e2e'])).toBe(true);
    expect(pareceIdentificador('Hacha', ['unidad-1', 'p-arma-e2e'])).toBe(false);
  });

  test('nombreLegible da el nombre limpio o el respaldo, nunca el código', () => {
    expect(nombreLegible('  Espada de una mano  ')).toBe('Espada de una mano');
    expect(nombreLegible(UUID)).toBe(SIN_NOMBRE);
    expect(nombreLegible(undefined)).toBe(SIN_NOMBRE);
    expect(nombreLegible(UUID, { respaldo: '' })).toBe('');
    // El mismo respaldo que ya usa la sala de pujas para un nombre que falta.
    expect(SIN_NOMBRE).toBe('Objeto sin nombre');
  });
});

describe('los nombres del catálogo', () => {
  test('un nombre del catálogo se pide una sola vez y se recuerda', async () => {
    const consultar = jest.fn(async (id) => ({ id, nombre: 'Espada de una mano' }));
    const nombres = crearResolutorDeNombres({ consultar });

    const [a, b] = await Promise.all([nombres.resolver('p-1'), nombres.resolver('p-1')]);

    expect(consultar).toHaveBeenCalledTimes(1);
    expect(a).toBe(b);
    expect(nombres.leer('p-1')).toMatchObject({ estado: 'ok', nombre: 'Espada de una mano' });
    expect(nombres.leer('p-2')).toBeNull();
  });

  test('si el catálogo falla, queda «error» y el motivo no se guarda', async () => {
    const nombres = crearResolutorDeNombres({
      consultar: jest.fn(async () => {
        throw new Error(`El catalogo de productos respondio 503 al pedir ${UUID}`);
      }),
    });

    const resultado = await nombres.resolver(UUID);

    expect(resultado.estado).toBe('error');
    expect(resultado.nombre).toBeNull();
    expect(JSON.stringify(resultado)).not.toContain('503');
  });

  test('si responde sin un nombre legible, queda «sin-nombre»', async () => {
    const nombres = crearResolutorDeNombres({
      consultar: jest.fn(async (id) => ({ id, nombre: id })),
    });

    expect((await nombres.resolver(UUID)).estado).toBe('sin-nombre');
    expect((await nombres.resolver('')).estado).toBe('sin-nombre');
  });

  test('«olvidarFallidos» deja volver a pedir solo los que fallaron', async () => {
    let caido = true;
    const consultar = jest.fn(async (id) => {
      if (caido && id === 'p-caido') {
        throw new Error('sin red');
      }
      return { id, nombre: `Nombre de ${id}` };
    });
    const nombres = crearResolutorDeNombres({ consultar });
    await nombres.resolver('p-caido');
    await nombres.resolver('p-bien');

    caido = false;
    nombres.olvidarFallidos();
    await nombres.resolver('p-caido');
    await nombres.resolver('p-bien');

    expect(consultar.mock.calls.map(([id]) => id)).toEqual(['p-caido', 'p-bien', 'p-caido']);
    expect(nombres.leer('p-caido')).toMatchObject({ estado: 'ok', nombre: 'Nombre de p-caido' });
  });
});
