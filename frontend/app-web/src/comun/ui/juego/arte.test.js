import { describe, expect, test } from '@jest/globals';
import { esImagenProvisional, imagenDeMision, imagenDeProducto, retratoDe } from './arte.js';
import { ARTE_DE_MISIONES, ARTE_DE_PRODUCTOS, RETRATOS_DE_PROTOTIPOS } from './arte-manifiesto.js';

const PROVISIONAL =
  'data:image/svg+xml,%3Csvg%20xmlns%3D%27http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%27%3E';

describe('fase 2 de arte: de donde sale cada imagen', () => {
  test('el manifiesto trae los 65 assets aprobados: 57 productos, 8 misiones y 8 retratos de prototipo', () => {
    expect(Object.keys(ARTE_DE_PRODUCTOS)).toHaveLength(57);
    expect(Object.keys(ARTE_DE_MISIONES)).toHaveLength(8);
    expect(Object.keys(RETRATOS_DE_PROTOTIPOS)).toHaveLength(8);
    const urls = [
      ...Object.values(ARTE_DE_PRODUCTOS).map(([, u]) => u),
      ...Object.values(ARTE_DE_MISIONES),
    ];
    expect(new Set(urls).size).toBe(65);
    urls.forEach((u) => expect(u).toMatch(/^\/shared\/ui-kit\/arte\/.+\.webp$/));
  });

  test('la figura provisional de la semilla y el vacio no son arte; cualquier URL elegida si', () => {
    expect(esImagenProvisional(PROVISIONAL)).toBe(true);
    expect(esImagenProvisional('')).toBe(true);
    expect(esImagenProvisional(null)).toBe(true);
    expect(esImagenProvisional('https://cdn.example/a.png')).toBe(false);
    expect(esImagenProvisional('espada.png')).toBe(false);
  });

  test('producto: la imagen de un administrador manda; la provisional se cambia por el arte por id o por nombre', () => {
    expect(imagenDeProducto({ id: 'heroe-chaman', imagen: 'https://cdn.example/mio.png' })).toBe(
      'https://cdn.example/mio.png',
    );
    expect(imagenDeProducto({ id: 'heroe-chaman', imagen: PROVISIONAL })).toBe(
      '/shared/ui-kit/arte/heroes/chaman.webp',
    );
    expect(imagenDeProducto({ nombre: 'Escudo de dragón', miniaturaUrl: PROVISIONAL })).toBe(
      '/shared/ui-kit/arte/productos/armas/escudo-de-dragon.webp',
    );
    expect(imagenDeProducto({ nombre: 'escudo de dragon' })).toBe(
      '/shared/ui-kit/arte/productos/armas/escudo-de-dragon.webp',
    );
    expect(imagenDeProducto({ id: 'otro', nombre: 'Algo nuevo', imagen: PROVISIONAL })).toBeNull();
  });

  test('mision: su imagen o la del manifiesto; sin arte, null (el emblema de la categoria se queda)', () => {
    expect(imagenDeMision({ id: 'templo-olvidado', imagen: null })).toBe(
      '/shared/ui-kit/arte/misiones/templo-olvidado.webp',
    );
    expect(imagenDeMision({ id: 'x', imagen: '/otra.webp' })).toBe('/otra.webp');
    expect(imagenDeMision({ id: 'mision-de-dev' })).toBeNull();
  });

  test('heroe: retratoUrl si llega; si no, el retrato de su prototipo de la Tabla 6', () => {
    expect(retratoDe({ retratoUrl: '/r.webp', prototipo: 'Médico' })).toBe('/r.webp');
    expect(retratoDe({ prototipo: 'Médico' })).toBe('/shared/ui-kit/arte/heroes/medico.webp');
    expect(retratoDe({ prototipo: 'medico' })).toBe('/shared/ui-kit/arte/heroes/medico.webp');
    expect(retratoDe({ nombre: 'Kael' })).toBeNull();
  });
});
