/**
 * Tarjeta — la unidad de contenido del producto (`.tarjeta` en el kit).
 *
 * Una sala, una subasta, un heroe, un torneo y un comentario son cosas
 * distintas, pero se miran igual: un titulo, cuatro datos, un par de
 * distintivos y una accion. Hoy cada listado arma esa misma caja a mano y el
 * resultado es que la tarjeta de subasta y la de sala no se parecen aunque
 * estan a un clic de distancia.
 *
 * Este componente no sabe de subastas ni de salas: recibe titulo, datos,
 * distintivos y acciones. Las variantes de dominio (`tarjeta--subasta`,
 * `tarjeta--producto`) son solo una clase mas, ya definida en el kit.
 */

import { h } from './dom.js';

/**
 * @param {{titulo: string, subtitulo?: string|null, variante?: string|null,
 *          distintivos?: Array<Node>, datos?: Array<{etiqueta: string, valor: string}>,
 *          medios?: Node|null, cuerpo?: Node|null, acciones?: Array<Node>,
 *          href?: string|null, alAbrir?: Function|null,
 *          atributosDeDatos?: Record<string, string|number|boolean>}} opciones
 * @returns {HTMLElement} `<article class="tarjeta">`, o `<a>` si lleva `href`
 */
export function tarjeta({
  titulo,
  subtitulo = null,
  variante = null,
  distintivos = [],
  datos = [],
  medios = null,
  cuerpo = null,
  acciones = [],
  href = null,
  alAbrir = null,
  atributosDeDatos = {},
}) {
  const pulsable = Boolean(href || alAbrir);
  const clase = [
    'tarjeta',
    'pila',
    'pila--ajustada',
    variante ? `tarjeta--${variante}` : null,
    pulsable ? 'tarjeta--pulsable' : null,
  ]
    .filter(Boolean)
    .join(' ');

  const caja = href
    ? h('a', { clase, atributos: { href }, datos: atributosDeDatos })
    : h('article', { clase, datos: atributosDeDatos });

  if (medios) {
    caja.append(h('div', { clase: 'tarjeta__medios', hijos: [medios] }));
  }

  const cabeza = h('div', { clase: 'tarjeta__cabecera' });
  const textos = h('div', { clase: 'pila pila--ajustada' });
  textos.append(h('h3', { clase: 'tarjeta__titulo', texto: titulo }));
  if (subtitulo) {
    textos.append(h('p', { clase: 't-meta', texto: subtitulo }));
  }
  cabeza.append(textos);
  if (distintivos.length > 0) {
    cabeza.append(h('div', { clase: 'tarjeta__distintivos', hijos: distintivos }));
  }
  caja.append(cabeza);

  if (datos.length > 0) {
    // <dl>: son pares etiqueta/valor de verdad, y asi un lector de pantalla
    // lee «Puja actual, 120 creditos» en vez de dos textos sueltos.
    const lista = h('dl', { clase: 'tarjeta__datos' });
    for (const dato of datos) {
      // Cada par va en su propio `<div>` (HTML lo permite dentro de `<dl>`):
      // asi la rejilla coloca PARES, y la etiqueta nunca se separa de su
      // valor. Con dt/dd sueltos, tres pares en cuatro columnas ponian «200»
      // debajo de «Equipos» (UX-GAME-3).
      lista.append(
        h('div', {
          clase: 'tarjeta__dato',
          hijos: [
            h('dt', { clase: 't-meta', texto: dato.etiqueta }),
            h('dd', { texto: dato.valor }),
          ],
        }),
      );
    }
    caja.append(lista);
  }

  if (cuerpo) {
    caja.append(cuerpo);
  }

  if (acciones.length > 0) {
    caja.append(h('div', { clase: 'tarjeta__acciones', hijos: acciones }));
  }

  if (alAbrir && !href) {
    caja.setAttribute('tabindex', '0');
    caja.setAttribute('role', 'button');
    caja.addEventListener('click', alAbrir);
    // Con `role="button"` hay que responder al teclado: Enter y Espacio.
    caja.addEventListener('keydown', (evento) => {
      if (evento.key === 'Enter' || evento.key === ' ') {
        evento.preventDefault();
        alAbrir(evento);
      }
    });
  }

  return caja;
}

/**
 * Cifra destacada con su etiqueta (saldo, sanciones del mes, latencia media).
 *
 * `valor` acepta también un nodo, para cifras que se presentan con su propio
 * componente —créditos con su moneda, por ejemplo— en vez de como texto suelto.
 *
 * @param {{etiqueta: string, valor: string|Node, detalle?: string|null, tono?: string|null}} opciones
 * @returns {HTMLElement}
 */
export function tarjetaDeCifra({ etiqueta, valor, detalle = null, tono = null }) {
  const caja = h('article', {
    clase: 'metrica metrica--cifra',
    datos: tono ? { tono } : {},
  });
  const cifra =
    valor instanceof Node
      ? h('p', { clase: 'metrica__valor', hijos: [valor] })
      : h('p', { clase: 'metrica__valor', texto: valor });
  caja.append(h('p', { clase: 't-meta', texto: etiqueta }), cifra);
  if (detalle) {
    caja.append(h('p', { clase: 't-meta', texto: detalle }));
  }
  return caja;
}
