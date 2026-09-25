/**
 * UXC-4 — el carrito desplegado y minimizado: la insignia y el panel.
 */

import {
  CLAVE_PANEL,
  ESTADOS_DEL_PANEL,
  montarCajonDelCarrito,
  pintarInsignia,
  textoDeUnidades,
  unidadesDelCarrito,
} from './tienda-carrito.js';

const VISTA = `
  <main class="main-container">
    <section class="store-section">
      <button id="insignia-carrito" type="button" aria-controls="panel-carrito">
        <span data-zona="unidades">—</span>
      </button>
    </section>
    <aside id="panel-carrito">
      <h2 data-zona="titulo-carrito" tabindex="-1">Tu carrito</h2>
      <button id="minimizar-carrito" type="button">Minimizar</button>
    </aside>
  </main>
`;

function almacenDeMemoria(inicial = {}) {
  const datos = new Map(Object.entries(inicial));
  return {
    getItem: (clave) => (datos.has(clave) ? datos.get(clave) : null),
    setItem: (clave, valor) => datos.set(clave, valor),
    datos,
  };
}

beforeEach(() => {
  document.body.innerHTML = VISTA;
});

describe('unidades e insignia', () => {
  test('suma las cantidades; lo que no es una cantidad no suma', () => {
    expect(unidadesDelCarrito({ items: [{ cantidad: 2 }, { cantidad: 1 }, {}] })).toBe(3);
    expect(unidadesDelCarrito(null)).toBe(0);
    expect(textoDeUnidades(1)).toBe('1 producto');
    expect(textoDeUnidades(0)).toBe('0 productos');
  });

  test('la insignia dice el número y su nombre accesible lo repite', () => {
    const insignia = document.getElementById('insignia-carrito');

    pintarInsignia(insignia, 3);
    expect(insignia.querySelector('[data-zona="unidades"]').textContent).toBe('3');
    expect(insignia.getAttribute('aria-label')).toBe('Carrito, 3 productos');

    pintarInsignia(insignia, null);
    expect(insignia.querySelector('[data-zona="unidades"]').textContent).toBe('—');
    expect(insignia.getAttribute('aria-label')).toBe('Carrito');
  });
});

describe('panel desplegado y minimizado', () => {
  test('de entrada desplegado, con aria-expanded', () => {
    const cajon = montarCajonDelCarrito(document, { almacen: almacenDeMemoria() });

    expect(cajon.desplegado()).toBe(true);
    expect(document.getElementById('panel-carrito').hidden).toBe(false);
    expect(document.getElementById('insignia-carrito').getAttribute('aria-expanded')).toBe('true');
  });

  test('minimizar recoge el panel, devuelve el foco a la insignia y se recuerda', () => {
    const almacen = almacenDeMemoria();
    const cajon = montarCajonDelCarrito(document, { almacen });

    document.getElementById('minimizar-carrito').click();

    expect(cajon.desplegado()).toBe(false);
    expect(document.querySelector('.main-container').dataset.carrito).toBe(
      ESTADOS_DEL_PANEL.MINIMIZADO,
    );
    expect(document.getElementById('panel-carrito').hidden).toBe(true);
    expect(document.activeElement).toBe(document.getElementById('insignia-carrito'));
    expect(almacen.datos.get(CLAVE_PANEL)).toBe(ESTADOS_DEL_PANEL.MINIMIZADO);
  });

  test('la insignia lo vuelve a desplegar y lleva el foco al título del panel', () => {
    const cajon = montarCajonDelCarrito(document, { almacen: almacenDeMemoria() });
    const insignia = document.getElementById('insignia-carrito');

    insignia.click();
    expect(cajon.desplegado()).toBe(false);
    insignia.click();

    expect(cajon.desplegado()).toBe(true);
    expect(insignia.getAttribute('aria-expanded')).toBe('true');
    expect(document.activeElement).toBe(document.querySelector('[data-zona="titulo-carrito"]'));
  });

  test('se abre como se dejó', () => {
    const cajon = montarCajonDelCarrito(document, {
      almacen: almacenDeMemoria({ [CLAVE_PANEL]: ESTADOS_DEL_PANEL.MINIMIZADO }),
    });

    expect(cajon.desplegado()).toBe(false);
  });

  test('sin almacenamiento (bloqueado o privado) funciona igual, sin recordar', () => {
    const roto = {
      getItem: () => {
        throw new Error('SecurityError');
      },
      setItem: () => {
        throw new Error('SecurityError');
      },
    };
    const cajon = montarCajonDelCarrito(document, { almacen: roto });

    expect(cajon.desplegado()).toBe(true);
    document.getElementById('minimizar-carrito').click();
    expect(cajon.desplegado()).toBe(false);
  });

  test('una vista sin insignia ni panel no se rompe', () => {
    document.body.innerHTML = '<div id="cart-items"></div>';

    expect(montarCajonDelCarrito(document)).toBeNull();
  });
});
