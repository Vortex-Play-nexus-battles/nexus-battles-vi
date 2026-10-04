/**
 * «Restablecer contraseña» — HU-COR-003 y activación de cuentas
 * administrativas; B1 (identidad 2.0.0, 7.1.1).
 *
 * Contrato 2.0.0 (incompatible con el anterior): el canje ya no manda un
 * `token` suelto, sino `email` + `codigo` (+ `respuestas` si la cuenta
 * configuró preguntas de seguridad). Se prueba el cliente contra esa forma y
 * la vista montada sobre su marcado real, con los dos pasos: el código se
 * canjea por las preguntas, y las respuestas y la contraseña nueva por el
 * cambio.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

import { CLAVES_DEL_CORREO } from '../comun/codigo-de-correo.js';
import { CLAVE_CORREO_REGISTRADO } from '../comun/entrada.js';
import { FalloDeRecuperacion, MENSAJE_DE_CAMBIO } from '../comun/recuperacion.js';
import {
  confirmarRestablecimiento,
  consultarPreguntas,
  montarRestablecimiento,
  rechazoDelCanje,
} from './restablecer-confirmar.js';

const MARCADO = readFileSync(new URL('./restablecer-confirmar.html', import.meta.url), 'utf8');
const BASE = 'http://localhost:8099/frontend/app-web/src/comun/sesion.js';
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
const CLAVE_BUENA = 'Clave-Nueva-2026';
const PREGUNTAS = [
  { id: '0f3c9a4e-5d1b-4c8e-9a2f-6b7d8e9f0a1b', texto: '¿Cómo se llamaba tu primera mascota?' },
  { id: '7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55', texto: '¿En qué ciudad naciste?' },
];

function respuesta(estado, cuerpo) {
  return Promise.resolve({
    ok: estado >= 200 && estado < 300,
    status: estado,
    text: async () => {
      if (cuerpo === undefined || cuerpo === null) {
        return '';
      }
      return typeof cuerpo === 'string' ? cuerpo : JSON.stringify(cuerpo);
    },
  });
}

const tick = () => new Promise((resolver) => setTimeout(resolver, 0));
const zona = (nombre) => document.querySelector(`[data-zona="${nombre}"]`);
const $ = (selector) => document.querySelector(selector);

function montar({
  hash = '',
  consultar = jest.fn(() => Promise.resolve({ configuradas: false, preguntas: [] })),
  confirmar = jest.fn(() => Promise.resolve(MENSAJE_DE_CAMBIO)),
} = {}) {
  const historial = { state: null, replaceState: jest.fn() };
  const navegar = jest.fn();
  montarRestablecimiento(document, {
    consultar,
    confirmar,
    almacen: sessionStorage,
    ubicacion: { hash, search: '', pathname: '/restablecer' },
    historial,
    navegar,
    base: BASE,
  });
  return { consultar, confirmar, historial, navegar };
}

async function pasoDelCodigo(email = 'ana@nexus.test', codigo = 'K7QX2M9P') {
  $('#email').value = email;
  $('#codigo').value = codigo;
  $('#formCodigo').dispatchEvent(new Event('submit', { cancelable: true }));
  await tick();
}

async function pasoDeLaClave(clave = CLAVE_BUENA, confirmacion = clave) {
  $('#nuevaPassword').value = clave;
  $('#confirmarPassword').value = confirmacion;
  $('#formClave').dispatchEvent(new Event('submit', { cancelable: true }));
  await tick();
}

beforeEach(() => {
  document.documentElement.innerHTML = MARCADO.replace(/<script[\s\S]*?<\/script>/g, '');
  sessionStorage.clear();
});

// ------------------------------------------------------------------ cliente

describe('consultarPreguntas (cliente)', () => {
  test('POST a la ruta del contrato con el correo y el código', async () => {
    const fetchImpl = jest.fn(() => respuesta(200, { configuradas: true, preguntas: PREGUNTAS }));

    const resultado = await consultarPreguntas(
      { email: 'ana@nexus.test', codigo: 'K7QX2M9P' },
      { fetchImpl },
    );

    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/auth/restablecer/preguntas');
    expect(opciones.method).toBe('POST');
    expect(JSON.parse(opciones.body)).toEqual({ email: 'ana@nexus.test', codigo: 'K7QX2M9P' });
    expect(resultado).toEqual({ configuradas: true, preguntas: PREGUNTAS });
  });

  test('lista vacía = la cuenta no configuró preguntas; lo mal formado no se pinta', async () => {
    const vacia = await consultarPreguntas(
      { email: 'a@b.test', codigo: 'K7QX2M9P' },
      { fetchImpl: () => respuesta(200, { configuradas: false, preguntas: [] }) },
    );
    expect(vacia).toEqual({ configuradas: false, preguntas: [] });

    const rara = await consultarPreguntas(
      { email: 'a@b.test', codigo: 'K7QX2M9P' },
      {
        fetchImpl: () =>
          respuesta(200, { configuradas: true, preguntas: [{ id: 1 }, null, PREGUNTAS[0]] }),
      },
    );
    expect(rara.preguntas).toEqual([PREGUNTAS[0]]);
  });

  test('un código inválido llega con su type', async () => {
    const error = await consultarPreguntas(
      { email: 'a@b.test', codigo: 'MALO1234' },
      {
        fetchImpl: () =>
          respuesta(400, { type: `${ERRORES}codigo-invalido`, status: 400, detail: 'x' }),
      },
    ).catch((e) => e);
    expect(error).toBeInstanceOf(FalloDeRecuperacion);
    expect(error.tipo).toBe('codigo-invalido');
    expect(error.estado).toBe(400);
  });
});

describe('confirmarRestablecimiento (cliente, contrato 2.0.0)', () => {
  test('manda correo, código y contraseña; ya no manda `token`', async () => {
    const fetchImpl = jest.fn(() => respuesta(200, 'Contraseña actualizada correctamente.'));

    const mensaje = await confirmarRestablecimiento(
      { email: 'ana@nexus.test', codigo: 'K7QX2M9P', nuevaPassword: CLAVE_BUENA },
      { fetchImpl },
    );

    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/auth/restablecer/confirmar');
    const cuerpo = JSON.parse(opciones.body);
    expect(cuerpo).toEqual({
      email: 'ana@nexus.test',
      codigo: 'K7QX2M9P',
      nuevaPassword: CLAVE_BUENA,
    });
    // Sin preguntas no viaja `respuestas`, y la confirmación nunca sale del navegador.
    expect(Object.keys(cuerpo)).not.toContain('respuestas');
    expect(Object.keys(cuerpo)).not.toContain('token');
    expect(Object.keys(cuerpo)).not.toContain('confirmacion');
    expect(mensaje).toBe('Contraseña actualizada correctamente.');
  });

  test('con preguntas, una respuesta por cada preguntaId', async () => {
    const fetchImpl = jest.fn(() => respuesta(200, ''));
    const respuestas = [
      { preguntaId: PREGUNTAS[0].id, respuesta: 'Firulais' },
      { preguntaId: PREGUNTAS[1].id, respuesta: 'Bucaramanga' },
    ];

    const mensaje = await confirmarRestablecimiento(
      { email: 'ana@nexus.test', codigo: 'K7QX2M9P', nuevaPassword: CLAVE_BUENA, respuestas },
      { fetchImpl },
    );

    expect(JSON.parse(fetchImpl.mock.calls[0][1].body).respuestas).toEqual(respuestas);
    // Sin texto del servidor, el de siempre.
    expect(mensaje).toBe(MENSAJE_DE_CAMBIO);
  });

  test('cada rechazo llega con su type', async () => {
    for (const [estado, tipo] of [
      [400, 'codigo-invalido'],
      [429, 'demasiados-intentos'],
      [422, 'respuestas-incorrectas'],
      [422, 'contrasena-no-cumple-politica'],
    ]) {
      const error = await confirmarRestablecimiento(
        { email: 'a@b.test', codigo: 'K7QX2M9P', nuevaPassword: 'x' },
        { fetchImpl: () => respuesta(estado, { type: `${ERRORES}${tipo}`, status: estado }) },
      ).catch((e) => e);
      expect(error.tipo).toBe(tipo);
      expect(error.estado).toBe(estado);
    }
  });
});

describe('rechazoDelCanje', () => {
  const fallo = (estado, tipo, detalle = null) =>
    new FalloDeRecuperacion({ estado, tipo, detalle });

  test('los del código vuelven al paso del código', () => {
    const invalido = rechazoDelCanje(fallo(400, 'codigo-invalido'));
    expect(invalido).toMatchObject({ campo: 'codigo', volverAlCodigo: true });
    expect(invalido.titulo).toBe('El código no es válido');
    expect(rechazoDelCanje(fallo(429, 'demasiados-intentos')).volverAlCodigo).toBe(true);
  });

  test('respuestas incorrectas y contraseña fuera de política se quedan en el segundo paso', () => {
    expect(rechazoDelCanje(fallo(422, 'respuestas-incorrectas'))).toMatchObject({
      campo: 'respuestas',
      volverAlCodigo: false,
    });
    const politica = rechazoDelCanje(
      fallo(422, 'contrasena-no-cumple-politica', 'Le falta un símbolo.'),
    );
    expect(politica.campo).toBe('nuevaPassword');
    expect(politica.detalle).toBe('Le falta un símbolo.');
  });

  test('sin conexión, o un 5xx, no enseña nada interno', () => {
    expect(rechazoDelCanje(new TypeError('red')).tono).toBe('error');
    const caido = rechazoDelCanje(fallo(500, null, 'java.lang.NullPointerException'));
    expect(caido.tono).toBe('error');
    expect(caido.detalle).not.toContain('java');
  });

  test('el límite del borde (429 sin type) no manda a pedir otro código', () => {
    const limite = rechazoDelCanje(fallo(429, null));
    expect(limite.titulo).toBe('Demasiadas solicitudes seguidas');
    expect(limite.volverAlCodigo).toBe(false);
  });
});

// -------------------------------------------------------------------- vista

describe('al llegar', () => {
  test('G1 — los dos formularios van por POST y sus botones nacen apagados hasta montar', () => {
    const botones = [$('[data-accion="continuar"]'), $('[data-accion="guardar"]')];
    expect($('#formCodigo').getAttribute('method')).toBe('post');
    expect($('#formClave').getAttribute('method')).toBe('post');
    expect(botones.map((boton) => boton.disabled)).toEqual([true, true]);

    montar();

    expect(botones.map((boton) => boton.disabled)).toEqual([false, false]);
  });

  test('desde el enlace del correo: rellena, borra el fragmento y espera un clic', () => {
    const { historial, consultar } = montar({
      hash: '#codigo=k7qx-2m9p&correo=ana%40nexus.test',
    });
    expect($('#email').value).toBe('ana@nexus.test');
    expect($('#codigo').value).toBe('K7QX2M9P');
    expect(historial.replaceState).toHaveBeenCalledWith(null, '', '/restablecer');
    expect(consultar).not.toHaveBeenCalled();
    expect(document.activeElement).toBe($('[data-accion="continuar"]'));
  });

  test('tras pedir el código, trae el correo con el que se pidió', () => {
    sessionStorage.setItem(CLAVES_DEL_CORREO.recuperacion, 'ana@nexus.test');
    montar();
    expect($('#email').value).toBe('ana@nexus.test');
    expect(document.activeElement).toBe($('#codigo'));
  });
});

describe('con preguntas de seguridad', () => {
  test('pinta una pregunta por campo y manda cada respuesta con su preguntaId', async () => {
    const consultar = jest.fn(() => Promise.resolve({ configuradas: true, preguntas: PREGUNTAS }));
    const { confirmar, navegar } = montar({ consultar });

    await pasoDelCodigo('ana@nexus.test', 'k7qx 2m9p');

    expect(consultar).toHaveBeenCalledWith({ email: 'ana@nexus.test', codigo: 'K7QX2M9P' });
    expect($('#formCodigo').hidden).toBe(true);
    expect($('#formClave').hidden).toBe(false);
    expect(zona('preguntas').hidden).toBe(false);
    const etiquetas = [...zona('lista-preguntas').querySelectorAll('label')].map(
      (e) => e.textContent,
    );
    expect(etiquetas).toEqual(PREGUNTAS.map((p) => p.texto));
    const campos = [...zona('lista-preguntas').querySelectorAll('input')];
    // Cada campo con su etiqueta de verdad (for/id), no por cercanía.
    for (const campo of campos) {
      expect(document.querySelector(`label[for="${campo.id}"]`)).not.toBeNull();
    }
    expect(document.activeElement).toBe(campos[0]);
    expect(zona('resumen-codigo').textContent).toContain('ana@nexus.test');

    campos[0].value = 'Firulais';
    campos[1].value = 'Bucaramanga';
    await pasoDeLaClave();

    expect(confirmar).toHaveBeenCalledWith({
      email: 'ana@nexus.test',
      codigo: 'K7QX2M9P',
      nuevaPassword: CLAVE_BUENA,
      respuestas: [
        { preguntaId: PREGUNTAS[0].id, respuesta: 'Firulais' },
        { preguntaId: PREGUNTAS[1].id, respuesta: 'Bucaramanga' },
      ],
    });
    const destino = new URL(navegar.mock.calls[0][0]);
    expect(destino.pathname).toBe('/frontend/app-web/src/cuentas/login.html');
    expect(destino.searchParams.get('motivo')).toBe('restablecida');
    expect(destino.href).not.toContain('ana');
    expect(sessionStorage.getItem(CLAVE_CORREO_REGISTRADO)).toBe('ana@nexus.test');
    // La contraseña no se queda en el formulario.
    expect($('#nuevaPassword').value).toBe('');
  });

  test('una respuesta vacía no llega al servidor', async () => {
    const consultar = jest.fn(() => Promise.resolve({ configuradas: true, preguntas: PREGUNTAS }));
    const { confirmar } = montar({ consultar });
    await pasoDelCodigo();

    const campos = [...zona('lista-preguntas').querySelectorAll('input')];
    campos[0].value = 'Firulais';
    await pasoDeLaClave();

    expect(confirmar).not.toHaveBeenCalled();
    expect(campos[1].getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(campos[1]);
  });

  test('respuestas-incorrectas: lo explica y deja el foco en la primera respuesta', async () => {
    const consultar = jest.fn(() => Promise.resolve({ configuradas: true, preguntas: PREGUNTAS }));
    const confirmar = jest.fn(() =>
      Promise.reject(new FalloDeRecuperacion({ estado: 422, tipo: 'respuestas-incorrectas' })),
    );
    const { navegar } = montar({ consultar, confirmar });
    await pasoDelCodigo();
    const campos = [...zona('lista-preguntas').querySelectorAll('input')];
    campos[0].value = 'mal';
    campos[1].value = 'mal';

    await pasoDeLaClave();

    expect(navegar).not.toHaveBeenCalled();
    expect(zona('aviso').textContent).toContain('Alguna respuesta no coincide');
    expect($('#formClave').hidden).toBe(false);
    expect(document.activeElement).toBe(campos[0]);
  });
});

describe('sin preguntas de seguridad', () => {
  test('basta el código: no se pide ninguna respuesta ni se manda `respuestas`', async () => {
    const { confirmar } = montar();
    await pasoDelCodigo();

    expect(zona('preguntas').hidden).toBe(true);
    expect(document.activeElement).toBe($('#nuevaPassword'));
    await pasoDeLaClave();

    expect(confirmar).toHaveBeenCalledWith({
      email: 'ana@nexus.test',
      codigo: 'K7QX2M9P',
      nuevaPassword: CLAVE_BUENA,
      respuestas: [],
    });
  });
});

describe('rechazos del código', () => {
  test('codigo-invalido en el primer paso: marca el código y no avanza', async () => {
    const consultar = jest.fn(() =>
      Promise.reject(new FalloDeRecuperacion({ estado: 400, tipo: 'codigo-invalido' })),
    );
    montar({ consultar });

    await pasoDelCodigo();

    expect($('#formClave').hidden).toBe(true);
    expect(zona('aviso').textContent).toContain('El código no es válido');
    expect($('#codigo').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe($('#codigo'));
    expect($('[data-accion="continuar"]').disabled).toBe(false);
  });

  test('demasiados-intentos al canjear: vuelve al paso del código para pedir otro', async () => {
    const confirmar = jest.fn(() =>
      Promise.reject(new FalloDeRecuperacion({ estado: 429, tipo: 'demasiados-intentos' })),
    );
    montar({ confirmar });
    await pasoDelCodigo();

    await pasoDeLaClave();

    expect($('#formClave').hidden).toBe(true);
    expect($('#formCodigo').hidden).toBe(false);
    expect(zona('aviso').textContent).toContain('Ese código ya no sirve');
  });

  test('«Usar otro código» vuelve al primer paso con el campo vacío', async () => {
    montar();
    await pasoDelCodigo();

    $('[data-accion="otro-codigo"]').click();

    expect($('#formCodigo').hidden).toBe(false);
    expect($('#formClave').hidden).toBe(true);
    expect($('#codigo').value).toBe('');
    expect(document.activeElement).toBe($('#codigo'));
  });
});

describe('la contraseña nueva', () => {
  test('una débil no llega al servidor y se dice qué le falta', async () => {
    const { confirmar } = montar();
    await pasoDelCodigo();

    await pasoDeLaClave('corta');

    expect(confirmar).not.toHaveBeenCalled();
    expect($('#nuevaPassword').getAttribute('aria-invalid')).toBe('true');
    expect($('#nuevaPassword').closest('.campo').textContent).toContain('A tu contraseña le falta');
  });

  test('si la confirmación no coincide, tampoco', async () => {
    const { confirmar } = montar();
    await pasoDelCodigo();

    await pasoDeLaClave(CLAVE_BUENA, 'Otra-Clave-2026');

    expect(confirmar).not.toHaveBeenCalled();
    expect($('#confirmarPassword').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe($('#confirmarPassword'));
  });

  test('si el servidor la rechaza por política, marca el campo con su motivo', async () => {
    const confirmar = jest.fn(() =>
      Promise.reject(
        new FalloDeRecuperacion({
          estado: 422,
          tipo: 'contrasena-no-cumple-politica',
          detalle: 'La contraseña no puede contener el apodo.',
        }),
      ),
    );
    montar({ confirmar });
    await pasoDelCodigo();

    await pasoDeLaClave();

    expect($('#nuevaPassword').closest('.campo').textContent).toContain(
      'La contraseña no puede contener el apodo.',
    );
    expect(document.activeElement).toBe($('#nuevaPassword'));
  });
});
