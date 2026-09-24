/**
 * Cliente del panel del asistente — HU-CHA-012.
 */

import { jest } from '@jest/globals';

import { CLAVE_BASE_LOCAL } from '../comun/cliente-chatbot.js';
import {
consultaDePeriodo,
crearClientePanelChatbot,
nombreDeArchivo,
    } from './cliente-panel-chatbot.js';

function almacen(inicial = {}) {
    const datos = new Map(Object.entries(inicial));
    return { getItem: (clave) => (datos.has(clave) ? datos.get(clave) : null) };
    }

function respuesta(cuerpo, status = 200, cabeceras = {}) {
    return {
ok: status >= 200 && status < 300,
status,
headers: { get: (nombre) => cabeceras[nombre] ?? null },
json: async () => {
    if (cuerpo === undefined) {
    throw new SyntaxError('sin cuerpo');
      }
          return cuerpo;
    },
blob: async () => new Blob([String(cuerpo)]),
    };
    }

function cliente(fetch, base) {
    return crearClientePanelChatbot({
        fetch,
        almacenLocal: almacen(base ? { [CLAVE_BASE_LOCAL]: base } : {}),
    sesion: () => ({ token: 'token-admin' }),
  });
}

test('consultaDePeriodo arma los parámetros solo con lo que hay', () => {
expect(consultaDePeriodo()).toBe('');
expect(consultaDePeriodo({ desde: '2026-09-01' })).toBe('?desde=2026-09-01');
expect(consultaDePeriodo({ desde: '2026-09-01', hasta: '2026-09-30' })).toBe(
    '?desde=2026-09-01&hasta=2026-09-30',
);
});

test('nombreDeArchivo lee Content-Disposition y usa la alternativa si no hay', () => {
expect(nombreDeArchivo('attachment; filename="base-conocimiento-v3.json"', 'x.json')).toBe(
    'base-conocimiento-v3.json',
);
expect(nombreDeArchivo(null, 'x.json')).toBe('x.json');
});

test('manda el token del administrador y usa la base local', async () => {
    const fetch = jest.fn(async () => respuesta({ conversaciones: 1 }));
await cliente(fetch, 'http://localhost:8094').analiticas({ desde: '2026-09-01' });

    const [url, opciones] = fetch.mock.calls[0];
expect(url).toBe('http://localhost:8094/api/v1/chatbot/admin/analiticas?desde=2026-09-01');
expect(opciones.headers.Authorization).toBe('Bearer token-admin');
});

test('las rutas de la base de conocimiento y del reentrenamiento', async () => {
    const fetch = jest.fn(async () => respuesta({}));
    const panel = cliente(fetch);

await panel.crearCandidata('');
await panel.editarTema('t-1', { titulo: 'x' });
await panel.importarTemas({ temas: [] });
await panel.desplegarCandidata();
await panel.revertir();
await panel.eliminarCaso('c-1');

  const llamadas = fetch.mock.calls.map(([url, opciones]) => `${opciones.method} ${url}`);
expect(llamadas).toEqual([
                             'POST /api/v1/chatbot/admin/base-conocimiento/borrador',
                             'PUT /api/v1/chatbot/admin/base-conocimiento/borrador/temas/t-1',
                             'PUT /api/v1/chatbot/admin/base-conocimiento/borrador/importacion',
                             'POST /api/v1/chatbot/admin/base-conocimiento/borrador/despliegue',
                             'POST /api/v1/chatbot/admin/base-conocimiento/produccion/reversion',
                             'DELETE /api/v1/chatbot/admin/base-conocimiento/casos-evaluacion/c-1',
                             ]);
expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ descripcion: null });
    });

test('una exportación devuelve el nombre del archivo y su contenido', async () => {
    const fetch = jest.fn(async () =>
respuesta('a;b', 200, { 'Content-Disposition': 'attachment; filename="analiticas.csv"' }),
    );
    const archivo = await cliente(fetch).exportarAnaliticas({});

expect(archivo.nombre).toBe('analiticas.csv');
expect(archivo.contenido).toBeInstanceOf(Blob);
});

test('un 409 del despliegue conserva el cuerpo con los dos resultados', async () => {
    const problema = { title: 'Candidata con peor desempeno', candidata: { aciertos: 2 } };
    const fetch = jest.fn(async () => respuesta(problema, 409));

    const error = await cliente(fetch)
    .desplegarCandidata()
    .catch((e) => e);
expect(error.estado).toBe(409);
expect(error.problema.candidata.aciertos).toBe(2);
expect(error.noDisponible).toBe(false);
});

test('un 404 sin cuerpo del servicio es «no disponible»; uno con cuerpo no', async () => {
    const sinCuerpo = await cliente(jest.fn(async () => respuesta(undefined, 404)))
    .listarVersiones()
    .catch((e) => e);
    const conCuerpo = await cliente(
    jest.fn(async () => respuesta({ title: 'Not Found', detail: 'No existe esa version.' }, 404)),
    )
    .exportarVersion('v-1')
    .catch((e) => e);

expect(sinCuerpo.noDisponible).toBe(true);
expect(conCuerpo.noDisponible).toBe(false);
});
