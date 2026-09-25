/**
 * Un comentario del hilo de un producto — UXC-3 (CommentCard, CommentAttachment).
 *
 * RN-CMT-001 fija lo que lleva: apodo, estrellas si calificó, fecha, texto e
 * imágenes. Aquí se añade lo que el jugador necesita para no perderse:
 *
 *   - quién lo escribió, también cuando fue él mismo («Tú»);
 *   - qué puede hacer con él: retirar el suyo (HU-COM-004) o reportar el de
 *     otro (RF-COM-006). Las dos acciones son excluyentes a propósito: un
 *     reporte a uno mismo no significa nada, y «Eliminar» sobre uno ajeno
 *     sería una promesa que el servicio contesta con 403;
 *   - en qué estado quedó cuando él lo reportó: «En revisión», porque el
 *     primer reporte lo saca del hilo público hasta que un moderador decide.
 *
 * ## Las imágenes
 *
 * `imagenes` son **nombres de archivo** (comentarios.yaml,
 * `PublicacionComentarioRequest.imagenes`): el servicio no guarda la imagen,
 * así que no hay miniatura que enseñar. El adjunto se pinta con su símbolo y
 * su nombre, y se dice que la vista previa no está disponible. Enseñar un
 * recuadro gris como si fuera la imagen sería fingir un dato.
 *
 * Todo el texto entra por `textContent` (`h()`): el comentario lo escribe
 * una persona.
 *
 * @module comun/ui/comunidad/comentario
 */

import { clases, h } from '../dom.js';
import { fechaHora } from '../formato.js';
import { icono } from '../icono.js';
import { estrellasDeCalificacion } from './estrellas.js';

/** Estados que la vista le pone a un comentario tras una acción de quien mira. */
export const ESTADO_LOCAL = Object.freeze({
  /** Quien mira lo reportó: el servicio lo pasó a revisión. */
  REPORTADO: 'REPORTADO',
});

/**
 * La inicial del apodo, para el círculo del autor. Sin avatar en el
 * contrato, la inicial distingue a unos de otros mejor que un círculo vacío.
 *
 * @param {string|null|undefined} apodo
 * @returns {string}
 */
export function inicialDe(apodo) {
  const limpio = String(apodo ?? '').trim();
  return limpio ? limpio.charAt(0).toLocaleUpperCase('es-CO') : '?';
}

/**
 * Los adjuntos de un comentario.
 *
 * @param {string[]|null|undefined} nombres
 * @returns {HTMLElement|null} `null` si no hay ninguno
 */
export function adjuntosDeComentario(nombres) {
  const lista = (Array.isArray(nombres) ? nombres : []).filter(
    (nombre) => typeof nombre === 'string' && nombre.trim() !== '',
  );
  if (lista.length === 0) {
    return null;
  }
  return h('div', {
    clase: 'comentario__adjuntos',
    hijos: [
      h('ul', {
        clase: 'comentario__adjuntos-lista',
        atributos: {
          'aria-label': lista.length === 1 ? 'Imagen adjunta' : `${lista.length} imágenes adjuntas`,
        },
        hijos: lista.map((nombre) =>
          h('li', {
            clase: 'comentario__adjunto',
            datos: { nombre },
            hijos: [
              icono('imagen', { clase: 'icono comentario__adjunto-icono', etiqueta: null }),
              h('span', {
                clase: 'comentario__adjunto-nombre',
                texto: nombre,
                atributos: { title: nombre },
              }),
            ],
          }),
        ),
      }),
      h('p', {
        clase: 'comentario__adjuntos-nota',
        texto: 'La vista previa de las imágenes aún no está disponible.',
      }),
    ],
  });
}

/**
 * Tarjeta de un comentario.
 *
 * @param {object} comentario `ComentarioResponse` del contrato
 * @param {{yo?: string|null, estadoLocal?: string|null,
 *          alEliminar?: ((comentario: object, articulo: HTMLElement) => void)|null,
 *          alReportar?: ((comentario: object, articulo: HTMLElement) => void)|null}} [opciones]
 *   `yo`: el `uid` de quien mira; sin él no se ofrece ninguna acción.
 * @returns {HTMLElement} `article.comentario`
 */
export function tarjetaDeComentario(
  comentario,
  { yo = null, estadoLocal = null, alEliminar = null, alReportar = null } = {},
) {
  const apodo = typeof comentario?.apodoAutor === 'string' ? comentario.apodoAutor : '';
  const esMio = Boolean(yo) && comentario?.autorId === yo;

  const autor = h('p', {
    clase: 'comentario__autor',
    hijos: [
      h('span', { clase: 'comentario__apodo', texto: apodo || 'Jugador' }),
      esMio
        ? h('span', { clase: 'distintivo distintivo--equipado comentario__propio', texto: 'Tú' })
        : null,
    ],
  });

  const fecha = h('time', {
    clase: 'comentario__fecha',
    texto: fechaHora(comentario?.fechaPublicacion),
    atributos: comentario?.fechaPublicacion ? { datetime: comentario.fechaPublicacion } : {},
  });

  const cabecera = h('header', {
    clase: 'comentario__cabecera',
    hijos: [
      h('span', {
        clase: 'comentario__avatar',
        texto: inicialDe(apodo),
        atributos: { 'aria-hidden': 'true' },
      }),
      h('div', {
        clase: 'comentario__firma',
        hijos: [
          autor,
          h('div', {
            clase: 'comentario__meta',
            hijos: [
              Number.isInteger(comentario?.estrellas)
                ? estrellasDeCalificacion(comentario.estrellas, { clase: 'comentario__estrellas' })
                : null,
              fecha,
            ],
          }),
        ],
      }),
    ],
  });

  const articulo = h('article', {
    clase: clases('comentario', esMio && 'comentario--propio'),
    datos: {
      comentarioId: comentario?.id ?? '',
      ...(estadoLocal ? { estado: estadoLocal } : {}),
    },
    // Enfocable por programa (no por Tab): tras «Ver más» o un reporte, el
    // foco se lleva a la tarjeta que cambió en vez de perderse en `body`.
    atributos: { tabindex: '-1', 'aria-label': `Opinión de ${apodo || 'un jugador'}` },
    hijos: [
      cabecera,
      h('p', { clase: 'comentario__texto', texto: comentario?.texto ?? '' }),
      adjuntosDeComentario(comentario?.imagenes),
    ],
  });

  const pie = pieDelComentario({
    comentario,
    apodo,
    esMio,
    yo,
    estadoLocal,
    alEliminar,
    alReportar,
  });
  if (pie) {
    articulo.append(pie);
  }
  return articulo;
}

/**
 * La fila de acciones, o el estado que la sustituye.
 *
 * @returns {HTMLElement|null}
 */
function pieDelComentario({ comentario, apodo, esMio, yo, estadoLocal, alEliminar, alReportar }) {
  if (estadoLocal === ESTADO_LOCAL.REPORTADO) {
    return h('p', {
      clase: 'comentario__estado',
      atributos: { role: 'status' },
      hijos: [
        h('span', {
          clase: 'distintivo distintivo--reportado',
          hijos: [
            icono('bandera', { clase: 'icono comentario__estado-icono', etiqueta: null }),
            h('span', { texto: 'Reportado' }),
          ],
        }),
        h('span', {
          clase: 'comentario__estado-texto',
          texto: 'Un moderador lo revisará; mientras tanto no se muestra a nadie más.',
        }),
      ],
    });
  }
  if (!yo) {
    return null;
  }
  let accion = null;
  if (esMio && typeof alEliminar === 'function') {
    accion = h('button', {
      clase: 'boton boton--secundario boton--pequeno comentario__accion',
      datos: { accion: 'eliminar-comentario' },
      atributos: { type: 'button', 'aria-label': 'Eliminar tu comentario' },
      hijos: [
        icono('papelera', { clase: 'icono comentario__accion-icono', etiqueta: null }),
        h('span', { texto: 'Eliminar' }),
      ],
    });
    accion.addEventListener('click', () => alEliminar(comentario, accion.closest('.comentario')));
  } else if (!esMio && typeof alReportar === 'function') {
    accion = h('button', {
      clase: 'boton boton--secundario boton--pequeno comentario__accion',
      datos: { accion: 'reportar-comentario' },
      atributos: {
        type: 'button',
        'aria-label': `Reportar el comentario de ${apodo || 'este jugador'}`,
      },
      hijos: [
        icono('bandera', { clase: 'icono comentario__accion-icono', etiqueta: null }),
        h('span', { texto: 'Reportar' }),
      ],
    });
    accion.addEventListener('click', () => alReportar(comentario, accion.closest('.comentario')));
  }
  return accion ? h('footer', { clase: 'comentario__acciones', hijos: [accion] }) : null;
}
