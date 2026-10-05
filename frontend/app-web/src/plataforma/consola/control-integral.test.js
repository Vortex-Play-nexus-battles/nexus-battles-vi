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
import {
  SECCIONES,
  formatearFecha,
  formatearInstante,
  montarControlIntegral,
  pintarAlertasDeModeracion,
} from './control-integral.js';

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
      id: 15,
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
      id: 16,
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

/** `TorneoResumen` de torneos.yaml 1.2.0, tal cual. */
const TORNEO = {
  id: '9a8b7c6d-1111-2222-3333-444455556666',
  nombre: 'Copa Nexo',
  estado: 'INSCRIPCIONES_ABIERTAS',
  creadoEn: '2026-10-01T12:00:00Z',
  inscripcionesCierranEn: '2026-10-31T23:00:00Z',
  costoInscripcion: 0,
  equiposInscritos: 3,
  cupos: 8,
  campeonEquipoId: null,
};

/** `PaginaDeSubastas` de ms-subastas-listado.yaml 1.2.0, tal cual. */
const SUBASTAS = {
  contenido: [
    {
      id: '7d1f0c2e-aaaa-bbbb-cccc-ddddeeeeffff',
      estado: 'ACTIVA',
      nombreProducto: 'Espada corta',
      tipoProducto: 'ARMA',
      rareza: null,
      precioInicial: 50,
      ofertaVigente: 65,
      precioCompraInmediata: null,
      cantidadPujas: 2,
      fechaFin: '2026-10-06T18:00:00Z',
      esMaestroDeJuego: false,
      vendedorId: null,
      esPropia: false,
    },
  ],
  pagina: 0,
  tamanoPagina: 16,
  totalElementos: 13,
  totalPaginas: 1,
};

/** `PaginaDeSalas` de salas-partidas.yaml: sin nombre de sala ni apodo de anfitrión. */
const SALAS = {
  contenido: [
    {
      id: '3c2b1a00-1234-4321-8888-999900001111',
      estado: 'ABIERTA',
      modalidad: 'UNO_CONTRA_UNO',
      maximoParticipantes: 2,
      ocupacion: 1,
      recompensaCreditos: 100,
      incluirHeroeIA: false,
      heroesIA: 0,
      privada: true,
      idAnfitrion: '0f0e0d0c-5555-6666-7777-888899990000',
      participantes: ['0f0e0d0c-5555-6666-7777-888899990000'],
      idPartida: null,
      creadaEn: '2026-10-04T15:00:00Z',
    },
  ],
  pagina: 0,
  tamano: 16,
  totalElementos: 1,
  totalPaginas: 1,
};

/** Un UUID a la vista es un dato que nadie puede leer (RFINAL-06). */
const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

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
        total: 18,
        operativos: 12,
        caidos: 2,
        noDesplegados: 3,
        noObservables: 1,
        instante: '2026-09-24T02:00:00Z',
        servicios: [
          { servicio: 'ms-identidad', estado: 'OPERATIVO', detalle: 'HTTP 200' },
          { servicio: 'ms-subastas', estado: 'CAIDO', detalle: 'Connection refused' },
          { servicio: 'ms-chatbot', estado: 'NO_DESPLEGADO', detalle: 'fuera del host' },
          { servicio: 'heroes', estado: 'NO_OBSERVABLE', detalle: 'en otro host' },
        ],
      });
    }
    if (recurso.startsWith('/torneos')) {
      return conDatos([TORNEO]);
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

  test('UXC-9 — cada cuenta enlaza a su historial de sanciones por su uid', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const enlaces = [
      ...raiz.querySelectorAll('[data-panel="directorio"] tbody a[data-accion="ver-sanciones"]'),
    ];
    expect(enlaces).toHaveLength(2);
    const destino = new URL(enlaces[0].href);
    expect(destino.pathname).toMatch(/moderacion-sanciones\/sanciones-admin\.html$/);
    expect(destino.searchParams.get('usuario')).toBe('11111111-2222-3333-4444-555555555555');
    expect(enlaces[0].getAttribute('aria-label')).toBe('Ver las sanciones de Ana');
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

    expect(consultarApi.mock.calls.at(-1)[0]).toBe(
      '/admin/jugadores?page=0&size=20&buscar=Ana&ocultarPruebas=true',
    );
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

    expect(consultarApi.mock.calls.at(-1)[0]).toBe(
      '/admin/jugadores?page=1&size=20&ocultarPruebas=true',
    );
  });

  /**
   * RFINAL-06 — «las primeras veinte filas del directorio son cuentas qa_,
   * smoke_ y canario» (revisión del 4-oct). No se borra nada: el servidor las
   * excluye antes de paginar (ms-identidad-admin.yaml 1.2.0,
   * `ocultarPruebas`), así que la página y el total cuadran.
   */
  test('RFINAL-06: abre ocultando, en el servidor, las cuentas de pruebas automáticas', async () => {
    const consultarApi = apiSimulada();
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi });
    await asentar();

    const directorio = raiz.querySelector('[data-panel="directorio"]');
    const casilla = directorio.querySelector('input[type="checkbox"][name="ocultarPruebas"]');
    expect(casilla.checked).toBe(true);
    expect(casilla.closest('label').textContent).toContain(
      'Ocultar cuentas de pruebas automáticas',
    );
    expect(consultarApi.mock.calls.map((c) => c[0]).filter((r) => r.includes('size=20'))).toEqual([
      '/admin/jugadores?page=0&size=20&ocultarPruebas=true',
    ]);
    expect(directorio.querySelector('caption').textContent).toContain(
      'sin contar las de pruebas automáticas',
    );
  });

  test('RFINAL-06: quitar la casilla las vuelve a pedir, desde la primera página', async () => {
    const consultarApi = apiSimulada();
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi });
    await asentar();
    const directorio = raiz.querySelector('[data-panel="directorio"]');
    directorio.querySelector('[data-accion="siguiente"]').dispatchEvent(new window.Event('click'));
    await asentar();

    const casilla = directorio.querySelector('input[name="ocultarPruebas"]');
    casilla.checked = false;
    casilla.dispatchEvent(new window.Event('change'));
    await asentar();

    expect(consultarApi.mock.calls.at(-1)[0]).toBe('/admin/jugadores?page=0&size=20');
    expect(directorio.querySelector('caption').textContent).toContain('cuentas en total');
  });

  /**
   * RFINAL-06 — «hoy solo se edita tras buscar por ID». Cada fila abre la
   * ficha de gestión de esa cuenta con su clave (`id`, 1.2.0), sin copiar
   * nada; la clave no se pinta como dato.
   */
  test('RFINAL-06: cada cuenta abre su ficha de gestión sin copiar identificadores', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const enlaces = [
      ...raiz.querySelectorAll('[data-panel="directorio"] tbody a[data-accion="gestionar"]'),
    ];
    expect(enlaces).toHaveLength(2);
    const destino = new URL(enlaces[0].href);
    expect(destino.pathname).toMatch(/cuentas\/gestion-usuarios\.html$/);
    expect(destino.searchParams.get('usuario')).toBe('15');
    expect(enlaces[0].textContent).toBe('Gestionar');
    expect(enlaces[0].getAttribute('aria-label')).toBe('Gestionar la cuenta de Ana');
  });

  test('una cuenta sin clave (servicio anterior a 1.2.0) dice «sin dato» en vez de un enlace roto', async () => {
    const sinClave = {
      ...JUGADORES,
      contenido: JUGADORES.contenido.map(({ id: _id, ...resto }) => resto),
    };
    const raiz = pagina();

    montarControlIntegral(
      raiz,
      {},
      { consultarApi: apiSimulada({ '/admin/jugadores?page=0&size=20': conDatos(sinClave) }) },
    );
    await asentar();

    const directorio = raiz.querySelector('[data-panel="directorio"]');
    expect(directorio.querySelectorAll('a[data-accion="gestionar"]')).toHaveLength(0);
    expect(directorio.textContent).toContain('sin dato');
  });
});

describe('sistema', () => {
  test('distingue los cuatro estados con palabras, no solo con color', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const sistema = raiz.querySelector('[data-panel="sistema"]');
    const sellos = [...sistema.querySelectorAll('.tabla .sello-estado')].map((s) => s.textContent);
    expect(sellos).toEqual(['OPERATIVO', 'CAIDO', 'NO DESPLEGADO', 'NO OBSERVABLE']);
    expect(sistema.textContent).toContain('12 operativos');
    expect(sistema.textContent).toContain('de 18 catalogados');
  });

  /** Un servicio de otro host no puede salir en rojo: no esta roto. */
  test('lo que vive en otro host no se pinta como caido', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const sellos = [...raiz.querySelectorAll('[data-panel="sistema"] .tabla .sello-estado')];
    const caido = sellos.find((s) => s.textContent === 'CAIDO');
    const otroHost = sellos.find((s) => s.textContent === 'NO OBSERVABLE');
    expect(caido.dataset.estado).toBe('malo');
    expect(otroHost.dataset.estado).toBe('neutro');
  });

  /**
   * RFINAL-08: el servicio que conecta y no contesta a tiempo no está caído ni
   * operativo. Va en ámbar, con su palabra, y se cuenta aparte; y el panel dice
   * de qué hora es la medición, porque la ronda se reutiliza unos segundos.
   */
  test('un servicio lento va en ámbar, se cuenta aparte y se ve la hora de la medición', async () => {
    const raiz = pagina();
    const instante = '2026-10-05T15:04:05Z';

    montarControlIntegral(
      raiz,
      {},
      {
        consultarApi: apiSimulada({
          '/admin/sistema/servicios': conDatos({
            total: 3,
            operativos: 1,
            caidos: 1,
            lentos: 1,
            noDesplegados: 0,
            noObservables: 0,
            instante,
            desdeCache: true,
            servicios: [
              { servicio: 'torneos', estado: 'OPERATIVO', detalle: '' },
              { servicio: 'misiones', estado: 'LENTO', detalle: 'sin respuesta en 1500 ms' },
              { servicio: 'correo', estado: 'CAIDO', detalle: 'conexión rechazada' },
            ],
          }),
        }),
      },
    );
    await asentar();

    const sistema = raiz.querySelector('[data-panel="sistema"]');
    const lento = [...sistema.querySelectorAll('.tabla .sello-estado')].find(
      (s) => s.textContent === 'LENTO',
    );
    expect(lento.dataset.estado).toBe('aviso');
    expect(sistema.textContent).toContain('sin respuesta en 1500 ms');
    expect(sistema.textContent).toContain('1 operativos, 1 caídos, 1 lentos');
    expect(sistema.querySelector('[data-zona="medido"]').textContent).toBe(
      `Medido a las ${formatearInstante(instante)}.`,
    );
  });

  test('sin lentos no se menciona la palabra: el resumen de siempre no cambia', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    expect(raiz.querySelector('[data-panel="sistema"]').textContent).not.toContain('lentos');
  });
});

describe('las consultas son las que el sistema atiende de verdad', () => {
  test('la auditoria se lee de /admin/auditoria, no de la ruta de escritura', async () => {
    const consultarApi = apiSimulada();
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi });
    await asentar();

    const pedidas = consultarApi.mock.calls.map((c) => c[0]);
    expect(pedidas.some((r) => r.startsWith('/admin/auditoria?'))).toBe(true);
    // /admin/auditoria/eventos acepta POST; a un GET responde 405.
    expect(pedidas.some((r) => r.startsWith('/admin/auditoria/eventos'))).toBe(false);
  });

  /**
   * RFINAL-06 (revisión del 4-oct): el panel decía «últimos quince eventos» y
   * pintaba los más antiguos (23 y 24 de septiembre), porque sin `sort` el
   * orden lo decide la base de datos.
   */
  test('la bitácora pide los quince más recientes, con desempate estable', async () => {
    const consultarApi = apiSimulada();
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi });
    await asentar();

    const pedida = consultarApi.mock.calls
      .map((c) => c[0])
      .find((r) => r.startsWith('/admin/auditoria?'));
    const consulta = new URLSearchParams(pedida.split('?')[1]);
    expect(consulta.get('page')).toBe('0');
    expect(consulta.get('size')).toBe('15');
    expect(consulta.getAll('sort')).toEqual(['fechaHora,desc', 'id,desc']);
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

/**
 * RFINAL-06 — revisión del super administrador en DEV (4-oct, §3.2): «Subastas
 * activas» con elemento y cierre «--», «Torneos» con inicio «--» y «Salas y
 * partidas» con el id de la sala y el anfitrión «--». El panel leía campos que
 * ningún contrato publica (`elemento.nombre`, `cierraEn`, `fechaInicio`,
 * `anfitrion`). Ahora lee los del contrato de cada fuente, y lo que el
 * contrato no trae se dice («sin dato»), no se rellena con un guion ni con un
 * identificador.
 */
describe('cada panel lee los campos de su contrato', () => {
  async function montarCon(sobrescribir) {
    const raiz = pagina();
    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada(sobrescribir) });
    await asentar();
    return raiz;
  }

  const celdas = (panel) =>
    [...panel.querySelectorAll('tbody td')].map((td) => td.textContent.trim());
  const encabezados = (panel) =>
    [...panel.querySelectorAll('thead th')].map((th) => th.textContent.trim());

  test('subastas: el producto, la oferta vigente, las pujas y el cierre; nunca el id', async () => {
    const raiz = await montarCon({ '/subastas': conDatos(SUBASTAS) });
    const panel = raiz.querySelector('[data-panel="subastas"]');

    expect(encabezados(panel)).toEqual(['Elemento', 'Precio actual', 'Pujas', 'Cierra']);
    const fila = celdas(panel);
    expect(fila.slice(0, 3)).toEqual(['Espada corta', '65', '2']);
    expect(fila[3]).toBe(formatearFecha('2026-10-06T18:00:00Z'));
    expect(fila).not.toContain('--');
    expect(panel.textContent).not.toMatch(UUID);
    expect(panel.querySelector('caption').textContent).toContain('13');
  });

  test('torneos: equipos inscritos de los cupos y el cierre de inscripciones', async () => {
    const raiz = await montarCon({});
    const panel = raiz.querySelector('[data-panel="torneos"]');

    expect(encabezados(panel)).toEqual(['Torneo', 'Estado', 'Equipos', 'Inscripciones hasta']);
    const fila = celdas(panel);
    expect(fila.slice(0, 3)).toEqual(['Copa Nexo', 'INSCRIPCIONES_ABIERTAS', '3 de 8']);
    expect(fila[3]).toBe(formatearFecha('2026-10-31T23:00:00Z'));
    expect(panel.textContent).not.toMatch(UUID);
  });

  test('salas: modalidad y ocupación; el anfitrión que el contrato no trae es «sin dato»', async () => {
    const raiz = await montarCon({ '/salas': conDatos(SALAS) });
    const panel = raiz.querySelector('[data-panel="salas"]');

    expect(encabezados(panel)).toEqual(['Sala', 'Estado', 'Jugadores', 'Creada', 'Anfitrión']);
    const fila = celdas(panel);
    expect(fila.slice(0, 3)).toEqual(['1 contra 1 · privada', 'ABIERTA', '1 de 2']);
    expect(fila[3]).toBe(formatearFecha('2026-10-04T15:00:00Z'));
    expect(fila[4]).toBe('sin dato');
    expect(panel.textContent).not.toMatch(UUID);
  });

  test('un campo que la respuesta no trae se dice «sin dato», no «--»', async () => {
    const raiz = await montarCon({
      '/subastas': conDatos({ contenido: [{ id: SUBASTAS.contenido[0].id }] }),
      '/torneos': conDatos([{ id: TORNEO.id }]),
    });

    for (const id of ['subastas', 'torneos']) {
      const fila = celdas(raiz.querySelector(`[data-panel="${id}"]`));
      expect(fila).not.toContain('--');
      expect(fila.join(' ')).not.toMatch(UUID);
      expect(fila).toContain('sin dato');
    }
  });
});

describe('maquetado del aviso', () => {
  /**
   * `.aviso` del kit es una fila: [cuerpo][accion]. Si el titulo, el motivo y
   * el detalle cuelgan como hermanos sueltos salen como cuatro columnas
   * estrujadas, que es como se vio en dev la primera vez.
   */
  test('el titulo, el motivo y el detalle van dentro de aviso__cuerpo', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const aviso = raiz.querySelector('[data-panel="subastas"] .aviso');
    expect(aviso.children.length).toBe(2);
    const cuerpo = aviso.querySelector('.aviso__cuerpo');
    expect(cuerpo.querySelector('.aviso__titulo').textContent).toBe('SERVICIO DEGRADADO');
    expect(cuerpo.querySelector('.aviso__detalle').textContent).toContain('GET /api/v1/subastas');
    expect(aviso.lastElementChild.dataset.accion).toBe('reintentar');
  });

  test('sin permiso el aviso lleva solo el cuerpo', async () => {
    const raiz = pagina();

    montarControlIntegral(raiz, {}, { consultarApi: apiSimulada() });
    await asentar();

    const aviso = raiz.querySelector('[data-panel="auditoria"] .aviso');
    expect(aviso.children.length).toBe(1);
    expect(aviso.firstElementChild.className).toBe('aviso__cuerpo');
  });
});

describe('alertas de moderación (UXC-9, §7.3.4)', () => {
  const contenedor = (partes) => {
    const caja = document.createElement('div');
    caja.append(...partes);
    return caja;
  };

  test('el panel sale de GET /api/v1/moderacion y pinta las alertas del servicio', async () => {
    const raiz = pagina();
    const consultarApi = apiSimulada({
      '/moderacion': conDatos({
        sanciones: {},
        alertasConfiguradas: true,
        umbralSancionesPorDia: 10,
        alertas: ['2026-09-20: 14 sanciones (umbral 10)'],
        pendientes: [],
      }),
    });

    montarControlIntegral(raiz, {}, { consultarApi });
    await asentar();

    const panelDeAlertas = raiz.querySelector('[data-panel="alertas-moderacion"]');
    expect(panelDeAlertas.textContent).toContain('Fuente: GET /api/v1/moderacion');
    expect(panelDeAlertas.querySelector('[data-zona="alertas"]').textContent).toContain(
      '2026-09-20: 14 sanciones (umbral 10)',
    );
  });

  test('sin umbral configurado lo dice, en vez de un «sin alertas» que no se evaluó', () => {
    const caja = contenedor(
      pintarAlertasDeModeracion({ alertasConfiguradas: false, alertas: [], pendientes: [] }),
    );
    expect(caja.querySelector('[data-zona="sin-umbral"]').textContent).toMatch(
      /Sin umbral configurado/,
    );
    expect(caja.querySelector('[data-zona="sin-alertas"]')).toBeNull();
  });

  test('con umbral y sin días por encima, «sin alertas» con el umbral', () => {
    const caja = contenedor(
      pintarAlertasDeModeracion({
        alertasConfiguradas: true,
        umbralSancionesPorDia: 10,
        alertas: [],
        pendientes: ['registro de usuarios sin contrato de lectura'],
      }),
    );
    expect(caja.querySelector('[data-zona="sin-alertas"]').textContent).toBe(
      'Sin alertas: ningún día supera el umbral de 10 sanciones.',
    );
    expect(caja.querySelector('[data-zona="pendientes"]').textContent).toBe(
      'Pendiente: registro de usuarios sin contrato de lectura',
    );
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
