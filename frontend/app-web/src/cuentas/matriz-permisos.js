/**
 * ==========================================================================
 * Controlador de Interfaz: HU-RBAC-001 (Panel de Acceso por Roles)
 * Pila Tecnológica: Vanilla JS ES2022 (Sin frameworks / Sin dependencias)
 * Arquitectura: Conexión en vivo con ms-identidad y directiva has-permission
 * ==========================================================================
 */

import {
  applyHasPermissionDirective,
  setPermissionMatrix,
  setCurrentRole
} from './directives/has-permission.directive.js';
import { construirBarra } from '../comun/barra-navegacion.js';

// Montar la barra superior compartida oficial (HU-INV-004)
const token = sessionStorage.getItem('nexus.token');
const contenedorBarra = document.getElementById('contenedor-barra');
if (contenedorBarra) {
  contenedorBarra.replaceChildren(
    construirBarra({
      seccionActiva: 'cuenta',
      sesion: { autenticado: Boolean(token) },
      navegar: (ruta) => {
        if (ruta === '/inventario') {
          window.location.href = '../contenido/inventario/inventario.html';
        } else if (ruta === '/cuenta') {
          window.location.href = './matriz-permisos.html';
        } else {
          alert(`La sección ${ruta} se habilitará en el Sprint 2.`);
        }
      }
    })
  );
}

// Candidatos de puerto: Spring Boot local (8089) o contenedor Docker (8081)
const BASES_BACKEND = [
  'http://localhost:8089/api/v1',
  'http://localhost:8081/api/v1'
];

const selectorRol = document.querySelector('#selector-rol');
const rolActivoEtiqueta = document.querySelector('#rol-activo-etiqueta');
const contadorPrivilegios = document.querySelector('#contador-privilegios');
const progresoRelleno = document.querySelector('#progreso-relleno');
const toastAccion = document.querySelector('#toast-accion');
const toastMensaje = document.querySelector('#toast-mensaje');
const tarjetasRoles = document.querySelectorAll('.tarjeta-rol');
const botonesAccion = document.querySelectorAll('.boton-accion-card');
const avisoSesion = document.querySelector('#aviso-sesion-activa');
const textoSesion = document.querySelector('#texto-sesion-activa');
const estadoConexion = document.querySelector('#estado-conexion');
const textoEstadoConexion = document.querySelector('#texto-estado-conexion');

const NOMBRES_ROLES = {
  JUGADOR: 'Jugador',
  MODERADOR: 'Moderador',
  ADMINISTRADOR: 'Administrador',
  SUPER_ADMINISTRADOR: 'Super Administrador'
};

const NIVELES_ROLES = {
  JUGADOR: 'Nivel 1',
  MODERADOR: 'Nivel 2',
  ADMINISTRADOR: 'Nivel 3',
  SUPER_ADMINISTRADOR: 'Nivel 4 - Total'
};

// Matriz de referencia local (Tabla 24 extendida)
const MATRIZ_REFERENCIA = {
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
    GESTIONAR_CUENTAS: 'DENIED'
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
    GESTIONAR_CUENTAS: 'DENIED'
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
    GESTIONAR_CUENTAS: 'GRANTED'
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
    GESTIONAR_CUENTAS: 'GRANTED'
  }
};

let matrizActiva = MATRIZ_REFERENCIA;
let baseActiva = null;

async function pedir(base, ruta, opciones = {}) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), 1500);
  try {
    return await fetch(`${base}${ruta}`, { ...opciones, signal: controller.signal });
  } finally {
    clearTimeout(t);
  }
}

async function cargarMatrizDesdeBackend() {
  for (const base of BASES_BACKEND) {
    try {
      const res = await pedir(base, '/rbac/matrix');
      if (!res.ok) continue;
      const payload = await res.json();
      if (payload && payload.matrix && Object.keys(payload.matrix).length > 0) {
        matrizActiva = payload.matrix;
        baseActiva = base;

        try {
          const resRoles = await pedir(base, '/rbac/roles');
          if (resRoles.ok) {
            const roles = await resRoles.json();
            if (Array.isArray(roles)) {
              roles.forEach((r) => {
                const clave = r.role || r.name;
                if (clave && NOMBRES_ROLES[clave]) NOMBRES_ROLES[clave] = r.name || NOMBRES_ROLES[clave];
                const card = document.querySelector(`.tarjeta-rol[data-rol="${clave}"] .tarjeta-rol__desc`);
                if (card && r.description) card.textContent = r.description;
              });
            }
          }
        } catch {
          // Descripciones opcionales
        }

        const puerto = base.match(/:(\d+)/)?.[1] ?? '8089';
        estadoConexion.className = 'indicador-estado-conexion indicador-conectado';
        textoEstadoConexion.textContent = `Conectado a ms-identidad (puerto ${puerto}) — Versión ${payload.version || '1.1.0'}. Matriz cargada desde el servidor.`;
        return;
      }
    } catch {
      // Probar siguiente candidato
    }
  }

  matrizActiva = MATRIZ_REFERENCIA;
  baseActiva = null;
  estadoConexion.className = 'indicador-estado-conexion indicador-desconectado';
  textoEstadoConexion.textContent = 'Sin conexión con ms-identidad (:8089 / :8081). Mostrando la matriz de referencia local (Tabla 24).';
}

let timerToast = null;
function mostrarToast(mensaje) {
  if (timerToast) clearTimeout(timerToast);
  toastMensaje.textContent = mensaje;
  toastAccion.style.display = 'flex';
  timerToast = setTimeout(() => {
    toastAccion.style.display = 'none';
  }, 4200);
}

/**
 * Actualiza la vista completa gobernada estrictamente por la directiva
 * has-permission (HU-RBAC-001) sin pisar la visibilidad.
 */
function actualizarVistaRol() {
  const rol = selectorRol.value;

  // 1. Sincronizar tarjetas visuales de rol
  tarjetasRoles.forEach((card) => {
    const esActiva = card.dataset.rol === rol;
    card.classList.toggle('activa', esActiva);
    card.setAttribute('aria-checked', String(esActiva));
  });

  // 2. Actualizar etiquetas de resumen y cálculo de capacidad
  rolActivoEtiqueta.textContent = `Rol Activo: ${NOMBRES_ROLES[rol]} (${NIVELES_ROLES[rol]})`;

  let permitidas = 0;
  const total = 12;
  const permisosRol = matrizActiva[rol] || {};
  for (const accion of Object.keys(permisosRol)) {
    if (permisosRol[accion] === 'GRANTED' || permisosRol[accion] === 'TEMPORARY') {
      permitidas++;
    }
  }
  contadorPrivilegios.textContent = `${permitidas} / ${total} Acciones`;
  progresoRelleno.style.width = `${(permitidas / total) * 100}%`;

  // 3. Pasar la matriz y el rol a la directiva reactiva (ÚNICA dueña del display)
  setPermissionMatrix(matrizActiva);
  setCurrentRole(rol);
  applyHasPermissionDirective();

  // 4. Actualizar badges semánticos de estado para los elementos permitidos
  botonesAccion.forEach((btn) => {
    const accion = btn.dataset.hasPermission;
    const tipo = permisosRol[accion];
    const badge = btn.querySelector('.badge-estado');
    if (badge) {
      if (tipo === 'TEMPORARY') {
        badge.textContent = 'Temporal';
        badge.className = 'badge-estado badge-estado--temporal';
      } else {
        badge.textContent = 'Habilitado';
        badge.className = 'badge-estado';
      }
    }
  });
}

/**
 * Consulta la autorización real en el servidor (POST /api/v1/rbac/authorize)
 */
async function evaluarAccion(accion, nombreAccion) {
  const rol = selectorRol.value;

  if (baseActiva) {
    try {
      const res = await pedir(baseActiva, '/rbac/authorize', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ role: rol, action: accion })
      });
      if (res.ok) {
        const r = await res.json();
        const veredicto = r.permitted ? 'AUTORIZADA' : 'DENEGADA';
        mostrarToast(`Servidor: acción "${nombreAccion}" ${veredicto} para ${NOMBRES_ROLES[rol]} (${r.permissionType}). ${r.reason || ''}`);
        return;
      }
      mostrarToast(`El servidor respondió ${res.status} al evaluar "${nombreAccion}".`);
      return;
    } catch {
      mostrarToast(`No se pudo contactar al servidor para evaluar "${nombreAccion}". Se muestra el resultado local.`);
    }
  }

  const tipo = (matrizActiva[rol] || {})[accion] || 'DENIED';
  const permitido = tipo === 'GRANTED' || tipo === 'TEMPORARY';
  mostrarToast(`Local (sin conexión): "${nombreAccion}" ${permitido ? 'AUTORIZADA' : 'DENEGADA'} para ${NOMBRES_ROLES[rol]} (${tipo}).`);
}

// Eventos de selección por tarjetas
tarjetasRoles.forEach((card) => {
  card.addEventListener('click', () => {
    selectorRol.value = card.dataset.rol;
    actualizarVistaRol();
  });
  card.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      card.click();
    }
  });
});

// Evento de selección por dropdown
selectorRol.addEventListener('change', actualizarVistaRol);

// Cada botón de acción consulta la autorización real en el servidor
botonesAccion.forEach((btn) => {
  btn.addEventListener('click', () => {
    const accion = btn.dataset.hasPermission;
    const nombreAccion = btn.dataset.accionNombre || btn.textContent.trim();
    evaluarAccion(accion, nombreAccion);
  });
});

/**
 * Decodifica el token JWT de la sesión activa para preseleccionar
 * el rol real con el que se autenticó el usuario.
 */
function obtenerRolDesdeSesion() {
  try {
    if (!token) return null;
    const partes = token.split('.');
    if (partes.length !== 3) return null;
    const payload = JSON.parse(atob(partes[1].replace(/-/g, '+').replace(/_/g, '/')));
    const rol = payload.rol;
    if (['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR'].includes(rol)) {
      return { rol, apodo: payload.sub };
    }
  } catch {
    // Token inválido o ausente
  }
  return null;
}

// Inicialización
const sesionInfo = obtenerRolDesdeSesion();
if (sesionInfo) {
  selectorRol.value = sesionInfo.rol;
  avisoSesion.style.display = 'flex';
  textoSesion.textContent = `Sesión autenticada detectada: conectado como "${sesionInfo.apodo}" (${NOMBRES_ROLES[sesionInfo.rol]}).`;
}

actualizarVistaRol();
cargarMatrizDesdeBackend().then(actualizarVistaRol);
