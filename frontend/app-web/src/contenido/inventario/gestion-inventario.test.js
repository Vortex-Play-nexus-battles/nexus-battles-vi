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
  throw new Error('La interfaz no termino la operación esperada');
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

  // RFINAL-04 — el alta manual es de la administración (inventario.yaml, B4).
  await montarInventario(raiz, 'jugador-A', 0, { consultar, crear, rol: 'ADMINISTRADOR' });
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

test('RFINAL-04: al jugador no se le ofrece «Agregar elemento» (el servidor le respondería 403)', async () => {
  const crear = jest.fn();
  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => pagina([]),
    crear,
    rol: 'JUGADOR',
  });

  const boton = raiz.querySelector('.inventario__nuevo');
  expect(boton.hidden).toBe(true);
  boton.click();
  expect(raiz.querySelector('.inventario-editor').hidden).toBe(true);
  expect(crear).not.toHaveBeenCalled();
});

test('RFINAL-04: sin rol conocido tampoco: el alta es solo de la administración', async () => {
  await montarInventario(raiz, 'jugador-A', 0, { consultar: async () => pagina([]), rol: null });

  expect(raiz.querySelector('.inventario__nuevo').hidden).toBe(true);
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
    const fallo = new Error('El servicio de inventario respondió 403');
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

test('si el servidor rechaza el producto, el formulario muestra su mensaje', async () => {
  const consola = jest.spyOn(console, 'error').mockImplementation(() => {});
  const consultar = async () => pagina([]);
  const crear = async () => {
    const fallo = new Error('El servicio de inventario respondio 422 al guardar');
    fallo.status = 422;
    fallo.detalle = 'El producto no existe en el catalogo.';
    throw fallo;
  };

  await montarInventario(raiz, 'jugador-A', 0, { consultar, crear, rol: 'ADMINISTRADOR' });
  raiz.querySelector('.inventario__nuevo').click();
  raiz.querySelector('[name="productoId"]').value = 'espada-corta';
  raiz.querySelector('[name="tipo"]').value = 'ARMA';
  raiz.querySelector('[name="nombrePropio"]').value = 'Espada inventada';
  raiz
    .querySelector('.inventario-editor__formulario')
    .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await esperarHasta(
    () => raiz.querySelector('.inventario__mensaje').getAttribute('role') === 'alert',
  );
  expect(raiz.querySelector('.inventario__mensaje').textContent).toBe(
    'El producto no existe en el catalogo.',
  );
  expect(raiz.querySelectorAll('.vitrina__producto')).toHaveLength(0);
  consola.mockRestore();
});

/*
 * HU-INV-008 — el jugador retira de su inventario lo que ya no usa.
 */

test('criterio 1: un elemento libre se retira del inventario', async () => {
  let elementos = [elemento('Amuleto de Niebla')];
  const consultar = async () => pagina(elementos);
  const eliminar = async (_identidad, elementoId) => {
    elementos = elementos.filter((e) => e.id !== elementoId);
  };

  await montarInventario(raiz, 'jugador-A', 0, { consultar, eliminar, confirmar: () => true });
  await esperarHasta(() => raiz.querySelectorAll('.vitrina__producto').length === 1);
  raiz.querySelector('.vitrina__eliminar').click();

  await esperarHasta(() => raiz.querySelectorAll('.vitrina__producto').length === 0);
  expect(raiz.querySelector('.inventario__mensaje').textContent).toMatch(/eliminado/i);
});

test('eliminar se confirma antes: si el jugador dice que no, no se toca nada', async () => {
  const eliminar = jest.fn();

  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => pagina([elemento()]),
    eliminar,
    confirmar: () => false,
  });
  await esperarHasta(() => raiz.querySelectorAll('.vitrina__producto').length === 1);
  raiz.querySelector('.vitrina__eliminar').click();

  expect(eliminar).not.toHaveBeenCalled();
  expect(raiz.querySelectorAll('.vitrina__producto')).toHaveLength(1);
});

test('criterio 2: un elemento comprometido se rechaza diciendo el motivo del bloqueo', async () => {
  const eliminar = async () => {
    const fallo = new Error('409');
    fallo.status = 409;
    // Lo que manda el servicio en el problem detail.
    fallo.detalle = 'El heroe esta en una mision y no se puede modificar hasta que vuelva.';
    throw fallo;
  };

  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => pagina([elemento()]),
    eliminar,
    confirmar: () => true,
  });
  await esperarHasta(() => raiz.querySelectorAll('.vitrina__producto').length === 1);
  raiz.querySelector('.vitrina__eliminar').click();

  await esperarHasta(() => /mision/i.test(raiz.querySelector('.inventario__mensaje').textContent));
  const mensaje = raiz.querySelector('.inventario__mensaje').textContent;
  expect(mensaje).toMatch(/mision/i);
  // El cliente tiene dicho que un codigo HTTP no se le ensena al jugador.
  expect(mensaje).not.toMatch(/409|http/i);
  // Y el elemento sigue ahi: el rechazo no retira nada.
  expect(raiz.querySelectorAll('.vitrina__producto')).toHaveLength(1);
});

test('criterio 3: eliminar un elemento ajeno se rechaza diciendo que no es suyo', async () => {
  const eliminar = async () => {
    const fallo = new Error('403');
    fallo.status = 403;
    throw fallo;
  };

  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => pagina([elemento()]),
    eliminar,
    confirmar: () => true,
  });
  await esperarHasta(() => raiz.querySelectorAll('.vitrina__producto').length === 1);
  raiz.querySelector('.vitrina__eliminar').click();

  await esperarHasta(() =>
    /no es tuyo/i.test(raiz.querySelector('.inventario__mensaje').textContent),
  );
  expect(raiz.querySelector('.inventario__mensaje').textContent).not.toMatch(/403|http/i);
});
