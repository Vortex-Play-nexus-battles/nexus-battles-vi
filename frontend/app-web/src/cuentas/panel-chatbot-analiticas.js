/**
 * Pestaña «Analíticas» del panel del asistente — HU-CHA-012 (RF-CHA-012).
 *
 * Muestra el tablero que calcula el servicio (`GET /chatbot/admin/analiticas`)
 * para un período, y lo descarga en CSV. **No recalcula nada:** las tasas,
 * los promedios y la tendencia vienen hechos del backend, que es donde están
 * los mensajes. Aquí solo se formatean.
 *
 * Una tasa `null` significa «sin datos» (p. ej. nadie calificó) y se muestra
 * como «—», nunca como 0 %.
 *
 * @module cuentas/panel-chatbot-analiticas
 */

import { campo } from '../comun/ui/campo.js';
import { conCarga } from '../comun/ui/boton.js';
import { h, vaciar } from '../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../comun/ui/estado-vista.js';
import { numero, porcentaje } from '../comun/ui/formato.js';
import { encabezadoDeSeccion } from '../comun/ui/pagina.js';
import { tarjetaDeCifra } from '../comun/ui/tarjeta.js';

const SVG = 'http://www.w3.org/2000/svg';

/** Alto útil de la gráfica y ancho de cada día, en unidades del `viewBox`. */
const ALTO = 120;
const PASO = 12;
const SEPARACION = 2;

/**
 * Texto de un error del panel, decidido por el estado (MAPEO-ERRORES §2).
 *
 * @param {{noDisponible?: boolean, sinPermiso?: boolean, estado?: number}|null} error
 * @returns {{titulo: string, detalle: string}}
 */
export function textoDeErrorDelPanel(error) {
  if (error?.noDisponible) {
    return {
      titulo: 'El servicio del asistente no responde',
      detalle: 'Vuelve a intentarlo en unos minutos.',
    };
  }
  if (error?.sinPermiso) {
    return {
      titulo: 'Tu sesión no tiene permiso para ver esto',
      detalle: 'Vuelve a iniciar sesión con una cuenta de administración.',
    };
  }
  if (error?.estado === 400) {
    return {
      titulo: 'El período no es válido',
      detalle: 'Revisa las fechas: «hasta» no puede ser anterior a «desde» y el máximo es un año.',
    };
  }
  return {
    titulo: 'No se pudieron cargar las analíticas',
    detalle: 'Revisa tu conexión e inténtalo de nuevo.',
  };
}

/**
 * Día (`AAAA-MM-DD`) de un instante, en la zona del tablero.
 *
 * @param {string} instante ISO-8601
 * @param {string} zona p. ej. `America/Bogota`
 * @param {number} [desplazamientoMs] `-1` para el `hasta` exclusivo
 * @returns {string}
 */
export function diaEnZona(instante, zona, desplazamientoMs = 0) {
  const momento = new Date(new Date(instante).getTime() + desplazamientoMs);
  return momento.toLocaleDateString('en-CA', { timeZone: zona });
}

/**
 * Tiempo de respuesta legible: milisegundos por debajo de 1 s.
 *
 * @param {number|null} ms
 * @returns {string}
 */
export function duracion(ms) {
  if (ms === null || ms === undefined) {
    return '—';
  }
  return ms < 1000 ? `${numero(ms)} ms` : `${numero(ms / 1000, 1)} s`;
}

/** @param {string} dia `AAAA-MM-DD` */
function diaCorto(dia) {
  return new Date(`${dia}T00:00:00Z`).toLocaleDateString('es-CO', {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  });
}

/**
 * Barras de preguntas por día. Una sola serie: el título de la sección la
 * nombra, así que no lleva leyenda. Cada barra dice su valor al pasar el
 * cursor (`<title>`), y la misma información va en una tabla debajo.
 *
 * @param {Array<{dia: string, preguntas: number, escalamientos: number}>} tendencia
 * @returns {SVGSVGElement}
 */
export function graficaDeTendencia(tendencia) {
  const maximo = Math.max(1, ...tendencia.map((punto) => punto.preguntas));
  const ancho = Math.max(PASO, tendencia.length * PASO);

  const svg = document.createElementNS(SVG, 'svg');
  svg.setAttribute('class', 'grafica-tendencia');
  svg.setAttribute('viewBox', `0 0 ${ancho} ${ALTO}`);
  svg.setAttribute('preserveAspectRatio', 'none');
  svg.setAttribute('role', 'img');
  svg.setAttribute(
    'aria-label',
    `Preguntas por día: ${tendencia.length} días, máximo ${numero(maximo)} en un día.`,
  );

  const base = document.createElementNS(SVG, 'line');
  base.setAttribute('class', 'grafica-tendencia__base');
  base.setAttribute('x1', '0');
  base.setAttribute('x2', String(ancho));
  base.setAttribute('y1', String(ALTO));
  base.setAttribute('y2', String(ALTO));
  svg.append(base);

  tendencia.forEach((punto, indice) => {
    const alto = (punto.preguntas / maximo) * (ALTO - 4);
    const barra = document.createElementNS(SVG, 'rect');
    barra.setAttribute('class', 'grafica-tendencia__barra');
    barra.setAttribute('x', String(indice * PASO + SEPARACION / 2));
    barra.setAttribute('y', String(ALTO - alto));
    barra.setAttribute('width', String(PASO - SEPARACION));
    barra.setAttribute('height', String(alto));
    barra.setAttribute('rx', '1.5');
    barra.dataset.dia = punto.dia;
    const titulo = document.createElementNS(SVG, 'title');
    titulo.textContent = `${diaCorto(punto.dia)}: ${numero(punto.preguntas)} preguntas, ${numero(
      punto.escalamientos,
    )} escaladas`;
    barra.append(titulo);
    svg.append(barra);
  });
  return svg;
}

function tablaDeTendencia(tendencia) {
  const filas = tendencia.map((punto) =>
    h('tr', {
      hijos: [
        h('td', { texto: diaCorto(punto.dia) }),
        h('td', { texto: numero(punto.conversaciones) }),
        h('td', { texto: numero(punto.preguntas) }),
        h('td', { texto: numero(punto.escalamientos) }),
      ],
    }),
  );
  return h('table', {
    clase: 'tabla-panel',
    hijos: [
      h('thead', {
        hijos: [
          h('tr', {
            hijos: ['Día', 'Conversaciones', 'Preguntas', 'Escaladas'].map((texto) =>
              h('th', { texto, atributos: { scope: 'col' } }),
            ),
          }),
        ],
      }),
      h('tbody', { hijos: filas }),
    ],
  });
}

function tablaDeTemas(temas) {
  if (temas.length === 0) {
    return estadoVacio({
      titulo: 'Ningún tema respondió preguntas en este período',
      detalle: 'Aparecen aquí cuando el asistente responde con la base de conocimiento.',
    });
  }
  return h('table', {
    clase: 'tabla-panel',
    datos: { zona: 'temas' },
    hijos: [
      h('thead', {
        hijos: [
          h('tr', {
            hijos: [
              h('th', { texto: 'Tema', atributos: { scope: 'col' } }),
              h('th', { texto: 'Respuestas', atributos: { scope: 'col' } }),
            ],
          }),
        ],
      }),
      h('tbody', {
        hijos: temas.map((tema) =>
          h('tr', {
            hijos: [h('td', { texto: tema.titulo }), h('td', { texto: numero(tema.respuestas) })],
          }),
        ),
      }),
    ],
  });
}

/**
 * Pinta el tablero ya recibido.
 *
 * @param {HTMLElement} zona
 * @param {object} analitica respuesta de `GET /chatbot/admin/analiticas`
 */
export function pintarAnaliticas(zona, analitica) {
  vaciar(zona);

  const cifras = h('div', {
    clase: 'panel-chatbot__cifras',
    hijos: [
      tarjetaDeCifra({ etiqueta: 'Conversaciones', valor: numero(analitica.conversaciones) }),
      tarjetaDeCifra({ etiqueta: 'Preguntas', valor: numero(analitica.preguntas) }),
      tarjetaDeCifra({
        etiqueta: 'Tasa de resolución',
        valor: porcentaje(analitica.tasaResolucion),
        detalle:
          analitica.tasaResolucion === null
            ? 'Sin respuestas medidas en el período'
            : `${numero(analitica.escalamientos)} escaladas a soporte humano`,
      }),
      tarjetaDeCifra({
        etiqueta: 'Satisfacción',
        valor: porcentaje(analitica.satisfaccion),
        detalle:
          analitica.calificaciones === 0
            ? 'Nadie calificó respuestas en el período'
            : `${numero(analitica.calificacionesUtiles)} de ${numero(
                analitica.calificaciones,
              )} calificaciones útiles`,
      }),
      tarjetaDeCifra({
        etiqueta: 'Tiempo de respuesta promedio',
        valor: duracion(analitica.tiempoRespuestaPromedioMs),
      }),
    ],
  });

  const tendencia = analitica.tendencia ?? [];
  const hayActividad = tendencia.some((punto) => punto.preguntas > 0);
  const seccionTendencia = h('section', {
    clase: 'pila',
    hijos: [
      encabezadoDeSeccion({
        titulo: 'Preguntas por día',
        detalle: `Días en hora de ${analitica.zonaHoraria}.`,
      }),
      hayActividad
        ? h('div', {
            clase: 'panel-chatbot__grafica',
            hijos: [
              graficaDeTendencia(tendencia),
              h('div', {
                clase: 'panel-chatbot__eje t-meta',
                hijos: [
                  h('span', { texto: diaCorto(tendencia[0].dia) }),
                  h('span', { texto: diaCorto(tendencia[tendencia.length - 1].dia) }),
                ],
              }),
            ],
          })
        : estadoVacio({ titulo: 'Sin preguntas en este período' }),
      hayActividad
        ? h('details', {
            hijos: [h('summary', { texto: 'Ver como tabla' }), tablaDeTendencia(tendencia)],
          })
        : null,
    ],
  });

  const seccionTemas = h('section', {
    clase: 'pila',
    hijos: [
      encabezadoDeSeccion({
        titulo: 'Temas más consultados',
        detalle: 'Los diez temas de la base de conocimiento que más respondieron.',
      }),
      tablaDeTemas(analitica.temasFrecuentes ?? []),
    ],
  });

  zona.append(cifras, seccionTendencia, seccionTemas);
}

/**
 * Monta la pestaña: el filtro de período, la descarga en CSV y el tablero.
 *
 * @param {HTMLElement} raiz
 * @param {{cliente: ReturnType<typeof import('./cliente-panel-chatbot.js').crearClientePanelChatbot>,
 *          descargarArchivo: (archivo: {nombre: string, contenido: Blob}) => void}} dependencias
 * @returns {{cargar: () => Promise<void>}}
 */
export function montarAnaliticas(raiz, { cliente, descargarArchivo }) {
  const desde = campo({ nombre: 'desde', etiqueta: 'Desde', tipo: 'date' });
  const hasta = campo({ nombre: 'hasta', etiqueta: 'Hasta', tipo: 'date' });
  const consultar = h('button', {
    clase: 'boton boton--primario',
    texto: 'Consultar',
    atributos: { type: 'submit' },
  });
  const exportar = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Descargar CSV',
    atributos: { type: 'button' },
    datos: { accion: 'exportar-analiticas' },
  });
  const formulario = h('form', {
    clase: 'panel-chatbot__filtros',
    atributos: { novalidate: true },
    hijos: [desde.elemento, hasta.elemento, consultar, exportar],
  });
  const aviso = h('p', { clase: 'panel-chatbot__nota t-meta', atributos: { role: 'status' } });
  const resultados = h('div', { clase: 'pila pila--amplia', datos: { zona: 'analiticas' } });
  raiz.append(formulario, aviso, resultados);

  function periodo() {
    return { desde: desde.control.value || null, hasta: hasta.control.value || null };
  }

  function periodoValido() {
    const { desde: inicio, hasta: fin } = periodo();
    const invalido = inicio && fin && fin < inicio;
    hasta.marcarError(invalido ? '«Hasta» no puede ser anterior a «Desde».' : null);
    return !invalido;
  }

  async function cargar() {
    if (!periodoValido()) {
      return;
    }
    aviso.textContent = '';
    pintarEstado(resultados, estadoDeCarga({ filas: 4, etiqueta: 'Cargando analíticas…' }));
    try {
      const analitica = await cliente.analiticas(periodo());
      // Sin fechas, el servicio usa los últimos 30 días: se muestran en el
      // filtro para que se sepa qué período se está viendo.
      if (!desde.control.value) {
        desde.control.value = diaEnZona(analitica.desde, analitica.zonaHoraria);
      }
      if (!hasta.control.value) {
        hasta.control.value = diaEnZona(analitica.hasta, analitica.zonaHoraria, -1);
      }
      pintarAnaliticas(resultados, analitica);
    } catch (error) {
      pintarEstado(
        resultados,
        estadoDeError({ ...textoDeErrorDelPanel(error), alReintentar: () => cargar() }),
      );
    }
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    cargar();
  });

  exportar.addEventListener('click', async () => {
    if (!periodoValido()) {
      return;
    }
    conCarga(exportar, true, 'Preparando…');
    aviso.textContent = '';
    try {
      descargarArchivo(await cliente.exportarAnaliticas(periodo()));
    } catch (error) {
      aviso.textContent = textoDeErrorDelPanel(error).titulo;
    } finally {
      conCarga(exportar, false);
    }
  });

  return { cargar };
}
