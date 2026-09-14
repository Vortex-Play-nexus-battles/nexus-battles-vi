/**
 * HU-NOT-006 — Campana: contador, estado del canal, lista, emergentes.
 * La bandeja se inyecta falsa; aqui solo se prueba lo que se pinta.
 */

import { jest } from '@jest/globals';

import { montarCampana, pintarEmergente, textoDeContador, fechaLegible } from './campana.js';
import { ESTADO_CANAL } from './bandeja.js';

const HTML = `
  <header>
    <button type="button" data-zona="boton" aria-controls="panel">
      <span class="cabecera__contador" data-zona="contador" hidden>0</span>
    </button>
  </header>
  <main>
    <div data-zona="emergentes" aria-live="polite"></div>
    <section id="panel" data-zona="panel" hidden>
      <span class="conexion conexion--sin-conexion" data-zona="conexion"></span>
      <p data-zona="lista-vacia">Todavia no tienes notificaciones.</p>
      <ul data-zona="lista"></ul>
    </section>
  </main>
`;

const aviso = (id, extra = {}) => ({
  id,
  tipo: 'mision',
  titulo: `Titulo ${id}`,
  cuerpo: `Cuerpo ${id}`,
  creadaEn: '2026-09-09T10:00:00Z',
  leida: false,
  ...extra,
});

function preparar() {
  document.body.innerHTML = HTML;
  let callbacks;
  const bandeja = {
    iniciar: jest.fn(),
    marcarLeida: jest.fn(async () => 0),
    detener: jest.fn(),
  };
  const fabrica = jest.fn((cb) => {
    callbacks = cb;
    return bandeja;
  });
  const programar = jest.fn();
  const montado = montarCampana(document, { fabrica, programar, duracionEmergente: 5000 });
  return { ...montado, callbacks: () => callbacks, bandeja, programar };
}

describe('montarCampana', () => {
  test('arranca la bandeja y pinta el estado inicial: sin contador, sin canal', () => {
    const { bandeja } = preparar();
    expect(bandeja.iniciar).toHaveBeenCalledTimes(1);
    expect(document.querySelector('[data-zona="contador"]').hidden).toBe(true);
    expect(document.querySelector('[data-zona="conexion"]').className).toBe(
      'conexion conexion--sin-conexion',
    );
  });

  test('alCambiar pinta contador, estado del canal y lista con boton de marcar', () => {
    const { callbacks, bandeja } = preparar();

    callbacks().alCambiar({
      canal: ESTADO_CANAL.ESTABLE,
      noLeidas: 2,
      avisos: [aviso('n-2'), aviso('n-1', { leida: true })],
    });

    const contador = document.querySelector('[data-zona="contador"]');
    expect(contador.hidden).toBe(false);
    expect(contador.textContent).toBe('2');
    expect(document.querySelector('[data-zona="boton"]').getAttribute('aria-label')).toBe(
      '2 notificaciones sin leer',
    );
    expect(document.querySelector('[data-zona="conexion"]').className).toBe(
      'conexion conexion--estable',
    );
    expect(document.querySelector('[data-zona="lista-vacia"]').hidden).toBe(true);

    const items = document.querySelectorAll('[data-zona="lista"] li');
    expect(items).toHaveLength(2);
    expect(items[0].dataset.avisoId).toBe('n-2');
    expect(items[0].querySelector('.tarjeta__titulo').textContent).toBe('Titulo n-2');
    expect(items[1].querySelector('[data-accion="marcar-leida"]')).toBeNull();

    items[0].querySelector('[data-accion="marcar-leida"]').click();
    expect(bandeja.marcarLeida).toHaveBeenCalledWith('n-2');
  });

  test('con cero no leidos el contador se oculta y la lista vacia vuelve', () => {
    const { callbacks } = preparar();
    callbacks().alCambiar({ canal: ESTADO_CANAL.ESTABLE, noLeidas: 1, avisos: [aviso('n-1')] });
    callbacks().alCambiar({ canal: ESTADO_CANAL.ESTABLE, noLeidas: 0, avisos: [] });

    expect(document.querySelector('[data-zona="contador"]').hidden).toBe(true);
    expect(document.querySelector('[data-zona="lista-vacia"]').hidden).toBe(false);
    expect(document.querySelectorAll('[data-zona="lista"] li')).toHaveLength(0);
  });

  test('la caida del canal se pinta como Estado de conexion, no como Aviso (mapeo 5.4)', () => {
    const { callbacks } = preparar();
    callbacks().alCambiar({ canal: ESTADO_CANAL.RECONECTANDO, noLeidas: 0, avisos: [] });
    const conexion = document.querySelector('[data-zona="conexion"]');
    expect(conexion.className).toBe('conexion conexion--reconectando');
    expect(conexion.textContent).toBe('Reconectando con el canal…');
    expect(document.querySelector('[data-zona="emergentes"] .aviso--error')).toBeNull();
  });

  test('alAviso pinta una emergente no bloqueante con cierre propio y automatico', () => {
    const { callbacks, programar } = preparar();

    callbacks().alAviso(aviso('n-5'));

    const emergente = document.querySelector('[data-zona="emergentes"] .aviso--info');
    expect(emergente).not.toBeNull();
    expect(emergente.getAttribute('role')).toBe('status');
    expect(emergente.querySelector('.aviso__titulo').textContent).toBe('Titulo n-5');
    expect(document.querySelector('.velo')).toBeNull();
    expect(programar).toHaveBeenCalledWith(expect.any(Function), 5000);

    emergente.querySelector('[data-accion="cerrar-emergente"]').click();
    expect(document.querySelector('[data-zona="emergentes"] .aviso--info')).toBeNull();
  });

  test('el boton de la campana abre y cierra el panel', () => {
    preparar();
    const boton = document.querySelector('[data-zona="boton"]');
    const panel = document.querySelector('[data-zona="panel"]');

    boton.click();
    expect(panel.hidden).toBe(false);
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    boton.click();
    expect(panel.hidden).toBe(true);
    expect(boton.getAttribute('aria-expanded')).toBe('false');
  });
});

describe('utilidades', () => {
  test('textoDeContador concuerda en numero', () => {
    expect(textoDeContador(0)).toBe('Sin notificaciones sin leer');
    expect(textoDeContador(1)).toBe('1 notificacion sin leer');
    expect(textoDeContador(3)).toBe('3 notificaciones sin leer');
  });

  test('pintarEmergente con duracion 0 no programa el cierre', () => {
    document.body.innerHTML = '<div id="z"></div>';
    const programar = jest.fn();
    pintarEmergente(document.getElementById('z'), aviso('n-1'), { duracion: 0, programar });
    expect(programar).not.toHaveBeenCalled();
  });

  test('fechaLegible devuelve el original si no es fecha', () => {
    expect(fechaLegible('x')).toBe('x');
  });
});
