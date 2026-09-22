/**
 * Construccion de DOM — la pieza de mas abajo del kit de interfaz.
 *
 * Antes de este modulo, cuatro vistas (`torneos`, `sanciones`, `parametros`,
 * `tablero-tecnico`) declaraban cada una su propia funcion `nodo()`, identica
 * salvo detalles, y otras treinta usaban `document.createElement` a pelo. El
 * resultado eran cuatro formas distintas de crear el mismo `<article
 * class="tarjeta">` y ningun sitio donde arreglar un descuido de accesibilidad
 * una sola vez.
 *
 * No es un motor de plantillas ni un framework: es `createElement` con las
 * tres cosas que siempre se repiten (clase, texto, atributos de datos) y con
 * `textContent` SIEMPRE en vez de `innerHTML`, para que ningun dato del
 * servidor pueda inyectar marcado.
 */

/**
 * Crea un elemento.
 *
 * @param {string} etiqueta nombre de la etiqueta (`div`, `article`, `button`…)
 * @param {{clase?: string, texto?: string, datos?: Record<string, string|number|boolean>,
 *          atributos?: Record<string, string|number|boolean|null>, hijos?: Array<Node|string|null|undefined>}} [opciones]
 * @returns {HTMLElement}
 */
export function h(etiqueta, opciones = {}) {
  const { clase, texto, datos, atributos, hijos } = opciones;
  const elemento = document.createElement(etiqueta);
  if (clase) {
    elemento.className = clase;
  }
  if (texto !== undefined && texto !== null) {
    // textContent y no innerHTML: un nombre de equipo o un motivo de sancion
    // vienen del servidor y no pueden traer marcado.
    elemento.textContent = String(texto);
  }
  for (const [nombre, valor] of Object.entries(datos ?? {})) {
    elemento.dataset[nombre] = String(valor);
  }
  for (const [nombre, valor] of Object.entries(atributos ?? {})) {
    if (valor === null || valor === false) {
      continue;
    }
    elemento.setAttribute(nombre, valor === true ? '' : String(valor));
  }
  for (const hijo of hijos ?? []) {
    if (hijo === null || hijo === undefined || hijo === false) {
      continue;
    }
    elemento.append(hijo);
  }
  return elemento;
}

/**
 * Atajo para los casos de siempre: etiqueta, clase y texto.
 *
 * @param {string} etiqueta
 * @param {string} [clase]
 * @param {string|number} [texto]
 * @returns {HTMLElement}
 */
export function nodo(etiqueta, clase, texto) {
  return h(etiqueta, { clase, texto });
}

/**
 * Deja un contenedor vacio y devuelve el mismo contenedor, para encadenar.
 *
 * @param {ParentNode & {replaceChildren: Function}} contenedor
 * @returns {ParentNode}
 */
export function vaciar(contenedor) {
  contenedor.replaceChildren();
  return contenedor;
}

/**
 * Une clases ignorando lo que no es una cadena util. Evita el
 * `` `tarjeta ${x ? 'tarjeta--rota' : ''}` `` que deja espacios sueltos.
 *
 * @param {...(string|false|null|undefined)} clases
 * @returns {string}
 */
export function clases(...clases_) {
  return clases_.filter((c) => typeof c === 'string' && c.trim() !== '').join(' ');
}
