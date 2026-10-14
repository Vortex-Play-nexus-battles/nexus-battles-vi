/**
 * El héroe al que pertenece una habilidad o una épica, elegido por su nombre.
 *
 * El contrato guarda el identificador del producto de tipo héroe
 * (`heroe`, UUID); el alta y la ficha pedían escribirlo a mano («UUID del
 * héroe»), que es un dato que quien administra no tiene por qué saber. Aquí se
 * listan los héroes del catálogo (`GET /api/v1/productos?tipo=HEROE`) y se
 * elige uno por su nombre; el valor sigue siendo el identificador.
 *
 * @module contenido/productos/heroes-del-catalogo
 */

import { h } from '../../comun/ui/dom.js';
import { listarProductos } from './cliente-productos.js';

/** El contrato admite hasta 50 por página; el catálogo tiene ocho héroes. */
const HEROES_POR_CONSULTA = 50;

/**
 * Los héroes del catálogo, por nombre.
 *
 * @param {{listar?: typeof listarProductos, fetchImpl?: Function}} [opciones]
 * @returns {Promise<Array<{id: string, nombre: string}>>}
 */
export async function heroesDelCatalogo({ listar = listarProductos, fetchImpl } = {}) {
  const pagina = await listar(
    { tipo: 'HEROE', tamano: HEROES_POR_CONSULTA },
    fetchImpl ? { fetchImpl } : {},
  );
  return (pagina?.content ?? [])
    .filter((producto) => producto?.id && producto?.nombre)
    .map((producto) => ({ id: String(producto.id), nombre: String(producto.nombre) }))
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
}

/**
 * Rellena un `<select>` con los héroes y deja elegido `valor`. Si el valor no
 * está entre ellos (un héroe suspendido), se conserva como opción para no
 * perderlo al guardar. No toca `disabled`: eso lo decide quien muestra u
 * oculta el grupo de campos de cada tipo.
 *
 * @param {HTMLSelectElement} control
 * @param {{valor?: string|null, listar?: typeof listarProductos, fetchImpl?: Function}} [opciones]
 * @returns {Promise<void>}
 */
export async function rellenarHeroes(control, { valor = null, listar, fetchImpl } = {}) {
  control.replaceChildren(h('option', { texto: 'Cargando héroes…', atributos: { value: '' } }));
  try {
    const heroes = await heroesDelCatalogo({ listar, fetchImpl });
    const opciones = [
      h('option', { texto: 'Elige el héroe', atributos: { value: '' } }),
      ...heroes.map((heroe) =>
        h('option', { texto: heroe.nombre, atributos: { value: heroe.id } }),
      ),
    ];
    if (valor && !heroes.some((heroe) => heroe.id === valor)) {
      opciones.push(
        h('option', { texto: 'Héroe actual (no está a la venta)', atributos: { value: valor } }),
      );
    }
    control.replaceChildren(...opciones);
    control.value = valor ?? '';
  } catch {
    control.replaceChildren(
      h('option', {
        texto: valor ? 'Héroe actual' : 'No se pudieron cargar los héroes',
        atributos: { value: valor ?? '' },
      }),
    );
  }
}
