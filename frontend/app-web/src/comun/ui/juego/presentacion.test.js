/**
 * Presentación de los héroes — HU-JUE-017 CA-04 (UX-R2.10).
 *
 * El criterio pide «vista de alto impacto al iniciar (presentación de los
 * héroes)». Durante UX-R2.3 se dejó pendiente porque parecía hacer falta una
 * señal del servidor que no existía; al revisarlo resultó que **sí existe**:
 * `sala.partida.iniciada` trae `participantes`, `ordenDeTurnos` y
 * `turnoActual`.
 *
 * Por eso lo primero que se comprueba aquí es que **nada de esto se inventa**:
 * ni el momento, ni la duración, ni los datos.
 */

import { jest } from '@jest/globals';
import { mostrarPresentacion, presentacionDeHeroes } from './presentacion.js';

/** Un participante con la forma del esquema `Participante` del contrato. */
function participante(id, nombre, extra = {}) {
  return {
    jugador: { id, apodo: `apodo-${id}` },
    heroe: { nombre, vidaActual: 100, vidaMaxima: 100 },
    esIA: false,
    ...extra,
  };
}

const DOS = [
  participante('u-1', 'Sombra de Vael', { equipo: 1 }),
  participante('u-2', 'Guardián de Hierro', { equipo: 2 }),
];

afterEach(() => {
  document.body.innerHTML = '';
});

describe('presentacionDeHeroes', () => {
  test('presenta a los héroes que trae el aviso, y a ninguno más', () => {
    const capa = presentacionDeHeroes({ participantes: DOS });
    const nombres = [...capa.querySelectorAll('.presentacion__nombre')].map((n) => n.textContent);

    expect(nombres).toEqual(['Sombra de Vael', 'Guardián de Hierro']);
  });

  test('sin héroes no se presenta nada', () => {
    // La verificación de héroe no es obligatoria al entrar a la sala. Una
    // pantalla de impacto con huecos es peor que entrar directo al campo.
    expect(presentacionDeHeroes({ participantes: [{ jugador: { id: 'u-1' } }] })).toBeNull();
    expect(presentacionDeHeroes({ participantes: [] })).toBeNull();
  });

  test('separa los bandos y los enfrenta', () => {
    const capa = presentacionDeHeroes({ participantes: DOS });

    expect(capa.querySelectorAll('.presentacion__bando')).toHaveLength(2);
    expect(capa.querySelector('.presentacion__versus').textContent).toBe('VS');
  });

  test('sin equipos declarados no inventa un enfrentamiento', () => {
    const capa = presentacionDeHeroes({
      participantes: [participante('u-1', 'Uno'), participante('u-2', 'Dos')],
    });

    expect(capa.querySelectorAll('.presentacion__bando')).toHaveLength(1);
    expect(capa.querySelector('.presentacion__versus')).toBeNull();
  });

  test('dice quién abre, con el dato del aviso', () => {
    const capa = presentacionDeHeroes({
      participantes: DOS,
      turnoActual: { idJugador: 'u-2', numeroTurno: 1 },
      yo: 'u-1',
    });

    const turno = capa.querySelector('.presentacion__turno');
    expect(turno.textContent).toBe('Abre Guardián de Hierro');
    // Se anuncia: quien no ve la pantalla decide igual si le toca actuar.
    expect(turno.getAttribute('aria-live')).toBe('polite');
  });

  test('cuando abres tú, lo dice en segunda persona', () => {
    const capa = presentacionDeHeroes({
      participantes: DOS,
      turnoActual: { idJugador: 'u-1', numeroTurno: 1 },
      yo: 'u-1',
    });

    expect(capa.querySelector('.presentacion__turno').textContent).toBe('Abres tú');
    expect(capa.querySelector('[data-jugador="u-1"]').className).toContain('--abre');
  });

  test('distingue tu héroe, el de otro jugador y el de la IA', () => {
    const capa = presentacionDeHeroes({
      participantes: [
        participante('u-1', 'Mío', { equipo: 1 }),
        participante('u-2', 'Suyo', { equipo: 2 }),
        participante('ia-1', 'Autómata', { equipo: 2, esIA: true }),
      ],
      yo: 'u-1',
    });

    const quienes = [...capa.querySelectorAll('.presentacion__jugador')].map((n) => n.textContent);
    expect(quienes).toEqual(['Tu héroe', 'apodo-u-2', 'Controlado por la IA']);
  });

  test('no bloquea: es una capa, no un diálogo modal', () => {
    // El combate ya está montado detrás y sigue vivo.
    const capa = presentacionDeHeroes({ participantes: DOS });
    expect(capa.getAttribute('aria-modal')).toBe('false');
  });
});

describe('mostrarPresentacion', () => {
  function zona() {
    const el = document.createElement('div');
    el.hidden = true;
    document.body.appendChild(el);
    return el;
  }

  test('se cierra al pulsar, y no con un tiempo fijo', () => {
    // Si alguien tarda en leer, la presentación espera. No hay setTimeout
    // que decida cuánto dura.
    const raiz = zona();
    const alCerrar = jest.fn();
    mostrarPresentacion(raiz, { participantes: DOS, alCerrar });

    expect(raiz.hidden).toBe(false);
    raiz.querySelector('[data-accion="entrar-al-combate"]').click();

    expect(raiz.querySelector('.presentacion')).toBeNull();
    expect(raiz.hidden).toBe(true);
    expect(alCerrar).toHaveBeenCalledTimes(1);
  });

  test('Escape también cierra', () => {
    // Sin esto el teclado se queda dentro (WCAG 2.1.2).
    const raiz = zona();
    mostrarPresentacion(raiz, { participantes: DOS });

    raiz
      .querySelector('.presentacion')
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(raiz.querySelector('.presentacion')).toBeNull();
  });

  test('el cierre devuelto la quita, y es idempotente', () => {
    // Es lo que permite que el primer aviso del canal la retire.
    const raiz = zona();
    const alCerrar = jest.fn();
    const cerrar = mostrarPresentacion(raiz, { participantes: DOS, alCerrar });

    cerrar();
    cerrar();

    expect(raiz.querySelector('.presentacion')).toBeNull();
    expect(alCerrar).toHaveBeenCalledTimes(1);
  });

  test('el foco entra en la salida', () => {
    const raiz = zona();
    mostrarPresentacion(raiz, { participantes: DOS });

    expect(document.activeElement.dataset.accion).toBe('entrar-al-combate');
  });

  test('sin héroes no monta nada y el cierre no revienta', () => {
    const raiz = zona();
    const cerrar = mostrarPresentacion(raiz, { participantes: [] });

    expect(raiz.hidden).toBe(true);
    expect(() => cerrar()).not.toThrow();
  });
});
