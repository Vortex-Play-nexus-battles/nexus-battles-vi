/**
 * Revisión del modo jugador del 6-oct, punto 25 (PLAYER-07a): «Mis héroes» y
 * «Héroes del Nexo» (posesión y catálogo, separados), la búsqueda general y el
 * banner de misiones (RF-INV-003) al final, con su título.
 */
import { jest } from '@jest/globals';

import { listarProductos } from './cliente-productos.js';
import { cartaDelNexo, hrefDeLaTiendaPara, pintarHeroesDelNexo } from './heroes-del-nexo.js';
import { montarInventario } from './inventario.js';

const asentar = () => new Promise((resolver) => setTimeout(resolver, 0));

async function esperarHasta(condicion) {
  for (let intento = 0; intento < 30; intento += 1) {
    if (condicion()) {
      return;
    }
    await asentar();
  }
  throw new Error('La interfaz no terminó la operación esperada');
}

function pagina(elementos = []) {
  return {
    elementos,
    numero: 0,
    tamanio: 16,
    totalElementos: elementos.length,
    totalPaginas: elementos.length === 0 ? 0 : 1,
    ultima: true,
  };
}

const HEROE_PROPIO = {
  id: 'he-1',
  productoId: 'prod-tanque',
  tipo: 'HEROE',
  nombrePropio: 'Aquiles',
  disponible: true,
};
const ESPADA = { id: 'ob-1', productoId: 'prod-espada', tipo: 'ARMA', nombrePropio: 'Espada' };

const CATALOGO = [
  {
    id: 'prod-tanque',
    nombre: 'Guerrero Tanque',
    tipo: 'HEROE',
    prototipo: 'Guerrero Tanque',
    descripcion: 'Aguanta lo que le echen.',
    imagen: null,
  },
  {
    id: 'prod-fuego',
    nombre: 'Maga del Fuego',
    tipo: 'HEROE',
    prototipo: 'Mago Fuego',
    descripcion: 'Quema el campo.',
    imagen: null,
  },
  // Lo que no es un héroe no entra, aunque el catálogo lo mande.
  { id: 'prod-espada', nombre: 'Espada', tipo: 'ARMA' },
];

/** Las dependencias de `montarInventario` que esta vista necesita, sin red. */
function dependencias(extra = {}) {
  return {
    consultar: async () => pagina([HEROE_PROPIO, ESPADA]),
    buscar: async () => pagina([HEROE_PROPIO]),
    consultarEquipo: async (_, heroeId) => ({ heroeId, armas: [], armaduras: {}, items: [] }),
    consultarEstadisticas: async () => ({}),
    consultarProducto: async (id) => CATALOGO.find((p) => p.id === id) ?? { id, nombre: id },
    listarHeroesDelNexo: async () => ({ productos: CATALOGO, total: CATALOGO.length }),
    fuenteMisiones: Promise.resolve({ disponible: false }),
    rol: 'JUGADOR',
    ...extra,
  };
}

let raiz;
beforeEach(() => {
  raiz = document.createElement('main');
  document.body.replaceChildren(raiz);
});

describe('el catálogo de productos por tipo', () => {
  test('pide la página pública filtrada por tipo y lee content y totalElements', async () => {
    const fetchImpl = jest.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ content: CATALOGO, totalElements: 3, page: 0, size: 50 }),
    }));
    const { productos, total } = await listarProductos({ tipo: 'HEROE', fetchImpl });
    const url = new URL(fetchImpl.mock.calls[0][0], 'http://x');
    expect(url.pathname).toBe('/api/v1/productos');
    expect(url.searchParams.get('tipo')).toBe('HEROE');
    expect(url.searchParams.get('size')).toBe('50');
    expect(productos).toHaveLength(3);
    expect(total).toBe(3);
  });

  test('un fallo del catálogo se propaga para que la vista lo diga', async () => {
    const fetchImpl = async () => ({ ok: false, status: 503, json: async () => null });
    await expect(listarProductos({ tipo: 'HEROE', fetchImpl })).rejects.toThrow(/503/);
  });
});

describe('Héroes del Nexo', () => {
  test('la carta dice si ya es tuyo; si no, lleva a buscarlo en la tienda', () => {
    const tuyo = cartaDelNexo(CATALOGO[0], { tuyo: true });
    expect(tuyo.dataset.tuyo).toBe('true');
    expect(tuyo.dataset.prototipo).toBe('guerrero-tanque');
    expect(tuyo.querySelector('[data-zona="ya-es-tuyo"]').textContent).toBe('Ya es tuyo');
    expect(tuyo.querySelector('a')).toBeNull();
    // Se llama como su prototipo: debajo, solo la familia (no se repite).
    expect(tuyo.querySelector('.nexo-heroe__prototipo').textContent).toBe('Guerrero');

    const ajeno = cartaDelNexo(CATALOGO[1], { tuyo: false });
    expect(ajeno.querySelector('.nexo-heroe__prototipo').textContent).toBe('Mago Fuego · Mago');
    const enlace = ajeno.querySelector('[data-accion="conseguir-heroe"]');
    expect(enlace.getAttribute('href')).toBe(
      '../../cuentas/tienda.html?busqueda=Maga%20del%20Fuego',
    );
    expect(enlace.getAttribute('aria-label')).toBe('Conseguir en la tienda: Maga del Fuego');
    expect(ajeno.querySelector('[data-zona="ya-es-tuyo"]')).toBeNull();
  });

  test('pinta solo los héroes del catálogo y cuenta cuántos son tuyos', async () => {
    const contenedor = document.createElement('div');
    const resultado = await pintarHeroesDelNexo(contenedor, {
      listar: async () => ({ productos: CATALOGO }),
      propios: new Set(['prod-tanque']),
    });
    expect(resultado).toEqual({ estado: 'ok', total: 2, tuyos: 1 });
    expect(contenedor.querySelectorAll('.nexo-heroe')).toHaveLength(2);
    expect(contenedor.querySelector('[data-zona="cuenta-nexo"]').textContent).toBe(
      '2 héroes en el Nexo · 1 es tuyo',
    );
  });

  test('si el catálogo no responde lo dice, y «Reintentar» vuelve a pedirlo', async () => {
    const contenedor = document.createElement('div');
    const avisos = jest.spyOn(console, 'warn').mockImplementation(() => {});
    let intentos = 0;
    const listar = async () => {
      intentos += 1;
      if (intentos === 1) {
        throw new Error('caído');
      }
      return { productos: CATALOGO };
    };
    expect((await pintarHeroesDelNexo(contenedor, { listar })).estado).toBe('error');
    expect(contenedor.textContent).toContain('No podemos enseñar los héroes del Nexo ahora');
    expect(contenedor.textContent).not.toMatch(/\b503\b|Error/);
    contenedor.querySelector('button').click();
    await esperarHasta(() => contenedor.querySelectorAll('.nexo-heroe').length === 2);
    expect(intentos).toBe(2);
    avisos.mockRestore();
  });

  test('sin héroes en el catálogo, un estado vacío sin acción inventada', async () => {
    const contenedor = document.createElement('div');
    const resultado = await pintarHeroesDelNexo(contenedor, {
      listar: async () => ({ productos: [] }),
    });
    expect(resultado.estado).toBe('vacio');
    expect(contenedor.querySelector('a')).toBeNull();
  });

  test('la dirección de la tienda busca el nombre, codificado', () => {
    expect(hrefDeLaTiendaPara('Pícaro & Co')).toBe(
      '../../cuentas/tienda.html?busqueda=P%C3%ADcaro%20%26%20Co',
    );
    expect(hrefDeLaTiendaPara('')).toBe('../../cuentas/tienda.html');
  });
});

describe('Mi inventario por dentro', () => {
  test('«Mis héroes» y «Héroes del Nexo» en la pestaña de héroes; el tuyo, marcado', async () => {
    await montarInventario(raiz, 'jugador-A', 0, dependencias());
    const titulos = [...raiz.querySelectorAll('.inventario__panel--heroes h2')].map(
      (titulo) => titulo.textContent,
    );
    expect(titulos).toEqual(['Mis héroes', 'Héroes del Nexo']);
    await esperarHasta(() => raiz.querySelectorAll('.nexo-heroe').length === 2);
    expect(raiz.querySelector('.nexo-heroe[data-producto="prod-tanque"]').dataset.tuyo).toBe(
      'true',
    );
    expect(
      raiz.querySelector('.nexo-heroe[data-producto="prod-fuego"] [data-accion="conseguir-heroe"]'),
    ).not.toBeNull();
  });

  test('la búsqueda va arriba, sobre las pestañas, y sus resultados en «Objetos»', async () => {
    await montarInventario(raiz, 'jugador-A', 0, dependencias());
    const busqueda = raiz.querySelector('.inventario-busqueda');
    const pestanas = raiz.querySelector('.inventario__pestanas');
    // Delante de las pestañas, no dentro de un panel.
    expect(busqueda.closest('.inventario__panel')).toBeNull();
    expect(
      busqueda.compareDocumentPosition(pestanas) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(busqueda.querySelector('label').textContent).toMatch(/buscar en tu inventario/i);
    const campo = busqueda.querySelector('[name="criterio"]');
    expect(raiz.querySelector(`#${campo.getAttribute('aria-describedby')}`).textContent).toMatch(
      /4 letras/,
    );

    campo.value = 'aquiles';
    busqueda.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await esperarHasta(
      () => raiz.querySelectorAll('.inventario__contenido .vitrina__producto').length === 1,
    );
    expect(raiz.querySelector('.inventario__panel--objetos').hidden).toBe(false);
    expect(raiz.querySelector('.inventario__panel--heroes').hidden).toBe(true);
    expect(
      raiz.querySelector('.inventario__panel--objetos .inventario__intro').textContent,
    ).toMatch(/todo el inventario: héroes y objetos/);

    raiz.querySelector('.inventario-busqueda__limpiar').click();
    await esperarHasta(() => !raiz.querySelector('.inventario-busqueda--activa'));
    expect(
      raiz.querySelector('.inventario__panel--objetos .inventario__intro').textContent,
    ).toMatch(/^Armas, armaduras/);
  });

  test('sin módulo de misiones, ni el banner ni su título (RF-INV-003)', async () => {
    await montarInventario(raiz, 'jugador-A', 0, dependencias());
    await asentar();
    await asentar();
    expect(raiz.querySelector('.inventario__misiones').hidden).toBe(true);
  });

  test('con misiones, el banner va al final, con su título, y no arriba', async () => {
    const fuente = {
      disponible: true,
      destacadas: async () => [
        {
          id: 'm-1',
          nombre: 'El Templo Olvidado',
          categoria: 'HISTORIA',
          dificultad: 'NORMAL',
          duracionHoras: 12,
          descripcionBreve: 'Un templo antiguo.',
        },
      ],
    };
    await montarInventario(raiz, 'jugador-A', 0, dependencias({ fuenteMisiones: fuente }));
    await esperarHasta(() => !raiz.querySelector('.inventario__misiones').hidden);
    const seccion = raiz.querySelector('.inventario__misiones');
    expect(seccion.querySelector('h2').textContent).toBe('Misiones para tus héroes');
    expect(seccion.querySelector('.banner-misiones__diapositiva')).not.toBeNull();
    // Lo último de la vista, después de los tres paneles.
    expect(raiz.lastElementChild).toBe(seccion);
    expect(raiz.firstElementChild.classList.contains('inventario-cabecera')).toBe(true);
  });
});
