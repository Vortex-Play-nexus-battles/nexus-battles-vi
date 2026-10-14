/**
 * Métricas de plataforma — SA-8 (puntos 23 y 24 del informe de revisión):
 * cifras, tablas de la consola por fuente y actualización en vivo, sobre
 * metricas-plataforma.yaml.
 */

import { jest } from '@jest/globals';

import {
  cifrasDeConsultas,
  cifrasDeMetricas,
  detalleDeLatencia,
  duracion,
  listaDeDegradacion,
  montarPanelMetricas,
  operacionLegible,
  tablaDeDisponibilidad,
  tablaDeTiempos,
} from './panel-metricas.js';

const asentar = async () => {
  for (let i = 0; i < 8; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
};

const INFORME_DE_DISPONIBILIDAD = {
  desde: '2026-09-14T00:00:00Z',
  hasta: '2026-10-14T00:00:00Z',
  periodoMinutos: 43200,
  umbral: 99.95,
  porcentajeDelBloque: 99.81,
  cumpleElUmbral: false,
  servicios: [
    {
      servicio: 'torneos',
      disponibleMinutos: 43200,
      indisponibleMinutos: 0,
      porcentaje: 100,
      cumple: true,
      interrupciones: [],
    },
    {
      servicio: 'ms-subastas',
      disponibleMinutos: 43118,
      indisponibleMinutos: 82,
      porcentaje: 99.81,
      cumple: false,
      interrupciones: [
        {
          inicio: '2026-10-02T10:00:00Z',
          fin: '2026-10-02T11:00:00Z',
          detalle: 'connection refused',
        },
        { inicio: '2026-10-13T09:00:00Z', fin: null, detalle: 'connection refused' },
      ],
    },
    {
      servicio: 'salas-partidas',
      disponibleMinutos: 43170,
      indisponibleMinutos: 30,
      porcentaje: 99.93,
      cumple: false,
      interrupciones: [
        { inicio: '2026-10-05T03:00:00Z', fin: '2026-10-05T03:30:00Z', detalle: 'timeout' },
      ],
    },
  ],
};

const ESTADO = {
  servicios: [
    { servicio: 'torneos', estado: 'DISPONIBLE', comprobadoEn: '2026-10-14T10:00:00Z' },
    { servicio: 'ms-subastas', estado: 'INDISPONIBLE', comprobadoEn: '2026-10-14T10:00:00Z' },
  ],
};

const TECNICAS = {
  generadoEn: '2026-10-14T10:00:00Z',
  desdeCache: false,
  servicios: [
    {
      servicio: 'salas-partidas',
      cpu: 0.12,
      memoriaMb: 182,
      peticiones: 5240,
      promedioMs: 38,
      maximoMs: 812,
      errores5xx: 3,
      tasaDeError: 0.0006,
    },
    {
      servicio: 'comentarios',
      cpu: 0.05,
      memoriaMb: 150,
      peticiones: 900,
      promedioMs: 22,
      maximoMs: 140,
      errores5xx: 0,
      tasaDeError: 0,
    },
    { servicio: 'correo', peticiones: 0, errores5xx: 0, brecha: 'no expone metricas' },
  ],
  alertas: [],
  brechas: ['correo: no expone metricas'],
  umbrales: { cpu: 0.75, latenciaMs: 500, disponibilidadPorcentaje: 99.95 },
};

const LATENCIA = {
  servicio: 'metricas-plataforma',
  muestras: 1284,
  percentil: 'p95',
  objetivoMs: 500,
  percentilMs: 312,
  maximoMs: 1904,
  cumple: true,
  sinDatos: false,
  porTipo: [
    { tipo: 'lectura', muestras: 1200, percentilMs: 120, maximoMs: 900 },
    { tipo: 'escritura', muestras: 84, percentilMs: 340, maximoMs: 1904 },
  ],
  operacionesMasLentas: [
    { metodo: 'GET', ruta: '/api/v1/tecnicas', muestras: 30, percentilMs: 640 },
    { metodo: 'POST', ruta: '/api/v1/subastas/{subastaId}/pujas', muestras: 2, percentilMs: 410 },
  ],
};

const CONSULTAS = {
  servicio: 'metricas-plataforma',
  muestras: 8421,
  percentil: 'p95',
  objetivoMs: 500,
  percentilMs: 7,
  maximoMs: 812,
  umbralLentaMs: 500,
  cuantasLentas: 3,
  cumple: true,
  sinDatos: false,
  sentenciasMasLentas: [
    {
      sentencia: 'select * from muestras where servicio = ?',
      muestras: 4,
      percentilMs: 700,
      maximoMs: 812,
    },
  ],
};

const DEGRADACION = {
  operativoPorCompleto: false,
  seccionesLimitadas: [
    { dependencia: 'inventario', seccion: 'Inventario', desde: '2026-10-14T09:58:00Z' },
  ],
};

function servicio(respuestas) {
  return jest.fn(async (url, opciones = {}) => {
    const ruta = new URL(url, 'http://x').pathname.replace(/^\/api\/v1/, '');
    const respuesta = respuestas[ruta];
    if (!respuesta) {
      throw new Error(`sin ruta simulada para ${ruta}`);
    }
    const { estado = 200, cuerpo = null } = respuesta;
    return {
      ok: estado >= 200 && estado < 300,
      status: estado,
      json: async () => cuerpo,
      text: async () => String(cuerpo),
      headers: opciones.headers,
    };
  });
}

const TODAS = {
  '/disponibilidad': { cuerpo: ESTADO },
  '/disponibilidad/informe': { cuerpo: INFORME_DE_DISPONIBILIDAD },
  '/tecnicas': { cuerpo: TECNICAS },
  '/latencia/informe': { cuerpo: LATENCIA },
  '/consultas/informe': { cuerpo: CONSULTAS },
  '/degradacion': { cuerpo: DEGRADACION },
  '/latencia/informe/texto': { cuerpo: 'Informe de latencia — metricas-plataforma' },
};

function montar(respuestas = TODAS, opciones = {}) {
  document.body.innerHTML = `
    <main>
      <div data-zona="encabezado"></div>
      <div data-zona="indicadores"></div>
      <div data-zona="paneles"></div>
    </main>`;
  const fetchImpl = servicio(respuestas);
  const descargar = jest.fn();
  const vista = montarPanelMetricas(document, {
    fetchImpl,
    descargar,
    actualizarSola: false,
    ...opciones,
  });
  return { fetchImpl, descargar, vista };
}

const panelDe = (id) => document.querySelector(`[data-panel="${id}"]`);
const cifra = (etiqueta) => document.querySelector(`[data-indicador="${etiqueta}"]`);

describe('presentación', () => {
  test('los minutos sin servicio se dicen como se leen', () => {
    expect(duracion(8)).toBe('8 min');
    expect(duracion(60)).toBe('1 h');
    expect(duracion(125)).toBe('2 h 5 min');
    expect(duracion(1620)).toBe('1 d 3 h');
    expect(duracion(null)).toBe('0 min');
  });

  test('una operación se dice con su verbo y su nombre, nunca con el método ni la ruta', () => {
    expect(operacionLegible({ metodo: 'GET', ruta: '/api/v1/tecnicas' })).toBe(
      'Consulta · Tablero técnico',
    );
    expect(operacionLegible({ metodo: 'GET', ruta: '/api/v1/admin/sistema/servicios' })).toBe(
      'Consulta · Estado de los servicios',
    );
    // Una ruta que la consola no conoce se dice con sus palabras, sin
    // prefijo de versión ni parámetros.
    expect(operacionLegible({ metodo: 'POST', ruta: '/api/v1/subastas/{id}/pujas' })).toBe(
      'Alta · Subastas › pujas',
    );
    expect(operacionLegible({ metodo: 'OPTIONS', ruta: '/api/v1/salas' })).toBe('Salas');
    for (const ruta of ['/api/v1/tecnicas', '/api/v1/subastas/{id}/pujas', '/api/v1/x-y/{z}']) {
      expect(operacionLegible({ metodo: 'GET', ruta })).not.toMatch(/\/|\{|GET/);
    }
  });

  test('una función limitada dice su causa con el nombre del servicio', () => {
    const tabla = listaDeDegradacion(DEGRADACION);
    const fila = tabla.querySelector('[data-fila]');
    expect(fila.textContent).toContain('Inventario');
    expect(fila.textContent).toContain('Inventario no responde');
    expect(tabla.textContent).not.toContain('inventario no responde');
  });

  test('las cifras de arriba salen de lo que respondió, sin inventar lo que falta', () => {
    const [mes, objetivo, interrupciones, limitadas] = cifrasDeMetricas({
      disponibilidad: INFORME_DE_DISPONIBILIDAD,
      degradacion: DEGRADACION,
    });
    expect(mes).toMatchObject({ valor: '99,81 %', tono: 'error', detalle: 'Objetivo: 99,95 %' });
    expect(objetivo).toMatchObject({
      valor: '1 de 3',
      tono: 'advertencia',
      detalle: 'Por debajo: Subastas, Salas y partidas',
    });
    expect(interrupciones.valor).toBe(3);
    expect(interrupciones.detalle).toMatch(/^La última: Subastas, /);
    expect(limitadas).toMatchObject({ valor: 1, tono: 'error', detalle: 'Inventario' });

    const sinFuentes = cifrasDeMetricas({ disponibilidad: null, degradacion: null });
    expect(sinFuentes.map((c) => c.valor)).toEqual([null, null, null, null]);
    expect(
      cifrasDeMetricas({ disponibilidad: null, degradacion: { seccionesLimitadas: [] } })[3],
    ).toMatchObject({ valor: 0, tono: 'exito', detalle: 'Todo funciona con normalidad' });
  });

  test('la disponibilidad por servicio: el más bajo primero, con su estado de ahora', () => {
    document.body.replaceChildren(tablaDeDisponibilidad(INFORME_DE_DISPONIBILIDAD, ESTADO));
    const filas = [...document.querySelectorAll('tbody tr')];
    expect(filas.map((f) => f.dataset.fila)).toEqual(['ms-subastas', 'salas-partidas', 'torneos']);
    expect(filas[0].textContent).toContain('Subastas');
    expect(filas[0].textContent).toContain('Sin respuesta');
    expect(filas[0].textContent).toContain('99,81 %');
    expect(filas[0].textContent).toContain('1 h 22 min');
    expect(filas[0].textContent).toMatch(/En curso desde /);
    expect(filas[2].textContent).toContain('En línea');
    expect(document.body.textContent).not.toContain('ms-subastas');
  });

  test('los tiempos por servicio: nombres de producto, el máximo sobre el objetivo marcado', () => {
    document.body.replaceChildren(tablaDeTiempos(TECNICAS));
    const salas = document.querySelector('[data-fila="salas-partidas"]');
    expect(salas.textContent).toContain('Salas y partidas');
    expect(salas.textContent).toContain('38 ms');
    expect(salas.querySelector('.estado-admin[data-tono="advertencia"]').textContent).toContain(
      '812 ms',
    );
    expect(salas.textContent).toContain('5.240');
    expect(salas.textContent).toMatch(/0,06 % de las peticiones/);
    expect(document.querySelector('[data-fila="correo"]').textContent).toContain(
      'No se pudo medir en esta ronda',
    );
    expect(document.body.textContent).not.toMatch(/5xx|Maxima|n\/d/);
  });

  test('el detalle del percentil: cifras, por tipo y las operaciones más lentas', () => {
    document.body.replaceChildren(...detalleDeLatencia(LATENCIA));
    expect(cifra('Tiempo de respuesta (p95)').textContent).toContain('312 ms');
    expect(cifra('Tiempo de respuesta (p95)').dataset.tono).toBe('exito');
    expect(document.body.textContent).toContain('Lecturas');
    expect(document.body.textContent).toContain('Escrituras');
    expect(document.body.textContent).toContain('Consulta · Tablero técnico');
    expect(document.body.textContent).not.toContain('/api/');
    expect(detalleDeLatencia({ ...LATENCIA, sinDatos: true })[0].textContent).toContain(
      'Todavía no hay mediciones',
    );
  });

  test('las consultas a la base de datos en cifras, sin enseñar las sentencias', () => {
    document.body.replaceChildren(cifrasDeConsultas(CONSULTAS));
    expect(cifra('Consultas lentas').textContent).toContain('3');
    expect(cifra('Consultas lentas').textContent).toContain('Más de 500 ms');
    expect(document.body.textContent).not.toMatch(/select|from muestras/i);
  });

  test('las funciones limitadas, o que todo funciona', () => {
    document.body.replaceChildren(listaDeDegradacion(DEGRADACION));
    expect(document.body.textContent).toContain('Inventario');
    document.body.replaceChildren(listaDeDegradacion({ seccionesLimitadas: [] }));
    expect(document.body.textContent).toContain('Todo funciona con normalidad');
  });
});

describe('la página', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  test('encabezado, cifras y cada sección con su fuente, sin textos de trabajo', async () => {
    montar();
    await asentar();

    expect(document.querySelector('h1').textContent).toBe('Métricas de plataforma');
    expect(cifra('Disponibilidad del mes').textContent).toContain('99,81 %');
    expect(panelDe('disponibilidad').querySelectorAll('tbody tr')).toHaveLength(3);
    expect(panelDe('tiempos').textContent).toContain('Objetivo de tiempo de respuesta: 500 ms');
    expect(panelDe('latencia').textContent).toContain('312 ms');
    expect(panelDe('consultas').textContent).toContain('Consultas medidas');
    expect(panelDe('degradacion').textContent).toContain('Inventario');
    expect(document.querySelector('[data-zona="actualizacion"]').textContent).toContain(
      'Actualizado a las',
    );
    expect(document.body.textContent).not.toMatch(
      /shared\/libs|recalcula|para el acta|Product Owner|HU-|RNF|observabilidad/,
    );
  });

  test('si una fuente no responde, lo dice su sección y el resto sigue', async () => {
    montar({
      ...TODAS,
      '/consultas/informe': { estado: 503, cuerpo: { title: 'Caído', status: 503 } },
    });
    await asentar();

    expect(panelDe('consultas').textContent).toContain('No pudimos cargar las consultas');
    expect(panelDe('consultas').querySelector('[data-indicador]')).toBeNull();
    expect(panelDe('disponibilidad').querySelectorAll('tbody tr')).toHaveLength(3);
  });

  test('sin rol de administración, cada sección lo dice sin códigos', async () => {
    const prohibido = { estado: 403, cuerpo: { title: 'Prohibido', status: 403 } };
    montar(Object.fromEntries(Object.keys(TODAS).map((ruta) => [ruta, prohibido])));
    await asentar();

    expect(panelDe('disponibilidad').textContent).toContain('Esta sección es de administración');
    expect(document.body.textContent).not.toContain('403');
    expect(cifra('Disponibilidad del mes').dataset.sinDato).toBe('si');
  });

  test('«Descargar informe» guarda el informe redactado que publica el servicio', async () => {
    const { descargar } = montar();
    await asentar();
    document.querySelector('[data-accion="descargar-informe"]').click();
    await asentar();
    expect(descargar).toHaveBeenCalledWith(
      'informe-de-tiempos.txt',
      'Informe de latencia — metricas-plataforma',
      'text/plain',
    );
  });
});
