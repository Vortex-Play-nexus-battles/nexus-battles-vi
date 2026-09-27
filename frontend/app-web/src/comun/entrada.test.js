/**
 * Entrar al juego (login y registro) — R17, y desde B1 con el correo
 * verificado.
 *
 * Lo importante: cuando algo falla se dice qué campo y por qué; y desde la
 * 2.0.0 de identidad, una cuenta nueva NO entra sola: nace pendiente de
 * verificar su correo y se lleva a la verificación, con el correo en
 * `sessionStorage` y nunca en la dirección.
 */

import { jest } from '@jest/globals';

import { CLAVES_DEL_CORREO } from './codigo-de-correo.js';
import {
  CLAVE_CORREO_REGISTRADO,
  entrarCon,
  identificadorDeSesion,
  mensajeDelServidor,
  pedirLogin,
  registrarCuenta,
} from './entrada.js';
import { CLAVES } from './sesion.js';

const BASE = 'http://localhost:8099/frontend/app-web/src/comun/entrada.js';
const UID = '0f3c9a4e-5d1b-4c8e-9a2f-6b7d8e9f0a1b';

function tokenCon(claims) {
  const cuerpo = btoa(JSON.stringify(claims))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `entrada.${cuerpo}.firma`;
}

const TOKEN = tokenCon({ uid: UID, sub: 'Lyra', rol: 'JUGADOR', exp: 4_102_444_800 });

function respuesta(status, cuerpo) {
  const texto = typeof cuerpo === 'string' ? cuerpo : JSON.stringify(cuerpo ?? '');
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(cuerpo === undefined ? '' : texto),
  });
}

const LOGIN_OK = {
  usuarioId: 7,
  uid: UID,
  apodo: 'Lyra',
  rol: 'JUGADOR',
  token: TOKEN,
  onboardingListo: false,
};

beforeEach(() => {
  sessionStorage.clear();
});

describe('mensajeDelServidor', () => {
  test('lee problem details, el JSON antiguo y el texto plano', () => {
    expect(mensajeDelServidor({ detail: 'El apodo ya está en uso.' })).toBe(
      'El apodo ya está en uso.',
    );
    expect(mensajeDelServidor({ mensaje: 'Antiguo' })).toBe('Antiguo');
    expect(mensajeDelServidor('Texto plano')).toBe('Texto plano');
    expect(mensajeDelServidor('')).toBeUndefined();
    expect(mensajeDelServidor(null)).toBeUndefined();
  });
});

describe('identificadorDeSesion', () => {
  test('el uid del token manda sobre la clave primaria (ADR-002)', () => {
    expect(identificadorDeSesion(TOKEN, 7)).toBe(UID);
    expect(identificadorDeSesion('ilegible', 7)).toBe('7');
  });
});

describe('pedirLogin', () => {
  test('pide problem details y manda las credenciales como JSON', async () => {
    const fetchImpl = jest.fn(() => respuesta(200, LOGIN_OK));
    const { respuesta: r, body } = await pedirLogin(
      { email: 'lyra@nexus.test', password: 'Secreta#2026' },
      fetchImpl,
    );
    expect(r.ok).toBe(true);
    expect(body).toEqual(LOGIN_OK);
    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/auth/login');
    expect(opciones.method).toBe('POST');
    expect(opciones.headers.Accept).toContain('application/problem+json');
    expect(JSON.parse(opciones.body)).toEqual({
      email: 'lyra@nexus.test',
      password: 'Secreta#2026',
    });
    // Nunca con el token de una sesión anterior.
    expect(opciones.headers.Authorization).toBeUndefined();
  });
});

describe('entrarCon', () => {
  test('guarda la sesión y, con el alta a medias, lleva a prepararla', () => {
    const destino = new URL(entrarCon(LOGIN_OK, { almacen: sessionStorage, base: BASE }));
    expect(destino.pathname).toBe('/frontend/app-web/src/cuentas/preparando.html');
    expect(sessionStorage.getItem(CLAVES.token)).toBe(TOKEN);
    expect(sessionStorage.getItem(CLAVES.usuarioId)).toBe(UID);
    expect(sessionStorage.getItem(CLAVES.apodo)).toBe('Lyra');
    expect(sessionStorage.getItem(CLAVES.rol)).toBe('JUGADOR');
  });

  test('con el alta lista, directo a la vuelta', () => {
    const destino = entrarCon(
      { ...LOGIN_OK, onboardingListo: true },
      { volver: '/frontend/app-web/src/cuentas/tienda.html', almacen: sessionStorage, base: BASE },
    );
    expect(destino).toBe('http://localhost:8099/frontend/app-web/src/cuentas/tienda.html');
  });

  test('sin uid en la respuesta (servidor anterior), lo saca del token', () => {
    entrarCon({ ...LOGIN_OK, uid: undefined }, { almacen: sessionStorage, base: BASE });
    expect(sessionStorage.getItem(CLAVES.usuarioId)).toBe(UID);
  });
});

describe('entrarCon tras verificar el correo (B1)', () => {
  test('la primera entrada de una cuenta recién verificada pasa SIEMPRE por la preparación', () => {
    // Aunque el alta ya esté lista y haya una vuelta: es la pantalla que le
    // dice qué créditos y qué héroe acaba de recibir.
    const destino = new URL(
      entrarCon(
        { ...LOGIN_OK, onboardingListo: true },
        {
          volver: '/frontend/app-web/src/cuentas/tienda.html',
          cuentaNueva: true,
          almacen: sessionStorage,
          base: BASE,
        },
      ),
    );
    expect(destino.pathname).toBe('/frontend/app-web/src/cuentas/preparando.html');
    expect(sessionStorage.getItem(CLAVES.token)).toBe(TOKEN);
  });
});

describe('registrarCuenta', () => {
  const credenciales = { email: 'lyra@nexus.test', password: 'Secreta#2026' };
  const AHORA = 1_790_000_000_000;

  function servidor({ registro, login }) {
    return jest.fn((url) => {
      if (url.endsWith('/auth/registro')) {
        return registro();
      }
      return login();
    });
  }

  function registrar(fetchImpl, extra = {}) {
    return registrarCuenta(new FormData(), credenciales, {
      fetchImpl,
      almacen: sessionStorage,
      base: BASE,
      ahora: () => AHORA,
      ...extra,
    });
  }

  test('B1 — la cuenta nace pendiente: a confirmar el correo, sin entrar y sin el correo en la URL', async () => {
    const fetchImpl = servidor({
      registro: () => respuesta(201, { apodo: 'Lyra', estado: 'PENDIENTE_VERIFICACION' }),
      login: () => respuesta(200, LOGIN_OK),
    });
    const datos = new FormData();

    const resultado = await registrarCuenta(datos, credenciales, {
      fetchImpl,
      almacen: sessionStorage,
      base: BASE,
      ahora: () => AHORA,
    });

    expect(resultado.resultado).toBe('pendiente');
    const destino = new URL(resultado.destino);
    expect(destino.pathname).toBe('/frontend/app-web/src/cuentas/verificar-cuenta.html');
    expect(destino.searchParams.get('motivo')).toBe('registro');
    expect(destino.href).not.toContain('lyra');
    // El correo viaja por sessionStorage, y la espera del reenvío empieza ya:
    // el código acaba de salir.
    expect(sessionStorage.getItem(CLAVES_DEL_CORREO.porVerificar)).toBe('lyra@nexus.test');
    expect(sessionStorage.getItem(CLAVES_DEL_CORREO.ultimoEnvio)).toBe(String(AHORA));
    // No se intenta entrar: el login respondería 403 cuenta-no-verificada.
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(CLAVES.token)).toBeNull();
    // El multipart va tal cual, con problem details pedidos.
    const [, opciones] = fetchImpl.mock.calls[0];
    expect(opciones.body).toBe(datos);
    expect(opciones.headers.Accept).toContain('application/problem+json');
    expect(opciones.headers['Content-Type']).toBeUndefined();
  });

  test('sin estado en la respuesta, también a confirmar el correo: solo ACTIVO entra', async () => {
    const fetchImpl = servidor({
      registro: () => respuesta(201, { apodo: 'Lyra' }),
      login: () => respuesta(200, LOGIN_OK),
    });
    const resultado = await registrar(fetchImpl);
    expect(resultado.resultado).toBe('pendiente');
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  test('un servicio anterior a la verificación (cuenta ACTIVO): entra sola y va a «Preparando tu cuenta»', async () => {
    const fetchImpl = servidor({
      registro: () => respuesta(201, { apodo: 'Lyra', estado: 'ACTIVO' }),
      login: () => respuesta(200, { ...LOGIN_OK, onboardingListo: true }),
    });

    const resultado = await registrar(fetchImpl);

    expect(resultado.resultado).toBe('dentro');
    const destino = new URL(resultado.destino);
    expect(destino.pathname).toBe('/frontend/app-web/src/cuentas/preparando.html');
    expect(destino.searchParams.get('volver')).toBe('/frontend/app-web/src/cuentas/index.html');
    expect(sessionStorage.getItem(CLAVES.token)).toBe(TOKEN);
  });

  test('un rechazo dice el motivo y el campo a marcar', async () => {
    const fetchImpl = servidor({
      registro: () =>
        respuesta(400, {
          type: 'https://nexusbattles.upb.edu.co/errors/apodo-en-uso',
          title: 'El apodo ya está en uso',
          status: 400,
          detail: 'El apodo ya está en uso.',
          campo: 'apodo',
        }),
      login: () => respuesta(200, LOGIN_OK),
    });

    const resultado = await registrar(fetchImpl);

    // UXC-7 — por el `type` estable, con palabras del producto y qué hacer.
    expect(resultado).toEqual({
      resultado: 'rechazada',
      mensaje:
        'Ese apodo ya lo usa otro jugador. Prueba con otro: es el nombre con el que te verán en las partidas.',
      campo: 'apodo',
      estado: 400,
      motivo: 'apodo-en-uso',
    });
    // No se intenta nada más con una cuenta que no se creó.
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(CLAVES.token)).toBeNull();
    expect(sessionStorage.getItem(CLAVES_DEL_CORREO.porVerificar)).toBeNull();
  });

  test('UXC-7 — apodo no permitido: se explica sin decir qué palabra ni resolver la lista negra aquí', async () => {
    const fetchImpl = servidor({
      registro: () =>
        respuesta(400, {
          type: 'https://nexusbattles.upb.edu.co/errors/apodo-no-permitido',
          title: 'Apodo no permitido',
          status: 400,
          detail: 'El apodo contiene terminos prohibidos.',
          campo: 'apodo',
        }),
      login: () => respuesta(200, LOGIN_OK),
    });
    const resultado = await registrarYEntrar(new FormData(), credenciales, {
      fetchImpl,
      almacen: sessionStorage,
    });
    expect(resultado.motivo).toBe('apodo-no-permitido');
    expect(resultado.mensaje).toMatch(/^Ese apodo no está permitido/);
    expect(resultado.campo).toBe('apodo');
  });

  test('UXC-7 — un motivo que la vista no conoce se dice con el texto del servidor', async () => {
    const fetchImpl = servidor({
      registro: () =>
        respuesta(400, {
          type: 'https://nexusbattles.upb.edu.co/errors/datos-de-registro-invalidos',
          status: 400,
          detail: 'Los nombres no pueden superar 80 caracteres.',
          campo: 'nombres',
        }),
      login: () => respuesta(200, LOGIN_OK),
    });
    const resultado = await registrarYEntrar(new FormData(), credenciales, {
      fetchImpl,
      almacen: sessionStorage,
    });
    expect(resultado.mensaje).toBe('Los nombres no pueden superar 80 caracteres.');
  });

  test('el texto plano de siempre también sirve (servidor sin problem details)', async () => {
    const fetchImpl = servidor({
      registro: () => respuesta(400, 'El correo electrónico ya está registrado.'),
      login: () => respuesta(200, LOGIN_OK),
    });
    const resultado = await registrar(fetchImpl);
    expect(resultado.mensaje).toBe('El correo electrónico ya está registrado.');
    expect(resultado.campo).toBeNull();
  });

  test('un 5xx no enseña el error interno: dice que se intente luego', async () => {
    const fetchImpl = servidor({
      registro: () => respuesta(500, { detail: 'org.postgresql.util.PSQLException: …' }),
      login: () => respuesta(200, LOGIN_OK),
    });
    const resultado = await registrar(fetchImpl);
    expect(resultado.resultado).toBe('rechazada');
    expect(resultado.mensaje).not.toContain('PSQL');
    expect(resultado.mensaje).toContain('Inténtalo de nuevo');
  });

  test('2.0.0 — la lista negra no respondió (503): se dice que el apodo no se pudo comprobar', async () => {
    const fetchImpl = servidor({
      registro: () =>
        respuesta(503, {
          type: 'https://nexusbattles.upb.edu.co/errors/moderacion-no-disponible',
          title: 'Moderación no disponible',
          status: 503,
          detail: 'Detalle interno que no se enseña.',
        }),
      login: () => respuesta(200, LOGIN_OK),
    });
    const resultado = await registrar(fetchImpl);
    expect(resultado.resultado).toBe('rechazada');
    expect(resultado.estado).toBe(503);
    expect(resultado.mensaje).toContain('comprobar tu apodo');
    expect(resultado.mensaje).not.toContain('interno');
  });

  test.each([
    ['el login rechaza', () => respuesta(403, { detail: 'Cuenta inactiva' })],
    ['el login no contesta', () => Promise.reject(new TypeError('red'))],
  ])('cuenta ACTIVO y %s: al login con el correo ya escrito', async (_caso, login) => {
    const fetchImpl = servidor({
      registro: () => respuesta(201, { estado: 'ACTIVO' }),
      login,
    });

    const resultado = await registrar(fetchImpl);

    expect(resultado.resultado).toBe('creada');
    const destino = new URL(resultado.destino);
    expect(destino.pathname).toBe('/frontend/app-web/src/cuentas/login.html');
    expect(destino.searchParams.get('motivo')).toBe('registrada');
    // El correo viaja por sessionStorage, nunca por la URL.
    expect(destino.search).not.toContain('lyra');
    expect(sessionStorage.getItem(CLAVE_CORREO_REGISTRADO)).toBe('lyra@nexus.test');
  });

  test('un fallo de red al CREAR la cuenta se propaga: no se sabe si se creó', async () => {
    const fetchImpl = servidor({
      registro: () => Promise.reject(new TypeError('red')),
      login: () => respuesta(200, LOGIN_OK),
    });
    await expect(registrar(fetchImpl)).rejects.toThrow('red');
  });
});
