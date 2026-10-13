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
import {
  mostrarPresentacion,
  presentacionDeHeroes,
  quienAbrio,
  segundosDeCuentaAtras,
  GRITO_DE_COMBATE,
  PAUSA_FINAL_MS,
} from './presentacion.js';

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

  /*
   * Auditoría de DEV del 30-sep: contra la máquina, si abre la IA juega en el
   * mismo instante en que empieza la partida, y quien pulsa «Iniciar combate»
   * recibe la partida ya en el turno 2. La presentación decía «Abres tú».
   */
  test('pasado el turno 1, dice quién abrió (el primero del orden) y a quién le toca', () => {
    const capa = presentacionDeHeroes({
      // El orden de `participantes` es el de los turnos (salas-partidas 1.7.0).
      participantes: [
        participante('ia-1', 'Mago Fuego', { esIA: true }),
        participante('u-1', 'Guerrero Tanque'),
      ],
      turnoActual: { idJugador: 'u-1', numeroTurno: 2 },
      yo: 'u-1',
    });

    expect(capa.querySelector('.presentacion__turno').textContent).toBe(
      'Abrió Mago Fuego; ahora te toca a ti',
    );
    expect(capa.querySelector('[data-jugador="ia-1"]').className).toContain('--abre');
    expect(capa.querySelector('[data-jugador="u-1"]').className).not.toContain('--abre');
  });

  test('quienAbrio: en el turno 1 es el del turno; después, el primero del orden', () => {
    expect(quienAbrio(DOS, { idJugador: 'u-2', numeroTurno: 1 })).toBe('u-2');
    expect(quienAbrio(DOS, { idJugador: 'u-2', numeroTurno: 3 })).toBe('u-1');
    expect(quienAbrio(DOS, { idJugador: 'u-2' })).toBe('u-2');
    expect(quienAbrio(DOS, null)).toBeNull();
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
    expect(quienes).toEqual(['Tu héroe', 'apodo-u-2', 'IA']);
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

/** Revisión del modo jugador del 6-oct, punto 17: la cuenta atrás del comienzo. */
describe('cuenta atrás del comienzo', () => {
  function relojDePrueba() {
    const pendientes = [];
    let ahora = 0;
    return {
      programar: (fn, ms) => pendientes.push({ fn, cuando: ahora + ms }),
      pasa(ms) {
        ahora += ms;
        for (;;) {
          pendientes.sort((a, b) => a.cuando - b.cuando);
          if (!pendientes.length || pendientes[0].cuando > ahora) {
            break;
          }
          pendientes.shift().fn();
        }
      },
    };
  }

  test('5, 4, 3, 2, 1, ¡COMBATE! y se cierra sola, sin botón', () => {
    const raiz = document.createElement('div');
    document.body.append(raiz);
    const reloj = relojDePrueba();
    const alCerrar = jest.fn();

    mostrarPresentacion(raiz, {
      participantes: DOS,
      cuentaAtras: 5,
      programar: reloj.programar,
      alCerrar,
    });

    const cifra = () => raiz.querySelector('[data-zona="cuenta-atras"]');
    expect(raiz.querySelector('[data-accion="entrar-al-combate"]')).toBeNull();
    expect(cifra().textContent).toBe('5');
    expect(cifra().getAttribute('aria-hidden')).toBe('true');
    const vistos = [cifra().textContent];
    for (let i = 0; i < 5; i += 1) {
      reloj.pasa(1000);
      vistos.push(cifra().textContent);
    }
    expect(vistos).toEqual(['5', '4', '3', '2', '1', GRITO_DE_COMBATE]);
    expect(alCerrar).not.toHaveBeenCalled();

    reloj.pasa(PAUSA_FINAL_MS);
    expect(raiz.querySelector('.presentacion')).toBeNull();
    expect(alCerrar).toHaveBeenCalledTimes(1);
  });

  test('al lector de pantalla se le dice una vez cuándo empieza, no cinco números', () => {
    const raiz = document.createElement('div');
    document.body.append(raiz);
    mostrarPresentacion(raiz, { participantes: DOS, cuentaAtras: 5, programar: () => {} });

    const avisos = [...raiz.querySelectorAll('[role="status"]')].map((n) => n.textContent);
    expect(avisos).toContain('El combate empieza en 5 segundos.');
  });

  test('los segundos que quedan salen de la hora de inicio del servidor, entre 0 y 5', () => {
    const inicio = '2026-10-06T20:00:00.000Z';
    const t = Date.parse(inicio);
    expect(segundosDeCuentaAtras(inicio, t)).toBe(5);
    expect(segundosDeCuentaAtras(inicio, t + 1200)).toBe(4);
    expect(segundosDeCuentaAtras(inicio, t + 4900)).toBe(1);
    expect(segundosDeCuentaAtras(inicio, t + 6000)).toBe(0);
    // Un reloj adelantado al servidor no alarga la cuenta.
    expect(segundosDeCuentaAtras(inicio, t - 30_000)).toBe(5);
    // Sin hora de inicio, la cuenta entera.
    expect(segundosDeCuentaAtras(null, t)).toBe(5);
  });
});
