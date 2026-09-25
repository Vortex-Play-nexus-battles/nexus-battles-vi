// restablecer-confirmar.js
// Vista de canje de codigo (activacion o restablecimiento) — HU-COR-003 / HU-USR-002.
//
// Sirve para los dos casos que maneja TokenCredencialService.canjearToken:
// un codigo de "ACTIVACION" (cuenta creada por un admin, define su
// contraseña por primera vez) o de "RESTABLECIMIENTO" (olvido su
// contraseña). El cliente nunca distingue cual es -- el backend ya sabe
// que hacer segun el tipo que tenga guardado ese codigo.
//
// La logica de red vive en confirmarRestablecimiento(), exportada y con
// fetchImpl inyectable (mismo patron que cambiarRol en cambio-rol.js).
// La validacion de "las dos contraseñas coinciden" es puramente de
// cliente -- el backend nunca recibe la confirmacion, solo nuevaPassword.

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { textoDeError, textoDelServidor } from '../comun/ui/texto-de-fallo.js';

const URL_CONFIRMAR = '/api/v1/auth/restablecer/confirmar';

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
 * Canjea un codigo (de activacion o restablecimiento) por una nueva
 * contraseña.
 *
 * @param {string} token
 * @param {string} nuevaPassword
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<string>} el mensaje a mostrar al usuario
 * @throws {Error} si el codigo es invalido, expirado, o ya usado
 */
export async function confirmarRestablecimiento(
  token,
  nuevaPassword,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(URL_CONFIRMAR, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, nuevaPassword }),
  });

  const { body } = await cuerpoDe(respuesta);

  if (!respuesta.ok) {
    throw new Error(
      textoDelServidor(body, respuesta.status, 'No se pudo restablecer la contraseña.'),
    );
  }

  return typeof body === 'string'
    ? body
    : 'Contraseña actualizada correctamente. Ya puedes iniciar sesión.';
}

const form = document.getElementById('formConfirmacion');
const botonEnviar = document.getElementById('botonEnviar');
const estadoConfirmacion = document.getElementById('estadoConfirmacion');

// Referencias explicitas a cada campo, en vez de acceso implicito por
// nombre (form.codigo, form.nuevaPassword) -- ese acceso "magico" que el
// propio HTML habilita funciona en un navegador real, pero no es
// confiable en el entorno de pruebas (JSDOM) usado por este proyecto. Ser
// explicito evita la dependencia de ese comportamiento implicito, y es
// igual de correcto en produccion.
const campoCodigo = document.getElementById('codigo');
const campoNuevaPassword = document.getElementById('nuevaPassword');
const campoConfirmarPassword = document.getElementById('confirmarPassword');

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

form?.addEventListener('submit', async (evento) => {
  evento.preventDefault();

  ocultarEstado();
  campoConfirmarPassword.setCustomValidity('');

  if (campoNuevaPassword.value !== campoConfirmarPassword.value) {
    campoConfirmarPassword.setCustomValidity('Las contraseñas no coinciden.');
  }

  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }

  botonEnviar.disabled = true;
  setEstado('Restableciendo tu contraseña…', 'carga');

  try {
    const mensaje = await confirmarRestablecimiento(
      campoCodigo.value.trim(),
      campoNuevaPassword.value,
    );
    setEstado(mensaje, 'exito');
    form.reset();

    setTimeout(() => {
      window.location.href = './login.html';
    }, 2500);
  } catch (error) {
    setEstado(textoDeError(error, 'No se pudo restablecer la contraseña.'), 'error');
  } finally {
    botonEnviar.disabled = false;
  }
});
