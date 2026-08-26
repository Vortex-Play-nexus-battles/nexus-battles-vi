/**
 * SCRUM-320 - Estado vacio y los cuatro estados de la vista.
 *
 * Cubre el criterio 3 de HU-INV-001 y RNF-USA-003, que exige carga, error,
 * exito y vacio en toda vista que consulte datos. La reorganizacion en
 * resoluciones inferiores es CSS y se verifica en navegador, no aqui.
 */
import { jest } from '@jest/globals';
import { montarVitrina } from './pagina-inventario.js';

function elemento(i) {
  return {
    id: `e${i}`, productoId: `p${i}`, tipo: 'ARMA', nombrePropio: `Espada ${i}`,
  };
}

function paginaCon(cantidad) {
  return {
    elementos: Array.from({ length: cantidad }, (_, i) => elemento(i)),
    numero: 0, tamanio: 16, totalElementos: cantidad,
    totalPaginas: cantidad === 0 ? 0 : 1, ultima: true,
  };
}

/** Un codigo de estado del protocolo: tres digitos entre 100 y 599. */
function contieneCodigoDeEstado(texto) {
  return /\b[1-5]\d{2}\b/.test(texto);
}

let contenedor;
beforeEach(() => {
  contenedor = document.createElement('div');
  document.body.replaceChildren(contenedor);
});

describe('Los cuatro estados de la vitrina', () => {
  test('muestra el estado de carga mientras la consulta esta en vuelo', async () => {
    let resolver;
    const enVuelo = new Promise((r) => { resolver = r; });
    const promesa = montarVitrina(contenedor, 'jugador-A', 0, {
      consultar: () => enVuelo,
    });

    expect(contenedor.querySelector('.estado-carga')).not.toBeNull();

    resolver(paginaCon(1));
    await promesa;
    expect(contenedor.querySelector('.estado-carga')).toBeNull();
  });

  test('con productos pinta la cuadricula y ningun estado', async () => {
    await montarVitrina(contenedor, 'jugador-A', 0, {
      consultar: async () => paginaCon(3),
    });

    expect(contenedor.querySelectorAll('.vitrina__producto')).toHaveLength(3);
    expect(contenedor.querySelector('.estado-vacio')).toBeNull();
    expect(contenedor.querySelector('.estado-error')).toBeNull();
  });

  test('un inventario vacio muestra un estado que explica que aun no tiene productos', async () => {
    await montarVitrina(contenedor, 'jugador-A', 0, {
      consultar: async () => paginaCon(0),
    });

    const vacio = contenedor.querySelector('.estado-vacio');
    expect(vacio).not.toBeNull();
    expect(vacio.textContent).toMatch(/todav[ií]a no tienes productos/i);
    expect(contenedor.querySelector('.vitrina__producto')).toBeNull();
  });

  test('el estado vacio no es un error ni ensena un codigo del protocolo', async () => {
    await montarVitrina(contenedor, 'jugador-A', 0, {
      consultar: async () => paginaCon(0),
    });

    const texto = contenedor.textContent;
    expect(contenedor.querySelector('.estado-error')).toBeNull();
    expect(texto).not.toMatch(/error/i);
    expect(contieneCodigoDeEstado(texto)).toBe(false);
  });

  test('si el servicio falla, el jugador ve un aviso sin codigo de estado', async () => {
    const consola = jest.spyOn(console, 'error').mockImplementation(() => {});

    await montarVitrina(contenedor, 'jugador-A', 0, {
      consultar: async () => {
        throw new Error('El servicio de inventario respondio 503 al pedir la pagina 0');
      },
    });

    const aviso = contenedor.querySelector('.estado-error');
    expect(aviso).not.toBeNull();
    expect(contieneCodigoDeEstado(aviso.textContent)).toBe(false);
    expect(aviso.textContent).not.toMatch(/503/);
    consola.mockRestore();
  });

  test('el detalle tecnico del fallo se registra para el equipo, no para el jugador', async () => {
    const consola = jest.spyOn(console, 'error').mockImplementation(() => {});
    const fallo = new Error('respondio 503');

    await montarVitrina(contenedor, 'jugador-A', 0, {
      consultar: async () => { throw fallo; },
    });

    expect(consola).toHaveBeenCalledWith(expect.any(String), fallo);
    consola.mockRestore();
  });

  test('reemplaza el estado anterior en cada montaje, sin apilar', async () => {
    await montarVitrina(contenedor, 'jugador-A', 0, {
      consultar: async () => paginaCon(0),
    });
    await montarVitrina(contenedor, 'jugador-A', 0, {
      consultar: async () => paginaCon(2),
    });

    expect(contenedor.querySelectorAll('.estado-vacio')).toHaveLength(0);
    expect(contenedor.querySelectorAll('.vitrina')).toHaveLength(1);
  });
});
