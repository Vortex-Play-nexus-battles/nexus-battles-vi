/**
 * Auditoría del 4-oct — el ritmo del combate: tu golpe primero, el de la
 * máquina después, para que su contragolpe no parezca parte del tuyo.
 */
import { jest } from '@jest/globals';
import {
  PAUSA_ENTRE_ACCIONES_MS,
  PAUSA_TRAS_AJENA_MS,
  suscripcionConRitmo,
} from './ritmo-de-combate.js';

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
function banco({ pausaMs = 700, pausaTrasAjenaMs } = {}) {
  let entregar = null;
  let reloj = 0;
  const pendientes = [];
  const canal = (alRecibir) => {
    entregar = alRecibir;
  };
  const suscribir = suscripcionConRitmo(canal, {
    yo: YO,
    pausaMs,
    pausaTrasAjenaMs,
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
  test('las pausas de producción dan 2–3 s para leer cada golpe (revisión del 6-oct)', () => {
    expect(PAUSA_ENTRE_ACCIONES_MS).toBeGreaterThanOrEqual(2000);
    expect(PAUSA_ENTRE_ACCIONES_MS).toBeLessThanOrEqual(3000);
    expect(PAUSA_TRAS_AJENA_MS).toBeGreaterThan(0);
    expect(PAUSA_TRAS_AJENA_MS).toBeLessThan(PAUSA_ENTRE_ACCIONES_MS);
  });

  test('tu golpe y el turno de la IA al instante; su golpe tras la pausa; tu turno, después de verlo', () => {
    const b = banco({ pausaMs: 2200, pausaTrasAjenaMs: 900 });
    const vistos = [];
    b.suscribir((aviso) => vistos.push(`${aviso.tipo}:${aviso.idEjecutor ?? aviso.idJugador}`));

    b.llega(accion(YO));
    b.llega(turno(MAQUINA, 2));
    b.llega(accion(MAQUINA));
    b.llega(turno(YO, 3));

    // El mando se cierra al instante y el HUD dice de quién es el turno.
    expect(vistos).toEqual(['partida.accion.resuelta:u-yo', 'partida.turno.cambiado:u-maquina']);

    b.pasa(2199);
    expect(vistos).toHaveLength(2);

    b.pasa(1);
    expect(vistos).toEqual([
      'partida.accion.resuelta:u-yo',
      'partida.turno.cambiado:u-maquina',
      'partida.accion.resuelta:u-maquina',
    ]);

    // El turno que vuelve (con su +2 de poder) no pisa el golpe de la IA.
    b.pasa(899);
    expect(vistos).toHaveLength(3);
    b.pasa(1);
    expect(vistos[3]).toBe('partida.turno.cambiado:u-yo');
  });

  test('el final tras el golpe de la IA también espera a que se vea ese golpe', () => {
    const b = banco({ pausaMs: 2200, pausaTrasAjenaMs: 900 });
    const vistos = [];
    b.suscribir((aviso) => vistos.push(aviso.tipo));

    b.llega(accion(YO));
    b.llega(accion(MAQUINA));
    b.llega({ tipo: 'partida.finalizada', idPartida: 'p', ganadores: [MAQUINA] });

    b.pasa(2200);
    expect(vistos).toEqual(['partida.accion.resuelta', 'partida.accion.resuelta']);
    b.pasa(900);
    expect(vistos).toEqual([
      'partida.accion.resuelta',
      'partida.accion.resuelta',
      'partida.finalizada',
    ]);
  });

  test('lo que sigue a TU acción no espera: solo se hace esperar lo que viene tras una ajena', () => {
    const b = banco({ pausaMs: 2200, pausaTrasAjenaMs: 900 });
    const vistos = [];
    b.suscribir((aviso) => vistos.push(aviso.tipo));

    b.llega(accion(YO));
    b.llega({ tipo: 'partida.finalizada', idPartida: 'p', ganadores: [YO] });

    expect(vistos).toEqual(['partida.accion.resuelta', 'partida.finalizada']);
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

  /*
   * Revisión del 6-oct, punto 17: la cuenta atrás del comienzo. Si la IA abre,
   * su golpe llega en el mismo instante en que empieza la partida; se retiene
   * hasta que termina la cuenta, y después sigue con el ritmo de siempre.
   */
  test('retenerHasta: nada se enseña durante la cuenta atrás; después, en orden', () => {
    const b = banco({ pausaMs: 2200, pausaTrasAjenaMs: 900 });
    const vistos = [];
    b.suscribir((aviso) => vistos.push(`${aviso.tipo}:${aviso.idEjecutor ?? aviso.idJugador}`));
    b.suscribir.retenerHasta(5800);

    b.llega(accion(MAQUINA));
    b.llega(turno(YO, 2));
    b.pasa(5799);
    expect(vistos).toEqual([]);

    b.pasa(1);
    expect(vistos).toEqual(['partida.accion.resuelta:u-maquina']);
    // Lo que sigue a la acción ajena guarda su pausa de siempre.
    b.pasa(899);
    expect(vistos).toHaveLength(1);
    b.pasa(1);
    expect(vistos[1]).toBe('partida.turno.cambiado:u-yo');
  });

  test('retenerHasta solo alarga: una retención más corta no acorta la vigente', () => {
    const b = banco({ pausaMs: 0 });
    const vistos = [];
    b.suscribir((aviso) => vistos.push(aviso.tipo));
    b.suscribir.retenerHasta(3000);
    b.suscribir.retenerHasta(1000);

    b.llega(turno(YO, 1));
    b.pasa(2999);
    expect(vistos).toEqual([]);
    b.pasa(1);
    expect(vistos).toEqual(['partida.turno.cambiado']);
  });
});
