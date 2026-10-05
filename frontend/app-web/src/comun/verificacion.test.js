/**
 * Verificación del correo — cliente (identidad 2.0.0).
 *
 * Lo que se prueba: las dos rutas y los dos cuerpos del contrato; que un
 * rechazo vuelva con su `type` para que la vista decida; que el reenvío diga
 * siempre lo mismo; y que la espera entre reenvíos cuente bien y no se quede
 * bloqueada si el reloj se mueve.
 */

import { jest } from '@jest/globals';

import { CLAVES_DEL_CORREO } from './codigo-de-correo.js';
import {
  ESPERA_ENTRE_REENVIOS_MS,
  MENSAJE_DE_REENVIO,
  RUTAS_DE_VERIFICACION,
  anotarEnvio,
  confirmarCorreo,
  reenviarCodigo,
  segundosParaReenviar,
} from './verificacion.js';

const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';

function respuesta(estado, cuerpo) {
  const texto = typeof cuerpo === 'string' ? cuerpo : JSON.stringify(cuerpo ?? '');
  return Promise.resolve({
    ok: estado >= 200 && estado < 300,
    status: estado,
    text: () => Promise.resolve(cuerpo === undefined ? '' : texto),
  });
}

beforeEach(() => {
  sessionStorage.clear();
});

describe('confirmarCorreo', () => {
  test('POST a la ruta del contrato con el correo y el código, pidiendo problem details', async () => {
    const fetchImpl = jest.fn(() =>
      respuesta(200, { estado: 'ACTIVO', mensaje: 'Tu correo quedó verificado.' }),
    );

    const resultado = await confirmarCorreo(
      { email: 'ana@nexus.test', codigo: 'K7QX2M9P' },
      { fetchImpl },
    );

    expect(resultado).toEqual({ ok: true, estado: 200, mensaje: 'Tu correo quedó verificado.' });
    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/auth/verificacion/confirmacion');
    expect(url).toBe(RUTAS_DE_VERIFICACION.confirmacion);
    expect(opciones.method).toBe('POST');
    expect(opciones.headers.Accept).toContain('application/problem+json');
    expect(opciones.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(opciones.body)).toEqual({ email: 'ana@nexus.test', codigo: 'K7QX2M9P' });
    // Ruta pública: no viaja el token de ninguna sesión.
    expect(opciones.headers.Authorization).toBeUndefined();
  });

  test('un rechazo vuelve con su motivo y su texto, sin lanzar', async () => {
    const fetchImpl = jest.fn(() =>
      respuesta(400, {
        type: `${ERRORES}codigo-invalido`,
        title: 'Código inválido',
        status: 400,
        detail: 'El código no es válido.',
      }),
    );

    const resultado = await confirmarCorreo({ email: 'a@b.test', codigo: 'X' }, { fetchImpl });

    expect(resultado).toEqual({
      ok: false,
      estado: 400,
      tipo: 'codigo-invalido',
      detalle: 'El código no es válido.',
    });
  });

  test('cinco fallos anulan el código: 429 demasiados-intentos', async () => {
    const fetchImpl = jest.fn(() =>
      respuesta(429, { type: `${ERRORES}demasiados-intentos`, status: 429 }),
    );
    const resultado = await confirmarCorreo({ email: 'a@b.test', codigo: 'X' }, { fetchImpl });
    expect(resultado.tipo).toBe('demasiados-intentos');
    expect(resultado.estado).toBe(429);
  });

  test('un fallo de red se propaga: la vista no sabe si llegó', async () => {
    const fetchImpl = jest.fn(() => Promise.reject(new TypeError('red')));
    await expect(
      confirmarCorreo({ email: 'a@b.test', codigo: 'K7QX2M9P' }, { fetchImpl }),
    ).rejects.toThrow('red');
  });
});

describe('reenviarCodigo', () => {
  test('POST a la ruta del contrato solo con el correo', async () => {
    const fetchImpl = jest.fn(() => respuesta(202, { mensaje: 'Texto del servidor' }));

    const resultado = await reenviarCodigo('ana@nexus.test', { fetchImpl });

    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/auth/verificacion/reenvio');
    expect(JSON.parse(opciones.body)).toEqual({ email: 'ana@nexus.test' });
    // El mensaje es siempre el mismo, lo diga como lo diga el servidor.
    expect(resultado).toEqual({ ok: true, estado: 202, mensaje: MENSAJE_DE_REENVIO });
  });

  test('exista o no la cuenta, lo que se enseña es idéntico', async () => {
    const existe = await reenviarCodigo('existe@nexus.test', {
      fetchImpl: () => respuesta(202, { mensaje: 'a' }),
    });
    const noExiste = await reenviarCodigo('no.existe@nexus.test', {
      fetchImpl: () => respuesta(202, { mensaje: 'b' }),
    });
    expect(existe.mensaje).toBe(noExiste.mensaje);
  });

  test('el límite del borde (429) vuelve como tal', async () => {
    const resultado = await reenviarCodigo('ana@nexus.test', {
      fetchImpl: () => respuesta(429, ''),
    });
    expect(resultado.ok).toBe(false);
    expect(resultado.estado).toBe(429);
    expect(resultado.tipo).toBeNull();
  });
});

describe('espera entre reenvíos', () => {
  const AHORA = 1_790_000_000_000;

  test('sin ningún envío anotado se puede pedir ya', () => {
    expect(segundosParaReenviar(sessionStorage, AHORA)).toBe(0);
  });

  test('cuenta hacia atrás desde el último envío, y llega a cero', () => {
    anotarEnvio(sessionStorage, AHORA);
    expect(sessionStorage.getItem(CLAVES_DEL_CORREO.ultimoEnvio)).toBe(String(AHORA));
    expect(segundosParaReenviar(sessionStorage, AHORA)).toBe(60);
    expect(segundosParaReenviar(sessionStorage, AHORA + 1_000)).toBe(59);
    expect(segundosParaReenviar(sessionStorage, AHORA + 59_001)).toBe(1);
    expect(segundosParaReenviar(sessionStorage, AHORA + ESPERA_ENTRE_REENVIOS_MS)).toBe(0);
  });

  test('un reloj que se movió hacia atrás no deja el botón bloqueado más de la espera', () => {
    anotarEnvio(sessionStorage, AHORA + 10 * 60_000);
    expect(segundosParaReenviar(sessionStorage, AHORA)).toBe(60);
  });

  test('una marca ilegible no bloquea nada', () => {
    sessionStorage.setItem(CLAVES_DEL_CORREO.ultimoEnvio, 'no-es-un-numero');
    expect(segundosParaReenviar(sessionStorage, AHORA)).toBe(0);
  });
});
