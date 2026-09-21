/**
 * Cambiar mi contraseña desde «Mi Cuenta» — HU-AUT-006 (RF-AUT-006).
 *
 * Habla con `PUT /api/v1/auth/password` de ms-identidad. Quien cambia es quien
 * firma el token que el interceptor adjunta; el cuerpo no identifica a nadie.
 *
 * Lo que hace la vista, y solo eso:
 *   - comprueba antes de enviar lo que se puede comprobar aquí (campos
 *     vacíos, confirmación distinta) para ahorrar un viaje; la política de
 *     RF-AUT-002 la decide el servidor y su rechazo dice qué regla falla (CA-03);
 *   - muestra el rechazo por `type` (CA-06), nunca inventa el motivo;
 *   - al cambiar, guarda el token nuevo que devuelve el servidor (CA-04: esta
 *     sesión sigue válida, las demás no) y vacía el formulario;
 *   - Cancelar vacía el formulario y no llama a nada (CA-05).
 *
 * Sin DOM global: todo entra por parámetros para poder probarlo con jsdom.
 *
 * @module cambiar-password
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';

/** Clave de sessionStorage donde vive el token de sesión (la misma del login). */
export const CLAVE_TOKEN = 'nexus.token';

const RUTA = '/api/v1/auth/password';

/** Textos por `type` del problem details, para no depender de la redacción del servidor. */
const TITULOS_POR_TIPO = Object.freeze({
  'https://nexusbattles.upb.edu.co/errors/contrasena-actual-incorrecta':
    'La contraseña actual es incorrecta.',
  'https://nexusbattles.upb.edu.co/errors/contrasena-no-cumple-politica':
    'La contraseña nueva no cumple la política.',
  'https://nexusbattles.upb.edu.co/errors/contrasena-repetida':
    'La contraseña nueva es igual a la anterior.',
  'https://nexusbattles.upb.edu.co/errors/contrasena-confirmacion-no-coincide':
    'La confirmación no coincide.',
  'https://nexusbattles.upb.edu.co/errors/cuenta-bloqueada':
    'Tu cuenta está bloqueada temporalmente.',
});

/** Error del servicio, ya interpretado. La vista decide por `tipo`, no por el texto. */
export class ErrorDeCambio extends Error {
  constructor(problema, estado) {
    super(problema?.detail || problema?.title || 'No se pudo cambiar la contraseña.');
    this.name = 'ErrorDeCambio';
    this.tipo = problema?.type ?? null;
    this.estado = problema?.status ?? estado;
    this.titulo =
      TITULOS_POR_TIPO[this.tipo] ?? problema?.title ?? 'No se pudo cambiar la contraseña';
    this.detalle = this.message;
  }
}

async function cuerpoDelProblema(respuesta) {
  try {
    return await respuesta.json();
  } catch {
    return null;
  }
}

/**
 * Llama al servicio.
 *
 * @param {{passwordActual: string, nuevaPassword: string, confirmacion: string}} datos
 * @param {{fetchImpl?: Function, base?: string}} [opciones] inyección para las pruebas
 * @returns {Promise<{token: string, mensaje: string}>}
 * @throws {ErrorDeCambio}
 */
export async function cambiarPassword(
  datos,
  { fetchImpl = fetchWithHttpErrorInterceptor, base = '' } = {},
) {
  const respuesta = await fetchImpl(`${base}${RUTA}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      passwordActual: datos.passwordActual,
      nuevaPassword: datos.nuevaPassword,
      confirmacion: datos.confirmacion,
    }),
  });

  if (respuesta.ok) {
    return respuesta.json();
  }
  throw new ErrorDeCambio(await cuerpoDelProblema(respuesta), respuesta.status);
}

/**
 * Lo que la vista puede rechazar sin preguntar al servidor.
 *
 * @param {{passwordActual: string, nuevaPassword: string, confirmacion: string}} datos
 * @returns {string|null} motivo, o null si se puede enviar
 */
export function validarLocalmente(datos) {
  if (!datos.passwordActual) {
    return 'Escribe tu contraseña actual.';
  }
  if (!datos.nuevaPassword) {
    return 'Escribe la contraseña nueva.';
  }
  if (datos.nuevaPassword !== datos.confirmacion) {
    return 'La confirmación no coincide con la contraseña nueva.';
  }
  return null;
}

/**
 * Título y detalle del rechazo, sin repetirse cuando dicen lo mismo.
 *
 * @param {{titulo?: string, detalle?: string}} error
 * @returns {string}
 */
export function textoDelRechazo(error) {
  const titulo = error?.titulo ?? 'No se pudo cambiar la contraseña.';
  const detalle = error?.detalle ?? '';
  const normal = (s) => s.replace(/\.$/, '').trim().toLowerCase();
  if (!detalle || normal(detalle) === normal(titulo)) {
    return titulo;
  }
  return `${titulo} ${detalle}`.trim();
}

/**
 * Monta el formulario sobre `[data-zona="cambiar-password"]`.
 *
 * @param {ParentNode} raiz
 * @param {object} [opciones]
 * @param {(datos: object) => Promise<{token: string, mensaje: string}>} [opciones.cambiar]
 * @param {Storage} [opciones.storage] donde guardar el token nuevo
 * @returns {{leer: () => object, limpiar: () => void}}
 */
export function montarCambioDePassword(
  raiz,
  { cambiar = cambiarPassword, storage = globalThis.sessionStorage } = {},
) {
  const zona = raiz.querySelector('[data-zona="cambiar-password"]');
  const formulario = zona?.querySelector('form');
  const actual = zona?.querySelector('[name="passwordActual"]');
  const nueva = zona?.querySelector('[name="nuevaPassword"]');
  const confirmacion = zona?.querySelector('[name="confirmacion"]');
  const mensaje = zona?.querySelector('[data-zona="mensaje-password"]');
  const botonGuardar = zona?.querySelector('[data-accion="guardar-password"]');
  const botonCancelar = zona?.querySelector('[data-accion="cancelar-password"]');

  const leer = () => ({
    passwordActual: actual?.value ?? '',
    nuevaPassword: nueva?.value ?? '',
    confirmacion: confirmacion?.value ?? '',
  });

  const decir = (texto, tono) => {
    if (!mensaje) {
      return;
    }
    mensaje.textContent = texto ?? '';
    mensaje.hidden = !texto;
    mensaje.dataset.tono = tono ?? '';
  };

  const limpiar = () => {
    for (const campo of [actual, nueva, confirmacion]) {
      if (campo) {
        campo.value = '';
      }
    }
  };

  formulario?.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const datos = leer();
    const motivoLocal = validarLocalmente(datos);
    if (motivoLocal) {
      decir(motivoLocal, 'error');
      return;
    }
    if (botonGuardar) {
      botonGuardar.disabled = true;
    }
    decir('Guardando...', 'info');
    try {
      const resultado = await cambiar(datos);
      // CA-04: el servidor invalidó las demás sesiones y renovó esta.
      if (resultado?.token && storage) {
        try {
          storage.setItem(CLAVE_TOKEN, resultado.token);
        } catch {
          // Sin almacenamiento la sesión actual caduca al siguiente uso; el
          // cambio ya está hecho igual.
        }
      }
      limpiar();
      decir(resultado?.mensaje ?? 'Contraseña actualizada.', 'exito');
    } catch (error) {
      // CA-03 / CA-06: el detalle lo redacta el servicio (qué regla falla).
      decir(textoDelRechazo(error), 'error');
    } finally {
      if (botonGuardar) {
        botonGuardar.disabled = false;
      }
    }
  });

  // CA-05: cancelar no llama a nada y conserva la contraseña vigente.
  botonCancelar?.addEventListener('click', () => {
    limpiar();
    decir('', '');
  });

  return { leer, limpiar };
}
