/**
 * Parámetros de plataforma — HU-ADM-001 con el kit de la consola (SA-8,
 * punto 22 del informe de revisión): presentación pura y montaje contra un
 * servicio simulado.
 */

import { jest } from '@jest/globals';

import { RESULTADO } from '../consola/cliente-consola.js';
import {
  ESTADOS_DE_PARAMETRO,
  ErrorDeParametros,
  estadoDe,
  filtrarCatalogo,
  montarParametros,
  porArea,
  rangoLegible,
  resumenDelCambio,
  textoDelRechazo,
  validarValor,
} from './parametros.js';

const UID_ADMIN = 'a1a1a1a1-0000-4000-8000-000000000001';

const asentar = async () => {
  for (let i = 0; i < 6; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
};

const parametro = (extra = {}) => ({
  clave: 'sanciones.suspension.maxima-dias',
  descripcion: 'Duracion maxima de una suspension temporal',
  tipo: 'ENTERO',
  valor: '30',
  unidad: 'dias',
  minimo: 1,
  maximo: 365,
  opciones: null,
  inalterable: false,
  origen: 'D-20 / HU-USR-005',
  version: 1,
  actualizadoPor: null,
  actualizadoEn: null,
  valorProgramado: null,
  vigenteDesde: null,
  ...extra,
});

const CATALOGO = [
  parametro({
    version: 2,
    actualizadoPor: UID_ADMIN,
    actualizadoEn: '2026-10-05T15:00:00Z',
  }),
  parametro({
    clave: 'metricas.umbral-reportes-por-dia',
    descripcion: 'Reportes en un dia; vacio = sin alerta',
    valor: null,
    unidad: 'reportes/dia',
    minimo: 1,
    maximo: 10000,
    origen: 'D-25 / HU-MET-001',
  }),
  parametro({
    clave: 'chat.historial.tamano',
    descripcion: 'Mensajes del historial',
    valor: '50',
    unidad: 'mensajes',
    minimo: 1,
    maximo: 500,
    valorProgramado: '100',
    vigenteDesde: '2026-10-20T17:00:00Z',
    origen: 'D-16 / HU-JUE-015',
  }),
  parametro({
    clave: 'torneos.cupos',
    descripcion: 'Equipos por torneo',
    valor: '8',
    unidad: 'equipos',
    minimo: null,
    maximo: null,
    inalterable: true,
    origen: 'Charter / RF-TOR-004',
  }),
  parametro({
    clave: 'salas.apuestas.si-gana-la-maquina',
    descripcion: 'Que pasa con la apuesta',
    tipo: 'TEXTO',
    valor: 'LIBERAR',
    unidad: null,
    minimo: null,
    maximo: null,
    opciones: ['LIBERAR', 'CONSUMIR'],
    origen: 'D-02 / HU-JUE-014',
  }),
];

function servicio(rutas) {
  return jest.fn(async (url, opciones = {}) => {
    const metodo = opciones.method ?? 'GET';
    const clave = `${metodo} ${new URL(url, 'http://x').pathname}`;
    const respuesta = rutas[clave];
    if (!respuesta) {
      throw new Error(`sin ruta simulada para ${clave}`);
    }
    const { estado = 200, cuerpo = null } =
      typeof respuesta === 'function' ? respuesta(opciones) : respuesta;
    return { ok: estado >= 200 && estado < 300, status: estado, json: async () => cuerpo };
  });
}

/** Un panel lateral de mentira: lo pinta en el body y deja cerrarlo. */
function panelDePrueba() {
  return jest.fn(({ titulo, contenido, acciones = [] }) => {
    const caja = document.createElement('div');
    caja.dataset.panelDePrueba = titulo;
    caja.append(...[contenido].flat(), ...acciones);
    document.body.append(caja);
    return { elemento: caja, cerrar: jest.fn(() => caja.remove()) };
  });
}

function montar({ rol = 'SUPER_ADMINISTRADOR', rutas = {} } = {}) {
  document.body.innerHTML = `
    <main>
      <div data-zona="encabezado"></div>
      <div data-zona="aviso" hidden></div>
      <div data-zona="indicadores"></div>
      <div data-zona="catalogo"></div>
    </main>`;
  const fetchImpl = servicio({ 'GET /api/v1/parametros': { cuerpo: CATALOGO }, ...rutas });
  const consultarApi = jest.fn(async (recurso) =>
    recurso.startsWith('/admin/titulares')
      ? {
          resultado: RESULTADO.DATOS,
          datos: [{ uid: UID_ADMIN, apodo: 'admin_qa', estado: 'ACTIVO' }],
        }
      : { resultado: RESULTADO.SERVICIO_DEGRADADO },
  );
  const abrirPanel = panelDePrueba();
  const vista = montarParametros(document, { rol, fetchImpl, consultarApi, abrirPanel });
  return { fetchImpl, consultarApi, abrirPanel, vista };
}

const fila = (clave) => document.querySelector(`[data-fila="${clave}"]`);
const panelAbierto = () => document.querySelector('[data-panel-de-prueba]');

function escribir(control, valor) {
  control.value = valor;
  control.dispatchEvent(new Event('input', { bubbles: true }));
}

describe('presentación', () => {
  test('el estado de cada parámetro: fijo, programado, sin definir o editable', () => {
    expect(CATALOGO.map(estadoDe)).toEqual([
      ESTADOS_DE_PARAMETRO.EDITABLE,
      ESTADOS_DE_PARAMETRO.SIN_DEFINIR,
      ESTADOS_DE_PARAMETRO.PROGRAMADO,
      ESTADOS_DE_PARAMETRO.FIJO,
      ESTADOS_DE_PARAMETRO.EDITABLE,
    ]);
  });

  test('lo que admite cada uno se dice en palabras, sin referencias internas', () => {
    expect(rangoLegible(parametro())).toBe('Entre 1 y 365 días');
    expect(rangoLegible(CATALOGO[4])).toBe('Se devuelven · Se pierden');
    expect(rangoLegible(parametro({ tipo: 'BOOLEANO', minimo: null, maximo: null }))).toBe(
      'Sí · No',
    );
    expect(rangoLegible(CATALOGO[3])).toBe('Valor fijo');
    expect(rangoLegible(parametro({ maximo: null, minimo: 1, unidad: 'horas' }))).toBe(
      'Desde 1 hora',
    );
    expect(
      rangoLegible(
        parametro({ tipo: 'DECIMAL', minimo: 0.01, maximo: 1000000, unidad: 'creditos' }),
      ),
    ).toBe('Entre 0,01 y 1.000.000 créditos');
    for (const p of CATALOGO) {
      expect(rangoLegible(p)).not.toMatch(/Charter|RNF|RF-|HU-|D-\d|PO\b|origen/);
    }
  });

  test('el valor nuevo se valida con mensajes de producto', () => {
    expect(validarValor(parametro(), ' 45 ')).toEqual({ valor: '45', error: null });
    expect(validarValor(parametro(), '4.5').error).toBe('Escribe un número entero.');
    expect(validarValor(parametro(), '999').error).toBe(
      'Tiene que estar en el rango admitido: Entre 1 y 365 días.',
    );
    expect(validarValor(parametro(), '').error).toBe('Escribe el valor nuevo.');
    // Los que se pueden dejar sin valor (lo dice su ficha) aceptan el vacío.
    expect(validarValor(CATALOGO[1], '')).toEqual({ valor: null, error: null });
    expect(validarValor(parametro({ tipo: 'DECIMAL', maximo: 10 }), '2,5')).toEqual({
      valor: '2.5',
      error: null,
    });
    expect(validarValor(CATALOGO[4], 'CONSUMIR')).toEqual({ valor: 'CONSUMIR', error: null });
    expect(validarValor(CATALOGO[4], 'OTRA').error).toMatch(/Elige una de las opciones/);
  });

  test('la frase de lo que va a pasar, antes de guardar', () => {
    expect(resumenDelCambio(parametro(), '45')).toBe('Pasará de 30 días a 45 días, de inmediato.');
    expect(resumenDelCambio(parametro(), '1')).toBe('Pasará de 30 días a 1 día, de inmediato.');
    expect(resumenDelCambio(CATALOGO[1], '20')).toBe(
      'Pasará de no tener valor a 20 reportes por día, de inmediato.',
    );
    expect(resumenDelCambio(parametro(), '45', new Date('2026-12-01T15:00:00Z'))).toMatch(
      /^Pasará de 30 días a 45 días, a partir del /,
    );
  });

  test('buscar, filtrar por área y por estado; las áreas en su orden', () => {
    expect(filtrarCatalogo(CATALOGO, { buscar: 'suspensión' }).map((p) => p.clave)).toEqual([
      'sanciones.suspension.maxima-dias',
    ]);
    expect(filtrarCatalogo(CATALOGO, { area: 'Torneos' }).map((p) => p.clave)).toEqual([
      'torneos.cupos',
    ]);
    expect(filtrarCatalogo(CATALOGO, { estado: 'sin-definir' }).map((p) => p.clave)).toEqual([
      'metricas.umbral-reportes-por-dia',
    ]);
    expect(porArea(CATALOGO).map(([area]) => area)).toEqual([
      'Sanciones y moderación',
      'Partidas',
      'Torneos',
      'Chat',
    ]);
  });

  test('un rechazo se cuenta por su motivo, sin la clave técnica del servidor', () => {
    const rechazo = (motivo) =>
      new ErrorDeParametros(
        { motivo, detail: 'sanciones.suspension.maxima-dias no puede ser mayor que 365' },
        400,
      );
    expect(textoDelRechazo(rechazo('VALOR_INVALIDO'), parametro())).toBe(
      'Ese valor no está en el rango admitido: Entre 1 y 365 días.',
    );
    expect(textoDelRechazo(rechazo('INALTERABLE'), parametro())).toMatch(/valor fijo/);
    expect(textoDelRechazo(rechazo('PERMISO_INSUFICIENTE'), parametro())).toMatch(/no puede/);
    expect(textoDelRechazo(new Error('red'), parametro())).toMatch(/No pudimos guardar/);
    for (const motivo of ['VALOR_INVALIDO', 'INALTERABLE', 'SOLICITUD_INVALIDA', 'NO_ENCONTRADO']) {
      expect(textoDelRechazo(rechazo(motivo), parametro())).not.toContain('sanciones.');
    }
  });
});

describe('la vista', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  test('encabezado, cifras y una tabla por área, sin textos de trabajo', async () => {
    const { consultarApi } = montar();
    await asentar();

    expect(document.querySelector('h1').textContent).toBe('Parámetros de plataforma');
    const cifra = (etiqueta) => document.querySelector(`[data-indicador="${etiqueta}"]`);
    expect(cifra('Parámetros').textContent).toContain('5');
    expect(cifra('Se pueden cambiar').textContent).toContain('4');
    expect(cifra('Se pueden cambiar').textContent).toContain('1 con valor fijo');
    expect(cifra('Sin definir').textContent).toContain('1');
    expect(cifra('Cambios programados').textContent).toContain('El próximo, el');

    expect([...document.querySelectorAll('[data-area] h4')].map((n) => n.textContent)).toEqual([
      'Sanciones y moderación',
      'Partidas',
      'Torneos',
      'Chat',
    ]);
    expect(fila('sanciones.suspension.maxima-dias').textContent).toContain('Suspensión máxima');
    expect(fila('sanciones.suspension.maxima-dias').textContent).toContain('30 días');
    expect(fila('sanciones.suspension.maxima-dias').textContent).toContain('Por admin_qa');
    expect(fila('metricas.umbral-reportes-por-dia').textContent).toContain('Sin definir');
    expect(
      fila('chat.historial.tamano').querySelector('[data-campo="programado"]').textContent,
    ).toMatch(/^Pasará a 100 mensajes el /);
    expect(fila('torneos.cupos').querySelector('[data-campo="fijo"]').textContent).toBe('Fijo');
    expect(fila('torneos.cupos').querySelector('[data-accion="cambiar"]')).toBeNull();
    expect(document.body.textContent).not.toMatch(
      /Charter|RNF|RF-[A-Z]|HU-[A-Z]|D-\d|pendiente del PO|origen:|en caliente/,
    );
    // Los apodos se piden de una vez, solo los que aparecen.
    expect(consultarApi).toHaveBeenCalledWith(`/admin/titulares?uids=${UID_ADMIN}`);
  });

  test('cambiar: panel con lo que admite, frase de lo que pasará, motivo y PUT', async () => {
    let cuerpoRecibido = null;
    const { fetchImpl } = montar({
      rutas: {
        'PUT /api/v1/parametros/sanciones.suspension.maxima-dias': (opciones) => {
          cuerpoRecibido = JSON.parse(opciones.body);
          return { cuerpo: parametro({ valor: '45', version: 3 }) };
        },
      },
    });
    await asentar();

    fila('sanciones.suspension.maxima-dias').querySelector('[data-accion="cambiar"]').click();
    expect(panelAbierto().dataset.panelDePrueba).toBe('Cambiar · Suspensión máxima');
    expect(panelAbierto().textContent).toContain('Entre 1 y 365 días');

    const formulario = panelAbierto().querySelector('[data-zona="cambio"]');
    escribir(formulario.querySelector('[name="valor"]'), '45');
    expect(panelAbierto().querySelector('[data-zona="resumen-del-cambio"]').textContent).toBe(
      'Pasará de 30 días a 45 días, de inmediato.',
    );

    // Sin motivo no se envía nada.
    panelAbierto().querySelector('[data-accion="guardar-cambio"]').click();
    await asentar();
    expect(cuerpoRecibido).toBeNull();
    expect(formulario.querySelector('[name="motivo"]').getAttribute('aria-invalid')).toBe('true');

    escribir(formulario.querySelector('[name="motivo"]'), 'Acuerdo del comité de moderación');
    panelAbierto().querySelector('[data-accion="guardar-cambio"]').click();
    await asentar();

    expect(cuerpoRecibido).toEqual({ valor: '45', motivo: 'Acuerdo del comité de moderación' });
    expect(panelAbierto()).toBeNull();
    const aviso = document.querySelector('[data-zona="aviso"]');
    expect(aviso.hidden).toBe(false);
    expect(aviso.textContent).toContain('Cambio aplicado');
    expect(aviso.textContent).toContain('Suspensión máxima: ahora 45 días.');
    // Se recarga el catálogo después de guardar.
    expect(fetchImpl.mock.calls.filter(([url]) => url.endsWith('/api/v1/parametros'))).toHaveLength(
      2,
    );
  });

  test('el valor vigente no se «cambia» por sí mismo, y uno fuera de rango no sale', async () => {
    const { fetchImpl } = montar();
    await asentar();
    fila('sanciones.suspension.maxima-dias').querySelector('[data-accion="cambiar"]').click();
    const formulario = panelAbierto().querySelector('[data-zona="cambio"]');
    escribir(formulario.querySelector('[name="motivo"]'), 'Motivo suficiente');

    escribir(formulario.querySelector('[name="valor"]'), '30');
    panelAbierto().querySelector('[data-accion="guardar-cambio"]').click();
    await asentar();
    expect(formulario.textContent).toContain('Ese ya es el valor vigente (30 días).');

    escribir(formulario.querySelector('[name="valor"]'), '400');
    panelAbierto().querySelector('[data-accion="guardar-cambio"]').click();
    await asentar();
    expect(formulario.textContent).toContain('Entre 1 y 365 días');
    expect(fetchImpl.mock.calls.some(([, o]) => o?.method === 'PUT')).toBe(false);
  });

  test('un rechazo del servicio se dice en el panel, sin la clave técnica', async () => {
    montar({
      rutas: {
        'PUT /api/v1/parametros/sanciones.suspension.maxima-dias': {
          estado: 400,
          cuerpo: {
            title: 'Valor invalido',
            status: 400,
            motivo: 'VALOR_INVALIDO',
            detail: 'sanciones.suspension.maxima-dias no puede ser mayor que 365',
          },
        },
      },
    });
    await asentar();
    fila('sanciones.suspension.maxima-dias').querySelector('[data-accion="cambiar"]').click();
    const formulario = panelAbierto().querySelector('[data-zona="cambio"]');
    escribir(formulario.querySelector('[name="valor"]'), '60');
    escribir(formulario.querySelector('[name="motivo"]'), 'Ajuste de prueba');
    panelAbierto().querySelector('[data-accion="guardar-cambio"]').click();
    await asentar();

    const error = panelAbierto().querySelector('[data-zona="error-del-cambio"]');
    expect(error.hidden).toBe(false);
    expect(error.textContent).toBe('Ese valor no está en el rango admitido: Entre 1 y 365 días.');
    expect(panelAbierto().textContent).not.toContain('sanciones.suspension');
  });

  test('un cambio con fecha futura queda programado y lo dice', async () => {
    let cuerpoRecibido = null;
    montar({
      rutas: {
        'PUT /api/v1/parametros/sanciones.suspension.maxima-dias': (opciones) => {
          cuerpoRecibido = JSON.parse(opciones.body);
          return {
            cuerpo: parametro({
              valorProgramado: '45',
              vigenteDesde: '2027-01-10T15:00:00Z',
              version: 3,
            }),
          };
        },
      },
    });
    await asentar();
    fila('sanciones.suspension.maxima-dias').querySelector('[data-accion="cambiar"]').click();
    const formulario = panelAbierto().querySelector('[data-zona="cambio"]');
    escribir(formulario.querySelector('[name="valor"]'), '45');
    escribir(formulario.querySelector('[name="motivo"]'), 'Desde el próximo año');
    escribir(formulario.querySelector('[name="vigenteDesde"]'), '2027-01-10T10:00');
    expect(panelAbierto().querySelector('[data-zona="resumen-del-cambio"]').textContent).toMatch(
      /a partir del /,
    );
    panelAbierto().querySelector('[data-accion="guardar-cambio"]').click();
    await asentar();

    expect(cuerpoRecibido.vigenteDesde).toMatch(/^2027-01-10T/);
    const aviso = document.querySelector('[data-zona="aviso"]');
    expect(aviso.textContent).toContain('Cambio programado');
    expect(aviso.textContent).toContain('Suspensión máxima pasará a 45 días el');
  });

  test('el historial se abre en su panel, con apodos y el cambio en palabras', async () => {
    montar({
      rutas: {
        'GET /api/v1/parametros/sanciones.suspension.maxima-dias/historial': {
          cuerpo: [
            {
              version: 2,
              valorAnterior: '30',
              valorNuevo: '15',
              motivo: 'Ajuste de la Sprint Review',
              cambiadoPor: UID_ADMIN,
              cambiadoEn: '2026-10-05T15:00:00Z',
              vigenteDesde: '2026-10-05T15:00:00Z',
            },
            {
              version: 1,
              valorAnterior: null,
              valorNuevo: '30',
              motivo: 'Valor de partida del catálogo',
              cambiadoPor: '00000000-0000-0000-0000-000000000000',
              cambiadoEn: '2026-09-01T15:00:00Z',
              vigenteDesde: '2026-09-01T15:00:00Z',
            },
          ],
        },
      },
    });
    await asentar();
    fila('sanciones.suspension.maxima-dias').querySelector('[data-accion="historial"]').click();
    await asentar();

    expect(panelAbierto().dataset.panelDePrueba).toBe('Historial · Suspensión máxima');
    const historial = panelAbierto().querySelector('[data-zona="historial"]');
    expect(historial.textContent).toContain('30 días → 15 días');
    expect(historial.textContent).toContain('Ajuste de la Sprint Review');
    expect(historial.textContent).toContain('admin_qa');
    expect(historial.textContent).not.toContain(UID_ADMIN);
    // Lo que registró el propio servicio se dice «Sistema», no «cuenta sin apodo».
    expect(historial.textContent).toContain('Sin definir → 30 días');
    expect(historial.textContent).toContain('Sistema');
  });

  test('sin rol de administración no hay acciones ni se piden apodos', async () => {
    const { consultarApi } = montar({ rol: 'MODERADOR' });
    await asentar();
    expect(document.querySelectorAll('[data-accion="cambiar"]')).toHaveLength(0);
    expect(document.querySelectorAll('[data-accion="historial"]')).toHaveLength(0);
    expect(consultarApi).not.toHaveBeenCalled();
  });

  test('si el servicio no responde, la vista lo dice con reintento y sin cifras inventadas', async () => {
    document.body.innerHTML = `
      <div data-zona="encabezado"></div><div data-zona="aviso" hidden></div>
      <div data-zona="indicadores"></div><div data-zona="catalogo"></div>`;
    const fetchImpl = servicio({
      'GET /api/v1/parametros': { estado: 503, cuerpo: { title: 'Caido', status: 503 } },
    });
    montarParametros(document, {
      rol: 'SUPER_ADMINISTRADOR',
      fetchImpl,
      consultarApi: jest.fn(),
      abrirPanel: panelDePrueba(),
    });
    await asentar();
    expect(document.body.textContent).toContain('No pudimos cargar los parámetros');
    expect(document.querySelector('[data-indicador]')).toBeNull();
    expect(document.querySelector('[data-accion="reintentar"], button')).not.toBeNull();
  });
});
