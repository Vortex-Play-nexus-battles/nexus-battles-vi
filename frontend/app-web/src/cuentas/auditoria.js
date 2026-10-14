/**
 * Registro de auditoría — §7.3.5 del documento oficial (SA-5),
 * ms-cumplimiento-auditoria 1.3.0.
 *
 * Toda acción administrativa de la plataforma con quién la hizo, sobre qué,
 * cuándo, desde qué dirección, cómo estaba, cómo quedó y por qué. La lee y la
 * exporta solo el Super Administrador; nadie la puede cambiar.
 *
 *   - Cuatro cifras: hoy, siete días, treinta días y los intentos de acceso no
 *     autorizado del mes. Una consulta caída dice «Sin dato», nunca cero.
 *   - Filtros: quién actuó (por su apodo), la acción, la cuenta afectada y el
 *     periodo, en hora de Colombia.
 *   - La tabla, dieciséis por página y lo más reciente primero, con nombres
 *     en vez de identificadores; «Ver» abre la entrada completa.
 *   - La exportación en CSV (para analizar) o PDF (para un informe), con los
 *     mismos filtros. Cada exportación queda registrada en la propia bitácora.
 *
 * La misma persona figura con su apodo en lo que registra ms-identidad y con
 * su `uid` en lo que registran los servicios de plataforma: filtrar por quién
 * actuó busca su cuenta y pide los dos.
 *
 * @module cuentas/auditoria
 */

import { urlDeVista } from '../comun/acceso.js';
import { construirPaginacion } from '../comun/paginacion.js';
import { limpiarAviso, pintarAviso } from '../comun/ui/aviso.js';
import { barraDeFiltros } from '../comun/ui/admin/barra-de-filtros.js';
import { distintivoDeEstado } from '../comun/ui/admin/estado.js';
import { instanteDelDia } from '../comun/ui/admin/fechas.js';
import { rejillaDeIndicadores, tarjetaDeIndicador } from '../comun/ui/admin/indicador.js';
import { NOMBRE_DE_ACCION, nombreDeAccion } from '../comun/ui/admin/nombres.js';
import { abrirPanelLateral, listaDeDatos } from '../comun/ui/admin/panel-lateral.js';
import { tablaDeDatos } from '../comun/ui/admin/tabla-de-datos.js';
import { h, vaciar } from '../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../comun/ui/estado-vista.js';
import { fecha, fechaHora } from '../comun/ui/formato.js';
import { encabezadoDePagina } from '../comun/ui/pagina.js';
import { consultar, descargar, RESULTADO } from '../plataforma/consola/cliente-consola.js';
import {
  cambioDe,
  celdaDeSobre,
  momentoDe,
  motivoLegible,
  quienDe,
  recortado,
  sobreDe,
  uidsDe,
  valorLegible,
} from '../plataforma/consola/entradas-de-auditoria.js';
import { guardarArchivo } from '../plataforma/consola/panel-usuarios.js';
import { panel } from '../plataforma/consola/panel.js';
import { directorioDeTitulares, esUid } from '../plataforma/consola/titulares.js';

/** RNF-USA-001: dieciséis por página. */
export const TAMANO_DE_PAGINA = 16;

/** Cuántas cuentas, como mucho, se toman de una búsqueda por apodo. */
export const CUENTAS_POR_BUSQUEDA = 20;

const DIA = 24 * 60 * 60 * 1000;

/** Las acciones del filtro, en el orden en que se leen. */
export const OPCIONES_DE_ACCION = Object.freeze(
  [
    'SANCION',
    'SUSPENSION',
    'CAMBIO_ROL',
    'CREACION',
    'ACTUALIZACION',
    'ELIMINACION_LOGICA',
    'ELIMINACION',
    'APROBACION',
    'RECHAZO',
    'SECURITY_BYPASS_ATTEMPT',
    'EXPORTACION',
    'OTRO',
  ].map((tipo) => Object.freeze([tipo, NOMBRE_DE_ACCION[tipo] ?? nombreDeAccion(tipo)])),
);

/** El tono de cada acción: lo que restringe en ámbar, lo que viola permisos en rojo. */
const TONO_DE_ACCION = Object.freeze({
  SANCION: 'advertencia',
  SUSPENSION: 'advertencia',
  ELIMINACION: 'advertencia',
  ELIMINACION_LOGICA: 'advertencia',
  SECURITY_BYPASS_ATTEMPT: 'error',
  CREACION: 'exito',
  APROBACION: 'exito',
  RECHAZO: 'info',
  CAMBIO_ROL: 'info',
  EXPORTACION: 'info',
});

/**
 * El día de hoy en Colombia (`aaaa-mm-dd`).
 *
 * @param {number} ahora milisegundos
 * @returns {string}
 */
export function hoyEnColombia(ahora) {
  return new Date(ahora - 5 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

/**
 * Un día del filtro como instante UTC, el que entiende ms-cumplimiento.
 *
 * @param {string|null|undefined} dia
 * @param {{finDelDia?: boolean}} [opciones]
 * @returns {string|null}
 */
export function instanteUtc(dia, opciones = {}) {
  const instante = instanteDelDia(dia, opciones);
  return instante ? new Date(instante).toISOString() : null;
}

/**
 * Lo que se pide a la bitácora: los filtros ya resueltos (personas y cuentas
 * como listas de identificadores) y la página.
 *
 * @param {{administradores?: string[], afectados?: string[], tipoAccion?: string|null,
 *   desde?: string|null, hasta?: string|null}} filtro
 * @param {number} pagina empieza en cero
 * @returns {string}
 */
export function recursoDeConsulta(filtro, pagina) {
  const pares = [
    `page=${pagina}`,
    `size=${TAMANO_DE_PAGINA}`,
    'sort=fechaHora,desc',
    'sort=id,desc',
    ...paresDeFiltro(filtro),
  ];
  return `/admin/auditoria?${pares.join('&')}`;
}

/**
 * La exportación con los mismos filtros (1.3.0): `csv` o `pdf`.
 *
 * @param {Parameters<typeof recursoDeConsulta>[0]} filtro
 * @param {'csv'|'pdf'} formato
 * @returns {string}
 */
export function recursoDeExportacion(filtro, formato) {
  return `/admin/auditoria/exportar?${[...paresDeFiltro(filtro), `formato=${formato}`].join('&')}`;
}

/** @param {Parameters<typeof recursoDeConsulta>[0]} filtro */
function paresDeFiltro(filtro) {
  const pares = [];
  for (const persona of filtro?.administradores ?? []) {
    pares.push(`administradorId=${encodeURIComponent(persona)}`);
  }
  for (const cuenta of filtro?.afectados ?? []) {
    pares.push(`afectado=${encodeURIComponent(cuenta)}`);
  }
  if (filtro?.tipoAccion) {
    pares.push(`tipoAccion=${encodeURIComponent(filtro.tipoAccion)}`);
  }
  if (filtro?.desde) {
    pares.push(`desde=${encodeURIComponent(filtro.desde)}`);
  }
  if (filtro?.hasta) {
    pares.push(`hasta=${encodeURIComponent(filtro.hasta)}`);
  }
  return pares;
}

/**
 * Las cuentas que corresponden a lo escrito: un `uid` tal cual o lo que
 * devuelve la búsqueda por apodo (las que coinciden exactamente, si las hay).
 *
 * @param {string} texto
 * @param {ReturnType<typeof directorioDeTitulares>} titulares
 * @returns {Promise<{uids: string[], apodos: string[], respondio: boolean}>}
 */
export async function cuentasDe(texto, titulares) {
  const limpio = String(texto ?? '').trim();
  if (!limpio) {
    return { uids: [], apodos: [], respondio: true };
  }
  if (esUid(limpio)) {
    return { uids: [limpio.toLowerCase()], apodos: [], respondio: true };
  }
  const desenlace = await titulares.buscar(limpio);
  if (desenlace.resultado === RESULTADO.VACIO) {
    return { uids: [], apodos: [], respondio: true };
  }
  if (desenlace.resultado !== RESULTADO.DATOS || !Array.isArray(desenlace.datos)) {
    return { uids: [], apodos: [], respondio: false };
  }
  const exactas = desenlace.datos.filter(
    (t) => String(t?.apodo ?? '').toLowerCase() === limpio.toLowerCase(),
  );
  const elegidas = (exactas.length > 0 ? exactas : desenlace.datos).slice(0, CUENTAS_POR_BUSQUEDA);
  return {
    uids: elegidas.map((t) => String(t.uid).toLowerCase()).filter(esUid),
    apodos: elegidas.map((t) => String(t.apodo ?? '')).filter(Boolean),
    respondio: true,
  };
}

/**
 * Los filtros de la barra, resueltos en identificadores.
 *
 * Quien actuó: su texto, sus apodos y sus `uid` (ms-identidad registra el
 * apodo; los servicios de plataforma, el `uid`). La cuenta afectada: sus
 * `uid`; si no aparece ninguna, lo escrito tal cual (un `parametro:…` o un
 * `comentario:…` también sirven).
 *
 * @param {Record<string, string>} valores
 * @param {ReturnType<typeof directorioDeTitulares>} titulares
 * @returns {Promise<{filtro: Parameters<typeof recursoDeConsulta>[0], sinIdentidad: boolean}>}
 */
export async function resolverFiltros(valores, titulares) {
  const quien = String(valores.quien ?? '').trim();
  const cuenta = String(valores.cuenta ?? '').trim();
  const [personas, cuentas] = await Promise.all([
    cuentasDe(quien, titulares),
    cuentasDe(cuenta, titulares),
  ]);

  const administradores = quien ? [...new Set([quien, ...personas.apodos, ...personas.uids])] : [];
  let afectados = [];
  if (cuenta) {
    afectados = cuentas.uids.length > 0 ? cuentas.uids : [cuenta];
  }
  return {
    filtro: {
      administradores,
      afectados,
      tipoAccion: valores.tipoAccion || null,
      desde: instanteUtc(valores.desde),
      hasta: instanteUtc(valores.hasta, { finDelDia: true }),
    },
    sinIdentidad: (quien && !personas.respondio) || (cuenta && !cuentas.respondio),
  };
}

/**
 * Fecha y hora con segundos, en la hora de quien mira.
 *
 * @param {string|null|undefined} valor
 * @returns {string|null}
 */
export function fechaHoraExacta(valor) {
  const momento = valor ? new Date(valor) : null;
  if (!momento || Number.isNaN(momento.getTime())) {
    return null;
  }
  return momento.toLocaleString('es-CO', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

/** La ficha administrativa de una cuenta (`?usuario=<uid>`), o null si la vista no existe. */
export function urlDeFicha(uid) {
  const ficha = urlDeVista('ficha-usuario');
  if (!ficha || !uid) {
    return null;
  }
  const destino = new URL(ficha);
  destino.searchParams.set('usuario', uid);
  return destino.href;
}

/**
 * Las columnas de la tabla.
 *
 * @param {{titulares: ReturnType<typeof directorioDeTitulares>, alVer: (entrada: object) => void}} contexto
 */
export function columnasDeAuditoria({ titulares, alVer }) {
  return [
    {
      clave: 'fechaHora',
      titulo: 'Fecha',
      tipo: 'fecha',
      principal: true,
      // El día y, debajo y en pequeño, la hora: en una línea la fecha larga
      // ensanchaba la columna y empujaba el resto fuera del panel.
      valor: (e) => celdaDeMomento(momentoDe(e)),
    },
    {
      clave: 'administrador',
      titulo: 'Quién',
      valor: (e) => {
        const quien = quienDe(e.administrador, titulares);
        return quien.identificado
          ? quien.texto
          : h('span', { clase: 'tabla-admin__sin-dato', texto: quien.texto });
      },
    },
    {
      clave: 'tipoAccion',
      titulo: 'Acción',
      tipo: 'estado',
      valor: (e) =>
        distintivoDeEstado({
          texto: nombreDeAccion(e.tipoAccion),
          tono: TONO_DE_ACCION[e.tipoAccion] ?? 'neutro',
        }),
    },
    {
      clave: 'afectado',
      titulo: 'Sobre',
      valor: (e) => celdaDeSobre(sobreDe(e.afectado, titulares), { enlaceDeCuenta: urlDeFicha }),
    },
    { clave: 'cambio', titulo: 'Cambio', valor: (e) => celdaRecortada(cambioDe(e)) },
    {
      clave: 'motivo',
      titulo: 'Motivo',
      valor: (e) => celdaRecortada(recortado(motivoLegible(e.motivo))),
    },
    {
      clave: 'ver',
      titulo: 'Detalle',
      tipo: 'accion',
      valor: (e) => {
        const boton = h('button', {
          clase: 'boton boton--contorno boton--pequeno',
          texto: 'Ver',
          atributos: {
            type: 'button',
            'aria-label': `Ver la acción del ${fechaHora(momentoDe(e))}`,
          },
          datos: { accion: 'ver-entrada' },
        });
        boton.addEventListener('click', () => alVer(e));
        return boton;
      },
    },
  ];
}

/**
 * La entrada completa, para el panel lateral: aquí sí van los
 * identificadores, para quien tenga que seguir el rastro.
 *
 * @param {object} entrada `AuditLogResponse`
 * @param {ReturnType<typeof directorioDeTitulares>} titulares
 * @returns {Array<[string, string|Node|null]>}
 */
export function paresDelDetalle(entrada, titulares) {
  const quien = quienDe(entrada.administrador, titulares);
  const sobre = sobreDe(entrada.afectado, titulares);
  const momento = momentoDe(entrada);
  const pares = [['Ocurrió', fechaHoraExacta(momento)]];
  if (entrada.ocurridoEn && entrada.fechaHora && entrada.ocurridoEn !== entrada.fechaHora) {
    pares.push(['Registrada', fechaHoraExacta(entrada.fechaHora)]);
  }
  pares.push(
    ['Quién', quien.texto],
    ['Acción', nombreDeAccion(entrada.tipoAccion)],
    ['Sobre', textoDeSobre(sobre)],
    ['Antes', valorLegible(entrada.valorAnterior)],
    ['Después', valorLegible(entrada.valorNuevo)],
    ['Motivo', motivoLegible(entrada.motivo)],
    ['Dirección IP', entrada.ipOrigen ?? null],
  );
  if (quien.uid) {
    pares.push(['Cuenta de quien actuó', quien.uid]);
  }
  if (sobre.referencia) {
    pares.push(['Identificador de lo afectado', sobre.referencia]);
  }
  pares.push(['N.º de registro', entrada.id ?? null]);
  return pares;
}

/**
 * Monta la bitácora sobre sus zonas.
 *
 * @param {Document|HTMLElement} raiz
 * @param {{consultarApi?: typeof consultar, descargarApi?: typeof descargar,
 *   guardar?: typeof guardarArchivo, ahora?: () => number,
 *   abrirDetalle?: typeof abrirPanelLateral}} [opciones]
 */
export function montarAuditoria(
  raiz,
  {
    consultarApi = consultar,
    descargarApi = descargar,
    guardar = guardarArchivo,
    ahora = Date.now,
    abrirDetalle = abrirPanelLateral,
  } = {},
) {
  const zonaEncabezado = raiz.querySelector('[data-zona="encabezado"]');
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const zonaIndicadores = raiz.querySelector('[data-zona="indicadores"]');
  const zonaConsola = raiz.querySelector('[data-zona="consola"]');
  const titulares = directorioDeTitulares({ consultarApi });

  zonaEncabezado?.replaceChildren(
    encabezadoDePagina({
      titulo: 'Registro de auditoría',
      descripcion:
        'Cada acción administrativa con su autor, su motivo y su origen. Inmutable y conservado al menos cinco años.',
    }),
  );

  let filtro = { administradores: [], afectados: [], tipoAccion: null, desde: null, hasta: null };
  let paginaActual = 0;
  let consultaVigente = 0;

  // La exportación va en la cabecera del panel: con los filtros que haya.
  const listado = panel({
    id: 'acciones',
    titulo: 'Acciones administrativas',
    ancho: 'completo',
    acciones: [
      botonDeExportacion('csv', 'Exportar CSV'),
      botonDeExportacion('pdf', 'Exportar PDF'),
    ],
  });
  const zonaTabla = h('div', { clase: 'pila pila--compacta', datos: { zona: 'tabla' } });
  const zonaPaginacion = h('div', { datos: { zona: 'paginacion' } });

  const barra = barraDeFiltros({
    etiqueta: 'Buscar y filtrar acciones',
    busqueda: { nombre: 'quien', etiqueta: 'Quién actuó', placeholder: 'Apodo de quien actuó' },
    filtros: [
      {
        nombre: 'tipoAccion',
        etiqueta: 'Acción',
        opciones: OPCIONES_DE_ACCION,
        todos: 'Todas las acciones',
      },
      { nombre: 'cuenta', etiqueta: 'Cuenta afectada', tipo: 'texto' },
      { nombre: 'desde', etiqueta: 'Desde', tipo: 'fecha' },
      { nombre: 'hasta', etiqueta: 'Hasta', tipo: 'fecha' },
    ],
    validar: (valores) =>
      valores.desde && valores.hasta && valores.desde > valores.hasta
        ? '«Desde» tiene que ser anterior o igual a «Hasta».'
        : null,
    alAplicar: async (valores) => {
      const { filtro: resuelto, sinIdentidad } = await resolverFiltros(valores, titulares);
      filtro = resuelto;
      if (sinIdentidad) {
        pintarAviso(zonaAviso, {
          tono: 'advertencia',
          titulo: 'No se pudieron buscar las cuentas por su apodo',
          detalle: 'Se filtra por lo escrito tal cual. Inténtalo de nuevo en unos segundos.',
        });
      } else {
        limpiarAviso(zonaAviso);
      }
      cargar(0);
    },
  });
  listado.sello.dataset.estado = 'datos';
  vaciar(listado.zona).append(barra.elemento, zonaTabla, zonaPaginacion);
  if (zonaConsola) {
    vaciar(zonaConsola).append(
      h('div', { clase: 'consola-integral__rejilla', hijos: [listado.elemento] }),
    );
  }

  /** @param {'csv'|'pdf'} formato */
  function botonDeExportacion(formato, texto) {
    const boton = h('button', {
      clase: 'boton boton--secundario',
      texto,
      atributos: { type: 'button' },
      datos: { accion: `exportar-${formato}` },
    });
    boton.addEventListener('click', async () => {
      ocupado(boton, true);
      try {
        const desenlace = await descargarApi(recursoDeExportacion(filtro, formato), {
          acepta: formato === 'pdf' ? 'application/pdf, application/problem+json' : undefined,
        });
        if (desenlace.resultado === RESULTADO.DATOS) {
          guardar(
            desenlace.nombreArchivo ?? `auditoria.${formato}`,
            desenlace.contenido,
            formato === 'pdf' ? 'application/pdf' : 'text/csv;charset=utf-8',
          );
          limpiarAviso(zonaAviso);
        } else {
          pintarAviso(zonaAviso, {
            tono: 'advertencia',
            titulo: 'No se pudo exportar el registro',
            detalle: desenlace.motivo,
          });
        }
      } finally {
        ocupado(boton, false);
      }
    });
    return boton;
  }

  function verEntrada(entrada) {
    abrirDetalle({
      titulo: nombreDeAccion(entrada.tipoAccion),
      subtitulo: sobreDe(entrada.afectado, titulares).texto ?? null,
      contenido: listaDeDatos(paresDelDetalle(entrada, titulares)),
    });
  }

  async function cargar(pagina) {
    paginaActual = pagina;
    const turno = ++consultaVigente;
    pintarEstado(zonaTabla, estadoDeCarga({ filas: 6, etiqueta: 'Cargando acciones…' }));
    const desenlace = await consultarApi(recursoDeConsulta(filtro, pagina));
    if (turno !== consultaVigente) {
      return;
    }
    if (desenlace.resultado === RESULTADO.VACIO) {
      pintarEstado(
        zonaTabla,
        estadoVacio({
          titulo: hayFiltros(filtro)
            ? 'Ninguna acción con estos filtros'
            : 'Todavía no hay acciones registradas',
        }),
      );
      vaciar(zonaPaginacion);
      return;
    }
    if (desenlace.resultado !== RESULTADO.DATOS) {
      pintarEstado(
        zonaTabla,
        estadoDeError({
          titulo: 'No se pudo cargar el registro',
          detalle: desenlace.motivo,
          alReintentar: () => cargar(paginaActual),
        }),
      );
      vaciar(zonaPaginacion);
      return;
    }
    const entradas = Array.isArray(desenlace.datos?.content) ? desenlace.datos.content : [];
    await titulares.nombrar(uidsDe(entradas));
    if (turno !== consultaVigente) {
      return;
    }
    const total = Number(desenlace.datos?.totalElements ?? entradas.length);
    vaciar(zonaTabla).append(
      tablaDeDatos({
        titulo: 'Acciones administrativas',
        resumen: `${total.toLocaleString('es-CO')} ${total === 1 ? 'acción' : 'acciones'}`,
        columnas: columnasDeAuditoria({ titulares, alVer: verEntrada }),
        filas: entradas,
        claveDeFila: (e) => e.id,
      }),
    );
    const totalPaginas = Number(desenlace.datos?.totalPages ?? 1);
    vaciar(zonaPaginacion).append(
      construirPaginacion(
        { paginaActual: Math.min(pagina, Math.max(totalPaginas - 1, 0)), totalPaginas },
        (otra) => cargar(otra),
        { etiqueta: 'Páginas del registro' },
      ),
    );
  }

  async function cargarIndicadores() {
    if (!zonaIndicadores) {
      return;
    }
    const momento = ahora();
    const hoy = instanteUtc(hoyEnColombia(momento));
    const hace = (dias) => new Date(momento - dias * DIA).toISOString();
    const cuenta = async (pares) => {
      const desenlace = await consultarApi(`/admin/auditoria?size=1&${pares}`);
      if (desenlace.resultado === RESULTADO.VACIO) {
        return 0;
      }
      if (desenlace.resultado !== RESULTADO.DATOS) {
        return null;
      }
      const total = Number(desenlace.datos?.totalElements);
      return Number.isFinite(total) ? total : null;
    };
    const [deHoy, deSemana, deMes, intentos] = await Promise.all([
      cuenta(`desde=${encodeURIComponent(hoy)}`),
      cuenta(`desde=${encodeURIComponent(hace(7))}`),
      cuenta(`desde=${encodeURIComponent(hace(30))}`),
      cuenta(`desde=${encodeURIComponent(hace(30))}&tipoAccion=SECURITY_BYPASS_ATTEMPT`),
    ]);
    zonaIndicadores.replaceChildren(
      rejillaDeIndicadores(
        [
          tarjetaDeIndicador({ etiqueta: 'Acciones hoy', valor: deHoy }),
          tarjetaDeIndicador({ etiqueta: 'Últimos 7 días', valor: deSemana }),
          tarjetaDeIndicador({ etiqueta: 'Últimos 30 días', valor: deMes }),
          tarjetaDeIndicador({
            etiqueta: 'Accesos no autorizados (30 días)',
            valor: intentos,
            tono: intentos > 0 ? 'advertencia' : null,
          }),
        ],
        { zona: 'indicadores' },
      ),
    );
  }

  cargarIndicadores();
  cargar(0);
  return { recargar: () => cargar(paginaActual) };
}

/**
 * El día y, debajo, la hora, en la hora de quien mira.
 *
 * @param {string|null} momento
 * @returns {Node|null}
 */
function celdaDeMomento(momento) {
  const instante = momento ? new Date(momento) : null;
  if (!instante || Number.isNaN(instante.getTime())) {
    return null;
  }
  return h('span', {
    clase: 'celda-doble',
    hijos: [
      h('span', { texto: fecha(momento) }),
      h('span', {
        clase: 't-meta',
        texto: instante.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' }),
      }),
    ],
  });
}

/** Un texto de celda en dos líneas como mucho; el completo, en el detalle. */
function celdaRecortada(texto) {
  return texto ? h('span', { clase: 'celda-recortada', texto, atributos: { title: texto } }) : null;
}

/**
 * Un botón ocupado mientras dura su acción: deshabilitado y con
 * `aria-busy`. Un solo sitio para las dos marcas (ESLint:
 * require-atomic-updates).
 *
 * @param {HTMLButtonElement} boton
 * @param {boolean} si
 */
function ocupado(boton, si) {
  boton.disabled = si;
  if (si) {
    boton.setAttribute('aria-busy', 'true');
  } else {
    boton.removeAttribute('aria-busy');
  }
}

/** «lyra_92 · Cuenta», o solo lo que es si no se sabe su clase. */
function textoDeSobre(sobre) {
  if (!sobre?.texto) {
    return null;
  }
  return sobre.clase ? `${sobre.texto} · ${sobre.clase}` : sobre.texto;
}

/** @param {Parameters<typeof recursoDeConsulta>[0]} filtro */
function hayFiltros(filtro) {
  return Boolean(
    filtro.administradores?.length ||
    filtro.afectados?.length ||
    filtro.tipoAccion ||
    filtro.desde ||
    filtro.hasta,
  );
}
