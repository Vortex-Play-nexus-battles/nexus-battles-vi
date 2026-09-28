/**
 * El pago y «Mis compras» — B5.
 *
 * §7.5 del documento: «cuando el cliente quiera proceder al pago, se debe
 * mostrar una vista de resumen de la compra con el detalle de los productos,
 * el total a pagar y un formulario para ingresar los datos de pago», con el
 * nombre del titular, el número de tarjeta, la fecha de vencimiento y el
 * código de seguridad, y «un botón para confirmar el pago», contra una
 * pasarela de pagos simulada (`POST /api/v1/checkout`,
 * ecommerce-carrito.yaml 1.4.0).
 *
 * ## Lo que nunca sale de este formulario
 *
 * El número y el código de seguridad viven en los campos del diálogo y en el
 * cuerpo de UNA petición, y en ningún sitio más: no se guardan en
 * `sessionStorage` ni en `localStorage`, no se escriben en la consola y no
 * viajan en la dirección. Al cerrar el diálogo sus nodos desaparecen. El
 * servidor guarda solo la marca y los cuatro últimos dígitos.
 *
 * ## Un intento, una clave
 *
 * Cada intento de pago lleva su `Idempotency-Key`. Se **reutiliza** cuando la
 * respuesta dice que no se cobró nada y se puede reintentar sin riesgo: la
 * pasarela no respondió (503 `pasarela-no-disponible`, la orden sigue
 * PENDIENTE), la red no contestó, o el pago ya se está procesando (409
 * `compra-en-curso`). Así un segundo clic en «Confirmar pago» reintenta la
 * misma orden y el servidor nunca cobra dos veces. Se **cambia** cuando el
 * intento terminó (completada, rechazada, reembolsada) o cuando cambia el
 * carrito: pagar otra cosa es otra compra. La clave vive en memoria, con la
 * página.
 *
 * El precio no lo pone el navegador: el cuerpo lleva la tarjeta y la moneda,
 * y el servidor cobra lo que dice su carrito.
 *
 * @module cuentas/tienda-pago
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { rutaDeApi } from '../comun/base-api.js';
import { abrirDialogo } from '../comun/ui/dialogo.js';
import { clases, h } from '../comun/ui/dom.js';
import { aImporte, textoDePrecio } from './tienda-adaptador.js';

/** Cómo se llama cada estado de la orden para el jugador. */
export const ESTADOS_DE_ORDEN = Object.freeze({
  PENDIENTE: 'Pendiente de pago',
  COBRADA: 'Pagada, en entrega',
  ENTREGADA: 'Entregada',
  COMPLETA: 'Completada',
  RECHAZADA: 'Rechazada',
  COMPENSACION_PENDIENTE: 'Reembolso en curso',
  REEMBOLSADA: 'Reembolsada',
});

/** El distintivo del kit de cada estado. */
const DISTINTIVO_DEL_ESTADO = Object.freeze({
  COMPLETA: 'distintivo--completada',
  ENTREGADA: 'distintivo--completada',
  RECHAZADA: 'distintivo--suspendido',
  REEMBOLSADA: 'distintivo--aviso',
});

/** Los campos del formulario y su nombre en el contrato (`SolicitudDePago`). */
export const CAMPOS_DE_PAGO = Object.freeze({
  titular: 'titular',
  numero: 'numeroTarjeta',
  vencimiento: 'vencimiento',
  codigo: 'codigoSeguridad',
});

const PREFIJO = 'urn:nexus:problema:';

// ------------------------------------------------------------- validación

/**
 * Luhn sobre una cadena de dígitos.
 *
 * @param {string} digitos
 * @returns {boolean}
 */
export function pasaLuhn(digitos) {
  let suma = 0;
  let doblar = false;
  for (let i = digitos.length - 1; i >= 0; i -= 1) {
    let cifra = Number(digitos[i]);
    if (doblar) {
      cifra *= 2;
      if (cifra > 9) {
        cifra -= 9;
      }
    }
    suma += cifra;
    doblar = !doblar;
  }
  return digitos.length > 0 && suma % 10 === 0;
}

/**
 * El vencimiento como lo pide el contrato (`MM/AA`), se escriba como se
 * escriba: «1229», «12/29», «12-29», «1/29». El campo abre el teclado
 * numérico en un teléfono (`inputmode="numeric"`), que no tiene barra: sin
 * esto, desde un teléfono no se podría escribir el vencimiento.
 *
 * @param {unknown} valor
 * @returns {string} `MM/AA`, o el texto sin espacios si no se entiende (y la
 *   validación dirá qué falla)
 */
export function normalizarVencimiento(valor) {
  const texto = String(valor ?? '').replace(/\s+/g, '');
  const conSeparador = /^(\d{1,2})[/-](\d{2})$/.exec(texto);
  if (conSeparador) {
    return `${conSeparador[1].padStart(2, '0')}/${conSeparador[2]}`;
  }
  const seguido = /^(\d{2})(\d{2})$/.exec(texto);
  return seguido ? `${seguido[1]}/${seguido[2]}` : texto;
}

/**
 * Las mismas reglas que aplica el servidor (que es quien decide), para decir
 * el error junto al campo antes de enviar nada. Los mensajes nunca repiten el
 * valor escrito.
 *
 * @param {{titular?: string, numero?: string, vencimiento?: string, codigo?: string}} valores
 * @param {Date} [ahora]
 * @returns {Array<{campo: string, mensaje: string}>} en el orden del formulario
 */
export function validarPago(valores, ahora = new Date()) {
  const errores = [];
  const titular = String(valores.titular ?? '').trim();
  if (titular.length < 3 || titular.length > 80) {
    errores.push({
      campo: 'titular',
      mensaje: 'Escribe el nombre del titular como aparece en la tarjeta (de 3 a 80 caracteres).',
    });
  }
  const numero = String(valores.numero ?? '').trim();
  const digitos = numero.replace(/ /g, '');
  if (!/^[0-9 ]{12,23}$/.test(numero) || digitos.length < 12 || digitos.length > 19) {
    errores.push({
      campo: 'numero',
      mensaje:
        'El número de la tarjeta tiene entre 12 y 19 dígitos; puedes separarlos con espacios.',
    });
  } else if (!pasaLuhn(digitos)) {
    errores.push({ campo: 'numero', mensaje: 'Ese número de tarjeta no es válido. Revísalo.' });
  }
  const vencimiento = normalizarVencimiento(valores.vencimiento);
  const partes = /^(0[1-9]|1[0-2])\/([0-9]{2})$/.exec(vencimiento);
  if (!partes) {
    errores.push({
      campo: 'vencimiento',
      mensaje: 'Escribe el vencimiento como MM/AA, por ejemplo 08/29.',
    });
  } else {
    const mes = Number(partes[1]);
    const anio = 2000 + Number(partes[2]);
    const hoy = ahora.getFullYear() * 12 + ahora.getMonth();
    if (anio * 12 + (mes - 1) < hoy) {
      errores.push({ campo: 'vencimiento', mensaje: 'La tarjeta está vencida.' });
    }
  }
  if (!/^[0-9]{3,4}$/.test(String(valores.codigo ?? '').trim())) {
    errores.push({
      campo: 'codigo',
      mensaje: 'El código de seguridad son los 3 o 4 dígitos del reverso de la tarjeta.',
    });
  }
  return errores;
}

// ------------------------------------------------------------- la clave

/** La clave del intento en curso, por documento y solo en memoria. */
const CLAVES = new WeakMap();

/**
 * Una clave nueva de intento de pago (8..100 caracteres, contrato 1.4.0).
 *
 * @returns {string}
 */
export function nuevaClaveDePago() {
  const aleatoria =
    typeof globalThis.crypto?.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
  return `pago-${aleatoria}`;
}

/**
 * La clave del intento en curso, o una nueva si no hay ninguno.
 *
 * @param {Document} doc
 * @returns {string}
 */
export function claveDelIntento(doc = document) {
  if (!CLAVES.has(doc)) {
    CLAVES.set(doc, nuevaClaveDePago());
  }
  return CLAVES.get(doc);
}

/**
 * Da por terminado el intento: el siguiente pago lleva otra clave.
 *
 * @param {Document} doc
 */
export function olvidarIntentoDePago(doc = document) {
  CLAVES.delete(doc);
}

// ------------------------------------------------------------- la petición

/**
 * `POST /api/v1/checkout`.
 *
 * @param {{solicitud: object, clave: string, fetchImpl?: Function}} opciones
 * @returns {Promise<{estado: number, orden: object|null, problema: object|null}>}
 *   `estado` 0 si no hubo respuesta
 */
export async function enviarPago({ solicitud, clave, fetchImpl = fetchWithHttpErrorInterceptor }) {
  let respuesta;
  try {
    respuesta = await fetchImpl(rutaDeApi('/checkout'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': clave },
      body: JSON.stringify(solicitud),
    });
  } catch {
    return { estado: 0, orden: null, problema: null };
  }
  let cuerpo = null;
  try {
    cuerpo = await respuesta.json();
  } catch {
    cuerpo = null;
  }
  const objeto = cuerpo && typeof cuerpo === 'object' ? cuerpo : null;
  return respuesta.ok
    ? { estado: respuesta.status, orden: objeto, problema: null }
    : { estado: respuesta.status, orden: null, problema: objeto };
}

/**
 * Lo que dice el correo de confirmación de una orden terminada.
 *
 * @param {object|null} orden
 * @returns {string}
 */
function textoDelCorreo(orden) {
  if (orden?.correoConfirmacion === 'ENVIADO') {
    return ' Te enviamos la confirmación por correo.';
  }
  if (orden?.correoConfirmacion === 'PENDIENTE') {
    return ' La confirmación te llegará por correo en unos minutos.';
  }
  return '';
}

/**
 * Qué significa una respuesta de `POST /checkout` para el jugador. Por el
 * `status`, el `type` y el `estado` de la orden, nunca por el texto libre.
 *
 * @param {{estado: number, orden: object|null, problema: object|null}} respuesta
 * @param {{moneda?: string}} [contexto]
 * @returns {{tipo: string, titulo: string, detalle: string, conservarClave: boolean,
 *   orden: object|null, campo: string|null, monedasDisponibles: string[]|null}}
 *   `tipo`: completa, en-curso, rechazada, reembolsada, reintentar, corregir,
 *   carrito, sesion, moneda o no-disponible
 */
export function interpretarPago({ estado, orden, problema }, { moneda = 'COP' } = {}) {
  const base = {
    conservarClave: false,
    orden: null,
    campo: null,
    monedasDisponibles: null,
  };
  const motivo =
    (typeof problema?.motivo === 'string' && problema.motivo.trim()) ||
    (typeof orden?.motivo === 'string' && orden.motivo.trim()) ||
    null;

  if (orden) {
    if (orden.estado === 'COMPLETA') {
      return {
        ...base,
        tipo: 'completa',
        orden,
        titulo: 'Compra completada',
        detalle: `Tus productos ya están en tu inventario.${textoDelCorreo(orden)}`,
      };
    }
    if (orden.estado === 'RECHAZADA') {
      return {
        ...base,
        tipo: 'rechazada',
        orden,
        titulo: 'Pago rechazado',
        detalle: `${motivo ?? 'La pasarela rechazó el pago.'} Tu carrito sigue guardado.`,
      };
    }
    if (orden.estado === 'REEMBOLSADA' || orden.estado === 'COMPENSACION_PENDIENTE') {
      return {
        ...base,
        tipo: 'reembolsada',
        orden,
        titulo: 'No se pudo completar la compra',
        detalle: `${motivo ?? 'No se pudo entregar.'} Se te devuelve el pago.`,
      };
    }
    // PENDIENTE es «aún no se cobró» (la pasarela no contestó): el servidor la
    // reintenta con la misma clave en vez de devolverla, pero si llegara, no
    // es un «pago recibido». Se reintenta el mismo pago, con la misma clave.
    if (orden.estado === 'PENDIENTE') {
      return {
        ...base,
        tipo: 'reintentar',
        conservarClave: true,
        titulo: 'El pago no se completó',
        detalle:
          'No se cobró nada. Vuelve a pulsar «Confirmar pago»: se reintenta el mismo pago, sin cobrarte dos veces.',
      };
    }
    return {
      ...base,
      tipo: 'en-curso',
      orden,
      titulo: 'Pago recibido',
      detalle:
        'Estamos terminando tu compra: tus productos aparecerán en tu inventario en unos minutos. Puedes seguirla en «Mis compras».',
    };
  }

  const tipo = typeof problema?.type === 'string' ? problema.type.replace(PREFIJO, '') : '';
  const delServidor =
    typeof problema?.detail === 'string' && problema.detail.trim() ? problema.detail : null;

  if (estado === 0) {
    return {
      ...base,
      tipo: 'reintentar',
      conservarClave: true,
      titulo: 'No hubo respuesta',
      detalle:
        'Comprueba tu conexión y vuelve a pulsar «Confirmar pago»: si el pago llegó, no se cobrará dos veces.',
    };
  }
  if (estado === 401) {
    return {
      ...base,
      tipo: 'sesion',
      titulo: 'Tu sesión ya no es válida',
      detalle: 'Vuelve a iniciar sesión para pagar. Tu carrito sigue guardado.',
    };
  }
  if (tipo === 'pago-rechazado') {
    return {
      ...base,
      tipo: 'rechazada',
      titulo: 'Pago rechazado',
      detalle: `${motivo ?? 'La pasarela rechazó el pago.'} Tu carrito sigue guardado: puedes intentarlo con otra tarjeta.`,
    };
  }
  if (tipo === 'compra-reembolsada') {
    return {
      ...base,
      tipo: 'reembolsada',
      titulo: 'No se pudo completar la compra',
      detalle: `${motivo ?? 'No se pudo entregar.'} Se te devolvió el pago.`,
    };
  }
  if (tipo === 'pasarela-no-disponible') {
    return {
      ...base,
      tipo: 'reintentar',
      conservarClave: true,
      titulo: 'La pasarela de pagos no respondió',
      detalle:
        'No se cobró nada. Vuelve a pulsar «Confirmar pago»: se reintenta el mismo pago, sin cobrarte dos veces.',
    };
  }
  if (tipo === 'compra-en-curso') {
    return {
      ...base,
      tipo: 'reintentar',
      conservarClave: true,
      titulo: 'Tu pago se está procesando',
      detalle:
        'Espera unos segundos y vuelve a pulsar «Confirmar pago» para ver cómo quedó: no se cobrará dos veces.',
    };
  }
  if (tipo === 'datos-de-pago-invalidos') {
    const delFormulario =
      Object.entries(CAMPOS_DE_PAGO).find(
        ([, delContrato]) => delContrato === problema?.campo,
      )?.[0] ?? null;
    return {
      ...base,
      tipo: 'corregir',
      conservarClave: true,
      campo: delFormulario,
      titulo: 'Revisa los datos de pago',
      detalle: delServidor ?? 'Algún dato de la tarjeta no es válido.',
    };
  }
  if (tipo === 'moneda-no-disponible') {
    return {
      ...base,
      tipo: 'moneda',
      monedasDisponibles: Array.isArray(problema?.monedasDisponibles)
        ? problema.monedasDisponibles
        : ['COP'],
      titulo: `Los pagos en ${moneda} no están disponibles`,
      detalle: 'La tienda vuelve a pesos colombianos (COP). Revisa el total antes de pagar.',
    };
  }
  if (
    tipo === 'carrito-vacio' ||
    tipo === 'producto-agotado' ||
    tipo === 'producto-no-disponible' ||
    tipo === 'producto-inexistente' ||
    tipo === 'producto-sin-precio-en-moneda-real' ||
    tipo === 'tiraje-insuficiente' ||
    tipo === 'cantidad-fuera-de-rango'
  ) {
    return {
      ...base,
      tipo: 'carrito',
      conservarClave: true,
      titulo: tipo === 'carrito-vacio' ? 'Tu carrito está vacío' : 'Tu carrito cambió',
      detalle: delServidor ?? 'Revisa tu carrito antes de pagar. No se cobró nada.',
    };
  }
  if (tipo === 'clave-de-idempotencia-reutilizada' || tipo === 'clave-de-idempotencia-requerida') {
    return {
      ...base,
      tipo: 'reintentar',
      titulo: 'Vuelve a confirmar el pago',
      detalle: 'Ese intento ya no sirve para esta compra. Pulsa otra vez «Confirmar pago».',
    };
  }
  return {
    ...base,
    tipo: 'no-disponible',
    conservarClave: true,
    titulo: 'El pago no está disponible en este momento',
    detalle: 'No se cobró nada y tu carrito sigue guardado. Inténtalo de nuevo en unos minutos.',
  };
}

// ------------------------------------------------------------- el diálogo

let contador = 0;

/**
 * Un campo del formulario: etiqueta, control, pista y hueco del error, ya
 * enlazados con `aria-describedby`.
 */
function campo({ id, nombre, etiqueta, pista = null, atributos }) {
  const idError = `${id}-error`;
  const idPista = pista ? `${id}-pista` : null;
  const control = h('input', {
    clase: 'campo__control',
    atributos: {
      id,
      name: nombre,
      type: 'text',
      required: true,
      'aria-describedby': clases(idPista, idError),
      ...atributos,
    },
  });
  const error = h('p', { clase: 'campo__error', atributos: { id: idError } });
  error.hidden = true;
  return h('div', {
    clase: 'campo',
    datos: { campo: nombre },
    hijos: [
      h('label', { clase: 'campo__etiqueta', texto: etiqueta, atributos: { for: id } }),
      control,
      pista ? h('p', { clase: 'campo__pista', texto: pista, atributos: { id: idPista } }) : null,
      error,
    ],
  });
}

/** El resumen de la compra: cada producto, su cantidad y su subtotal, y el total. */
function resumenDeCompra(carrito, moneda) {
  const lineas = (Array.isArray(carrito?.items) ? carrito.items : [])
    .filter((item) => item?.disponible !== false)
    .map((item) => {
      const subtotal = textoDePrecio(aImporte(item.subtotal), moneda) ?? '—';
      return h('li', {
        clase: 'pago__linea',
        hijos: [
          h('span', { texto: `${item.producto?.nombre || 'Producto'} × ${item.cantidad ?? 1}` }),
          h('span', { clase: 'pago__importe', texto: subtotal }),
        ],
      });
    });
  const total = textoDePrecio(aImporte(carrito?.total), moneda) ?? '—';
  return h('section', {
    clase: 'pago__resumen',
    atributos: { 'aria-label': 'Resumen de la compra' },
    hijos: [
      h('h3', { clase: 'pago__subtitulo', texto: 'Resumen de la compra' }),
      h('ul', { clase: 'pago__lineas', hijos: lineas }),
      h('p', {
        clase: 'pago__total',
        hijos: [h('span', { texto: 'Total a pagar' }), h('strong', { texto: total })],
      }),
    ],
  });
}

/**
 * Abre el resumen de la compra con el formulario de pago.
 *
 * @param {{doc?: Document, carrito: object, moneda: string, fetchImpl?: Function,
 *          alTerminar?: (orden: object|null) => void,
 *          alCambiarCarrito?: () => void,
 *          alCambiarMoneda?: (disponibles: string[]) => void,
 *          alPedirSesion?: () => void,
 *          alVerCompras?: () => void}} opciones
 *   `alTerminar`: la compra se cobró (completa, en curso o reembolsada): la
 *   tienda recarga el carrito y lo que tiene el jugador.
 * @returns {{elemento: HTMLElement, cerrar: () => void}}
 */
export function abrirPago({
  doc = document,
  carrito,
  moneda,
  fetchImpl = fetchWithHttpErrorInterceptor,
  alTerminar = () => {},
  alCambiarCarrito = () => {},
  alCambiarMoneda = () => {},
  alPedirSesion = () => {},
  alVerCompras = () => {},
}) {
  contador += 1;
  const id = (nombre) => `pago-${nombre}-${contador}`;
  const total = textoDePrecio(aImporte(carrito?.total), moneda) ?? '';

  const estado = h('p', {
    clase: 'pago__estado',
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  const alerta = h('div', { clase: 'pago__alerta', atributos: { role: 'alert' } });
  alerta.hidden = true;

  const campos = {
    titular: campo({
      id: id('titular'),
      nombre: 'titular',
      etiqueta: 'Nombre del titular de la tarjeta',
      atributos: { autocomplete: 'cc-name', maxlength: '80', spellcheck: 'false' },
    }),
    numero: campo({
      id: id('numero'),
      nombre: 'numero',
      etiqueta: 'Número de tarjeta',
      pista: 'Entre 12 y 19 dígitos. No se guarda: solo quedan la marca y los cuatro últimos.',
      atributos: { autocomplete: 'cc-number', inputmode: 'numeric', maxlength: '23' },
    }),
    vencimiento: campo({
      id: id('vencimiento'),
      nombre: 'vencimiento',
      etiqueta: 'Fecha de vencimiento (MM/AA)',
      atributos: {
        autocomplete: 'cc-exp',
        inputmode: 'numeric',
        maxlength: '5',
        placeholder: 'MM/AA',
      },
    }),
    codigo: campo({
      id: id('codigo'),
      nombre: 'codigo',
      etiqueta: 'Código de seguridad',
      pista: 'Los 3 o 4 dígitos del reverso. No se guarda.',
      atributos: { autocomplete: 'cc-csc', inputmode: 'numeric', maxlength: '4' },
    }),
  };

  const confirmar = h('button', {
    clase: 'boton boton--primario',
    texto: total ? `Confirmar pago de ${total}` : 'Confirmar pago',
    datos: { accion: 'confirmar-pago' },
    atributos: { type: 'submit' },
  });
  const cancelar = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Cancelar',
    datos: { accion: 'cancelar-pago' },
    atributos: { type: 'button' },
  });

  const formulario = h('form', {
    clase: 'pago__formulario',
    atributos: { novalidate: true, 'aria-label': 'Datos de pago' },
    hijos: [
      h('h3', { clase: 'pago__subtitulo', texto: 'Datos de pago' }),
      h('p', {
        clase: 'pago__nota',
        texto: 'La pasarela de pagos es simulada: no se cobra dinero real.',
      }),
      campos.titular,
      campos.numero,
      h('div', { clase: 'pago__fila', hijos: [campos.vencimiento, campos.codigo] }),
      alerta,
      estado,
      h('div', { clase: 'pago__acciones', hijos: [cancelar, confirmar] }),
    ],
  });

  const cuerpo = h('div', { clase: 'pago', hijos: [resumenDeCompra(carrito, moneda), formulario] });
  const dialogo = abrirDialogo({ titulo: 'Pagar tu compra', cuerpo, cerrableFuera: false });
  dialogo.elemento.classList.add('dialogo--ancho', 'dialogo--pago');
  cancelar.addEventListener('click', () => dialogo.cerrar());
  campos.titular.querySelector('input').focus();

  const control = (nombre) => campos[nombre].querySelector('input');

  const limpiarErrores = () => {
    for (const nombre of Object.keys(campos)) {
      const error = campos[nombre].querySelector('.campo__error');
      error.textContent = '';
      error.hidden = true;
      control(nombre).removeAttribute('aria-invalid');
    }
    alerta.hidden = true;
    alerta.replaceChildren();
  };

  const marcarError = (nombre, mensaje) => {
    const error = campos[nombre].querySelector('.campo__error');
    error.textContent = mensaje;
    error.hidden = false;
    control(nombre).setAttribute('aria-invalid', 'true');
  };

  const avisar = (titulo, detalle, acciones = []) => {
    alerta.replaceChildren(
      h('p', { clase: 'pago__alerta-titulo', texto: titulo }),
      h('p', { texto: detalle }),
      acciones.length > 0 ? h('div', { clase: 'pago__acciones', hijos: acciones }) : null,
    );
    alerta.hidden = false;
  };

  /** Sustituye el formulario por el resultado de una compra que se cobró. */
  const terminar = (resultado) => {
    const verCompras = h('button', {
      clase: 'boton boton--secundario',
      texto: 'Ver mis compras',
      datos: { accion: 'ver-mis-compras' },
      atributos: { type: 'button' },
    });
    const seguir = h('button', {
      clase: 'boton boton--primario',
      texto: 'Seguir comprando',
      datos: { accion: 'seguir-comprando' },
      atributos: { type: 'button' },
    });
    verCompras.addEventListener('click', () => {
      dialogo.cerrar();
      alVerCompras();
    });
    seguir.addEventListener('click', () => dialogo.cerrar());
    const titulo = h('h3', {
      clase: 'pago__resultado-titulo',
      texto: resultado.titulo,
      atributos: { tabindex: '-1' },
    });
    const panel = h('div', {
      clase: 'pago__resultado',
      datos: { resultado: resultado.tipo, estado: resultado.orden?.estado ?? '' },
      atributos: { role: 'status', 'aria-live': 'polite' },
      hijos: [
        titulo,
        h('p', { texto: resultado.detalle }),
        h('div', { clase: 'pago__acciones', hijos: [verCompras, seguir] }),
      ],
    });
    formulario.replaceWith(panel);
    titulo.focus();
  };

  // Un pago a la vez: mientras se procesa, el botón se apaga y otro envío
  // (Intro, doble clic) no sale.
  let enCurso = false;
  const ocupar = () => {
    enCurso = true;
    confirmar.disabled = true;
    confirmar.setAttribute('aria-busy', 'true');
    estado.textContent = 'Procesando el pago…';
  };
  const liberar = () => {
    enCurso = false;
    confirmar.disabled = false;
    confirmar.removeAttribute('aria-busy');
    estado.textContent = '';
  };

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (enCurso) {
      return;
    }
    limpiarErrores();
    // «1229» se queda escrito como «12/29»: lo que se ve es lo que se envía.
    control('vencimiento').value = normalizarVencimiento(control('vencimiento').value);
    const errores = validarPago({
      titular: control('titular').value,
      numero: control('numero').value,
      vencimiento: control('vencimiento').value,
      codigo: control('codigo').value,
    });
    if (errores.length > 0) {
      for (const { campo: nombre, mensaje } of errores) {
        marcarError(nombre, mensaje);
      }
      estado.textContent = 'Revisa los datos marcados.';
      control(errores[0].campo).focus();
      return;
    }

    ocupar();
    const respuesta = await enviarPago({
      clave: claveDelIntento(doc),
      // Se construye aquí y no se guarda en ningún sitio: vive lo que dura la petición.
      solicitud: {
        titular: control('titular').value.trim(),
        numeroTarjeta: control('numero').value.trim(),
        vencimiento: control('vencimiento').value,
        codigoSeguridad: control('codigo').value.trim(),
        moneda,
      },
      fetchImpl,
    });
    liberar();

    const resultado = interpretarPago(respuesta, { moneda });
    if (!resultado.conservarClave) {
      olvidarIntentoDePago(doc);
    }

    switch (resultado.tipo) {
      case 'completa':
      case 'en-curso':
      case 'reembolsada':
        terminar(resultado);
        alTerminar(resultado.orden);
        return;
      case 'rechazada':
        // Otra tarjeta es otro intento: el número y el código se vacían.
        control('numero').value = '';
        control('codigo').value = '';
        avisar(resultado.titulo, resultado.detalle);
        control('numero').focus();
        return;
      case 'corregir':
        if (resultado.campo) {
          marcarError(resultado.campo, resultado.detalle);
          control(resultado.campo).focus();
        } else {
          avisar(resultado.titulo, resultado.detalle);
          confirmar.focus();
        }
        return;
      case 'carrito': {
        const volver = h('button', {
          clase: 'boton boton--secundario',
          texto: 'Volver al carrito',
          datos: { accion: 'volver-al-carrito' },
          atributos: { type: 'button' },
        });
        volver.addEventListener('click', () => {
          dialogo.cerrar();
          alCambiarCarrito();
        });
        avisar(resultado.titulo, resultado.detalle, [volver]);
        volver.focus();
        return;
      }
      case 'moneda': {
        const volver = h('button', {
          clase: 'boton boton--secundario',
          texto: 'Volver a la tienda',
          datos: { accion: 'volver-a-la-tienda' },
          atributos: { type: 'button' },
        });
        volver.addEventListener('click', () => {
          dialogo.cerrar();
          alCambiarMoneda(resultado.monedasDisponibles ?? ['COP']);
        });
        avisar(resultado.titulo, resultado.detalle, [volver]);
        volver.focus();
        return;
      }
      case 'sesion': {
        const entrar = h('button', {
          clase: 'boton boton--primario',
          texto: 'Iniciar sesión',
          datos: { accion: 'iniciar-sesion' },
          atributos: { type: 'button' },
        });
        entrar.addEventListener('click', () => {
          dialogo.cerrar();
          alPedirSesion();
        });
        avisar(resultado.titulo, resultado.detalle, [entrar]);
        entrar.focus();
        return;
      }
      default:
        // reintentar y no-disponible: el formulario sigue relleno, y el mismo
        // botón reintenta con la misma clave.
        avisar(resultado.titulo, resultado.detalle);
        confirmar.focus();
    }
  });

  return dialogo;
}

// ------------------------------------------------------------- Mis compras

/**
 * `GET /api/v1/ordenes`: las compras del jugador, de la más reciente a la más
 * antigua.
 *
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<object[]>}
 * @throws {Error & {estado: number}} si el servicio no las da
 */
export async function pedirMisCompras({ fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(rutaDeApi('/ordenes'), {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });
  if (!respuesta.ok) {
    const error = new Error(`Las compras respondieron ${respuesta.status}`);
    error.estado = respuesta.status;
    throw error;
  }
  const cuerpo = await respuesta.json();
  return Array.isArray(cuerpo) ? cuerpo : [];
}

/** Fecha y hora legibles, o null si la fecha no se entiende. */
function fechaLegible(iso) {
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) {
    return null;
  }
  return fecha.toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

/**
 * Una compra de la lista: cuándo, en qué estado, qué, cuánto y con qué
 * tarjeta (marca y cuatro últimos, lo único que se guarda).
 *
 * @param {object} orden `Orden` del contrato
 * @returns {HTMLElement}
 */
export function tarjetaDeOrden(orden) {
  const moneda = typeof orden?.moneda === 'string' ? orden.moneda : null;
  const estado = ESTADOS_DE_ORDEN[orden?.estado] ?? 'En proceso';
  const lineas = (Array.isArray(orden?.lineas) ? orden.lineas : []).map((linea) =>
    h('li', {
      texto: `${linea.nombre || 'Producto'} × ${linea.cantidad ?? 1} · ${
        textoDePrecio(aImporte(linea.subtotal), moneda) ?? '—'
      }`,
    }),
  );
  const medio = orden?.medioDePago?.ultimos4
    ? `${orden.medioDePago.marca ?? 'Tarjeta'} ···· ${orden.medioDePago.ultimos4}`
    : null;
  const motivo =
    (orden?.estado === 'RECHAZADA' || orden?.estado === 'REEMBOLSADA') && orden?.motivo
      ? orden.motivo
      : null;
  return h('article', {
    clase: 'compra',
    datos: { estado: String(orden?.estado ?? '') },
    hijos: [
      h('div', {
        clase: 'compra__cabecera',
        hijos: [
          h('p', { clase: 'compra__fecha', texto: fechaLegible(orden?.creadaEn) ?? 'Sin fecha' }),
          h('span', {
            clase: clases(
              'distintivo',
              DISTINTIVO_DEL_ESTADO[orden?.estado] ?? 'distintivo--en-progreso',
              'compra__estado',
            ),
            texto: estado,
          }),
        ],
      }),
      h('ul', { clase: 'compra__lineas', hijos: lineas }),
      h('p', {
        clase: 'compra__total',
        texto: `Total: ${textoDePrecio(aImporte(orden?.total), moneda) ?? '—'}`,
      }),
      medio ? h('p', { clase: 'compra__medio', texto: `Pagada con ${medio}` }) : null,
      motivo ? h('p', { clase: 'compra__motivo', texto: motivo }) : null,
    ],
  });
}

/**
 * Abre «Mis compras».
 *
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{elemento: HTMLElement, cerrar: () => void}>}
 */
export async function abrirMisCompras({ fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const contenido = h('div', {
    clase: 'compras',
    atributos: { 'aria-live': 'polite', 'aria-busy': 'true' },
    hijos: [h('p', { clase: 't-meta', texto: 'Cargando tus compras…' })],
  });
  const dialogo = abrirDialogo({ titulo: 'Mis compras', cuerpo: contenido });
  dialogo.elemento.classList.add('dialogo--ancho', 'dialogo--compras');

  const pintar = async () => {
    contenido.setAttribute('aria-busy', 'true');
    let ordenes;
    try {
      ordenes = await pedirMisCompras({ fetchImpl });
    } catch (error) {
      const reintentar = h('button', {
        clase: 'boton boton--secundario',
        texto: 'Reintentar',
        datos: { accion: 'reintentar-compras' },
        atributos: { type: 'button' },
      });
      reintentar.addEventListener('click', pintar);
      contenido.replaceChildren(
        h('p', {
          texto:
            error?.estado === 401
              ? 'Tu sesión ya no es válida. Vuelve a iniciar sesión para ver tus compras.'
              : 'No se pudieron cargar tus compras. Vuelve a intentarlo.',
        }),
        reintentar,
      );
      contenido.setAttribute('aria-busy', 'false');
      return;
    }
    contenido.replaceChildren(
      ordenes.length === 0
        ? h('p', { texto: 'Todavía no has comprado nada en la tienda.' })
        : h('div', { clase: 'compras__lista', hijos: ordenes.map(tarjetaDeOrden) }),
    );
    contenido.setAttribute('aria-busy', 'false');
  };
  await pintar();
  return dialogo;
}
