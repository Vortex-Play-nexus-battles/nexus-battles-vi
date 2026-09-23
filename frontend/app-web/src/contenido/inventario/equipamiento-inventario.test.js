/**
 * Equipamiento del héroe — HU-INV-005 #85, sobre las ranuras de UX-R2.5.
 *
 * Lo que se comprueba es el **comportamiento**, que no cambió: se consulta el
 * equipo del héroe, se equipa, se desequipa y un rechazo del servicio se
 * explica sin enseñar su código. Lo que cambió es la presentación —de una
 * lista de botones a las diez ranuras del contrato— y por eso los selectores
 * son otros.
 */

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
  throw new Error('La interfaz no termino la operación esperada');
}

/** La ranura por su etiqueta visible, que es como la encuentra un jugador. */
function ranuraPorEtiqueta(raiz, etiqueta) {
  return [...raiz.querySelectorAll('.ranura')].find(
    (r) => r.querySelector('.ranura__etiqueta')?.textContent === etiqueta,
  );
}

let raiz;
beforeEach(() => {
  raiz = document.createElement('main');
  document.body.replaceChildren(raiz);
});

async function abrirEquipo(opciones) {
  await montarInventario(raiz, 'jugador-A', 0, { consultar: async () => pagina(), ...opciones });
  raiz.querySelector('.vitrina__equipo').click();
  await esperarHasta(() => !raiz.querySelector('.inventario-equipo').hidden);
}

test('abre el equipo del heroe y pinta sus diez ranuras', async () => {
  const consultarEquipo = jest.fn(async () => vacio());
  await abrirEquipo({ consultarEquipo });

  expect(consultarEquipo).toHaveBeenCalledWith('jugador-A', 'heroe-1');
  // 2 armas + 6 armaduras + 2 ítems: los límites de `EquipamientoHeroe` son
  // ahora la forma de la pantalla, no un contador que hay que leer.
  expect(raiz.querySelectorAll('.ranura')).toHaveLength(10);
  expect(raiz.querySelectorAll('.grupo-ranuras')).toHaveLength(3);

  // Tres quedan como «vacía, elige algo» —las que tienen candidato en el
  // inventario— y siete como «bloqueada, no tienes la pieza». La diferencia
  // importa: una invita a pulsar y la otra explica por qué no.
  expect(raiz.querySelectorAll('.ranura--vacia')).toHaveLength(3);
  expect(raiz.querySelectorAll('.ranura--bloqueada')).toHaveLength(7);
});

test('el recuento sigue escrito para quien usa lector de pantalla', async () => {
  await abrirEquipo({ consultarEquipo: async () => vacio() });

  const resumen = raiz.querySelector('.equipamiento__resumen');
  expect(resumen.textContent).toMatch(/Armas 0\/2/);
  expect(resumen.textContent).toMatch(/Armadura 0\/6/);
  expect(resumen.getAttribute('aria-live')).toBe('polite');
});

test('cada parte de la armadura tiene su propia ranura, con su nombre', async () => {
  await abrirEquipo({ consultarEquipo: async () => vacio() });

  for (const parte of ['Casco', 'Pecho', 'Brazaletes', 'Guantes', 'Pantalón', 'Zapatos']) {
    expect(ranuraPorEtiqueta(raiz, parte)).toBeDefined();
  }
});

test('una ranura sin nada que ponerle dice POR QUE, en vez de no hacer nada', async () => {
  await abrirEquipo({ consultarEquipo: async () => vacio() });

  // En la página solo hay un casco: las otras cinco partes no tienen candidato.
  const pecho = ranuraPorEtiqueta(raiz, 'Pecho');
  expect(pecho.className).toContain('ranura--bloqueada');
  expect(pecho.querySelector('.ranura__caja').getAttribute('title')).toMatch(/No tienes/i);

  // La del casco sí, porque hay uno disponible.
  expect(ranuraPorEtiqueta(raiz, 'Casco').className).not.toContain('ranura--bloqueada');
});

test('equipar: se elige el objeto en el dialogo y la ranura pasa a ocupada', async () => {
  const equipar = jest.fn(async () => ({ ...vacio(), armas: ['arma-1'] }));
  await abrirEquipo({ consultarEquipo: async () => vacio(), equipar });

  ranuraPorEtiqueta(raiz, 'Arma 1').querySelector('.ranura__caja').click();

  // El diálogo del kit, con el único candidato compatible.
  const opcion = document.querySelector('[data-elegir="arma-1"]');
  expect(opcion.textContent).toBe('Espada');
  opcion.click();

  await esperarHasta(() => ranuraPorEtiqueta(raiz, 'Arma 1')?.querySelector('.ranura__caja'));
  await esperarHasta(() =>
    raiz.querySelector('.equipamiento__resumen').textContent.includes('1/2'),
  );

  expect(equipar).toHaveBeenCalledWith('jugador-A', 'heroe-1', 'arma-1');
  expect(raiz.querySelector('.inventario__mensaje').textContent).toBe('Elemento equipado.');

  // La ranura ocupada pasa a llamarse por lo que lleva puesto —«Espada»— y no
  // por su posición: un icono de espada no distingue dos espadas, y el hueco
  // ya no está vacío. La posición sigue en el nombre accesible.
  const ocupada = ranuraPorEtiqueta(raiz, 'Espada');
  expect(ocupada).toBeDefined();
  expect(ocupada.className).not.toContain('ranura--vacia');
  expect(ocupada.querySelector('.ranura__caja').getAttribute('aria-label')).toBe(
    'Arma 1: Espada. Cambiar',
  );
});

test('el dialogo NO ofrece un arma para la ranura del casco', async () => {
  await abrirEquipo({ consultarEquipo: async () => vacio() });

  ranuraPorEtiqueta(raiz, 'Casco').querySelector('.ranura__caja').click();

  expect(document.querySelector('[data-elegir="casco-1"]')).not.toBeNull();
  // El arma existe en el inventario, pero no cabe aquí: ofrecerla sería
  // invitar a un 409 que el jugador no ha provocado.
  expect(document.querySelector('[data-elegir="arma-1"]')).toBeNull();
});

test('desequipar: pulsar una ranura ocupada la vacia', async () => {
  const desequipar = jest.fn(async () => vacio());
  await abrirEquipo({
    consultarEquipo: async () => ({ ...vacio(), armas: ['arma-1'] }),
    desequipar,
  });

  const ocupada = ranuraPorEtiqueta(raiz, 'Espada');
  expect(ocupada).toBeDefined();
  ocupada.querySelector('.ranura__caja').click();

  await esperarHasta(() => raiz.querySelector('.inventario__mensaje').textContent !== '');
  expect(desequipar).toHaveBeenCalledWith('jugador-A', 'heroe-1', 'arma-1');
  expect(raiz.querySelector('.inventario__mensaje').textContent).toBe('Elemento desequipado.');
});

test('un limite rechazado se explica sin mostrar el codigo', async () => {
  const consola = jest.spyOn(console, 'error').mockImplementation(() => {});
  const equipar = async () => {
    const error = new Error('409');
    error.status = 409;
    throw error;
  };
  await abrirEquipo({ consultarEquipo: async () => vacio(), equipar });

  ranuraPorEtiqueta(raiz, 'Arma 1').querySelector('.ranura__caja').click();
  document.querySelector('[data-elegir="arma-1"]').click();

  await esperarHasta(
    () => raiz.querySelector('.inventario__mensaje').getAttribute('role') === 'alert',
  );

  expect(raiz.querySelector('.inventario__mensaje').textContent).toMatch(/límite/i);
  expect(raiz.querySelector('.inventario__mensaje').textContent).not.toMatch(/409/);
  consola.mockRestore();
});

test('cada código del contrato tiene su frase, y ninguna lleva el número', async () => {
  const consola = jest.spyOn(console, 'error').mockImplementation(() => {});
  // 400 `ElementoNoEquipable`, 403 `InventarioAjeno`, 404 `ElementoNoEncontrado`.
  for (const [estado, esperado] of [
    [400, /no ocupa esa ranura/i],
    [403, /no es tuyo/i],
    [404, /ya no está/i],
  ]) {
    document.body.replaceChildren((raiz = document.createElement('main')));
    await abrirEquipo({
      consultarEquipo: async () => vacio(),
      equipar: async () => {
        const error = new Error(String(estado));
        error.status = estado;
        throw error;
      },
    });
    ranuraPorEtiqueta(raiz, 'Arma 1').querySelector('.ranura__caja').click();
    document.querySelector('[data-elegir="arma-1"]').click();
    await esperarHasta(
      () => raiz.querySelector('.inventario__mensaje').getAttribute('role') === 'alert',
    );

    const texto = raiz.querySelector('.inventario__mensaje').textContent;
    expect(texto).toMatch(esperado);
    expect(texto).not.toMatch(new RegExp(String(estado)));
  }
  consola.mockRestore();
});
