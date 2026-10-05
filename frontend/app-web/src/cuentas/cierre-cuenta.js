/**
 * «Cerrar mi cuenta» — derecho al olvido (HU-PRV-005, RF-PRV-005, 7.3.6;
 * ms-identidad-perfiles.yaml 1.4.0).
 *
 *   - `GET    /api/v1/perfiles/{uid}/cierre` dice si hay un cierre programado
 *     y para cuándo;
 *   - `POST`  lo programa, con la contraseña actual como verificación de
 *     identidad;
 *   - `DELETE` lo cancela.
 *
 * **El servidor decide todo.** El plazo (`plazoDias`, RN-USR-011), si hay
 * subastas o pujas que lo impiden, si la contraseña es la correcta y si la
 * cuenta está bloqueada: la vista solo lo explica, por el `type` del problem
 * details y nunca por la redacción.
 *
 * Mismo patrón que `preguntas-seguridad.js`: el formulario vive en el HTML
 * con `method="post"` y su botón nace apagado hasta que este módulo escucha
 * `submit` (G1, `formularioListo`). La contraseña no se guarda en ningún
 * sitio y el formulario se vacía después de cada intento.
 *
 * @module cuentas/cierre-cuenta
 */

import { baseDeApi } from '../comun/base-api.js';
import { detalleDelProblema, tipoDelProblema } from '../comun/codigo-de-correo.js';
import { cuerpoDe } from '../comun/entrada.js';
import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { RUTAS, resolver } from '../comun/sesion.js';
import { limpiarAviso, pintarAviso, tonoPorEstado } from '../comun/ui/aviso.js';
import { conCarga } from '../comun/ui/boton.js';
import { marcarErrorDe, mejorarContrasena } from '../comun/ui/campo.js';
import { confirmar } from '../comun/ui/dialogo.js';
import { fecha } from '../comun/ui/formato.js';
import { formularioListo } from '../comun/ui/formulario-seguro.js';

/** Estados del cierre, tal como los publica el contrato (`CierreDeCuenta.estado`). */
export const ESTADOS_DEL_CIERRE = Object.freeze({
  SIN_SOLICITUD: 'SIN_SOLICITUD',
  PROGRAMADO: 'PROGRAMADO',
});

/** Motivos de rechazo del `POST`, tal como los nombra el contrato. */
export const PROBLEMAS_DEL_CIERRE = Object.freeze({
  CONTRASENA_ACTUAL_INCORRECTA: 'contrasena-actual-incorrecta',
  CUENTA_BLOQUEADA: 'cuenta-bloqueada',
  OPERACIONES_PENDIENTES: 'cierre-con-operaciones-pendientes',
  SUBASTAS_NO_DISPONIBLES: 'subastas-no-disponibles',
});

/**
 * Ruta del contrato para una cuenta. El `uid` es el del token (ADR-002).
 *
 * @param {string} uid
 * @returns {string}
 */
export function rutaDelCierre(uid) {
  return `/api/v1/perfiles/${encodeURIComponent(uid)}/cierre`;
}

/** Un rechazo del servidor, ya leído. La vista decide por `tipo` y `estado`. */
export class FalloDelCierre extends Error {
  /**
   * @param {{estado: number, tipo?: string|null, detalle?: string|null,
   *   subastasActivas?: number, pujasVigentes?: number}} datos
   */
  constructor({ estado, tipo = null, detalle = null, subastasActivas = 0, pujasVigentes = 0 }) {
    super(detalle ?? `HTTP ${estado}`);
    this.name = 'FalloDelCierre';
    this.estado = estado;
    this.tipo = tipo;
    this.detalle = detalle;
    this.subastasActivas = subastasActivas;
    this.pujasVigentes = pujasVigentes;
  }
}

/**
 * La respuesta del servidor con la forma que la vista espera aunque falte
 * algo: sin un estado conocido, nada está programado.
 *
 * @param {unknown} cuerpo
 * @returns {{estado: string, plazoDias: number|null, solicitadoEn: string|null,
 *   programadoPara: string|null}}
 */
export function leerEstadoDelCierre(cuerpo) {
  const programado = cuerpo?.estado === ESTADOS_DEL_CIERRE.PROGRAMADO && cuerpo?.programadoPara;
  return {
    estado: programado ? ESTADOS_DEL_CIERRE.PROGRAMADO : ESTADOS_DEL_CIERRE.SIN_SOLICITUD,
    plazoDias: Number.isInteger(cuerpo?.plazoDias) ? cuerpo.plazoDias : null,
    solicitadoEn: programado ? (cuerpo.solicitadoEn ?? null) : null,
    programadoPara: programado ? cuerpo.programadoPara : null,
  };
}

/** @param {number|unknown} valor */
function entero(valor) {
  return Number.isInteger(valor) && valor > 0 ? valor : 0;
}

/**
 * @param {Response} respuesta
 * @returns {Promise<ReturnType<typeof leerEstadoDelCierre>>}
 */
async function leerRespuesta(respuesta) {
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw new FalloDelCierre({
      estado: respuesta.status,
      tipo: tipoDelProblema(cuerpo),
      detalle: detalleDelProblema(cuerpo),
      subastasActivas: entero(cuerpo?.subastasActivas),
      pujasVigentes: entero(cuerpo?.pujasVigentes),
    });
  }
  return leerEstadoDelCierre(cuerpo);
}

const ACEPTA = 'application/json, application/problem+json';

/**
 * @param {string} uid
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export async function consultarCierre(uid, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(`${baseDeApi()}${rutaDelCierre(uid)}`, {
    headers: { Accept: ACEPTA },
    cache: 'no-store',
  });
  return leerRespuesta(respuesta);
}

/**
 * @param {string} uid
 * @param {string} passwordActual
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export async function solicitarCierre(
  uid,
  passwordActual,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(`${baseDeApi()}${rutaDelCierre(uid)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: ACEPTA },
    body: JSON.stringify({ passwordActual }),
  });
  return leerRespuesta(respuesta);
}

/**
 * @param {string} uid
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export async function cancelarCierre(uid, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(`${baseDeApi()}${rutaDelCierre(uid)}`, {
    method: 'DELETE',
    headers: { Accept: ACEPTA },
  });
  return leerRespuesta(respuesta);
}

/**
 * «2 subastas activas y 1 puja vigente».
 *
 * @param {number} subastas
 * @param {number} pujas
 * @returns {string}
 */
export function queLoImpide(subastas, pujas) {
  const partes = [];
  if (subastas > 0) {
    partes.push(`${subastas} ${subastas === 1 ? 'subasta activa' : 'subastas activas'}`);
  }
  if (pujas > 0) {
    partes.push(`${pujas} ${pujas === 1 ? 'puja vigente' : 'pujas vigentes'}`);
  }
  return partes.join(' y ');
}

/**
 * Qué decir cuando el servidor no programa el cierre, por su `type`.
 *
 * @param {unknown} error
 * @returns {{tono: 'error'|'advertencia'|'info', titulo: string, detalle: string,
 *   campo: 'passwordActual'|null, irASubastas: boolean}}
 */
export function rechazoDelCierre(error) {
  const base = { campo: null, irASubastas: false };
  if (!(error instanceof FalloDelCierre)) {
    return {
      ...base,
      tono: 'error',
      titulo: 'No pudimos programar el cierre',
      detalle: 'La conexión falló y no se programó nada. Inténtalo de nuevo en unos segundos.',
    };
  }
  const tono = tonoPorEstado(error.estado);
  switch (error.tipo) {
    case PROBLEMAS_DEL_CIERRE.CONTRASENA_ACTUAL_INCORRECTA:
      return {
        ...base,
        tono,
        titulo: 'La contraseña actual es incorrecta',
        detalle:
          'No se programó nada. Cuenta como un intento fallido: tras varios, la cuenta se bloquea unos minutos.',
        campo: 'passwordActual',
      };
    case PROBLEMAS_DEL_CIERRE.CUENTA_BLOQUEADA:
      return {
        ...base,
        tono,
        titulo: 'Tu cuenta está bloqueada temporalmente',
        detalle: 'Hubo demasiados intentos fallidos. Espera unos minutos y vuelve a intentarlo.',
      };
    case PROBLEMAS_DEL_CIERRE.OPERACIONES_PENDIENTES: {
      const que = queLoImpide(error.subastasActivas, error.pujasVigentes);
      return {
        ...base,
        tono,
        titulo: 'Todavía no puedes cerrar tu cuenta',
        detalle: `${que ? `Tienes ${que}. ` : 'Tienes subastas o pujas abiertas. '}Espera a que terminen (o cancela tus publicaciones) y vuelve a intentarlo. No se programó nada.`,
        irASubastas: true,
      };
    }
    case PROBLEMAS_DEL_CIERRE.SUBASTAS_NO_DISPONIBLES:
      return {
        ...base,
        tono: 'error',
        titulo: 'No pudimos comprobar tus subastas',
        detalle:
          'Antes de cerrar una cuenta revisamos que no tenga subastas ni pujas abiertas, y ahora mismo no se pudo. No se programó nada: inténtalo de nuevo en unos minutos.',
      };
    default:
      break;
  }
  return {
    ...base,
    tono: error.estado >= 500 || !error.estado ? 'error' : tono,
    titulo: 'No pudimos programar el cierre',
    detalle:
      error.estado >= 500 || !error.estado
        ? 'El servicio de cuentas no respondió bien. No se programó nada: inténtalo de nuevo en unos minutos.'
        : (error.detalle ?? 'No se programó nada. Revisa los datos e inténtalo otra vez.'),
  };
}

/**
 * Monta la sección sobre `[data-zona="cierre-cuenta"]`. No consulta nada hasta
 * que se llama a `cargar()` (cuando se abre la pestaña «Privacidad»).
 *
 * @param {ParentNode} raiz
 * @param {{sesion: {uid: string|null}, consultar?: Function, solicitar?: Function,
 *   cancelar?: Function, confirmarImpl?: typeof confirmar, irA?: (url: string) => void}} opciones
 * @returns {{cargar: () => Promise<void>}|null} `null` si la vista no tiene la sección
 */
export function montarCierreDeCuenta(
  raiz,
  {
    sesion,
    consultar = consultarCierre,
    solicitar = solicitarCierre,
    cancelar = cancelarCierre,
    confirmarImpl = confirmar,
    irA = (url) => window.location.assign(url),
  },
) {
  const seccion = raiz.querySelector('[data-zona="cierre-cuenta"]');
  if (!seccion) {
    return null;
  }
  const zona = (nombre) => seccion.querySelector(`[data-zona="${nombre}"]`);
  const estado = zona('estado-cierre');
  const plazo = zona('cierre-plazo');
  const zonaAviso = zona('aviso-cierre');
  const acciones = zona('acciones-cierre');
  const formulario = zona('formulario-cierre');
  const botonSolicitar = seccion.querySelector('[data-accion="solicitar-cierre"]');
  const botonCancelar = seccion.querySelector('[data-accion="cancelar-cierre"]');
  const botonReintentar = seccion.querySelector('[data-accion="reintentar-cierre"]');
  const botonVolver = formulario.querySelector('[data-accion="volver-cierre"]');
  const botonConfirmar = formulario.querySelector('[data-accion="confirmar-cierre"]');
  const campoClave = formulario.querySelector('[name="passwordActualCierre"]');
  mejorarContrasena(campoClave);

  /** El último plazo que dio el servidor; sin él, la vista no inventa uno. */
  let plazoDias = null;

  function decir(texto) {
    estado.textContent = texto;
    estado.hidden = !texto;
  }

  /** La contraseña escrita no se conserva tras un intento, salga bien o mal. */
  function olvidarClave() {
    campoClave.value = '';
  }

  function cerrarFormulario() {
    olvidarClave();
    marcarErrorDe(campoClave, null);
    formulario.hidden = true;
    acciones.hidden = false;
  }

  function pintarPlazo() {
    if (!plazo) {
      return;
    }
    plazo.textContent = plazoDias
      ? `${plazoDias} días después de pedirlo. Hasta entonces tu cuenta sigue funcionando y puedes cancelar la solicitud aquí mismo.`
      : 'Pasado el plazo que fija el servicio de cuentas. Hasta entonces tu cuenta sigue funcionando y puedes cancelar la solicitud aquí mismo.';
  }

  /** @param {ReturnType<typeof leerEstadoDelCierre>} situacion */
  function pintarSituacion(situacion) {
    plazoDias = situacion.plazoDias ?? plazoDias;
    pintarPlazo();
    botonReintentar.hidden = true;
    seccion.dataset.estado = situacion.estado;
    if (situacion.estado === ESTADOS_DEL_CIERRE.PROGRAMADO) {
      decir(
        `Cierre programado para el ${fecha(situacion.programadoPara)}. ` +
          `Lo pediste el ${fecha(situacion.solicitadoEn)}; hasta entonces puedes cancelarlo.`,
      );
      botonSolicitar.hidden = true;
      botonCancelar.hidden = false;
    } else {
      decir('Tu cuenta está activa: no has pedido cerrarla.');
      botonSolicitar.hidden = false;
      botonCancelar.hidden = true;
    }
    acciones.hidden = false;
  }

  async function cargar() {
    limpiarAviso(zonaAviso);
    botonSolicitar.hidden = true;
    botonCancelar.hidden = true;
    botonReintentar.hidden = true;
    decir('Consultando el estado de tu cuenta…');
    try {
      pintarSituacion(await consultar(sesion.uid));
    } catch {
      decir('');
      pintarAviso(zonaAviso, {
        tono: 'error',
        titulo: 'No pudimos consultar si tu cuenta tiene un cierre programado',
        detalle: 'No significa que lo tenga: significa que no lo sabemos ahora mismo.',
      });
      acciones.hidden = false;
      botonReintentar.hidden = false;
    }
  }

  botonReintentar.addEventListener('click', () => {
    cargar();
  });

  botonSolicitar.addEventListener('click', () => {
    limpiarAviso(zonaAviso);
    campoClave.value = '';
    marcarErrorDe(campoClave, null);
    formulario.hidden = false;
    acciones.hidden = true;
    campoClave.focus();
  });

  botonVolver.addEventListener('click', () => {
    limpiarAviso(zonaAviso);
    cerrarFormulario();
    botonSolicitar.focus();
  });

  campoClave.addEventListener('input', () => {
    if (campoClave.getAttribute('aria-invalid') === 'true') {
      marcarErrorDe(campoClave, null);
    }
  });

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    marcarErrorDe(campoClave, null);
    if (!campoClave.value) {
      marcarErrorDe(campoClave, 'Escribe tu contraseña actual para confirmar que eres tú.');
      campoClave.focus();
      return;
    }

    const confirmado = await confirmarImpl({
      titulo: 'Cerrar tu cuenta',
      mensaje: plazoDias
        ? `Tu cuenta y tus datos personales se eliminarán dentro de ${plazoDias} días. Hasta entonces puedes cancelarlo.`
        : 'Tu cuenta y tus datos personales se eliminarán cuando venza el plazo. Hasta entonces puedes cancelarlo.',
      textoConfirmar: 'Programar el cierre',
      textoCancelar: 'No, volver',
      peligro: true,
    });
    if (!confirmado) {
      return;
    }

    conCarga(botonConfirmar, true, 'Programando…');
    let situacion;
    try {
      situacion = await solicitar(sesion.uid, campoClave.value);
    } catch (error) {
      conCarga(botonConfirmar, false);
      const rechazo = rechazoDelCierre(error);
      olvidarClave();
      pintarAviso(zonaAviso, {
        tono: rechazo.tono,
        titulo: rechazo.titulo,
        detalle: rechazo.detalle,
        accion: rechazo.irASubastas
          ? {
              texto: 'Ir a Subastas',
              nombre: 'ir-a-subastas',
              alPulsar: () => irA(resolver(RUTAS.subastas)),
            }
          : null,
      });
      if (rechazo.campo === 'passwordActual') {
        marcarErrorDe(campoClave, 'La contraseña actual es incorrecta.');
        campoClave.focus();
      } else {
        zonaAviso.focus();
      }
      return;
    }
    conCarga(botonConfirmar, false);
    cerrarFormulario();
    pintarSituacion(situacion);
    pintarAviso(zonaAviso, {
      tono: 'exito',
      titulo: 'Programaste el cierre de tu cuenta',
      detalle: `Se eliminará el ${fecha(situacion.programadoPara)}. Hasta entonces puedes cancelarlo desde aquí.`,
    });
    zonaAviso.focus();
  });

  botonCancelar.addEventListener('click', async () => {
    limpiarAviso(zonaAviso);
    const confirmado = await confirmarImpl({
      titulo: 'Cancelar el cierre de tu cuenta',
      mensaje: 'Tu cuenta seguirá activa y no se eliminará ningún dato.',
      textoConfirmar: 'Cancelar el cierre',
      textoCancelar: 'No, mantenerlo',
      peligro: false,
    });
    if (!confirmado) {
      return;
    }
    conCarga(botonCancelar, true, 'Cancelando…');
    try {
      pintarSituacion(await cancelar(sesion.uid));
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Cancelaste el cierre de tu cuenta',
        detalle: 'Tu cuenta sigue activa y no se eliminará ningún dato.',
      });
    } catch {
      pintarAviso(zonaAviso, {
        tono: 'error',
        titulo: 'No pudimos cancelar el cierre',
        detalle: 'El cierre sigue programado. Inténtalo de nuevo en unos segundos.',
      });
    } finally {
      conCarga(botonCancelar, false);
    }
    zonaAviso.focus();
  });

  // G1 — la vista ya escucha `submit`: el botón se puede encender.
  formularioListo(formulario);

  formulario.hidden = true;
  pintarPlazo();
  return { cargar };
}
