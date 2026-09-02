/**
 * HU-INV-007 - Ficha de detalle del producto.
 * Fuente: Proyecto Integrador II, seccion 7.1, p. 34.
 *
 * Los atributos que se pintan salen del contrato de productos: cada tipo
 * trae los suyos, no hay listas genericas de "habilidades" y "efectos".
 */
import { construirFicha, ATRIBUTOS_POR_TIPO } from './ficha-producto.js';

function producto(extra = {}) {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    nombre: 'Hacha de Vorn',
    imagen: '/imagenes/hacha-de-vorn.png',
    descripcion: 'Un hacha forjada en la niebla.',
    tipo: 'ARMA',
    tiraje: -1,
    premium: false,
    estado: 'ACTIVO',
    poderDeAtaque: 42,
    tasaDeCaida: 0.15,
    ...extra,
  };
}

function textoDe(ficha, selector) {
  const e = ficha.querySelector(selector);
  return e === null ? null : e.textContent;
}

function atributos(ficha) {
  return [...ficha.querySelectorAll('.ficha__atributo')].map((a) => [
    a.querySelector('.ficha__etiqueta').textContent,
    a.querySelector('.ficha__valor').textContent,
  ]);
}

describe('Ficha de detalle del producto', () => {
  test('muestra nombre, descripcion e imagen del producto', () => {
    const ficha = construirFicha(producto());

    expect(textoDe(ficha, '.ficha__nombre')).toBe('Hacha de Vorn');
    expect(textoDe(ficha, '.ficha__descripcion')).toBe('Un hacha forjada en la niebla.');
    const imagen = ficha.querySelector('.ficha__imagen');
    expect(imagen.getAttribute('src')).toBe('/imagenes/hacha-de-vorn.png');
  });

  test('la imagen lleva texto alternativo con el nombre del producto', () => {
    const imagen = construirFicha(producto()).querySelector('.ficha__imagen');

    expect(imagen.getAttribute('alt')).toBe('Hacha de Vorn');
  });

  test('un arma muestra su poder de ataque y su tasa de caida', () => {
    const ficha = construirFicha(producto());

    expect(atributos(ficha)).toEqual([
      ['Poder de ataque', '42'],
      ['Tasa de caida', '0.15'],
    ]);
  });

  test('una habilidad muestra los suyos, que son otros', () => {
    const ficha = construirFicha(
      producto({
        tipo: 'HABILIDAD',
        nombre: 'Bola de fuego',
        heroe: '22222222-2222-4222-8222-222222222222',
        costoPoder: 30,
        multiplicadorNivel: 1.5,
        turnosCarga: 2,
      }),
    );

    expect(atributos(ficha).map(([etiqueta]) => etiqueta)).toEqual([
      'Costo de poder',
      'Multiplicador por nivel',
      'Turnos de carga',
    ]);
  });

  test('cada tipo del catalogo tiene sus atributos declarados', () => {
    expect(Object.keys(ATRIBUTOS_POR_TIPO).sort()).toEqual(
      ['ARMA', 'ARMADURA', 'EPICA', 'HABILIDAD', 'ITEM', 'HEROE'].sort(),
    );
  });

  test('un atributo ausente no deja una fila vacia', () => {
    const ficha = construirFicha(producto({ tasaDeCaida: undefined }));

    expect(atributos(ficha)).toEqual([['Poder de ataque', '42']]);
  });

  test('un tipo que el catalogo agregue despues no rompe la ficha', () => {
    const ficha = construirFicha(producto({ tipo: 'MONTURA' }));

    expect(textoDe(ficha, '.ficha__nombre')).toBe('Hacha de Vorn');
    expect(atributos(ficha)).toEqual([]);
  });

  test('el tiraje ilimitado se dice con palabras, no con -1', () => {
    const ficha = construirFicha(producto({ tiraje: -1 }));

    expect(textoDe(ficha, '.ficha__tiraje')).toMatch(/ilimitado/i);
  });

  test('un tiraje limitado muestra su cifra', () => {
    const ficha = construirFicha(producto({ tiraje: 500 }));

    expect(textoDe(ficha, '.ficha__tiraje')).toContain('500');
  });

  test('el texto del catalogo se escribe como texto y nunca como marcado', () => {
    const ficha = construirFicha(
      producto({
        descripcion: '<img src=x onerror="robar()">',
      }),
    );

    const descripcion = ficha.querySelector('.ficha__descripcion');
    expect(descripcion.querySelector('img')).toBeNull();
    expect(descripcion.textContent).toBe('<img src=x onerror="robar()">');
  });

  test('es un dialogo anunciado por su propio nombre', () => {
    const ficha = construirFicha(producto());

    expect(ficha.getAttribute('role')).toBe('dialog');
    expect(ficha.getAttribute('aria-modal')).toBe('true');
    const etiquetadoPor = ficha.getAttribute('aria-labelledby');
    expect(ficha.querySelector(`#${etiquetadoPor}`).textContent).toBe('Hacha de Vorn');
  });

  test('rechaza construirse sin producto', () => {
    expect(() => construirFicha(null)).toThrow(/producto/i);
  });

  // --- Producto suspendido (RN-27, seccion 7.2.1) --------------------------

  test('un producto suspendido se marca como no disponible para adquisicion', () => {
    const ficha = construirFicha(producto({ estado: 'SUSPENDIDO' }));

    const aviso = ficha.querySelector('.ficha__no-disponible');
    expect(aviso).not.toBeNull();
    expect(aviso.textContent).toMatch(/no disponible/i);
  });

  test('el aviso explica que el producto sigue siendo tuyo', () => {
    const ficha = construirFicha(producto({ estado: 'SUSPENDIDO' }));

    // RN-27: los productos suspendidos permanecen en los inventarios de
    // quienes ya los poseen. El jugador no debe pensar que lo perdio.
    expect(ficha.querySelector('.ficha__no-disponible').textContent).toMatch(
      /sigue en tu inventario/i,
    );
  });

  test('un producto activo no lleva ningun aviso', () => {
    const ficha = construirFicha(producto({ estado: 'ACTIVO' }));

    expect(ficha.querySelector('.ficha__no-disponible')).toBeNull();
  });

  test('un producto unico tampoco lo lleva: unico no es suspendido', () => {
    const ficha = construirFicha(producto({ estado: 'UNICO' }));

    expect(ficha.querySelector('.ficha__no-disponible')).toBeNull();
  });

  test('el aviso se anuncia a los lectores de pantalla', () => {
    const ficha = construirFicha(producto({ estado: 'SUSPENDIDO' }));

    expect(ficha.querySelector('.ficha__no-disponible').getAttribute('role')).toBe('status');
  });

  test('un producto sin estado no se marca como suspendido', () => {
    const ficha = construirFicha(producto({ estado: undefined }));

    expect(ficha.querySelector('.ficha__no-disponible')).toBeNull();
  });
});
