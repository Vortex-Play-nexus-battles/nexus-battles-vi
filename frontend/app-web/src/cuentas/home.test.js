/**
 * Home del jugador (PR-UX-2, HU-UX-001).
 *
 * Lo que más importa aquí no es que pinte: es que **no mienta** cuando un
 * servicio no está en el entorno. En el host de dev no corren ms-finanzas ni
 * inventario (#430/#435) y la home tiene que decirlo con palabras, no
 * enseñar un saldo de cero ni un 502.
 */

import { jest } from '@jest/globals';

import {
  PRODUCTOS_EN_INICIO,
  bloqueDeTienda,
  montarHome,
  motivoDeIndisponibilidad,
  urlDeLaTiendaCon,
} from './home.js';

const VISTA = `
  <main>
    <h1 data-zona="saludo"></h1>
    <div data-zona="bloque-saldo"></div>
    <div data-zona="bloque-heroe"></div>
    <div data-zona="bloque-torneo"></div>
    <div data-zona="bloque-avisos"></div>
    <div data-zona="bloque-tienda"></div>
  </main>
`;

/** Un producto de la vitrina con la forma de ecommerce-carrito.yaml. */
const producto = (i) => ({
  id: `p-${i}`,
  nombre: `Espada rúnica ${i}`,
  tipo: 'ARMA',
  precioFinal: 1000,
  precioOriginal: 1000,
  moneda: 'COP',
});

const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

const SESION = { uid: 'u-1', apodo: 'vael', rol: 'JUGADOR' };

/** Servicio simulado: por ruta, el estado y el cuerpo que devuelve. */
function servicio(rutas) {
  return jest.fn(async (url) => {
    const ruta = new URL(url, 'http://x').pathname;
    const clave = Object.keys(rutas).find((r) => ruta.startsWith(r));
    if (!clave) {
      throw new Error(`sin ruta simulada para ${ruta}`);
    }
    const { estado = 200, cuerpo = null } = rutas[clave];
    return {
      ok: estado >= 200 && estado < 300,
      status: estado,
      json: async () => cuerpo,
      text: async () => (cuerpo === null ? '' : JSON.stringify(cuerpo)),
    };
  });
}

const TODO_BIEN = {
  '/api/v1/creditos': { cuerpo: { saldoDisponible: 380, saldoReservado: 120, saldoBruto: 500 } },
  // Los campos son los de `ElementoInventario` en `contracts/openapi/inventario.yaml`:
  // `nombrePropio` y `disponible`. Antes este doble devolvia `nombre` y
  // `equipado`, que NO existen en el contrato, y por eso la prueba pasaba
  // mientras el bloque estaba roto contra la API real (UX-R2.2).
  '/api/v1/inventario/elementos': {
    cuerpo: {
      elementos: [
        {
          id: 'h-1',
          productoId: 'p-1',
          tipo: 'HEROE',
          nombrePropio: 'Sombra de Vael',
          disponible: true,
        },
      ],
      ultima: true,
    },
  },
  // «Equipado» no es un campo: es tener algo puesto. Misma regla que aplica la
  // puerta de heroe del servidor (`ClienteInventarioHeroes`).
  '/api/v1/inventario/heroes': {
    cuerpo: { heroeId: 'h-1', armas: ['a-1'], armaduras: {}, items: [] },
  },
  '/api/v1/torneos': {
    cuerpo: [
      {
        id: 't-1',
        nombre: 'Copa Otoño',
        estado: 'INSCRIPCIONES_ABIERTAS',
        equiposInscritos: 3,
        cupos: 8,
        costoInscripcion: 10,
        inscripcionesCierranEn: '2099-01-01T10:00:00Z',
      },
    ],
  },
  '/api/v1/users': { cuerpo: { contenido: [{ titulo: 'Te sancionaron', mensaje: 'Revisa' }] } },
};

beforeEach(() => {
  document.body.innerHTML = VISTA;
});

describe('con todos los servicios disponibles', () => {
  test('saluda por el apodo y pinta saldo, héroe, torneo y avisos', async () => {
    montarHome(document, { sesion: SESION, fetchImpl: servicio(TODO_BIEN) });
    await asentar();
    await asentar();
    // Una vuelta más: el héroe encadena una segunda llamada (el equipamiento).
    await asentar();

    expect(document.querySelector('[data-zona="saludo"]').textContent).toBe('Hola, vael');
    expect(document.querySelector('[data-zona="bloque-saldo"]').textContent).toContain('380');
    expect(document.querySelector('[data-zona="bloque-heroe"]').textContent).toContain(
      'Sombra de Vael',
    );
    expect(document.querySelector('[data-zona="bloque-torneo"]').textContent).toContain(
      'Copa Otoño',
    );
    expect(document.querySelector('[data-zona="bloque-avisos"]').textContent).toContain(
      'Te sancionaron',
    );
  });

  test('punto 6: el escaparate pinta los productos de la vitrina con «Ver en la tienda»', async () => {
    const vitrina = {
      '/api/v1/vitrina': { cuerpo: { content: [producto(1), producto(2)], totalElements: 2 } },
    };
    montarHome(document, { sesion: SESION, fetchImpl: servicio({ ...TODO_BIEN, ...vitrina }) });
    await asentar();
    await asentar();

    const zona = document.querySelector('[data-zona="bloque-tienda"]');
    const tarjetas = zona.querySelectorAll('.product-card');
    expect(tarjetas).toHaveLength(2);
    const enlace = tarjetas[0].querySelector('a[data-ver-en-tienda]');
    expect(enlace.textContent).toBe('Ver en la tienda');
    expect(new URL(enlace.href).searchParams.get('busqueda')).toBe('Espada rúnica 1');
    expect(enlace.getAttribute('aria-label')).toBe('Ver en la tienda: Espada rúnica 1');
    // Con cuenta no hay «Entra para comprar» ni el botón de la ficha pública.
    expect(zona.querySelector('[data-ver-producto]')).toBeNull();
  });
});

/*
 * Revisión del modo jugador del 6-oct, punto 6 — el escaparate de la tienda
 * en el inicio. Productos reales de la vitrina; si no hay o no responde, se
 * dice.
 */
describe('bloqueDeTienda (punto 6)', () => {
  test('pide la vitrina con el tamaño del escaparate y no enseña más', async () => {
    const consultar = jest.fn(async () => ({
      productos: Array.from({ length: 12 }, (_, i) => producto(i + 1)),
    }));
    const bloque = await bloqueDeTienda({ consultar, fetchImpl: () => {} });

    expect(consultar).toHaveBeenCalledWith(
      expect.objectContaining({ cuantos: PRODUCTOS_EN_INICIO }),
    );
    expect(bloque.querySelectorAll('.product-card')).toHaveLength(PRODUCTOS_EN_INICIO);
    expect(PRODUCTOS_EN_INICIO).toBeGreaterThanOrEqual(4);
    expect(PRODUCTOS_EN_INICIO).toBeLessThanOrEqual(8);
  });

  test('la vitrina vacía se dice, sin tarjetas inventadas', async () => {
    const bloque = await bloqueDeTienda({ consultar: async () => ({ productos: [] }) });
    expect(bloque.textContent).toMatch(/Todavía no hay productos a la venta/);
    expect(bloque.querySelector('.product-card')).toBeNull();
  });

  test('si la vitrina no responde se dice y se puede reintentar, sin códigos', async () => {
    const alReintentar = jest.fn();
    const bloque = await bloqueDeTienda({
      consultar: async () => {
        throw new Error('La vitrina respondió 503');
      },
      alReintentar,
    });
    expect(bloque.textContent).toMatch(/La tienda no responde ahora mismo/);
    expect(bloque.textContent).not.toMatch(/503/);
    bloque.querySelector('button').click();
    expect(alReintentar).toHaveBeenCalled();
  });

  test('urlDeLaTiendaCon: la tienda con el nombre buscado, o la tienda sin más', () => {
    expect(new URL(urlDeLaTiendaCon('Casco de Obsidiana')).searchParams.get('busqueda')).toBe(
      'Casco de Obsidiana',
    );
    expect(new URL(urlDeLaTiendaCon('')).search).toBe('');
    expect(urlDeLaTiendaCon()).toMatch(/cuentas\/tienda\.html$/);
  });
});

describe('cuando un servicio no está en este entorno', () => {
  test('el saldo caído se explica y ofrece reintentar, sin enseñar un cero ni el código', async () => {
    montarHome(document, {
      sesion: SESION,
      fetchImpl: servicio({ ...TODO_BIEN, '/api/v1/creditos': { estado: 502 } }),
    });
    await asentar();
    await asentar();

    const zona = document.querySelector('[data-zona="bloque-saldo"]');
    expect(zona.textContent).toMatch(/no está[n]? disponible/i);
    expect(zona.textContent).not.toMatch(/\b502\b/);
    expect(zona.textContent).not.toMatch(/\b0\b/);
    expect(zona.querySelector('[data-accion="reintentar"]')).not.toBeNull();
    // Y el resto de la home sigue en pie.
    expect(document.querySelector('[data-zona="bloque-torneo"]').textContent).toContain(
      'Copa Otoño',
    );
  });

  test('reintentar vuelve a pedir y, si ya responde, pinta el dato', async () => {
    let caido = true;
    const fetchImpl = jest.fn(async (url) => {
      const ruta = new URL(url, 'http://x').pathname;
      if (ruta.startsWith('/api/v1/creditos')) {
        if (caido) {
          caido = false;
          return { ok: false, status: 502, json: async () => null };
        }
        return {
          ok: true,
          status: 200,
          json: async () => ({ saldoDisponible: 380, saldoReservado: 0, saldoBruto: 380 }),
        };
      }
      return { ok: true, status: 200, json: async () => ({ elementos: [], contenido: [] }) };
    });

    montarHome(document, { sesion: SESION, fetchImpl });
    await asentar();
    await asentar();
    document.querySelector('[data-zona="bloque-saldo"] [data-accion="reintentar"]').click();
    await asentar();
    await asentar();

    expect(document.querySelector('[data-zona="bloque-saldo"]').textContent).toContain('380');
  });

  test('sin red tampoco se inventa nada', async () => {
    const fetchImpl = jest.fn(async () => {
      throw new TypeError('Failed to fetch');
    });

    montarHome(document, { sesion: SESION, fetchImpl });
    await asentar();
    await asentar();

    expect(document.querySelector('[data-zona="bloque-saldo"]').textContent).toMatch(
      /no está disponible/i,
    );
  });
});

// UX-R2.2 — este bloque es el guardián del defecto que nadie vio: la home
// filtraba por `elemento.equipado` y leía `heroe.nombre`, dos campos que
// `contracts/openapi/inventario.yaml` no declara. Contra la API real el
// resultado era SIEMPRE «no tienes un héroe equipado». Si alguien vuelve a
// leer campos inventados, estas dos pruebas se caen.
describe('el héroe se busca como lo hace el servidor', () => {
  test('lo encuentra por `nombrePropio` y por tener equipo puesto', async () => {
    montarHome(document, { sesion: SESION, fetchImpl: servicio(TODO_BIEN) });
    await asentar();
    await asentar();
    await asentar();

    const zona = document.querySelector('[data-zona="bloque-heroe"]');
    expect(zona.textContent).toContain('Sombra de Vael');
    // Y se pinta con el marco del kit, no como una línea de texto.
    expect(zona.querySelector('.marco-heroe')).not.toBeNull();
  });

  test('un héroe SIN nada puesto no cuenta como equipado', async () => {
    montarHome(document, {
      sesion: SESION,
      fetchImpl: servicio({
        ...TODO_BIEN,
        '/api/v1/inventario/heroes': {
          cuerpo: { heroeId: 'h-1', armas: [], armaduras: {}, items: [] },
        },
      }),
    });
    await asentar();
    await asentar();
    await asentar();

    expect(document.querySelector('[data-zona="bloque-heroe"]').textContent).toMatch(
      /no tienes un héroe equipado/i,
    );
  });
});

describe('cuando el servicio responde pero no hay nada', () => {
  test('sin héroe equipado se dice qué falta y a dónde ir', async () => {
    montarHome(document, {
      sesion: SESION,
      fetchImpl: servicio({
        ...TODO_BIEN,
        '/api/v1/inventario/elementos': { cuerpo: { elementos: [] } },
      }),
    });
    await asentar();
    await asentar();

    const zona = document.querySelector('[data-zona="bloque-heroe"]');
    expect(zona.textContent).toMatch(/no tienes un héroe equipado/i);
    expect(zona.querySelector('a').getAttribute('href')).toContain('inventario.html');
  });

  test('sin torneo abierto se explica la regla de los 91 días', async () => {
    montarHome(document, {
      sesion: SESION,
      fetchImpl: servicio({ ...TODO_BIEN, '/api/v1/torneos': { cuerpo: [] } }),
    });
    await asentar();
    await asentar();

    expect(document.querySelector('[data-zona="bloque-torneo"]').textContent).toMatch(/91 días/);
  });
});

/**
 * RF-NOT-002 (#533) sobre la gestión de HU-PRD-013 (#854): la home monta el
 * banner rotativo con los vigentes de productos. Cómo rota y cómo se retira
 * lo prueba `banner-rotativo.test.js`; aquí, que la home lo monte en su zona,
 * con la ruta del contrato, y que su fallo no toque nada más.
 */
describe('el banner de anuncios de la home (RF-NOT-002)', () => {
  const VIGENTE = {
    id: 'b-1',
    contenido: 'Temporada de héroes: nuevos prototipos en la tienda',
    publicarDesde: '2026-10-01T15:00:00Z',
    vigenteHasta: '2099-12-31T23:00:00Z',
    retirado: false,
    creadoEn: '2026-10-01T14:00:00Z',
    modificadoEn: '2026-10-01T14:00:00Z',
  };

  beforeEach(() => {
    document.body.innerHTML = `<div data-zona="bloque-banners" hidden></div>${VISTA}`;
  });

  const zona = () => document.querySelector('[data-zona="bloque-banners"]');

  test('pinta los vigentes que publica productos, con su publicación y su vigencia', async () => {
    const fetchImpl = servicio({ ...TODO_BIEN, '/api/v1/banners/vigentes': { cuerpo: [VIGENTE] } });
    montarHome(document, { sesion: SESION, fetchImpl });
    await asentar();
    await asentar();

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/banners/vigentes', { method: 'GET' });
    expect(zona().hidden).toBe(false);
    expect(zona().textContent).toContain(VIGENTE.contenido);
    expect(zona().textContent).toContain('Publicado el');
    expect(zona().textContent).toContain('Vigente hasta el');
    // La #854 pintaba solo `banners[0].contenido` con un rótulo propio: ahora
    // es el componente de RF-NOT-002 el que vive en la zona.
    expect(zona().querySelector('[data-componente="banner-rotativo"]')).not.toBeNull();
  });

  test('sin vigentes la zona sigue oculta y el resto de la home se pinta igual', async () => {
    montarHome(document, {
      sesion: SESION,
      fetchImpl: servicio({ ...TODO_BIEN, '/api/v1/banners/vigentes': { cuerpo: [] } }),
    });
    await asentar();
    await asentar();
    await asentar();

    expect(zona().hidden).toBe(true);
    expect(document.querySelector('[data-zona="bloque-torneo"]').textContent).toContain(
      'Copa Otoño',
    );
  });

  test('si productos no responde, el banner no aparece y la home sigue entera', async () => {
    const aviso = jest.spyOn(console, 'warn').mockImplementation(() => {});
    montarHome(document, {
      sesion: SESION,
      fetchImpl: servicio({ ...TODO_BIEN, '/api/v1/banners/vigentes': { estado: 502 } }),
    });
    await asentar();
    await asentar();
    await asentar();

    expect(zona().hidden).toBe(true);
    expect(zona().childElementCount).toBe(0);
    expect(aviso).toHaveBeenCalled();
    expect(document.querySelector('[data-zona="bloque-saldo"]').textContent).toContain('380');
    aviso.mockRestore();
  });
});

describe('motivoDeIndisponibilidad', () => {
  test('nunca menciona el código de estado', () => {
    for (const estado of [0, 500, 502, 503, 401, 403, 404]) {
      expect(motivoDeIndisponibilidad(estado, 'el saldo')).not.toMatch(/\d{3}/);
    }
  });

  test('un 401 habla de la sesión, no de una caída', () => {
    expect(motivoDeIndisponibilidad(401, 'el saldo')).toMatch(/sesión/i);
  });
});

/**
 * UX-R3.2 — el saldo acompaña al jugador por toda la aplicación.
 *
 * El armazón reserva el hueco de créditos y lo deja oculto a propósito: no
 * pinta una cifra que no sabe. Quien la sabe es la home, que es la que llamó
 * a `ms-finanzas`. Lo que se comprueba aquí es que la publique **y que no la
 * invente** cuando el servicio no contesta: un cero en el HUD sería peor que
 * un hueco vacío, porque parece un saldo.
 */
describe('el saldo en el HUD de la cabecera', () => {
  function cabeceraSimulada() {
    const cabecera = document.createElement('header');
    cabecera.innerHTML = '<span data-zona="creditos" hidden><span data-zona="saldo"></span></span>';
    document.body.append(cabecera);
    return cabecera;
  }

  test('publica el disponible y descubre el hueco', async () => {
    const cabecera = cabeceraSimulada();

    montarHome(document, { sesion: SESION, fetchImpl: servicio(TODO_BIEN), cabecera });
    await asentar();
    await asentar();

    const hueco = cabecera.querySelector('[data-zona="creditos"]');
    expect(hueco.hidden).toBe(false);
    expect(cabecera.querySelector('[data-zona="saldo"]').textContent).toContain('380');
    // El disponible, no el bruto: es lo que se puede apostar ahora.
    expect(cabecera.querySelector('[data-zona="saldo"]').textContent).not.toContain('500');
  });

  test('si el servicio no responde, el hueco se queda vacío y oculto', async () => {
    const cabecera = cabeceraSimulada();

    montarHome(document, {
      sesion: SESION,
      fetchImpl: servicio({ ...TODO_BIEN, '/api/v1/creditos': { estado: 502 } }),
      cabecera,
    });
    await asentar();
    await asentar();

    expect(cabecera.querySelector('[data-zona="creditos"]').hidden).toBe(true);
    expect(cabecera.querySelector('[data-zona="saldo"]').textContent).toBe('');
  });

  test('sin cabecera montada, la home sigue funcionando', async () => {
    // Las pruebas de arriba y cualquier vista que monte la home sin pasar su
    // cabecera: el HUD es un extra, no un requisito.
    montarHome(document, { sesion: SESION, fetchImpl: servicio(TODO_BIEN) });
    await asentar();
    await asentar();

    expect(document.querySelector('[data-zona="bloque-saldo"]').textContent).toContain('380');
  });
});
