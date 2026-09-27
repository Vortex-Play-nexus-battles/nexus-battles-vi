/**
 * B5 — la lista de deseos: la guarda el servicio, idempotente, y la vista
 * pinta lo que él confirma.
 */

import { jest } from '@jest/globals';

import { cambiarDeseo, pintarDeseo, textoDelFalloDeDeseo } from './tienda-deseos.js';
import { tarjetaDeProducto } from './tienda-producto.js';

const UUID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

function respuesta(status, cuerpo = null) {
  return { ok: status >= 200 && status < 300, status, json: async () => cuerpo };
}

describe('cambiarDeseo', () => {
  test('añadir es PUT /lista-deseos/{id}; quitar, DELETE', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValueOnce(respuesta(200, []))
      .mockResolvedValueOnce(respuesta(204));

    expect(await cambiarDeseo(UUID, true, { fetchImpl })).toEqual({
      ok: true,
      estado: 200,
      problema: null,
    });
    expect(await cambiarDeseo(UUID, false, { fetchImpl })).toEqual(
      expect.objectContaining({ ok: true, estado: 204 }),
    );

    expect(fetchImpl.mock.calls[0][0]).toBe(`/api/v1/lista-deseos/${UUID}`);
    expect(fetchImpl.mock.calls[0][1].method).toBe('PUT');
    expect(fetchImpl.mock.calls[1][1].method).toBe('DELETE');
  });

  test('el id viaja codificado en la ruta', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(200, []));

    await cambiarDeseo('a/b c', true, { fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/lista-deseos/a%2Fb%20c');
  });

  test('un rechazo trae su estado y su problem detail; sin respuesta, estado 0', async () => {
    const problema = { type: 'urn:nexus:problema:producto-inexistente', status: 404 };
    const fetchImpl = jest.fn().mockResolvedValueOnce(respuesta(404, problema));

    expect(await cambiarDeseo(UUID, true, { fetchImpl })).toEqual({
      ok: false,
      estado: 404,
      problema,
    });

    const sinRed = jest.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    expect(await cambiarDeseo(UUID, true, { fetchImpl: sinRed })).toEqual({
      ok: false,
      estado: 0,
      problema: null,
    });
  });
});

describe('lo que se dice cuando falla', () => {
  test('por el estado y el tipo, nunca por el texto del servidor', () => {
    expect(textoDelFalloDeDeseo({ estado: 401, problema: null }, true)).toMatch(/sesión/);
    expect(
      textoDelFalloDeDeseo(
        { estado: 404, problema: { type: 'urn:nexus:problema:producto-inexistente' } },
        true,
      ),
    ).toMatch(/ya no está en el catálogo/);
    expect(textoDelFalloDeDeseo({ estado: 0, problema: null }, true)).toMatch(/No se pudo guardar/);
    expect(textoDelFalloDeDeseo({ estado: 500, problema: null }, false)).toMatch(
      /No se pudo quitar/,
    );
  });
});

describe('pintarDeseo', () => {
  function vista() {
    const tarjeta = tarjetaDeProducto({
      id: UUID,
      nombre: 'Yelmo del Alba',
      tipo: 'ARMADURA',
      precioFinal: 18500,
      precioOriginal: 18500,
      moneda: 'COP',
      enListaDeseos: false,
    });
    document.body.replaceChildren(tarjeta);
    return tarjeta;
  }

  test('lo deseado: el conmutador pulsado, el borde y el distintivo', () => {
    const tarjeta = vista();
    const conmutador = tarjeta.querySelector('[data-deseo]');
    expect(conmutador.getAttribute('aria-pressed')).toBe('false');

    pintarDeseo(document, UUID, true);

    expect(conmutador.getAttribute('aria-pressed')).toBe('true');
    expect(tarjeta.dataset.deseado).toBe('si');
    expect(tarjeta.querySelector('.producto-deseado').textContent).toBe('En tu lista de deseos');
    // El nombre accesible no cambia: el estado lo anuncia aria-pressed.
    expect(conmutador.getAttribute('aria-label')).toBe('Lista de deseos: Yelmo del Alba');
  });

  test('al quitarlo, todo vuelve, sin dejar huecos', () => {
    const tarjeta = vista();
    pintarDeseo(document, UUID, true);

    pintarDeseo(document, UUID, false);

    expect(tarjeta.querySelector('[data-deseo]').getAttribute('aria-pressed')).toBe('false');
    expect(tarjeta.dataset.deseado).toBeUndefined();
    expect(tarjeta.querySelector('.producto-deseado')).toBeNull();
    expect(tarjeta.querySelector('.product-card__distintivos')).toBeNull();
  });

  test('pintar dos veces lo mismo no duplica el distintivo', () => {
    const tarjeta = vista();

    pintarDeseo(document, UUID, true);
    pintarDeseo(document, UUID, true);

    expect(tarjeta.querySelectorAll('.producto-deseado')).toHaveLength(1);
  });

  test('otro producto no se toca', () => {
    const tarjeta = vista();

    pintarDeseo(document, 'otro-id', true);

    expect(tarjeta.dataset.deseado).toBeUndefined();
    expect(tarjeta.querySelector('[data-deseo]').getAttribute('aria-pressed')).toBe('false');
  });
});
