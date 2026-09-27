/**
 * Cliente de moderacion — RF-COM-005/006/008, contrato 1.3.0, y lo que anade
 * la 1.5.0 (B3, 7.3.3): EDITAR, MARCAR, DESMARCAR, el filtro `marcado` y las
 * imagenes privadas de un comentario en revision.
 *
 * Lo que se afirma aqui son las dos cosas que un cambio descuidado rompe sin
 * que nada mas se entere: la RUTA (que no puede volver al prefijo ajeno) y
 * que el cuerpo NO nombra a quien actua.
 */

import { jest } from '@jest/globals';

import {
  ACCIONES,
  ACCIONES_INTERNAS,
  ACCIONES_SIN_CAMBIO_DE_ESTADO,
  CATEGORIAS,
  FILTROS_DE_COLA,
  accionesDesde,
  consultarCola,
  consultarDetalle,
  imagenParaModeracion,
  reportarComentario,
  resolverComentario,
  rutaDeModeracion,
  ErrorDeApi,
} from './cliente-moderacion.js';

function respuesta(cuerpo, { ok = true, status = 200 } = {}) {
  return { ok, status, json: async () => cuerpo };
}

describe('las rutas', () => {
  test('la cola NO cuelga de /api/v1/moderacion: ese prefijo es de metricas', () => {
    // Si alguien "arregla" la ruta a la que parece natural, el borde manda
    // la peticion a metricas-plataforma y la vista da 404 en el navegador
    // aunque todas las pruebas de rodaja sigan verdes. Esta linea es el
    // unico sitio donde eso se nota antes.
    expect(rutaDeModeracion()).toBe('/api/v1/comentarios/moderacion');
    expect(rutaDeModeracion().startsWith('/api/v1/moderacion')).toBe(false);
  });

  test('reportar cuelga del comentario, que es donde esta el jugador', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta({ id: 'r-1' }, { status: 201 }));
    await reportarComentario('prod 1', 'com/1', { categoria: 'SPAM' }, { fetchImpl });

    const [url] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/products/prod%201/comments/com%2F1/reportes');
  });

  test('la cola lleva el filtro y la paginacion como parametros', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta({ entradas: [] }));
    await consultarCola({ productoId: 'p-1', pagina: 2, tamano: 50 }, { fetchImpl });

    const [url] = fetchImpl.mock.calls[0];
    expect(url).toContain('pagina=2');
    expect(url).toContain('tamano=50');
    expect(url).toContain('productoId=p-1');
  });

  test('sin producto no se manda el filtro vacio: pedir toda la cola es lo normal', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta({ entradas: [] }));
    await consultarCola({}, { fetchImpl });
    expect(fetchImpl.mock.calls[0][0]).not.toContain('productoId');
    expect(fetchImpl.mock.calls[0][0]).not.toContain('marcado');
  });

  test('1.5.0: el filtro marcado viaja solo cuando es true o false', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta({ entradas: [] }));
    await consultarCola({ marcado: true }, { fetchImpl });
    await consultarCola({ marcado: false }, { fetchImpl });
    await consultarCola({ marcado: null }, { fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toContain('marcado=true');
    expect(fetchImpl.mock.calls[1][0]).toContain('marcado=false');
    expect(fetchImpl.mock.calls[2][0]).not.toContain('marcado');
    expect(FILTROS_DE_COLA.map((f) => f.marcado)).toEqual([null, true, false]);
  });

  test('el detalle y la decision cuelgan del comentario dentro de la cola', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta({}));
    await consultarDetalle('com-1', { fetchImpl });
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/comentarios/moderacion/com-1');

    await resolverComentario('com-1', { accion: 'OCULTAR', motivo: 'x' }, { fetchImpl });
    expect(fetchImpl.mock.calls[1][0]).toBe('/api/v1/comentarios/moderacion/com-1/decision');
  });
});

describe('quien actua no viaja en el cuerpo', () => {
  test('el reporte solo lleva categoria y descripcion', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta({}, { status: 201 }));
    await reportarComentario(
      'p-1',
      'com-1',
      { categoria: 'ACOSO', descripcion: 'Insulta' },
      { fetchImpl },
    );

    const cuerpo = JSON.parse(fetchImpl.mock.calls[0][1].body);
    expect(cuerpo).toEqual({ categoria: 'ACOSO', descripcion: 'Insulta' });
    // Si el reportante viajara aqui, cualquiera podria dejar reportes a
    // nombre de otra persona y el limite por usuario no significaria nada.
    expect(Object.keys(cuerpo)).not.toContain('reportanteId');
  });

  test('la decision solo lleva accion y motivo', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta({}));
    await resolverComentario('com-1', { accion: 'APROBAR', motivo: 'Es opinion' }, { fetchImpl });

    const cuerpo = JSON.parse(fetchImpl.mock.calls[0][1].body);
    expect(cuerpo).toEqual({ accion: 'APROBAR', motivo: 'Es opinion' });
    expect(Object.keys(cuerpo)).not.toContain('moderadorId');
  });

  test('1.5.0: EDITAR lleva ademas el texto nuevo, y nada de quien edita', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta({}));
    await resolverComentario(
      'com-1',
      { accion: 'EDITAR', motivo: 'Afirmacion falsa', textoNuevo: 'Texto corregido' },
      { fetchImpl },
    );

    const cuerpo = JSON.parse(fetchImpl.mock.calls[0][1].body);
    expect(cuerpo).toEqual({
      accion: 'EDITAR',
      motivo: 'Afirmacion falsa',
      textoNuevo: 'Texto corregido',
    });
    expect(Object.keys(cuerpo)).not.toContain('moderadorId');
  });
});

describe('imagenes de un comentario en revision (1.5.0)', () => {
  test('se piden con fetch (el interceptor pone la sesion) y llegan como Blob', async () => {
    const blob = new Blob(['png'], { type: 'image/png' });
    const fetchImpl = jest
      .fn()
      .mockResolvedValue({ ok: true, status: 200, blob: async () => blob });

    await expect(imagenParaModeracion('img/1', { fetchImpl })).resolves.toBe(blob);
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/comentarios/imagenes/img%2F1');
  });

  test('404 (no existe o no se puede ver) es null, no un error', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(null, { ok: false, status: 404 }));
    await expect(imagenParaModeracion('x', { fetchImpl })).resolves.toBeNull();
  });

  test('otro fallo llega como ErrorDeApi', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta({}, { ok: false, status: 503 }));
    await expect(imagenParaModeracion('x', { fetchImpl })).rejects.toMatchObject({
      name: 'ErrorDeApi',
      estado: 503,
    });
  });
});

describe('errores', () => {
  test('un 409 llega como ErrorDeApi con su motivo estable', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      respuesta(
        {
          status: 409,
          title: 'Reporte duplicado',
          detail: 'Ya lo reportaste',
          motivo: 'REPORTE_DUPLICADO',
        },
        { ok: false, status: 409 },
      ),
    );

    await expect(
      reportarComentario('p-1', 'com-1', { categoria: 'SPAM' }, { fetchImpl }),
    ).rejects.toMatchObject({ estado: 409, motivo: 'REPORTE_DUPLICADO' });
  });

  test('un cuerpo que no es JSON no se traga el error: sigue siendo ErrorDeApi', async () => {
    const fetchImpl = jest.fn().mockResolvedValue({
      ok: false,
      status: 502,
      json: async () => {
        throw new Error('no es json');
      },
    });

    const fallo = await consultarCola({}, { fetchImpl }).catch((e) => e);
    expect(fallo).toBeInstanceOf(ErrorDeApi);
    expect(fallo.estado).toBe(502);
  });
});

describe('la tabla de acciones', () => {
  test('es un espejo de AccionDeModeracion del servicio (1.5.0: EDITAR, MARCAR, DESMARCAR)', () => {
    expect(ACCIONES.map((a) => a.valor)).toEqual([
      'APROBAR',
      'OCULTAR',
      'ELIMINAR',
      'RESTAURAR',
      'EDITAR',
      'MARCAR',
      'DESMARCAR',
    ]);
    expect(accionesDesde('EN_REVISION').map((a) => a.valor)).toEqual([
      'APROBAR',
      'OCULTAR',
      'ELIMINAR',
      'EDITAR',
      'MARCAR',
    ]);
    expect(accionesDesde('PUBLICADO').map((a) => a.valor)).toEqual([
      'OCULTAR',
      'ELIMINAR',
      'EDITAR',
      'MARCAR',
    ]);
    expect(accionesDesde('OCULTO').map((a) => a.valor)).toEqual([
      'ELIMINAR',
      'RESTAURAR',
      'EDITAR',
      'MARCAR',
    ]);
    // ELIMINADO es terminal: ninguna accion sale de ahi, ni editar ni marcar.
    expect(accionesDesde('ELIMINADO')).toEqual([]);
    expect(accionesDesde('ELIMINADO', true)).toEqual([]);
  });

  test('la marca decide entre MARCAR y DESMARCAR: nunca se ofrece la que daria 409', () => {
    const conMarca = accionesDesde('PUBLICADO', true).map((a) => a.valor);
    expect(conMarca).toContain('DESMARCAR');
    expect(conMarca).not.toContain('MARCAR');
    const sinMarca = accionesDesde('PUBLICADO', false).map((a) => a.valor);
    expect(sinMarca).toContain('MARCAR');
    expect(sinMarca).not.toContain('DESMARCAR');
  });

  test('EDITAR, MARCAR y DESMARCAR no cambian el estado; las dos ultimas no se avisan', () => {
    expect(ACCIONES_SIN_CAMBIO_DE_ESTADO).toEqual(['EDITAR', 'MARCAR', 'DESMARCAR']);
    expect(ACCIONES_INTERNAS).toEqual(['MARCAR', 'DESMARCAR']);
  });

  test('las categorias son las seis del contrato, sin inventos', () => {
    expect(CATEGORIAS.map((c) => c.valor)).toEqual([
      'CONTENIDO_OFENSIVO',
      'ACOSO',
      'SPAM',
      'INFORMACION_FALSA',
      'CONTENIDO_INAPROPIADO',
      'VIOLACION_DE_DERECHOS',
    ]);
  });
});
