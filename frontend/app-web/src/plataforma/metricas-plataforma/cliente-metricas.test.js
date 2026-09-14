/**
 * HU-REN-002 CA-03 — el cliente del informe de latencia.
 */

import {
  ErrorDeMetricas,
  TIPO_PERCENTIL_NO_ACORDADO,
  baseDeApi,
  obtenerInforme,
} from './cliente-metricas.js';

/** Respuesta minima con lo que usa el modulo. */
function respuesta(ok, status, cuerpo) {
  return {
    ok,
    status,
    json: async () => {
      if (cuerpo === undefined) {
        throw new Error('no es JSON');
      }
      return cuerpo;
    },
  };
}

beforeEach(() => {
  document.head.replaceChildren();
});

describe('base de la API', () => {
  it('por omision es el mismo origen', () => {
    expect(baseDeApi()).toBe('');
  });

  it('la pagina puede apuntar a otro backend', () => {
    const meta = document.createElement('meta');
    meta.name = 'nexus-api-base';
    meta.content = 'http://127.0.0.1:8087/';
    document.head.append(meta);

    expect(baseDeApi()).toBe('http://127.0.0.1:8087');
  });
});

describe('obtener el informe', () => {
  it('devuelve el informe tal cual lo publica el contrato', async () => {
    const esperado = { servicio: 'ms-subastas', porTipo: [] };

    const informe = await obtenerInforme({ fetch: async () => respuesta(true, 200, esperado) });

    expect(informe).toEqual(esperado);
  });

  it('pide la ruta del contrato', async () => {
    const llamadas = [];

    await obtenerInforme({
      fetch: async (url) => {
        llamadas.push(url);
        return respuesta(true, 200, {});
      },
    });

    expect(llamadas[0]).toBe('/api/v1/latencia/informe');
  });

  it('un 409 se reconoce como decision pendiente del Product Owner', async () => {
    const problema = {
      type: TIPO_PERCENTIL_NO_ACORDADO,
      title: 'Percentil de evaluacion no acordado',
      variable: 'LATENCIA_PERCENTIL',
      criterio: 'HU-REN-001 CA-03',
      muestrasAcumuladas: 1284,
    };

    await expect(
      obtenerInforme({ fetch: async () => respuesta(false, 409, problema) }),
    ).rejects.toMatchObject({
      name: 'ErrorDeMetricas',
      estado: 409,
      variable: 'LATENCIA_PERCENTIL',
      muestrasAcumuladas: 1284,
    });
  });

  it('se decide por el type, no por el texto', async () => {
    // MAPEO-ERRORES §2: title y detail pueden reescribirse sin aviso.
    const impostor = {
      type: 'https://nexusbattles.local/errores/otra-cosa',
      title: 'Percentil de evaluacion no acordado',
    };

    try {
      await obtenerInforme({ fetch: async () => respuesta(false, 409, impostor) });
      throw new Error('deberia haber lanzado');
    } catch (error) {
      expect(error).toBeInstanceOf(ErrorDeMetricas);
      expect(error.esPercentilNoAcordado()).toBe(false);
    }
  });

  it('un 500 sin cuerpo JSON no rompe el cliente', async () => {
    await expect(
      obtenerInforme({ fetch: async () => respuesta(false, 500, undefined) }),
    ).rejects.toMatchObject({ name: 'ErrorDeMetricas', estado: 500 });
  });
});
