/**
 * ==========================================================================
 * Directiva / Helper de Permisos RBAC en JavaScript Estándar (HU-RBAC-001 / 003 / 004)
 * Pila Tecnológica: Vanilla JS ES2022 (Sin Angular / Sin TypeScript)
 * 
 * Consume la matriz autorizada por el backend y oculta/muestra elementos del DOM
 * que contengan el atributo [data-has-permission="ACCION"].
 * ==========================================================================
 */

// La UI no conserva una matriz propia: el backend es la única fuente de verdad.
// Hasta cargarla, el resultado es denegado por defecto (fail-closed).
let permissionMatrix = {};

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
 * Configura la matriz obtenida de GET /api/v1/rbac/matrix.
 * @param {Record<string, Record<string, 'GRANTED'|'DENIED'|'TEMPORARY'>>} matrix
 */
export function setPermissionMatrix(matrix) {
  permissionMatrix = matrix && typeof matrix === 'object' ? matrix : {};
  applyHasPermissionDirective();
}

/**
 * Verifica si un rol tiene permiso para ejecutar una acción
 * @param {string} role - Nombre del rol
 * @param {string} action - Nombre de la acción (ej: 'ASIGNAR_ROL')
 * @returns {boolean}
 */
export function checkPermission(role, action) {
  const permission = permissionMatrix[role]?.[action];
  return permission === 'GRANTED' || permission === 'TEMPORARY';
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
