/**
 * HU-INV-011 - La paginacion montada dentro de la vista de inventario.
 *
 * El control en si se prueba en `src/comun/paginacion.test.js`. Aqui se
 * verifica lo que solo se ve al integrarlo: que cambiar de pagina conserva
 * los criterios (criterio 3: «esos criterios se conservan») y que el control
 * refleja el total de lo que se esta paginando.
 *
 * UXC-1 — desde que «Mi inventario» separa heroes y objetos, la vista reune el
 * inventario al montarse (`coleccion-inventario.js`) y la pestana «Objetos»
 * pagina esa coleccion de dieciseis en dieciseis. Cambiar de pagina ya no
 * vuelve al servicio sin busqueda: el criterio que se conserva es «solo
 * objetos». Con una busqueda activa si se consulta el indice del servicio por
 * pagina, y ahi viven las pruebas de carrera y de fallo que antes estaban
 * aqui (el orden de llegada solo importa cuando hay red por medio).
 */
import { montarInventario } from './inventario.js';

function elemento(i) {
  return { id: `e${i}`, productoId: `p${i}`, tipo: 'ARMA', nombrePropio: `Espada ${i}` };
}

/** Respuesta del servicio con la forma de SCRUM-318. */
function paginaDe(numero, totalElementos, tamanio = 16) {
  const totalPaginas = Math.ceil(totalElementos / tamanio);
  const desde = numero * tamanio;
  const cuantos = Math.max(0, Math.min(tamanio, totalElementos - desde));
  return {
    elementos: Array.from({ length: cuantos }, (_, i) => elemento(desde + i)),
    numero,
    tamanio,
    totalElementos,
    totalPaginas,
    ultima: numero >= totalPaginas - 1,
  };
}

function casillas(raiz) {
  return [...raiz.querySelectorAll('.paginacion__pagina:not([data-direccion])')];
}

function control(raiz) {
  return raiz.querySelector('.paginacion');
}

/** Dobles inertes: esta prueba no ejercita creacion, edicion ni equipamiento. */
const SIN_ESCRITURA = {
  crear: async () => {},
  modificar: async () => {},
  consultarEquipo: async () => ({ armas: [], armaduras: {}, items: [] }),
  equipar: async () => {},
  desequipar: async () => {},
};

let raiz;
beforeEach(() => {
  raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
});

describe('Paginacion integrada en la vista de inventario', () => {
  test('con un inventario de una sola pagina el control queda oculto', async () => {
    await montarInventario(raiz, 'jugador-A', 0, {
      ...SIN_ESCRITURA,
      consultar: async (_id, numero) => paginaDe(numero, 10),
    });

    expect(control(raiz).hidden).toBe(true);
  });

  test('con varias paginas el control muestra una casilla por pagina', async () => {
    await montarInventario(raiz, 'jugador-A', 0, {
      ...SIN_ESCRITURA,
      consultar: async (_id, numero) => paginaDe(numero, 80),
    });

    // 80 elementos a 16 por pagina son 5 paginas.
    expect(casillas(raiz).map((c) => c.textContent)).toEqual(['1', '2', '3', '4', '5']);
    expect(control(raiz).hidden).toBe(false);
  });

  test('pulsar una casilla muestra esa pagina de objetos', async () => {
    const pedidas = [];
    await montarInventario(raiz, 'jugador-A', 0, {
      ...SIN_ESCRITURA,
      consultar: async (_id, numero) => {
        pedidas.push(numero);
        return paginaDe(numero, 80);
      },
    });
    // Al montar se reunen las cinco paginas, una vez.
    expect(pedidas).toEqual([0, 1, 2, 3, 4]);

    casillas(raiz)[2].click();
    await new Promise((r) => setTimeout(r, 0));

    expect(raiz.querySelectorAll('.vitrina__producto')).toHaveLength(16);
    expect(raiz.querySelector('.vitrina__nombre').textContent).toBe('Espada 32');
    // Sin busqueda no hace falta volver al servicio: ya estaba todo.
    expect(pedidas).toEqual([0, 1, 2, 3, 4]);
  });

  test('reune el inventario con la identidad del jugador, pagina a pagina', async () => {
    const consultas = [];
    await montarInventario(raiz, 'jugador-A', 0, {
      ...SIN_ESCRITURA,
      consultar: async (identidad, numero) => {
        consultas.push({ identidad, numero });
        return paginaDe(numero, 80);
      },
    });

    casillas(raiz)[3].click();
    await new Promise((r) => setTimeout(r, 0));

    expect(consultas).toEqual(
      [0, 1, 2, 3, 4].map((numero) => ({ identidad: 'jugador-A', numero })),
    );
    expect(control(raiz).querySelector('[aria-current="page"]').textContent).toBe('4');
  });

  test('los heroes no ocupan casillas de la vitrina de objetos', async () => {
    const conHeroe = (numero) => {
      const pagina = paginaDe(numero, 20);
      if (numero === 0) {
        pagina.elementos[0] = { id: 'h1', productoId: 'ph', tipo: 'HEROE', nombrePropio: 'Ayla' };
      }
      return pagina;
    };
    await montarInventario(raiz, 'jugador-A', 0, {
      ...SIN_ESCRITURA,
      consultar: async (_id, numero) => conHeroe(numero),
    });

    const objetos = raiz.querySelector('.inventario__contenido');
    // 20 elementos, uno es heroe: 19 objetos, 16 en la primera pagina.
    expect(objetos.querySelectorAll('.vitrina__producto')).toHaveLength(16);
    expect(objetos.querySelector('[data-tipo="HEROE"]')).toBeNull();
    expect(casillas(raiz).map((c) => c.textContent)).toEqual(['1', '2']);
    // Y el heroe esta en su pestana.
    expect(raiz.querySelector('.inventario-heroes [data-heroe]')).not.toBeNull();
  });

  test('la casilla activa sigue a la pagina que se esta mostrando', async () => {
    await montarInventario(raiz, 'jugador-A', 0, {
      ...SIN_ESCRITURA,
      consultar: async (_id, numero) => paginaDe(numero, 80),
    });

    expect(control(raiz).querySelector('[aria-current="page"]').textContent).toBe('1');

    casillas(raiz)[3].click();
    await new Promise((r) => setTimeout(r, 0));

    expect(control(raiz).querySelector('[aria-current="page"]').textContent).toBe('4');
  });

  test('un inventario vacio no pinta control de paginacion', async () => {
    await montarInventario(raiz, 'jugador-A', 0, {
      ...SIN_ESCRITURA,
      consultar: async (_id, numero) => paginaDe(numero, 0),
    });

    expect(control(raiz).hidden).toBe(true);
  });

  test('tras cambiar de pagina con el teclado el foco sigue en el control', async () => {
    await montarInventario(raiz, 'jugador-A', 0, {
      ...SIN_ESCRITURA,
      consultar: async (_id, numero) => paginaDe(numero, 80),
    });

    // El jugador llega a la casilla 4 con el tabulador y la activa.
    const cuarta = casillas(raiz)[3];
    cuarta.focus();
    cuarta.click();
    await new Promise((r) => setTimeout(r, 0));

    // RNF-ACC-002: el control se repinta entero, pero el foco no puede caer
    // al body — el jugador perderia su sitio y tendria que tabular de nuevo
    // desde el principio de la pagina.
    expect(control(raiz).contains(document.activeElement)).toBe(true);
    expect(document.activeElement.getAttribute('aria-current')).toBe('page');
    expect(document.activeElement.textContent).toBe('4');
  });

  /** Monta con una busqueda activa: la unica paginacion que va a la red. */
  async function conBusqueda(buscar) {
    await montarInventario(raiz, 'jugador-A', 0, {
      ...SIN_ESCRITURA,
      consultar: async (_id, numero) => paginaDe(numero, 16),
      buscar,
    });
    raiz.querySelector('.inventario-busqueda__control').value = 'Espada';
    raiz
      .querySelector('.inventario-busqueda')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    for (let i = 0; i < 5; i += 1) {
      await new Promise((r) => setTimeout(r, 0));
    }
  }

  test('dos clics seguidos no dejan la vitrina en la pagina equivocada', async () => {
    // El servicio responde fuera de orden: la pagina 2 tarda mas que la 6.
    // Sin proteccion, la respuesta lenta llega la ultima y pisa a la rapida.
    const enEspera = new Map();
    await conBusqueda((_id, _criterio, numero) =>
      numero === 0
        ? Promise.resolve(paginaDe(0, 160))
        : new Promise((resolver) => enEspera.set(numero, resolver)),
    );

    casillas(raiz)[1].click();
    casillas(raiz)[5].click();

    // Llegan al reves de como se pidieron.
    enEspera.get(5)?.(paginaDe(5, 160));
    await new Promise((r) => setTimeout(r, 0));
    enEspera.get(1)?.(paginaDe(1, 160));
    await new Promise((r) => setTimeout(r, 0));

    // Lo ultimo que pidio el jugador fue la pagina 6, y eso es lo que ve.
    expect(control(raiz).querySelector('[aria-current="page"]').textContent).toBe('6');
    expect(raiz.querySelector('.vitrina__nombre').textContent).toBe('Espada 80');
  });

  test('si la consulta falla el control no queda con la pagina equivocada', async () => {
    let fallar = false;
    await conBusqueda(async (_id, _criterio, numero) => {
      if (fallar) {
        throw new Error('servicio caido');
      }
      return paginaDe(numero, 80);
    });

    fallar = true;
    casillas(raiz)[3].click();
    await new Promise((r) => setTimeout(r, 0));

    // La consulta fallo: se sigue anunciando la pagina que de verdad se ve.
    expect(control(raiz).querySelector('[aria-current="page"]').textContent).toBe('1');
  });
});
