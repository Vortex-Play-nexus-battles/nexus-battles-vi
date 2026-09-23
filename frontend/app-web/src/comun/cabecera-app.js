/**
 * Cabecera de la aplicación — punto de entrada estable para las vistas.
 *
 * ## Qué es esto ahora
 *
 * Hasta UX-R2 este archivo contenía tres cosas: la lectura de la sesión, los
 * guardas de ruta y el pintado de una barra única para todo el producto.
 * UX-R3.0 las separó, porque la barra dejó de ser una:
 *
 *   - `sesion.js`  — leer, cerrar y olvidar la sesión; rutas de la aplicación
 *   - `acceso.js`  — catálogo de roles, matriz vista→permiso y guardas
 *   - `shell.js`   — los tres armazones (portal, jugador, consola)
 *
 * Aquí queda el nombre que ya importan las 31 vistas, para que el cambio de
 * arquitectura no obligue a tocarlas todas a la vez y para que un módulo de
 * otro grupo que importe `montarCabecera` siga funcionando. Las vistas nuevas
 * importan de `shell.js` y de `acceso.js` directamente.
 *
 * @module comun/cabecera-app
 */

import { vistaDeRuta } from './acceso.js';
import { montarArmazon } from './shell.js';
import { leerSesion } from './sesion.js';

export { CLAVES, RUTAS, resolver, leerSesion, cerrarSesion, rutaDeVuelta } from './sesion.js';
export { exigirSesion, exigirAcceso, puedeVer, MATRIZ, ROLES } from './acceso.js';
export { SECCIONES, montarArmazon } from './shell.js';

/**
 * Monta el armazón que corresponde a esta vista y devuelve la sesión leída.
 *
 * **No hace falta decirle quién eres.** Deduce la vista de la URL y la matriz
 * (`acceso.js`) le dice si es portal, aplicación o consola. Una vista puede
 * forzarlo con `armazon` si sabe algo que la matriz no —por ahora, ninguna lo
 * necesita.
 *
 * @param {HTMLElement} raiz contenedor; se recomienda `<div data-cabecera-app>`
 * @param {object} [opciones]
 * @param {string|null} [opciones.seccionActiva] id de sección en curso
 * @param {{placeholder?: string, alBuscar?: (texto: string) => void}|null} [opciones.buscador]
 * @param {'publico'|'jugador'|'admin'} [opciones.armazon] fuerza un armazón
 * @param {string|null} [opciones.vista] fuerza la vista (por omisión, la URL)
 * @returns {{elemento: HTMLElement, sesion: object|null}}
 */
export function montarCabecera(raiz, opciones = {}) {
  const vista = opciones.vista ?? vistaDeRuta(opciones.ruta);
  return montarArmazon(raiz, { ...opciones, vista });
}

/**
 * La sesión de esta pestaña. Reexportado con su nombre de siempre para que
 * ninguna vista tenga que cambiar su importación en este bloque.
 */
export default leerSesion;
