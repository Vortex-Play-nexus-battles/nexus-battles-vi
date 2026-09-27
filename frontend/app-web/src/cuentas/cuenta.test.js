/**
 * Pruebas de Mi Cuenta (#567, #569).
 *
 * El defecto #567 fue que la vista quedaba en blanco porque el módulo
 * reventaba buscando elementos que el HTML no tenía. Estas pruebas montan el
 * marcado real de `perfil.html` y comprueban que cada zona se pinta, que un
 * servicio caído se cuenta con palabras y no con un código HTTP, y que el
 * apodo no cambia por accidente al guardar otra cosa.
 */

import { jest } from '@jest/globals';

import {
  comoImporte,
  cuerpoDeActualizacion,
  llenarFormulario,
  montarAccionesDeSesion,
  montarCuenta,
  nombreDelConcepto,
  pintarHistorial,
} from './cuenta.js';

const SESION = { uid: 'u-1', apodo: 'Valkiria', rol: 'JUGADOR' };

/** Marcado equivalente al de `perfil.html`, sin el resto de la página. */
function montarVista() {
  document.body.innerHTML = `
    <div id="raiz">
      <div data-zona="aviso"></div>
      <section data-zona="panel-resumen"><div data-zona="contenido"></div></section>
      <section data-zona="panel-perfil">
        <form id="formulario-perfil">
          <div class="campo"><input name="apodo" /></div>
          <div class="campo"><input name="nombres" /></div>
          <div class="campo"><input name="apellidos" /></div>
          <div class="campo"><textarea name="preferencias"></textarea></div>
          <input type="file" name="avatar" />
          <img data-zona="avatar-previo" hidden alt="" />
          <button type="button" data-accion="descartar-perfil">Descartar</button>
          <button type="submit" data-accion="guardar-perfil">Guardar</button>
        </form>
      </section>
      <section data-zona="panel-historial"><div data-zona="contenido"></div></section>
      <section data-zona="panel-seguridad"><div data-zona="sesion"></div></section>
    </div>`;
  return document.getElementById('raiz');
}

function respuesta(cuerpo, { ok = true, status = 200 } = {}) {
  return Promise.resolve({ ok, status, json: () => Promise.resolve(cuerpo) });
}

/** Devuelve la respuesta que corresponda a cada ruta pedida. */
function fetchFalso(rutas) {
  return jest.fn((url) => {
    for (const [fragmento, valor] of Object.entries(rutas)) {
      if (String(url).includes(fragmento)) {
        return typeof valor === 'function' ? valor(url) : valor();
      }
    }
    return respuesta({}, { ok: false, status: 404 });
  });
}

const PERFIL = {
  apodo: 'Valkiria',
  email: 'val@nexus.test',
  nombres: 'Valeria',
  apellidos: 'Ruiz',
  preferencias: 'Ataques rapidos',
};

beforeEach(() => {
  sessionStorage.clear();
});

describe('comoImporte()', () => {
  test.each([
    ['SUMA', '+', 'suma'],
    ['RESTA', '−', 'resta'],
  ])('%s antepone el signo %s', (signo, prefijo, tono) => {
    const importe = comoImporte({ signo, monto: 1500 });

    expect(importe.texto.startsWith(prefijo)).toBe(true);
    expect(importe.tono).toBe(tono);
  });

  test('APARTA dice que el crédito esta apartado, no restado', () => {
    const importe = comoImporte({ signo: 'APARTA', monto: 500 });

    expect(importe.texto).toContain('apartados');
    expect(importe.tono).toBe('aparta');
  });

  test('un signo desconocido no rompe la fila', () => {
    const importe = comoImporte({ signo: 'LO_QUE_SEA', monto: 10 });

    expect(importe.tono).toBe('neutro');
    expect(importe.texto).toContain('devueltos');
  });
});

describe('nombreDelConcepto()', () => {
  test('traduce los conceptos que escribe el backend', () => {
    expect(nombreDelConcepto('apuesta-sala')).toBe('Apuesta de una batalla');
    expect(nombreDelConcepto('recompensa-victoria')).toBe('Recompensa por ganar');
  });

  test('el abono de bienvenida de una cuenta nueva se lee como tal (R17)', () => {
    expect(nombreDelConcepto('bono-registro')).toBe('Créditos de bienvenida');
  });

  test('un concepto nuevo se muestra tal cual en vez de desaparecer', () => {
    expect(nombreDelConcepto('concepto-que-no-existia')).toBe('concepto-que-no-existia');
  });

  test('sin concepto pone un nombre generico', () => {
    expect(nombreDelConcepto(null)).toBe('Movimiento de créditos');
  });
});

describe('llenarFormulario() y cuerpoDeActualizacion()', () => {
  test('llena los cuatro campos del perfil', () => {
    const raiz = montarVista();
    const formulario = raiz.querySelector('#formulario-perfil');

    llenarFormulario(formulario, PERFIL);

    expect(formulario.elements.apodo.value).toBe('Valkiria');
    expect(formulario.elements.nombres.value).toBe('Valeria');
    expect(formulario.elements.preferencias.value).toBe('Ataques rapidos');
  });

  test('un perfil sin datos deja los campos vacios en vez de poner undefined', () => {
    const raiz = montarVista();
    const formulario = raiz.querySelector('#formulario-perfil');

    llenarFormulario(formulario, null);

    expect(formulario.elements.nombres.value).toBe('');
  });

  test('el apodo NO viaja si no cambio', () => {
    const raiz = montarVista();
    const formulario = raiz.querySelector('#formulario-perfil');
    llenarFormulario(formulario, PERFIL);

    const cuerpo = cuerpoDeActualizacion(formulario, 'Valkiria');

    expect(cuerpo.has('apodo')).toBe(false);
    expect(cuerpo.get('nombres')).toBe('Valeria');
  });

  test('el apodo viaja cuando de verdad cambio', () => {
    const raiz = montarVista();
    const formulario = raiz.querySelector('#formulario-perfil');
    llenarFormulario(formulario, PERFIL);
    formulario.elements.apodo.value = 'Valkiria2';

    expect(cuerpoDeActualizacion(formulario, 'Valkiria').get('apodo')).toBe('Valkiria2');
  });
});

describe('pintarHistorial() — #569', () => {
  test('lista los movimientos de créditos, no solo los pagos', async () => {
    const zona = montarVista().querySelector(
      '[data-zona="panel-historial"] [data-zona="contenido"]',
    );
    const fetchImpl = fetchFalso({
      '/movimientos': () =>
        respuesta({
          content: [
            {
              id: 1,
              concepto: 'apuesta-sala',
              monto: 200,
              signo: 'RESTA',
              estado: 'CONSUMIDA',
              creado: '2026-09-20T10:00:00Z',
            },
            {
              id: 2,
              concepto: 'recompensa-victoria',
              monto: 400,
              signo: 'SUMA',
              estado: 'APLICADO',
              creado: '2026-09-20T10:30:00Z',
            },
          ],
        }),
    });

    await pintarHistorial(zona, { sesion: SESION, fetchImpl });

    const filas = zona.querySelectorAll('[data-movimiento]');
    expect(filas).toHaveLength(2);
    expect(filas[0].textContent).toContain('Apuesta de una batalla');
    expect(filas[1].querySelector('.movimiento__importe').dataset.tono).toBe('suma');
  });

  test('sin movimientos invita a jugar en vez de dejar la tabla vacia', async () => {
    const zona = montarVista().querySelector(
      '[data-zona="panel-historial"] [data-zona="contenido"]',
    );
    const fetchImpl = fetchFalso({ '/movimientos': () => respuesta({ content: [] }) });

    await pintarHistorial(zona, { sesion: SESION, fetchImpl });

    expect(zona.textContent).toContain('Todavía no has movido créditos');
    expect(zona.querySelector('a')).not.toBeNull();
  });

  test('con el servicio caido lo dice con palabras y ofrece reintentar, sin código HTTP', async () => {
    const zona = montarVista().querySelector(
      '[data-zona="panel-historial"] [data-zona="contenido"]',
    );
    const fetchImpl = fetchFalso({
      '/movimientos': () => respuesta({}, { ok: false, status: 502 }),
    });

    await pintarHistorial(zona, { sesion: SESION, fetchImpl });

    expect(zona.textContent).toContain('no está disponible');
    expect(zona.textContent).not.toContain('502');
    expect(zona.querySelector('button')).not.toBeNull();
  });

  test('una respuesta sin content no rompe la vista', async () => {
    const zona = montarVista().querySelector(
      '[data-zona="panel-historial"] [data-zona="contenido"]',
    );
    const fetchImpl = fetchFalso({ '/movimientos': () => respuesta({ content: null }) });

    await pintarHistorial(zona, { sesion: SESION, fetchImpl });

    expect(zona.textContent).toContain('Todavía no has movido créditos');
  });
});

describe('montarCuenta()', () => {
  test('carga el perfil, lo pone en el formulario y pinta el saldo', async () => {
    const raiz = montarVista();
    const fetchImpl = fetchFalso({
      '/perfiles/': () => respuesta(PERFIL),
      '/saldo': () => respuesta({ saldoDisponible: 3200, saldoReservado: 200 }),
      '/movimientos': () => respuesta({ content: [] }),
    });

    const cuenta = montarCuenta(raiz, { sesion: SESION, fetchImpl });
    await cuenta.recargar();

    expect(raiz.querySelector('#formulario-perfil').elements.nombres.value).toBe('Valeria');
    const resumen = raiz.querySelector('[data-zona="panel-resumen"]');
    expect(resumen.textContent).toContain('Créditos disponibles');
    expect(sessionStorage.getItem('nexus.apodoActual')).toBe('Valkiria');
  });

  test('UXC-7 — el resumen dice el estado de la cuenta: en regla, o la suspensión con su cuenta atrás', async () => {
    const hasta = new Date(Date.now() + 2 * 86_400_000).toISOString();
    const raiz = montarVista();
    const fetchImpl = fetchFalso({
      '/perfiles/': () => respuesta(PERFIL),
      '/sanciones/usuarios/u-1': () =>
        respuesta([
          {
            id: 's-1',
            tipo: 'SUSPENSION',
            motivo: 'Lenguaje ofensivo',
            vigenteHasta: hasta,
            vigente: true,
            emitidaEn: new Date().toISOString(),
          },
        ]),
      '/saldo': () => respuesta({ saldoDisponible: 0, saldoReservado: 0 }),
      '/movimientos': () => respuesta({ content: [] }),
    });

    const cuenta = montarCuenta(raiz, { sesion: SESION, fetchImpl });
    await cuenta.recargar();
    await new Promise((r) => setTimeout(r, 0));

    const estado = raiz.querySelector('[data-estado-cuenta]');
    expect(estado.dataset.estadoCuenta).toBe('suspendida');
    expect(estado.textContent).toContain('Suspensión activa');
    expect(estado.textContent).toContain('Motivo: Lenguaje ofensivo');
    expect(estado.querySelector('time.cuenta-atras').getAttribute('datetime')).toBe(hasta);
    expect(estado.querySelector('a').getAttribute('href')).toMatch(/mis-sanciones\.html$/);
  });

  test('UXC-7 — si no se puede consultar, no se dice «en regla»', async () => {
    const raiz = montarVista();
    const fetchImpl = fetchFalso({
      '/perfiles/': () => respuesta(PERFIL),
      '/sanciones/': () => respuesta({}, { ok: false, status: 503 }),
      '/saldo': () => respuesta({ saldoDisponible: 0, saldoReservado: 0 }),
      '/movimientos': () => respuesta({ content: [] }),
    });

    const cuenta = montarCuenta(raiz, { sesion: SESION, fetchImpl });
    await cuenta.recargar();
    await new Promise((r) => setTimeout(r, 0));

    const zona = raiz.querySelector('[data-zona="resumen-estado"]');
    expect(zona.textContent).toContain('No pudimos consultar el estado de tu cuenta');
    expect(zona.textContent).not.toContain('en regla');
  });

  test('si el perfil no carga, avisa sin dejar la vista en blanco (#567)', async () => {
    const raiz = montarVista();
    const fetchImpl = fetchFalso({
      '/perfiles/': () => respuesta({}, { ok: false, status: 503 }),
      '/saldo': () => respuesta({ saldoDisponible: 0, saldoReservado: 0 }),
      '/movimientos': () => respuesta({ content: [] }),
    });

    const cuenta = montarCuenta(raiz, { sesion: SESION, fetchImpl });
    await cuenta.recargar();

    const aviso = raiz.querySelector('[data-zona="aviso"]');
    expect(aviso.textContent).toContain('No pudimos cargar tu perfil');
    expect(aviso.textContent).not.toContain('503');
    // La zona de resumen se pinta igual: una parte caída no tumba la pantalla.
    expect(raiz.querySelector('[data-zona="panel-resumen"]').textContent).not.toBe('');
  });

  test('con los créditos caidos el perfil se sigue viendo', async () => {
    const raiz = montarVista();
    const fetchImpl = fetchFalso({
      '/perfiles/': () => respuesta(PERFIL),
      '/saldo': () => respuesta({}, { ok: false, status: 502 }),
      '/movimientos': () => respuesta({}, { ok: false, status: 502 }),
    });

    const cuenta = montarCuenta(raiz, { sesion: SESION, fetchImpl });
    await cuenta.recargar();

    expect(raiz.querySelector('#formulario-perfil').elements.apodo.value).toBe('Valkiria');
    expect(raiz.querySelector('[data-zona="panel-resumen"]').textContent).toContain(
      'no están disponibles',
    );
  });

  test('guardar sin nombres marca el campo y no llama al backend', async () => {
    const raiz = montarVista();
    const fetchImpl = fetchFalso({
      '/perfiles/': () => respuesta(PERFIL),
      '/saldo': () => respuesta({ saldoDisponible: 0, saldoReservado: 0 }),
      '/movimientos': () => respuesta({ content: [] }),
    });
    const cuenta = montarCuenta(raiz, { sesion: SESION, fetchImpl });
    await cuenta.recargar();
    const formulario = raiz.querySelector('#formulario-perfil');
    formulario.elements.nombres.value = '';
    fetchImpl.mockClear();

    formulario.dispatchEvent(new Event('submit', { cancelable: true }));
    await Promise.resolve();

    expect(formulario.elements.nombres.getAttribute('aria-invalid')).toBe('true');
    expect(fetchImpl).not.toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ method: 'PUT' }),
    );
  });

  test('descartar devuelve el formulario a lo que hay guardado', async () => {
    const raiz = montarVista();
    const fetchImpl = fetchFalso({
      '/perfiles/': () => respuesta(PERFIL),
      '/saldo': () => respuesta({ saldoDisponible: 0, saldoReservado: 0 }),
      '/movimientos': () => respuesta({ content: [] }),
    });
    const cuenta = montarCuenta(raiz, { sesion: SESION, fetchImpl });
    await cuenta.recargar();
    const formulario = raiz.querySelector('#formulario-perfil');
    formulario.elements.nombres.value = 'Otro nombre';

    raiz.querySelector('[data-accion="descartar-perfil"]').click();

    expect(formulario.elements.nombres.value).toBe('Valeria');
  });
});

describe('montarAccionesDeSesion()', () => {
  test('muestra identificador y rol, y el botón de cerrar sesión llama a quien toca', () => {
    const raiz = montarVista();
    const alCerrarSesion = jest.fn();

    montarAccionesDeSesion(raiz, { sesion: SESION, alCerrarSesion });
    raiz.querySelector('[data-accion="cerrar-sesion"]').click();

    const zona = raiz.querySelector('[data-zona="sesion"]');
    expect(zona.textContent).toContain('u-1');
    expect(zona.textContent).toContain('JUGADOR');
    expect(alCerrarSesion).toHaveBeenCalled();
  });

  test('sin la zona en el HTML no revienta', () => {
    document.body.innerHTML = '<div id="raiz"></div>';

    expect(() =>
      montarAccionesDeSesion(document.getElementById('raiz'), {
        sesion: SESION,
        alCerrarSesion() {},
      }),
    ).not.toThrow();
  });
});
