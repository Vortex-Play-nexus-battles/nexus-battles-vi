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
    // UX-R3.11 — el respaldo nombraba UN informe concreto («…el informe de
    // latencia»). Esta clase la comparten el panel de latencia y el tablero
    // tecnico, asi que la tarjeta de «Usuarios y moderacion» anunciaba un
    // fallo de latencia. El respaldo no nombra informe: lo nombra quien pinta.
    super(problema?.detail || problema?.title || 'El servicio de métricas no respondió.');
    this.name = 'ErrorDeMetricas';
    this.tipo = problema?.type ?? null;
    this.titulo = problema?.title ?? 'No se pudo obtener el informe';
    this.estado = estado;
    this.variable = problema?.variable ?? null;
    this.criterio = problema?.criterio ?? null;
    this.muestrasAcumuladas = problema?.muestrasAcumuladas ?? null;
  }

  /**
   * HU-MET-001 (#527): la observabilidad del bloque es de administracion. Un
   * 401 (sesion caducada) o un 403 (rol insuficiente) no son un fallo del
   * servicio y no se pintan como tal: no hay nada que reintentar.
   *
   * @returns {boolean}
   */
  esFaltaDePermiso() {
    return this.estado === 401 || this.estado === 403;
  }

  /** Titulo y detalle en lenguaje de persona para ese caso. */
  get avisoDePermiso() {
    return this.estado === 401
      ? {
          titulo: 'Tu sesión ya no es válida',
          detalle: 'Vuelve a entrar para consultar la observabilidad de la plataforma.',
        }
      : {
          titulo: 'Esta sección es de administración',
          detalle:
            'El estado técnico de la plataforma y los agregados de moderación solo los ve ' +
            'un administrador. Si crees que deberias verlos, pidelo al equipo.',
        };
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
