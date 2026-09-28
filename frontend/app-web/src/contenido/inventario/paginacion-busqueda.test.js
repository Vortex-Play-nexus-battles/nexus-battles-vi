/**
 * HU-INV-011, criterio 3, con la busqueda de HU-INV-002 ya integrada.
 *
 * "Dado un jugador con filtros o texto de busqueda aplicados, cuando cambia de
 * pagina, entonces esos criterios se conservan."
 *
 * Mientras la busqueda no existia solo se podia verificar que cambiar de
 * pagina no tocaba el resto del estado. Con HU-INV-002 fusionada, aqui se
 * verifica el criterio completo: la pagina siguiente se pide a la busqueda,
 * con el mismo criterio, y no al inventario completo.
 */
import { montarInventario } from './inventario.js';

const TAMANIO = 16;

function elemento(i, prefijo) {
  return {
    id: `${prefijo}-${i}`,
    productoId: `p${i}`,
    tipo: 'ARMA',
    nombrePropio: `${prefijo} ${i}`,
  };
}

/** Respuesta con la forma de SCRUM-318, que la busqueda tambien respeta. */
function paginaDe(numero, totalElementos, prefijo) {
  const totalPaginas = Math.ceil(totalElementos / TAMANIO);
  const desde = numero * TAMANIO;
  const cuantos = Math.max(0, Math.min(TAMANIO, totalElementos - desde));
  return {
    elementos: Array.from({ length: cuantos }, (_, i) => elemento(desde + i, prefijo)),
    numero,
    tamanio: TAMANIO,
    totalElementos,
    totalPaginas,
    ultima: numero >= totalPaginas - 1,
  };
}

/** Deja correr las promesas encadenadas del envio y del repintado. */
async function esperar() {
  for (let i = 0; i < 5; i += 1) {
    await new Promise((r) => setTimeout(r, 0));
  }
}

/** Dobles inertes: esta prueba no ejercita creacion, edicion ni equipamiento. */
const SIN_ESCRITURA = {
  crear: async () => {},
  modificar: async () => {},
  consultarEquipo: async () => ({ armas: [], armaduras: {}, items: [] }),
  equipar: async () => {},
  desequipar: async () => {},
};

function casillas(raiz) {
  return [...raiz.querySelectorAll('.paginacion__pagina:not([data-direccion])')];
}

async function buscarTexto(raiz, texto) {
  raiz.querySelector('.inventario-busqueda__control').value = texto;
  raiz
    .querySelector('.inventario-busqueda')
    .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  await esperar();
}

/**
 * Inventario completo de 10 paginas y una busqueda que encuentra 3. Registra
 * cada llamada para comprobar a quien se le pidio cada pagina.
 */
async function montarConBusqueda(raiz) {
  const consultas = [];
  const busquedas = [];
  await montarInventario(raiz, 'jugador-A', 0, {
    ...SIN_ESCRITURA,
    consultar: async (_id, numero) => {
      consultas.push(numero);
      return paginaDe(numero, 160, 'Inventario');
    },
    buscar: async (identidad, criterio, numero) => {
      busquedas.push([identidad, criterio, numero]);
      return paginaDe(numero, 40, 'Hallazgo');
    },
  });
  return { consultas, busquedas };
}

let raiz;
beforeEach(() => {
  raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
});

describe('Paginacion con una búsqueda activa', () => {
  test('cambiar de página pide la página siguiente a la búsqueda, con el mismo criterio', async () => {
    const { consultas, busquedas } = await montarConBusqueda(raiz);
    // UXC-1 — al montar se reune el inventario entero (diez paginas).
    const alMontar = consultas.length;

    await buscarTexto(raiz, 'Espada larga');
    casillas(raiz)[1].click();
    await esperar();

    expect(busquedas).toEqual([
      ['jugador-A', 'Espada larga', 0],
      ['jugador-A', 'Espada larga', 1],
    ]);
    // El inventario completo solo se consulto al montar la vista: la busqueda
    // pagina contra su indice, no contra el inventario.
    expect(consultas).toHaveLength(alMontar);
    expect(raiz.querySelector('.vitrina__nombre').textContent).toBe('Hallazgo 16');
  });

  test('el control refleja el total de la busqueda y no el del inventario completo', async () => {
    await montarConBusqueda(raiz);
    expect(casillas(raiz)).toHaveLength(10);

    await buscarTexto(raiz, 'Espada larga');

    // 40 coincidencias a 16 por pagina son 3 paginas.
    expect(casillas(raiz).map((c) => c.textContent)).toEqual(['1', '2', '3']);
    expect(raiz.querySelector('[aria-current="page"]').textContent).toBe('1');
  });

  test('tras cambiar de página el texto buscado y el botón Limpiar siguen a la vista', async () => {
    await montarConBusqueda(raiz);

    await buscarTexto(raiz, 'Espada larga');
    casillas(raiz)[2].click();
    await esperar();

    expect(raiz.querySelector('.inventario-busqueda__control').value).toBe('Espada larga');
    expect(raiz.querySelector('.inventario-busqueda__limpiar').hidden).toBe(false);
    expect(raiz.querySelector('[aria-current="page"]').textContent).toBe('3');
  });

  test('limpiar la busqueda vuelve a la primera pagina del inventario completo', async () => {
    const { consultas } = await montarConBusqueda(raiz);
    const alMontar = consultas.length;

    await buscarTexto(raiz, 'Espada larga');
    casillas(raiz)[1].click();
    await esperar();
    raiz.querySelector('.inventario-busqueda__limpiar').click();
    await esperar();

    // UXC-1 — limpiar vuelve a la coleccion ya reunida (pagina 1 de objetos)
    // sin volver a pedirla: el criterio «solo objetos» se conserva (HU-INV-011
    // CA-3) y no hay nada nuevo que traer.
    expect(consultas).toHaveLength(alMontar);
    expect(casillas(raiz)).toHaveLength(10);
    expect(raiz.querySelector('[aria-current="page"]').textContent).toBe('1');
    expect(raiz.querySelector('.vitrina__nombre').textContent).toBe('Inventario 0');
  });
});
