/**
 * Diccionario FX — Battle Motion Language (HU-UX-003, MOTION-2).
 *
 * Lo que se prueba es la TRADUCCIÓN: cada acción de los ocho héroes
 * (Tabla 7 y Tabla 20 del motor) cae en su clave, cada categoría del motor
 * en su resultado, y ningún dato se inventa (sin tipo ni prototipo, lo
 * prudente: golpe físico).
 */

import { FX, fxDeEfecto, iconoDe, semanticaDe } from './fx.js';

const YO = 'u-yo';
const RIVAL = 'u-rival';
const ALIADO = 'u-aliado';

function participantes(prototipo, { equipo = null } = {}) {
  return [
    { jugador: { id: YO }, heroe: { nombre: 'Mío', prototipo }, equipo },
    { jugador: { id: ALIADO }, heroe: { nombre: 'Aliado', prototipo: 'Médico' }, equipo },
    {
      jugador: { id: RIVAL },
      heroe: { nombre: 'Rival', prototipo: 'Guerrero Armas' },
      esIA: true,
      equipo: equipo === null ? null : 'B',
    },
  ];
}

function ataque(
  prototipo,
  {
    categoria = 'CAUSAR_DANO',
    diferencia = -7,
    esEpica = false,
    codigo = 'ATAQUE_BASICO',
    efectos = [],
  } = {},
) {
  return {
    aviso: {
      tipo: 'partida.accion.resuelta',
      idEjecutor: YO,
      idObjetivo: RIVAL,
      accion: { codigo, nombre: categoria, tipo: 'ATAQUE', esEpica },
      afectados: [
        { idJugador: YO, vidaActual: 40, vidaMaxima: 52, diferencia: 0, poderActual: 6 },
        {
          idJugador: RIVAL,
          vidaActual: 53 + diferencia,
          vidaMaxima: 60,
          diferencia,
          efectosActivos: efectos,
        },
      ],
    },
    participantes: participantes(prototipo),
  };
}

describe('el FX de un ataque sale de la familia del héroe', () => {
  test.each([
    ['Guerrero Tanque', FX.PHYSICAL_HIT],
    ['Guerrero Armas', FX.PHYSICAL_HIT],
    ['Pícaro Machete', FX.PHYSICAL_HIT],
    ['Mago Fuego', FX.MAGIC_FIRE],
    ['Mago Hielo', FX.MAGIC_ICE],
    ['Pícaro Veneno', FX.POISON],
  ])('%s → %s', (prototipo, fx) => {
    const { aviso, participantes: lista } = ataque(prototipo);
    const s = semanticaDe(aviso, lista);
    expect(s.fx).toBe(fx);
    expect(s.ataca).toBe(true);
    expect(s.ejecutor).toBe(YO);
    expect(s.objetivo).toBe(RIVAL);
    expect(s.afectados).toEqual([
      { id: RIVAL, diferencia: -7, resultado: 'golpe', caido: false, efectos: [] },
    ]);
  });

  test('sin prototipo conocido (o sin participantes) se pinta como golpe físico: no se inventa', () => {
    const { aviso } = ataque('Guerrero Tanque');
    expect(semanticaDe(aviso, []).fx).toBe(FX.PHYSICAL_HIT);
    expect(semanticaDe(aviso, participantes('Dragón Rosa')).fx).toBe(FX.PHYSICAL_HIT);
  });

  test('un sanador que atacara (el motor lo prohíbe) se vería como golpe físico, no como curación', () => {
    const { aviso, participantes: lista } = ataque('Chamán');
    expect(semanticaDe(aviso, lista).fx).toBe(FX.PHYSICAL_HIT);
  });

  test('el ejecutor viaja en su aviso por el poder y no cuenta como afectado', () => {
    const { aviso, participantes: lista } = ataque('Mago Fuego');
    expect(semanticaDe(aviso, lista).afectados.map((a) => a.id)).toEqual([RIVAL]);
  });
});

describe('la categoría del motor es el resultado sobre el afectado', () => {
  test.each([
    ['CAUSAR_DANO', -7, 'golpe'],
    ['CAUSAR_DANO_CRITICO', -12, 'critico'],
    ['EVADIR_EL_GOLPE', -2, 'esquiva'],
    ['RESISTIR_EL_GOLPE', -4, 'resistencia'],
    ['ESCAPAR_AL_GOLPE', -1, 'escape'],
    ['SIN_EFECTO', 0, 'fallo'],
  ])('%s → %s', (categoria, diferencia, resultado) => {
    const { aviso, participantes: lista } = ataque('Guerrero Armas', { categoria, diferencia });
    expect(semanticaDe(aviso, lista).afectados[0].resultado).toBe(resultado);
  });

  test('quien queda a cero cae', () => {
    const { aviso, participantes: lista } = ataque('Guerrero Armas', { diferencia: -53 });
    const [afectado] = semanticaDe(aviso, lista).afectados;
    expect(afectado.caido).toBe(true);
    expect(afectado.resultado).toBe('golpe');
  });

  test('una épica se marca además de su FX base', () => {
    const { aviso, participantes: lista } = ataque('Pícaro Machete', {
      codigo: 'Intimidación sangrienta',
      esEpica: true,
    });
    const s = semanticaDe(aviso, lista);
    expect(s.epica).toBe(true);
    expect(s.fx).toBe(FX.PHYSICAL_HIT);
  });
});

describe('curar, defender, apoyar y los efectos por turno', () => {
  test('una sanación es HEAL sobre el aliado y no «ataca»', () => {
    const aviso = {
      idEjecutor: ALIADO,
      idObjetivo: YO,
      accion: { codigo: 'Curación Directa', nombre: 'Curación Directa', tipo: 'SANACION' },
      afectados: [{ idJugador: YO, vidaActual: 48, vidaMaxima: 52, diferencia: 8 }],
    };
    const s = semanticaDe(aviso, participantes('Guerrero Tanque'));
    expect(s.fx).toBe(FX.HEAL);
    expect(s.ataca).toBe(false);
    expect(s.afectados[0].resultado).toBe('curacion');
  });

  test('una sanación de grupo (Canto del Bosque) cura a todos los afectados', () => {
    const aviso = {
      idEjecutor: ALIADO,
      idObjetivo: null,
      accion: { codigo: 'Canto del Bosque', nombre: 'Canto del Bosque', tipo: 'SANACION_GRUPAL' },
      afectados: [
        { idJugador: YO, vidaActual: 50, vidaMaxima: 52, diferencia: 5 },
        { idJugador: ALIADO, vidaActual: 30, vidaMaxima: 40, diferencia: 5 },
      ],
    };
    const s = semanticaDe(aviso, participantes('Guerrero Tanque', { equipo: 'A' }));
    expect(s.fx).toBe(FX.HEAL);
    expect(s.afectados.map((a) => a.resultado)).toEqual(['curacion', 'curacion']);
  });

  test('una defensa (Mano de piedra) es SHIELD sobre uno mismo', () => {
    const aviso = {
      idEjecutor: YO,
      idObjetivo: YO,
      accion: { codigo: 'Mano de piedra', nombre: 'Mano de piedra', tipo: 'DEFENSA' },
      afectados: [
        {
          idJugador: YO,
          vidaActual: 40,
          vidaMaxima: 52,
          diferencia: 0,
          efectosActivos: [
            { codigo: 'MANO_DE_PIEDRA', nombre: 'Mano de piedra', tipo: 'BONO_DEFENSA' },
          ],
        },
      ],
    };
    const s = semanticaDe(aviso, participantes('Guerrero Tanque'));
    expect(s.fx).toBe(FX.SHIELD);
    expect(s.ataca).toBe(false);
    // El ejecutor sin cambio de vida no se narra como afectado.
    expect(s.afectados).toEqual([]);
  });

  test('un apoyo sobre el rival es DEBUFF; sobre un compañero, BUFF', () => {
    const base = {
      idEjecutor: YO,
      accion: {
        codigo: 'Frío concentrado',
        nombre: 'Frío concentrado',
        tipo: 'APOYO',
        esEpica: true,
      },
      afectados: [],
    };
    expect(semanticaDe({ ...base, idObjetivo: RIVAL }, participantes('Mago Hielo')).fx).toBe(
      FX.DEBUFF,
    );
    expect(
      semanticaDe({ ...base, idObjetivo: ALIADO }, participantes('Mago Hielo', { equipo: 'A' })).fx,
    ).toBe(FX.BUFF);
  });

  test('un efecto por turno que quita vida es POISON y no mueve al ejecutor; uno que la da, HEAL', () => {
    const veneno = {
      idEjecutor: RIVAL,
      idObjetivo: null,
      accion: { codigo: 'EFECTO_POR_TURNO', nombre: 'Veneno', tipo: 'EFECTO' },
      afectados: [{ idJugador: YO, vidaActual: 36, vidaMaxima: 52, diferencia: -4 }],
    };
    const s = semanticaDe(veneno, participantes('Guerrero Tanque'));
    expect(s.porTurno).toBe(true);
    expect(s.ataca).toBe(false);
    expect(s.fx).toBe(FX.POISON);
    expect(s.afectados[0].resultado).toBe('golpe');
    const canto = {
      ...veneno,
      afectados: [{ idJugador: YO, vidaActual: 44, vidaMaxima: 52, diferencia: 4 }],
    };
    expect(semanticaDe(canto, participantes('Guerrero Tanque')).fx).toBe(FX.HEAL);
  });

  test('los efectos aplicados llevan su FX: escudo, mejora o perjuicio', () => {
    const { aviso, participantes: lista } = ataque('Mago Hielo', {
      codigo: 'Cono de hielo',
      efectos: [
        { codigo: 'CONO_DE_HIELO', nombre: 'Cono de hielo', tipo: 'PENALIZA_ATAQUE' },
        { codigo: 'VIEJO', nombre: 'Viejo', tipo: null },
      ],
    });
    const [afectado] = semanticaDe(aviso, lista).afectados;
    expect(afectado.efectos).toEqual([
      { codigo: 'CONO_DE_HIELO', fx: FX.DEBUFF },
      { codigo: 'VIEJO', fx: null },
    ]);
    expect(fxDeEfecto({ tipo: 'BONO_DEFENSA' })).toBe(FX.SHIELD);
    expect(fxDeEfecto({ tipo: 'INMUNE_TOTAL' })).toBe(FX.SHIELD);
    expect(fxDeEfecto({ tipo: 'BONO_ATAQUE' })).toBe(FX.BUFF);
    expect(fxDeEfecto({ tipo: 'SANACION_POR_TURNO' })).toBe(FX.BUFF);
    expect(fxDeEfecto({ tipo: 'DANO_POR_TURNO' })).toBe(FX.DEBUFF);
    expect(fxDeEfecto({ tipo: 'bono_dano' })).toBe(FX.BUFF);
    expect(fxDeEfecto(null)).toBeNull();
  });

  test('sin `tipo` (partida anterior a 1.7.0) una vida que sube fue sanación', () => {
    const aviso = {
      idEjecutor: ALIADO,
      accion: { codigo: 'Toque de la Vida', nombre: 'Toque de la Vida' },
      afectados: [{ idJugador: YO, vidaActual: 48, vidaMaxima: 52, diferencia: 8 }],
    };
    expect(semanticaDe(aviso, participantes('Guerrero Tanque')).fx).toBe(FX.HEAL);
  });

  test('un aviso vacío o roto no rompe: golpe físico sin afectados', () => {
    expect(semanticaDe(null, [])).toEqual(
      expect.objectContaining({
        fx: FX.PHYSICAL_HIT,
        afectados: [],
        ejecutor: null,
        objetivo: null,
      }),
    );
    expect(semanticaDe({ accion: null, afectados: 'no' }, []).afectados).toEqual([]);
  });
});

describe('el icono de la cifra nunca deja el color solo', () => {
  test.each([
    [FX.PHYSICAL_HIT, 'golpe', 'espada'],
    [FX.MAGIC_FIRE, 'golpe', 'fuego'],
    [FX.MAGIC_ICE, 'golpe', 'copo'],
    [FX.POISON, 'golpe', 'gota'],
    [FX.HEAL, 'curacion', 'cruz'],
    [FX.PHYSICAL_HIT, 'critico', 'rayo'],
    [FX.PHYSICAL_HIT, 'esquiva', 'escudo'],
    [FX.PHYSICAL_HIT, 'resistencia', 'escudo'],
    [FX.PHYSICAL_HIT, 'escape', 'escudo'],
    [FX.PHYSICAL_HIT, 'fallo', 'cerrar'],
    [FX.SHIELD, 'sinCambio', 'escudo'],
  ])('%s · %s → %s', (fx, resultado, esperado) => {
    expect(iconoDe(fx, resultado)).toBe(esperado);
  });
});
