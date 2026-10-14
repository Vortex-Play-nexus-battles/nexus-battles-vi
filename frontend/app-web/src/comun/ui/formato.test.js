/** Formato de cifras y fechas (PR-UX-1). */

import { creditos, cuantoFalta, dinero, fecha, numero, porcentaje } from './formato.js';

describe('créditos', () => {
  test('separador de miles y sin decimales', () => {
    expect(creditos(1250)).toMatch(/1.250/);
    expect(creditos('60')).toBe('60');
    expect(creditos(0)).toBe('0');
  });

  test('sin dato se dice con una raya, no con un cero que enganaria', () => {
    expect(creditos(null)).toBe('—');
    expect(creditos(undefined)).toBe('—');
    expect(creditos('no es un numero')).toBe('—');
  });
});

describe('fechas', () => {
  test('una fecha invalida no revienta la vista', () => {
    expect(fecha('cuando sea')).toBe('—');
    expect(fecha(null)).toBe('—');
  });

  test('una fecha real se pinta', () => {
    expect(fecha('2026-10-08T10:00:00Z')).toMatch(/2026/);
  });
});

describe('cuantoFalta', () => {
  const ahora = new Date('2026-10-01T10:00:00Z');

  test('días, horas y minutos', () => {
    expect(cuantoFalta('2026-10-04T10:00:00Z', ahora)).toBe('en 3 d');
    expect(cuantoFalta('2026-10-01T12:15:00Z', ahora)).toBe('en 2 h 15 min');
    expect(cuantoFalta('2026-10-01T10:20:00Z', ahora)).toBe('en 20 min');
  });

  test('lo que ya paso se dice que termino, no en negativo', () => {
    expect(cuantoFalta('2026-09-30T10:00:00Z', ahora)).toBe('termino');
    expect(cuantoFalta(ahora, ahora)).toBe('termino');
  });
});

describe('numero y porcentaje', () => {
  test('numero admite decimales fijos', () => {
    expect(numero(1234.5678, 2)).toMatch(/1.234,57|1,234.57/);
    expect(numero(null)).toBe('—');
  });

  test('porcentaje redondea y pone el simbolo', () => {
    expect(porcentaje(0.914)).toBe('91 %');
    expect(porcentaje(0)).toBe('0 %');
    expect(porcentaje(null)).toBe('—');
  });
});

describe('dinero (SA-15)', () => {
  test('la cifra con separadores y el código de la moneda, céntimos solo si los hay', () => {
    expect(dinero(25000, 'COP')).toBe('25.000 COP');
    expect(dinero('25000.00', 'cop')).toBe('25.000 COP');
    expect(dinero(10.5, 'USD')).toBe('10,50 USD');
    expect(dinero(7, null)).toBe('7');
  });

  test('sin cifra, un guion; nunca un cero inventado', () => {
    expect(dinero(null, 'COP')).toBe('—');
    expect(dinero(undefined, 'COP')).toBe('—');
    expect(dinero('', 'COP')).toBe('—');
    expect(dinero('mucho', 'COP')).toBe('—');
  });
});
