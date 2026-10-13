// restablecer-confirmar.js
// «Restablecer contraseña», paso 2 — HU-COR-003 y activación de una cuenta
// creada por un Super Administrador (HU-USR-002); B1 (identidad 2.0.0, 7.1.1).
//
// 7.1.1: «El usuario puede recuperar su cuenta contestando preguntas con
// respuestas previamente configuradas y el código enviado a su correo
// electrónico». Dos pasos en la misma tarjeta:
//
//   1. correo y código → `POST /auth/restablecer/preguntas` devuelve las
//      preguntas que la cuenta configuró (ninguna: basta el código);
//   2. una respuesta por pregunta y la contraseña nueva →
//      `POST /auth/restablecer/confirmar`.
//
// Se llega también desde el enlace del correo, que trae el código y el correo
// en el fragmento (`/restablecer#codigo=...&correo=...`): la vista los lee,
// los borra de la barra y espera un clic en «Continuar», igual que la
// verificación del correo y por lo mismo.
//
// Los rechazos se explican por su `type` (`codigo-invalido`,
// `demasiados-intentos`, `respuestas-incorrectas`,
// `contrasena-no-cumple-politica`), nunca por el texto.
//
// Las dos llamadas viven en `comun/recuperacion.js`; aquí solo la pantalla.

import { montarCabecera } from '../comun/cabecera-app.js';
import {
  CLAVES_DEL_CORREO,
  PROBLEMAS_DEL_CODIGO,
  leerEnlaceDelCorreo,
  limpiarFragmento,
  motivoDelCodigo,
  normalizarCodigo,
  rechazoDelCodigo,
  recordado,
  recordar,
} from '../comun/codigo-de-correo.js';
import { CLAVE_CORREO_REGISTRADO } from '../comun/entrada.js';
import { motivoDeContrasena } from '../comun/politica-contrasena.js';
import {
  FalloDeRecuperacion,
  PROBLEMAS_DE_RECUPERACION,
  confirmarRestablecimiento,
  consultarPreguntas,
} from '../comun/recuperacion.js';
import { MOTIVOS, urlDeLogin } from '../comun/sesion.js';
import { limpiarAviso, pintarAviso, tonoPorEstado } from '../comun/ui/aviso.js';
import { conCarga } from '../comun/ui/boton.js';
import { campo, marcarErrorDe, mejorarContrasena } from '../comun/ui/campo.js';
import { vaciar } from '../comun/ui/dom.js';
import { formularioListo, sinCredencialesEnLaDireccion } from '../comun/ui/formulario-seguro.js';

// Se reexportan con su nombre de siempre: las llamadas viven en `comun/`.
export { confirmarRestablecimiento, consultarPreguntas };

/** Largo máximo de una respuesta (contrato: `RespuestaDeSeguridad.respuesta`). */
const RESPUESTA_MAXIMA = 100;

/**
 * Qué decir cuando el canje (o la consulta de preguntas) no sale, por su
 * `type`. `null` en `campo` es que no hay un campo concreto que señalar.
 *
 * @param {unknown} error lo que lanzó la llamada
 * @returns {{tono: 'error'|'advertencia'|'info', titulo: string, detalle: string,
 *   campo: 'codigo'|'respuestas'|'nuevaPassword'|null, volverAlCodigo: boolean}}
 */
export function rechazoDelCanje(error) {
  if (!(error instanceof FalloDeRecuperacion)) {
    return {
      tono: 'error',
      titulo: 'No pudimos conectar con el servidor',
      detalle: 'Inténtalo de nuevo en unos segundos.',
      campo: null,
      volverAlCodigo: false,
    };
  }
  const tono = tonoPorEstado(error.estado);
  const delCodigo = rechazoDelCodigo(error.tipo, error.estado);
  if (delCodigo && error.tipo) {
    return {
      tono,
      titulo: delCodigo.titulo,
      detalle: delCodigo.detalle,
      campo: error.tipo === PROBLEMAS_DEL_CODIGO.CODIGO_INVALIDO ? 'codigo' : null,
      volverAlCodigo: true,
    };
  }
  if (error.tipo === PROBLEMAS_DE_RECUPERACION.RESPUESTAS_INCORRECTAS) {
    return {
      tono,
      titulo: 'Alguna respuesta no coincide',
      detalle:
        'Contéstalas como las escribiste al configurarlas. Cada intento fallido cuenta: tras cinco, el código se anula.',
      campo: 'respuestas',
      volverAlCodigo: false,
    };
  }
  if (error.tipo === PROBLEMAS_DE_RECUPERACION.CONTRASENA_NO_CUMPLE_POLITICA) {
    return {
      tono,
      titulo: 'La contraseña nueva no cumple la política',
      detalle:
        error.detalle ??
        'Debe tener más de 8 caracteres, con mayúscula, minúscula, número y símbolo.',
      campo: 'nuevaPassword',
      volverAlCodigo: false,
    };
  }
  if (delCodigo) {
    // 429 sin `type`: el límite del borde, no el del código.
    return { tono, ...delCodigo, campo: null, volverAlCodigo: false };
  }
  if (!error.estado || error.estado >= 500) {
    return {
      tono: 'error',
      titulo: 'No pudimos completar la operación',
      detalle: 'El servicio no respondió bien. Inténtalo de nuevo en unos minutos.',
      campo: null,
      volverAlCodigo: false,
    };
  }
  return {
    tono,
    titulo: 'Revisa los datos',
    detalle: error.detalle ?? 'Revisa el correo, el código y la contraseña nueva.',
    campo: null,
    volverAlCodigo: false,
  };
}

/**
 * Monta la vista sobre el marcado de `restablecer-confirmar.html`. Todo lo que
 * toca el mundo exterior se puede inyectar, para probarlo con jsdom.
 *
 * @param {ParentNode} raiz
 * @param {{
 *   consultar?: typeof consultarPreguntas,
 *   confirmar?: typeof confirmarRestablecimiento,
 *   almacen?: Storage,
 *   ubicacion?: Location,
 *   historial?: History,
 *   navegar?: (url: string) => void,
 *   base?: string,
 * }} [opciones]
 */
export function montarRestablecimiento(
  raiz,
  {
    consultar = consultarPreguntas,
    confirmar = confirmarRestablecimiento,
    almacen = globalThis.sessionStorage,
    ubicacion = globalThis.location,
    historial = globalThis.history,
    navegar = (url) => {
      globalThis.location.assign(url);
    },
    base,
  } = {},
) {
  const zona = (nombre) => raiz.querySelector(`[data-zona="${nombre}"]`);
  const formCodigo = raiz.querySelector('#formCodigo');
  const formClave = raiz.querySelector('#formClave');
  const campoCorreo = raiz.querySelector('#email');
  const campoCodigo = raiz.querySelector('#codigo');
  const campoClave = raiz.querySelector('#nuevaPassword');
  const campoConfirmacion = raiz.querySelector('#confirmarPassword');
  const botonContinuar = raiz.querySelector('[data-accion="continuar"]');
  const botonGuardar = raiz.querySelector('[data-accion="guardar"]');
  const botonOtroCodigo = raiz.querySelector('[data-accion="otro-codigo"]');
  const zonaAviso = zona('aviso');
  const zonaEstado = zona('estado');
  const zonaIntroduccion = zona('introduccion');
  const zonaResumen = zona('resumen-codigo');
  const zonaPreguntas = zona('preguntas');
  const listaPreguntas = zona('lista-preguntas');

  /** Lo que el paso 1 dejó aceptado. */
  let aceptado = null;
  /** @type {Array<{id: string, control: HTMLInputElement, marcarError: Function}>} */
  let respuestas = [];

  // ------------------------------------------------------ lo que ya se sabe
  const delEnlace = leerEnlaceDelCorreo(ubicacion?.hash);
  if (delEnlace) {
    limpiarFragmento({ historial, ubicacion });
  }
  campoCorreo.value = delEnlace?.correo || recordado(CLAVES_DEL_CORREO.recuperacion, almacen) || '';
  if (delEnlace?.codigo) {
    campoCodigo.value = delEnlace.codigo;
    zonaIntroduccion.textContent =
      'Comprueba el correo y el código que trae el enlace, y pulsa «Continuar».';
  }

  // ------------------------------------------------------------ utilidades
  function decir(texto) {
    zonaEstado.textContent = texto;
    zonaEstado.hidden = !texto;
  }

  function avisar(opciones, { enfocar = false } = {}) {
    pintarAviso(zonaAviso, opciones);
    if (enfocar) {
      zonaAviso.focus();
    }
  }

  function volverAlCodigo() {
    aceptado = null;
    respuestas = [];
    vaciar(listaPreguntas);
    zonaPreguntas.hidden = true;
    formClave.hidden = true;
    formCodigo.hidden = false;
    zonaIntroduccion.textContent = 'Escribe el correo de tu cuenta y el código que te enviamos.';
  }

  function pintarPreguntas(preguntas) {
    vaciar(listaPreguntas);
    respuestas = preguntas.map((pregunta, indice) => {
      const { elemento, control, marcarError } = campo({
        nombre: `respuesta-${indice + 1}`,
        etiqueta: pregunta.texto,
        requerido: true,
        autocompletar: 'off',
        atributos: {
          maxlength: RESPUESTA_MAXIMA,
          spellcheck: 'false',
          autocapitalize: 'off',
          'data-pregunta-id': pregunta.id,
        },
      });
      control.addEventListener('input', () => marcarError(null));
      listaPreguntas.append(elemento);
      return { id: pregunta.id, control, marcarError };
    });
    zonaPreguntas.hidden = respuestas.length === 0;
  }

  /**
   * Explica un rechazo y deja el foco donde se puede arreglar.
   *
   * @param {unknown} error
   */
  function mostrarRechazo(error) {
    const rechazo = rechazoDelCanje(error);
    if (rechazo.volverAlCodigo) {
      volverAlCodigo();
    }
    avisar({ tono: rechazo.tono, titulo: rechazo.titulo, detalle: rechazo.detalle });
    if (rechazo.campo === 'codigo') {
      marcarErrorDe(campoCodigo, 'Revisa el código o pide uno nuevo.');
      campoCodigo.focus();
    } else if (rechazo.campo === 'respuestas' && respuestas.length > 0) {
      respuestas[0].control.focus();
    } else if (rechazo.campo === 'nuevaPassword') {
      marcarErrorDe(campoClave, rechazo.detalle);
      campoClave.focus();
    } else {
      zonaAviso.focus();
    }
  }

  // ------------------------------------------------------ paso 1: el código
  formCodigo.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    marcarErrorDe(campoCorreo, null);
    marcarErrorDe(campoCodigo, null);

    const email = campoCorreo.value.trim();
    if (!email || !campoCorreo.checkValidity()) {
      marcarErrorDe(campoCorreo, 'Escribe el correo de tu cuenta.');
      campoCorreo.focus();
      return;
    }
    const codigo = normalizarCodigo(campoCodigo.value);
    const motivoLocal = motivoDelCodigo(codigo);
    if (motivoLocal) {
      marcarErrorDe(campoCodigo, motivoLocal);
      campoCodigo.focus();
      return;
    }
    campoCodigo.value = codigo;

    conCarga(botonContinuar, true, 'Comprobando…');
    decir('Comprobando el código…');
    let preguntas;
    try {
      ({ preguntas } = await consultar({ email, codigo }));
    } catch (error) {
      decir('');
      mostrarRechazo(error);
      return;
    } finally {
      conCarga(botonContinuar, false);
    }
    decir('');

    aceptado = { email, codigo };
    recordar(CLAVES_DEL_CORREO.recuperacion, email, almacen);
    pintarPreguntas(preguntas);
    zonaResumen.textContent = `Código aceptado para ${email}.`;
    zonaIntroduccion.textContent =
      respuestas.length > 0
        ? 'Contesta tus preguntas de seguridad y elige tu contraseña nueva.'
        : 'Elige tu contraseña nueva.';
    formCodigo.hidden = true;
    formClave.hidden = false;
    (respuestas[0]?.control ?? campoClave).focus();
  });

  // --------------------------------------- paso 2: respuestas y contraseña
  function vaciarClaves() {
    campoClave.value = '';
    campoConfirmacion.value = '';
  }

  function contrasenaValida() {
    const motivo = motivoDeContrasena(campoClave.value);
    marcarErrorDe(campoClave, motivo);
    return motivo === null;
  }

  function confirmacionValida() {
    const coincide = campoClave.value === campoConfirmacion.value;
    marcarErrorDe(campoConfirmacion, coincide ? null : 'Las contraseñas no coinciden.');
    return coincide;
  }

  campoClave.addEventListener('blur', () => {
    if (campoClave.value) {
      contrasenaValida();
    }
  });
  campoClave.addEventListener('input', () => {
    if (
      campoClave.getAttribute('aria-invalid') === 'true' &&
      !motivoDeContrasena(campoClave.value)
    ) {
      marcarErrorDe(campoClave, null);
    }
  });
  campoConfirmacion.addEventListener('input', () => {
    if (campoConfirmacion.getAttribute('aria-invalid') === 'true') {
      confirmacionValida();
    }
  });

  formClave.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    if (!aceptado) {
      volverAlCodigo();
      return;
    }

    for (const respuesta of respuestas) {
      const vacia = !respuesta.control.value.trim();
      respuesta.marcarError(vacia ? 'Escribe tu respuesta.' : null);
    }
    const sinContestar = respuestas.find((respuesta) => !respuesta.control.value.trim());
    if (sinContestar) {
      sinContestar.control.focus();
      return;
    }
    if (!contrasenaValida()) {
      campoClave.focus();
      return;
    }
    if (!confirmacionValida()) {
      campoConfirmacion.focus();
      return;
    }

    conCarga(botonGuardar, true, 'Guardando…');
    decir('Guardando tu contraseña nueva…');
    try {
      await confirmar({
        email: aceptado.email,
        codigo: aceptado.codigo,
        nuevaPassword: campoClave.value,
        respuestas: respuestas.map((respuesta) => ({
          preguntaId: respuesta.id,
          respuesta: respuesta.control.value,
        })),
      });
    } catch (error) {
      decir('');
      conCarga(botonGuardar, false);
      mostrarRechazo(error);
      return;
    }

    // El login traerá el correo escrito. La contraseña no se guarda en ningún
    // sitio: se vacía del formulario antes de irse.
    recordar(CLAVE_CORREO_REGISTRADO, aceptado.email, almacen);
    recordar(CLAVES_DEL_CORREO.recuperacion, null, almacen);
    vaciarClaves();
    decir('Contraseña guardada. Te llevamos a la entrada…');
    navegar(urlDeLogin({ motivo: MOTIVOS.RESTABLECIDA }, base));
  });

  botonOtroCodigo.addEventListener('click', () => {
    limpiarAviso(zonaAviso);
    volverAlCodigo();
    campoCodigo.value = '';
    campoCodigo.focus();
  });

  // Si se pega el enlace entero del correo en el campo del código, se saca de
  // él el código (y el correo, si todavía no estaba escrito).
  campoCodigo.addEventListener('change', () => {
    const valor = campoCodigo.value;
    const fragmento = valor.indexOf('#');
    if (fragmento >= 0 && !campoCorreo.value.trim()) {
      const enlace = leerEnlaceDelCorreo(valor.slice(fragmento));
      if (enlace?.correo) {
        campoCorreo.value = enlace.correo;
      }
    }
    campoCodigo.value = normalizarCodigo(valor);
  });
  for (const control of [campoCorreo, campoCodigo]) {
    control.addEventListener('input', () => {
      if (control.getAttribute('aria-invalid') === 'true') {
        marcarErrorDe(control, null);
      }
    });
  }

  // G1 — los dos formularios ya escuchan `submit`: sus botones se pueden
  // encender. Antes del foco de abajo, que no llega a un botón desactivado.
  formularioListo(formCodigo);
  formularioListo(formClave);

  // ------------------------------------------------------------- arranque
  if (delEnlace?.codigo && campoCorreo.value) {
    botonContinuar.focus();
  } else if (campoCorreo.value) {
    campoCodigo.focus();
  } else {
    campoCorreo.focus();
  }
}

// ---------------------------------------------------------------- arranque

if (document.body?.dataset.vista === 'restablecer-confirmar') {
  // G1 — lo que un envío nativo de una versión vieja pudo dejar en la barra
  // (`?codigo=…`, `?nuevaPassword=…`). El fragmento del enlace del correo no
  // se toca: lo lee y lo borra la propia vista.
  sinCredencialesEnLaDireccion();
  montarCabecera(document.querySelector('[data-cabecera-app]'), {
    vista: 'restablecer-confirmar',
    seccionActiva: null,
  });
  // Ver/ocultar la contraseña: el mismo interruptor en las vistas de entrada.
  mejorarContrasena(document.getElementById('nuevaPassword'));
  mejorarContrasena(document.getElementById('confirmarPassword'));
  montarRestablecimiento(document);
}
