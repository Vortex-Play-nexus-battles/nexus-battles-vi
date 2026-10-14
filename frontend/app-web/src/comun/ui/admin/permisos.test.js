/**
 * Los permisos de un rol, en palabras (SA-3, §7.3.4 «rol y permisos
 * asignados»). Salen de la matriz que publica el servidor (rbac.yaml 2.0.0).
 */

import { NOMBRE_DEL_PERMISO, permisosDelRol } from './permisos.js';

const MATRIZ = {
  JUGADOR: {
    CREAR_CUENTA_JUGADOR: 'GRANTED',
    PUBLICAR_COMENTARIOS: 'GRANTED',
    MODERAR_COMENTARIOS: 'DENIED',
  },
  MODERADOR: {
    MODERAR_COMENTARIOS: 'GRANTED',
    SUSPENDER_USUARIOS: 'TEMPORARY',
    BANEAR_DEFINITIVAMENTE: 'DENIED',
    ACCION_DESCONOCIDA: 'GRANTED',
  },
};

describe('los permisos de un rol', () => {
  test('solo lo concedido, en el orden de la matriz, y lo temporal dicho', () => {
    expect(permisosDelRol(MATRIZ, 'JUGADOR')).toEqual([
      'Crear una cuenta de jugador',
      'Publicar comentarios',
    ]);
    expect(permisosDelRol(MATRIZ, 'MODERADOR')).toEqual([
      'Moderar comentarios',
      'Suspender cuentas (solo de forma temporal)',
    ]);
  });

  test('sin matriz, sin rol o con un rol que no está, ninguno', () => {
    expect(permisosDelRol(null, 'JUGADOR')).toEqual([]);
    expect(permisosDelRol(MATRIZ, null)).toEqual([]);
    expect(permisosDelRol(MATRIZ, 'NADIE')).toEqual([]);
  });

  test('las trece acciones de la matriz tienen nombre', () => {
    expect(Object.keys(NOMBRE_DEL_PERMISO)).toHaveLength(13);
    expect(Object.values(NOMBRE_DEL_PERMISO).every((n) => /^[A-ZÁÉÍÓÚ]/.test(n))).toBe(true);
  });
});
