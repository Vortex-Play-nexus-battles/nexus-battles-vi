/**
 * HU-SAL-002 · RF-JUE-002 — Canal en tiempo real de una sala.
 *
 * Tercer criterio del issue #30: «el estado de la sala se actualiza para todos
 * los participantes». Quien ya esta dentro se entera de que entro alguien sin
 * recargar.
 *
 * Escucha el canal `salaEstado` de `contracts/websocket/salas-partidas.yaml`,
 * en el destino `/tema/salas/{idSala}`, y procesa el mensaje
 * `sala.participante.ingreso`.
 *
 * NO trae cliente STOMP propio. `suscribir` se inyecta, igual que en
 * `sala-batalla.js` de HU-SAL-005: mientras no exista una vista que necesite
 * conectarse de verdad desde el navegador, montar aqui una biblioteca de
 * transporte seria adelantar una decision que todavia no toca. Sin `suscribir`
 * el modulo no finge nada: simplemente no escucha.
 *
 * Tampoco procesa el chat de HU-JUE-015: ese vive en `/tema/salas/{id}/chat`,
 * es de otro dueño y no se toca desde aqui.
 */

/** Discriminador del mensaje, fijado por el AsyncAPI. */
export const TIPO_INGRESO = 'sala.participante.ingreso';

/**
 * Destino del canal `salaEstado`. Espejo de `CanalDeSalaStomp.destinoDe`.
 *
 * @param {string} idSala
 * @returns {string}
 */
export function destinoDeSala(idSala) {
  return `/tema/salas/${idSala}`;
}

/**
 * Aplica un aviso al estado local y devuelve el estado resultante.
 *
 * Funcion pura: no toca el DOM ni la red, para que la regla de actualizacion se
 * pueda probar sin navegador y sin servidor.
 *
 * Descarta lo que no le corresponde en vez de romperse: un mensaje de otro tipo
 * -el chat comparte prefijo de canal-, uno de otra sala, o un ingreso repetido.
 * Lo ultimo importa: al reconectar puede llegar dos veces el mismo aviso, y
 * contar dos veces al mismo jugador dejaria una ocupacion imposible.
 *
 * @param {{idSala: string, ocupacion: {actual: number, maximo: number}, participantes: string[]}} estado
 * @param {object} aviso mensaje recibido por el canal
 * @returns {object} el estado actualizado, o el mismo objeto si el aviso no aplica
 */
export function aplicarAviso(estado, aviso) {
  if (!aviso || aviso.tipo !== TIPO_INGRESO) {
    return estado;
  }
  if (aviso.idSala !== estado.idSala) {
    return estado;
  }
  if (estado.participantes.includes(aviso.idJugador)) {
    return estado;
  }

  return {
    ...estado,
    ocupacion: { ...aviso.ocupacion },
    participantes: [...estado.participantes, aviso.idJugador],
  };
}

/**
 * Se suscribe al canal de una sala y mantiene el estado local al dia.
 *
 * @param {object} estadoInicial estado de la sala tal como lo devolvio la API
 * @param {object} [opciones]
 * @param {(destino: string, alRecibir: (aviso: object) => void) => void} [opciones.suscribir]
 * @param {(estado: object) => void} [opciones.alCambiar] se invoca solo cuando el estado cambia
 * @returns {{estado: () => object, recibir: (aviso: object) => void, conectado: boolean}}
 */
export function seguirSala(estadoInicial, { suscribir, alCambiar = () => {} } = {}) {
  let estado = estadoInicial;

  const recibir = (aviso) => {
    const siguiente = aplicarAviso(estado, aviso);
    if (siguiente === estado) {
      return;
    }
    estado = siguiente;
    alCambiar(estado);
  };

  const conectado = typeof suscribir === 'function';
  if (conectado) {
    suscribir(destinoDeSala(estadoInicial.idSala), recibir);
  }

  return { estado: () => estado, recibir, conectado };
}
