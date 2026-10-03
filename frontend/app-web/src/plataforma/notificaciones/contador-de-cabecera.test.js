/**
 * Auditoría de DEV del 30-sep: «el número de la campana solo se actualiza al
 * abrir la bandeja». El contador de la cabecera usa la misma bandeja.
 */
import { jest } from '@jest/globals';

import { montarContadorDeCabecera, textoDelNumero, TOPE_VISIBLE } from './contador-de-cabecera.js';

const UID = '11111111-1111-1111-1111-111111111111';

/** Un JWT sin firmar con el `uid`: la sesion la lee de ahi (comun/identidad.js). */
function tokenCon(uid) {
  const cuerpo = btoa(
    JSON.stringify({ uid, sub: 'ana', exp: Math.floor(Date.now() / 1000) + 3600 }),
  )
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `c.${cuerpo}.f`;
}

function cabecera() {
  const raiz = document.createElement('header');
  raiz.innerHTML =
    '<a class="cabecera__campana" aria-label="Notificaciones"><span data-zona="contador" hidden>0</span></a>';
  return raiz;
}

/** Una bandeja de mentira: guarda los callbacks para moverlos desde la prueba. */
function fabricaFalsa() {
  const fabrica = jest.fn((callbacks) => {
    fabrica.callbacks = callbacks;
    fabrica.bandeja = { iniciar: jest.fn(), detener: jest.fn() };
    return fabrica.bandeja;
  });
  return fabrica;
}

beforeEach(() => {
  sessionStorage.clear();
  sessionStorage.setItem('nexus.token', tokenCon(UID));
});

describe('montarContadorDeCabecera', () => {
  test('arranca la bandeja y pinta la cuenta que llegue, por HTTP o por el canal', () => {
    const raiz = cabecera();
    const fabrica = fabricaFalsa();

    montarContadorDeCabecera(raiz, { fabrica, ventana: null });
    expect(fabrica.bandeja.iniciar).toHaveBeenCalledTimes(1);

    const contador = raiz.querySelector('[data-zona="contador"]');
    fabrica.callbacks.alCambiar({ noLeidas: 3 });
    expect(contador.hidden).toBe(false);
    expect(contador.textContent).toBe('3');
    expect(raiz.querySelector('.cabecera__campana').getAttribute('aria-label')).toBe(
      'Notificaciones: 3 notificaciones sin leer',
    );

    // Leídas en otra pestaña: llega `ContadorActualizado` con 0 y se apaga.
    fabrica.callbacks.alCambiar({ noLeidas: 0 });
    expect(contador.hidden).toBe(true);
    expect(raiz.querySelector('.cabecera__campana').getAttribute('aria-label')).toBe(
      'Notificaciones: sin notificaciones sin leer',
    );
  });

  test('más de lo que cabe se dice con un «+»', () => {
    expect(textoDelNumero(TOPE_VISIBLE)).toBe('99');
    expect(textoDelNumero(TOPE_VISIBLE + 1)).toBe('99+');
  });

  test('sin sesión o sin contador no abre nada', () => {
    const fabrica = fabricaFalsa();
    const sinContador = document.createElement('header');
    expect(montarContadorDeCabecera(sinContador, { fabrica, ventana: null })).toBeNull();

    sessionStorage.clear();
    expect(montarContadorDeCabecera(cabecera(), { fabrica, ventana: null })).toBeNull();
    expect(fabrica).not.toHaveBeenCalled();
  });

  test('al salir de la página se cierra el canal; si vuelve de la caché, se reabre', () => {
    const ventana = new EventTarget();
    const fabrica = fabricaFalsa();
    montarContadorDeCabecera(cabecera(), { fabrica, ventana });

    ventana.dispatchEvent(new Event('pagehide'));
    expect(fabrica.bandeja.detener).toHaveBeenCalledTimes(1);

    const vuelta = new Event('pageshow');
    vuelta.persisted = true;
    ventana.dispatchEvent(vuelta);
    expect(fabrica.bandeja.iniciar).toHaveBeenCalledTimes(2);
  });
});
