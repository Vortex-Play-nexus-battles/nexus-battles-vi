/**
 * Entregar productos al inventario de una cuenta — SA-10, §7.3.4
 * (ms-identidad-admin.yaml 1.10.0).
 */

import { jest } from '@jest/globals';

import {
  abrirEntregaAdministrativa,
  claveDeLaOperacion,
  pedirEntrega,
  productosDelTipo,
  puedeEntregarA,
  resumenDeEntrega,
  textoDelRechazo,
  validarEntrega,
} from './entrega-administrativa.js';

const asentar = async () => {
  for (let i = 0; i < 6; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
};

const respuesta = (estado, cuerpo) => ({
  ok: estado >= 200 && estado < 300,
  status: estado,
  json: async () => cuerpo,
});

const catalogo = (filas, { totalPages = 1 } = {}) => ({
  content: filas,
  page: 0,
  size: 50,
  totalElements: filas.length,
  totalPages,
});

describe('quién puede modificar el inventario de quién (7.3.1)', () => {
  test('Administrador sobre roles inferiores; Super Administrador también sobre otro', () => {
    expect(puedeEntregarA('ADMINISTRADOR', 'JUGADOR')).toBe(true);
    expect(puedeEntregarA('ADMINISTRADOR', 'MODERADOR')).toBe(true);
    expect(puedeEntregarA('ADMINISTRADOR', 'ADMINISTRADOR')).toBe(false);
    expect(puedeEntregarA('ADMINISTRADOR', 'SUPER_ADMINISTRADOR')).toBe(false);
    expect(puedeEntregarA('SUPER_ADMINISTRADOR', 'ADMINISTRADOR')).toBe(true);
    expect(puedeEntregarA('SUPER_ADMINISTRADOR', 'SUPER_ADMINISTRADOR')).toBe(true);
    expect(puedeEntregarA('MODERADOR', 'JUGADOR')).toBe(false);
    expect(puedeEntregarA('JUGADOR', 'JUGADOR')).toBe(false);
    expect(puedeEntregarA(null, 'JUGADOR')).toBe(false);
  });
});

describe('el formulario', () => {
  test('dice qué falta, campo por campo', () => {
    expect(validarEntrega({ productoId: '', cantidad: '0', motivo: 'no' })).toEqual({
      producto: 'Elige el producto.',
      cantidad: 'De 1 a 20 unidades.',
      motivo: 'Escribe por qué se entrega: queda en la auditoría.',
    });
    expect(validarEntrega({ productoId: 'p', cantidad: '21', motivo: 'Compensación' })).toEqual({
      cantidad: 'De 1 a 20 unidades.',
    });
    expect(
      validarEntrega({ productoId: 'p', cantidad: '2.5', motivo: 'Compensación' }).cantidad,
    ).toBe('De 1 a 20 unidades.');
    expect(validarEntrega({ productoId: 'p', cantidad: '3', motivo: 'x'.repeat(501) }).motivo).toBe(
      'Como mucho 500 caracteres.',
    );
    expect(validarEntrega({ productoId: 'p', cantidad: '3', motivo: ' Compensación ' })).toEqual(
      {},
    );
  });

  test('la frase de lo que va a pasar, en singular o en plural', () => {
    expect(resumenDeEntrega({ cantidad: '1', nombre: 'Hacha', apodo: 'ana' })).toBe(
      'Se entregará 1 × Hacha a ana. Queda en la auditoría con tu motivo.',
    );
    expect(resumenDeEntrega({ cantidad: '3', nombre: 'Hacha', apodo: 'ana' })).toBe(
      'Se entregarán 3 × Hacha a ana. Queda en la auditoría con tu motivo.',
    );
    expect(resumenDeEntrega({ cantidad: '3', nombre: null, apodo: 'ana' })).toBe(
      'Elige el producto y cuántas unidades.',
    );
  });

  test('cada rechazo en palabras y si se repite con la misma clave', () => {
    const errores = 'https://nexusbattles.upb.edu.co/errors/';
    expect(textoDelRechazo(503, { type: `${errores}auditoria-no-disponible` })).toEqual({
      texto: expect.stringContaining('no se entregará dos veces'),
      repetirConLaMismaClave: true,
    });
    expect(textoDelRechazo(503, { type: `${errores}inventario-no-disponible` })).toEqual({
      texto: expect.stringContaining('no se entregó nada'),
      repetirConLaMismaClave: true,
    });
    expect(textoDelRechazo(0, null).repetirConLaMismaClave).toBe(true);
    expect(textoDelRechazo(403, { type: `${errores}jerarquia-insuficiente` }).texto).toBe(
      'No puedes modificar el inventario de una cuenta con un rol igual o superior al tuyo.',
    );
    expect(textoDelRechazo(409, { detail: 'El producto esta suspendido.' })).toEqual({
      texto: 'El producto esta suspendido.',
      repetirConLaMismaClave: false,
    });
  });

  test('la clave se mantiene hasta que se renueva', () => {
    const clave = claveDeLaOperacion();
    const primera = clave.actual();
    expect(primera.length).toBeGreaterThanOrEqual(8);
    expect(clave.actual()).toBe(primera);
    expect(clave.renovar()).not.toBe(primera);
  });
});

describe('el catálogo y la entrega', () => {
  test('los productos del tipo: todas las páginas, sin suspendidos y por nombre', async () => {
    const buscar = jest
      .fn()
      .mockResolvedValueOnce(
        respuesta(
          200,
          catalogo(
            [
              { id: 'p2', nombre: 'Martillo', estado: 'ACTIVO' },
              { id: 'p3', nombre: 'Daga', estado: 'SUSPENDIDO' },
            ],
            { totalPages: 2 },
          ),
        ),
      )
      .mockResolvedValueOnce(
        respuesta(
          200,
          catalogo([{ id: 'p1', nombre: 'Hacha', estado: 'UNICO' }], { totalPages: 2 }),
        ),
      );

    const productos = await productosDelTipo('ARMA', { buscar });

    expect(productos).toEqual([
      { id: 'p1', nombre: 'Hacha' },
      { id: 'p2', nombre: 'Martillo' },
    ]);
    expect(buscar.mock.calls[0][0]).toContain('/api/v1/productos?tipo=ARMA&size=50&page=0');
    expect(buscar.mock.calls[1][0]).toContain('page=1');
    await expect(
      productosDelTipo('ARMA', { buscar: jest.fn().mockResolvedValue(respuesta(503, {})) }),
    ).rejects.toThrow();
  });

  test('la entrega: POST con la clave y el cuerpo; el rechazo trae su estado y su problema', async () => {
    const buscar = jest
      .fn()
      .mockResolvedValueOnce(respuesta(201, { entregaId: 'e-1', nueva: true }))
      .mockResolvedValueOnce(respuesta(409, { detail: 'Suspendido' }))
      .mockRejectedValueOnce(new TypeError('sin red'));
    const datos = { productoId: 'p1', cantidad: 2, motivo: 'Compensación' };

    expect(await pedirEntrega(15, datos, 'clave-0001', { buscar })).toEqual({
      ok: true,
      estado: 201,
      entrega: { entregaId: 'e-1', nueva: true },
    });
    const [url, opciones] = buscar.mock.calls[0];
    expect(url).toContain('/api/v1/admin/usuarios/15/inventario/entregas');
    expect(opciones.method).toBe('POST');
    expect(opciones.headers['Idempotency-Key']).toBe('clave-0001');
    expect(JSON.parse(opciones.body)).toEqual(datos);
    expect(opciones.rechazoEsperado).toBe(true);

    expect(await pedirEntrega(15, datos, 'clave-0001', { buscar })).toEqual({
      ok: false,
      estado: 409,
      problema: { detail: 'Suspendido' },
    });
    expect(await pedirEntrega(15, datos, 'clave-0001', { buscar })).toEqual({
      ok: false,
      estado: 0,
      problema: null,
    });
  });
});

describe('el panel de la entrega', () => {
  function montarPanel({ respuestasDeEntrega }) {
    document.body.replaceChildren();
    const cerrar = jest.fn();
    const abrirPanel = jest.fn(({ contenido, acciones }) => {
      const panel = document.createElement('div');
      panel.setAttribute('role', 'dialog');
      panel.append(...contenido, ...acciones);
      document.body.append(panel);
      return { cerrar };
    });
    const entregas = [...respuestasDeEntrega];
    const buscar = jest.fn(async (url, opciones = {}) => {
      if (opciones.method === 'POST') {
        return entregas.shift();
      }
      return respuesta(
        200,
        catalogo([
          { id: 'p-hacha', nombre: 'Hacha de Obsidiana', estado: 'ACTIVO' },
          { id: 'p-daga', nombre: 'Daga', estado: 'ACTIVO' },
        ]),
      );
    });
    const alEntregar = jest.fn();
    const control = abrirEntregaAdministrativa({
      ficha: { id: 15, apodo: 'Ana', uid: 'u-1' },
      alEntregar,
      abrirPanel,
      buscar,
    });
    const dialogo = document.querySelector('[role="dialog"]');
    return { control, dialogo, buscar, alEntregar, cerrar, abrirPanel };
  }

  async function llenar(dialogo, { cantidad = '2', motivo = 'Compensación por la caída' } = {}) {
    const tipo = dialogo.querySelector('select[name="tipo"]');
    tipo.value = 'ARMA';
    tipo.dispatchEvent(new Event('change'));
    await asentar();
    const producto = dialogo.querySelector('select[name="producto"]');
    producto.value = 'p-hacha';
    producto.dispatchEvent(new Event('change'));
    const unidades = dialogo.querySelector('input[name="cantidad"]');
    unidades.value = cantidad;
    unidades.dispatchEvent(new Event('input'));
    dialogo.querySelector('textarea[name="motivo"]').value = motivo;
  }

  test('elige del catálogo, dice lo que va a pasar y entrega; luego estrena clave', async () => {
    const { control, dialogo, buscar, alEntregar, cerrar, abrirPanel } = montarPanel({
      respuestasDeEntrega: [respuesta(201, { entregaId: 'e-1', cantidad: 2, nueva: true })],
    });
    expect(abrirPanel.mock.calls[0][0].titulo).toBe('Entregar productos · Ana');
    expect(dialogo.querySelector('select[name="producto"]').disabled).toBe(true);

    await llenar(dialogo);
    const opciones = [...dialogo.querySelectorAll('select[name="producto"] option')].map(
      (o) => o.textContent,
    );
    expect(opciones).toEqual(['Elige el producto', 'Daga', 'Hacha de Obsidiana']);
    expect(dialogo.querySelector('[data-zona="resumen-de-la-entrega"]').textContent).toBe(
      'Se entregarán 2 × Hacha de Obsidiana a Ana. Queda en la auditoría con tu motivo.',
    );
    const claveUsada = control.clave.actual();

    dialogo.querySelector('[data-accion="confirmar-entrega"]').click();
    await asentar();

    const post = buscar.mock.calls.find(([, o]) => o?.method === 'POST');
    expect(post[1].headers['Idempotency-Key']).toBe(claveUsada);
    expect(JSON.parse(post[1].body)).toEqual({
      productoId: 'p-hacha',
      cantidad: 2,
      motivo: 'Compensación por la caída',
    });
    expect(cerrar).toHaveBeenCalled();
    expect(alEntregar).toHaveBeenCalledWith(
      expect.objectContaining({ entregaId: 'e-1', nombre: 'Hacha de Obsidiana' }),
    );
    expect(control.clave.actual()).not.toBe(claveUsada);
  });

  test('sin datos completos no se envía nada y cada campo dice lo suyo', async () => {
    const { dialogo, buscar } = montarPanel({ respuestasDeEntrega: [] });

    dialogo.querySelector('[data-accion="confirmar-entrega"]').click();
    await asentar();

    expect(buscar.mock.calls.some(([, o]) => o?.method === 'POST')).toBe(false);
    expect(dialogo.textContent).toContain('Elige el producto.');
    expect(dialogo.textContent).toContain('Escribe por qué se entrega');
  });

  test('si la bitácora no respondió, repetir usa la MISMA clave y no entrega dos veces', async () => {
    const errores = 'https://nexusbattles.upb.edu.co/errors/';
    const { control, dialogo, buscar, alEntregar } = montarPanel({
      respuestasDeEntrega: [
        respuesta(503, { type: `${errores}auditoria-no-disponible`, status: 503 }),
        respuesta(200, { entregaId: 'e-1', cantidad: 2, nueva: false }),
      ],
    });
    await llenar(dialogo);
    const claveUsada = control.clave.actual();

    dialogo.querySelector('[data-accion="confirmar-entrega"]').click();
    await asentar();
    const error = dialogo.querySelector('[data-zona="error-de-la-entrega"]');
    expect(error.hidden).toBe(false);
    expect(error.textContent).toContain('no pudo quedar en la auditoría');
    expect(alEntregar).not.toHaveBeenCalled();

    dialogo.querySelector('[data-accion="confirmar-entrega"]').click();
    await asentar();
    const posts = buscar.mock.calls.filter(([, o]) => o?.method === 'POST');
    expect(posts.map(([, o]) => o.headers['Idempotency-Key'])).toEqual([claveUsada, claveUsada]);
    expect(alEntregar).toHaveBeenCalledWith(expect.objectContaining({ nueva: false }));
  });

  test('un rechazo que no se arregla repitiendo estrena clave y se lee en palabras', async () => {
    const { control, dialogo } = montarPanel({
      respuestasDeEntrega: [respuesta(409, { detail: 'El producto esta suspendido.' })],
    });
    await llenar(dialogo);
    const claveUsada = control.clave.actual();

    dialogo.querySelector('[data-accion="confirmar-entrega"]').click();
    await asentar();

    expect(dialogo.querySelector('[data-zona="error-de-la-entrega"]').textContent).toBe(
      'El producto esta suspendido.',
    );
    expect(control.clave.actual()).not.toBe(claveUsada);
  });
});
