import {
    fetchWithHttpErrorInterceptor
} from '../comun/interceptors/http-error.interceptor.js';

import {
    setCurrentRole,
    getCurrentRole
} from './directives/has-permission.directive.js';
import { construirBarra } from '../comun/barra-navegacion.js';



const BASE_API = '/api/v1/perfiles';
const RUTA_LOGIN = './login.html';

const CLAVE_USUARIO_ID = 'nexus.usuarioId';
const CLAVE_APODO = 'nexus.apodoActual';
const CLAVE_ROL = 'nexus.rolActual';


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


let perfilOriginal = null;
let guardadoPendiente = false;


/* =========================
   ELEMENTOS DEL DOM
   ========================= */

const estadoCarga = document.getElementById('estado-carga');
const estadoError = document.getElementById('estado-error');
const estadoVacio = document.getElementById('estado-vacio');

const mensajeError = document.getElementById('mensaje-error');

const formulario = document.getElementById('formulario-perfil');

const campoApodo = document.getElementById('campo-apodo');
const campoNombres = document.getElementById('campo-nombres');
const campoApellidos = document.getElementById('campo-apellidos');
const campoBiografia = document.getElementById('campo-biografia');
const campoPreferencias = document.getElementById('campo-preferencias');

const galeriaAvatares = document.getElementById('galeria-avatares');

const btnGuardar = document.getElementById('btn-guardar');
const btnDescartar = document.getElementById('btn-descartar');
const btnReintentar = document.getElementById('btn-reintentar');

const mensajeFormulario = document.getElementById(
    'mensaje-formulario'
);

const dialogoApodo = document.getElementById(
    'dialogo-apodo'
);

const btnCancelarApodo = document.getElementById(
    'btn-cancelar-apodo'
);

const btnConfirmarApodo = document.getElementById(
    'btn-confirmar-apodo'
);


/* =========================
   SESIÓN
   ========================= */

function obtenerSesionActual() {

    const usuarioId =
        sessionStorage.getItem(CLAVE_USUARIO_ID);

    const apodo =
        sessionStorage.getItem(CLAVE_APODO);

    const rolPersistido =
        sessionStorage.getItem(CLAVE_ROL);


    /*
     * El rol se mantiene también en memoria para que
     * data-has-permission funcione correctamente.
     */
    if (rolPersistido) {
        setCurrentRole(rolPersistido);
    }


    const rol =
        typeof getCurrentRole === 'function'
            ? getCurrentRole()
            : rolPersistido;


    return {
        usuarioId,
        apodo,
        rol
    };
}


/* =========================
   BARRA Y MENU ADMIN
   ========================= */

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

/* =========================
   ESTADOS
   ========================= */

function mostrarCarga() {

    estadoCarga.classList.remove('oculto');
    estadoError.classList.add('oculto');
    estadoVacio.classList.add('oculto');
    formulario.classList.add('oculto');
}


function mostrarFormulario() {

    estadoCarga.classList.add('oculto');
    estadoError.classList.add('oculto');
    estadoVacio.classList.add('oculto');
    formulario.classList.remove('oculto');
}


function mostrarError(mensaje) {

    estadoCarga.classList.add('oculto');
    estadoError.classList.remove('oculto');
    estadoVacio.classList.add('oculto');
    formulario.classList.add('oculto');

    mensajeError.textContent = mensaje;
}


function mostrarVacio() {

    estadoCarga.classList.add('oculto');
    estadoError.classList.add('oculto');
    estadoVacio.classList.remove('oculto');
    formulario.classList.add('oculto');
}


/* =========================
   AVATARES
   ========================= */

function avatarPermitido(avatar) {

    return AVATARES_PERMITIDOS.includes(avatar);
}


function obtenerAvatarSeleccionado() {

    const seleccionado =
        document.querySelector(
            'input[name="avatar"]:checked'
        );

    return seleccionado
        ? seleccionado.value
        : null;
}


function seleccionarAvatar(nombreAvatar) {

    if (!avatarPermitido(nombreAvatar)) {
        return;
    }


    const opcion =
        document.querySelector(
            `input[name="avatar"][value="${CSS.escape(nombreAvatar)}"]`
        );


    if (opcion) {
        opcion.checked = true;
    }
}


function validarAvatarDelBackend(avatar) {

    if (!avatar) {
        return false;
    }

    return avatarPermitido(avatar);
}


/* =========================
   FORMULARIO
   ========================= */

function llenarFormulario(perfil) {

    campoApodo.value =
        perfil.apodo ?? '';

    campoNombres.value =
        perfil.nombres ?? '';

    campoApellidos.value =
        perfil.apellidos ?? '';

    campoBiografia.value =
        perfil.biografia ?? '';

    campoPreferencias.value =
        perfil.preferencias ?? '';


    if (
        perfil.avatar &&
        validarAvatarDelBackend(perfil.avatar)
    ) {

        seleccionarAvatar(perfil.avatar);

    } else {

        const primerAvatar =
            document.querySelector(
                'input[name="avatar"]'
            );

        if (primerAvatar) {
            primerAvatar.checked = true;
        }
    }
}


function construirCuerpoActualizacion() {

    const avatar =
        obtenerAvatarSeleccionado();


    if (!avatar || !avatarPermitido(avatar)) {

        throw new Error(
            'Debes seleccionar uno de los avatares disponibles.'
        );
    }


    const cuerpo = {

        nombres:
            campoNombres.value.trim(),

        apellidos:
            campoApellidos.value.trim(),

        avatar:
            avatar,

        biografia:
            campoBiografia.value.trim(),

        preferencias:
            campoPreferencias.value.trim()
    };


    const apodoActual =
        perfilOriginal?.apodo ?? '';

    const nuevoApodo =
        campoApodo.value.trim();


    /*
     * El apodo solamente se envía cuando realmente cambió.
     * La validación de blacklist y unicidad corresponde al backend.
     */
    if (
        nuevoApodo &&
        nuevoApodo !== apodoActual
    ) {

        cuerpo.apodo = nuevoApodo;
    }


    return cuerpo;
}


/* =========================
   CARGAR PERFIL
   ========================= */

async function cargarPerfil() {

    mostrarCarga();


    const sesion =
        obtenerSesionActual();


    /*
     * El usuarioId proviene del login y se mantiene
     * en sessionStorage como nexus.usuarioId.
     */
    if (!sesion.usuarioId) {

        window.location.href = RUTA_LOGIN;

        return;
    }


    try {

        /*
         * No enviamos X-User-Name ni X-User-Role.
         *
         * El interceptor agrega automáticamente:
         *
         * Authorization: Bearer <token>
         */
        const respuesta =
            await fetchWithHttpErrorInterceptor(
                `${BASE_API}/${encodeURIComponent(sesion.usuarioId)}`,
                {
                    method: 'GET'
                }
            );


        if (!respuesta.ok) {

            let mensaje =
                `No fue posible cargar el perfil. Código ${respuesta.status}.`;


            try {

                const errorBody =
                    await respuesta.json();

                if (errorBody?.detail) {
                    mensaje = errorBody.detail;
                } else if (
                    typeof errorBody === 'string' &&
                    errorBody.trim()
                ) {
                    mensaje = errorBody;
                }

            } catch {
                // La respuesta puede no tener cuerpo JSON.
            }


            throw new Error(mensaje);
        }


        const perfil =
            await respuesta.json();


        if (!perfil) {

            mostrarVacio();

            return;
        }


        perfilOriginal = {
            ...perfil
        };


        /*
         * Si el backend devuelve el apodo actual,
         * sincronizamos también la sesión.
         */
        if (perfil.apodo) {

            sessionStorage.setItem(
                CLAVE_APODO,
                perfil.apodo
            );
        }


        llenarFormulario(perfil);

        mostrarFormulario();

    } catch (error) {

        console.error(
            'Error cargando perfil:',
            error
        );

        mostrarError(
            error.message ||
            'No fue posible cargar tu perfil.'
        );
    }
}


/* =========================
   GUARDAR PERFIL
   ========================= */

async function guardarPerfil() {

    if (guardadoPendiente) {
        return;
    }


    let cuerpo;


    try {

        cuerpo =
            construirCuerpoActualizacion();

    } catch (error) {

        mostrarMensajeFormulario(
            error.message,
            true
        );

        return;
    }


    if (
        !cuerpo.nombres ||
        !cuerpo.apellidos
    ) {

        mostrarMensajeFormulario(
            'Nombres y apellidos son obligatorios.',
            true
        );

        return;
    }


    const sesion =
        obtenerSesionActual();


    if (!sesion.usuarioId) {

        window.location.href = RUTA_LOGIN;

        return;
    }


    const apodoOriginal =
        perfilOriginal?.apodo ?? '';

    const cambioApodo =
        Boolean(
            cuerpo.apodo &&
            cuerpo.apodo !== apodoOriginal
        );


    /*
     * El cambio de apodo requiere confirmación explícita.
     */
    if (cambioApodo) {

        guardadoPendiente = true;

        dialogoApodo.showModal();

        return;
    }


    await enviarActualizacion(
        sesion,
        cuerpo
    );
}


/* =========================
   ENVIAR ACTUALIZACIÓN
   ========================= */

async function enviarActualizacion(
    sesion,
    cuerpo
) {

    guardadoPendiente = true;

    btnGuardar.disabled = true;


    try {

        /*
         * El JWT NO se coloca manualmente.
         * fetchWithHttpErrorInterceptor lo agrega automáticamente.
         */
        const respuesta =
            await fetchWithHttpErrorInterceptor(
                `${BASE_API}/${encodeURIComponent(sesion.usuarioId)}`,
                {
                    method: 'PUT',

                    headers: {
                        'Content-Type':
                            'application/json'
                    },

                    body:
                        JSON.stringify(cuerpo)
                }
            );


        if (!respuesta.ok) {

            let mensaje =
                `No fue posible guardar los cambios. Código ${respuesta.status}.`;


            try {

                const errorBody =
                    await respuesta.json();

                if (errorBody?.detail) {

                    mensaje =
                        errorBody.detail;

                } else if (
                    typeof errorBody === 'string' &&
                    errorBody.trim()
                ) {

                    mensaje =
                        errorBody;
                }

            } catch {
                // La respuesta puede no tener cuerpo JSON.
            }


            throw new Error(mensaje);
        }


        const perfilActualizado =
            await respuesta.json();


        perfilOriginal = {
            ...perfilActualizado
        };


        /*
         * Sincronizamos el apodo actualizado con la sesión.
         */
        sessionStorage.setItem(
            CLAVE_APODO,
            perfilActualizado.apodo ?? ''
        );


        llenarFormulario(
            perfilActualizado
        );


        mostrarMensajeFormulario(
            'Perfil actualizado correctamente.',
            false
        );

    } catch (error) {

        console.error(
            'Error actualizando perfil:',
            error
        );


        mostrarMensajeFormulario(
            error.message ||
            'No fue posible guardar los cambios.',
            true
        );

    } finally {

        guardadoPendiente = false;

        btnGuardar.disabled = false;
    }
}


/* =========================
   DESCARTAR
   ========================= */

function descartarCambios() {

    if (!perfilOriginal) {
        return;
    }


    llenarFormulario(
        perfilOriginal
    );


    ocultarMensajeFormulario();
}


/* =========================
   MENSAJES
   ========================= */

function mostrarMensajeFormulario(
    mensaje,
    esError
) {

    mensajeFormulario.textContent =
        mensaje;

    mensajeFormulario.classList.remove(
        'oculto'
    );


    if (esError) {

        mensajeFormulario.style.background =
            '#fee2e2';

        mensajeFormulario.style.color =
            '#dc2626';

    } else {

        mensajeFormulario.style.background =
            '#dcfce7';

        mensajeFormulario.style.color =
            '#16a34a';
    }
}


function ocultarMensajeFormulario() {

    mensajeFormulario.classList.add(
        'oculto'
    );

    mensajeFormulario.textContent = '';
}


/* =========================
   CONFIRMACIÓN APODO
   ========================= */

function cancelarCambioApodo() {

    guardadoPendiente = false;

    dialogoApodo.close();
}


async function confirmarCambioApodo() {

    const sesion =
        obtenerSesionActual();


    if (!sesion.usuarioId) {

        dialogoApodo.close();

        guardadoPendiente = false;

        window.location.href = RUTA_LOGIN;

        return;
    }


    let cuerpo;


    try {

        cuerpo =
            construirCuerpoActualizacion();

    } catch (error) {

        dialogoApodo.close();

        guardadoPendiente = false;

        mostrarMensajeFormulario(
            error.message,
            true
        );

        return;
    }


    dialogoApodo.close();


    await enviarActualizacion(
        sesion,
        cuerpo
    );
}


/* =========================
   EVENTOS
   ========================= */

formulario.addEventListener(
    'submit',
    async (evento) => {

        evento.preventDefault();

        await guardarPerfil();
    }
);


btnDescartar.addEventListener(
    'click',
    descartarCambios
);


btnReintentar.addEventListener(
    'click',
    cargarPerfil
);


btnCancelarApodo.addEventListener(
    'click',
    cancelarCambioApodo
);


btnConfirmarApodo.addEventListener(
    'click',
    confirmarCambioApodo
);


galeriaAvatares.addEventListener(
    'change',
    (evento) => {

        if (
            evento.target.matches(
                'input[name="avatar"]'
            )
        ) {

            const avatar =
                evento.target.value;


            if (!avatarPermitido(avatar)) {

                evento.target.checked = false;

                mostrarMensajeFormulario(
                    'El avatar seleccionado no es válido.',
                    true
                );
            }
        }
    }
);


/* =========================
   INICIO
   ========================= */

montarBarraNavegacion();
montarMenuAdmin();
cargarPerfil();
