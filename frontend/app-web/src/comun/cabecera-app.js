/**
 * Cabecera única de la aplicación — HU-UX-001 (RNF-USA-002, RNF-USA-003),
 * sobre la barra de HU-INV-004 (#88) y la sesión de HU-AUT-004 (#50).
 *
 * ## Por qué existe
 *
 * Hasta el 21-sep-2026 había dos cabeceras: `construirBarra` (`.barra`, con su
 * propio CSS) en las vistas de cuentas, inventario y subastas, y un `<header
 * class="cabecera">` copiado a mano en cada vista de plataforma. Tenían orden
 * distinto, estado activo distinto, buscador en sitios distintos y, sobre
 * todo, **sesión distinta**: cuatro vistas montaban la barra sin pasarle la
 * sesión y ofrecían «Registrarse» a quien ya había iniciado sesión.
 *
 * Aquí vive lo que la guía de la empresa (§10.2) manda subir a `src/comun/`:
 * marca, navegación con estado activo, sesión (sin sesión: iniciar sesión y
 * registrarse; con sesión: avatar, apodo, notificaciones, Mi Cuenta y cerrar
 * sesión), rutas públicas frente a privadas, y buscador solo donde aplica.
 * Cada vista lo monta con una línea y no vuelve a escribir su propia barra.
 *
 * El marcado es el de `.cabecera` del ui-kit compartido (derivado del Figma:
 * `shared/ui-kit/css/componentes.css`), no uno nuevo.
 */

import { cuerpoDelToken } from './identidad.js';

/** Claves que deja `login.js` en `sessionStorage`. Un solo sitio las nombra. */
export const CLAVES = Object.freeze({
  token: 'nexus.token',
  apodo: 'nexus.apodoActual',
  rol: 'nexus.rolActual',
  usuarioId: 'nexus.usuarioId',
});

/**
 * Las seis secciones del alcance (RF-INV-004, §7.1 del documento fuente), en
 * su orden. `pendiente` marca las que todavía no tienen vista: se muestran
 * —el requisito exige las seis— pero no fingen llevar a ninguna parte.
 *
 * Destinos relativos a ESTE módulo (`src/comun/`), resueltos contra
 * `import.meta.url`, para acertar tanto servidos por el borde
 * (`/frontend/app-web/src/`) como por `npm run dev` (`src/`).
 */
export const SECCIONES = Object.freeze([
  { id: 'jugar', etiqueta: 'Jugar online', destino: '../plataforma/salas-partidas/batallas.html', privada: true },
  { id: 'misiones', etiqueta: 'Misiones', pendiente: 'HU-MIS (grupo-2)' },
  { id: 'torneo', etiqueta: 'Torneo', pendiente: 'HU-TOR-008 (#493, Sprint 3)' },
  { id: 'inventario', etiqueta: 'Mi inventario', destino: '../contenido/inventario/inventario.html', privada: true },
  { id: 'subasta', etiqueta: 'Subasta', destino: '../cuentas/subastas.html', privada: false },
  { id: 'cuenta', etiqueta: 'Mi Cuenta', destino: '../cuentas/perfil.html', privada: true },
]);

/** Rutas de cuenta y de sesión, también relativas a este módulo. */
export const RUTAS = Object.freeze({
  inicio: '../cuentas/index.html',
  login: '../cuentas/login.html',
  registro: '../cuentas/registro.html',
  perfil: '../cuentas/perfil.html',
  historial: '../cuentas/historial-transacciones.html',
  cofres: '../cuentas/mis-cofres.html',
  tienda: '../cuentas/tienda.html',
  notificaciones: '../plataforma/notificaciones/notificaciones.html',
  gestionUsuarios: '../cuentas/gestion-usuarios.html',
  auditoria: '../cuentas/auditoria.html',
  listaNegra: '../plataforma/moderacion-sanciones/lista-negra-admin.html',
  metricas: '../plataforma/metricas-plataforma/panel-metricas.html',
});

const ROLES_ADMINISTRATIVOS = Object.freeze(['MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR']);

/** URL real de una ruta relativa a este módulo. */
export function resolver(ruta, base = import.meta.url) {
  return new URL(ruta, base).href;
}

/**
 * La sesión tal como la dejó el login, comprobada contra el `exp` del token.
 *
 * Un token caducado NO cuenta como sesión: la vista se comportaría como con
 * sesión y cada llamada devolvería 401. Se prefiere decirlo y llevar al login.
 *
 * @param {Storage} [almacen]
 * @param {() => number} [ahora] inyectable en pruebas (milisegundos)
 * @returns {{autenticado: boolean, caducada: boolean, apodo: string, uid: string|null, rol: string|null, token: string|null}}
 */
export function leerSesion(almacen = globalThis.sessionStorage, ahora = () => Date.now()) {
  const token = almacen?.getItem(CLAVES.token) ?? null;
  if (!token) {
    return { autenticado: false, caducada: false, apodo: '', uid: null, rol: null, token: null };
  }
  let claims = {};
  try {
    claims = cuerpoDelToken(token) ?? {};
  } catch {
    claims = {};
  }
  const caducada = typeof claims.exp === 'number' && claims.exp * 1000 <= ahora();
  return {
    autenticado: !caducada,
    caducada,
    apodo: almacen.getItem(CLAVES.apodo) ?? claims.preferred_username ?? claims.apodo ?? claims.sub ?? '',
    uid: claims.uid ?? almacen.getItem(CLAVES.usuarioId) ?? null,
    rol: almacen.getItem(CLAVES.rol) ?? claims.rol ?? null,
    token,
  };
}

/** Borra la sesión del navegador y lleva al login. */
export function cerrarSesion({
  almacen = globalThis.sessionStorage,
  navegar = (url) => {
    globalThis.location.href = url;
  },
  base = import.meta.url,
} = {}) {
  for (const clave of Object.values(CLAVES)) {
    almacen.removeItem(clave);
  }
  navegar(resolver(RUTAS.login, base));
}

/**
 * Para vistas privadas: si no hay sesión (o caducó), lleva al login con la
 * ruta de vuelta y devuelve `null`; si la hay, la devuelve.
 *
 * El destino de vuelta es solo ruta + búsqueda del propio origen: nunca una
 * URL absoluta que alguien pudiera colar para sacar al usuario del sitio.
 */
export function exigirSesion({
  almacen = globalThis.sessionStorage,
  ubicacion = globalThis.location,
  navegar = (url) => {
    globalThis.location.href = url;
  },
  base = import.meta.url,
  ahora,
} = {}) {
  const sesion = leerSesion(almacen, ahora);
  if (sesion.autenticado) {
    return sesion;
  }
  if (sesion.caducada) {
    for (const clave of Object.values(CLAVES)) {
      almacen.removeItem(clave);
    }
  }
  const volver = `${ubicacion.pathname}${ubicacion.search}`;
  const login = new URL(resolver(RUTAS.login, base));
  login.searchParams.set('volver', volver);
  if (sesion.caducada) {
    login.searchParams.set('motivo', 'caducada');
  }
  navegar(login.href);
  return null;
}

/**
 * Ruta de vuelta segura leída de `?volver=`: solo rutas del mismo origen.
 * La usa `login.js` tras iniciar sesión.
 */
export function rutaDeVuelta(busqueda = globalThis.location?.search ?? '') {
  const volver = new URLSearchParams(busqueda).get('volver');
  if (!volver || !volver.startsWith('/') || volver.startsWith('//')) {
    return null;
  }
  return volver;
}

function icono(nombre, base, clase = 'icono') {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', clase);
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  use.setAttribute('href', `${resolver('../../../../shared/ui-kit/iconos/sprite.svg', base)}#${nombre}`);
  svg.appendChild(use);
  return svg;
}

function enlace(texto, href, clase) {
  const a = document.createElement('a');
  a.className = clase;
  a.href = href;
  a.textContent = texto;
  return a;
}

/**
 * Monta la cabecera en `raiz` (se vacía primero) y devuelve la sesión leída.
 *
 * @param {HTMLElement} raiz contenedor; se recomienda un `<div data-cabecera-app>`
 * @param {object} [opciones]
 * @param {string|null} [opciones.seccionActiva] id de `SECCIONES` en curso
 * @param {{placeholder?: string, alBuscar?: (texto: string) => void}|null} [opciones.buscador]
 *        solo las vistas con búsqueda real (inventario, subastas, tienda) lo pasan
 * @param {Storage} [opciones.almacen]
 * @param {string} [opciones.base] inyectable en pruebas
 * @param {() => number} [opciones.ahora]
 * @param {(url: string) => void} [opciones.navegar]
 * @returns {{elemento: HTMLElement, sesion: ReturnType<typeof leerSesion>}}
 */
export function montarCabecera(
  raiz,
  {
    seccionActiva = null,
    buscador = null,
    almacen = globalThis.sessionStorage,
    base = import.meta.url,
    ahora,
    navegar = (url) => {
      globalThis.location.href = url;
    },
  } = {},
) {
  const sesion = leerSesion(almacen, ahora);

  const cabecera = document.createElement('header');
  cabecera.className = 'cabecera';
  cabecera.dataset.cabeceraApp = '';

  // --- marca + navegación --------------------------------------------------
  const grupoMarca = document.createElement('div');
  grupoMarca.className = 'cabecera__grupo-marca';
  const marca = enlace('NEXUS BATTLES VI', resolver(sesion.autenticado ? RUTAS.inicio : RUTAS.login, base), 'cabecera__marca');
  marca.setAttribute('aria-label', 'Nexus Battles VI — inicio');
  grupoMarca.appendChild(marca);

  const nav = document.createElement('nav');
  nav.className = 'cabecera__nav';
  nav.setAttribute('aria-label', 'Navegacion principal');
  for (const seccion of SECCIONES) {
    const destino = document.createElement('a');
    destino.className = 'cabecera__destino';
    destino.dataset.seccion = seccion.id;
    destino.textContent = seccion.etiqueta;
    if (seccion.pendiente) {
      destino.classList.add('cabecera__destino--pendiente');
      destino.setAttribute('aria-disabled', 'true');
      destino.dataset.pendiente = seccion.pendiente;
      destino.title = `Todavia no publicada: ${seccion.pendiente}`;
    } else {
      destino.href = resolver(seccion.destino, base);
      if (seccion.privada && !sesion.autenticado) {
        // Una seccion privada sin sesion lleva al login con vuelta a ella.
        const login = new URL(resolver(RUTAS.login, base));
        login.searchParams.set('volver', new URL(destino.href).pathname);
        destino.href = login.href;
        destino.dataset.exigeSesion = '';
      }
    }
    if (seccion.id === seccionActiva) {
      destino.classList.add('activo');
      destino.setAttribute('aria-current', 'page');
    }
    nav.appendChild(destino);
  }
  grupoMarca.appendChild(nav);
  cabecera.appendChild(grupoMarca);

  // --- buscador, solo donde aplica -----------------------------------------
  if (buscador) {
    const formulario = document.createElement('form');
    formulario.className = 'cabecera__buscador';
    formulario.setAttribute('role', 'search');
    formulario.appendChild(icono('buscar', base, 'icono icono--menudo'));
    const etiqueta = document.createElement('label');
    etiqueta.className = 'solo-lectores';
    etiqueta.textContent = buscador.placeholder ?? 'Buscar';
    etiqueta.htmlFor = 'cabecera-busqueda';
    const campo = document.createElement('input');
    campo.className = 'cabecera__buscador-texto';
    campo.type = 'search';
    campo.id = 'cabecera-busqueda';
    campo.name = 'q';
    campo.placeholder = buscador.placeholder ?? 'Buscar';
    formulario.append(etiqueta, campo);
    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      buscador.alBuscar?.(campo.value.trim());
    });
    cabecera.appendChild(formulario);
  }

  // --- acciones: sesión ----------------------------------------------------
  const acciones = document.createElement('div');
  acciones.className = 'cabecera__acciones';

  if (!sesion.autenticado) {
    const zona = document.createElement('div');
    zona.className = 'cabecera__sesion';
    zona.dataset.zona = 'sesion';
    const login = new URL(resolver(RUTAS.login, base));
    if (globalThis.location?.pathname) {
      login.searchParams.set('volver', `${globalThis.location.pathname}${globalThis.location.search ?? ''}`);
    }
    zona.appendChild(enlace('Iniciar sesion', login.href, 'boton boton--secundario boton--pequeno'));
    zona.appendChild(enlace('Registrarse', resolver(RUTAS.registro, base), 'boton boton--primario boton--pequeno'));
    if (sesion.caducada) {
      const aviso = document.createElement('span');
      aviso.className = 'cabecera__aviso-sesion';
      aviso.setAttribute('role', 'status');
      aviso.textContent = 'Tu sesion caduco';
      zona.prepend(aviso);
    }
    acciones.appendChild(zona);
  } else {
    // Créditos: se pinta cuando la vista lo sepa (queda oculto, sin inventar cifras).
    const creditos = document.createElement('span');
    creditos.className = 'cabecera__dato';
    creditos.dataset.zona = 'creditos';
    creditos.hidden = true;
    creditos.appendChild(icono('moneda', base, 'icono icono--menudo'));
    const saldo = document.createElement('span');
    saldo.dataset.zona = 'saldo';
    creditos.appendChild(saldo);
    acciones.appendChild(creditos);

    const campana = document.createElement('a');
    campana.className = 'cabecera__campana';
    campana.href = resolver(RUTAS.notificaciones, base);
    campana.setAttribute('aria-label', 'Notificaciones');
    campana.appendChild(icono('campana', base));
    const contador = document.createElement('span');
    contador.className = 'cabecera__contador';
    contador.dataset.zona = 'contador';
    contador.setAttribute('aria-hidden', 'true');
    contador.hidden = true;
    contador.textContent = '0';
    campana.appendChild(contador);
    acciones.appendChild(campana);

    const cuenta = document.createElement('div');
    cuenta.className = 'cabecera__cuenta';
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'cabecera__cuenta-boton';
    boton.dataset.zona = 'cuenta';
    boton.setAttribute('aria-haspopup', 'menu');
    boton.setAttribute('aria-expanded', 'false');
    const avatar = document.createElement('span');
    avatar.className = 'cabecera__avatar';
    avatar.setAttribute('aria-hidden', 'true');
    avatar.textContent = (sesion.apodo || '?').slice(0, 1).toUpperCase();
    const identidad = document.createElement('span');
    identidad.className = 'cabecera__identidad';
    const apodo = document.createElement('span');
    apodo.className = 'cabecera__apodo';
    apodo.dataset.zona = 'apodo';
    apodo.textContent = sesion.apodo;
    identidad.appendChild(apodo);
    if (sesion.rol && sesion.rol !== 'JUGADOR') {
      const rango = document.createElement('span');
      rango.className = 'cabecera__rango';
      rango.dataset.zona = 'rol';
      rango.textContent = sesion.rol.toLowerCase().replaceAll('_', ' ');
      identidad.appendChild(rango);
    }
    boton.append(avatar, identidad, icono('chevron', base, 'icono icono--menudo'));

    const menu = document.createElement('div');
    menu.className = 'menu cabecera__menu';
    menu.setAttribute('role', 'menu');
    menu.dataset.zona = 'menu-cuenta';
    menu.hidden = true;
    const grupo = document.createElement('div');
    grupo.className = 'menu__grupo';
    const opciones = [
      ['Mi perfil', RUTAS.perfil],
      ['Historial de transacciones', RUTAS.historial],
      ['Mis cofres', RUTAS.cofres],
      ['Tienda', RUTAS.tienda],
    ];
    if (ROLES_ADMINISTRATIVOS.includes(sesion.rol)) {
      opciones.push(['Gestion de usuarios', RUTAS.gestionUsuarios], ['Lista negra', RUTAS.listaNegra]);
      if (sesion.rol !== 'MODERADOR') {
        opciones.push(['Auditoria', RUTAS.auditoria], ['Panel de observabilidad', RUTAS.metricas]);
      }
    }
    for (const [texto, ruta] of opciones) {
      const opcion = enlace(texto, resolver(ruta, base), 'menu__opcion');
      opcion.setAttribute('role', 'menuitem');
      grupo.appendChild(opcion);
    }
    const salir = document.createElement('button');
    salir.type = 'button';
    salir.className = 'menu__opcion menu__opcion--peligro';
    salir.setAttribute('role', 'menuitem');
    salir.dataset.zona = 'cerrar-sesion';
    salir.textContent = 'Cerrar sesion';
    salir.addEventListener('click', () => cerrarSesion({ almacen, navegar, base }));
    grupo.appendChild(salir);
    menu.appendChild(grupo);

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
    acciones.appendChild(cuenta);
  }

  cabecera.appendChild(acciones);
  raiz.replaceChildren(cabecera);
  return { elemento: cabecera, sesion };
}
