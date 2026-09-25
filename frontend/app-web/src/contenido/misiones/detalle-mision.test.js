/**
 * UXC-5 — el detalle de una misión (MissionDetail).
 *
 * El dato de prueba es el ejemplo de misión del documento del curso
 * (§7.8.14, «El Templo Olvidado»), tal como lo escribe.
 */

import { jest } from '@jest/globals';

import { admiteInicio, compartir, detalleDeMision, tablaDeRecompensas } from './detalle-mision.js';

const TEMPLO = {
  id: 'templo',
  nombre: 'El Templo Olvidado',
  categoria: 'HISTORIA',
  descripcionBreve: 'Un antiguo templo custodiado por un guardián milenario.',
  dificultad: 'NORMAL',
  duracionHoras: 12,
  nivelRecomendado: 15,
  estado: 'DISPONIBLE',
  narrativa:
    'En las profundidades del Bosque Sombrío yace un antiguo templo dedicado a los Dioses Olvidados.\n\nEstá custodiado por criaturas corrompidas y un guardián milenario.',
  objetivos: {
    principales: [
      'Derrotar al Guardián del Templo (Jefe final).',
      'Explorar las 5 cámaras del templo.',
    ],
    secundarios: [
      'Completar la misión sin que la vida del héroe baje del 50 %.',
      'Derrotar al Máster si aparece.',
      'Encontrar los 3 fragmentos del Sello Antiguo.',
    ],
  },
  enemigos: [
    {
      nombre: 'Sombras Corrompidas',
      cantidad: 10,
      descripcion: 'Enemigos básicos con ataque moderado.',
    },
    { nombre: 'Guardianes de Piedra', cantidad: 5, descripcion: 'Enemigos con alta defensa.' },
    { nombre: 'Espectros Ancestrales', cantidad: 3, descripcion: 'Enemigos con ataques mágicos.' },
  ],
  jefe: {
    nombre: 'El Guardián Eterno',
    prototipo: 'Guerrero Tanque',
    vida: 100,
    descripcion: 'Con habilidades potenciadas.',
  },
  probabilidadMaster: 0.15,
  masters: [
    {
      nombre: 'Sombra del Olvido',
      prototipo: 'Pícaro Veneno',
      epica: {
        nombre: 'Velo de Sombras',
        efectoGeneral: '+2 a la defensa para todos los héroes.',
        efectoPotenciado:
          'El héroe se vuelve intangible durante 1 turno, evitando todo el daño recibido y causando envenenamiento al atacante (+3 de daño por veneno durante 2 turnos).',
      },
    },
  ],
  recompensas: {
    garantizadas: ['50 créditos', '1 Cofre de Bronce (contiene ítems comunes)'],
    potenciales: [
      { nombre: 'Fragmento del Sello Antiguo', probabilidad: 0.6, detalle: 'cada uno, 3 en total' },
      { nombre: 'Armadura «Piel del Guardián»', probabilidad: 0.2 },
      { nombre: 'Arma «Espada del Templo»', probabilidad: 0.15 },
    ],
    primeraVez: ['10 créditos adicionales', 'Título «Explorador del Templo»'],
  },
};

const rutas = {
  hrefTablon: 'misiones.html',
  hrefReporte: (id) => `misiones.html?reporte=${id}`,
  hrefEnCurso: 'misiones.html#en-curso',
  urlParaCompartir: 'https://nexus.test/misiones.html?mision=templo',
};

const configurador = () => ({ elemento: document.createElement('section') });

beforeEach(() => {
  document.body.innerHTML = '';
});

describe('lo que enseña', () => {
  test('narrativa, objetivos, enemigos, jefe, Máster y recompensas del documento', () => {
    const { elemento } = detalleDeMision(TEMPLO, { ...rutas, configurador: configurador() });
    document.body.append(elemento);

    expect(elemento.querySelector('h1').textContent).toBe('El Templo Olvidado');
    expect(elemento.querySelectorAll('.mision-detalle__narrativa')).toHaveLength(2);
    expect(elemento.querySelector('[data-seccion="objetivos"]').textContent).toContain(
      'Explorar las 5 cámaras del templo.',
    );
    const enemigos = [...elemento.querySelectorAll('.mision-enemigo__cantidad')].map((c) =>
      c.getAttribute('aria-label'),
    );
    expect(enemigos).toEqual(['10 en total', '5 en total', '3 en total']);
    const jefe = elemento.querySelector('.mision-jefe');
    expect(jefe.textContent).toContain('El Guardián Eterno');
    expect(jefe.textContent).toContain('Guerrero Tanque');
    expect(jefe.textContent).toContain('100');
    const master = elemento.querySelector('[data-seccion="master"]');
    expect(master.textContent).toContain('Probabilidad de que aparezca: 15 %');
    expect(master.textContent).toContain('Épica: Velo de Sombras');
    expect(master.textContent).toContain('Solo Pícaro Veneno');
  });

  test('la tabla de recompensas agrupa por cuándo se gana, con su probabilidad', () => {
    const tabla = tablaDeRecompensas(TEMPLO);
    const filas = [...tabla.querySelectorAll('tbody tr:not(.mision-recompensas__grupo)')].map(
      (fila) => [...fila.children].map((celda) => celda.textContent),
    );

    expect(
      [...tabla.querySelectorAll('.mision-recompensas__grupo th')].map((t) => t.textContent),
    ).toEqual(['Garantizadas', 'Potenciales', 'Primera vez']);
    expect(filas).toContainEqual(['Fragmento del Sello Antiguo', '60 % · cada uno, 3 en total']);
    expect(filas).toContainEqual(['Arma «Espada del Templo»', '15 %']);
    expect(filas).toContainEqual(['50 créditos', 'Al completar la misión']);
    expect(tabla.querySelector('caption').textContent).toBe('Recompensas de El Templo Olvidado');
  });

  test('lo que la misión no trae no se pinta', () => {
    const { elemento } = detalleDeMision(
      {
        ...TEMPLO,
        masters: [],
        probabilidadMaster: null,
        jefe: null,
        enemigos: [],
        recompensas: {},
      },
      { ...rutas, configurador: configurador() },
    );
    expect(elemento.querySelector('[data-seccion="master"]')).toBeNull();
    expect(elemento.querySelector('[data-seccion="enemigos"]')).toBeNull();
    expect(elemento.querySelector('[data-seccion="recompensas"]')).toBeNull();
  });
});

describe('iniciar, repetir o no', () => {
  test('qué estados admiten mandar a un héroe', () => {
    expect(admiteInicio({ estado: 'DISPONIBLE' })).toBe(true);
    expect(admiteInicio({ estado: 'COMPLETADA' })).toBe(true);
    expect(admiteInicio({ estado: 'BLOQUEADA' })).toBe(false);
    expect(admiteInicio({ estado: 'EN_PROGRESO' })).toBe(false);
  });

  test('«Iniciar misión» espera a que haya héroe y estrategia, y dice por qué', async () => {
    const alIniciar = jest.fn(async () => {});
    const detalle = detalleDeMision(TEMPLO, { ...rutas, configurador: configurador(), alIniciar });
    document.body.append(detalle.elemento);
    const boton = detalle.elemento.querySelector('[data-accion="iniciar-mision"]');

    expect(boton.getAttribute('aria-disabled')).toBe('true');
    expect(document.getElementById(boton.getAttribute('aria-describedby')).textContent).toBe(
      'Primero elige el héroe y comprueba su estrategia.',
    );
    boton.click();
    expect(alIniciar).not.toHaveBeenCalled();

    detalle.habilitarInicio(true);
    boton.click();
    await Promise.resolve();
    expect(alIniciar).toHaveBeenCalledTimes(1);
    expect(detalle.elemento.querySelector('#configurar')).not.toBeNull();
  });

  test('completada: «Repetir misión»; bloqueada: su motivo y ningún configurador', () => {
    const repetir = detalleDeMision(
      { ...TEMPLO, estado: 'COMPLETADA' },
      { ...rutas, configurador: configurador() },
    );
    expect(repetir.elemento.querySelector('[data-accion="iniciar-mision"]').textContent).toBe(
      'Repetir misión',
    );

    const bloqueada = detalleDeMision(
      { ...TEMPLO, estado: 'BLOQUEADA', motivoBloqueo: 'Completa antes el prólogo.' },
      { ...rutas, configurador: configurador() },
    );
    expect(bloqueada.elemento.querySelector('#configurar')).toBeNull();
    expect(bloqueada.elemento.querySelector('.mision-detalle__bloqueo').textContent).toBe(
      'Completa antes el prólogo.',
    );
  });

  test('en curso: lleva a ver su progreso', () => {
    const { elemento } = detalleDeMision(
      { ...TEMPLO, estado: 'EN_PROGRESO' },
      { ...rutas, configurador: configurador() },
    );
    expect(elemento.querySelector('[data-accion="ver-progreso"]').getAttribute('href')).toBe(
      'misiones.html#en-curso',
    );
  });
});

describe('favorita y compartir', () => {
  test('favorita es un conmutador con estado; si no se guarda, vuelve atrás y lo dice', async () => {
    const alMarcarFavorita = jest
      .fn()
      .mockResolvedValueOnce()
      .mockRejectedValueOnce(new Error('x'));
    const { elemento } = detalleDeMision(TEMPLO, {
      ...rutas,
      configurador: configurador(),
      alMarcarFavorita,
    });
    document.body.append(elemento);
    const boton = elemento.querySelector('[data-accion="favorita"]');

    expect(boton.getAttribute('aria-pressed')).toBe('false');
    boton.click();
    await Promise.resolve();
    expect(alMarcarFavorita).toHaveBeenLastCalledWith(true);
    expect(boton.getAttribute('aria-pressed')).toBe('true');
    expect(boton.textContent).toBe('En tus favoritas');

    boton.click();
    await new Promise((r) => setTimeout(r, 0));
    expect(boton.getAttribute('aria-pressed')).toBe('true');
    expect(elemento.querySelector('[data-zona="aviso"]').textContent).toContain(
      'No pudimos guardar tu favorita',
    );
  });

  test('compartir: la hoja del sistema, o copiar, o enseñarlo para copiar a mano', async () => {
    const zona = document.createElement('div');
    const url = 'https://nexus.test/x';

    const conHoja = { share: jest.fn(async () => {}) };
    expect(await compartir({ titulo: 't', url, zona, navegador: conHoja })).toBe('compartida');

    const conPortapapeles = { clipboard: { writeText: jest.fn(async () => {}) } };
    expect(await compartir({ titulo: 't', url, zona, navegador: conPortapapeles })).toBe('copiada');
    expect(zona.textContent).toContain('Enlace copiado');

    const sinNada = {};
    document.body.append(zona);
    expect(await compartir({ titulo: 't', url, zona, navegador: sinNada })).toBe('a-mano');
    expect(zona.querySelector('input').value).toBe(url);
  });
});
