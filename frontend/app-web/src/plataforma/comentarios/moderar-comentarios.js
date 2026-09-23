/**
 * La cola de moderacion de comentarios — RF-COM-005 y RF-COM-008.
 *
 * <h2>Que pantalla es esta y por que no existia</h2>
 *
 * El estado EN_REVISION existia desde la primera migracion y el filtro
 * automatico metia comentarios ahi. Lo que no existia era la SALIDA: ningun
 * sitio donde un moderador los viera, ninguna forma de resolverlos. Un
 * comentario retenido se quedaba invisible para siempre y su autor no sabia
 * por que. Esta vista es esa salida.
 *
 * <h2>Tres decisiones de esta pantalla</h2>
 *
 * <b>La cola y el detalle van juntos.</b> El moderador decide mirando el
 * texto, quien lo reporto y por que; mandarlo a otra pantalla para ver los
 * reportes y volver es como se acaba resolviendo sin leer.
 *
 * <b>El motivo es obligatorio y el boton esta apagado hasta que lo hay.</b>
 * El servicio ya lo exige (400 sin motivo). Repetirlo aqui no es
 * desconfianza: es que enterarse por un error despues de pulsar es peor que
 * verlo antes.
 *
 * <b>Las acciones que se ofrecen dependen del estado.</b> No se pinta
 * «Restaurar» sobre algo que esta en revision. La tabla que lo decide es un
 * espejo del servicio y esta en `cliente-moderacion.js`; si se separan, manda
 * el servicio y la vista se entera por el 409.
 *
 * Nada se pinta con innerHTML: el texto de un comentario reportado es
 * exactamente el contenido del que hay que desconfiar.
 */

import { h, vaciar } from '../../comun/ui/dom.js';
import { pintarAviso, limpiarAviso } from '../../comun/ui/aviso.js';
import {
  pintarEstado,
  estadoVacio,
  estadoDeError,
  estadoDeCarga,
} from '../../comun/ui/estado-vista.js';
import { fechaHora } from '../../comun/ui/formato.js';
import {
  accionesDesde,
  consultarCola,
  consultarDetalle,
  resolverComentario,
  MOTIVO_MODERACION,
} from './cliente-moderacion.js';

/** Cuantas entradas se piden de una vez. El servicio recorta a 100. */
const TAMANO = 20;

/**
 * Mensajes por `motivo` del problem detail. Nunca se compara el texto del
 * servidor: se compara el codigo estable que el contrato declara.
 */
const EXPLICACION = Object.freeze({
  [MOTIVO_MODERACION.TRANSICION_INVALIDA]: {
    titulo: 'Otro moderador se adelanto',
    detalle: 'El comentario ya no esta como lo tenias en pantalla. Se recarga la cola.',
  },
});

/**
 * Una entrada de la cola, como tarjeta pulsable.
 *
 * @param {object} entrada `EntradaDeCola` del contrato
 * @param {(id: string) => void} alAbrir
 * @returns {HTMLElement}
 */
export function tarjetaDeEntrada(entrada, alAbrir) {
  const comentario = entrada.comentario ?? {};
  const categorias = Object.entries(entrada.porCategoria ?? {})
    .sort((a, b) => b[1] - a[1])
    .map(([nombre, cuantos]) =>
      h('span', { clase: 'distintivo', texto: `${nombre.replaceAll('_', ' ')}: ${cuantos}` }),
    );

  const articulo = h('article', {
    clase: 'tarjeta pila pila--compacta',
    datos: { comentarioId: comentario.id ?? '' },
  });

  articulo.append(
    h('div', {
      clase: 'fila',
      hijos: [
        h('span', {
          clase: 'tarjeta__titulo',
          datos: { campo: 'apodo' },
          texto: comentario.apodoAutor ?? '',
        }),
        h('span', {
          clase: 'distintivo distintivo--reportado',
          datos: { campo: 'reportes' },
          texto: `${entrada.reportes ?? 0} reporte${entrada.reportes === 1 ? '' : 's'}`,
        }),
        h('span', {
          clase: 'tarjeta__meta',
          texto: `Esperando desde ${fechaHora(entrada.primerReporte)}`,
        }),
      ],
    }),
    h('p', { clase: 't-cuerpo', datos: { campo: 'texto' }, texto: comentario.texto ?? '' }),
    h('div', { clase: 'fila', hijos: categorias }),
    h('div', {
      clase: 'fila',
      hijos: [
        h('button', {
          clase: 'boton boton--secundario boton--pequeno',
          texto: 'Revisar',
          datos: { accion: 'revisar' },
          atributos: { type: 'button' },
        }),
      ],
    }),
  );

  articulo
    .querySelector('[data-accion="revisar"]')
    .addEventListener('click', () => alAbrir(comentario.id));
  return articulo;
}

/**
 * El detalle: el comentario, sus reportes, su historial y la decision.
 *
 * @param {object} detalle `DetalleDeModeracionResponse` del contrato
 * @param {(decision: {accion: string, motivo: string}) => void} alDecidir
 * @returns {HTMLElement}
 */
export function panelDeDetalle(detalle, alDecidir) {
  const comentario = detalle.comentario ?? {};
  const panel = h('section', {
    clase: 'tarjeta pila',
    datos: { zona: 'detalle', comentarioId: comentario.id ?? '' },
  });

  panel.append(
    h('h2', { texto: `Comentario de ${comentario.apodoAutor ?? 'alguien'}` }),
    h('p', {
      clase: 't-meta',
      hijos: [
        h('span', {
          clase: 'distintivo',
          datos: { campo: 'estado' },
          texto: comentario.estado ?? '',
        }),
      ],
    }),
    h('p', { clase: 't-cuerpo', datos: { campo: 'texto' }, texto: comentario.texto ?? '' }),
  );

  // --------------------------------------------------------------- reportes
  const reportes = detalle.reportes ?? [];
  panel.append(h('h3', { texto: `Reportes (${reportes.length})` }));
  panel.append(
    h('ul', {
      clase: 'pila pila--compacta',
      datos: { zona: 'reportes' },
      hijos: reportes.map((r) =>
        h('li', {
          clase: 't-meta',
          texto: `${String(r.categoria ?? '').replaceAll('_', ' ')} · ${fechaHora(r.fecha)}${
            r.descripcion ? ` · ${r.descripcion}` : ''
          }`,
        }),
      ),
    }),
  );

  // -------------------------------------------------------------- historial
  const historial = detalle.historial ?? [];
  panel.append(h('h3', { texto: 'Historial de decisiones' }));
  panel.append(
    historial.length === 0
      ? h('p', {
          clase: 't-meta',
          datos: { zona: 'historial' },
          texto: 'Todavía no se ha decidido nada.',
        })
      : h('ol', {
          clase: 'pila pila--compacta',
          datos: { zona: 'historial' },
          hijos: historial.map((a) =>
            h('li', {
              clase: 't-meta',
              texto: `${a.accion} por ${a.apodoModerador} (${a.estadoAnterior} → ${a.estadoNuevo}) · ${fechaHora(a.fecha)} · ${a.motivo}`,
            }),
          ),
        }),
  );

  // --------------------------------------------------------------- decision
  const posibles = accionesDesde(comentario.estado ?? '');
  if (posibles.length === 0) {
    panel.append(
      h('p', {
        clase: 't-meta',
        datos: { zona: 'sin-acciones' },
        texto: 'Este comentario ya no admite mas decisiones.',
      }),
    );
    return panel;
  }

  const seleccion = h('select', {
    clase: 'desplegable',
    atributos: { id: 'accion', name: 'accion' },
    hijos: posibles.map((a) => h('option', { texto: a.etiqueta, atributos: { value: a.valor } })),
  });
  const motivo = h('textarea', {
    clase: 'campo__control',
    atributos: { id: 'motivo', name: 'motivo', rows: 2 },
  });
  const confirmar = h('button', {
    clase: 'boton boton--primario',
    texto: 'REGISTRAR DECISION',
    datos: { accion: 'decidir' },
    atributos: { type: 'submit', disabled: true },
  });

  // El servicio ya exige motivo (400). Apagarlo aqui no es desconfianza: es
  // que enterarse por un error despues de pulsar es peor que verlo antes.
  motivo.addEventListener('input', () => {
    confirmar.disabled = motivo.value.trim().length === 0;
  });

  const formulario = h('form', {
    clase: 'pila pila--compacta',
    datos: { zona: 'decision' },
    atributos: { novalidate: true },
    hijos: [
      h('div', {
        clase: 'campo',
        hijos: [
          h('label', { clase: 'campo__etiqueta', texto: 'Decision', atributos: { for: 'accion' } }),
          seleccion,
        ],
      }),
      h('div', {
        clase: 'campo campo--area',
        hijos: [
          h('label', { clase: 'campo__etiqueta', texto: 'Motivo', atributos: { for: 'motivo' } }),
          motivo,
          h('p', {
            clase: 'campo__pista',
            texto: 'Obligatorio, también al aprobar: el autor lo recibe y queda en el asiento.',
          }),
        ],
      }),
      h('div', { clase: 'fila', hijos: [confirmar] }),
    ],
  });

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    alDecidir({ accion: seleccion.value, motivo: motivo.value.trim() });
  });

  panel.append(h('h3', { texto: 'Resolver' }), formulario);
  return panel;
}

/**
 * Monta la vista completa.
 *
 * @param {HTMLElement} raiz elemento con las zonas `cola`, `detalle` y `aviso`
 * @param {{api?: object, productoId?: string|null}} [opciones]
 *   `api` se inyecta en las pruebas; por omision es el cliente HTTP real.
 * @returns {{recargar: () => Promise<void>}}
 */
export function montarModeracion(raiz, { api = null, productoId = null } = {}) {
  const cliente = api ?? { consultarCola, consultarDetalle, resolverComentario };
  const zonaCola = raiz.querySelector('[data-zona="cola"]');
  const zonaDetalle = raiz.querySelector('[data-zona="detalle-contenedor"]');
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');

  function problema(error) {
    const explicacion = EXPLICACION[error?.motivo];
    pintarAviso(zonaAviso, {
      tono: 'error',
      titulo: explicacion?.titulo ?? error?.titulo ?? 'No se pudo completar la operación',
      detalle: explicacion?.detalle ?? error?.detalle ?? null,
    });
  }

  /**
   * Vuelve a pedir la cola.
   *
   * <p>{@code conservarAviso} no es un adorno: sin el, recargar despues de
   * una decision borraba el aviso que acababa de aparecer —«se oculto, se
   * aviso al autor» o «otro moderador se adelanto»— y el moderador se
   * quedaba sin saber que habia pasado. Lo destapo la prueba de la vista, no
   * una revision: en pantalla el aviso llegaba a pintarse y desaparecia en
   * el mismo instante.
   *
   * @param {{conservarAviso?: boolean}} [opciones]
   */
  async function recargar({ conservarAviso = false } = {}) {
    if (!conservarAviso) {
      limpiarAviso(zonaAviso);
    }
    vaciar(zonaDetalle);
    pintarEstado(zonaCola, estadoDeCarga({ filas: 3, etiqueta: 'Cargando la cola…' }));
    try {
      const cola = await cliente.consultarCola({ productoId, tamano: TAMANO });
      vaciar(zonaCola);
      if ((cola.entradas ?? []).length === 0) {
        // Cola vacia NO es un error: es la respuesta correcta cuando no hay
        // nada pendiente. Por eso estado vacio y no estado de error.
        pintarEstado(
          zonaCola,
          estadoVacio({
            titulo: 'No hay comentarios esperando revisión',
            detalle: 'Cuando alguien reporte uno, aparecerá aquí.',
          }),
        );
        return;
      }
      for (const entrada of cola.entradas) {
        zonaCola.append(tarjetaDeEntrada(entrada, abrir));
      }
    } catch (error) {
      vaciar(zonaCola);
      pintarEstado(
        zonaCola,
        estadoDeError({
          titulo:
            error?.estado === 403
              ? 'Esta pantalla es solo para moderación'
              : 'No se pudo cargar la cola',
          detalle:
            error?.estado === 403
              ? 'Tu cuenta no tiene permiso para revisar comentarios reportados.'
              : (error?.detalle ?? null),
        }),
      );
    }
  }

  async function abrir(comentarioId) {
    limpiarAviso(zonaAviso);
    vaciar(zonaDetalle);
    try {
      const detalle = await cliente.consultarDetalle(comentarioId);
      zonaDetalle.append(panelDeDetalle(detalle, (decision) => decidir(comentarioId, decision)));
    } catch (error) {
      problema(error);
    }
  }

  async function decidir(comentarioId, decision) {
    limpiarAviso(zonaAviso);
    try {
      const resuelto = await cliente.resolverComentario(comentarioId, decision);
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: `Comentario ${resuelto.comentario.estado.toLowerCase()}`,
        // Se dice si el aviso salio o no: el aviso es fail-open (HU-DIS-003),
        // asi que puede no haber salido y la decision valer igual. Callarlo
        // dejaria al moderador creyendo que el autor se entero.
        detalle: resuelto.autorNotificado
          ? 'Se aviso al autor con el motivo.'
          : 'La decision quedo registrada, pero el aviso al autor no salio.',
      });
      await recargar({ conservarAviso: true });
    } catch (error) {
      problema(error);
      if (error?.motivo === MOTIVO_MODERACION.TRANSICION_INVALIDA) {
        // La pantalla estaba vieja: se recarga, pero el aviso que lo explica
        // tiene que sobrevivir a la recarga o nadie se entera de por que
        // cambio la cola sola.
        await recargar({ conservarAviso: true });
      }
    }
  }

  recargar();
  return { recargar };
}
