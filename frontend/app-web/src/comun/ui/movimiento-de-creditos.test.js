/**
 * Un movimiento del libro de créditos, dicho para quien lo lee (SA-3). El
 * signo lo decide ms-finanzas (`signo`, creditos.yaml 1.3.0): aquí solo se
 * traduce.
 */

import { comoImporte, importeConUnidad, nombreDelConcepto } from './movimiento-de-creditos.js';

describe('el importe de un movimiento', () => {
  test.each([
    ['SUMA', 1500, '+1.500', 'suma', '+1.500 créditos'],
    ['RESTA', 150, '−150', 'resta', '−150 créditos'],
    ['APARTA', 320, '320 apartados', 'aparta', '320 créditos apartados'],
    ['NEUTRO', 100, '100 devueltos', 'neutro', '100 créditos devueltos'],
  ])('%s', (signo, monto, texto, tono, frase) => {
    expect(comoImporte({ signo, monto })).toEqual({ texto, tono });
    expect(importeConUnidad({ signo, monto })).toBe(frase);
  });

  test('un signo que no se conoce no rompe: se lee como devuelto, sin sumar ni restar', () => {
    expect(comoImporte({ signo: 'OTRO', monto: 5 }).tono).toBe('neutro');
    expect(importeConUnidad({ signo: 'OTRO', monto: 5 })).toBe('5 créditos devueltos');
  });
});

describe('el concepto de un movimiento', () => {
  test('los conceptos que escribe el backend, con palabras', () => {
    expect(nombreDelConcepto('apuesta-sala')).toBe('Apuesta de una batalla');
    expect(nombreDelConcepto('inscripcion-torneo')).toBe('Inscripción a un torneo');
    expect(nombreDelConcepto('bono-registro')).toBe('Créditos de bienvenida');
  });

  test('uno nuevo se enseña tal cual y uno ausente se dice', () => {
    expect(nombreDelConcepto('concepto-nuevo')).toBe('concepto-nuevo');
    expect(nombreDelConcepto(null)).toBe('Movimiento de créditos');
  });
});
