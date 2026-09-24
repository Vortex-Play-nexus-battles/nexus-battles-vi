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

/**
 * Abre el selector de una ranura y espera su primera tanda — FI-R7.
 *
 * El dialogo ya no recibe los candidatos resueltos: los pide al inventario al
 * abrirse, en tandas. Antes de FI-R7 la lista estaba pintada en el mismo tic
 * que el clic, y estas pruebas afirmaban justo despues.
 */
async function abrirSelector(raizVista, etiqueta) {
  ranuraPorEtiqueta(raizVista, etiqueta).querySelector('.ranura__caja').click();
  await esperarHasta(
    () =>
      document.querySelector('[data-elegir]') ||
      document.querySelector('[data-zona="dialogo"] .t-meta')?.textContent?.includes('No tienes'),
  );
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

  // FI-R7 — las diez quedan como «vacía, elige algo». Antes siete salían
  // «bloqueada, no tienes la pieza», y esa frase se decía tras mirar los
  // dieciséis elementos de UNA página: con cincuenta piezas en el inventario
  // era una afirmación sobre lo que no se había leído. Ahora el selector
  // recorre el inventario entero en tandas, y es el diálogo —después de
  // buscar— el que puede decir que no hay nada.
  expect(raiz.querySelectorAll('.ranura--vacia')).toHaveLength(10);
  expect(raiz.querySelectorAll('.ranura--bloqueada')).toHaveLength(0);
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

test('una ranura sin nada que ponerle lo dice DESPUES de mirar el inventario', async () => {
  // FI-R7 — el «no tienes» se movio de la ranura al dialogo, y con ello dejo de
  // ser una suposicion: la ranura ya no afirma nada sobre un inventario que no
  // ha leido, y el dialogo lo afirma cuando el servicio le ha dicho que no hay
  // mas paginas.
  await abrirEquipo({ consultarEquipo: async () => vacio() });

  const pecho = ranuraPorEtiqueta(raiz, 'Pecho');
  expect(pecho.className).not.toContain('ranura--bloqueada');

  pecho.querySelector('.ranura__caja').click();
  await esperarHasta(() => document.body.textContent.includes('No tienes nada en el inventario'));

  expect(document.querySelector('[data-elegir]')).toBeNull();
  // Y no ofrece «Ver mas» cuando el servicio ya dijo que era la ultima pagina.
  expect(document.querySelector('[data-accion="ver-mas-candidatos"]')).toBeNull();
});

test('equipar: se elige el objeto en el dialogo y la ranura pasa a ocupada', async () => {
  const equipar = jest.fn(async () => ({ ...vacio(), armas: ['arma-1'] }));
  await abrirEquipo({ consultarEquipo: async () => vacio(), equipar });

  await abrirSelector(raiz, 'Arma 1');

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

  await abrirSelector(raiz, 'Casco');

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

  await abrirSelector(raiz, 'Arma 1');
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
    await abrirSelector(raiz, 'Arma 1');
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

/**
 * FI-R7 — un objeto de la pagina 3 se puede equipar.
 *
 * El defecto: el selector recibia `paginaMostrada.elementos`, los dieciseis de
 * la pagina que el jugador tenia delante. Un objeto en cualquier otra pagina no
 * aparecia entre los candidatos y nada decia por que. Con cincuenta piezas, dos
 * tercios del inventario estaban fuera del alcance del panel de equipamiento, y
 * la unica forma de llegar a ellas era adivinar su pagina y navegar hasta alli
 * antes de abrir la ranura.
 */
describe('FI-R7 - el selector alcanza todo el inventario, no solo la pagina visible', () => {
  const heroe = { id: 'heroe-1', productoId: 'p-heroe', tipo: 'HEROE', nombrePropio: 'Ayla' };

  /** Inventario de tres paginas; el arma buena esta en la ultima. */
  function inventarioDeTresPaginas() {
    const relleno = (prefijo, cuantos) =>
      Array.from({ length: cuantos }, (_, i) => ({
        id: `${prefijo}-${i}`,
        productoId: 'p-item',
        tipo: 'ITEM',
        nombrePropio: `Chatarra ${prefijo}${i}`,
      }));

    const paginas = [
      [heroe, ...relleno('a', 15)],
      relleno('b', 16),
      [
        {
          id: 'arma-lejana',
          productoId: 'p-arma',
          tipo: 'ARMA',
          nombrePropio: 'Espada del Alba',
        },
        ...relleno('c', 2),
      ],
    ];

    return (numero) =>
      Promise.resolve({
        elementos: paginas[numero] ?? [],
        numero,
        tamanio: 16,
        totalElementos: 35,
        totalPaginas: paginas.length,
        ultima: numero >= paginas.length - 1,
      });
  }

  async function abrirConTresPaginas(extra = {}) {
    const paginas = inventarioDeTresPaginas();
    const consultar = jest.fn((_, numero) => paginas(numero));
    await montarInventario(raiz, 'jugadora', 0, {
      consultar,
      consultarEquipo: async () => vacio(),
      equipar: async () => ({ ...vacio(), armas: ['arma-lejana'] }),
      ...extra,
    });
    raiz.querySelector('.vitrina__equipo').click();
    await esperarHasta(() => !raiz.querySelector('.inventario-equipo').hidden);
    return { consultar };
  }

  test('un arma que esta en la tercera pagina aparece entre los candidatos', async () => {
    await abrirConTresPaginas();

    await abrirSelector(raiz, 'Arma 1');

    expect(document.querySelector('[data-elegir="arma-lejana"]')).not.toBeNull();
  });

  test('y se puede equipar de verdad', async () => {
    const equipar = jest.fn(async () => ({ ...vacio(), armas: ['arma-lejana'] }));
    await abrirConTresPaginas({ equipar });

    await abrirSelector(raiz, 'Arma 1');
    document.querySelector('[data-elegir="arma-lejana"]').click();

    await esperarHasta(() => equipar.mock.calls.length > 0);
    expect(equipar.mock.calls[0][2]).toBe('arma-lejana');
  });

  test('no se descarga el inventario entero: se para al juntar la tanda', async () => {
    // Treinta y dos ITEM caben de sobra en una tanda de dieciseis, asi que la
    // ranura de items no tiene por que llegar a la tercera pagina.
    const { consultar } = await abrirConTresPaginas();
    const antes = consultar.mock.calls.length;

    await abrirSelector(raiz, 'Ítem 1');

    const paginasPedidas = consultar.mock.calls.slice(antes).map(([, p]) => p);
    expect(paginasPedidas).not.toContain(2);
  });

  test('«Ver mas» aparece cuando queda inventario por mirar', async () => {
    await abrirConTresPaginas();

    await abrirSelector(raiz, 'Ítem 1');

    expect(document.querySelector('[data-accion="ver-mas-candidatos"]')).not.toBeNull();
  });

  test('«Ver mas» trae la tanda siguiente sin repetir la anterior', async () => {
    await abrirConTresPaginas();
    await abrirSelector(raiz, 'Ítem 1');
    const idsAntes = [...document.querySelectorAll('[data-elegir]')].map((b) => b.dataset.elegir);
    // La primera tanda junta las paginas 0 y 1; la 2 queda para «Ver mas».
    expect(idsAntes).not.toContain('c-0');

    document.querySelector('[data-accion="ver-mas-candidatos"]').click();
    await esperarHasta(() => document.querySelector('[data-elegir="c-0"]'));

    const idsDespues = [...document.querySelectorAll('[data-elegir]')].map((b) => b.dataset.elegir);
    // Ninguno repetido: la cola de tandas avanza de pagina, no vuelve a empezar.
    expect(new Set(idsDespues).size).toBe(idsDespues.length);
    expect(idsDespues.slice(0, idsAntes.length)).toEqual(idsAntes);
    // Y ya no queda nada: era la ultima pagina.
    expect(document.querySelector('[data-accion="ver-mas-candidatos"]')).toBeNull();
  });

  test('cuando el inventario se acaba, «Ver mas» desaparece', async () => {
    await abrirConTresPaginas();
    await abrirSelector(raiz, 'Arma 1');

    // La ranura de armas recorre las tres paginas de una vez: solo hay un arma,
    // y la busqueda no puede juntar dieciseis, asi que llega hasta el final.
    expect(document.querySelector('[data-accion="ver-mas-candidatos"]')).toBeNull();
  });

  test('si el inventario no responde, se dice y se puede reintentar', async () => {
    let fallar = true;
    const consultar = jest.fn((_, numero) => {
      if (fallar && numero > 0) {
        return Promise.reject(new Error('el inventario no responde'));
      }
      return inventarioDeTresPaginas()(numero);
    });
    await montarInventario(raiz, 'jugadora', 0, {
      consultar,
      consultarEquipo: async () => vacio(),
    });
    raiz.querySelector('.vitrina__equipo').click();
    await esperarHasta(() => !raiz.querySelector('.inventario-equipo').hidden);

    ranuraPorEtiqueta(raiz, 'Arma 1').querySelector('.ranura__caja').click();
    await esperarHasta(() => document.body.textContent.includes('No se pudo traer el inventario'));

    // Y el boton vuelve a estar disponible: un fallo no cierra la puerta.
    const boton = document.querySelector('[data-accion="ver-mas-candidatos"]');
    expect(boton).not.toBeNull();
    expect(boton.disabled).toBe(false);

    fallar = false;
    boton.click();
    await esperarHasta(() => document.querySelector('[data-elegir="arma-lejana"]'));
    expect(document.querySelector('[data-elegir="arma-lejana"]')).not.toBeNull();
  });
});
