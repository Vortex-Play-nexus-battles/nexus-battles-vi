/**
 * Arranque de «Mi cuenta».
 *
 * Solo monta: sesión, cabecera, pestañas y los módulos que hacen el trabajo
 * (`cuenta.js`, `cambiar-password.js`, desde B1 `preguntas-seguridad.js` y,
 * desde UXC-9, `estadisticas-cuenta.js`).
 * Antes este archivo tenía 513 líneas y buscaba trece elementos por id que la
 * vista no tenía (#567).
 */

import { montarCabecera, cerrarSesion } from '../comun/cabecera-app.js';
import { exigirAcceso } from '../comun/acceso.js';
import { montarPestanas } from '../comun/ui/pestanas.js';
import { montarCuenta, montarAccionesDeSesion } from './cuenta.js';
import { montarEstadisticas } from './estadisticas-cuenta.js';
import { montarCambioDePassword } from './cambiar-password.js';
import { montarPreguntasDeSeguridad } from './preguntas-seguridad.js';
import { mejorarContrasena } from '../comun/ui/campo.js';

const sesion = exigirAcceso('perfil');

if (sesion) {
  montarCabecera(document.querySelector('[data-cabecera-app]'), {
    vista: 'perfil',
    seccionActiva: 'cuenta',
  });

  montarPestanas(
    document.querySelector('[data-zona="pestanas"]'),
    [
      { id: 'resumen', etiqueta: 'Resumen', panel: panelDe('resumen') },
      { id: 'estadisticas', etiqueta: 'Estadísticas', panel: panelDe('estadisticas') },
      { id: 'perfil', etiqueta: 'Perfil', panel: panelDe('perfil') },
      { id: 'seguridad', etiqueta: 'Seguridad', panel: panelDe('seguridad') },
      { id: 'historial', etiqueta: 'Historial', panel: panelDe('historial') },
    ],
    { activa: 'resumen' },
  );

  montarCuenta(document, { sesion });
  montarEstadisticas(document);
  montarCambioDePassword(document);
  montarPreguntasDeSeguridad(document);
  montarAccionesDeSesion(document, { sesion, alCerrarSesion: () => cerrarSesion() });

  for (const nombre of [
    'passwordActual',
    'nuevaPassword',
    'confirmacion',
    'passwordActualPreguntas',
  ]) {
    mejorarContrasena(document.getElementById(nombre));
  }
}

/** @param {string} id */
function panelDe(id) {
  return document.querySelector(`[data-zona="panel-${id}"]`);
}
