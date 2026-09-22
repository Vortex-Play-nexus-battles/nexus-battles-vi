/**
 * Escapado — UX-R2.8c.
 *
 * Las pruebas de abajo son las cargas que de verdad se probaron contra
 * `pujas.js` antes de arreglarlo: un nombre de subasta con una imagen rota,
 * una descripcion que cierra un atributo, un apodo con un `javascript:`.
 */

import { esc, urlSegura } from './escapar.js';

describe('esc', () => {
  test('el nombre de una subasta con marcado se queda en texto', () => {
    const cargado = '<img src=x onerror="alert(1)">';
    const marcado = `<h3>${esc(cargado)}</h3>`;

    const caja = document.createElement('div');
    caja.innerHTML = marcado;

    expect(caja.querySelector('img')).toBeNull();
    expect(caja.querySelector('h3').textContent).toBe(cargado);
  });

  test('una comilla no puede cerrar un atributo entrecomillado', () => {
    // El vector real: `title="${sub.descripcion}"`.
    const cargado = '" onmouseover="alert(1)';
    const caja = document.createElement('div');
    caja.innerHTML = `<span title="${esc(cargado)}">x</span>`;

    const span = caja.querySelector('span');
    expect(span.getAttribute('onmouseover')).toBeNull();
    expect(span.getAttribute('title')).toBe(cargado);
  });

  test('la comilla simple tambien, porque hay atributos escritos asi', () => {
    const caja = document.createElement('div');
    caja.innerHTML = `<span title='${esc("' onclick='alert(1)")}'>x</span>`;

    expect(caja.querySelector('span').getAttribute('onclick')).toBeNull();
  });

  test('el ampersand va primero: si no, se escapa dos veces', () => {
    // Si `&` se sustituyera al final, `<` ya seria `&lt;` y pasaria a
    // `&amp;lt;`, que se lee «&lt;» en la pantalla.
    expect(esc('a<b')).toBe('a&lt;b');
    expect(esc('Tom & Jerry')).toBe('Tom &amp; Jerry');
  });

  test('null y undefined son cadena vacia, no la palabra «null»', () => {
    expect(esc(null)).toBe('');
    expect(esc(undefined)).toBe('');
  });

  test('los numeros y el cero pasan tal cual', () => {
    expect(esc(0)).toBe('0');
    expect(esc(1200)).toBe('1200');
  });

  test('un texto normal no se toca', () => {
    expect(esc('Espada de Vael')).toBe('Espada de Vael');
  });
});

describe('urlSegura', () => {
  test('javascript: no sobrevive', () => {
    expect(urlSegura('javascript:alert(1)')).toBe('');
    expect(urlSegura('JaVaScRiPt:alert(1)')).toBe('');
  });

  test('data: tampoco', () => {
    expect(urlSegura('data:text/html,<script>alert(1)</script>')).toBe('');
  });

  test('http y https pasan', () => {
    expect(urlSegura('https://ejemplo.test/a.png')).toBe('https://ejemplo.test/a.png');
  });

  test('una ruta relativa se resuelve contra el origen', () => {
    expect(urlSegura('/imagenes/espada.png')).toContain('/imagenes/espada.png');
  });

  test('lo vacio y lo ilegible devuelven la alternativa', () => {
    expect(urlSegura(null, './sin-imagen.png')).toBe('./sin-imagen.png');
    expect(urlSegura('http://[', './sin-imagen.png')).toBe('./sin-imagen.png');
  });

  test('lo que devuelve ya viene escapado', () => {
    const caja = document.createElement('div');
    caja.innerHTML = `<a href="${urlSegura('https://ejemplo.test/?a=1&b=2')}">x</a>`;
    expect(caja.querySelector('a').getAttribute('href')).toBe('https://ejemplo.test/?a=1&b=2');
  });
});
