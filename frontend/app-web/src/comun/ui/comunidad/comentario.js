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
 *   - en qué estado quedó cuando él lo reportó: «Reportado». Desde
 *     comentarios.yaml 1.8.0 un reporte lo pone en la cola de moderación sin
 *     sacarlo del hilo: ocultarlo es decisión de un moderador;
 *   - si un moderador cambió su texto (`editado`, comentarios.yaml 1.5.0):
 *     quien lo lee tiene que saber que no es exactamente lo que escribió su
 *     autor (7.3.3, «Editar ... con registro de la edición»).
 *
 * ## Las imágenes
 *
 * Desde la 1.4.0 `imagenes` son los `id` de imágenes que el servicio guarda
 * (`POST /comentarios/imagenes`) y sirve en `GET /comentarios/imagenes/{id}`:
 * se pintan como imágenes de verdad, acotadas a una miniatura, con carga
 * perezosa y un enlace a su tamaño completo. Las de un comentario publicado
 * son públicas, así que basta un `<img>` normal (no hace falta token).
 *
 * Los comentarios anteriores a la 1.4.0 pueden traer todavía **nombres de
 * archivo** sin imagen detrás. Esos se pintan como antes —su símbolo y su
 * nombre— y se dice por qué no hay miniatura: enseñar un recuadro gris como
 * si fuera la imagen sería fingir un dato. Si una imagen real deja de estar
 * (su `<img>` falla), el hueco dice «Imagen no disponible» en vez de quedarse
 * roto.
 *
 * La dirección de cada imagen la pone quien pinta la tarjeta (`urlDeImagen`):
 * este módulo es de `comun/` y no conoce las rutas de ningún servicio.
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

/** `ImagenSubida.id` del contrato: un UUID. Un nombre de archivo no lo es. */
const ID_DE_IMAGEN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Lado de la miniatura, en píxeles CSS: lo que ocupa una imagen en el hilo. */
export const LADO_DE_MINIATURA = 96;

/** Estados que la vista le pone a un comentario tras una acción de quien mira. */
export const ESTADO_LOCAL = Object.freeze({
  /** Quien mira lo reportó: el servicio lo puso en la cola de moderación. */
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
 * ¿Es esto el `id` de una imagen guardada, y no un nombre de archivo antiguo?
 *
 * @param {unknown} valor
 * @returns {boolean}
 */
export function esIdDeImagen(valor) {
  return typeof valor === 'string' && ID_DE_IMAGEN.test(valor.trim());
}

/**
 * El texto alternativo de una imagen adjunta. No se puede describir lo que
 * enseña (nadie lo escribió), pero sí qué es y de quién: «Imagen 2 de 3 que
 * adjuntó Lyra» se entiende; un `alt` vacío la borraría para quien no la ve.
 *
 * @param {number} indice desde 0
 * @param {number} cuantas
 * @param {string} autor
 * @returns {string}
 */
export function textoAlternativo(indice, cuantas, autor) {
  return cuantas === 1
    ? `Imagen que adjuntó ${autor}`
    : `Imagen ${indice + 1} de ${cuantas} que adjuntó ${autor}`;
}

/** El símbolo de imagen y un nombre, para lo que no tiene miniatura. */
function sinMiniatura(nombre) {
  return [
    icono('imagen', { clase: 'icono comentario__adjunto-icono', etiqueta: null }),
    h('span', {
      clase: 'comentario__adjunto-nombre',
      texto: nombre,
      atributos: { title: nombre },
    }),
  ];
}

/**
 * Una imagen guardada: miniatura acotada y enlace a su tamaño completo.
 *
 * @param {string} id
 * @param {{url: string, alt: string}} datos
 * @returns {HTMLElement} `li.comentario__adjunto`
 */
function imagenAdjunta(id, { url, alt }) {
  const imagen = h('img', {
    clase: 'comentario__imagen',
    atributos: {
      src: url,
      alt,
      width: LADO_DE_MINIATURA,
      height: LADO_DE_MINIATURA,
      loading: 'lazy',
      decoding: 'async',
    },
  });
  const elemento = h('li', {
    clase: 'comentario__adjunto comentario__adjunto--imagen',
    datos: { imagenId: id },
    hijos: [
      h('a', {
        clase: 'comentario__imagen-enlace',
        atributos: { href: url, target: '_blank', rel: 'noopener' },
        hijos: [imagen, h('span', { clase: 'solo-lectores', texto: ' (se abre en otra pestaña)' })],
      }),
    ],
  });
  // Si ya no está (el servicio responde 404), el hueco lo dice en vez de
  // quedarse como un icono de imagen rota.
  imagen.addEventListener(
    'error',
    () => {
      elemento.classList.remove('comentario__adjunto--imagen');
      elemento.dataset.estado = 'no-disponible';
      elemento.replaceChildren(...sinMiniatura('Imagen no disponible'));
    },
    { once: true },
  );
  return elemento;
}

/**
 * Los adjuntos de un comentario.
 *
 * @param {string[]|null|undefined} imagenes `ComentarioResponse.imagenes`: ids
 *   de imágenes guardadas o, en comentarios anteriores a la 1.4.0, nombres de
 *   archivo
 * @param {{urlDeImagen?: ((id: string) => string)|null, autor?: string|null}} [opciones]
 *   `urlDeImagen`: la dirección de cada imagen; sin ella, todo se pinta por su
 *   nombre. `autor`: el apodo, para el texto alternativo.
 * @returns {HTMLElement|null} `null` si no hay ninguno
 */
export function adjuntosDeComentario(imagenes, { urlDeImagen = null, autor = null } = {}) {
  const lista = (Array.isArray(imagenes) ? imagenes : []).filter(
    (valor) => typeof valor === 'string' && valor.trim() !== '',
  );
  if (lista.length === 0) {
    return null;
  }
  const quien = String(autor ?? '').trim() || 'un jugador';
  const conImagen = typeof urlDeImagen === 'function';
  let antiguas = 0;
  const elementos = lista.map((valor, indice) => {
    if (conImagen && esIdDeImagen(valor)) {
      const id = valor.trim();
      return imagenAdjunta(id, {
        url: urlDeImagen(id),
        alt: textoAlternativo(indice, lista.length, quien),
      });
    }
    antiguas += 1;
    return h('li', {
      clase: 'comentario__adjunto',
      datos: { nombre: valor },
      hijos: sinMiniatura(valor),
    });
  });

  let nota = null;
  if (antiguas > 0) {
    nota = h('p', {
      clase: 'comentario__adjuntos-nota',
      texto:
        antiguas === 1
          ? 'Una imagen se adjuntó antes de que se guardaran: solo se conserva su nombre.'
          : 'Algunas imágenes se adjuntaron antes de que se guardaran: solo se conservan sus nombres.',
    });
  }
  return h('div', {
    clase: 'comentario__adjuntos',
    hijos: [
      h('ul', {
        clase: 'comentario__adjuntos-lista',
        atributos: {
          'aria-label': lista.length === 1 ? 'Imagen adjunta' : `${lista.length} imágenes adjuntas`,
        },
        hijos: elementos,
      }),
      nota,
    ],
  });
}

/**
 * ¿Es de quien mira? G4 (comentarios.yaml 1.9.0): el hilo público ya no trae
 * el `uid` del autor; lo dice el servidor en `propio`, comparando con el token
 * de quien lo pide. Si la respuesta no trae `propio` (un servicio anterior a
 * 1.9.0, o la respuesta de publicar de un cliente viejo), se compara el
 * `autorId` con el `uid` de la sesión, como antes.
 *
 * @param {object} comentario `ComentarioResponse`
 * @param {string|null} yo el `uid` de quien mira
 * @returns {boolean}
 */
export function esPropio(comentario, yo) {
  if (typeof comentario?.propio === 'boolean') {
    return comentario.propio;
  }
  return Boolean(yo) && comentario?.autorId === yo;
}

/**
 * Tarjeta de un comentario.
 *
 * @param {object} comentario `ComentarioResponse` del contrato
 * @param {{yo?: string|null, estadoLocal?: string|null,
 *          alEliminar?: ((comentario: object, articulo: HTMLElement) => void)|null,
 *          alReportar?: ((comentario: object, articulo: HTMLElement) => void)|null,
 *          urlDeImagen?: ((id: string) => string)|null}} [opciones]
 *   `yo`: el `uid` de quien mira; sin él no se ofrece ninguna acción.
 *   `urlDeImagen`: dónde se sirve cada imagen (ver «Las imágenes»).
 * @returns {HTMLElement} `article.comentario`
 */
export function tarjetaDeComentario(
  comentario,
  { yo = null, estadoLocal = null, alEliminar = null, alReportar = null, urlDeImagen = null } = {},
) {
  const apodo = typeof comentario?.apodoAutor === 'string' ? comentario.apodoAutor : '';
  const esMio = Boolean(yo) && esPropio(comentario, yo);

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
              comentario?.editado === true
                ? h('span', {
                    clase: 'distintivo distintivo--moderador comentario__editado',
                    texto: 'Editado por moderación',
                  })
                : null,
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
      adjuntosDeComentario(comentario?.imagenes, { urlDeImagen, autor: apodo }),
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
          texto: 'Un moderador lo revisará. Mientras tanto sigue a la vista.',
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
