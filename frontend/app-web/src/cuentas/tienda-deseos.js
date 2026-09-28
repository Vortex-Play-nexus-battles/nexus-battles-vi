/**
 * La lista de deseos de la tienda — B5 (§7.5: «añadir a la lista de deseos»;
 * «los productos que son enviados a la lista de deseos siempre tendrán una
 * distinción para que el cliente los tenga presente»).
 *
 * La lista la guarda el servicio, por jugador (ecommerce-carrito.yaml 1.4.0):
 * `PUT /api/v1/lista-deseos/{productoId}` la añade y
 * `DELETE /api/v1/lista-deseos/{productoId}` la quita. Las dos son
 * idempotentes: pulsar dos veces, o reintentar tras un corte, deja la lista
 * igual. En el navegador no se guarda nada; lo que se pinta es lo que dice la
 * vitrina (`enListaDeseos`) o lo que acaba de confirmar el servicio.
 *
 * @module cuentas/tienda-deseos
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { rutaDeApi } from '../comun/base-api.js';
import { h } from '../comun/ui/dom.js';
import { distintivoDeDeseo } from './tienda-producto.js';

/**
 * Añade o quita un producto de la lista del jugador.
 *
 * @param {string} productoId UUID del catálogo
 * @param {boolean} desear true para añadirlo, false para quitarlo
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{ok: boolean, estado: number, problema: object|null}>}
 *   `estado` 0 si no hubo respuesta
 */
export async function cambiarDeseo(
  productoId,
  desear,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  let respuesta;
  try {
    respuesta = await fetchImpl(
      rutaDeApi(`/lista-deseos/${encodeURIComponent(String(productoId))}`),
      { method: desear ? 'PUT' : 'DELETE', headers: { 'Content-Type': 'application/json' } },
    );
  } catch {
    return { ok: false, estado: 0, problema: null };
  }
  if (respuesta.ok) {
    return { ok: true, estado: respuesta.status, problema: null };
  }
  let problema = null;
  try {
    const cuerpo = await respuesta.json();
    problema = cuerpo && typeof cuerpo === 'object' ? cuerpo : null;
  } catch {
    problema = null;
  }
  return { ok: false, estado: respuesta.status, problema };
}

/**
 * Lo que se le dice al jugador cuando la lista no se pudo cambiar. Por el
 * `status` y el `type`, nunca por el texto del servidor.
 *
 * @param {{estado: number, problema: object|null}} fallo
 * @param {boolean} desear
 * @returns {string}
 */
export function textoDelFalloDeDeseo({ estado, problema }, desear) {
  if (estado === 401) {
    return 'Tu sesión ya no es válida. Vuelve a iniciar sesión para usar tu lista de deseos.';
  }
  if (problema?.type === 'urn:nexus:problema:producto-inexistente' || estado === 404) {
    return 'Ese producto ya no está en el catálogo. Actualiza la tienda.';
  }
  return desear
    ? 'No se pudo guardar en tu lista de deseos. Inténtalo de nuevo en unos segundos.'
    : 'No se pudo quitar de tu lista de deseos. Inténtalo de nuevo en unos segundos.';
}

/**
 * Pinta en la vista lo que el servicio acaba de confirmar, sin volver a
 * pintar las tarjetas (quien pulsó no pierde el foco): cada conmutador de ese
 * producto —el de la tarjeta y el del detalle— y la distinción de la tarjeta.
 *
 * El conmutador conserva su nombre («Lista de deseos: …»); lo que cambia es
 * `aria-pressed`, que es como un lector de pantalla anuncia un botón que se
 * activa y se desactiva, y lo que el estilo pinta relleno.
 *
 * @param {ParentNode} raiz el documento
 * @param {string} productoId
 * @param {boolean} deseado
 */
export function pintarDeseo(raiz, productoId, deseado) {
  const id = String(productoId);
  for (const boton of raiz.querySelectorAll('[data-deseo]')) {
    if (boton.dataset.deseo === id) {
      boton.setAttribute('aria-pressed', String(deseado));
    }
  }
  for (const tarjeta of raiz.querySelectorAll('.product-card[data-id-producto]')) {
    if (tarjeta.dataset.idProducto !== id) {
      continue;
    }
    const existente = tarjeta.querySelector('.producto-deseado');
    if (deseado) {
      tarjeta.dataset.deseado = 'si';
      if (!existente) {
        let zona = tarjeta.querySelector('.product-card__distintivos');
        if (!zona) {
          zona = h('div', { clase: 'product-card__distintivos' });
          tarjeta.querySelector('.product-card__nombre')?.after(zona);
        }
        zona.append(distintivoDeDeseo());
      }
    } else {
      delete tarjeta.dataset.deseado;
      const zona = existente?.parentElement ?? null;
      existente?.remove();
      if (zona && zona.childElementCount === 0) {
        zona.remove();
      }
    }
  }
}
