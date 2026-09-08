/**
 * HU-JUE-015 — Vista del chat sobre jsdom.
 *
 * CA-01: el historial y los mensajes en vivo se pintan y el envio sale por el
 * destino del canal. CA-02: el logro se pinta con su detalle. CA-03: el error
 * de la cola privada se muestra a quien escribio, con el tono del mapeo.
 */

import { jest } from '@jest/globals';

import { montarChat, destinosDe, canalDesdeUrl, tonoPara, pintarMensaje } from './chat.js';

const ID_SALA = '3f2b6f3e-3c2a-4a1e-9f0e-6f1a2b3c4d5e';

function raiz() {
  document.body.innerHTML = `
    <main id="chat">
      <span data-zona="conexion" class="conexion"></span>
      <ol data-zona="mensajes"></ol>
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
    suscribir: jest.fn((destino, alRecibir) => {
      suscripciones[destino] = alRecibir;
    }),
    enviar: jest.fn(),
    cerrar: jest.fn(),
  };
}

const MENSAJE = {
  id: '1',
  tipo: 'chat.mensaje',
  idSala: ID_SALA,
  autor: { id: 'j1', apodo: 'Ana' },
  texto: 'vamos a la sala 3',
  logro: null,
  enviadoEn: '2026-09-02T10:00:00Z',
};

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

  await montarChat(contenedor, { canal: { idSala: ID_SALA }, token: 't', conectar: async () => cliente });
  cliente.suscripciones[`/app/salas/${ID_SALA}/chat/historial`]([MENSAJE]);
  cliente.suscripciones[`/tema/salas/${ID_SALA}/chat`]({ ...MENSAJE, id: '2', texto: 'listo' });

  const items = contenedor.querySelectorAll('[data-zona="mensajes"] li');
  expect(items).toHaveLength(2);
  expect(items[0].textContent).toContain('Ana');
  expect(items[1].textContent).toContain('listo');
  expect(contenedor.querySelector('[data-zona="conexion"]').className).toContain('estable');
});

test('CA-01: enviar manda el texto al destino del canal y limpia el formulario', async () => {
  const cliente = clienteFalso();
  const contenedor = raiz();
  await montarChat(contenedor, { canal: {}, token: 't', conectar: async () => cliente });

  const formulario = contenedor.querySelector('form');
  formulario.elements.texto.value = '  hola  ';
  formulario.dispatchEvent(new Event('submit', { cancelable: true }));

  expect(cliente.enviar).toHaveBeenCalledWith('/app/chat/general', { texto: 'hola', logro: null });
  expect(formulario.elements.texto.value).toBe('');
});

test('CA-02: un logro se pinta con su detalle', () => {
  const item = pintarMensaje({
    ...MENSAJE,
    tipo: 'chat.logro',
    logro: { mision: 'mision-7', titulo: 'Cazador de dragones' },
  });

  expect(item.dataset.tipo).toBe('chat.logro');
  expect(item.querySelector('.logro').textContent).toContain('Cazador de dragones');
});

test('CA-03: el error de la cola privada se muestra a quien escribio con el tono del mapeo', async () => {
  const cliente = clienteFalso();
  const contenedor = raiz();
  await montarChat(contenedor, { canal: {}, token: 't', conectar: async () => cliente });

  cliente.suscripciones['/usuario/cola/salas']({
    type: 'https://nexusbattles.local/errores/jugador-silenciado',
    title: 'No puedes escribir en el chat',
    status: 403,
    detail: 'Tienes una sancion activa de silencio.',
  });

  const aviso = contenedor.querySelector('[data-zona="aviso"] .aviso');
  expect(aviso.className).toContain('aviso--advertencia');
  expect(aviso.textContent).toContain('No puedes escribir en el chat');
  expect(tonoPara(503)).toBe('error');
});

test('sin token no se conecta y se pide iniciar sesion', async () => {
  const conectar = jest.fn();
  const contenedor = raiz();

  const cliente = await montarChat(contenedor, { canal: {}, token: null, conectar });

  expect(cliente).toBeNull();
  expect(conectar).not.toHaveBeenCalled();
  expect(contenedor.querySelector('[type="submit"]').disabled).toBe(true);
  expect(contenedor.querySelector('[data-zona="aviso"]').textContent).toContain('Inicia sesion');
});

test('si el servidor rechaza la conexion, la vista lo dice y bloquea el envio', async () => {
  const contenedor = raiz();

  await montarChat(contenedor, {
    canal: {},
    token: 't',
    conectar: async () => {
      throw new Error('El token de acceso no es valido.');
    },
  });

  expect(contenedor.querySelector('[data-zona="conexion"]').className).toContain('sin-conexion');
  expect(contenedor.querySelector('[data-zona="aviso"] .aviso').className).toContain('aviso--error');
});
