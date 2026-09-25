/**
 * UXC-4 — reunir la vitrina, buscar, filtrar, ordenar y saber qué es tuyo.
 */

import { jest } from '@jest/globals';

import {
  coincideBusqueda,
  filtrarProductos,
  hayCriterios,
  MAX_LOTES,
  normalizar,
  ordenarProductos,
  ORDENES,
  propiedadesDelJugador,
  reunirVitrina,
} from './tienda-catalogo.js';
import { aProductoDeVitrina } from './tienda-adaptador.js';

function respuesta(cuerpo, ok = true, status = 200) {
  return { ok, status, json: async () => cuerpo };
}

function dto(i, cambios = {}) {
  return {
    id: `p-${i}`,
    nombre: `Producto ${i}`,
    descripcion: 'Descripción',
    habilidades: null,
    tipo: 'ARMA',
    precioFinal: 1000 * i,
    precioOriginal: 1000 * i,
    moneda: 'COP',
    ...cambios,
  };
}

const modelos = (lista) => lista.map((d) => ({ ...aProductoDeVitrina(d), dto: d }));

describe('reunir la vitrina', () => {
  test('pide de cincuenta en cincuenta hasta la última', async () => {
    const lote = (n, desde) => Array.from({ length: n }, (_, i) => dto(desde + i));
    const fetchImpl = jest
      .fn()
      .mockResolvedValueOnce(respuesta({ content: lote(50, 0), last: false, totalPages: 2 }))
      .mockResolvedValueOnce(respuesta({ content: lote(7, 50), last: true, totalPages: 2 }));

    const { productos, completo } = await reunirVitrina({ fetchImpl });

    expect(productos).toHaveLength(57);
    expect(completo).toBe(true);
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/vitrina?size=50');
    expect(fetchImpl.mock.calls[1][0]).toBe('/api/v1/vitrina?page=1&size=50');
  });

  test('un lote corto también es el último, aunque no diga `last`', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta({ content: [dto(1)] }));

    const { productos, completo } = await reunirVitrina({ fetchImpl });

    expect(productos).toHaveLength(1);
    expect(completo).toBe(true);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  test('con el tope alcanzado, lo dice (completo: false)', async () => {
    const lleno = Array.from({ length: 50 }, (_, i) => dto(i));
    const fetchImpl = jest.fn().mockResolvedValue(respuesta({ content: lleno, last: false }));

    const { completo } = await reunirVitrina({ fetchImpl });

    expect(completo).toBe(false);
    expect(fetchImpl).toHaveBeenCalledTimes(MAX_LOTES);
  });

  test('un rechazo sale con su estado y su problem detail', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValue(
        respuesta({ type: 'urn:nexus:problema:catalogo-no-disponible' }, false, 503),
      );

    await expect(reunirVitrina({ fetchImpl })).rejects.toMatchObject({
      estado: 503,
      problema: { type: 'urn:nexus:problema:catalogo-no-disponible' },
    });
  });
});

describe('buscar', () => {
  const [espada] = modelos([
    dto(45, { nombre: 'Espada Élfica', habilidades: 'Corte veloz', tipo: 'ARMA' }),
  ]);

  test('sin mayúsculas ni tildes, en nombre, habilidades y tipo', () => {
    expect(normalizar('  Élfica ')).toBe('elfica');
    expect(coincideBusqueda(espada, 'elfica')).toBe(true);
    expect(coincideBusqueda(espada, 'VELOZ')).toBe(true);
    expect(coincideBusqueda(espada, 'arma')).toBe(true);
    expect(coincideBusqueda(espada, 'escudo')).toBe(false);
    expect(coincideBusqueda(espada, '')).toBe(true);
  });

  test('por precio: «45000», «45.000» y «45 000» son el mismo', () => {
    expect(coincideBusqueda(espada, '45000')).toBe(true);
    expect(coincideBusqueda(espada, '45.000')).toBe(true);
    expect(coincideBusqueda(espada, '45 000')).toBe(true);
    expect(coincideBusqueda(espada, '99000')).toBe(false);
  });

  test('una búsqueda con letras y cifras no se lee como precio', () => {
    expect(coincideBusqueda(espada, 'espada 4')).toBe(false);
  });
});

describe('filtrar y ordenar', () => {
  const lista = modelos([
    dto(1, { tipo: 'ARMA' }),
    dto(2, { tipo: 'ARMADURA' }),
    dto(3, { tipo: 'ARMA', precioOriginal: 5000, precioFinal: 3000 }),
    dto(4, { tipo: 'ITEM', precioFinal: null, precioOriginal: null }),
  ]);

  test('por tipo', () => {
    expect(filtrarProductos(lista, { tipo: 'ARMA' }).map((p) => p.id)).toEqual(['p-1', 'p-3']);
  });

  test('por rango de precio; el que no tiene precio no entra en un filtro de precio', () => {
    expect(filtrarProductos(lista, { precioMinimo: 2000 }).map((p) => p.id)).toEqual([
      'p-2',
      'p-3',
    ]);
    expect(filtrarProductos(lista, { precioMaximo: 2000 }).map((p) => p.id)).toEqual([
      'p-1',
      'p-2',
    ]);
    expect(filtrarProductos(lista, {}).map((p) => p.id)).toContain('p-4');
  });

  test('«solo en promoción» es una rebaja que se ve en el precio', () => {
    expect(filtrarProductos(lista, { soloPromocion: true }).map((p) => p.id)).toEqual(['p-3']);
  });

  test('por precio con los que no tienen precio al final; y por nombre', () => {
    expect(ordenarProductos(lista, ORDENES.PRECIO_ASC).map((p) => p.id)).toEqual([
      'p-1',
      'p-2',
      'p-3',
      'p-4',
    ]);
    expect(ordenarProductos(lista, ORDENES.PRECIO_DESC).map((p) => p.id)).toEqual([
      'p-3',
      'p-2',
      'p-1',
      'p-4',
    ]);
    const nombres = modelos([dto(1, { nombre: 'Zafiro' }), dto(2, { nombre: 'Ámbar' })]);
    expect(ordenarProductos(nombres, ORDENES.NOMBRE).map((p) => p.nombre)).toEqual([
      'Ámbar',
      'Zafiro',
    ]);
  });

  test('el orden del catálogo no reordena y no toca la lista original', () => {
    const copia = [...lista];
    expect(ordenarProductos(lista, ORDENES.CATALOGO)).toEqual(copia);
    ordenarProductos(lista, ORDENES.PRECIO_DESC);
    expect(lista).toEqual(copia);
  });

  test('hay criterios solo si alguno filtra de verdad', () => {
    expect(hayCriterios({})).toBe(false);
    expect(hayCriterios({ busqueda: '   ', orden: ORDENES.NOMBRE })).toBe(false);
    expect(hayCriterios({ tipo: 'ARMA' })).toBe(true);
    expect(hayCriterios({ precioMinimo: 0 })).toBe(true);
    expect(hayCriterios({ soloPromocion: true })).toBe(true);
  });
});

describe('lo que ya tiene el jugador', () => {
  test('cuenta las unidades de cada producto del inventario', async () => {
    const consultar = jest.fn().mockResolvedValue({
      elementos: [
        { id: 'e1', productoId: 'p-1' },
        { id: 'e2', productoId: 'p-1' },
        { id: 'e3', productoId: 'p-2' },
      ],
      ultima: true,
    });

    const propias = await propiedadesDelJugador('uid-1', { consultar });

    expect(propias.get('p-1')).toBe(2);
    expect(propias.get('p-2')).toBe(1);
    expect(consultar).toHaveBeenCalledWith('uid-1', 0);
  });

  test('sin inventario legible no se afirma nada', async () => {
    const consultar = jest.fn().mockRejectedValue(new Error('caído'));

    expect((await propiedadesDelJugador('uid-1', { consultar })).size).toBe(0);
    expect((await propiedadesDelJugador(null, { consultar })).size).toBe(0);
  });
});
