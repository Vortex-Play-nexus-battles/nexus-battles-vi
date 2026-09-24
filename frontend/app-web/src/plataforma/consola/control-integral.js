/**
 * Consola de control integral -- la pantalla unica de operacion.
 *
 * ## Que responde
 *
 * Ocho preguntas, en el orden en que las hace quien opera el sistema:
 * como esta el Nexo, quien juega, que se esta jugando, como va la economia,
 * que se esta subastando, que torneos hay, que se ha moderado y que servicios
 * estan de pie.
 *
 * ## De donde sale cada numero
 *
 * De la API del servicio que es dueno del dato, por HTTP, con el JWT de quien
 * mira y contra los mismos guardas de RBAC que protegen el resto del
 * producto. **El navegador no habla con ninguna base de datos**, no hay una
 * base administrativa aparte, y ningun servicio lee las tablas de otro: cada
 * panel nombra su endpoint al pie, y ese endpoint es el que ya existia.
 *
 * Donde no hay backend, la consola lo dice -- NO IMPLEMENTADO -- en vez de
 * fabricar el dato. Donde el servicio no contesta, lo dice tambien --
 * SERVICIO DEGRADADO -- en vez de pintar un cero. Un panel en blanco con una
 * explicacion es informacion; un cero inventado es una mentira operativa.
 *
 * @module plataforma/consola/control-integral
 */

import { consultar, filasDe, totalDe, RESULTADO } from './cliente-consola.js';
import { indicador, moduloNoImplementado, panel, pintarDesenlace, tabla } from './panel.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { NOMBRE_DE_ROL } from '../../comun/shell.js';

/** Tamano de pagina del directorio. Coincide con el techo comodo del backend. */
const FILAS_POR_PAGINA = 20;

/**
 * Las ocho secciones, con el recurso del que se alimenta cada una.
 *
 * Es una tabla y no codigo repetido a proposito: anadir una seccion es una
 * fila aqui, y el orden de la pantalla es el orden de esta lista.
 */
export const SECCIONES = Object.freeze([
  { id: 'resumen', etiqueta: 'Resumen' },
  { id: 'jugadores', etiqueta: 'Jugadores' },
  { id: 'partidas', etiqueta: 'Partidas' },
  { id: 'economia', etiqueta: 'Economía' },
  { id: 'subastas', etiqueta: 'Subastas' },
  { id: 'torneos', etiqueta: 'Torneos' },
  { id: 'moderacion', etiqueta: 'Moderación y auditoría' },
  { id: 'sistema', etiqueta: 'Sistema' },
]);

/**
 * Monta la consola completa.
 *
 * @param {Document|HTMLElement} raiz
 * @param {{apodo?: string, rol?: string|null}} sesion
 * @param {{consultarApi?: typeof consultar}} [dependencias] inyectable en pruebas
 */
export function montarControlIntegral(raiz, sesion, { consultarApi = consultar } = {}) {
  const zonaEncabezado = raiz.querySelector('[data-zona="encabezado"]');
  if (zonaEncabezado) {
    zonaEncabezado.replaceChildren(
      encabezadoDePagina({
        titulo: 'Control integral del Nexo',
        descripcion: sesion?.apodo
          ? `Sesión de ${sesion.apodo} - ${NOMBRE_DE_ROL[sesion?.rol] ?? 'Operacion'}`
          : 'Operación de la plataforma',
      }),
    );
  }

  const zonaSecciones = raiz.querySelector('[data-zona="secciones"]');
  if (!zonaSecciones) {
    return null;
  }
  vaciar(zonaSecciones);

  const paneles = {
    resumen: seccionResumen(consultarApi),
    jugadores: seccionJugadores(consultarApi),
    partidas: seccionPartidas(consultarApi),
    economia: seccionEconomia(consultarApi),
    subastas: seccionSubastas(consultarApi),
    torneos: seccionTorneos(consultarApi),
    moderacion: seccionModeracion(consultarApi),
    sistema: seccionSistema(consultarApi),
  };

  for (const seccion of SECCIONES) {
    const bloque = h('section', {
      clase: 'consola-integral__seccion',
      datos: { seccion: seccion.id },
      atributos: { 'aria-label': seccion.etiqueta },
    });
    bloque.append(h('h2', { clase: 't-subtitulo', texto: seccion.etiqueta }));
    const rejilla = h('div', { clase: 'consola-integral__rejilla' });
    for (const p of paneles[seccion.id]) {
      rejilla.append(p.elemento);
    }
    bloque.append(rejilla);
    zonaSecciones.append(bloque);
  }

  // Cada panel se carga por su cuenta. No hay un `await` que los encadene:
  // que subastas tarde diez segundos no puede retrasar a jugadores.
  for (const lista of Object.values(paneles)) {
    for (const p of lista) {
      p.cargar();
    }
  }

  return paneles;
}

/* -------------------------------------------------------------------------
   Armador comun de panel
   ------------------------------------------------------------------------- */

/**
 * Un panel que consulta un recurso y pinta el resultado.
 *
 * @param {{id: string, titulo: string, descripcion?: string, recurso: string,
 *          pintar: (datos: *) => HTMLElement|HTMLElement[],
 *          consultarApi: Function}} opciones
 */
function panelDeRecurso({ id, titulo, descripcion, recurso, pintar, consultarApi }) {
  const partes = panel({ id, titulo, descripcion, fuente: `GET /api/v1${recurso}` });
  const cargar = async () => {
    const desenlace = await consultarApi(recurso);
    pintarDesenlace(partes, desenlace, pintar, { alReintentar: cargar });
    return desenlace;
  };
  return { ...partes, cargar, id };
}
/* -------------------------------------------------------------------------
   1. Resumen
   ------------------------------------------------------------------------- */

function seccionResumen(consultarApi) {
  const jugadores = panelDeRecurso({
    id: 'resumen-jugadores',
    titulo: 'Cuentas registradas',
    descripcion: 'Total del directorio de identidad.',
    recurso: '/admin/jugadores?page=0&size=1',
    consultarApi,
    pintar: (datos) => indicador({ etiqueta: 'cuentas', valor: totalDe(datos) }),
  });

  const servicios = panelDeRecurso({
    id: 'resumen-servicios',
    titulo: 'Servicios en linea',
    descripcion: 'Sondeo real de los diecisiete servicios del sistema.',
    recurso: '/admin/sistema/servicios',
    consultarApi,
    pintar: (datos) => {
      const total = Number(datos?.total ?? 0);
      const operativos = Number(datos?.operativos ?? 0);
      return indicador({
        etiqueta: 'operativos',
        valor: `${operativos} / ${total}`,
        nota: datos?.instante ? `Sondeado ${formatearInstante(datos.instante)}` : '',
      });
    },
  });

  const sanciones = panelDeRecurso({
    id: 'resumen-sanciones',
    titulo: 'Sanciones del último mes',
    descripcion: 'Recuento que publica el servicio de moderación.',
    recurso: '/sanciones/metricas',
    consultarApi,
    pintar: (datos) =>
      indicador({
        etiqueta: 'sanciones',
        valor: typeof datos?.total === 'number' ? datos.total : null,
        nota:
          datos?.apelaciones && typeof datos.apelaciones.PENDIENTE === 'number'
            ? `${datos.apelaciones.PENDIENTE} apelaciones pendientes`
            : '',
      }),
  });

  const torneos = panelDeRecurso({
    id: 'resumen-torneos',
    titulo: 'Torneos publicados',
    descripcion: 'Los que el servicio de torneos lista ahora mismo.',
    recurso: '/torneos',
    consultarApi,
    pintar: (datos) => indicador({ etiqueta: 'torneos', valor: totalDe(datos) }),
  });

  return [jugadores, servicios, sanciones, torneos];
}

/* -------------------------------------------------------------------------
   2. Jugadores
   ------------------------------------------------------------------------- */

function seccionJugadores(consultarApi) {
  const estado = { pagina: 0, buscar: '' };
  const partes = panel({
    id: 'directorio',
    titulo: 'Directorio de jugadores',
    descripcion:
      'Busca por apodo o correo. El filtrado y la paginación los hace el servidor, no el navegador.',
    fuente: 'GET /api/v1/admin/jugadores',
  });

  const buscador = h('form', { clase: 'consola-integral__buscador' });
  const campo = h('input', {
    clase: 'campo__entrada',
    atributos: {
      type: 'search',
      name: 'buscar',
      placeholder: 'Apodo o correo',
      'aria-label': 'Buscar jugador por apodo o correo',
    },
  });
  const boton = h('button', {
    clase: 'boton boton--primario',
    texto: 'Buscar',
    atributos: { type: 'submit' },
  });
  buscador.append(campo, boton);
  partes.elemento.insertBefore(buscador, partes.zona);

  const cargar = async () => {
    const recurso = `/admin/jugadores?page=${estado.pagina}&size=${FILAS_POR_PAGINA}${
      estado.buscar ? `&buscar=${encodeURIComponent(estado.buscar)}` : ''
    }`;
    const desenlace = await consultarApi(recurso);
    pintarDesenlace(partes, desenlace, (datos) => pintarDirectorio(datos, estado, cargar), {
      alReintentar: cargar,
    });
    return desenlace;
  };

  buscador.addEventListener('submit', (evento) => {
    evento.preventDefault();
    estado.buscar = campo.value.trim();
    estado.pagina = 0;
    cargar();
  });

  return [{ ...partes, cargar, id: 'directorio' }];
}

export function pintarDirectorio(datos, estado, recargar) {
  const filas = filasDe(datos).map((jugador) => [
    jugador.apodo ?? '--',
    jugador.email ?? '--',
    jugador.rol ?? '--',
    jugador.bloqueada ? 'BLOQUEADA' : (jugador.estado ?? '--'),
    formatearFecha(jugador.creadoEn),
    jugador.ultimoAcceso ? formatearFecha(jugador.ultimoAcceso) : 'Nunca ha entrado',
  ]);

  const cuerpo = tabla({
    columnas: ['Apodo', 'Correo', 'Rol', 'Estado', 'Registro', 'Última entrada'],
    filas,
    resumen: `Página ${(datos?.pagina ?? 0) + 1} de ${datos?.totalPaginas ?? 1}, ${
      datos?.total ?? filas.length
    } cuentas en total`,
  });

  const navegacion = h('nav', {
    clase: 'consola-integral__paginacion',
    atributos: { 'aria-label': 'Paginación del directorio' },
  });
  const totalPaginas = Number(datos?.totalPaginas ?? 1);
  const anterior = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Anterior',
    atributos: { type: 'button' },
    datos: { accion: 'anterior' },
  });
  anterior.disabled = estado.pagina <= 0;
  anterior.addEventListener('click', () => {
    estado.pagina = Math.max(0, estado.pagina - 1);
    recargar();
  });
  const siguiente = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Siguiente',
    atributos: { type: 'button' },
    datos: { accion: 'siguiente' },
  });
  siguiente.disabled = estado.pagina >= totalPaginas - 1;
  siguiente.addEventListener('click', () => {
    estado.pagina += 1;
    recargar();
  });
  navegacion.append(anterior, siguiente);

  return [cuerpo, navegacion];
}
/* -------------------------------------------------------------------------
   3. Partidas
   ------------------------------------------------------------------------- */

function seccionPartidas(consultarApi) {
  const salas = panelDeRecurso({
    id: 'salas',
    titulo: 'Salas y partidas',
    descripcion: 'Partidas y salas abiertas en este momento.',
    recurso: '/salas',
    consultarApi,
    pintar: (datos) =>
      tabla({
        columnas: ['Sala', 'Estado', 'Jugadores', 'Anfitrión'],
        filas: filasDe(datos).map((sala) => [
          sala.nombre ?? sala.idSala ?? sala.id ?? '--',
          sala.estado ?? '--',
          sala.participantes?.length ?? sala.jugadores ?? '--',
          sala.anfitrion ?? sala.creador ?? '--',
        ]),
      }),
  });

  return [salas, panelDeMisiones()];
}

/**
 * Misiones: no hay backend, y no se fabrica uno.
 *
 * Ningun servicio del catalogo publica misiones. La alternativa -- inventar
 * tres misiones de ejemplo para que el panel se vea lleno -- convertiria la
 * consola en una demo, que es justo lo que no puede ser.
 */
function panelDeMisiones() {
  const partes = panel({
    id: 'misiones',
    titulo: 'Misiones',
    descripcion: 'Progreso de misiones de los jugadores.',
  });
  const cargar = async () => {
    vaciar(partes.zona).append(
      moduloNoImplementado({
        razon:
          'Todavía no hay ningún servicio que publique misiones. No hay nada que ' +
          'consultar, y esta consola no fabrica datos para llenar un hueco.',
      }),
    );
    partes.sello.dataset.estado = 'neutro';
    partes.sello.textContent = 'NO IMPLEMENTADO';
    return { resultado: RESULTADO.NO_IMPLEMENTADO, datos: null, estado: null, recurso: '' };
  };
  return { ...partes, cargar, id: 'misiones' };
}

/* -------------------------------------------------------------------------
   4. Economia
   ------------------------------------------------------------------------- */

function seccionEconomia(consultarApi) {
  const transacciones = panelDeRecurso({
    id: 'transacciones',
    titulo: 'Movimientos de tu cuenta',
    descripcion:
      'El sistema no publica un libro mayor global: solo el historial de quien consulta.',
    // No hay un libro mayor global: /transacciones acepta POST para registrar
    // un movimiento y a un GET responde 405. Lo unico consultable es el
    // historial de quien pregunta. Se ensena eso, dicho con todas las letras,
    // en vez de fabricar un agregado que ningun servicio calcula.
    recurso: '/transacciones/mi-historial',
    consultarApi,
    pintar: (datos) =>
      tabla({
        columnas: ['Referencia', 'Tipo', 'Monto', 'Fecha'],
        filas: filasDe(datos)
          .slice(0, 25)
          .map((t) => [
            t.referencia ?? t.refId ?? t.id ?? '--',
            t.tipo ?? '--',
            t.monto ?? t.valor ?? '--',
            formatearFecha(t.fecha ?? t.creadoEn),
          ]),
      }),
  });

  const catalogo = panelDeRecurso({
    id: 'catalogo',
    titulo: 'Catálogo de productos',
    descripcion: 'Lo que está publicado a la venta ahora mismo.',
    recurso: '/productos',
    consultarApi,
    pintar: (datos) => indicador({ etiqueta: 'productos publicados', valor: totalDe(datos) }),
  });

  return [transacciones, catalogo];
}

/* -------------------------------------------------------------------------
   5. Subastas
   ------------------------------------------------------------------------- */

function seccionSubastas(consultarApi) {
  return [
    panelDeRecurso({
      id: 'subastas',
      titulo: 'Subastas activas',
      descripcion: 'Publicaciones abiertas ahora mismo.',
      recurso: '/subastas',
      consultarApi,
      pintar: (datos) =>
        tabla({
          columnas: ['Subasta', 'Elemento', 'Precio actual', 'Cierra'],
          filas: filasDe(datos)
            .slice(0, 25)
            .map((s) => [
              s.id ?? s.subastaId ?? '--',
              s.elemento?.nombre ?? s.nombreElemento ?? '--',
              s.precioActual ?? s.precioInicial ?? '--',
              formatearFecha(s.cierraEn ?? s.fechaCierre),
            ]),
        }),
    }),
  ];
}

/* -------------------------------------------------------------------------
   6. Torneos
   ------------------------------------------------------------------------- */

function seccionTorneos(consultarApi) {
  return [
    panelDeRecurso({
      id: 'torneos',
      titulo: 'Torneos',
      descripcion: 'Convocatorias que publica el servicio de torneos.',
      recurso: '/torneos',
      consultarApi,
      pintar: (datos) =>
        tabla({
          columnas: ['Torneo', 'Estado', 'Equipos', 'Inicio'],
          filas: filasDe(datos).map((t) => [
            t.nombre ?? t.id ?? '--',
            t.estado ?? '--',
            t.equipos?.length ?? t.totalEquipos ?? '--',
            formatearFecha(t.fechaInicio ?? t.inicio),
          ]),
        }),
    }),
  ];
}
/* -------------------------------------------------------------------------
   7. Moderacion y auditoria
   ------------------------------------------------------------------------- */

function seccionModeracion(consultarApi) {
  const auditoria = panelDeRecurso({
    id: 'auditoria',
    titulo: 'Bitácora de acciones administrativas',
    descripcion: 'Cada acción, con la identidad de quien la hizo.',
    // La ruta de LECTURA es /admin/auditoria. /admin/auditoria/eventos es la
    // de escritura: acepta POST y a un GET responde 405. Comprobado contra
    // dev con un token de administrador.
    recurso: '/admin/auditoria?page=0&size=15',
    consultarApi,
    pintar: (datos) =>
      tabla({
        columnas: ['Cuándo', 'Quién', 'Acción', 'Sobre', 'Motivo'],
        filas: filasDe(datos).map((e) => [
          formatearFecha(e.fechaHora),
          e.administrador ?? '--',
          e.tipoAccion ?? '--',
          e.afectado ?? '--',
          e.motivo ?? '--',
        ]),
        resumen: 'Últimos quince eventos registrados',
      }),
  });

  // No hay listado global de sanciones: /sanciones acepta POST para emitir una,
  // y la consulta es por usuario (/sanciones/usuarios/{id}). Lo unico agregado
  // que el servicio publica es este recuento, y es lo que se ensena. Inventar
  // una tabla de sanciones a partir del recuento seria exactamente lo que esta
  // consola no hace.
  const sanciones = panelDeRecurso({
    id: 'sanciones',
    titulo: 'Sanciones del último mes',
    descripcion: 'Recuento por tipo y estado de las apelaciones.',
    recurso: '/sanciones/metricas',
    consultarApi,
    pintar: (datos) => {
      const porTipo = datos?.porTipo ?? {};
      const apelaciones = datos?.apelaciones ?? {};
      return tabla({
        columnas: ['Concepto', 'Cantidad'],
        filas: [
          ['Total de sanciones', datos?.total ?? '--'],
          ...Object.entries(porTipo).map(([tipo, cuantas]) => [tipo, cuantas]),
          ...Object.entries(apelaciones).map(([estado, cuantas]) => [
            `Apelaciones ${estado.toLowerCase()}`,
            cuantas,
          ]),
          ['Moderadores activos', datos?.moderadoresActivos ?? '--'],
        ],
        resumen: 'Ventana de treinta días que calcula el servicio',
      });
    },
  });

  const comentarios = panelDeRecurso({
    id: 'comentarios',
    titulo: 'Comentarios reportados',
    descripcion: 'Cola de moderación de comentarios.',
    recurso: '/comentarios/moderacion',
    consultarApi,
    pintar: (datos) => indicador({ etiqueta: 'en cola', valor: totalDe(datos) }),
  });

  return [auditoria, sanciones, comentarios];
}

/* -------------------------------------------------------------------------
   8. Sistema
   ------------------------------------------------------------------------- */

function seccionSistema(consultarApi) {
  const servicios = panelDeRecurso({
    id: 'sistema',
    titulo: 'Estado de los diecisiete servicios',
    descripcion:
      'Sondeo real contra la salud de cada servicio. NO DESPLEGADO no es lo mismo que CAÍDO.',
    recurso: '/admin/sistema/servicios',
    consultarApi,
    pintar: (datos) => {
      const filas = (datos?.servicios ?? []).map((s) => [
        s.nombre ?? '--',
        selloDeServicio(s.estado),
        s.detalle ?? '--',
      ]);
      const resumen = h('p', {
        clase: 't-meta',
        texto: `${datos?.operativos ?? 0} operativos, ${datos?.caidos ?? 0} caídos, ${
          datos?.noDesplegados ?? 0
        } no desplegados, de ${datos?.total ?? filas.length} catalogados.`,
      });
      return [resumen, tabla({ columnas: ['Servicio', 'Estado', 'Detalle'], filas })];
    },
  });

  const parametros = panelDeRecurso({
    id: 'parametros',
    titulo: 'Parámetros de plataforma',
    descripcion: 'Valores en caliente que gobiernan el juego.',
    recurso: '/parametros',
    consultarApi,
    pintar: (datos) =>
      tabla({
        columnas: ['Clave', 'Valor', 'Unidad', 'Versión'],
        filas: filasDe(datos)
          .slice(0, 25)
          .map((p) => [p.clave ?? '--', p.valor ?? '--', p.unidad ?? '--', p.version ?? '--']),
      }),
  });

  return [servicios, parametros];
}

/** @param {string} estado */
function selloDeServicio(estado) {
  const tono =
    { OPERATIVO: 'ok', CAIDO: 'malo', NO_DESPLEGADO: 'neutro' }[String(estado)] ?? 'neutro';
  return h('span', {
    clase: 'sello-estado',
    texto: String(estado ?? '--').replace('_', ' '),
    datos: { estado: tono },
  });
}

/* -------------------------------------------------------------------------
   Formato
   ------------------------------------------------------------------------- */

/** Fecha corta y local. Nulo se pinta como guion, no como "hoy". */
export function formatearFecha(valor) {
  if (!valor) {
    return '--';
  }
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) {
    return String(valor);
  }
  return fecha.toLocaleString('es', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Hora del sondeo, sin fecha: el panel se mira en el momento. */
export function formatearInstante(valor) {
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) {
    return String(valor);
  }
  return fecha.toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}
