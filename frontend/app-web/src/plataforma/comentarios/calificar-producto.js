/**
 * Calificar un producto sin comentarlo, dentro de su detalle — B3 (RatingControl).
 *
 * §7.1 del documento: «Los usuarios solo pueden calificar un producto una vez,
 * pero podrán agregar o retirar tantos comentarios como sea de su agrado».
 * Hasta la 1.5.0 de `comentarios.yaml` las estrellas viajaban dentro del
 * comentario y el hilo deducía quién había calificado; desde entonces la
 * calificación es un recurso aparte, y este control es su cara:
 *
 *   - `GET /products/{id}/rating/mia` decide qué se enseña: sin calificación
 *     (404, el estado normal de quien aún no opinó) se ofrecen las estrellas;
 *     con ella, «Tu calificación: N de 5», sin nada que cambiar;
 *   - `POST /products/{id}/rating` califica una sola vez y devuelve el resumen
 *     ya recalculado, que el hilo pinta sin otra petición;
 *   - un segundo intento (409 `ya-calificado`, también desde otra pestaña) no
 *     es un error del jugador: se dice que ya había calificado y se enseña la
 *     calificación que cuenta.
 *
 * Calificar es irreversible (ni edición ni retirada), así que no se manda al
 * pulsar una estrella: se elige y se confirma con «Calificar». Las estrellas son
 * radios de verdad (`selectorDeEstrellas`): flechas del teclado, un nombre por
 * opción y el foco siempre visible.
 *
 * Cada rechazo se decide por `estado`, `tipo` y `motivo`, nunca por el texto
 * del servidor (`shared/ui-kit/MAPEO-ERRORES.md`).
 *
 * @module plataforma/comentarios/calificar-producto
 */

import { h } from '../../comun/ui/dom.js';
import { conCarga } from '../../comun/ui/boton.js';
import { limpiarAviso, pintarAviso } from '../../comun/ui/aviso.js';
import {
  estrellasDeCalificacion,
  MAXIMO_ESTRELLAS,
  selectorDeEstrellas,
} from '../../comun/ui/comunidad/estrellas.js';
import { RUTAS, resolver, urlDeLogin } from '../../comun/sesion.js';
import { calificar, ErrorDeApi, miCalificacion, MOTIVO, TIPO } from './cliente-comentarios.js';

/** Estados del control, en `data-estado`. */
export const ESTADO_CALIFICACION = Object.freeze({
  COMPROBANDO: 'comprobando',
  PENDIENTE: 'pendiente',
  CALIFICADO: 'calificado',
  ERROR: 'error',
});

let secuencia = 0;

/**
 * ¿Es el 409 de «ya calificaste»?
 *
 * @param {unknown} error
 * @returns {boolean}
 */
export function esCalificacionDuplicada(error) {
  return (
    error instanceof ErrorDeApi &&
    (error.estado === 409 ||
      error.tipo === TIPO.YA_CALIFICADO ||
      error.motivo === MOTIVO.CALIFICACION_DUPLICADA)
  );
}

/**
 * Lo que dice el aviso cuando calificar no sale (salvo el 409, que no es un
 * fallo y tiene su propio camino).
 *
 * @param {unknown} error
 * @returns {{tono: string, titulo: string, detalle: string|null,
 *   enlace?: {texto: string, href: string}, sesion?: boolean, reintentar?: boolean}}
 */
export function mensajeDeCalificacion(error) {
  if (!(error instanceof ErrorDeApi)) {
    return {
      tono: 'error',
      titulo: 'No pudimos registrar tu calificación',
      detalle: 'Tu elección sigue marcada. Revisa tu conexión y vuelve a intentarlo.',
      reintentar: true,
    };
  }
  if (error.motivo === MOTIVO.AUTOR_SILENCIADO) {
    return {
      tono: 'advertencia',
      titulo: 'Ahora mismo no puedes calificar',
      detalle:
        'Tu cuenta tiene una sanción activa. En tus sanciones ves hasta cuándo dura y cómo apelar.',
      enlace: { texto: 'Ver mis sanciones', href: resolver(RUTAS.misSanciones) },
    };
  }
  if (error.estado === 401) {
    return {
      tono: 'advertencia',
      titulo: 'Tu sesión ya no es válida',
      detalle: 'Vuelve a entrar para calificar este producto.',
      sesion: true,
    };
  }
  if (error.estado === 403) {
    return {
      tono: 'advertencia',
      titulo: 'Tu cuenta no puede calificar productos',
      detalle: null,
    };
  }
  if (error.estado === 404 || error.tipo === TIPO.PRODUCTO_INEXISTENTE) {
    return {
      tono: 'info',
      titulo: 'Este producto ya no está en el catálogo',
      detalle: 'No se puede calificar.',
    };
  }
  if (error.estado === 400) {
    return {
      tono: 'advertencia',
      titulo: 'Elige de 1 a 5 estrellas',
      detalle: null,
    };
  }
  return {
    tono: 'error',
    titulo: 'No pudimos registrar tu calificación',
    detalle: 'Tu elección sigue marcada. Vuelve a intentarlo en un momento.',
    reintentar: true,
  };
}

/** La vuelta a esta misma página tras entrar. */
function volverAqui() {
  return `${globalThis.location?.pathname ?? ''}${globalThis.location?.search ?? ''}`;
}

/**
 * El control de calificación de un producto, para quien tiene sesión.
 *
 * @param {object} opciones
 * @param {string} opciones.productoId
 * @param {typeof miCalificacion} [opciones.miCalificacionImpl]
 * @param {typeof calificar} [opciones.calificarImpl]
 * @param {(resultado: {estrellas: number, resumen: object|null, yaExistia: boolean}) => void} [opciones.alCalificar]
 *   tras calificar (o descubrir que ya se había calificado): `resumen` es el
 *   que devolvió el servicio, o `null` si hay que volver a leerlo.
 * @returns {{elemento: HTMLElement, cargar: () => Promise<void>, estado: () => string}}
 */
export function controlDeCalificacion({
  productoId,
  miCalificacionImpl = miCalificacion,
  calificarImpl = calificar,
  alCalificar = () => {},
}) {
  secuencia += 1;
  const idError = `calificar-${secuencia}-error`;

  const elemento = h('div', {
    clase: 'calificar-producto',
    datos: { zona: 'calificar' },
  });
  const zonaAviso = h('div', {
    clase: 'calificar-producto__aviso',
    datos: { zona: 'aviso-calificacion' },
  });
  zonaAviso.hidden = true;
  let estado = ESTADO_CALIFICACION.COMPROBANDO;

  const cambiarA = (nuevo, ...hijos) => {
    estado = nuevo;
    elemento.dataset.estado = nuevo;
    elemento.replaceChildren(...hijos);
  };

  const avisar = (mensaje, { alReintentar = null } = {}) => {
    let accion = null;
    if (mensaje.reintentar && alReintentar) {
      accion = { texto: 'Reintentar', nombre: 'reintentar', alPulsar: alReintentar };
    } else if (mensaje.sesion) {
      accion = {
        texto: 'Iniciar sesión',
        nombre: 'iniciar-sesion',
        alPulsar: () => globalThis.location.assign(urlDeLogin({ volver: volverAqui() })),
      };
    }
    const caja = pintarAviso(zonaAviso, {
      tono: mensaje.tono,
      titulo: mensaje.titulo,
      detalle: mensaje.detalle,
      accion,
    });
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

  /** «Tu calificación: N de 5», sin opción de cambiarla. */
  const pintarCalificado = (estrellas, aviso = null) => {
    const bloque = h('div', {
      clase: 'calificar-producto__propia',
      // Enfocable por programa: tras calificar, el foco llega aquí en vez de
      // perderse con el formulario que desaparece.
      atributos: { tabindex: '-1' },
      hijos: [
        h('p', {
          clase: 'calificar-producto__titulo',
          hijos: [
            h('span', { texto: `Tu calificación: ${estrellas} de ${MAXIMO_ESTRELLAS}` }),
            // Las estrellas repiten lo que ya dice el texto: fuera del lector.
            h('span', {
              clase: 'calificar-producto__estrellas',
              atributos: { 'aria-hidden': 'true' },
              hijos: [estrellasDeCalificacion(estrellas, { conCifra: false })],
            }),
          ],
        }),
        h('p', {
          clase: 'campo__pista',
          texto:
            'Solo se califica una vez: tu calificación ya cuenta en el promedio y no se cambia.',
        }),
      ],
    });
    limpiarAviso(zonaAviso);
    cambiarA(ESTADO_CALIFICACION.CALIFICADO, bloque, zonaAviso);
    if (aviso) {
      avisar(aviso);
    }
    return bloque;
  };

  const pintarFormulario = () => {
    const selector = selectorDeEstrellas({
      leyenda: 'Califica este producto',
      ayuda:
        'Solo se califica una vez y no se cambia después. Opinar, en cambio, puedes las veces que quieras.',
    });
    const error = h('p', {
      clase: 'campo__error',
      texto: 'Elige de 1 a 5 estrellas antes de calificar.',
      atributos: { id: idError },
    });
    error.hidden = true;
    const enviar = h('button', {
      clase: 'boton boton--primario boton--pequeno',
      texto: 'Calificar',
      datos: { accion: 'calificar' },
      atributos: { type: 'submit' },
    });
    const formulario = h('form', {
      clase: 'calificar-producto__formulario',
      atributos: { novalidate: true },
      hijos: [
        selector.elemento,
        error,
        h('div', { clase: 'calificar-producto__acciones', hijos: [enviar] }),
      ],
    });
    const ayudaDelGrupo = selector.elemento.getAttribute('aria-describedby');
    const marcarError = (visible) => {
      error.hidden = !visible;
      selector.elemento.setAttribute(
        'aria-describedby',
        visible ? `${ayudaDelGrupo} ${idError}` : ayudaDelGrupo,
      );
    };
    selector.elemento.addEventListener('change', () => marcarError(false));
    /** Mientras se envía, ni se cambia la nota ni se vuelve a enviar. */
    const bloquear = (activo) => {
      selector.elemento.disabled = activo;
      conCarga(enviar, activo, 'Calificando…');
    };

    formulario.addEventListener('submit', async (evento) => {
      evento.preventDefault();
      limpiarAviso(zonaAviso);
      const valor = selector.valor();
      if (valor === null) {
        marcarError(true);
        selector.enfocar();
        return;
      }
      marcarError(false);
      bloquear(true);
      try {
        const resultado = await calificarImpl(productoId, valor);
        const estrellas = Number.isInteger(resultado?.estrellas) ? resultado.estrellas : valor;
        pintarCalificado(estrellas, {
          tono: 'exito',
          titulo: 'Calificación registrada',
          detalle: 'Gracias: ya cuenta en el promedio del producto.',
        }).focus();
        alCalificar({ estrellas, resumen: resultado?.resumen ?? null, yaExistia: false });
      } catch (fallo) {
        if (esCalificacionDuplicada(fallo)) {
          await yaCalificado();
          return;
        }
        bloquear(false);
        avisar(mensajeDeCalificacion(fallo), { alReintentar: () => formulario.requestSubmit() });
      }
    });

    cambiarA(ESTADO_CALIFICACION.PENDIENTE, formulario, zonaAviso);
  };

  /**
   * El servicio dice que ya había una calificación (otra pestaña, otro
   * dispositivo): se lee cuál es y se enseña, sin tratarlo como un error.
   */
  const yaCalificado = async () => {
    let propia = null;
    try {
      propia = await miCalificacionImpl(productoId);
    } catch {
      propia = null;
    }
    const aviso = {
      tono: 'info',
      titulo: 'Ya habías calificado este producto',
      detalle: 'Cuenta tu primera calificación: no se cambia ni se suma otra.',
    };
    if (Number.isInteger(propia?.estrellas)) {
      pintarCalificado(propia.estrellas, aviso).focus();
      alCalificar({ estrellas: propia.estrellas, resumen: null, yaExistia: true });
      return;
    }
    // No se pudo leer cuál: se dice igual, sin inventar una cifra.
    const bloque = h('div', { clase: 'calificar-producto__propia', atributos: { tabindex: '-1' } });
    cambiarA(ESTADO_CALIFICACION.CALIFICADO, bloque, zonaAviso);
    avisar(aviso);
    bloque.focus();
    alCalificar({ estrellas: null, resumen: null, yaExistia: true });
  };

  const pintarError = (fallo) => {
    const sinSesion = fallo instanceof ErrorDeApi && fallo.estado === 401;
    limpiarAviso(zonaAviso);
    cambiarA(ESTADO_CALIFICACION.ERROR, zonaAviso);
    pintarAviso(zonaAviso, {
      tono: sinSesion ? 'advertencia' : 'error',
      titulo: sinSesion
        ? 'Tu sesión ya no es válida'
        : 'No pudimos comprobar si ya calificaste este producto',
      detalle: sinSesion
        ? 'Vuelve a entrar para calificar este producto.'
        : 'Las opiniones se siguen viendo. Vuelve a intentarlo en un momento.',
      accion: sinSesion
        ? {
            texto: 'Iniciar sesión',
            nombre: 'iniciar-sesion',
            alPulsar: () => globalThis.location.assign(urlDeLogin({ volver: volverAqui() })),
          }
        : { texto: 'Reintentar', nombre: 'reintentar-calificacion', alPulsar: () => cargar() },
    });
  };

  async function cargar() {
    cambiarA(
      ESTADO_CALIFICACION.COMPROBANDO,
      h('p', {
        clase: 'calificar-producto__estado',
        texto: 'Comprobando si ya calificaste este producto…',
        atributos: { role: 'status' },
      }),
    );
    let propia;
    try {
      propia = await miCalificacionImpl(productoId);
    } catch (fallo) {
      pintarError(fallo);
      return;
    }
    if (Number.isInteger(propia?.estrellas)) {
      pintarCalificado(propia.estrellas);
    } else {
      pintarFormulario();
    }
  }

  elemento.dataset.estado = estado;
  return { elemento, cargar, estado: () => estado };
}
