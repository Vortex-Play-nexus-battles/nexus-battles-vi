/**
 * Preguntas de seguridad desde «Mi cuenta» — B1 (identidad 2.0.0, 7.1.1).
 *
 * 7.1.1: «El usuario puede recuperar su cuenta contestando preguntas con
 * respuestas previamente configuradas y el código enviado a su correo
 * electrónico». Aquí se configuran esas preguntas:
 *
 *   - `GET /api/v1/auth/preguntas-seguridad` dice si hay y cuáles (nunca las
 *     respuestas);
 *   - `PUT` las reemplaza todas, y exige la contraseña actual: un token robado
 *     no basta para cambiar el segundo factor de la recuperación.
 *
 * **Las preguntas las escribe la persona.** El juego no propone ninguna: el
 * documento no fija un catálogo y no se inventa uno (el contrato lo dice igual:
 * «el sistema no inventa un catálogo»).
 *
 * Cuántas: entre `MINIMO_DE_PREGUNTAS` y `MAXIMO_DE_PREGUNTAS`. Son los valores
 * por omisión de `identidad.recuperacion.preguntas-minimas` y `-maximas` en el
 * servidor, **provisionales** mientras el PO no decida: el servidor es quien
 * de verdad los aplica (`preguntas-invalidas`), y su motivo se enseña tal cual.
 *
 * Mismo patrón que `cambiar-password.js`: sin DOM global, todo entra por
 * parámetros para poder probarlo con jsdom.
 *
 * @module cuentas/preguntas-seguridad
 */

import { baseDeApi } from '../comun/base-api.js';
import { detalleDelProblema, tipoDelProblema } from '../comun/codigo-de-correo.js';
import { cuerpoDe } from '../comun/entrada.js';
import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { limpiarAviso, pintarAviso, tonoPorEstado } from '../comun/ui/aviso.js';
import { conCarga } from '../comun/ui/boton.js';
import { marcarErrorDe } from '../comun/ui/campo.js';
import { h, vaciar } from '../comun/ui/dom.js';
import { formularioListo } from '../comun/ui/formulario-seguro.js';

/** Ruta del contrato. Literal entero: el guardián de rutas la lee. */
export const RUTA_PREGUNTAS = '/api/v1/auth/preguntas-seguridad';

/** Provisional: valor por omisión del servidor, pendiente de decisión del PO. */
export const MINIMO_DE_PREGUNTAS = 2;
/** Provisional: valor por omisión del servidor, pendiente de decisión del PO. */
export const MAXIMO_DE_PREGUNTAS = 3;

/** Límites del contrato (`ConfigurarPreguntasRequest`). */
export const LIMITES = Object.freeze({
  pregunta: { minimo: 5, maximo: 200 },
  respuesta: { minimo: 2, maximo: 100 },
});

/** Motivos de rechazo del `PUT`, tal como los nombra el contrato. */
export const PROBLEMAS_DE_PREGUNTAS = Object.freeze({
  CONTRASENA_ACTUAL_INCORRECTA: 'contrasena-actual-incorrecta',
  PREGUNTAS_INVALIDAS: 'preguntas-invalidas',
  CUENTA_BLOQUEADA: 'cuenta-bloqueada',
});

/** Un rechazo del servidor, ya leído. La vista decide por `tipo` y `estado`. */
export class FalloDePreguntas extends Error {
  /**
   * @param {{estado: number, tipo?: string|null, detalle?: string|null}} datos
   */
  constructor({ estado, tipo = null, detalle = null }) {
    super(detalle ?? `HTTP ${estado}`);
    this.name = 'FalloDePreguntas';
    this.estado = estado;
    this.tipo = tipo;
    this.detalle = detalle;
  }
}

/**
 * La respuesta del servidor, con la forma que la vista espera aunque falte
 * algo: nunca pinta una pregunta sin texto.
 *
 * @param {unknown} cuerpo
 * @returns {{configuradas: boolean, preguntas: Array<{id: string, texto: string}>}}
 */
export function leerPreguntas(cuerpo) {
  const preguntas = Array.isArray(cuerpo?.preguntas)
    ? cuerpo.preguntas.filter((p) => typeof p?.texto === 'string' && p.texto.trim())
    : [];
  return { configuradas: cuerpo?.configuradas === true && preguntas.length > 0, preguntas };
}

/**
 * @param {Response} respuesta
 * @returns {Promise<ReturnType<typeof leerPreguntas>>}
 */
async function leerRespuesta(respuesta) {
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw new FalloDePreguntas({
      estado: respuesta.status,
      tipo: tipoDelProblema(cuerpo),
      detalle: detalleDelProblema(cuerpo),
    });
  }
  return leerPreguntas(cuerpo);
}

/**
 * Las preguntas configuradas por la cuenta de esta sesión.
 *
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export async function consultarMisPreguntas({ fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(`${baseDeApi()}${RUTA_PREGUNTAS}`, {
    headers: { Accept: 'application/json, application/problem+json' },
    cache: 'no-store',
  });
  return leerRespuesta(respuesta);
}

/**
 * Reemplaza todas las preguntas. Quien las cambia es quien firma el token: el
 * cuerpo no identifica a nadie.
 *
 * @param {{passwordActual: string, preguntas: Array<{texto: string, respuesta: string}>}} datos
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export async function guardarMisPreguntas(
  { passwordActual, preguntas },
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(`${baseDeApi()}${RUTA_PREGUNTAS}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json, application/problem+json',
    },
    body: JSON.stringify({ passwordActual, preguntas }),
  });
  return leerRespuesta(respuesta);
}

/** Forma comparable de una pregunta, para no aceptar dos iguales. */
function comparable(texto) {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').replace(/\s+/g, ' ').trim().toLowerCase();
}

/**
 * Lo que la vista puede rechazar sin preguntar al servidor, campo a campo.
 *
 * @param {Array<{texto: string, respuesta: string}>} preguntas las visibles
 * @param {string} passwordActual
 * @returns {Array<{indice: number|null, campo: 'texto'|'respuesta'|'passwordActual', motivo: string}>}
 */
export function validarPreguntas(preguntas, passwordActual) {
  const errores = [];
  const vistas = new Map();
  preguntas.forEach(({ texto, respuesta }, indice) => {
    const pregunta = texto.trim();
    if (pregunta.length < LIMITES.pregunta.minimo) {
      errores.push({
        indice,
        campo: 'texto',
        motivo: `Escribe una pregunta de al menos ${LIMITES.pregunta.minimo} caracteres.`,
      });
    } else if (pregunta.length > LIMITES.pregunta.maximo) {
      errores.push({
        indice,
        campo: 'texto',
        motivo: `La pregunta no puede pasar de ${LIMITES.pregunta.maximo} caracteres.`,
      });
    } else if (vistas.has(comparable(pregunta))) {
      errores.push({ indice, campo: 'texto', motivo: 'Esta pregunta ya está en la lista.' });
    } else {
      vistas.set(comparable(pregunta), indice);
    }
    const contestacion = respuesta.trim();
    if (contestacion.length < LIMITES.respuesta.minimo) {
      errores.push({
        indice,
        campo: 'respuesta',
        motivo: `Escribe una respuesta de al menos ${LIMITES.respuesta.minimo} caracteres.`,
      });
    } else if (contestacion.length > LIMITES.respuesta.maximo) {
      errores.push({
        indice,
        campo: 'respuesta',
        motivo: `La respuesta no puede pasar de ${LIMITES.respuesta.maximo} caracteres.`,
      });
    }
  });
  if (!passwordActual) {
    errores.push({
      indice: null,
      campo: 'passwordActual',
      motivo: 'Escribe tu contraseña actual para confirmar el cambio.',
    });
  }
  return errores;
}

/**
 * Qué decir cuando el servidor no guarda las preguntas, por su `type`.
 *
 * @param {unknown} error
 * @returns {{tono: 'error'|'advertencia'|'info', titulo: string, detalle: string|null,
 *   campo: 'passwordActual'|null}}
 */
export function rechazoDePreguntas(error) {
  if (!(error instanceof FalloDePreguntas)) {
    return {
      tono: 'error',
      titulo: 'No pudimos guardar tus preguntas',
      detalle: 'La conexión falló. Inténtalo de nuevo en unos segundos.',
      campo: null,
    };
  }
  const tono = tonoPorEstado(error.estado);
  switch (error.tipo) {
    case PROBLEMAS_DE_PREGUNTAS.CONTRASENA_ACTUAL_INCORRECTA:
      return {
        tono,
        titulo: 'La contraseña actual es incorrecta',
        detalle: 'Cuenta como un intento fallido: tras varios, la cuenta se bloquea unos minutos.',
        campo: 'passwordActual',
      };
    case PROBLEMAS_DE_PREGUNTAS.PREGUNTAS_INVALIDAS:
      return {
        tono,
        titulo: 'Revisa las preguntas',
        detalle:
          error.detalle ??
          `Entre ${MINIMO_DE_PREGUNTAS} y ${MAXIMO_DE_PREGUNTAS} preguntas distintas, cada una con su respuesta.`,
        campo: null,
      };
    case PROBLEMAS_DE_PREGUNTAS.CUENTA_BLOQUEADA:
      return {
        tono,
        titulo: 'Tu cuenta está bloqueada temporalmente',
        detalle: 'Hubo demasiados intentos fallidos. Espera unos minutos y vuelve a intentarlo.',
        campo: null,
      };
    default:
      break;
  }
  if (error.estado >= 500 || !error.estado) {
    return {
      tono: 'error',
      titulo: 'No pudimos guardar tus preguntas',
      detalle: 'El servicio no respondió bien. Inténtalo de nuevo en unos minutos.',
      campo: null,
    };
  }
  return {
    tono,
    titulo: 'No pudimos guardar tus preguntas',
    detalle: error.detalle ?? 'Revisa los datos e inténtalo de nuevo.',
    campo: null,
  };
}

/**
 * Monta la sección sobre `[data-zona="preguntas-seguridad"]` y consulta el
 * estado de la cuenta.
 *
 * @param {ParentNode} raiz
 * @param {{consultar?: typeof consultarMisPreguntas, guardar?: typeof guardarMisPreguntas}} [opciones]
 * @returns {{cargado: Promise<void>}|null} `null` si la vista no tiene la sección
 */
export function montarPreguntasDeSeguridad(
  raiz,
  { consultar = consultarMisPreguntas, guardar = guardarMisPreguntas } = {},
) {
  const seccion = raiz.querySelector('[data-zona="preguntas-seguridad"]');
  if (!seccion) {
    return null;
  }
  const zona = (nombre) => seccion.querySelector(`[data-zona="${nombre}"]`);
  const estado = zona('estado-preguntas');
  const lista = zona('lista-preguntas');
  const zonaAviso = zona('aviso-preguntas');
  const acciones = zona('acciones-preguntas');
  const botonConfigurar = seccion.querySelector('[data-accion="configurar-preguntas"]');
  const botonReintentar = seccion.querySelector('[data-accion="reintentar-preguntas"]');
  const formulario = seccion.querySelector('form');
  const bloques = [...formulario.querySelectorAll('[data-pregunta]')];
  const botonAnadir = formulario.querySelector('[data-accion="anadir-pregunta"]');
  const botonQuitar = formulario.querySelector('[data-accion="quitar-pregunta"]');
  const campoClave = formulario.querySelector('[name="passwordActualPreguntas"]');
  const botonGuardar = formulario.querySelector('[data-accion="guardar-preguntas"]');
  const botonCancelar = formulario.querySelector('[data-accion="cancelar-preguntas"]');

  const controlesDe = (bloque) => ({
    texto: bloque.querySelector('[data-campo="texto"]'),
    respuesta: bloque.querySelector('[data-campo="respuesta"]'),
  });

  let visibles = MINIMO_DE_PREGUNTAS;

  /** La contraseña escrita no se conserva tras un rechazo. */
  function olvidarClave() {
    campoClave.value = '';
  }

  function decir(texto) {
    estado.textContent = texto;
    estado.hidden = !texto;
  }

  function pintarVisibles() {
    bloques.forEach((bloque, indice) => {
      bloque.hidden = indice >= visibles;
    });
    botonAnadir.hidden = visibles >= Math.min(MAXIMO_DE_PREGUNTAS, bloques.length);
    botonQuitar.hidden = visibles <= MINIMO_DE_PREGUNTAS;
  }

  function limpiarFormulario() {
    for (const bloque of bloques) {
      const { texto, respuesta } = controlesDe(bloque);
      texto.value = '';
      respuesta.value = '';
      marcarErrorDe(texto, null);
      marcarErrorDe(respuesta, null);
    }
    campoClave.value = '';
    marcarErrorDe(campoClave, null);
    visibles = MINIMO_DE_PREGUNTAS;
    pintarVisibles();
  }

  function cerrarFormulario() {
    limpiarFormulario();
    formulario.hidden = true;
    acciones.hidden = false;
  }

  /** @param {ReturnType<typeof leerPreguntas>} situacion */
  function pintarSituacion(situacion) {
    vaciar(lista);
    botonReintentar.hidden = true;
    if (situacion.configuradas) {
      decir(
        situacion.preguntas.length === 1
          ? 'Tienes 1 pregunta de seguridad configurada:'
          : `Tienes ${situacion.preguntas.length} preguntas de seguridad configuradas:`,
      );
      for (const pregunta of situacion.preguntas) {
        lista.append(h('li', { texto: pregunta.texto }));
      }
      lista.hidden = false;
      botonConfigurar.textContent = 'Cambiar mis preguntas';
    } else {
      decir(
        'Todavía no configuraste preguntas de seguridad. Configúralas para proteger mejor la recuperación de tu cuenta.',
      );
      lista.hidden = true;
      botonConfigurar.textContent = 'Configurar preguntas';
    }
    botonConfigurar.hidden = false;
    acciones.hidden = false;
  }

  async function cargar() {
    limpiarAviso(zonaAviso);
    botonReintentar.hidden = true;
    botonConfigurar.hidden = true;
    lista.hidden = true;
    decir('Consultando tus preguntas de seguridad…');
    try {
      pintarSituacion(await consultar());
    } catch {
      decir('');
      pintarAviso(zonaAviso, {
        tono: 'error',
        titulo: 'No pudimos consultar tus preguntas de seguridad',
        detalle: 'Inténtalo de nuevo en unos segundos.',
      });
      acciones.hidden = false;
      botonReintentar.hidden = false;
    }
  }

  botonConfigurar.addEventListener('click', () => {
    limpiarAviso(zonaAviso);
    limpiarFormulario();
    formulario.hidden = false;
    acciones.hidden = true;
    controlesDe(bloques[0]).texto.focus();
  });

  botonReintentar.addEventListener('click', () => {
    cargar();
  });

  botonAnadir.addEventListener('click', () => {
    visibles = Math.min(visibles + 1, MAXIMO_DE_PREGUNTAS, bloques.length);
    pintarVisibles();
    controlesDe(bloques[visibles - 1]).texto.focus();
  });

  botonQuitar.addEventListener('click', () => {
    const ultimo = controlesDe(bloques[visibles - 1]);
    ultimo.texto.value = '';
    ultimo.respuesta.value = '';
    marcarErrorDe(ultimo.texto, null);
    marcarErrorDe(ultimo.respuesta, null);
    visibles = Math.max(visibles - 1, MINIMO_DE_PREGUNTAS);
    pintarVisibles();
    botonAnadir.focus();
  });

  botonCancelar.addEventListener('click', () => {
    limpiarAviso(zonaAviso);
    cerrarFormulario();
    botonConfigurar.focus();
  });

  formulario.addEventListener('input', (evento) => {
    const control = evento.target;
    if (control instanceof HTMLElement && control.getAttribute('aria-invalid') === 'true') {
      marcarErrorDe(control, null);
    }
  });

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);

    const activos = bloques.slice(0, visibles).map(controlesDe);
    const preguntas = activos.map(({ texto, respuesta }) => ({
      texto: texto.value,
      respuesta: respuesta.value,
    }));
    for (const { texto, respuesta } of activos) {
      marcarErrorDe(texto, null);
      marcarErrorDe(respuesta, null);
    }
    marcarErrorDe(campoClave, null);

    const errores = validarPreguntas(preguntas, campoClave.value);
    if (errores.length > 0) {
      let primero = null;
      for (const error of errores) {
        const control =
          error.campo === 'passwordActual' ? campoClave : activos[error.indice][error.campo];
        marcarErrorDe(control, error.motivo);
        primero = primero ?? control;
      }
      primero.focus();
      return;
    }

    conCarga(botonGuardar, true, 'Guardando…');
    let situacion;
    try {
      situacion = await guardar({
        passwordActual: campoClave.value,
        preguntas: preguntas.map(({ texto, respuesta }) => ({
          texto: texto.trim(),
          respuesta: respuesta.trim(),
        })),
      });
    } catch (error) {
      conCarga(botonGuardar, false);
      const rechazo = rechazoDePreguntas(error);
      pintarAviso(zonaAviso, rechazo);
      if (rechazo.campo === 'passwordActual') {
        olvidarClave();
        marcarErrorDe(campoClave, 'La contraseña actual es incorrecta.');
        campoClave.focus();
      } else {
        zonaAviso.focus();
      }
      return;
    }
    conCarga(botonGuardar, false);

    // Ni la contraseña ni las respuestas se quedan en el formulario.
    cerrarFormulario();
    pintarSituacion(situacion);
    pintarAviso(zonaAviso, {
      tono: 'exito',
      titulo: 'Tus preguntas de seguridad quedaron guardadas',
      detalle: 'Te las pediremos, junto con el código del correo, si recuperas tu contraseña.',
    });
    zonaAviso.focus();
  });

  // G1 — la vista ya escucha `submit`: el botón se puede encender.
  formularioListo(formulario);

  pintarVisibles();
  formulario.hidden = true;
  return { cargado: cargar() };
}
