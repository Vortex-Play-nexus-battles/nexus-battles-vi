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

  test('SA-3: el historial de sanciones va en la consulta y en la exportación', () => {
    expect(recursoDelDirectorio({ pagina: 0, historialSanciones: 'CON' }, 16)).toBe(
      '/admin/jugadores?page=0&size=16&historialSanciones=CON',
    );
    expect(recursoDeExportacion({ historialSanciones: 'SIN', ocultarPruebas: true })).toBe(
      '/admin/jugadores/exportacion?historialSanciones=SIN&ocultarPruebas=true',
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
  test('una columna por día, proporcional al máximo, con su valor al pasar el cursor', () => {
    const grafico = graficoDeRegistros(INDICADORES.registros);

    const columnas = [...grafico.querySelectorAll('.grafico-admin__columna')];
    expect(columnas).toHaveLength(3);
    expect(columnas.map((c) => c.dataset.valor)).toEqual(['4', '0', '1']);
    const altos = columnas.map((c) =>
      parseFloat(c.querySelector('.grafico-admin__barra').style.getPropertyValue('--grafico-alto')),
    );
    expect(altos[0]).toBeGreaterThan(altos[2]);
    expect(altos[1]).toBe(0);
    expect(altos[2] * 4).toBeCloseTo(altos[0]);
    expect(columnas[0].querySelector('.grafico-admin__globo').textContent).toMatch(
      /: 4 cuentas nuevas$/,
    );
  });

  test('lleva eje con marcas redondas, título con el periodo y resumen legible', () => {
    const grafico = graficoDeRegistros(INDICADORES.registros);

    expect(grafico.querySelector('figcaption').textContent).toMatch(/^Registros por día · del /);
    expect(
      [...grafico.querySelectorAll('.grafico-admin__eje-y span')].map((s) => s.textContent),
    ).toEqual(['4', '3', '2', '1', '0']);
    expect(grafico.querySelector('.grafico-admin__resumen').textContent).toMatch(
      /^5 cuentas nuevas en total · máximo 4/,
    );
  });

  test('las barras repiten la tabla: el lector de pantalla lee la tabla, un día por fila', () => {
    const grafico = graficoDeRegistros(INDICADORES.registros);

    expect(grafico.querySelector('.grafico-admin__area').getAttribute('aria-hidden')).toBe('true');
    const tabla = grafico.querySelector('details table');
    expect(tabla.querySelectorAll('tbody tr')).toHaveLength(3);
    expect([...tabla.querySelectorAll('thead th')].map((th) => th.textContent)).toEqual([
      'Día',
      'Cuentas nuevas',
    ]);
  });

  test('sin altas en el periodo no divide por cero ni pinta barras altas', () => {
    const grafico = graficoDeRegistros({
      desde: '2026-10-01',
      hasta: '2026-10-02',
      total: 0,
      porDia: [
        { fecha: '2026-10-01', cuentas: 0 },
        { fecha: '2026-10-02', cuentas: 0 },
      ],
    });

    for (const barra of grafico.querySelectorAll('.grafico-admin__barra')) {
      expect(parseFloat(barra.style.getPropertyValue('--grafico-alto'))).toBe(0);
    }
    expect(grafico.querySelector('.grafico-admin__resumen').textContent).toMatch(
      /^0 cuentas nuevas/,
    );
  });
});

describe('indicadores pintados', () => {
  const valor = (caja, etiqueta) =>
    caja.querySelector(`[data-indicador="${etiqueta}"] .indicador-admin__valor`)?.textContent;

  test('el total y los cinco estados, con sus nombres en castellano', () => {
    const caja = contenedor(pintarIndicadores(INDICADORES));

    expect(valor(caja, 'Cuentas en total')).toBe('16');
    expect(ESTADOS_DE_CUENTA.map(({ etiqueta }) => valor(caja, etiqueta))).toEqual([
      '10',
      '2',
      '0',
      '1',
      '3',
    ]);
    expect(caja.textContent).not.toMatch(/pruebas automáticas|Contado a las/);
  });

  test('suspendidas y baneadas llevan su tono con franja e icono, no solo color', () => {
    const caja = contenedor(pintarIndicadores(INDICADORES));

    expect(caja.querySelector('[data-indicador="Suspendidas"]').dataset.tono).toBe('advertencia');
    expect(
      caja.querySelector('[data-indicador="Baneadas"] .indicador-admin__icono'),
    ).not.toBeNull();
  });

  test('un estado que no llegó es «sin dato», no un cero inventado', () => {
    const caja = contenedor(pintarIndicadores({ ...INDICADORES, porEstado: { ACTIVO: 10 } }));

    const baneadas = caja.querySelector('[data-indicador="Baneadas"]');
    expect(baneadas.dataset.sinDato).toBe('si');
    expect(baneadas.querySelector('.indicador-admin__valor').textContent).toBe('—Sin dato');
  });

  test('un estado que el panel no conoce se enseña con su nombre legible, no se esconde', () => {
    const caja = contenedor(
      pintarIndicadores({
        ...INDICADORES,
        porEstado: { ...INDICADORES.porEstado, EN_REVISION: 2 },
      }),
    );

    const desconocido = caja.querySelector('[data-indicador="En revision"]');
    expect(desconocido.querySelector('.indicador-admin__cifra').textContent).toBe('2');
    // Sin la constante en mayúsculas: el panel habla como el producto.
    expect(caja.textContent).not.toContain('EN_REVISION');
    // La rejilla cuenta la tarjeta de más.
    expect(caja.querySelector('[data-zona="estados"]').dataset.cantidad).toBe('7');
  });

  test('el gráfico de registros va debajo de las cifras', () => {
    const caja = contenedor(pintarIndicadores(INDICADORES));

    expect(caja.querySelector('.rejilla-indicadores-admin + .grafico-admin')).not.toBeNull();
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
      'Sin verificar el correo,2',
      'Pendientes de activar,0',
      'Suspendidas,1',
      'Baneadas,3',
      'Cuentas de prueba excluidas,Sí',
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

  test('pide los indicadores sin las cuentas de prueba, con el filtro a la vista, y los pinta', async () => {
    const consultarApi = jest.fn(async () => conDatos(INDICADORES));
    const { p } = montar(consultarApi);

    await p.cargar();

    expect(consultarApi).toHaveBeenCalledWith('/admin/jugadores/indicadores?ocultarPruebas=true');
    expect(p.elemento.querySelector('.panel-admin__estado').textContent).toBe('');
    expect(
      p.elemento.querySelector('[data-indicador="Activas"] .indicador-admin__valor').textContent,
    ).toBe('10');
    expect(p.elemento.querySelector('[data-quitar="cuentas"]').textContent).toContain(
      'Cuentas de prueba: Excluidas',
    );
    expect(p.elemento.textContent).not.toMatch(/Fuente:|\/api\/v1|pruebas automáticas/);
  });

  test('quitar el filtro de cuentas de prueba vuelve a pedir con todas', async () => {
    const consultarApi = jest.fn(async () => conDatos(INDICADORES));
    const { p } = montar(consultarApi);
    await p.cargar();

    p.elemento.querySelector('[data-quitar="cuentas"]').dispatchEvent(new window.Event('click'));
    await asentar();

    expect(consultarApi.mock.calls.at(-1)[0]).toBe(
      '/admin/jugadores/indicadores?desde=2026-10-01&hasta=2026-10-03',
    );
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

    const desde = p.elemento.querySelector('input[name="desde"]');
    desde.value = '2026-09-01';
    p.elemento.querySelector('input[name="hasta"]').value = '2026-09-30';
    desde.dispatchEvent(new window.Event('change'));
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
    desde.dispatchEvent(new window.Event('change'));
    await asentar();

    expect(consultarApi.mock.calls.length).toBe(llamadas);
    expect(p.elemento.querySelector('[data-zona="error-filtros"]').textContent).toMatch(
      /posterior/,
    );
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
      motivo: 'No pudimos cargar esta información. Inténtalo de nuevo en unos segundos.',
      recurso: '/admin/jugadores/indicadores',
      detalle: null,
    };
    await p.cargar();

    expect(p.elemento.querySelector('.panel-admin__estado').textContent).toBe('Sin conexión');
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
