/**
 * Acuse de un cambio de estado — UX-R2.10.
 *
 * Lo que se comprueba es la REGLA, no la animación: que no se pueda acusar
 * algo que no cambió, que la información no viva solo en el movimiento, y que
 * dos acuses seguidos no se apilen.
 */

import { jest } from '@jest/globals';
import { acusar, acusarCambioDeVida } from './acuse.js';

/** Temporizador de mentira: nada corre solo, todo se dispara a mano. */
function reloj() {
  const pendientes = [];
  return {
    pendientes,
    fijar: (fn, ms) => {
      pendientes.push({ fn, ms });
      return pendientes.length;
    },
    quitar: jest.fn(),
    correrTodo() {
      // Copia: una función puede programar otra.
      for (const { fn } of [...pendientes]) {
        fn();
      }
    },
  };
}

function barra() {
  const el = document.createElement('div');
  el.className = 'barra-vida';
  document.body.appendChild(el);
  return el;
}

afterEach(() => {
  document.body.innerHTML = '';
});

describe('acusar', () => {
  test('pone la clase del tipo y la quita cuando pasa el tiempo', () => {
    const el = barra();
    const r = reloj();

    acusar(el, { tipo: 'dano', temporizador: r });
    expect(el.classList.contains('acuse--dano')).toBe(true);

    r.correrTodo();
    expect(el.classList.contains('acuse--dano')).toBe(false);
  });

  test('un tipo que no comunica un estado conocido no se acepta', () => {
    // La API no deja «animar por animar»: un tipo nuevo obliga a decidir qué
    // cambio de estado comunica.
    expect(() => acusar(barra(), { tipo: 'brillito' })).toThrow(RangeError);
  });

  test('la cifra es texto anunciable, no solo movimiento', () => {
    // Es lo único que sobrevive a `prefers-reduced-motion`.
    const el = barra();
    const rastro = acusar(el, { tipo: 'dano', texto: '−12', temporizador: reloj() });

    expect(rastro.textContent).toBe('−12');
    expect(rastro.getAttribute('aria-live')).toBe('polite');
    expect(rastro.getAttribute('role')).toBe('status');
    expect(el.textContent).toContain('−12');
  });

  test('dos acuses seguidos no se apilan: el segundo reinicia el primero', () => {
    // En una sala de seis llegan varios avisos por segundo.
    const el = barra();
    const r = reloj();

    acusar(el, { tipo: 'dano', temporizador: r });
    acusar(el, { tipo: 'dano', temporizador: r });

    expect(r.quitar).toHaveBeenCalledTimes(1);
    expect(el.classList.contains('acuse--dano')).toBe(true);
  });

  test('sin texto no deja rastro, y eso es válido', () => {
    // Equipar no tiene cifra: el cambio se ve en la ranura misma.
    const el = barra();
    expect(acusar(el, { tipo: 'equipar', temporizador: reloj() })).toBeNull();
  });
});

describe('acusarCambioDeVida', () => {
  test('daño: cifra negativa y clase de daño', () => {
    const el = barra();
    const rastro = acusarCambioDeVida(el, 100, 88, { temporizador: reloj() });

    expect(rastro.textContent).toBe('−12');
    expect(rastro.className).toContain('acuse__cifra--dano');
    expect(el.classList.contains('acuse--dano')).toBe(true);
  });

  test('curación: cifra positiva y clase de curación', () => {
    const el = barra();
    const rastro = acusarCambioDeVida(el, 40, 65, { temporizador: reloj() });

    expect(rastro.textContent).toBe('+25');
    expect(rastro.className).toContain('acuse__cifra--curacion');
    expect(el.classList.contains('acuse--curacion')).toBe(true);
  });

  test('si la vida no cambió no se acusa nada', () => {
    // El canal reenvía mensajes. Animar por un mensaje que no trae noticia es
    // exactamente lo que la regla de R2.10 prohíbe.
    const el = barra();

    expect(acusarCambioDeVida(el, 50, 50, { temporizador: reloj() })).toBeNull();
    expect(el.className).toBe('barra-vida');
  });

  test('una vida no numérica no pinta «NaN»', () => {
    const el = barra();
    expect(acusarCambioDeVida(el, Number.NaN, 50, { temporizador: reloj() })).toBeNull();
    expect(el.textContent).not.toContain('NaN');
  });
});
