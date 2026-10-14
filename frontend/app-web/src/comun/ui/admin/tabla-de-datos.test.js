/**
 * La tabla de la consola — SA-2.
 *
 * Lo que se afirma es lo que el informe de revisión echó en falta: que las
 * columnas casen con sus encabezados porque es una tabla de verdad (no filas
 * flexibles), que se pueda ordenar con el teclado y que el lector de pantalla
 * sepa por qué columna y en qué sentido, que un dato que falta se diga, y que
 * en un teléfono cada fila lleve la etiqueta de cada dato.
 */

import { jest } from '@jest/globals';

import { SIN_DATO, ordenarLocal, tablaDeDatos, vigilarDesborde } from './tabla-de-datos.js';

const COLUMNAS = [
  { clave: 'nombre', titulo: 'Nombre', principal: true, ordenable: true },
  { clave: 'pujas', titulo: 'Pujas', tipo: 'numero', ordenable: true },
  { clave: 'cierra', titulo: 'Cierra', tipo: 'fecha', ordenable: true },
  {
    clave: 'acciones',
    titulo: 'Acciones',
    tipo: 'accion',
    valor: (f) => {
      const boton = document.createElement('button');
      boton.textContent = `Ver ${f.nombre}`;
      return boton;
    },
  },
];

const FILAS = [
  { nombre: 'Espada', pujas: 2, cierra: '2026-10-06T18:00:00Z' },
  { nombre: 'Arco', pujas: 10, cierra: '2026-10-05T18:00:00Z' },
  { nombre: 'Yelmo', pujas: null, cierra: null },
];

const montar = (opciones = {}) => {
  const tabla = tablaDeDatos({
    titulo: 'Subastas activas',
    columnas: COLUMNAS,
    filas: FILAS,
    ...opciones,
  });
  document.body.replaceChildren(tabla);
  return tabla;
};

const primeraColumna = (tabla) =>
  [...tabla.querySelectorAll('tbody tr')].map((tr) => tr.firstElementChild.textContent);

describe('estructura', () => {
  test('es una tabla de verdad, con caption, encabezados de columna y de fila', () => {
    const tabla = montar();

    const table = tabla.querySelector('table');
    expect(table.querySelector('caption').textContent).toBe('Subastas activas');
    expect(table.querySelector('caption').className).toBe('solo-lectores');
    expect([...table.querySelectorAll('thead th[scope="col"]')]).toHaveLength(4);
    const filas = table.querySelectorAll('tbody tr');
    expect(filas).toHaveLength(3);
    expect(filas[0].firstElementChild.tagName).toBe('TH');
    expect(filas[0].firstElementChild.getAttribute('scope')).toBe('row');
    // Ninguna fila flexible del patrón antiguo: eso era lo que descuadraba.
    expect(table.querySelector('.tabla__fila, .tabla__celda')).toBeNull();
  });

  test('los roles ARIA van escritos: sigue siendo tabla aunque el CSS la apile', () => {
    const tabla = montar();

    expect(tabla.querySelector('table').getAttribute('role')).toBe('table');
    expect(tabla.querySelector('thead').getAttribute('role')).toBe('rowgroup');
    expect(tabla.querySelector('tbody tr').getAttribute('role')).toBe('row');
    expect(tabla.querySelector('thead th').getAttribute('role')).toBe('columnheader');
    expect(tabla.querySelector('tbody th').getAttribute('role')).toBe('rowheader');
    expect(tabla.querySelector('tbody td').getAttribute('role')).toBe('cell');
  });

  test('cada celda lleva su tipo y la etiqueta de su columna para el teléfono', () => {
    const tabla = montar();

    const celdas = tabla.querySelectorAll('tbody tr:first-child > *');
    expect([...celdas].map((c) => c.dataset.tipo)).toEqual(['texto', 'numero', 'fecha', 'accion']);
    expect(celdas[1].dataset.etiqueta).toBe('Pujas');
    // La que identifica la fila y la de acciones no llevan etiqueta delante.
    expect(celdas[0].dataset.etiqueta).toBeUndefined();
    expect(celdas[3].dataset.etiqueta).toBeUndefined();
    expect(celdas[3].querySelector('.tabla-admin__acciones button').textContent).toBe('Ver Espada');
  });

  test('el marco que se desplaza tiene nombre y entra en el orden del teclado', () => {
    const tabla = montar();

    const marco = tabla.querySelector('.tabla-admin__marco');
    expect(marco.getAttribute('tabindex')).toBe('0');
    expect(marco.getAttribute('role')).toBe('region');
    const nombre = document.getElementById(marco.getAttribute('aria-labelledby'));
    expect(nombre.tagName).toBe('CAPTION');
  });

  test('un dato que falta se dice, no se rellena con un cero ni con un guion', () => {
    const tabla = montar();

    const yelmo = [...tabla.querySelectorAll('tbody tr')][2];
    expect(yelmo.querySelectorAll('.tabla-admin__sin-dato')).toHaveLength(2);
    expect(yelmo.textContent).toContain(SIN_DATO);
    expect(yelmo.textContent).not.toMatch(/--|\b0\b/);
  });

  test('con leyenda visible, el caption se ve', () => {
    const tabla = montar({ leyendaVisible: true });
    expect(tabla.querySelector('caption').className).toBe('tabla-admin__leyenda');
  });

  test('sin filas: un estado vacío que dice qué falta, sin tabla', () => {
    const tabla = montar({
      filas: [],
      vacio: { titulo: 'No hay subastas', detalle: 'Vuelve luego.' },
    });

    expect(tabla.querySelector('table')).toBeNull();
    expect(tabla.querySelector('.estado-vista--vacio').textContent).toContain('No hay subastas');
  });

  test('el resumen va encima, junto al orden en el teléfono', () => {
    const tabla = montar({ resumen: '3 de 13 subastas' });

    expect(tabla.querySelector('.tabla-admin__resumen').textContent).toBe('3 de 13 subastas');
    const selector = tabla.querySelector('[data-zona="orden-movil"]');
    expect([...selector.options].map((o) => o.value)).toEqual([
      '',
      'nombre:asc',
      'nombre:desc',
      'pujas:asc',
      'pujas:desc',
      'cierra:asc',
      'cierra:desc',
    ]);
  });

  test('sin columnas o sin título no se monta nada a medias', () => {
    expect(() => tablaDeDatos({ titulo: 'x', columnas: [], filas: [] })).toThrow(TypeError);
    expect(() => tablaDeDatos({ columnas: COLUMNAS, filas: [] })).toThrow(TypeError);
  });
});

describe('orden', () => {
  test('el encabezado es un botón; ordena de menor a mayor y luego al revés', () => {
    const tabla = montar();

    const pujas = tabla.querySelector('[data-orden="pujas"]');
    expect(pujas.tagName).toBe('BUTTON');
    expect(pujas.closest('th').getAttribute('aria-sort')).toBe('none');

    pujas.click();
    expect(pujas.closest('th').getAttribute('aria-sort')).toBe('ascending');
    expect(primeraColumna(tabla)).toEqual(['Espada', 'Arco', 'Yelmo']);

    pujas.click();
    expect(pujas.closest('th').getAttribute('aria-sort')).toBe('descending');
    // Lo que no tiene dato va al final en los dos sentidos.
    expect(primeraColumna(tabla)).toEqual(['Arco', 'Espada', 'Yelmo']);
    expect(tabla.querySelector('[role="status"]').textContent).toBe(
      'Ordenado por Pujas, de mayor a menor.',
    );
  });

  test('las fechas se ordenan por instante y los textos sin mayúsculas ni tildes', () => {
    const tabla = montar();

    tabla.querySelector('[data-orden="cierra"]').click();
    expect(primeraColumna(tabla)).toEqual(['Arco', 'Espada', 'Yelmo']);
    tabla.querySelector('[data-orden="nombre"]').click();
    expect(primeraColumna(tabla)).toEqual(['Arco', 'Espada', 'Yelmo']);
  });

  test('el selector del teléfono ordena igual y queda a la par de los encabezados', () => {
    const tabla = montar();

    const selector = tabla.querySelector('[data-zona="orden-movil"]');
    selector.value = 'pujas:desc';
    selector.dispatchEvent(new Event('change'));
    expect(primeraColumna(tabla)).toEqual(['Arco', 'Espada', 'Yelmo']);
    expect(tabla.querySelector('[data-columna="pujas"]').getAttribute('aria-sort')).toBe(
      'descending',
    );
  });

  test('con alOrdenar, el orden lo aplica quien llama (el servidor) y la tabla no reordena', () => {
    const alOrdenar = jest.fn();
    const tabla = montar({ alOrdenar });

    tabla.querySelector('[data-orden="pujas"]').click();
    expect(alOrdenar).toHaveBeenCalledWith({ clave: 'pujas', direccion: 'asc' });
    expect(primeraColumna(tabla)).toEqual(['Espada', 'Arco', 'Yelmo']);
  });

  test('un orden de partida se respeta y se anuncia en el encabezado', () => {
    const tabla = montar({ orden: { clave: 'pujas', direccion: 'desc' } });

    expect(primeraColumna(tabla)).toEqual(['Arco', 'Espada', 'Yelmo']);
    expect(tabla.querySelector('[data-columna="pujas"]').getAttribute('aria-sort')).toBe(
      'descending',
    );
  });

  test('ordenarLocal es estable y deja intacta la lista original', () => {
    const filas = [
      { n: 'b', v: 1 },
      { n: 'a', v: 1 },
      { n: 'c', v: 0 },
    ];
    const ordenadas = ordenarLocal(filas, [{ clave: 'v', titulo: 'V', tipo: 'numero' }], {
      clave: 'v',
      direccion: 'asc',
    });
    expect(ordenadas.map((f) => f.n)).toEqual(['c', 'b', 'a']);
    expect(filas.map((f) => f.n)).toEqual(['b', 'a', 'c']);
    expect(ordenarLocal(filas, [], { clave: 'x', direccion: 'asc' })).toBe(filas);
  });
});

describe('desborde', () => {
  const medidas = (marco, { scrollLeft, scrollWidth, clientWidth }) => {
    Object.defineProperty(marco, 'scrollWidth', { value: scrollWidth, configurable: true });
    Object.defineProperty(marco, 'clientWidth', { value: clientWidth, configurable: true });
    marco.scrollLeft = scrollLeft;
  };

  test('el marco dice hacia qué lado sigue la tabla al desplazarse', () => {
    const tabla = montar();
    const marco = tabla.querySelector('.tabla-admin__marco');

    medidas(marco, { scrollLeft: 0, scrollWidth: 1200, clientWidth: 600 });
    marco.dispatchEvent(new Event('scroll'));
    expect(marco.dataset.desborde).toBe('fin');

    medidas(marco, { scrollLeft: 300, scrollWidth: 1200, clientWidth: 600 });
    marco.dispatchEvent(new Event('scroll'));
    expect(marco.dataset.desborde).toBe('inicio fin');

    medidas(marco, { scrollLeft: 600, scrollWidth: 1200, clientWidth: 600 });
    marco.dispatchEvent(new Event('scroll'));
    expect(marco.dataset.desborde).toBe('inicio');
  });

  test('si cabe entera, sin atributo; y mide al cambiar de ancho donde se puede', () => {
    const observados = [];
    const original = globalThis.ResizeObserver;
    globalThis.ResizeObserver = class {
      constructor(alCambiar) {
        this.alCambiar = alCambiar;
      }

      observe(elemento) {
        observados.push(elemento);
        this.alCambiar();
      }
    };
    try {
      const marco = document.createElement('div');
      medidas(marco, { scrollLeft: 0, scrollWidth: 500, clientWidth: 500 });
      marco.dataset.desborde = 'fin';
      vigilarDesborde(marco);
      expect(observados).toEqual([marco]);
      expect(marco.dataset.desborde).toBeUndefined();
    } finally {
      globalThis.ResizeObserver = original;
    }
  });
});
