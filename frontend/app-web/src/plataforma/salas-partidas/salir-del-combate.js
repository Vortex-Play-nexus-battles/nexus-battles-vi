/**
 * «Salir» en pleno combate — revisión del modo jugador del 6-oct, punto 18:
 * el primer botón decía «Jugar online» y te sacaba de la batalla sin decir
 * qué pasaba con ella.
 *
 * Ahora, mientras sigues combatiendo, «Salir» pregunta antes con las
 * consecuencias exactas y, si confirmas, te rindes
 * (`POST /partidas/{id}/rendicion`, salas-partidas 1.10.0). Lo que pasa
 * después —quién gana, cómo se liquida la apuesta— lo decide el servidor con
 * las reglas de la partida; aquí no se manda ningún ganador.
 *
 * Fuera de combate (en la sala de espera, con la partida terminada o si ya
 * caíste) «Salir» es un enlace normal al listado: no hay nada que perder.
 *
 * @module salir-del-combate
 */

/** Lo que dice el diálogo, tal cual lo pidió la revisión. */
export const TEXTO_DE_ABANDONO =
  'Si abandonas la batalla se contará como derrota. Tu rival será declarado ganador y se ' +
  'liquidará la apuesta según las reglas de la partida.';

/**
 * ¿Quien mira sigue combatiendo en esta partida? Solo entonces salir es
 * rendirse.
 *
 * @param {{estado?: string, participantes?: Array<{jugador: string, esIA?: boolean,
 *   heroe?: {vidaActual?: number}}>}|null} partida
 * @param {string|null} yo
 * @param {{terminada?: boolean, caido?: boolean}} [vivo] lo que el canal ya dijo
 * @returns {boolean}
 */
export function sigueCombatiendo(partida, yo, { terminada = false, caido = false } = {}) {
  if (!partida || !yo || terminada || caido || partida.estado !== 'EN_CURSO') {
    return false;
  }
  const propio = (partida.participantes ?? []).find(
    (participante) => participante?.jugador === yo && !participante.esIA,
  );
  if (!propio) {
    return false;
  }
  const vida = propio.heroe?.vidaActual;
  return typeof vida !== 'number' || vida > 0;
}

/**
 * Engancha «Salir» del HUD del combate.
 *
 * @param {HTMLElement|null} enlace `[data-zona="salir"]`
 * @param {object} opciones
 * @param {() => {partida: object|null, terminada?: boolean, caido?: boolean}} opciones.situacion
 *   lo que se sabe ahora mismo de la partida
 * @param {string|null} opciones.yo
 * @param {(opciones: object) => Promise<boolean>} opciones.confirmar el diálogo del kit
 * @param {(idPartida: string) => Promise<unknown>} opciones.rendirse
 * @param {() => void} opciones.alAbandonar después de rendirse
 * @param {(error: unknown) => void} [opciones.alFallar]
 * @returns {boolean} true si se enganchó
 */
export function montarSalidaDelCombate(
  enlace,
  { situacion, yo, confirmar, rendirse, alAbandonar, alFallar = () => {} },
) {
  if (!enlace) {
    return false;
  }
  /** La salida en marcha, si la hay: un segundo clic no abre otro diálogo. */
  let enCurso = null;

  async function salir(partida) {
    try {
      const seguro = await confirmar({
        titulo: '¿Abandonar la batalla?',
        mensaje: TEXTO_DE_ABANDONO,
        textoConfirmar: 'Abandonar batalla',
        textoCancelar: 'Seguir jugando',
        peligro: true,
      });
      if (!seguro) {
        return;
      }
      await rendirse(partida.id);
      alAbandonar();
    } catch (error) {
      alFallar(error);
    }
  }

  enlace.addEventListener('click', (evento) => {
    const { partida, terminada = false, caido = false } = situacion() ?? {};
    if (!sigueCombatiendo(partida, yo, { terminada, caido })) {
      // Nada que perder: el enlace lleva al listado como siempre.
      return;
    }
    evento.preventDefault();
    if (!enCurso) {
      enCurso = salir(partida).finally(() => {
        enCurso = null;
      });
    }
  });
  return true;
}
