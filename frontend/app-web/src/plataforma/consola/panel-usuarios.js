/**
 * Usuarios en la consola de control integral — HU-USR-008 (#561), 7.3.4.
 *
 * ## Qué hay aquí
 *
 * - El panel «Usuarios de la comunidad» del Resumen: cuántas cuentas hay en
 *   cada estado ahora (activas, pendientes de verificar, inactivas,
 *   suspendidas, baneadas), cuántas se registraron cada día de un rango
 *   (gráfico SVG con su tabla), el selector de rango y «Exportar indicadores»
 *   (CSV hecho en el navegador con lo que se está viendo, CA-02).
 * - Las consultas del directorio con sus filtros (rol, estado, fecha de
 *   registro) y la exportación del listado, que hace el servidor con esos
 *   mismos filtros (`GET /admin/jugadores/exportacion`).
 *
 * Todo sale de `ms-identidad-admin.yaml` 1.3.0 (`/admin/jugadores/indicadores`
 * y `/admin/jugadores/exportacion`). Como el resto de la consola, el panel no
 * calcula ni rellena nada: un estado que no llegó es un guion, no un cero, y
 * si el servicio no responde el panel lo dice y el resto del tablero sigue
 * (CA-03).
 *
 * Las «alertas de comportamiento sospechoso» de la ficha no están aquí:
 * ningún documento fija su umbral. Las que sí existen (alta frecuencia de
 * sanciones, con su umbral configurable) se ven en «Alertas de moderación».
 *
 * @module plataforma/consola/panel-usuarios
 */

import { RESULTADO, descargar } from './cliente-consola.js';
import { indicador, panel, pintarDesenlace, tabla } from './panel.js';
import { h } from '../../comun/ui/dom.js';

const SVG = 'http://www.w3.org/2000/svg';
const ANCHO_GRAFICO = 600;
const ALTO_GRAFICO = 160;
/** Hueco entre barras, en unidades del dibujo. */
const SEPARACION = 2;

/**
 * Los estados de una cuenta que publica el contrato, en su orden, con el
 * nombre que lee una persona: `etiqueta` debajo de una cifra, `nombre` en un
 * desplegable o en una celda.
 */
export const ESTADOS_DE_CUENTA = Object.freeze([
  { estado: 'ACTIVO', etiqueta: 'activas', nombre: 'Activas' },
  {
    estado: 'PENDIENTE_VERIFICACION',
    etiqueta: 'pendientes de verificar el correo',
    nombre: 'Pendientes de verificar el correo',
  },
  {
    estado: 'INACTIVO',
    etiqueta: 'inactivas (administrativas sin activar)',
    nombre: 'Inactivas (administrativas sin activar)',
  },
  { estado: 'SUSPENDIDO', etiqueta: 'suspendidas', nombre: 'Suspendidas' },
  { estado: 'BANEADO', etiqueta: 'baneadas', nombre: 'Baneadas' },
]);

/* -------------------------------------------------------------------------
   Consultas
   ------------------------------------------------------------------------- */

/**
 * Los filtros del directorio, en el orden de la consulta. Solo los que tienen
 * valor: sin filtros, la consulta es la de siempre.
 *
 * @param {{buscar?: string, rol?: string, estadoCuenta?: string, desde?: string,
 *          hasta?: string, ocultarPruebas?: boolean}} filtros
 * @returns {string[]} pares `clave=valor` ya codificados
 */
function paresDeFiltros(filtros) {
  const pares = [];
  const poner = (clave, valor) => {
    if (valor) {
      pares.push(`${clave}=${encodeURIComponent(valor)}`);
    }
  };
  poner('buscar', filtros?.buscar);
  poner('rol', filtros?.rol);
  poner('estado', filtros?.estadoCuenta);
  poner('registradoDesde', filtros?.desde);
  poner('registradoHasta', filtros?.hasta);
  if (filtros?.ocultarPruebas) {
    pares.push('ocultarPruebas=true');
  }
  return pares;
}

/**
 * Una página del directorio con sus filtros.
 *
 * @param {{pagina?: number}} estado el del directorio (página y filtros aplicados)
 * @param {number} filasPorPagina
 */
export function recursoDelDirectorio(estado, filasPorPagina) {
  const pares = [
    `page=${estado?.pagina ?? 0}`,
    `size=${filasPorPagina}`,
    ...paresDeFiltros(estado),
  ];
  return `/admin/jugadores?${pares.join('&')}`;
}

/** La exportación del directorio con los mismos filtros, sin página. */
export function recursoDeExportacion(estado) {
  const pares = paresDeFiltros(estado);
  return `/admin/jugadores/exportacion${pares.length ? `?${pares.join('&')}` : ''}`;
}

/**
 * Los indicadores de un rango.
 *
 * @param {{desde?: string, hasta?: string, ocultarPruebas?: boolean}} rango
 */
export function recursoDeIndicadores(rango) {
  const pares = [];
  if (rango?.desde) {
    pares.push(`desde=${encodeURIComponent(rango.desde)}`);
  }
  if (rango?.hasta) {
    pares.push(`hasta=${encodeURIComponent(rango.hasta)}`);
  }
  if (rango?.ocultarPruebas) {
    pares.push('ocultarPruebas=true');
  }
  return `/admin/jugadores/indicadores${pares.length ? `?${pares.join('&')}` : ''}`;
}

/* -------------------------------------------------------------------------
   Panel «Usuarios de la comunidad»
   ------------------------------------------------------------------------- */

/**
 * El panel del Resumen. Se carga por su cuenta, como los demás: si falla, lo
 * dice y no arrastra a nadie.
 *
 * @param {Function} consultarApi el `consultar` de la consola (inyectable)
 * @param {{guardar?: typeof guardarArchivo}} [opciones]
 */
export function panelDeUsuarios(consultarApi, { guardar = guardarArchivo } = {}) {
  const partes = panel({
    id: 'resumen-usuarios',
    titulo: 'Usuarios de la comunidad',
    descripcion:
      'Cuántas cuentas hay ahora en cada estado y cuántas se registraron cada día del periodo.',
    fuente: 'GET /api/v1/admin/jugadores/indicadores',
  });
  // Mismo criterio que el directorio: las cuentas de las pruebas automáticas
  // se dejan fuera por omisión (las excluye el servidor).
  const rango = { desde: '', hasta: '', ocultarPruebas: true };
  /** Los últimos indicadores pintados: lo único que se puede exportar. */
  let vigentes = null;

  const desde = campoDeFecha('desde', 'Desde');
  const hasta = campoDeFecha('hasta', 'Hasta');
  const ocultarPruebas = casilla('ocultarPruebas', 'Ocultar cuentas de pruebas automáticas');
  ocultarPruebas.entrada.checked = rango.ocultarPruebas;
  const exportar = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Exportar indicadores (CSV)',
    atributos: { type: 'button' },
    datos: { accion: 'exportar-indicadores' },
  });
  exportar.disabled = true;
  const formulario = h('form', {
    clase: 'consola-integral__filtros',
    datos: { zona: 'rango' },
    atributos: { 'aria-label': 'Periodo de los indicadores de usuarios' },
    hijos: [
      desde.campo,
      hasta.campo,
      ocultarPruebas.etiqueta,
      h('button', {
        clase: 'boton boton--primario',
        texto: 'Aplicar',
        atributos: { type: 'submit' },
      }),
      exportar,
    ],
  });
  const aviso = h('p', {
    clase: 't-meta',
    datos: { zona: 'aviso-rango' },
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  partes.elemento.insertBefore(formulario, partes.zona);
  partes.elemento.insertBefore(aviso, partes.zona);

  const cargar = async () => {
    vigentes = null;
    exportar.disabled = true;
    const desenlace = await consultarApi(recursoDeIndicadores(rango));
    pintarDesenlace(partes, desenlace, pintarIndicadores, { alReintentar: cargar });
    if (desenlace.resultado === RESULTADO.DATOS) {
      vigentes = desenlace.datos;
      exportar.disabled = false;
      // El rango que aplicó el servidor (sin rango, el de por omisión) queda
      // escrito: lo que se ve en el selector es lo que se está mirando.
      desde.entrada.value = vigentes?.registros?.desde ?? '';
      hasta.entrada.value = vigentes?.registros?.hasta ?? '';
    }
    return desenlace;
  };

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const inicio = desde.entrada.value;
    const fin = hasta.entrada.value;
    if (inicio && fin && inicio > fin) {
      // No se pregunta lo que no puede tener respuesta. El servidor lo
      // rechazaría igual (400); aquí se explica junto al selector.
      desde.entrada.setAttribute('aria-invalid', 'true');
      aviso.textContent = 'La fecha «Desde» es posterior a «Hasta»: corrige el periodo.';
      return;
    }
    desde.entrada.removeAttribute('aria-invalid');
    aviso.textContent = '';
    rango.desde = inicio;
    rango.hasta = fin;
    rango.ocultarPruebas = ocultarPruebas.entrada.checked;
    cargar();
  });

  exportar.addEventListener('click', () => {
    if (!vigentes) {
      return;
    }
    const registros = vigentes.registros ?? {};
    guardar(
      `indicadores-de-usuarios-${registros.desde ?? 'sin-fecha'}-a-${registros.hasta ?? 'sin-fecha'}.csv`,
      indicadoresComoCsv(vigentes),
      'text/csv;charset=utf-8',
    );
    aviso.textContent = 'Indicadores exportados.';
  });

  return { ...partes, cargar, id: 'resumen-usuarios' };
}

/**
 * Los indicadores, tal como llegan del servicio.
 *
 * @param {*} datos `IndicadoresDeCuentas` de ms-identidad-admin.yaml 1.3.0
 * @returns {HTMLElement[]}
 */
export function pintarIndicadores(datos) {
  const porEstado = datos?.porEstado ?? {};
  const estados = h('div', { clase: 'consola-integral__estados', datos: { zona: 'estados' } });
  estados.append(
    indicador({
      etiqueta: 'cuentas en total',
      valor: cifra(datos?.total),
      nota: datos?.ocultarPruebas ? 'Sin contar las cuentas de pruebas automáticas' : '',
    }),
  );
  for (const { estado, etiqueta } of ESTADOS_DE_CUENTA) {
    estados.append(indicador({ etiqueta, valor: cifra(porEstado[estado]) }));
  }
  // Un estado que el panel no conoce no desaparece del recuento: se enseña
  // con el nombre que trae.
  const conocidos = new Set(ESTADOS_DE_CUENTA.map(({ estado }) => estado));
  for (const [estado, cuantas] of Object.entries(porEstado)) {
    if (!conocidos.has(estado)) {
      estados.append(indicador({ etiqueta: `en estado ${estado}`, valor: cifra(cuantas) }));
    }
  }

  const partes = [estados];
  const registros = datos?.registros;
  if (registros && Array.isArray(registros.porDia)) {
    const resumen = resumenDeRegistros(registros);
    partes.push(h('h4', { clase: 't-etiqueta', texto: 'Registros por día' }));
    partes.push(h('p', { clase: 't-meta', datos: { zona: 'resumen-registros' }, texto: resumen }));
    if (Number(registros.total) === 0) {
      partes.push(
        h('p', {
          clase: 't-meta',
          datos: { zona: 'sin-registros' },
          texto: 'Nadie se registró en este periodo.',
        }),
      );
    }
    partes.push(graficoDeRegistros(registros));
    // El eje: primer y último día, para leer el gráfico sin pasar el cursor.
    // Es redundante para un lector de pantalla (lo dice el aria-label).
    partes.push(
      h('div', {
        clase: 'grafico-registros__eje t-meta',
        datos: { zona: 'eje' },
        atributos: { 'aria-hidden': 'true' },
        hijos: [
          h('span', { texto: fechaCompleta(registros.desde) }),
          h('span', { texto: fechaCompleta(registros.hasta) }),
        ],
      }),
    );
    partes.push(
      h('details', {
        clase: 'consola-integral__datos-grafico',
        hijos: [
          h('summary', { texto: 'Ver los registros por día en una tabla' }),
          tabla({
            columnas: ['Día', 'Cuentas nuevas'],
            filas: registros.porDia.map((dia) => [fechaCompleta(dia.fecha), cifra(dia.cuentas)]),
            resumen,
          }),
        ],
      }),
    );
  }
  if (datos?.calculadoEn) {
    partes.push(
      h('p', {
        clase: 't-meta',
        datos: { zona: 'contado' },
        texto: `Contado a las ${horaDe(datos.calculadoEn)}.`,
      }),
    );
  }
  return partes;
}

/**
 * Barras de cuentas nuevas por día. Una sola serie: el título de la sección
 * la nombra. Cada barra dice su valor al pasar el cursor (`<title>`), el
 * conjunto se describe en `aria-label` y los números exactos van en la tabla
 * de debajo. Colores del kit (`.grafico-registros__*`), sin librerías.
 *
 * @param {{desde?: string, hasta?: string, total?: number,
 *          porDia?: Array<{fecha: string, cuentas: number}>}} registros
 * @returns {SVGSVGElement}
 */
export function graficoDeRegistros(registros) {
  const porDia = Array.isArray(registros?.porDia) ? registros.porDia : [];
  const valores = porDia.map((dia) => Math.max(0, Number(dia.cuentas) || 0));
  const maximo = Math.max(1, ...valores);
  const paso = ANCHO_GRAFICO / Math.max(porDia.length, 1);

  const svg = nodoSvg('svg', {
    class: 'grafico-registros',
    viewBox: `0 0 ${ANCHO_GRAFICO} ${ALTO_GRAFICO}`,
    preserveAspectRatio: 'none',
    role: 'img',
    'aria-label': descripcionDelGrafico(registros, porDia, valores),
  });
  svg.append(
    nodoSvg('line', {
      class: 'grafico-registros__base',
      x1: 0,
      x2: ANCHO_GRAFICO,
      y1: ALTO_GRAFICO,
      y2: ALTO_GRAFICO,
    }),
  );
  porDia.forEach((dia, indice) => {
    const cuentas = valores[indice];
    const alto = (cuentas / maximo) * (ALTO_GRAFICO - 8);
    const barra = nodoSvg('rect', {
      class: 'grafico-registros__barra',
      x: indice * paso + Math.min(SEPARACION, paso / 4) / 2,
      y: ALTO_GRAFICO - alto,
      width: Math.max(paso - Math.min(SEPARACION, paso / 4), 0.5),
      height: alto,
    });
    barra.dataset.fecha = String(dia.fecha ?? '');
    barra.dataset.cuentas = String(cuentas);
    const titulo = nodoSvg('title');
    titulo.textContent = `${fechaCompleta(dia.fecha)}: ${cuentasNuevas(cuentas)}`;
    barra.append(titulo);
    svg.append(barra);
  });
  return svg;
}

/**
 * Los indicadores en CSV: el total, cada estado y la serie diaria. Con marca
 * de orden de bytes (las tildes en una hoja de cálculo) y fin de línea CRLF.
 * Se exporta exactamente lo que el panel tiene pintado.
 *
 * @param {*} datos `IndicadoresDeCuentas`
 * @returns {string}
 */
export function indicadoresComoCsv(datos) {
  const porEstado = datos?.porEstado ?? {};
  const conocidos = new Set(ESTADOS_DE_CUENTA.map(({ estado }) => estado));
  const filas = [
    ['Indicador', 'Valor'],
    ['Cuentas en total', datos?.total],
    ...ESTADOS_DE_CUENTA.map(({ estado, nombre }) => [nombre, porEstado[estado]]),
    ...Object.entries(porEstado)
      .filter(([estado]) => !conocidos.has(estado))
      .map(([estado, cuantas]) => [`En estado ${estado}`, cuantas]),
    ['Sin cuentas de pruebas automáticas', datos?.ocultarPruebas ? 'Sí' : 'No'],
    ['Contado', datos?.calculadoEn],
    [],
    ['Día', 'Cuentas nuevas'],
    ...(datos?.registros?.porDia ?? []).map((dia) => [dia.fecha, dia.cuentas]),
  ];
  return `\uFEFF${filas.map((fila) => fila.map(celdaCsv).join(',')).join('\r\n')}\r\n`;
}

/* -------------------------------------------------------------------------
   Exportar el directorio
   ------------------------------------------------------------------------- */

/**
 * El directorio con los filtros vigentes, en CSV. Lo arma el servidor en una
 * sola consulta (`/admin/jugadores/exportacion`): aquí solo se descarga y se
 * guarda con el nombre que da. Si no hay archivo (sin permiso, demasiadas
 * cuentas, servicio caído) no se guarda nada y se devuelve el motivo.
 *
 * @param {object} filtros el estado del directorio (filtros aplicados)
 * @param {{descargarApi?: typeof descargar, guardar?: typeof guardarArchivo}} [opciones]
 */
export async function exportarDirectorio(
  filtros,
  { descargarApi = descargar, guardar = guardarArchivo } = {},
) {
  const desenlace = await descargarApi(recursoDeExportacion(filtros));
  if (desenlace.resultado === RESULTADO.DATOS) {
    guardar(
      desenlace.nombreArchivo ?? 'directorio-de-cuentas.csv',
      desenlace.contenido,
      'text/csv;charset=utf-8',
    );
  }
  return desenlace;
}

/**
 * Guarda un archivo en el equipo de quien mira, sin pasar por ningún
 * servidor más.
 *
 * @param {string} nombre
 * @param {Blob|string} contenido
 * @param {string} tipo
 */
export function guardarArchivo(nombre, contenido, tipo) {
  const archivo = contenido instanceof Blob ? contenido : new Blob([contenido], { type: tipo });
  const enlace = h('a', { atributos: { href: URL.createObjectURL(archivo), download: nombre } });
  document.body.append(enlace);
  enlace.click();
  enlace.remove();
  URL.revokeObjectURL(enlace.href);
}

/* -------------------------------------------------------------------------
   Piezas
   ------------------------------------------------------------------------- */

/** Un campo de fecha con su etiqueta visible. */
function campoDeFecha(nombre, etiqueta) {
  const entrada = h('input', {
    clase: 'campo__control',
    atributos: { type: 'date', name: nombre },
  });
  const campo = h('label', {
    clase: 'campo',
    hijos: [h('span', { clase: 'campo__etiqueta', texto: etiqueta }), entrada],
  });
  return { campo, entrada };
}

/** Una casilla con su etiqueta. */
function casilla(nombre, texto) {
  const entrada = h('input', {
    clase: 'casilla__entrada',
    atributos: { type: 'checkbox', name: nombre },
  });
  const etiqueta = h('label', {
    clase: 'casilla',
    hijos: [entrada, h('span', { clase: 'casilla__etiqueta', texto })],
  });
  return { etiqueta, entrada };
}

/** @param {string} etiqueta @param {Record<string, string|number>} [atributos] */
function nodoSvg(etiqueta, atributos = {}) {
  const nodo = document.createElementNS(SVG, etiqueta);
  for (const [nombre, valor] of Object.entries(atributos)) {
    nodo.setAttribute(nombre, String(valor));
  }
  return nodo;
}

/** Un número que llegó, o null (el indicador pinta un guion), nunca un cero inventado. */
function cifra(valor) {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : null;
}

/** «1 cuenta nueva», «4 cuentas nuevas». */
function cuentasNuevas(cuantas) {
  return cuantas === 1 ? '1 cuenta nueva' : `${cuantas} cuentas nuevas`;
}

function resumenDeRegistros(registros) {
  const total = Number(registros?.total) || 0;
  return `${cuentasNuevas(total)} del ${fechaCompleta(registros?.desde)} al ${fechaCompleta(
    registros?.hasta,
  )}`;
}

function descripcionDelGrafico(registros, porDia, valores) {
  const total = valores.reduce((suma, valor) => suma + valor, 0);
  const periodo = `del ${fechaCompleta(registros?.desde)} al ${fechaCompleta(registros?.hasta)}`;
  if (total === 0) {
    return `Registros por día ${periodo}: ninguna cuenta nueva en ${porDia.length} días.`;
  }
  const mayor = valores.indexOf(Math.max(...valores));
  return (
    `Registros por día ${periodo}: ${cuentasNuevas(total)} en ${porDia.length} días; ` +
    `el día con más altas fue el ${fechaCompleta(porDia[mayor]?.fecha)}, con ${valores[mayor]}.`
  );
}

/** `2026-10-05` → «5 oct 2026». La fecha es un día, sin hora ni zona. */
function fechaCompleta(dia) {
  if (!dia) {
    return 'sin fecha';
  }
  const fecha = new Date(`${dia}T00:00:00Z`);
  if (Number.isNaN(fecha.getTime())) {
    return String(dia);
  }
  return fecha.toLocaleDateString('es', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  });
}

/** Hora de un instante, sin fecha: el panel se mira en el momento. */
function horaDe(valor) {
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) {
    return String(valor);
  }
  return fecha.toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' });
}

/**
 * Una celda CSV: entre comillas solo si hace falta, con las comillas
 * dobladas; lo que empiece por `=`, `+`, `-` o `@` lleva un apóstrofo delante
 * para que una hoja de cálculo no lo ejecute (inyección CSV). Los números van
 * tal cual.
 */
function celdaCsv(valor) {
  if (valor === null || valor === undefined) {
    return '';
  }
  if (typeof valor === 'number') {
    return String(valor);
  }
  let texto = String(valor);
  if (/^[=+\-@\t\r]/.test(texto)) {
    texto = `'${texto}`;
  }
  return /[",\r\n]/.test(texto) ? `"${texto.replaceAll('"', '""')}"` : texto;
}
