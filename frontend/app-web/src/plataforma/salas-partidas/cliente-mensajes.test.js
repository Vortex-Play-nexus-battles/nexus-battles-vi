/**
 * B6 — Cliente de mensajes privados: rutas del contrato, cuerpos y motivos.
 */

import { jest } from '@jest/globals';

import {
  COLA_MENSAJES,
  ErrorDeMensajes,
  LARGO_MAXIMO,
  RECHAZOS,
  destinoDeEnvio,
  enviarPorRest,
  esUid,
  historial,
  listarConversaciones,
  marcarLeida,
  motivoDeProblema,
  nuevoIdCliente,
  textoDeRechazo,
  urlDeMensajesCon,
} from './cliente-mensajes.js';

const BRUNO = '22222222-2222-2222-2222-222222222222';

function respuesta(estado, cuerpo, cabeceras = {}) {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    headers: { get: (nombre) => cabeceras[nombre] ?? null },
    json: async () => {
      if (cuerpo === undefined) {
        throw new SyntaxError('sin cuerpo');
      }
      return cuerpo;
    },
  };
}

describe('destinos y enlaces del contrato', () => {
  test('la cola y el destino de envío son los del AsyncAPI', () => {
    expect(COLA_MENSAJES).toBe('/usuario/cola/mensajes-directos');
    expect(destinoDeEnvio(BRUNO)).toBe(`/app/mensajes-directos/${BRUNO}`);
    expect(LARGO_MAXIMO).toBe(500);
  });

  test('esUid solo acepta uids', () => {
    expect(esUid(BRUNO)).toBe(true);
    expect(esUid(BRUNO.toUpperCase())).toBe(true);
    expect(esUid('dm:a:b')).toBe(false);
    expect(esUid('')).toBe(false);
    expect(esUid(null)).toBe(false);
    expect(esUid(42)).toBe(false);
  });

  test('el enlace lleva a la vista de mensajes con ?con=<uid>', () => {
    const url = new URL(
      urlDeMensajesCon(BRUNO, {
        base: 'http://localhost:8099/frontend/app-web/src/comun/sesion.js',
      }),
    );
    expect(url.pathname).toBe('/frontend/app-web/src/plataforma/salas-partidas/mensajes.html');
    expect(url.searchParams.get('con')).toBe(BRUNO);
    expect(urlDeMensajesCon(BRUNO)).toMatch(/mensajes\.html\?con=2222/);
  });

  test('cada envío lleva un idCliente distinto y de como mucho 64 caracteres', () => {
    const uno = nuevoIdCliente();
    const otro = nuevoIdCliente();
    expect(uno).not.toBe(otro);
    expect(uno.length).toBeLessThanOrEqual(64);
  });

  test('sin crypto.randomUUID también hay idCliente', () => {
    const original = globalThis.crypto;
    Object.defineProperty(globalThis, 'crypto', { value: {}, configurable: true });
    try {
      expect(nuevoIdCliente()).toMatch(/^c-/);
    } finally {
      Object.defineProperty(globalThis, 'crypto', { value: original, configurable: true });
    }
  });
});

describe('motivos de rechazo', () => {
  test('cada motivo del contrato tiene su texto, con tildes y sin códigos', () => {
    for (const motivo of [
      'TEXTO_NO_PERMITIDO',
      'SANCIONADO',
      'DESTINATARIO_INEXISTENTE',
      'DESTINATARIO_PROPIO',
      'DEMASIADO_RAPIDO',
      'TEXTO_INVALIDO',
      'MODERACION_NO_DISPONIBLE',
    ]) {
      const texto = textoDeRechazo(motivo);
      expect(texto).toBe(RECHAZOS[motivo]);
      expect(texto.titulo).toBeTruthy();
      // Ni el código HTTP del rechazo ni una URL: eso es para quien programa.
      expect(`${texto.titulo} ${texto.detalle}`).not.toMatch(/\b(400|403|404|422|429|503)\b|http/);
    }
    expect(textoDeRechazo('MODERACION_NO_DISPONIBLE').tono).toBe('error');
    expect(textoDeRechazo('OTRA_COSA').titulo).toBe('Tu mensaje no se envió');
    expect(textoDeRechazo(undefined).tono).toBe('error');
  });

  test('por REST el motivo se saca del type, nunca del texto', () => {
    const tipo = (sufijo) => ({ type: `https://nexusbattles.local/errores/${sufijo}` });
    expect(motivoDeProblema(tipo('contenido-bloqueado'))).toBe('TEXTO_NO_PERMITIDO');
    expect(motivoDeProblema(tipo('jugador-sancionado'))).toBe('SANCIONADO');
    expect(motivoDeProblema(tipo('destinatario-inexistente'))).toBe('DESTINATARIO_INEXISTENTE');
    expect(motivoDeProblema(tipo('destinatario-propio'))).toBe('DESTINATARIO_PROPIO');
    expect(motivoDeProblema(tipo('demasiados-mensajes'))).toBe('DEMASIADO_RAPIDO');
    expect(motivoDeProblema(tipo('mensaje-invalido'))).toBe('TEXTO_INVALIDO');
    expect(motivoDeProblema(tipo('moderacion-no-disponible'))).toBe('MODERACION_NO_DISPONIBLE');
    expect(motivoDeProblema(tipo('otra'))).toBeNull();
    expect(motivoDeProblema(null)).toBeNull();
  });
});

describe('API REST', () => {
  test('conversaciones: GET del contrato', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, [{ uidOtro: BRUNO }]));
    await expect(listarConversaciones({ fetchImpl })).resolves.toEqual([{ uidOtro: BRUNO }]);
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/mensajes-directos/conversaciones');
  });

  test('historial: límite siempre, antesDe solo cuando se pagina hacia atrás', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, []));
    await historial(BRUNO, { fetchImpl });
    await historial(BRUNO, { antesDe: '2026-09-25T18:00:00Z', limite: 20, fetchImpl });
    expect(fetchImpl.mock.calls[0][0]).toBe(
      `/api/v1/mensajes-directos/conversaciones/${BRUNO}/mensajes?limite=50`,
    );
    expect(fetchImpl.mock.calls[1][0]).toBe(
      `/api/v1/mensajes-directos/conversaciones/${BRUNO}/mensajes?limite=20&antesDe=2026-09-25T18%3A00%3A00Z`,
    );
  });

  test('envío de respaldo: POST con texto e idCliente, sin remitente', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(201, { id: 'm1' }));
    await expect(
      enviarPorRest(BRUNO, { texto: 'hola', idCliente: 'c1' }, { fetchImpl }),
    ).resolves.toEqual({ id: 'm1' });
    const [url, peticion] = fetchImpl.mock.calls[0];
    expect(url).toBe(`/api/v1/mensajes-directos/conversaciones/${BRUNO}/mensajes`);
    expect(peticion.method).toBe('POST');
    expect(JSON.parse(peticion.body)).toEqual({ texto: 'hola', idCliente: 'c1' });

    await enviarPorRest(BRUNO, { texto: 'sin id' }, { fetchImpl });
    expect(JSON.parse(fetchImpl.mock.calls[1][1].body)).toEqual({ texto: 'sin id' });
  });

  test('un rechazo sale como ErrorDeMensajes con su motivo y su Retry-After', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(
        429,
        {
          type: 'https://nexusbattles.local/errores/demasiados-mensajes',
          title: 'Vas demasiado rápido',
          status: 429,
        },
        { 'Retry-After': '4' },
      ),
    );
    const error = await enviarPorRest(BRUNO, { texto: 'x' }, { fetchImpl }).catch((e) => e);
    expect(error).toBeInstanceOf(ErrorDeMensajes);
    expect(error.motivo).toBe('DEMASIADO_RAPIDO');
    expect(error.estado).toBe(429);
    expect(error.reintentarEnSegundos).toBe(4);
  });

  test('un fallo sin cuerpo no revienta: se queda con el estado', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(401, undefined));
    const error = await listarConversaciones({ fetchImpl }).catch((e) => e);
    expect(error.estado).toBe(401);
    expect(error.motivo).toBeNull();
    expect(error.reintentarEnSegundos).toBeNull();
  });

  test('marcar leído: POST a /leido', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(204, undefined));
    await marcarLeida(BRUNO, { fetchImpl });
    expect(fetchImpl).toHaveBeenCalledWith(
      `/api/v1/mensajes-directos/conversaciones/${BRUNO}/leido`,
      { method: 'POST' },
    );
  });
});
