/** Estados de una zona: vacio, cargando y error (PR-UX-1). */

import { jest } from '@jest/globals';

import { estadoDeCarga, estadoDeError, estadoVacio, pintarEstado } from './estado-vista.js';
import { esqueletoDeFilas, esqueletoDeTarjetas } from './esqueleto.js';

describe('estado vacio', () => {
  test('dice que falta y ofrece la accion que lo llena', () => {
    const alPulsar = jest.fn();
    const e = estadoVacio({
      titulo: 'Todavia no hay ningun torneo',
      detalle: 'Se abre uno cada 91 dias.',
      accion: { texto: 'Ver el reglamento', nombre: 'reglamento', alPulsar },
    });

    expect(e.dataset.estado).toBe('vacio');
    expect(e.getAttribute('role')).toBe('status');
    expect(e.querySelector('.estado-vista__titulo').textContent).toMatch(/ningun torneo/);
    e.querySelector('[data-accion="reglamento"]').click();
    expect(alPulsar).toHaveBeenCalled();
  });

  test('la accion puede ser un enlace, no siempre un boton', () => {
    const e = estadoVacio({
      titulo: 'No tienes heroes',
      accion: { texto: 'Ir a la tienda', href: '../cuentas/tienda.html' },
    });

    const enlace = e.querySelector('a');
    expect(enlace.getAttribute('href')).toBe('../cuentas/tienda.html');
  });
});

describe('estado de error', () => {
  test('no menciona codigos y ofrece reintentar', () => {
    const alReintentar = jest.fn();
    const e = estadoDeError({ alReintentar });

    expect(e.dataset.estado).toBe('error');
    expect(e.getAttribute('role')).toBe('alert');
    expect(e.textContent).not.toMatch(/\b[45]\d\d\b/);
    e.querySelector('[data-accion="reintentar"]').click();
    expect(alReintentar).toHaveBeenCalled();
  });

  test('sin funcion de reintento no se pinta un boton que no hace nada', () => {
    expect(estadoDeError().querySelector('[data-accion="reintentar"]')).toBeNull();
  });
});

describe('estado de carga', () => {
  test('muestra esqueletos y se anuncia como ocupado', () => {
    const e = estadoDeCarga({ filas: 4 });

    expect(e.getAttribute('aria-busy')).toBe('true');
    expect(e.getAttribute('aria-live')).toBe('polite');
    expect(e.querySelectorAll('.esqueleto')).toHaveLength(4);
  });
});

describe('esqueletos', () => {
  test('la rejilla de tarjetas se esconde de los lectores de pantalla', () => {
    const r = esqueletoDeTarjetas(3);

    expect(r.getAttribute('aria-hidden')).toBe('true');
    expect(r.querySelectorAll('.tarjeta')).toHaveLength(3);
  });

  test('las filas fantasma respetan el numero de columnas de la tabla', () => {
    const tbody = document.createElement('tbody');
    tbody.append(esqueletoDeFilas(4, 2));

    expect(tbody.querySelectorAll('tr')).toHaveLength(2);
    expect(tbody.querySelectorAll('tr')[0].children).toHaveLength(4);
  });
});

describe('pintarEstado', () => {
  test('reemplaza lo que hubiera en la zona', () => {
    const caja = document.createElement('div');
    caja.append(document.createElement('p'));

    pintarEstado(caja, estadoVacio({ titulo: 'Nada por aqui' }));

    expect(caja.querySelectorAll('p')).toHaveLength(1);
    expect(caja.querySelector('.estado-vista')).not.toBeNull();
  });
});
