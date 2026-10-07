/**
 * Ritmo del combate — auditoría del 4-oct («cuando ataco, también me hago
 * daño a mí mismo») y revisión del modo jugador del 6-oct («apenas ataco yo,
 * inmediatamente ataca la IA… pasa muy rápido y no sé qué pasa»).
 *
 * El servidor resuelve tu golpe y, en la misma ráfaga, el contragolpe de la
 * máquina: los avisos llegan con milisegundos de diferencia. Si se pintaran
 * así, tu barra bajaría justo al pulsar «Atacar», como si el golpe fuera tuyo,
 * y el poder que gastaste volvería a llenarse antes de que lo vieras bajar.
 *
 * Aquí se reparte esa ráfaga al ritmo que una persona puede seguir, sin tocar
 * el servidor (nada de esperas en el backend):
 *
 *  1. Tu acción y el cambio de turno a la IA se ven al instante: el mando se
 *     cierra y el HUD dice «Turno de la IA».
 *  2. La acción de la IA (o de otro jugador) espera {@link PAUSA_ENTRE_ACCIONES_MS}
 *     desde la última acción que se vio: se lee tu golpe, tu poder gastado, y
 *     entonces llega el suyo.
 *  3. Lo que viene detrás de una acción ajena —el turno que vuelve a ti, con su
 *     +2 de poder, o los efectos por turno— espera {@link PAUSA_TRAS_AJENA_MS}
 *     más: el golpe de la IA se ve antes de que el mando se reabra.
 *
 * Una sola suscripción al canal de la partida reparte cada aviso, en el orden
 * en que llegó, a todos los que escuchan (panel de vidas, controles, campo):
 * todos ven lo mismo a la vez. No reordena, no descarta y no inventa nada;
 * solo espera. La verdad (vida, poder, turno) sigue siendo la del servidor.
 *
 * @module ritmo-de-combate
 */

/** `tipo` del aviso de una acción resuelta (contracts/websocket/salas-partidas.yaml). */
const ACCION_RESUELTA = 'partida.accion.resuelta';

/** Pausa entre la última acción vista y la siguiente que no es tuya (2–3 s perceptuales). */
export const PAUSA_ENTRE_ACCIONES_MS = 2200;

/** Pausa tras una acción ajena antes de lo que la sigue (el turno que vuelve, los efectos). */
export const PAUSA_TRAS_AJENA_MS = 900;

/**
 * Envuelve el `suscribir` del canal de una partida para repartir sus avisos
 * con ritmo.
 *
 * @param {(alRecibir: (aviso: object) => void) => unknown} suscribir
 *   el de `suscripcionDePartida`: entrega cada mensaje del tema de la partida
 * @param {object} [opciones]
 * @param {string|null} [opciones.yo] quien mira: sus acciones no esperan
 * @param {number} [opciones.pausaMs] pausa antes de una acción ajena
 * @param {number} [opciones.pausaTrasAjenaMs] pausa tras una acción ajena (por omisión, la menor de las dos)
 * @param {() => number} [opciones.ahora] reloj, para las pruebas
 * @param {(fn: () => void, ms: number) => unknown} [opciones.programar] temporizador, para las pruebas
 * @returns {(alRecibir: (aviso: object) => void) => void} un `suscribir` con la misma forma
 */
export function suscripcionConRitmo(suscribir, opciones = {}) {
  if (typeof suscribir !== 'function') {
    return suscribir;
  }
  const {
    yo = null,
    pausaMs = PAUSA_ENTRE_ACCIONES_MS,
    ahora = () => Date.now(),
    programar = (fn, ms) => setTimeout(fn, ms),
  } = opciones;
  const pausaTrasAjenaMs = opciones.pausaTrasAjenaMs ?? Math.min(PAUSA_TRAS_AJENA_MS, pausaMs);

  const oyentes = [];
  const cola = [];
  let suscrito = false;
  let esperando = false;
  let ultimaAccion = -Infinity;
  let ultimaAjena = -Infinity;
  /** Si lo último que se vio fue una acción ajena: lo siguiente espera a que se lea. */
  let trasAjena = false;

  const esAccion = (aviso) => aviso?.tipo === ACCION_RESUELTA;
  const esAjena = (aviso) => esAccion(aviso) && aviso.idEjecutor !== yo;

  function repartir(aviso) {
    for (const oyente of oyentes.slice()) {
      try {
        oyente(aviso);
      } catch (error) {
        // Un oyente roto no deja sin avisos a los demás.
        console.error('ritmo-de-combate: un oyente falló', error);
      }
    }
  }

  /** Cuánto falta para poder enseñar este aviso; 0 si ya se puede. */
  function faltaPara(aviso) {
    if (esAjena(aviso)) {
      return ultimaAccion + pausaMs - ahora();
    }
    if (trasAjena && !esAccion(aviso)) {
      return ultimaAjena + pausaTrasAjenaMs - ahora();
    }
    return 0;
  }

  function avanzar() {
    while (!esperando && cola.length > 0) {
      const aviso = cola[0];
      const falta = faltaPara(aviso);
      if (falta > 0) {
        esperando = true;
        programar(() => {
          esperando = false;
          avanzar();
        }, falta);
        return;
      }
      cola.shift();
      if (esAccion(aviso)) {
        ultimaAccion = ahora();
        trasAjena = esAjena(aviso);
        if (trasAjena) {
          ultimaAjena = ultimaAccion;
        }
      }
      repartir(aviso);
    }
  }

  return function suscribirConRitmo(alRecibir) {
    if (typeof alRecibir === 'function') {
      oyentes.push(alRecibir);
    }
    if (!suscrito) {
      suscrito = true;
      suscribir((aviso) => {
        cola.push(aviso);
        avanzar();
      });
    }
  };
}
