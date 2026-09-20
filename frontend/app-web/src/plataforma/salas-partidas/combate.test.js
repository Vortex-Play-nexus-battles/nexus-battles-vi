/**
 * HU-SAL-005 — jugar el turno y ver el combate.
 *
 * Lo que se prueba: que la acción se mande al destino del contrato sin el
 * identificador del jugador, que reconectar no duplique nada, y que el final se
 * refleje en la vista. El pintado de barras ya lo cubre `panel-vidas.test.js`.
 */

import { jest } from '@jest/globals';

import {
  destinoDeAccion,
  enviarAccion,
  registroDeAvisos,
  textoDelResultado,
  montarControlesDeCombate,
  ACCION_RESUELTA,
  TURNO_CAMBIADO,
  PARTIDA_FINALIZADA,
} from './combate.js';

const PARTIDA = '11111111-1111-1111-1111-111111111111';
const ANA = '22222222-2222-2222-2222-222222222222';
const BRUNO = '33333333-3333-3333-3333-333333333333';

const VISTA = `
  <div class="fila" data-zona="acciones"></div>
  <p class="t-cuerpo" data-zona="resultado" hidden></p>
`;

function participantes() {
  return [
    {
      jugador: { id: ANA },
      heroe: { nombre: 'Arquero del Norte', vidaActual: 100, vidaMaxima: 100 },
    },
    { jugador: { id: BRUNO }, heroe: { nombre: 'Centinela', vidaActual: 90, vidaMaxima: 90 } },
  ];
}

function accionResuelta(vidaDeBruno) {
  return {
    tipo: ACCION_RESUELTA,
    idPartida: PARTIDA,
    idEjecutor: ANA,
    accion: { codigo: 'ATAQUE_BASICO', nombre: 'CAUSAR_DANO' },
    afectados: [{ idJugador: BRUNO, vidaActual: vidaDeBruno, vidaMaxima: 90, diferencia: -10 }],
  };
}

beforeEach(() => {
  document.body.innerHTML = VISTA;
});

describe('enviarAccion', () => {
  test('usa el destino accionDelJugador del contrato', () => {
    expect(destinoDeAccion(PARTIDA)).toBe(`/app/partidas/${PARTIDA}/acciones`);
  });

  test('el identificador del jugador NO viaja: lo pone el servidor desde el token', () => {
    // Si viajara en el mensaje, cualquiera podria jugar el turno de otro.
    const enviados = [];
    const cliente = { enviar: (destino, cuerpo) => enviados.push({ destino, cuerpo }) };

    enviarAccion(cliente, PARTIDA, { idObjetivo: BRUNO });

    expect(enviados[0].destino).toBe(`/app/partidas/${PARTIDA}/acciones`);
    expect(enviados[0].cuerpo).toEqual({ codigoAccion: 'ATAQUE_BASICO', idObjetivo: BRUNO });
    expect(JSON.stringify(enviados[0].cuerpo)).not.toContain(ANA);
  });

  test('sin objetivo se manda null: el servidor resuelve el 1v1 por su cuenta', () => {
    const enviados = [];
    enviarAccion({ enviar: (d, c) => enviados.push(c) }, PARTIDA);

    expect(enviados[0].idObjetivo).toBeNull();
  });
});

describe('registroDeAvisos · reconectar no duplica', () => {
  test('la misma accion resuelta dos veces solo cuenta una', () => {
    const registro = registroDeAvisos();

    expect(registro.yaVisto(accionResuelta(80))).toBe(false);
    expect(registro.yaVisto(accionResuelta(80))).toBe(true);
  });

  test('dos golpes distintos si cuentan los dos', () => {
    const registro = registroDeAvisos();

    registro.yaVisto(accionResuelta(80));

    expect(registro.yaVisto(accionResuelta(70))).toBe(false);
  });

  test('el turno se identifica por su numero, que siempre sube', () => {
    const registro = registroDeAvisos();
    const turno = (n) => ({
      tipo: TURNO_CAMBIADO,
      idPartida: PARTIDA,
      idJugador: ANA,
      numeroTurno: n,
    });

    expect(registro.yaVisto(turno(2))).toBe(false);
    expect(registro.yaVisto(turno(2))).toBe(true);
    expect(registro.yaVisto(turno(3))).toBe(false);
  });

  test('una partida termina una sola vez, por mucho que se reenvie', () => {
    const registro = registroDeAvisos();
    const fin = { tipo: PARTIDA_FINALIZADA, idPartida: PARTIDA, ganadores: [ANA] };

    expect(registro.yaVisto(fin)).toBe(false);
    expect(registro.yaVisto(fin)).toBe(true);
  });

  test('un mensaje de otro tipo no se deduplica: no es asunto de este registro', () => {
    const registro = registroDeAvisos();
    const otro = { tipo: 'sala.chat.mensaje', idPartida: PARTIDA };

    expect(registro.yaVisto(otro)).toBe(false);
    expect(registro.yaVisto(otro)).toBe(false);
  });
});

describe('textoDelResultado', () => {
  test('distingue ganar de perder segun quien mira', () => {
    const fin = { ganadores: [ANA] };

    expect(textoDelResultado(fin, ANA)).toMatch(/ganado/i);
    expect(textoDelResultado(fin, BRUNO)).toMatch(/perdido/i);
  });

  test('sin ganadores es empate, no un vencedor inventado', () => {
    expect(textoDelResultado({ ganadores: [] }, ANA)).toMatch(/empate/i);
  });
});

describe('montarControlesDeCombate', () => {
  test('hay un boton por rival, y ninguno para uno mismo', () => {
    montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar: () => {},
    });

    const botones = document.querySelectorAll('[data-atacar]');
    expect(botones).toHaveLength(1);
    expect(botones[0].dataset.atacar).toBe(BRUNO);
    expect(botones[0].textContent).toContain('Centinela');
  });

  test('los botones empiezan deshabilitados: todavia no se sabe de quien es el turno', () => {
    montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar: () => {},
    });

    expect(document.querySelector('[data-atacar]').disabled).toBe(true);
  });

  // Los tres que siguen cierran el defecto que destapo el E2E del corte
  // vertical: los botones solo se abrian al recibir `partida.turno.cambiado`,
  // y ese mensaje SOLO lo emite `AvanzarTurno`, es decir, despues de que
  // alguien haya jugado. En el turno 1 nadie ha jugado todavia, asi que nadie
  // podia dar el primer golpe desde el navegador: el combate no arrancaba. Y
  // quien recargaba a mitad de partida se quedaba sin poder jugar hasta que
  // actuara el rival.
  //
  // El turno en curso ya viaja en `GET /partidas/{id}` (`turnoActual`), asi
  // que la vista lo sabe al montar y no hace falta esperar ningun mensaje.
  test('si al montar ya se sabe que el turno es mio, los botones nacen abiertos', () => {
    montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      turnoDe: ANA,
      alAtacar: () => {},
    });

    expect(document.querySelector('[data-atacar]').disabled).toBe(false);
  });

  test('si el turno es del rival, siguen cerrados', () => {
    montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      turnoDe: BRUNO,
      alAtacar: () => {},
    });

    expect(document.querySelector('[data-atacar]').disabled).toBe(true);
  });

  test('el turno del montaje no le gana al que llega despues por el canal', () => {
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      turnoDe: ANA,
      alAtacar: () => {},
    });

    controles.recibir({
      tipo: TURNO_CAMBIADO,
      idPartida: PARTIDA,
      idJugador: BRUNO,
      numeroTurno: 2,
    });

    expect(document.querySelector('[data-atacar]').disabled).toBe(true);
  });

  test('se habilitan cuando llega el turno propio, y no con el ajeno', () => {
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar: () => {},
    });

    controles.recibir({ tipo: TURNO_CAMBIADO, idPartida: PARTIDA, idJugador: ANA, numeroTurno: 1 });
    expect(document.querySelector('[data-atacar]').disabled).toBe(false);

    controles.recibir({
      tipo: TURNO_CAMBIADO,
      idPartida: PARTIDA,
      idJugador: BRUNO,
      numeroTurno: 2,
    });
    expect(document.querySelector('[data-atacar]').disabled).toBe(true);
  });

  test('pulsar ataca al rival de ese boton', () => {
    const alAtacar = jest.fn();
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar,
    });
    controles.recibir({ tipo: TURNO_CAMBIADO, idPartida: PARTIDA, idJugador: ANA, numeroTurno: 1 });

    document.querySelector('[data-atacar]').click();

    expect(alAtacar).toHaveBeenCalledWith({ idObjetivo: BRUNO, codigoAccion: 'ATAQUE_BASICO' });
  });

  test('un boton deshabilitado no ataca aunque se pulse', () => {
    const alAtacar = jest.fn();
    montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar,
    });

    document.querySelector('[data-atacar]').click();

    expect(alAtacar).not.toHaveBeenCalled();
  });

  test('al terminar el combate desaparecen los botones y se dice el resultado', () => {
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar: () => {},
    });

    controles.recibir({ tipo: PARTIDA_FINALIZADA, idPartida: PARTIDA, ganadores: [ANA] });

    const resultado = document.querySelector('[data-zona="resultado"]');
    expect(document.querySelector('[data-zona="acciones"]').hidden).toBe(true);
    expect(resultado.hidden).toBe(false);
    expect(resultado.textContent).toMatch(/ganado/i);
  });

  test('el final de OTRA partida no toca esta vista', () => {
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar: () => {},
    });
    controles.recibir({ tipo: TURNO_CAMBIADO, idPartida: PARTIDA, idJugador: ANA, numeroTurno: 1 });

    controles.recibir({ tipo: PARTIDA_FINALIZADA, idPartida: 'otra', ganadores: [BRUNO] });

    expect(document.querySelector('[data-zona="acciones"]').hidden).toBe(false);
    expect(document.querySelector('[data-atacar]').disabled).toBe(false);
  });

  test('reconectar no vuelve a disparar el final ni reabre los botones', () => {
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar: () => {},
    });
    const fin = { tipo: PARTIDA_FINALIZADA, idPartida: PARTIDA, ganadores: [ANA] };

    controles.recibir(fin);
    controles.recibir(fin);

    expect(document.querySelector('[data-zona="acciones"]').hidden).toBe(true);
  });
});
