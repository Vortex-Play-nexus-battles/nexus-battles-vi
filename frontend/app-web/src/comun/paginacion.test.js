/**
 * HU-INV-011 - Control de paginacion.
 * Fuente: issue #43 (SCRUM-272). Criterios de aceptacion migrados de Jira.
 *
 * Las pruebas se escriben antes que la implementacion (TDD, ciclo rojo).
 */
import { construirPaginacion, calcularVentana, CASILLAS_VISIBLES } from './paginacion.js';

/** Botones numerados del control, en orden. */
function casillas(control) {
  return [...control.querySelectorAll('.paginacion__pagina:not([data-direccion])')];
}

/** Numeros que muestran las casillas. */
function numeros(control) {
  return casillas(control).map((c) => c.textContent);
}

function flecha(control, direccion) {
  return control.querySelector(`.paginacion__pagina[data-direccion="${direccion}"]`);
}

describe('Control de paginacion', () => {
  // --- Criterio 1: hasta diez casillas y marca la activa -------------------

  test('con menos de diez paginas las muestra todas', () => {
    const control = construirPaginacion({ paginaActual: 0, totalPaginas: 5 }, () => {});

    expect(numeros(control)).toEqual(['1', '2', '3', '4', '5']);
  });

  test('con mas de diez paginas presenta exactamente diez casillas', () => {
    const control = construirPaginacion({ paginaActual: 0, totalPaginas: 40 }, () => {});

    expect(casillas(control)).toHaveLength(CASILLAS_VISIBLES);
    expect(CASILLAS_VISIBLES).toBe(10);
  });

  test('numera las casillas desde uno aunque el indice de pagina empiece en cero', () => {
    const control = construirPaginacion({ paginaActual: 0, totalPaginas: 3 }, () => {});

    expect(numeros(control)).toEqual(['1', '2', '3']);
  });

  test('marca la pagina activa, y solo esa', () => {
    const control = construirPaginacion({ paginaActual: 2, totalPaginas: 5 }, () => {});

    const activas = casillas(control).filter((c) => c.getAttribute('aria-current') === 'page');
    expect(activas).toHaveLength(1);
    expect(activas[0].textContent).toBe('3');
  });

  test('pulsar una casilla pide esa pagina por su indice', () => {
    const pedidas = [];
    const control = construirPaginacion({ paginaActual: 0, totalPaginas: 5 }, (n) =>
      pedidas.push(n),
    );

    casillas(control)[3].click();

    expect(pedidas).toEqual([3]);
  });

  test('pulsar la pagina activa no vuelve a pedirla', () => {
    const pedidas = [];
    const control = construirPaginacion({ paginaActual: 2, totalPaginas: 5 }, (n) =>
      pedidas.push(n),
    );

    casillas(control)[2].click();

    expect(pedidas).toEqual([]);
  });

  // --- Criterio 2: flechas y ventana deslizante ---------------------------

  test('sin paginas fuera del rango visible no hay flechas', () => {
    const control = construirPaginacion({ paginaActual: 0, totalPaginas: 8 }, () => {});

    expect(flecha(control, 'anterior')).toBeNull();
    expect(flecha(control, 'siguiente')).toBeNull();
  });

  test('en la primera pagina de un inventario grande solo aparece la flecha derecha', () => {
    const control = construirPaginacion({ paginaActual: 0, totalPaginas: 40 }, () => {});

    expect(flecha(control, 'anterior')).toBeNull();
    expect(flecha(control, 'siguiente')).not.toBeNull();
  });

  test('en la ultima pagina solo aparece la flecha izquierda', () => {
    const control = construirPaginacion({ paginaActual: 39, totalPaginas: 40 }, () => {});

    expect(flecha(control, 'anterior')).not.toBeNull();
    expect(flecha(control, 'siguiente')).toBeNull();
  });

  test('en medio de un inventario grande aparecen las dos flechas', () => {
    const control = construirPaginacion({ paginaActual: 20, totalPaginas: 40 }, () => {});

    expect(flecha(control, 'anterior')).not.toBeNull();
    expect(flecha(control, 'siguiente')).not.toBeNull();
  });

  test('la ventana de casillas se desplaza al avanzar', () => {
    const alPrincipio = construirPaginacion({ paginaActual: 0, totalPaginas: 40 }, () => {});
    const avanzado = construirPaginacion({ paginaActual: 20, totalPaginas: 40 }, () => {});

    expect(numeros(alPrincipio)).toEqual(['1', '2', '3', '4', '5', '6', '7', '8', '9', '10']);
    expect(numeros(avanzado)).not.toEqual(numeros(alPrincipio));
    expect(numeros(avanzado)).toContain('21');
    expect(casillas(avanzado)).toHaveLength(CASILLAS_VISIBLES);
  });

  test('las flechas avanzan y retroceden una pagina', () => {
    const pedidas = [];
    const control = construirPaginacion({ paginaActual: 20, totalPaginas: 40 }, (n) =>
      pedidas.push(n),
    );

    flecha(control, 'siguiente').click();
    flecha(control, 'anterior').click();

    expect(pedidas).toEqual([21, 19]);
  });

  test('la ventana nunca se sale del rango de paginas', () => {
    for (const paginaActual of [0, 1, 19, 38, 39]) {
      const { inicio, fin } = calcularVentana(paginaActual, 40);
      expect(inicio).toBeGreaterThanOrEqual(0);
      expect(fin).toBeLessThanOrEqual(40);
      expect(fin - inicio).toBe(CASILLAS_VISIBLES);
      expect(paginaActual).toBeGreaterThanOrEqual(inicio);
      expect(paginaActual).toBeLessThan(fin);
    }
  });

  test('con menos paginas que casillas la ventana las abarca todas', () => {
    expect(calcularVentana(2, 5)).toEqual({ inicio: 0, fin: 5 });
  });

  // --- Criterio 3: conservacion de filtros y busqueda ----------------------
  //
  // La busqueda es HU-INV-002 y todavia no existe. Lo que si depende de este
  // control es que al cambiar de pagina no arrastre ni reinicie nada mas:
  // emite unicamente el indice pedido y deja el resto del estado al llamador.

  test('al cambiar de pagina emite solo el indice, sin tocar otro estado', () => {
    const recibido = [];
    const control = construirPaginacion({ paginaActual: 0, totalPaginas: 5 }, (...args) =>
      recibido.push(args),
    );

    casillas(control)[2].click();

    expect(recibido).toHaveLength(1);
    expect(recibido[0]).toEqual([2]);
  });

  // --- Contrato y accesibilidad -------------------------------------------

  test('no se pinta control cuando hay una sola pagina', () => {
    const control = construirPaginacion({ paginaActual: 0, totalPaginas: 1 }, () => {});

    expect(control.hidden).toBe(true);
    expect(casillas(control)).toHaveLength(0);
  });

  test('no se pinta control cuando el inventario esta vacio', () => {
    const control = construirPaginacion({ paginaActual: 0, totalPaginas: 0 }, () => {});

    expect(control.hidden).toBe(true);
  });

  test('rechaza un total de paginas que no sea un entero no negativo', () => {
    expect(() => construirPaginacion({ paginaActual: 0, totalPaginas: -1 }, () => {})).toThrow(
      RangeError,
    );
    expect(() => construirPaginacion({ paginaActual: 0, totalPaginas: 2.5 }, () => {})).toThrow(
      RangeError,
    );
  });

  test('rechaza una pagina actual fuera del total', () => {
    expect(() => construirPaginacion({ paginaActual: 5, totalPaginas: 5 }, () => {})).toThrow(
      RangeError,
    );
  });

  test('todas las casillas son botones alcanzables por teclado', () => {
    const control = construirPaginacion({ paginaActual: 20, totalPaginas: 40 }, () => {});

    const todos = [...control.querySelectorAll('.paginacion__pagina')];
    expect(todos.length).toBeGreaterThan(0);
    for (const boton of todos) {
      expect(boton.tagName).toBe('BUTTON');
      expect(boton.type).toBe('button');
    }
  });

  test('el control se anuncia como navegacion y las flechas llevan nombre accesible', () => {
    const control = construirPaginacion({ paginaActual: 20, totalPaginas: 40 }, () => {});

    expect(control.tagName).toBe('NAV');
    expect(control.getAttribute('aria-label')).toMatch(/paginacion/i);
    expect(flecha(control, 'anterior').getAttribute('aria-label')).toMatch(/anterior/i);
    expect(flecha(control, 'siguiente').getAttribute('aria-label')).toMatch(/siguiente/i);
  });

  test('informa en texto la pagina en curso y el total', () => {
    const control = construirPaginacion({ paginaActual: 2, totalPaginas: 5 }, () => {});

    expect(control.querySelector('.paginacion__info').textContent).toBe('Pagina 3 de 5');
  });
});
