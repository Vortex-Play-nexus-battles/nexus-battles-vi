/**
 * UXC-7 — SanctionCountdown (estado de la cuenta) y AdminTimeline (línea de
 * tiempo de las sanciones).
 */

import { estadoDeCuenta, hechosDeSanciones, sancionQueRestringe } from './sancion.js';
import { lineaDeTiempo } from './linea-de-tiempo.js';

const DIA = 86_400_000;
const AHORA = Date.parse('2026-09-22T12:00:00Z');

const sancion = (cambios = {}) => ({
  id: 's-1',
  usuarioId: 'u-1',
  tipo: 'SUSPENSION',
  motivo: 'Lenguaje ofensivo',
  politica: null,
  emitidaPor: 'm-1',
  rolEmisor: 'MODERADOR',
  emitidaEn: '2026-09-21T12:00:00Z',
  vigenteHasta: '2026-09-24T12:00:00Z',
  revertidaEn: null,
  motivoReversion: null,
  vigente: true,
  ...cambios,
});

describe('sancionQueRestringe', () => {
  test('la dice `vigente` del servidor; una advertencia no restringe', () => {
    expect(sancionQueRestringe([sancion({ vigente: false })])).toBeNull();
    expect(sancionQueRestringe([sancion({ tipo: 'ADVERTENCIA', vigenteHasta: null })])).toBeNull();
    expect(sancionQueRestringe([sancion()]).id).toBe('s-1');
  });

  test('manda el baneo; entre suspensiones, la que dura más', () => {
    const corta = sancion({ id: 'corta', vigenteHasta: '2026-09-23T00:00:00Z' });
    const larga = sancion({ id: 'larga', vigenteHasta: '2026-09-30T00:00:00Z' });
    const baneo = sancion({ id: 'baneo', tipo: 'BANEO', vigenteHasta: null });
    expect(sancionQueRestringe([corta, larga]).id).toBe('larga');
    expect(sancionQueRestringe([corta, baneo, larga]).id).toBe('baneo');
  });
});

describe('estadoDeCuenta (SanctionCountdown)', () => {
  test('en regla, contando las advertencias sin alarmar', () => {
    const tarjeta = estadoDeCuenta([sancion({ tipo: 'ADVERTENCIA', vigenteHasta: null })], {
      hrefSanciones: '/sanciones',
    });
    expect(tarjeta.dataset.estadoCuenta).toBe('en-regla');
    expect(tarjeta.textContent).toContain('Tu cuenta está en regla');
    expect(tarjeta.textContent).toContain('Tienes una advertencia: no te impide jugar.');
    expect(tarjeta.querySelector('a').getAttribute('href')).toBe('/sanciones');
  });

  test('suspendida: cuenta atrás hasta la fecha del servidor, fecha y motivo', () => {
    const hasta = new Date(Date.now() + 3 * DIA).toISOString();
    const tarjeta = estadoDeCuenta([sancion({ vigenteHasta: hasta })], { persona: 'tercera' });

    expect(tarjeta.dataset.estadoCuenta).toBe('suspendida');
    expect(tarjeta.textContent).toContain('Suspensión activa');
    const tiempo = tarjeta.querySelector('time.cuenta-atras');
    expect(tiempo.getAttribute('datetime')).toBe(hasta);
    expect(tiempo.getAttribute('aria-label')).toMatch(/^Termina en \d días?$/);
    expect(tarjeta.textContent).toContain('Motivo: Lenguaje ofensivo');
    // Sin enlace si no se da (la consola ya está en la ficha de la cuenta).
    expect(tarjeta.querySelector('a')).toBeNull();
  });

  test('baneada: sin fecha de fin; en la consola habla en tercera persona', () => {
    const tarjeta = estadoDeCuenta([sancion({ tipo: 'BANEO', vigenteHasta: null })]);
    expect(tarjeta.textContent).toContain('Cuenta baneada');
    expect(tarjeta.textContent).toContain('Sin fecha de fin.');
    expect(estadoDeCuenta([], { persona: 'tercera' }).textContent).toContain(
      'La cuenta está en regla',
    );
  });
});

describe('línea de tiempo (AdminTimeline)', () => {
  test('emitida, revertida y el fin de la suspensión, del más reciente al más antiguo', () => {
    const hechos = hechosDeSanciones(
      [
        sancion(),
        sancion({
          id: 's-2',
          tipo: 'ADVERTENCIA',
          vigenteHasta: null,
          emitidaEn: '2026-09-10T08:00:00Z',
          revertidaEn: '2026-09-11T08:00:00Z',
          motivoReversion: 'Apelación aceptada',
        }),
      ],
      { ahora: AHORA },
    );
    const lista = lineaDeTiempo(hechos, { etiqueta: 'Historial' });

    expect(lista.getAttribute('aria-label')).toBe('Historial');
    const titulos = [...lista.querySelectorAll('.linea-tiempo__titulo')].map((t) => t.textContent);
    expect(titulos).toEqual([
      'La suspensión termina',
      'Suspensión emitida',
      'Advertencia revertida',
      'Advertencia emitida',
    ]);
    const [termina, emitida] = lista.querySelectorAll('.linea-tiempo__hecho');
    expect(termina.className).toContain('linea-tiempo__hecho--futuro');
    expect(termina.textContent).toContain('Previsto');
    expect(emitida.dataset.tono).toBe('advertencia');
    expect(emitida.textContent).toContain('Por moderación');
    expect(emitida.querySelector('time').getAttribute('datetime')).toBe('2026-09-21T12:00:00Z');
    // La emisión se sigue encontrando por el id de la sanción (la consola y
    // la prueba de extremo a extremo del historial la buscan así).
    expect(emitida.dataset.sancionId).toBe('s-1');
    expect(lista.querySelector('[data-sancion-id="s-2"]').textContent).toContain(
      'Advertencia emitida',
    );
    expect(termina.dataset.sancionId).toBeUndefined();
  });

  test('una suspensión ya cumplida dice que terminó, sin cuenta atrás', () => {
    const hechos = hechosDeSanciones([sancion({ vigenteHasta: '2026-09-21T18:00:00Z' })], {
      ahora: AHORA,
    });
    const fin = hechos.find((h) => h.titulo.startsWith('La suspensión'));
    expect(fin.titulo).toBe('La suspensión terminó');
    expect(fin.futuro).toBe(false);
    expect(fin.extra).toBeNull();
  });
});
