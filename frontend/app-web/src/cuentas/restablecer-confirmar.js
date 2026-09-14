// restablecer-confirmar.js
// Vista de canje de codigo (activacion o restablecimiento) — HU-COR-003 / HU-USR-002.
//
// Sirve para los dos casos que maneja TokenCredencialService.canjearToken:
// un codigo de "ACTIVACION" (cuenta creada por un admin, define su
// contraseña por primera vez) o de "RESTABLECIMIENTO" (olvido su
// contraseña). El cliente nunca distingue cual es -- el backend ya sabe
// que hacer segun el tipo que tenga guardado ese codigo.

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';

const URL_CONFIRMAR = '/api/v1/auth/restablecer/confirmar';

/** @type {HTMLFormElement} */
const form = document.getElementById('formConfirmacion');

/** @type {HTMLButtonElement} */
const botonEnviar = document.getElementById('botonEnviar');

/** @type {HTMLElement} */
const estadoConfirmacion = document.getElementById('estadoConfirmacion');

/** @type {HTMLInputElement} */
const campoConfirmarPassword = document.getElementById('confirmarPassword');

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
  estadoConfirmacion.textContent = texto;
  estadoConfirmacion.className = `estado ${tipo}`;
  estadoConfirmacion.hidden = false;
}

function ocultarEstado() {
  estadoConfirmacion.hidden = true;
}

form.addEventListener('submit', async (evento) => {
  evento.preventDefault();

  ocultarEstado();
  campoConfirmarPassword.setCustomValidity('');

  if (form.nuevaPassword.value !== campoConfirmarPassword.value) {
    campoConfirmarPassword.setCustomValidity('Las contraseñas no coinciden.');
  }

  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }

  const payload = {
    token: form.codigo.value.trim(),
    nuevaPassword: form.nuevaPassword.value
  };

  botonEnviar.disabled = true;
  setEstado('Restableciendo tu contraseña…', 'carga');

  try {
    const respuesta = await fetchWithHttpErrorInterceptor(URL_CONFIRMAR, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const { body } = await cuerpoDe(respuesta);

    if (!respuesta.ok) {
      const mensajeServidor = typeof body === 'string' ? body : body?.detail;
      setEstado(mensajeServidor || 'No se pudo restablecer la contraseña.', 'error');
      return;
    }

    setEstado(
      typeof body === 'string' ? body : 'Contraseña actualizada correctamente. Ya puedes iniciar sesión.',
      'exito'
    );

    form.reset();

    // Da tiempo a leer el mensaje de éxito antes de mandar al login.
    setTimeout(() => {
      window.location.href = './login.html';
    }, 2500);
  } finally {
    botonEnviar.disabled = false;
  }
});
