/**
 * Escribir una opinión sobre un producto, dentro de su detalle — UXC-3
 * (CommentComposer).
 *
 * Es el mismo acto que la vista aparte de comentarios (HU-COM-001), pero en
 * el sitio donde el jugador está mirando el producto: la ficha del
 * inventario, el detalle de la tienda. Texto, estrellas opcionales y
 * adjuntos, contra `POST /products/{id}/comments` (comentarios.yaml 1.3.0).
 *
 * ## Lo que decide el servicio, no esta vista
 *
 *   - Si una calificación cuenta: la segunda del mismo jugador entra sin
 *     estrellas (`calificacionDescartada`, D-07). Aquí solo se avisa antes
 *     cuando el hilo ya dice que calificó, para no ofrecer lo que no valdrá.
 *   - Si el texto pasa el filtro: 202 es «en revisión», no «publicado».
 *   - Si el autor puede publicar: 403 `AUTOR_SILENCIADO`.
 *   - Qué formatos de imagen se admiten: 422 `FORMATO_DE_IMAGEN_NO_ADMITIDO`.
 *
 * ## Las imágenes
 *
 * El contrato recibe **nombres de archivo**. La vista previa se hace con el
 * archivo que el jugador eligió, en su navegador (`URL.createObjectURL`), y
 * se dice que al publicar viaja el nombre: el hilo no podrá enseñar la
 * imagen hasta que el servicio la guarde.
 *
 * @module plataforma/comentarios/redactor-comentario
 */

import { h } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { limpiarAviso, pintarAviso } from '../../comun/ui/aviso.js';
import { conCarga } from '../../comun/ui/boton.js';
import { selectorDeEstrellas } from '../../comun/ui/comunidad/estrellas.js';
import { RUTAS, resolver, urlDeLogin } from '../../comun/sesion.js';
import { ErrorDeApi, ESTADO, MOTIVO, publicarComentario } from './cliente-comentarios.js';

let secuencia = 0;

/**
 * Lo que dice el aviso tras un rechazo de publicación.
 *
 * @param {unknown} error
 * @returns {{tono: string, titulo: string, detalle: string, enlace?: {texto: string, href: string},
 *   campo?: 'texto'|'imagenes', sinCalificar?: boolean, reintentar?: boolean, sesion?: boolean}}
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
  if (error.motivo === MOTIVO.FORMATO_DE_IMAGEN_NO_ADMITIDO || error.estado === 422) {
    return {
      tono: 'advertencia',
      titulo: 'Alguna imagen no se puede adjuntar',
      detalle: error.detalle || 'Quita la imagen marcada o elige otra en un formato habitual.',
      campo: 'imagenes',
    };
  }
  if (error.estado === 409) {
    return {
      tono: 'advertencia',
      titulo: 'Tu calificación chocó con otra al mismo tiempo',
      detalle:
        'Puedes publicar tu opinión sin estrellas; la calificación que ya cuenta es la primera.',
      sinCalificar: true,
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
 * El formulario para opinar.
 *
 * @param {{productoId: string, yaCalificado?: boolean,
 *          publicarImpl?: typeof publicarComentario,
 *          alPublicar?: (resultado: {comentario: object, estado: string}) => void,
 *          crearUrl?: (archivo: Blob) => string}} opciones
 * @returns {{elemento: HTMLFormElement, enfocar: () => void}}
 */
export function redactorDeComentario({
  productoId,
  yaCalificado = false,
  publicarImpl = publicarComentario,
  alPublicar = () => {},
  crearUrl = (archivo) => globalThis.URL?.createObjectURL?.(archivo) ?? '',
}) {
  secuencia += 1;
  const idTexto = `opinion-${secuencia}`;
  const idTextoError = `${idTexto}-error`;
  const idImagenes = `${idTexto}-imagenes`;

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

  const estrellas = selectorDeEstrellas();
  const marcarCalificado = () =>
    estrellas.deshabilitar(
      'Ya calificaste este producto: tu opinión se publica sin estrellas y tu nota sigue contando.',
    );
  if (yaCalificado) {
    marcarCalificado();
  }

  // Adjuntos: el archivo se queda en el navegador para la vista previa; al
  // servicio viaja su nombre (el contrato no recibe imágenes).
  /** @type {Array<{nombre: string, url: string}>} */
  let adjuntos = [];
  const entradaImagenes = h('input', {
    clase: 'redactor-comentario__archivo solo-lectores',
    atributos: { id: idImagenes, type: 'file', accept: 'image/*', multiple: true },
  });
  const miniaturas = h('ul', {
    clase: 'redactor-comentario__miniaturas',
    atributos: { 'aria-label': 'Imágenes que vas a adjuntar' },
  });
  miniaturas.hidden = true;
  const notaImagenes = h('p', {
    clase: 'campo__pista',
    texto:
      'Al publicar se guarda el nombre de cada imagen; su vista previa aún no se muestra en el hilo.',
  });

  const pintarMiniaturas = () => {
    miniaturas.replaceChildren(
      ...adjuntos.map((adjunto, indice) => {
        const quitar = h('button', {
          clase: 'boton boton--secundario boton--pequeno redactor-comentario__quitar',
          texto: 'Quitar',
          datos: { accion: 'quitar-imagen' },
          atributos: { type: 'button', 'aria-label': `Quitar ${adjunto.nombre}` },
        });
        quitar.addEventListener('click', () => {
          globalThis.URL?.revokeObjectURL?.(adjunto.url);
          adjuntos = adjuntos.filter((_, i) => i !== indice);
          pintarMiniaturas();
          entradaImagenes.focus();
        });
        return h('li', {
          clase: 'redactor-comentario__miniatura',
          hijos: [
            adjunto.url
              ? h('img', { atributos: { src: adjunto.url, alt: '', width: 48, height: 48 } })
              : icono('imagen', { clase: 'icono', etiqueta: null }),
            h('span', {
              clase: 'redactor-comentario__nombre',
              texto: adjunto.nombre,
              atributos: { title: adjunto.nombre },
            }),
            quitar,
          ],
        });
      }),
    );
    miniaturas.hidden = adjuntos.length === 0;
  };

  entradaImagenes.addEventListener('change', () => {
    for (const archivo of Array.from(entradaImagenes.files ?? [])) {
      adjuntos.push({ nombre: archivo.name, url: crearUrl(archivo) });
    }
    entradaImagenes.value = '';
    pintarMiniaturas();
  });

  const zonaAviso = h('div', { clase: 'redactor-comentario__aviso', datos: { zona: 'aviso' } });
  zonaAviso.hidden = true;

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
      estrellas.elemento,
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

  const reiniciar = () => {
    texto.value = '';
    for (const adjunto of adjuntos) {
      globalThis.URL?.revokeObjectURL?.(adjunto.url);
    }
    adjuntos = [];
    pintarMiniaturas();
    estrellas.limpiar();
  };

  const avisar = (mensaje) => {
    const { tono, titulo, detalle } = mensaje;
    let accion = null;
    if (mensaje.sinCalificar) {
      accion = {
        texto: 'Publicar sin estrellas',
        nombre: 'publicar-sin-estrellas',
        alPulsar: () => {
          estrellas.limpiar();
          elemento.requestSubmit();
        },
      };
    } else if (mensaje.reintentar) {
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

  elemento.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    const escrito = texto.value.trim();
    if (!escrito) {
      marcarTexto('Escribe tu opinión antes de publicarla.');
      return;
    }
    desmarcarTexto();

    const cuerpo = { texto: escrito };
    if (adjuntos.length > 0) {
      cuerpo.imagenes = adjuntos.map((adjunto) => adjunto.nombre);
    }
    const nota = estrellas.valor();
    if (nota !== null) {
      cuerpo.estrellas = nota;
    }

    conCarga(publicar, true, 'Publicando…');
    try {
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
        avisar({
          tono: 'exito',
          titulo: 'Opinión publicada',
          detalle: resultado.comentario?.calificacionDescartada
            ? 'Ya habías calificado este producto: se publicó sin estrellas.'
            : 'Ya aparece en el hilo.',
        });
      }
      alPublicar(resultado);
    } catch (error) {
      const mensaje = mensajeDePublicacion(error);
      avisar(mensaje);
      if (mensaje.campo === 'texto') {
        marcarTexto(mensaje.detalle);
      } else if (mensaje.campo === 'imagenes') {
        entradaImagenes.focus();
      }
    } finally {
      conCarga(publicar, false);
    }
  });

  return { elemento, enfocar: () => texto.focus(), marcarCalificado };
}
