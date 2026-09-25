/**
 * UXC-5 — el tablón de misiones (MissionBoard + MissionCard).
 *
 * «El Templo Olvidado» es el ejemplo de misión del documento del curso
 * (§7.8.14); aquí es un dato de prueba, no del producto.
 */

import { jest } from '@jest/globals';

import {
  indicadorDeDificultad,
  montarTablon,
  tablonSinAbrir,
  tarjetaDeMision,
} from './tablon-misiones.js';

const TEMPLO = {
  id: 'templo',
  nombre: 'El Templo Olvidado',
  categoria: 'HISTORIA',
  descripcionBreve: 'Un antiguo templo en el Bosque Sombrío, custodiado por un guardián milenario.',
  dificultad: 'NORMAL',
  duracionHoras: 12,
  nivelRecomendado: 15,
  recompensasDestacadas: [
    '50 créditos',
    '1 Cofre de Bronce',
    'Armadura «Piel del Guardián»',
    'Otra',
  ],
  estado: 'DISPONIBLE',
};

const rutas = {
  hrefDe: (m, ancla = null) => `misiones.html?mision=${m.id}${ancla ? `#${ancla}` : ''}`,
  hrefReporte: (id) => `misiones.html?reporte=${id}`,
  hrefEnCurso: 'misiones.html#en-curso',
};

const esperar = async () => {
  for (let i = 0; i < 5; i += 1) {
    await new Promise((resolver) => setTimeout(resolver, 0));
  }
};

const acciones = (tarjeta) =>
  [...tarjeta.querySelectorAll('.mision-card__acciones a')].map((a) => [
    a.textContent,
    a.getAttribute('href'),
  ]);

describe('MissionCard', () => {
  test('lo que pide §7.8.9: nombre, descripción, dificultad, duración, nivel, recompensas y estado', () => {
    const tarjeta = tarjetaDeMision(TEMPLO, rutas);

    expect(tarjeta.querySelector('h3').textContent).toBe('El Templo Olvidado');
    expect(tarjeta.getAttribute('aria-labelledby')).toBe(tarjeta.querySelector('h3').id);
    expect(tarjeta.querySelector('.mision-categoria').textContent).toBe('Historia');
    expect(tarjeta.querySelector('.mision-estado').textContent).toBe('Disponible');
    expect(tarjeta.querySelector('.dificultad').textContent).toBe('Normal');
    expect(tarjeta.textContent).toContain('12 horas');
    expect(tarjeta.textContent).toContain('Nivel recomendado15');
    // Como mucho tres recompensas en la tarjeta: el resto, en el detalle.
    expect(tarjeta.querySelectorAll('.mision-card__recompensas li')).toHaveLength(3);
  });

  test('disponible: «Iniciar misión» lleva al configurador del detalle', () => {
    expect(acciones(tarjetaDeMision(TEMPLO, rutas))).toEqual([
      ['Iniciar misión', 'misiones.html?mision=templo#configurar'],
      ['Ver detalles', 'misiones.html?mision=templo'],
    ]);
  });

  test('completada: repetir y, si hay, su reporte', () => {
    const tarjeta = tarjetaDeMision(
      { ...TEMPLO, estado: 'COMPLETADA', ultimaEjecucionId: 'e-1' },
      rutas,
    );
    expect(acciones(tarjeta).map(([t]) => t)).toEqual([
      'Repetir misión',
      'Ver reporte',
      'Ver detalles',
    ]);
  });

  test('en progreso: el avance con su cifra y «Ver progreso» a En curso', () => {
    const tarjeta = tarjetaDeMision({ ...TEMPLO, estado: 'EN_PROGRESO', progreso: 0.45 }, rutas);

    const barra = tarjeta.querySelector('[role="progressbar"]');
    expect(barra.getAttribute('aria-valuenow')).toBe('45');
    expect(tarjeta.querySelector('.progreso__valor').textContent).toBe('45 %');
    expect(acciones(tarjeta)[0]).toEqual(['Ver progreso', 'misiones.html#en-curso']);
  });

  test('bloqueada: dice por qué y solo deja ver detalles', () => {
    const tarjeta = tarjetaDeMision(
      { ...TEMPLO, estado: 'BLOQUEADA', motivoBloqueo: 'Completa «El Templo Olvidado» primero.' },
      rutas,
    );
    expect(tarjeta.querySelector('.mision-card__motivo').textContent).toBe(
      'Completa «El Templo Olvidado» primero.',
    );
    expect(acciones(tarjeta).map(([t]) => t)).toEqual(['Ver detalles']);
  });

  test('las acciones nombran su misión para el lector de pantalla', () => {
    const [iniciar] = tarjetaDeMision(TEMPLO, rutas).querySelectorAll('.mision-card__accion');
    expect(iniciar.getAttribute('aria-label')).toBe('Iniciar misión: El Templo Olvidado');
  });

  test('la dificultad son marcas que se cuentan y una palabra', () => {
    const extremo = indicadorDeDificultad('EXTREMO');
    expect(extremo.querySelectorAll('.dificultad__marca--llena')).toHaveLength(4);
    expect(extremo.querySelector('.dificultad__marcas').getAttribute('aria-hidden')).toBe('true');
    expect(extremo.textContent).toBe('Extremo');
    expect(indicadorDeDificultad('NINGUNA')).toBeNull();
  });
});

describe('MissionBoard', () => {
  function fuenteCon(respuestas) {
    return {
      disponible: true,
      tablero: jest.fn(async (criterios) =>
        typeof respuestas === 'function' ? respuestas(criterios) : respuestas,
      ),
    };
  }

  beforeEach(() => {
    document.body.innerHTML = '<div id="tablon"></div>';
  });

  test('empieza por Historia y pasa los criterios a la fuente: no filtra por su cuenta', async () => {
    const fuente = fuenteCon({ misiones: [TEMPLO], total: 1, pagina: 0, totalPaginas: 1 });
    montarTablon(document.getElementById('tablon'), { fuente, ...rutas });
    await esperar();

    expect(fuente.tablero).toHaveBeenCalledWith({
      categoria: 'HISTORIA',
      dificultad: '',
      estado: '',
      duracion: '',
      pagina: 0,
    });
    expect(document.querySelectorAll('.misiones-rejilla .mision-card')).toHaveLength(1);
    expect(document.querySelector('.misiones-categoria__cuenta').textContent).toBe('1 misión');
    expect(document.querySelector('[role="tab"][aria-selected="true"]').textContent).toBe(
      'Historia',
    );
  });

  test('cambiar de pestaña y de filtro vuelve a preguntar, desde la primera página', async () => {
    const fuente = fuenteCon({ misiones: [], total: 0, pagina: 0, totalPaginas: 0 });
    montarTablon(document.getElementById('tablon'), { fuente, ...rutas });
    await esperar();

    document.querySelector('[role="tab"][data-pestana="categoria-exploracion"]').click();
    await esperar();
    const dificultad = document.querySelector('select[name="dificultad"]');
    dificultad.value = 'DIFICIL';
    dificultad.dispatchEvent(new Event('change', { bubbles: true }));
    await esperar();

    expect(fuente.tablero).toHaveBeenLastCalledWith({
      categoria: 'EXPLORACION',
      dificultad: 'DIFICIL',
      estado: '',
      duracion: '',
      pagina: 0,
    });
  });

  test('vacío por filtros: lo dice y «Quitar filtros» los quita', async () => {
    const fuente = fuenteCon({ misiones: [], total: 0, pagina: 0, totalPaginas: 0 });
    montarTablon(document.getElementById('tablon'), { fuente, ...rutas });
    await esperar();
    const estado = document.querySelector('select[name="estado"]');
    estado.value = 'COMPLETADA';
    estado.dispatchEvent(new Event('change', { bubbles: true }));
    await esperar();

    const vacio = document.querySelector('.misiones-categoria:not([hidden]) .estado-vista--vacio');
    expect(vacio.textContent).toContain('Ninguna misión de Historia cumple estos filtros');
    vacio.querySelector('[data-accion="quitar-filtros"]').click();
    await esperar();

    expect(estado.value).toBe('');
    expect(fuente.tablero).toHaveBeenLastCalledWith(expect.objectContaining({ estado: '' }));
  });

  test('vacío sin filtros: la categoría no tiene misiones todavía', async () => {
    const fuente = fuenteCon({ misiones: [], total: 0, pagina: 0, totalPaginas: 0 });
    montarTablon(document.getElementById('tablon'), { fuente, ...rutas });
    await esperar();

    expect(document.querySelector('.estado-vista--vacio').textContent).toContain(
      'Todavía no hay misiones de Historia',
    );
  });

  test('un fallo se dice sin códigos y «Reintentar» vuelve a preguntar', async () => {
    const fuente = {
      disponible: true,
      tablero: jest
        .fn()
        .mockRejectedValueOnce(Object.assign(new Error('x'), { status: 502 }))
        .mockResolvedValue({ misiones: [TEMPLO], total: 1, pagina: 0, totalPaginas: 1 }),
    };
    montarTablon(document.getElementById('tablon'), { fuente, ...rutas });
    await esperar();

    const error = document.querySelector('.estado-vista--error');
    expect(error.textContent).toContain('No pudimos cargar las misiones');
    expect(error.textContent).not.toMatch(/502/);
    error.querySelector('button').click();
    await esperar();
    expect(document.querySelectorAll('.mision-card')).toHaveLength(1);
  });

  test('dieciséis por página, con el control de páginas', async () => {
    const pagina = (n) =>
      Array.from({ length: 16 }, (_, i) => ({
        ...TEMPLO,
        id: `m-${n}-${i}`,
        nombre: `Misión ${n}-${i}`,
      }));
    const fuente = fuenteCon((criterios) => ({
      misiones: pagina(criterios.pagina),
      total: 20,
      pagina: criterios.pagina,
      totalPaginas: 2,
    }));
    montarTablon(document.getElementById('tablon'), { fuente, ...rutas });
    await esperar();

    const control = document.querySelector('.paginacion');
    expect(control.getAttribute('aria-label')).toBe('Páginas de misiones');
    [...control.querySelectorAll('button')].find((b) => b.textContent.trim() === '2').click();
    await esperar();

    expect(fuente.tablero).toHaveBeenLastCalledWith(expect.objectContaining({ pagina: 1 }));
    expect(document.activeElement).toBe(document.querySelector('.misiones-categoria__cuenta'));
  });
});

describe('tablón sin abrir', () => {
  test('explica las tres categorías sin fingir ni una tarjeta', () => {
    const seccion = tablonSinAbrir();

    expect(seccion.querySelector('h2').textContent).toBe('Así serán las misiones');
    expect([...seccion.querySelectorAll('h3')].map((t) => t.textContent)).toEqual([
      'Historia',
      'Desafío',
      'Exploración',
    ]);
    expect(seccion.querySelector('.mision-card')).toBeNull();
    expect(seccion.textContent).not.toMatch(/próximamente/i);
  });
});
