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

import { montarSalaBatalla, leerEstadoInicial } from './sala-batalla.js';

const ID_PARTIDA = '11111111-1111-1111-1111-111111111111';
const ANA = '22222222-2222-2222-2222-222222222222';

/** El mismo marcado que trae sala-batalla.html, sin la cabecera. */
const VISTA = `
  <span class="conexion" data-zona="conexion"></span>
  <div class="estado-vista" data-zona="sin-partida"></div>
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

  test('sin transporte del canal la vista lo dice, en vez de aparentar conexion', () => {
    montarSalaBatalla(document, { idPartida: ID_PARTIDA, participantes: participantes() });

    expect(conexion().className).toBe('conexion conexion--sin-conexion');
    expect(conexion().textContent).toMatch(/no conectado/i);
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
