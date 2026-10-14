/**
 * Sanciones — HU-USR-004/005/006/007: presentación pura y montaje de las dos
 * vistas contra un servicio simulado.
 */

import { jest } from '@jest/globals';

import {
  ErrorDeSanciones,
  descripcionDe,
  frasePlazoDeApelacion,
  montarMisSanciones,
  sePuedeApelar,
  tarjetaDeApelacion,
  tiempoRestante,
} from './sanciones.js';

const AHORA = Date.parse('2026-09-21T10:00:00Z');
const UID = '11111111-1111-1111-1111-111111111111';

/** Lo que responde `GET /api/v1/sanciones/limites` con el catálogo por omisión. */
const LIMITES = {
  suspensionMinimaHoras: 1,
  suspensionMaximaHoras: 720,
  suspensionMaximaDias: 30,
  apelacionPlazoDias: 30,
};

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
  test('tiempoRestante en minutos, horas o días; vencida si paso; vacío si no aplica', () => {
    expect(tiempoRestante(null)).toBe('');
    expect(tiempoRestante('2026-09-21T10:30:00Z', AHORA)).toBe('30 min');
    expect(tiempoRestante('2026-09-21T15:30:00Z', AHORA)).toBe('5 h 30 min');
    expect(tiempoRestante('2026-09-24T12:00:00Z', AHORA)).toBe('3 d 2 h');
    expect(tiempoRestante('2026-09-21T09:00:00Z', AHORA)).toBe('vencida');
  });

  test('descripcionDe: advertencia no restringe, suspensión activa o vencida (según el servidor), baneo sin fin, revertida', () => {
    expect(descripcionDe(sancion({ tipo: 'ADVERTENCIA', vigenteHasta: null }), AHORA)).toBe(
      'Advertencia · no restringe tu acceso',
    );
    // UXC-7 — el tiempo que falta va en su cuenta atrás viva (tarjetaDeSancion);
    // la línea dice el estado, y el estado lo decide `vigente` del servidor.
    expect(descripcionDe(sancion(), AHORA)).toBe('Suspensión · activa');
    expect(descripcionDe(sancion({ vigente: false }), AHORA)).toBe('Suspensión · vencida');
    expect(descripcionDe(sancion({ vigente: undefined }), AHORA)).toBe('Suspensión · activa');
    expect(descripcionDe(sancion({ tipo: 'BANEO', vigenteHasta: null }), AHORA)).toBe(
      'Baneo definitivo · sin fecha fin',
    );
    expect(descripcionDe(sancion({ revertidaEn: '2026-09-21T09:30:00Z' }), AHORA)).toBe(
      'Suspensión · revertida',
    );
  });

  test('sePuedeApelar: vigente, dentro del plazo vigente y sin apelación abierta', () => {
    expect(sePuedeApelar(sancion(), [], AHORA, 30)).toBe(true);
    expect(sePuedeApelar(sancion({ vigente: false }), [], AHORA, 30)).toBe(false);
    expect(sePuedeApelar(sancion({ emitidaEn: '2026-08-01T00:00:00Z' }), [], AHORA, 30)).toBe(
      false,
    );
    expect(sePuedeApelar(sancion(), [{ sancionId: 's-1', estado: 'PENDIENTE' }], AHORA, 30)).toBe(
      false,
    );
    expect(sePuedeApelar(sancion(), [{ sancionId: 's-1', estado: 'MANTENIDA' }], AHORA, 30)).toBe(
      true,
    );
  });

  test('sePuedeApelar: el plazo es el que diga el servicio, no un 30 quemado', () => {
    // Emitida hace 20 días: dentro de plazo con 30, fuera con 7.
    const hace20Dias = sancion({ emitidaEn: '2026-09-01T10:00:00Z' });
    expect(sePuedeApelar(hace20Dias, [], AHORA, 30)).toBe(true);
    expect(sePuedeApelar(hace20Dias, [], AHORA, 7)).toBe(false);
  });

  test('sePuedeApelar: sin plazo conocido no se esconde el botón, decide el servicio', () => {
    const antigua = sancion({ emitidaEn: '2026-01-01T00:00:00Z' });
    expect(sePuedeApelar(antigua, [], AHORA, null)).toBe(true);
    expect(sePuedeApelar(antigua, [], AHORA)).toBe(true);
    // Lo que no depende del plazo se sigue descartando igual.
    expect(sePuedeApelar(sancion({ vigente: false }), [], AHORA, null)).toBe(false);
  });

  test('frasePlazoDeApelacion: con dato el número, sin dato una frase honesta sin número', () => {
    expect(frasePlazoDeApelacion(7)).toBe('dentro de los 7 días siguientes');
    expect(frasePlazoDeApelacion(30)).toBe('dentro de los 30 días siguientes');
    expect(frasePlazoDeApelacion(null)).toBe('dentro del plazo de apelación vigente');
    expect(frasePlazoDeApelacion(undefined)).toBe('dentro del plazo de apelación vigente');
    expect(frasePlazoDeApelacion(0)).toBe('dentro del plazo de apelación vigente');
    expect(frasePlazoDeApelacion(Number.NaN)).toBe('dentro del plazo de apelación vigente');
  });
});

const MIAS = `
  <div data-zona="aviso" hidden></div>
  <p data-zona="intro">Tu historial disciplinario. Una sanción vigente se puede apelar dentro del plazo de apelación vigente; el panel de revisión responde con una decisión motivada.</p>
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
      'GET /api/v1/sanciones/limites': { cuerpo: LIMITES },
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
    // UXC-7 — SanctionCountdown: la cuenta atrás hasta la fecha del servidor.
    expect(tarjetas[0].textContent).toMatch(/Suspensión · activa/);
    expect(tarjetas[0].querySelector('time.cuenta-atras').getAttribute('datetime')).toBe(
      '2026-09-22T10:00:00Z',
    );
    expect(tarjetas[0].textContent).toMatch(/hasta el martes,? 22 de septiembre/);
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
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/Apelación enviada/);
  });

  test('si no hay sanciones se dice; un error del servicio se avisa', async () => {
    montarMisSanciones(document, {
      uid: UID,
      fetchImpl: servicio({
        [`GET /api/v1/sanciones/usuarios/${UID}`]: { cuerpo: [] },
        'GET /api/v1/apelaciones?mias=true': { cuerpo: [] },
        'GET /api/v1/sanciones/limites': { cuerpo: LIMITES },
      }),
    });
    await asentar();
    await asentar();
    expect(document.querySelector('[data-zona="sanciones"]').textContent).toMatch(
      /No tienes ninguna sanción/,
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
        'GET /api/v1/sanciones/limites': { cuerpo: LIMITES },
      }),
    });
    await asentar();
    await asentar();

    // UX-R3.8 — el fallo se dice DONDE iban las sanciones.
    //
    // Antes solo saltaba el aviso flotante con «Caido» y quedaban dos
    // tarjetas vacias debajo: «Sanciones» y «Mis apelaciones», con nada
    // dentro. La pantalla parecia decir que no tienes ninguna sancion, que es
    // exactamente lo contrario de lo que se sabe — no se sabe nada.
    const zona = document.querySelector('[data-zona="sanciones"]');
    expect(zona.textContent).toMatch(/No pudimos consultar tu historial/);
    expect(zona.textContent).toMatch(/no significa que no tengas sanciones/i);
    expect(zona.querySelector('[data-accion="reintentar"]')).not.toBeNull();
  });

  test('el plazo de los textos es el vigente, no un 30 escrito a mano', async () => {
    const fetchImpl = servicio({
      [`GET /api/v1/sanciones/usuarios/${UID}`]: { cuerpo: [] },
      'GET /api/v1/apelaciones?mias=true': { cuerpo: [] },
      'GET /api/v1/sanciones/limites': { cuerpo: { ...LIMITES, apelacionPlazoDias: 7 } },
    });
    montarMisSanciones(document, { uid: UID, fetchImpl, ahora: () => AHORA });
    await asentar();
    await asentar();

    expect(document.querySelector('[data-zona="intro"]').textContent).toMatch(
      /dentro de los 7 días siguientes/,
    );
    expect(document.querySelector('[data-zona="apelaciones"]').textContent).toMatch(
      /dentro de los 7 días siguientes/,
    );
    expect(document.body.textContent).not.toMatch(/30 días/);
  });

  test('sin límites, los textos hablan del plazo sin número y nunca de 30 días', async () => {
    const fetchImpl = servicio({
      [`GET /api/v1/sanciones/usuarios/${UID}`]: { cuerpo: [] },
      'GET /api/v1/apelaciones?mias=true': { cuerpo: [] },
      'GET /api/v1/sanciones/limites': { estado: 401, cuerpo: { title: 'Sin sesión' } },
    });
    montarMisSanciones(document, { uid: UID, fetchImpl, ahora: () => AHORA });
    await asentar();
    await asentar();

    // El fallo de los límites NO tumba la vista: el historial se pintó igual.
    expect(document.querySelector('[data-zona="sanciones"]').textContent).toMatch(
      /No tienes ninguna sanción/,
    );
    expect(document.querySelector('[data-zona="intro"]').textContent).toMatch(
      /dentro del plazo de apelación vigente/,
    );
    expect(document.querySelector('[data-zona="apelaciones"]').textContent).toMatch(
      /dentro del plazo de apelación vigente/,
    );
    expect(document.body.textContent).not.toMatch(/30 días/);
  });

  test('con el plazo en 7 días, una sanción de hace 20 ya no ofrece el botón de apelar', async () => {
    const fetchImpl = servicio({
      [`GET /api/v1/sanciones/usuarios/${UID}`]: {
        cuerpo: [sancion({ emitidaEn: '2026-09-01T10:00:00Z' })],
      },
      'GET /api/v1/apelaciones?mias=true': { cuerpo: [] },
      'GET /api/v1/sanciones/limites': { cuerpo: { ...LIMITES, apelacionPlazoDias: 7 } },
    });
    montarMisSanciones(document, { uid: UID, fetchImpl, ahora: () => AHORA });
    await asentar();
    await asentar();

    const tarjeta = document.querySelector('[data-zona="sanciones"] article');
    expect(tarjeta).not.toBeNull();
    expect(tarjeta.querySelector('[data-accion="apelar"]')).toBeNull();
  });

  test('ErrorDeSanciones conserva estado, título, detalle y motivo', () => {
    const e = new ErrorDeSanciones({ title: 'T', detail: 'D', motivo: 'SOLICITUD_INVALIDA' }, 400);
    expect(e.estado).toBe(400);
    expect(e.titulo).toBe('T');
    expect(e.detalle).toBe('D');
    expect(e.motivo).toBe('SOLICITUD_INVALIDA');
  });
});

describe('tarjeta de una apelación en «Mis sanciones»', () => {
  test('pendiente: en revisión y la fecha en que decide el panel; sin formulario de resolución', () => {
    const tarjeta = tarjetaDeApelacion({
      id: 'a-1',
      sancionId: 's-1',
      argumento: 'No fui yo',
      creadaEn: '2026-10-13T16:00:00Z',
      estado: 'PENDIENTE',
      decisionAntesDe: '2026-11-03T04:59:59.999Z',
      vencida: false,
    });
    expect(tarjeta.textContent).toContain('Apelación · en revisión');
    expect(tarjeta.textContent).toMatch(/El panel decide a más tardar el \d+ de noviembre de 2026/);
    expect(tarjeta.querySelector('form')).toBeNull();
  });

  test('resuelta: la decisión con su motivo', () => {
    const tarjeta = tarjetaDeApelacion({
      id: 'a-2',
      sancionId: 's-1',
      argumento: 'Fue un error',
      creadaEn: '2026-10-13T16:00:00Z',
      estado: 'REVERTIDA',
      decisionMotivo: 'Las pruebas no eran concluyentes',
      decisionAntesDe: '2026-11-03T04:59:59.999Z',
      vencida: false,
    });
    expect(tarjeta.textContent).toContain('Apelación · revertida');
    expect(tarjeta.textContent).toContain('Decisión: Las pruebas no eran concluyentes');
    expect(tarjeta.textContent).not.toContain('El panel decide');
  });
});
