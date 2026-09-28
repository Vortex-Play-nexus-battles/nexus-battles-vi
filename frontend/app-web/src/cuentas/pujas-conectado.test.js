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
        creditosRetenidos: '0',
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
        // B8 — el nombre del contrato (ms-subastas-pujas.yaml).
        creditosRetenidos: '1450',
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

  // Las dos pruebas que siguen existen porque el resto de este bloque abre el
  // canal a mano (`await ctrl.abrirCanalEnVivo()` despues de `iniciar()`), y
  // eso tapaba el fallo: en produccion nadie lo reabre. `recargar()` termina
  // llamando a `iniciarTemporizador()`, que llamaba a `destruir()`, que cierra
  // el canal. Resultado: el canal moria en la recarga del arranque, y el unico
  // mensaje en vivo que llegaba lo volvia a cerrar al releer. La pantalla
  // parecia tener tiempo real y no lo tenia.
  test('el canal sobrevive a la recarga del arranque', async () => {
    const falso = canalFalso();
    const { api } = apiQueCuenta([SUBASTA]);

    const ctrl = new ControladorSubastas({
      contenedor: document.createElement('div'),
      api,
      urlCanal: 'ws://x/ws-subastas',
      conectarCanal: async () => falso.cliente,
    });
    await ctrl.iniciar();
    await new Promise((resolver) => setTimeout(resolver, 0));

    expect(ctrl.canal).toBe(falso.cliente);
    ctrl.destruir();
  });

  test('un mensaje en vivo no cierra el canal por el que llego', async () => {
    const falso = canalFalso();
    const { api } = apiQueCuenta([SUBASTA]);

    const ctrl = new ControladorSubastas({
      contenedor: document.createElement('div'),
      api,
      urlCanal: 'ws://x/ws-subastas',
      conectarCanal: async () => falso.cliente,
    });
    await ctrl.iniciar();
    await ctrl.abrirCanalEnVivo();

    ctrl.alLlegarActualizacion({ id: 's1', oferta: 150 });
    await new Promise((resolver) => setTimeout(resolver, 0));

    expect(falso.cliente.cerrar).not.toHaveBeenCalled();
    expect(ctrl.canal).toBe(falso.cliente);
    ctrl.destruir();
  });

  // La pantalla no puede inventarse desenlaces. Lo arreglo Simon en FI-R1
  // (#654) y esta prueba lo fija desde el lado de HU-SUB-004, que es quien
  // sufre el defecto: `eventosCierre` tenia como valor por defecto
  // EVENTOS_CIERRE_DEFAULT y `pujas.html` monta sin pasarlo,
  // asi que en produccion la pestana «Cierre multiple» anunciaba siempre «3» y
  // al abrirla se leia «3 CERRARON · Ganaste 1, te superaron en 2», con el
  // Hacha adjudicada a andres_nv y derrotas contra thar_vex y valkyria_99.
  // Ninguna existe, y el consejo tactico mezclaba esas cifras con el saldo real.
  test('sin cierres del servidor no se inventa ninguno', async () => {
    const { api } = apiQueCuenta([SUBASTA]);
    const ctrl = new ControladorSubastas({
      contenedor: document.createElement('div'),
      api,
    });
    await ctrl.iniciar();

    expect(ctrl.eventosCierre).toEqual([]);
    ctrl.abrirCierreMultiple();
    const texto = ctrl.contenedor.textContent;
    expect(texto).not.toMatch(/Hacha de Obsidiana|Grebas del Centinela|Amuleto de Brasa/);
    expect(texto).not.toMatch(/thar_vex|valkyria_99/);
    expect(texto).not.toMatch(/CERRARON/);
    expect(texto).toMatch(/Todav[ií]a no hay resultados de cierre/);
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

// ---------------------------------------------------------------------- B8

describe('B8 — las reglas las dice el servidor', () => {
  const REGLAS = {
    duraciones: [
      { codigo: '24H', horas: 24, comision: '1' },
      { codigo: '48H', horas: 48, comision: '3' },
    ],
    incrementoMinimo: '25',
    incrementoMinimoConfigurado: true,
    maxSubastasActivasPorJugador: 10,
    maxPujasActivasPorJugador: 40,
    intervaloMinimoSegundos: 7,
    penalizacionCancelacionPorcentaje: 50,
    cancelacionProhibidaUltimasHoras: 6,
    recordatorioMinutosAntesDelCierre: 60,
    diasParaRecoger: 7,
    alVencerPendientes: 'ENTREGAR',
  };

  test('aplica el incremento, el intervalo y el tope de pujas de GET /subastas/reglas', async () => {
    const api = apiFalsa({ reglas: jest.fn(async () => REGLAS) });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });

    await ctrl.iniciar();

    expect(api.reglas).toHaveBeenCalled();
    expect(ctrl.config.incrementoMinimo).toBe(25);
    expect(ctrl.config.intervaloSegundos).toBe(7);
    expect(ctrl.config.maxPujasActivas).toBe(40);
    ctrl.destruir();
  });

  test('sin incremento configurado no inventa uno y el detalle dice DECISIÓN PO pendiente', async () => {
    const api = apiFalsa({
      reglas: jest.fn(async () => ({
        ...REGLAS,
        incrementoMinimo: null,
        incrementoMinimoConfigurado: false,
      })),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api });
    await ctrl.iniciar();

    ctrl.abrirDetalle('sub-1');
    await ctrl.cargarDetalle('sub-1');

    expect(ctrl.config.incrementoMinimo).toBeNull();
    expect(caja.querySelector('[data-decision-po="incremento-minimo"]').textContent).toContain(
      'DECISIÓN PO pendiente',
    );
    expect(caja.textContent).not.toContain('(+50)');
    ctrl.destruir();
  });

  test('si las reglas no llegan se sigue con el respaldo, sin incremento', async () => {
    const api = apiFalsa({
      reglas: jest.fn(async () => {
        throw new Error('sin red');
      }),
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });

    await ctrl.iniciar();

    expect(ctrl.config.incrementoMinimo).toBeNull();
    expect(ctrl.config.intervaloSegundos).toBe(5);
    ctrl.destruir();
  });
});

describe('B8 — la ficha de la subasta (GET /subastas/{id})', () => {
  test('la puja minima y el incremento de ESTA subasta salen de la ficha', async () => {
    const api = apiFalsa({
      ficha: jest.fn(async () => ({
        id: 'sub-1',
        estado: 'ACTIVA',
        ofertaVigente: '1350',
        pujaMinimaSiguiente: '1360',
        incrementoMinimo: '10',
        compraInmediataDisponible: true,
        cantidadPujas: 3,
        vendedorApodo: 'forjador',
      })),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api });
    await ctrl.iniciar();

    ctrl.abrirDetalle('sub-1');
    await ctrl.cargarDetalle('sub-1');

    expect(api.ficha).toHaveBeenCalledWith('sub-1');
    const atajo = caja.querySelector('.btn-atajo');
    expect(atajo.getAttribute('data-monto')).toBe('1360');
    expect(atajo.textContent).toContain('(+10)');
    expect(caja.textContent).toContain('Incremento mínimo entre pujas en esta subasta: 10 cr');
    ctrl.destruir();
  });

  test('la primera puja es el precio minimo, sin incremento', async () => {
    const api = apiFalsa({
      listar: jest.fn(async () => [subastaDelServidor({ oferta: 100, rivales: 0 })]),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api });
    await ctrl.iniciar();

    ctrl.abrirDetalle('sub-1');

    const atajo = caja.querySelector('.btn-atajo');
    expect(atajo.getAttribute('data-monto')).toBe('100');
    expect(atajo.textContent).toContain('precio mínimo');
    ctrl.destruir();
  });

  test('una compra inmediata superada por una puja se ofrece desactivada y lo explica', async () => {
    const api = apiFalsa({
      ficha: jest.fn(async () => ({
        id: 'sub-1',
        estado: 'ACTIVA',
        compraInmediataDisponible: false,
      })),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api });
    await ctrl.iniciar();

    ctrl.abrirDetalle('sub-1');
    await ctrl.cargarDetalle('sub-1');

    const boton = caja.querySelector('#btn-solicitar-compra');
    expect(boton.disabled).toBe(true);
    expect(boton.textContent).toContain('Ya no disponible');
    ctrl.destruir();
  });
});

describe('B8 — seguimiento, cancelación y pendientes de recoger', () => {
  const YO = 'aaaaaaaa-0000-0000-0000-00000000000a';

  /** Espera a que termine la operacion en curso (el clic la lanza sin await). */
  async function esperarOperacion(ctrl) {
    for (let i = 0; i < 50 && ctrl.enviando; i++) {
      await new Promise((resolver) => setTimeout(resolver, 0));
    }
  }

  function conSesion(extra = {}) {
    return {
      leerToken: () => 'token-de-prueba',
      leerUid: () => YO,
      ...extra,
    };
  }

  test('seguir y dejar de seguir llaman al servidor segun lo que diga mi participacion', async () => {
    const api = apiFalsa({
      seguir: jest.fn(async () => null),
      dejarDeSeguir: jest.fn(async () => null),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api, ...conSesion() });
    await ctrl.iniciar();
    ctrl.abrirDetalle('sub-1');
    await ctrl.cargarDetalle('sub-1');

    caja.querySelector('#btn-seguir').click();
    await esperarOperacion(ctrl);
    expect(api.seguir).toHaveBeenCalledWith('sub-1');

    api.miParticipacion.mockResolvedValue({ vasGanando: false, siguiendo: true });
    await ctrl.cargarDetalle('sub-1');
    expect(caja.querySelector('#btn-seguir').textContent).toContain('Dejar de seguir');
    await ctrl.alternarSeguimiento();
    expect(api.dejarDeSeguir).toHaveBeenCalledWith('sub-1');
    ctrl.destruir();
  });

  test('sin sesion no se ofrece seguir ni cancelar', async () => {
    const caja = contenedor();
    const ctrl = new ControladorSubastas({
      contenedor: caja,
      api: apiFalsa(),
      leerToken: () => null,
    });
    await ctrl.iniciar();
    ctrl.abrirDetalle('sub-1');

    expect(caja.querySelector('#btn-seguir')).toBeNull();
    expect(caja.querySelector('#btn-cancelar-subasta')).toBeNull();
    ctrl.destruir();
  });

  /** Una subasta propia que todavia se puede cancelar: sin pujas y a mas de 6 h del cierre. */
  const CANCELABLE = { vendedor: YO, rivales: 0, segundosRestantes: 7 * 3600 };

  test('el vendedor puede cancelar su subasta tras confirmar la penalizacion', async () => {
    const api = apiFalsa({
      listar: jest.fn(async () => [subastaDelServidor(CANCELABLE)]),
      cancelar: jest.fn(async () => ({ estado: 'CANCELADA', penalizacionCobrada: '1.50' })),
      reglas: jest.fn(async () => ({
        duraciones: [
          { codigo: '24H', horas: 24, comision: '1' },
          { codigo: '48H', horas: 48, comision: '3' },
        ],
        incrementoMinimoConfigurado: false,
        penalizacionCancelacionPorcentaje: 50,
        cancelacionProhibidaUltimasHoras: 6,
      })),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api, ...conSesion() });
    await ctrl.iniciar();
    ctrl.abrirDetalle('sub-1');

    caja.querySelector('#btn-cancelar-subasta').click();
    const modal = caja.querySelector('#modal-cancelar-subasta');
    expect(modal).not.toBeNull();
    expect(modal.textContent).toContain('50 %');
    expect(modal.textContent).toMatch(/0[.,]5 cr si era de 24 h/);
    expect(api.cancelar).not.toHaveBeenCalled();

    await ctrl.confirmarCancelacion();

    expect(api.cancelar).toHaveBeenCalledWith('sub-1');
    ctrl.destruir();
  });

  test('una subasta ajena no ofrece cancelar', async () => {
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api: apiFalsa(), ...conSesion() });
    await ctrl.iniciar();
    ctrl.abrirDetalle('sub-1');

    expect(caja.querySelector('#btn-cancelar-subasta')).toBeNull();
    ctrl.destruir();
  });

  test('con pujas registradas, cancelar se ofrece desactivado y dice por que', async () => {
    const api = apiFalsa({
      listar: jest.fn(async () => [subastaDelServidor({ ...CANCELABLE, rivales: 2 })]),
      cancelar: jest.fn(),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api, ...conSesion() });
    await ctrl.iniciar();
    ctrl.abrirDetalle('sub-1');

    const boton = caja.querySelector('#btn-cancelar-subasta');
    expect(boton.disabled).toBe(true);
    expect(boton.getAttribute('aria-describedby')).toBe('motivo-no-cancelable');
    expect(caja.querySelector('#motivo-no-cancelable').textContent).toMatch(/no se puede cancelar/);
    boton.click();
    expect(caja.querySelector('#modal-cancelar-subasta')).toBeNull();
    expect(api.cancelar).not.toHaveBeenCalled();
    ctrl.destruir();
  });

  test('en las ultimas 6 horas cancelar se ofrece desactivado', async () => {
    const api = apiFalsa({
      listar: jest.fn(async () => [
        subastaDelServidor({ ...CANCELABLE, segundosRestantes: 5 * 3600 }),
      ]),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api, ...conSesion() });
    await ctrl.iniciar();
    ctrl.abrirDetalle('sub-1');

    expect(caja.querySelector('#btn-cancelar-subasta').disabled).toBe(true);
    expect(caja.querySelector('#motivo-no-cancelable').textContent).toContain('últimas 6 horas');
    ctrl.destruir();
  });

  test('si el servidor rechaza la cancelacion, el motivo se ve en pantalla', async () => {
    // La carrera: la pantalla la veia cancelable, pero alguien pujo entre medias.
    const api = apiFalsa({
      listar: jest.fn(async () => [subastaDelServidor(CANCELABLE)]),
      cancelar: jest.fn(async () => {
        throw new ErrorDeSubastas('Ya hay pujas registradas: la subasta no se puede cancelar.', {
          estado: 409,
          motivo: 'CANCELACION_CON_PUJAS',
        });
      }),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api, ...conSesion() });
    await ctrl.iniciar();
    ctrl.abrirDetalle('sub-1');
    ctrl.solicitarCancelacion();

    await ctrl.confirmarCancelacion();

    expect(ctrl.mensajeError).toContain('no se puede cancelar');
    ctrl.destruir();
  });

  test('los pendientes de recoger se ven en Mis subastas y se recogen uno a uno o todos', async () => {
    const pendiente = {
      subastaId: 'sub-ganada',
      nombreProducto: 'Hacha de Obsidiana',
      montoPagado: '1350',
      venceEn: '2026-10-01T12:00:00Z',
      estado: 'PENDIENTE',
    };
    const api = apiFalsa({
      pendientes: jest.fn(async () => [pendiente]),
      recoger: jest.fn(async () => ({ ...pendiente, estado: 'RECOGIDO' })),
      recogerTodo: jest.fn(async () => ({ recogidos: [pendiente], fallidos: [] })),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api, ...conSesion() });
    await ctrl.iniciar();

    ctrl.abrirMisSubastas();
    expect(caja.textContent).toContain('Pendientes de recoger');
    expect(caja.textContent).toContain('Hacha de Obsidiana');

    caja.querySelector('.btn-recoger-pendiente').click();
    await esperarOperacion(ctrl);
    expect(api.recoger).toHaveBeenCalledWith('sub-ganada');

    await ctrl.recogerTodosLosPendientes();
    expect(api.recogerTodo).toHaveBeenCalled();
    ctrl.destruir();
  });

  test('«recoger todo» con fallos lo dice: lo que no se recogio sigue pendiente', async () => {
    const api = apiFalsa({
      pendientes: jest.fn(async () => []),
      recogerTodo: jest.fn(async () => ({
        recogidos: [],
        fallidos: [{ subastaId: 'x', detalle: 'no' }],
      })),
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api, ...conSesion() });
    await ctrl.iniciar();

    await ctrl.recogerTodosLosPendientes();

    expect(ctrl.mensajeError).toContain('siguen pendientes');
    ctrl.destruir();
  });

  test('sin subastas en curso los pendientes siguen a la vista', async () => {
    const api = apiFalsa({
      listar: jest.fn(async () => []),
      pendientes: jest.fn(async () => [
        {
          subastaId: 'sub-ganada',
          nombreProducto: 'Arco',
          montoPagado: '90',
          venceEn: '2026-10-01T12:00:00Z',
          estado: 'PENDIENTE',
        },
      ]),
      recoger: jest.fn(async () => ({})),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api, ...conSesion() });
    await ctrl.iniciar();

    expect(ctrl.estadoDatos).toBe('vacio');
    expect(caja.textContent).toContain('Pendientes de recoger');
    caja.querySelector('.btn-recoger-pendiente').click();
    await esperarOperacion(ctrl);
    expect(api.recoger).toHaveBeenCalledWith('sub-ganada');
    ctrl.destruir();
  });

  test('el nombre de un pendiente se pinta como texto, sin interpretar HTML', async () => {
    const api = apiFalsa({
      listar: jest.fn(async () => []),
      pendientes: jest.fn(async () => [
        {
          subastaId: 'sub-ganada',
          nombreProducto: '<img src=x onerror=alert(1)>',
          montoPagado: '90',
          venceEn: '2026-10-01T12:00:00Z',
          estado: 'PENDIENTE',
        },
      ]),
    });
    const caja = contenedor();
    const ctrl = new ControladorSubastas({ contenedor: caja, api, ...conSesion() });
    await ctrl.iniciar();

    expect(caja.querySelector('img')).toBeNull();
    expect(caja.textContent).toContain('<img src=x onerror=alert(1)>');
    ctrl.destruir();
  });

  test('el retenido acepta todavia el nombre viejo de un servidor sin actualizar', async () => {
    const api = apiFalsa({
      miParticipacion: jest.fn(async () => ({ vasGanando: true, retenidoAqui: '300' })),
    });
    const ctrl = new ControladorSubastas({ contenedor: contenedor(), api });
    await ctrl.iniciar();

    await ctrl.cargarDetalle('sub-1');

    expect(ctrl.subastas[0].retenido).toBe(300);
    ctrl.destruir();
  });
});
