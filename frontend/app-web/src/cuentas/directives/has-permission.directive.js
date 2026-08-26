/**
 * ==========================================================================
 * Directiva / Helper de Permisos RBAC en JavaScript Estándar (HU-RBAC-001 / 003 / 004)
 * Pila Tecnológica: Vanilla JS ES2022 (Sin Angular / Sin TypeScript)
 * 
 * Evalúa la matriz de 44 combinaciones y oculta/muestra elementos del DOM
 * que contengan el atributo [data-has-permission="ACCION"].
 * ==========================================================================
 */

// Matriz de permisos reactiva para la UI según Tabla 24 Extendida (HU-RBAC-001 y HU-RBAC-003)
export const RBAC_PERMISSION_MATRIX = {
  'JUGADOR': [
    'CREAR_CUENTA_JUGADOR',
    'MODIFICAR_PERFIL_PROPIO',
    'PUBLICAR_COMENTARIOS',
    'ELIMINAR_COMENTARIO_PROPIO'
  ],
  'MODERADOR': [
    'MODIFICAR_PERFIL_PROPIO',
    'PUBLICAR_COMENTARIOS',
    'ELIMINAR_COMENTARIO_PROPIO',
    'MODERAR_COMENTARIOS',
    'EMITIR_ADVERTENCIAS',
    'SUSPENDER_USUARIOS'
  ],
  'ADMINISTRADOR': [
    'MODIFICAR_PERFIL_PROPIO',
    'PUBLICAR_COMENTARIOS',
    'ELIMINAR_COMENTARIO_PROPIO',
    'MODERAR_COMENTARIOS',
    'EMITIR_ADVERTENCIAS',
    'SUSPENDER_USUARIOS',
    'BANEAR_DEFINITIVAMENTE',
    'GESTIONAR_PRODUCTOS'
  ],
  'SUPER_ADMINISTRADOR': [
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
    'ASIGNAR_ROL' // Exclusivo de Super Administrador (HU-RBAC-003)
  ]
};

/**
 * Rol activo actual del usuario en la interfaz (por defecto 'JUGADOR')
 */
let currentRole = 'JUGADOR';

/**
 * Actualiza el rol activo en el frontend
 * @param {'JUGADOR'|'MODERADOR'|'ADMINISTRADOR'|'SUPER_ADMINISTRADOR'} newRole
 */
export function setCurrentRole(newRole) {
  currentRole = newRole;
  applyHasPermissionDirective();
}

/**
 * Obtiene el rol activo actual
 * @returns {string}
 */
export function getCurrentRole() {
  return currentRole;
}

/**
 * Verifica si un rol tiene permiso para ejecutar una acción
 * @param {string} role - Nombre del rol
 * @param {string} action - Nombre de la acción (ej: 'ASIGNAR_ROL')
 * @returns {boolean}
 */
export function checkPermission(role, action) {
  const permissions = RBAC_PERMISSION_MATRIX[role] || [];
  return permissions.includes(action);
}

/**
 * Directiva DOM: Escanea el DOM y oculta/muestra elementos con data-has-permission="ACCION"
 * Reemplaza la directiva [hasPermission] de Angular sin dependencias ni compilación.
 * @param {HTMLElement|Document} [root=document]
 */
export function applyHasPermissionDirective(root = document) {
  const elements = root.querySelectorAll('[data-has-permission]');
  
  elements.forEach((el) => {
    const requiredAction = el.getAttribute('data-has-permission');
    const isPermitted = checkPermission(currentRole, requiredAction);

    if (isPermitted) {
      el.style.display = '';
      el.removeAttribute('aria-hidden');
    } else {
      el.style.display = 'none';
      el.setAttribute('aria-hidden', 'true');
    }
  });
}
