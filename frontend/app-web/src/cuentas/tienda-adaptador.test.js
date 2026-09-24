/**
 * La traduccion DTO -> vista de la tienda, probada contra la forma real del
 * contrato y no contra la que el frontend suponia (FI-R2).
 */

import { aImporte, textoDePrecio, aProductoDeVitrina, aFilaDeCarrito } from './tienda-adaptador.js';

describe('aImporte', () => {
  test('acepta el numero y la cadena con que se serializa un BigDecimal', () => {
    expect(aImporte(18500)).toBe(18500);
    expect(aImporte('18500.00')).toBe(18500);
    expect(aImporte(0)).toBe(0);
  });

  test('lo que no es un importe es null, nunca cero', () => {
    expect(aImporte(null)).toBeNull();
    expect(aImporte(undefined)).toBeNull();
    expect(aImporte('')).toBeNull();
    expect(aImporte('gratis')).toBeNull();
    expect(aImporte(NaN)).toBeNull();
  });
});

describe('textoDePrecio', () => {
  test('separa los miles a la colombiana y pone la moneda que le den', () => {
    expect(textoDePrecio(1234567, 'COP')).toBe('1.234.567 COP');
    expect(textoDePrecio(12, 'USD')).toBe('12 USD');
  });

  test('sin moneda ensena la cifra sola, sin suponer COP', () => {
    expect(textoDePrecio(500, null)).toBe('500');
  });

  test('sin importe no hay texto', () => {
    expect(textoDePrecio(null, 'COP')).toBeNull();
  });
});

describe('aProductoDeVitrina', () => {
  const base = {
    id: 3,
    nombre: 'Yelmo',
    descripcion: 'Acero',
    habilidades: 'Defensa +4',
    tipo: 'ARMADURA',
    imagenUrl: 'https://cdn.example/y.png',
    precioFinal: 16000,
    precioOriginal: 20000,
    moneda: 'COP',
    enPromocion: true,
    porcentajeDescuento: 20,
    esPropio: false,
    enListaDeseos: true,
  };

  test('lee precioFinal, no un campo `precio` que el DTO no tiene', () => {
    const vm = aProductoDeVitrina(base);
    expect(vm.precio).toBe(16000);
    expect(vm.precioTexto).toBe('16.000 COP');
  });

  test('un DTO con `precio` en vez de `precioFinal` no se cuela como cero', () => {
    // Exactamente la forma que montaban las pruebas viejas. Ahora se ve que
    // ese objeto no trae precio, en vez de convertirse en 0.
    const vm = aProductoDeVitrina({ id: 1, nombre: 'Espada', precio: 100 });
    expect(vm.precio).toBeNull();
    expect(vm.precioTexto).toBeNull();
  });

  test('la rebaja se anuncia solo cuando los dos precios difieren', () => {
    expect(aProductoDeVitrina(base).descuento).toBe(20);
    expect(aProductoDeVitrina(base).precioAnteriorTexto).toBe('20.000 COP');

    const sinRebaja = aProductoDeVitrina({ ...base, precioOriginal: 16000 });
    expect(sinRebaja.descuento).toBeNull();
    expect(sinRebaja.precioAnteriorTexto).toBeNull();
  });

  test('un porcentaje de cero no es una promocion', () => {
    const vm = aProductoDeVitrina({ ...base, porcentajeDescuento: 0 });
    expect(vm.descuento).toBeNull();
  });

  test('los campos de texto ausentes quedan vacios o null, no «undefined»', () => {
    const vm = aProductoDeVitrina({});
    expect(vm.nombre).toBe('');
    expect(vm.descripcion).toBe('');
    expect(vm.habilidades).toBeNull();
    expect(vm.imagenUrl).toBeNull();
    expect(vm.id).toBeNull();
    expect(vm.moneda).toBeNull();
  });

  test('R16: el id UUID del catálogo se conserva tal cual, en texto', () => {
    // Es lo que `tienda.js` manda a POST /carrito/items. Un `Number()` por el
    // camino lo convertiría en NaN y el producto en «inexistente».
    const uuid = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
    const vm = aProductoDeVitrina({ ...base, id: uuid });
    expect(vm.id).toBe(uuid);
    expect(typeof vm.id).toBe('string');
  });

  test('esPropio y enListaDeseos solo son ciertos si el servicio dice true', () => {
    const vm = aProductoDeVitrina(base);
    expect(vm.esPropio).toBe(false);
    expect(vm.enListaDeseos).toBe(true);
    expect(aProductoDeVitrina({ esPropio: 'si' }).esPropio).toBe(false);
  });
});

describe('aFilaDeCarrito', () => {
  test('formatea el subtotal y el unitario que el carrito si persiste', () => {
    const fila = aFilaDeCarrito(
      { cantidad: 2, subtotal: 250, precioUnitario: 125, producto: { nombre: 'Pocion' } },
      'COP',
    );
    expect(fila.nombre).toBe('Pocion');
    expect(fila.cantidad).toBe(2);
    expect(fila.subtotalTexto).toBe('250 COP');
    expect(fila.unitarioTexto).toBe('125 COP');
  });

  test('un item incompleto no produce «undefined COP»', () => {
    const fila = aFilaDeCarrito({}, 'COP');
    expect(fila.nombre).toBe('Producto');
    expect(fila.cantidad).toBe(1);
    expect(fila.subtotalTexto).toBeNull();
    expect(fila.unitarioTexto).toBeNull();
  });
});
