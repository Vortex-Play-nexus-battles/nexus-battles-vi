/**
 * UXC-5 — la vista de misiones: honesta sin servicio, completa con él.
 */

import { jest } from '@jest/globals';

import { FUENTE_SIN_SERVICIO } from './fuente-misiones.js';
import { montarMisiones, resumenDeMatricula, rutasDeMisiones } from './misiones.js';

const esperar = async () => {
  for (let i = 0; i < 10; i += 1) {
    await new Promise((resolver) => setTimeout(resolver, 0));
  }
};

const MISION = {
  id: 'templo',
  nombre: 'El Templo Olvidado',
  categoria: 'HISTORIA',
  dificultad: 'NORMAL',
  duracionHoras: 12,
  estado: 'DISPONIBLE',
  narrativa: 'En las profundidades del Bosque Sombrío…',
  objetivos: { principales: ['Derrotar al Guardián del Templo.'], secundarios: [] },
  enemigos: [],
  recompensas: { garantizadas: ['50 créditos'], potenciales: [] },
};

/** Configurador con servicios de mentira: un héroe y todo válido. */
function inyeccionesDeEstrategia() {
  return {
    consultar: jest.fn(async () => ({
      elementos: [{ id: 'h-1', tipo: 'HEROE', nombrePropio: 'Aquiles', productoId: 'p-1' }],
      ultima: true,
      totalPaginas: 1,
    })),
    consultarProducto: jest.fn(async () => ({ prototipo: 'Guerrero Armas' })),
    validar: jest.fn(async ({ heroe, nivel = 1, rotaciones }) => ({
      valida: true,
      heroe,
      nivel,
      habilidadesValidas: ['Embate sangriento', 'Ataque básico'],
      porDefecto: !rotaciones?.length,
      comportamientoPorDefecto: 'Ataque básico',
      rotaciones: (rotaciones ?? []).map((r, i) => ({
        prioridad: ['Alta', 'Media', 'Baja'][i],
        pasos: r.pasos,
      })),
    })),
    vista: jest.fn(async () => null),
  };
}

function fuenteDeLaboratorio(cambios = {}) {
  return {
    disponible: true,
    tablero: jest.fn(async () => ({ misiones: [MISION], total: 1, pagina: 0, totalPaginas: 1 })),
    destacadas: jest.fn(async () => [MISION]),
    detalle: jest.fn(async () => MISION),
    activas: jest.fn(async () => []),
    historial: jest.fn(async () => ({ completadas: [] })),
    reporte: jest.fn(),
    matricular: jest.fn(async () => ({})),
    cancelar: jest.fn(),
    marcarFavorita: jest.fn(async () => {}),
    ...cambios,
  };
}

beforeEach(() => {
  document.body.innerHTML = '<main data-vista="misiones"></main>';
  window.history.replaceState(null, '', '/misiones.html');
});

describe('sin servicio de misiones (hoy)', () => {
  test('dice qué pasa, por qué y qué se puede hacer, sin pedir nada', async () => {
    // Copia espiable de la fuente de producción (que está congelada).
    const fuente = { ...FUENTE_SIN_SERVICIO, tablero: jest.fn(), destacadas: jest.fn() };

    const modo = await montarMisiones(document, {
      fuente,
      identidad: 'ana',
      ubicacion: new URL('https://nexus.test/misiones.html'),
      estrategia: inyeccionesDeEstrategia(),
    });

    expect(modo).toBe('sin-abrir');
    const estado = document.querySelector('.misiones-estado');
    expect(estado.querySelector('h2').textContent).toBe('Las misiones todavía no están abiertas');
    expect(estado.textContent).toContain('preparar la estrategia de combate');
    expect(estado.querySelector('a[data-accion="jugar"]').getAttribute('href')).toMatch(
      /batallas\.html$/,
    );
    expect(document.body.textContent).not.toMatch(/próximamente|próxima actualización/i);
    expect(document.querySelector('.misiones-sin-abrir')).not.toBeNull();
    expect(document.querySelector('.mision-card')).toBeNull();
    expect(fuente.tablero).not.toHaveBeenCalled();
    expect(fuente.destacadas).not.toHaveBeenCalled();
  });

  test('las cuatro secciones existen; «Preparar la estrategia» lleva a la que funciona', async () => {
    const estrategia = inyeccionesDeEstrategia();
    await montarMisiones(document, {
      fuente: FUENTE_SIN_SERVICIO,
      identidad: 'ana',
      ubicacion: new URL('https://nexus.test/misiones.html'),
      estrategia,
    });

    expect([...document.querySelectorAll('[role="tab"]')].map((t) => t.textContent)).toEqual([
      'Tablón',
      'En curso',
      'Historial',
      'Estrategia',
    ]);
    // El configurador no pide el inventario hasta que se abre su sección.
    expect(estrategia.consultar).not.toHaveBeenCalled();

    document.querySelector('.misiones-estado [data-accion="preparar-estrategia"]').click();
    await esperar();

    expect(document.querySelector('#pestana-estrategia').getAttribute('aria-selected')).toBe(
      'true',
    );
    expect(estrategia.consultar).toHaveBeenCalled();
    expect(document.activeElement).toBe(document.querySelector('.estrategia__titulo'));
    expect(document.querySelectorAll('.estrategia__paso select')).toHaveLength(1);
  });

  test('En curso e Historial lo dicen en su sitio, con el siguiente paso', async () => {
    await montarMisiones(document, {
      fuente: FUENTE_SIN_SERVICIO,
      identidad: 'ana',
      ubicacion: new URL('https://nexus.test/misiones.html'),
      estrategia: inyeccionesDeEstrategia(),
    });

    document.querySelector('#pestana-en-curso').click();
    const enCurso = document.querySelector('[data-seccion="en-curso"]');
    expect(enCurso.textContent).toContain('Ningún héroe está en misión');
    expect(enCurso.querySelector('[data-accion="preparar-estrategia"]')).not.toBeNull();

    document.querySelector('#pestana-historial').click();
    expect(document.querySelector('[data-seccion="historial"]').textContent).toContain(
      'Todavía no hay historial',
    );
  });

  test('un enlace a una misión no abre nada que no existe: se queda en el estado honesto', async () => {
    const modo = await montarMisiones(document, {
      fuente: FUENTE_SIN_SERVICIO,
      identidad: 'ana',
      ubicacion: new URL('https://nexus.test/misiones.html?mision=templo'),
      estrategia: inyeccionesDeEstrategia(),
    });
    expect(modo).toBe('sin-abrir');
  });
});

describe('con servicio de misiones', () => {
  test('el tablón con su banner y sus tarjetas', async () => {
    const fuente = fuenteDeLaboratorio();
    const modo = await montarMisiones(document, {
      fuente,
      identidad: 'ana',
      ubicacion: new URL('https://nexus.test/misiones.html'),
      estrategia: inyeccionesDeEstrategia(),
    });
    await esperar();

    expect(modo).toBe('tablon');
    expect(document.querySelector('.misiones-estado')).toBeNull();
    expect(document.querySelector('.banner-misiones')).not.toBeNull();
    expect(document.querySelectorAll('.mision-card')).toHaveLength(1);
  });

  test('?mision=: el detalle; con héroe y estrategia comprobada se confirma y se matricula', async () => {
    const fuente = fuenteDeLaboratorio();
    const navegar = jest.fn();
    await montarMisiones(document, {
      fuente,
      identidad: 'ana',
      ubicacion: new URL('https://nexus.test/misiones.html?mision=templo#configurar'),
      navegar,
      estrategia: inyeccionesDeEstrategia(),
    });
    await esperar();

    expect(document.title).toBe('El Templo Olvidado · Misiones · Nexus Battles VI');
    const iniciar = document.querySelector('[data-accion="iniciar-mision"]');
    expect(iniciar.getAttribute('aria-disabled')).toBe('true');

    const paso = document.querySelector('.estrategia__paso select');
    paso.value = 'Embate sangriento';
    paso.dispatchEvent(new Event('change', { bubbles: true }));
    document
      .querySelector('.estrategia__formulario')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await esperar();
    expect(iniciar.getAttribute('aria-disabled')).toBe('false');

    iniciar.click();
    await esperar();
    const dialogo = document.querySelector('[role="dialog"]');
    expect(dialogo.textContent).toContain('¿Enviar a Aquiles a «El Templo Olvidado»?');
    expect(dialogo.textContent).toContain('queda bloqueado 12 horas');
    dialogo.querySelector('[data-accion="confirmar"]').click();
    await esperar();

    expect(fuente.matricular).toHaveBeenCalledWith({
      misionId: 'templo',
      heroeId: 'h-1',
      rotaciones: [{ pasos: ['Embate sangriento'] }],
    });
    expect(navegar).toHaveBeenCalledWith('misiones.html?iniciada=templo#en-curso');
  });

  test('si el servidor no deja matricular, se dice con su motivo y no se navega', async () => {
    const rechazo = Object.assign(new Error('409'), {
      detalle: 'Tu héroe está en un torneo: no puede salir de misión.',
    });
    const fuente = fuenteDeLaboratorio({ matricular: jest.fn().mockRejectedValue(rechazo) });
    const navegar = jest.fn();
    await montarMisiones(document, {
      fuente,
      identidad: 'ana',
      ubicacion: new URL('https://nexus.test/misiones.html?mision=templo'),
      navegar,
      estrategia: inyeccionesDeEstrategia(),
    });
    await esperar();
    const paso = document.querySelector('.estrategia__paso select');
    paso.value = 'Ataque básico';
    paso.dispatchEvent(new Event('change', { bubbles: true }));
    document
      .querySelector('.estrategia__formulario')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await esperar();
    document.querySelector('[data-accion="iniciar-mision"]').click();
    await esperar();
    document.querySelector('[data-accion="confirmar"]').click();
    await esperar();

    expect(navegar).not.toHaveBeenCalled();
    expect(document.querySelector('.mision-detalle__aviso').textContent).toContain(
      'Tu héroe está en un torneo: no puede salir de misión.',
    );
  });

  test('una misión que ya no existe lo dice y lleva al tablón', async () => {
    const fuente = fuenteDeLaboratorio({
      detalle: jest.fn().mockRejectedValue(Object.assign(new Error('404'), { status: 404 })),
    });
    await montarMisiones(document, {
      fuente,
      identidad: 'ana',
      ubicacion: new URL('https://nexus.test/misiones.html?mision=vieja'),
      estrategia: inyeccionesDeEstrategia(),
    });

    const vacio = document.querySelector('.estado-vista--vacio');
    expect(vacio.textContent).toContain('Esa misión no existe o ya no está publicada');
    expect(vacio.querySelector('a').getAttribute('href')).toBe('misiones.html');
  });
});

test('las rutas son relativas a la vista y las del detalle se pueden compartir', () => {
  const rutas = rutasDeMisiones();
  expect(rutas.hrefDe({ id: 'a b' }, 'configurar')).toBe('misiones.html?mision=a%20b#configurar');
  expect(rutas.hrefReporte('e/1')).toBe('misiones.html?reporte=e%2F1');
  expect(rutas.hrefEquipamiento).toBe('../inventario/inventario.html#equipamiento');
});

test('el resumen de la matrícula dice qué queda bloqueado y cuánto', () => {
  const resumen = resumenDeMatricula(MISION, {
    heroe: 'Aquiles',
    rotaciones: [{ pasos: ['Embate sangriento', 'Ataque básico'] }],
  });
  expect(resumen.textContent).toContain('1. Embate sangriento → Ataque básico');
  expect(resumen.textContent).toContain(
    'Aquiles queda bloqueado 12 horas: no podrá jugar en línea, entrar en torneos ni cambiar su equipamiento',
  );
});
