/**
 * Vitrina, carrito y pago — HU-CAR-001 y HU-CAR-010.
 *
 * Habla con `ms-ecommerce`: `GET /api/v1/vitrina`, `GET /api/v1/carrito`,
 * `POST /api/v1/carrito/items` y `POST /api/v1/checkout`
 * (`contracts/openapi/ecommerce-carrito.yaml`).
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
 * 2. **`API_BASE_URL` no estaba declarada.** Creaba un global implícito. Ahora
 *    usa `baseDeApi()`, el mismo mecanismo que el resto de clientes.
 * 3. **El interceptor era una copia local** que perdía el formato de error de
 *    la plataforma. Ahora usa el compartido, que además adjunta el Bearer.
 * 4. **Los botones iban por `onclick` en el HTML generado.** Se sustituyen por
 *    delegación de eventos.
 *
 * ## HU-CAR-010 — resumen y pago
 *
 * - Todo el DOM se construye con `h()`/`nodo()` del kit (guardián
 *   `sin-innerhtml`). En esta vista importa el doble: comparte página con el
 *   formulario de la tarjeta.
 * - El diálogo de pago es el `abrirDialogo()` del kit y los campos son
 *   `campo()`: foco atrapado, Escape, errores con `aria-invalid`. Al vivir en
 *   JS, el HTML ya no lleva clases de modal propias (guardián `clases-del-kit`).
 * - El resumen se genera desde los datos del carrito, no copiando su marcado.
 * - Los datos de la tarjeta se validan en formato antes de enviarse, nunca se
 *   escriben en consola, el número y el CVV se borran tras cada intento, y al
 *   cerrar el diálogo sus nodos se destruyen.
 * - El `carritoId` ya no va fijo en 1: sale del carrito cargado.
 * - Como el interceptor no lanza ante un 4xx o un 5xx, el pago también
 *   comprueba `ok`: un 503 de ms-finanzas se leía como un cuerpo sin
 *   `aprobado: false`, y la tienda decía «pago aprobado» sobre un pago que
 *   no ocurrió.
 * - El resultado sigue el contrato de ms-finanzas (HU-PAG-001): APROBADO,
 *   RECHAZADO o INDETERMINADO. Un pago indeterminado no se da por bueno ni
 *   por malo: el jugador lo ve «en revisión» y la tienda no le ofrece pagar
 *   otra vez, para no cobrarle dos veces.
 * - La tarjeta de producto de R16 se construye con `h()`/`nodo()`: `tienda.js`
 *   salió de los pendientes de `marcado-sin-plantillas`.
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
import { h, nodo, clases } from '../comun/ui/dom.js';
import { abrirDialogo } from '../comun/ui/dialogo.js';
import { campo } from '../comun/ui/campo.js';
import { aFilaDeCarrito, aImporte, aProductoDeVitrina, textoDePrecio } from './tienda-adaptador.js';

/** Último carrito pintado: de aquí salen el resumen y el `carritoId` del pago. */
let ultimoCarrito = null;

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
 * La identidad viaja en el token Bearer manejado por el interceptor (ADR-002).
 */
function cabeceras() {
  return { 'Content-Type': 'application/json' };
}

/** @returns {boolean} true si hay una sesión utilizable */
export function haySesion() {
  return usuarioIdDeSesion() !== null;
}

/**
 * Moneda del carrito cuando el servicio no la dice. El contrato 1.2.0 la
 * declara «hoy siempre COP» y solo `null` con el carrito vacío; se usa para no
 * dejar una cifra sin moneda en el total, nunca para inventar una distinta.
 */
const MONEDA_DEL_CARRITO = 'COP';

/** @param {object|null} carrito */
function monedaDe(carrito) {
  const moneda = carrito?.moneda;
  return typeof moneda === 'string' && moneda.trim() ? moneda.trim() : MONEDA_DEL_CARRITO;
}

/**
 * Importe con su moneda; «—» si no llegó. Cero es un precio y no se inventa.
 *
 * @param {unknown} valor
 * @param {string} moneda
 */
function dinero(valor, moneda) {
  return textoDePrecio(aImporte(valor), moneda) ?? '—';
}

// --- RENDERIZADO DE PRODUCTOS (HU-CAR-001) ---

// --- RENDERIZADO DE PRODUCTOS (HU-CAR-001) ---

export async function cargarVitrina(doc = document) {
  const rejilla = doc.getElementById('productos-grid');

  // UX-R2.8d — estado de carga en lugar de una rejilla en blanco (RNF-USA-003).
  pintarEn(rejilla, estadoDeCarga({ filas: 4, etiqueta: 'Cargando la tienda…' }));

  try {
    // R16 — `/vitrina` y no `/productos`: ese prefijo es del catálogo maestro.
    const respuesta = await fetchWithHttpErrorInterceptor(rutaDeApi('/vitrina'), {
      method: 'GET',
      headers: cabeceras(),
    });
    if (!respuesta.ok) {
      throw await errorDeRespuesta(respuesta);
    }

    const datos = await respuesta.json();
    const productos = datos.content ?? [];

    if (productos.length === 0) {
      // Un catálogo vacío es un estado legítimo, y distinto de un fallo.
      pintarEn(
        rejilla,
        estadoVacio({
          titulo: 'La tienda no tiene productos ahora mismo',
          detalle: 'Vuelve más tarde: el catálogo lo publica la administración.',
          icono: '◇',
        }),
      );
      return;
    }

    rejilla.replaceChildren(...productos.map((dto) => tarjetaDeProducto(dto)));
  } catch (error) {
    // UX-R2.8d — un fallo ofrece reintentar en vez de dejar un callejón sin salida.
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
    console.error('Error al cargar la vitrina:', error);
  }
}

/**
 * Coloca un estado del kit dentro de un contenedor.
 *
 * La rejilla es un `grid`, así que el estado ocupa todas las columnas en vez
 * de quedarse en la primera.
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

/**
 * Tarjeta de un producto.
 *
 * FI-R2 — la traducción del DTO es explícita y está probada aparte
 * (`tienda-adaptador.js`). Antes esta función leía `dto.precio`, un campo que
 * el servicio no devuelve.
 *
 * Todo se construye con nodos y `textContent`: el nombre, la descripción y
 * las habilidades vienen del catálogo, y un producto con `<script>` en el
 * nombre no debe ejecutarse. El color sale del tipo por `data-tipo`, y el
 * botón NO lleva `onclick`: la escucha está delegada en la rejilla.
 */
function tarjetaDeProducto(dto) {
  const producto = aProductoDeVitrina(dto);

  // RF-CAR-001 pide la imagen del producto; `imagenUrl` viene en el DTO.
  const imagen = producto.imagenUrl
    ? h('img', { atributos: { src: producto.imagenUrl, alt: '', loading: 'lazy' } })
    : null;

  // FI-R2 — sin precio no se escribe «0 COP»: cero es un precio, y decir que
  // algo es gratis cuando lo que pasa es que no llegó el dato es mentir.
  const precio = h('span', {
    clase: clases('price', producto.precioTexto === null && 'precio-ausente'),
    texto: producto.precioTexto ?? 'Precio no disponible',
  });

  // Sin id no hay nada que añadir: el botón se deshabilita en vez de mandar
  // `undefined`. R16 — el id es el UUID del catálogo, en texto, y viaja tal
  // cual: ni `Number()` ni `parseInt`, que lo convertirían en `NaN`.
  const sinId = producto.id === null;
  const boton = h('button', {
    clase: 'btn-add',
    texto: 'Añadir',
    atributos: {
      type: 'button',
      disabled: sinId,
      title: sinId ? 'Este producto llegó incompleto y no se puede añadir al carrito.' : null,
    },
    datos: sinId ? {} : { producto: String(producto.id) },
  });

  const datos = { tipo: producto.tipo };
  if (producto.esPropio) {
    datos.propio = 'si';
  }
  if (producto.enListaDeseos) {
    datos.deseado = 'si';
  }

  return h('div', {
    clase: 'product-card',
    datos,
    hijos: [
      h('div', { clase: clases('product-image', imagen && 'con-imagen'), hijos: [imagen] }),
      nodo('h4', undefined, producto.nombre),
      nodo('p', undefined, producto.descripcion),
      // RF-CAR-001 pide también las habilidades.
      producto.habilidades ? nodo('p', 'habilidades', producto.habilidades) : null,
      h('div', {
        clase: 'product-footer',
        hijos: [
          h('span', {
            clase: 'precio-bloque',
            hijos: [
              precio,
              producto.precioAnteriorTexto
                ? nodo('s', 'price-antes', producto.precioAnteriorTexto)
                : null,
              producto.descuento === null
                ? null
                : nodo('span', 'badge-descuento', `-${producto.descuento}%`),
            ],
          }),
          boton,
        ],
      }),
    ],
  });
}

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
    avisarFalloAlAnadir(zona, { estado: 0, problema: null, productoId, doc });
    console.error('Error al agregar item:', error);
    return;
  }

  if (!respuesta.ok) {
    const problema = await leerProblema(respuesta);
    avisarFalloAlAnadir(zona, { estado: respuesta.status, problema, productoId, doc });
    console.error('El carrito rechazó el producto:', respuesta.status, problema?.type);
    return;
  }

  await cargarCarrito(doc);
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
    // apilarlos.
    return;
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
    const delServidor =
      typeof problema?.detail === 'string' && problema.detail.trim() ? problema.detail : null;
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
    // Un 404 es un carrito que todavía no existe: eso SÍ es un carrito vacío.
    // Cualquier otro fallo no lo es: pintar «vacío» ante un 500 le esconde al
    // jugador que sus productos siguen ahí.
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
  ultimoCarrito = null;
  if (contenedor) {
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

/**
 * Fila de un ítem del carrito; la reutiliza el resumen del pago.
 *
 * @param {object} item
 * @param {string} moneda
 */
function filaDeCarrito(item, moneda) {
  const fila = aFilaDeCarrito(item, moneda);
  return h('div', {
    clase: 'cart-item',
    hijos: [
      h('div', {
        clase: 'item-info',
        hijos: [nodo('h5', undefined, fila.nombre), nodo('span', undefined, `x${fila.cantidad}`)],
      }),
      nodo('div', 'item-price', fila.subtotalTexto ?? '—'),
    ],
  });
}

function carritoTieneItems(carrito) {
  return Boolean(carrito?.items?.length);
}

export function actualizarUI(carrito, doc = document) {
  const contenedor = doc.getElementById('cart-items');
  const subtotal = doc.getElementById('cart-subtotal');
  const total = doc.getElementById('cart-total');
  const botonPagar = doc.getElementById('btn-pagar');

  if (!carritoTieneItems(carrito)) {
    ultimoCarrito = null;
    contenedor.replaceChildren(nodo('p', 't-meta', 'Tu carrito está vacío'));
    subtotal.textContent = dinero(0, MONEDA_DEL_CARRITO);
    total.textContent = dinero(0, MONEDA_DEL_CARRITO);
    botonPagar.disabled = true;
    return;
  }

  const moneda = monedaDe(carrito);
  ultimoCarrito = carrito;
  contenedor.replaceChildren(...carrito.items.map((item) => filaDeCarrito(item, moneda)));
  subtotal.textContent = dinero(carrito.total, moneda);
  total.textContent = dinero(carrito.total, moneda);
  botonPagar.disabled = false;
}

// --- RESUMEN Y PAGO (HU-CAR-010) ---

/**
 * Resumen de la compra, construido desde los datos del carrito.
 *
 * @param {{items: Array, total: number|string, moneda?: string|null}} carrito
 * @returns {HTMLElement}
 */
export function resumenDeCompra(carrito) {
  const moneda = monedaDe(carrito);
  return h('section', {
    clase: 'pila',
    atributos: { 'aria-label': 'Resumen de la compra' },
    hijos: [
      ...carrito.items.map((item) => filaDeCarrito(item, moneda)),
      h('p', {
        hijos: [nodo('strong', undefined, `Total a pagar: ${dinero(carrito.total, moneda)}`)],
      }),
    ],
  });
}

/** Algoritmo de Luhn: descarta números mal tecleados antes de ir a la pasarela. */
function pasaLuhn(digitos) {
  let suma = 0;
  let doblar = false;
  for (let i = digitos.length - 1; i >= 0; i -= 1) {
    let d = Number(digitos[i]);
    if (doblar) {
      d *= 2;
      if (d > 9) {
        d -= 9;
      }
    }
    suma += d;
    doblar = !doblar;
  }
  return suma % 10 === 0;
}

/**
 * Acepta `MM/AA`, `MM/AAAA` o el `AAAA-MM` de un `<input type="month">`.
 *
 * @returns {{mes: number, anio: number} | null}
 */
function leerVencimiento(texto) {
  const valor = (texto ?? '').trim();
  let mes;
  let anio;
  let m = valor.match(/^(\d{2})\s*\/\s*(\d{2}|\d{4})$/);
  if (m) {
    mes = Number(m[1]);
    anio = m[2].length === 2 ? 2000 + Number(m[2]) : Number(m[2]);
  } else {
    m = valor.match(/^(\d{4})-(\d{2})$/);
    if (!m) {
      return null;
    }
    anio = Number(m[1]);
    mes = Number(m[2]);
  }
  if (mes < 1 || mes > 12) {
    return null;
  }
  return { mes, anio };
}

/**
 * Valida el FORMATO de los datos de pago. No guarda nada.
 *
 * @param {{titular: string, numero: string, fechaExpiracion: string, cvv: string}} datos
 * @param {Date} [hoy] inyectable para las pruebas
 * @returns {Record<string, string>} campo → mensaje; vacío si todo es válido
 */
export function validarTarjeta({ titular, numero, fechaExpiracion, cvv }, hoy = new Date()) {
  const errores = {};

  const nombre = (titular ?? '').trim();
  if (nombre.length < 3 || !/^[\p{L}][\p{L} .'-]*$/u.test(nombre)) {
    errores.titular = 'Escribe el nombre como aparece en la tarjeta.';
  }

  const digitos = (numero ?? '').replace(/[\s-]/g, '');
  if (!/^\d{13,19}$/.test(digitos) || !pasaLuhn(digitos)) {
    errores.numero = 'El número de tarjeta no es válido.';
  }

  const vencimiento = leerVencimiento(fechaExpiracion);
  if (!vencimiento) {
    errores.fechaExpiracion = 'Usa el formato MM/AA.';
  } else {
    // Una tarjeta vale hasta el último día de su mes de vencimiento.
    const primerDiaDelMesSiguiente = new Date(vencimiento.anio, vencimiento.mes, 1);
    if (hoy >= primerDiaDelMesSiguiente) {
      errores.fechaExpiracion = 'La tarjeta está vencida.';
    }
  }

  if (!/^\d{3,4}$/.test((cvv ?? '').trim())) {
    errores.cvv = 'El código de seguridad tiene 3 o 4 dígitos.';
  }

  return errores;
}

/**
 * Campos del formulario de pago, por el nombre que llevan en el payload.
 *
 * @returns {Map<string, {elemento: HTMLElement, control: HTMLInputElement, marcarError: Function}>}
 */
function camposDePago() {
  return new Map([
    [
      'titular',
      campo({
        nombre: 'titular',
        etiqueta: 'Nombre del titular',
        requerido: true,
        autocompletar: 'cc-name',
      }),
    ],
    [
      'numero',
      campo({
        nombre: 'numero',
        etiqueta: 'Número de tarjeta',
        requerido: true,
        autocompletar: 'cc-number',
        atributos: { inputmode: 'numeric', maxlength: 23 },
      }),
    ],
    [
      'fechaExpiracion',
      campo({
        nombre: 'vencimiento',
        etiqueta: 'Vencimiento',
        pista: 'MM/AA',
        requerido: true,
        autocompletar: 'cc-exp',
        atributos: { inputmode: 'numeric', maxlength: 7, placeholder: 'MM/AA' },
      }),
    ],
    [
      'cvv',
      campo({
        nombre: 'cvv',
        etiqueta: 'Código de seguridad',
        tipo: 'password',
        requerido: true,
        autocompletar: 'cc-csc',
        atributos: { inputmode: 'numeric', maxlength: 4 },
      }),
    ],
  ]);
}

/** El número y el CVV no se quedan en el formulario después de un intento. */
function borrarDatosSensibles(campos) {
  campos.get('numero').control.value = '';
  campos.get('cvv').control.value = '';
}

/**
 * @param {HTMLElement} mensaje
 * @param {'aprobado'|'rechazado'|'revision'} resultado
 * @param {string} texto
 */
function mostrarResultado(mensaje, resultado, texto) {
  mensaje.textContent = texto;
  mensaje.dataset.resultado = resultado;
  mensaje.hidden = false;
}

const ESTADOS_DE_PAGO = new Set(['APROBADO', 'RECHAZADO', 'INDETERMINADO']);

/**
 * Estado del pago según el contrato de ms-finanzas (HU-PAG-001).
 *
 * Lo que no se entiende se trata como INDETERMINADO: ante la duda no se dice
 * «aprobado» y no se invita a pagar otra vez.
 *
 * @param {object} resultado cuerpo del 200 de `POST /checkout`
 * @returns {'APROBADO'|'RECHAZADO'|'INDETERMINADO'}
 */
function estadoDelPago(resultado) {
  const estado = typeof resultado?.estado === 'string' ? resultado.estado.trim().toUpperCase() : '';
  if (ESTADOS_DE_PAGO.has(estado)) {
    return estado;
  }
  if (resultado?.aprobado === true) {
    return 'APROBADO';
  }
  return 'INDETERMINADO';
}

/** El texto de un cuerpo de error, si trae uno escrito para el jugador. */
function textoDelServidor(problema) {
  for (const campoDeTexto of [problema?.mensaje, problema?.detail]) {
    if (typeof campoDeTexto === 'string' && campoDeTexto.trim()) {
      return campoDeTexto;
    }
  }
  return null;
}

/**
 * Lo que se le dice al jugador cuando `POST /checkout` no respondió 2xx.
 *
 * @param {number} estado status HTTP (0 = no hubo respuesta)
 * @param {object|null} problema cuerpo de la respuesta, si se pudo leer
 */
function mensajeDeFallo(estado, problema) {
  if (estado === 0) {
    return 'Error de conexión con el servidor. Revisa tu conexión y vuelve a intentarlo.';
  }
  if (estado === 401) {
    return 'Tu sesión expiró durante el pago. Inicia sesión de nuevo: el pago no se procesó.';
  }
  if (estado === 403) {
    return 'No tienes permiso para completar esta compra.';
  }
  if (estado === 400 || estado === 422) {
    return 'Revisa los datos de la tarjeta e inténtalo otra vez.';
  }
  if (estado === 409) {
    return (
      textoDelServidor(problema) ?? 'Tu carrito cambió. Actualiza la tienda e inténtalo otra vez.'
    );
  }
  return 'No pudimos procesar el pago en este momento. Vuelve a intentarlo en unos minutos.';
}

/** Tras un pago indeterminado no se ofrece pagar otra vez desde esta página. */
function bloquearNuevoPago(doc) {
  const botonPagar = doc.getElementById('btn-pagar');
  if (botonPagar) {
    botonPagar.disabled = true;
    botonPagar.title = 'Tu último pago está en revisión.';
  }
}

/**
 * Valida, envía y pinta el resultado.
 *
 * @returns {Promise<'aprobado'|'revision'|'reintentar'>} cómo sigue el diálogo
 */
async function pagar(doc, { campos, mensaje, confirmar, carrito }) {
  const datos = {};
  for (const [nombre, uno] of campos) {
    datos[nombre] = uno.control.value;
  }

  const errores = validarTarjeta(datos);
  for (const [nombre, uno] of campos) {
    uno.marcarError(errores[nombre] ?? null);
  }
  const primerError = Object.keys(errores)[0];
  if (primerError) {
    mostrarResultado(mensaje, 'rechazado', 'Revisa los datos marcados antes de pagar.');
    campos.get(primerError).control.focus();
    return 'reintentar';
  }

  confirmar.disabled = true;
  confirmar.textContent = 'Procesando…';

  try {
    let respuesta;
    try {
      respuesta = await fetchWithHttpErrorInterceptor(rutaDeApi('/checkout'), {
        method: 'POST',
        headers: cabeceras(),
        body: JSON.stringify({
          carritoId: carrito?.id ?? null,
          tarjeta: { ...datos, numero: datos.numero.replace(/[\s-]/g, '') },
        }),
      });
    } catch (error) {
      mostrarResultado(mensaje, 'rechazado', mensajeDeFallo(0, null));
      // Solo el error: los datos de la tarjeta no se escriben nunca en consola.
      console.error('Error de red al procesar el pago:', error);
      return 'reintentar';
    }

    // El interceptor devuelve los 4xx y 5xx tal cual: sin esto, el cuerpo de
    // un 503 se tomaba por un pago aprobado.
    if (!respuesta.ok) {
      const problema = await leerProblema(respuesta);
      mostrarResultado(mensaje, 'rechazado', mensajeDeFallo(respuesta.status, problema));
      console.error('El pago no se completó:', respuesta.status, problema?.type);
      return 'reintentar';
    }

    const resultado = (await leerProblema(respuesta)) ?? {};
    const estado = estadoDelPago(resultado);
    const delServidor = textoDelServidor(resultado);

    if (estado === 'APROBADO') {
      const revision = resultado.marcadoParaRevisionManual === true;
      mostrarResultado(
        mensaje,
        'aprobado',
        [
          delServidor ?? '¡Pago aprobado!',
          revision ? 'Tu compra quedó marcada para una revisión de rutina.' : null,
        ]
          .filter(Boolean)
          .join(' '),
      );
      await cargarCarrito(doc);
      return 'aprobado';
    }

    if (estado === 'RECHAZADO') {
      mostrarResultado(
        mensaje,
        'rechazado',
        delServidor ?? 'La pasarela rechazó el pago. Revisa los datos o usa otra tarjeta.',
      );
      return 'reintentar';
    }

    // INDETERMINADO: ms-finanzas lo concilia. Ni se acredita ni se reintenta.
    mostrarResultado(
      mensaje,
      'revision',
      [
        delServidor ?? 'Tu pago quedó en revisión.',
        'No vuelvas a pagar: te avisaremos cuando se confirme.',
      ].join(' '),
    );
    bloquearNuevoPago(doc);
    return 'revision';
  } finally {
    borrarDatosSensibles(campos);
    confirmar.disabled = false;
    confirmar.textContent = 'Confirmar pago';
  }
}

/**
 * Abre el diálogo de resumen y pago con el carrito actual.
 *
 * @param {Document} [doc]
 * @returns {{elemento: HTMLElement, cerrar: () => void} | null}
 */
export function abrirCheckout(doc = document) {
  if (!carritoTieneItems(ultimoCarrito)) {
    return null;
  }
  const carrito = ultimoCarrito;
  const campos = camposDePago();

  const mensaje = h('p', {
    clase: 'aviso',
    atributos: { role: 'status', 'aria-live': 'polite', hidden: true },
  });
  const confirmar = h('button', {
    clase: 'boton boton--primario',
    texto: 'Confirmar pago',
    atributos: { type: 'submit' },
    datos: { accion: 'pagar' },
  });
  const cancelar = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Cancelar',
    atributos: { type: 'button' },
    datos: { accion: 'cancelar' },
  });

  const formulario = h('form', {
    clase: 'pila',
    atributos: { novalidate: true, 'aria-label': 'Datos de la tarjeta' },
    hijos: [
      campos.get('titular').elemento,
      campos.get('numero').elemento,
      h('div', {
        clase: 'fila',
        hijos: [campos.get('fechaExpiracion').elemento, campos.get('cvv').elemento],
      }),
      mensaje,
      h('div', { clase: 'acciones', hijos: [cancelar, confirmar] }),
    ],
  });

  const dialogo = abrirDialogo({
    titulo: 'Resumen de compra y pago',
    cuerpo: h('div', { clase: 'pila', hijos: [resumenDeCompra(carrito), formulario] }),
  });

  cancelar.addEventListener('click', dialogo.cerrar);

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const desenlace = await pagar(doc, { campos, mensaje, confirmar, carrito });
    if (desenlace === 'aprobado' || desenlace === 'revision') {
      // Aprobado o en revisión no queda nada que corregir: el formulario se va
      // (y con él los datos de la tarjeta) y queda el resultado con una salida.
      const volver = h('button', {
        clase: 'boton boton--primario',
        texto: 'Volver a la tienda',
        atributos: { type: 'button' },
        datos: { accion: 'volver' },
      });
      volver.addEventListener('click', dialogo.cerrar);
      formulario.replaceWith(mensaje, h('div', { clase: 'acciones', hijos: [volver] }));
      volver.focus();
    }
  });

  return dialogo;
}

/**
 * Engancha la vista.
 *
 * La escucha de «Añadir» va delegada en la rejilla: las tarjetas se crean
 * después y así no hay que volver a enganchar nada al repintar.
 *
 * @param {Document} [doc]
 * @returns {Promise<void>} resuelve cuando la primera carga terminó de pintar
 */
export function montarTienda(doc = document) {
  const rejilla = doc.getElementById('productos-grid');

  rejilla?.addEventListener('click', (evento) => {
    const boton = evento.target.closest('[data-producto]');
    if (boton) {
      agregarAlCarrito(boton.dataset.producto, doc);
    }
  });

  doc.getElementById('btn-pagar')?.addEventListener('click', () => abrirCheckout(doc));

  // Se devuelve la promesa para que las pruebas puedan esperar al pintado.
  return Promise.all([cargarVitrina(doc), cargarCarrito(doc)]).then(() => undefined);
}

// Arranque automático solo en el navegador; en las pruebas se monta a mano.
if (globalThis.document?.addEventListener) {
  globalThis.document.addEventListener('DOMContentLoaded', () => montarTienda());
}
