import {
setCurrentRole,
getCurrentRole,
checkPermission,
applyHasPermissionDirective
} from './directives/has-permission.directive.js';

import {
fetchWithHttpErrorInterceptor
} from '../comun/interceptors/http-error.interceptor.js';

const BASE_API = '/api/admin/cuentas';

const CLAVE_ROL = 'nexus.rolActual';

const PERMISO_CREAR = 'CREAR_ADMIN_MODERADOR';

const ROLES_PERMITIDOS = [
'MODERADOR',
'ADMINISTRADOR'
];

const PERMISOS_POR_ROL = {

```
MODERADOR: [
    'GESTIONAR_CUENTAS',
    'SUSPENDER_USUARIOS'
],

ADMINISTRADOR: [
    'GESTIONAR_CUENTAS',
    'SUSPENDER_USUARIOS',
    'BANEAR_DEFINITIVAMENTE'
]
```

};

document.addEventListener('DOMContentLoaded', iniciar);

function iniciar() {

```
const rolGuardado =
    sessionStorage.getItem(CLAVE_ROL) || 'JUGADOR';

setCurrentRole(rolGuardado);

aplicarDirectivas();

configurarEventos();

verificarAcceso();

actualizarPermisosVisuales();
```

}

function aplicarDirectivas() {

```
applyHasPermissionDirective();
```

}

function configurarEventos() {

```
const formulario =
    document.getElementById('form-crear-cuenta');

const selectorRol =
    document.getElementById('rol');

const btnVolverHeader =
    document.getElementById('btn-volver-header');

const btnCancelarAcceso =
    document.getElementById('btn-cancelar-acceso');

const btnCancelar =
    document.getElementById('btn-cancelar');

const btnNuevaCuenta =
    document.getElementById('btn-nueva-cuenta');

const btnVolverResultado =
    document.getElementById('btn-volver-resultado');


if (formulario) {

    formulario.addEventListener(
        'submit',
        manejarCreacionCuenta
    );

}


if (selectorRol) {

    selectorRol.addEventListener(
        'change',
        actualizarPermisosVisuales
    );

}


if (btnVolverHeader) {

    btnVolverHeader.addEventListener(
        'click',
        volverInicio
    );

}


if (btnCancelarAcceso) {

    btnCancelarAcceso.addEventListener(
        'click',
        volverInicio
    );

}


if (btnCancelar) {

    btnCancelar.addEventListener(
        'click',
        limpiarFormulario
    );

}


if (btnNuevaCuenta) {

    btnNuevaCuenta.addEventListener(
        'click',
        mostrarFormularioNuevaCuenta
    );

}


if (btnVolverResultado) {

    btnVolverResultado.addEventListener(
        'click',
        volverInicio
    );

}
```

}

function verificarAcceso() {

```
const accesoDenegado =
    document.getElementById('acceso-denegado');

const formularioContenedor =
    document.getElementById('formulario-contenedor');

if (!accesoDenegado || !formularioContenedor) {
    return;
}


const rolActual = getCurrentRole();

const tienePermiso =
    checkPermission(PERMISO_CREAR);

const esSuperAdministrador =
    rolActual === 'SUPER_ADMINISTRADOR';


if (!esSuperAdministrador || !tienePermiso) {

    accesoDenegado.hidden = false;

    formularioContenedor.hidden = true;

    return;
}


accesoDenegado.hidden = true;

formularioContenedor.hidden = false;
```

}

function actualizarPermisosVisuales() {

```
const selectorRol =
    document.getElementById('rol');

const contenedorPermisos =
    document.getElementById('permisos');

if (!selectorRol || !contenedorPermisos) {
    return;
}


const rolSeleccionado =
    selectorRol.value;


if (!rolSeleccionado ||
    !PERMISOS_POR_ROL[rolSeleccionado]) {

    contenedorPermisos.innerHTML = `
        <div class="sin-permisos">
            Selecciona un rol para consultar sus permisos.
        </div>
    `;

    return;
}


const permisos =
    PERMISOS_POR_ROL[rolSeleccionado];


contenedorPermisos.innerHTML =
    permisos.map((permiso) => `
        <div class="permiso">
            <span>${formatearPermiso(permiso)}</span>
        </div>
    `).join('');
```

}

function formatearPermiso(permiso) {

```
return permiso
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, letra => letra.toUpperCase());
```

}

async function manejarCreacionCuenta(evento) {

```
evento.preventDefault();


const rolActual =
    getCurrentRole();

const tienePermiso =
    checkPermission(PERMISO_CREAR);


if (
    rolActual !== 'SUPER_ADMINISTRADOR' ||
    !tienePermiso
) {

    mostrarError(
        'No tienes permisos para crear cuentas administrativas.'
    );

    return;
}


const nombres =
    document.getElementById('nombres')?.value.trim();

const apellidos =
    document.getElementById('apellidos')?.value.trim();

const apodo =
    document.getElementById('apodo')?.value.trim();

const email =
    document.getElementById('email')?.value.trim();

const password =
    document.getElementById('password')?.value;

const rol =
    document.getElementById('rol')?.value;


const error =
    validarDatos(
        nombres,
        apellidos,
        apodo,
        email,
        password,
        rol
    );


if (error) {

    mostrarError(error);

    return;
}


const confirmado =
    window.confirm(
        `¿Deseas crear la cuenta administrativa para "${apodo}" con rol ${rol}?`
    );


if (!confirmado) {
    return;
}


ocultarMensajes();

cambiarEstadoBoton(true);


try {

    const respuesta =
        await fetchWithHttpErrorInterceptor(
            BASE_API,
            {
                method: 'POST',

                headers: {
                    'Content-Type': 'application/json'
                },

                body: JSON.stringify({
                    nombres,
                    apellidos,
                    email,
                    password,
                    apodo,
                    avatar: '',
                    rolNombre: rol
                })
            }
        );


    if (!respuesta.ok) {

        const mensaje =
            await obtenerMensajeError(respuesta);

        throw new Error(mensaje);
    }


    const usuarioCreado =
        await obtenerJsonSeguro(respuesta);


    mostrarResultado(
        usuarioCreado,
        apodo,
        rol
    );


} catch (error) {

    console.error(
        'Error creando cuenta administrativa:',
        error
    );

    mostrarError(
        error.message ||
        'No fue posible crear la cuenta administrativa.'
    );

} finally {

    cambiarEstadoBoton(false);

}
```

}

function validarDatos(
nombres,
apellidos,
apodo,
email,
password,
rol
) {

```
if (!nombres) {
    return 'Debes ingresar los nombres.';
}


if (!apellidos) {
    return 'Debes ingresar los apellidos.';
}


if (!apodo) {
    return 'Debes ingresar un apodo.';
}


if (!email) {
    return 'Debes ingresar un correo electrónico.';
}


if (!validarEmail(email)) {
    return 'Ingresa un correo electrónico válido.';
}


if (!password) {
    return 'Debes ingresar una contraseña inicial.';
}


if (password.length < 9) {
    return 'La contraseña debe tener al menos 9 caracteres.';
}


if (!ROLES_PERMITIDOS.includes(rol)) {
    return 'Debes seleccionar un rol administrativo válido.';
}


return null;
```

}

function validarEmail(email) {

```
return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
```

}

async function obtenerMensajeError(respuesta) {

```
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
```

}

async function obtenerJsonSeguro(respuesta) {

```
try {
    return await respuesta.json();
} catch {
    return null;
}
```

}

function mostrarResultado(
usuarioCreado,
apodo,
rol
) {

```
const formularioContenedor =
    document.getElementById('formulario-contenedor');

const resultado =
    document.getElementById('resultado-creacion');

const resultadoTexto =
    document.getElementById('resultado-texto');


if (!formularioContenedor ||
    !resultado ||
    !resultadoTexto) {

    return;
}


let identificador = 'No disponible';


if (usuarioCreado) {

    identificador =
        usuarioCreado.id ??
        usuarioCreado.usuarioId ??
        'No disponible';

}


resultadoTexto.textContent =
    `La cuenta "${apodo}" fue creada con el rol ${rol}. ` +
    `ID de usuario: ${identificador}.`;


formularioContenedor.hidden = true;

resultado.hidden = false;
```

}

function limpiarFormulario() {

```
const formulario =
    document.getElementById('form-crear-cuenta');

if (!formulario) {
    return;
}


formulario.reset();

actualizarPermisosVisuales();

ocultarMensajes();
```

}

function mostrarFormularioNuevaCuenta() {

```
const formularioContenedor =
    document.getElementById('formulario-contenedor');

const resultado =
    document.getElementById('resultado-creacion');


if (formularioContenedor) {
    formularioContenedor.hidden = false;
}


if (resultado) {
    resultado.hidden = true;
}


limpiarFormulario();
```

}

function cambiarEstadoBoton(cargando) {

```
const boton =
    document.getElementById('btn-crear');

if (!boton) {
    return;
}


boton.disabled = cargando;

boton.textContent =
    cargando
        ? 'Creando cuenta...'
        : 'Crear cuenta';
```

}

function mostrarError(mensaje) {

```
const elemento =
    document.getElementById('mensaje-error');

const exito =
    document.getElementById('mensaje-exito');


if (exito) {
    exito.hidden = true;
}


if (!elemento) {
    return;
}


elemento.textContent = mensaje;

elemento.hidden = false;
```

}

function mostrarExito(mensaje) {

```
const elemento =
    document.getElementById('mensaje-exito');

const error =
    document.getElementById('mensaje-error');


if (error) {
    error.hidden = true;
}


if (!elemento) {
    return;
}


elemento.textContent = mensaje;

elemento.hidden = false;
```

}

function ocultarMensajes() {

```
const error =
    document.getElementById('mensaje-error');

const exito =
    document.getElementById('mensaje-exito');


if (error) {
    error.hidden = true;
}


if (exito) {
    exito.hidden = true;
}
```

}

function volverInicio() {

```
window.location.href = '../index.html';
```

}
