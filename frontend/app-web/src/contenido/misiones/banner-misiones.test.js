/**
 * UXC-5 — el banner de misiones: carrusel accesible y la regla de RF-INV-003.
 */

import { jest } from '@jest/globals';

import { bannerDeMisiones, montarBannerDeMisiones, selloDeMision } from './banner-misiones.js';
import { FUENTE_SIN_SERVICIO } from './fuente-misiones.js';

const MISIONES = [
  {
    id: 'templo',
    nombre: 'El Templo Olvidado',
    categoria: 'HISTORIA',
    dificultad: 'NORMAL',
    duracionHoras: 12,
    descripcionBreve: 'Un templo custodiado por un guardián milenario.',
    nueva: true,
  },
  { id: 'dos', nombre: 'Segunda', categoria: 'DESAFIO', dificultad: 'DIFICIL', duracionHoras: 6 },
  {
    id: 'tres',
    nombre: 'Tercera',
    categoria: 'EXPLORACION',
    dificultad: 'FACIL',
    duracionHoras: 48,
  },
];

const hrefDe = (m) => `misiones.html?mision=${m.id}`;

function reloj() {
  const programados = new Map();
  let siguiente = 1;
  return {
    setInterval: jest.fn((fn) => {
      programados.set(siguiente, fn);
      siguiente += 1;
      return siguiente - 1;
    }),
    clearInterval: jest.fn((id) => programados.delete(id)),
    avanzar() {
      for (const fn of [...programados.values()]) {
        fn();
      }
    },
    activos: () => programados.size,
  };
}

const visibles = (raiz) =>
  [...raiz.querySelectorAll('.banner-misiones__diapositiva')].filter((d) => !d.hidden);

beforeEach(() => {
  document.body.innerHTML = '<div id="zona"></div>';
});

describe('carrusel', () => {
  test('una diapositiva a la vez, con su posición y su enlace', () => {
    const { elemento } = bannerDeMisiones(MISIONES, { hrefDe, temporizador: reloj() });
    document.body.append(elemento);

    expect(elemento.getAttribute('aria-roledescription')).toBe('carrusel');
    const [actual] = visibles(elemento);
    expect(visibles(elemento)).toHaveLength(1);
    expect(actual.getAttribute('aria-label')).toBe('1 de 3: El Templo Olvidado');
    expect(actual.querySelector('.banner-misiones__sello').textContent).toBe('Nueva');
    expect(actual.querySelector('.banner-misiones__datos').textContent).toBe(
      'Historia · Normal · 12 horas',
    );
    expect(actual.querySelector('a').getAttribute('href')).toBe('misiones.html?mision=templo');
  });

  test('rota sola, callada; pausada, se anuncia; el punto actual se marca sin depender del color', () => {
    const temporizador = reloj();
    const { elemento, actual } = bannerDeMisiones(MISIONES, {
      hrefDe,
      temporizador,
      enPausa: false,
    });
    const pista = elemento.querySelector('.banner-misiones__pista');

    expect(pista.getAttribute('aria-live')).toBe('off');
    temporizador.avanzar();
    expect(actual()).toBe(1);
    expect(
      elemento.querySelectorAll('.banner-misiones__punto')[1].getAttribute('aria-current'),
    ).toBe('true');

    elemento.querySelector('[data-accion="pausar-banner"]').click();
    expect(temporizador.activos()).toBe(0);
    expect(pista.getAttribute('aria-live')).toBe('polite');
    expect(elemento.querySelector('[data-accion="pausar-banner"]').getAttribute('aria-label')).toBe(
      'Reanudar la rotación de misiones',
    );
  });

  test('anterior, siguiente y los puntos llevan a su diapositiva y dan la vuelta', () => {
    const { elemento, actual } = bannerDeMisiones(MISIONES, { hrefDe, temporizador: reloj() });

    elemento.querySelector('[data-accion="mision-anterior"]').click();
    expect(actual()).toBe(2);
    elemento.querySelector('[data-accion="mision-siguiente"]').click();
    expect(actual()).toBe(0);
    elemento.querySelectorAll('[data-accion="ir-a-mision"]')[1].click();
    expect(visibles(elemento)[0].dataset.mision).toBe('dos');
  });

  test('con el foco dentro se detiene, y al salir sigue', () => {
    const temporizador = reloj();
    const { elemento } = bannerDeMisiones(MISIONES, { hrefDe, temporizador, enPausa: false });
    document.body.append(elemento);

    elemento.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    expect(temporizador.activos()).toBe(0);
    expect(elemento.dataset.rotacion).toBe('en-pausa');

    elemento.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }));
    expect(temporizador.activos()).toBe(1);
  });

  test('si la vista lo quita de la página, deja de rotar solo', () => {
    const temporizador = reloj();
    const { elemento } = bannerDeMisiones(MISIONES, { hrefDe, temporizador, enPausa: false });
    document.body.append(elemento);
    temporizador.avanzar();
    elemento.remove();

    temporizador.avanzar();

    expect(temporizador.activos()).toBe(0);
  });

  test('con movimiento reducido empieza en pausa', () => {
    const temporizador = reloj();
    bannerDeMisiones(MISIONES, { hrefDe, temporizador, enPausa: true });
    expect(temporizador.setInterval).not.toHaveBeenCalled();
  });

  test('una sola misión no rota ni pinta controles', () => {
    const temporizador = reloj();
    const { elemento } = bannerDeMisiones([MISIONES[0]], { hrefDe, temporizador, enPausa: false });
    expect(elemento.querySelector('.banner-misiones__controles')).toBeNull();
    expect(temporizador.setInterval).not.toHaveBeenCalled();
  });

  test('el sello dice «tiempo limitado» con lo que falta, y nada si ya venció', () => {
    const ahora = new Date('2026-09-25T10:00:00Z');
    expect(selloDeMision({ disponibleHasta: '2026-09-28T10:00:00Z' }, ahora)).toBe(
      'Tiempo limitado · termina en 3 d',
    );
    expect(selloDeMision({ disponibleHasta: '2026-09-20T10:00:00Z' }, ahora)).toBe(
      'Tiempo limitado',
    );
    expect(selloDeMision({}, ahora)).toBeNull();
  });
});

describe('montarBannerDeMisiones (RF-INV-003)', () => {
  // El carrusel de verdad rota con `setInterval`: con relojes falsos la
  // prueba no deja un temporizador vivo al terminar.
  beforeEach(() => {
    jest.useFakeTimers();
  });
  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  test('sin módulo de misiones se oculta y no pide nada', async () => {
    const zona = document.getElementById('zona');
    // La fuente de producción está congelada: se espía una copia con la misma
    // forma, que sigue diciendo que no hay servicio.
    const fuente = { ...FUENTE_SIN_SERVICIO, destacadas: jest.fn() };

    const resultado = await montarBannerDeMisiones(zona, { fuente, hrefDe });

    expect(resultado).toBe('oculto');
    expect(zona.hidden).toBe(true);
    expect(zona.children).toHaveLength(0);
    expect(fuente.destacadas).not.toHaveBeenCalled();
  });

  test('si el módulo falla, también se oculta sin tocar el resto', async () => {
    const zona = document.getElementById('zona');
    const fuente = { disponible: true, destacadas: jest.fn().mockRejectedValue(new Error('503')) };

    expect(await montarBannerDeMisiones(zona, { fuente, hrefDe })).toBe('oculto');
    expect(zona.hidden).toBe(true);
  });

  test('sin destacadas, el contenido alternativo lleva a preparar la estrategia', async () => {
    const zona = document.getElementById('zona');
    const fuente = { disponible: true, destacadas: jest.fn().mockResolvedValue([]) };

    const resultado = await montarBannerDeMisiones(zona, {
      fuente,
      hrefDe,
      hrefEstrategia: '../misiones/misiones.html#estrategia',
    });

    expect(resultado).toBe('promocion');
    expect(zona.hidden).toBe(false);
    expect(zona.querySelector('[data-accion="preparar-estrategia"]').getAttribute('href')).toBe(
      '../misiones/misiones.html#estrategia',
    );
  });

  test('con destacadas, el carrusel y el acceso directo al tablón', async () => {
    const zona = document.getElementById('zona');
    const fuente = { disponible: true, destacadas: jest.fn().mockResolvedValue(MISIONES) };

    await montarBannerDeMisiones(zona, { fuente, hrefDe, hrefTablon: '../misiones/misiones.html' });

    expect(zona.querySelectorAll('.banner-misiones__diapositiva')).toHaveLength(3);
    expect(zona.querySelector('.banner-misiones__tablon').getAttribute('href')).toBe(
      '../misiones/misiones.html',
    );
  });
});
