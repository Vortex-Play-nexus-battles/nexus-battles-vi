/**
 * ==========================================================================
 * Interceptor de Errores HTTP en JavaScript Estándar (HU-RBAC-004)
 * Pila Tecnológica: Vanilla JS ES2022 (Sin Angular / Sin TypeScript)
 *
 * Captura errores 403 Forbidden devueltos por el backend en formato RFC 7807
 * (Problem Details) y muestra el mensaje amigable de acceso denegado.
 *
 * También adjunta automáticamente el token de sesión (JWT), cuando existe,
 * como header Authorization: Bearer <token> — sin que cada página tenga
 * que hacerlo manualmente. Si no hay token guardado (por ejemplo, antes de
 * iniciar sesión), la petición sale exactamente igual que antes, sin ese
 * header.
 *
 * R17 — si un servicio rechaza (401/403) una petición que llevaba el token de
 * la sesión, se avisa con el evento `nexus:credencial-rechazada`. No decide
 * nada: el vigilante de la sesión (`comun/vigilante-sesion.js`) pregunta al
 * emisor si el token sigue valiendo y solo entonces lleva al login.
 * ==========================================================================
 */

import { EVENTO_RECHAZO } from '../vigilante-sesion.js';

const CLAVE_TOKEN = 'nexus.token';

/**
 * Las llamadas de entrada (login, registro, recuperación, cierre) no hablan
 * de la sesión en curso: un 401 del login es una contraseña mal escrita.
 */
const RUTAS_DE_ENTRADA = /\/api\/v1\/auth\/(?:login|registro|restablecer|logout)(?:[/?#]|$)/;

/**
 * ¿Esta petición salió con el token de la sesión de esta pestaña?
 * @param {RequestInit} opciones las que se enviaron de verdad
 * @param {string|null} token
 */
function llevabaElTokenDeLaSesion(opciones, token) {
  if (!token) {
    return false;
  }
  const cabeceras = opciones?.headers;
  const valor = esHeaders(cabeceras) ? cabeceras.get('Authorization') : cabeceras?.Authorization;
  return valor === `Bearer ${token}`;
}

/** `instanceof Headers` sin romper donde `Headers` no existe (jsdom). */
function esHeaders(valor) {
  return typeof Headers !== 'undefined' && valor instanceof Headers;
}

/**
 * Avisa de que un servicio rechazó el token, para que el vigilante lo compruebe.
 * @param {number} estado
 * @param {string} url
 */
function avisarRechazoDeCredencial(estado, url) {
  window.dispatchEvent(new CustomEvent(EVENTO_RECHAZO, { detail: { estado, url } }));
}

/**
 * Si hay un token de sesión guardado, devuelve una copia de las opciones
 * de fetch con el header Authorization agregado — sin sobreescribir uno
 * que el propio llamador ya haya puesto explícitamente.
 * @param {RequestInit} options
 * @returns {RequestInit}
 */
function conAuthorizationSiHayToken(options) {
  const token = sessionStorage.getItem(CLAVE_TOKEN);
  if (!token) {
    return options;
  }

  const headersOriginales = options.headers;

  // Caso: el llamador ya pasó una instancia real de Headers.
  if (esHeaders(headersOriginales)) {
    const headers = new Headers(headersOriginales);
    if (!headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return { ...options, headers };
  }

  // Caso más común en el proyecto: headers como objeto plano.
  const headers = { ...(headersOriginales || {}) };
  if (!('Authorization' in headers)) {
    headers.Authorization = `Bearer ${token}`;
  }
  return { ...options, headers };
}

/**
 * Envoltorio para fetch que intercepta errores HTTP 403 y Problem Details,
 * y adjunta el token de sesión automáticamente cuando existe.
 * @param {string} url
 * @param {RequestInit} [options={}]
 * @returns {Promise<Response>}
 */
export async function fetchWithHttpErrorInterceptor(url, options = {}) {
  try {
    const token = sessionStorage.getItem(CLAVE_TOKEN);
    const opcionesConAuth = conAuthorizationSiHayToken(options);
    const response = await fetch(url, opcionesConAuth);

    if (
      (response.status === 401 || response.status === 403) &&
      llevabaElTokenDeLaSesion(opcionesConAuth, token) &&
      !RUTAS_DE_ENTRADA.test(String(url))
    ) {
      avisarRechazoDeCredencial(response.status, String(url));
    }

    if (response.status === 403) {
      // Capturar respuesta RFC 7807 (Problem Details)
      let errorDetail = 'No tienes permiso para realizar esta acción.';
      try {
        const errorData = await response.clone().json();
        if (errorData && (errorData.detail || errorData.title)) {
          errorDetail = errorData.detail || errorData.title;
        }
      } catch {
        // En caso de que el body no sea JSON
      }

      console.warn(`[HU-RBAC-004 - 403 Forbidden]: ${errorDetail}`);
      mostrarMensajeAccesoDenegado(errorDetail);
    }

    return response;
  } catch (error) {
    console.error('[HTTP Interceptor Network Error]:', error);
    throw error;
  }
}

/**
 * Muestra el mensaje amigable al usuario (HU-RBAC-004) sin usar alert()
 * @param {string} mensaje
 */
function mostrarMensajeAccesoDenegado(mensaje) {
  // Disparar evento personalizado para que cualquier componente del frontend lo capture
  window.dispatchEvent(
    new CustomEvent('nexus:rbac-forbidden', {
      detail: { message: mensaje },
    }),
  );

  const banner = document.getElementById('nexus-rbac-forbidden');
  if (banner) {
    banner.hidden = false;
    banner.textContent = mensaje;
    return;
  }

  // Aviso flotante. Antes se construía aquí a mano: veinte líneas de
  // `toast.style.*` en línea y, lo importante,
  //
  //     toast.innerHTML = `<span…>⛔</span> <span>${mensaje}</span>`;
  //
  // `mensaje` es el texto de error que devuelve el servidor (el `detail` o el
  // `title` del problem detail). Iba al parser de HTML **sin escapar**, en el
  // interceptor que usan TODAS las vistas del producto. Ahora el texto va por
  // `textContent`, que no interpreta marcado, y el aspecto lo pone `.aviso`
  // del sistema de diseño en vez de estilos en línea.
  let toast = document.getElementById('nexus-rbac-toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'nexus-rbac-toast';
    toast.className = 'aviso aviso--error aviso-flotante';
    // `role="alert"` y no `status`: es un rechazo de permiso, y quien usa
    // lector de pantalla tiene que enterarse en el momento.
    toast.setAttribute('role', 'alert');
    document.body.appendChild(toast);
  }

  toast.textContent = mensaje;
  toast.hidden = false;
  toast.dataset.visible = 'si';

  clearTimeout(toast._timeout);
  toast._timeout = setTimeout(() => {
    delete toast.dataset.visible;
  }, 4500);
}
