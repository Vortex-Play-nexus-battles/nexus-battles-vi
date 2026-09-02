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
 * Muestra el mensaje amigable al usuario (HU-RBAC-004) sin usar alert()
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

  // Notificación flotante moderna (Toast) en lugar de alert()
  let toast = document.getElementById('nexus-rbac-toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'nexus-rbac-toast';
    toast.setAttribute('role', 'alert');
    toast.style.position = 'fixed';
    toast.style.top = '24px';
    toast.style.right = '24px';
    toast.style.zIndex = '9999';
    toast.style.backgroundColor = '#fbe4e4';
    toast.style.color = '#b81a1a';
    toast.style.border = '1px solid #f5c6c6';
    toast.style.borderRadius = '8px';
    toast.style.padding = '14px 20px';
    toast.style.boxShadow = '0 8px 24px rgba(0,0,0,0.12)';
    toast.style.fontFamily = "Inter, 'Segoe UI', system-ui, sans-serif";
    toast.style.fontSize = '14px';
    toast.style.fontWeight = '600';
    toast.style.display = 'flex';
    toast.style.alignItems = 'center';
    toast.style.gap = '10px';
    toast.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
    document.body.appendChild(toast);
  }

  toast.innerHTML = `<span style="font-size: 16px;">⛔</span> <span>${mensaje}</span>`;
  toast.style.opacity = '1';
  toast.style.transform = 'translateY(0)';

  clearTimeout(toast._timeout);
  toast._timeout = setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(-10px)';
  }, 4500);
}
