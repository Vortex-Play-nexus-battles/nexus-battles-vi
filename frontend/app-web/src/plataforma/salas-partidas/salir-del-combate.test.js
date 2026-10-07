/**
 * «Salir» en pleno combate — revisión del modo jugador del 6-oct, punto 18.
 *
 * Lo que se fija: mientras sigues combatiendo, salir pregunta con el texto
 * exacto y, si confirmas, te rindes por el servidor (nunca se manda un
 * ganador); fuera de combate, el enlace funciona como siempre.
 */

import { jest } from '@jest/globals';

import {
  montarSalidaDelCombate,
  sigueCombatiendo,
  TEXTO_DE_ABANDONO,
} from './salir-del-combate.js';

const YO = '11111111-1111-1111-1111-111111111111';
const RIVAL = '22222222-2222-2222-2222-222222222222';

const partida = (cambios = {}) => ({
  id: 'p-1',
  estado: 'EN_CURSO',
  participantes: [
    { jugador: YO, esIA: false, heroe: { vidaActual: 40, vidaMaxima: 100 } },
    { jugador: RIVAL, esIA: false, heroe: { vidaActual: 70, vidaMaxima: 100 } },
  ],
  ...cambios,
});

const asentar = () => new Promise((resolver) => setTimeout(resolver, 0));

describe('sigueCombatiendo', () => {
  test('en una partida en curso, con vida, sí', () => {
    expect(sigueCombatiendo(partida(), YO)).toBe(true);
  });

  test('terminada, caído, sin partida, sin identidad o mirando una ajena: no', () => {
    expect(sigueCombatiendo(partida({ estado: 'FINALIZADA' }), YO)).toBe(false);
    expect(sigueCombatiendo(partida(), YO, { terminada: true })).toBe(false);
    expect(sigueCombatiendo(partida(), YO, { caido: true })).toBe(false);
    expect(sigueCombatiendo(null, YO)).toBe(false);
    expect(sigueCombatiendo(partida(), null)).toBe(false);
    expect(sigueCombatiendo(partida(), '99999999-9999-9999-9999-999999999999')).toBe(false);
  });

  test('con la vida a cero ya no hay nada que abandonar', () => {
    const caida = partida();
    caida.participantes[0].heroe.vidaActual = 0;
    expect(sigueCombatiendo(caida, YO)).toBe(false);
  });
});

describe('montarSalidaDelCombate', () => {
  function montar({ situacion = { partida: partida() }, confirmar, rendirse } = {}) {
    document.body.innerHTML = '<a data-zona="salir" href="./batallas.html">Salir</a>';
    const enlace = document.querySelector('[data-zona="salir"]');
    const puertos = {
      situacion: () => situacion,
      yo: YO,
      confirmar: confirmar ?? jest.fn().mockResolvedValue(true),
      rendirse: rendirse ?? jest.fn().mockResolvedValue({ id: 'p-1', estado: 'FINALIZADA' }),
      alAbandonar: jest.fn(),
      alFallar: jest.fn(),
    };
    montarSalidaDelCombate(enlace, puertos);
    return { enlace, ...puertos };
  }

  function pulsar(enlace) {
    const evento = new MouseEvent('click', { bubbles: true, cancelable: true });
    enlace.dispatchEvent(evento);
    return evento;
  }

  test('en combate pregunta con el texto exacto y los dos botones de la revisión', async () => {
    const { enlace, confirmar } = montar({ confirmar: jest.fn().mockResolvedValue(false) });

    const evento = pulsar(enlace);
    await asentar();

    expect(evento.defaultPrevented).toBe(true);
    expect(confirmar).toHaveBeenCalledWith(
      expect.objectContaining({
        mensaje: TEXTO_DE_ABANDONO,
        textoConfirmar: 'Abandonar batalla',
        textoCancelar: 'Seguir jugando',
        peligro: true,
      }),
    );
    expect(TEXTO_DE_ABANDONO).toBe(
      'Si abandonas la batalla se contará como derrota. Tu rival será declarado ganador y se liquidará la apuesta según las reglas de la partida.',
    );
  });

  test('«Seguir jugando» no toca nada', async () => {
    const { enlace, rendirse, alAbandonar } = montar({
      confirmar: jest.fn().mockResolvedValue(false),
    });

    pulsar(enlace);
    await asentar();

    expect(rendirse).not.toHaveBeenCalled();
    expect(alAbandonar).not.toHaveBeenCalled();
  });

  test('«Abandonar batalla» se rinde por el servidor con la partida y nada más, y sale', async () => {
    const { enlace, rendirse, alAbandonar } = montar();

    pulsar(enlace);
    await asentar();

    expect(rendirse).toHaveBeenCalledWith('p-1');
    expect(rendirse.mock.calls[0]).toHaveLength(1);
    expect(alAbandonar).toHaveBeenCalledTimes(1);
  });

  test('si el servidor no responde, no se sale y se dice', async () => {
    const fallo = new Error('sin red');
    const { enlace, alAbandonar, alFallar } = montar({
      rendirse: jest.fn().mockRejectedValue(fallo),
    });

    pulsar(enlace);
    await asentar();

    expect(alAbandonar).not.toHaveBeenCalled();
    expect(alFallar).toHaveBeenCalledWith(fallo);
  });

  test('fuera de combate (partida terminada) es un enlace normal: ni pregunta ni se rinde', async () => {
    const { enlace, confirmar, rendirse } = montar({
      situacion: { partida: partida({ estado: 'FINALIZADA' }) },
    });

    const evento = pulsar(enlace);
    await asentar();

    expect(evento.defaultPrevented).toBe(false);
    expect(confirmar).not.toHaveBeenCalled();
    expect(rendirse).not.toHaveBeenCalled();
  });

  test('en la sala de espera (sin partida) tampoco pregunta', async () => {
    const { enlace, confirmar } = montar({ situacion: { partida: null } });

    const evento = pulsar(enlace);
    await asentar();

    expect(evento.defaultPrevented).toBe(false);
    expect(confirmar).not.toHaveBeenCalled();
  });

  test('sin enlace no se monta nada', () => {
    expect(
      montarSalidaDelCombate(null, {
        situacion: () => ({}),
        yo: YO,
        confirmar: jest.fn(),
        rendirse: jest.fn(),
        alAbandonar: jest.fn(),
      }),
    ).toBe(false);
  });
});
