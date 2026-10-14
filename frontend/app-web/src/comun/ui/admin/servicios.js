/**
 * Los servicios de la plataforma, con el nombre que lee una persona — SA-2.
 *
 * La sonda de la consola (`/admin/sistema/servicios`) y el tablero técnico
 * nombran cada servicio por su nombre de despliegue («salas-partidas»,
 * «ms-identidad»). Eso es para el equipo que lo opera por dentro; en la
 * consola se lee lo que hace cada uno. Un nombre que este catálogo no conoce
 * se enseña tal cual: es preferible a esconder un servicio.
 *
 * @module comun/ui/admin/servicios
 */

/** Nombre de despliegue → nombre para la consola. */
export const NOMBRE_DEL_SERVICIO = Object.freeze({
  'ms-identidad': 'Identidad y cuentas',
  'ms-cumplimiento': 'Auditoría y privacidad',
  'ms-ecommerce': 'Tienda y pagos',
  'ms-finanzas': 'Créditos y movimientos',
  'ms-subastas': 'Subastas',
  'ms-chatbot': 'Asistente',
  'salas-partidas': 'Salas y partidas',
  'moderacion-sanciones': 'Moderación y sanciones',
  'metricas-plataforma': 'Métricas',
  'admin-parametros': 'Parámetros',
  comentarios: 'Comentarios',
  correo: 'Correo',
  torneos: 'Torneos',
  notificaciones: 'Notificaciones',
  heroes: 'Héroes',
  inventario: 'Inventario',
  productos: 'Catálogo de productos',
  'motor-combate': 'Motor de combate',
  misiones: 'Misiones',
});

/**
 * @param {string|null|undefined} servicio nombre de despliegue
 * @returns {string}
 */
export function nombreDelServicio(servicio) {
  const clave = String(servicio ?? '').trim();
  return NOMBRE_DEL_SERVICIO[clave] ?? (clave || 'Servicio sin nombre');
}

/**
 * Lo que significa cada estado de la sonda, dicho para quien opera: sin el
 * motivo técnico de la conexión, que es asunto del tablero técnico.
 */
export const SITUACION_DEL_SERVICIO = Object.freeze({
  OPERATIVO: 'Responde con normalidad.',
  LENTO: 'Responde, pero tarda más de lo esperado.',
  CAIDO: 'No responde.',
  NO_DESPLEGADO: 'No se ejecuta en este entorno.',
  NO_OBSERVABLE: 'Se ejecuta en otro servidor y su salud se consulta allí.',
});
