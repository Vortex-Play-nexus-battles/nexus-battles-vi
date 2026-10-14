/**
 * Canal en tiempo real del asistente — 7.4.9 (`contracts/websocket/chatbot.yaml`
 * 1.0.0), con un transporte STOMP falso y un reloj a mano.
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from './cliente-chatbot.js';
import {
  CUERPO_MAXIMO_POR_EL_CANAL,
  DESTINOS_DEL_CHATBOT,
  LATIDOS_DEL_CANAL,
  PLAZOS_DEL_CANAL,
  crearCanalChatbot,
  urlDelCanalDelChatbot,
} from './canal-chatbot.js';
import { armarFrame, conectarStomp, leerFrame } from './transporte-stomp.js';

const BOT = {
  id: 'm-bot-1',
  remitente: 'BOT',
  contenido: 'Puedes pujar desde la sección Subasta.',
  adjuntoUrl: null,
  fechaEnvio: '2026-10-14T18:00:00Z',
};
const POR_HTTP = { ...BOT, id: 'm-bot-http' };
const VISITANTE = 'visitante-0123';
const SUSCRIPCIONES = [
  DESTINOS_DEL_CHATBOT.estado,
  DESTINOS_DEL_CHATBOT.respuestas,
  DESTINOS_DEL_CHATBOT.errores,
  DESTINOS_DEL_CHATBOT.tickets,
];

/** Deja correr las promesas pendientes. */
async function vaciar() {
  for (let i = 0; i < 10; i += 1) {
    await Promise.resolve();
  }
}

/** Un reloj que solo avanza cuando la prueba lo dice. */
function relojFalso() {
  let ahora = 0;
  let siguiente = 1;
  const tareas = new Map();
  return {
    setTimeout(funcion, ms) {
      const id = siguiente;
      siguiente += 1;
      tareas.set(id, { funcion, vence: ahora + ms });
      return id;
    },
    clearTimeout(id) {
      tareas.delete(id);
    },
    async avanzar(ms) {
      ahora += ms;
      let vencida = true;
      while (vencida) {
        vencida = false;
        const orden = [...tareas.entries()].sort((a, b) => a[1].vence - b[1].vence);
        for (const [id, tarea] of orden) {
          if (tarea.vence <= ahora && tareas.has(id)) {
            tareas.delete(id);
            vencida = true;
            await tarea.funcion();
            await vaciar();
          }
        }
      }
      await vaciar();
    },
  };
}

/** Un cliente STOMP falso: lo que manda la ventana y lo que manda el «servidor». */
function clienteStompFalso() {
  const suscripciones = new Map();
  const cliente = {
    alCerrar: null,
    alError: null,
    cerrado: false,
    enviados: [],
    suscripciones,
    suscribir: jest.fn((destino, alRecibir) => {
      suscripciones.set(destino, alRecibir);
      return `sub-${suscripciones.size}`;
    }),
    enviar: jest.fn((destino, cuerpo) => {
      cliente.enviados.push({ destino, cuerpo });
    }),
    cerrar: jest.fn(() => {
      cliente.cerrado = true;
    }),
    entregar(destino, cuerpo, cabeceras = {}) {
      suscripciones.get(destino)?.(cuerpo, cabeceras);
    },
    caer() {
      cliente.alCerrar?.();
    },
  };
  return cliente;
}

function conexiones({ fallos = 0 } = {}) {
  let quedan = fallos;
  const abiertas = [];
  const llamadas = [];
  const conectar = jest.fn(async ({ url, cabeceras }) => {
    llamadas.push({ url, cabeceras });
    if (quedan > 0) {
      quedan -= 1;
      throw new Error('No se pudo abrir el canal.');
    }
    const cliente = clienteStompFalso();
    abiertas.push(cliente);
    return cliente;
  });
  return {
    conectar,
    abiertas,
    llamadas,
    get ultima() {
      return abiertas.at(-1);
    },
  };
}

function almacenFalso(valores = {}) {
  const datos = new Map(Object.entries(valores));
  return {
    getItem: (clave) => datos.get(clave) ?? null,
    setItem: (clave, valor) => datos.set(clave, String(valor)),
  };
}

function preparar({ sesion = () => ({ autenticado: false }), fallos = 0, clienteHttp } = {}) {
  const red = conexiones({ fallos });
  const reloj = relojFalso();
  let correlacion = 0;
  const cliente = clienteHttp ?? { enviarMensaje: jest.fn(async () => POR_HTTP) };
  const ventana = { addEventListener: jest.fn(), removeEventListener: jest.fn() };
  const canal = crearCanalChatbot({
    cliente,
    conectar: red.conectar,
    sesion,
    almacen: almacenFalso({ 'nexus.chatbot.sesionAnonima': VISITANTE }),
    almacenLocal: almacenFalso(),
    ubicacion: { href: 'https://juego.example/plataforma/inicio.html' },
    reloj,
    esperas: [1000, 2000],
    ventana,
    generarId: () => {
      correlacion += 1;
      return `c-${correlacion}`;
    },
  });
  return { canal, red, reloj, cliente, ventana };
}

async function abierto(opciones) {
  const prueba = preparar(opciones);
  await prueba.canal.abrir();
  await vaciar();
  return prueba;
}

describe('url y CONNECT', () => {
  test('la URL es la de la API del asistente con ws, o wss bajo https', () => {
    expect(urlDelCanalDelChatbot(almacenFalso(), { href: 'https://juego.example/x/y.html' })).toBe(
      'wss://juego.example/api/v1/ws-chatbot',
    );
    expect(
      urlDelCanalDelChatbot(almacenFalso(), { href: 'http://localhost:8080/inicio.html' }),
    ).toBe('ws://localhost:8080/api/v1/ws-chatbot');
    const local = almacenFalso({ 'nexus.chatbot.base': 'http://localhost:8094/' });
    expect(urlDelCanalDelChatbot(local, { href: 'http://localhost:8080/' })).toBe(
      'ws://localhost:8094/api/v1/ws-chatbot',
    );
  });

  test('un visitante conecta con su sesión de visitante y sin token; un jugador, con su token', async () => {
    const visitante = await abierto();
    expect(visitante.red.llamadas[0]).toEqual({
      url: 'wss://juego.example/api/v1/ws-chatbot',
      cabeceras: { 'heart-beat': LATIDOS_DEL_CANAL, 'X-Id-Sesion-Anonima': VISITANTE },
    });

    const jugador = await abierto({ sesion: () => ({ autenticado: true, token: 'jwt-ana' }) });
    expect(jugador.red.llamadas[0].cabeceras).toEqual({
      'heart-beat': '0,10000',
      'X-Id-Sesion-Anonima': VISITANTE,
      Authorization: 'Bearer jwt-ana',
    });
  });

  test('por el transporte real, el CONNECT lleva esas cabeceras y pide los latidos del servidor', async () => {
    const sockets = [];
    class SocketFalso {
      constructor(url) {
        this.url = url;
        this.enviados = [];
        sockets.push(this);
      }

      send(texto) {
        this.enviados.push(leerFrame(texto));
      }

      close() {
        this.onclose?.();
      }
    }
    const canal = crearCanalChatbot({
      cliente: { enviarMensaje: jest.fn() },
      conectar: (opciones) => conectarStomp({ ...opciones, WebSocketImpl: SocketFalso }),
      sesion: () => ({ autenticado: true, token: 'jwt-ana' }),
      almacen: almacenFalso({ 'nexus.chatbot.sesionAnonima': VISITANTE }),
      almacenLocal: almacenFalso(),
      ubicacion: { href: 'http://localhost:8080/' },
      reloj: relojFalso(),
      ventana: null,
    });
    const abriendo = canal.abrir();
    await vaciar();
    const [socket] = sockets;
    socket.onopen();
    expect(socket.url).toBe('ws://localhost:8080/api/v1/ws-chatbot');
    expect(socket.enviados[0]).toEqual({
      comando: 'CONNECT',
      cabeceras: {
        'accept-version': '1.2',
        'heart-beat': '0,10000',
        'X-Id-Sesion-Anonima': VISITANTE,
        Authorization: 'Bearer jwt-ana',
      },
      cuerpo: '',
    });
    socket.onmessage({
      data: armarFrame('CONNECTED', { version: '1.2', 'heart-beat': '10000,0' }),
    });
    await abriendo;
    expect(canal.enVivo).toBe(true);
    expect(socket.enviados.slice(1).map((frame) => frame.cabeceras.destination)).toEqual(
      SUSCRIPCIONES,
    );
  });

  test('se suscribe a los cuatro destinos de usuario del contrato y a nada más', async () => {
    const { red } = await abierto();
    expect([...red.ultima.suscripciones.keys()]).toEqual(SUSCRIPCIONES);
  });

  test('abrir dos veces no abre dos conexiones', async () => {
    const { canal, red } = preparar();
    canal.abrir();
    canal.abrir();
    await vaciar();
    await canal.abrir();
    expect(red.conectar).toHaveBeenCalledTimes(1);
  });
});

describe('preguntar por el canal', () => {
  test('manda la pregunta con su correlación y resuelve con el Mensaje; «escribiendo…» con ESCRIBIENDO', async () => {
    const { canal, red, cliente } = await abierto();
    const alEscribiendo = jest.fn();

    const respuesta = canal.enviarMensaje('como pujo', { vista: 'SUBASTAS', alEscribiendo });
    expect(red.ultima.enviados).toEqual([
      {
        destino: '/app/chat/mensajes',
        cuerpo: { contenido: 'como pujo', idCorrelacion: 'c-1', vista: 'SUBASTAS' },
      },
    ]);
    expect(alEscribiendo).not.toHaveBeenCalled();

    red.ultima.entregar(
      DESTINOS_DEL_CHATBOT.estado,
      { estado: 'ESCRIBIENDO', idCorrelacion: 'c-1' },
      { 'id-correlacion': 'c-1' },
    );
    expect(alEscribiendo).toHaveBeenCalledTimes(1);

    red.ultima.entregar(DESTINOS_DEL_CHATBOT.respuestas, BOT, { 'id-correlacion': 'c-1' });
    await expect(respuesta).resolves.toEqual(BOT);
    expect(cliente.enviarMensaje).not.toHaveBeenCalled();
  });

  test('sin vista, el cuerpo no la lleva', async () => {
    const { canal, red } = await abierto();
    canal.enviarMensaje('hola');
    expect(red.ultima.enviados[0].cuerpo).toEqual({ contenido: 'hola', idCorrelacion: 'c-1' });
  });

  test('la respuesta de otra correlación no contesta esta pregunta', async () => {
    const { canal, red } = await abierto();
    const resuelta = jest.fn();
    canal.enviarMensaje('hola').then(resuelta);

    red.ultima.entregar(
      DESTINOS_DEL_CHATBOT.respuestas,
      { ...BOT, id: 'ajena' },
      {
        'id-correlacion': 'c-99',
      },
    );
    red.ultima.entregar(DESTINOS_DEL_CHATBOT.respuestas, { ...BOT, id: 'sin-correlacion' });
    await vaciar();
    expect(resuelta).not.toHaveBeenCalled();

    red.ultima.entregar(DESTINOS_DEL_CHATBOT.respuestas, BOT, { 'id-correlacion': 'c-1' });
    await vaciar();
    expect(resuelta).toHaveBeenCalledWith(BOT);
  });

  test('un error del servidor llega como el ErrorDelChatbot de la API, con su motivo', async () => {
    const { canal, red, cliente } = await abierto();
    const pregunta = canal.enviarMensaje('hola');
    red.ultima.entregar(
      DESTINOS_DEL_CHATBOT.errores,
      {
        type: 'about:blank',
        title: 'Too Many Requests',
        status: 429,
        detail: 'Demasiadas peticiones seguidas.',
        instance: '/app/chat/mensajes',
        motivo: 'LIMITE_DE_FRECUENCIA',
        reintentarEn: 12,
        idCorrelacion: 'c-1',
      },
      { 'id-correlacion': 'c-1' },
    );

    const error = await pregunta.catch((e) => e);
    expect(error).toBeInstanceOf(ErrorDelChatbot);
    expect(error.estado).toBe(429);
    expect(error.problema.motivo).toBe('LIMITE_DE_FRECUENCIA');
    expect(error.problema.reintentarEn).toBe(12);
    expect(error.noDisponible).toBe(false);
    // Un error del servidor no se repite por HTTP: ya contestó.
    expect(cliente.enviarMensaje).not.toHaveBeenCalled();
  });

  test('un 503 sin moderación se ve como «no disponible», igual que por HTTP', async () => {
    const { canal, red } = await abierto();
    const pregunta = canal.enviarMensaje('hola');
    red.ultima.entregar(DESTINOS_DEL_CHATBOT.errores, {
      title: 'Service Unavailable',
      status: 503,
      motivo: 'MODERACION_NO_DISPONIBLE',
      idCorrelacion: 'c-1',
    });
    const error = await pregunta.catch((e) => e);
    expect(error.estado).toBe(503);
    expect(error.noDisponible).toBe(true);
  });
});

describe('degradación controlada (riesgo #7)', () => {
  test('sin abrir el canal, la pregunta va por HTTP y «escribiendo…» sale en el acto', async () => {
    const { canal, cliente } = preparar();
    const alEscribiendo = jest.fn();

    await expect(canal.enviarMensaje('hola', { vista: 'INICIO', alEscribiendo })).resolves.toEqual(
      POR_HTTP,
    );
    expect(cliente.enviarMensaje).toHaveBeenCalledWith('hola', { vista: 'INICIO' });
    expect(alEscribiendo).toHaveBeenCalledTimes(1);

    await canal.enviarMensaje('otra');
    expect(cliente.enviarMensaje).toHaveBeenLastCalledWith('otra');
  });

  test('si el canal no abre, todo va por HTTP mientras reintenta; al abrir, por el canal', async () => {
    const { canal, red, reloj, cliente } = preparar({ fallos: 1 });
    const estados = [];
    canal.alEstado((estado) => estados.push(estado.estado));
    await canal.abrir();
    await vaciar();

    expect(canal.enVivo).toBe(false);
    await expect(canal.enviarMensaje('hola')).resolves.toEqual(POR_HTTP);
    expect(estados).toEqual(['conectando', 'reconectando']);

    await reloj.avanzar(1000);
    expect(red.conectar).toHaveBeenCalledTimes(2);
    expect(canal.enVivo).toBe(true);
    // Las suscripciones se hacen en la conexión que sí abrió.
    expect([...red.ultima.suscripciones.keys()]).toEqual(SUSCRIPCIONES);
    expect(estados.at(-1)).toBe('reconectado');

    canal.enviarMensaje('otra');
    expect(red.ultima.enviados).toHaveLength(1);
    expect(cliente.enviarMensaje).toHaveBeenCalledTimes(1);
  });

  test('si el servidor no acusa la pregunta a tiempo, va por HTTP y el canal se rehace', async () => {
    const { canal, red, reloj, cliente } = await abierto();
    const alEscribiendo = jest.fn();
    const pregunta = canal.enviarMensaje('hola', { alEscribiendo });

    await reloj.avanzar(PLAZOS_DEL_CANAL.acuseMs);

    await expect(pregunta).resolves.toEqual(POR_HTTP);
    expect(cliente.enviarMensaje).toHaveBeenCalledWith('hola');
    expect(alEscribiendo).toHaveBeenCalledTimes(1);
    expect(red.abiertas[0].cerrar).toHaveBeenCalled();
    expect(red.conectar).toHaveBeenCalledTimes(2);
  });

  test('con el acuse, espera la respuesta; si no llega a tiempo, va por HTTP', async () => {
    const { canal, red, reloj, cliente } = await abierto();
    const pregunta = canal.enviarMensaje('hola');
    red.ultima.entregar(
      DESTINOS_DEL_CHATBOT.estado,
      { estado: 'ESCRIBIENDO' },
      {
        'id-correlacion': 'c-1',
      },
    );

    // El plazo del acuse ya no cuenta: hay acuse.
    await reloj.avanzar(PLAZOS_DEL_CANAL.acuseMs);
    expect(cliente.enviarMensaje).not.toHaveBeenCalled();

    await reloj.avanzar(PLAZOS_DEL_CANAL.respuestaMs);
    await expect(pregunta).resolves.toEqual(POR_HTTP);
  });

  test('si el canal se cae con la pregunta en vuelo, va por HTTP: nunca se queda esperando', async () => {
    const { canal, red, cliente } = await abierto();
    const pregunta = canal.enviarMensaje('hola', { vista: 'TORNEOS' });

    red.ultima.caer();

    await expect(pregunta).resolves.toEqual(POR_HTTP);
    expect(cliente.enviarMensaje).toHaveBeenCalledWith('hola', { vista: 'TORNEOS' });
    expect(canal.enVivo).toBe(false);
  });

  test('una pregunta que el JSON alarga demasiado va por HTTP: el servidor cortaría la conexión', async () => {
    const { canal, red, cliente } = await abierto();
    // 4000 caracteres, el máximo del contrato, pero cada uno el JSON lo escribe con seis.
    const larga = '\u0001'.repeat(4000);
    expect(JSON.stringify({ contenido: larga }).length).toBeGreaterThan(CUERPO_MAXIMO_POR_EL_CANAL);

    await expect(canal.enviarMensaje(larga)).resolves.toEqual(POR_HTTP);
    expect(cliente.enviarMensaje).toHaveBeenCalledWith(larga);
    expect(red.ultima.enviados).toEqual([]);

    // 4000 comillas (el doble al escaparlas) todavía caben y van por el canal.
    canal.enviarMensaje('"'.repeat(4000), { vista: 'INVENTARIO' });
    expect(red.ultima.enviados).toHaveLength(1);
  });

  test('si HTTP también falla, el error llega a la ventana', async () => {
    const caido = new ErrorDelChatbot(null, 0);
    const { canal, red } = await abierto({
      clienteHttp: {
        enviarMensaje: jest.fn(async () => {
          throw caido;
        }),
      },
    });
    const pregunta = canal.enviarMensaje('hola');
    red.ultima.caer();
    await expect(pregunta).rejects.toBe(caido);
  });

  test('tras una caída se reconecta, rehace las suscripciones y avisa para releer', async () => {
    const { canal, red, reloj } = await abierto();
    const alReconectar = jest.fn();
    const estados = [];
    canal.alReconectar(alReconectar);
    canal.alEstado((estado) => estados.push(estado.estado));

    red.ultima.caer();
    await reloj.avanzar(1000);

    expect(red.abiertas).toHaveLength(2);
    expect([...red.ultima.suscripciones.keys()]).toEqual(SUSCRIPCIONES);
    expect(alReconectar).toHaveBeenCalledTimes(1);
    expect(estados).toEqual(['conectado', 'reconectando', 'reconectado']);
  });

  test('agotados los intentos queda «sin conexión», y «Reintentar» abre otra ronda', async () => {
    const { canal, red, reloj } = await abierto();
    const estados = [];
    canal.alEstado((estado) => estados.push(estado.estado));
    red.conectar.mockImplementation(async () => {
      throw new Error('No se pudo abrir el canal.');
    });

    red.ultima.caer();
    await reloj.avanzar(1000);
    await reloj.avanzar(2000);
    expect(estados.at(-1)).toBe('sin-conexion');

    canal.reintentar();
    await vaciar();
    expect(estados.at(-1)).toBe('reconectando');
  });
});

describe('identidad', () => {
  test('si la sesión cambia, esa pregunta va por HTTP y el canal se rehace con la nueva', async () => {
    let actual = { autenticado: false };
    const { canal, red, cliente } = await abierto({ sesion: () => actual });
    expect(red.llamadas[0].cabeceras.Authorization).toBeUndefined();

    actual = { autenticado: true, token: 'jwt-nuevo' };
    await expect(canal.enviarMensaje('hola')).resolves.toEqual(POR_HTTP);
    expect(cliente.enviarMensaje).toHaveBeenCalledWith('hola');
    expect(red.abiertas[0].cerrar).toHaveBeenCalled();
    expect(red.abiertas[0].enviados).toEqual([]);

    await vaciar();
    expect(red.llamadas[1].cabeceras.Authorization).toBe('Bearer jwt-nuevo');
    canal.enviarMensaje('otra');
    expect(red.ultima.enviados).toHaveLength(1);
  });
});

describe('soporte en vivo y estado', () => {
  test('el cambio de una solicitud llega a quien escucha; algo sin ticketId no', async () => {
    const { canal, red } = await abierto();
    const alTicket = jest.fn();
    canal.alTicket(alTicket);

    const ticket = { ticketId: 't-1', estado: 'RESUELTO', respuesta: 'Ya quedó.' };
    red.ultima.entregar(DESTINOS_DEL_CHATBOT.tickets, ticket);
    red.ultima.entregar(DESTINOS_DEL_CHATBOT.tickets, { estado: 'RESUELTO' });
    red.ultima.entregar(DESTINOS_DEL_CHATBOT.tickets, null);

    expect(alTicket).toHaveBeenCalledTimes(1);
    expect(alTicket).toHaveBeenCalledWith(ticket);
  });

  test('quien escucha el estado tarde recibe el último', async () => {
    const { canal } = await abierto();
    const alEstado = jest.fn();
    canal.alEstado(alEstado);
    expect(alEstado).toHaveBeenCalledWith({ estado: 'conectado' });
  });

  test('cerrar suelta lo que estaba en vuelo (por HTTP) y no se vuelve a abrir', async () => {
    const { canal, red, ventana } = await abierto();
    const pregunta = canal.enviarMensaje('hola');

    canal.cerrar();

    await expect(pregunta).resolves.toEqual(POR_HTTP);
    expect(red.ultima.cerrar).toHaveBeenCalled();
    expect(ventana.removeEventListener).toHaveBeenCalledWith('online', expect.any(Function));
    await canal.abrir();
    expect(red.conectar).toHaveBeenCalledTimes(1);
    expect(canal.enVivo).toBe(false);
  });
});
