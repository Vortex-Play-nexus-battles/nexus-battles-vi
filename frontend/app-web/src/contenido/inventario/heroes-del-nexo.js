/**
 * «Héroes del Nexo» — revisión del modo jugador del 6-oct, punto 25.
 *
 * «Cuando uno crea una cuenta debe de salir todos héroes disponibles del
 * juego […], no solo uno». La instrucción que lo acompaña es distinguir
 * POSESIÓN de CATÁLOGO: no regalar los héroes, sino enseñar cuáles existen y
 * cómo se consiguen.
 *
 *   - «Mis héroes» (la carta de siempre, `heroes-inventario.js`): los que el
 *     jugador tiene. Eso lo decide el inventario y aquí no se toca.
 *   - «Héroes del Nexo» (esto): los héroes que se pueden conseguir, leídos del
 *     catálogo público de productos de tipo HÉROE
 *     (`GET /api/v1/productos?tipo=HEROE`, productos.yaml: solo ACTIVO y
 *     UNICO). Cada uno dice si ya es tuyo —algún héroe de tu inventario es ese
 *     producto— o lleva a la tienda a buscarlo.
 *
 * Nada se escribe a mano: ni la lista de prototipos, ni precios, ni cómo se
 * obtiene más allá de la tienda, que es donde el catálogo los vende. Si el
 * catálogo no responde, se dice y se ofrece reintentar; los héroes propios
 * siguen en su sitio.
 *
 * @module contenido/inventario/heroes-del-nexo
 */

import { h } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { retratoDeHeroe } from '../../comun/ui/juego/heroe.js';
import { identidadDePrototipo, normalizarNombre } from '../../comun/ui/juego/prototipos.js';
import { listarProductos } from './cliente-productos.js';
import { construirCarga, construirError, construirVacio } from './estados-vista.js';

/** A dónde lleva «Conseguir en la tienda»: la tienda buscando ese héroe. */
export function hrefDeLaTiendaPara(nombre, base = '../../cuentas/tienda.html') {
  const limpio = String(nombre ?? '').trim();
  return limpio ? `${base}?busqueda=${encodeURIComponent(limpio)}` : base;
}

/**
 * La carta de un héroe del catálogo.
 *
 * @param {{id: string, nombre: string, prototipo?: string|null, imagen?: string|null,
 *   descripcion?: string|null}} producto `ProductoCreado` de tipo HÉROE
 * @param {{tuyo: boolean, hrefTienda?: (nombre: string) => string}} opciones
 * @returns {HTMLElement}
 */
export function cartaDelNexo(producto, { tuyo, hrefTienda = hrefDeLaTiendaPara }) {
  const identidad = identidadDePrototipo(producto.prototipo);
  const idNombre = `nexo-heroe-${producto.id}`;
  // Debajo del nombre, el prototipo y su familia; si el producto ya se llama
  // como su prototipo («Guerrero Tanque»), solo la familia: no se repite.
  const mismoNombre = normalizarNombre(identidad.nombre) === normalizarNombre(producto.nombre);
  const subtitulo = identidad.conocido
    ? [mismoNombre ? null : identidad.nombre, identidad.etiquetaFamilia].filter(Boolean).join(' · ')
    : null;
  return h('article', {
    clase: 'tarjeta nexo-heroe',
    datos: {
      producto: producto.id,
      tuyo: String(tuyo),
      ...(identidad.conocido ? { prototipo: identidad.clave.replace(/\s+/g, '-') } : {}),
    },
    atributos: { 'aria-labelledby': idNombre },
    hijos: [
      retratoDeHeroe(
        { nombre: producto.nombre, imagen: producto.imagen ?? null, prototipo: producto.prototipo },
        { conNombre: false },
      ),
      h('div', {
        clase: 'nexo-heroe__cuerpo',
        hijos: [
          h('h3', {
            clase: 'nexo-heroe__nombre',
            texto: producto.nombre,
            atributos: { id: idNombre },
          }),
          subtitulo ? h('p', { clase: 'nexo-heroe__prototipo', texto: subtitulo }) : null,
          producto.descripcion
            ? h('p', { clase: 'nexo-heroe__descripcion', texto: producto.descripcion })
            : null,
          tuyo
            ? h('p', {
                clase: 'nexo-heroe__tuyo',
                datos: { zona: 'ya-es-tuyo' },
                hijos: [icono('check', { etiqueta: null }), h('span', { texto: 'Ya es tuyo' })],
              })
            : h('a', {
                clase: 'boton boton--secundario boton--pequeno nexo-heroe__conseguir',
                texto: 'Conseguir en la tienda',
                datos: { accion: 'conseguir-heroe' },
                atributos: {
                  href: hrefTienda(producto.nombre),
                  'aria-label': `Conseguir en la tienda: ${producto.nombre}`,
                },
              }),
        ],
      }),
    ],
  });
}

/**
 * Pinta «Héroes del Nexo» en `contenedor`.
 *
 * @param {HTMLElement} contenedor
 * @param {{listar?: () => Promise<{productos: object[]}>, propios?: Set<string>,
 *   hrefTienda?: (nombre: string) => string}} [opciones]
 *   `propios`: los `productoId` de los héroes del inventario
 * @returns {Promise<{estado: 'ok'|'vacio'|'error', total?: number, tuyos?: number}>}
 */
export async function pintarHeroesDelNexo(
  contenedor,
  {
    listar = () => listarProductos({ tipo: 'HEROE' }),
    propios = new Set(),
    hrefTienda = hrefDeLaTiendaPara,
  } = {},
) {
  contenedor.replaceChildren(construirCarga('Buscando los héroes del Nexo...'));
  let productos;
  try {
    ({ productos } = await listar());
  } catch (fallo) {
    console.warn('No se pudo leer el catálogo de héroes', fallo);
    contenedor.replaceChildren(
      construirError(
        'No podemos enseñar los héroes del Nexo ahora.',
        'El catálogo no responde. Tus héroes siguen aquí arriba.',
        {
          texto: 'Reintentar',
          alPulsar: () => pintarHeroesDelNexo(contenedor, { listar, propios, hrefTienda }),
        },
      ),
    );
    return { estado: 'error' };
  }

  // Solo lo que el catálogo dice que es un héroe y tiene nombre que enseñar.
  const heroes = (productos ?? []).filter(
    (producto) => producto?.tipo === 'HEROE' && typeof producto.nombre === 'string' && producto.id,
  );
  if (heroes.length === 0) {
    contenedor.replaceChildren(
      construirVacio(
        'Ahora mismo el catálogo no tiene héroes.',
        'Cuando los haya, aparecerán aquí con cómo conseguirlos.',
        null,
      ),
    );
    return { estado: 'vacio', total: 0, tuyos: 0 };
  }

  const tuyos = heroes.filter((producto) => propios.has(producto.id)).length;
  contenedor.replaceChildren(
    h('p', {
      clase: 'heroes-del-nexo__cuenta',
      datos: { zona: 'cuenta-nexo' },
      texto: `${heroes.length} ${heroes.length === 1 ? 'héroe' : 'héroes'} en el Nexo · ${tuyos} ${tuyos === 1 ? 'es tuyo' : 'son tuyos'}`,
    }),
    h('ul', {
      clase: 'heroes-del-nexo__lista',
      hijos: heroes.map((producto) =>
        h('li', {
          hijos: [cartaDelNexo(producto, { tuyo: propios.has(producto.id), hrefTienda })],
        }),
      ),
    }),
  );
  return { estado: 'ok', total: heroes.length, tuyos };
}
