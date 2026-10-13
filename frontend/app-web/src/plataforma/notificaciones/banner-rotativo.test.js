/**
 * Banner informativo rotativo de la home — RF-NOT-002 (HU-NOT-002, #533).
 *
 * Los criterios de #533 son los que se prueban: CA-01 rota los vigentes y
 * retira solos los que expiran; CA-02 uno solo se ve sin rotar; CA-03 sin
 * vigentes (o sin servicio) el banner no se pinta; CA-04 cada mensaje dice su
 * fecha de publicación y su vigencia. Y lo que pide WCAG 2.2.2 a todo lo que
 * se mueve solo: se puede pausar, se detiene con el foco y con el ratón, y
 * con movimiento reducido no arranca.
 */

import { jest } from '@jest/globals';

import { fechaHora } from '../../comun/ui/formato.js';
import {
  INTERVALO_DE_ROTACION_MS,
  bannerRotativo,
  montarBannerRotativo,
} from './banner-rotativo.js';

const AHORA = new Date('2026-10-05T15:00:00Z');
const MINUTO = 60_000;

/** Un banner como lo devuelve `GET /api/v1/banners/vigentes` (productos.yaml 1.6.0). */
function banner(id, contenido, { venceEnMs = 24 * 60 * MINUTO } = {}) {
  return {
    id,
    contenido,
    publicarDesde: new Date(AHORA.getTime() - 60 * MINUTO).toISOString(),
    vigenteHasta: new Date(AHORA.getTime() + venceEnMs).toISOString(),
    retirado: false,
    creadoEn: new Date(AHORA.getTime() - 120 * MINUTO).toISOString(),
    modificadoEn: new Date(AHORA.getTime() - 120 * MINUTO).toISOString(),
  };
}

const TRES = [
  banner('b-1', 'Temporada de héroes: nuevos prototipos en la tienda'),
  banner('b-2', 'Mantenimiento programado el domingo'),
  banner('b-3', 'Torneo de otoño: inscripciones abiertas'),
];

/** El mensaje que se ve ahora (los demás están `hidden`). */
function visible(raiz) {
  const visibles = [...raiz.querySelectorAll('[data-banner]')].filter((m) => !m.hidden);
  expect(visibles).toHaveLength(1);
  return visibles[0];
}

const accion = (raiz, nombre) => raiz.querySelector(`[data-accion="${nombre}"]`);

let zona;

beforeEach(() => {
  jest.useFakeTimers({ now: AHORA });
  document.body.innerHTML = '<div data-zona="bloque-banners" hidden></div>';
  zona = document.querySelector('[data-zona="bloque-banners"]');
  delete window.matchMedia;
});

afterEach(() => {
  jest.clearAllTimers();
  jest.useRealTimers();
  jest.restoreAllMocks();
});

describe('CA-01 · rota los vigentes y retira solos los que expiran', () => {
  test('pasa al siguiente cada intervalo y vuelve al primero', async () => {
    await montarBannerRotativo(zona, { consultar: async () => TRES });

    expect(zona.hidden).toBe(false);
    expect(visible(zona).dataset.banner).toBe('b-1');
    jest.advanceTimersByTime(INTERVALO_DE_ROTACION_MS);
    expect(visible(zona).dataset.banner).toBe('b-2');
    jest.advanceTimersByTime(INTERVALO_DE_ROTACION_MS);
    expect(visible(zona).dataset.banner).toBe('b-3');
    jest.advanceTimersByTime(INTERVALO_DE_ROTACION_MS);
    expect(visible(zona).dataset.banner).toBe('b-1');
  });

  test('el intervalo es una constante de interfaz documentada, no una regla de negocio', () => {
    // Mismo ritmo que el banner de misiones (UXC-5): ocho segundos dan para
    // leer un aviso corto, y la persona puede pausar o pasar a mano.
    expect(INTERVALO_DE_ROTACION_MS).toBe(8000);
  });

  test('el que vence con la página abierta desaparece sin recargar', async () => {
    const pronto = banner('b-pronto', 'Promoción relámpago', { venceEnMs: 5 * MINUTO });
    await montarBannerRotativo(zona, { consultar: async () => [pronto, ...TRES.slice(0, 2)] });
    expect(zona.querySelectorAll('[data-banner]')).toHaveLength(3);

    jest.advanceTimersByTime(5 * MINUTO);

    expect(zona.querySelector('[data-banner="b-pronto"]')).toBeNull();
    expect(zona.querySelectorAll('[data-banner]')).toHaveLength(2);
    expect(zona.querySelectorAll('[data-accion="ir-a-anuncio"]')).toHaveLength(2);
    expect(zona.textContent).not.toContain('Promoción relámpago');
  });

  test('si vencen todos, la zona se oculta y no queda ningún reloj vivo', async () => {
    await montarBannerRotativo(zona, {
      consultar: async () => [
        banner('a', 'Uno', { venceEnMs: MINUTO }),
        banner('b', 'Dos', { venceEnMs: 2 * MINUTO }),
      ],
    });

    jest.advanceTimersByTime(2 * MINUTO);

    expect(zona.hidden).toBe(true);
    expect(zona.childElementCount).toBe(0);
    expect(jest.getTimerCount()).toBe(0);
  });

  test('cuando queda uno solo deja de rotar y quita los controles', async () => {
    await montarBannerRotativo(zona, {
      consultar: async () => [banner('a', 'Se va', { venceEnMs: MINUTO }), TRES[0]],
    });
    expect(accion(zona, 'pausar-banner')).not.toBeNull();

    jest.advanceTimersByTime(MINUTO);

    expect(visible(zona).dataset.banner).toBe('b-1');
    expect(accion(zona, 'pausar-banner')).toBeNull();
    expect(zona.querySelector('[data-componente="banner-rotativo"]').dataset.rotacion).toBe('fija');
  });

  test('lo que ya llegó vencido (reloj del navegador adelantado) no se pinta', async () => {
    await montarBannerRotativo(zona, {
      consultar: async () => [banner('viejo', 'Ya pasó', { venceEnMs: -MINUTO }), TRES[0]],
    });

    expect(zona.querySelector('[data-banner="viejo"]')).toBeNull();
    expect(visible(zona).dataset.banner).toBe('b-1');
  });
});

describe('CA-02 · un único vigente se muestra sin rotar', () => {
  test('sin controles, sin rotación y sin semántica de carrusel', async () => {
    await montarBannerRotativo(zona, { consultar: async () => [TRES[0]] });
    const region = zona.querySelector('[data-componente="banner-rotativo"]');

    expect(region.dataset.rotacion).toBe('fija');
    expect(region.hasAttribute('aria-roledescription')).toBe(false);
    expect(zona.querySelector('button')).toBeNull();
    expect(region.getAttribute('aria-label')).toBe('Anuncio del Nexo');
    jest.advanceTimersByTime(10 * INTERVALO_DE_ROTACION_MS);
    expect(visible(zona).dataset.banner).toBe('b-1');
  });
});

describe('CA-03 · sin vigentes, o sin servicio, el banner no se pinta', () => {
  test('una lista vacía deja la zona oculta y vacía', async () => {
    const resultado = await montarBannerRotativo(zona, { consultar: async () => [] });

    expect(resultado).toBeNull();
    expect(zona.hidden).toBe(true);
    expect(zona.childElementCount).toBe(0);
  });

  test('si la consulta falla, la zona sigue oculta y el fallo va a la consola, no a la home', async () => {
    const aviso = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const fallo = Object.assign(new Error('No se pudo gestionar el banner.'), { status: 502 });

    await expect(
      montarBannerRotativo(zona, { consultar: () => Promise.reject(fallo) }),
    ).resolves.toBeNull();

    expect(zona.hidden).toBe(true);
    expect(zona.childElementCount).toBe(0);
    expect(aviso).toHaveBeenCalledWith(expect.stringContaining('banner'), fallo);
  });

  test('una respuesta con otra forma tampoco rompe nada', async () => {
    await montarBannerRotativo(zona, { consultar: async () => ({ contenido: [] }) });
    expect(zona.hidden).toBe(true);
  });

  test('descarta los mensajes sin contenido o con fechas que no se pueden leer', async () => {
    await montarBannerRotativo(zona, {
      consultar: async () => [
        { ...TRES[0], id: 'vacio', contenido: '   ' },
        { ...TRES[1], id: 'sin-fecha', vigenteHasta: 'pronto' },
        TRES[2],
      ],
    });

    expect([...zona.querySelectorAll('[data-banner]')].map((m) => m.dataset.banner)).toEqual([
      'b-3',
    ]);
  });

  test('sin zona en la página no pide nada', async () => {
    const consultar = jest.fn();
    await expect(montarBannerRotativo(null, { consultar })).resolves.toBeNull();
    expect(consultar).not.toHaveBeenCalled();
  });
});

describe('CA-04 · cada mensaje dice cuándo se publicó y hasta cuándo vale', () => {
  test('«Publicado el …» y «Vigente hasta el …» con el formateador común y su <time>', async () => {
    await montarBannerRotativo(zona, { consultar: async () => [TRES[0]] });
    const mensaje = visible(zona);

    expect(mensaje.textContent).toContain(TRES[0].contenido);
    expect(mensaje.textContent).toContain(`Publicado el ${fechaHora(TRES[0].publicarDesde)}`);
    expect(mensaje.textContent).toContain(`Vigente hasta el ${fechaHora(TRES[0].vigenteHasta)}`);
    const [publicado, vigente] = mensaje.querySelectorAll('time');
    expect(publicado.getAttribute('datetime')).toBe(TRES[0].publicarDesde);
    expect(vigente.getAttribute('datetime')).toBe(TRES[0].vigenteHasta);
  });

  test('el contenido del servidor se pinta como texto, nunca como marcado', async () => {
    await montarBannerRotativo(zona, {
      consultar: async () => [banner('x', '<img src=x onerror="alert(1)">Oferta')],
    });

    expect(zona.querySelector('img')).toBeNull();
    expect(visible(zona).textContent).toContain('<img src=x onerror="alert(1)">Oferta');
  });
});

describe('accesible: se puede parar, y solo habla cuando lo mueve la persona', () => {
  test('es un carrusel con nombre, y cada anuncio dice su posición', async () => {
    await montarBannerRotativo(zona, { consultar: async () => TRES });
    const region = zona.querySelector('[data-componente="banner-rotativo"]');

    expect(region.tagName).toBe('SECTION');
    expect(region.getAttribute('aria-roledescription')).toBe('carrusel');
    expect(region.getAttribute('aria-label')).toBe('Anuncios del Nexo');
    const mensaje = visible(zona);
    expect(mensaje.getAttribute('role')).toBe('group');
    expect(mensaje.getAttribute('aria-roledescription')).toBe('anuncio');
    expect(mensaje.getAttribute('aria-label')).toBe('1 de 3');
  });

  test('mientras rota solo calla; cuando la persona cambia de anuncio, se anuncia', async () => {
    await montarBannerRotativo(zona, { consultar: async () => TRES });
    const pista = zona.querySelector('[aria-live]');

    expect(pista.getAttribute('aria-live')).toBe('off');
    jest.advanceTimersByTime(INTERVALO_DE_ROTACION_MS);
    expect(pista.getAttribute('aria-live')).toBe('off');

    const siguiente = accion(zona, 'anuncio-siguiente');
    siguiente.focus();
    siguiente.click();

    expect(visible(zona).dataset.banner).toBe('b-3');
    expect(pista.getAttribute('aria-live')).toBe('polite');
  });

  test('pausar y reanudar con el botón', async () => {
    await montarBannerRotativo(zona, { consultar: async () => TRES });
    const pausa = accion(zona, 'pausar-banner');
    expect(pausa.textContent).toBe('Pausar');

    pausa.click();
    jest.advanceTimersByTime(5 * INTERVALO_DE_ROTACION_MS);
    expect(visible(zona).dataset.banner).toBe('b-1');
    expect(pausa.textContent).toBe('Reanudar');
    expect(pausa.getAttribute('aria-label')).toBe('Reanudar la rotación de anuncios');

    pausa.click();
    jest.advanceTimersByTime(INTERVALO_DE_ROTACION_MS);
    expect(visible(zona).dataset.banner).toBe('b-2');
  });

  test('con el foco dentro se detiene; al salir el foco, sigue', async () => {
    await montarBannerRotativo(zona, { consultar: async () => TRES });
    const region = zona.querySelector('[data-componente="banner-rotativo"]');

    region.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    jest.advanceTimersByTime(3 * INTERVALO_DE_ROTACION_MS);
    expect(visible(zona).dataset.banner).toBe('b-1');

    region.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }));
    jest.advanceTimersByTime(INTERVALO_DE_ROTACION_MS);
    expect(visible(zona).dataset.banner).toBe('b-2');
  });

  test('con el ratón encima se detiene; al irse, sigue', async () => {
    await montarBannerRotativo(zona, { consultar: async () => TRES });
    const region = zona.querySelector('[data-componente="banner-rotativo"]');

    region.dispatchEvent(new MouseEvent('mouseenter'));
    jest.advanceTimersByTime(3 * INTERVALO_DE_ROTACION_MS);
    expect(visible(zona).dataset.banner).toBe('b-1');

    region.dispatchEvent(new MouseEvent('mouseleave'));
    jest.advanceTimersByTime(INTERVALO_DE_ROTACION_MS);
    expect(visible(zona).dataset.banner).toBe('b-2');
  });

  test('con movimiento reducido no rota solo; la persona sigue pudiendo pasar', async () => {
    window.matchMedia = jest.fn((consulta) => ({
      matches: consulta === '(prefers-reduced-motion: reduce)',
    }));
    await montarBannerRotativo(zona, { consultar: async () => TRES });

    jest.advanceTimersByTime(5 * INTERVALO_DE_ROTACION_MS);
    expect(visible(zona).dataset.banner).toBe('b-1');
    expect(accion(zona, 'pausar-banner').textContent).toBe('Reanudar');

    accion(zona, 'anuncio-siguiente').click();
    expect(visible(zona).dataset.banner).toBe('b-2');
  });

  test('anterior, siguiente y los puntos llevan a su anuncio y marcan el actual', async () => {
    await montarBannerRotativo(zona, { consultar: async () => TRES });

    accion(zona, 'anuncio-anterior').click();
    expect(visible(zona).dataset.banner).toBe('b-3');
    accion(zona, 'anuncio-siguiente').click();
    expect(visible(zona).dataset.banner).toBe('b-1');

    const puntos = zona.querySelectorAll('[data-accion="ir-a-anuncio"]');
    puntos[1].click();
    expect(visible(zona).dataset.banner).toBe('b-2');
    expect(puntos[1].getAttribute('aria-current')).toBe('true');
    expect(puntos[0].hasAttribute('aria-current')).toBe(false);
    expect(puntos[1].getAttribute('aria-label')).toBe('Ver el anuncio 2 de 3');
  });
});

describe('ciclo de vida', () => {
  test('detener() apaga la rotación y la retirada programada', () => {
    const carrusel = bannerRotativo(TRES);
    expect(jest.getTimerCount()).toBe(2);

    carrusel.detener();

    expect(jest.getTimerCount()).toBe(0);
  });

  test('si la vista lo quita de la página, deja de rotar solo', async () => {
    const carrusel = await montarBannerRotativo(zona, { consultar: async () => TRES });
    jest.advanceTimersByTime(INTERVALO_DE_ROTACION_MS);
    expect(carrusel.actual()).toBe(1);

    zona.replaceChildren();
    jest.advanceTimersByTime(INTERVALO_DE_ROTACION_MS);

    expect(carrusel.actual()).toBe(1);
    expect(jest.getTimerCount()).toBe(0);
  });
});
