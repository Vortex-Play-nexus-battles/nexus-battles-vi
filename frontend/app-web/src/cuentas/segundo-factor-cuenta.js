/**
 * «Verificación en dos pasos» en Mi cuenta > Seguridad — HU-AUT-007.
 *
 * Se monta sobre `[data-zona="segundo-factor"]` de `perfil.html`, igual que
 * `preguntas-seguridad.js` sobre la suya. Tres cosas:
 *
 *   1. Dice el estado: activa o no, desde cuándo, cuántos códigos de
 *      recuperación quedan y si el rol de la cuenta la exige.
 *   2. Activarla: pide la clave al servidor, la enseña para la aplicación
 *      (enlace `otpauth://` y la clave para escribirla a mano), la confirma
 *      con un código y enseña los códigos de recuperación UNA vez.
 *   3. Desactivarla: con la contraseña actual y un código (de la aplicación o
 *      de recuperación).
 *
 * La clave y los códigos solo están en pantalla: al terminar o cancelar se
 * borran del DOM, y nada se guarda en el navegador.
 *
 * @module cuentas/segundo-factor-cuenta
 */

import { limpiarAviso, pintarAviso } from '../comun/ui/aviso.js';
import { conCarga } from '../comun/ui/boton.js';
import { marcarErrorDe } from '../comun/ui/campo.js';
import { vaciar } from '../comun/ui/dom.js';
import { fechaHora } from '../comun/ui/formato.js';
import { formularioListo } from '../comun/ui/formulario-seguro.js';
import {
  PROBLEMAS_DEL_SEGUNDO_FACTOR,
  activarSegundoFactor,
  consultarSegundoFactor,
  copiarTexto,
  desactivarSegundoFactor,
  descargarTexto,
  esCodigoDeAplicacion,
  iniciarEnrolamiento,
  motivoDelCodigo,
  motivoDelCodigoDeRecuperacion,
  pintarCodigosDeRecuperacion,
  pintarSecreto,
  rechazoDelSegundoFactor,
} from './segundo-factor.js';

/** Con tan pocos códigos de recuperación se avisa de que conviene renovarlos. */
export const POCOS_CODIGOS = 3;

/** Rechazos de la activación tras los que la clave en pantalla ya no sirve. */
const CLAVE_QUE_YA_NO_VALE = new Set([
  PROBLEMAS_DEL_SEGUNDO_FACTOR.SIN_ENROLAMIENTO,
  PROBLEMAS_DEL_SEGUNDO_FACTOR.YA_ACTIVO,
]);

/**
 * Vacía un campo. Aparte, como `olvidarClave` en preguntas-seguridad.js: tras
 * un rechazo, la contraseña o el código escritos se borran a propósito.
 *
 * @param {HTMLInputElement} campo
 */
function borrar(campo) {
  campo.value = '';
}

/**
 * Lo que se dice del estado de la cuenta.
 *
 * @param {{activo?: boolean, obligatorio?: boolean, disponible?: boolean,
 *   activadoEn?: string|null, codigosRecuperacionRestantes?: number|null}} estado
 * @returns {{texto: string, nota: {tono: 'advertencia'|'info', titulo: string, detalle: string}|null,
 *   puedeActivar: boolean, puedeDesactivar: boolean}}
 */
export function describirEstado(estado) {
  if (estado.activo) {
    const desde = estado.activadoEn ? fechaHora(estado.activadoEn) : '—';
    const restantes = estado.codigosRecuperacionRestantes;
    const partes = [desde === '—' ? 'Activada.' : `Activada desde el ${desde}.`];
    if (typeof restantes === 'number') {
      partes.push(
        restantes === 1
          ? 'Te queda 1 código de recuperación.'
          : `Te quedan ${restantes} códigos de recuperación.`,
      );
    }
    let nota = null;
    if (typeof restantes === 'number' && restantes < POCOS_CODIGOS) {
      nota = {
        tono: 'advertencia',
        titulo: 'Te quedan pocos códigos de recuperación',
        detalle:
          'Para tener otros nuevos, desactiva la verificación en dos pasos y vuelve a activarla.',
      };
    } else if (estado.obligatorio) {
      nota = {
        tono: 'info',
        titulo: 'Tu rol la exige',
        detalle: 'Si la desactivas, te pediremos activarla de nuevo la próxima vez que entres.',
      };
    }
    return { texto: partes.join(' '), nota, puedeActivar: false, puedeDesactivar: true };
  }
  if (estado.disponible === false) {
    return {
      texto: 'Desactivada.',
      nota: {
        tono: 'advertencia',
        titulo: 'La verificación en dos pasos no está disponible ahora mismo',
        detalle: 'El servicio no puede guardar claves nuevas. Inténtalo de nuevo más tarde.',
      },
      puedeActivar: false,
      puedeDesactivar: false,
    };
  }
  return {
    texto:
      'Desactivada. Actívala para que, además de tu contraseña, te pidamos un código de tu teléfono al entrar.',
    nota: estado.obligatorio
      ? {
          tono: 'advertencia',
          titulo: 'Tu rol la exige',
          detalle: 'Actívala ahora: la próxima vez que entres no podrás hacerlo sin ella.',
        }
      : null,
    puedeActivar: true,
    puedeDesactivar: false,
  };
}

/**
 * ¿Se puede enviar este código para desactivar? Vale el de la aplicación o
 * uno de recuperación.
 *
 * @param {string} texto
 * @returns {string|null} el motivo para no enviarlo, o `null`
 */
export function motivoDelCodigoParaDesactivar(texto) {
  if (esCodigoDeAplicacion(texto) || motivoDelCodigoDeRecuperacion(texto) === null) {
    return null;
  }
  return 'Escribe los 6 dígitos de tu aplicación o uno de tus códigos de recuperación.';
}

/**
 * Monta la sección y consulta el estado.
 *
 * @param {ParentNode} raiz
 * @param {{consultar?: typeof consultarSegundoFactor, enrolar?: typeof iniciarEnrolamiento,
 *   activar?: typeof activarSegundoFactor, desactivar?: typeof desactivarSegundoFactor,
 *   copiar?: typeof copiarTexto, descargar?: typeof descargarTexto}} [opciones]
 * @returns {{cargado: Promise<void>}|null} `null` si la vista no tiene la sección
 */
export function montarSegundoFactor(
  raiz,
  {
    consultar = consultarSegundoFactor,
    enrolar = iniciarEnrolamiento,
    activar = activarSegundoFactor,
    desactivar = desactivarSegundoFactor,
    copiar = copiarTexto,
    descargar = descargarTexto,
  } = {},
) {
  const seccion = raiz.querySelector('[data-zona="segundo-factor"]');
  if (!seccion) {
    return null;
  }
  const zona = (nombre) => seccion.querySelector(`[data-zona="${nombre}"]`);
  const accion = (nombre) => seccion.querySelector(`[data-accion="${nombre}"]`);

  const estado = zona('estado-segundo-factor');
  const zonaAviso = zona('aviso-segundo-factor');
  const acciones = zona('acciones-segundo-factor');
  const zonaClave = zona('clave-segundo-factor');
  const zonaCodigos = zona('codigos-segundo-factor');
  const formActivacion = zona('formulario-activacion');
  const formDesactivacion = zona('formulario-desactivacion');
  const botonActivar = accion('activar-segundo-factor');
  const botonDesactivar = accion('desactivar-segundo-factor');
  const botonReintentar = accion('reintentar-segundo-factor');
  const botonConfirmar = accion('confirmar-activacion');
  const botonQuitar = accion('confirmar-desactivacion');
  const campoActivacion = formActivacion.querySelector('[name="codigo"]');
  const campoClave = formDesactivacion.querySelector('[name="passwordActualSegundoFactor"]');
  const campoDesactivacion = formDesactivacion.querySelector('[name="codigo"]');

  /** La cuenta del enrolamiento en curso: va en el archivo de códigos. */
  let cuentaDelEnrolamiento = null;

  function decir(texto) {
    estado.textContent = texto;
    estado.hidden = !texto;
  }

  function botones({ activar: verActivar = false, desactivar: verDesactivar = false } = {}) {
    botonActivar.hidden = !verActivar;
    botonDesactivar.hidden = !verDesactivar;
    botonReintentar.hidden = true;
    acciones.hidden = !(verActivar || verDesactivar);
  }

  /** Lo de la activación en curso fuera de la pantalla: clave, código, cuenta. */
  function cerrarActivacion() {
    vaciar(zonaClave);
    zonaClave.hidden = true;
    campoActivacion.value = '';
    marcarErrorDe(campoActivacion, null);
    formActivacion.hidden = true;
    cuentaDelEnrolamiento = null;
  }

  function cerrarDesactivacion() {
    campoClave.value = '';
    campoDesactivacion.value = '';
    marcarErrorDe(campoClave, null);
    marcarErrorDe(campoDesactivacion, null);
    formDesactivacion.hidden = true;
  }

  function cerrarCodigos() {
    vaciar(zonaCodigos);
    zonaCodigos.hidden = true;
  }

  function pintarEstado(situacion) {
    const { texto, nota, puedeActivar, puedeDesactivar } = describirEstado(situacion);
    decir(texto);
    botones({ activar: puedeActivar, desactivar: puedeDesactivar });
    if (nota) {
      pintarAviso(zonaAviso, nota);
    }
  }

  async function cargar() {
    limpiarAviso(zonaAviso);
    botones();
    decir('Consultando la verificación en dos pasos…');
    try {
      pintarEstado(await consultar());
    } catch {
      decir('');
      pintarAviso(zonaAviso, {
        tono: 'error',
        titulo: 'No pudimos consultar la verificación en dos pasos',
        detalle: 'Inténtalo de nuevo en unos segundos.',
      });
      acciones.hidden = false;
      botonReintentar.hidden = false;
    }
  }

  /** @param {unknown} error @param {'cuenta'|'activacion'} contexto */
  function explicar(error, contexto) {
    const rechazo = rechazoDelSegundoFactor(error, { contexto });
    pintarAviso(zonaAviso, rechazo);
    return rechazo;
  }

  // ------------------------------------------------------------- activar

  botonActivar.addEventListener('click', async () => {
    limpiarAviso(zonaAviso);
    conCarga(botonActivar, true, 'Generando la clave…');
    let enrolamiento;
    try {
      enrolamiento = await enrolar();
    } catch (error) {
      conCarga(botonActivar, false);
      explicar(error, 'cuenta');
      zonaAviso.focus();
      return;
    }
    conCarga(botonActivar, false);
    cuentaDelEnrolamiento = enrolamiento.cuenta ?? null;
    botones();
    decir('Añade la cuenta a tu aplicación de autenticación y confirma con un código.');
    pintarSecreto(zonaClave, enrolamiento, { copiar });
    formActivacion.hidden = false;
    campoActivacion.focus();
  });

  accion('cancelar-activacion').addEventListener('click', () => {
    limpiarAviso(zonaAviso);
    cerrarActivacion();
    cargar();
  });

  formActivacion.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    marcarErrorDe(campoActivacion, null);
    const motivo = motivoDelCodigo(campoActivacion.value);
    if (motivo) {
      marcarErrorDe(campoActivacion, motivo);
      campoActivacion.focus();
      return;
    }
    conCarga(botonConfirmar, true, 'Comprobando…');
    let activacion;
    try {
      activacion = await activar(campoActivacion.value);
    } catch (error) {
      conCarga(botonConfirmar, false);
      const rechazo = rechazoDelSegundoFactor(error, { contexto: 'activacion' });
      if (rechazo.campo === 'codigo') {
        pintarAviso(zonaAviso, rechazo);
        borrar(campoActivacion);
        marcarErrorDe(campoActivacion, 'Ese código no sirvió. Escribe el que se ve ahora.');
        campoActivacion.focus();
        return;
      }
      if (CLAVE_QUE_YA_NO_VALE.has(error?.tipo)) {
        // Otra pestaña terminó o rehízo la activación: la clave de esta
        // pantalla ya no vale para nada.
        cerrarActivacion();
        await cargar();
      }
      // Sin red o con el servicio caído, la clave sigue en pantalla para
      // reintentar con el código siguiente.
      pintarAviso(zonaAviso, rechazo);
      zonaAviso.focus();
      return;
    }
    conCarga(botonConfirmar, false);
    const cuenta = cuentaDelEnrolamiento;
    cerrarActivacion();
    decir('La verificación en dos pasos quedó activada.');
    pintarCodigosDeRecuperacion(zonaCodigos, activacion.codigosRecuperacion ?? [], {
      cuenta,
      copiar,
      descargar,
      alTerminar: async () => {
        cerrarCodigos();
        await cargar();
        pintarAviso(zonaAviso, {
          tono: 'exito',
          titulo: 'La verificación en dos pasos está activa',
          detalle:
            'La próxima vez que entres te pediremos, además de tu contraseña, el código de tu aplicación.',
        });
        zonaAviso.focus();
      },
    });
  });

  // ---------------------------------------------------------- desactivar

  botonDesactivar.addEventListener('click', () => {
    limpiarAviso(zonaAviso);
    cerrarDesactivacion();
    botones();
    formDesactivacion.hidden = false;
    campoClave.focus();
  });

  accion('cancelar-desactivacion').addEventListener('click', () => {
    limpiarAviso(zonaAviso);
    cerrarDesactivacion();
    cargar();
  });

  formDesactivacion.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    marcarErrorDe(campoClave, null);
    marcarErrorDe(campoDesactivacion, null);

    let primero = null;
    if (!campoClave.value) {
      marcarErrorDe(campoClave, 'Escribe tu contraseña actual.');
      primero = campoClave;
    }
    const motivo = motivoDelCodigoParaDesactivar(campoDesactivacion.value);
    if (motivo) {
      marcarErrorDe(campoDesactivacion, motivo);
      primero = primero ?? campoDesactivacion;
    }
    if (primero) {
      primero.focus();
      return;
    }

    conCarga(botonQuitar, true, 'Desactivando…');
    try {
      await desactivar({ passwordActual: campoClave.value, codigo: campoDesactivacion.value });
    } catch (error) {
      conCarga(botonQuitar, false);
      const rechazo = explicar(error, 'cuenta');
      if (rechazo.campo === 'passwordActual') {
        borrar(campoClave);
        marcarErrorDe(campoClave, 'La contraseña actual es incorrecta.');
        campoClave.focus();
      } else if (rechazo.campo === 'codigo') {
        borrar(campoDesactivacion);
        marcarErrorDe(campoDesactivacion, 'Ese código no sirvió.');
        campoDesactivacion.focus();
      } else {
        zonaAviso.focus();
      }
      return;
    }
    conCarga(botonQuitar, false);
    cerrarDesactivacion();
    await cargar();
    pintarAviso(zonaAviso, {
      tono: 'exito',
      titulo: 'La verificación en dos pasos quedó desactivada',
      detalle:
        'Al entrar solo te pediremos la contraseña. Tus códigos de recuperación ya no sirven.',
    });
    zonaAviso.focus();
  });

  for (const formulario of [formActivacion, formDesactivacion]) {
    formulario.addEventListener('input', (evento) => {
      const control = evento.target;
      if (control instanceof HTMLElement && control.getAttribute('aria-invalid') === 'true') {
        marcarErrorDe(control, null);
      }
    });
    // G1 — la vista ya escucha `submit`: los botones se pueden encender.
    formularioListo(formulario);
  }

  botonReintentar.addEventListener('click', () => {
    cargar();
  });

  return { cargado: cargar() };
}
