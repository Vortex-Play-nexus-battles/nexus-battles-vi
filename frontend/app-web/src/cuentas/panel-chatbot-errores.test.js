/**
 * Lo que dice el panel del asistente cuando algo falla — ms-chatbot.yaml 1.7.0.
 */

import { ErrorDelChatbot } from '../comun/cliente-chatbot.js';
import { fraseDeErrorDelPanel, textoDeErrorDelPanel } from './panel-chatbot-errores.js';

describe('textoDeErrorDelPanel', () => {
  test('el título es siempre lo que no se pudo hacer, también si el servicio no está', () => {
    for (const estado of [0, 502, 503, 504, 400, 403, 404, 409, 500]) {
      expect(
        textoDeErrorDelPanel(new ErrorDelChatbot(null, estado), 'No se pudo guardar el caso')
          .titulo,
      ).toBe('No se pudo guardar el caso');
    }
  });

  test('el detalle es el porqué, decidido por el estado', () => {
    const detalle = (error) => textoDeErrorDelPanel(error, 'No se pudo X').detalle;

    expect(detalle(new ErrorDelChatbot(null, 0))).toContain('no responde');
    expect(detalle(new ErrorDelChatbot(null, 503))).toContain('no responde');
    // Un 404 sin cuerpo del servicio en una ruta fija es que el asistente no está.
    expect(detalle(new ErrorDelChatbot(null, 404, { rutaFija: true }))).toContain('no responde');
    expect(detalle(new ErrorDelChatbot({ title: 'Not Found' }, 404))).toContain('Ya no existe');
    expect(detalle(new ErrorDelChatbot(null, 401))).toContain('permiso');
    expect(detalle(new ErrorDelChatbot(null, 403))).toContain('permiso');
    expect(detalle(new ErrorDelChatbot({ title: 'Bad Request' }, 400))).toContain('rechazó');
    expect(detalle(new ErrorDelChatbot({ title: 'Conflict' }, 409))).toContain('Otro cambio');
    expect(detalle(new ErrorDelChatbot({ title: 'x' }, 500))).toContain('respondió con un error');
  });

  test('sin «qué falló» usa uno genérico, nunca el de otra pestaña', () => {
    const { titulo } = textoDeErrorDelPanel(new ErrorDelChatbot(null, 500));

    expect(titulo).toBe('No se pudo completar la acción');
    expect(titulo).not.toContain('analíticas');
  });

  test('aguanta un error que no es del cliente del asistente', () => {
    expect(textoDeErrorDelPanel(null, 'No se pudo X')).toEqual({
      titulo: 'No se pudo X',
      detalle:
        'El servicio del asistente respondió con un error. Inténtalo de nuevo en unos minutos.',
    });
  });
});

test('fraseDeErrorDelPanel une título y detalle en una frase', () => {
  expect(fraseDeErrorDelPanel(new ErrorDelChatbot(null, 409), 'No se pudo eliminar el caso')).toBe(
    'No se pudo eliminar el caso. Otro cambio llegó antes: revisa lo que se muestra ahora y vuelve a intentarlo.',
  );
});
