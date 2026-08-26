/**
 * SCRUM-319 - Acceso a la consulta paginada de SCRUM-318.
 * El endpoint es GET /api/v1/inventarios/{propietarioId}/elementos?pagina=N.
 */
import { consultarPagina } from './cliente-inventario.js';

function respuesta(cuerpo, ok = true, status = 200) {
  return { ok, status, json: async () => cuerpo };
}

describe('Cliente de la consulta paginada', () => {
  test('pide la pagina del propietario al endpoint acordado', async () => {
    const pedidas = [];
    const fetchFalso = async (url) => {
      pedidas.push(url);
      return respuesta({ elementos: [] });
    };

    await consultarPagina('jugador-A', 2, { fetchImpl: fetchFalso });

    expect(pedidas).toEqual(
      ['/api/v1/inventarios/jugador-A/elementos?pagina=2']);
  });

  test('la primera pagina es la cero por omision', async () => {
    const pedidas = [];
    const fetchFalso = async (url) => {
      pedidas.push(url);
      return respuesta({ elementos: [] });
    };

    await consultarPagina('jugador-A', undefined, { fetchImpl: fetchFalso });

    expect(pedidas[0]).toContain('pagina=0');
  });

  test('codifica el identificador del propietario en la ruta', async () => {
    const pedidas = [];
    const fetchFalso = async (url) => {
      pedidas.push(url);
      return respuesta({ elementos: [] });
    };

    await consultarPagina('jugador/../admin', 0, { fetchImpl: fetchFalso });

    expect(pedidas[0]).toBe(
      '/api/v1/inventarios/jugador%2F..%2Fadmin/elementos?pagina=0');
  });

  test('devuelve el cuerpo de la respuesta', async () => {
    const cuerpo = { elementos: [], numero: 0, totalElementos: 0 };
    const fetchFalso = async () => respuesta(cuerpo);

    await expect(consultarPagina('jugador-A', 0, { fetchImpl: fetchFalso }))
      .resolves.toEqual(cuerpo);
  });

  test('rechaza numeros de pagina negativos sin llamar al servicio', async () => {
    let llamado = false;
    const fetchFalso = async () => { llamado = true; return respuesta({}); };

    await expect(consultarPagina('jugador-A', -1, { fetchImpl: fetchFalso }))
      .rejects.toThrow(/negativ/);
    expect(llamado).toBe(false);
  });

  test('rechaza un propietario vacio sin llamar al servicio', async () => {
    let llamado = false;
    const fetchFalso = async () => { llamado = true; return respuesta({}); };

    await expect(consultarPagina('   ', 0, { fetchImpl: fetchFalso }))
      .rejects.toThrow(/propietarioId/);
    expect(llamado).toBe(false);
  });

  test('convierte una respuesta fallida en un error con su estado', async () => {
    const fetchFalso = async () => respuesta(null, false, 503);

    await expect(consultarPagina('jugador-A', 0, { fetchImpl: fetchFalso }))
      .rejects.toThrow(/503/);
  });
});
