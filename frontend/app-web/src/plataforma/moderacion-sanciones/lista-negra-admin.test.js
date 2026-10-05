/**
 * Lista negra — HU-ADM-002 con el contrato 2.0.x: presentación pura y la vista
 * montada sobre el marcado REAL de `lista-negra-admin.html` contra un servicio
 * simulado. Usar el HTML de verdad es a propósito: si una zona o un `name`
 * cambian en el marcado y no en el módulo, esta prueba se pone roja.
 */

import { jest } from '@jest/globals';
import { readFileSync } from 'node:fs';

import {
  CATEGORIAS,
  ErrorDeListaNegra,
  TAMANO_DE_PAGINA,
  altaDesde,
  consultaDe,
  descripcionDe,
  etiquetaDeCategoria,
  etiquetaDeModo,
  filaDeTermino,
  montarListaNegra,
} from './lista-negra-admin.js';

const MARCADO = readFileSync(new URL('./lista-negra-admin.html', import.meta.url), 'utf8');
const MAIN = MARCADO.match(/<main[\s\S]*<\/main>/)[0];

const termino = (extra = {}) => ({
  id: 1,
  termino: 'spiderman',
  normalizado: 'spiderman',
  categoria: 'MARCA',
  modo: 'SUBCADENA',
  activo: true,
  creadoPor: 'semilla',
  creadoEn: '2026-09-25T10:00:00Z',
  actualizadoEn: null,
  ...extra,
});

const pagina = (contenido, extra = {}) => ({
  contenido,
  pagina: 0,
  tamano: TAMANO_DE_PAGINA,
  total: contenido.length,
  ...extra,
});

const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

/** Un fetch simulado que responde por método, ruta y consulta; guarda lo que recibe. */
function servicio(rutas) {
  const llamadas = [];
  const fetchImpl = jest.fn(async (url, opciones = {}) => {
    const metodo = opciones.method ?? 'GET';
    const direccion = new URL(url, 'http://x');
    const clave = `${metodo} ${decodeURIComponent(direccion.pathname)}${direccion.search}`;
    llamadas.push({ clave, cuerpo: opciones.body ? JSON.parse(opciones.body) : null });
    const respuesta = rutas[clave];
    if (!respuesta) {
      throw new Error(`sin ruta simulada para ${clave}`);
    }
    const { estado = 200, cuerpo = null } =
      typeof respuesta === 'function' ? respuesta(llamadas.length) : respuesta;
    return {
      ok: estado >= 200 && estado < 300,
      status: estado,
      json: async () => cuerpo,
    };
  });
  fetchImpl.llamadas = llamadas;
  return fetchImpl;
}

const LISTAR = 'GET /api/v1/lista-negra/terminos?pagina=0&tamano=16';

describe('presentación', () => {
  test('etiquetas de categoría y modo del contrato, y lo desconocido tal cual', () => {
    expect(Object.keys(CATEGORIAS)).toEqual([
      'OFENSIVO',
      'MARCA',
      'CELEBRIDAD',
      'POLITICO',
      'DIRIGENTE',
      'OTRO',
    ]);
    expect(etiquetaDeCategoria('POLITICO')).toBe('Político');
    expect(etiquetaDeCategoria('NUEVA')).toBe('NUEVA');
    expect(etiquetaDeModo('PALABRA')).toBe('Palabra entera');
    expect(etiquetaDeModo('SUBCADENA')).toBe('Dentro de otras palabras');
  });

  test('consultaDe: página de 16 y solo los filtros con valor', () => {
    expect(consultaDe()).toBe('?pagina=0&tamano=16');
    expect(consultaDe({ categoria: 'MARCA', activo: 'false', buscar: '  spi ' }, 2)).toBe(
      '?pagina=2&tamano=16&categoria=MARCA&activo=false&buscar=spi',
    );
    expect(consultaDe({ activo: 'quizas', buscar: '   ' })).toBe('?pagina=0&tamano=16');
  });

  test('altaDesde: el modo solo viaja si se eligió uno', () => {
    expect(altaDesde({ termino: ' Spider-Man ', categoria: 'MARCA', modo: '' })).toEqual({
      termino: 'Spider-Man',
      categoria: 'MARCA',
    });
    expect(altaDesde({ termino: 'culo', categoria: 'OFENSIVO', modo: 'PALABRA' })).toEqual({
      termino: 'culo',
      categoria: 'OFENSIVO',
      modo: 'PALABRA',
    });
  });

  test('descripcionDe: forma normalizada, autor y fecha; sin autor lo dice', () => {
    expect(descripcionDe(termino())).toMatch(/^Se compara como «spiderman» · alta de semilla · /);
    expect(descripcionDe(termino({ creadoPor: null, creadoEn: null }))).toBe(
      'Se compara como «spiderman» · autor desconocido',
    );
  });

  test('la fila enseña categoría, modo, estado y acciones con nombre accesible', () => {
    const fila = filaDeTermino(termino({ activo: false }));
    expect(fila.dataset.activo).toBe('false');
    expect(fila.textContent).toContain('Marca registrada');
    expect(fila.textContent).toContain('Dentro de otras palabras');
    expect(fila.textContent).toContain('Inactivo');
    expect(fila.querySelector('[data-accion="alternar"]').textContent).toBe('Activar');
    expect(fila.querySelector('[data-accion="eliminar"]').getAttribute('aria-label')).toBe(
      'Eliminar el término spiderman',
    );
  });

  test('ErrorDeListaNegra toma el problem details', () => {
    const error = new ErrorDeListaNegra({ title: 'Termino duplicado', detail: 'Ya existe' }, 409);
    expect(error.estado).toBe(409);
    expect(error.titulo).toBe('Termino duplicado');
    expect(error.detalle).toBe('Ya existe');
    expect(new ErrorDeListaNegra(null, 500).titulo).toBe('No se pudo completar');
  });
});

describe('vista', () => {
  beforeEach(() => {
    document.body.innerHTML = MAIN;
  });

  test('carga la primera página y pinta cada término con sus datos', async () => {
    const fetchImpl = servicio({
      [LISTAR]: {
        cuerpo: pagina([
          termino(),
          termino({
            id: 2,
            termino: 'puta',
            normalizado: 'puta',
            categoria: 'OFENSIVO',
            modo: 'PALABRA',
          }),
        ]),
      },
    });

    montarListaNegra(document, { fetchImpl });
    await asentar();

    const filas = document.querySelectorAll('.lista-terminos__fila');
    expect(filas).toHaveLength(2);
    expect(filas[1].textContent).toContain('Ofensivo');
    expect(filas[1].textContent).toContain('Palabra entera');
    expect(document.querySelector('[data-zona="paginacion"] nav').hidden).toBe(true);
  });

  test('lista vacía: estado vacío que explica qué pasa', async () => {
    montarListaNegra(document, { fetchImpl: servicio({ [LISTAR]: { cuerpo: pagina([]) } }) });
    await asentar();

    expect(document.querySelector('[data-estado="vacio"]').textContent).toMatch(/está vacía/);
  });

  test('si el servicio falla: estado de error con reintento que vuelve a pedir', async () => {
    const fetchImpl = servicio({
      [LISTAR]: (n) => (n === 1 ? { estado: 503, cuerpo: null } : { cuerpo: pagina([termino()]) }),
    });

    montarListaNegra(document, { fetchImpl });
    await asentar();
    expect(document.querySelector('[data-estado="error"]')).not.toBeNull();

    document.querySelector('[data-accion="reintentar"]').click();
    await asentar();
    expect(document.querySelectorAll('.lista-terminos__fila')).toHaveLength(1);
  });

  test('alta con categoría y modo: POST con el cuerpo del contrato, recarga y aviso de éxito', async () => {
    const fetchImpl = servicio({
      [LISTAR]: { cuerpo: pagina([]) },
      'POST /api/v1/lista-negra/terminos': {
        estado: 201,
        cuerpo: termino({ termino: 'batman', categoria: 'MARCA' }),
      },
    });
    montarListaNegra(document, { fetchImpl });
    await asentar();

    const form = document.querySelector('[data-zona="alta"]');
    form.querySelector('[name="termino"]').value = 'batman';
    form.querySelector('[name="categoria"]').value = 'MARCA';
    form.querySelector('[name="modo"]').value = 'SUBCADENA';
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    await asentar();
    await asentar();

    const alta = fetchImpl.llamadas.find((l) => l.clave.startsWith('POST'));
    expect(alta.cuerpo).toEqual({ termino: 'batman', categoria: 'MARCA', modo: 'SUBCADENA' });
    expect(form.querySelector('[name="termino"]').value).toBe('');
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/batman/);
  });

  test('un duplicado (409) se cuenta con el detalle del servicio, como advertencia', async () => {
    const fetchImpl = servicio({
      [LISTAR]: { cuerpo: pagina([termino()]) },
      'POST /api/v1/lista-negra/terminos': {
        estado: 409,
        cuerpo: { title: 'Termino duplicado', detail: 'Ya existe un término que se compara igual' },
      },
    });
    montarListaNegra(document, { fetchImpl });
    await asentar();

    const form = document.querySelector('[data-zona="alta"]');
    form.querySelector('[name="termino"]').value = 'Spider-Man';
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    await asentar();

    const aviso = document.querySelector('.aviso--advertencia');
    expect(aviso.textContent).toMatch(/se compara igual/);
    expect(aviso.getAttribute('role')).toBe('alert');
  });

  test('sin término no se llama al servicio', async () => {
    const fetchImpl = servicio({ [LISTAR]: { cuerpo: pagina([]) } });
    montarListaNegra(document, { fetchImpl });
    await asentar();

    const form = document.querySelector('[data-zona="alta"]');
    form.querySelector('[name="termino"]').value = '   ';
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    await asentar();

    expect(fetchImpl.llamadas.filter((l) => l.clave.startsWith('POST'))).toHaveLength(0);
    expect(document.querySelector('.aviso--advertencia')).not.toBeNull();
  });

  test('desactivar manda PUT con activo=false al término de la fila y recarga', async () => {
    const fetchImpl = servicio({
      [LISTAR]: { cuerpo: pagina([termino({ termino: 'coca-cola', normalizado: 'cocacola' })]) },
      'PUT /api/v1/lista-negra/terminos/coca-cola': { cuerpo: termino({ activo: false }) },
    });
    montarListaNegra(document, { fetchImpl });
    await asentar();

    document.querySelector('[data-accion="alternar"]').click();
    await asentar();
    await asentar();

    const cambio = fetchImpl.llamadas.find((l) => l.clave.startsWith('PUT'));
    expect(cambio.cuerpo).toEqual({ termino: 'coca-cola', activo: false });
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/desactivado/);
  });

  test('editar pregunta el término nuevo; cancelar no llama a nadie', async () => {
    const preguntar = jest.fn().mockReturnValueOnce(null).mockReturnValueOnce('Spider Man');
    const fetchImpl = servicio({
      [LISTAR]: { cuerpo: pagina([termino()]) },
      'PUT /api/v1/lista-negra/terminos/spiderman': { cuerpo: termino({ termino: 'Spider Man' }) },
    });
    montarListaNegra(document, { fetchImpl, preguntar });
    await asentar();

    document.querySelector('[data-accion="editar"]').click();
    await asentar();
    expect(fetchImpl.llamadas.filter((l) => l.clave.startsWith('PUT'))).toHaveLength(0);

    document.querySelector('[data-accion="editar"]').click();
    await asentar();
    await asentar();
    expect(fetchImpl.llamadas.find((l) => l.clave.startsWith('PUT')).cuerpo).toEqual({
      termino: 'Spider Man',
    });
  });

  test('eliminar pide confirmación: sin ella no borra; con ella DELETE y aviso', async () => {
    const confirmar = jest.fn().mockReturnValueOnce(false).mockReturnValueOnce(true);
    const fetchImpl = servicio({
      [LISTAR]: { cuerpo: pagina([termino()]) },
      'DELETE /api/v1/lista-negra/terminos/spiderman': { estado: 204 },
    });
    montarListaNegra(document, { fetchImpl, confirmar });
    await asentar();

    document.querySelector('[data-accion="eliminar"]').click();
    await asentar();
    expect(fetchImpl.llamadas.filter((l) => l.clave.startsWith('DELETE'))).toHaveLength(0);

    document.querySelector('[data-accion="eliminar"]').click();
    await asentar();
    await asentar();
    expect(fetchImpl.llamadas.filter((l) => l.clave.startsWith('DELETE'))).toHaveLength(1);
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/eliminado/);
  });

  test('un fallo de red al eliminar se cuenta como error, sin romper la vista', async () => {
    const fetchImpl = servicio({ [LISTAR]: { cuerpo: pagina([termino()]) } });
    montarListaNegra(document, { fetchImpl, confirmar: () => true });
    await asentar();

    document.querySelector('[data-accion="eliminar"]').click();
    await asentar();

    expect(document.querySelector('.aviso--error').textContent).toMatch(/No se pudo eliminar/);
  });

  test('los filtros vuelven a la página 0 con la categoría, el estado y la búsqueda', async () => {
    const fetchImpl = servicio({
      [LISTAR]: { cuerpo: pagina([termino()]) },
      'GET /api/v1/lista-negra/terminos?pagina=0&tamano=16&categoria=MARCA&activo=false&buscar=spi':
        {
          cuerpo: pagina([]),
        },
    });
    montarListaNegra(document, { fetchImpl });
    await asentar();

    const filtros = document.querySelector('[data-zona="filtros"]');
    filtros.querySelector('[name="categoria"]').value = 'MARCA';
    filtros.querySelector('[name="activo"]').value = 'false';
    filtros.querySelector('[name="buscar"]').value = 'spi';
    filtros.dispatchEvent(new Event('submit', { cancelable: true }));
    await asentar();

    expect(document.querySelector('[data-estado="vacio"]').textContent).toMatch(/filtro/);
  });

  test('con más de 16 términos se pagina y la casilla pide la página siguiente', async () => {
    const fetchImpl = servicio({
      [LISTAR]: { cuerpo: pagina([termino()], { total: 40 }) },
      'GET /api/v1/lista-negra/terminos?pagina=1&tamano=16': {
        cuerpo: pagina([termino({ id: 9, termino: 'hitler', normalizado: 'hitler' })], {
          pagina: 1,
          total: 40,
        }),
      },
    });
    montarListaNegra(document, { fetchImpl });
    await asentar();

    const nav = document.querySelector('[data-zona="paginacion"] nav');
    expect(nav.hidden).toBe(false);
    expect(nav.getAttribute('aria-label')).toBe('Paginación de la lista negra');
    nav.querySelectorAll('.paginacion__pagina')[1].click();
    await asentar();

    expect(document.querySelector('.lista-terminos__fila').dataset.termino).toBe('hitler');
  });
});
