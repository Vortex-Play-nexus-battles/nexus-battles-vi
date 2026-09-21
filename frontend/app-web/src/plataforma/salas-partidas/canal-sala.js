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
 * NO trae cliente STOMP propio. `suscribir` se inyecta: el transporte STOMP
 * sobre WebSocket nativo que fija la pila ya existe en este mismo servicio
 * (`cliente-chat.js`, HU-JUE-015: CONNECT con `Authorization: Bearer`,
 * SUBSCRIBE, SEND, MESSAGE, ERROR) y la vista de Batallas lo conecta y le pasa
 * `suscribir` a este modulo. Aqui solo vive la regla de actualizacion, que es
 * lo unico propio de la sala. Sin `suscribir` el modulo no finge nada:
 * simplemente no escucha.
 *
 * Tampoco procesa el chat de HU-JUE-015: ese vive en `/tema/salas/{id}/chat`,
 * es de otro dueño y no se toca desde aqui.
 */

/** Discriminador del mensaje, fijado por el AsyncAPI. */
export const TIPO_INGRESO = 'sala.participante.ingreso';

/**
 * Arranque del combate — HU-SAL-004. Llega por el canal de la SALA y no solo
 * por el de la partida porque quien espera aqui todavia no conoce el
 * identificador de la partida y no puede estar suscrito a su tema.
 */
export const TIPO_PARTIDA_INICIADA = 'sala.partida.iniciada';

/** Un participante se fue antes de empezar — HU-SAL-006. Baja la ocupacion. */
export const TIPO_SALIDA = 'sala.participante.salio';

/**
 * El anfitrion cancelo la sala — HU-SAL-006. No cambia la ocupacion: cambia a
 * que pantalla pertenece la persona (vuelve al listado). Lo decide la vista
 * con `alCancelar`, igual que el arranque con `alIniciarPartida`.
 */
export const TIPO_CANCELADA = 'sala.cancelada';

/**
 * Motivos de `sala.cancelada` del AsyncAPI, traducidos para quien mira. El
 * mensaje viaja como enumerado a proposito: el idioma lo pone la interfaz.
 */
export const MOTIVOS_DE_CANCELACION = Object.freeze({
  CANCELADA_POR_ANFITRION: 'El anfitrion cancelo la sala.',
  INACTIVIDAD: 'La sala se cerro por inactividad.',
  ERROR_DEL_SISTEMA: 'La sala se cerro por un error del sistema.',
});

/**
 * Texto para quien es devuelto al listado por una cancelacion.
 *
 * @param {{motivo?: string, creditosDevueltos?: number|null}} aviso
 * @returns {string}
 */
export function textoDeCancelacion(aviso) {
  const motivo = MOTIVOS_DE_CANCELACION[aviso?.motivo] ?? 'La sala se cerro.';
  const devueltos = Number(aviso?.creditosDevueltos);
  if (Number.isFinite(devueltos) && devueltos > 0) {
    return `${motivo} Se te devolvieron ${devueltos} creditos.`;
  }
  return motivo;
}

/**
 * URL del canal STOMP del servicio de salas, `/ws` en el mismo origen que la
 * API (`contracts/websocket/salas-partidas.yaml`).
 *
 * Misma logica de origen que `baseDeApi()` en `cliente-salas.js`: en la
 * ejecucion integrada la API y el canal viven donde vive la pagina; en la
 * revision estatica, en la base que declare `<meta name="nexus-api-base">`.
 * El token NO va en esta URL: viaja en la cabecera del CONNECT.
 *
 * @param {{base?: string, location?: {protocol: string, host: string}}} [opciones]
 * @returns {string}
 */
export function urlDelCanal({ base = '', location = globalThis.location } = {}) {
  if (base) {
    return `${base.replace(/^http/, 'ws').replace(/\/+$/, '')}/ws`;
  }
  const protocolo = location?.protocol === 'https:' ? 'wss' : 'ws';
  return `${protocolo}://${location?.host ?? 'localhost:8084'}/ws`;
}

/**
 * Estado local de una sala a partir de la ficha que devuelve `GET /salas`.
 *
 * El listado trae la ocupacion como numero y los participantes como lista de
 * identificadores; el canal habla de `ocupacion: {actual, maximo}`. Aqui se
 * traduce una sola vez, para que `aplicarAviso` reciba siempre la misma forma.
 *
 * @param {{id: string, ocupacion: number, maximoParticipantes: number, participantes?: string[]}} sala
 * @returns {{idSala: string, ocupacion: {actual: number, maximo: number}, participantes: string[]}}
 */
export function estadoDesdeFicha(sala) {
  return {
    idSala: sala.id,
    ocupacion: { actual: sala.ocupacion, maximo: sala.maximoParticipantes },
    participantes: Array.isArray(sala.participantes) ? [...sala.participantes] : [],
  };
}

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
 * El arranque del combate NO se procesa aqui: no cambia la ocupacion ni quien
 * esta dentro, cambia a que pantalla pertenece la persona. Eso lo decide la
 * vista, con `alIniciarPartida`.
 *
 * @param {{idSala: string, ocupacion: {actual: number, maximo: number}, participantes: string[]}} estado
 * @param {object} aviso mensaje recibido por el canal
 * @returns {object} el estado actualizado, o el mismo objeto si el aviso no aplica
 */
export function aplicarAviso(estado, aviso) {
  if (!aviso || aviso.idSala !== estado.idSala) {
    return estado;
  }

  if (aviso.tipo === TIPO_INGRESO) {
    if (estado.participantes.includes(aviso.idJugador)) {
      return estado;
    }
    return {
      ...estado,
      ocupacion: { ...aviso.ocupacion },
      participantes: [...estado.participantes, aviso.idJugador],
    };
  }

  if (aviso.tipo === TIPO_SALIDA) {
    // Simetrico del ingreso (HU-SAL-006): quien ya no esta, no se quita dos
    // veces al reconectar.
    if (!estado.participantes.includes(aviso.idJugador)) {
      return estado;
    }
    return {
      ...estado,
      ocupacion: { ...aviso.ocupacion },
      participantes: estado.participantes.filter((id) => id !== aviso.idJugador),
    };
  }

  return estado;
}

/**
 * Se suscribe al canal de una sala y mantiene el estado local al dia.
 *
 * @param {object} estadoInicial estado de la sala tal como lo devolvio la API
 * @param {object} [opciones]
 * @param {(destino: string, alRecibir: (aviso: object) => void) => void} [opciones.suscribir]
 * @param {(estado: object) => void} [opciones.alCambiar] se invoca solo cuando el estado cambia
 * @param {(aviso: object) => void} [opciones.alIniciarPartida] se invoca una sola vez,
 *        cuando el anfitrion arranca el combate de ESTA sala
 * @param {(aviso: object) => void} [opciones.alCancelar] se invoca una sola vez,
 *        cuando ESTA sala se cancela (HU-SAL-006)
 * @returns {{estado: () => object, recibir: (aviso: object) => void, conectado: boolean}}
 */
export function seguirSala(
  estadoInicial,
  { suscribir, alCambiar = () => {}, alIniciarPartida = () => {}, alCancelar = () => {} } = {},
) {
  let estado = estadoInicial;
  let yaArranco = false;
  let yaCancelada = false;

  const recibir = (aviso) => {
    if (aviso?.tipo === TIPO_PARTIDA_INICIADA && aviso.idSala === estado.idSala) {
      // Una sola vez: al reconectar puede repetirse el aviso, y mandar dos
      // veces a la misma persona al combate le borraria lo que estuviera
      // haciendo en la vista de batalla.
      if (!yaArranco) {
        yaArranco = true;
        alIniciarPartida(aviso);
      }
      return;
    }
    if (aviso?.tipo === TIPO_CANCELADA && aviso.idSala === estado.idSala) {
      // Mismo criterio: devolver dos veces al listado no aporta nada.
      if (!yaCancelada) {
        yaCancelada = true;
        alCancelar(aviso);
      }
      return;
    }

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
