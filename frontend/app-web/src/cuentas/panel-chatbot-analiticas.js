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
 * Desde ms-chatbot.yaml 1.3.7 (7.4.7) muestra también las solicitudes de
 * soporte del período y las palabras más usadas en las preguntas. Si el
 * servicio todavía no las manda, esas secciones no se pintan.
 *
 * Desde 1.7.0 cada cifra dice lo que cuenta: las conversaciones son las
 * INICIADAS en el período; la tasa de resolución sale del resultado que
 * guardó cada respuesta (y se ve cómo terminaron todas); las veces que el
 * asistente ofreció soporte y las solicitudes que de verdad se abrieron son
 * dos cifras distintas; y se suman las preguntas más frecuentes y la
 * tendencia de cada palabra.
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
import { CATEGORIAS } from '../comun/ui/soporte-chatbot.js';
import { tarjetaDeCifra } from '../comun/ui/tarjeta.js';
import { textoDeErrorDelPanel } from './panel-chatbot-errores.js';

// Las otras pestañas lo importaban de aquí; ahora vive en su propio módulo.
export { textoDeErrorDelPanel };

const SVG = 'http://www.w3.org/2000/svg';

/** Alto útil de la gráfica y ancho de cada día, en unidades del `viewBox`. */
const ALTO = 120;
const PASO = 12;
const SEPARACION = 2;

/** 1.7.0: cómo terminó una respuesta del asistente (`ResultadoDeRespuesta`). */
export const RESULTADOS = Object.freeze({
  TEMA: { texto: 'Respondió con un tema de la base', resuelve: true },
  CONSULTA_EN_VIVO: { texto: 'Consultó datos de la cuenta', resuelve: true },
  NAVEGACION: { texto: 'Llevó a la sección pedida', resuelve: true },
  NO_DISPONIBLE: { texto: 'El servicio consultado no respondió', resuelve: false },
  ESCALADO: { texto: 'No entendió y ofreció soporte', resuelve: false },
  NO_SIRVIO: { texto: 'No le sirvió y ofreció soporte', resuelve: false },
  PIDE_REFORMULAR: { texto: 'Pidió la pregunta completa', resuelve: false },
});

/** 1.7.0: la tendencia de una palabra contra el período anterior. */
export const TENDENCIAS = Object.freeze({
  SUBE: 'Sube',
  BAJA: 'Baja',
  IGUAL: 'Igual',
  NUEVA: 'Nueva',
});

/**
 * Texto de un error de esta pestaña: un 400 es el período; lo demás, lo
 * común del panel con lo que falló aquí.
 *
 * @param {object|null} error
 * @param {string} [queFallo]
 * @returns {{titulo: string, detalle: string}}
 */
export function textoDeErrorDeAnaliticas(error, queFallo = 'No se pudieron cargar las analíticas') {
  if (error?.estado === 400) {
    return {
      titulo: 'El período no es válido',
      detalle: 'Revisa las fechas: «hasta» no puede ser anterior a «desde» y el máximo es un año.',
    };
  }
  return textoDeErrorDelPanel(error, queFallo);
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

/**
 * Horas de atención legibles: minutos por debajo de una hora.
 *
 * @param {number|null} horas
 * @returns {string}
 */
export function horasDeAtencion(horas) {
  if (horas === null || horas === undefined) {
    return '—';
  }
  return horas < 1 ? `${numero(horas * 60)} min` : `${numero(horas, 1)} h`;
}

/**
 * Nombre de una categoría como lo lee el jugador al abrir una solicitud.
 *
 * @param {string} categoria valor del contrato, p. ej. `SOPORTE_TECNICO`
 * @returns {string}
 */
export function nombreDeCategoria(categoria) {
  return CATEGORIAS.find((opcion) => opcion.valor === categoria)?.texto ?? categoria;
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
    titulo.textContent = `${diaCorto(punto.dia)}: ${numero(punto.preguntas)} preguntas; ofreció soporte ${numero(
      punto.escalamientos,
    )} veces`;
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
            hijos: ['Día', 'Conversaciones iniciadas', 'Preguntas', 'Ofreció soporte'].map(
              (texto) => h('th', { texto, atributos: { scope: 'col' } }),
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

function tabla(zona, columnas, filas) {
  return h('table', {
    clase: 'tabla-panel',
    datos: { zona },
    hijos: [
      h('thead', {
        hijos: [
          h('tr', {
            hijos: columnas.map((texto) => h('th', { texto, atributos: { scope: 'col' } })),
          }),
        ],
      }),
      h('tbody', {
        hijos: filas.map((celdas) => h('tr', { hijos: celdas.map((texto) => h('td', { texto })) })),
      }),
    ],
  });
}

/**
 * 1.3.7: las solicitudes de soporte abiertas en el período.
 *
 * @param {{total: number, abiertos: number, enProceso: number, resueltos: number,
 *          cerrados: number, horasPromedioDeAtencion: number|null,
 *          porCategoria: Array<{categoria: string, tickets: number}>}} tickets
 * @returns {HTMLElement}
 */
function seccionDeSoporte(tickets) {
  const porCategoria = tickets.porCategoria ?? [];
  return h('section', {
    clase: 'pila',
    datos: { zona: 'soporte' },
    hijos: [
      encabezadoDeSeccion({
        titulo: 'Solicitudes de soporte',
        detalle: 'Las que abrieron los jugadores en este período.',
      }),
      h('div', {
        clase: 'panel-chatbot__cifras',
        hijos: [
          tarjetaDeCifra({ etiqueta: 'Abiertas en el período', valor: numero(tickets.total) }),
          tarjetaDeCifra({
            etiqueta: 'Por atender',
            valor: numero(tickets.abiertos + tickets.enProceso),
            detalle: `${numero(tickets.abiertos)} sin atender, ${numero(tickets.enProceso)} en revisión`,
          }),
          tarjetaDeCifra({
            etiqueta: 'Atendidas',
            valor: numero(tickets.resueltos + tickets.cerrados),
            detalle: `${numero(tickets.resueltos)} respondidas, ${numero(tickets.cerrados)} cerradas`,
          }),
          tarjetaDeCifra({
            etiqueta: 'Tiempo promedio de atención',
            valor: horasDeAtencion(tickets.horasPromedioDeAtencion),
            detalle:
              tickets.horasPromedioDeAtencion === null
                ? 'Todavía no hay solicitudes atendidas'
                : 'Desde que se abre hasta su última actualización',
          }),
        ],
      }),
      porCategoria.length === 0
        ? estadoVacio({ titulo: 'Nadie pidió soporte en este período' })
        : tabla(
            'soporte-por-categoria',
            ['Tema', 'Solicitudes'],
            porCategoria.map((fila) => [nombreDeCategoria(fila.categoria), numero(fila.tickets)]),
          ),
    ],
  });
}

/**
 * 1.3.7: las palabras que más usan los jugadores en sus preguntas. 1.7.0: con
 * su tendencia contra el período anterior de los mismos días, si el servicio
 * la manda.
 *
 * @param {Array<{palabra: string, preguntas: number, conversaciones: number,
 *          preguntasPeriodoAnterior?: number, tendencia?: string}>} palabras
 * @returns {HTMLElement}
 */
function seccionDePalabras(palabras) {
  const conTendencia = palabras.some((fila) => fila.tendencia);
  return h('section', {
    clase: 'pila',
    datos: { zona: 'palabras' },
    hijos: [
      encabezadoDeSeccion({
        titulo: 'Palabras más usadas',
        detalle: conTendencia
          ? 'En las preguntas del período, sin tildes ni mayúsculas, comparadas con los mismos días justo antes. Solo aparecen las que usaron al menos dos conversaciones.'
          : 'En las preguntas del período, sin tildes ni mayúsculas. Solo aparecen las que usaron al menos dos conversaciones.',
      }),
      palabras.length === 0
        ? estadoVacio({
            titulo: 'Todavía no hay palabras que se repitan',
            detalle: 'Aparecen cuando varias conversaciones preguntan por lo mismo.',
          })
        : tabla(
            'palabras-clave',
            conTendencia
              ? ['Palabra', 'Preguntas', 'Conversaciones', 'Período anterior', 'Tendencia']
              : ['Palabra', 'Preguntas', 'Conversaciones'],
            palabras.map((fila) =>
              conTendencia
                ? [
                    fila.palabra,
                    numero(fila.preguntas),
                    numero(fila.conversaciones),
                    numero(fila.preguntasPeriodoAnterior ?? 0),
                    TENDENCIAS[fila.tendencia] ?? '—',
                  ]
                : [fila.palabra, numero(fila.preguntas), numero(fila.conversaciones)],
            ),
          ),
    ],
  });
}

/**
 * 1.7.0 (7.4.7 «preguntas más frecuentes»): lo que preguntan los jugadores,
 * como lo lee el asistente. Solo las que hicieron al menos tres
 * conversaciones distintas, para no mostrar lo que escribió una persona.
 *
 * @param {Array<{texto: string, preguntas: number, conversaciones: number}>} preguntas
 * @returns {HTMLElement}
 */
function seccionDePreguntasFrecuentes(preguntas) {
  return h('section', {
    clase: 'pila',
    datos: { zona: 'preguntas-frecuentes' },
    hijos: [
      encabezadoDeSeccion({
        titulo: 'Preguntas más frecuentes',
        detalle:
          'Las que más se repitieron, sin tildes, mayúsculas ni signos, como las lee el asistente. Solo aparecen las que hicieron al menos tres conversaciones distintas.',
      }),
      preguntas.length === 0
        ? estadoVacio({
            titulo: 'Ninguna pregunta se repitió lo suficiente',
            detalle: 'Aparecen cuando al menos tres conversaciones preguntan lo mismo.',
          })
        : tabla(
            'tabla-preguntas-frecuentes',
            ['Pregunta', 'Veces', 'Conversaciones'],
            preguntas.map((fila) => [
              fila.texto,
              numero(fila.preguntas),
              numero(fila.conversaciones),
            ]),
          ),
    ],
  });
}

/**
 * 1.7.0: cómo terminaron las respuestas con resultado guardado, y cuáles
 * cuentan como resueltas en la tasa.
 *
 * @param {Array<{resultado: string, respuestas: number}>} resultados
 * @returns {HTMLElement}
 */
function seccionDeResultados(resultados) {
  const total = resultados.reduce((suma, fila) => suma + fila.respuestas, 0);
  return h('section', {
    clase: 'pila',
    datos: { zona: 'resultados' },
    hijos: [
      encabezadoDeSeccion({
        titulo: 'Cómo terminaron las respuestas',
        detalle:
          'Solo las que guardaron su resultado. Cuentan como resueltas las que respondió un tema, las consultas que trajeron datos y la navegación a una sección.',
      }),
      total === 0
        ? estadoVacio({ titulo: 'Ninguna respuesta del período guardó su resultado' })
        : tabla(
            'tabla-resultados',
            ['Resultado', 'Respuestas', 'Cuenta como'],
            resultados.map((fila) => {
              const conocido = RESULTADOS[fila.resultado];
              return [
                conocido?.texto ?? fila.resultado,
                numero(fila.respuestas),
                conocido?.resuelve ? 'Resuelta' : 'No resuelta',
              ];
            }),
          ),
    ],
  });
}

/**
 * Detalle de la tasa de resolución: de cuántas respuestas sale (1.7.0), o lo
 * que había antes si el servicio todavía no lo manda.
 *
 * @param {object} analitica
 * @returns {string}
 */
function detalleDeResolucion(analitica) {
  if (analitica.tasaResolucion === null || analitica.tasaResolucion === undefined) {
    return 'Ninguna respuesta del período guardó su resultado';
  }
  if (analitica.respuestasClasificadas === undefined) {
    return `De ${numero(analitica.respuestasMedidas)} respuestas medidas`;
  }
  return `${numero(analitica.respuestasResueltas)} de ${numero(
    analitica.respuestasClasificadas,
  )} resueltas sin una persona del equipo`;
}

/**
 * Pinta el tablero ya recibido.
 *
 * @param {HTMLElement} zona
 * @param {object} analitica respuesta de `GET /chatbot/admin/analiticas`
 */
export function pintarAnaliticas(zona, analitica) {
  vaciar(zona);

  // 1.7.0: lo que hizo el asistente (ofrecer soporte) y lo que hicieron los
  // jugadores (abrir una solicitud) son dos cifras, una al lado de la otra.
  const cifras = h('div', {
    clase: 'panel-chatbot__cifras',
    hijos: [
      tarjetaDeCifra({
        etiqueta: 'Conversaciones iniciadas',
        valor: numero(analitica.conversaciones),
        detalle:
          analitica.conversacionesConActividad === undefined
            ? null
            : `${numero(analitica.conversacionesConActividad)} con preguntas en el período`,
      }),
      tarjetaDeCifra({ etiqueta: 'Preguntas', valor: numero(analitica.preguntas) }),
      tarjetaDeCifra({
        etiqueta: 'Tasa de resolución',
        valor: porcentaje(analitica.tasaResolucion),
        detalle: detalleDeResolucion(analitica),
      }),
      tarjetaDeCifra({
        etiqueta: 'Ofreció soporte',
        valor: numero(analitica.escalamientos),
        detalle: 'Veces que el asistente no resolvió y ofreció hablar con una persona',
      }),
      analitica.tickets
        ? tarjetaDeCifra({
            etiqueta: 'Solicitudes de soporte',
            valor: numero(analitica.tickets.total),
            detalle: 'Las que los jugadores abrieron de verdad',
          })
        : null,
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
        detalle: 'Lo que tarda el servidor en preparar la respuesta',
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

  zona.append(cifras, seccionTendencia);
  if (Array.isArray(analitica.resultados)) {
    zona.append(seccionDeResultados(analitica.resultados));
  }
  zona.append(seccionTemas);
  if (Array.isArray(analitica.preguntasFrecuentes)) {
    zona.append(seccionDePreguntasFrecuentes(analitica.preguntasFrecuentes));
  }
  if (analitica.tickets) {
    zona.append(seccionDeSoporte(analitica.tickets));
  }
  if (Array.isArray(analitica.palabrasClave)) {
    zona.append(seccionDePalabras(analitica.palabrasClave));
  }
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
        estadoDeError({ ...textoDeErrorDeAnaliticas(error), alReintentar: () => cargar() }),
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
      const { titulo, detalle } = textoDeErrorDeAnaliticas(error, 'No se pudo descargar el CSV');
      aviso.textContent = `${titulo}. ${detalle}`;
    } finally {
      conCarga(exportar, false);
    }
  });

  return { cargar };
}
