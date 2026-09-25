// restablecer-solicitar.js
// Vista de solicitud de restablecimiento de contraseña — HU-COR-003.
//
// La logica de red vive en solicitarRestablecimiento(), exportada y con
// fetchImpl inyectable (mismo patron que cambiarRol en cambio-rol.js) --
// asi se puede probar sin depender de un fetch global real. El listener
// del formulario es solo una capa delgada que la llama y actualiza el DOM.

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { textoDeError, textoDelServidor } from '../comun/ui/texto-de-fallo.js';

const URL_SOLICITAR = '/api/v1/auth/restablecer/solicitar';

/**
 * Lee el cuerpo de una respuesta que puede venir como JSON o texto plano.
 *
 * @param {Response} response
 * @returns {Promise<{status: number, body: unknown}>}
 */
async function cuerpoDe(response) {
  const texto = await response.text();

  if (!texto) {
    return { status: response.status, body: null };
  }

  try {
    return { status: response.status, body: JSON.parse(texto) };
  } catch {
    return { status: response.status, body: texto };
  }
}

/**
 * Pide el restablecimiento de contraseña para un correo.
 *
 * El backend responde siempre el mismo mensaje generico, exista o no la
 * cuenta (para no permitir enumerar correos registrados) -- esta funcion
 * solo propaga ese mensaje, nunca distingue el caso.
 *
 * @param {string} email
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<string>} el mensaje a mostrar al usuario
 * @throws {Error} si el backend responde con error
 */
export async function solicitarRestablecimiento(
  email,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(URL_SOLICITAR, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  });

  const { body } = await cuerpoDe(respuesta);

  if (!respuesta.ok) {
    throw new Error(textoDelServidor(body, respuesta.status, 'No se pudo procesar la solicitud.'));
  }

  return typeof body === 'string'
    ? body
    : 'Si el correo está registrado, recibirás un mensaje con instrucciones.';
}

const form = document.getElementById('formSolicitud');
const botonEnviar = document.getElementById('botonEnviar');
const estadoSolicitud = document.getElementById('estadoSolicitud');

// Referencia explicita, no acceso implicito por nombre (form.email) --
// mismo motivo que en restablecer-confirmar.js: funciona en un navegador
// real, pero no es confiable en el entorno de pruebas (JSDOM).
const campoEmail = document.getElementById('email');

/**
 * @param {string} texto
 * @param {'carga'|'error'|'exito'} tipo
 */
function setEstado(texto, tipo) {
  estadoSolicitud.textContent = texto;
  estadoSolicitud.className = `estado ${tipo}`;
  estadoSolicitud.hidden = false;
}

function ocultarEstado() {
  estadoSolicitud.hidden = true;
}

form?.addEventListener('submit', async (evento) => {
  evento.preventDefault();

  ocultarEstado();

  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }

  botonEnviar.disabled = true;
  setEstado('Enviando…', 'carga');

  try {
    const mensaje = await solicitarRestablecimiento(campoEmail.value.trim());
    setEstado(mensaje, 'exito');
    form.reset();
  } catch (error) {
    setEstado(textoDeError(error, 'No se pudo procesar la solicitud.'), 'error');
  } finally {
    botonEnviar.disabled = false;
  }
});
