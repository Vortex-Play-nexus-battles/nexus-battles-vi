import { jest } from '@jest/globals';

import { crearBanner, editarBanner, listarBanners, retirarBanner } from './cliente-banners.js';

function respuesta(cuerpo = null, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (cuerpo === null ? '' : JSON.stringify(cuerpo)),
  };
}

test('lista todos los banners para administracion', async () => {
  const fetchImpl = jest.fn().mockResolvedValue(respuesta([{ id: 'b-1' }]));
  await expect(listarBanners({ fetchImpl })).resolves.toEqual([{ id: 'b-1' }]);
  expect(fetchImpl).toHaveBeenCalledWith('/api/v1/banners', { method: 'GET' });
});

test('crea, edita y retira mediante el contrato HTTP', async () => {
  const solicitud = {
    contenido: 'Anuncio',
    publicarDesde: '2099-01-01T00:00:00Z',
    vigenteHasta: '2099-01-02T00:00:00Z',
  };
  const fetchImpl = jest.fn().mockResolvedValue(respuesta({ id: 'b-1' }, 201));
  await crearBanner(solicitud, { fetchImpl });
  await editarBanner('b-1', solicitud, { fetchImpl });
  fetchImpl.mockResolvedValueOnce(respuesta(null, 204));
  await retirarBanner('b-1', { fetchImpl });
  expect(fetchImpl.mock.calls.map(([, opciones]) => opciones.method)).toEqual([
    'POST',
    'PUT',
    'DELETE',
  ]);
});

test('conserva Problem Details cuando el servidor rechaza la solicitud', async () => {
  const fetchImpl = jest.fn().mockResolvedValue(respuesta({ detail: 'Vigencia invalida' }, 400));
  await expect(crearBanner({}, { fetchImpl })).rejects.toMatchObject({
    status: 400,
    message: 'Vigencia invalida',
  });
});
