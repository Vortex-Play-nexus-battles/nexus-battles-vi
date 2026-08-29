/**
 * ==========================================================================
 * Interceptor de Errores HTTP en JavaScript Estándar (HU-RBAC-004)
 * Pila Tecnológica: Vanilla JS ES2022 (Sin Angular / Sin TypeScript)
 * 
 * Captura errores 403 Forbidden devueltos por el backend en formato RFC 7807
 * (Problem Details) y muestra el mensaje amigable de acceso denegado.
 * ==========================================================================
 */

/**
 * Envoltorio para fetch que intercepta errores HTTP 403 y Problem Details
 * @param {string} url
 * @param {RequestInit} [options={}]
 * @returns {Promise<Response>}
 */
export async function fetchWithHttpErrorInterceptor(url, options = {}) {
  try {
    const response = await fetch(url, options);

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
 * Muestra el mensaje amigable al usuario (HU-RBAC-004)
 * @param {string} mensaje
 */
function mostrarMensajeAccesoDenegado(mensaje) {
  // Disparar evento personalizado para que cualquier componente del frontend lo capture
  window.dispatchEvent(new CustomEvent('nexus:rbac-forbidden', {
    detail: { message: mensaje }
  }));

  const banner = document.getElementById('nexus-rbac-forbidden');
  if (banner) {
    banner.hidden = false;
    banner.textContent = mensaje;
    return;
  }

  alert(`⛔ Acceso Denegado: ${mensaje}`);
}
