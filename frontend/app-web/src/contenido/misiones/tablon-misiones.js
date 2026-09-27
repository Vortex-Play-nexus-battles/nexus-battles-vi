/**
 * Tablón de misiones — UXC-5 (MissionBoard + MissionCard).
 *
 * §7.8.9 y RF-MIS-001: pestañas por categoría (Historia, Desafío,
 * Exploración), filtros por dificultad, estado y duración, y tarjetas con
 * imagen, nombre, descripción breve, dificultad, duración, nivel recomendado,
 * recompensas destacadas, estado de progreso y su botón: «Iniciar misión» o
 * «Ver detalles».
 *
 * El tablón no filtra ni ordena por su cuenta: pasa los criterios a la fuente
 * y pinta la página que vuelve, de dieciséis en dieciséis. Qué misión está
 * disponible y cuál bloqueada, y por qué, lo dice quien publica las misiones.
 *
 * Todas las acciones de una tarjeta son enlaces de verdad (`?mision=…`,
 * `?reporte=…`, `#en-curso`): se pueden abrir en otra pestaña, compartir y
 * volver con «Atrás».
 *
 * @module contenido/misiones/tablon-misiones
 */

import { clases, h, vaciar } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { distintivo } from '../../comun/ui/distintivo.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../../comun/ui/estado-vista.js';
import { montarPestanas } from '../../comun/ui/pestanas.js';
import { construirPaginacion } from '../../comun/paginacion.js';
import { porcentaje } from '../../comun/ui/formato.js';
import {
  CATEGORIAS,
  DIFICULTADES,
  FILTRO_DE_DURACION,
  FILTRO_DE_ESTADO,
  MARCAS_DE_DIFICULTAD,
  MISIONES_POR_PAGINA,
  categoriaDe,
  dificultadDe,
  estadoDe,
  textoDeDuracion,
} from './modelo-misiones.js';

/**
 * Las marcas de dificultad y su nombre. Las marcas son adorno (`aria-hidden`):
 * lo que se lee es la palabra.
 *
 * @param {string} id
 * @returns {HTMLElement|null}
 */
export function indicadorDeDificultad(id) {
  const dificultad = dificultadDe(id);
  if (!dificultad) {
    return null;
  }
  const marcas = h('span', { clase: 'dificultad__marcas', atributos: { 'aria-hidden': 'true' } });
  for (let indice = 0; indice < MARCAS_DE_DIFICULTAD; indice += 1) {
    marcas.append(
      h('span', {
        clase: clases(
          'dificultad__marca',
          indice < dificultad.marcas && 'dificultad__marca--llena',
        ),
      }),
    );
  }
  return h('span', {
    clase: 'dificultad',
    datos: { dificultad: id.toLowerCase() },
    hijos: [marcas, h('span', { clase: 'dificultad__texto', texto: dificultad.etiqueta })],
  });
}

/**
 * El estado con su icono y su palabra.
 *
 * @param {string} id
 * @returns {HTMLElement|null}
 */
export function selloDeMision(id) {
  const estado = estadoDe(id);
  if (!estado) {
    return null;
  }
  const sello = distintivo(estado.texto, estado.variante);
  sello.classList.add('mision-estado');
  sello.prepend(icono(estado.icono, { etiqueta: null, clase: 'icono mision-estado__icono' }));
  return sello;
}

/**
 * La categoría como chip: icono y nombre.
 *
 * @param {string} id
 * @returns {HTMLElement|null}
 */
export function chipDeCategoria(id) {
  const categoria = categoriaDe(id);
  if (!categoria) {
    return null;
  }
  return h('span', {
    clase: 'mision-categoria',
    datos: { categoria: categoria.id.toLowerCase() },
    hijos: [
      icono(categoria.icono, { etiqueta: null, clase: 'icono mision-categoria__icono' }),
      h('span', { texto: categoria.etiqueta }),
    ],
  });
}

/**
 * Barra de progreso de una misión en curso, con la cifra escrita.
 *
 * @param {number} proporcion de 0 a 1
 * @param {string} nombre de la misión, para el nombre accesible
 * @returns {HTMLElement}
 */
export function progresoDeMision(proporcion, nombre) {
  const acotada = Math.min(Math.max(proporcion, 0), 1);
  const cifra = porcentaje(acotada);
  const relleno = h('div', { clase: 'progreso__relleno' });
  relleno.style.width = `${Math.round(acotada * 100)}%`;
  return h('div', {
    clase: 'progreso mision-progreso',
    hijos: [
      h('div', {
        clase: 'progreso__cabecera',
        hijos: [
          h('span', { clase: 'progreso__etiqueta', texto: 'Progreso estimado' }),
          h('span', { clase: 'progreso__valor', texto: cifra }),
        ],
      }),
      h('div', {
        clase: 'progreso__riel',
        atributos: {
          role: 'progressbar',
          'aria-valuemin': '0',
          'aria-valuemax': '100',
          'aria-valuenow': String(Math.round(acotada * 100)),
          'aria-label': `Progreso de ${nombre}`,
        },
        hijos: [relleno],
      }),
    ],
  });
}

/**
 * Enlaces de una tarjeta según su estado. §7.8.9: «Iniciar misión» si está
 * disponible, «Repetir misión» si ya se completó; el resto, ver detalles.
 *
 * @param {import('./fuente-misiones.js').ResumenMision} mision
 * @param {{hrefDe: Function, hrefReporte: Function, hrefEnCurso: string}} rutas
 * @returns {HTMLElement[]}
 */
function accionesDeTarjeta(mision, { hrefDe, hrefReporte, hrefEnCurso }) {
  const enlace = (texto, href, principal, accion) =>
    h('a', {
      clase: clases(
        'boton boton--pequeno',
        principal ? 'boton--primario' : 'boton--secundario',
        'mision-card__accion',
      ),
      texto,
      datos: { accion },
      atributos: { href, 'aria-label': `${texto}: ${mision.nombre}` },
    });
  const detalles = enlace('Ver detalles', hrefDe(mision), false, 'ver-detalles');
  const reporte = mision.ultimaEjecucionId
    ? enlace('Ver reporte', hrefReporte(mision.ultimaEjecucionId), false, 'ver-reporte')
    : null;

  switch (mision.estado) {
    case 'DISPONIBLE':
      return [enlace('Iniciar misión', hrefDe(mision, 'configurar'), true, 'iniciar'), detalles];
    case 'COMPLETADA':
      return [
        enlace('Repetir misión', hrefDe(mision, 'configurar'), true, 'repetir'),
        reporte,
        detalles,
      ].filter(Boolean);
    case 'EN_PROGRESO':
      return [enlace('Ver progreso', hrefEnCurso, true, 'ver-progreso'), detalles];
    case 'FALLIDA':
      return [reporte, detalles].filter(Boolean);
    default:
      return [detalles];
  }
}

/**
 * La tarjeta de una misión.
 *
 * @param {import('./fuente-misiones.js').ResumenMision} mision
 * @param {{hrefDe: Function, hrefReporte: Function, hrefEnCurso: string}} rutas
 * @returns {HTMLElement}
 */
export function tarjetaDeMision(mision, rutas) {
  const categoria = categoriaDe(mision.categoria);
  const idNombre = `mision-${mision.id}-nombre`;
  const recompensas = Array.isArray(mision.recompensasDestacadas)
    ? mision.recompensasDestacadas.slice(0, 3)
    : [];
  const duracion = textoDeDuracion(mision.duracionHoras);

  return h('article', {
    clase: 'tarjeta tarjeta--mision mision-card',
    datos: {
      mision: mision.id,
      estado: String(mision.estado ?? '').toLowerCase(),
      categoria: String(mision.categoria ?? '').toLowerCase(),
    },
    atributos: { 'aria-labelledby': idNombre },
    hijos: [
      h('div', {
        clase: 'mision-card__arte',
        hijos: [
          mision.imagen
            ? h('img', {
                clase: 'mision-card__imagen',
                atributos: { src: mision.imagen, alt: '', loading: 'lazy' },
              })
            : icono(categoria?.icono ?? 'mapa', {
                clase: 'icono mision-card__emblema',
                etiqueta: null,
              }),
          h('div', {
            clase: 'mision-card__chips',
            hijos: [chipDeCategoria(mision.categoria), selloDeMision(mision.estado)],
          }),
        ],
      }),
      h('div', {
        clase: 'mision-card__cuerpo',
        hijos: [
          h('h3', {
            clase: 'mision-card__nombre',
            texto: mision.nombre,
            atributos: { id: idNombre },
          }),
          mision.descripcionBreve
            ? h('p', { clase: 'mision-card__descripcion', texto: mision.descripcionBreve })
            : null,
          h('dl', {
            clase: 'mision-card__datos',
            hijos: [
              h('div', {
                hijos: [
                  h('dt', { texto: 'Dificultad' }),
                  h('dd', { hijos: [indicadorDeDificultad(mision.dificultad)] }),
                ],
              }),
              duracion
                ? h('div', {
                    hijos: [
                      h('dt', { texto: 'Duración' }),
                      h('dd', {
                        hijos: [icono('reloj', { etiqueta: null }), h('span', { texto: duracion })],
                      }),
                    ],
                  })
                : null,
              Number.isFinite(mision.nivelRecomendado)
                ? h('div', {
                    hijos: [
                      h('dt', { texto: 'Nivel recomendado' }),
                      h('dd', { texto: String(mision.nivelRecomendado) }),
                    ],
                  })
                : null,
            ],
          }),
          recompensas.length > 0
            ? h('div', {
                clase: 'mision-card__recompensas',
                hijos: [
                  h('p', { clase: 'mision-card__recompensas-titulo', texto: 'Recompensas' }),
                  h('ul', {
                    hijos: recompensas.map((recompensa) =>
                      h('li', {
                        hijos: [
                          icono('cofre', { etiqueta: null }),
                          h('span', { texto: recompensa }),
                        ],
                      }),
                    ),
                  }),
                ],
              })
            : null,
          mision.estado === 'EN_PROGRESO' && Number.isFinite(mision.progreso)
            ? progresoDeMision(mision.progreso, mision.nombre)
            : null,
          mision.estado === 'BLOQUEADA' && mision.motivoBloqueo
            ? h('p', {
                clase: 'mision-card__motivo',
                hijos: [
                  icono('candado', { etiqueta: null }),
                  h('span', { texto: mision.motivoBloqueo }),
                ],
              })
            : null,
        ],
      }),
      h('div', { clase: 'mision-card__acciones', hijos: accionesDeTarjeta(mision, rutas) }),
    ],
  });
}

/**
 * Los tres filtros del tablón, en un formulario que se envía solo al cambiar.
 *
 * @param {{alCambiar: (criterios: object) => void}} opciones
 * @returns {{elemento: HTMLFormElement, criterios: () => object, limpiar: () => void,
 *   hayFiltros: () => boolean}}
 */
function filtrosDelTablon({ alCambiar }) {
  const desplegable = (nombre, etiqueta, opciones) => {
    const id = `filtro-mision-${nombre}`;
    return h('div', {
      clase: 'desplegable',
      hijos: [
        h('label', { clase: 'campo__etiqueta', texto: etiqueta, atributos: { for: id } }),
        h('select', {
          clase: 'desplegable__control',
          atributos: { id, name: nombre },
          hijos: opciones.map((opcion) =>
            h('option', { texto: opcion.etiqueta, atributos: { value: opcion.id } }),
          ),
        }),
      ],
    });
  };
  const limpiar = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Quitar filtros',
    datos: { accion: 'quitar-filtros' },
    atributos: { type: 'reset' },
  });
  const elemento = h('form', {
    clase: 'misiones-filtros',
    atributos: { 'aria-label': 'Filtrar misiones' },
    hijos: [
      desplegable('dificultad', 'Dificultad', [
        { id: '', etiqueta: 'Todas las dificultades' },
        ...DIFICULTADES,
      ]),
      desplegable('estado', 'Estado', FILTRO_DE_ESTADO),
      desplegable('duracion', 'Duración', FILTRO_DE_DURACION),
      limpiar,
    ],
  });

  const criterios = () => ({
    dificultad: elemento.elements.dificultad.value,
    estado: elemento.elements.estado.value,
    duracion: elemento.elements.duracion.value,
  });
  const hayFiltros = () => Object.values(criterios()).some(Boolean);
  const actualizarLimpiar = () => {
    limpiar.hidden = !hayFiltros();
  };

  elemento.addEventListener('change', () => {
    actualizarLimpiar();
    alCambiar(criterios());
  });
  elemento.addEventListener('submit', (evento) => evento.preventDefault());
  elemento.addEventListener('reset', () => {
    // El `reset` vacía los campos DESPUÉS de este evento: se lee en la
    // siguiente vuelta.
    setTimeout(() => {
      actualizarLimpiar();
      alCambiar(criterios());
      elemento.elements.dificultad.focus();
    }, 0);
  });
  actualizarLimpiar();

  return {
    elemento,
    criterios,
    hayFiltros,
    limpiar: () => elemento.reset(),
  };
}

/**
 * Monta el tablón: pestañas de categoría, filtros, tarjetas y paginación.
 *
 * @param {HTMLElement} zona
 * @param {object} opciones
 * @param {import('./fuente-misiones.js').FuenteDeMisiones} opciones.fuente
 * @param {Function} opciones.hrefDe `(mision, ancla?) => url` del detalle
 * @param {Function} opciones.hrefReporte `(ejecucionId) => url`
 * @param {string} opciones.hrefEnCurso
 * @param {string} [opciones.categoriaInicial]
 * @returns {{recargar: () => Promise<void>}}
 */
export function montarTablon(
  zona,
  { fuente, hrefDe, hrefReporte, hrefEnCurso, categoriaInicial = 'HISTORIA' },
) {
  const rutas = { hrefDe, hrefReporte, hrefEnCurso };
  let categoria = categoriaDe(categoriaInicial)?.id ?? CATEGORIAS[0].id;
  let pagina = 0;
  let peticion = 0;
  /** Tras cambiar de página, el foco va al principio de la lista nueva. */
  let enfocarAlPintar = false;

  const paneles = new Map(
    CATEGORIAS.map((c) => [
      c.id,
      h('div', { clase: 'misiones-categoria', datos: { categoria: c.id.toLowerCase() } }),
    ]),
  );
  const zonaPestanas = h('div', { clase: 'misiones-tablon__pestanas' });
  const filtros = filtrosDelTablon({
    alCambiar: () => {
      pagina = 0;
      cargar();
    },
  });

  zona.replaceChildren(zonaPestanas);

  // `montarPestanas` pone la lista de pestañas y, detrás, los paneles. Los
  // filtros valen para las tres categorías: van entre las pestañas y el
  // panel, que es donde se leen. Montarlas llama ya a `alCambiar` con la
  // pestaña inicial, y eso hace la primera carga.
  montarPestanas(
    zonaPestanas,
    CATEGORIAS.map((c) => ({
      id: `categoria-${c.id.toLowerCase()}`,
      etiqueta: c.etiqueta,
      panel: paneles.get(c.id),
    })),
    {
      activa: `categoria-${categoria.toLowerCase()}`,
      hash: false,
      alCambiar: (id) => {
        categoria = id.replace('categoria-', '').toUpperCase();
        pagina = 0;
        cargar();
      },
    },
  );
  zonaPestanas.firstElementChild.after(filtros.elemento);

  function panelActual() {
    return paneles.get(categoria);
  }

  function resumenDeCategoria() {
    const actual = categoriaDe(categoria);
    return h('p', {
      clase: 'misiones-categoria__resumen',
      hijos: [icono(actual.icono, { etiqueta: null }), h('span', { texto: actual.resumen })],
    });
  }

  function vacio() {
    const actual = categoriaDe(categoria);
    if (filtros.hayFiltros()) {
      return estadoVacio({
        titulo: `Ninguna misión de ${actual.etiqueta} cumple estos filtros`,
        detalle: 'Prueba con otra dificultad, otro estado u otra duración.',
        accion: { texto: 'Quitar filtros', nombre: 'quitar-filtros', alPulsar: filtros.limpiar },
      });
    }
    return estadoVacio({
      titulo: `Todavía no hay misiones de ${actual.etiqueta}`,
      detalle:
        'Cuando se publiquen aparecerán aquí. Mientras tanto, mira las otras categorías o prepara la estrategia de tu héroe.',
    });
  }

  async function cargar() {
    peticion += 1;
    const numero = peticion;
    const panel = panelActual();
    vaciar(panel).append(
      resumenDeCategoria(),
      estadoDeCarga({ filas: 3, etiqueta: 'Cargando misiones…' }),
    );
    let respuesta;
    try {
      respuesta = await fuente.tablero({ categoria, ...filtros.criterios(), pagina });
    } catch (fallo) {
      if (numero !== peticion) {
        return;
      }
      console.error('No se pudo cargar el tablón de misiones', fallo);
      vaciar(panel).append(
        resumenDeCategoria(),
        estadoDeError({
          titulo: 'No pudimos cargar las misiones',
          detalle: 'Revisa tu conexión e inténtalo de nuevo.',
          alReintentar: cargar,
        }),
      );
      return;
    }
    if (numero !== peticion) {
      // El jugador ya cambió de pestaña o de filtro: esta respuesta es vieja.
      return;
    }
    pintar(panel, respuesta);
  }

  function pintar(panel, respuesta) {
    const misiones = Array.isArray(respuesta?.misiones) ? respuesta.misiones : [];
    vaciar(panel).append(resumenDeCategoria());
    if (misiones.length === 0) {
      panel.append(vacio());
      return;
    }
    const total = Number.isFinite(respuesta.total) ? respuesta.total : misiones.length;
    panel.append(
      h('p', {
        clase: 'misiones-categoria__cuenta',
        texto: total === 1 ? '1 misión' : `${total} misiones`,
        atributos: { role: 'status', tabindex: '-1' },
      }),
      h('ul', {
        clase: 'misiones-rejilla',
        atributos: { 'aria-label': `Misiones de ${categoriaDe(categoria).etiqueta}` },
        hijos: misiones
          .slice(0, MISIONES_POR_PAGINA)
          .map((mision) => h('li', { hijos: [tarjetaDeMision(mision, rutas)] })),
      }),
    );
    const totalPaginas = Number.isInteger(respuesta.totalPaginas) ? respuesta.totalPaginas : 1;
    if (totalPaginas > 1) {
      const control = construirPaginacion(
        { paginaActual: Math.min(pagina, totalPaginas - 1), totalPaginas },
        (numeroPagina) => {
          pagina = numeroPagina;
          enfocarAlPintar = true;
          cargar();
        },
      );
      control.setAttribute('aria-label', 'Páginas de misiones');
      panel.append(control);
    }
    if (enfocarAlPintar) {
      enfocarAlPintar = false;
      panel.querySelector('.misiones-categoria__cuenta').focus();
    }
  }

  return { recargar: cargar };
}

/**
 * El tablón cuando las misiones todavía no existen: qué es cada categoría,
 * sin fingir tarjetas.
 *
 * @returns {HTMLElement}
 */
export function tablonSinAbrir() {
  return h('section', {
    clase: 'misiones-sin-abrir',
    atributos: { 'aria-labelledby': 'misiones-sin-abrir-titulo' },
    hijos: [
      h('h2', {
        clase: 'misiones-sin-abrir__titulo',
        texto: 'Así serán las misiones',
        atributos: { id: 'misiones-sin-abrir-titulo' },
      }),
      h('p', {
        clase: 'misiones-sin-abrir__texto',
        texto:
          'Cada misión dice cuánto dura, qué nivel recomienda, a qué enemigos y a qué jefe final se enfrenta tu héroe, y qué puede ganar: créditos, objetos y épicas de enemigos Máster.',
      }),
      h('ul', {
        clase: 'misiones-sin-abrir__categorias',
        hijos: CATEGORIAS.map((c) =>
          h('li', {
            clase: 'tarjeta misiones-sin-abrir__categoria',
            datos: { categoria: c.id.toLowerCase() },
            hijos: [
              icono(c.icono, { etiqueta: null, clase: 'icono misiones-sin-abrir__icono' }),
              h('h3', { texto: c.etiqueta }),
              h('p', { texto: c.resumen }),
            ],
          }),
        ),
      }),
    ],
  });
}
