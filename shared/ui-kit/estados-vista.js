/**
 * SCRUM-320 / RNF-USA-003 — Los cuatro estados obligatorios de una vista que consulta datos.
 *
 * Toda vista que consulte datos DEBE mostrar exactamente uno de estos estados
 * en cada momento. Centralizado en shared/ui-kit para que los 20 módulos no
 * los implementen de 20 formas distintas.
 *
 * Regla del cliente (2026-08-13): «uno como usuario jamás debería ver un
 * status de HTML». Por eso ningún texto de estos estados lleva códigos de
 * protocolo ni detalle técnico — eso va a la consola, para el equipo.
 */

/**
 * Helper to build common structure
 * @param {string} modifier - The class modifier (e.g., carga, vacio)
 * @param {string} [role] - ARIA role
 * @param {string} mensaje - Main message
 * @param {string} [detalle] - Optional detail message
 * @returns {HTMLDivElement}
 */
function buildStateElement(modifier, role, mensaje, detalle) {
  const container = document.createElement('div');
  container.className = `nexus-estado nexus-estado--${modifier}`;
  if (role) {
    container.setAttribute('role', role);
  }

  const pMensaje = document.createElement('p');
  pMensaje.className = 'nexus-estado__mensaje';
  pMensaje.textContent = mensaje;
  container.appendChild(pMensaje);

  if (detalle) {
    const pDetalle = document.createElement('p');
    pDetalle.className = 'nexus-estado__detalle';
    pDetalle.textContent = detalle;
    container.appendChild(pDetalle);
  }

  return container;
}

/**
 * Construye el estado de carga (Loading).
 * @param {string} [mensaje='Cargando...'] - El mensaje a mostrar.
 * @returns {HTMLDivElement} El contenedor del estado de carga.
 */
export function construirCarga(mensaje = 'Cargando...') {
  return buildStateElement('carga', 'status', mensaje);
}

/**
 * Construye el estado vacío (Empty).
 * @param {string} mensaje - El mensaje principal a mostrar.
 * @param {string} [detalle] - Detalle adicional opcional.
 * @returns {HTMLDivElement} El contenedor del estado vacío.
 */
export function construirVacio(mensaje, detalle) {
  return buildStateElement('vacio', '', mensaje, detalle);
}

/**
 * Construye el estado de error (Error).
 * @param {string} mensaje - El mensaje principal de error.
 * @param {string} [detalle] - Detalle adicional del error.
 * @returns {HTMLDivElement} El contenedor del estado de error.
 */
export function construirError(mensaje, detalle) {
  return buildStateElement('error', 'alert', mensaje, detalle);
}

/**
 * Construye el estado de éxito (Success).
 * @param {string} mensaje - El mensaje principal de éxito.
 * @param {string} [detalle] - Detalle adicional opcional.
 * @returns {HTMLDivElement} El contenedor del estado de éxito.
 */
export function construirExito(mensaje, detalle) {
  return buildStateElement('exito', 'status', mensaje, detalle);
}
