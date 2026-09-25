/**
 * Los tres armazones de Nexus Battles VI — UX-R3.0.
 *
 * RF-INV-008 (barra de navegación principal), RF-RBAC-002 (matriz de
 * permisos), RNF-USA-002 y RNF-USA-003.
 *
 * ## El problema que resuelve
 *
 * Había **una** cabecera para todo el producto. Eso significaba que la
 * pantalla de inicio de sesión —la primera que ve alguien que todavía no
 * tiene cuenta— mostraba arriba «Jugar online · Misiones · Torneo · Mi
 * inventario · Subasta · Mi Cuenta», seis destinos de los que cuatro le
 * devolvían al login del que no había salido. Y significaba también que un
 * administrador veía exactamente la navegación de un jugador con cuatro
 * opciones escondidas dentro de un menú desplegable.
 *
 * RF-INV-008 no pide eso. Su proceso principal dice, literalmente: «el sistema
 * renderiza la barra superior con los seis accesos y el buscador, **y adapta
 * las opciones visibles al estado de autenticación y al rol del usuario**», y
 * su flujo alternativo fija qué ve quien no ha entrado: «usuario no
 * autenticado, para el que "Mi Cuenta" muestra únicamente la opción de
 * registro». La barra de los seis destinos es la barra de la **aplicación**;
 * el portal de entrada es otra cosa, y el requisito ya lo contemplaba.
 *
 * ## Los tres
 *
 * | Armazón   | Quién lo ve            | Qué comunica                        |
 * |-----------|------------------------|-------------------------------------|
 * | `publico` | visitante sin sesión   | esto es un juego; entra o regístrate|
 * | `jugador` | cuenta con sesión      | tu héroe, tus créditos, a jugar     |
 * | `admin`   | rol de trastienda      | estás operando el Nexo              |
 *
 * Los tres salen del **mismo** sistema de diseño: mismos tokens, misma
 * tipografía, mismos botones, mismo foco. No son tres productos; son tres
 * densidades del mismo producto. El armazón de consola es más compacto y más
 * informativo, y lo dice con un distintivo y con el cromo, no con otra paleta.
 *
 * ## Qué NO hace
 *
 * No autoriza. Decidir qué destinos pinta es usabilidad; la autorización real
 * la hace cada microservicio (RF-RBAC-004). Ver `acceso.js`.
 *
 * @module comun/shell
 */

import {
  ACCESO,
  armazonDeVista,
  destinoVisible,
  esRolDeTrastienda,
  puedeVer,
  urlDeVista,
  VEREDICTO,
} from './acceso.js';
import { cerrarSesion, leerSesion, resolver, RUTAS } from './sesion.js';
import { h } from './ui/dom.js';
import { vigilarSesion } from './vigilante-sesion.js';

const BASE_RUTAS = import.meta.url;

/**
 * Los seis destinos que fija RF-INV-008, con sus etiquetas **literales**.
 *
 * No se renombran. Se estudió pasarlos a «Inicio · Jugar · Colección ·
 * Torneos · Mercado · Cuenta», que se lee mejor, y se descartó: el requisito
 * nombra los seis menús entre comillas y está en estado «Confirmado». Un
 * cambio de etiqueta aquí es un cambio de requisito, y eso lo decide el PO,
 * no el rediseño. Lo que sí cambia es **dónde** aparecen (solo dentro de la
 * aplicación) y **cómo** se agrupan.
 *
 * `vista` enlaza con `MATRIZ` para saber si el destino es visible para quien
 * mira.
 *
 * UXC-5 — «Misiones» era el único destino sin pantalla: se pintaba
 * deshabilitado con un «llegará en una próxima actualización». Ahora lleva a
 * su vista, que cuenta la verdad desde dentro —si hay misiones o no, y qué se
 * puede hacer ya (preparar la estrategia del héroe)—, en vez de un aviso mudo
 * en la barra.
 */
export const SECCIONES = Object.freeze([
  { id: 'jugar', etiqueta: 'Jugar online', vista: 'batallas', icono: 'espadas' },
  { id: 'misiones', etiqueta: 'Misiones', vista: 'misiones', icono: 'mapa' },
  { id: 'torneo', etiqueta: 'Torneo', vista: 'torneos', icono: 'trofeo' },
  { id: 'inventario', etiqueta: 'Mi inventario', vista: 'inventario', icono: 'mochila' },
  { id: 'subasta', etiqueta: 'Subasta', vista: 'subastas', icono: 'martillo' },
  { id: 'cuenta', etiqueta: 'Mi Cuenta', vista: 'perfil', icono: 'usuario' },
]);

/**
 * La consola. Solo herramientas que existen de verdad (§10): cada entrada
 * apunta a una vista de `MATRIZ`, y `MATRIZ` se comprueba contra el disco.
 * Un moderador no ve «Parámetros» porque `puedeVer` dice que no, no porque
 * aquí haya una lista aparte.
 */
export const SECCIONES_CONSOLA = Object.freeze([
  { id: 'resumen', etiqueta: 'Resumen', vista: 'consola', icono: 'panel' },
  { id: 'control', etiqueta: 'Control integral', vista: 'control-integral', icono: 'pulso' },
  { id: 'usuarios', etiqueta: 'Usuarios', vista: 'gestion-usuarios', icono: 'usuarios' },
  { id: 'productos', etiqueta: 'Productos', vista: 'productos', icono: 'mochila' },
  { id: 'sanciones', etiqueta: 'Sanciones', vista: 'sanciones-admin', icono: 'escudo' },
  { id: 'lista-negra', etiqueta: 'Lista negra', vista: 'lista-negra-admin', icono: 'prohibido' },
  { id: 'parametros', etiqueta: 'Parámetros', vista: 'parametros-admin', icono: 'ajustes' },
  { id: 'metricas', etiqueta: 'Métricas', vista: 'panel-metricas', icono: 'grafico' },
  { id: 'tecnico', etiqueta: 'Técnico', vista: 'tablero-tecnico', icono: 'pulso' },
  { id: 'auditoria', etiqueta: 'Auditoría', vista: 'auditoria', icono: 'lista' },
]);

/** Nombre legible de un rol, para el distintivo. RF-RBAC-001 fija los cuatro. */
export const NOMBRE_DE_ROL = Object.freeze({
  JUGADOR: 'Jugador',
  MODERADOR: 'Moderador',
  ADMINISTRADOR: 'Administrador',
  SUPER_ADMINISTRADOR: 'Super administrador',
});

/* -------------------------------------------------------------------------
   Piezas compartidas por los tres armazones
   ------------------------------------------------------------------------- */

function icono(nombre, base, clase = 'icono') {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', clase);
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  use.setAttribute(
    'href',
    `${resolver('../../../../shared/ui-kit/iconos/sprite.svg', base)}#${nombre}`,
  );
  svg.appendChild(use);
  return svg;
}

function enlace(texto, href, clase) {
  const a = h('a', { clase, texto });
  a.href = href;
  return a;
}

/**
 * La marca, en dos longitudes. El nombre accesible NO cambia: `aria-label` lo
 * fija y la versión corta va `aria-hidden`, así que un lector de pantalla
 * siempre oye el nombre entero aunque en pantalla quepa «NB VI».
 *
 * @param {{destino: string, sufijo?: string|null}} opciones
 */
function marca({ destino, sufijo = null, base = import.meta.url }) {
  const a = h('a', {
    clase: 'cabecera__marca',
    atributos: { 'aria-label': 'Nexus Battles VI — inicio' },
  });
  a.href = destino;
  // UX-GAME-2 — el emblema del logotipo (el cristal que lo corona) acompaña
  // al nombre en todas las barras. Es decorativo: `alt` vacío, el nombre
  // accesible sigue siendo el `aria-label` del enlace. El logotipo completo
  // solo va en el portal de entrada (login); aquí la versión compacta.
  const emblema = h('img', {
    clase: 'cabecera__emblema',
    atributos: { alt: '', 'aria-hidden': 'true', width: '32', height: '30', decoding: 'async' },
  });
  emblema.src = resolver('../../../../shared/ui-kit/marca/emblema.webp', base);
  a.append(
    emblema,
    h('span', { clase: 'cabecera__marca-larga', texto: 'NEXUS BATTLES VI' }),
    h('span', {
      clase: 'cabecera__marca-corta',
      texto: 'NB VI',
      atributos: { 'aria-hidden': 'true' },
    }),
  );
  if (sufijo) {
    // El sufijo entra en el nombre accesible del enlace, no solo en pantalla:
    // «Nexus Battles VI — Control» es donde estás, y un lector de pantalla
    // tiene que oírlo igual que se ve.
    a.setAttribute('aria-label', `Nexus Battles VI ${sufijo} — inicio`);
    a.append(h('span', { clase: 'cabecera__marca-sufijo', texto: sufijo }));
  }
  return a;
}

/** Distintivo de rol. Solo aparece cuando el rol NO es jugador (§5). */
function distintivoDeRol(rol) {
  if (!rol || rol === 'JUGADOR') {
    return null;
  }
  return h('span', {
    clase: `distintivo-rol distintivo-rol--${rol.toLowerCase().replaceAll('_', '-')}`,
    texto: NOMBRE_DE_ROL[rol] ?? rol,
    datos: { zona: 'rol' },
  });
}

/**
 * Menú de cuenta, con las opciones que correspondan al rol.
 *
 * El corte por rol sale de `MATRIZ` vía `destinoVisible`: si una pantalla
 * está denegada para este rol, su opción no se pinta. Antes había aquí una
 * lista de roles escrita a mano que ya no coincidía con lo que el servidor
 * permitía.
 */
function menuDeCuenta({ sesion, base, almacen, navegar, opcionesExtra = [] }) {
  const cuenta = h('div', { clase: 'cabecera__cuenta' });
  const boton = h('button', {
    clase: 'cabecera__cuenta-boton',
    atributos: {
      type: 'button',
      'aria-haspopup': 'menu',
      'aria-expanded': 'false',
      // UX-R3.11 — el nombre accesible NO puede depender del CSS.
      //
      // Lo unico con texto dentro de este boton es `.cabecera__identidad`, y a
      // 375 px la cabecera la oculta (`display: none`) porque no cabe. El
      // avatar y el chevron son `aria-hidden`: decoracion. Resultado, medido
      // con axe en las 32 vistas: a 375 px el boton se queda SIN nombre
      // accesible -`button-name`, impacto critico- y quien usa lector de
      // pantalla en el movil oye «boton» y nada mas. Era la unica incidencia
      // critica del producto, y estaba en las 32 pantallas privadas a la vez.
      //
      // El `aria-label` lleva el apodo dentro, asi que sigue cumpliendo
      // «Label in Name» (WCAG 2.5.3) cuando el apodo si se ve.
      'aria-label': `Cuenta de ${sesion.apodo}`,
    },
    datos: { zona: 'cuenta' },
  });
  const avatar = h('span', {
    clase: 'cabecera__avatar',
    texto: (sesion.apodo || '?').slice(0, 1).toUpperCase(),
    atributos: { 'aria-hidden': 'true' },
  });
  const identidad = h('span', { clase: 'cabecera__identidad' });
  identidad.append(
    h('span', { clase: 'cabecera__apodo', texto: sesion.apodo, datos: { zona: 'apodo' } }),
  );
  const rango = distintivoDeRol(sesion.rol);
  if (rango) {
    identidad.append(rango);
  }
  boton.append(avatar, identidad, icono('chevron', base, 'icono icono--menudo'));

  const menu = h('div', {
    clase: 'menu cabecera__menu',
    atributos: { role: 'menu' },
    datos: { zona: 'menu-cuenta' },
  });
  menu.hidden = true;
  const grupo = h('div', { clase: 'menu__grupo' });

  const opciones = [
    ['Mi perfil', RUTAS.perfil, 'perfil'],
    ['Historial de transacciones', RUTAS.historial, 'historial-transacciones'],
    ['Mis cofres', RUTAS.cofres, 'mis-cofres'],
    ['Tienda', RUTAS.tienda, 'tienda'],
    ['Mis sanciones', RUTAS.misSanciones, 'mis-sanciones'],
    ...opcionesExtra,
  ];
  for (const [texto, ruta, vista] of opciones) {
    if (vista && !destinoVisible(vista, sesion)) {
      continue;
    }
    const opcion = enlace(texto, resolver(ruta, base), 'menu__opcion');
    opcion.setAttribute('role', 'menuitem');
    grupo.append(opcion);
  }

  const salir = h('button', {
    clase: 'menu__opcion menu__opcion--peligro',
    texto: 'Cerrar sesión',
    atributos: { type: 'button', role: 'menuitem' },
    datos: { zona: 'cerrar-sesion' },
  });
  salir.addEventListener('click', () => cerrarSesion({ almacen, navegar, base }));
  grupo.append(salir);
  menu.append(grupo);

  boton.addEventListener('click', () => {
    const abierto = menu.hidden;
    menu.hidden = !abierto;
    boton.setAttribute('aria-expanded', String(abierto));
  });
  document.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape' && !menu.hidden) {
      menu.hidden = true;
      boton.setAttribute('aria-expanded', 'false');
      boton.focus();
    }
  });

  cuenta.append(boton, menu);
  return cuenta;
}

/** El disparador del menú plegable, y el cableado de su estado. */
function alternarNavegacion(cabecera, base) {
  const boton = h('button', {
    clase: 'cabecera__alternar',
    atributos: {
      type: 'button',
      'aria-expanded': 'false',
      'aria-controls': 'cabecera-nav',
      'aria-label': 'Abrir el menú de navegación',
    },
    datos: { zona: 'alternar-nav' },
  });
  boton.append(icono('mas', base, 'icono'));
  boton.addEventListener('click', () => {
    const abierto = cabecera.dataset.navAbierto !== 'si';
    cabecera.dataset.navAbierto = abierto ? 'si' : 'no';
    boton.setAttribute('aria-expanded', String(abierto));
    boton.setAttribute(
      'aria-label',
      abierto ? 'Cerrar el menú de navegación' : 'Abrir el menú de navegación',
    );
  });
  document.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape' && cabecera.dataset.navAbierto === 'si') {
      cabecera.dataset.navAbierto = 'no';
      boton.setAttribute('aria-expanded', 'false');
      boton.setAttribute('aria-label', 'Abrir el menú de navegación');
      boton.focus();
    }
  });
  cabecera.dataset.navAbierto = 'no';
  return boton;
}

/* -------------------------------------------------------------------------
   1. Armazón público — el portal de entrada
   ------------------------------------------------------------------------- */

/**
 * La barra del portal: marca y la salida que **no** es esta pantalla.
 *
 * No lleva navegación de la aplicación, ni campana, ni saldo, ni menú de
 * jugador: nada de eso existe todavía para quien está aquí. Es la lectura
 * literal del flujo alternativo de RF-INV-008 («usuario no autenticado, para
 * el que "Mi Cuenta" muestra únicamente la opción de registro»).
 *
 * La acción que se ofrece depende de dónde estés: en el login, «Crear cuenta»;
 * en el registro y en la recuperación, «Iniciar sesión». Ofrecer las dos
 * siempre significa que una de ellas lleva a la pantalla en la que ya estás.
 *
 * R17 — «Preparando tu cuenta» también es portal (todavía no hay juego que
 * navegar), pero quien la ve ya entró: lo que se le ofrece es salir.
 *
 * @param {HTMLElement} raiz
 * @param {{vista?: string, base?: string, almacen?: Storage, navegar?: (url: string) => void}} [opciones]
 */
export function montarArmazonPublico(
  raiz,
  {
    vista = 'login',
    base = BASE_RUTAS,
    almacen = globalThis.sessionStorage,
    navegar = (url) => {
      globalThis.location.href = url;
    },
  } = {},
) {
  const cabecera = h('header', { clase: 'cabecera cabecera--portal' });
  cabecera.dataset.cabeceraApp = '';
  cabecera.dataset.armazon = 'publico';

  const grupoMarca = h('div', { clase: 'cabecera__grupo-marca' });
  grupoMarca.append(marca({ destino: resolver(RUTAS.login, base), base }));
  cabecera.append(grupoMarca);

  const acciones = h('div', { clase: 'cabecera__acciones' });
  const zona = h('div', { clase: 'cabecera__sesion', datos: { zona: 'sesion' } });
  if (vista === 'preparando') {
    const salir = h('button', {
      clase: 'boton boton--secundario boton--pequeno',
      texto: 'Cerrar sesión',
      atributos: { type: 'button' },
      datos: { zona: 'cerrar-sesion' },
    });
    salir.addEventListener('click', () => cerrarSesion({ almacen, navegar, base }));
    zona.append(salir);
  } else if (vista === 'login') {
    zona.append(
      h('span', { clase: 'cabecera__invitacion', texto: '¿Primera vez en el Nexo?' }),
      enlace(
        'Crear cuenta',
        resolver(RUTAS.registro, base),
        'boton boton--primario boton--pequeno',
      ),
    );
  } else {
    zona.append(
      h('span', { clase: 'cabecera__invitacion', texto: '¿Ya tienes cuenta?' }),
      enlace(
        'Iniciar sesión',
        resolver(RUTAS.login, base),
        'boton boton--secundario boton--pequeno',
      ),
    );
  }
  acciones.append(zona);
  cabecera.append(acciones);

  raiz.replaceChildren(cabecera);
  return { elemento: cabecera, sesion: null };
}

/* -------------------------------------------------------------------------
   2. Armazón de jugador — la aplicación
   ------------------------------------------------------------------------- */

/**
 * La barra de RF-INV-008: los seis destinos, el buscador donde aplica y el
 * HUD del jugador (créditos, avisos, cuenta).
 *
 * @param {HTMLElement} raiz
 * @param {object} [opciones]
 */
export function montarArmazonJugador(
  raiz,
  {
    seccionActiva = null,
    buscador = null,
    sesion,
    base = BASE_RUTAS,
    almacen = globalThis.sessionStorage,
    navegar = (url) => {
      globalThis.location.href = url;
    },
  },
) {
  const cabecera = h('header', { clase: 'cabecera cabecera--jugador' });
  cabecera.dataset.cabeceraApp = '';
  cabecera.dataset.armazon = 'jugador';

  const grupoMarca = h('div', { clase: 'cabecera__grupo-marca' });
  grupoMarca.append(
    marca({ destino: resolver(sesion.autenticado ? RUTAS.inicio : RUTAS.login, base), base }),
    alternarNavegacion(cabecera, base),
  );

  const nav = h('nav', {
    clase: 'cabecera__nav',
    atributos: { id: 'cabecera-nav', 'aria-label': 'Navegación principal' },
  });

  for (const seccion of SECCIONES) {
    const destino = h('a', {
      clase: 'cabecera__destino',
      texto: seccion.etiqueta,
      datos: { seccion: seccion.id },
    });

    // RF-INV-008, Excepciones: «módulo destino no disponible, que debe
    // informarse al seleccionar el acceso correspondiente». Hasta UXC-5
    // Misiones se informaba aquí, deshabilitado; ahora lo informa su vista.
    const { veredicto } = puedeVer(seccion.vista, sesion);
    if (veredicto === VEREDICTO.DENEGADA) {
      continue;
    }
    destino.href = urlDeVista(seccion.vista, base);
    if (veredicto === VEREDICTO.REDIRIGE) {
      const login = new URL(resolver(RUTAS.login, base));
      login.searchParams.set('volver', new URL(destino.href).pathname);
      destino.href = login.href;
      destino.dataset.exigeSesion = '';
      destino.classList.add('cabecera__destino--con-sesion');
      destino.title = `${seccion.etiqueta}: hay que iniciar sesión`;
    }
    if (seccion.id === seccionActiva) {
      destino.classList.add('activo');
      destino.setAttribute('aria-current', 'page');
    }
    nav.append(destino);
  }
  grupoMarca.append(nav);
  cabecera.append(grupoMarca);

  if (buscador) {
    cabecera.append(construirBuscador(buscador, base));
  }

  const acciones = h('div', { clase: 'cabecera__acciones' });

  if (!sesion.autenticado) {
    // Una vitrina pública (productos, subastas, torneos) se ve sin cuenta,
    // pero desde ella se entra y se registra uno.
    const zona = h('div', { clase: 'cabecera__sesion', datos: { zona: 'sesion' } });
    if (sesion.caducada) {
      zona.append(
        h('span', {
          clase: 'cabecera__aviso-sesion',
          texto: 'Tu sesión terminó',
          atributos: { role: 'status' },
        }),
      );
    }
    const login = new URL(resolver(RUTAS.login, base));
    if (globalThis.location?.pathname) {
      login.searchParams.set(
        'volver',
        `${globalThis.location.pathname}${globalThis.location.search ?? ''}`,
      );
    }
    zona.append(
      enlace('Iniciar sesión', login.href, 'boton boton--secundario boton--pequeno'),
      enlace('Registrarse', resolver(RUTAS.registro, base), 'boton boton--primario boton--pequeno'),
    );
    acciones.append(zona);
  } else {
    acciones.append(
      indicadorDeCreditos(base),
      campana(base),
      menuDeCuenta({
        sesion,
        base,
        almacen,
        navegar,
        opcionesExtra: esRolDeTrastienda(sesion.rol)
          ? [['Consola de operación', RUTAS.consola, 'consola']]
          : [],
      }),
    );
  }

  cabecera.append(acciones);
  raiz.replaceChildren(cabecera);
  return { elemento: cabecera, sesion };
}

/** Créditos: oculto hasta que la vista sepa la cifra. Nunca se inventa. */
function indicadorDeCreditos(base) {
  const creditos = h('span', { clase: 'cabecera__dato', datos: { zona: 'creditos' } });
  creditos.hidden = true;
  creditos.append(
    icono('moneda', base, 'icono icono--menudo'),
    h('span', { datos: { zona: 'saldo' } }),
  );
  return creditos;
}

function campana(base) {
  const enlaceCampana = h('a', {
    clase: 'cabecera__campana',
    atributos: { 'aria-label': 'Notificaciones' },
  });
  enlaceCampana.href = resolver(RUTAS.notificaciones, base);
  enlaceCampana.append(icono('campana', base));
  const contador = h('span', {
    clase: 'cabecera__contador',
    texto: '0',
    atributos: { 'aria-hidden': 'true' },
    datos: { zona: 'contador' },
  });
  contador.hidden = true;
  enlaceCampana.append(contador);
  return enlaceCampana;
}

function construirBuscador(buscador, base) {
  const formulario = h('form', {
    clase: 'cabecera__buscador',
    atributos: { role: 'search' },
  });
  formulario.append(icono('buscar', base, 'icono icono--menudo'));
  const etiqueta = h('label', {
    clase: 'solo-lectores',
    texto: buscador.placeholder ?? 'Buscar',
  });
  etiqueta.htmlFor = 'cabecera-busqueda';
  const campo = h('input', {
    clase: 'cabecera__buscador-texto',
    atributos: {
      type: 'search',
      id: 'cabecera-busqueda',
      name: 'q',
      placeholder: buscador.placeholder ?? 'Buscar',
    },
  });
  formulario.append(etiqueta, campo);
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    buscador.alBuscar?.(campo.value.trim());
  });
  return formulario;
}

/* -------------------------------------------------------------------------
   3. Armazón de consola — la trastienda
   ------------------------------------------------------------------------- */

/**
 * La consola de operación. Mismo sistema de diseño, otra densidad.
 *
 * Tres diferencias deliberadas con el armazón de jugador, y ninguna más:
 *
 * 1. **Dice dónde estás.** La marca lleva el sufijo «Control» y el cromo va
 *    más oscuro. Quien opera tiene que saber en todo momento que lo que pulsa
 *    afecta a otras personas.
 * 2. **Enseña el rol.** Un moderador y un super administrador ven consolas
 *    distintas; el distintivo dice cuál de las dos estás viendo, para que
 *    «esa opción no me aparece» tenga respuesta sin abrir un ticket.
 * 3. **Tiene salida al juego.** Un administrador también es jugador. Sin este
 *    enlace, volver significaba escribir la URL a mano.
 *
 * Los destinos salen de `MATRIZ`: si `puedeVer` deniega uno, no se pinta.
 *
 * @param {HTMLElement} raiz
 * @param {object} opciones
 */
export function montarArmazonAdmin(
  raiz,
  {
    seccionActiva = null,
    sesion,
    base = BASE_RUTAS,
    almacen = globalThis.sessionStorage,
    navegar = (url) => {
      globalThis.location.href = url;
    },
  },
) {
  const cabecera = h('header', { clase: 'cabecera cabecera--consola' });
  cabecera.dataset.cabeceraApp = '';
  cabecera.dataset.armazon = 'admin';

  const grupoMarca = h('div', { clase: 'cabecera__grupo-marca' });
  grupoMarca.append(
    marca({ destino: resolver(RUTAS.consola, base), sufijo: 'Control', base }),
    alternarNavegacion(cabecera, base),
  );

  const nav = h('nav', {
    clase: 'cabecera__nav cabecera__nav--consola',
    atributos: { id: 'cabecera-nav', 'aria-label': 'Herramientas de operación' },
  });
  for (const seccion of SECCIONES_CONSOLA) {
    if (seccion.vista !== 'consola' && !destinoVisible(seccion.vista, sesion)) {
      continue;
    }
    const destino = h('a', {
      clase: 'cabecera__destino',
      texto: seccion.etiqueta,
      datos: { seccion: seccion.id },
    });
    destino.href = urlDeVista(seccion.vista, base);
    if (seccion.id === seccionActiva) {
      destino.classList.add('activo');
      destino.setAttribute('aria-current', 'page');
    }
    nav.append(destino);
  }
  grupoMarca.append(nav);
  cabecera.append(grupoMarca);

  const acciones = h('div', { clase: 'cabecera__acciones' });
  acciones.append(
    enlace(
      'Volver al juego',
      resolver(RUTAS.inicio, base),
      'boton boton--secundario boton--pequeno cabecera__salida',
    ),
    // UX-GAME-6 — la salida al juego tambien en el menu de cuenta: en la banda
    // de portatil (<=1440) el boton de la barra se pliega para que los diez
    // destinos del super administrador quepan en una fila, y la salida tiene
    // que seguir a un toque.
    menuDeCuenta({
      sesion,
      base,
      almacen,
      navegar,
      opcionesExtra: [['Volver al juego', RUTAS.inicio, null]],
    }),
  );
  cabecera.append(acciones);

  raiz.replaceChildren(cabecera);
  return { elemento: cabecera, sesion };
}

/* -------------------------------------------------------------------------
   Despachador
   ------------------------------------------------------------------------- */

/**
 * Monta el armazón que toca y devuelve la sesión leída.
 *
 * Quién decide cuál: primero lo que diga la vista (`armazon`), y si no lo
 * dice, `MATRIZ`. Una vista de trastienda abierta por alguien sin rol no
 * llega hasta aquí: `exigirAcceso` la ha parado antes.
 *
 * R17 — con sesión, además, deja puesto el vigilante (`vigilante-sesion.js`):
 * caducidad, rechazo del token, cierre en otra pestaña y la vuelta desde la
 * caché de páginas. Así ninguna vista tiene que acordarse de hacerlo.
 *
 * @param {HTMLElement} raiz contenedor; se recomienda `<div data-cabecera-app>`
 * @param {object} [opciones]
 * @param {string|null} [opciones.vista] clave de `MATRIZ` — de dónde sale todo
 * @param {'publico'|'jugador'|'admin'} [opciones.armazon] fuerza uno
 * @param {(opciones: object) => unknown} [opciones.vigilar] inyectable en pruebas
 * @returns {{elemento: HTMLElement, sesion: object|null}}
 */
export function montarArmazon(
  raiz,
  {
    vista = null,
    armazon = null,
    seccionActiva = null,
    buscador = null,
    almacen = globalThis.sessionStorage,
    base = BASE_RUTAS,
    ahora,
    navegar = (url) => {
      globalThis.location.href = url;
    },
    documento = globalThis.document,
    vigilar = vigilarSesion,
  } = {},
) {
  // Página interrumpida por una guarda (§17): no se monta nada encima.
  //
  // Una vista puede tener su guarda en un `<script type="module">` y montar su
  // cabecera desde OTRO módulo (`gestion-usuarios.js` lo hace). Lanzar una
  // excepción detiene el módulo de la guarda, no el de al lado: el segundo
  // seguía ejecutándose y pintaba la cabecera de consola encima de la
  // pantalla de «sin acceso». Sin contenedor tampoco se monta: eso es lo que
  // queda cuando esa pantalla ya sustituyó al `<body>`.
  if (documento?.documentElement?.dataset?.acceso === 'denegado' || !raiz) {
    return { elemento: null, sesion: null };
  }

  const sesion = leerSesion(almacen, ahora);
  const elegido = armazon ?? armazonDeVista(vista) ?? (sesion.autenticado ? 'jugador' : 'publico');

  if (sesion.autenticado) {
    vigilar({ almacen, ahora, documento });
  }

  if (elegido === 'publico') {
    return montarArmazonPublico(raiz, { vista: vista ?? 'login', base, almacen, navegar });
  }
  if (elegido === 'admin') {
    return montarArmazonAdmin(raiz, { seccionActiva, sesion, base, almacen, navegar });
  }
  return montarArmazonJugador(raiz, {
    seccionActiva,
    buscador,
    sesion,
    base,
    almacen,
    navegar,
  });
}

export { ACCESO };
