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
} from './narracion.js';

const YO = 'u-yo';
const RIVAL = 'u-rival';
const participantes = [
  { jugador: { id: YO }, heroe: { nombre: 'Aquiles' } },
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
    expect(nombreDe(RIVAL, participantes, YO)).toBe('Centinela');
    expect(nombreDe('otro', participantes, YO)).toBe('un rival');
  });

  test('el ataque basico se dice en español', () => {
    expect(nombreDeAccion('ATAQUE_BASICO')).toBe('Ataque básico');
    expect(nombreDeAccion('')).toBe('una acción');
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
    expect(lineas[0].texto).toBe('¡Crítico! Aquiles (tú) golpea a Centinela: −9 de vida (14/60).');
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
      'Aquiles (tú) evade el golpe de Centinela y recibe el 80 % del daño: −3 de vida (35/52).',
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
    expect(lineas[0].texto).toBe('El golpe de Aquiles (tú) no hace efecto en Centinela (60/60).');
  });

  test('quien llega a cero cae', () => {
    const { lineas } = narrarAccion(
      aviso('CAUSAR_DANO', [{ idJugador: RIVAL, vidaActual: 0, vidaMaxima: 60, diferencia: -5 }]),
      participantes,
      YO,
    );
    expect(lineas.at(-1)).toEqual({ texto: 'Centinela cae.', tono: 'caida', icono: 'alerta' });
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
      'Aquiles (tú) usa Golpe con escudo sobre Centinela: −10 de vida (50/60).',
    );
  });

  test('sin afectados no se inventa un golpe', () => {
    const { lineas, impactos } = narrarAccion(aviso('SIN_EFECTO', []), participantes, YO);
    expect(lineas).toHaveLength(1);
    expect(impactos).toHaveLength(0);
  });
});

describe('narrarTurno', () => {
  test('dice el numero y quien juega', () => {
    expect(narrarTurno({ idJugador: YO, numeroTurno: 8 }, participantes, YO).texto).toBe(
      'Turno 8: te toca a ti.',
    );
    expect(narrarTurno({ idJugador: RIVAL }, participantes, YO).texto).toBe('Juega Centinela.');
  });
});
