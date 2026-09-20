/**
 * Vitrina y carrito — HU-CAR-001.
 *
 * Lo que se prueba es lo que estaba roto: la identidad que viaja en cada
 * petición, la base de la API, y que un fallo del carrito no se disfrace de
 * carrito vacío.
 */

import { jest } from '@jest/globals';

import {
  haySesion,
  cargarVitrina,
  cargarCarrito,
  agregarAlCarrito,
  actualizarUI,
  montarTienda,
} from './tienda.js';

const UID = '44444444-4444-4444-4444-444444444444';

const VISTA = `
  <div id="productos-grid"><p>Cargando…</p></div>
  <div id="cart-items"></div>
  <span id="cart-subtotal"></span>
  <span id="cart-total"></span>
  <button id="btn-pagar"></button>
`;

/** JWT de mentira con el claim que lee la identidad. */
function token(cuerpo) {
  const b64 = (o) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'none' })}.${b64(cuerpo)}.firma`;
}

function respuesta(cuerpo, ok = true, status = 200) {
  return { ok, status, json: async () => cuerpo, headers: { get: () => 'application/json' } };
}

beforeEach(() => {
  document.head.innerHTML = '';
  document.body.innerHTML = VISTA;
  sessionStorage.clear();
  sessionStorage.setItem('nexus.token', token({ uid: UID }));
  globalThis.fetch = jest.fn();
});

describe('identidad', () => {
  test('hay sesion cuando el token trae uid', () => {
    expect(haySesion()).toBe(true);
  });

  test('sin token no hay sesion', () => {
    sessionStorage.clear();

    expect(haySesion()).toBe(false);
  });

  test('X-User-Id lleva al usuario REAL, no al de prueba que habia antes', async () => {
    // El defecto: `const USER_ID = 'usr_test_123'` viajaba en cada peticion,
    // asi que todos los compradores eran el mismo usuario.
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));

    await cargarVitrina(document);

    const cabeceras = globalThis.fetch.mock.calls[0][1].headers;
    expect(cabeceras['X-User-Id']).toBe(UID);
    expect(JSON.stringify(cabeceras)).not.toContain('usr_test_123');
  });

  test('sin sesion NO se manda X-User-Id: mejor un 400 que mezclar carritos', async () => {
    sessionStorage.clear();
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));

    await cargarVitrina(document);

    expect(globalThis.fetch.mock.calls[0][1].headers['X-User-Id']).toBeUndefined();
  });
});

describe('base de la API', () => {
  test('mismo origen por omision, con el prefijo de version', async () => {
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));

    await cargarVitrina(document);

    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/productos');
  });

  test('con meta declarada, la base la manda la pagina', async () => {
    // La cabecera se declara ANTES de esperar nada, y `beforeEach` la limpia:
    // tocarla despues de un await es lo que ESLint marca como carrera.
    document.head.innerHTML = '<meta name="nexus-api-base" content="http://127.0.0.1:8083/" />';
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));

    await cargarVitrina(document);

    expect(globalThis.fetch.mock.calls[0][0]).toBe('http://127.0.0.1:8083/api/v1/productos');
  });
});

describe('vitrina', () => {
  test('pinta una tarjeta por producto', async () => {
    globalThis.fetch.mockResolvedValue(
      respuesta({
        content: [{ id: 'p1', nombre: 'Espada', descripcion: 'Filo', precio: 100, tipo: 'ARMA' }],
      }),
    );

    await cargarVitrina(document);

    expect(document.querySelectorAll('.product-card')).toHaveLength(1);
    expect(document.querySelector('h4').textContent).toBe('Espada');
  });

  test('un nombre con etiquetas no se interpreta como HTML', async () => {
    globalThis.fetch.mockResolvedValue(
      respuesta({ content: [{ id: 'p1', nombre: '<img src=x onerror=alert(1)>', precio: 1 }] }),
    );

    await cargarVitrina(document);

    expect(document.querySelector('#productos-grid img')).toBeNull();
    expect(document.querySelector('h4').textContent).toContain('<img');
  });

  test('si la vitrina falla se dice, en vez de dejar el cargador girando', async () => {
    globalThis.fetch.mockRejectedValue(new Error('sin red'));

    await cargarVitrina(document);

    expect(document.getElementById('productos-grid').textContent).toMatch(/no se pudo cargar/i);
  });
});

describe('carrito', () => {
  test('un 404 SI es un carrito vacio', async () => {
    globalThis.fetch.mockRejectedValue(Object.assign(new Error('no hay'), { estado: 404 }));

    await cargarCarrito(document);

    expect(document.getElementById('cart-items').textContent).toMatch(/vacío/i);
    expect(document.getElementById('btn-pagar').disabled).toBe(true);
  });

  test('un 500 NO es un carrito vacio: se avisa del fallo', async () => {
    // El defecto anterior: cualquier error se pintaba como «carrito vacio», y
    // el jugador no veia sus productos sin que nada se lo dijera.
    globalThis.fetch.mockRejectedValue(Object.assign(new Error('roto'), { estado: 500 }));

    await cargarCarrito(document);

    const texto = document.getElementById('cart-items').textContent;
    expect(texto).toMatch(/no se pudo cargar/i);
    expect(texto).not.toMatch(/vacío/i);
    expect(document.getElementById('btn-pagar').disabled).toBe(true);
  });

  test('con items pinta cada uno y habilita pagar', () => {
    actualizarUI(
      { total: 250, items: [{ cantidad: 2, subtotal: 250, producto: { nombre: 'Poción' } }] },
      document,
    );

    expect(document.querySelectorAll('.cart-item')).toHaveLength(1);
    expect(document.getElementById('cart-total').textContent).toBe('250 COP');
    expect(document.getElementById('btn-pagar').disabled).toBe(false);
  });

  test('agregar manda el producto y refresca el carrito', async () => {
    globalThis.fetch.mockResolvedValue(respuesta({ total: 0, items: [] }));

    await agregarAlCarrito('p1', document);

    const [url, opciones] = globalThis.fetch.mock.calls[0];
    expect(url).toBe('/api/v1/carrito/items');
    expect(JSON.parse(opciones.body)).toEqual({ productoId: 'p1', cantidad: 1 });
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
  });
});

describe('montarTienda', () => {
  test('el boton de una tarjeta creada despues tambien anade al carrito', async () => {
    // Es lo que la delegacion resuelve: antes iba por onclick en el HTML
    // generado, y eso deja de encontrar la funcion en un modulo. La tarjeta se
    // crea DESPUES de enganchar la escucha, que es el caso que se rompia.
    globalThis.fetch.mockResolvedValue(
      respuesta({ content: [{ id: 'p1', nombre: 'Espada', precio: 100 }], items: [] }),
    );

    await montarTienda(document);
    globalThis.fetch.mockClear();
    document.querySelector('[data-producto]').click();
    await Promise.resolve();

    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/carrito/items');
    expect(JSON.parse(globalThis.fetch.mock.calls[0][1].body).productoId).toBe('p1');
  });
});
