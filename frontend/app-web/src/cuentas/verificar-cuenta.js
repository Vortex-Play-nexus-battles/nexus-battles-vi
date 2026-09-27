/**
 * «Confirma tu correo» — B1 (identidad 2.0.0; 7.4.12 «confirmar la creación
 * de una cuenta de usuario»).
 *
 * Una cuenta de autorregistro nace pendiente de verificar su correo, y el
 * login la rechaza (403 `cuenta-no-verificada`) hasta que alguien escribe el
 * código que llegó al buzón. Así, y solo así, se demuestra que el buzón es de
 * quien se registró: nadie pregunta a ningún servidor de correo si la
 * dirección existe.
 *
 * Se llega de tres sitios, y cada uno cambia lo primero que se lee:
 *
 *   - del registro (`?motivo=registro`): el código acaba de salir;
 *   - del login (`?motivo=login` o `reenviado`): la cuenta existe y no está
 *     verificada, o se acaba de pedir otro código;
 *   - del enlace del correo (`#codigo=...&correo=...`): la vista rellena los
 *     dos campos, borra el fragmento de la barra y espera un clic. No se
 *     confirma sola a propósito: los antivirus de correo abren los enlaces
 *     para analizarlos, y una verificación que se hiciera al cargar la
 *     página la podría hacer una máquina.
 *
 * El correo nunca va en la dirección: viaja en `sessionStorage`
 * (`comun/codigo-de-correo.js`).
 *
 * Al confirmar, al login con el correo ya escrito y `?motivo=verificada`: el
 * login dice que el correo quedó verificado y, al entrar, lleva a «Preparando
 * tu cuenta», porque el alta del jugador empieza justo al verificar.
 *
 * @module cuentas/verificar-cuenta
 */

import { montarCabecera } from '../comun/cabecera-app.js';
import {
  CLAVES_DEL_CORREO,
  LONGITUD_DEL_CODIGO,
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
import { MOTIVOS, MOTIVOS_DE_VERIFICACION, urlDeLogin } from '../comun/sesion.js';
import { limpiarAviso, pintarAviso, tonoPorEstado } from '../comun/ui/aviso.js';
import { conCarga } from '../comun/ui/boton.js';
import { marcarErrorDe } from '../comun/ui/campo.js';
import {
  MENSAJE_DE_REENVIO,
  anotarEnvio,
  confirmarCorreo,
  reenviarCodigo,
  segundosParaReenviar,
} from '../comun/verificacion.js';

/** Texto del botón de reenvío cuando se puede pulsar. */
export const TEXTO_REENVIAR = 'Reenviar código';

/**
 * Lo primero que se lee en la tarjeta, según por qué se llega.
 *
 * @param {string|null} motivo el `?motivo=` de la dirección, o `enlace`
 * @param {string} correo el que se conoce (puede estar vacío)
 * @returns {string}
 */
export function introduccion(motivo, correo) {
  switch (motivo) {
    case MOTIVOS_DE_VERIFICACION.REGISTRO:
      return correo
        ? `Te enviamos un código de ${LONGITUD_DEL_CODIGO} caracteres a ${correo}. Escríbelo aquí para activar tu cuenta.`
        : `Te enviamos un código de ${LONGITUD_DEL_CODIGO} caracteres a tu correo. Escríbelo aquí para activar tu cuenta.`;
    case MOTIVOS_DE_VERIFICACION.LOGIN:
      return 'Tu cuenta todavía no está activa: escribe el código que te enviamos al registrarte, o pide uno nuevo.';
    case MOTIVOS_DE_VERIFICACION.REENVIADO:
      return 'Escribe el código más reciente que te llegue: los anteriores dejan de servir.';
    case 'enlace':
      return 'Comprueba el correo y el código que trae el enlace, y pulsa «Confirmar».';
    default:
      return 'Escribe el correo con el que te registraste y el código que te enviamos.';
  }
}

/**
 * Texto del botón de reenvío mientras hay que esperar.
 *
 * @param {number} segundos
 * @returns {string}
 */
export function textoDeEspera(segundos) {
  return `${TEXTO_REENVIAR} (${segundos} s)`;
}

/**
 * Monta la verificación sobre el marcado de `verificar-cuenta.html`. Todo lo
 * que toca el mundo exterior se puede inyectar, para probarlo con jsdom.
 *
 * @param {ParentNode} raiz
 * @param {{
 *   confirmar?: typeof confirmarCorreo,
 *   reenviar?: typeof reenviarCodigo,
 *   almacen?: Storage,
 *   ubicacion?: Location,
 *   historial?: History,
 *   reloj?: {setTimeout: Function, clearTimeout: Function},
 *   ahora?: () => number,
 *   navegar?: (url: string) => void,
 *   base?: string,
 * }} [opciones]
 * @returns {{detener: () => void}}
 */
export function montarVerificacion(
  raiz,
  {
    confirmar = confirmarCorreo,
    reenviar = reenviarCodigo,
    almacen = globalThis.sessionStorage,
    ubicacion = globalThis.location,
    historial = globalThis.history,
    reloj = globalThis,
    ahora = () => Date.now(),
    navegar = (url) => {
      globalThis.location.assign(url);
    },
    base,
  } = {},
) {
  const zona = (nombre) => raiz.querySelector(`[data-zona="${nombre}"]`);
  const formulario = raiz.querySelector('#formVerificacion');
  const campoCorreo = raiz.querySelector('#email');
  const campoCodigo = raiz.querySelector('#codigo');
  const botonConfirmar = raiz.querySelector('[data-accion="confirmar"]');
  const botonReenviar = raiz.querySelector('[data-accion="reenviar"]');
  const zonaAviso = zona('aviso');
  const zonaEstado = zona('estado');
  const zonaIntroduccion = zona('introduccion');

  // ------------------------------------------------------ lo que ya se sabe
  const motivo = new URLSearchParams(ubicacion?.search ?? '').get('motivo');
  const delEnlace = leerEnlaceDelCorreo(ubicacion?.hash);
  if (delEnlace) {
    // Leído una vez y fuera de la barra: que no quede en el historial.
    limpiarFragmento({ historial, ubicacion });
  }
  const correoConocido =
    delEnlace?.correo || recordado(CLAVES_DEL_CORREO.porVerificar, almacen) || '';
  campoCorreo.value = correoConocido;
  if (delEnlace?.codigo) {
    campoCodigo.value = delEnlace.codigo;
  }
  zonaIntroduccion.textContent = introduccion(
    delEnlace?.codigo ? 'enlace' : motivo,
    correoConocido,
  );

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

  function limpiarMarcas() {
    marcarErrorDe(campoCorreo, null);
    marcarErrorDe(campoCodigo, null);
  }

  /** El correo del formulario, o `null` tras marcarlo si no sirve. */
  function correoValido(motivoSiFalta) {
    const email = campoCorreo.value.trim();
    if (!email || !campoCorreo.checkValidity()) {
      marcarErrorDe(campoCorreo, motivoSiFalta);
      campoCorreo.focus();
      return null;
    }
    return email;
  }

  // ------------------------------------------------ espera entre reenvíos
  let temporizador = null;
  let enviando = false;

  function pintarEspera() {
    reloj.clearTimeout(temporizador);
    temporizador = null;
    if (enviando) {
      return;
    }
    const segundos = segundosParaReenviar(almacen, ahora());
    if (segundos > 0) {
      botonReenviar.disabled = true;
      botonReenviar.textContent = textoDeEspera(segundos);
      temporizador = reloj.setTimeout(pintarEspera, 1000);
    } else {
      botonReenviar.disabled = false;
      botonReenviar.textContent = TEXTO_REENVIAR;
    }
  }

  // ------------------------------------------------------------- confirmar
  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    limpiarMarcas();

    const email = correoValido('Escribe el correo con el que te registraste.');
    if (!email) {
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

    conCarga(botonConfirmar, true, 'Confirmando…');
    decir('Comprobando el código…');

    let resultado;
    try {
      resultado = await confirmar({ email, codigo });
    } catch {
      decir('');
      conCarga(botonConfirmar, false);
      avisar(
        {
          tono: 'error',
          titulo: 'No pudimos comprobar el código',
          detalle: 'La conexión falló. Inténtalo de nuevo en unos segundos.',
        },
        { enfocar: true },
      );
      return;
    }

    if (resultado.ok) {
      // El login traerá el correo escrito; lo de esta verificación ya sobra.
      recordar(CLAVE_CORREO_REGISTRADO, email, almacen);
      recordar(CLAVES_DEL_CORREO.porVerificar, null, almacen);
      recordar(CLAVES_DEL_CORREO.ultimoEnvio, null, almacen);
      decir('¡Correo verificado! Te llevamos a la entrada…');
      navegar(urlDeLogin({ motivo: MOTIVOS.VERIFICADA }, base));
      return;
    }

    decir('');
    conCarga(botonConfirmar, false);
    const rechazo = rechazoDelCodigo(resultado.tipo, resultado.estado);
    if (rechazo) {
      avisar({
        tono: tonoPorEstado(resultado.estado),
        titulo: rechazo.titulo,
        detalle: rechazo.detalle,
      });
      if (resultado.tipo === PROBLEMAS_DEL_CODIGO.CODIGO_INVALIDO) {
        marcarErrorDe(campoCodigo, 'Revisa el código o pide uno nuevo.');
        campoCodigo.focus();
      } else if (!botonReenviar.disabled && rechazo.pedirOtro) {
        botonReenviar.focus();
      } else {
        zonaAviso.focus();
      }
      return;
    }

    const caido = !resultado.estado || resultado.estado >= 500;
    avisar(
      {
        tono: tonoPorEstado(resultado.estado),
        titulo: caido ? 'No pudimos comprobar el código' : 'Revisa el correo y el código',
        detalle: caido
          ? 'El servicio no respondió bien. Inténtalo de nuevo en unos minutos.'
          : (resultado.detalle ?? 'Revisa que el correo y el código estén bien escritos.'),
      },
      { enfocar: true },
    );
  });

  // -------------------------------------------------------------- reenviar
  botonReenviar.addEventListener('click', async () => {
    limpiarAviso(zonaAviso);
    limpiarMarcas();
    const email = correoValido('Escribe el correo al que tenemos que enviar el código.');
    if (!email || segundosParaReenviar(almacen, ahora()) > 0) {
      return;
    }

    enviando = true;
    botonReenviar.disabled = true;
    botonReenviar.setAttribute('aria-busy', 'true');
    botonReenviar.textContent = 'Enviando…';

    let resultado = null;
    try {
      resultado = await reenviar(email);
    } catch {
      resultado = null;
    }
    enviando = false;
    botonReenviar.removeAttribute('aria-busy');

    if (resultado?.ok) {
      recordar(CLAVES_DEL_CORREO.porVerificar, email, almacen);
      anotarEnvio(almacen, ahora());
      avisar({
        tono: 'info',
        titulo: resultado.mensaje,
        detalle: 'Si no lo ves en unos minutos, revisa la carpeta de correo no deseado.',
      });
      campoCodigo.value = '';
      pintarEspera();
      campoCodigo.focus();
      return;
    }
    pintarEspera();
    if (resultado?.estado === 429) {
      avisar(
        {
          tono: 'advertencia',
          titulo: 'Demasiadas solicitudes seguidas',
          detalle: 'Espera un momento antes de pedir otro código.',
        },
        { enfocar: true },
      );
      return;
    }
    avisar(
      {
        tono: 'error',
        titulo: 'No pudimos pedir otro código',
        detalle: 'Inténtalo de nuevo en unos segundos.',
      },
      { enfocar: true },
    );
  });

  // ------------------------------------------------------ campos al vuelo
  for (const campo of [campoCorreo, campoCodigo]) {
    campo.addEventListener('input', () => {
      if (campo.getAttribute('aria-invalid') === 'true') {
        marcarErrorDe(campo, null);
      }
    });
  }

  // Si se pega el enlace entero del correo en el campo del código, se saca
  // de él el código (y el correo, si todavía no estaba escrito).
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

  // ------------------------------------------------------------- arranque
  pintarEspera();
  if (delEnlace?.codigo && correoConocido) {
    botonConfirmar.focus();
  } else if (motivo === MOTIVOS_DE_VERIFICACION.REENVIADO) {
    avisar({
      tono: 'info',
      titulo: MENSAJE_DE_REENVIO,
      detalle: 'Si no lo ves en unos minutos, revisa la carpeta de correo no deseado.',
    });
    campoCodigo.focus();
  } else if (correoConocido) {
    campoCodigo.focus();
  } else {
    campoCorreo.focus();
  }

  return {
    detener() {
      reloj.clearTimeout(temporizador);
      temporizador = null;
    },
  };
}

// ---------------------------------------------------------------- arranque

function arrancar() {
  montarCabecera(document.querySelector('[data-cabecera-app]'), {
    vista: 'verificar-cuenta',
    seccionActiva: null,
  });
  montarVerificacion(document);
}

if (document.body?.dataset.vista === 'verificar-cuenta') {
  arrancar();
}
