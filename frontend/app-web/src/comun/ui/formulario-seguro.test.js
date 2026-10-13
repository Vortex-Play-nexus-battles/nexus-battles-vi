/**
 * Formularios con credenciales sin envío nativo — G1 (4-oct).
 *
 * Lo que se fija aquí es el comportamiento del navegador que hace segura la
 * vista ANTES de que llegue su guion (jsdom implementa el envío de
 * formularios: un botón desactivado no envía, ni con clic ni con Enter) y lo
 * que hace `formularioListo()` cuando llega.
 */

import { jest } from '@jest/globals';

import {
  ESPERA_GUION,
  formularioListo,
  hayCredencialesEn,
  sinCredencialesEnLaDireccion,
} from './formulario-seguro.js';

function montar() {
  document.body.innerHTML = `
    <form id="f" method="post" novalidate>
      <input id="email" name="email" type="email" />
      <input id="password" name="password" type="password" />
      <button type="submit" id="enviar" disabled data-espera-guion>Entrar</button>
      <button type="button" id="otro">Otra cosa</button>
    </form>`;
  return {
    formulario: document.getElementById('f'),
    enviar: document.getElementById('enviar'),
    otro: document.getElementById('otro'),
  };
}

describe('antes del guion: el formulario no se puede enviar', () => {
  test('un clic en el botón desactivado no dispara `submit`', () => {
    const { formulario, enviar } = montar();
    const envios = jest.fn((evento) => evento.preventDefault());
    formulario.addEventListener('submit', envios);

    enviar.click();

    expect(envios).not.toHaveBeenCalled();
  });

  // Enter en un campo (el envío implícito) no lo implementa jsdom: lo prueba
  // Chromium de verdad en tests/e2e/login-sin-envio-nativo.e2e.spec.js.
});

describe('formularioListo', () => {
  test('enciende solo los botones marcados y deja la marca de listo', () => {
    const { formulario, enviar, otro } = montar();
    otro.disabled = true;

    formularioListo(formulario);

    expect(enviar.disabled).toBe(false);
    expect(enviar.hasAttribute(ESPERA_GUION)).toBe(false);
    // Un botón que la vista apagó por su cuenta no es de esta función.
    expect(otro.disabled).toBe(true);
    expect(formulario.hasAttribute('data-listo')).toBe(true);
  });

  test('después, el envío llega al manejador de la vista', () => {
    const { formulario, enviar } = montar();
    const envios = jest.fn((evento) => evento.preventDefault());
    formulario.addEventListener('submit', envios);

    formularioListo(formulario);
    enviar.click();

    expect(envios).toHaveBeenCalledTimes(1);
  });

  test('una segunda llamada no vuelve a encender lo que la vista apagó', () => {
    const { formulario, enviar } = montar();
    formularioListo(formulario);
    enviar.disabled = true; // la vista está enviando

    formularioListo(formulario);

    expect(enviar.disabled).toBe(true);
  });

  test('sin formulario no hace nada', () => {
    expect(() => formularioListo(null)).not.toThrow();
    expect(() => formularioListo(undefined)).not.toThrow();
  });
});

describe('hayCredencialesEn', () => {
  test.each([
    ['?email=ana%40nexus.test&password=Secreta-1', true],
    ['?nuevaPassword=x&confirmarPassword=x', true],
    ['?passwordActual=x&confirmacion=x', true],
    ['?passwordActualPreguntas=x&respuestaSeguridad1=perro', true],
    ['?email=ana%40nexus.test&codigo=K7QX2M9P', true],
    ['?respuesta-1=perro', true],
    ['?volver=%2Fjugar&motivo=caducada', false],
    ['?motivo=verificada', false],
    ['', false],
  ])('%s → %s', (busqueda, esperado) => {
    expect(hayCredencialesEn(busqueda)).toBe(esperado);
  });
});

describe('sinCredencialesEnLaDireccion', () => {
  function entorno(pathname, search, hash = '') {
    const historial = { state: { a: 1 }, replaceState: jest.fn() };
    return { ubicacion: { pathname, search, hash }, historial };
  }

  test('quita los campos de credenciales y conserva lo demás', () => {
    const { ubicacion, historial } = entorno(
      '/login',
      '?volver=%2Fjugar&email=ana%40nexus.test&password=Secreta-1',
      '#arriba',
    );

    expect(sinCredencialesEnLaDireccion({ ubicacion, historial })).toBe(true);

    expect(historial.replaceState).toHaveBeenCalledWith(
      { a: 1 },
      '',
      '/login?volver=%2Fjugar#arriba',
    );
  });

  test('si no queda consulta, la dirección queda sin `?`', () => {
    const { ubicacion, historial } = entorno('/login', '?email=a%40b.co&password=x');

    sinCredencialesEnLaDireccion({ ubicacion, historial });

    expect(historial.replaceState).toHaveBeenCalledWith({ a: 1 }, '', '/login');
  });

  test('una dirección limpia no se toca', () => {
    const { ubicacion, historial } = entorno('/login', '?motivo=caducada');

    expect(sinCredencialesEnLaDireccion({ ubicacion, historial })).toBe(false);
    expect(historial.replaceState).not.toHaveBeenCalled();
  });

  test('ni el valor de la contraseña ni su nombre sobreviven', () => {
    const { ubicacion, historial } = entorno(
      '/cuenta',
      '?passwordActual=Vieja-1&nuevaPassword=Nueva-2&confirmacion=Nueva-2',
    );

    sinCredencialesEnLaDireccion({ ubicacion, historial });

    const [, , destino] = historial.replaceState.mock.calls[0];
    expect(destino).toBe('/cuenta');
    expect(destino).not.toMatch(/Vieja|Nueva|password/i);
  });
});
