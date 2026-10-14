/**
 * La cifra de la consola — SA-2: nunca un cero inventado, y el tono dicho con
 * franja, icono y texto, no solo con color.
 */

import { rejillaDeIndicadores, tarjetaDeIndicador } from './indicador.js';

test('una cifra con su etiqueta, su unidad y separador de miles', () => {
  const caja = tarjetaDeIndicador({ etiqueta: 'Cuentas', valor: 1894, unidad: 'cuentas' });

  expect(caja.dataset.indicador).toBe('Cuentas');
  expect(caja.querySelector('.indicador-admin__etiqueta').textContent).toBe('Cuentas');
  expect(caja.querySelector('.indicador-admin__cifra').textContent).toBe('1.894');
  expect(caja.querySelector('.indicador-admin__unidad').textContent).toBe('cuentas');
});

test('sin dato se pinta «—» y se dice «Sin dato», nunca 0', () => {
  for (const valor of [null, undefined, '', Number.NaN]) {
    const caja = tarjetaDeIndicador({ etiqueta: 'Baneadas', valor, unidad: 'cuentas' });
    expect(caja.dataset.sinDato).toBe('si');
    expect(caja.querySelector('.indicador-admin__valor').textContent).toBe('—Sin dato');
    expect(caja.querySelector('.indicador-admin__unidad')).toBeNull();
  }
});

test('un cero que llegó se pinta como cero', () => {
  const caja = tarjetaDeIndicador({ etiqueta: 'Baneadas', valor: 0 });
  expect(caja.dataset.sinDato).toBeUndefined();
  expect(caja.querySelector('.indicador-admin__cifra').textContent).toBe('0');
});

test('el tono lleva icono; sin tono no hay icono', () => {
  const alerta = tarjetaDeIndicador({ etiqueta: 'Caídos', valor: 2, tono: 'error' });
  expect(alerta.dataset.tono).toBe('error');
  expect(alerta.querySelector('svg.indicador-admin__icono').getAttribute('aria-hidden')).toBe(
    'true',
  );

  const neutra = tarjetaDeIndicador({ etiqueta: 'Torneos', valor: 1 });
  expect(neutra.dataset.tono).toBeUndefined();
  expect(neutra.querySelector('svg')).toBeNull();
});

test('el detalle y el enlace al detalle, si se dan', () => {
  const caja = tarjetaDeIndicador({
    etiqueta: 'Sanciones',
    valor: 3,
    detalle: '1 apelación pendiente',
    enlace: { texto: 'Ir a sanciones', href: '/admin/sanciones' },
  });
  expect(caja.querySelector('.indicador-admin__detalle').textContent).toBe('1 apelación pendiente');
  const enlace = caja.querySelector('a.indicador-admin__enlace');
  expect(enlace.textContent).toBe('Ir a sanciones');
  expect(enlace.getAttribute('href')).toBe('/admin/sanciones');
});

test('un texto se pinta tal cual («12 de 18»)', () => {
  const caja = tarjetaDeIndicador({ etiqueta: 'Servicios', valor: '12 de 18' });
  expect(caja.querySelector('.indicador-admin__cifra').textContent).toBe('12 de 18');
});

test('la rejilla dice cuántas cifras lleva, para repartirlas sin dejar una suelta', () => {
  const rejilla = rejillaDeIndicadores(
    [1, 2, 3].map((v) => tarjetaDeIndicador({ etiqueta: `c${v}`, valor: v })),
    { zona: 'estados' },
  );
  expect(rejilla.className).toBe('rejilla-indicadores-admin');
  expect(rejilla.dataset.cantidad).toBe('3');
  expect(rejilla.dataset.zona).toBe('estados');
  expect(rejilla.children).toHaveLength(3);
  expect(rejillaDeIndicadores([]).dataset.zona).toBeUndefined();
});
