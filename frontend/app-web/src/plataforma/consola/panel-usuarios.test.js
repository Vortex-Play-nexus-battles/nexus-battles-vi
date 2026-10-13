/**
 * Panel de usuarios de la consola — HU-USR-008 (#561), 7.3.4.
 *
 * Indicadores por estado, gráfico de registros por día (con su tabla), rango
 * de fechas, exportación de los indicadores y del directorio, y las consultas
 * que arman los filtros. Lo que se prueba es lo de siempre en esta consola:
 * que nada se inventa (un estado que no llegó es un guion, no un cero), que un
 * fallo no deja nada a medias y que lo que se exporta es lo que se ve.
 */

import { jest } from '@jest/globals';

import { RESULTADO } from './cliente-consola.js';
import {
  ESTADOS_DE_CUENTA,
  exportarDirectorio,
  graficoDeRegistros,
  indicadoresComoCsv,
  panelDeUsuarios,
  pintarIndicadores,
  recursoDeExportacion,
  recursoDeIndicadores,
  recursoDelDirectorio,
} from './panel-usuarios.js';

const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

const conDatos = (datos) => ({
  resultado: RESULTADO.DATOS,
  datos,
  estado: 200,
  motivo: '',
  recurso: '',
  detalle: null,
});

/** `IndicadoresDeCuentas` de ms-identidad-admin.yaml 1.3.0, tal cual. */
const INDICADORES = {
  total: 16,
  porEstado: { ACTIVO: 10, PENDIENTE_VERIFICACION: 2, INACTIVO: 0, SUSPENDIDO: 1, BANEADO: 3 },
  registros: {
    desde: '2026-10-01',
    hasta: '2026-10-03',
    total: 5,
    porDia: [
      { fecha: '2026-10-01', cuentas: 4 },
      { fecha: '2026-10-02', cuentas: 0 },
      { fecha: '2026-10-03', cuentas: 1 },
    ],
  },
  ocultarPruebas: true,
  calculadoEn: '2026-10-05T15:00:00-05:00',
};

function contenedor(partes) {
  const caja = document.createElement('div');
  caja.append(...(Array.isArray(partes) ? partes : [partes]));
  return caja;
}

describe('consultas que arman los filtros', () => {
  test('sin filtros, el directorio pide lo mismo que antes', () => {
    expect(recursoDelDirectorio({ pagina: 0 }, 20)).toBe('/admin/jugadores?page=0&size=20');
    expect(recursoDelDirectorio({ pagina: 1, buscar: 'Ana', ocultarPruebas: true }, 20)).toBe(
      '/admin/jugadores?page=1&size=20&buscar=Ana&ocultarPruebas=true',
    );
  });

  test('rol, estado y fechas de registro van en la consulta, codificados', () => {
    expect(
      recursoDelDirectorio(
        {
          pagina: 0,
          buscar: 'Ana Pérez',
          rol: 'MODERADOR',
          estadoCuenta: 'SUSPENDIDO',
          desde: '2026-09-01',
          hasta: '2026-09-30',
          ocultarPruebas: true,
        },
        20,
      ),
    ).toBe(
      '/admin/jugadores?page=0&size=20&buscar=Ana%20P%C3%A9rez&rol=MODERADOR&estado=SUSPENDIDO' +
        '&registradoDesde=2026-09-01&registradoHasta=2026-09-30&ocultarPruebas=true',
    );
  });

  test('la exportación lleva los mismos filtros, sin página', () => {
    expect(recursoDeExportacion({ pagina: 3 })).toBe('/admin/jugadores/exportacion');
    expect(
      recursoDeExportacion({
        pagina: 3,
        rol: 'JUGADOR',
        estadoCuenta: 'BANEADO',
        ocultarPruebas: true,
      }),
    ).toBe('/admin/jugadores/exportacion?rol=JUGADOR&estado=BANEADO&ocultarPruebas=true');
  });

  test('los indicadores piden el rango y el criterio de pruebas', () => {
    expect(recursoDeIndicadores({})).toBe('/admin/jugadores/indicadores');
    expect(
      recursoDeIndicadores({ desde: '2026-09-01', hasta: '2026-09-30', ocultarPruebas: true }),
    ).toBe('/admin/jugadores/indicadores?desde=2026-09-01&hasta=2026-09-30&ocultarPruebas=true');
  });
});

describe('gráfico de registros por día', () => {
  test('una barra por día, proporcional al máximo, con su valor al pasar el cursor', () => {
    const svg = graficoDeRegistros(INDICADORES.registros);

    const barras = [...svg.querySelectorAll('rect.grafico-registros__barra')];
    expect(barras).toHaveLength(3);
    expect(barras.map((b) => b.dataset.cuentas)).toEqual(['4', '0', '1']);
    const altos = barras.map((b) => Number(b.getAttribute('height')));
    expect(altos[0]).toBeGreaterThan(altos[2]);
    expect(altos[1]).toBe(0);
    expect(altos[2] * 4).toBeCloseTo(altos[0]);
    expect(barras[0].querySelector('title').textContent).toMatch(/4 cuentas nuevas$/);
    expect(barras[2].querySelector('title').textContent).toMatch(/1 cuenta nueva$/);
  });

  test('es una imagen accesible: dice el periodo, el total y el día con más altas', () => {
    const svg = graficoDeRegistros(INDICADORES.registros);

    expect(svg.getAttribute('role')).toBe('img');
    const etiqueta = svg.getAttribute('aria-label');
    expect(etiqueta).toContain('5 cuentas nuevas');
    expect(etiqueta).toContain('3 días');
    expect(etiqueta).toMatch(/más altas.*con 4/);
  });

  test('sin altas en el periodo lo dice en la etiqueta, sin dividir por cero', () => {
    const svg = graficoDeRegistros({
      desde: '2026-10-01',
      hasta: '2026-10-02',
      total: 0,
      porDia: [
        { fecha: '2026-10-01', cuentas: 0 },
        { fecha: '2026-10-02', cuentas: 0 },
      ],
    });

    expect(svg.getAttribute('aria-label')).toContain('ninguna cuenta nueva');
    for (const barra of svg.querySelectorAll('rect.grafico-registros__barra')) {
      expect(Number(barra.getAttribute('height'))).toBe(0);
    }
  });
});

describe('indicadores pintados', () => {
  test('el total y los cinco estados, con sus nombres en castellano', () => {
    const caja = contenedor(pintarIndicadores(INDICADORES));

    const valor = (etiqueta) =>
      caja.querySelector(`[data-indicador="${etiqueta}"] .indicador__valor`)?.textContent;
    expect(valor('cuentas en total')).toBe('16');
    expect(ESTADOS_DE_CUENTA.map(({ etiqueta }) => valor(etiqueta))).toEqual([
      '10',
      '2',
      '0',
      '1',
      '3',
    ]);
    expect(caja.textContent).toContain('suspendidas');
    expect(caja.textContent).toContain('baneadas');
    expect(caja.textContent).toContain('Sin contar las cuentas de pruebas automáticas');
  });

  test('un estado que no llegó es un guion, no un cero inventado', () => {
    const caja = contenedor(pintarIndicadores({ ...INDICADORES, porEstado: { ACTIVO: 10 } }));

    expect(caja.querySelector('[data-indicador="baneadas"] .indicador__valor').textContent).toBe(
      '--',
    );
  });

  test('un estado que el panel no conoce se enseña con su nombre, no se esconde', () => {
    const caja = contenedor(
      pintarIndicadores({ ...INDICADORES, porEstado: { ...INDICADORES.porEstado, CONGELADO: 2 } }),
    );

    expect(caja.textContent).toContain('CONGELADO');
  });

  test('el gráfico va con su tabla alternativa, un día por fila', () => {
    const caja = contenedor(pintarIndicadores(INDICADORES));

    expect(caja.querySelector('svg.grafico-registros')).not.toBeNull();
    const eje = [...caja.querySelectorAll('[data-zona="eje"] span')].map((s) => s.textContent);
    expect(eje).toHaveLength(2);
    expect(eje[0]).toMatch(/2026/);
    const tabla = caja.querySelector('details table');
    expect(tabla).not.toBeNull();
    expect(tabla.querySelectorAll('tbody tr')).toHaveLength(3);
    expect(tabla.querySelector('caption').textContent).toContain('5 cuentas nuevas');
  });

  test('sin altas en el periodo, lo dice con palabras', () => {
    const caja = contenedor(
      pintarIndicadores({
        ...INDICADORES,
        registros: {
          ...INDICADORES.registros,
          total: 0,
          porDia: [{ fecha: '2026-10-01', cuentas: 0 }],
        },
      }),
    );

    expect(caja.querySelector('[data-zona="sin-registros"]').textContent).toMatch(
      /Nadie se registró/,
    );
  });
});

describe('exportar indicadores (CSV en el navegador)', () => {
  test('el total, cada estado y la serie, con marca de orden de bytes y fin de línea CRLF', () => {
    const csv = indicadoresComoCsv(INDICADORES);

    expect(csv.startsWith('\uFEFF')).toBe(true);
    const lineas = csv.slice(1).split('\r\n');
    expect(lineas).toEqual([
      'Indicador,Valor',
      'Cuentas en total,16',
      'Activas,10',
      'Pendientes de verificar el correo,2',
      'Inactivas (administrativas sin activar),0',
      'Suspendidas,1',
      'Baneadas,3',
      'Sin cuentas de pruebas automáticas,Sí',
      'Contado,2026-10-05T15:00:00-05:00',
      '',
      'Día,Cuentas nuevas',
      '2026-10-01,4',
      '2026-10-02,0',
      '2026-10-03,1',
      '',
    ]);
  });
});

describe('panel de usuarios', () => {
  function montar(consultarApi, guardar = jest.fn()) {
    const p = panelDeUsuarios(consultarApi, { guardar });
    document.body.replaceChildren(p.elemento);
    return { p, guardar };
  }

  test('pide los indicadores ocultando las cuentas de pruebas y los pinta', async () => {
    const consultarApi = jest.fn(async () => conDatos(INDICADORES));
    const { p } = montar(consultarApi);

    await p.cargar();

    expect(consultarApi).toHaveBeenCalledWith('/admin/jugadores/indicadores?ocultarPruebas=true');
    expect(p.elemento.querySelector('.sello-estado').textContent).toBe('EN LINEA');
    expect(
      p.elemento.querySelector('[data-indicador="activas"] .indicador__valor').textContent,
    ).toBe('10');
    expect(p.elemento.textContent).toContain('Fuente: GET /api/v1/admin/jugadores/indicadores');
  });

  test('el rango que aplicó el servidor queda escrito en el selector', async () => {
    const { p } = montar(jest.fn(async () => conDatos(INDICADORES)));

    await p.cargar();

    expect(p.elemento.querySelector('input[name="desde"]').value).toBe('2026-10-01');
    expect(p.elemento.querySelector('input[name="hasta"]').value).toBe('2026-10-03');
  });

  test('aplicar un rango vuelve a pedir con desde y hasta', async () => {
    const consultarApi = jest.fn(async () => conDatos(INDICADORES));
    const { p } = montar(consultarApi);
    await p.cargar();

    p.elemento.querySelector('input[name="desde"]').value = '2026-09-01';
    p.elemento.querySelector('input[name="hasta"]').value = '2026-09-30';
    p.elemento
      .querySelector('form')
      .dispatchEvent(new window.Event('submit', { cancelable: true }));
    await asentar();

    expect(consultarApi.mock.calls.at(-1)[0]).toBe(
      '/admin/jugadores/indicadores?desde=2026-09-01&hasta=2026-09-30&ocultarPruebas=true',
    );
  });

  test('un rango al revés no se pide: se explica junto al selector', async () => {
    const consultarApi = jest.fn(async () => conDatos(INDICADORES));
    const { p } = montar(consultarApi);
    await p.cargar();
    const llamadas = consultarApi.mock.calls.length;

    const desde = p.elemento.querySelector('input[name="desde"]');
    desde.value = '2026-10-05';
    p.elemento.querySelector('input[name="hasta"]').value = '2026-10-01';
    p.elemento
      .querySelector('form')
      .dispatchEvent(new window.Event('submit', { cancelable: true }));
    await asentar();

    expect(consultarApi.mock.calls.length).toBe(llamadas);
    expect(desde.getAttribute('aria-invalid')).toBe('true');
    expect(p.elemento.querySelector('[data-zona="aviso-rango"]').textContent).toMatch(/posterior/);
  });

  test('exportar está apagado hasta que hay datos y descarga lo que se ve', async () => {
    const { p, guardar } = montar(jest.fn(async () => conDatos(INDICADORES)));
    const boton = p.elemento.querySelector('[data-accion="exportar-indicadores"]');
    expect(boton.disabled).toBe(true);

    await p.cargar();
    expect(boton.disabled).toBe(false);
    boton.dispatchEvent(new window.Event('click'));

    expect(guardar).toHaveBeenCalledTimes(1);
    const [nombre, contenido, tipo] = guardar.mock.calls[0];
    expect(nombre).toBe('indicadores-de-usuarios-2026-10-01-a-2026-10-03.csv');
    expect(contenido).toBe(indicadoresComoCsv(INDICADORES));
    expect(tipo).toMatch(/^text\/csv/);
  });

  test('CA-03: si el servicio no responde, el panel lo dice y no deja exportar nada viejo', async () => {
    let respuesta = conDatos(INDICADORES);
    const consultarApi = jest.fn(async () => respuesta);
    const { p, guardar } = montar(consultarApi);
    await p.cargar();

    respuesta = {
      resultado: RESULTADO.SERVICIO_DEGRADADO,
      datos: null,
      estado: 503,
      motivo: 'El servicio que responde esta consulta no está atendiendo.',
      recurso: '/admin/jugadores/indicadores',
      detalle: null,
    };
    await p.cargar();

    expect(p.elemento.querySelector('.sello-estado').textContent).toBe('SERVICIO DEGRADADO');
    const boton = p.elemento.querySelector('[data-accion="exportar-indicadores"]');
    expect(boton.disabled).toBe(true);
    boton.dispatchEvent(new window.Event('click'));
    expect(guardar).not.toHaveBeenCalled();
  });
});

describe('exportar el directorio', () => {
  test('descarga el CSV del servidor con los filtros vigentes y lo guarda con su nombre', async () => {
    const contenido = new Blob(['csv']);
    const descargarApi = jest.fn(async () => ({
      resultado: RESULTADO.DATOS,
      contenido,
      nombreArchivo: 'directorio-de-cuentas-20261005-1500.csv',
      estado: 200,
      motivo: '',
      recurso: '',
    }));
    const guardar = jest.fn();

    const desenlace = await exportarDirectorio(
      { pagina: 2, rol: 'JUGADOR', ocultarPruebas: true },
      { descargarApi, guardar },
    );

    expect(descargarApi).toHaveBeenCalledWith(
      '/admin/jugadores/exportacion?rol=JUGADOR&ocultarPruebas=true',
    );
    expect(guardar).toHaveBeenCalledWith(
      'directorio-de-cuentas-20261005-1500.csv',
      contenido,
      'text/csv;charset=utf-8',
    );
    expect(desenlace.resultado).toBe(RESULTADO.DATOS);
  });

  test('por encima del tope no se guarda nada y se devuelve el motivo del servidor', async () => {
    const descargarApi = jest.fn(async () => ({
      resultado: RESULTADO.NO_DISPONIBLE,
      contenido: null,
      nombreArchivo: null,
      estado: 422,
      motivo: 'Los filtros dejan 12000 cuentas y una exportación admite como máximo 10000.',
      recurso: '/admin/jugadores/exportacion',
    }));
    const guardar = jest.fn();

    const desenlace = await exportarDirectorio({ pagina: 0 }, { descargarApi, guardar });

    expect(guardar).not.toHaveBeenCalled();
    expect(desenlace.motivo).toContain('12000');
  });
});
