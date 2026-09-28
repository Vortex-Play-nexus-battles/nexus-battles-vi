/**
 * Vitrina de subastas — HU-SUB-011 (UX-R2.8b).
 *
 * Esta vista no tenia ninguna prueba, y era la que el laboratorio visual
 * (#600) marcaba con `error-tecnico-visible` en **las cinco anchuras**: con
 * el mercado caido, la pantalla del jugador decia literalmente «Error 404».
 *
 * De ahi el orden de lo que se comprueba aqui: primero que un servicio caido
 * no se confunda con un mercado vacio, y solo despues lo demas.
 */

import { jest } from '@jest/globals';

import { inicializar, rutaDePujas } from './subastas.js';

const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

const HORA = 3_600_000;

/** Una subasta con la forma de `SubastaResumen` del contrato. */
function subasta(extra = {}) {
  return {
    id: 's-1',
    nombreProducto: 'Espada de Vael',
    tipoProducto: 'ARMA',
    rareza: 'Rara',
    ofertaVigente: 1200,
    cantidadPujas: 3,
    fechaFin: new Date(Date.now() + 2 * HORA).toISOString(),
    esMaestroDeJuego: false,
    ...extra,
  };
}

/** Respuesta del listado con las subastas que se le pasen. */
function listado(contenido, extra = {}) {
  return { contenido, pagina: 0, totalPaginas: 1, totalElementos: contenido.length, ...extra };
}

function responder(cuerpo, estado = 200) {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    json: async () => cuerpo,
  };
}

function montar() {
  document.body.innerHTML = '<div id="raiz-subastas"></div>';
  inicializar();
  return asentar();
}

const zona = () => document.getElementById('subastas-resultados');
const texto = () => document.body.textContent;

beforeEach(() => {
  globalThis.fetch = jest.fn(async () => responder(listado([])));
  // La cabecera compartida mira la sesion; sin ella pinta la version anonima,
  // que es un estado valido de esta pantalla (subasta es publica).
  globalThis.localStorage?.clear?.();
});

afterEach(() => {
  jest.restoreAllMocks();
  document.body.innerHTML = '';
});

describe('cuando el mercado no responde', () => {
  test('no aparece ningún código HTTP en la pantalla', async () => {
    globalThis.fetch = jest.fn(async () => responder({ detail: null }, 502));
    await montar();

    // El defecto original, exactamente: el patron que buscaba el laboratorio.
    expect(texto()).not.toMatch(/\b(?:Error|HTTP|[Ss]tatus|[Cc]ódigo)\s*:?\s*\d{3}\b/);
    expect(texto()).not.toMatch(/\b502\b/);
  });

  test('se dice que es el servicio, no que no haya subastas', async () => {
    globalThis.fetch = jest.fn(async () => responder({ detail: null }, 502));
    await montar();

    expect(zona().querySelector('[data-estado="error"]')).not.toBeNull();
    expect(zona().querySelector('[data-estado="vacio"]')).toBeNull();
    expect(texto()).toContain('El mercado no responde');
  });

  test('hay un botón de reintentar, y reintenta de verdad', async () => {
    let fallar = true;
    globalThis.fetch = jest.fn(async () =>
      fallar ? responder({ detail: null }, 502) : responder(listado([subasta()])),
    );
    await montar();

    const reintentar = zona().querySelector('[data-accion="reintentar"]');
    expect(reintentar).not.toBeNull();

    fallar = false;
    reintentar.click();
    await asentar();

    expect(zona().querySelector('.subastas__producto')).not.toBeNull();
  });

  test('un fallo de red no enseña «Failed to fetch»', async () => {
    // Un TypeError de `fetch` no trae `estado`, asi que su mensaje es tecnico
    // y no puede llegar a la pantalla.
    globalThis.fetch = jest.fn(async () => {
      throw new TypeError('Failed to fetch');
    });
    await montar();

    expect(texto()).not.toContain('Failed to fetch');
    expect(texto()).toContain('Revisa tu conexión');
  });

  test('si la sesión no alcanza, el título no culpa al mercado', async () => {
    globalThis.fetch = jest.fn(async () => responder({ detail: 'Tu sesión no alcanza.' }, 403));
    await montar();

    expect(texto()).toContain('No podemos mostrarte las subastas');
    expect(texto()).not.toContain('El mercado no responde');
  });
});

describe('cuando no hay resultados', () => {
  test('sin filtros invita a publicar, sin inventarse un motivo', async () => {
    await montar();

    const vacio = zona().querySelector('[data-estado="vacio"]');
    expect(vacio).not.toBeNull();
    expect(vacio.textContent).toContain('Todavía no hay subastas activas');
    expect(vacio.querySelector('a[href="./publicar-subasta.html"]')).not.toBeNull();
  });

  test('con búsqueda puesta, ofrece quitar los filtros', async () => {
    await montar();

    const campo = document.querySelector('.subastas-busqueda__campo');
    campo.value = 'hacha';
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    await asentar();

    const vacio = zona().querySelector('[data-estado="vacio"]');
    expect(vacio.textContent).toContain('Ninguna subasta coincide');
    expect(vacio.textContent).toContain('Quitar los filtros');
  });
});

describe('cuando hay subastas', () => {
  test('el contador de cada lote late: no se queda congelado', async () => {
    // Era el defecto: `subastas-vitrina.js` calcula el tiempo restante al
    // pintar y su propio comentario deja dicho que ponerlo al dia le toca a
    // quien llama. Nadie lo hacia.
    globalThis.fetch = jest.fn(async () => responder(listado([subasta()])));
    await montar();

    const contador = document.querySelector('.subastas__contador');
    expect(contador.className).toContain('cuenta-atras');
    expect(contador.dataset.terminaEn).toBeDefined();
    expect(contador.getAttribute('aria-label')).toContain('Termina en');
  });

  test('un lote ya vencido dice «Finalizada», no un número negativo', async () => {
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta({ fechaFin: new Date(Date.now() - HORA).toISOString() })])),
    );
    await montar();

    const contador = document.querySelector('.subastas__contador');
    expect(contador.textContent).toBe('Finalizada');
    expect(contador.textContent).not.toMatch(/-\d/);
  });

  test('«Comprar ahora» sale solo si la subasta lo admite, y lleva a confirmarlo', async () => {
    // UXC-8 — la vitrina sabía pintar el botón, pero la pantalla no le pasaba
    // el manejador: nunca aparecía. Ahora lleva a la sala de pujas con la
    // confirmación abierta, que es donde se ve precio, saldo y objeto.
    globalThis.fetch = jest.fn(async () =>
      responder(
        listado([
          subasta({ id: 'con', precioCompraInmediata: 2800 }),
          subasta({ id: 'sin', precioCompraInmediata: null }),
        ]),
      ),
    );
    await montar();

    const botones = [...document.querySelectorAll('.subastas__comprar-ahora')];
    expect(botones).toHaveLength(1);
    expect(botones[0].textContent).toContain('Comprar ahora');
    expect(rutaDePujas('con', { comprar: true })).toBe('./pujas.html?id=con&accion=comprar');
    expect(rutaDePujas('sin')).toBe('./pujas.html?id=sin');
  });

  test('la paginacion usa el componente del kit, no una copia local', async () => {
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta()], { totalPaginas: 3, pagina: 0 })),
    );
    await montar();

    expect(zona().querySelector('nav.paginacion')).not.toBeNull();
    expect(zona().querySelector('.subastas-paginacion')).toBeNull();
    // El activo se marca con aria-current, que es lo que estiliza el kit.
    expect(zona().querySelector('.paginacion__pagina[aria-current="page"]').textContent).toBe('1');
  });

  test('«Anterior» esta deshabilitado en la primera pagina', async () => {
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta()], { totalPaginas: 3, pagina: 0 })),
    );
    await montar();

    const [anterior] = [...zona().querySelectorAll('.paginacion__pagina')];
    expect(anterior.textContent).toBe('Anterior');
    expect(anterior.disabled).toBe(true);
  });
});

describe('estructura de la pantalla', () => {
  test('el título y «Publicar subasta» van en el encabezado del kit', async () => {
    await montar();

    const encabezado = document.querySelector('.encabezado-pagina');
    expect(encabezado).not.toBeNull();
    expect(encabezado.querySelector('h1').textContent).toBe('Subastas activas');
    expect(encabezado.querySelector('[data-zona="acciones-pagina"] a').textContent).toBe(
      'Publicar subasta',
    );
  });

  test('mientras carga se ve la forma de lo que viene, no una página en blanco', async () => {
    let resolver;
    globalThis.fetch = jest.fn(() => new Promise((r) => (resolver = r)));
    document.body.innerHTML = '<div id="raiz-subastas"></div>';
    inicializar();

    expect(zona().querySelector('[data-estado="cargando"]')).not.toBeNull();

    resolver(responder(listado([])));
    await asentar();
  });
});

describe('el panel de filtros no tapa el mercado en telefono — UX-R4.8', () => {
  const panel = () => document.querySelector('.subastas-filtros__plegable');
  const resumen = () => document.querySelector('.subastas-filtros__resumen');

  test('los filtros viven dentro de un desplegable, con su resumen delante', async () => {
    await montar();

    expect(panel()).not.toBeNull();
    expect(panel().tagName).toBe('DETAILS');
    // El resumen tiene que ser el PRIMER hijo o el navegador no lo trata como
    // el control que abre y cierra.
    expect(panel().firstElementChild).toBe(resumen());
    expect(resumen().tagName).toBe('SUMMARY');
    expect(panel().querySelector('.subastas-filtros')).not.toBeNull();
  });

  test('los resultados van DESPUES del panel, no dentro', async () => {
    await montar();

    expect(panel().contains(zona())).toBe(false);
    expect(panel().nextElementSibling).toBe(zona());
  });

  test('sin filtros puestos el resumen no promete nada', async () => {
    await montar();

    expect(resumen().textContent).toBe('Filtros');
  });

  test('con filtros puestos el resumen dice cuantos, aunque este plegado', async () => {
    await montar();

    const casilla = document.querySelector('input[name="tipoProducto"]');
    casilla.checked = true;
    casilla.dispatchEvent(new Event('change', { bubbles: true }));
    await asentar();

    // Un panel plegado que esconde filtros activos deja a alguien mirando
    // «ninguna subasta coincide» sin saber por que.
    expect(resumen().textContent).toBe('Filtros · 1 activos');
  });
});
