/**
 * El controlador contra la API real (HU-SUB-004).
 *
 * subastas.test.js cubre la vista con datos de ejemplo. Aqui se verifica lo
 * otro: que cuando hay un cliente de API, el controlador deje de simular y
 * pregunte al servidor, que es lo unico que sabe quien va ganando.
 */

import { jest } from '@jest/globals';
import { ControladorSubastas } from './pujas.js';
import { ErrorDeSubastas } from './pujas-api.js';

function subastaDelServidor(extra = {}) {
  return Object.assign({
    id: 'sub-1',
    nombre: 'Hacha de Obsidiana',
    tipo: 'Arma',
    descripcion: '',
    rareza: 'epica',
    nivel: 0,
    vendedor: 'kaelthas_vx',
    oferta: 1350,
    compraInmediata: 2800,
    mediaMercado: 0,
    segundosRestantes: 120,
    ganando: false,
    superado: false,
    autoLimite: 0,
    esperaSegundos: 0,
    retenido: 0,
    rival: null,
    rivales: 3,
    aporte: { poder: 0, vida: 0, defensa: 0 },
    historial: []
  }, extra);
}

function apiFalsa(sobrescribir = {}) {
  return Object.assign({
    listar: jest.fn(async () => [subastaDelServidor()]),
    pujar: jest.fn(async () => ({ id: 'p1', estado: 'ACTIVA' })),
    comprarAhora: jest.fn(async () => ({ id: 'p2', estado: 'GANADORA' })),
    configurarAutomatica: jest.fn(async () => ({ id: 'a1', activa: true })),
    desactivarAutomatica: jest.fn(async () => null)
  }, sobrescribir);
}

function contenedor() {
  const div = document.createElement('div');
  document.body.appendChild(div);
  return div;
}

afterEach(() => {
  document.body.innerHTML = '';
  jest.restoreAllMocks();
});

describe('carga inicial', () => {
  test('pinta lo que devuelve el servidor, no los datos de ejemplo', async () => {
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });

    await ctrl.iniciar();

    expect(api.listar).toHaveBeenCalled();
    expect(ctrl.subastas).toHaveLength(1);
    expect(ctrl.subastas[0].nombre).toBe('Hacha de Obsidiana');
    expect(ctrl.estadoDatos).toBe('exito');
    ctrl.destruir();
  });

  test('un listado vacio se distingue de un error', async () => {
    const ctrl = new ControladorSubastas({
      contenedor: contenedor(),
      api: apiFalsa({ listar: jest.fn(async () => []) })
    });

    await ctrl.iniciar();

    expect(ctrl.estadoDatos).toBe('vacio');
    ctrl.destruir();
  });

  test('si el servidor falla, la pantalla lo dice en vez de quedarse en blanco', async () => {
    const ctrl = new ControladorSubastas({
      contenedor: contenedor(),
      api: apiFalsa({
        listar: jest.fn(async () => {
          throw new ErrorDeSubastas('No se pudo contactar al servidor de subastas.', { estado: 0 });
        })
      })
    });

    await ctrl.iniciar();

    expect(ctrl.estadoDatos).toBe('error');
    expect(ctrl.mensajeError).toContain('No se pudo contactar');
    ctrl.destruir();
  });
});

describe('llegada desde el listado (HU-SUB-011)', () => {
  /**
   * El listado de Cristian navega a ./pujas.html?id=<uuid>, y esa pagina
   * traduce el parametro a subastaInicialId. Es un contrato entre dos modulos
   * de personas distintas: si el nombre del parametro o el comportamiento
   * cambian, el enlace deja de funcionar sin que falle nada visible.
   */
  test('abre directamente el detalle de la subasta que llega en el enlace', async () => {
    const api = apiFalsa({
      listar: jest.fn(async () => [
        subastaDelServidor({ id: 'sub-1' }),
        subastaDelServidor({ id: 'sub-2', nombre: 'Grebas del Centinela' })
      ])
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api, subastaInicialId: 'sub-2' });

    await ctrl.iniciar();

    expect(ctrl.vista).toBe('detalle');
    expect(ctrl.subastaActivaId).toBe('sub-2');
    ctrl.destruir();
  });

  /**
   * Un enlace a una subasta que ya se cerro o se adjudico. Abrir un detalle
   * vacio seria peor que decirlo: el jugador vendria de pulsar "Ver subasta"
   * y no entenderia que esta mirando.
   */
  test('si la subasta del enlace ya no esta, lo dice en vez de abrir un detalle vacio', async () => {
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({
      contenedor: contenedor(), api, subastaInicialId: 'sub-que-ya-no-existe'
    });

    await ctrl.iniciar();

    expect(ctrl.vista).not.toBe('detalle');
    expect(ctrl.mensajeError).toContain('ya no esta disponible');
    ctrl.destruir();
  });

  /** Sin id en la URL se entra por la lista, como siempre. */
  test('sin id en el enlace se queda en el listado', async () => {
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api: apiFalsa() });

    await ctrl.iniciar();

    expect(ctrl.vista).not.toBe('detalle');
    ctrl.destruir();
  });

  /**
   * El id solo vale para la primera carga. Si se quedara pegado, cada refresco
   * posterior devolveria al jugador al detalle aunque hubiera navegado a otro
   * sitio.
   */
  test('el id del enlace no reabre el detalle en cada recarga', async () => {
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api, subastaInicialId: 'sub-1' });
    await ctrl.iniciar();

    ctrl.volverALista();
    await ctrl.recargar();

    expect(ctrl.vista).not.toBe('detalle');
    ctrl.destruir();
  });
});

describe('acciones', () => {
  test('pujar llama al servidor y no toca la oferta por su cuenta', async () => {
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();
    ctrl.subastaActivaId = 'sub-1';

    await ctrl.pujar(1400);

    expect(api.pujar).toHaveBeenCalledWith('sub-1', 1400);
    // La oferta la fija el servidor dentro de su lock. Escribirla aqui pintaria
    // al jugador como ganador antes de saber si gano la carrera.
    expect(api.listar).toHaveBeenCalledTimes(2);
    ctrl.destruir();
  });

  test('un rechazo del servidor se le ensena al jugador', async () => {
    const alerta = jest.spyOn(globalThis, 'alert').mockImplementation(() => {});
    const api = apiFalsa({
      pujar: jest.fn(async () => {
        throw new ErrorDeSubastas('Alguien se te adelanto: la oferta ya subio. Revisa el nuevo minimo.',
          { estado: 409, motivo: 'OFERTA_INSUFICIENTE' });
      })
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();
    ctrl.subastaActivaId = 'sub-1';

    const exito = await ctrl.pujar(1360);

    expect(exito).toBe(false);
    expect(alerta).toHaveBeenCalledWith(expect.stringContaining('Alguien se te adelanto'));
    ctrl.destruir();
  });

  test('un monto que no es numero ni llega al servidor', async () => {
    jest.spyOn(globalThis, 'alert').mockImplementation(() => {});
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();
    ctrl.subastaActivaId = 'sub-1';

    await ctrl.pujar(Number.NaN);

    expect(api.pujar).not.toHaveBeenCalled();
    ctrl.destruir();
  });

  test('la compra inmediata confirma contra el servidor y marca el cierre', async () => {
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();
    ctrl.subastaActivaId = 'sub-1';
    ctrl.confirmandoCompra = true;

    await ctrl.confirmarCompraInmediata();

    expect(api.comprarAhora).toHaveBeenCalledWith('sub-1');
    expect(ctrl.resultadoCierre).toBe('comprada');
    ctrl.destruir();
  });

  test('configurar y desactivar la puja automatica van al servidor', async () => {
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();
    ctrl.subastaActivaId = 'sub-1';

    await ctrl.configurarAutoPuja(2000);
    await ctrl.desactivarAutoPuja();

    expect(api.configurarAutomatica).toHaveBeenCalledWith('sub-1', 2000);
    expect(api.desactivarAutomatica).toHaveBeenCalledWith('sub-1');
    ctrl.destruir();
  });

  /**
   * Dos clics seguidos en Pujar no pueden mandar dos pujas: cada una reserva
   * creditos por su cuenta.
   */
  test('no se manda una segunda peticion mientras la primera esta en vuelo', async () => {
    let resolver;
    const api = apiFalsa({
      pujar: jest.fn(() => new Promise((r) => { resolver = r; }))
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();
    ctrl.subastaActivaId = 'sub-1';

    const primera = ctrl.pujar(1400);
    const segunda = await ctrl.pujar(1450);

    expect(segunda).toBe(false);
    expect(api.pujar).toHaveBeenCalledTimes(1);
    resolver({});
    await primera;
    ctrl.destruir();
  });
});

describe('la subasta desaparece mientras se mira', () => {
  test('si ya no esta en el listado, vuelve a la lista en vez de dejar un detalle huerfano', async () => {
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();
    ctrl.vista = 'detalle';
    ctrl.subastaActivaId = 'sub-1';
    ctrl.origenVista = 'explorar';

    api.listar.mockImplementationOnce(async () => []);
    await ctrl.recargar();

    expect(ctrl.vista).toBe('explorar');
    expect(ctrl.subastaActivaId).toBeNull();
    ctrl.destruir();
  });
});
