/**
 * El historial de moderación — §7.3.3 (SA-4, comentarios.yaml 1.14.0).
 */

import { jest } from '@jest/globals';

import {
  cambioDeEstado,
  consultaDelHistorial,
  FILAS_POR_PAGINA,
  HECHO_DE_ACCION,
  montarHistorialDeModeracion,
} from './historial-moderacion.js';
import { historialDeModeracion } from './cliente-moderacion.js';

const asiento = (extra = {}) => ({
  id: 'asi-1',
  fecha: '2026-10-14T15:00:00Z',
  accion: 'OCULTAR',
  moderadorId: 'mod-1',
  apodoModerador: 'AdaLaJusta',
  motivo: 'Lenguaje inadecuado',
  estadoAnterior: 'EN_REVISION',
  estadoNuevo: 'OCULTO',
  textoEditado: false,
  comentarioId: 'com-1',
  productoId: 'espada-del-alba',
  autorId: 'aut-1',
  apodoAutor: 'LyraRoja',
  extracto: 'Un comentario con un insulto',
  ...extra,
});

const pagina = (contenido, total = contenido.length) => ({
  contenido,
  pagina: 0,
  tamano: FILAS_POR_PAGINA,
  total,
  totalPaginas: Math.max(1, Math.ceil(total / FILAS_POR_PAGINA)),
});

const asentar = () => new Promise((resolver) => setTimeout(resolver, 0));

describe('lo que se calcula', () => {
  test('la consulta: los días son instantes de Colombia y «hasta» cuenta el día entero', () => {
    expect(
      consultaDelHistorial(
        { accion: 'OCULTAR', moderador: 'ada', desde: '2026-10-01', hasta: '2026-10-14' },
        2,
      ),
    ).toEqual({
      accion: 'OCULTAR',
      moderador: 'ada',
      desde: '2026-10-01T00:00:00-05:00',
      hasta: '2026-10-15T00:00:00-05:00',
      pagina: 2,
      tamano: 16,
    });
    expect(consultaDelHistorial({}, 0)).toEqual({
      accion: null,
      moderador: null,
      desde: null,
      hasta: null,
      pagina: 0,
      tamano: 16,
    });
  });

  test('el cambio de estado, o que siguió igual', () => {
    expect(cambioDeEstado({ estadoAnterior: 'EN_REVISION', estadoNuevo: 'OCULTO' })).toBe(
      'En revisión → Oculto',
    );
    expect(cambioDeEstado({ estadoAnterior: 'PUBLICADO', estadoNuevo: 'PUBLICADO' })).toBe(
      'Sigue publicado',
    );
  });

  test('cada acción del contrato se dice como hecho', () => {
    expect(Object.keys(HECHO_DE_ACCION)).toEqual([
      'APROBAR',
      'OCULTAR',
      'ELIMINAR',
      'RESTAURAR',
      'EDITAR',
      'MARCAR',
      'DESMARCAR',
    ]);
  });
});

describe('el cliente', () => {
  test('GET /comentarios/moderacion/historial con solo los filtros puestos', async () => {
    const fetchImpl = jest.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => pagina([]),
      text: async () => JSON.stringify(pagina([])),
    }));
    await historialDeModeracion({ accion: 'EDITAR', pagina: 1 }, { fetchImpl });
    const url = new URL(fetchImpl.mock.calls[0][0], 'http://x');
    expect(url.pathname).toBe('/api/v1/comentarios/moderacion/historial');
    expect(Object.fromEntries(url.searchParams)).toEqual({
      accion: 'EDITAR',
      pagina: '1',
      tamano: '16',
    });
  });
});

describe('montado', () => {
  let zona;
  beforeEach(() => {
    document.body.innerHTML = '<section data-zona="historial-moderacion"></section>';
    zona = document.querySelector('[data-zona="historial-moderacion"]');
  });

  test('pinta cada decisión con quién, qué, sobre qué comentario y por qué, sin identificadores', async () => {
    const consultar = jest.fn(async () =>
      pagina(
        [
          asiento(),
          asiento({
            id: 'asi-2',
            accion: 'EDITAR',
            estadoAnterior: 'PUBLICADO',
            estadoNuevo: 'PUBLICADO',
            textoEditado: true,
          }),
        ],
        2,
      ),
    );
    montarHistorialDeModeracion(zona, { consultar });
    await asentar();

    const filas = zona.querySelectorAll('tbody tr');
    expect(filas).toHaveLength(2);
    expect(filas[0].querySelector('th').textContent).toBe('Ocultado');
    expect(filas[0].textContent).toContain('AdaLaJusta');
    expect(filas[0].textContent).toContain('«Un comentario con un insulto»');
    expect(filas[0].textContent).toContain('de LyraRoja');
    expect(filas[0].textContent).toContain('En revisión → Oculto');
    expect(filas[0].textContent).toContain('Lenguaje inadecuado');
    expect(filas[1].textContent).toContain('Texto editado');
    expect(filas[1].textContent).toContain('Sigue publicado');
    expect(zona.textContent).not.toMatch(/com-1|aut-1|mod-1|EN_REVISION|OCULTAR/);
    expect(zona.textContent).toContain('1–2 de 2 decisiones');
  });

  test('«Abrir» lleva el comentario al panel de revisión', async () => {
    const alAbrirComentario = jest.fn();
    montarHistorialDeModeracion(zona, {
      consultar: jest.fn(async () => pagina([asiento()])),
      alAbrirComentario,
    });
    await asentar();
    zona.querySelector('[data-accion="abrir-comentario"]').click();
    expect(alAbrirComentario).toHaveBeenCalledWith('com-1');
  });

  test('un filtro vuelve a pedir desde la primera página', async () => {
    const consultar = jest.fn(async () => pagina([asiento()]));
    montarHistorialDeModeracion(zona, { consultar });
    await asentar();
    const accion = zona.querySelector('select[name="accion"]');
    accion.value = 'ELIMINAR';
    accion.dispatchEvent(new Event('change'));
    await asentar();
    expect(consultar).toHaveBeenLastCalledWith(
      expect.objectContaining({ accion: 'ELIMINAR', pagina: 0 }),
    );
  });

  test('sin decisiones se dice; con filtros, que ninguna coincide', async () => {
    const consultar = jest.fn(async () => pagina([]));
    montarHistorialDeModeracion(zona, { consultar });
    await asentar();
    expect(zona.textContent).toContain('Todavía no hay decisiones de moderación');

    const moderador = zona.querySelector('input[name="moderador"]');
    moderador.value = 'nadie';
    moderador.closest('form').requestSubmit();
    await asentar();
    expect(zona.textContent).toContain('Ninguna decisión coincide con los filtros');
  });

  test('si el servicio falla se dice en su sitio, con reintento', async () => {
    const consultar = jest
      .fn()
      .mockRejectedValueOnce(
        Object.assign(new Error('x'), { detalle: 'El servicio no está disponible ahora.' }),
      )
      .mockResolvedValueOnce(pagina([asiento()]));
    montarHistorialDeModeracion(zona, { consultar });
    await asentar();
    expect(zona.textContent).toContain('No pudimos cargar el historial');
    expect(zona.textContent).toContain('El servicio no está disponible ahora.');
    zona.querySelector('[data-accion="reintentar"], button').click();
    await asentar();
    expect(zona.querySelectorAll('tbody tr')).toHaveLength(1);
  });
});
