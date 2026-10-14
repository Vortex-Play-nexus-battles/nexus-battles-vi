/**
 * La traduccion DTO -> vista de la tienda, probada contra la forma real del
 * contrato y no contra la que el frontend suponia (FI-R2).
 */

import {
  CREDITOS,
  aImporte,
  textoDeCreditos,
  textoDePrecio,
  aProductoDeVitrina,
  aFilaDeCarrito,
} from './tienda-adaptador.js';

describe('D-44 — créditos del juego', () => {
  test('se escriben con separadores es-CO y en singular cuando es uno', () => {
    expect(textoDeCreditos(1)).toBe('1 crédito');
    expect(textoDeCreditos(300)).toBe('300 créditos');
    expect(textoDeCreditos(1250)).toBe('1.250 créditos');
    expect(textoDeCreditos(0)).toBe('0 créditos');
  });

  test('sin cifra no hay texto: nunca «NaN créditos»', () => {
    expect(textoDeCreditos(null)).toBeNull();
    expect(textoDeCreditos(undefined)).toBeNull();
    expect(textoDeCreditos(Number.NaN)).toBeNull();
  });

  test('una orden en CREDITOS se escribe en créditos, no como una moneda', () => {
    expect(textoDePrecio(825, CREDITOS)).toBe('825 créditos');
    expect(textoDePrecio(825, 'COP')).toBe('825 COP');
  });

  test('la vitrina trae el precio en créditos que calculó el servidor; si no viene, null', () => {
    const conCreditos = aProductoDeVitrina({
      precioFinal: 6000,
      moneda: 'COP',
      precioCreditos: 300,
    });
    expect(conCreditos.precioCreditos).toBe(300);
    expect(conCreditos.precioCreditosTexto).toBe('300 créditos');

    for (const raro of [null, undefined, 0, -5, 12.5, '300']) {
      const sin = aProductoDeVitrina({ precioFinal: 6000, moneda: 'COP', precioCreditos: raro });
      expect(sin.precioCreditos).toBeNull();
      expect(sin.precioCreditosTexto).toBeNull();
    }
  });
});

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

  test('identifica como premium el producto que solo se adquiere con moneda real', () => {
    expect(aProductoDeVitrina({ precioFinal: 20000, precioCreditos: null }).premium).toBe(true);
    expect(aProductoDeVitrina({ precioFinal: 20000, precioCreditos: 300 }).premium).toBe(false);
    expect(aProductoDeVitrina({ premium: true, precioFinal: 20000 }).premium).toBe(true);
    expect(
      aProductoDeVitrina({ premium: false, precioFinal: 20000, precioCreditos: null }).premium,
    ).toBe(false);
  });
});

describe('aFilaDeCarrito', () => {
  test('formatea el subtotal y el unitario que el carrito si persiste', () => {
    const fila = aFilaDeCarrito(
      { id: 42, cantidad: 2, subtotal: 250, precioUnitario: 125, producto: { nombre: 'Pocion' } },
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

  test('expone el id del item: lo que necesita «Quitar» para llamar DELETE /carrito/items/{itemId}', () => {
    expect(aFilaDeCarrito({ id: 42 }, 'COP').id).toBe(42);
    expect(aFilaDeCarrito({}, 'COP').id).toBeNull();
  });

  // B5 — la linea dice si se puede pagar, cuantas admite y su imagen (1.4.0).
  test('el máximo lo dice el servidor, con el tope de 20; cero es agotado, no «sin dato»', () => {
    expect(aFilaDeCarrito({ maximo: 3 }).maximo).toBe(3);
    expect(aFilaDeCarrito({ maximo: 50 }).maximo).toBe(20);
    expect(aFilaDeCarrito({ maximo: 0 }).maximo).toBe(0);
    expect(aFilaDeCarrito({}).maximo).toBe(20);
    expect(aFilaDeCarrito({ maximo: null }).maximo).toBe(20);
  });

  test('una línea que no se puede pagar trae su motivo, dicho para el jugador', () => {
    const agotada = aFilaDeCarrito({ disponible: false, motivo: 'AGOTADO' });
    expect(agotada.disponible).toBe(false);
    expect(agotada.motivo).toBe('AGOTADO');
    expect(agotada.motivoTexto).toBe('Se agotó. Quítalo para pagar.');

    expect(
      aFilaDeCarrito({ disponible: false, motivo: 'TIRAJE_INSUFICIENTE' }).motivoTexto,
    ).toMatch(/Baja la cantidad/);
    expect(aFilaDeCarrito({ disponible: false, motivo: 'NUEVO' }).motivoTexto).toMatch(
      /Quítalo del carrito/,
    );
    const buena = aFilaDeCarrito({ disponible: true, motivo: 'AGOTADO' });
    expect(buena.motivo).toBeNull();
    expect(buena.motivoTexto).toBeNull();
    // Un carrito anterior a 1.4.0 no dice nada: se puede pagar.
    expect(aFilaDeCarrito({}).disponible).toBe(true);
  });

  test('la imagen del producto, si la trae; el id de la línea y del producto', () => {
    const fila = aFilaDeCarrito({
      id: 7,
      producto: { id: 'p-1', nombre: 'Yelmo', imagen: '/img/yelmo.png' },
    });
    expect(fila.imagen).toBe('/img/yelmo.png');
    expect(fila.id).toBe(7);
    expect(fila.productoId).toBe('p-1');
    expect(aFilaDeCarrito({ producto: { imagen: '  ' } }).imagen).toBeNull();
  });

  test('G3: una línea solo en créditos enseña los créditos del servidor, nunca «Sin precio» ni «0 COP»', () => {
    const fila = aFilaDeCarrito(
      {
        id: 3,
        cantidad: 2,
        precioUnitario: null,
        subtotal: null,
        precioCreditos: 120,
        subtotalCreditos: 240,
        soloEnCreditos: true,
        disponible: true,
        producto: { id: 'p-3', nombre: 'Amuleto', moneda: null },
      },
      'COP',
    );
    expect(fila.soloEnCreditos).toBe(true);
    expect(fila.unitarioTexto).toBe('120 créditos');
    expect(fila.subtotalTexto).toBe('240 créditos');
    expect(fila.subtotal).toBeNull();
    expect(fila.subtotalCreditos).toBe(240);
  });

  test('G3: una línea con los dos precios sigue en dinero real; sin dato de créditos no se inventa', () => {
    const ambos = aFilaDeCarrito(
      { precioUnitario: 6000, subtotal: 6000, precioCreditos: 300, subtotalCreditos: 300 },
      'COP',
    );
    expect(ambos.soloEnCreditos).toBe(false);
    expect(ambos.subtotalTexto).toBe('6.000 COP');
    expect(ambos.unitarioTexto).toBe('6.000 COP');

    const sinCifra = aFilaDeCarrito({ soloEnCreditos: true, subtotalCreditos: null }, 'COP');
    expect(sinCifra.subtotalTexto).toBeNull();
    expect(sinCifra.unitarioTexto).toBeNull();
  });

  test('G3: sin ningún precio el motivo lo dice así (ya no es «no se vende con dinero real»)', () => {
    expect(
      aFilaDeCarrito({ disponible: false, motivo: 'SIN_PRECIO_EN_MONEDA_REAL' }).motivoTexto,
    ).toBe('Ya no tiene precio de venta. Quítalo para pagar.');
  });
});

describe('G3 — producto que solo se vende en créditos', () => {
  test('sin precio en dinero real y con créditos: se vende en créditos', () => {
    const vm = aProductoDeVitrina({
      precioFinal: null,
      precioOriginal: null,
      moneda: null,
      precioCreditos: 120,
    });
    expect(vm.soloEnCreditos).toBe(true);
    expect(vm.precio).toBeNull();
    expect(vm.precioTexto).toBeNull();
    expect(vm.precioCreditosTexto).toBe('120 créditos');
  });

  test('con los dos precios, solo dinero real, o ninguno: no es «solo en créditos»', () => {
    expect(aProductoDeVitrina({ precioFinal: 6000, moneda: 'COP', precioCreditos: 300 })).toEqual(
      expect.objectContaining({ soloEnCreditos: false }),
    );
    expect(aProductoDeVitrina({ precioFinal: 6000, moneda: 'COP' }).soloEnCreditos).toBe(false);
    expect(aProductoDeVitrina({ precioFinal: null, precioCreditos: null }).soloEnCreditos).toBe(
      false,
    );
    // Un cero en créditos no es un precio (el contrato: mínimo 1).
    expect(aProductoDeVitrina({ precioFinal: null, precioCreditos: 0 }).soloEnCreditos).toBe(false);
  });
});
