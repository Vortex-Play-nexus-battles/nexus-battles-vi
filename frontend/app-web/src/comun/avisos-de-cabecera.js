/**
 * El contador de la campana, en TODAS las vistas — auditoría de DEV del 30-sep.
 *
 * La cabecera pinta la campana con su contador oculto (`shell.js`) y hasta
 * ahora solo la vista de notificaciones lo encendía (`campana.js`): con avisos
 * sin leer, la campana no decía nada hasta que uno abría la bandeja. Aquí la
 * cabecera arranca la misma bandeja de HU-NOT-006 —cuenta por HTTP al cargar,
 * `ContadorActualizado` por el canal al instante, también cuando se lee en
 * otra pestaña, y consulta periódica si el canal cae— y solo pinta la cuenta.
 *
 * La lógica es de `plataforma/notificaciones/` (M15), su dueño: aquí no se
 * repite. Se carga bajo demanda, así una vista sin sesión no la descarga y un
 * fallo al cargarla no tumba la cabecera.
 *
 * @module comun/avisos-de-cabecera
 */

/**
 * Si hay un servicio al que preguntar. Las pruebas unitarias corren sobre
 * jsdom, sin `fetch` y sin servidor: ahí no se abre ningún canal.
 *
 * @returns {boolean}
 */
export function hayRed() {
  return (
    typeof globalThis.fetch === 'function' &&
    !/\bjsdom\//.test(String(globalThis.navigator?.userAgent ?? ''))
  );
}

/**
 * @param {object} [opciones]
 * @param {ParentNode|null} [opciones.raiz] la cabecera ya montada
 * @param {() => Promise<{montarContadorDeCabecera: Function}>} [opciones.cargar]
 * @param {() => boolean} [opciones.conRed]
 * @returns {Promise<object|null>|null} la bandeja, o null si no hay nada que montar
 */
export function montarAvisosDeCabecera({
  raiz = null,
  cargar = () => import('../plataforma/notificaciones/contador-de-cabecera.js'),
  conRed = hayRed,
} = {}) {
  if (!raiz?.querySelector?.('[data-zona="contador"]') || !conRed()) {
    return null;
  }
  return cargar()
    .then((modulo) => modulo.montarContadorDeCabecera(raiz))
    .catch((error) => {
      console.warn('No se pudo montar el contador de notificaciones:', error?.message ?? error);
      return null;
    });
}
