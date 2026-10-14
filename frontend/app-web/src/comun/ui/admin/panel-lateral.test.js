/**
 * Panel lateral de la consola — SA-2: el diálogo del kit pegado al borde
 * derecho, con sus garantías (foco dentro, Escape, el foco vuelve).
 */

import { jest } from '@jest/globals';

import { abrirPanelLateral, listaDeDatos } from './panel-lateral.js';

beforeEach(() => {
  document.body.innerHTML = '<button type="button" id="origen">Ver ficha</button>';
  document.getElementById('origen').focus();
});

test('se abre como diálogo modal lateral, con título y contenido', () => {
  const panel = abrirPanelLateral({
    titulo: 'Ana',
    subtitulo: 'Jugadora desde enero',
    contenido: listaDeDatos([['Correo', 'ana@nexus.test']]),
  });

  const caja = panel.elemento;
  expect(caja.getAttribute('role')).toBe('dialog');
  expect(caja.getAttribute('aria-modal')).toBe('true');
  expect(caja.classList.contains('dialogo--lateral')).toBe(true);
  expect(caja.parentElement.classList.contains('velo--lateral')).toBe(true);
  expect(document.getElementById(caja.getAttribute('aria-labelledby')).textContent).toBe('Ana');
  expect(caja.querySelector('.panel-lateral__subtitulo').textContent).toBe('Jugadora desde enero');
  expect(caja.querySelector('dl dd').textContent).toBe('ana@nexus.test');
});

test('Escape lo cierra, llama a alCerrar una vez y el foco vuelve a quien lo abrió', () => {
  const alCerrar = jest.fn();
  abrirPanelLateral({ titulo: 'Ana', contenido: document.createElement('p'), alCerrar });

  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
  expect(document.querySelector('.dialogo--lateral')).toBeNull();
  expect(alCerrar).toHaveBeenCalledTimes(1);
  expect(document.activeElement.id).toBe('origen');
});

test('listaDeDatos dice «Sin dato» donde no hay valor y acepta nodos', () => {
  const enlace = document.createElement('a');
  enlace.textContent = 'Ver';
  const lista = listaDeDatos([
    ['Correo', null],
    ['Ficha', enlace],
    ['Créditos', 0],
  ]);
  const valores = [...lista.querySelectorAll('dd')];
  expect(valores[0].textContent).toBe('Sin dato');
  expect(valores[0].className).toBe('lista-datos-admin__sin-dato');
  expect(valores[1].querySelector('a')).toBe(enlace);
  expect(valores[2].textContent).toBe('0');
});
