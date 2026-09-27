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
 * @module comun/ui/asistente
 */

import { h } from './dom.js';

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
      texto: 'IA',
      atributos: { type: 'button' },
    });
  boton.setAttribute('aria-label', 'Abrir el asistente');
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
    boton.setAttribute('aria-label', abierta ? 'Cerrar el asistente' : 'Abrir el asistente');
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
