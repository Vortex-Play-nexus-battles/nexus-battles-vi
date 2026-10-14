/**
 * Inactividad de las sesiones administrativas — 7.3.9.
 *
 * Lo que se afirma es lo que vería quien administra: que trabajando no se le
 * cierra nada; que si se va, un minuto antes le avisa con la cuenta atrás;
 * que «Seguir conectado» le deja seguir; y que si nadie responde, el login le
 * dice que fue por inactividad. Y que un jugador no tiene nada de esto.
 */

import { jest } from '@jest/globals';

import { CLAVES, MOTIVOS, guardarSesion, leerSesion } from './sesion.js';
import {
  AVISAR_CUANDO_QUEDEN_MS,
  RENOVACION,
  RENOVAR_CUANDO_QUEDEN_MS,
  crearGuardaDeInactividad,
  mostrarAvisoDeInactividad,
  renovarSesion,
  tiempoRestante,
} from './inactividad-administrativa.js';

const QUINCE_MINUTOS = 15 * 60_000;

function tokenCon(claims) {
  const cuerpo = btoa(JSON.stringify(claims))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `cabecera.${cuerpo}.firma`;
}

/** Reloj manual: el tiempo solo avanza cuando la prueba lo dice. */
function crearReloj(inicio = 1_700_000_000_000) {
  let ahora = inicio;
  let siguienteId = 1;
  const pendientes = new Map();
  return {
    ahora: () => ahora,
    setTimeout(funcion, ms) {
      const id = siguienteId++;
      pendientes.set(id, { funcion, cuando: ahora + ms });
      return id;
    },
    clearTimeout(id) {
      pendientes.delete(id);
    },
    /** Avanza de uno en uno los temporizadores que vencen, en orden. */
    avanzar(ms) {
      const destino = ahora + ms;
      for (;;) {
        const proximo = [...pendientes.entries()]
          .filter(([, t]) => t.cuando <= destino)
          .sort((a, b) => a[1].cuando - b[1].cuando)[0];
        if (!proximo) {
          break;
        }
        const [id, { funcion, cuando }] = proximo;
        pendientes.delete(id);
        ahora = Math.max(ahora, cuando);
        funcion();
      }
      ahora = destino;
    },
  };
}

const vaciarPromesas = async () => {
  for (let i = 0; i < 10; i += 1) {
    await Promise.resolve();
  }
};

function conSesion(reloj, { rol = 'ADMINISTRADOR', vidaMs = QUINCE_MINUTOS, marca = 'a' } = {}) {
  const exp = Math.floor((reloj.ahora() + vidaMs) / 1000);
  const token = tokenCon({ uid: 'u-1', sub: 'ana_admin', rol, exp, marca });
  sessionStorage.setItem(CLAVES.token, token);
  sessionStorage.setItem(CLAVES.apodo, 'ana_admin');
  sessionStorage.setItem(CLAVES.rol, rol);
  return token;
}

/** Un fetch que responde a la renovación con un token nuevo de 15 minutos. */
function renovacionQueFunciona(reloj) {
  let veces = 0;
  return jest.fn(async () => {
    veces += 1;
    const exp = Math.floor((reloj.ahora() + QUINCE_MINUTOS) / 1000);
    return {
      ok: true,
      status: 200,
      json: async () => ({ token: tokenCon({ uid: 'u-1', rol: 'ADMINISTRADOR', exp, veces }) }),
    };
  });
}

function montar(reloj, extra = {}) {
  const terminar = jest.fn();
  const avisos = [];
  const avisar = jest.fn((opciones) => {
    const aviso = { ...opciones, actualizar: jest.fn(), cerrar: jest.fn() };
    avisos.push(aviso);
    return aviso;
  });
  const documento = new EventTarget();
  documento.visibilityState = 'visible';
  const guarda = crearGuardaDeInactividad({
    terminar,
    almacen: sessionStorage,
    ahora: reloj.ahora,
    reloj,
    documento,
    avisar,
    fetchImpl: extra.fetchImpl ?? renovacionQueFunciona(reloj),
    ...extra,
  });
  return { guarda, terminar, avisar, avisos, documento };
}

beforeEach(() => {
  sessionStorage.clear();
  document.body.innerHTML = '';
});

describe('a quién vigila', () => {
  test('un jugador no tiene límite de inactividad: no hay guarda', () => {
    const reloj = crearReloj();
    conSesion(reloj, { rol: 'JUGADOR', vidaMs: 24 * 3600_000 });
    expect(montar(reloj).guarda).toBeNull();
  });

  test('sin sesión no hay nada que vigilar', () => {
    expect(montar(crearReloj()).guarda).toBeNull();
  });

  test.each(['MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR'])('%s sí', (rol) => {
    const reloj = crearReloj();
    conSesion(reloj, { rol });
    const { guarda } = montar(reloj);
    expect(guarda).not.toBeNull();
    guarda.detener();
  });
});

describe('trabajando no se cierra nada', () => {
  test('con actividad, el token se renueva antes de caducar y no sale ningún aviso', async () => {
    const reloj = crearReloj();
    const original = conSesion(reloj);
    const fetchImpl = renovacionQueFunciona(reloj);
    const { documento, avisar, terminar, guarda } = montar(reloj, { fetchImpl });

    // Una hora de trabajo: una tecla cada minuto.
    for (let minuto = 0; minuto < 60; minuto += 1) {
      reloj.avanzar(60_000);
      documento.dispatchEvent(new Event('keydown'));
      await vaciarPromesas();
    }

    expect(fetchImpl).toHaveBeenCalled();
    const [ruta, opciones] = fetchImpl.mock.calls[0];
    expect(ruta).toMatch(/\/api\/v1\/auth\/sesion\/renovacion$/);
    expect(opciones.method).toBe('POST');
    expect(opciones.headers.Authorization).toBe(`Bearer ${original}`);
    expect(sessionStorage.getItem(CLAVES.token)).not.toBe(original);
    expect(sessionStorage.getItem(CLAVES.apodo)).toBe('ana_admin');
    expect(avisar).not.toHaveBeenCalled();
    expect(terminar).not.toHaveBeenCalled();
    // No se renueva en cada tecla: solo cuando quedan menos de 5 minutos.
    expect(fetchImpl.mock.calls.length).toBeLessThanOrEqual(60 / 10 + 1);
    guarda.detener();
  });

  test('con más de 5 minutos por delante no se pide nada aunque haya actividad', async () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const fetchImpl = renovacionQueFunciona(reloj);
    const { documento, guarda } = montar(reloj, { fetchImpl });

    reloj.avanzar(QUINCE_MINUTOS - RENOVAR_CUANDO_QUEDEN_MS - 30_000);
    documento.dispatchEvent(new Event('pointerdown'));
    await vaciarPromesas();

    expect(fetchImpl).not.toHaveBeenCalled();
    guarda.detener();
  });
});

describe('si la persona se va', () => {
  test('un minuto antes avisa con la cuenta atrás y, sin respuesta, cierra por inactividad', async () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const fetchImpl = renovacionQueFunciona(reloj);
    const { avisar, avisos, terminar } = montar(reloj, { fetchImpl });

    // La carga de la página cuenta como actividad: la primera renovación
    // ocurre al entrar en los últimos 5 minutos. Desde ahí, nadie toca nada.
    reloj.avanzar(QUINCE_MINUTOS - RENOVAR_CUANDO_QUEDEN_MS + 1_000);
    await vaciarPromesas();
    expect(fetchImpl).toHaveBeenCalledTimes(1);

    reloj.avanzar(QUINCE_MINUTOS - AVISAR_CUANDO_QUEDEN_MS);
    await vaciarPromesas();
    expect(avisar).toHaveBeenCalledTimes(1);
    expect(avisos[0].restanteMs).toBeLessThanOrEqual(AVISAR_CUANDO_QUEDEN_MS);

    reloj.avanzar(5_000);
    expect(avisos[0].actualizar).toHaveBeenCalled();
    expect(terminar).not.toHaveBeenCalled();

    reloj.avanzar(AVISAR_CUANDO_QUEDEN_MS);
    await vaciarPromesas();
    expect(terminar).toHaveBeenCalledWith(MOTIVOS.INACTIVIDAD);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  test('con actividad pero sin red, la renovación que no llega no se toma por inactividad', async () => {
    const reloj = crearReloj();
    // Cuatro minutos de vida: la carga de la página (actividad) ya pide renovar.
    conSesion(reloj, { vidaMs: 4 * 60_000 });
    const fetchImpl = jest.fn(async () => ({ ok: false, status: 503, json: async () => ({}) }));
    const { terminar, avisar } = montar(reloj, { fetchImpl });

    for (let i = 0; i < 20; i += 1) {
      reloj.avanzar(15_000);
      await vaciarPromesas();
    }

    expect(fetchImpl).toHaveBeenCalled();
    // Decide el vigilante de la sesión al caducar («caducada»), no esta guarda.
    expect(terminar).not.toHaveBeenCalled();
    expect(avisar).not.toHaveBeenCalled();
  });

  test('«Seguir conectado» renueva y cierra el aviso', async () => {
    const reloj = crearReloj();
    const original = conSesion(reloj, { vidaMs: 50_000 });
    const fetchImpl = renovacionQueFunciona(reloj);
    const { avisos, terminar, guarda } = montar(reloj, { fetchImpl });
    // Sin actividad desde la carga: se gasta con una renovación inicial…
    await vaciarPromesas();
    reloj.avanzar(15_000);
    await vaciarPromesas();
    // …que deja un token nuevo de 15 min. Se espera al aviso.
    reloj.avanzar(QUINCE_MINUTOS - AVISAR_CUANDO_QUEDEN_MS);
    await vaciarPromesas();
    expect(avisos).toHaveLength(1);

    const antes = sessionStorage.getItem(CLAVES.token);
    avisos[0].alSeguir();
    await vaciarPromesas();

    expect(sessionStorage.getItem(CLAVES.token)).not.toBe(antes);
    expect(sessionStorage.getItem(CLAVES.token)).not.toBe(original);
    expect(terminar).not.toHaveBeenCalled();
    guarda.detener();
  });

  test('«Cerrar sesión» en el aviso cierra como un cierre voluntario', async () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const { avisos, terminar } = montar(reloj);
    reloj.avanzar(QUINCE_MINUTOS - RENOVAR_CUANDO_QUEDEN_MS + 1_000);
    await vaciarPromesas();
    reloj.avanzar(QUINCE_MINUTOS - AVISAR_CUANDO_QUEDEN_MS);
    await vaciarPromesas();

    avisos[0].alSalir();

    expect(terminar).toHaveBeenCalledWith(MOTIVOS.CERRADA);
  });

  test('actuar en la página con el aviso abierto también es seguir conectado', async () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const fetchImpl = renovacionQueFunciona(reloj);
    const { avisos, documento, terminar, guarda } = montar(reloj, { fetchImpl });
    reloj.avanzar(QUINCE_MINUTOS - RENOVAR_CUANDO_QUEDEN_MS + 1_000);
    await vaciarPromesas();
    reloj.avanzar(QUINCE_MINUTOS - AVISAR_CUANDO_QUEDEN_MS);
    await vaciarPromesas();
    const llamadas = fetchImpl.mock.calls.length;

    documento.dispatchEvent(new Event('keydown'));
    await vaciarPromesas();

    expect(avisos[0].cerrar).toHaveBeenCalled();
    expect(fetchImpl.mock.calls.length).toBe(llamadas + 1);
    reloj.avanzar(2 * 60_000);
    await vaciarPromesas();
    expect(terminar).not.toHaveBeenCalled();
    guarda.detener();
  });
});

describe('un reloj descuadrado (7.3.9)', () => {
  const TRES_HORAS = 3 * 3600_000;

  /**
   * El servidor vive TRES HORAS por detrás de este equipo: su token dice
   * `iat` y `exp` en SU hora. Es lo que pasa con un reloj mal puesto, y lo
   * mismo que hace `page.clock` en el E2E al adelantar el navegador.
   */
  function servidorAtrasado(reloj) {
    const horaDelServidor = () => reloj.ahora() - TRES_HORAS;
    const emitir = (extra = {}) => {
      const iat = Math.floor(horaDelServidor() / 1000);
      return tokenCon({
        uid: 'u-1',
        sub: 'ana_admin',
        rol: 'ADMINISTRADOR',
        iat,
        exp: iat + QUINCE_MINUTOS / 1000,
        ...extra,
      });
    };
    const fetchImpl = jest.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ token: emitir({ veces: fetchImpl.mock.calls.length }) }),
    }));
    return { emitir, fetchImpl };
  }

  test('sin medir el desfase, el token recién emitido parecería caducado', () => {
    const reloj = crearReloj();
    const { emitir } = servidorAtrasado(reloj);
    guardarSesion({ token: emitir(), apodo: 'ana_admin', rol: 'ADMINISTRADOR' }, sessionStorage);

    expect(leerSesion(sessionStorage, reloj.ahora).caducada).toBe(true);
  });

  test('medido al recibirlo, la sesión vale, se renueva a 5 minutos del fin real y avisa al minuto', async () => {
    const reloj = crearReloj();
    const { emitir, fetchImpl } = servidorAtrasado(reloj);
    guardarSesion(
      { token: emitir(), apodo: 'ana_admin', rol: 'ADMINISTRADOR', recibidoEn: reloj.ahora() },
      sessionStorage,
    );
    expect(leerSesion(sessionStorage, reloj.ahora).autenticado).toBe(true);
    const { guarda, avisos, terminar } = montar(reloj, { fetchImpl });
    expect(guarda).not.toBeNull();

    // Abrir la página es actividad: renueva al entrar en los últimos 5 minutos
    // del token, contados en la hora del servidor; no antes.
    reloj.avanzar(QUINCE_MINUTOS - RENOVAR_CUANDO_QUEDEN_MS - 30_000);
    await vaciarPromesas();
    expect(fetchImpl).not.toHaveBeenCalled();
    reloj.avanzar(31_000);
    await vaciarPromesas();
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(terminar).not.toHaveBeenCalled();

    // Sin actividad: el aviso sale un minuto antes del fin del token renovado.
    reloj.avanzar(QUINCE_MINUTOS - AVISAR_CUANDO_QUEDEN_MS - 1_000);
    await vaciarPromesas();
    expect(avisos).toHaveLength(0);
    reloj.avanzar(1_000);
    await vaciarPromesas();
    expect(avisos).toHaveLength(1);
    expect(avisos[0].restanteMs).toBeGreaterThan(AVISAR_CUANDO_QUEDEN_MS - 2_000);
    expect(avisos[0].restanteMs).toBeLessThanOrEqual(AVISAR_CUANDO_QUEDEN_MS);

    reloj.avanzar(AVISAR_CUANDO_QUEDEN_MS);
    await vaciarPromesas();
    expect(terminar).toHaveBeenCalledWith(MOTIVOS.INACTIVIDAD);
  });

  test('el desfase se vuelve a medir con cada token renovado', async () => {
    const reloj = crearReloj();
    const { emitir, fetchImpl } = servidorAtrasado(reloj);
    guardarSesion(
      { token: emitir(), apodo: 'ana_admin', rol: 'ADMINISTRADOR', recibidoEn: reloj.ahora() },
      sessionStorage,
    );
    const { guarda, documento } = montar(reloj, { fetchImpl });

    // Una hora de trabajo con el reloj descuadrado: renueva siempre a tiempo.
    for (let minuto = 0; minuto < 60; minuto += 1) {
      reloj.avanzar(60_000);
      documento.dispatchEvent(new Event('keydown'));
      await vaciarPromesas();
      expect(leerSesion(sessionStorage, reloj.ahora).autenticado).toBe(true);
    }
    expect(fetchImpl.mock.calls.length).toBeGreaterThanOrEqual(5);
    expect(JSON.parse(sessionStorage.getItem(CLAVES.desfase)).ms).toBeGreaterThanOrEqual(
      TRES_HORAS,
    );
    guarda.detener();
  });
});

describe('una renovación rechazada', () => {
  test('un 401 cierra en el acto: la sesión ya no vale', async () => {
    const reloj = crearReloj();
    conSesion(reloj, { vidaMs: 4 * 60_000 });
    const fetchImpl = jest.fn(async () => ({ ok: false, status: 401, json: async () => ({}) }));
    const { terminar } = montar(reloj, { fetchImpl });

    reloj.avanzar(15_000);
    await vaciarPromesas();

    expect(fetchImpl).toHaveBeenCalled();
    expect(terminar).toHaveBeenCalledWith(MOTIVOS.CADUCADA);
  });
});

describe('renovarSesion', () => {
  test.each([
    [{ ok: true, status: 200, json: async () => ({ token: 'nuevo' }) }, RENOVACION.RENOVADA],
    [{ ok: false, status: 401, json: async () => ({}) }, RENOVACION.RECHAZADA],
    [{ ok: false, status: 403, json: async () => ({}) }, RENOVACION.RECHAZADA],
    [{ ok: false, status: 503, json: async () => ({}) }, RENOVACION.SIN_RESPUESTA],
    [{ ok: true, status: 200, json: async () => ({}) }, RENOVACION.SIN_RESPUESTA],
  ])('%#: responde %s', async (respuesta, esperado) => {
    const resultado = await renovarSesion('viejo', async () => respuesta);
    expect(resultado.estado).toBe(esperado);
  });

  test('una red caída no es un rechazo', async () => {
    const resultado = await renovarSesion('viejo', async () => {
      throw new TypeError('Failed to fetch');
    });
    expect(resultado.estado).toBe(RENOVACION.SIN_RESPUESTA);
  });

  test('sin token no se llama a nadie', async () => {
    const fetchImpl = jest.fn();
    expect((await renovarSesion(null, fetchImpl)).estado).toBe(RENOVACION.SIN_RESPUESTA);
    expect(fetchImpl).not.toHaveBeenCalled();
  });
});

describe('el aviso en pantalla', () => {
  test('es un diálogo con título, cuenta atrás y las dos salidas por teclado', () => {
    const alSeguir = jest.fn();
    const alSalir = jest.fn();
    const aviso = mostrarAvisoDeInactividad({ restanteMs: 59_400, alSeguir, alSalir });

    const caja = document.querySelector('[role="dialog"]');
    expect(caja.dataset.zona).toBe('aviso-de-inactividad');
    expect(caja.querySelector('h2').textContent).toBe('Tu sesión está por cerrarse');
    expect(caja.textContent).toContain('1:00');
    aviso.actualizar(9_000);
    expect(caja.textContent).toContain('0:09');
    expect(document.activeElement.dataset.accion).toBe('seguir-conectado');

    caja.querySelector('[data-accion="seguir-conectado"]').click();
    expect(alSeguir).toHaveBeenCalledTimes(1);
    expect(alSalir).not.toHaveBeenCalled();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });

  test('Escape también es «sigo aquí»; «Cerrar sesión» no lo es', () => {
    const alSeguir = jest.fn();
    const alSalir = jest.fn();
    mostrarAvisoDeInactividad({ restanteMs: 30_000, alSeguir, alSalir });
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(alSeguir).toHaveBeenCalledTimes(1);

    mostrarAvisoDeInactividad({ restanteMs: 30_000, alSeguir, alSalir });
    document.querySelector('[data-accion="cerrar-sesion"]').click();
    expect(alSalir).toHaveBeenCalledTimes(1);
    expect(alSeguir).toHaveBeenCalledTimes(1);
  });

  test('tiempoRestante se lee de un vistazo', () => {
    expect(tiempoRestante(60_000)).toBe('1:00');
    expect(tiempoRestante(59_001)).toBe('1:00');
    expect(tiempoRestante(9_000)).toBe('0:09');
    expect(tiempoRestante(-5)).toBe('0:00');
  });
});
