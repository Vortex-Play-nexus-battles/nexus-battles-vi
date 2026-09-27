/**
 * Indicador de resultado de un pago procesado (HU-PAG-001, criterio de
 * aceptación "Front-end"). Traduce la respuesta de
 * `POST /pagos/procesar` (ms-finanzas) al mismo componente `aviso` que ya
 * usa el resto de la aplicación, en vez de crear una pieza visual nueva.
 *
 * Quien conecte el flujo de pago real (HU-CAR-010) importa esta función y
 * la llama con la respuesta del endpoint; este archivo no sabe nada de
 * carritos, tiendas ni subastas — solo de cómo se ve un resultado de pago.
 */

import { pintarAviso } from './aviso.js';

/**
 * @typedef {'APROBADO'|'RECHAZADO'|'INDETERMINADO'} EstadoPago
 */

/**
 * @typedef {{estado: EstadoPago, mensaje: string,
 *            marcadoParaRevisionManual: boolean}} RespuestaPago
 *   Misma forma que ProcesarPagoResponse (ms-finanzas, pagos/dto/PagoDTOs.java).
 */

/**
 * Tono y título según el estado del pago. INDETERMINADO no es un error del
 * jugador —la pasarela no respondió— así que se muestra como aviso
 * informativo, no como fallo suyo.
 *
 * @param {EstadoPago} estado
 * @returns {{tono: 'exito'|'advertencia'|'info', titulo: string}}
 */
function tonoYTituloPorEstado(estado) {
  switch (estado) {
    case 'APROBADO':
      return { tono: 'exito', titulo: 'Pago aprobado' };
    case 'RECHAZADO':
      return { tono: 'advertencia', titulo: 'Pago rechazado' };
    case 'INDETERMINADO':
      return { tono: 'info', titulo: 'Pago en revisión' };
    default:
      return { tono: 'info', titulo: 'Resultado del pago' };
  }
}

/**
 * Pinta el resultado de un pago dentro de una zona, con el mismo
 * componente `aviso` del resto de la aplicación.
 *
 * @param {HTMLElement} zona contenedor `[data-zona="aviso"]`
 * @param {RespuestaPago} respuesta la respuesta de POST /pagos/procesar
 * @returns {HTMLElement} el aviso insertado
 */
export function pintarResultadoPago(zona, respuesta) {
  const { tono, titulo } = tonoYTituloPorEstado(respuesta.estado);

  let detalle = respuesta.mensaje;
  if (respuesta.marcadoParaRevisionManual) {
    detalle = detalle
      ? `${detalle} Por el monto, además queda en revisión manual.`
      : 'Por el monto, queda en revisión manual.';
  }

  return pintarAviso(zona, { tono, titulo, detalle });
}
