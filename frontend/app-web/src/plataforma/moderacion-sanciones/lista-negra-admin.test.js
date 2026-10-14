/**
 * Lista negra — HU-ADM-002 con el contrato 2.1.x y el kit de la consola (SA-4,
 * punto 21): presentación pura y la vista montada sobre el marcado REAL de
 * `lista-negra-admin.html` contra un servicio simulado. Usar el HTML de verdad
 * es a propósito: si una zona cambia en el marcado y no en el módulo, esta
 * prueba se pone roja.
 */

import { jest } from '@jest/globals';
import { readFileSync } from 'node:fs';

import {
  CATEGORIAS,
  ErrorDeListaNegra,
  MOTIVO_MAXIMO,
  MOTIVO_MINIMO,
  dialogoDeAccion,
  TAMANO_DE_PAGINA,
  accionesDeTermino,
  altaDe,
  altaDesde,
  autorDe,
  consultaDe,
  etiquetaDeCategoria,
  etiquetaDeModo,
  formularioDeEdicion,
  montarListaNegra,
  tablaDeTerminos,
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
const ACTIVOS = `${LISTAR}&activo=true`;
const APAGADOS = `${LISTAR}&activo=false`;

/** Las tres cifras de arriba, contestadas con totales fijos. */
const cifras = (todos = 3, activos = 2, apagados = 1) => ({
  [ACTIVOS]: { cuerpo: pagina([], { total: activos }) },
  [APAGADOS]: { cuerpo: pagina([], { total: apagados }) },
  [`${LISTAR}#cifra`]: { cuerpo: pagina([], { total: todos }) },
});

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

  test('el alta se lee como persona: la carga inicial no es un autor; sin autor lo dice', () => {
    expect(autorDe(termino())).toBe('Carga inicial');
    expect(autorDe(termino({ creadoPor: 'ana_mod' }))).toBe('ana_mod');
    expect(autorDe(termino({ creadoPor: null }))).toBe('Sin autor');
    expect(altaDe(termino({ creadoPor: 'ana_mod' }))).toMatch(/^ana_mod · \d{1,2}/);
    expect(altaDe(termino({ creadoPor: null, creadoEn: null }))).toBe('Sin autor');
  });

  test('la tabla enseña categoría, modo, estado y alta, con las acciones a un lado y nombre accesible', () => {
    const tabla = tablaDeTerminos([termino(), termino({ id: 2, termino: 'culo', activo: false })]);
    const filas = tabla.querySelectorAll('tbody tr');
    expect(filas).toHaveLength(2);
    expect(filas[0].dataset.fila).toBe('1');
    expect(filas[0].textContent).toContain('Marca registrada');
    expect(filas[0].textContent).toContain('Dentro de otras palabras');
    expect(filas[0].textContent).toContain('Activo');
    expect(filas[0].textContent).toContain('Carga inicial');
    expect(filas[1].textContent).toContain('Apagado');
    expect(filas[1].querySelector('[data-accion="alternar"]').textContent).toBe('Activar');
    expect(filas[0].querySelector('[data-accion="eliminar"]').getAttribute('aria-label')).toBe(
      'Quitar el término spiderman',
    );
  });

  test('las acciones llaman con el término de su fila', () => {
    const editar = jest.fn();
    const acciones = accionesDeTermino(termino(), { editar });
    acciones.querySelector('[data-accion="editar"]').click();
    expect(editar).toHaveBeenCalledWith(expect.objectContaining({ termino: 'spiderman' }));
    expect(acciones.querySelector('[data-accion="alternar"]').textContent).toBe('Apagar');
  });

  test('el formulario de edición parte de los valores de ahora y los devuelve tal cual', () => {
    const { elemento, leer } = formularioDeEdicion(termino());
    expect(leer()).toEqual({
      termino: 'spiderman',
      categoria: 'MARCA',
      modo: 'SUBCADENA',
      motivo: '',
    });
    elemento.querySelector('[name="termino"]').value = ' Spider Man ';
    elemento.querySelector('[name="modo"]').value = 'PALABRA';
    expect(leer()).toEqual({
      termino: 'Spider Man',
      categoria: 'MARCA',
      modo: 'PALABRA',
      motivo: '',
    });
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

  /**
   * El listado sin filtros y la cifra «Términos en la lista» piden la misma
   * dirección: la primera llamada es la del listado y la segunda, la de la
   * cifra (o al revés); las dos reciben la misma página.
   */
  const rutas = (listado, extra = {}) => ({
    ...cifras(),
    [LISTAR]: listado,
    ...extra,
  });

  test('cabecera corta, cifras de verdad y cada término en la tabla', async () => {
    const fetchImpl = servicio(
      rutas({
        cuerpo: pagina(
          [
            termino(),
            termino({
              id: 2,
              termino: 'puta',
              normalizado: 'puta',
              categoria: 'OFENSIVO',
              modo: 'PALABRA',
            }),
          ],
          { total: 2 },
        ),
      }),
    );

    montarListaNegra(document, { fetchImpl });
    await asentar();
    await asentar();

    const cabecera = document.querySelector('[data-zona="encabezado"]').textContent;
    expect(cabecera).toMatch(/Lista negra/);
    expect(cabecera).toMatch(/Vocabulario vetado en apodos, nombres, comentarios y chat\./);
    expect(cabecera).not.toMatch(/sp1der|mayúsculas|tildes/);
    const cifrasPintadas = document.querySelector('[data-zona="cifras-de-la-lista"]');
    expect(cifrasPintadas.textContent).toMatch(/Términos en la lista\s*2/);
    expect(cifrasPintadas.textContent).toMatch(/Activos\s*2/);
    expect(cifrasPintadas.textContent).toMatch(/Apagados\s*1/);
    const filas = document.querySelectorAll('[data-panel="terminos"] tbody tr');
    expect(filas).toHaveLength(2);
    expect(filas[1].textContent).toContain('Ofensivo');
    expect(filas[1].textContent).toContain('Palabra entera');
    expect(document.querySelector('[data-zona="paginacion"] nav')).toBeNull();
    // Los desplegables son los del kit, no un contenedor flexible con el texto arriba.
    expect(document.querySelector('select.desplegable')).toBeNull();
    expect(document.querySelectorAll('select.desplegable__control').length).toBeGreaterThanOrEqual(
      2,
    );
  });

  test('una cifra que no responde se dice «sin dato», nunca cero', async () => {
    const fetchImpl = servicio({
      [LISTAR]: { cuerpo: pagina([termino()]) },
      [ACTIVOS]: { estado: 503, cuerpo: null },
      [APAGADOS]: { cuerpo: pagina([], { total: 0 }) },
    });
    montarListaNegra(document, { fetchImpl });
    await asentar();
    await asentar();

    const activos = document.querySelector('[data-indicador="Activos"]');
    expect(activos.dataset.sinDato).toBe('si');
  });

  test('lista vacía: estado vacío que explica qué hacer', async () => {
    montarListaNegra(document, { fetchImpl: servicio(rutas({ cuerpo: pagina([]) })) });
    await asentar();
    await asentar();

    expect(document.querySelector('[data-panel="terminos"]').textContent).toMatch(
      /La lista negra está vacía/,
    );
  });

  test('si el servicio falla: estado de error con reintento que vuelve a pedir', async () => {
    let listados = 0;
    const fetchImpl = servicio(
      rutas(() => {
        listados += 1;
        return listados === 1 ? { estado: 503, cuerpo: null } : { cuerpo: pagina([termino()]) };
      }),
    );

    montarListaNegra(document, { fetchImpl });
    await asentar();
    expect(document.querySelector('[data-zona="terminos"] [data-estado="error"]')).not.toBeNull();

    document.querySelector('[data-zona="terminos"] [data-accion="reintentar"]').click();
    await asentar();
    expect(document.querySelectorAll('[data-panel="terminos"] tbody tr')).toHaveLength(1);
  });

  test('alta con categoría y modo: POST con el cuerpo del contrato, recarga y aviso de éxito', async () => {
    const fetchImpl = servicio(
      rutas(
        { cuerpo: pagina([]) },
        {
          'POST /api/v1/lista-negra/terminos': {
            estado: 201,
            cuerpo: termino({ termino: 'batman', categoria: 'MARCA' }),
          },
        },
      ),
    );
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

  test('sin término o sin categoría no se llama al servicio y se dice qué falta', async () => {
    const fetchImpl = servicio(rutas({ cuerpo: pagina([]) }));
    montarListaNegra(document, { fetchImpl });
    await asentar();

    const form = document.querySelector('[data-zona="alta"]');
    form.querySelector('[name="termino"]').value = '   ';
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    await asentar();
    expect(document.querySelector('.aviso--advertencia').textContent).toMatch(/Escribe el término/);

    form.querySelector('[name="termino"]').value = 'batman';
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    await asentar();
    expect(document.querySelector('.aviso--advertencia').textContent).toMatch(/categoría/);

    expect(fetchImpl.llamadas.filter((l) => l.clave.startsWith('POST'))).toHaveLength(0);
  });

  test('un duplicado (409) se cuenta con el detalle del servicio, como advertencia', async () => {
    const fetchImpl = servicio(
      rutas(
        { cuerpo: pagina([termino()]) },
        {
          'POST /api/v1/lista-negra/terminos': {
            estado: 409,
            cuerpo: {
              title: 'Termino duplicado',
              detail: 'Ya existe un término que se compara igual',
            },
          },
        },
      ),
    );
    montarListaNegra(document, { fetchImpl });
    await asentar();

    const form = document.querySelector('[data-zona="alta"]');
    form.querySelector('[name="termino"]').value = 'Spider-Man';
    form.querySelector('[name="categoria"]').value = 'MARCA';
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    await asentar();

    const aviso = document.querySelector('.aviso--advertencia');
    expect(aviso.textContent).toMatch(/se compara igual/);
    expect(aviso.getAttribute('role')).toBe('alert');
  });

  test('apagar manda PUT con activo=false al término de la fila, recarga y lo dice', async () => {
    const fetchImpl = servicio(
      rutas(
        { cuerpo: pagina([termino({ termino: 'coca-cola', normalizado: 'cocacola' })]) },
        { 'PUT /api/v1/lista-negra/terminos/coca-cola': { cuerpo: termino({ activo: false }) } },
      ),
    );
    const confirmar = jest.fn(() => 'Da falsos positivos en el chat');
    montarListaNegra(document, { fetchImpl, confirmar });
    await asentar();

    document.querySelector('[data-accion="alternar"]').click();
    await asentar();
    await asentar();

    // SA-5 (2.2.0): apagar pide el motivo, que va a la bitácora central.
    expect(confirmar).toHaveBeenCalledWith(
      expect.objectContaining({ termino: 'coca-cola' }),
      'apagar',
    );
    const cambio = fetchImpl.llamadas.find((l) => l.clave.startsWith('PUT'));
    expect(cambio.cuerpo).toEqual({
      termino: 'coca-cola',
      activo: false,
      motivo: 'Da falsos positivos en el chat',
    });
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/apagado/);
  });

  test('editar abre el panel con el término, la categoría y el modo; guardar manda los tres', async () => {
    const cerrar = jest.fn();
    const abrirEdicion = jest.fn(({ contenido, acciones }) => {
      document.body.append(contenido, ...acciones);
      return { cerrar };
    });
    const fetchImpl = servicio(
      rutas(
        { cuerpo: pagina([termino()]) },
        {
          'PUT /api/v1/lista-negra/terminos/spiderman': {
            cuerpo: termino({ termino: 'Spider Man', modo: 'PALABRA' }),
          },
        },
      ),
    );
    montarListaNegra(document, { fetchImpl, abrirEdicion });
    await asentar();

    document.querySelector('[data-accion="editar"]').click();
    expect(abrirEdicion).toHaveBeenCalledWith(
      expect.objectContaining({ titulo: 'Editar «spiderman»' }),
    );
    const form = document.querySelector('[data-zona="edicion"]');
    form.querySelector('[name="termino"]').value = 'Spider Man';
    form.querySelector('[name="modo"]').value = 'PALABRA';
    // Sin motivo no se guarda: queda en la bitácora central (SA-5).
    document.querySelector('[data-accion="guardar-termino"]').click();
    await asentar();
    expect(fetchImpl.llamadas.filter((l) => l.clave.startsWith('PUT'))).toHaveLength(0);
    expect(document.querySelector('[data-zona="error-edicion"]').textContent).toMatch(/motivo/);
    form.querySelector('[name="motivo"]').value = 'Se escribe separado';
    document.querySelector('[data-accion="guardar-termino"]').click();
    await asentar();
    await asentar();

    expect(fetchImpl.llamadas.find((l) => l.clave.startsWith('PUT')).cuerpo).toEqual({
      termino: 'Spider Man',
      categoria: 'MARCA',
      modo: 'PALABRA',
      motivo: 'Se escribe separado',
    });
    expect(cerrar).toHaveBeenCalled();
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/Spider Man/);
  });

  test('un error al editar se queda en el panel, que sigue abierto', async () => {
    const cerrar = jest.fn();
    const abrirEdicion = ({ contenido, acciones }) => {
      document.body.append(contenido, ...acciones);
      return { cerrar };
    };
    const fetchImpl = servicio(
      rutas(
        { cuerpo: pagina([termino()]) },
        {
          'PUT /api/v1/lista-negra/terminos/spiderman': {
            estado: 409,
            cuerpo: { title: 'Termino duplicado', detail: 'Ya existe «batman»' },
          },
        },
      ),
    );
    montarListaNegra(document, { fetchImpl, abrirEdicion });
    await asentar();

    document.querySelector('[data-accion="editar"]').click();
    document.querySelector('[data-zona="edicion"] [name="termino"]').value = 'batman';
    document.querySelector('[data-zona="edicion"] [name="motivo"]').value = 'Cambio de prueba';
    document.querySelector('[data-accion="guardar-termino"]').click();
    await asentar();

    const error = document.querySelector('[data-zona="error-edicion"]');
    expect(error.hidden).toBe(false);
    expect(error.textContent).toBe('Ya existe «batman»');
    expect(cerrar).not.toHaveBeenCalled();
  });

  test('quitar pide confirmación y motivo: sin ella no borra; con ella DELETE con el motivo y aviso', async () => {
    const confirmar = jest
      .fn()
      .mockReturnValueOnce(null)
      .mockReturnValueOnce('Repetido con otra forma');
    const fetchImpl = servicio(
      rutas(
        { cuerpo: pagina([termino()]) },
        {
          'DELETE /api/v1/lista-negra/terminos/spiderman?motivo=Repetido%20con%20otra%20forma': {
            estado: 204,
          },
        },
      ),
    );
    montarListaNegra(document, { fetchImpl, confirmar });
    await asentar();

    document.querySelector('[data-accion="eliminar"]').click();
    await asentar();
    expect(fetchImpl.llamadas.filter((l) => l.clave.startsWith('DELETE'))).toHaveLength(0);
    expect(confirmar).toHaveBeenCalledWith(
      expect.objectContaining({ termino: 'spiderman' }),
      'quitar',
    );

    document.querySelector('[data-accion="eliminar"]').click();
    await asentar();
    await asentar();
    expect(fetchImpl.llamadas.filter((l) => l.clave.startsWith('DELETE'))).toHaveLength(1);
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/quitado/);
  });

  test('un fallo de red al quitar se cuenta como error, sin romper la vista', async () => {
    const fetchImpl = servicio(rutas({ cuerpo: pagina([termino()]) }));
    montarListaNegra(document, { fetchImpl, confirmar: () => true });
    await asentar();

    document.querySelector('[data-accion="eliminar"]').click();
    await asentar();

    expect(document.querySelector('.aviso--error').textContent).toMatch(/No se pudo quitar/);
  });

  test('los filtros vuelven a la página 0 con la categoría, el estado y la búsqueda', async () => {
    const fetchImpl = servicio(
      rutas(
        { cuerpo: pagina([termino()]) },
        {
          [`${LISTAR}&categoria=MARCA&activo=false&buscar=spi`]: { cuerpo: pagina([]) },
        },
      ),
    );
    montarListaNegra(document, { fetchImpl });
    await asentar();

    const filtros = document.querySelector('[data-panel="terminos"] [data-zona="filtros"]');
    filtros.querySelector('[name="categoria"]').value = 'MARCA';
    filtros.querySelector('[name="activo"]').value = 'false';
    filtros.querySelector('[name="buscar"]').value = 'spi';
    filtros.dispatchEvent(new Event('submit', { cancelable: true }));
    await asentar();

    expect(
      fetchImpl.llamadas.some((l) => l.clave.endsWith('&categoria=MARCA&activo=false&buscar=spi')),
    ).toBe(true);
    expect(document.querySelector('[data-panel="terminos"]').textContent).toMatch(
      /Ningún término cumple el filtro/,
    );
  });

  test('con más de 16 términos se pagina y la casilla pide la página siguiente', async () => {
    const fetchImpl = servicio(
      rutas(
        { cuerpo: pagina([termino()], { total: 40 }) },
        {
          'GET /api/v1/lista-negra/terminos?pagina=1&tamano=16': {
            cuerpo: pagina([termino({ id: 9, termino: 'hitler', normalizado: 'hitler' })], {
              pagina: 1,
              total: 40,
            }),
          },
        },
      ),
    );
    montarListaNegra(document, { fetchImpl });
    await asentar();

    const nav = document.querySelector('[data-zona="paginacion"] nav');
    expect(nav.hidden).toBe(false);
    expect(nav.getAttribute('aria-label')).toBe('Paginación de la lista negra');
    nav.querySelectorAll('.paginacion__pagina')[1].click();
    await asentar();

    expect(document.querySelector('[data-panel="terminos"] tbody tr').dataset.fila).toBe('9');
  });

  describe('SA-5 · los diálogos de cada acción piden el motivo', () => {
    test('quitar es peligroso; apagar y activar no; los tres dicen qué pasa', () => {
      const t = termino();
      expect(dialogoDeAccion(t, 'quitar')).toMatchObject({
        titulo: '¿Quitar «spiderman» de la lista negra?',
        textoConfirmar: 'Quitar término',
        peligro: true,
      });
      expect(dialogoDeAccion(t, 'apagar')).toMatchObject({
        titulo: '¿Apagar «spiderman»?',
        peligro: false,
      });
      expect(dialogoDeAccion(t, 'activar').mensaje).toMatch(/Vuelve a filtrarse/);
      expect(MOTIVO_MINIMO).toBe(5);
      expect(MOTIVO_MAXIMO).toBe(500);
    });
  });
});
