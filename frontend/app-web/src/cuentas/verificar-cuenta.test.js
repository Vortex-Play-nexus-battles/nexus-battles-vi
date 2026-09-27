/**
 * «Confirma tu correo» — B1 (identidad 2.0.0).
 *
 * Se monta sobre el marcado real de `verificar-cuenta.html`, con respuestas de
 * la forma exacta del contrato. El reloj es manual y la navegación, el
 * historial y la ubicación se inyectan: aquí nada sale de la prueba.
 *
 * Lo que se promete y se prueba:
 *   - el enlace del correo rellena los dos campos, se borra de la barra y
 *     espera un clic (no se confirma solo);
 *   - el código se acepta como se pegue y se envía como el servidor lo espera;
 *   - al confirmar, al login con el correo en `sessionStorage` y nunca en la URL;
 *   - cada rechazo se explica por su `type`, y el foco va a donde se arregla;
 *   - reenviar dice siempre lo mismo y respeta la espera de un minuto.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

import { CLAVES_DEL_CORREO } from '../comun/codigo-de-correo.js';
import { CLAVE_CORREO_REGISTRADO } from '../comun/entrada.js';
import { MENSAJE_DE_REENVIO } from '../comun/verificacion.js';
import {
  TEXTO_REENVIAR,
  introduccion,
  montarVerificacion,
  textoDeEspera,
} from './verificar-cuenta.js';

const MARCADO = readFileSync(new URL('./verificar-cuenta.html', import.meta.url), 'utf8');
const BASE = 'http://localhost:8099/frontend/app-web/src/comun/sesion.js';
const INICIO = 1_790_000_000_000;

function crearReloj(inicio = INICIO) {
  let ahora = inicio;
  let siguienteId = 1;
  const pendientes = new Map();
  return {
    ahora: () => ahora,
    setTimeout(funcion, ms) {
      const id = siguienteId++;
      pendientes.set(id, { funcion, cuando: ahora + ms });
      return id;
    },
    clearTimeout(id) {
      pendientes.delete(id);
    },
    avanzar(ms) {
      const hasta = ahora + ms;
      while (true) {
        const vencidos = [...pendientes].filter(([, p]) => p.cuando <= hasta);
        if (vencidos.length === 0) {
          break;
        }
        const [id, { funcion, cuando }] = vencidos.sort((a, b) => a[1].cuando - b[1].cuando)[0];
        pendientes.delete(id);
        ahora = cuando;
        funcion();
      }
      ahora = hasta;
    },
  };
}

const tick = () => new Promise((resolver) => setTimeout(resolver, 0));
const zona = (nombre) => document.querySelector(`[data-zona="${nombre}"]`);
const campoCorreo = () => document.getElementById('email');
const campoCodigo = () => document.getElementById('codigo');
const botonReenviar = () => document.querySelector('[data-accion="reenviar"]');

function montar({
  hash = '',
  search = '',
  confirmar = jest.fn(() => Promise.resolve({ ok: true, estado: 200, mensaje: null })),
  reenviar = jest.fn(() => Promise.resolve({ ok: true, estado: 202, mensaje: MENSAJE_DE_REENVIO })),
} = {}) {
  const reloj = crearReloj();
  const historial = { state: null, replaceState: jest.fn() };
  const navegar = jest.fn();
  const vista = montarVerificacion(document, {
    confirmar,
    reenviar,
    almacen: sessionStorage,
    ubicacion: { hash, search, pathname: '/verificar' },
    historial,
    reloj,
    ahora: reloj.ahora,
    navegar,
    base: BASE,
  });
  return { reloj, historial, navegar, confirmar, reenviar, vista };
}

function enviar() {
  document
    .getElementById('formVerificacion')
    .dispatchEvent(new Event('submit', { cancelable: true }));
}

beforeEach(() => {
  document.documentElement.innerHTML = MARCADO.replace(/<script[\s\S]*?<\/script>/g, '');
  sessionStorage.clear();
});

describe('lo primero que se lee', () => {
  test('tras registrarse dice adónde fue el código', () => {
    expect(introduccion('registro', 'ana@nexus.test')).toBe(
      'Te enviamos un código de 8 caracteres a ana@nexus.test. Escríbelo aquí para activar tu cuenta.',
    );
    expect(introduccion('registro', '')).toContain('a tu correo');
  });

  test('desde el login, desde el enlace y sin motivo, cada uno lo suyo', () => {
    expect(introduccion('login', 'x')).toContain('todavía no está activa');
    expect(introduccion('enlace', 'x')).toContain('«Confirmar»');
    expect(introduccion(null, '')).toContain('Escribe el correo con el que te registraste');
    expect(introduccion('<script>', '')).toBe(introduccion(null, ''));
  });
});

describe('al llegar', () => {
  test('desde el enlace del correo: rellena, borra el fragmento y espera un clic', () => {
    const { historial, confirmar } = montar({
      hash: '#codigo=k7qx-2m9p&correo=ana%2Bnexus%40nexus.test',
    });

    expect(campoCorreo().value).toBe('ana+nexus@nexus.test');
    expect(campoCodigo().value).toBe('K7QX2M9P');
    expect(historial.replaceState).toHaveBeenCalledWith(null, '', '/verificar');
    // No se confirma solo: un antivirus que abra el enlace no activa la cuenta.
    expect(confirmar).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(document.querySelector('[data-accion="confirmar"]'));
    expect(zona('introduccion').textContent).toContain('«Confirmar»');
  });

  test('desde el registro: el correo sale de la pestaña y el foco va al código', () => {
    sessionStorage.setItem(CLAVES_DEL_CORREO.porVerificar, 'ana@nexus.test');
    montar({ search: '?motivo=registro' });

    expect(campoCorreo().value).toBe('ana@nexus.test');
    expect(zona('introduccion').textContent).toContain('ana@nexus.test');
    expect(document.activeElement).toBe(campoCodigo());
  });

  test('sin nada conocido, el foco va al correo', () => {
    montar();
    expect(campoCorreo().value).toBe('');
    expect(document.activeElement).toBe(campoCorreo());
  });

  test('tras pedir otro código desde el login, lo dice con el mensaje neutro', () => {
    montar({ search: '?motivo=reenviado' });
    expect(zona('aviso').hidden).toBe(false);
    expect(zona('aviso').textContent).toContain(MENSAJE_DE_REENVIO);
  });
});

describe('confirmar', () => {
  test('manda el código limpio y lleva al login con el correo en la pestaña, nunca en la URL', async () => {
    sessionStorage.setItem(CLAVES_DEL_CORREO.porVerificar, 'ana@nexus.test');
    sessionStorage.setItem(CLAVES_DEL_CORREO.ultimoEnvio, String(INICIO));
    const { confirmar, navegar } = montar({ search: '?motivo=registro' });
    campoCodigo().value = ' k7qx 2m9p ';

    enviar();
    await tick();

    expect(confirmar).toHaveBeenCalledWith({ email: 'ana@nexus.test', codigo: 'K7QX2M9P' });
    expect(navegar).toHaveBeenCalledTimes(1);
    const destino = new URL(navegar.mock.calls[0][0]);
    expect(destino.pathname).toBe('/frontend/app-web/src/cuentas/login.html');
    expect(destino.searchParams.get('motivo')).toBe('verificada');
    expect(destino.href).not.toContain('ana');
    expect(sessionStorage.getItem(CLAVE_CORREO_REGISTRADO)).toBe('ana@nexus.test');
    // Lo de esta verificación ya sobra.
    expect(sessionStorage.getItem(CLAVES_DEL_CORREO.porVerificar)).toBeNull();
    expect(sessionStorage.getItem(CLAVES_DEL_CORREO.ultimoEnvio)).toBeNull();
    expect(zona('estado').textContent).toContain('Correo verificado');
  });

  test('sin código, o con un correo mal escrito, no se pregunta al servidor', async () => {
    const { confirmar } = montar();
    campoCorreo().value = 'ana@nexus.test';
    enviar();
    await tick();
    expect(confirmar).not.toHaveBeenCalled();
    expect(campoCodigo().getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(campoCodigo());

    campoCorreo().value = 'no-es-un-correo';
    campoCodigo().value = 'K7QX2M9P';
    enviar();
    await tick();
    expect(confirmar).not.toHaveBeenCalled();
    expect(campoCorreo().getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(campoCorreo());
  });

  test('codigo-invalido: lo explica, marca el código y deja el foco en él', async () => {
    const confirmar = jest.fn(() =>
      Promise.resolve({
        ok: false,
        estado: 400,
        tipo: 'codigo-invalido',
        detalle: 'Texto del servidor',
      }),
    );
    const { navegar } = montar({ confirmar });
    campoCorreo().value = 'ana@nexus.test';
    campoCodigo().value = 'K7QX2M9P';

    enviar();
    await tick();

    expect(navegar).not.toHaveBeenCalled();
    expect(zona('aviso').textContent).toContain('El código no es válido');
    // Por su type, no por el texto que mandó el servidor.
    expect(zona('aviso').textContent).not.toContain('Texto del servidor');
    expect(zona('aviso').querySelector('[role="alert"]')).not.toBeNull();
    expect(campoCodigo().getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(campoCodigo());
    expect(document.querySelector('[data-accion="confirmar"]').disabled).toBe(false);
  });

  test('demasiados-intentos: pide un código nuevo y lleva el foco a pedirlo', async () => {
    const confirmar = jest.fn(() =>
      Promise.resolve({ ok: false, estado: 429, tipo: 'demasiados-intentos', detalle: null }),
    );
    montar({ confirmar });
    campoCorreo().value = 'ana@nexus.test';
    campoCodigo().value = 'K7QX2M9P';

    enviar();
    await tick();

    expect(zona('aviso').textContent).toContain('Ese código ya no sirve');
    expect(document.activeElement).toBe(botonReenviar());
  });

  test('el límite del borde (429 sin type) no se confunde con el del código', async () => {
    const confirmar = jest.fn(() =>
      Promise.resolve({ ok: false, estado: 429, tipo: null, detalle: null }),
    );
    montar({ confirmar });
    campoCorreo().value = 'ana@nexus.test';
    campoCodigo().value = 'K7QX2M9P';

    enviar();
    await tick();

    expect(zona('aviso').textContent).toContain('Demasiadas solicitudes seguidas');
    expect(document.activeElement).toBe(zona('aviso'));
  });

  test('sin conexión lo dice y deja reintentar', async () => {
    const confirmar = jest.fn(() => Promise.reject(new TypeError('red')));
    montar({ confirmar });
    campoCorreo().value = 'ana@nexus.test';
    campoCodigo().value = 'K7QX2M9P';

    enviar();
    await tick();

    expect(zona('aviso').textContent).toContain('No pudimos comprobar el código');
    expect(zona('aviso').querySelector('.aviso--error')).not.toBeNull();
    expect(document.querySelector('[data-accion="confirmar"]').disabled).toBe(false);
  });

  test('un 5xx no enseña su código ni su texto interno', async () => {
    const confirmar = jest.fn(() =>
      Promise.resolve({ ok: false, estado: 500, tipo: null, detalle: 'NullPointerException' }),
    );
    montar({ confirmar });
    campoCorreo().value = 'ana@nexus.test';
    campoCodigo().value = 'K7QX2M9P';

    enviar();
    await tick();

    expect(zona('aviso').textContent).not.toMatch(/500|NullPointer/);
  });
});

describe('reenviar el código', () => {
  test('recién registrada: hay que esperar el minuto, y el botón lo cuenta', () => {
    sessionStorage.setItem(CLAVES_DEL_CORREO.porVerificar, 'ana@nexus.test');
    sessionStorage.setItem(CLAVES_DEL_CORREO.ultimoEnvio, String(INICIO));
    const { reloj } = montar({ search: '?motivo=registro' });

    expect(botonReenviar().disabled).toBe(true);
    expect(botonReenviar().textContent).toBe(textoDeEspera(60));

    reloj.avanzar(30_000);
    expect(botonReenviar().textContent).toBe(textoDeEspera(30));

    reloj.avanzar(30_000);
    expect(botonReenviar().disabled).toBe(false);
    expect(botonReenviar().textContent).toBe(TEXTO_REENVIAR);
  });

  test('pide otro con el correo escrito, dice el mensaje neutro y vuelve a esperar', async () => {
    const { reenviar } = montar();
    campoCorreo().value = 'ana@nexus.test';
    campoCodigo().value = 'VIEJO123';

    botonReenviar().click();
    await tick();

    expect(reenviar).toHaveBeenCalledWith('ana@nexus.test');
    expect(zona('aviso').textContent).toContain(MENSAJE_DE_REENVIO);
    expect(zona('aviso').querySelector('[role="status"]')).not.toBeNull();
    expect(sessionStorage.getItem(CLAVES_DEL_CORREO.porVerificar)).toBe('ana@nexus.test');
    expect(sessionStorage.getItem(CLAVES_DEL_CORREO.ultimoEnvio)).toBe(String(INICIO));
    expect(botonReenviar().disabled).toBe(true);
    expect(botonReenviar().textContent).toBe(textoDeEspera(60));
    // El código viejo deja de servir: se vacía y el foco espera el nuevo.
    expect(campoCodigo().value).toBe('');
    expect(document.activeElement).toBe(campoCodigo());
  });

  test('sin correo no pide nada', async () => {
    const { reenviar } = montar();
    botonReenviar().click();
    await tick();
    expect(reenviar).not.toHaveBeenCalled();
    expect(campoCorreo().getAttribute('aria-invalid')).toBe('true');
  });

  test('si el borde lo frena (429), lo dice sin inventar que salió', async () => {
    const reenviar = jest.fn(() =>
      Promise.resolve({ ok: false, estado: 429, tipo: null, detalle: null }),
    );
    montar({ reenviar });
    campoCorreo().value = 'ana@nexus.test';

    botonReenviar().click();
    await tick();

    expect(zona('aviso').textContent).toContain('Demasiadas solicitudes seguidas');
    expect(zona('aviso').textContent).not.toContain(MENSAJE_DE_REENVIO);
    expect(botonReenviar().disabled).toBe(false);
  });
});

describe('pegar el enlace entero en el campo del código', () => {
  test('se queda el código y, si faltaba, el correo', () => {
    montar();
    campoCodigo().value = 'http://nexus.test/verificar#codigo=k7qx2m9p&correo=ana%40nexus.test';
    campoCodigo().dispatchEvent(new Event('change'));

    expect(campoCodigo().value).toBe('K7QX2M9P');
    expect(campoCorreo().value).toBe('ana@nexus.test');
  });
});

describe('detener', () => {
  test('no deja temporizadores vivos', () => {
    sessionStorage.setItem(CLAVES_DEL_CORREO.ultimoEnvio, String(INICIO));
    const { vista, reloj } = montar();
    vista.detener();
    reloj.avanzar(120_000);
    // Detenido, el botón se queda como estaba: nadie lo sigue pintando.
    expect(botonReenviar().textContent).toBe(textoDeEspera(60));
  });
});
