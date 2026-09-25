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
  montarPanelDeModeracion,
  sePuedeApelar,
  solicitudDesde,
  textoDeRangoDeSuspension,
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

  test('textoDeRangoDeSuspension: el rango real, con singular y plural; sin dato, sin rango', () => {
    expect(textoDeRangoDeSuspension(LIMITES)).toBe('Duración en horas (1 hora a 30 días)');
    expect(textoDeRangoDeSuspension({ suspensionMinimaHoras: 2, suspensionMaximaDias: 1 })).toBe(
      'Duración en horas (2 horas a 1 día)',
    );
    expect(textoDeRangoDeSuspension(null)).toBe('Duración en horas');
    expect(textoDeRangoDeSuspension({})).toBe('Duración en horas');
  });

  test('solicitudDesde solo manda los campos del contrato según el tipo', () => {
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
    <label data-solo="SUSPENSION"><span data-campo="rango-suspension">Duración en horas</span><input type="number" name="duracionHoras" min="1" value="24" /></label>
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

  test('sin rol de moderación, se dice y el formulario queda cerrado', () => {
    montarPanelDeModeracion(document, { rol: 'JUGADOR', fetchImpl: servicio({}) });
    expect(document.querySelector('.aviso--advertencia').textContent).toMatch(/moderación/);
    expect(document.querySelector('[data-zona="emitir"] button').disabled).toBe(true);
  });

  test('un moderador no ve el baneo como opción; un administrador si', () => {
    montarPanelDeModeracion(document, {
      rol: 'MODERADOR',
      fetchImpl: servicio({
        'GET /api/v1/apelaciones': { cuerpo: [] },
        'GET /api/v1/sanciones/limites': { cuerpo: LIMITES },
      }),
    });
    expect(document.querySelector('option[value="BANEO"]').disabled).toBe(true);
    document.body.innerHTML = PANEL;
    montarPanelDeModeracion(document, {
      rol: 'ADMINISTRADOR',
      fetchImpl: servicio({
        'GET /api/v1/apelaciones': { cuerpo: [] },
        'GET /api/v1/sanciones/limites': { cuerpo: LIMITES },
      }),
    });
    expect(document.querySelector('option[value="BANEO"]').disabled).toBe(false);
  });

  test('emitir una advertencia manda el cuerpo del contrato, avisa y recarga el historial', async () => {
    const fetchImpl = servicio({
      'GET /api/v1/apelaciones': { cuerpo: [] },
      'GET /api/v1/sanciones/limites': { cuerpo: LIMITES },
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
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/Sanción emitida/);
    // UXC-7 — AdminTimeline: el estado de la cuenta y la línea de tiempo.
    const historial = document.querySelector('[data-zona="historial"]');
    expect(historial.querySelector('[data-estado-cuenta="en-regla"]').textContent).toContain(
      'Tiene una advertencia: no le impide jugar.',
    );
    const hechos = historial.querySelectorAll('.linea-tiempo__hecho');
    expect(hechos).toHaveLength(1);
    expect(hechos[0].textContent).toContain('Advertencia emitida');
    expect(hechos[0].textContent).toContain('Por moderación');
  });

  test('el baneo sin confirmación no sale de la vista; el 403 del servicio se muestra tal cual', async () => {
    const fetchImpl = servicio({
      'GET /api/v1/apelaciones': { cuerpo: [] },
      'GET /api/v1/sanciones/limites': { cuerpo: LIMITES },
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
      'GET /api/v1/sanciones/limites': { cuerpo: LIMITES },
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

  // El defecto: `max="720"` en el HTML eran los 30 dias del catalogo pasados a
  // horas a mano. Con el parametro en 2 dias, el formulario aceptaba 720 y el
  // servicio devolvia 400.
  test('el rango de la suspensión sale del servicio, no del HTML', async () => {
    const fetchImpl = servicio({
      'GET /api/v1/apelaciones': { cuerpo: [] },
      'GET /api/v1/sanciones/limites': {
        cuerpo: {
          suspensionMinimaHoras: 2,
          suspensionMaximaHoras: 48,
          suspensionMaximaDias: 2,
          apelacionPlazoDias: 7,
        },
      },
    });
    montarPanelDeModeracion(document, { rol: 'ADMINISTRADOR', fetchImpl });
    await asentar();
    await asentar();

    const duracion = document.querySelector('[name="duracionHoras"]');
    expect(duracion.getAttribute('max')).toBe('48');
    expect(duracion.getAttribute('min')).toBe('2');
    // 24 seguía dentro del rango, así que el valor por omisión no se toca.
    expect(duracion.value).toBe('24');
    expect(document.querySelector('[data-campo="rango-suspension"]').textContent).toBe(
      'Duración en horas (2 horas a 2 días)',
    );
  });

  test('el valor por omisión se ajusta si queda fuera del rango vigente', async () => {
    const fetchImpl = servicio({
      'GET /api/v1/apelaciones': { cuerpo: [] },
      'GET /api/v1/sanciones/limites': {
        cuerpo: {
          suspensionMinimaHoras: 1,
          suspensionMaximaHoras: 6,
          suspensionMaximaDias: 1,
          apelacionPlazoDias: 7,
        },
      },
    });
    montarPanelDeModeracion(document, { rol: 'ADMINISTRADOR', fetchImpl });
    await asentar();
    await asentar();

    expect(document.querySelector('[name="duracionHoras"]').value).toBe('6');
  });

  test('sin límites no se inventa un tope: el campo queda sin max y la etiqueta sin rango', async () => {
    const fetchImpl = servicio({
      'GET /api/v1/apelaciones': { cuerpo: [] },
      'GET /api/v1/sanciones/limites': { estado: 503, cuerpo: { title: 'Caido', detail: 'x' } },
    });
    montarPanelDeModeracion(document, { rol: 'ADMINISTRADOR', fetchImpl });
    await asentar();
    await asentar();

    expect(document.querySelector('[name="duracionHoras"]').getAttribute('max')).toBeNull();
    expect(document.querySelector('[data-campo="rango-suspension"]').textContent).toBe(
      'Duración en horas',
    );
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
