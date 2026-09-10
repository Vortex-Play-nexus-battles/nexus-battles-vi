/**
 * Transporte STOMP 1.2 sobre WebSocket nativo, sin dependencia de dominio.
 *
 * Misma pieza que `cliente-chat.js` de HU-JUE-015 (Alexander), sacada de su
 * dominio: aqui no hay destinos de sala ni de notificaciones, solo los cinco
 * frames que un cliente necesita (CONNECT, SUBSCRIBE, SEND, MESSAGE, ERROR).
 * No hay libreria STOMP en el proyecto y agregar una es decision de equipo.
 *
 * Que va en el CONNECT lo decide quien conecta, por `cabeceras`. Hoy el
 * servicio de notificaciones identifica la conexion en el handshake
 * (`contracts/websocket/notificaciones.yaml`); el dia que el servicio pida el
 * JWT en el CONNECT, como ya lo hace salas-partidas en #222, se pasa
 * `{ Authorization: 'Bearer ...' }` y no cambia nada mas.
 *
 * Candidato a `frontend/app-web/src/comun/` cuando los tres Scrum Masters lo
 * acuerden: mientras tanto vive aqui para no bloquear la historia.
 */

const NUL = '\u0000';

/** Arma un frame STOMP 1.2. */
export function armarFrame(comando, cabeceras = {}, cuerpo = '') {
  const lineas = [
    comando,
    ...Object.entries(cabeceras).map(([clave, valor]) => `${clave}:${valor}`),
  ];
  return `${lineas.join('\n')}\n\n${cuerpo}${NUL}`;
}

/** Lee un frame STOMP. Devuelve null para los latidos (frames vacios). */
export function leerFrame(texto) {
  const crudo = String(texto);
  const limpio = crudo.endsWith(NUL) ? crudo.slice(0, -1) : crudo;
  if (limpio.trim() === '') {
    return null;
  }
  const [cabecera, ...resto] = limpio.split('\n\n');
  const [comando, ...lineas] = cabecera.split('\n');
  const cabeceras = {};
  for (const linea of lineas) {
    const separador = linea.indexOf(':');
    if (separador > 0) {
      cabeceras[linea.slice(0, separador)] = linea.slice(separador + 1);
    }
  }
  return { comando, cabeceras, cuerpo: resto.join('\n\n') };
}

/**
 * Abre el canal y resuelve cuando el servidor acepta el CONNECT.
 *
 * @param {{url: string, cabeceras?: Record<string, string>, WebSocketImpl?: Function}} opciones
 *   `cabeceras` se suman a las del CONNECT. `WebSocketImpl` es inyeccion para las pruebas.
 * @returns {Promise<{suscribir: Function, enviar: Function, cerrar: Function,
 *   alCerrar: Function|null, alError: Function|null}>}
 */
export function conectarStomp({ url, cabeceras = {}, WebSocketImpl = globalThis.WebSocket }) {
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
          ...cabeceras,
        }),
      );
    };

    socket.onmessage = (evento) => {
      const frame = leerFrame(evento.data);
      if (!frame) {
        return;
      }
      if (frame.comando === 'CONNECTED') {
        conectado = true;
        resolver(cliente);
        return;
      }
      if (frame.comando === 'MESSAGE') {
        const alRecibir = suscripciones.get(frame.cabeceras.subscription);
        if (alRecibir) {
          alRecibir(frame.cuerpo ? JSON.parse(frame.cuerpo) : null, frame.cabeceras);
        }
        return;
      }
      if (frame.comando === 'ERROR') {
        const error = new Error(frame.cabeceras.message || 'El canal rechazo la conexion.');
        if (!conectado) {
          rechazar(error);
        } else if (cliente.alError) {
          cliente.alError(error);
        }
      }
    };

    socket.onclose = () => {
      if (!conectado) {
        rechazar(new Error('No se pudo abrir el canal.'));
      } else if (cliente.alCerrar) {
        cliente.alCerrar();
      }
    };
  });
}
