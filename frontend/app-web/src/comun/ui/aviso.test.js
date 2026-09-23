/** Aviso — el componente unico de retroalimentacion (PR-UX-1). */

import { jest } from '@jest/globals';

import { aviso, limpiarAviso, pintarAviso, tonoPorEstado } from './aviso.js';

function zona() {
  const div = document.createElement('div');
  div.hidden = true;
  document.body.append(div);
  return div;
}

describe('aviso', () => {
  test('lleva la clase del tono, el titulo y el detalle', () => {
    const caja = aviso({ tono: 'exito', titulo: 'Sala creada', detalle: '4 participantes' });

    expect(caja.className).toBe('aviso aviso--exito');
    expect(caja.querySelector('.aviso__titulo').textContent).toBe('Sala creada');
    expect(caja.querySelector('.aviso__detalle').textContent).toBe('4 participantes');
  });

  test('un fallo interrumpe (alert) y un exito solo informa (status)', () => {
    expect(aviso({ tono: 'error', titulo: 'x' }).getAttribute('role')).toBe('alert');
    expect(aviso({ tono: 'advertencia', titulo: 'x' }).getAttribute('role')).toBe('alert');
    expect(aviso({ tono: 'exito', titulo: 'x' }).getAttribute('role')).toBe('status');
    expect(aviso({ tono: 'info', titulo: 'x' }).getAttribute('role')).toBe('status');
  });

  test('sin detalle no se deja un parrafo vacio', () => {
    expect(aviso({ tono: 'info', titulo: 'x' }).querySelector('.aviso__detalle')).toBeNull();
  });

  test('puede llevar una accion que se puede pulsar', () => {
    const alPulsar = jest.fn();
    const caja = aviso({
      tono: 'error',
      titulo: 'No se pudo',
      accion: { texto: 'Reintentar', nombre: 'reintentar', alPulsar },
    });

    caja.querySelector('[data-accion="reintentar"]').click();

    expect(alPulsar).toHaveBeenCalledTimes(1);
  });
});

describe('pintarAviso y limpiarAviso', () => {
  test('pintar muestra la zona y reemplaza lo anterior', () => {
    const caja = zona();

    pintarAviso(caja, { tono: 'error', titulo: 'Primero' });
    pintarAviso(caja, { tono: 'exito', titulo: 'Segundo' });

    expect(caja.hidden).toBe(false);
    expect(caja.querySelectorAll('.aviso')).toHaveLength(1);
    expect(caja.textContent).toContain('Segundo');
  });

  test('limpiar vacia y vuelve a ocultar, para que no quede el fallo anterior', () => {
    const caja = zona();
    pintarAviso(caja, { tono: 'error', titulo: 'Fallo' });

    limpiarAviso(caja);

    expect(caja.hidden).toBe(true);
    expect(caja.childNodes).toHaveLength(0);
  });

  test('limpiar una zona que no existe no revienta', () => {
    expect(() => limpiarAviso(null)).not.toThrow();
  });
});

describe('tonoPorEstado (MAPEO-ERRORES tabla 4)', () => {
  test('4xx es algo que quien mira puede corregir: advertencia', () => {
    expect(tonoPorEstado(400)).toBe('advertencia');
    expect(tonoPorEstado(403)).toBe('advertencia');
    expect(tonoPorEstado(422)).toBe('advertencia');
  });

  test('5xx, 0 y lo desconocido no dependen de el: error', () => {
    expect(tonoPorEstado(500)).toBe('error');
    expect(tonoPorEstado(503)).toBe('error');
    expect(tonoPorEstado(0)).toBe('error');
    expect(tonoPorEstado(null)).toBe('error');
    expect(tonoPorEstado(undefined)).toBe('error');
  });
});
