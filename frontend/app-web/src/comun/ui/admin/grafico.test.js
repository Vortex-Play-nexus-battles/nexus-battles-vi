/**
 * Gráfico de barras de la consola — SA-2: eje con marcas redondas, valor de
 * cada barra, resumen legible y los mismos datos en una tabla.
 */

import { escalaAgradable, graficoDeBarras } from './grafico.js';

describe('escalaAgradable', () => {
  test.each([
    [4, { tope: 4, paso: 1 }],
    [5, { tope: 6, paso: 2 }],
    [9, { tope: 10, paso: 2.5 }],
    [37, { tope: 40, paso: 10 }],
    [1250, { tope: 1500, paso: 500 }],
    [0.8, { tope: 0.8, paso: 0.2 }],
  ])('máximo %p → %p', (maximo, esperado) => {
    expect(escalaAgradable(maximo)).toEqual(esperado);
  });

  test('sin máximo útil, un eje de 0 a 4', () => {
    expect(escalaAgradable(0)).toEqual({ tope: 4, paso: 1 });
    expect(escalaAgradable(Number.NaN)).toEqual({ tope: 4, paso: 1 });
  });

  test('en cifras enteras nunca hay medias marcas', () => {
    expect(escalaAgradable(2).paso).toBeGreaterThanOrEqual(1);
  });
});

const DATOS = [
  { etiqueta: '1 oct', valor: 4 },
  { etiqueta: '2 oct', valor: 0 },
  { etiqueta: '3 oct', valor: null },
  { etiqueta: '4 oct', valor: 2 },
];

test('una columna por dato, con su altura proporcional al tope del eje', () => {
  const figura = graficoDeBarras({ titulo: 'Registros por día', datos: DATOS, unidad: 'cuentas' });

  const columnas = [...figura.querySelectorAll('.grafico-admin__columna')];
  expect(columnas.map((c) => c.dataset.valor)).toEqual(['4', '0', 'sin-dato', '2']);
  const alto = (c) =>
    c.querySelector('.grafico-admin__barra').style.getPropertyValue('--grafico-alto');
  expect(alto(columnas[0])).toBe('100.00%');
  expect(alto(columnas[3])).toBe('50.00%');
  expect(columnas[2].querySelector('.grafico-admin__barra--sin-dato')).not.toBeNull();
  expect(columnas[2].querySelector('.grafico-admin__globo').textContent).toBe('3 oct: sin dato');
  expect(columnas[0].querySelector('.grafico-admin__globo').textContent).toBe('1 oct: 4 cuentas');
});

test('el eje dice las marcas, de arriba abajo, y las líneas guía saben cuántas son', () => {
  const figura = graficoDeBarras({ titulo: 'x', datos: DATOS });

  expect(
    [...figura.querySelectorAll('.grafico-admin__eje-y span')].map((s) => s.textContent),
  ).toEqual(['4', '3', '2', '1', '0']);
  expect(
    figura.querySelector('.grafico-admin__columnas').style.getPropertyValue('--grafico-marcas'),
  ).toBe('4');
});

test('con muchas barras, las etiquetas del eje se aclaran para no pisarse', () => {
  const muchos = Array.from({ length: 30 }, (_, i) => ({ etiqueta: `${i + 1} oct`, valor: i }));
  const figura = graficoDeBarras({ titulo: 'x', datos: muchos, maximoEtiquetas: 10 });

  const etiquetas = [...figura.querySelectorAll('.grafico-admin__etiqueta')].filter(
    (e) => e.textContent !== '',
  );
  expect(etiquetas.length).toBeLessThanOrEqual(10);
  expect(etiquetas[0].textContent).toBe('1 oct');
});

test('resumen legible y la misma serie en una tabla; las barras, ocultas al lector', () => {
  const figura = graficoDeBarras({
    titulo: 'Registros por día',
    datos: DATOS,
    unidad: 'cuentas nuevas',
    columnaEtiqueta: 'Día',
  });

  expect(figura.tagName).toBe('FIGURE');
  expect(figura.querySelector('figcaption').textContent).toBe('Registros por día');
  expect(figura.querySelector('.grafico-admin__resumen').textContent).toBe(
    '6 cuentas nuevas en total · máximo 4 (1 oct)',
  );
  expect(figura.querySelector('.grafico-admin__area').getAttribute('aria-hidden')).toBe('true');
  const tabla = figura.querySelector('details.grafico-admin__datos table');
  expect([...tabla.querySelectorAll('thead th')].map((th) => th.textContent)).toEqual([
    'Día',
    'Cuentas nuevas',
  ]);
  expect(tabla.querySelectorAll('tbody tr')).toHaveLength(4);
  expect(tabla.querySelectorAll('.tabla-admin__sin-dato')).toHaveLength(1);
});

test('sin ningún valor no hay barras: lo dice', () => {
  const figura = graficoDeBarras({ titulo: 'x', datos: [{ etiqueta: 'a', valor: null }] });
  expect(figura.querySelector('.grafico-admin__area')).toBeNull();
  expect(figura.querySelector('.grafico-admin__resumen').textContent).toBe(
    'Sin datos en este periodo.',
  );
});

test('el globo de las barras de los bordes se abre hacia dentro', () => {
  const muchos = Array.from({ length: 8 }, (_, i) => ({ etiqueta: `${i + 1} oct`, valor: i }));
  const figura = graficoDeBarras({ titulo: 'x', datos: muchos });

  const lados = [...figura.querySelectorAll('.grafico-admin__columna')].map(
    (c) => c.dataset.lado ?? 'centro',
  );
  expect(lados).toEqual(['inicio', 'inicio', 'centro', 'centro', 'centro', 'centro', 'fin', 'fin']);
});

test('en un teléfono se queda una de cada tres etiquetas del eje', () => {
  const muchos = Array.from({ length: 30 }, (_, i) => ({ etiqueta: `${i + 1} oct`, valor: i }));
  const figura = graficoDeBarras({ titulo: 'x', datos: muchos, maximoEtiquetas: 10 });

  const enElMovil = [...figura.querySelectorAll('.grafico-admin__etiqueta[data-movil="si"]')].map(
    (e) => e.textContent,
  );
  expect(enElMovil).toEqual(['1 oct', '10 oct', '19 oct', '28 oct']);
});
