/**
 * Mi cuenta · Estadísticas — UXC-9 (§7.1.1 «Mi Cuenta»: «visualización de
 * estadísticas y logros del jugador»; RF-USR-012).
 *
 * Hasta ahora Mi cuenta no decía nada de cómo le va a quien juega, aunque dos
 * servicios ya lo publican. Esta pestaña lo junta **sin calcular por su
 * cuenta nada que el servidor no haya decidido**:
 *
 * | Bloque        | Endpoint                                  | Contrato            |
 * |---------------|-------------------------------------------|---------------------|
 * | Tus batallas  | `GET /api/v1/partidas/mias?pagina&tamano` | salas-partidas 1.7.0 |
 * | Tus misiones  | `GET /api/v1/misiones/historial`          | misiones 1.0.0      |
 *
 * - **Partidas jugadas** es `totalElementos` del servidor. El resultado de
 *   cada una (victoria, derrota, empate) también viene del servidor, desde el
 *   punto de vista de quien pregunta. El recuento de resultados se hace sobre
 *   las partidas que se ven y lo dice («De estas 16…»): no hay agregado de
 *   carrera publicado, y un porcentaje de victorias inventado no se muestra.
 * - **Misiones** suma los contadores por categoría que da el servidor
 *   (`porCategoria`) y cuenta las épicas de su colección (`epicas`).
 * - **Torneos y logros** no tienen agregado en ningún contrato: el bloque lo
 *   dice y lleva a donde sí está esa información (Torneos, historial de
 *   misiones). Nada de «próximamente» mudo.
 *
 * Cada bloque tiene sus cuatro estados (RNF-USA-003) y falla por separado: si
 * misiones no responde, las batallas se siguen viendo.
 *
 * @module estadisticas-cuenta
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { h, vaciar } from '../comun/ui/dom.js';
import { fechaHora, numero } from '../comun/ui/formato.js';
import { tarjetaDeCifra } from '../comun/ui/tarjeta.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../comun/ui/estado-vista.js';
import { construirPaginacion } from '../comun/paginacion.js';
import {
  distintivoDeResultado,
  nombreDeModalidad,
  recuentoDeResultados,
} from '../comun/ui/juego/partida.js';
import { RUTAS, resolver } from '../comun/sesion.js';
import { misPartidas } from '../plataforma/salas-partidas/cliente-salas.js';
import { fuenteHttpDeMisiones } from '../contenido/misiones/fuente-misiones.js';

/** Dieciséis por página, como el listado de salas (RNF-USA-001). */
export const PARTIDAS_POR_PAGINA = 16;

const RUTA_MISIONES = '../contenido/misiones/misiones.html';
const RUTA_SALA = '../plataforma/salas-partidas/sala-batalla.html';

/**
 * «Una partida» / «16 partidas».
 *
 * @param {number} cantidad
 * @param {string} singular
 * @param {string} plural
 * @returns {string}
 */
function cuantas(cantidad, singular, plural) {
  return `${numero(cantidad)} ${cantidad === 1 ? singular : plural}`;
}

/**
 * La frase del recuento, siempre con de cuántas partidas sale.
 *
 * @param {Array<object>} partidas las que se muestran
 * @returns {string}
 */
export function fraseDelRecuento(partidas) {
  const { victorias, derrotas, empates, enCurso } = recuentoDeResultados(partidas);
  const partes = [
    cuantas(victorias, 'victoria', 'victorias'),
    cuantas(derrotas, 'derrota', 'derrotas'),
    cuantas(empates, 'empate', 'empates'),
  ];
  if (enCurso > 0) {
    partes.push(`${numero(enCurso)} en curso`);
  }
  const total = partidas.length;
  const cabeza =
    total === 1 ? 'De la partida de esta página' : `De las ${numero(total)} de esta página`;
  return `${cabeza}: ${partes.join(' · ')}.`;
}

/**
 * La fila de una partida.
 *
 * @param {object} partida `ResumenDePartida`
 * @returns {HTMLTableRowElement}
 */
function filaDePartida(partida) {
  const cuando = partida.finalizadaEn ?? partida.iniciadaEn;
  return h('tr', {
    datos: { partida: partida.id, estado: partida.estado ?? '' },
    hijos: [
      h('td', { hijos: [distintivoDeResultado(partida)] }),
      h('td', { texto: partida.heroe || 'Héroe sin nombre' }),
      h('td', { texto: nombreDeModalidad(partida.modalidad) }),
      h('td', {
        texto: Number.isInteger(partida.participantes) ? numero(partida.participantes) : '—',
      }),
      h('td', { clase: 't-meta', texto: cuando ? fechaHora(cuando) : '—' }),
      h('td', { hijos: [accionDePartida(partida)] }),
    ],
  });
}

/**
 * Qué se puede hacer con una partida desde la tabla: volver a la que sigue en
 * curso, o ver cómo terminó la que ya acabó (el campo de combate la pinta con
 * su desenlace). Auditoría de DEV del 30-sep: la columna «Acción» salía vacía
 * en las terminadas.
 *
 * @param {object} partida `ResumenDePartida`
 * @returns {HTMLElement}
 */
export function accionDePartida(partida) {
  if (partida.idSala && (partida.estado === 'EN_CURSO' || partida.estado === 'FINALIZADA')) {
    const enCurso = partida.estado === 'EN_CURSO';
    const consulta = new URLSearchParams({ sala: partida.idSala });
    if (!enCurso && partida.id) {
      consulta.set('partida', partida.id);
    }
    return h('a', {
      clase: 'boton boton--secundario boton--pequeno',
      texto: enCurso ? 'Volver a la partida' : 'Ver resultado',
      atributos: { href: `${RUTA_SALA}?${consulta}` },
    });
  }
  // Sin sala a la que ir no hay acción, y se dice en vez de dejar la celda vacía.
  return h('span', { clase: 't-meta', texto: '—', atributos: { 'aria-label': 'Sin acción' } });
}

/**
 * Tus batallas: cuántas, el recuento de la página y la tabla paginada.
 *
 * @param {HTMLElement} zona
 * @param {{fetchImpl: Function, pagina?: number, alPintar?: Function}} opciones
 * @returns {Promise<void>}
 */
export async function pintarPartidas(zona, { fetchImpl, pagina = 0, alPintar = null }) {
  pintarEstado(zona, estadoDeCarga({ filas: 3, etiqueta: 'Buscando tus partidas…' }));

  let respuesta;
  try {
    respuesta = await misPartidas({ pagina, tamano: PARTIDAS_POR_PAGINA }, { fetchImpl });
  } catch {
    vaciar(zona).append(
      estadoDeError({
        titulo: 'Tus partidas no están disponibles',
        detalle:
          'El servicio de batallas no responde ahora mismo. Tus partidas siguen guardadas: vuelve a intentarlo.',
        alReintentar: () => pintarPartidas(zona, { fetchImpl, pagina, alPintar }),
      }),
    );
    return;
  }

  const partidas = Array.isArray(respuesta?.contenido) ? respuesta.contenido : [];
  const total = Number.isInteger(respuesta?.totalElementos)
    ? respuesta.totalElementos
    : partidas.length;

  if (total === 0 || (partidas.length === 0 && pagina === 0)) {
    vaciar(zona).append(
      estadoVacio({
        titulo: 'Todavía no has jugado ninguna batalla',
        detalle:
          'Cada partida que juegues quedará aquí con su resultado, el héroe con el que jugaste y cuándo fue.',
        accion: { texto: 'Jugar una batalla', href: resolver(RUTAS.crearSala) },
      }),
    );
    return;
  }

  const cifras = h('div', {
    clase: 'cuenta-estadisticas__cifras',
    hijos: [
      tarjetaDeCifra({
        etiqueta: 'Partidas jugadas',
        valor: numero(total),
        detalle: 'En curso y terminadas, desde que tienes cuenta.',
      }),
    ],
  });

  const tabla = h('table', {
    clase: 'tabla',
    datos: { zona: 'mis-partidas' },
    hijos: [
      h('caption', {
        clase: 'solo-lectores',
        texto: 'Tus partidas, de la más reciente a la más antigua',
      }),
      h('thead', {
        hijos: [
          h('tr', {
            hijos: ['Resultado', 'Héroe', 'Modalidad', 'Jugadores', 'Cuándo', 'Acción'].map(
              (titulo) => h('th', { texto: titulo, atributos: { scope: 'col' } }),
            ),
          }),
        ],
      }),
      h('tbody', { hijos: partidas.map(filaDePartida) }),
    ],
  });

  const hijos = [
    cifras,
    h('p', { clase: 't-meta', datos: { zona: 'recuento' }, texto: fraseDelRecuento(partidas) }),
    // Región desplazable alcanzable con teclado, como el historial de pagos:
    // a 375 px la tabla se desplaza dentro de su tarjeta.
    h('div', {
      clase: 'tabla-envoltorio',
      atributos: {
        tabindex: '0',
        role: 'region',
        'aria-label': 'Tus partidas, desplazable en horizontal',
      },
      hijos: [tabla],
    }),
  ];

  const totalPaginas = Number.isInteger(respuesta?.totalPaginas) ? respuesta.totalPaginas : 1;
  if (totalPaginas > 1) {
    hijos.push(
      construirPaginacion(
        { paginaActual: Math.min(pagina, totalPaginas - 1), totalPaginas },
        (nueva) =>
          pintarPartidas(zona, { fetchImpl, pagina: nueva, alPintar }).then(() =>
            zona.querySelector('[aria-current="page"]')?.focus(),
          ),
        { etiqueta: 'Páginas de tus partidas' },
      ),
    );
  }

  vaciar(zona).append(...hijos);
  alPintar?.(respuesta);
}

/**
 * Tus misiones: cumplidas, fallidas y épicas, con enlace al historial.
 *
 * @param {HTMLElement} zona
 * @param {{fetchImpl: Function}} opciones
 * @returns {Promise<void>}
 */
export async function pintarMisiones(zona, { fetchImpl }) {
  pintarEstado(zona, estadoDeCarga({ filas: 2, etiqueta: 'Buscando tus misiones…' }));

  let historial;
  try {
    historial = await fuenteHttpDeMisiones({ fetchImpl }).historial();
  } catch {
    vaciar(zona).append(
      estadoDeError({
        titulo: 'Tus misiones no están disponibles',
        detalle:
          'El servicio de misiones no responde ahora mismo. Lo que tus héroes consiguieron sigue guardado: vuelve a intentarlo.',
        alReintentar: () => pintarMisiones(zona, { fetchImpl }),
      }),
    );
    return;
  }

  const porCategoria = Array.isArray(historial?.porCategoria) ? historial.porCategoria : [];
  const cumplidas = porCategoria.reduce((suma, c) => suma + (Number(c?.completadas) || 0), 0);
  const fallidas = porCategoria.reduce((suma, c) => suma + (Number(c?.fallidas) || 0), 0);
  const epicas = Array.isArray(historial?.epicas) ? historial.epicas : [];

  if (cumplidas + fallidas === 0 && epicas.length === 0) {
    vaciar(zona).append(
      estadoVacio({
        titulo: 'Todavía no has terminado ninguna misión',
        detalle:
          'Cuando un héroe vuelva de una misión verás aquí cuántas cumpliste y las épicas que ganaste.',
        accion: { texto: 'Elegir una misión', href: RUTA_MISIONES },
      }),
    );
    return;
  }

  vaciar(zona).append(
    h('div', {
      clase: 'cuenta-estadisticas__cifras',
      hijos: [
        tarjetaDeCifra({ etiqueta: 'Misiones cumplidas', valor: numero(cumplidas) }),
        tarjetaDeCifra({ etiqueta: 'Misiones fallidas', valor: numero(fallidas) }),
        tarjetaDeCifra({
          etiqueta: 'Épicas obtenidas',
          valor: numero(epicas.length),
          detalle: 'Las que ganaste a un Máster.',
        }),
      ],
    }),
    h('div', {
      clase: 'tarjeta__acciones',
      hijos: [
        h('a', {
          clase: 'boton boton--secundario',
          texto: 'Ver el historial de misiones',
          atributos: { href: `${RUTA_MISIONES}#historial` },
        }),
      ],
    }),
  );
}

/**
 * Torneos y logros: lo que ningún servicio agrega todavía, dicho con su
 * porqué y con adónde ir.
 *
 * @param {HTMLElement} zona
 */
export function pintarTorneosYLogros(zona) {
  const nota = (titulo, texto) =>
    h('div', {
      clase: 'cuenta-notas__nota',
      hijos: [h('dt', { texto: titulo }), h('dd', { texto })],
    });
  vaciar(zona).append(
    h('dl', {
      clase: 'cuenta-notas',
      hijos: [
        nota(
          'Torneos',
          'Aún no hay un resumen de tus torneos en tu cuenta. Tu equipo, tu camino en el árbol y tu premio están en la vista de cada torneo.',
        ),
        nota(
          'Logros',
          'Todavía no hay logros que ganar: su catálogo no está definido. Lo que sí cuenta ya son tus épicas de misión, arriba.',
        ),
      ],
    }),
    h('div', {
      clase: 'tarjeta__acciones',
      hijos: [
        h('a', {
          clase: 'boton boton--secundario',
          texto: 'Ir a Torneos',
          atributos: { href: resolver(RUTAS.torneos) },
        }),
      ],
    }),
  );
}

/**
 * Monta la pestaña. Los tres bloques se piden a la vez y fallan por separado.
 *
 * @param {ParentNode} raiz
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {{recargar: () => Promise<void>}}
 */
export function montarEstadisticas(raiz, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const zonaPartidas = raiz.querySelector('[data-zona="estadisticas-partidas"]');
  const zonaMisiones = raiz.querySelector('[data-zona="estadisticas-misiones"]');
  const zonaPendiente = raiz.querySelector('[data-zona="estadisticas-pendiente"]');

  async function recargar() {
    if (zonaPendiente) {
      pintarTorneosYLogros(zonaPendiente);
    }
    await Promise.all([
      zonaPartidas ? pintarPartidas(zonaPartidas, { fetchImpl }) : null,
      zonaMisiones ? pintarMisiones(zonaMisiones, { fetchImpl }) : null,
    ]);
  }

  recargar();
  return { recargar };
}
