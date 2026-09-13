/**
 * HU-REN-002 (CA-03) y HU-REN-003 — Acceso HTTP al informe de latencia.
 *
 * Habla con `/api/v1/latencia/informe` de
 * `contracts/openapi/metricas-plataforma.yaml`.
 *
 * **No calcula nada.** Los percentiles, el desglose por tipo y el veredicto de
 * cumplimiento los produce el backend, que es donde estan las muestras.
 * Recalcularlos aqui daria dos numeros distintos para la misma pregunta el dia
 * que una de las dos implementaciones cambie.
 *
 * @module plataforma/metricas-plataforma/cliente-metricas
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

/** El `type` que devuelve el backend cuando el PO no ha acordado el percentil. */
export const TIPO_PERCENTIL_NO_ACORDADO =
  'https://nexusbattles.local/errores/percentil-no-acordado';

/**
 * Base de la API. Vacia por omision, es decir mismo origen.
 * @returns {string} base sin barra final
 */
export function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

/**
 * El informe no se pudo obtener, ya interpretado.
 *
 * Se decide por `tipo` y por `estado`, nunca por el texto: MAPEO-ERRORES §2.
 */
export class ErrorDeMetricas extends Error {
  /**
   * @param {{type?: string, title?: string, detail?: string, variable?: string,
   *          criterio?: string, muestrasAcumuladas?: number}} problema
   * @param {number} estado
   */
  constructor(problema, estado) {
    super(problema?.detail || problema?.title || 'No se pudo obtener el informe de latencia.');
    this.name = 'ErrorDeMetricas';
    this.tipo = problema?.type ?? null;
    this.titulo = problema?.title ?? 'No se pudo obtener el informe';
    this.estado = estado;
    this.variable = problema?.variable ?? null;
    this.criterio = problema?.criterio ?? null;
    this.muestrasAcumuladas = problema?.muestrasAcumuladas ?? null;
  }

  /**
   * True cuando el informe no sale porque falta una decision del Product
   * Owner, no porque algo este roto.
   *
   * La distincion importa para la pantalla: un fallo se pinta en rojo y se
   * ofrece reintentar; esto no se arregla reintentando.
   */
  esPercentilNoAcordado() {
    return this.tipo === TIPO_PERCENTIL_NO_ACORDADO;
  }
}

/**
 * Pide el informe de latencia al servicio de metricas.
 *
 * @param {{fetch?: typeof fetch}} [opciones] inyeccion para las pruebas
 * @returns {Promise<object>} el informe tal cual lo publica el contrato
 * @throws {ErrorDeMetricas}
 */
export async function obtenerInforme(opciones = {}) {
  const peticion = opciones.fetch ?? fetchWithHttpErrorInterceptor;
  const respuesta = await peticion(`${baseDeApi()}/api/v1/latencia/informe`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });

  if (!respuesta.ok) {
    let problema = null;
    try {
      problema = await respuesta.json();
    } catch {
      // Sin cuerpo JSON no hay nada que interpretar: se informa el estado.
      problema = null;
    }
    throw new ErrorDeMetricas(problema, respuesta.status);
  }

  return respuesta.json();
}
