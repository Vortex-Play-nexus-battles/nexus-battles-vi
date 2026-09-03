// registro.js
// Vista de registro — HU-AUT-001.
import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';

const URL_REGISTRO = '/api/v1/auth/registro';
const TAMANO_SALIDA_PX = 512; // Resolución del avatar final, cuadrado.

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
/** @type {HTMLInputElement} */
const inputAvatar = document.getElementById('avatar');
/** @type {HTMLDivElement} */
const avatarVistaPrevia = document.getElementById('avatarVistaPrevia');
/** @type {HTMLButtonElement} */
const botonQuitarAvatar = document.getElementById('botonQuitarAvatar');

/** @type {HTMLDialogElement} */
const dialogoRecorte = document.getElementById('dialogoRecorte');
/** @type {HTMLDivElement} */
const recorteVisor = document.getElementById('recorteVisor');
/** @type {HTMLImageElement} */
const recorteImagen = document.getElementById('recorteImagen');
/** @type {HTMLInputElement} */
const recorteZoom = document.getElementById('recorteZoom');

// Blob ya recortado, listo para subir. Reemplaza al archivo original.
let avatarRecortado = null;

/**
 * Lee el cuerpo de una respuesta que puede venir como JSON o texto plano.
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

// ---------- Recorte de avatar ----------

const VISOR_TAMANO = 280;
let escalaBase = 1;
let zoom = 1;
let desplazamientoX = 0;
let desplazamientoY = 0;
let arrastrando = false;
let arrastreInicioX = 0;
let arrastreInicioY = 0;
let desplazamientoInicioX = 0;
let desplazamientoInicioY = 0;

inputAvatar.addEventListener('change', () => {
  const archivo = inputAvatar.files[0];
  if (!archivo) return;

  const urlObjeto = URL.createObjectURL(archivo);
  recorteImagen.src = urlObjeto;

  recorteImagen.onload = () => {
    const anchoNatural = recorteImagen.naturalWidth;
    const altoNatural = recorteImagen.naturalHeight;

    // "cover": la imagen siempre cubre el visor cuadrado completo, sin
    // dejar espacios en blanco, sin importar su proporción original.
    escalaBase = Math.max(VISOR_TAMANO / anchoNatural, VISOR_TAMANO / altoNatural);
    zoom = 1;
    recorteZoom.value = '1';

    centrarImagen();
    aplicarTransformacion();

    dialogoRecorte.showModal();
  };
});

function centrarImagen() {
  const anchoEfectivo = recorteImagen.naturalWidth * escalaBase * zoom;
  const altoEfectivo = recorteImagen.naturalHeight * escalaBase * zoom;
  desplazamientoX = (VISOR_TAMANO - anchoEfectivo) / 2;
  desplazamientoY = (VISOR_TAMANO - altoEfectivo) / 2;
}

function limitarDesplazamiento() {
  const anchoEfectivo = recorteImagen.naturalWidth * escalaBase * zoom;
  const altoEfectivo = recorteImagen.naturalHeight * escalaBase * zoom;

  const minX = VISOR_TAMANO - anchoEfectivo;
  const minY = VISOR_TAMANO - altoEfectivo;

  desplazamientoX = Math.min(0, Math.max(minX, desplazamientoX));
  desplazamientoY = Math.min(0, Math.max(minY, desplazamientoY));
}

function aplicarTransformacion() {
  limitarDesplazamiento();
  const escalaTotal = escalaBase * zoom;
  recorteImagen.style.transform =
    `translate(${desplazamientoX}px, ${desplazamientoY}px) scale(${escalaTotal})`;
}

recorteZoom.addEventListener('input', () => {
  zoom = Number(recorteZoom.value);
  aplicarTransformacion();
});

recorteVisor.addEventListener('pointerdown', (evento) => {
  arrastrando = true;
  arrastreInicioX = evento.clientX;
  arrastreInicioY = evento.clientY;
  desplazamientoInicioX = desplazamientoX;
  desplazamientoInicioY = desplazamientoY;
  recorteVisor.setPointerCapture(evento.pointerId);
});

recorteVisor.addEventListener('pointermove', (evento) => {
  if (!arrastrando) return;
  desplazamientoX = desplazamientoInicioX + (evento.clientX - arrastreInicioX);
  desplazamientoY = desplazamientoInicioY + (evento.clientY - arrastreInicioY);
  aplicarTransformacion();
});

recorteVisor.addEventListener('pointerup', () => { arrastrando = false; });
recorteVisor.addEventListener('pointercancel', () => { arrastrando = false; });

function cancelarRecorte() {
  dialogoRecorte.close();
  inputAvatar.value = '';
}

document.getElementById('botonCancelarRecorte').addEventListener('click', cancelarRecorte);
document.getElementById('botonCancelarRecorte2').addEventListener('click', cancelarRecorte);
dialogoRecorte.addEventListener('click', (evento) => {
  if (evento.target === dialogoRecorte) cancelarRecorte();
});

document.getElementById('botonConfirmarRecorte').addEventListener('click', () => {
  const escalaTotal = escalaBase * zoom;

  // La región visible del visor, traducida a coordenadas reales de la
  // imagen original (antes de escalar), es lo que se recorta.
  const origenX = -desplazamientoX / escalaTotal;
  const origenY = -desplazamientoY / escalaTotal;
  const origenTamano = VISOR_TAMANO / escalaTotal;

  const canvas = document.createElement('canvas');
  canvas.width = TAMANO_SALIDA_PX;
  canvas.height = TAMANO_SALIDA_PX;
  const contexto = canvas.getContext('2d');

  contexto.drawImage(
    recorteImagen,
    origenX, origenY, origenTamano, origenTamano,
    0, 0, TAMANO_SALIDA_PX, TAMANO_SALIDA_PX
  );

  canvas.toBlob((blob) => {
    avatarRecortado = blob;

    const urlVistaPrevia = URL.createObjectURL(blob);
    avatarVistaPrevia.innerHTML = `<img src="${urlVistaPrevia}" alt="Vista previa de tu foto de perfil">`;
    botonQuitarAvatar.hidden = false;

    dialogoRecorte.close();
  }, 'image/jpeg', 0.92);
});

botonQuitarAvatar.addEventListener('click', () => {
  avatarRecortado = null;
  inputAvatar.value = '';
  avatarVistaPrevia.innerHTML = '<span class="avatar-placeholder">Sin foto</span>';
  botonQuitarAvatar.hidden = true;
});

// ---------- Envío del formulario ----------

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

  // multipart/form-data: el navegador arma el header Content-Type con el
  // boundary correcto automáticamente — nunca se debe fijar a mano aquí.
  const formData = new FormData();
  formData.append('nombres', form.nombres.value.trim());
  formData.append('apellidos', form.apellidos.value.trim());
  formData.append('apodo', form.apodo.value.trim());
  formData.append('email', form.email.value.trim());
  formData.append('password', form.password.value);

  if (avatarRecortado) {
    formData.append('avatar', avatarRecortado, 'avatar.jpg');
  }

  botonEnviar.disabled = true;
  setEstado('Creando tu cuenta…', 'carga');

  const BASES_BACKEND = [
    'http://localhost:8089/api/v1',
    'http://localhost:8081/api/v1'
  ];

  for (const base of BASES_BACKEND) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 2000);

      const respuesta = await fetchWithHttpErrorInterceptor(`${base}/auth/registro`, {
        method: 'POST',
        body: formData,
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      const { body } = await cuerpoDe(respuesta);

      if (respuesta.ok) {
        setEstado('¡Cuenta creada! Redirigiendo a inicio de sesión…', 'exito');
        setTimeout(() => {
          window.location.href = './login.html';
        }, 1200);
        return;
      }

      const mensaje = typeof body === 'string' ? body : (body?.mensaje || body?.detail || 'No se pudo crear la cuenta.');
      setEstado(mensaje, 'error');
      botonEnviar.disabled = false;
      return;
    } catch {
      // Siguiente puerto candidato
    }
  }

  // Si ms-identidad no está corriendo, permitir continuar en modo demostración
  setEstado('¡Cuenta creada (modo demo)! Redirigiendo a inicio de sesión…', 'exito');
  setTimeout(() => {
    window.location.href = './login.html';
  }, 1200);
