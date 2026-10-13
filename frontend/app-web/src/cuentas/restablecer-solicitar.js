// restablecer-solicitar.js
// «Recuperar contraseña», paso 1 — HU-COR-003; B1 (identidad 2.0.0, 7.1.1).
//
// Pide el código de recuperación para un correo. Lo que se dice después es
// SIEMPRE lo mismo, exista o no la cuenta: «Si existe una cuenta asociada,
// recibirás instrucciones.» Lo fija la interfaz (`MENSAJE_DE_SOLICITUD`) y no
// el texto de la respuesta, para que ningún cambio de redacción en el
// servidor pueda acabar diciendo qué correos están registrados.
//
// El correo se recuerda en esta pestaña (nunca en la URL) para que la
// pantalla del código (`/restablecer`) lo traiga escrito.

import { CLAVES_DEL_CORREO, recordado, recordar } from '../comun/codigo-de-correo.js';
import {
  FalloDeRecuperacion,
  MENSAJE_DE_SOLICITUD,
  solicitarRestablecimiento,
} from '../comun/recuperacion.js';
import { montarCabecera } from '../comun/cabecera-app.js';
import { limpiarAviso, pintarAviso } from '../comun/ui/aviso.js';
import { conCarga } from '../comun/ui/boton.js';
import { marcarErrorDe } from '../comun/ui/campo.js';
import { formularioListo, sinCredencialesEnLaDireccion } from '../comun/ui/formulario-seguro.js';

// Se reexporta con su nombre de siempre: la llamada vive en `comun/`.
export { solicitarRestablecimiento, MENSAJE_DE_SOLICITUD };

/**
 * Qué decir si la solicitud no se pudo hacer. Ninguno de estos casos depende
 * de que la cuenta exista: son el límite de peticiones, un correo mal escrito
 * o un servicio caído.
 *
 * @param {unknown} error
 * @returns {{tono: 'error'|'advertencia', titulo: string, detalle: string}}
 */
export function rechazoDeLaSolicitud(error) {
  const estado = error instanceof FalloDeRecuperacion ? error.estado : 0;
  if (estado === 429) {
    return {
      tono: 'advertencia',
      titulo: 'Demasiadas solicitudes seguidas',
      detalle: 'Espera un momento y vuelve a intentarlo.',
    };
  }
  if (estado >= 400 && estado < 500) {
    return {
      tono: 'advertencia',
      titulo: 'Revisa el correo',
      detalle: 'Escribe la dirección completa, por ejemplo tu@correo.com.',
    };
  }
  return {
    tono: 'error',
    titulo: 'No pudimos enviar la solicitud',
    detalle: 'Inténtalo de nuevo en unos minutos.',
  };
}

/**
 * Monta la vista sobre el marcado de `restablecer-solicitar.html`.
 *
 * @param {ParentNode} raiz
 * @param {{solicitar?: typeof solicitarRestablecimiento, almacen?: Storage}} [opciones]
 */
export function montarSolicitud(
  raiz,
  { solicitar = solicitarRestablecimiento, almacen = globalThis.sessionStorage } = {},
) {
  const formulario = raiz.querySelector('#formSolicitud');
  const campoEmail = raiz.querySelector('#email');
  const botonEnviar = raiz.querySelector('#botonEnviar');
  const estado = raiz.querySelector('#estadoSolicitud');
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const siguiente = raiz.querySelector('[data-zona="siguiente"]');
  const escribirCodigo = raiz.querySelector('[data-zona="escribir-codigo"]');

  const anterior = recordado(CLAVES_DEL_CORREO.recuperacion, almacen);
  if (anterior && !campoEmail.value) {
    campoEmail.value = anterior;
  }

  function decir(texto) {
    estado.textContent = texto;
    estado.hidden = !texto;
  }

  campoEmail.addEventListener('input', () => {
    if (campoEmail.getAttribute('aria-invalid') === 'true') {
      marcarErrorDe(campoEmail, null);
    }
  });

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    siguiente.hidden = true;
    marcarErrorDe(campoEmail, null);

    const email = campoEmail.value.trim();
    if (!email || !campoEmail.checkValidity()) {
      marcarErrorDe(campoEmail, 'Escribe el correo con el que creaste la cuenta.');
      campoEmail.focus();
      return;
    }

    conCarga(botonEnviar, true, 'Enviando…');
    decir('Enviando la solicitud…');
    try {
      const mensaje = await solicitar(email);
      recordar(CLAVES_DEL_CORREO.recuperacion, email, almacen);
      decir('');
      pintarAviso(zonaAviso, {
        tono: 'info',
        titulo: mensaje,
        detalle:
          'El código llega al correo de la cuenta, caduca pronto y solo sirve una vez. Si no lo ves, revisa la carpeta de correo no deseado.',
      });
      siguiente.hidden = false;
      escribirCodigo.focus();
    } catch (error) {
      decir('');
      pintarAviso(zonaAviso, rechazoDeLaSolicitud(error));
      zonaAviso.focus();
    } finally {
      conCarga(botonEnviar, false);
    }
  });

  // G1 — la vista ya escucha `submit`: el botón se puede encender.
  formularioListo(formulario);
}

// ---------------------------------------------------------------- arranque

if (document.body?.dataset.vista === 'restablecer-solicitar') {
  // G1 — lo que un envío nativo de una versión vieja pudo dejar en la barra.
  sinCredencialesEnLaDireccion();
  montarCabecera(document.querySelector('[data-cabecera-app]'), {
    vista: 'restablecer-solicitar',
    seccionActiva: null,
  });
  montarSolicitud(document);
}
