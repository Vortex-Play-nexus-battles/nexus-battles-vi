/**
 * Cliente del panel del asistente — HU-CHA-012.
 */

import { jest } from '@jest/globals';

import { CLAVE_BASE_LOCAL } from '../comun/cliente-chatbot.js';
import {
  consultaDeBrechas,
  consultaDePeriodo,
  consultaDeTickets,
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
  await panel.programarDespliegue('2026-10-01T15:00:00.000Z');
  await panel.cancelarProgramacion();
  await panel.revertir();
  await panel.eliminarCaso('c-1');

  const llamadas = fetch.mock.calls.map(([url, opciones]) => `${opciones.method} ${url}`);
  expect(llamadas).toEqual([
    'POST /api/v1/chatbot/admin/base-conocimiento/borrador',
    'PUT /api/v1/chatbot/admin/base-conocimiento/borrador/temas/t-1',
    'PUT /api/v1/chatbot/admin/base-conocimiento/borrador/importacion',
    'POST /api/v1/chatbot/admin/base-conocimiento/borrador/despliegue',
    'PUT /api/v1/chatbot/admin/base-conocimiento/borrador/programacion',
    'DELETE /api/v1/chatbot/admin/base-conocimiento/borrador/programacion',
    'POST /api/v1/chatbot/admin/base-conocimiento/produccion/reversion',
    'DELETE /api/v1/chatbot/admin/base-conocimiento/casos-evaluacion/c-1',
  ]);
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ descripcion: null });
  expect(JSON.parse(fetch.mock.calls[4][1].body)).toEqual({
    desplegarEn: '2026-10-01T15:00:00.000Z',
  });
});

test('consultaDeTickets pone el estado solo si lo hay', () => {
  expect(consultaDeTickets()).toBe('?pagina=0&tamano=20');
  expect(consultaDeTickets({ estado: 'ABIERTO', pagina: 2, tamano: 10 })).toBe(
    '?estado=ABIERTO&pagina=2&tamano=10',
  );
});

test('las rutas de la bandeja de soporte', async () => {
  const fetch = jest.fn(async () => respuesta({}));
  const panel = cliente(fetch);

  await panel.listarTickets({ estado: 'EN_PROCESO', pagina: 1 });
  await panel.obtenerTicket('t-1');
  await panel.atenderTicket('t-1', { estado: 'RESUELTO', respuesta: 'Listo' });

  const llamadas = fetch.mock.calls.map(([url, opciones]) => `${opciones.method} ${url}`);
  expect(llamadas).toEqual([
    'GET /api/v1/chatbot/admin/tickets?estado=EN_PROCESO&pagina=1&tamano=20',
    'GET /api/v1/chatbot/admin/tickets/t-1',
    'PATCH /api/v1/chatbot/admin/tickets/t-1',
  ]);
  expect(JSON.parse(fetch.mock.calls[2][1].body)).toEqual({
    estado: 'RESUELTO',
    respuesta: 'Listo',
  });
});

test('consultaDeBrechas: período y estado solo si los hay, siempre la página (de 16)', () => {
  expect(consultaDeBrechas()).toBe('?pagina=0&tamano=16');
  expect(
    consultaDeBrechas({
      desde: '2026-09-01',
      hasta: '2026-09-30',
      estado: 'PENDIENTE',
      pagina: 2,
      tamano: 16,
    }),
  ).toBe('?desde=2026-09-01&hasta=2026-09-30&estado=PENDIENTE&pagina=2&tamano=16');
  expect(consultaDeBrechas({ estado: '' })).toBe('?pagina=0&tamano=16');
});

test('las rutas de las preguntas sin respuesta (1.7.0)', async () => {
  const fetch = jest.fn(async () => respuesta({}));
  const panel = cliente(fetch);

  await panel.listarBrechas({ estado: 'ATENDIDA', pagina: 1 });
  await panel.listarBrechas();
  await panel.cambiarEstadoDeBrecha('b/1', 'DESCARTADA');

  const llamadas = fetch.mock.calls.map(([url, opciones]) => `${opciones.method} ${url}`);
  expect(llamadas).toEqual([
    'GET /api/v1/chatbot/admin/brechas?estado=ATENDIDA&pagina=1&tamano=16',
    'GET /api/v1/chatbot/admin/brechas?pagina=0&tamano=16',
    'PATCH /api/v1/chatbot/admin/brechas/b%2F1',
  ]);
  expect(JSON.parse(fetch.mock.calls[2][1].body)).toEqual({ estado: 'DESCARTADA' });
  expect(fetch.mock.calls[2][1].headers.Authorization).toBe('Bearer token-admin');
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

// ms-chatbot.yaml 1.14.0 (§7.4.6): el contenido de una captura de la solicitud.
test('leerCaptura pide la imagen con el token del administrador y la da como Blob de su tipo', async () => {
  const fetch = jest.fn(async () => ({
    ok: true,
    status: 200,
    headers: { get: (nombre) => (nombre === 'Content-Type' ? 'image/png' : null) },
    blob: async () => new Blob(['png'], { type: 'application/octet-stream' }),
  }));

  const imagen = await cliente(fetch).leerCaptura('t-1', 'c-1');

  const [url, opciones] = fetch.mock.calls[0];
  expect(url).toBe('/api/v1/chatbot/admin/tickets/t-1/capturas/c-1');
  expect(opciones.method).toBe('GET');
  expect(opciones.headers.Authorization).toBe('Bearer token-admin');
  expect(opciones.headers.Accept).toBe('image/png, image/jpeg, application/problem+json');
  expect(imagen.type).toBe('image/png');
});

test('leerCaptura no da por captura lo que no es una imagen, ni un 404', async () => {
  const html = jest.fn(async () => respuesta('<script>', 200, { 'Content-Type': 'text/html' }));
  await expect(cliente(html).leerCaptura('t-1', 'c-1')).rejects.toBeInstanceOf(Error);

  const noEsta = jest.fn(async () => respuesta({ title: 'Not Found', status: 404 }, 404));
  const error = await cliente(noEsta)
    .leerCaptura('t-1', 'c-9')
    .catch((e) => e);
  expect(error.estado).toBe(404);
});
