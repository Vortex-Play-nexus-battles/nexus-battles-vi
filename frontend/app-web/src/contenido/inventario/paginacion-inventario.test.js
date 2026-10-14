/**
 * HU-INV-011 - La paginacion montada dentro de la vista de inventario.
 *
 * El control en si se prueba en `src/comun/paginacion.test.js`. Aqui se
 * verifica lo que solo se ve al integrarlo: que cambiar de pagina conserva
 * los criterios (criterio 3: «esos criterios se conservan») y que el control
 * refleja el total de lo que se esta paginando.
 *
 * Revisión final del módulo (13-oct) — §7.1: cada pestaña pide al servidor SOLO
 * la página que enseña (`categoria=HEROES|OBJETOS`, inventario.yaml 1.7.0); ya
 * no se reúne el inventario entero al montar. Con una página aparece la casilla
 * «1». Y si el servidor ignora la categoría (uno anterior a 1.7.0) se vuelve al
 * camino de antes: reunir y paginar aquí.
 */
import { montarInventario } from './inventario.js';

function elemento(i, tipo = 'ARMA') {
  return tipo === 'HEROE'
    ? { id: `h${i}`, productoId: `ph${i}`, tipo, nombrePropio: `Heroe ${i}` }
    : { id: `e${i}`, productoId: `p${i}`, tipo, nombrePropio: `Espada ${i}` };
}

/** Respuesta del servicio con la forma de SCRUM-318. */
function paginaDe(numero, totalElementos, tamanio = 16, tipo = 'ARMA') {
  const totalPaginas = Math.ceil(totalElementos / tamanio);
  const desde = numero * tamanio;
  const cuantos = Math.max(0, Math.min(tamanio, totalElementos - desde));
  return {
    elementos: Array.from({ length: cuantos }, (_, i) => elemento(desde + i, tipo)),
    numero,
    tamanio,
    totalElementos,
    totalPaginas,
    ultima: totalPaginas === 0 || numero >= totalPaginas - 1,
  };
}

/** Un servidor 1.7.0: pagina cada categoria por separado y anota lo pedido. */
function servidor({ objetos = 0, heroes = 0 } = {}) {
  const pedidas = [];
  const consultar = async (identidad, numero, { categoria } = {}) => {
    pedidas.push({ identidad, numero, categoria });
    return categoria === 'HEROES'
      ? paginaDe(numero, heroes, 16, 'HEROE')
      : paginaDe(numero, objetos);
  };
  return { consultar, pedidas };
}

function panelObjetos(raiz) {
  return raiz.querySelector('.inventario__panel--objetos');
}

function casillas(raiz) {
  return [...panelObjetos(raiz).querySelectorAll('.paginacion__pagina:not([data-direccion])')];
}

function control(raiz) {
  return panelObjetos(raiz).querySelector('.paginacion');
}

/** Dobles inertes: esta prueba no ejercita creacion, edicion ni equipamiento. */
const SIN_ESCRITURA = {
  crear: async () => {},
  modificar: async () => {},
  consultarEquipo: async () => ({ armas: [], armaduras: {}, items: [] }),
  consultarEstadisticas: async () => null,
  consultarProducto: async () => ({ tipo: 'HEROE', prototipo: 'Guerrero Tanque' }),
  equipar: async () => {},
  desequipar: async () => {},
  listarHeroesDelNexo: async () => ({ productos: [] }),
  fuenteMisiones: Promise.resolve({ disponible: false }),
};

let raiz;
beforeEach(() => {
  raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
});

describe('Paginacion integrada en la vista de inventario', () => {
  test('§7.1: con una sola página aparece la casilla 1', async () => {
    const { consultar } = servidor({ objetos: 10 });
    await montarInventario(raiz, 'jugador-A', 0, { ...SIN_ESCRITURA, consultar });

    expect(control(raiz).hidden).toBe(false);
    expect(casillas(raiz).map((c) => c.textContent)).toEqual(['1']);
  });

  test('con varias paginas el control muestra una casilla por pagina', async () => {
    const { consultar } = servidor({ objetos: 80 });
    await montarInventario(raiz, 'jugador-A', 0, { ...SIN_ESCRITURA, consultar });

    // 80 elementos a 16 por pagina son 5 paginas.
    expect(casillas(raiz).map((c) => c.textContent)).toEqual(['1', '2', '3', '4', '5']);
    expect(control(raiz).hidden).toBe(false);
  });

  test('cada pestaña pide al servidor solo su página, con la identidad del jugador', async () => {
    const { consultar, pedidas } = servidor({ objetos: 80, heroes: 3 });
    await montarInventario(raiz, 'jugador-A', 0, { ...SIN_ESCRITURA, consultar });

    // Al montar: la primera página de héroes y la primera de objetos. Nada más.
    expect(pedidas).toEqual([
      { identidad: 'jugador-A', numero: 0, categoria: 'HEROES' },
      { identidad: 'jugador-A', numero: 0, categoria: 'OBJETOS' },
    ]);

    casillas(raiz)[3].click();
    await new Promise((r) => setTimeout(r, 0));

    expect(pedidas.at(-1)).toEqual({ identidad: 'jugador-A', numero: 3, categoria: 'OBJETOS' });
    expect(pedidas).toHaveLength(3);
    expect(control(raiz).querySelector('[aria-current="page"]').textContent).toBe('4');
  });

  test('pulsar una casilla muestra esa pagina de objetos', async () => {
    const { consultar } = servidor({ objetos: 80 });
    await montarInventario(raiz, 'jugador-A', 0, { ...SIN_ESCRITURA, consultar });

    casillas(raiz)[2].click();
    await new Promise((r) => setTimeout(r, 0));

    expect(panelObjetos(raiz).querySelectorAll('.vitrina__producto')).toHaveLength(16);
    expect(panelObjetos(raiz).querySelector('.vitrina__nombre').textContent).toBe('Espada 32');
  });

  test('«Mis héroes» también pagina de dieciséis en dieciséis con su propio control', async () => {
    const { consultar, pedidas } = servidor({ objetos: 0, heroes: 20 });
    await montarInventario(raiz, 'jugador-A', 0, { ...SIN_ESCRITURA, consultar });

    const zona = raiz.querySelector('.inventario__paginacion--heroes');
    const numeros = [...zona.querySelectorAll('.paginacion__pagina:not([data-direccion])')].map(
      (c) => c.textContent,
    );
    expect(numeros).toEqual(['1', '2']);
    expect(raiz.querySelectorAll('.inventario-heroes .hero-card')).toHaveLength(16);

    zona.querySelectorAll('.paginacion__pagina')[1].click();
    for (let i = 0; i < 4; i += 1) {
      await new Promise((r) => setTimeout(r, 0));
    }

    expect(raiz.querySelectorAll('.inventario-heroes .hero-card')).toHaveLength(4);
    expect(pedidas.filter((p) => p.categoria === 'HEROES').map((p) => p.numero)).toContain(1);
  });

  test('«Mis héroes»: una página que llega tarde no tapa a la que se pidió después', async () => {
    const { consultar } = servidor({ objetos: 0, heroes: 40 });
    // El equipo de los héroes de la página 2 (h16…h31) tarda hasta que la
    // prueba lo suelte; los demás llegan al momento.
    let soltar;
    const retenido = new Promise((resolver) => {
      soltar = resolver;
    });
    const consultarEquipo = async (_id, heroeId) => {
      const n = Number(heroeId.slice(1));
      if (n >= 16 && n < 32) {
        await retenido;
      }
      return { armas: [], armaduras: {}, items: [] };
    };
    await montarInventario(raiz, 'jugador-A', 0, { ...SIN_ESCRITURA, consultar, consultarEquipo });
    const zona = raiz.querySelector('.inventario__paginacion--heroes');
    const casilla = (texto) =>
      [...zona.querySelectorAll('.paginacion__pagina:not([data-direccion])')].find(
        (c) => c.textContent === texto,
      );
    const esperarVuelta = async () => {
      for (let i = 0; i < 6; i += 1) {
        await new Promise((r) => setTimeout(r, 0));
      }
    };

    casilla('2').click();
    await esperarVuelta();
    casilla('3').click();
    await esperarVuelta();
    expect(raiz.querySelectorAll('.inventario-heroes .hero-card')).toHaveLength(8);

    soltar();
    await esperarVuelta();

    // Sigue la 3: sus ocho cartas y su casilla marcada.
    const cartas = [...raiz.querySelectorAll('.inventario-heroes [data-heroe]')];
    expect(cartas).toHaveLength(8);
    expect(cartas[0].textContent).toContain('Heroe 32');
    expect(zona.querySelector('[aria-current="page"]').textContent).toBe('3');
  });

  test('compatibilidad: si el servidor ignora la categoría se reúne y se pagina aquí', async () => {
    const pedidas = [];
    const conHeroe = (numero) => {
      const pagina = paginaDe(numero, 20);
      if (numero === 0) {
        pagina.elementos[0] = elemento(1, 'HEROE');
      }
      return pagina;
    };
    await montarInventario(raiz, 'jugador-A', 0, {
      ...SIN_ESCRITURA,
      consultar: async (_id, numero) => {
        pedidas.push(numero);
        return conHeroe(numero);
      },
    });

    const objetos = raiz.querySelector('.inventario__contenido');
    // 20 elementos, uno es heroe: 19 objetos, 16 en la primera pagina.
    expect(objetos.querySelectorAll('.vitrina__producto')).toHaveLength(16);
    expect(objetos.querySelector('[data-tipo="HEROE"]')).toBeNull();
    expect(casillas(raiz).map((c) => c.textContent)).toEqual(['1', '2']);
    // Y el heroe esta en su pestana.
    expect(raiz.querySelector('.inventario-heroes [data-heroe]')).not.toBeNull();
    // La página 0 que delató al servidor se aprovecha: cada página se pide una
    // sola vez (0 y 1), no la 0 dos veces.
    expect(pedidas).toEqual([0, 1]);
  });

  test('compatibilidad con un inventario enorme: «Objetos» pagina contra el servidor y no deja nada fuera', async () => {
    const pedidas = [];
    const mezclada = (numero) => {
      const pagina = paginaDe(numero, 48);
      pagina.elementos[0] = elemento(numero * 16, 'HEROE');
      return pagina;
    };
    await montarInventario(raiz, 'jugador-A', 0, {
      ...SIN_ESCRITURA,
      consultar: async (_id, numero) => {
        pedidas.push(numero);
        return mezclada(numero);
      },
      // Tope de una página: la colección no se puede reunir entera.
      maxPaginas: 1,
    });

    // Las tres páginas del servidor, contadas por el servidor.
    expect(casillas(raiz).map((c) => c.textContent)).toEqual(['1', '2', '3']);
    expect(raiz.querySelector('.inventario-heroes .inventario__aviso').textContent).toMatch(
      /muy grande/,
    );

    casillas(raiz)[2].click();
    await new Promise((r) => setTimeout(r, 0));

    expect(pedidas.at(-1)).toBe(2);
    // La página tal cual la da el servidor: también trae a sus héroes (el aviso
    // de «Mis héroes» lo dice), pero nada se queda fuera.
    const nombres = [...panelObjetos(raiz).querySelectorAll('.vitrina__nombre')].map(
      (n) => n.textContent,
    );
    expect(nombres).toHaveLength(16);
    expect(nombres.slice(0, 2)).toEqual(['Heroe 32', 'Espada 33']);
  });

  test('RF-INV-002: una página que no existe lleva a la última válida', async () => {
    const { consultar, pedidas } = servidor({ objetos: 20 });
    await montarInventario(raiz, 'jugador-A', 5, { ...SIN_ESCRITURA, consultar });

    // 20 objetos son dos páginas: la 6 no existe y se enseña la 2, sin pasar
    // por el «Todavía no tienes objetos».
    expect(panelObjetos(raiz).querySelectorAll('.vitrina__producto')).toHaveLength(4);
    expect(control(raiz).querySelector('[aria-current="page"]').textContent).toBe('2');
    expect(panelObjetos(raiz).textContent).not.toMatch(/Todavía no tienes objetos/);
    expect(pedidas.filter((p) => p.categoria === 'OBJETOS').map((p) => p.numero)).toEqual([5, 1]);
  });

  test('la casilla activa sigue a la pagina que se esta mostrando', async () => {
    const { consultar } = servidor({ objetos: 80 });
    await montarInventario(raiz, 'jugador-A', 0, { ...SIN_ESCRITURA, consultar });

    expect(control(raiz).querySelector('[aria-current="page"]').textContent).toBe('1');

    casillas(raiz)[3].click();
    await new Promise((r) => setTimeout(r, 0));

    expect(control(raiz).querySelector('[aria-current="page"]').textContent).toBe('4');
  });

  test('un inventario vacio no pinta control de paginacion', async () => {
    const { consultar } = servidor({ objetos: 0 });
    await montarInventario(raiz, 'jugador-A', 0, { ...SIN_ESCRITURA, consultar });

    expect(control(raiz).hidden).toBe(true);
  });

  test('tras cambiar de pagina con el teclado el foco sigue en el control', async () => {
    const { consultar } = servidor({ objetos: 80 });
    await montarInventario(raiz, 'jugador-A', 0, { ...SIN_ESCRITURA, consultar });

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

  test('§7.1: la flecha derecha revela las páginas ocultas y conserva el foco', async () => {
    const { consultar } = servidor({ objetos: 16 * 25 });
    await montarInventario(raiz, 'jugador-A', 0, { ...SIN_ESCRITURA, consultar });

    const derecha = control(raiz).querySelector('[data-direccion="siguiente"]');
    expect(control(raiz).querySelector('[data-direccion="anterior"]')).toBeNull();
    derecha.focus();
    derecha.click();
    await new Promise((r) => setTimeout(r, 0));

    const numeros = casillas(raiz).map((c) => c.textContent);
    expect(numeros).toContain('11');
    expect(control(raiz).querySelector('[aria-current="page"]').textContent).toBe('11');
    expect(document.activeElement.dataset.direccion).toBe('siguiente');
  });

  /** Monta con una busqueda activa: la paginacion va al indice del servicio. */
  async function conBusqueda(buscar) {
    const { consultar } = servidor({ objetos: 16 });
    await montarInventario(raiz, 'jugador-A', 0, { ...SIN_ESCRITURA, consultar, buscar });
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
    expect(panelObjetos(raiz).querySelector('.vitrina__nombre').textContent).toBe('Espada 80');
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
