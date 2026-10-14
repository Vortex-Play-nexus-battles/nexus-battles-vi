/**
 * Tablero de metricas tecnicas (HU-MET-004) y de usuarios y moderacion
 * (HU-MET-001 y el panel principal de §7.3.8), sobre
 * contracts/openapi/metricas-plataforma.yaml 1.11.0.
 *
 * **No calcula nada.** Alertas, brechas y agregados vienen del servicio; esta
 * pantalla los pinta y los deja exportar (CA-02 de las dos historias). Lo
 * unico que hace con una serie por dia es poner a cero los dias que el
 * servicio no publica (un dia sin sanciones no sale en `porDia`), para que el
 * grafico no junte dos dias separados.
 * Sigue `shared/ui-kit/MAPEO-ERRORES.md`: se decide por `type`, nunca por el
 * texto.
 */

import { baseDeApi, ErrorDeMetricas } from './cliente-metricas.js';
import { actualizacionPeriodica } from '../../comun/ui/admin/actualizacion.js';
import { distintivoDeEstado } from '../../comun/ui/admin/estado.js';
import { nombreDelServicio } from '../../comun/ui/admin/servicios.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { h, nodo } from '../../comun/ui/dom.js';
import { fecha as fechaLocal, fechaHora, numero } from '../../comun/ui/formato.js';
import { graficoDeBarras } from '../../comun/ui/admin/grafico.js';
import { rejillaDeIndicadores, tarjetaDeIndicador } from '../../comun/ui/admin/indicador.js';
import { tablaDeDatos } from '../../comun/ui/admin/tabla-de-datos.js';
import { diaCompleto, diaCorto, serieDiaria } from '../../comun/ui/admin/fechas.js';
import { NOMBRE_DE_CATEGORIA_DE_REPORTE } from '../../comun/ui/admin/nombres.js';
import {
  fraseDeNoDisponible,
  pintarAlertasDeModeracion,
} from '../../comun/ui/admin/alertas-de-moderacion.js';

async function pedir(
  ruta,
  fetchImpl = fetchWithHttpErrorInterceptor,
  aceptar = 'application/json',
) {
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
    method: 'GET',
    headers: { Accept: aceptar },
  });
  if (!respuesta.ok) {
    let problema = null;
    try {
      problema = await respuesta.json();
    } catch {
      problema = null;
    }
    throw new ErrorDeMetricas(problema, respuesta.status);
  }
  return aceptar === 'text/plain' ? respuesta.text() : respuesta.json();
}

export const api = {
  tecnicas: (f) => pedir('/api/v1/tecnicas', f),
  tecnicasTexto: (f) => pedir('/api/v1/tecnicas/informe/texto', f, 'text/plain'),
  moderacion: (desde, hasta, f) => {
    const q = new URLSearchParams();
    if (desde) {
      q.set('desde', desde);
    }
    if (hasta) {
      q.set('hasta', hasta);
    }
    const consulta = q.toString();
    return pedir(`/api/v1/moderacion${consulta ? `?${consulta}` : ''}`, f);
  },
};

/* ---- Presentación (puro, probado) ---- */

/** Lo que se lee donde el servicio no dio la cifra. */
const SIN_DATO = 'Sin dato';

/** @returns {string} porcentaje legible o «Sin dato» */
export function porcentaje(fraccion) {
  return typeof fraccion === 'number' ? `${Math.round(fraccion * 100)} %` : SIN_DATO;
}

/** @returns {string} milisegundos redondeados o «Sin dato» */
export function ms(valor) {
  return typeof valor === 'number' ? `${numero(Math.round(valor))} ms` : SIN_DATO;
}

/**
 * Fila de la tabla tecnica para un servicio: valores y si cada uno esta en
 * alerta segun los umbrales que publica el propio servicio.
 */
export function filaDe(metricas, umbrales) {
  return {
    servicio: metricas.servicio,
    brecha: metricas.brecha ?? null,
    cpu: porcentaje(metricas.cpu),
    cpuEnAlerta: typeof metricas.cpu === 'number' && metricas.cpu > umbrales.cpu,
    memoria:
      typeof metricas.memoriaMb === 'number'
        ? `${numero(Math.round(metricas.memoriaMb))} MB`
        : SIN_DATO,
    peticiones: String(metricas.peticiones ?? 0),
    promedio: ms(metricas.promedioMs),
    maximo: ms(metricas.maximoMs),
    maximoEnAlerta:
      typeof metricas.maximoMs === 'number' && metricas.maximoMs > umbrales.latenciaMs,
    errores: String(metricas.errores5xx ?? 0),
    tasaDeError:
      typeof metricas.tasaDeError === 'number'
        ? `${numero(metricas.tasaDeError * 100, 2)} %`
        : SIN_DATO,
  };
}

/** «1 sanción», «4 sanciones». */
function cuantas(cantidad, singular, plural) {
  const n = Number.isFinite(cantidad) ? cantidad : 0;
  return `${numero(n)} ${n === 1 ? singular : plural}`;
}

/** Resumen de una linea del tablero de moderacion. */
export function resumenDeModeracion(tablero) {
  const s = tablero.sanciones;
  const tipos = s.porTipo ?? {};
  return (
    `${cuantas(s.total, 'sanción', 'sanciones')}: ` +
    `${cuantas(tipos.ADVERTENCIA ?? 0, 'advertencia', 'advertencias')}, ` +
    `${cuantas(tipos.SUSPENSION ?? 0, 'suspensión', 'suspensiones')} y ` +
    `${cuantas(tipos.BANEO ?? 0, 'baneo', 'baneos')} · ` +
    `${cuantas(s.moderadoresActivos, 'moderador sancionó', 'moderadores sancionaron')} · ` +
    `${cuantas(s.revertidas, 'revertida', 'revertidas')}`
  );
}

/**
 * Resumen de una linea de las cuentas que publica ms-identidad (HU-MET-001, via /moderacion):
 * el estado de las cuentas AHORA y las altas del periodo. Identidad cuenta; aqui solo se dice.
 */
export function resumenDeCuentas(cuentas) {
  const e = cuentas.porEstado ?? {};
  return (
    `${cuantas(cuentas.total, 'cuenta', 'cuentas')} ahora: ` +
    `${numero(e.ACTIVO ?? 0)} activas, ${numero(e.SUSPENDIDO ?? 0)} suspendidas, ` +
    `${numero(e.BANEADO ?? 0)} baneadas, ${numero(e.PENDIENTE_VERIFICACION ?? 0)} sin verificar ` +
    `y ${numero(e.INACTIVO ?? 0)} pendientes de activar · ` +
    `${cuantas(cuentas.registros?.total ?? 0, 'cuenta nueva', 'cuentas nuevas')} en el periodo`
  );
}

/** Un numero publicado o null: nunca un 0 que nadie dio. */
function cifra(valor) {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : null;
}

/** Todas las apelaciones del periodo, sumando sus estados. */
function apelacionesDelPeriodo(apelaciones) {
  return Object.values(apelaciones ?? {})
    .map(cifra)
    .reduce((suma, n) => suma + (n ?? 0), 0);
}

/**
 * Las cifras de arriba del tablero de moderacion. Lo que no llego (cuentas o
 * reportes) sale como «sin dato», nunca como cero.
 */
export function indicadoresDeModeracion(tablero) {
  const s = tablero.sanciones ?? {};
  const reportes = tablero.reportes ?? null;
  const cuentas = tablero.registroDeUsuarios ?? null;
  const pendientes = cifra(s.apelaciones?.PENDIENTE);
  return rejillaDeIndicadores(
    [
      tarjetaDeIndicador({
        etiqueta: 'Sanciones',
        valor: cifra(s.total),
        detalle: `${cuantas(s.moderadoresActivos, 'moderador', 'moderadores')} · ${cuantas(
          s.revertidas,
          'revertida',
          'revertidas',
        )}`,
      }),
      tarjetaDeIndicador({
        etiqueta: 'Reportes de comentarios',
        valor: cifra(reportes?.total),
        detalle: reportes
          ? `${cuantas(reportes.comentariosReportados, 'comentario', 'comentarios')} · ${cuantas(
              reportes.reportantes,
              'persona',
              'personas',
            )}`
          : 'No disponible ahora',
      }),
      tarjetaDeIndicador({
        etiqueta: 'Apelaciones pendientes',
        valor: pendientes,
        tono: pendientes > 0 ? 'advertencia' : null,
        detalle: `de ${cuantas(apelacionesDelPeriodo(s.apelaciones), 'apelación', 'apelaciones')} del periodo`,
      }),
      tarjetaDeIndicador({
        etiqueta: 'Cuentas nuevas',
        valor: cifra(cuentas?.registros?.total),
        detalle: cuentas ? 'registradas en el periodo' : 'No disponible ahora',
      }),
    ],
    { zona: 'indicadores-moderacion' },
  );
}

/** Una serie por dia, con los dias vacios a cero, como grafico de barras. */
function graficoPorDia({ titulo, desde, hasta, porDia, valor, unidad, zona }) {
  const grafico = graficoDeBarras({
    titulo: `${titulo} · del ${fechaLocal(desde)} al ${fechaLocal(hasta)}`,
    datos: serieDiaria(desde, hasta, porDia, valor).map((dia) => ({
      etiqueta: diaCorto(dia.fecha),
      valor: dia.valor,
    })),
    unidad,
    columnaEtiqueta: 'Día',
  });
  grafico.dataset.zona = zona;
  return grafico;
}

/** Los reportes del periodo por categoria, en el orden de §7.3.3. */
export function tablaDeCategorias(reportes) {
  const porCategoria = reportes?.porCategoria ?? {};
  return tablaDeDatos({
    titulo: 'Reportes por categoría',
    claveDeFila: (fila) => fila.categoria,
    columnas: [
      { clave: 'nombre', titulo: 'Categoría', principal: true },
      { clave: 'reportes', titulo: 'Reportes', tipo: 'numero' },
    ],
    filas: Object.entries(NOMBRE_DE_CATEGORIA_DE_REPORTE).map(([categoria, nombre]) => ({
      categoria,
      nombre,
      reportes: cifra(porCategoria[categoria]) ?? 0,
    })),
  });
}

/**
 * Pinta el tablero de moderacion en su zona: periodo, cifras, alertas, lo que
 * no se pudo leer, las tres series por dia y los reportes por categoria.
 *
 * @param {HTMLElement} zona
 * @param {object} tablero lo que publica `GET /moderacion` (1.11.0)
 */
export function pintarModeracion(zona, tablero) {
  const s = tablero.sanciones;
  zona.replaceChildren();
  zona.append(
    h('p', {
      clase: 't-meta',
      datos: { zona: 'periodo-consultado' },
      texto: `Del ${fechaLocal(s.desde)} al ${fechaLocal(s.hasta)}`,
    }),
    indicadoresDeModeracion(tablero),
    h('p', {
      clase: 't-cuerpo',
      datos: { zona: 'resumen-moderacion' },
      texto: resumenDeModeracion(tablero),
    }),
  );

  zona.append(h('h3', { texto: 'Alertas' }), ...pintarAlertasDeModeracion(tablero));

  const faltas = Array.isArray(tablero.noDisponibles) ? tablero.noDisponibles : [];
  if (faltas.length > 0) {
    zona.append(
      h('ul', {
        clase: 'pila pila--ajustada',
        datos: { zona: 'no-disponibles' },
        hijos: faltas.map((falta) =>
          h('li', { clase: 'aviso aviso--info', texto: fraseDeNoDisponible(falta) }),
        ),
      }),
    );
  }

  const graficos = [
    graficoPorDia({
      titulo: 'Sanciones por día',
      desde: s.desde,
      hasta: s.hasta,
      porDia: s.porDia,
      valor: (d) => cifra(d.emitidas) ?? 0,
      unidad: 'sanciones',
      zona: 'por-dia',
    }),
  ];
  if (tablero.reportes) {
    graficos.push(
      graficoPorDia({
        titulo: 'Reportes por día',
        desde: tablero.reportes.desde ?? s.desde,
        hasta: tablero.reportes.hasta ?? s.hasta,
        porDia: tablero.reportes.porDia,
        valor: (d) => cifra(d.reportes) ?? 0,
        unidad: 'reportes',
        zona: 'reportes-por-dia',
      }),
    );
  }
  zona.append(h('div', { clase: 'rejilla-graficos-admin', hijos: graficos }));
  if (tablero.reportes) {
    const categorias = tablaDeCategorias(tablero.reportes);
    categorias.dataset.zona = 'reportes-por-categoria';
    zona.append(categorias);
  }

  // Cuentas por estado y altas por dia (ms-identidad). Si identidad no las
  // dio, no se pinta ningun numero (ni un 0 que nadie dio): se dice que no
  // estan, con su motivo arriba (CA-03), y lo de moderacion sigue completo.
  zona.append(h('h3', { texto: 'Usuarios' }));
  const cuentas = tablero.registroDeUsuarios;
  if (!cuentas) {
    zona.append(
      h('p', {
        clase: 't-meta',
        datos: { zona: 'usuarios-no-disponibles' },
        texto: 'Cuentas por estado y cuentas nuevas por día: no disponibles ahora.',
      }),
    );
    return;
  }
  zona.append(
    h('p', {
      clase: 't-cuerpo',
      datos: { zona: 'resumen-usuarios' },
      texto: resumenDeCuentas(cuentas),
    }),
  );
  const altas = graficoDeBarras({
    titulo: `Cuentas nuevas por día · del ${diaCompleto(cuentas.registros?.desde)} al ${diaCompleto(
      cuentas.registros?.hasta,
    )}`,
    datos: (cuentas.registros?.porDia ?? []).map((d) => ({
      etiqueta: diaCorto(d.fecha),
      valor: cifra(d.cuentas),
    })),
    unidad: 'cuentas nuevas',
    columnaEtiqueta: 'Día',
  });
  altas.dataset.zona = 'altas-por-dia';
  zona.append(altas);
}

/* ---- DOM ---- */

/** Una cifra en alerta, marcada para quien no distingue el color. */
function enAlerta(texto, alerta) {
  if (!alerta) {
    return texto;
  }
  const marca = distintivoDeEstado({ texto, tono: 'advertencia' });
  marca.dataset.alerta = 'true';
  return marca;
}

/**
 * SA-8 (punto 24) — la tabla técnica con el componente de la consola: cada
 * servicio por su nombre, las cifras alineadas a la derecha y lo que supera
 * un umbral marcado. Un servicio que no se pudo medir lo dice en su fila, sin
 * el motivo técnico de la conexión.
 */
export function tablaTecnica(tablero) {
  const filas = tablero.servicios.map((m) => ({ ...filaDe(m, tablero.umbrales), origen: m }));
  const sinMedir = (f, valor) => (f.brecha ? null : valor);
  const tabla = tablaDeDatos({
    titulo: 'Consumo y errores por servicio',
    resumen: `${numero(filas.length)} ${filas.length === 1 ? 'servicio' : 'servicios'}`,
    claveDeFila: (f) => f.servicio,
    columnas: [
      {
        clave: 'servicio',
        titulo: 'Servicio',
        principal: true,
        valor: (f) =>
          f.brecha
            ? h('span', {
                clase: 'celda-doble',
                datos: { brecha: 'true' },
                hijos: [
                  h('span', { texto: nombreDelServicio(f.servicio) }),
                  h('span', { clase: 't-meta', texto: 'No se pudo medir en esta ronda' }),
                ],
              })
            : nombreDelServicio(f.servicio),
      },
      {
        clave: 'cpu',
        titulo: 'Procesador',
        tipo: 'numero',
        valor: (f) => sinMedir(f, enAlerta(f.cpu, f.cpuEnAlerta)),
      },
      { clave: 'memoria', titulo: 'Memoria', tipo: 'numero', valor: (f) => sinMedir(f, f.memoria) },
      {
        clave: 'peticiones',
        titulo: 'Peticiones',
        tipo: 'numero',
        valor: (f) => sinMedir(f, numero(Number(f.origen.peticiones) || 0)),
      },
      {
        clave: 'promedio',
        titulo: 'Tiempo medio',
        tipo: 'numero',
        valor: (f) => sinMedir(f, f.promedio),
      },
      {
        clave: 'maximo',
        titulo: 'Tiempo máximo',
        tipo: 'numero',
        valor: (f) => sinMedir(f, enAlerta(f.maximo, f.maximoEnAlerta)),
      },
      {
        clave: 'errores',
        titulo: 'Errores del servidor',
        tipo: 'numero',
        valor: (f) => sinMedir(f, numero(Number(f.origen.errores5xx) || 0)),
      },
      {
        clave: 'tasa',
        titulo: 'Tasa de error',
        tipo: 'numero',
        valor: (f) => sinMedir(f, f.tasaDeError),
      },
    ],
    filas,
  });
  tabla.dataset.zona = 'tabla-tecnica';
  return tabla;
}

/** Qué mide cada alerta, en palabras. */
const NOMBRE_DE_METRICA = Object.freeze({
  cpu: 'Procesador',
  latencia: 'Tiempo de respuesta',
  disponibilidad: 'Disponibilidad',
});

/** «Salas y partidas · Procesador: uso de procesador 91 % por encima del 75 %». */
export function textoDeAlerta(alerta) {
  if (typeof alerta === 'string') {
    return alerta;
  }
  const detalle = String(alerta?.detalle ?? '');
  return `${nombreDelServicio(alerta?.servicio)} · ${NOMBRE_DE_METRICA[alerta?.metrica] ?? 'Alerta'}: ${detalle}`;
}

function listaDeAlertas(alertas, vacio) {
  if (alertas.length === 0) {
    // Sin alertas no hay lista: se dice que todo está dentro de sus umbrales.
    const bien = nodo('p', 'aviso aviso--exito', vacio);
    bien.dataset.zona = 'alertas';
    return bien;
  }
  const ul = nodo('ul', 'pila pila--ajustada');
  ul.dataset.zona = 'alertas';
  alertas.forEach((a) => {
    ul.appendChild(nodo('li', 'aviso aviso--advertencia', textoDeAlerta(a)));
  });
  return ul;
}

/**
 * @param {HTMLElement} zona
 * @param {Error} error
 * @param {{informe: string, alReintentar?: Function|null}} contexto que se
 *   estaba pidiendo, en palabras. Las dos tarjetas de esta pantalla comparten
 *   la clase de error, asi que si no lo dice quien pinta, una acaba anunciando
 *   el fallo de la otra.
 */
function pintarError(zona, error, { informe, alReintentar = null } = { informe: 'el informe' }) {
  const deNegocio = error instanceof ErrorDeMetricas;
  // #527: sin rol administrativo no hay nada roto ni nada que reintentar; se
  // dice quien puede ver esto, no el codigo HTTP.
  const sinPermiso = deNegocio && error.esFaltaDePermiso();
  const aviso = nodo('div', sinPermiso ? 'aviso aviso--info' : 'aviso aviso--error');
  aviso.dataset.motivo = sinPermiso ? 'sin-permiso' : 'error';
  zona.replaceChildren(aviso);

  let titulo = `No pudimos cargar ${informe}`;
  let detalle = 'El servicio de métricas no respondió. Vuelve a intentarlo.';
  if (sinPermiso) {
    ({ titulo, detalle } = error.avisoDePermiso);
  } else if (deNegocio && error.titulo !== 'No se pudo obtener el informe') {
    // Un titulo que viene del servicio (RFC 9457) dice mas que el generico.
    titulo = error.titulo;
    detalle = error.message;
  }
  aviso.append(nodo('strong', 'aviso__titulo', titulo), nodo('p', 'aviso__detalle', detalle));
  if (alReintentar && !sinPermiso) {
    const boton = nodo('button', 'boton boton--secundario boton--pequeno', 'Reintentar');
    boton.type = 'button';
    boton.dataset.accion = 'reintentar';
    boton.addEventListener('click', () => alReintentar());
    aviso.append(boton);
  }
}

/**
 * Monta la vista: zonas `[data-zona="tecnicas"]`, `[data-zona="moderacion"]`,
 * formulario `[data-zona="periodo"]` y botones `[data-accion]`.
 */
export function montarTableroTecnico(raiz, { fetchImpl, descargar, actualizarSola = true } = {}) {
  const zonaEncabezado = raiz.querySelector('[data-zona="encabezado"]');
  const zonaControl = raiz.querySelector('[data-zona="control-tecnicas"]');
  const zonaTecnicas = raiz.querySelector('[data-zona="tecnicas"]');
  const zonaModeracion = raiz.querySelector('[data-zona="moderacion"]');
  const formPeriodo = raiz.querySelector('[data-zona="periodo"]');
  const guardar =
    descargar ??
    ((nombre, contenido, tipo) => {
      const enlace = document.createElement('a');
      enlace.href = URL.createObjectURL(new Blob([contenido], { type: tipo }));
      enlace.download = nombre;
      enlace.click();
      URL.revokeObjectURL(enlace.href);
    });
  let ultimoTecnico = null;
  let ultimaModeracion = null;

  async function cargarTecnicas() {
    // La primera vez se dice que se está midiendo; después, lo último bueno
    // se queda a la vista mientras llega lo nuevo.
    if (!zonaTecnicas.querySelector('[data-zona="tabla-tecnica"]')) {
      zonaTecnicas.replaceChildren(nodo('p', 't-meta', 'Midiendo los servicios…'));
    }
    try {
      const tablero = await api.tecnicas(fetchImpl);
      ultimoTecnico = tablero;
      zonaTecnicas.replaceChildren();
      const umbrales = tablero.umbrales ?? {};
      zonaTecnicas.appendChild(
        nodo(
          'p',
          't-meta',
          `Medido el ${fechaHora(ultimoTecnico.generadoEn)} · Umbrales: procesador ${porcentaje(umbrales.cpu)}, tiempo de respuesta ${numero(umbrales.latenciaMs)} ms, disponibilidad ${numero(umbrales.disponibilidadPorcentaje, 2)} %`,
        ),
      );
      zonaTecnicas.appendChild(tablaTecnica(ultimoTecnico));
      const sinMedir = ultimoTecnico.servicios.filter((m) => m.brecha);
      if (sinMedir.length > 0) {
        const brechas = nodo(
          'p',
          'aviso aviso--advertencia',
          `No se pudo medir ${sinMedir.length === 1 ? 'un servicio' : `${sinMedir.length} servicios`} en esta ronda: ${sinMedir.map((m) => nombreDelServicio(m.servicio)).join(', ')}.`,
        );
        brechas.dataset.zona = 'brechas';
        zonaTecnicas.appendChild(brechas);
      }
      zonaTecnicas.appendChild(nodo('h3', undefined, 'Alertas'));
      zonaTecnicas.appendChild(
        listaDeAlertas(ultimoTecnico.alertas, 'Ningún servicio supera sus umbrales.'),
      );
      return true;
    } catch (error) {
      pintarError(zonaTecnicas, error, {
        informe: 'las métricas técnicas',
        alReintentar: () => cargarTecnicas(),
      });
      return false;
    }
  }

  const actualizacion = actualizacionPeriodica({
    nombre: 'las métricas técnicas',
    cadaMs: 60_000,
    automatica: actualizarSola,
    cargar: cargarTecnicas,
  });
  zonaControl?.replaceChildren(actualizacion.elemento);
  zonaEncabezado?.replaceChildren(
    encabezadoDePagina({
      titulo: 'Tablero técnico',
      descripcion: 'Consumo, tráfico y errores de cada servicio, y la actividad de moderación.',
    }),
  );

  async function cargarModeracion(desde, hasta) {
    zonaModeracion.replaceChildren(nodo('p', 't-meta', 'Consultando moderación…'));
    try {
      ultimaModeracion = await api.moderacion(desde, hasta, fetchImpl);
      pintarModeracion(zonaModeracion, ultimaModeracion);
    } catch (error) {
      pintarError(zonaModeracion, error, {
        informe: 'la actividad de usuarios y moderación',
        alReintentar: () => cargarModeracion(desde, hasta),
      });
    }
  }

  raiz.querySelector('[data-accion="exportar-tecnicas"]')?.addEventListener('click', async () => {
    try {
      const texto = await api.tecnicasTexto(fetchImpl);
      guardar('metricas-tecnicas.txt', texto, 'text/plain');
    } catch (error) {
      pintarError(zonaTecnicas, error, { informe: 'el informe para exportar' });
    }
  });
  raiz.querySelector('[data-accion="exportar-moderacion"]')?.addEventListener('click', () => {
    if (ultimaModeracion) {
      guardar(
        'metricas-moderacion.json',
        JSON.stringify(ultimaModeracion, null, 2),
        'application/json',
      );
    }
  });
  formPeriodo?.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const datos = new FormData(formPeriodo);
    const desde = String(datos.get('desde') ?? '');
    const hasta = String(datos.get('hasta') ?? '');
    cargarModeracion(
      desde ? new Date(desde).toISOString() : null,
      hasta ? new Date(hasta).toISOString() : null,
    );
  });

  if (zonaControl) {
    actualizacion.actualizar();
  } else {
    cargarTecnicas();
  }
  cargarModeracion(null, null);
  return { cargarTecnicas, cargarModeracion, actualizacion };
}
