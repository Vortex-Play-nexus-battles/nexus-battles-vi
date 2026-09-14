/**
 * Barra de vida — HU-SAL-005 (RF-JUE-009).
 *
 * Las pruebas se escriben contra los tres criterios de aceptacion del issue #31,
 * citados literalmente:
 *
 *   1. «Verde por encima del 60 %, amarillo entre el 60 % y el 40 % inclusive,
 *      y rojo por debajo del 40 %.»
 *   2. «La barra muestra el valor numerico junto al color.»
 *   3. «La barra y el valor se actualizan tras cada accion para todos los
 *      participantes.»
 *
 * El criterio 1 se prueba en sus FRONTERAS, que es donde estas cosas se rompen:
 * 60 y 40 exactos, no solo 80 y 20. El enunciado dice «inclusive», asi que el
 * 40 % pertenece al amarillo y el rojo empieza por debajo.
 *
 * El color no se comprueba leyendo CSS: se comprueba el atributo `data-estado`,
 * que es el contrato entre este modulo y `componentes.css`. Probar el color
 * calculado seria probar el navegador, no nuestro codigo.
 */

import { clasificar, actualizar, inicializar, leerUmbrales } from './barra-vida.js';

/** Marcado minimo que documenta el propio modulo. */
const MARCADO = `
  <div class="barra-vida" data-barra-vida>
    <span class="barra-vida__nombre">Arquero del Norte</span>
    <div class="barra-vida__pista"><div class="barra-vida__relleno"></div></div>
    <span class="barra-vida__valor"></span>
  </div>
`;

/**
 * jsdom no carga tokens.css, asi que las fichas de diseno se ponen a mano.
 * Se usan los MISMOS valores que tokens.css para no probar contra numeros
 * inventados: --vida-umbral-alto: 60 y --vida-umbral-medio: 40.
 */
beforeAll(() => {
  document.documentElement.style.setProperty('--vida-umbral-alto', '60');
  document.documentElement.style.setProperty('--vida-umbral-medio', '40');
});

beforeEach(() => {
  document.body.innerHTML = MARCADO;
});

/** @returns {HTMLElement} */
function barra() {
  return /** @type {HTMLElement} */ (document.querySelector('.barra-vida'));
}

describe('leerUmbrales', () => {
  test('toma los umbrales de las fichas de diseno, no de constantes en el codigo', () => {
    expect(leerUmbrales()).toEqual({ alto: 60, medio: 40 });
  });
});

describe('clasificar · criterio 1 — umbrales de color', () => {
  test('con toda la vida es alto', () => {
    expect(clasificar(100)).toBe('alto');
  });

  test('justo por encima del 60 % sigue siendo alto', () => {
    expect(clasificar(60.1)).toBe('alto');
  });

  test('exactamente 60 % ya es medio: el verde es «por encima del 60 %»', () => {
    expect(clasificar(60)).toBe('medio');
  });

  test('a mitad de camino entre los dos umbrales es medio', () => {
    expect(clasificar(50)).toBe('medio');
  });

  test('exactamente 40 % es medio: el enunciado dice «40 % inclusive»', () => {
    expect(clasificar(40)).toBe('medio');
  });

  test('justo por debajo del 40 % es bajo', () => {
    expect(clasificar(39.9)).toBe('bajo');
  });

  test('sin vida es bajo', () => {
    expect(clasificar(0)).toBe('bajo');
  });
});

describe('actualizar · criterio 2 — valor numerico junto al color', () => {
  test('escribe el valor numerico, no solo el color', () => {
    actualizar(barra(), 30, 120);

    expect(barra().querySelector('.barra-vida__valor').textContent).toBe('30/120');
  });

  test('marca el estado que el CSS usa para pintar el color', () => {
    actualizar(barra(), 30, 120); // 25 % -> bajo

    expect(barra().dataset.estado).toBe('bajo');
  });

  test('expone el porcentaje como ficha para que el relleno crezca', () => {
    actualizar(barra(), 60, 120);

    expect(barra().style.getPropertyValue('--vida')).toBe('50');
  });

  test('el color nunca es el unico indicador: tambien lo anuncia el lector de pantalla', () => {
    actualizar(barra(), 45, 90);

    expect(barra().getAttribute('role')).toBe('progressbar');
    expect(barra().getAttribute('aria-valuenow')).toBe('45');
    expect(barra().getAttribute('aria-valuemax')).toBe('90');
    expect(barra().getAttribute('aria-label')).toBe('Vida de Arquero del Norte: 45 de 90');
  });

  test('una vida negativa se acota a cero en vez de romper la barra', () => {
    actualizar(barra(), -25, 100);

    expect(barra().querySelector('.barra-vida__valor').textContent).toBe('0/100');
    expect(barra().dataset.estado).toBe('bajo');
  });

  test('una vida por encima del maximo se acota al maximo', () => {
    actualizar(barra(), 250, 100);

    expect(barra().querySelector('.barra-vida__valor').textContent).toBe('100/100');
    expect(barra().dataset.estado).toBe('alto');
  });

  test('un maximo que no es positivo se rechaza: dividir por cero no es una barra', () => {
    expect(() => actualizar(barra(), 10, 0)).toThrow(RangeError);
  });

  test('rechaza algo que no es un elemento del documento', () => {
    expect(() => actualizar(null, 10, 100)).toThrow(TypeError);
  });
});

describe('inicializar · criterio 3 — todos los participantes', () => {
  test('pinta de una vez las barras de todos los participantes de la sala', () => {
    document.body.innerHTML = `
      <div class="barra-vida" data-barra-vida data-vida-actual="90"  data-vida-maxima="100"></div>
      <div class="barra-vida" data-barra-vida data-vida-actual="50"  data-vida-maxima="100"></div>
      <div class="barra-vida" data-barra-vida data-vida-actual="10"  data-vida-maxima="100"></div>
    `;

    inicializar();

    const estados = [...document.querySelectorAll('.barra-vida')].map((b) => b.dataset.estado);
    expect(estados).toEqual(['alto', 'medio', 'bajo']);
  });

  test('una barra sin datos de vida se deja intacta en vez de pintarse a cero', () => {
    document.body.innerHTML = `<div class="barra-vida" data-barra-vida></div>`;

    inicializar();

    expect(barra().dataset.estado).toBeUndefined();
  });
});
