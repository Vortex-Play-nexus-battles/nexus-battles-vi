import { jest } from '@jest/globals';
import { montarPanelCatalogo } from './panel-catalogo.js';

const resumenInicial = {
  total: 8,
  porTipo: {
    HEROE: 2,
    HABILIDAD: 1,
    ARMA: 2,
    ARMADURA: 1,
    ITEM: 1,
    EPICA: 1,
  },
  porEstado: {
    ACTIVO: 6,
    UNICO: 1,
    SUSPENDIDO: 1,
  },
};

async function esperarHasta(condicion) {
  for (let intento = 0; intento < 20; intento += 1) {
    if (condicion()) {
      return;
    }
    await new Promise((resolver) => setTimeout(resolver, 0));
  }

  throw new Error('El panel no terminó la actualización esperada');
}

let raiz;

beforeEach(() => {
  raiz = document.createElement('main');
  document.body.replaceChildren(raiz);
});

test('muestra el total y la distribución por tipos y estados', async () => {
  const consultar = jest.fn(async () => resumenInicial);
  const panel = montarPanelCatalogo(raiz, { consultar });

  await panel.cargaInicial;

  expect(consultar).toHaveBeenCalledTimes(1);
  expect(raiz.querySelector('[data-total-catalogo]').textContent).toBe('8');
  expect(raiz.querySelector('[data-tipo-catalogo="HEROE"]').textContent).toBe('2');
  expect(raiz.querySelector('[data-tipo-catalogo="EPICA"]').textContent).toBe('1');
  expect(raiz.querySelector('[data-estado-catalogo="ACTIVO"]').textContent).toBe('6');
  expect(raiz.querySelector('[data-estado-catalogo="SUSPENDIDO"]').textContent).toBe('1');
  expect(raiz.querySelector('[data-panel-mensaje]').textContent).toMatch(/actualizadas/i);
});

test('actualiza las cifras al volver a consultar el catálogo', async () => {
  const consultar = jest
    .fn()
    .mockResolvedValueOnce(resumenInicial)
    .mockResolvedValueOnce({
      total: 9,
      porTipo: {
        ...resumenInicial.porTipo,
        HEROE: 3,
      },
      porEstado: {
        ...resumenInicial.porEstado,
        ACTIVO: 7,
      },
    });

  const panel = montarPanelCatalogo(raiz, { consultar });
  await panel.cargaInicial;

  raiz.querySelector('[data-actualizar-panel]').click();

  await esperarHasta(() => raiz.querySelector('[data-total-catalogo]').textContent === '9');

  expect(consultar).toHaveBeenCalledTimes(2);
  expect(raiz.querySelector('[data-tipo-catalogo="HEROE"]').textContent).toBe('3');
  expect(raiz.querySelector('[data-estado-catalogo="ACTIVO"]').textContent).toBe('7');
});

test('muestra cero para cantidades ausentes o inválidas', async () => {
  const panel = montarPanelCatalogo(raiz, {
    consultar: async () => ({
      total: 0,
      porTipo: {},
      porEstado: {},
    }),
  });

  await panel.cargaInicial;

  expect(raiz.querySelector('[data-total-catalogo]').textContent).toBe('0');
  expect(raiz.querySelector('[data-tipo-catalogo="ARMA"]').textContent).toBe('0');
  expect(raiz.querySelector('[data-estado-catalogo="UNICO"]').textContent).toBe('0');
});

test('informa cuando la sesión no está disponible', async () => {
  const consola = jest.spyOn(console, 'error').mockImplementation(() => {});
  const panel = montarPanelCatalogo(raiz, {
    consultar: async () => {
      const fallo = new Error('401 interno');
      fallo.status = 401;
      throw fallo;
    },
  });

  await panel.cargaInicial;

  expect(raiz.querySelector('[data-panel-mensaje]').textContent).toMatch(/sesión/i);
  expect(raiz.querySelector('[data-panel-mensaje]').textContent).not.toMatch(/401/);
  expect(raiz.querySelector('[data-panel-mensaje]').getAttribute('role')).toBe('alert');
  consola.mockRestore();
});
