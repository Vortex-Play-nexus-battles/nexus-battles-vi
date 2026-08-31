import {
  applyHasPermissionDirective,
  getCurrentRole,
  setPermissionMatrix,
  setCurrentRole,
} from './directives/has-permission.directive.js';
import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';

/** @type {HTMLSelectElement} */
const selectorRol = document.querySelector('#selector-rol');
/** @type {HTMLParagraphElement} */
const descripcionRol = document.querySelector('#descripcion-rol');
/** @type {HTMLElement} */
const estadoRoles = document.querySelector('#estado-roles');
/** @type {HTMLInputElement} */
const apiBaseInput = document.querySelector('#api-base');
/** @type {HTMLInputElement} */
const usuarioIdInput = document.querySelector('#usuario-id');
/** @type {HTMLPreElement} */
const logEl = document.querySelector('#log');
const banner = document.querySelector('#nexus-rbac-forbidden');

const ROLES_RESPALDO = [
  {
    role: 'JUGADOR',
    name: 'Jugador',
    description:
      'Usuario estándar con acceso a funciones básicas de juego, comentarios propios y gestión de su perfil.',
  },
  {
    role: 'MODERADOR',
    name: 'Moderador',
    description:
      'Usuario con facultades de moderación de contenido, advertencias y suspensiones temporales.',
  },
  {
    role: 'ADMINISTRADOR',
    name: 'Administrador',
    description:
      'Usuario con control administrativo sobre usuarios, productos de tienda y sanciones definitivas.',
  },
  {
    role: 'SUPER_ADMINISTRADOR',
    name: 'Super Administrador',
    description:
      'Máxima autoridad del sistema con control total y capacidad de nombrar otros administradores.',
  },
];

/**
 * @returns {string}
 */
function apiBase() {
  return apiBaseInput.value.replace(/\/$/, '');
}

/**
 * @param {string} texto
 * @param {'carga'|'error'|'exito'|'vacio'} tipo
 */
function setEstado(texto, tipo) {
  estadoRoles.textContent = texto;
  estadoRoles.className = `estado ${tipo}`;
}

/**
 * @param {unknown} valor
 */
function escribirLog(valor) {
  logEl.textContent = typeof valor === 'string' ? valor : JSON.stringify(valor, null, 2);
}

/**
 * @param {Array<{role: string, name: string, description: string}>} roles
 */
function pintarRoles(roles) {
  const actual = getCurrentRole();
  selectorRol.replaceChildren();

  roles.forEach((rol) => {
    const option = document.createElement('option');
    option.value = rol.role;
    option.textContent = rol.name || rol.role;
    option.dataset.description = rol.description || '';
    selectorRol.append(option);
  });

  if (roles.some((rol) => rol.role === actual)) {
    selectorRol.value = actual;
  }

  actualizarDescripcion();
  aplicarRol();
}

function actualizarDescripcion() {
  const elegido = selectorRol.selectedOptions[0];
  descripcionRol.textContent = elegido?.dataset.description || '';
}

function aplicarRol() {
  const rol = selectorRol.value;
  if (!rol) {
    return;
  }
  setCurrentRole(rol);
}

function encabezadosSesionDemo() {
  return {
    'Content-Type': 'application/json',
    'X-User-Role': getCurrentRole(),
    'X-User-Name': 'demo-local-andres',
  };
}

function ocultarBanner() {
  if (banner) {
    banner.hidden = true;
    banner.textContent = '';
  }
}

/**
 * @param {Response} response
 */
async function cuerpoDe(response) {
  const texto = await response.text();
  if (!texto) {
    return { status: response.status, body: null };
  }
  try {
    return { status: response.status, body: JSON.parse(texto) };
  } catch {
    return { status: response.status, body: texto };
  }
}

async function cargarRoles() {
  setEstado('Cargando catálogo de roles…', 'carga');
  try {
    const response = await fetchWithHttpErrorInterceptor(`${apiBase()}/rbac/roles`);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const roles = await response.json();
    const normalizados = Array.isArray(roles)
      ? roles.map((rol) => ({
          role: typeof rol.role === 'string' ? rol.role : rol.role?.name || rol.name,
          name: rol.name || rol.displayName || rol.role,
          description: rol.description || '',
        }))
      : [];
    if (normalizados.length === 0) {
      pintarRoles(ROLES_RESPALDO);
      setEstado('El servidor no devolvió roles. Se usa el catálogo local.', 'vacio');
      return;
    }
    pintarRoles(normalizados);
    setEstado('Roles cargados desde ms-identidad.', 'exito');
  } catch (error) {
    pintarRoles(ROLES_RESPALDO);
    setEstado(
      `Sin API (${error instanceof Error ? error.message : 'error'}). Selector local activo.`,
      'error',
    );
  }
}

async function cargarPermisos() {
  try {
    const response = await fetchWithHttpErrorInterceptor(`${apiBase()}/rbac/matrix`);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const payload = await response.json();
    setPermissionMatrix(payload?.matrix);
    setEstado(`Roles y permisos cargados desde ms-identidad (matriz ${payload?.version || 'vigente'}).`, 'exito');
  } catch (error) {
    // Sin matriz no se habilita ninguna acción: la UI también aplica default-deny.
    setPermissionMatrix({});
    setEstado(
      `No fue posible cargar permisos; las acciones se mantienen ocultas (${error instanceof Error ? error.message : 'error'}).`,
      'error',
    );
  }
}

async function intentarBan() {
  ocultarBanner();
  const response = await fetchWithHttpErrorInterceptor(`${apiBase()}/admin/ban`, {
    method: 'POST',
    headers: encabezadosSesionDemo(),
    body: JSON.stringify({ userId: 'demo-42' }),
  });
  escribirLog(await cuerpoDe(response));
}

async function intentarAsignarRol() {
  ocultarBanner();
  const usuarioId = usuarioIdInput.value.trim() || '2';
  const response = await fetchWithHttpErrorInterceptor(
    `${apiBase()}/rbac/usuarios/${encodeURIComponent(usuarioId)}/rol`,
    {
      method: 'PUT',
      headers: encabezadosSesionDemo(),
      body: JSON.stringify({ nuevoRol: 'MODERADOR' }),
    },
  );
  escribirLog(await cuerpoDe(response));
}

selectorRol.addEventListener('change', () => {
  actualizarDescripcion();
  aplicarRol();
});

document.querySelector('#btn-cargar').addEventListener('click', () => {
  cargarRoles();
  cargarPermisos();
});

document.querySelector('#btn-ban').addEventListener('click', () => {
  intentarBan().catch((error) => escribirLog(String(error)));
});

document.querySelector('#btn-asignar').addEventListener('click', () => {
  intentarAsignarRol().catch((error) => escribirLog(String(error)));
});

document.querySelector('#acciones-ui').addEventListener('click', (evento) => {
  const boton = evento.target.closest('button[data-has-permission]');
  if (!boton) {
    return;
  }
  const accion = boton.getAttribute('data-has-permission');
  escribirLog({
    ui: 'acción visible para este rol',
    rol: getCurrentRole(),
    accion,
  });
});

pintarRoles(ROLES_RESPALDO);
applyHasPermissionDirective();
cargarRoles();
cargarPermisos();
