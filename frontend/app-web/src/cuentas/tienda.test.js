/**
 * Vitrina y carrito — HU-CAR-001.
 *
 * Lo que se prueba es lo que estaba roto: la identidad que viaja en cada
 * petición, la base de la API, y que un fallo del carrito no se disfrace de
 * carrito vacío.
 *
 * FI-R2 — los productos de estas pruebas montaban `{ precio: 100 }`. Ese campo
 * no existe: `ProductoVitrinaDto` expone `precioFinal`, `precioOriginal` y
 * `moneda`. La prueba confirmaba la suposicion del frontend en vez del
 * contrato del servicio, y por eso el defecto —cada tarjeta decia «0 COP»—
 * sobrevivio a una bateria verde. Los datos de aqui usan ahora la forma real
 * del DTO.
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

  test('la identidad viaja en el Bearer, nunca en X-User-Id ni como usuario de prueba', async () => {
    // El defecto original: `const USER_ID = 'usr_test_123'` viajaba en cada
    // peticion, asi que todos los compradores eran el mismo usuario. Despues
    // viajo el uid en X-User-Id, que el navegador podia poner a su gusto.
    // Ahora ms-ecommerce lee el uid del JWT (ADR-002).
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));

    await cargarVitrina(document);

    const cabeceras = globalThis.fetch.mock.calls[0][1].headers;
    expect(cabeceras['X-User-Id']).toBeUndefined();
    expect(cabeceras.Authorization).toBe(`Bearer ${sessionStorage.getItem('nexus.token')}`);
    expect(JSON.stringify(cabeceras)).not.toContain('usr_test_123');
  });

  test('sin sesión no viaja ninguna identidad: el backend respondera 401', async () => {
    sessionStorage.clear();
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));

    await cargarVitrina(document);

    const cabeceras = globalThis.fetch.mock.calls[0][1].headers;
    expect(cabeceras['X-User-Id']).toBeUndefined();
    expect(cabeceras.Authorization).toBeUndefined();
  });
});

describe('base de la API', () => {
  test('mismo origen por omision, con el prefijo de versión', async () => {
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));

    await cargarVitrina(document);

    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/productos');
  });

  test('con meta declarada, la base la manda la página', async () => {
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
        content: [
          {
            id: 'p1',
            nombre: 'Espada',
            descripcion: 'Filo',
            precioFinal: 100,
            precioOriginal: 100,
            moneda: 'COP',
            tipo: 'ARMA',
          },
        ],
      }),
    );

    await cargarVitrina(document);

    expect(document.querySelectorAll('.product-card')).toHaveLength(1);
    expect(document.querySelector('h4').textContent).toBe('Espada');
  });

  test('un nombre con etiquetas no se interpreta como HTML', async () => {
    globalThis.fetch.mockResolvedValue(
      respuesta({
        content: [
          { id: 'p1', nombre: '<img src=x onerror=alert(1)>', precioFinal: 1, moneda: 'COP' },
        ],
      }),
    );

    await cargarVitrina(document);

    // El producto no trae `imagenUrl`, asi que la tarjeta no pinta ninguna
    // imagen propia: cualquier <img> aqui vendria del nombre interpretado.
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
  test('un 404 SI es un carrito vacío', async () => {
    globalThis.fetch.mockRejectedValue(Object.assign(new Error('no hay'), { estado: 404 }));

    await cargarCarrito(document);

    expect(document.getElementById('cart-items').textContent).toMatch(/vacío/i);
    expect(document.getElementById('btn-pagar').disabled).toBe(true);
  });

  test('un 500 NO es un carrito vacío: se avisa del fallo', async () => {
    // El defecto anterior: cualquier error se pintaba como «carrito vacio», y
    // el jugador no veia sus productos sin que nada se lo dijera.
    globalThis.fetch.mockRejectedValue(Object.assign(new Error('roto'), { estado: 500 }));

    await cargarCarrito(document);

    const texto = document.getElementById('cart-items').textContent;
    expect(texto).toMatch(/no se pudo cargar/i);
    expect(texto).not.toMatch(/vacío/i);
    expect(document.getElementById('btn-pagar').disabled).toBe(true);
  });

  test('con items pinta cada uno y el total sale con su moneda', () => {
    actualizarUI(
      {
        total: 250,
        items: [
          {
            cantidad: 2,
            subtotal: 250,
            precioUnitario: 125,
            producto: { nombre: 'Poción', moneda: 'COP' },
          },
        ],
      },
      document,
    );

    expect(document.querySelectorAll('.cart-item')).toHaveLength(1);
    expect(document.getElementById('cart-total').textContent).toBe('250 COP');
    // FI-R2 — «Pagar» ya NO se habilita: RF-CAR-010 y RF-PAG-001 estan
    // confirmados pero `CarritoController` no tiene ninguna ruta de pago, y el
    // boton no tenia manejador. Ver `prepararBotonDePago`.
    expect(document.getElementById('btn-pagar').disabled).toBe(true);
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
      respuesta({
        content: [{ id: 'p1', nombre: 'Espada', precioFinal: 100, moneda: 'COP' }],
        items: [],
      }),
    );

    await montarTienda(document);
    globalThis.fetch.mockClear();
    document.querySelector('[data-producto]').click();
    await Promise.resolve();

    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/carrito/items');
    expect(JSON.parse(globalThis.fetch.mock.calls[0][1].body).productoId).toBe('p1');
  });
});

/**
 * UX-R2.8d — los estados que le faltaban a la Tienda.
 */
describe('UX-R2.8d - estados de la vitrina', () => {
  test('mientras carga se ve la forma de lo que viene, no una rejilla en blanco', async () => {
    // El HTML traia `<!-- Cargando productos... -->`: un comentario, o sea
    // nada en la pantalla hasta que respondiera el servicio.
    let resolver;
    globalThis.fetch.mockReturnValue(new Promise((r) => (resolver = r)));

    const pintando = cargarVitrina(document);
    expect(document.querySelector('#productos-grid [data-estado="cargando"]')).not.toBeNull();

    resolver({ ok: true, status: 200, json: async () => ({ content: [] }) });
    await pintando;
  });

  test('un catalogo vacio no se confunde con un fallo', async () => {
    globalThis.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ content: [] }),
    });

    await cargarVitrina(document);

    const rejilla = document.getElementById('productos-grid');
    expect(rejilla.querySelector('[data-estado="vacio"]')).not.toBeNull();
    expect(rejilla.querySelector('[data-estado="error"]')).toBeNull();
    expect(rejilla.textContent).not.toMatch(/no se pudo/i);
  });

  test('un fallo ofrece reintentar, y reintenta de verdad', async () => {
    globalThis.fetch.mockRejectedValueOnce(new Error('sin red'));
    await cargarVitrina(document);

    const reintentar = document.querySelector('#productos-grid [data-accion="reintentar"]');
    expect(reintentar).not.toBeNull();

    globalThis.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        content: [{ id: 'p1', nombre: 'Espada', precioFinal: 10, moneda: 'COP', tipo: 'ARMA' }],
      }),
    });
    reintentar.click();
    await new Promise((r) => setTimeout(r, 0));

    expect(document.querySelectorAll('.product-card')).toHaveLength(1);
  });

  test('el carrito caido tambien ofrece reintentar', async () => {
    globalThis.fetch.mockRejectedValue(Object.assign(new Error('roto'), { estado: 500 }));

    await cargarCarrito(document);

    expect(document.querySelector('#cart-items [data-accion="reintentar"]')).not.toBeNull();
    expect(document.getElementById('btn-pagar').disabled).toBe(true);
  });
});

/**
 * FI-R2 — el precio de la tienda.
 *
 * El defecto: `tienda.js` leia `producto.precio`, un campo que
 * `ProductoVitrinaDto` no tiene. `undefined ?? 0` daba cero y **todas** las
 * tarjetas del catalogo decian «0 COP». Las pruebas no lo vieron porque sus
 * productos tambien traian `precio`.
 *
 * Estas pruebas van contra la forma real del DTO, la que devuelve
 * `VitrinaService.convertirADto`.
 */
describe('FI-R2 - el precio que se ensena es el que cobra el servicio', () => {
  const dto = (extra = {}) => ({
    id: 7,
    nombre: 'Yelmo del Alba',
    descripcion: 'Acero claro',
    habilidades: 'Defensa +4',
    tipo: 'ARMADURA',
    precioFinal: 18500,
    precioOriginal: 18500,
    moneda: 'COP',
    enPromocion: false,
    porcentajeDescuento: 0,
    esPropio: false,
    enListaDeseos: false,
    ...extra,
  });

  test('el precio sale de precioFinal, no de un campo inventado', async () => {
    globalThis.fetch.mockResolvedValue(respuesta({ content: [dto()] }));

    await cargarVitrina(document);

    const precio = document.querySelector('.price');
    expect(precio.textContent).toBe('18.500 COP');
    expect(precio.textContent).not.toBe('0 COP');
  });

  test('la moneda es la que declara el producto, no un COP supuesto', async () => {
    globalThis.fetch.mockResolvedValue(
      respuesta({ content: [dto({ precioFinal: 12, moneda: 'USD' })] }),
    );

    await cargarVitrina(document);

    expect(document.querySelector('.price').textContent).toBe('12 USD');
  });

  test('sin precio se dice que no esta, en vez de anunciar que es gratis', async () => {
    globalThis.fetch.mockResolvedValue(
      respuesta({ content: [dto({ precioFinal: null, precioOriginal: null })] }),
    );

    await cargarVitrina(document);

    const precio = document.querySelector('.price');
    expect(precio.textContent).not.toMatch(/^0/);
    expect(precio.textContent).toMatch(/no disponible/i);
  });

  test('un precio de cero SI se ensena: gratis es un precio', async () => {
    globalThis.fetch.mockResolvedValue(respuesta({ content: [dto({ precioFinal: 0 })] }));

    await cargarVitrina(document);

    expect(document.querySelector('.price').textContent).toBe('0 COP');
    expect(document.querySelector('.price').textContent).not.toMatch(/no disponible/i);
  });

  test('una rebaja real ensena el precio anterior tachado y el porcentaje', async () => {
    globalThis.fetch.mockResolvedValue(
      respuesta({
        content: [
          dto({
            precioOriginal: 20000,
            precioFinal: 16000,
            enPromocion: true,
            porcentajeDescuento: 20,
          }),
        ],
      }),
    );

    await cargarVitrina(document);

    expect(document.querySelector('.price').textContent).toBe('16.000 COP');
    expect(document.querySelector('.price-antes').textContent).toBe('20.000 COP');
    expect(document.querySelector('.badge-descuento').textContent).toBe('-20%');
  });

  test('«en promocion» sin rebaja en el precio no pinta ningun descuento', async () => {
    // Es el estado real de hoy: `VitrinaService` pone
    // precioFinal = precioOriginal = precioBaseCop y nunca aplica el
    // porcentaje. Un «-30%» junto a un precio sin rebajar es una promesa que
    // el carrito no cumple.
    globalThis.fetch.mockResolvedValue(
      respuesta({
        content: [
          dto({
            precioOriginal: 20000,
            precioFinal: 20000,
            enPromocion: true,
            porcentajeDescuento: 30,
          }),
        ],
      }),
    );

    await cargarVitrina(document);

    expect(document.querySelector('.badge-descuento')).toBeNull();
    expect(document.querySelector('.price-antes')).toBeNull();
    expect(document.querySelector('.price').textContent).toBe('20.000 COP');
  });

  test('la imagen y las habilidades del DTO llegan a la tarjeta (RF-CAR-001)', async () => {
    globalThis.fetch.mockResolvedValue(
      respuesta({ content: [dto({ imagenUrl: 'https://cdn.example/yelmo.png' })] }),
    );

    await cargarVitrina(document);

    const img = document.querySelector('#productos-grid .product-image img');
    expect(img).not.toBeNull();
    expect(img.getAttribute('src')).toBe('https://cdn.example/yelmo.png');
    expect(document.querySelector('.habilidades').textContent).toBe('Defensa +4');
  });

  test('un producto sin id no manda undefined al carrito', async () => {
    globalThis.fetch.mockResolvedValue(respuesta({ content: [dto({ id: null })] }));

    await cargarVitrina(document);

    const boton = document.querySelector('.btn-add');
    expect(boton.disabled) /* no hay nada que anadir */
      .toBe(true);
    expect(boton.dataset.producto).toBeUndefined();
  });

  test('«Pagar» no se enciende mientras no exista la pasarela (RF-PAG-001)', () => {
    actualizarUI(
      {
        total: 400,
        items: [{ cantidad: 1, subtotal: 400, producto: { nombre: 'Escudo', moneda: 'COP' } }],
      },
      document,
    );

    const boton = document.getElementById('btn-pagar');
    expect(boton.disabled).toBe(true);
    // Y el motivo esta escrito, no solo apagado.
    expect(boton.getAttribute('aria-describedby')).toBe('aviso-pago-pendiente');
  });

  test('un item sin subtotal no escribe «undefined» en el carrito', () => {
    actualizarUI(
      { total: null, items: [{ cantidad: 1, producto: { nombre: 'Escudo' } }] },
      document,
    );

    const texto = document.getElementById('cart-items').textContent;
    expect(texto).not.toContain('undefined');
    expect(texto).not.toContain('null');
    expect(document.getElementById('cart-total').textContent).not.toContain('undefined');
  });
});
