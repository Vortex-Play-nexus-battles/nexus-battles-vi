/** Las alertas de moderación (§7.3.8, SA-4): lo que se dice de cada serie. */

import {
  SERIES_CON_UMBRAL,
  contar,
  fraseDeNoDisponible,
  fraseDeUmbral,
  pintarAlertasDeModeracion,
} from './alertas-de-moderacion.js';

const caja = (datos) => {
  const contenedor = document.createElement('div');
  contenedor.append(...pintarAlertasDeModeracion(datos));
  return contenedor;
};

const serie = (contenedor, tipo) => contenedor.querySelector(`[data-serie="${tipo}"]`);

const REPORTES = { total: 30, porDia: [{ fecha: '2026-10-10', reportes: 25 }] };

describe('frases', () => {
  const [sanciones, reportes] = SERIES_CON_UMBRAL;

  test('cuenta en singular y en plural', () => {
    expect(contar(1, sanciones)).toBe('1 sanción');
    expect(contar(4, sanciones)).toBe('4 sanciones');
    expect(contar(1, reportes)).toBe('1 reporte');
  });

  test('con umbral dice a partir de cuánto avisa; sin umbral, que no se evalúa y dónde se fija', () => {
    expect(fraseDeUmbral(sanciones, 3)).toBe('Se avisa a partir de 3 sanciones en un mismo día.');
    const sinUmbral = fraseDeUmbral(reportes, null);
    expect(sinUmbral).toMatch(/^Sin umbral de reportes/);
    expect(sinUmbral).toMatch(/no se evalúa ninguna alerta/);
    expect(sinUmbral).toMatch(/Parámetros/);
  });

  test('un indicador que falta se dice en una frase, sin códigos ni servicios', () => {
    expect(fraseDeNoDisponible({ indicador: 'REGISTRO_DE_USUARIOS', causa: 'SIN_PERMISO' })).toBe(
      'Las cuentas nuevas por día no están disponibles: tu rol no tiene permiso para consultar ese dato. El resto del tablero sí está al día.',
    );
    const reportesCaidos = fraseDeNoDisponible({
      indicador: 'FRECUENCIA_DE_REPORTES',
      causa: 'NO_RESPONDE',
    });
    expect(reportesCaidos).toMatch(/^La frecuencia de reportes no está disponible/);
    expect(reportesCaidos).not.toMatch(/comentarios|ms-|HTTP|\d{3}/);
    expect(fraseDeNoDisponible({ indicador: 'OTRO', causa: 'RARA' })).toBe(
      'Un indicador no está disponible: no se pudo leer. El resto del tablero sí está al día.',
    );
  });
});

describe('las dos series', () => {
  test('con el umbral de sanciones del PO y sin el de reportes: una avisa, la otra está desactivada', () => {
    const contenedor = caja({
      umbralSancionesPorDia: 3,
      umbralReportesPorDia: null,
      avisos: [
        { tipo: 'SANCIONES', fecha: '2026-09-28', cantidad: 5, umbral: 3 },
        { tipo: 'SANCIONES', fecha: '2026-09-29', cantidad: 3, umbral: 3 },
      ],
      reportes: REPORTES,
    });

    expect(contenedor.querySelector('[data-zona="alertas"]')).not.toBeNull();
    const sanciones = serie(contenedor, 'SANCIONES');
    expect(sanciones.querySelector('.estado-admin').textContent).toBe('2 alertas');
    expect(sanciones.querySelector('.estado-admin').dataset.tono).toBe('error');
    expect(sanciones.querySelector('[data-zona="umbral"]').textContent).toBe(
      'Se avisa a partir de 3 sanciones en un mismo día.',
    );
    const dias = sanciones.querySelectorAll('[data-zona="avisos"] li');
    expect([...dias].map((li) => li.dataset.fecha)).toEqual(['2026-09-28', '2026-09-29']);
    expect(dias[0].textContent).toMatch(/28 (de )?sept?\.?( de)? 2026: 5 sanciones/);

    const reportes = serie(contenedor, 'REPORTES');
    expect(reportes.querySelector('.estado-admin').textContent).toBe('Desactivada');
    expect(reportes.querySelector('[data-zona="sin-umbral"]').textContent).toMatch(
      /no se evalúa ninguna alerta/,
    );
    expect(reportes.querySelector('[data-zona="avisos"]')).toBeNull();
  });

  test('con umbral y sin días que lleguen: «sin alertas», con la cifra', () => {
    const contenedor = caja({
      umbralSancionesPorDia: 10,
      umbralReportesPorDia: 20,
      avisos: [],
      reportes: REPORTES,
    });
    const sanciones = serie(contenedor, 'SANCIONES');
    expect(sanciones.querySelector('.estado-admin').textContent).toBe('Sin alertas');
    expect(sanciones.querySelector('.estado-admin').dataset.tono).toBe('exito');
    expect(sanciones.querySelector('[data-zona="sin-alertas"]').textContent).toBe(
      'Ningún día llegó a 10 sanciones.',
    );
    expect(
      serie(contenedor, 'REPORTES').querySelector('[data-zona="sin-alertas"]').textContent,
    ).toBe('Ningún día llegó a 20 reportes.');
  });

  test('con umbral de reportes, los días que llegaron; sin la frecuencia, «sin datos» y no «sin alertas»', () => {
    const conDias = caja({
      umbralSancionesPorDia: 3,
      umbralReportesPorDia: 20,
      avisos: [{ tipo: 'REPORTES', fecha: '2026-10-10', cantidad: 25, umbral: 20 }],
      reportes: REPORTES,
    });
    const reportes = serie(conDias, 'REPORTES');
    expect(reportes.querySelector('.estado-admin').textContent).toBe('1 alerta');
    expect(reportes.querySelector('[data-zona="avisos"] li').textContent).toMatch(/25 reportes/);
    expect(serie(conDias, 'SANCIONES').querySelector('.estado-admin').textContent).toBe(
      'Sin alertas',
    );

    const sinFrecuencia = caja({
      umbralSancionesPorDia: 3,
      umbralReportesPorDia: 20,
      avisos: [],
      reportes: null,
    });
    const sinDatos = serie(sinFrecuencia, 'REPORTES');
    expect(sinDatos.querySelector('.estado-admin').textContent).toBe('Sin datos');
    expect(sinDatos.querySelector('[data-zona="sin-datos"]').textContent).toMatch(
      /No se pudo evaluar/,
    );
    expect(sinDatos.querySelector('[data-zona="sin-alertas"]')).toBeNull();
  });

  test('un servicio anterior (solo `alertas` redactadas) no pierde sus alertas de sanciones', () => {
    const contenedor = caja({
      alertasConfiguradas: true,
      umbralSancionesPorDia: 10,
      alertas: ['alta frecuencia de sanciones el 2026-09-20: 14 (umbral: 10 o mas por dia)'],
    });
    const sanciones = serie(contenedor, 'SANCIONES');
    expect(sanciones.querySelector('.estado-admin').textContent).toBe('1 alerta');
    expect(sanciones.querySelector('[data-zona="avisos"]').textContent).toContain('2026-09-20');
    expect(serie(contenedor, 'REPORTES').querySelector('.estado-admin').textContent).toBe(
      'Desactivada',
    );
  });

  test('las notas técnicas de `pendientes` no se pintan nunca', () => {
    const contenedor = caja({
      umbralSancionesPorDia: 3,
      avisos: [],
      pendientes: ['frecuencia de reportes: comentarios no responde: I/O error on GET'],
      reportes: null,
    });
    expect(contenedor.textContent).not.toMatch(/I\/O|GET|comentarios no responde/);
  });
});
