/**
 * Preguntas de seguridad desde «Mi cuenta» — B1 (identidad 2.0.0, 7.1.1).
 *
 * Se monta sobre la sección real de `perfil.html`. Lo que se prueba: la ruta
 * y el cuerpo del contrato; que la pantalla diga si hay preguntas y cuáles
 * (nunca las respuestas); que el juego no proponga ninguna; que se pidan entre
 * dos y tres, distintas y con su respuesta, con la contraseña actual; y que
 * cada rechazo se explique por su `type`.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

import {
  FalloDePreguntas,
  MAXIMO_DE_PREGUNTAS,
  MINIMO_DE_PREGUNTAS,
  consultarMisPreguntas,
  guardarMisPreguntas,
  leerPreguntas,
  montarPreguntasDeSeguridad,
  rechazoDePreguntas,
  validarPreguntas,
} from './preguntas-seguridad.js';

const MARCADO = readFileSync(new URL('./perfil.html', import.meta.url), 'utf8');
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
const CONFIGURADAS = {
  configuradas: true,
  preguntas: [
    { id: '0f3c9a4e-5d1b-4c8e-9a2f-6b7d8e9f0a1b', texto: '¿Cómo se llamaba tu primera mascota?' },
    { id: '7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55', texto: '¿En qué ciudad naciste?' },
  ],
};
const SIN_CONFIGURAR = { configuradas: false, preguntas: [] };

function respuesta(estado, cuerpo) {
  return Promise.resolve({
    ok: estado >= 200 && estado < 300,
    status: estado,
    text: async () => (cuerpo === undefined ? '' : JSON.stringify(cuerpo)),
  });
}

const tick = () => new Promise((resolver) => setTimeout(resolver, 0));
const seccion = () => document.querySelector('[data-zona="preguntas-seguridad"]');
const en = (selector) => seccion().querySelector(selector);
const bloque = (n) => en(`[data-pregunta="${n}"]`);
const texto = (n) => bloque(n).querySelector('[data-campo="texto"]');
const contestacion = (n) => bloque(n).querySelector('[data-campo="respuesta"]');

async function montar({
  consultar = jest.fn(() => Promise.resolve(SIN_CONFIGURAR)),
  guardar = jest.fn(() => Promise.resolve(CONFIGURADAS)),
} = {}) {
  const vista = montarPreguntasDeSeguridad(document, { consultar, guardar });
  await vista.cargado;
  return { consultar, guardar };
}

function rellenar(pares, clave = 'Clave-Actual-2026') {
  pares.forEach(([pregunta, respuestaEscrita], indice) => {
    texto(indice + 1).value = pregunta;
    contestacion(indice + 1).value = respuestaEscrita;
  });
  en('[name="passwordActualPreguntas"]').value = clave;
}

function enviar() {
  en('form').dispatchEvent(new Event('submit', { cancelable: true }));
}

beforeEach(() => {
  document.documentElement.innerHTML = MARCADO.replace(/<script[\s\S]*?<\/script>/g, '');
});

// ------------------------------------------------------------------ cliente

describe('cliente', () => {
  test('GET a la ruta del contrato, sin caché', async () => {
    const fetchImpl = jest.fn(() => respuesta(200, CONFIGURADAS));
    const resultado = await consultarMisPreguntas({ fetchImpl });
    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/auth/preguntas-seguridad');
    expect(opciones.method).toBeUndefined();
    expect(opciones.cache).toBe('no-store');
    expect(resultado).toEqual(CONFIGURADAS);
  });

  test('PUT con la contraseña actual y las preguntas, y nada más', async () => {
    const fetchImpl = jest.fn(() => respuesta(200, CONFIGURADAS));
    const datos = {
      passwordActual: 'Clave-Actual-2026',
      preguntas: [
        { texto: 'Pregunta uno', respuesta: 'uno' },
        { texto: 'Pregunta dos', respuesta: 'dos' },
      ],
    };
    await guardarMisPreguntas(datos, { fetchImpl });
    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/auth/preguntas-seguridad');
    expect(opciones.method).toBe('PUT');
    expect(JSON.parse(opciones.body)).toEqual(datos);
  });

  test('un rechazo llega con su type', async () => {
    const error = await guardarMisPreguntas(
      { passwordActual: 'x', preguntas: [] },
      {
        fetchImpl: () =>
          respuesta(422, { type: `${ERRORES}contrasena-actual-incorrecta`, status: 422 }),
      },
    ).catch((e) => e);
    expect(error).toBeInstanceOf(FalloDePreguntas);
    expect(error.tipo).toBe('contrasena-actual-incorrecta');
  });

  test('leerPreguntas no pinta una pregunta sin texto', () => {
    expect(leerPreguntas({ configuradas: true, preguntas: [{ id: 'x' }] })).toEqual(SIN_CONFIGURAR);
    expect(leerPreguntas(null)).toEqual(SIN_CONFIGURAR);
  });
});

describe('validarPreguntas', () => {
  test('entre los límites del contrato, distintas y con la contraseña actual', () => {
    expect(
      validarPreguntas(
        [
          { texto: '¿Cómo se llamaba tu primera mascota?', respuesta: 'Firulais' },
          { texto: '¿En qué ciudad naciste?', respuesta: 'Bucaramanga' },
        ],
        'Clave-Actual-2026',
      ),
    ).toEqual([]);
  });

  test('cada fallo dice qué campo y por qué', () => {
    const errores = validarPreguntas(
      [
        { texto: 'Hola', respuesta: 'x' },
        { texto: '¿En qué ciudad naciste?', respuesta: 'Bucaramanga' },
        { texto: '¿en que ciudad  naciste?', respuesta: 'Otra' },
      ],
      '',
    );
    expect(errores).toEqual([
      { indice: 0, campo: 'texto', motivo: expect.stringContaining('al menos 5') },
      { indice: 0, campo: 'respuesta', motivo: expect.stringContaining('al menos 2') },
      // La misma pregunta con otras mayúsculas, tildes o espacios es la misma.
      { indice: 2, campo: 'texto', motivo: 'Esta pregunta ya está en la lista.' },
      { indice: null, campo: 'passwordActual', motivo: expect.stringContaining('contraseña') },
    ]);
  });
});

describe('rechazoDePreguntas', () => {
  test('por type, con el campo a marcar cuando lo hay', () => {
    const clave = rechazoDePreguntas(
      new FalloDePreguntas({ estado: 422, tipo: 'contrasena-actual-incorrecta' }),
    );
    expect(clave.campo).toBe('passwordActual');
    expect(clave.detalle).toContain('intento fallido');

    const invalidas = rechazoDePreguntas(
      new FalloDePreguntas({
        estado: 422,
        tipo: 'preguntas-invalidas',
        detail: null,
        detalle: 'Hacen falta entre 2 y 3 preguntas.',
      }),
    );
    expect(invalidas.titulo).toBe('Revisa las preguntas');
    expect(invalidas.detalle).toBe('Hacen falta entre 2 y 3 preguntas.');

    expect(
      rechazoDePreguntas(new FalloDePreguntas({ estado: 423, tipo: 'cuenta-bloqueada' })).titulo,
    ).toContain('bloqueada');
    expect(rechazoDePreguntas(new TypeError('red')).tono).toBe('error');
    expect(rechazoDePreguntas(new FalloDePreguntas({ estado: 500 })).tono).toBe('error');
  });
});

// -------------------------------------------------------------------- vista

describe('lo que se ve al abrir la pestaña', () => {
  test('sin preguntas: lo dice y ofrece configurarlas; no propone ninguna', async () => {
    await montar();
    expect(en('[data-zona="estado-preguntas"]').textContent).toContain(
      'Todavía no configuraste preguntas de seguridad',
    );
    expect(en('[data-accion="configurar-preguntas"]').hidden).toBe(false);
    expect(en('[data-accion="configurar-preguntas"]').textContent).toBe('Configurar preguntas');
    expect(en('[data-zona="lista-preguntas"]').hidden).toBe(true);
    // Ningún campo de pregunta viene escrito: las escribe la persona.
    for (const n of [1, 2, 3]) {
      expect(texto(n).value).toBe('');
      expect(texto(n).getAttribute('placeholder')).toBeNull();
    }
  });

  test('con preguntas: enseña las preguntas y nunca las respuestas', async () => {
    await montar({ consultar: jest.fn(() => Promise.resolve(CONFIGURADAS)) });
    const lista = en('[data-zona="lista-preguntas"]');
    expect(lista.hidden).toBe(false);
    expect([...lista.querySelectorAll('li')].map((li) => li.textContent)).toEqual(
      CONFIGURADAS.preguntas.map((p) => p.texto),
    );
    expect(en('[data-zona="estado-preguntas"]').textContent).toContain('2 preguntas');
    expect(en('[data-accion="configurar-preguntas"]').textContent).toBe('Cambiar mis preguntas');
  });

  test('si no se pueden consultar, lo dice y deja reintentar', async () => {
    const consultar = jest
      .fn()
      .mockRejectedValueOnce(new FalloDePreguntas({ estado: 503 }))
      .mockResolvedValueOnce(SIN_CONFIGURAR);
    await montar({ consultar });

    expect(en('[data-zona="aviso-preguntas"]').textContent).toContain(
      'No pudimos consultar tus preguntas de seguridad',
    );
    const reintentar = en('[data-accion="reintentar-preguntas"]');
    expect(reintentar.hidden).toBe(false);

    reintentar.click();
    await tick();
    expect(consultar).toHaveBeenCalledTimes(2);
    expect(en('[data-accion="configurar-preguntas"]').hidden).toBe(false);
  });
});

describe('configurarlas', () => {
  test('empieza con dos; se puede añadir una tercera y quitarla', async () => {
    await montar();
    en('[data-accion="configurar-preguntas"]').click();

    expect(en('form').hidden).toBe(false);
    expect(bloque(1).hidden).toBe(false);
    expect(bloque(2).hidden).toBe(false);
    expect(bloque(3).hidden).toBe(true);
    expect(document.activeElement).toBe(texto(1));

    en('[data-accion="anadir-pregunta"]').click();
    expect(bloque(3).hidden).toBe(false);
    expect(en('[data-accion="anadir-pregunta"]').hidden).toBe(true);
    expect(document.activeElement).toBe(texto(3));

    en('[data-accion="quitar-pregunta"]').click();
    expect(bloque(3).hidden).toBe(true);
    expect(en('[data-accion="quitar-pregunta"]').hidden).toBe(true);
    expect(MINIMO_DE_PREGUNTAS).toBe(2);
    expect(MAXIMO_DE_PREGUNTAS).toBe(3);
  });

  test('guarda las visibles, recortadas, y no deja ni la contraseña ni las respuestas escritas', async () => {
    const { guardar } = await montar();
    en('[data-accion="configurar-preguntas"]').click();
    rellenar([
      ['  ¿Cómo se llamaba tu primera mascota?  ', ' Firulais '],
      ['¿En qué ciudad naciste?', 'Bucaramanga'],
    ]);

    enviar();
    await tick();

    expect(guardar).toHaveBeenCalledWith({
      passwordActual: 'Clave-Actual-2026',
      preguntas: [
        { texto: '¿Cómo se llamaba tu primera mascota?', respuesta: 'Firulais' },
        { texto: '¿En qué ciudad naciste?', respuesta: 'Bucaramanga' },
      ],
    });
    expect(en('form').hidden).toBe(true);
    expect(en('[name="passwordActualPreguntas"]').value).toBe('');
    expect(contestacion(1).value).toBe('');
    const aviso = en('[data-zona="aviso-preguntas"]');
    expect(aviso.textContent).toContain('quedaron guardadas');
    expect(aviso.querySelector('[role="status"]')).not.toBeNull();
    expect(en('[data-zona="lista-preguntas"]').querySelectorAll('li')).toHaveLength(2);
  });

  test('lo que se puede comprobar aquí no llega al servidor', async () => {
    const { guardar } = await montar();
    en('[data-accion="configurar-preguntas"]').click();
    rellenar(
      [
        ['¿En qué ciudad naciste?', 'Bucaramanga'],
        ['¿en que ciudad naciste?', 'Otra'],
      ],
      '',
    );

    enviar();
    await tick();

    expect(guardar).not.toHaveBeenCalled();
    expect(texto(2).getAttribute('aria-invalid')).toBe('true');
    expect(en('[name="passwordActualPreguntas"]').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(texto(2));
  });

  test('contrasena-actual-incorrecta: la borra, la marca y deja el foco en ella', async () => {
    const guardar = jest.fn(() =>
      Promise.reject(new FalloDePreguntas({ estado: 422, tipo: 'contrasena-actual-incorrecta' })),
    );
    await montar({ guardar });
    en('[data-accion="configurar-preguntas"]').click();
    rellenar([
      ['¿Cómo se llamaba tu primera mascota?', 'Firulais'],
      ['¿En qué ciudad naciste?', 'Bucaramanga'],
    ]);

    enviar();
    await tick();

    const clave = en('[name="passwordActualPreguntas"]');
    expect(en('form').hidden).toBe(false);
    expect(clave.value).toBe('');
    expect(clave.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(clave);
    expect(en('[data-zona="aviso-preguntas"]').textContent).toContain(
      'La contraseña actual es incorrecta',
    );
  });

  test('preguntas-invalidas: enseña el motivo del servidor', async () => {
    const guardar = jest.fn(() =>
      Promise.reject(
        new FalloDePreguntas({
          estado: 422,
          tipo: 'preguntas-invalidas',
          detalle: 'Hacen falta entre 2 y 3 preguntas.',
        }),
      ),
    );
    await montar({ guardar });
    en('[data-accion="configurar-preguntas"]').click();
    rellenar([
      ['¿Cómo se llamaba tu primera mascota?', 'Firulais'],
      ['¿En qué ciudad naciste?', 'Bucaramanga'],
    ]);

    enviar();
    await tick();

    const aviso = en('[data-zona="aviso-preguntas"]');
    expect(aviso.textContent).toContain('Hacen falta entre 2 y 3 preguntas.');
    expect(document.activeElement).toBe(aviso);
  });

  test('cancelar no llama a nada y vacía lo escrito', async () => {
    const { guardar } = await montar();
    en('[data-accion="configurar-preguntas"]').click();
    rellenar([['¿En qué ciudad naciste?', 'Bucaramanga']]);

    en('[data-accion="cancelar-preguntas"]').click();

    expect(guardar).not.toHaveBeenCalled();
    expect(en('form').hidden).toBe(true);
    expect(texto(1).value).toBe('');
    expect(en('[name="passwordActualPreguntas"]').value).toBe('');
  });
});

test('una vista sin la sección no monta nada', () => {
  document.body.replaceChildren();
  expect(montarPreguntasDeSeguridad(document)).toBeNull();
});
