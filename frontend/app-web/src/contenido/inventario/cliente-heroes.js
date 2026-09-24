/**
 * Acceso al catalogo de heroes — R5.
 *
 * Lectura publica de `GET /api/v1/heroes/{nombre}`, declarado en
 * `contracts/openapi/heroes.yaml`. No habia ningun cliente de este servicio en
 * el frontend: un barrido de `api/v1/heroes` sobre `src/` no encontraba ni una
 * llamada, solo comentarios. Asi que la descripcion del heroe y sus acciones
 * —que el catalogo publica y estan probadas— no se veian en ninguna pantalla.
 *
 * ## Lo que este cliente NO trae
 *
 * **Nivel.** El catalogo lo acepta como parametro de ruta
 * (`/heroes/{nombre}/niveles/{nivel}`) pero no lo guarda: su propio contrato
 * dice que «el estado del heroe (nivel y experiencia) lo guarda el inventario;
 * aqui no se persiste nada», y el documento del inventario no tiene esa
 * columna. Nadie lo persiste, asi que no hay nivel que pedir. Llamar a
 * `/niveles/1` para rellenar el hueco seria inventarse que todos los heroes
 * estan a nivel 1.
 *
 * **Rareza.** No existe en ningun contrato de contenido.
 *
 * ## Catalogo y propiedad son cosas distintas
 *
 * Lo que devuelve esto describe el PROTOTIPO («Guerrero Tanque»), no el heroe
 * de un jugador («Aquiles»). Las estadisticas de un heroe propio, con su
 * equipamiento aplicado, las da el inventario en otra ruta. Mezclarlas seria
 * ensenarle al jugador las cifras de otro.
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

const RUTA = '/api/v1/heroes';

/**
 * La ficha del prototipo: descripcion, si sana y sus tres acciones.
 *
 * @param {string} prototipo nombre del catalogo, tal como lo publica productos
 *        en el campo `prototipo` del producto.
 * @param {{fetchImpl?: Function}} [opciones] inyeccion para las pruebas.
 * @returns {Promise<object>} la ficha tal como la devuelve el servicio.
 */
export async function consultarFichaDeHeroe(
  prototipo,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  if (typeof prototipo !== 'string' || prototipo.trim() === '') {
    throw new TypeError('El prototipo es obligatorio');
  }
  // Sin cabecera de identidad: la lectura del catalogo es publica, y mandarla
  // sugeriria que la respuesta depende de quien pregunta.
  const respuesta = await fetchImpl(`${RUTA}/${encodeURIComponent(prototipo.trim())}`);
  if (!respuesta.ok) {
    const fallo = new Error(`No se pudo leer el héroe del catálogo (${respuesta.status})`);
    fallo.status = respuesta.status;
    throw fallo;
  }
  return respuesta.json();
}
