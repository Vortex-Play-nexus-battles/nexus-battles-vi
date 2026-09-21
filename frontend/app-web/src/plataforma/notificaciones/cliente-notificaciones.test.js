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
  tokenDeSesion,
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
  test('deriva ws:// del origen de la pagina, sin usuario ni sesion en la URL', () => {
    const location = { protocol: 'http:', host: 'localhost:8085' };
    expect(urlDelCanal({ base: '', location })).toBe('ws://localhost:8085/ws/notificaciones');
  });

  test('con base https la URL es wss y respeta la base declarada', () => {
    document.head.innerHTML =
      '<meta name="nexus-api-base" content="https://api.nexusbattles.local/notificaciones" />';
    expect(urlDelCanal({ location: {} })).toBe('wss://api.nexusbattles.local/ws/notificaciones');
  });

  test('por la URL del canal NO viaja ni el token ni el usuario: la identidad va en el CONNECT', () => {
    sessionStorage.setItem('nexus.token', 'jwt-secreto');
    const url = urlDelCanal({ base: 'http://x', location: {} });
    expect(url).not.toContain('jwt-secreto');
    expect(url).not.toContain('usuario=');
    expect(url).not.toContain('sesion=');
    sessionStorage.removeItem('nexus.token');
  });

  test('los destinos son los del contrato AsyncAPI', () => {
    expect(CANAL).toEqual({
      RUTA_HANDSHAKE: '/ws/notificaciones',
      COLA_PRIVADA: '/usuario/cola/notificaciones',
      ALTA_DE_SESION: '/app/notificaciones/sesion',
    });
  });
});

describe('tokenDeSesion', () => {
  test('es el JWT que dejo el login, el mismo que lleva la API HTTP', () => {
    expect(tokenDeSesion(almacenFalso({ 'nexus.token': 'jwt-1' }))).toBe('jwt-1');
    expect(tokenDeSesion(almacenFalso())).toBeNull();
    expect(tokenDeSesion(null)).toBeNull();
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

  test('el destinatario sale del token, no de la clave que pisa el panel de administracion', () => {
    // `gestion-usuarios.js` escribe en `nexus.usuarioId` el id del usuario que
    // el administrador acaba de seleccionar. Suscribirse con eso le entregaria
    // las notificaciones de esa persona a quien no son.
    const uid = '44444444-4444-4444-4444-444444444444';
    const cuerpo = btoa(JSON.stringify({ uid }))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
    const almacen = almacenFalso({
      'nexus.usuarioId': '99',
      'nexus.apodoActual': 'Simon_P',
      'nexus.token': `eyJ.${cuerpo}.firma`,
    });

    expect(leerSesion(almacen).usuarioId).toBe(uid);
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
