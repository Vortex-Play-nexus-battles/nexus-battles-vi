/**
 * UXC-2 — la narracion del combate a partir de `partida.accion.resuelta`.
 */
import {
  CATEGORIAS,
  categoriaDe,
  narrarAccion,
  narrarTurno,
  nombreDe,
  nombreDeAccion,
  nombreDeLaAccion,
} from './narracion.js';

const YO = 'u-yo';
const RIVAL = 'u-rival';
const participantes = [
  { jugador: { id: YO }, heroe: { nombre: 'Aquiles' } },
  // Desde la auditoría del 4-oct el rival de la máquina se nombra con «(IA)».
  { jugador: { id: RIVAL }, heroe: { nombre: 'Centinela' }, esIA: true },
];

function aviso(nombre, afectados, idEjecutor = YO) {
  return {
    tipo: 'partida.accion.resuelta',
    idPartida: 'p',
    idEjecutor,
    accion: { codigo: 'ATAQUE_BASICO', nombre },
    afectados,
  };
}

describe('categoriaDe', () => {
  test('reconoce las seis categorias del motor en el nombre de la accion', () => {
    for (const clave of Object.keys(CATEGORIAS)) {
      expect(categoriaDe({ nombre: clave })?.clave).toBe(clave);
    }
  });

  test('un nombre de accion cualquiera no es una categoria', () => {
    expect(categoriaDe({ codigo: 'ATAQUE_BASICO', nombre: 'Golpe con escudo' })).toBeNull();
  });
});

describe('nombres', () => {
  test('quien mira es «tú»', () => {
    expect(nombreDe(YO, participantes, YO)).toBe('Aquiles (tú)');
    expect(nombreDe(RIVAL, participantes, YO)).toBe('Centinela (IA)');
    expect(nombreDe('otro', participantes, YO)).toBe('un rival');
  });

  test('el ataque basico se dice en español', () => {
    expect(nombreDeAccion('ATAQUE_BASICO')).toBe('Ataque básico');
    expect(nombreDeAccion('')).toBe('una acción');
  });

  test('un código del motor sin nombre conocido se dice legible; un nombre, tal cual', () => {
    expect(nombreDeAccion('MANO_DE_PIEDRA')).toBe('Mano de piedra');
    expect(nombreDeAccion('Golpe con escudo')).toBe('Golpe con escudo');
  });

  test('el nombre de una acción sale del estado de combate de quien la juega', () => {
    const conAcciones = [
      {
        jugador: { id: YO },
        heroe: {
          nombre: 'Aquiles',
          acciones: [{ codigo: 'SANACION_BASICA', nombre: 'Sanación básica' }],
        },
      },
    ];
    expect(nombreDeLaAccion('SANACION_BASICA', YO, conAcciones)).toBe('Sanación básica');
    expect(nombreDeLaAccion('BOLA_DE_HIELO', YO, conAcciones)).toBe('Bola de hielo');
  });
});

describe('narrarAccion', () => {
  test('un critico lo dice y deja la cifra para el campo', () => {
    const { lineas, impactos } = narrarAccion(
      aviso('CAUSAR_DANO_CRITICO', [
        { idJugador: RIVAL, vidaActual: 14, vidaMaxima: 60, diferencia: -9 },
      ]),
      participantes,
      YO,
    );
    expect(lineas[0].texto).toBe(
      '¡Crítico! Aquiles (tú) golpea a Centinela (IA): −9 de vida (14/60).',
    );
    expect(lineas[0].tono).toBe('critico');
    expect(impactos[0]).toEqual({
      idJugador: RIVAL,
      cifra: '−9',
      etiqueta: 'Crítico',
      tono: 'critico',
    });
  });

  test('una evasion dice el porcentaje del documento, sin calcular nada', () => {
    const { lineas } = narrarAccion(
      aviso(
        'EVADIR_EL_GOLPE',
        [{ idJugador: YO, vidaActual: 35, vidaMaxima: 52, diferencia: -3 }],
        RIVAL,
      ),
      participantes,
      YO,
    );
    expect(lineas[0].texto).toBe(
      'Aquiles (tú) evade el golpe de Centinela (IA) y recibe el 80 % del daño: −3 de vida (35/52).',
    );
    expect(lineas[0].tono).toBe('mitigado');
  });

  test('una curacion es positiva y se dice como tal', () => {
    const { lineas, impactos } = narrarAccion(
      aviso('CAUSAR_DANO', [{ idJugador: YO, vidaActual: 41, vidaMaxima: 52, diferencia: 6 }]),
      participantes,
      YO,
    );
    expect(lineas[0].texto).toBe('Aquiles (tú) se cura: +6 de vida (41/52).');
    expect(impactos[0].cifra).toBe('+6');
    expect(impactos[0].tono).toBe('curacion');
  });

  test('sin efecto', () => {
    const { lineas } = narrarAccion(
      aviso('SIN_EFECTO', [{ idJugador: RIVAL, vidaActual: 60, vidaMaxima: 60, diferencia: 0 }]),
      participantes,
      YO,
    );
    expect(lineas[0].texto).toBe(
      'El golpe de Aquiles (tú) no hace efecto en Centinela (IA) (60/60).',
    );
  });

  test('quien llega a cero cae', () => {
    const { lineas } = narrarAccion(
      aviso('CAUSAR_DANO', [{ idJugador: RIVAL, vidaActual: 0, vidaMaxima: 60, diferencia: -5 }]),
      participantes,
      YO,
    );
    expect(lineas.at(-1)).toEqual({ texto: 'Centinela (IA) cae.', tono: 'caida', icono: 'alerta' });
  });

  test('una accion con nombre propio (no categoria) se nombra tal cual', () => {
    const { lineas } = narrarAccion(
      aviso('Golpe con escudo', [
        { idJugador: RIVAL, vidaActual: 50, vidaMaxima: 60, diferencia: -10 },
      ]),
      participantes,
      YO,
    );
    expect(lineas[0].texto).toBe(
      'Aquiles (tú) usa Golpe con escudo sobre Centinela (IA): −10 de vida (50/60).',
    );
  });

  test('sin afectados no se inventa un golpe', () => {
    const { lineas, impactos } = narrarAccion(aviso('SIN_EFECTO', []), participantes, YO);
    expect(lineas).toHaveLength(1);
    expect(impactos).toHaveLength(0);
  });

  /* B7 · canal 1.5.0: el ejecutor viaja en `afectados` por su poder y sus cargas. */

  test('el ejecutor que solo viaja por su poder no se narra como si recibiera el golpe', () => {
    const { lineas, impactos } = narrarAccion(
      aviso('CAUSAR_DANO', [
        { idJugador: RIVAL, vidaActual: 50, vidaMaxima: 60, diferencia: -10 },
        { idJugador: YO, vidaActual: 52, vidaMaxima: 52, diferencia: 0, poderActual: 4 },
      ]),
      participantes,
      YO,
    );
    expect(lineas).toEqual([
      {
        texto: 'Aquiles (tú) golpea a Centinela (IA): −10 de vida (50/60).',
        tono: 'dano',
        icono: 'espada',
      },
    ]);
    expect(impactos.map((i) => i.idJugador)).toEqual([RIVAL]);
  });

  test('una accion que no mueve ninguna vida (una defensa) dice que se usó, sin «no hace efecto»', () => {
    const { lineas, impactos } = narrarAccion(
      {
        ...aviso('Mano de piedra', [
          { idJugador: YO, vidaActual: 52, vidaMaxima: 52, diferencia: 0 },
        ]),
        accion: { codigo: 'Mano de piedra', nombre: 'Mano de piedra', tipo: 'DEFENSA' },
      },
      participantes,
      YO,
    );
    expect(lineas).toEqual([
      { texto: 'Aquiles (tú) usa Mano de piedra.', tono: 'sistema', icono: 'rayo' },
    ]);
    expect(impactos).toHaveLength(0);
  });

  test('un ataque especial dice que accion fue antes de contar el golpe', () => {
    const { lineas } = narrarAccion(
      {
        ...aviso('CAUSAR_DANO_CRITICO', [
          { idJugador: RIVAL, vidaActual: 40, vidaMaxima: 60, diferencia: -20 },
        ]),
        accion: { codigo: 'Golpe con escudo', nombre: 'CAUSAR_DANO_CRITICO', tipo: 'ATAQUE' },
      },
      participantes,
      YO,
    );
    expect(lineas.map((l) => l.texto)).toEqual([
      'Aquiles (tú) usa Golpe con escudo.',
      '¡Crítico! Aquiles (tú) golpea a Centinela (IA): −20 de vida (40/60).',
    ]);
  });

  test('UXC-9 — sin poder para la acción pedida, se dice que atacó con su valor base', () => {
    const golpe = aviso('DANO', [
      { idJugador: RIVAL, vidaActual: 50, vidaMaxima: 60, diferencia: -4 },
    ]);
    golpe.accion = { ...golpe.accion, enValorBase: true, accionPedida: 'Golpe con escudo' };
    const { lineas } = narrarAccion(golpe, participantes, YO);
    expect(lineas[0].texto).toBe(
      'A Aquiles (tú) no le alcanza el poder para Golpe con escudo: ataca con su valor base.',
    );
  });

  test('quien se cura a si mismo sigue narrandose: su vida si cambió', () => {
    const { lineas } = narrarAccion(
      {
        ...aviso('SANACION_BASICA', [
          { idJugador: YO, vidaActual: 45, vidaMaxima: 52, diferencia: 5 },
        ]),
        accion: { codigo: 'SANACION_BASICA', nombre: 'SANACION_BASICA', tipo: 'SANACION' },
      },
      participantes,
      YO,
    );
    expect(lineas.at(-1).texto).toBe('Aquiles (tú) se cura: +5 de vida (45/52).');
  });

  /* Auditoría de DEV del 30-sep: «los especiales no se narran en el registro». */

  test('una sanación dice qué acción fue, con el nombre que da el servidor', () => {
    const conAcciones = [
      {
        jugador: { id: YO },
        heroe: {
          nombre: 'Aquiles',
          acciones: [{ codigo: 'SANACION_BASICA', nombre: 'Sanación básica' }],
        },
      },
      participantes[1],
    ];
    const { lineas } = narrarAccion(
      {
        ...aviso('SANACION_BASICA', [
          { idJugador: YO, vidaActual: 45, vidaMaxima: 52, diferencia: 5 },
        ]),
        accion: { codigo: 'SANACION_BASICA', nombre: 'SANACION_BASICA', tipo: 'SANACION' },
      },
      conAcciones,
      YO,
    );
    expect(lineas.map((l) => l.texto)).toEqual([
      'Aquiles (tú) usa Sanación básica.',
      'Aquiles (tú) se cura: +5 de vida (45/52).',
    ]);
  });

  test('una sanación de grupo se nombra una sola vez', () => {
    const { lineas } = narrarAccion(
      {
        ...aviso('CURA_EN_GRUPO', [
          { idJugador: YO, vidaActual: 45, vidaMaxima: 52, diferencia: 3 },
          { idJugador: RIVAL, vidaActual: 50, vidaMaxima: 60, diferencia: 3 },
        ]),
        accion: { codigo: 'CURA_EN_GRUPO', nombre: 'CURA_EN_GRUPO', tipo: 'SANACION_GRUPAL' },
      },
      participantes,
      YO,
    );
    expect(lineas.filter((l) => l.texto.includes('usa Cura en grupo'))).toHaveLength(1);
    expect(lineas).toHaveLength(3);
  });

  test('un ataque especial con código del motor se nombra con el nombre de la acción', () => {
    const conAcciones = [
      {
        jugador: { id: YO },
        heroe: {
          nombre: 'Aquiles',
          acciones: [{ codigo: 'GOLPE_CON_ESCUDO', nombre: 'Golpe con escudo' }],
        },
      },
      participantes[1],
    ];
    const { lineas } = narrarAccion(
      {
        ...aviso('CAUSAR_DANO', [
          { idJugador: RIVAL, vidaActual: 50, vidaMaxima: 60, diferencia: -10 },
        ]),
        accion: { codigo: 'GOLPE_CON_ESCUDO', nombre: 'CAUSAR_DANO', tipo: 'ATAQUE' },
      },
      conAcciones,
      YO,
    );
    expect(lineas[0].texto).toBe('Aquiles (tú) usa Golpe con escudo.');
  });
});

describe('narrarTurno', () => {
  test('dice el numero y quien juega', () => {
    expect(narrarTurno({ idJugador: YO, numeroTurno: 8 }, participantes, YO).texto).toBe(
      'Turno 8: te toca a ti.',
    );
    expect(narrarTurno({ idJugador: RIVAL }, participantes, YO).texto).toBe(
      'Juega Centinela (IA).',
    );
  });
});

/**
 * Auditoría del 4-oct: «cuando yo ataco, también me hago daño a mí mismo».
 * El servidor nunca dirige un ataque contra quien lo lanza; lo que se leía como
 * auto-daño era el rival de la máquina con el mismo nombre que tu héroe, un daño
 * que te devolvió el objetivo (Pinchos de escudo) o tu propia Empuñadura de Furia.
 */
describe('sin auto-daño al atacar (auditoría del 4-oct)', () => {
  const MAQUINA = 'u-maquina';
  // El rival de la máquina se llama como su prototipo, que puede ser el de tu héroe.
  const espejo = [
    { jugador: { id: YO }, heroe: { nombre: 'Mago Fuego' } },
    { jugador: { id: MAQUINA }, heroe: { nombre: 'Mago Fuego' }, esIA: true },
  ];

  test('el rival de la máquina se marca con «(IA)», aunque se llame como tu héroe', () => {
    expect(nombreDe(MAQUINA, espejo, YO)).toBe('Mago Fuego (IA)');
    expect(nombreDe(YO, espejo, YO)).toBe('Mago Fuego (tú)');

    const { lineas } = narrarAccion(
      aviso(
        'CAUSAR_DANO',
        [{ idJugador: YO, vidaActual: 35, vidaMaxima: 40, diferencia: -5 }],
        MAQUINA,
      ),
      espejo,
      YO,
    );
    expect(lineas[0].texto).toBe('Mago Fuego (IA) golpea a Mago Fuego (tú): −5 de vida (35/40).');
  });

  test('si el objetivo te devuelve daño, se dice quién y con qué, nunca «golpea a ti mismo»', () => {
    const { lineas, impactos } = narrarAccion(
      {
        ...aviso('CAUSAR_DANO', [
          {
            idJugador: MAQUINA,
            vidaActual: 34,
            vidaMaxima: 40,
            diferencia: -6,
            causas: [{ tipo: 'DANO', origen: YO, efecto: 'ATAQUE_BASICO', cantidad: 6 }],
          },
          {
            idJugador: YO,
            vidaActual: 39,
            vidaMaxima: 40,
            diferencia: -1,
            causas: [
              { tipo: 'REFLEJO', origen: MAQUINA, efecto: 'Pinchos de escudo', cantidad: 1 },
            ],
          },
        ]),
        idObjetivo: MAQUINA,
      },
      espejo,
      YO,
    );
    const textos = lineas.map((l) => l.texto);
    expect(textos).toEqual([
      'Mago Fuego (tú) golpea a Mago Fuego (IA): −6 de vida (34/40).',
      'Pinchos de escudo de Mago Fuego (IA) te devuelve 1 de daño (39/40).',
    ]);
    expect(textos.join(' ')).not.toMatch(/golpea a Mago Fuego \(tú\)/);
    expect(impactos.find((i) => i.idJugador === YO)).toEqual({
      idJugador: YO,
      cifra: '−1',
      etiqueta: 'Devuelto',
      tono: 'dano',
    });
  });

  test('un golpe sin efecto que aun así te quita vida no dice «no hace efecto en ti»', () => {
    const { lineas } = narrarAccion(
      aviso('SIN_EFECTO', [
        { idJugador: MAQUINA, vidaActual: 40, vidaMaxima: 40, diferencia: 0 },
        { idJugador: YO, vidaActual: 39, vidaMaxima: 40, diferencia: -1 },
      ]),
      espejo,
      YO,
    );
    const textos = lineas.map((l) => l.texto);
    expect(textos).toContain(
      'El golpe de Mago Fuego (tú) no hace efecto en Mago Fuego (IA) (40/40).',
    );
    expect(textos).toContain('Recibes 1 de daño de vuelta (39/40).');
    expect(textos.join(' ')).not.toMatch(/no hace efecto en Mago Fuego \(tú\)/);
  });

  test('la Empuñadura de Furia propia se dice como lo que es: tu arma te quita vida', () => {
    const { lineas } = narrarAccion(
      {
        tipo: 'partida.accion.resuelta',
        idPartida: 'p',
        idEjecutor: YO,
        accion: { codigo: 'EFECTO_POR_TURNO', nombre: 'Empuñadura de Furia', tipo: 'EFECTO' },
        afectados: [{ idJugador: YO, vidaActual: 43, vidaMaxima: 44, diferencia: -1 }],
      },
      espejo,
      YO,
    );
    expect(lineas[0].texto).toBe('Pierdes 1 de vida por tu Empuñadura de Furia (43/44).');
  });

  test('un sangrado que te causó otro dice quién lo causó', () => {
    const { lineas } = narrarAccion(
      {
        tipo: 'partida.accion.resuelta',
        idPartida: 'p',
        idEjecutor: MAQUINA,
        accion: { codigo: 'EFECTO_POR_TURNO', nombre: 'Cierra sangrienta', tipo: 'EFECTO' },
        afectados: [{ idJugador: YO, vidaActual: 38, vidaMaxima: 40, diferencia: -2 }],
      },
      espejo,
      YO,
    );
    expect(lineas[0].texto).toBe(
      'Cierra sangrienta de Mago Fuego (IA): Mago Fuego (tú) pierde 2 de vida (38/40).',
    );
  });
});
