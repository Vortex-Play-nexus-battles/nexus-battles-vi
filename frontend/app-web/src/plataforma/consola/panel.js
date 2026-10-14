/**
 * El panel: la unidad de la consola de operación.
 *
 * ## Por qué cada panel es independiente
 *
 * La consola pregunta a doce servicios. Si un solo fallo pudiera dejarla en
 * blanco, sería inservible justo el día que hace falta: el día que algo está
 * caído. Cada panel consulta lo suyo, se pinta con lo que consiguió y, si no
 * consiguió nada, lo dice y ofrece reintentar solo esa parte.
 *
 * ## Lo que un panel nunca hace
 *
 * No rellena. No hay un cero donde no se pudo preguntar, ni un «0 registros»
 * cuando lo cierto es que el servicio no contestó. Y no habla como el código:
 * desde SA-2 (informe de revisión, puntos 2 a 15) no hay «Fuente: GET …», ni
 * códigos HTTP, ni etiquetas en mayúsculas de estado interno. Cuando todo va
 * bien el panel no lleva distintivo; cuando no, uno con palabras e icono
 * («Sin conexión», «Sin acceso») y el motivo en lenguaje de persona.
 *
 * @module plataforma/consola/panel
 */

import { RESULTADO } from './cliente-consola.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { estadoDeCarga, estadoVacio } from '../../comun/ui/estado-vista.js';
import { distintivoDeEstado } from '../../comun/ui/admin/estado.js';
import { tablaDeDatos } from '../../comun/ui/admin/tabla-de-datos.js';
import { numero } from '../../comun/ui/formato.js';

/**
 * El distintivo de cada desenlace que no son datos. Con datos, o sin
 * registros, no hay distintivo: el contenido ya lo dice.
 */
export const ESTADO_DEL_PANEL = Object.freeze({
  [RESULTADO.SIN_PERMISO]: { texto: 'Sin acceso', tono: 'bloqueado' },
  [RESULTADO.NO_IMPLEMENTADO]: { texto: 'No disponible', tono: 'neutro' },
  [RESULTADO.SERVICIO_DEGRADADO]: { texto: 'Sin conexión', tono: 'error' },
  [RESULTADO.NO_DISPONIBLE]: { texto: 'No disponible', tono: 'error' },
});

/** El título del aviso que sustituye a los datos. */
const TITULO_DEL_AVISO = Object.freeze({
  [RESULTADO.SIN_PERMISO]: 'Sin acceso',
  [RESULTADO.NO_IMPLEMENTADO]: 'Información no disponible',
  [RESULTADO.SERVICIO_DEGRADADO]: 'No pudimos cargar esta información',
  [RESULTADO.NO_DISPONIBLE]: 'No pudimos cargar esta información',
});

/**
 * Arma un panel vacío, con su encabezado y la zona donde irá el contenido.
 *
 * El ancho se declara al armarlo y no se deduce de lo que se pinte: un panel
 * con tabla ocupa la fila entera también mientras carga o si sale vacío, así
 * la página no salta cuando llegan los datos.
 *
 * @param {{id: string, titulo: string, descripcion?: string, acciones?: Node[],
 *          ancho?: 'completo'|null}} opciones
 * @returns {{elemento: HTMLElement, zona: HTMLElement, sello: HTMLElement}}
 */
export function panel({ id, titulo, descripcion = '', acciones = [], ancho = null }) {
  const elemento = h('section', {
    clase: 'tarjeta panel-admin',
    datos: { panel: id, ...(ancho ? { ancho } : {}) },
    atributos: { 'aria-labelledby': `panel-${id}-titulo` },
  });

  const encabezado = h('header', { clase: 'panel-admin__encabezado' });
  const textos = h('div', { clase: 'panel-admin__textos' });
  textos.append(
    h('h3', {
      clase: 'tarjeta__titulo',
      texto: titulo,
      atributos: { id: `panel-${id}-titulo` },
    }),
  );
  if (descripcion) {
    textos.append(h('p', { clase: 'panel-admin__descripcion', texto: descripcion }));
  }
  // El distintivo de estado: vacío mientras carga y mientras todo va bien.
  const sello = h('span', { clase: 'panel-admin__estado', datos: { estado: 'cargando' } });
  encabezado.append(textos, sello);
  if (acciones.length > 0) {
    encabezado.append(h('div', { clase: 'panel-admin__acciones', hijos: acciones }));
  }

  const zona = h('div', { clase: 'panel-admin__cuerpo', datos: { zona: 'cuerpo' } });
  zona.append(estadoDeCarga({ filas: 2, etiqueta: `Cargando ${titulo}` }));

  elemento.append(encabezado, zona);
  return { elemento, zona, sello };
}

/**
 * Pinta un desenlace en la zona del panel.
 *
 * @param {{zona: HTMLElement, sello: HTMLElement}} partes
 * @param {import('./cliente-consola.js').Desenlace} desenlace
 * @param {(datos: *) => HTMLElement|HTMLElement[]} pintarDatos
 * @param {{alReintentar?: (() => void)|null, vacio?: {titulo: string, detalle?: string|null}}} [opciones]
 */
export function pintarDesenlace(
  { zona, sello },
  desenlace,
  pintarDatos,
  { alReintentar, vacio } = {},
) {
  marcarSello(sello, desenlace.resultado);
  vaciar(zona);

  if (desenlace.resultado === RESULTADO.DATOS) {
    const pintado = pintarDatos(desenlace.datos);
    zona.append(...(Array.isArray(pintado) ? pintado : [pintado]));
    return zona;
  }
  if (desenlace.resultado === RESULTADO.VACIO) {
    zona.append(
      estadoVacio({
        titulo: vacio?.titulo ?? 'Sin registros por ahora',
        detalle: vacio?.detalle ?? null,
      }),
    );
    return zona;
  }

  zona.append(avisoDeEstado(desenlace, alReintentar));
  return zona;
}

/**
 * El bloque que sustituye a los datos cuando no se pudieron leer: qué pasó y
 * por qué, en lenguaje de persona, y «Reintentar» si reintentar sirve de algo.
 *
 * @param {import('./cliente-consola.js').Desenlace} desenlace
 * @param {(() => void)|null} alReintentar
 */
export function avisoDeEstado(desenlace, alReintentar = null) {
  const leve =
    desenlace.resultado === RESULTADO.SIN_PERMISO ||
    desenlace.resultado === RESULTADO.NO_IMPLEMENTADO ||
    desenlace.resultado === RESULTADO.VACIO;
  const caja = h('div', {
    clase: `aviso aviso--${leve ? 'info' : 'advertencia'}`,
    datos: { estado: desenlace.resultado },
    atributos: { role: 'status' },
  });
  // `.aviso` es una fila: [cuerpo][accion]. El título y el motivo van DENTRO
  // de `.aviso__cuerpo`, que es el que se estira.
  const cuerpo = h('div', { clase: 'aviso__cuerpo' });
  cuerpo.append(
    h('p', {
      clase: 'aviso__titulo',
      texto: TITULO_DEL_AVISO[desenlace.resultado] ?? 'No pudimos cargar esta información',
    }),
  );
  if (desenlace.motivo) {
    cuerpo.append(h('p', { texto: desenlace.motivo }));
  }
  caja.append(cuerpo);

  if (alReintentar && desenlace.resultado !== RESULTADO.SIN_PERMISO) {
    // Un 401 o un 403 no se arregla reintentando: no se ofrece el botón.
    const boton = h('button', {
      clase: 'boton boton--secundario',
      texto: 'Reintentar',
      atributos: { type: 'button' },
      datos: { accion: 'reintentar' },
    });
    boton.addEventListener('click', alReintentar);
    caja.append(boton);
  }
  return caja;
}

/**
 * Cifra grande de un indicador.
 *
 * `null` se pinta como un guion largo, nunca como cero: no saber cuántos hay y
 * saber que hay cero son dos respuestas distintas.
 *
 * @param {{etiqueta: string, valor: number|string|null, nota?: string}} opciones
 */
export function indicador({ etiqueta, valor, nota = '' }) {
  const sinDato = valor === null || valor === undefined || valor === '';
  const caja = h('div', {
    clase: 'indicador',
    datos: { indicador: etiqueta, ...(sinDato ? { sinDato: 'si' } : {}) },
  });
  const cifra = h('p', { clase: 'indicador__valor' });
  if (sinDato) {
    cifra.append(
      h('span', { texto: '—', atributos: { 'aria-hidden': 'true' } }),
      h('span', { clase: 'solo-lectores', texto: 'Sin dato' }),
    );
  } else {
    cifra.textContent = typeof valor === 'number' ? numero(valor) : String(valor);
  }
  caja.append(cifra, h('p', { clase: 'indicador__etiqueta', texto: etiqueta }));
  if (nota) {
    caja.append(h('p', { clase: 'indicador__nota', texto: nota }));
  }
  return caja;
}

/**
 * Tabla de solo lectura de un panel, sobre la tabla del kit de la consola
 * (`comun/ui/admin/tabla-de-datos.js`).
 *
 * Cada columna se da como texto (su título) o como objeto (`{titulo, tipo,
 * ordenable}`); cada fila, como lista de celdas en el orden de las columnas.
 * La primera columna identifica la fila.
 *
 * @param {{titulo?: string, columnas: Array<string|{titulo: string, tipo?: string, ordenable?: boolean}>,
 *          filas: Array<Array<string|number|Node|null>>, resumen?: string,
 *          vacio?: {titulo: string, detalle?: string|null}}} opciones
 */
export function tabla({ titulo = '', columnas, filas, resumen = '', vacio = null }) {
  const definidas = columnas.map((columna, indice) => {
    const base = typeof columna === 'string' ? { titulo: columna } : columna;
    return {
      ...base,
      clave: `c${indice}`,
      principal: indice === 0,
      // El orden local compara lo que se ve; una celda con un nodo, por su texto.
      ordenarPor: base.ordenable ? (fila) => textoDeOrden(fila[`c${indice}`]) : undefined,
    };
  });
  return tablaDeDatos({
    titulo: titulo || resumen || 'Tabla',
    columnas: definidas,
    filas: filas.map((celdas) =>
      Object.fromEntries(celdas.map((valor, indice) => [`c${indice}`, valor])),
    ),
    resumen: resumen || null,
    vacio,
  });
}

/** Con qué se ordena una celda: el número si lo es, el texto si es un nodo. */
function textoDeOrden(valor) {
  if (valor === null || valor === undefined) {
    return null;
  }
  if (typeof valor === 'number') {
    return valor;
  }
  if (valor instanceof globalThis.Node) {
    return valor.dataset?.orden ?? valor.textContent ?? null;
  }
  return String(valor);
}

/** @param {HTMLElement} sello @param {string} resultado */
function marcarSello(sello, resultado) {
  sello.dataset.estado = resultado;
  const estado = ESTADO_DEL_PANEL[resultado];
  sello.replaceChildren(...(estado ? [distintivoDeEstado(estado)] : []));
}
