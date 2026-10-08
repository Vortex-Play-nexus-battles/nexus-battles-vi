/**
 * La cola de moderacion de comentarios — RF-COM-005 y RF-COM-008, y desde B3
 * lo que 7.3.3 anade: editar el texto (con registro de la edicion) y marcar
 * comentarios para seguimiento especial (comentarios.yaml 1.5.0).
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
 * texto, sus imagenes, quien lo reporto y por que; mandarlo a otra pantalla
 * para ver los reportes y volver es como se acaba resolviendo sin leer.
 *
 * <b>El motivo es obligatorio y el boton esta apagado hasta que lo hay.</b>
 * El servicio ya lo exige (400 sin motivo, o con menos de 3 caracteres).
 * Repetirlo aqui no es desconfianza: es que enterarse por un error despues de
 * pulsar es peor que verlo antes. EDITAR pide ademas el texto nuevo.
 *
 * <b>Las acciones que se ofrecen dependen del estado y de la marca.</b> No se
 * pinta «Restaurar» sobre algo que esta en revision, ni «Marcar» sobre algo ya
 * marcado. La tabla que lo decide es un espejo del servicio y esta en
 * `cliente-moderacion.js`; si se separan, manda el servicio y la vista se
 * entera por el 409.
 *
 * <h2>Editar y marcar no cierran el caso</h2>
 *
 * EDITAR, MARCAR y DESMARCAR dejan el comentario en su estado: tras ellas el
 * detalle se vuelve a abrir, con el historial al dia, para que el moderador
 * siga (por ejemplo, editar y despues aprobar). MARCAR y DESMARCAR son una
 * nota interna: el autor no recibe aviso, y la pantalla lo dice.
 *
 * Nada se pinta con innerHTML: el texto de un comentario reportado es
 * exactamente el contenido del que hay que desconfiar.
 */

import { destinoVisible, urlDeVista } from '../../comun/acceso.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { pintarAviso, limpiarAviso } from '../../comun/ui/aviso.js';
import { conCarga } from '../../comun/ui/boton.js';
import { confirmarCritico } from '../../comun/ui/dialogo.js';
import { urlDeLogin } from '../../comun/sesion.js';
import {
  pintarEstado,
  estadoVacio,
  estadoDeError,
  estadoDeCarga,
} from '../../comun/ui/estado-vista.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { respaldoPorEstado } from '../../comun/ui/texto-de-fallo.js';
import { esIdDeImagen, textoAlternativo } from '../../comun/ui/comunidad/comentario.js';
import {
  accionesDesde,
  consultarCola,
  hayReportesPendientes,
  consultarDetalle,
  historialDelAutor,
  imagenParaModeracion,
  resolverComentario,
  resolverEnLote,
  ACCIONES_EN_LOTE,
  ACCIONES_INTERNAS,
  ACCIONES_SIN_CAMBIO_DE_ESTADO,
  CATEGORIAS,
  CONFIRMACION_DE_ELIMINAR,
  FILTROS_DE_COLA,
  FILTROS_DE_PRIORIDAD,
  MOTIVO_MAXIMO,
  MOTIVO_MINIMO,
  MOTIVO_MODERACION,
  TEXTO_NUEVO_MAXIMO,
} from './cliente-moderacion.js';

/** Cuantas entradas se piden de una vez. El servicio recorta a 100. */
const TAMANO = 20;

/**
 * Mensajes por `motivo` del problem detail. Nunca se compara el texto del
 * servidor: se compara el codigo estable que el contrato declara.
 */
const EXPLICACION = Object.freeze({
  [MOTIVO_MODERACION.TRANSICION_INVALIDA]: {
    titulo: 'Otro moderador se adelantó',
    detalle: 'El comentario ya no está como lo tenías en pantalla. Se recarga la cola.',
  },
});

/** Los estados del contrato, como se leen. */
export const ESTADO_LEGIBLE = Object.freeze({
  PUBLICADO: 'Publicado',
  EN_REVISION: 'En revisión',
  OCULTO: 'Oculto',
  ELIMINADO: 'Eliminado',
});

/** El titulo del aviso tras cada accion: lo que acaba de pasar, dicho. */
export const RESULTADO_DE_ACCION = Object.freeze({
  APROBAR: 'Comentario aprobado',
  OCULTAR: 'Comentario ocultado',
  ELIMINAR: 'Comentario eliminado',
  RESTAURAR: 'Comentario restaurado',
  EDITAR: 'Texto del comentario editado',
  MARCAR: 'Comentario marcado para seguimiento',
  DESMARCAR: 'Marca de seguimiento retirada',
});

/**
 * Los distintivos de un comentario: su estado (si no es el de la cola de
 * siempre), si esta marcado para seguimiento y si moderacion edito su texto.
 *
 * @param {object} comentario `ComentarioResponse` de moderacion
 * @param {{conEstado?: boolean, prioridadElevada?: boolean}} [opciones] `conEstado`: pintar
 *   tambien EN_REVISION; `prioridadElevada` (comentarios.yaml 1.6.0): solo `true` pinta el
 *   distintivo. Viene de la entrada de la cola, no del comentario: el detalle no la trae.
 * @returns {HTMLElement[]}
 */
export function distintivosDe(comentario, { conEstado = false, prioridadElevada = false } = {}) {
  const lista = [];
  if (prioridadElevada === true) {
    // Con texto y no solo con color: el tono ambar no basta para quien no lo distingue.
    lista.push(
      h('span', {
        clase: 'distintivo distintivo--aviso',
        datos: { campo: 'prioridad' },
        texto: 'Prioridad elevada',
      }),
    );
  }
  if (conEstado || comentario?.estado !== 'EN_REVISION') {
    lista.push(
      h('span', {
        clase: 'distintivo',
        datos: { campo: 'estado' },
        texto: ESTADO_LEGIBLE[comentario?.estado] ?? comentario?.estado ?? '',
      }),
    );
  }
  if (comentario?.marcado === true) {
    lista.push(
      h('span', {
        clase: 'distintivo distintivo--aviso',
        datos: { campo: 'marcado' },
        texto: 'Marcado para seguimiento',
      }),
    );
  }
  if (comentario?.editado === true) {
    lista.push(
      h('span', {
        clase: 'distintivo distintivo--moderador',
        datos: { campo: 'editado' },
        texto: 'Editado por moderación',
      }),
    );
  }
  return lista;
}

/**
 * HU-USR-010 (CA-02) — la dirección de la ficha administrativa del autor, por
 * su `uid` (lo único que la cola sabe de él), o `null` si este rol no puede
 * abrirla: la ficha es de administración (la matriz de acceso lo decide) y a
 * un moderador no se le ofrece una puerta cerrada.
 *
 * @param {string|null} rol
 * @returns {((autorId: string) => string)|null}
 */
export function enlazadorDeFicha(rol) {
  if (!destinoVisible('ficha-usuario', { autenticado: true, rol })) {
    return null;
  }
  return (autorId) => {
    const destino = new URL(urlDeVista('ficha-usuario'));
    destino.searchParams.set('usuario', autorId);
    return destino.href;
  };
}

/**
 * Una entrada de la cola, como tarjeta pulsable.
 *
 * @param {object} entrada `EntradaDeCola` del contrato
 * @param {(id: string) => void} alAbrir
 * @param {{hrefDeFicha?: ((autorId: string) => string)|null, seleccionable?: boolean,
 *          seleccionada?: boolean, alCambiarSeleccion?: ((marcada: boolean) => void)|null}} [opciones]
 *   HU-USR-010: con `hrefDeFicha`, «Ver ficha» junto al autor. HU-COM-005 (decisión en
 *   lote): con `seleccionable`, una casilla para elegir el comentario; sin ella, la
 *   tarjeta es la de siempre.
 * @returns {HTMLElement}
 */
export function tarjetaDeEntrada(
  entrada,
  alAbrir,
  {
    hrefDeFicha = null,
    seleccionable = false,
    seleccionada = false,
    alCambiarSeleccion = null,
  } = {},
) {
  const comentario = entrada.comentario ?? {};
  let casillaDeSeleccion = null;
  if (seleccionable) {
    const entradaDeCasilla = h('input', {
      clase: 'casilla__entrada',
      datos: { accion: 'seleccionar' },
      atributos: {
        type: 'checkbox',
        'aria-label': `Seleccionar el comentario de ${comentario.apodoAutor || 'su autor'}`,
      },
    });
    entradaDeCasilla.checked = seleccionada;
    // `click` y no `change`: cubre el ratón, la barra espaciadora y la etiqueta, y
    // además salta en una tarjeta que aún no está en el documento, donde un
    // checkbox cambia de valor pero no emite `change`.
    entradaDeCasilla.addEventListener('click', () =>
      alCambiarSeleccion?.(entradaDeCasilla.checked),
    );
    casillaDeSeleccion = h('label', {
      clase: 'casilla',
      hijos: [entradaDeCasilla, h('span', { clase: 'casilla__etiqueta', texto: 'Seleccionar' })],
    });
  }
  const fichaDelAutor =
    hrefDeFicha && typeof comentario.autorId === 'string' && comentario.autorId
      ? h('a', {
          texto: 'Ver ficha',
          datos: { accion: 'ver-ficha' },
          atributos: {
            href: hrefDeFicha(comentario.autorId),
            'aria-label': `Ver la ficha de ${comentario.apodoAutor || 'su autor'}`,
          },
        })
      : null;
  const categorias = Object.entries(entrada.porCategoria ?? {})
    .sort((a, b) => b[1] - a[1])
    .map(([nombre, cuantos]) =>
      h('span', { clase: 'distintivo', texto: `${nombre.replaceAll('_', ' ')}: ${cuantos}` }),
    );
  const reportes = entrada.reportes ?? 0;

  const articulo = h('article', {
    clase: 'tarjeta pila pila--compacta',
    datos: { comentarioId: comentario.id ?? '' },
  });

  articulo.append(
    // `fila--envuelta` y no `fila`: los distintivos miden lo que dicen. Con
    // el reparto a partes iguales de `.fila`, cada pildora se estiraba hasta
    // parecer una barra (el defecto que ya describe el kit).
    h('div', {
      clase: 'fila fila--envuelta',
      hijos: [
        casillaDeSeleccion,
        h('span', {
          clase: 'tarjeta__titulo',
          datos: { campo: 'apodo' },
          texto: comentario.apodoAutor ?? '',
        }),
        fichaDelAutor,
        h('span', {
          clase: 'distintivo distintivo--reportado',
          datos: { campo: 'reportes' },
          texto: `${reportes} reporte${reportes === 1 ? '' : 's'}`,
        }),
        ...distintivosDe(comentario, { prioridadElevada: entrada.prioridadElevada }),
        h('span', {
          clase: 'tarjeta__meta',
          // En la lista de seguimiento hay marcados sin ningun reporte: para
          // esos, «esperando» no dice nada; la fecha que cuenta es la suya.
          texto:
            reportes > 0
              ? `Esperando desde ${fechaHora(entrada.primerReporte)}`
              : `Publicado el ${fechaHora(comentario.fechaPublicacion ?? entrada.primerReporte)}`,
        }),
      ],
    }),
    h('p', { clase: 't-cuerpo', datos: { campo: 'texto' }, texto: comentario.texto ?? '' }),
    h('div', { clase: 'fila fila--envuelta', hijos: categorias }),
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
 * Las imagenes del comentario, pedidas con la sesion del moderador: las de
 * un comentario en revision no son publicas (comentarios.yaml 1.5.0).
 *
 * @param {object} comentario
 * @param {{cargarImagen: ((id: string) => Promise<Blob|null>)|null, crearUrl: (blob: Blob) => string}} opciones
 * @returns {HTMLElement|null}
 */
function imagenesDelComentario(comentario, { cargarImagen, crearUrl }) {
  const lista = (Array.isArray(comentario?.imagenes) ? comentario.imagenes : []).filter(
    (valor) => typeof valor === 'string' && valor.trim() !== '',
  );
  if (lista.length === 0) {
    return null;
  }
  const autor = comentario?.apodoAutor || 'un jugador';
  const elementos = lista.map((valor, indice) => {
    const item = h('li', { clase: 'moderacion-imagenes__elemento', datos: { imagen: valor } });
    if (!esIdDeImagen(valor) || !cargarImagen) {
      // Un nombre de archivo de antes de la 1.4.0: no hay imagen detras.
      item.append(h('span', { clase: 't-meta', texto: valor }));
      return item;
    }
    item.append(h('span', { clase: 't-meta', texto: 'Cargando imagen…' }));
    cargarImagen(valor).then(
      (blob) => {
        if (!blob) {
          item.replaceChildren(h('span', { clase: 't-meta', texto: 'Imagen no disponible' }));
          return;
        }
        const url = crearUrl(blob);
        const imagen = h('img', {
          clase: 'moderacion-imagenes__imagen',
          atributos: { src: url, alt: textoAlternativo(indice, lista.length, autor) },
        });
        imagen.addEventListener('load', () => globalThis.URL?.revokeObjectURL?.(url), {
          once: true,
        });
        item.replaceChildren(imagen);
      },
      () => {
        item.replaceChildren(h('span', { clase: 't-meta', texto: 'Imagen no disponible' }));
      },
    );
    return item;
  });
  return h('ul', {
    clase: 'moderacion-imagenes',
    datos: { zona: 'imagenes' },
    atributos: {
      'aria-label': lista.length === 1 ? 'Imagen adjunta' : `${lista.length} imágenes adjuntas`,
    },
    hijos: elementos,
  });
}

/** Una linea del historial, legible. EDITAR dice que texto habia y cual queda. */
function lineaDelHistorial(asiento) {
  const base = `${asiento.accion} por ${asiento.apodoModerador} (${asiento.estadoAnterior} → ${asiento.estadoNuevo}) · ${fechaHora(asiento.fecha)} · ${asiento.motivo}`;
  if (asiento.accion === 'EDITAR' && (asiento.textoAnterior || asiento.textoNuevo)) {
    return `${base} · Antes: «${asiento.textoAnterior ?? ''}» · Ahora: «${asiento.textoNuevo ?? ''}»`;
  }
  return base;
}

/** Un comentario del autor en su historial: texto, fecha, producto y estado con palabras. */
function itemDelHistorial(item, comentarioAbiertoId) {
  return h('li', {
    clase: 'pila pila--compacta',
    datos: { comentarioId: item.id ?? '' },
    hijos: [
      h('div', {
        clase: 'fila fila--envuelta',
        hijos: [
          // El estado y «editado» van con texto, no solo con color.
          ...distintivosDe(item, { conEstado: true }),
          h('span', {
            clase: 't-meta',
            datos: { campo: 'fecha' },
            texto: fechaHora(item.fechaPublicacion),
          }),
          // El contrato solo trae el identificador del producto, no su nombre.
          h('span', {
            clase: 't-meta',
            datos: { campo: 'producto' },
            texto: `Producto: ${item.productoId ?? ''}`,
          }),
          item.id === comentarioAbiertoId
            ? h('span', {
                clase: 't-meta',
                datos: { campo: 'abierto' },
                texto: 'Este es el comentario abierto',
              })
            : null,
        ],
      }),
      h('p', { clase: 't-cuerpo', datos: { campo: 'texto' }, texto: item.texto ?? '' }),
    ],
  });
}

/**
 * Otros comentarios del autor, bajo demanda (HU-COM-005): abrir el detalle no
 * los pide, porque son contexto secundario y pueden ser muchos; un fallo de
 * esta parte se queda en esta parte y el resto del detalle sigue operativo.
 *
 * Los errores se dicen con el texto del kit (`ErrorDeApi.detalle`), sin el
 * numero del estado. Reintentar solo se ofrece donde puede servir: un 401, un
 * 403 o un 404 no cambian pulsando otra vez (`shared/ui-kit/MAPEO-ERRORES.md`).
 *
 * @param {object} comentario el comentario abierto (`autorId`, `id`)
 * @param {(autorId: string, paginacion: {pagina: number}) => Promise<object>} cargar
 * @returns {HTMLElement}
 */
function seccionHistorialDelAutor(comentario, cargar) {
  let pagina = 0;
  let pintados = 0;
  let total = 0;
  const peticion = { activa: false };
  let lista = null;

  const boton = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Ver historial del autor',
    datos: { accion: 'ver-historial-autor' },
    atributos: { type: 'button', 'aria-expanded': 'false' },
  });
  const estado = h('div', {
    datos: { zona: 'historial-autor-estado' },
    atributos: { hidden: true },
  });
  const aviso = h('div', { datos: { zona: 'historial-autor-aviso' }, atributos: { hidden: true } });
  const cuerpo = h('div', { clase: 'pila pila--compacta' });
  const pie = h('div', { clase: 'fila' });
  const verMas = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Ver más',
    datos: { accion: 'ver-mas' },
    atributos: { type: 'button' },
  });

  function problema(error, paginaPedida) {
    const estadoHttp = error?.estado;
    const sinCambio = [401, 403, 404].includes(estadoHttp);
    /** @type {"advertencia"|"info"|"error"} */
    let tono = 'advertencia';
    if (estadoHttp === 404) {
      tono = 'info';
    } else if (!sinCambio) {
      tono = 'error';
    }
    pintarAviso(aviso, {
      tono,
      titulo: 'No se pudo cargar el historial del autor',
      detalle: error?.detalle ?? respaldoPorEstado(estadoHttp),
      accion: sinCambio
        ? null
        : { texto: 'Reintentar', nombre: 'reintentar', alPulsar: () => void pedir(paginaPedida) },
    });
  }

  function cerrarPeticion() {
    peticion.activa = false;
    if (pintados < total && aviso.hidden) {
      pie.append(verMas);
    }
  }

  async function pedir(paginaPedida) {
    if (peticion.activa) {
      return;
    }
    peticion.activa = true;
    verMas.remove();
    limpiarAviso(aviso);
    if (paginaPedida === 0) {
      pintarEstado(estado, estadoDeCarga({ filas: 2, etiqueta: 'Cargando el historial…' }));
    }
    try {
      const respuesta = await cargar(comentario.autorId, { pagina: paginaPedida });
      vaciar(estado);
      estado.hidden = true;
      total = respuesta.total ?? 0;
      const items = respuesta.comentarios ?? [];
      if (total === 0 && pintados === 0) {
        pintarEstado(
          estado,
          estadoVacio({
            titulo: 'Este autor no tiene comentarios',
            detalle: 'Cuando publique alguno, aparecerá aquí.',
          }),
        );
        return;
      }
      if (!lista) {
        lista = h('ol', { clase: 'pila pila--compacta', datos: { zona: 'historial-autor-lista' } });
        cuerpo.append(lista);
      }
      lista.append(...items.map((item) => itemDelHistorial(item, comentario.id)));
      pintados += items.length;
      pagina = paginaPedida + 1;
    } catch (error) {
      vaciar(estado);
      estado.hidden = true;
      problema(error, paginaPedida);
    } finally {
      cerrarPeticion();
    }
  }

  boton.addEventListener('click', () => {
    boton.disabled = true;
    boton.setAttribute('aria-expanded', 'true');
    void pedir(0);
  });
  verMas.addEventListener('click', () => void pedir(pagina));

  return h('div', {
    clase: 'pila pila--compacta',
    datos: { zona: 'historial-autor' },
    hijos: [h('h3', { texto: 'Otros comentarios del autor' }), boton, estado, aviso, cuerpo, pie],
  });
}

/** La pista del motivo: a quien le llega depende de la accion. */
function pistaDelMotivo(accion) {
  return ACCIONES_INTERNAS.includes(accion)
    ? `Obligatorio, de ${MOTIVO_MINIMO} a ${MOTIVO_MAXIMO} caracteres. Es una nota interna: el autor no recibe aviso, y queda en el historial.`
    : `Obligatorio, de ${MOTIVO_MINIMO} a ${MOTIVO_MAXIMO} caracteres, también al aprobar: el autor lo recibe y queda en el historial.`;
}

/**
 * El detalle: el comentario, sus imagenes, sus reportes, su historial y la
 * decision.
 *
 * @param {object} detalle `DetalleDeModeracionResponse` del contrato
 * @param {(decision: {accion: string, motivo: string, textoNuevo?: string}) => void} alDecidir
 * @param {{cargarImagen?: ((id: string) => Promise<Blob|null>)|null,
 *          crearUrl?: (blob: Blob) => string,
 *          prioridadElevada?: boolean,
 *          cargarHistorialDelAutor?: ((autorId: string, paginacion: {pagina: number}) => Promise<object>)|null}} [opciones]
 *   `prioridadElevada` sale de la entrada
 *   de la cola: `DetalleDeModeracionResponse` no la trae.
 * @returns {HTMLElement}
 */
export function panelDeDetalle(
  detalle,
  alDecidir,
  {
    cargarImagen = null,
    crearUrl = (blob) => globalThis.URL?.createObjectURL?.(blob) ?? '',
    prioridadElevada = false,
    cargarHistorialDelAutor = null,
  } = {},
) {
  const comentario = detalle.comentario ?? {};
  const panel = h('section', {
    clase: 'tarjeta pila',
    datos: { zona: 'detalle', comentarioId: comentario.id ?? '' },
  });

  panel.append(
    h('h2', { texto: `Comentario de ${comentario.apodoAutor ?? 'alguien'}` }),
    h('p', {
      clase: 't-meta fila fila--envuelta',
      hijos: distintivosDe(comentario, { conEstado: true, prioridadElevada }),
    }),
    h('p', { clase: 't-cuerpo', datos: { campo: 'texto' }, texto: comentario.texto ?? '' }),
  );
  const imagenes = imagenesDelComentario(comentario, { cargarImagen, crearUrl });
  if (imagenes) {
    panel.append(h('h3', { texto: 'Imágenes' }), imagenes);
  }

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
          hijos: historial.map((a) => h('li', { clase: 't-meta', texto: lineaDelHistorial(a) })),
        }),
  );

  // ---------------------------------------------------- historial del autor
  if (cargarHistorialDelAutor && typeof comentario.autorId === 'string' && comentario.autorId) {
    panel.append(seccionHistorialDelAutor(comentario, cargarHistorialDelAutor));
  }

  // --------------------------------------------------------------- decision
  // 1.8.0: uno PUBLICADO con reportes pendientes sigue a la vista y se puede
  // aprobar (cierra sus reportes) ademas de ocultar, eliminar o editar.
  const posibles = accionesDesde(comentario.estado ?? '', comentario.marcado === true, {
    reportesPendientes: hayReportesPendientes(detalle),
  });
  if (posibles.length === 0) {
    panel.append(
      h('p', {
        clase: 't-meta',
        datos: { zona: 'sin-acciones' },
        texto: 'Este comentario ya no admite más decisiones.',
      }),
    );
    return panel;
  }

  const seleccion = h('select', {
    clase: 'desplegable__control',
    atributos: { id: 'accion', name: 'accion' },
    hijos: posibles.map((a) => h('option', { texto: a.etiqueta, atributos: { value: a.valor } })),
  });

  // EDITAR: el texto que quedara visible, empezando por el que hay.
  const textoNuevo = h('textarea', {
    clase: 'campo__control',
    atributos: {
      id: 'texto-nuevo',
      name: 'textoNuevo',
      rows: 3,
      maxlength: TEXTO_NUEVO_MAXIMO,
      'aria-describedby': 'texto-nuevo-pista',
    },
  });
  textoNuevo.value = comentario.texto ?? '';
  const campoTextoNuevo = h('div', {
    clase: 'campo campo--area',
    datos: { zona: 'texto-nuevo' },
    hijos: [
      h('label', {
        clase: 'campo__etiqueta',
        texto: 'Texto nuevo',
        atributos: { for: 'texto-nuevo' },
      }),
      textoNuevo,
      h('p', {
        clase: 'campo__pista',
        texto:
          'Es el texto que quedará visible, marcado como editado por moderación. El anterior queda en el historial y el autor recibe aviso.',
        atributos: { id: 'texto-nuevo-pista' },
      }),
    ],
  });

  const motivo = h('textarea', {
    clase: 'campo__control',
    atributos: {
      id: 'motivo',
      name: 'motivo',
      rows: 2,
      maxlength: MOTIVO_MAXIMO,
      'aria-describedby': 'motivo-pista',
    },
  });
  const pista = h('p', { clase: 'campo__pista', atributos: { id: 'motivo-pista' } });
  const confirmar = h('button', {
    clase: 'boton boton--primario',
    texto: 'Registrar decisión',
    datos: { accion: 'decidir' },
    atributos: { type: 'submit', disabled: true },
  });

  const editando = () => seleccion.value === 'EDITAR';
  // El servicio ya exige motivo (400) y, con EDITAR, texto nuevo. Apagar el
  // boton aqui no es desconfianza: es que enterarse por un error despues de
  // pulsar es peor que verlo antes.
  const actualizar = () => {
    campoTextoNuevo.hidden = !editando();
    pista.textContent = pistaDelMotivo(seleccion.value);
    const conMotivo = motivo.value.trim().length >= MOTIVO_MINIMO;
    const conTexto = !editando() || textoNuevo.value.trim().length > 0;
    confirmar.disabled = !(conMotivo && conTexto);
  };
  motivo.addEventListener('input', actualizar);
  textoNuevo.addEventListener('input', actualizar);
  seleccion.addEventListener('change', actualizar);

  const formulario = h('form', {
    clase: 'pila pila--compacta',
    datos: { zona: 'decision' },
    atributos: { novalidate: true },
    hijos: [
      h('div', {
        clase: 'campo',
        hijos: [
          h('label', { clase: 'campo__etiqueta', texto: 'Decisión', atributos: { for: 'accion' } }),
          seleccion,
        ],
      }),
      campoTextoNuevo,
      h('div', {
        clase: 'campo campo--area',
        hijos: [
          h('label', { clase: 'campo__etiqueta', texto: 'Motivo', atributos: { for: 'motivo' } }),
          motivo,
          pista,
        ],
      }),
      h('div', { clase: 'fila', hijos: [confirmar] }),
    ],
  });
  actualizar();

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const decision = { accion: seleccion.value, motivo: motivo.value.trim() };
    if (editando()) {
      decision.textoNuevo = textoNuevo.value.trim();
    }
    alDecidir(decision);
  });

  panel.append(h('h3', { texto: 'Resolver' }), formulario);
  return panel;
}

// ----------------------------------------------------- decision en lote
// HU-COM-005 (#519), comentarios.yaml 1.10.1. El servicio es atomico: o se
// resuelven todos los comentarios del lote o no cambia ninguno.

/** El titulo del aviso de exito de un lote, por accion. */
export const RESULTADO_DE_LOTE = Object.freeze({
  APROBAR: 'Comentarios aprobados',
  OCULTAR: 'Comentarios ocultados',
  ELIMINAR: 'Comentarios eliminados',
  RESTAURAR: 'Comentarios restaurados',
  MARCAR: 'Comentarios marcados para seguimiento',
  DESMARCAR: 'Marca de seguimiento retirada de los comentarios',
});

/** Como se nombra, en una lista, un comentario que la cola ya no tiene delante. */
const SIN_NOMBRE = 'un comentario que ya no está en la cola';

/** Cuanto del texto de un comentario se enseña para distinguirlo de otro del mismo autor. */
const LARGO_DEL_EXTRACTO = 40;

/** Autor y comienzo del texto: lo que la persona reconoce, nunca un identificador. */
function nombreDeEntrada(entrada) {
  const comentario = entrada?.comentario ?? {};
  const texto = String(comentario.texto ?? '').trim();
  const extracto =
    texto.length > LARGO_DEL_EXTRACTO ? `${texto.slice(0, LARGO_DEL_EXTRACTO).trimEnd()}…` : texto;
  const autor = comentario.apodoAutor || 'Un jugador';
  return extracto ? `${autor} · «${extracto}»` : autor;
}

/**
 * Que decir ante el rechazo de un lote. Se decide por `estado` y `motivo`, nunca
 * por el texto del servidor: el `detail` de este contrato lleva identificadores y
 * nombres de enum, y puede traer una cifra. Es una funcion pura: devuelve QUE
 * mostrar y la vista decide COMO (el aviso, la lista, la recarga, el foco).
 *
 * @param {{estado?: number, motivo?: string|null, comentarioIds?: string[]}|unknown} error
 * @param {(id: string) => string|null} [nombrePorId] nombre legible de un comentario de
 *   la cola, o `null` si ya no esta
 * @returns {{tono: 'error'|'advertencia'|'info', titulo: string, detalle: string,
 *   afectados: string[], quitar: string[], recargar: boolean, apagarBarra: boolean,
 *   accion: {texto: string, nombre: 'iniciar-sesion'|'actualizar-cola'}|null}}
 */
export function mensajeDelLote(error, nombrePorId = () => null) {
  const estado = error?.estado;
  const ids = Array.isArray(error?.comentarioIds) ? error.comentarioIds : [];
  const base = {
    afectados: [],
    quitar: [],
    recargar: false,
    apagarBarra: false,
    accion: null,
  };

  if (estado === 400 && error?.motivo === MOTIVO_MODERACION.CONFIRMACION_REQUERIDA) {
    return {
      ...base,
      tono: 'advertencia',
      titulo: 'Falta la confirmación',
      detalle: `Escribe ${CONFIRMACION_DE_ELIMINAR} tal cual para eliminar varios comentarios. No se cambió nada.`,
    };
  }
  if (estado === 400) {
    return {
      ...base,
      tono: 'advertencia',
      titulo: 'Revisa la selección y el motivo',
      detalle: 'No se cambió nada.',
    };
  }
  if (estado === 401) {
    return {
      ...base,
      tono: 'advertencia',
      titulo: 'Tu sesión ya no es válida',
      detalle: 'Vuelve a entrar para aplicar la decisión. Tu selección y tu motivo siguen aquí.',
      accion: { texto: 'Iniciar sesión', nombre: 'iniciar-sesion' },
    };
  }
  if (estado === 403) {
    return {
      ...base,
      tono: 'advertencia',
      titulo: 'Tu cuenta no puede resolver comentarios',
      detalle: 'La decisión no se aplicó.',
      apagarBarra: true,
    };
  }
  if (estado === 404) {
    return {
      ...base,
      tono: 'info',
      titulo: 'Algunos comentarios ya no están',
      detalle: 'No se cambió ningún comentario del lote.',
      afectados: ids.map((id) => nombrePorId(id) ?? SIN_NOMBRE),
      quitar: ids,
      recargar: true,
    };
  }
  if (estado === 409) {
    return {
      ...base,
      tono: 'advertencia',
      titulo: 'Otro moderador se adelantó',
      detalle:
        'Estos comentarios ya no admiten esa decisión y no se cambió ninguno del lote. Se actualiza la cola.',
      afectados: ids.map((id) => nombrePorId(id) ?? SIN_NOMBRE),
      quitar: ids,
      recargar: true,
    };
  }
  // Red caida o fallo del servidor: no se sabe si la decision llego a aplicarse,
  // asi que no se promete que no cambio nada.
  return {
    ...base,
    tono: 'error',
    titulo: 'No se pudo aplicar la decisión',
    detalle: 'Actualiza la cola para ver cómo quedó antes de volver a intentarlo.',
    accion: { texto: 'Actualizar la cola', nombre: 'actualizar-cola' },
  };
}

/**
 * Lo que se dice del aviso a los autores tras un lote. El aviso es fail-open
 * (HU-DIS-003): la decision vale aunque el aviso no salga, y callarlo dejaria al
 * moderador creyendo que los autores se enteraron.
 *
 * @param {string} accion
 * @param {Array<{autorNotificado?: boolean}>} resultados
 * @returns {string}
 */
function detalleDelAviso(accion, resultados) {
  if (ACCIONES_INTERNAS.includes(accion)) {
    return 'Es una nota interna: los autores no reciben aviso. Queda en el historial.';
  }
  const sinAviso = resultados.filter((r) => r?.autorNotificado !== true).length;
  if (sinAviso === 0) {
    return 'Se avisó a los autores con el motivo.';
  }
  if (sinAviso === resultados.length) {
    return 'La decisión quedó registrada, pero el aviso a los autores no salió.';
  }
  return 'La decisión quedó registrada, pero el aviso no salió para algunos autores.';
}

/**
 * La barra del lote: seleccion de la pagina, contador, decision y motivo. Se
 * construye una vez; `montarModeracion` la mantiene al dia.
 *
 * @returns {{elemento: HTMLElement, maestra: HTMLInputElement, limpiar: HTMLButtonElement,
 *   contador: HTMLElement, nota: HTMLElement, formulario: HTMLFormElement,
 *   accion: HTMLSelectElement, motivo: HTMLTextAreaElement, pista: HTMLElement,
 *   ayuda: HTMLElement, aplicar: HTMLButtonElement}}
 */
function construirBarraDeLote() {
  const maestra = h('input', {
    clase: 'casilla__entrada',
    datos: { accion: 'seleccionar-pagina' },
    atributos: { type: 'checkbox' },
  });
  const limpiar = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Limpiar selección',
    datos: { accion: 'limpiar-seleccion' },
    atributos: { type: 'button', disabled: true },
  });
  const contador = h('p', {
    clase: 't-meta',
    datos: { zona: 'contador-lote' },
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  const nota = h('p', {
    clase: 't-meta',
    texto:
      'La cola tiene más comentarios de los que ves; la decisión solo alcanza a los que seleccionaste.',
    datos: { zona: 'nota-lote' },
    atributos: { hidden: true },
  });

  const accion = h('select', {
    clase: 'desplegable__control',
    atributos: { id: 'accion-lote', name: 'accion' },
    hijos: ACCIONES_EN_LOTE.map((a) =>
      h('option', { texto: a.etiqueta, atributos: { value: a.valor } }),
    ),
  });
  const motivo = h('textarea', {
    clase: 'campo__control',
    atributos: {
      id: 'motivo-lote',
      name: 'motivo',
      rows: 2,
      maxlength: MOTIVO_MAXIMO,
      'aria-describedby': 'motivo-lote-pista',
    },
  });
  const pista = h('p', { clase: 'campo__pista', atributos: { id: 'motivo-lote-pista' } });
  const ayuda = h('p', {
    clase: 'campo__pista',
    texto: 'Elige al menos un comentario y escribe un motivo para aplicar la decisión.',
    atributos: { id: 'aplicar-lote-ayuda' },
  });
  const aplicar = h('button', {
    clase: 'boton boton--primario',
    texto: 'Aplicar a los seleccionados',
    datos: { accion: 'aplicar-lote' },
    atributos: { type: 'submit', disabled: true, 'aria-describedby': 'aplicar-lote-ayuda' },
  });

  const formulario = h('form', {
    clase: 'pila pila--compacta',
    datos: { zona: 'decision-lote' },
    atributos: { novalidate: true },
    hijos: [
      h('div', {
        clase: 'campo',
        hijos: [
          h('label', {
            clase: 'campo__etiqueta',
            texto: 'Decisión para los seleccionados',
            atributos: { for: 'accion-lote' },
          }),
          accion,
        ],
      }),
      h('div', {
        clase: 'campo campo--area',
        hijos: [
          h('label', {
            clase: 'campo__etiqueta',
            texto: 'Motivo',
            atributos: { for: 'motivo-lote' },
          }),
          motivo,
          pista,
        ],
      }),
      ayuda,
      h('div', { clase: 'fila', hijos: [aplicar] }),
    ],
  });

  const elemento = h('section', {
    clase: 'pila pila--compacta',
    atributos: { 'aria-labelledby': 'titulo-lote' },
    hijos: [
      h('h3', { texto: 'Decisión en lote', atributos: { id: 'titulo-lote' } }),
      h('div', {
        clase: 'fila fila--envuelta',
        hijos: [
          h('label', {
            clase: 'casilla',
            hijos: [
              maestra,
              h('span', { clase: 'casilla__etiqueta', texto: 'Seleccionar los de esta página' }),
            ],
          }),
          limpiar,
          contador,
        ],
      }),
      nota,
      formulario,
    ],
  });

  return {
    elemento,
    maestra,
    limpiar,
    contador,
    nota,
    formulario,
    accion,
    motivo,
    pista,
    ayuda,
    aplicar,
  };
}

/**
 * El filtro de la cola que esta elegido.
 *
 * @param {HTMLSelectElement|null} filtro
 * @returns {{valor: string, etiqueta: string, marcado: boolean|null}}
 */
function filtroElegido(filtro) {
  return FILTROS_DE_COLA.find((f) => f.valor === filtro?.value) ?? FILTROS_DE_COLA[0];
}

/**
 * El filtro de prioridad que esta elegido (1.10.0); sin el control, «Todas».
 *
 * @param {HTMLSelectElement|null} filtro
 * @returns {{valor: string, etiqueta: string, prioridadElevada: boolean|null}}
 */
function prioridadElegida(filtro) {
  return FILTROS_DE_PRIORIDAD.find((f) => f.valor === filtro?.value) ?? FILTROS_DE_PRIORIDAD[0];
}

/**
 * Anade las opciones de un select que el HTML trae solo con «Todas». Si ya trae
 * mas, no las duplica: montar dos veces la vista no debe repetir la lista.
 *
 * @param {HTMLSelectElement|null} select
 * @param {Array<{valor: string, etiqueta: string}>} opciones
 */
function llenarOpciones(select, opciones) {
  if (!select || select.options.length > 1) {
    return;
  }
  select.append(
    ...opciones.map(({ valor, etiqueta }) =>
      h('option', { texto: etiqueta, atributos: { value: valor } }),
    ),
  );
}

/**
 * La cola vacia porque los filtros no coinciden con nada. NO es una cola sin
 * trabajo ni un error: no dice que no haya nada pendiente, ni da ninguna
 * explicacion de por que, y ofrece quitar los filtros.
 *
 * @param {{categoria: string|null, prioridadElevada: boolean|null}} filtros
 * @param {() => void} alQuitar
 * @returns {HTMLElement}
 */
function vacioPorFiltros({ categoria, prioridadElevada }, alQuitar) {
  const soloPrioridad = prioridadElevada === true && !categoria;
  return estadoVacio({
    titulo: 'Ningún comentario coincide con los filtros',
    detalle: soloPrioridad
      ? 'En este momento ningún comentario de la cola tiene prioridad elevada.'
      : 'Prueba con otros filtros o quítalos para ver todo lo pendiente.',
    accion: { texto: 'Quitar filtros', nombre: 'quitar-filtros', alPulsar: alQuitar },
  });
}

/**
 * El estado vacio de la cola, segun por que esta vacia. «Marcado» elige que cola
 * se mira y tiene sus propios vacios; solo categoria y prioridad son filtros que
 * pueden dejarla sin nada, y con ellos manda el mensaje de los filtros.
 *
 * @param {{marcado: boolean|null, categoria: string|null, prioridadElevada: boolean|null}} elegido
 * @param {() => void} alQuitarFiltros
 * @returns {HTMLElement}
 */
function vacioDeLaCola({ marcado, categoria, prioridadElevada }, alQuitarFiltros) {
  if (categoria !== null || prioridadElevada !== null) {
    return vacioPorFiltros({ categoria, prioridadElevada }, alQuitarFiltros);
  }
  if (marcado === true) {
    return estadoVacio({
      titulo: 'No hay comentarios marcados para seguimiento',
      detalle: 'Cuando marques uno desde su detalle, aparecerá aquí.',
    });
  }
  return estadoVacio({
    titulo: 'No hay comentarios esperando revisión',
    detalle: 'Cuando alguien reporte uno, aparecerá aquí.',
  });
}

/**
 * Monta la vista completa.
 *
 * @param {HTMLElement} raiz elemento con las zonas `cola`, `detalle` y `aviso`
 *   (y, si los trae, los filtros `[data-zona="filtro"]`, `"filtro-categoria"` y
 *   `"filtro-prioridad"`; los dos ultimos traen solo «Todas» y la vista anade el resto)
 * @param {{api?: object, productoId?: string|null, crearUrl?: (blob: Blob) => string,
 *          rol?: string|null}} [opciones]
 *   `api` se inyecta en las pruebas; por omision es el cliente HTTP real. `rol`
 *   (HU-USR-010) decide si cada autor lleva «Ver ficha».
 * @returns {{recargar: () => Promise<void>}}
 */
export function montarModeracion(
  raiz,
  { api = null, productoId = null, crearUrl, rol = null } = {},
) {
  const hrefDeFicha = enlazadorDeFicha(rol);
  const cliente = {
    consultarCola,
    consultarDetalle,
    resolverComentario,
    imagenParaModeracion,
    historialDelAutor,
    resolverEnLote,
    ...(api ?? {}),
  };
  const zonaCola = raiz.querySelector('[data-zona="cola"]');
  const zonaDetalle = raiz.querySelector('[data-zona="detalle-contenedor"]');
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const filtro = raiz.querySelector('[data-zona="filtro"]');
  // 1.10.0: categoria de reporte y prioridad. Se combinan con `filtro` (marcado).
  const filtroCategoria = raiz.querySelector('[data-zona="filtro-categoria"]');
  const filtroPrioridad = raiz.querySelector('[data-zona="filtro-prioridad"]');
  llenarOpciones(filtroCategoria, CATEGORIAS);
  llenarOpciones(filtroPrioridad, FILTROS_DE_PRIORIDAD.slice(1));
  // Cada recarga lleva un numero: la respuesta de una anterior que llega tarde no
  // pinta la cola (con tres filtros y el teclado, los cambios seguidos son normales).
  let generacion = 0;
  // El detalle no trae `prioridadElevada`: se recuerda de la entrada de la
  // cola desde la que se abrio. `recargar` lo rehace antes de reabrir.
  const prioridadPorComentario = new Map();

  // ------------------------------------------------------- decision en lote
  // Sin la zona `lote` en el HTML la vista funciona como siempre: sin casillas.
  const lote = raiz.querySelector('[data-zona="lote"]');
  const barra = lote ? construirBarraDeLote() : null;
  /** Los comentarios elegidos. Siempre un subconjunto de los que hay en pantalla. */
  const seleccion = new Set();
  /** Las entradas de la cola que se ven, por id y en el orden de la cola. */
  let entradasPorId = new Map();
  /**
   * `enVuelo`: mientras hay un envio (o su confirmacion) en curso no se acepta otro.
   * `apagada`: un 403, la cuenta no puede decidir; la barra se apaga hasta recargar
   * la pagina. Es un objeto y no dos variables porque se escribe despues de un
   * `await` (la regla `require-atomic-updates` lo exige asi).
   */
  const bloqueo = { enVuelo: false, apagada: false };
  if (lote && barra) {
    lote.hidden = true;
    lote.append(barra.elemento);
  }

  /** Se escribe desde aqui y no despues de un `await`: `require-atomic-updates`. */
  function marcarEnvio(enCurso) {
    bloqueo.enVuelo = enCurso;
  }

  /** Pone al dia todo lo que depende de la seleccion: contador, casillas y botones. */
  function actualizarBarra() {
    if (!barra) {
      return;
    }
    const elegidos = seleccion.size;
    const visibles = entradasPorId.size;
    const bloqueada = bloqueo.enVuelo || bloqueo.apagada;
    const completa = elegidos > 0 && barra.motivo.value.trim().length >= MOTIVO_MINIMO;

    if (elegidos === 0) {
      barra.contador.textContent = 'Ninguno seleccionado';
    } else if (elegidos === 1) {
      barra.contador.textContent = '1 seleccionado';
    } else {
      barra.contador.textContent = `${elegidos} seleccionados`;
    }
    barra.maestra.checked = visibles > 0 && elegidos === visibles;
    barra.maestra.indeterminate = elegidos > 0 && elegidos < visibles;
    barra.maestra.disabled = bloqueada;
    barra.limpiar.disabled = bloqueada || elegidos === 0;
    barra.accion.disabled = bloqueada;
    barra.motivo.disabled = bloqueada;
    barra.pista.textContent = pistaDelMotivo(barra.accion.value);
    barra.aplicar.disabled = bloqueada || !completa;
    barra.ayuda.hidden = bloqueada || completa;
    for (const caja of zonaCola.querySelectorAll('input[data-accion="seleccionar"]')) {
      caja.checked = seleccion.has(caja.closest('article')?.dataset.comentarioId);
      caja.disabled = bloqueada;
    }
  }

  function cambiarSeleccion(comentarioId, marcada) {
    if (marcada) {
      seleccion.add(comentarioId);
    } else {
      seleccion.delete(comentarioId);
    }
    actualizarBarra();
  }

  function limpiarSeleccion() {
    seleccion.clear();
    actualizarBarra();
  }

  /** Muestra la barra (hay cola) con la nota de «hay mas de lo que ves», si toca. */
  function mostrarLote(cola) {
    if (!lote || !barra) {
      return;
    }
    const total = typeof cola.total === 'number' ? cola.total : entradasPorId.size;
    barra.nota.hidden = total <= entradasPorId.size;
    lote.hidden = false;
    actualizarBarra();
  }

  /** Tras un lote el trabajo sigue en la casilla maestra; sin cola, en el aviso. */
  function enfocarTrasElLote() {
    if (lote && barra && !lote.hidden && !barra.maestra.disabled) {
      barra.maestra.focus();
      return;
    }
    zonaAviso.setAttribute('tabindex', '-1');
    zonaAviso.focus();
  }

  const AL_PULSAR_DEL_AVISO = {
    'iniciar-sesion': () =>
      globalThis.location.assign(
        urlDeLogin({ volver: `${globalThis.location.pathname}${globalThis.location.search}` }),
      ),
    'actualizar-cola': () => {
      // `recargar` pinta sus propios fallos: no rechaza.
      recargar();
    },
  };

  /** Pinta el rechazo de un lote y deja la pantalla lista para corregir o reintentar. */
  async function rechazarLote(error, nombrePorId) {
    const mensaje = mensajeDelLote(error, nombrePorId);
    pintarAviso(zonaAviso, {
      tono: mensaje.tono,
      titulo: mensaje.titulo,
      detalle: mensaje.detalle,
      accion: mensaje.accion
        ? { ...mensaje.accion, alPulsar: AL_PULSAR_DEL_AVISO[mensaje.accion.nombre] }
        : null,
    });
    if (mensaje.afectados.length > 0) {
      zonaAviso.append(
        h('ul', {
          clase: 'pila pila--compacta',
          datos: { zona: 'afectados' },
          hijos: mensaje.afectados.map((texto) => h('li', { clase: 't-meta', texto })),
        }),
      );
    }
    if (mensaje.apagarBarra) {
      bloqueo.apagada = true;
    }
    for (const id of mensaje.quitar) {
      seleccion.delete(id);
    }
    if (mensaje.recargar) {
      // La lista de afectados ya esta en el aviso: sobrevive a la recarga.
      await recargar({ conservarAviso: true });
    } else {
      actualizarBarra();
    }
  }

  /** «Aplicar»: confirma si es ELIMINAR, manda el lote y cuenta que paso. */
  async function aplicarLote() {
    if (!barra || bloqueo.enVuelo || bloqueo.apagada) {
      return;
    }
    const ids = [...entradasPorId.keys()].filter((id) => seleccion.has(id));
    const motivo = barra.motivo.value.trim();
    const accion = barra.accion.value;
    if (ids.length === 0 || motivo.length < MOTIVO_MINIMO) {
      return;
    }

    marcarEnvio(true);
    limpiarAviso(zonaAviso);
    if (accion === 'ELIMINAR') {
      // §7.3.9: eliminar en masa pide escribir la palabra, no solo pulsar.
      const confirmado = await confirmarCritico({
        titulo: 'Eliminar los comentarios seleccionados',
        mensaje: `Vas a eliminar ${ids.length === 1 ? '1 comentario' : `${ids.length} comentarios`}.`,
        consecuencias: [
          'Eliminar es definitivo: no se puede deshacer.',
          'Los autores reciben aviso con el motivo.',
          'Si alguno ya no admite la decisión, no se elimina ninguno.',
        ],
        palabra: CONFIRMACION_DE_ELIMINAR,
        textoConfirmar: 'Eliminar',
      });
      if (!confirmado) {
        marcarEnvio(false);
        return;
      }
    }

    // Los nombres se toman ANTES de recargar: tras un 409 la cola ya no trae
    // a los afectados y la lista tiene que decir de quien eran.
    const antes = new Map(entradasPorId);
    const nombrePorId = (id) => (antes.has(id) ? nombreDeEntrada(antes.get(id)) : null);

    conCarga(barra.aplicar, true, 'Aplicando…');
    actualizarBarra();
    let resultado;
    try {
      resultado = await cliente.resolverEnLote({
        comentarioIds: ids,
        accion,
        motivo,
        // A ELIMINAR se le manda la constante del contrato, no lo tecleado.
        ...(accion === 'ELIMINAR' ? { confirmacion: CONFIRMACION_DE_ELIMINAR } : {}),
      });
    } catch (error) {
      marcarEnvio(false);
      conCarga(barra.aplicar, false);
      await rechazarLote(error, nombrePorId);
      return;
    }

    marcarEnvio(false);
    conCarga(barra.aplicar, false);
    pintarAviso(zonaAviso, {
      tono: 'exito',
      titulo: RESULTADO_DE_LOTE[accion] ?? 'Decisión aplicada',
      detalle: detalleDelAviso(accion, resultado?.resultados ?? []),
    });
    seleccion.clear();
    barra.motivo.value = '';
    await recargar({ conservarAviso: true });
    enfocarTrasElLote();
  }

  if (barra) {
    barra.maestra.addEventListener('change', () => {
      seleccion.clear();
      if (barra.maestra.checked) {
        for (const id of entradasPorId.keys()) {
          seleccion.add(id);
        }
      }
      actualizarBarra();
    });
    barra.limpiar.addEventListener('click', limpiarSeleccion);
    barra.motivo.addEventListener('input', actualizarBarra);
    barra.accion.addEventListener('change', actualizarBarra);
    barra.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      void aplicarLote();
    });
    actualizarBarra();
  }

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
    // La barra del lote se esconde mientras se recarga: decidir sobre una cola
    // que se esta cambiando es decidir a ciegas. Vuelve si hay cola que mostrar.
    if (lote) {
      lote.hidden = true;
    }
    pintarEstado(zonaCola, estadoDeCarga({ filas: 3, etiqueta: 'Cargando la cola…' }));
    const elegido = filtroElegido(filtro);
    const categoria = filtroCategoria?.value || null;
    const soloPrioridad = prioridadElegida(filtroPrioridad).prioridadElevada;
    const miRecarga = ++generacion;
    try {
      const cola = await cliente.consultarCola({
        productoId,
        marcado: elegido.marcado,
        categoria,
        prioridadElevada: soloPrioridad,
        tamano: TAMANO,
      });
      if (miRecarga !== generacion) {
        return;
      }
      vaciar(zonaCola);
      prioridadPorComentario.clear();
      entradasPorId = new Map();
      for (const entrada of cola.entradas ?? []) {
        const { comentario, prioridadElevada } = entrada;
        prioridadPorComentario.set(comentario?.id, prioridadElevada === true);
        if (comentario?.id) {
          entradasPorId.set(comentario.id, entrada);
        }
      }
      // Solo se decide sobre lo que se ve: lo que ya no esta en la cola sale de la seleccion.
      for (const id of [...seleccion]) {
        if (!entradasPorId.has(id)) {
          seleccion.delete(id);
        }
      }
      if ((cola.entradas ?? []).length === 0) {
        // Cola vacia NO es un error: es la respuesta correcta cuando no hay
        // nada pendiente. Por eso estado vacio y no estado de error.
        pintarEstado(
          zonaCola,
          vacioDeLaCola(
            { marcado: elegido.marcado, categoria, prioridadElevada: soloPrioridad },
            quitarFiltros,
          ),
        );
        actualizarBarra();
        return;
      }
      for (const entrada of cola.entradas) {
        const id = entrada.comentario?.id;
        zonaCola.append(
          tarjetaDeEntrada(entrada, (abierto) => abrir(abierto), {
            hrefDeFicha,
            ...(barra
              ? {
                  seleccionable: true,
                  seleccionada: seleccion.has(id),
                  alCambiarSeleccion: (marcada) => cambiarSeleccion(id, marcada),
                }
              : {}),
          }),
        );
      }
      mostrarLote(cola);
    } catch (error) {
      if (miRecarga !== generacion) {
        return;
      }
      vaciar(zonaCola);
      // UX-R4.3 — el reintento solo cuando reintentar puede servir de algo.
      // Un 403 no se arregla pulsando otra vez: el permiso no va a cambiar
      // entre dos clics, y ofrecer el boton seria prometer una salida que no
      // existe. Cualquier otro fallo si es transitorio, y hasta ahora esta
      // pantalla era la unica del producto que dejaba al moderador con un
      // mensaje y ninguna forma de volver a intentarlo que no fuera recargar
      // la pagina entera a mano.
      const sinPermiso = error?.estado === 403;
      pintarEstado(
        zonaCola,
        estadoDeError({
          titulo: sinPermiso
            ? 'Esta pantalla es solo para moderación'
            : 'No se pudo cargar la cola',
          detalle: sinPermiso
            ? 'Tu cuenta no tiene permiso para revisar comentarios reportados.'
            : (error?.detalle ?? null),
          alReintentar: sinPermiso ? null : () => recargar({ conservarAviso: true }),
        }),
      );
    }
  }

  async function abrir(comentarioId, { conservarAviso = false } = {}) {
    if (!conservarAviso) {
      limpiarAviso(zonaAviso);
    }
    vaciar(zonaDetalle);
    try {
      const detalle = await cliente.consultarDetalle(comentarioId);
      zonaDetalle.append(
        panelDeDetalle(detalle, (decision) => decidir(comentarioId, decision), {
          cargarImagen: (id) => cliente.imagenParaModeracion(id),
          cargarHistorialDelAutor: (autorId, paginacion) =>
            cliente.historialDelAutor(autorId, paginacion),
          prioridadElevada: prioridadPorComentario.get(comentarioId) === true,
          ...(crearUrl ? { crearUrl } : {}),
        }),
      );
    } catch (error) {
      problema(error);
    }
  }

  async function decidir(comentarioId, decision) {
    limpiarAviso(zonaAviso);
    try {
      const resuelto = await cliente.resolverComentario(comentarioId, decision);
      const interna = ACCIONES_INTERNAS.includes(decision.accion);
      let detalle = 'La decisión quedó registrada, pero el aviso al autor no salió.';
      if (interna) {
        detalle = 'Es una nota interna: el autor no recibe aviso. Queda en el historial.';
      } else if (resuelto.autorNotificado) {
        detalle = 'Se avisó al autor con el motivo.';
      }
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo:
          RESULTADO_DE_ACCION[decision.accion] ??
          `Comentario ${ESTADO_LEGIBLE[resuelto.comentario?.estado]?.toLowerCase() ?? 'resuelto'}`,
        // Se dice si el aviso salio o no: el aviso es fail-open (HU-DIS-003),
        // asi que puede no haber salido y la decision valer igual. Callarlo
        // dejaria al moderador creyendo que el autor se entero.
        detalle,
      });
      await recargar({ conservarAviso: true });
      if (ACCIONES_SIN_CAMBIO_DE_ESTADO.includes(decision.accion)) {
        // Editar o marcar no cierran el caso: el mismo comentario vuelve a
        // quedar delante, con el historial al dia, para seguir con el.
        await abrir(comentarioId, { conservarAviso: true });
      }
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

  /**
   * «Quitar filtros»: categoria y prioridad vuelven a «Todas». «Mostrar» no se
   * toca. El foco pasa al primer filtro antes de recargar, porque el boton que
   * se acaba de pulsar desaparece con la recarga.
   */
  function quitarFiltros() {
    limpiarSeleccion();
    if (filtroCategoria) {
      filtroCategoria.value = '';
    }
    if (filtroPrioridad) {
      filtroPrioridad.value = FILTROS_DE_PRIORIDAD[0].valor;
    }
    (filtroCategoria ?? filtroPrioridad)?.focus();
    void recargar();
  }

  // Cambiar de filtro cambia lo que se ve: la seleccion no puede sobrevivirle, o
  // la decision alcanzaria comentarios que el moderador ya no tiene delante.
  const alCambiarFiltro = () => {
    limpiarSeleccion();
    return recargar();
  };
  filtro?.addEventListener('change', alCambiarFiltro);
  filtroCategoria?.addEventListener('change', alCambiarFiltro);
  filtroPrioridad?.addEventListener('change', alCambiarFiltro);
  recargar();
  return { recargar };
}
