/**
 * «Verificación en dos pasos» en Mi cuenta > Seguridad — HU-AUT-007.
 *
 * Se monta sobre la sección real de `perfil.html`, con el cliente inyectado.
 * Lo que se prueba: que diga el estado (y si el rol la exige o quedan pocos
 * códigos), el recorrido de activar (clave → código → códigos de
 * recuperación una sola vez), el de desactivar (contraseña + código), cada
 * rechazo en su campo, y que la clave y los códigos no se queden ni en la
 * pantalla ni en el navegador.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

import { FalloDelSegundoFactor, PROBLEMAS_DEL_SEGUNDO_FACTOR } from './segundo-factor.js';
import {
  POCOS_CODIGOS,
  describirEstado,
  montarSegundoFactor,
  motivoDelCodigoParaDesactivar,
} from './segundo-factor-cuenta.js';

const MARCADO = readFileSync(new URL('./perfil.html', import.meta.url), 'utf8');

const INACTIVO = {
  activo: false,
  obligatorio: false,
  disponible: true,
  enrolamientoPendiente: false,
  activadoEn: null,
  codigosRecuperacionRestantes: null,
};
const ACTIVO = {
  activo: true,
  obligatorio: false,
  disponible: true,
  enrolamientoPendiente: false,
  activadoEn: '2026-10-05T15:00:00Z',
  codigosRecuperacionRestantes: 10,
};
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
const CODIGOS = ['K7QX2-M9PRT', '4HNZW-8CVBE', 'ABCDE-FGHJK'];

const tick = () => new Promise((resolver) => setTimeout(resolver, 0));
const seccion = () => document.querySelector('[data-zona="segundo-factor"]');
const en = (selector) => seccion().querySelector(selector);
const zona = (nombre) => en(`[data-zona="${nombre}"]`);
const accion = (nombre) => en(`[data-accion="${nombre}"]`);
const aviso = () => zona('aviso-segundo-factor');
const enviar = (formulario) => formulario.dispatchEvent(new Event('submit', { cancelable: true }));
const fallo = (estado, tipo) => new FalloDelSegundoFactor({ estado, tipo, detalle: null });

async function montar(dobles = {}) {
  const cliente = {
    consultar: jest.fn(() => Promise.resolve(INACTIVO)),
    enrolar: jest.fn(() => Promise.resolve(ENROLAMIENTO)),
    activar: jest.fn(() => Promise.resolve({ activo: true, codigosRecuperacion: CODIGOS })),
    desactivar: jest.fn(() => Promise.resolve(null)),
    copiar: jest.fn(() => Promise.resolve(true)),
    descargar: jest.fn(),
    ...dobles,
  };
  const vista = montarSegundoFactor(document, cliente);
  await vista.cargado;
  return cliente;
}

beforeEach(() => {
  document.documentElement.innerHTML = MARCADO.replace(/<script[\s\S]*?<\/script>/g, '');
  globalThis.localStorage?.clear();
  globalThis.sessionStorage?.clear();
});

// ------------------------------------------------------------------ estado

describe('qué dice del estado', () => {
  test('sin segundo factor: lo explica y ofrece activarlo', async () => {
    await montar();
    expect(zona('estado-segundo-factor').textContent).toMatch(/^Desactivada\./);
    expect(accion('activar-segundo-factor').hidden).toBe(false);
    expect(accion('desactivar-segundo-factor').hidden).toBe(true);
    expect(aviso().hidden).toBe(true);
  });

  test('si el rol la exige y no está activa, lo advierte', async () => {
    await montar({ consultar: jest.fn(() => Promise.resolve({ ...INACTIVO, obligatorio: true })) });
    expect(aviso().textContent).toMatch(/Tu rol la exige/);
    expect(aviso().querySelector('.aviso--advertencia')).not.toBeNull();
  });

  test('si el servicio no puede activarla, no ofrece un botón que va a fallar', async () => {
    await montar({ consultar: jest.fn(() => Promise.resolve({ ...INACTIVO, disponible: false })) });
    expect(accion('activar-segundo-factor').hidden).toBe(true);
    expect(aviso().textContent).toMatch(/no está disponible/);
  });

  test('activa: desde cuándo y cuántos códigos de recuperación quedan', async () => {
    await montar({ consultar: jest.fn(() => Promise.resolve(ACTIVO)) });
    const texto = zona('estado-segundo-factor').textContent;
    expect(texto).toMatch(/^Activada desde el /);
    expect(texto).toMatch(/Te quedan 10 códigos de recuperación\./);
    expect(accion('desactivar-segundo-factor').hidden).toBe(false);
    expect(accion('activar-segundo-factor').hidden).toBe(true);
  });

  test(`con menos de ${POCOS_CODIGOS} códigos, avisa de que conviene renovarlos`, () => {
    const estado = describirEstado({ ...ACTIVO, codigosRecuperacionRestantes: 1 });
    expect(estado.texto).toMatch(/Te queda 1 código de recuperación\./);
    expect(estado.nota.titulo).toBe('Te quedan pocos códigos de recuperación');
    expect(describirEstado({ ...ACTIVO, obligatorio: true }).nota.detalle).toMatch(
      /te pediremos activarla de nuevo/,
    );
  });

  test('si la consulta falla, lo dice y deja reintentar', async () => {
    const consultar = jest
      .fn()
      .mockImplementationOnce(() => Promise.reject(new TypeError('fallo de red')))
      .mockImplementationOnce(() => Promise.resolve(INACTIVO));
    await montar({ consultar });
    expect(aviso().textContent).toMatch(/No pudimos consultar/);
    expect(accion('reintentar-segundo-factor').hidden).toBe(false);

    accion('reintentar-segundo-factor').click();
    await tick();
    expect(consultar).toHaveBeenCalledTimes(2);
    expect(accion('activar-segundo-factor').hidden).toBe(false);
  });
});

// ------------------------------------------------------------------ activar

describe('activar', () => {
  test('clave para la aplicación → código → códigos de recuperación una vez → estado activo', async () => {
    const cliente = await montar();
    cliente.consultar.mockImplementation(() => Promise.resolve(ACTIVO));

    accion('activar-segundo-factor').click();
    await tick();
    expect(cliente.enrolar).toHaveBeenCalledTimes(1);
    expect(zona('clave-segundo-factor').hidden).toBe(false);
    expect(zona('enlace-otpauth').getAttribute('href')).toBe(ENROLAMIENTO.uriOtpauth);
    expect(zona('secreto').textContent).toBe('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP');
    const formulario = zona('formulario-activacion');
    expect(formulario.hidden).toBe(false);
    const campo = formulario.querySelector('[name="codigo"]');
    expect(campo.getAttribute('autocomplete')).toBe('one-time-code');
    expect(campo.getAttribute('inputmode')).toBe('numeric');
    expect(document.activeElement).toBe(campo);

    // Lo que no tiene forma de código no se envía.
    campo.value = '28708';
    enviar(formulario);
    await tick();
    expect(cliente.activar).not.toHaveBeenCalled();
    expect(campo.getAttribute('aria-invalid')).toBe('true');

    campo.value = '287 082';
    enviar(formulario);
    await tick();
    expect(cliente.activar).toHaveBeenCalledWith('287 082');

    // La clave ya no está en la pantalla; los códigos, sí, una vez.
    expect(zona('clave-segundo-factor').hidden).toBe(true);
    expect(seccion().textContent).not.toContain('JBSW');
    expect(formulario.hidden).toBe(true);
    expect(campo.value).toBe('');
    const mostrados = [...seccion().querySelectorAll('[data-zona="codigo-recuperacion"]')].map(
      (n) => n.textContent,
    );
    expect(mostrados).toEqual(CODIGOS);

    accion('descargar-codigos').click();
    expect(cliente.descargar.mock.calls[0][0]).toContain('ana@nexus.test');

    accion('codigos-guardados').click();
    await tick();
    await tick();
    expect(seccion().querySelectorAll('[data-zona="codigo-recuperacion"]')).toHaveLength(0);
    expect(zona('codigos-segundo-factor').hidden).toBe(true);
    expect(zona('estado-segundo-factor').textContent).toMatch(/^Activada desde el /);
    expect(aviso().textContent).toMatch(/está activa/);

    // Nada de esto quedó guardado en el navegador.
    expect(globalThis.localStorage.length).toBe(0);
    expect(globalThis.sessionStorage.length).toBe(0);
  });

  test('un código incorrecto se marca en su campo y la clave sigue para reintentar', async () => {
    const cliente = await montar({
      activar: jest.fn(() =>
        Promise.reject(fallo(422, PROBLEMAS_DEL_SEGUNDO_FACTOR.CODIGO_INVALIDO)),
      ),
    });
    accion('activar-segundo-factor').click();
    await tick();
    const formulario = zona('formulario-activacion');
    const campo = formulario.querySelector('[name="codigo"]');
    campo.value = '000000';
    enviar(formulario);
    await tick();

    expect(cliente.activar).toHaveBeenCalledTimes(1);
    expect(aviso().textContent).toMatch(/El código no es válido/);
    expect(aviso().textContent).toMatch(/hora de tu teléfono/);
    expect(campo.value).toBe('');
    expect(campo.getAttribute('aria-invalid')).toBe('true');
    expect(zona('secreto').textContent).toContain('JBSW');
    expect(formulario.hidden).toBe(false);
  });

  test('si otra pestaña cambió la activación, la clave de esta pantalla se retira', async () => {
    const cliente = await montar({
      activar: jest.fn(() =>
        Promise.reject(fallo(409, PROBLEMAS_DEL_SEGUNDO_FACTOR.SIN_ENROLAMIENTO)),
      ),
    });
    accion('activar-segundo-factor').click();
    await tick();
    const formulario = zona('formulario-activacion');
    formulario.querySelector('[name="codigo"]').value = '287082';
    enviar(formulario);
    await tick();
    await tick();

    expect(zona('clave-segundo-factor').hidden).toBe(true);
    expect(seccion().textContent).not.toContain('JBSW');
    expect(formulario.hidden).toBe(true);
    expect(cliente.consultar).toHaveBeenCalledTimes(2);
    expect(aviso().textContent).toMatch(/Primero genera la clave/);
  });

  test('sin clave de cifrado en el servidor (503), lo explica sin enseñar nada', async () => {
    await montar({
      enrolar: jest.fn(() =>
        Promise.reject(fallo(503, PROBLEMAS_DEL_SEGUNDO_FACTOR.NO_DISPONIBLE)),
      ),
    });
    accion('activar-segundo-factor').click();
    await tick();
    expect(aviso().textContent).toMatch(/no está disponible ahora mismo/);
    expect(zona('clave-segundo-factor').hidden).toBe(true);
    expect(zona('formulario-activacion').hidden).toBe(true);
  });

  test('cancelar retira la clave de la pantalla y vuelve al estado', async () => {
    const cliente = await montar();
    accion('activar-segundo-factor').click();
    await tick();
    accion('cancelar-activacion').click();
    await tick();
    expect(seccion().textContent).not.toContain('JBSW');
    expect(zona('formulario-activacion').hidden).toBe(true);
    expect(cliente.consultar).toHaveBeenCalledTimes(2);
    expect(accion('activar-segundo-factor').hidden).toBe(false);
  });
});

// --------------------------------------------------------------- desactivar

describe('desactivar', () => {
  function abrir() {
    accion('desactivar-segundo-factor').click();
    const formulario = zona('formulario-desactivacion');
    return {
      formulario,
      clave: formulario.querySelector('[name="passwordActualSegundoFactor"]'),
      codigo: formulario.querySelector('[name="codigo"]'),
    };
  }

  test('pide la contraseña y un código; sin ellos no envía', async () => {
    const cliente = await montar({ consultar: jest.fn(() => Promise.resolve(ACTIVO)) });
    const { formulario, clave, codigo } = abrir();
    expect(formulario.hidden).toBe(false);
    expect(document.activeElement).toBe(clave);

    enviar(formulario);
    await tick();
    expect(cliente.desactivar).not.toHaveBeenCalled();
    expect(clave.getAttribute('aria-invalid')).toBe('true');
    expect(codigo.getAttribute('aria-invalid')).toBe('true');
  });

  test('con contraseña y código de la aplicación (o uno de recuperación) la quita', async () => {
    const cliente = await montar({ consultar: jest.fn(() => Promise.resolve(ACTIVO)) });
    const { formulario, clave, codigo } = abrir();
    clave.value = 'Clave-Actual-2026';
    codigo.value = 'k7qx2-m9prt';
    cliente.consultar.mockImplementation(() => Promise.resolve(INACTIVO));
    enviar(formulario);
    await tick();
    await tick();

    expect(cliente.desactivar).toHaveBeenCalledWith({
      passwordActual: 'Clave-Actual-2026',
      codigo: 'k7qx2-m9prt',
    });
    expect(formulario.hidden).toBe(true);
    expect(clave.value).toBe('');
    expect(codigo.value).toBe('');
    expect(aviso().textContent).toMatch(/quedó desactivada/);
    expect(accion('activar-segundo-factor').hidden).toBe(false);
  });

  test('contraseña incorrecta: se borra y se marca su campo', async () => {
    await montar({
      consultar: jest.fn(() => Promise.resolve(ACTIVO)),
      desactivar: jest.fn(() =>
        Promise.reject(fallo(422, PROBLEMAS_DEL_SEGUNDO_FACTOR.CONTRASENA_INCORRECTA)),
      ),
    });
    const { formulario, clave, codigo } = abrir();
    clave.value = 'Equivocada-1';
    codigo.value = '287082';
    enviar(formulario);
    await tick();
    expect(clave.value).toBe('');
    expect(clave.getAttribute('aria-invalid')).toBe('true');
    expect(codigo.value).toBe('287082');
    expect(aviso().textContent).toMatch(/La contraseña actual es incorrecta/);
  });

  test('código incorrecto: se borra y se marca su campo', async () => {
    await montar({
      consultar: jest.fn(() => Promise.resolve(ACTIVO)),
      desactivar: jest.fn(() =>
        Promise.reject(fallo(422, PROBLEMAS_DEL_SEGUNDO_FACTOR.CODIGO_INVALIDO)),
      ),
    });
    const { formulario, clave, codigo } = abrir();
    clave.value = 'Clave-Actual-2026';
    codigo.value = '000000';
    enviar(formulario);
    await tick();
    expect(codigo.value).toBe('');
    expect(codigo.getAttribute('aria-invalid')).toBe('true');
    expect(aviso().textContent).toMatch(/El código no es válido/);
  });

  test('qué código se acepta para desactivar', () => {
    expect(motivoDelCodigoParaDesactivar('287 082')).toBeNull();
    expect(motivoDelCodigoParaDesactivar('K7QX2-M9PRT')).toBeNull();
    expect(motivoDelCodigoParaDesactivar('1234')).toMatch(/6 dígitos/);
  });
});

// --------------------------------------------------------------- formularios

test('G1 — los dos formularios van por POST y el guion enciende sus botones', async () => {
  await montar();
  for (const nombre of ['formulario-activacion', 'formulario-desactivacion']) {
    const formulario = zona(nombre);
    expect(formulario.getAttribute('method')).toBe('post');
    expect(formulario.hasAttribute('data-listo')).toBe(true);
    const boton = formulario.querySelector('button[type="submit"]');
    expect(boton.disabled).toBe(false);
    expect(boton.hasAttribute('data-espera-guion')).toBe(false);
  }
});

test('sin la sección en la vista, no monta nada', () => {
  document.body.replaceChildren();
  expect(montarSegundoFactor(document)).toBeNull();
});
