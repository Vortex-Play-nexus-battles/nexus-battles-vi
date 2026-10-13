/**
 * Botón flotante del asistente — HU-CHA-001 (RF-CHA-001).
 *
 * ## Qué hace
 *
 * Monta el botón «IA» en la esquina de la vista y, al pulsarlo, abre y
 * cierra la ventana del asistente (`ventana-chatbot.js`). RF-CHA-001 exige el
 * icono en **todas** las vistas, así que no lo monta cada vista: lo monta el
 * armazón (`shell.js`, `montarArmazon`) una sola vez.
 *
 * ## Historia
 *
 * Antes de existir el servicio, este módulo solo mostraba un aviso («el
 * asistente llega en una entrega posterior») con el enlace al chat general.
 * Ese aviso no desaparece: ahora lo da la propia ventana cuando el servicio
 * no responde, que es lo que pide CA-03 de HU-CHA-001.
 *
 * ## Por qué la ventana se carga al primer clic
 *
 * El botón está en todas las vistas y casi nadie lo pulsa. La ventana (y el
 * cliente HTTP) se importa la primera vez que se abre, no al cargar la vista.
 *
 * ## Revisión del modo jugador del 6-oct, punto 4
 *
 * «Mejorar el botón de IA para que no se vea tan genérico en todas las
 * pantallas». Era un círculo azul con «IA». Ahora lleva el emblema del Nexo
 * (el cristal del logotipo) sobre el cromo, con un halo de energía, una
 * señal y el rótulo «Asistente Nexus» al pasar el puntero o al llegar con el
 * teclado. Su nombre accesible es «Abrir asistente Nexus» (y «Cerrar…» con la
 * ventana abierta); el rótulo visible está contenido en él (WCAG 2.5.3). No
 * lleva otro texto visible: un «IA» suelto no estaría en ese nombre.
 *
 * @module comun/ui/asistente
 */

import { h } from './dom.js';

/** Lo que dice el botón: su nombre accesible y su rótulo. */
export const TEXTOS_DEL_LANZADOR = Object.freeze({
  abrir: 'Abrir asistente Nexus',
  cerrar: 'Cerrar asistente Nexus',
  rotulo: 'Asistente Nexus',
});

/**
 * El emblema del logotipo, resuelto desde este módulo y no desde la vista
 * (las vistas cuelgan a profundidades distintas; ver `icono.js`).
 *
 * @returns {string}
 */
export function rutaDelEmblema() {
  return new URL('../../../../../shared/ui-kit/marca/emblema.webp', import.meta.url).href;
}

/**
 * Lo de dentro del botón: halo, emblema, señal y rótulo. Todo adorno para el
 * lector de pantalla, que oye el `aria-label`.
 *
 * @returns {HTMLElement[]}
 */
function contenidoDelLanzador() {
  const emblema = h('img', {
    clase: 'chatbot-flotante__emblema',
    atributos: { alt: '', width: 34, height: 32, decoding: 'async', draggable: 'false' },
  });
  emblema.src = rutaDelEmblema();
  return [
    h('span', { clase: 'chatbot-flotante__halo', atributos: { 'aria-hidden': 'true' } }),
    emblema,
    h('span', { clase: 'chatbot-flotante__senal', atributos: { 'aria-hidden': 'true' } }),
    h('span', {
      clase: 'chatbot-flotante__rotulo',
      texto: TEXTOS_DEL_LANZADOR.rotulo,
      atributos: { 'aria-hidden': 'true' },
    }),
  ];
}

/**
 * Crea la ventana real. Se importa bajo demanda; las pruebas inyectan otra.
 *
 * @returns {Promise<{alternar: (desde?: HTMLElement) => void, abierta: () => boolean,
 *                    elemento: HTMLElement}>}
 */
async function crearVentanaPorOmision() {
  const { crearVentanaChatbot } = await import('./ventana-chatbot.js');
  return crearVentanaChatbot();
}

/**
 * Monta el botón flotante del asistente. Es idempotente: si ya está montado
 * en esa vista, devuelve el mismo botón sin volver a enganchar nada.
 *
 * @param {Document|HTMLElement} [raiz] dónde colgarlo; por omisión el `body`
 * @param {{crearVentana?: () => Promise<object>|object}} [opciones]
 * @returns {HTMLButtonElement|null}
 */
export function montarAsistente(raiz = document, { crearVentana = crearVentanaPorOmision } = {}) {
  const destino = raiz?.body ?? raiz;
  if (!destino?.append) {
    return null;
  }

  // Si la vista todavía trae el botón en su HTML, se reutiliza ese en vez de
  // añadir un segundo: dos botones flotantes superpuestos confunden.
  const existente = destino.querySelector?.('.chatbot-flotante');
  if (existente?.dataset.asistenteMontado === 'si') {
    return existente;
  }

  const boton =
    existente ??
    h('button', {
      clase: 'chatbot-flotante',
      atributos: { type: 'button' },
    });
  // Punto 4 — el mismo aspecto aunque la vista traiga un botón suyo con «IA».
  boton.replaceChildren(...contenidoDelLanzador());
  boton.setAttribute('aria-label', TEXTOS_DEL_LANZADOR.abrir);
  boton.setAttribute('aria-expanded', 'false');
  boton.dataset.asistenteMontado = 'si';

  // Una sola ventana por vista, creada en el primer clic. Se guarda la
  // promesa (no la ventana) para que dos clics seguidos no creen dos.
  let creando = null;

  function obtenerVentana() {
    creando ??= Promise.resolve(crearVentana()).then((ventana) => {
      if (ventana?.elemento?.id) {
        boton.setAttribute('aria-controls', ventana.elemento.id);
      }
      return ventana;
    });
    return creando;
  }

  function reflejarEstado(abierta) {
    boton.setAttribute('aria-expanded', String(abierta));
    boton.setAttribute(
      'aria-label',
      abierta ? TEXTOS_DEL_LANZADOR.cerrar : TEXTOS_DEL_LANZADOR.abrir,
    );
  }

  boton.addEventListener('click', async () => {
    const actual = await obtenerVentana();
    actual.alternar(boton);
    reflejarEstado(actual.abierta());
  });

  // La ventana también se cierra con Escape o con su propia «×»: el botón
  // tiene que enterarse para no anunciar «Cerrar» con la ventana cerrada.
  destino.addEventListener?.('chatbot:cerrada', () => reflejarEstado(false));

  if (!existente) {
    destino.append(boton);
  }
  return boton;
}
