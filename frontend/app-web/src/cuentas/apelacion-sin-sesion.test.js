/**
 * Apelar sin sesión — SA-4 (ms-identidad-auth 2.4.0, §7.3.2).
 *
 * Sobre la sección real de `login.html`, con el cliente inyectado. Lo que se
 * prueba: qué rechazo trae código para apelar; la forma de las dos
 * peticiones; lo que dice la pantalla en cada estado de la sanción y de su
 * apelación; los fallos en su sitio; y que el código nunca sale de la
 * memoria de la página.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

import {
  ARGUMENTO_MAXIMO,
  FalloDeLaApelacion,
  RUTAS_DE_APELACION,
  codigoDeApelacion,
  consultarApelacion,
  falloDeLaApelacion,
  montarApelacionSinSesion,
  motivoDelArgumento,
  presentarApelacion,
  resumenDeLaApelacion,
} from './apelacion-sin-sesion.js';

const MARCADO = readFileSync(new URL('./login.html', import.meta.url), 'utf8');
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
const CODIGO = 'q3V9Yb0n2xk7r8T1mW4cJ6pA5sD0fG2hL9zX3vB7nQe';
const SANCION = 'aaaaaaaa-0000-4000-8000-000000000001';

const BANEO = Object.freeze({
  id: SANCION,
  tipo: 'BANEO',
  causal: 'FRAUDE',
  motivo: 'Duplicó créditos con dos sesiones a la vez',
  politica: 'Reglamento de juego limpio',
  emitidaEn: '2026-10-10T15:00:00Z',
  vigenteHasta: null,
});

const APELABLE = Object.freeze({
  sancion: BANEO,
  apelableHasta: '2026-11-09T15:00:00Z',
  puedeApelar: true,
  motivoNoApelable: null,
  apelacion: null,
});

const PENDIENTE = Object.freeze({
  id: 'bbbbbbbb-0000-4000-8000-000000000002',
  sancionId: SANCION,
  estado: 'PENDIENTE',
  argumento: 'No fui yo',
  creadaEn: '2026-10-14T15:00:00Z',
  decisionAntesDe: '2026-11-05T04:59:59.999Z',
  vencida: false,
});

const tick = () => new Promise((resolver) => setTimeout(resolver, 0));
const $ = (selector) => document.querySelector(selector);
const seccion = () => $('[data-zona="apelacion"]');
const enviar = (formulario) => formulario.dispatchEvent(new Event('submit', { cancelable: true }));
const fallo = (estado, tipo = null) => new FalloDeLaApelacion({ estado, tipo });

function montar(dobles = {}) {
  const cliente = {
    alVolver: jest.fn(),
    consultar: jest.fn(() => Promise.resolve(APELABLE)),
    presentar: jest.fn(() => Promise.resolve(PENDIENTE)),
    ...dobles,
  };
  const pantalla = montarApelacionSinSesion(document, cliente);
  return { pantalla, ...cliente };
}

beforeEach(() => {
  document.documentElement.innerHTML = MARCADO.replace(/<script[\s\S]*?<\/script>/g, '');
  globalThis.localStorage?.clear();
  globalThis.sessionStorage?.clear();
});

afterEach(() => {
  // El código no sale de la memoria de la página.
  expect(globalThis.localStorage.length).toBe(0);
  expect(globalThis.sessionStorage.length).toBe(0);
  expect(globalThis.location.href).not.toContain(CODIGO);
});

// ---------------------------------------------------- qué rechazo trae código

describe('qué rechazo del login trae el código para apelar', () => {
  test('el 403 de una cuenta suspendida o baneada, con su código y cuándo caduca', () => {
    for (const tipo of ['cuenta-suspendida', 'cuenta-baneada']) {
      expect(
        codigoDeApelacion(403, {
          type: ERRORES + tipo,
          apelacion: { codigo: CODIGO, expiraEn: '2026-10-14T15:30:00Z' },
        }),
      ).toEqual({ codigo: CODIGO, expiraEn: '2026-10-14T15:30:00Z' });
    }
  });

  test('sin código, con otro tipo, otro estado o texto plano: no hay apelación desde aquí', () => {
    expect(codigoDeApelacion(403, { type: `${ERRORES}cuenta-baneada` })).toBeNull();
    expect(
      codigoDeApelacion(403, { type: `${ERRORES}cuenta-inactiva`, apelacion: { codigo: CODIGO } }),
    ).toBeNull();
    expect(
      codigoDeApelacion(401, { type: `${ERRORES}cuenta-baneada`, apelacion: { codigo: CODIGO } }),
    ).toBeNull();
    expect(codigoDeApelacion(403, 'Esta cuenta ha sido baneada permanentemente.')).toBeNull();
    expect(
      codigoDeApelacion(403, { type: `${ERRORES}cuenta-baneada`, apelacion: { codigo: '' } }),
    ).toBeNull();
  });
});

// ------------------------------------------------------------- las peticiones

describe('las dos peticiones', () => {
  function respuesta(estado, cuerpo) {
    return {
      ok: estado >= 200 && estado < 300,
      status: estado,
      text: () => Promise.resolve(cuerpo === undefined ? '' : JSON.stringify(cuerpo)),
    };
  }

  test('consultar: POST con el código en el cuerpo, nunca en la dirección', async () => {
    const fetchImpl = jest.fn(() => Promise.resolve(respuesta(200, APELABLE)));

    await expect(consultarApelacion(CODIGO, { fetchImpl })).resolves.toEqual(APELABLE);

    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe(RUTAS_DE_APELACION.consulta);
    expect(url).not.toContain(CODIGO);
    expect(opciones.method).toBe('POST');
    expect(opciones.headers.Accept).toContain('application/problem+json');
    expect(JSON.parse(opciones.body)).toEqual({ codigo: CODIGO });
  });

  test('presentar: el código, la sanción que se vio y el argumento sin espacios sobrantes', async () => {
    const fetchImpl = jest.fn(() => Promise.resolve(respuesta(201, PENDIENTE)));

    await presentarApelacion(
      { codigo: CODIGO, sancionId: SANCION, argumento: '  No fui yo  ' },
      { fetchImpl },
    );

    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe(RUTAS_DE_APELACION.apelacion);
    expect(JSON.parse(opciones.body)).toEqual({
      codigo: CODIGO,
      sancionId: SANCION,
      argumento: 'No fui yo',
    });
  });

  test('un rechazo se lee por su estado y su type', async () => {
    const fetchImpl = jest.fn(() =>
      Promise.resolve(respuesta(401, { type: `${ERRORES}codigo-de-apelacion-invalido` })),
    );

    const error = await consultarApelacion(CODIGO, { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(FalloDeLaApelacion);
    expect(error.estado).toBe(401);
    expect(error.tipo).toBe('codigo-de-apelacion-invalido');
  });
});

// ---------------------------------------------------- lo que dice la pantalla

describe('lo que dice la pantalla', () => {
  test('el baneo: causal del documento, motivo, política y fecha; se puede apelar hasta…', () => {
    const resumen = resumenDeLaApelacion(APELABLE);

    expect(resumen.titulo).toBe('Inhabilitación definitiva');
    expect(resumen.datos).toEqual([
      ['Causal', 'Intentos de fraude o manipulación del sistema'],
      ['Motivo', 'Duplicó créditos con dos sesiones a la vez'],
      ['Política', 'Reglamento de juego limpio'],
      ['Desde', expect.any(String)],
    ]);
    expect(resumen.puedeApelar).toBe(true);
    expect(resumen.plazo).toMatch(/^Puedes apelar hasta el /);
    expect(resumen.aviso).toBeNull();
  });

  test('la suspensión dice hasta cuándo; sin causal (anterior a SA-4) no se inventa una', () => {
    const resumen = resumenDeLaApelacion({
      ...APELABLE,
      sancion: {
        ...BANEO,
        tipo: 'SUSPENSION',
        causal: null,
        politica: null,
        vigenteHasta: '2026-10-20T15:00:00Z',
      },
    });

    expect(resumen.titulo).toMatch(/^Suspensión hasta el /);
    expect(resumen.datos.map(([etiqueta]) => etiqueta)).toEqual(['Motivo', 'Desde']);
  });

  test('con la apelación en revisión: cuándo se presentó y hasta cuándo tiene el panel', () => {
    const resumen = resumenDeLaApelacion({
      ...APELABLE,
      puedeApelar: false,
      motivoNoApelable: 'APELACION_ABIERTA',
      apelacion: PENDIENTE,
    });

    expect(resumen.puedeApelar).toBe(false);
    expect(resumen.aviso.tono).toBe('info');
    expect(resumen.aviso.titulo).toBe('Tu apelación está en revisión');
    expect(resumen.aviso.detalle).toMatch(/te responderá a más tardar el/);
  });

  test('si el panel ya pasó de su plazo, se dice sin culpar a nadie y la apelación sigue abierta', () => {
    const resumen = resumenDeLaApelacion({
      ...APELABLE,
      puedeApelar: false,
      motivoNoApelable: 'APELACION_ABIERTA',
      apelacion: { ...PENDIENTE, vencida: true },
    });

    expect(resumen.aviso.tono).toBe('advertencia');
    expect(resumen.aviso.detalle).toMatch(/venció el .*sigue abierta/);
  });

  test('una apelación resuelta con plazo todavía: la decisión y, debajo, se puede volver a apelar', () => {
    const resumen = resumenDeLaApelacion({
      ...APELABLE,
      apelacion: {
        ...PENDIENTE,
        estado: 'MANTENIDA',
        resueltaEn: '2026-10-16T15:00:00Z',
        decisionMotivo: 'Las pruebas son claras',
      },
    });

    expect(resumen.aviso.titulo).toMatch(
      /^Tu última apelación se resolvió el .*: la sanción se mantiene\.$/,
    );
    expect(resumen.aviso.detalle).toBe('Decisión del panel: Las pruebas son claras');
    expect(resumen.puedeApelar).toBe(true);
  });

  test('fuera de plazo: hasta cuándo se podía, sin formulario', () => {
    const resumen = resumenDeLaApelacion({
      ...APELABLE,
      puedeApelar: false,
      motivoNoApelable: 'FUERA_DE_PLAZO',
    });

    expect(resumen.aviso.titulo).toBe('El plazo para apelar terminó');
    expect(resumen.aviso.detalle).toMatch(/^Se podía apelar hasta el /);
    expect(resumen.puedeApelar).toBe(false);
    expect(resumen.plazo).toBeNull();
  });

  test('sin sanción que impida entrar: se dice y se invita a entrar', () => {
    const resumen = resumenDeLaApelacion({
      sancion: null,
      puedeApelar: false,
      motivoNoApelable: 'SIN_SANCION',
    });

    expect(resumen.sinSancion).toBe(true);
    expect(resumen.aviso.tono).toBe('exito');
    expect(resumen.puedeApelar).toBe(false);
  });

  test('el argumento: no vacío y como mucho 2000 caracteres', () => {
    expect(motivoDelArgumento('   ')).toMatch(/por qué crees/);
    expect(motivoDelArgumento('x'.repeat(ARGUMENTO_MAXIMO + 1))).toMatch(/2000/);
    expect(motivoDelArgumento(' Era una cita ')).toBeNull();
  });

  test('cada fallo con su salida: caducado vuelve a entrar; no procede vuelve a consultar', () => {
    expect(falloDeLaApelacion(fallo(401, 'codigo-de-apelacion-invalido'))).toMatchObject({
      volverAEntrar: true,
      consultarDeNuevo: false,
    });
    expect(falloDeLaApelacion(fallo(422, 'apelacion-no-procede'))).toMatchObject({
      volverAEntrar: false,
      consultarDeNuevo: true,
    });
    expect(falloDeLaApelacion(fallo(429)).titulo).toMatch(/varias solicitudes/);
    expect(falloDeLaApelacion(fallo(503, 'moderacion-no-disponible')).tono).toBe('error');
    expect(falloDeLaApelacion(new TypeError('Failed to fetch')).tono).toBe('error');
  });
});

// ------------------------------------------------------------------ la vista

describe('la pantalla sobre login.html', () => {
  test('abrir aparta el primer paso, carga la sanción y pone el foco en el argumento', async () => {
    const { pantalla, consultar } = montar();

    await pantalla.abrir({ codigo: CODIGO });

    expect(consultar).toHaveBeenCalledWith(CODIGO);
    expect(seccion().hidden).toBe(false);
    expect($('#formLogin').hidden).toBe(true);
    expect($('[data-zona="titulo-sancion"]').textContent).toBe('Inhabilitación definitiva');
    expect(seccion().textContent).toContain('Intentos de fraude o manipulación del sistema');
    expect(seccion().textContent).not.toMatch(/GET |\/api\/|HU-|HTTP/);
    expect($('#formApelacion').hidden).toBe(false);
    expect($('[data-zona="plazo-apelacion"]').textContent).toMatch(/^Puedes apelar hasta el /);
    expect(document.activeElement).toBe($('#argumentoApelacion'));
  });

  test('sin argumento no se envía; con él, se presenta y el código queda gastado', async () => {
    const { pantalla, presentar } = montar();
    await pantalla.abrir({ codigo: CODIGO });

    enviar($('#formApelacion'));
    await tick();
    expect(presentar).not.toHaveBeenCalled();
    expect($('.campo--invalido')).not.toBeNull();

    $('#argumentoApelacion').value = '  Era una cita de otro jugador  ';
    enviar($('#formApelacion'));
    await tick();
    await tick();

    expect(presentar).toHaveBeenCalledWith({
      codigo: CODIGO,
      sancionId: SANCION,
      argumento: '  Era una cita de otro jugador  ',
    });
    expect($('#formApelacion').hidden).toBe(true);
    expect($('[data-zona="aviso-apelacion"]').textContent).toContain('Recibimos tu apelación');
    expect($('[data-zona="aviso-apelacion"]').textContent).toContain('te llegará al correo');

    // Un segundo envío ya no tiene código con el que ir.
    enviar($('#formApelacion'));
    await tick();
    expect(presentar).toHaveBeenCalledTimes(1);
  });

  test('si no procede, vuelve a consultar y enseña cómo está ahora', async () => {
    const consultar = jest
      .fn()
      .mockResolvedValueOnce(APELABLE)
      .mockResolvedValueOnce({
        ...APELABLE,
        puedeApelar: false,
        motivoNoApelable: 'APELACION_ABIERTA',
        apelacion: PENDIENTE,
      });
    const { pantalla } = montar({
      consultar,
      presentar: jest.fn(() => Promise.reject(fallo(422, 'apelacion-no-procede'))),
    });
    await pantalla.abrir({ codigo: CODIGO });

    $('#argumentoApelacion').value = 'Otra vez';
    enviar($('#formApelacion'));
    await tick();
    await tick();
    await tick();

    expect(consultar).toHaveBeenCalledTimes(2);
    expect($('#formApelacion').hidden).toBe(true);
    expect($('[data-zona="aviso-apelacion"]').textContent).toContain(
      'No se pudo presentar la apelación',
    );
  });

  test('un código caducado ofrece volver a la entrada, que devuelve el primer paso como estaba', async () => {
    const { pantalla, alVolver } = montar({
      consultar: jest.fn(() => Promise.reject(fallo(401, 'codigo-de-apelacion-invalido'))),
    });
    await pantalla.abrir({ codigo: CODIGO });

    expect($('[data-zona="aviso-apelacion"]').textContent).toContain(
      'El código para apelar caducó',
    );
    $('[data-accion="volver-a-entrar"]').click();

    expect(seccion().hidden).toBe(true);
    expect($('#formLogin').hidden).toBe(false);
    expect(alVolver).toHaveBeenCalledTimes(1);
  });

  test('moderación caída: el formulario sigue ahí y se puede reintentar', async () => {
    const presentar = jest
      .fn()
      .mockRejectedValueOnce(fallo(503, 'moderacion-no-disponible'))
      .mockResolvedValueOnce(PENDIENTE);
    const { pantalla } = montar({ presentar });
    await pantalla.abrir({ codigo: CODIGO });

    $('#argumentoApelacion').value = 'No fui yo';
    enviar($('#formApelacion'));
    await tick();
    await tick();
    expect($('[data-zona="aviso-apelacion"]').textContent).toContain(
      'No pudimos registrar tu apelación ahora',
    );
    expect($('#formApelacion').hidden).toBe(false);

    enviar($('#formApelacion'));
    await tick();
    await tick();
    expect(presentar).toHaveBeenCalledTimes(2);
    expect($('[data-zona="aviso-apelacion"]').textContent).toContain('Recibimos tu apelación');
  });

  test('«Volver a la entrada» olvida el código y lo que se escribió', async () => {
    const { pantalla, alVolver, consultar } = montar();
    await pantalla.abrir({ codigo: CODIGO });
    $('#argumentoApelacion').value = 'Borrador';

    $('[data-accion="volver-desde-apelacion"]').click();

    expect(seccion().hidden).toBe(true);
    expect($('#argumentoApelacion').value).toBe('');
    expect(alVolver).toHaveBeenCalledTimes(1);
    // Reabrir pide otra vez la sanción: nada quedó en caché.
    await pantalla.abrir({ codigo: CODIGO });
    expect(consultar).toHaveBeenCalledTimes(2);
  });

  test('el contador dice cuánto lleva el argumento', async () => {
    const { pantalla } = montar();
    await pantalla.abrir({ codigo: CODIGO });

    $('#argumentoApelacion').value = 'Hola';
    $('#argumentoApelacion').dispatchEvent(new Event('input'));

    expect($('[data-zona="contador-argumento"]').textContent).toBe(`4 de ${ARGUMENTO_MAXIMO}`);
  });

  test('sin la sección en la vista no monta nada', () => {
    document.body.innerHTML = '<main></main>';
    expect(montarApelacionSinSesion(document)).toBeNull();
  });
});
