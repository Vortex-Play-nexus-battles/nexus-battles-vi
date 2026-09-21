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

/**
 * Cabeceras de cada petición.
 *
 * `X-User-Id` lo exige `CarritoController` de `ms-ecommerce`, así que se manda;
 * lo que cambia es que ahora lleva **al usuario de verdad**. Sin sesión se
 * omite: mejor que el backend responda 400 a que el carrito de alguien se
 * mezcle con el de otro.
 */
function cabeceras() {
  const usuario = usuarioIdDeSesion();
  const base = { 'Content-Type': 'application/json' };
  return usuario ? { ...base, 'X-User-Id': usuario } : base;
}

/** @returns {boolean} true si hay una sesión utilizable */
export function haySesion() {
  return usuarioIdDeSesion() !== null;
}

// --- RENDERIZADO DE PRODUCTOS (HU-CAR-001) ---

export async function cargarVitrina(doc = document) {
  const rejilla = doc.getElementById('productos-grid');
  try {
    const respuesta = await fetchWithHttpErrorInterceptor(rutaDeApi('/productos'), {
      method: 'GET',
      headers: cabeceras(),
    });

    const datos = await respuesta.json();
    rejilla.innerHTML = '';

    for (const producto of datos.content ?? []) {
      rejilla.appendChild(tarjetaDeProducto(producto, doc));
    }
  } catch (error) {
    // Antes esto no existía: un fallo dejaba el cargador girando para siempre.
    rejilla.innerHTML =
      '<p class="empty-cart-msg">No se pudo cargar la vitrina. Vuelve a intentarlo.</p>';
    console.error('Error al cargar la vitrina:', error);
  }
}

/**
 * Tarjeta de un producto.
 *
 * El color sale del tipo, como en la maqueta. El botón NO lleva `onclick`: la
 * escucha está delegada en la rejilla (ver `montarTienda`), que es lo único que
 * funciona cuando este archivo se carga como módulo.
 */
function tarjetaDeProducto(producto, doc) {
  const colorCaja = producto.tipo === 'ARMA' ? '#006b8f' : '#6a1b9a';

  const tarjeta = doc.createElement('div');
  tarjeta.className = 'product-card';
  tarjeta.innerHTML = `
    <div class="product-image" style="background-color: ${colorCaja};"></div>
    <h4></h4>
    <p></p>
    <div class="product-footer">
      <span class="price"></span>
      <button class="btn-add" type="button">Añadir</button>
    </div>
  `;
  // textContent y no innerHTML: el nombre y la descripción vienen del
  // catálogo, y un producto con `<script>` en el nombre no debe ejecutarse.
  tarjeta.querySelector('h4').textContent = producto.nombre ?? '';
  tarjeta.querySelector('p').textContent = producto.descripcion ?? '';
  tarjeta.querySelector('.price').textContent = `${producto.precio ?? 0} COP`;
  tarjeta.querySelector('.btn-add').dataset.producto = producto.id;

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
    contenedor.innerHTML =
      '<p class="empty-cart-msg">No se pudo cargar tu carrito. Vuelve a intentarlo.</p>';
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
    contenedor.innerHTML = '<p class="empty-cart-msg">Tu carrito está vacío</p>';
    subtotal.textContent = '0 COP';
    total.textContent = '0 COP';
    botonPagar.disabled = true;
    return;
  }

  for (const item of carrito.items) {
    const fila = doc.createElement('div');
    fila.className = 'cart-item';
    fila.innerHTML = `
      <div class="item-info"><h5></h5><span></span></div>
      <div class="item-price"></div>
    `;
    fila.querySelector('h5').textContent = item.producto ? item.producto.nombre : 'Producto';
    fila.querySelector('span').textContent = `x${item.cantidad}`;
    fila.querySelector('.item-price').textContent = `${item.subtotal} COP`;
    contenedor.appendChild(fila);
  }

  subtotal.textContent = `${carrito.total} COP`;
  total.textContent = `${carrito.total} COP`;
  botonPagar.disabled = false;
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

// Referencias al DOM
const btnPagar = document.getElementById('btn-pagar');
const checkoutModal = document.getElementById('checkout-modal');
const closeModal = document.getElementById('close-modal');
const paymentForm = document.getElementById('payment-form');
const paymentMessage = document.getElementById('payment-message');

// Abrir modal al hacer clic en PAGAR
if (btnPagar) {
    btnPagar.addEventListener('click', () => {
        // Aquí puedes clonar el HTML de tu carrito actual hacia #checkout-summary para mostrar el resumen
        document.getElementById('checkout-summary').innerHTML = document.getElementById('cart-items').innerHTML;
        if (checkoutModal) checkoutModal.classList.remove('hidden');
    });
}

// Cerrar modal
if (closeModal) {
    closeModal.addEventListener('click', () => {
        if (checkoutModal) checkoutModal.classList.add('hidden');
    });
}

// Interceptar el formulario de pago
if (paymentForm) {
    paymentForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        const btnConfirm = document.getElementById('btn-confirm-payment');
        if (btnConfirm) {
            btnConfirm.disabled = true;
            btnConfirm.textContent = 'Procesando...';
        }

        // Construir el payload. Los datos van a viajar en el body.
        const payload = {
            carritoId: 1, // ID dinámico de tu carrito actual
            tarjeta: {
                titular: document.getElementById('card-name').value,
                numero: document.getElementById('card-number').value,
                fechaExpiracion: document.getElementById('card-expiry').value,
                cvv: document.getElementById('card-cvv').value
            }
        };

        try {
            // Utilizamos fetchWithHttpErrorInterceptor y rutaDeApi para ser compatibles con el estándar del equipo
            const response = await fetchWithHttpErrorInterceptor(rutaDeApi('/checkout'), {
                method: 'POST',
                headers: cabeceras(),
                body: JSON.stringify(payload)
            });

            // fetchWithHttpErrorInterceptor devuelve la respuesta cruda si todo sale bien
            const result = await response.json();

            if (paymentMessage) {
                paymentMessage.textContent = '¡Pago aprobado! Los productos han sido añadidos a tu inventario. Revisa tu correo.';
                paymentMessage.className = 'success-msg';
            }
            paymentForm.reset();

        } catch (error) {
            if (paymentMessage) {
                // Maneja los errores arrojados por el interceptor del equipo
                paymentMessage.textContent = `Error: ${error.mensaje || 'Pago rechazado o error de conexión'}`;
                paymentMessage.className = 'error-msg';
            }
        } finally {
            if (paymentMessage) paymentMessage.classList.remove('hidden');
            if (btnConfirm) {
                btnConfirm.disabled = false;
                btnConfirm.textContent = 'Confirmar Pago';
            }
        }
    });
}
