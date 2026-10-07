/**
 * PLAYER-07b (punto 27) — el nombre de un producto tal como lo lee el jugador.
 *
 * ## El defecto
 *
 * Al montar una subasta, la confirmación decía
 * «Espada de luz · ARMA · 7c9e6679-7425-40de-944b-e07fc1f90ae7»: la constante
 * del tipo y el identificador interno del elemento del inventario. El
 * identificador hace falta para la operación —viaja en la petición—, pero
 * nunca es algo que el jugador tenga que leer.
 *
 * ## De dónde sale el nombre
 *
 * Del catálogo (`GET /api/v1/productos/{id}`, público). El inventario guarda
 * solo la referencia `productoId` y un `nombrePropio` que se copió al
 * entregar el objeto (o que el jugador cambió después), así que el nombre de
 * verdad —el que verá quien puje, porque ms-subastas lo copia del catálogo al
 * publicar— es el del catálogo.
 *
 * ## El criterio para no enseñar un código (documentado en la PR)
 *
 * Un texto es un identificador técnico, y no un nombre, si está vacío, si
 * contiene un UUID, si es una tira hexadecimal larga o si coincide con uno de
 * los identificadores del propio objeto. En ese caso se enseña
 * «Objeto sin nombre», el mismo respaldo que ya usan `pujas.js` y
 * `pujas-api.js` para un nombre que falta. No se inventa nada: ni un nombre ni
 * una rareza; solo se deja de enseñar lo que no es un nombre.
 */

/** Lo que se lee cuando no hay un nombre legible. Igual que en `pujas.js`. */
export const SIN_NOMBRE = 'Objeto sin nombre';

const UUID_EN_EL_TEXTO = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;
const TIRA_HEXADECIMAL = /^[0-9a-f]{16,}$/i;

/**
 * ¿Es un identificador técnico en vez de un nombre?
 *
 * @param {unknown} texto
 * @param {Array<unknown>} [identificadores] ids del propio objeto (elemento,
 *   producto, subasta): un nombre igual a uno de ellos tampoco es un nombre
 * @returns {boolean}
 */
export function pareceIdentificador(texto, identificadores = []) {
  const limpio = typeof texto === 'string' ? texto.trim() : '';
  if (limpio === '') {
    return true;
  }
  if (UUID_EN_EL_TEXTO.test(limpio) || TIRA_HEXADECIMAL.test(limpio)) {
    return true;
  }
  return identificadores.some(
    (id) => id !== null && id !== undefined && String(id).trim() === limpio,
  );
}

/**
 * El nombre que se enseña, o el respaldo si lo que hay no es un nombre.
 *
 * @param {unknown} nombre
 * @param {{respaldo?: string, identificadores?: Array<unknown>}} [opciones]
 * @returns {string}
 */
export function nombreLegible(nombre, { respaldo = SIN_NOMBRE, identificadores = [] } = {}) {
  return pareceIdentificador(nombre, identificadores) ? respaldo : String(nombre).trim();
}

/**
 * Nombres del catálogo por `productoId`, pidiendo cada producto una sola vez.
 *
 * El estado de cada uno es:
 *
 *   - `ok`: el catálogo respondió con un nombre legible (`nombre`);
 *   - `sin-nombre`: respondió, pero sin un nombre que se pueda enseñar;
 *   - `error`: no respondió o respondió con un fallo. El motivo NO se guarda:
 *     el mensaje de `consultarProducto` lleva el identificador del producto y
 *     no puede acabar en la pantalla.
 *
 * Es memoria de la página y nada más: no escribe en `localStorage` ni en
 * `sessionStorage`.
 *
 * @param {{consultar: (productoId: string) => Promise<object>}} dependencias
 */
export function crearResolutorDeNombres({ consultar }) {
  /** @type {Map<string, {estado: string, nombre: string|null, promesa: Promise<object>}>} */
  const conocidos = new Map();

  /**
   * @param {string} productoId
   * @returns {Promise<{estado: string, nombre: string|null}>}
   */
  function resolver(productoId) {
    const clave = typeof productoId === 'string' ? productoId.trim() : '';
    if (clave === '') {
      return Promise.resolve({ estado: 'sin-nombre', nombre: null });
    }
    const conocido = conocidos.get(clave);
    if (conocido) {
      return conocido.promesa;
    }
    const entrada = { estado: 'pendiente', nombre: null, promesa: null };
    entrada.promesa = Promise.resolve()
      .then(() => consultar(clave))
      .then(
        (producto) => {
          const nombre = typeof producto?.nombre === 'string' ? producto.nombre.trim() : '';
          if (pareceIdentificador(nombre, [clave, producto?.id])) {
            entrada.estado = 'sin-nombre';
          } else {
            entrada.estado = 'ok';
            entrada.nombre = nombre;
          }
          return entrada;
        },
        () => {
          entrada.estado = 'error';
          return entrada;
        },
      );
    conocidos.set(clave, entrada);
    return entrada.promesa;
  }

  return {
    resolver,
    /** Lo que se sabe ya de un producto, sin pedirlo; null si nunca se pidió. */
    leer(productoId) {
      return conocidos.get(String(productoId ?? '').trim()) ?? null;
    },
    /** Olvida los que fallaron, para que «Reintentar» los vuelva a pedir. */
    olvidarFallidos() {
      for (const [clave, entrada] of conocidos) {
        if (entrada.estado === 'error') {
          conocidos.delete(clave);
        }
      }
    },
  };
}
