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
 * @module cuentas/panel-chatbot-analiticas
 */

import { campo } from '../comun/ui/campo.js';
import { conCarga } from '../comun/ui/boton.js';
import { h, vaciar } from '../comun/ui/dom.js';
import { vigilarDesborde } from '../comun/ui/admin/tabla-de-datos.js';
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

/** Zonas horarias que se dicen por su país y no por su identificador. */
const ZONAS_LEGIBLES = Object.freeze({ 'America/Bogota': 'Colombia' });

/**
 * La zona horaria del tablero como la lee una persona: «Colombia» y no
 * «America/Bogota»; una que no está en la lista, por su ciudad.
 *
 * @param {string|null|undefined} zona
 * @returns {string|null}
 */
export function zonaLegible(zona) {
  if (!zona) {
    return null;
  }
  return ZONAS_LEGIBLES[zona] ?? String(zona).split('/').pop().replace(/_/g, ' ');
}

/**
 * La serie en una línea: el total del período y el día con más preguntas.
 *
 * @param {Array<{dia: string, preguntas: number}>} tendencia
 * @returns {string}
 */
export function resumenDeTendencia(tendencia) {
  const total = tendencia.reduce((suma, punto) => suma + (punto.preguntas ?? 0), 0);
  const pico = tendencia.reduce(
    (mayor, punto) => ((punto.preguntas ?? 0) > (mayor.preguntas ?? 0) ? punto : mayor),
    tendencia[0],
  );
  const dias = tendencia.length;
  return `${numero(total)} ${total === 1 ? 'pregunta' : 'preguntas'} en ${numero(dias)} ${
    dias === 1 ? 'día' : 'días'
  } · el día con más: ${diaCorto(pico.dia)}, con ${numero(pico.preguntas)}`;
}

/** Los días que se rotulan bajo la gráfica: el primero, el del medio y el último. */
function diasDelEje(tendencia) {
  const indices = [0, Math.floor((tendencia.length - 1) / 2), tendencia.length - 1];
  return [...new Set(indices)].map((indice) => tendencia[indice].dia);
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
  return tabla(
    'temas',
    ['Tema', 'Respuestas'],
    temas.map((tema) => [tema.titulo, numero(tema.respuestas)]),
  );
}

/*
 * Las tablas del tablero (punto 25 del informe de revisión): la tabla de la
 * consola (`.tabla-admin__marco` + `.tabla-admin__tabla`) con lo que la hace
 * legible — las cifras a la derecha con números tabulares, una barra que dice
 * cuánto pesa cada fila frente a la mayor y, en el teléfono, cada fila como
 * ficha con la etiqueta de cada dato. Las celdas siguen siendo `<td>` con el
 * mismo texto: quien las lea (pruebas, lector de pantalla) lee lo mismo.
 */

/** Una celda que es una cifra: «96», «1.200», «93 %», «840 ms», «6,4 h» o «—». */
const CIFRA = /^(?:—|-?\d[\d.,]*(?:\s?(?:%|ms|s|min|h))?)$/;

/** El nombre de cada tabla para el lector de pantalla (su `<caption>`). */
const NOMBRE_DE_TABLA = Object.freeze({
  temas: 'Temas más consultados',
  'soporte-por-categoria': 'Solicitudes de soporte por tema',
  'palabras-clave': 'Palabras más usadas',
});

let tablasMontadas = 0;

/** El valor de una cifra ya formateada en es-CO («1.200» → 1200, «6,4 h» → 6,4). */
function valorDeCifra(texto) {
  const valor = Number.parseFloat(
    String(texto ?? '')
      .replace(/\./g, '')
      .replace(',', '.'),
  );
  return Number.isFinite(valor) ? valor : 0;
}

/** La barra de una fila: su proporción frente a la mayor de la columna. */
function barraDeProporcion(proporcion) {
  const relleno = h('span', { clase: 'tabla-panel__relleno' });
  relleno.style.setProperty('--proporcion', String(Math.round(proporcion * 1000) / 1000));
  return h('span', {
    clase: 'tabla-panel__barra',
    atributos: { 'aria-hidden': 'true' },
    hijos: [relleno],
  });
}

function tabla(zona, columnas, filas) {
  tablasMontadas += 1;
  const id = `tabla-analiticas-${tablasMontadas}`;
  // Una columna es de cifras si todas sus celdas lo son.
  const deCifras = columnas.map(
    (_, i) =>
      filas.length > 0 && filas.every((celdas) => CIFRA.test(String(celdas[i] ?? '').trim())),
  );
  // La primera columna de cifras (nunca la que nombra la fila) lleva la barra.
  const medida = deCifras.findIndex((esCifra, i) => esCifra && i > 0);
  const valores = filas.map((celdas) => (medida > 0 ? valorDeCifra(celdas[medida]) : 0));
  const tope = Math.max(0, ...valores);

  const tablaDelPanel = h('table', {
    clase: 'tabla-panel tabla-admin__tabla',
    datos: { zona },
    atributos: { role: 'table' },
    hijos: [
      h('caption', {
        clase: 'solo-lectores',
        texto: NOMBRE_DE_TABLA[zona] ?? columnas.join(' · '),
        atributos: { id: `${id}-nombre` },
      }),
      h('thead', {
        atributos: { role: 'rowgroup' },
        hijos: [
          h('tr', {
            atributos: { role: 'row' },
            hijos: columnas.map((texto, i) =>
              h('th', {
                texto,
                atributos: { scope: 'col', role: 'columnheader' },
                datos: { tipo: deCifras[i] ? 'numero' : 'texto' },
              }),
            ),
          }),
        ],
      }),
      h('tbody', {
        atributos: { role: 'rowgroup' },
        hijos: filas.map((celdas, fila) =>
          h('tr', {
            atributos: { role: 'row' },
            hijos: celdas.map((texto, i) => {
              const celda = h('td', {
                texto,
                clase: i === 0 ? 'tabla-admin__principal' : null,
                atributos: { role: 'cell' },
                datos: {
                  tipo: deCifras[i] ? 'numero' : 'texto',
                  ...(i === 0 ? {} : { etiqueta: columnas[i] }),
                },
              });
              if (i === medida && tope > 0) {
                celda.dataset.medida = '';
                celda.prepend(barraDeProporcion(valores[fila] / tope));
              }
              return celda;
            }),
          }),
        ),
      }),
    ],
  });

  const marco = h('div', {
    clase: 'tabla-admin__marco',
    // Si no cabe, se desplaza aquí dentro (y con el teclado), no la página.
    atributos: { tabindex: '0', role: 'region', 'aria-labelledby': `${id}-nombre` },
    hijos: [tablaDelPanel],
  });
  vigilarDesborde(marco);
  return marco;
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
 * 1.3.7: las palabras que más usan los jugadores en sus preguntas.
 *
 * @param {Array<{palabra: string, preguntas: number, conversaciones: number}>} palabras
 * @returns {HTMLElement}
 */
function seccionDePalabras(palabras) {
  return h('section', {
    clase: 'pila',
    datos: { zona: 'palabras' },
    hijos: [
      encabezadoDeSeccion({
        titulo: 'Palabras más usadas',
        detalle:
          'En las preguntas del período, sin tildes ni mayúsculas. Solo aparecen las que usaron al menos dos conversaciones.',
      }),
      palabras.length === 0
        ? estadoVacio({
            titulo: 'Todavía no hay palabras que se repitan',
            detalle: 'Aparecen cuando varias conversaciones preguntan por lo mismo.',
          })
        : tabla(
            'palabras-clave',
            ['Palabra', 'Preguntas', 'Conversaciones'],
            palabras.map((fila) => [
              fila.palabra,
              numero(fila.preguntas),
              numero(fila.conversaciones),
            ]),
          ),
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
        detalle: zonaLegible(analitica.zonaHoraria)
          ? `Cuántas preguntas recibió el asistente cada día, en hora de ${zonaLegible(
              analitica.zonaHoraria,
            )}.`
          : 'Cuántas preguntas recibió el asistente cada día.',
      }),
      hayActividad
        ? h('div', {
            clase: 'panel-chatbot__grafica',
            hijos: [
              graficaDeTendencia(tendencia),
              h('div', {
                clase: 'panel-chatbot__eje t-meta',
                hijos: diasDelEje(tendencia).map((dia) => h('span', { texto: diaCorto(dia) })),
              }),
              h('p', {
                clase: 'panel-chatbot__resumen t-meta',
                datos: { zona: 'resumen-tendencia' },
                texto: resumenDeTendencia(tendencia),
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
