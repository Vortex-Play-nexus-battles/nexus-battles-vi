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

const RUTA_INICIO = './';

const form = document.getElementById('form-crear-cuenta');

const formularioContenedor =
document.getElementById('formulario-contenedor');

const accesoDenegado =
document.getElementById('acceso-denegado');

const resultadoCreacion =
document.getElementById('resultado-creacion');

const mensajeError =
document.getElementById('mensaje-error');

const mensajeExito =
document.getElementById('mensaje-exito');

const resultadoTexto =
document.getElementById('resultado-texto');

const rolSelect =
document.getElementById('rol');

const permisosContenedor =
document.getElementById('permisos');

const btnCrear =
document.getElementById('btn-crear');

const btnCancelar =
document.getElementById('btn-cancelar');

const btnVolver =
document.getElementById('btn-volver');

const btnNuevaCuenta =
document.getElementById('btn-nueva-cuenta');

/*

* Los permisos reales son determinados por RBAC
* según el rol asignado en backend.
*
* Esta información solamente se muestra al Super
* Administrador; NO se utiliza para otorgar permisos
* directamente desde el frontend.
  */
  const PERMISOS_POR_ROL = {
  MODERADOR: [
  {
  nombre: 'Modificar perfil',
  descripcion: 'Puede modificar perfiles según las reglas RBAC.'
  },
  {
  nombre: 'Publicar, eliminar y moderar comentarios',
  descripcion: 'Puede realizar acciones de moderación de comentarios.'
  },
  {
  nombre: 'Emitir advertencias',
  descripcion: 'Puede emitir advertencias a usuarios.'
  },
  {
  nombre: 'Suspender usuarios',
  descripcion: 'Permiso temporal según la matriz RBAC.'
  }
  ],

  ADMINISTRADOR: [
  {
  nombre: 'Modificar perfil',
  descripcion: 'Puede modificar perfiles según las reglas RBAC.'
  },
  {
  nombre: 'Publicar, eliminar y moderar comentarios',
  descripcion: 'Puede realizar acciones de moderación de comentarios.'
  },
  {
  nombre: 'Emitir advertencias',
  descripcion: 'Puede emitir advertencias a usuarios.'
  },
  {
  nombre: 'Suspender usuarios',
  descripcion: 'Permiso concedido por la matriz RBAC.'
  },
  {
  nombre: 'Banear usuarios',
  descripcion: 'Puede realizar baneos definitivos.'
  },
  {
  nombre: 'Gestionar productos',
  descripcion: 'Puede gestionar productos.'
  },
  {
  nombre: 'Gestionar cuentas',
  descripcion: 'Puede gestionar cuentas administrativas y de usuarios.'
  }
  ]
  };

function mostrar(elemento) {
elemento.classList.remove('oculto');
}

function ocultar(elemento) {
elemento.classList.add('oculto');
}

function mostrarError(mensaje) {
mensajeError.textContent = mensaje;
mostrar(mensajeError);
}

function limpiarMensajes() {
ocultar(mensajeError);
ocultar(mensajeExito);

```
mensajeError.textContent = '';
mensajeExito.textContent = '';
```

}

function cargarPermisos() {

```
const rol = rolSelect.value;

permisosContenedor.innerHTML = '';

if (!rol || !PERMISOS_POR_ROL[rol]) {

    permisosContenedor.innerHTML = `
        <div class="sin-permisos">
            Selecciona un rol para consultar sus permisos.
        </div>
    `;

    return;
}

const permisos = PERMISOS_POR_ROL[rol];

for (const permiso of permisos) {

    const elemento = document.createElement('div');

    elemento.className = 'permiso';

    elemento.innerHTML = `
        <strong>${permiso.nombre}</strong>
        <span>${permiso.descripcion}</span>
    `;

    permisosContenedor.appendChild(elemento);
}
```

}

function validarFormulario() {

```
const nombres =
    document.getElementById('nombres').value.trim();

const apellidos =
    document.getElementById('apellidos').value.trim();

const apodo =
    document.getElementById('apodo').value.trim();

const email =
    document.getElementById('email').value.trim();

const password =
    document.getElementById('password').value;

const rol =
    rolSelect.value;

if (!nombres) {
    mostrarError('Los nombres son obligatorios.');
    return false;
}

if (!apellidos) {
    mostrarError('Los apellidos son obligatorios.');
    return false;
}

if (!apodo) {
    mostrarError('El apodo es obligatorio.');
    return false;
}

if (!email) {
    mostrarError('El correo electrónico es obligatorio.');
    return false;
}

if (!email.includes('@')) {
    mostrarError('Ingresa un correo electrónico válido.');
    return false;
}

if (!password) {
    mostrarError('La contraseña es obligatoria.');
    return false;
}

if (password.length < 9) {
    mostrarError(
        'La contraseña debe tener al menos 9 caracteres.'
    );

    return false;
}

if (rol !== 'MODERADOR' && rol !== 'ADMINISTRADOR') {

    mostrarError(
        'Solo puedes crear cuentas de Moderador o Administrador.'
    );

    return false;
}

return true;
```

}

async function crearCuenta(event) {

```
event.preventDefault();

limpiarMensajes();

if (!validarFormulario()) {
    return;
}

btnCrear.disabled = true;
btnCrear.textContent = 'Creando...';

const datos = {

    nombres:
        document.getElementById('nombres').value.trim(),

    apellidos:
        document.getElementById('apellidos').value.trim(),

    email:
        document.getElementById('email').value.trim(),

    password:
        document.getElementById('password').value,

    apodo:
        document.getElementById('apodo').value.trim(),

    avatar:
        '',

    rolNombre:
        rolSelect.value
};

try {

    const respuesta =
        await fetchWithHttpErrorInterceptor(
            BASE_API,
            {
                method: 'POST',

                headers: {
                    'Content-Type': 'application/json',
                    'X-User-Role': getCurrentRole()
                },

                body: JSON.stringify(datos)
            }
        );

    if (!respuesta.ok) {

        let mensaje =
            'No fue posible crear la cuenta administrativa.';

        try {

            const body = await respuesta.json();

            if (body.message) {
                mensaje = body.message;
            } else if (typeof body === 'string') {
                mensaje = body;
            }

        } catch (error) {
            // Se conserva el mensaje genérico.
        }

        throw new Error(mensaje);
    }

    const usuario = await respuesta.json();

    mostrarResultado(usuario);

} catch (error) {

    mostrarError(
        error.message ||
        'Ocurrió un error al crear la cuenta.'
    );

} finally {

    btnCrear.disabled = false;
    btnCrear.textContent = 'Crear cuenta';
}
```

}

function mostrarResultado(usuario) {

```
ocultar(formularioContenedor);

mostrar(resultadoCreacion);

const apodo =
    usuario?.apodo ||
    document.getElementById('apodo').value.trim();

const rol =
    usuario?.rol?.nombre ||
    rolSelect.value;

resultadoTexto.textContent =
    `La cuenta de ${apodo} fue creada correctamente con el rol ${rol}.`;
```

}

function reiniciarFormulario() {

```
form.reset();

cargarPermisos();

limpiarMensajes();

ocultar(resultadoCreacion);

mostrar(formularioContenedor);
```

}

function verificarAcceso() {

```
const rolActual =
    sessionStorage.getItem(CLAVE_ROL);

if (rolActual) {
    setCurrentRole(rolActual);
}

const rol =
    getCurrentRole();

const autorizado =
    rol === 'SUPER_ADMINISTRADOR' &&
    checkPermission(PERMISO_CREAR);

if (!autorizado) {

    ocultar(formularioContenedor);

    mostrar(accesoDenegado);

    return;
}

mostrar(formularioContenedor);

applyHasPermissionDirective();
```

}

rolSelect.addEventListener(
'change',
cargarPermisos
);

form.addEventListener(
'submit',
crearCuenta
);

btnCancelar.addEventListener(
'click',
() => {
window.location.href = RUTA_INICIO;
}
);

btnVolver.addEventListener(
'click',
() => {
window.location.href = RUTA_INICIO;
}
);

btnNuevaCuenta.addEventListener(
'click',
reiniciarFormulario
);

verificarAcceso();
cargarPermisos();
