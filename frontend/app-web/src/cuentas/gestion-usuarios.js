```javascript
import {
    setCurrentRole,
    applyHasPermissionDirective
} from './directives/has-permission.directive.js';

import {
    fetchWithHttpErrorInterceptor
} from '../comun/interceptors/http-error.interceptor.js';


const BASE_API = '/api/admin/usuarios';

const CLAVE_ROL = 'nexus.rolActual';
const CLAVE_APODO = 'nexus.apodoActual';

const AVATARES_PERMITIDOS = [
    'alquimista-picaro-veneno.jpg',
    'arquero-cazador.jpg',
    'chaman-sanador.jpg',
    'comandante-con-casco.jpg',
    'gran-mago-sabio.jpg',
    'guerrero-berserker.jpg',
    'guerrero-tanque.jpg',
    'mago-de-fuego.jpg',
    'mago-de-hielo.jpg',
    'picaro-asesino.jpg'
];


let usuarioSeleccionado = null;
let accionPendiente = null;


// ============================================================
// INICIALIZACIÓN
// ============================================================

document.addEventListener('DOMContentLoaded', () => {

    const rol = sessionStorage.getItem(CLAVE_ROL);

    if (!rol) {
        window.location.href = './login.html';
        return;
    }

    setCurrentRole(rol);
    applyHasPermissionDirective();

    configurarEventos();
});


// ============================================================
// EVENTOS
// ============================================================

function configurarEventos() {

    document
        .getElementById('btn-cargar')
        .addEventListener('click', cargarUsuario);

    document
        .getElementById('form-perfil')
        .addEventListener('submit', guardarPerfil);

    document
        .getElementById('btn-descartar')
        .addEventListener('click', cargarDatosEnFormulario);

    document
        .getElementById('btn-suspender')
        .addEventListener('click', confirmarSuspension);

    document
        .getElementById('btn-banear')
        .addEventListener('click', confirmarBaneo);

    document
        .getElementById('btn-reactivar')
        .addEventListener('click', confirmarReactivacion);

    document
        .getElementById('btn-restablecer-password')
        .addEventListener('click', confirmarRestablecimiento);

    document
        .getElementById('dialog-cancelar')
        .addEventListener('click', cerrarDialogo);

    document
        .getElementById('dialog-confirmar')
        .addEventListener('click', ejecutarAccionConfirmada);

    document
        .getElementById('btn-volver')
        .addEventListener('click', () => {
            window.history.back();
        });
}


// ============================================================
// CARGAR USUARIO
// ============================================================

async function cargarUsuario() {

    const input = document.getElementById('usuario-id');
    const usuarioId = Number(input.value);

    if (!Number.isInteger(usuarioId) || usuarioId <= 0) {
        mostrarMensaje(
            'mensaje-busqueda',
            'Introduce un ID de usuario válido.',
            'error'
        );
        return;
    }

    ocultarMensaje('mensaje-busqueda');

    try {

        /*
         * No existe actualmente un endpoint de consulta administrativa
         * confirmado en el backend.
         *
         * Por eso utilizamos el endpoint de perfil existente.
         *
         * IMPORTANTE:
         * El endpoint /api/v1/perfiles/{usuarioId} exige X-User-Name
         * y comprueba que sea el dueño del perfil.
         *
         * Por tanto, un administrador NO podrá consultar aquí el perfil
         * de otro usuario con ese endpoint.
         *
         * Se deja esta función preparada para conectarla al endpoint
         * administrativo cuando esté disponible.
         */

        throw new Error(
            'El backend actual no tiene un endpoint de consulta administrativa de usuarios. ' +
            'La edición administrativa ya existe, pero primero necesita recibir el usuario seleccionado.'
        );

    } catch (error) {

        mostrarMensaje(
            'mensaje-busqueda',
            error.message,
            'error'
        );
    }
}


// ============================================================
// PERFIL
// ============================================================

async function guardarPerfil(event) {

    event.preventDefault();

    if (!usuarioSeleccionado) {
        mostrarMensaje(
            'mensaje-perfil',
            'Primero debes seleccionar un usuario.',
            'error'
        );
        return;
    }

    const datos = obtenerDatosFormulario();

    if (!validarDatosPerfil(datos)) {
        return;
    }

    const cambioApodo =
        datos.apodo !== usuarioSeleccionado.apodo;

    if (cambioApodo) {

        abrirDialogo(
            'Cambiar apodo',
            '¿Estás seguro de cambiar el apodo de este usuario?',
            async () => {
                await ejecutarActualizacionPerfil(datos);
            }
        );

        return;
    }

    await ejecutarActualizacionPerfil(datos);
}


async function ejecutarActualizacionPerfil(datos) {

    try {

        const response = await fetchWithHttpErrorInterceptor(
            `${BASE_API}/${usuarioSeleccionado.usuarioId}/perfil`,
            {
                method: 'PUT',
                headers: construirHeaders(),
                body: JSON.stringify(datos)
            }
        );

        if (!response.ok) {
            throw new Error(
                await obtenerMensajeError(response)
            );
        }

        const actualizado = await response.json();

        usuarioSeleccionado = normalizarPerfil(actualizado);

        cargarUsuarioEnPantalla();
        cargarDatosEnFormulario();

        mostrarMensaje(
            'mensaje-perfil',
            'Perfil actualizado correctamente.',
            'success'
        );

    } catch (error) {

        mostrarMensaje(
            'mensaje-perfil',
            error.message,
            'error'
        );
    }
}


function obtenerDatosFormulario() {

    const avatarSeleccionado =
        document.querySelector('input[name="avatar"]:checked');

    return {
        nombres: document.getElementById('nombres').value.trim(),
        apellidos: document.getElementById('apellidos').value.trim(),
        avatar: avatarSeleccionado
            ? avatarSeleccionado.value
            : null,
        biografia: document.getElementById('biografia').value.trim(),
        preferencias: document.getElementById('preferencias').value.trim(),
        apodo: document.getElementById('apodo').value.trim()
    };
}


function validarDatosPerfil(datos) {

    if (!datos.nombres) {
        mostrarMensaje(
            'mensaje-perfil',
            'Los nombres son obligatorios.',
            'error'
        );
        return false;
    }

    if (!datos.apellidos) {
        mostrarMensaje(
            'mensaje-perfil',
            'Los apellidos son obligatorios.',
            'error'
        );
        return false;
    }

    if (!datos.apodo) {
        mostrarMensaje(
            'mensaje-perfil',
            'El apodo es obligatorio.',
            'error'
        );
        return false;
    }

    if (
        datos.avatar &&
        !AVATARES_PERMITIDOS.includes(datos.avatar)
    ) {
        mostrarMensaje(
            'mensaje-perfil',
            'El avatar seleccionado no es válido.',
            'error'
        );
        return false;
    }

    return true;
}


function cargarDatosEnFormulario() {

    if (!usuarioSeleccionado) {
        return;
    }

    document.getElementById('nombres').value =
        usuarioSeleccionado.nombres ?? '';

    document.getElementById('apellidos').value =
        usuarioSeleccionado.apellidos ?? '';

    document.getElementById('apodo').value =
        usuarioSeleccionado.apodo ?? '';

    document.getElementById('biografia').value =
        usuarioSeleccionado.biografia ?? '';

    document.getElementById('preferencias').value =
        usuarioSeleccionado.preferencias ?? '';

    const avatar = usuarioSeleccionado.avatar;

    document
        .querySelectorAll('input[name="avatar"]')
        .forEach(input => {
            input.checked = input.value === avatar;
        });
}


// ============================================================
// ESTADO
// ============================================================

function confirmarSuspension() {

    if (!usuarioSeleccionado) {
        return;
    }

    const fecha = document
        .getElementById('suspendido-hasta')
        .value;

    if (!fecha) {

        mostrarMensaje(
            'mensaje-estado',
            'Debes seleccionar hasta cuándo se suspenderá la cuenta.',
            'error'
        );

        return;
    }

    abrirDialogo(
        'Suspender cuenta',
        '¿Estás seguro de suspender esta cuenta?',
        async () => {
            await suspenderCuenta(fecha);
        }
    );
}


async function suspenderCuenta(fecha) {

    try {

        const response =
            await fetchWithHttpErrorInterceptor(
                `${BASE_API}/${usuarioSeleccionado.usuarioId}/suspender`,
                {
                    method: 'PUT',
                    headers: construirHeaders(),
                    body: JSON.stringify({
                        suspendidoHasta: convertirFecha(fecha)
                    })
                }
            );

        if (!response.ok) {
            throw new Error(
                await obtenerMensajeError(response)
            );
        }

        mostrarMensaje(
            'mensaje-estado',
            'Cuenta suspendida correctamente.',
            'success'
        );

        await refrescarEstadoVisual('SUSPENDIDA');

    } catch (error) {

        mostrarMensaje(
            'mensaje-estado',
            error.message,
            'error'
        );
    }
}


function confirmarBaneo() {

    if (!usuarioSeleccionado) {
        return;
    }

    abrirDialogo(
        'Baneo definitivo',
        'Esta acción es permanente. ¿Estás seguro de banear esta cuenta?',
        async () => {
            await banearCuenta();
        }
    );
}


async function banearCuenta() {

    try {

        const response =
            await fetchWithHttpErrorInterceptor(
                `${BASE_API}/${usuarioSeleccionado.usuarioId}/banear`,
                {
                    method: 'PUT',
                    headers: construirHeaders()
                }
            );

        if (!response.ok) {
            throw new Error(
                await obtenerMensajeError(response)
            );
        }

        mostrarMensaje(
            'mensaje-estado',
            'Cuenta baneada definitivamente.',
            'success'
        );

        await refrescarEstadoVisual('BANEADA');

    } catch (error) {

        mostrarMensaje(
            'mensaje-estado',
            error.message,
            'error'
        );
    }
}


function confirmarReactivacion() {

    if (!usuarioSeleccionado) {
        return;
    }

    abrirDialogo(
        'Reactivar cuenta',
        '¿Estás seguro de reactivar esta cuenta?',
        async () => {
            await reactivarCuenta();
        }
    );
}


async function reactivarCuenta() {

    try {

        const response =
            await fetchWithHttpErrorInterceptor(
                `${BASE_API}/${usuarioSeleccionado.usuarioId}/reactivar`,
                {
                    method: 'PUT',
                    headers: construirHeaders()
                }
            );

        if (!response.ok) {
            throw new Error(
                await obtenerMensajeError(response)
            );
        }

        mostrarMensaje(
            'mensaje-estado',
            'Cuenta reactivada correctamente.',
            'success'
        );

        await refrescarEstadoVisual('ACTIVO');

    } catch (error) {

        mostrarMensaje(
            'mensaje-estado',
            error.message,
            'error'
        );
    }
}


// ============================================================
// PASSWORD
// ============================================================

function confirmarRestablecimiento() {

    if (!usuarioSeleccionado) {
        return;
    }

    abrirDialogo(
        'Restablecer contraseña',
        'Se generará un mecanismo de restablecimiento para el usuario. ¿Deseas continuar?',
        async () => {
            await restablecerPassword();
        }
    );
}


async function restablecerPassword() {

    try {

        const response =
            await fetchWithHttpErrorInterceptor(
                `${BASE_API}/${usuarioSeleccionado.usuarioId}/restablecer-password`,
                {
                    method: 'POST',
                    headers: construirHeaders()
                }
            );

        if (!response.ok) {
            throw new Error(
                await obtenerMensajeError(response)
            );
        }

        mostrarMensaje(
            'mensaje-password',
            'El restablecimiento fue generado correctamente. El usuario deberá completar el proceso mediante el mecanismo de recuperación.',
            'success'
        );

    } catch (error) {

        mostrarMensaje(
            'mensaje-password',
            error.message,
            'error'
        );
    }
}


// ============================================================
// INTERFAZ
// ============================================================

function cargarUsuarioEnPantalla() {

    if (!usuarioSeleccionado) {
        return;
    }

    document
        .getElementById('panel-usuario')
        .classList.remove('hidden');

    document
        .getElementById('panel-perfil')
        .classList.remove('hidden');

    document
        .getElementById('panel-estado')
        .classList.remove('hidden');

    document
        .getElementById('panel-password')
        .classList.remove('hidden');

    document.getElementById('usuario-apodo').textContent =
        usuarioSeleccionado.apodo ?? 'Usuario';

    document.getElementById('usuario-apodo-display').textContent =
        usuarioSeleccionado.apodo ?? '-';

    document.getElementById('usuario-email').textContent =
        usuarioSeleccionado.email ?? '-';

    document.getElementById('usuario-id-display').textContent =
        usuarioSeleccionado.usuarioId ?? '-';

    document.getElementById('usuario-nombre-display').textContent =
        `${usuarioSeleccionado.nombres ?? ''} ${usuarioSeleccionado.apellidos ?? ''}`.trim();

    document.getElementById('usuario-estado').textContent =
        usuarioSeleccionado.estado ?? '-';

    if (usuarioSeleccionado.avatar) {

        document.getElementById('usuario-avatar').src =
            `./avatares/${usuarioSeleccionado.avatar}`;
    }

    cargarDatosEnFormulario();
}


function refrescarEstadoVisual(nuevoEstado) {

    usuarioSeleccionado.estado = nuevoEstado;

    document.getElementById('usuario-estado').textContent =
        nuevoEstado;
}


// ============================================================
// NORMALIZACIÓN
// ============================================================

function normalizarPerfil(perfil) {

    return {
        usuarioId:
            perfil.usuarioId ?? perfil.id,

        apodo:
            perfil.apodo,

        email:
            perfil.email,

        nombres:
            perfil.nombres,

        apellidos:
            perfil.apellidos,

        avatar:
            perfil.avatar,

        biografia:
            perfil.biografia,

        preferencias:
            perfil.preferencias,

        estado:
            perfil.estado
    };
}


// ============================================================
// HEADERS
// ============================================================

function construirHeaders() {

    const apodo =
        sessionStorage.getItem(CLAVE_APODO);

    const rol =
        sessionStorage.getItem(CLAVE_ROL);

    return {
        'Content-Type': 'application/json',
        'X-User-Name': apodo ?? '',
        'X-User-Role': rol ?? ''
    };
}


// ============================================================
// FECHAS
// ============================================================

function convertirFecha(fecha) {

    /*
     * datetime-local produce:
     *
     * 2026-09-01T20:30
     *
     * Se mantiene como fecha local porque el backend recibe
     * LocalDateTime.
     */

    return fecha;
}


// ============================================================
// DIÁLOGO
// ============================================================

function abrirDialogo(titulo, mensaje, accion) {

    const dialog =
        document.getElementById('dialog-confirmacion');

    document.getElementById('dialog-titulo').textContent =
        titulo;

    document.getElementById('dialog-mensaje').textContent =
        mensaje;

    accionPendiente = accion;

    dialog.showModal();
}


function cerrarDialogo() {

    accionPendiente = null;

    document
        .getElementById('dialog-confirmacion')
        .close();
}


async function ejecutarAccionConfirmada() {

    const accion = accionPendiente;

    cerrarDialogo();

    if (accion) {
        await accion();
    }
}


// ============================================================
// MENSAJES
// ============================================================

function mostrarMensaje(id, texto, tipo = '') {

    const elemento = document.getElementById(id);

    elemento.textContent = texto;
    elemento.classList.remove('hidden', 'error', 'success');

    if (tipo) {
        elemento.classList.add(tipo);
    }
}


function ocultarMensaje(id) {

    document
        .getElementById(id)
        .classList.add('hidden');
}


// ============================================================
// ERRORES HTTP
// ============================================================

async function obtenerMensajeError(response) {

    try {

        const body = await response.json();

        if (typeof body === 'string') {
            return body;
        }

        return (
            body.detail ||
            body.message ||
            'La operación no pudo completarse.'
        );

    } catch {

        return 'La operación no pudo completarse.';
    }
}
```
