import { jest } from '@jest/globals';
import { borradorDelFormulario, montarFormularioProductos } from './productos.js';

const HEROE_ID = '0a1b2c3d-4e5f-3a1b-9c2d-0123456789ab';
const listarHeroes = jest.fn(async () => ({
  content: [
    { id: HEROE_ID, nombre: 'Mago Fuego' },
    { id: '1a1b2c3d-4e5f-3a1b-9c2d-0123456789ab', nombre: 'Chamán' },
  ],
}));

function montar(opciones = {}) {
  return montarFormularioProductos(raiz, { listarHeroes, ...opciones });
}

function completarArma(raiz) {
  const formulario = raiz.querySelector('form');
  formulario.elements.namedItem('nombre').value = 'Espada solar';
  formulario.elements.namedItem('imagen').value = '/api/v1/productos/recursos/r-1';
  formulario.elements.namedItem('descripcion').value = 'Arma de prueba';
  formulario.elements.namedItem('tiraje').value = '100';
  formulario.elements.namedItem('precioCreditos').value = '500';
  formulario.elements.namedItem('tipo').value = 'ARMA';
  formulario.elements.namedItem('tipo').dispatchEvent(new Event('change'));
  formulario.elements.namedItem('poderDeAtaque').value = '25';
  formulario.querySelector('[name="tasaDeCaida"]:not([disabled])').value = '10';
  return formulario;
}

async function esperarHasta(condicion) {
  for (let intento = 0; intento < 30; intento += 1) {
    if (condicion()) {
      return;
    }
    await new Promise((resolver) => setTimeout(resolver, 0));
  }
  throw new Error('La interfaz no terminó la operación esperada');
}

let raiz;
beforeEach(() => {
  raiz = document.createElement('main');
  document.body.replaceChildren(raiz);
});

test('muestra únicamente los atributos del tipo seleccionado', () => {
  montar();
  const formulario = raiz.querySelector('form');
  formulario.elements.namedItem('tipo').value = 'ITEM';
  formulario.elements.namedItem('tipo').dispatchEvent(new Event('change'));

  expect(raiz.querySelector('[data-tipo="ITEM"]').hidden).toBe(false);
  expect(raiz.querySelector('[data-tipo="ARMA"]').hidden).toBe(true);
  expect(formulario.elements.namedItem('efecto').disabled).toBe(false);
  expect(formulario.elements.namedItem('poderDeAtaque').disabled).toBe(true);
});

test('cambia entre precio en créditos y moneda real', () => {
  montar();
  const premium = raiz.querySelector('[name="premium"]');
  premium.checked = true;
  premium.dispatchEvent(new Event('change'));

  expect(raiz.querySelector('[data-precio="creditos"]').hidden).toBe(true);
  expect(raiz.querySelector('[name="precioCreditos"]').disabled).toBe(true);
  expect(raiz.querySelector('[data-precio="real"]').hidden).toBe(false);
});

test('crea el producto y muestra confirmación', async () => {
  const creado = {
    nombre: 'Espada solar',
    estado: 'ACTIVO',
    id: '550e8400-e29b-41d4-a716-446655440000',
  };
  const crear = jest.fn(async () => creado);
  montar({ crear });
  const formulario = completarArma(raiz);
  formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await esperarHasta(() => raiz.querySelector('[data-estado]').dataset.estado === 'exito');
  expect(crear).toHaveBeenCalledWith(
    expect.objectContaining({ tipo: 'ARMA', imagen: '/api/v1/productos/recursos/r-1' }),
  );
  expect(raiz.querySelector('[data-estado]').textContent).toBe(
    '«Espada solar» ya está en el catálogo.',
  );
});

test('informa que falta sesión sin mostrar códigos técnicos', async () => {
  const consola = jest.spyOn(console, 'error').mockImplementation(() => {});
  montar({
    crear: async () => {
      const fallo = new Error('401 interno');
      fallo.status = 401;
      throw fallo;
    },
  });
  const formulario = completarArma(raiz);
  formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await esperarHasta(() => raiz.querySelector('[data-estado]').dataset.estado === 'error');
  expect(raiz.querySelector('[data-estado]').textContent).toMatch(/sesión/i);
  expect(raiz.querySelector('[data-estado]').textContent).not.toMatch(/401/);
  consola.mockRestore();
});

describe('SA-6 — imagen, héroe y vista previa', () => {
  test('no hay campos de ruta ni de UUID: la imagen se sube o se elige y el héroe se elige por nombre', async () => {
    montar();
    const texto = raiz.textContent;
    expect(texto).not.toMatch(/Referencia de imagen|Ruta o referencia|UUID/);
    expect(raiz.querySelector('[data-accion="subir-recurso"]').textContent).toBe('Subir archivo');
    expect(raiz.querySelector('[data-accion="elegir-recurso"]').textContent).toBe(
      'Elegir de la biblioteca',
    );
    expect(raiz.querySelector('input[type="file"]').getAttribute('accept')).toBe(
      'image/png,image/jpeg,image/webp,image/gif',
    );
    await esperarHasta(() => raiz.querySelectorAll('select[name="heroe"] option').length > 2);
    const opciones = [...raiz.querySelector('select[name="heroe"]').options].map((o) => o.text);
    expect(opciones).toEqual(['Elige el héroe', 'Chamán', 'Mago Fuego']);
    expect(listarHeroes).toHaveBeenCalledWith(
      expect.objectContaining({ tipo: 'HEROE', tamano: 50 }),
      {},
    );
  });

  test('sin imagen no se crea: el campo lo dice', async () => {
    const crear = jest.fn();
    montar({ crear });
    const formulario = completarArma(raiz);
    formulario.elements.namedItem('imagen').value = '';
    formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    expect(crear).not.toHaveBeenCalled();
    const error = raiz.querySelector('.selector-recurso .campo__error');
    expect(error.hidden).toBe(false);
    expect(error.textContent).toBe('Sube la imagen del producto o elige una de la biblioteca.');
  });

  test('subir un archivo deja su url como imagen y la vista previa la enseña', async () => {
    const subir = jest.fn(async () => ({
      id: 'r-9',
      url: '/api/v1/productos/recursos/r-9',
      nombre: 'espada.png',
      formato: 'PNG',
      ancho: 320,
      alto: 200,
      bytes: 46080,
    }));
    montar({ subir });
    const entrada = raiz.querySelector('input[type="file"]');
    const archivo = new File([new Uint8Array([1, 2, 3])], 'espada.png', { type: 'image/png' });
    Object.defineProperty(entrada, 'files', { value: [archivo], configurable: true });
    entrada.dispatchEvent(new Event('change'));

    await esperarHasta(
      () => subir.mock.calls.length === 1 && raiz.querySelector('[name="imagen"]').value,
    );
    expect(subir).toHaveBeenCalledWith(archivo, { clase: 'IMAGEN', productoId: null }, {});
    expect(raiz.querySelector('[name="imagen"]').value).toBe('/api/v1/productos/recursos/r-9');
    expect(raiz.querySelector('.selector-recurso__descripcion').textContent).toBe(
      'espada.png · PNG · 320 × 200 px · 45 KB',
    );
    const previa = raiz.querySelector('[data-zona="vista-previa-alta"] .product-image img');
    expect(previa.getAttribute('src')).toBe('/api/v1/productos/recursos/r-9');
  });

  test('un rechazo del servicio se enseña con su requisito, sin cambiar la imagen', async () => {
    const subir = jest.fn(async () => {
      const fallo = new Error('Imagen admite PNG, JPEG o WebP; este archivo es GIF.');
      fallo.status = 415;
      throw fallo;
    });
    montar({ subir });
    const entrada = raiz.querySelector('input[type="file"]');
    Object.defineProperty(entrada, 'files', {
      value: [new File([new Uint8Array([1])], 'baile.gif', { type: 'image/gif' })],
      configurable: true,
    });
    entrada.dispatchEvent(new Event('change'));

    await esperarHasta(() => !raiz.querySelector('.selector-recurso .campo__error').hidden);
    expect(raiz.querySelector('.selector-recurso .campo__error').textContent).toBe(
      'Imagen admite PNG, JPEG o WebP; este archivo es GIF.',
    );
    expect(raiz.querySelector('[name="imagen"]').value).toBe('');
  });

  test('un archivo que pesa más de lo admitido no se sube', async () => {
    const subir = jest.fn();
    montar({ subir });
    const clase = raiz.querySelector('select[name="imagen-clase"]');
    clase.value = 'ICONO';
    clase.dispatchEvent(new Event('change'));
    const entrada = raiz.querySelector('input[type="file"]');
    Object.defineProperty(entrada, 'files', {
      value: [new File([new Uint8Array(300 * 1024)], 'icono.png', { type: 'image/png' })],
      configurable: true,
    });
    entrada.dispatchEvent(new Event('change'));

    expect(subir).not.toHaveBeenCalled();
    expect(raiz.querySelector('.selector-recurso .campo__error').textContent).toBe(
      'El archivo pesa 300 KB y un icono puede pesar como máximo 256 KB.',
    );
  });

  test('la habilidad lleva el héroe elegido por su nombre', async () => {
    const crear = jest.fn(async (solicitud) => ({ ...solicitud, estado: 'ACTIVO' }));
    montar({ crear });
    const formulario = raiz.querySelector('form');
    formulario.elements.namedItem('nombre').value = 'Bola de fuego';
    formulario.elements.namedItem('imagen').value = '/api/v1/productos/recursos/r-2';
    formulario.elements.namedItem('descripcion').value = 'Habilidad';
    formulario.elements.namedItem('tiraje').value = '-1';
    formulario.elements.namedItem('precioCreditos').value = '50';
    formulario.elements.namedItem('tipo').value = 'HABILIDAD';
    formulario.elements.namedItem('tipo').dispatchEvent(new Event('change'));
    await esperarHasta(
      () => raiz.querySelectorAll('[data-tipo="HABILIDAD"] select[name="heroe"] option').length > 2,
    );
    const heroe = raiz.querySelector('[data-tipo="HABILIDAD"] select[name="heroe"]');
    heroe.value = HEROE_ID;
    formulario.querySelector('[name="costoPoder"]:not([disabled])').value = '3';
    formulario.querySelector('[name="multiplicadorNivel"]:not([disabled])').value = '1.5';
    formulario.querySelector('[name="turnosCarga"]:not([disabled])').value = '1';
    formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await esperarHasta(() => crear.mock.calls.length === 1);
    expect(crear).toHaveBeenCalledWith(expect.objectContaining({ heroe: HEROE_ID }));
  });

  test('el borrador de la vista previa sigue al formulario', () => {
    montar();
    const formulario = raiz.querySelector('form');
    expect(borradorDelFormulario(formulario)).toMatchObject({
      nombre: 'Nombre del producto',
      imagen: null,
      premium: false,
    });
    formulario.elements.namedItem('nombre').value = 'Escudo';
    formulario.elements.namedItem('precioCreditos').value = '80';
    formulario.dispatchEvent(new Event('input'));
    expect(
      raiz.querySelector('[data-zona="vista-previa-alta"] .product-card__nombre').textContent,
    ).toBe('Escudo');
    expect(borradorDelFormulario(formulario)).toMatchObject({
      nombre: 'Escudo',
      precioCreditos: 80,
    });
  });
});
