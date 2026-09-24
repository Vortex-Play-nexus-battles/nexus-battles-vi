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

import { ACCESO, MATRIZ, VEREDICTO, puedeVer, rutaDeVista } from './matriz-acceso.js';
import { leerSesion, olvidarSesion, resolver, RUTAS } from './sesion.js';
import { h } from './ui/dom.js';

/** Base de resolución de rutas: este módulo vive en `src/comun/`, igual que `sesion.js`. */
const BASE_RUTAS = import.meta.url;

export {
  ACCESO,
  MATRIZ,
  ROLES,
  ROLES_DE_ADMINISTRACION,
  ROLES_DE_MODERACION,
  ROLES_DE_SUPERADMINISTRACION,
  ROLES_DE_TRASTIENDA,
  VEREDICTO,
  armazonDeVista,
  destinoVisible,
  esRolDeTrastienda,
  puedeVer,
  rutaDeVista,
  vistaDeRuta,
} from './matriz-acceso.js';

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
  // La marca que hace que esto sea definitivo.
  //
  // Una vista puede tener su guarda en un `<script type="module">` y montar su
  // cabecera desde OTRO módulo (`gestion-usuarios.js` lo hace). Lanzar una
  // excepción detiene el módulo de la guarda, no el de al lado: el segundo
  // seguía ejecutándose y volvía a pintar la cabecera de consola encima de
  // esta pantalla. Con la marca en `<html>`, cualquier montaje posterior
  // —venga del módulo que venga, y llegue en el orden que llegue— ve que la
  // página está interrumpida y no toca nada.
  documento.documentElement.dataset.acceso = 'denegado';
  // Una barra mínima con la marca. Sin esto, la pantalla de «sin acceso»
  // salía distinta según la vista: las que montan su cabecera desde otro
  // módulo la conservaban (y encima enseñaban la navegación de consola a
  // quien acababa de ser rechazado), y las que la montan en el mismo módulo
  // se quedaban sin ninguna. Ahora es la misma siempre, y no lleva
  // navegación: no se ofrece ir a sitios que tampoco se pueden abrir.
  const barra = h('header', { clase: 'cabecera cabecera--portal' });
  const grupo = h('div', { clase: 'cabecera__grupo-marca' });
  const marcaEnlace = h('a', {
    clase: 'cabecera__marca',
    atributos: { 'aria-label': 'Nexus Battles VI — inicio' },
  });
  marcaEnlace.href = resolver(RUTAS.inicio, base);
  marcaEnlace.append(
    h('span', { clase: 'cabecera__marca-larga', texto: 'NEXUS BATTLES VI' }),
    h('span', {
      clase: 'cabecera__marca-corta',
      texto: 'NB VI',
      atributos: { 'aria-hidden': 'true' },
    }),
  );
  grupo.append(marcaEnlace);
  barra.append(grupo);

  const marco = h('main', { clase: 'pagina interrupcion' });
  const tarjeta = h('section', {
    clase: 'interrupcion__tarjeta',
    atributos: { role: 'alert' },
    datos: { zona: 'sin-permiso' },
  });
  tarjeta.append(
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
  documento.body.replaceChildren(barra, marco);
}
