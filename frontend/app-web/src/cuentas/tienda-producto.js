/**
 * El producto en la tienda y en la portada — UXC-4 (ProductCard, PriceTag,
 * OwnedBadge, WishlistToggle y el bloque de compra del detalle).
 *
 * §7.5 del documento: cada producto de la vitrina lleva nombre, imagen,
 * descripción, habilidades, precio en la moneda que corresponda, el
 * porcentaje de descuento si lo hay, la marca de «propio» y los botones de
 * añadir a la cesta y a la lista de deseos; al pulsarlo, su detalle.
 *
 * ## Lo que no se finge
 *
 *   - **Descuento**: solo si el precio de verdad bajó (`tienda-adaptador.js`).
 *   - **Moneda**: la que dice el producto; sin moneda, la cifra sola.
 *   - **Propio**: lo que dice el inventario del jugador
 *     (`propiedadesDelJugador`); la vitrina hoy no lo calcula.
 *   - **Lista de deseos**: el contrato de la tienda no tiene dónde guardarla
 *     (`enListaDeseos` sale siempre en `false`). El control existe, se ve
 *     deshabilitado y dice por qué; no guarda nada en el navegador haciéndose
 *     pasar por la cuenta. Si un día el servicio marca un producto, la tarjeta
 *     lo distingue.
 *
 * Las clases `.product-card`, `.price`, `.price-antes`, `.badge-descuento`,
 * `.precio-ausente`, `.habilidades` y `.btn-add` se conservan: las usan el
 * banco E2E, la prueba del profesor y el laboratorio visual.
 *
 * @module cuentas/tienda-producto
 */

import { clases, h } from '../comun/ui/dom.js';
import { icono } from '../comun/ui/icono.js';
import { ICONO_DEL_TIPO } from '../contenido/inventario/vitrina.js';
import { NOMBRE_DEL_TIPO } from '../contenido/inventario/ficha-producto.js';
import { aProductoDeVitrina } from './tienda-adaptador.js';

/** Dónde se pinta: la tienda (con sesión y carrito) o la portada pública. */
export const MODOS = Object.freeze({ TIENDA: 'tienda', PORTADA: 'portada' });

/**
 * El precio (PriceTag): lo que se cobra, lo que costaba antes si hubo rebaja
 * de verdad y el porcentaje.
 *
 * @param {ReturnType<typeof aProductoDeVitrina>} producto
 * @param {{grande?: boolean}} [opciones]
 * @returns {HTMLElement}
 */
export function precioDeProducto(producto, { grande = false } = {}) {
  const cifra = h('span', {
    clase: clases('price', producto.precioTexto === null && 'precio-ausente'),
    texto: producto.precioTexto ?? 'Precio no disponible',
  });
  return h('span', {
    clase: clases('precio-bloque', grande && 'precio-bloque--grande'),
    hijos: [
      cifra,
      producto.precioAnteriorTexto
        ? h('s', {
            clase: 'price-antes',
            texto: producto.precioAnteriorTexto,
            atributos: { 'aria-label': `Antes ${producto.precioAnteriorTexto}` },
          })
        : null,
      producto.descuento !== null
        ? h('span', {
            clase: 'badge-descuento',
            texto: `-${producto.descuento}%`,
            atributos: { 'aria-label': `${producto.descuento} % de descuento` },
          })
        : null,
    ],
  });
}

/**
 * «Ya lo tienes» (OwnedBadge).
 *
 * @param {number} unidades cuántas tiene el jugador (≥ 1)
 * @returns {HTMLElement}
 */
export function distintivoDePropiedad(unidades) {
  return h('span', {
    clase: 'distintivo distintivo--activo producto-propio',
    datos: { unidades: String(unidades) },
    hijos: [
      icono('check', { clase: 'icono producto-propio__icono', etiqueta: null }),
      h('span', { texto: unidades > 1 ? `Tienes ${unidades}` : 'Ya lo tienes' }),
    ],
  });
}

/**
 * «En tu lista de deseos»: solo si el servicio lo dice (`enListaDeseos`).
 *
 * @returns {HTMLElement}
 */
export function distintivoDeDeseo() {
  return h('span', {
    clase: 'distintivo distintivo--privada producto-deseado',
    hijos: [
      icono('corazon', { clase: 'icono producto-propio__icono', etiqueta: null }),
      h('span', { texto: 'En tu lista de deseos' }),
    ],
  });
}

/**
 * El control de la lista de deseos (WishlistToggle), sin servicio que la
 * guarde: a la vista, deshabilitado y con el motivo al lado.
 *
 * `aria-disabled` y no `disabled`: así se alcanza con el teclado y el lector
 * lee el motivo (`aria-describedby`), que es justo lo que hay que contar.
 *
 * @param {{idMotivo: string}} opciones
 * @returns {HTMLElement}
 */
export function conmutadorDeDeseos({ idMotivo }) {
  const boton = h('button', {
    clase: 'boton boton--contorno boton--pequeno deseos',
    datos: { accion: 'lista-de-deseos' },
    atributos: {
      type: 'button',
      'aria-disabled': 'true',
      'aria-pressed': 'false',
      'aria-describedby': idMotivo,
    },
    hijos: [
      icono('corazon', { clase: 'icono deseos__icono', etiqueta: null }),
      h('span', { texto: 'Lista de deseos' }),
    ],
  });
  return h('div', {
    clase: 'deseos__caja',
    hijos: [
      boton,
      h('p', {
        clase: 'deseos__motivo',
        texto:
          'Todavía no se puede guardar: la tienda aún no conserva listas de deseos en tu cuenta.',
        atributos: { id: idMotivo },
      }),
    ],
  });
}

/**
 * La caja de la imagen: la del catálogo, o el símbolo del tipo sobre la
 * ranura oscura del kit cuando no la hay.
 *
 * @param {ReturnType<typeof aProductoDeVitrina>} producto
 * @returns {HTMLElement}
 */
function imagenDeProducto(producto) {
  const caja = h('div', { clase: 'product-image' });
  if (producto.imagenUrl) {
    caja.classList.add('con-imagen');
    caja.append(
      h('img', {
        atributos: { src: producto.imagenUrl, alt: '', loading: 'lazy', decoding: 'async' },
      }),
    );
  } else {
    caja.append(
      icono(ICONO_DEL_TIPO[producto.tipo] ?? 'estrella', {
        clase: 'icono product-image__simbolo',
        etiqueta: null,
      }),
    );
  }
  return caja;
}

/**
 * Tarjeta de un producto (ProductCard).
 *
 * @param {object} dto `ProductoDeVitrina` tal cual (ecommerce-carrito.yaml 1.2.0)
 * @param {{modo?: string, unidadesPropias?: number, nivel?: number}} [opciones]
 * @returns {HTMLElement} `article.product-card`
 */
export function tarjetaDeProducto(
  dto,
  { modo = MODOS.TIENDA, unidadesPropias = 0, nivel = 3 } = {},
) {
  const producto = aProductoDeVitrina(dto);
  const nombreDelTipo = NOMBRE_DEL_TIPO[producto.tipo] ?? producto.tipo;
  const tarjeta = h('article', {
    clase: 'product-card',
    datos: {
      tipo: producto.tipo,
      ...(producto.id !== null ? { idProducto: String(producto.id) } : {}),
    },
  });
  if (unidadesPropias > 0 || producto.esPropio) {
    tarjeta.dataset.propio = 'si';
  }
  if (producto.enListaDeseos) {
    tarjeta.dataset.deseado = 'si';
  }

  const distintivos = [
    unidadesPropias > 0 || producto.esPropio
      ? distintivoDePropiedad(Math.max(unidadesPropias, 1))
      : null,
    producto.enListaDeseos ? distintivoDeDeseo() : null,
  ].filter(Boolean);

  const ver = h('button', {
    clase: 'boton boton--secundario boton--pequeno product-card__ver',
    datos: producto.id !== null ? { verProducto: String(producto.id) } : {},
    atributos: {
      type: 'button',
      disabled: producto.id === null,
      // El nombre accesible empieza por lo que se lee (WCAG 2.5.3).
      'aria-label': `Ver producto: ${producto.nombre || 'sin nombre'}`,
    },
    texto: 'Ver producto',
  });

  const acciones = [ver];
  if (modo === MODOS.TIENDA) {
    // Sin id no hay nada que añadir: el botón se apaga en vez de mandar
    // `undefined` al servicio. R16 — el id es el UUID del catálogo, en texto.
    const anadir = h('button', {
      clase: 'btn-add',
      texto: 'Añadir',
      atributos: { type: 'button', disabled: producto.id === null },
    });
    if (producto.id === null) {
      anadir.title = 'Este producto llegó incompleto y no se puede añadir al carrito.';
    } else {
      anadir.dataset.producto = String(producto.id);
      anadir.setAttribute('aria-label', `Añadir ${producto.nombre} al carrito`);
    }
    acciones.push(anadir);
  }

  // `append` nativo escribiria «null» por cada hueco: se filtran antes.
  const partes = [
    imagenDeProducto(producto),
    h('p', {
      clase: 'product-card__tipo',
      hijos: [
        icono(ICONO_DEL_TIPO[producto.tipo] ?? 'estrella', {
          clase: 'icono product-card__tipo-icono',
          etiqueta: null,
        }),
        h('span', { texto: nombreDelTipo || 'Producto' }),
      ],
    }),
    h(`h${nivel}`, { clase: 'product-card__nombre', texto: producto.nombre }),
    distintivos.length > 0
      ? h('div', { clase: 'product-card__distintivos', hijos: distintivos })
      : null,
    producto.descripcion
      ? h('p', { clase: 'product-card__descripcion', texto: producto.descripcion })
      : null,
    producto.habilidades ? h('p', { clase: 'habilidades', texto: producto.habilidades }) : null,
    h('div', {
      clase: 'product-footer',
      hijos: [
        precioDeProducto(producto),
        h('div', { clase: 'product-card__acciones', hijos: acciones }),
      ],
    }),
  ];
  tarjeta.append(...partes.filter(Boolean));
  return tarjeta;
}

/** Un botón que espera la respuesta: apagado y con `aria-busy`. */
function ocupado(boton, activo) {
  boton.disabled = activo;
  if (activo) {
    boton.setAttribute('aria-busy', 'true');
  } else {
    boton.removeAttribute('aria-busy');
  }
}

/** Lo que pasó al añadir, escrito junto al botón. */
function contarResultado(zona, salida) {
  zona.hidden = false;
  zona.dataset.tono = salida?.ok ? 'exito' : 'advertencia';
  zona.textContent = salida?.ok
    ? 'Añadido a tu carrito.'
    : [salida?.titulo, salida?.detalle].filter(Boolean).join('. ');
}

/**
 * El bloque de compra del detalle (la ficha con `contexto: 'tienda'` o
 * `'portada'`): precio, si ya lo tienes, añadir al carrito o entrar para
 * comprar, y la lista de deseos con su motivo.
 *
 * @param {object} dto el `ProductoDeVitrina` de la tarjeta que se abrió
 * @param {{modo?: string, unidadesPropias?: number,
 *          alAnadir?: (productoId: string) => Promise<{ok: boolean, titulo?: string, detalle?: string}>,
 *          alEntrar?: () => void}} opciones
 * @returns {HTMLElement}
 */
export function bloqueDeCompra(
  dto,
  { modo = MODOS.TIENDA, unidadesPropias = 0, alAnadir, alEntrar } = {},
) {
  const producto = aProductoDeVitrina(dto);
  const idMotivo = `deseos-motivo-${String(producto.id ?? 'sin-id')}`;
  const resultado = h('p', {
    clase: 'compra-producto__resultado',
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  resultado.hidden = true;

  const hijos = [
    h('div', {
      clase: 'compra-producto__precio',
      hijos: [
        h('p', { clase: 'compra-producto__etiqueta', texto: 'Precio' }),
        precioDeProducto(producto, { grande: true }),
      ],
    }),
  ];
  if (unidadesPropias > 0) {
    hijos.push(
      h('p', {
        clase: 'compra-producto__propio',
        hijos: [
          distintivoDePropiedad(unidadesPropias),
          h('span', {
            texto:
              unidadesPropias > 1
                ? ` Ya tienes ${unidadesPropias} en tu inventario.`
                : ' Ya tienes uno en tu inventario.',
          }),
        ],
      }),
    );
  }

  const acciones = h('div', { clase: 'compra-producto__acciones' });
  if (modo === MODOS.TIENDA) {
    const anadir = h('button', {
      clase: 'boton boton--primario',
      datos: { accion: 'anadir-al-carrito' },
      atributos: { type: 'button', disabled: producto.id === null },
      hijos: [
        icono('carrito', { clase: 'icono', etiqueta: null }),
        h('span', { texto: 'Añadir al carrito' }),
      ],
    });
    anadir.addEventListener('click', async () => {
      if (!alAnadir || producto.id === null) {
        return;
      }
      ocupado(anadir, true);
      const salida = await alAnadir(String(producto.id));
      ocupado(anadir, false);
      contarResultado(resultado, salida);
    });
    acciones.append(anadir);
  } else {
    const entrar = h('button', {
      clase: 'boton boton--primario',
      texto: 'Entra para comprar',
      datos: { accion: 'entrar-para-comprar' },
      atributos: { type: 'button' },
    });
    entrar.addEventListener('click', () => alEntrar?.());
    acciones.append(entrar);
    hijos.push(
      h('p', {
        clase: 'compra-producto__nota',
        texto: 'Con tu cuenta lo añades al carrito, lo calificas y opinas sobre él.',
      }),
    );
  }
  hijos.push(acciones, resultado, conmutadorDeDeseos({ idMotivo }));

  return h('section', {
    clase: 'compra-producto',
    atributos: { 'aria-label': 'Comprar' },
    hijos,
  });
}
