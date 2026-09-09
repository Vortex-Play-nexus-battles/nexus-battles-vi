/**
 * HU-NOT-006 — Cliente HTTP y direcciones del canal, contra la forma exacta de
 * `contracts/openapi/notificaciones.yaml` y `contracts/websocket/notificaciones.yaml`.
 */

import { jest } from '@jest/globals';

import {
  urlDelCanal,
  identificadorDeSesion,
  leerSesion,
  consultarBandeja,
  marcarLeida,
  entregarPendientes,
  ErrorDeApi,
  CANAL,
  CLAVE_SESION_CANAL,
} from './cliente-notificaciones.js';

function respuesta(estado, cuerpo) {
  return { ok: estado >= 200 && estado < 300, status: estado, json: async () => cuerpo };
}

function almacenFalso(inicial = {}) {
  const datos = new Map(Object.entries(inicial));
  return {
    getItem: (k) => datos.get(k) ?? null,
    setItem: (k, v) => datos.set(k, v),
    datos,
  };
}

afterEach(() => {
  document.head.innerHTML = '';
});

describe('urlDelCanal', () => {
  test('deriva ws:// del origen de la pagina y lleva usuario y sesion como exige el contrato', () => {
    const location = { protocol: 'http:', host: 'localhost:8085' };
    expect(urlDelCanal({ base: '', usuarioId: 'u-1', sesionId: 's-1', location })).toBe(
      'ws://localhost:8085/ws?usuario=u-1&sesion=s-1',
    );
  });

  test('con base https la URL es wss y respeta la base declarada', () => {
    document.head.innerHTML =
      '<meta name="nexus-api-base" content="https://api.nexusbattles.local/notificaciones" />';
    expect(urlDelCanal({ usuarioId: 'u', sesionId: 's', location: {} })).toBe(
      'wss://api.nexusbattles.local/ws?usuario=u&sesion=s',
    );
  });

  test('por la URL del canal NO viaja el token de sesion', () => {
    sessionStorage.setItem('nexus.token', 'jwt-secreto');
    const url = urlDelCanal({ base: 'http://x', usuarioId: 'u', sesionId: 's', location: {} });
    expect(url).not.toContain('jwt-secreto');
    sessionStorage.removeItem('nexus.token');
  });

  test('los destinos son los del contrato AsyncAPI', () => {
    expect(CANAL).toEqual({
      RUTA_HANDSHAKE: '/ws',
      COLA_PRIVADA: '/usuario/cola/notificaciones',
      ALTA_DE_SESION: '/app/notificaciones/sesion',
    });
  });
});

describe('identificadorDeSesion', () => {
  test('crea uno estable la primera vez y lo reutiliza despues', () => {
    const almacen = almacenFalso();
    const generador = { randomUUID: () => 'uuid-1' };
    expect(identificadorDeSesion(almacen, generador)).toBe('uuid-1');
    expect(almacen.datos.get(CLAVE_SESION_CANAL)).toBe('uuid-1');
    generador.randomUUID = () => 'uuid-2';
    expect(identificadorDeSesion(almacen, generador)).toBe('uuid-1');
  });

  test('sin crypto.randomUUID genera igual un identificador', () => {
    const almacen = almacenFalso();
    expect(identificadorDeSesion(almacen, {})).toMatch(/^sesion-/);
  });
});

describe('leerSesion', () => {
  test('lee las claves que dejan las vistas de cuentas', () => {
    const almacen = almacenFalso({ 'nexus.usuarioId': 'u-1', 'nexus.apodoActual': 'Simon_P' });
    expect(leerSesion(almacen)).toEqual({ usuarioId: 'u-1', apodo: 'Simon_P' });
  });
});

describe('HTTP', () => {
  test('consultarBandeja pide GET /users/{id}/notifications', async () => {
    const bandeja = { usuarioId: 'u-1', noLeidas: 1, avisos: [] };
    const fetchImpl = jest.fn(async () => respuesta(200, bandeja));
    await expect(consultarBandeja('u-1', { fetchImpl })).resolves.toEqual(bandeja);
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/users/u-1/notifications');
  });

  test('marcarLeida hace POST .../read y devuelve el contador', async () => {
    const fetchImpl = jest.fn(async () => respuesta(200, { usuarioId: 'u-1', noLeidas: 0 }));
    await expect(marcarLeida('u-1', 'n/1', { fetchImpl })).resolves.toEqual({
      usuarioId: 'u-1',
      noLeidas: 0,
    });
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/users/u-1/notifications/n%2F1/read', {
      method: 'POST',
    });
  });

  test('entregarPendientes hace POST .../sessions/{sesionId}/pending', async () => {
    const fetchImpl = jest.fn(async () => respuesta(200, [{ id: 'n-1' }]));
    await expect(entregarPendientes('u-1', 's-1', { fetchImpl })).resolves.toEqual([{ id: 'n-1' }]);
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/users/u-1/sessions/s-1/pending', {
      method: 'POST',
    });
  });

  test('un 404 llega como ErrorDeApi con tipo y estado', async () => {
    const fetchImpl = jest.fn(async () =>
      respuesta(404, {
        type: 'https://nexusbattles.local/errores/aviso-no-encontrado',
        title: 'Aviso no encontrado',
        status: 404,
      }),
    );
    const error = await marcarLeida('u-1', 'x', { fetchImpl }).catch((e) => e);
    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.estado).toBe(404);
    expect(error.tipo).toBe('https://nexusbattles.local/errores/aviso-no-encontrado');
  });
});
