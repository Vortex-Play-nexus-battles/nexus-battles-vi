import {
setCurrentRole,
getCurrentRole,
checkPermission,
applyHasPermissionDirective
} from './directives/has-permission.directive.js';

import {
fetchWithHttpErrorInterceptor
} from '../comun/interceptors/http-error.interceptor.js';
import { construirBarra } from '../comun/barra-navegacion.js';


const BASE_API = '/api/admin/usuarios';

const CLAVE_ROL = 'nexus.rolActual';
const CLAVE_USUARIO_ID = 'nexus.usuarioId';

const PERMISO_GESTIONAR = 'GESTIONAR_CUENTAS';
const PERMISO_SUSPENDER = 'SUSPENDER_USUARIOS';
const PERMISO_BANEAR = 'BANEAR_DEFINITIVAMENTE';

let usuarioSeleccionado = null;

document.addEventListener('DOMContentLoaded', iniciar);


function montarBarraNavegacion() {
    const rolActual = sessionStorage.getItem(CLAVE_ROL);
    const barra = construirBarra({
        seccionActiva: 'cuenta',
        sesion: { autenticado: !!rolActual },
        navegar: (ruta) => { location.href = ruta; }
    });
    document.body.prepend(barra);
}

function montarMenuAdmin() {
    const rolActual = sessionStorage.getItem(CLAVE_ROL);
    const rolesConAcceso = ['ADMINISTRADOR', 'SUPER_ADMINISTRADOR'];

    if (!rolesConAcceso.includes(rolActual)) {
        return;
    }

    const menu = document.createElement('div');
    menu.className = 'menu-admin';

    const enlaceCrear = document.createElement('a');
    enlaceCrear.href = './crear-cuenta-admin.html';
    enlaceCrear.textContent = 'Crear cuenta admin';
    if (rolActual !== 'SUPER_ADMINISTRADOR') {
        enlaceCrear.style.display = 'none';
    }

    const enlaceGestion = document.createElement('a');
    enlaceGestion.href = './gestion-usuarios.html';
    enlaceGestion.textContent = 'Gestion de usuarios';

    menu.append(enlaceCrear, enlaceGestion);
    document.body.insertBefore(menu, document.body.children[1]);
}

function iniciar() {

montarBarraNavegacion();

montarMenuAdmin();

const rolActual =
    sessionStorage.getItem(CLAVE_ROL) || 'JUGADOR';

setCurrentRole(rolActual);

applyHasPermissionDirective();

configurarEventos();

verificarAcceso();

}

function configurarEventos() {

const formularioBusqueda =
    document.getElementById('form-buscar-usuario');

const formularioPerfil =
    document.getElementById('formulario-perfil-admin');

const btnVolver =
    document.getElementById('btn-volver');

const btnVolverAcceso =
    document.getElementById('btn-volver-acceso');

const btnSuspender =
    document.getElementById('btn-suspender');

const btnReactivar =
    document.getElementById('btn-reactivar');

const btnBanear =
    document.getElementById('btn-banear');

const btnRestablecerPassword =
    document.getElementById('btn-restablecer-password');


if (formularioBusqueda) {

    formularioBusqueda.addEventListener(
        'submit',
        buscarUsuario
    );

}


if (formularioPerfil) {

    formularioPerfil.addEventListener(
        'submit',
        guardarPerfil
    );

}


if (btnVolver) {

    btnVolver.addEventListener(
        'click',
        volverInicio
    );

}


if (btnVolverAcceso) {

    btnVolverAcceso.addEventListener(
        'click',
        volverInicio
    );

}


if (btnSuspender) {

    btnSuspender.addEventListener(
        'click',
        suspenderUsuario
    );

}


if (btnReactivar) {

    btnReactivar.addEventListener(
        'click',
        reactivarUsuario
    );

}


if (btnBanear) {

    btnBanear.addEventListener(
        'click',
        banearUsuario
    );

}


if (btnRestablecerPassword) {

    btnRestablecerPassword.addEventListener(
        'click',
        restablecerPassword
    );

}

}

function verificarAcceso() {

const contenedor =
    document.getElementById('gestion-contenedor');

const accesoDenegado =
    document.getElementById('acceso-denegado');


if (!contenedor || !accesoDenegado) {
    return;
}


const tienePermiso =
    checkPermission(PERMISO_GESTIONAR);


if (!tienePermiso) {

    contenedor.hidden = true;

    accesoDenegado.hidden = false;

    return;
}


contenedor.hidden = false;

accesoDenegado.hidden = true;

}

async function buscarUsuario(evento) {

evento.preventDefault();


limpiarMensajeBusqueda();


const input =
    document.getElementById('usuario-id');

if (!input) {
    return;
}


const usuarioId =
    input.value.trim();


if (!usuarioId || Number(usuarioId) <= 0) {

    mostrarMensajeBusqueda(
        'Debes ingresar un ID de usuario válido.'
    );

    return;
}


/*
 * Actualmente el backend conocido no expone todavía
 * un GET administrativo confirmado para consultar
 * la información completa del usuario.
 *
 * Por eso esta función deja seleccionado el ID
 * y prepara la interfaz para el futuro endpoint.
 */

usuarioSeleccionado = {
    id: Number(usuarioId)
};


sessionStorage.setItem(
    CLAVE_USUARIO_ID,
    String(usuarioId)
);


mostrarPanelUsuario();


limpiarDatosUsuario();


mostrarMensajeBusqueda(
    'Usuario seleccionado. La consulta de sus datos quedará conectada cuando el backend exponga el endpoint administrativo de consulta.'
);

}

function mostrarPanelUsuario() {

const panel =
    document.getElementById('panel-usuario');

if (!panel) {
    return;
}


panel.hidden = false;

}

function limpiarDatosUsuario() {

establecerTexto(
    'usuario-id-mostrado',
    usuarioSeleccionado?.id ?? '-'
);

establecerTexto(
    'usuario-apodo-mostrado',
    '-'
);

establecerTexto(
    'usuario-email-mostrado',
    '-'
);

establecerTexto(
    'usuario-rol-mostrado',
    '-'
);

establecerTexto(
    'usuario-estado-mostrado',
    '-'
);


establecerValor('nombres', '');
establecerValor('apellidos', '');
establecerValor('apodo', '');
establecerValor('avatar', '');
establecerValor('biografia', '');
establecerValor('preferencias', '');
establecerValor('estado', 'ACTIVO');
establecerValor('suspendido-hasta', '');

}

async function guardarPerfil(evento) {

evento.preventDefault();


if (!validarUsuarioSeleccionado()) {
    return;
}


if (!checkPermission(PERMISO_GESTIONAR)) {

    mostrarMensajePerfil(
        'No tienes permisos para modificar cuentas.'
    );

    return;
}


const nombres =
    obtenerValor('nombres');

const apellidos =
    obtenerValor('apellidos');

const apodo =
    obtenerValor('apodo');

const avatar =
    obtenerValor('avatar');

const biografia =
    obtenerValor('biografia');

const preferencias =
    obtenerValor('preferencias');


if (!nombres) {

    mostrarMensajePerfil(
        'Los nombres son obligatorios.'
    );

    return;
}


if (!apellidos) {

    mostrarMensajePerfil(
        'Los apellidos son obligatorios.'
    );

    return;
}


if (!apodo) {

    mostrarMensajePerfil(
        'El apodo es obligatorio.'
    );

    return;
}


const confirmado =
    window.confirm(
        `¿Deseas guardar los cambios del usuario ${usuarioSeleccionado.id}?`
    );


if (!confirmado) {
    return;
}


const boton =
    document.getElementById('btn-guardar-perfil');

cambiarEstadoBoton(
    boton,
    true,
    'Guardando...'
);


try {

    const respuesta =
        await fetchWithHttpErrorInterceptor(
            `${BASE_API}/${usuarioSeleccionado.id}/perfil`,
            {
                method: 'PUT',

                headers: {
                    'Content-Type': 'application/json'
                },

                body: JSON.stringify({
                    nombres,
                    apellidos,
                    apodo,
                    avatar,
                    biografia,
                    preferencias
                })
            }
        );


    if (!respuesta.ok) {

        throw new Error(
            await obtenerMensajeError(respuesta)
        );

    }


    mostrarMensajePerfil(
        'Perfil actualizado correctamente.'
    );


    actualizarResumenUsuario({
        apodo,
        avatar
    });


} catch (error) {

    console.error(
        'Error actualizando perfil:',
        error
    );

    mostrarMensajePerfil(
        error.message ||
        'No fue posible actualizar el perfil.'
    );

} finally {

    cambiarEstadoBoton(
        boton,
        false,
        'Guardar cambios'
    );

}

}

async function suspenderUsuario() {

if (!validarUsuarioSeleccionado()) {
    return;
}


if (!checkPermission(PERMISO_SUSPENDER)) {

    mostrarMensajePerfil(
        'No tienes permisos para suspender usuarios.'
    );

    return;
}


const suspendidoHasta =
    obtenerValor('suspendido-hasta');


if (!suspendidoHasta) {

    mostrarMensajePerfil(
        'Debes indicar hasta cuándo estará suspendida la cuenta.'
    );

    return;
}


const confirmado =
    window.confirm(
        `¿Deseas suspender al usuario ${usuarioSeleccionado.id}?`
    );


if (!confirmado) {
    return;
}


await ejecutarAccionEstado(
    `/suspender`,
    'SUSPENDIDO',
    'Cuenta suspendida correctamente.'
);

}

async function reactivarUsuario() {

if (!validarUsuarioSeleccionado()) {
    return;
}


if (!checkPermission(PERMISO_SUSPENDER)) {

    mostrarMensajePerfil(
        'No tienes permisos para reactivar usuarios.'
    );

    return;
}


const confirmado =
    window.confirm(
        `¿Deseas reactivar al usuario ${usuarioSeleccionado.id}?`
    );


if (!confirmado) {
    return;
}


await ejecutarAccionEstado(
    `/reactivar`,
    'ACTIVO',
    'Cuenta reactivada correctamente.'
);

}

async function banearUsuario() {

if (!validarUsuarioSeleccionado()) {
    return;
}


if (!checkPermission(PERMISO_BANEAR)) {

    mostrarMensajePerfil(
        'No tienes permisos para banear definitivamente a este usuario.'
    );

    return;
}


const confirmado =
    window.confirm(
        `Esta acción es permanente. ¿Deseas banear definitivamente al usuario ${usuarioSeleccionado.id}?`
    );


if (!confirmado) {
    return;
}


await ejecutarAccionEstado(
    `/banear`,
    'BANEADO',
    'Cuenta baneada definitivamente.'
);

}

async function ejecutarAccionEstado(
ruta,
estado,
mensajeExito
) {

try {

    const respuesta =
        await fetchWithHttpErrorInterceptor(
            `${BASE_API}/${usuarioSeleccionado.id}${ruta}`,
            {
                method: 'PUT'
            }
        );


    if (!respuesta.ok) {

        throw new Error(
            await obtenerMensajeError(respuesta)
        );

    }


    establecerValor(
        'estado',
        estado
    );


    establecerTexto(
        'usuario-estado-mostrado',
        estado
    );


    mostrarMensajePerfil(
        mensajeExito
    );


} catch (error) {

    console.error(
        'Error modificando estado:',
        error
    );

    mostrarMensajePerfil(
        error.message ||
        'No fue posible modificar el estado de la cuenta.'
    );

}

}

async function restablecerPassword() {

if (!validarUsuarioSeleccionado()) {
    return;
}


if (!checkPermission(PERMISO_GESTIONAR)) {

    mostrarMensajePerfil(
        'No tienes permisos para restablecer contraseñas.'
    );

    return;
}


const confirmado =
    window.confirm(
        `¿Deseas restablecer la contraseña del usuario ${usuarioSeleccionado.id}? El usuario deberá completar el mecanismo seguro de restablecimiento.`
    );


if (!confirmado) {
    return;
}


try {

    const respuesta =
        await fetchWithHttpErrorInterceptor(
            `${BASE_API}/${usuarioSeleccionado.id}/restablecer-password`,
            {
                method: 'POST'
            }
        );


    if (!respuesta.ok) {

        throw new Error(
            await obtenerMensajeError(respuesta)
        );

    }


    mostrarMensajePerfil(
        'El restablecimiento de contraseña fue solicitado correctamente.'
    );


} catch (error) {

    console.error(
        'Error restableciendo contraseña:',
        error
    );

    mostrarMensajePerfil(
        error.message ||
        'No fue posible restablecer la contraseña.'
    );

}

}

function validarUsuarioSeleccionado() {

if (!usuarioSeleccionado?.id) {

    mostrarMensajePerfil(
        'Primero debes seleccionar un usuario.'
    );

    return false;
}


return true;

}

function actualizarResumenUsuario(datos) {

if (datos.apodo !== undefined) {

    establecerTexto(
        'usuario-apodo-mostrado',
        datos.apodo
    );

}


if (datos.avatar !== undefined) {

    establecerTexto(
        'usuario-email-mostrado',
        establecerTexto
    );

}

}

function obtenerValor(id) {

const elemento =
    document.getElementById(id);

return elemento
    ? elemento.value.trim()
    : '';

}

function establecerValor(id, valor) {

const elemento =
    document.getElementById(id);

if (elemento) {
    elemento.value = valor ?? '';
}

}

function establecerTexto(id, texto) {

const elemento =
    document.getElementById(id);

if (elemento) {
    elemento.textContent = String(texto ?? '-');
}

}

function cambiarEstadoBoton(
boton,
deshabilitado,
texto
) {

if (!boton) {
    return;
}


boton.disabled = deshabilitado;

boton.textContent = texto;

}

function mostrarMensajeBusqueda(mensaje) {

const elemento =
    document.getElementById('mensaje-busqueda');

if (!elemento) {
    return;
}


elemento.textContent = mensaje;

elemento.hidden = false;

}

function limpiarMensajeBusqueda() {

const elemento =
    document.getElementById('mensaje-busqueda');

if (!elemento) {
    return;
}


elemento.textContent = '';

elemento.hidden = true;

}

function mostrarMensajePerfil(mensaje) {

const elemento =
    document.getElementById('mensaje-perfil');

if (!elemento) {
    return;
}


elemento.textContent = mensaje;

elemento.hidden = false;

}

async function obtenerMensajeError(respuesta) {

try {

    const datos =
        await respuesta.clone().json();


    if (typeof datos === 'string') {
        return datos;
    }


    return (
        datos.detail ||
        datos.message ||
        datos.title ||
        `Error HTTP ${respuesta.status}`
    );

} catch {

    try {

        const texto =
            await respuesta.clone().text();

        if (texto) {
            return texto;
        }

    } catch {
        // Se utiliza el mensaje genérico.
    }


    return `Error HTTP ${respuesta.status}`;

}

}

function volverInicio() {

window.location.href = '../index.html';

}
