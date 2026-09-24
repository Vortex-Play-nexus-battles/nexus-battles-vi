/**
 * R5 · lo que un heroe propio tiene de verdad, y lo que no se inventa.
 */

import { jest } from '@jest/globals';

import {
  formulaLegible,
  construirEstadisticas,
  construirAccionesDelPrototipo,
  construirDetalleDeHeroe,
} from './detalle-heroe.js';

/** Respuesta de `GET /inventario/heroes/{id}/estadisticas`, tal como la manda. */
function estadisticasDelServicio(extra = {}) {
  return {
    heroeId: 'h-1',
    poder: 10,
    vida: 44,
    defensa: 11,
    ataque: { base: 10, cantidadDados: 1, caras: 6 },
    dano: null,
    sanar: null,
    ...extra,
  };
}

/** Respuesta de `GET /heroes/{prototipo}`. */
function fichaDelCatalogo(extra = {}) {
  return {
    nombre: 'Guerrero Tanque',
    tipo: 'TANQUE',
    descripcion: 'Aguanta.',
    esSanador: false,
    acciones: [
      { nombre: 'Golpe', costo: '2 puntos de poder', efecto: 'Daño directo.' },
      { nombre: 'Muro', costo: '3 puntos de poder', efecto: 'Sube la defensa.' },
    ],
    ...extra,
  };
}

describe('formulaLegible', () => {
  test('junta base y dados como los escribiria una persona', () => {
    expect(formulaLegible({ base: 10, cantidadDados: 1, caras: 6 })).toBe('10 + 1d6');
  });

  test('con base sola, la base; con dados solos, los dados', () => {
    expect(formulaLegible({ base: 7, cantidadDados: 0, caras: 0 })).toBe('7');
    expect(formulaLegible({ base: 0, cantidadDados: 1, caras: 4 })).toBe('1d4');
  });

  test('sin nada utilizable no devuelve una cadena vacia, devuelve null', () => {
    // Para que quien la pinta pueda omitir la fila en vez de dejarla en blanco.
    expect(formulaLegible(null)).toBeNull();
    expect(formulaLegible({})).toBeNull();
    expect(formulaLegible({ base: 0, cantidadDados: 0, caras: 0 })).toBeNull();
  });
});

describe('construirEstadisticas', () => {
  test('pinta las cifras del servicio, rotuladas como del jugador', () => {
    const bloque = construirEstadisticas(estadisticasDelServicio());

    expect(bloque.querySelector('.ficha__seccion-titulo').textContent).toBe('Tus estadísticas');
    // La aclaracion importa: estas cifras llevan el equipamiento aplicado y son
    // suyas, no las del catalogo.
    expect(bloque.querySelector('.ficha__seccion-nota').textContent).toMatch(/equipado/i);
    expect(bloque.textContent).toContain('44');
    expect(bloque.textContent).toContain('10 + 1d6');
  });

  test('una cifra que el servicio no manda no se rellena', () => {
    const bloque = construirEstadisticas(
      estadisticasDelServicio({ defensa: undefined, ataque: null }),
    );

    expect(bloque.textContent).not.toContain('Defensa');
    expect(bloque.textContent).not.toContain('Ataque');
    // Y no aparece un cero ni un guion ocupando su sitio.
    expect(bloque.textContent).not.toMatch(/—|\bDefensa\b/);
  });

  test('NO pinta nivel ni rareza: no existen en ningun contrato', () => {
    const bloque = construirEstadisticas(
      // Aunque el servicio empezara a mandarlos, este bloque no los conoce.
      estadisticasDelServicio({ nivel: 3, rareza: 'EPICA' }),
    );

    expect(bloque.textContent).not.toMatch(/nivel/i);
    expect(bloque.textContent).not.toMatch(/rareza|epica/i);
  });

  test('sin ninguna cifra utilizable no se pinta la seccion', () => {
    expect(construirEstadisticas({ heroeId: 'h-1' })).toBeNull();
    expect(construirEstadisticas(null)).toBeNull();
  });
});

describe('construirAccionesDelPrototipo', () => {
  test('pinta las acciones con su costo y efecto, rotuladas como del prototipo', () => {
    const bloque = construirAccionesDelPrototipo(fichaDelCatalogo());

    expect(bloque.querySelector('.ficha__seccion-titulo').textContent).toBe('Acciones');
    expect(bloque.querySelector('.ficha__seccion-nota').textContent).toMatch(/prototipo/i);
    expect(bloque.querySelectorAll('li')).toHaveLength(2);
    expect(bloque.textContent).toContain('Golpe');
    // El costo llega compuesto por el servidor y se muestra tal cual.
    expect(bloque.textContent).toContain('2 puntos de poder');
  });

  test('un prototipo sanador se dice', () => {
    const bloque = construirAccionesDelPrototipo(fichaDelCatalogo({ esSanador: true }));

    expect(bloque.querySelector('.ficha__seccion-nota').textContent).toMatch(/sanador/i);
  });

  test('sin acciones no se pinta la seccion', () => {
    expect(construirAccionesDelPrototipo(fichaDelCatalogo({ acciones: [] }))).toBeNull();
    expect(construirAccionesDelPrototipo({})).toBeNull();
  });
});

describe('construirDetalleDeHeroe · dos servicios, ninguno hunde al otro', () => {
  test('con los dos, se pintan los dos bloques en orden de lectura', async () => {
    const bloques = await construirDetalleDeHeroe({
      identidad: 'jugadora',
      heroeId: 'h-1',
      prototipo: 'Guerrero Tanque',
      estadisticasDe: async () => estadisticasDelServicio(),
      fichaDe: async () => fichaDelCatalogo(),
    });

    expect(bloques).toHaveLength(2);
    expect(bloques[0].textContent).toContain('Tus estadísticas');
    expect(bloques[1].textContent).toContain('Acciones');
  });

  test('si el catalogo falla, el jugador no pierde sus estadisticas', async () => {
    const bloques = await construirDetalleDeHeroe({
      identidad: 'jugadora',
      heroeId: 'h-1',
      prototipo: 'Guerrero Tanque',
      estadisticasDe: async () => estadisticasDelServicio(),
      fichaDe: async () => {
        throw new Error('503');
      },
    });

    expect(bloques).toHaveLength(1);
    expect(bloques[0].textContent).toContain('Tus estadísticas');
  });

  test('si el inventario falla, siguen estando las acciones del prototipo', async () => {
    const bloques = await construirDetalleDeHeroe({
      identidad: 'jugadora',
      heroeId: 'h-1',
      prototipo: 'Guerrero Tanque',
      estadisticasDe: async () => {
        throw new Error('503');
      },
      fichaDe: async () => fichaDelCatalogo(),
    });

    expect(bloques).toHaveLength(1);
    expect(bloques[0].textContent).toContain('Acciones');
  });

  test('si fallan las dos, no se pinta nada y no se lanza', async () => {
    await expect(
      construirDetalleDeHeroe({
        identidad: 'jugadora',
        heroeId: 'h-1',
        prototipo: 'Guerrero Tanque',
        estadisticasDe: async () => {
          throw new Error('503');
        },
        fichaDe: async () => {
          throw new Error('503');
        },
      }),
    ).resolves.toEqual([]);
  });

  test('sin prototipo conocido no se inventa una llamada al catalogo', async () => {
    const fichaDe = jest.fn();

    const bloques = await construirDetalleDeHeroe({
      identidad: 'jugadora',
      heroeId: 'h-1',
      prototipo: null,
      estadisticasDe: async () => estadisticasDelServicio(),
      fichaDe,
    });

    expect(fichaDe).not.toHaveBeenCalled();
    expect(bloques).toHaveLength(1);
  });
});
