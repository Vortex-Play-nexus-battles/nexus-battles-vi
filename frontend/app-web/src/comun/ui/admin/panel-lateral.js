/**
 * Panel lateral de la consola — SA-2.
 *
 * La ficha rápida de una fila (una cuenta, una sanción, un servicio) sin
 * salir de la lista: se abre pegado al borde derecho y se cierra con Escape,
 * con la equis o pulsando fuera, y el foco vuelve a la fila desde la que se
 * abrió. Es el diálogo del kit (`abrirDialogo`) en su variante lateral, así
 * que hereda la trampa de foco, el bloqueo del desplazamiento y el apilado
 * con otros modales.
 *
 * @module comun/ui/admin/panel-lateral
 */

import { abrirDialogo } from '../dialogo.js';
import { h } from '../dom.js';

/**
 * @param {object} opciones
 * @param {string} opciones.titulo
 * @param {string|null} [opciones.subtitulo]
 * @param {Node|Node[]} opciones.contenido
 * @param {Node[]} [opciones.acciones] botones del pie
 * @param {(() => void)|null} [opciones.alCerrar]
 * @returns {{elemento: HTMLElement, cerrar: () => void}}
 */
export function abrirPanelLateral({
  titulo,
  subtitulo = null,
  contenido,
  acciones = [],
  alCerrar = null,
}) {
  const cuerpo = h('div', { clase: 'panel-lateral__cuerpo' });
  if (subtitulo) {
    cuerpo.append(h('p', { clase: 'panel-lateral__subtitulo', texto: subtitulo }));
  }
  cuerpo.append(...(Array.isArray(contenido) ? contenido : [contenido]));
  return abrirDialogo({ titulo, cuerpo, acciones, alCerrar, variante: 'lateral' });
}

/**
 * Pares «etiqueta: valor» de una ficha, como lista de definiciones. Un valor
 * vacío se dice («Sin dato»), no se esconde: también es información.
 *
 * @param {Array<[string, string|number|Node|null|undefined]>} pares
 * @returns {HTMLElement}
 */
export function listaDeDatos(pares) {
  const lista = h('dl', { clase: 'lista-datos-admin' });
  for (const [etiqueta, valor] of pares) {
    const vacio = valor === null || valor === undefined || valor === '';
    lista.append(
      h('div', {
        clase: 'lista-datos-admin__par',
        hijos: [
          h('dt', { texto: etiqueta }),
          vacio
            ? h('dd', { clase: 'lista-datos-admin__sin-dato', texto: 'Sin dato' })
            : h('dd', { hijos: [valor instanceof globalThis.Node ? valor : String(valor)] }),
        ],
      }),
    );
  }
  return lista;
}
