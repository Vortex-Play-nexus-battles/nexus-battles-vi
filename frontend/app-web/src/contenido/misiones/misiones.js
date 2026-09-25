/**
 * Misiones — UXC-5. La vista del módulo de misiones (§7.8, M11).
 *
 * Tres modos, según la dirección:
 *
 *   - `misiones.html` → el tablón, con cuatro secciones: Tablón (banner
 *     rotativo, categorías, filtros y tarjetas), En curso, Historial y
 *     Estrategia. La sección activa va en el `#` para poder enlazarla.
 *   - `misiones.html?mision=<id>` → el detalle, con el configurador de la
 *     misión (`#configurar`).
 *   - `misiones.html?reporte=<id>` → el reporte de una misión terminada.
 *
 * ## Sin servicio de misiones
 *
 * Es el caso de hoy (ver `fuente-misiones.js`). La vista no finge un tablón:
 * dice qué pasa —las misiones todavía no están abiertas—, por qué —no hay
 * ninguna publicada—, y qué se puede hacer ya —preparar la estrategia del
 * héroe, que valida el servicio de héroes de verdad, o jugar en línea—. Las
 * secciones En curso e Historial dicen lo mismo en su sitio. No hay
 * «próximamente» ni tarjetas de ejemplo.
 *
 * @module contenido/misiones/misiones
 */

import { h, vaciar } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { aviso } from '../../comun/ui/aviso.js';
import { confirmar } from '../../comun/ui/dialogo.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../../comun/ui/estado-vista.js';
import { montarPestanas } from '../../comun/ui/pestanas.js';
import { fuenteDeMisiones } from './fuente-misiones.js';
import { montarBannerDeMisiones } from './banner-misiones.js';
import { montarTablon, tablonSinAbrir } from './tablon-misiones.js';
import { detalleDeMision } from './detalle-mision.js';
import { montarEnCurso } from './en-curso.js';
import { historialDeMisiones, reporteDeMision } from './reporte-mision.js';
import { constructorDeEstrategia } from './constructor-estrategia.js';
import { textoDeDuracion } from './modelo-misiones.js';

/** Las cuatro secciones del tablón, en su orden. */
export const SECCIONES_DE_MISIONES = Object.freeze([
  { id: 'tablon', etiqueta: 'Tablón' },
  { id: 'en-curso', etiqueta: 'En curso' },
  { id: 'historial', etiqueta: 'Historial' },
  { id: 'estrategia', etiqueta: 'Estrategia' },
]);

const TITULO_BASE = 'Misiones · Nexus Battles VI';

/**
 * Las direcciones que usa la vista, relativas a la propia página: el tablón,
 * sus secciones, el detalle y el reporte, y las de otras vistas.
 *
 * @returns {{hrefTablon: string, hrefEnCurso: string, hrefHistorial: string,
 *   hrefEstrategia: string, hrefDe: (mision: {id: string}, ancla?: string) => string,
 *   hrefReporte: (ejecucionId: string) => string, hrefEquipamiento: string,
 *   hrefJugar: string, hrefTienda: string}}
 */
export function rutasDeMisiones() {
  const pagina = 'misiones.html';
  return {
    hrefTablon: pagina,
    hrefEnCurso: `${pagina}#en-curso`,
    hrefHistorial: `${pagina}#historial`,
    hrefEstrategia: `${pagina}#estrategia`,
    hrefDe: (mision, ancla = null) =>
      `${pagina}?mision=${encodeURIComponent(mision.id)}${ancla ? `#${ancla}` : ''}`,
    hrefReporte: (ejecucionId) => `${pagina}?reporte=${encodeURIComponent(ejecucionId)}`,
    hrefEquipamiento: '../inventario/inventario.html#equipamiento',
    hrefJugar: '../../plataforma/salas-partidas/batallas.html',
    hrefTienda: '../../cuentas/tienda.html',
  };
}

/**
 * El aviso de «todavía no están abiertas», con sus dos salidas.
 *
 * @param {{alPrepararEstrategia: () => void, hrefJugar: string}} opciones
 * @returns {HTMLElement}
 */
export function avisoSinAbrir({ alPrepararEstrategia, hrefJugar }) {
  const preparar = h('button', {
    clase: 'boton boton--primario boton--pequeno',
    datos: { accion: 'preparar-estrategia' },
    atributos: { type: 'button' },
    hijos: [
      icono('escudo-check', { etiqueta: null }),
      h('span', { texto: 'Preparar la estrategia' }),
    ],
  });
  preparar.addEventListener('click', alPrepararEstrategia);
  return h('section', {
    clase: 'tarjeta misiones-estado',
    datos: { estado: 'sin-abrir' },
    atributos: { 'aria-labelledby': 'misiones-estado-titulo' },
    hijos: [
      icono('mapa', { etiqueta: null, clase: 'icono misiones-estado__icono' }),
      h('div', {
        clase: 'misiones-estado__cuerpo',
        hijos: [
          h('h2', {
            clase: 'misiones-estado__titulo',
            texto: 'Las misiones todavía no están abiertas',
            atributos: { id: 'misiones-estado-titulo' },
          }),
          h('p', {
            texto:
              'Aún no hay ninguna misión publicada, así que no se puede enviar a ningún héroe ni hay misiones en curso ni historial que enseñar.',
          }),
          h('p', {
            texto:
              'Lo que ya puedes hacer: preparar la estrategia de combate de tu héroe y comprobarla con las reglas del juego, o jugar en línea mientras tanto.',
          }),
          h('div', {
            clase: 'misiones-estado__acciones',
            hijos: [
              preparar,
              h('a', {
                clase: 'boton boton--secundario boton--pequeno',
                texto: 'Jugar en línea',
                atributos: { href: hrefJugar },
                datos: { accion: 'jugar' },
              }),
            ],
          }),
        ],
      }),
    ],
  });
}

/**
 * El resumen que se confirma antes de mandar al héroe (RF-MIS-004: «presenta
 * el resumen de la misión, exige confirmación»).
 *
 * @param {object} mision
 * @param {{heroe: string, rotaciones: Array<{pasos: string[]}>}} envio
 * @returns {HTMLElement}
 */
export function resumenDeMatricula(mision, { heroe, rotaciones }) {
  const duracion = textoDeDuracion(mision.duracionHoras);
  return h('div', {
    clase: 'pila pila--compacta misiones-confirmacion',
    hijos: [
      h('dl', {
        clase: 'misiones-confirmacion__datos',
        hijos: [
          ['Misión', mision.nombre],
          ['Duración', duracion],
          ['Héroe', heroe],
          [
            'Estrategia',
            rotaciones.length > 0
              ? rotaciones.map((r, i) => `${i + 1}. ${r.pasos.join(' → ')}`).join(' · ')
              : 'Ataque básico',
          ],
        ]
          .filter(([, valor]) => valor)
          .map(([etiqueta, valor]) =>
            h('div', { hijos: [h('dt', { texto: etiqueta }), h('dd', { texto: valor })] }),
          ),
      }),
      h('p', {
        clase: 'misiones-confirmacion__advertencia',
        hijos: [
          icono('candado', { etiqueta: null }),
          h('span', {
            texto: `${heroe} queda bloqueado${duracion ? ` ${duracion}` : ''}: no podrá jugar en línea, entrar en torneos ni cambiar su equipamiento hasta que termine o la canceles.`,
          }),
        ],
      }),
    ],
  });
}

/**
 * Monta la vista.
 *
 * @param {Document} documento
 * @param {object} [opciones]
 * @param {import('./fuente-misiones.js').FuenteDeMisiones} [opciones.fuente]
 * @param {string|null} [opciones.identidad] apodo de la sesión
 * @param {Location|URL} [opciones.ubicacion]
 * @param {(url: string) => void} [opciones.navegar]
 * @param {object} [opciones.estrategia] inyecciones del configurador (pruebas)
 * @returns {Promise<'sin-abrir'|'tablon'|'detalle'|'reporte'>}
 */
export async function montarMisiones(
  documento,
  {
    fuente = fuenteDeMisiones(),
    identidad = globalThis.sessionStorage?.getItem('nexus.apodoActual') ?? null,
    ubicacion = globalThis.location,
    navegar = (url) => {
      globalThis.location.href = url;
    },
    estrategia: inyeccionesDeEstrategia = {},
  } = {},
) {
  const raiz = documento.querySelector('[data-vista="misiones"]');
  const rutas = rutasDeMisiones();
  const parametros = new URLSearchParams(ubicacion.search ?? '');

  const nuevoConfigurador = (opciones) =>
    constructorDeEstrategia({
      identidad,
      hrefTienda: rutas.hrefTienda,
      ...inyeccionesDeEstrategia,
      ...opciones,
    });

  if (fuente.disponible && parametros.get('mision')) {
    await montarDetalle(parametros.get('mision'));
    return 'detalle';
  }
  if (fuente.disponible && parametros.get('reporte')) {
    await montarReporte(parametros.get('reporte'));
    return 'reporte';
  }
  return montarTablero();

  /* --------------------------------------------------------------- tablón */

  function montarTablero() {
    documento.title = TITULO_BASE;
    const zonaAviso = h('div', { clase: 'misiones__aviso', datos: { zona: 'aviso' } });
    const zonaBanner = h('div', { clase: 'misiones__banner', datos: { zona: 'banner' } });
    zonaBanner.hidden = true;
    const zonaSecciones = h('div', { clase: 'misiones__secciones', datos: { zona: 'secciones' } });
    const paneles = new Map(
      SECCIONES_DE_MISIONES.map((s) => [
        s.id,
        h('section', { clase: 'misiones__panel', datos: { seccion: s.id } }),
      ]),
    );

    raiz.replaceChildren(
      h('header', {
        clase: 'encabezado-pagina',
        hijos: [
          h('div', {
            clase: 'pila pila--ajustada',
            hijos: [
              h('h1', { texto: 'Misiones' }),
              h('p', {
                clase: 't-meta',
                texto:
                  'Aventuras contra el entorno: tu héroe pelea solo, dirigido por la IA con la estrategia que le prepares, y vuelve con créditos, objetos y épicas.',
              }),
            ],
          }),
        ],
      }),
      zonaAviso,
      zonaBanner,
      zonaSecciones,
    );

    const iniciada = parametros.get('iniciada');
    if (iniciada && fuente.disponible) {
      zonaAviso.append(
        aviso({
          tono: 'exito',
          titulo: 'Tu héroe salió a la misión',
          detalle: 'Aquí ves cuánto le queda. Te avisaremos cuando termine.',
        }),
      );
    }

    // Cada sección se monta la primera vez que se enseña: abrir el tablón no
    // pide el inventario, y abrir «Estrategia» no pide el tablón.
    const montadas = new Set();
    let pestanas = null;
    const configurador = nuevoConfigurador({ modo: 'estrategia', nivelTitulo: 2 });

    const montarSeccion = (id) => {
      if (montadas.has(id)) {
        return;
      }
      montadas.add(id);
      const panel = paneles.get(id);
      if (id === 'estrategia') {
        panel.append(configurador.elemento);
        configurador.cargar();
        return;
      }
      if (!fuente.disponible) {
        panel.append(seccionSinAbrir(id));
        return;
      }
      if (id === 'tablon') {
        montarTablon(panel, {
          fuente,
          hrefDe: rutas.hrefDe,
          hrefReporte: rutas.hrefReporte,
          hrefEnCurso: rutas.hrefEnCurso,
        });
      } else if (id === 'en-curso') {
        montarEnCurso(panel, {
          fuente,
          hrefDe: rutas.hrefDe,
          hrefEquipamiento: rutas.hrefEquipamiento,
          hrefTablon: rutas.hrefTablon,
          alContar: (cuantas) => contarEnCurso(cuantas),
        });
      } else if (id === 'historial') {
        montarHistorial(panel);
      }
    };

    const irAEstrategia = () => {
      pestanas.mostrar('estrategia');
      paneles.get('estrategia').querySelector('.estrategia__titulo')?.focus();
    };

    function seccionSinAbrir(id) {
      if (id === 'tablon') {
        return tablonSinAbrir();
      }
      return estadoVacio(
        id === 'en-curso'
          ? {
              titulo: 'Ningún héroe está en misión',
              detalle:
                'Las misiones todavía no están abiertas. Cuando lo estén, aquí verás cuánto le queda a cada héroe que envíes.',
              accion: {
                texto: 'Preparar la estrategia',
                nombre: 'preparar-estrategia',
                alPulsar: irAEstrategia,
              },
            }
          : {
              titulo: 'Todavía no hay historial',
              detalle:
                'Las misiones todavía no están abiertas. Cada una que termines quedará aquí con su reporte.',
              accion: {
                texto: 'Preparar la estrategia',
                nombre: 'preparar-estrategia',
                alPulsar: irAEstrategia,
              },
            },
      );
    }

    function contarEnCurso(cuantas) {
      const cara = zonaSecciones.querySelector('#pestana-en-curso .pestana__cara');
      if (cara) {
        cara.textContent = cuantas > 0 ? `En curso (${cuantas})` : 'En curso';
      }
    }

    pestanas = montarPestanas(
      zonaSecciones,
      SECCIONES_DE_MISIONES.map((s) => ({
        id: s.id,
        etiqueta: s.etiqueta,
        panel: paneles.get(s.id),
      })),
      { activa: 'tablon', alCambiar: montarSeccion },
    );

    if (!fuente.disponible) {
      zonaAviso.append(
        avisoSinAbrir({ alPrepararEstrategia: irAEstrategia, hrefJugar: rutas.hrefJugar }),
      );
      return 'sin-abrir';
    }

    montarBannerDeMisiones(zonaBanner, { fuente, hrefDe: rutas.hrefDe });
    return 'tablon';
  }

  async function montarHistorial(panel) {
    vaciar(panel).append(estadoDeCarga({ filas: 3, etiqueta: 'Cargando tu historial…' }));
    try {
      const historial = await fuente.historial();
      vaciar(panel).append(
        historialDeMisiones(historial, {
          hrefReporte: rutas.hrefReporte,
          hrefTablon: rutas.hrefTablon,
        }),
      );
    } catch (fallo) {
      console.error('No se pudo leer el historial de misiones', fallo);
      vaciar(panel).append(
        estadoDeError({
          titulo: 'No pudimos cargar tu historial',
          detalle: 'Revisa tu conexión e inténtalo de nuevo.',
          alReintentar: () => montarHistorial(panel),
        }),
      );
    }
  }

  /* -------------------------------------------------------------- detalle */

  async function montarDetalle(id) {
    raiz.replaceChildren(estadoDeCarga({ filas: 4, etiqueta: 'Cargando la misión…' }));
    let mision;
    try {
      mision = await fuente.detalle(id);
    } catch (fallo) {
      console.error('No se pudo leer la misión', fallo);
      raiz.replaceChildren(
        fallo?.status === 404
          ? estadoVacio({
              titulo: 'Esa misión no existe o ya no está publicada',
              detalle:
                'Puede que haya terminado su tiempo limitado. En el tablón están las que sí puedes jugar.',
              accion: { texto: 'Ir al tablón', href: rutas.hrefTablon },
            })
          : estadoDeError({
              titulo: 'No pudimos cargar la misión',
              detalle: 'Revisa tu conexión e inténtalo de nuevo.',
              alReintentar: () => montarDetalle(id),
            }),
      );
      return;
    }

    documento.title = `${mision.nombre} · ${TITULO_BASE}`;
    const configurador = nuevoConfigurador({
      modo: 'matricula',
      titulo: 'Tu héroe y su estrategia',
      nivelTitulo: 3,
      alCambiar: (lista) => detalle.habilitarInicio(lista !== null),
    });
    const detalle = detalleDeMision(mision, {
      hrefTablon: rutas.hrefTablon,
      hrefReporte: rutas.hrefReporte,
      hrefEnCurso: rutas.hrefEnCurso,
      configurador,
      urlParaCompartir: new URL(rutas.hrefDe(mision), ubicacion.href).href,
      alMarcarFavorita: (favorita) => fuente.marcarFavorita(mision.id, favorita),
      alIniciar: () => iniciar(mision, configurador, detalle),
    });
    raiz.replaceChildren(detalle.elemento);
    configurador.cargar();

    const destino =
      ubicacion.hash === '#configurar'
        ? raiz.querySelector('#mision-seccion-configurar')
        : raiz.querySelector('.mision-detalle__nombre');
    destino?.focus();
    if (ubicacion.hash === '#configurar') {
      destino?.scrollIntoView?.({ block: 'start' });
    }
  }

  async function iniciar(mision, configurador, detalle) {
    const envio = configurador.estrategia();
    if (!envio) {
      return;
    }
    const heroe = envio.heroeNombre ?? 'Tu héroe';
    const seguro = await confirmar({
      titulo: `¿Enviar a ${heroe} a «${mision.nombre}»?`,
      cuerpo: resumenDeMatricula(mision, { heroe, rotaciones: envio.rotaciones }),
      textoConfirmar: mision.estado === 'COMPLETADA' ? 'Repetir misión' : 'Iniciar misión',
      textoCancelar: 'Volver',
      peligro: false,
    });
    if (!seguro) {
      return;
    }
    detalle.ocupado(true);
    try {
      await fuente.matricular({
        misionId: mision.id,
        heroeId: envio.heroeId,
        rotaciones: envio.rotaciones,
      });
    } catch (fallo) {
      console.error('No se pudo iniciar la misión', fallo);
      detalle.ocupado(false);
      vaciar(detalle.zonaAviso).append(
        aviso({
          tono: 'error',
          titulo: 'No pudimos iniciar la misión',
          detalle:
            fallo?.detalle ??
            'Tu héroe sigue libre. Revisa tu conexión e inténtalo de nuevo en un momento.',
        }),
      );
      detalle.zonaAviso.scrollIntoView?.({ block: 'center' });
      return;
    }
    navegar(`${rutas.hrefTablon}?iniciada=${encodeURIComponent(mision.id)}#en-curso`);
  }

  /* -------------------------------------------------------------- reporte */

  async function montarReporte(ejecucionId) {
    raiz.replaceChildren(estadoDeCarga({ filas: 4, etiqueta: 'Cargando el reporte…' }));
    try {
      const reporte = await fuente.reporte(ejecucionId);
      documento.title = `Reporte: ${reporte.mision?.nombre ?? 'misión'} · ${TITULO_BASE}`;
      raiz.replaceChildren(
        reporteDeMision(reporte, {
          hrefTablon: rutas.hrefTablon,
          hrefHistorial: rutas.hrefHistorial,
          hrefDe: rutas.hrefDe,
        }),
      );
      raiz.querySelector('.mision-detalle__nombre')?.focus();
    } catch (fallo) {
      console.error('No se pudo leer el reporte', fallo);
      raiz.replaceChildren(
        estadoDeError({
          titulo: 'No pudimos cargar el reporte',
          detalle: 'Revisa tu conexión e inténtalo de nuevo.',
          alReintentar: () => montarReporte(ejecucionId),
        }),
      );
    }
  }
}
