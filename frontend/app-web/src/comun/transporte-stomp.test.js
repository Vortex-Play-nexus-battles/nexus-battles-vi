/**
 * Transporte STOMP: frames y ciclo de conexion, con un WebSocket falso.
 */

import { jest } from '@jest/globals';

import { armarFrame, leerFrame, conectarStomp } from './transporte-stomp.js';

const NUL = '\u0000';

/** WebSocket falso: registra lo enviado y deja simular al servidor. */
class SocketFalso {
  static ultimo = null;

  constructor(url) {
    this.url = url;
    this.enviados = [];
    this.cerrado = false;
    SocketFalso.ultimo = this;
  }

  send(texto) {
    this.enviados.push(leerFrame(texto));
  }

  close() {
    this.cerrado = true;
    this.onclose?.();
  }

  abrir() {
    this.onopen?.();
  }

  recibir(comando, cabeceras = {}, cuerpo = '') {
    this.onmessage?.({ data: armarFrame(comando, cabeceras, cuerpo) });
  }
}

describe('frames', () => {
  test('armarFrame produce un frame STOMP 1.2 terminado en NUL', () => {
    expect(armarFrame('SEND', { destination: '/app/x' }, '{"a":1}')).toBe(
      `SEND\ndestination:/app/x\n\n{"a":1}${NUL}`,
    );
  });

  test('leerFrame lee comando, cabeceras y cuerpo, y devuelve null para latidos', () => {
    expect(
      leerFrame(`MESSAGE\nsubscription:sub-1\ndestination:/usuario/cola/x\n\n{"id":"1"}${NUL}`),
    ).toEqual({
      comando: 'MESSAGE',
      cabeceras: { subscription: 'sub-1', destination: '/usuario/cola/x' },
      cuerpo: '{"id":"1"}',
    });
    expect(leerFrame('\n')).toBeNull();
  });
});

describe('conectarStomp', () => {
  test('manda CONNECT con las cabeceras dadas y resuelve al recibir CONNECTED', async () => {
    const promesa = conectarStomp({
      url: 'ws://x/ws',
      cabeceras: { Authorization: 'Bearer t' },
      WebSocketImpl: SocketFalso,
    });
    const socket = SocketFalso.ultimo;
    socket.abrir();

    expect(socket.enviados[0]).toEqual({
      comando: 'CONNECT',
      cabeceras: { 'accept-version': '1.2', 'heart-beat': '0,0', Authorization: 'Bearer t' },
      cuerpo: '',
    });

    socket.recibir('CONNECTED', { version: '1.2' });
    const cliente = await promesa;
    expect(typeof cliente.suscribir).toBe('function');
  });

  test('sin cabeceras extra el CONNECT no lleva Authorization', async () => {
    const promesa = conectarStomp({ url: 'ws://x/ws', WebSocketImpl: SocketFalso });
    const socket = SocketFalso.ultimo;
    socket.abrir();
    expect(socket.enviados[0].cabeceras).not.toHaveProperty('Authorization');
    socket.recibir('CONNECTED');
    await promesa;
  });

  test('suscribir entrega los MESSAGE de su suscripcion ya parseados; enviar serializa JSON', async () => {
    const promesa = conectarStomp({ url: 'ws://x/ws', WebSocketImpl: SocketFalso });
    const socket = SocketFalso.ultimo;
    socket.abrir();
    socket.recibir('CONNECTED');
    const cliente = await promesa;

    const recibido = jest.fn();
    const id = cliente.suscribir('/usuario/cola/notificaciones', recibido);
    expect(socket.enviados[1]).toEqual({
      comando: 'SUBSCRIBE',
      cabeceras: { id, destination: '/usuario/cola/notificaciones' },
      cuerpo: '',
    });

    socket.recibir('MESSAGE', { subscription: id }, '{"noLeidas":2}');
    socket.recibir('MESSAGE', { subscription: 'otra' }, '{"noLeidas":9}');
    expect(recibido).toHaveBeenCalledTimes(1);
    expect(recibido.mock.calls[0][0]).toEqual({ noLeidas: 2 });

    cliente.enviar('/app/notificaciones/sesion', { usuarioId: 'u', sesionId: 's' });
    expect(socket.enviados[2]).toEqual({
      comando: 'SEND',
      cabeceras: { destination: '/app/notificaciones/sesion', 'content-type': 'application/json' },
      cuerpo: '{"usuarioId":"u","sesionId":"s"}',
    });
  });

  test('un ERROR antes de CONNECTED rechaza; despues, avisa por alError', async () => {
    const promesa = conectarStomp({ url: 'ws://x/ws', WebSocketImpl: SocketFalso });
    const socket = SocketFalso.ultimo;
    socket.abrir();
    socket.recibir('ERROR', { message: 'no autorizado' });
    await expect(promesa).rejects.toThrow('no autorizado');

    const promesa2 = conectarStomp({ url: 'ws://x/ws', WebSocketImpl: SocketFalso });
    const socket2 = SocketFalso.ultimo;
    socket2.abrir();
    socket2.recibir('CONNECTED');
    const cliente = await promesa2;
    const alError = jest.fn();
    cliente.alError = alError;
    socket2.recibir('ERROR', { message: 'algo' });
    expect(alError).toHaveBeenCalledTimes(1);
  });

  test('el cierre del socket antes de conectar rechaza; despues, avisa por alCerrar', async () => {
    const promesa = conectarStomp({ url: 'ws://x/ws', WebSocketImpl: SocketFalso });
    SocketFalso.ultimo.close();
    await expect(promesa).rejects.toThrow('No se pudo abrir el canal.');

    const promesa2 = conectarStomp({ url: 'ws://x/ws', WebSocketImpl: SocketFalso });
    const socket = SocketFalso.ultimo;
    socket.abrir();
    socket.recibir('CONNECTED');
    const cliente = await promesa2;
    const alCerrar = jest.fn();
    cliente.alCerrar = alCerrar;
    cliente.cerrar();
    expect(socket.cerrado).toBe(true);
    expect(alCerrar).toHaveBeenCalledTimes(1);
  });
});
