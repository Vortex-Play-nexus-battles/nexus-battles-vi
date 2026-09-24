/**
 * El panel: la unidad de la consola de control integral.
 *
 * ## Por que cada panel es independiente
 *
 * La consola pregunta a doce servicios. Si un solo fallo pudiera dejarla en
 * blanco, seria inservible justo el dia que hace falta -- el dia que algo
 * esta caido. Cada panel consulta lo suyo, se pinta con lo que consiguio y,
 * si no consiguio nada, lo dice con todas las letras y ofrece reintentar solo
 * esa parte.
 *
 * ## Lo que un panel nunca hace
 *
 * No rellena. No hay un cero donde no se pudo preguntar, ni un "0 registros"
 * cuando lo cierto es "el servicio no contesto". Las cuatro etiquetas de
 * estado son literales y visibles: SIN DATOS, NO IMPLEMENTADO, SERVICIO
 * DEGRADADO y SIN PERMISO. Quien mire la pantalla sabe, sin preguntar a
 * nadie, si el numero que ve es real.
 *
 * @module plataforma/consola/panel
 */

import { RESULTADO } from './cliente-consola.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { estadoDeCarga } from '../../comun/ui/estado-vista.js';

/** Etiqueta visible de cada desenlace que no son datos. */
const ETIQUETA = Object.freeze({
  [RESULTADO.VACIO]: 'SIN REGISTROS',
  [RESULTADO.SIN_PERMISO]: 'SIN PERMISO',
  [RESULTADO.NO_IMPLEMENTADO]: 'NO IMPLEMENTADO',
  [RESULTADO.SERVICIO_DEGRADADO]: 'SERVICIO DEGRADADO',
  [RESULTADO.NO_DISPONIBLE]: 'NO DISPONIBLE',
});

/** Clase de color del distintivo, por desenlace. */
const TONO = Object.freeze({
  [RESULTADO.DATOS]: 'ok',
  [RESULTADO.VACIO]: 'neutro',
  [RESULTADO.SIN_PERMISO]: 'aviso',
  [RESULTADO.NO_IMPLEMENTADO]: 'neutro',
  [RESULTADO.SERVICIO_DEGRADADO]: 'malo',
  [RESULTADO.NO_DISPONIBLE]: 'malo',
});

/**
 * Arma un panel vacio, con su encabezado y la zona donde ira el contenido.
 *
 * @param {{id: string, titulo: string, descripcion?: string, fuente?: string}} opciones
 * @returns {{elemento: HTMLElement, zona: HTMLElement, sello: HTMLElement}}
 */
export function panel({ id, titulo, descripcion = '', fuente = '' }) {
  const elemento = h('section', {
    clase: 'tarjeta panel-admin',
    datos: { panel: id },
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
    textos.append(h('p', { clase: 't-meta', texto: descripcion }));
  }
  const sello = h('span', { clase: 'sello-estado', datos: { estado: 'cargando' }, texto: '...' });
  encabezado.append(textos, sello);

  const zona = h('div', { clase: 'panel-admin__cuerpo', datos: { zona: 'cuerpo' } });
  zona.append(estadoDeCarga({ filas: 2, etiqueta: `Cargando ${titulo}` }));

  elemento.append(encabezado, zona);
  if (fuente) {
    // De donde sale el dato. Es lo que permite que alguien lo verifique por su
    // cuenta en vez de creerse la pantalla.
    elemento.append(h('p', { clase: 'panel-admin__fuente t-meta', texto: `Fuente: ${fuente}` }));
  }
  return { elemento, zona, sello };
}

/**
 * Pinta un desenlace en la zona del panel.
 *
 * @param {{zona: HTMLElement, sello: HTMLElement}} partes
 * @param {import('./cliente-consola.js').Desenlace} desenlace
 * @param {(datos: *) => HTMLElement|HTMLElement[]} pintarDatos
 * @param {{alReintentar?: (() => void)|null}} [opciones]
 */
export function pintarDesenlace({ zona, sello }, desenlace, pintarDatos, { alReintentar } = {}) {
  marcarSello(sello, desenlace.resultado);
  vaciar(zona);

  if (desenlace.resultado === RESULTADO.DATOS) {
    const pintado = pintarDatos(desenlace.datos);
    zona.append(...(Array.isArray(pintado) ? pintado : [pintado]));
    return zona;
  }

  zona.append(avisoDeEstado(desenlace, alReintentar));
  return zona;
}

/**
 * El bloque que sustituye a los datos cuando no los hay.
 *
 * Dice tres cosas, en este orden: que paso (etiqueta), por que (motivo) y
 * de donde salia el dato (recurso). La tercera es la que convierte "no se ve"
 * en algo que alguien puede ir a arreglar.
 *
 * @param {import('./cliente-consola.js').Desenlace} desenlace
 * @param {(() => void)|null} alReintentar
 */
export function avisoDeEstado(desenlace, alReintentar = null) {
  const caja = h('div', {
    clase: `aviso aviso--${desenlace.resultado === RESULTADO.VACIO ? 'info' : 'advertencia'}`,
    datos: { estado: desenlace.resultado },
    atributos: { role: 'status' },
  });
  caja.append(
    h('p', {
      clase: 'aviso__titulo',
      texto: ETIQUETA[desenlace.resultado] ?? 'NO DISPONIBLE',
    }),
  );
  if (desenlace.motivo) {
    caja.append(h('p', { clase: 'aviso__cuerpo', texto: desenlace.motivo }));
  }
  if (desenlace.recurso) {
    caja.append(
      h('p', {
        clase: 'aviso__detalle',
        texto: `Consulta: GET /api/v1${desenlace.recurso}${
          desenlace.estado ? ` (HTTP ${desenlace.estado})` : ''
        }`,
      }),
    );
  }
  if (alReintentar && desenlace.resultado !== RESULTADO.SIN_PERMISO) {
    // Un 401 o un 403 no se arregla reintentando: no se ofrece el boton.
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
 * Bloque para un modulo que el backend todavia no tiene.
 *
 * No es lo mismo que un servicio caido y no se pinta igual. Se nombra la
 * historia de usuario pendiente para que la pantalla no parezca un fallo.
 *
 * @param {{titulo?: string, razon: string}} opciones
 */
export function moduloNoImplementado({ titulo = 'Módulo no implementado', razon }) {
  const caja = h('div', {
    clase: 'aviso aviso--info',
    datos: { estado: RESULTADO.NO_IMPLEMENTADO },
    atributos: { role: 'status' },
  });
  caja.append(
    h('p', { clase: 'aviso__titulo', texto: titulo }),
    h('p', { clase: 'aviso__cuerpo', texto: razon }),
  );
  return caja;
}

/**
 * Cifra grande de un indicador.
 *
 * `null` se pinta como un guion, nunca como cero: no saber cuantos hay y
 * saber que hay cero son dos respuestas distintas.
 *
 * @param {{etiqueta: string, valor: number|string|null, nota?: string}} opciones
 */
export function indicador({ etiqueta, valor, nota = '' }) {
  const caja = h('div', { clase: 'indicador', datos: { indicador: etiqueta } });
  caja.append(
    h('p', {
      clase: 'indicador__valor',
      texto: valor === null || valor === undefined ? '--' : String(valor),
    }),
    h('p', { clase: 'indicador__etiqueta', texto: etiqueta }),
  );
  if (nota) {
    caja.append(h('p', { clase: 'indicador__nota t-meta', texto: nota }));
  }
  return caja;
}

/**
 * Tabla simple de solo lectura.
 *
 * @param {{columnas: string[], filas: Array<Array<string|HTMLElement>>, resumen?: string}} opciones
 */
export function tabla({ columnas, filas, resumen = '' }) {
  const envoltorio = h('div', { clase: 'tabla-envoltorio' });
  const tablaHtml = h('table', { clase: 'tabla' });
  if (resumen) {
    tablaHtml.append(h('caption', { clase: 't-meta', texto: resumen }));
  }
  const cabeza = h('thead');
  const filaCabeza = h('tr', { clase: 'tabla__fila tabla__fila--encabezado' });
  for (const columna of columnas) {
    filaCabeza.append(
      h('th', { clase: 'tabla__celda', texto: columna, atributos: { scope: 'col' } }),
    );
  }
  cabeza.append(filaCabeza);

  const cuerpo = h('tbody');
  filas.forEach((fila, indice) => {
    const tr = h('tr', {
      clase: `tabla__fila${indice % 2 === 1 ? ' tabla__fila--alterna' : ''}`,
    });
    for (const celda of fila) {
      const td = h('td', { clase: 'tabla__celda' });
      if (celda instanceof globalThis.HTMLElement) {
        td.append(celda);
      } else {
        // `texto` y no `innerHTML`: estas filas llevan apodos y correos que
        // escribio gente de fuera.
        td.textContent = celda === null || celda === undefined ? '--' : String(celda);
      }
      tr.append(td);
    }
    cuerpo.append(tr);
  });

  tablaHtml.append(cabeza, cuerpo);
  envoltorio.append(tablaHtml);
  return envoltorio;
}

/** @param {HTMLElement} sello @param {string} resultado */
function marcarSello(sello, resultado) {
  sello.dataset.estado = TONO[resultado] ?? 'neutro';
  sello.textContent = resultado === RESULTADO.DATOS ? 'EN LINEA' : (ETIQUETA[resultado] ?? '--');
}

export { ETIQUETA as ETIQUETA_DE_ESTADO };
