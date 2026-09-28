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
 * inventario, el detalle de la tienda y el de la portada pública.
 *
 * ## De dónde sale cada cosa (comentarios.yaml 1.5.0, B3)
 *
 *   - El resumen (promedio, cuántas valoraciones): `GET /products/{id}/rating`,
 *     la única fuente del promedio. Ya no se lee del hilo ni se calcula aquí.
 *   - Calificar: su propio control (`calificar-producto.js`), una sola vez y
 *     sin escribir nada. Con sesión, `GET .../rating/mia` decide si se ofrecen
 *     las estrellas o se enseña «Tu calificación: N de 5».
 *   - El hilo: `GET /products/{id}/comments`, paginado por el servidor y ya
 *     ordenado del más reciente al más antiguo. Se conserva la experiencia de
 *     la ficha —de cinco en cinco—, pero cada «Ver más» pide la página
 *     siguiente al servidor (`pagina`, `tamano`) en vez de recortar una lista
 *     que ya estaba entera en el navegador. `total` (el de todas las páginas)
 *     dice cuántas hay y cuántas quedan.
 *   - Las imágenes de cada opinión: `GET /comentarios/imagenes/{id}`.
 *
 * ## Estados
 *
 *   - cargando: el esqueleto de la lista, con su nombre;
 *   - vacío: nadie ha opinado todavía, y qué hacer (opinar, o entrar para
 *     opinar);
 *   - uno, muchos: los más recientes primero; de cinco en cinco;
 *   - con imágenes: miniaturas reales (ver `comentario.js`);
 *   - reportado: el que quien mira acaba de reportar queda marcado «en
 *     revisión» en su sitio, en vez de desaparecer sin explicación;
 *   - error: la lista no cargó, se dice y se reintenta; calificar y publicar
 *     siguen disponibles, porque no dependen de la lista;
 *   - sin sesión: se lee todo; para calificar, opinar o reportar se ofrece
 *     entrar.
 *
 * El promedio y la lista los da el servicio: tras calificar, publicar o
 * retirar se vuelven a leer, no se recalculan aquí (regla 7).
 *
 * ## Si el hilo cambia mientras se lee
 *
 * La paginación es por posición: si alguien publica entre dos «Ver más», la
 * página siguiente trae repetido el último que ya se veía, y se descarta por
 * su `id`. Si alguien retira uno, el siguiente puede saltarse hasta la próxima
 * lectura del hilo. Es el precio de no guardar el hilo entero en el navegador,
 * y se paga una opinión, no la pantalla.
 *
 * @module plataforma/comentarios/hilo-comentarios
 */

import { h } from '../../comun/ui/dom.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../../comun/ui/estado-vista.js';
import { confirmar } from '../../comun/ui/dialogo.js';
import { pintarAviso, limpiarAviso } from '../../comun/ui/aviso.js';
import { conCarga } from '../../comun/ui/boton.js';
import { conCuenta, resumenDeCalificacion } from '../../comun/ui/comunidad/resumen.js';
import { ESTADO_LOCAL, tarjetaDeComentario } from '../../comun/ui/comunidad/comentario.js';
import { leerSesion, urlDeLogin } from '../../comun/sesion.js';
import {
  calificar,
  consultarHilo,
  eliminarComentario,
  miCalificacion,
  publicarComentario,
  resumenDeCalificaciones,
  subirImagen,
  urlDeImagen,
} from './cliente-comentarios.js';
import { reportarComentario } from './cliente-moderacion.js';
import { controlDeCalificacion } from './calificar-producto.js';
import { redactorDeComentario } from './redactor-comentario.js';
import { abrirReporteDeComentario, RESULTADO_REPORTE } from './reportar-comentario.js';

/** Cuántas opiniones se piden de entrada y cuántas más en cada «Ver más». */
export const OPINIONES_POR_TANDA = 5;

/** El servicio no sirve más de 50 por página (comentarios.yaml 1.4.0). */
export const TAMANO_MAXIMO_DE_PAGINA = 50;

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
 * Cuántas opiniones pedir al volver a leer el hilo sin perder lo que ya estaba
 * desplegado: las que había, redondeadas a tandas enteras (así la siguiente
 * página sigue cayendo en su sitio) y sin pasar del máximo del servicio.
 *
 * @param {number} cargadas
 * @returns {number}
 */
export function tamanoParaRecargar(cargadas) {
  const tandas = Math.max(1, Math.ceil((Number(cargadas) || 0) / OPINIONES_POR_TANDA));
  return Math.min(TAMANO_MAXIMO_DE_PAGINA, tandas * OPINIONES_POR_TANDA);
}

/**
 * El texto del botón «Ver más».
 *
 * @param {number} quedan
 * @returns {string}
 */
export function textoDeVerMas(quedan) {
  const siguientes = Math.min(quedan, OPINIONES_POR_TANDA);
  return `Ver ${conCuenta(siguientes, 'opinión', 'opiniones')} más (${quedan === 1 ? 'queda' : 'quedan'} ${quedan})`;
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
 *   correcta del hilo
 * @param {(datos: {resumen: object|null, opiniones: number|null}) => void} [opciones.alResumen]
 *   cada vez que cambia el resumen de la calificación o el número de opiniones
 *   (la ficha pinta el resumen compacto en su cabecera); `resumen` es `null`
 *   si no se pudo leer
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
    alResumen = () => {},
    consultarImpl = consultarHilo,
    resumenImpl = resumenDeCalificaciones,
    miCalificacionImpl = miCalificacion,
    calificarImpl = calificar,
    publicarImpl = publicarComentario,
    subirImagenImpl = subirImagen,
    eliminarImpl = eliminarComentario,
    reportarImpl = reportarComentario,
    urlDeImagenImpl = urlDeImagen,
  },
) {
  secuencia += 1;
  const idTitulo = `hilo-comentarios-${secuencia}`;
  const yo = sesion?.yo ?? null;

  const zonaResumen = h('div', { clase: 'hilo-comentarios__resumen', datos: { zona: 'resumen' } });
  const zonaCalificacion = h('div', {
    clase: 'hilo-comentarios__calificacion',
    datos: { zona: 'calificacion' },
  });
  zonaCalificacion.hidden = !yo;
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
      zonaCalificacion,
      zonaAviso,
      zonaLista,
      zonaRedactor,
    ],
  });
  zona.replaceChildren(elemento);

  /** Comentarios que quien mira reportó en esta visita: se quedan marcados. */
  const reportados = new Set();
  /** Lo leído del hilo: las opiniones cargadas, más reciente primero. */
  const hilo = { comentarios: [], total: null, cubiertas: 0, cargado: false };
  /** El resumen de la calificación: `undefined` mientras no se sabe. */
  const calificacion = { resumen: undefined, error: false };

  /** La tarjeta de un comentario ya pintada, por su id. */
  const tarjetaDe = (id) =>
    Array.from(zonaLista.querySelectorAll('.comentario')).find(
      (tarjeta) => tarjeta.dataset.comentarioId === String(id),
    ) ?? null;

  // ---------------------------------------------------------------- resumen

  function pintarResumen() {
    const opiniones = hilo.cargado ? hilo.total : null;
    if (calificacion.error) {
      const reintentar = h('button', {
        clase: 'boton boton--secundario boton--pequeno',
        texto: 'Reintentar',
        datos: { accion: 'reintentar-resumen' },
        atributos: { type: 'button' },
      });
      reintentar.addEventListener('click', () => cargarResumen());
      zonaResumen.replaceChildren(
        h('div', {
          clase: 'resumen-calificacion',
          datos: { estado: 'error' },
          hijos: [
            h('p', {
              clase: 'resumen-calificacion__detalle',
              texto: 'No pudimos cargar la valoración.',
            }),
            reintentar,
          ],
        }),
      );
      alResumen({ resumen: null, opiniones });
      return;
    }
    if (calificacion.resumen === undefined) {
      return;
    }
    zonaResumen.replaceChildren(resumenDeCalificacion(calificacion.resumen, { opiniones }));
    alResumen({ resumen: calificacion.resumen, opiniones });
  }

  function guardarResumen(resumen) {
    calificacion.resumen = resumen;
    calificacion.error = false;
    pintarResumen();
  }

  async function cargarResumen() {
    try {
      guardarResumen(await resumenImpl(productoId));
    } catch {
      calificacion.error = true;
      pintarResumen();
    }
  }

  // ------------------------------------------------------------------ lista

  const alEliminar = async (comentario) => {
    const seguro = await confirmar({
      titulo: 'Eliminar tu comentario',
      mensaje: Number.isInteger(comentario.estrellas)
        ? 'Desaparece del hilo y no se puede deshacer. Tu calificación del producto se mantiene: va aparte del comentario.'
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
      await cargarHilo();
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
        await cargarHilo();
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
      await cargarHilo();
    }
  };

  const elementoDeLista = (comentario) =>
    h('li', {
      clase: 'hilo-comentarios__elemento',
      hijos: [
        tarjetaDeComentario(comentario, {
          yo,
          estadoLocal: reportados.has(comentario.id) ? ESTADO_LOCAL.REPORTADO : null,
          alEliminar,
          alReportar,
          urlDeImagen: urlDeImagenImpl,
        }),
      ],
    });

  /** El `total` del servidor, a la vista: «Mostrando 5 de 12 opiniones…». */
  const totalDelHilo = () => hilo.total ?? hilo.comentarios.length;
  const textoDelConteo = () => {
    const total = totalDelHilo();
    const cuantas = conCuenta(total, 'opinión', 'opiniones');
    return hilo.comentarios.length < total
      ? `Mostrando ${hilo.comentarios.length} de ${cuantas}, de la más reciente a la más antigua.`
      : `${cuantas}, de la más reciente a la más antigua.`;
  };

  /** Cuántas quedan por pedir, si quedan. */
  const quedan = () => {
    const total = hilo.total ?? 0;
    return hilo.cubiertas < total ? Math.max(total - hilo.comentarios.length, 0) : 0;
  };

  function botonVerMas() {
    const restantes = quedan();
    if (restantes === 0) {
      return null;
    }
    const mas = h('button', {
      clase: 'boton boton--secundario boton--pequeno hilo-comentarios__mas',
      texto: textoDeVerMas(restantes),
      datos: { accion: 'ver-mas-opiniones' },
      atributos: { type: 'button' },
    });
    mas.addEventListener('click', () => verMas(mas));
    return mas;
  }

  function pintarLista() {
    if (hilo.comentarios.length === 0) {
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
    const hijos = [
      // Con una sola opinión no hay nada que contar ni que ordenar.
      totalDelHilo() > 1
        ? h('p', {
            clase: 'hilo-comentarios__conteo',
            texto: textoDelConteo(),
            datos: { zona: 'conteo' },
          })
        : null,
      h('ol', {
        clase: 'hilo-comentarios__lista',
        hijos: hilo.comentarios.map(elementoDeLista),
      }),
      botonVerMas(),
    ];
    // `replaceChildren` convertiría un `null` en el texto «null».
    zonaLista.replaceChildren(...hijos.filter(Boolean));
  }

  /**
   * La página siguiente, pedida al servidor. Las nuevas se añaden al final
   * sin volver a pintar las que ya estaban, y el foco va a la primera nueva.
   */
  async function verMas(boton) {
    const pagina = Math.floor(hilo.cubiertas / OPINIONES_POR_TANDA);
    conCarga(boton, true, 'Cargando opiniones…');
    let respuesta;
    try {
      respuesta = await consultarImpl(productoId, { pagina, tamano: OPINIONES_POR_TANDA });
    } catch {
      conCarga(boton, false);
      pintarAviso(zonaAviso, {
        tono: 'error',
        titulo: 'No pudimos cargar más opiniones',
        detalle: 'Las que ya ves siguen aquí. Vuelve a intentarlo en un momento.',
      });
      return;
    }
    limpiarAviso(zonaAviso);
    const nuevas = anadirPagina(respuesta, pagina);
    const lista = zonaLista.querySelector('.hilo-comentarios__lista');
    if (!lista) {
      pintarLista();
      return;
    }
    lista.append(...nuevas.map(elementoDeLista));
    const conteo = zonaLista.querySelector('[data-zona="conteo"]');
    if (conteo) {
      conteo.textContent = textoDelConteo();
    }
    const siguiente = botonVerMas();
    if (siguiente) {
      boton.replaceWith(siguiente);
    } else {
      boton.remove();
    }
    pintarResumen();
    // El foco va a la primera opinión que acaba de aparecer, no al principio
    // de la lista (ni a `body`, que es donde cae si el botón desaparece).
    const primera = nuevas[0] ? tarjetaDe(nuevas[0].id) : null;
    (primera ?? siguiente)?.focus();
  }

  /**
   * Suma una página a lo leído, descartando las que ya estaban.
   *
   * @returns {object[]} las nuevas
   */
  function anadirPagina(respuesta, pagina) {
    const vistas = new Set(hilo.comentarios.map((comentario) => comentario.id));
    const nuevas = (Array.isArray(respuesta?.comentarios) ? respuesta.comentarios : []).filter(
      (comentario) => !vistas.has(comentario.id),
    );
    const aplicado = Number.isInteger(respuesta?.tamano) ? respuesta.tamano : OPINIONES_POR_TANDA;
    hilo.comentarios = [...hilo.comentarios, ...nuevas];
    hilo.total = Number.isInteger(respuesta?.total) ? respuesta.total : hilo.comentarios.length;
    hilo.cubiertas = Math.max(hilo.cubiertas, (pagina + 1) * aplicado);
    return nuevas;
  }

  /** Guarda una lectura del hilo desde el principio. */
  function guardarHilo(respuesta, tamano) {
    const comentarios = Array.isArray(respuesta?.comentarios) ? respuesta.comentarios : [];
    const aplicado = Number.isInteger(respuesta?.tamano) ? respuesta.tamano : tamano;
    hilo.comentarios = comentarios;
    hilo.total = Number.isInteger(respuesta?.total) ? respuesta.total : comentarios.length;
    hilo.cubiertas = aplicado;
    hilo.cargado = true;
  }

  /**
   * Lee el hilo desde el principio, conservando lo que ya estaba desplegado
   * (tras publicar, retirar o calificar, la persona no vuelve a las cinco
   * primeras).
   *
   * @returns {Promise<object|null>} la respuesta del servicio, o `null` si falló
   */
  async function cargarHilo() {
    if (!hilo.cargado) {
      zonaLista.replaceChildren(estadoDeCarga({ filas: 2, etiqueta: 'Cargando opiniones…' }));
    }
    const tamano = tamanoParaRecargar(hilo.comentarios.length);
    let respuesta;
    try {
      respuesta = await consultarImpl(productoId, { pagina: 0, tamano });
    } catch {
      if (hilo.cargado) {
        // Ya había una lista: se queda, y se dice que puede no estar al día.
        pintarAviso(zonaAviso, {
          tono: 'error',
          titulo: 'No pudimos actualizar las opiniones',
          detalle: 'Las que ves pueden no estar al día. Vuelve a intentarlo en un momento.',
          accion: { texto: 'Reintentar', nombre: 'reintentar-hilo', alPulsar: () => cargarHilo() },
        });
        return null;
      }
      zonaLista.replaceChildren(
        estadoDeError({
          titulo: 'No pudimos cargar las opiniones',
          detalle: yo
            ? 'Puedes calificar y publicar la tuya igualmente. Vuelve a intentarlo para ver las demás.'
            : 'El producto sigue disponible. Vuelve a intentarlo en un momento.',
          alReintentar: () => cargarHilo(),
        }),
      );
      return null;
    }
    guardarHilo(respuesta, tamano);
    pintarLista();
    pintarResumen();
    alCargar(respuesta);
    return respuesta;
  }

  // ----------------------------------------------------- calificar y opinar

  function montarCalificacion() {
    if (!yo) {
      return;
    }
    const control = controlDeCalificacion({
      productoId,
      miCalificacionImpl,
      calificarImpl,
      alCalificar: ({ resumen }) => {
        if (resumen) {
          guardarResumen(resumen);
        } else {
          cargarResumen();
        }
        // Las estrellas de cada opinión son las de la calificación de su
        // autor: las propias ya las llevan.
        cargarHilo();
      },
    });
    zonaCalificacion.replaceChildren(control.elemento);
    control.cargar();
  }

  function montarRedactor() {
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
    // Se monta una vez: volver a leer el hilo no borra lo que se está escribiendo.
    const redactor = redactorDeComentario({
      productoId,
      publicarImpl,
      subirImagenImpl,
      alPublicar: ({ estado }) => {
        if (estado !== 'EN_REVISION') {
          cargarHilo();
        }
      },
    });
    zonaRedactor.replaceChildren(redactor.elemento);
  }

  montarCalificacion();
  montarRedactor();
  cargarResumen();
  cargarHilo();

  return {
    elemento,
    recargar: async () => {
      const [respuesta] = await Promise.all([cargarHilo(), cargarResumen()]);
      return respuesta;
    },
  };
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
      alResumen: (datos) => {
        opciones.alResumen?.(datos);
        pintarValoracionEnLaFicha(ficha, datos);
      },
    });
    return zona;
  };
}

/**
 * El resumen compacto bajo el tipo del producto, reemplazando el anterior. Si
 * el resumen no se pudo leer, se quita: una valoración vieja o inventada en la
 * cabecera diría algo que no se sabe.
 *
 * @param {HTMLElement|null} ficha
 * @param {{resumen: object|null, opiniones: number|null}} datos
 */
function pintarValoracionEnLaFicha(ficha, { resumen, opiniones }) {
  if (!ficha) {
    return;
  }
  const anterior = ficha.querySelector('.ficha__valoracion');
  if (!resumen) {
    anterior?.remove();
    return;
  }
  const nuevo = h('div', {
    clase: 'ficha__valoracion',
    hijos: [resumenDeCalificacion(resumen, { compacto: true, opiniones })],
  });
  if (anterior) {
    anterior.replaceWith(nuevo);
  } else {
    ficha.querySelector('.ficha__tipo')?.after(nuevo);
  }
}
