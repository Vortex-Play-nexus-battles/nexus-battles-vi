/**
 * HU-INV-003 - La creacion y la edicion se reflejan en la vitrina.
 */
import { jest } from '@jest/globals';
import { montarInventario } from './inventario.js';

function elemento(nombrePropio = 'Amuleto de Niebla') {
  return {
    id: 'elemento-1',
    productoId: 'producto-1',
    tipo: 'ITEM',
    nombrePropio,
  };
}

function pagina(elementos = []) {
  return {
    elementos,
    numero: 0,
    tamanio: 16,
    totalElementos: elementos.length,
    totalPaginas: elementos.length === 0 ? 0 : 1,
    ultima: true,
  };
}

async function esperarHasta(condicion) {
  for (let intento = 0; intento < 20; intento += 1) {
    if (condicion()) {
      return;
    }
    await new Promise((resolver) => setTimeout(resolver, 0));
  }
  throw new Error('La interfaz no termino la operacion esperada');
}

let raiz;
beforeEach(() => {
  raiz = document.createElement('main');
  document.body.replaceChildren(raiz);
});

test('crear vuelve a consultar y muestra el elemento nuevo', async () => {
  let elementos = [];
  const crear = async (_identidad, datos) => {
    elementos = [elemento(datos.nombrePropio)];
    return elementos[0];
  };
  const consultar = async () => pagina(elementos);

  await montarInventario(raiz, 'jugador-A', 0, { consultar, crear });
  raiz.querySelector('.inventario__nuevo').click();
  raiz.querySelector('[name="productoId"]').value = 'producto-1';
  raiz.querySelector('[name="tipo"]').value = 'ITEM';
  raiz.querySelector('[name="nombrePropio"]').value = 'Amuleto de Niebla';
  raiz
    .querySelector('.inventario-editor__formulario')
    .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await esperarHasta(() => raiz.querySelectorAll('.vitrina__producto').length === 1);
  expect(raiz.querySelector('.vitrina__nombre').textContent).toBe('Amuleto de Niebla');
  expect(raiz.querySelector('.inventario__mensaje').textContent).toMatch(/creado/i);
});

test('editar vuelve a consultar y muestra el nombre modificado', async () => {
  let elementos = [elemento()];
  const modificar = async (_identidad, _id, cambios) => {
    elementos = [{ ...elementos[0], nombrePropio: cambios.nombrePropio }];
    return elementos[0];
  };
  const consultar = async () => pagina(elementos);

  await montarInventario(raiz, 'jugador-A', 0, { consultar, modificar });
  raiz.querySelector('.vitrina__editar').click();

  expect(raiz.querySelector('[name="productoId"]').closest('label').hidden).toBe(true);
  expect(raiz.querySelector('[name="productoId"]').disabled).toBe(true);
  raiz.querySelector('[name="nombrePropio"]').value = 'Amuleto de Bruma';
  raiz
    .querySelector('.inventario-editor__formulario')
    .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await esperarHasta(() => raiz.querySelector('.vitrina__nombre').textContent.includes('Bruma'));
  expect(raiz.querySelector('.inventario__mensaje').textContent).toMatch(/actualizado/i);
});

test('un rechazo mantiene la vitrina anterior y muestra un mensaje sin codigo', async () => {
  const consola = jest.spyOn(console, 'error').mockImplementation(() => {});
  const consultar = async () => pagina([elemento()]);
  const modificar = async () => {
    const fallo = new Error('El servicio de inventario respondio 403');
    fallo.status = 403;
    throw fallo;
  };

  await montarInventario(raiz, 'jugador-A', 0, { consultar, modificar });
  raiz.querySelector('.vitrina__editar').click();
  raiz.querySelector('[name="nombrePropio"]').value = 'Nombre ajeno';
  raiz
    .querySelector('.inventario-editor__formulario')
    .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await esperarHasta(
    () => raiz.querySelector('.inventario__mensaje').getAttribute('role') === 'alert',
  );
  expect(raiz.querySelector('.vitrina__nombre').textContent).toBe('Amuleto de Niebla');
  expect(raiz.querySelector('.inventario__mensaje').textContent).not.toMatch(/403/);
  expect(raiz.querySelector('.inventario__mensaje').textContent).toMatch(/permiso/i);
  consola.mockRestore();
});
