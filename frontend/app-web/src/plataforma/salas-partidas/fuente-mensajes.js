/**
 * Mensajes privados entre jugadores — el puerto de la interfaz (UXC-6).
 *
 * ## Por qué es un puerto y no un cliente HTTP
 *
 * RF-JUE-015 pide chat **de la sala y de la vista general**, y es lo único
 * que publican los contratos (`contracts/websocket/salas-partidas.yaml`:
 * `/tema/salas/{idSala}/chat` y `/tema/chat/general`). Ningún requisito del
 * documento del curso ni del SRS pide conversaciones entre dos jugadores, y
 * ningún servicio las guarda ni las entrega.
 *
 * La retroalimentación del docente pide la experiencia completa (lista de
 * conversaciones, buscar jugador, abrir conversación, enviados y recibidos,
 * estados de carga, error, reconexión y bloqueo). La interfaz está hecha
 * contra ESTE puerto, que describe lo que necesita; hoy responde
 * `disponible: false` sin tocar la red y la vista lo dice. No se inventa
 * ninguna ruta: el día que el PO lo convierta en requisito con dueño y
 * contrato, se escribe un adaptador que cumpla esta forma y la vista no cambia.
 *
 * El laboratorio visual sustituye este archivo por
 * `tests/visual/laboratorio/fuente-mensajes.js` para fotografiar los estados.
 *
 * @module plataforma/salas-partidas/fuente-mensajes
 */

/**
 * @typedef {object} JugadorDeMensajes
 * @property {string} id `uid` del jugador
 * @property {string} apodo
 */

/**
 * @typedef {'ACTIVA'|'BLOQUEADA'|'NO_ADMITE'|'CUENTA_SANCIONADA'} EstadoDeConversacion
 *   ACTIVA: se puede escribir. BLOQUEADA: bloqueaste tú al otro jugador.
 *   NO_ADMITE: el otro no recibe mensajes tuyos (no se dice por qué).
 *   CUENTA_SANCIONADA: la cuenta del otro está suspendida o baneada.
 */

/**
 * @typedef {object} ResumenDeConversacion
 * @property {string} id
 * @property {JugadorDeMensajes} con el otro jugador
 * @property {{texto: string, enviadoEn: string, deMi: boolean}|null} ultimo
 * @property {number} noLeidos
 * @property {EstadoDeConversacion} estado
 */

/**
 * @typedef {object} MensajePrivado
 * @property {string} id
 * @property {'mensaje'|'sistema'} tipo
 * @property {JugadorDeMensajes|null} autor null en los del sistema
 * @property {string} texto
 * @property {string} enviadoEn ISO-8601
 * @property {'ENVIADO'|'LEIDO'} [entrega] solo en los tuyos
 */

/**
 * @typedef {object} RestriccionDeMensajes
 * @property {'SILENCIO'} tipo
 * @property {string|null} hasta ISO-8601; null si no vence
 * @property {string|null} motivo
 */

/**
 * @typedef {object} BandejaDeMensajes
 * @property {ResumenDeConversacion[]} conversaciones de la más reciente a la más vieja
 * @property {RestriccionDeMensajes|null} restriccion tu silencio, si lo hay
 */

/**
 * @typedef {{tipo: 'mensaje', conversacionId: string, mensaje: MensajePrivado}
 *   | {tipo: 'leido', conversacionId: string, hasta: string}
 *   | {tipo: 'canal', estado: 'conectado'|'reconectando'|'reconectado'|'sin-conexion',
 *      intento?: number, de?: number}} EventoDeMensajes
 */

/**
 * @typedef {object} FuenteDeMensajes
 * @property {boolean} disponible
 * @property {() => Promise<BandejaDeMensajes>} bandeja
 * @property {(texto: string) => Promise<JugadorDeMensajes[]>} buscarJugadores
 * @property {(jugadorId: string) => Promise<ResumenDeConversacion>} conversacionCon
 *   abre (o crea) la conversación con un jugador
 * @property {(conversacionId: string) => Promise<{mensajes: MensajePrivado[]}>} hilo
 * @property {(conversacionId: string, texto: string) => Promise<MensajePrivado>} enviar
 * @property {(conversacionId: string) => Promise<void>} marcarLeida
 * @property {(jugadorId: string, bloquear: boolean) => Promise<ResumenDeConversacion>} bloquear
 * @property {(alEvento: (evento: EventoDeMensajes) => void) => () => void} escuchar
 *   devuelve cómo dejar de escuchar
 * @property {() => void} [reintentar] otra ronda de conexión, a mano
 */

/** Lo que responde cada operación mientras no haya servicio. */
export class MensajesSinAbrir extends Error {
  constructor() {
    super('Los mensajes privados todavía no tienen servicio.');
    this.name = 'MensajesSinAbrir';
  }
}

const rechazar = async () => {
  throw new MensajesSinAbrir();
};

/** @type {FuenteDeMensajes} */
export const FUENTE_SIN_SERVICIO = Object.freeze({
  disponible: false,
  bandeja: rechazar,
  buscarJugadores: rechazar,
  conversacionCon: rechazar,
  hilo: rechazar,
  enviar: rechazar,
  marcarLeida: rechazar,
  bloquear: rechazar,
  escuchar: () => () => {},
});

/**
 * La fuente de mensajes privados de este despliegue.
 *
 * @returns {FuenteDeMensajes}
 */
export function fuenteDeMensajes() {
  return FUENTE_SIN_SERVICIO;
}
