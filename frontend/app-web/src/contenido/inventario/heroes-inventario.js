/**
 * Pestaña «Héroes» de Mi inventario — UXC-1 (HeroCard + HeroStatusBadge).
 *
 * Es la respuesta a «qué héroes poseo y qué puedo hacer con cada uno»: una
 * carta por héroe con su prototipo reconocible (§6.1.1, ocho prototipos), sus
 * cifras con el equipo puesto, cuántas ranuras lleva ocupadas, su estado y dos
 * salidas: la ficha completa y el equipamiento.
 *
 * Cada carta junta tres servicios y ninguno hunde a los otros
 * (`Promise.allSettled`): el prototipo e imagen del catálogo de productos, las
 * cifras de `/inventario/heroes/{id}/estadisticas` y el equipo de
 * `/inventario/heroes/{id}/equipamiento`. Lo que no llega no se pinta.
 */

import { h, vaciar } from '../../comun/ui/dom.js';
import { cartaDeHeroePropio } from '../../comun/ui/juego/heroe.js';
import { estadoDeHeroe, ranurasOcupadas, ESTADOS } from '../../comun/ui/juego/estado-heroe.js';
import { construirVacio } from './estados-vista.js';

/**
 * Por qué no se ofrece cambiar el equipo de un héroe, según su estado: el
 * bloqueo de una subasta (HU-INV-010) y la misión en curso (§7.8.10: «no puede
 * ser modificado su equipamiento»; el inventario respondería 409).
 */
const MOTIVO_SIN_EQUIPAR = Object.freeze({
  [ESTADOS.BLOQUEADO]: 'Está bloqueado por una subasta: no se puede equipar mientras dure.',
  [ESTADOS.EN_MISION]: 'Está en una misión: no se puede cambiar su equipo hasta que vuelva.',
});

/**
 * Pinta la rejilla de héroes y devuelve el equipo que se pudo leer de cada
 * uno (lo necesita la pestaña de objetos para decir qué está equipado).
 *
 * @param {HTMLElement} contenedor
 * @param {object} opciones
 * @param {object[]} opciones.heroes elementos de tipo HEROE
 * @param {string} opciones.identidad
 * @param {(identidad: string, heroeId: string) => Promise<object>} opciones.consultarEquipo
 * @param {(identidad: string, heroeId: string) => Promise<object>} opciones.consultarEstadisticas
 * @param {(productoId: string) => Promise<object>} opciones.consultarProducto
 * @param {(heroe: object, contexto: {prototipo: string|null}) => void} opciones.alVerFicha
 * @param {(heroe: object) => void} opciones.alEquipar
 * @param {() => Promise<Array<{nivel: number, experienciaParaSubir?: number}>>} [opciones.consultarNiveles]
 *   UXC-9 — la tabla de progresión, para decir cuánto falta al siguiente nivel
 * @param {string} [opciones.hrefTienda]
 * @returns {Promise<{equipos: Map<string, object|undefined>, prototipos: Map<string, string|null>}>}
 */
export async function pintarHeroes(
  contenedor,
  {
    heroes,
    identidad,
    consultarEquipo,
    consultarEstadisticas,
    consultarProducto,
    alVerFicha,
    alEquipar,
    consultarNiveles = null,
    hrefTienda = '../../cuentas/tienda.html',
  },
) {
  const equipos = new Map();
  const prototipos = new Map();

  if (!Array.isArray(heroes) || heroes.length === 0) {
    vaciar(contenedor);
    contenedor.append(
      construirVacio(
        'Todavía no tienes héroes.',
        'Un héroe es lo que llevas al combate. Las cuentas nuevas reciben uno al prepararse; ' +
          'también se consiguen en la tienda.',
        { texto: 'Ir a la tienda', href: hrefTienda },
      ),
    );
    return { equipos, prototipos };
  }

  // UXC-9 — la tabla de niveles, una vez para todos. Si no llega, el nivel
  // y la experiencia se dicen igual, sin lo que falta.
  const tablaDeNiveles = consultarNiveles ? await consultarNiveles().catch(() => null) : null;

  const detalles = await Promise.all(
    heroes.map(async (heroe) => {
      const [equipo, estadisticas, producto] = await Promise.allSettled([
        consultarEquipo(identidad, heroe.id),
        consultarEstadisticas(identidad, heroe.id),
        heroe.productoId ? consultarProducto(heroe.productoId) : Promise.reject(new Error('')),
      ]);
      return {
        heroe,
        equipo: equipo.status === 'fulfilled' ? equipo.value : undefined,
        estadisticas: estadisticas.status === 'fulfilled' ? estadisticas.value : null,
        producto: producto.status === 'fulfilled' ? producto.value : null,
      };
    }),
  );

  const rejilla = h('ul', { clase: 'hero-grid', atributos: { 'aria-label': 'Tus héroes' } });
  for (const { heroe, equipo, estadisticas, producto } of detalles) {
    equipos.set(heroe.id, equipo);
    const prototipo = producto?.prototipo ?? null;
    prototipos.set(heroe.id, prototipo);
    const estado = estadoDeHeroe({ elemento: heroe, equipamiento: equipo });
    const motivoSinEquipar = MOTIVO_SIN_EQUIPAR[estado.estado] ?? null;

    rejilla.append(
      h('li', {
        clase: 'hero-grid__celda',
        hijos: [
          cartaDeHeroePropio(
            {
              nombre: heroe.nombrePropio,
              prototipo,
              imagen: producto?.imagen ?? null,
              estadisticas,
              estado,
              ranurasOcupadas: equipo ? ranurasOcupadas(equipo) : null,
              progreso: progresoDe(heroe, tablaDeNiveles),
            },
            [
              {
                texto: 'Ver ficha',
                etiqueta: `Ver la ficha de ${heroe.nombrePropio}`,
                datos: { accion: 'ver-ficha', idHeroe: heroe.id },
                alPulsar: () => alVerFicha(heroe, { prototipo }),
              },
              {
                texto: estado.estado === ESTADOS.NO_ELEGIBLE ? 'Equiparlo' : 'Equipamiento',
                etiqueta: `Gestionar el equipamiento de ${heroe.nombrePropio}`,
                principal: estado.estado === ESTADOS.NO_ELEGIBLE,
                datos: { accion: 'equipar', idHeroe: heroe.id },
                deshabilitada: motivoSinEquipar,
                alPulsar: () => alEquipar(heroe),
              },
            ],
          ),
        ],
      }),
    );
  }

  vaciar(contenedor);
  contenedor.append(rejilla);
  return { equipos, prototipos };
}

/**
 * UXC-9 — el nivel y la experiencia que guarda el inventario (1.5.0) y, con
 * la tabla del servicio de héroes, la que pide el nivel para subir. Null si
 * el inventario no los trae: no se supone nivel 1.
 *
 * @param {{nivel?: number, experiencia?: number}} heroe elemento del inventario
 * @param {Array<{nivel: number, experienciaParaSubir?: number}>|null} tabla
 * @returns {{nivel: number, experiencia: number|null, paraSubir: number|null}|null}
 */
export function progresoDe(heroe, tabla) {
  if (!Number.isInteger(heroe?.nivel)) {
    return null;
  }
  const fila = Array.isArray(tabla) ? tabla.find((n) => n?.nivel === heroe.nivel) : null;
  return {
    nivel: heroe.nivel,
    experiencia: Number.isFinite(heroe.experiencia) ? heroe.experiencia : null,
    paraSubir: Number.isFinite(fila?.experienciaParaSubir) ? fila.experienciaParaSubir : null,
    maximo: Array.isArray(tabla) && Boolean(fila) && !Number.isFinite(fila?.experienciaParaSubir),
  };
}
