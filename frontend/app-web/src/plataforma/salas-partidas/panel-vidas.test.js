/**
 * Panel de vidas de la sala en batalla — HU-SAL-005, criterio 3.
 *
 * «La barra y el valor se actualizan tras cada accion para todos los
 * participantes.»
 *
 * Lo que se prueba aqui es esa frase: que un evento `partida.accion.resuelta`
 * del contrato AsyncAPI mueva TODAS las barras que el evento toca, y solo esas.
 * El transporte no se prueba porque no se implementa aqui: el panel recibe
 * eventos ya deserializados, venga quien venga a traerlos.
 *
 * Las formas de los datos salen literalmente de
 * `contracts/websocket/salas-partidas.yaml`: el esquema `Participante` y el
 * mensaje `AccionResuelta`. Si el contrato cambia, estas pruebas fallan, que
 * es justo lo que se quiere.
 */

import { jest } from '@jest/globals';

import { pintarParticipantes, aplicarAccionResuelta, montarPanelVidas } from './panel-vidas.js';

const ID_PARTIDA = '11111111-1111-1111-1111-111111111111';
const ANA = '22222222-2222-2222-2222-222222222222';
const BRUNO = '33333333-3333-3333-3333-333333333333';
const MAQUINA = '44444444-4444-4444-4444-444444444444';

/** Tres participantes con la forma del esquema `Participante` del contrato. */
function participantes() {
  return [
    {
      jugador: { id: ANA, apodo: 'Ana' },
      heroe: { id: 'h1', nombre: 'Arquero del Norte', vidaActual: 100, vidaMaxima: 100 },
      esIA: false,
      equipo: 1,
    },
    {
      jugador: { id: BRUNO, apodo: 'Bruno' },
      heroe: { id: 'h2', nombre: 'Gran Mago Sabio', vidaActual: 80, vidaMaxima: 100 },
      esIA: false,
      equipo: 2,
    },
    {
      jugador: { id: MAQUINA, apodo: 'Sombra' },
      heroe: { id: 'h3', nombre: 'Guerrero Tanque', vidaActual: 100, vidaMaxima: 100 },
      esIA: true,
      equipo: 2,
    },
  ];
}

/** Evento `AccionResuelta` tal y como lo define el contrato. */
function accionResuelta(afectados) {
  return {
    tipo: 'partida.accion.resuelta',
    idPartida: ID_PARTIDA,
    idEjecutor: ANA,
    accion: { codigo: 'FLECHA_DOBLE', nombre: 'Flecha doble', icono: null },
    afectados,
  };
}

beforeAll(() => {
  document.documentElement.style.setProperty('--vida-umbral-alto', '60');
  document.documentElement.style.setProperty('--vida-umbral-medio', '40');
});

let panel;

beforeEach(() => {
  document.body.innerHTML = '<div id="panel"></div>';
  panel = document.getElementById('panel');
});

/** @returns {HTMLElement} */
function barraDe(idJugador) {
  return panel.querySelector(`[data-jugador="${idJugador}"]`);
}

function valorDe(idJugador) {
  return barraDe(idJugador).querySelector('.barra-vida__valor').textContent;
}

describe('pintarParticipantes', () => {
  test('pinta una barra por participante, con su nombre y su vida inicial', () => {
    pintarParticipantes(panel, participantes());

    expect(panel.querySelectorAll('.barra-vida')).toHaveLength(3);
    expect(barraDe(ANA).querySelector('.barra-vida__nombre').textContent).toBe('Arquero del Norte');
    expect(valorDe(ANA)).toBe('100/100');
    expect(valorDe(BRUNO)).toBe('80/100');
  });

  test('aplica el umbral de color a cada barra desde el primer pintado', () => {
    pintarParticipantes(panel, participantes());

    expect(barraDe(ANA).dataset.estado).toBe('alto');
    expect(barraDe(BRUNO).dataset.estado).toBe('alto');
  });

  test('senala a los participantes controlados por la inteligencia artificial', () => {
    pintarParticipantes(panel, participantes());

    expect(barraDe(MAQUINA).dataset.ia).toBe('true');
    expect(barraDe(ANA).dataset.ia).toBeUndefined();
  });

  test('vuelve a pintar desde cero en vez de acumular barras', () => {
    pintarParticipantes(panel, participantes());
    pintarParticipantes(panel, participantes());

    expect(panel.querySelectorAll('.barra-vida')).toHaveLength(3);
  });
});

describe('aplicarAccionResuelta · criterio 3', () => {
  // Se pinta indicando la partida: sin eso el panel no puede saber que un
  // evento viene de otro combate, y el filtro de mas abajo no significaria nada.
  beforeEach(() => pintarParticipantes(panel, participantes(), { idPartida: ID_PARTIDA }));

  test('actualiza a la vez a todos los afectados por una misma accion', () => {
    aplicarAccionResuelta(
      panel,
      accionResuelta([
        { idJugador: BRUNO, vidaActual: 55, vidaMaxima: 100, diferencia: -25 },
        { idJugador: MAQUINA, vidaActual: 30, vidaMaxima: 100, diferencia: -70 },
      ])
    );

    expect(valorDe(BRUNO)).toBe('55/100');
    expect(barraDe(BRUNO).dataset.estado).toBe('medio');
    expect(valorDe(MAQUINA)).toBe('30/100');
    expect(barraDe(MAQUINA).dataset.estado).toBe('bajo');
  });

  test('no toca a quien la accion no afecto', () => {
    aplicarAccionResuelta(
      panel,
      accionResuelta([{ idJugador: BRUNO, vidaActual: 10, vidaMaxima: 100, diferencia: -70 }])
    );

    expect(valorDe(ANA)).toBe('100/100');
    expect(barraDe(ANA).dataset.estado).toBe('alto');
  });

  test('una curacion sube la barra igual que un dano la baja', () => {
    aplicarAccionResuelta(
      panel,
      accionResuelta([{ idJugador: BRUNO, vidaActual: 95, vidaMaxima: 100, diferencia: 15 }])
    );

    expect(valorDe(BRUNO)).toBe('95/100');
  });

  test('ignora un evento de otra partida en vez de mezclar dos combates', () => {
    aplicarAccionResuelta(panel, {
      ...accionResuelta([{ idJugador: ANA, vidaActual: 1, vidaMaxima: 100, diferencia: -99 }]),
      idPartida: '99999999-9999-9999-9999-999999999999',
    });

    expect(valorDe(ANA)).toBe('100/100');
  });

  test('ignora un mensaje de otro tipo del mismo canal', () => {
    aplicarAccionResuelta(panel, {
      tipo: 'partida.turno.cambiado',
      idPartida: ID_PARTIDA,
      idJugador: ANA,
      numeroTurno: 3,
    });

    expect(valorDe(ANA)).toBe('100/100');
  });

  test('un afectado que no esta en el panel no rompe al resto', () => {
    const fantasma = '55555555-5555-5555-5555-555555555555';

    aplicarAccionResuelta(
      panel,
      accionResuelta([
        { idJugador: fantasma, vidaActual: 5, vidaMaxima: 100, diferencia: -95 },
        { idJugador: ANA, vidaActual: 70, vidaMaxima: 100, diferencia: -30 },
      ])
    );

    expect(valorDe(ANA)).toBe('70/100');
  });

  test('un participante sin vida se queda en cero, no en negativo', () => {
    aplicarAccionResuelta(
      panel,
      accionResuelta([{ idJugador: ANA, vidaActual: 0, vidaMaxima: 100, diferencia: -100 }])
    );

    expect(valorDe(ANA)).toBe('0/100');
    expect(barraDe(ANA).dataset.estado).toBe('bajo');
  });
});

describe('montarPanelVidas', () => {
  test('pinta el estado inicial y se queda escuchando el canal', () => {
    const suscribir = jest.fn();

    montarPanelVidas(panel, { idPartida: ID_PARTIDA, participantes: participantes(), suscribir });

    expect(panel.querySelectorAll('.barra-vida')).toHaveLength(3);
    expect(suscribir).toHaveBeenCalledTimes(1);
  });

  test('cada evento que llega por el canal mueve las barras', () => {
    let entregar;
    const suscribir = (alRecibir) => {
      entregar = alRecibir;
    };

    montarPanelVidas(panel, { idPartida: ID_PARTIDA, participantes: participantes(), suscribir });

    entregar(accionResuelta([{ idJugador: ANA, vidaActual: 45, vidaMaxima: 100, diferencia: -55 }]));
    expect(valorDe(ANA)).toBe('45/100');

    entregar(accionResuelta([{ idJugador: ANA, vidaActual: 20, vidaMaxima: 100, diferencia: -25 }]));
    expect(valorDe(ANA)).toBe('20/100');
    expect(barraDe(ANA).dataset.estado).toBe('bajo');
  });

  test('funciona sin canal: la vista pinta el estado inicial igual', () => {
    expect(() =>
      montarPanelVidas(panel, { idPartida: ID_PARTIDA, participantes: participantes() })
    ).not.toThrow();

    expect(panel.querySelectorAll('.barra-vida')).toHaveLength(3);
  });
});
