/**
 * SCRUM-319 - Cuadricula de 16 productos a 1360 x 768.
 *
 * Cubre el criterio 1 de HU-INV-001, en sus dos escenarios:
 * la pagina llena y la pagina corta que no rellena huecos.
 * La reorganizacion en resoluciones inferiores y el estado vacio
 * son SCRUM-320; el "sin desplazamiento horizontal" medido en un
 * navegador real es SCRUM-321.
 */
import { construirVitrina, PRODUCTOS_POR_PAGINA } from './vitrina.js';

function elemento(indice) {
  return {
    id: `elemento-${indice}`,
    productoId: `producto-${indice}`,
    tipo: 'ARMA',
    nombrePropio: `Espada ${indice}`,
  };
}

function paginaCon(cantidad, totalElementos = cantidad) {
  const elementos = Array.from({ length: cantidad }, (_, i) => elemento(i));
  const totalPaginas = Math.ceil(totalElementos / PRODUCTOS_POR_PAGINA);
  return {
    elementos,
    numero: 0,
    tamanio: PRODUCTOS_POR_PAGINA,
    totalElementos,
    totalPaginas,
    ultima: totalPaginas <= 1,
  };
}

function tarjetas(vitrina) {
  return vitrina.querySelectorAll('.vitrina__producto');
}

describe('Cuadricula de la vitrina a 1360 x 768', () => {
  test('el tamano de pagina de referencia es 16', () => {
    expect(PRODUCTOS_POR_PAGINA).toBe(16);
  });

  test('con 40 productos, la primera pagina muestra exactamente 16 tarjetas', () => {
    const vitrina = construirVitrina(paginaCon(PRODUCTOS_POR_PAGINA, 40));

    expect(tarjetas(vitrina)).toHaveLength(16);
  });

  test('con 7 productos, muestra 7 tarjetas y no rellena huecos', () => {
    const vitrina = construirVitrina(paginaCon(7));

    expect(tarjetas(vitrina)).toHaveLength(7);
    expect(vitrina.querySelectorAll('.vitrina__hueco')).toHaveLength(0);
  });

  test('conserva el orden en el que llegan los elementos', () => {
    const vitrina = construirVitrina(paginaCon(5));

    const nombres = [...tarjetas(vitrina)].map(
      (t) => t.querySelector('.vitrina__nombre').textContent,
    );
    expect(nombres).toEqual(['Espada 0', 'Espada 1', 'Espada 2', 'Espada 3', 'Espada 4']);
  });

  test('cada tarjeta muestra el nombre propio y el tipo del elemento', () => {
    const vitrina = construirVitrina(paginaCon(1));
    const tarjeta = tarjetas(vitrina)[0];

    expect(tarjeta.querySelector('.vitrina__nombre').textContent).toBe('Espada 0');
    expect(tarjeta.querySelector('.vitrina__tipo').textContent).toBe('Arma');
    expect(tarjeta.dataset.elementoId).toBe('elemento-0');
  });

  test('permite solicitar la edicion del elemento desde su tarjeta', () => {
    const editados = [];
    const pagina = paginaCon(1);
    const vitrina = construirVitrina(pagina, {
      alEditar: (elementoSeleccionado) => editados.push(elementoSeleccionado),
    });

    vitrina.querySelector('.vitrina__editar').click();

    expect(editados).toEqual([pagina.elementos[0]]);
  });

  test('el nombre propio del jugador se escribe como texto y nunca como marcado', () => {
    const pagina = paginaCon(1);
    pagina.elementos[0].nombrePropio = '<img src=x onerror="robar()">';

    const vitrina = construirVitrina(pagina);

    const nombre = vitrina.querySelector('.vitrina__nombre');
    expect(nombre.querySelector('img')).toBeNull();
    expect(nombre.textContent).toBe('<img src=x onerror="robar()">');
  });

  test('un tipo que el catalogo agregue despues se muestra tal cual llega', () => {
    const pagina = paginaCon(1);
    pagina.elementos[0].tipo = 'MONTURA';

    const vitrina = construirVitrina(pagina);

    expect(vitrina.querySelector('.vitrina__tipo').textContent).toBe('MONTURA');
  });

  test('rechaza una pagina que exceda el tamano acordado en vez de recortarla', () => {
    const excedida = paginaCon(17, 40);

    expect(() => construirVitrina(excedida)).toThrow(/17 elementos/);
  });

  test('rechaza una pagina nula', () => {
    expect(() => construirVitrina(null)).toThrow(/pagina/);
  });
});
