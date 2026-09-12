/**
 * Aviso de seccion no disponible — HU-DIS-003, CA-02.
 *
 * Cuando un microservicio se cae, el jugador NO debe ver una pantalla en
 * blanco: debe ver con claridad que funcion esta limitada y que el resto del
 * juego sigue. Este modulo pinta ese aviso a partir del problem detail que
 * devuelve el backend.
 *
 * Cumple `shared/ui-kit/MAPEO-ERRORES.md`:
 *
 * - §2 — se decide por `type`, nunca por el texto de `title` o `detail`. El
 *   texto puede cambiar de redaccion sin aviso; `type` es estable.
 * - §3 — `type` e `instance` no se muestran nunca al usuario.
 * - §5.1 vs §5.2 — una seccion caida NO es el fallo de la vista entera ni el de
 *   una accion suelta: es una parte de la pantalla que no se puede pintar
 *   mientras el resto si. Por eso es un componente propio y no un `Aviso`, y
 *   por eso se registra en §7.
 * - §9 — no se deja sin salida: siempre lleva reintentar.
 *
 * Marcado esperado (el contenedor lo pone la vista, este modulo lo rellena):
 *
 *   <div data-seccion="Inventario"></div>
 *
 * @module comun/degradacion/aviso-degradacion
 */

/** Identificador estable acordado con el backend. Ver ErroresDeDegradacion. */
export const TIPO_SECCION_NO_DISPONIBLE =
  'https://nexusbattles.local/errores/seccion-no-disponible';

const SEGUNDOS_POR_OMISION = 30;

/**
 * ¿Este error es una seccion degradada?
 *
 * Se mira `type` y, como respaldo, el `status`. Nunca el texto: MAPEO-ERRORES
 * §2 lo prohibe expresamente porque `title` y `detail` pueden reescribirse.
 *
 * @param {unknown} problema cuerpo de la respuesta en formato problem details
 * @returns {boolean}
 */
export function esSeccionDegradada(problema) {
  if (!problema || typeof problema !== 'object') {
    return false;
  }
  return problema.type === TIPO_SECCION_NO_DISPONIBLE;
}

/**
 * Lee el nombre de la seccion limitada.
 *
 * El backend lo manda en la propiedad `seccion` justamente para esto: sin ese
 * dato solo se podria decir «algo fallo», y el criterio pide decir QUE funcion
 * esta limitada.
 *
 * @param {object} problema
 * @param {string} [respaldo] nombre a usar si el backend no lo mando
 * @returns {string}
 */
export function seccionDe(problema, respaldo = 'Esta seccion') {
  const seccion = problema && typeof problema.seccion === 'string' ? problema.seccion.trim() : '';
  return seccion || respaldo;
}

/**
 * Segundos que conviene esperar antes de reintentar.
 * @param {object} problema
 * @returns {number}
 */
export function reintentarEnSegundos(problema) {
  const segundos = Number(problema?.reintentarEnSegundos);
  return Number.isFinite(segundos) && segundos > 0 ? segundos : SEGUNDOS_POR_OMISION;
}

/**
 * Pinta el aviso de seccion limitada dentro de un contenedor.
 *
 * El contenido anterior se reemplaza: MAPEO-ERRORES §9 dice que no se apilan
 * avisos, uno a la vez por contexto.
 *
 * @param {HTMLElement} contenedor donde se pinta
 * @param {object} problema problem detail recibido del backend
 * @param {{ alReintentar?: () => void }} [opciones]
 * @returns {HTMLElement} el aviso pintado
 */
export function pintarSeccionDegradada(contenedor, problema, opciones = {}) {
  if (!contenedor) {
    throw new Error('aviso-degradacion: hace falta un contenedor donde pintar.');
  }

  const seccion = seccionDe(problema, contenedor.dataset?.seccion);
  const segundos = reintentarEnSegundos(problema);

  const aviso = document.createElement('div');
  aviso.className = 'seccion-degradada';
  aviso.dataset.seccionDegradada = seccion;

  // role="status" y no role="alert": el lector de pantalla debe anunciarlo sin
  // interrumpir lo que el jugador este haciendo, porque el resto de la pantalla
  // sigue siendo usable. Un "alert" aqui seria tratar una degradacion como una
  // emergencia.
  aviso.setAttribute('role', 'status');
  aviso.setAttribute('aria-live', 'polite');

  const titulo = document.createElement('p');
  titulo.className = 'seccion-degradada__titulo';
  titulo.textContent = `${seccion} no disponible temporalmente`;

  const cuerpo = document.createElement('p');
  cuerpo.className = 'seccion-degradada__cuerpo';
  cuerpo.textContent = 'El resto del juego sigue funcionando.';

  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'seccion-degradada__reintentar';
  boton.textContent = 'Reintentar';
  boton.addEventListener('click', () => {
    if (typeof opciones.alReintentar === 'function') {
      opciones.alReintentar();
    }
  });

  const espera = document.createElement('p');
  espera.className = 'seccion-degradada__espera';
  espera.textContent = `Vuelve a intentarlo en unos ${segundos} segundos.`;

  aviso.append(titulo, cuerpo, espera, boton);

  contenedor.replaceChildren(aviso);
  contenedor.hidden = false;

  return aviso;
}

/**
 * Quita el aviso cuando la seccion vuelve.
 * @param {HTMLElement} contenedor
 */
export function limpiarSeccionDegradada(contenedor) {
  if (!contenedor) {
    return;
  }
  contenedor.replaceChildren();
  contenedor.hidden = true;
}

/**
 * Atiende una respuesta HTTP y pinta el aviso si la seccion esta degradada.
 *
 * Devuelve `true` cuando lo ha gestionado, para que la vista sepa que no tiene
 * que pintar nada mas. Si la respuesta no es una degradacion, no toca nada y
 * devuelve `false`: cada error se pinta donde le toca segun MAPEO-ERRORES §5.
 *
 * @param {Response} respuesta
 * @param {HTMLElement} contenedor
 * @param {{ alReintentar?: () => void }} [opciones]
 * @returns {Promise<boolean>}
 */
export async function atenderRespuestaDegradada(respuesta, contenedor, opciones = {}) {
  if (!respuesta || respuesta.ok) {
    return false;
  }

  let problema;
  try {
    problema = await respuesta.clone().json();
  } catch {
    // Un 503 sin cuerpo JSON no es una degradacion declarada por el backend:
    // no se adivina. Lo trata quien corresponda.
    return false;
  }

  if (!esSeccionDegradada(problema)) {
    return false;
  }

  pintarSeccionDegradada(contenedor, problema, opciones);
  return true;
}
