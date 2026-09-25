/**
 * Estrellas de calificación — UXC-3 (RatingStars).
 *
 * §7.1 del documento pide, en el detalle de cada producto, la «calificación
 * promedio en cinco estrellas» y que cada jugador pueda calificar una sola
 * vez (RF-COM-002). Hasta aquí las estrellas solo existían dentro de la vista
 * aparte de comentarios, dibujadas a mano; la ficha del inventario, la tienda
 * y la portada no tenían cómo enseñarlas.
 *
 * Dos piezas:
 *
 *   - `estrellasDeCalificacion(valor)`: las cinco estrellas de solo lectura,
 *     con la cifra al lado. Las estrellas redondean («4,3» pinta cuatro); la
 *     precisión la da la cifra, que por eso no es opcional por defecto.
 *   - `selectorDeEstrellas()`: el control para calificar, cinco radios de
 *     verdad (flechas del teclado, un nombre por opción) y un «Sin calificar»
 *     para desmarcar, que un grupo de radios no permite por sí solo.
 *
 * ## No solo color
 *
 * La estrella llena se distingue por color, así que el grupo entero lleva un
 * nombre accesible («4,3 de 5 estrellas») y la cifra se escribe al lado. El
 * dibujo es un refuerzo, nunca el único canal (XAG 102).
 *
 * @module comun/ui/comunidad/estrellas
 */

import { clases, h } from '../dom.js';
import { icono, rutaDelSprite } from '../icono.js';

/** El máximo de la escala, fijado por §7.1. */
export const MAXIMO_ESTRELLAS = 5;

const FORMATO = new Intl.NumberFormat('es-CO', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 1,
});

let secuencia = 0;

/**
 * Una calificación escrita como se lee: «4», «4,3». `null` si no es un número.
 *
 * @param {number|null|undefined} valor
 * @returns {string|null}
 */
export function cifraDeCalificacion(valor) {
  return Number.isFinite(valor) ? FORMATO.format(valor) : null;
}

/**
 * Las cinco estrellas dibujadas, de las que `llenas` van en el color de la
 * calificación.
 *
 * @param {number} llenas
 * @returns {HTMLElement}
 */
function listaDeEstrellas(llenas) {
  const lista = h('span', { clase: 'estrellas__lista', atributos: { 'aria-hidden': 'true' } });
  for (let indice = 0; indice < MAXIMO_ESTRELLAS; indice += 1) {
    // Llena o hueca por la FORMA (dos simbolos), y ademas por el color.
    lista.append(
      icono(indice < llenas ? 'estrella-llena' : 'estrella', {
        clase: clases('estrellas__estrella', indice < llenas && 'estrellas__estrella--llena'),
        etiqueta: null,
      }),
    );
  }
  return lista;
}

/**
 * Llena o vacía una estrella ya pintada: la clase y el símbolo.
 *
 * @param {SVGElement} estrella
 * @param {boolean} llena
 */
function rellenar(estrella, llena) {
  estrella.classList.toggle('estrellas__estrella--llena', llena);
  estrella
    .querySelector('use')
    ?.setAttribute('href', `${rutaDelSprite()}#${llena ? 'estrella-llena' : 'estrella'}`);
}

/**
 * Estrellas de solo lectura.
 *
 * @param {number} valor de 1 a 5 (se acota)
 * @param {{conCifra?: boolean, clase?: string|null, decorativas?: boolean}} [opciones]
 *   `decorativas`: cinco estrellas vacías que no dicen nada (un producto sin
 *   valoraciones). Van ocultas al lector de pantalla: anunciarlas como «0 de 5
 *   estrellas» afirmaría una nota que nadie puso.
 * @returns {HTMLElement} `span.estrellas[role=img]`
 */
export function estrellasDeCalificacion(
  valor,
  { conCifra = true, clase = null, decorativas = false } = {},
) {
  if (decorativas) {
    return h('span', {
      clase: clases('estrellas', clase),
      atributos: { 'aria-hidden': 'true' },
      hijos: [listaDeEstrellas(0)],
    });
  }
  const acotado = Number.isFinite(valor) ? Math.min(Math.max(valor, 0), MAXIMO_ESTRELLAS) : 0;
  const cifra = cifraDeCalificacion(acotado);
  return h('span', {
    clase: clases('estrellas', clase),
    datos: { valor: String(acotado) },
    atributos: {
      role: 'img',
      'aria-label': `${cifra} de ${MAXIMO_ESTRELLAS} estrellas`,
    },
    hijos: [
      listaDeEstrellas(Math.round(acotado)),
      conCifra
        ? h('span', {
            clase: 'estrellas__detalle',
            texto: cifra,
            atributos: { 'aria-hidden': 'true' },
          })
        : null,
    ],
  });
}

/**
 * El control para calificar de 1 a 5, opcional.
 *
 * Cinco `input[type=radio]` escondidos a la vista pero no al teclado: cada uno
 * va dentro de su `label` con la estrella dibujada y su nombre («3
 * estrellas»). La estrella que sigue a la entrada enfocada lleva el anillo de
 * foco del kit (`.estrellas--editable .estrellas__entrada:focus-visible +
 * .estrellas__estrella`).
 *
 * @param {{leyenda?: string, ayuda?: string|null, alCambiar?: (valor: number|null) => void}} [opciones]
 * @returns {{elemento: HTMLFieldSetElement, valor: () => number|null,
 *   limpiar: () => void, deshabilitar: (motivo: string) => void}}
 */
export function selectorDeEstrellas({
  leyenda = 'Tu calificación (opcional)',
  ayuda = null,
  alCambiar = () => {},
} = {}) {
  secuencia += 1;
  const nombre = `estrellas-${secuencia}`;
  const idAyuda = `${nombre}-ayuda`;

  const entradas = [];
  const opciones = h('span', { clase: 'estrellas__lista selector-estrellas__opciones' });
  for (let valor = 1; valor <= MAXIMO_ESTRELLAS; valor += 1) {
    const entrada = h('input', {
      clase: 'estrellas__entrada solo-lectores',
      atributos: { type: 'radio', name: nombre, value: String(valor) },
    });
    entradas.push(entrada);
    opciones.append(
      h('label', {
        clase: 'selector-estrellas__opcion',
        hijos: [
          entrada,
          icono('estrella', { clase: 'estrellas__estrella', etiqueta: null }),
          h('span', {
            clase: 'solo-lectores',
            texto: valor === 1 ? '1 estrella' : `${valor} estrellas`,
          }),
        ],
      }),
    );
  }

  const cifra = h('span', {
    clase: 'estrellas__detalle selector-estrellas__cifra',
    texto: 'Sin calificar',
    atributos: { 'aria-live': 'polite' },
  });

  const quitar = h('button', {
    clase: 'boton boton--secundario boton--pequeno selector-estrellas__quitar',
    texto: 'Sin calificar',
    datos: { accion: 'quitar-calificacion' },
    atributos: { type: 'button' },
  });
  quitar.hidden = true;

  const nota = h('p', {
    clase: 'campo__pista',
    texto: ayuda ?? 'Solo puedes calificar un producto una vez; comentar, las veces que quieras.',
    atributos: { id: idAyuda },
  });

  const elemento = h('fieldset', {
    // `estrellas--editable` sin `estrellas`: la primera trae el anillo de foco
    // y el cursor; la segunda es una fila en linea y aplastaria la leyenda.
    clase: 'selector-estrellas estrellas--editable',
    atributos: { 'aria-describedby': idAyuda },
    hijos: [
      h('legend', { clase: 'campo__etiqueta', texto: leyenda }),
      h('div', { clase: 'selector-estrellas__fila', hijos: [opciones, cifra, quitar] }),
      nota,
    ],
  });

  /** @returns {number|null} */
  const valor = () => {
    const marcada = entradas.find((entrada) => entrada.checked);
    return marcada ? Number(marcada.value) : null;
  };

  const pintar = () => {
    const actual = valor();
    opciones.querySelectorAll('.estrellas__estrella').forEach((estrella, indice) => {
      rellenar(estrella, actual !== null && indice < actual);
    });
    cifra.textContent =
      actual === null ? 'Sin calificar' : `${actual} de ${MAXIMO_ESTRELLAS} estrellas`;
    quitar.hidden = actual === null;
  };

  const limpiar = () => {
    for (const entrada of entradas) {
      entrada.checked = false;
    }
    pintar();
  };

  for (const entrada of entradas) {
    entrada.addEventListener('change', () => {
      pintar();
      alCambiar(valor());
    });
  }
  quitar.addEventListener('click', () => {
    limpiar();
    alCambiar(null);
    entradas[0].focus();
  });

  return {
    elemento,
    valor,
    limpiar,
    /**
     * Deja el control a la vista pero sin uso, con el porqué debajo (por
     * ejemplo: ya calificaste este producto).
     *
     * @param {string} motivo
     */
    deshabilitar(motivo) {
      limpiar();
      elemento.disabled = true;
      elemento.dataset.estado = 'deshabilitado';
      nota.textContent = motivo;
    },
  };
}
