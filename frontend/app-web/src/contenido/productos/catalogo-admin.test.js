/**
 * UXC-7 — el catálogo en la consola y la ficha de gestión de un producto
 * (ProductAdminSheet), contra `productos.yaml`. Los productos son datos de
 * prueba.
 */

import { jest } from '@jest/globals';

import {
  abrirHojaDeProducto,
  cambiosDe,
  mismaPromocion,
  montarCatalogoAdmin,
  textoDePrecio,
  textoDePromocion,
  textoDeTiraje,
} from './catalogo-admin.js';

const YELMO = {
  id: 'aaaaaaa1-0000-4000-8000-000000000001',
  nombre: 'Yelmo del Alba',
  imagen: 'productos/yelmo-del-alba.png',
  descripcion: 'Acero claro, forjado al amanecer.',
  tipo: 'ARMADURA',
  tiraje: 40,
  premium: false,
  precioCreditos: 1200,
  defensa: 4,
  parte: 'CASCO',
  tasaDeCaida: 12.5,
  estado: 'ACTIVO',
  version: 3,
  creadoEn: '2026-09-01T10:00:00Z',
  modificadoEn: '2026-09-20T10:00:00Z',
};

const esperar = async () => {
  for (let i = 0; i < 8; i += 1) {
    await Promise.resolve();
  }
};

function respuesta(cuerpo, estado = 200) {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    text: async () => (cuerpo === null ? '' : JSON.stringify(cuerpo)),
  };
}

function pagina(productos, extra = {}) {
  return {
    content: productos,
    page: 0,
    size: 16,
    totalElements: productos.length,
    totalPages: productos.length ? 1 : 0,
    ...extra,
  };
}

beforeEach(() => {
  document.body.innerHTML = '<section id="catalogo"></section>';
});

describe('formato', () => {
  test('tiraje y precio en palabras', () => {
    expect(textoDeTiraje(-1)).toBe('Ilimitado');
    expect(textoDeTiraje(1)).toBe('1 unidad');
    expect(textoDeTiraje(40)).toBe('40 unidades');
    expect(textoDePrecio(YELMO)).toBe('1.200 créditos');
    expect(textoDePrecio({ premium: true, precioMonedaReal: 18500 })).toMatch(
      /^18\.500,00 en moneda real$/,
    );
  });

  test('cambiosDe: solo lo distinto y nunca el tipo', () => {
    expect(cambiosDe(YELMO, { tipo: 'ARMADURA', nombre: 'Yelmo del Alba', defensa: 6 })).toEqual({
      defensa: 6,
    });
    expect(cambiosDe(YELMO, { tipo: 'ARMADURA', nombre: 'Yelmo del Alba' })).toEqual({});
  });
});

describe('UXC-9 — promoción', () => {
  const AHORA = new Date('2026-09-28T12:00:00Z').getTime();

  test('mismaPromocion compara por lo que dice, no por cómo viene escrita', () => {
    const a = { porcentaje: 20, desde: '2026-10-01T10:00:00Z', hasta: '2026-10-15T10:00:00Z' };
    expect(
      mismaPromocion(a, {
        porcentaje: 20,
        desde: '2026-10-01T10:00:00.000Z',
        hasta: '2026-10-15T10:00:00+00:00',
      }),
    ).toBe(true);
    expect(mismaPromocion(a, { ...a, porcentaje: 25 })).toBe(false);
    expect(mismaPromocion(null, undefined)).toBe(true);
    expect(mismaPromocion(a, null)).toBe(false);
  });

  test('vigente, programada o terminada, dicho en palabras', () => {
    const base = { porcentaje: 20, desde: '2026-10-01T10:00:00Z', hasta: '2026-10-15T10:00:00Z' };
    expect(textoDePromocion({ ...base, vigente: true }, AHORA).estado).toBe('vigente');
    expect(textoDePromocion({ ...base, vigente: false }, AHORA)).toEqual(
      expect.objectContaining({ estado: 'programada' }),
    );
    expect(
      textoDePromocion(
        { porcentaje: 20, desde: '2026-08-01T10:00:00Z', hasta: '2026-08-15T10:00:00Z' },
        AHORA,
      ).texto,
    ).toMatch(/^20 % de descuento, terminó el /);
    expect(textoDePromocion(null, AHORA)).toBeNull();
  });
});

describe('la lista del catálogo', () => {
  test('pide 16 por página y pinta cada producto con su estado en palabras', async () => {
    const fetchImpl = jest.fn(async () => respuesta(pagina([YELMO])));
    montarCatalogoAdmin(document.getElementById('catalogo'), { fetchImpl });
    await esperar();

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/productos?page=0&size=16');
    const fila = document.querySelector(`tr[data-producto="${YELMO.id}"]`);
    expect(fila.querySelector('th').textContent).toContain('Yelmo del Alba');
    expect(fila.textContent).toContain('Activo');
    expect(fila.textContent).toContain('40 unidades');
    expect(document.querySelector('.catalogo-admin__cuenta').textContent).toBe('1 producto');
    expect(document.querySelector('.catalogo-admin__desplazable').getAttribute('tabindex')).toBe(
      '0',
    );
  });

  test('los suspendidos se piden explícitamente, como dice el contrato', async () => {
    const fetchImpl = jest.fn(async () => respuesta(pagina([])));
    montarCatalogoAdmin(document.getElementById('catalogo'), { fetchImpl });
    await esperar();

    const estado = document.querySelector('select[name="estado"]');
    estado.value = 'SUSPENDIDO';
    estado.dispatchEvent(new Event('change', { bubbles: true }));
    await esperar();

    expect(fetchImpl.mock.calls.at(-1)[0]).toBe(
      '/api/v1/productos?page=0&size=16&estado=SUSPENDIDO',
    );
    const vacio = document.querySelector('.estado-vista--vacio');
    expect(vacio.textContent).toContain('Ningún producto cumple estos filtros');
    vacio.querySelector('[data-accion="quitar-filtros"]').click();
    await esperar();
    expect(estado.value).toBe('');
    expect(fetchImpl.mock.calls.at(-1)[0]).toBe('/api/v1/productos?page=0&size=16');
  });

  test('un catálogo vacío invita a crear; un fallo se dice y deja reintentar', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValueOnce(respuesta(pagina([])))
      .mockRejectedValueOnce(new Error('red'))
      .mockResolvedValueOnce(respuesta(pagina([YELMO])));
    const { recargar } = montarCatalogoAdmin(document.getElementById('catalogo'), { fetchImpl });
    await esperar();
    expect(document.querySelector('.estado-vista--vacio').textContent).toContain(
      'El catálogo está vacío',
    );

    await recargar();
    const error = document.querySelector('.estado-vista--error');
    expect(error.textContent).toContain('No pudimos cargar el catálogo');
    error.querySelector('[data-accion="reintentar"]').click();
    await esperar();
    expect(document.querySelectorAll('tbody tr')).toHaveLength(1);
  });

  test('con más de una página hay paginación, y cambiar de página pide la siguiente', async () => {
    const fetchImpl = jest.fn(async () =>
      respuesta(pagina([YELMO], { totalElements: 20, totalPages: 2 })),
    );
    montarCatalogoAdmin(document.getElementById('catalogo'), { fetchImpl });
    await esperar();

    const control = document.querySelector('.paginacion');
    expect(control.getAttribute('aria-label')).toBe('Páginas del catálogo');
    [...control.querySelectorAll('button')].find((b) => b.textContent.trim() === '2').click();
    await esperar();
    expect(fetchImpl.mock.calls.at(-1)[0]).toBe('/api/v1/productos?page=1&size=16');
  });
});

describe('la ficha de gestión (ProductAdminSheet)', () => {
  const titulares = {
    de: (uid) => (uid === 'admin-uid' ? { apodo: 'Raíz' } : null),
    nombrar: jest.fn(async () => true),
  };
  const abrir = (producto, opciones = {}) =>
    abrirHojaDeProducto(producto, { titulares, ...opciones });
  const ficha = () => document.querySelector('[role="dialog"]');
  const campo = (nombre) => ficha().querySelector(`[name="${nombre}"]`);

  test('abre con los datos del producto, su tipo y su estado; el tipo no se edita', () => {
    abrir(YELMO, { fetchImpl: jest.fn() });

    expect(ficha().querySelector('h2').textContent).toBe('Yelmo del Alba');
    expect(ficha().textContent).toContain('Versión 3');
    expect(campo('nombre').value).toBe('Yelmo del Alba');
    expect(campo('defensa').value).toBe('4');
    expect(campo('parte').value).toBe('CASCO');
    expect(campo('tipo').type).toBe('hidden');
    expect(campo('precioMonedaReal').disabled).toBe(true);
  });

  test('HU-PRD-007 consulta el historial y revierte el último cambio con su motivo', async () => {
    const alCambiar = jest.fn();
    const pedirMotivo = jest.fn(async () => 'Precio mal puesto');
    const cambio = {
      id: 'respaldo-1',
      productoId: YELMO.id,
      tipo: 'MODIFICACION',
      campos: ['nombre', 'defensa'],
      autor: 'admin-uid',
      fecha: '2026-10-05T10:00:00Z',
      versionAnterior: 2,
      versionAplicada: 3,
      revertible: true,
    };
    const fetchImpl = jest.fn(async (url, opciones = {}) => {
      if (url.endsWith('/historial')) {
        return respuesta([cambio]);
      }
      if (url.endsWith('/reversiones') && opciones.method === 'POST') {
        return respuesta({ ...YELMO, nombre: 'Yelmo anterior', version: 4 });
      }
      throw new Error(`Petición inesperada: ${url}`);
    });

    abrir(YELMO, { fetchImpl, pedirMotivo, alCambiar });
    ficha().querySelector('[data-accion="consultar-historial"]').click();
    await esperar();

    const revertir = ficha().querySelector('[data-accion="revertir-producto"]');
    const entrada = ficha().querySelector('[data-cambio="respaldo-1"]');
    expect(entrada.textContent).toContain('Modificación');
    expect(entrada.textContent).toContain('Raíz');
    expect(entrada.textContent).toContain('Cambió: nombre, defensa.');
    expect(ficha().textContent).not.toContain('admin-uid');
    revertir.click();
    await esperar();

    expect(pedirMotivo).toHaveBeenCalledWith(
      expect.objectContaining({
        titulo: '¿Revertir «Yelmo del Alba»?',
        etiquetaMotivo: 'Motivo de la reversión',
        textoConfirmar: 'Revertir',
      }),
    );
    expect(fetchImpl).toHaveBeenCalledWith(`/api/v1/productos/${YELMO.id}/reversiones`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ respaldoId: 'respaldo-1', motivo: 'Precio mal puesto' }),
    });
    expect(ficha().querySelector('.aviso--exito').textContent).toContain('Producto restaurado');
    expect(alCambiar).toHaveBeenCalledWith(
      expect.objectContaining({ nombre: 'Yelmo anterior', version: 4 }),
    );
  });

  test('HU-PRD-011 lista versiones e importa una plantilla sin cambiar los demás datos', async () => {
    const confirmarAccion = jest.fn(async () => true);
    const alCambiar = jest.fn();
    const plantilla = {
      formato: 'nexus-producto-diseno',
      versionFormato: 1,
      productoOrigenId: 'producto-origen',
      versionProducto: 5,
      imagen: 'productos/diseno-importado.webp',
      descripcion: 'Diseño importado',
      exportadaEn: '2026-10-11T15:00:00Z',
    };
    const fetchImpl = jest.fn(async (url, opciones = {}) => {
      if (url.endsWith('/diseno/versiones')) {
        return respuesta([
          {
            version: 3,
            fecha: '2026-10-11T14:00:00Z',
            autor: 'admin-uid',
            imagen: YELMO.imagen,
            descripcion: YELMO.descripcion,
            actual: true,
          },
        ]);
      }
      if (url.endsWith('/diseno/importaciones') && opciones.method === 'POST') {
        return respuesta({
          ...YELMO,
          imagen: plantilla.imagen,
          descripcion: plantilla.descripcion,
          version: 4,
        });
      }
      throw new Error(`Petición inesperada: ${url}`);
    });

    abrir(YELMO, { fetchImpl, confirmarAccion, alCambiar });
    ficha().querySelector('[data-accion="consultar-versiones-diseno"]').click();
    await esperar();

    expect(ficha().querySelector('[data-version="3"]').textContent).toContain('Raíz');
    expect(ficha().querySelector('[data-version="3"]').textContent).toContain('Publicada');
    expect(ficha().querySelector('[data-version="3"] img').getAttribute('src')).toBe(YELMO.imagen);

    const archivo = ficha().querySelector('[data-accion="archivo-plantilla-diseno"]');
    Object.defineProperty(archivo, 'files', {
      configurable: true,
      value: [{ text: async () => JSON.stringify(plantilla) }],
    });
    archivo.dispatchEvent(new Event('change'));
    await esperar();
    await esperar();

    expect(confirmarAccion).toHaveBeenCalledWith(
      expect.objectContaining({ textoConfirmar: 'Importar' }),
    );
    expect(fetchImpl).toHaveBeenCalledWith(
      `/api/v1/productos/${YELMO.id}/diseno/importaciones`,
      expect.objectContaining({ method: 'POST', body: JSON.stringify(plantilla) }),
    );
    expect(alCambiar).toHaveBeenCalledWith(
      expect.objectContaining({ imagen: plantilla.imagen, descripcion: plantilla.descripcion }),
    );
    expect(ficha().querySelector('.aviso--exito').textContent).toContain('Diseño importado');
  });

  test('RFINAL-07: sin promoción el resumen no escribe «null» (revisión de DEV del 4-oct)', () => {
    abrir({ ...YELMO, promocion: null }, { fetchImpl: jest.fn() });

    const resumen = ficha().querySelector('.hoja-producto__resumen');
    expect(resumen.textContent).not.toContain('null');
    expect(resumen.textContent).toContain('Versión 3');
    // Solo elementos: ningún nodo de texto suelto entre las líneas del resumen.
    expect([...resumen.childNodes].every((nodo) => nodo.nodeType === Node.ELEMENT_NODE)).toBe(true);
  });

  test('publicar confirma lo que cambia, manda solo eso y pinta la versión nueva', async () => {
    const alCambiar = jest.fn();
    const confirmarAccion = jest.fn(async () => true);
    const fetchImpl = jest.fn(async (_url, opciones) =>
      respuesta({ ...YELMO, ...JSON.parse(opciones.body), version: 4 }),
    );
    abrir(YELMO, { fetchImpl, alCambiar, confirmarAccion });

    campo('defensa').value = '6';
    ficha()
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();

    expect(confirmarAccion).toHaveBeenCalledWith(
      expect.objectContaining({
        titulo: '¿Publicar los cambios de «Yelmo del Alba»?',
        textoConfirmar: 'Publicar',
      }),
    );
    expect(confirmarAccion.mock.calls[0][0].mensaje).toMatch(/^Se publicará: defensa\./);
    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe(`/api/v1/productos/${YELMO.id}`);
    expect(opciones.method).toBe('PATCH');
    expect(JSON.parse(opciones.body)).toEqual({ defensa: 6 });
    expect(ficha().querySelector('.aviso--exito').textContent).toContain('versión 4');
    expect(alCambiar).toHaveBeenCalledWith(expect.objectContaining({ defensa: 6, version: 4 }));
  });

  test('sin cambios no se llama al servicio; un dato inválido se marca en su campo', async () => {
    const fetchImpl = jest.fn();
    abrir(YELMO, { fetchImpl });
    const formulario = ficha().querySelector('form');

    formulario.dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    expect(fetchImpl).not.toHaveBeenCalled();
    expect(ficha().querySelector('.aviso--info').textContent).toContain('No hay nada que publicar');

    campo('tasaDeCaida').value = '140';
    formulario.dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    expect(fetchImpl).not.toHaveBeenCalled();
    expect(campo('tasaDeCaida').getAttribute('aria-invalid')).toBe('true');
  });

  test('UXC-9 — la promoción vigente se dice y se edita; solo viaja si cambió', async () => {
    const conPromocion = {
      ...YELMO,
      promocion: {
        porcentaje: 20,
        desde: '2026-09-20T10:00:00Z',
        hasta: '2099-10-15T10:00:00Z',
        vigente: true,
      },
    };
    const fetchImpl = jest.fn(async (_url, opciones) =>
      respuesta({ ...conPromocion, ...JSON.parse(opciones.body), version: 4 }),
    );
    abrir(conPromocion, { fetchImpl, confirmarAccion: async () => true });

    expect(ficha().querySelector('[data-zona="promocion-actual"]').textContent).toMatch(
      /^Promoción: 20 % de descuento, vigente hasta el /,
    );
    expect(campo('promocionPorcentaje').value).toBe('20');
    const formulario = ficha().querySelector('form');

    // Cambiar otra cosa no reenvía la promoción: es la misma.
    campo('defensa').value = '6';
    formulario.dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual({ defensa: 6 });

    campo('promocionPorcentaje').value = '35';
    formulario.dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    const cuerpo = JSON.parse(fetchImpl.mock.calls[1][1].body);
    expect(cuerpo).toEqual({ promocion: expect.objectContaining({ porcentaje: 35 }) });
    // La fecha viaja como el mismo instante que ya tenía (el campo local no
    // la cambia).
    expect(new Date(cuerpo.promocion.hasta).getTime()).toBe(
      new Date('2099-10-15T10:00:00Z').getTime(),
    );
  });

  test('si el servidor rechaza la combinación, se dice su motivo', async () => {
    const fetchImpl = jest.fn(async () =>
      respuesta(
        { status: 400, detail: 'Un producto premium no puede tener precio en créditos.' },
        400,
      ),
    );
    abrir(YELMO, { fetchImpl, confirmarAccion: async () => true });
    campo('nombre').value = 'Yelmo del Ocaso';
    ficha()
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();

    const aviso = ficha().querySelector('.aviso--advertencia');
    expect(aviso.textContent).toContain('Un producto premium no puede tener precio en créditos.');
  });

  test('suspender pide confirmación con motivo, llama al servicio y cambia a «Reactivar»', async () => {
    const pedirMotivo = jest.fn(async () => 'Error de balance');
    const alCambiar = jest.fn();
    const fetchImpl = jest.fn(async () =>
      respuesta({ productoId: YELMO.id, estado: 'SUSPENDIDO', tiraje: 40 }),
    );
    abrir(YELMO, { fetchImpl, pedirMotivo, alCambiar });

    ficha().querySelector('[data-accion="suspender"]').click();
    await esperar();

    expect(pedirMotivo).toHaveBeenCalledWith(
      expect.objectContaining({
        titulo: '¿Suspender «Yelmo del Alba»?',
        etiquetaMotivo: 'Motivo de la suspensión',
        textoConfirmar: 'Suspender',
        consecuencias: expect.arrayContaining(['Desaparece de la tienda.']),
      }),
    );
    expect(fetchImpl).toHaveBeenCalledWith(
      `/api/v1/productos/${YELMO.id}/suspender?motivo=Error+de+balance`,
      { method: 'PUT' },
    );
    expect(ficha().querySelector('[data-accion="reactivar"]')).not.toBeNull();
    expect(ficha().textContent).toContain('Suspendido');
    expect(alCambiar).toHaveBeenCalledWith(expect.objectContaining({ estado: 'SUSPENDIDO' }));
  });

  test('cancelar la confirmación no toca el producto', async () => {
    const fetchImpl = jest.fn();
    abrir(YELMO, { fetchImpl, pedirMotivo: async () => null });
    ficha().querySelector('[data-accion="suspender"]').click();
    await esperar();
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  test('un 403 habla del rol, no de un error genérico', async () => {
    const fetchImpl = jest.fn(async () => respuesta({ status: 403 }, 403));
    abrir(
      { ...YELMO, estado: 'SUSPENDIDO' },
      {
        fetchImpl,
        pedirMotivo: async () => 'Vuelve a la venta',
      },
    );
    ficha().querySelector('[data-accion="reactivar"]').click();
    await esperar();
    expect(ficha().querySelector('.aviso').textContent).toContain('Tu rol no puede hacer esto');
  });

  test('SA-6: la imagen se sube o se elige; no hay rutas, UUID ni identificadores a la vista', () => {
    abrir(YELMO, { fetchImpl: jest.fn() });
    expect(ficha().querySelector('.selector-recurso')).not.toBeNull();
    expect(campo('imagen').type).toBe('hidden');
    expect(campo('imagen').value).toBe(YELMO.imagen);
    expect(ficha().querySelector('.selector-recurso img').getAttribute('src')).toBe(YELMO.imagen);
    expect(ficha().textContent).not.toMatch(/Referencia de imagen|Identificador|UUID/);
  });

  test('HU-PRD-010: la vista previa sigue lo escrito; descartar deja lo publicado', async () => {
    const fetchImpl = jest.fn();
    abrir(YELMO, { fetchImpl });
    const previa = () => ficha().querySelector('[data-zona="vista-previa-hoja"]');
    expect(previa().querySelector('.product-card__nombre').textContent).toBe('Yelmo del Alba');
    expect(ficha().querySelector('.hoja-producto__pendientes').textContent).toBe(
      'Es lo que hoy ven los jugadores.',
    );
    expect(ficha().querySelector('[data-accion="guardar-producto"]').disabled).toBe(true);

    campo('nombre').value = 'Yelmo del Ocaso';
    ficha()
      .querySelector('form')
      .dispatchEvent(new Event('input', { bubbles: true }));
    expect(previa().querySelector('.product-card__nombre').textContent).toBe('Yelmo del Ocaso');
    expect(ficha().querySelector('.hoja-producto__pendientes').textContent).toBe(
      'Cambios sin publicar: nombre.',
    );
    expect(ficha().querySelector('[data-accion="guardar-producto"]').disabled).toBe(false);

    ficha().querySelector('[data-accion="descartar-cambios"]').click();
    expect(campo('nombre').value).toBe('Yelmo del Alba');
    expect(previa().querySelector('.product-card__nombre').textContent).toBe('Yelmo del Alba');
    expect(ficha().querySelector('.aviso--info').textContent).toContain('Cambios descartados');
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  test('HU-PRD-011: restaurar una versión anterior trae su imagen y descripción', async () => {
    const confirmarAccion = jest.fn(async () => true);
    const fetchImpl = jest.fn(async (url, opciones = {}) => {
      if (url.endsWith('/diseno/versiones')) {
        return respuesta([
          {
            version: 3,
            fecha: '2026-10-11T14:00:00Z',
            autor: 'admin-uid',
            imagen: YELMO.imagen,
            descripcion: YELMO.descripcion,
            actual: true,
          },
          {
            version: 2,
            fecha: '2026-10-01T14:00:00Z',
            autor: null,
            imagen: 'data:image/png;base64,AA==',
            descripcion: 'Antes',
            actual: false,
          },
        ]);
      }
      if (url.endsWith('/diseno/importaciones') && opciones.method === 'POST') {
        const cuerpo = JSON.parse(opciones.body);
        return respuesta({
          ...YELMO,
          imagen: cuerpo.imagen,
          descripcion: cuerpo.descripcion,
          version: 4,
        });
      }
      throw new Error(`Petición inesperada: ${url}`);
    });
    abrir(YELMO, { fetchImpl, confirmarAccion });
    ficha().querySelector('[data-accion="consultar-versiones-diseno"]').click();
    await esperar();

    expect(ficha().querySelector('[data-version="2"]').textContent).toContain('Catálogo inicial');
    expect(ficha().querySelector('[data-version="3"] [data-accion="restaurar-diseno"]')).toBeNull();
    ficha().querySelector('[data-version="2"] [data-accion="restaurar-diseno"]').click();
    await esperar();
    await esperar();

    expect(confirmarAccion).toHaveBeenCalledWith(
      expect.objectContaining({ titulo: '¿Restaurar el diseño de la versión 2?' }),
    );
    const llamada = fetchImpl.mock.calls.find(([url]) => url.endsWith('/diseno/importaciones'));
    expect(JSON.parse(llamada[1].body)).toMatchObject({
      formato: 'nexus-producto-diseno',
      versionFormato: 1,
      productoOrigenId: YELMO.id,
      versionProducto: 2,
      imagen: 'data:image/png;base64,AA==',
      descripcion: 'Antes',
    });
    expect(ficha().querySelector('.aviso--exito').textContent).toContain('Diseño restaurado');
    expect(campo('descripcion').value).toBe('Antes');
  });

  test('el héroe de una habilidad se elige por su nombre', async () => {
    const HEROE = '0a1b2c3d-4e5f-3a1b-9c2d-0123456789ab';
    const listarHeroes = jest.fn(async () => ({ content: [{ id: HEROE, nombre: 'Mago Fuego' }] }));
    abrir(
      {
        ...YELMO,
        tipo: 'HABILIDAD',
        heroe: HEROE,
        costoPoder: 2,
        multiplicadorNivel: 1.5,
        turnosCarga: 1,
      },
      { fetchImpl: jest.fn(), listarHeroes },
    );
    await esperar();
    const selector = campo('heroe');
    expect(selector.tagName).toBe('SELECT');
    expect([...selector.options].map((o) => o.text)).toEqual(['Elige el héroe', 'Mago Fuego']);
    expect(selector.value).toBe(HEROE);
  });
});
