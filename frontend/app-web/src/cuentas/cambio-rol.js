import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { textoDelServidor } from '../comun/ui/texto-de-fallo.js';

const CLAVE_TOKEN = 'nexus.token';

export const ROLES_DISPONIBLES = ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR'];

function obtenerToken() {
  return sessionStorage.getItem(CLAVE_TOKEN);
}

/**
 * @param {number|string} usuarioId clave de la cuenta
 * @param {string} nuevoRol
 * @param {{motivo?: string|null, fetchImpl?: typeof fetch}} [opciones]
 *   `motivo` (rbac.yaml 2.0.0) queda en la auditoría del cambio
 */
export async function cambiarRol(
  usuarioId,
  nuevoRol,
  { motivo = null, fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const token = obtenerToken();

  if (!token) {
    throw new Error('No existe una sesión autenticada para realizar el cambio de rol.');
  }

  if (!usuarioId) {
    throw new Error('El usuario afectado es obligatorio.');
  }

  if (!ROLES_DISPONIBLES.includes(nuevoRol)) {
    throw new Error('El rol seleccionado no es válido.');
  }

  const response = await fetchImpl(`/api/v1/rbac/usuarios/${encodeURIComponent(usuarioId)}/rol`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(motivo ? { nuevoRol, motivo } : { nuevoRol }),
  });

  const texto = await response.text();

  let body = null;

  if (texto) {
    try {
      body = JSON.parse(texto);
    } catch {
      body = texto;
    }
  }

  if (!response.ok) {
    const mensaje = textoDelServidor(
      body,
      response.status,
      'No se pudo cambiar el rol. Vuelve a intentarlo en un momento.',
    );

    throw new Error(mensaje);
  }

  return body;
}
