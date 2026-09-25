/**
 * HU-JUE-015 — Vista del chat sobre jsdom.
 *
 * CA-01: el historial y los mensajes en vivo se pintan y el envio sale por el
 * destino del canal. CA-02: el logro se pinta con su detalle. CA-03: el error
 * de la cola privada se muestra a quien escribio, con el tono del mapeo.
 *
 * UXC-6 — lo que se añadió: quién habla se dice con palabras («Tú», el apodo,
 * «Sistema»), el canal se reconecta solo y lo cuenta en el hilo, el silencio
 * por sanción sustituye el campo por su motivo, y un mensaje rechazado por el
 * filtro vuelve al campo.
 */

import { jest } from '@jest/globals';

import {
  montarChat,
  montarVistaDeChat,
  destinosDe,
  canalDesdeUrl,
  tonoPara,
  pintarMensaje,
  reaccionAlError,
} from './chat.js';

const ID_SALA = '3f2b6f3e-3c2a-4a1e-9f0e-6f1a2b3c4d5e';
const YO = 'aaaaaaaa-0000-4000-8000-000000000001';

function raiz() {
  document.body.innerHTML = `
    <main id="chat">
      <span data-zona="conexion" class="conexion"></span>
      <ol data-zona="mensajes"></ol>
      <div data-zona="sin-mensajes"></div>
      <form>
        <div data-zona="aviso" hidden></div>
        <textarea name="texto"></textarea>
        <input name="logroMision" /><input name="logroTitulo" />
        <button type="submit">Enviar</button>
      </form>
    </main>`;
  return document.getElementById('chat');
}

/** Cliente falso: guarda las suscripciones para que la prueba "reciba" mensajes. */
function clienteFalso() {
  const suscripciones = {};
  return {
    suscripciones,
    alCerrar: null,
    alError: null,
    suscribir: jest.fn((destino, alRecibir) => {
      suscripciones[destino] = alRecibir;
    }),
    enviar: jest.fn(),
    cerrar: jest.fn(),
  };
}

/** Un reloj que no espera: los reintentos se disparan a mano. */
function relojManual() {
  const pendientes = [];
  return {
    pendientes,
    setTimeout: (fn) => {
      pendientes.push(fn);
      return pendientes.length;
    },
    clearTimeout: () => {},
    async avanzar() {
      const fn = pendientes.shift();
      await fn?.();
    },
  };
}

const esperar = async () => {
  for (let i = 0; i < 4; i += 1) {
    await Promise.resolve();
  }
};

const MENSAJE = {
  id: '1',
  tipo: 'chat.mensaje',
  idSala: ID_SALA,
  autor: { id: 'j1', apodo: 'Ana' },
  texto: 'vamos a la sala 3',
  logro: null,
  enviadoEn: '2026-09-02T10:00:00Z',
};

const globos = (contenedor) => [
  ...contenedor.querySelectorAll('[data-zona="mensajes"] li.mensaje'),
];

test('los destinos siguen el contrato para la sala y para el general', () => {
  expect(destinosDe({ idSala: ID_SALA })).toEqual({
    vivo: `/tema/salas/${ID_SALA}/chat`,
    historial: `/app/salas/${ID_SALA}/chat/historial`,
    envio: `/app/salas/${ID_SALA}/chat`,
  });
  expect(destinosDe({}).vivo).toBe('/tema/chat/general');
  expect(canalDesdeUrl(`?sala=${ID_SALA}`)).toEqual({ idSala: ID_SALA });
  expect(canalDesdeUrl('')).toEqual({});
});

test('CA-01: el historial se pinta al suscribirse y los mensajes en vivo se agregan', async () => {
  const cliente = clienteFalso();
  const contenedor = raiz();

  await montarChat(contenedor, {
    canal: { idSala: ID_SALA },
    token: 't',
    conectar: async () => cliente,
  });
  cliente.suscripciones[`/app/salas/${ID_SALA}/chat/historial`]([MENSAJE]);
  cliente.suscripciones[`/tema/salas/${ID_SALA}/chat`]({ ...MENSAJE, id: '2', texto: 'listo' });

  const items = globos(contenedor);
  expect(items).toHaveLength(2);
  expect(items[0].textContent).toContain('Ana');
  expect(items[1].textContent).toContain('listo');
  expect(contenedor.querySelector('[data-zona="conexion"]').className).toContain('estable');
  // Un separador de día encabeza la conversación.
  expect(contenedor.querySelector('.conversacion__dia')).not.toBeNull();
});

test('CA-01: enviar manda el texto al destino del canal y limpia el formulario', async () => {
  const cliente = clienteFalso();
  const contenedor = raiz();
  await montarChat(contenedor, { canal: {}, token: 't', conectar: async () => cliente });

  const formulario = contenedor.querySelector('form');
  formulario.elements.texto.value = '  hola  ';
  formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  await esperar();

  expect(cliente.enviar).toHaveBeenCalledWith('/app/chat/general', { texto: 'hola', logro: null });
  expect(formulario.elements.texto.value).toBe('');
});

test('CA-01: Enter envía y Mayús + Enter no', async () => {
  const cliente = clienteFalso();
  const contenedor = raiz();
  await montarChat(contenedor, { canal: {}, token: 't', conectar: async () => cliente });
  const campo = contenedor.querySelector('textarea');

  campo.value = 'primera línea';
  campo.dispatchEvent(
    new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, bubbles: true }),
  );
  await esperar();
  expect(cliente.enviar).not.toHaveBeenCalled();

  campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
  await esperar();
  expect(cliente.enviar).toHaveBeenCalledWith('/app/chat/general', {
    texto: 'primera línea',
    logro: null,
  });
});

test('CA-02: un logro se pinta con su detalle', () => {
  const item = pintarMensaje({
    ...MENSAJE,
    tipo: 'chat.logro',
    logro: { mision: 'mision-7', titulo: 'Cazador de dragones' },
  });

  expect(item.dataset.tipo).toBe('chat.logro');
  expect(item.querySelector('.mensaje__logro').textContent).toContain('Cazador de dragones');
});

test('quién habla se dice con palabras: «Tú», el apodo del otro o «Sistema»', () => {
  const mio = pintarMensaje({ ...MENSAJE, autor: { id: YO, apodo: 'Simón' } }, { miId: YO });
  const ajeno = pintarMensaje(MENSAJE, { miId: YO });

  expect(mio.dataset.quien).toBe('yo');
  expect(mio.querySelector('.mensaje__autor').textContent).toBe('Tú');
  expect(ajeno.dataset.quien).toBe('otro');
  expect(ajeno.querySelector('.mensaje__autor').textContent).toBe('Ana');
  expect(ajeno.querySelector('.mensaje__inicial').textContent).toBe('A');
});

test('CA-03: el silencio por sanción sustituye el campo por su motivo y el enlace a tus sanciones', async () => {
  const cliente = clienteFalso();
  const contenedor = raiz();
  await montarChat(contenedor, { canal: {}, token: 't', conectar: async () => cliente });

  cliente.suscripciones['/usuario/cola/salas']({
    type: 'https://nexusbattles.local/errores/jugador-silenciado',
    title: 'No puedes escribir en el chat',
    status: 403,
    detail: 'Tienes una sancion activa de silencio.',
  });

  const bloqueo = contenedor.querySelector('[data-zona="bloqueo"]');
  expect(bloqueo.hidden).toBe(false);
  expect(bloqueo.textContent).toContain('Tienes un silencio activo');
  expect(bloqueo.querySelector('a').getAttribute('href')).toMatch(/mis-sanciones\.html$/);
  expect(contenedor.querySelector('[type="submit"]').disabled).toBe(true);
  expect(contenedor.querySelector('textarea').disabled).toBe(true);
});

test('CA-03: un mensaje que el filtro rechaza vuelve al campo, con un aviso en palabras del producto', async () => {
  const cliente = clienteFalso();
  const contenedor = raiz();
  await montarChat(contenedor, { canal: {}, token: 't', conectar: async () => cliente });
  const formulario = contenedor.querySelector('form');
  formulario.elements.texto.value = 'algo feo';
  formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  await esperar();
  expect(formulario.elements.texto.value).toBe('');

  cliente.suscripciones['/usuario/cola/salas']({
    type: 'https://nexusbattles.local/errores/contenido-bloqueado',
    title: 'Mensaje bloqueado',
    status: 422,
    detail: 'El mensaje contiene terminos que no estan permitidos y no se entrego al canal.',
  });

  const aviso = contenedor.querySelector('[data-zona="aviso"] .aviso');
  expect(aviso.className).toContain('aviso--advertencia');
  expect(aviso.textContent).toContain('Tu mensaje no salió');
  expect(formulario.elements.texto.value).toBe('algo feo');
});

test('CA-03: un error que la vista no conoce se dice con el tono del mapeo', async () => {
  const cliente = clienteFalso();
  const contenedor = raiz();
  await montarChat(contenedor, { canal: {}, token: 't', conectar: async () => cliente });

  cliente.suscripciones['/usuario/cola/salas']({
    type: 'https://nexusbattles.local/errores/otra-cosa',
    title: 'Algo no salió',
    status: 409,
    detail: 'Detalle del servicio.',
  });

  const aviso = contenedor.querySelector('[data-zona="aviso"] .aviso');
  expect(aviso.className).toContain('aviso--advertencia');
  expect(aviso.textContent).toContain('Algo no salió');
  expect(tonoPara(503)).toBe('error');
  expect(reaccionAlError({ type: 'x', status: 503, title: 't' }).aviso.tono).toBe('error');
});

test('sin token no se conecta y se pide iniciar sesion', async () => {
  const conectar = jest.fn();
  const contenedor = raiz();

  const cliente = await montarChat(contenedor, { canal: {}, token: null, conectar });

  expect(cliente).toBeNull();
  expect(conectar).not.toHaveBeenCalled();
  expect(contenedor.querySelector('[type="submit"]').disabled).toBe(true);
  expect(contenedor.querySelector('[data-zona="bloqueo"]').textContent).toContain('Inicia sesión');
});

test('si el servidor rechaza la conexión, la vista lo dice donde iría la conversación y deja reintentar', async () => {
  const contenedor = raiz();
  const cliente = clienteFalso();
  const conectar = jest
    .fn()
    .mockRejectedValueOnce(new Error('El token de acceso no es válido.'))
    .mockResolvedValue(cliente);

  await montarChat(contenedor, { canal: {}, token: 't', conectar });

  expect(contenedor.querySelector('[data-zona="conexion"]').className).toContain('sin-conexion');
  const error = contenedor.querySelector('[data-zona="sin-mensajes"] .estado-vista--error');
  expect(error.textContent).toContain('No pudimos abrir el chat');
  expect(contenedor.querySelector('[type="submit"]').disabled).toBe(true);

  error.querySelector('[data-accion="reintentar"]').click();
  await esperar();
  expect(conectar).toHaveBeenCalledTimes(2);
  expect(contenedor.querySelector('[data-zona="conexion"]').className).toContain('estable');
  expect(contenedor.querySelector('[type="submit"]').disabled).toBe(false);
});

test('al caerse el canal se reintenta solo, se cuenta en el hilo y lo perdido se intercala al volver', async () => {
  const contenedor = raiz();
  const primero = clienteFalso();
  const segundo = clienteFalso();
  const conectar = jest.fn().mockResolvedValueOnce(primero).mockResolvedValueOnce(segundo);
  const reloj = relojManual();
  await montarChat(contenedor, { canal: {}, token: 't', conectar, reloj, esperas: [10, 20] });
  primero.suscripciones['/app/chat/general/historial']([MENSAJE]);

  primero.alCerrar();
  expect(contenedor.querySelector('[data-zona="conexion"]').textContent).toMatch(/Reconectando/);
  expect(contenedor.querySelector('[type="submit"]').disabled).toBe(true);
  expect(contenedor.querySelector('.mensaje--sistema').textContent).toContain(
    'Se cortó la conexión',
  );

  await reloj.avanzar();
  const perdido = {
    ...MENSAJE,
    id: '9',
    texto: 'mientras no estabas',
    enviadoEn: '2026-09-02T10:05:00Z',
  };
  segundo.suscripciones['/app/chat/general/historial']([MENSAJE, perdido]);

  const textos = globos(contenedor).map((li) => li.querySelector('.mensaje__texto').textContent);
  expect(textos).toContain('mientras no estabas');
  expect(textos.filter((t) => t === 'vamos a la sala 3')).toHaveLength(1);
  expect(contenedor.querySelector('[data-zona="conexion"]').textContent).toMatch(/recuperada/);
  expect(contenedor.textContent).toContain('Conexión recuperada');
  expect(contenedor.querySelector('[type="submit"]').disabled).toBe(false);
});

describe('la página', () => {
  function pagina() {
    document.body.innerHTML = `
      <h1 data-zona="titulo">Chat</h1>
      <a data-zona="volver-a-la-sala" href="./batallas.html" hidden>Volver a la sala</a>
      <p data-zona="intro"></p>
      <div data-zona="pestanas-chat"></div>
      <section data-panel-chat="general">
        <h2 data-zona="titulo-conversacion">Conversación general</h2>
        <span data-zona="conexion" class="conexion"></span>
        <ol data-zona="mensajes"></ol>
        <div data-zona="sin-mensajes"></div>
        <form>
          <div data-zona="aviso" hidden></div>
          <textarea name="texto"></textarea>
          <button type="submit">Enviar</button>
        </form>
      </section>`;
  }

  test('con ?sala= es el chat de esa sala, con su vuelta y sin pestañas', async () => {
    pagina();
    const cliente = clienteFalso();

    const { chat } = montarVistaDeChat(document, {
      busqueda: `?sala=${ID_SALA}`,
      token: 't',
      miId: YO,
      conectar: async () => cliente,
    });
    await chat;

    expect(document.querySelector('[data-zona="titulo"]').textContent).toBe('Chat de la sala');
    const vuelta = document.querySelector('[data-zona="volver-a-la-sala"]');
    expect(vuelta.hidden).toBe(false);
    expect(vuelta.getAttribute('href')).toBe(`./sala-batalla.html?sala=${ID_SALA}`);
    expect(document.querySelector('[role="tablist"]')).toBeNull();
    expect(cliente.suscribir).toHaveBeenCalledWith(
      `/tema/salas/${ID_SALA}/chat`,
      expect.any(Function),
    );
  });

  test('sin sala: pestañas «Chat general» y «Mensajes privados», que hoy cuenta que no están abiertos', async () => {
    pagina();
    const cliente = clienteFalso();

    const { chat } = montarVistaDeChat(document, {
      busqueda: '',
      token: 't',
      miId: YO,
      conectar: async () => cliente,
    });
    await chat;

    const pestanas = [...document.querySelectorAll('[role="tab"]')].map((p) => p.textContent);
    expect(pestanas).toEqual(['Chat general', 'Mensajes privados']);
    document.getElementById('pestana-privados').click();
    await esperar();

    const aviso = document.querySelector('[data-estado="sin-abrir"]');
    expect(aviso.querySelector('h2').textContent).toBe(
      'Los mensajes privados todavía no están abiertos',
    );
    expect(aviso.textContent).not.toMatch(/próximamente/i);
    aviso.querySelector('[data-accion="ir-al-chat-general"]').click();
    expect(document.getElementById('pestana-general').getAttribute('aria-selected')).toBe('true');
  });
});
