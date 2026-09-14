/**
 * HU-JUE-015 — Cliente STOMP del chat y del canal de salas.
 *
 * Habla con los destinos de `contracts/websocket/salas-partidas.yaml`. El
 * transporte STOMP 1.2 sobre WebSocket nativo (CONNECT, SUBSCRIBE, SEND,
 * MESSAGE, ERROR) es el compartido de `src/comun/transporte-stomp.js` (#351):
 * aqui ya no se arman ni se leen frames. Este modulo solo aporta lo que es
 * propio de salas-partidas:
 *
 *  1. El JWT en la cabecera `Authorization` del CONNECT, porque el navegador no
 *     puede mandar cabeceras en el handshake y el servidor lo exige ahi (#222):
 *     sin token no hay conexion.
 *  2. `ErrorDeCanal`: los errores de negocio no llegan como frames ERROR sino
 *     como mensajes por la cola privada `/usuario/cola/salas`, en formato
 *     problem details, igual que la API HTTP. La vista decide por `tipo` y
 *     `estado`, nunca por el texto (MAPEO-ERRORES.md, regla de oro).
 *
 * `armarFrame` y `leerFrame` se reexportan para no romper a quien ya los
 * importaba de aqui; la implementacion es la del transporte comun.
 */

import { armarFrame, conectarStomp, leerFrame } from '../../comun/transporte-stomp.js';

export { armarFrame, leerFrame };

/** Error de negocio recibido por la cola privada, ya interpretado. */
export class ErrorDeCanal extends Error {
  constructor(problema) {
    super(problema?.detail || problema?.title || 'El chat no pudo entregar el mensaje.');
    this.name = 'ErrorDeCanal';
    this.tipo = problema?.type ?? null;
    this.titulo = problema?.title ?? 'No se pudo enviar';
    this.detalle = this.message;
    this.estado = problema?.status ?? 0;
  }
}

/**
 * Abre el canal de salas-partidas y resuelve cuando el servidor acepta el
 * CONNECT.
 *
 * @param {{url: string, token: string, WebSocketImpl?: Function}} opciones
 *   `WebSocketImpl` es inyeccion para las pruebas.
 * @returns {Promise<{suscribir: Function, enviar: Function, cerrar: Function,
 *   alCerrar: Function|null, alError: Function|null}>}
 */
export function conectarChat({ url, token, WebSocketImpl }) {
  return conectarStomp({
    url,
    cabeceras: { Authorization: `Bearer ${token}` },
    WebSocketImpl,
  });
}
