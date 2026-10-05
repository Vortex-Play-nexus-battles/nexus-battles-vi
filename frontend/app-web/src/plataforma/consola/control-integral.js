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

import { consultar, descargar, filasDe, totalDe, RESULTADO } from './cliente-consola.js';
import { indicador, moduloNoImplementado, panel, pintarDesenlace, tabla } from './panel.js';
import {
  ESTADOS_DE_CUENTA,
  exportarDirectorio,
  guardarArchivo,
  panelDeUsuarios,
  recursoDelDirectorio,
} from './panel-usuarios.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { NOMBRE_DE_ROL } from '../../comun/shell.js';
import { RUTAS, resolver } from '../../comun/sesion.js';
import { distintivo } from '../../comun/ui/distintivo.js';
import { nombreDeModalidad } from '../../comun/ui/juego/partida.js';

/** Tamano de pagina del directorio. Coincide con el techo comodo del backend. */
const FILAS_POR_PAGINA = 20;

/**
 * RFINAL-06 — lo que se pinta cuando el contrato de la fuente no trae el dato.
 * Un guion parecia un fallo de la pantalla y un identificador no lo lee nadie;
 * «sin dato» dice exactamente lo que pasa.
 */
export const SIN_DATO = 'sin dato';

/** @param {*} valor */
function oSinDato(valor) {
  return valor === null || valor === undefined || valor === '' ? SIN_DATO : valor;
}

/** Fecha legible, o «sin dato»: nunca la de hoy, nunca un guion. */
function fechaOSinDato(valor) {
  return valor ? formatearFecha(valor) : SIN_DATO;
}

/** «3 de 8», solo si el servidor dio las dos cifras. */
function deCuantos(parte, total) {
  return Number.isFinite(parte) && Number.isFinite(total) ? `${parte} de ${total}` : SIN_DATO;
}

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
 * @param {{consultarApi?: typeof consultar, descargarApi?: typeof descargar,
 *          guardar?: typeof guardarArchivo}} [dependencias] inyectables en pruebas
 */
export function montarControlIntegral(
  raiz,
  sesion,
  { consultarApi = consultar, descargarApi = descargar, guardar = guardarArchivo } = {},
) {
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
    resumen: seccionResumen(consultarApi, { guardar }),
    jugadores: seccionJugadores(consultarApi, { descargarApi, guardar }),
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

function seccionResumen(consultarApi, { guardar } = {}) {
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
    descripcion: 'Sondeo real de la salud de cada servicio del catálogo.',
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

  // HU-USR-008 (§7.3.4): cuentas por estado, registros por día, periodo y
  // exportación. Va el último porque ocupa el ancho entero.
  const usuarios = panelDeUsuarios(consultarApi, { guardar });

  return [jugadores, servicios, sanciones, torneos, usuarios];
}

/* -------------------------------------------------------------------------
   2. Jugadores
   ------------------------------------------------------------------------- */

function seccionJugadores(
  consultarApi,
  { descargarApi = descargar, guardar = guardarArchivo } = {},
) {
  // RFINAL-06 — las cuentas de las pruebas automáticas se ocultan por omisión
  // y las excluye el servidor antes de paginar (ms-identidad-admin.yaml 1.2.0,
  // `ocultarPruebas`). Qué cuenta es de pruebas lo decide su configuración,
  // no esta pantalla.
  //
  // HU-USR-008 (1.3.0) — rol, estado y fecha de registro. Son los filtros
  // APLICADOS: los de la página que se ve y los de la exportación.
  const estado = {
    pagina: 0,
    buscar: '',
    ocultarPruebas: true,
    rol: '',
    estadoCuenta: '',
    desde: '',
    hasta: '',
  };
  const partes = panel({
    id: 'directorio',
    titulo: 'Directorio de jugadores',
    descripcion:
      'Busca por apodo, correo o nombre y filtra por rol, estado y fecha de registro. El ' +
      'filtrado, la paginación y la exportación los hace el servidor, no el navegador.',
    fuente: 'GET /api/v1/admin/jugadores',
  });

  const buscador = h('form', { clase: 'consola-integral__buscador' });
  const campo = h('input', {
    clase: 'campo__entrada',
    atributos: {
      type: 'search',
      name: 'buscar',
      placeholder: 'Apodo, correo o nombre',
      'aria-label': 'Buscar jugador por apodo, correo o nombre',
    },
  });
  const boton = h('button', {
    clase: 'boton boton--primario',
    texto: 'Buscar',
    atributos: { type: 'submit' },
  });
  const filtros = filtrosDelDirectorio();
  buscador.append(campo, boton, filtros.fila);
  partes.elemento.insertBefore(buscador, partes.zona);

  const ocultarPruebas = h('input', {
    clase: 'casilla__entrada',
    atributos: { type: 'checkbox', name: 'ocultarPruebas' },
  });
  ocultarPruebas.checked = estado.ocultarPruebas;
  partes.elemento.insertBefore(
    h('label', {
      clase: 'casilla',
      hijos: [
        ocultarPruebas,
        h('span', {
          clase: 'casilla__etiqueta',
          texto: 'Ocultar cuentas de pruebas automáticas',
        }),
      ],
    }),
    partes.zona,
  );

  // HU-USR-008 — exportar el listado con los filtros vigentes. Lo arma el
  // servidor (una consulta, sin páginas que se corran mientras alguien se
  // registra); aquí se descarga y se dice cómo fue.
  const exportar = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Exportar listado (CSV)',
    atributos: { type: 'button' },
    datos: { accion: 'exportar-directorio' },
  });
  const aviso = h('p', {
    clase: 't-meta',
    datos: { zona: 'aviso-directorio' },
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  partes.elemento.insertBefore(
    h('div', { clase: 'fila fila--acciones', hijos: [exportar] }),
    partes.zona,
  );
  partes.elemento.insertBefore(aviso, partes.zona);

  const cargar = async () => {
    const desenlace = await consultarApi(recursoDelDirectorio(estado, FILAS_POR_PAGINA));
    pintarDesenlace(partes, desenlace, (datos) => pintarDirectorio(datos, estado, cargar), {
      alReintentar: cargar,
    });
    return desenlace;
  };

  /** Aplica lo que dice el formulario y vuelve a la primera página. */
  const aplicar = () => {
    const valores = filtros.leer();
    if (valores.desde && valores.hasta && valores.desde > valores.hasta) {
      // El servidor lo rechazaría igual (400); aquí se explica sin preguntar.
      filtros.marcarRangoInvalido(true);
      aviso.textContent = 'La fecha «Registrada desde» es posterior a «hasta»: corrige el periodo.';
      return;
    }
    filtros.marcarRangoInvalido(false);
    aviso.textContent = '';
    Object.assign(estado, valores, { buscar: campo.value.trim(), pagina: 0 });
    cargar();
  };

  buscador.addEventListener('submit', (evento) => {
    evento.preventDefault();
    aplicar();
  });
  for (const control of filtros.controles) {
    control.addEventListener('change', aplicar);
  }

  ocultarPruebas.addEventListener('change', () => {
    estado.ocultarPruebas = ocultarPruebas.checked;
    estado.pagina = 0;
    cargar();
  });

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

  return [{ ...partes, cargar, id: 'directorio' }];
}

/**
 * HU-USR-008 — rol, estado y fecha de registro del directorio, cada uno con su
 * etiqueta visible. «Todos» es no filtrar.
 *
 * @returns {{fila: HTMLElement, controles: HTMLElement[],
 *   leer: () => {rol: string, estadoCuenta: string, desde: string, hasta: string},
 *   marcarRangoInvalido: (invalido: boolean) => void}}
 */
function filtrosDelDirectorio() {
  const desplegable = (nombre, etiqueta, opciones) => {
    const control = h('select', {
      clase: 'desplegable__control',
      atributos: { name: nombre },
      hijos: [
        h('option', { texto: 'Todos', atributos: { value: '' } }),
        ...opciones.map(([valor, texto]) => h('option', { texto, atributos: { value: valor } })),
      ],
    });
    const caja = h('label', {
      clase: 'desplegable',
      hijos: [h('span', { clase: 'campo__etiqueta', texto: etiqueta }), control],
    });
    return { caja, control };
  };
  const fecha = (nombre, etiqueta) => {
    const control = h('input', {
      clase: 'campo__control',
      atributos: { type: 'date', name: nombre },
    });
    const caja = h('label', {
      clase: 'campo',
      hijos: [h('span', { clase: 'campo__etiqueta', texto: etiqueta }), control],
    });
    return { caja, control };
  };

  const rol = desplegable('rol', 'Rol', Object.entries(NOMBRE_DE_ROL));
  const estadoCuenta = desplegable(
    'estado',
    'Estado',
    ESTADOS_DE_CUENTA.map(({ estado, nombre }) => [estado, nombre]),
  );
  const desde = fecha('registradoDesde', 'Registrada desde');
  const hasta = fecha('registradoHasta', 'Registrada hasta');

  return {
    fila: h('div', {
      clase: 'consola-integral__filtros',
      hijos: [rol.caja, estadoCuenta.caja, desde.caja, hasta.caja],
    }),
    controles: [rol.control, estadoCuenta.control, desde.control, hasta.control],
    leer: () => ({
      rol: rol.control.value,
      estadoCuenta: estadoCuenta.control.value,
      desde: desde.control.value,
      hasta: hasta.control.value,
    }),
    marcarRangoInvalido: (invalido) => {
      if (invalido) {
        desde.control.setAttribute('aria-invalid', 'true');
      } else {
        desde.control.removeAttribute('aria-invalid');
      }
    },
  };
}

export function pintarDirectorio(datos, estado, recargar) {
  const filas = filasDe(datos).map((jugador) => [
    jugador.apodo ?? '--',
    jugador.email ?? '--',
    jugador.rol ?? '--',
    jugador.bloqueada ? 'BLOQUEADA' : (jugador.estado ?? '--'),
    formatearFecha(jugador.creadoEn),
    jugador.ultimoAcceso ? formatearFecha(jugador.ultimoAcceso) : 'Nunca ha entrado',
    enlaceAGestion(jugador),
    enlaceASanciones(jugador),
  ]);

  const cuantas = estado?.ocultarPruebas
    ? 'cuentas sin contar las de pruebas automáticas'
    : 'cuentas en total';
  const cuerpo = tabla({
    columnas: [
      'Apodo',
      'Correo',
      'Rol',
      'Estado',
      'Registro',
      'Última entrada',
      'Gestión',
      'Sanciones',
    ],
    filas,
    resumen: `Página ${(datos?.pagina ?? 0) + 1} de ${datos?.totalPaginas ?? 1}, ${
      datos?.total ?? filas.length
    } ${cuantas}`,
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
/**
 * UXC-9 — §7.3.4: de la ficha del directorio al historial de sanciones de
 * esa cuenta, sin copiar su identificador. El panel de sanciones hace su
 * propia guarda de rol (Tabla 24).
 *
 * @param {{uid?: string, apodo?: string}} jugador
 * @returns {HTMLAnchorElement|string}
 */
function enlaceASanciones(jugador) {
  if (!jugador?.uid) {
    return '--';
  }
  const destino = new URL(resolver(RUTAS.sanciones));
  destino.searchParams.set('usuario', jugador.uid);
  return h('a', {
    texto: 'Ver sanciones',
    atributos: {
      href: destino.href,
      'aria-label': `Ver las sanciones de ${jugador.apodo ?? 'esta cuenta'}`,
    },
    datos: { accion: 'ver-sanciones' },
  });
}

/**
 * RFINAL-06 — de la fila a la ficha de gestión de esa cuenta, sin copiar su
 * identificador: la gestión espera la clave interna de la cuenta, que el
 * directorio publica desde ms-identidad-admin.yaml 1.2.0 (`id`). Sin ella
 * (un servicio anterior) se dice, en vez de pintar un enlace que no abre nada.
 *
 * @param {{id?: number, apodo?: string}} jugador
 * @returns {HTMLAnchorElement|string}
 */
function enlaceAGestion(jugador) {
  const clave = Number(jugador?.id);
  if (!Number.isInteger(clave) || clave <= 0) {
    return SIN_DATO;
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
    titulo: 'Salas y partidas',
    descripcion: 'Partidas y salas abiertas en este momento.',
    recurso: '/salas',
    consultarApi,
    // `Sala` de salas-partidas.yaml: no tiene nombre («las salas no tienen
    // nombre») y del anfitrion solo trae `idAnfitrion`, un UUID; su apodo es
    // de identidad y no se replica alli. La sala se describe por su modalidad
    // y el anfitrion queda «sin dato» en vez de un identificador ilegible.
    pintar: (datos) =>
      tabla({
        columnas: ['Sala', 'Estado', 'Jugadores', 'Creada', 'Anfitrión'],
        filas: filasDe(datos).map((sala) => [
          descripcionDeSala(sala),
          oSinDato(sala.estado),
          deCuantos(sala.ocupacion, sala.maximoParticipantes),
          fechaOSinDato(sala.creadaEn),
          SIN_DATO,
        ]),
      }),
  });

  return [salas, panelDeMisiones()];
}

/** «1 contra 1 · privada»: lo que identifica una sala que no tiene nombre. */
function descripcionDeSala(sala) {
  const modalidad = nombreDeModalidad(sala?.modalidad, SIN_DATO);
  return sala?.privada ? `${modalidad} · privada` : modalidad;
}

/**
 * Misiones: el servicio existe, pero no publica nada para operar.
 *
 * UXC-9 — este panel decía que ningún servicio publicaba misiones, y desde B9
 * ya no es verdad: `misiones.yaml` publica el tablón, las misiones en curso y
 * el historial, pero todo es de cada jugador (sale de su token). No hay un
 * agregado de progreso para la consola, y la alternativa -- inventar cifras
 * para que el panel se vea lleno -- la convertiría en una demo.
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
          'El servicio de misiones publica lo de cada jugador (su tablón, sus misiones en curso ' +
          'y su historial), pero no un progreso de todos para operación. No hay nada que ' +
          'consultar aquí, y esta consola no fabrica datos para llenar un hueco.',
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
      // `SubastaResumen` de ms-subastas-listado.yaml: el producto viene en
      // `nombreProducto`, la puja mas alta en `ofertaVigente` y el cierre en
      // `fechaFin`. El `id` es un UUID: no se pinta.
      pintar: (datos) => {
        const filas = filasDe(datos)
          .slice(0, 25)
          .map((s) => [
            oSinDato(s.nombreProducto),
            oSinDato(s.ofertaVigente),
            oSinDato(s.cantidadPujas),
            fechaOSinDato(s.fechaFin),
          ]);
        const total = datos?.totalElementos;
        return tabla({
          columnas: ['Elemento', 'Precio actual', 'Pujas', 'Cierra'],
          filas,
          resumen: Number.isFinite(total) ? `${filas.length} de ${total} subastas activas` : '',
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
      titulo: 'Torneos',
      descripcion: 'Convocatorias que publica el servicio de torneos.',
      recurso: '/torneos',
      consultarApi,
      // `TorneoResumen` de torneos.yaml: equipos en `equiposInscritos` de
      // `cupos`. El listado no publica fecha de inicio (el inicio real lo da
      // un administrador y solo sale en la ficha, `iniciadoEn`); lo que si
      // trae es el cierre de inscripciones.
      pintar: (datos) =>
        tabla({
          columnas: ['Torneo', 'Estado', 'Equipos', 'Inscripciones hasta'],
          filas: filasDe(datos).map((t) => [
            oSinDato(t.nombre),
            oSinDato(t.estado),
            deCuantos(t.equiposInscritos, t.cupos),
            fechaOSinDato(t.inscripcionesCierranEn),
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
    //
    // RFINAL-06: sin `sort` el orden lo decidia la base de datos y «los
    // ultimos quince» eran los primeros que se registraron. Fecha descendente
    // y el `id` como desempate (ms-cumplimiento-auditoria.yaml, `sort`).
    recurso: '/admin/auditoria?page=0&size=15&sort=fechaHora,desc&sort=id,desc',
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

  // UXC-9 — §7.3.4 «alertas»: las de alta frecuencia de sanciones que evalúa
  // el servicio de métricas contra su umbral (`TableroDeModeracion`). Es el
  // mismo dato que el tablero técnico; aquí, a la vista de quien opera sin
  // cambiar de pantalla. Sin umbral configurado no hay alerta que evaluar, y
  // se dice así en vez de pintar «sin alertas».
  const alertas = panelDeRecurso({
    id: 'alertas-moderacion',
    titulo: 'Alertas de moderación',
    descripcion: 'Días en que las sanciones superan el umbral configurado.',
    recurso: '/moderacion',
    consultarApi,
    pintar: (datos) => pintarAlertasDeModeracion(datos),
  });

  return [alertas, auditoria, sanciones, comentarios];
}

/**
 * Las alertas de moderación tal como las publica el servicio de métricas.
 *
 * @param {{alertasConfiguradas?: boolean, umbralSancionesPorDia?: number|null,
 *          alertas?: string[], pendientes?: string[]}} datos
 * @returns {HTMLElement[]}
 */
export function pintarAlertasDeModeracion(datos) {
  const lista = (Array.isArray(datos?.alertas) ? datos.alertas : []).filter(
    (alerta) => typeof alerta === 'string' && alerta.trim(),
  );
  const partes = [];
  if (!datos?.alertasConfiguradas) {
    partes.push(
      h('p', {
        clase: 't-meta',
        datos: { zona: 'sin-umbral' },
        texto:
          'Sin umbral configurado: se publican los conteos, pero no se evalúa ninguna alerta. El umbral de sanciones por día se fija en Parámetros.',
      }),
    );
  } else if (lista.length === 0) {
    const umbral = Number.isInteger(datos?.umbralSancionesPorDia)
      ? ` de ${datos.umbralSancionesPorDia} sanciones`
      : '';
    partes.push(
      h('p', {
        clase: 't-meta',
        datos: { zona: 'sin-alertas' },
        texto: `Sin alertas: ningún día supera el umbral${umbral}.`,
      }),
    );
  } else {
    partes.push(
      h('ul', {
        clase: 'pila pila--ajustada',
        datos: { zona: 'alertas' },
        hijos: lista.map((alerta) =>
          h('li', { hijos: [distintivo('Alerta', 'reportado'), ` ${alerta}`] }),
        ),
      }),
    );
  }
  const pendientes = (Array.isArray(datos?.pendientes) ? datos.pendientes : []).filter(
    (p) => typeof p === 'string' && p.trim(),
  );
  if (pendientes.length > 0) {
    partes.push(
      h('ul', {
        clase: 'pila pila--ajustada',
        datos: { zona: 'pendientes' },
        hijos: pendientes.map((p) => h('li', { clase: 't-meta', texto: `Pendiente: ${p}` })),
      }),
    );
  }
  return partes;
}

/* -------------------------------------------------------------------------
   8. Sistema
   ------------------------------------------------------------------------- */

function seccionSistema(consultarApi) {
  const servicios = panelDeRecurso({
    id: 'sistema',
    titulo: 'Estado de los servicios',
    descripcion:
      'Sondeo real de la salud de cada servicio. Caído, lento, fuera del host y no observable ' +
      'son cuatro cosas distintas.',
    recurso: '/admin/sistema/servicios',
    consultarApi,
    pintar: (datos) => {
      // `servicio` y no `nombre`: es como se llama el campo en la respuesta.
      const filas = (datos?.servicios ?? []).map((s) => [
        s.servicio ?? '--',
        selloDeServicio(s.estado),
        s.detalle ?? '--',
      ]);
      const partes = [`${datos?.operativos ?? 0} operativos`, `${datos?.caidos ?? 0} caídos`];
      // RFINAL-08: conectó y no contestó a tiempo. Solo se nombra si hay alguno.
      if (datos?.lentos) {
        partes.push(`${datos.lentos} lentos`);
      }
      partes.push(`${datos?.noDesplegados ?? 0} fuera del host`);
      if (datos?.noObservables) {
        partes.push(`${datos.noObservables} en otro host`);
      }
      const resumen = h('p', {
        clase: 't-meta',
        texto: `${partes.join(', ')}, de ${datos?.total ?? filas.length} catalogados.`,
      });
      const piezas = [resumen];
      // La ronda se reutiliza unos segundos (desdeCache): la hora dice de cuándo es.
      if (datos?.instante) {
        piezas.push(
          h('p', {
            clase: 't-meta',
            datos: { zona: 'medido' },
            texto: `Medido a las ${formatearInstante(datos.instante)}.`,
          }),
        );
      }
      piezas.push(tabla({ columnas: ['Servicio', 'Estado', 'Detalle'], filas }));
      return piezas;
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
    {
      OPERATIVO: 'ok',
      CAIDO: 'malo',
      // Conectó y no contestó a tiempo: ni verde ni rojo (RFINAL-08).
      LENTO: 'aviso',
      NO_DESPLEGADO: 'neutro',
      // Un servicio de otro host no va en rojo: no esta roto, esta fuera del
      // alcance de la sonda.
      NO_OBSERVABLE: 'neutro',
    }[String(estado)] ?? 'neutro';
  return h('span', {
    clase: 'sello-estado',
    texto: String(estado ?? '--').replaceAll('_', ' '),
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
