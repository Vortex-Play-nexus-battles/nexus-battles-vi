/**
 * HU-JUE-015 — Cliente STOMP del chat.
 *
 * Se prueba con un WebSocket falso: que el CONNECT lleve el token, que las
 * suscripciones reciban solo lo suyo, y que un rechazo del servidor no deje
 * la promesa colgada.
 */

import { jest } from '@jest/globals';

import { armarFrame, leerFrame, conectarChat, ErrorDeCanal } from './cliente-chat.js';

class WebSocketFalso {
  static instancias = [];

  constructor(url) {
    this.url = url;
    this.enviados = [];
    this.cerrado = false;
    WebSocketFalso.instancias.push(this);
  }

  send(frame) {
    this.enviados.push(leerFrame(frame));
  }

  close() {
    this.cerrado = true;
    this.onclose?.();
  }

  /** El servidor "habla". */
  recibir(comando, cabeceras = {}, cuerpo = '') {
    this.onmessage({ data: armarFrame(comando, cabeceras, cuerpo) });
  }
}

beforeEach(() => {
  WebSocketFalso.instancias = [];
});

test('arma y lee un frame STOMP con cabeceras y cuerpo', () => {
  const frame = armarFrame('SEND', { destination: '/app/chat/general' }, '{"texto":"hola"}');

  const leido = leerFrame(frame);

  expect(frame.endsWith('\u0000')).toBe(true);
  expect(leido).toEqual({
    comando: 'SEND',
    cabeceras: { destination: '/app/chat/general' },
    cuerpo: '{"texto":"hola"}',
  });
  expect(leerFrame('\n')).toBeNull();
});

test('el CONNECT lleva el token y la promesa resuelve con el CONNECTED', async () => {
  const conexion = conectarChat({ url: 'ws://x/ws', token: 'abc', WebSocketImpl: WebSocketFalso });
  const socket = WebSocketFalso.instancias[0];
  socket.onopen();

  expect(socket.enviados[0].comando).toBe('CONNECT');
  expect(socket.enviados[0].cabeceras.Authorization).toBe('Bearer abc');

  socket.recibir('CONNECTED', { version: '1.2' });
  const cliente = await conexion;
  expect(typeof cliente.enviar).toBe('function');
});

test('cada suscripcion recibe solo los mensajes de su destino, ya como JSON', async () => {
  const conexion = conectarChat({ url: 'ws://x/ws', token: 'abc', WebSocketImpl: WebSocketFalso });
  const socket = WebSocketFalso.instancias[0];
  socket.onopen();
  socket.recibir('CONNECTED');
  const cliente = await conexion;
  const enSala = jest.fn();
  const enErrores = jest.fn();

  const idSala = cliente.suscribir('/tema/salas/1/chat', enSala);
  const idErrores = cliente.suscribir('/usuario/cola/salas', enErrores);
  socket.recibir('MESSAGE', { subscription: idSala }, '{"texto":"hola"}');
  socket.recibir('MESSAGE', { subscription: idErrores }, '{"status":422}');

  expect(enSala).toHaveBeenCalledWith({ texto: 'hola' }, expect.any(Object));
  expect(enErrores).toHaveBeenCalledWith({ status: 422 }, expect.any(Object));
  expect(enSala).toHaveBeenCalledTimes(1);
  expect(socket.enviados.filter((f) => f.comando === 'SUBSCRIBE')).toHaveLength(2);
});

test('enviar manda un SEND con el cuerpo en JSON al destino', async () => {
  const conexion = conectarChat({ url: 'ws://x/ws', token: 'abc', WebSocketImpl: WebSocketFalso });
  const socket = WebSocketFalso.instancias[0];
  socket.onopen();
  socket.recibir('CONNECTED');
  const cliente = await conexion;

  cliente.enviar('/app/chat/general', { texto: 'vamos', logro: null });

  const envio = socket.enviados.at(-1);
  expect(envio.comando).toBe('SEND');
  expect(envio.cabeceras.destination).toBe('/app/chat/general');
  expect(JSON.parse(envio.cuerpo)).toEqual({ texto: 'vamos', logro: null });
});

test('si el servidor rechaza el CONNECT, la promesa falla en vez de colgarse', async () => {
  const conexion = conectarChat({ url: 'ws://x/ws', token: 'malo', WebSocketImpl: WebSocketFalso });
  const socket = WebSocketFalso.instancias[0];
  socket.onopen();

  socket.recibir('ERROR', { message: 'El token de acceso no es valido.' });

  await expect(conexion).rejects.toThrow('El token de acceso no es valido.');
});

test('un problem details de la cola privada se interpreta por tipo y estado', () => {
  const error = new ErrorDeCanal({
    type: 'https://nexusbattles.local/errores/contenido-bloqueado',
    title: 'Mensaje bloqueado',
    status: 422,
    detail: 'El mensaje contiene terminos que no estan permitidos.',
  });

  expect(error.tipo).toBe('https://nexusbattles.local/errores/contenido-bloqueado');
  expect(error.estado).toBe(422);
  expect(error.titulo).toBe('Mensaje bloqueado');
});
