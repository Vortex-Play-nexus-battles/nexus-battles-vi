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

import { jest } from '@jest/globals';

import {
  montarSalaBatalla,
  montarHistorial,
  leerEstadoInicial,
  destinoDePartida,
  suscripcionDePartida,
  participantesParaElPanel,
  urlConPartida,
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

describe('urlConPartida (R17.4 · un F5 en pleno combate vuelve al combate)', () => {
  const BASE = 'http://nexus.test/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html';

  test('anota la partida y conserva la sala', () => {
    const url = new URL(urlConPartida(`${BASE}?sala=s-1`, ID_PARTIDA));
    expect(url.searchParams.get('sala')).toBe('s-1');
    expect(url.searchParams.get('partida')).toBe(ID_PARTIDA);
    expect(url.pathname).toMatch(/sala-batalla\.html$/);
  });

  test('no duplica la partida si ya estaba: la sustituye', () => {
    const url = new URL(urlConPartida(`${BASE}?sala=s-1&partida=vieja`, ID_PARTIDA));
    expect(url.searchParams.getAll('partida')).toEqual([ID_PARTIDA]);
  });

  test('conserva el hash', () => {
    expect(urlConPartida(`${BASE}?sala=s-1#chat`, ID_PARTIDA)).toMatch(/#chat$/);
  });

  test('el identificador va codificado: no puede colar otro parametro', () => {
    const url = new URL(urlConPartida(`${BASE}?sala=s-1`, 'x&sala=otra'));
    expect(url.searchParams.getAll('sala')).toEqual(['s-1']);
    expect(url.searchParams.get('partida')).toBe('x&sala=otra');
  });
});

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
    expect(conexion().textContent).toMatch(/sin conexión/i);
    // Sin conexion SI se ve: es lo que le pide algo a quien juega.
    expect(conexion().hidden).toBe(false);
  });

  // R17.4 — la sala de espera sigue la SALA por el canal: todavia no hay
  // partida a la que suscribirse, pero el canal esta abierto y funciona.
  test('en la sala de espera, con el canal abierto, dice «conectado» aunque no haya partida', () => {
    montarSalaBatalla(document, { canalConectado: true });

    expect(sinPartida().hidden).toBe(false);
    expect(conexion().className).toBe('conexion conexion--estable');
    expect(conexion().textContent).toMatch(/^Canal en tiempo real: Conectado$/);
    // Revisión del modo jugador del 6-oct (punto 13): «Canal en tiempo real:
    // Conectado» no se enseña; el estado sigue pintado para quien lo lea.
    expect(conexion().hidden).toBe(true);
    expect(conexion().dataset.estadoCanal).toBe('conectado');
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
    expect(conexion().hidden).toBe(true);
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

/*
 * Revisión del modo jugador del 6-oct, punto 17: «5, 4, 3, 2, 1, COMBATE».
 * La cuenta es de la vista (sin esperas en el servidor): la presentación se
 * cierra sola al llegar a cero y no la cierra el primer aviso del canal —lo
 * que llegue mientras tanto lo retiene el ritmo del combate—.
 */
describe('montarSalaBatalla · cuenta atrás del comienzo (punto 17)', () => {
  const CON_PRESENTACION = `${VISTA}<div data-zona="presentacion" hidden></div>`;

  function montarConOyentes(extra) {
    const oyentes = [];
    montarSalaBatalla(document, {
      idPartida: ID_PARTIDA,
      participantes: participantes(),
      yo: ANA,
      presentar: true,
      suscribir: (alRecibir) => oyentes.push(alRecibir),
      ...extra,
    });
    return (aviso) => oyentes.forEach((alRecibir) => alRecibir(aviso));
  }

  const presentacion = () => document.querySelector('[data-zona="presentacion-heroes"]');
  const turno = { tipo: 'partida.turno.cambiado', idPartida: ID_PARTIDA, idJugador: ANA };

  beforeEach(() => {
    document.body.innerHTML = CON_PRESENTACION;
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  test('cuenta 5…1, grita «¡COMBATE!» y se cierra sola; el primer aviso no la corta', () => {
    const entregar = montarConOyentes({ cuentaAtras: 5 });
    const cifra = () => presentacion()?.querySelector('[data-zona="cuenta-atras"]');

    expect(cifra().textContent).toBe('5');
    // Sin botón: no hay nada que pulsar mientras se cuenta.
    expect(presentacion().querySelector('[data-accion="entrar-al-combate"]')).toBeNull();

    entregar(turno);
    expect(presentacion()).not.toBeNull();

    const vistos = [];
    for (let i = 0; i < 4; i += 1) {
      jest.advanceTimersByTime(1000);
      vistos.push(cifra().textContent);
    }
    expect(vistos).toEqual(['4', '3', '2', '1']);

    jest.advanceTimersByTime(1000);
    expect(cifra().textContent).toBe('¡COMBATE!');
    expect(cifra().dataset.final).toBe('si');

    jest.advanceTimersByTime(800);
    expect(presentacion()).toBeNull();
    expect(document.querySelector('[data-zona="presentacion"]').hidden).toBe(true);
  });

  test('sin cuenta atrás, como siempre: el primer aviso de la partida la cierra', () => {
    const entregar = montarConOyentes({ cuentaAtras: 0 });
    expect(presentacion().querySelector('[data-accion="entrar-al-combate"]')).not.toBeNull();

    entregar(turno);
    expect(presentacion()).toBeNull();
  });
});

/*
 * Punto 19: «LO QUE HA PASADO» ocupaba la barra de mando. Ahora es un botón
 * «Historial» que lo abre encima; cerrado se oculta a la vista, no al lector.
 */
describe('montarHistorial (punto 19)', () => {
  const HISTORIAL = `
    <section data-zona="historial">
      <button type="button" data-accion="ver-historial" aria-controls="registro-combate">
        Historial
      </button>
      <div id="registro-combate" data-zona="registro"></div>
    </section>
  `;

  const boton = () => document.querySelector('[data-accion="ver-historial"]');
  const cuerpo = () => document.querySelector('[data-zona="registro"]');

  beforeEach(() => {
    document.body.innerHTML = HISTORIAL;
  });

  test('nace cerrado; el botón lo abre y lo cierra diciendo su estado', () => {
    montarHistorial(document);
    expect(cuerpo().dataset.abierto).toBe('no');
    expect(boton().getAttribute('aria-expanded')).toBe('false');
    expect(boton().textContent).toBe('Historial');

    boton().click();
    expect(cuerpo().dataset.abierto).toBe('si');
    expect(boton().getAttribute('aria-expanded')).toBe('true');
    expect(boton().textContent).toBe('Ocultar historial');

    boton().click();
    expect(cuerpo().dataset.abierto).toBe('no');
  });

  test('Escape lo cierra y devuelve el foco al botón', () => {
    montarHistorial(document);
    boton().click();
    cuerpo().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(cuerpo().dataset.abierto).toBe('no');
    expect(document.activeElement).toBe(boton());
  });

  test('cerrado, el registro no se alcanza con el tabulador; abierto, sí (aunque llegue después)', async () => {
    montarHistorial(document);
    // El registro lo crean los controles del combate, después de montar esto.
    const registro = document.createElement('div');
    registro.setAttribute('role', 'log');
    registro.setAttribute('tabindex', '0');
    cuerpo().append(registro);
    await new Promise((resolver) => setTimeout(resolver, 0));
    expect(registro.getAttribute('tabindex')).toBe('-1');

    boton().click();
    expect(registro.getAttribute('tabindex')).toBe('0');
    boton().click();
    expect(registro.getAttribute('tabindex')).toBe('-1');
  });

  test('sin su marcado no monta nada', () => {
    document.body.innerHTML = '';
    expect(montarHistorial(document)).toBeNull();
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
