/**
 * Los códigos que llegan por correo — B1.
 *
 * Lo que se prueba: que un código pegado de cualquier manera llegue al
 * servidor como él lo espera; que el enlace del correo se lea sin perder un
 * `+` del correo y se borre de la barra; y que un rechazo se lea por su `type`,
 * nunca por su texto.
 */

import { jest } from '@jest/globals';

import {
  CLAVES_DEL_CORREO,
  CODIGO_MAXIMO,
  PROBLEMAS_DEL_CODIGO,
  detalleDelProblema,
  leerEnlaceDelCorreo,
  limpiarFragmento,
  motivoDelCodigo,
  normalizarCodigo,
  rechazoDelCodigo,
  recordado,
  recordar,
  tipoDelProblema,
} from './codigo-de-correo.js';

const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';

beforeEach(() => {
  sessionStorage.clear();
});

describe('normalizarCodigo', () => {
  test.each([
    ['en minúsculas', 'k7qx2m9p'],
    ['con espacios alrededor', '  K7QX2M9P  '],
    ['partido con un guion', 'K7QX-2M9P'],
    ['partido con un espacio', 'K7QX 2M9P'],
    ['con un guion largo', 'k7qx–2m9p'],
    ['con un espacio que no se ve', 'K7QX 2M9P'],
    ['letra a letra', 'K 7 Q X 2 M 9 P'],
  ])('lo acepta %s', (_caso, entrada) => {
    expect(normalizarCodigo(entrada)).toBe('K7QX2M9P');
  });

  test('si se pega el enlace entero del correo, se saca el código de él', () => {
    expect(
      normalizarCodigo('http://nexus.test/verificar#codigo=k7qx2m9p&correo=ana%40nexus.test'),
    ).toBe('K7QX2M9P');
  });

  test('sin nada escrito no inventa un código', () => {
    expect(normalizarCodigo(null)).toBe('');
    expect(normalizarCodigo(undefined)).toBe('');
    expect(normalizarCodigo('   ')).toBe('');
  });
});

describe('motivoDelCodigo', () => {
  test('vacío o fuera de los límites del contrato no se envía', () => {
    expect(motivoDelCodigo('')).toMatch(/Escribe el código/);
    expect(motivoDelCodigo('K7QX')).toMatch(/8 caracteres/);
    expect(motivoDelCodigo('A'.repeat(CODIGO_MAXIMO + 1))).toMatch(/8 caracteres/);
  });

  test('dentro de los límites, decide el servidor', () => {
    expect(motivoDelCodigo('K7QX2M9P')).toBeNull();
    // Los códigos de activación anteriores tenían diez: siguen entrando.
    expect(motivoDelCodigo('82AEN4P56Z')).toBeNull();
  });
});

describe('leerEnlaceDelCorreo', () => {
  test('lee el código y el correo del fragmento', () => {
    expect(leerEnlaceDelCorreo('#codigo=k7qx2m9p&correo=ana%40nexus.test')).toEqual({
      codigo: 'K7QX2M9P',
      correo: 'ana@nexus.test',
    });
  });

  test('un + del correo sigue siendo un +, venga codificado o no', () => {
    expect(leerEnlaceDelCorreo('#codigo=K7QX2M9P&correo=ana%2Bnexus%40nexus.test').correo).toBe(
      'ana+nexus@nexus.test',
    );
    expect(leerEnlaceDelCorreo('#codigo=K7QX2M9P&correo=ana+nexus@nexus.test').correo).toBe(
      'ana+nexus@nexus.test',
    );
  });

  test('sin fragmento, o sin nada de lo suyo, no hay enlace', () => {
    expect(leerEnlaceDelCorreo('')).toBeNull();
    expect(leerEnlaceDelCorreo(null)).toBeNull();
    expect(leerEnlaceDelCorreo('#avisos')).toBeNull();
    expect(leerEnlaceDelCorreo('#otra=1')).toBeNull();
  });

  test('un fragmento mal codificado no rompe la vista', () => {
    expect(() => leerEnlaceDelCorreo('#codigo=%E0%A4%A&correo=%')).not.toThrow();
  });

  test('con solo el correo, lo devuelve y deja el código vacío', () => {
    expect(leerEnlaceDelCorreo('#correo=ana%40nexus.test')).toEqual({
      codigo: '',
      correo: 'ana@nexus.test',
    });
  });
});

describe('limpiarFragmento', () => {
  test('borra el fragmento sin recargar y con la ruta absoluta', () => {
    const historial = { state: { x: 1 }, replaceState: jest.fn() };
    limpiarFragmento({
      historial,
      ubicacion: { hash: '#codigo=K7QX2M9P', pathname: '/verificar', search: '?motivo=registro' },
    });
    expect(historial.replaceState).toHaveBeenCalledWith({ x: 1 }, '', '/verificar?motivo=registro');
  });

  test('sin fragmento no toca el historial', () => {
    const historial = { state: null, replaceState: jest.fn() };
    limpiarFragmento({ historial, ubicacion: { hash: '', pathname: '/verificar', search: '' } });
    expect(historial.replaceState).not.toHaveBeenCalled();
  });
});

describe('recordar / recordado', () => {
  test('guarda en la pestaña y borra con un valor vacío', () => {
    recordar(CLAVES_DEL_CORREO.porVerificar, 'ana@nexus.test');
    expect(recordado(CLAVES_DEL_CORREO.porVerificar)).toBe('ana@nexus.test');
    recordar(CLAVES_DEL_CORREO.porVerificar, null);
    expect(recordado(CLAVES_DEL_CORREO.porVerificar)).toBeNull();
  });

  test('sin almacenamiento no falla', () => {
    const roto = {
      getItem: () => {
        throw new Error('bloqueado');
      },
      setItem: () => {
        throw new Error('bloqueado');
      },
      removeItem: () => {
        throw new Error('bloqueado');
      },
    };
    expect(() => recordar('x', 'y', roto)).not.toThrow();
    expect(recordado('x', roto)).toBeNull();
  });
});

describe('tipoDelProblema y detalleDelProblema', () => {
  test('el motivo sale del type, no del texto', () => {
    expect(
      tipoDelProblema({ type: `${ERRORES}codigo-invalido`, detail: 'Cualquier redacción' }),
    ).toBe('codigo-invalido');
    expect(tipoDelProblema({ type: 'about:blank' })).toBeNull();
    expect(tipoDelProblema({ type: 'https://otro.sitio/errors/codigo-invalido' })).toBeNull();
    expect(tipoDelProblema('texto plano')).toBeNull();
    expect(tipoDelProblema(null)).toBeNull();
  });

  test('el detalle, venga como problem details o como texto plano', () => {
    expect(detalleDelProblema({ detail: ' El código no es válido. ' })).toBe(
      'El código no es válido.',
    );
    expect(detalleDelProblema('Texto plano')).toBe('Texto plano');
    expect(detalleDelProblema({ title: 'sin detalle' })).toBeNull();
    expect(detalleDelProblema('')).toBeNull();
  });
});

describe('rechazoDelCodigo', () => {
  test('código inválido, caducado o usado: un solo mensaje, sin decir cuál de los tres', () => {
    const rechazo = rechazoDelCodigo(PROBLEMAS_DEL_CODIGO.CODIGO_INVALIDO, 400);
    expect(rechazo.titulo).toBe('El código no es válido');
    expect(rechazo.pedirOtro).toBe(true);
  });

  test('anulado tras cinco intentos: hay que pedir otro', () => {
    const rechazo = rechazoDelCodigo(PROBLEMAS_DEL_CODIGO.DEMASIADOS_INTENTOS, 429);
    expect(rechazo.titulo).toBe('Ese código ya no sirve');
    expect(rechazo.detalle).toMatch(/Pide un código nuevo/);
  });

  test('un 429 sin type es el límite del borde, no el del código', () => {
    const rechazo = rechazoDelCodigo(null, 429);
    expect(rechazo.titulo).toBe('Demasiadas solicitudes seguidas');
    expect(rechazo.pedirOtro).toBe(false);
  });

  test('cualquier otra cosa no es un rechazo del código', () => {
    expect(rechazoDelCodigo(null, 400)).toBeNull();
    expect(rechazoDelCodigo('respuestas-incorrectas', 422)).toBeNull();
    expect(rechazoDelCodigo(null, 500)).toBeNull();
  });
});
