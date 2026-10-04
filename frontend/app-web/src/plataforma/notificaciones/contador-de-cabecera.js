/**
 * HU-NOT-006 · el contador de la campana fuera de la bandeja — auditoría de
 * DEV del 30-sep: «el número solo se actualiza al abrir la bandeja».
 *
 * Es la misma bandeja que usa la vista de notificaciones (`bandeja.js`): la
 * cuenta por HTTP al cargar, `ContadorActualizado` por la cola privada al
 * instante —también si se lee en otra pestaña o en otro dispositivo (CA-01)—
 * y consulta periódica si el canal se cae (CA-03). Aquí solo se pinta el
 * número de la campana; la lista y las emergentes siguen en la bandeja.
 */

import { crearBandeja } from './bandeja.js';
import { identificadorDeSesion, leerSesion } from './cliente-notificaciones.js';
import { textoDeContador } from './campana.js';

/** Más de esto no cabe en el círculo de la campana. */
export const TOPE_VISIBLE = 99;

/**
 * @param {number} noLeidas
 * @returns {string}
 */
export function textoDelNumero(noLeidas) {
  return noLeidas > TOPE_VISIBLE ? `${TOPE_VISIBLE}+` : String(noLeidas);
}

/**
 * Enciende el contador de la cabecera y lo mantiene al día.
 *
 * @param {ParentNode} raiz cabecera con `[data-zona="contador"]`
 * @param {object} [opciones]
 * @param {Storage} [opciones.almacen]
 * @param {Function} [opciones.fabrica] `(callbacks) => bandeja`, para las pruebas
 * @param {EventTarget|null} [opciones.ventana] donde escuchar el ciclo de la página
 * @returns {object|null} la bandeja, o null si no hay contador o sesión
 */
export function montarContadorDeCabecera(
  raiz,
  { almacen = globalThis.sessionStorage, fabrica = null, ventana = globalThis } = {},
) {
  const contador = raiz?.querySelector?.('[data-zona="contador"]');
  if (!contador) {
    return null;
  }
  const { usuarioId } = leerSesion(almacen);
  if (!usuarioId) {
    return null;
  }
  const sesionId = identificadorDeSesion(almacen);
  const campana = contador.closest('a, button');
  const crear = fabrica ?? ((callbacks) => crearBandeja({ usuarioId, sesionId, ...callbacks }));

  const bandeja = crear({
    alCambiar({ noLeidas }) {
      const cuenta = Number.isFinite(noLeidas) && noLeidas > 0 ? noLeidas : 0;
      contador.textContent = textoDelNumero(cuenta);
      contador.hidden = cuenta === 0;
      campana?.setAttribute(
        'aria-label',
        `Notificaciones: ${textoDeContador(cuenta).toLowerCase()}`,
      );
    },
    // La cabecera no grita: el estado del canal se ve en la bandeja.
    alError: () => {},
  });
  bandeja.iniciar();

  // Al salir se cierra el canal; si la página vuelve de la caché del
  // navegador, se abre otra vez y se pone al día.
  ventana?.addEventListener?.('pagehide', () => bandeja.detener());
  ventana?.addEventListener?.('pageshow', (evento) => {
    if (evento?.persisted) {
      bandeja.iniciar();
    }
  });
  return bandeja;
}
