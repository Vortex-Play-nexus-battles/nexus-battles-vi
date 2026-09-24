/**
 * Heroes: retrato con marco de rareza y carta de coleccion.
 *
 * ## Por que existe
 *
 * `.marco-heroe` esta en el kit desde que se derivo de las maquetas de Figma,
 * con sus cuatro bordes de rareza y su insignia de nivel, y **ninguna vista lo
 * usaba**. El heroe —la pieza central de un juego de heroes— se pintaba en
 * todas partes como una linea de texto: «Arquero del Norte (nivel 7)».
 *
 * Aqui no se inventa CSS nuevo: se construye el marcado que ese CSS ya espera.
 *
 * ## La rareza no se comunica solo con color
 *
 * El borde de color es el adorno; el nombre de la rareza va SIEMPRE en el
 * texto, porque un borde dorado y uno morado son el mismo borde para quien no
 * distingue esos tonos. Misma regla que la barra de vida con sus umbrales.
 */

import { h, clases } from '../dom.js';
import { claseDeMarco, distintivoDeRareza } from '../distintivo.js';

/**
 * Retrato circular con el marco que le toca a su rareza y la insignia de nivel.
 *
 * @param {object} heroe
 * @param {string} heroe.nombre
 * @param {number} [heroe.nivel]
 * @param {string} [heroe.rareza] COMUN · RARA · EPICA · LEGENDARIA
 * @param {string} [heroe.imagen] URL del retrato
 * @param {{conNombre?: boolean}} [opciones]
 * @returns {HTMLElement}
 */
export function retratoDeHeroe(heroe, { conNombre = true } = {}) {
  const { nombre, nivel, rareza, imagen } = heroe ?? {};
  const etiquetaRareza = rareza ? String(rareza).toLowerCase() : null;

  const retrato = h('div', { clase: 'marco-heroe__retrato' });

  if (imagen) {
    retrato.append(
      h('img', {
        clase: 'marco-heroe__imagen',
        atributos: {
          src: imagen,
          // El nombre ya va en el texto de al lado; repetirlo en el alt hace
          // que el lector lo diga dos veces.
          alt: '',
          loading: 'lazy',
        },
      }),
    );
  } else {
    // Sin imagen no se deja el hueco: la inicial del heroe es mejor marcador
    // de posicion que un circulo vacio, y ademas distingue a unos de otros.
    retrato.append(
      h('span', {
        clase: 'marco-heroe__inicial',
        texto: (nombre ?? '?').trim().charAt(0).toUpperCase() || '?',
        atributos: { 'aria-hidden': 'true' },
      }),
    );
  }

  if (Number.isFinite(nivel)) {
    retrato.append(h('span', { clase: 'marco-heroe__nivel', texto: String(nivel) }));
  }

  return h('div', {
    clase: claseDeMarco(rareza),
    datos: etiquetaRareza ? { rareza: etiquetaRareza } : {},
    atributos: {
      // Un `div` sin rol no admite `aria-label` (axe: aria-prohibited-attr,
      // serio). El marco es una imagen compuesta: retrato, anillo y nivel.
      role: 'img',
      // El nombre accesible lo dice todo de una vez, para que el lector no
      // tenga que juntar tres nodos sueltos.
      'aria-label': [
        nombre ?? 'Heroe',
        Number.isFinite(nivel) ? `nivel ${nivel}` : null,
        etiquetaRareza,
      ]
        .filter(Boolean)
        .join(', '),
    },
    hijos: [retrato, conNombre ? h('span', { clase: 'marco-heroe__nombre', texto: nombre }) : null],
  });
}

/**
 * Carta de coleccion: retrato, rareza escrita y las estadisticas que traiga.
 *
 * Las estadisticas se pintan tal como llegan. Sus valores son inalterables por
 * decision del Project Charter, asi que aqui no se calcula ni se redondea
 * nada: se muestra.
 *
 * @param {object} heroe
 * @param {string} heroe.nombre
 * @param {number} [heroe.nivel]
 * @param {string} [heroe.rareza]
 * @param {string} [heroe.imagen]
 * @param {Array<{etiqueta: string, valor: string|number}>} [heroe.estadisticas]
 * @param {{alPulsar?: (heroe: object) => void, seleccionada?: boolean}} [opciones]
 * @returns {HTMLElement}
 */
export function tarjetaDeHeroe(heroe, { alPulsar, seleccionada = false } = {}) {
  const { nombre, rareza, estadisticas } = heroe ?? {};

  const cuerpo = [
    retratoDeHeroe(heroe, { conNombre: false }),
    h('div', {
      clase: 'tarjeta-heroe__identidad',
      hijos: [
        h('p', { clase: 't-subtitulo', texto: nombre }),
        // La rareza, escrita. Ver la cabecera del archivo.
        distintivoDeRareza(rareza),
      ],
    }),
  ];

  if (Array.isArray(estadisticas) && estadisticas.length > 0) {
    cuerpo.push(
      h('dl', {
        clase: 'tarjeta-heroe__estadisticas',
        hijos: estadisticas.flatMap(({ etiqueta, valor }) => [
          h('dt', { clase: 't-meta', texto: etiqueta }),
          h('dd', { clase: 'tarjeta-heroe__cifra', texto: String(valor) }),
        ]),
      }),
    );
  }

  // Si se puede pulsar, es un boton de verdad: se llega con Tab, responde a
  // Enter y a espacio, y el lector lo anuncia como control. Un `div` con
  // `onclick` no hace ninguna de las tres cosas.
  if (typeof alPulsar !== 'function') {
    return h('article', {
      clase: clases('tarjeta', 'tarjeta-heroe', seleccionada && 'tarjeta-heroe--seleccionada'),
      hijos: cuerpo,
    });
  }

  const boton = h('button', {
    clase: clases('tarjeta', 'tarjeta-heroe', 'tarjeta--pulsable'),
    atributos: { type: 'button', 'aria-pressed': seleccionada ? 'true' : 'false' },
    hijos: cuerpo,
  });
  boton.addEventListener('click', () => alPulsar(heroe));
  return boton;
}
