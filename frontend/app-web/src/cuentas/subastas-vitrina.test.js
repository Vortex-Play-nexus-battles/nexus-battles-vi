import { jest } from '@jest/globals';
import { construirVitrinaSubastas } from './subastas-vitrina.js';

function subastaBase(sobrescribir = {}) {
  return {
    id: 'abc-123',
    nombreProducto: 'Espada del Alba Eterna',
    tipoProducto: 'ARMA',
    rareza: 'Legendaria',
    miniaturaUrl: null,
    precioInicial: 100,
    ofertaVigente: 150,
    precioCompraInmediata: null,
    cantidadPujas: 0,
    fechaFin: new Date(Date.now() + 3_600_000).toISOString(), // +1h
    esMaestroDeJuego: false,
    vendedorId: 'vendedor-1',
    ...sobrescribir,
  };
}

describe('HU-SUB-011 - construirVitrinaSubastas', () => {
  test('lanza si la pagina no trae un arreglo "contenido"', () => {
    expect(() => construirVitrinaSubastas({})).toThrow(TypeError);
    expect(() => construirVitrinaSubastas(null)).toThrow(TypeError);
  });

  test('crea una lista vacia cuando el contenido esta vacio', () => {
    const vitrina = construirVitrinaSubastas({ contenido: [] });

    expect(vitrina.tagName).toBe('UL');
    expect(vitrina.className).toBe('subastas');
    expect(vitrina.children).toHaveLength(0);
  });

  test('crea una tarjeta por cada subasta, con el id en el dataset', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ id: 'a' }), subastaBase({ id: 'b' })],
    });

    expect(vitrina.children).toHaveLength(2);
    expect(vitrina.children[0].dataset.subastaId).toBe('a');
    expect(vitrina.children[1].dataset.subastaId).toBe('b');
  });

  test('muestra el nombre y la etiqueta legible del tipo de producto', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ nombreProducto: 'Grimorio del Vacío', tipoProducto: 'EPICA' })],
    });

    const tarjeta = vitrina.children[0];
    expect(tarjeta.querySelector('.subastas__nombre').textContent).toBe('Grimorio del Vacío');
    expect(tarjeta.querySelector('.subastas__meta').textContent).toBe('Épica');
  });

  test('si el tipo de producto no esta en el catalogo, muestra el valor tal cual', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ tipoProducto: 'TIPO_DESCONOCIDO' })],
    });

    expect(vitrina.children[0].querySelector('.subastas__meta').textContent).toBe(
      'TIPO_DESCONOCIDO',
    );
  });

  test('muestra la insignia de rareza cuando es una de las 4 oficiales', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ rareza: 'Épica' })],
    });

    const insignia = vitrina.children[0].querySelector('.subastas__rareza');
    expect(insignia).not.toBeNull();
    expect(insignia.textContent).toBe('Épica');
    expect(insignia.className).toContain('subastas__rareza--epica');
  });

  test('no muestra insignia de rareza si el valor no es una de las 4 oficiales', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ rareza: 'Mitica' })],
    });

    expect(vitrina.children[0].querySelector('.subastas__rareza')).toBeNull();
  });

  test('no muestra insignia de rareza si la subasta no trae rareza', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ rareza: null })],
    });

    expect(vitrina.children[0].querySelector('.subastas__rareza')).toBeNull();
  });

  test('formatea el precio en créditos con separador de miles (es-CO)', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ ofertaVigente: 1500 })],
    });

    expect(vitrina.children[0].querySelector('.subastas__precio').textContent).toBe(
      '1.500 créditos',
    );
  });

  test('muestra "pujas" en plural cuando cantidadPujas no es 1', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ cantidadPujas: 0 }), subastaBase({ id: 'b', cantidadPujas: 5 })],
    });

    expect(vitrina.children[0].querySelector('.subastas__pujas').textContent).toBe('0 pujas');
    expect(vitrina.children[1].querySelector('.subastas__pujas').textContent).toBe('5 pujas');
  });

  test('muestra "puja" en singular cuando cantidadPujas es exactamente 1', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ cantidadPujas: 1 })],
    });

    expect(vitrina.children[0].querySelector('.subastas__pujas').textContent).toBe('1 puja');
  });

  test('muestra la insignia de Maestro de Juego cuando esMaestroDeJuego es true', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ esMaestroDeJuego: true })],
    });

    const insignia = vitrina.children[0].querySelector('.subastas__insignia-mdj');
    expect(insignia).not.toBeNull();
    expect(insignia.textContent).toBe('Maestro de Juego');
  });

  test('no muestra la insignia de Maestro de Juego cuando es false', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ esMaestroDeJuego: false })],
    });

    expect(vitrina.children[0].querySelector('.subastas__insignia-mdj')).toBeNull();
  });

  test('crea la imagen de la miniatura solo si viene miniaturaUrl', () => {
    const conImagen = construirVitrinaSubastas({
      contenido: [subastaBase({ miniaturaUrl: 'https://ejemplo.test/img.png' })],
    });
    const sinImagen = construirVitrinaSubastas({
      contenido: [subastaBase({ miniaturaUrl: null })],
    });

    expect(conImagen.children[0].querySelector('img')).not.toBeNull();
    expect(sinImagen.children[0].querySelector('img')).toBeNull();
  });

  test('el botón "Ver subasta" solo aparece si se pasa alAbrirDetalle', () => {
    const vitrinaSin = construirVitrinaSubastas({ contenido: [subastaBase()] });
    const vitrinaCon = construirVitrinaSubastas(
      { contenido: [subastaBase()] },
      { alAbrirDetalle: () => {} },
    );

    expect(vitrinaSin.children[0].querySelector('.subastas__ver-detalle')).toBeNull();
    expect(vitrinaCon.children[0].querySelector('.subastas__ver-detalle')).not.toBeNull();
  });

  test('el botón "Ver subasta" invoca alAbrirDetalle con la subasta al hacer clic', () => {
    const subasta = subastaBase({ id: 'xyz' });
    let recibido = null;

    const vitrina = construirVitrinaSubastas(
      { contenido: [subasta] },
      {
        alAbrirDetalle: (s) => {
          recibido = s;
        },
      },
    );

    vitrina.children[0].querySelector('.subastas__ver-detalle').click();

    expect(recibido).toBe(subasta);
  });

  test('el botón "Comprar ahora" solo aparece si hay callback Y precioCompraInmediata', () => {
    const sinPrecio = construirVitrinaSubastas(
      { contenido: [subastaBase({ precioCompraInmediata: null })] },
      { alComprarAhora: () => {} },
    );
    const sinCallback = construirVitrinaSubastas({
      contenido: [subastaBase({ precioCompraInmediata: 300 })],
    });
    const conAmbos = construirVitrinaSubastas(
      { contenido: [subastaBase({ precioCompraInmediata: 300 })] },
      { alComprarAhora: () => {} },
    );

    expect(sinPrecio.children[0].querySelector('.subastas__comprar-ahora')).toBeNull();
    expect(sinCallback.children[0].querySelector('.subastas__comprar-ahora')).toBeNull();
    expect(conAmbos.children[0].querySelector('.subastas__comprar-ahora')).not.toBeNull();
  });

  test('el botón "Comprar ahora" muestra el precio formateado en el texto', () => {
    const vitrina = construirVitrinaSubastas(
      { contenido: [subastaBase({ precioCompraInmediata: 300 })] },
      { alComprarAhora: () => {} },
    );

    expect(vitrina.children[0].querySelector('.subastas__comprar-ahora').textContent).toContain(
      '300',
    );
  });
});

describe('HU-SUB-011 - formatearTiempoRestante (a través de la tarjeta renderizada)', () => {
  // jest.useFakeTimers() congela Date.now(): sin esto, el momento en que
  // se construye fechaFin y el momento en que formatearTiempoRestante
  // calcula la diferencia son dos llamadas reales distintas a Date.now(),
  // separadas por el tiempo real que tarda Jest en ejecutar el codigo --
  // si esa fraccion de segundo cruza un limite de minuto/hora/dia, el
  // resultado cambia y la prueba es intermitente (ya paso: "30m" vs "29m").
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-01-01T12:00:00.000Z'));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  test('muestra minutos cuando faltan menos de 60', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ fechaFin: new Date(Date.now() + 30 * 60_000).toISOString() })],
    });

    expect(vitrina.children[0].querySelector('.subastas__contador').textContent).toBe('30m');
  });

  test('muestra horas cuando faltan 60 minutos o más, pero menos de 24h', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ fechaFin: new Date(Date.now() + 5 * 3_600_000).toISOString() })],
    });

    expect(vitrina.children[0].querySelector('.subastas__contador').textContent).toBe('5h');
  });

  test('muestra días cuando faltan 24h o más', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ fechaFin: new Date(Date.now() + 3 * 86_400_000).toISOString() })],
    });

    expect(vitrina.children[0].querySelector('.subastas__contador').textContent).toBe('3d');
  });

  test('muestra "Finalizada" cuando la fecha de fin ya pasó', () => {
    const vitrina = construirVitrinaSubastas({
      contenido: [subastaBase({ fechaFin: new Date(Date.now() - 60_000).toISOString() })],
    });

    expect(vitrina.children[0].querySelector('.subastas__contador').textContent).toBe('Finalizada');
  });
});
