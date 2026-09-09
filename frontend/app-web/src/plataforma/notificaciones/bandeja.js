/**
 * HU-NOT-006 — Bandeja de notificaciones del jugador en el navegador.
 *
 * Es el estado y el ciclo de vida del canal, sin DOM: la campana (`campana.js`)
 * solo pinta lo que aqui pasa. Cubre los tres criterios de #32:
 *
 *   CA-01  Un aviso llega por la cola privada a todas las sesiones abiertas
 *          del jugador; el contador de no leidos es el mismo en todas y, si
 *          cambia por lectura en otra sesion, aqui se reconcilia la bandeja.
 *   CA-02  Sin sesiones abiertas, el aviso se guarda; al conectar, esta sesion
 *          se anuncia (`/app/notificaciones/sesion`) y recibe lo pendiente.
 *   CA-03  Si el canal se cae, se pasa a consulta periodica por HTTP
 *          (`.../sessions/{sesionId}/pending` + bandeja) sin perder eventos, se
 *          reintenta la conexion con espera creciente y, al volver, se
 *          reconcilia el estado de lectura. Nunca falla en silencio: el
 *          estado del canal se publica para que la vista lo pinte
 *          (`Estado de conexion`, no un `Aviso`, segun MAPEO-ERRORES.md §5.4).
 *
 * Los mensajes de la cola privada se distinguen por su forma, que es lo que
 * fija `contracts/websocket/notificaciones.yaml`: un `Aviso` trae `id` y
 * `titulo`; `ContadorActualizado` trae solo `noLeidas`; `ErrorDeCanal` es un
 * problem details con `status` y `title`.
 */

import {
  CANAL,
  consultarBandeja as consultarBandejaHttp,
  marcarLeida as marcarLeidaHttp,
  entregarPendientes as entregarPendientesHttp,
  urlDelCanal,
} from './cliente-notificaciones.js';
import { conectarStomp } from './transporte-stomp.js';

/** Variantes del componente `Estado de conexion` del ui-kit. */
export const ESTADO_CANAL = Object.freeze({
  CONECTANDO: 'reconectando',
  ESTABLE: 'estable',
  RECONECTANDO: 'reconectando',
  SIN_CONEXION: 'sin-conexion',
});

/** `Aviso` del contrato: tiene identificador y titulo. */
export function esAviso(mensaje) {
  return Boolean(mensaje) && typeof mensaje.id === 'string' && typeof mensaje.titulo === 'string';
}

/** `ContadorActualizado` del contrato: solo trae la cuenta. */
export function esContador(mensaje) {
  return Boolean(mensaje) && typeof mensaje.noLeidas === 'number' && !('id' in mensaje);
}

/** `ErrorDeCanal` del contrato: problem details. */
export function esProblema(mensaje) {
  return (
    Boolean(mensaje) &&
    typeof mensaje.status === 'number' &&
    typeof mensaje.title === 'string' &&
    !('id' in mensaje)
  );
}

/** Del mas reciente al mas antiguo, que es como se lee una bandeja. */
function ordenar(avisos) {
  return [...avisos].sort((a, b) => String(b.creadaEn).localeCompare(String(a.creadaEn)));
}

const ESPERAS_POR_OMISION = [1000, 2000, 5000, 10000, 30000];

/**
 * @param {object} opciones
 * @param {string} opciones.usuarioId
 * @param {string} opciones.sesionId identificador estable de esta sesion
 * @param {Function} [opciones.conectar] `({url}) => Promise<canal>`; por omision STOMP real
 * @param {{consultarBandeja?: Function, marcarLeida?: Function, entregarPendientes?: Function}} [opciones.cliente]
 * @param {Function} [opciones.esperar] `(ms) => Promise`; inyeccion para las pruebas
 * @param {number[]} [opciones.esperas] espera creciente entre reintentos de conexion, en ms
 * @param {number} [opciones.intervaloSondeo] cada cuanto se consulta por HTTP mientras no hay canal
 * @param {Function} [opciones.alCambiar] recibe el estado completo tras cada cambio
 * @param {Function} [opciones.alAviso] recibe cada aviso NUEVO (para la emergente)
 * @param {Function} [opciones.alError] recibe errores del canal o de HTTP; nunca se tragan
 */
export function crearBandeja({
  usuarioId,
  sesionId,
  conectar = ({ url }) => conectarStomp({ url }),
  cliente = {},
  esperar = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  esperas = ESPERAS_POR_OMISION,
  intervaloSondeo = 15000,
  alCambiar = () => {},
  alAviso = () => {},
  alError = () => {},
}) {
  const http = {
    consultarBandeja: consultarBandejaHttp,
    marcarLeida: marcarLeidaHttp,
    entregarPendientes: entregarPendientesHttp,
    ...cliente,
  };

  const avisos = new Map();
  let noLeidas = 0;
  let canalEstado = ESTADO_CANAL.SIN_CONEXION;
  let canal = null;
  let activa = false;
  /** Promesa de la recuperacion en curso, para no lanzar dos a la vez. */
  let recuperacion = null;

  function estado() {
    return { canal: canalEstado, noLeidas, avisos: ordenar(avisos.values()) };
  }

  function publicar() {
    alCambiar(estado());
  }

  function noLeidasLocales() {
    let cuenta = 0;
    avisos.forEach((aviso) => {
      if (!aviso.leida) {
        cuenta += 1;
      }
    });
    return cuenta;
  }

  /** Incorpora un aviso; devuelve true si era nuevo para esta sesion. */
  function incorporar(aviso) {
    const nuevo = !avisos.has(aviso.id);
    avisos.set(aviso.id, { ...avisos.get(aviso.id), ...aviso });
    return nuevo;
  }

  /** Bandeja completa desde el servidor: la verdad sobre lectura y cuenta. */
  async function reconciliar() {
    const bandeja = await http.consultarBandeja(usuarioId);
    (bandeja.avisos ?? []).forEach((aviso) => incorporar(aviso));
    noLeidas = bandeja.noLeidas ?? noLeidasLocales();
    publicar();
  }

  function alMensaje(mensaje) {
    if (esAviso(mensaje)) {
      const nuevo = incorporar(mensaje);
      noLeidas = noLeidasLocales();
      publicar();
      if (nuevo && !mensaje.leida) {
        alAviso(avisos.get(mensaje.id));
      }
      return;
    }
    if (esContador(mensaje)) {
      noLeidas = mensaje.noLeidas;
      publicar();
      // Otra sesion marco algo como leido: aqui se refleja aviso por aviso.
      if (mensaje.noLeidas !== noLeidasLocales()) {
        reconciliar().catch(alError);
      }
      return;
    }
    if (esProblema(mensaje)) {
      alError(new Error(mensaje.detail || mensaje.title));
    }
  }

  function cambiarCanal(nuevoEstado) {
    canalEstado = nuevoEstado;
    publicar();
  }

  async function abrirCanal() {
    const url = urlDelCanal({ usuarioId, sesionId });
    const abierto = await conectar({ url });
    canal = abierto;
    abierto.suscribir(CANAL.COLA_PRIVADA, alMensaje);
    // CA-02: la sesion se anuncia y el servidor le devuelve lo que se perdio.
    abierto.enviar(CANAL.ALTA_DE_SESION, { usuarioId, sesionId });
    abierto.alCerrar = () => {
      if (canal === abierto) {
        canal = null;
        if (activa) {
          recuperar();
        }
      }
    };
    abierto.alError = alError;
    cambiarCanal(ESTADO_CANAL.ESTABLE);
  }

  /**
   * CA-03. Mientras no haya canal: consulta periodica por HTTP y reintentos
   * de conexion con espera creciente. Al volver, se reconcilia una vez.
   */
  function recuperar() {
    if (!recuperacion) {
      recuperacion = cicloDeRecuperacion().finally(() => {
        recuperacion = null;
      });
    }
    return recuperacion;
  }

  async function cicloDeRecuperacion() {
    cambiarCanal(ESTADO_CANAL.RECONECTANDO);

    let intento = 0;
    let ultimoSondeo = 0;
    while (activa && !canal) {
      const espera = esperas[Math.min(intento, esperas.length - 1)];
      await esperar(espera);
      if (!activa) {
        break;
      }
      try {
        await abrirCanal();
      } catch (error) {
        alError(error);
        cambiarCanal(ESTADO_CANAL.SIN_CONEXION);
        ultimoSondeo += espera;
        if (ultimoSondeo >= intervaloSondeo || intento === 0) {
          ultimoSondeo = 0;
          await sondear();
        }
        intento += 1;
        continue;
      }
      // Volvio el canal: el servidor ya reenvio lo perdido por el alta de
      // sesion; la bandeja completa reconcilia el estado de lectura.
      await reconciliar().catch(alError);
    }
  }

  /** Consulta por HTTP lo que se perdio; no reemplaza al canal. */
  async function sondear() {
    try {
      const pendientes = await http.entregarPendientes(usuarioId, sesionId);
      pendientes.forEach((aviso) => {
        if (incorporar(aviso) && !aviso.leida) {
          alAviso(avisos.get(aviso.id));
        }
      });
      await reconciliar();
    } catch (error) {
      alError(error);
    }
  }

  return {
    get estado() {
      return estado();
    },

    /** Pinta la bandeja por HTTP y abre el canal. Si el canal falla, recupera. */
    async iniciar() {
      activa = true;
      try {
        await reconciliar();
      } catch (error) {
        alError(error);
      }
      cambiarCanal(ESTADO_CANAL.CONECTANDO);
      try {
        await abrirCanal();
      } catch (error) {
        alError(error);
        recuperar();
      }
    },

    /**
     * Marca un aviso como leido para todo el jugador. La cuenta que devuelve
     * el servidor manda sobre la local; las demas sesiones la reciben por el
     * canal (CA-01).
     */
    async marcarLeida(notificacionId) {
      const respuesta = await http.marcarLeida(usuarioId, notificacionId);
      const aviso = avisos.get(notificacionId);
      if (aviso) {
        aviso.leida = true;
      }
      noLeidas = respuesta?.noLeidas ?? noLeidasLocales();
      publicar();
      return noLeidas;
    },

    detener() {
      activa = false;
      const abierto = canal;
      canal = null;
      if (abierto) {
        abierto.cerrar();
      }
      cambiarCanal(ESTADO_CANAL.SIN_CONEXION);
    },
  };
}
