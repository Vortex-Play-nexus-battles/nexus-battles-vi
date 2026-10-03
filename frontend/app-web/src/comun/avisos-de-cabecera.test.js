/**
 * Auditoría de DEV del 30-sep: el contador de la campana solo se encendía al
 * abrir la bandeja. La cabecera lo monta en todas las vistas con sesión.
 */
import { jest } from '@jest/globals';

import { hayRed, montarAvisosDeCabecera } from './avisos-de-cabecera.js';

function cabecera({ conContador = true } = {}) {
  const raiz = document.createElement('header');
  raiz.innerHTML = conContador
    ? '<a class="cabecera__campana"><span data-zona="contador" hidden>0</span></a>'
    : '<a class="cabecera__campana"></a>';
  return raiz;
}

describe('montarAvisosDeCabecera', () => {
  test('con contador y servicio, carga el contador de notificaciones y lo monta sobre la cabecera', async () => {
    const montarContadorDeCabecera = jest.fn(() => ({ bandeja: true }));
    const cargar = jest.fn(async () => ({ montarContadorDeCabecera }));
    const raiz = cabecera();

    const resultado = await montarAvisosDeCabecera({ raiz, cargar, conRed: () => true });

    expect(cargar).toHaveBeenCalledTimes(1);
    expect(montarContadorDeCabecera).toHaveBeenCalledWith(raiz);
    expect(resultado).toEqual({ bandeja: true });
  });

  test('sin contador en la cabecera (la consola) no carga nada', () => {
    const cargar = jest.fn();
    expect(
      montarAvisosDeCabecera({
        raiz: cabecera({ conContador: false }),
        cargar,
        conRed: () => true,
      }),
    ).toBeNull();
    expect(cargar).not.toHaveBeenCalled();
  });

  test('sin servicio al que preguntar (jsdom) no abre nada', () => {
    const cargar = jest.fn();
    expect(montarAvisosDeCabecera({ raiz: cabecera(), cargar })).toBeNull();
    expect(hayRed()).toBe(false);
    expect(cargar).not.toHaveBeenCalled();
  });

  test('si el módulo no carga, la cabecera sigue y se dice en la consola', async () => {
    const aviso = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const cargar = jest.fn(async () => {
      throw new Error('sin red');
    });

    await expect(
      montarAvisosDeCabecera({ raiz: cabecera(), cargar, conRed: () => true }),
    ).resolves.toBeNull();
    expect(aviso).toHaveBeenCalled();
    aviso.mockRestore();
  });
});
