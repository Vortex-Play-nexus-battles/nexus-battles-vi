/**
 * La matriz de acceso, en datos puros — UX-R3.0.
 *
 * ## Por qué está separada de `acceso.js`
 *
 * `acceso.js` resuelve URLs contra `import.meta.url`, porque necesita saber
 * desde dónde se sirvió para acertar tanto tras el borde
 * (`/frontend/app-web/src/…`) como en `npm run dev` (`/src/…`). Eso lo ata a
 * ESM. El laboratorio visual (`tests/visual/`) corre bajo Playwright, que
 * transpila sus ficheros a CommonJS, donde `import.meta` no existe: importar
 * `acceso.js` desde allí reventaba con «Cannot use import.meta outside a
 * module».
 *
 * La alternativa era que el laboratorio mantuviera su propia copia de la
 * matriz —que es exactamente el problema que UX-R3.0 vino a quitar: tres
 * listas de permisos que se contradecían—. Así que se parte por donde toca:
 * aquí los **datos y las decisiones puras**, que no dependen de dónde se
 * sirvió nada; en `acceso.js`, lo que toca el navegador.
 *
 * Nadie importa este módulo directamente desde una vista: `acceso.js`
 * reexporta todo lo de aquí.
 *
 * @module comun/matriz-acceso
 */

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
