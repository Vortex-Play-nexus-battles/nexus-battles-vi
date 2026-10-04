/**
 * Lo que un héroe PROPIO sabe hacer, reunido para el combate — UXC-2.
 *
 * La partida (`GET /partidas/{id}` de salas-partidas.yaml) trae del héroe lo
 * que pinta una barra: nombre, vida, retrato. No trae su prototipo ni sus
 * acciones, y la barra de acciones del combate necesita las tres de la Tabla 7
 * con su coste (§6.1.2). Esto las junta desde los servicios que ya las
 * publican, con el token del jugador y sin inventar nada:
 *
 *   1. el elemento del inventario propio (`GET /inventario/elementos?pagina=`,
 *      hasta encontrarlo) → `productoId`;
 *   2. el producto del catálogo (`GET /productos/{id}`, público) → `prototipo`;
 *   3. la ficha del prototipo (`GET /heroes/{prototipo}`, pública) → acciones;
 *   4. las cifras con el equipo puesto
 *      (`GET /inventario/heroes/{id}/estadisticas`) → poder máximo.
 *
 * `GET /inventario/elementos/{id}` no sirve aquí: es una operación interna con
 * token de servicio (inventario.yaml, `BearerInterno`).
 *
 * Si algo falla, se devuelve lo que se haya podido reunir y `motivo` dice qué
 * faltó: el combate sigue con el ataque básico, que no depende de esto.
 *
 * @module comun/heroe-propio
 */

import { fetchWithHttpErrorInterceptor } from './interceptors/http-error.interceptor.js';

/**
 * El coste en puntos de poder, leído del texto que compone el catálogo
 * («2 puntos de poder»). `null` si el texto no lleva cifra: entonces se
 * muestra el texto tal cual y no se inventa un número.
 *
 * @param {string|null|undefined} costo
 * @returns {number|null}
 */
export function puntosDePoder(costo) {
  const encontrado = /(\d+)/.exec(String(costo ?? ''));
  return encontrado ? Number(encontrado[1]) : null;
}

/** Tope de páginas que se recorren buscando el héroe (16 por página). */
const MAX_PAGINAS = 25;

async function leerJson(url, fetchImpl) {
  const respuesta = await fetchImpl(url);
  if (!respuesta.ok) {
    const fallo = new Error(`${url} respondió ${respuesta.status}`);
    fallo.status = respuesta.status;
    throw fallo;
  }
  return respuesta.json();
}

/**
 * Busca un elemento propio por su identificador, página a página.
 *
 * @param {string} elementoId
 * @param {{fetchImpl?: Function, maxPaginas?: number}} [opciones]
 * @returns {Promise<object|null>}
 */
export async function buscarElementoPropio(
  elementoId,
  { fetchImpl = fetchWithHttpErrorInterceptor, maxPaginas = MAX_PAGINAS } = {},
) {
  for (let pagina = 0; pagina < maxPaginas; pagina += 1) {
    const respuesta = await leerJson(`/api/v1/inventario/elementos?pagina=${pagina}`, fetchImpl);
    const elementos = Array.isArray(respuesta?.elementos) ? respuesta.elementos : [];
    const encontrado = elementos.find((elemento) => elemento?.id === elementoId);
    if (encontrado) {
      return encontrado;
    }
    const ultima =
      respuesta?.ultima === true ||
      elementos.length === 0 ||
      pagina + 1 >= (respuesta?.totalPaginas ?? 0);
    if (ultima) {
      return null;
    }
  }
  return null;
}

/**
 * @param {object} opciones
 * @param {string} opciones.heroeId identificador del héroe en el inventario
 * @param {Function} [opciones.fetchImpl]
 * @returns {Promise<{prototipo: string|null, acciones: Array<{nombre: string, costo?: string,
 *   efecto?: string}>, poderMaximo: number|null, motivo: string|null}>}
 */
export async function cargarHeroePropio({ heroeId, fetchImpl = fetchWithHttpErrorInterceptor }) {
  const vacio = { prototipo: null, acciones: [], poderMaximo: null, motivo: null };
  if (!heroeId) {
    return { ...vacio, motivo: 'No se sabe qué héroe llevas a esta partida.' };
  }

  let elemento;
  try {
    elemento = await buscarElementoPropio(heroeId, { fetchImpl });
  } catch {
    return {
      ...vacio,
      motivo: 'No se pudo leer tu inventario para traer las acciones de tu héroe.',
    };
  }
  if (!elemento?.productoId) {
    return { ...vacio, motivo: 'Tu héroe no aparece en tu inventario.' };
  }

  let prototipo = null;
  try {
    const producto = await leerJson(
      `/api/v1/productos/${encodeURIComponent(elemento.productoId)}`,
      fetchImpl,
    );
    prototipo = producto?.prototipo ?? null;
  } catch {
    return {
      ...vacio,
      motivo: 'El catálogo no respondió: no se conocen las acciones de tu héroe.',
    };
  }
  if (!prototipo) {
    return { ...vacio, motivo: 'El catálogo no dice de qué prototipo es tu héroe.' };
  }

  const [ficha, estadisticas] = await Promise.allSettled([
    leerJson(`/api/v1/heroes/${encodeURIComponent(prototipo)}`, fetchImpl),
    leerJson(`/api/v1/inventario/heroes/${encodeURIComponent(heroeId)}/estadisticas`, fetchImpl),
  ]);

  const acciones =
    ficha.status === 'fulfilled' && Array.isArray(ficha.value?.acciones)
      ? ficha.value.acciones.filter((accion) => accion?.nombre)
      : [];
  const poderPropio =
    estadisticas.status === 'fulfilled' && Number.isFinite(estadisticas.value?.poder)
      ? estadisticas.value.poder
      : null;
  const poderDelPrototipo =
    ficha.status === 'fulfilled' && Number.isFinite(ficha.value?.estadisticasNivel1?.poder)
      ? ficha.value.estadisticasNivel1.poder
      : null;

  return {
    prototipo,
    acciones,
    poderMaximo: poderPropio ?? poderDelPrototipo,
    motivo:
      ficha.status === 'fulfilled'
        ? null
        : 'El catálogo de héroes no respondió: no se conocen las acciones de tu héroe.',
  };
}
