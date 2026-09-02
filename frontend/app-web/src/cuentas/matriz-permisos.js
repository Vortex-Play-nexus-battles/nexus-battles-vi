/**
 * HU-RBAC-001 — Panel de Control de Acceso por Roles.
 *
 * Lógica de la vista `matriz-permisos.html`: selector visual de rol
 * (tarjetas + dropdown nativo sincronizados), resumen de privilegios y
 * aplicación de la directiva reactiva `[data-has-permission]`.
 *
 * Convención del repo (`.claude/rules/frontend-web.md`): un `.html` + un
 * `.js` del mismo nombre por vista. Antes vivía embebido en el HTML y en
 * el ya retirado `demo-rbac.js`.
 */
import {
  applyHasPermissionDirective,
  setPermissionMatrix,
  setCurrentRole
} from './directives/has-permission.directive.js';
import { construirBarra } from '../comun/barra-navegacion.js';

// Montar la barra superior funcional compartida (HU-INV-004)
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

const selectorRol = document.querySelector('#selector-rol');
const rolActivoEtiqueta = document.querySelector('#rol-activo-etiqueta');
const contadorPrivilegios = document.querySelector('#contador-privilegios');
const progresoRelleno = document.querySelector('#progreso-relleno');
const toastAccion = document.querySelector('#toast-accion');
const toastMensaje = document.querySelector('#toast-mensaje');
const toastIcono = document.querySelector('#toast-icono');
const tarjetasRoles = document.querySelectorAll('.tarjeta-rol');
const botonesAccion = document.querySelectorAll('.boton-accion-card');

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

const MATRIZ_BASE = {
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

let timerToast = null;
function mostrarToast(mensaje, icono = '✓') {
  if (timerToast) clearTimeout(timerToast);
  toastIcono.textContent = icono;
  toastMensaje.textContent = mensaje;
  toastAccion.style.display = 'flex';
  timerToast = setTimeout(() => {
    toastAccion.style.display = 'none';
  }, 3200);
}

function actualizarVistaRol() {
  const rol = selectorRol.value;

  // 1. Sincronizar tarjetas visuales de rol
  tarjetasRoles.forEach((card) => {
    const esActiva = card.dataset.rol === rol;
    card.classList.toggle('activa', esActiva);
    card.setAttribute('aria-checked', String(esActiva));
  });

  // 2. Actualizar etiquetas de resumen y barra de privilegios
  rolActivoEtiqueta.textContent = `Rol Activo: ${NOMBRES_ROLES[rol]} (${NIVELES_ROLES[rol]})`;

  let permitidas = 0;
  const total = 12;
  const permisosRol = MATRIZ_BASE[rol] || {};
  for (const accion of Object.keys(permisosRol)) {
    if (permisosRol[accion] === 'GRANTED' || permisosRol[accion] === 'TEMPORARY') {
      permitidas++;
    }
  }
  contadorPrivilegios.textContent = `${permitidas} / ${total} Acciones`;
  progresoRelleno.style.width = `${(permitidas / total) * 100}%`;

  // 3. Aplicar directiva reactiva de interfaz [data-has-permission]
  setCurrentRole(rol);
  applyHasPermissionDirective();
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

// Evento en cada botón de acción para feedback interactivo
botonesAccion.forEach((btn) => {
  btn.addEventListener('click', () => {
    const nombreAccion = btn.dataset.accionNombre || btn.textContent.trim();
    const rol = selectorRol.value;
    mostrarToast(`Operación autorizada: "${nombreAccion}" ejecutada con éxito bajo rol ${NOMBRES_ROLES[rol]}.`);
  });
});

// Inicialización
setPermissionMatrix(MATRIZ_BASE);
actualizarVistaRol();
