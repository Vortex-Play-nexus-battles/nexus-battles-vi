import { construirFiltros, leerFiltros } from './subastas-filtros.js';

describe('HU-SUB-011 - construirFiltros (estructura)', () => {
  test('crea un <form> con la clase y aria-label esperados', () => {
    const formulario = construirFiltros();

    expect(formulario.tagName).toBe('FORM');
    expect(formulario.className).toBe('subastas-filtros');
    expect(formulario.getAttribute('aria-label')).toBe('Filtros de subastas');
  });

  test('incluye los 6 checkboxes de tipoProducto', () => {
    const formulario = construirFiltros();

    const casillas = formulario.querySelectorAll('input[name="tipoProducto"]');
    expect(casillas).toHaveLength(6);
    expect([...casillas].map((c) => c.value)).toEqual([
      'HEROE',
      'HABILIDAD',
      'ARMA',
      'ARMADURA',
      'ITEM',
      'EPICA',
    ]);
  });

  test('cada grupo de radio incluye una opción "Todas" marcada por defecto', () => {
    const formulario = construirFiltros();

    for (const grupo of ['rareza', 'tiempoRestante', 'tipoVenta', 'metodoPago', 'vendedor']) {
      const radioTodas = formulario.querySelector(`input[name="${grupo}"][value=""]`);
      expect(radioTodas).not.toBeNull();
      expect(radioTodas.checked).toBe(true);
    }
  });

  test('incluye el botón "Limpiar filtros" de tipo reset', () => {
    const formulario = construirFiltros();

    const boton = formulario.querySelector('.subastas-filtros__limpiar');
    expect(boton).not.toBeNull();
    expect(boton.type).toBe('reset');
  });

  test('el submit no navega (preventDefault)', () => {
    const formulario = construirFiltros();
    document.body.appendChild(formulario);

    const evento = new Event('submit', { cancelable: true });
    formulario.dispatchEvent(evento);

    expect(evento.defaultPrevented).toBe(true);
  });

  test('llama a alCambiar cuando cambia un campo', () => {
    const cambios = [];
    const formulario = construirFiltros({ alCambiar: (filtros) => cambios.push(filtros) });
    document.body.appendChild(formulario);

    const radioRara = formulario.querySelector('input[name="rareza"][value="Rara"]');
    radioRara.checked = true;
    radioRara.dispatchEvent(new Event('change', { bubbles: true }));

    expect(cambios).toHaveLength(1);
    expect(cambios[0]).toEqual({ rareza: 'Rara' });
  });

  test('llama a alCambiar con el estado limpio tras un reset', async () => {
    const cambios = [];
    const formulario = construirFiltros({ alCambiar: (filtros) => cambios.push(filtros) });
    document.body.appendChild(formulario);

    const radioRara = formulario.querySelector('input[name="rareza"][value="Rara"]');
    radioRara.checked = true;

    formulario.querySelector('.subastas-filtros__limpiar').click();

    // notificar() ahora se difiere con setTimeout(0) tras el reset real
    // (ver hallazgo en subastas-filtros.js) -- hay que esperar esa misma
    // cola de tareas antes de verificar.
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(cambios.length).toBeGreaterThan(0);
    expect(cambios.at(-1)).toEqual({});
  });

  test('no falla si no se pasa alCambiar', () => {
    const formulario = construirFiltros();
    document.body.appendChild(formulario);

    const radioRara = formulario.querySelector('input[name="rareza"][value="Rara"]');
    expect(() => {
      radioRara.checked = true;
      radioRara.dispatchEvent(new Event('change', { bubbles: true }));
    }).not.toThrow();
  });
});

describe('HU-SUB-011 - leerFiltros', () => {
  test('un formulario recién construido (sin cambios) no incluye ningún filtro', () => {
    const formulario = construirFiltros();

    expect(leerFiltros(formulario)).toEqual({});
  });

  test('lee varios checkboxes de tipoProducto marcados como un arreglo', () => {
    const formulario = construirFiltros();
    formulario.querySelector('input[name="tipoProducto"][value="ARMA"]').checked = true;
    formulario.querySelector('input[name="tipoProducto"][value="EPICA"]').checked = true;

    expect(leerFiltros(formulario)).toEqual({ tipoProducto: ['ARMA', 'EPICA'] });
  });

  test('lee un valor de radio seleccionado (rareza)', () => {
    const formulario = construirFiltros();
    formulario.querySelector('input[name="rareza"][value="Legendaria"]').checked = true;

    expect(leerFiltros(formulario)).toEqual({ rareza: 'Legendaria' });
  });

  test('el valor "Todas" (radio vacío) no aparece en el resultado', () => {
    const formulario = construirFiltros();

    expect(leerFiltros(formulario)).not.toHaveProperty('rareza');
  });

  test('convierte precioMin y precioMax a número', () => {
    const formulario = construirFiltros();
    formulario.querySelector('input[name="precioMin"]').value = '50';
    formulario.querySelector('input[name="precioMax"]').value = '500';

    const filtros = leerFiltros(formulario);

    expect(filtros.precioMin).toBe(50);
    expect(filtros.precioMax).toBe(500);
    expect(typeof filtros.precioMin).toBe('number');
  });

  test('precioMin/precioMax vacíos no aparecen en el resultado', () => {
    const formulario = construirFiltros();

    const filtros = leerFiltros(formulario);

    expect(filtros).not.toHaveProperty('precioMin');
    expect(filtros).not.toHaveProperty('precioMax');
  });

  test('combina varios filtros a la vez', () => {
    const formulario = construirFiltros();
    formulario.querySelector('input[name="tipoProducto"][value="ARMA"]').checked = true;
    formulario.querySelector('input[name="rareza"][value="Rara"]').checked = true;
    formulario.querySelector('input[name="vendedor"][value="MAESTRO_DE_JUEGO"]').checked = true;
    formulario.querySelector('input[name="precioMin"]').value = '10';

    expect(leerFiltros(formulario)).toEqual({
      tipoProducto: ['ARMA'],
      rareza: 'Rara',
      vendedor: 'MAESTRO_DE_JUEGO',
      precioMin: 10,
    });
  });
});
