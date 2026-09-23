/**
 * Campo de combate — HU-JUE-017 CA-03 (UX-R2.3).
 *
 * Lo que se comprueba es que la «ubicacion aleatoria» de la ficha sea
 * **estable**: si cambiara en cada repintado, cada accion resuelta
 * teletransportaria a los seis participantes. Eso no es lo que pide el
 * requisito y ademas no habria forma de probarlo.
 */

import { dispersion, marcarTurnoEnElCampo, pintarCampo, repartirEnElCampo } from './campo.js';

const heroe = (nombre) => ({ nombre, vidaActual: 100, vidaMaxima: 100 });

const PARTIDA = [
  { jugador: { id: 'u-yo' }, heroe: heroe('Sombra'), esIA: false },
  { jugador: { id: 'u-rival' }, heroe: heroe('Golem'), esIA: false },
];

describe('dispersion', () => {
  test('el mismo texto da siempre el mismo numero', () => {
    expect(dispersion('u-yo')).toBe(dispersion('u-yo'));
  });

  test('textos distintos caen en sitios distintos', () => {
    expect(dispersion('u-yo')).not.toBe(dispersion('u-rival'));
  });

  test('la semilla da un segundo numero del mismo texto', () => {
    expect(dispersion('u-yo', 7)).not.toBe(dispersion('u-yo'));
  });

  test('siempre dentro de [0, 1)', () => {
    for (const texto of ['', 'a', 'u-1234-abcd', 'ñ', '0'.repeat(64)]) {
      const n = dispersion(texto);
      expect(n).toBeGreaterThanOrEqual(0);
      expect(n).toBeLessThan(1);
    }
  });
});

describe('repartirEnElCampo', () => {
  test('cada participante recibe un puesto', () => {
    expect(repartirEnElCampo(PARTIDA, 'u-yo')).toHaveLength(2);
  });

  test('repetir el reparto devuelve exactamente lo mismo', () => {
    // Esta es la prueba que importa: sin ella, cada `partida.accion.resuelta`
    // movia a todo el mundo de sitio.
    expect(repartirEnElCampo(PARTIDA, 'u-yo')).toEqual(repartirEnElCampo(PARTIDA, 'u-yo'));
  });

  test('quien mira va a su mitad y el rival a la otra', () => {
    const [mio, rival] = repartirEnElCampo(PARTIDA, 'u-yo').sort((a, b) =>
      a.lado.localeCompare(b.lado),
    );
    expect(mio.lado).toBe('propio');
    expect(mio.izquierda).toBeLessThan(50);
    expect(rival.lado).toBe('rival');
    expect(rival.izquierda).toBeGreaterThan(50);
  });

  test('en modo por equipos, los companeros comparten lado', () => {
    const porEquipos = [
      { jugador: { id: 'a' }, heroe: heroe('A'), equipo: 1 },
      { jugador: { id: 'b' }, heroe: heroe('B'), equipo: 1 },
      { jugador: { id: 'c' }, heroe: heroe('C'), equipo: 2 },
    ];
    const puestos = repartirEnElCampo(porEquipos, 'a');
    const lado = (id) => puestos.find((p) => p.participante.jugador.id === id).lado;

    expect(lado('a')).toBe('propio');
    expect(lado('b')).toBe('propio');
    expect(lado('c')).toBe('rival');
  });

  test('nadie cae bajo el HUD ni bajo los controles', () => {
    // Las dos franjas reservadas: arriba la vida y el turno (CA-02), abajo los
    // menus (CA-01 dice que no se superponen al campo).
    for (const puesto of repartirEnElCampo(PARTIDA, 'u-yo')) {
      expect(puesto.arriba).toBeGreaterThanOrEqual(18);
      expect(puesto.arriba).toBeLessThanOrEqual(78);
      expect(puesto.izquierda).toBeGreaterThanOrEqual(0);
      expect(puesto.izquierda).toBeLessThanOrEqual(100);
    }
  });

  test('seis participantes no se amontonan dentro de su bando', () => {
    // Entre bandos si pueden coincidir de altura: estan en mitades distintas
    // del campo, uno a la izquierda y otro a la derecha. Lo que no puede pasar
    // es que dos companeros queden uno encima del otro.
    const seis = Array.from({ length: 6 }, (_, i) => ({
      jugador: { id: `u-${i}` },
      heroe: heroe(`H${i}`),
      equipo: i % 2 === 0 ? 1 : 2,
    }));
    const puestos = repartirEnElCampo(seis, 'u-0');

    for (const bando of ['propio', 'rival']) {
      const alturas = puestos.filter((p) => p.lado === bando).map((p) => p.arriba);
      expect(alturas).toHaveLength(3);
      expect(new Set(alturas).size).toBe(3);
    }
  });

  test('una lista vacia no rompe', () => {
    expect(repartirEnElCampo(null, 'u-yo')).toEqual([]);
    expect(repartirEnElCampo([], 'u-yo')).toEqual([]);
  });
});

describe('pintarCampo', () => {
  let campo;
  beforeEach(() => {
    campo = document.createElement('div');
    document.body.append(campo);
  });
  afterEach(() => campo.remove());

  test('pinta un puesto por participante con su retrato', () => {
    pintarCampo(campo, PARTIDA, 'u-yo');

    expect(campo.querySelectorAll('[data-puesto]')).toHaveLength(2);
    expect(campo.querySelectorAll('.marco-heroe')).toHaveLength(2);
    expect(campo.textContent).toContain('Sombra');
  });

  test('es decorativo: la vida y el turno se leen en el HUD, no aquí', () => {
    pintarCampo(campo, PARTIDA, 'u-yo');
    expect(campo.getAttribute('aria-hidden')).toBe('true');
  });

  test('la posición va en el estilo, en porcentaje del campo', () => {
    pintarCampo(campo, PARTIDA, 'u-yo');
    const estilo = campo.querySelector('[data-puesto="u-yo"]').getAttribute('style');
    expect(estilo).toMatch(/left:\s*[\d.]+%/);
    expect(estilo).toMatch(/top:\s*[\d.]+%/);
  });

  test('repintar no acumula puestos fantasma', () => {
    pintarCampo(campo, PARTIDA, 'u-yo');
    pintarCampo(campo, PARTIDA, 'u-yo');
    expect(campo.querySelectorAll('[data-puesto]')).toHaveLength(2);
  });

  test('los caidos se marcan', () => {
    pintarCampo(campo, PARTIDA, 'u-yo', { caidos: new Set(['u-rival']) });
    expect(campo.querySelector('[data-puesto="u-rival"]').className).toContain(
      'campo__puesto--caido',
    );
    expect(campo.querySelector('[data-puesto="u-yo"]').className).not.toContain('--caido');
  });

  test('se niega a trabajar sobre algo que no es un elemento', () => {
    expect(() => pintarCampo(null, PARTIDA, 'u-yo')).toThrow(TypeError);
  });
});

describe('marcarTurnoEnElCampo', () => {
  test('marca a uno y desmarca a los demas', () => {
    const campo = document.createElement('div');
    pintarCampo(campo, PARTIDA, 'u-yo');

    marcarTurnoEnElCampo(campo, 'u-rival');
    expect(campo.querySelector('[data-puesto="u-rival"]').dataset.turno).toBe('si');
    expect(campo.querySelector('[data-puesto="u-yo"]').dataset.turno).toBeUndefined();

    marcarTurnoEnElCampo(campo, null);
    expect(campo.querySelector('[data-puesto="u-rival"]').dataset.turno).toBeUndefined();
  });
});
