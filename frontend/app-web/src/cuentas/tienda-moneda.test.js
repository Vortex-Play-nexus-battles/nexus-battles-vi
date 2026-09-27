/**
 * B5 — la moneda de la tienda: de dónde sale, cuál se enseña y cómo se pide.
 */

import {
  CLAVE_MONEDA,
  MONEDAS,
  conMoneda,
  disponiblesDe,
  guardarMoneda,
  idiomasDe,
  monedaAMostrar,
  monedaGuardada,
  monedaPorIdioma,
  monedaPreferida,
  notaDeMoneda,
  pintarSelectorDeMoneda,
  regionDe,
} from './tienda-moneda.js';

/** Un almacenamiento de mentira, con la forma de `Storage`. */
function almacen(inicial = {}) {
  const datos = new Map(Object.entries(inicial));
  return {
    getItem: (clave) => (datos.has(clave) ? datos.get(clave) : null),
    setItem: (clave, valor) => datos.set(clave, String(valor)),
    datos,
  };
}

const almacenRoto = {
  getItem: () => {
    throw new Error('bloqueado');
  },
  setItem: () => {
    throw new Error('bloqueado');
  },
};

describe('la región del idioma', () => {
  test('la región de dos letras, en mayúsculas', () => {
    expect(regionDe('es-CO')).toBe('CO');
    expect(regionDe('en_us')).toBe('US');
    expect(regionDe('zh-Hant-TW')).toBe('TW');
  });

  test('sin región, o una que no es un país, no hay región', () => {
    expect(regionDe('es')).toBeNull();
    expect(regionDe('es-419')).toBeNull();
    expect(regionDe(undefined)).toBeNull();
  });
});

describe('la moneda por el idioma del navegador (aproxima la ubicación)', () => {
  test.each([
    [['es-CO'], 'COP'],
    [['en-US'], 'USD'],
    [['es-EC', 'es'], 'USD'],
    [['es-PA'], 'USD'],
    [['de-DE'], 'EUR'],
    [['es-ES'], 'EUR'],
    [['bg-BG'], 'EUR'],
    [['pt-BR'], 'COP'],
    [['es-MX', 'en-US'], 'COP'],
  ])('%j → %s', (idiomas, moneda) => {
    expect(monedaPorIdioma(idiomas)).toBe(moneda);
  });

  test('decide el primer idioma que declara región; sin ninguno, COP', () => {
    expect(monedaPorIdioma(['es', 'es-419', 'fr-FR'])).toBe('EUR');
    expect(monedaPorIdioma(['es'])).toBe('COP');
    expect(monedaPorIdioma([])).toBe('COP');
    expect(monedaPorIdioma(null)).toBe('COP');
  });

  test('los idiomas salen de navigator.languages, o de navigator.language', () => {
    expect(idiomasDe({ languages: ['es-CO', 'en'] })).toEqual(['es-CO', 'en']);
    expect(idiomasDe({ languages: [], language: 'en-US' })).toEqual(['en-US']);
    expect(idiomasDe(null)).toEqual([]);
  });
});

describe('la preferencia recordada', () => {
  test('se guarda y se lee solo una de las tres', () => {
    const local = almacen();
    guardarMoneda(local, 'EUR');
    expect(local.datos.get(CLAVE_MONEDA)).toBe('EUR');
    expect(monedaGuardada(local)).toBe('EUR');

    guardarMoneda(local, 'GBP');
    expect(local.datos.get(CLAVE_MONEDA)).toBe('EUR');
    expect(monedaGuardada(almacen({ [CLAVE_MONEDA]: 'BTC' }))).toBeNull();
  });

  test('sin almacenamiento (ventana privada) no se recuerda, y nada revienta', () => {
    expect(() => guardarMoneda(almacenRoto, 'USD')).not.toThrow();
    expect(monedaGuardada(almacenRoto)).toBeNull();
    expect(monedaGuardada(null)).toBeNull();
  });

  test('la elegida manda sobre el idioma; sin elegir, el idioma', () => {
    expect(
      monedaPreferida({
        almacen: almacen({ [CLAVE_MONEDA]: 'COP' }),
        navegador: { language: 'en-US' },
      }),
    ).toEqual({ moneda: 'COP', porIdioma: false });
    expect(monedaPreferida({ almacen: almacen(), navegador: { language: 'en-US' } })).toEqual({
      moneda: 'USD',
      porIdioma: true,
    });
  });
});

describe('las monedas que ofrece el servidor', () => {
  test('COP siempre; las demás si la vitrina las declara', () => {
    expect(disponiblesDe({ monedasDisponibles: ['COP', 'USD'] })).toEqual(['COP', 'USD']);
    expect(disponiblesDe({ monedasDisponibles: ['EUR'] })).toEqual(['COP', 'EUR']);
    expect(disponiblesDe({ monedasDisponibles: ['GBP', 'USD'] })).toEqual(['COP', 'USD']);
  });

  test('sin el campo (un servicio anterior a 1.4.0), solo COP', () => {
    expect(disponiblesDe({ content: [] })).toEqual(['COP']);
    expect(disponiblesDe(null)).toEqual(['COP']);
  });

  test('se enseña la preferida si está; si no, COP', () => {
    expect(monedaAMostrar('USD', ['COP', 'USD'])).toBe('USD');
    expect(monedaAMostrar('EUR', ['COP', 'USD'])).toBe('COP');
    expect(monedaAMostrar(null, MONEDAS)).toBe('COP');
  });
});

describe('la moneda en la ruta', () => {
  test('COP no lleva parámetro: las rutas en pesos no cambian', () => {
    expect(conMoneda('/carrito', 'COP')).toBe('/carrito');
    expect(conMoneda('/vitrina?size=50', null)).toBe('/vitrina?size=50');
  });

  test('las demás se añaden con ? o con &', () => {
    expect(conMoneda('/carrito', 'USD')).toBe('/carrito?moneda=USD');
    expect(conMoneda('/vitrina?size=50', 'EUR')).toBe('/vitrina?size=50&moneda=EUR');
  });

  test('una moneda que no es del contrato no viaja', () => {
    expect(conMoneda('/carrito', 'GBP')).toBe('/carrito');
  });
});

describe('el selector', () => {
  function selector() {
    document.body.innerHTML = `
      <select id="moneda-tienda">
        <option value="COP">COP</option>
        <option value="USD">USD</option>
        <option value="EUR">EUR</option>
      </select>`;
    return document.getElementById('moneda-tienda');
  }

  test('las que no están salen desactivadas y lo dicen en el texto', () => {
    const control = selector();

    pintarSelectorDeMoneda(control, { actual: 'COP', disponibles: ['COP', 'USD'] });

    expect(control.value).toBe('COP');
    const [cop, usd, eur] = Array.from(control.options);
    expect(cop.disabled).toBe(false);
    expect(cop.textContent).toBe('COP · peso colombiano');
    expect(usd.disabled).toBe(false);
    expect(eur.disabled).toBe(true);
    expect(eur.textContent).toBe('EUR · euro (no disponible)');
  });

  test('sin selector en la vista no pasa nada', () => {
    expect(() =>
      pintarSelectorDeMoneda(null, { actual: 'COP', disponibles: ['COP'] }),
    ).not.toThrow();
  });

  test('la nota dice por qué se ve otra moneda que la preferida', () => {
    expect(
      notaDeMoneda({ actual: 'COP', preferida: 'USD', disponibles: ['COP'], porIdioma: true }),
    ).toBe(
      'Los precios en USD no están disponibles por ahora (la tienda aún no tiene su tasa de cambio): se muestran en COP.',
    );
    expect(
      notaDeMoneda({
        actual: 'USD',
        preferida: 'USD',
        disponibles: ['COP', 'USD'],
        porIdioma: true,
      }),
    ).toMatch(/según el idioma de tu navegador/);
  });

  test('tasa no configurada (D-32): la nota explica las opciones desactivadas', () => {
    expect(
      notaDeMoneda({ actual: 'COP', preferida: 'COP', disponibles: ['COP'], porIdioma: false }),
    ).toBe('USD y EUR no están disponibles por ahora: la tienda aún no tiene su tasa de cambio.');
    expect(
      notaDeMoneda({
        actual: 'COP',
        preferida: 'COP',
        disponibles: ['COP', 'USD'],
        porIdioma: true,
      }),
    ).toBe('EUR no está disponible por ahora: la tienda aún no tiene su tasa de cambio.');
  });

  test('con las tres disponibles y la moneda elegida, no hay nada que explicar', () => {
    expect(
      notaDeMoneda({ actual: 'EUR', preferida: 'EUR', disponibles: MONEDAS, porIdioma: false }),
    ).toBe('');
    expect(
      notaDeMoneda({ actual: 'COP', preferida: 'COP', disponibles: MONEDAS, porIdioma: true }),
    ).toBe('');
  });
});
