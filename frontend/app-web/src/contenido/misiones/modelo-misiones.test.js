/**
 * UXC-5 — el vocabulario de las misiones y la fuente de hoy (sin servicio).
 */

import { jest } from '@jest/globals';

import {
  CATEGORIAS,
  DIFICULTADES,
  ESTADOS,
  FILTRO_DE_ESTADO,
  MISIONES_POR_PAGINA,
  categoriaDe,
  dificultadDe,
  estadoDe,
  nombreDeRotacion,
  textoDeDuracion,
  textoDeProbabilidad,
  textoDeTiempo,
} from './modelo-misiones.js';
import { FUENTE_SIN_SERVICIO, MisionesSinAbrir, fuenteDeMisiones } from './fuente-misiones.js';

describe('vocabulario de §7.8', () => {
  test('tres categorías en el orden de las pestañas, cada una con su regla', () => {
    expect(CATEGORIAS.map((c) => c.etiqueta)).toEqual(['Historia', 'Desafío', 'Exploración']);
    expect(categoriaDe('EXPLORACION').resumen).toMatch(/24 a 72 horas/);
    expect(categoriaDe('OTRA')).toBeNull();
  });

  test('cuatro dificultades, de una a cuatro marcas', () => {
    expect(DIFICULTADES.map((d) => [d.etiqueta, d.marcas])).toEqual([
      ['Fácil', 1],
      ['Normal', 2],
      ['Difícil', 3],
      ['Extremo', 4],
    ]);
    expect(dificultadDe('DIFICIL').etiqueta).toBe('Difícil');
  });

  test('seis estados con palabra e icono; uno desconocido no se disfraza de disponible', () => {
    expect(Object.values(ESTADOS).map((e) => e.texto)).toEqual([
      'Disponible',
      'Bloqueada',
      'En progreso',
      'Completada',
      'Fallida',
      'Abandonada',
    ]);
    expect(estadoDe('RARO')).toBeNull();
    expect(estadoDe('toString')).toBeNull();
  });

  test('el filtro de estado ofrece los tres del documento, más «todos»', () => {
    expect(FILTRO_DE_ESTADO.map((f) => f.etiqueta)).toEqual([
      'Todos los estados',
      'Disponibles',
      'En progreso',
      'Completadas',
    ]);
    expect(MISIONES_POR_PAGINA).toBe(16);
  });
});

describe('cifras', () => {
  test('la duración se dice en horas, como el documento', () => {
    expect(textoDeDuracion(12)).toBe('12 horas');
    expect(textoDeDuracion(1)).toBe('1 hora');
    expect(textoDeDuracion(72)).toBe('72 horas');
    expect(textoDeDuracion(0)).toBeNull();
    expect(textoDeDuracion(undefined)).toBeNull();
  });

  test('un tiempo del reporte, en horas y minutos', () => {
    expect(textoDeTiempo(11 * 3_600_000 + 42 * 60_000)).toBe('11 h 42 min');
    expect(textoDeTiempo(45 * 60_000)).toBe('45 min');
    expect(textoDeTiempo(2 * 3_600_000)).toBe('2 h');
    expect(textoDeTiempo(-1)).toBeNull();
  });

  test('la probabilidad no se redondea hacia arriba cuando es menor que un 1 %', () => {
    expect(textoDeProbabilidad(0.15)).toBe('15 %');
    expect(textoDeProbabilidad(0.6)).toBe('60 %');
    expect(textoDeProbabilidad(0.0015)).toBe('0,15 %');
    expect(textoDeProbabilidad(1.5)).toBeNull();
    expect(textoDeProbabilidad(null)).toBeNull();
  });

  test('cada rotación dice su prioridad', () => {
    expect(nombreDeRotacion(0)).toBe('Rotación 1 · prioridad alta');
    expect(nombreDeRotacion(2)).toBe('Rotación 3 · prioridad baja');
  });
});

describe('la fuente de misiones de este despliegue', () => {
  test('dice que no hay servicio y no toca la red', async () => {
    const buscar = jest.fn();
    globalThis.fetch = buscar;

    const fuente = fuenteDeMisiones();

    expect(fuente).toBe(FUENTE_SIN_SERVICIO);
    expect(fuente.disponible).toBe(false);
    for (const operacion of [
      'tablero',
      'destacadas',
      'detalle',
      'activas',
      'historial',
      'reporte',
      'matricular',
      'cancelar',
      'marcarFavorita',
    ]) {
      await expect(fuente[operacion]()).rejects.toBeInstanceOf(MisionesSinAbrir);
    }
    expect(buscar).not.toHaveBeenCalled();
  });
});
