/**
 * Pestañas accesibles (`.pestanas` en el kit, que hasta ahora ninguna vista
 * usaba).
 *
 * Sigue el patrón ARIA de tabs: `role="tablist"` / `tab` / `tabpanel`,
 * navegación con flechas, Inicio y Fin, y un solo tabulador para entrar y
 * salir del grupo. Sin eso, unas «pestañas» son botones que cambian cosas sin
 * avisar a quien no las ve.
 *
 * La pestaña activa se refleja en el hash de la URL para que se pueda
 * enlazar y para que recargar no devuelva siempre a la primera.
 */

import { h } from './dom.js';

/**
 * @param {ParentNode} raiz contenedor donde se monta
 * @param {Array<{id: string, etiqueta: string, panel: HTMLElement}>} pestanas
 * @param {{activa?: string|null, alCambiar?: (id: string) => void, hash?: boolean}} [opciones]
 * @returns {{mostrar: (id: string) => void, activa: () => string}}
 */
export function montarPestanas(
  raiz,
  pestanas,
  { activa = null, alCambiar = null, hash = true } = {},
) {
  const lista = h('div', { clase: 'pestanas', atributos: { role: 'tablist' } });
  const botones = new Map();

  const desdeHash = hash ? window.location.hash.replace('#', '') : '';
  let actual = pestanas.some((p) => p.id === desdeHash)
    ? desdeHash
    : (activa ?? pestanas[0]?.id ?? '');

  for (const pestana of pestanas) {
    const boton = h('button', {
      clase: 'pestana',
      atributos: { type: 'button', role: 'tab', id: `pestana-${pestana.id}` },
      datos: { pestana: pestana.id },
      hijos: [
        h('span', { clase: 'pestana__cara', texto: pestana.etiqueta }),
        h('span', { clase: 'pestana__linea' }),
      ],
    });
    boton.addEventListener('click', () => mostrar(pestana.id));
    botones.set(pestana.id, boton);
    lista.append(boton);

    pestana.panel.setAttribute('role', 'tabpanel');
    pestana.panel.setAttribute('aria-labelledby', `pestana-${pestana.id}`);
    pestana.panel.dataset.panel = pestana.id;
  }

  lista.addEventListener('keydown', (evento) => {
    const orden = pestanas.map((p) => p.id);
    const desde = orden.indexOf(actual);
    let destino = null;
    if (evento.key === 'ArrowRight') {
      destino = orden[(desde + 1) % orden.length];
    } else if (evento.key === 'ArrowLeft') {
      destino = orden[(desde - 1 + orden.length) % orden.length];
    } else if (evento.key === 'Home') {
      destino = orden[0];
    } else if (evento.key === 'End') {
      destino = orden[orden.length - 1];
    }
    if (destino) {
      evento.preventDefault();
      mostrar(destino);
      botones.get(destino).focus();
    }
  });

  /** @param {string} id */
  function mostrar(id) {
    actual = id;
    for (const pestana of pestanas) {
      const seleccionada = pestana.id === id;
      const boton = botones.get(pestana.id);
      boton.setAttribute('aria-selected', String(seleccionada));
      // Un solo tabulador para el grupo: dentro se navega con flechas.
      boton.setAttribute('tabindex', seleccionada ? '0' : '-1');
      pestana.panel.hidden = !seleccionada;
    }
    if (hash) {
      // `replaceState` y no `location.hash`: cambiar de pestaña no debe
      // llenar el historial del navegador.
      window.history.replaceState(null, '', `#${id}`);
    }
    if (alCambiar) {
      alCambiar(id);
    }
  }

  raiz.append(lista);
  for (const pestana of pestanas) {
    raiz.append(pestana.panel);
  }
  mostrar(actual);

  return { mostrar, activa: () => actual };
}
