/**
 * HU-AUT-006 — Cambiar mi contraseña desde «Mi Cuenta».
 *
 * Lo que se prueba: que se mande exactamente el cuerpo del contrato a la ruta
 * del contrato; que el token nuevo sustituya al de la sesión (CA-04); que un
 * rechazo se muestre con el motivo que redactó el servidor (CA-03, CA-06);
 * que cancelar no llame a nada y vacíe el formulario (CA-05).
 */

import { jest } from '@jest/globals';

import {
  cambiarPassword,
  montarCambioDePassword,
  validarLocalmente,
  textoDelRechazo,
  ErrorDeCambio,
  CLAVE_TOKEN,
} from './cambiar-password.js';

const VISTA = `
  <section data-zona="cambiar-password">
    <form>
      <input name="passwordActual" type="password" />
      <input name="nuevaPassword" type="password" />
      <input name="confirmacion" type="password" />
      <div data-zona="mensaje-password" hidden></div>
      <button type="button" data-accion="cancelar-password">Cancelar</button>
      <button type="submit" data-accion="guardar-password">Cambiar contraseña</button>
    </form>
  </section>
`;

function respuesta(estado, cuerpo) {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    json: async () => {
      if (cuerpo === undefined) {
        throw new SyntaxError('sin cuerpo');
      }
      return cuerpo;
    },
  };
}

function almacen() {
  const datos = new Map();
  return {
    getItem: (k) => (datos.has(k) ? datos.get(k) : null),
    setItem: (k, v) => datos.set(k, String(v)),
    removeItem: (k) => datos.delete(k),
  };
}

const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

function rellenar(actual, nueva, confirmacion) {
  document.querySelector('[name="passwordActual"]').value = actual;
  document.querySelector('[name="nuevaPassword"]').value = nueva;
  document.querySelector('[name="confirmacion"]').value = confirmacion;
}

function enviar() {
  document.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
}

beforeEach(() => {
  document.body.innerHTML = VISTA;
});

describe('cambiarPassword (cliente)', () => {
  test('hace PUT a /api/v1/auth/password con los tres campos y nada mas', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, { token: 't2', mensaje: 'ok' }));

    const resultado = await cambiarPassword(
      { passwordActual: 'Actual-1!', nuevaPassword: 'Nueva-2!x', confirmacion: 'Nueva-2!x' },
      { fetchImpl },
    );

    expect(resultado).toEqual({ token: 't2', mensaje: 'ok' });
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/auth/password');
    expect(fetchImpl.mock.calls[0][1].method).toBe('PUT');
    expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual({
      passwordActual: 'Actual-1!',
      nuevaPassword: 'Nueva-2!x',
      confirmacion: 'Nueva-2!x',
    });
  });

  test('un rechazo llega como ErrorDeCambio con el type y el detalle del servidor', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(422, {
        type: 'https://nexusbattles.upb.edu.co/errors/contrasena-no-cumple-politica',
        title: 'La contraseña nueva no cumple la política',
        status: 422,
        detail: 'La contraseña nueva debe incluir al menos un número.',
      }),
    );

    const error = await cambiarPassword(
      { passwordActual: 'a', nuevaPassword: 'b', confirmacion: 'b' },
      { fetchImpl },
    ).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeCambio);
    expect(error.estado).toBe(422);
    expect(error.tipo).toBe('https://nexusbattles.upb.edu.co/errors/contrasena-no-cumple-politica');
    expect(error.titulo).toBe('La contraseña nueva no cumple la política.');
    expect(error.detalle).toContain('un número');
  });

  test('sin cuerpo JSON el error sigue siendo utilizable', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(500, undefined));

    const error = await cambiarPassword(
      { passwordActual: 'a', nuevaPassword: 'b', confirmacion: 'b' },
      { fetchImpl },
    ).catch((e) => e);

    expect(error.estado).toBe(500);
    expect(error.titulo).toBe('No se pudo cambiar la contraseña');
  });
});

describe('textoDelRechazo', () => {
  test('junta titulo y detalle cuando aportan cosas distintas, y no repite cuando son lo mismo', () => {
    expect(
      textoDelRechazo({
        titulo: 'La contraseña nueva no cumple la política.',
        detalle: 'La contraseña nueva debe incluir al menos un número.',
      }),
    ).toBe(
      'La contraseña nueva no cumple la política. La contraseña nueva debe incluir al menos un número.',
    );
    expect(
      textoDelRechazo({
        titulo: 'La confirmación no coincide.',
        detalle: 'La confirmación no coincide',
      }),
    ).toBe('La confirmación no coincide.');
    expect(textoDelRechazo(undefined)).toBe('No se pudo cambiar la contraseña.');
  });
});

describe('validarLocalmente', () => {
  test('exige los campos y que la confirmacion coincida; la politica la decide el servidor', () => {
    expect(
      validarLocalmente({ passwordActual: '', nuevaPassword: 'x', confirmacion: 'x' }),
    ).toMatch(/actual/);
    expect(validarLocalmente({ passwordActual: 'a', nuevaPassword: '', confirmacion: '' })).toMatch(
      /nueva/,
    );
    expect(
      validarLocalmente({ passwordActual: 'a', nuevaPassword: 'x', confirmacion: 'y' }),
    ).toMatch(/confirmaci/);
    expect(
      validarLocalmente({ passwordActual: 'a', nuevaPassword: 'corta', confirmacion: 'corta' }),
    ).toBeNull();
  });
});

describe('montarCambioDePassword', () => {
  test('CA-01 + CA-04: al cambiar, guarda el token nuevo, vacia el formulario y confirma', async () => {
    const storage = almacen();
    storage.setItem(CLAVE_TOKEN, 'token-viejo');
    const cambiar = jest
      .fn()
      .mockResolvedValue({ token: 'token-nuevo', mensaje: 'Contraseña actualizada.' });
    montarCambioDePassword(document, { cambiar, storage });
    rellenar('Actual-1!', 'Nueva-2!x', 'Nueva-2!x');

    enviar();
    await tick();

    expect(cambiar).toHaveBeenCalledWith({
      passwordActual: 'Actual-1!',
      nuevaPassword: 'Nueva-2!x',
      confirmacion: 'Nueva-2!x',
    });
    expect(storage.getItem(CLAVE_TOKEN)).toBe('token-nuevo');
    expect(document.querySelector('[name="passwordActual"]').value).toBe('');
    expect(document.querySelector('[name="nuevaPassword"]').value).toBe('');
    const mensaje = document.querySelector('[data-zona="mensaje-password"]');
    expect(mensaje.hidden).toBe(false);
    expect(mensaje.textContent).toBe('Contraseña actualizada.');
    expect(mensaje.dataset.tono).toBe('exito');
  });

  test('CA-03 / CA-06: un rechazo del servidor se muestra con su motivo y el token no cambia', async () => {
    const storage = almacen();
    storage.setItem(CLAVE_TOKEN, 'token-viejo');
    const cambiar = jest.fn().mockRejectedValue(
      new ErrorDeCambio(
        {
          type: 'https://nexusbattles.upb.edu.co/errors/contrasena-actual-incorrecta',
          detail: 'La contraseña actual es incorrecta.',
          status: 422,
        },
        422,
      ),
    );
    montarCambioDePassword(document, { cambiar, storage });
    rellenar('mal', 'Nueva-2!x', 'Nueva-2!x');

    enviar();
    await tick();

    const mensaje = document.querySelector('[data-zona="mensaje-password"]');
    // Título y detalle dicen lo mismo: no se repite.
    expect(mensaje.textContent).toBe('La contraseña actual es incorrecta.');
    expect(mensaje.dataset.tono).toBe('error');
    expect(storage.getItem(CLAVE_TOKEN)).toBe('token-viejo');
    expect(document.querySelector('[data-accion="guardar-password"]').disabled).toBe(false);
  });

  test('una confirmacion distinta no llega al servidor', async () => {
    const cambiar = jest.fn();
    montarCambioDePassword(document, { cambiar, storage: almacen() });
    rellenar('Actual-1!', 'Nueva-2!x', 'otra');

    enviar();
    await tick();

    expect(cambiar).not.toHaveBeenCalled();
    expect(document.querySelector('[data-zona="mensaje-password"]').textContent).toMatch(
      /confirmaci/,
    );
  });

  test('CA-05: cancelar vacia el formulario y no llama a nada', async () => {
    const cambiar = jest.fn();
    montarCambioDePassword(document, { cambiar, storage: almacen() });
    rellenar('Actual-1!', 'Nueva-2!x', 'Nueva-2!x');

    document.querySelector('[data-accion="cancelar-password"]').click();
    await tick();

    expect(cambiar).not.toHaveBeenCalled();
    expect(document.querySelector('[name="passwordActual"]').value).toBe('');
    expect(document.querySelector('[name="confirmacion"]').value).toBe('');
    expect(document.querySelector('[data-zona="mensaje-password"]').hidden).toBe(true);
  });
});
