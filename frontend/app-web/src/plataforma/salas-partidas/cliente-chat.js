/**
 * HU-JUE-015 — Cliente STOMP del chat sobre WebSocket nativo.
 *
 * Habla con los destinos de `contracts/websocket/salas-partidas.yaml`. No hay
 * ninguna libreria de STOMP en el proyecto y agregar una es decision de
 * equipo, asi que aqui se arman y se leen los cuatro frames que el chat
 * necesita: CONNECT, SUBSCRIBE, SEND y MESSAGE (mas ERROR). Es poco, y queda
 * probado.
 *
 * El JWT viaja en la cabecera Authorization del CONNECT, porque el navegador
 * no puede mandar cabeceras en el handshake. El servidor lo exige: sin token
 * no hay conexion.
 *
 * Los errores de negocio no llegan como frames ERROR sino como mensajes por
 * la cola privada `/usuario/cola/salas`, en formato problem details, igual que
 * la API HTTP. La vista los recibe como `ErrorDeCanal` y decide por `tipo` y
 * `estado`, nunca por el texto (MAPEO-ERRORES.md, regla de oro).
 */

const NUL = '\u0000';

/** Arma un frame STOMP 1.2. */
export function armarFrame(comando, cabeceras = {}, cuerpo = '') {
  const lineas = [comando, ...Object.entries(cabeceras).map(([clave, valor]) => `${clave}:${valor}`)];
  return `${lineas.join('\n')}\n\n${cuerpo}${NUL}`;
}

/** Lee un frame STOMP. Devuelve null para los latidos (frames vacios). */
export function leerFrame(texto) {
  const limpio = String(texto).replace(/\u0000$/, '');
  if (limpio.trim() === '') return null;
  const [cabecera, ...resto] = limpio.split('\n\n');
  const [comando, ...lineas] = cabecera.split('\n');
  const cabeceras = {};
  for (const linea of lineas) {
    const separador = linea.indexOf(':');
    if (separador > 0) cabeceras[linea.slice(0, separador)] = linea.slice(separador + 1);
  }
  return { comando, cabeceras, cuerpo: resto.join('\n\n') };
}

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
 * Abre el canal y resuelve cuando el servidor acepta el CONNECT.
 *
 * @param {{url: string, token: string, WebSocketImpl?: Function}} opciones
 *   `WebSocketImpl` es inyeccion para las pruebas.
 * @returns {Promise<{suscribir: Function, enviar: Function, cerrar: Function,
 *   alCerrar: Function|null, alError: Function|null}>}
 */
export function conectarChat({ url, token, WebSocketImpl = globalThis.WebSocket }) {
  return new Promise((resolver, rechazar) => {
    const socket = new WebSocketImpl(url);
    const suscripciones = new Map();
    let contador = 0;
    let conectado = false;

    const cliente = {
      alCerrar: null,
      alError: null,
      suscribir(destino, alRecibir) {
        const id = `sub-${++contador}`;
        suscripciones.set(id, alRecibir);
        socket.send(armarFrame('SUBSCRIBE', { id, destination: destino }));
        return id;
      },
      enviar(destino, cuerpo) {
        socket.send(
          armarFrame(
            'SEND',
            { destination: destino, 'content-type': 'application/json' },
            JSON.stringify(cuerpo),
          ),
        );
      },
      cerrar() {
        socket.close();
      },
    };

    socket.onopen = () => {
      socket.send(
        armarFrame('CONNECT', {
          'accept-version': '1.2',
          'heart-beat': '0,0',
          Authorization: `Bearer ${token}`,
        }),
      );
    };

    socket.onmessage = (evento) => {
      const frame = leerFrame(evento.data);
      if (!frame) return;
      if (frame.comando === 'CONNECTED') {
        conectado = true;
        resolver(cliente);
        return;
      }
      if (frame.comando === 'MESSAGE') {
        const alRecibir = suscripciones.get(frame.cabeceras.subscription);
        if (alRecibir) alRecibir(frame.cuerpo ? JSON.parse(frame.cuerpo) : null, frame.cabeceras);
        return;
      }
      if (frame.comando === 'ERROR') {
        const error = new Error(frame.cabeceras.message || 'El canal rechazo la conexion.');
        if (!conectado) rechazar(error);
        else if (cliente.alError) cliente.alError(error);
      }
    };

    socket.onclose = () => {
      if (!conectado) rechazar(new Error('No se pudo abrir el canal del chat.'));
      else if (cliente.alCerrar) cliente.alCerrar();
    };
  });
}
