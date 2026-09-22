/**
 * Panel de vidas de la sala en batalla — HU-SAL-005 (RF-JUE-009).
 *
 * Pinta una barra de vida por participante y las mueve cuando llega una accion
 * resuelta. Cubre el tercer criterio de la historia: «la barra y el valor se
 * actualizan tras cada accion para todos los participantes».
 *
 * Dos limites deliberados:
 *
 *  1. **No calcula dano.** El resultado de cada accion llega ya calculado en el
 *     evento `partida.accion.resuelta`. El motor de combate es de otro bloque
 *     (esta en las exclusiones del Project Charter): aqui se consume, no se
 *     implementa.
 *  2. **No habla el protocolo del canal.** `montarPanelVidas` recibe una
 *     funcion `suscribir`, asi que el transporte —STOMP sobre WebSocket, un
 *     doble en pruebas, o lo que venga— se enchufa por fuera. El panel solo
 *     sabe de eventos ya deserializados.
 *
 * El color NO se decide aqui: se delega en `barra-vida.js`, que lee los
 * umbrales de las fichas de diseno. Este modulo no conoce el 60 ni el 40.
 *
 * @module panel-vidas
 */

import { actualizar } from '../../../../../shared/ui-kit/js/barra-vida.js';
import { acusarCambioDeVida } from '../../comun/ui/acuse.js';
import { barraDeVida } from '../../comun/ui/juego/combate.js';

/** Tipo del mensaje del contrato AsyncAPI que mueve las barras. */
const ACCION_RESUELTA = 'partida.accion.resuelta';

/**
 * Traduce un `Participante` del contrato al componente del kit.
 *
 * Hasta UX-R2.2 esta funcion construia los cinco nodos a mano, con
 * `document.createElement`, y era la unica copia de una estructura que el CSS
 * del kit ya definia. Ahora la estructura vive en un solo sitio
 * (`comun/ui/juego/combate.js`) y una prueba comprueba que coincide con los
 * selectores que consulta `shared/ui-kit/js/barra-vida.js`.
 *
 * @param {object} participante  esquema `Participante` del contrato
 * @returns {HTMLElement}
 */
function crearBarra(participante) {
  const { jugador, heroe, esIA, equipo } = participante;
  return barraDeVida({
    nombre: heroe.nombre,
    idJugador: jugador.id,
    esIA,
    equipo,
  });
}

/**
 * Pinta el estado inicial del combate: una barra por participante.
 *
 * Vacia el contenedor antes de pintar, para que volver a llamar no acumule
 * barras fantasma de una partida anterior.
 *
 * @param {HTMLElement} contenedor
 * @param {Array<object>} participantes  esquema `Participante` del contrato
 * @param {{idPartida?: string}} [opciones]
 *   `idPartida` deja al panel reconocer sus propios eventos. Sin ella el panel
 *   acepta cualquier accion que le entreguen, que es lo que se quiere cuando
 *   se usa suelto en pruebas o en una maqueta.
 */
export function pintarParticipantes(contenedor, participantes, { idPartida } = {}) {
  if (!(contenedor instanceof HTMLElement)) {
    throw new TypeError('panel-vidas: se esperaba un HTMLElement como contenedor.');
  }

  if (idPartida) {
    contenedor.dataset.partida = idPartida;
  }

  contenedor.replaceChildren();

  for (const participante of participantes) {
    const barra = crearBarra(participante);
    contenedor.appendChild(barra);
    actualizar(barra, participante.heroe.vidaActual, participante.heroe.vidaMaxima);
  }
}

/**
 * Aplica una accion resuelta: mueve la barra de cada afectado.
 *
 * Descarta en silencio lo que no le corresponde —otro tipo de mensaje, otra
 * partida, un jugador que no esta en pantalla— porque el canal de la partida
 * lleva varios tipos de mensaje y un panel no debe romperse por recibir uno
 * que no es suyo.
 *
 * @param {HTMLElement} contenedor
 * @param {object} evento  mensaje `AccionResuelta` del contrato
 */
export function aplicarAccionResuelta(contenedor, evento) {
  if (!evento || evento.tipo !== ACCION_RESUELTA) {
    return;
  }

  const propia = contenedor.dataset.partida;
  if (propia && evento.idPartida !== propia) {
    return;
  }

  for (const afectado of evento.afectados ?? []) {
    const barra = contenedor.querySelector(`[data-jugador="${afectado.idJugador}"]`);
    if (!barra) {
      continue;
    } // espectador, o participante ya retirado de la vista

    // UX-R2.10 — la vida ANTES, para poder decir cuánto cambió.
    //
    // La barra ya se movía y ya se anunciaba el valor nuevo, pero el jugador
    // no veía **el golpe**: en una sala de seis, una barra que baja un poco
    // entre otras cinco pasa desapercibida. `acusarCambioDeVida` marca cuál
    // fue y con cuánto, y lo deja escrito — la cifra es texto con
    // `aria-live`, así que con `prefers-reduced-motion` puesto se pierde el
    // movimiento pero no el dato.
    //
    // Se lee de `aria-valuenow`, que es lo que `actualizar()` deja puesto:
    // no hace falta llevar un estado paralelo que pueda desincronizarse.
    const antes = Number(barra.getAttribute('aria-valuenow'));

    actualizar(barra, afectado.vidaActual, afectado.vidaMaxima);

    if (Number.isFinite(antes)) {
      acusarCambioDeVida(barra, antes, afectado.vidaActual);
    }
  }
}

/**
 * Monta el panel: pinta el estado inicial y engancha el canal si lo hay.
 *
 * @param {HTMLElement} contenedor
 * @param {object} opciones
 * @param {string} opciones.idPartida
 * @param {Array<object>} opciones.participantes
 * @param {(alRecibir: (evento: object) => void) => void} [opciones.suscribir]
 *   Recibe el manejador al que entregar cada mensaje del canal de la partida.
 *   Si no se pasa, el panel queda pintado con el estado inicial y quieto: util
 *   mientras el canal en tiempo real no este disponible.
 */
export function montarPanelVidas(contenedor, { idPartida, participantes, suscribir }) {
  pintarParticipantes(contenedor, participantes, { idPartida });

  if (typeof suscribir === 'function') {
    suscribir((evento) => aplicarAccionResuelta(contenedor, evento));
  }
}
