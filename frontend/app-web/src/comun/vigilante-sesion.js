/**
 * El vigilante de la sesión — R17.
 *
 * `leerSesion()` responde bien a «¿hay sesión AHORA?», pero solo cuando se le
 * pregunta, y hasta R17 se le preguntaba una vez: al cargar la vista. Lo que
 * pasaba después no lo veía nadie:
 *
 *   - el token caducaba con la pestaña abierta y la vista seguía pintada,
 *     con cada botón devolviendo un 401 que la persona leía como «el juego
 *     está roto»;
 *   - el servidor dejaba de aceptar el token antes de tiempo (cambio de
 *     contraseña, cambio de rol, servicio de identidad redesplegado con otra
 *     clave) y pasaba lo mismo;
 *   - se cerraba sesión en una pestaña y las demás seguían dentro;
 *   - tras cerrar sesión, el botón «Atrás» del navegador devolvía la página
 *     privada entera desde su caché de páginas (bfcache).
 *
 * Aquí se vigila todo eso mientras la página está abierta. Lo instala
 * `montarArmazon` cuando hay sesión, así que ninguna vista tiene que hacer
 * nada.
 *
 * ## Por qué un 401 o un 403 no cierran la sesión sin más
 *
 * Porque no siempre son culpa de la sesión. Un 403 puede ser un permiso que
 * de verdad falta (un jugador en una acción de moderación), y un 401 de un
 * servicio puede venir de ESE servicio, que no pudo descargar las claves del
 * emisor. Echar a la persona al login en esos casos crea un bucle: entra, la
 * vista vuelve a pedir, el servicio vuelve a fallar, vuelta al login.
 *
 * Así que ante un rechazo se le pregunta al dueño de la sesión —el servicio
 * de identidad— si el token sigue valiendo. Solo si dice que no se cierra; si
 * no contesta, no se decide nada (una caída no es una sesión caducada).
 *
 * @module comun/vigilante-sesion
 */

import { rutaDeApi } from './base-api.js';
import { escucharCanal } from './canal-sesion.js';
import { cuerpoDelToken } from './identidad.js';
import { MOTIVOS, leerSesion, olvidarSesion, rutaSegura, urlDeLogin } from './sesion.js';

/** Evento que emite el interceptor HTTP cuando un servicio rechaza el token. */
export const EVENTO_RECHAZO = 'nexus:credencial-rechazada';

/** Lo que dura un «el token sigue valiendo» antes de volver a preguntar. */
export const VALIDEZ_DE_COMPROBACION_MS = 30_000;

/** Máximo que admite `setTimeout` sin desbordarse (unos 24,8 días). */
const MAXIMO_TEMPORIZADOR_MS = 2_147_483_647;

/** Veredictos de `comprobarCredencial`. */
export const VEREDICTOS = Object.freeze({
  VALIDA: 'valida',
  INVALIDA: 'invalida',
  DESCONOCIDA: 'desconocida',
});

/**
 * Pregunta al servicio de identidad si el token sigue valiendo.
 *
 * Usa el estado del alta (`GET /auth/onboarding`) porque es la consulta más
 * barata que exige sesión y que los cuatro roles tienen permitida: un 200 es
 * «vale», un 401/403 es «no vale». Va con `fetch` directo y no con el
 * interceptor, para que la propia comprobación no dispare otra.
 *
 * @param {string} token
 * @param {typeof fetch} [fetchImpl]
 * @returns {Promise<'valida'|'invalida'|'desconocida'>}
 */
export async function comprobarCredencial(token, fetchImpl = globalThis.fetch) {
  if (!token || typeof fetchImpl !== 'function') {
    return VEREDICTOS.DESCONOCIDA;
  }
  try {
    const respuesta = await fetchImpl(rutaDeApi('/auth/onboarding'), {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      cache: 'no-store',
    });
    if (respuesta.ok) {
      return VEREDICTOS.VALIDA;
    }
    if (respuesta.status === 401 || respuesta.status === 403) {
      return VEREDICTOS.INVALIDA;
    }
    return VEREDICTOS.DESCONOCIDA;
  } catch {
    return VEREDICTOS.DESCONOCIDA;
  }
}

/** Instante de caducidad del token, en milisegundos, o `null` si no lo dice. */
export function caducidadDe(sesion) {
  const exp = cuerpoDelToken(sesion?.token)?.exp;
  return typeof exp === 'number' ? exp * 1000 : null;
}

/**
 * Crea un vigilante para la sesión de esta pestaña. Devuelve `null` si no
 * hay sesión que vigilar.
 *
 * Todo es inyectable para las pruebas; en el navegador no se pasa nada.
 *
 * @param {object} [opciones]
 * @returns {{detener: () => void, terminar: (motivo: string) => void, comprobar: () => Promise<void>}|null}
 */
export function crearVigilante({
  almacen = globalThis.sessionStorage,
  ubicacion = globalThis.location,
  navegar = (url) => {
    globalThis.location.replace(url);
  },
  base,
  ahora = () => Date.now(),
  reloj = globalThis,
  ventana = globalThis,
  documento = globalThis.document,
  comprobar = comprobarCredencial,
  escuchar = escucharCanal,
} = {}) {
  const inicial = leerSesion(almacen, ahora);
  if (!inicial.autenticado) {
    return null;
  }

  let terminado = false;
  let temporizador = null;
  let enCurso = null;
  let validaHasta = 0;
  const desenganches = [];

  function rutaActual() {
    if (!ubicacion?.pathname) {
      return null;
    }
    return rutaSegura(
      `${ubicacion.pathname}${ubicacion.search ?? ''}${ubicacion.hash ?? ''}`,
      ubicacion.origin,
    );
  }

  function detener() {
    reloj.clearTimeout(temporizador);
    temporizador = null;
    for (const soltar of desenganches.splice(0)) {
      soltar();
    }
  }

  let destinoFinal = null;

  /** Olvida la sesión y lleva al login con el motivo. Solo una vez. */
  function terminar(motivo) {
    if (terminado) {
      return;
    }
    terminado = true;
    detener();
    olvidarSesion(almacen);
    // Tras un cierre voluntario no se ofrece volver: quien cerró no quiere
    // que la siguiente persona en ese navegador aterrice en su pantalla.
    const volver = motivo === MOTIVOS.CERRADA ? null : rutaActual();
    destinoFinal = urlDeLogin({ volver, motivo }, base);
    navegar(destinoFinal);
  }

  /** Programa el aviso de caducidad para el instante que dice el token. */
  function programarCaducidad() {
    reloj.clearTimeout(temporizador);
    const expira = caducidadDe(leerSesion(almacen, ahora));
    if (expira === null) {
      return;
    }
    const restante = Math.min(Math.max(expira - ahora(), 0), MAXIMO_TEMPORIZADOR_MS);
    temporizador = reloj.setTimeout(revisar, restante);
  }

  /** ¿Sigue habiendo sesión? Si no, fuera; si sí, se reprograma. */
  function revisar() {
    if (terminado) {
      return;
    }
    const sesion = leerSesion(almacen, ahora);
    if (!sesion.autenticado) {
      // Sin token (otra vista lo borró) o caducado: en los dos casos lo que
      // la persona necesita es volver a entrar.
      terminar(MOTIVOS.CADUCADA);
      return;
    }
    programarCaducidad();
  }

  /** Un servicio rechazó el token: se le pregunta al emisor si sigue valiendo. */
  function comprobarConElEmisor() {
    if (terminado) {
      return Promise.resolve();
    }
    if (enCurso) {
      return enCurso;
    }
    if (ahora() < validaHasta) {
      return Promise.resolve();
    }
    const { token } = leerSesion(almacen, ahora);
    enCurso = Promise.resolve(comprobar(token))
      .then((veredicto) => {
        if (veredicto === VEREDICTOS.INVALIDA) {
          terminar(MOTIVOS.CADUCADA);
        } else if (veredicto === VEREDICTOS.VALIDA) {
          validaHasta = ahora() + VALIDEZ_DE_COMPROBACION_MS;
        }
      })
      .catch(() => {})
      .finally(() => {
        enCurso = null;
      });
    return enCurso;
  }

  function enganchar(objetivo, evento, manejador) {
    if (typeof objetivo?.addEventListener !== 'function') {
      return;
    }
    objetivo.addEventListener(evento, manejador);
    desenganches.push(() => objetivo.removeEventListener(evento, manejador));
  }

  // 1. Caducidad: a la hora exacta que dice el token.
  programarCaducidad();

  // 2. Vuelta a la pestaña: los temporizadores se congelan con el equipo
  //    suspendido, así que al volver se mira el reloj otra vez.
  enganchar(documento, 'visibilitychange', () => {
    if (documento.visibilityState === 'visible') {
      revisar();
    }
  });

  // 3. «Atrás» tras cerrar sesión: la página vuelve de la caché de páginas
  //    tal como estaba, con datos privados pintados. Se comprueba de nuevo.
  //    Este oyente NO se suelta al terminar: si la página ya echó a la
  //    persona y el navegador la resucita, tiene que volver a echarla.
  if (typeof ventana?.addEventListener === 'function') {
    ventana.addEventListener('pageshow', (evento) => {
      if (!evento?.persisted) {
        return;
      }
      if (terminado) {
        navegar(destinoFinal);
        return;
      }
      revisar();
    });
  }

  // 4. Un servicio rechazó el token (lo avisa el interceptor HTTP).
  enganchar(ventana, EVENTO_RECHAZO, () => {
    comprobarConElEmisor();
  });

  // 5. Otras pestañas: cierres y peticiones de sesión.
  desenganches.push(
    escuchar({
      alCerrarse: () => terminar(MOTIVOS.CERRADA),
      sesionParaCompartir: () => {
        const sesion = leerSesion(almacen, ahora);
        if (!sesion.autenticado) {
          return null;
        }
        return { token: sesion.token, apodo: sesion.apodo, rol: sesion.rol, uid: sesion.uid };
      },
    }),
  );

  return { detener, terminar, comprobar: comprobarConElEmisor };
}

let vigente = null;

/**
 * El vigilante de esta página. Idempotente: montar la cabecera dos veces no
 * instala dos vigilantes.
 *
 * @param {object} [opciones] las de `crearVigilante`
 */
export function vigilarSesion(opciones = {}) {
  if (vigente) {
    return vigente;
  }
  vigente = crearVigilante(opciones);
  return vigente;
}
