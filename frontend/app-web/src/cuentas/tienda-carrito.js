/**
 * El carrito minimizado y desplegado — UXC-4 (CartBadge, CartDrawer).
 *
 * §7.5 del documento: el carrito está disponible en las vistas del comercio y
 * tiene una «vista desplegada y minimizada»; minimizado, un icono con el
 * número de productos. Hasta aquí el carrito era una columna fija que en
 * escritorio le quitaba un tercio del ancho a la vitrina y no se podía
 * recoger.
 *
 * Aquí:
 *
 *   - la **insignia** (icono + unidades) vive en la cabecera de la tienda y
 *     despliega o minimiza el panel (`aria-expanded`, `aria-controls`);
 *   - **desplegado** es la columna de siempre en escritorio y, en pantalla
 *     estrecha, el bloque debajo de la vitrina, como antes;
 *   - **minimizado**, la vitrina ocupa todo el ancho y la insignia sigue
 *     diciendo cuántos productos hay.
 *
 * Desplegar o minimizar no vuelve a pintar la vitrina ni la mueve de sitio:
 * se conserva el contexto (la página, los filtros, lo que se estaba mirando).
 *
 * El carrito en sí lo guarda el servicio (`GET /carrito`, por usuario). En el
 * navegador solo se recuerda si el panel estaba desplegado o minimizado, una
 * comodidad de quien mira; si el almacenamiento falla, no se recuerda y ya.
 *
 * @module cuentas/tienda-carrito
 */

/** Clave de la preferencia (solo desplegado/minimizado, nunca el contenido). */
export const CLAVE_PANEL = 'nexus.tienda.panelCarrito';

/** Los dos estados del panel. */
export const ESTADOS_DEL_PANEL = Object.freeze({
  DESPLEGADO: 'desplegado',
  MINIMIZADO: 'minimizado',
});

/**
 * Unidades en el carrito: la suma de las cantidades.
 *
 * @param {{items?: Array<{cantidad?: number}>}|null} carrito
 * @returns {number}
 */
export function unidadesDelCarrito(carrito) {
  const items = Array.isArray(carrito?.items) ? carrito.items : [];
  return items.reduce(
    (suma, item) =>
      suma + (Number.isFinite(item?.cantidad) && item.cantidad > 0 ? item.cantidad : 0),
    0,
  );
}

/**
 * «1 producto», «3 productos».
 *
 * @param {number} unidades
 * @returns {string}
 */
export function textoDeUnidades(unidades) {
  return unidades === 1 ? '1 producto' : `${unidades} productos`;
}

/**
 * Pone el número en la insignia y su nombre accesible.
 *
 * @param {HTMLElement|null} insignia `#insignia-carrito`
 * @param {number|null} unidades `null` mientras no se sabe
 */
export function pintarInsignia(insignia, unidades) {
  if (!insignia) {
    return;
  }
  const cuenta = insignia.querySelector('[data-zona="unidades"]');
  if (cuenta) {
    cuenta.textContent = unidades === null ? '—' : String(unidades);
  }
  insignia.dataset.unidades = unidades === null ? '' : String(unidades);
  insignia.setAttribute(
    'aria-label',
    unidades === null ? 'Carrito' : `Carrito, ${textoDeUnidades(unidades)}`,
  );
}

function leerPreferencia(almacen) {
  try {
    return almacen?.getItem(CLAVE_PANEL) ?? null;
  } catch {
    return null;
  }
}

function guardarPreferencia(almacen, valor) {
  try {
    almacen?.setItem(CLAVE_PANEL, valor);
  } catch {
    // Sin almacenamiento (ventana privada, bloqueado): no se recuerda y ya.
  }
}

/**
 * Engancha la insignia, el botón de minimizar y el panel.
 *
 * @param {Document} doc
 * @param {{almacen?: Storage|null}} [opciones]
 * @returns {{desplegar: (opciones?: {enfocar?: boolean}) => void,
 *   minimizar: (opciones?: {enfocar?: boolean}) => void, desplegado: () => boolean}|null}
 *   `null` si la vista no tiene la insignia y el panel (pruebas con una vista mínima)
 */
export function montarCajonDelCarrito(
  doc = document,
  { almacen = globalThis.localStorage ?? null } = {},
) {
  const contenedor = doc.querySelector('.main-container');
  const panel = doc.getElementById('panel-carrito');
  const insignia = doc.getElementById('insignia-carrito');
  const minimizarBoton = doc.getElementById('minimizar-carrito');
  if (!contenedor || !panel || !insignia) {
    return null;
  }

  const aplicar = (estado, { enfocar = false } = {}) => {
    const desplegado = estado === ESTADOS_DEL_PANEL.DESPLEGADO;
    contenedor.dataset.carrito = estado;
    insignia.setAttribute('aria-expanded', String(desplegado));
    panel.hidden = !desplegado;
    if (!enfocar) {
      return;
    }
    if (desplegado) {
      // En pantalla estrecha el panel queda debajo de la vitrina: se lleva
      // hasta él. En escritorio aparece al lado y el foco va a su título.
      panel.scrollIntoView?.({ block: 'start', behavior: 'smooth' });
      panel.querySelector('[data-zona="titulo-carrito"]')?.focus();
    } else {
      insignia.focus();
    }
  };

  const desplegar = (opciones) => {
    aplicar(ESTADOS_DEL_PANEL.DESPLEGADO, opciones);
    guardarPreferencia(almacen, ESTADOS_DEL_PANEL.DESPLEGADO);
  };
  const minimizar = (opciones) => {
    aplicar(ESTADOS_DEL_PANEL.MINIMIZADO, opciones);
    guardarPreferencia(almacen, ESTADOS_DEL_PANEL.MINIMIZADO);
  };

  insignia.addEventListener('click', () => {
    if (contenedor.dataset.carrito === ESTADOS_DEL_PANEL.DESPLEGADO) {
      minimizar({ enfocar: true });
    } else {
      desplegar({ enfocar: true });
    }
  });
  minimizarBoton?.addEventListener('click', () => minimizar({ enfocar: true }));

  // De entrada, lo que la persona dejó; desplegado si nunca eligió.
  aplicar(
    leerPreferencia(almacen) === ESTADOS_DEL_PANEL.MINIMIZADO
      ? ESTADOS_DEL_PANEL.MINIMIZADO
      : ESTADOS_DEL_PANEL.DESPLEGADO,
  );

  return {
    desplegar: (opciones = {}) => desplegar(opciones),
    minimizar: (opciones = {}) => minimizar(opciones),
    desplegado: () => contenedor.dataset.carrito === ESTADOS_DEL_PANEL.DESPLEGADO,
  };
}
