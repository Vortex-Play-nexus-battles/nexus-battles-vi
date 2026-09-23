/**
 * Iconos del kit.
 *
 * `shared/ui-kit/iconos/sprite.svg` trae treinta simbolos dibujados para este
 * producto —espada, escudo, rayo, frasco, corazon, moneda, trofeo, candado— y
 * hasta ahora solo dos vistas referenciaban alguno. Todo lo demas de la
 * aplicacion se comunica con texto pelado, que es parte de por que no parece un
 * videojuego.
 *
 * ## Por que no se escribe la ruta a mano
 *
 * El sprite vive en la raiz del monorepo y las vistas cuelgan a profundidades
 * distintas: desde `cuentas/` son cuatro saltos y desde
 * `contenido/inventario/` son cinco. Un componente compartido no puede saber
 * desde donde lo llaman, asi que la ruta se calcula desde la del propio modulo
 * con `import.meta.url` y sale bien mire quien lo mire.
 *
 * ## El icono nunca es la unica informacion
 *
 * `etiqueta` no es opcional por capricho. Un icono sin nombre accesible es una
 * caja vacia para un lector de pantalla, y el producto tiene que cumplir
 * contraste y lectura (RNF de accesibilidad). Quien quiera el icono como
 * adorno, porque el texto ya esta al lado, pasa `etiqueta: null` y el simbolo
 * queda marcado como decorativo de forma explicita.
 */

/** Los simbolos que trae el sprite. Si no esta aqui, no existe. */
export const ICONOS = Object.freeze([
  'espada',
  'escudo',
  'rayo',
  'frasco',
  'reloj',
  'corazon',
  'gota',
  'estrella',
  'moneda',
  'fuego',
  'copo',
  'objetivo',
  'usuarios',
  'campana',
  'chat',
  'buscar',
  'filtro',
  'ajustes',
  'usuario',
  'cerrar',
  'chevron',
  'mas',
  'editar',
  'papelera',
  'ojo',
  'alerta',
  'check',
  'candado',
  'trofeo',
  'escudo-check',
]);

const ESPACIO_SVG = 'http://www.w3.org/2000/svg';

/**
 * Ruta al sprite, relativa a ESTE archivo y no a quien lo importe.
 *
 * @returns {string}
 */
export function rutaDelSprite() {
  return new URL('../../../../../shared/ui-kit/iconos/sprite.svg', import.meta.url).href;
}

/**
 * Dibuja un icono del sprite.
 *
 * @param {string} nombre uno de `ICONOS`
 * @param {{etiqueta?: string|null, clase?: string, tam?: string}} [opciones]
 *   `etiqueta` es el nombre accesible; `null` marca el icono como decorativo,
 *   que es lo correcto cuando el texto va justo al lado.
 * @returns {SVGElement}
 */
export function icono(nombre, { etiqueta, clase = 'icono', tam } = {}) {
  if (!ICONOS.includes(nombre)) {
    // Fallar aqui y no pintar un hueco: un icono que no existe se ve como un
    // espacio en blanco y nadie lo nota hasta la demo.
    throw new Error(`icono: «${nombre}» no esta en el sprite. Disponibles: ${ICONOS.join(', ')}`);
  }

  const svg = document.createElementNS(ESPACIO_SVG, 'svg');
  svg.setAttribute('class', clase);
  svg.setAttribute('focusable', 'false');
  if (tam) {
    svg.setAttribute('width', tam);
    svg.setAttribute('height', tam);
  }

  if (etiqueta === null || etiqueta === undefined) {
    svg.setAttribute('aria-hidden', 'true');
  } else {
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', etiqueta);
  }

  const uso = document.createElementNS(ESPACIO_SVG, 'use');
  uso.setAttribute('href', `${rutaDelSprite()}#${nombre}`);
  svg.append(uso);
  return svg;
}

/**
 * El mismo icono, pero como texto de marcado.
 *
 * `icono()` devuelve un nodo, que es lo correcto cuando se construye el DOM
 * pieza a pieza. Las vistas que todavia arman su marcado con plantillas de
 * cadena —`pujas.js` entre ellas— no pueden usarlo sin reescribirse enteras,
 * y hasta que les toque el turno la alternativa real no era `icono()`: era un
 * emoji. Un emoji no hereda `currentColor`, cambia de dibujo en cada sistema
 * operativo y el lector de pantalla lo lee en voz alta.
 *
 * Valida contra `ICONOS` igual que `icono()`: el simbolo que no existe falla
 * aqui y no en la demo, pintando un hueco que nadie nota.
 *
 * @param {string} nombre uno de `ICONOS`
 * @param {{etiqueta?: string|null, clase?: string}} [opciones]
 * @returns {string}
 */
export function iconoHtml(nombre, { etiqueta = null, clase = 'icono' } = {}) {
  if (!ICONOS.includes(nombre)) {
    throw new Error(
      `iconoHtml: «${nombre}» no esta en el sprite. Disponibles: ${ICONOS.join(', ')}`,
    );
  }
  const accesible =
    etiqueta === null || etiqueta === undefined
      ? 'aria-hidden="true"'
      : `role="img" aria-label="${etiqueta.replace(/"/g, '&quot;')}"`;
  return `<svg class="${clase}" focusable="false" ${accesible}><use href="${rutaDelSprite()}#${nombre}" /></svg>`;
}
