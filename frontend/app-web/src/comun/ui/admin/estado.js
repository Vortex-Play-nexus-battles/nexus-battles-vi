/**
 * El estado de algo, dicho con palabras — SA-2.
 *
 * Un distintivo con texto, icono y color: el color nunca va solo (WCAG
 * 1.4.1). Y el texto es el de una persona, no la constante del contrato:
 * «Fuera de este entorno», no «NO_DESPLEGADO»; «Suspendida», no «SUSPENDIDO».
 *
 * @module comun/ui/admin/estado
 */

import { h } from '../dom.js';
import { icono } from '../icono.js';

/** Los tonos que sabe pintar, con su icono. */
export const TONOS_DE_ESTADO = Object.freeze({
  exito: 'check',
  advertencia: 'reloj',
  error: 'alerta',
  bloqueado: 'candado',
  info: 'ojo',
  neutro: null,
});

/**
 * La salud de un servicio, tal como la mide la sonda de la consola
 * (`/admin/sistema/servicios`).
 */
export const ESTADOS_DE_SERVICIO = Object.freeze({
  OPERATIVO: { texto: 'Operativo', tono: 'exito' },
  LENTO: { texto: 'Lento', tono: 'advertencia' },
  CAIDO: { texto: 'Caído', tono: 'error' },
  NO_DESPLEGADO: { texto: 'Fuera de este entorno', tono: 'neutro' },
  NO_OBSERVABLE: { texto: 'En otro servidor', tono: 'neutro' },
});

/** El estado de una cuenta (ms-identidad), en femenino: «la cuenta está…». */
export const ESTADOS_DE_CUENTA_ADMIN = Object.freeze({
  ACTIVO: { texto: 'Activa', tono: 'exito' },
  INACTIVO: { texto: 'Pendiente de activar', tono: 'neutro' },
  PENDIENTE_VERIFICACION: { texto: 'Sin verificar', tono: 'neutro' },
  SUSPENDIDO: { texto: 'Suspendida', tono: 'advertencia' },
  BANEADO: { texto: 'Baneada', tono: 'error' },
  BLOQUEADA: { texto: 'Bloqueada', tono: 'bloqueado' },
});

/**
 * El resultado de un cobro en moneda real (transacciones.yaml,
 * `ResultadoTransaccion`): lo que pasó con el pago, no la constante.
 */
export const ESTADOS_DE_PAGO = Object.freeze({
  APROBADO: { texto: 'Aprobado', tono: 'exito' },
  RECHAZADO: { texto: 'Rechazado', tono: 'error' },
  INDETERMINADO: { texto: 'Sin confirmar', tono: 'advertencia' },
});

/** El estado de una sala de batalla (salas-partidas, `EstadoSala`). */
export const ESTADOS_DE_SALA = Object.freeze({
  ABIERTA: { texto: 'Abierta', tono: 'exito' },
  LLENA: { texto: 'Llena', tono: 'advertencia' },
  EN_JUEGO: { texto: 'En juego', tono: 'info' },
  PRIVADA: { texto: 'Privada', tono: 'neutro' },
  CANCELADA: { texto: 'Cancelada', tono: 'neutro' },
  FINALIZADA: { texto: 'Finalizada', tono: 'neutro' },
});

/** El estado de un torneo (torneos, `TorneoResumen.estado`). */
export const ESTADOS_DE_TORNEO = Object.freeze({
  INSCRIPCIONES_ABIERTAS: { texto: 'Inscripciones abiertas', tono: 'exito' },
  EN_CURSO: { texto: 'En curso', tono: 'info' },
  FINALIZADO: { texto: 'Finalizado', tono: 'neutro' },
  CANCELADO: { texto: 'Cancelado', tono: 'neutro' },
});

/**
 * «NO_DESPLEGADO» → «No desplegado»: para un estado que el mapa no conoce, al
 * menos una lectura humana en vez de la constante.
 *
 * @param {string|null|undefined} codigo
 * @returns {string}
 */
export function humanizar(codigo) {
  const texto = String(codigo ?? '')
    .replaceAll('_', ' ')
    .trim()
    .toLowerCase();
  return texto ? texto.charAt(0).toUpperCase() + texto.slice(1) : 'Sin dato';
}

/**
 * @param {{texto: string, tono?: keyof typeof TONOS_DE_ESTADO}} estado
 * @returns {HTMLElement}
 */
export function distintivoDeEstado({ texto, tono = 'neutro' }) {
  const clave = Object.prototype.hasOwnProperty.call(TONOS_DE_ESTADO, tono) ? tono : 'neutro';
  const caja = h('span', { clase: 'estado-admin', datos: { tono: clave } });
  const simbolo = TONOS_DE_ESTADO[clave];
  if (simbolo) {
    caja.append(icono(simbolo, { etiqueta: null, clase: 'icono estado-admin__icono' }));
  } else {
    caja.append(h('span', { clase: 'estado-admin__punto', atributos: { 'aria-hidden': 'true' } }));
  }
  caja.append(h('span', { texto }));
  return caja;
}

/**
 * El distintivo de un código según un mapa (`ESTADOS_DE_SERVICIO`,
 * `ESTADOS_DE_CUENTA_ADMIN`…). Un código que el mapa no conoce se humaniza.
 *
 * @param {string|null|undefined} codigo
 * @param {Record<string, {texto: string, tono: string}>} mapa
 * @returns {HTMLElement}
 */
export function estadoSegun(codigo, mapa) {
  const conocido = mapa?.[String(codigo ?? '')];
  const distintivo = distintivoDeEstado(conocido ?? { texto: humanizar(codigo), tono: 'neutro' });
  distintivo.dataset.estado = String(codigo ?? '');
  return distintivo;
}
