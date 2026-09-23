/**
 * El controlador contra la API real (HU-SUB-004).
 *
 * subastas.test.js cubre la vista con datos de ejemplo. Aqui se verifica lo
 * otro: que cuando hay un cliente de API, el controlador deje de simular y
 * pregunte al servidor, que es lo unico que sabe quien va ganando.
 */

import { jest } from '@jest/globals';
import { ControladorSubastas, CANAL_SUBASTAS } from './pujas.js';
import { ErrorDeSubastas } from './pujas-api.js';

function subastaDelServidor(extra = {}) {
  return Object.assign(
    {
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
      historial: [],
    },
    extra,
  );
}

function apiFalsa(sobrescribir = {}) {
  return Object.assign(
    {
      listar: jest.fn(async () => [subastaDelServidor()]),
      miResumen: jest.fn(async () => ({ creditosRetenidos: '0', subastasGanando: 0 })),
      historial: jest.fn(async () => []),
      miParticipacion: jest.fn(async () => ({
        vasGanando: false,
        teSuperaron: false,
        tuOfertaVigente: null,
        retenidoAqui: '0',
        limiteAutomatico: null,
        automaticaActiva: false,
        segundosParaVolverAPujar: 0,
      })),
      pujar: jest.fn(async () => ({ id: 'p1', estado: 'ACTIVA' })),
      comprarAhora: jest.fn(async () => ({ id: 'p2', estado: 'GANADORA' })),
      configurarAutomatica: jest.fn(async () => ({ id: 'a1', activa: true })),
      desactivarAutomatica: jest.fn(async () => null),
    },
    sobrescribir,
  );
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
      api: apiFalsa({ listar: jest.fn(async () => []) }),
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
        }),
      }),
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
        subastaDelServidor({ id: 'sub-2', nombre: 'Grebas del Centinela' }),
      ]),
    });
    const ctrl = new ControladorSubastas({
      contenedor: contenedor(),
      api,
      subastaInicialId: 'sub-2',
    });

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
  test('si la subasta del enlace ya no esta, lo dice en vez de abrir un detalle vacío', async () => {
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({
      contenedor: contenedor(),
      api,
      subastaInicialId: 'sub-que-ya-no-existe',
    });

    await ctrl.iniciar();

    expect(ctrl.vista).not.toBe('detalle');
    expect(ctrl.mensajeError).toContain('ya no está disponible');
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
    const ctrl = new ControladorSubastas({
      contenedor: contenedor(),
      api,
      subastaInicialId: 'sub-1',
    });
    await ctrl.iniciar();

    ctrl.volverALista();
    await ctrl.recargar();

    expect(ctrl.vista).not.toBe('detalle');
    ctrl.destruir();
  });
});

describe('datos propios del detalle', () => {
  /**
   * El listado es el mismo para todos: no puede decir si TU vas ganando. Antes
   * de tener /mi-participacion la pantalla mostraba siempre "no vas ganando",
   * aunque fueras el mejor postor.
   */
  test('al abrir el detalle se pregunta al servidor por la situacion propia', async () => {
    const api = apiFalsa({
      miParticipacion: jest.fn(async () => ({
        vasGanando: true,
        teSuperaron: false,
        tuOfertaVigente: '1450',
        retenidoAqui: '1450',
        limiteAutomatico: '2000',
        automaticaActiva: true,
        segundosParaVolverAPujar: 3,
      })),
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();

    await ctrl.cargarDetalle('sub-1');

    const sub = ctrl.subastas.find((s) => s.id === 'sub-1');
    expect(api.miParticipacion).toHaveBeenCalledWith('sub-1');
    expect(sub.ganando).toBe(true);
    expect(sub.retenido).toBe(1450);
    expect(sub.autoLimite).toBe(2000);
    expect(sub.esperaSegundos).toBe(3);
    ctrl.destruir();
  });

  test('el historial viene del servidor y marca cuales son tuyas', async () => {
    const api = apiFalsa({
      historial: jest.fn(async () => [
        {
          id: 'p1',
          monto: '1450',
          tipo: 'MANUAL',
          estado: 'ACTIVA',
          creadaEn: '2026-09-14T12:00:00Z',
          esTuya: true,
        },
        {
          id: 'p2',
          monto: '1400',
          tipo: 'AUTOMATICA',
          estado: 'SUPERADA',
          creadaEn: '2026-09-14T11:59:00Z',
          esTuya: false,
        },
      ]),
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();

    await ctrl.cargarDetalle('sub-1');

    const sub = ctrl.subastas.find((s) => s.id === 'sub-1');
    expect(sub.historial).toHaveLength(2);
    expect(sub.historial[0].esTu).toBe(true);
    expect(sub.historial[1].apodo).toBe('Otro jugador');
    ctrl.destruir();
  });

  /**
   * Una puja cambia si vas ganando y cuanto llevas retenido, y eso el listado
   * no lo refleja. Sin releer la parte propia, la pantalla se quedaria
   * diciendo lo de antes de pujar.
   */
  test('despues de pujar se relee tambien la situacion propia', async () => {
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();
    // Se puja DESDE el detalle: montar solo subastaActivaId dejaba un estado
    // que no ocurre, y la recarga no tenia por que traer el detalle.
    ctrl.abrirDetalle('sub-1');
    api.miParticipacion.mockClear();

    await ctrl.pujar(1400);

    expect(api.miParticipacion).toHaveBeenCalledWith('sub-1');
    ctrl.destruir();
  });

  /** Si el servidor no responde, el detalle se pinta igual: pujar importa mas. */
  test('un fallo al traer el detalle no rompe la pantalla', async () => {
    const api = apiFalsa({
      historial: jest.fn(async () => {
        throw new Error('sin red');
      }),
      miParticipacion: jest.fn(async () => {
        throw new Error('sin red');
      }),
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();

    await expect(ctrl.cargarDetalle('sub-1')).resolves.toBeUndefined();
    expect(ctrl.subastas).toHaveLength(1);
    ctrl.destruir();
  });
});

describe('nada inventado en pantalla', () => {
  /**
   * El saldo total y el disponible los sabe ms-finanzas, que no existe. La
   * pantalla mostraba 6.200 cr fijos, que no eran de nadie.
   */
  test('el retenido sale del servidor, no de un valor de ejemplo', async () => {
    const api = apiFalsa({
      miResumen: jest.fn(async () => ({ creditosRetenidos: '2750', subastasGanando: 2 })),
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });

    await ctrl.iniciar();

    expect(ctrl.getRetenidoReal()).toBe(2750);
    expect(ctrl.getSubastasGanando()).toBe(2);
    ctrl.destruir();
  });

  /**
   * Si no se sabe, no se pinta. Un cero se leeria como "no vas ganando en
   * ninguna", que es una afirmacion distinta de "no lo sabemos".
   */
  test('sin resumen del servidor no se afirma en cuantas vas ganando', async () => {
    const api = apiFalsa({
      miResumen: jest.fn(async () => {
        throw new Error('sin red');
      }),
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });

    await ctrl.iniciar();

    expect(ctrl.getSubastasGanando()).toBeNull();
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

  test('un rechazo del servidor se le enseña al jugador en el DOM sin bloquear', async () => {
    const alertaSpy = jest.spyOn(globalThis, 'alert').mockImplementation(() => {});
    const api = apiFalsa({
      pujar: jest.fn(async () => {
        throw new ErrorDeSubastas(
          'Alguien se te adelantó: la oferta ya subió. Revisa el nuevo mínimo.',
          { estado: 409, motivo: 'OFERTA_INSUFICIENTE' },
        );
      }),
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();
    ctrl.subastaActivaId = 'sub-1';

    const exito = await ctrl.pujar(1360);

    expect(exito).toBe(false);
    expect(alertaSpy).not.toHaveBeenCalled();
    const alerta = ctrl.contenedor.querySelector('#alerta-pujas');
    expect(alerta).not.toBeNull();
    expect(alerta.getAttribute('role')).toBe('alert');
    expect(alerta.hidden).toBe(false);
    expect(alerta.textContent).toContain('Alguien se te adelantó');
    alertaSpy.mockRestore();
    ctrl.destruir();
  });

  test('un monto que no es número ni llega al servidor y enseña aviso en el DOM', async () => {
    const alertaSpy = jest.spyOn(globalThis, 'alert').mockImplementation(() => {});
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();
    ctrl.subastaActivaId = 'sub-1';

    const exito = await ctrl.pujar(Number.NaN);

    expect(exito).toBe(false);
    expect(api.pujar).not.toHaveBeenCalled();
    expect(alertaSpy).not.toHaveBeenCalled();
    const alerta = ctrl.contenedor.querySelector('#alerta-pujas');
    expect(alerta).not.toBeNull();
    expect(alerta.getAttribute('role')).toBe('alert');
    expect(alerta.hidden).toBe(false);
    expect(alerta.textContent).toContain('Escribe un monto válido');
    alertaSpy.mockRestore();
    ctrl.destruir();
  });

  test('un nuevo intento limpia el mensaje de error previo del DOM', async () => {
    const api = apiFalsa();
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();
    ctrl.subastaActivaId = 'sub-1';

    await ctrl.pujar(Number.NaN);
    let alerta = ctrl.contenedor.querySelector('#alerta-pujas');
    expect(alerta.hidden).toBe(false);
    expect(alerta.textContent).toContain('Escribe un monto válido');

    await ctrl.pujar(1500);
    alerta = ctrl.contenedor.querySelector('#alerta-pujas');
    expect(alerta.hidden).toBe(true);
    expect(alerta.textContent).toBe('');
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
   * créditos por su cuenta.
   */
  test('no se manda una segunda peticion mientras la primera esta en vuelo', async () => {
    let resolver;
    const api = apiFalsa({
      pujar: jest.fn(
        () =>
          new Promise((r) => {
            resolver = r;
          }),
      ),
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

describe('canal en vivo (HU-SUB-011 publica, esta pantalla escucha)', () => {
  function canalFalso() {
    const suscripciones = [];
    return {
      cliente: {
        suscribir: (destino, alRecibir) => {
          suscripciones.push({ destino, alRecibir });
          return 'sub-1';
        },
        enviar: () => {},
        cerrar: jest.fn(),
      },
      suscripciones,
    };
  }

  function apiQueCuenta(subastas) {
    let llamadas = 0;
    return {
      api: {
        listar: async () => {
          llamadas += 1;
          return JSON.parse(JSON.stringify(subastas));
        },
        miResumen: async () => ({
          creditosRetenidos: '0',
          saldoDisponible: '1000',
          subastasGanando: 0,
        }),
        historial: async () => [],
        miParticipacion: async () => ({
          vasGanando: false,
          teSuperaron: false,
          creditosRetenidos: '0',
          automaticaActiva: false,
          segundosParaVolverAPujar: 0,
        }),
      },
      veces: () => llamadas,
    };
  }

  const SUBASTA = {
    id: 's1',
    nombre: 'Hacha',
    oferta: 100,
    compraInmediata: 500,
    segundosRestantes: 600,
    retenido: 0,
    ganando: false,
    superado: false,
    pujas: [],
    rareza: 'comun',
  };

  test('R9.6: el CONNECT del canal lleva el JWT de la sesion', async () => {
    // El navegador no puede poner cabeceras en el handshake del WebSocket, asi
    // que el token viaja en la cabecera `Authorization` del frame CONNECT.
    // Antes de R9.6 este canal era el unico de los cuatro de la casa que se
    // abria sin acreditar nada: cualquiera con la URL escuchaba el listado.
    const caja = document.createElement('div');
    const falso = canalFalso();
    const { api } = apiQueCuenta([SUBASTA]);
    let recibido = null;

    const ctrl = new ControladorSubastas({
      contenedor: caja,
      api,
      urlCanal: 'ws://servidor/api/v1/ws-subastas',
      conectarCanal: async (opciones) => {
        recibido = opciones;
        return falso.cliente;
      },
      leerToken: () => 'jwt-de-lyra',
    });
    await ctrl.iniciar();
    await ctrl.abrirCanalEnVivo();

    expect(recibido.cabeceras).toEqual({ Authorization: 'Bearer jwt-de-lyra' });
    ctrl.destruir();
  });

  test('R9.6: sin sesion se conecta igual, sin cabecera vacia', async () => {
    // El listado es publico: quien no ha entrado tiene derecho a verlo
    // actualizarse. Mandar `Bearer null` seria peor que no mandar nada.
    const caja = document.createElement('div');
    const falso = canalFalso();
    const { api } = apiQueCuenta([SUBASTA]);
    let recibido = null;

    const ctrl = new ControladorSubastas({
      contenedor: caja,
      api,
      urlCanal: 'ws://servidor/api/v1/ws-subastas',
      conectarCanal: async (opciones) => {
        recibido = opciones;
        return falso.cliente;
      },
      leerToken: () => null,
    });
    await ctrl.iniciar();
    await ctrl.abrirCanalEnVivo();

    expect(recibido.cabeceras).toEqual({});
    expect(ctrl.canal).toBe(falso.cliente);
    ctrl.destruir();
  });

  test('se suscribe al canal que publica el servidor', async () => {
    const caja = document.createElement('div');
    const falso = canalFalso();
    const { api } = apiQueCuenta([SUBASTA]);

    const ctrl = new ControladorSubastas({
      contenedor: caja,
      api,
      urlCanal: 'ws://servidor/api/v1/ws-subastas',
      conectarCanal: async () => falso.cliente,
    });
    await ctrl.iniciar();
    await ctrl.abrirCanalEnVivo();

    expect(falso.suscripciones[0].destino).toBe(CANAL_SUBASTAS);
    ctrl.destruir();
  });

  test('un cambio en una subasta que se esta mirando dispara una relectura', async () => {
    const caja = document.createElement('div');
    const falso = canalFalso();
    const { api, veces } = apiQueCuenta([SUBASTA]);

    const ctrl = new ControladorSubastas({
      contenedor: caja,
      api,
      urlCanal: 'ws://x/ws-subastas',
      conectarCanal: async () => falso.cliente,
    });
    await ctrl.iniciar();
    const antes = veces();

    // Se relee en vez de pintar lo que llega: el mensaje trae el resumen de la
    // subasta, pero no sabe si la puja es tuya ni cuanto llevas retenido.
    const atendido = ctrl.alLlegarActualizacion(JSON.stringify({ id: 's1', ofertaVigente: '150' }));

    expect(atendido).toBe(true);
    await Promise.resolve();
    expect(veces()).toBeGreaterThan(antes);
    ctrl.destruir();
  });

  test('un cambio en una subasta que no tengo no relee nada', async () => {
    const caja = document.createElement('div');
    const falso = canalFalso();
    const { api, veces } = apiQueCuenta([SUBASTA]);

    const ctrl = new ControladorSubastas({
      contenedor: caja,
      api,
      urlCanal: 'ws://x/ws-subastas',
      conectarCanal: async () => falso.cliente,
    });
    await ctrl.iniciar();
    const antes = veces();

    expect(ctrl.alLlegarActualizacion(JSON.stringify({ id: 'otra-que-no-miro' }))).toBe(false);
    expect(veces()).toBe(antes);
    ctrl.destruir();
  });

  test('un frame ilegible no rompe la pantalla', async () => {
    const caja = document.createElement('div');
    const { api } = apiQueCuenta([SUBASTA]);
    const ctrl = new ControladorSubastas({ contenedor: caja, api });

    expect(ctrl.alLlegarActualizacion('{esto no es json')).toBe(false);
  });

  /**
   * Lo que sostiene el riesgo #7 del acta: si el tiempo real no levanta, la
   * pantalla degrada a consulta periodica en vez de fallar.
   */
  test('si el canal no conecta, la pantalla sigue funcionando con el sondeo', async () => {
    const caja = document.createElement('div');
    const { api } = apiQueCuenta([SUBASTA]);

    const ctrl = new ControladorSubastas({
      contenedor: caja,
      api,
      urlCanal: 'ws://servidor-caido/ws-subastas',
      conectarCanal: async () => {
        throw new Error('ECONNREFUSED');
      },
    });
    await ctrl.iniciar();

    expect(await ctrl.abrirCanalEnVivo()).toBeNull();
    expect(ctrl.estadoDatos).toBe('exito');
    expect(ctrl.subastas).toHaveLength(1);
    ctrl.destruir();
  });

  test('destruir cierra el canal', async () => {
    const caja = document.createElement('div');
    const falso = canalFalso();
    const { api } = apiQueCuenta([SUBASTA]);

    const ctrl = new ControladorSubastas({
      contenedor: caja,
      api,
      urlCanal: 'ws://x/ws-subastas',
      conectarCanal: async () => falso.cliente,
    });
    await ctrl.iniciar();
    await ctrl.abrirCanalEnVivo();
    ctrl.destruir();

    expect(falso.cliente.cerrar).toHaveBeenCalled();
  });
});
