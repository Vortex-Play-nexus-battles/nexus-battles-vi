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
 * @module tienda
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { rutaDeApi } from '../comun/base-api.js';
import { usuarioIdDeSesion } from '../comun/identidad.js';
import { exigirSesion } from '../comun/acceso.js';
import { olvidarSesion } from '../comun/sesion.js';
import { limpiarAviso, pintarAviso, tonoPorEstado } from '../comun/ui/aviso.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../comun/ui/estado-vista.js';
import { aProductoDeVitrina, aFilaDeCarrito, textoDePrecio, aImporte } from './tienda-adaptador.js';

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

export async function cargarVitrina(doc = document) {
  const rejilla = doc.getElementById('productos-grid');

  // UX-R2.8d — no habia estado de carga: el HTML traia un comentario
  // (`<!-- Cargando productos... -->`) donde deberia ir, asi que la rejilla
  // estaba en blanco hasta que llegaba la respuesta. Ahora se ve la forma de
  // lo que viene, como en el resto de la aplicacion (RNF-USA-003).
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
      // Un catalogo vacio es un estado legitimo, y distinto de un fallo.
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

    rejilla.replaceChildren();
    for (const producto of productos) {
      rejilla.appendChild(tarjetaDeProducto(producto, doc));
    }
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
    console.error('Error al cargar la vitrina:', error);
  }
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

/**
 * Tarjeta de un producto.
 *
 * El color sale del tipo, como en la maqueta. El botón NO lleva `onclick`: la
 * escucha está delegada en la rejilla (ver `montarTienda`), que es lo único que
 * funciona cuando este archivo se carga como módulo.
 */
function tarjetaDeProducto(dto, doc) {
  // FI-R2 — la traduccion del DTO es explicita y esta probada aparte. Antes
  // esta funcion leia `dto.precio`, un campo que el servicio no devuelve.
  const producto = aProductoDeVitrina(dto);
  const tarjeta = doc.createElement('div');
  tarjeta.className = 'product-card';
  tarjeta.dataset.tipo = producto.tipo;
  // UX-R2.8 — el color de la caja se interpolaba dentro de la plantilla
  // (`background-color: ${colorCaja}`). Los dos valores eran constantes, asi
  // que no habia agujero, pero era `innerHTML` con una interpolacion: la
  // forma exacta que el guardian persigue, y la que alguien copia el dia que
  // el color venga del catalogo. Ahora el marcado es fijo y el color se pone
  // por `dataset`, con las fichas del kit.
  tarjeta.innerHTML = `
    <div class="product-image"></div>
    <h4></h4>
    <p></p>
    <p class="habilidades"></p>
    <div class="product-footer">
      <span class="precio-bloque">
        <span class="price"></span>
        <s class="price-antes"></s>
        <span class="badge-descuento"></span>
      </span>
      <button class="btn-add" type="button">Añadir</button>
    </div>
  `;
  // textContent y no innerHTML: el nombre y la descripción vienen del
  // catálogo, y un producto con `<script>` en el nombre no debe ejecutarse.
  tarjeta.querySelector('h4').textContent = producto.nombre;
  tarjeta.querySelector('p').textContent = producto.descripcion;

  // RF-CAR-001 pide la imagen del producto. La caja de color era el marcador
  // de posicion de la maqueta; `imagenUrl` viene en el DTO desde el principio.
  const caja = tarjeta.querySelector('.product-image');
  if (producto.imagenUrl) {
    const img = doc.createElement('img');
    img.src = producto.imagenUrl;
    img.alt = '';
    img.loading = 'lazy';
    caja.appendChild(img);
    caja.classList.add('con-imagen');
  }

  // RF-CAR-001 pide tambien las habilidades. Estaban en el DTO y no se
  // pintaban en ningun sitio.
  const habilidades = tarjeta.querySelector('.habilidades');
  if (producto.habilidades) {
    habilidades.textContent = producto.habilidades;
  } else {
    habilidades.remove();
  }

  // FI-R2 — sin precio no se escribe «0 COP». Cero es un precio, y decirle a
  // alguien que un objeto es gratis cuando lo que pasa es que no llego el dato
  // es exactamente la clase de mentira que esta ronda persigue.
  const precio = tarjeta.querySelector('.price');
  precio.textContent = producto.precioTexto ?? 'Precio no disponible';
  if (producto.precioTexto === null) {
    precio.classList.add('precio-ausente');
  }

  const antes = tarjeta.querySelector('.price-antes');
  if (producto.precioAnteriorTexto) {
    antes.textContent = producto.precioAnteriorTexto;
  } else {
    antes.remove();
  }

  const distintivo = tarjeta.querySelector('.badge-descuento');
  if (producto.descuento !== null) {
    distintivo.textContent = `-${producto.descuento}%`;
  } else {
    distintivo.remove();
  }

  if (producto.esPropio) {
    tarjeta.dataset.propio = 'si';
  }
  if (producto.enListaDeseos) {
    tarjeta.dataset.deseado = 'si';
  }

  // Sin id no hay nada que anadir al carrito: el boton se deshabilita en vez
  // de mandar `undefined` al servicio.
  //
  // R16 — el id es el UUID del catalogo maestro, en texto. `dataset` guarda
  // texto y `agregarAlCarrito` lo manda tal cual: ni `Number()` ni `parseInt`,
  // que convertirian un UUID en `NaN` y el producto en «inexistente».
  const boton = tarjeta.querySelector('.btn-add');
  if (producto.id === null) {
    boton.disabled = true;
    boton.title = 'Este producto llegó incompleto y no se puede añadir al carrito.';
  } else {
    boton.dataset.producto = String(producto.id);
  }

  return tarjeta;
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

  contenedor.innerHTML = '';

  if (!carrito || !carrito.items || carrito.items.length === 0) {
    contenedor.innerHTML = '<p class="t-meta">Tu carrito está vacío</p>';
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

  for (const item of carrito.items) {
    const fila = aFilaDeCarrito(item, moneda);
    const nodo = doc.createElement('div');
    nodo.className = 'cart-item';
    nodo.innerHTML = `
      <div class="item-info"><h5></h5><span></span></div>
      <div class="item-price"></div>
    `;
    nodo.querySelector('h5').textContent = fila.nombre;
    nodo.querySelector('span').textContent = `x${fila.cantidad}`;
    // FI-R2 — antes salia «undefined COP» cuando el item no traia subtotal.
    nodo.querySelector('.item-price').textContent = fila.subtotalTexto ?? 'Sin precio';
    contenedor.appendChild(nodo);
  }

  const totalTexto = textoDePrecio(aImporte(carrito.total), moneda) ?? 'Sin total';
  subtotal.textContent = totalTexto;
  total.textContent = totalTexto;
  prepararBotonDePago(botonPagar);
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

  rejilla?.addEventListener('click', (evento) => {
    const boton = evento.target.closest('[data-producto]');
    if (boton) {
      agregarAlCarrito(boton.dataset.producto, doc);
    }
  });

  // Se devuelve la promesa para que quien monte la vista pueda esperar a que
  // esté pintada. En el navegador nadie la espera; en las pruebas, sí.
  return Promise.all([cargarVitrina(doc), cargarCarrito(doc)]).then(() => undefined);
}

// Arranque automático solo en el navegador; en las pruebas se monta a mano.
if (globalThis.document?.addEventListener) {
  globalThis.document.addEventListener('DOMContentLoaded', () => montarTienda());
}
