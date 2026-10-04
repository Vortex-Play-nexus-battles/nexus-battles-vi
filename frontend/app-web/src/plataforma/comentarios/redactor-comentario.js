/**
 * Escribir una opinión sobre un producto, dentro de su detalle — UXC-3
 * (CommentComposer).
 *
 * Es el mismo acto que la vista aparte de comentarios (HU-COM-001), pero en
 * el sitio donde el jugador está mirando el producto: la ficha del
 * inventario, el detalle de la tienda. Texto e imágenes, contra
 * `POST /products/{id}/comments` (comentarios.yaml 1.5.0).
 *
 * ## Sin estrellas (B3)
 *
 * Calificar ya no es parte de opinar: el detalle tiene su propio control
 * (`calificar-producto.js`, `POST /products/{id}/rating`), que se usa una sola
 * vez y sin escribir nada (7.1). El contrato aún acepta `estrellas` en el
 * comentario por compatibilidad, pero este formulario no las manda: con dos
 * sitios para calificar, uno de los dos acabaría diciendo «tu calificación se
 * descartó» a quien solo quería opinar.
 *
 * ## Lo que decide el servicio, no esta vista
 *
 *   - Si el texto pasa el filtro: 202 es «en revisión», no «publicado».
 *   - Si el autor puede publicar: 403 `AUTOR_SILENCIADO`.
 *   - Qué es una imagen: el tipo lo dice la firma de sus bytes, no la
 *     extensión (415 `FORMATO_DE_IMAGEN_NO_ADMITIDO`); el tamaño, 2 MB y
 *     4096 píxeles por lado (413).
 *
 * ## Las imágenes
 *
 * Cada imagen se sube al elegirla (`POST /comentarios/imagenes`) y el
 * comentario viaja con sus `id` (máximo tres). Mientras sube, su miniatura lo
 * dice («Subiendo…», con una barra de progreso indeterminada: `fetch` no
 * informa de cuánto lleva); al terminar, «Lista» o el motivo del rechazo, con
 * «Reintentar» solo cuando reintentar puede servir. La miniatura se pinta con
 * el archivo que eligió el jugador, en su navegador: la imagen subida no es
 * pública hasta que el comentario se publica.
 *
 * Publicar con una imagen todavía subiendo espera a que termine; con una que
 * falló, no publica y dice qué hacer. Nada se pierde: el texto se queda.
 *
 * @module plataforma/comentarios/redactor-comentario
 */

import { h } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { limpiarAviso, pintarAviso } from '../../comun/ui/aviso.js';
import { conCarga } from '../../comun/ui/boton.js';
import { RUTAS, resolver, urlDeLogin } from '../../comun/sesion.js';
import {
  ErrorDeApi,
  ESTADO,
  MAXIMO_DE_IMAGENES,
  MOTIVO,
  TIPO,
  publicarComentario,
  subirImagen,
} from './cliente-comentarios.js';

/** Los formatos que admite el servicio (comentarios.yaml 1.5.0). */
export const FORMATOS_DE_IMAGEN = 'image/jpeg,image/png,image/webp';

/** Estados de una imagen elegida, en `data-estado` de su miniatura. */
export const ESTADO_IMAGEN = Object.freeze({
  SUBIENDO: 'subiendo',
  LISTA: 'lista',
  ERROR: 'error',
});

let secuencia = 0;

/**
 * Lo que dice el aviso tras un rechazo de publicación.
 *
 * @param {unknown} error
 * @returns {{tono: string, titulo: string, detalle: string, enlace?: {texto: string, href: string},
 *   campo?: 'texto'|'imagenes', reintentar?: boolean, sesion?: boolean}}
 */
export function mensajeDePublicacion(error) {
  if (!(error instanceof ErrorDeApi)) {
    return {
      tono: 'error',
      titulo: 'No pudimos publicar tu opinión',
      detalle: 'Tu texto sigue aquí. Revisa tu conexión y vuelve a intentarlo.',
      reintentar: true,
    };
  }
  if (error.motivo === MOTIVO.AUTOR_SILENCIADO) {
    return {
      tono: 'advertencia',
      titulo: 'Ahora mismo no puedes publicar',
      detalle:
        'Tu cuenta tiene un silencio activo. En tus sanciones ves hasta cuándo dura y cómo apelar.',
      enlace: { texto: 'Ver mis sanciones', href: resolver(RUTAS.misSanciones) },
    };
  }
  if (error.tipo === TIPO.IMAGENES_NO_VALIDAS) {
    return {
      tono: 'advertencia',
      titulo: 'Alguna imagen ya no se puede adjuntar',
      detalle:
        'Quita las imágenes y vuelve a elegirlas: las que no se publican en un día se borran. Tu texto sigue aquí.',
      campo: 'imagenes',
    };
  }
  if (error.estado === 404 || error.tipo === TIPO.PRODUCTO_INEXISTENTE) {
    return {
      tono: 'info',
      titulo: 'Este producto ya no está en el catálogo',
      detalle: 'No se pueden publicar opiniones sobre él.',
    };
  }
  if (error.estado === 409) {
    return {
      tono: 'advertencia',
      titulo: 'Tu opinión se cruzó con otra operación',
      detalle: 'Tu texto sigue aquí. Vuelve a intentarlo.',
      reintentar: true,
    };
  }
  if (error.estado === 401) {
    return {
      tono: 'advertencia',
      titulo: 'Tu sesión ya no es válida',
      detalle:
        'Vuelve a entrar para publicar. Tu texto no se ha perdido mientras no cierres esta ventana.',
      sesion: true,
    };
  }
  if (error.esDeFormulario) {
    return {
      tono: 'advertencia',
      titulo: 'Revisa tu opinión',
      detalle: error.errores[0]?.mensaje || error.detalle,
      campo: 'texto',
    };
  }
  if (error.estado >= 500 || error.estado === 0) {
    return {
      tono: 'error',
      titulo: 'No pudimos publicar tu opinión',
      detalle: 'Tu texto sigue aquí. Vuelve a intentarlo en un momento.',
      reintentar: true,
    };
  }
  return {
    tono: 'advertencia',
    titulo: 'No se pudo publicar tu opinión',
    detalle: error.detalle,
  };
}

/**
 * Por qué no se subió una imagen, en una frase, y si reintentar tiene sentido.
 * Se decide por `tipo`, `motivo` y `estado`: el 413 del borde (más de 3 MB)
 * llega sin problem details, y dice lo mismo que el del servicio.
 *
 * @param {unknown} error
 * @returns {{texto: string, reintentable: boolean, sesion?: boolean}}
 */
export function mensajeDeSubida(error) {
  if (!(error instanceof ErrorDeApi)) {
    return { texto: 'No se pudo subir. Revisa tu conexión y reinténtalo.', reintentable: true };
  }
  if (error.tipo === TIPO.IMAGEN_DEMASIADO_GRANDE || error.estado === 413) {
    return {
      texto: 'Pesa más de 2 MB o mide más de 4096 píxeles de lado.',
      reintentable: false,
    };
  }
  if (
    error.tipo === TIPO.IMAGEN_NO_ADMITIDA ||
    error.motivo === MOTIVO.FORMATO_DE_IMAGEN_NO_ADMITIDO ||
    error.estado === 415
  ) {
    return { texto: 'No es una imagen JPEG, PNG o WebP válida.', reintentable: false };
  }
  if (error.tipo === TIPO.IMAGEN_AUSENTE || error.estado === 400) {
    return { texto: 'El archivo está vacío.', reintentable: false };
  }
  if (error.estado === 401) {
    return {
      texto: 'Tu sesión ya no es válida: vuelve a entrar para adjuntarla.',
      reintentable: false,
      sesion: true,
    };
  }
  return { texto: 'No se pudo subir. Vuelve a intentarlo en un momento.', reintentable: true };
}

/**
 * El formulario para opinar.
 *
 * @param {{productoId: string,
 *          publicarImpl?: typeof publicarComentario,
 *          subirImagenImpl?: typeof subirImagen,
 *          alPublicar?: (resultado: {comentario: object, estado: string}) => void,
 *          crearUrl?: (archivo: Blob) => string,
 *          liberarUrl?: (url: string) => void}} opciones
 * @returns {{elemento: HTMLFormElement, enfocar: () => void}}
 */
export function redactorDeComentario({
  productoId,
  publicarImpl = publicarComentario,
  subirImagenImpl = subirImagen,
  alPublicar = () => {},
  crearUrl = (archivo) => globalThis.URL?.createObjectURL?.(archivo) ?? '',
  liberarUrl = (url) => {
    if (url) {
      globalThis.URL?.revokeObjectURL?.(url);
    }
  },
}) {
  secuencia += 1;
  const idTexto = `opinion-${secuencia}`;
  const idTextoError = `${idTexto}-error`;
  const idImagenes = `${idTexto}-imagenes`;
  const idPistaImagenes = `${idImagenes}-pista`;

  const texto = h('textarea', {
    clase: 'campo__control',
    atributos: {
      id: idTexto,
      name: 'texto',
      rows: 3,
      required: true,
      placeholder: '¿Qué te pareció? Cuéntalo para quien lo esté pensando.',
    },
  });
  const errorTexto = h('p', { clase: 'campo__error', atributos: { id: idTextoError } });
  errorTexto.hidden = true;

  const zonaAviso = h('div', { clase: 'redactor-comentario__aviso', datos: { zona: 'aviso' } });
  zonaAviso.hidden = true;

  // ---------------------------------------------------------------- imágenes

  /**
   * @typedef {{clave: number, archivo: File, nombre: string, url: string,
   *   estado: string, id: string|null, mensaje: string|null, reintentable: boolean,
   *   promesa: Promise<void>|null}} Adjunto
   */
  /** @type {Adjunto[]} */
  let adjuntos = [];
  let claves = 0;

  const entradaImagenes = h('input', {
    clase: 'redactor-comentario__archivo solo-lectores',
    atributos: {
      id: idImagenes,
      type: 'file',
      accept: FORMATOS_DE_IMAGEN,
      multiple: true,
      'aria-describedby': idPistaImagenes,
    },
  });
  const miniaturas = h('ul', {
    clase: 'redactor-comentario__miniaturas',
    atributos: { 'aria-label': 'Imágenes que vas a adjuntar' },
  });
  miniaturas.hidden = true;
  const notaImagenes = h('p', {
    clase: 'campo__pista',
    texto: `Hasta ${MAXIMO_DE_IMAGENES} imágenes JPEG, PNG o WebP de 2 MB como máximo. Se suben al elegirlas.`,
    atributos: { id: idPistaImagenes },
  });
  // Lo que pasa con cada imagen se anuncia aquí, una frase cada vez: que la
  // lista entera se vuelva a leer por cada cambio de estado no se entiende.
  const anuncio = h('p', {
    clase: 'solo-lectores',
    datos: { zona: 'anuncio-imagenes' },
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  const anunciar = (frase) => {
    anuncio.textContent = frase;
  };

  const quitar = (clave) => {
    const adjunto = adjuntos.find((a) => a.clave === clave);
    if (!adjunto) {
      return;
    }
    liberarUrl(adjunto.url);
    adjuntos = adjuntos.filter((a) => a.clave !== clave);
    pintarMiniaturas();
    anunciar(`${adjunto.nombre} quitada.`);
    entradaImagenes.focus();
  };

  const miniatura = (adjunto) => {
    const hijos = [
      adjunto.url
        ? h('img', { atributos: { src: adjunto.url, alt: '', width: 48, height: 48 } })
        : icono('imagen', { clase: 'icono', etiqueta: null }),
    ];
    const detalle = h('div', {
      clase: 'redactor-comentario__detalle',
      hijos: [
        h('span', {
          clase: 'redactor-comentario__nombre',
          texto: adjunto.nombre,
          atributos: { title: adjunto.nombre },
        }),
      ],
    });
    if (adjunto.estado === ESTADO_IMAGEN.SUBIENDO) {
      detalle.append(
        h('span', { clase: 'redactor-comentario__estado', texto: 'Subiendo…' }),
        // Indeterminada a propósito: `fetch` no dice cuánto lleva subido, y
        // una barra que avanza sola sería inventarse el dato.
        h('progress', {
          clase: 'redactor-comentario__progreso',
          atributos: { 'aria-label': `Subiendo ${adjunto.nombre}` },
        }),
      );
    } else if (adjunto.estado === ESTADO_IMAGEN.LISTA) {
      detalle.append(
        h('span', {
          clase: 'redactor-comentario__estado redactor-comentario__estado--lista',
          hijos: [
            icono('check', { clase: 'icono', etiqueta: null }),
            h('span', { texto: 'Lista' }),
          ],
        }),
      );
    } else {
      detalle.append(
        h('span', {
          clase: 'redactor-comentario__estado redactor-comentario__estado--error',
          texto: adjunto.mensaje,
        }),
      );
    }
    hijos.push(detalle);

    if (adjunto.estado === ESTADO_IMAGEN.ERROR && adjunto.reintentable) {
      const reintentar = h('button', {
        clase: 'boton boton--secundario boton--pequeno',
        texto: 'Reintentar',
        datos: { accion: 'reintentar-imagen' },
        atributos: { type: 'button', 'aria-label': `Volver a subir ${adjunto.nombre}` },
      });
      reintentar.addEventListener('click', () => {
        subir(adjunto);
        miniaturas
          .querySelector(`[data-clave="${adjunto.clave}"] [data-accion="quitar-imagen"]`)
          ?.focus();
      });
      hijos.push(reintentar);
    }
    const quitarBoton = h('button', {
      clase: 'boton boton--secundario boton--pequeno redactor-comentario__quitar',
      texto: 'Quitar',
      datos: { accion: 'quitar-imagen' },
      atributos: { type: 'button', 'aria-label': `Quitar ${adjunto.nombre}` },
    });
    quitarBoton.addEventListener('click', () => quitar(adjunto.clave));
    hijos.push(quitarBoton);

    return h('li', {
      clase: 'redactor-comentario__miniatura',
      datos: { estado: adjunto.estado, clave: adjunto.clave },
      atributos: { 'aria-busy': adjunto.estado === ESTADO_IMAGEN.SUBIENDO ? 'true' : null },
      hijos,
    });
  };

  function pintarMiniaturas() {
    miniaturas.replaceChildren(...adjuntos.map(miniatura));
    miniaturas.hidden = adjuntos.length === 0;
    // Con tres no caben más: el selector se apaga y la pista lo dice.
    entradaImagenes.disabled = adjuntos.length >= MAXIMO_DE_IMAGENES;
  }

  /** Cambia el estado de una imagen, si sigue elegida, y repinta. */
  const actualizar = (adjunto, cambios) => {
    if (!adjuntos.includes(adjunto)) {
      return false;
    }
    Object.assign(adjunto, cambios);
    pintarMiniaturas();
    return true;
  };

  function subir(adjunto) {
    actualizar(adjunto, { estado: ESTADO_IMAGEN.SUBIENDO, mensaje: null, id: null });
    adjunto.promesa = subirImagenImpl(adjunto.archivo).then(
      (imagen) => {
        if (actualizar(adjunto, { estado: ESTADO_IMAGEN.LISTA, id: imagen?.id ?? null })) {
          anunciar(`${adjunto.nombre}: lista para publicar.`);
        }
      },
      (error) => {
        const { texto: motivo, reintentable } = mensajeDeSubida(error);
        if (actualizar(adjunto, { estado: ESTADO_IMAGEN.ERROR, mensaje: motivo, reintentable })) {
          anunciar(`${adjunto.nombre} no se pudo adjuntar: ${motivo}`);
        }
      },
    );
    return adjunto.promesa;
  }

  entradaImagenes.addEventListener('change', () => {
    const elegidos = Array.from(entradaImagenes.files ?? []);
    entradaImagenes.value = '';
    const caben = Math.max(MAXIMO_DE_IMAGENES - adjuntos.length, 0);
    const nuevos = elegidos.slice(0, caben).map((archivo) => {
      claves += 1;
      return {
        clave: claves,
        archivo,
        nombre: archivo.name || 'imagen',
        url: crearUrl(archivo),
        estado: ESTADO_IMAGEN.SUBIENDO,
        id: null,
        mensaje: null,
        reintentable: false,
        promesa: null,
      };
    });
    adjuntos = [...adjuntos, ...nuevos];
    pintarMiniaturas();
    for (const adjunto of nuevos) {
      subir(adjunto);
    }
    const sobran = elegidos.length - nuevos.length;
    if (sobran > 0) {
      pintarAviso(zonaAviso, {
        tono: 'advertencia',
        titulo: `Caben ${MAXIMO_DE_IMAGENES} imágenes por opinión`,
        detalle:
          sobran === 1
            ? 'Una de las que elegiste se quedó fuera. Quita otra si prefieres esa.'
            : `${sobran} de las que elegiste se quedaron fuera. Quita otras si prefieres esas.`,
      });
    }
  });

  // -------------------------------------------------------------- formulario

  const publicar = h('button', {
    clase: 'boton boton--primario',
    texto: 'Publicar opinión',
    datos: { accion: 'publicar-opinion' },
    atributos: { type: 'submit' },
  });

  const elemento = h('form', {
    clase: 'redactor-comentario pila',
    atributos: { novalidate: true, 'aria-label': 'Escribe tu opinión' },
    hijos: [
      h('div', {
        clase: 'campo campo--area',
        hijos: [
          h('label', {
            clase: 'campo__etiqueta',
            texto: 'Tu opinión',
            atributos: { for: idTexto },
          }),
          texto,
          errorTexto,
        ],
      }),
      h('div', {
        clase: 'redactor-comentario__imagenes',
        hijos: [
          // La entrada va ANTES de su etiqueta: escondida a la vista pero no
          // al teclado, su foco se pinta en la etiqueta que la sigue
          // (`.redactor-comentario__archivo:focus-visible + ...`).
          entradaImagenes,
          h('label', {
            clase: 'boton boton--secundario boton--pequeno redactor-comentario__adjuntar',
            atributos: { for: idImagenes },
            hijos: [
              icono('imagen', { clase: 'icono', etiqueta: null }),
              h('span', { texto: 'Adjuntar imágenes' }),
            ],
          }),
          miniaturas,
          notaImagenes,
          anuncio,
        ],
      }),
      zonaAviso,
      h('div', { clase: 'redactor-comentario__pie', hijos: [publicar] }),
    ],
  });

  const marcarTexto = (mensaje) => {
    errorTexto.textContent = mensaje;
    errorTexto.hidden = false;
    texto.setAttribute('aria-invalid', 'true');
    texto.setAttribute('aria-describedby', idTextoError);
    texto.focus();
  };
  const desmarcarTexto = () => {
    errorTexto.hidden = true;
    texto.removeAttribute('aria-invalid');
    texto.removeAttribute('aria-describedby');
  };
  texto.addEventListener('input', desmarcarTexto);

  /**
   * El foco va a la primera imagen con problema; si no hay, al selector, y si
   * el selector está apagado (ya hay tres), a la primera miniatura.
   */
  const enfocarImagenes = () => {
    const conProblema = miniaturas.querySelector(`[data-estado="${ESTADO_IMAGEN.ERROR}"] button`);
    if (conProblema) {
      conProblema.focus();
    } else if (!entradaImagenes.disabled) {
      entradaImagenes.focus();
    } else {
      miniaturas.querySelector('button')?.focus();
    }
  };

  const reiniciar = () => {
    texto.value = '';
    for (const adjunto of adjuntos) {
      liberarUrl(adjunto.url);
    }
    adjuntos = [];
    pintarMiniaturas();
  };

  const avisar = (mensaje) => {
    const { tono, titulo, detalle } = mensaje;
    let accion = null;
    if (mensaje.reintentar) {
      accion = {
        texto: 'Reintentar',
        nombre: 'reintentar',
        alPulsar: () => elemento.requestSubmit(),
      };
    } else if (mensaje.sesion) {
      accion = {
        texto: 'Iniciar sesión',
        nombre: 'iniciar-sesion',
        alPulsar: () =>
          globalThis.location.assign(
            urlDeLogin({ volver: `${globalThis.location.pathname}${globalThis.location.search}` }),
          ),
      };
    }
    const caja = pintarAviso(zonaAviso, { tono, titulo, detalle, accion });
    if (mensaje.enlace) {
      caja.append(
        h('a', {
          clase: 'boton boton--secundario boton--pequeno',
          texto: mensaje.enlace.texto,
          atributos: { href: mensaje.enlace.href },
        }),
      );
    }
    return caja;
  };

  /** Espera a las imágenes que siguen subiendo. */
  const esperarSubidas = async () => {
    const pendientes = adjuntos
      .filter((adjunto) => adjunto.estado === ESTADO_IMAGEN.SUBIENDO && adjunto.promesa)
      .map((adjunto) => adjunto.promesa);
    if (pendientes.length > 0) {
      conCarga(publicar, true, 'Subiendo imágenes…');
      await Promise.allSettled(pendientes);
    }
  };

  elemento.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    const escrito = texto.value.trim();
    if (!escrito) {
      marcarTexto('Escribe tu opinión antes de publicarla.');
      return;
    }
    desmarcarTexto();

    try {
      await esperarSubidas();
      if (adjuntos.some((adjunto) => adjunto.estado !== ESTADO_IMAGEN.LISTA)) {
        avisar({
          tono: 'advertencia',
          titulo: 'Alguna imagen no se pudo adjuntar',
          detalle: 'Quítala o vuelve a intentarlo antes de publicar. Tu texto sigue aquí.',
        });
        enfocarImagenes();
        return;
      }

      const cuerpo = { texto: escrito };
      const ids = adjuntos.map((adjunto) => adjunto.id).filter(Boolean);
      if (ids.length > 0) {
        cuerpo.imagenes = ids;
      }

      conCarga(publicar, true, 'Publicando…');
      const resultado = await publicarImpl(productoId, cuerpo);
      reiniciar();
      if (resultado.estado === ESTADO.EN_REVISION) {
        avisar({
          tono: 'info',
          titulo: 'Tu opinión quedó en revisión',
          detalle:
            'Un moderador la revisará antes de publicarla. No aparece en el hilo mientras tanto.',
        });
      } else {
        avisar({ tono: 'exito', titulo: 'Opinión publicada', detalle: 'Ya aparece en el hilo.' });
      }
      alPublicar(resultado);
    } catch (error) {
      const mensaje = mensajeDePublicacion(error);
      avisar(mensaje);
      if (mensaje.campo === 'texto') {
        marcarTexto(mensaje.detalle);
      } else if (mensaje.campo === 'imagenes') {
        enfocarImagenes();
      }
    } finally {
      conCarga(publicar, false);
    }
  });

  return { elemento, enfocar: () => texto.focus() };
}
