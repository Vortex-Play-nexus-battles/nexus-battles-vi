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
 * ## Pagar con créditos del Nexo (D-44, ecommerce-carrito.yaml 1.6.0)
 *
 * Un método adicional en el mismo diálogo: «¿Cómo quieres pagar? ○ Créditos
 * del Nexo ○ Pago simulado». Con créditos no hay formulario: se pide la
 * cotización al servidor (`GET /api/v1/checkout/creditos`) y se enseña tal
 * cual —saldo actual, precio, saldo después—, sin sumar ni restar nada aquí;
 * «Confirmar» hace `POST /api/v1/checkout/creditos`, sin cuerpo, con su
 * `Idempotency-Key`. Las mismas reglas de la clave: se reutiliza cuando no se
 * sabe si se cobró (sin respuesta, 503 `creditos-no-disponibles`, 409
 * `compra-en-curso`) y se cambia al terminar o al cambiar de forma de pago.
 * Al terminar: «Compra realizada» y «Ver inventario».
 *
 * @module cuentas/tienda-pago
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { rutaDeApi } from '../comun/base-api.js';
import { RUTAS, resolver } from '../comun/sesion.js';
import { abrirDialogo } from '../comun/ui/dialogo.js';
import { clases, h } from '../comun/ui/dom.js';
import { CREDITOS, aImporte, textoDeCreditos, textoDePrecio } from './tienda-adaptador.js';

/** Las formas de pago del diálogo (`Orden.formaDePago`, contrato 1.6.0). */
export const FORMAS_DE_PAGO = Object.freeze({ CREDITOS: 'CREDITOS', TARJETA: 'TARJETA' });

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
  return enviarCompra(
    '/checkout',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': clave },
      body: JSON.stringify(solicitud),
    },
    fetchImpl,
  );
}

/**
 * D-44 — `POST /api/v1/checkout/creditos`: paga el carrito con los créditos
 * del juego. Sin cuerpo: el precio lo pone el servidor.
 *
 * @param {{clave: string, fetchImpl?: Function}} opciones
 * @returns {Promise<{estado: number, orden: object|null, problema: object|null}>}
 */
export async function enviarPagoConCreditos({ clave, fetchImpl = fetchWithHttpErrorInterceptor }) {
  return enviarCompra(
    '/checkout/creditos',
    { method: 'POST', headers: { 'Idempotency-Key': clave } },
    fetchImpl,
  );
}

/**
 * D-44 — `GET /api/v1/checkout/creditos`: lo que costaría el carrito en
 * créditos, el saldo y cómo quedaría (`CotizacionEnCreditos`).
 *
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<{estado: number, cotizacion: object|null}>} `estado` 0 sin respuesta
 */
export async function pedirCotizacionEnCreditos({
  fetchImpl = fetchWithHttpErrorInterceptor,
} = {}) {
  let respuesta;
  try {
    respuesta = await fetchImpl(rutaDeApi('/checkout/creditos'), {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
  } catch {
    return { estado: 0, cotizacion: null };
  }
  if (!respuesta.ok) {
    return { estado: respuesta.status, cotizacion: null };
  }
  try {
    const cuerpo = await respuesta.json();
    return {
      estado: respuesta.status,
      cotizacion: cuerpo && typeof cuerpo === 'object' ? cuerpo : null,
    };
  } catch {
    return { estado: respuesta.status, cotizacion: null };
  }
}

/** Una petición de compra y su lectura: la orden si salió bien, el problem detail si no. */
async function enviarCompra(ruta, peticion, fetchImpl) {
  let respuesta;
  try {
    respuesta = await fetchImpl(rutaDeApi(ruta), peticion);
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
    let titulo = 'Tu carrito cambió';
    if (tipo === 'carrito-vacio') {
      titulo = 'Tu carrito está vacío';
    } else if (tipo === 'producto-sin-precio-en-moneda-real') {
      // G3 (1.7.0): algo del carrito solo se vende en créditos del juego.
      titulo = 'Hay algo que solo se paga con créditos';
    }
    return {
      ...base,
      tipo: 'carrito',
      conservarClave: true,
      titulo,
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

/** Lo que se dice cuando no se sabe si el cobro en créditos llegó. */
const CONFIRMA_OTRA_VEZ =
  'Vuelve a pulsar «Confirmar compra»: si el cobro llegó, no se te cobrará dos veces.';

/**
 * D-44 — qué significa una respuesta de `POST /checkout/creditos`, por el
 * `status`, el `type` y el `estado` de la orden. Lo propio de los créditos
 * (saldo, cobro sin confirmar, devolución) se dice aquí; lo que es igual que
 * con tarjeta (sesión, carrito que cambió, servicio no disponible) lo dice
 * {@link interpretarPago}.
 *
 * @param {{estado: number, orden: object|null, problema: object|null}} respuesta
 * @returns {ReturnType<typeof interpretarPago>} `tipo` además puede ser `saldo`
 */
export function interpretarPagoConCreditos(respuesta) {
  const { estado, orden, problema } = respuesta;
  const base = { conservarClave: false, orden: null, campo: null, monedasDisponibles: null };
  const motivo =
    (typeof problema?.motivo === 'string' && problema.motivo.trim()) ||
    (typeof orden?.motivo === 'string' && orden.motivo.trim()) ||
    null;

  if (orden) {
    const total = textoDePrecio(aImporte(orden.total), CREDITOS);
    switch (orden.estado) {
      case 'COMPLETA':
        return {
          ...base,
          tipo: 'completa',
          orden,
          titulo: 'Compra realizada',
          detalle: `${total ? `Pagaste ${total}. ` : ''}Tus productos ya están en tu inventario.`,
        };
      case 'RECHAZADA':
        return {
          ...base,
          tipo: 'saldo',
          orden,
          titulo: 'No se pudo pagar con créditos',
          detalle: `${motivo ?? 'No se cobró nada.'} Tu carrito sigue guardado.`,
        };
      case 'REEMBOLSADA':
      case 'COMPENSACION_PENDIENTE':
        return {
          ...base,
          tipo: 'reembolsada',
          orden,
          titulo: 'No se pudo completar la compra',
          detalle: `${motivo ?? 'No se pudo entregar.'} Te devolvemos los créditos.`,
        };
      case 'PENDIENTE':
        return {
          ...base,
          tipo: 'reintentar',
          conservarClave: true,
          titulo: 'El cobro no se confirmó',
          detalle: CONFIRMA_OTRA_VEZ,
        };
      default:
        return {
          ...base,
          tipo: 'en-curso',
          orden,
          titulo: 'Compra pagada',
          detalle:
            'Estamos entregando tus productos: aparecerán en tu inventario en unos minutos. Puedes seguirla en «Mis compras».',
        };
    }
  }

  const tipo = typeof problema?.type === 'string' ? problema.type.replace(PREFIJO, '') : '';
  if (estado === 0) {
    return {
      ...base,
      tipo: 'reintentar',
      conservarClave: true,
      titulo: 'No hubo respuesta',
      detalle: `Comprueba tu conexión. ${CONFIRMA_OTRA_VEZ}`,
    };
  }
  if (tipo === 'saldo-insuficiente') {
    const costaba = Number.isInteger(problema?.totalCreditos)
      ? ` La compra cuesta ${textoDeCreditos(problema.totalCreditos)}.`
      : '';
    return {
      ...base,
      tipo: 'saldo',
      titulo: 'No tienes créditos suficientes',
      detalle: `No se cobró nada.${costaba} Tu carrito sigue guardado.`,
    };
  }
  if (tipo === 'creditos-no-disponibles' || tipo === 'compra-en-curso') {
    return {
      ...base,
      tipo: 'reintentar',
      conservarClave: true,
      titulo:
        tipo === 'compra-en-curso'
          ? 'Tu compra se está procesando'
          : 'No pudimos confirmar el cobro',
      detalle: CONFIRMA_OTRA_VEZ,
    };
  }
  if (tipo === 'producto-sin-precio-en-creditos') {
    return {
      ...base,
      tipo: 'carrito',
      conservarClave: true,
      titulo: 'Algo de tu carrito no se vende con créditos',
      detalle:
        (typeof problema?.detail === 'string' && problema.detail.trim()) ||
        'Quítalo del carrito o paga con tarjeta. No se cobró nada.',
    };
  }
  if (tipo === 'compra-reembolsada') {
    return {
      ...base,
      tipo: 'reembolsada',
      titulo: 'No se pudo completar la compra',
      detalle: `${motivo ?? 'No se pudo entregar.'} Te devolvimos los créditos.`,
    };
  }
  if (tipo === 'clave-de-idempotencia-reutilizada' || tipo === 'clave-de-idempotencia-requerida') {
    return {
      ...base,
      tipo: 'reintentar',
      titulo: 'Vuelve a confirmar la compra',
      detalle: 'Ese intento ya no sirve para esta compra. Pulsa otra vez «Confirmar compra».',
    };
  }
  return interpretarPago(respuesta);
}

/**
 * D-44 — la cotización en créditos tal como la manda el servidor: cada línea
 * con su subtotal, y saldo actual, precio y saldo después. Aquí no se suma ni
 * se resta nada: si una cifra no vino, se dice que no se sabe.
 *
 * @param {object} cotizacion `CotizacionEnCreditos` (contrato 1.6.0)
 * @returns {HTMLElement}
 */
export function panelDeCreditos(cotizacion) {
  const cifra = (valor) => (Number.isInteger(valor) ? textoDeCreditos(valor) : null);
  const lineas = (Array.isArray(cotizacion?.lineas) ? cotizacion.lineas : []).map((linea) =>
    h('li', {
      clase: 'pago__linea',
      hijos: [
        h('span', { texto: `${linea.nombre || 'Producto'} × ${linea.cantidad ?? 1}` }),
        h('span', {
          clase: 'pago__importe',
          texto: cifra(linea.subtotalCreditos) ?? 'No se vende con créditos',
        }),
      ],
    }),
  );
  const fila = (zona, etiqueta, valor) =>
    h('div', {
      clase: 'pago__cuenta',
      datos: { zona },
      hijos: [h('dt', { texto: etiqueta }), h('dd', { texto: valor })],
    });
  const saldo = cifra(cotizacion?.saldoDisponible);
  const precio = cifra(cotizacion?.totalCreditos);
  const despues = cifra(cotizacion?.saldoDespues);
  const avisos = [];
  if (cotizacion?.motivo === 'SIN_PRECIO_EN_CREDITOS') {
    avisos.push(
      'Algún producto de tu carrito no se vende con créditos. Quítalo o paga con el pago simulado.',
    );
  } else if (cotizacion?.motivo === 'PRODUCTO_NO_DISPONIBLE') {
    avisos.push(
      'Tu carrito cambió: algún producto ya no se puede comprar. Revísalo antes de pagar.',
    );
  } else if (cotizacion?.motivo === 'CARRITO_VACIO') {
    avisos.push('Tu carrito está vacío: no hay nada que pagar.');
  } else if (cotizacion?.alcanza === false && Number.isInteger(cotizacion?.saldoDespues)) {
    avisos.push(`No te alcanza: te faltan ${textoDeCreditos(-cotizacion.saldoDespues)}.`);
  } else if (saldo === null && cotizacion?.pagable) {
    avisos.push(
      'No pudimos consultar tu saldo ahora. Puedes confirmar igual: si no alcanza, no se cobra nada.',
    );
  }
  return h('section', {
    clase: 'pago__resumen pago__resumen--creditos',
    atributos: { 'aria-label': 'Pago con créditos del Nexo' },
    hijos: [
      h('h3', { clase: 'pago__subtitulo', texto: 'Pagar con créditos del Nexo' }),
      lineas.length > 0 ? h('ul', { clase: 'pago__lineas', hijos: lineas }) : null,
      h('dl', {
        clase: 'pago__cuentas',
        hijos: [
          fila('saldo-actual', 'Saldo actual', saldo ?? 'No disponible ahora'),
          fila('precio', 'Precio', precio ?? '—'),
          fila('saldo-despues', 'Saldo después', despues ?? '—'),
        ],
      }),
      ...avisos.map((texto) =>
        h('p', { clase: 'pago__nota', datos: { aviso: 'creditos' }, texto }),
      ),
    ],
  });
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

  // D-44 — «¿Cómo quieres pagar?»: créditos del juego o el pago simulado de
  // siempre, que sigue siendo el que viene marcado. G3 (1.7.0): si algo del
  // carrito solo se vende en créditos, con tarjeta no hay nada que cobrar por
  // ello (el servidor respondería 422): se paga con créditos y el pago
  // simulado se apaga diciendo por qué.
  const soloConCreditos = productosSoloEnCreditos(carrito);
  const formas = selectorDeFormaDePago(id, { soloConCreditos });
  const zonaTarjeta = h('div', {
    clase: 'pago__zona',
    datos: { zona: 'tarjeta' },
    hijos: [
      h('h3', { clase: 'pago__subtitulo', texto: 'Datos de pago' }),
      h('p', {
        clase: 'pago__nota',
        texto: 'La pasarela de pagos es simulada: no se cobra dinero real.',
      }),
      campos.titular,
      campos.numero,
      h('div', { clase: 'pago__fila', hijos: [campos.vencimiento, campos.codigo] }),
    ],
  });
  const zonaCreditos = h('div', {
    clase: 'pago__zona',
    datos: { zona: 'creditos' },
    atributos: { 'aria-live': 'polite' },
  });
  zonaCreditos.hidden = true;

  const formulario = h('form', {
    clase: 'pago__formulario',
    atributos: { novalidate: true, 'aria-label': 'Datos de pago' },
    hijos: [
      formas.elemento,
      zonaTarjeta,
      zonaCreditos,
      alerta,
      estado,
      h('div', { clase: 'pago__acciones', hijos: [cancelar, confirmar] }),
    ],
  });

  const resumen = resumenDeCompra(carrito, moneda);
  const cuerpo = h('div', { clase: 'pago', hijos: [resumen, formulario] });
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
    // RFINAL-07 — sin acciones no se pasa `null`: `replaceChildren` lo pintaría
    // como el texto «null» debajo del aviso.
    alerta.replaceChildren(
      h('p', { clase: 'pago__alerta-titulo', texto: titulo }),
      h('p', { texto: detalle }),
      ...(acciones.length > 0 ? [h('div', { clase: 'pago__acciones', hijos: acciones })] : []),
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
    // D-44 — lo comprado ya está en el inventario: se puede ir a verlo.
    const entregada = ['COMPLETA', 'ENTREGADA'].includes(resultado.orden?.estado);
    const verInventario = entregada
      ? h('a', {
          clase: 'boton boton--primario',
          texto: 'Ver inventario',
          datos: { accion: 'ver-inventario' },
          atributos: { href: resolver(RUTAS.inventario) },
        })
      : null;
    if (verInventario) {
      seguir.className = 'boton boton--secundario';
    }
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
        h('div', { clase: 'pago__acciones', hijos: [verCompras, seguir, verInventario] }),
      ],
    });
    resumen.hidden = false;
    formulario.replaceWith(panel);
    titulo.focus();
  };

  // ---------------------------------------------- D-44: con créditos del Nexo

  let forma = FORMAS_DE_PAGO.TARJETA;
  /** La última cotización del servidor; null mientras no hay una. */
  let cotizacion = null;

  const puedePagarConCreditos = () =>
    cotizacion?.pagable === true &&
    cotizacion?.alcanza !== false &&
    Number.isInteger(cotizacion?.totalCreditos);

  // Un pago a la vez: mientras se procesa, el botón se apaga y otro envío
  // (Intro, doble clic) no sale.
  let enCurso = false;
  const ocupar = (texto = 'Procesando el pago…') => {
    enCurso = true;
    confirmar.disabled = true;
    confirmar.setAttribute('aria-busy', 'true');
    estado.textContent = texto;
  };
  const liberar = () => {
    enCurso = false;
    confirmar.disabled = forma === FORMAS_DE_PAGO.CREDITOS ? !puedePagarConCreditos() : false;
    confirmar.removeAttribute('aria-busy');
    estado.textContent = '';
  };

  const textoDeConfirmar = () => {
    if (forma === FORMAS_DE_PAGO.CREDITOS) {
      return puedePagarConCreditos()
        ? `Confirmar compra por ${textoDeCreditos(cotizacion.totalCreditos)}`
        : 'Confirmar compra';
    }
    return total ? `Confirmar pago de ${total}` : 'Confirmar pago';
  };

  /** Pide la cotización y la enseña tal cual; «Confirmar» solo si se puede pagar así. */
  const cargarCotizacion = async () => {
    cotizacion = null;
    confirmar.disabled = true;
    confirmar.textContent = textoDeConfirmar();
    zonaCreditos.setAttribute('aria-busy', 'true');
    zonaCreditos.replaceChildren(h('p', { clase: 'pago__nota', texto: 'Consultando tu saldo…' }));
    const { estado: codigo, cotizacion: leida } = await pedirCotizacionEnCreditos({ fetchImpl });
    if (forma !== FORMAS_DE_PAGO.CREDITOS) {
      return;
    }
    zonaCreditos.setAttribute('aria-busy', 'false');
    if (!leida) {
      const reintentar = h('button', {
        clase: 'boton boton--secundario',
        texto: 'Reintentar',
        datos: { accion: 'reintentar-cotizacion' },
        atributos: { type: 'button' },
      });
      reintentar.addEventListener('click', cargarCotizacion);
      zonaCreditos.replaceChildren(
        h('p', {
          clase: 'pago__nota',
          texto:
            codigo === 401
              ? 'Tu sesión ya no es válida. Vuelve a iniciar sesión para pagar con créditos.'
              : 'No pudimos calcular el precio en créditos ahora. Vuelve a intentarlo o usa el pago simulado.',
        }),
        reintentar,
      );
      return;
    }
    cotizacion = leida;
    zonaCreditos.replaceChildren(panelDeCreditos(leida));
    confirmar.disabled = !puedePagarConCreditos();
    confirmar.textContent = textoDeConfirmar();
  };

  /** Otra forma de pago es otro intento: otra clave, sin errores del anterior. */
  const cambiarForma = async (nueva) => {
    if (nueva === forma || enCurso) {
      return;
    }
    forma = nueva;
    olvidarIntentoDePago(doc);
    limpiarErrores();
    estado.textContent = '';
    const conCreditos = forma === FORMAS_DE_PAGO.CREDITOS;
    zonaTarjeta.hidden = conCreditos;
    zonaCreditos.hidden = !conCreditos;
    // Con créditos el resumen es el del servidor, en créditos: el de dinero
    // real no se enseña para no poner dos precios distintos a la misma compra.
    resumen.hidden = conCreditos;
    if (conCreditos) {
      await cargarCotizacion();
    } else {
      confirmar.disabled = false;
      confirmar.textContent = textoDeConfirmar();
    }
  };
  formas.creditos.addEventListener('change', () => cambiarForma(FORMAS_DE_PAGO.CREDITOS));
  formas.tarjeta.addEventListener('change', () => cambiarForma(FORMAS_DE_PAGO.TARJETA));
  if (soloConCreditos.length > 0) {
    // G3: se abre ya en créditos, con su cotización.
    cambiarForma(FORMAS_DE_PAGO.CREDITOS);
    formas.creditos.focus();
  }

  const volverAlCarrito = (resultado) => {
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
  };

  const pedirSesion = (resultado) => {
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
  };

  /** `POST /checkout/creditos` con la clave del intento, y qué significó. */
  const pagarConCreditos = async () => {
    if (!puedePagarConCreditos()) {
      return;
    }
    ocupar('Procesando la compra…');
    const respuesta = await enviarPagoConCreditos({ clave: claveDelIntento(doc), fetchImpl });
    liberar();
    const resultado = interpretarPagoConCreditos(respuesta);
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
      case 'saldo':
        avisar(resultado.titulo, resultado.detalle);
        // El saldo pudo cambiar: se vuelve a preguntar y «Confirmar» se apaga si no alcanza.
        await cargarCotizacion();
        return;
      case 'carrito':
        volverAlCarrito(resultado);
        return;
      case 'sesion':
        pedirSesion(resultado);
        return;
      default:
        // reintentar y no-disponible: el mismo botón reintenta con la misma clave.
        avisar(resultado.titulo, resultado.detalle);
        confirmar.focus();
    }
  };

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (enCurso) {
      return;
    }
    if (forma === FORMAS_DE_PAGO.CREDITOS) {
      limpiarErrores();
      await pagarConCreditos();
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
      case 'carrito':
        volverAlCarrito(resultado);
        return;
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
      case 'sesion':
        pedirSesion(resultado);
        return;
      default:
        // reintentar y no-disponible: el formulario sigue relleno, y el mismo
        // botón reintenta con la misma clave.
        avisar(resultado.titulo, resultado.detalle);
        confirmar.focus();
    }
  });

  return dialogo;
}

/**
 * G3 (ecommerce-carrito 1.7.0): los nombres de lo que el carrito tiene que
 * solo se vende en créditos del juego (líneas `soloEnCreditos` que se pueden
 * comprar). Con alguno, el pago simulado no puede cobrar el carrito.
 *
 * @param {object} carrito `Carrito` del contrato
 * @returns {string[]}
 */
export function productosSoloEnCreditos(carrito) {
  return (Array.isArray(carrito?.items) ? carrito.items : [])
    .filter((item) => item?.soloEnCreditos === true && item?.disponible !== false)
    .map((item) => item.producto?.nombre || 'Un producto');
}

/**
 * D-44 — «¿Cómo quieres pagar?»: créditos del Nexo o el pago simulado. Un
 * grupo de dos radios con su leyenda; el pago simulado viene marcado.
 *
 * G3 — si el carrito tiene algo que solo se vende en créditos, vienen marcados
 * los créditos y el pago simulado se apaga con el motivo en su pista.
 *
 * @param {(nombre: string) => string} id
 * @param {{soloConCreditos?: string[]}} [opciones]
 * @returns {{elemento: HTMLElement, creditos: HTMLInputElement, tarjeta: HTMLInputElement}}
 */
function selectorDeFormaDePago(id, { soloConCreditos = [] } = {}) {
  const sinTarjeta = soloConCreditos.length > 0;
  const opcion = (valor, nombre, pista, marcada, apagada = false) => {
    const control = h('input', {
      atributos: {
        type: 'radio',
        name: 'forma-de-pago',
        value: valor,
        id: id(`forma-${valor.toLowerCase()}`),
        checked: marcada,
        disabled: apagada,
      },
    });
    const elemento = h('label', {
      clase: 'pago__forma',
      atributos: { for: control.id },
      hijos: [
        control,
        h('span', { clase: 'pago__forma-nombre', texto: nombre }),
        h('span', { clase: 'pago__forma-pista', texto: pista }),
      ],
    });
    return { control, elemento };
  };
  const creditos = opcion(
    FORMAS_DE_PAGO.CREDITOS,
    'Créditos del Nexo',
    'Con el saldo de créditos del juego.',
    sinTarjeta,
  );
  const tarjeta = opcion(
    FORMAS_DE_PAGO.TARJETA,
    'Pago simulado',
    sinTarjeta
      ? `No disponible: ${soloConCreditos.map((nombre) => `«${nombre}»`).join(', ')} solo se ${
          soloConCreditos.length > 1 ? 'venden' : 'vende'
        } con créditos del juego.`
      : 'Con tarjeta, en la pasarela simulada.',
    !sinTarjeta,
    sinTarjeta,
  );
  if (sinTarjeta) {
    tarjeta.elemento.dataset.motivo = 'solo-creditos';
  }
  const elemento = h('fieldset', {
    clase: 'pago__formas',
    hijos: [
      h('legend', { clase: 'pago__subtitulo', texto: '¿Cómo quieres pagar?' }),
      creditos.elemento,
      tarjeta.elemento,
    ],
  });
  return { elemento, creditos: creditos.control, tarjeta: tarjeta.control };
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
  // D-44: una compra con créditos no tiene tarjeta; se dice con qué se pagó.
  let medio = null;
  if (orden?.formaDePago === FORMAS_DE_PAGO.CREDITOS) {
    medio = 'créditos del Nexo';
  } else if (orden?.medioDePago?.ultimos4) {
    medio = `${orden.medioDePago.marca ?? 'Tarjeta'} ···· ${orden.medioDePago.ultimos4}`;
  }
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
