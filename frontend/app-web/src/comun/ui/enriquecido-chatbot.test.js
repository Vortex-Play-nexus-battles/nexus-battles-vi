/**
 * Respuestas enriquecidas del asistente y la vista donde está el jugador.
 */

import { jest } from '@jest/globals';

import { MATRIZ } from '../matriz-acceso.js';
import { CLAVES } from '../sesion.js';
import {
  TEXTOS_ENRIQUECIDO,
  VISTA_DEL_CHAT,
  introAntesDePasos,
  pintarEnriquecido,
  vistaDelChat,
} from './enriquecido-chatbot.js';

const url = (destino) => (destino === 'no-existe' ? null : `/vistas/${destino}.html`);

function pintar(enriquecido, { visible } = {}) {
  const alPreguntar = jest.fn();
  const alPedirSoporte = jest.fn();
  const el = pintarEnriquecido(enriquecido, {
    alPreguntar,
    alPedirSoporte,
    url,
    ...(visible ? { visible } : {}),
  });
  return { el, alPreguntar, alPedirSoporte };
}

beforeEach(() => {
  sessionStorage.clear();
});

test('pinta los pasos numerados', () => {
  const { el } = pintar({ pasos: ['Elige el ítem.', 'Confirma.'] });

  const pasos = el.querySelectorAll('.chatbot-enriquecido__pasos li');
  expect([...pasos].map((li) => li.textContent)).toEqual(['Elige el ítem.', 'Confirma.']);
});

test('los enlaces son a vistas del sitio; uno desconocido no se pinta', () => {
  const { el } = pintar({
    enlaces: [
      { texto: 'Ir a Subastas', destino: 'subastas' },
      { texto: 'Afuera', destino: 'no-existe' },
    ],
  });

  const enlaces = el.querySelectorAll('.chatbot-enriquecido__enlace');
  expect(enlaces).toHaveLength(1);
  expect(enlaces[0].getAttribute('href')).toBe('/vistas/subastas.html');
  expect(enlaces[0].textContent).toBe('Ir a Subastas');
});

test('las tarjetas llevan título, texto y su enlace si lo hay', () => {
  const { el } = pintar({
    tarjetas: [
      {
        titulo: 'El Templo Olvidado',
        texto: '40 % completada',
        enlace: { texto: 'Ver', destino: 'misiones' },
      },
      { titulo: 'Sin enlace', texto: 'x', enlace: null },
    ],
  });

  const tarjetas = el.querySelectorAll('.chatbot-enriquecido__tarjeta');
  expect(tarjetas).toHaveLength(2);
  expect(tarjetas[0].textContent).toContain('El Templo Olvidado');
  expect(tarjetas[0].querySelector('a').getAttribute('href')).toBe('/vistas/misiones.html');
  expect(tarjetas[1].querySelector('a')).toBeNull();
});

test('los botones preguntan y «Hablar con soporte» abre el soporte', () => {
  const { el, alPreguntar, alPedirSoporte } = pintar({
    respuestasRapidas: ['Cómo funciona el Torneo'],
    ofrecerSoporteHumano: true,
  });

  el.querySelector('[data-accion="respuesta-rapida"]').click();
  el.querySelector('[data-accion="ofrecer-soporte"]').click();

  expect(alPreguntar).toHaveBeenCalledWith('Cómo funciona el Torneo');
  expect(alPedirSoporte).toHaveBeenCalled();
});

// ms-chatbot.yaml 1.12.0 (RF-CHA-007): la solicitud que se abrió sola.
describe('la solicitud de soporte que abrió el asistente', () => {
  const SOLICITUD = {
    id: '6f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f',
    asunto: 'Sin respuesta del asistente: zxqwv plorf',
    estado: 'ABIERTO',
    nueva: true,
  };

  test('se avisa con su asunto y «Ver solicitud», en lugar de «Hablar con soporte»', () => {
    const alVerSolicitud = jest.fn();
    const el = pintarEnriquecido(
      { respuestasRapidas: [], ofrecerSoporteHumano: true, solicitud: SOLICITUD },
      { alPreguntar: jest.fn(), alPedirSoporte: jest.fn(), alVerSolicitud, url },
    );

    const aviso = el.querySelector('.chatbot-enriquecido__solicitud');
    expect(aviso.dataset.solicitudId).toBe(SOLICITUD.id);
    expect(aviso.textContent).toContain(TEXTOS_ENRIQUECIDO.solicitudAbierta);
    expect(aviso.textContent).toContain('Sin respuesta del asistente: zxqwv plorf');
    expect(el.querySelector('[data-accion="ofrecer-soporte"]')).toBeNull();

    el.querySelector('[data-accion="ver-solicitud"]').click();
    expect(alVerSolicitud).toHaveBeenCalledWith(SOLICITUD);
  });

  test('si la pregunta se sumó a la que ya estaba abierta, lo dice', () => {
    const { el } = pintar({ solicitud: { ...SOLICITUD, nueva: false } });

    expect(el.querySelector('.chatbot-enriquecido__solicitud-titulo').textContent).toBe(
      TEXTOS_ENRIQUECIDO.solicitudSumada,
    );
  });

  test('sin `alVerSolicitud`, «Ver solicitud» abre el soporte', () => {
    const { el, alPedirSoporte } = pintar({ solicitud: SOLICITUD });

    el.querySelector('[data-accion="ver-solicitud"]').click();
    expect(alPedirSoporte).toHaveBeenCalled();
  });

  test('el asunto entra como texto, nunca como HTML', () => {
    const { el } = pintar({ solicitud: { ...SOLICITUD, asunto: '<img src=x onerror=alert(1)>' } });

    expect(el.querySelector('img')).toBeNull();
    expect(el.querySelector('.chatbot-enriquecido__solicitud-asunto').textContent).toBe(
      '<img src=x onerror=alert(1)>',
    );
  });

  test('una solicitud sin id no se pinta y vuelve «Hablar con soporte»', () => {
    const { el } = pintar({ ofrecerSoporteHumano: true, solicitud: { asunto: 'x' } });

    expect(el.querySelector('.chatbot-enriquecido__solicitud')).toBeNull();
    expect(el.querySelector('[data-accion="ofrecer-soporte"]')).not.toBeNull();
  });
});

test('sin nada que pintar devuelve null', () => {
  expect(pintar(null).el).toBeNull();
  expect(
    pintar({ pasos: [], enlaces: [{ destino: 'no-existe' }], tarjetas: [], respuestasRapidas: [] })
      .el,
  ).toBeNull();
});

test('el texto del servidor nunca se interpreta como HTML', () => {
  const { el } = pintar({ pasos: ['<img src=x onerror=alert(1)>'] });

  expect(el.querySelector('img')).toBeNull();
  expect(el.textContent).toContain('<img src=x onerror=alert(1)>');
});

test('introAntesDePasos deja solo lo que va antes del 1)', () => {
  expect(introAntesDePasos('Para publicar: 1) Elige. 2) Confirma.')).toBe('Para publicar:');
  expect(introAntesDePasos('1) Elige. 2) Confirma.')).toBe('');
  expect(introAntesDePasos('Sin pasos.')).toBe('Sin pasos.');
  expect(introAntesDePasos(null)).toBe('');
});

test('vistaDelChat traduce la vista de la matriz a la del contrato', () => {
  expect(vistaDelChat('subastas')).toBe('SUBASTAS');
  expect(vistaDelChat('pujas')).toBe('SUBASTAS');
  expect(vistaDelChat('perfil')).toBe('CUENTA');
  expect(vistaDelChat('panel-chatbot')).toBeNull();
  expect(vistaDelChat(null)).toBeNull();
  expect(Object.values(VISTA_DEL_CHAT)).toEqual(
    expect.arrayContaining(['INICIO', 'INVENTARIO', 'MISIONES', 'TORNEOS', 'SUBASTAS', 'CUENTA']),
  );
});

// #506 — más vistas mandan la sección donde está el jugador.
describe('VISTA_DEL_CHAT (#506)', () => {
  const SEIS = ['INICIO', 'INVENTARIO', 'MISIONES', 'TORNEOS', 'SUBASTAS', 'CUENTA'];

  test.each([
    ['portada', 'INICIO'],
    ['home', 'INICIO'],
    ['publicar-subasta', 'SUBASTAS'],
    ['mis-sanciones', 'CUENTA'],
    ['login', 'CUENTA'],
    ['registro', 'CUENTA'],
    ['restablecer-solicitar', 'CUENTA'],
    ['restablecer-confirmar', 'CUENTA'],
    ['verificar-cuenta', 'CUENTA'],
    ['preparando', 'CUENTA'],
    ['mis-cofres', 'INVENTARIO'],
    ['misiones', 'MISIONES'],
    ['torneos', 'TORNEOS'],
  ])('%s → %s', (vista, seccion) => {
    expect(vistaDelChat(vista)).toBe(seccion);
  });

  test('solo usa los seis valores del contrato y solo vistas que existen', () => {
    for (const [vista, seccion] of Object.entries(VISTA_DEL_CHAT)) {
      expect(SEIS).toContain(seccion);
      expect(MATRIZ[vista]).toBeDefined();
    }
  });

  test('ninguna vista de la consola de operación manda sección', () => {
    const deLaConsola = Object.entries(MATRIZ)
      .filter(([, entrada]) => entrada.armazon === 'admin')
      .map(([id]) => id);
    expect(deLaConsola.length).toBeGreaterThan(0);
    for (const vista of deLaConsola) {
      expect(vistaDelChat(vista)).toBeNull();
    }
  });
});

// #506 — defensa en profundidad: ni un enlace suelto ni el de una tarjeta a
// una vista que la sesión actual no puede ver.
describe('enlaces según quién mira (#506)', () => {
  const RICO = {
    enlaces: [
      { texto: 'Ir a Catálogo', destino: 'productos' },
      { texto: 'Ir a Subastas', destino: 'subastas' },
    ],
    tarjetas: [{ titulo: 'Usuarios', texto: 'x', enlace: { texto: 'Ver', destino: 'auditoria' } }],
  };

  function sesionDe(rol) {
    const cuerpo = btoa(JSON.stringify({ uid: 'u-1', sub: 'Ana', rol, exp: 4102444800 }))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
    sessionStorage.setItem(CLAVES.token, `e.${cuerpo}.f`);
    sessionStorage.setItem(CLAVES.rol, rol);
  }

  test('con la comprobación inyectada, lo que no se ve no se pinta', () => {
    const visible = jest.fn((destino) => destino === 'subastas');
    const { el } = pintar(RICO, { visible });

    expect(
      [...el.querySelectorAll('.chatbot-enriquecido__enlace')].map((a) => a.dataset.destino),
    ).toEqual(['subastas']);
    expect(el.querySelector('.chatbot-enriquecido__tarjeta a')).toBeNull();
    expect(el.querySelector('.chatbot-enriquecido__tarjeta').textContent).toContain('Usuarios');
    expect(visible).toHaveBeenCalledWith('productos');
    expect(visible).toHaveBeenCalledWith('auditoria');
  });

  test('por omisión pregunta a la sesión: a un jugador no le enseña la consola', () => {
    sesionDe('JUGADOR');
    const { el } = pintar(RICO);

    expect(
      [...el.querySelectorAll('.chatbot-enriquecido__enlace')].map((a) => a.dataset.destino),
    ).toEqual(['subastas']);
  });

  test('por omisión, al super administrador sí se los enseña', () => {
    sesionDe('SUPER_ADMINISTRADOR');
    const { el } = pintar(RICO);

    // Primero la tarjeta (con su enlace), luego los enlaces sueltos.
    expect(
      [...el.querySelectorAll('.chatbot-enriquecido__enlace')].map((a) => a.dataset.destino),
    ).toEqual(['auditoria', 'productos', 'subastas']);
  });
});
