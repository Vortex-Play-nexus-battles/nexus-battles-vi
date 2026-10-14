/** Fechas de la consola: días de filtro y días de serie (SA-4). */

import { diaCompleto, diaCorto, inicioDelMes, instanteDelDia, serieDiaria } from './fechas.js';

describe('días de un filtro', () => {
  test('un día es el principio de ese día en Colombia; «hasta» es el principio del siguiente', () => {
    expect(instanteDelDia('2026-10-05')).toBe('2026-10-05T00:00:00-05:00');
    expect(instanteDelDia('2026-10-31', { finDelDia: true })).toBe('2026-11-01T00:00:00-05:00');
    expect(instanteDelDia('ayer')).toBeNull();
    expect(instanteDelDia('')).toBeNull();
  });

  test('el mes en curso empieza el día 1 en Colombia aunque en UTC ya sea el siguiente', () => {
    // 1 de noviembre 02:00 UTC = 31 de octubre 21:00 en Colombia.
    expect(inicioDelMes(Date.UTC(2026, 10, 1, 2, 0))).toBe('2026-10-01T00:00:00-05:00');
  });
});

describe('días de una serie', () => {
  test('se escriben tal cual, sin correrse un día por la zona horaria', () => {
    // La etiqueta de una barra va sin «de», sea cual sea la versión de ICU.
    expect(diaCorto('2026-10-05')).toMatch(/^5 oct\.?$/);
    expect(diaCorto('2026-09-14')).toMatch(/^14 sept?\.?$/);
    // La fecha completa, «5 oct 2026» o «5 de oct de 2026», según el motor.
    expect(diaCompleto('2026-10-05')).toMatch(/^5 (de )?oct\.?( de)? 2026$/);
    expect(diaCorto(null)).toBe('sin fecha');
    expect(diaCompleto('no-es-un-dia')).toBe('no-es-un-dia');
  });

  test('los días sin actividad se rellenan con cero, del día de «desde» al de «hasta»', () => {
    const serie = serieDiaria(
      '2026-10-01T10:00:00Z',
      '2026-10-05T09:00:00Z',
      [
        { fecha: '2026-10-04', emitidas: 3 },
        { fecha: '2026-10-02', emitidas: 1 },
      ],
      (d) => d.emitidas,
    );
    expect(serie).toEqual([
      { fecha: '2026-10-01', valor: 0 },
      { fecha: '2026-10-02', valor: 1 },
      { fecha: '2026-10-03', valor: 0 },
      { fecha: '2026-10-04', valor: 3 },
      { fecha: '2026-10-05', valor: 0 },
    ]);
  });

  test('un «hasta» a medianoche UTC no cuenta ese día', () => {
    const serie = serieDiaria('2026-10-01T00:00:00Z', '2026-10-03T00:00:00Z', [], () => 0);
    expect(serie.map((d) => d.fecha)).toEqual(['2026-10-01', '2026-10-02']);
  });

  test('un periodo ilegible o enorme devuelve la serie tal cual la publicó el servicio', () => {
    const publicados = [{ fecha: '2026-10-02', reportes: 4 }];
    expect(serieDiaria('ayer', 'hoy', publicados, (d) => d.reportes)).toEqual([
      { fecha: '2026-10-02', valor: 4 },
    ]);
    expect(
      serieDiaria('2020-01-01T00:00:00Z', '2026-01-01T00:00:00Z', publicados, (d) => d.reportes),
    ).toEqual([{ fecha: '2026-10-02', valor: 4 }]);
  });
});
