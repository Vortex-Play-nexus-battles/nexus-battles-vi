/**
 * HU-NOT-006 — Bandeja: los tres criterios de #32 con un canal falso.
 *
 *   CA-01  el aviso llega por la cola privada; el contador se sincroniza
 *          entre sesiones y la lectura hecha en otra sesion se reconcilia
 *   CA-02  al conectar, la sesion se anuncia y recibe lo pendiente
 *   CA-03  si el canal se cae: consulta periodica por HTTP, reintento con
 *          espera creciente y reconciliacion al volver
 */

import { jest } from '@jest/globals';

import { crearBandeja, ESTADO_CANAL, esAviso, esContador, esProblema } from './bandeja.js';
import { CANAL } from './cliente-notificaciones.js';

/** Canal falso: registra suscripciones y envios, y deja simular al servidor. */
function canalFalso() {
  const canal = {
    suscripciones: new Map(),
    enviados: [],
    alCerrar: null,
    alError: null,
    suscribir(destino, alRecibir) {
      canal.suscripciones.set(destino, alRecibir);
      return destino;
    },
    enviar(destino, cuerpo) {
      canal.enviados.push({ destino, cuerpo });
    },
    cerrar() {
      canal.cerrado = true;
    },
    // Simulaciones del servidor
    servidorEnvia(mensaje) {
      canal.suscripciones.get(CANAL.COLA_PRIVADA)?.(mensaje);
    },
    seCae() {
      canal.alCerrar?.();
    },
  };
  return canal;
}

const aviso = (id, extra = {}) => ({
  id,
  tipo: 'subasta',
  titulo: `Aviso ${id}`,
  cuerpo: 'cuerpo',
  creadaEn: `2026-09-09T10:0${id.slice(-1)}:00Z`,
  leida: false,
  ...extra,
});

const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

function preparar({
  bandejaInicial = { usuarioId: 'u-1', noLeidas: 0, avisos: [] },
  conectar,
} = {}) {
  const canales = [];
  const cliente = {
    consultarBandeja: jest.fn(async () => bandejaInicial),
    marcarLeida: jest.fn(async () => ({ usuarioId: 'u-1', noLeidas: 0 })),
    entregarPendientes: jest.fn(async () => []),
  };
  const esperas = [];
  const alCambiar = jest.fn();
  const alAviso = jest.fn();
  const alError = jest.fn();
  const bandeja = crearBandeja({
    usuarioId: 'u-1',
    sesionId: 's-1',
    conectar:
      conectar ??
      jest.fn(async () => {
        const canal = canalFalso();
        canales.push(canal);
        return canal;
      }),
    cliente,
    esperar: jest.fn(async (ms) => {
      esperas.push(ms);
    }),
    esperas: [10, 20, 50],
    intervaloSondeo: 60,
    alCambiar,
    alAviso,
    alError,
  });
  return { bandeja, cliente, canales, esperas, alCambiar, alAviso, alError };
}

describe('formas de los mensajes del contrato', () => {
  test('distingue aviso, contador y problem details por su forma', () => {
    expect(esAviso(aviso('n-1'))).toBe(true);
    expect(esContador({ noLeidas: 3 })).toBe(true);
    expect(esProblema({ type: 'x', title: 'Mal', status: 400 })).toBe(true);
    expect(esAviso({ noLeidas: 3 })).toBe(false);
    expect(esContador(aviso('n-1'))).toBe(false);
    expect(esProblema(aviso('n-1'))).toBe(false);
  });
});

describe('iniciar', () => {
  test('pinta la bandeja por HTTP, conecta, se suscribe a la cola privada y anuncia la sesion (CA-02)', async () => {
    const { bandeja, cliente, canales } = preparar({
      bandejaInicial: {
        usuarioId: 'u-1',
        noLeidas: 1,
        avisos: [aviso('n-1'), aviso('n-2', { leida: true })],
      },
    });

    await bandeja.iniciar();

    expect(cliente.consultarBandeja).toHaveBeenCalledWith('u-1');
    expect(canales).toHaveLength(1);
    expect(canales[0].suscripciones.has(CANAL.COLA_PRIVADA)).toBe(true);
    expect(canales[0].enviados).toEqual([
      { destino: CANAL.ALTA_DE_SESION, cuerpo: { usuarioId: 'u-1', sesionId: 's-1' } },
    ]);
    expect(bandeja.estado.canal).toBe(ESTADO_CANAL.ESTABLE);
    expect(bandeja.estado.noLeidas).toBe(1);
    expect(bandeja.estado.avisos.map((a) => a.id)).toEqual(['n-2', 'n-1']);
  });

  test('CA-02: lo que la sesion se perdio llega por la cola tras el alta y se anuncia como nuevo', async () => {
    const { bandeja, canales, alAviso } = preparar();
    await bandeja.iniciar();

    canales[0].servidorEnvia(aviso('n-7'));
    canales[0].servidorEnvia({ noLeidas: 1 });

    expect(alAviso).toHaveBeenCalledTimes(1);
    expect(alAviso.mock.calls[0][0].id).toBe('n-7');
    expect(bandeja.estado.noLeidas).toBe(1);
  });
});

describe('CA-01: sincronizacion entre sesiones', () => {
  test('un aviso nuevo por la cola entra a la bandeja una sola vez aunque llegue repetido', async () => {
    const { bandeja, canales, alAviso, alCambiar } = preparar();
    await bandeja.iniciar();
    alCambiar.mockClear();

    canales[0].servidorEnvia(aviso('n-1'));
    canales[0].servidorEnvia(aviso('n-1'));

    expect(bandeja.estado.avisos).toHaveLength(1);
    expect(alAviso).toHaveBeenCalledTimes(1);
    expect(alCambiar).toHaveBeenCalled();
  });

  test('el contador que llega manda; si no cuadra con lo local, se reconcilia la bandeja', async () => {
    const { bandeja, cliente, canales } = preparar();
    await bandeja.iniciar();
    canales[0].servidorEnvia(aviso('n-1'));
    canales[0].servidorEnvia(aviso('n-2'));
    expect(bandeja.estado.noLeidas).toBe(2);

    // Otra sesion marco n-1 como leida: el servidor reenvia el contador.
    cliente.consultarBandeja.mockResolvedValueOnce({
      usuarioId: 'u-1',
      noLeidas: 1,
      avisos: [aviso('n-1', { leida: true }), aviso('n-2')],
    });
    canales[0].servidorEnvia({ noLeidas: 1 });
    await asentar();

    expect(bandeja.estado.noLeidas).toBe(1);
    expect(bandeja.estado.avisos.find((a) => a.id === 'n-1').leida).toBe(true);
    expect(cliente.consultarBandeja).toHaveBeenCalledTimes(2);
  });

  test('marcarLeida llama al servicio, marca localmente y toma la cuenta del servidor', async () => {
    const { bandeja, cliente, canales } = preparar();
    await bandeja.iniciar();
    canales[0].servidorEnvia(aviso('n-1'));
    cliente.marcarLeida.mockResolvedValueOnce({ usuarioId: 'u-1', noLeidas: 0 });

    await expect(bandeja.marcarLeida('n-1')).resolves.toBe(0);

    expect(cliente.marcarLeida).toHaveBeenCalledWith('u-1', 'n-1');
    expect(bandeja.estado.avisos[0].leida).toBe(true);
    expect(bandeja.estado.noLeidas).toBe(0);
  });

  test('un problem details por la cola sale por alError, nunca en silencio', async () => {
    const { bandeja, canales, alError } = preparar();
    await bandeja.iniciar();

    canales[0].servidorEnvia({
      type: 'x',
      title: 'Sesion invalida',
      status: 400,
      detail: 'falta sesionId',
    });

    expect(alError).toHaveBeenCalledTimes(1);
    expect(alError.mock.calls[0][0].message).toBe('falta sesionId');
  });
});

describe('CA-03: caida del canal', () => {
  test('al caerse pasa a reconectando, consulta por HTTP lo pendiente y reintenta con espera creciente', async () => {
    const conectar = jest.fn();
    const canales = [];
    conectar
      .mockImplementationOnce(async () => {
        const canal = canalFalso();
        canales.push(canal);
        return canal;
      })
      .mockRejectedValueOnce(new Error('sin red'))
      .mockRejectedValueOnce(new Error('sin red'))
      .mockImplementationOnce(async () => {
        const canal = canalFalso();
        canales.push(canal);
        return canal;
      });
    const { bandeja, cliente, esperas, alAviso, alCambiar } = preparar({ conectar });
    await bandeja.iniciar();

    cliente.entregarPendientes.mockResolvedValueOnce([aviso('n-9')]);
    cliente.consultarBandeja.mockResolvedValue({
      usuarioId: 'u-1',
      noLeidas: 1,
      avisos: [aviso('n-9')],
    });

    canales[0].seCae();
    await asentar();
    await asentar();
    await asentar();

    const estadosVistos = alCambiar.mock.calls.map(([e]) => e.canal);
    expect(estadosVistos).toContain(ESTADO_CANAL.RECONECTANDO);
    expect(estadosVistos).toContain(ESTADO_CANAL.SIN_CONEXION);
    expect(bandeja.estado.canal).toBe(ESTADO_CANAL.ESTABLE);

    // Espera creciente: 10, 20, 50 (tope) ...
    expect(esperas.slice(0, 3)).toEqual([10, 20, 50]);
    // Lo pendiente se recupero por HTTP mientras no habia canal y se anuncio.
    expect(cliente.entregarPendientes).toHaveBeenCalledWith('u-1', 's-1');
    expect(alAviso.mock.calls.some(([a]) => a.id === 'n-9')).toBe(true);
    expect(bandeja.estado.avisos.map((a) => a.id)).toContain('n-9');
    // Al volver, la nueva sesion de canal se anuncia otra vez.
    expect(canales[1].enviados[0].destino).toBe(CANAL.ALTA_DE_SESION);
    expect(conectar).toHaveBeenCalledTimes(4);
  });

  test('si la conexion inicial falla, no se queda callada: recupera igual', async () => {
    const conectar = jest
      .fn()
      .mockRejectedValueOnce(new Error('sin red'))
      .mockImplementationOnce(async () => canalFalso());
    const { bandeja, alError } = preparar({ conectar });

    await bandeja.iniciar();
    await asentar();
    await asentar();

    expect(alError).toHaveBeenCalled();
    expect(bandeja.estado.canal).toBe(ESTADO_CANAL.ESTABLE);
  });

  test('detener cierra el canal y no reintenta', async () => {
    const { bandeja, canales } = preparar();
    await bandeja.iniciar();

    bandeja.detener();
    canales[0].seCae();
    await asentar();

    expect(canales[0].cerrado).toBe(true);
    expect(bandeja.estado.canal).toBe(ESTADO_CANAL.SIN_CONEXION);
    expect(canales).toHaveLength(1);
  });
});
