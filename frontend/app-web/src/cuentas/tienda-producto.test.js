/**
 * UXC-4 — la tarjeta de producto, el precio, la marca de «tuyo», la lista de
 * deseos sin servicio y el bloque de compra del detalle.
 */

import { jest } from '@jest/globals';

import {
  bloqueDeCompra,
  conmutadorDeDeseos,
  distintivoDePropiedad,
  MODOS,
  precioDeProducto,
  tarjetaDeProducto,
} from './tienda-producto.js';
import { aProductoDeVitrina } from './tienda-adaptador.js';

const UUID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

function dto(cambios = {}) {
  return {
    id: UUID,
    nombre: 'Yelmo del Alba',
    descripcion: 'Acero claro',
    habilidades: 'Defensa +4',
    tipo: 'ARMADURA',
    precioFinal: 18500,
    precioOriginal: 18500,
    moneda: 'COP',
    enPromocion: false,
    porcentajeDescuento: null,
    esPropio: false,
    enListaDeseos: false,
    ...cambios,
  };
}

const esperar = () => new Promise((r) => setTimeout(r, 0));

describe('tarjeta en la tienda', () => {
  test('tipo, nombre h3, descripción, habilidades, precio, «Ver producto» y «Añadir»', () => {
    const tarjeta = tarjetaDeProducto(dto());

    expect(tarjeta.classList.contains('product-card')).toBe(true);
    expect(tarjeta.dataset.idProducto).toBe(UUID);
    expect(tarjeta.querySelector('.product-card__tipo').textContent).toBe('Armadura');
    expect(tarjeta.querySelector('h3.product-card__nombre').textContent).toBe('Yelmo del Alba');
    expect(tarjeta.querySelector('.habilidades').textContent).toBe('Defensa +4');
    expect(tarjeta.querySelector('.price').textContent).toBe('18.500 COP');
    const ver = tarjeta.querySelector('[data-ver-producto]');
    expect(ver.dataset.verProducto).toBe(UUID);
    // El nombre accesible empieza por lo que se lee (WCAG 2.5.3).
    expect(ver.getAttribute('aria-label')).toBe('Ver producto: Yelmo del Alba');
    const anadir = tarjeta.querySelector('.btn-add');
    expect(anadir.dataset.producto).toBe(UUID);
    expect(anadir.getAttribute('aria-label')).toMatch(/^Añadir Yelmo del Alba/);
  });

  test('lo que no viene no deja rastro: ni «null» ni huecos', () => {
    const tarjeta = tarjetaDeProducto(dto({ descripcion: '', habilidades: null }));

    expect(tarjeta.textContent).not.toContain('null');
    expect(tarjeta.textContent).not.toContain('undefined');
    expect(tarjeta.querySelector('.product-card__descripcion')).toBeNull();
    expect(tarjeta.querySelector('.habilidades')).toBeNull();
    expect(tarjeta.querySelector('.product-card__distintivos')).toBeNull();
  });

  test('sin imagen, el símbolo del tipo; con imagen, la imagen', () => {
    expect(tarjetaDeProducto(dto()).querySelector('.product-image svg')).not.toBeNull();
    const conImagen = tarjetaDeProducto(dto({ imagenUrl: '/img/yelmo.png' }));
    expect(conImagen.querySelector('.product-image img').getAttribute('src')).toBe(
      '/img/yelmo.png',
    );
  });

  test('lo que ya tienes lleva su marca, con las unidades', () => {
    const tarjeta = tarjetaDeProducto(dto(), { unidadesPropias: 2 });

    expect(tarjeta.dataset.propio).toBe('si');
    expect(tarjeta.querySelector('.producto-propio').textContent).toBe('Tienes 2');
    expect(tarjetaDeProducto(dto(), { unidadesPropias: 1 }).textContent).toContain('Ya lo tienes');
    expect(tarjetaDeProducto(dto()).querySelector('.producto-propio')).toBeNull();
  });

  test('solo si el servicio lo dice, «En tu lista de deseos»', () => {
    expect(tarjetaDeProducto(dto({ enListaDeseos: true })).textContent).toContain(
      'En tu lista de deseos',
    );
    expect(tarjetaDeProducto(dto()).textContent).not.toContain('lista de deseos');
  });

  test('sin id: ni «Ver» ni «Añadir» llevan a ninguna parte', () => {
    const tarjeta = tarjetaDeProducto(dto({ id: null }));

    expect(tarjeta.querySelector('.btn-add').disabled).toBe(true);
    expect(tarjeta.querySelector('.btn-add').dataset.producto).toBeUndefined();
    expect(tarjeta.querySelector('.product-card__ver').disabled).toBe(true);
  });
});

describe('tarjeta en la portada', () => {
  test('sin «Añadir» (no hay sesión ni carrito); «Ver producto» sí', () => {
    const tarjeta = tarjetaDeProducto(dto(), { modo: MODOS.PORTADA });

    expect(tarjeta.querySelector('.btn-add')).toBeNull();
    expect(tarjeta.querySelector('[data-ver-producto]')).not.toBeNull();
  });
});

describe('precio', () => {
  test('rebaja real: el de antes tachado y el porcentaje', () => {
    const precio = precioDeProducto(
      aProductoDeVitrina(
        dto({ precioOriginal: 20000, precioFinal: 16000, porcentajeDescuento: 20 }),
      ),
    );

    expect(precio.querySelector('.price').textContent).toBe('16.000 COP');
    expect(precio.querySelector('.price-antes').textContent).toBe('20.000 COP');
    expect(precio.querySelector('.badge-descuento').textContent).toBe('-20%');
    expect(precio.querySelector('.badge-descuento').getAttribute('aria-label')).toBe(
      '20 % de descuento',
    );
  });

  test('sin precio se dice, no se inventa un cero', () => {
    const precio = precioDeProducto(aProductoDeVitrina(dto({ precioFinal: null })));

    expect(precio.querySelector('.price').textContent).toBe('Precio no disponible');
    expect(precio.querySelector('.precio-ausente')).not.toBeNull();
  });

  test('distintivo de propiedad en singular', () => {
    expect(distintivoDePropiedad(1).textContent).toBe('Ya lo tienes');
  });
});

describe('lista de deseos sin servicio', () => {
  test('se ve, no se usa, y el motivo está escrito y enlazado', () => {
    const caja = conmutadorDeDeseos({ idMotivo: 'motivo-1' });
    document.body.replaceChildren(caja);
    const boton = caja.querySelector('[data-accion="lista-de-deseos"]');

    expect(boton.getAttribute('aria-disabled')).toBe('true');
    expect(boton.getAttribute('aria-pressed')).toBe('false');
    expect(boton.getAttribute('aria-describedby')).toBe('motivo-1');
    expect(document.getElementById('motivo-1').textContent).toMatch(/Todavía no se puede guardar/);
  });
});

describe('bloque de compra del detalle', () => {
  test('en la tienda: «Añadir al carrito» y lo que pasó, escrito al lado', async () => {
    const alAnadir = jest.fn().mockResolvedValueOnce({ ok: true }).mockResolvedValueOnce({
      ok: false,
      titulo: 'Ese producto se agotó',
      detalle: 'Ya no quedan.',
    });
    const bloque = bloqueDeCompra(dto(), { alAnadir, unidadesPropias: 1 });
    document.body.replaceChildren(bloque);

    expect(bloque.textContent).toContain('Ya tienes uno en tu inventario.');
    const boton = bloque.querySelector('[data-accion="anadir-al-carrito"]');
    boton.click();
    await esperar();
    expect(alAnadir).toHaveBeenCalledWith(UUID);
    const resultado = bloque.querySelector('.compra-producto__resultado');
    expect(resultado.hidden).toBe(false);
    expect(resultado.dataset.tono).toBe('exito');
    expect(resultado.textContent).toBe('Añadido a tu carrito.');
    expect(boton.disabled).toBe(false);

    boton.click();
    await esperar();
    expect(resultado.dataset.tono).toBe('advertencia');
    expect(resultado.textContent).toBe('Ese producto se agotó. Ya no quedan.');
  });

  test('en la portada: «Entra para comprar», sin carrito', () => {
    const alEntrar = jest.fn();
    const bloque = bloqueDeCompra(dto(), { modo: MODOS.PORTADA, alEntrar });

    expect(bloque.querySelector('[data-accion="anadir-al-carrito"]')).toBeNull();
    bloque.querySelector('[data-accion="entrar-para-comprar"]').click();
    expect(alEntrar).toHaveBeenCalled();
    expect(bloque.textContent).toMatch(/lo calificas y opinas/);
  });
});
