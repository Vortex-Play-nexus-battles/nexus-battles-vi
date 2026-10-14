/**
 * Los nombres de producto de la consola — SA-2 (informe de revisión, puntos
 * 14, 15 y 22): servicios, parámetros y acciones de auditoría se leen como
 * los diría quien opera, nunca con la clave técnica ni con referencias
 * internas (D-xx, HU, Charter, PO).
 */

import { NOMBRE_DE_ACCION, nombreDeAccion } from './nombres.js';
import {
  CATALOGO_DE_PARAMETROS,
  GRUPOS_DE_PARAMETROS,
  fichaDeParametro,
  valorLegible,
} from './parametros.js';
import { NOMBRE_DEL_SERVICIO, SITUACION_DEL_SERVICIO, nombreDelServicio } from './servicios.js';

const INTERNO = /\bHU-|\bRF-|\bRNF-|\bD-\d|Charter|\bPO\b|pendiente del|_/;

describe('servicios', () => {
  test('cada servicio del sondeo tiene nombre de producto', () => {
    for (const nombre of [
      'ms-identidad',
      'ms-cumplimiento',
      'salas-partidas',
      'moderacion-sanciones',
      'metricas-plataforma',
      'admin-parametros',
      'ms-subastas',
      'ms-chatbot',
      'motor-combate',
    ]) {
      expect(NOMBRE_DEL_SERVICIO[nombre]).toBeTruthy();
      expect(nombreDelServicio(nombre)).not.toMatch(/^ms-|-/);
    }
  });

  test('un servicio desconocido se enseña tal cual, no se esconde', () => {
    expect(nombreDelServicio('ms-nuevo')).toBe('ms-nuevo');
    expect(nombreDelServicio('')).toBe('Servicio sin nombre');
  });

  test('cada estado de la sonda tiene su explicación', () => {
    for (const estado of ['OPERATIVO', 'LENTO', 'CAIDO', 'NO_DESPLEGADO', 'NO_OBSERVABLE']) {
      expect(SITUACION_DEL_SERVICIO[estado]).toMatch(/\.$/);
    }
  });
});

describe('parámetros', () => {
  test('cada parámetro del catálogo tiene nombre, ayuda y grupo conocido, sin referencias internas', () => {
    for (const [clave, ficha] of Object.entries(CATALOGO_DE_PARAMETROS)) {
      expect(ficha.nombre).toBeTruthy();
      expect(ficha.ayuda).toMatch(/\.$/);
      expect(GRUPOS_DE_PARAMETROS).toContain(ficha.grupo);
      expect(`${ficha.nombre} ${ficha.ayuda}`).not.toMatch(INTERNO);
      expect(ficha.nombre).not.toBe(clave);
    }
  });

  test('cubre los veintiocho parámetros que siembra el servicio', () => {
    // SA-4: V6 de admin-parametros añade el umbral de reportes por día.
    expect(Object.keys(CATALOGO_DE_PARAMETROS)).toHaveLength(28);
    expect(CATALOGO_DE_PARAMETROS['metricas.umbral-reportes-por-dia'].nombre).toBe(
      'Alerta de reportes por día',
    );
  });

  test('una clave desconocida conserva la descripción del servicio', () => {
    expect(fichaDeParametro({ clave: 'nuevo.parametro', descripcion: 'Algo nuevo' })).toEqual({
      nombre: 'nuevo.parametro',
      ayuda: 'Algo nuevo',
      grupo: 'Otros',
    });
  });

  test('el valor se lee con su unidad; sin valor, «Sin definir»', () => {
    expect(valorLegible({ valor: '30', unidad: 'dias' })).toBe('30 días');
    expect(valorLegible({ valor: '5', unidad: 'creditos' })).toBe('5 créditos');
    expect(valorLegible({ valor: '99.95', unidad: '%' })).toBe('99.95 %');
    expect(valorLegible({ valor: null, unidad: 'dias' })).toBe('Sin definir');
    expect(valorLegible({ valor: 'LIBERAR', unidad: null })).toBe('Se devuelven');
    expect(valorLegible({ valor: '3', unidad: 'otra' })).toBe('3 otra');
    expect(valorLegible({ valor: '20', unidad: 'reportes/dia' })).toBe('20 reportes por día');
  });
});

describe('acciones de auditoría', () => {
  test('cada tipo del contrato tiene nombre de producto', () => {
    for (const tipo of [
      'CREACION',
      'ACTUALIZACION',
      'ELIMINACION_LOGICA',
      'SUSPENSION',
      'SANCION',
      'CAMBIO_ROL',
      'APROBACION',
      'RECHAZO',
      'SECURITY_BYPASS_ATTEMPT',
      'ELIMINACION',
      'EXPORTACION',
      // SA-18 (ms-cumplimiento-auditoria.yaml 1.4.0): lo que importa del catálogo.
      'REACTIVACION',
      'REVERSION',
      'OTRO',
    ]) {
      expect(NOMBRE_DE_ACCION[tipo]).toBeTruthy();
      expect(nombreDeAccion(tipo)).not.toMatch(INTERNO);
    }
  });

  test('un tipo nuevo se humaniza', () => {
    expect(nombreDeAccion('RESTABLECIMIENTO_2FA')).toBe('Restablecimiento 2fa');
  });
});
