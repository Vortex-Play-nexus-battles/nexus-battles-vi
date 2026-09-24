/**
 * Vitrina, carrito y pago — HU-CAR-001 y HU-CAR-010.
 *
 * Habla con `ms-ecommerce`: `GET /api/v1/productos`, `GET /api/v1/carrito`,
 * `POST /api/v1/carrito/items` y `POST /api/v1/checkout`.
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
 *
 * @module tienda
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { rutaDeApi } from '../comun/base-api.js';
import { usuarioIdDeSesion } from '../comun/identidad.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../comun/ui/estado-vista.js';
import { h, nodo } from '../comun/ui/dom.js';
import { abrirDialogo } from '../comun/ui/dialogo.js';
import { campo } from '../comun/ui/campo.js';

/** Último carrito pintado: de aquí salen el resumen y el `carritoId` del pago. */
let ultimoCarrito = null;

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

/** @param {number|string|null|undefined} valor */
function pesos(valor) {
  return `${valor ?? 0} COP`;
}

// --- RENDERIZADO DE PRODUCTOS (HU-CAR-001) ---

export async function cargarVitrina(doc = document) {
  const rejilla = doc.getElementById('productos-grid');

  // UX-R2.8d — estado de carga en lugar de una rejilla en blanco (RNF-USA-003).
  pintarEn(rejilla, estadoDeCarga({ filas: 4, etiqueta: 'Cargando la tienda…' }));

  try {
    const respuesta = await fetchWithHttpErrorInterceptor(rutaDeApi('/productos'), {
      method: 'GET',
      headers: cabeceras(),
    });

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

    rejilla.replaceChildren(...productos.map(tarjetaDeProducto));
  } catch (error) {
    // UX-R2.8d — un fallo ofrece reintentar en vez de dejar un callejón sin salida.
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
 * El color sale del tipo por `data-tipo`. El botón NO lleva `onclick`: la
 * escucha está delegada en la rejilla (ver `montarTienda`).
 */
function tarjetaDeProducto(producto) {
  return h('div', {
    clase: 'product-card',
    datos: { tipo: producto.tipo ?? '' },
    hijos: [
      nodo('div', 'product-image'),
      nodo('h4', undefined, producto.nombre ?? ''),
      nodo('p', undefined, producto.descripcion ?? ''),
      h('div', {
        clase: 'product-footer',
        hijos: [
          nodo('span', 'price', pesos(producto.precio)),
          h('button', {
            clase: 'btn-add',
            texto: 'Añadir',
            atributos: { type: 'button' },
            datos: { producto: producto.id },
          }),
        ],
      }),
    ],
  });
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

/** Fila de un ítem del carrito; la reutiliza el resumen del pago. */
function filaDeCarrito(item) {
  return h('div', {
    clase: 'cart-item',
    hijos: [
      h('div', {
        clase: 'item-info',
        hijos: [
          nodo('h5', undefined, item.producto ? item.producto.nombre : 'Producto'),
          nodo('span', undefined, `x${item.cantidad}`),
        ],
      }),
      nodo('div', 'item-price', pesos(item.subtotal)),
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
    subtotal.textContent = pesos(0);
    total.textContent = pesos(0);
    botonPagar.disabled = true;
    return;
  }

  ultimoCarrito = carrito;
  contenedor.replaceChildren(...carrito.items.map(filaDeCarrito));
  subtotal.textContent = pesos(carrito.total);
  total.textContent = pesos(carrito.total);
  botonPagar.disabled = false;
}

// --- RESUMEN Y PAGO (HU-CAR-010) ---

/**
 * Resumen de la compra, construido desde los datos del carrito.
 *
 * @param {{items: Array, total: number|string}} carrito
 * @returns {HTMLElement}
 */
export function resumenDeCompra(carrito) {
  return h('section', {
    clase: 'pila',
    atributos: { 'aria-label': 'Resumen de la compra' },
    hijos: [
      ...carrito.items.map(filaDeCarrito),
      h('p', { hijos: [nodo('strong', undefined, `Total a pagar: ${pesos(carrito.total)}`)] }),
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
 * @param {'aprobado'|'rechazado'} resultado
 * @param {string} texto
 */
function mostrarResultado(mensaje, resultado, texto) {
  mensaje.textContent = texto;
  mensaje.dataset.resultado = resultado;
  mensaje.hidden = false;
}

/**
 * Admite las dos formas razonables de respuesta de la pasarela:
 * `{ aprobado: boolean }` o `{ estado: 'APROBADO' | 'RECHAZADO' }`.
 */
function pagoAprobado(resultado) {
  if (resultado?.aprobado === false) {
    return false;
  }
  if (typeof resultado?.estado === 'string' && /RECHAZ/i.test(resultado.estado)) {
    return false;
  }
  return true;
}

function mensajeDeFallo(error) {
  const estado = error?.estado ?? error?.status;
  if (estado === 401) {
    return 'Tu sesión expiró durante el pago. Inicia sesión de nuevo: el pago no se procesó.';
  }
  if (estado === 400 || estado === 402 || estado === 422) {
    return 'La pasarela rechazó el pago. Revisa los datos o usa otra tarjeta.';
  }
  if (estado) {
    return 'No pudimos procesar el pago en este momento. Vuelve a intentarlo en unos minutos.';
  }
  return 'Error de conexión con el servidor. Revisa tu conexión y vuelve a intentarlo.';
}

/**
 * Valida, envía y pinta el resultado.
 *
 * @returns {Promise<boolean>} true si el pago quedó aprobado
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
    return false;
  }

  confirmar.disabled = true;
  confirmar.textContent = 'Procesando…';

  try {
    const respuesta = await fetchWithHttpErrorInterceptor(rutaDeApi('/checkout'), {
      method: 'POST',
      headers: cabeceras(),
      body: JSON.stringify({
        carritoId: carrito?.id ?? null,
        tarjeta: { ...datos, numero: datos.numero.replace(/[\s-]/g, '') },
      }),
    });

    const resultado = await respuesta.json().catch(() => ({}));

    if (!pagoAprobado(resultado)) {
      mostrarResultado(
        mensaje,
        'rechazado',
        resultado?.mensaje || 'La pasarela rechazó el pago. Revisa los datos o usa otra tarjeta.',
      );
      return false;
    }

    mostrarResultado(
      mensaje,
      'aprobado',
      '¡Pago aprobado! Los productos se añadieron a tu inventario. Te enviamos la confirmación por correo.',
    );
    await cargarCarrito(doc);
    return true;
  } catch (error) {
    mostrarResultado(mensaje, 'rechazado', mensajeDeFallo(error));
    // Solo el error: los datos de la tarjeta no se escriben nunca en consola.
    console.error('Error al procesar el pago:', error);
    return false;
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
    const aprobado = await pagar(doc, { campos, mensaje, confirmar, carrito });
    if (aprobado) {
      // Tras aprobar no queda nada que corregir: el formulario se va y solo
      // queda el resultado con una salida clara.
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
