/**
 * Vista de sala en batalla — HU-SAL-005.
 *
 * Se prueba lo que la vista aporta por encima del panel: que sin partida
 * cargada muestre su estado vacio en vez de un panel a medias, que el
 * indicador de conexion no mienta, y que un evento entregado por el canal
 * llegue de verdad hasta las barras.
 *
 * Lo que ya cubre `panel-vidas.test.js` no se repite aqui.
 */

import {
  montarSalaBatalla,
  leerEstadoInicial,
  destinoDePartida,
  suscripcionDePartida,
  participantesParaElPanel,
} from './sala-batalla.js';

const ID_PARTIDA = '11111111-1111-1111-1111-111111111111';
const ANA = '22222222-2222-2222-2222-222222222222';
const BRUNO = '33333333-3333-3333-3333-333333333333';
const MAQUINA = '44444444-4444-4444-4444-444444444444';

/** El mismo marcado que trae sala-batalla.html, sin la cabecera. */
const VISTA = `
  <span class="conexion" data-zona="conexion"></span>
  <div class="estado-vista" data-zona="sin-partida"><p class="t-meta"></p></div>
  <section class="tarjeta pila" data-zona="panel" hidden>
    <div class="pila" data-zona="vidas"></div>
  </section>
`;

function participantes() {
  return [
    {
      jugador: { id: ANA, apodo: 'Ana' },
      heroe: { id: 'h1', nombre: 'Arquero del Norte', vidaActual: 100, vidaMaxima: 100 },
      esIA: false,
    },
  ];
}

beforeAll(() => {
  document.documentElement.style.setProperty('--vida-umbral-alto', '60');
  document.documentElement.style.setProperty('--vida-umbral-medio', '40');
});

beforeEach(() => {
  document.body.innerHTML = VISTA;
});

const conexion = () => document.querySelector('[data-zona="conexion"]');
const sinPartida = () => document.querySelector('[data-zona="sin-partida"]');
const panel = () => document.querySelector('[data-zona="panel"]');

describe('montarSalaBatalla', () => {
  test('sin partida cargada muestra el estado vacio y no pinta ninguna barra', () => {
    montarSalaBatalla(document);

    expect(sinPartida().hidden).toBe(false);
    expect(panel().hidden).toBe(true);
    expect(document.querySelectorAll('.barra-vida')).toHaveLength(0);
  });

  test('con participantes pinta el panel y esconde el estado vacio', () => {
    montarSalaBatalla(document, { idPartida: ID_PARTIDA, participantes: participantes() });

    expect(sinPartida().hidden).toBe(true);
    expect(panel().hidden).toBe(false);
    expect(document.querySelectorAll('.barra-vida')).toHaveLength(1);
  });

  test('sin transporte del canal la vista lo dice, en vez de aparentar conexión', () => {
    montarSalaBatalla(document, { idPartida: ID_PARTIDA, participantes: participantes() });

    expect(conexion().className).toBe('conexion conexion--sin-conexion');
    expect(conexion().textContent).toMatch(/no conectado/i);
  });

  // R17.4 — la sala de espera sigue la SALA por el canal: todavia no hay
  // partida a la que suscribirse, pero el canal esta abierto y funciona.
  test('en la sala de espera, con el canal abierto, dice «conectado» aunque no haya partida', () => {
    montarSalaBatalla(document, { canalConectado: true });

    expect(sinPartida().hidden).toBe(false);
    expect(conexion().className).toBe('conexion conexion--estable');
    expect(conexion().textContent).toMatch(/^Canal en tiempo real conectado$/);
  });

  test('en la sala de espera, sin canal, sigue diciendo que no hay conexión', () => {
    montarSalaBatalla(document, { canalConectado: false });

    expect(conexion().className).toBe('conexion conexion--sin-conexion');
  });

  test('con transporte del canal el indicador pasa a estable', () => {
    montarSalaBatalla(document, {
      idPartida: ID_PARTIDA,
      participantes: participantes(),
      suscribir: () => {},
    });

    expect(conexion().className).toBe('conexion conexion--estable');
  });

  test('un evento entregado por el canal llega hasta la barra', () => {
    let entregar;

    montarSalaBatalla(document, {
      idPartida: ID_PARTIDA,
      participantes: participantes(),
      suscribir: (alRecibir) => {
        entregar = alRecibir;
      },
    });

    entregar({
      tipo: 'partida.accion.resuelta',
      idPartida: ID_PARTIDA,
      idEjecutor: ANA,
      accion: { codigo: 'GOLPE', nombre: 'Golpe' },
      afectados: [{ idJugador: ANA, vidaActual: 35, vidaMaxima: 100, diferencia: -65 }],
    });

    const barra = document.querySelector(`[data-jugador="${ANA}"]`);
    expect(barra.querySelector('.barra-vida__valor').textContent).toBe('35/100');
    expect(barra.dataset.estado).toBe('bajo');
  });
});

describe('participantesParaElPanel · adaptar GET /partidas/{id} al panel', () => {
  /** Respuesta de `GET /partidas/{id}` tal como la define el OpenAPI. */
  function partidaDeLaApi(enCombate) {
    return {
      id: ID_PARTIDA,
      idSala: '99999999-9999-9999-9999-999999999999',
      estado: 'EN_CURSO',
      participantes: enCombate,
      turnoActual: { idJugador: ANA, numeroTurno: 1, segundosRestantes: null },
      recompensaEnJuego: 320,
      iniciadaEn: '2026-09-17T20:00:00Z',
    };
  }

  const conHeroe = {
    jugador: ANA,
    heroe: { id: 'h1', nombre: 'Arquero del Norte', vidaActual: 100, vidaMaxima: 100 },
    esIA: false,
    listo: true,
    equipo: 1,
    creditosApostados: 320,
  };
  const sinHeroe = {
    jugador: BRUNO,
    heroe: null,
    esIA: false,
    listo: true,
    equipo: null,
    creditosApostados: 320,
  };
  const maquina = {
    jugador: MAQUINA,
    heroe: { id: 'h2', nombre: 'Centinela', vidaActual: 80, vidaMaxima: 120 },
    esIA: true,
    listo: true,
    equipo: null,
    creditosApostados: 0,
  };

  test('traduce jugador y heroe a la forma que espera el panel', () => {
    expect(participantesParaElPanel(partidaDeLaApi([conHeroe]))).toEqual([
      {
        jugador: { id: ANA },
        heroe: conHeroe.heroe,
        esIA: false,
        equipo: 1,
      },
    ]);
  });

  test('descarta a quien no tiene heroe conocido en vez de inventarle una vida', () => {
    const adaptados = participantesParaElPanel(partidaDeLaApi([conHeroe, sinHeroe, maquina]));

    expect(adaptados.map((p) => p.jugador.id)).toEqual([ANA, MAQUINA]);
  });

  test('conserva la marca de la IA: quien mira tiene que distinguirla de una persona', () => {
    const adaptados = participantesParaElPanel(partidaDeLaApi([maquina]));

    expect(adaptados[0].esIA).toBe(true);
  });

  test('una partida sin participantes o ausente no revienta', () => {
    expect(participantesParaElPanel(partidaDeLaApi([]))).toEqual([]);
    expect(participantesParaElPanel(null)).toEqual([]);
  });

  describe('montada en la vista', () => {
    test('con la partida de la API pinta una barra por participante con héroe', () => {
      montarSalaBatalla(document, { partida: partidaDeLaApi([conHeroe, maquina]) });

      expect(panel().hidden).toBe(false);
      expect(document.querySelectorAll('.barra-vida')).toHaveLength(2);
      expect(document.querySelector('[data-zona="vidas"]').dataset.partida).toBe(ID_PARTIDA);
    });

    test('una partida en curso sin ningún héroe conocido lo explica, no deja un panel vacío', () => {
      montarSalaBatalla(document, { partida: partidaDeLaApi([sinHeroe]) });

      expect(panel().hidden).toBe(true);
      expect(sinPartida().hidden).toBe(false);
      expect(sinPartida().querySelector('.t-meta').textContent).toMatch(/héroe de ninguno/i);
      expect(document.querySelectorAll('.barra-vida')).toHaveLength(0);
    });
  });
});

describe('leerEstadoInicial', () => {
  test('sin bloque incrustado no hay partida', () => {
    expect(leerEstadoInicial(document)).toBeNull();
  });

  test('lee la partida que el servidor deje incrustada en la pagina', () => {
    document.body.innerHTML += `
      <script type="application/json" data-estado-inicial>
        {"idPartida": "${ID_PARTIDA}", "participantes": []}
      </script>
    `;

    expect(leerEstadoInicial(document)).toEqual({ idPartida: ID_PARTIDA, participantes: [] });
  });
});

describe('transporte real del canal de la partida', () => {
  test('el destino es el canal partidaEstado del AsyncAPI', () => {
    expect(destinoDePartida(ID_PARTIDA)).toBe(`/tema/partidas/${ID_PARTIDA}`);
  });

  test('suscripcionDePartida usa el cliente STOMP ya conectado, sin inventar otro transporte', () => {
    const suscripciones = [];
    const cliente = {
      suscribir(destino, alRecibir) {
        suscripciones.push({ destino, alRecibir });
        return 'sub-1';
      },
    };

    montarSalaBatalla(document, {
      idPartida: ID_PARTIDA,
      participantes: participantes(),
      suscribir: suscripcionDePartida(cliente, ID_PARTIDA),
    });

    expect(suscripciones).toHaveLength(1);
    expect(suscripciones[0].destino).toBe(`/tema/partidas/${ID_PARTIDA}`);
    expect(conexion().className).toContain('conexion--estable');

    // Lo que el broker entregue por ese destino mueve la barra, con la forma
    // exacta de AccionResuelta (vidaActual/vidaMaxima por afectado, sin color).
    suscripciones[0].alRecibir({
      tipo: 'partida.accion.resuelta',
      idPartida: ID_PARTIDA,
      idEjecutor: ANA,
      accion: { codigo: 'GOLPE', nombre: 'Golpe' },
      afectados: [{ idJugador: ANA, vidaActual: 50, vidaMaxima: 100, diferencia: -50 }],
    });

    const barra = document.querySelector(`[data-jugador="${ANA}"]`);
    expect(barra.querySelector('.barra-vida__valor').textContent).toBe('50/100');
    expect(barra.dataset.estado).toBe('medio');
  });
});
