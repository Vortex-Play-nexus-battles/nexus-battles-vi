/**
 * UXC-6 — mensajes privados: la vista contra su puerto.
 *
 * No hay servicio de mensajes privados: estas pruebas usan una fuente falsa
 * con la forma de `fuente-mensajes.js` para comprobar que la vista cubre los
 * estados que pidió el docente. Los apodos son datos de prueba.
 */

import { jest } from '@jest/globals';

import {
  elementoDeConversacion,
  montarMensajesPrivados,
  motivoDeBloqueo,
  vistaPrevia,
} from './mensajes-privados.js';
import { FUENTE_SIN_SERVICIO, fuenteDeMensajes, MensajesSinAbrir } from './fuente-mensajes.js';

const YO = 'uid-yo';
const BRUMA = { id: 'uid-bruma', apodo: 'Bruma' };
const KAEL = { id: 'uid-kael', apodo: 'Kael' };

const esperar = async (vueltas = 6) => {
  for (let i = 0; i < vueltas; i += 1) {
    await Promise.resolve();
  }
};

function resumen(con, cambios = {}) {
  return {
    id: `c-${con.apodo.toLowerCase()}`,
    con,
    ultimo: { texto: '¿Revancha a las 8?', enviadoEn: '2026-09-22T15:05:00Z', deMi: false },
    noLeidos: 0,
    estado: 'ACTIVA',
    ...cambios,
  };
}

function mensaje(id, autor, texto, enviadoEn = '2026-09-22T15:00:00Z', extra = {}) {
  return { id, tipo: 'mensaje', autor, texto, enviadoEn, ...extra };
}

function fuenteFalsa(cambios = {}) {
  const oyentes = [];
  return {
    disponible: true,
    oyentes,
    emitir: (evento) => oyentes.forEach((o) => o(evento)),
    bandeja: jest.fn(async () => ({
      conversaciones: [resumen(BRUMA, { noLeidos: 2 }), resumen(KAEL, { ultimo: null })],
      restriccion: null,
    })),
    buscarJugadores: jest.fn(async () => [KAEL, { id: YO, apodo: 'Yo mismo' }]),
    conversacionCon: jest.fn(async (id) => resumen(id === KAEL.id ? KAEL : BRUMA)),
    hilo: jest.fn(async () => ({
      mensajes: [
        mensaje('m1', BRUMA, '¿Revancha a las 8?'),
        mensaje('m2', { id: YO, apodo: 'Simón' }, 'Hecho', '2026-09-22T15:01:00Z', {
          entrega: 'ENVIADO',
        }),
      ],
    })),
    enviar: jest.fn(async (_c, texto) =>
      mensaje('m-nuevo', { id: YO, apodo: 'Simón' }, texto, new Date().toISOString(), {
        entrega: 'ENVIADO',
      }),
    ),
    marcarLeida: jest.fn(async () => {}),
    bloquear: jest.fn(async (_id, bloquear) =>
      resumen(BRUMA, { estado: bloquear ? 'BLOQUEADA' : 'ACTIVA' }),
    ),
    escuchar: jest.fn((oyente) => {
      oyentes.push(oyente);
      return () => {};
    }),
    reintentar: jest.fn(),
    ...cambios,
  };
}

function zona() {
  document.body.innerHTML = '<section id="privados"></section>';
  return document.getElementById('privados');
}

async function montar(fuente = fuenteFalsa(), opciones = {}) {
  const raiz = zona();
  const vista = await montarMensajesPrivados(raiz, {
    fuente,
    miId: YO,
    demoraDeBusqueda: 0,
    confirmarBloqueo: async () => true,
    ...opciones,
  });
  return { raiz, vista, fuente };
}

async function abrir(raiz, id = 'c-bruma') {
  raiz.querySelector(`[data-conversacion="${id}"]`).click();
  await esperar();
}

describe('sin servicio', () => {
  test('la fuente de hoy no está disponible y no toca la red', async () => {
    expect(fuenteDeMensajes()).toBe(FUENTE_SIN_SERVICIO);
    expect(FUENTE_SIN_SERVICIO.disponible).toBe(false);
    await expect(FUENTE_SIN_SERVICIO.bandeja()).rejects.toBeInstanceOf(MensajesSinAbrir);
  });

  test('la vista dice qué pasa, por qué y qué hacer ya, sin fingir conversaciones', async () => {
    const alIrAlChatGeneral = jest.fn();
    const raiz = zona();
    const { estado } = await montarMensajesPrivados(raiz, {
      fuente: FUENTE_SIN_SERVICIO,
      alIrAlChatGeneral,
      hrefCrearSala: './crear-sala.html',
    });

    expect(estado).toBe('sin-abrir');
    expect(raiz.querySelector('h2').textContent).toBe(
      'Los mensajes privados todavía no están abiertos',
    );
    expect(raiz.textContent).toContain('Aún no hay un servicio');
    expect(raiz.querySelector('.conversaciones__item')).toBeNull();
    expect(raiz.querySelector('textarea')).toBeNull();
    expect(raiz.querySelector('a').getAttribute('href')).toBe('./crear-sala.html');
    raiz.querySelector('[data-accion="ir-al-chat-general"]').click();
    expect(alIrAlChatGeneral).toHaveBeenCalled();
  });
});

describe('la lista de conversaciones', () => {
  test('cada conversación: apodo, lo último, cuándo y los no leídos también en palabras', async () => {
    const { raiz } = await montar();
    const [bruma, kael] = raiz.querySelectorAll('.conversaciones__item');

    expect(bruma.querySelector('.conversaciones__apodo').textContent).toBe('Bruma');
    expect(bruma.querySelector('.conversaciones__vista').textContent).toBe('¿Revancha a las 8?');
    expect(bruma.querySelector('.conversaciones__no-leidos').textContent).toContain(
      '2 mensajes sin leer',
    );
    expect(kael.querySelector('.conversaciones__vista').textContent).toBe('Sin mensajes todavía');
  });

  test('vista previa: lo tuyo empieza por «Tú:»', () => {
    expect(vistaPrevia({ texto: 'gg', deMi: true })).toBe('Tú: gg');
    expect(vistaPrevia(null)).toBe('Sin mensajes todavía');
  });

  test('una conversación que no admite mensajes lo dice en la lista', () => {
    const item = elementoDeConversacion(resumen(BRUMA, { estado: 'CUENTA_SANCIONADA' }));
    expect(item.textContent).toContain('Cuenta sancionada');
    expect(motivoDeBloqueo('ACTIVA', 'Bruma')).toBeNull();
  });

  test('vacía: lo dice y ofrece buscar a alguien', async () => {
    const fuente = fuenteFalsa({
      bandeja: jest.fn(async () => ({ conversaciones: [], restriccion: null })),
    });
    const { raiz } = await montar(fuente);

    const vacio = raiz.querySelector('[data-zona="conversaciones"] .estado-vista--vacio');
    expect(vacio.textContent).toContain('Todavía no tienes conversaciones');
    vacio.querySelector('[data-accion="enfocar-busqueda"]').click();
    expect(document.activeElement).toBe(raiz.querySelector('#buscar-jugador'));
  });

  test('un fallo se dice sin códigos y «Reintentar» vuelve a pedir', async () => {
    const fuente = fuenteFalsa();
    fuente.bandeja
      .mockRejectedValueOnce(Object.assign(new Error('x'), { status: 502 }))
      .mockResolvedValueOnce({ conversaciones: [resumen(BRUMA)], restriccion: null });
    const { raiz, vista } = await montar(fuente);

    expect(vista.estado).toBe('error');
    const error = raiz.querySelector('.estado-vista--error');
    expect(error.textContent).toContain('No pudimos cargar tus conversaciones');
    expect(error.textContent).not.toMatch(/502/);
    error.querySelector('[data-accion="reintentar"]').click();
    await esperar();
    expect(raiz.querySelectorAll('.conversaciones__item')).toHaveLength(1);
  });
});

describe('buscar jugador', () => {
  test('pide al menos dos letras, no se encuentra a sí mismo y abre la conversación', async () => {
    const { raiz, fuente } = await montar();
    const campo = raiz.querySelector('#buscar-jugador');

    campo.value = 'k';
    campo.dispatchEvent(new Event('input'));
    await new Promise((r) => setTimeout(r, 5));
    expect(fuente.buscarJugadores).not.toHaveBeenCalled();

    campo.value = 'ka';
    campo.dispatchEvent(new Event('input'));
    await new Promise((r) => setTimeout(r, 5));
    await esperar();
    expect(fuente.buscarJugadores).toHaveBeenCalledWith('ka');
    const resultados = [...raiz.querySelectorAll('.buscador-jugador__resultado')];
    expect(resultados.map((b) => b.getAttribute('aria-label'))).toEqual(['Escribir a Kael']);

    resultados[0].click();
    await esperar();
    expect(fuente.conversacionCon).toHaveBeenCalledWith(KAEL.id);
    expect(raiz.querySelector('.mensajes-privados__con').textContent).toBe('Kael');
    expect(campo.value).toBe('');
  });

  test('sin resultados y con fallo, lo dice', async () => {
    const fuente = fuenteFalsa({
      buscarJugadores: jest
        .fn()
        .mockResolvedValueOnce([])
        .mockRejectedValueOnce(new Error('caído')),
    });
    const { raiz } = await montar(fuente);
    const campo = raiz.querySelector('#buscar-jugador');

    campo.value = 'zz';
    campo.dispatchEvent(new Event('input'));
    await new Promise((r) => setTimeout(r, 5));
    await esperar();
    expect(raiz.querySelector('[data-zona="resultados"]').textContent).toContain(
      'Ningún jugador tiene un apodo con «zz»',
    );

    raiz
      .querySelector('.buscador-jugador')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    expect(raiz.querySelector('.buscador-jugador__estado--error').textContent).toContain(
      'No pudimos buscar ahora',
    );
  });
});

describe('la conversación', () => {
  test('abrirla carga el hilo, marca como leída y enfoca su nombre', async () => {
    const { raiz, fuente } = await montar();
    await abrir(raiz);

    expect(fuente.hilo).toHaveBeenCalledWith('c-bruma');
    const globos = [...raiz.querySelectorAll('li.mensaje')];
    expect(globos.map((g) => g.dataset.quien)).toEqual(['otro', 'yo']);
    expect(globos[1].querySelector('.mensaje__entrega').textContent).toBe('Enviado');
    expect(fuente.marcarLeida).toHaveBeenCalledWith('c-bruma');
    expect(
      raiz.querySelector('[data-conversacion="c-bruma"] .conversaciones__no-leidos'),
    ).toBeNull();
    expect(raiz.querySelector('[data-conversacion="c-bruma"]').getAttribute('aria-current')).toBe(
      'true',
    );
    expect(document.activeElement).toBe(raiz.querySelector('#mensajes-privados-con'));
  });

  test('una conversación nueva, sin mensajes, invita a escribir el primero', async () => {
    const fuente = fuenteFalsa({ hilo: jest.fn(async () => ({ mensajes: [] })) });
    const { raiz } = await montar(fuente);
    await abrir(raiz, 'c-kael');

    expect(raiz.querySelector('[data-zona="sin-mensajes"]').textContent).toContain(
      'Escribe el primer mensaje a Kael',
    );
  });

  test('si el hilo no carga, lo dice y deja reintentar', async () => {
    const fuente = fuenteFalsa();
    fuente.hilo.mockRejectedValueOnce(new Error('x'));
    const { raiz } = await montar(fuente);
    await abrir(raiz);

    const error = raiz.querySelector('[data-zona="sin-mensajes"] .estado-vista--error');
    expect(error.textContent).toContain('No pudimos cargar la conversación');
    error.querySelector('[data-accion="reintentar"]').click();
    await esperar();
    expect(raiz.querySelectorAll('li.mensaje')).toHaveLength(2);
  });

  test('enviar: primero «Enviando…», luego «Enviado», y sube la conversación', async () => {
    const { raiz, fuente } = await montar();
    await abrir(raiz, 'c-kael');
    const formulario = raiz.querySelector('.redactor-mensaje');
    formulario.elements.texto.value = 'gg';
    formulario.dispatchEvent(new Event('submit', { cancelable: true }));

    expect(raiz.querySelector('li.mensaje--yo:last-of-type .mensaje__entrega').textContent).toBe(
      'Enviando…',
    );
    await esperar();
    expect(fuente.enviar).toHaveBeenCalledWith('c-kael', 'gg');
    const ultimo = [...raiz.querySelectorAll('li.mensaje--yo')].at(-1);
    expect(ultimo.querySelector('.mensaje__entrega').textContent).toBe('Enviado');
    const primera = raiz.querySelector('.conversaciones__item');
    expect(primera.dataset.conversacion).toBe('c-kael');
    expect(primera.querySelector('.conversaciones__vista').textContent).toBe('Tú: gg');
  });

  test('si no sale: «No se envió» con «Reintentar», que lo vuelve a mandar', async () => {
    const fuente = fuenteFalsa();
    fuente.enviar.mockRejectedValueOnce(new Error('sin red'));
    const { raiz } = await montar(fuente);
    await abrir(raiz);
    const formulario = raiz.querySelector('.redactor-mensaje');
    formulario.elements.texto.value = 'hola';
    formulario.dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();

    const fallido = [...raiz.querySelectorAll('li.mensaje--yo')].at(-1);
    expect(fallido.querySelector('.mensaje__entrega').textContent).toContain('No se envió');
    fallido.querySelector('[data-accion="reintentar-mensaje"]').click();
    await esperar();
    expect(fuente.enviar).toHaveBeenCalledTimes(2);
    const reenviado = [...raiz.querySelectorAll('li.mensaje--yo')].at(-1);
    expect(reenviado.querySelector('.mensaje__entrega').textContent).toBe('Enviado');
    expect(raiz.querySelectorAll('li.mensaje--yo')).toHaveLength(2);
  });

  test('lo que llega en vivo: al hilo abierto, o a la lista con su no leído y un anuncio', async () => {
    const { raiz, fuente } = await montar();
    await abrir(raiz);

    fuente.emitir({
      tipo: 'mensaje',
      conversacionId: 'c-bruma',
      mensaje: mensaje('m3', BRUMA, '¿Vienes?', '2026-09-22T15:10:00Z'),
    });
    expect([...raiz.querySelectorAll('li.mensaje')].at(-1).textContent).toContain('¿Vienes?');

    fuente.emitir({
      tipo: 'mensaje',
      conversacionId: 'c-kael',
      mensaje: mensaje('m4', KAEL, 'Hola', '2026-09-22T15:11:00Z'),
    });
    const kael = raiz.querySelector('[data-conversacion="c-kael"]');
    expect(kael.querySelector('.conversaciones__no-leidos').textContent).toContain(
      '1 mensaje sin leer',
    );
    await new Promise((r) => setTimeout(r, 40));
    expect(raiz.querySelector('[role="status"].solo-lectores').textContent).toBe(
      'Nuevo mensaje de Kael.',
    );
  });

  test('«Leído» llega a lo tuyo cuando el otro lo lee', async () => {
    const { raiz, fuente } = await montar();
    await abrir(raiz);
    fuente.emitir({ tipo: 'leido', conversacionId: 'c-bruma', hasta: '2026-09-22T15:02:00Z' });
    expect(raiz.querySelector('li.mensaje--yo .mensaje__entrega').textContent).toBe('Leído');
  });

  test('reconexión: la píldora lo dice, el envío espera y el hilo lo cuenta', async () => {
    const { raiz, fuente } = await montar();
    await abrir(raiz);

    fuente.emitir({ tipo: 'canal', estado: 'reconectando', intento: 1, de: 5 });
    expect(raiz.querySelector('[data-zona="conexion-privados"]').textContent).toMatch(
      /Reconectando… \(intento 1 de 5\)/,
    );
    expect(raiz.querySelector('.redactor-mensaje [type="submit"]').disabled).toBe(true);
    expect(raiz.querySelector('.mensaje--sistema').textContent).toContain('Se cortó la conexión');

    fuente.emitir({ tipo: 'canal', estado: 'reconectado' });
    expect(raiz.querySelector('.redactor-mensaje [type="submit"]').disabled).toBe(false);
    expect(raiz.textContent).toContain('Conexión recuperada');

    fuente.emitir({ tipo: 'canal', estado: 'sin-conexion' });
    raiz.querySelector('[data-accion="reintentar-canal"]').click();
    expect(fuente.reintentar).toHaveBeenCalled();
  });
});

describe('bloqueos y sanciones', () => {
  test('bloquear pide confirmación, sustituye el campo y deja desbloquear', async () => {
    const confirmarBloqueo = jest.fn(async () => true);
    const { raiz, fuente } = await montar(fuenteFalsa(), { confirmarBloqueo });
    await abrir(raiz);

    raiz.querySelector('[data-accion="bloquear"]').click();
    await esperar();
    expect(confirmarBloqueo).toHaveBeenCalledWith(
      expect.objectContaining({ titulo: '¿Bloquear a Bruma?', textoConfirmar: 'Bloquear' }),
    );
    expect(fuente.bloquear).toHaveBeenCalledWith(BRUMA.id, true);
    const bloqueo = raiz.querySelector('[data-zona="bloqueo"]');
    expect(bloqueo.textContent).toContain('Bloqueaste a Bruma');

    [...bloqueo.querySelectorAll('button')].find((b) => b.textContent === 'Desbloquear').click();
    await esperar();
    expect(fuente.bloquear).toHaveBeenLastCalledWith(BRUMA.id, false);
    expect(raiz.querySelector('[data-zona="bloqueo"]').hidden).toBe(true);
  });

  test('si cancelas la confirmación, no se bloquea', async () => {
    const { raiz, fuente } = await montar(fuenteFalsa(), { confirmarBloqueo: async () => false });
    await abrir(raiz);
    raiz.querySelector('[data-accion="bloquear"]').click();
    await esperar();
    expect(fuente.bloquear).not.toHaveBeenCalled();
  });

  test('la cuenta del otro sancionada: no se escribe y no se ofrece bloquear', async () => {
    const fuente = fuenteFalsa({
      bandeja: jest.fn(async () => ({
        conversaciones: [resumen(BRUMA, { estado: 'CUENTA_SANCIONADA' })],
        restriccion: null,
      })),
    });
    const { raiz } = await montar(fuente);
    await abrir(raiz);

    expect(raiz.querySelector('[data-zona="bloqueo"]').textContent).toContain(
      'La cuenta de Bruma está sancionada',
    );
    expect(raiz.querySelector('[data-accion="bloquear"]')).toBeNull();
  });

  test('tu silencio: aviso con su cuenta atrás y el campo bloqueado en cada conversación', async () => {
    const hasta = new Date(Date.now() + 3 * 86_400_000).toISOString();
    const fuente = fuenteFalsa({
      bandeja: jest.fn(async () => ({
        conversaciones: [resumen(BRUMA)],
        restriccion: { tipo: 'SILENCIO', hasta, motivo: 'Lenguaje ofensivo' },
      })),
    });
    const { raiz, vista } = await montar(fuente);

    const aviso = raiz.querySelector('[data-zona="restriccion"]');
    expect(aviso.hidden).toBe(false);
    expect(aviso.textContent).toContain('Tienes un silencio activo');
    expect(aviso.querySelector('time').getAttribute('datetime')).toBe(hasta);
    expect(aviso.querySelector('a').getAttribute('href')).toMatch(/mis-sanciones\.html$/);

    await abrir(raiz);
    expect(raiz.querySelector('[data-zona="bloqueo"]').textContent).toContain(
      'Tienes un silencio activo',
    );
    expect(raiz.querySelector('.redactor-mensaje textarea').disabled).toBe(true);
    vista.detener();
  });
});

test('en pantalla estrecha, «Conversaciones» vuelve a la lista con el foco en la que estaba', async () => {
  const { raiz } = await montar();
  await abrir(raiz);
  const contenedor = raiz.querySelector('.mensajes-privados');
  expect(contenedor.dataset.vistaMovil).toBe('hilo');

  raiz.querySelector('[data-accion="volver-a-conversaciones"]').click();
  expect(contenedor.dataset.vistaMovil).toBe('lista');
  expect(document.activeElement).toBe(raiz.querySelector('[data-conversacion="c-bruma"]'));
});
