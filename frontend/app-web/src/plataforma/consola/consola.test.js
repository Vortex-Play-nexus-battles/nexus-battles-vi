/**
 * El aterrizaje de la consola — UX-R3.0 / UX-R3.3, y B3: la cola de
 * comentarios reportados ya tiene su puerta.
 *
 * Existía (`moderar-comentarios.html`, RF-COM-005/008) y ninguna herramienta
 * de la consola llevaba a ella: un moderador solo llegaba escribiendo su
 * dirección. Lo que se fija aquí es que ahora aparece donde busca, con lo que
 * hace dicho en una línea, y que las tarjetas siguen saliendo de la misma
 * matriz que los guardas.
 */

import { montarConsola } from './consola.js';

const BASE = 'http://localhost:8099/frontend/app-web/src/comun/shell.js';

function montar(rol) {
  document.body.innerHTML = `
    <div data-zona="encabezado"></div>
    <div data-zona="herramientas"></div>
    <div data-zona="alcance"></div>`;
  montarConsola(document, { autenticado: true, apodo: 'qa', rol }, { base: BASE });
  return [...document.querySelectorAll('[data-herramienta]')];
}

test('el moderador encuentra la cola de comentarios entre sus herramientas', () => {
  const herramientas = montar('MODERADOR');

  expect(herramientas.map((t) => t.dataset.herramienta)).toEqual([
    'comentarios',
    'sanciones',
    'lista-negra',
  ]);
  const comentarios = herramientas[0];
  expect(comentarios.getAttribute('href')).toMatch(
    /plataforma\/comentarios\/moderar-comentarios\.html$/,
  );
  expect(comentarios.querySelector('.tarjeta__titulo').textContent).toBe('Comentarios');
  expect(comentarios.textContent).toMatch(/Comentarios reportados: aprobar, ocultar, editar/);
});

test('cada herramienta que se ve lleva su descripción: ninguna tarjeta muda', () => {
  const herramientas = montar('SUPER_ADMINISTRADOR');

  expect(herramientas.map((t) => t.dataset.herramienta)).toContain('comentarios');
  for (const tarjeta of herramientas) {
    expect(tarjeta.querySelector('.t-meta').textContent.trim()).not.toBe('');
  }
});
