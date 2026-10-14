/**
 * Vista previa de un producto tal como lo verá el jugador — HU-PRD-010,
 * §7.2.3 «pre-visualizar los cambios antes de aplicarlos».
 *
 * Usa la misma tarjeta que la tienda (`tarjetaDeProducto`), alimentada con lo
 * que la tienda recibiría de este producto (`ProductoDeVitrina`,
 * ecommerce-carrito.yaml): así lo que se ve aquí es lo que verá el jugador, y
 * no una imitación que se desvíe con el tiempo. El precio sigue la misma regla
 * que la vitrina (CalculadoraDePrecios de ms-ecommerce): con una promoción
 * vigente, `base × (100 − %) / 100` redondeado a la mitad hacia arriba y nunca
 * por debajo de 1.
 *
 * Los cambios que se previsualizan no se guardan: viven en el formulario hasta
 * que quien administra los publica o los descarta (HU-PRD-010, criterios 2 y
 * 3). La tarjeta va sin botones activos: es una muestra.
 *
 * @module contenido/productos/vista-previa-producto
 */

import { MODOS, tarjetaDeProducto } from '../../cuentas/tienda-producto.js';
import { h } from '../../comun/ui/dom.js';

/**
 * El porcentaje de la promoción que está vigente en `ahora`, o null.
 *
 * @param {{porcentaje?: number, desde?: string, hasta?: string}|null|undefined} promocion
 * @param {number} ahora milisegundos
 * @returns {number|null}
 */
export function porcentajeVigente(promocion, ahora = Date.now()) {
  const porcentaje = Number(promocion?.porcentaje);
  if (!Number.isInteger(porcentaje) || porcentaje < 1 || porcentaje > 99) {
    return null;
  }
  const desde = Date.parse(promocion?.desde ?? '');
  const hasta = Date.parse(promocion?.hasta ?? '');
  if (!Number.isFinite(desde) || !Number.isFinite(hasta)) {
    return null;
  }
  return desde <= ahora && ahora < hasta ? porcentaje : null;
}

/**
 * `base × (100 − %) / 100` a enteros, mitad hacia arriba, nunca menos de 1.
 *
 * @param {number} base
 * @param {number|null} porcentaje
 * @returns {number}
 */
export function conDescuento(base, porcentaje) {
  if (!porcentaje) {
    return base;
  }
  return Math.max(1, Math.floor((base * (100 - porcentaje) + 50) / 100));
}

/**
 * Lo que la tienda recibiría de este producto.
 *
 * @param {object} producto los datos del formulario o del catálogo
 * @param {number} [ahora]
 * @returns {object} `ProductoDeVitrina`
 */
export function comoProductoDeVitrina(producto, ahora = Date.now()) {
  const porcentaje = porcentajeVigente(producto?.promocion, ahora);
  const real = Number(producto?.precioMonedaReal);
  const creditos = Number(producto?.precioCreditos);
  const conPrecioReal = Boolean(producto?.premium) && Number.isFinite(real) && real > 0;
  const conCreditos = !producto?.premium && Number.isInteger(creditos) && creditos > 0;
  const baseReal = conPrecioReal ? Math.round(real) : null;
  return {
    id: producto?.id ?? 'vista-previa',
    nombre: producto?.nombre ?? '',
    descripcion: producto?.descripcion ?? '',
    tipo: producto?.tipo ?? '',
    imagenUrl: producto?.imagen ?? null,
    precioFinal: baseReal === null ? null : conDescuento(baseReal, porcentaje),
    precioOriginal: baseReal,
    moneda: baseReal === null ? null : 'COP',
    porcentajeDescuento: porcentaje,
    precioCreditos: conCreditos ? conDescuento(creditos, porcentaje) : null,
  };
}

/**
 * La tarjeta de la tienda con estos datos, sin botones activos.
 *
 * @param {object} producto
 * @param {{ahora?: number}} [opciones]
 * @returns {HTMLElement}
 */
export function vistaPreviaDeProducto(producto, { ahora = Date.now() } = {}) {
  const tarjeta = tarjetaDeProducto(comoProductoDeVitrina(producto, ahora), {
    modo: MODOS.PORTADA,
    nivel: 4,
  });
  for (const control of tarjeta.querySelectorAll('button, a')) {
    control.setAttribute('tabindex', '-1');
    if (control instanceof HTMLButtonElement) {
      control.disabled = true;
    }
  }
  return h('figure', {
    clase: 'vista-previa-producto',
    datos: { zona: 'vista-previa' },
    hijos: [
      tarjeta,
      h('figcaption', {
        clase: 't-meta',
        texto: 'Así lo verá el jugador en la tienda.',
      }),
    ],
  });
}
