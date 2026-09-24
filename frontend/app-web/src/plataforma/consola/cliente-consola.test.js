/**
 * El cliente de la consola: lo que se prueba es la honestidad.
 *
 * Cada codigo HTTP tiene que acabar en un desenlace distinto y ninguno puede
 * acabar en un numero inventado. La prueba que mas importa es la ultima de
 * `estaVacio`: un cero que el servicio dijo de verdad NO es un hueco.
 */

import { jest } from '@jest/globals';

import {
  RESULTADO,
  consultar,
  consultarVarios,
  estaVacio,
  filasDe,
  totalDe,
} from './cliente-consola.js';

const respuesta = (estado, cuerpo = null) => ({
  ok: estado >= 200 && estado < 300,
  status: estado,
  text: async () => (cuerpo === null ? '' : JSON.stringify(cuerpo)),
});

const buscarQueDevuelve = (estado, cuerpo) => jest.fn(async () => respuesta(estado, cuerpo));

describe('consultar: cada codigo tiene su desenlace', () => {
  test('200 con datos es DATOS', async () => {
    const d = await consultar('/torneos', { buscar: buscarQueDevuelve(200, [{ id: 1 }]) });

    expect(d.resultado).toBe(RESULTADO.DATOS);
    expect(d.datos).toEqual([{ id: 1 }]);
    expect(d.estado).toBe(200);
  });

  test('200 con lista vacia es VACIO, no un fallo', async () => {
    const d = await consultar('/torneos', { buscar: buscarQueDevuelve(200, []) });

    expect(d.resultado).toBe(RESULTADO.VACIO);
    expect(d.motivo).toMatch(/no hay registros/i);
  });

  test('401 es falta de permiso, no servicio caido', async () => {
    const d = await consultar('/admin/jugadores', { buscar: buscarQueDevuelve(401) });

    expect(d.resultado).toBe(RESULTADO.SIN_PERMISO);
    expect(d.datos).toBeNull();
  });

  test('403 tambien es falta de permiso, con otro motivo', async () => {
    const d = await consultar('/admin/jugadores', { buscar: buscarQueDevuelve(403) });

    expect(d.resultado).toBe(RESULTADO.SIN_PERMISO);
    expect(d.motivo).toMatch(/rol/i);
  });

  test('404 es modulo no implementado: el servicio vive, la ruta no', async () => {
    const d = await consultar('/misiones', { buscar: buscarQueDevuelve(404) });

    expect(d.resultado).toBe(RESULTADO.NO_IMPLEMENTADO);
  });

  test.each([500, 502, 503, 504])('%i es servicio degradado', async (estado) => {
    const d = await consultar('/subastas', { buscar: buscarQueDevuelve(estado) });

    expect(d.resultado).toBe(RESULTADO.SERVICIO_DEGRADADO);
  });

  test('un codigo raro se admite como no disponible y se dice cual era', async () => {
    const d = await consultar('/algo', { buscar: buscarQueDevuelve(418) });

    expect(d.resultado).toBe(RESULTADO.NO_DISPONIBLE);
    expect(d.detalle).toBe('HTTP 418');
  });

  test('si fetch revienta, la consulta no lanza: devuelve degradado', async () => {
    const buscar = jest.fn(async () => {
      throw new Error('ECONNREFUSED');
    });

    const d = await consultar('/subastas', { buscar });

    expect(d.resultado).toBe(RESULTADO.SERVICIO_DEGRADADO);
    expect(d.estado).toBeNull();
  });

  test('un 200 que no trae JSON no se confunde con datos', async () => {
    const buscar = jest.fn(async () => ({ ok: true, status: 200, text: async () => '<html>' }));

    const d = await consultar('/parametros', { buscar });

    expect(d.resultado).toBe(RESULTADO.VACIO);
  });
});

describe('consultarVarios', () => {
  test('un servicio caido no arrastra a los demas', async () => {
    const buscar = jest.fn(async (url) =>
      url.includes('subastas') ? respuesta(502) : respuesta(200, [{ id: 1 }]),
    );

    const d = await consultarVarios({ torneos: '/torneos', subastas: '/subastas' }, { buscar });

    expect(d.torneos.resultado).toBe(RESULTADO.DATOS);
    expect(d.subastas.resultado).toBe(RESULTADO.SERVICIO_DEGRADADO);
  });
});

describe('estaVacio: la frontera entre "no hay" y "no se pudo preguntar"', () => {
  test('nulo y sin definir estan vacios', () => {
    expect(estaVacio(null)).toBe(true);
    expect(estaVacio(undefined)).toBe(true);
  });

  test('lista vacia y pagina sin contenido estan vacias', () => {
    expect(estaVacio([])).toBe(true);
    expect(estaVacio({ contenido: [], total: 0 })).toBe(true);
    expect(estaVacio({ content: [] })).toBe(true);
  });

  test('cero y falso NO estan vacios: son respuestas legitimas', () => {
    expect(estaVacio(0)).toBe(false);
    expect(estaVacio(false)).toBe(false);
    expect(estaVacio({ total: 0 })).toBe(false);
  });
});

describe('totalDe: un guion no es un cero', () => {
  test('lee el total de una pagina propia y de una de Spring', () => {
    expect(totalDe({ contenido: [1], total: 42 })).toBe(42);
    expect(totalDe({ content: [1], totalElements: 7 })).toBe(7);
  });

  test('cuenta los elementos de una lista suelta', () => {
    expect(totalDe([1, 2, 3])).toBe(3);
  });

  test('devuelve null cuando la respuesta no dice cuantos hay', () => {
    expect(totalDe(null)).toBeNull();
    expect(totalDe({ algo: 'otro' })).toBeNull();
  });
});

describe('filasDe', () => {
  test('acepta lista, pagina propia y pagina de Spring', () => {
    expect(filasDe([1, 2])).toEqual([1, 2]);
    expect(filasDe({ contenido: [3] })).toEqual([3]);
    expect(filasDe({ content: [4] })).toEqual([4]);
  });

  test('lo que no es ninguna de las tres da lista vacia, no revienta', () => {
    expect(filasDe(null)).toEqual([]);
    expect(filasDe(7)).toEqual([]);
  });
});
