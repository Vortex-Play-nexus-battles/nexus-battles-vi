/**
 * UXC-4 — la tienda en la portada pública (retroalimentación del profesor).
 */

import { jest } from '@jest/globals';

import {
  entrarParaComprar,
  llevarAlFormulario,
  pedirVitrinaPublica,
  pintarVitrinaPublica,
  PRODUCTOS_EN_PORTADA,
  rutaDeLaTienda,
} from './portada-tienda.js';

const esperar = () => new Promise((r) => setTimeout(r, 0));

function dto(i) {
  return {
    id: `p-${i}`,
    nombre: `Producto ${i}`,
    descripcion: 'Del catálogo',
    tipo: 'ARMA',
    precioFinal: 1000,
    precioOriginal: 1000,
    moneda: 'COP',
  };
}

beforeEach(() => {
  document.body.innerHTML = `
    <form id="formLogin"><input id="email" /></form>
    <div data-zona="productos-publicos"></div>
  `;
  sessionStorage.clear();
});

const zona = () => document.querySelector('[data-zona="productos-publicos"]');

describe('pedir la vitrina pública', () => {
  test('la primera página, del tamaño de la portada, sin identidad', async () => {
    const fetchImpl = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ content: [dto(1)], totalElements: 30 }),
    });

    const { productos, total } = await pedirVitrinaPublica({ fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toBe(`/api/v1/vitrina?size=${PRODUCTOS_EN_PORTADA}`);
    expect(fetchImpl.mock.calls[0][1].headers.Authorization).toBeUndefined();
    expect(productos).toHaveLength(1);
    expect(total).toBe(30);
  });

  test('un rechazo es un error, no una vitrina vacía', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValue({ ok: false, status: 503, json: async () => ({}) });

    await expect(pedirVitrinaPublica({ fetchImpl })).rejects.toThrow();
  });
});

describe('pintar la vitrina pública', () => {
  test('mientras carga, el esqueleto con nombre', () => {
    pintarVitrinaPublica(zona(), { pedir: () => new Promise(() => {}) });

    expect(zona().querySelector('[data-estado="cargando"]').getAttribute('aria-label')).toBe(
      'Cargando la tienda…',
    );
  });

  test('con productos: tarjetas sin «Añadir», y «Ver producto» abre el detalle', async () => {
    const abrir = jest.fn();
    await pintarVitrinaPublica(zona(), {
      pedir: async () => ({ productos: [dto(1), dto(2)], total: 2 }),
      abrir,
    });

    expect(zona().querySelectorAll('.product-card')).toHaveLength(2);
    expect(zona().querySelector('.btn-add')).toBeNull();
    zona().querySelector('[data-ver-producto="p-2"]').click();
    expect(abrir).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'p-2' }),
      zona().querySelector('[data-ver-producto="p-2"]'),
    );
  });

  test('vacía: se dice que no hay productos a la venta, sin fingir un error', async () => {
    await pintarVitrinaPublica(zona(), { pedir: async () => ({ productos: [], total: 0 }) });

    const vacio = zona().querySelector('[data-estado="vacio"]');
    expect(vacio.textContent).toMatch(/Todavía no hay productos a la venta/);
    expect(zona().querySelector('[data-estado="error"]')).toBeNull();
  });

  test('error: se dice sin códigos, y «Reintentar» vuelve a pedir sin duplicar escuchas', async () => {
    const abrir = jest.fn();
    const pedir = jest
      .fn()
      .mockRejectedValueOnce(new Error('La vitrina respondió 503'))
      .mockResolvedValue({ productos: [dto(1)], total: 1 });
    await pintarVitrinaPublica(zona(), { pedir, abrir });

    const error = zona().querySelector('[data-estado="error"]');
    expect(error.textContent).toMatch(/La tienda no responde ahora mismo/);
    expect(error.textContent).not.toMatch(/503/);

    error.querySelector('[data-accion="reintentar"]').click();
    await esperar();
    expect(zona().querySelectorAll('.product-card')).toHaveLength(1);

    zona().querySelector('[data-ver-producto]').click();
    expect(abrir).toHaveBeenCalledTimes(1);
  });
});

describe('entrar desde la portada', () => {
  test('«Entra para comprar» deja la vuelta a la tienda y enfoca el correo', () => {
    history.replaceState(null, '', '/frontend/app-web/src/cuentas/login.html');

    entrarParaComprar(document);

    const volver = new URLSearchParams(globalThis.location.search).get('volver');
    expect(volver).toBe(rutaDeLaTienda());
    expect(volver).toMatch(/tienda\.html$/);
    expect(document.activeElement).toBe(document.getElementById('email'));
  });

  test('llevar al formulario cierra la ficha abierta', () => {
    const cerrar = document.createElement('button');
    cerrar.className = 'ficha__cerrar';
    const alCerrar = jest.fn();
    cerrar.addEventListener('click', alCerrar);
    document.body.append(cerrar);

    llevarAlFormulario(document);

    expect(alCerrar).toHaveBeenCalled();
    expect(document.activeElement).toBe(document.getElementById('email'));
  });
});
