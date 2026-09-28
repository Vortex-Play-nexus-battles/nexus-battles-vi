/**
 * B3 — calificar un producto sin comentarlo, una sola vez (7.1).
 *
 * Contra dobles de `miCalificacion` y `calificar` con la forma exacta de
 * `comentarios.yaml` 1.5.0: 404 de `rating/mia` es «todavía no calificaste»
 * (el cliente lo devuelve como `null`), 201 trae el resumen recalculado, 409
 * `ya-calificado` no es un error del jugador, 403 es una sanción.
 */

import { jest } from '@jest/globals';

import { ErrorDeApi, MOTIVO, TIPO } from './cliente-comentarios.js';
import {
  controlDeCalificacion,
  esCalificacionDuplicada,
  ESTADO_CALIFICACION,
  mensajeDeCalificacion,
} from './calificar-producto.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const RESUMEN = {
  productoId: 'p-1',
  promedio: 4.5,
  total: 2,
  distribucion: { 1: 0, 2: 0, 3: 0, 4: 1, 5: 1 },
};

async function montar(opciones = {}) {
  const miCalificacionImpl = opciones.miCalificacionImpl ?? jest.fn().mockResolvedValue(null);
  const calificarImpl = opciones.calificarImpl ?? jest.fn();
  const alCalificar = opciones.alCalificar ?? jest.fn();
  const control = controlDeCalificacion({
    productoId: 'p-1',
    miCalificacionImpl,
    calificarImpl,
    alCalificar,
  });
  document.body.replaceChildren(control.elemento);
  const carga = control.cargar();
  await carga;
  return { control, elemento: control.elemento, miCalificacionImpl, calificarImpl, alCalificar };
}

function elegir(elemento, estrellas) {
  const radio = elemento.querySelectorAll('input[type="radio"]')[estrellas - 1];
  radio.checked = true;
  radio.dispatchEvent(new Event('change', { bubbles: true }));
}

function enviar(elemento) {
  elemento.querySelector('form').requestSubmit();
}

beforeEach(() => {
  document.body.replaceChildren();
});

describe('qué se enseña según rating/mia', () => {
  test('mientras comprueba, lo dice con un estado que se anuncia', () => {
    const control = controlDeCalificacion({
      productoId: 'p-1',
      miCalificacionImpl: () => new Promise(() => {}),
    });
    control.cargar();

    expect(control.elemento.dataset.estado).toBe(ESTADO_CALIFICACION.COMPROBANDO);
    const estado = control.elemento.querySelector('[role="status"]');
    expect(estado.textContent).toMatch(/Comprobando si ya calificaste/);
  });

  test('sin calificación (404 → null): estrellas interactivas, accesibles, y «Calificar»', async () => {
    const { elemento, miCalificacionImpl } = await montar();

    expect(miCalificacionImpl).toHaveBeenCalledWith('p-1');
    expect(elemento.dataset.estado).toBe(ESTADO_CALIFICACION.PENDIENTE);
    const grupo = elemento.querySelector('fieldset');
    expect(grupo.querySelector('legend').textContent).toBe('Califica este producto');
    expect(grupo.querySelectorAll('input[type="radio"]')).toHaveLength(5);
    expect(grupo.textContent).toMatch(/Solo se califica una vez/);
    expect(elemento.querySelector('[data-accion="calificar"]').textContent).toBe('Calificar');
  });

  test('con calificación: «Tu calificación: N de 5», sin nada con que cambiarla', async () => {
    const { elemento } = await montar({
      miCalificacionImpl: jest
        .fn()
        .mockResolvedValue({ productoId: 'p-1', estrellas: 4, fecha: '2026-09-25T12:00:00Z' }),
    });

    expect(elemento.dataset.estado).toBe(ESTADO_CALIFICACION.CALIFICADO);
    expect(elemento.textContent).toContain('Tu calificación: 4 de 5');
    expect(elemento.textContent).toMatch(/no se cambia/);
    expect(elemento.querySelector('input')).toBeNull();
    expect(elemento.querySelector('button')).toBeNull();
    // Las estrellas repiten el texto: fuera del lector de pantalla.
    expect(
      elemento.querySelector('.calificar-producto__estrellas').getAttribute('aria-hidden'),
    ).toBe('true');
  });

  test('si no se puede comprobar: se dice y se reintenta, sin ofrecer estrellas a ciegas', async () => {
    const miCalificacionImpl = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDeApi({ status: 503 }, 503))
      .mockResolvedValue(null);
    const { elemento } = await montar({ miCalificacionImpl });

    expect(elemento.dataset.estado).toBe(ESTADO_CALIFICACION.ERROR);
    expect(elemento.querySelector('input')).toBeNull();
    expect(elemento.textContent).toMatch(/No pudimos comprobar si ya calificaste/);
    expect(elemento.textContent).not.toMatch(/503/);

    elemento.querySelector('[data-accion="reintentar-calificacion"]').click();
    await esperar();
    expect(elemento.dataset.estado).toBe(ESTADO_CALIFICACION.PENDIENTE);
  });

  test('sesión caducada al comprobar: se ofrece entrar, no reintentar', async () => {
    const { elemento } = await montar({
      miCalificacionImpl: jest.fn().mockRejectedValue(new ErrorDeApi({ status: 401 }, 401)),
    });

    expect(elemento.textContent).toMatch(/Tu sesión ya no es válida/);
    expect(elemento.querySelector('[data-accion="iniciar-sesion"]')).not.toBeNull();
    expect(elemento.querySelector('[data-accion="reintentar-calificacion"]')).toBeNull();
  });
});

describe('calificar', () => {
  test('sin elegir no se envía: se dice junto al grupo y el foco va a las estrellas', async () => {
    const { elemento, calificarImpl } = await montar();

    enviar(elemento);
    await esperar();

    expect(calificarImpl).not.toHaveBeenCalled();
    const error = elemento.querySelector('.campo__error');
    expect(error.hidden).toBe(false);
    expect(elemento.querySelector('fieldset').getAttribute('aria-describedby')).toContain(error.id);
    expect(document.activeElement).toBe(elemento.querySelector('input[type="radio"]'));

    // Elegir quita el aviso.
    elegir(elemento, 2);
    expect(error.hidden).toBe(true);
  });

  test('201: manda las estrellas, enseña la propia, devuelve el resumen y lleva el foco', async () => {
    const calificarImpl = jest.fn().mockResolvedValue({
      productoId: 'p-1',
      estrellas: 4,
      fecha: '2026-09-25T12:00:00Z',
      resumen: RESUMEN,
    });
    const { elemento, alCalificar } = await montar({ calificarImpl });

    elegir(elemento, 4);
    enviar(elemento);
    await esperar();

    expect(calificarImpl).toHaveBeenCalledWith('p-1', 4);
    expect(elemento.dataset.estado).toBe(ESTADO_CALIFICACION.CALIFICADO);
    expect(elemento.textContent).toContain('Tu calificación: 4 de 5');
    expect(elemento.querySelector('.aviso--exito').textContent).toMatch(/Calificación registrada/);
    expect(document.activeElement).toBe(elemento.querySelector('.calificar-producto__propia'));
    expect(alCalificar).toHaveBeenCalledWith({ estrellas: 4, resumen: RESUMEN, yaExistia: false });
  });

  test('mientras envía, el botón queda ocupado y las estrellas no se pueden cambiar', async () => {
    let resolver;
    const calificarImpl = jest.fn(
      () =>
        new Promise((r) => {
          resolver = r;
        }),
    );
    const { elemento } = await montar({ calificarImpl });

    elegir(elemento, 5);
    enviar(elemento);

    const boton = elemento.querySelector('[data-accion="calificar"]');
    expect(boton.disabled).toBe(true);
    expect(boton.getAttribute('aria-busy')).toBe('true');
    expect(elemento.querySelector('fieldset').disabled).toBe(true);

    resolver({ productoId: 'p-1', estrellas: 5, fecha: 'x', resumen: RESUMEN });
    await esperar();
    expect(elemento.dataset.estado).toBe(ESTADO_CALIFICACION.CALIFICADO);
  });

  test('409 ya-calificado: no es un error; se lee la que cuenta y se enseña', async () => {
    const miCalificacionImpl = jest
      .fn()
      .mockResolvedValueOnce(null)
      .mockResolvedValue({ productoId: 'p-1', estrellas: 2, fecha: 'x' });
    const calificarImpl = jest
      .fn()
      .mockRejectedValue(
        new ErrorDeApi(
          { type: TIPO.YA_CALIFICADO, status: 409, motivo: MOTIVO.CALIFICACION_DUPLICADA },
          409,
        ),
      );
    const { elemento, alCalificar } = await montar({ miCalificacionImpl, calificarImpl });

    elegir(elemento, 5);
    enviar(elemento);
    await esperar();
    await esperar();

    expect(elemento.dataset.estado).toBe(ESTADO_CALIFICACION.CALIFICADO);
    expect(elemento.textContent).toContain('Tu calificación: 2 de 5');
    const aviso = elemento.querySelector('.aviso--info');
    expect(aviso.textContent).toMatch(/Ya habías calificado este producto/);
    expect(aviso.getAttribute('role')).toBe('status');
    expect(alCalificar).toHaveBeenCalledWith({ estrellas: 2, resumen: null, yaExistia: true });
  });

  test('409 sin poder leer la propia: se dice igual, sin inventar una cifra', async () => {
    const miCalificacionImpl = jest
      .fn()
      .mockResolvedValueOnce(null)
      .mockRejectedValue(new ErrorDeApi({ status: 503 }, 503));
    const calificarImpl = jest
      .fn()
      .mockRejectedValue(new ErrorDeApi({ type: TIPO.YA_CALIFICADO, status: 409 }, 409));
    const { elemento } = await montar({ miCalificacionImpl, calificarImpl });

    elegir(elemento, 3);
    enviar(elemento);
    await esperar();
    await esperar();

    expect(elemento.dataset.estado).toBe(ESTADO_CALIFICACION.CALIFICADO);
    expect(elemento.textContent).toMatch(/Ya habías calificado/);
    expect(elemento.textContent).not.toMatch(/Tu calificación: \d/);
  });

  test('403 por sanción: advertencia con el camino a sus sanciones; el control sigue', async () => {
    const calificarImpl = jest
      .fn()
      .mockRejectedValue(new ErrorDeApi({ motivo: MOTIVO.AUTOR_SILENCIADO, status: 403 }, 403));
    const { elemento } = await montar({ calificarImpl });

    elegir(elemento, 3);
    enviar(elemento);
    await esperar();

    expect(elemento.dataset.estado).toBe(ESTADO_CALIFICACION.PENDIENTE);
    const aviso = elemento.querySelector('.aviso--advertencia');
    expect(aviso.textContent).toMatch(/Ahora mismo no puedes calificar/);
    expect(aviso.querySelector('a').textContent).toBe('Ver mis sanciones');
    expect(elemento.querySelector('[data-accion="calificar"]').disabled).toBe(false);
  });

  test('un fallo del servicio conserva la elección y deja reintentar', async () => {
    const calificarImpl = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDeApi({ status: 503 }, 503))
      .mockResolvedValue({ productoId: 'p-1', estrellas: 3, fecha: 'x', resumen: RESUMEN });
    const { elemento } = await montar({ calificarImpl });

    elegir(elemento, 3);
    enviar(elemento);
    await esperar();

    expect(elemento.querySelector('.aviso--error').textContent).toMatch(
      /Tu elección sigue marcada/,
    );
    expect(elemento.querySelectorAll('input[type="radio"]')[2].checked).toBe(true);
    expect(elemento.querySelector('fieldset').disabled).toBe(false);

    elemento.querySelector('[data-accion="reintentar"]').click();
    await esperar();
    expect(calificarImpl).toHaveBeenCalledTimes(2);
    expect(elemento.dataset.estado).toBe(ESTADO_CALIFICACION.CALIFICADO);
  });
});

describe('frases de los rechazos', () => {
  test('se deciden por estado, tipo y motivo; nunca enseñan un código', () => {
    expect(mensajeDeCalificacion(new ErrorDeApi({ status: 401 }, 401)).sesion).toBe(true);
    expect(mensajeDeCalificacion(new ErrorDeApi({ status: 403 }, 403)).titulo).toMatch(
      /no puede calificar/,
    );
    expect(
      mensajeDeCalificacion(new ErrorDeApi({ type: TIPO.PRODUCTO_INEXISTENTE, status: 404 }, 404))
        .titulo,
    ).toMatch(/ya no está en el catálogo/);
    expect(mensajeDeCalificacion(new ErrorDeApi({ status: 503 }, 503)).reintentar).toBe(true);
    expect(mensajeDeCalificacion(new TypeError('sin red')).reintentar).toBe(true);
    for (const estado of [400, 401, 403, 404, 500, 503]) {
      const { titulo, detalle } = mensajeDeCalificacion(new ErrorDeApi({ status: estado }, estado));
      expect(`${titulo} ${detalle ?? ''}`).not.toMatch(/\b\d{3}\b|ms-|comentarios\.yaml/);
    }
  });

  test('el 409 se reconoce por estado, tipo o motivo', () => {
    expect(esCalificacionDuplicada(new ErrorDeApi({ status: 409 }, 409))).toBe(true);
    expect(esCalificacionDuplicada(new ErrorDeApi({ type: TIPO.YA_CALIFICADO }, 409))).toBe(true);
    expect(esCalificacionDuplicada(new ErrorDeApi({ status: 503 }, 503))).toBe(false);
    expect(esCalificacionDuplicada(new Error('otra cosa'))).toBe(false);
  });
});
