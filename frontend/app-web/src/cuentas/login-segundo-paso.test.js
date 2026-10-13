/**
 * Segundo paso del login — HU-AUT-007 (ms-identidad-auth 2.2.0).
 *
 * Sobre las secciones reales de `login.html`, con el cliente inyectado. Lo que
 * se prueba: qué 403 es un desafío y cuál sigue siendo un rechazo; que el
 * primer paso se aparte y vuelva tal como estaba; el código de la aplicación,
 * el de recuperación (y cuántos quedan), los rechazos en su sitio, la vuelta
 * al primer paso cuando el desafío ya no vale; y el enrolamiento obligatorio
 * con los códigos de recuperación antes de entrar. El desafío nunca sale de
 * la memoria de la página.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

import {
  avisoTrasRecuperacion,
  desafioDelRechazo,
  montarSegundoPaso,
} from './login-segundo-paso.js';
import { FalloDelSegundoFactor, PROBLEMAS_DEL_SEGUNDO_FACTOR } from './segundo-factor.js';

const MARCADO = readFileSync(new URL('./login.html', import.meta.url), 'utf8');
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
const DESAFIO = 'kP3v-9xQ_desafio-opaco-de-prueba';
const SESION = { token: 'h.p.f', apodo: 'ana', rol: 'ROLE_ADMIN', uid: 'u-1' };
const ENROLAMIENTO = {
  secreto: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
  uriOtpauth:
    'otpauth://totp/Nexus%20Battles%20VI:ana%40nexus.test?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=Nexus%20Battles%20VI&algorithm=SHA1&digits=6&period=30',
  emisor: 'Nexus Battles VI',
  cuenta: 'ana@nexus.test',
  algoritmo: 'SHA1',
  digitos: 6,
  periodoSegundos: 30,
};
const CODIGOS = ['K7QX2-M9PRT', '4HNZW-8CVBE'];

const tick = () => new Promise((resolver) => setTimeout(resolver, 0));
const $ = (selector) => document.querySelector(selector);
const seccion = () => $('[data-zona="segundo-paso"]');
const obligatorio = () => $('[data-zona="enrolamiento-obligatorio"]');
const enviar = (formulario) => formulario.dispatchEvent(new Event('submit', { cancelable: true }));
const fallo = (estado, tipo, detalle = null) =>
  new FalloDelSegundoFactor({ estado, tipo, detalle });

function montar(dobles = {}) {
  const cliente = {
    alEntrar: jest.fn(),
    alVolver: jest.fn(),
    canjear: jest.fn(() => Promise.resolve(SESION)),
    enrolar: jest.fn(() => Promise.resolve(ENROLAMIENTO)),
    activar: jest.fn(() => Promise.resolve({ ...SESION, codigosRecuperacion: CODIGOS })),
    copiar: jest.fn(() => Promise.resolve(true)),
    descargar: jest.fn(),
    ...dobles,
  };
  const paso = montarSegundoPaso(document, cliente);
  return { paso, ...cliente };
}

beforeEach(() => {
  document.documentElement.innerHTML = MARCADO.replace(/<script[\s\S]*?<\/script>/g, '');
  globalThis.localStorage?.clear();
  globalThis.sessionStorage?.clear();
});

afterEach(() => {
  // El desafío no sale de la memoria de la página.
  expect(globalThis.localStorage.length).toBe(0);
  expect(globalThis.sessionStorage.length).toBe(0);
  expect(globalThis.location.href).not.toContain('desafio');
});

// ------------------------------------------------------------ qué es un desafío

describe('qué 403 es un desafío', () => {
  test('segundo factor requerido, con su desafío', () => {
    expect(
      desafioDelRechazo(403, {
        type: `${ERRORES}segundo-factor-requerido`,
        status: 403,
        desafio: DESAFIO,
        expiraEn: '2026-10-05T15:05:00Z',
      }),
    ).toEqual({
      tipo: PROBLEMAS_DEL_SEGUNDO_FACTOR.REQUERIDO,
      desafio: DESAFIO,
      expiraEn: '2026-10-05T15:05:00Z',
    });
    expect(
      desafioDelRechazo(403, {
        type: `${ERRORES}segundo-factor-enrolamiento-requerido`,
        desafio: DESAFIO,
      }).tipo,
    ).toBe(PROBLEMAS_DEL_SEGUNDO_FACTOR.ENROLAMIENTO_REQUERIDO);
  });

  test('un 403 de cuenta suspendida, uno sin desafío o un 401 siguen siendo rechazos', () => {
    expect(desafioDelRechazo(403, { type: `${ERRORES}cuenta-suspendida` })).toBeNull();
    expect(desafioDelRechazo(403, { type: `${ERRORES}segundo-factor-requerido` })).toBeNull();
    expect(
      desafioDelRechazo(401, { type: `${ERRORES}segundo-factor-requerido`, desafio: DESAFIO }),
    ).toBeNull();
    expect(desafioDelRechazo(403, 'Forbidden')).toBeNull();
    expect(desafioDelRechazo(403, null)).toBeNull();
  });

  test('tras un código de recuperación se dice cuántos quedan', () => {
    expect(avisoTrasRecuperacion(5).detalle).toMatch(/Te quedan 5\./);
    expect(avisoTrasRecuperacion(5).tono).toBe('info');
    expect(avisoTrasRecuperacion(1).detalle).toMatch(/Te queda 1\./);
    expect(avisoTrasRecuperacion(0).detalle).toMatch(/Ya no te queda ninguno\./);
    expect(avisoTrasRecuperacion(0).tono).toBe('advertencia');
  });
});

// ------------------------------------------------------------------ verificar

describe('el código en el acceso', () => {
  test('aparta el primer paso y pide el código con los atributos del teléfono', () => {
    const { paso } = montar();
    expect(seccion().hidden).toBe(true);

    paso.verificar({ desafio: DESAFIO });

    expect(seccion().hidden).toBe(false);
    expect($('#formLogin').hidden).toBe(true);
    expect($('.entrada__pie').hidden).toBe(true);
    const campo = $('#codigoSegundoPaso');
    expect(campo.getAttribute('autocomplete')).toBe('one-time-code');
    expect(campo.getAttribute('inputmode')).toBe('numeric');
    expect(campo.getAttribute('name')).toBe('codigo');
    expect(document.activeElement).toBe(campo);
  });

  test('con el código de la aplicación canjea el desafío y entra', async () => {
    const { paso, canjear, alEntrar } = montar();
    paso.verificar({ desafio: DESAFIO });
    $('#codigoSegundoPaso').value = '28708';
    enviar($('#formSegundoPaso'));
    await tick();
    expect(canjear).not.toHaveBeenCalled();
    expect($('#codigoSegundoPaso').getAttribute('aria-invalid')).toBe('true');

    $('#codigoSegundoPaso').value = '287 082';
    enviar($('#formSegundoPaso'));
    await tick();
    expect(canjear).toHaveBeenCalledWith({ desafio: DESAFIO, codigo: '287 082' });
    expect(alEntrar).toHaveBeenCalledWith(SESION);
  });

  test('dos envíos seguidos son un solo canje', async () => {
    let soltar;
    const { paso, canjear } = montar({
      canjear: jest.fn(
        () =>
          new Promise((resolver) => {
            soltar = resolver;
          }),
      ),
    });
    paso.verificar({ desafio: DESAFIO });
    $('#codigoSegundoPaso').value = '287082';
    enviar($('#formSegundoPaso'));
    enviar($('#formSegundoPaso'));
    await tick();
    expect(canjear).toHaveBeenCalledTimes(1);
    expect($('[data-accion="verificar-segundo-paso"]').disabled).toBe(true);
    soltar(SESION);
    await tick();
  });

  test('«Usar un código de recuperación»: lo canjea y dice cuántos quedan antes de entrar', async () => {
    const { paso, canjear, alEntrar } = montar({
      canjear: jest.fn(() => Promise.resolve({ ...SESION, codigosRecuperacionRestantes: 2 })),
    });
    paso.verificar({ desafio: DESAFIO });
    const alternar = $('[data-accion="usar-recuperacion"]');
    alternar.click();
    expect($('[data-modo="recuperacion"]').hidden).toBe(false);
    expect($('[data-modo="aplicacion"]').hidden).toBe(true);
    expect(alternar.textContent).toBe('Usar el código de mi aplicación');
    expect(document.activeElement).toBe($('#codigoRecuperacionSegundoPaso'));

    $('#codigoRecuperacionSegundoPaso').value = 'K7QX2';
    enviar($('#formSegundoPaso'));
    await tick();
    expect(canjear).not.toHaveBeenCalled();

    $('#codigoRecuperacionSegundoPaso').value = 'k7qx2-m9prt';
    enviar($('#formSegundoPaso'));
    await tick();
    expect(canjear).toHaveBeenCalledWith({ desafio: DESAFIO, codigoRecuperacion: 'k7qx2-m9prt' });

    const aviso = $('[data-zona="rechazo-segundo-paso"]');
    expect(aviso.textContent).toMatch(/Entraste con un código de recuperación/);
    expect(aviso.textContent).toMatch(/Te quedan 2\./);
    expect($('#formSegundoPaso').hidden).toBe(true);
    expect(alEntrar).not.toHaveBeenCalled();

    $('[data-accion="continuar-tras-recuperacion"]').click();
    await tick();
    expect(alEntrar).toHaveBeenCalledWith({ ...SESION, codigosRecuperacionRestantes: 2 });
  });

  test('un código incorrecto se dice en su sitio y se puede reintentar', async () => {
    const canjear = jest
      .fn()
      .mockImplementationOnce(() =>
        Promise.reject(fallo(401, PROBLEMAS_DEL_SEGUNDO_FACTOR.CODIGO_INVALIDO)),
      )
      .mockImplementationOnce(() => Promise.resolve(SESION));
    const { paso, alEntrar, alVolver } = montar({ canjear });
    paso.verificar({ desafio: DESAFIO });
    $('#codigoSegundoPaso').value = '000000';
    enviar($('#formSegundoPaso'));
    await tick();

    const aviso = $('[data-zona="rechazo-segundo-paso"]');
    expect(aviso.textContent).toMatch(/El código no es válido/);
    expect(aviso.textContent).toMatch(/bloquea/);
    expect($('#codigoSegundoPaso').value).toBe('');
    expect($('#codigoSegundoPaso').getAttribute('aria-invalid')).toBe('true');
    expect(alVolver).not.toHaveBeenCalled();

    $('#codigoSegundoPaso').value = '287082';
    enviar($('#formSegundoPaso'));
    await tick();
    expect(alEntrar).toHaveBeenCalledWith(SESION);
  });

  test('desafío caducado: vuelve al primer paso con el motivo y olvida el desafío', async () => {
    const { paso, canjear, alVolver } = montar({
      canjear: jest.fn(() =>
        Promise.reject(fallo(401, PROBLEMAS_DEL_SEGUNDO_FACTOR.DESAFIO_INVALIDO)),
      ),
    });
    paso.verificar({ desafio: DESAFIO });
    $('#codigoSegundoPaso').value = '287082';
    enviar($('#formSegundoPaso'));
    await tick();

    expect(alVolver).toHaveBeenCalledTimes(1);
    expect(alVolver.mock.calls[0][0].titulo).toBe('Tu verificación caducó');
    expect(seccion().hidden).toBe(true);
    expect($('#formLogin').hidden).toBe(false);

    // Sin desafío ya no se canjea nada.
    enviar($('#formSegundoPaso'));
    await tick();
    expect(canjear).toHaveBeenCalledTimes(1);
  });

  test('una sanción entre los dos pasos (403) también devuelve al primero, con el motivo del servidor', async () => {
    const { paso, alVolver } = montar({
      canjear: jest.fn(() =>
        Promise.reject(
          fallo(403, 'cuenta-suspendida', 'Cuenta suspendida. Tiempo restante: 60 minutos.'),
        ),
      ),
    });
    paso.verificar({ desafio: DESAFIO });
    $('#codigoSegundoPaso').value = '287082';
    enviar($('#formSegundoPaso'));
    await tick();
    expect(alVolver.mock.calls[0][0]).toMatchObject({
      titulo: 'Esta cuenta no puede entrar ahora',
      detalle: 'Cuenta suspendida. Tiempo restante: 60 minutos.',
    });
  });

  test('«Volver a empezar» deja el primer paso como estaba', () => {
    const { paso, alVolver } = montar();
    $('#avisoMotivo').hidden = false;
    paso.verificar({ desafio: DESAFIO });
    expect($('#avisoMotivo').hidden).toBe(true);

    seccion().querySelector('[data-accion="volver-al-primer-paso"]').click();

    expect(alVolver).toHaveBeenCalledWith(null);
    expect(seccion().hidden).toBe(true);
    expect($('#formLogin').hidden).toBe(false);
    expect($('#avisoMotivo').hidden).toBe(false);
    expect($('#estadoLogin').hidden).toBe(true);
    expect($('[data-zona="rechazo"]').hidden).toBe(true);
  });
});

// --------------------------------------------------- enrolamiento obligatorio

describe('enrolamiento obligatorio', () => {
  test('clave → código → códigos de recuperación → entra solo al confirmar que los guardó', async () => {
    const { paso, enrolar, activar, alEntrar, descargar } = montar();
    paso.enrolar({ desafio: DESAFIO });
    expect(obligatorio().hidden).toBe(false);
    expect(seccion().hidden).toBe(true);
    expect($('#formLogin').hidden).toBe(true);
    expect(document.activeElement).toBe($('#tituloEnrolamientoObligatorio'));

    $('[data-accion="generar-clave"]').click();
    await tick();
    expect(enrolar).toHaveBeenCalledWith(DESAFIO);
    expect(obligatorio().querySelector('[data-zona="secreto"]').textContent).toBe(
      'JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP',
    );
    expect(obligatorio().querySelector('[data-zona="enlace-otpauth"]').getAttribute('href')).toBe(
      ENROLAMIENTO.uriOtpauth,
    );
    expect($('[data-zona="inicio-enrolamiento"]').hidden).toBe(true);
    const formulario = $('#formEnrolamientoObligatorio');
    expect(formulario.hidden).toBe(false);

    $('#codigoEnrolamiento').value = '123 456';
    enviar(formulario);
    await tick();
    expect(activar).toHaveBeenCalledWith({ desafio: DESAFIO, codigo: '123 456' });

    expect(obligatorio().textContent).not.toContain('JBSW');
    expect(formulario.hidden).toBe(true);
    const mostrados = [...obligatorio().querySelectorAll('[data-zona="codigo-recuperacion"]')].map(
      (n) => n.textContent,
    );
    expect(mostrados).toEqual(CODIGOS);
    $('[data-accion="descargar-codigos"]').click();
    expect(descargar.mock.calls[0][0]).toContain('ana@nexus.test');
    expect(alEntrar).not.toHaveBeenCalled();

    const seguir = $('[data-accion="codigos-guardados"]');
    expect(seguir.textContent).toBe('Ya los guardé, entrar');
    seguir.click();
    expect(alEntrar).toHaveBeenCalledWith({ ...SESION, codigosRecuperacion: CODIGOS });
  });

  test('un código incorrecto al activar se marca y la clave sigue en pantalla', async () => {
    const { paso } = montar({
      activar: jest.fn(() =>
        Promise.reject(fallo(422, PROBLEMAS_DEL_SEGUNDO_FACTOR.CODIGO_INVALIDO)),
      ),
    });
    paso.enrolar({ desafio: DESAFIO });
    $('[data-accion="generar-clave"]').click();
    await tick();
    $('#codigoEnrolamiento').value = '000000';
    enviar($('#formEnrolamientoObligatorio'));
    await tick();
    expect($('[data-zona="rechazo-enrolamiento"]').textContent).toMatch(/hora de tu teléfono/);
    expect($('#codigoEnrolamiento').value).toBe('');
    expect($('#codigoEnrolamiento').getAttribute('aria-invalid')).toBe('true');
    expect(obligatorio().querySelector('[data-zona="secreto"]')).not.toBeNull();
  });

  test('sin clave de cifrado en el servidor lo dice y no enseña nada', async () => {
    const { paso } = montar({
      enrolar: jest.fn(() =>
        Promise.reject(fallo(503, PROBLEMAS_DEL_SEGUNDO_FACTOR.NO_DISPONIBLE)),
      ),
    });
    paso.enrolar({ desafio: DESAFIO });
    $('[data-accion="generar-clave"]').click();
    await tick();
    expect($('[data-zona="rechazo-enrolamiento"]').textContent).toMatch(
      /no está disponible ahora mismo/,
    );
    expect($('#formEnrolamientoObligatorio').hidden).toBe(true);
    expect(obligatorio().querySelector('[data-zona="secreto"]')).toBeNull();
  });

  test('con el desafío caducado vuelve al primer paso con el motivo', async () => {
    const { paso, alVolver } = montar({
      enrolar: jest.fn(() =>
        Promise.reject(fallo(401, PROBLEMAS_DEL_SEGUNDO_FACTOR.DESAFIO_INVALIDO)),
      ),
    });
    paso.enrolar({ desafio: DESAFIO });
    $('[data-accion="generar-clave"]').click();
    await tick();
    expect(alVolver.mock.calls[0][0].titulo).toBe('Tu verificación caducó');
    expect(obligatorio().hidden).toBe(true);
    expect($('#formLogin').hidden).toBe(false);
  });
});

// ------------------------------------------------------------------ formularios

test('G1 — los dos formularios van por POST y el guion enciende sus botones', () => {
  montar();
  for (const id of ['formSegundoPaso', 'formEnrolamientoObligatorio']) {
    const formulario = document.getElementById(id);
    expect(formulario.getAttribute('method')).toBe('post');
    expect(formulario.hasAttribute('data-listo')).toBe(true);
    const boton = formulario.querySelector('button[type="submit"]');
    expect(boton.disabled).toBe(false);
    expect(boton.hasAttribute('data-espera-guion')).toBe(false);
  }
});

test('sin las secciones en la vista, no monta nada', () => {
  document.body.replaceChildren();
  expect(montarSegundoPaso(document, { alEntrar: () => {} })).toBeNull();
});
