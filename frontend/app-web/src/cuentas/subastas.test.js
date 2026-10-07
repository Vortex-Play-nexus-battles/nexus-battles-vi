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

import { CANAL_DEL_LISTADO, TAMANOS_DE_PAGINA, inicializar, rutaDePujas } from './subastas.js';

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

function montar(opciones = { conectarCanal: null }) {
  document.body.innerHTML = '<div id="raiz-subastas"></div>';
  inicializar(opciones);
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
  // PLAYER-07b — algunas pruebas simulan el ancho de la pantalla; jsdom no
  // trae `matchMedia` y la siguiente prueba tiene que empezar sin él.
  delete globalThis.matchMedia;
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
    // PLAYER-07b — y es el control COMPARTIDO, con su nombre de lo que pagina.
    expect(zona().querySelector('nav.paginacion').getAttribute('aria-label')).toBe(
      'Páginas de subastas',
    );
  });

  test('PLAYER-07b: la flecha «Página anterior» está apagada en la primera página', async () => {
    // Era «Anterior» escrito en una casilla de 32 px: el texto se salía de la
    // casilla («sale el anterior y siguiente desbordados»). Ahora es una
    // flecha con nombre accesible, apagada en el extremo y sin desaparecer.
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta()], { totalPaginas: 3, pagina: 0 })),
    );
    await montar();

    const anterior = zona().querySelector('.paginacion__pagina[data-direccion="anterior"]');
    const siguiente = zona().querySelector('.paginacion__pagina[data-direccion="siguiente"]');
    expect(anterior.getAttribute('aria-label')).toBe('Página anterior');
    expect(anterior.disabled).toBe(true);
    expect(siguiente.getAttribute('aria-label')).toBe('Página siguiente');
    expect(siguiente.disabled).toBe(false);
    expect(zona().querySelector('nav.paginacion').textContent).not.toMatch(/Anterior|Siguiente/);
  });

  test('PLAYER-07b: la flecha «Página siguiente» pide la página de al lado con los mismos filtros', async () => {
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta()], { totalPaginas: 3, pagina: 0 })),
    );
    await montar();
    document.querySelector('#subastas-ordenar').value = 'PRECIO_ASC';
    document.querySelector('#subastas-ordenar').dispatchEvent(new Event('change'));
    await asentar();

    zona().querySelector('.paginacion__pagina[data-direccion="siguiente"]').click();
    await asentar();

    const url = String(globalThis.fetch.mock.calls.at(-1)[0]);
    expect(url).toContain('page=1');
    expect(url).toContain('ordenarPor=PRECIO_ASC');
  });

  test('PLAYER-07b: con una sola página no se pinta paginación', async () => {
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta()], { totalPaginas: 1, pagina: 0 })),
    );
    await montar();

    expect(zona().querySelector('nav.paginacion').hidden).toBe(true);
  });
});

describe('PLAYER-07b — las imágenes de la vitrina', () => {
  test('una imagen que no carga se cambia por el símbolo del tipo', async () => {
    // En DEV hay productos con «espada.png», una ruta que no sirve nadie: el
    // navegador pintaba su icono de imagen rota con el nombre encima.
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta({ miniaturaUrl: 'espada.png' })])),
    );
    await montar();

    const miniatura = zona().querySelector('.subastas__miniatura');
    const imagen = miniatura.querySelector('img');
    expect(imagen.getAttribute('src')).toBe('espada.png');
    // El nombre ya está escrito debajo: la imagen no lo repite.
    expect(imagen.alt).toBe('');

    imagen.dispatchEvent(new Event('error'));

    expect(miniatura.dataset.imagen).toBe('rota');
    expect(miniatura.querySelector('img')).toBeNull();
    expect(miniatura.querySelector('svg.subastas__simbolo')).not.toBeNull();
    expect(miniatura.querySelector('svg').getAttribute('aria-hidden')).toBe('true');
  });

  test('sin imagen, la ranura lleva el símbolo del tipo y no se queda vacía', async () => {
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta({ miniaturaUrl: null, tipoProducto: 'ARMADURA' })])),
    );
    await montar();

    const miniatura = zona().querySelector('.subastas__miniatura');
    expect(miniatura.dataset.imagen).toBe('no');
    expect(miniatura.dataset.tipo).toBe('ARMADURA');
    expect(miniatura.querySelector('use').getAttribute('href')).toMatch(/#escudo$/);
  });

  test('una imagen que sí carga se queda', async () => {
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta({ miniaturaUrl: './avatares/guerrero-tanque.jpg' })])),
    );
    await montar();

    const miniatura = zona().querySelector('.subastas__miniatura');
    miniatura.querySelector('img').dispatchEvent(new Event('load'));
    expect(miniatura.dataset.imagen).toBe('si');
    expect(miniatura.querySelector('img')).not.toBeNull();
  });
});

describe('PLAYER-07b — el nombre de cada lote nunca es un código', () => {
  test('sin nombre, la tarjeta y sus botones dicen «Objeto sin nombre», no «null»', async () => {
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta({ nombreProducto: null, precioCompraInmediata: 900 })])),
    );
    await montar();

    const tarjeta = zona().querySelector('.subastas__producto');
    expect(tarjeta.querySelector('.subastas__nombre').textContent).toBe('Objeto sin nombre');
    expect(tarjeta.querySelector('.subastas__ver-detalle').getAttribute('aria-label')).toBe(
      'Ver la subasta de Objeto sin nombre',
    );
    expect(tarjeta.querySelector('.subastas__comprar-ahora').getAttribute('aria-label')).toBe(
      'Comprar Objeto sin nombre ahora',
    );
    expect(tarjeta.innerHTML).not.toMatch(/null|undefined/);
  });

  test('un nombre que es un identificador no llega a la pantalla', async () => {
    const uuid = '7c9e6679-7425-40de-944b-e07fc1f90ae7';
    globalThis.fetch = jest.fn(async () => responder(listado([subasta({ nombreProducto: uuid })])));
    await montar();

    expect(zona().querySelector('.subastas__nombre').textContent).toBe('Objeto sin nombre');
    expect(zona().textContent).not.toContain(uuid);
  });

  test('un nombre de verdad se enseña tal cual', async () => {
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta({ nombreProducto: 'Espada de una mano' })])),
    );
    await montar();

    expect(zona().querySelector('.subastas__nombre').textContent).toBe('Espada de una mano');
  });
});

describe('PLAYER-07b — buscar, ordenar y por página tienen título a la vista', () => {
  test('cada control tiene su etiqueta visible asociada, no solo un aria-label', async () => {
    await montar();

    for (const [selector, rotulo] of [
      ['.subastas-busqueda__campo', 'Buscar subastas'],
      ['#subastas-ordenar', 'Ordenar por'],
      ['[data-control="tamano-pagina"]', 'Por página'],
    ]) {
      const control = document.querySelector(selector);
      expect(control.labels).toHaveLength(1);
      expect(control.labels[0].textContent).toBe(rotulo);
      // Con la etiqueta a la vista sobra el aria-label: si no coincidieran, el
      // lector de pantalla diría una cosa y la pantalla otra.
      expect(control.hasAttribute('aria-label')).toBe(false);
    }
  });

  test('el ordenar conserva sus ocho criterios y el tamaño sus tres cifras', async () => {
    await montar();

    expect(document.querySelectorAll('#subastas-ordenar option')).toHaveLength(8);
    expect(
      [...document.querySelectorAll('[data-control="tamano-pagina"] option')].map(
        (o) => o.textContent,
      ),
    ).toEqual(['16', '32', '48']);
  });

  test('cada grupo de filtros lleva su título visible', async () => {
    await montar();

    const grupos = [...document.querySelectorAll('.subastas-filtros fieldset')];
    expect(grupos.length).toBeGreaterThanOrEqual(7);
    for (const grupo of grupos) {
      expect(grupo.querySelector('legend').textContent.trim()).not.toBe('');
    }
  });
});

describe('PLAYER-07b — dos lecturas seguidas: gana la última', () => {
  test('si la primera respuesta llega después, no tapa a la segunda', async () => {
    const pendientes = [];
    globalThis.fetch = jest.fn(
      () =>
        new Promise((resolver) => {
          pendientes.push(resolver);
        }),
    );
    await montar();
    document.querySelector('#subastas-ordenar').value = 'PRECIO_ASC';
    document.querySelector('#subastas-ordenar').dispatchEvent(new Event('change'));
    await asentar();

    // La segunda (la vigente) responde primero; la primera, después.
    pendientes[1](responder(listado([subasta({ id: 'nueva', nombreProducto: 'La vigente' })])));
    await asentar();
    pendientes[0](responder(listado([subasta({ id: 'vieja', nombreProducto: 'La vieja' })])));
    await asentar();

    expect(zona().querySelector('[data-subasta-id="nueva"]')).not.toBeNull();
    expect(zona().querySelector('[data-subasta-id="vieja"]')).toBeNull();
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
    inicializar({ conectarCanal: null });

    expect(zona().querySelector('[data-estado="cargando"]')).not.toBeNull();

    resolver(responder(listado([])));
    await asentar();
  });
});

/*
 * UX-R4.8 plegaba el panel en un `<details>` para que en un teléfono no tapara
 * el mercado. PLAYER-07b (punto 26) lo convierte en un panel aparte: columna
 * fija en escritorio y cajón en el teléfono. Lo que UX-R4.8 protegía se sigue
 * exigiendo aquí: que los resultados no queden detrás de los filtros y que un
 * panel cerrado no esconda cuántos filtros hay puestos.
 */
describe('el panel de filtros no tapa el mercado — UX-R4.8 y PLAYER-07b', () => {
  const panel = () => document.querySelector('.mercado__filtros');
  const boton = () => document.querySelector('[data-accion="abrir-filtros"]');
  const tecla = (key, extra = {}) =>
    document.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, ...extra }));

  test('los filtros viven en su propio panel, con título, y un botón «Filtros» lo abre', async () => {
    await montar();

    expect(panel().tagName).toBe('ASIDE');
    expect(panel().querySelector('form.subastas-filtros')).not.toBeNull();
    const titulo = document.getElementById(panel().getAttribute('aria-labelledby'));
    expect(titulo.tagName).toBe('H2');
    expect(titulo.textContent).toBe('Filtros');
    expect(boton().getAttribute('aria-controls')).toBe(panel().id);
    expect(boton().getAttribute('aria-expanded')).toBe('false');
    // Cerrado no es un diálogo: en escritorio es la columna de siempre.
    expect(panel().getAttribute('role')).toBeNull();
  });

  test('los resultados van DESPUES del panel, no dentro', async () => {
    await montar();

    expect(panel().contains(zona())).toBe(false);
    // UXC-9 — en la columna de al lado, con el aviso de cambios del mercado
    // encima (oculto mientras no haya ninguno).
    expect(panel().nextElementSibling.contains(zona())).toBe(true);
    expect(document.querySelector('[data-zona="novedades-mercado"]').hidden).toBe(true);
  });

  test('sin filtros puestos el botón no promete nada', async () => {
    await montar();

    expect(boton().textContent).toBe('Filtros');
    expect(document.querySelector('[data-zona="filtros-activos"]').hidden).toBe(true);
  });

  test('con filtros puestos, el botón y el panel dicen cuántos, aunque esté cerrado', async () => {
    await montar();

    const casilla = document.querySelector('input[name="tipoProducto"]');
    casilla.checked = true;
    casilla.dispatchEvent(new Event('change', { bubbles: true }));
    await asentar();

    // Un panel cerrado que esconde filtros activos deja a alguien mirando
    // «ninguna subasta coincide» sin saber por que.
    expect(boton().textContent).toBe('Filtros · 1 activo');
    const activos = document.querySelector('[data-zona="filtros-activos"]');
    expect(activos.hidden).toBe(false);
    expect(activos.textContent).toBe('1 activo');

    const rara = document.querySelector('input[name="rareza"][value="Rara"]');
    rara.checked = true;
    rara.dispatchEvent(new Event('change', { bubbles: true }));
    await asentar();
    expect(boton().textContent).toBe('Filtros · 2 activos');
  });

  test('en el teléfono se abre como diálogo, con el foco dentro y la página quieta', async () => {
    await montar();

    boton().click();

    expect(panel().dataset.cajon).toBe('abierto');
    expect(panel().getAttribute('role')).toBe('dialog');
    expect(panel().getAttribute('aria-modal')).toBe('true');
    expect(boton().getAttribute('aria-expanded')).toBe('true');
    expect(panel().contains(document.activeElement)).toBe(true);
    expect(document.activeElement.getAttribute('aria-label')).toBe('Cerrar filtros');
    expect(document.documentElement.classList.contains('con-modal')).toBe(true);
    expect(document.querySelector('[data-zona="velo-filtros"]').hidden).toBe(false);
  });

  test('Escape lo cierra y el foco vuelve al botón «Filtros»', async () => {
    await montar();
    boton().click();

    tecla('Escape');

    expect(panel().dataset.cajon).toBeUndefined();
    expect(panel().getAttribute('role')).toBeNull();
    expect(panel().getAttribute('aria-modal')).toBeNull();
    expect(boton().getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(boton());
    expect(document.documentElement.classList.contains('con-modal')).toBe(false);
    expect(document.querySelector('[data-zona="velo-filtros"]').hidden).toBe(true);
  });

  test('la equis, «Ver resultados» y el velo también lo cierran', async () => {
    await montar();

    for (const cerrar of [
      '[data-accion="cerrar-filtros"]',
      '[data-accion="ver-resultados"]',
      '[data-zona="velo-filtros"]',
    ]) {
      boton().click();
      expect(panel().dataset.cajon).toBe('abierto');
      document.querySelector(cerrar).click();
      expect(panel().dataset.cajon).toBeUndefined();
      expect(document.activeElement).toBe(boton());
    }
    expect(
      document.querySelector('[data-accion="cerrar-filtros"]').getAttribute('aria-label'),
    ).toBe('Cerrar filtros');
  });

  test('el tabulador no se escapa del cajón abierto', async () => {
    await montar();
    boton().click();

    const ultimo = document.querySelector('[data-accion="ver-resultados"]');
    ultimo.focus();
    tecla('Tab');
    expect(document.activeElement.dataset.accion).toBe('cerrar-filtros');

    tecla('Tab', { shiftKey: true });
    expect(document.activeElement).toBe(ultimo);
  });

  test('cerrado, Escape no hace nada ni roba el foco', async () => {
    await montar();
    const campo = document.querySelector('.subastas-busqueda__campo');
    campo.focus();

    tecla('Escape');

    expect(document.activeElement).toBe(campo);
  });

  test('«Limpiar filtros» vuelve a pedir el listado entero', async () => {
    // El `reset` del formulario avisa ANTES de vaciarse (ver #390): sin la
    // segunda lectura, el listado se quedaba filtrado con el panel vacío.
    await montar();
    const rara = document.querySelector('input[name="rareza"][value="Rara"]');
    rara.checked = true;
    rara.dispatchEvent(new Event('change', { bubbles: true }));
    await asentar();
    expect(String(globalThis.fetch.mock.calls.at(-1)[0])).toContain('rareza=Rara');

    document.querySelector('.subastas-filtros__limpiar').click();
    await asentar();
    await asentar();

    expect(String(globalThis.fetch.mock.calls.at(-1)[0])).not.toContain('rareza');
    expect(boton().textContent).toBe('Filtros');
  });
});

describe('UXC-9 — paginación de 7.7.9 y listado en vivo', () => {
  test('el tamaño de página se elige y se pide al servicio', async () => {
    await montar();
    const selector = document.querySelector('[data-control="tamano-pagina"]');
    expect([...selector.options].map((o) => Number(o.value))).toEqual([...TAMANOS_DE_PAGINA]);

    selector.value = '32';
    selector.dispatchEvent(new Event('change'));
    await asentar();

    const url = String(globalThis.fetch.mock.calls.at(-1)[0]);
    expect(url).toContain('size=32');
    expect(url).toContain('page=0');
  });

  test('hasta diez casillas de página, centradas en la actual', async () => {
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta()], { totalPaginas: 25, pagina: 0 })),
    );
    await montar();

    // PLAYER-07b — las casillas numeradas son las que no llevan dirección; las
    // dos flechas van aparte, a los lados.
    const casillas = [...zona().querySelectorAll('.paginacion__pagina:not([data-direccion])')];
    expect(zona().querySelectorAll('.paginacion__pagina[data-direccion]')).toHaveLength(2);
    expect(casillas.map((c) => c.textContent)).toEqual([
      '1',
      '2',
      '3',
      '4',
      '5',
      '6',
      '7',
      '8',
      '9',
      '10',
    ]);
  });

  // jsdom no trae `matchMedia`: estas dos pruebas lo ponen y el `afterEach`
  // de arriba lo quita.
  test('PLAYER-07b: en un teléfono, cinco casillas y las dos flechas, en una fila', async () => {
    globalThis.matchMedia = (consulta) => ({
      matches: consulta === '(max-width: 599px)',
      addEventListener() {},
      removeEventListener() {},
    });
    globalThis.fetch = jest.fn(async () =>
      responder(listado([subasta()], { totalPaginas: 25, pagina: 0 })),
    );
    await montar();

    const casillas = [...zona().querySelectorAll('.paginacion__pagina:not([data-direccion])')];
    expect(casillas.map((c) => c.textContent)).toEqual(['1', '2', '3', '4', '5']);
    expect(zona().querySelectorAll('.paginacion__pagina[data-direccion]')).toHaveLength(2);
  });

  test('PLAYER-07b: si la ventana se ensancha con el cajón abierto, se cierra', async () => {
    const oyentes = new Map();
    const consultas = new Map();
    globalThis.matchMedia = (consulta) => {
      const mq = {
        matches: false,
        addEventListener: (_evento, fn) => oyentes.set(consulta, fn),
        removeEventListener() {},
      };
      consultas.set(consulta, mq);
      return mq;
    };
    await montar();
    const boton = document.querySelector('[data-accion="abrir-filtros"]');
    boton.click();
    expect(document.querySelector('.mercado__filtros').dataset.cajon).toBe('abierto');

    consultas.get('(min-width: 900px)').matches = true;
    oyentes.get('(min-width: 900px)')();

    expect(document.querySelector('.mercado__filtros').dataset.cajon).toBeUndefined();
    expect(document.querySelector('.mercado__filtros').getAttribute('role')).toBeNull();
    expect(document.documentElement.classList.contains('con-modal')).toBe(false);
  });

  test('un cambio de una subasta a la vista pone su tarjeta al día; uno de fuera se avisa', async () => {
    globalThis.fetch = jest.fn(async () => responder(listado([subasta()])));
    let alRecibir = null;
    const canal = {
      suscribir: jest.fn((destino, fn) => {
        alRecibir = fn;
      }),
      cerrar: jest.fn(),
    };
    await montar({ conectarCanal: async () => canal, urlCanal: 'ws://prueba/ws-subastas' });
    await asentar();

    expect(canal.suscribir).toHaveBeenCalledWith(CANAL_DEL_LISTADO, expect.any(Function));

    alRecibir({ id: 's-1', estado: 'ACTIVA', ofertaVigente: 1450, cantidadPujas: 4 });
    const tarjeta = zona().querySelector('[data-subasta-id="s-1"]');
    expect(tarjeta.querySelector('.subastas__precio').textContent).toBe('1.450 créditos');
    expect(tarjeta.querySelector('.subastas__pujas').textContent).toBe('4 pujas');

    alRecibir({ id: 's-1', estado: 'ADJUDICADA', ofertaVigente: 1450, cantidadPujas: 4 });
    expect(tarjeta.querySelector('.subastas__acciones')).toBeNull();
    expect(tarjeta.textContent).toContain('Esta subasta ya terminó');

    const novedades = document.querySelector('[data-zona="novedades-mercado"]');
    alRecibir({ id: 'otra', estado: 'ACTIVA', ofertaVigente: 10, cantidadPujas: 0 });
    expect(novedades.hidden).toBe(false);
    expect(novedades.textContent).toContain('Hubo un cambio en una subasta');

    const llamadas = globalThis.fetch.mock.calls.length;
    novedades.querySelector('[data-accion="actualizar-mercado"]').click();
    await asentar();
    expect(globalThis.fetch.mock.calls.length).toBe(llamadas + 1);
    expect(novedades.hidden).toBe(true);
  });

  test('sin canal, el listado funciona igual y no se inventa ningún aviso', async () => {
    globalThis.fetch = jest.fn(async () => responder(listado([subasta()])));
    await montar({
      conectarCanal: async () => {
        throw new Error('no abre');
      },
    });
    await asentar();
    expect(zona().querySelector('[data-subasta-id="s-1"]')).not.toBeNull();
    expect(document.querySelector('[data-zona="novedades-mercado"]').hidden).toBe(true);
  });
});
