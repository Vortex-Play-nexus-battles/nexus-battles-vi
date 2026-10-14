/**
 * Las alertas de moderación — §7.3.8 «Alertas de actividad sospechosa o alta
 * frecuencia de reportes» y RF-MET-001 (SA-4, metricas-plataforma 1.11.0).
 *
 * El servicio de métricas compara cada serie por día con su umbral, que vive
 * en Parámetros (D-25: 3 o más sanciones en un día, fijado por el PO; el de
 * reportes, sin cifra todavía) y publica los días que llegaron en `avisos`.
 * Aquí no se calcula nada: se dice, serie por serie, si la alerta está
 * activa, a partir de cuánto avisa y qué días llegaron.
 *
 * Una serie sin umbral no es «sin alertas»: es una alerta que no se evalúa, y
 * se dice así. Y un indicador que no se pudo leer se dice en una frase, nunca
 * con el motivo técnico que el servicio deja en `pendientes`.
 *
 * Lo usan el tablero de moderación y la consola de control integral.
 *
 * @module comun/ui/admin/alertas-de-moderacion
 */

import { h } from '../dom.js';
import { numero } from '../formato.js';
import { distintivo } from '../distintivo.js';
import { distintivoDeEstado } from './estado.js';
import { diaCompleto } from './fechas.js';

/** Las dos series con umbral de alta frecuencia, en el orden en que se leen. */
export const SERIES_CON_UMBRAL = Object.freeze([
  Object.freeze({
    tipo: 'SANCIONES',
    titulo: 'Sanciones por día',
    umbral: 'umbralSancionesPorDia',
    singular: 'sanción',
    plural: 'sanciones',
  }),
  Object.freeze({
    tipo: 'REPORTES',
    titulo: 'Reportes por día',
    umbral: 'umbralReportesPorDia',
    singular: 'reporte',
    plural: 'reportes',
  }),
]);

/**
 * «1 sanción», «4 sanciones».
 *
 * @param {number} cantidad
 * @param {{singular: string, plural: string}} serie
 * @returns {string}
 */
export function contar(cantidad, { singular, plural }) {
  return `${numero(cantidad)} ${cantidad === 1 ? singular : plural}`;
}

/** Un umbral publicado: entero y positivo, o null. */
function umbralDe(datos, serie) {
  const valor = datos?.[serie.umbral];
  return Number.isInteger(valor) && valor > 0 ? valor : null;
}

/**
 * A partir de cuánto avisa una serie, o por qué no avisa.
 *
 * @param {(typeof SERIES_CON_UMBRAL)[number]} serie
 * @param {number|null} umbral
 * @returns {string}
 */
export function fraseDeUmbral(serie, umbral) {
  if (umbral === null) {
    return (
      `Sin umbral de ${serie.plural}: se muestran los conteos, pero no se evalúa ` +
      'ninguna alerta. El umbral se fija en Parámetros.'
    );
  }
  return `Se avisa a partir de ${contar(umbral, serie)} en un mismo día.`;
}

/** Lo que dice cada indicador accesorio cuando falta. */
const INDICADOR_QUE_FALTA = Object.freeze({
  REGISTRO_DE_USUARIOS: 'Las cuentas nuevas por día no están disponibles',
  FRECUENCIA_DE_REPORTES: 'La frecuencia de reportes no está disponible',
});

/** Por qué falta, sin códigos ni nombres de servicio. */
const CAUSA_DE_LA_FALTA = Object.freeze({
  SIN_PERMISO: 'tu rol no tiene permiso para consultar ese dato',
  NO_RESPONDE: 'el servicio que lo publica no respondió a tiempo',
  RESPUESTA_INCOMPLETA: 'el servicio que lo publica respondió con datos incompletos',
  RANGO_NO_ADMITIDO: 'el periodo elegido es más largo de lo que admite ese dato (hasta 366 días)',
});

/**
 * Un indicador que no se pudo leer, dicho para quien opera.
 *
 * @param {{indicador?: string, causa?: string}} falta un elemento de `noDisponibles`
 * @returns {string}
 */
export function fraseDeNoDisponible(falta) {
  const que = INDICADOR_QUE_FALTA[falta?.indicador] ?? 'Un indicador no está disponible';
  const porque = CAUSA_DE_LA_FALTA[falta?.causa] ?? 'no se pudo leer';
  return `${que}: ${porque}. El resto del tablero sí está al día.`;
}

/**
 * Los avisos publicados de una serie. Un servicio anterior a la 1.11.0 solo
 * manda `alertas` ya redactadas, todas de sanciones: se muestran tal cual en
 * esa serie antes que perderlas.
 */
function avisosDe(datos, serie) {
  if (Array.isArray(datos?.avisos)) {
    return datos.avisos
      .filter((a) => a?.tipo === serie.tipo && a.fecha && Number.isFinite(a.cantidad))
      .map((a) => ({
        fecha: a.fecha,
        texto: `${diaCompleto(a.fecha)}: ${contar(a.cantidad, serie)}`,
      }));
  }
  if (serie.tipo !== 'SANCIONES') {
    return [];
  }
  return (Array.isArray(datos?.alertas) ? datos.alertas : [])
    .filter((a) => typeof a === 'string' && a.trim())
    .map((texto) => ({ fecha: null, texto }));
}

/** El bloque de una serie: su estado, su umbral y los días que llegaron. */
function bloqueDeSerie(datos, serie) {
  const umbral = umbralDe(datos, serie);
  const sinDatos = serie.tipo === 'REPORTES' && Array.isArray(datos?.avisos) && !datos?.reportes;
  const avisos = avisosDe(datos, serie);
  const bloque = h('section', {
    clase: 'alertas-moderacion__serie',
    datos: { serie: serie.tipo },
    atributos: { 'aria-label': serie.titulo },
  });
  const cabeza = h('div', { clase: 'alertas-moderacion__cabeza' });
  cabeza.append(h('h4', { clase: 'panel-admin__grupo', texto: serie.titulo }));

  if (umbral === null && avisos.length === 0) {
    cabeza.append(distintivoDeEstado({ texto: 'Desactivada', tono: 'neutro' }));
    bloque.append(
      cabeza,
      h('p', {
        clase: 'panel-admin__explicacion',
        datos: { zona: 'sin-umbral' },
        texto: fraseDeUmbral(serie, null),
      }),
    );
    return bloque;
  }

  const explicacion = h('p', {
    clase: 'panel-admin__explicacion',
    datos: { zona: 'umbral' },
    texto: umbral === null ? 'Días que llegaron al umbral:' : fraseDeUmbral(serie, umbral),
  });
  if (sinDatos) {
    cabeza.append(distintivoDeEstado({ texto: 'Sin datos', tono: 'advertencia' }));
    bloque.append(
      cabeza,
      explicacion,
      h('p', {
        clase: 'panel-admin__explicacion',
        datos: { zona: 'sin-datos' },
        texto: 'No se pudo evaluar: la frecuencia de reportes no está disponible ahora.',
      }),
    );
    return bloque;
  }
  if (avisos.length === 0) {
    cabeza.append(distintivoDeEstado({ texto: 'Sin alertas', tono: 'exito' }));
    bloque.append(
      cabeza,
      explicacion,
      h('p', {
        clase: 'panel-admin__explicacion',
        datos: { zona: 'sin-alertas' },
        texto: `Ningún día llegó a ${contar(umbral, serie)}.`,
      }),
    );
    return bloque;
  }
  cabeza.append(
    distintivoDeEstado({
      texto: `${numero(avisos.length)} ${avisos.length === 1 ? 'alerta' : 'alertas'}`,
      tono: 'error',
    }),
  );
  bloque.append(
    cabeza,
    explicacion,
    h('ul', {
      clase: 'pila pila--ajustada',
      datos: { zona: 'avisos' },
      hijos: avisos.map((aviso) =>
        h('li', {
          datos: aviso.fecha ? { fecha: aviso.fecha } : {},
          hijos: [distintivo('Alerta', 'reportado'), ` ${aviso.texto}`],
        }),
      ),
    }),
  );
  return bloque;
}

/**
 * Las alertas de moderación tal como las publica el servicio de métricas, una
 * serie debajo de otra.
 *
 * @param {{umbralSancionesPorDia?: number|null, umbralReportesPorDia?: number|null,
 *          avisos?: Array<{tipo: string, fecha: string, cantidad: number, umbral: number}>,
 *          alertas?: string[], reportes?: object|null}} datos el tablero de `/moderacion`
 * @returns {HTMLElement[]}
 */
export function pintarAlertasDeModeracion(datos) {
  return [
    h('div', {
      clase: 'alertas-moderacion',
      datos: { zona: 'alertas' },
      hijos: SERIES_CON_UMBRAL.map((serie) => bloqueDeSerie(datos, serie)),
    }),
  ];
}
