/**
 * Ritmo del combate — auditoría del 4-oct («cuando ataco, también me hago
 * daño a mí mismo»).
 *
 * El servidor resuelve tu golpe y, en la misma ráfaga, el contragolpe de la
 * máquina: los dos avisos llegaban con milisegundos de diferencia y tu barra
 * bajaba justo al pulsar «Atacar», como si el golpe fuera tuyo. Aquí se deja
 * una pausa antes de cada acción que no es tuya, para que cada golpe se vea
 * por separado: primero el tuyo, después el de la máquina o el del rival.
 *
 * Una sola suscripción al canal de la partida reparte cada aviso, en el orden
 * en que llegó, a todos los que escuchan (panel de vidas, presentación,
 * controles): todos ven lo mismo a la vez. No reordena, no descarta y no
 * inventa nada; solo espera.
 *
 * @module ritmo-de-combate
 */

/** `tipo` del aviso de una acción resuelta (contracts/websocket/salas-partidas.yaml). */
const ACCION_RESUELTA = 'partida.accion.resuelta';

/** Pausa entre tu acción y la siguiente que no es tuya. */
export const PAUSA_ENTRE_ACCIONES_MS = 700;

/**
 * Envuelve el `suscribir` del canal de una partida para repartir sus avisos
 * con ritmo.
 *
 * @param {(alRecibir: (aviso: object) => void) => unknown} suscribir
 *   el de `suscripcionDePartida`: entrega cada mensaje del tema de la partida
 * @param {object} [opciones]
 * @param {string|null} [opciones.yo] quien mira: sus acciones no esperan
 * @param {number} [opciones.pausaMs] pausa antes de una acción ajena
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

  const oyentes = [];
  const cola = [];
  let suscrito = false;
  let esperando = false;
  let ultimaAccion = -Infinity;

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

  function avanzar() {
    while (!esperando && cola.length > 0) {
      const aviso = cola[0];
      if (esAjena(aviso)) {
        const falta = ultimaAccion + pausaMs - ahora();
        if (falta > 0) {
          esperando = true;
          programar(() => {
            esperando = false;
            avanzar();
          }, falta);
          return;
        }
      }
      cola.shift();
      if (esAccion(aviso)) {
        ultimaAccion = ahora();
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
