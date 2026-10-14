/**
 * Usuarios en la consola de control integral — HU-USR-008 (#561), 7.3.4.
 *
 * ## Qué hay aquí
 *
 * - El panel «Usuarios de la comunidad» del Resumen: cuántas cuentas hay en
 *   cada estado ahora (activas, sin verificar, pendientes de activar,
 *   suspendidas, baneadas), cuántas se registraron cada día de un rango
 *   (el gráfico de barras del kit, con su tabla), el periodo y «Exportar
 *   CSV» (hecho en el navegador con lo que se está viendo, CA-02).
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
import { panel, pintarDesenlace } from './panel.js';
import { h } from '../../comun/ui/dom.js';
import { barraDeFiltros } from '../../comun/ui/admin/barra-de-filtros.js';
import { graficoDeBarras } from '../../comun/ui/admin/grafico.js';
import { humanizar } from '../../comun/ui/admin/estado.js';
import { rejillaDeIndicadores, tarjetaDeIndicador } from '../../comun/ui/admin/indicador.js';

/** Valor del filtro «Cuentas» que deja fuera las de las pruebas automáticas. */
const SIN_CUENTAS_DE_PRUEBA = 'sin-pruebas';

/**
 * Los estados de una cuenta que publica el contrato, en su orden, con el
 * nombre que lee una persona: `etiqueta` debajo de una cifra, `nombre` en un
 * desplegable o en una celda.
 */
export const ESTADOS_DE_CUENTA = Object.freeze([
  { estado: 'ACTIVO', etiqueta: 'Activas', nombre: 'Activas', tono: 'exito' },
  {
    estado: 'PENDIENTE_VERIFICACION',
    etiqueta: 'Sin verificar el correo',
    nombre: 'Sin verificar el correo',
    tono: null,
  },
  {
    estado: 'INACTIVO',
    etiqueta: 'Pendientes de activar',
    nombre: 'Pendientes de activar',
    tono: null,
  },
  { estado: 'SUSPENDIDO', etiqueta: 'Suspendidas', nombre: 'Suspendidas', tono: 'advertencia' },
  { estado: 'BANEADO', etiqueta: 'Baneadas', nombre: 'Baneadas', tono: 'error' },
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
  const exportar = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Exportar CSV',
    atributos: { type: 'button' },
    datos: { accion: 'exportar-indicadores' },
  });
  exportar.disabled = true;
  const partes = panel({
    id: 'resumen-usuarios',
    titulo: 'Usuarios de la comunidad',
    acciones: [exportar],
    ancho: 'completo',
  });
  // Mismo criterio que el directorio: las cuentas de las pruebas automáticas
  // se excluyen con un filtro visible, que viene puesto y se puede quitar.
  const rango = { desde: '', hasta: '', ocultarPruebas: true };
  /** Los últimos indicadores pintados: lo único que se puede exportar. */
  let vigentes = null;

  const filtros = barraDeFiltros({
    etiqueta: 'Periodo de los indicadores de usuarios',
    filtros: [
      { nombre: 'desde', etiqueta: 'Desde', tipo: 'fecha' },
      { nombre: 'hasta', etiqueta: 'Hasta', tipo: 'fecha' },
      {
        nombre: 'cuentas',
        etiqueta: 'Cuentas de prueba',
        todos: 'Incluidas',
        opciones: [[SIN_CUENTAS_DE_PRUEBA, 'Excluidas']],
      },
    ],
    valores: { cuentas: SIN_CUENTAS_DE_PRUEBA },
    validar: (valores) =>
      valores.desde && valores.hasta && valores.desde > valores.hasta
        ? 'La fecha «Desde» es posterior a «Hasta»: corrige el periodo.'
        : null,
    alAplicar: (valores) => {
      rango.desde = valores.desde;
      rango.hasta = valores.hasta;
      rango.ocultarPruebas = valores.cuentas === SIN_CUENTAS_DE_PRUEBA;
      cargar();
    },
  });
  filtros.elemento.dataset.zona = 'rango';
  const aviso = h('p', {
    clase: 't-meta',
    datos: { zona: 'aviso-rango' },
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  partes.elemento.insertBefore(filtros.elemento, partes.zona);
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
      filtros.fijar({
        desde: vigentes?.registros?.desde ?? '',
        hasta: vigentes?.registros?.hasta ?? '',
      });
    }
    return desenlace;
  };

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

  return { ...partes, cargar, id: 'resumen-usuarios', filtros };
}

/**
 * Los indicadores, tal como llegan del servicio: una cifra por estado y las
 * altas de cada día en un gráfico con su tabla.
 *
 * @param {*} datos `IndicadoresDeCuentas` de ms-identidad-admin.yaml 1.3.0
 * @returns {HTMLElement[]}
 */
export function pintarIndicadores(datos) {
  const porEstado = datos?.porEstado ?? {};
  const tarjetas = [
    tarjetaDeIndicador({ etiqueta: 'Cuentas en total', valor: cifra(datos?.total) }),
  ];
  for (const { estado, etiqueta, tono } of ESTADOS_DE_CUENTA) {
    tarjetas.push(tarjetaDeIndicador({ etiqueta, valor: cifra(porEstado[estado]), tono }));
  }
  // Un estado que el panel no conoce no desaparece del recuento: se enseña
  // con su nombre legible.
  const conocidos = new Set(ESTADOS_DE_CUENTA.map(({ estado }) => estado));
  for (const [estado, cuantas] of Object.entries(porEstado)) {
    if (!conocidos.has(estado)) {
      tarjetas.push(tarjetaDeIndicador({ etiqueta: humanizar(estado), valor: cifra(cuantas) }));
    }
  }

  const partes = [rejillaDeIndicadores(tarjetas, { zona: 'estados' })];
  const registros = datos?.registros;
  if (registros && Array.isArray(registros.porDia)) {
    partes.push(graficoDeRegistros(registros));
  }
  return partes;
}

/**
 * Las altas de cada día del periodo: el gráfico del kit, con eje, valor de
 * cada barra al pasar el cursor, resumen y la misma serie en una tabla.
 *
 * @param {{desde?: string, hasta?: string, total?: number,
 *          porDia?: Array<{fecha: string, cuentas: number}>}} registros
 * @returns {HTMLElement}
 */
export function graficoDeRegistros(registros) {
  const porDia = Array.isArray(registros?.porDia) ? registros.porDia : [];
  return graficoDeBarras({
    titulo: `Registros por día · del ${fechaCompleta(registros?.desde)} al ${fechaCompleta(
      registros?.hasta,
    )}`,
    datos: porDia.map((dia) => ({
      etiqueta: fechaCorta(dia.fecha),
      valor: cifra(dia.cuentas),
    })),
    unidad: 'cuentas nuevas',
    columnaEtiqueta: 'Día',
  });
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
    ['Cuentas de prueba excluidas', datos?.ocultarPruebas ? 'Sí' : 'No'],
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

/** Un número que llegó, o null (el indicador pinta un guion), nunca un cero inventado. */
function cifra(valor) {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : null;
}

/** `2026-10-05` → «5 oct»: la etiqueta de una barra. */
function fechaCorta(dia) {
  if (!dia) {
    return 'sin fecha';
  }
  const fecha = new Date(`${dia}T00:00:00Z`);
  if (Number.isNaN(fecha.getTime())) {
    return String(dia);
  }
  return fecha.toLocaleDateString('es-CO', { day: 'numeric', month: 'short', timeZone: 'UTC' });
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
  return fecha.toLocaleDateString('es-CO', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  });
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
