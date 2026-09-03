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

/** Tipo del mensaje del contrato AsyncAPI que mueve las barras. */
const ACCION_RESUELTA = 'partida.accion.resuelta';

/**
 * Construye el marcado de una barra, con la estructura que documenta el
 * componente compartido.
 *
 * @param {object} participante  esquema `Participante` del contrato
 * @returns {HTMLElement}
 */
function crearBarra(participante) {
  const { jugador, heroe, esIA } = participante;

  const barra = document.createElement('div');
  barra.className = 'barra-vida';
  barra.dataset.barraVida = '';
  barra.dataset.jugador = jugador.id;

  // Se marca a la IA porque el requisito permite que cualquier participante
  // de una partida de seis este controlado por la maquina, y quien mira la
  // pantalla necesita distinguirlo de una persona.
  if (esIA) barra.dataset.ia = 'true';

  const nombre = document.createElement('span');
  nombre.className = 'barra-vida__nombre';
  nombre.textContent = heroe.nombre;

  const pista = document.createElement('div');
  pista.className = 'barra-vida__pista';
  const relleno = document.createElement('div');
  relleno.className = 'barra-vida__relleno';
  pista.appendChild(relleno);

  const valor = document.createElement('span');
  valor.className = 'barra-vida__valor';

  barra.append(nombre, pista, valor);
  return barra;
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

  if (idPartida) contenedor.dataset.partida = idPartida;

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
  if (!evento || evento.tipo !== ACCION_RESUELTA) return;

  const propia = contenedor.dataset.partida;
  if (propia && evento.idPartida !== propia) return;

  for (const afectado of evento.afectados ?? []) {
    const barra = contenedor.querySelector(`[data-jugador="${afectado.idJugador}"]`);
    if (!barra) continue; // espectador, o participante ya retirado de la vista

    actualizar(barra, afectado.vidaActual, afectado.vidaMaxima);
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
