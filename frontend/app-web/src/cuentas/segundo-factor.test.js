/**
 * Verificación en dos pasos — lo que comparten el login y «Mi cuenta»
 * (HU-AUT-007, ms-identidad-auth 2.2.0): las rutas y los cuerpos del
 * contrato, la forma de los códigos, los mensajes de cada rechazo por su
 * `type` y las dos piezas de interfaz (la clave para la aplicación y los
 * códigos de recuperación).
 */

import { jest } from '@jest/globals';

import {
  FalloDelSegundoFactor,
  PROBLEMAS_DEL_SEGUNDO_FACTOR,
  RUTAS_SEGUNDO_FACTOR,
  activarConDesafio,
  activarSegundoFactor,
  canjearDesafio,
  consultarSegundoFactor,
  copiarTexto,
  desactivarSegundoFactor,
  descargarTexto,
  enrolarConDesafio,
  esCodigoDeAplicacion,
  iniciarEnrolamiento,
  limpiarCodigo,
  motivoDelCodigo,
  motivoDelCodigoDeRecuperacion,
  nombreDelArchivo,
  pintarCodigosDeRecuperacion,
  pintarSecreto,
  rechazoDelSegundoFactor,
  secretoLegible,
  textoDeCodigos,
} from './segundo-factor.js';

const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';

function respuesta(estado, cuerpo) {
  return Promise.resolve({
    ok: estado >= 200 && estado < 300,
    status: estado,
    text: async () => (cuerpo === undefined ? '' : JSON.stringify(cuerpo)),
  });
}

const ENROLAMIENTO = {
  secreto: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
  uriOtpauth:
    'otpauth://totp/Nexus%20Battles%20VI:ana%40nexus.test?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=Nexus%20Battles%20VI&algorithm=SHA1&digits=6&period=30',
  emisor: 'Nexus Battles VI',
  cuenta: 'ana@nexus.test',
  algoritmo: 'SHA1',
  digitos: 6,
  periodoSegundos: 30,
};

const CODIGOS = ['K7QX2-M9PRT', '4HNZW-8CVBE', 'ABCDE-FGHJK'];

beforeEach(() => {
  document.body.replaceChildren();
});

// ------------------------------------------------------------------ cliente

describe('cliente del contrato 2.2.0', () => {
  test('las siete rutas, escritas enteras (el guardián de rutas las lee)', () => {
    expect(RUTAS_SEGUNDO_FACTOR).toEqual({
      estado: '/api/v1/auth/segundo-factor',
      enrolamiento: '/api/v1/auth/segundo-factor/enrolamiento',
      activacion: '/api/v1/auth/segundo-factor/activacion',
      desactivacion: '/api/v1/auth/segundo-factor/desactivacion',
      segundoPaso: '/api/v1/auth/login/segundo-factor',
      enrolamientoObligatorio: '/api/v1/auth/login/segundo-factor/enrolamiento',
      activacionObligatoria: '/api/v1/auth/login/segundo-factor/activacion',
    });
  });

  test('estado: GET sin caché', async () => {
    const fetchImpl = jest.fn(() => respuesta(200, { activo: false }));
    expect(await consultarSegundoFactor({ fetchImpl })).toEqual({ activo: false });
    const [ruta, opciones] = fetchImpl.mock.calls[0];
    expect(ruta).toBe('/api/v1/auth/segundo-factor');
    expect(opciones.method).toBeUndefined();
    expect(opciones.cache).toBe('no-store');
  });

  test('enrolamiento: POST sin cuerpo; activación: POST con el código limpio', async () => {
    const fetchImpl = jest.fn(() => respuesta(200, ENROLAMIENTO));
    expect(await iniciarEnrolamiento({ fetchImpl })).toEqual(ENROLAMIENTO);
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/auth/segundo-factor/enrolamiento');
    expect(fetchImpl.mock.calls[0][1].method).toBe('POST');
    expect(fetchImpl.mock.calls[0][1].body).toBeUndefined();

    const activar = jest.fn(() => respuesta(200, { activo: true, codigosRecuperacion: CODIGOS }));
    expect(await activarSegundoFactor(' 287 082 ', { fetchImpl: activar })).toEqual({
      activo: true,
      codigosRecuperacion: CODIGOS,
    });
    expect(activar.mock.calls[0][0]).toBe('/api/v1/auth/segundo-factor/activacion');
    expect(JSON.parse(activar.mock.calls[0][1].body)).toEqual({ codigo: '287082' });
  });

  test('desactivación: seis cifras van como código; lo demás, como código de recuperación', async () => {
    const fetchImpl = jest.fn(() => respuesta(204));
    expect(
      await desactivarSegundoFactor(
        { passwordActual: 'Clave-1', codigo: '287 082' },
        { fetchImpl },
      ),
    ).toBeNull();
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/auth/segundo-factor/desactivacion');
    expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual({
      passwordActual: 'Clave-1',
      codigo: '287082',
    });

    await desactivarSegundoFactor(
      { passwordActual: 'Clave-1', codigo: 'k7qx2-m9prt' },
      { fetchImpl },
    );
    expect(JSON.parse(fetchImpl.mock.calls[1][1].body)).toEqual({
      passwordActual: 'Clave-1',
      codigoRecuperacion: 'K7QX2-M9PRT',
    });
  });

  test('segundo paso del login: sin sesión, con el desafío y uno de los dos códigos', async () => {
    const fetchImpl = jest.fn(() => respuesta(200, { token: 't', apodo: 'ana' }));
    await canjearDesafio({ desafio: 'd', codigo: '287082' }, { fetchImpl });
    const [ruta, opciones] = fetchImpl.mock.calls[0];
    expect(ruta).toBe('/api/v1/auth/login/segundo-factor');
    expect(opciones.method).toBe('POST');
    expect(opciones.headers.Accept).toContain('application/problem+json');
    expect(opciones.headers.Authorization).toBeUndefined();
    expect(JSON.parse(opciones.body)).toEqual({ desafio: 'd', codigo: '287082' });

    await canjearDesafio({ desafio: 'd', codigoRecuperacion: ' k7qx2 m9prt ' }, { fetchImpl });
    expect(JSON.parse(fetchImpl.mock.calls[1][1].body)).toEqual({
      desafio: 'd',
      codigoRecuperacion: 'K7QX2-M9PRT',
    });
  });

  test('enrolamiento obligatorio sin sesión: secreto con el desafío y activación con el código', async () => {
    const fetchImpl = jest.fn(() => respuesta(200, ENROLAMIENTO));
    await enrolarConDesafio('d', { fetchImpl });
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/auth/login/segundo-factor/enrolamiento');
    expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual({ desafio: 'd' });

    await activarConDesafio({ desafio: 'd', codigo: '123 456' }, { fetchImpl });
    expect(fetchImpl.mock.calls[1][0]).toBe('/api/v1/auth/login/segundo-factor/activacion');
    expect(JSON.parse(fetchImpl.mock.calls[1][1].body)).toEqual({ desafio: 'd', codigo: '123456' });
  });

  test('un rechazo llega con su estado, su type y su detalle', async () => {
    const error = await canjearDesafio(
      { desafio: 'd', codigo: '000000' },
      {
        fetchImpl: () =>
          respuesta(401, {
            type: `${ERRORES}codigo-segundo-factor-invalido`,
            status: 401,
            detail: 'El código no es válido.',
          }),
      },
    ).catch((e) => e);
    expect(error).toBeInstanceOf(FalloDelSegundoFactor);
    expect(error.estado).toBe(401);
    expect(error.tipo).toBe(PROBLEMAS_DEL_SEGUNDO_FACTOR.CODIGO_INVALIDO);
    expect(error.detalle).toBe('El código no es válido.');
  });
});

// ------------------------------------------------------------------ códigos

describe('forma de los códigos', () => {
  test('el de la aplicación: seis cifras, con espacios o guiones tolerados', () => {
    expect(limpiarCodigo(' 287-082 ')).toBe('287082');
    expect(esCodigoDeAplicacion('287 082')).toBe(true);
    expect(esCodigoDeAplicacion('28708')).toBe(false);
    expect(esCodigoDeAplicacion('K7QX2M9PRT')).toBe(false);
    expect(motivoDelCodigo('287082')).toBeNull();
    expect(motivoDelCodigo('')).toMatch(/6 dígitos/);
    expect(motivoDelCodigo('12345a')).toMatch(/6 dígitos/);
  });

  test('el de recuperación: diez caracteres del alfabeto, sin importar mayúsculas ni guion', () => {
    expect(motivoDelCodigoDeRecuperacion('k7qx2-m9prt')).toBeNull();
    expect(motivoDelCodigoDeRecuperacion('')).toMatch(/código de recuperación/);
    expect(motivoDelCodigoDeRecuperacion('K7QX2')).toMatch(/10 caracteres/);
  });

  test('la clave, en grupos de cuatro para copiarla a mano', () => {
    expect(secretoLegible('JBSWY3DPEHPK3PXP')).toBe('JBSW Y3DP EHPK 3PXP');
    expect(secretoLegible('')).toBe('');
  });
});

// --------------------------------------------------------- guardar códigos

describe('guardar los códigos de recuperación', () => {
  test('el archivo dice qué son, de qué cuenta, y trae todos', () => {
    const texto = textoDeCodigos(CODIGOS, {
      cuenta: 'ana@nexus.test',
      fecha: new Date('2026-10-05T15:00:00Z'),
    });
    expect(texto).toContain('Nexus Battles VI');
    expect(texto).toContain('ana@nexus.test');
    expect(texto).toMatch(/una sola vez/);
    for (const codigo of CODIGOS) {
      expect(texto).toContain(codigo);
    }
    expect(nombreDelArchivo(new Date('2026-10-05T15:00:00Z'))).toBe(
      'nexus-battles-codigos-de-recuperacion-2026-10-05.txt',
    );
  });

  test('descargar: un enlace temporal con el nombre del archivo, y se libera', () => {
    const enlaces = [];
    const url = { createObjectURL: jest.fn(() => 'blob:codigos'), revokeObjectURL: jest.fn() };
    const clic = jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
      enlaces.push({ href: this.getAttribute('href'), descarga: this.download });
    });
    jest.useFakeTimers();
    descargarTexto('contenido', 'codigos.txt', { documento: document, url });
    jest.runAllTimers();
    jest.useRealTimers();
    clic.mockRestore();

    expect(enlaces).toEqual([{ href: 'blob:codigos', descarga: 'codigos.txt' }]);
    expect(url.revokeObjectURL).toHaveBeenCalledWith('blob:codigos');
    expect(document.querySelector('a[download]')).toBeNull();
  });

  test('copiar: dice si se pudo, y sin portapapeles no finge', async () => {
    const portapapeles = { writeText: jest.fn(() => Promise.resolve()) };
    expect(await copiarTexto('x', { portapapeles })).toBe(true);
    expect(portapapeles.writeText).toHaveBeenCalledWith('x');
    expect(await copiarTexto('x', { portapapeles: null })).toBe(false);
    expect(
      await copiarTexto('x', {
        portapapeles: { writeText: () => Promise.reject(new Error('no')) },
      }),
    ).toBe(false);
  });
});

// ------------------------------------------------------------------ rechazos

describe('qué decir ante cada rechazo, por su type', () => {
  const fallo = (estado, tipo, detalle = null) =>
    new FalloDelSegundoFactor({ estado, tipo, detalle });

  test('código incorrecto en el acceso: explica el reloj y que cuenta como intento', () => {
    const rechazo = rechazoDelSegundoFactor(
      fallo(401, PROBLEMAS_DEL_SEGUNDO_FACTOR.CODIGO_INVALIDO),
      { contexto: 'acceso' },
    );
    expect(rechazo.titulo).toBe('El código no es válido');
    expect(rechazo.detalle).toMatch(/30 segundos/);
    expect(rechazo.detalle).toMatch(/bloquea/);
    expect(rechazo.campo).toBe('codigo');
    expect(rechazo.volverAEmpezar).toBe(false);
  });

  test('código incorrecto al activar: no amenaza con bloquear (ahí no cuenta)', () => {
    const rechazo = rechazoDelSegundoFactor(
      fallo(422, PROBLEMAS_DEL_SEGUNDO_FACTOR.CODIGO_INVALIDO),
      { contexto: 'activacion' },
    );
    expect(rechazo.detalle).toMatch(/hora de tu teléfono/);
    expect(rechazo.detalle).not.toMatch(/bloquea/);
  });

  test('desafío caducado: hay que volver a empezar con la contraseña', () => {
    const rechazo = rechazoDelSegundoFactor(
      fallo(401, PROBLEMAS_DEL_SEGUNDO_FACTOR.DESAFIO_INVALIDO),
      { contexto: 'acceso' },
    );
    expect(rechazo.titulo).toBe('Tu verificación caducó');
    expect(rechazo.volverAEmpezar).toBe(true);
  });

  test('bloqueo, servicio sin clave, contraseña incorrecta, ya activo, no activo', () => {
    expect(
      rechazoDelSegundoFactor(fallo(423, PROBLEMAS_DEL_SEGUNDO_FACTOR.CUENTA_BLOQUEADA)).titulo,
    ).toBe('Cuenta bloqueada temporalmente');
    expect(
      rechazoDelSegundoFactor(fallo(503, PROBLEMAS_DEL_SEGUNDO_FACTOR.NO_DISPONIBLE), {
        contexto: 'acceso',
      }).detalle,
    ).toMatch(/código de recuperación/);
    const clave = rechazoDelSegundoFactor(
      fallo(422, PROBLEMAS_DEL_SEGUNDO_FACTOR.CONTRASENA_INCORRECTA),
    );
    expect(clave.titulo).toBe('La contraseña actual es incorrecta');
    expect(clave.campo).toBe('passwordActual');
    expect(rechazoDelSegundoFactor(fallo(409, PROBLEMAS_DEL_SEGUNDO_FACTOR.YA_ACTIVO)).titulo).toBe(
      'La verificación en dos pasos ya está activa',
    );
    expect(rechazoDelSegundoFactor(fallo(409, PROBLEMAS_DEL_SEGUNDO_FACTOR.NO_ACTIVO)).titulo).toBe(
      'La verificación en dos pasos no está activa',
    );
    expect(
      rechazoDelSegundoFactor(fallo(409, PROBLEMAS_DEL_SEGUNDO_FACTOR.SIN_ENROLAMIENTO)).titulo,
    ).toBe('Primero genera la clave para tu aplicación');
  });

  test('una cuenta que ya no puede entrar (403): el detalle del servidor y volver a empezar', () => {
    const rechazo = rechazoDelSegundoFactor(
      fallo(403, 'cuenta-suspendida', 'Cuenta suspendida. Tiempo restante: 60 minutos.'),
      { contexto: 'acceso' },
    );
    expect(rechazo.titulo).toBe('Esta cuenta no puede entrar ahora');
    expect(rechazo.detalle).toBe('Cuenta suspendida. Tiempo restante: 60 minutos.');
    expect(rechazo.volverAEmpezar).toBe(true);
  });

  test('sin red o con el servidor caído, nunca su texto', () => {
    expect(rechazoDelSegundoFactor(new TypeError('fallo de red')).titulo).toBe(
      'No pudimos comprobarlo',
    );
    const caido = rechazoDelSegundoFactor(fallo(502, null, '502 Bad Gateway'));
    expect(caido.tono).toBe('error');
    expect(caido.detalle).not.toMatch(/Gateway/);
  });
});

// ------------------------------------------------------------------ interfaz

describe('la clave para la aplicación', () => {
  test('enlace otpauth para abrirla, la clave en grupos para escribirla y copiarla sin espacios', async () => {
    const contenedor = document.createElement('div');
    document.body.append(contenedor);
    const copiar = jest.fn(() => Promise.resolve(true));

    pintarSecreto(contenedor, ENROLAMIENTO, { copiar });

    const enlace = contenedor.querySelector('[data-zona="enlace-otpauth"]');
    expect(enlace.getAttribute('href')).toBe(ENROLAMIENTO.uriOtpauth);
    expect(enlace.textContent).toBe('Abrir en mi aplicación de autenticación');
    expect(contenedor.querySelector('[data-zona="secreto"]').textContent).toBe(
      'JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP',
    );
    expect(contenedor.textContent).toContain('ana@nexus.test');

    contenedor.querySelector('[data-accion="copiar-secreto"]').click();
    await Promise.resolve();
    await Promise.resolve();
    expect(copiar).toHaveBeenCalledWith('JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP');
    expect(contenedor.querySelector('[data-zona="acuse-secreto"]').textContent).toBe(
      'Clave copiada.',
    );
  });

  test('sin portapapeles lo dice, en vez de decir que copió', async () => {
    const contenedor = document.createElement('div');
    pintarSecreto(contenedor, ENROLAMIENTO, { copiar: () => Promise.resolve(false) });
    contenedor.querySelector('[data-accion="copiar-secreto"]').click();
    await Promise.resolve();
    await Promise.resolve();
    expect(contenedor.querySelector('[data-zona="acuse-secreto"]').textContent).toMatch(/a mano/);
  });
});

describe('los códigos de recuperación', () => {
  test('se ven todos, una sola vez, con copiar, descargar y «Ya los guardé»', async () => {
    const contenedor = document.createElement('div');
    const copiar = jest.fn(() => Promise.resolve(true));
    const descargar = jest.fn();
    const alTerminar = jest.fn();

    pintarCodigosDeRecuperacion(contenedor, CODIGOS, {
      cuenta: 'ana@nexus.test',
      copiar,
      descargar,
      alTerminar,
    });

    const lista = [...contenedor.querySelectorAll('[data-zona="codigo-recuperacion"]')].map(
      (n) => n.textContent,
    );
    expect(lista).toEqual(CODIGOS);
    expect(contenedor.querySelector('.aviso').textContent).toMatch(/No los volveremos a mostrar/);

    contenedor.querySelector('[data-accion="copiar-codigos"]').click();
    await Promise.resolve();
    await Promise.resolve();
    expect(copiar.mock.calls[0][0]).toContain('K7QX2-M9PRT');
    expect(contenedor.querySelector('[data-zona="acuse-codigos"]').textContent).toBe(
      'Códigos copiados.',
    );

    contenedor.querySelector('[data-accion="descargar-codigos"]').click();
    expect(descargar.mock.calls[0][0]).toContain('4HNZW-8CVBE');
    expect(descargar.mock.calls[0][1]).toMatch(/^nexus-battles-codigos-de-recuperacion-.*\.txt$/);

    contenedor.querySelector('[data-accion="codigos-guardados"]').click();
    expect(alTerminar).toHaveBeenCalledTimes(1);
  });

  test('si la descarga no se puede, lo dice', () => {
    const contenedor = document.createElement('div');
    pintarCodigosDeRecuperacion(contenedor, CODIGOS, {
      descargar: () => {
        throw new Error('sin descargas');
      },
      alTerminar: () => {},
    });
    contenedor.querySelector('[data-accion="descargar-codigos"]').click();
    expect(contenedor.querySelector('[data-zona="acuse-codigos"]').textContent).toMatch(
      /no deja guardar/,
    );
  });
});
