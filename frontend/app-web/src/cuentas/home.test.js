/**
 * Home del jugador (PR-UX-2, HU-UX-001).
 *
 * Lo que más importa aquí no es que pinte: es que **no mienta** cuando un
 * servicio no está en el entorno. En el host de dev no corren ms-finanzas ni
 * inventario (#430/#435) y la home tiene que decirlo con palabras, no
 * enseñar un saldo de cero ni un 502.
 */

import { jest } from '@jest/globals';

import { ACCESOS, montarHome, motivoDeIndisponibilidad } from './home.js';

const VISTA = `
  <main>
    <h1 data-zona="saludo"></h1>
    <div data-zona="bloque-saldo"></div>
    <div data-zona="bloque-heroe"></div>
    <div data-zona="bloque-torneo"></div>
    <div data-zona="bloque-avisos"></div>
    <div data-zona="accesos"></div>
  </main>
`;

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
    return { ok: estado >= 200 && estado < 300, status: estado, json: async () => cuerpo };
  });
}

const TODO_BIEN = {
  '/api/v1/creditos': { cuerpo: { saldoDisponible: 380, saldoReservado: 120, saldoBruto: 500 } },
  '/api/v1/inventario/elementos': {
    cuerpo: { elementos: [{ id: 'h-1', nombre: 'Sombra de Vael', tipo: 'HEROE', equipado: true }] },
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

  test('los accesos salen de una lista, no de HTML escrito a mano', async () => {
    montarHome(document, { sesion: SESION, fetchImpl: servicio(TODO_BIEN) });
    await asentar();

    const enlaces = document.querySelectorAll('[data-zona="accesos"] [data-acceso]');
    expect(enlaces).toHaveLength(ACCESOS.length);
    expect(enlaces[0].getAttribute('href')).toBe(ACCESOS[0].destino);
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
