/**
 * B9 — el adaptador HTTP de misiones contra `misiones.yaml` 1.0.0, y la
 * pregunta previa que decide si la vista tiene servicio o pinta su estado
 * honesto.
 *
 * Las respuestas imitan lo que devuelve el servicio (y el borde cuando el
 * servicio no está): cuerpos con la forma del contrato y problem details con
 * su `detail`.
 */

import { jest } from '@jest/globals';

import {
  ESPERA_DE_LA_SONDA_MS,
  FUENTE_SIN_SERVICIO,
  fuenteDeMisiones,
  fuenteHttpDeMisiones,
} from './fuente-misiones.js';

/** Una respuesta como la de `fetch`, sin depender de `Response` (jsdom no lo trae). */
function respuesta(status, cuerpo) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      if (cuerpo === undefined) {
        throw new SyntaxError('Unexpected end of JSON input');
      }
      return cuerpo;
    },
  };
}

function problema(status, detail, extra = {}) {
  return respuesta(status, {
    type: 'https://nexusbattles.local/errores/regla-de-mision',
    title: 'No se puede hacer ahora',
    status,
    detail,
    ...extra,
  });
}

const DESTACADA = {
  id: 'templo-olvidado',
  nombre: 'El Templo Olvidado',
  categoria: 'HISTORIA',
  descripcionBreve: 'Un antiguo templo en el Bosque Sombrío.',
  dificultad: 'NORMAL',
  duracionHoras: 12,
  estado: 'DISPONIBLE',
};

/** Quita el `fetch` global (si lo hay) y devuelve cómo ponerlo de vuelta. */
function quitarFetch() {
  const antes = globalThis.fetch;
  delete globalThis.fetch;
  return () => {
    if (antes) {
      globalThis.fetch = antes;
    }
  };
}

/** Un `fetch` que contesta según la ruta y el método, y apunta cada llamada. */
function servidor(rutas) {
  return jest.fn(async (url, opciones = {}) => {
    const clave = `${opciones.method ?? 'GET'} ${url}`;
    const contestar = rutas[clave];
    if (!contestar) {
      throw new Error(`ruta no prevista en la prueba: ${clave}`);
    }
    return typeof contestar === 'function' ? contestar(opciones) : contestar;
  });
}

describe('la pregunta previa: ¿responde el servicio de misiones?', () => {
  test('si contesta, la fuente es el adaptador y la primera consulta de destacadas no se repite', async () => {
    const fetchImpl = servidor({ 'GET /api/v1/misiones/destacadas': respuesta(200, [DESTACADA]) });

    const fuente = await fuenteDeMisiones({ fetchImpl });

    expect(fuente.disponible).toBe(true);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/misiones/destacadas');
    expect(fetchImpl.mock.calls[0][1].headers).toEqual({ Accept: 'application/json' });

    // El banner pide las destacadas justo al montar: son las de la pregunta.
    expect(await fuente.destacadas()).toEqual([DESTACADA]);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    // La segunda vez ya van al servidor.
    await fuente.destacadas();
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  test.each([
    ['el borde da 502 (el servicio no está desplegado)', respuesta(502, undefined)],
    [
      'el borde no conoce la ruta (404 de «ruta sin servicio»)',
      respuesta(404, { title: 'Ruta sin servicio en el borde', status: 404 }),
    ],
    ['el servicio falla (500)', problema(500, 'fallo interno')],
    ['la sesión ya no vale (401)', respuesta(401, undefined)],
    ['un 200 que no es JSON (una página en vez del servicio)', respuesta(200, undefined)],
    ['un 200 que no es una lista', respuesta(200, { misiones: [] })],
  ])('si %s, la fuente es la de sin servicio', async (_caso, contestacion) => {
    const fetchImpl = jest.fn(async () => contestacion);

    const fuente = await fuenteDeMisiones({ fetchImpl });

    expect(fuente).toBe(FUENTE_SIN_SERVICIO);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  test('sin red, la fuente es la de sin servicio y no se lanza nada', async () => {
    const fetchImpl = jest.fn(async () => {
      throw new TypeError('Failed to fetch');
    });

    await expect(fuenteDeMisiones({ fetchImpl })).resolves.toBe(FUENTE_SIN_SERVICIO);
  });

  test('si no contesta a tiempo, se corta la petición y la vista no espera más', async () => {
    let senal;
    const fetchImpl = jest.fn((_url, { signal }) => {
      senal = signal;
      return new Promise(() => {});
    });

    const fuente = await fuenteDeMisiones({ fetchImpl, esperaMs: 10 });

    expect(fuente).toBe(FUENTE_SIN_SERVICIO);
    expect(senal.aborted).toBe(true);
    expect(ESPERA_DE_LA_SONDA_MS).toBeGreaterThanOrEqual(1000);
  });

  test('sin `fetch` en el entorno no hay a quién preguntar: sin servicio y sin intentarlo', async () => {
    const restaurar = quitarFetch();
    try {
      await expect(fuenteDeMisiones()).resolves.toBe(FUENTE_SIN_SERVICIO);
    } finally {
      restaurar();
    }
  });
});

describe('el adaptador HTTP (misiones.yaml 1.0.0)', () => {
  test('tablón: la categoría siempre; los filtros y la página solo si hay', async () => {
    const pagina = { misiones: [DESTACADA], total: 1, pagina: 0, totalPaginas: 1, tamanio: 16 };
    const fetchImpl = jest.fn(async () => respuesta(200, pagina));
    const fuente = fuenteHttpDeMisiones({ fetchImpl });

    expect(await fuente.tablero({ categoria: 'HISTORIA', dificultad: '', estado: '' })).toEqual(
      pagina,
    );
    await fuente.tablero({
      categoria: 'EXPLORACION',
      dificultad: 'DIFICIL',
      estado: 'EN_PROGRESO',
      duracion: 'MAS_DE_24',
      pagina: 2,
    });

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/misiones?categoria=HISTORIA');
    expect(fetchImpl.mock.calls[1][0]).toBe(
      '/api/v1/misiones?categoria=EXPLORACION&dificultad=DIFICIL&estado=EN_PROGRESO&duracion=MAS_DE_24&pagina=2',
    );
    expect(fetchImpl.mock.calls[1][1]).toEqual({
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
  });

  test('detalle, en curso, historial y reporte, con los identificadores escapados', async () => {
    const fetchImpl = servidor({
      'GET /api/v1/misiones/templo%2Folvidado': respuesta(200, { id: 'templo/olvidado' }),
      'GET /api/v1/misiones/en-curso': respuesta(200, []),
      'GET /api/v1/misiones/historial': respuesta(200, { completadas: [] }),
      'GET /api/v1/misiones/ejecuciones/e%201': respuesta(200, { ejecucionId: 'e 1' }),
    });
    const fuente = fuenteHttpDeMisiones({ fetchImpl });

    expect(await fuente.detalle('templo/olvidado')).toEqual({ id: 'templo/olvidado' });
    expect(await fuente.activas()).toEqual([]);
    expect(await fuente.historial()).toEqual({ completadas: [] });
    expect(await fuente.reporte('e 1')).toEqual({ ejecucionId: 'e 1' });
  });

  test('matricular: el cuerpo es `SolicitudDeMatricula` (sin la misión, solo pasos) y va con Idempotency-Key', async () => {
    const activa = {
      ejecucionId: '5f6d2b1e-0000-4000-8000-000000000001',
      misionId: 'templo-olvidado',
      nombre: 'El Templo Olvidado',
      categoria: 'HISTORIA',
      heroe: { id: 'h-1', nombre: 'Aquiles' },
      iniciadaEn: '2026-09-27T10:00:00Z',
      terminaEn: '2026-09-27T22:00:00Z',
      estado: 'EN_PROGRESO',
    };
    const fetchImpl = servidor({
      'POST /api/v1/misiones/templo-olvidado/ejecuciones': respuesta(201, activa),
    });
    const fuente = fuenteHttpDeMisiones({ fetchImpl, generarClave: () => 'clave-1' });

    const creada = await fuente.matricular({
      misionId: 'templo-olvidado',
      heroeId: 'h-1',
      rotaciones: [{ prioridad: 'Alta', pasos: ['Embate sangriento', 'Ataque básico'] }],
    });

    expect(creada).toEqual(activa);
    const [, opciones] = fetchImpl.mock.calls[0];
    expect(opciones.method).toBe('POST');
    expect(opciones.headers).toEqual({
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'Idempotency-Key': 'clave-1',
    });
    expect(JSON.parse(opciones.body)).toEqual({
      heroeId: 'h-1',
      rotaciones: [{ pasos: ['Embate sangriento', 'Ataque básico'] }],
    });
  });

  test('matricular sin rotaciones deja que el servidor use la guardada; con escalón, lo manda', async () => {
    const fetchImpl = jest.fn(async () => respuesta(201, {}));
    const claves = ['a', 'b'];
    const fuente = fuenteHttpDeMisiones({ fetchImpl, generarClave: () => claves.shift() });

    await fuente.matricular({ misionId: 'm', heroeId: 'h-1' });
    await fuente.matricular({ misionId: 'm', heroeId: 'h-1', rotaciones: [], escalon: 'HEROICO' });

    expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual({ heroeId: 'h-1' });
    expect(JSON.parse(fetchImpl.mock.calls[1][1].body)).toEqual({
      heroeId: 'h-1',
      rotaciones: [],
      escalon: 'HEROICO',
    });
    // Cada envío, su clave.
    expect(fetchImpl.mock.calls[0][1].headers['Idempotency-Key']).toBe('a');
    expect(fetchImpl.mock.calls[1][1].headers['Idempotency-Key']).toBe('b');
  });

  test('sin generador inyectado, la clave es un UUID nuevo en cada envío', async () => {
    const fetchImpl = jest.fn(async () => respuesta(201, {}));
    const fuente = fuenteHttpDeMisiones({ fetchImpl });

    await fuente.matricular({ misionId: 'm', heroeId: 'h' });
    await fuente.matricular({ misionId: 'm', heroeId: 'h' });

    const [primera, segunda] = fetchImpl.mock.calls.map((c) => c[1].headers['Idempotency-Key']);
    expect(primera).toMatch(/^[0-9a-f-]{36}$/);
    expect(segunda).not.toBe(primera);
  });

  test('cancelar y marcar o quitar la favorita', async () => {
    const cancelacion = {
      ejecucionId: 'e-1',
      estado: 'ABANDONADA',
      heroeLiberado: true,
      penalizacion: 'Pierdes las recompensas de esta misión.',
    };
    const fetchImpl = servidor({
      'POST /api/v1/misiones/ejecuciones/e-1/cancelacion': respuesta(200, cancelacion),
      'PUT /api/v1/misiones/templo/favorita': respuesta(204, undefined),
      'DELETE /api/v1/misiones/templo/favorita': respuesta(204, undefined),
    });
    const fuente = fuenteHttpDeMisiones({ fetchImpl });

    expect(await fuente.cancelar('e-1')).toEqual(cancelacion);
    await expect(fuente.marcarFavorita('templo', true)).resolves.toBeUndefined();
    await expect(fuente.marcarFavorita('templo', false)).resolves.toBeUndefined();
    expect(fetchImpl.mock.calls.map(([url, o]) => `${o.method} ${url}`)).toEqual([
      'POST /api/v1/misiones/ejecuciones/e-1/cancelacion',
      'PUT /api/v1/misiones/templo/favorita',
      'DELETE /api/v1/misiones/templo/favorita',
    ]);
  });

  test('estrategia guardada: la de ese héroe, o nula si todavía no guardó ninguna (404)', async () => {
    const guardada = {
      heroeId: 'h-1',
      prototipo: 'Guerrero Armas',
      nivel: 1,
      rotaciones: [{ prioridad: 'Alta', pasos: ['Embate sangriento'] }],
      actualizadaEn: '2026-09-27T10:00:00Z',
    };
    const fetchImpl = servidor({
      'GET /api/v1/misiones/estrategias/h-1': respuesta(200, guardada),
      'GET /api/v1/misiones/estrategias/h-2': problema(
        404,
        'Todavía no guardaste una estrategia para este héroe.',
      ),
      'GET /api/v1/misiones/estrategias/h-3': problema(
        503,
        'La sección de Inventario no esta disponible temporalmente.',
        { seccion: 'Inventario' },
      ),
    });
    const fuente = fuenteHttpDeMisiones({ fetchImpl });

    expect(await fuente.estrategiaGuardada('h-1')).toEqual(guardada);
    expect(await fuente.estrategiaGuardada('h-2')).toBeNull();
    await expect(fuente.estrategiaGuardada('h-3')).rejects.toMatchObject({
      status: 503,
      detalle: 'La sección de Inventario no esta disponible temporalmente.',
    });
  });

  test('guardar la estrategia: PUT con solo las rotaciones; si no vale, el motivo y lo que sí vale', async () => {
    const fetchImpl = servidor({
      'PUT /api/v1/misiones/estrategias/h-1': (opciones) =>
        respuesta(200, { heroeId: 'h-1', nivel: 1, cuerpo: JSON.parse(opciones.body) }),
      'PUT /api/v1/misiones/estrategias/h-2': problema(
        422,
        'La rotación 1 usa una habilidad que Guerrero Armas no posee en nivel 1: Golpe de tormenta.',
        { habilidadesValidas: ['Embate sangriento', 'Ataque básico'] },
      ),
    });
    const fuente = fuenteHttpDeMisiones({ fetchImpl });

    const guardada = await fuente.guardarEstrategia('h-1', [
      { prioridad: 'Alta', pasos: ['Embate sangriento'] },
    ]);
    expect(guardada.cuerpo).toEqual({ rotaciones: [{ pasos: ['Embate sangriento'] }] });

    await expect(
      fuente.guardarEstrategia('h-2', [{ pasos: ['Golpe de tormenta'] }]),
    ).rejects.toMatchObject({
      status: 422,
      detalle:
        'La rotación 1 usa una habilidad que Guerrero Armas no posee en nivel 1: Golpe de tormenta.',
      habilidadesValidas: ['Embate sangriento', 'Ataque básico'],
    });
  });
});

describe('los errores, como los leen las vistas', () => {
  test('409: el `detail` es apto para el jugador y llega como `detalle`', async () => {
    const fetchImpl = jest.fn(async () =>
      problema(409, 'Tu héroe ya está en otra misión: espera a que vuelva.'),
    );
    const fuente = fuenteHttpDeMisiones({ fetchImpl, generarClave: () => 'k' });

    await expect(fuente.matricular({ misionId: 'm', heroeId: 'h' })).rejects.toMatchObject({
      status: 409,
      detalle: 'Tu héroe ya está en otra misión: espera a que vuelva.',
    });
  });

  test('422 de matrícula: todos los motivos (HU-MIS-008)', async () => {
    const motivos = ['Tu héroe no lleva nada equipado.', 'Un sanador no puede ir solo.'];
    const fetchImpl = jest.fn(async () => problema(422, motivos.join(' '), { motivos }));
    const fuente = fuenteHttpDeMisiones({ fetchImpl, generarClave: () => 'k' });

    const fallo = await fuente.matricular({ misionId: 'm', heroeId: 'h' }).catch((e) => e);

    expect(fallo.status).toBe(422);
    expect(fallo.detalle).toBe(motivos.join(' '));
    expect(fallo.motivos).toEqual(motivos);
  });

  test('400 y 500 no traen nada que enseñar; un cuerpo que no es JSON tampoco rompe nada', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValueOnce(problema(400, 'La petición no cumple el contrato de misiones.'))
      .mockResolvedValueOnce(problema(500, 'NullPointerException en algún sitio'))
      .mockResolvedValueOnce(respuesta(502, undefined));
    const fuente = fuenteHttpDeMisiones({ fetchImpl });

    for (const status of [400, 500, 502]) {
      const fallo = await fuente.historial().catch((e) => e);
      expect(fallo).toBeInstanceOf(Error);
      expect(fallo.status).toBe(status);
      expect(fallo.detalle).toBeUndefined();
    }
  });

  test('404 del detalle: la vista lo reconoce por su `status`', async () => {
    const fetchImpl = jest.fn(async () =>
      problema(404, 'No hay una misión publicada con el identificador «vieja».'),
    );
    const fuente = fuenteHttpDeMisiones({ fetchImpl });

    await expect(fuente.detalle('vieja')).rejects.toMatchObject({ status: 404 });
  });
});
