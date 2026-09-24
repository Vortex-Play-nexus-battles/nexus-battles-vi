/**
 * HU-SAL-005 — jugar el turno y ver el combate.
 *
 * Lo que se prueba: que la acción se mande al destino del contrato sin el
 * identificador del jugador, que reconectar no duplique nada, y que el final se
 * refleje en la vista. El pintado de barras ya lo cubre `panel-vidas.test.js`.
 */

import { jest } from '@jest/globals';

import {
  creditosDe,
  recompensaDe,
  destinoDeAccion,
  enviarAccion,
  registroDeAvisos,
  textoDelResultado,
  textoDelTurno,
  montarControlesDeCombate,
  gano,
  empate,
  desenlaceDe,
  netoDeCreditos,
  mezclarPorJugador,
  ACCION_RESUELTA,
  TURNO_CAMBIADO,
  PARTIDA_FINALIZADA,
} from './combate.js';

const PARTIDA = '11111111-1111-1111-1111-111111111111';
const ANA = '22222222-2222-2222-2222-222222222222';
const BRUNO = '33333333-3333-3333-3333-333333333333';

const VISTA = `
  <p class="turno-actual" data-zona="turno" role="status" hidden></p>
  <div class="pila" data-zona="vidas">
    <div class="barra-vida" data-jugador="22222222-2222-2222-2222-222222222222"></div>
    <div class="barra-vida" data-jugador="33333333-3333-3333-3333-333333333333"></div>
  </div>
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

  test('el turno se identifica por su número, que siempre sube', () => {
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

  /* HU-JUE-014, CA-04: el reparto de la apuesta, desde el punto de vista de quien mira. */

  test('con reparto, el ganador ve cuanto se lleva y el perdedor cuanto pierde', () => {
    const fin = {
      ganadores: [ANA],
      reparto: [
        { idJugador: ANA, creditos: 200 },
        { idJugador: BRUNO, creditos: -100 },
      ],
    };

    expect(textoDelResultado(fin, ANA)).toMatch(/ganado.*llevas 200 créditos/i);
    expect(textoDelResultado(fin, BRUNO)).toMatch(/perdido.*pierdes los 100 créditos/i);
  });

  test('en empate con apuesta se dice que los créditos vuelven', () => {
    const fin = { ganadores: [], reparto: [{ idJugador: ANA, creditos: 0 }] };

    expect(textoDelResultado(fin, ANA)).toMatch(/empate.*devuelven/i);
  });

  test('sin reparto (sin apuesta o liquidacion pendiente) no se inventa ninguna cifra', () => {
    expect(textoDelResultado({ ganadores: [ANA] }, ANA)).toBe('Has ganado el combate.');
    expect(creditosDe({ ganadores: [ANA] }, ANA)).toBeNull();
    expect(creditosDe({ reparto: [{ idJugador: BRUNO, creditos: 5 }] }, ANA)).toBeNull();
  });

  /* HU-SAL-004: modo cooperativo, el resultado es del equipo. */

  test('con equipo ganador, quien esta entre los ganadores ve ganar a su equipo', () => {
    const fin = { ganadores: [ANA], equipoGanador: 1 };

    expect(textoDelResultado(fin, ANA)).toBe('Tu equipo (1) ha ganado el combate.');
    expect(textoDelResultado(fin, BRUNO)).toBe('Gana el equipo 1. Tu equipo ha perdido.');
  });

  test('un compañero que cayo también gana con su equipo, aunque no este en ganadores', () => {
    const fin = { ganadores: [ANA], equipoGanador: 1 };

    expect(textoDelResultado(fin, BRUNO, 1)).toBe('Tu equipo (1) ha ganado el combate.');
    expect(textoDelResultado(fin, BRUNO, 2)).toMatch(/Tu equipo ha perdido/);
  });
});

describe('montarControlesDeCombate · equipos (HU-SAL-004)', () => {
  const CARLA = '44444444-4444-4444-4444-444444444444';

  test('no hay boton para atacar a un companero de equipo', () => {
    montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      turnoDe: ANA,
      participantes: [
        { jugador: { id: ANA }, heroe: { nombre: 'Arquero' }, equipo: 1 },
        { jugador: { id: BRUNO }, heroe: { nombre: 'Centinela' }, equipo: 1 },
        { jugador: { id: CARLA }, heroe: { nombre: 'Maga' }, equipo: 2 },
      ],
      alAtacar: () => {},
    });

    const botones = [...document.querySelectorAll('[data-atacar]')].map((b) => b.dataset.atacar);
    expect(botones).toEqual([CARLA]);
  });

  test('sin equipos todos los demas son rivales, como siempre', () => {
    montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: [
        { jugador: { id: ANA }, heroe: { nombre: 'Arquero' }, equipo: null },
        { jugador: { id: BRUNO }, heroe: { nombre: 'Centinela' }, equipo: null },
        { jugador: { id: CARLA }, heroe: { nombre: 'Maga' } },
      ],
      alAtacar: () => {},
    });

    expect(document.querySelectorAll('[data-atacar]')).toHaveLength(2);
  });
});

describe('registroDeAvisos con reparto (HU-JUE-014, CA-06)', () => {
  test('el mismo fin, primero sin reparto y después con el, NO es un duplicado', () => {
    const registro = registroDeAvisos();
    const sinReparto = { tipo: PARTIDA_FINALIZADA, idPartida: PARTIDA, ganadores: [ANA] };
    const conReparto = { ...sinReparto, reparto: [{ idJugador: ANA, creditos: 100 }] };

    expect(registro.yaVisto(sinReparto)).toBe(false);
    expect(registro.yaVisto(conReparto)).toBe(false);
    expect(registro.yaVisto(conReparto)).toBe(true);
  });

  test('el segundo aviso, ya con reparto, actualiza el texto del resultado', () => {
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar: () => {},
    });
    const resultado = document.querySelector('[data-zona="resultado"]');

    controles.recibir({ tipo: PARTIDA_FINALIZADA, idPartida: PARTIDA, ganadores: [ANA] });
    // Desde UX-R2.3 el desenlace es un panel con la palabra grande delante
    // (HU-JUE-017 CA-04), asi que se comprueba el detalle, no la cadena exacta.
    expect(resultado.textContent).toContain('Has ganado el combate.');
    expect(resultado.textContent).toContain('VICTORIA');

    controles.recibir({
      tipo: PARTIDA_FINALIZADA,
      idPartida: PARTIDA,
      ganadores: [ANA],
      reparto: [{ idJugador: ANA, creditos: 100 }],
    });
    expect(resultado.textContent).toMatch(/llevas 100 créditos/i);
  });
});

describe('montarControlesDeCombate', () => {
  test('hay un botón por rival, y ninguno para uno mismo', () => {
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

  test('los botones empiezan deshabilitados: todavía no se sabe de quien es el turno', () => {
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

// HU-DIS-003 · el motor de combate no responde durante la partida. El rechazo
// llega por la cola privada (errorDeCanal, contrato 1.3.0): los controles
// dicen que el combate esta limitado, siguen ahi, y se puede reintentar.
describe('montarControlesDeCombate · motor degradado (HU-DIS-003)', () => {
  const motorCaido = () => ({
    type: 'https://nexusbattles.local/errores/seccion-no-disponible',
    title: 'Motor de combate no disponible temporalmente',
    status: 503,
    detail: 'La sección de Motor de combate no esta disponible temporalmente.',
    seccion: 'Motor de combate',
    reintentarEnSegundos: 4,
    dependencia: 'motor-combate',
  });

  function conHueco() {
    document.body.innerHTML = `${VISTA}<div data-zona="degradacion" data-seccion="Combate" hidden></div>`;
  }

  test('pinta Sección degradada sobre los controles y los deja vivos: el turno sigue siendo mio', () => {
    conHueco();
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      turnoDe: ANA,
      alAtacar: () => {},
    });

    expect(controles.rechazar(motorCaido())).toBe(true);

    const degradada = document.querySelector('[data-zona="degradacion"] .seccion-degradada');
    expect(degradada).not.toBeNull();
    expect(degradada.textContent).toContain('Motor de combate no disponible temporalmente');
    expect(degradada.textContent).toContain('4 segundos');
    expect(document.querySelector('[data-atacar]').disabled).toBe(false);
    expect(document.querySelector('[data-zona="acciones"]').hidden).toBe(false);
  });

  test('Reintentar quita el aviso y vuelve a mandar la ultima acción', () => {
    conHueco();
    const alAtacar = jest.fn();
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      turnoDe: ANA,
      alAtacar,
    });
    document.querySelector('[data-atacar]').click();
    controles.rechazar(motorCaido());

    document.querySelector('.seccion-degradada__reintentar').click();

    expect(alAtacar).toHaveBeenCalledTimes(2);
    expect(alAtacar).toHaveBeenLastCalledWith({ idObjetivo: BRUNO, codigoAccion: 'ATAQUE_BASICO' });
    expect(document.querySelector('.seccion-degradada')).toBeNull();
  });

  test('una acción resuelta después limpia el aviso: el motor volvio', () => {
    conHueco();
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar: () => {},
    });
    controles.rechazar(motorCaido());

    controles.recibir(accionResuelta(80));

    expect(document.querySelector('.seccion-degradada')).toBeNull();
  });

  test('otro rechazo (no es tu turno) se dice en la zona de rechazo, no como degradacion', () => {
    document.body.innerHTML = `${VISTA}<div data-zona="degradacion" hidden></div><p data-zona="rechazo" role="alert" hidden></p>`;
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar: () => {},
    });

    const gestionado = controles.rechazar({
      type: 'https://nexusbattles.local/errores/no-es-tu-turno',
      title: 'No es tu turno',
      status: 409,
      detail: 'Espera a que juegue tu rival.',
    });

    expect(gestionado).toBe(true);
    expect(document.querySelector('.seccion-degradada')).toBeNull();
    const rechazo = document.querySelector('[data-zona="rechazo"]');
    expect(rechazo.hidden).toBe(false);
    expect(rechazo.textContent).toContain('Espera a que juegue tu rival.');
  });

  test('sin hueco de degradacion en la vista no revienta: devuelve false', () => {
    const controles = montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      alAtacar: () => {},
    });

    expect(controles.rechazar(motorCaido())).toBe(false);
  });
});

describe('recompensa por jugar (HU-JUE-012)', () => {
  const fin = {
    tipo: 'partida.finalizada',
    idPartida: 'p1',
    ganadores: [ANA],
    recompensa: [
      { idJugador: ANA, creditos: 2, ganador: true },
      { idJugador: BRUNO, creditos: 1, ganador: false, cofre: 'cofre-1' },
    ],
  };

  test('el texto dice cuantos créditos se ganan y por que, para quien mira', () => {
    expect(textoDelResultado(fin, ANA)).toBe('Has ganado el combate. Ganas 2 créditos por ganar.');
    expect(textoDelResultado(fin, BRUNO)).toBe(
      'Has perdido el combate. Ganas 1 crédito por participar. Además te llevas un cofre.',
    );
  });

  test('con apuesta y recompensa, las dos coletillas van en orden: primero la apuesta', () => {
    const conApuesta = { ...fin, reparto: [{ idJugador: ANA, creditos: 100 }] };
    expect(textoDelResultado(conApuesta, ANA)).toBe(
      'Has ganado el combate. Te llevas 100 créditos de la apuesta. Ganas 2 créditos por ganar.',
    );
  });

  test('sin recompensa en el aviso (pendiente o sancionado) no se inventa nada', () => {
    expect(recompensaDe({ ganadores: [ANA] }, ANA)).toBeNull();
    expect(recompensaDe(fin, 'otro')).toBeNull();
    expect(textoDelResultado({ ganadores: [ANA] }, ANA)).toBe('Has ganado el combate.');
  });

  test('el aviso que llega despues con la recompensa no es un duplicado del que llego sin ella', () => {
    const registro = registroDeAvisos();
    const sin = { tipo: PARTIDA_FINALIZADA, idPartida: 'p1', ganadores: [ANA] };
    expect(registro.yaVisto(sin)).toBe(false);
    expect(registro.yaVisto(fin)).toBe(false);
    expect(registro.yaVisto(fin)).toBe(true);
  });
});

// --------------------------------------------------------------- el turno

describe('textoDelTurno()', () => {
  test('sin turno conocido no dice nada, en vez de inventarse uno', () => {
    expect(textoDelTurno(null, participantes(), ANA)).toEqual({ texto: '', mio: false });
  });

  test('mi turno se dice en segunda persona y se marca como mio', () => {
    expect(textoDelTurno(ANA, participantes(), ANA)).toEqual({ texto: 'Es tu turno', mio: true });
  });

  test('el turno de otra persona nombra a su heroe', () => {
    expect(textoDelTurno(BRUNO, participantes(), ANA)).toEqual({
      texto: 'Turno de Centinela',
      mio: false,
    });
  });

  test('el turno de la maquina se distingue del de una persona', () => {
    const conIA = [
      participantes()[0],
      { jugador: { id: BRUNO }, heroe: { nombre: 'Golem' }, esIA: true },
    ];

    expect(textoDelTurno(BRUNO, conIA, ANA).texto).toBe('Juega la máquina (Golem)');
  });

  test('un identificador que no esta en pantalla no deja el indicador en blanco', () => {
    expect(textoDelTurno('44444444-4444-4444-4444-444444444444', participantes(), ANA)).toEqual({
      texto: 'Turno de otro participante',
      mio: false,
    });
  });
});

describe('indicador de turno en la vista', () => {
  /**
   * El turno era invisible: lo unico que cambiaba era que los botones de
   * atacar estuvieran grises. Estas pruebas afirman que ahora se dice.
   */
  function montar(turnoDe) {
    return montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      turnoDe,
      alAtacar: () => {},
    });
  }

  test('al montar con el turno propio lo dice y marca la barra', () => {
    montar(ANA);

    const indicador = document.querySelector('[data-zona="turno"]');
    expect(indicador.hidden).toBe(false);
    expect(indicador.textContent).toBe('Es tu turno');
    expect(indicador.dataset.mio).toBe('true');
    expect(document.querySelector(`[data-jugador="${ANA}"]`).dataset.turno).toBe('si');
    expect(document.querySelector(`[data-jugador="${BRUNO}"]`).dataset.turno).toBeUndefined();
  });

  test('es una region viva: se anuncia sin que nadie mire la pantalla', () => {
    montar(ANA);

    expect(document.querySelector('[data-zona="turno"]').getAttribute('role')).toBe('status');
  });

  test('sin turno conocido el indicador no aparece', () => {
    montar(undefined);

    expect(document.querySelector('[data-zona="turno"]').hidden).toBe(true);
  });

  test('un cambio de turno mueve el texto y la marca a quien juega', () => {
    const controles = montar(ANA);

    controles.recibir({ tipo: TURNO_CAMBIADO, idPartida: PARTIDA, idJugador: BRUNO });

    const indicador = document.querySelector('[data-zona="turno"]');
    expect(indicador.textContent).toBe('Turno de Centinela');
    expect(indicador.dataset.mio).toBe('false');
    expect(document.querySelector(`[data-jugador="${BRUNO}"]`).dataset.turno).toBe('si');
    expect(document.querySelector(`[data-jugador="${ANA}"]`).dataset.turno).toBeUndefined();
  });

  test('al acabar la partida ya no es el turno de nadie', () => {
    const controles = montar(ANA);

    controles.recibir({ tipo: PARTIDA_FINALIZADA, idPartida: PARTIDA, ganadores: [ANA] });

    // UX-GAME-4: el indicador no desaparece, pasa al tercer estado. Ya no
    // es el turno de nadie: ni es «mio» ni hay barra marcada.
    const turno = document.querySelector('[data-zona="turno"]');
    expect(turno.hidden).toBe(false);
    expect(turno.textContent).toBe('Combate finalizado');
    expect(turno.dataset.mio).toBe('false');
    expect(turno.dataset.fin).toBe('si');
    expect(document.querySelector(`[data-jugador="${ANA}"]`).dataset.turno).toBeUndefined();
  });

  test('un turno de OTRA partida no toca el indicador', () => {
    const controles = montar(ANA);

    controles.recibir({ tipo: TURNO_CAMBIADO, idPartida: 'otra', idJugador: BRUNO });

    expect(document.querySelector('[data-zona="turno"]').textContent).toBe('Es tu turno');
  });

  test('sin las zonas en el HTML los controles siguen funcionando', () => {
    document.body.innerHTML = `
      <div class="fila" data-zona="acciones"></div>
      <p data-zona="resultado" hidden></p>`;

    expect(() => montar(ANA)).not.toThrow();
  });
});

/**
 * HU-JUE-017 (RF-JUE-017) — la interfaz de combate, criterio por criterio.
 *
 * Estas pruebas no comprueban que el combate funcione (eso ya lo hacen las de
 * arriba): comprueban que se PRESENTE como pide la ficha. Son las que se caen
 * si alguien vuelve a poner un boton azul de formulario en la barra de
 * acciones o si el final de partida vuelve a ser un parrafo.
 */
describe('HU-JUE-017 · presentacion del combate (UX-R2.3)', () => {
  beforeEach(() => {
    document.body.innerHTML = VISTA;
  });

  const montar = (turnoDe) =>
    montarControlesDeCombate(document, {
      idPartida: PARTIDA,
      yo: ANA,
      participantes: participantes(),
      turnoDe,
      alAtacar: () => {},
    });

  test('CA-03 · cada acción lleva icono, no solo texto', () => {
    montar(ANA);
    const accion = document.querySelector('[data-atacar]');

    expect(accion.className).toContain('accion-combate');
    expect(accion.querySelector('.accion-combate__icono')).not.toBeNull();
    expect(accion.querySelector('use').getAttribute('href')).toContain('#espada');
  });

  test('CA-03 · el icono no sustituye al nombre del rival', () => {
    montar(ANA);
    expect(document.querySelector('[data-atacar]').textContent).toContain('Centinela');
  });

  test('fuera de turno se dice POR QUE, no solo se apaga', () => {
    montar(BRUNO);
    const accion = document.querySelector('[data-atacar]');

    expect(accion.disabled).toBe(true);
    expect(accion.className).toContain('accion-combate--fuera-de-turno');
    expect(accion.getAttribute('title')).toBe('No es tu turno');
    expect(accion.getAttribute('aria-label')).toContain('No es tu turno');
  });

  test('cuando toca, el motivo desaparece y el botón invita a atacar', () => {
    const controles = montar(BRUNO);
    controles.recibir({ tipo: TURNO_CAMBIADO, idPartida: PARTIDA, idJugador: ANA });

    const accion = document.querySelector('[data-atacar]');
    expect(accion.disabled).toBe(false);
    expect(accion.className).not.toContain('accion-combate--fuera-de-turno');
    expect(accion.getAttribute('title')).toMatch(/^Atacar a /);
  });

  test('CA-04 · el final de la partida es una vista de alto impacto', () => {
    const controles = montar(ANA);
    controles.recibir({ tipo: PARTIDA_FINALIZADA, idPartida: PARTIDA, ganadores: [ANA] });

    const panel = document.querySelector('[data-zona="resultado"] .panel-resultado');
    expect(panel).not.toBeNull();
    expect(panel.dataset.resultado).toBe('victoria');
    // La palabra, no solo el color: quien no distinga verde de rojo la lee.
    expect(panel.querySelector('.panel-resultado__palabra').textContent).toBe('VICTORIA');
  });

  test('CA-04 · perder también se ve, y no es el mismo panel en rojo', () => {
    const controles = montar(ANA);
    controles.recibir({ tipo: PARTIDA_FINALIZADA, idPartida: PARTIDA, ganadores: [BRUNO] });

    const panel = document.querySelector('[data-zona="resultado"] .panel-resultado');
    expect(panel.dataset.resultado).toBe('derrota');
    expect(panel.querySelector('.panel-resultado__palabra').textContent).toBe('DERROTA');
  });

  test('CA-04 · el reparto de créditos sale en el panel', () => {
    const controles = montar(ANA);
    controles.recibir({
      tipo: PARTIDA_FINALIZADA,
      idPartida: PARTIDA,
      ganadores: [ANA],
      recompensa: [{ idJugador: ANA, creditos: 40, ganador: true }],
    });

    expect(
      document.querySelector('[data-zona="resultado"] .panel-resultado__creditos').textContent,
    ).toBe('+40');
  });
});

describe('gano · la regla del desenlace, en un solo sitio', () => {
  test('gana quien esta en la lista de ganadores', () => {
    expect(gano({ ganadores: [ANA] }, ANA)).toBe(true);
    expect(gano({ ganadores: [BRUNO] }, ANA)).toBe(false);
  });

  test('por equipos se gana con los companeros aunque uno haya caido', () => {
    expect(gano({ ganadores: [BRUNO], equipoGanador: 2 }, ANA, 2)).toBe(true);
    expect(gano({ ganadores: [BRUNO], equipoGanador: 2 }, ANA, 1)).toBe(false);
  });

  test('sin ganadores nadie gano: es empate', () => {
    expect(gano({ ganadores: [] }, ANA)).toBe(false);
    expect(gano({}, ANA)).toBe(false);
  });

  test('coincide con lo que dice el texto, que es de donde se extrajo', () => {
    const aviso = { ganadores: [ANA] };
    expect(textoDelResultado(aviso, ANA)).toContain('Has ganado');
    expect(gano(aviso, ANA)).toBe(true);
  });
});

/* R10: lo que el panel de desenlace muestra. Tres estados, y una cifra que es
   la que de verdad le paso al saldo del jugador. */

describe('desenlaceDe · empate no es derrota', () => {
  test('sin ganadores es empate, no la derrota de todos', () => {
    const fin = { ganadores: [] };

    expect(empate(fin)).toBe(true);
    expect(desenlaceDe(fin, ANA)).toBe('empate');
    expect(desenlaceDe(fin, BRUNO)).toBe('empate');
    // Y el texto ya lo decia: era el panel el que contradecia al texto.
    expect(textoDelResultado(fin, ANA)).toMatch(/empate/i);
  });

  test('con ganador, cada uno ve el suyo', () => {
    const fin = { ganadores: [ANA] };

    expect(empate(fin)).toBe(false);
    expect(desenlaceDe(fin, ANA)).toBe('victoria');
    expect(desenlaceDe(fin, BRUNO)).toBe('derrota');
  });

  test('por equipos no hay empate: el aviso trae el equipo ganador', () => {
    const fin = { ganadores: [], equipoGanador: 2 };

    expect(empate(fin)).toBe(false);
    expect(desenlaceDe(fin, ANA, 2)).toBe('victoria');
    expect(desenlaceDe(fin, BRUNO, 1)).toBe('derrota');
  });
});

describe('netoDeCreditos · la cifra grande es el movimiento real', () => {
  /*
   * El defecto que arregla R10: el numero grande salia de `recompensa`, que
   * nunca resta (el contrato la declara `minimum: 0`). Quien perdia una apuesta
   * de 350 veia un «+2» enorme y la perdida solo en la frase de abajo.
   */
  test('quien pierde la apuesta ve un neto negativo, no la recompensa sola', () => {
    const fin = {
      ganadores: [BRUNO],
      reparto: [{ idJugador: ANA, creditos: -350 }],
      recompensa: [{ idJugador: ANA, creditos: 1, ganador: false }],
    };

    expect(netoDeCreditos(fin, ANA)).toBe(-349);
  });

  test('quien gana ve la apuesta mas la recompensa', () => {
    const fin = {
      ganadores: [ANA],
      reparto: [{ idJugador: ANA, creditos: 350 }],
      recompensa: [{ idJugador: ANA, creditos: 2, ganador: true }],
    };

    expect(netoDeCreditos(fin, ANA)).toBe(352);
  });

  test('con una sola de las dos, vale esa', () => {
    expect(netoDeCreditos({ recompensa: [{ idJugador: ANA, creditos: 2 }] }, ANA)).toBe(2);
    expect(netoDeCreditos({ reparto: [{ idJugador: ANA, creditos: -10 }] }, ANA)).toBe(-10);
  });

  test('sin ninguna de las dos es null: no se inventa un cero', () => {
    expect(netoDeCreditos({ ganadores: [ANA] }, ANA)).toBeNull();
    // Y lo de otro jugador no se cuela como propio.
    expect(netoDeCreditos({ reparto: [{ idJugador: BRUNO, creditos: 9 }] }, ANA)).toBeNull();
  });
});

describe('mezclarPorJugador · el final se anuncia por partes', () => {
  /*
   * La liquidacion tardia de la apuesta (HU-JUE-014 CA-06) llega en un segundo
   * aviso que trae `recompensa` VACIA. Quedarse con el ultimo aviso borraria la
   * recompensa ya anunciada, y la cifra grande cambiaria de significado a
   * mitad.
   */
  test('lo que traia el primer aviso no se pierde con el segundo', () => {
    const primero = [{ idJugador: ANA, creditos: 2, ganador: true }];

    expect(mezclarPorJugador(primero, [])).toEqual(primero);
    expect(mezclarPorJugador(primero, undefined)).toEqual(primero);
  });

  test('si el segundo trae lo del mismo jugador, manda el segundo', () => {
    const mezclado = mezclarPorJugador(
      [{ idJugador: ANA, creditos: 0 }],
      [{ idJugador: ANA, creditos: 2 }],
    );

    expect(mezclado).toEqual([{ idJugador: ANA, creditos: 2 }]);
  });

  test('cada jugador conserva su entrada', () => {
    const mezclado = mezclarPorJugador(
      [{ idJugador: ANA, creditos: 1 }],
      [{ idJugador: BRUNO, creditos: 2 }],
    );

    expect(mezclado).toHaveLength(2);
  });
});
