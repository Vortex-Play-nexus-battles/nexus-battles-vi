// registro.js
// Vista de registro — HU-AUT-001.
//
// R17 — crear la cuenta ya no termina en «ahora ve al login y escribe otra vez
// lo mismo»: se entra solo y se pasa a «Preparando tu cuenta», que cuenta en
// vivo cómo el servidor le da al jugador sus créditos y su héroe. Los
// rechazos marcan el campo exacto (problem details con `campo`), y la
// política de contraseñas se dice antes de enviar.
import { registrarYEntrar } from '../comun/entrada.js';
import { motivoDeContrasena } from '../comun/politica-contrasena.js';
import { marcarErrorDe } from '../comun/ui/campo.js';
import { h } from '../comun/ui/dom.js';

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

/**
 * Dice qué le falta a la contraseña. Al salir del campo y al enviar; mientras
 * se escribe solo se quita el aviso cuando ya cumple, para no regañar a quien
 * todavía no ha terminado de escribirla.
 *
 * @returns {boolean} si cumple la política
 */
function validarContrasena() {
  const motivo = motivoDeContrasena(campoPassword.value);
  marcarErrorDe(campoPassword, motivo);
  return motivo === null;
}

campoConfirmar.addEventListener('input', validarConfirmacion);
campoPassword.addEventListener('input', () => {
  if (campoConfirmar.value.length > 0) {
    validarConfirmacion();
  }
  if (
    campoPassword.getAttribute('aria-invalid') === 'true' &&
    !motivoDeContrasena(campoPassword.value)
  ) {
    marcarErrorDe(campoPassword, null);
  }
});
campoPassword.addEventListener('blur', () => {
  if (campoPassword.value.length > 0) {
    validarContrasena();
  }
});

// Lo que se escribe en un campo marcado por el servidor lo desmarca: el
// motivo se refería al valor anterior.
for (const nombre of ['nombres', 'apellidos', 'apodo', 'email']) {
  const control = form.elements.namedItem(nombre);
  control?.addEventListener('input', () => {
    if (control.getAttribute('aria-invalid') === 'true') {
      marcarErrorDe(control, null);
    }
  });
}

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
  if (!archivo) {
    return;
  }

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
  recorteImagen.style.transform = `translate(${desplazamientoX}px, ${desplazamientoY}px) scale(${escalaTotal})`;
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
  if (!arrastrando) {
    return;
  }
  desplazamientoX = desplazamientoInicioX + (evento.clientX - arrastreInicioX);
  desplazamientoY = desplazamientoInicioY + (evento.clientY - arrastreInicioY);
  aplicarTransformacion();
});

recorteVisor.addEventListener('pointerup', () => {
  arrastrando = false;
});
recorteVisor.addEventListener('pointercancel', () => {
  arrastrando = false;
});

function cancelarRecorte() {
  dialogoRecorte.close();
  inputAvatar.value = '';
}

document.getElementById('botonCancelarRecorte').addEventListener('click', cancelarRecorte);
document.getElementById('botonCancelarRecorte2').addEventListener('click', cancelarRecorte);
dialogoRecorte.addEventListener('click', (evento) => {
  if (evento.target === dialogoRecorte) {
    cancelarRecorte();
  }
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
    origenX,
    origenY,
    origenTamano,
    origenTamano,
    0,
    0,
    TAMANO_SALIDA_PX,
    TAMANO_SALIDA_PX,
  );

  canvas.toBlob(
    (blob) => {
      avatarRecortado = blob;

      // R17 — con nodos y no con una plantilla en `innerHTML`: la URL entra
      // como atributo y no pasa por el analizador de HTML.
      const urlVistaPrevia = URL.createObjectURL(blob);
      avatarVistaPrevia.replaceChildren(
        h('img', { atributos: { src: urlVistaPrevia, alt: 'Vista previa de tu foto de perfil' } }),
      );
      botonQuitarAvatar.hidden = false;

      dialogoRecorte.close();
    },
    'image/jpeg',
    0.92,
  );
});

botonQuitarAvatar.addEventListener('click', () => {
  avatarRecortado = null;
  inputAvatar.value = '';
  avatarVistaPrevia.replaceChildren(h('span', { clase: 'avatar-placeholder', texto: 'Sin foto' }));
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
  if (!validarContrasena()) {
    campoPassword.focus();
    return;
  }
  if (!validarConfirmacion()) {
    campoConfirmar.focus();
    return;
  }

  // multipart/form-data: el navegador arma el header Content-Type con el
  // boundary correcto automáticamente — nunca se debe fijar a mano aquí.
  const email = form.email.value.trim();
  const formData = new FormData();
  formData.append('nombres', form.nombres.value.trim());
  formData.append('apellidos', form.apellidos.value.trim());
  formData.append('apodo', form.apodo.value.trim());
  formData.append('email', email);
  formData.append('password', form.password.value);

  if (avatarRecortado) {
    formData.append('avatar', avatarRecortado, 'avatar.jpg');
  }

  botonEnviar.disabled = true;
  setEstado('Creando tu cuenta…', 'carga');

  let resultado;
  try {
    resultado = await registrarYEntrar(formData, { email, password: form.password.value });
  } catch {
    // Solo llega aquí un fallo de red al CREAR la cuenta: no se sabe si se
    // creó, y se dice así en vez de prometer nada.
    setEstado(
      'No pudimos confirmar que la cuenta se creara: la conexión falló. Prueba a entrar con ese correo antes de registrarte otra vez.',
      'error',
    );
    botonEnviar.disabled = false;
    return;
  }

  if (resultado.resultado === 'rechazada') {
    setEstado(resultado.mensaje, 'error');
    const control = resultado.campo ? form.elements.namedItem(resultado.campo) : null;
    if (control instanceof HTMLElement && control.type !== 'file') {
      marcarErrorDe(control, resultado.mensaje);
      control.focus();
    }
    botonEnviar.disabled = false;
    return;
  }

  // Cuenta creada. El botón se queda desactivado: la página ya se va.
  setEstado(
    resultado.resultado === 'dentro'
      ? '¡Cuenta creada! Preparando tu cuenta…'
      : '¡Cuenta creada! Te llevamos a la entrada…',
    'exito',
  );
  window.location.href = resultado.destino;
});
