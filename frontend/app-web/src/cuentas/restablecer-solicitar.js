// restablecer-solicitar.js
// Vista de solicitud de restablecimiento de contraseña — HU-COR-003.

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';

const URL_SOLICITAR = '/api/v1/auth/restablecer/solicitar';

/** @type {HTMLFormElement} */
const form = document.getElementById('formSolicitud');

/** @type {HTMLButtonElement} */
const botonEnviar = document.getElementById('botonEnviar');

/** @type {HTMLElement} */
const estadoSolicitud = document.getElementById('estadoSolicitud');

/**
 * Lee el cuerpo de una respuesta que puede venir como JSON o texto plano.
 * Mismo patrón que login.js: el endpoint de solicitud responde texto
 * plano, no JSON.
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

form.addEventListener('submit', async (evento) => {
  evento.preventDefault();

  ocultarEstado();

  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }

  const payload = { email: form.email.value.trim() };

  botonEnviar.disabled = true;
  setEstado('Enviando…', 'carga');

  try {
    const respuesta = await fetchWithHttpErrorInterceptor(URL_SOLICITAR, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const { body } = await cuerpoDe(respuesta);

    if (!respuesta.ok) {
      const mensajeServidor = typeof body === 'string' ? body : body?.detail;
      setEstado(mensajeServidor || 'No se pudo procesar la solicitud.', 'error');
      return;
    }

    // El backend responde siempre el mismo mensaje generico, exista o no
    // la cuenta (para no permitir enumerar correos registrados) -- no hay
    // ningun "siguiente paso" al que redirigir solo, el usuario decide
    // el mismo cuando revise su correo.
    setEstado(
      typeof body === 'string'
        ? body
        : 'Si el correo está registrado, recibirás un mensaje con instrucciones.',
      'exito'
    );

    form.reset();
  } finally {
    botonEnviar.disabled = false;
  }
});
