import { jest } from '@jest/globals';
import { consultarAlertasCatalogo, RUTA_ALERTAS_CATALOGO } from './cliente-alertas-catalogo.js';

test('consulta las alertas pendientes al iniciar sesion', async () => {
  const alertas = [
    {
      id: 'alerta-1',
      productoNombre: 'Espada solar',
      descripcion: 'Se actualizo el balance de Espada solar.',
      implementadaEn: '2026-09-11T15:30:00Z',
    },
  ];
  const fetchImpl = jest.fn().mockResolvedValue({
    ok: true,
    json: async () => alertas,
  });

  const resultado = await consultarAlertasCatalogo({ fetchImpl });

  expect(resultado).toEqual(alertas);
  expect(fetchImpl).toHaveBeenCalledWith(RUTA_ALERTAS_CATALOGO, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
  });
});

test('informa un error cuando el servicio rechaza la consulta', async () => {
  const fetchImpl = jest.fn().mockResolvedValue({
    ok: false,
    status: 401,
  });

  await expect(consultarAlertasCatalogo({ fetchImpl })).rejects.toMatchObject({
    status: 401,
  });
});
