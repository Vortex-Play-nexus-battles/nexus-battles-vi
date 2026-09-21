/**
 * HU-SUB-011 - Cliente HTTP hacia ms-subastas.
 *
 * Solo hace fetch y valida la forma de la respuesta -- nunca construye DOM
 * (eso es responsabilidad de vitrina-subastas.js), mismo patron de
 * separacion que ya usa el proyecto (cliente-inventario.js / vitrina.js).
 */

// Ruta relativa, no host absoluto.
//
// Antes apuntaba a http://localhost:8092. Desde un navegador que no sea el de
// la maquina de desarrollo, "localhost" es el equipo de quien mira la pagina,
// asi que la vista no cargaba nada en el servidor. El borde del entorno
// (infrastructure/red-balanceo/borde-dev.conf) enruta /api/v1/subastas a
// ms-subastas en el mismo origen, igual que hace con el resto de servicios.
const BASE_URL = '/api/v1/subastas';

/**
 * Trae una pagina del listado de subastas activas.
 *
 * @param {object} filtros ver contracts/openapi/ms-subastas-listado.yaml
 * @param {string} [filtros.q]
 * @param {string[]} [filtros.tipoProducto]
 * @param {string} [filtros.rareza]
 * @param {number} [filtros.precioMin]
 * @param {number} [filtros.precioMax]
 * @param {string} [filtros.tiempoRestante]
 * @param {string} [filtros.tipoVenta]
 * @param {string} [filtros.metodoPago]
 * @param {string} [filtros.vendedor]
 * @param {string} [filtros.ordenarPor]
 * @param {number} [pagina] cero-indexado, como el backend.
 * @param {number} [tamano]
 * @returns {Promise<object>} PaginaDeSubastasResponse
 */
export async function listarSubastas(filtros = {}, pagina = 0, tamano = 16) {
  const parametros = construirParametros(filtros, pagina, tamano);
  const respuesta = await fetch(`${BASE_URL}?${parametros}`);

  if (!respuesta.ok) {
    throw await errorDesdeRespuesta(respuesta);
  }

  const pagina_ = await respuesta.json();
  if (!Array.isArray(pagina_.contenido)) {
    throw new TypeError('La respuesta del listado de subastas debe traer un arreglo "contenido"');
  }
  return pagina_;
}

/**
 * Sugerencias de autocompletado. q es obligatorio: sin texto no tiene
 * sentido pedir sugerencias (mismo criterio que el backend, que responde
 * 400 si falta).
 *
 * @param {string} q
 * @param {number} [limite]
 * @returns {Promise<string[]>}
 */
export async function sugerirSubastas(q, limite = 8) {
  if (typeof q !== 'string' || q.trim() === '') {
    throw new TypeError('q es obligatorio para pedir sugerencias');
  }

  const parametros = new URLSearchParams({ q, limite: String(limite) });
  const respuesta = await fetch(`${BASE_URL}/sugerencias?${parametros}`);

  if (!respuesta.ok) {
    throw await errorDesdeRespuesta(respuesta);
  }

  const cuerpo = await respuesta.json();
  return Array.isArray(cuerpo.sugerencias) ? cuerpo.sugerencias : [];
}

function construirParametros(filtros, pagina, tamano) {
  const parametros = new URLSearchParams();
  parametros.set('page', String(pagina));
  parametros.set('size', String(tamano));

  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor === undefined || valor === null || valor === '') {
      continue;
    }
    if (Array.isArray(valor)) {
      for (const item of valor) {
        parametros.append(clave, item);
      }
    } else {
      parametros.set(clave, String(valor));
    }
  }
  return parametros;
}

/**
 * El backend responde application/problem+json (ManejadorDeErroresSubastas).
 * Se envuelve en un Error normal para que quien llame no tenga que conocer
 * el formato problem+json.
 */
async function errorDesdeRespuesta(respuesta) {
  try {
    const problema = await respuesta.json();
    return new Error(problema.detail ?? `Error ${respuesta.status} al consultar subastas`);
  } catch {
    return new Error(`Error ${respuesta.status} al consultar subastas`);
  }
}
