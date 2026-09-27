/**
 * B6 — el adaptador de mensajes privados contra el servicio, con dobles.
 *
 * La red (`fetch`) y el canal STOMP son falsos: lo que se prueba es que el
 * adaptador cumple el puerto de la vista (`FuenteDeMensajes`) hablando los
 * contratos (`salas-partidas.yaml` 1.6.1, `mensajes-directos.yaml` 1.0.1 y
 * `ms-identidad-perfiles.yaml` 1.1.0). El servidor tiene sus propias pruebas
 * con STOMP de verdad (`MensajesDirectosIT`), y el banco E2E las dos cosas.
 * Los apodos y textos son datos de prueba.
 */

import { jest } from '@jest/globals';

import {
  COLA_DE_MENSAJES,
  FUENTE_SIN_SERVICIO,
  FalloDeMensajes,
  MensajesSinAbrir,
  RUTA_DE_CONVERSACIONES,
  RUTA_DE_PERFILES_PUBLICOS,
  destinoDeEnvio,
  falloPorMotivo,
  fuenteDeMensajes,
  fuenteHttpDeMensajes,
} from './fuente-mensajes.js';

const YO = 'aaaaaaaa-0000-4000-8000-000000000001';
const BRUMA = 'bbbbbbbb-0000-4000-8000-000000000002';
const KAEL = 'cccccccc-0000-4000-8000-000000000003';
const NYRA = 'dddddddd-0000-4000-8000-000000000004';
const APODOS = { [YO]: 'Simón', [BRUMA]: 'Bruma', [KAEL]: 'Kael', [NYRA]: 'Nyra' };

const TIPO = 'https://nexusbattles.local/errores/';

/** Un `MensajeDirecto` del contrato. */
function mensaje(id, de, a, texto, fecha, extra = {}) {
  return {
    id,
    conversacion: `dm:${[de, a].sort().join(':')}`,
    remitente: de,
    apodoRemitente: APODOS[de],
    destinatario: a,
    texto,
    fecha,
    leido: true,
    idCliente: null,
    ...extra,
  };
}

/** Un `ResumenDeConversacion` del contrato. */
function resumen(uidOtro, ultimoMensaje, noLeidos = 0, apodoOtro = APODOS[uidOtro]) {
  return { uidOtro, apodoOtro, ultimoMensaje, noLeidos };
}

function respuesta(status, cuerpo, cabeceras = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (nombre) => cabeceras[nombre] ?? null },
    json: async () => {
      if (cuerpo === undefined) {
        throw new SyntaxError('sin cuerpo');
      }
      return cuerpo;
    },
  };
}

/**
 * `fetch` falso: responde por «MÉTODO ruta» (sin la consulta), apunta cada
 * llamada y, lo que no conoce, lo contesta con 404.
 */
function redFalsa(rutas = {}) {
  const llamadas = [];
  const fetchImpl = jest.fn(async (url, opciones = {}) => {
    const metodo = (opciones.method ?? 'GET').toUpperCase();
    const [ruta, consulta = ''] = String(url).split('?');
    const llamada = {
      metodo,
      ruta,
      consulta: new URLSearchParams(consulta),
      cuerpo: opciones.body ? JSON.parse(opciones.body) : null,
    };
    llamadas.push(llamada);
    const manejador = rutas[`${metodo} ${ruta}`];
    if (manejador === undefined) {
      return respuesta(404, { status: 404 });
    }
    return typeof manejador === 'function' ? manejador(llamada) : manejador;
  });
  return { fetchImpl, llamadas, rutas };
}

/** Un cliente STOMP falso: apunta lo que se envía y deja «recibir» y «caer». */
function clienteFalso() {
  const cliente = {
    suscripciones: new Map(),
    enviados: [],
    alCerrar: null,
    alError: null,
    suscribir: jest.fn((destino, alRecibir) => {
      cliente.suscripciones.set(destino, alRecibir);
      return `sub-${cliente.suscripciones.size}`;
    }),
    enviar: jest.fn((destino, cuerpo) => {
      cliente.enviados.push({ destino, cuerpo });
    }),
    cerrar: jest.fn(),
    entregar(carga) {
      cliente.suscripciones.get(COLA_DE_MENSAJES)?.(carga);
    },
    caer() {
      cliente.alCerrar?.();
    },
  };
  return cliente;
}

/** El broker: cada conexión es un cliente nuevo; `fallar` rechaza las siguientes. */
function brokerFalso() {
  const broker = {
    clientes: [],
    fallar: 0,
    conectar: jest.fn(async () => {
      if (broker.fallar > 0) {
        broker.fallar -= 1;
        throw new Error('No se pudo abrir el canal.');
      }
      const cliente = clienteFalso();
      broker.clientes.push(cliente);
      return cliente;
    }),
    get actual() {
      return broker.clientes.at(-1);
    },
  };
  return broker;
}

/** Un reloj que solo avanza cuando la prueba lo dice. */
function relojFalso() {
  let ahora = 0;
  let siguiente = 1;
  const tareas = new Map();
  const programar = (fn, ms, cada) => {
    const id = siguiente;
    siguiente += 1;
    tareas.set(id, { fn, cuando: ahora + (Number(ms) || 0), cada });
    return id;
  };
  return {
    setTimeout: (fn, ms) => programar(fn, ms, null),
    setInterval: (fn, ms) => programar(fn, ms, ms),
    clearTimeout: (id) => tareas.delete(id),
    clearInterval: (id) => tareas.delete(id),
    get intervalos() {
      return [...tareas.values()].filter((t) => t.cada).length;
    },
    async avanzar(ms) {
      const hasta = ahora + ms;
      for (;;) {
        const [proxima] = [...tareas.entries()]
          .filter(([, t]) => t.cuando <= hasta)
          .sort(([, a], [, b]) => a.cuando - b.cuando);
        if (!proxima) {
          break;
        }
        const [id, tarea] = proxima;
        ahora = tarea.cuando;
        if (tarea.cada) {
          tarea.cuando += tarea.cada;
        } else {
          tareas.delete(id);
        }
        await tarea.fn();
        await vaciar();
      }
      ahora = hasta;
      await vaciar();
    },
  };
}

/** Deja correr todo lo pendiente (el reloj real no lo usa el adaptador). */
const vaciar = () => new Promise((resolver) => setTimeout(resolver, 0));

/** Un adaptador con todo falso. */
function adaptador({ red = redFalsa(), broker = brokerFalso(), ...resto } = {}) {
  const reloj = resto.reloj ?? relojFalso();
  let contador = 0;
  const fuente = fuenteHttpDeMensajes({
    fetchImpl: red.fetchImpl,
    miId: YO,
    base: '',
    conectar: broker.conectar,
    reloj,
    esperas: [1000, 1000],
    generarIdCliente: () => {
      contador += 1;
      return `id-${contador}`;
    },
    ...resto,
  });
  return { fuente, red, broker, reloj };
}

/** Escucha y guarda los eventos; espera a que el canal esté abierto. */
async function escuchando(fuente) {
  const eventos = [];
  const dejar = fuente.escuchar((evento) => eventos.push(evento));
  await vaciar();
  return { eventos, dejar };
}

const soloDe = (eventos, tipo) => eventos.filter((e) => e.tipo === tipo);

describe('fuenteDeMensajes: primero pregunta al servicio', () => {
  test('si contesta, el adaptador; y la bandeja de la pregunta es la primera que recibe la vista', async () => {
    const red = redFalsa({
      [`GET ${RUTA_DE_CONVERSACIONES}`]: respuesta(200, [
        resumen(BRUMA, mensaje('m1', BRUMA, YO, '¿Revancha?', '2026-09-22T15:00:00Z'), 1),
      ]),
    });
    const fuente = await fuenteDeMensajes({
      fetchImpl: red.fetchImpl,
      miId: YO,
      base: '',
      reloj: relojFalso(),
    });

    expect(fuente.disponible).toBe(true);
    const { conversaciones } = await fuente.bandeja();
    expect(conversaciones.map((c) => c.con.apodo)).toEqual(['Bruma']);
    expect(red.llamadas).toHaveLength(1);
    await fuente.bandeja();
    expect(red.llamadas).toHaveLength(2);
  });

  test.each([
    ['el borde da 502', () => respuesta(502, undefined)],
    ['el borde no conoce la ruta', () => respuesta(404, { status: 404 })],
    ['la sesión no vale', () => respuesta(401, undefined)],
    ['contesta una página y no el contrato', () => respuesta(200, undefined)],
    ['contesta algo que no es una lista', () => respuesta(200, { conversaciones: [] })],
    [
      'no hay red',
      () => {
        throw new TypeError('Failed to fetch');
      },
    ],
  ])('si %s, la fuente sin servicio', async (_caso, contestar) => {
    const fetchImpl = jest.fn(async () => contestar());
    const fuente = await fuenteDeMensajes({ fetchImpl, miId: YO, base: '', reloj: relojFalso() });
    expect(fuente).toBe(FUENTE_SIN_SERVICIO);
  });

  test('si no contesta a tiempo, corta la pregunta y da la fuente sin servicio', async () => {
    const reloj = relojFalso();
    let senal = null;
    const fetchImpl = jest.fn((_url, opciones) => {
      senal = opciones.signal;
      return new Promise(() => {});
    });
    const pedida = fuenteDeMensajes({ fetchImpl, miId: YO, base: '', reloj, esperaMs: 6000 });
    await reloj.avanzar(6000);

    await expect(pedida).resolves.toBe(FUENTE_SIN_SERVICIO);
    expect(senal.aborted).toBe(true);
  });

  test('sin uid de sesión no se sabe qué es tuyo: ni pregunta', async () => {
    const fetchImpl = jest.fn();
    await expect(fuenteDeMensajes({ fetchImpl, miId: null })).resolves.toBe(FUENTE_SIN_SERVICIO);
    await expect(fuenteDeMensajes({ fetchImpl, miId: '7' })).resolves.toBe(FUENTE_SIN_SERVICIO);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  test('la fuente sin servicio no toca la red y rechaza todo', async () => {
    expect(FUENTE_SIN_SERVICIO.disponible).toBe(false);
    await expect(FUENTE_SIN_SERVICIO.bandeja()).rejects.toBeInstanceOf(MensajesSinAbrir);
  });
});

describe('la bandeja', () => {
  test('cada conversación en la forma del puerto, la más reciente primero, y sin silencio que inventar', async () => {
    const red = redFalsa({
      [`GET ${RUTA_DE_CONVERSACIONES}`]: respuesta(200, [
        resumen(BRUMA, mensaje('m2', BRUMA, YO, '¿Revancha?', '2026-09-22T15:05:00Z'), 2),
        resumen(
          KAEL,
          mensaje('m1', YO, KAEL, 'Te paso el código', '2026-09-22T14:00:00Z'),
          0,
          null,
        ),
        { uidOtro: 'no-es-un-uid', noLeidos: 1 },
      ]),
    });
    const { fuente } = adaptador({ red });

    const bandeja = await fuente.bandeja();

    expect(bandeja.restriccion).toBeNull();
    expect(bandeja.conversaciones).toEqual([
      {
        id: BRUMA,
        con: { id: BRUMA, apodo: 'Bruma' },
        ultimo: { texto: '¿Revancha?', enviadoEn: '2026-09-22T15:05:00Z', deMi: false },
        noLeidos: 2,
        estado: 'ACTIVA',
      },
      {
        id: KAEL,
        con: { id: KAEL, apodo: 'Jugador' },
        ultimo: { texto: 'Te paso el código', enviadoEn: '2026-09-22T14:00:00Z', deMi: true },
        noLeidos: 0,
        estado: 'ACTIVA',
      },
    ]);
  });

  test('si no contesta, un fallo que se puede enseñar: sin códigos ni nombres internos', async () => {
    const red = redFalsa({ [`GET ${RUTA_DE_CONVERSACIONES}`]: respuesta(502, undefined) });
    const { fuente } = adaptador({ red });

    const fallo = await fuente.bandeja().catch((error) => error);
    expect(fallo).toBeInstanceOf(FalloDeMensajes);
    expect(fallo.detalle).toMatch(/no responde/);
    expect(fallo.detalle).not.toMatch(/502|salas|ms-/);
    expect(fallo.reintentable).toBe(true);
  });
});

describe('buscar jugadores', () => {
  test('por apodo, con los datos públicos del contrato', async () => {
    const red = redFalsa({
      [`GET ${RUTA_DE_PERFILES_PUBLICOS}`]: respuesta(200, [
        { uid: BRUMA, apodo: 'Bruma', avatar: null },
        { uid: 'no-es-un-uid', apodo: 'Raro' },
      ]),
    });
    const { fuente } = adaptador({ red });

    await expect(fuente.buscarJugadores('  bru ')).resolves.toEqual([
      { id: BRUMA, apodo: 'Bruma' },
    ]);
    expect(red.llamadas[0].consulta.get('apodo')).toBe('bru');
  });

  test('con menos de tres letras no pregunta y dice por qué', async () => {
    const { fuente, red } = adaptador();
    const fallo = await fuente.buscarJugadores('br').catch((error) => error);
    expect(fallo.detalle).toBe('Escribe al menos 3 letras del apodo.');
    expect(red.fetchImpl).not.toHaveBeenCalled();
  });

  test('más largo que cualquier apodo: nadie, sin preguntar', async () => {
    const { fuente, red } = adaptador();
    await expect(fuente.buscarJugadores('x'.repeat(51))).resolves.toEqual([]);
    expect(red.fetchImpl).not.toHaveBeenCalled();
  });

  test('si la búsqueda no responde, lo dice', async () => {
    const red = redFalsa({ [`GET ${RUTA_DE_PERFILES_PUBLICOS}`]: respuesta(500, undefined) });
    const { fuente } = adaptador({ red });
    const fallo = await fuente.buscarJugadores('bru').catch((error) => error);
    expect(fallo.detalle).toMatch(/búsqueda de jugadores no responde/);
  });
});

describe('abrir una conversación', () => {
  test('con quien ya está en la bandeja, esa misma', async () => {
    const red = redFalsa({
      [`GET ${RUTA_DE_CONVERSACIONES}`]: respuesta(200, [
        resumen(BRUMA, mensaje('m1', BRUMA, YO, 'hola', '2026-09-22T15:00:00Z'), 1),
      ]),
    });
    const { fuente } = adaptador({ red });
    const { conversaciones } = await fuente.bandeja();

    await expect(fuente.conversacionCon(BRUMA)).resolves.toEqual(conversaciones[0]);
  });

  test('con alguien de la búsqueda: vacía y con su apodo, sin tocar la red', async () => {
    const red = redFalsa({
      [`GET ${RUTA_DE_PERFILES_PUBLICOS}`]: respuesta(200, [{ uid: NYRA, apodo: 'Nyra' }]),
    });
    const { fuente } = adaptador({ red });
    await fuente.buscarJugadores('nyr');

    await expect(fuente.conversacionCon(NYRA)).resolves.toEqual({
      id: NYRA,
      con: { id: NYRA, apodo: 'Nyra' },
      ultimo: null,
      noLeidos: 0,
      estado: 'ACTIVA',
    });
    expect(red.llamadas).toHaveLength(1);
  });

  test('contigo mismo, o con algo que no es un jugador, no', async () => {
    const { fuente } = adaptador();
    await expect(fuente.conversacionCon(YO)).rejects.toMatchObject({
      motivo: 'DESTINATARIO_PROPIO',
    });
    await expect(fuente.conversacionCon('dm:x:y')).rejects.toMatchObject({
      detalle: 'Ese jugador no existe.',
    });
  });
});

describe('el hilo', () => {
  test('lo más reciente en la forma del puerto; lo tuyo se queda en «Enviado» aunque el servidor diga leido', async () => {
    const red = redFalsa({
      [`GET ${RUTA_DE_CONVERSACIONES}/${BRUMA}/mensajes`]: respuesta(200, [
        mensaje('m1', BRUMA, YO, '¿Revancha?', '2026-09-22T15:00:00Z'),
        mensaje('m2', YO, BRUMA, 'Hecho', '2026-09-22T15:01:00Z', { idCliente: 'id-x' }),
      ]),
    });
    const { fuente } = adaptador({ red });

    const { mensajes, hayMasAntiguos } = await fuente.hilo(BRUMA);

    expect(mensajes).toEqual([
      {
        id: 'm1',
        tipo: 'mensaje',
        autor: { id: BRUMA, apodo: 'Bruma' },
        texto: '¿Revancha?',
        enviadoEn: '2026-09-22T15:00:00Z',
      },
      {
        id: 'm2',
        tipo: 'mensaje',
        autor: { id: YO, apodo: 'Simón' },
        texto: 'Hecho',
        enviadoEn: '2026-09-22T15:01:00Z',
        entrega: 'ENVIADO',
      },
    ]);
    expect(hayMasAntiguos).toBe(false);
    expect(red.llamadas[0].consulta.get('limite')).toBe('50');
    expect(red.llamadas[0].consulta.has('antesDe')).toBe(false);
  });

  test('hacia atrás: la página anterior a un momento; si vino llena, puede haber más', async () => {
    const red = redFalsa({
      [`GET ${RUTA_DE_CONVERSACIONES}/${BRUMA}/mensajes`]: respuesta(200, [
        mensaje('m0', BRUMA, YO, 'antes', '2026-09-21T10:00:00Z'),
        mensaje('m00', YO, BRUMA, 'antes aún', '2026-09-21T10:01:00Z'),
      ]),
    });
    const { fuente } = adaptador({ red });

    const pagina = await fuente.hilo(BRUMA, { antesDe: '2026-09-22T15:00:00Z', limite: 2 });

    expect(pagina.mensajes.map((m) => m.id)).toEqual(['m0', 'm00']);
    expect(pagina.hayMasAntiguos).toBe(true);
    expect(red.llamadas[0].consulta.get('antesDe')).toBe('2026-09-22T15:00:00Z');
    expect(red.llamadas[0].consulta.get('limite')).toBe('2');
  });

  test('si no carga, un fallo con su porqué; una conversación que no es un jugador ni se pide', async () => {
    const { fuente, red } = adaptador();
    await expect(fuente.hilo(BRUMA)).rejects.toMatchObject({ detalle: expect.any(String) });
    await expect(fuente.hilo('c-bruma')).rejects.toMatchObject({
      detalle: 'Esa conversación no existe.',
    });
    expect(red.llamadas).toHaveLength(1);
  });
});

describe('enviar', () => {
  test('por el canal, con un idCliente; el eco confirma ESE envío y no se cuenta como mensaje nuevo', async () => {
    const { fuente, broker, red } = adaptador();
    const { eventos } = await escuchando(fuente);

    const enviado = fuente.enviar(BRUMA, '  gg  ');
    expect(broker.actual.enviados).toEqual([
      { destino: destinoDeEnvio(BRUMA), cuerpo: { texto: 'gg', idCliente: 'id-1' } },
    ]);
    expect(destinoDeEnvio(BRUMA)).toBe(`/app/mensajes-directos/${BRUMA}`);

    broker.actual.entregar({
      tipo: 'MENSAJE',
      ...mensaje('m9', YO, BRUMA, 'gg', '2026-09-22T16:00:00Z', { idCliente: 'id-1' }),
    });

    await expect(enviado).resolves.toMatchObject({
      id: 'm9',
      autor: { id: YO },
      texto: 'gg',
      entrega: 'ENVIADO',
    });
    expect(soloDe(eventos, 'mensaje')).toEqual([]);
    expect(red.llamadas.filter((l) => l.metodo === 'POST')).toEqual([]);
  });

  test('un rechazo por la cola llega a quien envió, con su motivo y su porqué', async () => {
    const { fuente, broker } = adaptador();
    await escuchando(fuente);

    const enviado = fuente.enviar(BRUMA, 'algo vetado');
    broker.actual.entregar({ tipo: 'RECHAZO', motivo: 'TEXTO_NO_PERMITIDO', idCliente: 'id-1' });

    const fallo = await enviado.catch((error) => error);
    expect(fallo).toBeInstanceOf(FalloDeMensajes);
    expect(fallo).toMatchObject({ motivo: 'TEXTO_NO_PERMITIDO', reintentable: false });
    expect(fallo.detalle).toMatch(/no están permitidas/);
  });

  test('un rechazo sin idCliente es del único envío en el aire', async () => {
    const { fuente, broker } = adaptador();
    await escuchando(fuente);

    const enviado = fuente.enviar(BRUMA, 'hola');
    broker.actual.entregar({ tipo: 'RECHAZO', motivo: 'TEXTO_INVALIDO', idCliente: null });

    await expect(enviado).rejects.toMatchObject({ motivo: 'TEXTO_INVALIDO' });
  });

  test('si el eco no llega a tiempo, sale por REST con el MISMO idCliente; el eco tardío no se cuenta', async () => {
    const guardado = mensaje('m7', YO, BRUMA, 'hola', '2026-09-22T16:00:00Z', {
      idCliente: 'id-1',
    });
    const red = redFalsa({
      [`POST ${RUTA_DE_CONVERSACIONES}/${BRUMA}/mensajes`]: respuesta(201, guardado),
    });
    const { fuente, broker, reloj } = adaptador({ red });
    const { eventos } = await escuchando(fuente);

    const enviado = fuente.enviar(BRUMA, 'hola');
    await reloj.avanzar(8000);

    await expect(enviado).resolves.toMatchObject({ id: 'm7', entrega: 'ENVIADO' });
    const post = red.llamadas.find((l) => l.metodo === 'POST');
    expect(post.cuerpo).toEqual({ texto: 'hola', idCliente: 'id-1' });

    broker.actual.entregar({ tipo: 'MENSAJE', ...guardado });
    expect(soloDe(eventos, 'mensaje')).toEqual([]);
  });

  test('sin canal, por REST', async () => {
    const red = redFalsa({
      [`POST ${RUTA_DE_CONVERSACIONES}/${BRUMA}/mensajes`]: ({ cuerpo }) =>
        respuesta(
          201,
          mensaje('m8', YO, BRUMA, cuerpo.texto, '2026-09-22T16:00:00Z', {
            idCliente: cuerpo.idCliente,
          }),
        ),
    });
    const { fuente } = adaptador({ red });

    await expect(fuente.enviar(BRUMA, 'sin canal')).resolves.toMatchObject({ id: 'm8' });
  });

  test.each([
    [422, 'contenido-bloqueado', {}, 'TEXTO_NO_PERMITIDO', false, /no están permitidas/],
    [403, 'jugador-sancionado', {}, 'SANCIONADO', false, /sanción activa/],
    [
      404,
      'destinatario-inexistente',
      {},
      'DESTINATARIO_INEXISTENTE',
      false,
      /no existe o no está activa/,
    ],
    [400, 'destinatario-propio', {}, 'DESTINATARIO_PROPIO', false, /a ti mismo/],
    [
      429,
      'demasiados-mensajes',
      { 'Retry-After': '4' },
      'DEMASIADO_RAPIDO',
      true,
      /espera 4 segundos/,
    ],
    [503, 'moderacion-no-disponible', {}, 'MODERACION_NO_DISPONIBLE', true, /no sale sin revisar/],
    [500, 'otro-fallo', {}, null, true, /No pudimos enviarlo/],
  ])(
    'un %i %s por REST se explica por su motivo',
    async (estado, tipo, cabeceras, motivo, reintentable, detalle) => {
      const red = redFalsa({
        [`POST ${RUTA_DE_CONVERSACIONES}/${BRUMA}/mensajes`]: respuesta(
          estado,
          { type: `${TIPO}${tipo}`, title: 'x', status: estado },
          cabeceras,
        ),
      });
      const { fuente } = adaptador({ red });

      const fallo = await fuente.enviar(BRUMA, 'hola').catch((error) => error);
      expect(fallo).toMatchObject({ motivo, reintentable, estado });
      expect(fallo.detalle).toMatch(detalle);
    },
  );

  test('lo que falló sin saber si llegó se reintenta con el mismo idCliente; un rechazo, con otro', async () => {
    const ids = [];
    let caida = true;
    const red = redFalsa({
      [`POST ${RUTA_DE_CONVERSACIONES}/${BRUMA}/mensajes`]: ({ cuerpo }) => {
        ids.push(cuerpo.idCliente);
        if (caida) {
          caida = false;
          throw new TypeError('Failed to fetch');
        }
        return respuesta(422, { type: `${TIPO}contenido-bloqueado`, status: 422 });
      },
    });
    const { fuente } = adaptador({ red });

    await expect(fuente.enviar(BRUMA, 'hola')).rejects.toMatchObject({ reintentable: true });
    await expect(fuente.enviar(BRUMA, 'hola')).rejects.toMatchObject({ reintentable: false });
    await expect(fuente.enviar(BRUMA, 'hola')).rejects.toBeInstanceOf(FalloDeMensajes);
    expect(ids).toEqual(['id-1', 'id-1', 'id-2']);
  });

  test('un texto vacío, demasiado largo o para ti mismo no sale', async () => {
    const { fuente, red } = adaptador();
    await expect(fuente.enviar(BRUMA, '   ')).rejects.toMatchObject({ motivo: 'TEXTO_INVALIDO' });
    await expect(fuente.enviar(BRUMA, 'x'.repeat(501))).rejects.toMatchObject({
      motivo: 'TEXTO_INVALIDO',
    });
    await expect(fuente.enviar(YO, 'hola')).rejects.toMatchObject({
      motivo: 'DESTINATARIO_PROPIO',
    });
    expect(red.fetchImpl).not.toHaveBeenCalled();
  });

  test('un motivo que no se conoce se trata como un fallo que se puede reintentar', () => {
    expect(falloPorMotivo('ALGO_NUEVO')).toMatchObject({
      motivo: 'ALGO_NUEVO',
      reintentable: true,
    });
    expect(falloPorMotivo('DEMASIADO_RAPIDO', { reintentarEnSegundos: 1 }).detalle).toMatch(
      /espera 1 segundo antes/,
    );
  });
});

describe('en vivo', () => {
  test('el canal abre, se suscribe a la cola y cuenta lo que llega, sin repetir', async () => {
    const { fuente, broker } = adaptador();
    const { eventos } = await escuchando(fuente);

    expect(broker.actual.suscribir).toHaveBeenCalledWith(COLA_DE_MENSAJES, expect.any(Function));
    expect(eventos[0]).toEqual({ tipo: 'canal', estado: 'conectado' });

    const deBruma = mensaje('m1', BRUMA, YO, '¿Vienes?', '2026-09-22T15:10:00Z');
    broker.actual.entregar({ tipo: 'MENSAJE', ...deBruma });
    broker.actual.entregar({ tipo: 'MENSAJE', ...deBruma });
    // Lo tuyo desde otra pestaña: su conversación es la del destinatario.
    broker.actual.entregar({
      tipo: 'MENSAJE',
      ...mensaje('m2', YO, KAEL, 'desde el móvil', '2026-09-22T15:11:00Z', { idCliente: 'otra' }),
    });
    // Ni tuyo ni para ti, o sin la forma del contrato: fuera.
    broker.actual.entregar({
      tipo: 'MENSAJE',
      ...mensaje('m3', BRUMA, KAEL, 'ajeno', '2026-09-22T15:12:00Z'),
    });
    broker.actual.entregar({ tipo: 'MENSAJE', id: 'm4' });
    broker.actual.entregar('basura');

    expect(soloDe(eventos, 'mensaje')).toEqual([
      {
        tipo: 'mensaje',
        conversacionId: BRUMA,
        mensaje: {
          id: 'm1',
          tipo: 'mensaje',
          autor: { id: BRUMA, apodo: 'Bruma' },
          texto: '¿Vienes?',
          enviadoEn: '2026-09-22T15:10:00Z',
        },
      },
      {
        tipo: 'mensaje',
        conversacionId: KAEL,
        mensaje: expect.objectContaining({
          id: 'm2',
          entrega: 'ENVIADO',
          autor: { id: YO, apodo: 'Simón' },
        }),
      },
    ]);
    expect(soloDe(eventos, 'leido')).toEqual([]);
  });

  test('una fecha que llega como número (segundos desde 1970) se entiende igual, en ISO-8601', async () => {
    const { fuente, broker } = adaptador();
    const { eventos } = await escuchando(fuente);

    broker.actual.entregar({
      tipo: 'MENSAJE',
      ...mensaje('m1', BRUMA, YO, 'con segundos', 1790000000.5),
    });
    broker.actual.entregar({
      tipo: 'MENSAJE',
      ...mensaje('m2', BRUMA, YO, 'sin fecha que valga', 'ayer'),
    });

    expect(soloDe(eventos, 'mensaje').map((e) => e.mensaje.enviadoEn)).toEqual([
      new Date(1790000000500).toISOString(),
    ]);
  });

  test('tras suscribirse se pone al día: lo que llegó entre la primera bandeja y la cola no se pierde', async () => {
    const viejo = mensaje('m1', BRUMA, YO, 'viejo', '2026-09-22T15:00:00Z');
    const nuevo = mensaje('m2', BRUMA, YO, 'nuevo', '2026-09-22T15:01:00Z');
    const red = redFalsa({
      [`GET ${RUTA_DE_CONVERSACIONES}`]: respuesta(200, [resumen(BRUMA, nuevo, 2)]),
      [`GET ${RUTA_DE_CONVERSACIONES}/${BRUMA}/mensajes`]: respuesta(200, [viejo, nuevo]),
    });
    const { fuente } = adaptador({ red, primeraBandeja: [resumen(BRUMA, viejo, 1)] });

    await fuente.bandeja();
    const { eventos } = await escuchando(fuente);

    expect(eventos.map((e) => e.estado ?? e.mensaje.id)).toEqual(['conectado', 'm2']);
  });

  test('se cae el canal: lo cuenta, pregunta por REST cada poco y, al volver, se pone al día ANTES de decir «reconectado»', async () => {
    const hola = mensaje('m1', BRUMA, YO, 'hola', '2026-09-22T15:00:00Z');
    const red = redFalsa({
      [`GET ${RUTA_DE_CONVERSACIONES}`]: respuesta(200, [resumen(BRUMA, hola, 1)]),
    });
    // El primer reintento del canal es al segundo; se pregunta cada 700 ms.
    const { fuente, broker, reloj } = adaptador({ red, intervaloDeSondeo: 700 });
    await fuente.bandeja();
    const { eventos } = await escuchando(fuente);
    const primero = broker.actual;

    primero.caer();
    expect(eventos.at(-1)).toEqual({ tipo: 'canal', estado: 'reconectando', intento: 1, de: 2 });
    expect(reloj.intervalos).toBe(1);

    // Mientras no hay canal, lo nuevo llega preguntando.
    const mientras = mensaje('m2', BRUMA, YO, '¿sigues?', '2026-09-22T15:02:00Z');
    red.rutas[`GET ${RUTA_DE_CONVERSACIONES}`] = respuesta(200, [resumen(BRUMA, mientras, 2)]);
    red.rutas[`GET ${RUTA_DE_CONVERSACIONES}/${BRUMA}/mensajes`] = respuesta(200, [hola, mientras]);
    await reloj.avanzar(700);
    expect(soloDe(eventos, 'mensaje').map((e) => e.mensaje.id)).toEqual(['m2']);

    // Lo que llega justo antes de volver se cuenta antes de «reconectado».
    const alVolver = mensaje('m3', BRUMA, YO, 'ya', '2026-09-22T15:03:00Z');
    red.rutas[`GET ${RUTA_DE_CONVERSACIONES}`] = respuesta(200, [resumen(BRUMA, alVolver, 3)]);
    red.rutas[`GET ${RUTA_DE_CONVERSACIONES}/${BRUMA}/mensajes`] = respuesta(200, [
      hola,
      mientras,
      alVolver,
    ]);
    await reloj.avanzar(300);

    const despues = eventos.slice(eventos.findIndex((e) => e.estado === 'reconectando'));
    expect(despues.map((e) => e.estado ?? e.mensaje.id)).toEqual([
      'reconectando',
      'm2',
      'm3',
      'reconectado',
    ]);
    // Con canal, se deja de preguntar; y la cola se pide en la conexión nueva.
    expect(reloj.intervalos).toBe(0);
    expect(broker.actual).not.toBe(primero);
    expect(broker.actual.suscribir).toHaveBeenCalledWith(COLA_DE_MENSAJES, expect.any(Function));
  });

  test('la primera conexión no sale: reintenta, se queda «sin conexión» y «Reintentar» la abre', async () => {
    const { fuente, broker, reloj } = adaptador();
    broker.fallar = 3;
    const { eventos } = await escuchando(fuente);
    await reloj.avanzar(2000);

    expect(eventos).toEqual([
      { tipo: 'canal', estado: 'reconectando', intento: 1, de: 2 },
      { tipo: 'canal', estado: 'reconectando', intento: 2, de: 2 },
      { tipo: 'canal', estado: 'sin-conexion', de: 2 },
    ]);
    expect(reloj.intervalos).toBe(1);

    fuente.reintentar();
    await reloj.avanzar(0);
    expect(eventos.at(-2)).toEqual({ tipo: 'canal', estado: 'reconectando', intento: 1, de: 2 });
    await reloj.avanzar(1000);
    expect(eventos.at(-1)).toEqual({ tipo: 'canal', estado: 'reconectado' });
    expect(broker.actual.suscribir).toHaveBeenCalledWith(COLA_DE_MENSAJES, expect.any(Function));
    expect(reloj.intervalos).toBe(0);
  });

  test('dejar de escuchar cierra el canal y ya no cuenta nada', async () => {
    const { fuente, broker, reloj } = adaptador();
    const { eventos, dejar } = await escuchando(fuente);
    const cliente = broker.actual;

    dejar();

    expect(cliente.cerrar).toHaveBeenCalled();
    cliente.entregar({
      tipo: 'MENSAJE',
      ...mensaje('m1', BRUMA, YO, 'tarde', '2026-09-22T15:00:00Z'),
    });
    expect(soloDe(eventos, 'mensaje')).toEqual([]);
    expect(reloj.intervalos).toBe(0);
  });
});

describe('lo que el servicio no tiene, dicho', () => {
  test('bloquear no está disponible: lo dice y no toca nada', async () => {
    const { fuente, red } = adaptador();
    const fallo = await fuente.bloquear(BRUMA, true).catch((error) => error);
    expect(fallo).toBeInstanceOf(FalloDeMensajes);
    expect(fallo.detalle).toMatch(/todavía no está disponible/);
    expect(fallo.reintentable).toBe(false);
    expect(red.fetchImpl).not.toHaveBeenCalled();
  });

  test('marcar leída es el POST de leído de esa conversación', async () => {
    const red = redFalsa({
      [`POST ${RUTA_DE_CONVERSACIONES}/${BRUMA}/leido`]: respuesta(204, undefined),
    });
    const { fuente } = adaptador({ red });
    await expect(fuente.marcarLeida(BRUMA)).resolves.toBeUndefined();
    expect(red.llamadas).toEqual([expect.objectContaining({ metodo: 'POST' })]);
  });
});
