/**
 * Registro de auditoría — §7.3.5 (SA-5) con el kit de la consola: lo que se
 * pide a la bitácora, cómo se resuelven los filtros por apodo y la vista
 * montada sobre el marcado REAL de `auditoria.html` contra un servicio
 * simulado. Lo más reciente primero (RFINAL-06) sigue valiendo: el orden lo
 * pide la vista, fecha descendente y el `id` como desempate.
 */

import { jest } from '@jest/globals';
import { readFileSync } from 'node:fs';

import { RESULTADO } from '../plataforma/consola/cliente-consola.js';
import {
  CUENTAS_POR_BUSQUEDA,
  TAMANO_DE_PAGINA,
  cuentasDe,
  hoyEnColombia,
  instanteUtc,
  montarAuditoria,
  paresDelDetalle,
  recursoDeConsulta,
  recursoDeExportacion,
  resolverFiltros,
} from './auditoria.js';

const MARCADO = readFileSync(new URL('./auditoria.html', import.meta.url), 'utf8');
const MAIN = MARCADO.match(/<main[\s\S]*<\/main>/)[0];
const LYRA = '7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55';
const MODERADORA = '5b2e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55';
const AHORA = Date.parse('2026-10-14T15:00:00Z');

const asentar = async () => {
  for (let i = 0; i < 8; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
};

const entrada = (extra = {}) => ({
  id: 'e-1',
  fechaHora: '2026-10-14T15:00:02Z',
  ocurridoEn: '2026-10-14T15:00:00Z',
  administrador: MODERADORA,
  tipoAccion: 'SANCION',
  afectado: LYRA,
  valorAnterior: null,
  valorNuevo: 'Advertencia · Causal: Lenguaje ofensivo',
  motivo: 'Insultos en el chat general',
  ipOrigen: '203.0.113.9',
  referencia: 'moderacion-sanciones:a-1',
  ...extra,
});

const pagina = (content, extra = {}) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: TAMANO_DE_PAGINA,
  ...extra,
});

const datos = (cuerpo) => ({ resultado: RESULTADO.DATOS, datos: cuerpo, estado: 200, motivo: '' });

/** Un `consultar` simulado que responde por ruta; guarda lo que se le pide. */
function consultaSimulada(responder) {
  const pedidos = [];
  const consultarApi = jest.fn(async (recurso) => {
    pedidos.push(recurso);
    return responder(recurso);
  });
  return { consultarApi, pedidos };
}

function titularesDe(lista) {
  return {
    buscar: jest.fn(async (texto) =>
      lista === null
        ? { resultado: RESULTADO.SERVICIO_DEGRADADO, datos: null }
        : datos(lista.filter((t) => t.apodo.toLowerCase().includes(texto.toLowerCase()))),
    ),
  };
}

describe('lo que se pide a la bitácora', () => {
  test('dieciséis por página, lo más reciente primero y los filtros repetibles', () => {
    expect(
      recursoDeConsulta(
        {
          administradores: ['moderadora_qa', MODERADORA],
          afectados: [LYRA],
          tipoAccion: 'SANCION',
          desde: '2026-10-13T05:00:00.000Z',
          hasta: null,
        },
        2,
      ),
    ).toBe(
      `/admin/auditoria?page=2&size=16&sort=fechaHora,desc&sort=id,desc&administradorId=moderadora_qa&administradorId=${MODERADORA}` +
        `&afectado=${LYRA}&tipoAccion=SANCION&desde=2026-10-13T05%3A00%3A00.000Z`,
    );
    expect(recursoDeConsulta({}, 0)).toBe(
      '/admin/auditoria?page=0&size=16&sort=fechaHora,desc&sort=id,desc',
    );
  });

  test('la exportación lleva los mismos filtros y su formato', () => {
    expect(recursoDeExportacion({ afectados: ['parametro:chat.historial.tamano'] }, 'csv')).toBe(
      '/admin/auditoria/exportar?afectado=parametro%3Achat.historial.tamano&formato=csv',
    );
    expect(recursoDeExportacion({}, 'pdf')).toBe('/admin/auditoria/exportar?formato=pdf');
  });

  test('los días del filtro en hora de Colombia, enviados en UTC', () => {
    expect(instanteUtc('2026-10-14')).toBe('2026-10-14T05:00:00.000Z');
    expect(instanteUtc('2026-10-14', { finDelDia: true })).toBe('2026-10-15T05:00:00.000Z');
    expect(instanteUtc('')).toBeNull();
    // A las 2 a. m. UTC todavía es el día anterior en Colombia.
    expect(hoyEnColombia(Date.parse('2026-10-15T02:00:00Z'))).toBe('2026-10-14');
  });
});

describe('los filtros por apodo', () => {
  const lista = [
    { uid: MODERADORA, apodo: 'moderadora_qa' },
    { uid: LYRA, apodo: 'moderadora_qa_bis' },
  ];

  test('un uid pasa tal cual; un apodo exacto gana a los parecidos', async () => {
    expect(await cuentasDe(LYRA.toUpperCase(), titularesDe(lista))).toEqual({
      uids: [LYRA],
      apodos: [],
      respondio: true,
    });
    expect(await cuentasDe('moderadora_qa', titularesDe(lista))).toEqual({
      uids: [MODERADORA],
      apodos: ['moderadora_qa'],
      respondio: true,
    });
    const parecidos = await cuentasDe('moderadora', titularesDe(lista));
    expect(parecidos.uids).toEqual([MODERADORA, LYRA]);
    expect(CUENTAS_POR_BUSQUEDA).toBe(20);
  });

  test('quién actuó pide lo escrito, sus apodos y sus uid; la cuenta afectada, sus uid', async () => {
    const { filtro, sinIdentidad } = await resolverFiltros(
      {
        quien: 'moderadora_qa',
        cuenta: 'moderadora_qa_bis',
        tipoAccion: 'SANCION',
        desde: '2026-10-01',
      },
      titularesDe(lista),
    );
    expect(sinIdentidad).toBe(false);
    expect(filtro).toEqual({
      administradores: ['moderadora_qa', MODERADORA],
      afectados: [LYRA],
      tipoAccion: 'SANCION',
      desde: '2026-10-01T05:00:00.000Z',
      hasta: null,
    });
  });

  test('si identidad no responde se filtra por lo escrito y se avisa', async () => {
    const { filtro, sinIdentidad } = await resolverFiltros(
      { quien: 'lyra', cuenta: 'parametro:x' },
      titularesDe(null),
    );
    expect(sinIdentidad).toBe(true);
    expect(filtro.administradores).toEqual(['lyra']);
    expect(filtro.afectados).toEqual(['parametro:x']);
  });
});

describe('el detalle de una entrada', () => {
  test('con el momento, el registro, nombres y, al final, los identificadores', () => {
    const titulares = {
      de: (uid) =>
        ({ [LYRA]: { apodo: 'lyra_92' }, [MODERADORA]: { apodo: 'moderadora_qa' } })[uid],
    };
    const pares = Object.fromEntries(paresDelDetalle(entrada(), titulares));
    expect(pares['Ocurrió']).toMatch(/2026/);
    expect(pares.Registrada).toMatch(/2026/);
    expect(pares['Quién']).toBe('moderadora_qa');
    expect(pares['Acción']).toBe('Sanción');
    expect(pares.Sobre).toBe('lyra_92 · Cuenta');
    expect(pares['Después']).toBe('Advertencia · Causal: Lenguaje ofensivo');
    expect(pares['Dirección IP']).toBe('203.0.113.9');
    expect(pares['Cuenta de quien actuó']).toBe(MODERADORA);
    expect(pares['Identificador de lo afectado']).toBe(LYRA);
    expect(pares['N.º de registro']).toBe('e-1');
    expect(Object.keys(pares)).not.toContain('Referencia');
  });

  test('SA-18: una operación del catálogo, con el nombre que tenía el producto y su reactivación', () => {
    const titulares = { de: (uid) => ({ [MODERADORA]: { apodo: 'admin_catalogo' } })[uid] };
    const pares = Object.fromEntries(
      paresDelDetalle(
        entrada({
          tipoAccion: 'REACTIVACION',
          afectado: 'producto:550e8400-e29b-41d4-a716-446655440000',
          nombreAfectado: 'Espada solar',
          valorAnterior: 'Estado: Suspendido',
          valorNuevo: 'Estado: Activo',
          motivo: 'Vuelve a la tienda',
          referencia: 'productos:op-1',
        }),
        titulares,
      ),
    );
    expect(pares['Acción']).toBe('Reactivación');
    expect(pares.Sobre).toBe('«Espada solar» · Producto');
    expect(pares.Antes).toBe('Estado: Suspendido');
    expect(pares['Después']).toBe('Estado: Activo');
    expect(pares['Identificador de lo afectado']).toBe('550e8400-e29b-41d4-a716-446655440000');
  });
});

describe('la vista montada', () => {
  beforeEach(() => {
    document.body.innerHTML = MAIN;
  });

  function servicioDeAuditoria({ entradas = [entrada()], fallo = null } = {}) {
    return consultaSimulada((recurso) => {
      if (recurso.startsWith('/admin/titulares')) {
        return datos([
          { uid: LYRA, apodo: 'lyra_92', estado: 'ACTIVO' },
          { uid: MODERADORA, apodo: 'moderadora_qa', estado: 'ACTIVO' },
        ]);
      }
      if (recurso.startsWith('/admin/auditoria?size=1')) {
        return recurso.includes('SECURITY_BYPASS_ATTEMPT')
          ? { resultado: RESULTADO.VACIO, datos: pagina([]), estado: 200, motivo: '' }
          : datos(pagina([entrada()], { totalElements: 12 }));
      }
      if (fallo) {
        return fallo;
      }
      return entradas.length
        ? datos(pagina(entradas))
        : { resultado: RESULTADO.VACIO, datos: pagina([]), estado: 200, motivo: '' };
    });
  }

  test('cifras, y la tabla con nombres: ningún identificador ni prefijo técnico a la vista', async () => {
    const { consultarApi, pedidos } = servicioDeAuditoria({
      entradas: [
        entrada(),
        entrada({
          id: 'e-2',
          administrador: 'superadmin_qa',
          tipoAccion: 'ACTUALIZACION',
          afectado: 'parametro:chat.historial.tamano',
          valorAnterior: '50',
          valorNuevo: '60',
        }),
      ],
    });
    montarAuditoria(document, { consultarApi, ahora: () => AHORA });
    await asentar();

    const indicadores = document.querySelector('[data-zona="indicadores"]').textContent;
    expect(indicadores).toContain('Acciones hoy');
    expect(indicadores).toContain('12');
    expect(indicadores).toContain('Accesos no autorizados (30 días)');

    const tabla = document.querySelector('[data-zona="tabla"]');
    expect(tabla.querySelectorAll('tbody tr')).toHaveLength(2);
    const texto = tabla.textContent;
    expect(texto).toContain('moderadora_qa');
    expect(texto).toContain('lyra_92');
    expect(texto).toContain('Sanción');
    expect(texto).toContain('50 → 60');
    expect(texto).not.toContain(LYRA);
    expect(texto).not.toContain(MODERADORA);
    expect(texto).not.toMatch(/parametro:|comentario:|GET \/|HTTP/);
    expect(pedidos.find((p) => p.startsWith('/admin/auditoria?page=0'))).toContain(
      'sort=fechaHora,desc&sort=id,desc',
    );
    expect(pedidos.filter((p) => p.startsWith('/admin/titulares'))).toHaveLength(1);
    expect(document.querySelector('[data-zona="encabezado"]').textContent).not.toMatch(
      /HU-|RF-|Fuente/,
    );
  });

  test('«Ver» abre la entrada completa en el panel lateral', async () => {
    const { consultarApi } = servicioDeAuditoria();
    const abrirDetalle = jest.fn();
    montarAuditoria(document, { consultarApi, ahora: () => AHORA, abrirDetalle });
    await asentar();

    document.querySelector('[data-accion="ver-entrada"]').click();
    expect(abrirDetalle).toHaveBeenCalledTimes(1);
    const { titulo, subtitulo, contenido } = abrirDetalle.mock.calls[0][0];
    expect(titulo).toBe('Sanción');
    expect(subtitulo).toBe('lyra_92');
    expect(contenido.textContent).toContain('Insultos en el chat general');
    expect(contenido.textContent).toContain('203.0.113.9');
  });

  test('vacío y error se dicen; el error se puede reintentar', async () => {
    const vacio = servicioDeAuditoria({ entradas: [] });
    montarAuditoria(document, { consultarApi: vacio.consultarApi, ahora: () => AHORA });
    await asentar();
    expect(document.querySelector('[data-zona="tabla"]').textContent).toContain(
      'Todavía no hay acciones registradas',
    );

    document.body.innerHTML = MAIN;
    const caido = servicioDeAuditoria({
      fallo: {
        resultado: RESULTADO.SERVICIO_DEGRADADO,
        datos: null,
        estado: 503,
        motivo: 'El servicio no responde.',
      },
    });
    montarAuditoria(document, { consultarApi: caido.consultarApi, ahora: () => AHORA });
    await asentar();
    const zona = document.querySelector('[data-zona="tabla"]');
    expect(zona.textContent).toContain('No se pudo cargar el registro');
    expect(zona.querySelector('[data-accion="reintentar"]')).not.toBeNull();
  });

  test('exportar en CSV y en PDF con los filtros; un rechazo del servicio se explica', async () => {
    const { consultarApi } = servicioDeAuditoria();
    const descargarApi = jest
      .fn()
      .mockResolvedValueOnce({
        resultado: RESULTADO.DATOS,
        contenido: new Blob(['x']),
        nombreArchivo: 'auditoria-1.csv',
      })
      .mockResolvedValueOnce({
        resultado: RESULTADO.NO_DISPONIBLE,
        contenido: null,
        motivo:
          'La exportación tiene 15000 registros, que supera el máximo de 10000 por operación.',
      });
    const guardar = jest.fn();
    montarAuditoria(document, { consultarApi, descargarApi, guardar, ahora: () => AHORA });
    await asentar();

    document.querySelector('[data-accion="exportar-csv"]').click();
    await asentar();
    expect(descargarApi.mock.calls[0][0]).toBe('/admin/auditoria/exportar?formato=csv');
    expect(guardar).toHaveBeenCalledWith(
      'auditoria-1.csv',
      expect.any(Blob),
      'text/csv;charset=utf-8',
    );

    document.querySelector('[data-accion="exportar-pdf"]').click();
    await asentar();
    expect(descargarApi.mock.calls[1][0]).toBe('/admin/auditoria/exportar?formato=pdf');
    expect(descargarApi.mock.calls[1][1]).toEqual({
      acepta: 'application/pdf, application/problem+json',
    });
    expect(document.querySelector('[data-zona="aviso"]').textContent).toContain('supera el máximo');
    expect(document.querySelector('[data-accion="exportar-pdf"]').disabled).toBe(false);
  });
});
