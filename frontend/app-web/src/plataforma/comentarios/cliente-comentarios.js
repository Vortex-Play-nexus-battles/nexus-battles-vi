/**
 * HU-COM-001 — Acceso HTTP a la publicacion de comentarios sobre productos.
 *
 * Habla con `POST /api/v1/products/{productId}/comments` tal como lo define
 * `contracts/openapi/comentarios.yaml` (PR #197, servicio
 * `services/plataforma/comentarios`). Este modulo NO define nada que el
 * contrato no diga: si el contrato cambia, cambia aqui.
 *
 * Tres respuestas distintas, porque no son lo mismo:
 *
 *   - 201 -> el comentario ya esta en el hilo del producto (`PUBLICADO`)
 *   - 202 -> el filtro lo retuvo; se guardo y espera a un moderador (`EN_REVISION`)
 *   - 4xx -> no se guardo; llega un problem details (RFC 7807) con `motivo`
 *            cuando el rechazo es por silencio (403) o por formato de imagen (422)
 *
 * El contrato dice que el autor y su apodo viajan en el cuerpo mientras el
 * modulo de identidad no acuerde que claim del token los aporta. Aqui se
 * respeta tal cual: el dia que cambie, abrira una version nueva del contrato.
 *
 * Todo error sale de aqui como `ErrorDeApi`, para que la vista decida por
 * `tipo`, `estado` y `motivo`, nunca comparando textos
 * (`shared/ui-kit/MAPEO-ERRORES.md`).
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

/** Motivos de rechazo que enumera el contrato en `ProblemDetail.motivo`. */
export const MOTIVO = Object.freeze({
  AUTOR_SILENCIADO: 'AUTOR_SILENCIADO',
  FORMATO_DE_IMAGEN_NO_ADMITIDO: 'FORMATO_DE_IMAGEN_NO_ADMITIDO',
});

/** Estados del comentario que enumera el contrato en `ComentarioResponse.estado`. */
export const ESTADO = Object.freeze({
  PUBLICADO: 'PUBLICADO',
  EN_REVISION: 'EN_REVISION',
});

/**
 * Base de la API. Vacia por omision (mismo origen, como sirve Spring Boot las
 * vistas). Para revisar la vista como HTML estatico contra un backend que
 * corre en otro sitio, la pagina lo declara:
 *
 *   <meta name="nexus-api-base" content="http://127.0.0.1:8081" />
 *
 * @returns {string} base sin barra final, o cadena vacia
 */
export function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

/**
 * @param {string} productoId
 * @returns {string} ruta absoluta al recurso de comentarios del producto
 */
export function rutaDeComentarios(productoId) {
  return `${baseDeApi()}/api/v1/products/${encodeURIComponent(productoId)}/comments`;
}

/**
 * Error de negocio devuelto por el servicio, ya interpretado.
 */
export class ErrorDeApi extends Error {
  /**
   * @param {{type?: string, title?: string, detail?: string, status?: number,
   *          motivo?: string, errores?: Array<{campo: string, mensaje: string}>}} problema
   * @param {number} estado codigo HTTP real de la respuesta
   */
  constructor(problema, estado) {
    super(problema?.detail || problema?.title || 'El servicio no pudo completar la operacion.');
    this.name = 'ErrorDeApi';
    this.tipo = problema?.type ?? null;
    this.titulo = problema?.title ?? 'El servicio no pudo completar la operacion';
    this.detalle = this.message;
    this.estado = problema?.status ?? estado;
    /** Solo en 403 y 422, segun el contrato. */
    this.motivo = problema?.motivo ?? null;
    /** @type {Array<{campo: string, mensaje: string}>} */
    this.errores = Array.isArray(problema?.errores) ? problema.errores : [];
  }

  /** True cuando el rechazo se puede corregir campo a campo en el formulario. */
  get esDeFormulario() {
    return this.errores.length > 0;
  }
}

async function cuerpoDelProblema(respuesta) {
  try {
    return await respuesta.json();
  } catch {
    return null;
  }
}

/**
 * Publica un comentario sobre un producto — CA-01, CA-02 y CA-03 de #34.
 *
 * @param {string} productoId
 * @param {{autorId: string, apodoAutor: string, texto: string,
 *          imagenes?: string[], estrellas?: number}} cuerpo
 *   `PublicacionComentarioRequest` del contrato. `estrellas` se omite (no se
 *   manda `null`) cuando la persona no quiere calificar.
 * @param {{fetchImpl?: Function}} [opciones] inyeccion para las pruebas
 * @returns {Promise<{comentario: object, estado: string}>}
 *   `comentario` es el `ComentarioResponse`; `estado` es `PUBLICADO` (201) o
 *   `EN_REVISION` (202). Se toma del cuerpo y, si faltara, del codigo HTTP.
 * @throws {ErrorDeApi} si el servicio rechaza la publicacion
 */
export async function publicarComentario(
  productoId,
  cuerpo,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(rutaDeComentarios(productoId), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(cuerpo),
  });

  if (respuesta.ok) {
    const comentario = await respuesta.json();
    const estado =
      comentario?.estado ?? (respuesta.status === 202 ? ESTADO.EN_REVISION : ESTADO.PUBLICADO);
    return { comentario, estado };
  }

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta), respuesta.status);
}
