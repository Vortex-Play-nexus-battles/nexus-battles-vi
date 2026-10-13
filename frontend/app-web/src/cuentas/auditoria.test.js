/**
 * Registro de auditoría: abre con lo más reciente — RFINAL-06.
 *
 * Revisión del super administrador en AWS DEV (4-oct, §2.2 «7.3.5 Auditoría:
 * orden»): la pantalla abría con «23 sept, página 1 de 231», lo más antiguo.
 * `GET /api/v1/admin/auditoria` ya admite el `sort` de Spring Data
 * (`ms-cumplimiento-auditoria.yaml`, parámetro `sort`) y, sin él, el orden lo
 * decide la base de datos. La vista lo pide: fecha descendente y, para que dos
 * eventos del mismo instante no salten de una página a otra, el `id` como
 * desempate.
 *
 * `auditoria.js` es un guion clásico que arranca al cargarse: se monta su DOM,
 * se simula `fetch` y se importa una sola vez para todo el archivo.
 */

import { jest } from '@jest/globals';

const asentar = async () => {
  for (let i = 0; i < 6; i++) {
    await Promise.resolve();
  }
};

const REGISTRO = {
  id: '4f1c2a7e-3d6b-4b9a-8f0e-1c2d3e4f5a6b',
  fechaHora: '2026-10-04T14:31:00Z',
  administrador: 'simon_superadmin',
  tipoAccion: 'CAMBIO_ROL',
  afectado: 'jugador-6f1c',
  valorAnterior: 'JUGADOR',
  valorNuevo: 'MODERADOR',
  motivo: 'Ascenso aprobado',
  ipOrigen: '10.0.2.15',
};

const pagina = (numero) => ({
  ok: true,
  status: 200,
  json: async () => ({ content: [REGISTRO], totalPages: 3, number: numero }),
});

/** Lo que el servidor recibió en la última consulta. */
const ultimaConsulta = () => new URL(globalThis.fetch.mock.calls.at(-1)[0], 'http://localhost');

beforeAll(async () => {
  document.body.innerHTML = `
    <input id="filtro-administrador" />
    <select id="filtro-tipo-accion">
      <option value="">Todas</option>
      <option value="CAMBIO_ROL">Cambio de rol</option>
    </select>
    <input id="filtro-desde" type="date" />
    <input id="filtro-hasta" type="date" />
    <button id="btn-filtrar"></button>
    <button id="btn-limpiar"></button>
    <button id="btn-exportar">Exportar</button>
    <table><tbody id="auditoria-tbody"></tbody></table>
    <p id="auditoria-estado" hidden></p>
    <button id="btn-anterior"></button>
    <span id="auditoria-pagina-actual"></span>
    <button id="btn-siguiente"></button>`;
  sessionStorage.setItem('nexus.token', 'jwt-super');
  globalThis.fetch = jest.fn(async () => pagina(0));
  await import('./auditoria.js');
  await asentar();
});

test('abre pidiendo la bitácora del evento más reciente al más antiguo, con desempate estable', () => {
  const consulta = ultimaConsulta();
  expect(consulta.pathname).toBe('/api/v1/admin/auditoria');
  expect(consulta.searchParams.getAll('sort')).toEqual(['fechaHora,desc', 'id,desc']);
  expect(consulta.searchParams.get('page')).toBe('0');
  expect(document.getElementById('auditoria-tbody').textContent).toContain('simon_superadmin');
});

test('pasar de página conserva el mismo orden', async () => {
  globalThis.fetch.mockImplementation(async () => pagina(1));
  document.getElementById('btn-siguiente').click();
  await asentar();

  const consulta = ultimaConsulta();
  expect(consulta.searchParams.get('page')).toBe('1');
  expect(consulta.searchParams.getAll('sort')).toEqual(['fechaHora,desc', 'id,desc']);
});

test('filtrar vuelve a la primera página sin perder el orden', async () => {
  globalThis.fetch.mockImplementation(async () => pagina(0));
  document.getElementById('filtro-tipo-accion').value = 'CAMBIO_ROL';
  document.getElementById('btn-filtrar').click();
  await asentar();

  const consulta = ultimaConsulta();
  expect(consulta.searchParams.get('page')).toBe('0');
  expect(consulta.searchParams.get('tipoAccion')).toBe('CAMBIO_ROL');
  expect(consulta.searchParams.getAll('sort')).toEqual(['fechaHora,desc', 'id,desc']);
});
