/**
 * Reportar un comentario ajeno, desde el hilo del producto — UXC-3 (ReportComment).
 *
 * RF-COM-006: cualquier jugador con sesión reporta un comentario con una de
 * las seis categorías de la ficha y, si quiere, unas palabras (hasta 500).
 * El reportante sale del token, nunca del cuerpo (`cliente-moderacion.js`).
 *
 * Se abre como diálogo del kit (`comun/ui/dialogo.js`): foco dentro, Escape
 * para cerrar y el foco de vuelta al botón «Reportar». Cada respuesta del
 * contrato tiene su frase, decidida por `motivo` y `estado`, nunca por el
 * texto del servidor (`shared/ui-kit/MAPEO-ERRORES.md`):
 *
 *   - 201: reportado; el comentario pasa a revisión y sale del hilo público.
 *   - 409 REPORTE_DUPLICADO: ya lo había reportado; se dice, sin error.
 *   - 429 LIMITE_DE_REPORTES: el tope diario (D-27); se dice cuándo vuelve.
 *   - 404: el comentario ya no está publicado.
 *   - 401: la sesión ya no vale; se ofrece entrar otra vez.
 *   - lo demás: no se pudo enviar; el motivo elegido se conserva y se reintenta.
 *
 * @module plataforma/comentarios/reportar-comentario
 */

import { abrirDialogo } from '../../comun/ui/dialogo.js';
import { h } from '../../comun/ui/dom.js';
import { limpiarAviso, pintarAviso } from '../../comun/ui/aviso.js';
import { conCarga } from '../../comun/ui/boton.js';
import { urlDeLogin } from '../../comun/sesion.js';
import {
  CATEGORIAS,
  ErrorDeApi,
  MOTIVO_MODERACION,
  reportarComentario,
} from './cliente-moderacion.js';

/** Largo máximo de la descripción, de `ReporteRequest.descripcion`. */
export const LARGO_DESCRIPCION = 500;

/** Resultados posibles del diálogo. */
export const RESULTADO_REPORTE = Object.freeze({
  REPORTADO: 'REPORTADO',
  YA_REPORTADO: 'YA_REPORTADO',
  RETIRADO: 'RETIRADO',
  CANCELADO: 'CANCELADO',
});

let secuencia = 0;

/**
 * Qué decir ante un rechazo, o `null` si no es uno de los conocidos.
 *
 * @param {unknown} error
 * @returns {{tono: string, titulo: string, detalle: string, resultado?: string}|null}
 */
export function mensajeDelRechazo(error) {
  if (!(error instanceof ErrorDeApi)) {
    return null;
  }
  if (error.motivo === MOTIVO_MODERACION.REPORTE_DUPLICADO || error.estado === 409) {
    return {
      tono: 'info',
      titulo: 'Ya habías reportado este comentario',
      detalle: 'Tu reporte anterior sigue en pie y un moderador lo revisará.',
      resultado: RESULTADO_REPORTE.YA_REPORTADO,
    };
  }
  if (error.motivo === MOTIVO_MODERACION.LIMITE_DE_REPORTES || error.estado === 429) {
    return {
      tono: 'advertencia',
      titulo: 'Alcanzaste el límite de reportes de hoy',
      detalle: 'Podrás volver a reportar mañana. Los reportes que ya enviaste siguen en revisión.',
    };
  }
  if (error.estado === 404) {
    return {
      tono: 'info',
      titulo: 'Ese comentario ya no está publicado',
      detalle: 'Lo retiró su autor o ya lo revisó un moderador.',
      resultado: RESULTADO_REPORTE.RETIRADO,
    };
  }
  return null;
}

/**
 * Abre el diálogo de reporte.
 *
 * @param {{productoId: string, comentario: {id: string, apodoAutor?: string},
 *          reportarImpl?: typeof reportarComentario, volver?: string|null}} opciones
 * @returns {Promise<{resultado: string, reporte?: object}>}
 */
export function abrirReporteDeComentario({
  productoId,
  comentario,
  reportarImpl = reportarComentario,
  volver = null,
}) {
  secuencia += 1;
  const nombre = `categoria-reporte-${secuencia}`;
  const idDescripcion = `descripcion-reporte-${secuencia}`;
  const idCuenta = `${idDescripcion}-cuenta`;
  const idErrorCategoria = `${nombre}-error`;

  return new Promise((resolver) => {
    let terminado = false;
    let cerrarDialogo = () => {};
    // Lo que significa cerrar el diálogo sin enviar: normalmente, cancelar;
    // tras un «ya lo habías reportado», confirmar que ya está en revisión.
    let resultadoAlCerrar = RESULTADO_REPORTE.CANCELADO;
    const terminar = (resultado, extra = {}) => {
      if (terminado) {
        return;
      }
      terminado = true;
      cerrarDialogo();
      resolver({ resultado, ...extra });
    };

    const opciones = CATEGORIAS.map(({ valor, etiqueta }) =>
      h('label', {
        clase: 'radio reporte-comentario__opcion',
        hijos: [
          h('input', {
            clase: 'radio__entrada',
            atributos: { type: 'radio', name: nombre, value: valor, required: true },
          }),
          h('span', { clase: 'radio__etiqueta', texto: etiqueta }),
        ],
      }),
    );
    const errorCategoria = h('p', {
      clase: 'campo__error',
      texto: 'Elige el motivo del reporte.',
      atributos: { id: idErrorCategoria },
    });
    errorCategoria.hidden = true;

    const categorias = h('fieldset', {
      clase: 'reporte-comentario__categorias',
      hijos: [
        h('legend', { clase: 'campo__etiqueta', texto: '¿Qué pasa con este comentario?' }),
        ...opciones,
        errorCategoria,
      ],
    });

    const descripcion = h('textarea', {
      clase: 'campo__control',
      atributos: {
        id: idDescripcion,
        rows: 3,
        maxlength: LARGO_DESCRIPCION,
        'aria-describedby': idCuenta,
      },
    });
    const cuenta = h('p', {
      clase: 'campo__pista',
      texto: `Opcional · hasta ${LARGO_DESCRIPCION} caracteres`,
      atributos: { id: idCuenta },
    });
    descripcion.addEventListener('input', () => {
      const quedan = LARGO_DESCRIPCION - descripcion.value.length;
      cuenta.textContent = `Opcional · quedan ${quedan} caracteres`;
    });

    const zonaAviso = h('div', { datos: { zona: 'aviso-reporte' } });
    zonaAviso.hidden = true;

    const cancelar = h('button', {
      clase: 'boton boton--secundario',
      texto: 'Cancelar',
      datos: { accion: 'cancelar' },
      atributos: { type: 'button' },
    });
    const enviar = h('button', {
      clase: 'boton boton--primario',
      texto: 'Enviar reporte',
      datos: { accion: 'enviar-reporte' },
      atributos: { type: 'submit' },
    });

    const formulario = h('form', {
      clase: 'reporte-comentario pila',
      atributos: { novalidate: true },
      hijos: [
        h('p', {
          clase: 't-meta',
          texto: `El comentario de ${comentario?.apodoAutor || 'este jugador'} pasará a revisión y dejará de verse hasta que un moderador decida.`,
        }),
        categorias,
        h('div', {
          clase: 'campo campo--area',
          hijos: [
            h('label', {
              clase: 'campo__etiqueta',
              texto: 'Cuéntanos más',
              atributos: { for: idDescripcion },
            }),
            descripcion,
            cuenta,
          ],
        }),
        zonaAviso,
        h('div', { clase: 'dialogo__acciones', hijos: [cancelar, enviar] }),
      ],
    });

    const { cerrar, elemento } = abrirDialogo({
      titulo: 'Reportar comentario',
      cuerpo: formulario,
    });
    cerrarDialogo = cerrar;
    elemento.classList.add('dialogo--reporte');

    // Escape, el aspa o el velo cierran sin pasar por aquí: el diálogo del kit
    // no avisa, así que se mira cuándo sale del documento (el velo cuelga
    // directamente de `body`).
    const vigia = new MutationObserver(() => {
      if (!elemento.isConnected) {
        vigia.disconnect();
        if (!terminado) {
          terminado = true;
          resolver({ resultado: resultadoAlCerrar });
        }
      }
    });
    vigia.observe(document.body, { childList: true });

    cancelar.addEventListener('click', () => terminar(resultadoAlCerrar));

    formulario.addEventListener('submit', async (evento) => {
      evento.preventDefault();
      limpiarAviso(zonaAviso);
      const elegida = formulario.querySelector(`input[name="${nombre}"]:checked`);
      if (!elegida) {
        errorCategoria.hidden = false;
        categorias.setAttribute('aria-describedby', idErrorCategoria);
        formulario.querySelector(`input[name="${nombre}"]`)?.focus();
        return;
      }
      errorCategoria.hidden = true;
      categorias.removeAttribute('aria-describedby');

      const cuerpo = { categoria: elegida.value };
      const texto = descripcion.value.trim();
      if (texto) {
        cuerpo.descripcion = texto;
      }

      conCarga(enviar, true, 'Enviando…');
      try {
        const reporte = await reportarImpl(productoId, comentario.id, cuerpo);
        terminar(RESULTADO_REPORTE.REPORTADO, { reporte });
      } catch (error) {
        conCarga(enviar, false);
        const conocido = mensajeDelRechazo(error);
        if (conocido?.resultado) {
          // Ya reportado o ya retirado: no hay nada que reintentar. Se dice y
          // el diálogo se queda con un solo botón para cerrarlo.
          const { resultado, ...mensaje } = conocido;
          pintarAviso(zonaAviso, mensaje);
          resultadoAlCerrar = resultado;
          enviar.hidden = true;
          cancelar.textContent = 'Cerrar';
          cancelar.focus();
          return;
        }
        if (conocido) {
          pintarAviso(zonaAviso, conocido);
          return;
        }
        if (error instanceof ErrorDeApi && error.estado === 401) {
          pintarAviso(zonaAviso, {
            tono: 'advertencia',
            titulo: 'Tu sesión ya no es válida',
            detalle: 'Vuelve a entrar para reportar este comentario.',
            accion: {
              texto: 'Iniciar sesión',
              nombre: 'iniciar-sesion',
              alPulsar: () => {
                globalThis.location.assign(
                  urlDeLogin({
                    volver:
                      volver ?? `${globalThis.location.pathname}${globalThis.location.search}`,
                  }),
                );
              },
            },
          });
          return;
        }
        pintarAviso(zonaAviso, {
          tono: 'error',
          titulo: 'No pudimos enviar el reporte',
          detalle: 'Tu motivo sigue elegido. Vuelve a intentarlo en un momento.',
          accion: {
            texto: 'Reintentar',
            nombre: 'reintentar-reporte',
            alPulsar: () => formulario.requestSubmit(),
          },
        });
      }
    });
  });
}
