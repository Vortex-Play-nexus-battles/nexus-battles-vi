// index.js
// Menú principal GLOBAL de toda la app tras iniciar sesión — es el destino al
// que login.js redirige con `window.location.href = './'`, así que este
// módulo asume responsabilidad completa por la sesión: si no hay rol
// guardado, no hay sesión.
//
// BORRADOR/PROTOTIPO: enlaza a páginas de los tres dominios (cuentas,
// contenido, plataforma) para mostrarle al equipo cómo podría quedar el
// punto de entrada. Las rutas se tomaron del listado real de
// frontend/app-web/src/, no se inventó ninguna.
//
// La barra compartida (../comun/barra-navegacion.js) no contempla opciones de
// rol admin (solo trae opciones fijas de jugador), así que el filtrado de los
// accesos administrativos por rol se resuelve aquí, no dentro de la barra.

import { construirBarra } from '../comun/barra-navegacion.js';

const RUTA_LOGIN = './login.html';

const CLAVE_ROL = 'nexus.rolActual';
const CLAVE_APODO = 'nexus.apodoActual';

const rolActual = sessionStorage.getItem(CLAVE_ROL);

if (!rolActual) {
  window.location.href = RUTA_LOGIN;
} else {
  iniciar();
}

function iniciar() {
  montarBarraNavegacion();
  mostrarBienvenida();
  filtrarTarjetasPorRol();
  ocultarSeccionesVacias();
  configurarCerrarSesion();
}

function montarBarraNavegacion() {
  const barra = construirBarra({
    seccionActiva: 'cuenta',
    sesion: { autenticado: true },
    navegar: (ruta) => {
      window.location.href = ruta;
    }
  });

  document.body.prepend(barra);
}

function mostrarBienvenida() {
  const apodo = sessionStorage.getItem(CLAVE_APODO);
  const elementoApodo = document.getElementById('bienvenida-apodo');

  if (elementoApodo && apodo) {
    elementoApodo.textContent = apodo;
  }
}

/**
 * Muestra u oculta cada tarjeta de acceso según su atributo
 * `data-roles` (lista separada por comas). Las tarjetas sin ese
 * atributo se ven para cualquier rol autenticado.
 */
function filtrarTarjetasPorRol() {
  const tarjetas = document.querySelectorAll('[data-roles]');

  tarjetas.forEach((tarjeta) => {
    const rolesPermitidos = tarjeta.dataset.roles
      .split(',')
      .map((rol) => rol.trim());

    tarjeta.hidden = !rolesPermitidos.includes(rolActual);
  });
}

/**
 * Una sección (ej. "Administración") deja de tener sentido si, tras
 * filtrar por rol, ninguna de sus tarjetas quedó visible — oculta
 * también su subtítulo para no dejar un encabezado huérfano.
 */
function ocultarSeccionesVacias() {
  const secciones = document.querySelectorAll('.seccion-menu');

  secciones.forEach((seccion) => {
    const tarjetas = seccion.querySelectorAll('.tarjeta-acceso');
    const tieneTarjetaVisible = Array.from(tarjetas).some(
      (tarjeta) => !tarjeta.hidden
    );

    seccion.hidden = !tieneTarjetaVisible;
  });
}

function configurarCerrarSesion() {
  const boton = document.getElementById('btn-cerrar-sesion');

  if (!boton) {
    return;
  }

  boton.addEventListener('click', () => {
    sessionStorage.clear();
    window.location.href = RUTA_LOGIN;
  });
}
