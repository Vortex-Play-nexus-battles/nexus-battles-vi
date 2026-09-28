/**
 * UXC-9 — el aviso de red transversal: dice «Sin conexión» cuando el
 * navegador pierde la red y «Conexión recuperada» al volver, una sola vez
 * por página, con texto además del color.
 */

import { jest } from '@jest/globals';

import { vigilarRed } from './aviso-de-red.js';

const aviso = () => document.querySelector('[data-zona="aviso-red"]');

beforeEach(() => {
  document.body.innerHTML = '';
});

test('sin red lo dice con texto, y al volver lo celebra y se va solo', () => {
  jest.useFakeTimers();
  try {
    const soltar = vigilarRed({ esperaAlVolver: 1000 });
    expect(aviso().hidden).toBe(true);

    window.dispatchEvent(new Event('offline'));
    expect(aviso().hidden).toBe(false);
    expect(aviso().dataset.estado).toBe('sin-red');
    expect(aviso().textContent).toContain('Sin conexión');
    expect(aviso().textContent).toContain('no llega al juego');
    expect(aviso().getAttribute('role')).toBe('status');

    window.dispatchEvent(new Event('online'));
    expect(aviso().dataset.estado).toBe('recuperada');
    expect(aviso().textContent).toContain('Conexión recuperada');

    jest.advanceTimersByTime(1000);
    expect(aviso().hidden).toBe(true);
    soltar();
    expect(aviso()).toBeNull();
  } finally {
    jest.useRealTimers();
  }
});

test('«online» sin haber caído no dice nada', () => {
  const soltar = vigilarRed();
  window.dispatchEvent(new Event('online'));
  expect(aviso().hidden).toBe(true);
  soltar();
});

test('se monta una sola vez por página', () => {
  const soltar = vigilarRed();
  const otra = vigilarRed();
  expect(document.querySelectorAll('[data-zona="aviso-red"]')).toHaveLength(1);
  otra();
  soltar();
});

test('si la página abre ya sin red, lo dice desde el principio', () => {
  const ventana = new EventTarget();
  ventana.navigator = { onLine: false };
  const soltar = vigilarRed({ ventana });
  expect(aviso().dataset.estado).toBe('sin-red');
  soltar();
});
