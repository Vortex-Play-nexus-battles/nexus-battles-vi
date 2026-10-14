/**
 * HU-PRD-009 — la biblioteca de recursos gráficos y el diálogo para elegir,
 * contra productos.yaml 1.10.0. Los recursos son datos de prueba.
 */

import { jest } from '@jest/globals';

import {
  CLASES_DE_RECURSO,
  abrirBibliotecaParaElegir,
  montarBibliotecaDeRecursos,
  pesoLegible,
  problemaAntesDeSubir,
  textoDeRecurso,
} from './recursos-graficos.js';

const ESPADA = {
  id: 'r-1',
  clase: 'IMAGEN',
  formato: 'PNG',
  tipoContenido: 'image/png',
  bytes: 46080,
  ancho: 320,
  alto: 200,
  nombre: 'espada.png',
  url: '/api/v1/productos/recursos/r-1',
  subidoPor: 'admin-uid',
  subidoEn: '2026-10-14T15:00:00Z',
  usadoEn: [{ productoId: 'p-1', nombre: 'Espada larga' }],
};
const BAILE = {
  ...ESPADA,
  id: 'r-2',
  clase: 'ANIMACION',
  formato: 'GIF',
  nombre: 'baile.gif',
  url: '/api/v1/productos/recursos/r-2',
  usadoEn: [],
};

const pagina = (recursos, extra = {}) => ({
  content: recursos,
  page: 0,
  size: 16,
  totalElements: recursos.length,
  totalPages: recursos.length ? 1 : 0,
  ...extra,
});

async function esperarHasta(condicion) {
  for (let intento = 0; intento < 30; intento += 1) {
    if (condicion()) {
      return;
    }
    await new Promise((resolver) => setTimeout(resolver, 0));
  }
  throw new Error('La biblioteca no terminó la operación esperada');
}

let raiz;
beforeEach(() => {
  raiz = document.createElement('section');
  document.body.replaceChildren(raiz);
});

describe('textos', () => {
  test('pesos, recursos y lo que se dice antes de subir', () => {
    expect(pesoLegible(900)).toBe('900 bytes');
    expect(pesoLegible(1)).toBe('1 byte');
    expect(pesoLegible(46080)).toBe('45 KB');
    expect(pesoLegible(1536 * 1024)).toBe('1,5 MB');
    expect(pesoLegible(-1)).toBe('—');
    expect(textoDeRecurso(ESPADA)).toBe('PNG · 320 × 200 px · 45 KB');
    expect(textoDeRecurso({ formato: 'WEBP' })).toBe('WebP');
    expect(problemaAntesDeSubir(null, 'IMAGEN')).toBe('Elige un archivo.');
    expect(problemaAntesDeSubir({ size: 10 }, 'VIDEO')).toBe(
      'Elige si es una imagen, un icono o una animación.',
    );
    expect(problemaAntesDeSubir({ size: 5 * 1024 * 1024 }, 'ANIMACION')).toBe(
      'El archivo pesa 5 MB y una animación puede pesar como máximo 4 MB.',
    );
    expect(problemaAntesDeSubir({ size: 1024 }, 'IMAGEN')).toBeNull();
    expect(CLASES_DE_RECURSO.map((c) => c.valor)).toEqual(['IMAGEN', 'ICONO', 'ANIMACION']);
  });
});

describe('la biblioteca del panel', () => {
  test('lista de dieciséis en dieciséis, con dónde se usa cada recurso', async () => {
    const listar = jest.fn(async () => pagina([ESPADA, BAILE]));
    montarBibliotecaDeRecursos(raiz, { listar });
    await esperarHasta(() => raiz.querySelectorAll('.recurso-tarjeta').length === 2);

    expect(listar).toHaveBeenCalledWith(
      { clase: null, incluirRetirados: false, pagina: 0, tamano: 16 },
      {},
    );
    const espada = raiz.querySelector('[data-recurso="r-1"]');
    expect(espada.textContent).toContain('espada.png');
    expect(espada.textContent).toContain('PNG · 320 × 200 px · 45 KB');
    expect(espada.textContent).toContain('Imagen de Espada larga');
    expect(espada.querySelector('img').getAttribute('src')).toBe(ESPADA.url);
    // La imagen de un producto no se puede retirar.
    expect(espada.querySelector('[data-accion="retirar-recurso"]').disabled).toBe(true);
    const baile = raiz.querySelector('[data-recurso="r-2"]');
    expect(baile.textContent).toContain('Animación');
    expect(baile.textContent).toContain('Sin usar en ningún producto');
    expect(baile.querySelector('[data-accion="retirar-recurso"]').disabled).toBe(false);
  });

  test('filtrar por clase e incluir los retirados vuelve a pedir desde la primera página', async () => {
    const listar = jest.fn(async () => pagina([BAILE]));
    montarBibliotecaDeRecursos(raiz, { listar });
    await esperarHasta(() => listar.mock.calls.length === 1);

    const filtro = raiz.querySelector('select[name="filtro-biblioteca"]');
    filtro.value = 'ANIMACION';
    filtro.dispatchEvent(new Event('change'));
    await esperarHasta(() => listar.mock.calls.length === 2);
    expect(listar.mock.calls[1][0]).toEqual({
      clase: 'ANIMACION',
      incluirRetirados: false,
      pagina: 0,
      tamano: 16,
    });

    const retirados = raiz.querySelector('input[name="ver-retirados"]');
    retirados.checked = true;
    retirados.dispatchEvent(new Event('change'));
    await esperarHasta(() => listar.mock.calls.length === 3);
    expect(listar.mock.calls[2][0]).toMatchObject({ incluirRetirados: true });
  });

  test('vacía lo dice; un fallo deja reintentar', async () => {
    const listar = jest
      .fn()
      .mockResolvedValueOnce(pagina([]))
      .mockRejectedValueOnce(new Error('No responde'))
      .mockResolvedValueOnce(pagina([BAILE]));
    const { recargar } = montarBibliotecaDeRecursos(raiz, { listar });
    await esperarHasta(() => raiz.querySelector('.estado-vista--vacio'));
    expect(raiz.textContent).toContain('La biblioteca está vacía');

    await recargar();
    const error = raiz.querySelector('.estado-vista--error');
    expect(error.textContent).toContain('No se pudo cargar la biblioteca');
    error.querySelector('[data-accion="reintentar"]').click();
    await esperarHasta(() => raiz.querySelectorAll('.recurso-tarjeta').length === 1);
  });

  test('subir con su clase deja el recurso en la biblioteca; un rechazo dice el requisito', async () => {
    const listar = jest.fn(async () => pagina([]));
    const subir = jest
      .fn()
      .mockResolvedValueOnce(BAILE)
      .mockRejectedValueOnce(
        new Error(
          'Este GIF es una imagen fija: una animación tiene que tener más de un fotograma.',
        ),
      );
    montarBibliotecaDeRecursos(raiz, { listar, subir });
    await esperarHasta(() => listar.mock.calls.length === 1);

    const clase = raiz.querySelector('select[name="clase-subida"]');
    clase.value = 'ANIMACION';
    clase.dispatchEvent(new Event('change'));
    expect(raiz.textContent).toContain('GIF, PNG animado o WebP animado');
    const entrada = raiz.querySelector('input[type="file"]');
    const archivo = new File([new Uint8Array([71, 73, 70])], 'baile.gif', { type: 'image/gif' });
    Object.defineProperty(entrada, 'files', { value: [archivo], configurable: true });
    entrada.dispatchEvent(new Event('change'));
    await esperarHasta(() => raiz.querySelector('.aviso--exito'));
    expect(subir).toHaveBeenCalledWith(archivo, { clase: 'ANIMACION' }, {});
    expect(raiz.querySelector('.aviso--exito').textContent).toContain('Recurso en la biblioteca');

    entrada.dispatchEvent(new Event('change'));
    await esperarHasta(() => raiz.querySelector('.aviso--advertencia'));
    expect(raiz.querySelector('.aviso--advertencia').textContent).toContain(
      'Este GIF es una imagen fija',
    );
  });

  test('retirar pide el motivo y lo manda; cancelar no toca nada', async () => {
    const listar = jest.fn(async () => pagina([BAILE]));
    const retirar = jest.fn(async () => ({ ...BAILE, retiradoEn: '2026-10-14T16:00:00Z' }));
    const pedirMotivo = jest.fn().mockResolvedValueOnce(null).mockResolvedValueOnce('Duplicada');
    montarBibliotecaDeRecursos(raiz, { listar, retirar, pedirMotivo });
    await esperarHasta(() => raiz.querySelector('[data-accion="retirar-recurso"]'));

    raiz.querySelector('[data-accion="retirar-recurso"]').click();
    await esperarHasta(() => pedirMotivo.mock.calls.length === 1);
    expect(retirar).not.toHaveBeenCalled();
    expect(pedirMotivo).toHaveBeenCalledWith(
      expect.objectContaining({
        titulo: '¿Retirar «baile.gif» de la biblioteca?',
        etiquetaMotivo: 'Motivo del retiro',
        textoConfirmar: 'Retirar',
      }),
    );

    raiz.querySelector('[data-accion="retirar-recurso"]').click();
    await esperarHasta(() => retirar.mock.calls.length === 1);
    expect(retirar).toHaveBeenCalledWith('r-2', 'Duplicada', {});
    await esperarHasta(() => raiz.querySelector('.aviso--exito'));
    expect(raiz.querySelector('.aviso--exito').textContent).toContain('Recurso retirado');
  });
});

describe('elegir de la biblioteca', () => {
  test('el diálogo lista, filtra y devuelve el recurso elegido', async () => {
    const listar = jest.fn(async () => pagina([ESPADA]));
    const alElegir = jest.fn();
    abrirBibliotecaParaElegir({ listar, alElegir });
    const dialogo = () => document.querySelector('[role="dialog"]');
    await esperarHasta(() => dialogo()?.querySelector('[data-accion="usar-recurso"]'));

    expect(dialogo().querySelector('h2').textContent).toBe('Elegir de la biblioteca');
    expect(listar).toHaveBeenCalledWith({ clase: null, pagina: 0, tamano: 16 }, {});
    const filtro = dialogo().querySelector('select[name="filtro-clase"]');
    filtro.value = 'ICONO';
    filtro.dispatchEvent(new Event('change'));
    await esperarHasta(() => listar.mock.calls.length === 2);
    expect(listar.mock.calls[1][0]).toEqual({ clase: 'ICONO', pagina: 0, tamano: 16 });
    await esperarHasta(() => dialogo()?.querySelector('[data-accion="usar-recurso"]'));

    dialogo().querySelector('[data-accion="usar-recurso"]').click();
    expect(alElegir).toHaveBeenCalledWith(ESPADA);
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });

  test('una biblioteca vacía lo dice', async () => {
    abrirBibliotecaParaElegir({ listar: async () => pagina([]), alElegir: jest.fn() });
    await esperarHasta(() => document.querySelector('[role="dialog"] .estado-vista--vacio'));
    expect(document.querySelector('[role="dialog"]').textContent).toContain(
      'La biblioteca está vacía',
    );
  });
});
