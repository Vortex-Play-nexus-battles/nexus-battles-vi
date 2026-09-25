/**
 * B6 — Vista de mensajes privados sobre jsdom.
 *
 * El canal STOMP y la API REST son dobles: lo que se prueba es la vista —qué
 * pinta, qué manda, cuándo cae al respaldo REST y cómo explica un rechazo—.
 * El servidor tiene sus propias pruebas con STOMP de verdad (MensajesDirectosIT).
 */

import { jest } from '@jest/globals';

import {
  conversacionDesdeUrl,
  montarMensajes,
  pintarConversacion,
  pintarMensaje,
  textoDelContador,
} from './mensajes.js';
import { ErrorDeMensajes, RECHAZOS } from './cliente-mensajes.js';

const YO = '11111111-1111-1111-1111-111111111111';
const BRUNO = '22222222-2222-2222-2222-222222222222';
const CARLA = '33333333-3333-3333-3333-333333333333';

const VISTA = `
  <main id="mensajes">
    <span data-zona="conexion" class="conexion"></span>
    <p data-zona="aviso-conexion" hidden></p>
    <div data-zona="aviso-general" hidden></div>
    <div data-zona="estado-conversaciones"></div>
    <ul data-zona="conversaciones"></ul>
    <h2 data-zona="titulo-hilo" tabindex="-1"></h2>
    <button type="button" data-accion="cargar-anteriores" hidden>Cargar mensajes anteriores</button>
    <div data-zona="estado-hilo"></div>
    <ol data-zona="lineas"></ol>
    <p data-zona="anuncio" aria-live="polite"></p>
    <form data-zona="redactor" hidden>
      <div data-zona="aviso-redactor" hidden></div>
      <textarea name="texto"></textarea>
      <p data-zona="contador"></p>
      <button type="submit">Enviar</button>
    </form>
  </main>`;

const APODOS = { [YO]: 'yo', [BRUNO]: 'bruno', [CARLA]: 'carla' };

function mensaje({ id, de, a, texto, fecha, idCliente = null, apodo = null }) {
  return {
    id,
    conversacion: `dm:${[de, a].sort().join(':')}`,
    remitente: de,
    apodoRemitente: apodo ?? APODOS[de],
    destinatario: a,
    texto,
    fecha,
    leido: de === YO,
    idCliente,
  };
}

function apiFalsa({ conversaciones = [], historiales = {} } = {}) {
  return {
    listarConversaciones: jest.fn().mockResolvedValue(conversaciones),
    historial: jest.fn(async (uid, { antesDe } = {}) => {
      const todos = historiales[uid] ?? [];
      return antesDe ? todos.filter((m) => m.fecha < antesDe) : todos;
    }),
    enviarPorRest: jest.fn(),
    marcarLeida: jest.fn().mockResolvedValue(undefined),
  };
}

/** Canal que se reconecta, falso: guarda la suscripción para «recibir». */
function canalFalso() {
  const canal = {
    conectado: true,
    suscripciones: {},
    suscribir: jest.fn((destino, alRecibir) => {
      canal.suscripciones[destino] = alRecibir;
    }),
    enviar: jest.fn(),
    cerrar: jest.fn(),
    reintentar: jest.fn(),
  };
  return canal;
}

/** Temporizadores a mano: la prueba decide cuándo pasa el tiempo. */
function relojFalso() {
  const tareas = [];
  const intervalos = [];
  return {
    tareas,
    intervalos,
    setTimeout: jest.fn((fn) => {
      tareas.push(fn);
      return tareas.length;
    }),
    clearTimeout: jest.fn((id) => {
      if (id) {
        tareas[id - 1] = null;
      }
    }),
    setInterval: jest.fn((fn) => {
      intervalos.push(fn);
      return intervalos.length;
    }),
    clearInterval: jest.fn((id) => {
      intervalos[id - 1] = null;
    }),
    async correr() {
      for (const tarea of tareas.splice(0)) {
        if (tarea) {
          await tarea();
        }
      }
    },
  };
}

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

/** Vistas montadas en la prueba en curso: se cierran al terminar (oyentes del documento). */
const montadas = [];

afterEach(() => {
  for (const vista of montadas.splice(0)) {
    vista.cerrar();
  }
});

async function montar({ api = apiFalsa(), canal = canalFalso(), busqueda = '', ...resto } = {}) {
  document.body.innerHTML = VISTA;
  const raiz = document.getElementById('mensajes');
  const reloj = resto.reloj ?? relojFalso();
  const alContador = jest.fn();
  const alSeleccionar = jest.fn();
  const vista = await montarMensajes(raiz, {
    yo: YO,
    api,
    abrirCanal: canal ? async () => canal : null,
    busqueda,
    reloj,
    alContador,
    alSeleccionar,
    ...resto,
  });
  montadas.push(vista);
  return { raiz, api, canal, reloj, vista, alContador, alSeleccionar };
}

const $ = (selector) => document.querySelector(selector);
const lineas = () => [...document.querySelectorAll('[data-zona="lineas"] li')];

describe('piezas', () => {
  test('?con= solo abre un uid', () => {
    expect(conversacionDesdeUrl(`?con=${BRUNO}`)).toBe(BRUNO);
    expect(conversacionDesdeUrl(`?con=${BRUNO.toUpperCase()}`)).toBe(BRUNO);
    expect(conversacionDesdeUrl('?con=<script>')).toBeNull();
    expect(conversacionDesdeUrl('')).toBeNull();
    expect(conversacionDesdeUrl(undefined)).toBeNull();
  });

  test('el contador dice cuánto queda de los 500', () => {
    expect(textoDelContador('hola')).toBe('4 de 500 caracteres');
    expect(textoDelContador(undefined)).toBe('0 de 500 caracteres');
  });

  test('una conversación se pinta con apodo, vista previa y no leídos, sin innerHTML', () => {
    const fila = pintarConversacion(
      {
        uidOtro: BRUNO,
        apodoOtro: '<b>bruno</b>',
        noLeidos: 3,
        ultimoMensaje: mensaje({ id: '1', de: BRUNO, a: YO, texto: 'x'.repeat(80), fecha: 'f' }),
      },
      { activa: true, yo: YO },
    );
    const boton = fila.querySelector('button');
    expect(boton.getAttribute('aria-current')).toBe('true');
    expect(boton.querySelector('.conversacion__nombre').textContent).toBe('<b>bruno</b>');
    expect(boton.querySelector('b')).toBeNull();
    expect(boton.textContent).toContain('3 sin leer');
    expect(boton.querySelector('.conversacion__ultimo').textContent).toHaveLength(60);
  });

  test('lo último propio se marca con «Tú:» y sin no leídos no hay distintivo', () => {
    const fila = pintarConversacion(
      {
        uidOtro: BRUNO,
        apodoOtro: null,
        noLeidos: 0,
        ultimoMensaje: mensaje({ id: '1', de: YO, a: BRUNO, texto: 'hola', fecha: 'f' }),
      },
      { yo: YO },
    );
    expect(fila.textContent).toContain('Jugador');
    expect(fila.textContent).toContain('Tú: hola');
    expect(fila.querySelector('[data-zona="sin-leer"]')).toBeNull();
    expect(fila.querySelector('button').hasAttribute('aria-current')).toBe(false);
  });

  test('un mensaje propio y uno ajeno se distinguen, y el estado se dice con texto', () => {
    const mio = pintarMensaje(
      mensaje({ id: '1', de: YO, a: BRUNO, texto: 'hola', fecha: '2026-09-25T18:00:00Z' }),
      YO,
      {
        estado: 'enviando',
      },
    );
    const suyo = pintarMensaje(
      mensaje({ id: '2', de: BRUNO, a: YO, texto: 'hey', fecha: '2026-09-25T18:01:00Z' }),
      YO,
    );
    expect(mio.classList.contains('mensaje-directo--propio')).toBe(true);
    expect(mio.textContent).toContain('Tú');
    expect(mio.textContent).toContain('Enviando…');
    expect(suyo.classList.contains('mensaje-directo--propio')).toBe(false);
    expect(suyo.textContent).toContain('bruno');
    expect(suyo.querySelector('time').dateTime).toBe('2026-09-25T18:01:00Z');
  });
});

describe('carga y estados', () => {
  test('abre la conversación más reciente, pinta su historial y la marca leída', async () => {
    const api = apiFalsa({
      conversaciones: [
        {
          uidOtro: CARLA,
          apodoOtro: 'carla',
          noLeidos: 0,
          ultimoMensaje: mensaje({
            id: 'c1',
            de: CARLA,
            a: YO,
            texto: 'vieja',
            fecha: '2026-09-25T10:00:00Z',
          }),
        },
        {
          uidOtro: BRUNO,
          apodoOtro: 'bruno',
          noLeidos: 2,
          ultimoMensaje: mensaje({
            id: 'b2',
            de: BRUNO,
            a: YO,
            texto: 'reciente',
            fecha: '2026-09-25T12:00:00Z',
          }),
        },
      ],
      historiales: {
        [BRUNO]: [
          mensaje({ id: 'b1', de: BRUNO, a: YO, texto: 'hola', fecha: '2026-09-25T11:00:00Z' }),
          mensaje({ id: 'b2', de: BRUNO, a: YO, texto: 'reciente', fecha: '2026-09-25T12:00:00Z' }),
        ],
      },
    });

    const { alContador, alSeleccionar } = await montar({ api });

    expect(api.historial).toHaveBeenCalledWith(BRUNO, { limite: 50 });
    expect($('[data-zona="titulo-hilo"]').textContent).toBe('Conversación con bruno');
    expect(lineas().map((l) => l.dataset.id)).toEqual(['b1', 'b2']);
    expect(api.marcarLeida).toHaveBeenCalledWith(BRUNO);
    expect(alContador).toHaveBeenLastCalledWith(0);
    expect(alSeleccionar).toHaveBeenCalledWith(BRUNO);
    const filas = [...document.querySelectorAll('[data-zona="conversaciones"] button')];
    expect(filas.map((b) => b.dataset.uid)).toEqual([BRUNO, CARLA]);
    expect(filas[0].getAttribute('aria-current')).toBe('true');
    expect($('[data-zona="redactor"]').hidden).toBe(false);
    expect($('[data-accion="cargar-anteriores"]').hidden).toBe(true);
  });

  test('con ?con= abre esa conversación aunque no exista todavía', async () => {
    const api = apiFalsa();
    await montar({ api, busqueda: `?con=${BRUNO}` });

    expect($('[data-zona="titulo-hilo"]').textContent).toBe('Nueva conversación');
    expect($('[data-zona="estado-hilo"]').textContent).toContain('Todavía no hay mensajes');
    expect(api.marcarLeida).not.toHaveBeenCalled();
  });

  test('sin conversaciones: estado vacío que dice cómo empezar una', async () => {
    await montar({ api: apiFalsa() });
    const vacio = $('[data-zona="estado-conversaciones"]');
    expect(vacio.textContent).toContain('Todavía no tienes conversaciones');
    expect(vacio.textContent).toContain('Mensaje privado');
    expect(vacio.querySelector('a').getAttribute('href')).toBe('./chat.html');
    expect($('[data-zona="titulo-hilo"]').textContent).toBe('Elige una conversación');
    expect($('[data-zona="redactor"]').hidden).toBe(true);
  });

  test('si no cargan las conversaciones se dice y se puede reintentar', async () => {
    const api = apiFalsa();
    api.listarConversaciones.mockRejectedValueOnce(new Error('caida'));
    await montar({ api });

    const error = $('[data-zona="estado-conversaciones"]');
    expect(error.textContent).toContain('No pudimos cargar tus conversaciones');
    error.querySelector('[data-accion="reintentar"]').click();
    await esperar();
    expect(api.listarConversaciones).toHaveBeenCalledTimes(2);
  });

  test('abrir ?con= no tapa el error de la lista: sigue a la vista hasta que cargue', async () => {
    // Si no, la lista enseñaría solo la conversación de ?con= y parecería que
    // no hay más: un fallo en silencio (riesgo #7).
    const api = apiFalsa();
    api.listarConversaciones.mockRejectedValueOnce(new Error('caida'));
    await montar({ api, busqueda: `?con=${BRUNO}` });

    const estado = $('[data-zona="estado-conversaciones"]');
    expect($('[data-zona="titulo-hilo"]').textContent).toBe('Nueva conversación');
    expect(estado.hidden).toBe(false);
    expect(estado.textContent).toContain('No pudimos cargar tus conversaciones');

    api.listarConversaciones.mockResolvedValueOnce([
      {
        uidOtro: CARLA,
        apodoOtro: 'carla',
        noLeidos: 1,
        ultimoMensaje: mensaje({ id: 'c1', de: CARLA, a: YO, texto: 'hola', fecha: 'f' }),
      },
    ]);
    estado.querySelector('[data-accion="reintentar"]').click();
    await esperar();

    expect(estado.hidden).toBe(true);
    expect(estado.textContent).toBe('');
    const filas = [...document.querySelectorAll('[data-zona="conversaciones"] button')];
    expect(filas.map((b) => b.dataset.uid).sort()).toEqual([BRUNO, CARLA].sort());
  });

  test('si no carga el historial se dice y se puede reintentar', async () => {
    const api = apiFalsa();
    api.historial.mockRejectedValueOnce(new Error('caida'));
    await montar({ api, busqueda: `?con=${BRUNO}` });

    expect($('[data-zona="estado-hilo"]').textContent).toContain(
      'No pudimos cargar esta conversación',
    );
    $('[data-zona="estado-hilo"] [data-accion="reintentar"]').click();
    await esperar();
    expect(api.historial).toHaveBeenCalledTimes(2);
  });

  test('un ?con= que no es un uid, o que eres tú, se explica', async () => {
    await montar({ busqueda: '?con=cualquier-cosa' });
    expect($('[data-zona="aviso-general"]').textContent).toContain('no lleva a ningún jugador');

    await montar({ busqueda: `?con=${YO}` });
    expect($('[data-zona="aviso-general"]').textContent).toContain(
      RECHAZOS.DESTINATARIO_PROPIO.titulo,
    );
  });

  test('con la página llena se ofrece cargar hacia atrás, y se anteponen los anteriores', async () => {
    const pagina = Array.from({ length: 50 }, (_, i) =>
      mensaje({
        id: `m${i + 10}`,
        de: BRUNO,
        a: YO,
        texto: `m${i + 10}`,
        fecha: `2026-09-25T12:${String(i + 10).padStart(2, '0')}:00Z`,
      }),
    );
    const anteriores = [
      mensaje({ id: 'm1', de: BRUNO, a: YO, texto: 'm1', fecha: '2026-09-25T11:00:00Z' }),
      mensaje({ id: 'm2', de: YO, a: BRUNO, texto: 'm2', fecha: '2026-09-25T11:30:00Z' }),
    ];
    const api = apiFalsa();
    api.historial.mockImplementation(async (uid, { antesDe } = {}) =>
      antesDe ? anteriores : pagina,
    );
    await montar({ api, busqueda: `?con=${BRUNO}` });

    const boton = $('[data-accion="cargar-anteriores"]');
    expect(boton.hidden).toBe(false);
    boton.click();
    await esperar();

    expect(api.historial).toHaveBeenLastCalledWith(BRUNO, {
      antesDe: '2026-09-25T12:10:00Z',
      limite: 50,
    });
    expect(
      lineas()
        .slice(0, 3)
        .map((l) => l.dataset.id),
    ).toEqual(['m1', 'm2', 'm10']);
    expect(boton.hidden).toBe(true);
    expect($('[data-zona="anuncio"]').textContent).toBe('Se cargaron 2 mensajes anteriores.');
  });
});

describe('enviar', () => {
  async function abiertaConBruno(opciones = {}) {
    const montada = await montar({ busqueda: `?con=${BRUNO}`, ...opciones });
    const texto = $('[name="texto"]');
    return {
      ...montada,
      escribir(valor) {
        texto.value = valor;
        texto.dispatchEvent(new Event('input'));
        $('[data-zona="redactor"]').dispatchEvent(new Event('submit', { cancelable: true }));
      },
    };
  }

  test('por STOMP con idCliente; el eco lo confirma y no hace falta REST', async () => {
    const { canal, api, escribir } = await abiertaConBruno();
    escribir('  hola bruno  ');

    expect(canal.enviar).toHaveBeenCalledTimes(1);
    const [destino, cuerpo] = canal.enviar.mock.calls[0];
    expect(destino).toBe(`/app/mensajes-directos/${BRUNO}`);
    expect(cuerpo.texto).toBe('hola bruno');
    expect(typeof cuerpo.idCliente).toBe('string');
    expect(lineas()[0].dataset.estado).toBe('enviando');
    expect($('[name="texto"]').value).toBe('');
    expect($('[data-zona="contador"]').textContent).toBe('0 de 500 caracteres');

    canal.suscripciones['/usuario/cola/mensajes-directos']({
      tipo: 'MENSAJE',
      ...mensaje({
        id: 'real-1',
        de: YO,
        a: BRUNO,
        texto: 'hola bruno',
        fecha: '2026-09-25T18:00:00Z',
        idCliente: cuerpo.idCliente,
      }),
    });

    expect(lineas()).toHaveLength(1);
    expect(lineas()[0].dataset.id).toBe('real-1');
    expect(lineas()[0].dataset.estado).toBe('entregado');
    expect(api.enviarPorRest).not.toHaveBeenCalled();
  });

  test('si el eco no llega, va por REST con el mismo idCliente', async () => {
    const { canal, api, reloj, escribir } = await abiertaConBruno();
    api.enviarPorRest.mockImplementation(async (uid, { texto, idCliente }) =>
      mensaje({ id: 'real-2', de: YO, a: uid, texto, fecha: '2026-09-25T18:00:00Z', idCliente }),
    );
    escribir('hola');
    const { idCliente } = canal.enviar.mock.calls[0][1];

    await reloj.correr();

    expect(api.enviarPorRest).toHaveBeenCalledWith(BRUNO, { texto: 'hola', idCliente });
    expect(lineas().map((l) => l.dataset.id)).toEqual(['real-2']);
  });

  test('sin canal, por REST; un rechazo deja el mensaje como no enviado y dice por qué', async () => {
    const { api, escribir } = await abiertaConBruno({ canal: null });
    api.enviarPorRest.mockRejectedValue(
      new ErrorDeMensajes(
        { type: 'https://nexusbattles.local/errores/contenido-bloqueado', status: 422 },
        422,
      ),
    );

    escribir('algo feo');
    await esperar();

    expect(api.enviarPorRest).toHaveBeenCalledTimes(1);
    expect(lineas()[0].dataset.estado).toBe('fallido');
    expect(lineas()[0].textContent).toContain('No se envió');
    expect($('[data-zona="aviso-redactor"]').textContent).toContain(
      RECHAZOS.TEXTO_NO_PERMITIDO.titulo,
    );
  });

  test('un RECHAZO por la cola se explica por su motivo', async () => {
    const { canal, escribir } = await abiertaConBruno();
    escribir('muy rapido');
    const { idCliente } = canal.enviar.mock.calls[0][1];

    canal.suscripciones['/usuario/cola/mensajes-directos']({
      tipo: 'RECHAZO',
      motivo: 'DEMASIADO_RAPIDO',
      idCliente,
    });

    expect(lineas()[0].dataset.estado).toBe('fallido');
    const aviso = $('[data-zona="aviso-redactor"]');
    expect(aviso.hidden).toBe(false);
    expect(aviso.textContent).toContain(RECHAZOS.DEMASIADO_RAPIDO.titulo);
  });

  test('un texto vacío o de más de 500 no se manda', async () => {
    const { canal, api, escribir } = await abiertaConBruno();
    escribir('   ');
    escribir('x'.repeat(501));
    expect(canal.enviar).not.toHaveBeenCalled();
    expect(api.enviarPorRest).not.toHaveBeenCalled();
    expect($('[data-zona="aviso-redactor"]').textContent).toContain(RECHAZOS.TEXTO_INVALIDO.titulo);
  });

  test('si el canal se cae justo al enviar, sigue por REST', async () => {
    const canal = canalFalso();
    canal.enviar.mockImplementation(() => {
      throw new Error('sin canal');
    });
    const { api, escribir } = await abiertaConBruno({ canal });
    api.enviarPorRest.mockResolvedValue(
      mensaje({ id: 'r', de: YO, a: BRUNO, texto: 'hola', fecha: '2026-09-25T18:00:00Z' }),
    );
    escribir('hola');
    await esperar();
    expect(api.enviarPorRest).toHaveBeenCalledTimes(1);
  });
});

describe('recibir en tiempo real', () => {
  test('lo que llega a la conversación abierta se pinta, se anuncia y se marca leído', async () => {
    const { canal, api, reloj } = await montar({ busqueda: `?con=${BRUNO}` });

    canal.suscripciones['/usuario/cola/mensajes-directos']({
      tipo: 'MENSAJE',
      ...mensaje({ id: 'n1', de: BRUNO, a: YO, texto: '¿jugamos?', fecha: '2026-09-25T18:00:00Z' }),
    });
    await reloj.correr();

    expect(lineas().map((l) => l.dataset.id)).toEqual(['n1']);
    expect($('[data-zona="anuncio"]').textContent).toBe('Mensaje nuevo de bruno: ¿jugamos?');
    expect($('[data-zona="titulo-hilo"]').textContent).toBe('Conversación con bruno');
    expect(api.marcarLeida).toHaveBeenCalledWith(BRUNO);
  });

  test('si la pestaña no se está mirando, se marca leído al volver a ella', async () => {
    let mirando = false;
    const { canal, api, reloj } = await montar({
      busqueda: `?con=${BRUNO}`,
      visible: () => mirando,
    });

    canal.suscripciones['/usuario/cola/mensajes-directos']({
      tipo: 'MENSAJE',
      ...mensaje({ id: 'n2', de: BRUNO, a: YO, texto: 'hola', fecha: '2026-09-25T18:00:00Z' }),
    });
    await reloj.correr();
    expect(api.marcarLeida).not.toHaveBeenCalled();

    mirando = true;
    document.dispatchEvent(new Event('visibilitychange'));
    await esperar();
    expect(api.marcarLeida).toHaveBeenCalledWith(BRUNO);
  });

  test('lo que llega a otra conversación sube sus no leídos y el contador', async () => {
    const api = apiFalsa({
      conversaciones: [
        {
          uidOtro: BRUNO,
          apodoOtro: 'bruno',
          noLeidos: 0,
          ultimoMensaje: mensaje({
            id: 'b1',
            de: YO,
            a: BRUNO,
            texto: 'hola',
            fecha: '2026-09-25T10:00:00Z',
          }),
        },
      ],
    });
    const { canal, alContador } = await montar({ api });

    const recibir = canal.suscripciones['/usuario/cola/mensajes-directos'];
    const deCarla = mensaje({
      id: 'c1',
      de: CARLA,
      a: YO,
      texto: 'soy carla',
      fecha: '2026-09-25T18:00:00Z',
    });
    recibir({ tipo: 'MENSAJE', ...deCarla });
    recibir({ tipo: 'MENSAJE', ...deCarla });

    const primera = document.querySelector('[data-zona="conversaciones"] button');
    expect(primera.dataset.uid).toBe(CARLA);
    expect(primera.textContent).toContain('carla');
    expect(primera.textContent).toContain('1 sin leer');
    expect(alContador).toHaveBeenLastCalledWith(1);
    expect($('[data-zona="anuncio"]').textContent).toBe('Mensaje nuevo de carla: soy carla');
  });

  test('elegir otra conversación la abre y lleva el foco al redactor', async () => {
    const api = apiFalsa({
      conversaciones: [
        {
          uidOtro: BRUNO,
          apodoOtro: 'bruno',
          noLeidos: 0,
          ultimoMensaje: mensaje({
            id: 'b1',
            de: BRUNO,
            a: YO,
            texto: 'hola',
            fecha: '2026-09-25T12:00:00Z',
          }),
        },
        {
          uidOtro: CARLA,
          apodoOtro: 'carla',
          noLeidos: 1,
          ultimoMensaje: mensaje({
            id: 'c1',
            de: CARLA,
            a: YO,
            texto: 'hey',
            fecha: '2026-09-25T10:00:00Z',
          }),
        },
      ],
    });
    await montar({ api });

    document.querySelector(`[data-uid="${CARLA}"]`).click();
    await esperar();
    await esperar();

    expect($('[data-zona="titulo-hilo"]').textContent).toBe('Conversación con carla');
    expect(document.activeElement).toBe($('[name="texto"]'));
    expect(api.marcarLeida).toHaveBeenCalledWith(CARLA);
  });

  test('lo que no es del contrato se ignora', async () => {
    const { canal } = await montar({ busqueda: `?con=${BRUNO}` });
    const recibir = canal.suscripciones['/usuario/cola/mensajes-directos'];
    recibir(null);
    recibir({ tipo: 'OTRO' });
    expect(lineas()).toHaveLength(0);
  });
});

describe('sin canal en tiempo real (riesgo #7)', () => {
  test('si el canal no abre: se dice, se consulta por REST cada poco y se pone al día', async () => {
    const api = apiFalsa({
      historiales: { [BRUNO]: [] },
    });
    const reloj = relojFalso();
    document.body.innerHTML = VISTA;
    montadas.push(
      await montarMensajes(document.getElementById('mensajes'), {
        yo: YO,
        api,
        abrirCanal: async () => {
          throw new Error('no abre');
        },
        busqueda: `?con=${BRUNO}`,
        reloj,
      }),
    );

    expect($('[data-zona="conexion"]').dataset.estadoCanal).toBe('sin-conexion');
    expect($('[data-zona="aviso-conexion"]').hidden).toBe(false);
    expect(reloj.setInterval).toHaveBeenCalledWith(expect.any(Function), 10000);

    api.historial.mockResolvedValueOnce([
      mensaje({
        id: 's1',
        de: BRUNO,
        a: YO,
        texto: 'llegó por el sondeo',
        fecha: '2026-09-25T18:00:00Z',
      }),
    ]);
    await reloj.intervalos[0]();

    expect(lineas().map((l) => l.dataset.id)).toEqual(['s1']);
    expect($('[data-zona="anuncio"]').textContent).toContain('llegó por el sondeo');
  });

  test('sin forma de abrir canal, la vista funciona solo por REST', async () => {
    const { reloj } = await montar({ canal: null, busqueda: `?con=${BRUNO}` });
    expect(reloj.setInterval).toHaveBeenCalled();
  });

  test('al recuperar el canal se deja de sondear', async () => {
    let alEstado;
    const canal = canalFalso();
    const reloj = relojFalso();
    document.body.innerHTML = VISTA;
    montadas.push(
      await montarMensajes(document.getElementById('mensajes'), {
        yo: YO,
        api: apiFalsa(),
        abrirCanal: async (opciones) => {
          alEstado = opciones.alEstado;
          return canal;
        },
        reloj,
      }),
    );

    alEstado({ estado: 'reconectando', intento: 1, de: 5 });
    expect(reloj.setInterval).toHaveBeenCalledTimes(1);
    alEstado({ estado: 'reconectado' });
    expect(reloj.clearInterval).toHaveBeenCalled();
    expect($('[data-zona="aviso-conexion"]').hidden).toBe(true);
  });
});
