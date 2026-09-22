/** Construccion de DOM — la pieza de mas abajo del kit (PR-UX-1). */

import { clases, h, nodo, vaciar } from './dom.js';

describe('h', () => {
  test('crea con etiqueta, clase, texto, datos y atributos', () => {
    const e = h('article', {
      clase: 'tarjeta',
      texto: 'Copa Otono',
      datos: { torneoId: 't-1', cupos: 8 },
      atributos: { role: 'listitem', 'aria-label': 'Torneo' },
    });

    expect(e.tagName).toBe('ARTICLE');
    expect(e.className).toBe('tarjeta');
    expect(e.textContent).toBe('Copa Otono');
    expect(e.dataset.torneoId).toBe('t-1');
    expect(e.dataset.cupos).toBe('8');
    expect(e.getAttribute('role')).toBe('listitem');
  });

  test('el texto NUNCA se interpreta como marcado', () => {
    const e = h('p', { texto: '<img src=x onerror="alert(1)">' });

    expect(e.querySelector('img')).toBeNull();
    expect(e.textContent).toContain('<img');
  });

  test('un atributo en true queda vacio y en false o null no se pone', () => {
    const e = h('input', { atributos: { disabled: true, readonly: false, placeholder: null } });

    expect(e.getAttribute('disabled')).toBe('');
    expect(e.hasAttribute('readonly')).toBe(false);
    expect(e.hasAttribute('placeholder')).toBe(false);
  });

  test('los hijos nulos se ignoran, para poder componer con condiciones', () => {
    const sinHijo = false;
    const e = h('div', { hijos: [h('span', { texto: 'a' }), null, undefined, sinHijo, 'texto'] });

    expect(e.childNodes).toHaveLength(2);
    expect(e.textContent).toBe('atexto');
  });
});

describe('nodo, vaciar y clases', () => {
  test('nodo es el atajo de etiqueta + clase + texto', () => {
    const e = nodo('h3', 'tarjeta__titulo', 'Los Valientes');

    expect(e.outerHTML).toBe('<h3 class="tarjeta__titulo">Los Valientes</h3>');
  });

  test('vaciar deja el contenedor sin hijos y lo devuelve', () => {
    const caja = h('div', { hijos: [h('p'), h('p')] });

    expect(vaciar(caja)).toBe(caja);
    expect(caja.childNodes).toHaveLength(0);
  });

  test('clases descarta lo vacio en vez de dejar espacios sueltos', () => {
    const nada = false;
    expect(clases('tarjeta', nada && 'x', null, undefined, '', 'tarjeta--rota')).toBe(
      'tarjeta tarjeta--rota',
    );
  });
});
