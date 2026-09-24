import { validarTarjeta } from './tienda.js';

// Fecha fija: la validez de la tarjeta depende del día en que corre la prueba.
const HOY = new Date(2026, 8, 23); // 23 de septiembre de 2026

const VALIDA = {
  titular: 'Ana Pérez',
  numero: '4111 1111 1111 1111',
  fechaExpiracion: '12/28',
  cvv: '123',
};

describe('validarTarjeta — HU-CAR-010', () => {
  test('una tarjeta con formato válido no tiene errores', () => {
    expect(validarTarjeta(VALIDA, HOY)).toEqual({});
  });

  test('rechaza un número que no pasa Luhn', () => {
    expect(validarTarjeta({ ...VALIDA, numero: '4111 1111 1111 1112' }, HOY)).toHaveProperty('numero');
  });

  test('rechaza un número con letras o demasiado corto', () => {
    expect(validarTarjeta({ ...VALIDA, numero: '4111abcd' }, HOY)).toHaveProperty('numero');
    expect(validarTarjeta({ ...VALIDA, numero: '411111' }, HOY)).toHaveProperty('numero');
  });

  test('rechaza una tarjeta vencida', () => {
    expect(validarTarjeta({ ...VALIDA, fechaExpiracion: '08/26' }, HOY)).toEqual({
      fechaExpiracion: 'La tarjeta está vencida.',
    });
  });

  test('acepta una tarjeta que vence este mismo mes', () => {
    expect(validarTarjeta({ ...VALIDA, fechaExpiracion: '09/26' }, HOY)).toEqual({});
  });

  test('rechaza un mes imposible y acepta el formato de input month', () => {
    expect(validarTarjeta({ ...VALIDA, fechaExpiracion: '13/27' }, HOY)).toHaveProperty('fechaExpiracion');
    expect(validarTarjeta({ ...VALIDA, fechaExpiracion: '2027-01' }, HOY)).toEqual({});
  });

  test('rechaza un CVV que no tiene 3 o 4 dígitos', () => {
    expect(validarTarjeta({ ...VALIDA, cvv: '12' }, HOY)).toHaveProperty('cvv');
    expect(validarTarjeta({ ...VALIDA, cvv: '12a' }, HOY)).toHaveProperty('cvv');
  });

  test('rechaza un titular vacío o con números', () => {
    expect(validarTarjeta({ ...VALIDA, titular: '' }, HOY)).toHaveProperty('titular');
    expect(validarTarjeta({ ...VALIDA, titular: 'Ana 123' }, HOY)).toHaveProperty('titular');
  });
});
