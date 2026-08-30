// registro.js
// Vista de registro — HU-AUT-001.
import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';

const URL_REGISTRO = '/api/v1/auth/registro';

// TODO equipo: confirmar la ruta pública real bajo la que Spring Boot sirve
// frontend/app-web/src/cuentas/avatares/ como recurso estático. Se asume
// /cuentas/avatares/ tal como sugiere la estructura del proyecto; si el
// mapeo real es distinto, ajustar solo esta constante.
const RUTA_BASE_AVATARES = '/cuentas/avatares/';

/** @type {HTMLFormElement} */
const form = document.getElementById('formRegistro');
/** @type {HTMLButtonElement} */
const botonEnviar = document.getElementById('botonEnviar');
/** @type {HTMLInputElement} */
const campoPassword = document.getElementById('password');
/** @type {HTMLInputElement} */
const campoConfirmar = document.getElementById('confirmarPassword');
/** @type {HTMLParagraphElement} */
const ayudaConfirmar = document.getElementById('ayudaConfirmar');
/** @type {HTMLElement} */
const estadoRegistro = document.getElementById('estadoRegistro');
/** @type {HTMLDialogElement} */
const dialogoAvatar = document.getElementById('dialogoAvatar');
/** @type {HTMLButtonElement} */
const botonAbrirAvatar = document.getElementById('botonAbrirAvatar');
/** @type {HTMLButtonElement} */
const botonCerrarAvatar = document.getElementById('botonCerrarAvatar');
/** @type {HTMLElement} */
const resumenAvatar = document.getElementById('resumenAvatar');

/**
 * Lee el cuerpo de una respuesta que puede venir como JSON o texto plano.
 * Mismo patrón que demo-rbac.js (cuerpoDe), incluyendo el caso de body vacío.
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
 * Mismo patrón que setEstado(texto, tipo) de demo-rbac.js: un único
 * elemento .estado al que se le cambia textContent y className.
 * @param {string} texto
 * @param {'carga'|'error'|'exito'|'vacio'} tipo
 */
function setEstado(texto, tipo) {
  estadoRegistro.textContent = texto;
  estadoRegistro.className = `estado ${tipo}`;
  estadoRegistro.hidden = false;
}

function ocultarEstado() {
  estadoRegistro.hidden = true;
}

function validarConfirmacion() {
  const coincide = campoPassword.value === campoConfirmar.value;
  const vacio = campoConfirmar.value.length === 0;
  ayudaConfirmar.hidden = coincide || vacio;
  campoConfirmar.setAttribute('aria-invalid', String(!coincide && !vacio));
  return coincide;
}

campoConfirmar.addEventListener('input', validarConfirmacion);
campoPassword.addEventListener('input', () => {
  if (campoConfirmar.value.length > 0) validarConfirmacion();
});

// Abrir/cerrar el modal de avatares.
botonAbrirAvatar.addEventListener('click', () => {
  dialogoAvatar.showModal();
});
botonCerrarAvatar.addEventListener('click', () => {
  dialogoAvatar.close();
});
// Cerrar al hacer clic en el fondo oscuro (fuera de la tarjeta del modal).
dialogoAvatar.addEventListener('click', (evento) => {
  if (evento.target === dialogoAvatar) {
    dialogoAvatar.close();
  }
});

// Al elegir un avatar: se muestra una miniatura + su nombre en el botón,
// y el modal se cierra automáticamente.
form.querySelectorAll('input[name="avatar"]').forEach((radio) => {
  radio.addEventListener('change', () => {
    const opcion = radio.closest('.avatar-opcion');
    const img = opcion.querySelector('img');
    const nombre = opcion.querySelector('span').textContent;

    resumenAvatar.outerHTML = `
      <span class="avatar-resumen-elegido" id="resumenAvatar">
        <img src="${img.getAttribute('src')}" alt="">
        <span>${nombre}</span>
      </span>
    `;

    dialogoAvatar.close();
  });
});

form.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  ocultarEstado();

  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }
  if (!validarConfirmacion()) {
    return;
  }

  const avatarSeleccionado = form.querySelector('input[name="avatar"]:checked');
  if (!avatarSeleccionado) {
    // Resguardo explícito: los radios ya tienen required, checkValidity()
    // debería atrapar esto antes, pero se deja el mensaje claro por si acaso.
    setEstado('Debes elegir un avatar para continuar.', 'error');
    return;
  }

  const payload = {
    nombres: form.nombres.value.trim(),
    apellidos: form.apellidos.value.trim(),
    apodo: form.apodo.value.trim(),
    email: form.email.value.trim(),
    password: form.password.value,
    avatar: RUTA_BASE_AVATARES + avatarSeleccionado.value
  };

  botonEnviar.disabled = true;
  setEstado('Creando tu cuenta…', 'carga');

  try {
    const respuesta = await fetchWithHttpErrorInterceptor(URL_REGISTRO, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const { body } = await cuerpoDe(respuesta);

    if (respuesta.ok) {
      setEstado('¡Cuenta creada! Redirigiendo a inicio de sesión…', 'exito');
      setTimeout(() => {
        window.location.href = './login.html';
      }, 1500);
      return;
    }

    const mensaje = typeof body === 'string' ? body : (body?.mensaje || 'No se pudo crear la cuenta.');
    setEstado(mensaje, 'error');
  } catch (error) {
    setEstado('No pudimos conectar con el servidor. Intenta de nuevo.', 'error');
  } finally {
    botonEnviar.disabled = false;
  }
});
