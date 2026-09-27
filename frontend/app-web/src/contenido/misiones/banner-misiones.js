/**
 * Banner de misiones — UXC-5 (MissionBanner).
 *
 * Dos requisitos lo piden, con dos comportamientos distintos:
 *
 *   - RF-MIS-001 (§7.8.9): el tablón empieza con un «banner rotativo» de
 *     misiones destacadas, eventos especiales, novedades y misiones de tiempo
 *     limitado.
 *   - RF-INV-003 (§7.1): el inventario presenta un banner con las misiones
 *     disponibles y acceso directo al tablón. Sin misiones que mostrar,
 *     «contenido promocional alternativo»; con el módulo de misiones caído o
 *     inexistente, **el banner se oculta sin afectar el resto de la vista**.
 *
 * Esto último es lo que pasa hoy, porque no hay servicio de misiones: en el
 * inventario el banner no aparece. No es un hueco: es la excepción que el
 * requisito escribe para este caso.
 *
 * ## Carrusel accesible
 *
 * Sigue el patrón de carrusel de la WAI: cada diapositiva es un grupo con su
 * posición («2 de 3»), hay botones de anterior y siguiente y uno por
 * diapositiva, y la rotación automática **se puede pausar** (WCAG 2.2.2) y se
 * detiene sola mientras el puntero o el foco están dentro. Con
 * `prefers-reduced-motion` empieza en pausa. Mientras rota, la región no se
 * anuncia (`aria-live="off"`); cuando la mueve quien la usa, sí.
 *
 * @module contenido/misiones/banner-misiones
 */

import { clases, h, vaciar } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { cuantoFalta } from '../../comun/ui/formato.js';
import { categoriaDe, dificultadDe, textoDeDuracion } from './modelo-misiones.js';

/** Cada cuánto pasa a la siguiente, si nadie la ha pausado. */
export const INTERVALO_DE_ROTACION_MS = 8000;

/**
 * Lo que dice el sello de una misión destacada: novedad y tiempo limitado.
 *
 * @param {{nueva?: boolean, disponibleHasta?: string|null}} mision
 * @param {Date} [ahora]
 * @returns {string|null}
 */
export function selloDeMision(mision, ahora = new Date()) {
  const partes = [];
  if (mision?.nueva) {
    partes.push('Nueva');
  }
  if (mision?.disponibleHasta) {
    // `cuantoFalta` da «en 3 d», «en 2 h 15 min», o una marca de «ya pasó» /
    // «sin fecha» que aquí no se enseña: una misión vencida no se destaca.
    const falta = cuantoFalta(mision.disponibleHasta, ahora);
    partes.push(falta.startsWith('en ') ? `Tiempo limitado · termina ${falta}` : 'Tiempo limitado');
  }
  return partes.length > 0 ? partes.join(' · ') : null;
}

/**
 * Una diapositiva: arte, sello, nombre, resumen, datos y su acción.
 *
 * @param {import('./fuente-misiones.js').ResumenMision} mision
 * @param {{posicion: number, total: number, hrefDe: (mision: object) => string}} contexto
 * @returns {HTMLElement}
 */
function diapositiva(mision, { posicion, total, hrefDe }) {
  const categoria = categoriaDe(mision.categoria);
  const dificultad = dificultadDe(mision.dificultad);
  const sello = selloDeMision(mision);
  return h('article', {
    clase: 'banner-misiones__diapositiva',
    datos: { mision: mision.id, categoria: String(mision.categoria ?? '').toLowerCase() },
    atributos: {
      role: 'group',
      'aria-roledescription': 'diapositiva',
      'aria-label': `${posicion} de ${total}: ${mision.nombre}`,
    },
    hijos: [
      h('div', {
        clase: 'banner-misiones__arte',
        atributos: { 'aria-hidden': 'true' },
        hijos: [
          mision.imagen
            ? h('img', {
                clase: 'banner-misiones__imagen',
                atributos: { src: mision.imagen, alt: '', loading: 'lazy' },
              })
            : icono(categoria?.icono ?? 'mapa', {
                clase: 'icono banner-misiones__emblema',
                etiqueta: null,
              }),
        ],
      }),
      h('div', {
        clase: 'banner-misiones__cuerpo',
        hijos: [
          sello ? h('p', { clase: 'banner-misiones__sello', texto: sello }) : null,
          h('h2', { clase: 'banner-misiones__nombre', texto: mision.nombre }),
          mision.descripcionBreve
            ? h('p', { clase: 'banner-misiones__texto', texto: mision.descripcionBreve })
            : null,
          h('p', {
            clase: 'banner-misiones__datos',
            texto: [
              categoria?.etiqueta,
              dificultad?.etiqueta,
              textoDeDuracion(mision.duracionHoras),
            ]
              .filter(Boolean)
              .join(' · '),
          }),
          h('a', {
            clase: 'boton boton--primario boton--pequeno banner-misiones__accion',
            texto: 'Ver misión',
            atributos: { href: hrefDe(mision), 'aria-label': `Ver la misión ${mision.nombre}` },
            datos: { accion: 'ver-mision' },
          }),
        ],
      }),
    ],
  });
}

/**
 * El carrusel.
 *
 * @param {import('./fuente-misiones.js').ResumenMision[]} misiones
 * @param {object} opciones
 * @param {(mision: object) => string} opciones.hrefDe a dónde lleva «Ver misión»
 * @param {string|null} [opciones.hrefTablon] enlace al tablón (en el inventario)
 * @param {number} [opciones.intervaloMs]
 * @param {boolean} [opciones.enPausa] empezar parado
 * @param {{setInterval: Function, clearInterval: Function}} [opciones.temporizador]
 * @returns {{elemento: HTMLElement, ir: (indice: number) => void, actual: () => number,
 *   pausar: () => void, reanudar: () => void, detener: () => void}}
 */
export function bannerDeMisiones(
  misiones,
  {
    hrefDe,
    hrefTablon = null,
    intervaloMs = INTERVALO_DE_ROTACION_MS,
    enPausa = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false,
    temporizador = globalThis,
  },
) {
  const total = misiones.length;
  const diapositivas = misiones.map((mision, indice) =>
    diapositiva(mision, { posicion: indice + 1, total, hrefDe }),
  );
  const pista = h('div', {
    clase: 'banner-misiones__pista',
    atributos: { 'aria-live': 'off' },
    hijos: diapositivas,
  });

  let indiceActual = 0;
  let pausadoPorPersona = enPausa;
  let pausadoPorFoco = false;
  let reloj = null;
  let vistoEnLaPagina = false;

  function detener() {
    if (reloj !== null) {
      temporizador.clearInterval(reloj);
      reloj = null;
    }
  }

  const botonPausa = h('button', {
    clase: 'boton boton--secundario boton--pequeno banner-misiones__pausa',
    datos: { accion: 'pausar-banner' },
    atributos: { type: 'button' },
  });
  const anterior = h('button', {
    clase: 'boton boton--icono boton--secundario banner-misiones__anterior',
    datos: { accion: 'mision-anterior' },
    atributos: { type: 'button', 'aria-label': 'Misión anterior' },
    hijos: [icono('chevron', { etiqueta: null })],
  });
  const siguiente = h('button', {
    clase: 'boton boton--icono boton--secundario banner-misiones__siguiente',
    datos: { accion: 'mision-siguiente' },
    atributos: { type: 'button', 'aria-label': 'Misión siguiente' },
    hijos: [icono('chevron', { etiqueta: null })],
  });
  const puntos = misiones.map((mision, indice) => {
    const punto = h('button', {
      clase: 'banner-misiones__punto',
      datos: { accion: 'ir-a-mision', indice },
      atributos: {
        type: 'button',
        'aria-label': `Ir a la misión ${indice + 1} de ${total}: ${mision.nombre}`,
      },
    });
    punto.addEventListener('click', () => ir(indice, { porPersona: true }));
    return punto;
  });

  const controles =
    total > 1
      ? h('div', {
          clase: 'banner-misiones__controles',
          hijos: [
            botonPausa,
            anterior,
            h('div', { clase: 'banner-misiones__puntos', hijos: puntos }),
            siguiente,
          ],
        })
      : null;

  const elemento = h('section', {
    clase: clases('banner-misiones', total > 1 && 'banner-misiones--rotativo'),
    datos: { componente: 'banner-misiones' },
    atributos: { 'aria-roledescription': 'carrusel', 'aria-label': 'Misiones destacadas' },
    hijos: [
      pista,
      controles || hrefTablon
        ? h('div', {
            clase: 'banner-misiones__pie',
            hijos: [
              controles,
              hrefTablon
                ? h('a', {
                    clase: 'banner-misiones__tablon',
                    texto: 'Ver el tablón de misiones',
                    atributos: { href: hrefTablon },
                  })
                : null,
            ],
          })
        : null,
    ],
  });

  function pintar() {
    diapositivas.forEach((dia, indice) => {
      dia.hidden = indice !== indiceActual;
    });
    puntos.forEach((punto, indice) => {
      if (indice === indiceActual) {
        punto.setAttribute('aria-current', 'true');
      } else {
        punto.removeAttribute('aria-current');
      }
    });
    const rotando = !pausadoPorPersona && !pausadoPorFoco && total > 1;
    // WAI: mientras rota solo, callar; cuando la mueve una persona, decir.
    pista.setAttribute('aria-live', rotando ? 'off' : 'polite');
    botonPausa.textContent = pausadoPorPersona ? 'Reanudar' : 'Pausar';
    botonPausa.setAttribute(
      'aria-label',
      pausadoPorPersona ? 'Reanudar la rotación de misiones' : 'Pausar la rotación de misiones',
    );
    elemento.dataset.rotacion = rotando ? 'activa' : 'en-pausa';
  }

  function programar() {
    detener();
    if (total > 1 && !pausadoPorPersona && !pausadoPorFoco) {
      reloj = temporizador.setInterval(() => {
        // Si estuvo en la página y la vista ya lo quitó, deja de rotar solo.
        if (elemento.isConnected) {
          vistoEnLaPagina = true;
        } else if (vistoEnLaPagina) {
          detener();
          return;
        }
        ir(indiceActual + 1);
      }, intervaloMs);
    }
  }

  /**
   * @param {number} indice
   * @param {{porPersona?: boolean}} [opciones]
   */
  function ir(indice, { porPersona = false } = {}) {
    if (total === 0) {
      return;
    }
    indiceActual = ((indice % total) + total) % total;
    pintar();
    if (porPersona) {
      // Quien pulsa una flecha se queda mirando: la rotación vuelve a contar
      // desde cero, no le salta la diapositiva a los dos segundos.
      programar();
    }
  }

  botonPausa.addEventListener('click', () => {
    pausadoPorPersona = !pausadoPorPersona;
    pintar();
    programar();
  });
  anterior.addEventListener('click', () => ir(indiceActual - 1, { porPersona: true }));
  siguiente.addEventListener('click', () => ir(indiceActual + 1, { porPersona: true }));

  const detenerPorFoco = () => {
    pausadoPorFoco = true;
    pintar();
    programar();
  };
  const reanudarPorFoco = () => {
    pausadoPorFoco = false;
    pintar();
    programar();
  };
  elemento.addEventListener('mouseenter', detenerPorFoco);
  elemento.addEventListener('mouseleave', reanudarPorFoco);
  elemento.addEventListener('focusin', detenerPorFoco);
  elemento.addEventListener('focusout', (evento) => {
    if (!elemento.contains(evento.relatedTarget)) {
      reanudarPorFoco();
    }
  });

  pintar();
  programar();

  return {
    elemento,
    ir: (indice) => ir(indice, { porPersona: true }),
    actual: () => indiceActual,
    pausar: () => {
      pausadoPorPersona = true;
      pintar();
      programar();
    },
    reanudar: () => {
      pausadoPorPersona = false;
      pintar();
      programar();
    },
    detener,
  };
}

/**
 * El contenido promocional que RF-INV-003 pide cuando no hay misiones que
 * enseñar: el siguiente paso real, que es preparar la estrategia.
 *
 * @param {string} hrefEstrategia
 * @returns {HTMLElement}
 */
export function promocionDeEstrategia(hrefEstrategia) {
  return h('section', {
    clase: 'banner-misiones banner-misiones--promocion',
    datos: { componente: 'banner-misiones' },
    atributos: { 'aria-label': 'Misiones' },
    hijos: [
      h('div', {
        clase: 'banner-misiones__arte',
        atributos: { 'aria-hidden': 'true' },
        hijos: [icono('escudo-check', { clase: 'icono banner-misiones__emblema', etiqueta: null })],
      }),
      h('div', {
        clase: 'banner-misiones__cuerpo',
        hijos: [
          h('p', { clase: 'banner-misiones__sello', texto: 'Ahora mismo no hay misiones para ti' }),
          h('h2', { clase: 'banner-misiones__nombre', texto: 'Prepara a tu héroe' }),
          h('p', {
            clase: 'banner-misiones__texto',
            texto:
              'En las misiones tu héroe pelea solo, guiado por las rotaciones que le prepares. Déjalas listas.',
          }),
          h('a', {
            clase: 'boton boton--primario boton--pequeno banner-misiones__accion',
            texto: 'Preparar su estrategia',
            atributos: { href: hrefEstrategia },
            datos: { accion: 'preparar-estrategia' },
          }),
        ],
      }),
    ],
  });
}

/**
 * Monta el banner donde toque, con la regla de RF-INV-003: sin módulo de
 * misiones, o si no responde, la zona se queda oculta y vacía.
 *
 * @param {HTMLElement} zona
 * @param {object} opciones
 * @param {import('./fuente-misiones.js').FuenteDeMisiones} opciones.fuente
 * @param {(mision: object) => string} opciones.hrefDe
 * @param {string|null} [opciones.hrefTablon]
 * @param {string|null} [opciones.hrefEstrategia] con él, sin destacadas se promociona la estrategia
 * @returns {Promise<'oculto'|'promocion'|'misiones'>}
 */
export async function montarBannerDeMisiones(
  zona,
  { fuente, hrefDe, hrefTablon = null, hrefEstrategia = null },
) {
  vaciar(zona);
  zona.hidden = true;
  if (!fuente?.disponible) {
    return 'oculto';
  }
  let destacadas;
  try {
    destacadas = await fuente.destacadas();
  } catch (fallo) {
    console.error('No se pudieron leer las misiones destacadas', fallo);
    return 'oculto';
  }
  if (!Array.isArray(destacadas) || destacadas.length === 0) {
    if (!hrefEstrategia) {
      return 'oculto';
    }
    zona.append(promocionDeEstrategia(hrefEstrategia));
    zona.hidden = false;
    return 'promocion';
  }
  zona.append(bannerDeMisiones(destacadas, { hrefDe, hrefTablon }).elemento);
  zona.hidden = false;
  return 'misiones';
}
