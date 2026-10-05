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
import { montarPrivacidad } from './privacidad-cuenta.js';
import { montarCierreDeCuenta } from './cierre-cuenta.js';

import { montarCambioDePassword } from './cambiar-password.js';
import { montarPreguntasDeSeguridad } from './preguntas-seguridad.js';
import { montarSegundoFactor } from './segundo-factor-cuenta.js';
import { mejorarContrasena } from '../comun/ui/campo.js';
import { sinCredencialesEnLaDireccion } from '../comun/ui/formulario-seguro.js';

// G1 — antes que nada, y antes de `exigirAcceso`: sin sesión, la dirección
// actual viaja al login como `?volver=`, y con ella lo que un envío nativo de
// una versión vieja de esta página hubiera dejado (`?passwordActual=…`).
sinCredencialesEnLaDireccion();

const sesion = exigirAcceso('perfil');

if (sesion) {
  montarCabecera(document.querySelector('[data-cabecera-app]'), {
    vista: 'perfil',
    seccionActiva: 'cuenta',
  });

  // RFINAL-05 (§7.3.6) — «Privacidad»: el portal de tus datos y el cierre de
  // la cuenta. Se montan antes que las pestañas para que abrir la vista ya en
  // `#privacidad` los encuentre, pero no consultan nada hasta que se abre la
  // pestaña, y solo la primera vez: el portal pregunta a quince módulos.
  const privacidad = montarPrivacidad(document, { sesion });
  const cierre = montarCierreDeCuenta(document, { sesion });
  let privacidadAbierta = false;
  const abrirPrivacidad = () => {
    if (!privacidadAbierta) {
      privacidadAbierta = true;
      privacidad?.cargar();
      cierre?.cargar();
    }
  };

  const pestanas = montarPestanas(
    document.querySelector('[data-zona="pestanas"]'),
    [
      { id: 'resumen', etiqueta: 'Resumen', panel: panelDe('resumen') },
      { id: 'estadisticas', etiqueta: 'Estadísticas', panel: panelDe('estadisticas') },
      { id: 'perfil', etiqueta: 'Perfil', panel: panelDe('perfil') },
      { id: 'seguridad', etiqueta: 'Seguridad', panel: panelDe('seguridad') },
      { id: 'historial', etiqueta: 'Historial', panel: panelDe('historial') },
      { id: 'privacidad', etiqueta: 'Privacidad', panel: panelDe('privacidad') },
    ],
    { activa: 'resumen', alCambiar: (id) => id === 'privacidad' && abrirPrivacidad() },
  );
  document
    .querySelector('[data-accion="ir-a-privacidad"]')
    ?.addEventListener('click', () => pestanas.mostrar('privacidad'));

  montarCuenta(document, { sesion });
  montarEstadisticas(document);
  montarCambioDePassword(document);
  montarPreguntasDeSeguridad(document);
  // HU-AUT-007 — verificación en dos pasos, en la misma pestaña Seguridad.
  montarSegundoFactor(document);
  montarAccionesDeSesion(document, { sesion, alCerrarSesion: () => cerrarSesion() });

  for (const nombre of [
    'passwordActual',
    'nuevaPassword',
    'confirmacion',
    'passwordActualPreguntas',
    'passwordActualSegundoFactor',
  ]) {
    mejorarContrasena(document.getElementById(nombre));
  }
}

/** @param {string} id */
function panelDe(id) {
  return document.querySelector(`[data-zona="panel-${id}"]`);
}
