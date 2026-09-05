/**
 * HU-SAL-002 — Canal en tiempo real de la sala.
 *
 * Prueba el tercer criterio del issue #30 desde el lado del cliente: que un
 * aviso de ingreso actualice el estado local, y que lo que no corresponde se
 * descarte en vez de corromperlo.
 */

import { jest } from '@jest/globals';

import { seguirSala, aplicarAviso, destinoDeSala, TIPO_INGRESO } from './canal-sala.js';

const SALA = '11111111-1111-1111-1111-111111111111';
const ANFITRION = 'aaaaaaaa-0000-0000-0000-000000000001';
const VISITANTE = 'bbbbbbbb-0000-0000-0000-000000000002';

function estado(cambios = {}) {
  return {
    idSala: SALA,
    ocupacion: { actual: 1, maximo: 4 },
    participantes: [ANFITRION],
    ...cambios,
  };
}

function avisoDeIngreso(cambios = {}) {
  return {
    tipo: TIPO_INGRESO,
    idSala: SALA,
    idJugador: VISITANTE,
    ocupacion: { actual: 2, maximo: 4 },
    ...cambios,
  };
}

describe('destinoDeSala', () => {
  test('coincide con el destino que publica el servidor', () => {
    expect(destinoDeSala(SALA)).toBe(`/tema/salas/${SALA}`);
  });
});

describe('aplicarAviso', () => {
  test('un ingreso sube la ocupacion y anade al jugador', () => {
    const resultado = aplicarAviso(estado(), avisoDeIngreso());

    expect(resultado.ocupacion).toEqual({ actual: 2, maximo: 4 });
    expect(resultado.participantes).toEqual([ANFITRION, VISITANTE]);
  });

  test('no muta el estado recibido: devuelve uno nuevo', () => {
    const original = estado();

    aplicarAviso(original, avisoDeIngreso());

    expect(original.ocupacion).toEqual({ actual: 1, maximo: 4 });
    expect(original.participantes).toEqual([ANFITRION]);
  });

  test('el mismo aviso dos veces no cuenta al jugador dos veces', () => {
    const unaVez = aplicarAviso(estado(), avisoDeIngreso());
    const dosVeces = aplicarAviso(unaVez, avisoDeIngreso());

    expect(dosVeces).toBe(unaVez);
    expect(dosVeces.participantes).toHaveLength(2);
  });

  test('un aviso de otra sala se descarta', () => {
    const original = estado();

    expect(aplicarAviso(original, avisoDeIngreso({ idSala: 'otra' }))).toBe(original);
  });

  test('un mensaje de otro tipo se descarta: el chat comparte prefijo de canal', () => {
    const original = estado();

    expect(aplicarAviso(original, { tipo: 'sala.chat.mensaje', idSala: SALA })).toBe(original);
  });

  test('un mensaje vacio o nulo no rompe nada', () => {
    const original = estado();

    expect(aplicarAviso(original, null)).toBe(original);
    expect(aplicarAviso(original, {})).toBe(original);
  });
});

describe('seguirSala', () => {
  test('se suscribe al destino de la sala', () => {
    const suscribir = jest.fn();

    seguirSala(estado(), { suscribir });

    expect(suscribir).toHaveBeenCalledWith(`/tema/salas/${SALA}`, expect.any(Function));
  });

  test('un aviso recibido actualiza el estado y avisa al que monto', () => {
    let entregar;
    const alCambiar = jest.fn();
    const canal = seguirSala(estado(), {
      suscribir: (_destino, alRecibir) => {
        entregar = alRecibir;
      },
      alCambiar,
    });

    entregar(avisoDeIngreso());

    expect(canal.estado().ocupacion).toEqual({ actual: 2, maximo: 4 });
    expect(canal.estado().participantes).toContain(VISITANTE);
    expect(alCambiar).toHaveBeenCalledTimes(1);
  });

  test('un aviso que no aplica no dispara el aviso de cambio', () => {
    let entregar;
    const alCambiar = jest.fn();
    seguirSala(estado(), {
      suscribir: (_destino, alRecibir) => {
        entregar = alRecibir;
      },
      alCambiar,
    });

    entregar({ tipo: 'sala.chat.mensaje', idSala: SALA });
    entregar(avisoDeIngreso({ idSala: 'otra' }));

    expect(alCambiar).not.toHaveBeenCalled();
  });

  test('sin suscribir no finge conexion, pero sigue procesando lo que le entreguen', () => {
    const canal = seguirSala(estado());

    expect(canal.conectado).toBe(false);

    canal.recibir(avisoDeIngreso());
    expect(canal.estado().ocupacion.actual).toBe(2);
  });
});
