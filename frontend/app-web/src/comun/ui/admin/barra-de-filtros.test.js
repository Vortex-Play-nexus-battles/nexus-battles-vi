/**
 * La barra de filtros de la consola — SA-2 (informe de revisión, puntos 4, 16
 * y 20): la búsqueda primero, los filtros en orden con su etiqueta, «Limpiar
 * filtros» cuando hay algo puesto y lo aplicado como fichas que se quitan.
 */

import { jest } from '@jest/globals';

import { barraDeFiltros } from './barra-de-filtros.js';

function montar(opciones = {}) {
  const alAplicar = jest.fn();
  const barra = barraDeFiltros({
    busqueda: { nombre: 'buscar', etiqueta: 'Buscar', placeholder: 'Apodo, correo o nombre' },
    filtros: [
      {
        nombre: 'rol',
        etiqueta: 'Rol',
        opciones: [
          ['MODERADOR', 'Moderador'],
          ['JUGADOR', 'Jugador'],
        ],
      },
      { nombre: 'desde', etiqueta: 'Desde', tipo: 'fecha' },
      { nombre: 'hasta', etiqueta: 'Hasta', tipo: 'fecha' },
    ],
    alAplicar,
    ...opciones,
  });
  document.body.replaceChildren(barra.elemento);
  return { barra, alAplicar, formulario: barra.elemento };
}

const fichas = (formulario) =>
  [...formulario.querySelectorAll('.filtros-admin__ficha')].map((f) =>
    f.textContent.replace('×', '').trim(),
  );

test('es un formulario de búsqueda con nombre, y la búsqueda va primero', () => {
  const { formulario } = montar();

  expect(formulario.getAttribute('role')).toBe('search');
  expect(formulario.getAttribute('aria-label')).toBe('Buscar y filtrar');
  expect(formulario.firstElementChild.className).toBe('filtros-admin__busqueda');
  const entrada = formulario.querySelector('input[name="buscar"]');
  expect(entrada.type).toBe('search');
  // Cada control con su etiqueta visible asociada.
  expect(formulario.querySelector(`label[for="${entrada.id}"]`).textContent).toContain('Buscar');
  const rol = formulario.querySelector('select[name="rol"]');
  expect(formulario.querySelector(`label[for="${rol.id}"]`).textContent).toContain('Rol');
  expect([...rol.options].map((o) => o.textContent)).toEqual(['Todos', 'Moderador', 'Jugador']);
});

test('la búsqueda se aplica al enviar (no a cada tecla), recortada', () => {
  const { formulario, alAplicar } = montar();

  const entrada = formulario.querySelector('input[name="buscar"]');
  entrada.value = '  Ana  ';
  entrada.dispatchEvent(new Event('input'));
  expect(alAplicar).not.toHaveBeenCalled();

  formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  expect(alAplicar).toHaveBeenCalledWith({ buscar: 'Ana', rol: '', desde: '', hasta: '' });
});

test('un desplegable o una fecha se aplican al cambiar', () => {
  const { formulario, alAplicar } = montar();

  const rol = formulario.querySelector('select[name="rol"]');
  rol.value = 'MODERADOR';
  rol.dispatchEvent(new Event('change'));
  expect(alAplicar).toHaveBeenLastCalledWith({
    buscar: '',
    rol: 'MODERADOR',
    desde: '',
    hasta: '',
  });
});

test('lo aplicado se ve como fichas; quitar una vacía su control y vuelve a aplicar', () => {
  const { formulario, alAplicar } = montar();

  formulario.querySelector('input[name="buscar"]').value = 'Ana';
  formulario.querySelector('select[name="rol"]').value = 'MODERADOR';
  formulario.querySelector('input[name="desde"]').value = '2026-09-01';
  formulario.dispatchEvent(new Event('submit', { cancelable: true }));

  expect(fichas(formulario)).toEqual(['Buscar: Ana', 'Rol: Moderador', 'Desde: 01/09/2026']);
  const quitarRol = formulario.querySelector('[data-quitar="rol"]');
  expect(quitarRol.getAttribute('aria-label')).toBe('Quitar el filtro Rol: Moderador');

  quitarRol.click();
  expect(formulario.querySelector('select[name="rol"]').value).toBe('');
  expect(alAplicar).toHaveBeenLastCalledWith({
    buscar: 'Ana',
    rol: '',
    desde: '2026-09-01',
    hasta: '',
  });
  expect(document.activeElement).toBe(formulario.querySelector('select[name="rol"]'));
  expect(fichas(formulario)).toEqual(['Buscar: Ana', 'Desde: 01/09/2026']);
});

test('«Limpiar filtros» solo aparece con algo puesto, y lo deja todo vacío', () => {
  const { formulario, alAplicar } = montar();

  const limpiar = formulario.querySelector('[data-accion="limpiar-filtros"]');
  expect(limpiar.hidden).toBe(true);

  const rol = formulario.querySelector('select[name="rol"]');
  rol.value = 'JUGADOR';
  rol.dispatchEvent(new Event('change'));
  expect(limpiar.hidden).toBe(false);

  limpiar.click();
  expect(alAplicar).toHaveBeenLastCalledWith({ buscar: '', rol: '', desde: '', hasta: '' });
  expect(limpiar.hidden).toBe(true);
  expect(fichas(formulario)).toEqual([]);
});

test('los valores de partida se ven puestos y con su ficha', () => {
  const { formulario, barra } = montar({ valores: { rol: 'MODERADOR' } });

  expect(formulario.querySelector('select[name="rol"]').value).toBe('MODERADOR');
  expect(fichas(formulario)).toEqual(['Rol: Moderador']);
  expect(barra.leer()).toEqual({ buscar: '', rol: 'MODERADOR', desde: '', hasta: '' });
});

test('una validación que no pasa no aplica nada y lo dice', () => {
  const { formulario, alAplicar } = montar({
    validar: (v) => (v.desde && v.hasta && v.desde > v.hasta ? 'El periodo está al revés.' : null),
  });

  formulario.querySelector('input[name="desde"]').value = '2026-10-05';
  formulario.querySelector('input[name="hasta"]').value = '2026-10-01';
  formulario.dispatchEvent(new Event('submit', { cancelable: true }));

  expect(alAplicar).not.toHaveBeenCalled();
  const error = formulario.querySelector('[data-zona="error-filtros"]');
  expect(error.hidden).toBe(false);
  expect(error.getAttribute('role')).toBe('alert');
  expect(error.textContent).toBe('El periodo está al revés.');

  formulario.querySelector('input[name="hasta"]').value = '2026-10-09';
  formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  expect(alAplicar).toHaveBeenCalledTimes(1);
  expect(error.hidden).toBe(true);
});

test('fijar cambia los valores sin aplicar, y actualiza las fichas', () => {
  const { barra, alAplicar, formulario } = montar();

  barra.fijar({ desde: '2026-09-06', hasta: '2026-10-05' });
  expect(alAplicar).not.toHaveBeenCalled();
  expect(fichas(formulario)).toEqual(['Desde: 06/09/2026', 'Hasta: 05/10/2026']);
});

test('sin alAplicar no se monta', () => {
  expect(() => barraDeFiltros({ filtros: [] })).toThrow(TypeError);
});
