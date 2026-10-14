/**
 * Los permisos de un rol, en palabras — SA-3 (§7.3.4, «rol y permisos
 * asignados»).
 *
 * Salen de la matriz que publica el servidor (`GET /rbac/matrix`, rbac.yaml
 * 2.0.0), no de una lista escrita aquí: hasta 7.3.9 el alta de cuentas
 * administrativas decía que un Moderador «gestiona cuentas», cosa que la
 * Tabla 24 no le da. Lo usan esa pantalla y la ficha administrativa del
 * usuario; por eso vive en el kit de la consola.
 *
 * @module comun/ui/admin/permisos
 */

/** Cómo se lee cada acción de la matriz (`RbacAction` de rbac.yaml), en su orden. */
export const NOMBRE_DEL_PERMISO = Object.freeze({
  CREAR_CUENTA_JUGADOR: 'Crear una cuenta de jugador',
  MODIFICAR_PERFIL_PROPIO: 'Editar su propio perfil',
  PUBLICAR_COMENTARIOS: 'Publicar comentarios',
  ELIMINAR_COMENTARIO_PROPIO: 'Eliminar sus comentarios',
  MODERAR_COMENTARIOS: 'Moderar comentarios',
  EMITIR_ADVERTENCIAS: 'Emitir advertencias',
  SUSPENDER_USUARIOS: 'Suspender cuentas',
  BANEAR_DEFINITIVAMENTE: 'Banear definitivamente',
  CREAR_ADMIN_MODERADOR: 'Crear cuentas administrativas',
  GESTIONAR_PRODUCTOS: 'Gestionar los productos de la tienda',
  ASIGNAR_ROL: 'Cambiar roles',
  GESTIONAR_CUENTAS: 'Gestionar cuentas de otros usuarios',
  RESTABLECER_SEGUNDO_FACTOR: 'Restablecer la verificación en dos pasos',
});

/**
 * Las acciones que la matriz da a un rol, en palabras. Una permitida solo de
 * forma temporal (el Moderador suspende, pero no para siempre) lo dice.
 *
 * @param {Record<string, Record<string, string>>|null|undefined} matriz
 * @param {string|null|undefined} rol
 * @returns {string[]}
 */
export function permisosDelRol(matriz, rol) {
  const fila = rol ? matriz?.[rol] : null;
  if (!fila || typeof fila !== 'object') {
    return [];
  }
  const permisos = [];
  for (const [accion, nombre] of Object.entries(NOMBRE_DEL_PERMISO)) {
    if (fila[accion] === 'GRANTED') {
      permisos.push(nombre);
    } else if (fila[accion] === 'TEMPORARY') {
      permisos.push(`${nombre} (solo de forma temporal)`);
    }
  }
  return permisos;
}
