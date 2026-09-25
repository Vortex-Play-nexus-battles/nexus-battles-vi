/**
 * UXC-8 — las subastas dicen solo lo que el servidor sabe.
 *
 * «Mis subastas» pintaba el mercado entero como propio, la compra inmediata
 * salía bien y la pantalla volvía al listado sin decir nada, una subasta sin
 * compra inmediata ofrecía «Comprar ya: 0 cr» y el vendedor era un UUID.
 * Estas pruebas fijan el comportamiento nuevo contra una API falsa con la
 * forma de los contratos (`ms-subastas-pujas.yaml`, `ms-subastas-listado.yaml`).
 */

import { jest } from '@jest/globals';
import {
  ControladorSubastas,
  ESTADO_CANAL,
  verificarSobreCompromiso,
  vistaDeParticipacion,
} from './pujas.js';

function subasta(extra = {}) {
  return {
    id: 'sub-1',
    nombre: 'Hacha de Obsidiana',
    tipo: 'ARMA',
    descripcion: '',
    rareza: null,
    nivel: null,
    vendedor: null,
    esPropia: false,
    oferta: 1350,
    compraInmediata: 2800,
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

function participacion(extra = {}) {
  return {
    vasGanando: false,
    teSuperaron: false,
    tuOfertaVigente: null,
    creditosRetenidos: '0',
    limiteAutomatico: null,
    automaticaActiva: false,
    segundosParaVolverAPujar: 0,
    ...extra,
  };
}

function apiFalsa({ listado, participaciones = {}, ...resto } = {}) {
  return {
    listar: jest.fn(async ({ page = 0 } = {}) =>
      page === 0 ? JSON.parse(JSON.stringify(listado ?? [subasta()])) : [],
    ),
    miResumen: jest.fn(async () => ({
      creditosRetenidos: '1350',
      saldoDisponible: '5000',
      subastasGanando: 1,
    })),
    historial: jest.fn(async () => []),
    miParticipacion: jest.fn(async (id) => participaciones[id] ?? participacion()),
    pujar: jest.fn(async () => ({ id: 'p1', estado: 'ACTIVA' })),
    comprarAhora: jest.fn(async () => ({ id: 'p2', estado: 'GANADORA' })),
    configurarAutomatica: jest.fn(async () => ({})),
    desactivarAutomatica: jest.fn(async () => null),
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
  for (let i = 0; i < 10; i += 1) {
    await Promise.resolve();
  }
};

afterEach(() => {
  document.body.innerHTML = '';
});

describe('«Mis subastas» con el servidor', () => {
  test('pregunta tu participación y enseña solo donde pujas, no el mercado', async () => {
    const api = apiFalsa({
      listado: [
        subasta({ id: 'a', nombre: 'Hacha de Obsidiana' }),
        subasta({ id: 'b', nombre: 'Grebas del Centinela' }),
        subasta({ id: 'c', nombre: 'Daga de Hueso' }),
      ],
      participaciones: {
        a: participacion({ vasGanando: true, creditosRetenidos: '1350' }),
        b: participacion({ teSuperaron: true }),
      },
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();

    expect(api.miParticipacion).toHaveBeenCalledTimes(3);
    const filas = [...contenedor.querySelectorAll('.fila-mi-subasta:not(.fila-publicacion)')];
    expect(filas.map((f) => f.dataset.id).sort()).toEqual(['a', 'b']);
    expect(contenedor.querySelector('.borde-ganando')).not.toBeNull();
    expect(contenedor.querySelector('.borde-superada')).not.toBeNull();
    // El medidor cuenta lo tuyo (2), no las tres del mercado.
    expect(contenedor.querySelector('.grid-topes-concurrencia').textContent).toContain('2 de 10');
    expect(contenedor.querySelector('.nota-alcance').textContent).toContain(
      'las 3 subastas abiertas',
    );
    ctrl.destruir();
  });

  test('una recarga del listado no olvida dónde pujas', async () => {
    const api = apiFalsa({
      participaciones: { 'sub-1': participacion({ vasGanando: true, creditosRetenidos: '1350' }) },
    });
    const { ctrl } = montar({ api });
    await ctrl.iniciar();
    expect(ctrl.subastas[0].ganando).toBe(true);

    await ctrl.recargar();

    expect(ctrl.subastas[0].ganando).toBe(true);
    expect(ctrl.subastas[0].retenido).toBe(1350);
    ctrl.destruir();
  });

  test('si no pujas en ninguna, el vacío dice qué hacer y lleva al mercado', async () => {
    const { contenedor, ctrl } = montar({ api: apiFalsa() });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();

    const vacio = contenedor.querySelector('[data-estado="sin-pujas"]');
    expect(vacio.textContent).toContain('No estás pujando en ninguna subasta abierta');
    vacio.querySelector('#btn-mis-a-explorar').click();
    expect(ctrl.vista).toBe('explorar');
    ctrl.destruir();
  });

  test('sin sesión no inventa «tus» subastas: invita a entrar', async () => {
    const { contenedor, ctrl } = montar({ api: apiFalsa(), leerToken: () => null });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();

    expect(contenedor.querySelector('[data-estado="sin-sesion"]').textContent).toContain(
      'Entra para ver tus pujas',
    );
    expect(ctrl.api.miParticipacion).not.toHaveBeenCalled();
    ctrl.destruir();
  });

  test('sin saldo del servidor no se dibuja una barra de «libre»', async () => {
    const api = apiFalsa({
      miResumen: jest.fn(async () => ({
        creditosRetenidos: '0',
        saldoDisponible: null,
        subastasGanando: 0,
      })),
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    await ctrl.abrirMisSubastas();

    expect(contenedor.querySelector('.barra-segmentada-tramos')).toBeNull();
    expect(contenedor.querySelector('[data-zona="saldo-desconocido"]')).not.toBeNull();
    expect(contenedor.querySelector('.tarjeta-credito-total').textContent).toContain('— cr');
    ctrl.destruir();
  });
});

describe('la subasta propia', () => {
  test('se reconoce, no deja pujar y sale en «Tus publicaciones»', async () => {
    const api = apiFalsa({ listado: [subasta({ id: 'mia', esPropia: true })] });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();

    expect(contenedor.querySelector('.tarjeta-subasta .badge-propia')).not.toBeNull();

    ctrl.abrirDetalle('mia');
    await esperar();
    expect(contenedor.querySelector('.aviso-subasta-propia').textContent).toContain(
      'Es tu subasta',
    );
    expect(contenedor.querySelector('#btn-pujar-manual').disabled).toBe(true);
    expect(contenedor.querySelector('#btn-solicitar-compra').disabled).toBe(true);

    await ctrl.abrirMisSubastas();
    expect(contenedor.querySelector('.fila-publicacion[data-id="mia"]')).not.toBeNull();
    ctrl.destruir();
  });
});

describe('compra inmediata', () => {
  test('sin compra inmediata no se ofrece ni en la tarjeta ni en el detalle', async () => {
    const api = apiFalsa({ listado: [subasta({ compraInmediata: null })] });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();

    expect(contenedor.querySelector('.compra-ya-texto')).toBeNull();
    ctrl.abrirDetalle('sub-1');
    await esperar();
    expect(contenedor.querySelector('.bloque-compra-inmediata')).toBeNull();
    expect(contenedor.textContent).not.toContain('0 cr Comprarla');
    ctrl.destruir();
  });

  test('comprada, la subasta sale del listado y el «¡Es tuya!» se queda a la vista', async () => {
    const api = apiFalsa();
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    ctrl.abrirDetalle('sub-1');
    await esperar();
    // Tras comprar, el listado de abiertas ya no la trae.
    api.listar.mockImplementation(async () => []);

    ctrl.solicitarCompraInmediata();
    await ctrl.confirmarCompraInmediata();

    expect(api.comprarAhora).toHaveBeenCalledWith('sub-1');
    expect(ctrl.vista).toBe('detalle');
    const victoria = contenedor.querySelector('.cierre-victoria');
    expect(victoria).not.toBeNull();
    expect(victoria.textContent).toContain('2.800 cr');
    expect(contenedor.textContent).toContain('Hacha de Obsidiana');
    ctrl.destruir();
  });

  test('si la compra falla, no queda un «¡Es tuya!» ni un detalle huérfano', async () => {
    const api = apiFalsa({
      comprarAhora: jest.fn(async () => {
        throw new Error('Esta subasta ya se cerro. Actualiza para ver el resultado.');
      }),
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    ctrl.abrirDetalle('sub-1');
    await esperar();
    api.listar.mockImplementation(async () => []);

    await ctrl.confirmarCompraInmediata();

    expect(ctrl.resultadoCierre).toBeNull();
    expect(ctrl.subastaCerrada).toBeNull();
    expect(ctrl.vista).not.toBe('detalle');
    expect(contenedor.querySelector('#alerta-pujas').textContent).toContain('ya se cerro');
    ctrl.destruir();
  });

  test('?accion=comprar abre la confirmación, que sigue haciendo falta', async () => {
    const { contenedor, ctrl } = montar({
      api: apiFalsa(),
      subastaInicialId: 'sub-1',
      accionInicial: 'comprar',
    });
    await ctrl.iniciar();

    expect(ctrl.confirmandoCompra).toBe(true);
    expect(contenedor.querySelector('#modal-compra-inmediata')).not.toBeNull();
    expect(ctrl.api.comprarAhora).not.toHaveBeenCalled();
    ctrl.destruir();
  });

  test('?accion=comprar no abre nada si la subasta no admite compra inmediata', async () => {
    const { ctrl } = montar({
      api: apiFalsa({ listado: [subasta({ compraInmediata: null })] }),
      subastaInicialId: 'sub-1',
      accionInicial: 'comprar',
    });
    await ctrl.iniciar();

    expect(ctrl.confirmandoCompra).toBe(false);
    ctrl.destruir();
  });
});

describe('enlaces a una subasta', () => {
  test('una subasta de la segunda página se abre y la recarga no la cierra', async () => {
    const primera = Array.from({ length: 16 }, (_, i) => subasta({ id: `p0-${i}` }));
    const api = apiFalsa({
      listar: jest.fn(async ({ page = 0 } = {}) => {
        if (page === 0) {
          return JSON.parse(JSON.stringify(primera));
        }
        return page === 1 ? [subasta({ id: 'lejana', nombre: 'Yelmo del Vigía' })] : [];
      }),
    });
    const { ctrl } = montar({ api, subastaInicialId: 'lejana' });
    await ctrl.iniciar();

    expect(ctrl.vista).toBe('detalle');
    expect(ctrl.getSubastaActiva().nombre).toBe('Yelmo del Vigía');
    ctrl.destruir();
  });

  test('si no está en ninguna página, lo dice con qué hacer', async () => {
    const { ctrl } = montar({ api: apiFalsa(), subastaInicialId: 'no-existe' });
    await ctrl.iniciar();

    expect(ctrl.vista).not.toBe('detalle');
    expect(ctrl.mensajeError).toContain('Aquí tienes las que siguen en curso');
    ctrl.destruir();
  });
});

describe('te superaron en otra subasta', () => {
  test('el aviso cruzado sale en el camino real, no solo en el de las pruebas', async () => {
    const api = apiFalsa({
      listado: [subasta({ id: 'a' }), subasta({ id: 'b', nombre: 'Grebas del Centinela' })],
      participaciones: { b: participacion({ vasGanando: true, creditosRetenidos: '880' }) },
    });
    const { contenedor, ctrl } = montar({ api });
    await ctrl.iniciar();
    ctrl.abrirDetalle('a');
    await esperar();

    api.miParticipacion.mockImplementation(async (id) =>
      id === 'b' ? participacion({ teSuperaron: true }) : participacion(),
    );
    ctrl.alLlegarActualizacion({ id: 'b' });
    await esperar();
    await esperar();

    expect(ctrl.avisoCruzado?.id).toBe('b');
    expect(contenedor.querySelector('.toast-cruzado-flotante').textContent).toContain(
      'Grebas del Centinela',
    );
    ctrl.destruir();
  });
});

describe('sondeo sin canal (riesgo #7)', () => {
  test('relee sin canal, y no con la compra a medio confirmar ni con canal estable', async () => {
    const api = apiFalsa();
    const { ctrl } = montar({ api });
    await ctrl.iniciar();
    const antes = api.listar.mock.calls.length;

    await ctrl.sondear();
    expect(api.listar.mock.calls.length).toBe(antes + 1);

    ctrl.confirmandoCompra = true;
    expect(ctrl.sondear()).toBeNull();
    ctrl.confirmandoCompra = false;

    ctrl.estadoCanal = ESTADO_CANAL.ESTABLE;
    expect(ctrl.sondear()).toBeNull();
    ctrl.destruir();
  });

  test('destruir para el sondeo', () => {
    jest.useFakeTimers();
    try {
      const api = apiFalsa();
      const { ctrl } = montar({ api, sondeoMs: 5000 });
      ctrl.iniciarSondeo();
      expect(ctrl.sondeoId).not.toBeNull();
      ctrl.destruir();
      expect(ctrl.sondeoId).toBeNull();
    } finally {
      jest.useRealTimers();
    }
  });
});

describe('repintar no roba el foco', () => {
  test('quien escribe un monto sigue en el campo tras un repintado', async () => {
    const { contenedor, ctrl } = montar({ api: apiFalsa() });
    await ctrl.iniciar();
    ctrl.abrirDetalle('sub-1');
    await esperar();

    contenedor.querySelector('#input-monto-puja').focus();
    ctrl.render();

    expect(document.activeElement?.id).toBe('input-monto-puja');
    ctrl.destruir();
  });
});

describe('funciones puras', () => {
  test('sin saldo conocido no hay sobrecompromiso que avisar', () => {
    expect(verificarSobreCompromiso(null, [{ autoLimite: 4000 }]).sobreCompromiso).toBe(false);
    expect(verificarSobreCompromiso(3000, [{ autoLimite: 4000 }]).sobreCompromiso).toBe(true);
  });

  test('la espera de una participación corre con el reloj', () => {
    const ahora = 1_000_000;
    const vista = vistaDeParticipacion(
      { ganando: false, superado: true, retenido: 0, autoLimite: 0, esperaHasta: ahora + 3000 },
      ahora + 1000,
    );
    expect(vista.esperaSegundos).toBe(2);
    expect(vista.superado).toBe(true);
  });
});
