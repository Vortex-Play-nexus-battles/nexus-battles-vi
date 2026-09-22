/**
 * Arranque de «Mi cuenta».
 *
 * Solo monta: sesión, cabecera, pestañas y los tres módulos que hacen el
 * trabajo (`cuenta.js`, `cambiar-password.js`). Antes este archivo tenía 513
 * líneas y buscaba trece elementos por id que la vista no tenía (#567).
 */

import { montarCabecera, exigirSesion, cerrarSesion } from '../comun/cabecera-app.js';
import { montarPestanas } from '../comun/ui/pestanas.js';
import { montarCuenta, montarAccionesDeSesion } from './cuenta.js';
import { montarCambioDePassword } from './cambiar-password.js';
import { mejorarContrasena } from '../comun/ui/campo.js';

const sesion = exigirSesion();

if (sesion) {
  montarCabecera(document.querySelector('[data-cabecera-app]'), { seccionActiva: 'cuenta' });

  montarPestanas(
    document.querySelector('[data-zona="pestanas"]'),
    [
      { id: 'resumen', etiqueta: 'Resumen', panel: panelDe('resumen') },
      { id: 'perfil', etiqueta: 'Perfil', panel: panelDe('perfil') },
      { id: 'seguridad', etiqueta: 'Seguridad', panel: panelDe('seguridad') },
      { id: 'historial', etiqueta: 'Historial', panel: panelDe('historial') },
    ],
    { activa: 'resumen' },
  );

  montarCuenta(document, { sesion });
  montarCambioDePassword(document);
  montarAccionesDeSesion(document, { sesion, alCerrarSesion: () => cerrarSesion() });

  for (const nombre of ['passwordActual', 'nuevaPassword', 'confirmacion']) {
    mejorarContrasena(document.getElementById(nombre));
  }
}

/** @param {string} id */
function panelDe(id) {
  return document.querySelector(`[data-zona="panel-${id}"]`);
}
