/**
 * «Hablar con soporte» dentro de la ventana del asistente.
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../cliente-chatbot.js';
import { TAMANO_MAXIMO_DE_CAPTURA, TEXTOS_CAPTURAS } from './capturas-chatbot.js';
import {
  CATEGORIAS,
  ESTADOS,
  TEXTOS_SOPORTE,
  avisoDeCaptura,
  avisoDeError,
  avisoDeRechazo,
  crearPanelSoporte,
} from './soporte-chatbot.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const TICKET = {
  ticketId: 't-1',
  categoria: 'SOPORTE_TECNICO',
  asunto: 'No carga mi inventario',
  mensaje: 'Se queda cargando',
  estado: 'RESUELTO',
  respuesta: 'Ya quedó, vuelve a entrar.',
  creadoEn: '2026-09-28T18:00:00Z',
  actualizadoEn: '2026-09-28T19:00:00Z',
};

function clienteFalso(sobrescribir = {}) {
  return {
    abrirTicket: jest.fn(async () => ({ ...TICKET, estado: 'ABIERTO', respuesta: null })),
    misTickets: jest.fn(async () => []),
    ...sobrescribir,
  };
}

async function montar({
  cliente = clienteFalso(),
  autenticado = true,
  login = '/login',
  registro = '/registro',
  imagenes = undefined,
} = {}) {
  const alVolver = jest.fn();
  const panel = crearPanelSoporte({
    cliente,
    sesion: () => ({ autenticado }),
    login,
    registro,
    alVolver,
    ...(imagenes ? { imagenes } : {}),
  });
  document.body.append(panel.elemento);
  panel.abrir();
  await esperar();
  const el = panel.elemento;
  return {
    panel,
    el,
    cliente,
    alVolver,
    formulario: el.querySelector('form'),
    asunto: el.querySelector('input[name="asunto"]'),
    mensaje: el.querySelector('textarea[name="mensaje"]'),
    categoria: el.querySelector('select[name="categoria"]'),
    aviso: () => el.querySelector('.chatbot-soporte__aviso').textContent,
  };
}

async function enviar(vista, asunto, mensaje) {
  vista.asunto.value = asunto;
  vista.mensaje.value = mensaje;
  vista.formulario.dispatchEvent(new Event('submit', { cancelable: true }));
  await esperar();
  await esperar();
}

beforeEach(() => {
  document.body.replaceChildren();
});

describe('visitante', () => {
  // #506 — antes: «necesitas iniciar sesión», un enlace al login sin vuelta y
  // el chat general, que también pide sesión. Quien no tenía cuenta no tenía
  // salida. ms-chatbot.yaml 1.12.0 (RF-CHA-007, actor Visitante): ahora pide
  // ayuda como cualquiera, y la cuenta queda como salida secundaria.
  test('ve el formulario y sus solicitudes, con la nota de su sesión y la cuenta como salida', async () => {
    const vista = await montar({ autenticado: false });

    expect(vista.formulario).not.toBeNull();
    expect(vista.cliente.misTickets).toHaveBeenCalled();
    const nota = vista.el.querySelector('[data-zona="visitante"]');
    expect(nota.textContent).toContain(TEXTOS_SOPORTE.visitante);
    expect(TEXTOS_SOPORTE.visitante).toContain('mientras sigas en esta sesión del navegador');
    const entrar = nota.querySelector('[data-accion="iniciar-sesion"]');
    const crear = nota.querySelector('[data-accion="crear-cuenta"]');
    expect(entrar.getAttribute('href')).toBe('/login');
    expect(entrar.textContent).toBe(TEXTOS_SOPORTE.iniciarSesion);
    expect(entrar.classList.contains('boton--secundario')).toBe(true);
    expect(crear.getAttribute('href')).toBe('/registro');
    expect(crear.textContent).toBe('Crear cuenta');
    expect(vista.el.querySelector('[data-accion="chat-general"]')).toBeNull();
    // Nada de «null» pintado como texto.
    expect(vista.el.textContent).not.toContain('null');
  });

  test('un jugador no ve la nota del visitante', async () => {
    const vista = await montar({ autenticado: true });

    expect(vista.el.querySelector('[data-zona="visitante"]')).toBeNull();
    expect(vista.el.textContent).not.toContain('null');
  });

  test('las rutas pueden darse como funciones, que se leen al abrir el panel', async () => {
    let volver = '/cuentas/subastas.html';
    const login = jest.fn(() => `/login?volver=${encodeURIComponent(volver)}`);
    const vista = await montar({ autenticado: false, login, registro: () => '/registro' });
    expect(vista.el.querySelector('[data-accion="iniciar-sesion"]').getAttribute('href')).toBe(
      '/login?volver=%2Fcuentas%2Fsubastas.html',
    );

    volver = '/plataforma/torneos/torneos.html';
    vista.panel.abrir();
    expect(vista.el.querySelector('[data-accion="iniciar-sesion"]').getAttribute('href')).toBe(
      '/login?volver=%2Fplataforma%2Ftorneos%2Ftorneos.html',
    );
    expect(login).toHaveBeenCalledTimes(2);
  });

  test('sin ruta de login ni de registro, el texto sigue y no hay enlaces rotos', async () => {
    const vista = await montar({ autenticado: false, login: null, registro: null });

    expect(vista.el.textContent).toContain(TEXTOS_SOPORTE.visitante);
    expect(vista.el.querySelector('[data-accion="iniciar-sesion"]')).toBeNull();
    expect(vista.el.querySelector('[data-accion="crear-cuenta"]')).toBeNull();
  });
});

describe('jugador', () => {
  test('ve el formulario con las 8 categorías y sus solicitudes', async () => {
    const vista = await montar({
      cliente: clienteFalso({ misTickets: jest.fn(async () => [TICKET]) }),
    });

    expect(vista.categoria.options).toHaveLength(CATEGORIAS.length);
    expect(vista.asunto.getAttribute('maxlength')).toBe('150');
    expect(vista.mensaje.getAttribute('maxlength')).toBe('2000');
    const item = vista.el.querySelector('.chatbot-soporte__ticket');
    expect(item.textContent).toContain('No carga mi inventario');
    expect(item.textContent).toContain(ESTADOS.RESUELTO.texto);
    expect(item.textContent).toContain('Ya quedó, vuelve a entrar.');
  });

  test('sin solicitudes lo dice', async () => {
    const vista = await montar();

    expect(vista.el.querySelector('.chatbot-soporte__vacio').textContent).toBe(
      TEXTOS_SOPORTE.ninguna,
    );
  });

  test('si la lista no carga lo dice sin romper el formulario', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        misTickets: jest.fn(async () => {
          throw new ErrorDelChatbot(null, 0);
        }),
      }),
    });

    expect(vista.el.querySelector('.chatbot-soporte__vacio').textContent).toBe(
      TEXTOS_SOPORTE.errorAlCargar,
    );
    expect(vista.formulario).not.toBeNull();
  });

  test('el texto del servidor nunca se interpreta como HTML', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        misTickets: jest.fn(async () => [{ ...TICKET, asunto: '<img src=x onerror=alert(1)>' }]),
      }),
    });

    expect(vista.el.querySelector('img')).toBeNull();
    expect(vista.el.querySelector('.chatbot-soporte__asunto').textContent).toBe(
      '<img src=x onerror=alert(1)>',
    );
  });

  test('sin asunto o sin mensaje no envía y marca el campo', async () => {
    const vista = await montar();

    await enviar(vista, '   ', 'algo');
    expect(vista.cliente.abrirTicket).not.toHaveBeenCalled();
    expect(vista.el.textContent).toContain(TEXTOS_SOPORTE.faltaAsunto);

    await enviar(vista, 'Asunto', '  ');
    expect(vista.cliente.abrirTicket).not.toHaveBeenCalled();
    expect(vista.el.textContent).toContain(TEXTOS_SOPORTE.faltaMensaje);
  });

  test('envía la solicitud recortada, avisa y recarga la lista', async () => {
    const vista = await montar();
    vista.categoria.value = 'CUENTA_Y_REGISTRO';

    await enviar(vista, '  No entro  ', '  Olvidé mi contraseña  ');

    expect(vista.cliente.abrirTicket).toHaveBeenCalledWith({
      categoria: 'CUENTA_Y_REGISTRO',
      asunto: 'No entro',
      mensaje: 'Olvidé mi contraseña',
    });
    expect(vista.aviso()).toContain(TEXTOS_SOPORTE.enviada);
    expect(vista.asunto.value).toBe('');
    expect(vista.mensaje.value).toBe('');
    expect(vista.cliente.misTickets).toHaveBeenCalledTimes(2);
  });

  test('un error del servidor se explica por su motivo', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        abrirTicket: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'x', motivo: 'TICKET_ABIERTO' }, 409);
        }),
      }),
    });

    await enviar(vista, 'Asunto', 'Mensaje');

    expect(vista.aviso()).toContain(TEXTOS_SOPORTE.yaAbierta);
    expect(vista.asunto.value).toBe('Asunto');
  });

  test('Volver al chat avisa a la ventana', async () => {
    const vista = await montar();

    vista.el.querySelector('[data-accion="volver-al-chat"]').click();

    expect(vista.alVolver).toHaveBeenCalled();
  });

  test('enfocar lleva el foco al primer control', async () => {
    const vista = await montar();

    vista.panel.enfocar();

    expect(document.activeElement).toBe(vista.categoria);
  });
});

describe('avisoDeError', () => {
  const con = (motivo, estado = 400) => new ErrorDelChatbot({ title: 'x', motivo }, estado);

  test.each([
    [con('TICKET_ABIERTO', 409), TEXTOS_SOPORTE.yaAbierta, 'advertencia'],
    [con('SESION_REQUERIDA', 401), TEXTOS_SOPORTE.sinSesion, 'advertencia'],
    [new ErrorDelChatbot(null, 403), TEXTOS_SOPORTE.sinSesion, 'advertencia'],
    [con('CONTENIDO_BLOQUEADO', 422), TEXTOS_SOPORTE.bloqueado, 'advertencia'],
    [con('LIMITE_DE_FRECUENCIA', 429), TEXTOS_SOPORTE.muchas, 'advertencia'],
    // 1.7.0: si lo que no responde es la revisión del texto, se dice eso, no
    // que soporte no está.
    [con('MODERACION_NO_DISPONIBLE', 503), TEXTOS_SOPORTE.sinRevision, 'error'],
    [new ErrorDelChatbot(null, 503), TEXTOS_SOPORTE.noDisponible, 'error'],
    [new ErrorDelChatbot(null, 0), TEXTOS_SOPORTE.noDisponible, 'error'],
    [new ErrorDelChatbot({ title: 'Bad Request' }, 400), TEXTOS_SOPORTE.invalido, 'advertencia'],
    [new ErrorDelChatbot({ title: 'x' }, 500), TEXTOS_SOPORTE.error, 'error'],
    // #506 — contrato 1.4.0, y el 429 del borde, que no trae `motivo`.
    [con('INYECCION_DETECTADA', 422), TEXTOS_SOPORTE.conCodigo, 'advertencia'],
    [new ErrorDelChatbot(null, 429), TEXTOS_SOPORTE.muchas, 'advertencia'],
  ])('%#: se elige el aviso por motivo y estado', (error, titulo, tono) => {
    expect(avisoDeError(error)).toMatchObject({ titulo, tono });
  });

  test('#506: un 429 dice cuántos segundos esperar si el servidor lo dijo', () => {
    expect(
      avisoDeError(
        new ErrorDelChatbot({ motivo: 'LIMITE_DE_FRECUENCIA' }, 429, { reintentarEn: 20 }),
      ).detalle,
    ).toBe('Espera 20 s y vuelve a intentarlo.');
    expect(avisoDeError(new ErrorDelChatbot(null, 429)).detalle).toBe(TEXTOS_SOPORTE.muchasDetalle);
  });

  test('#506: una solicitud con código se explica y conserva lo escrito', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        abrirTicket: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'x', motivo: 'INYECCION_DETECTADA' }, 422);
        }),
      }),
    });

    await enviar(vista, 'Asunto', '<script>alert(1)</script>');

    expect(vista.aviso()).toContain(TEXTOS_SOPORTE.conCodigo);
    expect(vista.aviso()).toContain(TEXTOS_SOPORTE.conCodigoDetalle);
    expect(vista.mensaje.value).toBe('<script>alert(1)</script>');
  });
});

// 1.7.0 — el detalle del 503 de la revisión del texto.
test('sin revisión del texto, explica que no se envió nada', () => {
  const sinRevision = new ErrorDelChatbot({ title: 'x', motivo: 'MODERACION_NO_DISPONIBLE' }, 503);
  expect(avisoDeError(sinRevision).detalle).toContain('no enviamos nada');
});

// --- ms-chatbot.yaml 1.14.0 (§7.4.6): capturas de pantalla ----------------

const CAPTURA = {
  capturaId: 'c-1',
  tipo: 'image/png',
  tamano: 348_160,
  ancho: 1280,
  alto: 720,
  subidaEn: '2026-10-14T15:00:00Z',
};
const ABIERTA = { ...TICKET, estado: 'ABIERTO', respuesta: null };

function png(tamano = 1024, nombre = 'pantallazo.png', tipo = 'image/png') {
  return new File([new Uint8Array(tamano)], nombre, { type: tipo });
}

function elegir(entrada, archivo) {
  Object.defineProperty(entrada, 'files', { value: [archivo], configurable: true });
  entrada.dispatchEvent(new Event('change'));
}

function imagenesFalsas() {
  const urls = [];
  const liberadas = [];
  return {
    urls,
    liberadas,
    crear: () => {
      const url = `blob:${urls.length + 1}`;
      urls.push(url);
      return url;
    },
    liberar: () => liberadas.push(urls.splice(0)),
  };
}

describe('capturas de pantalla (1.14.0)', () => {
  const ticketDe = (vista) => vista.el.querySelector('.chatbot-soporte__ticket');
  const selectorDe = (item) => item.querySelector('[data-accion="elegir-captura"]');

  test('una solicitud abierta ofrece adjuntar, con el aviso de privacidad antes de elegir', async () => {
    const vista = await montar({
      cliente: clienteFalso({ misTickets: jest.fn(async () => [ABIERTA]) }),
    });

    const entrada = selectorDe(ticketDe(vista));
    expect(entrada.getAttribute('accept')).toBe('image/png,image/jpeg');
    const pista = document.getElementById(entrada.getAttribute('aria-describedby'));
    expect(pista.textContent).toBe(TEXTOS_SOPORTE.pistaCaptura);
    expect(TEXTOS_SOPORTE.pistaCaptura).toContain('contraseñas, códigos ni datos de tarjetas');
    expect(ticketDe(vista).textContent).toContain(TEXTOS_SOPORTE.adjuntarCaptura);
    expect(vista.el.textContent).not.toContain('null');
  });

  test('una respondida enseña las que tiene, sin ofrecer más; sin capturas ni sitio, nada', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        misTickets: jest.fn(async () => [
          { ...TICKET, ticketId: 't-1', capturas: [CAPTURA] },
          { ...TICKET, ticketId: 't-2' },
          { ...ABIERTA, ticketId: 't-3', capturas: [CAPTURA, CAPTURA, CAPTURA] },
        ]),
      }),
    });

    const [respondida, sinNada, llena] = vista.el.querySelectorAll('.chatbot-soporte__ticket');
    expect(respondida.querySelectorAll('[data-accion="ver-captura"]')).toHaveLength(1);
    expect(selectorDe(respondida)).toBeNull();
    expect(sinNada.querySelector('[data-zona="capturas"]')).toBeNull();
    expect(llena.querySelectorAll('[data-accion="ver-captura"]')).toHaveLength(3);
    expect(selectorDe(llena)).toBeNull();
  });

  test('«Ver captura» la pide con la solicitud y la captura, y al repintar se liberan los enlaces', async () => {
    const imagenes = imagenesFalsas();
    const cliente = clienteFalso({
      misTickets: jest.fn(async () => [{ ...TICKET, capturas: [CAPTURA] }]),
      leerCaptura: jest.fn(async () => new Blob(['x'], { type: 'image/png' })),
    });
    const vista = await montar({ cliente, imagenes });

    vista.el.querySelector('[data-accion="ver-captura"]').click();
    await esperar();

    expect(cliente.leerCaptura).toHaveBeenCalledWith('t-1', 'c-1');
    expect(vista.el.querySelector('.chatbot-soporte__ticket img').getAttribute('src')).toBe(
      'blob:1',
    );

    vista.panel.abrir();
    await esperar();
    expect(imagenes.liberadas.flat()).toContain('blob:1');
  });

  test('adjuntar la sube y la solicitud se repinta con ella, con el foco en su «Ver captura»', async () => {
    const archivo = png();
    const cliente = clienteFalso({
      misTickets: jest.fn(async () => [ABIERTA]),
      subirCaptura: jest.fn(async () => CAPTURA),
    });
    const vista = await montar({ cliente });

    elegir(selectorDe(ticketDe(vista)), archivo);
    await esperar();

    expect(cliente.subirCaptura).toHaveBeenCalledWith('t-1', archivo);
    const item = ticketDe(vista);
    const ver = item.querySelector('[data-accion="ver-captura"]');
    expect(ver.textContent).toBe(TEXTOS_CAPTURAS.ver(1));
    expect(document.activeElement).toBe(ver);
    expect(item.querySelector('[data-zona="aviso-captura"]').textContent).toContain(
      TEXTOS_SOPORTE.capturaAdjunta,
    );
    // Sigue admitiendo (1 de 3) y no hizo falta releer la lista.
    expect(selectorDe(item)).not.toBeNull();
    expect(cliente.misTickets).toHaveBeenCalledTimes(1);
  });

  test('mientras sube, el selector espera; un rechazo del servidor se explica en la solicitud', async () => {
    let rechazar;
    const cliente = clienteFalso({
      misTickets: jest.fn(async () => [ABIERTA]),
      subirCaptura: jest.fn(
        () =>
          new Promise((_, falla) => {
            rechazar = falla;
          }),
      ),
    });
    const vista = await montar({ cliente });
    const entrada = selectorDe(ticketDe(vista));

    elegir(entrada, png());
    expect(entrada.disabled).toBe(true);
    expect(ticketDe(vista).textContent).toContain(TEXTOS_SOPORTE.subiendoCaptura);
    // Una segunda mientras tanto no se sube.
    elegir(entrada, png());
    expect(cliente.subirCaptura).toHaveBeenCalledTimes(1);

    rechazar(new ErrorDelChatbot({ title: 'x', motivo: 'CAPTURAS_COMPLETAS' }, 409));
    await esperar();

    expect(entrada.disabled).toBe(false);
    expect(ticketDe(vista).querySelector('[data-zona="aviso-captura"]').textContent).toContain(
      TEXTOS_SOPORTE.capturasCompletas,
    );
  });

  test('un archivo de otro tipo o de más de 2 MB no se sube', async () => {
    const cliente = clienteFalso({
      misTickets: jest.fn(async () => [ABIERTA]),
      subirCaptura: jest.fn(),
    });
    const vista = await montar({ cliente });
    const aviso = () => ticketDe(vista).querySelector('[data-zona="aviso-captura"]').textContent;

    elegir(selectorDe(ticketDe(vista)), png(10, 'a.gif', 'image/gif'));
    expect(aviso()).toContain(TEXTOS_SOPORTE.capturaNoAdmitida);
    elegir(selectorDe(ticketDe(vista)), png(TAMANO_MAXIMO_DE_CAPTURA + 1));
    expect(aviso()).toContain(TEXTOS_SOPORTE.capturaGrande);
    expect(cliente.subirCaptura).not.toHaveBeenCalled();
  });

  describe('en el formulario, opcional', () => {
    const selectorDelFormulario = (vista) =>
      vista.formulario.querySelector('[data-accion="elegir-captura"]');

    test('elegirla no sube nada; se puede quitar', async () => {
      const cliente = clienteFalso({ subirCaptura: jest.fn() });
      const vista = await montar({ cliente });

      elegir(selectorDelFormulario(vista), png(348_160));
      const elegida = vista.el.querySelector('[data-zona="captura-elegida"]');
      expect(elegida.hidden).toBe(false);
      expect(elegida.textContent).toContain('340 KB');
      expect(cliente.subirCaptura).not.toHaveBeenCalled();

      elegida.querySelector('[data-accion="quitar-captura"]').click();
      expect(elegida.hidden).toBe(true);
      expect(document.activeElement).toBe(selectorDelFormulario(vista));
    });

    test('se adjunta en cuanto la solicitud queda abierta', async () => {
      const archivo = png();
      const cliente = clienteFalso({ subirCaptura: jest.fn(async () => CAPTURA) });
      const vista = await montar({ cliente });

      elegir(selectorDelFormulario(vista), archivo);
      await enviar(vista, 'No carga', 'Se queda en blanco');
      await esperar();

      expect(cliente.subirCaptura).toHaveBeenCalledWith('t-1', archivo);
      expect(vista.aviso()).toContain(TEXTOS_SOPORTE.enviada);
      expect(vista.aviso()).toContain(TEXTOS_SOPORTE.conCaptura);
      expect(vista.el.querySelector('[data-zona="captura-elegida"]').hidden).toBe(true);
    });

    test('también con «Crear solicitud con esta conversación»', async () => {
      const archivo = png();
      const cliente = clienteFalso({
        abrirTicketDesdeConversacion: jest.fn(async () => ({ ...ABIERTA, ticketId: 't-7' })),
        subirCaptura: jest.fn(async () => CAPTURA),
      });
      const alVolver = jest.fn();
      const panel = crearPanelSoporte({
        cliente,
        sesion: () => ({ autenticado: true }),
        login: '/login',
        alVolver,
      });
      document.body.append(panel.elemento);
      panel.abrir({ conConversacion: true });
      await esperar();

      elegir(panel.elemento.querySelector('form [data-accion="elegir-captura"]'), archivo);
      panel.elemento.querySelector('[data-accion="crear-solicitud-desde-conversacion"]').click();
      await esperar();
      await esperar();

      expect(cliente.subirCaptura).toHaveBeenCalledWith('t-7', archivo);
      const aviso = panel.elemento.querySelector('.chatbot-soporte__aviso').textContent;
      expect(aviso).toContain(TEXTOS_SOPORTE.creadaDesdeConversacion);
      expect(aviso).toContain(TEXTOS_SOPORTE.conCaptura);
    });

    test('sin captura elegida, la solicitud en un clic no sube nada', async () => {
      const cliente = clienteFalso({
        abrirTicketDesdeConversacion: jest.fn(async () => ({ ...ABIERTA, ticketId: 't-7' })),
        subirCaptura: jest.fn(),
      });
      const panel = crearPanelSoporte({
        cliente,
        sesion: () => ({ autenticado: true }),
        login: '/login',
        alVolver: jest.fn(),
      });
      document.body.append(panel.elemento);
      panel.abrir({ conConversacion: true });
      await esperar();

      panel.elemento.querySelector('[data-accion="crear-solicitud-desde-conversacion"]').click();
      await esperar();
      await esperar();

      expect(cliente.subirCaptura).not.toHaveBeenCalled();
      expect(panel.elemento.querySelector('.chatbot-soporte__aviso').textContent).not.toContain(
        TEXTOS_SOPORTE.conCaptura,
      );
    });

    test('si la captura no entra, la solicitud queda abierta y se dice dónde adjuntarla', async () => {
      const cliente = clienteFalso({
        subirCaptura: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'x', motivo: 'CAPTURA_NO_ADMITIDA' }, 415);
        }),
      });
      const vista = await montar({ cliente });

      elegir(selectorDelFormulario(vista), png());
      await enviar(vista, 'No carga', 'Se queda en blanco');
      await esperar();

      expect(vista.aviso()).toContain(TEXTOS_SOPORTE.enviadaSinCaptura);
      expect(vista.aviso()).toContain(TEXTOS_SOPORTE.capturaNoAdmitida);
      expect(vista.aviso()).toContain('«Tus solicitudes»');
      expect(vista.asunto.value).toBe('');
    });

    test('una de otro tipo no se queda elegida', async () => {
      const vista = await montar();

      elegir(selectorDelFormulario(vista), png(10, 'a.svg', 'image/svg+xml'));

      expect(vista.el.querySelector('[data-zona="captura-elegida"]').hidden).toBe(true);
      expect(vista.aviso()).toContain(TEXTOS_SOPORTE.capturaNoAdmitida);
    });

    test('si la solicitud no se abre, la captura sigue elegida', async () => {
      const cliente = clienteFalso({
        abrirTicket: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'x', motivo: 'TICKET_ABIERTO' }, 409);
        }),
        subirCaptura: jest.fn(),
      });
      const vista = await montar({ cliente });

      elegir(selectorDelFormulario(vista), png());
      await enviar(vista, 'No carga', 'Se queda en blanco');

      expect(cliente.subirCaptura).not.toHaveBeenCalled();
      expect(vista.el.querySelector('[data-zona="captura-elegida"]').hidden).toBe(false);
    });
  });
});

describe('avisoDeCaptura y avisoDeRechazo', () => {
  const con = (motivo, estado) => new ErrorDelChatbot({ title: 'x', motivo }, estado);

  test.each([
    [con('CAPTURA_DEMASIADO_GRANDE', 413), TEXTOS_SOPORTE.capturaGrande, 'advertencia'],
    // El 413 del borde no trae motivo.
    [new ErrorDelChatbot(null, 413), TEXTOS_SOPORTE.capturaGrande, 'advertencia'],
    [con('CAPTURA_NO_ADMITIDA', 415), TEXTOS_SOPORTE.capturaNoAdmitida, 'advertencia'],
    [new ErrorDelChatbot(null, 415), TEXTOS_SOPORTE.capturaNoAdmitida, 'advertencia'],
    [con('CAPTURA_AUSENTE', 400), TEXTOS_SOPORTE.capturaVacia, 'advertencia'],
    [con('CAPTURAS_COMPLETAS', 409), TEXTOS_SOPORTE.capturasCompletas, 'advertencia'],
    [con('SOLICITUD_CERRADA', 409), TEXTOS_SOPORTE.solicitudCerrada, 'advertencia'],
    [con('SOLICITUD_NO_ENCONTRADA', 404), TEXTOS_SOPORTE.solicitudNoEncontrada, 'advertencia'],
    [con('LIMITE_DE_FRECUENCIA', 429), TEXTOS_SOPORTE.muchasCapturas, 'advertencia'],
    [con('SESION_REQUERIDA', 401), TEXTOS_SOPORTE.sinSesion, 'advertencia'],
    [new ErrorDelChatbot(null, 0), TEXTOS_SOPORTE.noDisponible, 'error'],
    [new ErrorDelChatbot({ title: 'x' }, 500), TEXTOS_SOPORTE.capturaError, 'error'],
  ])('%#: por motivo y estado', (error, titulo, tono) => {
    expect(avisoDeCaptura(error)).toMatchObject({ titulo, tono });
  });

  test('un 429 dice cuánto esperar', () => {
    expect(
      avisoDeCaptura(
        new ErrorDelChatbot({ motivo: 'LIMITE_DE_FRECUENCIA' }, 429, { reintentarEn: 7 }),
      ).detalle,
    ).toBe('Espera 7 s y vuelve a intentarlo.');
  });

  test('los rechazos antes de subir', () => {
    expect(avisoDeRechazo('tamano').titulo).toBe(TEXTOS_SOPORTE.capturaGrande);
    expect(avisoDeRechazo('tipo').titulo).toBe(TEXTOS_SOPORTE.capturaNoAdmitida);
    expect(avisoDeRechazo('vacia').titulo).toBe(TEXTOS_SOPORTE.capturaVacia);
  });
});
