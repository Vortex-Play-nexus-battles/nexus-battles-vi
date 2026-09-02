// login.js
// Vista de inicio de sesión — HU-AUT-004.

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { setCurrentRole } from './directives/has-permission.directive.js';

const URL_LOGIN = '/api/v1/auth/login';

// Claves de sesión
const CLAVE_USUARIO_ID = 'nexus.usuarioId';
const CLAVE_ROL = 'nexus.rolActual';
const CLAVE_APODO = 'nexus.apodoActual';

/** @type {HTMLFormElement} */
const form = document.getElementById('formLogin');

/** @type {HTMLButtonElement} */
const botonEnviar = document.getElementById('botonEnviar');

/** @type {HTMLElement} */
const estadoLogin = document.getElementById('estadoLogin');

/** @type {HTMLElement} */
const avisoDispositivo = document.getElementById('avisoDispositivo');

/**
 * Lee el cuerpo de una respuesta que puede venir como JSON o texto plano.
 * Mismo patrón que demo-rbac.js (cuerpoDe), incluyendo el caso de body vacío.
 *
 * @param {Response} response
 * @returns {Promise<{status: number, body: unknown}>}
 */
async function cuerpoDe(response) {
  const texto = await response.text();

  if (!texto) {
    return {
      status: response.status,
      body: null
    };
  }

  try {
    return {
      status: response.status,
      body: JSON.parse(texto)
    };
  } catch {
    return {
      status: response.status,
      body: texto
    };
  }
}

/**
 * Mismo patrón que setEstado(texto, tipo) de demo-rbac.js.
 *
 * @param {string} texto
 * @param {'carga'|'error'|'exito'|'vacio'} tipo
 */
function setEstado(texto, tipo) {
  estadoLogin.textContent = texto;
  estadoLogin.className = `estado ${tipo}`;
  estadoLogin.hidden = false;
}

function ocultarEstado() {
  estadoLogin.hidden = true;
}

/**
 * Traduce cada código de error del login a un mensaje específico,
 * siguiendo los casos documentados del contrato.
 *
 * @param {number} status
 * @param {string} [mensajeServidor]
 */
export function mensajeDeError(status, mensajeServidor) {
  switch (status) {
    case 401:
      return 'Acceso rechazado. El correo o la contraseña son incorrectos, o estas credenciales no están registradas en este ambiente.';

    case 403:
      return mensajeServidor || 'Esta cuenta no puede iniciar sesión en este momento.';

    case 423:
      return mensajeServidor || 'Cuenta bloqueada temporalmente. Intenta más tarde.';

    default:
      return mensajeServidor || 'No se pudo iniciar sesión.';
  }
}


form.addEventListener('submit', async (evento) => {

  evento.preventDefault();

  ocultarEstado();
  avisoDispositivo.hidden = true;


  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }


  const payload = {
    email: form.email.value.trim(),
    password: form.password.value
  };


  botonEnviar.disabled = true;

  setEstado(
    'Verificando tus datos…',
    'carga'
  );


  try {

    const respuesta =
      await fetchWithHttpErrorInterceptor(
        URL_LOGIN,
        {
          method: 'POST',

          headers: {
            'Content-Type': 'application/json'
          },

          body: JSON.stringify(payload)
        }
      );


    const { body } =
      await cuerpoDe(respuesta);


    if (!respuesta.ok) {

      const mensajeServidor =
        typeof body === 'string'
          ? body
          : body?.mensaje;


      setEstado(
        mensajeDeError(
          respuesta.status,
          mensajeServidor
        ),
        'error'
      );

      return;
    }


    /*
     * Éxito:
     *
     * 200 OK con:
     *
     * {
     *   usuarioId,
     *   apodo,
     *   email,
     *   rol,
     *   dispositivoNuevo
     * }
     */


    // Mantener el rol en memoria para esta página.
    setCurrentRole(body.rol);


    // Guardar los datos necesarios para las páginas siguientes.
    //
    // IMPORTANTE:
    // usuarioId es necesario para HU-USR-001 porque
    // perfil.js consulta:
    //
    // GET /api/v1/perfiles/{usuarioId}
    //
    sessionStorage.setItem(
      CLAVE_USUARIO_ID,
      String(body.usuarioId)
    );


    sessionStorage.setItem(
      CLAVE_ROL,
      body.rol
    );


    sessionStorage.setItem(
      CLAVE_APODO,
      body.apodo
    );


    if (body.dispositivoNuevo) {

      avisoDispositivo.hidden = false;

      avisoDispositivo.textContent =
        'Detectamos un inicio de sesión desde un dispositivo nuevo.';
    }


    ocultarEstado();


    // TODO equipo: apuntar a la pantalla real post-login cuando exista.
    window.location.href = './';


  } catch {

    setEstado(
      'No pudimos conectar con el servidor. Intenta de nuevo.',
      'error'
    );

  } finally {

    botonEnviar.disabled = false;
  }
});
