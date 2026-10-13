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
  descargar,
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

/**
 * HU-USR-008 — un 400 o un 422 con problem details explican por qué: el panel
 * enseña ese motivo («La fecha «desde» no es válida…») en vez de «el servicio
 * respondió algo que la consola no sabe interpretar».
 */
describe('consultar: un rechazo que se explica', () => {
  test.each([400, 422])('%i con problem details usa su detail como motivo', async (estado) => {
    const d = await consultar('/admin/jugadores/indicadores?desde=x', {
      buscar: buscarQueDevuelve(estado, {
        type: 'https://nexusbattles.upb.edu.co/errors/datos-invalidos',
        status: estado,
        detail: 'La fecha «desde» no es válida: escríbela como aaaa-mm-dd.',
      }),
    });

    expect(d.resultado).toBe(RESULTADO.NO_DISPONIBLE);
    expect(d.motivo).toBe('La fecha «desde» no es válida: escríbela como aaaa-mm-dd.');
    expect(d.estado).toBe(estado);
  });

  test('un 400 sin cuerpo legible conserva el motivo genérico', async () => {
    const d = await consultar('/algo', { buscar: buscarQueDevuelve(400) });

    expect(d.resultado).toBe(RESULTADO.NO_DISPONIBLE);
    expect(d.motivo).toMatch(/no sabe interpretar/);
  });
});

describe('descargar: un archivo, o el motivo por el que no lo hay', () => {
  const conArchivo = (cabecera) =>
    jest.fn(async () => ({
      ok: true,
      status: 200,
      headers: { get: (nombre) => (nombre === 'Content-Disposition' ? cabecera : null) },
      blob: async () => 'contenido-del-csv',
    }));

  test('200: el contenido y el nombre que da el servidor', async () => {
    const buscar = conArchivo('attachment; filename="directorio-de-cuentas-20261005-1500.csv"');

    const d = await descargar('/admin/jugadores/exportacion?rol=JUGADOR', { buscar });

    expect(d.resultado).toBe(RESULTADO.DATOS);
    expect(d.contenido).toBe('contenido-del-csv');
    expect(d.nombreArchivo).toBe('directorio-de-cuentas-20261005-1500.csv');
    expect(buscar.mock.calls[0][0]).toBe('/api/v1/admin/jugadores/exportacion?rol=JUGADOR');
    expect(buscar.mock.calls[0][1].headers.Accept).toContain('text/csv');
  });

  test('sin Content-Disposition no se inventa un nombre', async () => {
    const d = await descargar('/x', { buscar: conArchivo(null) });

    expect(d.nombreArchivo).toBeNull();
  });

  test('422: el motivo del servidor, sin contenido', async () => {
    const d = await descargar('/admin/jugadores/exportacion', {
      buscar: buscarQueDevuelve(422, { status: 422, detail: 'Los filtros dejan 12000 cuentas.' }),
    });

    expect(d.resultado).toBe(RESULTADO.NO_DISPONIBLE);
    expect(d.motivo).toBe('Los filtros dejan 12000 cuentas.');
    expect(d.contenido).toBeNull();
  });

  test('403 es falta de permiso; la red caída es servicio degradado; nunca lanza', async () => {
    expect((await descargar('/x', { buscar: buscarQueDevuelve(403) })).resultado).toBe(
      RESULTADO.SIN_PERMISO,
    );
    const caida = await descargar('/x', {
      buscar: jest.fn(async () => {
        throw new Error('ECONNREFUSED');
      }),
    });
    expect(caida.resultado).toBe(RESULTADO.SERVICIO_DEGRADADO);
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
