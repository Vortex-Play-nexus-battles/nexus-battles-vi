/**
 * Arranque de la home del jugador.
 *
 * Solo tres cosas: exigir acceso, montar el armazón y montar la home. La
 * lógica de qué se pinta y qué se pide vive en `home.js`, que sí se prueba.
 *
 * Hasta UX-R3.2 había una cuarta: `mostrarAdministracion()`, que recorría el
 * marcado buscando `data-roles` y enseñaba u ocultaba cuatro atajos
 * administrativos. Era la cuarta lista de roles del producto —escrita a mano,
 * en el HTML— y mezclaba en la home del jugador lo que ahora tiene consola
 * propia.
 */

import { montarCabecera } from '../comun/cabecera-app.js';
import { exigirAcceso } from '../comun/acceso.js';
import { montarHome } from './home.js';

// Vista privada: sin sesión (o caducada) se va al login con vuelta aquí;
// con sesión pero sin permiso, se explica en vez de rebotar.
// `exigirAcceso` comprueba sesión y rol contra la matriz, y devuelve la
// sesión ya leída, así que no se lee dos veces.
const sesion = exigirAcceso('home');
if (sesion) {
  const { elemento: cabecera } = montarCabecera(document.querySelector('[data-cabecera-app]'), {
    vista: 'home',
    seccionActiva: 'cuenta',
  });
  montarHome(document, { sesion, cabecera });
}
