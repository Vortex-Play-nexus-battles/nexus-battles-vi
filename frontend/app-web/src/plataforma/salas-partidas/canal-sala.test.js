/**
 * HU-SAL-002 — Canal en tiempo real de la sala.
 *
 * Prueba el tercer criterio del issue #30 desde el lado del cliente: que un
 * aviso de ingreso actualice el estado local, y que lo que no corresponde se
 * descarte en vez de corromperlo.
 */

import { jest } from '@jest/globals';

import {
  seguirSala,
  aplicarAviso,
  destinoDeSala,
  urlDelCanal,
  estadoDesdeFicha,
  TIPO_INGRESO,
  TIPO_PARTIDA_INICIADA,
  TIPO_SALIDA,
  TIPO_CANCELADA,
  textoDeCancelacion,
} from './canal-sala.js';

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

describe('urlDelCanal', () => {
  test('en la ejecucion integrada el canal vive en el mismo origen que la pagina', () => {
    expect(urlDelCanal({ location: { protocol: 'http:', host: 'localhost:8084' } })).toBe(
      'ws://localhost:8084/ws',
    );
    expect(urlDelCanal({ location: { protocol: 'https:', host: 'juego.nexus.local' } })).toBe(
      'wss://juego.nexus.local/ws',
    );
  });

  test('con base declarada, el canal sigue a la API y cambia http por ws', () => {
    expect(urlDelCanal({ base: 'http://127.0.0.1:8083' })).toBe('ws://127.0.0.1:8083/ws');
    expect(urlDelCanal({ base: 'https://api.nexus.local/' })).toBe('wss://api.nexus.local/ws');
  });

  test('el token nunca forma parte de la URL', () => {
    expect(urlDelCanal({ base: 'http://127.0.0.1:8083' })).not.toMatch(/token|Bearer|\?/);
  });
});

describe('estadoDesdeFicha', () => {
  test('traduce la ficha del listado a la forma que entiende el canal', () => {
    expect(
      estadoDesdeFicha({
        id: SALA,
        ocupacion: 3,
        maximoParticipantes: 6,
        participantes: [ANFITRION, VISITANTE],
      }),
    ).toEqual({
      idSala: SALA,
      ocupacion: { actual: 3, maximo: 6 },
      participantes: [ANFITRION, VISITANTE],
    });
  });

  test('sin lista de participantes arranca vacia, no rota', () => {
    expect(estadoDesdeFicha({ id: SALA, ocupacion: 1, maximoParticipantes: 2 })).toEqual({
      idSala: SALA,
      ocupacion: { actual: 1, maximo: 2 },
      participantes: [],
    });
  });
});

// ===========================================================================
// HU-SAL-004 — el combate arranca y la sala de espera se entera
//
// El aviso llega por el canal de la SALA porque quien espera todavia no conoce
// el identificador de la partida. Lo que aqui importa es que se avise una sola
// vez, que no se confunda con un ingreso, y que no altere el estado de la sala.
// ===========================================================================

function avisoDeArranque(cambios = {}) {
  return {
    tipo: TIPO_PARTIDA_INICIADA,
    idSala: SALA,
    idPartida: '55555555-5555-5555-5555-555555555555',
    ordenDeTurnos: [ANFITRION, VISITANTE],
    turnoActual: { idJugador: ANFITRION, numeroTurno: 1 },
    ...cambios,
  };
}

describe('arranque del combate (HU-SAL-004)', () => {
  test('avisa a la vista con el mensaje completo', () => {
    const alIniciarPartida = jest.fn();
    const canal = seguirSala(estado(), { alIniciarPartida });

    canal.recibir(avisoDeArranque());

    expect(alIniciarPartida).toHaveBeenCalledWith(avisoDeArranque());
  });

  test('avisa una sola vez aunque el aviso se repita al reconectar', () => {
    const alIniciarPartida = jest.fn();
    const canal = seguirSala(estado(), { alIniciarPartida });

    canal.recibir(avisoDeArranque());
    canal.recibir(avisoDeArranque());

    expect(alIniciarPartida).toHaveBeenCalledTimes(1);
  });

  test('el arranque de otra sala no mueve a nadie de pantalla', () => {
    const alIniciarPartida = jest.fn();
    const canal = seguirSala(estado(), { alIniciarPartida });

    canal.recibir(avisoDeArranque({ idSala: 'ffffffff-0000-0000-0000-000000000009' }));

    expect(alIniciarPartida).not.toHaveBeenCalled();
  });

  test('no toca la ocupacion ni los participantes: cambia de pantalla, no de sala', () => {
    const alCambiar = jest.fn();
    const canal = seguirSala(estado(), { alCambiar, alIniciarPartida: () => {} });

    canal.recibir(avisoDeArranque());

    expect(alCambiar).not.toHaveBeenCalled();
    expect(canal.estado()).toEqual(estado());
  });

  test('sin manejador el aviso se ignora en silencio, no rompe la vista', () => {
    const canal = seguirSala(estado());

    expect(() => canal.recibir(avisoDeArranque())).not.toThrow();
    expect(canal.estado()).toEqual(estado());
  });

  test('un ingreso posterior al arranque sigue procesandose con normalidad', () => {
    const alCambiar = jest.fn();
    const canal = seguirSala(estado(), { alCambiar, alIniciarPartida: () => {} });

    canal.recibir(avisoDeArranque());
    canal.recibir(avisoDeIngreso());

    expect(alCambiar).toHaveBeenCalledTimes(1);
    expect(canal.estado().participantes).toEqual([ANFITRION, VISITANTE]);
  });
});

// ===========================================================================
// HU-SAL-006 — salir y cancelar antes de empezar
// ===========================================================================

function avisoDeSalida(cambios = {}) {
  return {
    tipo: TIPO_SALIDA,
    idSala: SALA,
    idJugador: VISITANTE,
    ocupacion: { actual: 1, maximo: 4 },
    ...cambios,
  };
}

function avisoDeCancelacion(cambios = {}) {
  return {
    tipo: TIPO_CANCELADA,
    idSala: SALA,
    motivo: 'CANCELADA_POR_ANFITRION',
    creditosDevueltos: 0,
    ...cambios,
  };
}

describe('salida de un participante (HU-SAL-006, CA-01)', () => {
  test('una salida baja la ocupacion y quita al jugador', () => {
    const dentro = estado({
      ocupacion: { actual: 2, maximo: 4 },
      participantes: [ANFITRION, VISITANTE],
    });

    const resultado = aplicarAviso(dentro, avisoDeSalida());

    expect(resultado.ocupacion).toEqual({ actual: 1, maximo: 4 });
    expect(resultado.participantes).toEqual([ANFITRION]);
  });

  test('la salida de alguien que ya no estaba no cambia nada (reconexion)', () => {
    const solo = estado();

    expect(aplicarAviso(solo, avisoDeSalida())).toBe(solo);
  });

  test('la salida de otra sala se descarta', () => {
    const dentro = estado({ participantes: [ANFITRION, VISITANTE] });

    expect(aplicarAviso(dentro, avisoDeSalida({ idSala: 'otra' }))).toBe(dentro);
  });

  test('por seguirSala, la salida avisa al que monto con el estado nuevo', () => {
    const alCambiar = jest.fn();
    const canal = seguirSala(estado({ participantes: [ANFITRION, VISITANTE] }), { alCambiar });

    canal.recibir(avisoDeSalida());

    expect(alCambiar).toHaveBeenCalledTimes(1);
    expect(alCambiar.mock.calls[0][0].participantes).toEqual([ANFITRION]);
  });
});

describe('cancelacion de la sala (HU-SAL-006, CA-02)', () => {
  test('avisa a la vista con el mensaje completo, una sola vez', () => {
    const alCancelar = jest.fn();
    const canal = seguirSala(estado(), { alCancelar });

    canal.recibir(avisoDeCancelacion({ creditosDevueltos: 150 }));
    canal.recibir(avisoDeCancelacion({ creditosDevueltos: 150 }));

    expect(alCancelar).toHaveBeenCalledTimes(1);
    expect(alCancelar.mock.calls[0][0].motivo).toBe('CANCELADA_POR_ANFITRION');
    expect(alCancelar.mock.calls[0][0].creditosDevueltos).toBe(150);
  });

  test('la cancelacion de otra sala no mueve a nadie', () => {
    const alCancelar = jest.fn();
    const canal = seguirSala(estado(), { alCancelar });

    canal.recibir(avisoDeCancelacion({ idSala: 'otra' }));

    expect(alCancelar).not.toHaveBeenCalled();
  });

  test('no toca la ocupacion ni dispara alCambiar: cambia de pantalla, no de sala', () => {
    const alCambiar = jest.fn();
    const canal = seguirSala(estado(), { alCambiar, alCancelar: () => {} });

    canal.recibir(avisoDeCancelacion());

    expect(alCambiar).not.toHaveBeenCalled();
    expect(canal.estado()).toEqual(estado());
  });

  test('el texto traduce el motivo y cuenta los creditos devueltos si los hubo', () => {
    expect(textoDeCancelacion(avisoDeCancelacion())).toBe('El anfitrion cancelo la sala.');
    expect(textoDeCancelacion(avisoDeCancelacion({ creditosDevueltos: 150 }))).toBe(
      'El anfitrion cancelo la sala. Se te devolvieron 150 creditos.',
    );
    expect(textoDeCancelacion({ motivo: 'INACTIVIDAD' })).toMatch(/inactividad/i);
    expect(textoDeCancelacion({ motivo: 'ALGO_NUEVO' })).toBe('La sala se cerro.');
  });
});
