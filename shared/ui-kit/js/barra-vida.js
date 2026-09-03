/**
 * Barra de vida — HU-SAL-005
 *
 * Los umbrales de color (60 % y 40 %) NO estan escritos aqui: se leen de
 * las fichas de diseno en tokens.css (--vida-umbral-alto y
 * --vida-umbral-medio). Si el cliente cambia los umbrales, se toca un
 * archivo y no dos.
 *
 * El color nunca es el unico indicador: la funcion escribe tambien el
 * valor numerico y los atributos aria, para que la barra sea legible con
 * daltonismo y con lector de pantalla.
 *
 * Marcado esperado:
 *
 *   <div class="barra-vida" data-barra-vida role="progressbar"
 *        aria-valuemin="0" aria-valuemax="100">
 *     <span class="barra-vida__nombre">Arquero del Norte</span>
 *     <div class="barra-vida__pista"><div class="barra-vida__relleno"></div></div>
 *     <span class="barra-vida__valor"></span>
 *   </div>
 *
 * @module barra-vida
 */

/** @type {{alto: number, medio: number} | null} */
let umbralesCache = null;

/**
 * Lee los umbrales desde las fichas de diseno.
 * @returns {{alto: number, medio: number}}
 */
export function leerUmbrales() {
  if (umbralesCache) return umbralesCache;

  const raiz = getComputedStyle(document.documentElement);
  const alto = Number.parseFloat(raiz.getPropertyValue('--vida-umbral-alto'));
  const medio = Number.parseFloat(raiz.getPropertyValue('--vida-umbral-medio'));

  // Si tokens.css no esta cargado, no adivinamos: fallamos con un mensaje
  // claro. Un umbral inventado en una barra de vida es un error silencioso.
  if (Number.isNaN(alto) || Number.isNaN(medio)) {
    throw new Error(
      'barra-vida: no se encontraron --vida-umbral-alto / --vida-umbral-medio. ' +
      'Falta cargar shared/ui-kit/css/tokens.css antes de este modulo.'
    );
  }

  umbralesCache = { alto, medio };
  return umbralesCache;
}

/**
 * Clasifica un porcentaje de vida segun los umbrales.
 *
 * Las fronteras salen literalmente de RF-JUE-009 (HU-SAL-005): «verde por
 * encima del 60 %, amarillo entre el 60 % y el 40 % INCLUSIVE, y rojo por
 * debajo del 40 %». O sea:
 *
 *   (60, 100]  -> alto
 *   [40,  60]  -> medio   <- los dos extremos entran
 *   [ 0,  40)  -> bajo
 *
 * De ahi la asimetria entre los dos comparadores, que no es un descuido:
 * el 60 exacto NO es verde y el 40 exacto SI es amarillo. Antes ambos eran
 * `>`, y con 40 puntos justos la barra se pintaba roja.
 *
 * @param {number} porcentaje 0-100
 * @returns {'alto'|'medio'|'bajo'}
 */
export function clasificar(porcentaje) {
  const { alto, medio } = leerUmbrales();
  if (porcentaje > alto) return 'alto';
  if (porcentaje >= medio) return 'medio';
  return 'bajo';
}

/**
 * Pinta una barra de vida.
 *
 * @param {HTMLElement} elemento  contenedor .barra-vida
 * @param {number} vidaActual     puntos de vida actuales
 * @param {number} vidaMaxima     puntos de vida totales
 */
export function actualizar(elemento, vidaActual, vidaMaxima) {
  if (!(elemento instanceof HTMLElement)) {
    throw new TypeError('barra-vida: se esperaba un HTMLElement.');
  }
  if (!Number.isFinite(vidaMaxima) || vidaMaxima <= 0) {
    throw new RangeError('barra-vida: vidaMaxima debe ser un numero mayor que cero.');
  }

  // Se recorta al rango valido: un servidor que reporte vida negativa o
  // por encima del maximo no debe romper la interfaz.
  const acotada = Math.min(Math.max(vidaActual, 0), vidaMaxima);
  const porcentaje = (acotada / vidaMaxima) * 100;

  elemento.style.setProperty('--vida', String(porcentaje));
  elemento.dataset.estado = clasificar(porcentaje);

  const valor = elemento.querySelector('.barra-vida__valor');
  if (valor) valor.textContent = `${Math.round(acotada)}/${Math.round(vidaMaxima)}`;

  // Accesibilidad: el lector de pantalla anuncia el cambio.
  elemento.setAttribute('role', 'progressbar');
  elemento.setAttribute('aria-valuemin', '0');
  elemento.setAttribute('aria-valuemax', String(Math.round(vidaMaxima)));
  elemento.setAttribute('aria-valuenow', String(Math.round(acotada)));

  const nombre = elemento.querySelector('.barra-vida__nombre');
  elemento.setAttribute(
    'aria-label',
    `Vida de ${nombre?.textContent?.trim() || 'participante'}: ` +
    `${Math.round(acotada)} de ${Math.round(vidaMaxima)}`
  );
}

/**
 * Inicializa todas las barras del documento que traigan data-vida-actual
 * y data-vida-maxima. Comodo para pintar el estado inicial de la sala
 * antes de que llegue el primer mensaje por WebSocket.
 *
 * @param {ParentNode} [raiz=document]
 */
export function inicializar(raiz = document) {
  for (const el of raiz.querySelectorAll('[data-barra-vida]')) {
    const actual = Number(el.dataset.vidaActual);
    const maxima = Number(el.dataset.vidaMaxima);
    if (Number.isFinite(actual) && Number.isFinite(maxima) && maxima > 0) {
      actualizar(/** @type {HTMLElement} */ (el), actual, maxima);
    }
  }
}
