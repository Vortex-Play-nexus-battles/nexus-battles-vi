/**
 * Dónde vive la API — mecanismo único de base de URL del frontend.
 *
 * Es el patrón que ya usan `cliente-salas.js`, `cliente-comentarios.js`,
 * `cliente-notificaciones.js` y `cliente-metricas.js`, cada uno con su propia
 * copia. Aquí vive una sola vez para que quien lo necesite lo importe en vez de
 * copiarlo otra vez — que es como `tienda.js` acabó con una constante global
 * sin declarar.
 *
 * **Pendiente:** esos cuatro clientes siguen con su copia. Sustituirlas por
 * este módulo es un cambio mecánico, pero toca cuatro archivos de tres módulos
 * distintos, así que va en su propio PR y no colado en otro.
 *
 * **Vacío por omisión, es decir mismo origen.** Así es como Spring Boot sirve
 * estas vistas en la ejecución integrada, y por eso no hay ningún `localhost`
 * escrito en el código.
 *
 * Para revisar las vistas servidas como HTML estático contra un backend que
 * corre en otro sitio, la propia página lo declara:
 *
 *     <meta name="nexus-api-base" content="http://127.0.0.1:8083" />
 *
 * @module base-api
 */

/**
 * @param {ParentNode} [documento] inyectable para las pruebas
 * @returns {string} base sin barra final, o cadena vacía (mismo origen)
 */
export function baseDeApi(documento = globalThis.document) {
  const meta = documento?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

/**
 * Ruta absoluta a un recurso de la API, ya con el prefijo de versión.
 *
 * Regla 2 de plataforma: todo cuelga de `/api/v1`.
 *
 * @param {string} recurso por ejemplo `/productos`
 * @param {ParentNode} [documento]
 * @returns {string}
 */
export function rutaDeApi(recurso, documento) {
  return `${baseDeApi(documento)}/api/v1${recurso}`;
}
