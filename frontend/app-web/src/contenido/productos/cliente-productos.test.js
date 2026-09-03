import { jest } from '@jest/globals';
import { crearProducto, RUTA_PRODUCTOS } from './cliente-productos.js';

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
