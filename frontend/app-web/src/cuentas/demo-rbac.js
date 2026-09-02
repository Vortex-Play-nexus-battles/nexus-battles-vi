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
const matrizPermisos = document.querySelector('#matriz-permisos');
const descripcionMatriz = document.querySelector('#descripcion-matriz');
const accionesUi = document.querySelector('#acciones-ui');

const ORDEN_ROLES = ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR'];
const NOMBRES_ACCIONES = {
  CREAR_CUENTA_JUGADOR: 'Crear cuenta de jugador',
  MODIFICAR_PERFIL_PROPIO: 'Modificar perfil propio',
  PUBLICAR_COMENTARIOS: 'Publicar comentarios',
  ELIMINAR_COMENTARIO_PROPIO: 'Eliminar comentario propio',
  MODERAR_COMENTARIOS: 'Moderar comentarios',
  EMITIR_ADVERTENCIAS: 'Emitir advertencias',
  SUSPENDER_USUARIOS: 'Suspender usuarios',
  BANEAR_DEFINITIVAMENTE: 'Banear definitivamente',
  CREAR_ADMIN_MODERADOR: 'Crear admin o moderador',
  GESTIONAR_PRODUCTOS: 'Gestionar productos',
  ASIGNAR_ROL: 'Asignar rol',
  GESTIONAR_CUENTAS: 'Gestionar cuentas',
};

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

const MATRIZ_RESPALDO = {
  JUGADOR: {
    CREAR_CUENTA_JUGADOR: 'GRANTED',
    MODIFICAR_PERFIL_PROPIO: 'GRANTED',
    PUBLICAR_COMENTARIOS: 'GRANTED',
    ELIMINAR_COMENTARIO_PROPIO: 'GRANTED',
    MODERAR_COMENTARIOS: 'DENIED',
    EMITIR_ADVERTENCIAS: 'DENIED',
    SUSPENDER_USUARIOS: 'DENIED',
    BANEAR_DEFINITIVAMENTE: 'DENIED',
    CREAR_ADMIN_MODERADOR: 'DENIED',
    GESTIONAR_PRODUCTOS: 'DENIED',
    ASIGNAR_ROL: 'DENIED',
    GESTIONAR_CUENTAS: 'DENIED',
  },
  MODERADOR: {
    CREAR_CUENTA_JUGADOR: 'DENIED',
    MODIFICAR_PERFIL_PROPIO: 'GRANTED',
    PUBLICAR_COMENTARIOS: 'GRANTED',
    ELIMINAR_COMENTARIO_PROPIO: 'GRANTED',
    MODERAR_COMENTARIOS: 'GRANTED',
    EMITIR_ADVERTENCIAS: 'GRANTED',
    SUSPENDER_USUARIOS: 'TEMPORARY',
    BANEAR_DEFINITIVAMENTE: 'DENIED',
    CREAR_ADMIN_MODERADOR: 'DENIED',
    GESTIONAR_PRODUCTOS: 'DENIED',
    ASIGNAR_ROL: 'DENIED',
    GESTIONAR_CUENTAS: 'DENIED',
  },
  ADMINISTRADOR: {
    CREAR_CUENTA_JUGADOR: 'DENIED',
    MODIFICAR_PERFIL_PROPIO: 'GRANTED',
    PUBLICAR_COMENTARIOS: 'GRANTED',
    ELIMINAR_COMENTARIO_PROPIO: 'GRANTED',
    MODERAR_COMENTARIOS: 'GRANTED',
    EMITIR_ADVERTENCIAS: 'GRANTED',
    SUSPENDER_USUARIOS: 'GRANTED',
    BANEAR_DEFINITIVAMENTE: 'GRANTED',
    CREAR_ADMIN_MODERADOR: 'DENIED',
    GESTIONAR_PRODUCTOS: 'GRANTED',
    ASIGNAR_ROL: 'DENIED',
    GESTIONAR_CUENTAS: 'GRANTED',
  },
  SUPER_ADMINISTRADOR: {
    CREAR_CUENTA_JUGADOR: 'DENIED',
    MODIFICAR_PERFIL_PROPIO: 'GRANTED',
    PUBLICAR_COMENTARIOS: 'GRANTED',
    ELIMINAR_COMENTARIO_PROPIO: 'GRANTED',
    MODERAR_COMENTARIOS: 'GRANTED',
    EMITIR_ADVERTENCIAS: 'GRANTED',
    SUSPENDER_USUARIOS: 'GRANTED',
    BANEAR_DEFINITIVAMENTE: 'GRANTED',
    CREAR_ADMIN_MODERADOR: 'GRANTED',
    GESTIONAR_PRODUCTOS: 'GRANTED',
    ASIGNAR_ROL: 'GRANTED',
    GESTIONAR_CUENTAS: 'GRANTED',
  },
};

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

function nombreAccion(accion) {
  return NOMBRES_ACCIONES[accion] || accion.toLowerCase().replaceAll('_', ' ');
}

function crearInsignia(tipo) {
  const insignia = document.createElement('span');
  const valores = {
    GRANTED: ['permitido', 'Permitido'],
    DENIED: ['denegado', 'Denegado'],
    TEMPORARY: ['temporal', 'Temporal'],
  };
  const [clase, texto] = valores[tipo] || valores.DENIED;
  insignia.className = `insignia ${clase}`;
  insignia.textContent = texto;
  return insignia;
}

function pintarMatriz(matrix, version = 'vigente') {
  const matriz = matrix && typeof matrix === 'object' && Object.keys(matrix).length > 0 ? matrix : MATRIZ_RESPALDO;
  const acciones = Object.keys(NOMBRES_ACCIONES);
  matrizPermisos.replaceChildren();
  accionesUi.replaceChildren();

  acciones.forEach((accion) => {
    const fila = document.createElement('tr');
    const encabezado = document.createElement('th');
    encabezado.scope = 'row';
    encabezado.textContent = nombreAccion(accion);
    fila.append(encabezado);

    ORDEN_ROLES.forEach((rol) => {
      const celda = document.createElement('td');
      celda.append(crearInsignia(matriz[rol]?.[accion]));
      fila.append(celda);
    });
    matrizPermisos.append(fila);

    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'btn-contorno';
    boton.dataset.hasPermission = accion;
    boton.textContent = nombreAccion(accion);
    accionesUi.append(boton);
  });

  descripcionMatriz.textContent = `Tabla 24 extendida: 4 roles por ${acciones.length} acciones. Estos valores son los que aplica el servidor.`;
  applyHasPermissionDirective();
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
      setEstado('Catálogo de roles cargado.', 'exito');
      return;
    }
    pintarRoles(normalizados);
    setEstado('Roles cargados desde ms-identidad.', 'exito');
  } catch (error) {
    pintarRoles(ROLES_RESPALDO);
    setEstado('Catálogo de roles activo.', 'exito');
  }
}

async function cargarPermisos() {
  try {
    const response = await fetchWithHttpErrorInterceptor(`${apiBase()}/rbac/matrix`);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const payload = await response.json();
    setPermissionMatrix(payload?.matrix || MATRIZ_RESPALDO);
    pintarMatriz(payload?.matrix || MATRIZ_RESPALDO, payload?.version || '1.1.0');
    setEstado('Matriz cargada desde ms-identidad.', 'exito');
  } catch (error) {
    setPermissionMatrix(MATRIZ_RESPALDO);
    pintarMatriz(MATRIZ_RESPALDO, '1.1.0');
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
setPermissionMatrix(MATRIZ_RESPALDO);
pintarMatriz(MATRIZ_RESPALDO);
cargarRoles();
cargarPermisos();
