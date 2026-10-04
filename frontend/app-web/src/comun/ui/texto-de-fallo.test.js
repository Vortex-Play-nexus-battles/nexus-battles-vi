/**
 * UXC-9 — ningún jugador lee «502 Bad Gateway», «NullPointerException» ni
 * el nombre de un servicio interno. Los textos «malos» de estas pruebas son
 * los que llegaban de verdad a pantalla por los clientes de servicio.
 */

import {
  TEXTO_SIN_CONEXION,
  TEXTO_SIN_SERVICIO,
  pareceTextoTecnico,
  respaldoPorEstado,
  textoDeError,
  textoDelServidor,
  tituloDelServidor,
} from './texto-de-fallo.js';

const RESPALDO = 'No pudimos cargar los torneos. Vuelve a intentarlo en un momento.';

describe('pareceTextoTecnico', () => {
  test.each([
    '<html><head><title>502 Bad Gateway</title></head><body>nginx</body></html>',
    '502 Bad Gateway',
    'Internal Server Error',
    'Error 503',
    'HTTP 504',
    'java.lang.NullPointerException at com.nexus.Torneos.inscribir(Torneos.java:88)',
    'TypeError: Failed to fetch',
    'No se pudo contactar con ms-finanzas',
    'srv-inventario no responde',
    'could not execute statement; SQL [n/a]; constraint [uk_equipo]',
    '{"timestamp":"2026-09-25T10:00:00Z","status":500}',
    'Connection refused: localhost:8083',
    'Unexpected error occurred',
    'El equipo 3f2a9c1e-0000-4000-8000-000000000001 no existe',
  ])('%s', (texto) => {
    expect(pareceTextoTecnico(texto)).toBe(true);
  });

  test.each([
    'Esa sala ya no admite más jugadores.',
    'El libro de créditos no responde; nada cambió.',
    'Ya hay un torneo dentro de la ventana de 91 días.',
    'La puja de 105 no supera la oferta vigente más el incremento mínimo (110).',
  ])('%s se deja leer', (texto) => {
    expect(pareceTextoTecnico(texto)).toBe(false);
  });

  test('lo vacío, lo que no es texto y lo interminable no se leen', () => {
    expect(pareceTextoTecnico('')).toBe(true);
    expect(pareceTextoTecnico(null)).toBe(true);
    expect(pareceTextoTecnico({ detail: 'x' })).toBe(true);
    expect(pareceTextoTecnico('a'.repeat(400))).toBe(true);
  });
});

describe('textoDelServidor', () => {
  test('un rechazo escrito para personas se dice tal cual', () => {
    expect(
      textoDelServidor({ detail: 'Esa sala ya no admite más jugadores.' }, 409, RESPALDO),
    ).toBe('Esa sala ya no admite más jugadores.');
  });

  test('un 500 nunca se lee, aunque traiga texto', () => {
    expect(textoDelServidor({ detail: 'Algo muy concreto que pasó' }, 500, RESPALDO)).toBe(
      RESPALDO,
    );
  });

  test('un 503 con problem details legible sí se lee; la página de un proxy no', () => {
    expect(
      textoDelServidor({ detail: 'El libro de créditos no responde; nada cambió.' }, 503, RESPALDO),
    ).toBe('El libro de créditos no responde; nada cambió.');
    expect(textoDelServidor('<html>502 Bad Gateway</html>', 502, RESPALDO)).toBe(RESPALDO);
    expect(textoDelServidor('Servicio caído', 503, RESPALDO)).toBe(RESPALDO);
  });

  test('un detalle técnico en un 4xx tampoco pasa', () => {
    expect(textoDelServidor({ detail: 'IllegalStateException: equipo nulo' }, 409, RESPALDO)).toBe(
      RESPALDO,
    );
    expect(textoDelServidor({ title: 'Bad Request' }, 400, RESPALDO)).toBe(RESPALDO);
  });

  test('sin cuerpo, el respaldo', () => {
    expect(textoDelServidor(null, 404, RESPALDO)).toBe(RESPALDO);
    expect(textoDelServidor(undefined, undefined, RESPALDO)).toBe(RESPALDO);
  });
});

describe('respaldoPorEstado', () => {
  test('sin respuesta habla de la conexión; un 5xx, de que no responde, sin código', () => {
    expect(respaldoPorEstado(0)).toBe(TEXTO_SIN_CONEXION);
    expect(respaldoPorEstado(undefined)).toBe(TEXTO_SIN_CONEXION);
    expect(respaldoPorEstado(502)).toBe(TEXTO_SIN_SERVICIO);
    for (const estado of [401, 403, 404, 409, 422, 502]) {
      expect(respaldoPorEstado(estado)).not.toMatch(/\d{3}/);
    }
  });
});

describe('tituloDelServidor', () => {
  test('los títulos de estado en inglés no se leen; uno escrito para el jugador sí', () => {
    expect(tituloDelServidor({ title: 'Conflict' }, 'No se pudo completar')).toBe(
      'No se pudo completar',
    );
    expect(tituloDelServidor({ title: 'Unprocessable Entity' }, 'R')).toBe('R');
    expect(tituloDelServidor({ title: 'Sala llena' }, 'R')).toBe('Sala llena');
    expect(tituloDelServidor(null, 'R')).toBe('R');
  });
});

describe('textoDeError', () => {
  test('un error de red o de JSON no se lee; uno de negocio sí', () => {
    expect(textoDeError(new TypeError('Failed to fetch'), 'R')).toBe('R');
    expect(textoDeError(new SyntaxError('Unexpected token < in JSON at position 0'), 'R')).toBe(
      'R',
    );
    expect(textoDeError({ detalle: '', message: 'Esa sala ya empezó.' }, 'R')).toBe(
      'Esa sala ya empezó.',
    );
    expect(textoDeError(null, 'R')).toBe('R');
  });
});
