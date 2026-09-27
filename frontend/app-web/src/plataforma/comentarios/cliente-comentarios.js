/**
 * HU-COM-001/002/003/004 y B3 — Acceso HTTP a los comentarios, las
 * calificaciones y las imágenes de los productos.
 *
 * Habla con el servicio de comentarios tal como lo define
 * `contracts/openapi/comentarios.yaml` (1.5.0, servicio
 * `services/plataforma/comentarios`). Este módulo NO define nada que el
 * contrato no diga: si el contrato cambia, cambia aquí.
 *
 *   - `GET/POST/DELETE /api/v1/products/{productId}/comments`: el hilo
 *     (paginado desde la 1.4.0), publicar y retirar.
 *   - `GET/POST /api/v1/products/{productId}/rating` y `.../rating/mia`: la
 *     calificación, separada del comentario (7.1: «solo pueden calificar un
 *     producto una vez»).
 *   - `POST /api/v1/comentarios/imagenes` y `GET .../{id}`: las imágenes, que
 *     se suben antes y el comentario lleva por su `id`.
 *
 * Publicar tiene tres respuestas distintas, porque no son lo mismo:
 *
 *   - 201 -> el comentario ya está en el hilo del producto (`PUBLICADO`)
 *   - 202 -> el filtro lo retuvo; se guardó y espera a un moderador (`EN_REVISION`)
 *   - 4xx/5xx -> no se guardó; llega un problem details (RFC 7807) con su `type`
 *            y, cuando el contrato lo declara, un `motivo` estable
 *
 * El autor y su apodo salen del token de la sesión (lo pone el interceptor),
 * nunca del cuerpo: desde la 1.1.0 el servicio ignora `autorId` y
 * `apodoAutor` si llegan.
 *
 * Todo error sale de aquí como `ErrorDeApi`, para que la vista decida por
 * `tipo`, `estado` y `motivo`, nunca comparando textos
 * (`shared/ui-kit/MAPEO-ERRORES.md`).
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

/** Motivos de rechazo que enumera el contrato en `ProblemDetail.motivo`. */
export const MOTIVO = Object.freeze({
  AUTOR_SILENCIADO: 'AUTOR_SILENCIADO',
  FORMATO_DE_IMAGEN_NO_ADMITIDO: 'FORMATO_DE_IMAGEN_NO_ADMITIDO',
  CALIFICACION_DUPLICADA: 'CALIFICACION_DUPLICADA',
});

/**
 * `type` de los problem details del servicio que una vista puede querer
 * distinguir (comentarios.yaml 1.5.0).
 */
export const TIPO = Object.freeze({
  PRODUCTO_INEXISTENTE: 'https://nexusbattles.local/errores/producto-inexistente',
  CATALOGO_NO_DISPONIBLE: 'https://nexusbattles.local/errores/catalogo-no-disponible',
  SANCIONES_NO_DISPONIBLES: 'https://nexusbattles.local/errores/sanciones-no-disponibles',
  AUTOR_SILENCIADO: 'https://nexusbattles.local/errores/autor-silenciado',
  YA_CALIFICADO: 'https://nexusbattles.local/errores/ya-calificado',
  CALIFICACION_NO_ENCONTRADA: 'https://nexusbattles.local/errores/calificacion-no-encontrada',
  IMAGENES_NO_VALIDAS: 'https://nexusbattles.local/errores/imagenes-no-validas',
  IMAGEN_AUSENTE: 'https://nexusbattles.local/errores/imagen-ausente',
  IMAGEN_DEMASIADO_GRANDE: 'https://nexusbattles.local/errores/imagen-demasiado-grande',
  IMAGEN_NO_ADMITIDA: 'https://nexusbattles.local/errores/imagen-no-admitida',
});

/** Estados del comentario que enumera el contrato en `ComentarioResponse.estado`. */
export const ESTADO = Object.freeze({
  PUBLICADO: 'PUBLICADO',
  EN_REVISION: 'EN_REVISION',
  OCULTO: 'OCULTO',
  ELIMINADO: 'ELIMINADO',
});

/** Contrato 1.4.0: 16 comentarios por página por omisión, 50 como mucho. */
export const TAMANO_DE_PAGINA = 16;

/** Contrato 1.4.0: como mucho 3 imágenes por comentario, de 2 MB cada una. */
export const MAXIMO_DE_IMAGENES = 3;
export const TAMANO_MAXIMO_DE_IMAGEN = 2 * 1024 * 1024;

/**
 * Base de la API. Vacía por omisión (mismo origen, como sirve Spring Boot las
 * vistas). Para revisar la vista como HTML estático contra un backend que
 * corre en otro sitio, la página lo declara:
 *
 *   <meta name="nexus-api-base" content="http://127.0.0.1:8081" />
 *
 * @returns {string} base sin barra final, o cadena vacía
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
 * @param {string} productoId
 * @returns {string} ruta absoluta a la calificación del producto
 */
export function rutaDeCalificacion(productoId) {
  return `${baseDeApi()}/api/v1/products/${encodeURIComponent(productoId)}/rating`;
}

/**
 * Dirección de una imagen de comentario, para un `<img src>`. Las de un
 * comentario publicado se ven sin sesión; las demás solo las ve su autor o
 * moderación, y un `<img>` no manda el token: esas hay que pedirlas con
 * `fetch` (el interceptor lo pone) y pintarlas desde un `blob:`.
 *
 * @param {string} imagenId el `id` que devolvió `subirImagen`
 * @returns {string}
 */
export function urlDeImagen(imagenId) {
  return `${baseDeApi()}/api/v1/comentarios/imagenes/${encodeURIComponent(imagenId)}`;
}

/**
 * Error de negocio devuelto por el servicio, ya interpretado.
 */
export class ErrorDeApi extends Error {
  /**
   * @param {{type?: string, title?: string, detail?: string, status?: number,
   *          motivo?: string, errores?: Array<{campo: string, mensaje: string}>}} problema
   * @param {number} estado código HTTP real de la respuesta
   */
  constructor(problema, estado) {
    super(problema?.detail || problema?.title || 'El servicio no pudo completar la operación.');
    this.name = 'ErrorDeApi';
    this.tipo = problema?.type ?? null;
    this.titulo = problema?.title ?? 'El servicio no pudo completar la operación';
    this.detalle = this.message;
    this.estado = problema?.status ?? estado;
    /** Cuando el contrato lo declara: 403, 409 de calificar y 415 de imágenes. */
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

async function jsonOError(respuesta) {
  if (respuesta.ok) {
    return respuesta.json();
  }
  throw new ErrorDeApi(await cuerpoDelProblema(respuesta), respuesta.status);
}

/**
 * Publica un comentario sobre un producto — CA-01, CA-02 y CA-03 de #34.
 *
 * @param {string} productoId
 * @param {{texto: string, imagenes?: string[], estrellas?: number}} cuerpo
 *   `PublicacionComentarioRequest` del contrato. `imagenes` son los `id` que
 *   devolvió `subirImagen` (desde la 1.4.0; un nombre de archivo responde
 *   400). `estrellas` se omite (no se manda `null`) cuando la persona no
 *   quiere calificar; si ya había calificado, el comentario entra igual y la
 *   respuesta trae `calificacionDescartada: true`.
 * @param {{fetchImpl?: Function}} [opciones] inyección para las pruebas
 * @returns {Promise<{comentario: object, estado: string}>}
 *   `comentario` es el `ComentarioResponse`; `estado` es `PUBLICADO` (201) o
 *   `EN_REVISION` (202). Se toma del cuerpo y, si faltara, del código HTTP.
 * @throws {ErrorDeApi} si el servicio rechaza la publicación
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

/**
 * Una página del hilo de un producto — HU-COM-003 (CA-01/CA-03), HU-INV-014 y
 * la paginación de la 1.4.0. Pública: no hace falta sesión para leerla.
 *
 * @param {string} productoId
 * @param {{pagina?: number, tamano?: number, fetchImpl?: Function}} [opciones]
 *   `pagina` desde 0 y `tamano` de 1 a 50 (16 por omisión). Si no se dan, no
 *   viajan y el servicio aplica los suyos.
 * @returns {Promise<{productoId: string, comentarios: object[], pagina: number,
 *   tamano: number, total: number, totalPaginas: number,
 *   calificacionPromedio: number|null, totalCalificaciones: number}>}
 *   `HiloDeComentariosResponse` del contrato: los comentarios del más reciente
 *   al más antiguo, `total` de todas las páginas, y el promedio de la tabla de
 *   calificaciones (el mismo número que `resumenDeCalificaciones`). Viene
 *   `null` cuando nadie ha calificado: la vista lo pinta como «sin
 *   calificaciones», nunca como 0.
 * @throws {ErrorDeApi}
 */
export async function consultarHilo(
  productoId,
  { pagina, tamano, fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const consulta = new URLSearchParams();
  if (Number.isInteger(pagina)) {
    consulta.set('pagina', String(pagina));
  }
  if (Number.isInteger(tamano)) {
    consulta.set('tamano', String(tamano));
  }
  const texto = consulta.toString();
  const sufijo = texto ? `?${texto}` : '';
  const respuesta = await fetchImpl(`${rutaDeComentarios(productoId)}${sufijo}`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });
  return jsonOError(respuesta);
}

/**
 * Retira un comentario propio — HU-COM-004. Quien retira es el `uid` del
 * token (lo pone el interceptor); 204 también si ya estaba retirado. Retirar
 * un comentario no toca la calificación de su autor (1.5.0).
 *
 * @param {string} productoId
 * @param {string} comentarioId
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<void>}
 * @throws {ErrorDeApi} 403 si es de otro (`comentario-ajeno`), 404 si no existe
 */
export async function eliminarComentario(
  productoId,
  comentarioId,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(
    `${rutaDeComentarios(productoId)}/${encodeURIComponent(comentarioId)}`,
    { method: 'DELETE', headers: { Accept: 'application/problem+json' } },
  );
  if (respuesta.ok) {
    return;
  }
  throw new ErrorDeApi(await cuerpoDelProblema(respuesta), respuesta.status);
}

/**
 * Califica un producto de 1 a 5 estrellas, una sola vez y sin escribir nada
 * (contrato 1.4.0). Necesita sesión.
 *
 * @param {string} productoId
 * @param {number} estrellas de 1 a 5
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{productoId: string, estrellas: number, fecha: string,
 *   resumen: {productoId: string, promedio: number|null, total: number,
 *   distribucion: Record<'1'|'2'|'3'|'4'|'5', number>}}>}
 *   `CalificacionResponse`, con el resumen ya actualizado para repintar la
 *   ficha sin otra petición.
 * @throws {ErrorDeApi} 409 `ya-calificado` (motivo `CALIFICACION_DUPLICADA`)
 *   si ya lo había calificado; 403 si tiene una sanción activa; 404
 *   `producto-inexistente`; 503 si el catálogo o sanciones no responden.
 */
export async function calificar(
  productoId,
  estrellas,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(rutaDeCalificacion(productoId), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ estrellas }),
  });
  return jsonOError(respuesta);
}

/**
 * El resumen de las calificaciones de un producto: promedio con un decimal
 * (nulo si nadie calificó), total y distribución por estrellas. Público.
 *
 * @param {string} productoId
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{productoId: string, promedio: number|null, total: number,
 *   distribucion: Record<'1'|'2'|'3'|'4'|'5', number>}>}
 * @throws {ErrorDeApi} 404 `producto-inexistente`
 */
export async function resumenDeCalificaciones(
  productoId,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(rutaDeCalificacion(productoId), {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });
  return jsonOError(respuesta);
}

/**
 * La calificación de quien tiene la sesión sobre un producto, o `null` si
 * todavía no lo calificó: el 404 del contrato es el estado normal de quien aún
 * no ha opinado, no un error, y la vista lo usa para ofrecerle las estrellas.
 *
 * @param {string} productoId
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{productoId: string, estrellas: number, fecha: string}|null>}
 * @throws {ErrorDeApi} 401 sin sesión, o cualquier otro fallo del servicio
 */
export async function miCalificacion(
  productoId,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(`${rutaDeCalificacion(productoId)}/mia`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });
  if (respuesta.status === 404) {
    return null;
  }
  return jsonOError(respuesta);
}

/**
 * Sube una imagen para adjuntarla después a un comentario (contrato 1.4.0).
 * Necesita sesión. Viaja en el campo `archivo` de un `multipart/form-data`
 * (el navegador pone la cabecera con su frontera: aquí no se escribe).
 *
 * El servicio decide el tipo por los bytes, no por el nombre ni por el tipo
 * que declare el archivo, y descarta el nombre original. Lo único que se
 * comprueba aquí antes de subir es el tamaño: una imagen de más de 2 MB se
 * rechaza sin gastar la subida, con el mismo error que daría el servicio.
 *
 * @param {Blob} archivo lo que eligió la persona (un `File` es un `Blob`)
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{id: string, tipo: string, tamano: number, url: string}>}
 *   `ImagenSubida`: el `id` es lo que se manda en `imagenes` al publicar.
 * @throws {ErrorDeApi} 400 sin archivo, 413 si pesa más de 2 MB o mide más de
 *   4096×4096, 415 (motivo `FORMATO_DE_IMAGEN_NO_ADMITIDO`) si no es un JPEG,
 *   PNG o WebP válido
 */
export async function subirImagen(archivo, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  if (!archivo || typeof archivo.size !== 'number') {
    throw new ErrorDeApi(
      {
        type: TIPO.IMAGEN_AUSENTE,
        title: 'Falta la imagen',
        detail: 'Elige una imagen JPEG, PNG o WebP.',
        status: 400,
      },
      400,
    );
  }
  if (archivo.size > TAMANO_MAXIMO_DE_IMAGEN) {
    throw new ErrorDeApi(
      {
        type: TIPO.IMAGEN_DEMASIADO_GRANDE,
        title: 'La imagen es demasiado grande',
        detail: 'La imagen pesa más de 2 MB.',
        status: 413,
      },
      413,
    );
  }
  const datos = new FormData();
  datos.append('archivo', archivo);
  const respuesta = await fetchImpl(`${baseDeApi()}/api/v1/comentarios/imagenes`, {
    method: 'POST',
    headers: { Accept: 'application/json' },
    body: datos,
  });
  return jsonOError(respuesta);
}
