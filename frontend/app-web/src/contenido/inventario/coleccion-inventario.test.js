/**
 * UXC-1 — el inventario reunido y partido en heroes y objetos, y la pestana
 * «Heroes» con sus cartas.
 */
import { jest } from '@jest/globals';

import { esHeroe, mapaDeEquipados, paginaLocal, reunirInventario } from './coleccion-inventario.js';
import { pintarHeroes } from './heroes-inventario.js';

function pagina(numero, elementos, totalPaginas) {
  return {
    elementos,
    numero,
    tamanio: 16,
    totalElementos: 99,
    totalPaginas,
    ultima: numero >= totalPaginas - 1,
  };
}

describe('reunirInventario', () => {
  test('pide pagina a pagina hasta la ultima', async () => {
    const consultar = jest.fn(async (_id, n) => pagina(n, [{ id: `e${n}` }], 3));
    const { elementos, completo } = await reunirInventario(consultar, 'yo');
    expect(consultar.mock.calls.map(([, n]) => n)).toEqual([0, 1, 2]);
    expect(elementos.map((e) => e.id)).toEqual(['e0', 'e1', 'e2']);
    expect(completo).toBe(true);
  });

  test('con el tope alcanzado lo dice (completo = false)', async () => {
    const consultar = jest.fn(async (_id, n) => pagina(n, [{ id: `e${n}` }], 30));
    const { elementos, completo } = await reunirInventario(consultar, 'yo', { maxPaginas: 2 });
    expect(elementos).toHaveLength(2);
    expect(completo).toBe(false);
  });

  test('un inventario vacio es una pagina vacia, no un error', async () => {
    const { elementos, completo } = await reunirInventario(async () => pagina(0, [], 0), 'yo');
    expect(elementos).toEqual([]);
    expect(completo).toBe(true);
  });
});

describe('paginaLocal', () => {
  const lista = Array.from({ length: 35 }, (_, i) => ({ id: `o${i}` }));

  test('corta de dieciseis en dieciseis con la forma del contrato', () => {
    const p = paginaLocal(lista, 2);
    expect(p.elementos.map((e) => e.id)).toEqual(['o32', 'o33', 'o34']);
    expect(p).toMatchObject({ numero: 2, tamanio: 16, totalElementos: 35, totalPaginas: 3 });
    expect(p.ultima).toBe(true);
  });

  test('una pagina fuera de rango se acota a la ultima', () => {
    expect(paginaLocal(lista, 9).numero).toBe(2);
    expect(paginaLocal([], 3)).toMatchObject({ numero: 0, totalPaginas: 0, ultima: true });
  });
});

describe('mapaDeEquipados', () => {
  test('dice que heroe lleva cada objeto', () => {
    const heroes = [
      { id: 'h1', nombrePropio: 'Ayla' },
      { id: 'h2', nombrePropio: 'Kael' },
    ];
    const equipos = new Map([
      ['h1', { armas: ['a1'], armaduras: { CASCO: 'c1' }, items: [] }],
      ['h2', undefined],
    ]);
    const mapa = mapaDeEquipados(heroes, equipos);
    expect(mapa.get('a1')).toBe('Ayla');
    expect(mapa.get('c1')).toBe('Ayla');
    expect(mapa.size).toBe(2);
  });
});

describe('esHeroe', () => {
  test('por el tipo del contrato', () => {
    expect(esHeroe({ tipo: 'HEROE' })).toBe(true);
    expect(esHeroe({ tipo: 'ARMA' })).toBe(false);
  });
});

describe('pintarHeroes', () => {
  let contenedor;
  beforeEach(() => {
    contenedor = document.createElement('div');
    document.body.replaceChildren(contenedor);
  });

  const heroe = (id, cambios = {}) => ({
    id,
    productoId: `p-${id}`,
    tipo: 'HEROE',
    nombrePropio: `Heroe ${id}`,
    disponible: true,
    subastaId: null,
    ...cambios,
  });

  test('sin heroes: vacio con siguiente paso', async () => {
    await pintarHeroes(contenedor, {
      heroes: [],
      identidad: 'yo',
      consultarEquipo: jest.fn(),
      consultarEstadisticas: jest.fn(),
      consultarProducto: jest.fn(),
      alVerFicha: jest.fn(),
      alEquipar: jest.fn(),
    });
    expect(contenedor.textContent).toMatch(/Todavía no tienes héroes/);
    expect(contenedor.querySelector('a[href*="tienda"]')).not.toBeNull();
  });

  test('una carta por heroe con su prototipo, estado y acciones', async () => {
    const alEquipar = jest.fn();
    const alVerFicha = jest.fn();
    const { equipos, prototipos } = await pintarHeroes(contenedor, {
      heroes: [heroe('h1'), heroe('h2', { disponible: false, subastaId: 's' })],
      identidad: 'yo',
      consultarEquipo: async (_i, id) =>
        id === 'h1'
          ? { armas: ['a'], armaduras: {}, items: [] }
          : { armas: [], armaduras: {}, items: [] },
      consultarEstadisticas: async () => ({ poder: 10, vida: 44, defensa: 11 }),
      consultarProducto: async (id) => ({ prototipo: id === 'p-h1' ? 'Mago Hielo' : 'Médico' }),
      alVerFicha,
      alEquipar,
    });
    const cartas = contenedor.querySelectorAll('[data-heroe]');
    expect(cartas).toHaveLength(2);
    expect(cartas[0].dataset.prototipo).toBe('mago-hielo');
    expect(cartas[0].dataset.estado).toBe('DISPONIBLE');
    expect(cartas[1].dataset.estado).toBe('BLOQUEADO');
    expect(cartas[1].querySelector('[data-accion="equipar"]').disabled).toBe(true);
    expect(prototipos.get('h1')).toBe('Mago Hielo');
    expect(equipos.get('h1').armas).toEqual(['a']);

    cartas[0].querySelector('[data-accion="equipar"]').click();
    expect(alEquipar).toHaveBeenCalledWith(expect.objectContaining({ id: 'h1' }));
    cartas[0].querySelector('[data-accion="ver-ficha"]').click();
    expect(alVerFicha).toHaveBeenCalledWith(expect.objectContaining({ id: 'h1' }), {
      prototipo: 'Mago Hielo',
    });
  });

  test('si un servicio no contesta, la carta sale igual sin lo que falto', async () => {
    await pintarHeroes(contenedor, {
      heroes: [heroe('h1')],
      identidad: 'yo',
      consultarEquipo: async () => {
        throw new Error('caido');
      },
      consultarEstadisticas: async () => {
        throw new Error('caido');
      },
      consultarProducto: async () => {
        throw new Error('caido');
      },
      alVerFicha: jest.fn(),
      alEquipar: jest.fn(),
    });
    const carta = contenedor.querySelector('[data-heroe]');
    expect(carta).not.toBeNull();
    expect(carta.querySelector('.stat-block')).toBeNull();
    // Sin equipo leido no se afirma que no pueda combatir.
    expect(carta.dataset.estado).toBe('DISPONIBLE');
    expect(carta.textContent).toContain('Prototipo sin identificar');
  });
});
