/**
 * UXC-5 — el configurador de estrategia (RotationBuilder).
 *
 * Los datos de estas pruebas imitan lo que devuelven inventario, productos y
 * héroes; las habilidades son las de Guerrero Armas en el catálogo
 * (`PrototiposIniciales`), con su desbloqueo por nivel (1, 4 y 8).
 */

import { jest } from '@jest/globals';

import {
  ROTACIONES_MAXIMAS,
  constructorDeEstrategia,
  heroesConPrototipo,
  mensajeDeFallo,
} from './constructor-estrategia.js';

const HABILIDADES_POR_NIVEL = {
  1: ['Embate sangriento', 'Ataque básico'],
  4: ['Embate sangriento', 'Lanza de los dioses', 'Ataque básico'],
  8: ['Embate sangriento', 'Lanza de los dioses', 'Golpe de tormenta', 'Ataque básico'],
};

const esperar = async () => {
  for (let i = 0; i < 8; i += 1) {
    await new Promise((resolver) => setTimeout(resolver, 0));
  }
};

function servicios({
  elementos = [
    { id: 'h-1', tipo: 'HEROE', nombrePropio: 'Aquiles', productoId: 'p-armas' },
    { id: 'o-1', tipo: 'ARMA', nombrePropio: 'Espada', productoId: 'p-espada' },
  ],
  productos = { 'p-armas': { nombre: 'Guerrero Armas', prototipo: 'Guerrero Armas' } },
  validar,
} = {}) {
  return {
    consultar: jest.fn(async () => ({ elementos, ultima: true, totalPaginas: 1 })),
    consultarProducto: jest.fn(async (id) => {
      if (!productos[id]) {
        throw new Error('404');
      }
      return productos[id];
    }),
    validar:
      validar ??
      jest.fn(async ({ heroe, nivel = 1, rotaciones }) => {
        const validas = HABILIDADES_POR_NIVEL[nivel] ?? HABILIDADES_POR_NIVEL[1];
        if (!rotaciones || rotaciones.length === 0) {
          return {
            valida: true,
            heroe,
            nivel,
            habilidadesValidas: validas,
            porDefecto: true,
            comportamientoPorDefecto: 'Ataque básico',
            rotaciones: [],
          };
        }
        const ajena = rotaciones.flatMap((r) => r.pasos).find((p) => !validas.includes(p));
        if (ajena) {
          return {
            valida: false,
            motivo: `La rotación 1 usa una habilidad que ${heroe} no posee en nivel ${nivel}: ${ajena}.`,
            heroe,
            nivel,
            habilidadesValidas: validas,
            porDefecto: false,
            comportamientoPorDefecto: 'Ataque básico',
          };
        }
        return {
          valida: true,
          heroe,
          nivel,
          habilidadesValidas: validas,
          porDefecto: false,
          comportamientoPorDefecto: 'Ataque básico',
          rotaciones: rotaciones.map((r, i) => ({
            prioridad: ['Alta', 'Media', 'Baja'][i],
            pasos: r.pasos,
          })),
        };
      }),
    vista: jest.fn(async (prototipo, nivel) => ({
      nombre: prototipo,
      tipo: 'Guerrero',
      esSanador: false,
      nivel,
      estadisticas: { poder: 8, vida: 44, defensa: 11, ataque: '10 + 1d6', dano: '1d6' },
      accionesDisponibles: (HABILIDADES_POR_NIVEL[nivel] ?? [])
        .filter((a) => a !== 'Ataque básico')
        .map((nombre) => ({ nombre, costo: '4 puntos de poder', efecto: '+2 al ataque' })),
      multiplicadorDeEfecto: nivel,
      epica: { nombre: 'Furia del titán' },
    })),
  };
}

async function montar(opciones = {}) {
  const inyecciones = servicios(opciones);
  const alCambiar = jest.fn();
  const configurador = constructorDeEstrategia({
    identidad: 'ana',
    alCambiar,
    ...inyecciones,
    ...opciones.extra,
  });
  document.body.replaceChildren(configurador.elemento);
  await configurador.cargar();
  await esperar();
  return { configurador, alCambiar, ...inyecciones };
}

const pasos = () => [...document.querySelectorAll('.estrategia__paso select')];
const elegir = (select, valor) => {
  select.value = valor;
  select.dispatchEvent(new Event('change', { bubbles: true }));
};
const comprobar = async () => {
  document
    .querySelector('.estrategia__formulario')
    .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  await esperar();
};

beforeEach(() => {
  document.body.innerHTML = '';
});

describe('héroes', () => {
  test('lista solo los héroes del inventario, con su prototipo, y elige el primero', async () => {
    const { validar, vista, configurador } = await montar();

    const opciones = document.querySelectorAll('.estrategia__heroe');
    expect(opciones).toHaveLength(1);
    expect(opciones[0].textContent).toContain('Aquiles');
    expect(opciones[0].textContent).toContain('Guerrero Armas');
    expect(document.querySelector('.estrategia__heroe-entrada').checked).toBe(true);
    // Las habilidades se preguntan al servidor: validar sin rotaciones.
    expect(validar).toHaveBeenCalledWith({ heroe: 'Guerrero Armas', nivel: 1 });
    expect(vista).toHaveBeenCalledWith('Guerrero Armas', 1);
    expect(configurador.estrategia()).toBeNull();
  });

  test('un héroe cuyo prototipo no se pudo leer sale deshabilitado y dice por qué', async () => {
    await montar({
      elementos: [{ id: 'h-9', tipo: 'HEROE', nombrePropio: 'Sin ficha', productoId: 'p-rota' }],
      productos: {},
    });

    const entrada = document.querySelector('.estrategia__heroe-entrada');
    expect(entrada.disabled).toBe(true);
    const motivo = document.getElementById(entrada.getAttribute('aria-describedby'));
    expect(motivo.textContent).toBe('No pudimos leer su prototipo del catálogo.');
    expect(document.querySelector('[data-zona="editor"]').hidden).toBe(true);
  });

  test('sin héroes: lo dice y lleva a la tienda', async () => {
    await montar({ elementos: [] });

    const vacio = document.querySelector('.estado-vista--vacio');
    expect(vacio.textContent).toContain('Todavía no tienes héroes');
    expect(vacio.querySelector('a').getAttribute('href')).toMatch(/tienda\.html$/);
  });

  test('si el inventario no responde, error con «Reintentar» que reintenta de verdad', async () => {
    const inyecciones = servicios();
    inyecciones.consultar = jest
      .fn()
      .mockRejectedValueOnce(new Error('caído'))
      .mockResolvedValue({ elementos: [], ultima: true, totalPaginas: 1 });
    const configurador = constructorDeEstrategia({ identidad: 'ana', ...inyecciones });
    document.body.replaceChildren(configurador.elemento);
    await configurador.cargar();

    const error = document.querySelector('.estado-vista--error');
    expect(error.textContent).toContain('No pudimos traer tus héroes');
    error.querySelector('button').click();
    await esperar();
    expect(inyecciones.consultar).toHaveBeenCalledTimes(2);
    expect(document.querySelector('.estado-vista--vacio')).not.toBeNull();
  });

  test('heroesConPrototipo junta inventario y catálogo sin inventar el prototipo', async () => {
    const heroes = await heroesConPrototipo(
      [
        { id: 'a', tipo: 'HEROE', nombrePropio: 'Uno', productoId: 'p1' },
        { id: 'b', tipo: 'HEROE', nombrePropio: 'Dos', productoId: null },
        { id: 'c', tipo: 'ARMA', nombrePropio: 'Tres', productoId: 'p3' },
      ],
      async (id) => ({ prototipo: id === 'p1' ? 'Mago Hielo' : '' }),
    );
    expect(heroes).toEqual([
      { id: 'a', nombre: 'Uno', prototipo: 'Mago Hielo', imagen: null },
      { id: 'b', nombre: 'Dos', prototipo: null, imagen: null },
    ]);
  });
});

describe('vista previa', () => {
  test('las cifras, las habilidades del nivel con su costo y la épica afín', async () => {
    await montar();

    const previa = document.querySelector('[data-zona="vista-previa"]');
    expect(previa.querySelector('h3').textContent).toBe(
      'Vista previa: Aquiles (Guerrero Armas) en nivel 1',
    );
    expect(previa.querySelector('.stat-block')).not.toBeNull();
    expect(
      [...previa.querySelectorAll('.estrategia__habilidad-nombre')].map((n) => n.textContent),
    ).toEqual(['Embate sangriento']);
    expect(previa.textContent).toContain('4 puntos de poder');
    expect(previa.textContent).toContain('Épica afín: Furia del titán');
  });

  test('si la vista previa falla, lo dice y el editor sigue: las habilidades ya llegaron', async () => {
    const { vista } = servicios();
    vista.mockRejectedValue(new Error('caído'));
    await montar({ extra: { vista } });

    expect(document.querySelector('[data-zona="vista-previa"]').textContent).toContain(
      'No pudimos traer sus estadísticas',
    );
    expect(document.querySelector('[data-zona="editor"]').hidden).toBe(false);
  });

  test('si las habilidades no llegan, error con «Reintentar» y sin editor', async () => {
    const validar = jest.fn(async () => {
      const fallo = new Error('503');
      fallo.status = 503;
      throw fallo;
    });
    await montar({ validar });

    const error = document.querySelector('[data-zona="vista-previa"] .estado-vista--error');
    expect(error.textContent).toContain('No pudimos leer sus habilidades');
    expect(error.textContent).not.toMatch(/503/);
    expect(document.querySelector('[data-zona="editor"]').hidden).toBe(true);
  });
});

describe('rotaciones', () => {
  test('cada paso ofrece exactamente las habilidades que el servidor dice válidas', async () => {
    await montar();

    const [primero] = pasos();
    expect([...primero.options].map((o) => o.value)).toEqual([
      '',
      'Embate sangriento',
      'Ataque básico',
    ]);
    expect(document.querySelector('.estrategia__por-defecto').textContent).toBe(
      'Si ninguna rotación es viable: Ataque básico, sin gastar poder.',
    );
  });

  test('hasta tres rotaciones, alta, media y baja; al llegar a tres ya no se ofrece otra', async () => {
    await montar();
    const anadir = document.querySelector('[data-accion="anadir-rotacion"]');

    anadir.click();
    anadir.click();

    expect(ROTACIONES_MAXIMAS).toBe(3);
    expect(
      [...document.querySelectorAll('.estrategia__rotacion-titulo')].map((t) => t.textContent),
    ).toEqual([
      'Rotación 1 · prioridad alta',
      'Rotación 2 · prioridad media',
      'Rotación 3 · prioridad baja',
    ]);
    expect(anadir.hidden).toBe(true);
    // El foco va al paso nuevo, no se pierde.
    expect(document.activeElement).toBe(pasos()[2]);
  });

  test('añadir y quitar pasos; el único paso de una rotación no se quita', async () => {
    await montar();

    expect(document.querySelector('[data-accion="quitar-paso"]')).toBeNull();
    document.querySelector('[data-accion="anadir-paso"]').click();
    expect(pasos()).toHaveLength(2);

    document.querySelectorAll('[data-accion="quitar-paso"]')[1].click();
    expect(pasos()).toHaveLength(1);
    expect(document.activeElement).toBe(pasos()[0]);
  });

  test('un paso sin habilidad no se manda: se dice cuál falta y se lleva el foco allí', async () => {
    const { validar } = await montar();
    validar.mockClear();

    await comprobar();

    expect(validar).not.toHaveBeenCalled();
    expect(document.querySelector('[data-zona="veredicto"]').textContent).toContain(
      'Falta elegir el paso 1 de la rotación 1',
    );
    expect(pasos()[0].getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(pasos()[0]);
  });
});

describe('veredicto', () => {
  test('válida: el resumen con prioridades, que todavía no se guarda, y alCambiar con lo que se enviaría', async () => {
    const { validar, configurador, alCambiar } = await montar();
    elegir(pasos()[0], 'Embate sangriento');
    document.querySelector('[data-accion="anadir-paso"]').click();
    elegir(pasos()[1], 'Ataque básico');

    await comprobar();

    expect(validar).toHaveBeenLastCalledWith({
      heroe: 'Guerrero Armas',
      nivel: 1,
      rotaciones: [{ pasos: ['Embate sangriento', 'Ataque básico'] }],
    });
    const veredicto = document.querySelector('[data-zona="veredicto"]');
    expect(veredicto.textContent).toContain(
      'Estrategia válida para Aquiles (Guerrero Armas) en nivel 1',
    );
    expect(veredicto.textContent).toContain('Todavía no se guarda');
    expect(veredicto.textContent).toContain('Prioridad alta: Embate sangriento → Ataque básico');
    expect(configurador.estrategia()).toEqual({
      heroeId: 'h-1',
      heroeNombre: 'Aquiles',
      prototipo: 'Guerrero Armas',
      nivel: 1,
      rotaciones: [{ pasos: ['Embate sangriento', 'Ataque básico'] }],
    });
    expect(alCambiar).toHaveBeenLastCalledWith(configurador.estrategia());
  });

  test('bajar a un nivel que no tiene la habilidad vacía ese paso y lo dice', async () => {
    const { configurador } = await montar();
    // Se prepara en nivel 8 y se vuelve al 1: la habilidad del 8 se vacía.
    const nivel = document.querySelector('select[name="nivel"]');
    elegir(nivel, '8');
    await esperar();
    elegir(pasos()[0], 'Golpe de tormenta');
    elegir(nivel, '1');
    await esperar();

    expect(pasos()[0].value).toBe('');
    expect(document.querySelector('[data-zona="veredicto"]').textContent).toContain(
      'En nivel 1 no tiene Golpe de tormenta',
    );
    expect(configurador.estrategia()).toBeNull();
  });

  test('el servidor tiene la última palabra: su rechazo se enseña con su motivo', async () => {
    const validar = jest.fn(async ({ rotaciones, heroe, nivel = 1 }) =>
      rotaciones?.length
        ? {
            valida: false,
            motivo: 'La rotación 1 no tiene pasos.',
            heroe,
            nivel,
            habilidadesValidas: ['Embate sangriento', 'Ataque básico'],
            porDefecto: false,
            comportamientoPorDefecto: 'Ataque básico',
          }
        : {
            valida: true,
            heroe,
            nivel,
            habilidadesValidas: ['Embate sangriento', 'Ataque básico'],
            porDefecto: true,
            comportamientoPorDefecto: 'Ataque básico',
          },
    );
    const { configurador } = await montar({ validar });
    elegir(pasos()[0], 'Embate sangriento');

    await comprobar();

    const veredicto = document.querySelector('[data-zona="veredicto"]');
    expect(veredicto.querySelector('.aviso--error').textContent).toContain(
      'La rotación 1 no tiene pasos.',
    );
    expect(veredicto.textContent).toContain(
      'Habilidades válidas en nivel 1: Embate sangriento, Ataque básico.',
    );
    expect(configurador.estrategia()).toBeNull();
  });

  test('cambiar algo después de comprobar deja el veredicto viejo y lo dice', async () => {
    const { configurador } = await montar();
    elegir(pasos()[0], 'Embate sangriento');
    await comprobar();
    expect(configurador.estrategia()).not.toBeNull();

    elegir(pasos()[0], 'Ataque básico');

    expect(configurador.estrategia()).toBeNull();
    expect(document.querySelector('[data-zona="veredicto"]').textContent).toContain(
      'Cambiaste la estrategia',
    );
  });

  test('en la matrícula, el veredicto dice que viaja con el héroe', async () => {
    await montar({ extra: { modo: 'matricula' } });
    elegir(pasos()[0], 'Embate sangriento');
    await comprobar();

    expect(document.querySelector('[data-zona="veredicto"]').textContent).toContain(
      'Se enviará con tu héroe al iniciar la misión.',
    );
  });

  test('los fallos se dicen en el idioma del jugador, nunca con un código', () => {
    expect(mensajeDeFallo({ status: 401 }, 'comprobar la estrategia')).toBe(
      'Tu sesión ya no es válida. Vuelve a iniciar sesión para comprobar la estrategia.',
    );
    expect(mensajeDeFallo({ status: 404, detalle: 'El héroe no está en el catálogo.' }, 'x')).toBe(
      'El héroe no está en el catálogo.',
    );
    expect(mensajeDeFallo({ status: 502 }, 'leer sus habilidades')).toBe(
      'No pudimos leer sus habilidades. Revisa tu conexión e inténtalo de nuevo.',
    );
  });
});
