/**
 * Banner informativo rotativo de la home — RF-NOT-002 (HU-NOT-002, #533).
 *
 * «El sistema deberá incluir en la vista principal un banner rotativo con
 * anuncios […] indicando en cada mensaje su fecha de publicación y su
 * vigencia.» Los anuncios los programa la administración en productos
 * (HU-PRD-013, #854) y este componente pinta los que el servidor declara
 * vigentes (`GET /api/v1/banners/vigentes`, pública, productos.yaml 1.6.0).
 *
 * ## Lo que decide el servidor y lo que decide esta pieza
 *
 * Qué está vigente lo decide el servidor: no retirado, ya publicado y sin
 * vencer. Aquí solo se aplica la misma fecha que él devuelve, `vigenteHasta`,
 * para retirar el que vence **mientras la página sigue abierta** (CA-01): sin
 * esto, quien deja la home abierta seguiría viendo una promoción terminada
 * hasta recargar. No se adivina ninguna regla más.
 *
 * - CA-01: varios vigentes rotan solos y el que vence se va sin recargar.
 * - CA-02: con uno solo no hay rotación, ni controles, ni semántica de
 *   carrusel: es un aviso, no un carrusel de uno.
 * - CA-03: sin vigentes, o si la consulta falla, la zona se queda oculta. Un
 *   fallo no rompe la home: va a la consola (`console.warn`).
 * - CA-04: cada mensaje dice «Publicado el …» y «Vigente hasta el …» con el
 *   formateador común (`fechaHora`, zona horaria de quien mira).
 *
 * ## Carrusel accesible
 *
 * El mismo patrón WAI que el banner de misiones (UXC-5): región con
 * `aria-roledescription="carrusel"`, cada anuncio un grupo con su posición
 * («2 de 3»), botón para pausar o reanudar (WCAG 2.2.2), anterior, siguiente
 * y uno por anuncio. Se detiene solo con el foco dentro o el ratón encima, y
 * con `prefers-reduced-motion` no arranca. Mientras rota solo la región calla
 * (`aria-live="off"`); cuando la mueve la persona, se anuncia (`polite`).
 *
 * @module plataforma/notificaciones/banner-rotativo
 */

import { h, vaciar } from '../../comun/ui/dom.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { icono } from '../../comun/ui/icono.js';
import { listarVigentes } from '../../contenido/productos/cliente-banners.js';

/**
 * Cada cuánto pasa al siguiente anuncio, si nadie lo ha pausado.
 *
 * Es una constante de interfaz, no una regla de negocio: ni admin-parametros
 * ni las decisiones del PO fijan un ritmo para este banner. Es el mismo de
 * `banner-misiones.js` para que los dos carruseles del producto se muevan
 * igual. Se puede pasar otro con la opción `intervaloMs`.
 */
export const INTERVALO_DE_ROTACION_MS = 8000;

/** El mayor retraso que admite `setTimeout` (2^31 − 1 ms, unos 24,8 días). */
const ESPERA_MAXIMA_MS = 2_147_483_647;

/**
 * @typedef {object} BannerVigente
 * @property {string} id
 * @property {string} contenido
 * @property {string} publicarDesde ISO-8601
 * @property {string} vigenteHasta ISO-8601
 */

/**
 * ¿Se puede pintar? Sin contenido o sin fechas legibles no se cumpliría CA-04.
 *
 * @param {unknown} banner
 * @returns {banner is BannerVigente}
 */
function esMostrable(banner) {
  return (
    Boolean(banner) &&
    typeof banner.contenido === 'string' &&
    banner.contenido.trim() !== '' &&
    Number.isFinite(Date.parse(banner.publicarDesde)) &&
    Number.isFinite(Date.parse(banner.vigenteHasta))
  );
}

/**
 * @param {BannerVigente} banner
 * @param {number} ahoraMs
 * @returns {boolean}
 */
export function haExpirado(banner, ahoraMs) {
  return Date.parse(banner.vigenteHasta) <= ahoraMs;
}

/** «Publicado el …» / «Vigente hasta el …», con la fecha máquina en `<time>`. */
function fechaRotulada(rotulo, iso) {
  return h('span', {
    hijos: [`${rotulo} `, h('time', { texto: fechaHora(iso), atributos: { datetime: iso } })],
  });
}

/**
 * Un anuncio: su texto y sus dos fechas (CA-04).
 *
 * @param {BannerVigente} banner
 * @returns {HTMLElement}
 */
function mensajeDe(banner) {
  return h('div', {
    clase: 'banner-rotativo__mensaje',
    datos: { banner: banner.id },
    hijos: [
      h('p', { clase: 'banner__titulo', texto: banner.contenido }),
      h('p', {
        clase: 'banner__vigencia',
        hijos: [
          fechaRotulada('Publicado el', banner.publicarDesde),
          h('span', { texto: ' · ', atributos: { 'aria-hidden': 'true' } }),
          fechaRotulada('Vigente hasta el', banner.vigenteHasta),
        ],
      }),
    ],
  });
}

/**
 * El carrusel. Pinta lo que recibe menos lo que no se puede mostrar o ya
 * venció, y arranca sus relojes; quien lo monta decide dónde va.
 *
 * @param {Array<BannerVigente>} banners
 * @param {object} [opciones]
 * @param {number} [opciones.intervaloMs]
 * @param {boolean} [opciones.enPausa] empezar parado; por omisión, con
 *   `prefers-reduced-motion: reduce`
 * @param {() => number} [opciones.ahora] reloj, inyectable en pruebas
 * @param {{setInterval: Function, clearInterval: Function,
 *          setTimeout: Function, clearTimeout: Function}} [opciones.temporizador]
 * @param {() => void} [opciones.alVaciarse] cuando no queda ninguno: vence el
 *   último o, ya al construirlo, no llega ninguno que se pueda mostrar
 * @returns {{elemento: HTMLElement, total: () => number, actual: () => number,
 *   ir: (indice: number) => void, pausar: () => void, reanudar: () => void,
 *   detener: () => void}}
 */
export function bannerRotativo(
  banners,
  {
    intervaloMs = INTERVALO_DE_ROTACION_MS,
    enPausa = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false,
    ahora = () => Date.now(),
    temporizador = globalThis,
    alVaciarse = () => {},
  } = {},
) {
  /** @type {Array<{banner: BannerVigente, mensaje: HTMLElement, punto: HTMLElement}>} */
  let anuncios = [];
  let indice = 0;
  let pausadoPorPersona = enPausa;
  let pausadoPorFoco = false;
  let relojDeRotacion = null;
  let relojDeCaducidad = null;
  let vistoEnLaPagina = false;

  const pista = h('div', {
    clase: 'banner__cuerpo banner-rotativo__pista',
    atributos: { 'aria-live': 'off' },
  });
  const puntos = h('div', { clase: 'banner-rotativo__puntos' });
  const botonPausa = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    datos: { accion: 'pausar-banner' },
    atributos: { type: 'button' },
  });
  const anterior = h('button', {
    clase: 'boton boton--icono boton--secundario banner-rotativo__anterior',
    datos: { accion: 'anuncio-anterior' },
    atributos: { type: 'button', 'aria-label': 'Anuncio anterior' },
    hijos: [icono('chevron', { etiqueta: null })],
  });
  const siguiente = h('button', {
    clase: 'boton boton--icono boton--secundario banner-rotativo__siguiente',
    datos: { accion: 'anuncio-siguiente' },
    atributos: { type: 'button', 'aria-label': 'Anuncio siguiente' },
    hijos: [icono('chevron', { etiqueta: null })],
  });
  const controles = h('div', {
    clase: 'banner-rotativo__controles',
    hijos: [botonPausa, anterior, puntos, siguiente],
  });
  const elemento = h('section', {
    clase: 'banner banner-rotativo',
    datos: { componente: 'banner-rotativo' },
    hijos: [icono('campana', { clase: 'icono banner__icono', etiqueta: null }), pista],
  });

  for (const banner of banners.filter(esMostrable)) {
    const mensaje = mensajeDe(banner);
    const punto = h('button', {
      clase: 'banner-rotativo__punto',
      datos: { accion: 'ir-a-anuncio' },
      atributos: { type: 'button' },
    });
    const anuncio = { banner, mensaje, punto };
    punto.addEventListener('click', () => {
      const posicion = anuncios.indexOf(anuncio);
      if (posicion >= 0) {
        ir(posicion, { porPersona: true });
      }
    });
    anuncios.push(anuncio);
    pista.append(mensaje);
    puntos.append(punto);
  }

  const rotando = () => anuncios.length > 1 && !pausadoPorPersona && !pausadoPorFoco;

  function pintar() {
    const total = anuncios.length;
    const esCarrusel = total > 1;
    if (esCarrusel) {
      elemento.setAttribute('aria-roledescription', 'carrusel');
      elemento.setAttribute('aria-label', 'Anuncios del Nexo');
      if (controles.parentNode !== elemento) {
        elemento.append(controles);
      }
    } else {
      elemento.removeAttribute('aria-roledescription');
      elemento.setAttribute('aria-label', 'Anuncio del Nexo');
      controles.remove();
    }
    anuncios.forEach(({ mensaje, punto }, posicion) => {
      mensaje.hidden = posicion !== indice;
      if (esCarrusel) {
        mensaje.setAttribute('role', 'group');
        mensaje.setAttribute('aria-roledescription', 'anuncio');
        mensaje.setAttribute('aria-label', `${posicion + 1} de ${total}`);
      } else {
        mensaje.removeAttribute('role');
        mensaje.removeAttribute('aria-roledescription');
        mensaje.removeAttribute('aria-label');
      }
      punto.setAttribute('aria-label', `Ver el anuncio ${posicion + 1} de ${total}`);
      if (posicion === indice) {
        punto.setAttribute('aria-current', 'true');
      } else {
        punto.removeAttribute('aria-current');
      }
    });
    const enRotacion = rotando();
    // WAI: mientras rota solo, callar; cuando la mueve una persona, decir. Un
    // aviso fijo no cambia de mensaje: no necesita región viva.
    pista.setAttribute('aria-live', esCarrusel && !enRotacion ? 'polite' : 'off');
    botonPausa.textContent = pausadoPorPersona ? 'Reanudar' : 'Pausar';
    botonPausa.setAttribute(
      'aria-label',
      pausadoPorPersona ? 'Reanudar la rotación de anuncios' : 'Pausar la rotación de anuncios',
    );
    let estado = 'fija';
    if (enRotacion) {
      estado = 'activa';
    } else if (esCarrusel) {
      estado = 'en-pausa';
    }
    elemento.dataset.rotacion = estado;
  }

  function detener() {
    if (relojDeRotacion !== null) {
      temporizador.clearInterval(relojDeRotacion);
      relojDeRotacion = null;
    }
    if (relojDeCaducidad !== null) {
      temporizador.clearTimeout(relojDeCaducidad);
      relojDeCaducidad = null;
    }
  }

  /** Si estuvo en la página y la vista ya lo quitó, deja de trabajar solo. */
  function sigueEnLaPagina() {
    if (elemento.isConnected) {
      vistoEnLaPagina = true;
      return true;
    }
    if (vistoEnLaPagina) {
      detener();
      return false;
    }
    return true;
  }

  function programarRotacion() {
    if (relojDeRotacion !== null) {
      temporizador.clearInterval(relojDeRotacion);
      relojDeRotacion = null;
    }
    if (rotando()) {
      relojDeRotacion = temporizador.setInterval(() => {
        if (sigueEnLaPagina()) {
          ir(indice + 1);
        }
      }, intervaloMs);
    }
  }

  /** Despierta justo cuando vence el próximo, aunque la rotación esté parada. */
  function programarCaducidad() {
    if (relojDeCaducidad !== null) {
      temporizador.clearTimeout(relojDeCaducidad);
      relojDeCaducidad = null;
    }
    if (anuncios.length === 0) {
      return;
    }
    const proxima = Math.min(...anuncios.map(({ banner }) => Date.parse(banner.vigenteHasta)));
    const espera = Math.min(Math.max(proxima - ahora(), 0), ESPERA_MAXIMA_MS);
    relojDeCaducidad = temporizador.setTimeout(() => {
      relojDeCaducidad = null;
      if (sigueEnLaPagina()) {
        retirarExpirados();
      }
    }, espera);
  }

  /** CA-01: fuera los que ya vencieron; si no queda ninguno, CA-03. */
  function retirarExpirados() {
    const instante = ahora();
    const actual = anuncios[indice];
    const vencidos = anuncios.filter(({ banner }) => haExpirado(banner, instante));
    if (vencidos.length > 0) {
      for (const { mensaje, punto } of vencidos) {
        mensaje.remove();
        punto.remove();
      }
      const posicionActual = anuncios.indexOf(actual);
      const antesDelActual = anuncios
        .slice(0, posicionActual)
        .filter((anuncio) => vencidos.includes(anuncio)).length;
      anuncios = anuncios.filter((anuncio) => !vencidos.includes(anuncio));
      // El que se veía sigue si no venció; si venció, se ve el que venía después.
      indice = anuncios.length === 0 ? 0 : (posicionActual - antesDelActual) % anuncios.length;
    }
    if (anuncios.length === 0) {
      detener();
      alVaciarse();
      return;
    }
    pintar();
    programarRotacion();
    programarCaducidad();
  }

  /**
   * @param {number} nuevo
   * @param {{porPersona?: boolean}} [opciones]
   */
  function ir(nuevo, { porPersona = false } = {}) {
    if (anuncios.length === 0) {
      return;
    }
    indice = ((nuevo % anuncios.length) + anuncios.length) % anuncios.length;
    pintar();
    if (porPersona) {
      // Quien pulsa se queda mirando: la cuenta vuelve a empezar.
      programarRotacion();
    }
  }

  botonPausa.addEventListener('click', () => {
    pausadoPorPersona = !pausadoPorPersona;
    pintar();
    programarRotacion();
  });
  anterior.addEventListener('click', () => ir(indice - 1, { porPersona: true }));
  siguiente.addEventListener('click', () => ir(indice + 1, { porPersona: true }));

  const detenerPorFoco = () => {
    pausadoPorFoco = true;
    pintar();
    programarRotacion();
  };
  const seguirSinFoco = () => {
    pausadoPorFoco = false;
    pintar();
    programarRotacion();
  };
  elemento.addEventListener('mouseenter', detenerPorFoco);
  elemento.addEventListener('mouseleave', seguirSinFoco);
  elemento.addEventListener('focusin', detenerPorFoco);
  elemento.addEventListener('focusout', (evento) => {
    if (!elemento.contains(evento.relatedTarget)) {
      seguirSinFoco();
    }
  });

  // Lo que llega ya vencido (el reloj de quien mira va por delante del
  // servidor) no se llega a pintar: misma regla que la retirada en vivo.
  retirarExpirados();

  return {
    elemento,
    total: () => anuncios.length,
    actual: () => indice,
    ir: (nuevo) => ir(nuevo, { porPersona: true }),
    pausar: () => {
      pausadoPorPersona = true;
      pintar();
      programarRotacion();
    },
    reanudar: () => {
      pausadoPorPersona = false;
      pintar();
      programarRotacion();
    },
    detener,
  };
}

/** @param {HTMLElement} zona */
function ocultar(zona) {
  vaciar(zona);
  zona.hidden = true;
}

/**
 * Monta el banner en la zona de la home con las reglas de #533: pinta los
 * vigentes; sin vigentes o si la consulta falla, la zona queda oculta.
 *
 * @param {HTMLElement|null} zona `[data-zona="bloque-banners"]` de la home
 * @param {object} [opciones] las de `bannerRotativo` y además:
 * @param {() => Promise<unknown>} [opciones.consultar] por omisión, el
 *   cliente de productos (`listarVigentes`)
 * @returns {Promise<ReturnType<typeof bannerRotativo>|null>} el carrusel, o
 *   `null` si no se pintó nada
 */
export async function montarBannerRotativo(zona, { consultar = listarVigentes, ...opciones } = {}) {
  if (!zona) {
    return null;
  }
  ocultar(zona);
  let banners;
  try {
    banners = await consultar();
  } catch (fallo) {
    // CA-03 con el servicio caído: la home sigue entera y el motivo queda
    // para quien depura, no para el jugador.
    console.warn('Anuncios del Nexo: no se pudo consultar el banner de vigentes.', fallo);
    return null;
  }
  const carrusel = bannerRotativo(Array.isArray(banners) ? banners : [], {
    ...opciones,
    alVaciarse: () => ocultar(zona),
  });
  if (carrusel.total() === 0) {
    carrusel.detener();
    return null;
  }
  zona.append(carrusel.elemento);
  zona.hidden = false;
  return carrusel;
}
