/**
 * HU-INV-001 - Acceso a la consulta paginada.
 * Endpoint: GET /api/v1/inventario/elementos?pagina=N con cabecera X-User-Name.
 */
import { consultarPagina } from './cliente-inventario.js';

function respuesta(cuerpo, ok = true, status = 200) {
  return { ok, status, json: async () => cuerpo };
}

function espia(cuerpo = { elementos: [] }) {
  const llamadas = [];
  const fetchFalso = async (url, opciones) => {
    llamadas.push({ url, opciones });
    return respuesta(cuerpo);
  };
  return { llamadas, fetchFalso };
}

describe('Cliente de la consulta paginada', () => {
  test('pide la pagina al endpoint acordado', async () => {
    const { llamadas, fetchFalso } = espia();

    await consultarPagina('jugador-A', 2, { fetchImpl: fetchFalso });

    expect(llamadas[0].url).toBe('/api/v1/inventario/elementos?pagina=2');
  });

  test('identifica al jugador por cabecera y nunca por la ruta', async () => {
    const { llamadas, fetchFalso } = espia();

    await consultarPagina('jugador-A', 0, { fetchImpl: fetchFalso });

    expect(llamadas[0].opciones.headers['X-User-Name']).toBe('jugador-A');
    // La ruta no lleva identificador: no se puede pedir el inventario ajeno.
    expect(llamadas[0].url).not.toContain('jugador-A');
  });

  test('la primera pagina es la cero por omision', async () => {
    const { llamadas, fetchFalso } = espia();

    await consultarPagina('jugador-A', undefined, { fetchImpl: fetchFalso });

    expect(llamadas[0].url).toContain('pagina=0');
  });

  test('devuelve el cuerpo de la respuesta', async () => {
    const cuerpo = { elementos: [], numero: 0, totalElementos: 0 };
    const fetchFalso = async () => respuesta(cuerpo);

    await expect(consultarPagina('jugador-A', 0, { fetchImpl: fetchFalso })).resolves.toEqual(
      cuerpo,
    );
  });

  test('rechaza numeros de pagina negativos sin llamar al servicio', async () => {
    let llamado = false;
    const fetchFalso = async () => {
      llamado = true;
      return respuesta({});
    };

    await expect(consultarPagina('jugador-A', -1, { fetchImpl: fetchFalso })).rejects.toThrow(
      /negativ/,
    );
    expect(llamado).toBe(false);
  });

  test('rechaza una identidad vacia sin llamar al servicio', async () => {
    let llamado = false;
    const fetchFalso = async () => {
      llamado = true;
      return respuesta({});
    };

    await expect(consultarPagina('   ', 0, { fetchImpl: fetchFalso })).rejects.toThrow(
      /identidad/i,
    );
    expect(llamado).toBe(false);
  });

  test('convierte una respuesta fallida en un error con su estado', async () => {
    const fetchFalso = async () => respuesta(null, false, 503);

    await expect(consultarPagina('jugador-A', 0, { fetchImpl: fetchFalso })).rejects.toThrow(/503/);
  });
});
