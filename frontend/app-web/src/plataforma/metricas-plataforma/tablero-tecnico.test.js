/** Tablero tecnico y de moderacion — HU-MET-004 / HU-MET-001. */

import { jest } from '@jest/globals';

import {
  filaDe,
  montarTableroTecnico,
  ms,
  porcentaje,
  resumenDeCuentas,
  resumenDeModeracion,
} from './tablero-tecnico.js';

const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

const tecnico = () => ({
  generadoEn: '2026-10-01T10:00:00Z',
  servicios: [
    {
      servicio: 'salas-partidas',
      cpu: 0.91,
      memoriaMb: 180.4,
      peticiones: 1200,
      promedioMs: 40.2,
      maximoMs: 820,
      errores5xx: 3,
      tasaDeError: 0.0025,
      brecha: null,
    },
    {
      servicio: 'correo',
      cpu: null,
      memoriaMb: null,
      peticiones: 0,
      promedioMs: null,
      maximoMs: null,
      errores5xx: 0,
      tasaDeError: null,
      brecha: 'Connection refused',
    },
  ],
  alertas: [
    {
      servicio: 'salas-partidas',
      metrica: 'cpu',
      detalle: 'uso de procesador 91 % por encima del 75 %',
    },
    {
      servicio: 'salas-partidas',
      metrica: 'latencia',
      detalle: 'latencia maxima 820 ms por encima de 500 ms',
    },
  ],
  brechas: ['correo: Connection refused'],
  umbrales: { cpu: 0.75, latenciaMs: 500, disponibilidadPorcentaje: 99.95 },
});

const moderacion = (extra = {}) => ({
  sanciones: {
    desde: '2026-09-01T10:00:00Z',
    hasta: '2026-10-01T10:00:00Z',
    total: 6,
    porTipo: { ADVERTENCIA: 4, SUSPENSION: 2, BANEO: 0 },
    porDia: [
      { fecha: '2026-09-28', emitidas: 5 },
      { fecha: '2026-09-20', emitidas: 1 },
    ],
    apelaciones: { PENDIENTE: 1, MANTENIDA: 0, REDUCIDA: 0, REVERTIDA: 1 },
    moderadoresActivos: 2,
    revertidas: 1,
  },
  alertasConfiguradas: false,
  umbralSancionesPorDia: null,
  alertas: [],
  pendientes: ['registro de nuevos usuarios: sin contrato', 'frecuencia de reportes: HU-COM-006'],
  registroDeUsuarios: null,
  ...extra,
});

/** Lo que ms-identidad publica en /admin/jugadores/indicadores, reenviado por /moderacion (1.10.0). */
const cuentas = () => ({
  total: 12,
  porEstado: { ACTIVO: 8, PENDIENTE_VERIFICACION: 1, INACTIVO: 0, SUSPENDIDO: 2, BANEADO: 1 },
  registros: {
    desde: '2026-09-30',
    hasta: '2026-10-01',
    total: 3,
    porDia: [
      { fecha: '2026-09-30', cuentas: 1 },
      { fecha: '2026-10-01', cuentas: 2 },
    ],
  },
  ocultarPruebas: false,
  calculadoEn: '2026-10-01T10:00:00Z',
});

function servicio(rutas) {
  return jest.fn(async (url, opciones = {}) => {
    const clave = new URL(url, 'http://x').pathname;
    const respuesta = rutas[clave];
    if (!respuesta) {
      throw new Error(`sin ruta simulada para ${clave}`);
    }
    const { estado = 200, cuerpo = null } =
      typeof respuesta === 'function' ? respuesta(url, opciones) : respuesta;
    return {
      ok: estado >= 200 && estado < 300,
      status: estado,
      json: async () => cuerpo,
      text: async () => String(cuerpo),
    };
  });
}

describe('presentacion', () => {
  test('porcentaje y ms formatean o dicen n/d', () => {
    expect(porcentaje(0.914)).toBe('91 %');
    expect(porcentaje(null)).toBe('n/d');
    expect(ms(40.2)).toBe('40 ms');
    expect(ms(undefined)).toBe('n/d');
  });

  test('filaDe marca en alerta lo que supera los umbrales del servicio', () => {
    const t = tecnico();
    const fila = filaDe(t.servicios[0], t.umbrales);
    expect(fila).toMatchObject({
      cpu: '91 %',
      cpuEnAlerta: true,
      maximo: '820 ms',
      maximoEnAlerta: true,
      tasaDeError: '0.25 %',
      memoria: '180 MB',
    });
    const brecha = filaDe(t.servicios[1], t.umbrales);
    expect(brecha.brecha).toBe('Connection refused');
    expect(brecha.cpuEnAlerta).toBe(false);
  });

  test('resumenDeCuentas en una linea: estados ahora y altas del periodo', () => {
    expect(resumenDeCuentas(cuentas())).toBe(
      '12 cuentas (8 activas, 2 suspendidas, 1 baneadas, 1 sin verificar, 0 inactivas) · 3 altas en el periodo',
    );
  });

  test('resumenDeModeracion en una linea', () => {
    expect(resumenDeModeracion(moderacion())).toBe(
      '6 sanciones (4 advertencias, 2 suspensiones, 0 baneos) · 2 moderadores activos · 1 revertidas',
    );
  });
});

const VISTA = `
  <button data-accion="recargar-tecnicas"></button>
  <button data-accion="exportar-tecnicas"></button>
  <div data-zona="tecnicas"></div>
  <form data-zona="periodo"><input name="desde" /><input name="hasta" /><button type="submit"></button><button type="button" data-accion="exportar-moderacion"></button></form>
  <div data-zona="moderacion"></div>`;

describe('la observabilidad es de administracion (#527)', () => {
  beforeEach(() => {
    document.body.innerHTML = VISTA;
  });

  test('sin rol administrativo la vista lo explica y no habla de codigos ni de reintentar', async () => {
    const fetchImpl = servicio({
      '/api/v1/tecnicas': { estado: 403, cuerpo: { status: 403, title: 'Forbidden' } },
      '/api/v1/moderacion': { estado: 403, cuerpo: { status: 403, title: 'Forbidden' } },
    });

    montarTableroTecnico(document, { fetchImpl });
    await asentar();
    await asentar();

    const aviso = document.querySelector(
      '[data-zona="tecnicas"] .aviso[data-motivo="sin-permiso"]',
    );
    expect(aviso).not.toBeNull();
    expect(aviso.className).toContain('aviso--info');
    expect(aviso.textContent).toMatch(/administración/i);
    expect(aviso.textContent).not.toMatch(/403|error/i);
  });
});

describe('vista', () => {
  beforeEach(() => {
    document.body.innerHTML = VISTA;
  });

  test('pinta la tabla técnica con alertas y brechas, y la moderación sin umbral lo dice', async () => {
    const fetchImpl = servicio({
      '/api/v1/tecnicas': { cuerpo: tecnico() },
      '/api/v1/moderacion': { cuerpo: moderacion() },
    });
    montarTableroTecnico(document, { fetchImpl });
    await asentar();
    await asentar();
    const tabla = document.querySelector('[data-zona="tabla-tecnica"]');
    expect(tabla.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(
      tabla.querySelector('[data-servicio="salas-partidas"] [data-alerta="true"]'),
    ).not.toBeNull();
    expect(tabla.querySelector('[data-servicio="correo"][data-brecha="true"]').textContent).toMatch(
      /Brecha de observabilidad/,
    );
    expect(document.querySelector('[data-zona="brechas"]').textContent).toMatch(
      /correo: Connection refused/,
    );
    expect(
      document.querySelectorAll('[data-zona="tecnicas"] [data-zona="alertas"] li.aviso'),
    ).toHaveLength(2);

    expect(document.querySelector('[data-zona="resumen-moderacion"]').textContent).toMatch(
      /6 sanciones/,
    );
    expect(document.querySelectorAll('[data-zona="por-dia"] li')).toHaveLength(2);
    expect(
      document.querySelector('[data-zona="moderacion"] [data-zona="alertas"]').textContent,
    ).toMatch(/[Ss]in umbral configurado/);
    expect(document.querySelectorAll('[data-zona="pendientes"] li')).toHaveLength(2);
  });

  test('con los indicadores de identidad pinta cuentas por estado y altas por dia, y deja de listar "nuevos usuarios" como pendiente', async () => {
    const fetchImpl = servicio({
      '/api/v1/tecnicas': { cuerpo: tecnico() },
      '/api/v1/moderacion': {
        cuerpo: moderacion({
          registroDeUsuarios: cuentas(),
          pendientes: ['frecuencia de reportes: HU-COM-006'],
        }),
      },
    });
    montarTableroTecnico(document, { fetchImpl });
    await asentar();
    await asentar();

    expect(document.querySelector('[data-zona="resumen-usuarios"]').textContent).toMatch(
      /12 cuentas \(8 activas, 2 suspendidas, 1 baneadas/,
    );
    const dias = document.querySelectorAll('[data-zona="altas-por-dia"] li');
    expect(dias).toHaveLength(2);
    expect(dias[1].textContent).toBe('2026-10-01: 2');
    const pendientes = document.querySelectorAll('[data-zona="pendientes"] li');
    expect(pendientes).toHaveLength(1);
    expect(pendientes[0].textContent).toMatch(/frecuencia de reportes/);
  });

  test('si identidad no dio las cuentas no se pinta ese bloque y el motivo real queda en pendientes (CA-03)', async () => {
    const fetchImpl = servicio({
      '/api/v1/tecnicas': { cuerpo: tecnico() },
      '/api/v1/moderacion': {
        cuerpo: moderacion({
          registroDeUsuarios: null,
          pendientes: [
            'registro de nuevos usuarios: ms-identidad nego el permiso (se necesita un administrador con GESTIONAR_CUENTAS)',
            'frecuencia de reportes: HU-COM-006',
          ],
        }),
      },
    });
    montarTableroTecnico(document, { fetchImpl });
    await asentar();
    await asentar();

    expect(document.querySelector('[data-zona="resumen-moderacion"]')).not.toBeNull();
    expect(document.querySelector('[data-zona="resumen-usuarios"]')).toBeNull();
    expect(document.querySelector('[data-zona="altas-por-dia"]')).toBeNull();
    expect(document.querySelector('[data-zona="pendientes"]').textContent).toMatch(
      /GESTIONAR_CUENTAS/,
    );
  });

  test('con umbral configurado se listan las alertas; el periodo del formulario viaja en la consulta; exportar descarga', async () => {
    const descargas = [];
    const fetchImpl = servicio({
      '/api/v1/tecnicas': { cuerpo: tecnico() },
      '/api/v1/tecnicas/informe/texto': { cuerpo: 'Métricas técnicas de la plataforma' },
      '/api/v1/moderacion': (url) => ({
        cuerpo: moderacion({
          alertasConfiguradas: true,
          umbralSancionesPorDia: 3,
          alertas: url.includes('desde=')
            ? ['alta frecuencia de sanciones el 2026-09-28: 5 (umbral: 3 o mas por dia)']
            : [],
        }),
      }),
    });
    montarTableroTecnico(document, {
      fetchImpl,
      descargar: (n, c, t) => descargas.push([n, c, t]),
    });
    await asentar();
    await asentar();
    expect(
      document.querySelector('[data-zona="moderacion"] [data-zona="alertas"]').textContent,
    ).toMatch(/ningún día llega al umbral/);

    const form = document.querySelector('[data-zona="periodo"]');
    form.querySelector('[name="desde"]').value = '2026-09-01T00:00';
    form.querySelector('[name="hasta"]').value = '2026-10-01T00:00';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    await asentar();
    const consulta = fetchImpl.mock.calls
      .map((c) => String(c[0]))
      .find((u) => u.includes('desde='));
    expect(consulta).toMatch(/desde=2026-09-01/);
    expect(
      document.querySelectorAll('[data-zona="moderacion"] [data-zona="alertas"] li.aviso'),
    ).toHaveLength(1);

    document.querySelector('[data-accion="exportar-tecnicas"]').click();
    await asentar();
    await asentar();
    document.querySelector('[data-accion="exportar-moderacion"]').click();
    expect(descargas.map((d) => d[0])).toEqual([
      'metricas-tecnicas.txt',
      'metricas-moderacion.json',
    ]);
    expect(descargas[0][1]).toMatch(/Métricas técnicas/);
  });

  test('un 503 de la fuente se pinta como error con su título', async () => {
    montarTableroTecnico(document, {
      fetchImpl: servicio({
        '/api/v1/tecnicas': { cuerpo: tecnico() },
        '/api/v1/moderacion': {
          estado: 503,
          cuerpo: {
            type: 'https://nexusbattles.local/errores/fuente-no-disponible',
            title: 'La fuente de moderación no responde',
            detail: 'x',
          },
        },
      }),
    });
    await asentar();
    await asentar();
    expect(
      document.querySelector('[data-zona="moderacion"] .aviso--error .aviso__titulo').textContent,
    ).toBe('La fuente de moderación no responde');
  });
});
