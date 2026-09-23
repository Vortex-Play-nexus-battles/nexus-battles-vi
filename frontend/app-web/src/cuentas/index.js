/**
 * Arranque de la home del jugador.
 *
 * Solo tres cosas: exigir sesión, montar la cabecera y montar la home. La
 * lógica de qué se pinta y qué se pide vive en `home.js`, que sí se prueba.
 */

import { montarCabecera } from '../comun/cabecera-app.js';
import { exigirAcceso } from '../comun/acceso.js';
import { montarHome } from './home.js';

// Vista privada: sin sesión (o caducada) se va al login con vuelta aquí;
// con sesión pero sin permiso, se explica en vez de rebotar (§17).
// `exigirAcceso` comprueba sesión y rol contra la matriz, y devuelve la
// sesión ya leída, así que no se lee dos veces.
const sesion = exigirAcceso('home');
if (sesion) {
  montarCabecera(document.querySelector('[data-cabecera-app]'), {
    vista: 'home',
    seccionActiva: 'cuenta',
  });
  montarHome(document, { sesion });
  mostrarAdministracion(sesion.rol);
}

/**
 * Los atajos administrativos de la home. Quien no tiene el rol no ve ni la
 * sección: un encabezado huérfano sobre una rejilla vacía es ruido.
 *
 * @param {string|null} rol
 */
function mostrarAdministracion(rol) {
  const seccion = document.querySelector('[data-zona="administracion"]');
  if (!seccion) {
    return;
  }
  let alguna = false;
  for (const acceso of seccion.querySelectorAll('[data-roles]')) {
    const permitidos = acceso.dataset.roles.split(',').map((uno) => uno.trim());
    acceso.hidden = !permitidos.includes(rol);
    alguna = alguna || !acceso.hidden;
  }
  seccion.hidden = !alguna;
}
