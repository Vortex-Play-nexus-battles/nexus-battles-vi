/**
 * Canal STOMP simulado para el laboratorio visual — UX-GAME-4.
 *
 * El combate solo monta sus controles cuando hay canal (sin canal no se puede
 * jugar el turno, y la vista lo dice). Sin servicios el WebSocket no abre, y
 * el laboratorio nunca fotografiaba «Es tu turno», los botones de ataque ni
 * el panel de resultado. Este módulo intercepta el WebSocket con Playwright
 * (`page.routeWebSocket`) y hace de servidor STOMP mínimo:
 *
 *   - a `CONNECT` responde `CONNECTED`;
 *   - a cada `SUBSCRIBE` le apunta el id, y si hay mensajes preparados para
 *     ese destino, los manda como `MESSAGE` (uno detrás de otro).
 *
 * No simula el motor de combate: no responde a `SEND`. Lo que se fotografía
 * es lo que la vista pinta con lo que el servidor le mandaría; la lógica del
 * turno sigue siendo del servidor.
 *
 * @param {import('@playwright/test').Page} pagina
 * @param {{mensajes?: Record<string, object[]>}} [opciones] mensajes por
 *   destino, tal como los publica `contracts/websocket/salas-partidas.yaml`.
 */
export async function simularCanal(pagina, { mensajes = {} } = {}) {
  await pagina.routeWebSocket(/\/ws(\?.*)?$/, (ws) => {
    ws.onMessage((crudo) => {
      const texto = typeof crudo === 'string' ? crudo : String(crudo);
      const frame = leerFrame(texto);
      if (!frame) {
        return;
      }
      if (frame.comando === 'CONNECT') {
        ws.send(armarFrame('CONNECTED', { version: '1.2' }));
        return;
      }
      if (frame.comando === 'SUBSCRIBE') {
        const destino = frame.cabeceras.destination;
        for (const cuerpo of mensajes[destino] ?? []) {
          ws.send(
            armarFrame(
              'MESSAGE',
              {
                subscription: frame.cabeceras.id,
                destination: destino,
                'message-id': `m-${Math.random().toString(36).slice(2)}`,
                'content-type': 'application/json',
              },
              JSON.stringify(cuerpo),
            ),
          );
        }
      }
    });
  });
}

function armarFrame(comando, cabeceras, cuerpo = '') {
  const lineas = Object.entries(cabeceras).map(([k, v]) => `${k}:${v}`);
  return `${comando}\n${lineas.join('\n')}\n\n${cuerpo}\0`;
}

function leerFrame(texto) {
  const limpio = texto.replace(/\0$/, '');
  const [cabeza, ...resto] = limpio.split('\n\n');
  const [comando, ...lineas] = cabeza.split('\n');
  if (!comando) {
    return null;
  }
  const cabeceras = {};
  for (const linea of lineas) {
    const i = linea.indexOf(':');
    if (i > 0) {
      cabeceras[linea.slice(0, i)] = linea.slice(i + 1);
    }
  }
  return { comando, cabeceras, cuerpo: resto.join('\n\n') };
}
