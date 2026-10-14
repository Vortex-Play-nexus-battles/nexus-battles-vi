/**
 * Métricas de plataforma — HU-DIS-001, HU-REN-001/002/003, HU-DIS-003 sobre
 * metricas-plataforma.yaml, con el kit de la consola (SA-8, puntos 23 y 24
 * del informe de revisión).
 *
 * Lo que pidió el informe y cómo queda:
 *   - sin la explicación de trabajo («Latencia extremo a extremo medida por
 *     shared/libs/…», «esta pantalla no recalcula nada»): la página dice qué
 *     mide en palabras de producto;
 *   - más dinámica: cifras arriba (disponibilidad del mes, servicios dentro
 *     del objetivo, interrupciones y funciones limitadas), y se actualiza sola
 *     diciendo de cuándo es lo que se ve (`actualizacionPeriodica`);
 *   - tablas de la consola (`tablaDeDatos`) con los servicios por su nombre:
 *     disponibilidad por servicio, tiempos de respuesta por servicio, el
 *     detalle del percentil y las consultas a la base de datos. Cada sección
 *     pinta su propio resultado: si una fuente no responde, lo dice esa
 *     sección y el resto sigue.
 *
 * **No calcula métricas.** Porcentajes, percentiles, veredictos y alertas los
 * publica el servicio; aquí solo se cuentan filas y se ordenan.
 *
 * @module plataforma/metricas-plataforma/panel-metricas
 */

import { ErrorDeMetricas, pedirMetricas } from './cliente-metricas.js';
import { actualizacionPeriodica } from '../../comun/ui/admin/actualizacion.js';
import { distintivoDeEstado } from '../../comun/ui/admin/estado.js';
import { rejillaDeIndicadores, tarjetaDeIndicador } from '../../comun/ui/admin/indicador.js';
import { nombreDelServicio } from '../../comun/ui/admin/servicios.js';
import { tablaDeDatos } from '../../comun/ui/admin/tabla-de-datos.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { fechaHora, numero } from '../../comun/ui/formato.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { panel } from '../consola/panel.js';

/** Cada cuánto se actualiza sola la página. */
export const PERIODO_DE_METRICAS_MS = 60_000;

/** Las fuentes de la página, todas de metricas-plataforma. */
export const FUENTES = Object.freeze({
  estado: '/disponibilidad',
  disponibilidad: '/disponibilidad/informe',
  tecnicas: '/tecnicas',
  latencia: '/latencia/informe',
  consultas: '/consultas/informe',
  degradacion: '/degradacion',
});

/** El tipo de operación, en palabras (HU-REN-002). */
const NOMBRE_DE_TIPO = Object.freeze({
  lectura: 'Lecturas',
  escritura: 'Escrituras',
  otra: 'Otras',
});

/** El método HTTP de una operación, en palabras. */
const VERBO = Object.freeze({
  GET: 'Consulta',
  HEAD: 'Consulta',
  POST: 'Alta',
  PUT: 'Cambio',
  PATCH: 'Cambio',
  DELETE: 'Baja',
});

/* =========================================================================
   Presentación (pura, probada)
   ========================================================================= */

/** «99,92 %». */
export function porciento(valor, decimales = 2) {
  return typeof valor === 'number' && Number.isFinite(valor)
    ? `${numero(valor, decimales)} %`
    : null;
}

/** «320 ms», o `null` si no hay dato. */
export function milisegundos(valor) {
  return typeof valor === 'number' && Number.isFinite(valor)
    ? `${numero(Math.round(valor))} ms`
    : null;
}

/** Minutos sin servicio en palabras: «8 min», «2 h 5 min», «1 d 3 h». */
export function duracion(minutos) {
  const total = Math.max(0, Math.round(Number(minutos) || 0));
  if (total < 60) {
    return `${total} min`;
  }
  if (total < 1440) {
    const horas = Math.floor(total / 60);
    const resto = total % 60;
    return resto ? `${horas} h ${resto} min` : `${horas} h`;
  }
  const dias = Math.floor(total / 1440);
  const horas = Math.floor((total % 1440) / 60);
  return horas ? `${dias} d ${horas} h` : `${dias} d`;
}

/**
 * Las operaciones del propio servicio de métricas (las que mide su informe de
 * tiempos), dichas como las conoce la consola y no como su ruta.
 */
const OPERACIONES_CONOCIDAS = Object.freeze({
  '/disponibilidad': 'Disponibilidad actual',
  '/disponibilidad/informe': 'Informe de disponibilidad',
  '/latencia/informe': 'Informe de tiempos de respuesta',
  '/latencia/informe/texto': 'Descarga del informe de tiempos',
  '/consultas/informe': 'Informe de consultas',
  '/consultas/lentas': 'Consultas lentas',
  '/degradacion': 'Funciones limitadas',
  '/tecnicas': 'Tablero técnico',
  '/tecnicas/informe/texto': 'Descarga del informe técnico',
  '/moderacion': 'Actividad de moderación',
  '/admin/sistema/servicios': 'Estado de los servicios',
});

/** Una ruta que la consola no conoce, en palabras: «Subastas › pujas». */
function rutaEnPalabras(ruta) {
  const partes = String(ruta ?? '')
    .replace(/^\/api\/v\d+/, '')
    .split('/')
    .filter((parte) => parte && !/^\{.*\}$/.test(parte) && parte !== 'admin')
    .map((parte) => parte.replace(/-/g, ' '));
  if (!partes.length) {
    return '';
  }
  const texto = partes.join(' › ');
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/** La operación más lenta en palabras: «Consulta · Tablero técnico». */
export function operacionLegible(operacion) {
  const verbo = VERBO[String(operacion?.metodo ?? '').toUpperCase()] ?? '';
  const plantilla = String(operacion?.ruta ?? '').replace(/^\/api\/v\d+/, '');
  const nombre = OPERACIONES_CONOCIDAS[plantilla] ?? rutaEnPalabras(operacion?.ruta);
  return [verbo, nombre].filter(Boolean).join(' · ');
}

/** La interrupción más reciente de una línea del informe, o `null`. */
export function ultimaInterrupcion(linea) {
  const interrupciones = Array.isArray(linea?.interrupciones) ? linea.interrupciones : [];
  return (
    [...interrupciones]
      .filter((i) => Number.isFinite(Date.parse(i?.inicio)))
      .sort((a, b) => Date.parse(b.inicio) - Date.parse(a.inicio))[0] ?? null
  );
}

/** El tono de una cifra: ninguno sin dato; éxito si va bien; el de alarma si no. */
function tonoSegun(fuente, bien, alarma) {
  if (!fuente) {
    return null;
  }
  return bien ? 'exito' : alarma;
}

/** Qué funciones están limitadas, o que todo funciona. */
function notaDeLimitadas(limitadas) {
  if (!limitadas) {
    return null;
  }
  return limitadas.length
    ? limitadas.map((s) => s.seccion).join(', ')
    : 'Todo funciona con normalidad';
}

/** Las cuatro cifras de arriba, de lo que haya respondido. */
export function cifrasDeMetricas({ disponibilidad, degradacion }) {
  const servicios = Array.isArray(disponibilidad?.servicios) ? disponibilidad.servicios : null;
  const fuera = servicios ? servicios.filter((s) => s?.cumple === false) : [];
  const interrupciones = servicios
    ? servicios.flatMap((s) =>
        (Array.isArray(s?.interrupciones) ? s.interrupciones : []).map((i) => ({
          ...i,
          servicio: s.servicio,
        })),
      )
    : null;
  const ultima = interrupciones
    ? [...interrupciones]
        .filter((i) => Number.isFinite(Date.parse(i.inicio)))
        .sort((a, b) => Date.parse(b.inicio) - Date.parse(a.inicio))[0]
    : null;
  const limitadas = Array.isArray(degradacion?.seccionesLimitadas)
    ? degradacion.seccionesLimitadas
    : null;

  return [
    {
      etiqueta: 'Disponibilidad del mes',
      valor: porciento(disponibilidad?.porcentajeDelBloque),
      tono: tonoSegun(disponibilidad, disponibilidad?.cumpleElUmbral, 'error'),
      detalle:
        typeof disponibilidad?.umbral === 'number'
          ? `Objetivo: ${porciento(disponibilidad.umbral)}`
          : null,
    },
    {
      etiqueta: 'Servicios en el objetivo',
      valor: servicios
        ? `${numero(servicios.length - fuera.length)} de ${numero(servicios.length)}`
        : null,
      tono: tonoSegun(servicios, fuera.length === 0, 'advertencia'),
      detalle: fuera.length
        ? `Por debajo: ${fuera
            .slice(0, 2)
            .map((s) => nombreDelServicio(s.servicio))
            .join(', ')}${fuera.length > 2 ? '…' : ''}`
        : null,
    },
    {
      etiqueta: 'Interrupciones del mes',
      valor: interrupciones ? interrupciones.length : null,
      detalle: ultima
        ? `La última: ${nombreDelServicio(ultima.servicio)}, ${fechaHora(ultima.inicio)}`
        : null,
    },
    {
      etiqueta: 'Funciones limitadas ahora',
      valor: limitadas ? limitadas.length : null,
      tono: tonoSegun(limitadas, limitadas?.length === 0, 'error'),
      detalle: notaDeLimitadas(limitadas),
    },
  ];
}

/* =========================================================================
   Tablas
   ========================================================================= */

function celdaDoble(principal, secundaria) {
  return h('span', {
    clase: 'celda-doble',
    hijos: [
      typeof principal === 'string' ? h('span', { texto: principal }) : principal,
      secundaria ? h('span', { clase: 't-meta', texto: secundaria }) : null,
    ],
  });
}

/** Disponibilidad por servicio del periodo, con su estado de ahora. */
export function tablaDeDisponibilidad(informe, estado) {
  const ahora = new Map(
    (Array.isArray(estado?.servicios) ? estado.servicios : []).map((s) => [s.servicio, s]),
  );
  const filas = [...(informe?.servicios ?? [])].sort(
    (a, b) => (Number(a.porcentaje) || 0) - (Number(b.porcentaje) || 0),
  );
  return tablaDeDatos({
    titulo: 'Disponibilidad por servicio',
    resumen: `${numero(filas.length)} ${filas.length === 1 ? 'servicio' : 'servicios'}, el más bajo primero`,
    claveDeFila: (linea) => linea.servicio,
    columnas: [
      {
        clave: 'servicio',
        titulo: 'Servicio',
        principal: true,
        valor: (linea) => nombreDelServicio(linea.servicio),
      },
      {
        clave: 'ahora',
        titulo: 'Ahora',
        tipo: 'estado',
        valor: (linea) => {
          const vivo = ahora.get(linea.servicio);
          if (!vivo) {
            return null;
          }
          return vivo.estado === 'DISPONIBLE'
            ? distintivoDeEstado({ texto: 'En línea', tono: 'exito' })
            : distintivoDeEstado({ texto: 'Sin respuesta', tono: 'error' });
        },
      },
      {
        clave: 'porcentaje',
        titulo: 'Disponibilidad',
        tipo: 'numero',
        ordenable: true,
        valor: (linea) =>
          distintivoDeEstado({
            texto: porciento(linea.porcentaje) ?? 'Sin dato',
            tono: linea.cumple ? 'exito' : 'error',
          }),
        ordenarPor: (linea) => Number(linea.porcentaje) || 0,
      },
      {
        clave: 'indisponibleMinutos',
        titulo: 'Sin servicio',
        tipo: 'numero',
        ordenable: true,
        valor: (linea) => duracion(linea.indisponibleMinutos),
      },
      {
        clave: 'interrupciones',
        titulo: 'Interrupciones',
        tipo: 'numero',
        ordenable: true,
        valor: (linea) => {
          const ultima = ultimaInterrupcion(linea);
          const cuantas = Array.isArray(linea.interrupciones) ? linea.interrupciones.length : 0;
          if (!ultima) {
            return String(cuantas);
          }
          return celdaDoble(
            String(cuantas),
            ultima.fin
              ? `La última, ${fechaHora(ultima.inicio)}`
              : `En curso desde ${fechaHora(ultima.inicio)}`,
          );
        },
        ordenarPor: (linea) =>
          Array.isArray(linea.interrupciones) ? linea.interrupciones.length : 0,
      },
    ],
    filas,
    vacio: { titulo: 'Sin servicios medidos', detalle: 'Todavía no hay mediciones del periodo.' },
  });
}

/** Tiempos de respuesta, tráfico y errores de cada servicio (`/tecnicas`). */
export function tablaDeTiempos(tablero) {
  const objetivo = tablero?.umbrales?.latenciaMs;
  const filas = Array.isArray(tablero?.servicios) ? tablero.servicios : [];
  const conDato = (servicio, valor) => (servicio.brecha ? null : valor);
  return tablaDeDatos({
    titulo: 'Tiempos de respuesta por servicio',
    resumen: `${numero(filas.length)} ${filas.length === 1 ? 'servicio' : 'servicios'}`,
    claveDeFila: (servicio) => servicio.servicio,
    columnas: [
      {
        clave: 'servicio',
        titulo: 'Servicio',
        principal: true,
        valor: (servicio) =>
          servicio.brecha
            ? celdaDoble(nombreDelServicio(servicio.servicio), 'No se pudo medir en esta ronda')
            : nombreDelServicio(servicio.servicio),
      },
      {
        clave: 'promedioMs',
        titulo: 'Tiempo medio',
        tipo: 'numero',
        ordenable: true,
        valor: (servicio) => conDato(servicio, milisegundos(servicio.promedioMs)),
      },
      {
        clave: 'maximoMs',
        titulo: 'Tiempo máximo',
        tipo: 'numero',
        ordenable: true,
        valor: (servicio) => {
          const texto = conDato(servicio, milisegundos(servicio.maximoMs));
          if (!texto) {
            return null;
          }
          return typeof objetivo === 'number' && servicio.maximoMs > objetivo
            ? distintivoDeEstado({ texto, tono: 'advertencia' })
            : texto;
        },
      },
      {
        clave: 'peticiones',
        titulo: 'Peticiones',
        tipo: 'numero',
        ordenable: true,
        valor: (servicio) => conDato(servicio, numero(Number(servicio.peticiones) || 0)),
      },
      {
        clave: 'errores5xx',
        titulo: 'Errores del servidor',
        tipo: 'numero',
        ordenable: true,
        valor: (servicio) => {
          if (servicio.brecha) {
            return null;
          }
          const errores = Number(servicio.errores5xx) || 0;
          const tasa =
            typeof servicio.tasaDeError === 'number' ? porciento(servicio.tasaDeError * 100) : null;
          return celdaDoble(numero(errores), errores && tasa ? `${tasa} de las peticiones` : null);
        },
      },
    ],
    filas,
    vacio: { titulo: 'Sin servicios medidos', detalle: 'La recolección no devolvió servicios.' },
  });
}

/** El detalle del percentil: por tipo de operación y las operaciones más lentas. */
export function detalleDeLatencia(informe) {
  if (!informe || informe.sinDatos) {
    return [
      estadoVacio({
        titulo: 'Todavía no hay mediciones',
        detalle: 'El servicio no ha atendido peticiones desde su último arranque.',
      }),
    ];
  }
  const nodos = [
    rejillaDeIndicadores(
      [
        tarjetaDeIndicador({
          etiqueta: `Tiempo de respuesta (${informe.percentil})`,
          valor: milisegundos(informe.percentilMs),
          tono: informe.cumple ? 'exito' : 'error',
          detalle: `Objetivo: ${milisegundos(informe.objetivoMs)}`,
        }),
        tarjetaDeIndicador({ etiqueta: 'La más lenta', valor: milisegundos(informe.maximoMs) }),
        tarjetaDeIndicador({ etiqueta: 'Peticiones medidas', valor: informe.muestras }),
      ],
      { zona: 'cifras-de-latencia' },
    ),
  ];
  if (Array.isArray(informe.porTipo) && informe.porTipo.length > 0) {
    nodos.push(
      tablaDeDatos({
        titulo: 'Por tipo de operación',
        leyendaVisible: true,
        claveDeFila: (fila) => fila.tipo,
        columnas: [
          {
            clave: 'tipo',
            titulo: 'Tipo',
            principal: true,
            valor: (fila) => NOMBRE_DE_TIPO[fila.tipo] ?? fila.tipo,
          },
          {
            clave: 'percentilMs',
            titulo: informe.percentil,
            tipo: 'numero',
            valor: (fila) => milisegundos(fila.percentilMs),
          },
          {
            clave: 'maximoMs',
            titulo: 'La más lenta',
            tipo: 'numero',
            valor: (fila) => milisegundos(fila.maximoMs),
          },
          {
            clave: 'muestras',
            titulo: 'Peticiones',
            tipo: 'numero',
            valor: (fila) => numero(fila.muestras),
          },
        ],
        filas: informe.porTipo,
      }),
    );
  }
  if (Array.isArray(informe.operacionesMasLentas) && informe.operacionesMasLentas.length > 0) {
    nodos.push(
      tablaDeDatos({
        titulo: 'Operaciones más lentas',
        leyendaVisible: true,
        claveDeFila: (fila) => `${fila.metodo} ${fila.ruta}`,
        columnas: [
          {
            clave: 'operacion',
            titulo: 'Operación',
            principal: true,
            valor: operacionLegible,
          },
          {
            clave: 'percentilMs',
            titulo: informe.percentil,
            tipo: 'numero',
            valor: (fila) => milisegundos(fila.percentilMs),
          },
          {
            clave: 'muestras',
            titulo: 'Peticiones',
            tipo: 'numero',
            valor: (fila) => numero(fila.muestras),
          },
        ],
        filas: informe.operacionesMasLentas,
      }),
    );
  }
  return nodos;
}

/** Las consultas a la base de datos, en cifras (sin las sentencias). */
export function cifrasDeConsultas(informe) {
  if (!informe || informe.sinDatos) {
    return estadoVacio({
      titulo: 'Todavía no hay consultas medidas',
      detalle: 'Aparecerán en cuanto el servicio atienda peticiones.',
    });
  }
  return rejillaDeIndicadores(
    [
      tarjetaDeIndicador({
        etiqueta: `Tiempo de consulta (${informe.percentil})`,
        valor: milisegundos(informe.percentilMs),
        tono: informe.cumple ? 'exito' : 'error',
        detalle: `Objetivo: ${milisegundos(informe.objetivoMs)}`,
      }),
      tarjetaDeIndicador({ etiqueta: 'La más lenta', valor: milisegundos(informe.maximoMs) }),
      tarjetaDeIndicador({ etiqueta: 'Consultas medidas', valor: informe.muestras }),
      tarjetaDeIndicador({
        etiqueta: 'Consultas lentas',
        valor: informe.cuantasLentas,
        tono: informe.cuantasLentas ? 'advertencia' : 'exito',
        detalle: `Más de ${milisegundos(informe.umbralLentaMs)}`,
      }),
    ],
    { zona: 'cifras-de-consultas' },
  );
}

/** Qué funciones están limitadas ahora mismo (HU-DIS-003). */
export function listaDeDegradacion(degradacion) {
  const limitadas = Array.isArray(degradacion?.seccionesLimitadas)
    ? degradacion.seccionesLimitadas
    : [];
  if (!limitadas.length) {
    return estadoVacio({
      titulo: 'Todo funciona con normalidad',
      detalle: 'Ninguna función está limitada por la caída de otro servicio.',
    });
  }
  return tablaDeDatos({
    titulo: 'Funciones limitadas',
    claveDeFila: (s) => `${s.seccion}-${s.desde}`,
    columnas: [
      { clave: 'seccion', titulo: 'Función limitada', principal: true, valor: (s) => s.seccion },
      {
        clave: 'dependencia',
        titulo: 'Causa',
        valor: (s) => `${nombreDelServicio(s.dependencia)} no responde`,
      },
      { clave: 'desde', titulo: 'Desde', tipo: 'fecha', valor: (s) => fechaHora(s.desde) },
    ],
    filas: limitadas,
  });
}

/* =========================================================================
   Vista
   ========================================================================= */

/** Pinta una sección con su resultado: datos, o el fallo de ESA fuente. */
function pintarSeccion(seccion, resultado, pintar, { informe }) {
  if (resultado.status === 'fulfilled') {
    seccion.sello.dataset.estado = 'datos';
    const contenido = pintar(resultado.value);
    vaciar(seccion.zona).append(...(Array.isArray(contenido) ? contenido : [contenido]));
    return true;
  }
  const error = resultado.reason;
  const sinPermiso = error instanceof ErrorDeMetricas && error.esFaltaDePermiso();
  seccion.sello.dataset.estado = sinPermiso ? 'sin-permiso' : 'error';
  pintarEstado(
    seccion.zona,
    estadoDeError({
      titulo: sinPermiso ? error.avisoDePermiso.titulo : `No pudimos cargar ${informe}`,
      detalle: sinPermiso
        ? error.avisoDePermiso.detalle
        : 'El servicio de métricas no respondió. Se volverá a intentar en la próxima actualización.',
    }),
  );
  return false;
}

/**
 * Monta la página sobre sus zonas: `encabezado`, `indicadores` y `paneles`.
 *
 * @param {ParentNode} raiz
 * @param {{fetchImpl?: Function, descargar?: (nombre: string, contenido: string, tipo: string) => void,
 *          actualizarSola?: boolean}} [opciones]
 */
export function montarPanelMetricas(raiz, { fetchImpl, descargar, actualizarSola = true } = {}) {
  const zonaEncabezado = raiz.querySelector('[data-zona="encabezado"]');
  const zonaIndicadores = raiz.querySelector('[data-zona="indicadores"]');
  const zonaPaneles = raiz.querySelector('[data-zona="paneles"]');
  const guardar =
    descargar ??
    ((nombre, contenido, tipo) => {
      const enlace = document.createElement('a');
      enlace.href = URL.createObjectURL(new Blob([contenido], { type: tipo }));
      enlace.download = nombre;
      enlace.click();
      URL.revokeObjectURL(enlace.href);
    });

  const pedir = (ruta, aceptar) => pedirMetricas(ruta, { fetch: fetchImpl, aceptar });

  const disponibilidad = panel({
    id: 'disponibilidad',
    titulo: 'Disponibilidad por servicio',
    descripcion: 'Últimos 30 días, sin las ventanas de mantenimiento programado.',
    ancho: 'completo',
  });
  const tiempos = panel({
    id: 'tiempos',
    titulo: 'Tiempos de respuesta por servicio',
    ancho: 'completo',
  });
  const detalle = panel({
    id: 'latencia',
    titulo: 'Tiempo de respuesta en detalle',
    ancho: 'completo',
  });
  const consultas = panel({
    id: 'consultas',
    titulo: 'Consultas a la base de datos',
    ancho: 'completo',
  });
  const degradacion = panel({
    id: 'degradacion',
    titulo: 'Funciones limitadas',
    ancho: 'completo',
  });

  const descarga = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Descargar informe',
    atributos: { type: 'button' },
    datos: { accion: 'descargar-informe' },
  });
  descarga.addEventListener('click', async () => {
    try {
      guardar(
        'informe-de-tiempos.txt',
        await pedir('/latencia/informe/texto', 'text/plain'),
        'text/plain',
      );
    } catch {
      descarga.textContent = 'No se pudo descargar. Vuelve a intentarlo';
    }
  });

  async function cargar() {
    const [estado, informe, tablero, latencia, deConsultas, deDegradacion] =
      await Promise.allSettled([
        pedir(FUENTES.estado),
        pedir(FUENTES.disponibilidad),
        pedir(FUENTES.tecnicas),
        pedir(FUENTES.latencia),
        pedir(FUENTES.consultas),
        pedir(FUENTES.degradacion),
      ]);
    const valor = (resultado) => (resultado.status === 'fulfilled' ? resultado.value : null);

    zonaIndicadores?.replaceChildren(
      rejillaDeIndicadores(
        cifrasDeMetricas({
          disponibilidad: valor(informe),
          degradacion: valor(deDegradacion),
        }).map((cifra) => tarjetaDeIndicador(cifra)),
        { zona: 'cifras-de-metricas' },
      ),
    );
    const bien = [
      pintarSeccion(
        disponibilidad,
        informe,
        (datos) => tablaDeDisponibilidad(datos, valor(estado)),
        { informe: 'la disponibilidad' },
      ),
      pintarSeccion(
        tiempos,
        tablero,
        (datos) => [
          h('p', {
            clase: 't-meta',
            datos: { zona: 'medicion' },
            texto: `Medido el ${fechaHora(datos.generadoEn)}${
              typeof datos?.umbrales?.latenciaMs === 'number'
                ? ` · Objetivo de tiempo de respuesta: ${milisegundos(datos.umbrales.latenciaMs)}`
                : ''
            }`,
          }),
          tablaDeTiempos(datos),
        ],
        { informe: 'los tiempos de respuesta' },
      ),
      pintarSeccion(detalle, latencia, detalleDeLatencia, { informe: 'el detalle de tiempos' }),
      pintarSeccion(consultas, deConsultas, cifrasDeConsultas, { informe: 'las consultas' }),
      pintarSeccion(degradacion, deDegradacion, listaDeDegradacion, {
        informe: 'las funciones limitadas',
      }),
    ];
    return bien.some(Boolean);
  }

  const actualizacion = actualizacionPeriodica({
    nombre: 'las métricas',
    cadaMs: PERIODO_DE_METRICAS_MS,
    automatica: actualizarSola,
    cargar,
  });

  zonaEncabezado?.replaceChildren(
    encabezadoDePagina({
      titulo: 'Métricas de plataforma',
      descripcion: 'Disponibilidad y tiempos de respuesta de cada servicio, en vivo.',
      acciones: [actualizacion.elemento, descarga],
    }),
  );
  pintarEstado(zonaIndicadores, estadoDeCarga({ filas: 1, etiqueta: 'Cargando las métricas…' }));
  zonaPaneles?.replaceChildren(
    disponibilidad.elemento,
    tiempos.elemento,
    detalle.elemento,
    consultas.elemento,
    degradacion.elemento,
  );

  actualizacion.actualizar();
  return { cargar, actualizacion };
}
