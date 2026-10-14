/**
 * HU-PRD-010 — la vista previa usa la tarjeta de la tienda con lo que la
 * tienda recibiría (ProductoDeVitrina) y el precio con la regla de la vitrina.
 */

import {
  comoProductoDeVitrina,
  conDescuento,
  porcentajeVigente,
  vistaPreviaDeProducto,
} from './vista-previa-producto.js';

const AHORA = Date.parse('2026-10-14T15:00:00Z');
const PROMO = { porcentaje: 15, desde: '2026-10-01T00:00:00Z', hasta: '2026-10-31T00:00:00Z' };

test('la promoción solo cuenta si está vigente y es de 1 a 99 %', () => {
  expect(porcentajeVigente(PROMO, AHORA)).toBe(15);
  expect(porcentajeVigente({ ...PROMO, desde: '2026-10-20T00:00:00Z' }, AHORA)).toBeNull();
  expect(porcentajeVigente({ ...PROMO, hasta: '2026-10-14T15:00:00Z' }, AHORA)).toBeNull();
  expect(porcentajeVigente({ ...PROMO, porcentaje: 100 }, AHORA)).toBeNull();
  expect(porcentajeVigente(null, AHORA)).toBeNull();
});

test('el descuento redondea a la mitad hacia arriba y nunca baja de 1, como la vitrina', () => {
  expect(conDescuento(250, 15)).toBe(213);
  expect(conDescuento(1200, 20)).toBe(960);
  expect(conDescuento(1, 99)).toBe(1);
  expect(conDescuento(500, null)).toBe(500);
});

test('un producto en créditos y uno premium se proyectan como los vería la tienda', () => {
  expect(
    comoProductoDeVitrina(
      { nombre: 'Yelmo', tipo: 'ARMADURA', imagen: '/r/1', precioCreditos: 1200, promocion: PROMO },
      AHORA,
    ),
  ).toEqual({
    id: 'vista-previa',
    nombre: 'Yelmo',
    descripcion: '',
    tipo: 'ARMADURA',
    imagenUrl: '/r/1',
    precioFinal: null,
    precioOriginal: null,
    moneda: null,
    porcentajeDescuento: 15,
    precioCreditos: 1020,
  });
  expect(
    comoProductoDeVitrina(
      { nombre: 'Gema', premium: true, precioMonedaReal: 18500, tipo: 'ITEM' },
      AHORA,
    ),
  ).toMatchObject({
    precioFinal: 18500,
    precioOriginal: 18500,
    moneda: 'COP',
    precioCreditos: null,
  });
});

test('la tarjeta va sin botones activos y con su leyenda', () => {
  const previa = vistaPreviaDeProducto(
    { nombre: 'Yelmo', tipo: 'ARMADURA', imagen: '/r/1', precioCreditos: 900 },
    { ahora: AHORA },
  );
  expect(previa.querySelector('.product-card__nombre').textContent).toBe('Yelmo');
  expect(previa.querySelector('.product-image img').getAttribute('src')).toBe('/r/1');
  expect([...previa.querySelectorAll('button')].every((b) => b.disabled && b.tabIndex === -1)).toBe(
    true,
  );
  expect(previa.querySelector('figcaption').textContent).toBe(
    'Así lo verá el jugador en la tienda.',
  );
  expect(previa.textContent).toContain('900');
});
