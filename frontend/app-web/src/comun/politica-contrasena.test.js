/**
 * La política de contraseñas antes de enviar — R17 / RF-AUT-002.
 *
 * Los casos son los mismos que prueba `PasswordPolicyValidatorTest` en el
 * servicio de identidad: si esta copia y la del servidor discrepan, alguien
 * que no conoce el juego ve un «cumple» aquí y un rechazo allí.
 */

import {
  LONGITUD_MINIMA_EXCLUSIVA,
  motivoDeContrasena,
  reglasQueFaltan,
} from './politica-contrasena.js';

describe('reglasQueFaltan', () => {
  test('una contraseña que cumple no tiene reglas pendientes', () => {
    expect(reglasQueFaltan('Nexus#2026')).toEqual([]);
    expect(reglasQueFaltan('ÁrbolÑandú9!')).toEqual([]);
  });

  test('«más de 8» es estricto: 8 no llega, 9 sí', () => {
    expect(LONGITUD_MINIMA_EXCLUSIVA).toBe(8);
    expect(reglasQueFaltan('Abcde#1x')).toContain('más de 8 caracteres');
    expect(reglasQueFaltan('Abcde#1xy')).toEqual([]);
  });

  test('dice cada regla que falta, en orden', () => {
    expect(reglasQueFaltan('')).toEqual([
      'más de 8 caracteres',
      'una mayúscula',
      'una minúscula',
      'un número',
      'un símbolo',
    ]);
    expect(reglasQueFaltan('solominusculas')).toEqual(['una mayúscula', 'un número', 'un símbolo']);
    expect(reglasQueFaltan('SinSimbolo123')).toEqual(['un símbolo']);
  });

  test('un espacio cuenta como símbolo, igual que en el servidor', () => {
    expect(reglasQueFaltan('Nexus 2026 ok')).toEqual([]);
  });

  test('sin valor no rompe', () => {
    expect(reglasQueFaltan(undefined)).toHaveLength(5);
    expect(reglasQueFaltan(null)).toHaveLength(5);
  });
});

describe('motivoDeContrasena', () => {
  test('una frase legible, o null si cumple', () => {
    expect(motivoDeContrasena('Nexus#2026')).toBeNull();
    expect(motivoDeContrasena('SinSimbolo123')).toBe('A tu contraseña le falta: un símbolo.');
    expect(motivoDeContrasena('solominusculas')).toBe(
      'A tu contraseña le falta: una mayúscula, un número y un símbolo.',
    );
  });
});
