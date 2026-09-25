/**
 * Vitrina y carrito — HU-CAR-001.
 *
 * Habla con `ms-ecommerce`: `GET /api/v1/vitrina`, `GET /api/v1/carrito` y
 * `POST /api/v1/carrito/items` (`contracts/openapi/ecommerce-carrito.yaml`).
 *
 * ## Qué cambió en R16
 *
 * 1. **La vitrina tiene prefijo propio.** Antes pedía `GET /api/v1/productos`,
 *    que el borde repartía por método para que llegara a ms-ecommerce (#421).
 *    Ese prefijo es ahora del catálogo maestro entero, y la vitrina vive en
 *    `/api/v1/vitrina` (contrato 1.2.0): proyecta ese catálogo, así que por fin
 *    enseña los productos que existen, en vez de una tabla propia vacía.
 * 2. **Los identificadores son UUID en texto.** Salen del catálogo y viajan
 *    tal cual hasta `POST /carrito/items`; nada los convierte en número.
 * 3. **«Añadir» ya no falla en silencio.** Un producto agotado, suspendido,
 *    sin precio en dinero real o un catálogo caído llegan como problem details
 *    (409, 422, 503), y el jugador ve un aviso corto con una salida. Antes solo
 *    quedaba un `console.error`, y ni eso: el interceptor compartido no lanza
 *    ante un 4xx o un 5xx, así que el rechazo se tomaba por un éxito.
 * 4. **Por la misma razón, la vitrina y el carrito comprueban `ok`.** Un 503
 *    del catálogo se pintaba como «la tienda no tiene productos» y un 500 del
 *    carrito como «tu carrito está vacío»: las dos cosas que sus propios
 *    comentarios decían evitar, y que sus pruebas solo simulaban con un
 *    `fetch` que lanza.
 *
 * ## Qué se corrigió aquí (P3.1)
 *
 * 1. **La identidad era falsa.** Había un `const USER_ID = 'usr_test_123'` que
 *    viajaba como `X-User-Id` en cada petición: *todos* los compradores eran el
 *    mismo usuario de prueba. Ahora sale del token de la sesión, igual que en
 *    el resto del frontend.
 * 2. **`API_BASE_URL` no estaba declarada.** Creaba un global implícito — no
 *    reventaba porque la página la cargaba como script clásico, pero eran
 *    cuatro errores de ESLint y una trampa: el propio archivo avisaba de que al
 *    pasar a módulo se rompería. Ahora usa `baseDeApi()`, el mismo mecanismo
 *    que el resto de clientes.
 * 3. **El interceptor era una copia local de seis líneas** que perdía el
 *    formato de error de la plataforma. Ahora usa el compartido, que además
 *    adjunta el `Authorization: Bearer`.
 * 4. **Los botones iban por `onclick` en el HTML generado.** Al pasar a módulo
 *    dejarían de encontrar la función —tal como avisaba el comentario— así que
 *    se sustituyen por delegación de eventos.
 *
 * ## UXC-4 — la tienda que pide §7.5
 *
 * 1. **Buscar y filtrar.** Por texto (nombre, tipo, habilidades y precio),
 *    tipo, rango de precio y «solo en promoción», con orden por precio o
 *    nombre. La vitrina no filtra así, de modo que se reúne entera y se
 *    filtra aquí (`tienda-catalogo.js`); se pagina de dieciséis en dieciséis.
 * 2. **El detalle.** «Ver producto» abre la ficha del catálogo con el bloque
 *    de compra y las opiniones de la comunidad (calificación promedio e hilo).
 * 3. **Lo que ya tienes.** Se marca con lo que dice el inventario del jugador.
 * 4. **El carrito se minimiza.** Insignia con las unidades en la cabecera;
 *    el panel se despliega o se recoge sin mover la vitrina
 *    (`tienda-carrito.js`). Cada línea suma una unidad o se quita.
 *
 * @module tienda
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { rutaDeApi } from '../comun/base-api.js';
import { usuarioIdDeSesion } from '../comun/identidad.js';
import { exigirSesion } from '../comun/acceso.js';
import { olvidarSesion } from '../comun/sesion.js';
import { limpiarAviso, pintarAviso, tonoPorEstado } from '../comun/ui/aviso.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../comun/ui/estado-vista.js';
import { h } from '../comun/ui/dom.js';
import { construirPaginacion } from '../comun/paginacion.js';
import { abrirFicha } from '../contenido/inventario/ficha-producto.js';
import { complementoDeOpiniones } from '../plataforma/comentarios/hilo-comentarios.js';
import { aProductoDeVitrina, aFilaDeCarrito, textoDePrecio, aImporte } from './tienda-adaptador.js';
import {
  PRODUCTOS_POR_PAGINA,
  filtrarProductos,
  hayCriterios,
  propiedadesDelJugador,
  reunirVitrina,
} from './tienda-catalogo.js';
import {
  MODOS,
  bloqueDeCompra,
  distintivoDePropiedad,
  tarjetaDeProducto,
} from './tienda-producto.js';
import {
  montarCajonDelCarrito,
  pintarInsignia,
  textoDeUnidades,
  unidadesDelCarrito,
} from './tienda-carrito.js';
import { textoDelServidor } from '../comun/ui/texto-de-fallo.js';

/** `type` del problem detail cuando el catálogo maestro no responde (contrato 1.2.0). */
const TIPO_CATALOGO_NO_DISPONIBLE = 'urn:nexus:problema:catalogo-no-disponible';

/**
 * Lo que se le dice al jugador cuando «Añadir» falla por un motivo conocido.
 *
 * La clave es el `type` del problem detail: la interfaz decide por él y nunca
 * por el texto del servidor, que puede cambiar de redacción sin aviso
 * (`shared/ui-kit/MAPEO-ERRORES.md` §2). Los cinco son los que declara
 * `POST /carrito/items` en `ecommerce-carrito.yaml` 1.2.0.
 */
const MOTIVOS_DEL_CARRITO = Object.freeze({
  'urn:nexus:problema:producto-inexistente': {
    titulo: 'Ese producto ya no está en el catálogo',
    detalle: 'Actualiza la tienda para ver lo que sí está a la venta.',
  },
  'urn:nexus:problema:producto-no-disponible': {
    titulo: 'Ese producto no está a la venta ahora mismo',
    detalle: 'Actualiza la tienda para ver lo que sí está a la venta.',
  },
  'urn:nexus:problema:producto-agotado': {
    titulo: 'Ese producto se agotó',
    detalle: 'Ya no quedan unidades. Actualiza la tienda para ver lo que sí está a la venta.',
  },
  'urn:nexus:problema:producto-sin-precio-en-moneda-real': {
    titulo: 'Ese producto no se vende con dinero real',
    detalle: 'En la tienda solo se compra lo que tiene precio en dinero real.',
  },
  [TIPO_CATALOGO_NO_DISPONIBLE]: {
    titulo: 'La tienda no puede consultar el catálogo ahora mismo',
    detalle: 'Tu carrito no cambió. Inténtalo de nuevo en unos segundos.',
  },
});

/**
 * El problem detail de una respuesta que no fue bien, o `null` si no trae
 * uno legible (un 502 del borde, por ejemplo, es HTML).
 *
 * @param {Response} respuesta
 * @returns {Promise<object|null>}
 */
async function leerProblema(respuesta) {
  try {
    const cuerpo = await respuesta.json();
    return cuerpo && typeof cuerpo === 'object' ? cuerpo : null;
  } catch {
    return null;
  }
}

/**
 * Una respuesta que no es 2xx, convertida en un `Error` con su `estado` y su
 * problem detail.
 *
 * Hace falta porque `fetchWithHttpErrorInterceptor` devuelve la respuesta tal
 * cual —solo anuncia el 403—: sin esto, el 503 de una vitrina sin catálogo se
 * leía como un cuerpo sin `content`, y la tienda decía «no tiene productos»
 * cuando lo que pasaba era que no podía preguntarlo.
 *
 * @param {Response} respuesta
 * @returns {Promise<Error & {estado: number, problema: object|null}>}
 */
async function errorDeRespuesta(respuesta) {
  const error = new Error(`HTTP ${respuesta.status}`);
  error.estado = respuesta.status;
  error.problema = await leerProblema(respuesta);
  return error;
}

/**
 * Cabeceras de cada petición.
 *
 * La identidad ya no viaja en `X-User-Id`: `ms-ecommerce` toma el usuario del
 * `uid` del JWT, que `fetchWithHttpErrorInterceptor` pone en `Authorization`
 * (ADR-002). Una cabecera que el navegador escribía no identificaba a nadie.
 * Sin sesión el backend responde 401, y el carrito de nadie se mezcla.
 */
function cabeceras() {
  return { 'Content-Type': 'application/json' };
}

/** @returns {boolean} true si hay una sesión utilizable */
export function haySesion() {
  return usuarioIdDeSesion() !== null;
}

// --- RENDERIZADO DE PRODUCTOS (HU-CAR-001) ---

/**
 * Lo que la vista sabe de sí misma: la colección reunida, los criterios de
 * búsqueda, la página y lo que ya tiene el jugador. Uno por documento, para
 * que las pruebas (y cualquier vista que monte la tienda dos veces) no se
 * pisen.
 *
 * @type {WeakMap<Document, {productos: object[], completo: boolean, criterios: object,
 *   pagina: number, propias: Map<string, number>, cajon: object|null}>}
 */
const ESTADOS = new WeakMap();

/** @param {Document} doc */
function estadoDe(doc) {
  if (!ESTADOS.has(doc)) {
    ESTADOS.set(doc, {
      productos: [],
      completo: true,
      criterios: {},
      pagina: 0,
      propias: new Map(),
      cajon: null,
    });
  }
  return ESTADOS.get(doc);
}

export async function cargarVitrina(doc = document) {
  const rejilla = doc.getElementById('productos-grid');
  const vista = estadoDe(doc);

  // UX-R2.8d — no habia estado de carga: el HTML traia un comentario
  // (`<!-- Cargando productos... -->`) donde deberia ir, asi que la rejilla
  // estaba en blanco hasta que llegaba la respuesta. Ahora se ve la forma de
  // lo que viene, como en el resto de la aplicacion (RNF-USA-003).
  pintarEn(rejilla, estadoDeCarga({ filas: 4, etiqueta: 'Cargando la tienda…' }));
  pintarResultado(doc, '');

  try {
    // R16 — `/vitrina` y no `/productos`: ese prefijo es del catálogo maestro.
    // UXC-4 — entera, de cincuenta en cincuenta: buscar y filtrar trabajan
    // sobre toda la tienda, no sobre una página (`tienda-catalogo.js`).
    const { productos, completo } = await reunirVitrina();
    vista.productos = productos;
    vista.completo = completo;
    vista.pagina = 0;
    pintarCatalogo(doc);
  } catch (error) {
    // Antes esto no existía: un fallo dejaba el cargador girando para siempre.
    // `.empty-cart-msg` no existe en ningun CSS (el guardian de clases solo
    // mira el HTML, y esta estaba escrita en JavaScript): el mensaje salia con
    // el estilo por defecto del navegador.
    //
    // UX-R2.8d — y ademas era un callejon sin salida: el mensaje no ofrecia
    // volver a intentarlo, asi que la unica salida era recargar la pagina.
    //
    // R16 — si lo que falta es el catalogo maestro (503 del contrato 1.2.0),
    // se dice eso, y que el carrito sigue: es otra seccion y no depende de el.
    const sinCatalogo = error?.problema?.type === TIPO_CATALOGO_NO_DISPONIBLE;
    pintarEn(
      rejilla,
      estadoDeError({
        titulo: 'No se pudo cargar la tienda',
        detalle: sinCatalogo
          ? 'El catálogo no responde en este momento. Tu carrito sigue disponible.'
          : 'Vuelve a intentarlo en unos momentos.',
        alReintentar: () => cargarVitrina(doc),
      }),
    );
    pintarPaginacion(doc, { paginaActual: 0, totalPaginas: 0 });
    console.error('Error al cargar la vitrina:', error);
  }
}

/**
 * Pinta la página actual de la colección con los criterios actuales.
 *
 * No vuelve a pedir nada: cambiar un filtro, el orden o la página es
 * instantáneo y no pierde lo demás (criterio 3 de HU-INV-011, aplicado aquí).
 *
 * @param {Document} doc
 */
export function pintarCatalogo(doc = document) {
  const rejilla = doc.getElementById('productos-grid');
  const vista = estadoDe(doc);

  if (vista.productos.length === 0) {
    // Un catalogo vacio es un estado legitimo, y distinto de un fallo.
    pintarEn(
      rejilla,
      estadoVacio({
        titulo: 'La tienda no tiene productos ahora mismo',
        detalle: 'Vuelve más tarde: el catálogo lo publica la administración.',
        icono: '◇',
      }),
    );
    pintarResultado(doc, '');
    pintarPaginacion(doc, { paginaActual: 0, totalPaginas: 0 });
    return;
  }

  const modelos = vista.productos.map((dto) => ({ ...aProductoDeVitrina(dto), dto }));
  const encontrados = filtrarProductos(modelos, vista.criterios);

  if (encontrados.length === 0) {
    const { precioMinimo, precioMaximo } = vista.criterios;
    const rangoAlReves =
      Number.isFinite(precioMinimo) && Number.isFinite(precioMaximo) && precioMinimo > precioMaximo;
    pintarEn(
      rejilla,
      estadoVacio({
        titulo: 'Ningún producto coincide',
        detalle: rangoAlReves
          ? 'El precio mínimo es mayor que el máximo. Corrígelo o quita el filtro de precio.'
          : 'Prueba con otras palabras o quita algún filtro: la tienda tiene más productos.',
        icono: '◇',
        accion: {
          texto: 'Limpiar filtros',
          nombre: 'limpiar-filtros',
          alPulsar: () => limpiarFiltros(doc),
        },
      }),
    );
    pintarResultado(doc, `Ningún producto coincide de ${vista.productos.length} a la venta.`);
    pintarPaginacion(doc, { paginaActual: 0, totalPaginas: 0 });
    return;
  }

  const totalPaginas = Math.ceil(encontrados.length / PRODUCTOS_POR_PAGINA);
  vista.pagina = Math.min(Math.max(0, vista.pagina), totalPaginas - 1);
  const desde = vista.pagina * PRODUCTOS_POR_PAGINA;
  rejilla.replaceChildren(
    ...encontrados.slice(desde, desde + PRODUCTOS_POR_PAGINA).map((modelo) =>
      tarjetaDeProducto(modelo.dto, {
        modo: MODOS.TIENDA,
        unidadesPropias: modelo.id === null ? 0 : (vista.propias.get(String(modelo.id)) ?? 0),
      }),
    ),
  );

  const total = vista.productos.length;
  let resumen = hayCriterios(vista.criterios)
    ? `${encontrados.length} de ${total} productos coinciden.`
    : `${textoDeUnidades(total)} a la venta.`;
  if (!vista.completo) {
    resumen +=
      ' Hay más en el catálogo de los que se cargan de una vez: busca o filtra por tipo para encontrarlos.';
  }
  pintarResultado(doc, resumen);
  pintarPaginacion(doc, { paginaActual: vista.pagina, totalPaginas });
}

/** La línea de resultados (`role="status"`), si la vista la tiene. */
function pintarResultado(doc, texto) {
  const zona = doc.getElementById('resultado-tienda');
  if (zona) {
    zona.textContent = texto;
  }
}

/** El control 1…10 bajo la rejilla, si la vista lo tiene. */
function pintarPaginacion(doc, { paginaActual, totalPaginas }) {
  const zona = doc.getElementById('paginacion-tienda');
  if (!zona) {
    return;
  }
  const control = construirPaginacion({ paginaActual, totalPaginas }, (pagina) => {
    estadoDe(doc).pagina = pagina;
    pintarCatalogo(doc);
    // El foco vuelve al principio de la rejilla y la vista sube a ella: si no,
    // quien pagina con el teclado se queda al pie de una página nueva.
    const rejilla = doc.getElementById('productos-grid');
    rejilla?.scrollIntoView?.({ block: 'start' });
    rejilla?.querySelector('.product-card__ver, .btn-add')?.focus();
  });
  control.setAttribute('aria-label', 'Páginas de la tienda');
  zona.replaceChildren(control);
}

/**
 * Marca en las tarjetas ya pintadas lo que el jugador tiene, sin volver a
 * pintarlas (el inventario puede contestar después que la vitrina, y repintar
 * le quitaría el foco a quien ya está navegando).
 *
 * @param {Document} doc
 */
function marcarPropias(doc) {
  const { propias } = estadoDe(doc);
  for (const tarjeta of doc.querySelectorAll('#productos-grid .product-card[data-id-producto]')) {
    const unidades = propias.get(tarjeta.dataset.idProducto) ?? 0;
    if (unidades === 0 || tarjeta.querySelector('.producto-propio')) {
      continue;
    }
    tarjeta.dataset.propio = 'si';
    let zona = tarjeta.querySelector('.product-card__distintivos');
    if (!zona) {
      zona = h('div', { clase: 'product-card__distintivos' });
      tarjeta.querySelector('.product-card__nombre')?.after(zona);
    }
    zona.prepend(distintivoDePropiedad(unidades));
  }
}

/**
 * Criterios del formulario de filtros.
 *
 * @param {HTMLFormElement} formulario
 * @returns {import('./tienda-catalogo.js').Criterios}
 */
export function leerCriterios(formulario) {
  const numero = (nombre) => {
    const valor = formulario.elements.namedItem(nombre)?.value ?? '';
    if (String(valor).trim() === '') {
      return null;
    }
    const n = Number(valor);
    return Number.isFinite(n) && n >= 0 ? n : null;
  };
  return {
    busqueda: formulario.elements.namedItem('busqueda')?.value ?? '',
    tipo: formulario.elements.namedItem('tipo')?.value ?? '',
    precioMinimo: numero('precioMinimo'),
    precioMaximo: numero('precioMaximo'),
    soloPromocion: Boolean(formulario.elements.namedItem('soloPromocion')?.checked),
    orden: formulario.elements.namedItem('orden')?.value ?? 'catalogo',
  };
}

/**
 * «Filtros y orden · 2 activos»: con el panel recogido, que se sepa que hay
 * filtros puestos (la búsqueda está siempre a la vista y no cuenta).
 *
 * @param {HTMLFormElement} formulario
 * @param {import('./tienda-catalogo.js').Criterios} criterios
 */
function pintarResumenDeFiltros(formulario, criterios) {
  const resumen = formulario.querySelector('[data-zona="resumen-filtros"]');
  if (!resumen) {
    return;
  }
  const activos = [
    criterios.tipo,
    Number.isFinite(criterios.precioMinimo),
    Number.isFinite(criterios.precioMaximo),
    criterios.soloPromocion,
  ].filter(Boolean).length;
  let texto = 'Filtros y orden';
  if (activos === 1) {
    texto += ' · 1 activo';
  } else if (activos > 1) {
    texto += ` · ${activos} activos`;
  }
  resumen.textContent = texto;
}

/** Vacía los filtros y vuelve a pintar. */
function limpiarFiltros(doc) {
  const formulario = doc.getElementById('filtros-tienda');
  formulario?.reset();
  const vista = estadoDe(doc);
  vista.criterios = formulario ? leerCriterios(formulario) : {};
  vista.pagina = 0;
  if (formulario) {
    pintarResumenDeFiltros(formulario, vista.criterios);
  }
  pintarCatalogo(doc);
  formulario?.elements.namedItem('busqueda')?.focus();
}

/**
 * ¿Dicen lo mismo dos juegos de criterios? Salen los dos de `leerCriterios`,
 * con las mismas claves en el mismo orden.
 *
 * @param {object} a
 * @param {object} b
 * @returns {boolean}
 */
export function mismosCriterios(a, b) {
  return JSON.stringify(a ?? {}) === JSON.stringify(b ?? {});
}

/**
 * Engancha búsqueda, filtros y orden. El texto espera a que se deje de
 * escribir un momento; los desplegables y la casilla, no.
 *
 * @param {Document} doc
 */
function montarFiltros(doc) {
  const formulario = doc.getElementById('filtros-tienda');
  const vista = estadoDe(doc);
  // Los criterios salen del formulario tal como está al montar: el navegador
  // puede haberlo rellenado al volver atrás, y la vitrina debe coincidir con
  // lo que se ve en los campos.
  vista.criterios = formulario ? leerCriterios(formulario) : {};
  vista.pagina = 0;
  if (!formulario) {
    return;
  }
  // En un teléfono los filtros se recogen: la búsqueda queda a mano y la
  // vitrina no queda debajo de medio metro de campos.
  const mas = formulario.querySelector('.filtros-tienda__mas');
  if (mas && (globalThis.innerWidth ?? 1440) < 600) {
    mas.open = false;
  }
  // La búsqueda y el precio se aplican al escribir (con espera) y además
  // disparan `change` al perder el foco: por ejemplo, al pulsar «Añadir» en
  // una tarjeta. Repintar la vitrina entre el `pointerdown` y el `pointerup`
  // cambia lo que hay debajo del puntero y el clic no llega a ningún botón: el
  // producto no se añadía (lo destapó el E2E de la tienda, que busca y pulsa
  // enseguida). Mientras haya un botón del puntero pulsado, el repintado
  // espera a que se suelte; el clic, que llega justo después, va primero.
  const puntero = { pulsado: false, pendiente: false };
  const aplicar = () => {
    if (puntero.pulsado) {
      puntero.pendiente = true;
      return;
    }
    const criterios = leerCriterios(formulario);
    // Si nada cambió (el `change` del blur tras la espera), no se repinta.
    if (mismosCriterios(criterios, vista.criterios)) {
      return;
    }
    vista.criterios = criterios;
    vista.pagina = 0;
    pintarResumenDeFiltros(formulario, vista.criterios);
    pintarCatalogo(doc);
  };
  const soltar = () => {
    if (!puntero.pulsado) {
      return;
    }
    puntero.pulsado = false;
    // Una vuelta después: el `click` se despacha tras el `pointerup`.
    setTimeout(() => {
      if (puntero.pendiente) {
        puntero.pendiente = false;
        aplicar();
      }
    }, 0);
  };
  doc.addEventListener('pointerdown', () => {
    puntero.pulsado = true;
  });
  doc.addEventListener('pointerup', soltar);
  doc.addEventListener('pointercancel', soltar);
  let espera = null;
  formulario.addEventListener('input', (evento) => {
    if (evento.target?.type === 'search' || evento.target?.type === 'number') {
      clearTimeout(espera);
      espera = setTimeout(aplicar, 250);
    }
  });
  formulario.addEventListener('change', aplicar);
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    clearTimeout(espera);
    aplicar();
  });
  // `reset` limpia los campos DESPUÉS de este evento: se aplica en la vuelta
  // siguiente, con los valores ya vacíos.
  formulario.addEventListener('reset', () => {
    clearTimeout(espera);
    setTimeout(aplicar, 0);
  });
}

/**
 * «Ver producto»: la ficha del catálogo con el bloque de compra y las
 * opiniones de la comunidad.
 *
 * @param {string} productoId
 * @param {Document} doc
 * @param {HTMLElement} [origen] adonde vuelve el foco al cerrar
 */
function abrirDetalle(productoId, doc, origen) {
  const vista = estadoDe(doc);
  const dto = vista.productos.find((producto) => String(producto.id) === productoId);
  if (!dto) {
    return;
  }
  abrirFicha(productoId, {
    origen,
    contexto: 'tienda',
    complementos: [
      () =>
        bloqueDeCompra(dto, {
          modo: MODOS.TIENDA,
          unidadesPropias: vista.propias.get(productoId) ?? 0,
          alAnadir: (id) => agregarAlCarrito(id, doc),
        }),
      complementoDeOpiniones(),
    ],
  });
}

/**
 * Coloca un estado del kit dentro de un contenedor.
 *
 * La rejilla es un `grid`, asi que el estado se saltaria a una celda; por eso
 * ocupa todas las columnas en vez de quedarse en la primera.
 *
 * @param {HTMLElement} contenedor
 * @param {HTMLElement} estado
 */
function pintarEn(contenedor, estado) {
  if (!contenedor) {
    return;
  }
  estado.style.gridColumn = '1 / -1';
  contenedor.replaceChildren(estado);
}

// La tarjeta de producto vive desde UXC-4 en `tienda-producto.js`, con el
// precio, la marca de «propio» y «Ver producto»: la usa tambien la portada.
// Conserva lo que esta funcion habia ganado (FI-R2: `precioFinal` y no un
// `precio` inventado; R16: el UUID en texto hasta el carrito; UX-R2.8: nada
// de `innerHTML` con datos).

// --- CARRITO ---

/**
 * «Añadir» — `POST /api/v1/carrito/items`.
 *
 * Si el servicio lo rechaza, el jugador lo lee en un aviso dentro del
 * carrito, que es donde esperaba ver el producto (MAPEO-ERRORES §5.2: el fallo
 * de una acción es un `Aviso`, la vista sigue en pie). El carrito no se
 * recarga: no cambió.
 *
 * @param {string} productoId UUID del catálogo maestro, tal cual lo dio la vitrina
 * @param {Document} [doc]
 */
export async function agregarAlCarrito(productoId, doc = document) {
  const zona = zonaDeAvisoDelCarrito(doc);
  // Cada intento empieza limpio: el aviso del anterior no se queda en
  // pantalla ni se apila con el nuevo (MAPEO-ERRORES §9).
  limpiarAviso(zona);

  let respuesta;
  try {
    respuesta = await fetchWithHttpErrorInterceptor(rutaDeApi('/carrito/items'), {
      method: 'POST',
      headers: cabeceras(),
      body: JSON.stringify({ productoId, cantidad: 1 }),
    });
  } catch (error) {
    // Ni siquiera hubo respuesta: la red o el borde no contestaron.
    const mensaje = avisarFalloAlAnadir(zona, { estado: 0, problema: null, productoId, doc });
    desplegarCarrito(doc);
    console.error('Error al agregar item:', error);
    return { ok: false, ...mensaje };
  }

  if (!respuesta.ok) {
    const problema = await leerProblema(respuesta);
    const mensaje = avisarFalloAlAnadir(zona, {
      estado: respuesta.status,
      problema,
      productoId,
      doc,
    });
    // UXC-4 — con el carrito minimizado el aviso quedaría escondido: se
    // despliega para que se lea donde se esperaba ver el producto.
    desplegarCarrito(doc);
    console.error('El carrito rechazó el producto:', respuesta.status, problema?.type);
    return { ok: false, ...mensaje };
  }

  await cargarCarrito(doc);
  anunciarEnLaInsignia(doc, 'Añadido al carrito.');
  return { ok: true };
}

/**
 * Quitar una línea del carrito — `DELETE /api/v1/carrito/items/{itemId}`.
 *
 * El servicio devuelve el carrito ya sin la línea, y eso es lo que se pinta:
 * el total lo recalcula él.
 *
 * @param {string|number} itemId el `id` de la línea
 * @param {Document} [doc]
 * @returns {Promise<boolean>} si se quitó
 */
export async function quitarDelCarrito(itemId, doc = document) {
  const zona = zonaDeAvisoDelCarrito(doc);
  limpiarAviso(zona);
  const reintentar = {
    texto: 'Reintentar',
    nombre: 'reintentar-quitar',
    alPulsar: () => quitarDelCarrito(itemId, doc),
  };
  let respuesta;
  try {
    respuesta = await fetchWithHttpErrorInterceptor(
      rutaDeApi(`/carrito/items/${encodeURIComponent(String(itemId))}`),
      { method: 'DELETE', headers: cabeceras() },
    );
  } catch (error) {
    pintarAviso(zona, {
      tono: 'error',
      titulo: 'No se pudo quitar el producto',
      detalle: 'Sigue en tu carrito. Inténtalo de nuevo en unos segundos.',
      accion: reintentar,
    });
    console.error('Error al quitar item:', error);
    return false;
  }
  if (!respuesta.ok) {
    if (respuesta.status === 401) {
      pintarAviso(zona, {
        tono: 'advertencia',
        titulo: 'Tu sesión ya no es válida',
        detalle: 'Vuelve a iniciar sesión para usar el carrito.',
        accion: {
          texto: 'Iniciar sesión',
          nombre: 'iniciar-sesion',
          alPulsar: volverAIniciarSesion,
        },
      });
    } else if (respuesta.status !== 403) {
      pintarAviso(zona, {
        tono: tonoPorEstado(respuesta.status),
        titulo: 'No se pudo quitar el producto',
        detalle: 'Sigue en tu carrito. Inténtalo de nuevo en unos segundos.',
        accion: reintentar,
      });
    }
    return false;
  }
  actualizarUI(await respuesta.json(), doc);
  anunciarEnLaInsignia(doc, 'Quitado del carrito.');
  return true;
}

/** Despliega el panel del carrito si la vista lo tiene minimizado. */
function desplegarCarrito(doc) {
  const { cajon } = estadoDe(doc);
  if (cajon && !cajon.desplegado()) {
    cajon.desplegar();
  }
}

/**
 * Lo que acaba de pasar con el carrito, junto a la insignia: se lee aunque el
 * panel esté minimizado, y se anuncia (`role="status"`).
 *
 * @param {Document} doc
 * @param {string} texto
 */
function anunciarEnLaInsignia(doc, texto) {
  const zona = doc.getElementById('aviso-insignia');
  if (!zona) {
    return;
  }
  zona.textContent = texto;
  clearTimeout(zona._temporizador);
  zona._temporizador = setTimeout(() => {
    zona.textContent = '';
  }, 4000);
}

/**
 * El hueco del aviso de «Añadir», encima de las líneas del carrito.
 *
 * `tienda.html` lo declara; si una vista lo perdiera, se crea aquí antes que
 * volver a fallar en silencio.
 *
 * @param {Document} doc
 * @returns {HTMLElement}
 */
function zonaDeAvisoDelCarrito(doc) {
  const existente = doc.getElementById('aviso-carrito');
  if (existente) {
    return existente;
  }
  const zona = doc.createElement('div');
  zona.id = 'aviso-carrito';
  zona.dataset.zona = 'aviso';
  zona.hidden = true;
  const lineas = doc.getElementById('cart-items');
  if (lineas?.parentNode) {
    lineas.parentNode.insertBefore(zona, lineas);
  } else {
    doc.body.append(zona);
  }
  return zona;
}

/**
 * Pinta el aviso de un «Añadir» que no entró.
 *
 * - El `type` decide el mensaje (`MOTIVOS_DEL_CARRITO`); el `status`, el tono
 *   (MAPEO-ERRORES §4: 4xx advertencia, 5xx error).
 * - Siempre hay una salida (§9): «Reintentar» cuando el fallo es del sistema
 *   y puede pasar solo; «Actualizar la tienda» cuando el producto cambió
 *   —agotado, suspendido, retirado— y lo que hay que ver es la vitrina nueva.
 * - El texto va por `textContent` (`aviso()` construye nodos): ni el
 *   `detail` del servidor ni nada de lo que traiga puede meter marcado.
 *
 * @param {HTMLElement} zona
 * @param {{estado: number, problema: object|null, productoId: string, doc: Document}} fallo
 */
function avisarFalloAlAnadir(zona, { estado, problema, productoId, doc }) {
  if (estado === 403) {
    // El interceptor compartido ya anuncia el rechazo de permiso con su aviso
    // flotante (HU-RBAC-004). Un segundo aviso por el mismo fallo seria
    // apilarlos. Al detalle del producto se le devuelve la frase igual.
    return {
      titulo: 'No se pudo añadir el producto',
      detalle: 'Tu cuenta no tiene permiso para comprar.',
    };
  }

  const delSistema = estado === 0 || estado >= 500;
  const reintentar = {
    texto: 'Reintentar',
    nombre: 'reintentar',
    alPulsar: () => agregarAlCarrito(productoId, doc),
  };
  const actualizar = {
    texto: 'Actualizar la tienda',
    nombre: 'actualizar-tienda',
    alPulsar: () => {
      limpiarAviso(zona);
      cargarVitrina(doc);
    },
  };

  const conocido = MOTIVOS_DEL_CARRITO[problema?.type] ?? null;
  let mensaje;
  if (conocido) {
    mensaje = { ...conocido, accion: delSistema ? reintentar : actualizar };
  } else if (estado === 401) {
    // Un token que el servicio ya no acepta: se vuelve a iniciar sesion y el
    // login devuelve a la tienda (MAPEO-ERRORES §8).
    mensaje = {
      titulo: 'Tu sesión ya no es válida',
      detalle: 'Vuelve a iniciar sesión para usar el carrito.',
      accion: { texto: 'Iniciar sesión', nombre: 'iniciar-sesion', alPulsar: volverAIniciarSesion },
    };
  } else {
    // Un rechazo que el contrato no declara. El `detail`, si llega, esta
    // escrito para el jugador (MAPEO-ERRORES §3); si no, una pauta propia.
    const delServidor = textoDelServidor(problema, estado, '') || null;
    mensaje = {
      titulo: 'No se pudo añadir el producto al carrito',
      detalle:
        delServidor ??
        (delSistema
          ? 'Inténtalo de nuevo en unos segundos.'
          : 'Actualiza la tienda e inténtalo otra vez.'),
      accion: delSistema ? reintentar : actualizar,
    };
  }

  const caja = pintarAviso(zona, { tono: tonoPorEstado(estado), ...mensaje });
  // En pantalla estrecha el carrito queda debajo de la vitrina: sin esto, el
  // aviso existiria pero fuera de la vista de quien acaba de pulsar «Añadir».
  caja.scrollIntoView?.({ block: 'nearest' });
  return { titulo: mensaje.titulo, detalle: mensaje.detalle };
}

/** Borra la sesión que el servicio rechazó y lleva al login con vuelta a la tienda. */
function volverAIniciarSesion() {
  olvidarSesion();
  exigirSesion();
}

export async function cargarCarrito(doc = document) {
  try {
    const respuesta = await fetchWithHttpErrorInterceptor(rutaDeApi('/carrito'), {
      method: 'GET',
      headers: cabeceras(),
    });
    // R16 — el interceptor no lanza ante un 4xx o un 5xx: sin esto, el
    // problem detail de un 500 se pintaba como un carrito sin `items`, o sea
    // «vacío», que es justo lo que el `catch` de abajo dice evitar.
    if (!respuesta.ok) {
      throw await errorDeRespuesta(respuesta);
    }

    actualizarUI(await respuesta.json(), doc);
  } catch (error) {
    // Un 404 es un carrito que todavía no existe, y eso SÍ es un carrito
    // vacío. Cualquier otro fallo no lo es: pintar «vacío» ante un 500 le
    // esconde al jugador que sus productos siguen ahí.
    if (error?.estado === 404 || error?.status === 404) {
      actualizarUI(null, doc);
      return;
    }
    mostrarFalloDelCarrito(doc);
    console.error('Error al cargar el carrito:', error);
  }
}

function mostrarFalloDelCarrito(doc) {
  const contenedor = doc.getElementById('cart-items');
  const botonPagar = doc.getElementById('btn-pagar');
  if (contenedor) {
    // UX-R2.8d — era un parrafo sin salida. Ahora ofrece reintentar, que es
    // lo que alguien quiere hacer cuando su carrito no carga.
    contenedor.replaceChildren(
      estadoDeError({
        titulo: 'No se pudo cargar tu carrito',
        detalle: 'Tus productos siguen ahí. Vuelve a intentarlo.',
        alReintentar: () => cargarCarrito(doc),
      }),
    );
  }
  if (botonPagar) {
    // No se paga lo que no se ha podido leer.
    botonPagar.disabled = true;
  }
}

export function actualizarUI(carrito, doc = document) {
  const contenedor = doc.getElementById('cart-items');
  const subtotal = doc.getElementById('cart-subtotal');
  const total = doc.getElementById('cart-total');
  const botonPagar = doc.getElementById('btn-pagar');
  const unidades = doc.getElementById('cart-unidades');

  // UXC-4 — la insignia de la cabecera dice cuántos productos hay, también
  // con el panel minimizado.
  const cuantas = unidadesDelCarrito(carrito);
  pintarInsignia(doc.getElementById('insignia-carrito'), cuantas);
  if (unidades) {
    unidades.textContent = textoDeUnidades(cuantas);
  }

  if (!carrito || !carrito.items || carrito.items.length === 0) {
    // UXC-9 — un vacío dice qué hacer: la vitrina está al lado.
    contenedor.replaceChildren(
      h('p', { clase: 't-meta', texto: 'Tu carrito está vacío' }),
      h('p', {
        clase: 't-meta carrito-vacio__siguiente',
        texto: 'Pulsa «Añadir» en cualquier producto de la vitrina para traerlo aquí.',
      }),
    );
    // Un carrito vacio suma cero de verdad, pero la moneda no se sabe: la trae
    // cada producto, y aqui no hay ninguno. Se ensena la cifra sola.
    subtotal.textContent = '0';
    total.textContent = '0';
    botonPagar.disabled = true;
    return;
  }

  // La moneda del carrito es la del primer producto que la declare: el DTO la
  // trae por producto, no por carrito, y suponer COP seria decirle al jugador
  // en que paga sin saberlo.
  const moneda =
    carrito.items.map((i) => i.producto?.moneda).find((m) => typeof m === 'string' && m.trim()) ||
    null;

  contenedor.replaceChildren(...carrito.items.map((item) => lineaDelCarrito(item, moneda)));

  const totalTexto = textoDePrecio(aImporte(carrito.total), moneda) ?? 'Sin total';
  subtotal.textContent = totalTexto;
  total.textContent = totalTexto;
  prepararBotonDePago(botonPagar);
}

/**
 * Una línea del carrito: nombre, cantidad, precio por unidad, subtotal y sus
 * dos acciones — sumar una unidad (`POST /carrito/items`, que suma a la línea
 * del mismo producto) y quitarla (`DELETE /carrito/items/{id}`).
 *
 * No hay «restar una»: el contrato no tiene cómo, y quitar la línea y volver
 * a añadir N−1 serían dos operaciones que pueden quedarse a medias.
 *
 * @param {object} item `LineaDeCarrito` del contrato
 * @param {string|null} moneda
 * @returns {HTMLElement}
 */
function lineaDelCarrito(item, moneda) {
  const fila = aFilaDeCarrito(item, moneda);
  const productoId = item?.producto?.id ?? null;
  const lineaId = item?.id ?? null;
  const acciones = [];
  if (productoId !== null) {
    acciones.push(
      h('button', {
        clase: 'boton boton--secundario boton--pequeno item-accion',
        texto: '+1',
        datos: { sumarItem: String(productoId) },
        atributos: { type: 'button', 'aria-label': `+1: una unidad más de ${fila.nombre}` },
      }),
    );
  }
  if (lineaId !== null) {
    acciones.push(
      h('button', {
        clase: 'boton boton--secundario boton--pequeno item-accion',
        texto: 'Quitar',
        datos: { quitarItem: String(lineaId) },
        atributos: { type: 'button', 'aria-label': `Quitar ${fila.nombre} del carrito` },
      }),
    );
  }
  return h('div', {
    clase: 'cart-item',
    datos: lineaId !== null ? { itemId: String(lineaId) } : {},
    hijos: [
      h('div', {
        clase: 'item-info',
        hijos: [
          h('h5', { texto: fila.nombre }),
          h('span', { texto: `x${fila.cantidad}` }),
          fila.unitarioTexto && fila.cantidad > 1
            ? h('span', { clase: 'item-unitario', texto: ` · ${fila.unitarioTexto} c/u` })
            : null,
        ],
      }),
      h('div', {
        clase: 'item-derecha',
        hijos: [
          // FI-R2 — antes salia «undefined COP» cuando el item no traia subtotal.
          h('div', { clase: 'item-price', texto: fila.subtotalTexto ?? 'Sin precio' }),
          acciones.length > 0 ? h('div', { clase: 'item-acciones', hijos: acciones }) : null,
        ],
      }),
    ],
  });
}

/**
 * El boton «Pagar» — FI-R2 / FI-R14.
 *
 * RF-CAR-010 («Resumen de compra y formulario de pago») y RF-PAG-001
 * («Integracion con pasarela de pagos simulada») existen, son de prioridad
 * Alta y estan confirmados. Lo que **no** existe es su implementacion:
 * `CarritoController` expone `GET /carrito`, `POST /carrito/items` y
 * `DELETE /carrito/items/{itemId}`, y nada mas; `ecommerce-carrito.yaml`
 * declara esas mismas tres rutas y ninguna de pago.
 *
 * Los dos requisitos son de **Grupo de Santiago** (ver
 * `docs/gobierno/MAPA-RESPONSABILIDAD-RF.md`), asi que construir aqui la
 * pasarela seria adelantarles el Sprint, no completarlo.
 *
 * Mientras tanto el boton no puede quedarse encendido: estaba habilitado en
 * cuanto el carrito tenia algo y **no tenia ningun manejador**. Pulsarlo no
 * hacia nada, ni siquiera avisar. Un boton que se enciende es una promesa.
 *
 * @param {HTMLButtonElement|null} boton
 */
function prepararBotonDePago(boton) {
  if (!boton) {
    return;
  }
  boton.disabled = true;
  // El identificador del requisito vive en el comentario de arriba, no en la
  // pantalla: a quien compra no le dice nada «RF-PAG-001».
  boton.title = 'El pago todavía no está disponible.';
  boton.setAttribute('aria-describedby', 'aviso-pago-pendiente');
}

/**
 * Engancha la vista.
 *
 * La escucha va **delegada en la rejilla**, no en cada botón: las tarjetas se
 * crean después, y así no hay que volver a enganchar nada al repintar.
 *
 * @param {ParentNode} [doc]
 * @returns {Promise<void>} resuelve cuando la primera carga terminó de pintar
 */
export function montarTienda(doc = document) {
  const rejilla = doc.getElementById('productos-grid');
  const vista = estadoDe(doc);

  rejilla?.addEventListener('click', (evento) => {
    const anadir = evento.target.closest('[data-producto]');
    if (anadir) {
      agregarAlCarrito(anadir.dataset.producto, doc);
      return;
    }
    // UXC-4 — «Ver producto»: detalle con compra y opiniones.
    const ver = evento.target.closest('[data-ver-producto]');
    if (ver) {
      abrirDetalle(ver.dataset.verProducto, doc, ver);
    }
  });

  // Las acciones de cada línea del carrito, también delegadas: las líneas se
  // repintan enteras con cada respuesta del servicio.
  doc.getElementById('cart-items')?.addEventListener('click', (evento) => {
    const sumar = evento.target.closest('[data-sumar-item]');
    if (sumar) {
      agregarAlCarrito(sumar.dataset.sumarItem, doc);
      return;
    }
    const quitar = evento.target.closest('[data-quitar-item]');
    if (quitar) {
      quitarDelCarrito(quitar.dataset.quitarItem, doc);
    }
  });

  vista.cajon = montarCajonDelCarrito(doc);
  montarFiltros(doc);

  // Lo que el jugador ya tiene sale de su inventario y llega cuando llega: la
  // vitrina no lo espera, y las tarjetas se marcan al contestar.
  const propias = haySesion()
    ? propiedadesDelJugador(usuarioIdDeSesion()).then((mapa) => {
        vista.propias = mapa;
        marcarPropias(doc);
      })
    : Promise.resolve();

  // Se devuelve la promesa para que quien monte la vista pueda esperar a que
  // esté pintada. En el navegador nadie la espera; en las pruebas, sí.
  return Promise.all([cargarVitrina(doc), cargarCarrito(doc), propias]).then(() => undefined);
}

// Arranque automático solo en el navegador; en las pruebas se monta a mano.
if (globalThis.document?.addEventListener) {
  globalThis.document.addEventListener('DOMContentLoaded', () => montarTienda());
}
