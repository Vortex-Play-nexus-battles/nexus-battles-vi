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
 *     (`propiedadesDelJugador`) o la vitrina con sesión (`esPropio`, 1.4.0).
 *   - **Lista de deseos** (B5): la guarda el servicio (`PUT`/`DELETE
 *     /lista-deseos/{id}`, ecommerce-carrito.yaml 1.4.0) y la vitrina con
 *     sesión la marca (`enListaDeseos`). En la tienda el conmutador está en la
 *     tarjeta y en el detalle, con `aria-pressed`; lo deseado lleva borde,
 *     distintivo y corazón relleno. En la portada, sin cuenta, se ve apagado y
 *     dice que hay que entrar. Nada se guarda en el navegador.
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
  if (producto.soloEnCreditos) {
    return precioSoloEnCreditos(producto, { grande });
  }
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
      // D-44: el otro precio, si se puede pagar con créditos del juego. La
      // cifra la calculó el servidor (precioCreditos del catálogo, promoción
      // incluida); aquí solo se escribe.
      producto.precioCreditosTexto
        ? h('span', {
            clase: 'precio-creditos',
            texto: `o ${producto.precioCreditosTexto}`,
            datos: { precioCreditos: String(producto.precioCreditos) },
          })
        : null,
    ],
  });
}

/**
 * G3 (ecommerce-carrito 1.7.0): un producto que solo se vende en créditos del
 * juego. Su precio ES el de créditos —va en `.price`, con `data-precio-creditos`
 * como el «o N créditos» de los que tienen los dos— y se dice que con tarjeta
 * no se paga. Nunca «Precio no disponible» ni «0 COP».
 *
 * @param {ReturnType<typeof aProductoDeVitrina>} producto
 * @param {{grande?: boolean}} [opciones]
 * @returns {HTMLElement}
 */
function precioSoloEnCreditos(producto, { grande = false } = {}) {
  return h('span', {
    clase: clases('precio-bloque', 'precio-bloque--creditos', grande && 'precio-bloque--grande'),
    datos: { soloEnCreditos: 'si' },
    hijos: [
      h('span', {
        clase: 'price precio-creditos--principal',
        texto: producto.precioCreditosTexto,
        datos: { precioCreditos: String(producto.precioCreditos) },
      }),
      h('span', { clase: 'precio-solo-creditos', texto: 'Solo con créditos del juego' }),
    ],
  });
}

/** Por qué no se ofrece «Añadir» de lo que ya se tiene (RF-CAR-004, ecommerce-carrito 1.5.0). */
export const MOTIVO_YA_LO_TIENES =
  'Ya lo tienes en tu inventario: la tienda no lo vende dos veces.';

/**
 * Apaga «Añadir» de un producto que el jugador ya tiene. RF-CAR-004 pone
 * «producto ya adquirido por el cliente» entre las excepciones de añadir a la
 * cesta, y el servicio lo rechaza (409 `producto-ya-adquirido`): ofrecerlo
 * sería invitar a un rechazo seguro (auditoría de DEV del 30-sep).
 *
 * @param {HTMLButtonElement} boton
 */
export function apagarAnadirPorPropio(boton) {
  boton.disabled = true;
  boton.title = MOTIVO_YA_LO_TIENES;
  boton.dataset.motivo = 'propio';
  delete boton.dataset.producto;
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
 * El nombre accesible del conmutador: empieza por lo que se lee en él (WCAG
 * 2.5.3) y dice de qué producto es. No cambia al pulsarlo: el estado lo
 * anuncia `aria-pressed`.
 *
 * @param {string} nombre
 * @returns {string}
 */
export function etiquetaDelConmutadorDeDeseos(nombre) {
  return nombre ? `Lista de deseos: ${nombre}` : 'Lista de deseos';
}

/**
 * El control de la lista de deseos (WishlistToggle).
 *
 * - **Activo** (la tienda, con sesión y un producto con id): un botón con
 *   `aria-pressed` y `data-deseo`, que la vista engancha para llamar al
 *   servicio.
 * - **Apagado** (la portada, sin cuenta, o un producto sin id):
 *   `aria-disabled` y no `disabled`, para que se alcance con el teclado y el
 *   lector lea el motivo (`aria-describedby`), que es justo lo que hay que
 *   contar.
 *
 * @param {{idMotivo: string, productoId?: (string|null), nombre?: string,
 *          deseado?: boolean, activo?: boolean, motivo?: string}} opciones
 * @returns {HTMLElement}
 */
export function conmutadorDeDeseos({
  idMotivo,
  productoId = null,
  nombre = '',
  deseado = false,
  activo = false,
  motivo = 'Entra con tu cuenta para guardarlo en tu lista de deseos.',
}) {
  const puedeUsarse = activo && productoId !== null;
  const boton = h('button', {
    clase: 'boton boton--contorno boton--pequeno deseos',
    datos: puedeUsarse
      ? { accion: 'lista-de-deseos', deseo: String(productoId) }
      : { accion: 'lista-de-deseos' },
    atributos: {
      type: 'button',
      'aria-pressed': String(puedeUsarse && deseado),
      ...(puedeUsarse
        ? { 'aria-label': etiquetaDelConmutadorDeDeseos(nombre) }
        : { 'aria-disabled': 'true', 'aria-describedby': idMotivo }),
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
      puedeUsarse
        ? null
        : h('p', { clase: 'deseos__motivo', texto: motivo, atributos: { id: idMotivo } }),
    ],
  });
}

/**
 * El conmutador de la tarjeta: solo el corazón, con su nombre accesible, para
 * que quepa junto a «Ver producto» y «Añadir» también en un teléfono.
 *
 * @param {ReturnType<typeof aProductoDeVitrina>} producto
 * @returns {HTMLElement}
 */
function conmutadorDeTarjeta(producto) {
  return h('button', {
    clase: 'boton boton--contorno boton--pequeno deseos deseos--tarjeta',
    datos: { deseo: String(producto.id) },
    atributos: {
      type: 'button',
      title: 'Lista de deseos',
      'aria-pressed': String(producto.enListaDeseos),
      'aria-label': etiquetaDelConmutadorDeDeseos(producto.nombre),
    },
    hijos: [icono('corazon', { clase: 'icono deseos__icono', etiqueta: null })],
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
  const simbolo = () =>
    icono(ICONO_DEL_TIPO[producto.tipo] ?? 'estrella', {
      clase: 'icono product-image__simbolo',
      etiqueta: null,
    });
  if (producto.imagenUrl) {
    caja.classList.add('con-imagen');
    const imagen = h('img', {
      atributos: { src: producto.imagenUrl, alt: '', loading: 'lazy', decoding: 'async' },
    });
    // La imagen la escribe quien da de alta el producto y puede no existir
    // (en DEV hay productos con «espada.png», una ruta que no sirve nadie):
    // una imagen rota no se enseña, se cambia por el símbolo del tipo, igual
    // que cuando el catálogo no trae ninguna.
    imagen.addEventListener(
      'error',
      () => {
        caja.classList.remove('con-imagen');
        caja.dataset.imagen = 'rota';
        imagen.replaceWith(simbolo());
      },
      { once: true },
    );
    caja.append(imagen);
  } else {
    caja.append(simbolo());
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
      if (unidadesPropias > 0 || producto.esPropio) {
        apagarAnadirPorPropio(anadir);
      }
    }
    acciones.push(anadir);
    // B5 — «añadir a la lista de deseos» en el área de cada producto (§7.5).
    if (producto.id !== null) {
      acciones.push(conmutadorDeTarjeta(producto));
    }
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
 * comprar, y la lista de deseos.
 *
 * @param {object} dto el `ProductoDeVitrina` de la tarjeta que se abrió
 * @param {{modo?: string, unidadesPropias?: number,
 *          alAnadir?: (productoId: string) => Promise<{ok: boolean, titulo?: string, detalle?: string}>,
 *          alDesear?: (productoId: string) => Promise<{ok: boolean, texto: string}>,
 *          alEntrar?: () => void}} opciones
 *   `alDesear` añade o quita el producto de la lista y dice qué pasó; la vista
 *   repinta el conmutador con lo que confirme el servicio.
 * @returns {HTMLElement}
 */
export function bloqueDeCompra(
  dto,
  { modo = MODOS.TIENDA, unidadesPropias = 0, alAnadir, alDesear, alEntrar } = {},
) {
  const producto = aProductoDeVitrina(dto);
  // Lo que dice el inventario o, si aún no contestó, la marca de la vitrina.
  const propias = Math.max(unidadesPropias, producto.esPropio ? 1 : 0);
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
  if (propias > 0) {
    hijos.push(
      h('p', {
        clase: 'compra-producto__propio',
        hijos: [
          distintivoDePropiedad(propias),
          h('span', {
            texto:
              propias > 1
                ? ` Ya tienes ${propias} en tu inventario.`
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
    if (propias > 0 && producto.id !== null) {
      anadir.disabled = true;
      anadir.title = MOTIVO_YA_LO_TIENES;
      anadir.dataset.motivo = 'propio';
    }
    anadir.addEventListener('click', async () => {
      if (!alAnadir || producto.id === null || anadir.disabled) {
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
  const deseos = conmutadorDeDeseos({
    idMotivo,
    productoId: producto.id,
    nombre: producto.nombre,
    deseado: producto.enListaDeseos,
    activo: modo === MODOS.TIENDA && typeof alDesear === 'function',
  });
  const conmutador = deseos.querySelector('[data-deseo]');
  conmutador?.addEventListener('click', async () => {
    ocupado(conmutador, true);
    const salida = await alDesear(String(producto.id));
    ocupado(conmutador, false);
    resultado.hidden = false;
    resultado.dataset.tono = salida?.ok ? 'exito' : 'advertencia';
    resultado.textContent = salida?.texto ?? '';
  });
  hijos.push(acciones, resultado, deseos);

  return h('section', {
    clase: 'compra-producto',
    atributos: { 'aria-label': 'Comprar' },
    hijos,
  });
}
