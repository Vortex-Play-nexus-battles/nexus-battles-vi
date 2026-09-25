/**
 * HU-INV-007 - Apertura y cierre de la ficha, con teclado y foco.
 * Cubre RNF-ACC-002 (toda funcion operable por teclado, foco visible)
 * y RNF-USA-003 (los cuatro estados en toda vista que consulte datos).
 */
import { jest } from '@jest/globals';
import { abrirFicha } from './ficha-producto.js';

const PRODUCTO = {
  id: 'p-1',
  nombre: 'Hacha de Vorn',
  imagen: '/x.png',
  descripcion: 'Forjada en la niebla.',
  tipo: 'ARMA',
  tiraje: -1,
  premium: false,
  poderDeAtaque: 42,
};

/** Un codigo del protocolo: tres cifras entre 100 y 599. */
const CODIGO_DE_ESTADO = /\b[1-5]\d{2}\b/;

let origen;
beforeEach(() => {
  document.body.replaceChildren();
  origen = document.createElement('button');
  origen.textContent = 'Hacha de Vorn';
  document.body.appendChild(origen);
  origen.focus();
});

describe('Apertura y cierre de la ficha', () => {
  test('mientras consulta muestra el estado de carga', async () => {
    let resolver;
    const enVuelo = new Promise((r) => {
      resolver = r;
    });
    const abierta = abrirFicha('p-1', { consultarProducto: () => enVuelo, origen });

    expect(document.querySelector('.estado-carga')).not.toBeNull();

    resolver(PRODUCTO);
    await abierta;
    expect(document.querySelector('.estado-carga')).toBeNull();
  });

  test('con el producto pinta la ficha', async () => {
    await abrirFicha('p-1', { consultarProducto: async () => PRODUCTO, origen });

    expect(document.querySelector('.ficha__nombre').textContent).toBe('Hacha de Vorn');
  });

  test('si el catálogo falla, el jugador ve un aviso sin código de estado', async () => {
    const consola = jest.spyOn(console, 'error').mockImplementation(() => {});

    await abrirFicha('p-1', {
      consultarProducto: async () => {
        throw new Error('respondio 503');
      },
      origen,
    });

    const aviso = document.querySelector('.estado-error');
    expect(aviso).not.toBeNull();
    expect(CODIGO_DE_ESTADO.test(aviso.textContent)).toBe(false);
    consola.mockRestore();
  });

  test('al abrirse, el foco entra en la ficha', async () => {
    await abrirFicha('p-1', { consultarProducto: async () => PRODUCTO, origen });

    const capa = document.querySelector('.ficha-capa');
    expect(capa.contains(document.activeElement)).toBe(true);
  });

  test('la tecla Escape la cierra', async () => {
    await abrirFicha('p-1', { consultarProducto: async () => PRODUCTO, origen });

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(document.querySelector('.ficha-capa')).toBeNull();
  });

  test('tiene un boton de cerrar con nombre accesible', async () => {
    await abrirFicha('p-1', { consultarProducto: async () => PRODUCTO, origen });

    const cerrar = document.querySelector('.ficha__cerrar');
    expect(cerrar.getAttribute('aria-label')).toMatch(/cerrar/i);

    cerrar.click();
    expect(document.querySelector('.ficha-capa')).toBeNull();
  });

  test('al cerrarse, el foco vuelve a donde estaba', async () => {
    await abrirFicha('p-1', { consultarProducto: async () => PRODUCTO, origen });

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(document.activeElement).toBe(origen);
  });

  test('abrir dos veces no apila fichas', async () => {
    await abrirFicha('p-1', { consultarProducto: async () => PRODUCTO, origen });
    await abrirFicha('p-1', { consultarProducto: async () => PRODUCTO, origen });

    expect(document.querySelectorAll('.ficha-capa')).toHaveLength(1);
  });

  test('consulta el producto por su identificador', async () => {
    const pedidos = [];
    await abrirFicha('p-42', {
      consultarProducto: async (id) => {
        pedidos.push(id);
        return PRODUCTO;
      },
      origen,
    });

    expect(pedidos).toEqual(['p-42']);
  });

  test('Escape deja de escuchar tras cerrarse', async () => {
    await abrirFicha('p-1', { consultarProducto: async () => PRODUCTO, origen });
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    // Un segundo Escape con la ficha cerrada no debe reventar ni robar foco.
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(document.activeElement).toBe(origen);
  });
});

/**
 * UXC-3/UXC-4 — lo que otras vistas añaden al final de la ficha: las
 * opiniones de la comunidad (inventario, tienda, portada) y la compra (tienda,
 * portada). Van después de lo que dice el catálogo, en orden; uno que falle no
 * se lleva la ficha por delante.
 */
describe('Complementos de la ficha', () => {
  test('se pintan al final, en orden, con el producto y la ficha a mano', async () => {
    const primero = jest.fn((producto) => {
      const nodo = document.createElement('section');
      nodo.dataset.complemento = `compra-${producto.id}`;
      return nodo;
    });
    const segundo = jest.fn((_producto, { ficha }) => {
      const nodo = document.createElement('section');
      nodo.dataset.complemento = ficha.classList.contains('ficha') ? 'opiniones' : 'sin-ficha';
      return nodo;
    });

    await abrirFicha('p-1', {
      consultarProducto: async () => PRODUCTO,
      origen,
      complementos: [primero, segundo],
    });

    const zona = document.querySelector('.ficha .ficha__complementos');
    expect(zona).not.toBeNull();
    expect(Array.from(zona.children).map((n) => n.dataset.complemento)).toEqual([
      'compra-p-1',
      'opiniones',
    ]);
    // Después de lo que dice el catálogo.
    expect(document.querySelector('.ficha').lastElementChild).toBe(zona);
  });

  test('uno que falla no tumba la ficha ni a los demás', async () => {
    const consola = jest.spyOn(console, 'error').mockImplementation(() => {});
    const bueno = () => {
      const nodo = document.createElement('p');
      nodo.textContent = 'sigo aquí';
      return nodo;
    };

    await abrirFicha('p-1', {
      consultarProducto: async () => PRODUCTO,
      origen,
      complementos: [
        () => {
          throw new Error('roto');
        },
        bueno,
      ],
    });

    expect(document.querySelector('.ficha__nombre').textContent).toBe('Hacha de Vorn');
    expect(document.querySelector('.ficha__complementos').textContent).toBe('sigo aquí');
    consola.mockRestore();
  });

  test('las cifras de un héroe van antes que los complementos', async () => {
    const complemento = () => {
      const nodo = document.createElement('section');
      nodo.className = 'complemento-de-prueba';
      return nodo;
    };
    const bloque = document.createElement('section');
    bloque.className = 'bloque-del-heroe';

    await abrirFicha('p-h', {
      consultarProducto: async () => ({
        ...PRODUCTO,
        id: 'p-h',
        tipo: 'HEROE',
        prototipo: 'Médico',
      }),
      origen,
      elementoId: 'e-1',
      identidad: 'uid',
      detalleDeHeroe: async () => [bloque],
      complementos: [complemento],
    });

    const ficha = document.querySelector('.ficha');
    const hijos = Array.from(ficha.children);
    expect(hijos.indexOf(ficha.querySelector('.bloque-del-heroe'))).toBeLessThan(
      hijos.indexOf(ficha.querySelector('.ficha__complementos')),
    );
  });

  test('desde la tienda, un producto suspendido no habla de «tu inventario»', async () => {
    await abrirFicha('p-1', {
      consultarProducto: async () => ({ ...PRODUCTO, estado: 'SUSPENDIDO' }),
      origen,
      contexto: 'tienda',
    });

    const aviso = document.querySelector('.ficha__no-disponible');
    expect(aviso.textContent).toBe('Este producto no está a la venta ahora mismo.');
    expect(aviso.textContent).not.toMatch(/inventario/);
  });
});
