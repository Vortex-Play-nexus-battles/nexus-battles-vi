import { jest } from '@jest/globals';
import { montarInventario } from './inventario.js';

const elementos = [
  { id: 'heroe-1', productoId: 'p-heroe', tipo: 'HEROE', nombrePropio: 'Ayla' },
  { id: 'arma-1', productoId: 'p-arma', tipo: 'ARMA', nombrePropio: 'Espada' },
  {
    id: 'casco-1',
    productoId: 'p-casco',
    tipo: 'ARMADURA',
    nombrePropio: 'Casco',
    parteArmadura: 'CASCO',
  },
];

function pagina() {
  return {
    elementos,
    numero: 0,
    tamanio: 16,
    totalElementos: elementos.length,
    totalPaginas: 1,
    ultima: true,
  };
}

function vacio() {
  return { heroeId: 'heroe-1', armas: [], armaduras: {}, items: [] };
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

test('abre el equipo del heroe y muestra sus limites', async () => {
  const consultarEquipo = jest.fn(async () => vacio());
  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => pagina(),
    consultarEquipo,
  });

  raiz.querySelector('.vitrina__equipo').click();
  await esperarHasta(() => !raiz.querySelector('.inventario-equipo').hidden);

  expect(consultarEquipo).toHaveBeenCalledWith('jugador-A', 'heroe-1');
  expect(raiz.querySelector('.inventario-equipo__resumen').textContent).toMatch(/Armas 0\/2/);
  expect(raiz.querySelectorAll('.inventario-equipo__elemento')).toHaveLength(2);
});

test('equipa y luego ofrece desequipar el elemento', async () => {
  const equipar = jest.fn(async () => ({ ...vacio(), armas: ['arma-1'] }));
  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => pagina(),
    consultarEquipo: async () => vacio(),
    equipar,
  });
  raiz.querySelector('.vitrina__equipo').click();
  await esperarHasta(() => !raiz.querySelector('.inventario-equipo').hidden);

  raiz.querySelector('.inventario-equipo__equipar').click();
  await esperarHasta(() => raiz.querySelector('.inventario-equipo__desequipar'));

  expect(equipar).toHaveBeenCalledWith('jugador-A', 'heroe-1', 'arma-1');
  expect(raiz.querySelector('.inventario-equipo__resumen').textContent).toMatch(/Armas 1\/2/);
  expect(raiz.querySelector('.inventario__mensaje').textContent).toBe('Elemento equipado.');
});

test('un limite rechazado se explica sin mostrar el codigo', async () => {
  const consola = jest.spyOn(console, 'error').mockImplementation(() => {});
  const equipar = async () => {
    const error = new Error('409');
    error.status = 409;
    throw error;
  };
  await montarInventario(raiz, 'jugador-A', 0, {
    consultar: async () => pagina(),
    consultarEquipo: async () => vacio(),
    equipar,
  });
  raiz.querySelector('.vitrina__equipo').click();
  await esperarHasta(() => !raiz.querySelector('.inventario-equipo').hidden);

  raiz.querySelector('.inventario-equipo__equipar').click();
  await esperarHasta(
    () => raiz.querySelector('.inventario__mensaje').getAttribute('role') === 'alert',
  );

  expect(raiz.querySelector('.inventario__mensaje').textContent).toMatch(/límites/i);
  expect(raiz.querySelector('.inventario__mensaje').textContent).not.toMatch(/409/);
  consola.mockRestore();
});
