/**
 * Sanciones — HU-USR-004/005/006/007: presentación pura y montaje de las dos
 * vistas contra un servicio simulado.
 */

import { jest } from '@jest/globals';

import {
  ErrorDeSanciones,
  descripcionDe,
  montarMisSanciones,
  montarPanelDeModeracion,
  sePuedeApelar,
  solicitudDesde,
  tiempoRestante,
} from './sanciones.js';

const AHORA = Date.parse('2026-09-21T10:00:00Z');
const UID = '11111111-1111-1111-1111-111111111111';

const sancion = (extra = {}) => ({
  id: 's-1',
  usuarioId: UID,
  tipo: 'SUSPENSION',
  motivo: 'Reincidencia',
  politica: null,
  emitidaPor: '2222',
  rolEmisor: 'MODERADOR',
  emitidaEn: '2026-09-21T09:00:00Z',
  vigenteHasta: '2026-09-22T10:00:00Z',
  revertidaEn: null,
  motivoReversion: null,
  vigente: true,
  ...extra,
});

const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

/** Un fetch simulado que responde por ruta y metodo. */
function servicio(rutas) {
  return jest.fn(async (url, opciones = {}) => {
    const metodo = opciones.method ?? 'GET';
    const clave = `${metodo} ${new URL(url, 'http://x').pathname}${new URL(url, 'http://x').search}`;
    const respuesta = rutas[clave];
    if (!respuesta) {
      throw new Error(`sin ruta simulada para ${clave}`);
    }
    const { estado = 200, cuerpo = null } =
      typeof respuesta === 'function' ? respuesta(opciones) : respuesta;
    return {
      ok: estado >= 200 && estado < 300,
      status: estado,
      json: async () => cuerpo,
    };
  });
}

describe('presentacion', () => {
  test('tiempoRestante en minutos, horas o dias; vencida si paso; vacio si no aplica', () => {
    expect(tiempoRestante(null)).toBe('');
    expect(tiempoRestante('2026-09-21T10:30:00Z', AHORA)).toBe('30 min');
    expect(tiempoRestante('2026-09-21T15:30:00Z', AHORA)).toBe('5 h 30 min');
    expect(tiempoRestante('2026-09-24T12:00:00Z', AHORA)).toBe('3 d 2 h');
    expect(tiempoRestante('2026-09-21T09:00:00Z', AHORA)).toBe('vencida');
  });

  test('descripcionDe: advertencia no restringe, suspension con contador, baneo sin fin, revertida', () => {
    expect(descripcionDe(sancion({ tipo: 'ADVERTENCIA', vigenteHasta: null }), AHORA)).toBe(
      'Advertencia · no restringe tu acceso',
    );
    expect(descripcionDe(sancion(), AHORA)).toBe('Suspension · quedan 24 h 0 min');
    expect(descripcionDe(sancion({ tipo: 'BANEO', vigenteHasta: null }), AHORA)).toBe(
      'Baneo definitivo · sin fecha fin',
    );
    expect(descripcionDe(sancion({ revertidaEn: '2026-09-21T09:30:00Z' }), AHORA)).toBe(
      'Suspension · revertida',
    );
  });

  test('sePuedeApelar: vigente, dentro de 30 dias y sin apelacion abierta', () => {
    expect(sePuedeApelar(sancion(), [], AHORA)).toBe(true);
    expect(sePuedeApelar(sancion({ vigente: false }), [], AHORA)).toBe(false);
    expect(sePuedeApelar(sancion({ emitidaEn: '2026-08-01T00:00:00Z' }), [], AHORA)).toBe(false);
    expect(sePuedeApelar(sancion(), [{ sancionId: 's-1', estado: 'PENDIENTE' }], AHORA)).toBe(
      false,
    );
    expect(sePuedeApelar(sancion(), [{ sancionId: 's-1', estado: 'MANTENIDA' }], AHORA)).toBe(true);
  });

  test('solicitudDesde solo manda los campos del contrato segun el tipo', () => {
    expect(
      solicitudDesde({
        usuarioId: ` ${UID} `,
        tipo: 'ADVERTENCIA',
        motivo: ' x ',
        politica: '',
        duracionHoras: '5',
      }),
    ).toEqual({
      usuarioId: UID,
      tipo: 'ADVERTENCIA',
      motivo: 'x',
    });
    expect(
      solicitudDesde({ usuarioId: UID, tipo: 'SUSPENSION', motivo: 'x', duracionHoras: '48.7' }),
    ).toEqual({
      usuarioId: UID,
      tipo: 'SUSPENSION',
      motivo: 'x',
      duracionHoras: 48,
    });
    expect(
      solicitudDesde({
        usuarioId: UID,
        tipo: 'BANEO',
        motivo: 'grave',
        confirmacion: 'on',
        politica: 'P',
      }),
    ).toEqual({
      usuarioId: UID,
      tipo: 'BANEO',
      motivo: 'grave',
      politica: 'P',
      confirmacion: true,
    });
  });
});

const PANEL = `
  <div data-zona="aviso" hidden></div>
  <form data-zona="buscar"><input name="usuarioId" /><button type="submit">Ver</button></form>
  <form data-zona="emitir">
    <input name="usuarioId" />
    <select name="tipo"><option value="ADVERTENCIA" selected>A</option><option value="SUSPENSION">S</option><option value="BANEO">B</option></select>
    <label data-solo="SUSPENSION"><input name="duracionHoras" value="24" /></label>
    <textarea name="motivo"></textarea>
    <input name="politica" /><input name="comentarioId" />
    <label data-solo="BANEO"><input type="checkbox" name="confirmacion" /></label>
    <button type="submit">Emitir</button>
  </form>
  <div data-zona="historial"></div>
  <div data-zona="apelaciones"></div>`;

describe('panel de moderacion', () => {
  beforeEach(() => {
    document.body.innerHTML = PANEL;
  });

  test('sin rol de moderacion, se dice y el formulario queda cerrado', () => {
    montarPanelDeModeracion(document, { rol: 'JUGADOR', fetchImpl: servicio({}) });
    expect(document.querySelector('.aviso--advertencia').textContent).toMatch(/moderacion/);
    expect(document.querySelector('[data-zona="emitir"] button').disabled).toBe(true);
  });

  test('un moderador no ve el baneo como opcion; un administrador si', () => {
    montarPanelDeModeracion(document, {
      rol: 'MODERADOR',
      fetchImpl: servicio({ 'GET /api/v1/apelaciones': { cuerpo: [] } }),
    });
    expect(document.querySelector('option[value="BANEO"]').disabled).toBe(true);
    document.body.innerHTML = PANEL;
    montarPanelDeModeracion(document, {
      rol: 'ADMINISTRADOR',
      fetchImpl: servicio({ 'GET /api/v1/apelaciones': { cuerpo: [] } }),
    });
    expect(document.querySelector('option[value="BANEO"]').disabled).toBe(false);
  });

  test('emitir una advertencia manda el cuerpo del contrato, avisa y recarga el historial', async () => {
    const fetchImpl = servicio({
      'GET /api/v1/apelaciones': { cuerpo: [] },
      'POST /api/v1/sanciones': (opciones) => {
        const cuerpo = JSON.parse(opciones.body);
        return {
          estado: 201,
          cuerpo: sancion({ tipo: cuerpo.tipo, vigenteHasta: null, motivo: cuerpo.motivo }),
        };
      },
      [`GET /api/v1/sanciones/usuarios/${UID}`]: {
        cuerpo: [sancion({ tipo: 'ADVERTENCIA', vigenteHasta: null })],
      },
    });
    montarPanelDeModeracion(document, { rol: 'MODERADOR', fetchImpl, ahora: () => AHORA });
    const form = document.querySelector('[data-zona="emitir"]');
    form.querySelector('[name="usuarioId"]').value = UID;
    form.querySelector('[name="motivo"]').value = 'Lenguaje ofensivo';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    await asentar();

    const llamada = fetchImpl.mock.calls.find((c) => c[1]?.method === 'POST');
    expect(JSON.parse(llamada[1].body)).toEqual({
      usuarioId: UID,
      tipo: 'ADVERTENCIA',
      motivo: 'Lenguaje ofensivo',
    });
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/Sancion emitida/);
    expect(document.querySelectorAll('[data-zona="historial"] article')).toHaveLength(1);
  });

  test('el baneo sin confirmacion no sale de la vista; el 403 del servicio se muestra tal cual', async () => {
    const fetchImpl = servicio({
      'GET /api/v1/apelaciones': { cuerpo: [] },
      'POST /api/v1/sanciones': {
        estado: 403,
        cuerpo: {
          title: 'No tienes permiso para esto',
          detail: 'el baneo es de administrador',
          motivo: 'PERMISO_INSUFICIENTE',
        },
      },
    });
    montarPanelDeModeracion(document, { rol: 'ADMINISTRADOR', fetchImpl });
    const form = document.querySelector('[data-zona="emitir"]');
    form.querySelector('[name="usuarioId"]').value = UID;
    form.querySelector('[name="tipo"]').value = 'BANEO';
    form.querySelector('[name="motivo"]').value = 'Fraude';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    expect(document.querySelector('.aviso--advertencia').textContent).toMatch(/definitivo/);
    expect(fetchImpl.mock.calls.some((c) => c[1]?.method === 'POST')).toBe(false);

    form.querySelector('[name="confirmacion"]').checked = true;
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    await asentar();
    expect(document.querySelector('.aviso--advertencia .aviso__titulo').textContent).toBe(
      'No tienes permiso para esto',
    );
  });

  test('las apelaciones pendientes se listan y resolver manda decision y motivo', async () => {
    const pendiente = {
      id: 'a-1',
      sancionId: 's-1',
      usuarioId: UID,
      argumento: 'No fui yo',
      creadaEn: '2026-09-21T09:30:00Z',
      estado: 'PENDIENTE',
    };
    const fetchImpl = servicio({
      'GET /api/v1/apelaciones': { cuerpo: [pendiente] },
      'POST /api/v1/apelaciones/a-1/resolucion': (opciones) => ({
        cuerpo: {
          ...pendiente,
          ...JSON.parse(opciones.body),
          estado: JSON.parse(opciones.body).decision,
        },
      }),
    });
    montarPanelDeModeracion(document, { rol: 'ADMINISTRADOR', fetchImpl });
    await asentar();
    const form = document.querySelector('[data-zona="apelaciones"] [data-zona="resolucion"]');
    expect(form).not.toBeNull();
    form.querySelector('[name="decision"]').value = 'REVERTIDA';
    form.querySelector('[name="motivo"]').value = 'Tiene razon';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    await asentar();
    const llamada = fetchImpl.mock.calls.find((c) => String(c[0]).includes('/resolucion'));
    expect(JSON.parse(llamada[1].body)).toEqual({
      decision: 'REVERTIDA',
      motivo: 'Tiene razon',
      nuevaVigencia: null,
    });
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/revertida/);
  });
});

const MIAS = `
  <div data-zona="aviso" hidden></div>
  <div data-zona="sanciones"></div>
  <form data-zona="apelar" hidden><input type="hidden" name="sancionId" /><textarea name="argumento"></textarea><button type="submit">Enviar</button></form>
  <div data-zona="apelaciones"></div>`;

describe('mis sanciones', () => {
  beforeEach(() => {
    document.body.innerHTML = MIAS;
  });

  test('muestra el contador de la suspension y solo ofrece apelar lo apelable; apelar manda el argumento', async () => {
    const fetchImpl = servicio({
      [`GET /api/v1/sanciones/usuarios/${UID}`]: {
        cuerpo: [
          sancion(),
          sancion({
            id: 's-2',
            tipo: 'ADVERTENCIA',
            vigenteHasta: null,
            emitidaEn: '2026-07-01T00:00:00Z',
          }),
        ],
      },
      'GET /api/v1/apelaciones?mias=true': { cuerpo: [] },
      'POST /api/v1/sanciones/s-1/apelaciones': (opciones) => ({
        estado: 201,
        cuerpo: {
          id: 'a-9',
          sancionId: 's-1',
          usuarioId: UID,
          argumento: JSON.parse(opciones.body).argumento,
          creadaEn: '2026-09-21T10:00:00Z',
          estado: 'PENDIENTE',
        },
      }),
    });
    montarMisSanciones(document, { uid: UID, fetchImpl, ahora: () => AHORA });
    await asentar();
    await asentar();

    const tarjetas = document.querySelectorAll('[data-zona="sanciones"] article');
    expect(tarjetas).toHaveLength(2);
    expect(tarjetas[0].textContent).toMatch(/quedan 24 h 0 min/);
    expect(tarjetas[0].querySelector('[data-accion="apelar"]')).not.toBeNull();
    expect(tarjetas[1].querySelector('[data-accion="apelar"]')).toBeNull();

    tarjetas[0].querySelector('[data-accion="apelar"]').click();
    const form = document.querySelector('[data-zona="apelar"]');
    expect(form.hidden).toBe(false);
    expect(form.querySelector('[name="sancionId"]').value).toBe('s-1');
    form.querySelector('[name="argumento"]').value = 'No fui yo';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    await asentar();
    const llamada = fetchImpl.mock.calls.find(
      (c) => String(c[0]).includes('/apelaciones') && c[1]?.method === 'POST',
    );
    expect(JSON.parse(llamada[1].body)).toEqual({ argumento: 'No fui yo' });
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/Apelacion enviada/);
  });

  test('si no hay sanciones se dice; un error del servicio se avisa', async () => {
    montarMisSanciones(document, {
      uid: UID,
      fetchImpl: servicio({
        [`GET /api/v1/sanciones/usuarios/${UID}`]: { cuerpo: [] },
        'GET /api/v1/apelaciones?mias=true': { cuerpo: [] },
      }),
    });
    await asentar();
    await asentar();
    expect(document.querySelector('[data-zona="sanciones"]').textContent).toMatch(
      /No tienes sanciones/,
    );

    document.body.innerHTML = MIAS;
    montarMisSanciones(document, {
      uid: UID,
      fetchImpl: servicio({
        [`GET /api/v1/sanciones/usuarios/${UID}`]: {
          estado: 503,
          cuerpo: { title: 'Caido', detail: 'x' },
        },
        'GET /api/v1/apelaciones?mias=true': { cuerpo: [] },
      }),
    });
    await asentar();
    await asentar();
    expect(document.querySelector('.aviso--error .aviso__titulo').textContent).toBe('Caido');
  });

  test('ErrorDeSanciones conserva estado, titulo, detalle y motivo', () => {
    const e = new ErrorDeSanciones({ title: 'T', detail: 'D', motivo: 'SOLICITUD_INVALIDA' }, 400);
    expect(e.estado).toBe(400);
    expect(e.titulo).toBe('T');
    expect(e.detalle).toBe('D');
    expect(e.motivo).toBe('SOLICITUD_INVALIDA');
  });
});
