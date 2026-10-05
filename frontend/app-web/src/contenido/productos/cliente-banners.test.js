import { jest } from '@jest/globals';

import {
  crearBanner,
  editarBanner,
  listarBanners,
  listarVigentes,
  retirarBanner,
} from './cliente-banners.js';

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

test('RF-NOT-002: la home pide los vigentes a la ruta pública del contrato', async () => {
  const vigentes = [{ id: 'b-1', contenido: 'Anuncio' }];
  const fetchImpl = jest.fn().mockResolvedValue(respuesta(vigentes));
  await expect(listarVigentes({ fetchImpl })).resolves.toEqual(vigentes);
  expect(fetchImpl).toHaveBeenCalledWith('/api/v1/banners/vigentes', { method: 'GET' });
});

test('la página de error de un proxy no se cuela como texto ni rompe con un SyntaxError', async () => {
  const fetchImpl = jest.fn().mockResolvedValue({
    ok: false,
    status: 502,
    text: async () => '<html><body>502 Bad Gateway</body></html>',
  });
  const fallo = await listarBanners({ fetchImpl }).catch((error) => error);
  expect(fallo).not.toBeInstanceOf(SyntaxError);
  expect(fallo.status).toBe(502);
  expect(fallo.message).not.toMatch(/Bad Gateway|<html/);
});

test('conserva Problem Details cuando el servidor rechaza la solicitud', async () => {
  const fetchImpl = jest.fn().mockResolvedValue(respuesta({ detail: 'Vigencia invalida' }, 400));
  await expect(crearBanner({}, { fetchImpl })).rejects.toMatchObject({
    status: 400,
    message: 'Vigencia invalida',
  });
});
