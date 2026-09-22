/**
 * Escapado de texto para plantillas de marcado — UX-R2.8c.
 *
 * ## Cuando se usa esto, y cuando NO
 *
 * La regla del proyecto es construir nodos (`comun/ui/dom.js`), donde
 * `textContent` hace que ningun dato pueda inyectar marcado. Ese es el camino
 * por defecto y `sin-innerhtml.test.js` lo vigila.
 *
 * Este modulo existe para el caso que queda: una pantalla grande que **ya**
 * esta escrita como plantillas de cadena y que se va a reescribir por partes,
 * no de golpe. Reescribir dos mil trescientas lineas de una vez, en una vista
 * que mueve creditos, es mas arriesgado que cerrar el agujero hoy y mover la
 * estructura despues.
 *
 * ## Que agujero
 *
 * `pujas.js` interpolaba, sin escapar nada, el nombre del objeto, su
 * descripcion, el apodo del vendedor, el del pujador y el motivo del cierre.
 * Todos vienen del servidor y los escribe otra persona. Una subasta llamada
 *
 *     <img src=x onerror="fetch('/api/v1/creditos/transferir', …)">
 *
 * se ejecutaba al abrir «Mis pujas», con la sesion de quien mira puesta. En
 * una pantalla de dinero.
 *
 * ## Que escapa
 *
 * Los cinco caracteres que pueden sacar a un valor de su sitio dentro del
 * marcado: `&`, `<`, `>`, `"` y `'`. Con eso, un valor es seguro tanto dentro
 * de un elemento como dentro de un atributo **entrecomillado**. En un
 * atributo SIN comillas no basta —un espacio ya abre otro atributo— asi que
 * la regla es: todo atributo va entre comillas. La prueba lo comprueba.
 *
 * Lo que este modulo **no** hace, a proposito:
 *
 *  - No limpia URLs. Un `href` o un `src` con datos del servidor necesita
 *    ademas comprobar el esquema (`http:`/`https:`), porque `javascript:`
 *    no lleva ninguno de los cinco caracteres de arriba. Para eso esta
 *    `urlSegura()`.
 *  - No permite marcado «bueno». No hay lista blanca ni saneador: aqui todo
 *    valor es texto. Si algo tiene que llevar formato, se construye con
 *    nodos.
 */

const ESCAPES = Object.freeze({
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
});

/**
 * Deja un valor listo para meterlo en una plantilla de marcado.
 *
 * `null` y `undefined` se convierten en cadena vacia y no en «null», que es
 * el otro defecto clasico de las plantillas: ensenarle la palabra «undefined»
 * a quien esta mirando.
 *
 * @param {unknown} valor
 * @returns {string}
 */
export function esc(valor) {
  if (valor === null || valor === undefined) {
    return '';
  }
  return String(valor).replace(/[&<>"']/g, (caracter) => ESCAPES[caracter]);
}

/**
 * Una URL que se puede poner en `href` o `src` sin que ejecute nada.
 *
 * Solo pasan `http:` y `https:`. Cualquier otra cosa —`javascript:`,
 * `data:`, un esquema inventado, o una cadena que ni siquiera es una URL—
 * devuelve la alternativa, que por omision es vacio.
 *
 * @param {unknown} valor
 * @param {string} [alternativa]
 * @returns {string} ya escapada, lista para una plantilla
 */
export function urlSegura(valor, alternativa = '') {
  if (!valor) {
    return alternativa;
  }
  let destino;
  try {
    destino = new URL(String(valor), globalThis.location?.href ?? 'https://localhost');
  } catch {
    return alternativa;
  }
  if (destino.protocol !== 'http:' && destino.protocol !== 'https:') {
    return alternativa;
  }
  return esc(destino.href);
}
