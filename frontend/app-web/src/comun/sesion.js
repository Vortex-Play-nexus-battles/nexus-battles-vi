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
 * Aquí no se pinta nada. Solo se lee, se guarda, se borra y se resuelven rutas.
 *
 * ## R17 — la sesión como la espera alguien que no conoce el juego
 *
 * - `guardarSesion` es el único sitio que escribe lo que deja el login; antes
 *   lo escribía `login.js` a mano, clave por clave.
 * - `cerrarSesion` avisa al servidor (auditoría del cierre), avisa a las
 *   demás pestañas y lleva al login diciendo por qué.
 * - `rutaDeVuelta` ya no se fía de que `?volver=` empiece por `/`: el
 *   navegador trata `/\otro.sitio` y `/<tab>/otro.sitio` como direcciones de
 *   OTRO origen, y la comprobación anterior las dejaba pasar (redirección
 *   abierta, hallazgo de la auditoría R17.A).
 *
 * @module comun/sesion
 */

import { rutaDeApi } from './base-api.js';
import { difundirCierre } from './canal-sesion.js';
import { cuerpoDelToken } from './identidad.js';
import { LIMPIAS_POR_RUTA } from './matriz-acceso.js';

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
  preparando: '../cuentas/preparando.html',
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
  controlIntegral: '../plataforma/consola/control-integral.html',
  metricas: '../plataforma/metricas-plataforma/panel-metricas.html',
  tableroTecnico: '../plataforma/metricas-plataforma/tablero-tecnico.html',
});

/**
 * ¿Sirve el borde las direcciones limpias (`/login`, `/jugar`…)?
 *
 * Lo dice la propia página: el borde inyecta
 * `<meta name="nexus-rutas" content="limpias">` en todo HTML que sirve. Sin
 * esa marca (`npm run dev`, el laboratorio visual, un servidor estático) las
 * direcciones limpias no existen y se usan las rutas de siempre. Así el mismo
 * código funciona en los dos sitios sin configurar nada.
 *
 * @param {ParentNode} [documento]
 * @returns {boolean}
 */
export function hayRutasLimpias(documento = globalThis.document) {
  return documento?.querySelector?.('meta[name="nexus-rutas"]')?.content === 'limpias';
}

/**
 * URL real de una ruta relativa a este módulo.
 *
 * R17 — si la ruta es la de una vista con dirección limpia y el borde las
 * sirve, devuelve la limpia (`/jugar` en vez de
 * `/frontend/app-web/src/plataforma/salas-partidas/batallas.html`). Quien
 * llama no cambia: todos los enlaces de la aplicación pasan por aquí.
 */
export function resolver(ruta, base = import.meta.url, documento = globalThis.document) {
  const limpia = LIMPIAS_POR_RUTA[ruta];
  if (limpia && hayRutasLimpias(documento)) {
    return new URL(limpia, base).href;
  }
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

/**
 * Guarda la sesión que devolvió el login (o que compartió otra pestaña).
 *
 * Una clave sin valor se borra en vez de guardarse vacía: `leerSesion`
 * distingue «no está» de «está vacía», y una cadena vacía taparía lo que
 * dice el token.
 *
 * @param {{token: string, apodo?: string|null, rol?: string|null, uid?: string|null}} datos
 * @param {Storage} [almacen]
 */
export function guardarSesion({ token, apodo, rol, uid }, almacen = globalThis.sessionStorage) {
  const valores = {
    [CLAVES.token]: token,
    [CLAVES.apodo]: apodo,
    [CLAVES.rol]: rol,
    [CLAVES.usuarioId]: uid,
  };
  for (const [clave, valor] of Object.entries(valores)) {
    if (valor === undefined || valor === null || valor === '') {
      almacen.removeItem(clave);
    } else {
      almacen.setItem(clave, String(valor));
    }
  }
}

/** Borra del navegador todo lo que dejó el login. */
export function olvidarSesion(almacen = globalThis.sessionStorage) {
  for (const clave of Object.values(CLAVES)) {
    almacen.removeItem(clave);
  }
}

/** Por qué se llega al login. Lo lee `login.js` para decirlo con palabras. */
export const MOTIVOS = Object.freeze({
  CADUCADA: 'caducada',
  CERRADA: 'cerrada',
  REGISTRADA: 'registrada',
});

/**
 * URL del login con la vuelta y el motivo. Un solo sitio la compone.
 *
 * @param {{volver?: string|null, motivo?: string|null}} [opciones]
 * @param {string} [base]
 * @returns {string}
 */
export function urlDeLogin({ volver = null, motivo = null } = {}, base = import.meta.url) {
  const login = new URL(resolver(RUTAS.login, base));
  if (volver) {
    login.searchParams.set('volver', volver);
  }
  if (motivo) {
    login.searchParams.set('motivo', motivo);
  }
  return login.href;
}

/**
 * Avisa al servidor del cierre, para que quede en la auditoría.
 *
 * Nunca bloquea ni falla: cerrar sesión es que el navegador olvide el token
 * (D-31, tokens sin estado), y eso ocurre aunque esta llamada no llegue.
 * `keepalive` deja que la petición termine aunque la página ya esté
 * navegando al login.
 *
 * @param {string} token
 * @param {typeof fetch} [fetchImpl]
 */
export function avisarCierreAlServidor(token, fetchImpl = globalThis.fetch) {
  if (!token || typeof fetchImpl !== 'function') {
    return;
  }
  try {
    const envio = fetchImpl(rutaDeApi('/auth/logout'), {
      method: 'POST',
      keepalive: true,
      headers: { Authorization: `Bearer ${token}` },
    });
    envio?.catch?.(() => {});
  } catch {
    // Sin red o sin fetch: el cierre en el navegador ya está hecho.
  }
}

/**
 * Cierra la sesión: la olvida, avisa al servidor y a las demás pestañas, y
 * lleva al login diciendo que se cerró.
 *
 * La navegación es síncrona a propósito: el menú de cuenta la llama desde un
 * clic y no hay nada que esperar.
 */
export function cerrarSesion({
  almacen = globalThis.sessionStorage,
  navegar = (url) => {
    globalThis.location.href = url;
  },
  base = import.meta.url,
  avisar = avisarCierreAlServidor,
  difundir = difundirCierre,
} = {}) {
  const token = almacen.getItem(CLAVES.token);
  olvidarSesion(almacen);
  avisar(token);
  difundir();
  navegar(urlDeLogin({ motivo: MOTIVOS.CERRADA }, base));
}

/**
 * Las pantallas de entrada. Volver a una de ellas tras entrar sería un bucle:
 * del login al login, o a la preparación de una cuenta que ya está lista.
 */
const PUERTAS_DE_ENTRADA =
  /\/(?:login|registro|preparando|restablecer-solicitar|restablecer-confirmar)(?:\.html)?$/;

/**
 * ¿Es esta ruta un destino seguro al que volver? Devuelve la ruta normalizada
 * (camino + búsqueda + fragmento) o `null`.
 *
 * Se decide con el mismo analizador que usará el navegador (`URL`), no con
 * `startsWith`: `/\otro.sitio` y `/<tab>/otro.sitio` empiezan por `/`, pero
 * el navegador los lee como `//otro.sitio`, es decir, otro origen.
 *
 * @param {string|null|undefined} volver
 * @param {string} [origen] el de esta página
 * @returns {string|null}
 */
export function rutaSegura(volver, origen = globalThis.location?.origin) {
  if (typeof volver !== 'string' || !volver.startsWith('/') || volver.startsWith('//')) {
    return null;
  }
  // Barra invertida o cualquier carácter de control (tabuladores y saltos de
  // línea incluidos): el analizador de URL los normaliza o los descarta, y
  // así es como una ruta «local» acaba en otro sitio.
  if (/[\\\p{Cc}]/u.test(volver)) {
    return null;
  }
  let propio;
  let destino;
  try {
    propio = new URL(origen && origen !== 'null' ? origen : 'http://localhost');
    destino = new URL(volver, propio);
  } catch {
    return null;
  }
  if (destino.origin !== propio.origin || PUERTAS_DE_ENTRADA.test(destino.pathname)) {
    return null;
  }
  return `${destino.pathname}${destino.search}${destino.hash}`;
}

/**
 * Ruta de vuelta segura leída de `?volver=`: solo rutas del mismo origen, y
 * nunca otra pantalla de entrada. La usa `login.js` tras iniciar sesión.
 */
export function rutaDeVuelta(
  busqueda = globalThis.location?.search ?? '',
  origen = globalThis.location?.origin,
) {
  return rutaSegura(new URLSearchParams(busqueda).get('volver'), origen);
}
