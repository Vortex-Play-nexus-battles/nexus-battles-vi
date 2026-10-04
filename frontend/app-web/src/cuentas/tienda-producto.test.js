/**
 * UXC-4 — la tarjeta de producto, el precio, la marca de «tuyo», la lista de
 * deseos (B5: guardada en la cuenta) y el bloque de compra del detalle.
 */

import { jest } from '@jest/globals';

import {
  bloqueDeCompra,
  conmutadorDeDeseos,
  distintivoDePropiedad,
  MODOS,
  precioDeProducto,
  tarjetaDeProducto,
  MOTIVO_YA_LO_TIENES,
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

  test('auditoría 30-sep · RF-CAR-004: lo que ya tienes no se ofrece para añadir, y se dice por qué', () => {
    for (const tarjeta of [
      tarjetaDeProducto(dto(), { unidadesPropias: 1 }),
      tarjetaDeProducto(dto({ esPropio: true })),
    ]) {
      const anadir = tarjeta.querySelector('.btn-add');
      expect(anadir.disabled).toBe(true);
      expect(anadir.dataset.motivo).toBe('propio');
      expect(anadir.dataset.producto).toBeUndefined();
      expect(anadir.title).toBe(MOTIVO_YA_LO_TIENES);
    }
    const nueva = tarjetaDeProducto(dto()).querySelector('.btn-add');
    expect(nueva.disabled).toBe(false);
    expect(nueva.dataset.producto).toBe(UUID);
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

  test('D-44: el otro precio, en créditos del juego, si el servidor lo da; si no, nada', () => {
    const conCreditos = precioDeProducto(aProductoDeVitrina(dto({ precioCreditos: 300 })));
    const sinCreditos = precioDeProducto(aProductoDeVitrina(dto({ precioCreditos: null })));

    expect(conCreditos.querySelector('.precio-creditos').textContent).toBe('o 300 créditos');
    expect(conCreditos.querySelector('.precio-creditos').dataset.precioCreditos).toBe('300');
    // El precio en dinero real sigue siendo el principal.
    expect(conCreditos.querySelector('.price').textContent).not.toMatch(/crédito/);
    expect(sinCreditos.querySelector('.precio-creditos')).toBeNull();
  });

  test('distintivo de propiedad en singular', () => {
    expect(distintivoDePropiedad(1).textContent).toBe('Ya lo tienes');
  });
});

describe('lista de deseos (B5)', () => {
  test('sin cuenta (la portada) se ve apagada, y el motivo está escrito y enlazado', () => {
    const caja = conmutadorDeDeseos({ idMotivo: 'motivo-1', productoId: UUID });
    document.body.replaceChildren(caja);
    const boton = caja.querySelector('[data-accion="lista-de-deseos"]');

    expect(boton.getAttribute('aria-disabled')).toBe('true');
    expect(boton.getAttribute('aria-pressed')).toBe('false');
    expect(boton.getAttribute('aria-describedby')).toBe('motivo-1');
    expect(boton.dataset.deseo).toBeUndefined();
    expect(document.getElementById('motivo-1').textContent).toMatch(/Entra con tu cuenta/);
  });

  test('en la tienda es un conmutador de verdad: aria-pressed y el producto', () => {
    const caja = conmutadorDeDeseos({
      idMotivo: 'motivo-2',
      productoId: UUID,
      nombre: 'Yelmo del Alba',
      deseado: true,
      activo: true,
    });
    const boton = caja.querySelector('[data-deseo]');

    expect(boton.dataset.deseo).toBe(UUID);
    expect(boton.getAttribute('aria-pressed')).toBe('true');
    expect(boton.hasAttribute('aria-disabled')).toBe(false);
    expect(boton.getAttribute('aria-label')).toBe('Lista de deseos: Yelmo del Alba');
    expect(caja.querySelector('.deseos__motivo')).toBeNull();
  });

  test('la tarjeta de la tienda lleva el corazón; la de la portada no', () => {
    const enTienda = tarjetaDeProducto(dto({ enListaDeseos: true })).querySelector('[data-deseo]');
    expect(enTienda.getAttribute('aria-pressed')).toBe('true');
    expect(enTienda.getAttribute('aria-label')).toBe('Lista de deseos: Yelmo del Alba');

    expect(
      tarjetaDeProducto(dto(), { modo: MODOS.PORTADA }).querySelector('[data-deseo]'),
    ).toBeNull();
    expect(tarjetaDeProducto(dto({ id: null })).querySelector('[data-deseo]')).toBeNull();
  });

  test('en el detalle, el corazón llama a la tienda y dice lo que pasó', async () => {
    const alDesear = jest
      .fn()
      .mockResolvedValueOnce({ ok: true, texto: '«Yelmo del Alba» está en tu lista de deseos.' })
      .mockResolvedValueOnce({ ok: false, texto: 'No se pudo quitar de tu lista de deseos.' });
    const bloque = bloqueDeCompra(dto(), { alAnadir: jest.fn(), alDesear });
    document.body.replaceChildren(bloque);
    const corazon = bloque.querySelector('[data-deseo]');
    const resultado = bloque.querySelector('.compra-producto__resultado');

    corazon.click();
    await esperar();
    expect(alDesear).toHaveBeenCalledWith(UUID);
    expect(resultado.dataset.tono).toBe('exito');
    expect(resultado.textContent).toBe('«Yelmo del Alba» está en tu lista de deseos.');

    corazon.click();
    await esperar();
    expect(resultado.dataset.tono).toBe('advertencia');
    expect(corazon.disabled).toBe(false);
  });

  test('en el detalle de la portada, apagado: no hay cuenta que guarde nada', () => {
    const bloque = bloqueDeCompra(dto(), { modo: MODOS.PORTADA, alEntrar: jest.fn() });

    expect(bloque.querySelector('[data-deseo]')).toBeNull();
    expect(bloque.querySelector('.deseos').getAttribute('aria-disabled')).toBe('true');
  });
});

describe('bloque de compra del detalle', () => {
  test('en la tienda: «Añadir al carrito» y lo que pasó, escrito al lado', async () => {
    const alAnadir = jest.fn().mockResolvedValueOnce({ ok: true }).mockResolvedValueOnce({
      ok: false,
      titulo: 'Ese producto se agotó',
      detalle: 'Ya no quedan.',
    });
    const bloque = bloqueDeCompra(dto(), { alAnadir });
    document.body.replaceChildren(bloque);

    expect(bloque.textContent).not.toContain('en tu inventario');
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

  test('auditoría 30-sep · RF-CAR-004: lo que ya tienes se dice, y «Añadir al carrito» se apaga con su motivo', async () => {
    const alAnadir = jest.fn();
    const bloque = bloqueDeCompra(dto(), { alAnadir, unidadesPropias: 1 });
    document.body.replaceChildren(bloque);

    expect(bloque.textContent).toContain('Ya tienes uno en tu inventario.');
    const boton = bloque.querySelector('[data-accion="anadir-al-carrito"]');
    expect(boton.disabled).toBe(true);
    expect(boton.title).toBe(MOTIVO_YA_LO_TIENES);
    boton.click();
    await esperar();
    expect(alAnadir).not.toHaveBeenCalled();

    // Sin inventario aún, la marca de la vitrina basta.
    const porLaVitrina = bloqueDeCompra(dto({ esPropio: true }), { alAnadir });
    expect(porLaVitrina.querySelector('[data-accion="anadir-al-carrito"]').disabled).toBe(true);
    expect(porLaVitrina.textContent).toContain('Ya tienes uno en tu inventario.');
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
