/**
 * HU-INV-002 - Campo de busqueda en la vitrina.
 */
import { jest } from '@jest/globals';
import { montarInventario } from './inventario.js';

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

function elemento(nombrePropio = 'Espada de Bruma') {
  return {
    id: 'elemento-1',
    productoId: 'producto-1',
    tipo: 'ARMA',
    nombrePropio,
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

function enviar(formulario) {
  formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

let raiz;
beforeEach(() => {
  raiz = document.createElement('main');
  document.body.replaceChildren(raiz);
});

test('muestra un campo de busqueda con minimo de cuatro caracteres', async () => {
  await montarInventario(raiz, 'jugador-A', 0, { consultar: async () => pagina() });

  const formulario = raiz.querySelector('[role="search"]');
  const campo = formulario.querySelector('[name="criterio"]');
  expect(campo.type).toBe('search');
  expect(campo.minLength).toBe(4);
  expect(formulario.querySelector('label').textContent).toMatch(/buscar productos/i);
});

test('no consulta cuando el jugador escribe menos de cuatro caracteres', async () => {
  let llamadas = 0;
  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => pagina(),
    buscar: async () => {
      llamadas += 1;
      return pagina();
    },
  });

  raiz.querySelector('[name="criterio"]').value = 'arm';
  enviar(raiz.querySelector('.inventario-busqueda'));

  expect(llamadas).toBe(0);
  expect(raiz.querySelector('.inventario__mensaje').textContent).toMatch(/cuatro caracteres/i);
  expect(raiz.querySelector('.inventario__mensaje').getAttribute('role')).toBe('alert');
});

test('busca el criterio limpio y muestra los productos coincidentes', async () => {
  const llamadas = [];
  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => pagina(),
    buscar: async (...argumentos) => {
      llamadas.push(argumentos);
      return pagina([elemento()]);
    },
  });

  raiz.querySelector('[name="criterio"]').value = '  espada  ';
  enviar(raiz.querySelector('.inventario-busqueda'));

  await esperarHasta(() => raiz.querySelectorAll('.vitrina__producto').length === 1);
  expect(llamadas).toEqual([['jugador-A', 'espada', 0]]);
  expect(raiz.querySelector('.vitrina__nombre').textContent).toBe('Espada de Bruma');
  expect(raiz.querySelector('.inventario__mensaje').textContent).toMatch(/1 resultado/);
  expect(raiz.querySelector('.inventario-busqueda__limpiar').hidden).toBe(false);
});

test('explica cuando una busqueda valida no tiene coincidencias', async () => {
  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => pagina([elemento()]),
    buscar: async () => pagina(),
  });

  raiz.querySelector('[name="criterio"]').value = 'escudo';
  enviar(raiz.querySelector('.inventario-busqueda'));

  await esperarHasta(() => raiz.querySelector('.estado-vacio'));
  expect(raiz.querySelector('.estado-vacio').textContent).toMatch(/no encontramos productos/i);
  expect(raiz.querySelector('.inventario__mensaje').textContent).toMatch(/0 resultados/);
});

test('un fallo de busqueda se informa sin dejar el estado de carga', async () => {
  const consola = jest.spyOn(console, 'error').mockImplementation(() => {});
  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => pagina([elemento()]),
    buscar: async () => {
      throw new Error('El servicio respondio 503');
    },
  });

  raiz.querySelector('[name="criterio"]').value = 'espada';
  enviar(raiz.querySelector('.inventario-busqueda'));

  await esperarHasta(
    () => raiz.querySelector('.inventario__mensaje').getAttribute('role') === 'alert',
  );
  expect(raiz.querySelector('.inventario__mensaje').textContent).toMatch(/no pudimos realizar/i);
  expect(raiz.querySelector('.inventario__mensaje').textContent).not.toMatch(/503/);
  consola.mockRestore();
});

test('limpiar restaura el inventario completo', async () => {
  let consultasCompletas = 0;
  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => {
      consultasCompletas += 1;
      return pagina([elemento('Inventario completo')]);
    },
    buscar: async () => pagina([elemento('Resultado filtrado')]),
  });

  raiz.querySelector('[name="criterio"]').value = 'resultado';
  enviar(raiz.querySelector('.inventario-busqueda'));
  await esperarHasta(() =>
    raiz.querySelector('.vitrina__nombre')?.textContent.includes('filtrado'),
  );
  raiz.querySelector('.inventario-busqueda__limpiar').click();

  await esperarHasta(() =>
    raiz.querySelector('.vitrina__nombre')?.textContent.includes('completo'),
  );
  expect(consultasCompletas).toBe(2);
  expect(raiz.querySelector('[name="criterio"]').value).toBe('');
  expect(raiz.querySelector('.inventario-busqueda__limpiar').hidden).toBe(true);
});
