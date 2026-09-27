/**
 * HU-COM-001 y B3 — Cliente HTTP de comentarios, calificaciones e imágenes.
 *
 * Se prueba contra la forma exacta de `contracts/openapi/comentarios.yaml`
 * (1.5.0): rutas, cuerpos, los dos éxitos distintos de publicar (201 y 202),
 * la paginación del hilo, la calificación separada y la subida de imágenes, y
 * los rechazos con `type` y `motivo`. Si el contrato cambia, estas pruebas
 * deben fallar.
 */

import { jest } from '@jest/globals';

import {
  publicarComentario,
  consultarHilo,
  eliminarComentario,
  calificar,
  resumenDeCalificaciones,
  miCalificacion,
  subirImagen,
  rutaDeComentarios,
  rutaDeCalificacion,
  urlDeImagen,
  ErrorDeApi,
  MOTIVO,
  TIPO,
  ESTADO,
  TAMANO_MAXIMO_DE_IMAGEN,
} from './cliente-comentarios.js';

function respuesta(estado, cuerpo, tipo = 'application/json') {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    headers: { get: () => tipo },
    json: async () => cuerpo,
  };
}

const IMAGEN = '3f1c2b4a-1111-4222-8333-944455566677';

const CUERPO = {
  texto: 'Buena espada',
  imagenes: [IMAGEN],
  estrellas: 4,
};

const RESUMEN = {
  productoId: 'prod-1',
  promedio: 4.5,
  total: 2,
  distribucion: { 1: 0, 2: 0, 3: 0, 4: 1, 5: 1 },
};

afterEach(() => {
  document.head.innerHTML = '';
});

describe('rutas', () => {
  test('apuntan a los recursos del contrato, en el mismo origen por omisión', () => {
    expect(rutaDeComentarios('prod-1')).toBe('/api/v1/products/prod-1/comments');
    expect(rutaDeCalificacion('prod-1')).toBe('/api/v1/products/prod-1/rating');
    expect(urlDeImagen(IMAGEN)).toBe(`/api/v1/comentarios/imagenes/${IMAGEN}`);
  });

  test('respetan la base declarada por la página y escapan el identificador', () => {
    document.head.innerHTML = '<meta name="nexus-api-base" content="http://127.0.0.1:8081/" />';
    expect(rutaDeComentarios('a/b')).toBe('http://127.0.0.1:8081/api/v1/products/a%2Fb/comments');
    expect(rutaDeCalificacion('a b')).toBe('http://127.0.0.1:8081/api/v1/products/a%20b/rating');
    expect(urlDeImagen('../x')).toBe('http://127.0.0.1:8081/api/v1/comentarios/imagenes/..%2Fx');
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

  test('distingue la retención del filtro (202) de la publicación', async () => {
    const comentario = { id: 'c-2', estado: 'EN_REVISION', ...CUERPO };
    const fetchImpl = jest.fn(async () => respuesta(202, comentario));

    const resultado = await publicarComentario('prod-1', CUERPO, { fetchImpl });

    expect(resultado.estado).toBe(ESTADO.EN_REVISION);
  });

  test('si el cuerpo no trae estado, lo deduce del código HTTP', async () => {
    const fetchImpl = jest.fn(async () => respuesta(202, { id: 'c-3' }));

    const resultado = await publicarComentario('prod-1', CUERPO, { fetchImpl });

    expect(resultado.estado).toBe(ESTADO.EN_REVISION);
  });

  test('la segunda calificación no es un error: entra y la respuesta lo dice', async () => {
    const fetchImpl = jest.fn(async () =>
      respuesta(201, {
        id: 'c-4',
        estado: 'PUBLICADO',
        estrellas: 5,
        calificacionDescartada: true,
      }),
    );

    const { comentario } = await publicarComentario('prod-1', CUERPO, { fetchImpl });

    expect(comentario.calificacionDescartada).toBe(true);
    expect(comentario.estrellas).toBe(5);
  });

  test('un 403 por silencio llega como ErrorDeApi con su tipo y su motivo', async () => {
    const problema = {
      type: TIPO.AUTOR_SILENCIADO,
      title: 'Tu cuenta no puede publicar ahora',
      status: 403,
      detail: 'Tienes una sanción activa.',
      motivo: 'AUTOR_SILENCIADO',
    };
    const fetchImpl = jest.fn(async () => respuesta(403, problema, 'application/problem+json'));

    await expect(publicarComentario('prod-1', CUERPO, { fetchImpl })).rejects.toMatchObject({
      name: 'ErrorDeApi',
      estado: 403,
      tipo: TIPO.AUTOR_SILENCIADO,
      motivo: MOTIVO.AUTOR_SILENCIADO,
      titulo: 'Tu cuenta no puede publicar ahora',
      detalle: 'Tienes una sanción activa.',
      esDeFormulario: false,
    });
  });

  test('un producto que no existe es 404 producto-inexistente; sin catálogo, 503', async () => {
    const inexistente = jest.fn(async () =>
      respuesta(404, { type: TIPO.PRODUCTO_INEXISTENTE, status: 404 }, 'application/problem+json'),
    );
    await expect(
      publicarComentario('prod-x', CUERPO, { fetchImpl: inexistente }),
    ).rejects.toMatchObject({ estado: 404, tipo: TIPO.PRODUCTO_INEXISTENTE });

    const caido = jest.fn(async () =>
      respuesta(
        503,
        { type: TIPO.CATALOGO_NO_DISPONIBLE, status: 503 },
        'application/problem+json',
      ),
    );
    await expect(publicarComentario('prod-1', CUERPO, { fetchImpl: caido })).rejects.toMatchObject({
      estado: 503,
      tipo: TIPO.CATALOGO_NO_DISPONIBLE,
    });
  });

  test('una imagen que no se puede adjuntar es 400 imagenes-no-validas', async () => {
    const fetchImpl = jest.fn(async () =>
      respuesta(400, { type: TIPO.IMAGENES_NO_VALIDAS, status: 400 }, 'application/problem+json'),
    );
    await expect(publicarComentario('prod-1', CUERPO, { fetchImpl })).rejects.toMatchObject({
      estado: 400,
      tipo: TIPO.IMAGENES_NO_VALIDAS,
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
      tipo: null,
    });
  });
});

describe('consultarHilo', () => {
  const HILO = {
    productoId: 'prod-1',
    comentarios: [{ id: 'c-2' }, { id: 'c-1' }],
    pagina: 1,
    tamano: 2,
    total: 5,
    totalPaginas: 3,
    calificacionPromedio: 4.5,
    totalCalificaciones: 2,
  };

  test('sin página ni tamaño no manda consulta: el servicio aplica los suyos', async () => {
    const fetchImpl = jest.fn(async () => respuesta(200, HILO));

    const hilo = await consultarHilo('prod-1', { fetchImpl });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/products/prod-1/comments', {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
    expect(hilo).toEqual(HILO);
  });

  test('con página y tamaño los manda como pagina y tamano', async () => {
    const fetchImpl = jest.fn(async () => respuesta(200, HILO));

    await consultarHilo('prod-1', { pagina: 1, tamano: 2, fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/products/prod-1/comments?pagina=1&tamano=2');
  });

  test('solo manda lo que es un entero', async () => {
    const fetchImpl = jest.fn(async () => respuesta(200, HILO));

    await consultarHilo('prod-1', { pagina: 0, tamano: '16', fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/products/prod-1/comments?pagina=0');
  });

  test('un error del servicio llega como ErrorDeApi', async () => {
    const fetchImpl = jest.fn(async () =>
      respuesta(400, { title: 'pagina negativa', status: 400 }, 'application/problem+json'),
    );

    await expect(consultarHilo('prod-1', { pagina: -1, fetchImpl })).rejects.toMatchObject({
      name: 'ErrorDeApi',
      estado: 400,
    });
  });
});

describe('eliminarComentario', () => {
  test('manda DELETE al comentario y no devuelve nada si sale bien (204)', async () => {
    const fetchImpl = jest.fn(async () => ({ ok: true, status: 204 }));

    await expect(eliminarComentario('prod-1', 'c/1', { fetchImpl })).resolves.toBeUndefined();

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/products/prod-1/comments/c%2F1', {
      method: 'DELETE',
      headers: { Accept: 'application/problem+json' },
    });
  });

  test('uno ajeno es 403 con su tipo', async () => {
    const tipo = 'https://nexusbattles.local/errores/comentario-ajeno';
    const fetchImpl = jest.fn(async () => respuesta(403, { type: tipo, status: 403 }));

    await expect(eliminarComentario('prod-1', 'c-1', { fetchImpl })).rejects.toMatchObject({
      estado: 403,
      tipo,
    });
  });
});

describe('calificar', () => {
  test('manda las estrellas por POST y devuelve la calificación con el resumen actualizado', async () => {
    const calificacion = {
      productoId: 'prod-1',
      estrellas: 4,
      fecha: '2026-09-25T12:00:00Z',
      resumen: RESUMEN,
    };
    const fetchImpl = jest.fn(async () => respuesta(201, calificacion));

    const resultado = await calificar('prod-1', 4, { fetchImpl });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/products/prod-1/rating', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ estrellas: 4 }),
    });
    expect(resultado).toEqual(calificacion);
  });

  test('la segunda vez es 409 ya-calificado con motivo CALIFICACION_DUPLICADA', async () => {
    const fetchImpl = jest.fn(async () =>
      respuesta(
        409,
        { type: TIPO.YA_CALIFICADO, status: 409, motivo: 'CALIFICACION_DUPLICADA' },
        'application/problem+json',
      ),
    );

    await expect(calificar('prod-1', 5, { fetchImpl })).rejects.toMatchObject({
      name: 'ErrorDeApi',
      estado: 409,
      tipo: TIPO.YA_CALIFICADO,
      motivo: MOTIVO.CALIFICACION_DUPLICADA,
    });
  });
});

describe('resumenDeCalificaciones', () => {
  test('lee el resumen público del producto', async () => {
    const fetchImpl = jest.fn(async () => respuesta(200, RESUMEN));

    await expect(resumenDeCalificaciones('prod-1', { fetchImpl })).resolves.toEqual(RESUMEN);
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/products/prod-1/rating', {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
  });

  test('sin calificaciones el promedio es null, no 0', async () => {
    const vacio = { ...RESUMEN, promedio: null, total: 0 };
    const fetchImpl = jest.fn(async () => respuesta(200, vacio));

    const resumen = await resumenDeCalificaciones('prod-1', { fetchImpl });

    expect(resumen.promedio).toBeNull();
  });

  test('un producto inexistente es 404', async () => {
    const fetchImpl = jest.fn(async () =>
      respuesta(404, { type: TIPO.PRODUCTO_INEXISTENTE, status: 404 }),
    );

    await expect(resumenDeCalificaciones('x', { fetchImpl })).rejects.toMatchObject({
      estado: 404,
      tipo: TIPO.PRODUCTO_INEXISTENTE,
    });
  });
});

describe('miCalificacion', () => {
  test('devuelve la propia', async () => {
    const propia = { productoId: 'prod-1', estrellas: 3, fecha: '2026-09-25T12:00:00Z' };
    const fetchImpl = jest.fn(async () => respuesta(200, propia));

    await expect(miCalificacion('prod-1', { fetchImpl })).resolves.toEqual(propia);
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/products/prod-1/rating/mia');
  });

  test('el 404 no es un error: es que todavía no calificó, y devuelve null', async () => {
    const fetchImpl = jest.fn(async () =>
      respuesta(404, { type: TIPO.CALIFICACION_NO_ENCONTRADA, status: 404 }),
    );

    await expect(miCalificacion('prod-1', { fetchImpl })).resolves.toBeNull();
  });

  test('sin sesión (401) sí es un error', async () => {
    const fetchImpl = jest.fn(async () => respuesta(401, null));

    await expect(miCalificacion('prod-1', { fetchImpl })).rejects.toMatchObject({ estado: 401 });
  });
});

describe('subirImagen', () => {
  const PNG = new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], { type: 'image/png' });

  test('la manda en el campo archivo de un multipart y devuelve su id y su url', async () => {
    const subida = {
      id: IMAGEN,
      tipo: 'image/png',
      tamano: 4,
      url: `/api/v1/comentarios/imagenes/${IMAGEN}`,
    };
    const fetchImpl = jest.fn(async () => respuesta(201, subida));

    const resultado = await subirImagen(PNG, { fetchImpl });

    expect(resultado).toEqual(subida);
    const [ruta, opciones] = fetchImpl.mock.calls[0];
    expect(ruta).toBe('/api/v1/comentarios/imagenes');
    expect(opciones.method).toBe('POST');
    expect(opciones.body).toBeInstanceOf(FormData);
    expect(opciones.body.get('archivo')).toBeInstanceOf(Blob);
    // La frontera del multipart la pone el navegador: aquí no se escribe.
    expect(opciones.headers['Content-Type']).toBeUndefined();
  });

  test('más de 2 MB se rechaza sin subirla, con el mismo error que daría el servicio', async () => {
    const fetchImpl = jest.fn();
    const grande = { size: TAMANO_MAXIMO_DE_IMAGEN + 1 };

    await expect(subirImagen(grande, { fetchImpl })).rejects.toMatchObject({
      name: 'ErrorDeApi',
      estado: 413,
      tipo: TIPO.IMAGEN_DEMASIADO_GRANDE,
    });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  test('sin archivo no se sube nada: 400 imagen-ausente', async () => {
    const fetchImpl = jest.fn();

    await expect(subirImagen(null, { fetchImpl })).rejects.toMatchObject({
      estado: 400,
      tipo: TIPO.IMAGEN_AUSENTE,
    });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  test('una imagen que no es JPEG, PNG ni WebP es 415 con su motivo', async () => {
    const fetchImpl = jest.fn(async () =>
      respuesta(
        415,
        { type: TIPO.IMAGEN_NO_ADMITIDA, status: 415, motivo: 'FORMATO_DE_IMAGEN_NO_ADMITIDO' },
        'application/problem+json',
      ),
    );

    await expect(subirImagen(PNG, { fetchImpl })).rejects.toMatchObject({
      estado: 415,
      motivo: MOTIVO.FORMATO_DE_IMAGEN_NO_ADMITIDO,
    });
  });
});
