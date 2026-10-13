/**
 * RFINAL-04 — «Registrar mi equipo» sin UUID ni dirección de imagen.
 */

import { jest } from '@jest/globals';

import {
  EMBLEMAS,
  MINIMO_DE_BUSQUEDA,
  buscarCompaneros,
  emblemaDe,
  imagenDeEmblema,
  selectorDeCompanero,
  selectorDeEmblema,
} from './equipo-formulario.js';

const YO = '11111111-1111-1111-1111-111111111111';
const OTRO = '22222222-2222-2222-2222-222222222222';

function respuesta(cuerpo, ok = true) {
  return Promise.resolve({ ok, status: ok ? 200 : 503, json: () => Promise.resolve(cuerpo) });
}

const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('buscarCompaneros', () => {
  test('pregunta a la búsqueda pública por apodo y no se devuelve a sí mismo', async () => {
    const fetchImpl = jest.fn(() =>
      respuesta([
        { uid: YO, apodo: 'Yo', avatar: null },
        { uid: OTRO, apodo: 'Compa', avatar: '/avatares-subidos/x.jpg' },
      ]),
    );

    const encontrados = await buscarCompaneros('  Com ', { fetchImpl, yo: YO });

    expect(String(fetchImpl.mock.calls[0][0])).toMatch(/\/api\/v1\/perfiles\/publicos\?apodo=Com$/);
    expect(encontrados).toEqual([{ uid: OTRO, apodo: 'Compa', avatar: '/avatares-subidos/x.jpg' }]);
  });

  test(`con menos de ${MINIMO_DE_BUSQUEDA} letras no molesta al servicio`, async () => {
    const fetchImpl = jest.fn();

    expect(await buscarCompaneros('co', { fetchImpl })).toEqual([]);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  test('si el servicio no responde, lo dice con un error (la vista lo traduce)', async () => {
    await expect(
      buscarCompaneros('Compa', { fetchImpl: () => respuesta(null, false) }),
    ).rejects.toThrow();
  });
});

describe('emblemas', () => {
  test('cada emblema tiene un id neutro (no puede chocar con la lista negra) y su imagen', () => {
    for (const emblema of EMBLEMAS) {
      expect(emblema.id).toMatch(/^emblema-\d{2}$/);
      expect(imagenDeEmblema(emblema)).toMatch(/\/cuentas\/avatares\/.+\.jpg$/);
    }
    expect(new Set(EMBLEMAS.map((e) => e.id)).size).toBe(EMBLEMAS.length);
  });

  test('emblemaDe reconoce los nuestros y nada más', () => {
    expect(emblemaDe('emblema-03').nombre).toBe('Fuego');
    expect(emblemaDe('maquina')).toBeNull();
    expect(emblemaDe('https://ejemplo.test/a.png')).toBeNull();
    expect(emblemaDe(null)).toBeNull();
  });

  test('el selector empieza con un emblema elegido y su imagen a la vista', () => {
    const selector = selectorDeEmblema();
    document.body.replaceChildren(selector.elemento);

    expect(selector.elegido()).toBe(EMBLEMAS[0].id);
    const control = document.querySelector('[name="emblema"]');
    control.value = 'emblema-04';
    control.dispatchEvent(new Event('change'));
    expect(selector.elegido()).toBe('emblema-04');
    expect(document.querySelector('[data-zona="emblema-previo"]').alt).toBe('Emblema Hielo');
  });
});

describe('selectorDeCompanero', () => {
  test('se busca, se elige y queda el uid sin que la persona lo vea', async () => {
    const fetchImpl = jest.fn(() => respuesta([{ uid: OTRO, apodo: 'Compa', avatar: null }]));
    const selector = selectorDeCompanero({ fetchImpl, yo: YO });
    document.body.replaceChildren(selector.elemento);

    document.querySelector('[name="apodoCompanero"]').value = 'Com';
    document.querySelector('[data-accion="buscar-companero"]').click();
    await asentar();
    document.querySelector(`[data-jugador="${OTRO}"]`).click();

    expect(selector.elegido()).toEqual({ uid: OTRO, apodo: 'Compa' });
    expect(document.body.textContent).not.toContain(OTRO);
  });

  test('Enter en la búsqueda busca, no envía el formulario del equipo', async () => {
    const fetchImpl = jest.fn(() => respuesta([]));
    const selector = selectorDeCompanero({ fetchImpl });
    document.body.replaceChildren(selector.elemento);
    const control = document.querySelector('[name="apodoCompanero"]');
    control.value = 'Nadie';

    const enter = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
    control.dispatchEvent(enter);
    await asentar();

    expect(enter.defaultPrevented).toBe(true);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(document.querySelector('[data-zona="companeros"]').textContent).toMatch(
      /Ningún jugador/,
    );
  });

  test('cambiar el texto después de elegir deshace la elección', async () => {
    const fetchImpl = jest.fn(() => respuesta([{ uid: OTRO, apodo: 'Compa', avatar: null }]));
    const selector = selectorDeCompanero({ fetchImpl });
    document.body.replaceChildren(selector.elemento);
    const control = document.querySelector('[name="apodoCompanero"]');
    control.value = 'Com';
    document.querySelector('[data-accion="buscar-companero"]').click();
    await asentar();
    document.querySelector(`[data-jugador="${OTRO}"]`).click();

    control.value = 'Otro';
    control.dispatchEvent(new Event('input'));

    expect(selector.elegido()).toBeNull();
  });

  test('si la búsqueda falla lo dice sin códigos', async () => {
    const selector = selectorDeCompanero({ fetchImpl: () => respuesta(null, false) });
    document.body.replaceChildren(selector.elemento);
    document.querySelector('[name="apodoCompanero"]').value = 'Compa';
    document.querySelector('[data-accion="buscar-companero"]').click();
    await asentar();

    const texto = document.querySelector('[data-zona="companeros"]').textContent;
    expect(texto).toMatch(/No pudimos buscar/);
    expect(texto).not.toMatch(/503/);
  });
});
