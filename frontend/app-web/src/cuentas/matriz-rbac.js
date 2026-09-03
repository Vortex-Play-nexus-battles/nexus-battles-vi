/**
 * ==========================================================================
 * Matriz RBAC de referencia — Tabla 24 extendida (12 acciones × 4 roles).
 *
 * Fuente ÚNICA para el cliente:
 *   - matriz-permisos.js la usa como respaldo cuando ms-identidad no responde.
 *   - index.js la usa para contar las acciones por rol en el hub.
 *
 * El servidor (RbacAuthorizationService) sigue siendo la autoridad real;
 * esto es solo referencia de interfaz para que hub y panel nunca muestren
 * cifras distintas.
 * ==========================================================================
 */

export const ACCIONES_RBAC = Object.freeze([
  'CREAR_CUENTA_JUGADOR',
  'MODIFICAR_PERFIL_PROPIO',
  'PUBLICAR_COMENTARIOS',
  'ELIMINAR_COMENTARIO_PROPIO',
  'MODERAR_COMENTARIOS',
  'EMITIR_ADVERTENCIAS',
  'SUSPENDER_USUARIOS',
  'BANEAR_DEFINITIVAMENTE',
  'CREAR_ADMIN_MODERADOR',
  'GESTIONAR_PRODUCTOS',
  'ASIGNAR_ROL',
  'GESTIONAR_CUENTAS'
]);

export const TOTAL_ACCIONES = ACCIONES_RBAC.length; // 12

export const MATRIZ_RBAC = Object.freeze({
  JUGADOR: {
    CREAR_CUENTA_JUGADOR: 'GRANTED',
    MODIFICAR_PERFIL_PROPIO: 'GRANTED',
    PUBLICAR_COMENTARIOS: 'GRANTED',
    ELIMINAR_COMENTARIO_PROPIO: 'GRANTED',
    MODERAR_COMENTARIOS: 'DENIED',
    EMITIR_ADVERTENCIAS: 'DENIED',
    SUSPENDER_USUARIOS: 'DENIED',
    BANEAR_DEFINITIVAMENTE: 'DENIED',
    CREAR_ADMIN_MODERADOR: 'DENIED',
    GESTIONAR_PRODUCTOS: 'DENIED',
    ASIGNAR_ROL: 'DENIED',
    GESTIONAR_CUENTAS: 'DENIED'
  },
  MODERADOR: {
    CREAR_CUENTA_JUGADOR: 'DENIED',
    MODIFICAR_PERFIL_PROPIO: 'GRANTED',
    PUBLICAR_COMENTARIOS: 'GRANTED',
    ELIMINAR_COMENTARIO_PROPIO: 'GRANTED',
    MODERAR_COMENTARIOS: 'GRANTED',
    EMITIR_ADVERTENCIAS: 'GRANTED',
    SUSPENDER_USUARIOS: 'TEMPORARY',
    BANEAR_DEFINITIVAMENTE: 'DENIED',
    CREAR_ADMIN_MODERADOR: 'DENIED',
    GESTIONAR_PRODUCTOS: 'DENIED',
    ASIGNAR_ROL: 'DENIED',
    GESTIONAR_CUENTAS: 'DENIED'
  },
  ADMINISTRADOR: {
    CREAR_CUENTA_JUGADOR: 'DENIED',
    MODIFICAR_PERFIL_PROPIO: 'GRANTED',
    PUBLICAR_COMENTARIOS: 'GRANTED',
    ELIMINAR_COMENTARIO_PROPIO: 'GRANTED',
    MODERAR_COMENTARIOS: 'GRANTED',
    EMITIR_ADVERTENCIAS: 'GRANTED',
    SUSPENDER_USUARIOS: 'GRANTED',
    BANEAR_DEFINITIVAMENTE: 'GRANTED',
    CREAR_ADMIN_MODERADOR: 'DENIED',
    GESTIONAR_PRODUCTOS: 'GRANTED',
    ASIGNAR_ROL: 'DENIED',
    GESTIONAR_CUENTAS: 'GRANTED'
  },
  SUPER_ADMINISTRADOR: {
    CREAR_CUENTA_JUGADOR: 'DENIED',
    MODIFICAR_PERFIL_PROPIO: 'GRANTED',
    PUBLICAR_COMENTARIOS: 'GRANTED',
    ELIMINAR_COMENTARIO_PROPIO: 'GRANTED',
    MODERAR_COMENTARIOS: 'GRANTED',
    EMITIR_ADVERTENCIAS: 'GRANTED',
    SUSPENDER_USUARIOS: 'GRANTED',
    BANEAR_DEFINITIVAMENTE: 'GRANTED',
    CREAR_ADMIN_MODERADOR: 'GRANTED',
    GESTIONAR_PRODUCTOS: 'GRANTED',
    ASIGNAR_ROL: 'GRANTED',
    GESTIONAR_CUENTAS: 'GRANTED'
  }
});

/**
 * Cuenta las acciones que un rol tiene GRANTED o TEMPORARY.
 * @param {string} rol
 * @param {Record<string, Record<string, string>>} [matriz] matriz a evaluar (por defecto la de referencia)
 * @returns {number}
 */
export function contarAccionesPermitidas(rol, matriz = MATRIZ_RBAC) {
  const permisos = matriz[rol] || {};
  return Object.values(permisos).filter(
    (tipo) => tipo === 'GRANTED' || tipo === 'TEMPORARY'
  ).length;
}
