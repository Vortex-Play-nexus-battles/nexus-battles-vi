/**
 * «Cerrar mi cuenta» — derecho al olvido (HU-PRV-005; perfiles 1.4.0).
 *
 * Se monta sobre la sección real de `perfil.html`. Lo que se prueba: las
 * rutas y el cuerpo del contrato; que la pantalla diga si hay un cierre
 * programado y para cuándo, con el plazo que da el servidor; que solicitar
 * pida la contraseña y una confirmación; que cancelar devuelva la cuenta a
 * activa; y que cada rechazo se explique por su `type` sin dejar la
 * contraseña escrita.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

import {
  ESTADOS_DEL_CIERRE,
  FalloDelCierre,
  PROBLEMAS_DEL_CIERRE,
  cancelarCierre,
  consultarCierre,
  leerEstadoDelCierre,
  montarCierreDeCuenta,
  queLoImpide,
  rechazoDelCierre,
  rutaDelCierre,
  solicitarCierre,
} from './cierre-cuenta.js';

const MARCADO = readFileSync(new URL('./perfil.html', import.meta.url), 'utf8');
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
const UID = '0f3c9a4e-5d1b-4c8e-9a2f-6b7d8e9f0a1b';
const SIN_SOLICITUD = {
  estado: 'SIN_SOLICITUD',
  plazoDias: 30,
  solicitadoEn: null,
  programadoPara: null,
};
const PROGRAMADO = {
  estado: 'PROGRAMADO',
  plazoDias: 30,
  solicitadoEn: '2026-10-05T14:30:00-05:00',
  programadoPara: '2026-11-04T14:30:00-05:00',
};

function respuesta(estado, cuerpo) {
  return Promise.resolve({
    ok: estado >= 200 && estado < 300,
    status: estado,
    text: async () => (cuerpo === undefined ? '' : JSON.stringify(cuerpo)),
  });
}

const tick = () => new Promise((resolver) => setTimeout(resolver, 0));
const seccion = () => document.querySelector('[data-zona="cierre-cuenta"]');
const en = (selector) => seccion().querySelector(selector);
const estado = () => en('[data-zona="estado-cierre"]').textContent;
const aviso = () => en('[data-zona="aviso-cierre"]');
const clave = () => en('[name="passwordActualCierre"]');
const formulario = () => en('[data-zona="formulario-cierre"]');

async function montar({
  consultar = jest.fn(() => Promise.resolve(leerEstadoDelCierre(SIN_SOLICITUD))),
  solicitar = jest.fn(() => Promise.resolve(leerEstadoDelCierre(PROGRAMADO))),
  cancelar = jest.fn(() => Promise.resolve(leerEstadoDelCierre(SIN_SOLICITUD))),
  confirmarImpl = jest.fn(() => Promise.resolve(true)),
  irA = jest.fn(),
} = {}) {
  const vista = montarCierreDeCuenta(document, {
    sesion: { uid: UID },
    consultar,
    solicitar,
    cancelar,
    confirmarImpl,
    irA,
  });
  await vista.cargar();
  return { consultar, solicitar, cancelar, confirmarImpl, irA };
}

async function pedirCierreCon(contrasena) {
  en('[data-accion="solicitar-cierre"]').click();
  clave().value = contrasena;
  formulario().dispatchEvent(new Event('submit', { cancelable: true }));
  await tick();
  await tick();
}

beforeEach(() => {
  document.documentElement.innerHTML = MARCADO.replace(/<script[\s\S]*?<\/script>/g, '');
});

describe('cliente HTTP (contrato perfiles 1.4.0)', () => {
  test('la ruta es la de la propia cuenta, con el uid escapado', () => {
    expect(rutaDelCierre(UID)).toBe(`/api/v1/perfiles/${UID}/cierre`);
    expect(rutaDelCierre('a/b')).toBe('/api/v1/perfiles/a%2Fb/cierre');
  });

  test('GET, POST con la contraseña y DELETE, sin cachear la consulta', async () => {
    const fetchImpl = jest.fn(() => respuesta(200, PROGRAMADO));

    await consultarCierre(UID, { fetchImpl });
    await solicitarCierre(UID, 'Clave.Actual-1', { fetchImpl });
    await cancelarCierre(UID, { fetchImpl });

    const [get, post, del] = fetchImpl.mock.calls;
    expect(get[0]).toBe(`/api/v1/perfiles/${UID}/cierre`);
    expect(get[1].cache).toBe('no-store');
    expect(post[1].method).toBe('POST');
    expect(JSON.parse(post[1].body)).toEqual({ passwordActual: 'Clave.Actual-1' });
    expect(del[1].method).toBe('DELETE');
  });

  test('un rechazo se lee con su type, su detalle y lo que lo impide', async () => {
    const fetchImpl = jest.fn(() =>
      respuesta(409, {
        type: `${ERRORES}cierre-con-operaciones-pendientes`,
        status: 409,
        detail: 'Tienes subastas activas o pujas vigentes.',
        subastasActivas: 2,
        pujasVigentes: 1,
      }),
    );

    await expect(solicitarCierre(UID, 'x', { fetchImpl })).rejects.toMatchObject({
      estado: 409,
      tipo: PROBLEMAS_DEL_CIERRE.OPERACIONES_PENDIENTES,
      subastasActivas: 2,
      pujasVigentes: 1,
    });
  });

  test('lo que no es PROGRAMADO con fecha cuenta como sin solicitud', () => {
    expect(leerEstadoDelCierre(null).estado).toBe(ESTADOS_DEL_CIERRE.SIN_SOLICITUD);
    expect(leerEstadoDelCierre({ estado: 'PROGRAMADO' }).estado).toBe('SIN_SOLICITUD');
    expect(leerEstadoDelCierre(PROGRAMADO)).toEqual(PROGRAMADO);
  });
});

describe('la sección «Cerrar mi cuenta»', () => {
  test('sin solicitud: cuenta activa, el plazo del servidor y el botón para pedirlo', async () => {
    const { consultar } = await montar();

    expect(consultar).toHaveBeenCalledWith(UID);
    expect(estado()).toBe('Tu cuenta está activa: no has pedido cerrarla.');
    expect(en('[data-zona="cierre-plazo"]').textContent).toContain('30 días después de pedirlo');
    expect(en('[data-accion="solicitar-cierre"]').hidden).toBe(false);
    expect(en('[data-accion="cancelar-cierre"]').hidden).toBe(true);
    expect(formulario().hidden).toBe(true);
  });

  test('explica qué se elimina, qué se conserva y qué lo impide', () => {
    const explicacion = en('[data-zona="cierre-explicacion"]').textContent;
    expect(explicacion).toContain('Qué se elimina');
    expect(explicacion).toContain('Qué se conserva');
    expect(explicacion).toMatch(/registro de auditoría/);
    expect(explicacion).toMatch(/subastas tuyas activas ni pujas tuyas vigentes/);
  });

  test('con un cierre programado: la fecha y el botón para cancelarlo', async () => {
    await montar({ consultar: jest.fn(() => Promise.resolve(leerEstadoDelCierre(PROGRAMADO))) });

    expect(estado()).toMatch(/^Cierre programado para el /);
    expect(estado()).toContain('2026');
    expect(seccion().dataset.estado).toBe('PROGRAMADO');
    expect(en('[data-accion="solicitar-cierre"]').hidden).toBe(true);
    expect(en('[data-accion="cancelar-cierre"]').hidden).toBe(false);
  });

  test('si no se puede consultar: lo dice y ofrece reintentar', async () => {
    const consultar = jest
      .fn()
      .mockRejectedValueOnce(new FalloDelCierre({ estado: 503 }))
      .mockResolvedValueOnce(leerEstadoDelCierre(SIN_SOLICITUD));
    await montar({ consultar });

    expect(aviso().textContent).toContain('No pudimos consultar');
    expect(en('[data-accion="reintentar-cierre"]').hidden).toBe(false);

    en('[data-accion="reintentar-cierre"]').click();
    await tick();
    expect(estado()).toBe('Tu cuenta está activa: no has pedido cerrarla.');
  });

  test('solicitar: pide la contraseña, confirma y queda programado', async () => {
    const { solicitar, confirmarImpl } = await montar();

    await pedirCierreCon('Clave.Actual-1');

    expect(confirmarImpl).toHaveBeenCalledWith(
      expect.objectContaining({ titulo: 'Cerrar tu cuenta', peligro: true }),
    );
    expect(confirmarImpl.mock.calls[0][0].mensaje).toContain('30 días');
    expect(solicitar).toHaveBeenCalledWith(UID, 'Clave.Actual-1');
    expect(estado()).toMatch(/^Cierre programado para el /);
    expect(aviso().textContent).toContain('Programaste el cierre de tu cuenta');
    expect(formulario().hidden).toBe(true);
    expect(clave().value).toBe('');
  });

  test('sin contraseña no se pregunta ni se envía nada', async () => {
    const { solicitar, confirmarImpl } = await montar();

    await pedirCierreCon('');

    expect(clave().getAttribute('aria-invalid')).toBe('true');
    expect(confirmarImpl).not.toHaveBeenCalled();
    expect(solicitar).not.toHaveBeenCalled();
  });

  test('si no confirma, no se envía nada', async () => {
    const { solicitar } = await montar({ confirmarImpl: jest.fn(() => Promise.resolve(false)) });

    await pedirCierreCon('Clave.Actual-1');

    expect(solicitar).not.toHaveBeenCalled();
    expect(estado()).toBe('Tu cuenta está activa: no has pedido cerrarla.');
  });

  test('contraseña incorrecta: marca el campo, lo vacía y avisa del bloqueo', async () => {
    const solicitar = jest.fn(() =>
      Promise.reject(
        new FalloDelCierre({
          estado: 422,
          tipo: PROBLEMAS_DEL_CIERRE.CONTRASENA_ACTUAL_INCORRECTA,
        }),
      ),
    );
    await montar({ solicitar });

    await pedirCierreCon('otra');

    expect(clave().getAttribute('aria-invalid')).toBe('true');
    expect(clave().value).toBe('');
    expect(aviso().textContent).toContain('La contraseña actual es incorrecta');
    expect(aviso().textContent).toContain('intento fallido');
    expect(estado()).toBe('Tu cuenta está activa: no has pedido cerrarla.');
  });

  test('subastas o pujas abiertas: dice cuántas y lleva a Subastas', async () => {
    const solicitar = jest.fn(() =>
      Promise.reject(
        new FalloDelCierre({
          estado: 409,
          tipo: PROBLEMAS_DEL_CIERRE.OPERACIONES_PENDIENTES,
          subastasActivas: 2,
          pujasVigentes: 1,
        }),
      ),
    );
    const { irA } = await montar({ solicitar });

    await pedirCierreCon('Clave.Actual-1');

    expect(aviso().textContent).toContain('Tienes 2 subastas activas y 1 puja vigente');
    expect(aviso().textContent).toContain('No se programó nada');
    aviso().querySelector('[data-accion="ir-a-subastas"]').click();
    expect(irA).toHaveBeenCalledWith(expect.stringContaining('subastas'));
  });

  test('ms-subastas no responde: error, sin nada programado', async () => {
    const solicitar = jest.fn(() =>
      Promise.reject(
        new FalloDelCierre({ estado: 503, tipo: PROBLEMAS_DEL_CIERRE.SUBASTAS_NO_DISPONIBLES }),
      ),
    );
    await montar({ solicitar });

    await pedirCierreCon('Clave.Actual-1');

    expect(aviso().querySelector('.aviso--error')).not.toBeNull();
    expect(aviso().textContent).toContain('No pudimos comprobar tus subastas');
  });

  test('cancelar: confirma, vuelve a «activa» y lo dice', async () => {
    const { cancelar, confirmarImpl } = await montar({
      consultar: jest.fn(() => Promise.resolve(leerEstadoDelCierre(PROGRAMADO))),
    });

    en('[data-accion="cancelar-cierre"]').click();
    await tick();
    await tick();

    expect(confirmarImpl).toHaveBeenCalledWith(expect.objectContaining({ peligro: false }));
    expect(cancelar).toHaveBeenCalledWith(UID);
    expect(estado()).toBe('Tu cuenta está activa: no has pedido cerrarla.');
    expect(aviso().textContent).toContain('Cancelaste el cierre de tu cuenta');
  });

  test('si cancelar falla, el cierre sigue programado y se dice', async () => {
    await montar({
      consultar: jest.fn(() => Promise.resolve(leerEstadoDelCierre(PROGRAMADO))),
      cancelar: jest.fn(() => Promise.reject(new FalloDelCierre({ estado: 503 }))),
    });

    en('[data-accion="cancelar-cierre"]').click();
    await tick();
    await tick();

    expect(aviso().textContent).toContain('El cierre sigue programado');
    expect(estado()).toMatch(/^Cierre programado para el /);
  });

  test('el botón de envío nace apagado y se enciende al montar (G1)', () => {
    const boton = en('[data-accion="confirmar-cierre"]');
    expect(boton.disabled).toBe(true);
    montarCierreDeCuenta(document, { sesion: { uid: UID }, consultar: jest.fn() });
    expect(boton.disabled).toBe(false);
    expect(formulario().getAttribute('method')).toBe('post');
  });
});

describe('textos de los rechazos', () => {
  test('queLoImpide en singular y plural', () => {
    expect(queLoImpide(1, 0)).toBe('1 subasta activa');
    expect(queLoImpide(0, 3)).toBe('3 pujas vigentes');
    expect(queLoImpide(2, 1)).toBe('2 subastas activas y 1 puja vigente');
  });

  test('cuenta bloqueada y fallos sin type', () => {
    expect(
      rechazoDelCierre(
        new FalloDelCierre({ estado: 423, tipo: PROBLEMAS_DEL_CIERRE.CUENTA_BLOQUEADA }),
      ).titulo,
    ).toBe('Tu cuenta está bloqueada temporalmente');
    expect(rechazoDelCierre(new TypeError('red')).titulo).toBe('No pudimos programar el cierre');
    expect(rechazoDelCierre(new FalloDelCierre({ estado: 500 })).tono).toBe('error');
  });
});
