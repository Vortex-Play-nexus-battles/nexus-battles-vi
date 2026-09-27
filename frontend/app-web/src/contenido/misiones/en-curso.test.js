/**
 * UXC-5 — misiones en curso (ActiveMissionPanel).
 */

import { jest } from '@jest/globals';

import { montarEnCurso, tarjetaDeMisionActiva } from './en-curso.js';

const ACTIVA = {
  ejecucionId: 'e-1',
  misionId: 'templo',
  nombre: 'El Templo Olvidado',
  categoria: 'HISTORIA',
  heroe: { id: 'h-1', nombre: 'Aquiles', prototipo: 'Guerrero Armas' },
  iniciadaEn: '2026-09-25T08:00:00Z',
  terminaEn: '2099-01-01T00:00:00Z',
  progreso: 0.3,
  penalizacion: 'pierdes la recompensa garantizada.',
};

const rutas = {
  hrefDe: (m) => `misiones.html?mision=${m.id}`,
  hrefEquipamiento: '../inventario/inventario.html#equipamiento',
  hrefTablon: 'misiones.html',
};

const esperar = async () => {
  for (let i = 0; i < 6; i += 1) {
    await new Promise((resolver) => setTimeout(resolver, 0));
  }
};

beforeEach(() => {
  document.body.innerHTML = '<div id="zona"></div>';
});

test('cada misión en curso: cuánto le queda, su avance, el héroe y su equipo', () => {
  const tarjeta = tarjetaDeMisionActiva(ACTIVA, { ...rutas, alCancelar: () => {} });

  const cuenta = tarjeta.querySelector('time.cuenta-atras');
  expect(cuenta.getAttribute('datetime')).toBe(ACTIVA.terminaEn);
  expect(cuenta.getAttribute('aria-label')).toMatch(/^Termina en /);
  expect(tarjeta.querySelector('[role="progressbar"]').getAttribute('aria-valuenow')).toBe('30');
  expect(tarjeta.querySelector('.mision-activa__heroe').textContent).toContain('Aquiles');
  expect(tarjeta.querySelector('.mision-activa__equipo').getAttribute('href')).toBe(
    '../inventario/inventario.html#equipamiento',
  );
});

test('vacío: ningún héroe en misión, y el camino al tablón', async () => {
  const fuente = { activas: jest.fn(async () => []) };
  const alContar = jest.fn();
  montarEnCurso(document.getElementById('zona'), { fuente, ...rutas, alContar });
  await esperar();

  const vacio = document.querySelector('.estado-vista--vacio');
  expect(vacio.textContent).toContain('Ningún héroe está en misión');
  expect(vacio.querySelector('a').getAttribute('href')).toBe('misiones.html');
  expect(alContar).toHaveBeenCalledWith(0);
});

test('un fallo se dice y se reintenta', async () => {
  const fuente = {
    activas: jest.fn().mockRejectedValueOnce(new Error('503')).mockResolvedValue([ACTIVA]),
  };
  montarEnCurso(document.getElementById('zona'), { fuente, ...rutas });
  await esperar();

  const error = document.querySelector('.estado-vista--error');
  expect(error.textContent).toContain('No pudimos cargar tus misiones en curso');
  error.querySelector('button').click();
  await esperar();
  expect(document.querySelectorAll('.mision-activa')).toHaveLength(1);
});

test('cancelar pide confirmación con la penalización; confirmada, se cancela y se recarga', async () => {
  const fuente = {
    activas: jest.fn().mockResolvedValueOnce([ACTIVA]).mockResolvedValue([]),
    cancelar: jest.fn(async () => {}),
  };
  const { detener } = montarEnCurso(document.getElementById('zona'), { fuente, ...rutas });
  await esperar();

  document.querySelector('[data-accion="cancelar-mision"]').click();
  await esperar();
  const dialogo = document.querySelector('[role="dialog"]');
  expect(dialogo.textContent).toContain('¿Cancelar «El Templo Olvidado»?');
  expect(dialogo.textContent).toContain('Penalización: pierdes la recompensa garantizada.');

  dialogo.querySelector('[data-accion="confirmar"]').click();
  await esperar();

  expect(fuente.cancelar).toHaveBeenCalledWith('e-1');
  expect(document.querySelector('[data-zona="aviso"]').textContent).toContain(
    'Cancelaste «El Templo Olvidado»',
  );
  expect(document.querySelector('.estado-vista--vacio')).not.toBeNull();
  detener();
});

test('si se cierra el diálogo sin confirmar, no se cancela nada', async () => {
  const fuente = { activas: jest.fn(async () => [ACTIVA]), cancelar: jest.fn() };
  montarEnCurso(document.getElementById('zona'), { fuente, ...rutas });
  await esperar();

  document.querySelector('[data-accion="cancelar-mision"]').click();
  await esperar();
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
  await esperar();

  expect(fuente.cancelar).not.toHaveBeenCalled();
  expect(document.querySelectorAll('.mision-activa')).toHaveLength(1);
});

test('si cancelar falla, la misión sigue y se dice', async () => {
  const fuente = {
    activas: jest.fn(async () => [ACTIVA]),
    cancelar: jest.fn().mockRejectedValue(new Error('500')),
  };
  montarEnCurso(document.getElementById('zona'), { fuente, ...rutas });
  await esperar();

  document.querySelector('[data-accion="cancelar-mision"]').click();
  await esperar();
  document.querySelector('[data-accion="confirmar"]').click();
  await esperar();

  expect(document.querySelector('[data-zona="aviso"]').textContent).toContain(
    'No pudimos cancelar la misión',
  );
  expect(document.querySelector('[data-accion="cancelar-mision"]').disabled).toBe(false);
});
