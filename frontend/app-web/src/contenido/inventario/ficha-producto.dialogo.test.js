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

  test('si el catalogo falla, el jugador ve un aviso sin codigo de estado', async () => {
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
