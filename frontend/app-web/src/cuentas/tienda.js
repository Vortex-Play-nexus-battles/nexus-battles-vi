/**
 * Vitrina y carrito — HU-CAR-001.
 *
 * Habla con `ms-ecommerce`: `GET /api/v1/productos`, `GET /api/v1/carrito` y
 * `POST /api/v1/carrito/items`.
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
import { estadoDeCarga, estadoDeError, estadoVacio } from '../comun/ui/estado-vista.js';
import { aProductoDeVitrina, aFilaDeCarrito, textoDePrecio, aImporte } from './tienda-adaptador.js';

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
    const respuesta = await fetchWithHttpErrorInterceptor(rutaDeApi('/productos'), {
      method: 'GET',
      headers: cabeceras(),
    });

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
    pintarEn(
      rejilla,
      estadoDeError({
        titulo: 'No se pudo cargar la tienda',
        detalle: 'Vuelve a intentarlo en unos momentos.',
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

export async function agregarAlCarrito(productoId, doc = document) {
  try {
    await fetchWithHttpErrorInterceptor(rutaDeApi('/carrito/items'), {
      method: 'POST',
      headers: cabeceras(),
      body: JSON.stringify({ productoId, cantidad: 1 }),
    });

    await cargarCarrito(doc);
  } catch (error) {
    console.error('Error al agregar item:', error);
  }
}

export async function cargarCarrito(doc = document) {
  try {
    const respuesta = await fetchWithHttpErrorInterceptor(rutaDeApi('/carrito'), {
      method: 'GET',
      headers: cabeceras(),
    });

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
