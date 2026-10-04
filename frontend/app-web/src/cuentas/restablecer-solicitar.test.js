/**
 * «Recuperar contraseña», paso 1 — HU-COR-003; B1 (identidad 2.0.0).
 *
 * Lo que se prueba: la ruta y el cuerpo del contrato; que lo que se dice sea
 * SIEMPRE lo mismo, exista o no la cuenta (fijo en la interfaz, no el texto
 * del servidor); que el correo se recuerde para el paso siguiente sin ir en
 * la URL; y que un fallo que no depende de la cuenta (el límite de
 * peticiones, la red) se diga como tal.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

import { CLAVES_DEL_CORREO } from '../comun/codigo-de-correo.js';
import { FalloDeRecuperacion } from '../comun/recuperacion.js';
import {
  MENSAJE_DE_SOLICITUD,
  montarSolicitud,
  rechazoDeLaSolicitud,
  solicitarRestablecimiento,
} from './restablecer-solicitar.js';

const MARCADO = readFileSync(new URL('./restablecer-solicitar.html', import.meta.url), 'utf8');

function respuesta(cuerpo, estado = 200) {
  return Promise.resolve({
    ok: estado >= 200 && estado < 300,
    status: estado,
    text: async () => {
      if (cuerpo === null) {
        return '';
      }
      return typeof cuerpo === 'string' ? cuerpo : JSON.stringify(cuerpo);
    },
  });
}

const tick = () => new Promise((resolver) => setTimeout(resolver, 0));

beforeEach(() => {
  document.documentElement.innerHTML = MARCADO.replace(/<script[\s\S]*?<\/script>/g, '');
  sessionStorage.clear();
});

describe('solicitarRestablecimiento (cliente)', () => {
  test('POST a la ruta del contrato solo con el correo', async () => {
    const fetchImpl = jest.fn(() => respuesta('Cualquier texto'));

    await solicitarRestablecimiento('cristian@nexus.test', { fetchImpl });

    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/auth/restablecer/solicitar');
    expect(opciones.method).toBe('POST');
    expect(opciones.headers['Content-Type']).toBe('application/json');
    // Responde texto plano si va bien y problem details si no: acepta los dos.
    expect(opciones.headers.Accept).toContain('application/problem+json');
    expect(opciones.headers.Accept).toContain('text/plain');
    expect(JSON.parse(opciones.body)).toEqual({ email: 'cristian@nexus.test' });
  });

  test('dice SIEMPRE el mensaje neutro, no el texto del servidor', async () => {
    const mensaje = await solicitarRestablecimiento('cristian@nexus.test', {
      fetchImpl: () => respuesta('Si el correo está registrado, recibirás un mensaje.'),
    });
    expect(mensaje).toBe('Si existe una cuenta asociada, recibirás instrucciones.');
    expect(mensaje).toBe(MENSAJE_DE_SOLICITUD);
  });

  test('exista o no la cuenta, la respuesta es idéntica', async () => {
    const existe = await solicitarRestablecimiento('existe@nexus.test', {
      fetchImpl: () => respuesta('texto A'),
    });
    const noExiste = await solicitarRestablecimiento('noexiste@nexus.test', {
      fetchImpl: () => respuesta('texto B'),
    });
    expect(existe).toBe(noExiste);
  });

  test('un rechazo del servidor llega con su estado', async () => {
    const error = await solicitarRestablecimiento('x@nexus.test', {
      fetchImpl: () => respuesta('', 429),
    }).catch((e) => e);
    expect(error).toBeInstanceOf(FalloDeRecuperacion);
    expect(error.estado).toBe(429);
  });
});

describe('rechazoDeLaSolicitud', () => {
  test('el límite de peticiones, un correo mal escrito y un fallo del servicio se dicen distinto', () => {
    expect(rechazoDeLaSolicitud(new FalloDeRecuperacion({ estado: 429 })).titulo).toBe(
      'Demasiadas solicitudes seguidas',
    );
    expect(rechazoDeLaSolicitud(new FalloDeRecuperacion({ estado: 400 })).titulo).toBe(
      'Revisa el correo',
    );
    const caido = rechazoDeLaSolicitud(new FalloDeRecuperacion({ estado: 503 }));
    expect(caido.tono).toBe('error');
    expect(rechazoDeLaSolicitud(new TypeError('red')).tono).toBe('error');
  });
});

describe('la vista', () => {
  test('G1 — el formulario va por POST y su botón nace apagado hasta montar', () => {
    const boton = document.getElementById('botonEnviar');
    expect(document.getElementById('formSolicitud').getAttribute('method')).toBe('post');
    expect(boton.disabled).toBe(true);

    montarSolicitud(document, { solicitar: jest.fn(), almacen: sessionStorage });

    expect(boton.disabled).toBe(false);
  });

  test('tras pedirlo dice el mensaje neutro, recuerda el correo y ofrece escribir el código', async () => {
    const solicitar = jest.fn(() => Promise.resolve(MENSAJE_DE_SOLICITUD));
    montarSolicitud(document, { solicitar, almacen: sessionStorage });
    document.getElementById('email').value = ' ana@nexus.test ';

    document
      .getElementById('formSolicitud')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await tick();

    expect(solicitar).toHaveBeenCalledWith('ana@nexus.test');
    const aviso = document.querySelector('[data-zona="aviso"]');
    expect(aviso.hidden).toBe(false);
    expect(aviso.textContent).toContain(MENSAJE_DE_SOLICITUD);
    expect(aviso.querySelector('[role="status"]')).not.toBeNull();
    expect(sessionStorage.getItem(CLAVES_DEL_CORREO.recuperacion)).toBe('ana@nexus.test');
    expect(document.querySelector('[data-zona="siguiente"]').hidden).toBe(false);
    expect(document.activeElement).toBe(document.querySelector('[data-zona="escribir-codigo"]'));
    expect(document.getElementById('botonEnviar').disabled).toBe(false);
  });

  test('un correo mal escrito no llega al servidor', async () => {
    const solicitar = jest.fn();
    montarSolicitud(document, { solicitar, almacen: sessionStorage });
    document.getElementById('email').value = 'no-es-un-correo';

    document
      .getElementById('formSolicitud')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await tick();

    expect(solicitar).not.toHaveBeenCalled();
    expect(document.getElementById('email').getAttribute('aria-invalid')).toBe('true');
  });

  test('si el borde la frena, lo dice sin prometer instrucciones', async () => {
    const solicitar = jest.fn(() => Promise.reject(new FalloDeRecuperacion({ estado: 429 })));
    montarSolicitud(document, { solicitar, almacen: sessionStorage });
    document.getElementById('email').value = 'ana@nexus.test';

    document
      .getElementById('formSolicitud')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await tick();

    const aviso = document.querySelector('[data-zona="aviso"]');
    expect(aviso.textContent).toContain('Demasiadas solicitudes seguidas');
    expect(aviso.textContent).not.toContain(MENSAJE_DE_SOLICITUD);
    expect(document.querySelector('[data-zona="siguiente"]').hidden).toBe(true);
    expect(sessionStorage.getItem(CLAVES_DEL_CORREO.recuperacion)).toBeNull();
  });

  test('vuelve a traer el correo con el que se pidió antes', () => {
    sessionStorage.setItem(CLAVES_DEL_CORREO.recuperacion, 'ana@nexus.test');
    montarSolicitud(document, { solicitar: jest.fn(), almacen: sessionStorage });
    expect(document.getElementById('email').value).toBe('ana@nexus.test');
  });
});
