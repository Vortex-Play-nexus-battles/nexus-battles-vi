/**
 * Vista de administración de banners — HU-PRD-013 (#854).
 *
 * Lo que fija esta suite son los dos arreglos que el PR necesitaba para pasar
 * sus guardianes: retirar se confirma con el diálogo del kit (no con
 * `window.confirm()`, que `sin-dialogos-nativos.test.js` prohíbe) y las fechas
 * salen del formateador común, no de `toLocaleString()` a secas.
 */

import { jest } from '@jest/globals';

import { fechaHora } from '../../comun/ui/formato.js';
import { montarBanners } from './banners.js';

const asentar = () => new Promise((resolver) => setTimeout(resolver, 0));

const VIGENTE = {
  id: 'b-1',
  contenido: 'Temporada de héroes',
  publicarDesde: '2026-10-01T15:00:00Z',
  vigenteHasta: '2099-10-31T23:00:00Z',
  retirado: false,
};
const RETIRADO = { ...VIGENTE, id: 'b-2', contenido: 'Anuncio viejo', retirado: true };

function api(banners = [VIGENTE, RETIRADO]) {
  return {
    listarBanners: jest.fn().mockResolvedValue(banners),
    crearBanner: jest.fn().mockResolvedValue({}),
    editarBanner: jest.fn().mockResolvedValue({}),
    retirarBanner: jest.fn().mockResolvedValue(null),
  };
}

function botonRetirarDe(contenido) {
  const tarjeta = [...document.querySelectorAll('.banners__lista article')].find((t) =>
    t.textContent.includes(contenido),
  );
  return [...tarjeta.querySelectorAll('button')].find((b) => b.textContent === 'Retirar');
}

beforeEach(() => {
  document.body.innerHTML = '<main id="banners-app"></main>';
});

test('lista cada banner con su estado y sus fechas en el formato común', async () => {
  montarBanners(document.getElementById('banners-app'), api());
  await asentar();

  const lista = document.querySelector('.banners__lista').textContent;
  expect(lista).toContain('Temporada de héroes');
  expect(lista).toContain('Vigente');
  expect(lista).toContain('Retirado');
  expect(lista).toContain(fechaHora(VIGENTE.publicarDesde));
  expect(lista).toContain(fechaHora(VIGENTE.vigenteHasta));
  expect(botonRetirarDe('Anuncio viejo').disabled).toBe(true);
});

test('retirar pregunta con el diálogo del kit y, si se cancela, no toca el servidor', async () => {
  const servicio = api();
  const confirmar = jest.fn().mockResolvedValue(false);
  montarBanners(document.getElementById('banners-app'), servicio, { confirmar });
  await asentar();

  botonRetirarDe('Temporada de héroes').click();
  await asentar();

  expect(confirmar).toHaveBeenCalledWith(VIGENTE);
  expect(servicio.retirarBanner).not.toHaveBeenCalled();
});

test('al confirmar, retira ese banner y vuelve a pedir la lista', async () => {
  const servicio = api();
  montarBanners(document.getElementById('banners-app'), servicio, {
    confirmar: jest.fn().mockResolvedValue(true),
  });
  await asentar();

  botonRetirarDe('Temporada de héroes').click();
  await asentar();
  await asentar();

  expect(servicio.retirarBanner).toHaveBeenCalledWith('b-1');
  expect(servicio.listarBanners).toHaveBeenCalledTimes(2);
});

test('el diálogo por omisión es el del kit, no el del navegador', async () => {
  const nativo = jest.fn(() => true);
  window.confirm = nativo;
  montarBanners(document.getElementById('banners-app'), api());
  await asentar();

  botonRetirarDe('Temporada de héroes').click();
  await asentar();

  expect(nativo).not.toHaveBeenCalled();
  const dialogo = document.querySelector('[role="dialog"], [role="alertdialog"]');
  expect(dialogo).not.toBeNull();
  expect(dialogo.textContent).toContain('Retirar');
});

test('un fallo que no es para personas no llega a la pantalla', async () => {
  const servicio = api();
  servicio.listarBanners.mockRejectedValue(
    new SyntaxError('Unexpected token < in JSON at position 0'),
  );
  montarBanners(document.getElementById('banners-app'), servicio);
  await asentar();

  const texto = document.getElementById('banners-app').textContent;
  expect(texto).not.toMatch(/Unexpected|JSON/);
  expect(texto).toContain('No se pudieron cargar los banners');
});
