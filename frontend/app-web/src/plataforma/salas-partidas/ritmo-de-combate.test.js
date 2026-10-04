/**
 * Auditoría del 4-oct — el ritmo del combate: tu golpe primero, el de la
 * máquina después, para que su contragolpe no parezca parte del tuyo.
 */
import { jest } from '@jest/globals';
import { PAUSA_ENTRE_ACCIONES_MS, suscripcionConRitmo } from './ritmo-de-combate.js';

const YO = 'u-yo';
const MAQUINA = 'u-maquina';
const accion = (idEjecutor, codigo = 'ATAQUE_BASICO') => ({
  tipo: 'partida.accion.resuelta',
  idPartida: 'p',
  idEjecutor,
  accion: { codigo },
  afectados: [],
});
const turno = (idJugador, numeroTurno) => ({
  tipo: 'partida.turno.cambiado',
  idPartida: 'p',
  idJugador,
  numeroTurno,
});

/** Un canal falso y un reloj que se mueve a mano. */
function banco({ pausaMs = 700 } = {}) {
  let entregar = null;
  let reloj = 0;
  const pendientes = [];
  const canal = (alRecibir) => {
    entregar = alRecibir;
  };
  const suscribir = suscripcionConRitmo(canal, {
    yo: YO,
    pausaMs,
    ahora: () => reloj,
    programar: (fn, ms) => pendientes.push({ fn, cuando: reloj + ms }),
  });
  return {
    suscribir,
    llega: (aviso) => entregar(aviso),
    pasa(ms) {
      reloj += ms;
      for (const p of pendientes.splice(0).sort((a, b) => a.cuando - b.cuando)) {
        if (p.cuando <= reloj) {
          p.fn();
        } else {
          pendientes.push(p);
        }
      }
    },
  };
}

describe('suscripcionConRitmo', () => {
  test('la pausa por defecto es la de producción', () => {
    expect(PAUSA_ENTRE_ACCIONES_MS).toBe(700);
  });

  test('tu golpe y el cambio de turno llegan al instante; el contragolpe de la máquina espera la pausa', () => {
    const b = banco();
    const vistos = [];
    b.suscribir((aviso) => vistos.push(`${aviso.tipo}:${aviso.idEjecutor ?? aviso.idJugador}`));

    b.llega(accion(YO));
    b.llega(turno(MAQUINA, 2));
    b.llega(accion(MAQUINA));
    b.llega(turno(YO, 3));

    expect(vistos).toEqual(['partida.accion.resuelta:u-yo', 'partida.turno.cambiado:u-maquina']);

    b.pasa(699);
    expect(vistos).toHaveLength(2);

    b.pasa(1);
    expect(vistos).toEqual([
      'partida.accion.resuelta:u-yo',
      'partida.turno.cambiado:u-maquina',
      'partida.accion.resuelta:u-maquina',
      'partida.turno.cambiado:u-yo',
    ]);
  });

  test('todos los que escuchan (barras y registro) reciben cada aviso a la vez y en orden', () => {
    const b = banco();
    const barras = [];
    const registro = [];
    b.suscribir((a) => barras.push(a.idEjecutor));
    b.suscribir((a) => registro.push(a.idEjecutor));

    b.llega(accion(YO));
    b.llega(accion(MAQUINA));
    expect(barras).toEqual([YO]);
    expect(registro).toEqual([YO]);

    b.pasa(700);
    expect(barras).toEqual([YO, MAQUINA]);
    expect(registro).toEqual([YO, MAQUINA]);
  });

  test('si la máquina abre el combate, su primera acción no espera', () => {
    const b = banco();
    const vistos = [];
    b.suscribir((a) => vistos.push(a.idEjecutor));

    b.llega(accion(MAQUINA));

    expect(vistos).toEqual([MAQUINA]);
  });

  test('un oyente roto no deja sin avisos a los demás', () => {
    const b = banco();
    const vistos = [];
    const error = jest.spyOn(console, 'error').mockImplementation(() => {});
    b.suscribir(() => {
      throw new Error('roto');
    });
    b.suscribir((a) => vistos.push(a.idEjecutor));

    b.llega(accion(YO));

    expect(vistos).toEqual([YO]);
    expect(error).toHaveBeenCalled();
    error.mockRestore();
  });

  test('con pausa cero no espera nunca (pruebas y vistas sin animación)', () => {
    const b = banco({ pausaMs: 0 });
    const vistos = [];
    b.suscribir((a) => vistos.push(a.idEjecutor));

    b.llega(accion(YO));
    b.llega(accion(MAQUINA));

    expect(vistos).toEqual([YO, MAQUINA]);
  });

  test('se suscribe una sola vez al canal aunque escuchen varios', () => {
    const canal = jest.fn();
    const suscribir = suscripcionConRitmo(canal, { yo: YO });
    suscribir(() => {});
    suscribir(() => {});
    expect(canal).toHaveBeenCalledTimes(1);
  });

  test('sin canal devuelve lo que le dieron', () => {
    expect(suscripcionConRitmo(undefined)).toBeUndefined();
  });
});
