/**
 * Formularios con credenciales: ningún envío nativo — G1 (4-oct).
 *
 * ## El defecto
 *
 * Un `<form>` sin `method` se envía por GET. Si la persona pulsa «Entrar» (o
 * Enter en un campo) antes de que el guion de la vista escuche `submit` —red
 * lenta, un módulo que no cargó, JavaScript desactivado—, el navegador navega
 * a `/login?email=…&password=…`: la contraseña queda en la barra, en el
 * historial, en la bitácora del borde y en el `Referer` de cada recurso de la
 * página siguiente. La regresión del feedback lo vio en AWS DEV (#844): el
 * envío nativo llegó antes que `login.js`.
 *
 * ## Las capas
 *
 * Cada una basta por sí sola para que la contraseña no acabe en una dirección:
 *
 *   1. el HTML: `method="post"` en todo formulario con contraseña, código o
 *      correo de las vistas de cuenta. Un envío nativo, si llegara a haberlo,
 *      va en el cuerpo y no en la dirección;
 *   2. el HTML: sus botones de envío nacen `disabled` con `data-espera-guion`.
 *      Con el botón por omisión desactivado tampoco hay envío implícito con
 *      Enter (HTML, «implicit submission»). Los enciende `formularioListo()`
 *      cuando la vista YA escucha `submit`, nunca antes;
 *   3. el borde (`borde-dev.conf`): una petición con un parámetro de
 *      contraseña en la consulta no se registra tal cual y se redirige a la
 *      misma ruta sin consulta.
 *
 * Y, para cualquier otro anfitrión, `sinCredencialesEnLaDireccion()` borra de
 * la barra lo que una página vieja en caché haya podido dejar ahí.
 *
 * `sin-envio-nativo.test.js` impide que un formulario nuevo nazca sin las dos
 * primeras.
 *
 * @module comun/ui/formulario-seguro
 */

/** Marca con la que el HTML apaga un botón de envío hasta que llega el guion. */
export const ESPERA_GUION = 'data-espera-guion';

/**
 * Nombres de campo que no pueden quedarse en una dirección: cualquier
 * `*password*` y los demás de las vistas de cuenta (el correo, el código del
 * correo, la confirmación de la contraseña nueva, las preguntas y respuestas
 * de seguridad). Solo se usa en esas vistas: en `/jugar`, `?codigo=` es la
 * invitación a una sala privada y no se toca.
 */
const CAMPO_SENSIBLE =
  /password|contrasena|contraseña|^(?:email|codigo|confirmacion)$|^respuesta|^preguntaSeguridad/i;

/**
 * Enciende los botones de envío que el HTML trae apagados (`data-espera-guion`).
 *
 * Se llama DESPUÉS de `addEventListener('submit', …)`: antes, un clic sería un
 * envío nativo. Deja `data-listo` en el formulario, que es lo que esperan las
 * pruebas de navegador para escribir.
 *
 * @param {HTMLFormElement|null|undefined} formulario
 */
export function formularioListo(formulario) {
  if (!formulario) {
    return;
  }
  for (const control of Array.from(formulario.elements)) {
    if (control.hasAttribute(ESPERA_GUION)) {
      control.removeAttribute(ESPERA_GUION);
      control.disabled = false;
    }
  }
  formulario.setAttribute('data-listo', '');
}

/**
 * ¿Lleva esta consulta algún campo de credenciales?
 *
 * @param {string} busqueda `location.search`, con o sin `?`
 * @returns {boolean}
 */
export function hayCredencialesEn(busqueda) {
  const parametros = new URLSearchParams(busqueda ?? '');
  for (const nombre of parametros.keys()) {
    if (CAMPO_SENSIBLE.test(nombre)) {
      return true;
    }
  }
  return false;
}

/**
 * Quita de la barra los campos de credenciales sin recargar: los que dejaría un
 * envío nativo de una versión vieja de la página. El resto de la consulta
 * (`?volver=`, `?motivo=`) y el fragmento se conservan.
 *
 * @param {{ubicacion?: Location, historial?: History}} [entorno]
 * @returns {boolean} si había algo que quitar
 */
export function sinCredencialesEnLaDireccion({
  ubicacion = globalThis.location,
  historial = globalThis.history,
} = {}) {
  if (!ubicacion || !hayCredencialesEn(ubicacion.search)) {
    return false;
  }
  const parametros = new URLSearchParams(ubicacion.search);
  for (const nombre of Array.from(parametros.keys())) {
    if (CAMPO_SENSIBLE.test(nombre)) {
      parametros.delete(nombre);
    }
  }
  const consulta = parametros.toString();
  const limpia = `${ubicacion.pathname}${consulta ? `?${consulta}` : ''}${ubicacion.hash ?? ''}`;
  historial?.replaceState(historial.state, '', limpia);
  return true;
}
