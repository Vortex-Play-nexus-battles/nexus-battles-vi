/**
 * Cambios en las políticas de uso — SA-11, §7.3.7: presentación pura y el
 * panel de la consola de parámetros contra un servicio simulado.
 */

import { jest } from '@jest/globals';

import {
  avisoDelCambio,
  ErrorDePoliticas,
  fechaDelAviso,
  leerPublicacion,
  montarPoliticasDeUso,
  POLITICAS,
  tablaDeCambios,
  textoDelRechazo,
} from './politicas-de-uso.js';

const asentar = async () => {
  for (let i = 0; i < 6; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
};

const AHORA = Date.parse('2026-10-14T15:00:00Z');

const PUBLICADO = Object.freeze({
  id: '9d0c6a52-7a59-4d4b-8f3e-2d64c1b2a001',
  politica: 'POLITICA_DE_PRIVACIDAD',
  nombreDeLaPolitica: 'Política de privacidad',
  titulo: 'Datos del chat',
  descripcion: 'Los mensajes del chat se guardan noventa días y luego se borran.',
  vigenteDesde: '2026-10-20T05:00:00Z',
  publicadoEn: '2026-10-14T15:00:00Z',
});

const VALIDO = Object.freeze({
  politica: 'TERMINOS_Y_CONDICIONES',
  titulo: 'Reglas del chat',
  descripcion: 'Los enlaces externos en el chat se ocultan hasta que un moderador los revise.',
  motivo: 'Acuerdo del comité',
  vigenteDesde: '',
});

function servicio(rutas) {
  return jest.fn(async (url, opciones = {}) => {
    const metodo = opciones.method ?? 'GET';
    const clave = `${metodo} ${new URL(url, 'http://x').pathname}`;
    const respuesta = rutas[clave];
    if (!respuesta) {
      throw new Error(`sin ruta simulada para ${clave}`);
    }
    const { estado = 200, cuerpo = null } =
      typeof respuesta === 'function' ? respuesta(opciones) : respuesta;
    return { ok: estado >= 200 && estado < 300, status: estado, json: async () => cuerpo };
  });
}

function panelDePrueba() {
  return jest.fn(({ titulo, contenido, acciones = [] }) => {
    const caja = document.createElement('div');
    caja.dataset.panelDePrueba = titulo;
    caja.append(...[contenido].flat(), ...acciones);
    document.body.append(caja);
    return { elemento: caja, cerrar: jest.fn(() => caja.remove()) };
  });
}

function montar({ administra = true, rutas = {} } = {}) {
  document.body.innerHTML = '<div data-zona="aviso" hidden></div>';
  const zonaAviso = document.querySelector('[data-zona="aviso"]');
  const fetchImpl = servicio({
    'GET /api/v1/parametros/politicas/cambios': { cuerpo: [PUBLICADO] },
    ...rutas,
  });
  const abrirPanel = panelDePrueba();
  const vista = montarPoliticasDeUso({ administra, zonaAviso, fetchImpl, abrirPanel });
  document.body.append(vista.elemento);
  return { fetchImpl, abrirPanel, vista, zonaAviso };
}

function escribir(formulario, valores) {
  for (const [nombre, valor] of Object.entries(valores)) {
    const control = formulario.querySelector(`[name="${nombre}"]`);
    control.value = valor;
    control.dispatchEvent(new Event('input', { bubbles: true }));
  }
}

describe('presentación (pura)', () => {
  test('la fecha del aviso es la de notificaciones, en hora de Colombia', () => {
    expect(fechaDelAviso('2026-10-20T05:00:00Z')).toBe('20 de octubre de 2026, 00:00');
    expect(fechaDelAviso('2026-01-02T23:30:00Z')).toBe('2 de enero de 2026, 18:30');
    expect(fechaDelAviso('no es una fecha')).toBe('—');
  });

  test('el aviso tal como lo recibe el jugador: título por política, qué cambia y desde cuándo', () => {
    expect(avisoDelCambio(PUBLICADO)).toEqual({
      titulo: 'Actualizamos la política de privacidad',
      cuerpo:
        'Datos del chat. Los mensajes del chat se guardan noventa días y luego se borran. Rige desde el 20 de octubre de 2026, 00:00.',
    });
    expect(
      avisoDelCambio({ ...VALIDO, titulo: '¡Reglas del chat!', descripcion: 'Sin enlaces' }, AHORA),
    ).toEqual({
      titulo: 'Actualizamos los términos y condiciones del servicio',
      cuerpo: '¡Reglas del chat! Sin enlaces. Rige desde el 14 de octubre de 2026, 10:00.',
    });
    expect(avisoDelCambio({}, AHORA).titulo).toBe('Cambio en las políticas de uso');
  });

  test('solo las dos políticas que nombran las causales de sanción (§7.3.2)', () => {
    expect(Object.keys(POLITICAS)).toEqual(['TERMINOS_Y_CONDICIONES', 'POLITICA_DE_PRIVACIDAD']);
  });

  test('leer la publicación: el cuerpo del POST, sin vigencia si no se dio', () => {
    const { cuerpo, valido } = leerPublicacion({ ...VALIDO, titulo: '  Reglas del chat  ' }, AHORA);
    expect(valido).toBe(true);
    expect(cuerpo).toEqual({
      politica: 'TERMINOS_Y_CONDICIONES',
      titulo: 'Reglas del chat',
      descripcion: VALIDO.descripcion,
      motivo: 'Acuerdo del comité',
    });

    const futura = leerPublicacion({ ...VALIDO, vigenteDesde: '2026-10-20T00:00' }, AHORA);
    expect(futura.valido).toBe(true);
    expect(Date.parse(futura.cuerpo.vigenteDesde)).toBe(Date.parse('2026-10-20T00:00'));
  });

  test('cada campo dice lo que le falta, y la vigencia no puede estar en el pasado', () => {
    const { errores, valido } = leerPublicacion(
      { politica: 'NORMAS', titulo: 'Algo', descripcion: 'Muy corto', motivo: 'x' },
      AHORA,
    );
    expect(valido).toBe(false);
    expect(errores.politica).toBe('Elige la política que cambia.');
    expect(errores.titulo).toBe('El título necesita al menos 5 caracteres.');
    expect(errores.descripcion).toBe('Lo que cambia necesita al menos 20 caracteres.');
    expect(errores.motivo).toBe('El motivo necesita al menos 3 caracteres.');

    expect(leerPublicacion({ ...VALIDO, titulo: 'x'.repeat(121) }, AHORA).errores.titulo).toBe(
      'El título admite como mucho 120 caracteres.',
    );
    const pasada = leerPublicacion(
      { ...VALIDO, vigenteDesde: new Date(AHORA - 3600 * 1000).toISOString() },
      AHORA,
    );
    expect(pasada.errores.vigenteDesde).toMatch(/^No puede regir desde el pasado/);
    expect(pasada.cuerpo.vigenteDesde).toBeUndefined();
    const holgura = leerPublicacion(
      { ...VALIDO, vigenteDesde: new Date(AHORA - 2 * 60 * 1000).toISOString() },
      AHORA,
    );
    expect(holgura.errores.vigenteDesde).toBeNull();
    expect(
      leerPublicacion({ ...VALIDO, vigenteDesde: 'mañana' }, AHORA).errores.vigenteDesde,
    ).toMatch(/no se entiende/);
  });

  test('los rechazos en palabras de quien administra, nunca un código', () => {
    expect(textoDelRechazo(new ErrorDePoliticas({ motivo: 'PERMISO_INSUFICIENTE' }, 403))).toBe(
      'Tu cuenta no puede publicar cambios en las políticas de uso.',
    );
    expect(textoDelRechazo(new ErrorDePoliticas(null, 401))).toMatch(/^Tu sesión terminó/);
    expect(
      textoDelRechazo(
        new ErrorDePoliticas(
          { motivo: 'SOLICITUD_INVALIDA', detail: 'La vigencia no puede estar en el pasado.' },
          400,
        ),
      ),
    ).toBe('La vigencia no puede estar en el pasado.');
    const caido = textoDelRechazo(new ErrorDePoliticas({ status: 503 }, 503));
    expect(caido).not.toMatch(/503/);
    expect(textoDelRechazo(new TypeError('Failed to fetch'))).toMatch(/^No pudimos contactar/);
  });

  test('la tabla dice la política por su nombre, el cambio y las dos fechas', () => {
    const tabla = tablaDeCambios([PUBLICADO, { ...PUBLICADO, id: 'otro', nombreDeLaPolitica: '' }]);
    document.body.replaceChildren(tabla);
    const encabezados = [...tabla.querySelectorAll('thead th')].map((th) => th.textContent);
    expect(encabezados).toEqual(['Política', 'Cambio', 'Rige desde', 'Publicado']);
    const filas = tabla.querySelectorAll('tbody tr');
    expect(filas).toHaveLength(2);
    expect(filas[0].textContent).toContain('Política de privacidad');
    expect(filas[0].textContent).toContain('Datos del chat');
    expect(filas[1].textContent).toContain('Política de privacidad');
    expect(tabla.textContent).not.toContain('POLITICA_DE_PRIVACIDAD');
    expect(tabla.textContent).toContain('2 cambios publicados');
  });
});

describe('el panel de la consola', () => {
  test('carga lo publicado y, para quien administra, ofrece publicar', async () => {
    const { fetchImpl } = montar();
    await asentar();
    const panel = document.querySelector('[data-panel="politicas-de-uso"]');
    expect(panel.querySelector('h3').textContent).toBe('Cambios en las políticas de uso');
    expect(panel.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(panel.querySelector('[data-accion="publicar-cambio-de-politica"]')).not.toBeNull();
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  test('sin rol de administración se lee, pero no se publica', async () => {
    montar({ administra: false });
    await asentar();
    expect(document.querySelector('[data-accion="publicar-cambio-de-politica"]')).toBeNull();
    expect(document.querySelectorAll('[data-panel="politicas-de-uso"] tbody tr')).toHaveLength(1);
  });

  test('sin cambios lo dice; si el servicio no responde, lo dice con reintento', async () => {
    montar({ rutas: { 'GET /api/v1/parametros/politicas/cambios': { cuerpo: [] } } });
    await asentar();
    expect(document.body.textContent).toContain('Todavía no se ha publicado ningún cambio');

    let llamadas = 0;
    montar({
      rutas: {
        'GET /api/v1/parametros/politicas/cambios': () => {
          llamadas += 1;
          return llamadas === 1
            ? { estado: 503, cuerpo: { status: 503 } }
            : { cuerpo: [PUBLICADO] };
        },
      },
    });
    await asentar();
    expect(document.body.textContent).toContain('No pudimos cargar los cambios publicados');
    expect(document.body.textContent).not.toMatch(/503/);
    document.querySelector('[data-panel="politicas-de-uso"] [data-accion="reintentar"]').click();
    await asentar();
    expect(document.querySelectorAll('[data-panel="politicas-de-uso"] tbody tr')).toHaveLength(1);
  });

  test('publicar: la vista previa del aviso, lo que va a pasar, motivo obligatorio y nada se envía con errores', async () => {
    const { fetchImpl, abrirPanel } = montar();
    await asentar();
    document.querySelector('[data-accion="publicar-cambio-de-politica"]').click();
    expect(abrirPanel).toHaveBeenCalledTimes(1);
    const formulario = document.querySelector('[data-zona="publicacion"]');
    expect(document.querySelector('[data-zona="consecuencia"]').textContent).toContain(
      'no se puede retirar',
    );

    escribir(formulario, {
      politica: 'POLITICA_DE_PRIVACIDAD',
      titulo: 'Datos del chat',
      descripcion: 'Los mensajes del chat se guardan noventa días y luego se borran.',
    });
    const previa = document.querySelector('[data-zona="vista-previa-del-aviso"]');
    expect(previa.textContent).toContain('Actualizamos la política de privacidad');
    expect(previa.textContent).toContain('Datos del chat. Los mensajes del chat se guardan');
    expect(previa.textContent).toContain('Rige desde el');

    document.querySelector('[data-accion="confirmar-publicacion"]').click();
    await asentar();
    expect(formulario.textContent).toContain('El motivo necesita al menos 3 caracteres.');
    expect(fetchImpl.mock.calls.some(([, o]) => o?.method === 'POST')).toBe(false);
  });

  test('publicar envía el cambio, avisa con lo publicado y recarga la tabla', async () => {
    let cuerpoEnviado = null;
    const { fetchImpl, zonaAviso } = montar({
      rutas: {
        'POST /api/v1/parametros/politicas/cambios': (opciones) => {
          cuerpoEnviado = JSON.parse(opciones.body);
          return { estado: 201, cuerpo: { ...PUBLICADO, id: 'nuevo' } };
        },
      },
    });
    await asentar();
    document.querySelector('[data-accion="publicar-cambio-de-politica"]').click();
    const formulario = document.querySelector('[data-zona="publicacion"]');
    escribir(formulario, {
      politica: 'POLITICA_DE_PRIVACIDAD',
      titulo: 'Datos del chat',
      descripcion: 'Los mensajes del chat se guardan noventa días y luego se borran.',
      motivo: 'Ley de protección de datos',
    });
    document.querySelector('[data-accion="confirmar-publicacion"]').click();
    await asentar();

    expect(cuerpoEnviado).toEqual({
      politica: 'POLITICA_DE_PRIVACIDAD',
      titulo: 'Datos del chat',
      descripcion: 'Los mensajes del chat se guardan noventa días y luego se borran.',
      motivo: 'Ley de protección de datos',
    });
    expect(document.querySelector('[data-zona="publicacion"]')).toBeNull();
    expect(zonaAviso.hidden).toBe(false);
    expect(zonaAviso.textContent).toContain('Cambio publicado');
    expect(zonaAviso.textContent).toContain('próximo ingreso');
    expect(
      fetchImpl.mock.calls.filter(
        ([url, o]) => url.endsWith('/politicas/cambios') && (o?.method ?? 'GET') === 'GET',
      ),
    ).toHaveLength(2);
  });

  test('un rechazo del servicio se dice en el panel, sin cerrarlo', async () => {
    montar({
      rutas: {
        'POST /api/v1/parametros/politicas/cambios': {
          estado: 403,
          cuerpo: { motivo: 'PERMISO_INSUFICIENTE', status: 403 },
        },
      },
    });
    await asentar();
    document.querySelector('[data-accion="publicar-cambio-de-politica"]').click();
    escribir(document.querySelector('[data-zona="publicacion"]'), VALIDO);
    document.querySelector('[data-accion="confirmar-publicacion"]').click();
    await asentar();
    const error = document.querySelector('[data-zona="error-de-la-publicacion"]');
    expect(error.hidden).toBe(false);
    expect(error.textContent).toBe('Tu cuenta no puede publicar cambios en las políticas de uso.');
    expect(document.querySelector('[data-zona="publicacion"]')).not.toBeNull();
    expect(document.querySelector('[data-accion="confirmar-publicacion"]').disabled).toBe(false);
  });
});
