/**
 * Nombres de producto para las constantes de los contratos — SA-2.
 *
 * La consola enseñaba los valores tal cual llegaban («CAMBIO_ROL»,
 * «ELIMINACION_LOGICA»). Aquí se dicen como los diría quien opera. Un valor
 * que no esté se humaniza en vez de ocultarse.
 *
 * @module comun/ui/admin/nombres
 */

import { humanizar } from './estado.js';

/** El tipo de una acción registrada en la auditoría (ms-cumplimiento, `TipoAccion`). */
export const NOMBRE_DE_ACCION = Object.freeze({
  CREACION: 'Alta',
  ACTUALIZACION: 'Modificación',
  ELIMINACION_LOGICA: 'Retiro',
  SUSPENSION: 'Suspensión',
  SANCION: 'Sanción',
  CAMBIO_ROL: 'Cambio de rol',
  APROBACION: 'Aprobación',
  RECHAZO: 'Rechazo',
  SECURITY_BYPASS_ATTEMPT: 'Intento de acceso no autorizado',
  OTRO: 'Otra acción',
});

/**
 * @param {string|null|undefined} tipo
 * @returns {string}
 */
export function nombreDeAccion(tipo) {
  return NOMBRE_DE_ACCION[String(tipo ?? '')] ?? humanizar(tipo);
}
