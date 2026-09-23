/**
 * La sesión del navegador — UX-R3.0.
 *
 * Extraído de `cabecera-app.js`, donde convivía con el pintado de la barra.
 * Se separa porque ahora hay **tres** armazones (portal, jugador, consola) y
 * una política de acceso (`acceso.js`), y los cuatro necesitan saber quién ha
 * iniciado sesión. Dejarlo dentro del módulo que pinta la cabecera obligaba a
 * importar la cabecera para preguntar por la sesión, lo que en `acceso.js`
 * habría cerrado un ciclo: acceso → cabecera → acceso.
 *
 * Aquí no se pinta nada. Solo se lee, se borra y se resuelven rutas.
 *
 * @module comun/sesion
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
 * Rutas de la aplicación, relativas a ESTE módulo (`src/comun/`) y resueltas
 * contra `import.meta.url`, para acertar tanto servidas por el borde
 * (`/frontend/app-web/src/`) como por `npm run dev` (`src/`).
 */
export const RUTAS = Object.freeze({
  inicio: '../cuentas/index.html',
  login: '../cuentas/login.html',
  registro: '../cuentas/registro.html',
  recuperar: '../cuentas/restablecer-solicitar.html',
  perfil: '../cuentas/perfil.html',
  historial: '../cuentas/historial-transacciones.html',
  cofres: '../cuentas/mis-cofres.html',
  tienda: '../cuentas/tienda.html',
  inventario: '../contenido/inventario/inventario.html',
  productos: '../contenido/productos/productos.html',
  subastas: '../cuentas/subastas.html',
  publicarSubasta: '../cuentas/publicar-subasta.html',
  batallas: '../plataforma/salas-partidas/batallas.html',
  crearSala: '../plataforma/salas-partidas/crear-sala.html',
  torneos: '../plataforma/torneos/torneos.html',
  notificaciones: '../plataforma/notificaciones/notificaciones.html',
  gestionUsuarios: '../cuentas/gestion-usuarios.html',
  crearCuentaAdmin: '../cuentas/crear-cuenta-admin.html',
  auditoria: '../cuentas/auditoria.html',
  listaNegra: '../plataforma/moderacion-sanciones/lista-negra-admin.html',
  sanciones: '../plataforma/moderacion-sanciones/sanciones-admin.html',
  parametros: '../plataforma/admin-parametros/parametros-admin.html',
  misSanciones: '../plataforma/moderacion-sanciones/mis-sanciones.html',
  consola: '../plataforma/consola/consola.html',
  metricas: '../plataforma/metricas-plataforma/panel-metricas.html',
  tableroTecnico: '../plataforma/metricas-plataforma/tablero-tecnico.html',
});

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
    apodo:
      almacen.getItem(CLAVES.apodo) ??
      claims.preferred_username ??
      claims.apodo ??
      claims.sub ??
      '',
    uid: claims.uid ?? almacen.getItem(CLAVES.usuarioId) ?? null,
    rol: almacen.getItem(CLAVES.rol) ?? claims.rol ?? null,
    token,
  };
}

/** Borra del navegador todo lo que dejó el login. */
export function olvidarSesion(almacen = globalThis.sessionStorage) {
  for (const clave of Object.values(CLAVES)) {
    almacen.removeItem(clave);
  }
}

/** Borra la sesión del navegador y lleva al login. */
export function cerrarSesion({
  almacen = globalThis.sessionStorage,
  navegar = (url) => {
    globalThis.location.href = url;
  },
  base = import.meta.url,
} = {}) {
  olvidarSesion(almacen);
  navegar(resolver(RUTAS.login, base));
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
