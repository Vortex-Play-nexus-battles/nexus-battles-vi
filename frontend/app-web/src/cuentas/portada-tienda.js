/**
 * La tienda en la portada pública — UXC-4 (retroalimentación del profesor).
 *
 * Quien llega a Nexus Battles VI sin cuenta aterriza en la entrada (`/` lleva
 * a `/login`). Hasta aquí solo veía un formulario: nada de lo que el juego
 * vende. §7.5 describe una vitrina, y la vitrina es pública
 * (`GET /api/v1/vitrina`, `security: []` en ecommerce-carrito.yaml), así que
 * la portada la enseña: productos reales, con su imagen, nombre, tipo, precio
 * y rebaja si la hay, y «Ver producto» para su detalle —el mismo del
 * catálogo— con la calificación promedio y las opiniones (también públicas).
 *
 * Lo que necesita cuenta lo dice y lleva a ella sin salir de la página:
 * comprar, calificar, opinar y reportar. «Entra para comprar» deja la vuelta
 * preparada (`?volver=` a la tienda) y pone el foco en el correo.
 *
 * Estados: cargando, vacío (la tienda no tiene productos a la venta), error
 * (no responde; reintentar) y parcial (un producto sin imagen enseña el
 * símbolo de su tipo). Nunca un código HTTP ni el nombre de un servicio.
 *
 * @module cuentas/portada-tienda
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { rutaDeApi } from '../comun/base-api.js';
import { RUTAS, resolver } from '../comun/sesion.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../comun/ui/estado-vista.js';
import { MODOS, bloqueDeCompra, tarjetaDeProducto } from './tienda-producto.js';

/** Cuántos productos enseña la portada: dos filas en escritorio. */
export const PRODUCTOS_EN_PORTADA = 8;

/** Los productos pintados en cada zona, para el manejador delegado. */
const PINTADOS = new WeakMap();

/**
 * Pide la primera página de la vitrina.
 *
 * @param {{fetchImpl?: Function, cuantos?: number}} [opciones]
 * @returns {Promise<{productos: object[], total: number|null}>}
 */
export async function pedirVitrinaPublica({
  fetchImpl = fetchWithHttpErrorInterceptor,
  cuantos = PRODUCTOS_EN_PORTADA,
} = {}) {
  const respuesta = await fetchImpl(rutaDeApi(`/vitrina?size=${cuantos}`), {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });
  if (!respuesta.ok) {
    throw new Error(`La vitrina respondió ${respuesta.status}`);
  }
  const datos = await respuesta.json();
  return {
    productos: Array.isArray(datos?.content) ? datos.content : [],
    total: Number.isFinite(datos?.totalElements) ? datos.totalElements : null,
  };
}

/**
 * La ruta de la tienda en este sitio, para la vuelta tras entrar.
 *
 * @returns {string} ruta absoluta del mismo origen (`/frontend/app-web/src/cuentas/tienda.html`)
 */
export function rutaDeLaTienda() {
  const url = new URL(resolver(RUTAS.tienda));
  return `${url.pathname}${url.search}`;
}

/**
 * Deja preparada la vuelta a la tienda y lleva al formulario de entrada.
 *
 * `login.js` lee `?volver=` al enviar el formulario, así que basta con
 * escribirla en la dirección (sin recargar) antes de que la persona entre.
 *
 * @param {Document} doc
 */
export function entrarParaComprar(doc = document) {
  const url = new URL(globalThis.location.href);
  url.searchParams.set('volver', rutaDeLaTienda());
  globalThis.history?.replaceState?.(null, '', url.href);
  llevarAlFormulario(doc);
}

/**
 * Lleva al formulario de entrada y pone el foco en el correo, sin tocar la
 * vuelta (calificar u opinar se hace desde el mismo detalle, que se puede
 * volver a abrir tras entrar).
 *
 * @param {Document} doc
 */
export function llevarAlFormulario(doc = document) {
  // Si hay una ficha abierta, se cierra: el formulario está detrás.
  doc.querySelector('.ficha__cerrar')?.click();
  const correo = doc.getElementById('email');
  correo?.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
  correo?.focus({ preventScroll: true });
}

/**
 * Pinta la vitrina pública en su zona.
 *
 * @param {HTMLElement} zona `[data-zona="productos-publicos"]`
 * @param {{pedir?: typeof pedirVitrinaPublica, abrir?: Function}} [opciones]
 *   `abrir`: abre el detalle (por omisión, la ficha del catálogo, que se carga
 *   al primer uso para no hacer esperar a la entrada).
 * @returns {Promise<void>}
 */
export async function pintarVitrinaPublica(
  zona,
  { pedir = pedirVitrinaPublica, abrir = abrirDetallePublico } = {},
) {
  if (!zona) {
    return;
  }
  zona.replaceChildren(estadoDeCarga({ filas: 2, etiqueta: 'Cargando la tienda…' }));
  let productos;
  try {
    ({ productos } = await pedir());
  } catch (error) {
    zona.replaceChildren(
      estadoDeError({
        titulo: 'La tienda no responde ahora mismo',
        detalle: 'Puedes entrar igualmente. Vuelve a intentarlo para ver lo que está a la venta.',
        alReintentar: () => pintarVitrinaPublica(zona, { pedir, abrir }),
      }),
    );
    console.error('No se pudo cargar la vitrina pública:', error);
    return;
  }
  if (productos.length === 0) {
    zona.replaceChildren(
      estadoVacio({
        titulo: 'Todavía no hay productos a la venta',
        detalle:
          'Cuando la administración publique el catálogo aparecerán aquí. Mientras, entra y juega.',
        icono: '◇',
      }),
    );
    return;
  }
  zona.replaceChildren(...productos.map((dto) => tarjetaDeProducto(dto, { modo: MODOS.PORTADA })));
  PINTADOS.set(zona, { productos, abrir });
  // Un solo manejador por zona, delegado: «Reintentar» vuelve a pintar las
  // tarjetas pero no vuelve a enganchar nada.
  if (!zona.dataset.enganchada) {
    zona.dataset.enganchada = 'si';
    zona.addEventListener('click', (evento) => {
      const ver = evento.target.closest('[data-ver-producto]');
      const pintados = PINTADOS.get(zona);
      if (!ver || !pintados) {
        return;
      }
      const dto = pintados.productos.find(
        (producto) => String(producto.id) === ver.dataset.verProducto,
      );
      if (dto) {
        pintados.abrir(dto, ver);
      }
    });
  }
}

/**
 * El detalle público: la ficha del catálogo con el bloque de compra (en modo
 * portada) y las opiniones, de solo lectura hasta entrar.
 *
 * @param {object} dto
 * @param {HTMLElement} origen
 */
export async function abrirDetallePublico(dto, origen) {
  const [{ abrirFicha }, { complementoDeOpiniones }] = await Promise.all([
    import('../contenido/inventario/ficha-producto.js'),
    import('../plataforma/comentarios/hilo-comentarios.js'),
  ]);
  await abrirFicha(String(dto.id), {
    origen,
    contexto: 'portada',
    complementos: [
      () =>
        bloqueDeCompra(dto, {
          modo: MODOS.PORTADA,
          alEntrar: () => entrarParaComprar(document),
        }),
      complementoDeOpiniones({
        sesion: { yo: null, apodo: null },
        alPedirEntrada: () => llevarAlFormulario(document),
      }),
    ],
  });
}

// Arranque en el navegador; en las pruebas se llama a mano.
if (globalThis.document?.addEventListener) {
  globalThis.document.addEventListener('DOMContentLoaded', () => {
    pintarVitrinaPublica(globalThis.document.querySelector('[data-zona="productos-publicos"]'));
    globalThis.document
      .querySelector('[data-accion="ver-tienda-completa"]')
      ?.addEventListener('click', () => entrarParaComprar(globalThis.document));
  });
}
