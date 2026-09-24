/**
 * Vitrina, carrito y pago — HU-CAR-001 y HU-CAR-010.
 *
 * Lo que se prueba es lo que estaba roto: la identidad que viaja en cada
 * petición, la base de la API, y que un fallo del carrito no se disfrace de
 * carrito vacío. Para el pago: resumen antes de pagar, datos inválidos,
 * rechazo de la pasarela, sesión expirada y que la tarjeta no se quede en
 * ninguna parte.
 */

import { jest } from '@jest/globals';

import {
  haySesion,
  cargarVitrina,
  cargarCarrito,
  agregarAlCarrito,
  actualizarUI,
  montarTienda,
  abrirCheckout,
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

  test('sin sesion no viaja ninguna identidad: el backend respondera 401', async () => {
    sessionStorage.clear();
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));

    await cargarVitrina(document);

    const cabeceras = globalThis.fetch.mock.calls[0][1].headers;
    expect(cabeceras['X-User-Id']).toBeUndefined();
    expect(cabeceras.Authorization).toBeUndefined();
  });
});

describe('base de la API', () => {
  test('mismo origen por omision, con el prefijo de version', async () => {
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));

    await cargarVitrina(document);

    // R16 — la vitrina tiene prefijo propio. `/api/v1/productos` es del
    // catálogo maestro entero desde que el borde dejó de repartirlo por
    // método (#421): pedirlo ahí sería pedirle la vitrina a otro servicio.
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/vitrina');
  });

  test('con meta declarada, la base la manda la pagina', async () => {
    // La cabecera se declara ANTES de esperar nada, y `beforeEach` la limpia:
    // tocarla despues de un await es lo que ESLint marca como carrera.
    document.head.innerHTML = '<meta name="nexus-api-base" content="http://127.0.0.1:8083/" />';
    globalThis.fetch.mockResolvedValue(respuesta({ content: [] }));

    await cargarVitrina(document);

    expect(globalThis.fetch.mock.calls[0][0]).toBe('http://127.0.0.1:8083/api/v1/vitrina');
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

describe('resumen y pago — HU-CAR-010', () => {
  // Forma del contrato 1.2.0: el carrito trae `id` entero y su `moneda`.
  const CARRITO = {
    id: 7,
    total: 250,
    moneda: 'COP',
    items: [{ cantidad: 2, subtotal: 250, producto: { nombre: 'Poción' } }],
  };

  // Nombres de los campos tal como los crea `campo()` en el dialogo.
  // Vencimiento lejano para que la prueba no caduque con el calendario.
  const TARJETA = {
    titular: 'Ana Pérez',
    numero: '4111 1111 1111 1111',
    vencimiento: '12/30',
    cvv: '123',
  };

  /** Cuerpo del 200 de POST /checkout: la forma de la respuesta de ms-finanzas. */
  function resultadoDePago(estado, mensaje, marcadoParaRevisionManual = false) {
    return respuesta({
      refId: 'ref-1',
      estado,
      aprobado: estado === 'APROBADO',
      mensaje,
      marcadoParaRevisionManual,
    });
  }

  let dialogo = null;

  /** Deja correr las promesas encadenadas del envio (fetch → json → recarga). */
  const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

  function abrir(carrito = CARRITO) {
    actualizarUI(carrito, document);
    dialogo = abrirCheckout(document);
    return dialogo?.elemento ?? null;
  }

  function control(caja, nombre) {
    return caja.querySelector(`[name="${nombre}"]`);
  }

  function mensaje(caja) {
    return caja.querySelector('[role="status"]');
  }

  async function pagarCon(caja, datos = TARJETA) {
    for (const [nombre, valor] of Object.entries(datos)) {
      control(caja, nombre).value = valor;
    }
    caja
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await esperar();
    await esperar();
  }

  beforeEach(() => {
    // Los fallos simulados escriben en consola; aqui ademas se inspecciona.
    jest.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    // El dialogo escucha el teclado en `document`: sin cerrarlo, la escucha
    // se quedaria viva para la prueba siguiente.
    dialogo?.cerrar();
    dialogo = null;
    console.error.mockRestore();
  });

  test('PAGAR abre el resumen con el detalle y el total antes de pagar', async () => {
    globalThis.fetch.mockResolvedValue(respuesta({ content: [], ...CARRITO }));
    await montarTienda(document);

    document.getElementById('btn-pagar').click();

    const caja = document.querySelector('[role="dialog"]');
    expect(caja).not.toBeNull();
    expect(caja.textContent).toContain('Poción');
    expect(caja.textContent).toContain('x2');
    expect(caja.textContent).toContain('250 COP');

    caja.querySelector('[data-accion="cerrar"]').click();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });

  test('sin productos en el carro no se abre el pago', () => {
    expect(abrir(null)).toBeNull();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });

  test('datos invalidos: no se llama a la pasarela y se marca cada campo', async () => {
    const caja = abrir();

    await pagarCon(caja, { ...TARJETA, numero: '1234', cvv: '1' });

    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(control(caja, 'numero').getAttribute('aria-invalid')).toBe('true');
    expect(control(caja, 'cvv').getAttribute('aria-invalid')).toBe('true');
    expect(control(caja, 'titular').getAttribute('aria-invalid')).toBe('false');
    expect(mensaje(caja).hidden).toBe(false);
    expect(mensaje(caja).dataset.resultado).toBe('rechazado');
  });

  test('aprobado: envia al checkout con el Bearer, avisa y vacia el carro', async () => {
    globalThis.fetch
      .mockResolvedValueOnce(resultadoDePago('APROBADO', 'Pago aprobado'))
      .mockResolvedValueOnce(respuesta({ total: 0, items: [] }));
    const caja = abrir();

    await pagarCon(caja);

    const [url, opciones] = globalThis.fetch.mock.calls[0];
    expect(url).toBe('/api/v1/checkout');
    expect(opciones.headers.Authorization).toBe(`Bearer ${sessionStorage.getItem('nexus.token')}`);
    expect(opciones.headers['X-User-Id']).toBeUndefined();
    expect(JSON.parse(opciones.body)).toEqual({
      carritoId: 7,
      tarjeta: {
        titular: 'Ana Pérez',
        numero: '4111111111111111',
        fechaExpiracion: '12/30',
        cvv: '123',
      },
    });

    expect(mensaje(caja).dataset.resultado).toBe('aprobado');
    expect(mensaje(caja).textContent).toMatch(/aprobado/i);
    // Aprobado el pago no queda formulario con datos de tarjeta en la pagina.
    expect(caja.querySelector('form')).toBeNull();
    expect(document.getElementById('cart-items').textContent).toMatch(/vacío/i);
    expect(document.getElementById('btn-pagar').disabled).toBe(true);
  });

  test('aprobado con revision manual: se da por pagado y se avisa de la revision', async () => {
    globalThis.fetch
      .mockResolvedValueOnce(resultadoDePago('APROBADO', 'Pago aprobado', true))
      .mockResolvedValueOnce(respuesta({ total: 0, items: [] }));
    const caja = abrir();

    await pagarCon(caja);

    expect(mensaje(caja).dataset.resultado).toBe('aprobado');
    expect(mensaje(caja).textContent).toMatch(/revisión de rutina/i);
    expect(caja.querySelector('form')).toBeNull();
  });

  test('rechazo de la pasarela: se muestra el motivo y se borran numero y CVV', async () => {
    globalThis.fetch.mockResolvedValueOnce(resultadoDePago('RECHAZADO', 'Fondos insuficientes'));
    const caja = abrir();

    await pagarCon(caja);

    expect(mensaje(caja).dataset.resultado).toBe('rechazado');
    expect(mensaje(caja).textContent).toBe('Fondos insuficientes');
    expect(control(caja, 'numero').value).toBe('');
    expect(control(caja, 'cvv').value).toBe('');
    // El titular se conserva para reintentar sin volver a escribirlo todo.
    expect(control(caja, 'titular').value).toBe('Ana Pérez');
    // Rechazado no se recarga el carrito: los productos siguen ahi.
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  });

  test('indeterminado: queda en revision, sin reintento y sin volver a pagar', async () => {
    globalThis.fetch.mockResolvedValueOnce(resultadoDePago('INDETERMINADO', 'En conciliación.'));
    const caja = abrir();

    await pagarCon(caja);

    expect(mensaje(caja).dataset.resultado).toBe('revision');
    expect(mensaje(caja).textContent).toContain('En conciliación.');
    expect(mensaje(caja).textContent).toMatch(/no vuelvas a pagar/i);
    // Sin formulario no hay reintento desde el dialogo...
    expect(caja.querySelector('form')).toBeNull();
    // ...ni desde el carrito, que sigue con sus productos pero sin «PAGAR».
    expect(document.getElementById('btn-pagar').disabled).toBe(true);
    expect(document.getElementById('cart-items').textContent).toContain('Poción');
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  });

  test('un estado que no se entiende no se da por aprobado', async () => {
    globalThis.fetch.mockResolvedValueOnce(respuesta({ refId: 'ref-1', mensaje: '' }));
    const caja = abrir();

    await pagarCon(caja);

    expect(mensaje(caja).dataset.resultado).toBe('revision');
    expect(mensaje(caja).textContent).not.toMatch(/aprobado/i);
  });

  test('un 503 de ms-finanzas NO es un pago aprobado', async () => {
    // El defecto que destapo R16: el interceptor devuelve el 503 tal cual, y
    // su cuerpo sin `aprobado: false` se tomaba por un pago bueno.
    globalThis.fetch.mockResolvedValueOnce(
      respuesta({ mensaje: 'No pudimos procesar el pago en este momento.' }, false, 503),
    );
    const caja = abrir();

    await pagarCon(caja);

    expect(mensaje(caja).dataset.resultado).toBe('rechazado');
    expect(mensaje(caja).textContent).toMatch(/no pudimos procesar el pago/i);
    expect(mensaje(caja).textContent).not.toMatch(/aprobado/i);
    // El formulario sigue: se puede intentar otra vez, y el carrito no se tocó.
    expect(caja.querySelector('form')).not.toBeNull();
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  });

  test('un 409 dice lo que paso con el carrito', async () => {
    globalThis.fetch.mockResolvedValueOnce(
      respuesta({ mensaje: 'Tu carrito está vacío.' }, false, 409),
    );
    const caja = abrir();

    await pagarCon(caja);

    expect(mensaje(caja).textContent).toBe('Tu carrito está vacío.');
  });

  test('datos rechazados por el servidor (400) piden revisar la tarjeta', async () => {
    globalThis.fetch.mockResolvedValueOnce(respuesta({}, false, 400));
    const caja = abrir();

    await pagarCon(caja);

    expect(mensaje(caja).textContent).toMatch(/revisa los datos de la tarjeta/i);
  });

  test('sesion expirada durante el pago (401): se dice, y que no se cobro', async () => {
    globalThis.fetch.mockResolvedValueOnce(respuesta({}, false, 401));
    const caja = abrir();

    await pagarCon(caja);

    expect(mensaje(caja).textContent).toMatch(/sesión expiró/i);
    expect(mensaje(caja).textContent).toMatch(/no se procesó/i);
    expect(control(caja, 'numero').value).toBe('');
  });

  test('sin respuesta del servidor: se dice que falló la conexión', async () => {
    globalThis.fetch.mockRejectedValueOnce(new Error('sin red'));
    const caja = abrir();

    await pagarCon(caja);

    expect(mensaje(caja).textContent).toMatch(/conexión/i);
    expect(caja.querySelector('form')).not.toBeNull();
  });

  test('los datos de la tarjeta nunca se escriben en consola', async () => {
    globalThis.fetch.mockResolvedValueOnce(respuesta({ mensaje: 'caido' }, false, 500));
    const caja = abrir();

    await pagarCon(caja);

    const escrito = console.error.mock.calls
      .flat()
      .map((arg) => (arg instanceof Error ? arg.message : JSON.stringify(arg)))
      .join(' ');
    expect(escrito).not.toContain('4111');
    expect(escrito).not.toContain('Ana Pérez');
  });

  test('al cerrar el dialogo sus campos desaparecen de la pagina', () => {
    const caja = abrir();
    control(caja, 'numero').value = '4111 1111 1111 1111';

    dialogo.cerrar();
    dialogo = null;

    expect(document.querySelector('[name="numero"]')).toBeNull();
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

    expect(globalThis.fetch.mock.calls[1][0]).toBe('/api/v1/vitrina');
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
