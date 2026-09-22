/**
 * Política de acceso de la interfaz — UX-R3.0.
 *
 * RF-RBAC-001 (catálogo de roles), RF-RBAC-002 (matriz de permisos),
 * RF-RBAC-004 (autorización en servidor), RF-INV-008 (la barra «adapta las
 * opciones visibles al estado de autenticación y al rol del usuario»).
 *
 * ## Por qué existe
 *
 * Hasta UX-R2 cada vista decidía por su cuenta quién podía verla, y lo hacía
 * a medias:
 *
 * 1. Las ocho vistas de administración llamaban a `exigirSesion()` **y nada
 *    más**. Un JUGADOR que escribiera `cuentas/gestion-usuarios.html` en la
 *    barra del navegador veía la pantalla administrativa entera; la API le
 *    negaba los datos, pero la interfaz ya le había contado que existe, qué
 *    columnas tiene y qué acciones ofrece. La nav no la enseñaba: eso no es
 *    una comprobación, es un escondite.
 * 2. La lista de roles administrativos estaba escrita **cuatro veces**
 *    (`cabecera-app.js`, `parametros.js`, `sanciones.js`, `torneos.js`), con
 *    dos significados distintos bajo el mismo nombre: en unos sitios
 *    `ROLES_DE_ADMINISTRACION` incluía MODERADOR y en otros no.
 *
 * Aquí vive una sola vez: el catálogo de roles, la matriz vista→permiso y los
 * tres guardas (`exigirSesion`, `exigirRol`, `puedeVer`). Una vista no vuelve
 * a escribir `if (rol === ...)`.
 *
 * ## Lo que este módulo NO es
 *
 * No es seguridad. RF-RBAC-004 es explícito: «el sistema deberá verificar la
 * autorización del solicitante **en el servidor** antes de ejecutar cualquier
 * operación, con independencia de los controles aplicados en la interfaz de
 * usuario». Todo lo de aquí es *usabilidad*: no enseñar puertas que no se
 * pueden abrir, y explicarlo con palabras cuando se intenta. Quien salte
 * estos guardas con las herramientas del navegador se encuentra exactamente
 * lo mismo que antes: un 401/403 del microservicio.
 *
 * @module comun/acceso
 */

import { leerSesion, olvidarSesion, resolver, RUTAS } from './sesion.js';
import { h } from './ui/dom.js';

/** Base de resolución de rutas: este módulo vive en `src/comun/`, igual que `sesion.js`. */
const BASE_RUTAS = import.meta.url;

/** Catálogo de roles — RF-RBAC-001, §7.3.1 del documento fuente. */
export const ROLES = Object.freeze({
  JUGADOR: 'JUGADOR',
  MODERADOR: 'MODERADOR',
  ADMINISTRADOR: 'ADMINISTRADOR',
  SUPER_ADMINISTRADOR: 'SUPER_ADMINISTRADOR',
});

/**
 * Los tres conjuntos que de verdad usa la interfaz, con nombres que dicen qué
 * incluyen. El nombre viejo `ROLES_DE_ADMINISTRACION` significaba dos cosas
 * distintas según el archivo; aquí cada conjunto dice a quién abarca.
 */

/** Puede moderar contenido y sancionar — Tabla 24: «Moderar comentarios». */
export const ROLES_DE_MODERACION = Object.freeze([
  ROLES.MODERADOR,
  ROLES.ADMINISTRADOR,
  ROLES.SUPER_ADMINISTRADOR,
]);

/** Gestiona usuarios, productos y parámetros — Tabla 24: «Gestionar productos». */
export const ROLES_DE_ADMINISTRACION = Object.freeze([
  ROLES.ADMINISTRADOR,
  ROLES.SUPER_ADMINISTRADOR,
]);

/** Solo el Super Administrador — Tabla 24: «Crear admin/moderador». */
export const ROLES_DE_SUPERADMINISTRACION = Object.freeze([ROLES.SUPER_ADMINISTRADOR]);

/**
 * Cualquier rol de trastienda. Es la pregunta que hace el armazón para
 * decidir si además de jugar, esta persona opera el sistema.
 */
export const ROLES_DE_TRASTIENDA = ROLES_DE_MODERACION;

/**
 * ¿Este rol opera el sistema, además de jugar?
 *
 * @param {string|null|undefined} rol
 * @returns {boolean}
 */
export function esRolDeTrastienda(rol) {
  return ROLES_DE_TRASTIENDA.includes(String(rol ?? ''));
}

/**
 * Niveles de acceso de una vista. `publica` no es «sin protección»: es que el
 * contenido está pensado para un visitante (RF-INV-008 da como actor
 * principal «Jugador; Visitante»).
 */
export const ACCESO = Object.freeze({
  PUBLICA: 'publica',
  SESION: 'sesion',
  MODERACION: 'moderacion',
  ADMINISTRACION: 'administracion',
  SUPERADMINISTRACION: 'superadministracion',
});

/** Qué roles satisfacen cada nivel. `null` = no hace falta rol, solo sesión. */
const ROLES_POR_NIVEL = Object.freeze({
  [ACCESO.PUBLICA]: null,
  [ACCESO.SESION]: null,
  [ACCESO.MODERACION]: ROLES_DE_MODERACION,
  [ACCESO.ADMINISTRACION]: ROLES_DE_ADMINISTRACION,
  [ACCESO.SUPERADMINISTRACION]: ROLES_DE_SUPERADMINISTRACION,
});

/**
 * Matriz vista → (armazón, acceso). Es la fuente única de §15: el laboratorio
 * visual la lee para saber con qué identidad abrir cada pantalla, y cada vista
 * la lee para saber a quién dejar pasar. Una vista nueva que no esté aquí la
 * detecta `acceso.test.js` contra el disco.
 *
 * `armazon` decide qué navegación se monta:
 *   - `publico`  — portal de entrada: marca y poco más
 *   - `jugador`  — la aplicación del jugador (RF-INV-008: seis destinos)
 *   - `admin`    — la consola de operación
 */
export const MATRIZ = Object.freeze({
  // --- Portal de entrada ---------------------------------------------------
  login: { ruta: 'cuentas/login.html', acceso: ACCESO.PUBLICA, armazon: 'publico' },
  registro: { ruta: 'cuentas/registro.html', acceso: ACCESO.PUBLICA, armazon: 'publico' },
  'restablecer-solicitar': {
    ruta: 'cuentas/restablecer-solicitar.html',
    acceso: ACCESO.PUBLICA,
    armazon: 'publico',
  },
  'restablecer-confirmar': {
    ruta: 'cuentas/restablecer-confirmar.html',
    acceso: ACCESO.PUBLICA,
    armazon: 'publico',
  },

  // --- Vitrinas que un visitante puede mirar -------------------------------
  // RF-INV-008 da «Visitante» como actor: el catálogo de productos, el
  // listado de subastas y el cuadro de torneos se ven sin cuenta. Operar
  // sobre ellos (pujar, comprar, inscribirse) sí exige sesión, y eso lo
  // comprueba cada acción, no la pantalla.
  productos: {
    ruta: 'contenido/productos/productos.html',
    acceso: ACCESO.PUBLICA,
    armazon: 'jugador',
  },
  subastas: { ruta: 'cuentas/subastas.html', acceso: ACCESO.PUBLICA, armazon: 'jugador' },
  pujas: { ruta: 'cuentas/pujas.html', acceso: ACCESO.PUBLICA, armazon: 'jugador' },
  torneos: { ruta: 'plataforma/torneos/torneos.html', acceso: ACCESO.PUBLICA, armazon: 'jugador' },

  // --- Jugador -------------------------------------------------------------
  home: { ruta: 'cuentas/index.html', acceso: ACCESO.SESION, armazon: 'jugador' },
  perfil: { ruta: 'cuentas/perfil.html', acceso: ACCESO.SESION, armazon: 'jugador' },
  'historial-transacciones': {
    ruta: 'cuentas/historial-transacciones.html',
    acceso: ACCESO.SESION,
    armazon: 'jugador',
  },
  'mis-cofres': { ruta: 'cuentas/mis-cofres.html', acceso: ACCESO.SESION, armazon: 'jugador' },
  inventario: {
    ruta: 'contenido/inventario/inventario.html',
    acceso: ACCESO.SESION,
    armazon: 'jugador',
  },
  tienda: { ruta: 'cuentas/tienda.html', acceso: ACCESO.SESION, armazon: 'jugador' },
  'publicar-subasta': {
    ruta: 'cuentas/publicar-subasta.html',
    acceso: ACCESO.SESION,
    armazon: 'jugador',
  },
  batallas: {
    ruta: 'plataforma/salas-partidas/batallas.html',
    acceso: ACCESO.SESION,
    armazon: 'jugador',
  },
  'crear-sala': {
    ruta: 'plataforma/salas-partidas/crear-sala.html',
    acceso: ACCESO.SESION,
    armazon: 'jugador',
  },
  'sala-batalla': {
    ruta: 'plataforma/salas-partidas/sala-batalla.html',
    acceso: ACCESO.SESION,
    armazon: 'jugador',
  },
  'validacion-heroe': {
    ruta: 'plataforma/salas-partidas/validacion-heroe.html',
    acceso: ACCESO.SESION,
    armazon: 'jugador',
  },
  chat: { ruta: 'plataforma/salas-partidas/chat.html', acceso: ACCESO.SESION, armazon: 'jugador' },
  'publicar-comentario': {
    ruta: 'plataforma/comentarios/publicar-comentario.html',
    acceso: ACCESO.SESION,
    armazon: 'jugador',
  },
  notificaciones: {
    ruta: 'plataforma/notificaciones/notificaciones.html',
    acceso: ACCESO.SESION,
    armazon: 'jugador',
  },
  'mis-sanciones': {
    ruta: 'plataforma/moderacion-sanciones/mis-sanciones.html',
    acceso: ACCESO.SESION,
    armazon: 'jugador',
  },

  // --- Trastienda ----------------------------------------------------------
  // El aterrizaje de la consola. No consume ningún servicio propio: es el
  // índice de las herramientas que este rol sí puede abrir, y por eso le
  // basta con el nivel más bajo de la trastienda.
  consola: {
    ruta: 'plataforma/consola/consola.html',
    acceso: ACCESO.MODERACION,
    armazon: 'admin',
  },
  // El nivel sale de la Tabla 24 (matriz de permisos por rol), no del sitio
  // donde vive el archivo.
  'sanciones-admin': {
    ruta: 'plataforma/moderacion-sanciones/sanciones-admin.html',
    acceso: ACCESO.MODERACION,
    armazon: 'admin',
  },
  'lista-negra-admin': {
    ruta: 'plataforma/moderacion-sanciones/lista-negra-admin.html',
    acceso: ACCESO.MODERACION,
    armazon: 'admin',
  },
  'gestion-usuarios': {
    ruta: 'cuentas/gestion-usuarios.html',
    acceso: ACCESO.ADMINISTRACION,
    armazon: 'admin',
  },
  'parametros-admin': {
    ruta: 'plataforma/admin-parametros/parametros-admin.html',
    acceso: ACCESO.ADMINISTRACION,
    armazon: 'admin',
  },
  'panel-metricas': {
    ruta: 'plataforma/metricas-plataforma/panel-metricas.html',
    acceso: ACCESO.ADMINISTRACION,
    armazon: 'admin',
  },
  'tablero-tecnico': {
    ruta: 'plataforma/metricas-plataforma/tablero-tecnico.html',
    acceso: ACCESO.ADMINISTRACION,
    armazon: 'admin',
  },
  // RF-RBAC-003: «exclusivamente al Super Administrador».
  'crear-cuenta-admin': {
    ruta: 'cuentas/crear-cuenta-admin.html',
    acceso: ACCESO.SUPERADMINISTRACION,
    armazon: 'admin',
  },
  // RF-AUD-001 y SeguridadConfig de ms-cumplimiento:
  // `/api/v1/admin/auditoria` es `hasRole("SUPER_ADMINISTRADOR")`.
  auditoria: {
    ruta: 'cuentas/auditoria.html',
    acceso: ACCESO.SUPERADMINISTRACION,
    armazon: 'admin',
  },
});

/** Cómo termina una comprobación de acceso. */
export const VEREDICTO = Object.freeze({
  VISIBLE: 'VISIBLE',
  /** Falta sesión: se lleva al login con vuelta. */
  REDIRIGE: 'REDIRIGE',
  /** Hay sesión pero el rol no alcanza: se explica, no se rebota. */
  DENEGADA: 'DENEGADA',
});

/**
 * ¿Puede esta sesión ver esta vista? Sin efectos: solo responde.
 *
 * Se usa en dos sitios con la misma respuesta, que es justamente el punto:
 * el armazón para decidir qué destinos pinta, y la vista para decidir si se
 * carga. Antes eran dos criterios distintos y por eso no coincidían.
 *
 * @param {string} idVista clave de `MATRIZ`
 * @param {{autenticado?: boolean, rol?: string|null}|null} [sesion]
 * @returns {{veredicto: string, nivel: string, rolesAdmitidos: readonly string[]|null}}
 */
export function puedeVer(idVista, sesion = null) {
  const entrada = MATRIZ[idVista];
  const nivel = entrada?.acceso ?? ACCESO.SESION;
  const rolesAdmitidos = ROLES_POR_NIVEL[nivel] ?? null;

  if (nivel === ACCESO.PUBLICA) {
    return { veredicto: VEREDICTO.VISIBLE, nivel, rolesAdmitidos };
  }
  if (!sesion?.autenticado) {
    return { veredicto: VEREDICTO.REDIRIGE, nivel, rolesAdmitidos };
  }
  if (rolesAdmitidos && !rolesAdmitidos.includes(String(sesion.rol ?? ''))) {
    return { veredicto: VEREDICTO.DENEGADA, nivel, rolesAdmitidos };
  }
  return { veredicto: VEREDICTO.VISIBLE, nivel, rolesAdmitidos };
}

/**
 * Atajo para el armazón: ¿pinto este destino? Un destino que llevaría a una
 * pantalla denegada no se pinta — enseñar una puerta cerrada no informa,
 * frustra. Uno que solo pide sesión sí se pinta: lleva al login, que es un
 * sitio útil al que llegar.
 *
 * @param {string} idVista
 * @param {{autenticado?: boolean, rol?: string|null}|null} sesion
 * @returns {boolean}
 */
export function destinoVisible(idVista, sesion) {
  return puedeVer(idVista, sesion).veredicto !== VEREDICTO.DENEGADA;
}

/**
 * Ruta de una vista, relativa a `src/comun/` para que `resolver()` la entienda.
 *
 * Las rutas de `MATRIZ` se escriben desde `src/` porque así las lee el
 * laboratorio visual y así aparecen en la URL. Los módulos de `comun/` están
 * un nivel más adentro, de ahí el `../`.
 *
 * @param {string} idVista
 * @returns {string|null}
 */
export function rutaDeVista(idVista) {
  const entrada = MATRIZ[idVista];
  return entrada ? `../${entrada.ruta}` : null;
}

/**
 * URL absoluta de una vista, resuelta contra ESTE módulo.
 *
 * Quien llama no hace cuentas de `../`: da igual desde qué carpeta importe
 * `acceso.js`, porque la resolución ocurre aquí, donde se sabe que este
 * archivo vive en `src/comun/`. Antes cada llamante ajustaba los saltos a
 * mano y una vista dos niveles más abajo enlazaba a una ruta inexistente.
 *
 * `base` se puede inyectar —las pruebas lo hacen— para comprobar que el
 * resultado es el mismo servido por el borde (`/frontend/app-web/src/…`) y
 * por `npm run dev` (`/src/…`). En producción no se pasa: `import.meta.url`
 * ya es la ruta real desde la que se sirvió el módulo.
 *
 * @param {string} idVista
 * @param {string} [base]
 * @returns {string|null}
 */
export function urlDeVista(idVista, base = BASE_RUTAS) {
  const ruta = rutaDeVista(idVista);
  return ruta ? resolver(ruta, base) : null;
}

/**
 * Qué vista es esta, deducida de la URL.
 *
 * Es lo que permite que una vista no tenga que declarar quién es: el armazón
 * mira dónde está y la matriz le dice el resto. Sin esto, las 31 vistas
 * tendrían que pasar su propio identificador a mano, y la que se olvidara de
 * hacerlo volvería en silencio al comportamiento anterior — que es justo el
 * fallo que este bloque corrige.
 *
 * Se compara por el final de la ruta para que dé igual si sirve el borde
 * (`/frontend/app-web/src/cuentas/login.html`) o `npm run dev`
 * (`/cuentas/login.html`).
 *
 * @param {string} [ruta] `location.pathname`
 * @returns {string|null} clave de `MATRIZ`, o `null` si no es una vista conocida
 */
export function vistaDeRuta(ruta = globalThis.location?.pathname ?? '') {
  const limpia = String(ruta).split('?')[0].split('#')[0];
  for (const [id, entrada] of Object.entries(MATRIZ)) {
    if (limpia.endsWith(`/${entrada.ruta}`) || limpia === entrada.ruta) {
      return id;
    }
  }
  return null;
}

/**
 * Qué armazón le corresponde a una vista, según la matriz.
 *
 * @param {string|null} idVista
 * @returns {'publico'|'jugador'|'admin'|null}
 */
export function armazonDeVista(idVista) {
  return MATRIZ[idVista]?.armazon ?? null;
}

/* =========================================================================
   Guardas — lo que una vista llama en su primera línea
   ========================================================================= */

/**
 * Para vistas privadas: si no hay sesión (o caducó), lleva al login con la
 * ruta de vuelta y devuelve `null`; si la hay, la devuelve.
 *
 * El destino de vuelta es solo ruta + búsqueda del propio origen: nunca una
 * URL absoluta que alguien pudiera colar para sacar al usuario del sitio.
 *
 * @param {object} [opciones]
 * @returns {ReturnType<typeof leerSesion>|null}
 */
export function exigirSesion({
  almacen = globalThis.sessionStorage,
  ubicacion = globalThis.location,
  navegar = (url) => {
    globalThis.location.href = url;
  },
  base = BASE_RUTAS,
  ahora,
} = {}) {
  const sesion = leerSesion(almacen, ahora);
  if (sesion.autenticado) {
    return sesion;
  }
  if (sesion.caducada) {
    olvidarSesion(almacen);
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
 * Guarda completa de una vista: sesión **y** rol, según `MATRIZ`.
 *
 * Es la única línea que una vista privada necesita escribir. Resuelve los tres
 * finales de §17 sin que la vista los conozca:
 *
 *   - sin sesión      → al login, con vuelta y con el motivo
 *   - rol insuficiente→ **no rebota**: pinta «No tienes acceso a esta sección»
 *                       con una salida hacia donde sí puede ir. Rebotar a la
 *                       home deja a la persona sin saber qué pasó, y rebotar
 *                       al login le sugiere que su sesión está mal cuando no
 *                       lo está.
 *   - con permiso     → devuelve la sesión y la vista sigue
 *
 * @param {string} idVista clave de `MATRIZ`
 * @param {object} [opciones]
 * @returns {ReturnType<typeof leerSesion>|null} la sesión, o `null` si no pasa
 */
export function exigirAcceso(
  idVista,
  {
    almacen = globalThis.sessionStorage,
    ubicacion = globalThis.location,
    navegar = (url) => {
      globalThis.location.href = url;
    },
    base = BASE_RUTAS,
    ahora,
    documento = globalThis.document,
  } = {},
) {
  const nivel = MATRIZ[idVista]?.acceso ?? ACCESO.SESION;
  if (nivel === ACCESO.PUBLICA) {
    return leerSesion(almacen, ahora);
  }

  const sesion = exigirSesion({ almacen, ubicacion, navegar, base, ahora });
  if (!sesion) {
    return null;
  }

  const { veredicto, rolesAdmitidos } = puedeVer(idVista, sesion);
  if (veredicto === VEREDICTO.VISIBLE) {
    return sesion;
  }

  pintarSinPermiso(documento, { rolesAdmitidos, base });
  return null;
}

/**
 * Sustituye el contenido de la página por la explicación de §17.
 *
 * Se reemplaza el `<body>` entero a propósito: si se dejara la vista debajo,
 * seguiría cargando datos que el servidor va a negar y llenaría la consola de
 * 403 mientras la persona lee que no tiene acceso.
 *
 * @param {Document} documento
 * @param {{rolesAdmitidos: readonly string[]|null, base?: string}} opciones
 */
export function pintarSinPermiso(documento, { rolesAdmitidos = null, base = BASE_RUTAS } = {}) {
  if (!documento?.body) {
    return;
  }
  const marco = h('main', { clase: 'pagina interrupcion' });
  const tarjeta = h('section', {
    clase: 'interrupcion__tarjeta',
    atributos: { role: 'alert' },
    datos: { zona: 'sin-permiso' },
  });
  tarjeta.append(
    h('p', { clase: 'interrupcion__marca', texto: 'NEXUS BATTLES VI' }),
    h('h1', { clase: 'interrupcion__titulo', texto: 'No tienes acceso a esta sección.' }),
    h('p', {
      clase: 'interrupcion__detalle',
      texto: rolesAdmitidos
        ? 'Esta sección forma parte de las herramientas de operación del Nexo y tu cuenta no las tiene habilitadas.'
        : 'Tu cuenta no tiene habilitada esta sección.',
    }),
  );
  const salidas = h('div', { clase: 'interrupcion__salidas' });
  const volver = h('a', {
    clase: 'boton boton--primario boton--grande',
    texto: 'Volver a mi inicio',
  });
  volver.href = resolver(RUTAS.inicio, base);
  salidas.append(volver);
  tarjeta.append(salidas);
  marco.append(tarjeta);
  documento.body.replaceChildren(marco);
}
