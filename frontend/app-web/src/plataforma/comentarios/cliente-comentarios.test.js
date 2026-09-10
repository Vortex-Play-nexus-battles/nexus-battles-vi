/**
 * HU-COM-001 — Cliente HTTP de publicacion de comentarios.
 *
 * Se prueba contra la forma exacta de `contracts/openapi/comentarios.yaml`:
 * ruta, cuerpo, los dos exitos distintos (201 y 202) y los rechazos con
 * `motivo`. Si el contrato cambia, estas pruebas deben fallar.
 */

import { jest } from '@jest/globals';

import {
  publicarComentario,
  rutaDeComentarios,
  ErrorDeApi,
  MOTIVO,
  ESTADO,
} from './cliente-comentarios.js';

function respuesta(estado, cuerpo, tipo = 'application/json') {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    headers: { get: () => tipo },
    json: async () => cuerpo,
  };
}

const CUERPO = {
  autorId: 'jugador-7',
  apodoAutor: 'Simon_P',
  texto: 'Buena espada',
  imagenes: ['espada.png'],
  estrellas: 4,
};

describe('rutaDeComentarios', () => {
  test('apunta al recurso del contrato, en el mismo origen por omision', () => {
    document.head.innerHTML = '';
    expect(rutaDeComentarios('prod-1')).toBe('/api/v1/products/prod-1/comments');
  });

  test('respeta la base declarada por la pagina y escapa el identificador', () => {
    document.head.innerHTML = '<meta name="nexus-api-base" content="http://127.0.0.1:8081/" />';
    expect(rutaDeComentarios('a/b')).toBe('http://127.0.0.1:8081/api/v1/products/a%2Fb/comments');
    document.head.innerHTML = '';
  });
});

describe('publicarComentario', () => {
  test('manda el cuerpo del contrato por POST y devuelve el comentario publicado (201)', async () => {
    const comentario = {
      id: 'c-1',
      estado: 'PUBLICADO',
      ...CUERPO,
      fechaPublicacion: '2026-09-09T10:00:00Z',
    };
    const fetchImpl = jest.fn(async () => respuesta(201, comentario));

    const resultado = await publicarComentario('prod-1', CUERPO, { fetchImpl });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/products/prod-1/comments', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(CUERPO),
    });
    expect(resultado).toEqual({ comentario, estado: ESTADO.PUBLICADO });
  });

  test('distingue la retencion del filtro (202) de la publicacion', async () => {
    const comentario = { id: 'c-2', estado: 'EN_REVISION', ...CUERPO };
    const fetchImpl = jest.fn(async () => respuesta(202, comentario));

    const resultado = await publicarComentario('prod-1', CUERPO, { fetchImpl });

    expect(resultado.estado).toBe(ESTADO.EN_REVISION);
  });

  test('si el cuerpo no trae estado, lo deduce del codigo HTTP', async () => {
    const fetchImpl = jest.fn(async () => respuesta(202, { id: 'c-3' }));

    const resultado = await publicarComentario('prod-1', CUERPO, { fetchImpl });

    expect(resultado.estado).toBe(ESTADO.EN_REVISION);
  });

  test('un 403 por silencio llega como ErrorDeApi con su motivo', async () => {
    const problema = {
      type: 'https://nexusbattles.local/errores/autor-silenciado',
      title: 'No puedes publicar',
      status: 403,
      detail: 'Tienes una sancion de silencio activa.',
      motivo: 'AUTOR_SILENCIADO',
    };
    const fetchImpl = jest.fn(async () => respuesta(403, problema, 'application/problem+json'));

    await expect(publicarComentario('prod-1', CUERPO, { fetchImpl })).rejects.toMatchObject({
      name: 'ErrorDeApi',
      estado: 403,
      motivo: MOTIVO.AUTOR_SILENCIADO,
      titulo: 'No puedes publicar',
      detalle: 'Tienes una sancion de silencio activa.',
      esDeFormulario: false,
    });
  });

  test('un 422 por formato de imagen conserva el motivo del contrato', async () => {
    const problema = {
      type: 'https://nexusbattles.local/errores/formato-de-imagen',
      title: 'Imagen no admitida',
      status: 422,
      detail: 'Solo se admiten png y jpg.',
      motivo: 'FORMATO_DE_IMAGEN_NO_ADMITIDO',
    };
    const fetchImpl = jest.fn(async () => respuesta(422, problema, 'application/problem+json'));

    await expect(publicarComentario('prod-1', CUERPO, { fetchImpl })).rejects.toMatchObject({
      motivo: MOTIVO.FORMATO_DE_IMAGEN_NO_ADMITIDO,
      estado: 422,
    });
  });

  test('un 400 con errores[] se reconoce como error de formulario', async () => {
    const problema = {
      type: 'https://nexusbattles.local/errores/validacion',
      title: 'Datos invalidos',
      status: 400,
      errores: [{ campo: 'texto', mensaje: 'No puede estar vacio.' }],
    };
    const fetchImpl = jest.fn(async () => respuesta(400, problema, 'application/problem+json'));

    const error = await publicarComentario('prod-1', CUERPO, { fetchImpl }).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorDeApi);
    expect(error.esDeFormulario).toBe(true);
    expect(error.errores).toEqual([{ campo: 'texto', mensaje: 'No puede estar vacio.' }]);
  });

  test('si el cuerpo del error no es JSON, el error sigue siendo un ErrorDeApi con el estado real', async () => {
    const fetchImpl = jest.fn(async () => ({
      ok: false,
      status: 502,
      json: async () => {
        throw new SyntaxError('no es JSON');
      },
    }));

    await expect(publicarComentario('prod-1', CUERPO, { fetchImpl })).rejects.toMatchObject({
      name: 'ErrorDeApi',
      estado: 502,
      motivo: null,
    });
  });
});
