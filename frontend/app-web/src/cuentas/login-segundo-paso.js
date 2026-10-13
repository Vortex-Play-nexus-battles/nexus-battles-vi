/**
 * Segundo paso del login — HU-AUT-007 (ms-identidad-auth 2.2.0).
 *
 * Con la verificación en dos pasos activa, `POST /auth/login` no devuelve la
 * sesión: responde 403 `segundo-factor-requerido` con un desafío de corta
 * vida. Esta pieza lo recoge y pide el código:
 *
 *   - el de la aplicación de autenticación (6 dígitos, con
 *     `autocomplete="one-time-code"` para que el teléfono lo proponga), o
 *   - «Usar un código de recuperación», uno de los que se guardaron al
 *     activarla. Entrar con uno de ellos lo gasta, y se dice cuántos quedan
 *     antes de seguir.
 *
 * Si el rol de la cuenta exige el segundo factor y no lo tiene (403
 * `segundo-factor-enrolamiento-requerido`), en vez de pedir el código guía
 * la activación ahí mismo —clave para la aplicación, código, códigos de
 * recuperación— y solo entonces entra.
 *
 * El desafío vive en una variable de este módulo: nunca en la dirección, ni
 * en `localStorage` ni en `sessionStorage`. Al volver al primer paso se
 * olvida, y con él la clave y los códigos que hubiera en pantalla.
 *
 * @module cuentas/login-segundo-paso
 */

import { tipoDelProblema } from '../comun/codigo-de-correo.js';
import { limpiarAviso, pintarAviso } from '../comun/ui/aviso.js';
import { conCarga } from '../comun/ui/boton.js';
import { marcarErrorDe } from '../comun/ui/campo.js';
import { vaciar } from '../comun/ui/dom.js';
import { formularioListo } from '../comun/ui/formulario-seguro.js';
import {
  PROBLEMAS_DEL_SEGUNDO_FACTOR,
  activarConDesafio,
  canjearDesafio,
  copiarTexto,
  descargarTexto,
  enrolarConDesafio,
  motivoDelCodigo,
  motivoDelCodigoDeRecuperacion,
  pintarCodigosDeRecuperacion,
  pintarSecreto,
  rechazoDelSegundoFactor,
} from './segundo-factor.js';

/**
 * El desafío de un rechazo del login, si lo es: 403 con uno de los dos `type`
 * del segundo factor y un `desafio` que canjear. Cualquier otra cosa (un 403
 * de cuenta suspendida, un cuerpo sin desafío) sigue siendo un rechazo.
 *
 * @param {number} estado
 * @param {unknown} cuerpo
 * @returns {{tipo: string, desafio: string, expiraEn: string|null}|null}
 */
export function desafioDelRechazo(estado, cuerpo) {
  if (estado !== 403 || !cuerpo || typeof cuerpo !== 'object') {
    return null;
  }
  const tipo = tipoDelProblema(cuerpo);
  if (
    tipo !== PROBLEMAS_DEL_SEGUNDO_FACTOR.REQUERIDO &&
    tipo !== PROBLEMAS_DEL_SEGUNDO_FACTOR.ENROLAMIENTO_REQUERIDO
  ) {
    return null;
  }
  const { desafio, expiraEn } = /** @type {{desafio?: unknown, expiraEn?: unknown}} */ (cuerpo);
  if (typeof desafio !== 'string' || !desafio) {
    return null;
  }
  return { tipo, desafio, expiraEn: typeof expiraEn === 'string' ? expiraEn : null };
}

/**
 * Vacía un campo. Aparte, como `olvidarClave` en preguntas-seguridad.js: tras
 * un rechazo el código escrito se borra a propósito.
 *
 * @param {HTMLInputElement} campo
 */
function borrar(campo) {
  campo.value = '';
}

/**
 * Lo que se dice tras entrar con un código de recuperación.
 *
 * @param {number} restantes
 * @returns {{tono: 'advertencia'|'info', titulo: string, detalle: string}}
 */
export function avisoTrasRecuperacion(restantes) {
  let cuantos = `Te quedan ${restantes}.`;
  if (restantes === 0) {
    cuantos = 'Ya no te queda ninguno.';
  } else if (restantes === 1) {
    cuantos = 'Te queda 1.';
  }
  return {
    tono: restantes < 3 ? 'advertencia' : 'info',
    titulo: 'Entraste con un código de recuperación',
    detalle: `Ese código ya no sirve. ${cuantos} Si perdiste tu teléfono, desactiva la verificación en dos pasos en Mi cuenta > Seguridad y vuelve a activarla con el nuevo.`,
  };
}

/**
 * Monta los dos recorridos sobre las secciones de `login.html`.
 *
 * @param {ParentNode} raiz
 * @param {{
 *   alEntrar: (sesion: object) => (void|Promise<void>),
 *   alVolver?: (rechazo: ReturnType<typeof rechazoDelSegundoFactor>|null) => void,
 *   canjear?: typeof canjearDesafio,
 *   enrolar?: typeof enrolarConDesafio,
 *   activar?: typeof activarConDesafio,
 *   copiar?: typeof copiarTexto,
 *   descargar?: typeof descargarTexto,
 * }} opciones
 * @returns {{verificar: (desafio: {desafio: string}) => void,
 *   enrolar: (desafio: {desafio: string}) => void, volver: () => void}|null}
 *   `null` si la vista no tiene las secciones
 */
export function montarSegundoPaso(
  raiz,
  {
    alEntrar,
    alVolver = () => {},
    canjear = canjearDesafio,
    enrolar = enrolarConDesafio,
    activar = activarConDesafio,
    copiar = copiarTexto,
    descargar = descargarTexto,
  },
) {
  const verificacion = raiz.querySelector('[data-zona="segundo-paso"]');
  const enrolamiento = raiz.querySelector('[data-zona="enrolamiento-obligatorio"]');
  if (!verificacion || !enrolamiento) {
    return null;
  }

  // ------------------------------------------------ el código en el acceso
  const zonaVerificacion = verificacion.querySelector('[data-zona="rechazo-segundo-paso"]');
  const explicacion = verificacion.querySelector('[data-zona="explicacion-segundo-paso"]');
  const formVerificacion = verificacion.querySelector('form');
  const campoAplicacion = formVerificacion.querySelector('[name="codigo"]');
  const campoRecuperacion = formVerificacion.querySelector('[name="codigoRecuperacion"]');
  const cajaAplicacion = formVerificacion.querySelector('[data-modo="aplicacion"]');
  const cajaRecuperacion = formVerificacion.querySelector('[data-modo="recuperacion"]');
  const botonVerificar = formVerificacion.querySelector('button[type="submit"]');
  const botonModo = verificacion.querySelector('[data-accion="usar-recuperacion"]');
  const alternativas = verificacion.querySelector('[data-zona="alternativas-segundo-paso"]');

  // ------------------------------------------- el enrolamiento obligatorio
  const zonaEnrolamiento = enrolamiento.querySelector('[data-zona="rechazo-enrolamiento"]');
  const tituloEnrolamiento = enrolamiento.querySelector('h2');
  const inicio = enrolamiento.querySelector('[data-zona="inicio-enrolamiento"]');
  const botonGenerar = enrolamiento.querySelector('[data-accion="generar-clave"]');
  const zonaClave = enrolamiento.querySelector('[data-zona="clave-obligatoria"]');
  const formEnrolamiento = enrolamiento.querySelector('form');
  const campoEnrolamiento = formEnrolamiento.querySelector('[name="codigo"]');
  const botonActivar = formEnrolamiento.querySelector('button[type="submit"]');
  const zonaCodigos = enrolamiento.querySelector('[data-zona="codigos-obligatorios"]');

  /** Lo del primer paso que se aparta mientras dura el segundo, con cómo estaba. */
  const apartados = new Map();
  /** @type {string|null} */
  let desafio = null;
  let modo = 'aplicacion';
  let enviando = false;
  /** La cuenta del enrolamiento obligatorio en curso, para el archivo de códigos. */
  let cuenta = null;

  // Funciones aparte, como `liberarEnvio` en login.js: lo que se cambia tras
  // la respuesta del servidor se cambia a propósito (require-atomic-updates).
  function soltarEnvio() {
    enviando = false;
  }

  function olvidarDesafio() {
    desafio = null;
  }

  function apartarPrimerPaso() {
    for (const elemento of raiz.querySelectorAll('[data-paso="primero"]')) {
      if (!apartados.has(elemento)) {
        apartados.set(elemento, elemento.hidden);
      }
      elemento.hidden = true;
    }
  }

  function devolverPrimerPaso() {
    for (const [elemento, oculto] of apartados) {
      elemento.hidden = oculto;
    }
    apartados.clear();
  }

  function ponerModo(nuevo) {
    modo = nuevo;
    const recuperacion = modo === 'recuperacion';
    cajaAplicacion.hidden = recuperacion;
    cajaRecuperacion.hidden = !recuperacion;
    campoAplicacion.value = '';
    campoRecuperacion.value = '';
    marcarErrorDe(campoAplicacion, null);
    marcarErrorDe(campoRecuperacion, null);
    botonModo.textContent = recuperacion
      ? 'Usar el código de mi aplicación'
      : 'Usar un código de recuperación';
    explicacion.textContent = recuperacion
      ? 'Escribe uno de los códigos de recuperación que guardaste al activar la verificación. Cada uno sirve una sola vez.'
      : 'Escribe el código de 6 dígitos que muestra ahora tu aplicación de autenticación.';
  }

  /** Todo lo de los dos recorridos fuera de la pantalla y de la memoria. */
  function limpiarTodo() {
    desafio = null;
    cuenta = null;
    enviando = false;
    limpiarAviso(zonaVerificacion);
    limpiarAviso(zonaEnrolamiento);
    ponerModo('aplicacion');
    conCarga(botonVerificar, false);
    formVerificacion.hidden = false;
    alternativas.hidden = false;
    vaciar(zonaClave);
    zonaClave.hidden = true;
    vaciar(zonaCodigos);
    zonaCodigos.hidden = true;
    campoEnrolamiento.value = '';
    marcarErrorDe(campoEnrolamiento, null);
    conCarga(botonActivar, false);
    conCarga(botonGenerar, false);
    formEnrolamiento.hidden = true;
    inicio.hidden = false;
    verificacion.hidden = true;
    enrolamiento.hidden = true;
  }

  /** @param {ReturnType<typeof rechazoDelSegundoFactor>|null} [rechazo] */
  function volver(rechazo = null) {
    limpiarTodo();
    devolverPrimerPaso();
    alVolver(rechazo);
  }

  /** Entra, con la sesión ya emitida. El botón se queda apagado: la página se va. */
  async function entrar(sesion) {
    desafio = null;
    await alEntrar(sesion);
  }

  // ----------------------------------------------------------- verificar

  function verificar({ desafio: valor }) {
    limpiarTodo();
    desafio = valor;
    apartarPrimerPaso();
    verificacion.hidden = false;
    campoAplicacion.focus();
  }

  botonModo.addEventListener('click', () => {
    limpiarAviso(zonaVerificacion);
    ponerModo(modo === 'aplicacion' ? 'recuperacion' : 'aplicacion');
    (modo === 'aplicacion' ? campoAplicacion : campoRecuperacion).focus();
  });

  formVerificacion.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (enviando || !desafio) {
      return;
    }
    limpiarAviso(zonaVerificacion);
    const recuperacion = modo === 'recuperacion';
    const campo = recuperacion ? campoRecuperacion : campoAplicacion;
    marcarErrorDe(campo, null);
    const motivo = recuperacion
      ? motivoDelCodigoDeRecuperacion(campo.value)
      : motivoDelCodigo(campo.value);
    if (motivo) {
      marcarErrorDe(campo, motivo);
      campo.focus();
      return;
    }

    enviando = true;
    conCarga(botonVerificar, true, 'Verificando…');
    let sesion;
    try {
      sesion = await canjear(
        recuperacion
          ? { desafio, codigoRecuperacion: campo.value }
          : { desafio, codigo: campo.value },
      );
    } catch (error) {
      soltarEnvio();
      conCarga(botonVerificar, false);
      const rechazo = rechazoDelSegundoFactor(error, { contexto: 'acceso' });
      if (rechazo.volverAEmpezar) {
        volver(rechazo);
        return;
      }
      pintarAviso(zonaVerificacion, rechazo);
      if (rechazo.campo === 'codigo') {
        borrar(campo);
        marcarErrorDe(campo, 'Ese código no sirvió.');
        campo.focus();
      } else {
        zonaVerificacion.focus();
      }
      return;
    }

    if (typeof sesion?.codigosRecuperacionRestantes === 'number') {
      // Gastó un código de recuperación: se dice cuántos quedan antes de
      // seguir, que es justo cuando alguien que perdió el teléfono lo necesita.
      formVerificacion.hidden = true;
      alternativas.hidden = true;
      const caja = pintarAviso(zonaVerificacion, {
        ...avisoTrasRecuperacion(sesion.codigosRecuperacionRestantes),
        accion: {
          texto: 'Continuar',
          nombre: 'continuar-tras-recuperacion',
          alPulsar: () => entrar(sesion),
        },
      });
      caja.querySelector('[data-accion="continuar-tras-recuperacion"]')?.focus();
      return;
    }
    await entrar(sesion);
  });

  // ---------------------------------------------- enrolamiento obligatorio

  function enrolarObligatorio({ desafio: valor }) {
    limpiarTodo();
    desafio = valor;
    apartarPrimerPaso();
    enrolamiento.hidden = false;
    tituloEnrolamiento.focus();
  }

  botonGenerar.addEventListener('click', async () => {
    if (!desafio) {
      return;
    }
    limpiarAviso(zonaEnrolamiento);
    conCarga(botonGenerar, true, 'Generando la clave…');
    let datos;
    try {
      datos = await enrolar(desafio);
    } catch (error) {
      conCarga(botonGenerar, false);
      const rechazo = rechazoDelSegundoFactor(error, { contexto: 'activacion' });
      if (rechazo.volverAEmpezar) {
        volver(rechazo);
        return;
      }
      pintarAviso(zonaEnrolamiento, rechazo);
      zonaEnrolamiento.focus();
      return;
    }
    conCarga(botonGenerar, false);
    cuenta = datos.cuenta ?? null;
    inicio.hidden = true;
    pintarSecreto(zonaClave, datos, { copiar });
    formEnrolamiento.hidden = false;
    campoEnrolamiento.focus();
  });

  formEnrolamiento.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (enviando || !desafio) {
      return;
    }
    limpiarAviso(zonaEnrolamiento);
    marcarErrorDe(campoEnrolamiento, null);
    const motivo = motivoDelCodigo(campoEnrolamiento.value);
    if (motivo) {
      marcarErrorDe(campoEnrolamiento, motivo);
      campoEnrolamiento.focus();
      return;
    }

    enviando = true;
    conCarga(botonActivar, true, 'Activando…');
    let sesion;
    try {
      sesion = await activar({ desafio, codigo: campoEnrolamiento.value });
    } catch (error) {
      soltarEnvio();
      conCarga(botonActivar, false);
      const rechazo = rechazoDelSegundoFactor(error, { contexto: 'activacion' });
      if (rechazo.volverAEmpezar) {
        volver(rechazo);
        return;
      }
      pintarAviso(zonaEnrolamiento, rechazo);
      if (rechazo.campo === 'codigo') {
        borrar(campoEnrolamiento);
        marcarErrorDe(campoEnrolamiento, 'Ese código no sirvió. Escribe el que se ve ahora.');
        campoEnrolamiento.focus();
      } else {
        zonaEnrolamiento.focus();
      }
      return;
    }

    // Activada y con sesión: antes de entrar, los códigos de recuperación,
    // que no se vuelven a mostrar. La clave sale de la pantalla.
    olvidarDesafio();
    vaciar(zonaClave);
    zonaClave.hidden = true;
    borrar(campoEnrolamiento);
    formEnrolamiento.hidden = true;
    pintarCodigosDeRecuperacion(zonaCodigos, sesion.codigosRecuperacion ?? [], {
      cuenta,
      copiar,
      descargar,
      textoDelBoton: 'Ya los guardé, entrar',
      alTerminar: () => alEntrar(sesion),
    });
  });

  for (const boton of raiz.querySelectorAll('[data-accion="volver-al-primer-paso"]')) {
    boton.addEventListener('click', () => volver(null));
  }

  for (const formulario of [formVerificacion, formEnrolamiento]) {
    formulario.addEventListener('input', (evento) => {
      const control = evento.target;
      if (control instanceof HTMLElement && control.getAttribute('aria-invalid') === 'true') {
        marcarErrorDe(control, null);
      }
    });
    // G1 — la vista ya escucha `submit`: los botones se pueden encender.
    formularioListo(formulario);
  }

  limpiarTodo();
  return {
    verificar,
    enrolar: enrolarObligatorio,
    volver: () => volver(null),
  };
}
