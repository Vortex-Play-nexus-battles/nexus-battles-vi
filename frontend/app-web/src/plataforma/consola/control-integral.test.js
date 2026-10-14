/**
 * La consola de control integral, montada contra un sistema medio caído.
 *
 * Es el escenario real del entorno de demostración: identidad y torneos
 * responden, subastas está caída y auditoría niega el permiso. Lo que se
 * prueba es que la pantalla siga sirviendo, que cada hueco diga POR QUÉ está
 * vacío y —desde SA-2, con el informe de revisión del módulo— que lo diga como
 * un producto: sin rutas de la API, sin códigos HTTP, sin etiquetas internas
 * en mayúsculas y con las tablas del kit de la consola.
 */

import { jest } from '@jest/globals';

import { RESULTADO } from './cliente-consola.js';
import {
  FILAS_POR_PAGINA,
  SECCIONES,
  agruparPaneles,
  formatearDia,
  formatearFecha,
  montarControlIntegral,
  pintarAlertasDeModeracion,
  pintarMetricasDeSanciones,
  pintarServicios,
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

const conFallo = (resultado, estado, recurso, motivo = 'motivo de prueba') => ({
  resultado,
  datos: null,
  estado,
  motivo,
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
  tamano: 16,
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
      ofertaVigente: 1250,
      precioCompraInmediata: null,
      cantidadPujas: 2,
      fechaFin: '2026-10-06T18:00:00Z',
      esMaestroDeJuego: false,
      vendedorId: null,
      esPropia: false,
    },
    {
      id: '8d1f0c2e-aaaa-bbbb-cccc-ddddeeeeffff',
      estado: 'ACTIVA',
      nombreProducto: 'Arco largo',
      tipoProducto: 'ARMA',
      rareza: null,
      precioInicial: 20,
      ofertaVigente: 30,
      precioCompraInmediata: null,
      cantidadPujas: 7,
      fechaFin: '2026-10-07T18:00:00Z',
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

/** `IndicadoresDeCuentas` de ms-identidad-admin.yaml 1.3.0 (HU-USR-008), tal cual. */
const INDICADORES = {
  total: 16,
  porEstado: { ACTIVO: 10, PENDIENTE_VERIFICACION: 2, INACTIVO: 0, SUSPENDIDO: 1, BANEADO: 3 },
  registros: {
    desde: '2026-09-06',
    hasta: '2026-10-05',
    total: 4,
    porDia: [
      { fecha: '2026-10-04', cuentas: 1 },
      { fecha: '2026-10-05', cuentas: 3 },
    ],
  },
  ocultarPruebas: true,
  calculadoEn: '2026-10-05T15:00:00-05:00',
};

const SERVICIOS = {
  total: 18,
  operativos: 12,
  caidos: 2,
  lentos: 0,
  noDesplegados: 3,
  noObservables: 1,
  instante: '2026-09-24T02:00:00Z',
  desdeCache: false,
  servicios: [
    { servicio: 'ms-identidad', estado: 'OPERATIVO', detalle: 'HTTP 200' },
    { servicio: 'ms-subastas', estado: 'CAIDO', detalle: 'Connection refused' },
    { servicio: 'ms-chatbot', estado: 'NO_DESPLEGADO', detalle: 'fuera del host' },
    { servicio: 'heroes', estado: 'NO_OBSERVABLE', detalle: 'en otro host' },
  ],
};

/** Un UUID a la vista es un dato que nadie puede leer (RFINAL-06). */
const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

/**
 * Lo que el informe de revisión prohibió en la consola: la ruta de la
 * consulta, el código HTTP y las etiquetas internas.
 */
const ANDAMIO =
  /Fuente:|GET \/api|\/api\/v1|HTTP\s?\d|NO IMPLEMENTADO|SERVICIO DEGRADADO|SIN PERMISO|EN LINEA|Sondeo real|pruebas automáticas/;

/** Un sistema medio caído, como el del entorno de demostración. */
function apiSimulada(sobrescribir = {}) {
  return jest.fn(async (recurso) => {
    for (const [prefijo, respuesta] of Object.entries(sobrescribir)) {
      if (recurso.startsWith(prefijo)) {
        return typeof respuesta === 'function' ? respuesta(recurso) : respuesta;
      }
    }
    if (recurso.startsWith('/admin/jugadores/indicadores')) {
      return conDatos(INDICADORES);
    }
    if (recurso.startsWith('/admin/jugadores')) {
      return conDatos(JUGADORES);
    }
    if (recurso.startsWith('/admin/sistema/servicios')) {
      return conDatos(SERVICIOS);
    }
    if (recurso.startsWith('/torneos')) {
      return conDatos([TORNEO]);
    }
    if (recurso.startsWith('/sanciones/metricas')) {
      return conDatos({ total: 0, apelaciones: { PENDIENTE: 0 } });
    }
    if (recurso.startsWith('/subastas')) {
      return conFallo(
        RESULTADO.SERVICIO_DEGRADADO,
        502,
        recurso,
        'No pudimos cargar esta información. Inténtalo de nuevo en unos segundos.',
      );
    }
    if (recurso.startsWith('/admin/auditoria')) {
      return conFallo(
        RESULTADO.SIN_PERMISO,
        403,
        recurso,
        'Tu rol no tiene acceso a esta información.',
      );
    }
    if (recurso.startsWith('/comentarios/moderacion')) {
      return conDatos({ entradas: [{ id: 1 }, { id: 2 }], total: 2, pagina: 0, tamano: 20 });
    }
    return conDatos([]);
  });
}

function pagina() {
  document.body.innerHTML = '<div data-zona="encabezado"></div><div data-zona="secciones"></div>';
  return document;
}

async function montar(opciones = {}, sesion = {}) {
  const consultarApi = opciones.consultarApi ?? apiSimulada();
  const raiz = pagina();
  const paneles = montarControlIntegral(raiz, sesion, {
    actualizarSola: false,
    ...opciones,
    consultarApi,
  });
  await asentar();
  return { raiz, consultarApi, paneles };
}

const celdas = (panel) =>
  [...panel.querySelectorAll('tbody tr:first-child > *')].map((c) => c.textContent.trim());
const encabezados = (panel) =>
  [...panel.querySelectorAll('thead th')].map((th) => th.textContent.trim());

describe('montaje', () => {
  test('pinta las ocho secciones del mandato', async () => {
    const { raiz } = await montar({}, { apodo: 'admin.simon', rol: 'SUPER_ADMINISTRADOR' });

    const pintadas = [...raiz.querySelectorAll('[data-seccion]')].map((s) => s.dataset.seccion);
    expect(pintadas).toEqual(SECCIONES.map((s) => s.id));
  });

  test('SA-2: ningún panel dice de qué ruta sale su dato ni lleva texto de andamio', async () => {
    const { raiz } = await montar();

    expect(raiz.querySelector('.panel-admin__fuente')).toBeNull();
    expect(raiz.body.textContent).not.toMatch(ANDAMIO);
  });

  test('SA-2: sin la tarjeta de misiones ni los movimientos de la propia cuenta', async () => {
    const { raiz, consultarApi } = await montar();

    expect(raiz.querySelector('[data-panel="misiones"]')).toBeNull();
    expect(raiz.querySelector('[data-panel="transacciones"]')).toBeNull();
    expect(consultarApi.mock.calls.map((c) => c[0])).not.toContain('/transacciones/mi-historial');
  });

  test('el encabezado dice el rol y la hora de la última actualización', async () => {
    const { raiz } = await montar({}, { apodo: 'admin.simon', rol: 'SUPER_ADMINISTRADOR' });

    const encabezado = raiz.querySelector('[data-zona="encabezado"]');
    expect(encabezado.querySelector('h1').textContent).toBe('Control integral');
    expect(encabezado.textContent).toContain('Super administrador');
    expect(encabezado.querySelector('[data-zona="marca"]').textContent).toMatch(
      /^Actualizado a las /,
    );
  });

  test('«Actualizar» vuelve a consultar todos los paneles menos el directorio', async () => {
    const { raiz, consultarApi } = await montar();
    const antes = consultarApi.mock.calls.length;

    raiz.querySelector('[data-accion="actualizar"]').dispatchEvent(new window.Event('click'));
    await asentar();

    const nuevas = consultarApi.mock.calls.slice(antes).map((c) => c[0]);
    expect(nuevas).toContain('/subastas');
    expect(nuevas).toContain('/admin/sistema/servicios');
    expect(nuevas.some((r) => r.startsWith('/admin/jugadores?page='))).toBe(true); // el KPI
    expect(
      nuevas.some((r) => r.startsWith(`/admin/jugadores?page=0&size=${FILAS_POR_PAGINA}`)),
    ).toBe(false);
  });
});

describe('un servicio caído no tumba la consola', () => {
  test('subastas caída lo dice con palabras y torneos sigue con datos', async () => {
    const { raiz } = await montar();

    const subastas = raiz.querySelector('[data-panel="subastas"]');
    expect(subastas.querySelector('.panel-admin__estado').textContent).toBe('Sin conexión');
    expect(subastas.querySelector('.estado-admin').dataset.tono).toBe('error');
    expect(subastas.querySelector('.aviso__titulo').textContent).toBe(
      'No pudimos cargar esta información',
    );
    expect(subastas.textContent).not.toMatch(ANDAMIO);

    const torneos = raiz.querySelector('[data-panel="torneos"]');
    expect(torneos.querySelector('.panel-admin__estado').textContent).toBe('');
    expect(torneos.textContent).toContain('Copa Nexo');
  });

  test('un panel degradado ofrece reintentar y vuelve a consultar solo lo suyo', async () => {
    const { raiz, consultarApi } = await montar();
    const llamadasAntes = consultarApi.mock.calls.length;

    raiz
      .querySelector('[data-panel="subastas"] [data-accion="reintentar"]')
      .dispatchEvent(new window.Event('click'));
    await asentar();

    expect(consultarApi.mock.calls.length).toBe(llamadasAntes + 1);
    expect(consultarApi.mock.calls.at(-1)[0]).toBe('/subastas');
  });

  test('sin permiso NO ofrece reintentar: no se arregla reintentando', async () => {
    const { raiz } = await montar();

    const auditoria = raiz.querySelector('[data-panel="auditoria"]');
    expect(auditoria.querySelector('.panel-admin__estado').textContent).toBe('Sin acceso');
    expect(auditoria.querySelector('[data-accion="reintentar"]')).toBeNull();
    expect(auditoria.textContent).toContain('Tu rol no tiene acceso a esta información.');
  });

  test('una consulta que no trae registros lo dice, sin distintivo de fallo', async () => {
    const { raiz } = await montar({
      consultarApi: apiSimulada({
        '/salas': {
          ...conDatos(null),
          resultado: RESULTADO.VACIO,
          motivo: 'Todavía no hay registros.',
        },
      }),
    });

    const salas = raiz.querySelector('[data-panel="salas"]');
    expect(salas.querySelector('.panel-admin__estado').textContent).toBe('');
    expect(salas.querySelector('.estado-vista--vacio').textContent).toContain(
      'No hay salas abiertas ahora mismo',
    );
  });
});

describe('nada se inventa', () => {
  test('un contador sin total se pinta como «sin dato», nunca como cero', async () => {
    const { raiz } = await montar({
      consultarApi: apiSimulada({ '/admin/jugadores?page=0&size=1': conDatos({ algo: 1 }) }),
    });

    const caja = raiz.querySelector('[data-panel="resumen-jugadores"] .indicador');
    expect(caja.dataset.sinDato).toBe('si');
    expect(caja.querySelector('.indicador__valor').textContent).toBe('—Sin dato');
  });

  test('un cero que el servicio dijo de verdad se pinta como cero', async () => {
    const { raiz } = await montar();

    const valor = raiz.querySelector('[data-panel="resumen-sanciones"] .indicador__valor');
    expect(valor.textContent).toBe('0');
  });

  test('los servicios operativos se dicen «12 de 18», con lo que falla en la nota', async () => {
    const { raiz } = await montar();

    const panel = raiz.querySelector('[data-panel="resumen-servicios"]');
    expect(panel.querySelector('.indicador__valor').textContent).toBe('12 de 18');
    expect(panel.querySelector('.indicador__nota').textContent).toBe('2 caídos');
  });
});

describe('directorio de jugadores', () => {
  const pedidasAlDirectorio = (consultarApi) =>
    consultarApi.mock.calls
      .map((c) => c[0])
      .filter((r) => r.startsWith(`/admin/jugadores?page=`) && !r.includes('size=1&'))
      .filter((r) => !r.endsWith('size=1'));

  test('lista las cuentas sin publicar ninguna credencial', async () => {
    const { raiz } = await montar();

    const directorio = raiz.querySelector('[data-panel="directorio"]');
    expect(directorio.textContent).toContain('ana@nexus.test');
    expect(directorio.textContent).not.toMatch(/password|hash|token|\$2a\$/i);
  });

  test('pide dieciséis cuentas por página (RNF-USA-001) sin las de prueba, con el filtro a la vista', async () => {
    const { raiz, consultarApi } = await montar();

    expect(pedidasAlDirectorio(consultarApi)).toEqual([
      '/admin/jugadores?page=0&size=16&ocultarPruebas=true',
    ]);
    const directorio = raiz.querySelector('[data-panel="directorio"]');
    expect(directorio.querySelector('select[name="cuentas"]').value).toBe('sin-pruebas');
    const fichas = [...directorio.querySelectorAll('.filtros-admin__ficha')].map((f) =>
      f.textContent.replace('×', '').trim(),
    );
    expect(fichas).toEqual(['Cuentas de prueba: Excluidas']);
    expect(directorio.querySelector('input[type="checkbox"][name="ocultarPruebas"]')).toBeNull();
  });

  test('quitar el filtro de cuentas de prueba las vuelve a pedir, desde la primera página', async () => {
    const { raiz, consultarApi } = await montar();
    const directorio = raiz.querySelector('[data-panel="directorio"]');

    directorio.querySelector('[data-quitar="cuentas"]').dispatchEvent(new window.Event('click'));
    await asentar();

    expect(pedidasAlDirectorio(consultarApi).at(-1)).toBe('/admin/jugadores?page=0&size=16');
    expect(directorio.querySelectorAll('.filtros-admin__ficha')).toHaveLength(0);
  });

  test('la búsqueda va primero y pide al servidor con el filtro, desde la primera página', async () => {
    const { raiz, consultarApi } = await montar();

    const directorio = raiz.querySelector('[data-panel="directorio"]');
    const formulario = directorio.querySelector('form.filtros-admin');
    expect(formulario.firstElementChild.querySelector('input[name="buscar"]')).not.toBeNull();
    expect(directorio.querySelector('input[name="buscar"]').getAttribute('placeholder')).toBe(
      'Apodo, correo o nombre',
    );
    directorio.querySelector('input[name="buscar"]').value = '  Ana  ';
    formulario.dispatchEvent(new window.Event('submit', { cancelable: true }));
    await asentar();

    expect(pedidasAlDirectorio(consultarApi).at(-1)).toBe(
      '/admin/jugadores?page=0&size=16&buscar=Ana&ocultarPruebas=true',
    );
  });

  test('la paginación común lleva a otra página con los mismos filtros', async () => {
    const { raiz, consultarApi } = await montar();

    const directorio = raiz.querySelector('[data-panel="directorio"]');
    const paginacion = directorio.querySelector('nav.paginacion');
    expect(paginacion.getAttribute('aria-label')).toBe('Páginas del directorio');
    expect(paginacion.querySelector('[aria-current="page"]').textContent).toBe('1');
    expect(paginacion.querySelector('[data-direccion="anterior"]').disabled).toBe(true);
    [...paginacion.querySelectorAll('.paginacion__pagina')]
      .find((b) => b.textContent === '2')
      .dispatchEvent(new window.Event('click'));
    await asentar();

    expect(pedidasAlDirectorio(consultarApi).at(-1)).toBe(
      '/admin/jugadores?page=1&size=16&ocultarPruebas=true',
    );
  });

  test('las columnas son las de la operación, con el estado dicho con palabras', async () => {
    const { raiz } = await montar();

    const directorio = raiz.querySelector('[data-panel="directorio"]');
    expect(encabezados(directorio)).toEqual([
      'Apodo',
      'Correo',
      'Rol',
      'Estado',
      'Registro',
      'Última entrada',
      'Acciones',
    ]);
    const estados = [...directorio.querySelectorAll('tbody .estado-admin')].map((e) =>
      e.textContent.trim(),
    );
    expect(estados).toEqual(['Activa', 'Bloqueada']);
    expect(directorio.querySelector('tbody th[scope="row"]').textContent).toBe('Ana');
    // La página la dice la paginación; el resumen, cuántas cuentas hay.
    expect(directorio.querySelector('.tabla-admin__resumen').textContent).toBe('42 cuentas');
    expect(directorio.querySelector('.paginacion').textContent).toContain('Página 1 de 3');
  });

  test('una cuenta que nunca entró lo dice, no finge una fecha', async () => {
    const { raiz } = await montar();

    expect(raiz.querySelector('[data-panel="directorio"]').textContent).toContain(
      'Nunca ha entrado',
    );
  });

  test('cada cuenta abre su ficha, su gestión y sus sanciones sin copiar identificadores', async () => {
    const { raiz } = await montar();

    const directorio = raiz.querySelector('[data-panel="directorio"]');
    const ficha = directorio.querySelector('tbody a[data-accion="ver-ficha"]');
    expect(new URL(ficha.href).pathname).toMatch(
      /plataforma\/moderacion-sanciones\/ficha-usuario\.html$/,
    );
    expect(new URL(ficha.href).searchParams.get('usuario')).toBe('15');
    expect(ficha.getAttribute('aria-label')).toBe('Ver la ficha de Ana');

    const gestion = directorio.querySelector('tbody a[data-accion="gestionar"]');
    expect(new URL(gestion.href).pathname).toMatch(/cuentas\/gestion-usuarios\.html$/);
    expect(new URL(gestion.href).searchParams.get('usuario')).toBe('15');
    expect(gestion.getAttribute('aria-label')).toBe('Gestionar la cuenta de Ana');

    const sanciones = directorio.querySelector('tbody a[data-accion="ver-sanciones"]');
    expect(new URL(sanciones.href).pathname).toMatch(
      /moderacion-sanciones\/sanciones-admin\.html$/,
    );
    expect(new URL(sanciones.href).searchParams.get('usuario')).toBe(
      '11111111-2222-3333-4444-555555555555',
    );
    expect(directorio.querySelector('tbody').textContent).not.toMatch(UUID);
  });

  test('sin clave la ficha se abre por el uid, y sin ninguno no hay enlace roto', async () => {
    const soloUid = {
      ...JUGADORES,
      contenido: [
        { ...JUGADORES.contenido[0], id: undefined },
        { ...JUGADORES.contenido[1], id: undefined, uid: null },
      ],
    };
    const { raiz } = await montar({
      consultarApi: apiSimulada({ '/admin/jugadores?page=0&size=16': conDatos(soloUid) }),
    });

    const directorio = raiz.querySelector('[data-panel="directorio"]');
    const fichas = [...directorio.querySelectorAll('tbody a[data-accion="ver-ficha"]')];
    expect(fichas).toHaveLength(1);
    expect(new URL(fichas[0].href).searchParams.get('usuario')).toBe(
      '11111111-2222-3333-4444-555555555555',
    );
    expect(directorio.querySelectorAll('a[data-accion="gestionar"]')).toHaveLength(0);
  });

  test('elegir un rol vuelve a pedir desde la primera página, con el filtro y su ficha', async () => {
    const { raiz, consultarApi } = await montar();
    const directorio = raiz.querySelector('[data-panel="directorio"]');

    const rol = directorio.querySelector('select[name="rol"]');
    rol.value = 'MODERADOR';
    rol.dispatchEvent(new window.Event('change'));
    await asentar();

    expect(pedidasAlDirectorio(consultarApi).at(-1)).toBe(
      '/admin/jugadores?page=0&size=16&rol=MODERADOR&ocultarPruebas=true',
    );
    expect(directorio.querySelector('[data-quitar="rol"]').textContent).toContain('Rol: Moderador');
  });

  test('estado y fechas de registro van juntos con la búsqueda', async () => {
    const { raiz, consultarApi } = await montar();
    const directorio = raiz.querySelector('[data-panel="directorio"]');

    directorio.querySelector('input[name="buscar"]').value = 'Ana';
    directorio.querySelector('select[name="estadoCuenta"]').value = 'SUSPENDIDO';
    directorio.querySelector('input[name="desde"]').value = '2026-09-01';
    directorio.querySelector('input[name="hasta"]').value = '2026-09-30';
    directorio
      .querySelector('form.filtros-admin')
      .dispatchEvent(new window.Event('submit', { cancelable: true }));
    await asentar();

    expect(pedidasAlDirectorio(consultarApi).at(-1)).toBe(
      '/admin/jugadores?page=0&size=16&buscar=Ana&estado=SUSPENDIDO' +
        '&registradoDesde=2026-09-01&registradoHasta=2026-09-30&ocultarPruebas=true',
    );
  });

  test('los estados del filtro son los del contrato, con su nombre en castellano', async () => {
    const { raiz } = await montar();

    const opciones = [
      ...raiz.querySelectorAll('[data-panel="directorio"] select[name="estadoCuenta"] option'),
    ].map((o) => [o.value, o.textContent]);
    expect(opciones).toEqual([
      ['', 'Todos'],
      ['ACTIVO', 'Activas'],
      ['PENDIENTE_VERIFICACION', 'Sin verificar el correo'],
      ['INACTIVO', 'Pendientes de activar'],
      ['SUSPENDIDO', 'Suspendidas'],
      ['BANEADO', 'Baneadas'],
    ]);
  });

  test('un periodo al revés no se pide: se explica', async () => {
    const { raiz, consultarApi } = await montar();
    const directorio = raiz.querySelector('[data-panel="directorio"]');
    const antes = pedidasAlDirectorio(consultarApi).length;

    directorio.querySelector('input[name="desde"]').value = '2026-10-05';
    directorio.querySelector('input[name="hasta"]').value = '2026-10-01';
    directorio
      .querySelector('form.filtros-admin')
      .dispatchEvent(new window.Event('submit', { cancelable: true }));
    await asentar();

    expect(pedidasAlDirectorio(consultarApi)).toHaveLength(antes);
    expect(directorio.querySelector('[data-zona="error-filtros"]').textContent).toMatch(
      /posterior/,
    );
  });

  test('«Exportar CSV» descarga del servidor con los filtros aplicados', async () => {
    const descargarApi = jest.fn(async () => ({
      resultado: RESULTADO.DATOS,
      contenido: 'csv',
      nombreArchivo: 'directorio-de-cuentas-20261005-1500.csv',
      estado: 200,
      motivo: '',
      recurso: '',
    }));
    const guardar = jest.fn();
    const { raiz } = await montar({ descargarApi, guardar });
    const directorio = raiz.querySelector('[data-panel="directorio"]');
    const rol = directorio.querySelector('select[name="rol"]');
    rol.value = 'JUGADOR';
    rol.dispatchEvent(new window.Event('change'));
    await asentar();

    directorio
      .querySelector('[data-accion="exportar-directorio"]')
      .dispatchEvent(new window.Event('click'));
    await asentar();

    expect(descargarApi).toHaveBeenCalledWith(
      '/admin/jugadores/exportacion?rol=JUGADOR&ocultarPruebas=true',
    );
    expect(guardar).toHaveBeenCalledWith(
      'directorio-de-cuentas-20261005-1500.csv',
      'csv',
      'text/csv;charset=utf-8',
    );
    expect(directorio.querySelector('[data-zona="aviso-directorio"]').textContent).toBe(
      'Listado descargado: directorio-de-cuentas-20261005-1500.csv.',
    );
  });

  test('si el servidor no exporta (demasiadas cuentas), se dice por qué y no se guarda nada', async () => {
    const descargarApi = jest.fn(async () => ({
      resultado: RESULTADO.NO_DISPONIBLE,
      contenido: null,
      nombreArchivo: null,
      estado: 422,
      motivo: 'Los filtros dejan 12000 cuentas y una exportación admite como máximo 10000.',
      recurso: '/admin/jugadores/exportacion',
    }));
    const guardar = jest.fn();
    const { raiz } = await montar({ descargarApi, guardar });
    const directorio = raiz.querySelector('[data-panel="directorio"]');

    const boton = directorio.querySelector('[data-accion="exportar-directorio"]');
    boton.dispatchEvent(new window.Event('click'));
    await asentar();

    expect(guardar).not.toHaveBeenCalled();
    expect(boton.disabled).toBe(false);
    expect(directorio.querySelector('[data-zona="aviso-directorio"]').textContent).toContain(
      '12000 cuentas',
    );
  });

  test('ninguna cuenta coincide: lo dice y propone qué hacer', async () => {
    const { raiz } = await montar({
      consultarApi: apiSimulada({
        '/admin/jugadores?page=0&size=16': {
          ...conDatos({ contenido: [], total: 0 }),
          resultado: RESULTADO.VACIO,
        },
      }),
    });

    expect(
      raiz.querySelector('[data-panel="directorio"] .estado-vista--vacio').textContent,
    ).toContain('Ninguna cuenta coincide');
  });
});

/**
 * HU-USR-008 (#561), §7.3.4 — «Panel: vista general»: activas, suspendidas,
 * baneadas y los demás estados, registros por día con su gráfico, periodo y
 * exportación, en el Resumen.
 */
describe('HU-USR-008 — usuarios de la comunidad en el Resumen', () => {
  test('pinta las cuentas por estado y el gráfico de registros, sin las de prueba', async () => {
    const { raiz, consultarApi } = await montar();

    const panelUsuarios = raiz.querySelector(
      '[data-seccion="resumen"] [data-panel="resumen-usuarios"]',
    );
    expect(consultarApi.mock.calls.map((c) => c[0])).toContain(
      '/admin/jugadores/indicadores?ocultarPruebas=true',
    );
    const valor = (etiqueta) =>
      panelUsuarios.querySelector(`[data-indicador="${etiqueta}"] .indicador-admin__valor`)
        .textContent;
    expect(valor('Activas')).toBe('10');
    expect(valor('Suspendidas')).toBe('1');
    expect(valor('Baneadas')).toBe('3');
    expect(panelUsuarios.querySelectorAll('.grafico-admin__columna')).toHaveLength(2);
    expect(panelUsuarios.textContent).not.toMatch(ANDAMIO);
    expect(panelUsuarios.textContent).not.toContain('Contado a las');
  });

  test('CA-03: si los indicadores fallan, lo dice ese panel y el resto del tablero sigue', async () => {
    const { raiz } = await montar({
      consultarApi: apiSimulada({
        '/admin/jugadores/indicadores': conFallo(
          RESULTADO.SERVICIO_DEGRADADO,
          503,
          '/admin/jugadores/indicadores?ocultarPruebas=true',
        ),
      }),
    });

    const panelUsuarios = raiz.querySelector('[data-panel="resumen-usuarios"]');
    expect(panelUsuarios.querySelector('.panel-admin__estado').textContent).toBe('Sin conexión');
    expect(panelUsuarios.querySelector('[data-accion="exportar-indicadores"]').disabled).toBe(true);
    expect(raiz.querySelector('[data-panel="torneos"]').textContent).toContain('Copa Nexo');
    expect(raiz.querySelector('[data-panel="directorio"]').textContent).toContain('ana@nexus.test');
  });

  test('CA-02: «Exportar CSV» guarda en el navegador lo que se está viendo', async () => {
    const guardar = jest.fn();
    const { raiz } = await montar({ guardar });
    raiz
      .querySelector('[data-panel="resumen-usuarios"] [data-accion="exportar-indicadores"]')
      .dispatchEvent(new window.Event('click'));

    expect(guardar).toHaveBeenCalledTimes(1);
    const [nombre, contenido] = guardar.mock.calls[0];
    expect(nombre).toBe('indicadores-de-usuarios-2026-09-06-a-2026-10-05.csv');
    expect(contenido).toContain('Suspendidas,1');
    expect(contenido).toContain('2026-10-05,3');
  });
});

describe('sistema', () => {
  test('cada estado con palabras de producto e icono, lo que falla primero', async () => {
    const { raiz } = await montar();

    const sistema = raiz.querySelector('[data-panel="sistema"]');
    const filas = [...sistema.querySelectorAll('tbody tr')].map((tr) => [
      tr.querySelector('th').textContent,
      tr.querySelector('.estado-admin').textContent.trim(),
    ]);
    expect(filas).toEqual([
      ['Subastas', 'Caído'],
      ['Identidad y cuentas', 'Operativo'],
      ['Héroes', 'En otro servidor'],
      ['Asistente', 'Fuera de este entorno'],
    ]);
    expect(sistema.querySelector('.tabla-admin__resumen').textContent).toBe(
      '12 operativos · 2 caídos · 3 fuera de este entorno · 1 en otro servidor — de 18 servicios',
    );
    expect(sistema.textContent).not.toMatch(/Connection refused|HTTP 200/);
  });

  /** Un servicio de otro host no puede salir en rojo: no está roto. */
  test('lo que vive en otro servidor no se pinta como caído', async () => {
    const { raiz } = await montar();

    const estados = [...raiz.querySelectorAll('[data-panel="sistema"] tbody .estado-admin')];
    expect(estados.find((e) => e.dataset.estado === 'CAIDO').dataset.tono).toBe('error');
    expect(estados.find((e) => e.dataset.estado === 'NO_OBSERVABLE').dataset.tono).toBe('neutro');
  });

  /**
   * RFINAL-08: el servicio que conecta y no contesta a tiempo no está caído ni
   * operativo. Va en ámbar, con su palabra, y se cuenta aparte.
   */
  test('un servicio lento va en ámbar y se cuenta aparte', () => {
    const caja = document.createElement('div');
    caja.append(
      ...pintarServicios({
        total: 3,
        operativos: 1,
        caidos: 1,
        lentos: 1,
        noDesplegados: 0,
        noObservables: 0,
        servicios: [
          { servicio: 'torneos', estado: 'OPERATIVO', detalle: '' },
          { servicio: 'misiones', estado: 'LENTO', detalle: 'sin respuesta en 1500 ms' },
          { servicio: 'correo', estado: 'CAIDO', detalle: 'conexión rechazada' },
        ],
      }),
    );

    const lento = [...caja.querySelectorAll('.estado-admin')].find(
      (e) => e.dataset.estado === 'LENTO',
    );
    expect(lento.dataset.tono).toBe('advertencia');
    expect(lento.textContent.trim()).toBe('Lento');
    expect(caja.textContent).toContain('Responde, pero tarda más de lo esperado.');
    expect(caja.querySelector('.tabla-admin__resumen').textContent).toBe(
      '1 operativos · 1 lentos · 1 caídos — de 3 servicios',
    );
  });

  test('los parámetros se leen con su nombre y su unidad, no con la clave técnica', async () => {
    const { raiz } = await montar({
      consultarApi: apiSimulada({
        '/parametros': conDatos([
          {
            clave: 'sanciones.apelacion.plazo-dias',
            descripcion: 'Dias desde la sancion para poder apelarla',
            valor: '30',
            unidad: 'dias',
            version: 2,
            actualizadoEn: '2026-10-01T10:00:00Z',
          },
          {
            clave: 'subastas.incremento-minimo',
            descripcion: 'Incremento minimo entre pujas',
            valor: null,
            unidad: 'creditos',
            version: 1,
            actualizadoEn: null,
          },
        ]),
      }),
    });

    const parametros = raiz.querySelector('[data-panel="parametros"]');
    expect(encabezados(parametros)).toEqual([
      'Parámetro',
      'Valor vigente',
      'Área',
      'Último cambio',
    ]);
    expect(celdas(parametros).slice(0, 3)).toEqual([
      'Plazo para apelar',
      '30 días',
      'Sanciones y moderación',
    ]);
    expect(parametros.textContent).toContain('Sin definir');
    expect(parametros.textContent).not.toContain('sanciones.apelacion.plazo-dias');
  });
});

describe('las consultas son las que el sistema atiende de verdad', () => {
  test('la auditoría se lee de /admin/auditoria, no de la ruta de escritura', async () => {
    const { consultarApi } = await montar();

    const pedidas = consultarApi.mock.calls.map((c) => c[0]);
    expect(pedidas.some((r) => r.startsWith('/admin/auditoria?'))).toBe(true);
    expect(pedidas.some((r) => r.startsWith('/admin/auditoria/eventos'))).toBe(false);
  });

  /**
   * RFINAL-06: sin `sort` «los últimos quince» eran los primeros que se
   * registraron.
   */
  test('la bitácora pide los quince más recientes, con desempate estable', async () => {
    const { consultarApi } = await montar();

    const pedida = consultarApi.mock.calls
      .map((c) => c[0])
      .find((r) => r.startsWith('/admin/auditoria?'));
    const consulta = new URLSearchParams(pedida.split('?')[1]);
    expect(consulta.get('page')).toBe('0');
    expect(consulta.get('size')).toBe('15');
    expect(consulta.getAll('sort')).toEqual(['fechaHora,desc', 'id,desc']);
  });

  test('la bitácora nombra cada acción como la diría quien opera', async () => {
    const { raiz } = await montar({
      consultarApi: apiSimulada({
        '/admin/auditoria': conDatos({
          content: [
            {
              id: 1,
              fechaHora: '2026-10-12T10:00:00Z',
              administrador: 'simon_superadmin',
              tipoAccion: 'CAMBIO_ROL',
              afectado: 'ana',
              motivo: 'Refuerzo del equipo',
            },
          ],
        }),
      }),
    });

    const auditoria = raiz.querySelector('[data-panel="auditoria"]');
    expect(celdas(auditoria).slice(1, 3)).toEqual(['simon_superadmin', 'Cambio de rol']);
    expect(auditoria.textContent).not.toContain('CAMBIO_ROL');
  });

  test('la cola de moderación cuenta bien aunque llame a su lista «entradas»', async () => {
    const { raiz } = await montar();

    const comentarios = raiz.querySelector('[data-panel="comentarios"]');
    expect(comentarios.querySelector('.indicador__valor').textContent).toBe('2');
    expect(comentarios.querySelector('.indicador__etiqueta').textContent).toBe('por revisar');
  });

  test('sanciones enseña el recuento que publica el servicio, con nombres de producto', async () => {
    const { raiz } = await montar({
      consultarApi: apiSimulada({
        '/sanciones/metricas': conDatos({
          total: 3,
          porTipo: { ADVERTENCIA: 2, SUSPENSION: 1, BANEO: 0 },
          apelaciones: { PENDIENTE: 1 },
          moderadoresActivos: 2,
        }),
      }),
    });

    const sanciones = raiz.querySelector('[data-panel="sanciones"]');
    const cifras = (grupo) =>
      [...grupo.querySelectorAll('.indicador-admin')].map((c) => [
        c.querySelector('.indicador-admin__etiqueta').textContent,
        c.querySelector('.indicador-admin__valor').textContent,
      ]);
    const [deSanciones, deApelaciones] = sanciones.querySelectorAll('.rejilla-indicadores-admin');
    expect(
      [...sanciones.querySelectorAll('.panel-admin__grupo')].map((g) => g.textContent),
    ).toEqual(['Sanciones', 'Apelaciones']);
    expect(cifras(deSanciones)).toEqual([
      ['Sanciones en total', '3'],
      ['Advertencias', '2'],
      ['Suspensiones', '1'],
      ['Baneos definitivos', '0'],
      ['Moderadores que sancionaron', '2'],
    ]);
    expect(cifras(deApelaciones)).toEqual([['Apelaciones pendientes', '1']]);
    // Una apelación pendiente es trabajo por hacer: se dice con tono e icono.
    expect(deApelaciones.querySelector('.indicador-admin').dataset.tono).toBe('advertencia');
  });

  test('sin dato no hay cero inventado, y sin apelaciones no hay grupo vacío', () => {
    const partes = pintarMetricasDeSanciones({ porTipo: { OTRA_COSA: 1 } });
    const contenedor = document.createElement('div');
    contenedor.append(...partes);

    expect(
      [...contenedor.querySelectorAll('.panel-admin__grupo')].map((g) => g.textContent),
    ).toEqual(['Sanciones']);
    const etiquetas = [...contenedor.querySelectorAll('.indicador-admin__etiqueta')].map(
      (e) => e.textContent,
    );
    expect(etiquetas).toEqual(['Sanciones en total', 'Otra cosa', 'Moderadores que sancionaron']);
    expect(contenedor.querySelectorAll('[data-sin-dato="si"]')).toHaveLength(2);
  });
});

/**
 * RFINAL-06 — los paneles leen los campos del contrato de cada fuente, y lo
 * que el contrato no trae se dice («Sin dato»), no se rellena con un guion ni
 * con un identificador.
 */
describe('cada panel lee los campos de su contrato', () => {
  test('subastas: el producto, la puja más alta, las pujas y el cierre; nunca el id', async () => {
    const { raiz } = await montar({
      consultarApi: apiSimulada({ '/subastas': conDatos(SUBASTAS) }),
    });
    const panel = raiz.querySelector('[data-panel="subastas"]');

    expect(encabezados(panel)).toEqual(['Elemento', 'Puja más alta', 'Pujas', 'Cierra']);
    const fila = celdas(panel);
    expect(fila.slice(0, 3)).toEqual(['Espada corta', '1.250 créditos', '2']);
    expect(fila[3]).toBe(formatearFecha('2026-10-06T18:00:00Z'));
    expect(panel.textContent).not.toMatch(UUID);
    expect(panel.querySelector('.tabla-admin__resumen').textContent).toBe(
      '2 de 13 subastas activas',
    );
  });

  test('subastas: se ordenan por columna, con aria-sort y aviso al lector de pantalla', async () => {
    const { raiz } = await montar({
      consultarApi: apiSimulada({ '/subastas': conDatos(SUBASTAS) }),
    });
    const panel = raiz.querySelector('[data-panel="subastas"]');

    const pujas = panel.querySelector('[data-orden="cantidadPujas"]');
    pujas.dispatchEvent(new window.Event('click'));
    expect(pujas.closest('th').getAttribute('aria-sort')).toBe('ascending');
    expect(celdas(panel)[0]).toBe('Espada corta');
    pujas.dispatchEvent(new window.Event('click'));
    expect(pujas.closest('th').getAttribute('aria-sort')).toBe('descending');
    expect(celdas(panel)[0]).toBe('Arco largo');
    expect(panel.querySelector('[role="status"].solo-lectores').textContent).toBe(
      'Ordenado por Pujas, de mayor a menor.',
    );
  });

  test('torneos: estado con palabras, equipos de los cupos y el cierre de inscripciones', async () => {
    const { raiz } = await montar();
    const panel = raiz.querySelector('[data-panel="torneos"]');

    expect(encabezados(panel)).toEqual(['Torneo', 'Estado', 'Equipos', 'Inscripciones hasta']);
    const fila = celdas(panel);
    expect(fila.slice(0, 3)).toEqual(['Copa Nexo', 'Inscripciones abiertas', '3 de 8']);
    expect(fila[3]).toBe(formatearFecha('2026-10-31T23:00:00Z'));
    expect(panel.textContent).not.toMatch(UUID);
  });

  test('salas: modalidad, estado y ocupación; ninguna columna sin dato fijo', async () => {
    const { raiz } = await montar({ consultarApi: apiSimulada({ '/salas': conDatos(SALAS) }) });
    const panel = raiz.querySelector('[data-panel="salas"]');

    expect(encabezados(panel)).toEqual(['Sala', 'Estado', 'Jugadores', 'Creada']);
    const fila = celdas(panel);
    expect(fila.slice(0, 3)).toEqual(['1 contra 1 · privada', 'Abierta', '1 de 2']);
    expect(fila[3]).toBe(formatearFecha('2026-10-04T15:00:00Z'));
    expect(panel.textContent).not.toMatch(UUID);
  });

  test('un campo que la respuesta no trae se dice «Sin dato», no «--»', async () => {
    const { raiz } = await montar({
      consultarApi: apiSimulada({
        '/subastas': conDatos({ contenido: [{ id: SUBASTAS.contenido[0].id }] }),
        '/torneos': conDatos([{ id: TORNEO.id }]),
      }),
    });

    for (const id of ['subastas', 'torneos']) {
      const fila = celdas(raiz.querySelector(`[data-panel="${id}"]`));
      expect(fila).not.toContain('--');
      expect(fila.join(' ')).not.toMatch(UUID);
      expect(fila).toContain('Sin dato');
    }
  });
});

describe('maquetado del aviso', () => {
  /**
   * `.aviso` del kit es una fila: [cuerpo][accion]. Si el título y el motivo
   * cuelgan como hermanos sueltos salen como columnas estrujadas.
   */
  test('el título y el motivo van dentro de aviso__cuerpo, sin detalle técnico', async () => {
    const { raiz } = await montar();

    const aviso = raiz.querySelector('[data-panel="subastas"] .aviso');
    expect(aviso.children.length).toBe(2);
    const cuerpo = aviso.querySelector('.aviso__cuerpo');
    expect(cuerpo.querySelector('.aviso__titulo').textContent).toBe(
      'No pudimos cargar esta información',
    );
    expect(cuerpo.querySelector('.aviso__detalle')).toBeNull();
    expect(aviso.lastElementChild.dataset.accion).toBe('reintentar');
  });

  test('sin permiso el aviso lleva solo el cuerpo', async () => {
    const { raiz } = await montar();

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

  test('el panel pinta las alertas del servicio de métricas', async () => {
    const { raiz, consultarApi } = await montar({
      consultarApi: apiSimulada({
        '/moderacion': conDatos({
          sanciones: {},
          alertasConfiguradas: true,
          umbralSancionesPorDia: 10,
          alertas: ['2026-09-20: 14 sanciones (umbral 10)'],
          pendientes: [],
        }),
      }),
    });

    expect(consultarApi.mock.calls.map((c) => c[0])).toContain('/moderacion');
    const panelDeAlertas = raiz.querySelector('[data-panel="alertas-moderacion"]');
    expect(panelDeAlertas.querySelector('[data-zona="alertas"]').textContent).toContain(
      '2026-09-20: 14 sanciones (umbral 10)',
    );
    // Cuántas, de un vistazo y no solo con color.
    const resumen = panelDeAlertas.querySelector('.estado-admin');
    expect(resumen.textContent).toBe('1 alerta');
    expect(resumen.dataset.tono).toBe('error');
  });

  test('sin umbral configurado lo dice, en vez de un «sin alertas» que no se evaluó', () => {
    const caja = contenedor(
      pintarAlertasDeModeracion({ alertasConfiguradas: false, alertas: [], pendientes: [] }),
    );
    expect(caja.querySelector('.estado-admin').textContent).toBe('Desactivadas');
    expect(caja.querySelector('[data-zona="sin-umbral"]').textContent).toMatch(
      /alertas están desactivadas/,
    );
    expect(caja.querySelector('[data-zona="sin-alertas"]')).toBeNull();
  });

  test('con umbral y sin días por encima, «sin alertas» con el umbral; las notas internas no se pintan', () => {
    const caja = contenedor(
      pintarAlertasDeModeracion({
        alertasConfiguradas: true,
        umbralSancionesPorDia: 10,
        alertas: [],
        pendientes: ['registro de usuarios sin contrato de lectura'],
      }),
    );
    expect(caja.querySelector('.estado-admin').textContent).toBe('Sin alertas');
    expect(caja.querySelector('.estado-admin').dataset.tono).toBe('exito');
    expect(caja.querySelector('[data-zona="sin-alertas"]').textContent).toBe(
      'Ningún día llegó a 10 sanciones.',
    );
    expect(caja.textContent).not.toContain('contrato');
  });
});

describe('formatearFecha', () => {
  test('nulo es un guion largo, no la fecha de hoy', () => {
    expect(formatearFecha(null)).toBe('—');
    expect(formatearFecha('')).toBe('—');
  });

  test('lo que no es fecha se devuelve tal cual, sin inventar', () => {
    expect(formatearFecha('no soy una fecha')).toBe('no soy una fecha');
  });
});

/**
 * SA-2 — la disposición: las cifras seguidas comparten fila y cada tabla va
 * en la suya, declarado al armar el panel y no deducido de lo que se pinte.
 */
describe('disposición de los paneles', () => {
  const panelDe = (id, ancho = null) => {
    const elemento = document.createElement('section');
    elemento.dataset.panel = id;
    if (ancho) {
      elemento.dataset.ancho = ancho;
    }
    return elemento;
  };

  test('agruparPaneles junta las cifras seguidas y deja cada panel ancho en su fila', () => {
    const salida = agruparPaneles([
      panelDe('a'),
      panelDe('b', 'completo'),
      panelDe('c'),
      panelDe('d'),
    ]);

    expect(salida.map((e) => e.className || e.dataset.panel)).toEqual([
      'consola-integral__cifras',
      'b',
      'consola-integral__cifras',
    ]);
    expect(salida[0].dataset.cifras).toBe('1');
    expect(salida[2].dataset.cifras).toBe('2');
    expect([...salida[2].children].map((e) => e.dataset.panel)).toEqual(['c', 'd']);
    expect(agruparPaneles([])).toEqual([]);
  });

  test('el resumen: cuatro cifras en una fila y los usuarios debajo, de lado a lado', async () => {
    const { raiz } = await montar();

    const rejilla = raiz.querySelector('[data-seccion="resumen"] .consola-integral__rejilla');
    const [cifras, usuarios] = rejilla.children;
    expect(cifras.dataset.cifras).toBe('4');
    expect([...cifras.children].map((p) => p.dataset.panel)).toEqual([
      'resumen-jugadores',
      'resumen-servicios',
      'resumen-sanciones',
      'resumen-torneos',
    ]);
    expect(usuarios.dataset.panel).toBe('resumen-usuarios');
    expect(usuarios.dataset.ancho).toBe('completo');
  });

  test('moderación: las dos cifras juntas arriba, la bitácora y el recuento en su fila', async () => {
    const { raiz } = await montar();

    const rejilla = raiz.querySelector('[data-seccion="moderacion"] .consola-integral__rejilla');
    const orden = [...rejilla.children].map((e) =>
      e.dataset.cifras ? [...e.children].map((p) => p.dataset.panel) : e.dataset.panel,
    );
    expect(orden).toEqual([['alertas-moderacion', 'comentarios'], 'auditoria', 'sanciones']);
  });

  test('las tablas ocupan la fila entera desde que se arman, también mientras cargan', () => {
    const raiz = document.createElement('div');
    raiz.innerHTML = '<div data-zona="secciones"></div>';
    montarControlIntegral(
      raiz,
      { rol: 'SUPER_ADMINISTRADOR' },
      {
        consultarApi: () => new Promise(() => {}),
        actualizarSola: false,
      },
    );

    const anchos = [...raiz.querySelectorAll('[data-ancho="completo"]')].map(
      (p) => p.dataset.panel,
    );
    expect(anchos).toEqual([
      'resumen-usuarios',
      'directorio',
      'salas',
      'subastas',
      'torneos',
      'auditoria',
      'sanciones',
      'sistema',
      'parametros',
    ]);
  });
});

describe('formatearDia', () => {
  test('el día sin la hora; sin valor, un guion; lo ilegible, tal cual', () => {
    const dia = formatearDia('2026-09-10T15:00:00Z');
    expect(dia).toMatch(/2026/);
    expect(dia).not.toMatch(/:/);
    expect(formatearDia(null)).toBe('—');
    expect(formatearDia('no es fecha')).toBe('no es fecha');
  });
});
