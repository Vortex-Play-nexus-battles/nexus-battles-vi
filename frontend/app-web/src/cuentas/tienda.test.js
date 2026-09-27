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
  quitarDelCarrito,
  actualizarUI,
  mismosCriterios,
  montarTienda,
  leerCriterios,
} from './tienda.js';

const UID = '44444444-4444-4444-4444-444444444444';

/** Un identificador del catálogo maestro, con la forma que devuelve la vitrina 1.2.0. */
const UUID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

const VISTA = `
  <div id="productos-grid"><p>Cargando…</p></div>
  <div id="aviso-carrito" data-zona="aviso" hidden></div>
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

/** Un rechazo con problem details (regla 4), como los declara ecommerce-carrito.yaml 1.2.0. */
function problema(status, type, extra = {}) {
  return {
    ok: false,
    status,
    json: async () => ({ type, title: 'Rechazo', status, ...extra }),
    headers: { get: () => 'application/problem+json' },
  };
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

    // R16 — la vitrina tiene prefijo propio. `/api/v1/productos` es del
    // catálogo maestro entero desde que el borde dejó de repartirlo por
    // método (#421): pedirlo ahí sería pedirle la vitrina a otro servicio.
    // UXC-4 — y se pide entera, de cincuenta en cincuenta (el máximo del
    // contrato), para que buscar y filtrar vean toda la tienda.
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/vitrina?size=50');
  });

  test('con meta declarada, la base la manda la página', async () => {
    // La cabecera se declara ANTES de esperar nada, y `beforeEach` la limpia:
    // tocarla despues de un await es lo que ESLint marca como carrera.
    document.head.innerHTML = '<meta name="nexus-api-base" content="http://127.0.0.1:8083/" />';
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));

    await cargarVitrina(document);

    expect(globalThis.fetch.mock.calls[0][0]).toBe('http://127.0.0.1:8083/api/v1/vitrina?size=50');
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
    // UXC-4 — el nombre es un h3 (`.product-card__nombre`): la vitrina tiene
    // su h2 y la jerarquía ya no salta de h1 a h4.
    expect(document.querySelector('.product-card__nombre').textContent).toBe('Espada');
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
    expect(document.querySelector('.product-card__nombre').textContent).toContain('<img');
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

/**
 * R16 — la vitrina proyecta el catálogo maestro (ecommerce-carrito.yaml 1.2.0).
 *
 * Tres cosas nuevas que se fijan aquí: el identificador es un UUID en texto de
 * punta a punta; un catálogo caído (503) no se disfraza de tienda vacía; y un
 * «Añadir» rechazado se le dice al jugador en vez de perderse en la consola.
 */
describe('R16 - la vitrina del catálogo maestro', () => {
  test('el UUID del catálogo viaja tal cual, en texto, hasta POST /carrito/items', async () => {
    globalThis.fetch.mockResolvedValue(
      respuesta({
        content: [{ id: UUID, nombre: 'Espada', precioFinal: 45000, moneda: 'COP', tipo: 'ARMA' }],
        items: [],
      }),
    );

    await montarTienda(document);
    globalThis.fetch.mockClear();
    document.querySelector('[data-producto]').click();
    await new Promise((r) => setTimeout(r, 0));

    const [url, opciones] = globalThis.fetch.mock.calls[0];
    expect(url).toBe('/api/v1/carrito/items');
    const cuerpo = JSON.parse(opciones.body);
    // Ni `Number()` ni `parseInt()` por el camino: un UUID convertido en
    // número es `NaN`, y el servicio respondería «producto inexistente».
    expect(cuerpo.productoId).toBe(UUID);
    expect(typeof cuerpo.productoId).toBe('string');
    expect(cuerpo.cantidad).toBe(1);
  });

  test('un catálogo caído (503) no se confunde con una tienda sin productos', async () => {
    // El interceptor compartido no lanza ante un 503: devuelve la respuesta.
    // Antes, su cuerpo sin `content` se leía como «la tienda no tiene
    // productos», que es exactamente lo que el contrato quiere evitar.
    globalThis.fetch.mockResolvedValue(problema(503, 'urn:nexus:problema:catalogo-no-disponible'));

    await cargarVitrina(document);

    const rejilla = document.getElementById('productos-grid');
    expect(rejilla.querySelector('[data-estado="vacio"]')).toBeNull();
    expect(rejilla.querySelector('[data-estado="error"]')).not.toBeNull();
    expect(rejilla.textContent).toMatch(/no se pudo cargar la tienda/i);
    expect(rejilla.textContent).toMatch(/catálogo no responde/i);
    expect(rejilla.textContent).toMatch(/carrito sigue disponible/i);
    expect(rejilla.querySelector('[data-accion="reintentar"]')).not.toBeNull();
  });

  test('un 500 del carrito que llega como respuesta, no como excepción, tampoco es «vacío»', async () => {
    // Las pruebas de más arriba simulan el fallo con un `fetch` que lanza; el
    // interceptor real devuelve la respuesta. Esta es la forma real.
    globalThis.fetch.mockResolvedValue(
      respuesta({ type: 'about:blank', title: 'Error', status: 500 }, false, 500),
    );

    await cargarCarrito(document);

    const texto = document.getElementById('cart-items').textContent;
    expect(texto).toMatch(/no se pudo cargar/i);
    expect(texto).not.toMatch(/vacío/i);
    expect(document.querySelector('#cart-items [data-accion="reintentar"]')).not.toBeNull();
  });

  test('un 404 del carrito que llega como respuesta sigue siendo un carrito vacío', async () => {
    globalThis.fetch.mockResolvedValue(respuesta({}, false, 404));

    await cargarCarrito(document);

    expect(document.getElementById('cart-items').textContent).toMatch(/vacío/i);
    expect(document.getElementById('btn-pagar').disabled).toBe(true);
  });

  test('cualquier otro rechazo de la vitrina tampoco se pinta como tienda vacía', async () => {
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }, false, 500));

    await cargarVitrina(document);

    const rejilla = document.getElementById('productos-grid');
    expect(rejilla.querySelector('[data-estado="vacio"]')).toBeNull();
    expect(rejilla.textContent).toMatch(/no se pudo cargar la tienda/i);
    expect(rejilla.textContent).not.toMatch(/catálogo no responde/i);
  });
});

/**
 * R16 — «Añadir» rechazado: un aviso corto, con salida, en vez de silencio.
 *
 * Los cinco `type` son los que declara `POST /carrito/items` en el contrato
 * 1.2.0. El mensaje se decide por el `type` y el tono por el `status`
 * (MAPEO-ERRORES §2 y §4), nunca por el texto del servidor.
 */
describe('R16 - un «Añadir» rechazado se le dice al jugador', () => {
  const zona = () => document.getElementById('aviso-carrito');

  test.each([
    [409, 'urn:nexus:problema:producto-agotado', /se agotó/i],
    [409, 'urn:nexus:problema:producto-no-disponible', /no está a la venta/i],
    [422, 'urn:nexus:problema:producto-inexistente', /ya no está en el catálogo/i],
    [422, 'urn:nexus:problema:producto-sin-precio-en-moneda-real', /no se vende con dinero real/i],
  ])(
    '%i %s: aviso de advertencia que lleva a actualizar la tienda',
    async (estado, tipo, texto) => {
      globalThis.fetch.mockResolvedValue(problema(estado, tipo));

      await agregarAlCarrito(UUID, document);

      expect(zona().hidden).toBe(false);
      const aviso = zona().querySelector('.aviso');
      expect(aviso.classList.contains('aviso--advertencia')).toBe(true);
      // Un rechazo interrumpe: el lector de pantalla lo anuncia al momento.
      expect(aviso.getAttribute('role')).toBe('alert');
      expect(aviso.textContent).toMatch(texto);
      expect(aviso.querySelector('[data-accion="actualizar-tienda"]')).not.toBeNull();
      // Ni el código ni el identificador interno del error llegan a la pantalla.
      expect(aviso.textContent).not.toContain(String(estado));
      expect(aviso.textContent).not.toContain('urn:');
      // El carrito no cambió, así que no se vuelve a pedir.
      expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    },
  );

  test('503 catálogo caído: aviso de error con «Reintentar», que reintenta el mismo producto', async () => {
    globalThis.fetch.mockResolvedValueOnce(
      problema(503, 'urn:nexus:problema:catalogo-no-disponible'),
    );

    await agregarAlCarrito(UUID, document);

    const aviso = zona().querySelector('.aviso');
    expect(aviso.classList.contains('aviso--error')).toBe(true);
    expect(aviso.textContent).toMatch(/no puede consultar el catálogo/i);

    globalThis.fetch.mockResolvedValue(respuesta({ total: 0, items: [] }));
    aviso.querySelector('[data-accion="reintentar"]').click();
    await new Promise((r) => setTimeout(r, 0));

    const [url, opciones] = globalThis.fetch.mock.calls[1];
    expect(url).toBe('/api/v1/carrito/items');
    expect(JSON.parse(opciones.body).productoId).toBe(UUID);
    // El reintento entró: el aviso del intento anterior ya no está.
    expect(zona().hidden).toBe(true);
    expect(zona().querySelector('.aviso')).toBeNull();
  });

  test('sin respuesta (la red no contesta) también se dice, con «Reintentar»', async () => {
    globalThis.fetch.mockRejectedValue(new Error('sin red'));

    await agregarAlCarrito(UUID, document);

    const aviso = zona().querySelector('.aviso');
    expect(aviso.classList.contains('aviso--error')).toBe(true);
    expect(aviso.textContent).toMatch(/no se pudo añadir el producto al carrito/i);
    expect(aviso.querySelector('[data-accion="reintentar"]')).not.toBeNull();
  });

  test('el texto del servidor se pinta como TEXTO, nunca como marcado', async () => {
    // Un rechazo que el contrato no declara trae su `detail` para el jugador
    // (MAPEO-ERRORES §3). Va por `textContent`: con marcado dentro no ejecuta
    // nada, que es la clase de agujero que ya hubo en el interceptor (PR-UX-8).
    globalThis.fetch.mockResolvedValue(
      problema(400, 'urn:nexus:problema:otra-cosa', {
        detail: '<img src=x onerror=alert(1)> no se pudo',
      }),
    );

    await agregarAlCarrito(UUID, document);

    const aviso = zona().querySelector('.aviso');
    expect(aviso.querySelector('img')).toBeNull();
    expect(aviso.textContent).toContain('<img');
    expect(aviso.querySelector('[data-accion="actualizar-tienda"]')).not.toBeNull();
  });

  test('«Actualizar la tienda» vuelve a pedir la vitrina y retira el aviso', async () => {
    globalThis.fetch.mockResolvedValueOnce(problema(409, 'urn:nexus:problema:producto-agotado'));
    await agregarAlCarrito(UUID, document);

    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));
    zona().querySelector('[data-accion="actualizar-tienda"]').click();
    await new Promise((r) => setTimeout(r, 0));

    expect(globalThis.fetch.mock.calls[1][0]).toBe('/api/v1/vitrina?size=50');
    expect(zona().hidden).toBe(true);
  });

  test('un intento nuevo que entra no deja en pantalla el aviso del anterior', async () => {
    globalThis.fetch.mockResolvedValueOnce(problema(409, 'urn:nexus:problema:producto-agotado'));
    await agregarAlCarrito(UUID, document);
    expect(zona().hidden).toBe(false);

    globalThis.fetch.mockResolvedValue(respuesta({ total: 0, items: [] }));
    await agregarAlCarrito('11111111-2222-4333-8444-555555555555', document);

    expect(zona().hidden).toBe(true);
    expect(zona().textContent).toBe('');
  });

  test('401: la sesión ya no vale, y el aviso lleva a iniciarla otra vez', async () => {
    globalThis.fetch.mockResolvedValue(respuesta({}, false, 401));

    await agregarAlCarrito(UUID, document);

    const aviso = zona().querySelector('.aviso');
    expect(aviso.textContent).toMatch(/tu sesión ya no es válida/i);
    expect(aviso.querySelector('[data-accion="iniciar-sesion"]')).not.toBeNull();
  });

  test('403: no se apila un segundo aviso; ya lo anuncia el interceptor compartido', async () => {
    // Relojes falsos: el aviso flotante del interceptor se retira solo a los
    // 4,5 s y no se quiere dejar ese temporizador vivo al acabar la prueba.
    jest.useFakeTimers();
    try {
      globalThis.fetch.mockResolvedValue(
        problema(403, 'urn:nexus:problema:acceso-denegado', { detail: 'No tienes permiso.' }),
      );

      await agregarAlCarrito(UUID, document);

      expect(zona().hidden).toBe(true);
      expect(document.getElementById('nexus-rbac-toast')).not.toBeNull();
    } finally {
      jest.clearAllTimers();
      jest.useRealTimers();
    }
  });

  test('si la vista perdió el hueco del aviso, se crea antes que fallar en silencio', async () => {
    zona().remove();
    globalThis.fetch.mockResolvedValue(problema(409, 'urn:nexus:problema:producto-agotado'));

    await agregarAlCarrito(UUID, document);

    const creado = document.getElementById('aviso-carrito');
    expect(creado).not.toBeNull();
    expect(creado.nextElementSibling.id).toBe('cart-items');
    expect(creado.textContent).toMatch(/se agotó/i);
  });
});

/**
 * UXC-4 — buscar y filtrar, paginar, lo que ya tienes, el carrito
 * minimizado y las acciones de cada línea (§7.5 del documento).
 *
 * La vista completa: con la barra de filtros, la insignia del carrito y su
 * panel. Las pruebas de arriba siguen montando la vista mínima, que es lo
 * que protege que nada de esto sea obligatorio para que la tienda funcione.
 */
describe('UXC-4 - la tienda que pide §7.5', () => {
  const VISTA_COMPLETA = `
    <main class="main-container">
      <section class="store-section">
        <button id="insignia-carrito" type="button" aria-controls="panel-carrito">
          <span data-zona="unidades">—</span>
        </button>
        <p id="aviso-insignia" role="status"></p>
        <form id="filtros-tienda">
          <input type="search" name="busqueda" />
          <details class="filtros-tienda__mas" open>
            <summary data-zona="resumen-filtros">Filtros y orden</summary>
            <select name="tipo">
              <option value="">Todos</option>
              <option value="ARMA">Arma</option>
              <option value="ARMADURA">Armadura</option>
            </select>
            <input type="number" name="precioMinimo" />
            <input type="number" name="precioMaximo" />
            <input type="checkbox" name="soloPromocion" />
            <select name="orden">
              <option value="catalogo">Orden del catálogo</option>
              <option value="precio-asc">Precio: de menor a mayor</option>
            </select>
            <button type="reset">Limpiar filtros</button>
          </details>
        </form>
        <p id="resultado-tienda" role="status"></p>
        <div id="productos-grid"></div>
        <div id="paginacion-tienda"></div>
      </section>
      <aside id="panel-carrito">
        <h2 data-zona="titulo-carrito" tabindex="-1">Tu carrito</h2>
        <button id="minimizar-carrito" type="button">Minimizar</button>
        <div id="aviso-carrito" data-zona="aviso" hidden></div>
        <div id="cart-items"></div>
        <span id="cart-unidades"></span>
        <span id="cart-subtotal"></span>
        <span id="cart-total"></span>
        <button id="btn-pagar"></button>
      </aside>
    </main>
  `;

  const producto = (i, cambios = {}) => ({
    id: `p-${i}`,
    nombre: `Producto ${i}`,
    descripcion: 'Del catálogo',
    tipo: i % 2 === 0 ? 'ARMADURA' : 'ARMA',
    precioFinal: 1000 * i,
    precioOriginal: 1000 * i,
    moneda: 'COP',
    ...cambios,
  });

  /**
   * Un `fetch` que contesta según la ruta: la vitrina, el carrito y el
   * inventario del jugador, como los tres servicios de verdad.
   */
  function servicios({ vitrina = [], carrito = { items: [] }, inventario = [] } = {}) {
    return jest.fn(async (url, opciones = {}) => {
      if (String(url).includes('/api/v1/vitrina')) {
        return respuesta({ content: vitrina, last: true });
      }
      if (String(url).includes('/api/v1/inventario/elementos')) {
        return respuesta({ elementos: inventario, ultima: true, totalPaginas: 1 });
      }
      if (String(url).includes('/api/v1/carrito/items') && opciones.method === 'DELETE') {
        return respuesta({ items: [], total: 0 });
      }
      if (String(url).includes('/api/v1/carrito/items')) {
        return respuesta(carrito);
      }
      if (String(url).includes('/api/v1/carrito')) {
        return respuesta(carrito);
      }
      return respuesta({}, false, 404);
    });
  }

  const nombres = () =>
    Array.from(document.querySelectorAll('.product-card__nombre')).map((n) => n.textContent);
  const filtros = () => document.getElementById('filtros-tienda');
  const cambiar = (nombre, valor) => {
    const campo = filtros().elements.namedItem(nombre);
    if (campo.type === 'checkbox') {
      campo.checked = valor;
    } else {
      campo.value = valor;
    }
    campo.dispatchEvent(new Event('change', { bubbles: true }));
  };

  beforeEach(() => {
    document.body.innerHTML = VISTA_COMPLETA;
  });

  test('dieciséis por página, con el control de páginas y el total escrito', async () => {
    globalThis.fetch = servicios({
      vitrina: Array.from({ length: 20 }, (_, i) => producto(i + 1)),
    });

    await montarTienda(document);

    expect(document.querySelectorAll('.product-card')).toHaveLength(16);
    expect(document.getElementById('resultado-tienda').textContent).toBe(
      '20 productos a la venta.',
    );
    const control = document.querySelector('#paginacion-tienda .paginacion');
    expect(control.getAttribute('aria-label')).toBe('Páginas de la tienda');
    const segunda = Array.from(control.querySelectorAll('button')).find(
      (b) => b.textContent.trim() === '2',
    );
    segunda.click();
    expect(document.querySelectorAll('.product-card')).toHaveLength(4);
    expect(nombres()[0]).toBe('Producto 17');
  });

  test('buscar por nombre y por precio, filtrar por tipo y por promoción', async () => {
    globalThis.fetch = servicios({
      vitrina: [
        producto(1, { nombre: 'Espada Élfica' }),
        producto(2, { nombre: 'Coraza' }),
        producto(3, { nombre: 'Hacha', precioOriginal: 5000, precioFinal: 3000 }),
      ],
    });
    await montarTienda(document);

    cambiar('busqueda', 'elfica');
    expect(nombres()).toEqual(['Espada Élfica']);
    expect(document.getElementById('resultado-tienda').textContent).toBe(
      '1 de 3 productos coinciden.',
    );

    cambiar('busqueda', '2000');
    expect(nombres()).toEqual(['Coraza']);

    cambiar('busqueda', '');
    cambiar('tipo', 'ARMA');
    expect(nombres()).toEqual(['Espada Élfica', 'Hacha']);

    cambiar('tipo', '');
    cambiar('soloPromocion', true);
    expect(nombres()).toEqual(['Hacha']);
  });

  test('buscar y pulsar «Añadir»: salir de la búsqueda no repinta la vitrina y el clic llega', async () => {
    // El `change` de la búsqueda llega al perder el foco, justo entre el
    // `mousedown` y el `mouseup` de quien pulsa «Añadir». Repintar ahí
    // cambiaba el botón de debajo del puntero y el producto no se añadía.
    globalThis.fetch = servicios({ vitrina: [producto(1), producto(2), producto(3)] });
    await montarTienda(document);

    cambiar('busqueda', 'Producto 3');
    const boton = document.querySelector('.product-card [data-producto="p-3"]');
    expect(boton).not.toBeNull();

    // Mismo texto, otro `change` (el del blur): la tarjeta es la misma.
    filtros()
      .elements.namedItem('busqueda')
      .dispatchEvent(new Event('change', { bubbles: true }));
    expect(document.contains(boton)).toBe(true);

    boton.click();
    await new Promise((r) => setTimeout(r, 0));
    const alta = globalThis.fetch.mock.calls.find(
      ([url, opciones]) =>
        String(url).includes('/api/v1/carrito/items') && opciones?.method === 'POST',
    );
    expect(JSON.parse(alta[1].body)).toEqual({ productoId: 'p-3', cantidad: 1 });
  });

  test('buscar y pulsar «Añadir» enseguida: con el puntero pulsado la vitrina no se repinta', async () => {
    // El caso del E2E: el `change` del blur llega con la búsqueda aún sin
    // aplicar (la espera de 250 ms no ha vencido), así que los criterios SÍ
    // cambiaron. Repintar ahí se comía el clic; ahora espera a que se suelte.
    globalThis.fetch = servicios({ vitrina: [producto(1), producto(2), producto(3)] });
    await montarTienda(document);
    const boton = document.querySelector('.product-card [data-producto="p-3"]');
    filtros().elements.namedItem('busqueda').value = 'Producto 3';

    boton.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    filtros()
      .elements.namedItem('busqueda')
      .dispatchEvent(new Event('change', { bubbles: true }));
    expect(document.contains(boton)).toBe(true);
    expect(nombres()).toHaveLength(3);

    boton.dispatchEvent(new Event('pointerup', { bubbles: true }));
    boton.click();
    await new Promise((r) => setTimeout(r, 0));

    const alta = globalThis.fetch.mock.calls.find(
      ([url, opciones]) =>
        String(url).includes('/api/v1/carrito/items') && opciones?.method === 'POST',
    );
    expect(JSON.parse(alta[1].body)).toEqual({ productoId: 'p-3', cantidad: 1 });
    // Y la búsqueda se aplica al soltar: no se pierde.
    await new Promise((r) => setTimeout(r, 0));
    expect(nombres()).toEqual(['Producto 3']);
  });

  test('mismosCriterios compara lo que dicen, no la identidad del objeto', () => {
    expect(mismosCriterios({ busqueda: 'x', tipo: '' }, { busqueda: 'x', tipo: '' })).toBe(true);
    expect(mismosCriterios({ busqueda: 'x' }, { busqueda: 'y' })).toBe(false);
    expect(mismosCriterios(undefined, {})).toBe(true);
  });

  test('con los filtros recogidos se sabe cuántos hay puestos', async () => {
    globalThis.fetch = servicios({ vitrina: [producto(1), producto(2)] });
    await montarTienda(document);
    const resumen = document.querySelector('[data-zona="resumen-filtros"]');

    cambiar('tipo', 'ARMA');
    expect(resumen.textContent).toBe('Filtros y orden · 1 activo');
    cambiar('soloPromocion', true);
    expect(resumen.textContent).toBe('Filtros y orden · 2 activos');
    // La búsqueda está siempre a la vista y el orden no filtra: no cuentan.
    cambiar('soloPromocion', false);
    cambiar('orden', 'precio-asc');
    cambiar('busqueda', 'algo');
    expect(resumen.textContent).toBe('Filtros y orden · 1 activo');
  });

  test('en un teléfono los filtros empiezan recogidos; en escritorio, abiertos', async () => {
    const ancho = globalThis.innerWidth;
    const fijarAncho = (px) => {
      globalThis.innerWidth = px;
    };
    globalThis.fetch = servicios({ vitrina: [producto(1)] });
    try {
      fijarAncho(375);
      await montarTienda(document);
      expect(document.querySelector('.filtros-tienda__mas').open).toBe(false);

      document.body.innerHTML = VISTA_COMPLETA;
      fijarAncho(1440);
      await montarTienda(document);
      expect(document.querySelector('.filtros-tienda__mas').open).toBe(true);
    } finally {
      fijarAncho(ancho);
    }
  });

  test('nada coincide: se dice, con «Limpiar filtros», que deja todo como estaba', async () => {
    globalThis.fetch = servicios({ vitrina: [producto(1), producto(2)] });
    await montarTienda(document);

    cambiar('busqueda', 'dragón');
    const vacio = document.querySelector('#productos-grid [data-estado="vacio"]');
    expect(vacio.textContent).toMatch(/Ningún producto coincide/);
    expect(vacio.textContent).not.toMatch(/no tiene productos/);

    vacio.querySelector('[data-accion="limpiar-filtros"]').click();
    expect(nombres()).toHaveLength(2);
    expect(filtros().elements.namedItem('busqueda').value).toBe('');
  });

  test('un rango de precio al revés se explica', async () => {
    globalThis.fetch = servicios({ vitrina: [producto(1)] });
    await montarTienda(document);

    filtros().elements.namedItem('precioMinimo').value = '5000';
    cambiar('precioMaximo', '1000');

    expect(document.getElementById('productos-grid').textContent).toMatch(
      /El precio mínimo es mayor que el máximo/,
    );
  });

  test('leerCriterios: lo vacío es «sin filtro», nunca un cero', () => {
    const criterios = leerCriterios(filtros());

    expect(criterios.precioMinimo).toBeNull();
    expect(criterios.precioMaximo).toBeNull();
    expect(criterios.soloPromocion).toBe(false);
    expect(criterios.orden).toBe('catalogo');
  });

  test('lo que ya tienes se marca con lo que dice tu inventario', async () => {
    globalThis.fetch = servicios({
      vitrina: [producto(1), producto(2)],
      inventario: [
        { id: 'e1', productoId: 'p-2' },
        { id: 'e2', productoId: 'p-2' },
      ],
    });

    await montarTienda(document);

    const tarjeta = document.querySelector('[data-id-producto="p-2"]');
    expect(tarjeta.dataset.propio).toBe('si');
    expect(tarjeta.querySelector('.producto-propio').textContent).toBe('Tienes 2');
    expect(document.querySelector('[data-id-producto="p-1"] .producto-propio')).toBeNull();
    // El inventario viaja con la identidad en su cabecera, no en la ruta.
    const [, opciones] = globalThis.fetch.mock.calls.find(([url]) =>
      String(url).includes('/inventario/elementos'),
    );
    expect(opciones.headers['X-User-Name']).toBe(UID);
  });

  test('la insignia cuenta las unidades; cada línea suma una o se quita', async () => {
    const carrito = {
      total: 3000,
      items: [
        {
          id: 7,
          cantidad: 2,
          precioUnitario: 1000,
          subtotal: 2000,
          producto: { id: 'p-1', nombre: 'Producto 1', moneda: 'COP' },
        },
        {
          id: 8,
          cantidad: 1,
          precioUnitario: 1000,
          subtotal: 1000,
          producto: { id: 'p-2', nombre: 'Producto 2', moneda: 'COP' },
        },
      ],
    };
    globalThis.fetch = servicios({ vitrina: [producto(1)], carrito });
    await montarTienda(document);

    expect(document.querySelector('#insignia-carrito [data-zona="unidades"]').textContent).toBe(
      '3',
    );
    expect(document.getElementById('cart-unidades').textContent).toBe('3 productos');
    expect(document.querySelector('[data-item-id="7"]').textContent).toContain('1.000 COP c/u');

    globalThis.fetch.mockClear();
    document.querySelector('[data-sumar-item="p-1"]').click();
    await new Promise((r) => setTimeout(r, 0));
    const [url, opciones] = globalThis.fetch.mock.calls[0];
    expect(url).toBe('/api/v1/carrito/items');
    expect(JSON.parse(opciones.body)).toEqual({ productoId: 'p-1', cantidad: 1 });

    globalThis.fetch.mockClear();
    document.querySelector('[data-quitar-item="8"]').click();
    await new Promise((r) => setTimeout(r, 0));
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/carrito/items/8');
    expect(globalThis.fetch.mock.calls[0][1].method).toBe('DELETE');
    expect(document.getElementById('cart-items').textContent).toMatch(/vacío/);
    expect(document.getElementById('aviso-insignia').textContent).toBe('Quitado del carrito.');
  });

  test('quitar que falla: el producto sigue, se dice y se reintenta', async () => {
    document.body.innerHTML = VISTA_COMPLETA;
    globalThis.fetch = jest.fn().mockResolvedValue(respuesta({}, false, 500));

    const quitado = await quitarDelCarrito(8, document);

    expect(quitado).toBe(false);
    const aviso = document.querySelector('#aviso-carrito .aviso');
    expect(aviso.textContent).toMatch(/No se pudo quitar el producto/);
    expect(aviso.textContent).toMatch(/Sigue en tu carrito/);
    expect(aviso.querySelector('[data-accion="reintentar-quitar"]')).not.toBeNull();
  });

  test('con el carrito minimizado, un «Añadir» rechazado lo despliega para que se lea', async () => {
    globalThis.fetch = servicios({ vitrina: [producto(1)] });
    await montarTienda(document);
    document.getElementById('minimizar-carrito').click();
    expect(document.getElementById('panel-carrito').hidden).toBe(true);

    globalThis.fetch = jest
      .fn()
      .mockResolvedValue(problema(409, 'urn:nexus:problema:producto-agotado'));
    const resultado = await agregarAlCarrito('p-1', document);

    expect(resultado).toEqual(
      expect.objectContaining({ ok: false, titulo: 'Ese producto se agotó' }),
    );
    expect(document.getElementById('panel-carrito').hidden).toBe(false);
    expect(document.querySelector('#aviso-carrito').textContent).toMatch(/se agotó/);
  });

  test('añadir con el carrito minimizado: se queda recogido y la insignia lo dice', async () => {
    globalThis.fetch = servicios({
      vitrina: [producto(1)],
      carrito: {
        total: 1000,
        items: [{ id: 1, cantidad: 1, producto: { id: 'p-1', nombre: 'P', moneda: 'COP' } }],
      },
    });
    await montarTienda(document);
    document.getElementById('minimizar-carrito').click();

    const resultado = await agregarAlCarrito('p-1', document);

    expect(resultado).toEqual({ ok: true });
    expect(document.getElementById('panel-carrito').hidden).toBe(true);
    expect(document.getElementById('aviso-insignia').textContent).toBe('Añadido al carrito.');
  });
});
