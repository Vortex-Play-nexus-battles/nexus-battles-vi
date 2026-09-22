import { jest } from '@jest/globals';
import {
  publicarSubasta,
  crearClavePublicacion,
  interpretarProblema,
} from './cliente-publicacion-subastas.js';

const solicitud = {
  elementoInventarioId: 'unidad-1',
  productoId: 'a32b510f-9fd1-4a28-8b18-ae4979b41826',
  duracion: '24H',
  precioInicial: 10,
  precioCompraInmediata: null,
};
const resultado = { id: 'subasta-1', estado: 'ACTIVA', comisionCobrado: 1 };
const respuesta = (status, cuerpo) => ({
  status,
  ok: status < 400,
  json: async () => cuerpo,
  clone() {
    return this;
  },
});

beforeEach(() => {
  sessionStorage.clear();
  document.head.innerHTML = '';
  // El interceptor reutiliza este banner y no crea su toast con HTML.
  document.body.innerHTML = '<p id="nexus-rbac-forbidden" hidden></p>';
});
afterEach(() => {
  jest.restoreAllMocks();
  jest.useRealTimers();
  delete globalThis.fetch;
});

test('POST real, base común, Authorization del interceptor y solo campos contractuales', async () => {
  document.head.innerHTML = '<meta name="nexus-api-base" content="https://api.example.test/" />';
  sessionStorage.setItem('nexus.token', 'token-de-la-sesion');
  globalThis.fetch = jest.fn().mockResolvedValue(respuesta(201, resultado));
  await expect(publicarSubasta(solicitud, 'clave-1')).resolves.toEqual(resultado);
  const [url, opciones] = globalThis.fetch.mock.calls[0];
  expect(url).toBe('https://api.example.test/api/v1/subastas');
  expect(opciones).toMatchObject({
    method: 'POST',
    headers: {
      Authorization: 'Bearer token-de-la-sesion',
      'Idempotency-Key': 'clave-1',
      'Content-Type': 'application/json',
    },
  });
  expect(JSON.parse(opciones.body)).toEqual(solicitud);
});

test('genera claves únicas dentro de la longitud contractual', () => {
  const claves = new Set(Array.from({ length: 30 }, crearClavePublicacion));
  expect(claves.size).toBe(30);
  for (const clave of claves) {
    expect(clave.length).toBeGreaterThan(0);
    expect(clave.length).toBeLessThanOrEqual(100);
  }
});

test.each(['', ' ', 'x'.repeat(101)])('no envía una clave inválida', async (clave) => {
  const fetchImpl = jest.fn();
  await expect(publicarSubasta(solicitud, clave, { fetchImpl })).rejects.toThrow();
  expect(fetchImpl).not.toHaveBeenCalled();
});

test('conserva la clave aportada tras un fallo de red y reintento', async () => {
  const fetchImpl = jest
    .fn()
    .mockRejectedValueOnce(new TypeError('red'))
    .mockResolvedValueOnce(respuesta(201, resultado));
  await expect(publicarSubasta(solicitud, 'misma', { fetchImpl })).rejects.toMatchObject({
    incierto: true,
  });
  await publicarSubasta(solicitud, 'misma', { fetchImpl });
  expect(fetchImpl.mock.calls.map(([, opciones]) => opciones.headers['Idempotency-Key'])).toEqual([
    'misma',
    'misma',
  ]);
});

test('timeout aborta la espera y conserva resultado incierto', async () => {
  jest.useFakeTimers();
  const fetchImpl = (_url, { signal }) =>
    new Promise((_resolve, reject) =>
      signal.addEventListener('abort', () => reject(new Error('abort'))),
    );
  const verificacion = expect(
    publicarSubasta(solicitud, 'timeout', { fetchImpl, timeoutMs: 20 }),
  ).rejects.toMatchObject({ incierto: true });
  await jest.advanceTimersByTimeAsync(20);
  await verificacion;
});

test.each([
  [422, 'Creditos insuficientes para publicar la subasta', /créditos suficientes/, false],
  [403, 'El usuario tiene una sanción activa', /sanción activa/, false],
  [403, 'El elemento no pertenece al usuario', /no pertenece/, false],
  [422, 'El producto está en uso', /en uso/, false],
  [422, 'El producto no es subastable', /premium/, false],
  [409, 'El elemento ya tiene una subasta activa', /no está disponible/, false],
  [409, 'Publicacion con esta clave en curso; reintente tras su finalizacion', /en curso/, true],
  [409, 'La clave de idempotencia fue usada con otra solicitud', /conflicto/, true],
  [503, 'Resultado transaccional desconocido; requiere conciliacion', /conciliación/, true],
  [503, 'Respuesta inesperada de finanzas: secreto', /temporalmente/, true],
  [401, 'Se requiere un JWT valido con uid para publicar', /sesión/, false],
  [404, 'Producto inexistente', /no está disponible/, false],
])('traduce Problem Details real %s %s', async (status, detail, esperado, incierto) => {
  const fetchImpl = async () => respuesta(status, { type: 'about:blank', status, detail });
  await expect(publicarSubasta(solicitud, 'k', { fetchImpl })).rejects.toMatchObject({
    message: expect.stringMatching(esperado),
    status,
    incierto,
  });
});

test('error desconocido no revela detalles ni interpreta tipos inventados', () => {
  const fallo = interpretarProblema(422, {
    type: 'https://desconocido.test/error',
    detail: 'Creditos insuficientes para publicar la subasta',
  });
  expect(fallo.message).toBe('No se pudo publicar la subasta. Inténtalo de nuevo más tarde.');
  expect(interpretarProblema(500, { detail: 'password=secreto' }).message).not.toContain('secreto');
});

test.each([
  respuesta(201, {}),
  {
    status: 201,
    ok: true,
    json: async () => {
      throw new Error('json');
    },
  },
])('respuesta exitosa ilegible deja el intento incierto', async (valor) => {
  await expect(
    publicarSubasta(solicitud, 'k', { fetchImpl: async () => valor }),
  ).rejects.toMatchObject({ incierto: true });
});
