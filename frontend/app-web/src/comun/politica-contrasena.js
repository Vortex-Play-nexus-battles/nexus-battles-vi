/**
 * La política de contraseñas, para decirla ANTES de enviar — R17.
 *
 * Es la misma de RF-AUT-002 que aplica el servicio de identidad
 * (`PasswordPolicyValidator`): más de 8 caracteres, con mayúscula, minúscula,
 * número y símbolo. El servidor sigue siendo quien decide —esta copia solo
 * adelanta el aviso—; si algún día la política cambia allí y aquí no, lo peor
 * que pasa es que el servidor rechace con su propio motivo, que la vista
 * también enseña.
 *
 * @module comun/politica-contrasena
 */

/** Longitud que hay que SUPERAR (no alcanzar): «más de 8 caracteres». */
export const LONGITUD_MINIMA_EXCLUSIVA = 8;

/**
 * Las reglas que la contraseña todavía no cumple, en el orden en que se leen.
 * Vacía si cumple todas.
 *
 * @param {string|null|undefined} contrasena
 * @returns {string[]}
 */
export function reglasQueFaltan(contrasena) {
  const valor = typeof contrasena === 'string' ? contrasena : '';
  const faltan = [];
  // `length` cuenta unidades UTF-16, igual que `String.length()` en Java: así
  // la cuenta coincide con la del servidor también con emojis.
  if (valor.length <= LONGITUD_MINIMA_EXCLUSIVA) {
    faltan.push(`más de ${LONGITUD_MINIMA_EXCLUSIVA} caracteres`);
  }
  if (!/\p{Lu}/u.test(valor)) {
    faltan.push('una mayúscula');
  }
  if (!/\p{Ll}/u.test(valor)) {
    faltan.push('una minúscula');
  }
  if (!/\p{Nd}/u.test(valor)) {
    faltan.push('un número');
  }
  // Símbolo = cualquier cosa que no sea letra ni dígito (`isLetterOrDigit`).
  if (/^[\p{L}\p{Nd}]*$/u.test(valor)) {
    faltan.push('un símbolo');
  }
  return faltan;
}

/**
 * La frase que se enseña bajo el campo, o `null` si cumple.
 *
 * @param {string|null|undefined} contrasena
 * @returns {string|null}
 */
export function motivoDeContrasena(contrasena) {
  const faltan = reglasQueFaltan(contrasena);
  if (faltan.length === 0) {
    return null;
  }
  const lista =
    faltan.length === 1 ? faltan[0] : `${faltan.slice(0, -1).join(', ')} y ${faltan.at(-1)}`;
  return `A tu contraseña le falta: ${lista}.`;
}
