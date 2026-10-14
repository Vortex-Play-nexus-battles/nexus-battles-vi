/**
 * Lo que dice el panel del asistente cuando algo falla — ms-chatbot.yaml
 * 1.7.0.
 *
 * Hasta aquí las cinco pestañas usaban el texto de la de analíticas, así que
 * un 409 al crear la candidata, un fallo al guardar un caso o una solicitud
 * que no cargaba decían «No se pudieron cargar las analíticas». Ahora el
 * título es siempre lo que no se pudo hacer, que lo pone cada pestaña
 * (`queFallo`), y el detalle es el porqué, decidido por el estado
 * (MAPEO-ERRORES §2): que el servicio no responde, que la sesión no tiene
 * permiso, o qué hacer después.
 *
 * Los motivos propios de una pestaña (TEMA_DUPLICADO, CAMBIO_CONCURRENTE,
 * OTRO_TICKET_ABIERTO…) los explica esa pestaña antes de llegar aquí.
 *
 * @module cuentas/panel-chatbot-errores
 */

/** Por qué falló y qué hacer después, según el estado de la respuesta. */
const DETALLE_POR_ESTADO = Object.freeze({
  400: 'El servicio rechazó los datos: revísalos y vuelve a intentarlo.',
  404: 'Ya no existe: recarga la pestaña para ver lo que hay ahora.',
  409: 'Otro cambio llegó antes: revisa lo que se muestra ahora y vuelve a intentarlo.',
});

const SIN_SERVICIO = 'El servicio del asistente no responde. Vuelve a intentarlo en unos minutos.';
const SIN_PERMISO =
  'Tu sesión no tiene permiso para esto. Vuelve a iniciar sesión con una cuenta de administración.';
const OTRO_FALLO =
  'El servicio del asistente respondió con un error. Inténtalo de nuevo en unos minutos.';

/**
 * Título y detalle de un error del panel.
 *
 * @param {{noDisponible?: boolean, sinPermiso?: boolean, estado?: number}|null} error
 * @param {string} [queFallo] lo que no se pudo hacer, p. ej. «No se pudo crear la candidata»
 * @returns {{titulo: string, detalle: string}}
 */
export function textoDeErrorDelPanel(error, queFallo = 'No se pudo completar la acción') {
  let detalle = DETALLE_POR_ESTADO[error?.estado] ?? OTRO_FALLO;
  if (error?.noDisponible) {
    detalle = SIN_SERVICIO;
  } else if (error?.sinPermiso) {
    detalle = SIN_PERMISO;
  }
  return { titulo: queFallo, detalle };
}

/**
 * El mismo texto en una sola frase, para las notas de una línea.
 *
 * @param {object|null} error
 * @param {string} [queFallo]
 * @returns {string} p. ej. «No se pudo crear la candidata. Otro cambio llegó antes: …»
 */
export function fraseDeErrorDelPanel(error, queFallo) {
  const { titulo, detalle } = textoDeErrorDelPanel(error, queFallo);
  return `${titulo}. ${detalle}`;
}
