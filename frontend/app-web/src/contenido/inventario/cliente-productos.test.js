/**
 * HU-INV-007 - Lectura del catalogo de productos.
 * Contrato: GET /api/v1/productos/{id}, publico, definido en
 * contracts/openapi/productos.yaml.
 */
import { consultarProducto } from './cliente-productos.js';

const PRODUCTO = { id: 'p-1', nombre: 'Hacha de Vorn', tipo: 'ARMA' };

function respuesta(cuerpo, ok = true, status = 200) {
  return { ok, status, json: async () => cuerpo };
}

function espia(cuerpo = PRODUCTO, ok = true, status = 200) {
  const llamadas = [];
  return {
    llamadas,
    fetchFalso: async (url, opciones) => {
      llamadas.push({ url, opciones });
      return respuesta(cuerpo, ok, status);
    },
  };
}

describe('Cliente del catalogo de productos', () => {
  test('pide el producto a la ruta del contrato', async () => {
    const { llamadas, fetchFalso } = espia();

    await consultarProducto('p-1', { fetchImpl: fetchFalso });

    expect(llamadas[0].url).toBe('/api/v1/productos/p-1');
  });

  test('codifica el identificador en la ruta', async () => {
    const { llamadas, fetchFalso } = espia();

    await consultarProducto('p/../admin', { fetchImpl: fetchFalso });

    expect(llamadas[0].url).toBe('/api/v1/productos/p%2F..%2Fadmin');
  });

  test('devuelve el producto que entrega el catalogo', async () => {
    const { fetchFalso } = espia();

    await expect(consultarProducto('p-1', { fetchImpl: fetchFalso })).resolves.toEqual(PRODUCTO);
  });

  test('un producto inexistente se distingue de un fallo del servicio', async () => {
    const { fetchFalso } = espia(null, false, 404);

    await expect(consultarProducto('p-9', { fetchImpl: fetchFalso })).rejects.toThrow(/no existe/i);
  });

  test('un fallo del servicio conserva su estado para la consola', async () => {
    const { fetchFalso } = espia(null, false, 503);

    await expect(consultarProducto('p-1', { fetchImpl: fetchFalso })).rejects.toThrow(/503/);
  });

  test('rechaza un identificador vacio sin llamar al catalogo', async () => {
    let llamado = false;
    const fetchFalso = async () => {
      llamado = true;
      return respuesta(PRODUCTO);
    };

    await expect(consultarProducto('   ', { fetchImpl: fetchFalso })).rejects.toThrow(
      /identificador/i,
    );
    expect(llamado).toBe(false);
  });

  test('la lectura del catalogo es publica: no manda cabecera de identidad', async () => {
    const { llamadas, fetchFalso } = espia();

    await consultarProducto('p-1', { fetchImpl: fetchFalso });

    const cabeceras = llamadas[0].opciones?.headers ?? {};
    expect(cabeceras['X-User-Name']).toBeUndefined();
  });
});
