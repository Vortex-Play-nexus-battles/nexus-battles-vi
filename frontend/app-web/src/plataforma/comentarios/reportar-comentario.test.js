/**
 * HU-COM-006 / RF-COM-006 — el diálogo de reporte de un comentario.
 *
 * Se prueba el diálogo solo, contra un doble de `reportarComentario`; el
 * cableado del botón en el hilo lo cubre `hilo-comentarios.test.js`. Las frases
 * del rechazo se deciden por `motivo` y `estado` (`shared/ui-kit/MAPEO-ERRORES.md`)
 * y, mientras el tope de reportes y sus umbrales sean decisión abierta con el
 * PO (D-27), ninguna dice una cifra.
 */

import { jest } from '@jest/globals';

import { ErrorDeApi } from './cliente-comentarios.js';
import {
  abrirReporteDeComentario,
  LARGO_DESCRIPCION,
  mensajeDelRechazo,
  RESULTADO_REPORTE,
} from './reportar-comentario.js';

const COMENTARIO = { id: 'c-1', apodoAutor: 'Jugador 1' };

const esperar = () => new Promise((r) => setTimeout(r, 0));

const rechazo = (estado, motivo = null) =>
  new ErrorDeApi({ status: estado, ...(motivo ? { motivo } : {}) }, estado);

function abrir(reportarImpl = jest.fn()) {
  const resultado = abrirReporteDeComentario({
    productoId: 'p-1',
    comentario: COMENTARIO,
    reportarImpl,
  });
  const dialogo = document.querySelector('.dialogo--reporte');
  return { resultado, dialogo, reportarImpl };
}

async function enviarConMotivo(dialogo, categoria = 'SPAM') {
  dialogo.querySelector(`input[value="${categoria}"]`).checked = true;
  dialogo.querySelector('form').requestSubmit();
  await esperar();
}

beforeEach(() => {
  document.body.replaceChildren();
});

describe('el formulario', () => {
  test('ofrece las seis categorías y la descripción es opcional', () => {
    const { dialogo } = abrir();

    expect(dialogo.querySelectorAll('input[type="radio"]')).toHaveLength(6);
    expect(dialogo.querySelector('textarea').getAttribute('maxlength')).toBe(
      String(LARGO_DESCRIPCION),
    );
    expect(dialogo.querySelector('textarea').required).toBe(false);
  });

  test('sin elegir motivo no se envía: se dice y el foco va a la primera opción', async () => {
    const { dialogo, reportarImpl } = abrir();

    dialogo.querySelector('form').requestSubmit();
    await esperar();

    expect(reportarImpl).not.toHaveBeenCalled();
    expect(dialogo.querySelector('.campo__error').hidden).toBe(false);
    expect(document.activeElement).toBe(dialogo.querySelector('input[type="radio"]'));
  });

  test('una descripción en blanco no viaja', async () => {
    const reportarImpl = jest.fn().mockResolvedValue({ id: 'r-1' });
    const { dialogo } = abrir(reportarImpl);
    dialogo.querySelector('textarea').value = '   ';

    await enviarConMotivo(dialogo, 'ACOSO');

    expect(reportarImpl).toHaveBeenCalledWith('p-1', 'c-1', { categoria: 'ACOSO' });
  });

  test('el contador de la descripción dice cuántos caracteres quedan', () => {
    const { dialogo } = abrir();
    const area = dialogo.querySelector('textarea');

    area.value = 'abc';
    area.dispatchEvent(new Event('input'));

    expect(dialogo.querySelector('.campo__pista').textContent).toMatch(
      new RegExp(`quedan ${LARGO_DESCRIPCION - 3} caracteres`),
    );
  });
});

describe('cómo termina', () => {
  test('201: resuelve REPORTADO con el reporte y cierra el diálogo', async () => {
    const reporte = { id: 'r-1', estadoDelComentario: 'EN_REVISION' };
    const { dialogo, resultado } = abrir(jest.fn().mockResolvedValue(reporte));

    await enviarConMotivo(dialogo);

    await expect(resultado).resolves.toEqual({ resultado: RESULTADO_REPORTE.REPORTADO, reporte });
    expect(document.querySelector('.dialogo--reporte')).toBeNull();
  });

  test('Cancelar resuelve CANCELADO sin llamar al servicio', async () => {
    const { dialogo, resultado, reportarImpl } = abrir();

    dialogo.querySelector('[data-accion="cancelar"]').click();

    await expect(resultado).resolves.toEqual({ resultado: RESULTADO_REPORTE.CANCELADO });
    expect(reportarImpl).not.toHaveBeenCalled();
  });

  test('Escape resuelve CANCELADO', async () => {
    const { resultado } = abrir();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    await expect(resultado).resolves.toEqual({ resultado: RESULTADO_REPORTE.CANCELADO });
  });

  test('404: dice que ya no está publicado y cerrar resuelve RETIRADO', async () => {
    const { dialogo, resultado } = abrir(jest.fn().mockRejectedValue(rechazo(404)));

    await enviarConMotivo(dialogo);

    expect(dialogo.textContent).toMatch(/ya no está publicado/);
    dialogo.querySelector('[data-accion="cancelar"]').click();
    await expect(resultado).resolves.toEqual({ resultado: RESULTADO_REPORTE.RETIRADO });
  });
});

describe('400 reporte inválido', () => {
  const rechazado400 = () => rechazo(400, 'REPORTE_INVALIDO');

  test('se decide por estado y por motivo, con o sin motivo', () => {
    for (const error of [rechazado400(), rechazo(400)]) {
      const mensaje = mensajeDelRechazo(error);
      expect(mensaje).not.toBeNull();
      expect(mensaje.tono).toBe('advertencia');
      expect(mensaje.titulo).toMatch(/Revisa el reporte/);
      // Se puede corregir: no cierra el diálogo ni lo da por resuelto.
      expect(mensaje.resultado).toBeUndefined();
    }
  });

  test('no repite el texto del servidor, que puede traer una cifra', () => {
    const error = new ErrorDeApi(
      {
        status: 400,
        motivo: 'REPORTE_INVALIDO',
        detail: 'La descripcion admite hasta 500 caracteres',
      },
      400,
    );

    const mensaje = mensajeDelRechazo(error);

    expect(`${mensaje.titulo} ${mensaje.detalle}`).not.toMatch(/\d/);
  });

  test('el formulario se queda con el motivo elegido y sin ofrecer reintentar a ciegas', async () => {
    const reportarImpl = jest.fn().mockRejectedValue(rechazado400());
    const { dialogo } = abrir(reportarImpl);

    await enviarConMotivo(dialogo, 'INFORMACION_FALSA');

    expect(dialogo.querySelector('[data-zona="aviso-reporte"]').textContent).toMatch(
      /Revisa el reporte/,
    );
    expect(dialogo.querySelector('[data-accion="reintentar-reporte"]')).toBeNull();
    expect(dialogo.querySelector('input[value="INFORMACION_FALSA"]').checked).toBe(true);
    expect(dialogo.querySelector('[data-accion="enviar-reporte"]').hidden).toBe(false);
    expect(document.querySelector('.dialogo--reporte')).not.toBeNull();
  });
});

describe('otros rechazos', () => {
  test('401: aviso con «Iniciar sesión», no un error genérico', async () => {
    const { dialogo } = abrir(jest.fn().mockRejectedValue(rechazo(401)));

    await enviarConMotivo(dialogo);

    expect(dialogo.querySelector('[data-accion="iniciar-sesion"]')).not.toBeNull();
    expect(dialogo.querySelector('[data-accion="reintentar-reporte"]')).toBeNull();
  });

  test('5xx: se conserva el motivo y se ofrece reintentar', async () => {
    const reportarImpl = jest.fn().mockRejectedValueOnce(rechazo(503));
    const { dialogo } = abrir(reportarImpl);

    await enviarConMotivo(dialogo, 'SPAM');

    expect(dialogo.querySelector('[data-accion="reintentar-reporte"]')).not.toBeNull();
    expect(dialogo.querySelector('input[value="SPAM"]').checked).toBe(true);
  });

  test('un error que no es del servicio no tiene frase propia', () => {
    expect(mensajeDelRechazo(new Error('boom'))).toBeNull();
    expect(mensajeDelRechazo(rechazo(503))).toBeNull();
  });
});

describe('ningún mensaje escribe una cifra ni una ventana de tiempo (D-27 abierta)', () => {
  const casos = [
    ['409', rechazo(409, 'REPORTE_DUPLICADO')],
    ['429', rechazo(429, 'LIMITE_DE_REPORTES')],
    ['404', rechazo(404)],
    ['400', rechazo(400, 'REPORTE_INVALIDO')],
  ];

  test.each(casos)('el mensaje del %s', (_nombre, error) => {
    const { titulo, detalle } = mensajeDelRechazo(error);

    expect(`${titulo} ${detalle}`).not.toMatch(/\d/);
  });

  test('el 429 no promete cuándo vuelve: el tope y su plazo no están decididos', () => {
    const { titulo, detalle } = mensajeDelRechazo(rechazo(429, 'LIMITE_DE_REPORTES'));

    expect(`${titulo} ${detalle}`).not.toMatch(/\b(hoy|mañana|día|horas?)\b/i);
    expect(titulo).toMatch(/límite de reportes/);
  });
});
