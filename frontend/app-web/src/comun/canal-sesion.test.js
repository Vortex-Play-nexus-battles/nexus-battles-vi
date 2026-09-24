/**
 * Las pestañas hablan de la sesión entre sí — R17.
 *
 * jsdom no trae `BroadcastChannel`, así que aquí se usa un bus en memoria con
 * la misma semántica que el del navegador: un mensaje llega a TODOS los demás
 * canales abiertos con el mismo nombre, nunca al que lo publicó, y de forma
 * asíncrona.
 */

import { jest } from '@jest/globals';

import {
  MENSAJES,
  NOMBRE_CANAL,
  abrirCanal,
  difundirCierre,
  escucharCanal,
  pedirSesionAOtraPestana,
} from './canal-sesion.js';

function crearBus() {
  const abiertos = new Set();
  class CanalFalso {
    constructor(nombre) {
      this.nombre = nombre;
      this.onmessage = null;
      this.cerrado = false;
      abiertos.add(this);
    }
    postMessage(data) {
      if (this.cerrado) {
        throw new Error('canal cerrado');
      }
      for (const otro of abiertos) {
        if (otro !== this && otro.nombre === this.nombre && !otro.cerrado) {
          queueMicrotask(() => otro.onmessage?.({ data }));
        }
      }
    }
    close() {
      this.cerrado = true;
      abiertos.delete(this);
    }
  }
  return { CanalFalso, abiertos };
}

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const SESION = { token: 't.e30.f', apodo: 'Ana', rol: 'JUGADOR', uid: 'u-1' };

describe('canal de la sesión', () => {
  test('sin BroadcastChannel no hay canal y nada falla', async () => {
    expect(abrirCanal(undefined)).toBeNull();
    expect(() => difundirCierre(undefined)).not.toThrow();
    await expect(pedirSesionAOtraPestana({ Fabrica: undefined })).resolves.toBeNull();
    const soltar = escucharCanal({
      alCerrarse: jest.fn(),
      sesionParaCompartir: jest.fn(),
      Fabrica: undefined,
    });
    expect(() => soltar()).not.toThrow();
  });

  test('usa un único nombre de canal', () => {
    const { CanalFalso } = crearBus();
    expect(abrirCanal(CanalFalso).nombre).toBe(NOMBRE_CANAL);
  });

  test('un cierre en una pestaña llega a las demás, y el canal no queda abierto', async () => {
    const { CanalFalso, abiertos } = crearBus();
    const alCerrarse = jest.fn();
    escucharCanal({ alCerrarse, sesionParaCompartir: () => null, Fabrica: CanalFalso });

    difundirCierre(CanalFalso);
    await esperar();

    expect(alCerrarse).toHaveBeenCalledTimes(1);
    // Solo queda el de la pestaña que escucha.
    expect(abiertos.size).toBe(1);
  });

  test('una pestaña sin sesión recibe la de otra que sí la tiene', async () => {
    const { CanalFalso, abiertos } = crearBus();
    escucharCanal({
      alCerrarse: jest.fn(),
      sesionParaCompartir: () => SESION,
      Fabrica: CanalFalso,
    });

    await expect(pedirSesionAOtraPestana({ Fabrica: CanalFalso, espera: 50 })).resolves.toEqual(
      SESION,
    );
    // El canal de la pregunta se cierra al recibir la respuesta.
    expect(abiertos.size).toBe(1);
  });

  test('si nadie tiene sesión, la pregunta se da por perdida al agotar la espera', async () => {
    const { CanalFalso } = crearBus();
    escucharCanal({ alCerrarse: jest.fn(), sesionParaCompartir: () => null, Fabrica: CanalFalso });
    await expect(pedirSesionAOtraPestana({ Fabrica: CanalFalso, espera: 20 })).resolves.toBeNull();
  });

  test('sin ninguna otra pestaña, también se resuelve con null', async () => {
    const { CanalFalso } = crearBus();
    await expect(pedirSesionAOtraPestana({ Fabrica: CanalFalso, espera: 10 })).resolves.toBeNull();
  });

  test('una respuesta dirigida a OTRA pregunta no se adopta', async () => {
    const { CanalFalso } = crearBus();
    // Una pestaña que contesta siempre con un destinatario equivocado.
    const intrusa = new CanalFalso(NOMBRE_CANAL);
    intrusa.onmessage = () =>
      intrusa.postMessage({ tipo: MENSAJES.COMPARTIR, para: 'otra-pregunta', sesion: SESION });

    await expect(pedirSesionAOtraPestana({ Fabrica: CanalFalso, espera: 20 })).resolves.toBeNull();
  });

  test('una respuesta sin token no es una sesión', async () => {
    const { CanalFalso } = crearBus();
    const intrusa = new CanalFalso(NOMBRE_CANAL);
    intrusa.onmessage = ({ data }) =>
      intrusa.postMessage({ tipo: MENSAJES.COMPARTIR, para: data.id, sesion: { token: '' } });

    await expect(pedirSesionAOtraPestana({ Fabrica: CanalFalso, espera: 20 })).resolves.toBeNull();
  });

  test('dejar de escuchar cierra el canal', () => {
    const { CanalFalso, abiertos } = crearBus();
    const soltar = escucharCanal({
      alCerrarse: jest.fn(),
      sesionParaCompartir: () => SESION,
      Fabrica: CanalFalso,
    });
    expect(abiertos.size).toBe(1);
    soltar();
    expect(abiertos.size).toBe(0);
  });
});
