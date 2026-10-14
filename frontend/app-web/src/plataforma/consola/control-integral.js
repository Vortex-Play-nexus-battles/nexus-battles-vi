/**
 * Consola de control integral — la pantalla única de operación.
 *
 * ## Qué responde
 *
 * Las preguntas que hace quien opera el sistema, en su orden: cómo está el
 * Nexo, quién juega, qué se está jugando, cómo va la economía, qué se está
 * subastando, qué torneos hay, qué se ha moderado y qué servicios están de
 * pie.
 *
 * ## De dónde sale cada número
 *
 * De la API del servicio que es dueño del dato, por HTTP, con el JWT de quien
 * mira y contra los mismos guardas de RBAC que protegen el resto del
 * producto. El navegador no habla con ninguna base de datos y ningún servicio
 * lee las tablas de otro. Donde un servicio no contesta, el panel lo dice en
 * vez de pintar un cero: un hueco explicado es información; un cero inventado
 * es una mentira operativa.
 *
 * ## SA-2 — informe de revisión del módulo Super Administrador
 *
 * Fuera el texto de andamio que el informe señaló (puntos 2 a 15): la ruta de
 * cada consulta al pie del panel, los códigos HTTP, las etiquetas de estado
 * en mayúsculas, el «sondeo real», la tarjeta de un módulo que no existe
 * (misiones, que 7.2 y 7.3 no piden administrar) y el panel de movimientos,
 * que enseñaba el historial de la propia cuenta de quien mira y no el de la
 * plataforma. Las tablas son las del kit de la consola (columnas alineadas,
 * orden por columna, fichas en el teléfono) y la página dice de cuándo es lo
 * que se ve y se actualiza sola.
 *
 * @module plataforma/consola/control-integral
 */

import { consultar, descargar, filasDe, totalDe, RESULTADO } from './cliente-consola.js';
import { indicador, panel, pintarDesenlace } from './panel.js';
import {
  celdaDeSobre,
  momentoDe,
  quienDe,
  recortado,
  sobreDe,
  uidsDe,
} from './entradas-de-auditoria.js';
import { directorioDeTitulares } from './titulares.js';
import {
  ESTADOS_DE_CUENTA,
  exportarDirectorio,
  guardarArchivo,
  HISTORIAL_DE_SANCIONES,
  panelDeUsuarios,
  recursoDelDirectorio,
} from './panel-usuarios.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { NOMBRE_DE_ROL } from '../../comun/shell.js';
import { RUTAS, resolver } from '../../comun/sesion.js';
import { urlDeVista } from '../../comun/acceso.js';
import { nombreDeModalidad } from '../../comun/ui/juego/partida.js';
import { construirPaginacion } from '../../comun/paginacion.js';
import { creditos, numero } from '../../comun/ui/formato.js';
import { NOMBRE_DE_SANCION } from '../../comun/ui/sancion.js';
import { actualizacionPeriodica } from '../../comun/ui/admin/actualizacion.js';
import { barraDeFiltros } from '../../comun/ui/admin/barra-de-filtros.js';
import {
  ESTADOS_DE_CUENTA_ADMIN,
  ESTADOS_DE_SALA,
  ESTADOS_DE_SERVICIO,
  ESTADOS_DE_TORNEO,
  estadoSegun,
  humanizar,
} from '../../comun/ui/admin/estado.js';
import { rejillaDeIndicadores, tarjetaDeIndicador } from '../../comun/ui/admin/indicador.js';
import { nombreDeAccion } from '../../comun/ui/admin/nombres.js';
import { pintarAlertasDeModeracion } from '../../comun/ui/admin/alertas-de-moderacion.js';
import { fichaDeParametro, valorLegible } from '../../comun/ui/admin/parametros.js';
import { SITUACION_DEL_SERVICIO, nombreDelServicio } from '../../comun/ui/admin/servicios.js';
import { tablaDeDatos } from '../../comun/ui/admin/tabla-de-datos.js';

// SA-4: las alertas viven en el kit (las usa también el tablero de moderación);
// se reexportan para quien ya las importaba de aquí.
export { pintarAlertasDeModeracion };

/**
 * Cuentas por página del directorio: las dieciséis de los listados del
 * producto (RNF-USA-001).
 */
export const FILAS_POR_PAGINA = 16;

/** Cada cuánto se pone al día la consola sola, con la pestaña a la vista. */
export const PERIODO_DE_ACTUALIZACION_MS = 60_000;

/**
 * RFINAL-06 — lo que se pinta cuando el contrato de la fuente no trae el dato.
 * Un guion parecía un fallo de la pantalla y un identificador no lo lee
 * nadie; «Sin dato» dice exactamente lo que pasa.
 */
export const SIN_DATO = 'Sin dato';

/** «3 de 8», solo si el servidor dio las dos cifras. */
function deCuantos(parte, total) {
  return Number.isFinite(parte) && Number.isFinite(total)
    ? `${numero(parte)} de ${numero(total)}`
    : null;
}

/**
 * Las secciones, en el orden de la pantalla. Añadir una es una fila aquí.
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
 * @param {{consultarApi?: typeof consultar, descargarApi?: typeof descargar,
 *          guardar?: typeof guardarArchivo, actualizarSola?: boolean}} [dependencias]
 *   inyectables en pruebas
 */
export function montarControlIntegral(
  raiz,
  sesion,
  {
    consultarApi = consultar,
    descargarApi = descargar,
    guardar = guardarArchivo,
    actualizarSola = true,
  } = {},
) {
  const zonaSecciones = raiz.querySelector('[data-zona="secciones"]');
  if (!zonaSecciones) {
    return null;
  }
  vaciar(zonaSecciones);

  const paneles = {
    resumen: seccionResumen(consultarApi, { guardar }),
    jugadores: seccionJugadores(consultarApi, { descargarApi, guardar }),
    partidas: seccionPartidas(consultarApi),
    economia: seccionEconomia(consultarApi),
    subastas: seccionSubastas(consultarApi),
    torneos: seccionTorneos(consultarApi),
    moderacion: seccionModeracion(consultarApi),
    sistema: seccionSistema(consultarApi),
  };

  // La actualización en vivo: todos los paneles menos el directorio, que se
  // consulta cuando quien opera cambia un filtro o de página y no se le
  // redibuja mientras lo usa.
  const enVivo = Object.values(paneles)
    .flat()
    .filter((p) => p.id !== 'directorio');
  const actualizacion = actualizacionPeriodica({
    nombre: 'la consola',
    cadaMs: PERIODO_DE_ACTUALIZACION_MS,
    automatica: actualizarSola,
    cargar: async () => {
      const desenlaces = await Promise.all(enVivo.map((p) => p.cargar()));
      // «No se pudo» solo si no contestó nadie: con medio sistema caído la
      // consola sigue al día en lo demás, y cada panel caído lo dice.
      return desenlaces.some(
        (d) => d?.resultado === RESULTADO.DATOS || d?.resultado === RESULTADO.VACIO,
      );
    },
  });

  const zonaEncabezado = raiz.querySelector('[data-zona="encabezado"]');
  if (zonaEncabezado) {
    zonaEncabezado.replaceChildren(
      encabezadoDePagina({
        titulo: 'Control integral',
        descripcion: sesion?.rol
          ? `${NOMBRE_DE_ROL[sesion.rol] ?? 'Operación'} · jugadores, partidas, economía, moderación y servicios`
          : 'Jugadores, partidas, economía, moderación y servicios',
        acciones: [actualizacion.elemento],
      }),
    );
  }

  for (const seccion of SECCIONES) {
    const bloque = h('section', {
      clase: 'consola-integral__seccion',
      datos: { seccion: seccion.id },
      atributos: { 'aria-label': seccion.etiqueta },
    });
    bloque.append(h('h2', { clase: 't-subtitulo', texto: seccion.etiqueta }));
    bloque.append(
      h('div', {
        clase: 'consola-integral__rejilla',
        hijos: agruparPaneles(paneles[seccion.id].map((p) => p.elemento)),
      }),
    );
    zonaSecciones.append(bloque);
  }

  // La primera carga: el directorio por su cuenta y el resto con la marca de
  // hora de la actualización. Ningún `await` encadena paneles: que subastas
  // tarde diez segundos no retrasa a jugadores.
  paneles.jugadores[0].cargar();
  actualizacion.actualizar();

  return Object.assign(paneles, { actualizacion });
}

/**
 * Los paneles de cifras seguidos van juntos en una fila que se reparte entre
 * ellos (dos a la mitad, cuatro a un cuarto); los de ancho completo, cada uno
 * en su fila. Así no queda una tarjeta suelta junto a un hueco.
 *
 * @param {HTMLElement[]} elementos
 * @returns {HTMLElement[]}
 */
export function agruparPaneles(elementos) {
  const salida = [];
  let grupo = [];
  const cerrarGrupo = () => {
    if (grupo.length > 0) {
      salida.push(
        h('div', {
          clase: 'consola-integral__cifras',
          datos: { cifras: String(grupo.length) },
          hijos: grupo,
        }),
      );
      grupo = [];
    }
  };
  for (const elemento of elementos) {
    if (elemento.dataset.ancho === 'completo') {
      cerrarGrupo();
      salida.push(elemento);
    } else {
      grupo.push(elemento);
    }
  }
  cerrarGrupo();
  return salida;
}

/* -------------------------------------------------------------------------
   Armador común de panel
   ------------------------------------------------------------------------- */

/**
 * Un panel que consulta un recurso y pinta el resultado.
 *
 * @param {{id: string, titulo: string, descripcion?: string, recurso: string,
 *          pintar: (datos: *) => HTMLElement|HTMLElement[],
 *          consultarApi: Function, vacio?: {titulo: string, detalle?: string|null},
 *          acciones?: Node[], ancho?: 'completo'|null}} opciones
 */
function panelDeRecurso({
  id,
  titulo,
  descripcion,
  recurso,
  pintar,
  consultarApi,
  vacio,
  acciones,
  ancho = null,
}) {
  const partes = panel({ id, titulo, descripcion, acciones, ancho });
  const cargar = async () => {
    const desenlace = await consultarApi(recurso);
    pintarDesenlace(partes, desenlace, pintar, { alReintentar: cargar, vacio });
    return desenlace;
  };
  return { ...partes, cargar, id };
}

/** Enlace de texto a una vista de la consola. */
function enlaceA(vista, texto, parametros = {}) {
  let destino;
  try {
    destino = new URL(urlDeVista(vista));
  } catch {
    return null;
  }
  for (const [clave, valor] of Object.entries(parametros)) {
    destino.searchParams.set(clave, valor);
  }
  return h('a', { clase: 'panel-admin__enlace', texto, atributos: { href: destino.href } });
}

/* -------------------------------------------------------------------------
   1. Resumen
   ------------------------------------------------------------------------- */

function seccionResumen(consultarApi, { guardar } = {}) {
  const jugadores = panelDeRecurso({
    id: 'resumen-jugadores',
    titulo: 'Cuentas registradas',
    recurso: '/admin/jugadores?page=0&size=1',
    consultarApi,
    pintar: (datos) => indicador({ etiqueta: 'cuentas', valor: totalDe(datos) }),
  });

  const servicios = panelDeRecurso({
    id: 'resumen-servicios',
    titulo: 'Servicios en línea',
    recurso: '/admin/sistema/servicios',
    consultarApi,
    pintar: (datos) => {
      const total = Number.isFinite(datos?.total) ? datos.total : null;
      const operativos = Number.isFinite(datos?.operativos) ? datos.operativos : null;
      return indicador({
        etiqueta: 'operativos',
        valor: total !== null && operativos !== null ? `${operativos} de ${total}` : null,
        nota: notaDeServicios(datos),
      });
    },
  });

  const sanciones = panelDeRecurso({
    id: 'resumen-sanciones',
    titulo: 'Sanciones del último mes',
    recurso: '/sanciones/metricas',
    consultarApi,
    pintar: (datos) =>
      indicador({
        etiqueta: 'sanciones',
        valor: typeof datos?.total === 'number' ? datos.total : null,
        nota:
          datos?.apelaciones && typeof datos.apelaciones.PENDIENTE === 'number'
            ? `${numero(datos.apelaciones.PENDIENTE)} ${
                datos.apelaciones.PENDIENTE === 1 ? 'apelación pendiente' : 'apelaciones pendientes'
              }`
            : '',
      }),
  });

  const torneos = panelDeRecurso({
    id: 'resumen-torneos',
    titulo: 'Torneos publicados',
    recurso: '/torneos',
    consultarApi,
    pintar: (datos) => indicador({ etiqueta: 'torneos', valor: totalDe(datos) }),
  });

  // HU-USR-008 (§7.3.4): cuentas por estado, registros por día, periodo y
  // exportación. Va el último porque ocupa el ancho entero.
  const usuarios = panelDeUsuarios(consultarApi, { guardar });

  return [jugadores, servicios, sanciones, torneos, usuarios];
}

/** «1 lento · 2 caídos»: solo lo que no está bien, y solo si hay algo. */
function notaDeServicios(datos) {
  const partes = [];
  if (Number(datos?.lentos) > 0) {
    partes.push(`${datos.lentos} ${datos.lentos === 1 ? 'lento' : 'lentos'}`);
  }
  if (Number(datos?.caidos) > 0) {
    partes.push(`${datos.caidos} ${datos.caidos === 1 ? 'caído' : 'caídos'}`);
  }
  return partes.join(' · ');
}

/* -------------------------------------------------------------------------
   2. Jugadores
   ------------------------------------------------------------------------- */

/** Valor del filtro «Cuentas» que deja fuera las de las pruebas automáticas. */
const SIN_CUENTAS_DE_PRUEBA = 'sin-pruebas';

function seccionJugadores(
  consultarApi,
  { descargarApi = descargar, guardar = guardarArchivo } = {},
) {
  // HU-USR-008 (1.3.0) — búsqueda, rol, estado y fecha de registro: los
  // filtros APLICADOS, los de la página que se ve y los de la exportación.
  //
  // RFINAL-06 / SA-2 — las cuentas de las pruebas automáticas (las del dominio
  // reservado `.test`) se pueden excluir con un filtro VISIBLE, que viene
  // puesto y se quita como cualquier otro: nada se oculta sin que se vea.
  const estado = {
    pagina: 0,
    buscar: '',
    ocultarPruebas: true,
    rol: '',
    estadoCuenta: '',
    desde: '',
    hasta: '',
    historialSanciones: '',
  };

  const exportar = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Exportar CSV',
    atributos: { type: 'button' },
    datos: { accion: 'exportar-directorio' },
  });
  const partes = panel({
    id: 'directorio',
    titulo: 'Directorio de jugadores',
    acciones: [exportar],
    ancho: 'completo',
  });

  const filtros = barraDeFiltros({
    etiqueta: 'Buscar en el directorio',
    busqueda: {
      nombre: 'buscar',
      etiqueta: 'Buscar',
      // ms-identidad-admin 1.5.0: también por el ID de la cuenta o su uid.
      placeholder: 'Apodo, correo, nombre o ID',
    },
    filtros: [
      { nombre: 'rol', etiqueta: 'Rol', opciones: Object.entries(NOMBRE_DE_ROL) },
      {
        nombre: 'estadoCuenta',
        etiqueta: 'Estado',
        opciones: ESTADOS_DE_CUENTA.map(({ estado: valor, nombre }) => [valor, nombre]),
      },
      { nombre: 'desde', etiqueta: 'Registrada desde', tipo: 'fecha' },
      { nombre: 'hasta', etiqueta: 'Registrada hasta', tipo: 'fecha' },
      // SA-3 — §7.3.4 «filtros por historial de sanciones».
      {
        nombre: 'historialSanciones',
        etiqueta: 'Sanciones',
        todos: 'Todas las cuentas',
        opciones: HISTORIAL_DE_SANCIONES,
      },
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
        ? 'La fecha «Registrada desde» es posterior a «Registrada hasta»: corrige el periodo.'
        : null,
    alAplicar: (valores) => {
      Object.assign(estado, {
        buscar: valores.buscar,
        rol: valores.rol,
        estadoCuenta: valores.estadoCuenta,
        desde: valores.desde,
        hasta: valores.hasta,
        historialSanciones: valores.historialSanciones,
        ocultarPruebas: valores.cuentas === SIN_CUENTAS_DE_PRUEBA,
        pagina: 0,
      });
      cargar();
    },
  });
  const aviso = h('p', {
    clase: 't-meta',
    datos: { zona: 'aviso-directorio' },
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  partes.elemento.insertBefore(filtros.elemento, partes.zona);
  partes.elemento.insertBefore(aviso, partes.zona);

  const cargar = async () => {
    const desenlace = await consultarApi(recursoDelDirectorio(estado, FILAS_POR_PAGINA));
    pintarDesenlace(partes, desenlace, (datos) => pintarDirectorio(datos, estado, cargar), {
      alReintentar: cargar,
      vacio: {
        titulo: 'Ninguna cuenta coincide',
        detalle: 'Prueba con otra búsqueda o quita algún filtro.',
      },
    });
    return desenlace;
  };

  exportar.addEventListener('click', async () => {
    exportar.disabled = true;
    aviso.textContent = 'Preparando el archivo…';
    try {
      const desenlace = await exportarDirectorio(estado, { descargarApi, guardar });
      aviso.textContent =
        desenlace.resultado === RESULTADO.DATOS
          ? `Listado descargado: ${desenlace.nombreArchivo ?? 'directorio-de-cuentas.csv'}.`
          : desenlace.motivo || 'No se pudo exportar el listado.';
    } finally {
      exportar.disabled = false;
    }
  });

  return [{ ...partes, cargar, id: 'directorio', filtros }];
}

/**
 * Una página del directorio: la tabla del kit y la paginación común.
 *
 * @param {*} datos la página de `/admin/jugadores`
 * @param {{pagina: number}} estado
 * @param {() => void} recargar
 * @returns {HTMLElement[]}
 */
export function pintarDirectorio(datos, estado, recargar) {
  const cuentas = filasDe(datos);
  const total = Number.isFinite(datos?.total) ? datos.total : cuentas.length;
  const totalPaginas = Math.max(1, Number(datos?.totalPaginas) || 1);
  const pagina = Math.min(Math.max(0, Number(estado?.pagina) || 0), totalPaginas - 1);

  const cuerpo = tablaDeDatos({
    titulo: 'Directorio de jugadores',
    // La página en la que se está la dice la paginación, debajo.
    resumen: `${numero(total)} ${total === 1 ? 'cuenta' : 'cuentas'}`,
    columnas: [
      { clave: 'apodo', titulo: 'Apodo', principal: true },
      { clave: 'email', titulo: 'Correo' },
      { clave: 'rol', titulo: 'Rol', valor: (j) => NOMBRE_DE_ROL[j.rol] ?? j.rol },
      {
        clave: 'estado',
        titulo: 'Estado',
        tipo: 'estado',
        valor: (j) => estadoSegun(j.bloqueada ? 'BLOQUEADA' : j.estado, ESTADOS_DE_CUENTA_ADMIN),
      },
      {
        clave: 'creadoEn',
        titulo: 'Registro',
        tipo: 'fecha',
        valor: (j) => (j.creadoEn ? formatearDia(j.creadoEn) : null),
      },
      {
        clave: 'ultimoAcceso',
        titulo: 'Última entrada',
        tipo: 'fecha',
        valor: (j) => (j.ultimoAcceso ? formatearFecha(j.ultimoAcceso) : 'Nunca ha entrado'),
      },
      // SA-3 — cuántas sanciones se le han emitido (ms-identidad-admin 1.7.0);
      // un servicio anterior que no la publica deja «Sin dato», no un cero.
      {
        clave: 'sancionesRegistradas',
        titulo: 'Sanciones',
        tipo: 'numero',
        valor: (j) => (Number.isInteger(j.sancionesRegistradas) ? j.sancionesRegistradas : null),
      },
      {
        clave: 'acciones',
        titulo: 'Acciones',
        tipo: 'accion',
        valor: (j) => [enlaceAFicha(j), enlaceAGestion(j), enlaceASanciones(j)],
      },
    ],
    filas: cuentas,
    claveDeFila: (j) => String(j.uid ?? j.id ?? j.apodo ?? ''),
  });

  const paginacion = construirPaginacion(
    { paginaActual: pagina, totalPaginas },
    (nueva) => {
      estado.pagina = nueva;
      recargar();
    },
    { etiqueta: 'Páginas del directorio', flechas: 'siempre', casillas: 5 },
  );
  return [cuerpo, paginacion];
}

/**
 * UXC-9 — §7.3.4: de la fila al historial de sanciones de esa cuenta, sin
 * copiar su identificador. El panel de sanciones hace su propia guarda de rol.
 *
 * @param {{uid?: string, apodo?: string}} jugador
 * @returns {HTMLAnchorElement|null}
 */
function enlaceASanciones(jugador) {
  if (!jugador?.uid) {
    return null;
  }
  const destino = new URL(resolver(RUTAS.sanciones));
  destino.searchParams.set('usuario', jugador.uid);
  return h('a', {
    texto: 'Sanciones',
    atributos: {
      href: destino.href,
      'aria-label': `Ver las sanciones de ${jugador.apodo ?? 'esta cuenta'}`,
    },
    datos: { accion: 'ver-sanciones' },
  });
}

/**
 * HU-USR-010 — de la fila a la ficha administrativa de esa cuenta. Va por la
 * clave de la cuenta (`id`, la tienen todas); sin ella, por el `uid`.
 *
 * @param {{id?: number, uid?: string|null, apodo?: string}} jugador
 * @returns {HTMLAnchorElement|null}
 */
function enlaceAFicha(jugador) {
  const clave = Number(jugador?.id);
  const cuenta = Number.isInteger(clave) && clave > 0 ? String(clave) : jugador?.uid;
  if (!cuenta) {
    return null;
  }
  const destino = new URL(urlDeVista('ficha-usuario'));
  destino.searchParams.set('usuario', cuenta);
  return h('a', {
    texto: 'Ficha',
    atributos: {
      href: destino.href,
      'aria-label': `Ver la ficha de ${jugador.apodo ?? 'esta cuenta'}`,
    },
    datos: { accion: 'ver-ficha' },
  });
}

/**
 * RFINAL-06 — de la fila a la gestión de esa cuenta: la gestión espera la
 * clave interna (`id`, ms-identidad-admin.yaml 1.2.0). Sin ella no se pinta
 * un enlace que no abre nada.
 *
 * @param {{id?: number, apodo?: string}} jugador
 * @returns {HTMLAnchorElement|null}
 */
function enlaceAGestion(jugador) {
  const clave = Number(jugador?.id);
  if (!Number.isInteger(clave) || clave <= 0) {
    return null;
  }
  const destino = new URL(resolver(RUTAS.gestionUsuarios));
  destino.searchParams.set('usuario', String(clave));
  return h('a', {
    texto: 'Gestionar',
    atributos: {
      href: destino.href,
      'aria-label': `Gestionar la cuenta de ${jugador.apodo ?? 'esta cuenta'}`,
    },
    datos: { accion: 'gestionar' },
  });
}

/* -------------------------------------------------------------------------
   3. Partidas
   ------------------------------------------------------------------------- */

function seccionPartidas(consultarApi) {
  const salas = panelDeRecurso({
    id: 'salas',
    ancho: 'completo',
    titulo: 'Salas y partidas',
    recurso: '/salas',
    consultarApi,
    vacio: { titulo: 'No hay salas abiertas ahora mismo' },
    // `Sala` de salas-partidas.yaml no tiene nombre («las salas no tienen
    // nombre») y del anfitrión solo trae un UUID: la sala se describe por su
    // modalidad, y una columna que nunca tendría dato no se pinta.
    pintar: (datos) => {
      const filas = filasDe(datos);
      // SA-7 — el total de la página (`totalElementos`), no las filas de la
      // primera: con más de 16 salas el resumen se quedaba en 16.
      const total = totalDe(datos) ?? filas.length;
      return tablaDeDatos({
        titulo: 'Salas y partidas',
        resumen: `${numero(total)} ${total === 1 ? 'sala' : 'salas'}`,
        columnas: [
          { clave: 'sala', titulo: 'Sala', principal: true, valor: descripcionDeSala },
          {
            clave: 'estado',
            titulo: 'Estado',
            tipo: 'estado',
            ordenable: true,
            valor: (s) => estadoSegun(s.estado, ESTADOS_DE_SALA),
          },
          {
            clave: 'ocupacion',
            titulo: 'Jugadores',
            tipo: 'numero',
            ordenable: true,
            valor: (s) => deCuantos(s.ocupacion, s.maximoParticipantes),
          },
          {
            clave: 'creadaEn',
            titulo: 'Creada',
            tipo: 'fecha',
            ordenable: true,
            valor: (s) => fechaOVacio(s.creadaEn),
          },
        ],
        filas,
      });
    },
  });

  return [salas];
}

/** «1 contra 1 · privada»: lo que identifica una sala que no tiene nombre. */
function descripcionDeSala(sala) {
  const modalidad = nombreDeModalidad(sala?.modalidad, SIN_DATO);
  return sala?.privada ? `${modalidad} · privada` : modalidad;
}

/* -------------------------------------------------------------------------
   4. Economía
   ------------------------------------------------------------------------- */

function seccionEconomia(consultarApi) {
  const catalogo = panelDeRecurso({
    id: 'catalogo',
    titulo: 'Catálogo de productos',
    recurso: '/productos',
    consultarApi,
    acciones: [enlaceA('panel-catalogo', 'Gestionar el catálogo')].filter(Boolean),
    pintar: (datos) => indicador({ etiqueta: 'productos publicados', valor: totalDe(datos) }),
  });

  return [catalogo];
}

/* -------------------------------------------------------------------------
   5. Subastas
   ------------------------------------------------------------------------- */

function seccionSubastas(consultarApi) {
  return [
    panelDeRecurso({
      id: 'subastas',
      ancho: 'completo',
      titulo: 'Subastas activas',
      recurso: '/subastas',
      consultarApi,
      vacio: { titulo: 'No hay subastas abiertas ahora mismo' },
      // `SubastaResumen` de ms-subastas-listado.yaml: el producto viene en
      // `nombreProducto`, la puja más alta en `ofertaVigente` y el cierre en
      // `fechaFin`. El `id` es un UUID: no se pinta.
      pintar: (datos) => {
        const filas = filasDe(datos).slice(0, 25);
        const total = datos?.totalElementos;
        return tablaDeDatos({
          titulo: 'Subastas activas',
          resumen: Number.isFinite(total)
            ? `${numero(filas.length)} de ${numero(total)} subastas activas`
            : `${numero(filas.length)} subastas activas`,
          columnas: [
            { clave: 'nombreProducto', titulo: 'Elemento', principal: true, ordenable: true },
            {
              clave: 'ofertaVigente',
              titulo: 'Puja más alta',
              tipo: 'numero',
              ordenable: true,
              valor: (s) =>
                Number.isFinite(Number(s.ofertaVigente)) && s.ofertaVigente !== null
                  ? `${creditos(s.ofertaVigente)} créditos`
                  : null,
            },
            {
              clave: 'cantidadPujas',
              titulo: 'Pujas',
              tipo: 'numero',
              ordenable: true,
              valor: (s) => (Number.isFinite(s.cantidadPujas) ? numero(s.cantidadPujas) : null),
            },
            {
              clave: 'fechaFin',
              titulo: 'Cierra',
              tipo: 'fecha',
              ordenable: true,
              valor: (s) => fechaOVacio(s.fechaFin),
            },
          ],
          filas,
        });
      },
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
      ancho: 'completo',
      titulo: 'Torneos',
      recurso: '/torneos',
      consultarApi,
      vacio: { titulo: 'No hay torneos publicados' },
      // `TorneoResumen` de torneos.yaml: equipos en `equiposInscritos` de
      // `cupos`; del calendario, el cierre de inscripciones.
      pintar: (datos) => {
        const filas = filasDe(datos);
        return tablaDeDatos({
          titulo: 'Torneos',
          resumen: `${numero(filas.length)} ${filas.length === 1 ? 'torneo' : 'torneos'}`,
          columnas: [
            { clave: 'nombre', titulo: 'Torneo', principal: true, ordenable: true },
            {
              clave: 'estado',
              titulo: 'Estado',
              tipo: 'estado',
              ordenable: true,
              valor: (t) => estadoSegun(t.estado, ESTADOS_DE_TORNEO),
            },
            {
              clave: 'equiposInscritos',
              titulo: 'Equipos',
              tipo: 'numero',
              ordenable: true,
              valor: (t) => deCuantos(t.equiposInscritos, t.cupos),
            },
            {
              clave: 'inscripcionesCierranEn',
              titulo: 'Inscripciones hasta',
              tipo: 'fecha',
              ordenable: true,
              valor: (t) => fechaOVacio(t.inscripcionesCierranEn),
            },
          ],
          filas,
        });
      },
    }),
  ];
}

/* -------------------------------------------------------------------------
   7. Moderación y auditoría
   ------------------------------------------------------------------------- */

function seccionModeracion(consultarApi) {
  // §7.3.5 (SA-5): las quince acciones más recientes de la bitácora central,
  // con nombres en vez de identificadores; «Ver toda la auditoría» lleva al
  // registro completo, con sus filtros y su exportación.
  const auditoria = panelDeBitacora(consultarApi);

  // No hay listado global de sanciones en esta sección: el servicio publica
  // el recuento de la ventana de treinta días, y eso es lo que se enseña.
  const sanciones = panelDeRecurso({
    id: 'sanciones',
    ancho: 'completo',
    titulo: 'Sanciones del último mes',
    acciones: [enlaceA('sanciones-admin', 'Ir a sanciones')].filter(Boolean),
    recurso: '/sanciones/metricas',
    consultarApi,
    pintar: (datos) => pintarMetricasDeSanciones(datos),
  });

  const comentarios = panelDeRecurso({
    id: 'comentarios',
    titulo: 'Comentarios reportados',
    acciones: [enlaceA('moderar-comentarios', 'Abrir la moderación')].filter(Boolean),
    recurso: '/comentarios/moderacion',
    consultarApi,
    vacio: { titulo: 'No hay comentarios reportados' },
    pintar: (datos) => indicador({ etiqueta: 'por revisar', valor: totalDe(datos) }),
  });

  // §7.3.4 «alertas» y §7.3.8 «alertas de actividad sospechosa o alta
  // frecuencia de reportes»: los días con alta frecuencia de sanciones o de
  // reportes que evalúa el servicio de métricas contra los umbrales de
  // Parámetros (SA-4, metricas-plataforma 1.11.0).
  const alertas = panelDeRecurso({
    id: 'alertas-moderacion',
    titulo: 'Alertas de moderación',
    descripcion: 'Días con alta frecuencia de sanciones o de reportes en los últimos 30 días.',
    acciones: [enlaceA('tablero-tecnico', 'Ver el tablero de moderación')].filter(Boolean),
    recurso: '/moderacion',
    consultarApi,
    pintar: (datos) => pintarAlertasDeModeracion(datos),
  });

  // HU-USR-008 (D-45) — «comportamiento sospechoso», una de las dos señales que
  // fijó el PO: quien acumula varias sanciones sin revertir (#909, integrado en
  // SA-4). Sin `minimo` el servicio usa el del PO (3); el panel lo lee de la
  // respuesta, no lo repite.
  const reincidentes = panelDeReincidentes(consultarApi);

  // SA-20 (D-45, señal 1): la otra señal del PO, las cuentas que se bloquearon
  // al fallar el inicio de sesión (ms-identidad-admin 1.11.0), sin las de las
  // pruebas automáticas, como el directorio.
  const bloqueos = panelDeRecurso({
    id: 'bloqueos',
    ancho: 'completo',
    titulo: 'Cuentas bloqueadas por intentos fallidos',
    descripcion:
      'Cuentas que se bloquearon al fallar varias veces el inicio de sesión en la última ' +
      'semana: la otra alerta de comportamiento sospechoso.',
    recurso: '/admin/jugadores/bloqueos?ocultarPruebas=true',
    consultarApi,
    pintar: (datos) => pintarBloqueos(datos),
  });

  // Las dos cifras juntas arriba; las dos alertas, la bitácora y el recuento,
  // cada uno en su fila.
  return [alertas, comentarios, reincidentes, bloqueos, auditoria, sanciones];
}

/**
 * El panel de la bitácora central (§7.3.5, SA-5): las quince acciones más
 * recientes, con el apodo de quien actuó y de la cuenta afectada.
 *
 * @param {typeof consultar} consultarApi
 */
function panelDeBitacora(consultarApi) {
  const partes = panel({
    id: 'auditoria',
    titulo: 'Bitácora de acciones administrativas',
    acciones: [enlaceA('auditoria', 'Ver toda la auditoría')].filter(Boolean),
    ancho: 'completo',
  });
  const titulares = directorioDeTitulares({ consultarApi });
  const cargar = async () => {
    // La ruta de LECTURA es /admin/auditoria (la de escritura, /eventos, a un
    // GET responde 405). Fecha descendente y el `id` como desempate.
    const desenlace = await consultarApi(
      '/admin/auditoria?page=0&size=15&sort=fechaHora,desc&sort=id,desc',
    );
    if (desenlace.resultado === RESULTADO.DATOS) {
      await titulares.nombrar(uidsDe(filasDe(desenlace.datos)));
    }
    pintarDesenlace(partes, desenlace, (datos) => pintarBitacora(datos, titulares), {
      alReintentar: cargar,
      vacio: { titulo: 'Todavía no hay acciones registradas' },
    });
    return desenlace;
  };
  return { ...partes, cargar, id: 'auditoria' };
}

/**
 * Las acciones recientes de la bitácora: cuándo, quién, qué, sobre qué y por
 * qué, con los textos largos recortados (el registro completo los tiene
 * enteros) para que la tabla no se desborde.
 *
 * @param {{content?: object[]}} datos `PaginaDeAuditoria`
 * @param {{de: (uid: string) => ({apodo?: string}|null)}} titulares
 * @returns {HTMLElement}
 */
export function pintarBitacora(datos, titulares) {
  const filas = filasDe(datos);
  return tablaDeDatos({
    titulo: 'Bitácora de acciones administrativas',
    // Lo que hay, no lo que se pidió: con tres acciones no son «las quince».
    resumen:
      filas.length === 1
        ? 'La acción más reciente'
        : `Las ${numero(filas.length)} acciones más recientes`,
    claveDeFila: (e) => e.id,
    columnas: [
      {
        clave: 'fechaHora',
        titulo: 'Fecha',
        tipo: 'fecha',
        principal: true,
        valor: (e) => fechaOVacio(momentoDe(e)),
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
      { clave: 'tipoAccion', titulo: 'Acción', valor: (e) => nombreDeAccion(e.tipoAccion) },
      {
        clave: 'afectado',
        titulo: 'Sobre',
        valor: (e) => celdaDeSobre(sobreDe(e.afectado, titulares)),
      },
      { clave: 'motivo', titulo: 'Motivo', valor: (e) => recortado(e.motivo, 70) },
    ],
    filas,
  });
}

/**
 * El panel de quienes acumulan sanciones sin revertir, con el apodo de cada
 * cuenta (ms-identidad-admin 1.9.0, `GET /admin/titulares`): un identificador
 * en pantalla no le dice nada a quien opera.
 *
 * @param {typeof consultar} consultarApi
 */
function panelDeReincidentes(consultarApi) {
  const partes = panel({
    id: 'reincidentes',
    titulo: 'Usuarios con varias sanciones',
    descripcion:
      'Cuentas que acumulan sanciones sin revertir: una de las alertas de comportamiento ' +
      'sospechoso.',
    ancho: 'completo',
  });
  const cargar = async () => {
    const desenlace = await consultarApi('/sanciones/reincidentes');
    const apodos = new Map();
    const uids = (Array.isArray(desenlace.datos?.usuarios) ? desenlace.datos.usuarios : [])
      .map((u) => u?.usuarioId)
      .filter(Boolean);
    if (desenlace.resultado === RESULTADO.DATOS && uids.length > 0) {
      const nombres = await consultarApi(`/admin/titulares?uids=${uids.join(',')}`);
      if (nombres.resultado === RESULTADO.DATOS && Array.isArray(nombres.datos)) {
        nombres.datos.forEach((t) => apodos.set(String(t.uid).toLowerCase(), t.apodo));
      }
    }
    pintarDesenlace(partes, desenlace, (datos) => pintarReincidentes(datos, apodos), {
      alReintentar: cargar,
    });
    return desenlace;
  };
  return { ...partes, cargar, id: 'reincidentes' };
}

/**
 * La lista de reincidentes tal como la publica moderacion-sanciones
 * (`GET /sanciones/reincidentes`, 1.5.0). Un cuerpo que no tenga esa forma se
 * trata como «sin datos»: no se inventa a nadie.
 *
 * @param {{minimo?: number, total?: number,
 *          usuarios?: {usuarioId?: string, sanciones?: number, ultimaEn?: string}[]}} datos
 * @param {Map<string, string>} [apodos] uid → apodo
 * @returns {HTMLElement[]}
 */
export function pintarReincidentes(datos, apodos = new Map()) {
  const usuarios = (Array.isArray(datos?.usuarios) ? datos.usuarios : []).filter(
    (u) => u?.usuarioId,
  );
  const minimo = Number.isInteger(datos?.minimo) ? datos.minimo : null;
  const criterio = minimo === null ? 'varias' : `${minimo} o más`;
  if (usuarios.length === 0) {
    return [
      h('p', {
        clase: 't-meta',
        datos: { zona: 'sin-reincidentes' },
        texto: `Nadie tiene ${criterio} sanciones sin revertir.`,
      }),
    ];
  }

  const total =
    Number.isInteger(datos?.total) && datos.total >= usuarios.length
      ? datos.total
      : usuarios.length;
  const cortada =
    total > usuarios.length ? ` Se muestran los ${usuarios.length} con más sanciones.` : '';
  const filas = usuarios.map((u) => ({
    ...u,
    apodo: apodos.get(String(u.usuarioId).toLowerCase()) ?? null,
  }));
  return [
    h('p', {
      clase: 't-meta',
      datos: { zona: 'resumen-reincidentes' },
      texto: `${total} ${total === 1 ? 'usuario' : 'usuarios'} con ${criterio} sanciones sin revertir.${cortada}`,
    }),
    tablaDeDatos({
      titulo: 'Usuarios con varias sanciones sin revertir',
      claveDeFila: (u) => u.usuarioId,
      columnas: [
        {
          clave: 'apodo',
          titulo: 'Cuenta',
          principal: true,
          valor: (u) =>
            u.apodo ?? h('span', { clase: 'tabla-admin__sin-dato', texto: 'Sin apodo' }),
        },
        { clave: 'sanciones', titulo: 'Sanciones sin revertir', tipo: 'numero' },
        {
          clave: 'ultimaEn',
          titulo: 'Última sanción',
          tipo: 'fecha',
          valor: (u) => formatearFecha(u.ultimaEn),
        },
        {
          clave: 'acciones',
          titulo: 'Acciones',
          tipo: 'accion',
          valor: (u) => [
            enlaceAFicha({ uid: u.usuarioId, apodo: u.apodo ?? undefined }),
            enlaceASanciones({ uid: u.usuarioId, apodo: u.apodo ?? undefined }),
          ],
        },
      ],
      filas,
    }),
  ];
}

/**
 * `2026-10-08` → «8 de octubre»: un día que publica un servicio, dentro de una
 * frase. Un día no tiene hora ni zona: se escribe tal cual (leído como
 * instante sería la medianoche UTC, que en Colombia aún es el día anterior).
 *
 * @param {string} dia `aaaa-mm-dd`
 * @returns {string}
 */
function diaEnUnaFrase(dia) {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(dia ?? '').trim());
  if (!partes) {
    return String(dia);
  }
  const fecha = new Date(Date.UTC(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3])));
  return fecha.toLocaleDateString('es-CO', { day: 'numeric', month: 'long', timeZone: 'UTC' });
}

/**
 * Las cuentas que se bloquearon por intentos fallidos, tal como las publica
 * ms-identidad (`GET /admin/jugadores/bloqueos`, 1.11.0): la de bloqueo más
 * reciente primero, con lo que pasa ahora con cada una. Un cuerpo que no tenga
 * esa forma se trata como «sin datos»: no se inventa a nadie.
 *
 * @param {{desde?: string, total?: number,
 *          cuentas?: {uid?: string, apodo?: string, rol?: string, bloqueadaEn?: string,
 *                     vigente?: boolean, bloqueadaHasta?: string|null}[]}} datos
 * @returns {HTMLElement[]}
 */
export function pintarBloqueos(datos) {
  const cuentas = (Array.isArray(datos?.cuentas) ? datos.cuentas : []).filter((c) => c?.uid);
  const periodo = datos?.desde ? `desde el ${diaEnUnaFrase(datos.desde)}` : 'en la última semana';
  if (cuentas.length === 0) {
    return [
      h('p', {
        clase: 't-meta',
        datos: { zona: 'sin-bloqueos' },
        texto: `Ninguna cuenta se bloqueó por intentos fallidos ${periodo}.`,
      }),
    ];
  }

  const total =
    Number.isInteger(datos?.total) && datos.total >= cuentas.length ? datos.total : cuentas.length;
  const vigentes = cuentas.filter((c) => c.vigente === true).length;
  let ahora = '';
  if (vigentes === 1) {
    ahora = ' Una sigue bloqueada.';
  } else if (vigentes > 1) {
    ahora = ` ${numero(vigentes)} siguen bloqueadas.`;
  }
  const cortada =
    total > cuentas.length ? ` Se muestran las ${numero(cuentas.length)} más recientes.` : '';
  return [
    h('p', {
      clase: 't-meta',
      datos: { zona: 'resumen-bloqueos' },
      texto:
        `${numero(total)} ${total === 1 ? 'cuenta se bloqueó' : 'cuentas se bloquearon'} ` +
        `por intentos fallidos ${periodo}.${ahora}${cortada}`,
    }),
    tablaDeDatos({
      titulo: 'Cuentas bloqueadas por intentos fallidos',
      claveDeFila: (c) => c.uid,
      columnas: [
        {
          clave: 'apodo',
          titulo: 'Cuenta',
          principal: true,
          valor: (c) =>
            c.apodo ?? h('span', { clase: 'tabla-admin__sin-dato', texto: 'Sin apodo' }),
        },
        { clave: 'rol', titulo: 'Rol', valor: (c) => NOMBRE_DE_ROL[c.rol] ?? humanizar(c.rol) },
        {
          clave: 'bloqueadaEn',
          titulo: 'Se bloqueó',
          tipo: 'fecha',
          valor: (c) => formatearFecha(c.bloqueadaEn),
        },
        {
          // Lo que pasa ahora con la cuenta, en una celda: bloqueada (con su
          // distintivo) y hasta cuándo, o que ya puede entrar. Una columna
          // «Hasta» vacía para las que ya entran diría «Sin dato» sin serlo.
          clave: 'vigente',
          titulo: 'Ahora',
          valor: (c) =>
            c.vigente === true
              ? h('span', {
                  clase: 'bloqueo-vigente',
                  hijos: [
                    estadoSegun('BLOQUEADA', ESTADOS_DE_CUENTA_ADMIN),
                    c.bloqueadaHasta ? ` hasta ${formatearFecha(c.bloqueadaHasta)}` : null,
                  ],
                })
              : 'Ya puede entrar',
        },
        {
          clave: 'acciones',
          titulo: 'Acciones',
          tipo: 'accion',
          valor: (c) => [enlaceAFicha({ uid: c.uid, apodo: c.apodo ?? undefined })],
        },
      ],
      filas: cuentas,
    }),
  ];
}

/** El tipo de sanción en plural, para el rótulo de su cifra. */
const SANCIONES_EN_PLURAL = Object.freeze({
  ADVERTENCIA: 'Advertencias',
  SUSPENSION: 'Suspensiones',
  BANEO: 'Baneos definitivos',
});

/**
 * El recuento de sanciones de los últimos treinta días, en cifras: las
 * sanciones por tipo y quién las puso, y las apelaciones por estado. Una
 * apelación pendiente es trabajo por hacer: su cifra va en tono de aviso.
 *
 * @param {*} datos `MetricasSanciones` de moderacion-sanciones-admin.yaml
 * @returns {HTMLElement[]}
 */
export function pintarMetricasDeSanciones(datos) {
  const cifra = (valor) => (typeof valor === 'number' && Number.isFinite(valor) ? valor : null);
  const deSanciones = [
    tarjetaDeIndicador({ etiqueta: 'Sanciones en total', valor: cifra(datos?.total) }),
    ...Object.entries(datos?.porTipo ?? {}).map(([tipo, cuantas]) =>
      tarjetaDeIndicador({
        etiqueta: SANCIONES_EN_PLURAL[tipo] ?? NOMBRE_DE_SANCION[tipo] ?? humanizar(tipo),
        valor: cifra(cuantas),
      }),
    ),
    tarjetaDeIndicador({
      etiqueta: 'Moderadores que sancionaron',
      valor: cifra(datos?.moderadoresActivos),
    }),
  ];
  const deApelaciones = Object.entries(datos?.apelaciones ?? {}).map(([estadoApelacion, cuantas]) =>
    tarjetaDeIndicador({
      etiqueta:
        NOMBRE_DE_APELACION[estadoApelacion] ?? `Apelaciones: ${humanizar(estadoApelacion)}`,
      valor: cifra(cuantas),
      tono: estadoApelacion === 'PENDIENTE' && cifra(cuantas) > 0 ? 'advertencia' : null,
    }),
  );
  const grupo = (titulo, tarjetas) => [
    h('h4', { clase: 'panel-admin__grupo', texto: titulo }),
    rejillaDeIndicadores(tarjetas),
  ];
  return [
    ...grupo('Sanciones', deSanciones),
    ...(deApelaciones.length > 0 ? grupo('Apelaciones', deApelaciones) : []),
  ];
}

/** El estado de una apelación en el recuento de sanciones. */
const NOMBRE_DE_APELACION = Object.freeze({
  PENDIENTE: 'Apelaciones pendientes',
  ACEPTADA: 'Apelaciones aceptadas',
  RECHAZADA: 'Apelaciones rechazadas',
});

/* -------------------------------------------------------------------------
   8. Sistema
   ------------------------------------------------------------------------- */

function seccionSistema(consultarApi) {
  const servicios = panelDeRecurso({
    id: 'sistema',
    ancho: 'completo',
    titulo: 'Estado de los servicios',
    acciones: [enlaceA('tablero-tecnico', 'Ver el tablero técnico')].filter(Boolean),
    recurso: '/admin/sistema/servicios',
    consultarApi,
    pintar: (datos) => pintarServicios(datos),
  });

  const parametros = panelDeRecurso({
    id: 'parametros',
    ancho: 'completo',
    titulo: 'Parámetros de plataforma',
    acciones: [enlaceA('parametros-admin', 'Gestionar parámetros')].filter(Boolean),
    recurso: '/parametros',
    consultarApi,
    pintar: (datos) =>
      tablaDeDatos({
        titulo: 'Parámetros de plataforma',
        columnas: [
          {
            clave: 'nombre',
            titulo: 'Parámetro',
            principal: true,
            ordenable: true,
            valor: (p) => fichaDeParametro(p).nombre,
            ordenarPor: (p) => fichaDeParametro(p).nombre,
          },
          { clave: 'valor', titulo: 'Valor vigente', valor: (p) => valorLegible(p) },
          {
            clave: 'grupo',
            titulo: 'Área',
            ordenable: true,
            valor: (p) => fichaDeParametro(p).grupo,
            ordenarPor: (p) => fichaDeParametro(p).grupo,
          },
          {
            clave: 'actualizadoEn',
            titulo: 'Último cambio',
            tipo: 'fecha',
            ordenable: true,
            valor: (p) => fechaOVacio(p.actualizadoEn),
          },
        ],
        filas: filasDe(datos),
      }),
  });

  return [servicios, parametros];
}

/**
 * La salud de los servicios: el recuento y la tabla, con el nombre de lo que
 * hace cada servicio y lo que significa su estado.
 *
 * @param {*} datos `RespuestaDelSistema` de metricas-plataforma.yaml
 * @returns {HTMLElement[]}
 */
export function pintarServicios(datos) {
  const lista = Array.isArray(datos?.servicios) ? datos.servicios : [];
  const recuento = [
    [datos?.operativos, 'operativos'],
    [datos?.lentos, 'lentos'],
    [datos?.caidos, 'caídos'],
    [datos?.noDesplegados, 'fuera de este entorno'],
    [datos?.noObservables, 'en otro servidor'],
  ]
    .filter(([cuantos], indice) => indice === 0 || Number(cuantos) > 0)
    .map(([cuantos, que]) => `${Number.isFinite(cuantos) ? numero(cuantos) : SIN_DATO} ${que}`);
  const total = Number.isFinite(datos?.total) ? datos.total : lista.length;

  const ordenEstado = { CAIDO: 0, LENTO: 1, OPERATIVO: 2, NO_OBSERVABLE: 3, NO_DESPLEGADO: 4 };
  return [
    tablaDeDatos({
      titulo: 'Estado de los servicios',
      resumen: `${recuento.join(' · ')} — de ${numero(total)} servicios`,
      columnas: [
        {
          clave: 'servicio',
          titulo: 'Servicio',
          principal: true,
          ordenable: true,
          valor: (s) => nombreDelServicio(s.servicio),
          ordenarPor: (s) => nombreDelServicio(s.servicio),
        },
        {
          clave: 'estado',
          titulo: 'Estado',
          tipo: 'estado',
          ordenable: true,
          valor: (s) => estadoSegun(s.estado, ESTADOS_DE_SERVICIO),
          ordenarPor: (s) => ordenEstado[s.estado] ?? 9,
        },
        {
          clave: 'situacion',
          titulo: 'Situación',
          valor: (s) => SITUACION_DEL_SERVICIO[s.estado] ?? null,
        },
      ],
      // Lo que necesita atención, primero.
      filas: [...lista].sort((a, b) => (ordenEstado[a.estado] ?? 9) - (ordenEstado[b.estado] ?? 9)),
      orden: { clave: 'estado', direccion: 'asc' },
    }),
  ];
}

/* -------------------------------------------------------------------------
   Formato
   ------------------------------------------------------------------------- */

/** Fecha legible, o vacío (la tabla dice «Sin dato»): nunca la de hoy. */
function fechaOVacio(valor) {
  return valor ? formatearFecha(valor) : null;
}

const DIA_COMPACTO = new Intl.DateTimeFormat('es-CO', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});

const HORA_COMPACTA = new Intl.DateTimeFormat('es-CO', { hour: 'numeric', minute: '2-digit' });

/**
 * El día de una fecha, compacto: «05 oct 2026». `es-CO` lo escribe «05 de oct
 * de 2026»; en una tabla de la consola esos «de» son un tercio del ancho de la
 * columna, y a 1366 px (la resolución mínima, RNF-USA-001) el directorio no
 * cabía entero (SA-3, al ganar la columna «Sanciones»).
 *
 * @param {Date} fecha válida
 */
function diaCompacto(fecha) {
  return DIA_COMPACTO.formatToParts(fecha)
    .filter((parte) => parte.type !== 'literal')
    .map((parte) => (parte.type === 'month' ? parte.value.replace('.', '') : parte.value))
    .join(' ');
}

/** Fecha y hora de un instante, compactas: «05 oct 2026, 6:30 p. m.». Nulo es un guion. */
export function formatearFecha(valor) {
  if (!valor) {
    return '—';
  }
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) {
    return String(valor);
  }
  return `${diaCompacto(fecha)}, ${HORA_COMPACTA.format(fecha)}`;
}

/** El día de un instante, sin la hora: «05 oct 2026». Nulo se pinta como guion. */
export function formatearDia(valor) {
  if (!valor) {
    return '—';
  }
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) {
    return String(valor);
  }
  return diaCompacto(fecha);
}

/** Hora de un instante, sin fecha. */
export function formatearInstante(valor) {
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) {
    return String(valor);
  }
  return fecha.toLocaleTimeString('es-CO', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}
