/**
 * UXC-8 — el panel personal de «Mis subastas» (ms-subastas-panel.yaml 1.0.0)
 * y lo que la ficha (GET /subastas/{id}) añade al detalle.
 *
 * Antes, «Tus publicaciones» solo sabía de las tuyas que seguían abiertas en
 * la primera página del listado, no había lista de seguimiento ni historial,
 * y el detalle no decía quién vendía ni qué opinaba nadie del objeto. Estas
 * pruebas fijan lo nuevo contra una API falsa con la forma del contrato.
 */

import { jest } from '@jest/globals';
import {
  ControladorSubastas,
  descargarTexto,
  estadoDeSubasta,
  montoConSigno,
  resultadoDePuja,
  textoDeReputacion,
  vistaDeMiPuja,
  vistaDeHistorial,
  vistaDePublicacion,
  vistaDeSeguida,
} from './pujas.js';

const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;
const EN_UNA_HORA = () => new Date(Date.now() + 3600_000).toISOString();
const HACE_DOS_DIAS = () => new Date(Date.now() - 2 * 86_400_000).toISOString();

function subasta(extra = {}) {
  return {
    id: 'abierta-1',
    nombre: 'Hacha de Obsidiana',
    tipo: 'ARMA',
    descripcion: '',
    rareza: null,
    nivel: null,
    vendedor: null,
    esPropia: false,
    oferta: 1350,
    compraInmediata: null,
    mediaMercado: null,
    segundosRestantes: 600,
    ganando: false,
    superado: false,
    autoLimite: 0,
    esperaSegundos: 0,
    retenido: 0,
    rival: null,
    rivales: 3,
    aporte: null,
    historial: [],
    ...extra,
  };
}

function publicacion(extra = {}) {
  return {
    subastaId: '0a1b2c3d-0000-4000-8000-000000000001',
    nombreProducto: 'Grebas del Centinela',
    miniaturaUrl: null,
    estado: 'ACTIVA',
    precioInicial: '500',
    ofertaVigente: '500',
    precioCompraInmediata: null,
    cantidadPujas: 0,
    fechaPublicacion: HACE_DOS_DIAS(),
    fechaFin: EN_UNA_HORA(),
    cerradaEn: null,
    vistas: 34,
    comisionCobrada: '1.00',
    penalizacionCobrada: null,
    cancelable: true,
    penalizacionSiCancela: '0.50',
    ...extra,
  };
}

function seguida(extra = {}) {
  return {
    subastaId: '0a1b2c3d-0000-4000-8000-0000000000aa',
    nombreProducto: 'Daga de Hueso',
    miniaturaUrl: null,
    estado: 'ACTIVA',
    ofertaVigente: '820',
    precioCompraInmediata: null,
    cantidadPujas: 2,
    fechaFin: EN_UNA_HORA(),
    seguidaDesde: HACE_DOS_DIAS(),
    ...extra,
  };
}

function movimiento(tipo, monto, diasAtras, extra = {}) {
  return {
    tipo,
    subastaId: '0a1b2c3d-0000-4000-8000-0000000000bb',
    nombreProducto: 'Yelmo de Ceniza',
    monto,
    fecha: new Date(Date.now() - diasAtras * 86_400_000).toISOString(),
    ...extra,
  };
}

const HISTORIAL = {
  movimientos: [
    movimiento('COMISION', '1.00', 5),
    movimiento('VENTA', '1200', 1),
    movimiento('COMPRA', '850', 3),
    movimiento('PENALIZACION', '0.50', 4),
  ],
  totalGanado: '1200',
  totalGastado: '851.50',
  comisionesPagadas: '1.50',
  balance: '348.50',
};

function apiFalsa({ listado = [subasta()], ...resto } = {}) {
  return {
    listar: jest.fn(async ({ page = 0 } = {}) =>
      page === 0 ? JSON.parse(JSON.stringify(listado)) : [],
    ),
    miResumen: jest.fn(async () => ({
      creditosRetenidos: '0',
      saldoDisponible: '5000',
      subastasGanando: 0,
    })),
    historial: jest.fn(async () => []),
    miParticipacion: jest.fn(async () => null),
    ficha: jest.fn(async () => null),
    misPublicaciones: jest.fn(async () => [
      publicacion(),
      publicacion({
        subastaId: '0a1b2c3d-0000-4000-8000-000000000002',
        nombreProducto: 'Arco de Tejo',
        estado: 'ADJUDICADA',
        ofertaVigente: '2300',
        cantidadPujas: 6,
        cerradaEn: HACE_DOS_DIAS(),
        cancelable: false,
        penalizacionSiCancela: null,
      }),
      publicacion({
        subastaId: '0a1b2c3d-0000-4000-8000-000000000003',
        nombreProducto: 'Capa Raída',
        estado: 'SIN_ADJUDICACION',
        cantidadPujas: 0,
        cerradaEn: HACE_DOS_DIAS(),
        cancelable: false,
        penalizacionSiCancela: null,
      }),
      publicacion({
        subastaId: '0a1b2c3d-0000-4000-8000-000000000004',
        nombreProducto: 'Anillo Opaco',
        estado: 'CANCELADA',
        cerradaEn: HACE_DOS_DIAS(),
        penalizacionCobrada: '0.50',
        cancelable: false,
        penalizacionSiCancela: null,
      }),
    ]),
    misSeguidas: jest.fn(async () => [seguida()]),
    miHistorial: jest.fn(async () => JSON.parse(JSON.stringify(HISTORIAL))),
    exportarHistorial: jest.fn(async () => 'tipo,subastaId\nVENTA,x\n'),
    cancelar: jest.fn(async () => ({ estado: 'CANCELADA' })),
    dejarDeSeguir: jest.fn(async () => null),
    ...resto,
  };
}

function montar(opciones) {
  const contenedor = document.createElement('div');
  document.body.appendChild(contenedor);
  const ctrl = new ControladorSubastas({
    contenedor,
    leerToken: () => 'token-de-prueba',
    sondeoMs: null,
    ...opciones,
  });
  return { contenedor, ctrl };
}

const esperar = async () => {
  for (let i = 0; i < 12; i += 1) {
    await Promise.resolve();
  }
};

afterEach(() => {
  document.body.innerHTML = '';
});

describe('funciones del panel', () => {
  test('una publicación dice su estado y lo que cuesta cancelarla, sin calcularlo', () => {
    const vista = vistaDePublicacion(publicacion(), Date.now());
    expect(vista).toMatchObject({
      estado: 'ACTIVA',
      oferta: 500,
      vistas: 34,
      comision: 1,
      cancelable: true,
      penalizacionSiCancela: 0.5,
    });
    expect(vista.segundosRestantes).toBeGreaterThan(3500);
    // Terminada: sin reloj.
    expect(vistaDePublicacion(publicacion({ estado: 'ADJUDICADA' })).segundosRestantes).toBe(0);
    // Lo que no vino es null, nunca cero.
    expect(vistaDePublicacion(publicacion({ vistas: null, comisionCobrada: null }))).toMatchObject({
      vistas: null,
      comision: null,
    });
  });

  test('una seguida trae su oferta y desde cuándo la sigues', () => {
    expect(vistaDeSeguida(seguida())).toMatchObject({ oferta: 820, pujas: 2, estado: 'ACTIVA' });
  });

  test('el historial va del más reciente al más antiguo, con qué suma y qué resta', () => {
    const vista = vistaDeHistorial(HISTORIAL);
    expect(vista.movimientos.map((m) => m.tipo)).toEqual([
      'VENTA',
      'COMPRA',
      'PENALIZACION',
      'COMISION',
    ]);
    expect(vista.movimientos.map((m) => m.entra)).toEqual([true, false, false, false]);
    expect(vista).toMatchObject({ totalGanado: 1200, totalGastado: 851.5, balance: 348.5 });
    expect(vistaDeHistorial(null).movimientos).toEqual([]);
  });

  test('las cifras llevan el signo escrito', () => {
    expect(montoConSigno(1200, true)).toBe('+1.200 cr');
    expect(montoConSigno(850, false)).toBe('−850 cr');
    expect(montoConSigno(0, false)).toBe('0 cr');
    expect(montoConSigno(null, true)).toBe('—');
  });

  test('los estados en palabras; «adjudicada» es «vendida» en lo tuyo', () => {
    expect(estadoDeSubasta('ACTIVA').texto).toBe('En curso');
    expect(estadoDeSubasta('ADJUDICADA').texto).toBe('Adjudicada');
    expect(estadoDeSubasta('ADJUDICADA', { propia: true }).texto).toBe('Vendida');
    expect(estadoDeSubasta('SIN_ADJUDICACION').texto).toBe('Terminó sin pujas');
    expect(estadoDeSubasta('CANCELADA').texto).toBe('Cancelada');
  });

  test('la reputación del vendedor, con las cifras del servidor y sin estrellas inventadas', () => {
    expect(textoDeReputacion(null)).toBe('');
    expect(
      textoDeReputacion({ ventasCompletadas: 0, subastasTerminadas: 0, cancelaciones: 0 }),
    ).toBe('Todavía no ha terminado ninguna subasta.');
    expect(
      textoDeReputacion({ ventasCompletadas: 12, subastasTerminadas: 15, cancelaciones: 1 }),
    ).toBe('Vendió 12 de 15 subastas terminadas · canceló 1.');
    expect(
      textoDeReputacion({ ventasCompletadas: 1, subastasTerminadas: 1, cancelaciones: 0 }),
    ).toBe('Vendió 1 de 1 subasta terminada.');
  });

  test('descargar el CSV crea el archivo con su nombre y suelta la dirección', () => {
    jest.useFakeTimers();
    const url = { createObjectURL: jest.fn(() => 'blob:historial'), revokeObjectURL: jest.fn() };
    const clic = jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    descargarTexto('a,b\n', 'historial-subastas.csv', { url });
    expect(url.createObjectURL).toHaveBeenCalledTimes(1);
    const [blob] = url.createObjectURL.mock.calls[0];
    expect(blob.type).toBe('text/csv;charset=utf-8');
    expect(clic).toHaveBeenCalledTimes(1);
    // El enlace temporal no se queda en la página.
    expect(document.querySelector('a[download]')).toBeNull();
    jest.runAllTimers();
    expect(url.revokeObjectURL).toHaveBeenCalledWith('blob:historial');
    clic.mockRestore();
    jest.useRealTimers();
  });

  test('sin forma de guardar archivos, lo dice en vez de fallar en silencio', () => {
    expect(() => descargarTexto('x', 'a.csv', { url: {} })).toThrow('no deja guardar el archivo');
  });
});

function participacionDelServidor(extra = {}) {
  return {
    subastaId: '0a1b2c3d-0000-4000-8000-0000000000c1',
    nombreProducto: 'Lanza del Ocaso',
    miniaturaUrl: null,
    estadoSubasta: 'ACTIVA',
    estado: 'GANANDO',
    tuMejorPuja: '640',
    ofertaVigente: '640',
    cantidadPujas: 4,
    fechaFin: EN_UNA_HORA(),
    ultimaPujaEn: HACE_DOS_DIAS(),
    ...extra,
  };
}

describe('«Mis pujas» (GET /mis-pujas, 0.4.0)', () => {
  test('las participaciones en palabras', () => {
    expect(resultadoDePuja('GANADA').texto).toBe('La ganaste');
    expect(resultadoDePuja('PERDIDA').texto).toBe('Se la llevó otro jugador');
    expect(resultadoDePuja('AUTOMATICA').texto).toBe('Automática preparada');
    expect(vistaDeMiPuja(participacionDelServidor())).toMatchObject({
      estado: 'GANANDO',
      tuMejorPuja: 640,
      oferta: 640,
      pujas: 4,
    });
    expect(
      vistaDeMiPuja(participacionDelServidor({ estadoSubasta: 'ADJUDICADA' })).segundosRestantes,
    ).toBe(0);
  });

  test('suma las abiertas de otras páginas y aparte las terminadas; sin nota de alcance', async () => {
    const api = apiFalsa({
      misPujas: jest.fn(async () => [
        participacionDelServidor(),
        participacionDelServidor({
          subastaId: '0a1b2c3d-0000-4000-8000-0000000000c2',
          nombreProducto: 'Tótem Hueco',
          estadoSubasta: 'ADJUDICADA',
          estado: 'GANADA',
        }),
        participacionDelServidor({
          subastaId: '0a1b2c3d-0000-4000-8000-0000000000c3',
          nombreProducto: 'Botas Ligeras',
          estadoSubasta: 'ADJUDICADA',
          estado: 'PERDIDA',
          tuMejorPuja: '200',
          ofertaVigente: '260',
        }),
      ]),
      pendientes: jest.fn(async () => [
        {
          subastaId: '0a1b2c3d-0000-4000-8000-0000000000c2',
          nombreProducto: 'Tótem Hueco',
          elementoInventarioId: 'e-1',
          montoPagado: '640',
          ganadaEn: HACE_DOS_DIAS(),
          venceEn: EN_UNA_HORA(),
          estado: 'PENDIENTE',
        },
      ]),
      reglas: jest.fn(async () => null),
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    const seccion = contenedor.querySelector('#seccion-pujas');
    expect(seccion.querySelector('.contador-mis-subastas').textContent).toBe(
      '1 en curso · 2 terminadas',
    );
    const abierta = seccion.querySelector('.fila-mi-puja[data-estado="ACTIVA"]');
    expect(abierta.textContent).toContain('Vas ganando');
    expect(abierta.textContent).toContain('Tu mejor puja: 640 cr');
    expect(abierta.querySelector('[data-abrir-panel]')).not.toBeNull();
    const ganada = seccion.querySelector('[data-resultado="GANADA"]');
    expect(ganada.textContent).toContain('La ganaste');
    // Ganada al vencer y sin recoger: el botón para hacerlo, aquí mismo.
    expect(ganada.querySelector('.btn-recoger-pendiente')).not.toBeNull();
    const perdida = seccion.querySelector('[data-resultado="PERDIDA"]');
    expect(perdida.textContent).toContain('Se la llevó otro jugador');
    expect(perdida.textContent).toContain('Oferta final');
    // La lista es completa: no se explica un alcance que ya no aplica.
    expect(seccion.querySelector('.nota-alcance')).toBeNull();
    expect(seccion.textContent).not.toMatch(UUID);
    expect(contenedor.querySelector('.indice-mis-subastas').textContent).toContain(
      'Donde pujas (1)',
    );
    ctrl.destruir();
  });

  test('sin «Mis pujas», se sigue preguntando subasta por subasta y se dice el alcance', async () => {
    const api = apiFalsa({
      misPujas: jest.fn(async () => {
        throw new Error('caído');
      }),
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    const seccion = contenedor.querySelector('#seccion-pujas');
    expect(seccion.textContent).toContain('No pudimos traer todas tus pujas');
    expect(seccion.querySelector('[data-reintentar-panel]')).not.toBeNull();
    expect(seccion.querySelector('.nota-alcance')).not.toBeNull();
    ctrl.destruir();
  });
});

describe('el historial de una subasta', () => {
  test('dice quién pujó con el apodo anonimizado del servidor (7.7.11)', async () => {
    const api = apiFalsa({
      historial: jest.fn(async () => [
        {
          id: 'p2',
          monto: '1350',
          tipo: 'MANUAL',
          estado: 'ACTIVA',
          creadaEn: HACE_DOS_DIAS(),
          esTuya: false,
          postor: 'k***s',
        },
        {
          id: 'p1',
          monto: '1300',
          tipo: 'AUTOMATICA',
          estado: 'SUPERADA',
          creadaEn: HACE_DOS_DIAS(),
          esTuya: false,
          postor: null,
        },
      ]),
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    ctrl.abrirDetalle('abierta-1');
    await esperar();
    await esperar();

    const postores = [...contenedor.querySelectorAll('.historial-postor')].map((n) =>
      n.textContent.trim(),
    );
    expect(postores).toEqual(['k***s', 'Otro jugador']);
    ctrl.destruir();
  });
});

describe('«Tus publicaciones» con el panel', () => {
  test('enseña todas, en curso primero, con su estado, visitas y lo cobrado', async () => {
    const api = apiFalsa();
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    const filas = [...contenedor.querySelectorAll('.fila-publicacion--panel')];
    expect(filas.map((f) => f.dataset.estado)).toEqual([
      'ACTIVA',
      'ADJUDICADA',
      'SIN_ADJUDICACION',
      'CANCELADA',
    ]);
    const [enCurso, vendida, sinPujas, cancelada] = filas;
    expect(enCurso.textContent).toContain('En curso');
    expect(enCurso.textContent).toContain('34 visitas');
    expect(enCurso.textContent).toContain('Comisión: 1 cr');
    expect(enCurso.textContent).toContain('Precio mínimo');
    expect(enCurso.querySelector('[data-cancelar-publicacion]')).not.toBeNull();
    expect(vendida.textContent).toContain('Vendida');
    expect(vendida.textContent).toContain('Vendida por');
    expect(vendida.textContent).toContain('2.300 cr');
    expect(vendida.querySelector('[data-cancelar-publicacion]')).toBeNull();
    expect(sinPujas.textContent).toContain('Terminó sin pujas');
    expect(cancelada.textContent).toContain('Penalización: 0,5 cr');
    // Ningún identificador interno a la vista.
    expect(contenedor.querySelector('#seccion-publicaciones').textContent).not.toMatch(UUID);
    expect(contenedor.querySelector('.contador-mis-subastas').textContent).toBeTruthy();
    ctrl.destruir();
  });

  test('cancelar pide confirmación con lo que cuesta según el servidor, y relee el panel', async () => {
    const api = apiFalsa();
    const confirmarAccion = jest.fn(async () => true);
    const { contenedor, ctrl } = montar({ api, confirmarAccion });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    contenedor.querySelector('[data-cancelar-publicacion]').click();
    await esperar();
    await esperar();

    expect(confirmarAccion).toHaveBeenCalledTimes(1);
    const [{ mensaje, textoConfirmar }] = confirmarAccion.mock.calls[0];
    expect(mensaje).toContain('Grebas del Centinela');
    expect(mensaje).toContain('0,5 cr de penalización');
    expect(textoConfirmar).toBe('Cancelar y pagar 0,5 cr');
    expect(api.cancelar).toHaveBeenCalledWith('0a1b2c3d-0000-4000-8000-000000000001');
    expect(api.misPublicaciones).toHaveBeenCalledTimes(2);
    ctrl.destruir();
  });

  test('si no confirmas, no se cancela nada', async () => {
    const api = apiFalsa();
    const { contenedor, ctrl } = montar({ api, confirmarAccion: async () => false });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    contenedor.querySelector('[data-cancelar-publicacion]').click();
    await esperar();

    expect(api.cancelar).not.toHaveBeenCalled();
    ctrl.destruir();
  });

  test('el Maestro de Juego (sin comisión) cancela sin penalización, y se dice', async () => {
    const api = apiFalsa({
      misPublicaciones: jest.fn(async () => [
        publicacion({ comisionCobrada: '0', penalizacionSiCancela: '0' }),
      ]),
    });
    const confirmarAccion = jest.fn(async () => false);
    const { contenedor, ctrl } = montar({ api, confirmarAccion });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    contenedor.querySelector('[data-cancelar-publicacion]').click();
    await esperar();

    expect(confirmarAccion.mock.calls[0][0].mensaje).toContain('No pagas penalización');
    ctrl.destruir();
  });

  test('sin publicaciones, el vacío lleva a publicar', async () => {
    const api = apiFalsa({ misPublicaciones: jest.fn(async () => []) });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    const vacio = contenedor.querySelector('[data-estado="sin-publicaciones"]');
    expect(vacio.textContent).toContain('Todavía no has publicado nada');
    expect(vacio.querySelector('a[href="./publicar-subasta.html"]')).not.toBeNull();
    ctrl.destruir();
  });

  test('si el panel falla, quedan las tuyas abiertas del listado y se ofrece reintentar', async () => {
    const misPublicaciones = jest
      .fn()
      .mockRejectedValueOnce(new Error('caído'))
      .mockResolvedValue([publicacion()]);
    const api = apiFalsa({
      listado: [subasta({ id: 'mia', esPropia: true, nombre: 'Escudo Propio' })],
      misPublicaciones,
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    const seccion = contenedor.querySelector('#seccion-publicaciones');
    expect(seccion.textContent).toContain('No pudimos traer todas tus publicaciones');
    expect(seccion.querySelector('.fila-publicacion[data-id="mia"]')).not.toBeNull();
    // Las otras partes llegaron igual.
    expect(contenedor.querySelector('.fila-seguida')).not.toBeNull();

    seccion.querySelector('[data-reintentar-panel]').click();
    await esperar();
    await esperar();

    expect(misPublicaciones).toHaveBeenCalledTimes(2);
    expect(contenedor.querySelector('.fila-publicacion--panel')).not.toBeNull();
    expect(contenedor.textContent).not.toContain('No pudimos traer todas tus publicaciones');
    ctrl.destruir();
  });

  test('una en curso que no está en la primera página se busca y se abre', async () => {
    const lejana = subasta({ id: '0a1b2c3d-0000-4000-8000-000000000001', esPropia: true });
    const api = apiFalsa({
      listar: jest.fn(async ({ page = 0 } = {}) => {
        if (page === 0) {
          return [subasta()];
        }
        return page === 1 ? [lejana] : [];
      }),
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    contenedor.querySelector('[data-abrir-panel]').click();
    await esperar();
    await esperar();

    expect(ctrl.vista).toBe('detalle');
    expect(ctrl.subastaActivaId).toBe(lejana.id);
    ctrl.destruir();
  });

  test('sin panel en la api, «Mis subastas» sigue como antes y no deja partes vacías', async () => {
    const api = apiFalsa();
    delete api.misPublicaciones;
    delete api.misSeguidas;
    delete api.miHistorial;
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    expect(contenedor.querySelector('#seccion-publicaciones')).not.toBeNull();
    expect(contenedor.querySelector('#seccion-seguidas')).toBeNull();
    expect(contenedor.querySelector('#seccion-historial')).toBeNull();
    expect(contenedor.querySelector('.indice-mis-subastas')).toBeNull();
    ctrl.destruir();
  });
});

describe('«Siguiendo»', () => {
  test('lista lo que sigues y deja de seguir sin abrir la subasta', async () => {
    const api = apiFalsa();
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    const fila = contenedor.querySelector('.fila-seguida');
    expect(fila.textContent).toContain('Daga de Hueso');
    expect(fila.textContent).toContain('Oferta vigente');
    expect(fila.textContent).toContain('2 pujas');
    expect(fila.querySelector('[data-tiempo-subasta]').dataset.fin).toBeTruthy();

    fila.querySelector('[data-dejar-de-seguir]').click();
    await esperar();
    await esperar();

    expect(api.dejarDeSeguir).toHaveBeenCalledWith('0a1b2c3d-0000-4000-8000-0000000000aa');
    expect(ctrl.vista).toBe('mis-subastas');
    expect(api.misSeguidas).toHaveBeenCalledTimes(2);
    ctrl.destruir();
  });

  test('vacía, explica para qué sirve y lleva al mercado', async () => {
    const api = apiFalsa({ misSeguidas: jest.fn(async () => []) });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    const vacio = contenedor.querySelector('[data-estado="sin-seguidas"]');
    expect(vacio.textContent).toContain('una hora antes del cierre');
    vacio.querySelector('[data-ir-a-explorar]').click();
    expect(ctrl.vista).toBe('explorar');
    ctrl.destruir();
  });
});

describe('«Historial»', () => {
  test('el resumen y los movimientos, con el signo escrito', async () => {
    const api = apiFalsa();
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    const seccion = contenedor.querySelector('#seccion-historial');
    expect(seccion.querySelector('.resumen-historial').textContent).toContain('+1.200 cr');
    expect(seccion.querySelector('.resumen-historial').textContent).toContain('−851,5 cr');
    expect(seccion.querySelector('.resumen-historial__dato--balance').textContent).toContain(
      '+348,5 cr',
    );
    const filas = [...seccion.querySelectorAll('tbody tr')];
    expect(filas.map((f) => f.dataset.tipo)).toEqual([
      'VENTA',
      'COMPRA',
      'PENALIZACION',
      'COMISION',
    ]);
    expect(filas[0].textContent).toContain('+1.200 cr');
    expect(filas[1].textContent).toContain('−850 cr');
    expect(seccion.querySelector('caption').textContent).toContain('del más reciente');
    ctrl.destruir();
  });

  test('con muchos movimientos se ven los últimos y el resto a demanda', async () => {
    const muchos = Array.from({ length: 14 }, (_, i) => movimiento('COMPRA', '10', i + 1));
    const api = apiFalsa({
      miHistorial: jest.fn(async () => ({ ...HISTORIAL, movimientos: muchos })),
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    expect(contenedor.querySelectorAll('#seccion-historial tbody tr')).toHaveLength(10);
    const ver = contenedor.querySelector('[data-ver-todo="historial"]');
    expect(ver.textContent).toContain('Ver los 14 movimientos');
    expect(ver.getAttribute('aria-expanded')).toBe('false');
    ver.click();
    expect(contenedor.querySelectorAll('#seccion-historial tbody tr')).toHaveLength(14);
    expect(
      contenedor.querySelector('[data-ver-todo="historial"]').getAttribute('aria-expanded'),
    ).toBe('true');
    ctrl.destruir();
  });

  test('exportar pide el CSV al servidor y lo guarda', async () => {
    const api = apiFalsa();
    const { contenedor, ctrl } = montar({ api });
    const url = globalThis.URL;
    globalThis.URL.createObjectURL = jest.fn(() => 'blob:x');
    globalThis.URL.revokeObjectURL = jest.fn();
    const clic = jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    await ctrl.exportarHistorial();

    expect(api.exportarHistorial).toHaveBeenCalledTimes(1);
    expect(clic).toHaveBeenCalledTimes(1);
    expect(contenedor.querySelector('#btn-exportar-historial').disabled).toBe(false);
    clic.mockRestore();
    delete url.createObjectURL;
    delete url.revokeObjectURL;
    ctrl.destruir();
  });

  test('si exportar falla, se dice y no se descarga nada', async () => {
    const api = apiFalsa({
      exportarHistorial: jest.fn(async () => {
        throw new Error('Failed to fetch');
      }),
    });
    const { contenedor, ctrl } = montar({ api });
    const clic = jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    await ctrl.exportarHistorial();

    expect(clic).not.toHaveBeenCalled();
    const alerta = contenedor.querySelector('#alerta-pujas');
    expect(alerta.textContent).toContain('No pudimos preparar tu historial');
    expect(alerta.textContent).not.toContain('Failed to fetch');
    clic.mockRestore();
    ctrl.destruir();
  });

  test('sin movimientos no hay nada que exportar, y el vacío dice qué llenará la lista', async () => {
    const api = apiFalsa({
      miHistorial: jest.fn(async () => ({
        movimientos: [],
        totalGanado: '0',
        totalGastado: '0',
        comisionesPagadas: '0',
        balance: '0',
      })),
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();
    await esperar();

    expect(contenedor.querySelector('#btn-exportar-historial')).toBeNull();
    expect(contenedor.querySelector('[data-estado="sin-movimientos"]').textContent).toContain(
      'Todavía no tienes movimientos',
    );
    ctrl.destruir();
  });
});

describe('el detalle con la ficha', () => {
  const ficha = (extra = {}) => ({
    id: 'abierta-1',
    estado: 'ACTIVA',
    productoId: '7f000000-0000-4000-8000-00000000c0de',
    nombreProducto: 'Hacha de Obsidiana',
    ofertaVigente: '1350',
    pujaMinimaSiguiente: '1400',
    incrementoMinimo: '50',
    cantidadPujas: 3,
    compraInmediataDisponible: false,
    vendedorId: 'uid-otro',
    vendedorApodo: 'kaelthas_vx',
    reputacionVendedor: { ventasCompletadas: 12, subastasTerminadas: 15, cancelaciones: 1 },
    vistas: 48,
    ...extra,
  });

  test('dice quién vende, cómo le fue y cuántos la vieron', async () => {
    const api = apiFalsa({ ficha: jest.fn(async () => ficha()) });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    ctrl.abrirDetalle('abierta-1');
    await esperar();
    await esperar();

    expect(contenedor.textContent).toContain('Vendedor: kaelthas_vx');
    const datos = contenedor.querySelector('.ficha-subasta').textContent;
    expect(datos).toContain('48 jugadores la han visto');
    expect(datos).toContain('Vendió 12 de 15 subastas terminadas · canceló 1.');
    ctrl.destruir();
  });

  test('las opiniones del objeto se montan una vez, fuera de lo que se repinta', async () => {
    const zonaOpiniones = document.createElement('div');
    zonaOpiniones.hidden = true;
    document.body.appendChild(zonaOpiniones);
    const montarOpiniones = jest.fn((zona) => {
      zona.replaceChildren(Object.assign(document.createElement('section'), { id: 'hilo' }));
    });
    const api = apiFalsa({ ficha: jest.fn(async () => ficha()) });
    const { ctrl } = montar({ api, zonaOpiniones, montarOpiniones });
    await ctrl.iniciar();
    ctrl.abrirDetalle('abierta-1');
    await esperar();
    await esperar();

    expect(montarOpiniones).toHaveBeenCalledTimes(1);
    expect(montarOpiniones.mock.calls[0][1]).toMatchObject({
      productoId: '7f000000-0000-4000-8000-00000000c0de',
      titulo: 'Opiniones de este objeto',
    });
    expect(zonaOpiniones.hidden).toBe(false);

    // Repintar (el sondeo, el canal) no vuelve a montarlas.
    ctrl.render();
    ctrl.render();
    expect(montarOpiniones).toHaveBeenCalledTimes(1);

    // Fuera del detalle se esconden.
    ctrl.abrirExplorar();
    expect(zonaOpiniones.hidden).toBe(true);
    ctrl.destruir();
  });

  test('sin producto en la ficha no hay opiniones que enseñar', async () => {
    const zonaOpiniones = document.createElement('div');
    const montarOpiniones = jest.fn();
    const api = apiFalsa({ ficha: jest.fn(async () => ficha({ productoId: null })) });
    const { ctrl } = montar({ api, zonaOpiniones, montarOpiniones });
    await ctrl.iniciar();
    ctrl.abrirDetalle('abierta-1');
    await esperar();

    expect(montarOpiniones).not.toHaveBeenCalled();
    expect(zonaOpiniones.hidden).toBe(true);
    ctrl.destruir();
  });

  test('en tu subasta no se habla de tu reputación, pero sí de tus visitas', async () => {
    const api = apiFalsa({
      listado: [subasta({ esPropia: true })],
      ficha: jest.fn(async () => ficha()),
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    ctrl.abrirDetalle('abierta-1');
    await esperar();
    await esperar();

    const datos = contenedor.querySelector('.ficha-subasta').textContent;
    expect(datos).toContain('48 jugadores la han visto');
    expect(datos).not.toContain('Vendió');
    ctrl.destruir();
  });
});

describe('compartir una subasta', () => {
  test('copia el enlace a la subasta y lo confirma en el botón', async () => {
    const escribir = jest.fn(async () => {});
    const anterior = globalThis.navigator.clipboard;
    Object.defineProperty(globalThis.navigator, 'clipboard', {
      value: { writeText: escribir },
      configurable: true,
    });
    const api = apiFalsa();
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    ctrl.abrirDetalle('abierta-1');
    await esperar();

    contenedor.querySelector('#btn-compartir').click();
    await esperar();

    expect(escribir).toHaveBeenCalledWith(expect.stringMatching(/pujas\.html\?id=abierta-1$/));
    Object.defineProperty(globalThis.navigator, 'clipboard', {
      value: anterior,
      configurable: true,
    });
    ctrl.destruir();
  });

  test('si no se puede copiar, lo dice sin tecnicismos', async () => {
    Object.defineProperty(globalThis.navigator, 'clipboard', {
      value: {
        writeText: async () => {
          throw new Error('NotAllowedError: Document is not focused');
        },
      },
      configurable: true,
    });
    const api = apiFalsa();
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    ctrl.abrirDetalle('abierta-1');
    await esperar();

    await ctrl.compartirSubasta();

    const alerta = contenedor.querySelector('#alerta-pujas').textContent;
    expect(alerta).toContain('No pudimos copiar el enlace');
    expect(alerta).not.toContain('NotAllowedError');
    ctrl.destruir();
  });
});
