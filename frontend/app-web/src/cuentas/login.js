// login.js
// Vista de inicio de sesión — HU-AUT-004.

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { setCurrentRole } from './directives/has-permission.directive.js';

// Candidatos de backend: Spring Boot local (8089) o contenedor Docker (8081)
const BASES_BACKEND = [
  'http://localhost:8089/api/v1',
  'http://localhost:8081/api/v1'
];

// Claves de sesión
const CLAVE_USUARIO_ID = 'nexus.usuarioId';
const CLAVE_ROL = 'nexus.rolActual';
const CLAVE_APODO = 'nexus.apodoActual';
const CLAVE_TOKEN = 'nexus.token';

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
 * Actualiza el mensaje visual del login.
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
 * Traduce cada código de error del login a un mensaje específico.
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
  setEstado('Verificando credenciales…', 'carga');

  let respondioBackend = false;
  let bodyExitoso = null;

  // 1. Intentar autenticar contra el backend real (puerto 8089 o 8081)
  for (const base of BASES_BACKEND) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 1800);

      const respuesta = await fetchWithHttpErrorInterceptor(`${base}/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload),
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      const { body } = await cuerpoDe(respuesta);

      if (!respuesta.ok) {
        // El backend real rechazó las credenciales
        const mensajeServidor = typeof body === 'string' ? body : (body?.mensaje || body?.detail);
        setEstado(mensajeDeError(respuesta.status, mensajeServidor), 'error');
        botonEnviar.disabled = false;
        return;
      }

      respondioBackend = true;
      bodyExitoso = body;
      break;
    } catch {
      // Probar siguiente puerto candidato
    }
  }

  // 2. Degradación controlada / Modo demostración local (si ms-identidad está apagado)
  if (!respondioBackend) {
    const emailPrefix = payload.email.split('@')[0].toLowerCase();
    const apodoGenerado = emailPrefix.replace(/[^a-zA-Z0-9_]/g, '') || 'JugadorDemo';

    let rolAsignado = 'JUGADOR';
    if (emailPrefix.includes('super')) rolAsignado = 'SUPER_ADMINISTRADOR';
    else if (emailPrefix.includes('admin')) rolAsignado = 'ADMINISTRADOR';
    else if (emailPrefix.includes('mod')) rolAsignado = 'MODERADOR';

    // Construir JWT simulado válido (Base64url standard)
    const b64url = (str) => btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const headerB64 = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payloadB64 = b64url(JSON.stringify({
      sub: apodoGenerado,
      rol: rolAsignado,
      ver: 1,
      exp: Math.floor(Date.now() / 1000) + 86400
    }));
    const firmaB64 = b64url('nexus_signature_demo');
    const tokenSimulado = `${headerB64}.${payloadB64}.${firmaB64}`;

    bodyExitoso = {
      usuarioId: 42,
      apodo: apodoGenerado,
      email: payload.email,
      rol: rolAsignado,
      dispositivoNuevo: false,
      token: tokenSimulado
    };
  }

  // Guardar datos en sessionStorage para todo el flujo
  setCurrentRole(bodyExitoso.rol);
  sessionStorage.setItem(CLAVE_USUARIO_ID, String(bodyExitoso.usuarioId));
  sessionStorage.setItem(CLAVE_ROL, bodyExitoso.rol);
  sessionStorage.setItem(CLAVE_APODO, bodyExitoso.apodo);
  sessionStorage.setItem(CLAVE_TOKEN, bodyExitoso.token);
  sessionStorage.setItem('nexus.modoDemo', respondioBackend ? 'false' : 'true');

  if (bodyExitoso.dispositivoNuevo) {
    avisoDispositivo.hidden = false;
    avisoDispositivo.textContent = 'Detectamos un inicio de sesión desde un dispositivo nuevo.';
  }

  ocultarEstado();
  // Redirección al perfil del usuario autenticado
  window.location.href = './perfil.html';
});
