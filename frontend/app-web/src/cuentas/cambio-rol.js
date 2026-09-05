import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';

const CLAVE_TOKEN = 'nexus.token';

export const ROLES_DISPONIBLES = [
  'JUGADOR',
  'MODERADOR',
  'ADMINISTRADOR',
  'SUPER_ADMINISTRADOR',
];

function obtenerToken() {
  return sessionStorage.getItem(CLAVE_TOKEN);
}

export async function cambiarRol(
  usuarioId,
  nuevoRol,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
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

  const response = await fetchImpl(
    `/api/v1/rbac/usuarios/${encodeURIComponent(usuarioId)}/rol`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        nuevoRol,
      }),
    },
  );

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
    const mensaje =
      typeof body === 'string'
        ? body
        : body?.detail || body?.mensaje || `No se pudo cambiar el rol (${response.status}).`;

    throw new Error(mensaje);
  }

  return body;
}
