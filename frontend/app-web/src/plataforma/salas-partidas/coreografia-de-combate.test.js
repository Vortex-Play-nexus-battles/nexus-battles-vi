/**
 * Coreografía del combate — HU-UX-003, MOTION-2.
 *
 * Se prueba el COMPORTAMIENTO, nunca un milisegundo: quien actúa pasa a su
 * estado, quien recibe al suyo, la cifra nace en el impacto con su texto y
 * su icono, y al final todo vuelve a su sitio. La Web Animations API es de
 * mentira y se resuelve a mano, así que cada fase se puede observar.
 */

import { jest } from '@jest/globals';
import { ATRIBUTO_DE_PRUEBA } from '../../comun/ui/motion.js';
import {
  LUNGE_CRITICO_PX,
  LUNGE_PX,
  PARTICULAS,
  coreografiaDeCombate,
} from './coreografia-de-combate.js';

const YO = 'u-yo';
const RIVAL = 'u-rival';
const ALIADO = 'u-aliado';

const PARTICIPANTES = [
  { jugador: { id: YO }, heroe: { nombre: 'Mío', prototipo: 'Guerrero Tanque' }, esIA: false },
  { jugador: { id: ALIADO }, heroe: { nombre: 'Aliado', prototipo: 'Chamán' }, esIA: false },
  { jugador: { id: RIVAL }, heroe: { nombre: 'Rival', prototipo: 'Mago Fuego' }, esIA: true },
];

/** Un campo como el de `campo.js`: puesto → figura → retrato, y el nombre. */
function campoDePrueba() {
  const campo = document.createElement('div');
  campo.className = 'combate__campo';
  for (const [id, lado] of [
    [YO, 'propio'],
    [ALIADO, 'propio'],
    [RIVAL, 'rival'],
  ]) {
    const puesto = document.createElement('div');
    puesto.className = `campo__puesto campo__puesto--${lado}`;
    puesto.dataset.puesto = id;
    puesto.dataset.lado = lado;
    const figura = document.createElement('div');
    figura.className = 'marco-heroe';
    const retrato = document.createElement('div');
    retrato.className = 'marco-heroe__retrato';
    figura.append(retrato);
    const nombre = document.createElement('span');
    nombre.className = 'campo__nombre';
    nombre.textContent = id;
    puesto.append(figura, nombre);
    campo.append(puesto);
  }
  document.body.append(campo);
  return campo;
}

/** WAAPI de mentira: cada `animate` queda registrado y se termina a mano. */
function conWaapi() {
  const vivas = [];
  Element.prototype.animate = jest.fn(function animate(cuadros, opciones) {
    let terminar;
    const animacion = {
      elemento: this,
      cuadros,
      opciones,
      playState: 'running',
      finished: new Promise((resolver) => {
        terminar = resolver;
      }),
      cancel: jest.fn(),
      terminar() {
        animacion.playState = 'finished';
        terminar();
      },
    };
    vivas.push(animacion);
    return animacion;
  });
  Element.prototype.getAnimations = function getAnimations() {
    return vivas.filter((a) => a.elemento === this && a.playState === 'running');
  };
  return vivas;
}

function sinWaapi() {
  delete Element.prototype.animate;
  delete Element.prototype.getAnimations;
}

const tick = () => new Promise((resolver) => setTimeout(resolver, 0));
/** Deja pasar el hit-stop (60–90 ms): se espera a que ocurra, no se afirma su duración. */
const trasElHitStop = () => new Promise((resolver) => setTimeout(resolver, 120));
const terminarVivas = (vivas) => {
  for (const a of vivas) {
    if (a.playState === 'running') {
      a.terminar();
    }
  }
};

function ataque({
  ejecutor = YO,
  objetivo = RIVAL,
  categoria = 'CAUSAR_DANO',
  diferencia = -7,
  efectos = [],
  esEpica = false,
} = {}) {
  return {
    tipo: 'partida.accion.resuelta',
    idEjecutor: ejecutor,
    idObjetivo: objetivo,
    accion: { codigo: 'ATAQUE_BASICO', nombre: categoria, tipo: 'ATAQUE', esEpica },
    afectados: [
      { idJugador: ejecutor, vidaActual: 40, vidaMaxima: 52, diferencia: 0 },
      {
        idJugador: objetivo,
        vidaActual: 50 + diferencia,
        vidaMaxima: 60,
        diferencia,
        efectosActivos: efectos,
      },
    ],
  };
}

const IMPACTO = [{ idJugador: RIVAL, cifra: '−7', unidad: 'VIDA', etiqueta: null, tono: 'dano' }];

afterEach(() => {
  document.body.innerHTML = '';
  document.documentElement.removeAttribute(ATRIBUTO_DE_PRUEBA);
  delete globalThis.matchMedia;
  sinWaapi();
  jest.useRealTimers();
});

describe('un ataque, fase a fase', () => {
  test('anticipa, se lanza, golpea (estado + cifra con icono), espera y vuelve', async () => {
    const vivas = conWaapi();
    const campo = campoDePrueba();
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    const actor = campo.querySelector(`[data-puesto="${YO}"]`);
    const rival = campo.querySelector(`[data-puesto="${RIVAL}"]`);
    const figura = actor.querySelector('.marco-heroe');

    const corriendo = coreo.accion(ataque(), IMPACTO);
    // Fase 1: el actor está en su estado y anticipa (hacia atrás).
    expect(actor.dataset.accion).toBe('ataca');
    expect(actor.dataset.fx).toBe('PHYSICAL_HIT');
    expect(vivas).toHaveLength(1);
    expect(vivas[0].elemento).toBe(figura);
    expect(vivas[0].cuadros[1].transform).toContain('translate3d(-8px');
    expect(rival.dataset.golpe).toBeUndefined();
    expect(rival.querySelector('.impacto')).toBeNull();

    vivas[0].terminar();
    await tick();
    // Fase 2: el lunge, hacia el rival (bando propio: a la derecha).
    expect(vivas).toHaveLength(2);
    expect(vivas[1].cuadros[1].transform).toContain(`translate3d(${LUNGE_PX}px`);
    expect(vivas[1].opciones.duration).toBe(150);

    vivas[1].terminar();
    await tick();
    // Fase 3: el impacto. El rival acusa el golpe y la cifra nace AHORA.
    expect(rival.dataset.golpe).toBe('golpe');
    const cifra = rival.querySelector('.impacto');
    expect(cifra).not.toBeNull();
    expect(cifra.textContent).toBe('−7 VIDA');
    expect(cifra.querySelector('.impacto__icono use').getAttribute('href')).toMatch(/#espada$/);
    const golpe = vivas.find((a) => a.elemento === rival.querySelector('.marco-heroe'));
    expect(golpe).toBeDefined();
    // Retrocede hacia donde vino el golpe: el rival, a la derecha.
    expect(golpe.cuadros[1].transform).toBe('translate3d(14px, 0, 0)');
    expect(golpe.opciones.composite).toBe('add');

    terminarVivas(vivas);
    await trasElHitStop();
    // Fase 4 (tras el hit-stop): el regreso.
    const regreso = vivas.find(
      (a) => a.elemento === figura && a.cuadros[1].transform === 'translate3d(0, 0, 0) scale(1)',
    );
    expect(regreso).toBeDefined();
    regreso.terminar();
    await corriendo;
    // Todo vuelve a su sitio: sin estados y sin animaciones colgando.
    expect(actor.dataset.accion).toBeUndefined();
    expect(actor.dataset.fx).toBeUndefined();
    expect(rival.dataset.golpe).toBeUndefined();
    expect(figura.getAnimations()).toEqual([]);
  });

  test('un crítico se lanza más lejos, golpea más fuerte y lo dice', async () => {
    const vivas = conWaapi();
    const campo = campoDePrueba();
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    const rival = campo.querySelector(`[data-puesto="${RIVAL}"]`);
    const corriendo = coreo.accion(ataque({ categoria: 'CAUSAR_DANO_CRITICO', diferencia: -12 }), [
      { idJugador: RIVAL, cifra: '−12', unidad: 'VIDA', etiqueta: 'Crítico', tono: 'critico' },
    ]);
    vivas[0].terminar();
    await tick();
    expect(vivas[1].cuadros[1].transform).toContain(`translate3d(${LUNGE_CRITICO_PX}px`);
    expect(vivas[1].opciones.duration).toBe(170);
    vivas[1].terminar();
    await tick();
    expect(rival.dataset.golpe).toBe('critico');
    expect(rival.querySelector('.impacto').textContent).toBe('−12 VIDACrítico');
    expect(rival.querySelector('.impacto__icono use').getAttribute('href')).toMatch(/#rayo$/);
    const golpe = vivas.find((a) => a.elemento === rival.querySelector('.marco-heroe'));
    expect(golpe.cuadros[1].transform).toBe('translate3d(20px, 0, 0)');
    terminarVivas(vivas);
    await trasElHitStop();
    terminarVivas(vivas);
    await corriendo;
  });

  test('el rival se lanza hacia la izquierda y el golpeado retrocede hacia la izquierda', async () => {
    const vivas = conWaapi();
    const campo = campoDePrueba();
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    const corriendo = coreo.accion(ataque({ ejecutor: RIVAL, objetivo: YO }), [
      { idJugador: YO, cifra: '−7', unidad: 'VIDA', tono: 'dano' },
    ]);
    expect(campo.querySelector(`[data-puesto="${RIVAL}"]`).dataset.fx).toBe('MAGIC_FIRE');
    vivas[0].terminar();
    await tick();
    expect(vivas[1].cuadros[1].transform).toContain(`translate3d(-${LUNGE_PX}px`);
    vivas[1].terminar();
    await tick();
    const mio = campo.querySelector(`[data-puesto="${YO}"]`);
    expect(mio.dataset.golpe).toBe('golpe');
    expect(mio.querySelector('.impacto__icono use').getAttribute('href')).toMatch(/#fuego$/);
    const golpe = vivas.find((a) => a.elemento === mio.querySelector('.marco-heroe'));
    expect(golpe.cuadros[1].transform).toBe('translate3d(-14px, 0, 0)');
    terminarVivas(vivas);
    await trasElHitStop();
    terminarVivas(vivas);
    await corriendo;
  });

  test('una épica lleva su halo antes del golpe y lo deja escrito en el actor', async () => {
    const vivas = conWaapi();
    const campo = campoDePrueba();
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    const actor = campo.querySelector(`[data-puesto="${YO}"]`);
    const corriendo = coreo.accion(ataque({ esEpica: true }), IMPACTO);
    expect(actor.dataset.epica).toBe('si');
    expect(vivas[0].elemento).toBe(actor.querySelector('.marco-heroe__retrato'));
    expect(vivas[0].cuadros[0].transform).toBe('scale(0.92)');
    for (let i = 0; i < 4; i += 1) {
      terminarVivas(vivas);
      await tick();
    }
    await trasElHitStop();
    terminarVivas(vivas);
    await corriendo;
    expect(actor.dataset.epica).toBeUndefined();
  });
});

describe('sin lunge: curar, defender y los efectos por turno', () => {
  test('una curación brilla en quien cura y en quien recibe, con la cruz', async () => {
    const vivas = conWaapi();
    const campo = campoDePrueba();
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    const aviso = {
      idEjecutor: ALIADO,
      idObjetivo: YO,
      accion: { codigo: 'Toque de la Vida', nombre: 'Toque de la Vida', tipo: 'SANACION' },
      afectados: [{ idJugador: YO, vidaActual: 48, vidaMaxima: 52, diferencia: 8 }],
    };
    const corriendo = coreo.accion(aviso, [
      { idJugador: YO, cifra: '+8', unidad: 'VIDA', etiqueta: 'Curación', tono: 'curacion' },
    ]);
    const aliado = campo.querySelector(`[data-puesto="${ALIADO}"]`);
    const mio = campo.querySelector(`[data-puesto="${YO}"]`);
    expect(aliado.dataset.accion).toBe('cura');
    // El impacto es inmediato: no hay lunge que esperar.
    expect(mio.dataset.golpe).toBe('curacion');
    expect(mio.querySelector('.impacto').textContent).toBe('+8 VIDACuración');
    expect(mio.querySelector('.impacto__icono use').getAttribute('href')).toMatch(/#cruz$/);
    expect(vivas.some((a) => a.elemento === aliado.querySelector('.marco-heroe'))).toBe(true);
    for (const a of vivas) {
      a.terminar();
    }
    await corriendo;
    expect(aliado.dataset.accion).toBeUndefined();
  });

  test('un veneno por turno no mueve a quien lo causó: solo el afectado lo acusa, más suave', async () => {
    const vivas = conWaapi();
    const campo = campoDePrueba();
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    const aviso = {
      idEjecutor: RIVAL,
      idObjetivo: null,
      accion: { codigo: 'EFECTO_POR_TURNO', nombre: 'Veneno', tipo: 'EFECTO' },
      afectados: [{ idJugador: YO, vidaActual: 36, vidaMaxima: 52, diferencia: -4 }],
    };
    const corriendo = coreo.accion(aviso, [
      { idJugador: YO, cifra: '−4', unidad: 'VIDA', etiqueta: 'Veneno', tono: 'dano' },
    ]);
    const rival = campo.querySelector(`[data-puesto="${RIVAL}"]`);
    const mio = campo.querySelector(`[data-puesto="${YO}"]`);
    expect(rival.dataset.accion).toBeUndefined();
    expect(vivas.some((a) => a.elemento === rival.querySelector('.marco-heroe'))).toBe(false);
    expect(mio.dataset.golpe).toBe('golpe');
    const golpe = vivas.find((a) => a.elemento === mio.querySelector('.marco-heroe'));
    // 14 px × 0,6 = 8 px, hacia la izquierda (bando propio).
    expect(golpe.cuadros[1].transform).toBe('translate3d(-8px, 0, 0)');
    expect(mio.querySelector('.impacto__icono use').getAttribute('href')).toMatch(/#gota$/);
    for (const a of vivas) {
      a.terminar();
    }
    await corriendo;
  });

  test('un efecto NUEVO se ve llegar; uno que ya estaba, no', async () => {
    const vivas = conWaapi();
    const campo = campoDePrueba();
    const rival = campo.querySelector(`[data-puesto="${RIVAL}"]`);
    const chip = document.createElement('span');
    chip.dataset.efecto = 'VIEJO';
    rival.append(chip);
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    const corriendo = coreo.accion(
      ataque({
        efectos: [
          { codigo: 'VIEJO', nombre: 'Viejo', tipo: 'PENALIZA_DANO' },
          { codigo: 'CONO_DE_HIELO', nombre: 'Cono de hielo', tipo: 'PENALIZA_ATAQUE' },
        ],
      }),
      IMPACTO,
    );
    vivas[0].terminar();
    await tick();
    vivas[1].terminar();
    await tick();
    const sobreElRival = vivas.filter((a) => a.elemento === rival.querySelector('.marco-heroe'));
    expect(sobreElRival.some((a) => String(a.cuadros[1].filter).includes('saturate(0.4)'))).toBe(
      true,
    );
    terminarVivas(vivas);
    await trasElHitStop();
    terminarVivas(vivas);
    await corriendo;
  });
});

describe('sin movimiento: el estado y la cifra, al instante', () => {
  test('sin Web Animations API (jsdom) el impacto es inmediato y el estado se va solo', async () => {
    jest.useFakeTimers();
    const campo = campoDePrueba();
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    const actor = campo.querySelector(`[data-puesto="${YO}"]`);
    const rival = campo.querySelector(`[data-puesto="${RIVAL}"]`);
    const corriendo = coreo.accion(ataque(), IMPACTO);
    expect(rival.dataset.golpe).toBe('golpe');
    expect(rival.querySelector('.impacto').textContent).toBe('−7 VIDA');
    await corriendo;
    expect(actor.dataset.accion).toBeUndefined();
    jest.advanceTimersByTime(320);
    expect(rival.dataset.golpe).toBeUndefined();
    // La cifra se lee y luego se va.
    expect(rival.querySelector('.impacto')).not.toBeNull();
    jest.advanceTimersByTime(2400);
    expect(rival.querySelector('.impacto')).toBeNull();
  });

  test('con prefers-reduced-motion no se arranca ninguna animación aunque haya WAAPI', async () => {
    globalThis.matchMedia = () => ({ matches: true });
    const vivas = conWaapi();
    const campo = campoDePrueba();
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    const rival = campo.querySelector(`[data-puesto="${RIVAL}"]`);
    await coreo.accion(ataque(), IMPACTO);
    expect(vivas).toHaveLength(0);
    expect(rival.querySelector('.impacto').textContent).toBe('−7 VIDA');
  });

  test('con data-test-motion="off" tampoco, y la celebración no crea partículas', async () => {
    document.documentElement.setAttribute(ATRIBUTO_DE_PRUEBA, 'off');
    const vivas = conWaapi();
    const campo = campoDePrueba();
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    await coreo.accion(ataque(), IMPACTO);
    await coreo.desenlace('victoria', null);
    expect(vivas).toHaveLength(0);
    expect(campo.dataset.desenlace).toBe('victoria');
    expect(campo.querySelector('.celebracion')).toBeNull();
  });
});

describe('el turno y el desenlace', () => {
  test('un pulso en quien juega; la IA piensa hasta que actúa', async () => {
    const vivas = conWaapi();
    const campo = campoDePrueba();
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    const rival = campo.querySelector(`[data-puesto="${RIVAL}"]`);
    coreo.turno(RIVAL);
    expect(rival.dataset.pensando).toBe('si');
    expect(vivas[0].elemento).toBe(rival.querySelector('.marco-heroe__retrato'));
    expect(vivas[0].cuadros[1].transform).toBe('scale(1.08)');
    coreo.turno(YO);
    expect(rival.dataset.pensando).toBeUndefined();
    expect(campo.querySelector(`[data-puesto="${YO}"]`).dataset.pensando).toBeUndefined();
    coreo.turno(RIVAL);
    const corriendo = coreo.accion(ataque({ ejecutor: RIVAL, objetivo: YO }), []);
    expect(rival.dataset.pensando).toBeUndefined();
    for (let i = 0; i < 4; i += 1) {
      terminarVivas(vivas);
      await tick();
    }
    await trasElHitStop();
    terminarVivas(vivas);
    await corriendo;
    coreo.turno(null);
    expect(campo.querySelectorAll('[data-pensando]')).toHaveLength(0);
  });

  test('VICTORIA: la palabra encaja y llueven 60 partículas que luego se van; DERROTA y EMPATE, no', async () => {
    const vivas = conWaapi();
    const campo = campoDePrueba();
    const coreo = coreografiaDeCombate({ campo, participantes: PARTICIPANTES, yo: YO });
    const panel = document.createElement('div');
    panel.className = 'panel-resultado';
    const palabra = document.createElement('p');
    palabra.className = 'panel-resultado__palabra';
    palabra.textContent = 'VICTORIA';
    panel.append(palabra);
    document.body.append(panel);

    const corriendo = coreo.desenlace('victoria', panel);
    expect(campo.dataset.desenlace).toBe('victoria');
    expect(vivas[0].elemento).toBe(palabra);
    const capa = campo.querySelector('.celebracion');
    expect(capa).not.toBeNull();
    expect(capa.getAttribute('aria-hidden')).toBe('true');
    expect(capa.querySelectorAll('.celebracion__particula')).toHaveLength(PARTICULAS);
    for (const a of vivas) {
      a.terminar();
    }
    await corriendo;
    expect(campo.querySelector('.celebracion')).toBeNull();

    await coreo.desenlace('derrota', panel);
    expect(campo.dataset.desenlace).toBe('derrota');
    expect(campo.querySelector('.celebracion')).toBeNull();
    await coreo.desenlace('empate', panel);
    expect(campo.dataset.desenlace).toBe('empate');
  });

  test('sin campo (móvil, o una prueba sin esa zona) nada rompe', async () => {
    conWaapi();
    const coreo = coreografiaDeCombate({ campo: null, participantes: PARTICIPANTES, yo: YO });
    await expect(coreo.accion(ataque(), IMPACTO)).resolves.toBeUndefined();
    expect(() => coreo.turno(RIVAL)).not.toThrow();
    await expect(coreo.desenlace('victoria', null)).resolves.toBeUndefined();
  });
});
