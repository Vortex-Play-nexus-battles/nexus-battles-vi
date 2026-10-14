/**
 * Inactividad de las sesiones administrativas — 7.3.9.
 *
 * El token de un Moderador, Administrador o Super Administrador dura poco
 * (15 minutos por omisión; lo dice su `exp`) y el servidor deja de aceptarlo
 * en cuanto caduca: el cierre por inactividad lo impone ms-identidad, no esta
 * página. Lo que hace este módulo es que trabajar no sea una carrera contra
 * el reloj y que el cierre no llegue por sorpresa:
 *
 *   - **Mientras la persona trabaja** (clic, tecla, rueda, toque o escritura),
 *     el token se renueva antes de caducar con `POST /auth/sesion/renovacion`.
 *     La renovación conserva la autenticación original: no alarga la sesión
 *     más allá de su máximo, que también fija el servidor.
 *   - **Si deja de trabajar**, un minuto antes del fin aparece un aviso con la
 *     cuenta atrás y dos salidas: «Seguir conectado» (renueva) o «Cerrar
 *     sesión». Es lo que pide WCAG 2.2.1 (tiempo ajustable): avisar antes y
 *     dejar ampliar con una acción simple.
 *   - **Si nadie responde**, se cierra la sesión un segundo antes de que el
 *     token caduque y el login dice por qué («por inactividad»), con la vuelta
 *     a la misma pantalla.
 *
 * Una renovación que no llega (red caída) no se toma por inactividad: si hubo
 * actividad, al caducar decide el vigilante de la sesión (motivo «caducada»).
 * Un 401 de la renovación sí cierra en el acto: la sesión ya no vale (cambio
 * de rol, sanción, segundo factor restablecido).
 *
 * Cada pestaña tiene su propio token (sessionStorage) y su propia actividad:
 * una pestaña olvidada se cierra sola sin tocar a las demás.
 *
 * Los plazos se miden en la hora del SERVIDOR (`ahoraEnElServidor`): el `exp`
 * es suyo, y un equipo con el reloj descuadrado unos minutos renovaría tarde o
 * avisaría a destiempo. El desfase se mide al recibir cada token.
 *
 * @module comun/inactividad-administrativa
 */

import { rutaDeApi } from './base-api.js';
import { cuerpoDelToken } from './identidad.js';
import { esRolDeTrastienda } from './matriz-acceso.js';
import { MOTIVOS, ahoraEnElServidor, guardarSesion, leerSesion } from './sesion.js';
import { abrirDialogo } from './ui/dialogo.js';
import { h } from './ui/dom.js';

/** Con actividad reciente, se renueva cuando quedan menos de 5 minutos. */
export const RENOVAR_CUANDO_QUEDEN_MS = 5 * 60_000;

/** Sin actividad, el aviso sale cuando queda un minuto. */
export const AVISAR_CUANDO_QUEDEN_MS = 60_000;

/** El cierre se adelanta un segundo al fin del token para decir el motivo correcto. */
export const ADELANTO_DEL_CIERRE_MS = 1_000;

/** Cada cuánto se mira el reloj como mucho. */
export const PERIODO_DE_REVISION_MS = 15_000;

/** Lo que cuenta como «la persona está trabajando». */
export const EVENTOS_DE_ACTIVIDAD = Object.freeze([
  'pointerdown',
  'keydown',
  'wheel',
  'touchstart',
  'input',
]);

/** Resultados de {@link renovarSesion}. */
export const RENOVACION = Object.freeze({
  RENOVADA: 'renovada',
  RECHAZADA: 'rechazada',
  SIN_RESPUESTA: 'sin-respuesta',
});

/**
 * Pide un token nuevo para la sesión en uso.
 *
 * Va con `fetch` directo y no con el interceptor: un 401 aquí es la
 * respuesta esperada a una sesión que ya no vale, no un fallo de la vista.
 *
 * @param {string} token
 * @param {typeof fetch} [fetchImpl]
 * @returns {Promise<{estado: string, token?: string}>}
 */
export async function renovarSesion(token, fetchImpl = globalThis.fetch) {
  if (!token || typeof fetchImpl !== 'function') {
    return { estado: RENOVACION.SIN_RESPUESTA };
  }
  try {
    const respuesta = await fetchImpl(rutaDeApi('/auth/sesion/renovacion'), {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      cache: 'no-store',
    });
    if (respuesta.status === 401 || respuesta.status === 403) {
      return { estado: RENOVACION.RECHAZADA };
    }
    if (!respuesta.ok) {
      return { estado: RENOVACION.SIN_RESPUESTA };
    }
    const cuerpo = await respuesta.json();
    if (typeof cuerpo?.token !== 'string' || cuerpo.token === '') {
      return { estado: RENOVACION.SIN_RESPUESTA };
    }
    return { estado: RENOVACION.RENOVADA, token: cuerpo.token };
  } catch {
    return { estado: RENOVACION.SIN_RESPUESTA };
  }
}

/**
 * «0:59», «1:00»: lo que queda, para leerlo de un vistazo.
 *
 * @param {number} ms
 */
export function tiempoRestante(ms) {
  const segundos = Math.max(0, Math.ceil(ms / 1000));
  const minutos = Math.floor(segundos / 60);
  return `${minutos}:${String(segundos % 60).padStart(2, '0')}`;
}

/**
 * El aviso de «tu sesión se va a cerrar». Devuelve con qué actualizar la
 * cuenta atrás y con qué cerrarlo.
 *
 * @param {{restanteMs: number, alSeguir: () => void, alSalir: () => void}} opciones
 * @returns {{actualizar: (ms: number) => void, cerrar: () => void}}
 */
export function mostrarAvisoDeInactividad({ restanteMs, alSeguir, alSalir }) {
  const contador = h('strong', { clase: 't-cifra', texto: tiempoRestante(restanteMs) });
  const cuerpo = h('div', {
    hijos: [
      h('p', {
        hijos: [
          'Por seguridad, la consola se cierra si no se usa. Tu sesión terminará en ',
          contador,
          '.',
        ],
      }),
      h('p', {
        clase: 't-meta',
        texto: 'Si sigues aquí, continúa: no perderás lo que estabas haciendo.',
      }),
    ],
  });
  let decidido = false;
  const seguir = h('button', {
    clase: 'boton boton--primario',
    texto: 'Seguir conectado',
    atributos: { type: 'button' },
    datos: { accion: 'seguir-conectado' },
  });
  const salir = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Cerrar sesión',
    atributos: { type: 'button' },
    datos: { accion: 'cerrar-sesion' },
  });
  const dialogo = abrirDialogo({
    titulo: 'Tu sesión está por cerrarse',
    cuerpo,
    acciones: [seguir, salir],
    cerrableFuera: false,
    // Cerrar el aviso con Escape o con la equis también es «sigo aquí».
    alCerrar: () => {
      if (!decidido) {
        decidido = true;
        alSeguir();
      }
    },
  });
  dialogo.elemento.dataset.zona = 'aviso-de-inactividad';
  // El foco, en la salida que la persona casi siempre quiere.
  seguir.focus();
  seguir.addEventListener('click', () => {
    decidido = true;
    dialogo.cerrar();
    alSeguir();
  });
  salir.addEventListener('click', () => {
    decidido = true;
    dialogo.cerrar();
    alSalir();
  });
  return {
    actualizar(ms) {
      contador.textContent = tiempoRestante(ms);
    },
    cerrar() {
      decidido = true;
      dialogo.cerrar();
    },
  };
}

function caducidadDelToken(token) {
  const exp = cuerpoDelToken(token)?.exp;
  return typeof exp === 'number' ? exp * 1000 : null;
}

/**
 * Crea la guarda de inactividad de esta pestaña. `null` si no hay sesión o si
 * no es de un rol administrativo (los jugadores no tienen este límite).
 *
 * Todo es inyectable para las pruebas; en el navegador basta `terminar`.
 *
 * @param {object} opciones
 * @param {(motivo: string) => void} opciones.terminar cierre de la sesión (el del vigilante)
 * @returns {{detener: () => void, revisar: () => void, renovar: () => Promise<void>}|null}
 */
export function crearGuardaDeInactividad({
  terminar,
  almacen = globalThis.sessionStorage,
  ahora = () => Date.now(),
  reloj = globalThis,
  documento = globalThis.document,
  fetchImpl = globalThis.fetch,
  avisar = mostrarAvisoDeInactividad,
} = {}) {
  const inicial = leerSesion(almacen, ahora);
  if (!inicial.autenticado || !esRolDeTrastienda(inicial.rol) || typeof terminar !== 'function') {
    return null;
  }

  let detenida = false;
  let temporizador = null;
  let cuentaAtras = null;
  let aviso = null;
  let enCurso = null;
  // La carga de la página cuenta como actividad: quien la abrió está ahí.
  let ultimaActividad = ahora();
  let ultimaRenovacion = Number.NEGATIVE_INFINITY;
  const desenganches = [];
  // El `exp` es hora del servidor: se compara con la hora del servidor según
  // este equipo, no con su reloj (un reloj descuadrado no cierra antes ni
  // renueva tarde).
  const servidor = () => ahoraEnElServidor(almacen, ahora);

  function cerrarAviso() {
    reloj.clearTimeout(cuentaAtras);
    cuentaAtras = null;
    if (aviso) {
      const abierto = aviso;
      aviso = null;
      abierto.cerrar();
    }
  }

  function detener() {
    detenida = true;
    reloj.clearTimeout(temporizador);
    temporizador = null;
    cerrarAviso();
    for (const soltar of desenganches.splice(0)) {
      soltar();
    }
  }

  function finalizar(motivo) {
    if (detenida) {
      return;
    }
    detener();
    terminar(motivo);
  }

  function huboActividad() {
    return ultimaActividad > ultimaRenovacion;
  }

  /** Renueva el token; con un 401 la sesión ya no vale y se cierra. */
  function renovar() {
    if (detenida) {
      return Promise.resolve();
    }
    if (enCurso) {
      return enCurso;
    }
    const sesion = leerSesion(almacen, ahora);
    if (!sesion.autenticado) {
      return Promise.resolve();
    }
    const pedidaEn = ahora();
    enCurso = Promise.resolve(renovarSesion(sesion.token, fetchImpl))
      .then((resultado) => {
        if (detenida) {
          return;
        }
        if (resultado.estado === RENOVACION.RENOVADA) {
          ultimaRenovacion = pedidaEn;
          guardarSesion(
            {
              token: resultado.token,
              apodo: sesion.apodo,
              rol: sesion.rol,
              uid: sesion.uid,
              recibidoEn: ahora(),
            },
            almacen,
          );
          cerrarAviso();
        } else if (resultado.estado === RENOVACION.RECHAZADA) {
          finalizar(MOTIVOS.CADUCADA);
        }
      })
      .catch(() => {})
      .finally(() => {
        enCurso = null;
        if (!detenida) {
          programar();
        }
      });
    return enCurso;
  }

  function abrirAviso(restante) {
    aviso = avisar({
      restanteMs: restante,
      alSeguir: () => {
        aviso = null;
        reloj.clearTimeout(cuentaAtras);
        ultimaActividad = ahora();
        renovar();
      },
      alSalir: () => {
        aviso = null;
        finalizar(MOTIVOS.CERRADA);
      },
    });
    const latir = () => {
      if (!aviso || detenida) {
        return;
      }
      const fin = caducidadDelToken(leerSesion(almacen, ahora).token);
      if (fin !== null) {
        aviso.actualizar(fin - servidor());
      }
      cuentaAtras = reloj.setTimeout(latir, 1_000);
    };
    cuentaAtras = reloj.setTimeout(latir, 1_000);
  }

  /** Mira el reloj y decide: renovar, avisar, cerrar o esperar. */
  function revisar() {
    if (detenida) {
      return;
    }
    const sesion = leerSesion(almacen, ahora);
    if (!sesion.autenticado) {
      // Caducada u olvidada en otro sitio: es asunto del vigilante.
      detener();
      return;
    }
    const fin = caducidadDelToken(sesion.token);
    if (fin === null) {
      detener();
      return;
    }
    const restante = fin - servidor();
    if (restante <= ADELANTO_DEL_CIERRE_MS) {
      if (huboActividad()) {
        // Hubo actividad pero la renovación no llegó (red): no es
        // inactividad. Al caducar decide el vigilante.
        detener();
        return;
      }
      finalizar(MOTIVOS.INACTIVIDAD);
      return;
    }
    if (huboActividad() && restante <= RENOVAR_CUANDO_QUEDEN_MS) {
      renovar();
      return;
    }
    if (!huboActividad() && restante <= AVISAR_CUANDO_QUEDEN_MS && !aviso) {
      abrirAviso(restante);
    }
    programar();
  }

  /** La próxima revisión: lo antes de lo que toque (periodo, aviso o cierre). */
  function programar() {
    reloj.clearTimeout(temporizador);
    const fin = caducidadDelToken(leerSesion(almacen, ahora).token);
    if (fin === null) {
      return;
    }
    const restante = fin - servidor();
    const hitos = [PERIODO_DE_REVISION_MS, restante - ADELANTO_DEL_CIERRE_MS];
    if (!aviso) {
      hitos.push(restante - AVISAR_CUANDO_QUEDEN_MS);
    }
    const espera = Math.max(0, Math.min(...hitos.filter((ms) => ms > 0), PERIODO_DE_REVISION_MS));
    temporizador = reloj.setTimeout(revisar, espera);
  }

  let ultimaSenal = 0;
  function alActuar() {
    const momento = ahora();
    ultimaActividad = momento;
    // Con el aviso abierto, actuar en la página es seguir conectado.
    if (aviso) {
      cerrarAviso();
      renovar();
      return;
    }
    // No hace falta revisar en cada tecla: una vez por segundo basta.
    if (momento - ultimaSenal > 1_000) {
      ultimaSenal = momento;
      const fin = caducidadDelToken(leerSesion(almacen, ahora).token);
      if (fin !== null && fin - servidor() <= RENOVAR_CUANDO_QUEDEN_MS) {
        renovar();
      }
    }
  }

  if (typeof documento?.addEventListener === 'function') {
    for (const evento of EVENTOS_DE_ACTIVIDAD) {
      documento.addEventListener(evento, alActuar, { capture: true, passive: true });
      desenganches.push(() =>
        documento.removeEventListener(evento, alActuar, { capture: true, passive: true }),
      );
    }
    const alVolver = () => {
      if (documento.visibilityState === 'visible') {
        revisar();
      }
    };
    documento.addEventListener('visibilitychange', alVolver);
    desenganches.push(() => documento.removeEventListener('visibilitychange', alVolver));
  }

  programar();
  return { detener, revisar, renovar };
}

let vigente = null;

/**
 * La guarda de esta página. Idempotente.
 *
 * @param {object} opciones las de {@link crearGuardaDeInactividad}
 */
export function vigilarInactividad(opciones) {
  if (vigente) {
    return vigente;
  }
  vigente = crearGuardaDeInactividad(opciones);
  return vigente;
}
