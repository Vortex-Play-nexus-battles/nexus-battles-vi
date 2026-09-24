/**
 * La consola de control integral, montada contra un sistema medio caido.
 *
 * Es el escenario real del entorno de demostracion: identidad y torneos
 * responden, subastas esta caida, misiones no existe y auditoria niega el
 * permiso. Lo que se prueba es que la pantalla siga sirviendo -- y que cada
 * hueco diga POR QUE esta vacio, que es lo unico que distingue una consola
 * de operacion de un tablero decorativo.
 */

import { jest } from '@jest/globals';

import { RESULTADO } from './cliente-consola.js';
import { SECCIONES, formatearFecha, montarControlIntegral } from './control-integral.js';

const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

const conDatos = (datos) => ({
  resultado: RESULTADO.DATOS,
  datos,
  estado: 200,
  motivo: '',
  recurso: '',
  detalle: null,
});

const conFallo = (resultado, estado, recurso) => ({
  resultado,
  datos: null,
  estado,
  motivo: 'motivo de prueba',
  recurso,
  detalle: null,
});

const JUGADORES = {
  contenido: [
    {
      uid: '11111111-2222-3333-4444-555555555555',
      apodo: 'Ana',
      email: 'ana@nexus.test',
      estado: 'ACTIVO',
      rol: 'JUGADOR',
      creadoEn: '2026-01-05T10:00:00',
      ultimoAcceso: '2026-09-20T18:30:00',
      suspendidoHasta: null,
      bloqueada: false,
    },
    {
      uid: '22222222-2222-3333-4444-555555555555',
      apodo: 'Beto',
      email: 'beto@nexus.test',
      estado: 'ACTIVO',
      rol: 'JUGADOR',
      creadoEn: '2026-02-05T10:00:00',
      ultimoAcceso: null,
      suspendidoHasta: null,
      bloqueada: true,
    },
  ],
  pagina: 0,
  tamano: 20,
  total: 42,
  totalPaginas: 3,
};

/** Un sistema medio caido, como el del entorno de demostracion. */
function apiSimulada(sobrescribir = {}) {
  return jest.fn(async (recurso) => {
    for (const [prefijo, respuesta] of Object.entries(sobrescribir)) {
      if (recurso.startsWith(prefijo)) {
        return typeof respuesta === 'function' ? respuesta(recurso) : respuesta;
      }
    }
    if (recurso.startsWith('/admin/jugadores')) {
      return conDatos(JUGADORES);
    }
    if (recurso.startsWith('/admin/sistema/servicios')) {
      return conDatos({
        total: 17,
        operativos: 12,
        caidos: 2,
        noDesplegados: 3,
        instante: '2026-09-24T02:00:00Z',
        servicios: [
          { nombre: 'ms-identidad', estado: 'OPERATIVO', detalle: 'HTTP 200' },
          { nombre: 'ms-subastas', estado: 'CAIDO', detalle: 'HTTP 502' },
          { nombre: 'ms-chatbot', estado: 'NO_DESPLEGADO', detalle: 'fuera de dev' },
        ],
      });
    }
    if (recurso.startsWith('/torneos')) {
      return conDatos([{ nombre: 'Copa Nexo', estado: 'ABIERTO', fechaInicio: '2026-10-01' }]);
    }
    if (recurso.startsWith('/sanciones/metricas')) {
      return conDatos({ total: 0, apelaciones: { PENDIENTE: 0 } });
    }
    if (recurso.startsWith('/subastas')) {
      return conFallo(RESULTADO.SERVICIO_DEGRADADO, 502, recurso);
    }
    if (recurso.startsWith('/admin/auditoria')) {
      return conFallo(RESULTADO.SIN_PERMISO, 403, recurso);
    }
    if (recurso.startsWith('/comentarios/moderacion')) {
      return conDatos({ entradas: [{ id: 1 }, { id: 2 }], total: 2, pagina: 0, tamano: 20 });
    }
    if (recurso.startsWith('/transacciones')) {
      return conFallo(RESULTADO.SERVICIO_DEGRADADO, 502, recurso);
    }
    return conDatos([]);
  });
}

function pagina() {
  document.body.innerHTML = '<div data-zona="encabezado"></div><div data-zona="secciones"></div>';
  return document;
}

describe('montaje', () => {
  test('pinta las ocho secciones del mandato', async () => {
    const raiz = pagina();

    montarControlIntegral(
      raiz,
      { apodo: 'admin.simon', rol: 'SUPER_ADMINISTRADOR' },
      {
        consultarApi: apiSimulada(),
      },
    );
    await asentar();

    const pintadas = [...raiz.querySelectorAll('[data-seccion]')].map((s) => s.dataset.seccion);
    expect(pintadas).toEqual(SECCIONES.map((s) => s.id));
  });

  test('cada panel dice de que endpoint sale su dato', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const fuentes = [...raiz.querySelectorAll('.panel-admin__fuente')].map((p) => p.textContent);
    expect(fuentes.length).toBeGreaterThan(8);
    expect(fuentes.every((t) => t.startsWith('Fuente: GET /api/v1'))).toBe(true);
  });
});

describe('un servicio caido no tumba la consola', () => {
  test('subastas caida se pinta como SERVICIO DEGRADADO y torneos sigue con datos', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const subastas = raiz.querySelector('[data-panel="subastas"]');
    expect(subastas.querySelector('.sello-estado').textContent).toBe('SERVICIO DEGRADADO');
    expect(subastas.textContent).toContain('GET /api/v1/subastas');

    const torneos = raiz.querySelector('[data-panel="torneos"]');
    expect(torneos.querySelector('.sello-estado').textContent).toBe('EN LINEA');
    expect(torneos.textContent).toContain('Copa Nexo');
  });

  test('un panel degradado ofrece reintentar y vuelve a consultar solo lo suyo', async () => {
    const consultarApi = apiSimulada();
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi });
    await asentar();
    const llamadasAntes = consultarApi.mock.calls.length;

    raiz
      .querySelector('[data-panel="subastas"] [data-accion="reintentar"]')
      .dispatchEvent(new window.Event('click'));
    await asentar();

    expect(consultarApi.mock.calls.length).toBe(llamadasAntes + 1);
    expect(consultarApi.mock.calls.at(-1)[0]).toBe('/subastas');
  });

  test('sin permiso NO ofrece reintentar: no se arregla reintentando', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const auditoria = raiz.querySelector('[data-panel="auditoria"]');
    expect(auditoria.querySelector('.sello-estado').textContent).toBe('SIN PERMISO');
    expect(auditoria.querySelector('[data-accion="reintentar"]')).toBeNull();
  });
});

describe('nada se inventa', () => {
  test('misiones dice NO IMPLEMENTADO y no fabrica ninguna', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const misiones = raiz.querySelector('[data-panel="misiones"]');
    expect(misiones.querySelector('.sello-estado').textContent).toBe('NO IMPLEMENTADO');
    expect(misiones.textContent).toMatch(/no fabrica datos/i);
    expect(misiones.querySelector('.tabla')).toBeNull();
  });

  test('un contador sin total se pinta como guion, nunca como cero', async () => {
    const raiz = pagina();

    montarControlIntegral(
      raiz,
      {},
      { consultarApi: apiSimulada({ '/admin/jugadores?page=0&size=1': conDatos({ algo: 1 }) }) },
    );
    await asentar();

    const valor = raiz.querySelector('[data-panel="resumen-jugadores"] .indicador__valor');
    expect(valor.textContent).toBe('--');
  });

  test('un cero que el servicio dijo de verdad se pinta como cero', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const valor = raiz.querySelector('[data-panel="resumen-sanciones"] .indicador__valor');
    expect(valor.textContent).toBe('0');
  });
});

describe('directorio de jugadores', () => {
  test('lista las cuentas sin publicar ninguna credencial', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const directorio = raiz.querySelector('[data-panel="directorio"]');
    expect(directorio.textContent).toContain('ana@nexus.test');
    expect(directorio.textContent).not.toMatch(/password|hash|token|\$2a\$/i);
  });

  test('una cuenta que nunca entro lo dice, no finge una fecha', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    expect(raiz.querySelector('[data-panel="directorio"]').textContent).toContain(
      'Nunca ha entrado',
    );
  });

  test('buscar pide al servidor con el filtro y vuelve a la primera pagina', async () => {
    const consultarApi = apiSimulada();
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi });
    await asentar();

    const directorio = raiz.querySelector('[data-panel="directorio"]');
    directorio.querySelector('input[name="buscar"]').value = '  Ana  ';
    directorio
      .querySelector('form')
      .dispatchEvent(new window.Event('submit', { cancelable: true }));
    await asentar();

    expect(consultarApi.mock.calls.at(-1)[0]).toBe('/admin/jugadores?page=0&size=20&buscar=Ana');
  });

  test('la paginacion avanza y el boton de anterior arranca deshabilitado', async () => {
    const consultarApi = apiSimulada();
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi });
    await asentar();

    const directorio = raiz.querySelector('[data-panel="directorio"]');
    expect(directorio.querySelector('[data-accion="anterior"]').disabled).toBe(true);

    directorio.querySelector('[data-accion="siguiente"]').dispatchEvent(new window.Event('click'));
    await asentar();

    expect(consultarApi.mock.calls.at(-1)[0]).toBe('/admin/jugadores?page=1&size=20');
  });
});

describe('sistema', () => {
  test('distingue OPERATIVO, CAIDO y NO DESPLEGADO', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const sistema = raiz.querySelector('[data-panel="sistema"]');
    const sellos = [...sistema.querySelectorAll('.tabla .sello-estado')].map((s) => s.textContent);
    expect(sellos).toEqual(['OPERATIVO', 'CAIDO', 'NO DESPLEGADO']);
    expect(sistema.textContent).toContain('12 operativos');
  });
});

describe('las consultas son las que el sistema atiende de verdad', () => {
  test('la auditoria se lee de /admin/auditoria, no de la ruta de escritura', async () => {
    const consultarApi = apiSimulada();
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi });
    await asentar();

    const pedidas = consultarApi.mock.calls.map((c) => c[0]);
    expect(pedidas).toContain('/admin/auditoria?page=0&size=15');
    // /admin/auditoria/eventos acepta POST; a un GET responde 405.
    expect(pedidas.some((r) => r.startsWith('/admin/auditoria/eventos'))).toBe(false);
  });

  test('economia pide el historial consultable, no un libro mayor inexistente', async () => {
    const consultarApi = apiSimulada();
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi });
    await asentar();

    const pedidas = consultarApi.mock.calls.map((c) => c[0]);
    expect(pedidas).toContain('/transacciones/mi-historial');
    expect(pedidas).not.toContain('/transacciones');
  });

  test('la cola de moderacion cuenta bien aunque llame a su lista "entradas"', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    expect(raiz.querySelector('[data-panel="comentarios"] .indicador__valor').textContent).toBe(
      '2',
    );
  });

  test('sanciones ensena el recuento que publica el servicio, no una tabla inventada', async () => {
    const raiz = pagina();

    montarControlIntegral(
      raiz,
      {},
      {
        consultarApi: apiSimulada({
          '/sanciones/metricas': conDatos({
            total: 3,
            porTipo: { ADVERTENCIA: 2, SUSPENSION: 1, BANEO: 0 },
            apelaciones: { PENDIENTE: 1 },
            moderadoresActivos: 2,
          }),
        }),
      },
    );
    await asentar();

    const sanciones = raiz.querySelector('[data-panel="sanciones"]');
    expect(sanciones.textContent).toContain('ADVERTENCIA');
    expect(sanciones.textContent).toContain('Apelaciones pendiente');
    expect(sanciones.textContent).toContain('Moderadores activos');
  });
});

describe('formatearFecha', () => {
  test('nulo es un guion, no la fecha de hoy', () => {
    expect(formatearFecha(null)).toBe('--');
    expect(formatearFecha('')).toBe('--');
  });

  test('lo que no es fecha se devuelve tal cual, sin inventar', () => {
    expect(formatearFecha('no soy una fecha')).toBe('no soy una fecha');
  });
});
