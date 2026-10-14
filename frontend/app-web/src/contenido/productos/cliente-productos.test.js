import { jest } from '@jest/globals';
import {
  consultarHistorialProducto,
  consultarEstadisticasCatalogo,
  crearProducto,
  revertirProducto,
  RUTA_ESTADISTICAS,
  RUTA_PRODUCTOS,
} from './cliente-productos.js';

function respuesta(status, cuerpo) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (cuerpo === null ? '' : JSON.stringify(cuerpo)),
  };
}

const solicitud = {
  nombre: 'Espada solar',
  imagen: '/img/espada.webp',
  descripcion: 'Arma de prueba',
  tipo: 'ARMA',
  tiraje: 100,
  premium: false,
  precioCreditos: 500,
  poderDeAtaque: 25,
  tasaDeCaida: 10,
};

test('envía el contrato de creación mediante el interceptor común', async () => {
  const creado = { ...solicitud, id: 'id-1', estado: 'ACTIVO' };
  const fetchImpl = jest.fn(async () => respuesta(201, creado));

  await expect(crearProducto(solicitud, { fetchImpl })).resolves.toEqual(creado);
  expect(fetchImpl).toHaveBeenCalledWith(RUTA_PRODUCTOS, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(solicitud),
  });
});

test('conserva el detalle Problem Details de una solicitud inválida', async () => {
  const fetchImpl = jest.fn(async () =>
    respuesta(400, {
      type: 'urn:nexus:problema:solicitud-invalida',
      title: 'Solicitud inválida',
      status: 400,
      detail: 'El tiraje debe ser -1 o mayor que cero',
      instance: '/api/v1/productos',
    }),
  );

  await expect(crearProducto(solicitud, { fetchImpl })).rejects.toMatchObject({
    status: 400,
    message: 'El tiraje debe ser -1 o mayor que cero',
  });
});

test('consulta el resumen actual del catálogo mediante GET', async () => {
  const resumen = {
    total: 8,
    porTipo: {
      HEROE: 2,
      HABILIDAD: 1,
      ARMA: 2,
      ARMADURA: 1,
      ITEM: 1,
      EPICA: 1,
    },
    porEstado: {
      ACTIVO: 6,
      UNICO: 1,
      SUSPENDIDO: 1,
    },
  };
  const fetchImpl = jest.fn(async () => respuesta(200, resumen));

  await expect(consultarEstadisticasCatalogo({ fetchImpl })).resolves.toEqual(resumen);
  expect(fetchImpl).toHaveBeenCalledWith(RUTA_ESTADISTICAS, {
    method: 'GET',
  });
});

test('conserva el detalle del error al consultar las estadísticas', async () => {
  const fetchImpl = jest.fn(async () =>
    respuesta(401, {
      title: 'No autenticado',
      status: 401,
      detail: 'Se requiere un token Bearer válido',
    }),
  );

  await expect(consultarEstadisticasCatalogo({ fetchImpl })).rejects.toMatchObject({
    status: 401,
    message: 'Se requiere un token Bearer válido',
  });
});

test('consulta el historial inmutable de un producto', async () => {
  const cambios = [{ id: 'respaldo-1', tipo: 'MODIFICACION', revertible: true }];
  const fetchImpl = jest.fn(async () => respuesta(200, cambios));

  await expect(consultarHistorialProducto('producto 1', { fetchImpl })).resolves.toEqual(cambios);
  expect(fetchImpl).toHaveBeenCalledWith('/api/v1/productos/producto%201/historial', {
    method: 'GET',
  });
});

test('envía la reversión elegida y devuelve el producto restaurado', async () => {
  const restaurado = { id: 'producto-1', nombre: 'Estado anterior', version: 4 };
  const fetchImpl = jest.fn(async () => respuesta(200, restaurado));

  await expect(revertirProducto('producto-1', 'respaldo-1', { fetchImpl })).resolves.toEqual(
    restaurado,
  );
  expect(fetchImpl).toHaveBeenCalledWith('/api/v1/productos/producto-1/reversiones', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ respaldoId: 'respaldo-1' }),
  });
});
