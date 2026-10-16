/**
 * Arte del juego (fase 2): de donde sale la imagen de un producto, una mision o
 * un heroe.
 *
 * ## Orden de preferencia
 *
 * 1. La imagen que trae la API, si es real. Un administrador puede haberla
 *    cambiado (RF-ADM-10) y esa manda.
 * 2. Si la API trae la figura provisional de la semilla (el SVG con el nombre
 *    del tipo) o nada, el WebP aprobado del
 *    manifiesto (`arte-manifiesto.js`, generado desde
 *    docs/arte/manifest-assets.json): por id y, donde la API no da id —la
 *    vitrina de subastas—, por nombre del catalogo.
 * 3. Si no hay ninguna, `null`: cada vista conserva su icono vectorial, que es
 *    el fallback para lo que de verdad no tiene arte.
 *
 * Asi los datos ya sembrados en DEV con la figura provisional (o las subastas
 * publicadas antes, que copiaron esa figura en `miniaturaUrl`) muestran el arte
 * sin esperar a que nadie los edite.
 */

import { ARTE_DE_MISIONES, ARTE_DE_PRODUCTOS, RETRATOS_DE_PROTOTIPOS } from './arte-manifiesto.js';

/** Minusculas, sin tildes ni espacios sobrantes. */
function normalizar(texto) {
  return String(texto ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

const PRODUCTO_POR_NOMBRE = new Map(
  Object.values(ARTE_DE_PRODUCTOS).map(([nombre, url]) => [normalizar(nombre), url]),
);
const RETRATO_POR_PROTOTIPO = new Map(
  Object.entries(RETRATOS_DE_PROTOTIPOS).map(([prototipo, url]) => [normalizar(prototipo), url]),
);

/**
 * ¿La imagen no es arte de verdad? La figura provisional que pone la semilla
 * (un SVG embebido con el nombre del tipo) o el vacio. Cualquier otra URL,
 * absoluta o relativa, es una imagen que alguien eligio y manda.
 *
 * @param {unknown} url
 * @returns {boolean}
 */
export function esImagenProvisional(url) {
  if (typeof url !== 'string' || url.trim() === '') {
    return true;
  }
  return url.trim().startsWith('data:image/svg+xml');
}

/**
 * La imagen de un producto.
 *
 * @param {{id?: string, productoId?: string, nombre?: string, imagen?: string}} producto
 * @returns {string|null}
 */
export function imagenDeProducto(producto) {
  if (!producto) {
    return null;
  }
  const propia = producto.imagen ?? producto.miniaturaUrl;
  if (!esImagenProvisional(propia)) {
    return propia.trim();
  }
  const id = producto.productoId ?? producto.id;
  if (id && ARTE_DE_PRODUCTOS[id]) {
    return ARTE_DE_PRODUCTOS[id][1];
  }
  return PRODUCTO_POR_NOMBRE.get(normalizar(producto.nombre ?? producto.nombreProducto)) ?? null;
}

/**
 * La imagen panoramica de una mision.
 *
 * @param {{id?: string, imagen?: string}} mision
 * @returns {string|null}
 */
export function imagenDeMision(mision) {
  if (!mision) {
    return null;
  }
  if (!esImagenProvisional(mision.imagen)) {
    return mision.imagen.trim();
  }
  return ARTE_DE_MISIONES[mision.id] ?? null;
}

/**
 * El retrato de un heroe: su imagen si es real; si no, el del prototipo de la
 * Tabla 6 (los heroes del jugador comparten retrato con su prototipo).
 *
 * @param {{imagen?: string, retratoUrl?: string, prototipo?: string, productoId?: string, nombre?: string}} heroe
 * @returns {string|null}
 */
export function retratoDe(heroe) {
  if (!heroe) {
    return null;
  }
  const propia = heroe.imagen ?? heroe.retratoUrl;
  if (!esImagenProvisional(propia)) {
    return propia.trim();
  }
  if (heroe.productoId && ARTE_DE_PRODUCTOS[heroe.productoId]) {
    return ARTE_DE_PRODUCTOS[heroe.productoId][1];
  }
  return (
    RETRATO_POR_PROTOTIPO.get(normalizar(heroe.prototipo)) ??
    RETRATO_POR_PROTOTIPO.get(normalizar(heroe.nombre)) ??
    null
  );
}
