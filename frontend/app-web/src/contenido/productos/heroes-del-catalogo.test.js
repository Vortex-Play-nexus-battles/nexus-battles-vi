import { jest } from '@jest/globals';

import { heroesDelCatalogo, rellenarHeroes } from './heroes-del-catalogo.js';

const HEROES = {
  content: [
    { id: 'b', nombre: 'Mago Fuego' },
    { id: 'a', nombre: 'Chamán' },
    { id: null, nombre: 'Sin id' },
  ],
};

test('lista los héroes del catálogo por nombre, sin los incompletos', async () => {
  const listar = jest.fn(async () => HEROES);
  expect(await heroesDelCatalogo({ listar })).toEqual([
    { id: 'a', nombre: 'Chamán' },
    { id: 'b', nombre: 'Mago Fuego' },
  ]);
  expect(listar).toHaveBeenCalledWith({ tipo: 'HEROE', tamano: 50 }, {});
});

test('rellena el desplegable, conserva el héroe actual aunque no esté a la venta y no toca disabled', async () => {
  const control = document.createElement('select');
  control.disabled = true;
  await rellenarHeroes(control, { valor: 'z', listar: async () => HEROES });
  expect([...control.options].map((o) => o.text)).toEqual([
    'Elige el héroe',
    'Chamán',
    'Mago Fuego',
    'Héroe actual (no está a la venta)',
  ]);
  expect(control.value).toBe('z');
  expect(control.disabled).toBe(true);
});

test('si no se pueden cargar, lo dice sin perder el valor', async () => {
  const control = document.createElement('select');
  await rellenarHeroes(control, {
    valor: null,
    listar: async () => {
      throw new Error('caído');
    },
  });
  expect(control.options[0].text).toBe('No se pudieron cargar los héroes');
  const conValor = document.createElement('select');
  await rellenarHeroes(conValor, {
    valor: 'b',
    listar: async () => {
      throw new Error('caído');
    },
  });
  expect(conValor.value).toBe('b');
  expect(conValor.options[0].text).toBe('Héroe actual');
});
