/**
 * B5 — el pago (§7.5: resumen, formulario y confirmación contra la pasarela
 * simulada) y «Mis compras».
 *
 * Lo que se fija, sobre todo: el número y el código no se guardan ni se
 * registran en ningún sitio; un reintento tras un 503 lleva la MISMA clave de
 * idempotencia, y un intento terminado, otra.
 */

import { jest } from '@jest/globals';

import {
  abrirMisCompras,
  abrirPago,
  claveDelIntento,
  enviarPago,
  enviarPagoConCreditos,
  interpretarPago,
  interpretarPagoConCreditos,
  normalizarVencimiento,
  nuevaClaveDePago,
  olvidarIntentoDePago,
  panelDeCreditos,
  pasaLuhn,
  pedirCotizacionEnCreditos,
  tarjetaDeOrden,
  validarPago,
} from './tienda-pago.js';

const NUMERO = '4242 4242 4242 4242';
const CODIGO = '737';
const ORDEN = '8f14e45f-ceea-467a-a8a1-6a6d1e1b0c3d';

const CARRITO = {
  id: 1,
  moneda: 'COP',
  total: 108000,
  items: [
    {
      id: 7,
      cantidad: 2,
      precioUnitario: 45000,
      subtotal: 90000,
      disponible: true,
      producto: { id: 'p-1', nombre: 'Espada de Vorn', moneda: 'COP' },
    },
    {
      id: 8,
      cantidad: 1,
      precioUnitario: 18000,
      subtotal: 18000,
      disponible: true,
      producto: { id: 'p-2', nombre: 'Yelmo del Alba', moneda: 'COP' },
    },
  ],
};

function orden(cambios = {}) {
  return {
    id: ORDEN,
    estado: 'COMPLETA',
    moneda: 'COP',
    total: 108000,
    lineas: [{ productoId: 'p-1', nombre: 'Espada de Vorn', cantidad: 2, subtotal: 90000 }],
    medioDePago: { marca: 'VISA', ultimos4: '4242' },
    correoConfirmacion: 'ENVIADO',
    creadaEn: '2026-09-25T15:30:00Z',
    ...cambios,
  };
}

function respuesta(status, cuerpo) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => cuerpo,
    headers: { get: () => 'application/json' },
  };
}

function problema(status, tipo, extra = {}) {
  return respuesta(status, { type: `urn:nexus:problema:${tipo}`, title: 'x', status, ...extra });
}

const esperar = async () => {
  for (let i = 0; i < 4; i += 1) {
    await new Promise((r) => setTimeout(r, 0));
  }
};

beforeEach(() => {
  document.body.innerHTML = '<button id="btn-pagar">Pagar</button>';
  olvidarIntentoDePago(document);
  sessionStorage.clear();
  localStorage.clear();
});

describe('validación del formulario (las reglas del servidor, dichas antes)', () => {
  const buenos = { titular: 'Ana Pérez', numero: NUMERO, vencimiento: '12/99', codigo: CODIGO };

  test('Luhn', () => {
    expect(pasaLuhn('4242424242424242')).toBe(true);
    expect(pasaLuhn('4000000000000002')).toBe(true);
    expect(pasaLuhn('4000000000000069')).toBe(true);
    expect(pasaLuhn('4242424242424241')).toBe(false);
    expect(pasaLuhn('')).toBe(false);
  });

  test('una tarjeta buena no tiene errores', () => {
    expect(validarPago(buenos)).toEqual([]);
  });

  test.each([
    [{ titular: 'Al' }, 'titular'],
    [{ numero: '4242 4242' }, 'numero'],
    [{ numero: '4242-4242-4242-4242' }, 'numero'],
    [{ numero: '4242 4242 4242 4241' }, 'numero'],
    [{ vencimiento: '13/30' }, 'vencimiento'],
    // Sin barra se entiende («1230» es 12/30); con tres cifras, no.
    [{ vencimiento: '123' }, 'vencimiento'],
    [{ vencimiento: '01/20' }, 'vencimiento'],
    [{ codigo: '12' }, 'codigo'],
    [{ codigo: '12a' }, 'codigo'],
  ])('%j marca %s', (cambio, campo) => {
    const errores = validarPago({ ...buenos, ...cambio });
    expect(errores.map((e) => e.campo)).toEqual([campo]);
  });

  test('el vencimiento se entiende sin barra: el teclado numérico del teléfono no la tiene', () => {
    expect(normalizarVencimiento('1229')).toBe('12/29');
    expect(normalizarVencimiento(' 12 / 29 ')).toBe('12/29');
    expect(normalizarVencimiento('12-29')).toBe('12/29');
    expect(normalizarVencimiento('1/29')).toBe('01/29');
    // Lo que no se entiende se deja, y la validación dice qué falla.
    expect(normalizarVencimiento('129')).toBe('129');
    expect(normalizarVencimiento(undefined)).toBe('');
    expect(validarPago({ ...buenos, vencimiento: '1299' })).toEqual([]);
    expect(validarPago({ ...buenos, vencimiento: '1399' })[0].campo).toBe('vencimiento');
  });

  test('el mes en curso todavía vale', () => {
    const ahora = new Date(2026, 8, 25);
    expect(validarPago({ ...buenos, vencimiento: '09/26' }, ahora)).toEqual([]);
    expect(validarPago({ ...buenos, vencimiento: '08/26' }, ahora)[0].mensaje).toBe(
      'La tarjeta está vencida.',
    );
  });

  test('ningún mensaje repite lo que se escribió', () => {
    const errores = validarPago({
      titular: 'X',
      numero: '4242 4242 4242 4241',
      vencimiento: '01/20',
      codigo: '99999',
    });
    const texto = errores.map((e) => e.mensaje).join(' ');
    expect(texto).not.toContain('4241');
    expect(texto).not.toContain('99999');
  });
});

describe('la clave de idempotencia del intento', () => {
  test('tiene la forma del contrato (8..100) y cambia en cada intento nuevo', () => {
    const una = nuevaClaveDePago();
    expect(una).toMatch(/^pago-/);
    expect(una.length).toBeGreaterThanOrEqual(8);
    expect(una.length).toBeLessThanOrEqual(100);
    expect(nuevaClaveDePago()).not.toBe(una);
  });

  test('la misma mientras dura el intento; otra al olvidarlo', () => {
    const primera = claveDelIntento(document);
    expect(claveDelIntento(document)).toBe(primera);

    olvidarIntentoDePago(document);

    expect(claveDelIntento(document)).not.toBe(primera);
  });
});

describe('enviarPago', () => {
  test('POST /checkout con la clave en Idempotency-Key y la tarjeta en el cuerpo', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(201, orden()));
    const solicitud = {
      titular: 'Ana',
      numeroTarjeta: NUMERO,
      vencimiento: '12/99',
      codigoSeguridad: CODIGO,
      moneda: 'COP',
    };

    const r = await enviarPago({ solicitud, clave: 'pago-clave-1', fetchImpl });

    expect(r).toEqual({ estado: 201, orden: orden(), problema: null });
    const [url, opciones] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/checkout');
    expect(opciones.method).toBe('POST');
    expect(opciones.headers['Idempotency-Key']).toBe('pago-clave-1');
    expect(JSON.parse(opciones.body)).toEqual(solicitud);
    // La clave no viaja en la dirección, y la tarjeta tampoco.
    expect(url).not.toContain('4242');
  });

  test('un rechazo trae su problem detail; sin red, estado 0', async () => {
    const rechazo = jest.fn().mockResolvedValue(problema(402, 'pago-rechazado'));
    expect((await enviarPago({ solicitud: {}, clave: 'k', fetchImpl: rechazo })).estado).toBe(402);

    const sinRed = jest.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    expect(await enviarPago({ solicitud: {}, clave: 'k', fetchImpl: sinRed })).toEqual({
      estado: 0,
      orden: null,
      problema: null,
    });
  });
});

describe('interpretarPago: lo que significa cada respuesta', () => {
  test.each([
    [{ estado: 201, orden: orden() }, 'completa', false],
    [{ estado: 201, orden: orden({ estado: 'COBRADA' }) }, 'en-curso', false],
    [{ estado: 200, orden: orden({ estado: 'ENTREGADA' }) }, 'en-curso', false],
    [{ estado: 200, orden: orden({ estado: 'RECHAZADA', motivo: 'Fondos' }) }, 'rechazada', false],
    [{ estado: 200, orden: orden({ estado: 'REEMBOLSADA' }) }, 'reembolsada', false],
    // Aún sin cobrar: nunca un «pago recibido»; se reintenta con la misma clave.
    [{ estado: 200, orden: orden({ estado: 'PENDIENTE' }) }, 'reintentar', true],
    [{ estado: 0 }, 'reintentar', true],
    [{ estado: 401 }, 'sesion', false],
  ])('%j → %s (conservar clave: %s)', (respuestaDelServidor, tipo, conservarClave) => {
    const r = interpretarPago({ orden: null, problema: null, ...respuestaDelServidor });
    expect(r.tipo).toBe(tipo);
    expect(r.conservarClave).toBe(conservarClave);
  });

  test.each([
    [402, 'pago-rechazado', 'rechazada', false],
    [409, 'compra-reembolsada', 'reembolsada', false],
    [503, 'pasarela-no-disponible', 'reintentar', true],
    [409, 'compra-en-curso', 'reintentar', true],
    [409, 'clave-de-idempotencia-reutilizada', 'reintentar', false],
    [400, 'datos-de-pago-invalidos', 'corregir', true],
    [422, 'moneda-no-disponible', 'moneda', false],
    [409, 'producto-agotado', 'carrito', true],
    [409, 'tiraje-insuficiente', 'carrito', true],
    [400, 'carrito-vacio', 'carrito', true],
    [503, 'compra-no-disponible', 'no-disponible', true],
    [503, 'catalogo-no-disponible', 'no-disponible', true],
  ])('%s %s → %s (conservar clave: %s)', (estado, tipoDelProblema, tipo, conservarClave) => {
    const r = interpretarPago({
      estado,
      orden: null,
      problema: { type: `urn:nexus:problema:${tipoDelProblema}`, status: estado },
    });
    expect(r.tipo).toBe(tipo);
    expect(r.conservarClave).toBe(conservarClave);
  });

  test('el rechazo dice el motivo de la pasarela; el campo inválido se traduce al formulario', () => {
    expect(
      interpretarPago({
        estado: 402,
        orden: null,
        problema: {
          type: 'urn:nexus:problema:pago-rechazado',
          motivo: 'La pasarela rechazó el pago: fondos insuficientes.',
        },
      }).detalle,
    ).toMatch(/^La pasarela rechazó el pago: fondos insuficientes\. Tu carrito sigue guardado/);
    expect(
      interpretarPago({
        estado: 400,
        orden: null,
        problema: { type: 'urn:nexus:problema:datos-de-pago-invalidos', campo: 'codigoSeguridad' },
      }).campo,
    ).toBe('codigo');
  });

  test('la moneda sin tasa dice cuál y trae las disponibles', () => {
    const r = interpretarPago(
      {
        estado: 422,
        orden: null,
        problema: {
          type: 'urn:nexus:problema:moneda-no-disponible',
          monedasDisponibles: ['COP'],
        },
      },
      { moneda: 'USD' },
    );
    expect(r.titulo).toBe('Los pagos en USD no están disponibles');
    expect(r.monedasDisponibles).toEqual(['COP']);
  });

  test('una compra completa dice si se envió el correo', () => {
    expect(interpretarPago({ estado: 201, orden: orden() }).detalle).toMatch(/correo/);
    expect(
      interpretarPago({ estado: 201, orden: orden({ correoConfirmacion: 'OMITIDO' }) }).detalle,
    ).not.toMatch(/correo/);
  });
});

describe('el diálogo de pago', () => {
  const campo = (nombre) => document.querySelector(`.pago__formulario input[name="${nombre}"]`);
  const confirmar = () => document.querySelector('[data-accion="confirmar-pago"]');

  function rellenar({
    titular = 'Ana Pérez',
    numero = NUMERO,
    vencimiento = '12/99',
    codigo = CODIGO,
  } = {}) {
    campo('titular').value = titular;
    campo('numero').value = numero;
    campo('vencimiento').value = vencimiento;
    campo('codigo').value = codigo;
  }

  function abrir(fetchImpl, extra = {}) {
    document.getElementById('btn-pagar').focus();
    return abrirPago({ doc: document, carrito: CARRITO, moneda: 'COP', fetchImpl, ...extra });
  }

  test('el resumen: cada producto con su cantidad y subtotal, y el total a pagar', () => {
    abrir(jest.fn());

    const dialogo = document.querySelector('[role="dialog"]');
    expect(dialogo.textContent).toContain('Pagar tu compra');
    const lineas = Array.from(dialogo.querySelectorAll('.pago__linea')).map((l) => l.textContent);
    expect(lineas).toEqual(['Espada de Vorn × 290.000 COP', 'Yelmo del Alba × 118.000 COP']);
    expect(dialogo.querySelector('.pago__total').textContent).toBe('Total a pagar108.000 COP');
    expect(confirmar().textContent).toBe('Confirmar pago de 108.000 COP');
  });

  test('los cuatro campos de §7.5, con su etiqueta y su autocomplete', () => {
    abrir(jest.fn());

    const esperados = {
      titular: ['Nombre del titular de la tarjeta', 'cc-name'],
      numero: ['Número de tarjeta', 'cc-number'],
      vencimiento: ['Fecha de vencimiento (MM/AA)', 'cc-exp'],
      codigo: ['Código de seguridad', 'cc-csc'],
    };
    for (const [nombre, [etiqueta, autocompletar]] of Object.entries(esperados)) {
      const control = campo(nombre);
      expect(document.querySelector(`label[for="${control.id}"]`).textContent).toBe(etiqueta);
      expect(control.getAttribute('autocomplete')).toBe(autocompletar);
      expect(control.getAttribute('aria-describedby')).toContain(`${control.id}-error`);
    }
    // El foco entra al primer campo.
    expect(document.activeElement).toBe(campo('titular'));
  });

  test('con datos mal escritos no se envía nada: se marca el campo y se le lleva el foco', async () => {
    const fetchImpl = jest.fn();
    abrir(fetchImpl);
    rellenar({ numero: '4242 4242 4242 4241', codigo: '1' });

    confirmar().click();
    await esperar();

    expect(fetchImpl).not.toHaveBeenCalled();
    expect(campo('numero').getAttribute('aria-invalid')).toBe('true');
    expect(campo('codigo').getAttribute('aria-invalid')).toBe('true');
    expect(campo('titular').hasAttribute('aria-invalid')).toBe(false);
    const error = document.getElementById(`${campo('numero').id}-error`);
    expect(error.hidden).toBe(false);
    expect(error.textContent).toMatch(/no es válido/);
    expect(document.activeElement).toBe(campo('numero'));
  });

  test('aprobada: el resultado sustituye al formulario, la tienda recarga y el intento termina', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(201, orden()));
    const alTerminar = jest.fn();
    const alVerCompras = jest.fn();
    abrir(fetchImpl, { alTerminar, alVerCompras });
    rellenar();
    const clave = claveDelIntento(document);

    confirmar().click();
    await esperar();

    expect(fetchImpl.mock.calls[0][1].headers['Idempotency-Key']).toBe(clave);
    const resultado = document.querySelector('.pago__resultado');
    expect(resultado.dataset.estado).toBe('COMPLETA');
    expect(resultado.textContent).toContain('Compra completada');
    expect(document.querySelector('.pago__formulario')).toBeNull();
    expect(alTerminar).toHaveBeenCalledWith(orden());
    expect(claveDelIntento(document)).not.toBe(clave);

    document.querySelector('[data-accion="ver-mis-compras"]').click();
    expect(alVerCompras).toHaveBeenCalled();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });

  test('el vencimiento escrito sin barra se ve, y se envía, como MM/AA', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(respuesta(201, orden()));
    abrir(fetchImpl);
    rellenar({ vencimiento: '1299', codigo: '1' });

    confirmar().click();
    await esperar();

    // El código está mal: no sale nada, pero el vencimiento ya se ve bien escrito.
    expect(fetchImpl).not.toHaveBeenCalled();
    expect(campo('vencimiento').value).toBe('12/99');
    expect(campo('vencimiento').hasAttribute('aria-invalid')).toBe(false);

    campo('codigo').value = CODIGO;
    confirmar().click();
    await esperar();

    expect(JSON.parse(fetchImpl.mock.calls[0][1].body).vencimiento).toBe('12/99');
  });

  test('0069 — la pasarela no responde: se dice, y el reintento lleva la MISMA clave', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValueOnce(
        problema(503, 'pasarela-no-disponible', { ordenId: ORDEN, estado: 'PENDIENTE' }),
      )
      .mockResolvedValueOnce(respuesta(201, orden()));
    abrir(fetchImpl);
    rellenar();

    confirmar().click();
    await esperar();

    const alerta = document.querySelector('.pago__alerta');
    expect(alerta.hidden).toBe(false);
    expect(alerta.getAttribute('role')).toBe('alert');
    expect(alerta.textContent).toMatch(/No se cobró nada/);
    // El formulario sigue relleno: un clic reintenta.
    expect(campo('numero').value).toBe(NUMERO);
    expect(document.activeElement).toBe(confirmar());

    confirmar().click();
    await esperar();

    const [primera, segunda] = fetchImpl.mock.calls.map(([, o]) => o.headers['Idempotency-Key']);
    expect(segunda).toBe(primera);
    expect(document.querySelector('.pago__resultado').dataset.estado).toBe('COMPLETA');
  });

  test('409 compra-en-curso: se dice que espere, y el reintento pregunta por el MISMO pago', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValueOnce(
        problema(409, 'compra-en-curso', { ordenId: ORDEN, estado: 'PENDIENTE' }),
      )
      .mockResolvedValueOnce(respuesta(200, orden()));
    abrir(fetchImpl);
    rellenar();

    confirmar().click();
    await esperar();

    const alerta = document.querySelector('.pago__alerta');
    expect(alerta.textContent).toContain('Tu pago se está procesando');
    expect(alerta.textContent).toMatch(/no se cobrará dos veces/);
    expect(document.activeElement).toBe(confirmar());

    confirmar().click();
    await esperar();

    const [primera, segunda] = fetchImpl.mock.calls.map(([, o]) => o.headers['Idempotency-Key']);
    expect(segunda).toBe(primera);
    // 200 con la orden de esa clave: la compra que ya estaba en marcha.
    expect(document.querySelector('.pago__resultado').dataset.estado).toBe('COMPLETA');
  });

  test('0002 — rechazada: el motivo, el número y el código vaciados, y otra clave para otra tarjeta', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValueOnce(
        problema(402, 'pago-rechazado', {
          ordenId: ORDEN,
          estado: 'RECHAZADA',
          motivo: 'La pasarela rechazó el pago: fondos insuficientes.',
        }),
      )
      .mockResolvedValueOnce(respuesta(201, orden()));
    const alTerminar = jest.fn();
    abrir(fetchImpl, { alTerminar });
    rellenar({ numero: '4000 0000 0000 0002' });

    confirmar().click();
    await esperar();

    const alerta = document.querySelector('.pago__alerta');
    expect(alerta.textContent).toContain('Pago rechazado');
    expect(alerta.textContent).toContain('fondos insuficientes');
    expect(campo('numero').value).toBe('');
    expect(campo('codigo').value).toBe('');
    expect(campo('titular').value).toBe('Ana Pérez');
    expect(document.activeElement).toBe(campo('numero'));
    expect(alTerminar).not.toHaveBeenCalled();

    rellenar();
    confirmar().click();
    await esperar();

    const [primera, segunda] = fetchImpl.mock.calls.map(([, o]) => o.headers['Idempotency-Key']);
    expect(segunda).not.toBe(primera);
  });

  test('reembolsada tras cobrar: se dice el motivo y la tienda recarga el carrito', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      problema(409, 'compra-reembolsada', {
        ordenId: ORDEN,
        estado: 'REEMBOLSADA',
        motivo: '«Espada de Vorn» se agotó.',
      }),
    );
    const alTerminar = jest.fn();
    abrir(fetchImpl, { alTerminar });
    rellenar();

    confirmar().click();
    await esperar();

    const resultado = document.querySelector('.pago__resultado');
    expect(resultado.dataset.resultado).toBe('reembolsada');
    expect(resultado.textContent).toContain('«Espada de Vorn» se agotó.');
    expect(alTerminar).toHaveBeenCalled();
  });

  test('el servidor marca un campo: se marca ese campo', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      problema(400, 'datos-de-pago-invalidos', {
        campo: 'vencimiento',
        detail: 'La tarjeta está vencida.',
      }),
    );
    abrir(fetchImpl);
    rellenar();

    confirmar().click();
    await esperar();

    expect(campo('vencimiento').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(campo('vencimiento'));
  });

  test('el carrito cambió antes de cobrar: se dice y se vuelve al carrito', async () => {
    const fetchImpl = jest.fn().mockResolvedValue(
      problema(409, 'producto-agotado', {
        detail: '«Espada de Vorn» se agotó. Quítalo del carrito para pagar.',
      }),
    );
    const alCambiarCarrito = jest.fn();
    abrir(fetchImpl, { alCambiarCarrito });
    rellenar();

    confirmar().click();
    await esperar();

    expect(document.querySelector('.pago__alerta').textContent).toContain('Quítalo del carrito');
    document.querySelector('[data-accion="volver-al-carrito"]').click();
    expect(alCambiarCarrito).toHaveBeenCalled();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });

  test('la moneda dejó de estar: la tienda vuelve a las disponibles', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValue(problema(422, 'moneda-no-disponible', { monedasDisponibles: ['COP'] }));
    const alCambiarMoneda = jest.fn();
    abrirPago({ doc: document, carrito: CARRITO, moneda: 'USD', fetchImpl, alCambiarMoneda });
    rellenar();

    confirmar().click();
    await esperar();
    document.querySelector('[data-accion="volver-a-la-tienda"]').click();

    expect(JSON.parse(fetchImpl.mock.calls[0][1].body).moneda).toBe('USD');
    expect(alCambiarMoneda).toHaveBeenCalledWith(['COP']);
  });

  test('el número y el código no se guardan ni se escriben en la consola', async () => {
    const guardar = jest.spyOn(Storage.prototype, 'setItem');
    const consola = ['log', 'info', 'warn', 'error', 'debug'].map((metodo) =>
      jest.spyOn(console, metodo).mockImplementation(() => {}),
    );
    try {
      const fetchImpl = jest
        .fn()
        .mockResolvedValueOnce(problema(503, 'pasarela-no-disponible'))
        .mockResolvedValueOnce(problema(402, 'pago-rechazado', { motivo: 'No.' }));
      abrir(fetchImpl);
      rellenar();

      confirmar().click();
      await esperar();
      confirmar().click();
      await esperar();

      const guardado = JSON.stringify(guardar.mock.calls);
      expect(guardado).not.toContain('4242');
      expect(guardado).not.toContain(CODIGO);
      for (const espia of consola) {
        const escrito = JSON.stringify(espia.mock.calls);
        expect(escrito).not.toContain('4242');
        expect(escrito).not.toContain(CODIGO);
      }
      expect(JSON.stringify({ ...sessionStorage })).not.toContain('4242');
      expect(JSON.stringify({ ...localStorage })).not.toContain('4242');
    } finally {
      guardar.mockRestore();
      consola.forEach((espia) => espia.mockRestore());
    }
  });

  test('«Cancelar» cierra y el foco vuelve a «Pagar»', () => {
    abrir(jest.fn());

    document.querySelector('[data-accion="cancelar-pago"]').click();

    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(document.getElementById('btn-pagar'));
  });

  test('mientras se procesa, el botón se apaga y no se envía dos veces', async () => {
    let soltar;
    const fetchImpl = jest.fn(
      () =>
        new Promise((resolver) => {
          soltar = () => resolver(respuesta(201, orden()));
        }),
    );
    abrir(fetchImpl);
    rellenar();

    confirmar().click();
    await Promise.resolve();
    expect(confirmar().disabled).toBe(true);
    expect(confirmar().getAttribute('aria-busy')).toBe('true');
    expect(document.querySelector('.pago__estado').textContent).toBe('Procesando el pago…');
    document
      .querySelector('.pago__formulario')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    soltar();
    await esperar();

    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });
});

describe('Mis compras', () => {
  test('cada compra: fecha, estado, líneas, total y la tarjeta (marca y cuatro últimos)', () => {
    const tarjeta = tarjetaDeOrden(orden());

    expect(tarjeta.querySelector('.compra__estado').textContent).toBe('Completada');
    expect(tarjeta.querySelector('.compra__estado').classList).toContain('distintivo--completada');
    expect(tarjeta.querySelector('.compra__lineas').textContent).toBe(
      'Espada de Vorn × 2 · 90.000 COP',
    );
    expect(tarjeta.querySelector('.compra__total').textContent).toBe('Total: 108.000 COP');
    expect(tarjeta.querySelector('.compra__medio').textContent).toBe('Pagada con VISA ···· 4242');
    expect(tarjeta.querySelector('.compra__motivo')).toBeNull();
  });

  test('una rechazada o reembolsada dice por qué', () => {
    const tarjeta = tarjetaDeOrden(orden({ estado: 'REEMBOLSADA', motivo: 'Se agotó.' }));

    expect(tarjeta.querySelector('.compra__estado').textContent).toBe('Reembolsada');
    expect(tarjeta.querySelector('.compra__motivo').textContent).toBe('Se agotó.');
  });

  test('el diálogo lista las compras de GET /ordenes', async () => {
    const fetchImpl = jest
      .fn()
      .mockResolvedValue(respuesta(200, [orden(), orden({ id: 'o-2', estado: 'RECHAZADA' })]));

    await abrirMisCompras({ fetchImpl });

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/ordenes');
    const dialogo = document.querySelector('[role="dialog"]');
    expect(dialogo.textContent).toContain('Mis compras');
    expect(dialogo.querySelectorAll('.compra')).toHaveLength(2);
    expect(dialogo.querySelector('.compras').getAttribute('aria-busy')).toBe('false');
  });

  test('sin compras se dice; si falla, se ofrece reintentar', async () => {
    const vacio = jest.fn().mockResolvedValue(respuesta(200, []));
    await abrirMisCompras({ fetchImpl: vacio });
    expect(document.querySelector('.compras').textContent).toContain(
      'Todavía no has comprado nada',
    );
    document.querySelector('.dialogo__cerrar').click();

    const roto = jest
      .fn()
      .mockResolvedValueOnce(respuesta(500, {}))
      .mockResolvedValueOnce(respuesta(200, [orden()]));
    await abrirMisCompras({ fetchImpl: roto });
    expect(document.querySelector('.compras').textContent).toContain('No se pudieron cargar');

    document.querySelector('[data-accion="reintentar-compras"]').click();
    await esperar();
    expect(document.querySelectorAll('.compra')).toHaveLength(1);
  });
});

describe('D-44 — pagar con créditos del Nexo', () => {
  const COTIZACION = {
    pagable: true,
    motivo: null,
    lineas: [
      {
        productoId: 'p-1',
        nombre: 'Espada de Vorn',
        cantidad: 2,
        disponible: true,
        precioCreditos: 300,
        subtotalCreditos: 600,
        porcentajeDescuento: null,
      },
      {
        productoId: 'p-2',
        nombre: 'Yelmo del Alba',
        cantidad: 1,
        disponible: true,
        precioCreditos: 250,
        subtotalCreditos: 250,
        porcentajeDescuento: null,
      },
    ],
    totalCreditos: 850,
    saldoDisponible: 1000,
    saldoDespues: 150,
    alcanza: true,
  };

  const ordenEnCreditos = (cambios = {}) =>
    orden({
      moneda: 'CREDITOS',
      formaDePago: 'CREDITOS',
      total: 850,
      medioDePago: null,
      correoConfirmacion: 'OMITIDO',
      lineas: [{ productoId: 'p-1', nombre: 'Espada de Vorn', cantidad: 2, subtotal: 600 }],
      ...cambios,
    });

  const confirmar = () => document.querySelector('[data-accion="confirmar-pago"]');
  const radio = (valor) => document.querySelector(`input[name="forma-de-pago"][value="${valor}"]`);
  const posts = (fetchImpl) => fetchImpl.mock.calls.filter(([, o]) => o?.method === 'POST');
  const gets = (fetchImpl) => fetchImpl.mock.calls.filter(([, o]) => o?.method === 'GET');

  async function elegir(valor) {
    radio(valor).checked = true;
    radio(valor).dispatchEvent(new Event('change'));
    await esperar();
  }

  /** El servidor: la cotización por GET y la compra por POST. */
  function servidor({ cotizacion = COTIZACION, pago = respuesta(201, ordenEnCreditos()) } = {}) {
    return jest.fn(async (url, opciones = {}) => {
      if (!String(url).endsWith('/checkout/creditos')) {
        throw new Error(`petición inesperada: ${url}`);
      }
      if (opciones.method === 'POST') {
        return typeof pago === 'function' ? pago() : pago;
      }
      return typeof cotizacion === 'function' ? cotizacion() : respuesta(200, cotizacion);
    });
  }

  function abrir(fetchImpl, extra = {}) {
    document.getElementById('btn-pagar').focus();
    return abrirPago({ doc: document, carrito: CARRITO, moneda: 'COP', fetchImpl, ...extra });
  }

  test('«¿Cómo quieres pagar?»: Créditos del Nexo o Pago simulado; el simulado viene marcado', () => {
    abrir(jest.fn());

    const grupo = document.querySelector('.pago__formas');
    expect(grupo.querySelector('legend').textContent).toBe('¿Cómo quieres pagar?');
    expect(
      Array.from(grupo.querySelectorAll('.pago__forma-nombre')).map((n) => n.textContent),
    ).toEqual(['Créditos del Nexo', 'Pago simulado']);
    expect(radio('TARJETA').checked).toBe(true);
    expect(radio('CREDITOS').checked).toBe(false);
    expect(document.querySelector('[data-zona="creditos"]').hidden).toBe(true);
    expect(document.querySelector('[data-zona="tarjeta"]').hidden).toBe(false);
  });

  test('con créditos: saldo actual, precio y saldo después tal como los da el servidor', async () => {
    const fetchImpl = servidor();
    abrir(fetchImpl);

    await elegir('CREDITOS');

    expect(gets(fetchImpl)).toHaveLength(1);
    expect(gets(fetchImpl)[0][0]).toBe('/api/v1/checkout/creditos');
    const zona = document.querySelector('[data-zona="creditos"]');
    expect(zona.hidden).toBe(false);
    expect(document.querySelector('[data-zona="tarjeta"]').hidden).toBe(true);
    // El resumen en dinero real no se enseña: dos precios para la misma compra confunden.
    expect(document.querySelector('.pago__resumen:not(.pago__resumen--creditos)').hidden).toBe(
      true,
    );
    expect(zona.querySelector('[data-zona="saldo-actual"] dd').textContent).toBe('1.000 créditos');
    expect(zona.querySelector('[data-zona="precio"] dd').textContent).toBe('850 créditos');
    expect(zona.querySelector('[data-zona="saldo-despues"] dd').textContent).toBe('150 créditos');
    expect(Array.from(zona.querySelectorAll('.pago__linea')).map((l) => l.textContent)).toEqual([
      'Espada de Vorn × 2600 créditos',
      'Yelmo del Alba × 1250 créditos',
    ]);
    expect(confirmar().disabled).toBe(false);
    expect(confirmar().textContent).toBe('Confirmar compra por 850 créditos');
  });

  test('confirmar: POST /checkout/creditos sin cuerpo y con su clave; «Compra realizada» y «Ver inventario»', async () => {
    const alTerminar = jest.fn();
    const fetchImpl = servidor();
    abrir(fetchImpl, { alTerminar });
    await elegir('CREDITOS');
    const clave = claveDelIntento(document);

    confirmar().click();
    await esperar();

    expect(posts(fetchImpl)).toHaveLength(1);
    const [url, peticion] = posts(fetchImpl)[0];
    expect(url).toBe('/api/v1/checkout/creditos');
    expect(peticion.headers['Idempotency-Key']).toBe(clave);
    expect(peticion.body).toBeUndefined();
    const resultado = document.querySelector('.pago__resultado');
    expect(resultado.dataset.estado).toBe('COMPLETA');
    expect(resultado.textContent).toContain('Compra realizada');
    expect(resultado.textContent).toContain('Pagaste 850 créditos.');
    const verInventario = document.querySelector('[data-accion="ver-inventario"]');
    expect(verInventario.tagName).toBe('A');
    expect(verInventario.getAttribute('href')).toMatch(/inventario/);
    expect(alTerminar).toHaveBeenCalledWith(ordenEnCreditos());
    expect(claveDelIntento(document)).not.toBe(clave);
  });

  test('doble clic: mientras se procesa no sale un segundo cobro', async () => {
    let terminarPago;
    const fetchImpl = servidor({
      pago: () =>
        new Promise((r) => {
          terminarPago = r;
        }),
    });
    abrir(fetchImpl);
    await elegir('CREDITOS');

    confirmar().click();
    confirmar().click();
    await esperar();

    expect(posts(fetchImpl)).toHaveLength(1);
    expect(confirmar().disabled).toBe(true);
    terminarPago(respuesta(201, ordenEnCreditos()));
    await esperar();
    expect(document.querySelector('.pago__resultado').textContent).toContain('Compra realizada');
  });

  test('si no alcanza: cuánto falta, y «Confirmar» apagado', async () => {
    const fetchImpl = servidor({
      cotizacion: { ...COTIZACION, saldoDisponible: 700, saldoDespues: -150, alcanza: false },
    });
    abrir(fetchImpl);

    await elegir('CREDITOS');
    confirmar().click();
    await esperar();

    expect(document.querySelector('[data-aviso="creditos"]').textContent).toBe(
      'No te alcanza: te faltan 150 créditos.',
    );
    expect(confirmar().disabled).toBe(true);
    expect(confirmar().textContent).toBe('Confirmar compra');
    expect(posts(fetchImpl)).toHaveLength(0);
  });

  test('algo del carrito no se vende con créditos: se dice cuál camino queda y no se confirma', async () => {
    const fetchImpl = servidor({
      cotizacion: {
        ...COTIZACION,
        pagable: false,
        motivo: 'SIN_PRECIO_EN_CREDITOS',
        totalCreditos: null,
        saldoDespues: null,
        alcanza: null,
        lineas: [{ ...COTIZACION.lineas[0], precioCreditos: null, subtotalCreditos: null }],
      },
    });
    abrir(fetchImpl);

    await elegir('CREDITOS');

    expect(document.querySelector('[data-aviso="creditos"]').textContent).toMatch(
      /no se vende con créditos/,
    );
    expect(document.querySelector('[data-zona="creditos"] .pago__linea').textContent).toContain(
      'No se vende con créditos',
    );
    expect(confirmar().disabled).toBe(true);
  });

  test('sin el saldo de ms-finanzas: se dice que no se sabe y se puede confirmar (el servidor decide)', async () => {
    const fetchImpl = servidor({
      cotizacion: { ...COTIZACION, saldoDisponible: null, saldoDespues: null, alcanza: null },
    });
    abrir(fetchImpl);

    await elegir('CREDITOS');

    const zona = document.querySelector('[data-zona="creditos"]');
    expect(zona.querySelector('[data-zona="saldo-actual"] dd').textContent).toBe(
      'No disponible ahora',
    );
    expect(zona.querySelector('[data-zona="saldo-despues"] dd').textContent).toBe('—');
    expect(confirmar().disabled).toBe(false);
  });

  test('la cotización no llega: se ofrece reintentar y no se confirma a ciegas', async () => {
    const respuestas = [respuesta(503, {}), respuesta(200, COTIZACION)];
    const fetchImpl = servidor({ cotizacion: () => respuestas.shift() });
    abrir(fetchImpl);

    await elegir('CREDITOS');
    expect(confirmar().disabled).toBe(true);
    expect(document.querySelector('[data-zona="creditos"]').textContent).toMatch(
      /No pudimos calcular el precio en créditos/,
    );

    document.querySelector('[data-accion="reintentar-cotizacion"]').click();
    await esperar();
    expect(confirmar().disabled).toBe(false);
  });

  test('402 saldo-insuficiente: no se cobró, otra clave, y se vuelve a pedir el saldo', async () => {
    const fetchImpl = servidor({
      pago: () =>
        problema(402, 'saldo-insuficiente', {
          ordenId: ORDEN,
          estado: 'RECHAZADA',
          totalCreditos: 850,
        }),
    });
    abrir(fetchImpl);
    await elegir('CREDITOS');
    const clave = claveDelIntento(document);

    confirmar().click();
    await esperar();

    const alerta = document.querySelector('.pago__alerta');
    expect(alerta.hidden).toBe(false);
    expect(alerta.textContent).toContain('No tienes créditos suficientes');
    expect(alerta.textContent).toContain('No se cobró nada. La compra cuesta 850 créditos.');
    expect(claveDelIntento(document)).not.toBe(clave);
    expect(gets(fetchImpl)).toHaveLength(2);
  });

  test('503 creditos-no-disponibles: el reintento lleva la MISMA clave y cobra una vez', async () => {
    const respuestas = [
      problema(503, 'creditos-no-disponibles', { ordenId: ORDEN, estado: 'PENDIENTE' }),
      respuesta(201, ordenEnCreditos()),
    ];
    const fetchImpl = servidor({ pago: () => respuestas.shift() });
    abrir(fetchImpl);
    await elegir('CREDITOS');

    confirmar().click();
    await esperar();
    expect(document.querySelector('.pago__alerta').textContent).toContain(
      'No pudimos confirmar el cobro',
    );
    expect(document.activeElement).toBe(confirmar());

    confirmar().click();
    await esperar();

    const claves = posts(fetchImpl).map(([, o]) => o.headers['Idempotency-Key']);
    expect(claves).toHaveLength(2);
    expect(claves[1]).toBe(claves[0]);
    expect(document.querySelector('.pago__resultado').textContent).toContain('Compra realizada');
  });

  test('volver a «Pago simulado» enseña la tarjeta y empieza otro intento', async () => {
    const fetchImpl = servidor();
    abrir(fetchImpl);
    await elegir('CREDITOS');
    const conCreditos = claveDelIntento(document);

    await elegir('TARJETA');

    expect(document.querySelector('[data-zona="tarjeta"]').hidden).toBe(false);
    expect(document.querySelector('[data-zona="creditos"]').hidden).toBe(true);
    expect(document.querySelector('.pago__resumen').hidden).toBe(false);
    expect(confirmar().disabled).toBe(false);
    expect(confirmar().textContent).toBe('Confirmar pago de 108.000 COP');
    expect(claveDelIntento(document)).not.toBe(conCreditos);
  });

  test('el cliente HTTP: cotización por GET; compra por POST sin cuerpo; sin red, estado 0', async () => {
    const ok = jest.fn().mockResolvedValue(respuesta(200, COTIZACION));
    expect(await pedirCotizacionEnCreditos({ fetchImpl: ok })).toEqual({
      estado: 200,
      cotizacion: COTIZACION,
    });
    const caido = jest.fn().mockRejectedValue(new Error('sin red'));
    expect(await pedirCotizacionEnCreditos({ fetchImpl: caido })).toEqual({
      estado: 0,
      cotizacion: null,
    });
    expect(await enviarPagoConCreditos({ clave: 'pago-12345678', fetchImpl: caido })).toEqual({
      estado: 0,
      orden: null,
      problema: null,
    });
  });

  test('interpretarPagoConCreditos: cada respuesta dice lo que pasó con los créditos', () => {
    expect(
      interpretarPagoConCreditos({ estado: 201, orden: ordenEnCreditos(), problema: null }),
    ).toMatchObject({
      tipo: 'completa',
      titulo: 'Compra realizada',
    });
    expect(
      interpretarPagoConCreditos({
        estado: 409,
        orden: null,
        problema: { type: 'urn:nexus:problema:compra-reembolsada', motivo: 'Se agotó.' },
      }),
    ).toMatchObject({ tipo: 'reembolsada', detalle: 'Se agotó. Te devolvimos los créditos.' });
    expect(interpretarPagoConCreditos({ estado: 0, orden: null, problema: null })).toMatchObject({
      tipo: 'reintentar',
      conservarClave: true,
    });
    expect(
      interpretarPagoConCreditos({
        estado: 409,
        orden: null,
        problema: { type: 'urn:nexus:problema:producto-sin-precio-en-creditos', detail: '«X» no.' },
      }),
    ).toMatchObject({ tipo: 'carrito', detalle: '«X» no.' });
    expect(
      interpretarPagoConCreditos({
        estado: 409,
        orden: null,
        problema: { type: 'urn:nexus:problema:clave-de-idempotencia-reutilizada' },
      }),
    ).toMatchObject({ tipo: 'reintentar', conservarClave: false });
    // Lo común con la tarjeta lo dice interpretarPago, igual que siempre.
    expect(interpretarPagoConCreditos({ estado: 401, orden: null, problema: null }).tipo).toBe(
      'sesion',
    );
  });

  test('el panel no calcula: si el servidor no dio una cifra, se dice que no se sabe', () => {
    const panel = panelDeCreditos({ pagable: true, lineas: [], totalCreditos: null });
    expect(panel.querySelector('[data-zona="precio"] dd').textContent).toBe('—');
  });

  test('Mis compras: una compra con créditos dice el total en créditos y con qué se pagó', () => {
    const tarjeta = tarjetaDeOrden(ordenEnCreditos());

    expect(tarjeta.querySelector('.compra__total').textContent).toBe('Total: 850 créditos');
    expect(tarjeta.querySelector('.compra__medio').textContent).toBe(
      'Pagada con créditos del Nexo',
    );
    expect(tarjeta.textContent).toContain('Espada de Vorn × 2 · 600 créditos');
  });
});
