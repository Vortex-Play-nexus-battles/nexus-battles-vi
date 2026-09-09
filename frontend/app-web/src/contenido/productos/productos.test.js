import { jest } from '@jest/globals';
import { montarFormularioProductos } from './productos.js';

function completarArma(raiz) {
  const formulario = raiz.querySelector('form');
  formulario.elements.namedItem('nombre').value = 'Espada solar';
  formulario.elements.namedItem('imagen').value = '/img/espada.webp';
  formulario.elements.namedItem('descripcion').value = 'Arma de prueba';
  formulario.elements.namedItem('tiraje').value = '100';
  formulario.elements.namedItem('precioCreditos').value = '500';
  formulario.elements.namedItem('tipo').value = 'ARMA';
  formulario.elements.namedItem('tipo').dispatchEvent(new Event('change'));
  formulario.elements.namedItem('poderDeAtaque').value = '25';
  formulario.querySelector('[name="tasaDeCaida"]:not([disabled])').value = '10';
  return formulario;
}

async function esperarHasta(condicion) {
  for (let intento = 0; intento < 20; intento += 1) {
    if (condicion()) {
      return;
    }
    await new Promise((resolver) => setTimeout(resolver, 0));
  }
  throw new Error('La interfaz no terminó la operación esperada');
}

let raiz;
beforeEach(() => {
  raiz = document.createElement('main');
  document.body.replaceChildren(raiz);
});

test('muestra únicamente los atributos del tipo seleccionado', () => {
  montarFormularioProductos(raiz);
  const formulario = raiz.querySelector('form');
  formulario.elements.namedItem('tipo').value = 'ITEM';
  formulario.elements.namedItem('tipo').dispatchEvent(new Event('change'));

  expect(raiz.querySelector('[data-tipo="ITEM"]').hidden).toBe(false);
  expect(raiz.querySelector('[data-tipo="ARMA"]').hidden).toBe(true);
  expect(formulario.elements.namedItem('efecto').disabled).toBe(false);
  expect(formulario.elements.namedItem('poderDeAtaque').disabled).toBe(true);
});

test('cambia entre precio en créditos y moneda real', () => {
  montarFormularioProductos(raiz);
  const premium = raiz.querySelector('[name="premium"]');
  premium.checked = true;
  premium.dispatchEvent(new Event('change'));

  expect(raiz.querySelector('[data-precio="creditos"]').hidden).toBe(true);
  expect(raiz.querySelector('[name="precioCreditos"]').disabled).toBe(true);
  expect(raiz.querySelector('[data-precio="real"]').hidden).toBe(false);
});

test('crea el producto y muestra confirmación', async () => {
  const creado = {
    nombre: 'Espada solar',
    estado: 'ACTIVO',
    id: '550e8400-e29b-41d4-a716-446655440000',
  };
  const crear = jest.fn(async () => creado);
  montarFormularioProductos(raiz, { crear });
  const formulario = completarArma(raiz);
  formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await esperarHasta(() => raiz.querySelector('[data-estado]').dataset.estado === 'exito');
  expect(crear).toHaveBeenCalledWith(expect.objectContaining({ tipo: 'ARMA' }));
  expect(raiz.querySelector('[data-estado]').textContent).toMatch(/creado correctamente/i);
});

test('informa que falta sesión sin mostrar códigos técnicos', async () => {
  const consola = jest.spyOn(console, 'error').mockImplementation(() => {});
  montarFormularioProductos(raiz, {
    crear: async () => {
      const fallo = new Error('401 interno');
      fallo.status = 401;
      throw fallo;
    },
  });
  const formulario = completarArma(raiz);
  formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await esperarHasta(() => raiz.querySelector('[data-estado]').dataset.estado === 'error');
  expect(raiz.querySelector('[data-estado]').textContent).toMatch(/sesión/i);
  expect(raiz.querySelector('[data-estado]').textContent).not.toMatch(/401/);
  consola.mockRestore();
});
