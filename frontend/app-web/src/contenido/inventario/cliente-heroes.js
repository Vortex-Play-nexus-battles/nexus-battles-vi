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
 * **Nivel del heroe propio.** El catalogo no lo guarda: «el estado del
 * heroe (nivel y experiencia) lo guarda el inventario». Desde inventario
 * 1.5.0 (B4) `ElementoInventario` trae `nivel` y `experiencia`; lo que da
 * este servicio es la tabla de cuanta experiencia pide cada nivel
 * (`GET /api/v1/progresion/niveles`, UXC-9), para decir cuanto falta.
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

/**
 * UXC-9 — la tabla de progresión (§6.1.1): los ocho niveles y la
 * experiencia que pide cada uno para subir (el 8 no pide). La regla vive en
 * el servicio de héroes; aquí solo se lee.
 *
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<Array<{nivel: number, experienciaParaSubir?: number}>>}
 */
export async function consultarTablaDeNiveles({ fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl('/api/v1/progresion/niveles');
  if (!respuesta.ok) {
    const fallo = new Error('No se pudo leer la tabla de niveles');
    fallo.status = respuesta.status;
    throw fallo;
  }
  const tabla = await respuesta.json();
  return Array.isArray(tabla) ? tabla : [];
}

/**
 * UXC-9 — el prototipo en un nivel (`GET /api/v1/heroes/{nombre}/niveles/{nivel}`):
 * las acciones ya aprendidas en ese nivel y su épica afín.
 *
 * @param {string} prototipo
 * @param {number} nivel
 * @param {{fetchImpl?: Function}} [opciones]
 * @returns {Promise<object>} `VistaPorNivel` de heroes.yaml
 */
export async function consultarVistaPorNivel(
  prototipo,
  nivel,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  if (typeof prototipo !== 'string' || prototipo.trim() === '' || !Number.isInteger(nivel)) {
    throw new TypeError('El prototipo y el nivel son obligatorios');
  }
  const respuesta = await fetchImpl(
    `${RUTA}/${encodeURIComponent(prototipo.trim())}/niveles/${nivel}`,
  );
  if (!respuesta.ok) {
    const fallo = new Error('No se pudo leer el héroe en su nivel');
    fallo.status = respuesta.status;
    throw fallo;
  }
  return respuesta.json();
}
