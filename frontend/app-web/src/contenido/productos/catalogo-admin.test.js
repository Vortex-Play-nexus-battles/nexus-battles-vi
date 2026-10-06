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
  const ficha = () => document.querySelector('[role="dialog"]');
  const campo = (nombre) => ficha().querySelector(`[name="${nombre}"]`);

  test('abre con los datos del producto, su tipo y su estado; el tipo no se edita', () => {
    abrirHojaDeProducto(YELMO, { fetchImpl: jest.fn() });

    expect(ficha().querySelector('h2').textContent).toBe('Yelmo del Alba');
    expect(ficha().textContent).toContain('Versión 3');
    expect(campo('nombre').value).toBe('Yelmo del Alba');
    expect(campo('defensa').value).toBe('4');
    expect(campo('parte').value).toBe('CASCO');
    expect(campo('tipo').type).toBe('hidden');
    expect(campo('precioMonedaReal').disabled).toBe(true);
  });

  test('HU-PRD-007 consulta el historial y revierte el último cambio con confirmación', async () => {
    const alCambiar = jest.fn();
    const confirmarAccion = jest.fn(async () => true);
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

    abrirHojaDeProducto(YELMO, { fetchImpl, confirmarAccion, alCambiar });
    ficha().querySelector('[data-accion="consultar-historial"]').click();
    await esperar();

    const revertir = ficha().querySelector('[data-accion="revertir-producto"]');
    expect(ficha().textContent).toContain('admin-uid');
    expect(revertir.disabled).toBe(false);
    revertir.click();
    await esperar();

    expect(confirmarAccion).toHaveBeenCalled();
    expect(fetchImpl).toHaveBeenCalledWith(`/api/v1/productos/${YELMO.id}/reversiones`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ respaldoId: 'respaldo-1' }),
    });
    expect(ficha().querySelector('.aviso--exito').textContent).toContain('Producto restaurado');
    expect(alCambiar).toHaveBeenCalledWith(
      expect.objectContaining({ nombre: 'Yelmo anterior', version: 4 }),
    );
  });

  test('RFINAL-07: sin promoción el resumen no escribe «null» (revisión de DEV del 4-oct)', () => {
    abrirHojaDeProducto({ ...YELMO, promocion: null }, { fetchImpl: jest.fn() });

    const resumen = ficha().querySelector('.hoja-producto__resumen');
    expect(resumen.textContent).not.toContain('null');
    expect(resumen.textContent).toContain('Versión 3');
    // Solo elementos: ningún nodo de texto suelto entre las líneas del resumen.
    expect([...resumen.childNodes].every((nodo) => nodo.nodeType === Node.ELEMENT_NODE)).toBe(true);
  });

  test('guardar manda solo lo que cambió y pinta la versión nueva', async () => {
    const alCambiar = jest.fn();
    const fetchImpl = jest.fn(async (_url, opciones) =>
      respuesta({ ...YELMO, ...JSON.parse(opciones.body), version: 4 }),
    );
    abrirHojaDeProducto(YELMO, { fetchImpl, alCambiar });

    campo('defensa').value = '6';
    ficha()
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();

    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe(`/api/v1/productos/${YELMO.id}`);
    expect(opciones.method).toBe('PATCH');
    expect(JSON.parse(opciones.body)).toEqual({ defensa: 6 });
    expect(ficha().querySelector('.aviso--exito').textContent).toContain('versión 4');
    expect(alCambiar).toHaveBeenCalledWith(expect.objectContaining({ defensa: 6, version: 4 }));
  });

  test('sin cambios no se llama al servicio; un dato inválido se marca en su campo', async () => {
    const fetchImpl = jest.fn();
    abrirHojaDeProducto(YELMO, { fetchImpl });
    const formulario = ficha().querySelector('form');

    formulario.dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    expect(fetchImpl).not.toHaveBeenCalled();
    expect(ficha().querySelector('.aviso--info').textContent).toContain('No hay nada que guardar');

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
    abrirHojaDeProducto(conPromocion, { fetchImpl });

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
    abrirHojaDeProducto(YELMO, { fetchImpl });
    campo('nombre').value = 'Yelmo del Ocaso';
    ficha()
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();

    const aviso = ficha().querySelector('.aviso--advertencia');
    expect(aviso.textContent).toContain('Un producto premium no puede tener precio en créditos.');
  });

  test('suspender pide confirmación, llama al servicio y cambia a «Reactivar»', async () => {
    const confirmarAccion = jest.fn(async () => true);
    const alCambiar = jest.fn();
    const fetchImpl = jest.fn(async () =>
      respuesta({ productoId: YELMO.id, estado: 'SUSPENDIDO', tiraje: 40 }),
    );
    abrirHojaDeProducto(YELMO, { fetchImpl, confirmarAccion, alCambiar });

    ficha().querySelector('[data-accion="suspender"]').click();
    await esperar();

    expect(confirmarAccion).toHaveBeenCalledWith(
      expect.objectContaining({
        titulo: '¿Suspender «Yelmo del Alba»?',
        textoConfirmar: 'Suspender',
      }),
    );
    expect(fetchImpl).toHaveBeenCalledWith(`/api/v1/productos/${YELMO.id}/suspender`, {
      method: 'PUT',
    });
    expect(ficha().querySelector('[data-accion="reactivar"]')).not.toBeNull();
    expect(ficha().textContent).toContain('Suspendido');
    expect(alCambiar).toHaveBeenCalledWith(expect.objectContaining({ estado: 'SUSPENDIDO' }));
  });

  test('cancelar la confirmación no toca el producto', async () => {
    const fetchImpl = jest.fn();
    abrirHojaDeProducto(YELMO, { fetchImpl, confirmarAccion: async () => false });
    ficha().querySelector('[data-accion="suspender"]').click();
    await esperar();
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  test('un 403 habla del rol, no de un error genérico', async () => {
    const fetchImpl = jest.fn(async () => respuesta({ status: 403 }, 403));
    abrirHojaDeProducto(
      { ...YELMO, estado: 'SUSPENDIDO' },
      {
        fetchImpl,
        confirmarAccion: async () => true,
      },
    );
    ficha().querySelector('[data-accion="reactivar"]').click();
    await esperar();
    expect(ficha().querySelector('.aviso').textContent).toContain('Tu rol no puede hacer esto');
  });
});
