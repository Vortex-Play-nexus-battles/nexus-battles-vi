/**
 * Las opiniones de un producto, dentro de su detalle — UXC-3 (RatingThread).
 *
 * §7.1 del documento: al abrir un producto, la vista de detalle muestra «la
 * calificación promedio en cinco estrellas y el hilo de comentarios» (texto e
 * imágenes, apodo, estrellas y fecha; una calificación por usuario,
 * comentarios ilimitados). Hasta aquí eso vivía en una pantalla aparte a la
 * que no llevaba ninguna ficha: el requisito estaba construido y no se veía.
 *
 * Este componente lo pone donde el jugador mira el producto: la ficha del
 * inventario, el detalle de la tienda y el de la portada pública. Lee
 * `GET /products/{id}/comments` (público) y, con sesión, deja publicar,
 * retirar lo propio y reportar lo ajeno.
 *
 * ## Estados
 *
 *   - cargando: el esqueleto de la lista, con su nombre;
 *   - vacío: nadie ha opinado todavía, y qué hacer (opinar, o entrar para
 *     opinar);
 *   - uno, muchos: los más recientes primero; de cinco en cinco;
 *   - con imágenes: el adjunto con su nombre (ver `comentario.js`);
 *   - reportado: el que quien mira acaba de reportar queda marcado «en
 *     revisión» en su sitio, en vez de desaparecer sin explicación;
 *   - error: la lista no cargó, se dice y se reintenta; publicar sigue
 *     disponible, porque no depende de la lista;
 *   - sin sesión: se lee todo; para calificar, opinar o reportar se ofrece
 *     entrar.
 *
 * El promedio y la lista los da el servicio: tras publicar o retirar se
 * vuelven a leer, no se recalculan aquí (regla 7).
 *
 * @module plataforma/comentarios/hilo-comentarios
 */

import { h } from '../../comun/ui/dom.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../../comun/ui/estado-vista.js';
import { confirmar } from '../../comun/ui/dialogo.js';
import { pintarAviso, limpiarAviso } from '../../comun/ui/aviso.js';
import { resumenDeCalificacion } from '../../comun/ui/comunidad/resumen.js';
import { ESTADO_LOCAL, tarjetaDeComentario } from '../../comun/ui/comunidad/comentario.js';
import { leerSesion, urlDeLogin } from '../../comun/sesion.js';
import { consultarHilo, eliminarComentario, publicarComentario } from './cliente-comentarios.js';
import { reportarComentario } from './cliente-moderacion.js';
import { redactorDeComentario } from './redactor-comentario.js';
import { abrirReporteDeComentario, RESULTADO_REPORTE } from './reportar-comentario.js';

/** Cuántas opiniones se enseñan de entrada y cuántas más en cada «Ver más». */
export const OPINIONES_POR_TANDA = 5;

let secuencia = 0;

/**
 * La sesión de quien mira, en lo que al hilo le importa.
 *
 * @returns {{yo: string|null, apodo: string|null}}
 */
export function quienMira() {
  const sesion = leerSesion();
  return sesion?.autenticado
    ? { yo: sesion.uid ?? null, apodo: sesion.apodo ?? null }
    : { yo: null, apodo: null };
}

/**
 * Los comentarios, del más reciente al más antiguo. El servicio los da al
 * revés (del más antiguo al más reciente) y un hilo se lee desde lo último.
 *
 * @param {object} hilo `HiloDeComentariosResponse`
 * @returns {object[]}
 */
export function masRecientesPrimero(hilo) {
  const lista = Array.isArray(hilo?.comentarios) ? [...hilo.comentarios] : [];
  return lista.reverse();
}

/**
 * ¿Calificó ya quien mira este producto? Se deduce del hilo: un comentario
 * suyo con estrellas. Solo sirve para avisar antes de publicar; quien decide
 * es el servicio (D-07).
 *
 * @param {object[]} comentarios
 * @param {string|null} yo
 * @returns {boolean}
 */
export function yaCalifico(comentarios, yo) {
  return (
    Boolean(yo) && comentarios.some((c) => c?.autorId === yo && Number.isInteger(c?.estrellas))
  );
}

/**
 * Monta el hilo en una zona.
 *
 * @param {HTMLElement} zona donde se pinta (se vacía)
 * @param {object} opciones
 * @param {string} opciones.productoId
 * @param {{yo: string|null, apodo: string|null}} [opciones.sesion]
 * @param {string} [opciones.titulo]
 * @param {number} [opciones.nivel] nivel del encabezado (2..4)
 * @param {(() => void)|null} [opciones.alPedirEntrada] sin sesión: qué hace
 *   «Entrar para opinar». Por omisión, el enlace al login con vuelta aquí; la
 *   portada, que ya ES el login, lleva al formulario.
 * @param {(hilo: object) => void} [opciones.alCargar] tras cada lectura
 *   correcta (la ficha pinta el resumen compacto en su cabecera)
 * @returns {{elemento: HTMLElement, recargar: () => Promise<object|null>}}
 */
export function montarHiloDeComentarios(
  zona,
  {
    productoId,
    sesion = quienMira(),
    titulo = 'Opiniones de la comunidad',
    nivel = 3,
    alPedirEntrada = null,
    alCargar = () => {},
    consultarImpl = consultarHilo,
    publicarImpl = publicarComentario,
    eliminarImpl = eliminarComentario,
    reportarImpl = reportarComentario,
  },
) {
  secuencia += 1;
  const idTitulo = `hilo-comentarios-${secuencia}`;
  const yo = sesion?.yo ?? null;

  const zonaResumen = h('div', { clase: 'hilo-comentarios__resumen', datos: { zona: 'resumen' } });
  const zonaAviso = h('div', { clase: 'hilo-comentarios__aviso', datos: { zona: 'aviso-hilo' } });
  zonaAviso.hidden = true;
  const zonaLista = h('div', {
    clase: 'hilo-comentarios__cuerpo',
    datos: { zona: 'lista' },
    atributos: { 'aria-live': 'polite' },
  });
  const zonaRedactor = h('div', {
    clase: 'hilo-comentarios__redactor',
    datos: { zona: 'redactor' },
  });

  const elemento = h('section', {
    clase: 'hilo-comentarios',
    datos: { productoId },
    atributos: { 'aria-labelledby': idTitulo },
    hijos: [
      h('div', {
        clase: 'hilo-comentarios__cabecera',
        hijos: [
          h(`h${Math.min(Math.max(nivel, 2), 4)}`, {
            clase: 'hilo-comentarios__titulo',
            texto: titulo,
            atributos: { id: idTitulo },
          }),
          zonaResumen,
        ],
      }),
      zonaAviso,
      zonaLista,
      zonaRedactor,
    ],
  });
  zona.replaceChildren(elemento);

  /** Comentarios que quien mira reportó en esta visita: se quedan marcados. */
  const reportados = new Set();
  let visibles = OPINIONES_POR_TANDA;
  let ultimo = null;
  /** El formulario montado, para avisarle de que ya calificó sin rehacerlo. */
  let redactorActual = null;

  /** La tarjeta de un comentario ya pintada, por su id. */
  const tarjetaDe = (id) =>
    Array.from(zonaLista.querySelectorAll('.comentario')).find(
      (tarjeta) => tarjeta.dataset.comentarioId === String(id),
    ) ?? null;

  const alEliminar = async (comentario) => {
    const seguro = await confirmar({
      titulo: 'Eliminar tu comentario',
      mensaje: Number.isInteger(comentario.estrellas)
        ? 'Desaparece del hilo y tus estrellas dejan de contar en el promedio. Podrás volver a calificar en una opinión nueva.'
        : 'Desaparece del hilo. No se puede deshacer.',
      textoConfirmar: 'Eliminar',
      textoCancelar: 'Conservar',
    });
    if (!seguro) {
      return;
    }
    limpiarAviso(zonaAviso);
    try {
      await eliminarImpl(productoId, comentario.id);
      pintarAviso(zonaAviso, { tono: 'exito', titulo: 'Tu comentario se eliminó' });
      await recargar();
    } catch (error) {
      pintarAviso(zonaAviso, {
        tono: error?.estado === 404 ? 'info' : 'error',
        titulo:
          error?.estado === 404
            ? 'Ese comentario ya no estaba publicado'
            : 'No pudimos eliminar tu comentario',
        detalle:
          error?.estado === 404 ? null : 'Sigue publicado. Vuelve a intentarlo en un momento.',
      });
      if (error?.estado === 404) {
        await recargar();
      }
    }
  };

  const alReportar = async (comentario) => {
    const { resultado } = await abrirReporteDeComentario({
      productoId,
      comentario,
      reportarImpl,
    });
    if (resultado === RESULTADO_REPORTE.REPORTADO || resultado === RESULTADO_REPORTE.YA_REPORTADO) {
      reportados.add(comentario.id);
      pintarLista();
      // El botón que abrió el diálogo ya no existe (la tarjeta cambió): el
      // foco va a la tarjeta, que ahora dice que está en revisión.
      tarjetaDe(comentario.id)?.focus();
    } else if (resultado === RESULTADO_REPORTE.RETIRADO) {
      await recargar();
    }
  };

  function pintarLista() {
    const comentarios = masRecientesPrimero(ultimo);
    if (comentarios.length === 0) {
      zonaLista.replaceChildren(
        estadoVacio({
          titulo: 'Todavía nadie opina sobre este producto',
          detalle: yo
            ? 'Sé la primera persona en contar qué te pareció: escribe tu opinión aquí abajo.'
            : 'Entra con tu cuenta para ser la primera persona en opinar.',
          icono: '☆',
        }),
      );
      return;
    }
    const lista = h('ol', {
      clase: 'hilo-comentarios__lista',
      hijos: comentarios.slice(0, visibles).map((comentario) =>
        h('li', {
          clase: 'hilo-comentarios__elemento',
          hijos: [
            tarjetaDeComentario(comentario, {
              yo,
              estadoLocal: reportados.has(comentario.id) ? ESTADO_LOCAL.REPORTADO : null,
              alEliminar,
              alReportar,
            }),
          ],
        }),
      ),
    });
    const quedan = comentarios.length - visibles;
    const hijos = [lista];
    if (quedan > 0) {
      const mas = h('button', {
        clase: 'boton boton--secundario boton--pequeno hilo-comentarios__mas',
        texto: `Ver ${Math.min(quedan, OPINIONES_POR_TANDA)} opiniones más (quedan ${quedan})`,
        datos: { accion: 'ver-mas-opiniones' },
        atributos: { type: 'button' },
      });
      mas.addEventListener('click', () => {
        const primeraNueva = visibles;
        visibles += OPINIONES_POR_TANDA;
        pintarLista();
        // El foco va a la primera opinión que acaba de aparecer, no al
        // principio de la lista.
        zonaLista.querySelectorAll('.comentario')[primeraNueva]?.focus();
      });
      hijos.push(mas);
    }
    zonaLista.replaceChildren(...hijos);
  }

  function pintarRedactor(comentarios) {
    if (!yo) {
      const entrar = alPedirEntrada
        ? h('button', {
            clase: 'boton boton--secundario',
            texto: 'Entrar para opinar',
            datos: { accion: 'entrar-para-opinar' },
            atributos: { type: 'button' },
          })
        : h('a', {
            clase: 'boton boton--secundario',
            texto: 'Entrar para opinar',
            datos: { accion: 'entrar-para-opinar' },
            atributos: {
              href: urlDeLogin({
                volver: `${globalThis.location?.pathname ?? ''}${globalThis.location?.search ?? ''}`,
              }),
            },
          });
      if (alPedirEntrada) {
        entrar.addEventListener('click', alPedirEntrada);
      }
      zonaRedactor.replaceChildren(
        h('div', {
          clase: 'hilo-comentarios__entrada',
          hijos: [
            h('p', {
              texto:
                'Para calificar, opinar o reportar un comentario necesitas entrar con tu cuenta.',
            }),
            entrar,
          ],
        }),
      );
      return;
    }
    redactorActual = redactorDeComentario({
      productoId,
      yaCalificado: yaCalifico(comentarios, yo),
      publicarImpl,
      alPublicar: ({ estado }) => {
        if (estado !== 'EN_REVISION') {
          recargar({ conservarRedactor: true });
        }
      },
    });
    zonaRedactor.replaceChildren(redactorActual.elemento);
  }

  /** La última lectura buena del hilo. */
  function guardarHilo(hilo) {
    ultimo = hilo ?? {
      comentarios: [],
      total: 0,
      totalCalificaciones: 0,
      calificacionPromedio: null,
    };
  }

  async function recargar({ conservarRedactor = false } = {}) {
    if (!ultimo) {
      zonaLista.replaceChildren(estadoDeCarga({ filas: 2, etiqueta: 'Cargando opiniones…' }));
    }
    try {
      guardarHilo(await consultarImpl(productoId));
      zonaResumen.replaceChildren(resumenDeCalificacion(ultimo));
      pintarLista();
      const comentarios = masRecientesPrimero(ultimo);
      if (!conservarRedactor || !redactorActual) {
        pintarRedactor(comentarios);
      } else if (yaCalifico(comentarios, yo)) {
        // Acaba de calificar: el mismo formulario (con su aviso de «publicada»)
        // deja de ofrecer estrellas, que ya no contarían.
        redactorActual.marcarCalificado();
      }
      alCargar(ultimo);
      return ultimo;
    } catch {
      zonaResumen.replaceChildren();
      zonaLista.replaceChildren(
        estadoDeError({
          titulo: 'No pudimos cargar las opiniones',
          detalle: yo
            ? 'Puedes publicar la tuya igualmente. Vuelve a intentarlo para ver las demás.'
            : 'El producto sigue disponible. Vuelve a intentarlo en un momento.',
          alReintentar: () => recargar(),
        }),
      );
      if (!redactorActual && !zonaRedactor.firstChild) {
        pintarRedactor([]);
      }
      return null;
    }
  }

  recargar();
  return { elemento, recargar };
}

/**
 * Las opiniones como complemento de la ficha de un producto (`abrirFicha`
 * de `contenido/inventario/ficha-producto.js`).
 *
 * Además del hilo al final de la ficha, deja la valoración compacta bajo el
 * tipo del producto, que es donde se mira primero: «4,3 ★★★★☆ · 12
 * valoraciones» antes de bajar a leerlas.
 *
 * @param {Omit<Parameters<typeof montarHiloDeComentarios>[1], 'productoId'> & {productoId?: string}} [opciones]
 * @returns {(producto: object, contexto?: {ficha?: HTMLElement}) => HTMLElement|null}
 */
export function complementoDeOpiniones(opciones = {}) {
  return (producto, { ficha = null } = {}) => {
    const productoId = opciones.productoId ?? producto?.id;
    if (!productoId) {
      return null;
    }
    const zona = h('div', { clase: 'ficha__opiniones' });
    montarHiloDeComentarios(zona, {
      ...opciones,
      productoId: String(productoId),
      alCargar: (hilo) => {
        opciones.alCargar?.(hilo);
        pintarValoracionEnLaFicha(ficha, hilo);
      },
    });
    return zona;
  };
}

/**
 * El resumen compacto bajo el tipo del producto, reemplazando el anterior.
 *
 * @param {HTMLElement|null} ficha
 * @param {object} hilo
 */
function pintarValoracionEnLaFicha(ficha, hilo) {
  if (!ficha) {
    return;
  }
  const nuevo = h('div', {
    clase: 'ficha__valoracion',
    hijos: [resumenDeCalificacion(hilo, { compacto: true })],
  });
  const anterior = ficha.querySelector('.ficha__valoracion');
  if (anterior) {
    anterior.replaceWith(nuevo);
  } else {
    ficha.querySelector('.ficha__tipo')?.after(nuevo);
  }
}
