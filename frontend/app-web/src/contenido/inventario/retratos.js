/**
 * Retratos de la vitrina — UX-R2.5 (HU-INV-001).
 *
 * ## Por que hace falta un modulo para esto
 *
 * `ElementoInventario` no trae imagen: el inventario guarda solo la
 * referencia `productoId`, a proposito, para que un cambio del administrador
 * se propague a todas las instancias (RF-ADM-10). La imagen vive en el
 * catalogo, y `productos.yaml` solo publica `GET /productos/{id}` — no hay
 * listado ni consulta por lotes.
 *
 * O sea: dieciseis tarjetas son dieciseis peticiones. Eso no puede bloquear el
 * primer pintado, y no puede repetirse cada vez que se cambia de pagina.
 *
 * ## Como se resuelve
 *
 * 1. La vitrina pinta **ya**, con el icono del tipo en cada marco.
 * 2. Despues, en segundo plano, se piden los productos que falten.
 * 3. Cada imagen que llega se cuelga de su marco. Las que no llegan no pasa
 *    nada: el icono se queda, que es informacion de verdad y no un hueco.
 *
 * El cache es por `productoId` y vive lo que viva la pagina. Un jugador que
 * pasa de la pagina 1 a la 2 y vuelve no vuelve a pedir nada.
 *
 * Los fallos **no** se convierten en mensaje de error para el jugador: no
 * poder pintar un retrato no es un fallo del inventario, y llenar la pantalla
 * de avisos por dieciseis peticiones opcionales seria peor que no tenerlas.
 * Quedan en la consola, para el equipo.
 */

import { consultarProducto } from './cliente-productos.js';

/** Imagen ya conocida por `productoId`. `null` = se pidio y no habia. */
const cache = new Map();

/**
 * Cuelga en cada marco la imagen de su producto, segun vayan llegando.
 *
 * @param {ParentNode} raiz donde estan los marcos con `data-retrato-de`
 * @param {{consultar?: Function}} [opciones] inyeccion para las pruebas
 * @returns {Promise<number>} cuantos retratos se pintaron
 */
export async function pintarRetratos(raiz, { consultar = consultarProducto } = {}) {
  const marcos = [...(raiz?.querySelectorAll('[data-retrato-de]') ?? [])];
  if (marcos.length === 0) {
    return 0;
  }

  // Un marco por producto, pero varios elementos pueden ser del mismo
  // producto (dos espadas iguales con nombre propio distinto): se pide una
  // vez y se pinta en todos.
  const porProducto = new Map();
  for (const marco of marcos) {
    const id = marco.dataset.retratoDe;
    if (!id) {
      continue;
    }
    if (!porProducto.has(id)) {
      porProducto.set(id, []);
    }
    porProducto.get(id).push(marco);
  }

  let pintados = 0;
  await Promise.all(
    [...porProducto.entries()].map(async ([id, destinos]) => {
      const url = await imagenDe(id, consultar);
      if (!url) {
        return;
      }
      for (const marco of destinos) {
        // El marco puede haberse ido de la pagina mientras llegaba la
        // respuesta: repintar sobre un nodo huerfano no hace dano, pero
        // tampoco cuenta.
        if (!marco.isConnected && marco.ownerDocument?.defaultView) {
          continue;
        }
        colgarImagen(marco, url);
        pintados += 1;
      }
    }),
  );
  return pintados;
}

/**
 * La imagen de un producto, del cache o del catalogo.
 *
 * @param {string} id
 * @param {Function} consultar
 * @returns {Promise<string|null>}
 */
async function imagenDe(id, consultar) {
  if (cache.has(id)) {
    return cache.get(id);
  }
  try {
    const producto = await consultar(id);
    const url =
      typeof producto?.imagen === 'string' && producto.imagen.trim() !== ''
        ? producto.imagen
        : null;
    cache.set(id, url);
    return url;
  } catch (fallo) {
    // Se cachea el fallo como «no hay imagen» para no reintentar dieciseis
    // veces contra un catalogo que esta caido.
    cache.set(id, null);
    console.warn('No se pudo traer el retrato del producto', id, fallo);
    return null;
  }
}

/**
 * Sustituye el icono del marco por la imagen real.
 *
 * @param {HTMLElement} marco
 * @param {string} url
 */
function colgarImagen(marco, url) {
  if (marco.querySelector('.marco-heroe__imagen')) {
    return;
  }
  const img = marco.ownerDocument.createElement('img');
  img.className = 'marco-heroe__imagen';
  img.src = url;
  // El nombre del elemento ya esta escrito debajo del marco: un `alt` con el
  // mismo texto se lo haria oir dos veces a quien usa lector de pantalla.
  img.alt = '';
  img.loading = 'lazy';
  // Si la URL del catalogo esta rota, se vuelve al icono en vez de dejar el
  // simbolo de imagen partida del navegador.
  img.addEventListener('error', () => img.remove(), { once: true });
  marco.append(img);
}

/** Vacia el cache. Solo para las pruebas: en la vista no hay motivo. */
export function olvidarRetratos() {
  cache.clear();
}
