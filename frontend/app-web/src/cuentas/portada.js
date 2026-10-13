/**
 * La portada pública — F6 (auditoría del 4-oct, cambio autorizado n.º 3).
 *
 * Es lo que se ve en `/`: qué es el juego, cómo empezar y la tienda con
 * productos reales, antes de tener cuenta. Las direcciones del recorrido son
 * `/` (esta portada), `/login`, `/registro` y, tras entrar, `/inicio`.
 *
 * Este módulo solo monta la cabecera del portal, lleva los enlaces a sus
 * direcciones (limpias detrás del borde) y, si ya hay sesión, cambia «Crear
 * una cuenta / Entrar» por «Ir a mi inicio». La tienda la pinta
 * `portada-tienda.js`, la misma de la entrada: comprar, guardar, calificar y
 * comentar llevan a `/login` con la vuelta a la tienda.
 *
 * @module cuentas/portada
 */

import { montarCabecera } from '../comun/cabecera-app.js';
import { RUTAS, leerSesion, resolver } from '../comun/sesion.js';

/** Cada acción de la portada y la vista a la que lleva. */
export const DESTINOS = Object.freeze({
  'crear-cuenta': RUTAS.registro,
  entrar: RUTAS.login,
  'ir-al-inicio': RUTAS.inicio,
});

/**
 * Pone en cada enlace su dirección: la limpia (`/registro`, `/login`,
 * `/inicio`) cuando el borde las sirve, la del fichero si no.
 *
 * @param {Document} doc
 */
export function enlazar(doc = document) {
  for (const [accion, ruta] of Object.entries(DESTINOS)) {
    for (const enlace of doc.querySelectorAll(`a[data-accion="${accion}"]`)) {
      enlace.href = resolver(ruta);
    }
  }
}

/**
 * Con sesión, «Ir a mi inicio» en vez de «Crear una cuenta / Entrar»: quien
 * ya entró no tiene que volver a hacerlo para seguir jugando.
 *
 * @param {Document} doc
 * @param {{autenticado: boolean, apodo?: string}} [sesion]
 */
export function mostrarAcceso(doc = document, sesion = leerSesion()) {
  const sin = doc.querySelector('[data-zona="acciones-sin-sesion"]');
  const con = doc.querySelector('[data-zona="acciones-con-sesion"]');
  if (!sin || !con) {
    return;
  }
  const dentro = sesion?.autenticado === true;
  sin.hidden = dentro;
  con.hidden = !dentro;
  const saludo = doc.querySelector('[data-zona="saludo"]');
  if (saludo) {
    saludo.textContent = dentro && sesion.apodo ? `Hola, ${sesion.apodo}.` : '';
  }
}

// Arranque en el navegador; en las pruebas se llama a mano.
if (globalThis.document?.addEventListener) {
  globalThis.document.addEventListener('DOMContentLoaded', () => {
    montarCabecera(globalThis.document.querySelector('[data-cabecera-app]'), {
      vista: 'portada',
      seccionActiva: null,
    });
    enlazar(globalThis.document);
    mostrarAcceso(globalThis.document);
  });
}
